package com.f47mod.entity.projectile;

import com.f47mod.F47Config;
import com.f47mod.registry.ModDamage;
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
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Plasmageschoss des Plasmawerfers. Fliegt langsamer als eine Kugel, leuchtet
 * und setzt beim Aufschlag eine kleine Energieentladung frei.
 */
public class PlasmaBoltEntity extends ProjectileEntity {
	private int life;

	public PlasmaBoltEntity(EntityType<? extends PlasmaBoltEntity> type, World world) {
		super(type, world);
	}

	public PlasmaBoltEntity(World world, Entity owner) {
		this(ModEntities.PLASMA_BOLT, world);
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
		if (++life > 120) {
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
		setVelocity(velocity.multiply(0.998).add(0.0, -0.006, 0.0));
		updateRotation();

		if (getWorld() instanceof ServerWorld server) {
			server.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY(), getZ(), 2, 0.03, 0.03, 0.03, 0.0);
		} else {
			getWorld().addParticle(ParticleTypes.END_ROD, getX(), getY(), getZ(), 0.0, 0.0, 0.0);
		}
	}

	@Override
	protected boolean canHit(Entity entity) {
		return entity != getOwner() && !Iff.isFriendly(entity) && super.canHit(entity);
	}

	@Override
	protected void onEntityHit(EntityHitResult result) {
		super.onEntityHit(result);
		if (!getWorld().isClient) {
			result.getEntity().damage(ModDamage.laser(getWorld(), this), 8.0f);
			result.getEntity().setOnFireFor(3);
		}
	}

	@Override
	protected void onCollision(HitResult hitResult) {
		super.onCollision(hitResult);
		if (getWorld().isClient) {
			discard();
			return;
		}
		Vec3d pos = hitResult.getPos();
		getWorld().createExplosion(this, pos.x, pos.y, pos.z,
				F47Config.get().plasmaBlastPower, F47Config.get().explosionType());
		if (getWorld() instanceof ServerWorld server) {
			server.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y, pos.z, 24, 0.6, 0.6, 0.6, 0.06);
		}
		discard();
	}
}
