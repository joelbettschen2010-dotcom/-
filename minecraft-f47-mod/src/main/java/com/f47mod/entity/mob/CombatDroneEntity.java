package com.f47mod.entity.mob;

import com.f47mod.entity.projectile.MissileEntity;
import com.f47mod.entity.vehicle.F47Entity;
import com.f47mod.util.Iff;
import com.f47mod.util.Team;
import com.f47mod.util.TeamMember;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.control.FlightMoveControl;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.EnumSet;

/**
 * Kampfdrohne. Gehoert einer Partei an und greift ausschliesslich die andere
 * Seite sowie die Monster der Welt an - beide Parteien koennen sie einsetzen.
 * Ihre Raketen sind genau die Ziele, fuer die der Iron Dome gebaut ist.
 */
public class CombatDroneEntity extends HostileEntity implements TeamMember {
	private static final TrackedData<Integer> TEAM =
			DataTracker.registerData(CombatDroneEntity.class, TrackedDataHandlerRegistry.INTEGER);

	/** Reichweite der bordeigenen Zielsuche. */
	private static final double DETECTION_RANGE = 100.0;

	private int missileCooldown = 60;
	private int vehicleSearchTimer;
	/** Flugzeug, das die Drohne verfolgt - Jets sind keine LivingEntity. */
	@Nullable
	private F47Entity vehicleTarget;

	public CombatDroneEntity(EntityType<? extends CombatDroneEntity> type, World world) {
		super(type, world);
		this.moveControl = new FlightMoveControl(this, 20, true);
		this.experiencePoints = 12;
	}

	public static DefaultAttributeContainer.Builder createDroneAttributes() {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 22.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.30)
				.add(EntityAttributes.GENERIC_FLYING_SPEED, 0.65)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 96.0)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 5.0);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(TEAM, Team.RED.ordinal());
	}

	@Override
	public Team getTeam() {
		return Team.byOrdinal(dataTracker.get(TEAM));
	}

	@Override
	public void setTeam(Team team) {
		dataTracker.set(TEAM, team.ordinal());
	}

	@Override
	protected void initGoals() {
		goalSelector.add(1, new DroneAttackGoal(this));

		targetSelector.add(1, new RevengeGoal(this));
		// Ein Ziel ist alles, was zur Gegenpartei gehoert - Spieler, Soldaten
		// und die Monster der Welt gleichermassen.
		targetSelector.add(2, new ActiveTargetGoal<>(this, LivingEntity.class, 10, true, false,
				living -> Iff.isEnemy(this, living)));
	}

	@Override
	protected EntityNavigation createNavigation(World world) {
		BirdNavigation navigation = new BirdNavigation(this, world);
		navigation.setCanPathThroughDoors(false);
		navigation.setCanSwim(false);
		navigation.setCanEnterOpenDoors(true);
		return navigation;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean hasNoGravity() {
		return true;
	}

	@Override
	public void tick() {
		super.tick();
		// Drohnen bleiben in der Luft und sinken nicht ab.
		setVelocity(getVelocity().multiply(1.0, 0.7, 1.0));
		if (missileCooldown > 0) {
			missileCooldown--;
		}
		if (!getWorld().isClient) {
			huntAircraft();
		}
		if (getWorld().isClient && random.nextInt(4) == 0) {
			getWorld().addParticle(ParticleTypes.SMOKE, getX(), getY() - 0.2, getZ(), 0.0, -0.02, 0.0);
		}
	}

	/**
	 * Jagd auf Flugzeuge der Gegenpartei. Getarnte Maschinen werden erst sehr
	 * spaet erfasst - hier zahlt sich der Tarnkappenmodus wirklich aus.
	 */
	private void huntAircraft() {
		if (vehicleTarget != null && (!vehicleTarget.isAlive()
				|| !Iff.isEnemy(this, vehicleTarget)
				|| !Iff.canDetect(vehicleTarget, getPos(), DETECTION_RANGE))) {
			vehicleTarget = null;
		}
		if (++vehicleSearchTimer >= 30) {
			vehicleSearchTimer = 0;
			if (vehicleTarget == null && getTarget() == null) {
				vehicleTarget = findVehicleTarget(DETECTION_RANGE);
			}
		}
		if (vehicleTarget == null) {
			return;
		}

		// Auf Abfangkurs gehen und aus mittlerer Entfernung Raketen starten.
		Vec3d intercept = vehicleTarget.getPos().add(vehicleTarget.getVelocity().multiply(12));
		getLookControl().lookAt(intercept.x, intercept.y, intercept.z);
		if (age % 20 == 0) {
			getMoveControl().moveTo(intercept.x, intercept.y + 4.0, intercept.z, 1.2);
		}
		if (distanceTo(vehicleTarget) < 70.0) {
			launchMissile(vehicleTarget);
		}
	}

	@Nullable
	private F47Entity findVehicleTarget(double range) {
		return getWorld().getEntitiesByClass(F47Entity.class, getBoundingBox().expand(range),
						jet -> jet.isAlive() && Iff.isEnemy(this, jet)
								&& Iff.canDetect(jet, getPos(), range)).stream()
				.min(Comparator.comparingDouble(this::squaredDistanceTo))
				.orElse(null);
	}

	/** Feuert eine Rakete auf das aktuelle Ziel ab. */
	public void launchMissile(Entity target) {
		if (missileCooldown > 0 || getWorld().isClient) {
			return;
		}
		missileCooldown = 100 + random.nextInt(60);

		MissileEntity missile = new MissileEntity(getWorld(), this, MissileEntity.Kind.HEAVY);
		missile.setPosition(getX(), getY() - 0.3, getZ());
		missile.setTarget(target);
		Vec3d aim = target.getPos().add(0, target.getHeight() * 0.5, 0).subtract(getPos());
		missile.launch(aim);
		getWorld().spawnEntity(missile);
	}

	@Override
	public boolean canTarget(LivingEntity target) {
		return Iff.isEnemy(this, target) && super.canTarget(target);
	}

	@Override
	public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
		return false;
	}

	/** Drohnen verbrennen nicht im Tageslicht - sie fliegen rund um die Uhr. */
	@Override
	public boolean isAffectedByDaylight() {
		return false;
	}

	@Override
	protected boolean isDisallowedInPeaceful() {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.BLOCK_BEACON_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.BLOCK_COPPER_HIT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_GENERIC_EXPLODE.value();
	}

	@Override
	public void onDeath(DamageSource damageSource) {
		super.onDeath(damageSource);
		if (getWorld() instanceof ServerWorld server) {
			server.spawnParticles(ParticleTypes.EXPLOSION, getX(), getY(), getZ(), 3, 0.4, 0.4, 0.4, 0.0);
		}
	}

	@Override
	public boolean cannotDespawn() {
		return true;
	}

	@Override
	public boolean canImmediatelyDespawn(double distanceSquared) {
		return false;
	}

	@Override
	public Text getName() {
		if (hasCustomName()) {
			return super.getName();
		}
		return super.getName().copy().formatted(getTeam().formatting());
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putInt("Team", getTeam().ordinal());
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		setTeam(Team.byOrdinal(nbt.getInt("Team")));
	}

	/** Kampfverhalten: Abstand halten, Raketen abfeuern, Angriffsfluege fliegen. */
	private static class DroneAttackGoal extends Goal {
		private final CombatDroneEntity drone;
		private int repositionTimer;

		DroneAttackGoal(CombatDroneEntity drone) {
			this.drone = drone;
			setControls(EnumSet.of(Control.MOVE, Control.LOOK));
		}

		@Override
		public boolean canStart() {
			return drone.getTarget() != null && drone.getTarget().isAlive();
		}

		@Override
		public boolean shouldRunEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			LivingEntity target = drone.getTarget();
			if (target == null) {
				return;
			}
			drone.getLookControl().lookAt(target, 30.0f, 30.0f);
			double distance = drone.distanceTo(target);

			if (distance < 40.0) {
				drone.launchMissile(target);
			}

			if (--repositionTimer > 0) {
				return;
			}
			repositionTimer = 30;

			// Angriffsposition schraeg ueber dem Ziel.
			Vec3d offset = new Vec3d(
					(drone.getRandom().nextDouble() - 0.5) * 24.0,
					8.0 + drone.getRandom().nextDouble() * 6.0,
					(drone.getRandom().nextDouble() - 0.5) * 24.0);
			Vec3d goal = target.getPos().add(offset);
			drone.getMoveControl().moveTo(goal.x, goal.y, goal.z, 1.0);
		}
	}
}
