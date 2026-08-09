package ch.joel.westernmod.item;

import ch.joel.westernmod.world.WesternTownGenerator;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.List;

/**
 * Siedlungsurkunde: Rechtsklick auf den Boden lässt eine komplette Westernstadt entstehen —
 * Hauptstrasse, Saloon, Sheriffbüro, Bank, Kirche, Stall und alles, was dazugehört, samt
 * Bewohnern und einem Banditenlager in der Nähe.
 */
public class TownDeedItem extends Item {

	public TownDeedItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		World world = context.getWorld();
		if (world.isClient) {
			return ActionResult.SUCCESS;
		}
		if (context.getPlayer() == null) {
			return ActionResult.PASS;
		}

		BlockPos origin = context.getBlockPos().up();
		Direction facing = context.getPlayer().getHorizontalFacing();

		context.getPlayer().sendMessage(
				Text.translatable("message.westernmod.town.building").formatted(Formatting.GOLD), true);

		WesternTownGenerator.generate((ServerWorld) world, origin, facing, world.getRandom());

		world.playSound(null, origin, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 0.6f, 1.4f);
		context.getPlayer().sendMessage(
				Text.translatable("message.westernmod.town.done").formatted(Formatting.GOLD), false);

		if (!context.getPlayer().getAbilities().creativeMode) {
			context.getStack().decrement(1);
		}
		context.getPlayer().getItemCooldownManager().set(this, 100);
		return ActionResult.CONSUME;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.westernmod.town_deed.1").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.westernmod.town_deed.2").formatted(Formatting.DARK_GRAY));
	}
}
