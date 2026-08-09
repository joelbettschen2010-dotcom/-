package ch.joel.westernmod.item;

import ch.joel.westernmod.entity.DynamiteEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/** Dynamitstange — brennt kurz und geht dann hoch. Klassiker für Banküberfälle. */
public class DynamiteItem extends Item {

	public DynamiteItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);

		// `user` ausgenommen — sein Client spielt den Ton selbst, sonst klingt es doppelt.
		world.playSound(user, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_TNT_PRIMED,
				SoundCategory.PLAYERS, 0.8f, 1.2f);

		if (!world.isClient) {
			DynamiteEntity dynamite = new DynamiteEntity(world, user);
			dynamite.setItem(stack.copyWithCount(1));
			dynamite.setVelocity(user, user.getPitch(), user.getYaw(), 0.0f, 1.3f, 1.0f);
			world.spawnEntity(dynamite);
		}

		if (!user.getAbilities().creativeMode) {
			stack.decrement(1);
		}
		user.getItemCooldownManager().set(this, 20);
		return TypedActionResult.success(stack, world.isClient());
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.westernmod.dynamite").formatted(Formatting.GRAY));
	}
}
