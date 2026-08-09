package ch.joel.westernmod.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;

/**
 * Cowboy — gehört zur Stadt, streift umher und greift zum Revolver, sobald Banditen auftauchen.
 * Gegen den Spieler richtet er sich nur, wenn dieser zuerst schiesst.
 */
public class CowboyEntity extends GunslingerEntity {

	public CowboyEntity(EntityType<? extends CowboyEntity> type, World world) {
		super(type, world);
	}

	@Override
	public boolean isLawman() {
		return true;
	}

	@Override
	protected void initTargetGoals() {
		this.targetSelector.add(2, new ActiveTargetGoal<>(this, BanditEntity.class, true));
		this.targetSelector.add(3, new ActiveTargetGoal<>(this, BanditLeaderEntity.class, true));
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ENTITY_VILLAGER_AMBIENT;
	}
}
