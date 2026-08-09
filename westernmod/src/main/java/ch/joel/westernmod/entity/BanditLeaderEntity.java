package ch.joel.westernmod.entity;

import ch.joel.westernmod.item.GunProfile;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;

/**
 * Der Bandenchef — auf ihn ist das Kopfgeld ausgesetzt. Trägt eine abgesägte Schrotflinte
 * und steckt deutlich mehr ein als seine Leute.
 */
public class BanditLeaderEntity extends GunslingerEntity {
	private static final GunProfile LEADER_GUN = new GunProfile(3.0f, 40, 5, 8.0f, 2.2f, 0.8f, 0.0f);

	public BanditLeaderEntity(EntityType<? extends BanditLeaderEntity> type, World world) {
		super(type, world);
	}

	public static DefaultAttributeContainer.Builder createLeaderAttributes() {
		return createGunslingerAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 60.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.29)
				.add(EntityAttributes.GENERIC_ARMOR, 6.0)
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.5);
	}

	@Override
	public boolean isLawman() {
		return false;
	}

	@Override
	protected GunProfile gunProfile() {
		return LEADER_GUN;
	}

	/** Die Schrotflinte streut — er geht näher heran. */
	@Override
	protected float gunRange() {
		return 12.0f;
	}

	@Override
	protected void initTargetGoals() {
		this.targetSelector.add(2, new ActiveTargetGoal<>(this, SheriffEntity.class, true));
		this.targetSelector.add(3, new ActiveTargetGoal<>(this, CowboyEntity.class, true));
		this.targetSelector.add(4, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
		this.targetSelector.add(5, new ActiveTargetGoal<>(this, VillagerEntity.class, false));
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ENTITY_PILLAGER_AMBIENT;
	}
}
