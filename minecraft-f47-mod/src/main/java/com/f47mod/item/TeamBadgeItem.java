package com.f47mod.item;

import com.f47mod.util.Team;
import com.f47mod.util.TeamMember;
import com.f47mod.util.TeamState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * Truppenabzeichen - regelt, fuer welche Seite gekaempft wird.
 *
 * <ul>
 *   <li>Rechtsklick in die Luft: eigene Partei wechseln. Alles, was du danach
 *       aufstellst, gehoert zur neuen Partei.</li>
 *   <li>Rechtsklick auf eine Einheit: sie laeuft zu deiner Partei ueber.</li>
 *   <li>Rechtsklick auf Radar, Iron Dome oder Kaserne: die Anlage wechselt
 *       ebenfalls die Seite.</li>
 * </ul>
 *
 * <p>Wer nur mit einer Partei spielt, braucht das Abzeichen nie anzufassen -
 * dann bleibt einfach alles blau.
 */
public class TeamBadgeItem extends Item {

	public TeamBadgeItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
		ItemStack stack = player.getStackInHand(hand);
		if (world.isClient) {
			return TypedActionResult.success(stack);
		}

		Team next = TeamState.teamOf(player).opposite();
		TeamState.setTeam(player, next);
		player.sendMessage(Text.translatable("message.f47.team_switched",
				Text.translatable(next.translationKey()).formatted(next.formatting())), false);
		beep(world, player, next);
		return TypedActionResult.consume(stack);
	}

	@Override
	public ActionResult useOnEntity(ItemStack stack, PlayerEntity player, LivingEntity entity, Hand hand) {
		if (player.getWorld().isClient) {
			return ActionResult.SUCCESS;
		}
		if (!(entity instanceof TeamMember member)) {
			return ActionResult.PASS;
		}
		Team team = TeamState.teamOf(player);
		member.setTeam(team);
		player.sendMessage(Text.translatable("message.f47.unit_assigned",
				entity.getName(), Text.translatable(team.translationKey()).formatted(team.formatting())), true);
		beep(player.getWorld(), player, team);
		return ActionResult.CONSUME;
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		World world = context.getWorld();
		PlayerEntity player = context.getPlayer();
		if (world.isClient || player == null) {
			return ActionResult.SUCCESS;
		}

		BlockPos pos = context.getBlockPos();
		BlockEntity blockEntity = world.getBlockEntity(pos);
		if (!(blockEntity instanceof TeamMember member)) {
			// Kein zuweisbares Bauwerk - dann wechselt der Spieler die Seite.
			return ActionResult.PASS;
		}

		Team team = TeamState.teamOf(player);
		member.setTeam(team);
		player.sendMessage(Text.translatable("message.f47.building_assigned",
				world.getBlockState(pos).getBlock().getName(),
				Text.translatable(team.translationKey()).formatted(team.formatting())), true);
		beep(world, player, team);
		return ActionResult.CONSUME;
	}

	private void beep(World world, PlayerEntity player, Team team) {
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(),
				SoundCategory.PLAYERS, 0.7f, team == Team.BLUE ? 1.2f : 0.8f);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.f47.badge_1").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.f47.badge_2").formatted(Formatting.DARK_GRAY));
		tooltip.add(Text.translatable("tooltip.f47.badge_3").formatted(Formatting.DARK_GRAY));
	}
}
