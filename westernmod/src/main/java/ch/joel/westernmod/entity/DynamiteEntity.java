package ch.joel.westernmod.entity;

import ch.joel.westernmod.ModEntities;
import ch.joel.westernmod.ModItems;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;

/** Geworfene Dynamitstange: zündet beim Aufschlag, spätestens aber wenn die Lunte abgebrannt ist. */
public class DynamiteEntity extends ThrownItemEntity {
	private static final int FUSE = 100;
	private static final float POWER = 2.6f;

	private int fuse;

	public DynamiteEntity(EntityType<? extends DynamiteEntity> type, World world) {
		super(type, world);
	}

	public DynamiteEntity(World world, LivingEntity owner) {
		super(ModEntities.DYNAMITE, owner, world);
	}

	@Override
	protected Item getDefaultItem() {
		return ModItems.DYNAMITE;
	}

	@Override
	public void tick() {
		super.tick();

		if (this.getWorld().isClient) {
			this.getWorld().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.2, this.getZ(),
					0.0, 0.01, 0.0);
			if (this.age % 4 == 0) {
				this.getWorld().addParticle(ParticleTypes.FLAME, this.getX(), this.getY() + 0.25, this.getZ(),
						0.0, 0.01, 0.0);
			}
		} else if (++this.fuse > FUSE) {
			this.explode();
		}
	}

	@Override
	protected void onCollision(HitResult hitResult) {
		super.onCollision(hitResult);
		if (!this.getWorld().isClient) {
			this.explode();
		}
	}

	private void explode() {
		this.getWorld().createExplosion(this, this.getX(), this.getY(), this.getZ(), POWER,
				World.ExplosionSourceType.TNT);
		this.discard();
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putInt("Fuse", this.fuse);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		this.fuse = nbt.getInt("Fuse");
	}
}
