package com.f47mod.entity.projectile;

import com.f47mod.registry.ModEntities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Kinetisches Geschoss - Bordkanone der F-47 und Sturmgewehre der Bodentruppen.
 * Fliegt sehr schnell, macht wenig Einzelschaden und wirkt erst in Salven.
 */
public class BulletEntity extends TeamProjectileEntity {
	private float damage = 4.0f;
	private int life;
	private static final int MAX_AGE = 50;

	public BulletEntity(EntityType<? extends BulletEntity> type, World world) {
		super(type, world);
	}

	public BulletEntity(World world, Entity owner, float damage) {
		this(ModEntities.BULLET, world);
		setOwner(owner);
		this.damage = damage;
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		damage = nbt.getFloat("Damage");
		life = nbt.getInt("Life");
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putFloat("Damage", damage);
		nbt.putInt("Life", life);
	}

	@Override
	public void tick() {
		super.tick();
		if (++life > MAX_AGE) {
			discard();
			return;
		}

		HitResult hit = ProjectileUtil.getCollision(this, this::canHit);
		if (hit.getType() != HitResult.Type.MISS) {
			onCollision(hit);
			if (isRemoved()) {
				return;
			}
		}

		Vec3d velocity = getVelocity();
		setPosition(getX() + velocity.x, getY() + velocity.y, getZ() + velocity.z);
		// Leichter Geschossabfall auf grosse Entfernung.
		setVelocity(velocity.multiply(0.995).add(0.0, -0.012, 0.0));
		updateRotation();

		if (getWorld() instanceof ServerWorld server && life % 2 == 0) {
			server.spawnParticles(ParticleTypes.CRIT, getX(), getY(), getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	@Override
	protected boolean canHit(Entity entity) {
		return isValidHit(entity) && super.canHit(entity);
	}

	@Override
	protected void onEntityHit(EntityHitResult result) {
		super.onEntityHit(result);
		if (getWorld().isClient) {
			return;
		}
		Entity target = result.getEntity();
		target.damage(com.f47mod.registry.ModDamage.bullet(getWorld(), this, getOwner()), damage);
		getWorld().playSound(null, getBlockPos(), SoundEvents.ENTITY_ARROW_HIT,
				SoundCategory.NEUTRAL, 0.6f, 1.6f);
		discard();
	}

	@Override
	protected void onBlockHit(BlockHitResult result) {
		super.onBlockHit(result);
		if (getWorld() instanceof ServerWorld server) {
			Vec3d pos = result.getPos();
			server.spawnParticles(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 3, 0.05, 0.05, 0.05, 0.01);
		}
		discard();
	}

	@Override
	public boolean shouldRender(double distance) {
		return distance < 4096.0;
	}
}
