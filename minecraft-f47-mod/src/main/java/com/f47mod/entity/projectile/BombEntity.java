package com.f47mod.entity.projectile;

import com.f47mod.F47Config;
import com.f47mod.registry.ModEntities;
import com.f47mod.util.Iff;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Ungelenkte Freifallbombe. Faellt ballistisch und detoniert beim Aufschlag -
 * gedacht fuer Angriffe im Tiefflug auf Bodenziele.
 */
public class BombEntity extends ProjectileEntity {
	private int life;

	public BombEntity(EntityType<? extends BombEntity> type, World world) {
		super(type, world);
	}

	public BombEntity(World world, Entity owner) {
		this(ModEntities.BOMB, world);
		setOwner(owner);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		life = nbt.getInt("Life");
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putInt("Life", life);
	}

	@Override
	public void tick() {
		super.tick();
		if (++life > 400) {
			discard();
			return;
		}

		HitResult hit = ProjectileUtil.getCollision(this, this::canHit);
		if (hit.getType() != HitResult.Type.MISS) {
			detonate(hit.getPos());
			return;
		}

		Vec3d velocity = getVelocity();
		setPosition(getX() + velocity.x, getY() + velocity.y, getZ() + velocity.z);
		// Schwerkraft und Luftwiderstand.
		setVelocity(velocity.multiply(0.99).add(0.0, -0.05, 0.0));
		updateRotation();

		if (getWorld() instanceof ServerWorld server && life % 3 == 0) {
			server.spawnParticles(ParticleTypes.SMOKE, getX(), getY(), getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	@Override
	protected boolean canHit(Entity entity) {
		return entity != getOwner() && !Iff.isFriendly(entity) && super.canHit(entity);
	}

	@Override
	protected void onCollision(HitResult hitResult) {
		super.onCollision(hitResult);
		detonate(hitResult.getPos());
	}

	private void detonate(Vec3d pos) {
		if (getWorld().isClient) {
			discard();
			return;
		}
		getWorld().createExplosion(this, pos.x, pos.y, pos.z,
				F47Config.get().bombBlastPower, F47Config.get().explosionType());
		discard();
	}
}
