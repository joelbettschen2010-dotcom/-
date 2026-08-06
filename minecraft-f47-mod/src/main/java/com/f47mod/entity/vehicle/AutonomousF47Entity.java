package com.f47mod.entity.vehicle;

import com.f47mod.F47Config;
import com.f47mod.util.Iff;
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
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Autonome F-47 - fliegt ohne Spieler im Cockpit. Gesteuert wird sie von einem
 * einfachen Zustandsautomaten ("Bot-Pilot"), der Kurs, Schub und Waffeneinsatz
 * selbst bestimmt. Befehle kommen ueber das Kommando-Tablet.
 */
public class AutonomousF47Entity extends F47Entity {
	private static final TrackedData<Integer> ORDER =
			DataTracker.registerData(AutonomousF47Entity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<String> CALLSIGN =
			DataTracker.registerData(AutonomousF47Entity.class, TrackedDataHandlerRegistry.STRING);
	private static final TrackedData<Boolean> HAS_PILOT =
			DataTracker.registerData(AutonomousF47Entity.class, TrackedDataHandlerRegistry.BOOLEAN);

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
		setCustomName(Text.literal(callsign));
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
		homeBase = pos;
		patrolAltitude = Math.max(pos.getY() + 35, 75);
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
		flyMission();
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
				goal = new Vec3d(base.getX(), patrolAltitude, base.getZ())
						.add(getForwardVector().multiply(60));
				throttle = 1.0f;
				setAfterburner(true);
				if (getY() > patrolAltitude - 8) {
					setAfterburner(false);
					setOrder(Order.PATROL);
					announce("message.f47.on_station");
				}
			}
			case ESCORT -> {
				PlayerEntity owner = getOwner();
				if (owner == null) {
					goal = patrolPoint();
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
					throttle = 1.0f;
					setAfterburner(distance > 70);
					engage(currentTarget, distance);
				} else {
					goal = patrolPoint();
					setAfterburner(false);
				}
			}
			case RTB -> {
				BlockPos base = homeBase != null ? homeBase : getBlockPos();
				double horizontal = Math.sqrt(squaredHorizontalDistance(base));
				if (horizontal < 24) {
					// Sinkflug bis zum Aufsetzen, dann parken.
					goal = new Vec3d(base.getX(), base.getY() + 1.5, base.getZ());
					throttle = 0.22f;
					if (isOnGround() || getY() - base.getY() < 2.5) {
						setOrder(Order.PARKED);
						setThrottle(0.0f);
						setVelocity(Vec3d.ZERO);
						announce("message.f47.landed");
						return;
					}
				} else {
					goal = new Vec3d(base.getX(), Math.max(base.getY() + 25, patrolAltitude - 15), base.getZ());
					throttle = 0.7f;
				}
			}
			default -> {
				goal = patrolPoint();
				if (currentTarget != null && currentTarget.isAlive()) {
					setOrder(Order.ATTACK);
					announce("message.f47.engaging");
				}
			}
		}

		// Nur den Plan festlegen - geflogen wird im gemeinsamen Flugmodell,
		// das steer() aufruft. So laeuft die Physik genau einmal pro Tick.
		currentGoal = goal;
		setThrottle(throttle);
	}

	/** Kreisbahn um die Basis. */
	private Vec3d patrolPoint() {
		BlockPos base = homeBase != null ? homeBase : getBlockPos();
		patrolAngle += 0.02;
		double radius = 55.0;
		return new Vec3d(
				base.getX() + Math.cos(patrolAngle) * radius,
				patrolAltitude,
				base.getZ() + Math.sin(patrolAngle) * radius);
	}

	private double squaredHorizontalDistance(BlockPos pos) {
		double dx = getX() - (pos.getX() + 0.5);
		double dz = getZ() - (pos.getZ() + 0.5);
		return dx * dx + dz * dz;
	}

	/** Richtet Nase und Querlage auf einen Punkt aus. */
	private void steerTowards(Vec3d goal) {
		Vec3d delta = goal.subtract(getPos());
		double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
		float targetYaw = (float) (MathHelper.atan2(delta.z, delta.x) * MathHelper.DEGREES_PER_RADIAN) - 90.0f;
		float targetPitch = (float) (-MathHelper.atan2(delta.y, horizontal) * MathHelper.DEGREES_PER_RADIAN);
		targetPitch = MathHelper.clamp(targetPitch, -55.0f, 55.0f);

		float rate = 0.09f;
		float newYaw = approachAngle(getYaw(), targetYaw, rate);
		float yawDelta = MathHelper.wrapDegrees(newYaw - getYaw());
		setYaw(newYaw);
		setPitch(approachAngle(getPitch(), targetPitch, rate));
		setRoll(getRoll() + (MathHelper.clamp(yawDelta * 9.0f, -65.0f, 65.0f) - getRoll()) * 0.15f);
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
		Order order = getOrder();
		if (order == Order.PARKED || order == Order.RTB) {
			return null;
		}
		double range = F47Config.get().jetRadarRange;
		Box box = getBoundingBox().expand(range);
		List<Entity> threats = getWorld().getOtherEntities(this, box, Iff::isThreat);
		Entity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Entity threat : threats) {
			if (!Iff.canDetect(threat, getPos(), range)) {
				continue;
			}
			double distance = threat.squaredDistanceTo(this);
			// Luftziele haben Vorrang vor Bodenzielen.
			if (Iff.isAirThreat(threat)) {
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
	private void engage(Entity target, double distance) {
		if (weaponTimer > 0) {
			return;
		}
		Vec3d toTarget = target.getPos().add(0, target.getHeight() * 0.5, 0).subtract(getPos());
		double alignment = toTarget.normalize().dotProduct(getForwardVector());

		if (distance < 110 && distance > 18 && alignment > 0.90 && getMissileAmmo() > 0) {
			setLockedTarget(target);
			fireWeapon(WeaponType.MISSILE);
			weaponTimer = 70;
		} else if (distance < 55 && alignment > 0.97 && getCannonAmmo() > 0) {
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
}
