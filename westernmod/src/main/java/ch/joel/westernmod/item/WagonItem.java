package ch.joel.westernmod.item;

import ch.joel.westernmod.entity.WagonEntity;
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
import net.minecraft.world.World;

import java.util.List;

/** Stellt einen Planwagen auf. */
public class WagonItem extends Item {

	public WagonItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		World world = context.getWorld();
		if (world.isClient) {
			return ActionResult.SUCCESS;
		}

		BlockPos pos = context.getBlockPos().offset(context.getSide());
		WagonEntity wagon = WagonEntity.spawn((ServerWorld) world, pos, context.getPlayerYaw() + 180.0f);

		if (wagon == null) {
			return ActionResult.FAIL;
		}

		world.playSound(null, pos, SoundEvents.ENTITY_HORSE_SADDLE, SoundCategory.BLOCKS, 1.0f, 1.0f);
		if (context.getPlayer() == null || !context.getPlayer().getAbilities().creativeMode) {
			context.getStack().decrement(1);
		}
		return ActionResult.CONSUME;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.westernmod.wagon.1").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.westernmod.wagon.2").formatted(Formatting.DARK_GRAY));
	}
}
