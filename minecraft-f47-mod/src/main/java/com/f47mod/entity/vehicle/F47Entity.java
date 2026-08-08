package com.f47mod.entity.vehicle;

import com.f47mod.F47Config;
import com.f47mod.registry.ModDamage;
import com.f47mod.registry.ModItems;
import com.f47mod.entity.projectile.BombEntity;
import com.f47mod.entity.projectile.BulletEntity;
import com.f47mod.entity.projectile.MissileEntity;
import com.f47mod.util.Iff;
import com.f47mod.util.Team;
import com.f47mod.util.TeamMember;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/**
 * Die F-47 - ein bemannbarer Kampfjet mit eigenem Flugmodell.
 *
 * <p>Die Physik laeuft immer nur auf einer Seite: sitzt ein Spieler im Cockpit,
 * rechnet dessen Client (damit die Steuerung ohne Verzoegerung reagiert) und
 * schickt die Position ueber das normale Fahrzeug-Paket an den Server. Ohne
 * Pilot - also bei den autonomen Jets - rechnet der Server.
 */
public class F47Entity extends Entity implements TeamMember {
	protected static final TrackedData<Float> THROTTLE =
			DataTracker.registerData(F47Entity.class, TrackedDataHandlerRegistry.FLOAT);
	protected static final TrackedData<Float> STRUCTURE =
			DataTracker.registerData(F47Entity.class, TrackedDataHandlerRegistry.FLOAT);
	protected static final TrackedData<Float> FUEL =
			DataTracker.registerData(F47Entity.class, TrackedDataHandlerRegistry.FLOAT);
	protected static final TrackedData<Float> ROLL =
			DataTracker.registerData(F47Entity.class, TrackedDataHandlerRegistry.FLOAT);
	protected static final TrackedData<Boolean> STEALTH =
			DataTracker.registerData(F47Entity.class, TrackedDataHandlerRegistry.BOOLEAN);
	protected static final TrackedData<Boolean> AFTERBURNER =
			DataTracker.registerData(F47Entity.class, TrackedDataHandlerRegistry.BOOLEAN);
	protected static final TrackedData<Integer> MISSILE_AMMO =
			DataTracker.registerData(F47Entity.class, TrackedDataHandlerRegistry.INTEGER);
	protected static final TrackedData<Integer> CANNON_AMMO =
			DataTracker.registerData(F47Entity.class, TrackedDataHandlerRegistry.INTEGER);
	protected static final TrackedData<Integer> BOMB_AMMO =
			DataTracker.registerData(F47Entity.class, TrackedDataHandlerRegistry.INTEGER);
	protected static final TrackedData<Integer> WEAPON =
			DataTracker.registerData(F47Entity.class, TrackedDataHandlerRegistry.INTEGER);
	protected static final TrackedData<Integer> LOCKED_TARGET =
			DataTracker.registerData(F47Entity.class, TrackedDataHandlerRegistry.INTEGER);
	protected static final TrackedData<Integer> STEALTH_BREAK =
			DataTracker.registerData(F47Entity.class, TrackedDataHandlerRegistry.INTEGER);
	protected static final TrackedData<Integer> TEAM =
			DataTracker.registerData(F47Entity.class, TrackedDataHandlerRegistry.INTEGER);

	public static final int MAX_MISSILES = 8;
	public static final int MAX_CANNON = 480;
	public static final int MAX_BOMBS = 4;

	/** Restliche Interpolationsschritte fuer Jets, die woanders gerechnet werden. */
	private int lerpTicks;
	private double lerpX;
	private double lerpY;
	private double lerpZ;
	private double lerpYaw;
	private double lerpPitch;

	private int weaponCooldown;
	private int lockTimer;

	/** Aufschlagprotokoll, ueber die Umgebungsvariable F47_DEBUG geschaltet. */
	private static final boolean DEBUG = System.getenv("F47_DEBUG") != null;

	/**
	 * Direkte Steuereingaben, wie sie ein Joystick liefert (-1..1). Werden vom
	 * Client jeden Tick gesetzt und nicht gespeichert - sie beschreiben nur die
	 * augenblickliche Knueppelstellung.
	 */
	private float inputPitch;
	private float inputRoll;
	private float inputYaw;
	/** true = Knueppelsteuerung, false = der Jet folgt der Blickrichtung. */
	private boolean directControl;
	/** Anstellwinkel des letzten Ticks in Radiant - fuer Anzeige und Bot-Piloten. */
	private float angleOfAttack;

	public F47Entity(EntityType<? extends F47Entity> type, World world) {
		super(type, world);
		this.intersectionChecked = true;
	}

	// ------------------------------------------------------------------
	// Datenhaltung
	// ------------------------------------------------------------------

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(THROTTLE, 0.0f);
		builder.add(STRUCTURE, F47Config.get().jetMaxHealth);
		builder.add(FUEL, F47Config.get().maxFuel);
		builder.add(ROLL, 0.0f);
		builder.add(STEALTH, false);
		builder.add(AFTERBURNER, false);
		builder.add(MISSILE_AMMO, MAX_MISSILES);
		builder.add(CANNON_AMMO, MAX_CANNON);
		builder.add(BOMB_AMMO, MAX_BOMBS);
		builder.add(WEAPON, 0);
		builder.add(LOCKED_TARGET, -1);
		builder.add(STEALTH_BREAK, 0);
		builder.add(TEAM, Team.BLUE.ordinal());
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		setThrottle(nbt.getFloat("Throttle"));
		setStructure(nbt.contains("Structure") ? nbt.getFloat("Structure") : F47Config.get().jetMaxHealth);
		setFuel(nbt.contains("Fuel") ? nbt.getFloat("Fuel") : F47Config.get().maxFuel);
		setStealth(nbt.getBoolean("Stealth"));
		dataTracker.set(MISSILE_AMMO, nbt.contains("Missiles") ? nbt.getInt("Missiles") : MAX_MISSILES);
		dataTracker.set(CANNON_AMMO, nbt.contains("Cannon") ? nbt.getInt("Cannon") : MAX_CANNON);
		dataTracker.set(BOMB_AMMO, nbt.contains("Bombs") ? nbt.getInt("Bombs") : MAX_BOMBS);
		dataTracker.set(WEAPON, nbt.getInt("Weapon"));
		setTeam(Team.byOrdinal(nbt.getInt("Team")));
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.putFloat("Throttle", getThrottle());
		nbt.putFloat("Structure", getStructure());
		nbt.putFloat("Fuel", getFuel());
		nbt.putBoolean("Stealth", isStealthOn());
		nbt.putInt("Missiles", getMissileAmmo());
		nbt.putInt("Cannon", getCannonAmmo());
		nbt.putInt("Bombs", getBombAmmo());
		nbt.putInt("Weapon", dataTracker.get(WEAPON));
		nbt.putInt("Team", getTeam().ordinal());
	}

	@Override
	public Team getTeam() {
		return Team.byOrdinal(dataTracker.get(TEAM));
	}

	@Override
	public void setTeam(Team team) {
		dataTracker.set(TEAM, team.ordinal());
	}

	/**
	 * Knueppelstellung uebernehmen. Bei directControl steuert der Spieler
	 * unmittelbar Hoehen-, Quer- und Seitenruder - so, wie es sich mit einem
	 * Joystick gehoert. Ohne directControl folgt der Jet der Blickrichtung.
	 */
	public void setControlAxes(float pitch, float roll, float yaw, boolean direct) {
		this.inputPitch = MathHelper.clamp(pitch, -1.0f, 1.0f);
		this.inputRoll = MathHelper.clamp(roll, -1.0f, 1.0f);
		this.inputYaw = MathHelper.clamp(yaw, -1.0f, 1.0f);
		this.directControl = direct;
	}

	public boolean isDirectControl() {
		return directControl;
	}

	public float getThrottle() {
		return dataTracker.get(THROTTLE);
	}

	public void setThrottle(float throttle) {
		dataTracker.set(THROTTLE, MathHelper.clamp(throttle, 0.0f, 1.0f));
	}

	public float getStructure() {
		return dataTracker.get(STRUCTURE);
	}

	public void setStructure(float structure) {
		dataTracker.set(STRUCTURE, MathHelper.clamp(structure, 0.0f, F47Config.get().jetMaxHealth));
	}

	public float getFuel() {
		return dataTracker.get(FUEL);
	}

	public void setFuel(float fuel) {
		dataTracker.set(FUEL, MathHelper.clamp(fuel, 0.0f, F47Config.get().maxFuel));
	}

	public float getRoll() {
		return dataTracker.get(ROLL);
	}

	public void setRoll(float roll) {
		dataTracker.set(ROLL, roll);
	}

	public boolean isStealthOn() {
		return dataTracker.get(STEALTH);
	}

	public void setStealth(boolean stealth) {
		dataTracker.set(STEALTH, stealth);
	}

	/** Stealth wirkt nur, wenn gerade nicht geschossen wurde (Waffenschacht zu). */
	public boolean isStealthEffective() {
		return isStealthOn() && dataTracker.get(STEALTH_BREAK) <= 0;
	}

	public boolean isAfterburner() {
		return dataTracker.get(AFTERBURNER);
	}

	public void setAfterburner(boolean on) {
		dataTracker.set(AFTERBURNER, on);
	}

	public int getMissileAmmo() {
		return dataTracker.get(MISSILE_AMMO);
	}

	public int getCannonAmmo() {
		return dataTracker.get(CANNON_AMMO);
	}

	public int getBombAmmo() {
		return dataTracker.get(BOMB_AMMO);
	}

	public WeaponType getWeapon() {
		return WeaponType.byOrdinal(dataTracker.get(WEAPON));
	}

	public void setWeapon(WeaponType weapon) {
		dataTracker.set(WEAPON, weapon.ordinal());
	}

	public void cycleWeapon() {
		setWeapon(getWeapon().next());
	}

	public int getAmmo(WeaponType weapon) {
		return switch (weapon) {
			case CANNON -> getCannonAmmo();
			case MISSILE -> getMissileAmmo();
			case BOMB -> getBombAmmo();
			case LASER -> 999;
		};
	}

	@Nullable
	public Entity getLockedTarget() {
		int id = dataTracker.get(LOCKED_TARGET);
		return id < 0 ? null : getWorld().getEntityById(id);
	}

	public void setLockedTarget(@Nullable Entity entity) {
		dataTracker.set(LOCKED_TARGET, entity == null ? -1 : entity.getId());
	}

	/** Voll aufgetankt, repariert und bewaffnet - vom Wartungsfeld benutzt. */
	public boolean rearmAndRefuel() {
		boolean changed = false;
		F47Config config = F47Config.get();
		if (getFuel() < config.maxFuel) {
			setFuel(getFuel() + 12.0f);
			changed = true;
		}
		if (getStructure() < config.jetMaxHealth) {
			setStructure(getStructure() + 0.35f);
			changed = true;
		}
		if (getMissileAmmo() < MAX_MISSILES && age % 40 == 0) {
			dataTracker.set(MISSILE_AMMO, getMissileAmmo() + 1);
			changed = true;
		}
		if (getCannonAmmo() < MAX_CANNON) {
			dataTracker.set(CANNON_AMMO, Math.min(MAX_CANNON, getCannonAmmo() + 4));
			changed = true;
		}
		if (getBombAmmo() < MAX_BOMBS && age % 60 == 0) {
			dataTracker.set(BOMB_AMMO, getBombAmmo() + 1);
			changed = true;
		}
		return changed;
	}

	public void repair(float amount) {
		setStructure(getStructure() + amount);
	}

	public void refuel(float amount) {
		setFuel(getFuel() + amount);
	}

	// ------------------------------------------------------------------
	// Grundverhalten einer Entity
	// ------------------------------------------------------------------

	@Override
	public boolean isCollidable() {
		return true;
	}

	@Override
	public boolean canHit() {
		return !isRemoved();
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean isFireImmune() {
		return true;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return getPassengerList().isEmpty();
	}

	@Override
	@Nullable
	public LivingEntity getControllingPassenger() {
		return getFirstPassenger() instanceof LivingEntity living ? living : null;
	}

	@Override
	public ActionResult interact(PlayerEntity player, Hand hand) {
		if (player.shouldCancelInteraction()) {
			return ActionResult.PASS;
		}
		ItemStack stack = player.getStackInHand(hand);
		if (stack.isOf(ModItems.JET_FUEL)) {
			if (getFuel() < F47Config.get().maxFuel) {
				refuel(1200.0f);
				if (!player.getAbilities().creativeMode) {
					stack.decrement(1);
				}
				playSound(SoundEvents.ITEM_BUCKET_EMPTY, 0.8f, 1.0f);
				return ActionResult.SUCCESS;
			}
			return ActionResult.PASS;
		}
		if (getWorld().isClient) {
			return ActionResult.SUCCESS;
		}
		return player.startRiding(this) ? ActionResult.CONSUME : ActionResult.PASS;
	}

	@Override
	protected void updatePassengerPosition(Entity passenger, PositionUpdater positionUpdater) {
		if (!hasPassenger(passenger)) {
			return;
		}
		// Sitzposition leicht vor der Mitte, damit die Sicht aus dem Cockpit kommt.
		Vec3d offset = new Vec3d(0.0, 0.15, 0.55).rotateY(-getYaw() * MathHelper.RADIANS_PER_DEGREE);
		positionUpdater.accept(passenger, getX() + offset.x, getY() + offset.y, getZ() + offset.z);
	}

	@Override
	public Vec3d updatePassengerForDismount(LivingEntity passenger) {
		Vec3d side = new Vec3d(2.2, 0.0, 0.0).rotateY(-getYaw() * MathHelper.RADIANS_PER_DEGREE);
		return new Vec3d(getX() + side.x, getY() + 0.5, getZ() + side.z);
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		if (isRemoved() || getWorld().isClient) {
			return false;
		}
		if (source.getAttacker() instanceof PlayerEntity player && player.getAbilities().creativeMode
				&& player.isSneaking()) {
			discard();
			return true;
		}
		// Geschosse der eigenen Partei beschaedigen den Jet nicht.
		if (source.getSource() != null && Iff.isAlly(this, source.getSource())) {
			return false;
		}
		setStructure(getStructure() - amount);
		playSound(SoundEvents.ENTITY_IRON_GOLEM_DAMAGE, 0.7f, 1.4f);
		if (getStructure() <= 0.0f) {
			explode();
		}
		return true;
	}

	public void explode() {
		if (getWorld().isClient) {
			return;
		}
		for (Entity passenger : getPassengerList()) {
			passenger.stopRiding();
		}
		getWorld().createExplosion(this, getX(), getY(), getZ(), 3.5f, F47Config.get().explosionType());
		if (getWorld() instanceof ServerWorld server) {
			server.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY(), getZ(), 2, 1.5, 1.0, 1.5, 0.0);
		}
		discard();
	}

	// ------------------------------------------------------------------
	// Tick
	// ------------------------------------------------------------------

	@Override
	public void tick() {
		super.tick();

		if (weaponCooldown > 0) {
			weaponCooldown--;
		}
		int stealthBreak = dataTracker.get(STEALTH_BREAK);
		if (stealthBreak > 0 && !getWorld().isClient) {
			dataTracker.set(STEALTH_BREAK, stealthBreak - 1);
		}

		// Erst entscheiden (Treibstoff, Zielerfassung, Auftrag der Bot-Piloten),
		// dann fliegen - so wirkt eine Entscheidung noch im selben Tick.
		if (!getWorld().isClient) {
			serverTick();
			if (isRemoved()) {
				return;
			}
		}

		if (isSimulationSide()) {
			updateFlight();
		} else {
			interpolate();
		}

		if (getWorld().isClient) {
			clientEffects();
		}

		// Passagiere werden bei einem Aufprall aus dem Flugzeug geworfen.
		if (isRemoved()) {
			return;
		}
		checkBlockCollision();
	}

	/** Zusaetzliche Logik nur auf dem Server: Treibstoff, Zielerfassung, Absturz. */
	protected void serverTick() {
		F47Config config = F47Config.get();
		float burn = getThrottle() * config.fuelBurnPerTick;
		if (isAfterburner()) {
			burn *= config.afterburnerFuelFactor;
		}
		if (isStealthOn()) {
			burn *= config.stealthFuelFactor;
		}
		if (burn > 0) {
			setFuel(getFuel() - burn);
		}
		if (getFuel() <= 0.0f) {
			setAfterburner(false);
			if (getThrottle() > 0.0f && age % 20 == 0) {
				playSound(SoundEvents.BLOCK_FIRE_EXTINGUISH, 0.6f, 0.6f);
			}
		}

		if (++lockTimer >= 5) {
			lockTimer = 0;
			updateLock();
		}

		if (getStructure() <= 0.0f) {
			explode();
		}
	}

	/** Sucht das Ziel, auf das eine Lenkwaffe aufschalten kann. */
	protected void updateLock() {
		F47Config config = F47Config.get();
		Vec3d forward = getForwardVector();
		Vec3d origin = getPos();
		double cos = Math.cos(Math.toRadians(config.missileLockConeDegrees));

		Entity best = null;
		double bestScore = -1.0;
		Box box = getBoundingBox().expand(config.missileLockRange);
		for (Entity candidate : getWorld().getOtherEntities(this, box, candidate -> Iff.isEnemy(this, candidate))) {
			Vec3d toTarget = candidate.getPos().add(0, candidate.getHeight() * 0.5, 0).subtract(origin);
			double distance = toTarget.length();
			if (distance < 3.0 || distance > config.missileLockRange) {
				continue;
			}
			double alignment = toTarget.normalize().dotProduct(forward);
			if (alignment < cos) {
				continue;
			}
			double score = alignment - distance / (config.missileLockRange * 4.0);
			if (score > bestScore) {
				bestScore = score;
				best = candidate;
			}
		}
		Entity previous = getLockedTarget();
		setLockedTarget(best);
		if (best != null && previous != best && getControllingPassenger() instanceof PlayerEntity) {
			playSound(SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), 0.6f, 1.9f);
		}
	}

	protected void clientEffects() {
		if (getThrottle() > 0.05f && getFuel() > 0.0f) {
			Vec3d back = getForwardVector().multiply(-2.1);
			double spread = isAfterburner() ? 0.22 : 0.1;
			getWorld().addParticle(isAfterburner() ? ParticleTypes.FLAME : ParticleTypes.SMOKE,
					getX() + back.x + (random.nextDouble() - 0.5) * spread,
					getY() + back.y + 0.15,
					getZ() + back.z + (random.nextDouble() - 0.5) * spread,
					-getVelocity().x * 0.3, -getVelocity().y * 0.3, -getVelocity().z * 0.3);
		}
		if (getStructure() < F47Config.get().jetMaxHealth * 0.35f && random.nextInt(3) == 0) {
			getWorld().addParticle(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.3, getZ(), 0.0, 0.02, 0.0);
		}
	}

	/**
	 * Wird die Physik auf dieser Seite gerechnet? Mit Pilot rechnet der Client
	 * des Piloten, ohne Pilot der Server.
	 */
	public boolean isSimulationSide() {
		LivingEntity controller = getControllingPassenger();
		if (controller instanceof PlayerEntity player) {
			return player.isMainPlayer();
		}
		return !getWorld().isClient;
	}

	// ------------------------------------------------------------------
	// Flugmodell
	// ------------------------------------------------------------------

	protected void updateFlight() {
		F47Config config = F47Config.get();

		steer();

		Vec3d forward = getForwardVector();
		Vec3d right = getRightVector();
		boolean engineRunning = getFuel() > 0.0f;
		float throttle = engineRunning ? getThrottle() : 0.0f;

		// Der Nachbrenner ist kein Faktor auf den Schub, sondern eine zweite
		// Betriebsstufe - deshalb zwei getrennte Werte.
		double thrust = throttle * (isAfterburner() && engineRunning
				? config.afterburnerNewtons
				: config.thrustNewtons);

		// Rollreibung: auf der Bahn wenig, im Gelaende viel.
		double rolling = isOnRunway() ? 0.02 : 0.12;

		Vec3d velocity = usesFlightAssist()
				? FlightModel.assisted(getVelocity(), forward, thrust, isOnGround(), rolling)
				: FlightModel.step(getVelocity(), forward, right, thrust,
						getY(), isOnGround(), rolling);

		// Anstellwinkel merken - Cockpitwarnung und Bot-Piloten sollen
		// denselben Wert sehen, mit dem die Physik gerechnet hat.
		double speedMs = velocity.length() * FlightModel.TICKS_PER_SECOND;
		Vec3d flow = speedMs > 0.05
				? velocity.normalize()
				: forward;
		angleOfAttack = (float) FlightModel.angleOfAttack(flow, forward, right, speedMs);

		setVelocity(velocity);

		Vec3d before = getPos();
		move(MovementType.SELF, getVelocity());

		// Nur in der Luft gilt eine Kollision als Aufschlag.
		//
		// Am Boden meldet Minecraft beim schnellen Rollen staendig eine
		// waagerechte Kollision - die Trefferbox streift die Kanten der
		// Bahnbloecke unter sich. Wer das als Bruchlandung wertet, nimmt der
		// Maschine jeden Tick drei Viertel ihrer Fahrt: Sie kommt nie auf
		// Geschwindigkeit, bleibt mitten auf der Bahn stehen und kollidiert von
		// da an fuer immer mit sich selbst. Genau daran hing die halbe Staffel
		// bewegungslos fest.
		if (!isOnGround() && (horizontalCollision || verticalCollision)) {
			logImpact("in der Luft angeschlagen", before);
			handleImpact(before);
		}
		// Schnelles Rollen abseits befestigter Flaechen ist eine Bruchlandung.
		if (isOnGround() && !isOnRunway() && getVelocity().horizontalLength() > 1.6) {
			logImpact("abseits der Bahn aufgesetzt", before);
			handleImpact(before);
		}
	}

	/**
	 * Protokolliert einen Aufschlag, wenn die Umgebungsvariable
	 * {@code F47_DEBUG} gesetzt ist. Ohne die Meldung laesst sich nicht
	 * unterscheiden, ob eine Maschine gegen Gelaende geflogen oder abseits der
	 * Bahn aufgesetzt ist - beides sah im Protokoll gleich aus.
	 */
	private void logImpact(String reason, Vec3d before) {
		if (!DEBUG) {
			return;
		}
		// Was steht eigentlich im Weg? Alle festen Bloecke in der Trefferbox.
		StringBuilder blocked = new StringBuilder();
		Box box = getBoundingBox().expand(0.2);
		for (net.minecraft.util.math.BlockPos pos : net.minecraft.util.math.BlockPos.iterate(
				net.minecraft.util.math.BlockPos.ofFloored(box.minX, box.minY, box.minZ),
				net.minecraft.util.math.BlockPos.ofFloored(box.maxX, box.maxY, box.maxZ))) {
			net.minecraft.block.BlockState state = getWorld().getBlockState(pos);
			if (!state.isAir() && !state.getCollisionShape(getWorld(), pos).isEmpty()) {
				blocked.append(state.getBlock()).append('@').append(pos.toShortString()).append(' ');
			}
		}
		com.f47mod.F47Mod.LOGGER.info("[AUFSCHLAG] {} bei {} {} {} - Grund: {}, im Weg: {}",
				getType().getUntranslatedName(),
				String.format("%.1f", getX()), String.format("%.1f", getY()), String.format("%.1f", getZ()),
				reason, blocked.length() == 0 ? "nichts" : blocked.toString());
	}

	/**
	 * Fliegt diese Maschine mit Regelung statt voller Aerodynamik? Nur die
	 * autonomen Jets tun das, und auch die nur, wenn es eingeschaltet ist -
	 * wer selbst im Cockpit sitzt, bekommt immer die echte Physik.
	 */
	protected boolean usesFlightAssist() {
		return false;
	}

	/**
	 * Rechte Tragflaeche als Einheitsvektor, Querlage eingerechnet.
	 *
	 * <p>Ohne die Querlage waere jede Kurve ein reines Gieren - der Auftrieb
	 * zeigt aber immer aus der Maschine heraus, und genau daraus entsteht die
	 * Kurve beim Schraeglagenflug.
	 */
	public Vec3d getRightVector() {
		Vec3d forward = getForwardVector();
		Vec3d level = forward.crossProduct(new Vec3d(0.0, 1.0, 0.0));
		if (level.lengthSquared() < 1.0e-6) {
			// Senkrecht nach oben oder unten - dann gibt die Gierlage die Ebene vor.
			level = new Vec3d(-Math.cos(Math.toRadians(getYaw())), 0.0,
					Math.sin(Math.toRadians(getYaw())));
		}
		level = level.normalize();
		Vec3d up = level.crossProduct(forward).normalize();

		double roll = Math.toRadians(getRoll());
		return level.multiply(Math.cos(roll)).subtract(up.multiply(Math.sin(roll))).normalize();
	}

	/** Anstellwinkel des letzten Ticks, in Radiant. */
	public float getAngleOfAttack() {
		return angleOfAttack;
	}

	/** Ueberzieht die Maschine gerade? */
	public boolean isStalled() {
		return !isOnGround() && FlightModel.isStalled(angleOfAttack);
	}

	/** Standardsteuerung: Der Jet folgt der Blickrichtung des Piloten. */
	protected void steer() {
		LivingEntity controller = getControllingPassenger();
		if (controller == null) {
			return;
		}
		if (directControl) {
			steerByStick();
			return;
		}
		F47Config config = F47Config.get();
		float targetYaw = controller.getYaw();
		float targetPitch = MathHelper.clamp(controller.getPitch(), -80.0f, 80.0f);
		// Ruderwirkung waechst mit dem Staudruck: Im Langsamflug greift wenig,
		// bei Fahrt folgt die Maschine zuegig.
		float rate = MathHelper.clamp((float) (getVelocity().length()
				* FlightModel.TICKS_PER_SECOND / 90.0), 0.06f, 0.30f);

		float newYaw = approachAngle(getYaw(), targetYaw, rate);
		float yawDelta = MathHelper.wrapDegrees(newYaw - getYaw());
		setYaw(newYaw);
		setPitch(approachAngle(getPitch(), targetPitch, rate));

		// Sichtbare Querlage: Der Jet legt sich in die Kurve.
		float targetRoll = MathHelper.clamp(yawDelta * 9.0f, -70.0f, 70.0f);
		setRoll(getRoll() + (targetRoll - getRoll()) * 0.18f);
	}

	/**
	 * Knueppelsteuerung wie im echten Flugzeug: Der Stick legt die Maschine in
	 * die Querlage, das Ziehen bringt sie dann in die Kurve. Wer nur zieht,
	 * fliegt einen Looping - wer erst rollt und dann zieht, fliegt eine Kurve.
	 *
	 * <p>Bei wenig Fahrt greifen die Ruder schlechter, genau wie in echt.
	 */
	protected void steerByStick() {
		F47Config config = F47Config.get();
		// Ruderwirkung haengt am Staudruck, nicht an einer festen Kennzahl:
		// Im Langsamflug sind die Ruder weich, mit Fahrt beissen sie zu.
		double speedMs = getVelocity().length() * FlightModel.TICKS_PER_SECOND;
		float authority = MathHelper.clamp((float) (speedMs / 120.0), 0.12f, 1.0f);

		// Grad pro Tick bei vollem Ausschlag.
		float pitchRate = config.pitchRateDegrees * authority;
		float rollRate = config.rollRateDegrees * authority;
		float yawRate = 1.1f * authority;

		// Querruder: Der Stick steuert die Rollrate, nicht die Lage selbst.
		float roll = MathHelper.wrapDegrees(getRoll() + inputRoll * rollRate);
		if (Math.abs(inputRoll) < 0.02f) {
			// Ohne Eingabe richtet sich die Maschine langsam von selbst auf.
			roll *= 0.985f;
		}
		setRoll(roll);

		// Hoehenruder wirkt in der geneigten Ebene: Bei Querlage wird aus dem
		// Ziehen eine Kurve statt eines Steigflugs.
		double rollRadians = Math.toRadians(getRoll());
		float pitchInput = -inputPitch * pitchRate;
		float deltaPitch = (float) (pitchInput * Math.cos(rollRadians));
		float deltaYaw = (float) (-pitchInput * Math.sin(rollRadians)) + inputYaw * yawRate;

		setPitch(MathHelper.clamp(getPitch() + deltaPitch, -88.0f, 88.0f));
		setYaw(getYaw() + deltaYaw);
	}

	protected static float approachAngle(float current, float target, float rate) {
		float delta = MathHelper.wrapDegrees(target - current);
		return current + delta * MathHelper.clamp(rate, 0.0f, 1.0f);
	}

	/** Aufprall auf Gelaende - abhaengig vom Tempo gibt es Struktur-Schaden. */
	protected void handleImpact(Vec3d before) {
		double speed = getVelocity().length();
		if (speed < 0.45) {
			return;
		}
		float damage = (float) (speed * speed * 3.2);
		setStructure(getStructure() - damage);
		setVelocity(getVelocity().multiply(0.25));
		playSound(SoundEvents.ENTITY_IRON_GOLEM_DAMAGE, 1.0f, 0.7f);
		if (getWorld() instanceof ServerWorld server) {
			server.spawnParticles(ParticleTypes.LARGE_SMOKE, getX(), getY(), getZ(), 12, 0.6, 0.4, 0.6, 0.02);
		}
		if (getStructure() <= 0.0f) {
			explode();
		}
	}

	protected boolean isOnRunway() {
		return com.f47mod.block.RunwayBlock.isRunway(
				getWorld().getBlockState(getBlockPos().down()));
	}

	public Vec3d getForwardVector() {
		return Vec3d.fromPolar(getPitch(), getYaw()).normalize();
	}

	private void interpolate() {
		if (lerpTicks <= 0) {
			return;
		}
		double x = getX() + (lerpX - getX()) / lerpTicks;
		double y = getY() + (lerpY - getY()) / lerpTicks;
		double z = getZ() + (lerpZ - getZ()) / lerpTicks;
		float yaw = getYaw() + (float) MathHelper.wrapDegrees(lerpYaw - getYaw()) / lerpTicks;
		float pitch = getPitch() + (float) (lerpPitch - getPitch()) / lerpTicks;
		lerpTicks--;
		setPosition(x, y, z);
		setRotation(yaw, pitch);
	}

	@Override
	public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int interpolationSteps) {
		this.lerpX = x;
		this.lerpY = y;
		this.lerpZ = z;
		this.lerpYaw = yaw;
		this.lerpPitch = pitch;
		this.lerpTicks = Math.max(interpolationSteps, 2);
	}

	// ------------------------------------------------------------------
	// Bewaffnung
	// ------------------------------------------------------------------

	public boolean canFire(WeaponType weapon) {
		return weaponCooldown <= 0 && getAmmo(weapon) >= weapon.ammoCost();
	}

	/** Feuert die gewaehlte Waffe ab. Laeuft ausschliesslich auf dem Server. */
	public void fireWeapon(WeaponType weapon) {
		if (getWorld().isClient || !canFire(weapon)) {
			return;
		}
		weaponCooldown = weapon.cooldownTicks();
		// Beim Schiessen oeffnen sich die Waffenschaechte - Stealth faellt kurz aus.
		if (weapon != WeaponType.LASER) {
			dataTracker.set(STEALTH_BREAK, F47Config.get().stealthBreakTicks);
		}
		switch (weapon) {
			case CANNON -> fireCannon();
			case MISSILE -> fireMissile();
			case BOMB -> dropBomb();
			case LASER -> fireLaser();
		}
	}

	protected void fireCannon() {
		dataTracker.set(CANNON_AMMO, Math.max(0, getCannonAmmo() - 1));
		Vec3d forward = getForwardVector();
		Vec3d nose = getPos().add(forward.multiply(2.4)).add(0, 0.1, 0);
		BulletEntity bullet = new BulletEntity(getWorld(), this, F47Config.get().cannonDamage);
		bullet.setTeam(getTeam());
		bullet.setPosition(nose.x, nose.y, nose.z);
		bullet.setVelocity(forward.x, forward.y, forward.z, 5.2f, 1.6f);
		bullet.addVelocity(getVelocity().x, getVelocity().y, getVelocity().z);
		getWorld().spawnEntity(bullet);
		playSound(SoundEvents.BLOCK_DISPENSER_LAUNCH, 1.2f, 1.6f + random.nextFloat() * 0.2f);
	}

	protected void fireMissile() {
		dataTracker.set(MISSILE_AMMO, Math.max(0, getMissileAmmo() - 1));
		Vec3d forward = getForwardVector();
		// Wechselnde Aufhaengung links/rechts.
		double side = (getMissileAmmo() % 2 == 0) ? 1.1 : -1.1;
		Vec3d wing = new Vec3d(side, -0.25, 0.0).rotateY(-getYaw() * MathHelper.RADIANS_PER_DEGREE);
		Vec3d origin = getPos().add(wing).add(forward.multiply(0.8));

		MissileEntity missile = new MissileEntity(getWorld(), this, MissileEntity.Kind.AIR_TO_AIR);
		missile.setTeam(getTeam());
		missile.setPosition(origin.x, origin.y, origin.z);
		missile.setVelocity(forward.multiply(1.4).add(getVelocity()));
		missile.setTarget(getLockedTarget());
		getWorld().spawnEntity(missile);
		playSound(SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.6f, 0.8f);
	}

	protected void dropBomb() {
		dataTracker.set(BOMB_AMMO, Math.max(0, getBombAmmo() - 1));
		BombEntity bomb = new BombEntity(getWorld(), this);
		bomb.setTeam(getTeam());
		bomb.setPosition(getX(), getY() - 0.4, getZ());
		bomb.setVelocity(getVelocity().multiply(0.9));
		getWorld().spawnEntity(bomb);
		playSound(SoundEvents.BLOCK_PISTON_EXTEND, 1.0f, 0.6f);
	}

	protected void fireLaser() {
		Vec3d forward = getForwardVector();
		Vec3d start = getPos().add(forward.multiply(2.4));
		Vec3d end = start.add(forward.multiply(90.0));

		EntityHitResult hit = findEntityOnRay(start, end, 0.9);
		Vec3d impact = end;
		if (hit != null) {
			impact = hit.getPos();
			hit.getEntity().damage(ModDamage.laser(getWorld(), this), F47Config.get().laserDamage * 1.6f);
			hit.getEntity().setOnFireFor(4);
		}
		if (getWorld() instanceof ServerWorld server) {
			drawBeam(server, start, impact);
		}
		playSound(SoundEvents.BLOCK_CONDUIT_ATTACK_TARGET, 1.1f, 1.5f);
	}

	/** Zeichnet einen sichtbaren Laserstrahl aus Partikeln. */
	public static void drawBeam(ServerWorld world, Vec3d start, Vec3d end) {
		Vec3d delta = end.subtract(start);
		int steps = (int) Math.min(120, delta.length() * 2.0);
		for (int i = 0; i < steps; i++) {
			Vec3d point = start.add(delta.multiply((double) i / Math.max(1, steps)));
			world.spawnParticles(ParticleTypes.END_ROD, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
		world.spawnParticles(ParticleTypes.FLAME, end.x, end.y, end.z, 6, 0.15, 0.15, 0.15, 0.02);
	}

	/** Erste Entity auf der Sichtlinie, die kein eigenes Fahrzeug ist. */
	@Nullable
	public EntityHitResult findEntityOnRay(Vec3d start, Vec3d end, double margin) {
		Box searchBox = new Box(start, end).expand(margin + 1.0);
		List<Entity> candidates = getWorld().getOtherEntities(this, searchBox,
				entity -> entity.canHit() && Iff.isEnemy(this, entity));
		return candidates.stream()
				.map(entity -> {
					Box box = entity.getBoundingBox().expand(margin);
					return box.raycast(start, end).map(pos -> new EntityHitResult(entity, pos)).orElse(null);
				})
				.filter(java.util.Objects::nonNull)
				.min(Comparator.comparingDouble(result -> result.getPos().squaredDistanceTo(start)))
				.orElse(null);
	}
}
