package com.f47mod.entity.projectile;

import com.f47mod.F47Config;
import com.f47mod.registry.ModDamage;
import com.f47mod.registry.ModEntities;
import com.f47mod.util.Iff;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Lenkflugkoerper. Dieselbe Klasse deckt drei Rollen ab:
 * Luft-Luft-Rakete der F-47, Abfangrakete des Iron Dome und feindliche
 * Angriffsrakete - unterschiedlich sind nur Zielwahl, Tempo und Sprengkraft.
 */
public class MissileEntity extends TeamProjectileEntity {
	private static final TrackedData<Integer> KIND =
			DataTracker.registerData(MissileEntity.class, TrackedDataHandlerRegistry.INTEGER);

	public enum Kind {
		/** Luft-Luft-Rakete vom Jet. */
		AIR_TO_AIR(1.9, 0.16, 3.0, 200),
		/** Abfangrakete des Iron Dome - sehr wendig, kleiner Sprengkopf. */
		INTERCEPTOR(2.4, 0.30, 2.0, 140),
		/** Schwere Rakete der Kampfdrohnen - langsamer und traeger. */
		HEAVY(1.5, 0.11, 3.0, 240);

		public final double speed;
		public final double turnRate;
		public final double proximityFuse;
		public final int maxAge;

		Kind(double speed, double turnRate, double proximityFuse, int maxAge) {
			this.speed = speed;
			this.turnRate = turnRate;
			this.proximityFuse = proximityFuse;
			this.maxAge = maxAge;
		}
	}

	private int targetId = -1;
	private int life;
	/** Erste Ticks ohne Zielsuche, damit die Rakete sauber vom Traeger wegkommt. */
	private int armingDelay = 3;

	public MissileEntity(EntityType<? extends MissileEntity> type, World world) {
		super(type, world);
	}

	public MissileEntity(World world, Entity owner, Kind kind) {
		this(ModEntities.MISSILE, world);
		setOwner(owner);
		dataTracker.set(KIND, kind.ordinal());
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(KIND, Kind.AIR_TO_AIR.ordinal());
	}

	public Kind getKind() {
		int ordinal = dataTracker.get(KIND);
		Kind[] values = Kind.values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : Kind.AIR_TO_AIR;
	}

	public void setTarget(@Nullable Entity target) {
		targetId = target == null ? -1 : target.getId();
	}

	@Nullable
	public Entity getTarget() {
		return targetId < 0 ? null : getWorld().getEntityById(targetId);
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		dataTracker.set(KIND, nbt.getInt("Kind"));
		life = nbt.getInt("Life");
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putInt("Kind", dataTracker.get(KIND));
		nbt.putInt("Life", life);
	}

	@Override
	public void tick() {
		super.tick();
		Kind kind = getKind();

		if (++life > kind.maxAge) {
			detonate(getPos());
			return;
		}
		if (armingDelay > 0) {
			armingDelay--;
		}

		guide(kind);

		Vec3d velocity = getVelocity();
		Vec3d next = getPos().add(velocity);

		HitResult hit = ProjectileUtil.getCollision(this, this::canHit);
		if (hit.getType() != HitResult.Type.MISS) {
			onCollision(hit);
			if (isRemoved()) {
				return;
			}
		}

		// Naeherungszuender: Ein Direkttreffer ist bei schnellen Zielen unrealistisch.
		Entity target = getTarget();
		if (armingDelay <= 0 && target != null && target.isAlive()
				&& target.getPos().distanceTo(getPos()) < kind.proximityFuse) {
			detonate(target.getPos());
			return;
		}

		setPosition(next.x, next.y, next.z);
		updateRotation();
		spawnTrail();
	}

	/** Zielverfolgung: Der Geschwindigkeitsvektor dreht sich zum Ziel. */
	private void guide(Kind kind) {
		Entity target = getTarget();
		if (target == null || !target.isAlive()) {
			if (armingDelay <= 0 && (life % 10 == 0)) {
				reacquire(kind);
			}
			// Ohne Ziel fliegt die Rakete geradeaus weiter.
			setVelocity(getVelocity().normalize().multiply(kind.speed));
			return;
		}

		Vec3d aim = target.getPos().add(0, target.getHeight() * 0.5, 0);
		// Vorhalt auf die vermutete Position beim Einschlag.
		double timeToImpact = getPos().distanceTo(aim) / Math.max(0.1, kind.speed);
		aim = aim.add(target.getVelocity().multiply(Math.min(timeToImpact, 20.0)));

		Vec3d desired = aim.subtract(getPos()).normalize();
		Vec3d current = getVelocity().normalize();
		Vec3d steered = current.lerp(desired, kind.turnRate).normalize();
		setVelocity(steered.multiply(kind.speed));
	}

	/** Nach Zielverlust ein neues Ziel in der Naehe suchen. */
	private void reacquire(Kind kind) {
		Box box = getBoundingBox().expand(kind == Kind.INTERCEPTOR ? 40 : 60);
		List<Entity> candidates = getWorld().getOtherEntities(this, box, this::isValidTarget);
		Entity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Entity candidate : candidates) {
			double distance = candidate.squaredDistanceTo(this);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = candidate;
			}
		}
		setTarget(best);
	}

	private boolean isValidTarget(Entity entity) {
		if (entity == getOwner() || !entity.isAlive()) {
			return false;
		}
		// Abfangraketen nehmen nur Luftziele, alles andere jedes gegnerische Ziel.
		return getKind() == Kind.INTERCEPTOR
				? Iff.isAirThreat(getTeam(), entity)
				: Iff.isEnemy(getTeam(), entity);
	}

	@Override
	protected boolean canHit(Entity entity) {
		// Kurz nach dem Start und gegenueber anderen Raketen bleibt sie blind,
		// damit sie nicht am eigenen Traeger oder Schwarm haengen bleibt.
		if (armingDelay > 0 || entity instanceof MissileEntity) {
			return false;
		}
		return isValidHit(entity) && super.canHit(entity);
	}

	@Override
	protected void onEntityHit(EntityHitResult result) {
		super.onEntityHit(result);
		if (!getWorld().isClient) {
			result.getEntity().damage(ModDamage.missile(getWorld(), this, getOwner()),
					F47Config.get().missileDamage);
			detonate(result.getPos());
		}
	}

	@Override
	protected void onCollision(HitResult hitResult) {
		super.onCollision(hitResult);
		if (!getWorld().isClient && !isRemoved()) {
			detonate(hitResult.getPos());
		}
	}

	public void detonate(Vec3d pos) {
		if (getWorld().isClient) {
			discard();
			return;
		}
		float power = getKind() == Kind.INTERCEPTOR ? 1.6f : F47Config.get().missileBlastPower;
		World.ExplosionSourceType type = getKind() == Kind.INTERCEPTOR
				? World.ExplosionSourceType.NONE
				: F47Config.get().explosionType();
		getWorld().createExplosion(this, pos.x, pos.y, pos.z, power, type);
		if (getWorld() instanceof ServerWorld server) {
			server.spawnParticles(ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z, 3, 0.4, 0.4, 0.4, 0.0);
		}
		discard();
	}

	private void spawnTrail() {
		if (getWorld() instanceof ServerWorld server) {
			server.spawnParticles(ParticleTypes.SMOKE, getX(), getY(), getZ(), 2, 0.05, 0.05, 0.05, 0.0);
			if (life % 2 == 0) {
				server.spawnParticles(ParticleTypes.FLAME, getX(), getY(), getZ(), 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
	}

	@Override
	protected void onBlockHit(net.minecraft.util.hit.BlockHitResult result) {
		super.onBlockHit(result);
		if (!getWorld().isClient) {
			detonate(result.getPos());
		}
	}

	@Override
	public boolean shouldRender(double distance) {
		return distance < 16384.0;
	}

	/** Hilfsfunktion fuer Startgeschwindigkeit in eine Richtung. */
	public void launch(Vec3d direction) {
		Vec3d velocity = direction.normalize().multiply(getKind().speed);
		setVelocity(velocity);
		setYaw((float) (MathHelper.atan2(velocity.x, velocity.z) * MathHelper.DEGREES_PER_RADIAN));
		setPitch((float) (MathHelper.atan2(velocity.y,
				Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z)) * MathHelper.DEGREES_PER_RADIAN));
		getWorld().playSound(null, getBlockPos(), SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH,
				SoundCategory.NEUTRAL, 1.4f, 0.7f);
	}
}
