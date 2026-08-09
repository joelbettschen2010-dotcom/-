package ch.joel.westernmod.entity;

import ch.joel.westernmod.ModEntities;
import ch.joel.westernmod.ModItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;

/**
 * Eine Bleikugel. Fliegt fast gerade, trifft hart und ist nach spätestens drei Sekunden weg.
 */
public class BulletEntity extends ThrownItemEntity {
	private static final int MAX_LIFETIME = 60;

	private float damage = 5.0f;
	private int age;

	public BulletEntity(EntityType<? extends BulletEntity> type, World world) {
		super(type, world);
	}

	public BulletEntity(World world, LivingEntity owner, float damage) {
		super(ModEntities.BULLET, owner, world);
		this.damage = damage;
	}

	@Override
	protected Item getDefaultItem() {
		return ModItems.AMMO;
	}

	/** Kugeln fallen kaum — auf Duell-Distanz fliegen sie praktisch gerade. */
	@Override
	protected double getGravity() {
		return 0.002;
	}

	@Override
	public void tick() {
		super.tick();

		if (this.getWorld().isClient) {
			this.getWorld().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
		} else if (++this.age > MAX_LIFETIME) {
			this.discard();
		}
	}

	@Override
	protected void onEntityHit(EntityHitResult hitResult) {
		super.onEntityHit(hitResult);
		if (this.getWorld().isClient) {
			return;
		}

		Entity target = hitResult.getEntity();
		Entity owner = this.getOwner();
		DamageSource source = this.getDamageSources()
				.mobProjectile(this, owner instanceof LivingEntity living ? living : null);

		if (target.damage(source, this.damage)) {
			this.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(),
					SoundEvents.ENTITY_ARROW_HIT, SoundCategory.NEUTRAL, 0.8f, 1.4f);
		}
	}

	@Override
	protected void onBlockHit(BlockHitResult hitResult) {
		super.onBlockHit(hitResult);
		if (!this.getWorld().isClient) {
			this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(),
					SoundEvents.BLOCK_STONE_HIT, SoundCategory.NEUTRAL, 0.6f, 1.8f);
		}
	}

	@Override
	protected void onCollision(HitResult hitResult) {
		super.onCollision(hitResult);
		if (!this.getWorld().isClient) {
			// Statuscode 3 lässt den Client die Aufprall-Partikel des Wurfgeschosses zeigen.
			this.getWorld().sendEntityStatus(this, (byte) 3);
			this.discard();
		}
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putFloat("BulletDamage", this.damage);
		nbt.putInt("BulletAge", this.age);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.contains("BulletDamage")) {
			this.damage = nbt.getFloat("BulletDamage");
		}
		this.age = nbt.getInt("BulletAge");
	}
}
