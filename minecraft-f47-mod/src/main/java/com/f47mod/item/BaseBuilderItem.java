package com.f47mod.item;

import com.f47mod.util.Team;
import com.f47mod.util.TeamState;
import com.f47mod.world.BaseBlueprint;
import net.minecraft.entity.player.PlayerEntity;
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
 * Basis-Bausatz. Ein Rechtsklick auf den Boden stellt einen kompletten
 * Luftwaffenstuetzpunkt hin: Bahn, Hangar, Radar, Iron Dome, Kaserne und die
 * ersten Einheiten.
 *
 * <p>Die Anlage zeigt in die Richtung, in die man gerade schaut, und gehoert
 * der eigenen Partei - zwei Bausaetze und ein Wechsel mit dem
 * Truppenabzeichen ergeben also zwei verfeindete Basen.
 */
public class BaseBuilderItem extends Item {

	public BaseBuilderItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		World world = context.getWorld();
		if (world.isClient) {
			return ActionResult.SUCCESS;
		}
		if (!(world instanceof ServerWorld server)) {
			return ActionResult.PASS;
		}

		PlayerEntity player = context.getPlayer();
		// Die Bahn beginnt auf dem angeklickten Block und laeuft nach vorne weg.
		BlockPos origin = context.getBlockPos().up();
		Direction facing = player == null
				? Direction.NORTH
				: player.getHorizontalFacing();

		// Im Schleichen entsteht der Stuetzpunkt der Gegenpartei. Ohne das
		// baut man immer nur die eigene Seite auf - und dann steht die ganze
		// Anlage da, ohne dass jemand einen Gegner findet.
		Team own = player == null ? Team.BLUE : TeamState.teamOf(player);
		Team team = player != null && player.isSneaking() ? own.opposite() : own;

		int placed = BaseBlueprint.build(server, origin, facing, player, team);

		world.playSound(null, origin, SoundEvents.BLOCK_BEACON_ACTIVATE,
				SoundCategory.BLOCKS, 1.0f, 1.0f);
		if (player != null) {
			player.sendMessage(Text.translatable("message.f47.base_built",
					placed, Text.translatable(team.translationKey()).formatted(team.formatting())), false);
			if (!player.getAbilities().creativeMode) {
				context.getStack().decrement(1);
			}
		}
		return ActionResult.CONSUME;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.f47.base_builder_1").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.f47.base_builder_2").formatted(Formatting.DARK_GRAY));
		tooltip.add(Text.translatable("tooltip.f47.base_builder_3").formatted(Formatting.DARK_GRAY));
		tooltip.add(Text.translatable("tooltip.f47.base_builder_4").formatted(Formatting.RED));
	}
}
