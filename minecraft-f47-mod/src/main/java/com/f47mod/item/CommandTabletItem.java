package com.f47mod.item;

import com.f47mod.block.entity.RadarBlockEntity;
import com.f47mod.entity.mob.SoldierEntity;
import com.f47mod.entity.vehicle.AutonomousF47Entity;
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
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.List;

/**
 * Kommando-Tablet - die Befehlsschnittstelle des Spielers.
 *
 * <ul>
 *   <li>Rechtsklick auf einen autonomen Jet: naechster Auftrag</li>
 *   <li>Rechtsklick auf einen Soldaten: naechste Verhaltensweise</li>
 *   <li>Rechtsklick auf einen Block: Heimatbasis und Wachposten festlegen</li>
 *   <li>Rechtsklick in die Luft: Lagebericht aller Einheiten</li>
 * </ul>
 */
public class CommandTabletItem extends Item {
	private static final double COMMAND_RANGE = 96.0;

	public CommandTabletItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult useOnEntity(ItemStack stack, PlayerEntity player, net.minecraft.entity.LivingEntity entity,
			Hand hand) {
		if (player.getWorld().isClient) {
			return ActionResult.SUCCESS;
		}
		if (entity instanceof SoldierEntity soldier) {
			soldier.setOwner(player);
			SoldierEntity.Stance stance = soldier.cycleStance();
			player.sendMessage(Text.translatable("message.f47.stance_changed",
					Text.translatable(soldier.getRole().translationKey()),
					Text.translatable("stance.f47." + stance.name().toLowerCase(java.util.Locale.ROOT))), true);
			click(player);
			return ActionResult.CONSUME;
		}
		return ActionResult.PASS;
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		World world = context.getWorld();
		PlayerEntity player = context.getPlayer();
		if (world.isClient || player == null) {
			return ActionResult.SUCCESS;
		}
		BlockPos pos = context.getBlockPos().up();

		// Ist ein autonomer Jet in Reichweite des angeklickten Punktes? Dann
		// bekommt er den naechsten Auftrag, sonst wird die Basis gesetzt.
		AutonomousF47Entity jet = findJetNear(world, pos, 4.0);
		if (jet != null) {
			jet.setOwner(player);
			AutonomousF47Entity.Order order = jet.cycleOrder();
			player.sendMessage(Text.translatable("message.f47.order_set",
					jet.getCallsign(),
					Text.translatable("order.f47." + order.name().toLowerCase(java.util.Locale.ROOT))), true);
			click(player);
			return ActionResult.CONSUME;
		}

		int jets = 0;
		int soldiers = 0;
		Box box = new Box(pos).expand(COMMAND_RANGE);
		for (AutonomousF47Entity unit : world.getEntitiesByClass(AutonomousF47Entity.class, box, e -> true)) {
			unit.setHomeBase(pos);
			unit.setOwner(player);
			jets++;
		}
		for (SoldierEntity soldier : world.getEntitiesByClass(SoldierEntity.class, box, e -> true)) {
			soldier.setOwner(player);
			soldier.setGuardPost(pos);
			soldiers++;
		}

		player.sendMessage(Text.translatable("message.f47.base_set", jets, soldiers), false);
		click(player);
		return ActionResult.CONSUME;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
		ItemStack stack = player.getStackInHand(hand);
		if (world.isClient) {
			return TypedActionResult.success(stack);
		}

		Box box = player.getBoundingBox().expand(COMMAND_RANGE);
		List<AutonomousF47Entity> jets = world.getEntitiesByClass(AutonomousF47Entity.class, box, e -> true);
		List<SoldierEntity> soldiers = world.getEntitiesByClass(SoldierEntity.class, box, e -> true);

		player.sendMessage(Text.translatable("message.f47.report_header")
				.formatted(Formatting.GOLD, Formatting.BOLD), false);

		if (jets.isEmpty() && soldiers.isEmpty()) {
			player.sendMessage(Text.translatable("message.f47.report_empty").formatted(Formatting.GRAY), false);
		}

		for (AutonomousF47Entity jet : jets) {
			player.sendMessage(Text.translatable("message.f47.report_jet",
					jet.getCallsign(),
					Text.translatable("order.f47." + jet.getOrder().name().toLowerCase(java.util.Locale.ROOT)),
					(int) jet.getStructure(),
					jet.getMissileAmmo(),
					(int) (jet.getFuel() / com.f47mod.F47Config.get().maxFuel * 100)
			).formatted(jet.hasPilot() ? Formatting.GREEN : Formatting.YELLOW), false);
		}

		if (!soldiers.isEmpty()) {
			player.sendMessage(Text.translatable("message.f47.report_troops", soldiers.size())
					.formatted(Formatting.AQUA), false);
		}

		// Lagebild vom naechsten Radar dazu.
		RadarBlockEntity radar = RadarBlockEntity.findNearby(world, player.getBlockPos(), 64);
		if (radar != null) {
			player.sendMessage(Text.translatable("message.f47.report_radar",
					radar.getContactCount(), radar.getThreatCount())
					.formatted(radar.getThreatCount() > 0 ? Formatting.RED : Formatting.GREEN), false);
		}

		click(player);
		return TypedActionResult.consume(stack);
	}

	private AutonomousF47Entity findJetNear(World world, BlockPos pos, double radius) {
		return world.getEntitiesByClass(AutonomousF47Entity.class, new Box(pos).expand(radius), e -> true)
				.stream().findFirst().orElse(null);
	}

	private void click(PlayerEntity player) {
		player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(),
				SoundCategory.PLAYERS, 0.5f, 1.6f);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.f47.tablet_1").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.f47.tablet_2").formatted(Formatting.DARK_GRAY));
		tooltip.add(Text.translatable("tooltip.f47.tablet_3").formatted(Formatting.DARK_GRAY));
		tooltip.add(Text.translatable("tooltip.f47.tablet_4").formatted(Formatting.DARK_GRAY));
	}

	/** Der Entity-Klick wird auch ohne Ziel-Entity abgefangen. */
	@Override
	public boolean isEnchantable(ItemStack stack) {
		return false;
	}
}
