package com.f47mod.entity.vehicle;

import com.f47mod.F47Config;
import com.f47mod.util.Iff;
import com.f47mod.world.MissionLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Autonome F-47 - fliegt ohne Spieler im Cockpit. Gesteuert wird sie von einem
 * einfachen Zustandsautomaten ("Bot-Pilot"), der Kurs, Schub und Waffeneinsatz
 * selbst bestimmt. Befehle kommen ueber das Kommando-Tablet.
 */
public class AutonomousF47Entity extends F47Entity implements MissionLoader.MissionUnit {
	private static final TrackedData<Integer> ORDER =
			DataTracker.registerData(AutonomousF47Entity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<String> CALLSIGN =
			DataTracker.registerData(AutonomousF47Entity.class, TrackedDataHandlerRegistry.STRING);
	private static final TrackedData<Boolean> HAS_PILOT =
			DataTracker.registerData(AutonomousF47Entity.class, TrackedDataHandlerRegistry.BOOLEAN);

	/**
	 * Ausfuehrliches Flugprotokoll, eingeschaltet ueber die Umgebungsvariable
	 * {@code F47_DEBUG}. Damit laesst sich nachvollziehen, warum eine Maschine
	 * nicht abhebt - Anstellwinkel, Bodenkontakt und Kollisionen stehen dann
	 * fuenfmal je Sekunde im Protokoll.
	 */
	private static final boolean DEBUG = System.getenv("F47_DEBUG") != null;

	private static final String[] CALLSIGNS = {
			"Viper", "Falcon", "Ghost", "Raven", "Hammer", "Cobra", "Eagle", "Reaper",
			"Sabre", "Talon", "Wolf", "Nomad"
	};

	public enum Order {
		/** Am Boden warten, Triebwerke aus. */
		PARKED,
		/** Startbahn hoch und auf Patrouillenhoehe steigen. */
		TAKEOFF,
		/** Kreist um den Heimatpunkt und meldet Kontakte. */
		PATROL,
		/** Fliegt Formation mit dem Besitzer. */
		ESCORT,
		/** Greift Luft- und Bodenziele an. */
		ATTACK,
		/** Rueckflug zur Basis und Landung. */
		RTB
	}

	@Nullable
	private UUID ownerUuid;
	@Nullable
	private BlockPos homeBase;
	private int targetSearchTimer;
	private int weaponTimer;
	@Nullable
	private Entity currentTarget;
	private double patrolAngle;
	private int patrolAltitude = 70;
	/** Punkt, den der Bot-Pilot gerade anfliegt. */
	@Nullable
	private Vec3d currentGoal;
	/**
	 * Ticks, bis die Maschine von selbst zum naechsten Einsatz startet.
	 * Anfangs zufaellig, damit nicht die ganze Staffel gleichzeitig abhebt.
	 */
	private int scrambleTimer = 100 + (int) (Math.random() * 500);
	/** Wie lange die Maschine schon bewegungslos im Gelaende steht. */
	private int strandedTicks;
	/** Richtung der Startbahn in Grad - der Startlauf folgt ihr. */
	private float homeHeading;
	/** Zuletzt bekannte Stelle, an der Gegner stehen. Ziel des Vorstosses. */
	@Nullable
	private Vec3d knownEnemy;
	/** Uhr fuer die weitraeumige Gegnersuche. */
	private int sweepTimer;

	public AutonomousF47Entity(EntityType<? extends AutonomousF47Entity> type, World world) {
		super(type, world);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(ORDER, Order.PARKED.ordinal());
		builder.add(CALLSIGN, "Viper 1");
		builder.add(HAS_PILOT, false);
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		dataTracker.set(ORDER, nbt.getInt("Order"));
		if (nbt.contains("Callsign")) {
			dataTracker.set(CALLSIGN, nbt.getString("Callsign"));
		}
		dataTracker.set(HAS_PILOT, nbt.getBoolean("HasPilot"));
		if (nbt.containsUuid("Owner")) {
			ownerUuid = nbt.getUuid("Owner");
		}
		if (nbt.contains("HomeBase")) {
			homeBase = NbtHelper.toBlockPos(nbt, "HomeBase").orElse(null);
		}
		patrolAltitude = nbt.contains("PatrolAltitude") ? nbt.getInt("PatrolAltitude") : 70;
		homeHeading = nbt.getFloat("HomeHeading");
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putInt("Order", dataTracker.get(ORDER));
		nbt.putString("Callsign", getCallsign());
		nbt.putBoolean("HasPilot", hasPilot());
		if (ownerUuid != null) {
			nbt.putUuid("Owner", ownerUuid);
		}
		if (homeBase != null) {
			nbt.put("HomeBase", NbtHelper.fromBlockPos(homeBase));
		}
		nbt.putInt("PatrolAltitude", patrolAltitude);
		nbt.putFloat("HomeHeading", homeHeading);
	}

	public Order getOrder() {
		int ordinal = dataTracker.get(ORDER);
		Order[] values = Order.values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : Order.PARKED;
	}

	public void setOrder(Order order) {
		dataTracker.set(ORDER, order.ordinal());
		if (order == Order.PARKED) {
			setThrottle(0.0f);
			setAfterburner(false);
		}
	}

	public String getCallsign() {
		return dataTracker.get(CALLSIGN);
	}

	public void setCallsign(String callsign) {
		dataTracker.set(CALLSIGN, callsign);
		// Das Rufzeichen schwebt als Namensschild ueber der Maschine, damit man
		// im Verband erkennt, welcher Jet gerade welchen Auftrag hat.
		setCustomName(Text.literal(callsign).formatted(getTeam().formatting()));
		setCustomNameVisible(true);
	}

	public boolean hasPilot() {
		return dataTracker.get(HAS_PILOT);
	}

	public void setHasPilot(boolean hasPilot) {
		dataTracker.set(HAS_PILOT, hasPilot);
	}

	public void setOwner(@Nullable PlayerEntity player) {
		ownerUuid = player == null ? null : player.getUuid();
	}

	@Nullable
	public PlayerEntity getOwner() {
		return ownerUuid == null ? null : getWorld().getPlayerByUuid(ownerUuid);
	}

	public void setHomeBase(BlockPos pos) {
		setHomeBase(pos, getYaw());
	}

	/**
	 * Setzt Heimatflugplatz und Bahnrichtung.
	 *
	 * <p>Die Richtung ist noetig, weil der Startlauf ihr folgt: Eine Maschine,
	 * die beim Abstellen quer stand, wuerde sonst quer ueber das Vorfeld
	 * beschleunigen und im Gelaende haengen bleiben.
	 */
	public void setHomeBase(BlockPos pos, float heading) {
		homeBase = pos;
		homeHeading = heading;
		patrolAltitude = Math.max(pos.getY() + 35, 75);
	}

	/**
	 * Unterwegs braucht die Maschine eigenes gerechnetes Gelaende - der feste
	 * Bereich um den Stuetzpunkt reicht nur bis zur Warteschleife. Am Boden
	 * abgestellt braucht sie nichts, da steht sie ohnehin im Bereich der Basis.
	 */
	@Override
	public boolean needsLoadedTerrain() {
		return getOrder() != Order.PARKED;
	}

	@Override
	protected boolean usesFlightAssist() {
		return F47Config.get().botFlightAssist;
	}

	/** Richtung der Startbahn in Grad. */
	public float getHomeHeading() {
		return homeHeading;
	}

	@Nullable
	public BlockPos getHomeBase() {
		return homeBase;
	}

	public void randomiseCallsign() {
		setCallsign(CALLSIGNS[random.nextInt(CALLSIGNS.length)] + " " + (1 + random.nextInt(9)));
	}

	@Override
	public ActionResult interact(PlayerEntity player, Hand hand) {
		// Autonome Jets nimmt man nicht als Passagier - sie werden befehligt.
		if (getWorld().isClient) {
			return ActionResult.SUCCESS;
		}
		player.sendMessage(Text.translatable("message.f47.jet_status",
				getCallsign(),
				Text.translatable("order.f47." + getOrder().name().toLowerCase(java.util.Locale.ROOT)),
				(int) getStructure(),
				getMissileAmmo()), true);
		return ActionResult.CONSUME;
	}

	@Override
	protected void serverTick() {
		super.serverTick();
		if (!hasPilot()) {
			// Ohne Pilot bleibt die Maschine stehen und wartet auf Bodenpersonal.
			setThrottle(0.0f);
			return;
		}
		if (weaponTimer > 0) {
			weaponTimer--;
		}
		if (++targetSearchTimer >= 20) {
			targetSearchTimer = 0;
			currentTarget = findTarget();
		}
		lookForEnemyBase();
		scrambleIfIdle();
		checkForWreck();
		flyMission();

		if (DEBUG && age % 5 == 0) {
			com.f47mod.F47Mod.LOGGER.info(
					"[SPUR] {} {} v={} vy={} Nick={} Roll={} Anstell={} Y={} Gas={}",
					getCallsign(), getOrder(),
					String.format("%.1f", getVelocity().length() * FlightModel.TICKS_PER_SECOND),
					String.format("%+.1f", getVelocity().y * FlightModel.TICKS_PER_SECOND),
					String.format("%.1f", getPitch()),
					String.format("%.1f", getRoll()),
					String.format("%.1f", FlightModel.degrees(getAngleOfAttack())),
					String.format("%.2f", getY()),
					String.format("%.2f", getThrottle()));
		}
	}

	/**
	 * Erkennt eine verunglueckte Maschine und schreibt sie ab.
	 *
	 * <p>Wer irgendwo im Gelaende gestrandet ist, kommt aus eigener Kraft nicht
	 * mehr weg: kein Anlauf, kein Auftrieb, dazu bei jedem Tick eine Kollision.
	 * Ohne diese Pruefung stuende so ein Wrack fuer immer da und wuerde in der
	 * Staffelstaerke mitzaehlen - die Kaserne baute also nie Ersatz.
	 */
	private void checkForWreck() {
		// Bewusst ohne Bodenpruefung: Eine verkeilte Maschine meldet oft gar
		// keinen Bodenkontakt mehr, weil sie zwischen Bloecken haengt. Wer sich
		// nicht mehr bewegt, ist so oder so ein Wrack.
		boolean stranded = getOrder() != Order.PARKED
				&& getVelocity().lengthSquared() < 0.01
				&& (homeBase == null || squaredHorizontalDistance(homeBase) > 40.0 * 40.0);
		if (!stranded) {
			strandedTicks = 0;
			return;
		}
		// Ein paar Sekunden Geduld - eine harte Landung ist noch kein Totalverlust.
		if (++strandedTicks > 100) {
			announce("message.f47.lost");
			kill();
		}
	}

	/**
	 * Laesst eine wartende Maschine von selbst starten.
	 *
	 * <p>Ohne das bleibt eine frisch aufgestellte Staffel fuer immer stehen:
	 * {@link Order#PARKED} geht von allein in keinen anderen Zustand ueber,
	 * und ein Gegner auf einem Stuetzpunkt zweihundert Bloecke weiter liegt
	 * ausserhalb der Bordradarreichweite - keine Seite bemerkt also je die
	 * andere, und der Krieg beginnt nie.
	 *
	 * <p>Zwei Anlaesse zum Start: ein erkannter Gegner (Alarmstart) oder
	 * schlicht Zeit. Die Uhr laeuft je Maschine anders, so dass immer nur ein
	 * Teil der Staffel in der Luft ist und der Rest am Boden auftankt.
	 */
	private void scrambleIfIdle() {
		if (getOrder() != Order.PARKED) {
			return;
		}
		// Mit fast leeren Tanks bleibt sie stehen, bis ein Techniker auftankt.
		if (getFuel() < F47Config.get().maxFuel * 0.25f) {
			return;
		}
		if (currentTarget != null && currentTarget.isAlive()) {
			setOrder(Order.TAKEOFF);
			announce("message.f47.scramble");
			return;
		}
		if (--scrambleTimer <= 0) {
			// Nach der Landung eine Weile stehen bleiben, aber nicht zu lange -
			// sonst ist von zwoelf Maschinen nie mehr als eine in der Luft.
			scrambleTimer = 250 + (int) (Math.random() * 450);
			setOrder(Order.TAKEOFF);
		}
	}

	/** Der Bot-Pilot: waehlt je nach Befehl einen Zielpunkt und fliegt ihn an. */
	private void flyMission() {
		Order order = getOrder();
		if (order == Order.PARKED) {
			setThrottle(0.0f);
			return;
		}

		if (getFuel() < F47Config.get().maxFuel * 0.12f && order != Order.RTB && homeBase != null) {
			setOrder(Order.RTB);
			announce("message.f47.bingo_fuel");
		}

		Vec3d goal;
		float throttle = 0.65f;

		switch (order) {
			case TAKEOFF -> {
				BlockPos base = homeBase != null ? homeBase : getBlockPos();
				double speedMs = getVelocity().length() * FlightModel.TICKS_PER_SECOND;

				// Startverfahren in drei Abschnitten, wie in echt geflogen.
				// Der Bahnrichtung folgen, nicht der eigenen Nase: Beim
				// Abstellen kann die Maschine beliebig herumstehen.
				double radians = Math.toRadians(homeHeading);
				Vec3d along = new Vec3d(-Math.sin(radians), 0.0, Math.cos(radians));

				if (speedMs < ROTATE_SPEED && getY() < base.getY() + 12) {
					// 1. Anlauf: Nase unten, volle Leistung. Wer sofort zieht,
					// hebt zwar ab - bei sechsundzwanzig Metern je Sekunde
					// traegt die Flaeche aber noch nicht, der Anstellwinkel
					// laeuft weg, und die Maschine faellt wieder herunter.
					goal = getPos().add(along.multiply(150));
					throttle = 1.0f;
					setAfterburner(true);
				} else if (squaredHorizontalDistance(base) < 70.0 * 70.0) {
					// 2. Anfangssteigflug geradeaus auf Bahnrichtung. Hier
					// wird nicht gekurvt: Frisch abgehoben mit knapp fuenfzig
					// Metern je Sekunde kann die Maschine einer Kurve nicht
					// folgen - sie schiebt quer, der Anstellwinkel schiesst
					// ueber siebzig Grad, und sie faellt aus dem Himmel.
					// Steil steigen, nicht flach: Die Maschine schafft bei
					// dreizehn Grad Anstellwinkel ueber vierzig Grad Steigwinkel.
					// Ein flacher Zielpunkt verschenkt das - mit fuenfzehn Grad
					// war die Staffel am Ende des freigeraeumten Gelaendes erst
					// fuenfzehn Bloecke hoch und lief in den naechsten Hang.
					goal = getPos().add(along.multiply(60)).add(0.0, 70.0, 0.0);
					throttle = 1.0f;
					setAfterburner(true);
				} else {
					// 3. Ueber der Basis weitersteigen. Bewusst zurueck ueber
					// das eigene Gelaende: Dort ist bis zwoelf Bloecke ueber
					// Grund freigeraeumt, waehrend geradeaus schon der naechste
					// Huegel steht - in den ist die Staffel vorher reihenweise
					// hineingeflogen.
					goal = patrolPoint();
					setAfterburner(false);
					throttle = throttleForSpeed(PATROL_SPEED * 1.2);
				}

				// Frueh genug in die Patrouille wechseln. Der Wechsel auf
				// Angriff passiert erst von dort aus - wer im Start haengen
				// bleibt, greift also nie an, egal wie nah der Gegner ist.
				if (getY() > base.getY() + 26) {
					setAfterburner(false);
					setOrder(Order.PATROL);
					announce("message.f47.on_station");
				}
			}
			case ESCORT -> {
				PlayerEntity owner = getOwner();
				if (owner == null) {
					goal = patrolPoint();
					throttle = throttleForSpeed(PATROL_SPEED);
				} else {
					// Formation schraeg hinter und ueber dem Spieler.
					Vec3d offset = new Vec3d(9.0, 6.0, -12.0)
							.rotateY(-owner.getYaw() * MathHelper.RADIANS_PER_DEGREE);
					goal = owner.getPos().add(offset);
					double distance = getPos().distanceTo(goal);
					throttle = (float) MathHelper.clamp(distance / 40.0, 0.32, 1.0);
				}
			}
			case ATTACK -> {
				if (currentTarget != null && currentTarget.isAlive()) {
					Vec3d targetPos = currentTarget.getPos().add(0, currentTarget.getHeight() * 0.5, 0);
					double distance = getPos().distanceTo(targetPos);
					if (distance < 45) {
						// Kurz vor dem Ziel abfangen, um nicht hineinzufliegen.
						goal = targetPos.add(currentTarget.getVelocity().multiply(8)).add(0, 6, 0);
					} else {
						goal = targetPos.add(currentTarget.getVelocity().multiply(14));
					}
					// Auch im Angriff geregelt statt Vollgas: Wer schneller
					// fliegt, als seine Kurve zulaesst, schiesst am Ziel vorbei
					// und dann aus dem gerechneten Gebiet hinaus.
					throttle = throttleForSpeed(ATTACK_SPEED);
					setAfterburner(false);
					engage(currentTarget, distance);
				} else {
					goal = patrolPoint();
					throttle = throttleForSpeed(PATROL_SPEED);
					setAfterburner(false);
				}
			}
			case RTB -> {
				BlockPos base = homeBase != null ? homeBase : getBlockPos();
				double horizontal = Math.sqrt(squaredHorizontalDistance(base));
				if (horizontal < 24) {
					// Sinkflug bis zum Aufsetzen, dann parken. Anfluggeschwindigkeit
					// knapp ueber dem Abriss - darunter faellt sie durch.
					goal = new Vec3d(base.getX(), base.getY() + 1.5, base.getZ());
					throttle = throttleForSpeed(45.0);
					if (isOnGround() || getY() - base.getY() < 2.5) {
						setOrder(Order.PARKED);
						setThrottle(0.0f);
						setVelocity(Vec3d.ZERO);
						announce("message.f47.landed");
						return;
					}
				} else {
					goal = new Vec3d(base.getX(), Math.max(base.getY() + 25, patrolAltitude - 15), base.getZ());
					throttle = throttleForSpeed(PATROL_SPEED);
				}
			}
			default -> {
				goal = patrolOrSweep();
				// Auf der Warteschleife wird Fahrt gehalten, nicht Vollgas
				// gegeben - sonst wird der Kreis zu weit fuer die Physik.
				throttle = throttleForSpeed(knownEnemy != null ? ATTACK_SPEED : PATROL_SPEED);
				if (currentTarget != null && currentTarget.isAlive()) {
					setOrder(Order.ATTACK);
					announce("message.f47.engaging");
				}
			}
		}

		// Gelaende voraus? Dann Ziel anheben. Ein Kampfflugzeug steigt flach -
		// bei fuenfzig Metern je Sekunde und sieben Metern Steigen dauert es
		// fuenf Sekunden und zweihundert Meter Weg, um dreissig Bloecke hoch zu
		// kommen. Steht in dieser Strecke ein Huegel, fliegt die Maschine
		// hinein; im Versuch ist die halbe Staffel immer an derselben
		// Gelaendekante hinter der Bahn zerschellt.
		goal = avoidTerrain(goal);

		// Notfalls zurueck ins geladene Gebiet - das hat Vorrang vor allem
		// anderen, denn ausserhalb hoert die Maschine schlicht auf zu fliegen.
		Vec3d recall = stayInRange();
		if (recall != null) {
			goal = recall;
			throttle = throttleForSpeed(PATROL_SPEED * 1.4);
			setAfterburner(false);
		}

		// Nur den Plan festlegen - geflogen wird im gemeinsamen Flugmodell,
		// das steer() aufruft. So laeuft die Physik genau einmal pro Tick.
		currentGoal = goal;
		setThrottle(throttle);
	}

	/** Kreisbahn um die Basis. */
	/**
	 * Hebt das Ziel an, wenn voraus Gelaende steht.
	 *
	 * <p>Wie ein Bodenannaeherungs-Warnsystem: Es wird nicht die Stelle unter
	 * der Maschine geprueft, sondern die, an der sie in gut zwei Sekunden
	 * ankommt - darunter laesst sich ein Hang nicht mehr ueberfliegen. Die
	 * Hoehenkarte beantwortet das ohne Blockabfragen.
	 */
	private Vec3d avoidTerrain(Vec3d goal) {
		if (isOnGround()) {
			return goal;
		}
		Vec3d motion = getVelocity();
		if (motion.horizontalLengthSquared() < 0.01) {
			return goal;
		}
		Vec3d ahead = getPos().add(motion.multiply(70.0));
		int terrain = getWorld().getTopY(Heightmap.Type.MOTION_BLOCKING,
				MathHelper.floor(ahead.x), MathHelper.floor(ahead.z));
		double floor = terrain + TERRAIN_CLEARANCE;
		return goal.y < floor ? new Vec3d(goal.x, floor, goal.z) : goal;
	}

	/**
	 * Sucht weitraeumig, wo der Gegner ueberhaupt sitzt.
	 *
	 * <p>Das Bordradar reicht hundertvierzig Bloecke - ein feindlicher
	 * Stuetzpunkt liegt weiter weg. Ohne diese Suche kreist die Staffel bis in
	 * alle Ewigkeit ueber der eigenen Bahn und findet nie jemanden, obwohl der
	 * Gegner nur zwei Kreisdurchmesser entfernt steht. Gesucht wird selten,
	 * weil es ueber ein grosses Gebiet geht.
	 */
	private void lookForEnemyBase() {
		if (--sweepTimer > 0) {
			return;
		}
		sweepTimer = 100;
		double reach = F47Config.get().strikeRange;
		Box box = getBoundingBox().expand(reach);
		Entity nearest = null;
		double best = Double.MAX_VALUE;
		for (Entity candidate : getWorld().getOtherEntities(this, box, e -> Iff.isEnemy(this, e))) {
			double distance = candidate.squaredDistanceTo(this);
			if (distance < best) {
				best = distance;
				nearest = candidate;
			}
		}
		knownEnemy = nearest == null ? null : nearest.getPos();
	}

	/**
	 * Zielpunkt der Patrouille: entweder Warteschleife oder Vorstoss.
	 *
	 * <p>Solange kein Gegner bekannt ist, wird gekreist. Sobald einer bekannt
	 * ist, fliegt die Staffel hin - genau das hat vorher gefehlt, und deshalb
	 * sah es aus, als wuerde sie nur ueber der eigenen Basis Runden drehen.
	 */
	private Vec3d patrolOrSweep() {
		if (knownEnemy == null) {
			return patrolPoint();
		}
		return new Vec3d(knownEnemy.x, patrolAltitude, knownEnemy.z);
	}

	/**
	 * Punkt auf der Warteschleife.
	 *
	 * <p>Der Kreis wandert mit der Geschwindigkeit weiter, aber sein Radius
	 * bleibt fest - und der Bot haelt dazu passend Fahrt (siehe
	 * {@link #PATROL_SPEED}). Beides muss zusammenpassen: Nach der echten
	 * Kurvenformel r = v² / (g·√(n²−1)) braucht eine Maschine bei 360 km/h
	 * selbst mit sieben g rund 150 Meter Radius. Wer schneller kreisen will,
	 * als die Physik zulaesst, fliegt die Kurve einfach nach aussen auf - und
	 * landet ausserhalb der geladenen Chunks.
	 */
	private Vec3d patrolPoint() {
		BlockPos base = homeBase != null ? homeBase : getBlockPos();
		// Winkelschritt so, dass die Bahngeschwindigkeit ungefaehr stimmt.
		patrolAngle += PATROL_SPEED / (PATROL_RADIUS * FlightModel.TICKS_PER_SECOND);
		return new Vec3d(
				base.getX() + Math.cos(patrolAngle) * PATROL_RADIUS,
				patrolAltitude,
				base.getZ() + Math.sin(patrolAngle) * PATROL_RADIUS);
	}

	/**
	 * Haelt die Maschine im geladenen Gebiet.
	 *
	 * <p>Das ist die Lehre aus dem ersten Versuch mit echter Physik: Die
	 * Staffel hob ab, beschleunigte, verliess den dauerhaft geladenen Bereich -
	 * und blieb dort auf der Stelle stehen, weil Minecraft ausserhalb nicht
	 * mehr rechnet. Vier Minuten spaeter standen alle zwoelf Maschinen noch auf
	 * die Nachkommastelle genau dort. Wer ohne Ziel zu weit hinausfliegt,
	 * dreht deshalb wieder heim.
	 *
	 * @return Ersatzziel oder {@code null}, wenn alles in Ordnung ist
	 */
	@Nullable
	private Vec3d stayInRange() {
		if (homeBase == null) {
			return null;
		}
		// Wandert das Gelaende mit, gibt es keinen Grund mehr umzudrehen -
		// die Maschine nimmt ihren gerechneten Bereich einfach mit.
		if (F47Config.get().missionChunkRadius > 0) {
			return null;
		}
		// Der Force-Load deckt ein Quadrat ab; der eingeschriebene Kreis ist
		// die sichere Grenze. Etwas Rand bleibt fuer den Kurvenausgang.
		double loaded = (F47Config.get().baseForceLoadRadiusChunks * 2 + 1) * 8.0 - 24.0;
		// Mit Ziel darf sie weiter - das Ziel selbst tickt ja, liegt also
		// seinerseits in geladenem Gelaende.
		// Mit Ziel oder bekanntem Gegner darf sie weiter - beide ticken ja, ihr
		// Gelaende ist also gerechnet.
		boolean pursuing = (currentTarget != null && currentTarget.isAlive()) || knownEnemy != null;
		double limit = pursuing ? loaded * 2.6 : loaded;
		if (squaredHorizontalDistance(homeBase) < limit * limit) {
			return null;
		}
		return new Vec3d(homeBase.getX(), patrolAltitude, homeBase.getZ());
	}

	/**
	 * Schubstellung, die eine Wunschgeschwindigkeit haelt.
	 *
	 * <p>Ein Kampfflugzeug hat viel mehr Schub als Widerstand - mit festem
	 * Vollgas rennt es einfach bis zur Hoechstgeschwindigkeit davon und kann
	 * dann nicht mehr eng genug kurven. Ein echter Autopilot regelt deshalb
	 * auf eine Sollgeschwindigkeit, und genau das passiert hier.
	 */
	private float throttleForSpeed(double targetMs) {
		double speed = getVelocity().length() * FlightModel.TICKS_PER_SECOND;
		double error = targetMs - speed;
		return MathHelper.clamp((float) (0.30 + error * 0.02), 0.05f, 1.0f);
	}

	private double squaredHorizontalDistance(BlockPos pos) {
		double dx = getX() - (pos.getX() + 0.5);
		double dz = getZ() - (pos.getZ() + 0.5);
		return dx * dx + dz * dz;
	}

	/**
	 * Groesster Anstellwinkel, den sich der Bot-Pilot erlaubt, in Grad.
	 *
	 * <p>Deutlich unter dem kritischen Winkel von zwanzig Grad - ein echter
	 * Pilot fliegt nicht an der Abrisskante entlang, und ein Bot, der die Nase
	 * einfach auf sein Ziel richtet, wuerde beim Steigflug sofort ueberziehen.
	 */
	private static final float SAFE_ANGLE_OF_ATTACK = 13.0f;

	/**
	 * Radius der Warteschleife in Bloecken. Muss in den dauerhaft geladenen
	 * Bereich passen, sonst friert die Maschine ausserhalb ein.
	 */
	private static final double PATROL_RADIUS = 70.0;
	/**
	 * Geschwindigkeit auf der Warteschleife in Metern je Sekunde.
	 *
	 * <p>Rund 250 km/h - langsam fuer einen Jet, aber genau das fliegen echte
	 * Bereitschaftsmuster auch. Bei diesem Tempo laesst sich der Kreis oben
	 * mit knapp sieben g ausfliegen; schneller ginge nur mit groesserem
	 * Radius, und der passt nicht mehr ins geladene Gebiet. Im Angriff wird
	 * ohnehin auf Vollgas gegangen.
	 */
	private static final double PATROL_SPEED = 70.0;
	/**
	 * Hoechstgeschwindigkeit im Angriff, in Metern je Sekunde.
	 *
	 * <p>Rund 400 km/h - deutlich unter dem, was die Maschine koennte, und das
	 * mit Absicht. Bei voller Fahrt braucht eine Kurve nach der echten Formel
	 * fast tausend Bloecke Radius; das geladene Gebiet ist achtzig. Ein Bot
	 * mit Vollgas fliegt seine Kurve also weit hinaus, verlaesst den
	 * gerechneten Bereich und bleibt dort stehen. Der Spieler hat dieses
	 * Problem nicht: Um ihn herum laedt Minecraft ohnehin staendig nach, er
	 * darf die vollen 1060 km/h fliegen.
	 */
	private static final double ATTACK_SPEED = 110.0;
	/**
	 * Rotationsgeschwindigkeit in Metern je Sekunde - erst ab hier wird die
	 * Nase gehoben.
	 *
	 * <p>Rechnerisch traegt die Flaeche bei dreizehn Grad Anstellwinkel ab
	 * dreiunddreissig Metern je Sekunde. Viel mehr Reserve waere teuer: Der
	 * Anlauf bis hierher braucht schon rund sechsunddreissig Meter, und das
	 * Hochziehen selbst kostet nochmal knapp zwanzig.
	 */
	private static final double ROTATE_SPEED = 36.0;
	/**
	 * Umkreis der weitraeumigen Gegnersuche in Bloecken. Deckt einen
	 * Nachbarstuetzpunkt ab, ohne die halbe Welt abzusuchen.
	 */
	private static final double SWEEP_RANGE = 320.0;
	/** Mindestabstand ueber Gelaende, das voraus liegt, in Bloecken. */
	private static final double TERRAIN_CLEARANCE = 22.0;

	/** Richtet Nase und Querlage auf einen Punkt aus. */
	private void steerTowards(Vec3d goal) {
		Vec3d delta = goal.subtract(getPos());
		double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
		float targetYaw = (float) (MathHelper.atan2(delta.z, delta.x) * MathHelper.DEGREES_PER_RADIAN) - 90.0f;
		float targetPitch = (float) (-MathHelper.atan2(delta.y, horizontal) * MathHelper.DEGREES_PER_RADIAN);

		// Die Nase darf nur so weit ueber die Flugbahn, wie die Stroemung
		// mitmacht. Wer steiler zieht, haengt an der Abrisskante und faellt
		// durch - was bei einer stehenden Maschine auf der Bahn sofort
		// passieren wuerde, weil das Ziel hoch ueber ihr liegt.
		Vec3d motion = getVelocity();
		double alongGround = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
		float flightPath = (float) (-MathHelper.atan2(motion.y, alongGround)
				* MathHelper.DEGREES_PER_RADIAN);
		targetPitch = MathHelper.clamp(targetPitch,
				flightPath - SAFE_ANGLE_OF_ATTACK, flightPath + SAFE_ANGLE_OF_ATTACK);
		targetPitch = MathHelper.clamp(targetPitch, -60.0f, 60.0f);

		// Zuegig genug, dass das Hochziehen nicht die halbe Bahn kostet.
		float rate = 0.18f;
		float newYaw = approachAngle(getYaw(), targetYaw, rate);
		float yawDelta = MathHelper.wrapDegrees(newYaw - getYaw());
		setYaw(newYaw);
		setPitch(approachAngle(getPitch(), targetPitch, rate));

		// Querlage nach Fahrt begrenzen: Eine langsame Maschine kann eine
		// steile Kurve gar nicht ausfliegen, sie wuerde nur querschieben.
		double speed = getVelocity().length() * FlightModel.TICKS_PER_SECOND;
		float maxBank = MathHelper.clamp((float) (speed - 40.0) * 1.6f, 5.0f, 65.0f);
		float wanted = MathHelper.clamp(yawDelta * 9.0f, -maxBank, maxBank);
		setRoll(getRoll() + (wanted - getRoll()) * 0.15f);
	}

	/** Statt eines Piloten richtet der Bot die Maschine auf den Zielpunkt aus. */
	@Override
	protected void steer() {
		if (currentGoal != null && hasPilot() && getOrder() != Order.PARKED) {
			steerTowards(currentGoal);
		}
	}

	@Nullable
	private Entity findTarget() {
		// Auch am Boden wird gesucht: Der Alarmstart haengt daran, dass eine
		// wartende Maschine den Gegner ueberhaupt bemerkt. Ohne das konnte er
		// nie ausloesen - beim Anflug einer feindlichen Staffel blieb die
		// halbe Basis stehen und schaute zu.
		if (getOrder() == Order.RTB) {
			return null;
		}
		double range = radarRange();
		Box box = getBoundingBox().expand(range);
		List<Entity> threats = getWorld().getOtherEntities(this, box, candidate -> Iff.isEnemy(this, candidate));
		Entity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Entity threat : threats) {
			if (!Iff.canDetect(threat, getPos(), range) || !isWorthAttacking(threat)) {
				continue;
			}
			double distance = threat.squaredDistanceTo(this);
			// Luftziele haben Vorrang vor Bodenzielen.
			if (Iff.isAirThreat(getTeam(), threat)) {
				distance *= 0.4;
			}
			if (distance < bestDistance) {
				bestDistance = distance;
				best = threat;
			}
		}
		return best;
	}

	/** Waffeneinsatz gegen das aktuelle Ziel. */
	protected void engage(Entity target, double distance) {
		if (weaponTimer > 0) {
			return;
		}
		double alignment = toTargetAlignment(target);

		// Der Suchkopf sieht weit - enger als noetig zu zielen heisst, nie zu
		// schiessen: Der Bot fliegt seinen Zielpunkt bewusst etwas ueber dem
		// Gegner an, um nicht hineinzufliegen, und lag damit staendig knapp
		// ausserhalb des alten Kegels von sechsundzwanzig Grad.
		if (distance < 120 && distance > 14 && alignment > 0.78 && getMissileAmmo() > 0) {
			setLockedTarget(target);
			fireWeapon(WeaponType.MISSILE);
			weaponTimer = 70;
		} else if (distance < 60 && alignment > 0.94 && getCannonAmmo() > 0) {
			setLockedTarget(target);
			fireWeapon(WeaponType.CANNON);
			weaponTimer = 3;
		}
	}

	private void announce(String key) {
		PlayerEntity owner = getOwner();
		if (owner != null) {
			owner.sendMessage(Text.translatable(key, getCallsign()), false);
			owner.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), 0.4f, 1.5f);
		}
	}

	/** Naechster Befehl beim Durchschalten mit dem Kommando-Tablet. */
	public Order cycleOrder() {
		Order next = switch (getOrder()) {
			case PARKED -> Order.TAKEOFF;
			case TAKEOFF -> Order.PATROL;
			case PATROL -> Order.ESCORT;
			case ESCORT -> Order.ATTACK;
			case ATTACK -> Order.RTB;
			case RTB -> Order.PARKED;
		};
		setOrder(next);
		return next;
	}

	// ------------------------------------------------------------------
	// Haken fuer abgeleitete Muster (Bomber, Transporter)
	// ------------------------------------------------------------------

	/** Ortungsreichweite dieser Maschine. Der Bomber sieht weiter. */
	protected double radarRange() {
		return F47Config.get().jetRadarRange;
	}

	/**
	 * Lohnt sich dieses Ziel? Ein Jaeger nimmt alles, ein Bomber nur
	 * Bodenziele, ein Transporter gar nichts.
	 */
	protected boolean isWorthAttacking(Entity candidate) {
		return true;
	}

	/** Wie genau zeigt die Nase auf das Ziel? 1 heisst genau darauf. */
	protected double toTargetAlignment(Entity target) {
		Vec3d toTarget = target.getPos().add(0, target.getHeight() * 0.5, 0).subtract(getPos());
		return toTarget.lengthSquared() < 1.0e-6
				? 0.0
				: toTarget.normalize().dotProduct(getForwardVector());
	}

	protected int getWeaponTimer() {
		return weaponTimer;
	}

	protected void setWeaponTimer(int ticks) {
		weaponTimer = ticks;
	}

	/** Warteschleifenhoehe setzen - Bomber kreisen hoeher als Jaeger. */
	protected void setPatrolAltitude(int altitude) {
		patrolAltitude = altitude;
	}
}
