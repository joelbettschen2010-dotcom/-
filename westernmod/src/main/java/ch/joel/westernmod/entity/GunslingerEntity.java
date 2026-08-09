package ch.joel.westernmod.entity;

import ch.joel.westernmod.ModItems;
import ch.joel.westernmod.item.GunProfile;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.RangedAttackMob;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.ProjectileAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;

/**
 * Gemeinsame Basis aller Revolvermänner — Cowboys, Sheriffs und Banditen.
 *
 * <p>Alle tragen einen Revolver, halten Distanz und feuern über
 * {@link ProjectileAttackGoal} aufeinander. Wer auf wen schiesst, legen die Unterklassen
 * über {@link #initTargetGoals()} fest.
 */
public abstract class GunslingerEntity extends PathAwareEntity implements RangedAttackMob {

	protected GunslingerEntity(EntityType<? extends GunslingerEntity> type, World world) {
		super(type, world);
		this.equipStack(EquipmentSlot.MAINHAND, new ItemStack(ModItems.REVOLVER));
		this.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.06f);
		this.setCanPickUpLoot(false);
	}

	public static DefaultAttributeContainer.Builder createGunslingerAttributes() {
		return MobEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 24.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 40.0)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 3.0);
	}

	/** Steht diese Figur auf der Seite des Gesetzes? */
	public abstract boolean isLawman();

	protected abstract void initTargetGoals();

	protected GunProfile gunProfile() {
		return GunProfile.NPC_REVOLVER;
	}

	/** Reichweite, auf die der Schütze herangeht, bevor er feuert. */
	protected float gunRange() {
		return 18.0f;
	}

	@Override
	protected void initGoals() {
		this.goalSelector.add(0, new SwimGoal(this));
		this.goalSelector.add(1, new ProjectileAttackGoal(this, 1.0, this.gunProfile().cooldown(), this.gunRange()));
		this.goalSelector.add(4, new WanderAroundFarGoal(this, 0.7));
		this.goalSelector.add(5, new LookAtEntityGoal(this, PlayerEntity.class, 10.0f));
		this.goalSelector.add(6, new LookAroundGoal(this));

		this.targetSelector.add(1, new RevengeGoal(this).setGroupRevenge());
		this.initTargetGoals();
	}

	@Override
	public void shootAt(LivingEntity target, float pullProgress) {
		GunProfile profile = this.gunProfile();

		// Ein Schuss, aber je nach Waffe mehrere Kugeln (Schrot).
		for (int pellet = 0; pellet < profile.pellets(); pellet++) {
			BulletEntity bullet = new BulletEntity(this.getWorld(), this, profile.damage());

			double dx = target.getX() - this.getX();
			double dy = target.getBodyY(0.6) - bullet.getY();
			double dz = target.getZ() - this.getZ();
			double horizontal = Math.sqrt(dx * dx + dz * dz);

			// Etwas Vorhalt nach oben, damit die Kugel auf Distanz nicht zu tief einschlägt.
			bullet.setVelocity(dx, dy + horizontal * 0.015, dz, profile.velocity(), profile.spread());
			this.getWorld().spawnEntity(bullet);
		}

		this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(),
				SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.HOSTILE, 1.0f, profile.pitch());
		this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(),
				SoundEvents.ITEM_CROSSBOW_SHOOT, SoundCategory.HOSTILE, 0.5f, profile.pitch() * 0.8f);
		this.swingHand(net.minecraft.util.Hand.MAIN_HAND);
	}

	@Override
	protected SoundEvent getHurtSound(net.minecraft.entity.damage.DamageSource source) {
		return SoundEvents.ENTITY_PLAYER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_PLAYER_HURT;
	}

}
