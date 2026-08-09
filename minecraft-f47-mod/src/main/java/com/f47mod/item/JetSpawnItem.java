package com.f47mod.item;

import com.f47mod.entity.vehicle.AutonomousF47Entity;
import com.f47mod.entity.vehicle.F47Entity;
import com.f47mod.registry.ModEntities;
import com.f47mod.util.Team;
import com.f47mod.util.TeamState;
import net.minecraft.entity.EntityType;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Transportkiste fuer eine F-47. Auf den Boden gesetzt entsteht daraus die
 * fertige Maschine - bemannt oder als autonome Drohnenversion.
 */
public class JetSpawnItem extends Item {
	private final boolean autonomous;

	public JetSpawnItem(Settings settings, boolean autonomous) {
		super(settings);
		this.autonomous = autonomous;
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		World world = context.getWorld();
		if (world.isClient) {
			return ActionResult.SUCCESS;
		}
		if (context.getSide() != Direction.UP) {
			return ActionResult.PASS;
		}

		BlockPos pos = context.getBlockPos().up();
		PlayerEntity player = context.getPlayer();

		F47Entity jet;
		if (autonomous) {
			AutonomousF47Entity autoJet = ModEntities.AUTONOMOUS_F47.create(world);
			if (autoJet == null) {
				return ActionResult.FAIL;
			}
			autoJet.setOwner(player);
			autoJet.setHomeBase(pos);
			jet = autoJet;
		} else {
			jet = ModEntities.F47.create(world);
			if (jet == null) {
				return ActionResult.FAIL;
			}
		}

		// Die Maschine gehoert der Partei, fuer die der Spieler gerade kaempft.
		if (player != null) {
			jet.setTeam(TeamState.teamOf(player));
		}
		if (jet instanceof AutonomousF47Entity autonomous) {
			autonomous.randomiseCallsign();
		}

		float yaw = player == null ? 0.0f : player.getYaw();
		jet.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5, yaw, 0.0f);
		world.spawnEntity(jet);
		world.playSound(null, pos, SoundEvents.BLOCK_ANVIL_PLACE, SoundCategory.NEUTRAL, 0.8f, 0.9f);

		if (player != null) {
			if (autonomous) {
				player.sendMessage(Text.translatable("message.f47.autojet_placed",
						((AutonomousF47Entity) jet).getCallsign()), false);
			}
			if (!player.getAbilities().creativeMode) {
				context.getStack().decrement(1);
			}
		}
		return ActionResult.CONSUME;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable(autonomous
				? "tooltip.f47.autonomous_jet"
				: "tooltip.f47.jet").formatted(Formatting.GRAY));
		if (autonomous) {
			tooltip.add(Text.translatable("tooltip.f47.autonomous_jet_hint").formatted(Formatting.DARK_GRAY));
		} else {
			tooltip.add(Text.translatable("tooltip.f47.jet_hint").formatted(Formatting.DARK_GRAY));
		}
	}
}
