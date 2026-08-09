package ch.joel.westernmod.item;

import ch.joel.westernmod.ModItems;
import ch.joel.westernmod.entity.BulletEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;

/**
 * Basis aller Schusswaffen. Geschossen wird direkt aus den Patronen im Inventar — kein Magazin,
 * kein Nachladen: die Feuerrate ergibt sich aus dem Cooldown im {@link GunProfile}.
 */
public class GunItem extends Item {
	private final GunProfile profile;

	public GunItem(Settings settings, GunProfile profile) {
		super(settings);
		this.profile = profile;
	}

	public GunProfile profile() {
		return this.profile;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);

		if (user.getItemCooldownManager().isCoolingDown(this)) {
			return TypedActionResult.pass(stack);
		}

		boolean free = user.getAbilities().creativeMode;
		int ammoSlot = free ? -1 : findAmmoSlot(user);
		if (!free && ammoSlot < 0) {
			// `user` als Ausnahme: der Server spart ihn beim Verteilen aus, sein Client
			// spielt den Ton selbst — sonst hört der Schütze alles doppelt.
			world.playSound(user, user.getX(), user.getY(), user.getZ(), SoundEvents.BLOCK_LEVER_CLICK,
					SoundCategory.PLAYERS, 0.7f, 1.9f);
			if (!world.isClient) {
				user.sendMessage(Text.translatable("message.westernmod.no_ammo").formatted(Formatting.RED), true);
			}
			user.getItemCooldownManager().set(this, 10);
			return TypedActionResult.fail(stack);
		}

		if (!world.isClient) {
			fire(world, user);
			if (ammoSlot >= 0) {
				user.getInventory().getStack(ammoSlot).decrement(1);
			}
			stack.damage(1, user, hand == Hand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
		}

		// Mündungsknall: zwei überlagerte Vanilla-Sounds ergeben einen trockenen Schuss.
		world.playSound(user, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST,
				SoundCategory.PLAYERS, 1.0f, this.profile.pitch());
		world.playSound(user, user.getX(), user.getY(), user.getZ(), SoundEvents.ITEM_CROSSBOW_SHOOT,
				SoundCategory.PLAYERS, 0.6f, this.profile.pitch() * 0.8f);

		spawnMuzzleSmoke(world, user);
		user.getItemCooldownManager().set(this, this.profile.cooldown());

		// Rückstoss
		Vec3d look = user.getRotationVec(1.0f);
		user.addVelocity(-look.x * this.profile.recoil(), 0.0, -look.z * this.profile.recoil());
		user.velocityModified = true;

		return TypedActionResult.success(stack, world.isClient());
	}

	private void fire(World world, PlayerEntity user) {
		Vec3d look = user.getRotationVec(1.0f);
		for (int i = 0; i < this.profile.pellets(); i++) {
			BulletEntity bullet = new BulletEntity(world, user, this.profile.damage());
			bullet.setVelocity(look.x, look.y, look.z, this.profile.velocity(), this.profile.spread());
			world.spawnEntity(bullet);
		}
	}

	private static void spawnMuzzleSmoke(World world, PlayerEntity user) {
		if (!world.isClient) {
			return;
		}
		Vec3d look = user.getRotationVec(1.0f);
		Vec3d muzzle = user.getEyePos().add(look.multiply(0.9)).add(0.0, -0.15, 0.0);
		for (int i = 0; i < 6; i++) {
			world.addParticle(ParticleTypes.SMOKE,
					muzzle.x + (world.random.nextDouble() - 0.5) * 0.15,
					muzzle.y + (world.random.nextDouble() - 0.5) * 0.15,
					muzzle.z + (world.random.nextDouble() - 0.5) * 0.15,
					look.x * 0.06, look.y * 0.06 + 0.02, look.z * 0.06);
		}
	}

	private static int findAmmoSlot(PlayerEntity player) {
		PlayerInventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.size(); slot++) {
			if (inventory.getStack(slot).isOf(ModItems.AMMO)) {
				return slot;
			}
		}
		return -1;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.westernmod.gun.damage", String.format("%.1f", this.profile.damage()))
				.formatted(Formatting.GRAY));
		if (this.profile.pellets() > 1) {
			tooltip.add(Text.translatable("tooltip.westernmod.gun.pellets", this.profile.pellets())
					.formatted(Formatting.GRAY));
		}
		tooltip.add(Text.translatable("tooltip.westernmod.gun.ammo").formatted(Formatting.DARK_GRAY));
	}
}
