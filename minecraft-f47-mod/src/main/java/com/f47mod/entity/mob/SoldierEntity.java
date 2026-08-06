package com.f47mod.entity.mob;

import com.f47mod.F47Config;
import com.f47mod.entity.ai.BoardJetGoal;
import com.f47mod.entity.ai.EngineerServiceGoal;
import com.f47mod.entity.ai.FollowCommanderGoal;
import com.f47mod.entity.ai.GuardPostGoal;
import com.f47mod.entity.ai.MedicGoal;
import com.f47mod.entity.projectile.BulletEntity;
import com.f47mod.entity.projectile.MissileEntity;
import com.f47mod.registry.ModItems;
import com.f47mod.util.Iff;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.RangedAttackMob;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.ProjectileAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Bodenpersonal der Basis - vom Schuetzen bis zum Techniker. Soldaten gehoeren
 * immer einem Spieler, folgen ihm oder halten eine Stellung und bekaempfen
 * feindliche Einheiten selbstaendig.
 */
public class SoldierEntity extends PathAwareEntity implements RangedAttackMob {
	private static final TrackedData<Integer> ROLE =
			DataTracker.registerData(SoldierEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> STANCE =
			DataTracker.registerData(SoldierEntity.class, TrackedDataHandlerRegistry.INTEGER);

	public enum Stance {
		/** Folgt dem Kommandanten im Verband. */
		FOLLOW,
		/** Haelt die aktuelle Position und sichert den Sektor. */
		HOLD,
		/** Geht selbstaendig auf Patrouille rund um den Posten. */
		PATROL
	}

	@Nullable
	private UUID ownerUuid;
	@Nullable
	private BlockPos guardPost;

	public SoldierEntity(EntityType<? extends SoldierEntity> type, World world) {
		super(type, world);
		setPersistent();
	}

	public static DefaultAttributeContainer.Builder createSoldierAttributes() {
		return MobEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 24.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.30)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 40.0)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 4.0)
				.add(EntityAttributes.GENERIC_ARMOR, 4.0);
	}

	@Override
	protected void initGoals() {
		goalSelector.add(0, new SwimGoal(this));
		goalSelector.add(1, new BoardJetGoal(this));
		goalSelector.add(2, new MedicGoal(this));
		goalSelector.add(2, new EngineerServiceGoal(this));
		goalSelector.add(3, new ProjectileAttackGoal(this, 1.0, 20, 26.0f));
		goalSelector.add(4, new FollowCommanderGoal(this, 1.15, 6.0f, 22.0f));
		goalSelector.add(5, new GuardPostGoal(this, 0.9));
		goalSelector.add(7, new LookAtEntityGoal(this, PlayerEntity.class, 10.0f));
		goalSelector.add(8, new LookAroundGoal(this));

		targetSelector.add(1, new RevengeGoal(this));
		targetSelector.add(2, new ActiveTargetGoal<>(this, HostileEntity.class, 10, true, false,
				living -> F47Config.get().soldiersEngageAutomatically && Iff.isThreat(living)));
		targetSelector.add(3, new ActiveTargetGoal<>(this, EnemyDroneEntity.class, 10, true, false,
				living -> F47Config.get().soldiersEngageAutomatically));
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(ROLE, SoldierRole.RIFLEMAN.ordinal());
		builder.add(STANCE, Stance.FOLLOW.ordinal());
	}

	// ------------------------------------------------------------------
	// Rolle und Ausruestung
	// ------------------------------------------------------------------

	public SoldierRole getRole() {
		return SoldierRole.byOrdinal(dataTracker.get(ROLE));
	}

	public void setRole(SoldierRole role) {
		dataTracker.set(ROLE, role.ordinal());
		var maxHealth = getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
		if (maxHealth != null) {
			maxHealth.setBaseValue(role.maxHealth);
			setHealth(role.maxHealth);
		}
		var speed = getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (speed != null) {
			speed.setBaseValue(role.movementSpeed);
		}
		equipForRole(role);
	}

	private void equipForRole(SoldierRole role) {
		ItemStack weapon = switch (role) {
			case RIFLEMAN -> new ItemStack(ModItems.LASER_RIFLE);
			case HEAVY -> new ItemStack(ModItems.PLASMA_LAUNCHER);
			case MEDIC -> new ItemStack(net.minecraft.item.Items.GOLDEN_APPLE);
			case ENGINEER -> new ItemStack(ModItems.TITANIUM_PLATE);
			case PILOT -> new ItemStack(ModItems.COMMAND_TABLET);
		};
		equipStack(EquipmentSlot.MAINHAND, weapon);
		setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.0f);
	}

	public Stance getStance() {
		int ordinal = dataTracker.get(STANCE);
		Stance[] values = Stance.values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : Stance.FOLLOW;
	}

	public void setStance(Stance stance) {
		dataTracker.set(STANCE, stance.ordinal());
		if (stance == Stance.HOLD || stance == Stance.PATROL) {
			guardPost = getBlockPos();
		}
	}

	public Stance cycleStance() {
		Stance next = switch (getStance()) {
			case FOLLOW -> Stance.HOLD;
			case HOLD -> Stance.PATROL;
			case PATROL -> Stance.FOLLOW;
		};
		setStance(next);
		return next;
	}

	public void setOwner(@Nullable PlayerEntity player) {
		ownerUuid = player == null ? null : player.getUuid();
	}

	@Nullable
	public PlayerEntity getOwner() {
		return ownerUuid == null ? null : getWorld().getPlayerByUuid(ownerUuid);
	}

	@Nullable
	public BlockPos getGuardPost() {
		return guardPost;
	}

	public void setGuardPost(@Nullable BlockPos pos) {
		guardPost = pos;
	}

	// ------------------------------------------------------------------
	// Interaktion
	// ------------------------------------------------------------------

	@Override
	public ActionResult interactMob(PlayerEntity player, Hand hand) {
		if (getWorld().isClient) {
			return ActionResult.SUCCESS;
		}
		ItemStack stack = player.getStackInHand(hand);
		if (stack.isOf(ModItems.COMMAND_TABLET)) {
			// Das Tablet hat einen eigenen Handler - hier nicht doppelt reagieren.
			return ActionResult.PASS;
		}
		if (stack.isOf(net.minecraft.item.Items.BREAD) || stack.isOf(net.minecraft.item.Items.COOKED_BEEF)) {
			heal(6.0f);
			if (!player.getAbilities().creativeMode) {
				stack.decrement(1);
			}
			playSound(SoundEvents.ENTITY_PLAYER_BURP, 0.6f, 1.1f);
			return ActionResult.CONSUME;
		}
		Stance stance = cycleStance();
		player.sendMessage(Text.translatable("message.f47.stance_changed",
				Text.translatable(getRole().translationKey()),
				Text.translatable("stance.f47." + stance.name().toLowerCase(java.util.Locale.ROOT))), true);
		return ActionResult.CONSUME;
	}

	// ------------------------------------------------------------------
	// Kampf
	// ------------------------------------------------------------------

	@Override
	public void shootAt(LivingEntity target, float pullProgress) {
		if (getWorld().isClient) {
			return;
		}
		SoldierRole role = getRole();
		Vec3d origin = new Vec3d(getX(), getEyeY() - 0.1, getZ());
		Vec3d aim = new Vec3d(target.getX(), target.getBodyY(0.6), target.getZ()).subtract(origin);

		if (role == SoldierRole.HEAVY) {
			MissileEntity missile = new MissileEntity(getWorld(), this, MissileEntity.Kind.AIR_TO_AIR);
			missile.setPosition(origin.x, origin.y, origin.z);
			missile.setTarget(target);
			missile.launch(aim);
			getWorld().spawnEntity(missile);
			return;
		}

		float damage = role == SoldierRole.RIFLEMAN
				? F47Config.get().soldierRifleDamage
				: role.damage;
		// Kurze Salve statt Einzelschuss.
		int shots = role == SoldierRole.RIFLEMAN ? 3 : 1;
		for (int i = 0; i < shots; i++) {
			BulletEntity bullet = new BulletEntity(getWorld(), this, damage);
			bullet.setPosition(origin.x, origin.y, origin.z);
			bullet.setVelocity(aim.x, aim.y, aim.z, 3.2f, 3.0f);
			getWorld().spawnEntity(bullet);
		}
		playSound(SoundEvents.BLOCK_DISPENSER_LAUNCH, 0.9f, 1.7f);
	}

	@Override
	public boolean canTarget(LivingEntity target) {
		return Iff.isThreat(target) && super.canTarget(target);
	}

	@Override
	public boolean canBeLeashed() {
		return true;
	}

	@Override
	protected SoundEvent getHurtSound(net.minecraft.entity.damage.DamageSource source) {
		return SoundEvents.ENTITY_PLAYER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_PLAYER_DEATH;
	}

	@Override
	public boolean isPushable() {
		return true;
	}

	/** Soldaten verschwinden nicht beim Entfernen des Spielers. */
	@Override
	public boolean canImmediatelyDespawn(double distanceSquared) {
		return false;
	}

	@Override
	public boolean cannotDespawn() {
		return true;
	}

	// ------------------------------------------------------------------
	// Speichern
	// ------------------------------------------------------------------

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putInt("Role", dataTracker.get(ROLE));
		nbt.putInt("Stance", dataTracker.get(STANCE));
		if (ownerUuid != null) {
			nbt.putUuid("Owner", ownerUuid);
		}
		if (guardPost != null) {
			nbt.put("GuardPost", NbtHelper.fromBlockPos(guardPost));
		}
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		dataTracker.set(ROLE, nbt.getInt("Role"));
		dataTracker.set(STANCE, nbt.getInt("Stance"));
		if (nbt.containsUuid("Owner")) {
			ownerUuid = nbt.getUuid("Owner");
		}
		if (nbt.contains("GuardPost")) {
			guardPost = NbtHelper.toBlockPos(nbt, "GuardPost").orElse(null);
		}
	}

	@Override
	public Text getName() {
		if (hasCustomName()) {
			return super.getName();
		}
		return Text.translatable(getRole().translationKey());
	}
}
