package com.f47mod.entity.vehicle;

import com.f47mod.F47Config;
import com.f47mod.entity.mob.SoldierEntity;
import com.f47mod.util.Iff;
import com.f47mod.util.Team;
import com.f47mod.util.TeamMember;
import com.f47mod.world.MissionLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Schuetzenpanzer - gepanzertes Fahrzeug fuer die Bodentruppen.
 *
 * <p>Zu Fuss braucht ein Trupp fuer ein paar hundert Bloecke Minuten und
 * bleibt an jedem Hang haengen. Der Panzer nimmt bis zu sechs Mann auf,
 * faehrt selbstaendig zum Gegner und setzt sie dort ab - und schiesst
 * unterwegs mit dem Turm auf alles, was in Reichweite kommt.
 *
 * <p>Er faehrt bewusst schlicht: Vorwaerts auf das Ziel zu, Steigungen bis
 * einen Block nimmt er mit. Kein Wegfindungsnetz - das wuerde auf lange
 * Strecken ohnehin aufgeben.
 */
public class ArmoredVehicleEntity extends Entity implements TeamMember, MissionLoader.MissionUnit {
	private static final TrackedData<Integer> TEAM =
			DataTracker.registerData(ArmoredVehicleEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Float> ARMOUR =
			DataTracker.registerData(ArmoredVehicleEntity.class, TrackedDataHandlerRegistry.FLOAT);

	public static final float MAX_ARMOUR = 80.0f;
	/** Wie viele Mann mitfahren. */
	private static final int SEATS = 6;
	/** Fahrgeschwindigkeit in Bloecken je Tick - gut viermal Laufgeschwindigkeit. */
	private static final double DRIVE_SPEED = 0.34;
	/** Reichweite des Turms. */
	private static final double TURRET_RANGE = 34.0;

	private int turretTimer;
	private int searchTimer;
	@Nullable
	private Vec3d objective;

	public ArmoredVehicleEntity(EntityType<? extends ArmoredVehicleEntity> type, World world) {
		super(type, world);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(TEAM, Team.BLUE.ordinal());
		builder.add(ARMOUR, MAX_ARMOUR);
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		setTeam(Team.byOrdinal(nbt.getInt("Team")));
		dataTracker.set(ARMOUR, nbt.contains("Armour") ? nbt.getFloat("Armour") : MAX_ARMOUR);
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.putInt("Team", getTeam().ordinal());
		nbt.putFloat("Armour", dataTracker.get(ARMOUR));
	}

	@Override
	public Team getTeam() {
		return Team.byOrdinal(dataTracker.get(TEAM));
	}

	@Override
	public void setTeam(Team team) {
		dataTracker.set(TEAM, team.ordinal());
	}

	/** Unterwegs braucht auch ein Fahrzeug gerechnetes Gelaende. */
	@Override
	public boolean needsLoadedTerrain() {
		return objective != null;
	}

	@Override
	public void tick() {
		super.tick();
		if (getWorld().isClient) {
			return;
		}
		if (turretTimer > 0) {
			turretTimer--;
		}
		drive();
		fireTurret();
	}

	/** Faehrt in Richtung Gegner. Suche selten, Fahrt jeden Tick. */
	private void drive() {
		if (--searchTimer <= 0) {
			searchTimer = 100;
			objective = findEnemyPosition();
		}

		Vec3d velocity = getVelocity().add(0.0, -0.08, 0.0);
		if (objective != null) {
			Vec3d flat = new Vec3d(objective.x - getX(), 0.0, objective.z - getZ());
			if (flat.lengthSquared() > 36.0) {
				Vec3d dir = flat.normalize();
				velocity = new Vec3d(dir.x * DRIVE_SPEED, velocity.y, dir.z * DRIVE_SPEED);
				setYaw((float) (MathHelper.atan2(dir.z, dir.x) * MathHelper.DEGREES_PER_RADIAN) - 90.0f);
			} else {
				// Angekommen: Truppe absitzen lassen.
				velocity = new Vec3d(velocity.x * 0.6, velocity.y, velocity.z * 0.6);
				unload();
			}
		} else {
			velocity = new Vec3d(velocity.x * 0.8, velocity.y, velocity.z * 0.8);
		}

		setVelocity(velocity);
		Vec3d before = getPos();
		move(MovementType.SELF, getVelocity());

		// Kleine Stufen nehmen, statt davor stehen zu bleiben.
		if (horizontalCollision && isOnGround() && getPos().squaredDistanceTo(before) < 0.001) {
			setVelocity(getVelocity().add(0.0, 0.42, 0.0));
		}
		if (isOnGround()) {
			setVelocity(getVelocity().multiply(0.86, 1.0, 0.86));
		}
	}

	/** Setzt die Besatzung ab, sobald das Ziel erreicht ist. */
	private void unload() {
		if (getPassengerList().isEmpty()) {
			return;
		}
		for (Entity passenger : List.copyOf(getPassengerList())) {
			passenger.stopRiding();
			passenger.refreshPositionAfterTeleport(getX() + random.nextDouble() * 4 - 2,
					getY() + 1, getZ() + random.nextDouble() * 4 - 2);
		}
		playSound(SoundEvents.BLOCK_IRON_DOOR_OPEN, 1.0f, 0.8f);
	}

	/** Turm: schiesst auf Gegner in Reichweite. */
	private void fireTurret() {
		if (turretTimer > 0) {
			return;
		}
		Box box = getBoundingBox().expand(TURRET_RANGE);
		Entity target = null;
		double best = Double.MAX_VALUE;
		for (Entity candidate : getWorld().getOtherEntities(this, box, e -> Iff.isEnemy(this, e))) {
			double distance = candidate.squaredDistanceTo(this);
			if (distance < best) {
				best = distance;
				target = candidate;
			}
		}
		if (target == null) {
			return;
		}
		turretTimer = 25;

		if (target instanceof net.minecraft.entity.LivingEntity living) {
			living.damage(getDamageSources().generic(), F47Config.get().soldierRifleDamage * 1.8f);
		}
		if (getWorld() instanceof ServerWorld server) {
			Vec3d muzzle = getPos().add(0.0, 1.4, 0.0);
			server.spawnParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 6, 0.3, 0.2, 0.3, 0.02);
		}
		getWorld().playSound(null, getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(),
				SoundCategory.NEUTRAL, 0.5f, 1.8f);
	}

	@Nullable
	private Vec3d findEnemyPosition() {
		double reach = F47Config.get().strikeRange;
		Box box = getBoundingBox().expand(reach);
		Entity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Entity candidate : getWorld().getOtherEntities(this, box, e -> Iff.isEnemy(this, e))) {
			// Bodenziele - hinter Flugzeugen herzufahren waere sinnlos.
			if (!candidate.isOnGround()) {
				continue;
			}
			double distance = candidate.squaredDistanceTo(this);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = candidate;
			}
		}
		return best == null ? null : best.getPos();
	}

	// ------------------------------------------------------------------

	@Override
	public ActionResult interact(PlayerEntity player, Hand hand) {
		if (getWorld().isClient) {
			return ActionResult.SUCCESS;
		}
		return player.startRiding(this) ? ActionResult.CONSUME : ActionResult.PASS;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return getPassengerList().size() < SEATS
				&& (passenger instanceof SoldierEntity || passenger instanceof PlayerEntity);
	}

	@Override
	public boolean isCollidable() {
		return true;
	}

	@Override
	public boolean canHit() {
		return !isRemoved();
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		if (getWorld().isClient || isRemoved()) {
			return false;
		}
		float left = dataTracker.get(ARMOUR) - amount;
		dataTracker.set(ARMOUR, left);
		if (left <= 0.0f) {
			if (getWorld() instanceof ServerWorld server) {
				server.createExplosion(this, getX(), getY(), getZ(), 3.0f,
						F47Config.get().explosionType());
			}
			removeAllPassengers();
			discard();
		}
		return true;
	}
}
