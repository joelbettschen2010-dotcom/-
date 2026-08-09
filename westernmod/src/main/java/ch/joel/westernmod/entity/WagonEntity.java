package ch.joel.westernmod.entity;

import ch.joel.westernmod.ModEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;

import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Planwagen — das Fahrzeug des Westens. Ein Pferdegespann mit Wagen dahinter: der Fahrer
 * sitzt auf dem Bock und lenkt wie auf einem Pferd, zwei weitere Leute passen hinten drauf,
 * und unter der Plane stecken 27 Slots Ladung.
 *
 * <p>Auf festgetretenen Wegen rollt er spürbar schneller als querfeldein.
 */
public class WagonEntity extends PathAwareEntity implements NamedScreenHandlerFactory {
	private static final int CARGO_SIZE = 27;
	private static final int MAX_PASSENGERS = 3;

	private final SimpleInventory cargo = new SimpleInventory(CARGO_SIZE);

	public WagonEntity(EntityType<? extends WagonEntity> type, World world) {
		super(type, world);
	}

	public static DefaultAttributeContainer.Builder createWagonAttributes() {
		return MobEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 50.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.26)
				.add(EntityAttributes.GENERIC_STEP_HEIGHT, 1.0)
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
	}

	/** Stellt einen Planwagen an der angegebenen Stelle auf. */
	@Nullable
	public static WagonEntity spawn(ServerWorld world, BlockPos pos, float yaw) {
		WagonEntity wagon = ModEntities.WAGON.create(world);
		if (wagon == null) {
			return null;
		}
		wagon.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0f);
		wagon.setPersistent();
		world.spawnEntityAndPassengers(wagon);
		return wagon;
	}

	@Override
	protected void initGoals() {
		this.goalSelector.add(1, new LookAroundGoal(this));
	}

	// --- Steuerung -------------------------------------------------------------------------

	@Nullable
	@Override
	public LivingEntity getControllingPassenger() {
		return this.getFirstPassenger() instanceof LivingEntity rider ? rider : null;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return this.getPassengerList().size() < MAX_PASSENGERS;
	}

	/*
	 * Gesteuert wird über dieselben Haken wie beim Pferd: LivingEntity#travelControlled ruft
	 * der Reihe nach getControlledMovementInput, tickControlled und getSaddledSpeed auf und
	 * gibt das Ergebnis erst dann an travel weiter. travel selbst bleibt deshalb unangetastet.
	 */

	@Override
	protected Vec3d getControlledMovementInput(PlayerEntity controllingPlayer, Vec3d movementInput) {
		float sideways = controllingPlayer.sidewaysSpeed * 0.35f;
		float forward = controllingPlayer.forwardSpeed;
		if (forward <= 0.0f) {
			// Rückwärts rangieren geht nur im Schritttempo.
			forward *= 0.3f;
		}
		return new Vec3d(sideways, 0.0, forward);
	}

	@Override
	protected void tickControlled(PlayerEntity controllingPlayer, Vec3d movementInput) {
		super.tickControlled(controllingPlayer, movementInput);

		// Der Wagen folgt der Blickrichtung des Fahrers.
		this.setRotation(controllingPlayer.getYaw(), controllingPlayer.getPitch() * 0.5f);
		this.prevYaw = this.getYaw();
		this.bodyYaw = this.getYaw();
		this.headYaw = this.getYaw();
	}

	@Override
	protected float getSaddledSpeed(PlayerEntity controllingPlayer) {
		return (float) this.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED) * this.roadSpeedFactor();
	}

	/** Auf gebahnten Wegen rollt der Wagen deutlich besser. */
	private float roadSpeedFactor() {
		BlockState ground = this.getWorld().getBlockState(this.getBlockPos().down());
		if (ground.isOf(Blocks.DIRT_PATH) || ground.isOf(Blocks.COARSE_DIRT) || ground.isOf(Blocks.GRAVEL)
				|| ground.isOf(Blocks.PACKED_MUD)) {
			return 1.6f;
		}
		return 1.0f;
	}

	@Override
	public boolean isLogicalSideForUpdatingMovement() {
		return this.getControllingPassenger() instanceof PlayerEntity player
				? player.isMainPlayer()
				: super.isLogicalSideForUpdatingMovement();
	}

	@Override
	public void tick() {
		super.tick();

		// Rumpeln der Räder, solange gefahren wird. Die Rad-Drehung selbst kommt im
		// Renderer aus limbAngle und braucht hier keinen eigenen Zähler.
		double travelled = Math.sqrt(
				(this.getX() - this.prevX) * (this.getX() - this.prevX)
						+ (this.getZ() - this.prevZ) * (this.getZ() - this.prevZ));

		if (!this.getWorld().isClient && this.getControllingPassenger() != null && travelled > 0.03
				&& this.age % 9 == 0) {
			this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(),
					SoundEvents.ENTITY_HORSE_STEP_WOOD, SoundCategory.NEUTRAL, 0.5f, 0.9f);
		}
	}

	@Override
	protected void updatePassengerPosition(Entity passenger, PositionUpdater positionUpdater) {
		if (!this.hasPassenger(passenger)) {
			return;
		}

		// Sitz 0 ist der Kutschbock, danach geht es auf die Ladefläche.
		int index = Math.max(0, this.getPassengerList().indexOf(passenger));
		double alongAxis = switch (index) {
			case 0 -> -0.15;
			case 1 -> -1.15;
			default -> -1.95;
		};

		float yawRadians = this.getYaw() * ((float) Math.PI / 180.0f);
		double forwardX = -MathHelper.sin(yawRadians);
		double forwardZ = MathHelper.cos(yawRadians);

		positionUpdater.accept(passenger,
				this.getX() + forwardX * alongAxis,
				this.getY() + 1.05,
				this.getZ() + forwardZ * alongAxis);
	}

	// --- Interaktion -----------------------------------------------------------------------

	@Override
	protected ActionResult interactMob(PlayerEntity player, Hand hand) {
		if (this.getWorld().isClient) {
			return ActionResult.SUCCESS;
		}

		if (player.shouldCancelInteraction()) {
			// Schleichen + Rechtsklick öffnet die Ladefläche.
			player.openHandledScreen(this);
			return ActionResult.CONSUME;
		}

		return player.startRiding(this) ? ActionResult.CONSUME : ActionResult.PASS;
	}

	@Override
	public Text getDisplayName() {
		return this.getName();
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
		return GenericContainerScreenHandler.createGeneric9x3(syncId, playerInventory, this.cargo);
	}

	// --- Robustheit ------------------------------------------------------------------------

	@Override
	public boolean isCollidable() {
		return true;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canBeLeashed() {
		return true;
	}

	@Override
	public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ENTITY_HORSE_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_HORSE_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_HORSE_DEATH;
	}

	@Override
	protected void dropInventory() {
		super.dropInventory();
		for (int slot = 0; slot < this.cargo.size(); slot++) {
			ItemStack stack = this.cargo.getStack(slot);
			if (!stack.isEmpty()) {
				this.dropStack(stack);
			}
		}
		this.cargo.clear();
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.put("Cargo", this.cargo.toNbtList(this.getRegistryManager()));
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.contains("Cargo")) {
			NbtList list = nbt.getList("Cargo", NbtElement.COMPOUND_TYPE);
			this.cargo.readNbtList(list, this.getRegistryManager());
		}
	}

	@Override
	public boolean canImmediatelyDespawn(double distanceSquared) {
		return false;
	}
}
