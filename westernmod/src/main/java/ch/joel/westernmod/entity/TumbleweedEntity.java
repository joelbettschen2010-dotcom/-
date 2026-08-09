package ch.joel.westernmod.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LightType;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;

/**
 * Steppenläufer. Rollt ziellos durch die Prärie, macht keinen Schaden und zerfällt beim
 * ersten Treffer zu einem Büschel Gestrüpp — reine Western-Atmosphäre.
 */
public class TumbleweedEntity extends PathAwareEntity {

	public TumbleweedEntity(EntityType<? extends TumbleweedEntity> type, World world) {
		super(type, world);
	}

	public static DefaultAttributeContainer.Builder createTumbleweedAttributes() {
		return MobEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 1.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.42);
	}

	/** Steppenläufer treiben nur bei Tageslicht unter freiem Himmel herum. */
	public static boolean canSpawn(EntityType<? extends TumbleweedEntity> type, ServerWorldAccess world,
			SpawnReason reason, BlockPos pos, Random random) {
		return world.getLightLevel(LightType.SKY, pos) > 8 && MobEntity.canMobSpawn(type, world, reason, pos, random);
	}

	@Override
	protected void initGoals() {
		this.goalSelector.add(1, new WanderAroundFarGoal(this, 1.0, 1.0f));
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
		return false;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.BLOCK_GRASS_BREAK;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.BLOCK_GRASS_BREAK;
	}

	@Override
	public boolean isPushable() {
		return true;
	}
}
