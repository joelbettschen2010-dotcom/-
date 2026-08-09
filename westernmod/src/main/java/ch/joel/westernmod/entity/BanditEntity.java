package ch.joel.westernmod.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;
import net.minecraft.world.LightType;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;

/**
 * Bandit — überfällt Städte, schiesst auf alles, was zum Gesetz gehört, und macht auch
 * vor Dorfbewohnern und Spielern nicht halt.
 */
public class BanditEntity extends GunslingerEntity {

	public BanditEntity(EntityType<? extends BanditEntity> type, World world) {
		super(type, world);
	}

	public static DefaultAttributeContainer.Builder createBanditAttributes() {
		return createGunslingerAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 26.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.31);
	}

	/**
	 * Banditen lagern im Dunkeln und nur, wenn überhaupt gekämpft wird — auf Friedlich
	 * bleibt der Westen ruhig.
	 */
	public static boolean canSpawnBandit(EntityType<? extends BanditEntity> type, ServerWorldAccess world,
			SpawnReason reason, BlockPos pos, Random random) {
		return world.getDifficulty() != Difficulty.PEACEFUL
				&& world.getLightLevel(LightType.BLOCK, pos) <= 7
				&& MobEntity.canMobSpawn(type, world, reason, pos, random);
	}

	@Override
	public boolean isLawman() {
		return false;
	}

	@Override
	protected void initTargetGoals() {
		this.targetSelector.add(2, new ActiveTargetGoal<>(this, SheriffEntity.class, true));
		this.targetSelector.add(3, new ActiveTargetGoal<>(this, CowboyEntity.class, true));
		this.targetSelector.add(4, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
		this.targetSelector.add(5, new ActiveTargetGoal<>(this, VillagerEntity.class, false));
		this.targetSelector.add(6, new ActiveTargetGoal<>(this, IronGolemEntity.class, true));
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ENTITY_PILLAGER_AMBIENT;
	}
}
