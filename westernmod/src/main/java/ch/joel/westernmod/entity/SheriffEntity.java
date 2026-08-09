package ch.joel.westernmod.entity;

import ch.joel.westernmod.item.GunProfile;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;

/**
 * Der Sheriff — zäher als ein gewöhnlicher Cowboy, schiesst schneller und weiter und
 * geht Banditen von sich aus an den Kragen.
 */
public class SheriffEntity extends GunslingerEntity {
	private static final GunProfile SHERIFF_GUN = new GunProfile(6.0f, 22, 1, 2.0f, 3.0f, 1.5f, 0.0f);

	public SheriffEntity(EntityType<? extends SheriffEntity> type, World world) {
		super(type, world);
	}

	public static DefaultAttributeContainer.Builder createSheriffAttributes() {
		return createGunslingerAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 40.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.32)
				.add(EntityAttributes.GENERIC_ARMOR, 4.0);
	}

	@Override
	public boolean isLawman() {
		return true;
	}

	@Override
	protected GunProfile gunProfile() {
		return SHERIFF_GUN;
	}

	@Override
	protected float gunRange() {
		return 24.0f;
	}

	@Override
	protected void initTargetGoals() {
		this.targetSelector.add(2, new ActiveTargetGoal<>(this, BanditLeaderEntity.class, true));
		this.targetSelector.add(3, new ActiveTargetGoal<>(this, BanditEntity.class, true));
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ENTITY_VILLAGER_AMBIENT;
	}
}
