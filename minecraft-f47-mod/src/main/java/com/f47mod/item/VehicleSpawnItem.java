package com.f47mod.item;

import com.f47mod.util.Team;
import com.f47mod.util.TeamMember;
import com.f47mod.util.TeamState;
import com.f47mod.entity.vehicle.AutonomousF47Entity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;
import java.util.function.Supplier;

/**
 * Stellt ein Fahrzeug oder Flugzeug auf.
 *
 * <p>Ein Item fuer alle neuen Muster - Bomber, Transporter, Schuetzenpanzer.
 * Der Typ steckt im Item, alles andere ist gleich: Es gehoert der Partei
 * dessen, der es aufstellt, und richtet sich nach seiner Blickrichtung.
 */
public class VehicleSpawnItem extends Item {
	private final Supplier<EntityType<? extends Entity>> type;

	public VehicleSpawnItem(Settings settings, Supplier<EntityType<? extends Entity>> type) {
		super(settings);
		this.type = type;
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

		BlockPos pos = context.getBlockPos().up();
		Entity entity = type.get().create(server);
		if (entity == null) {
			return ActionResult.PASS;
		}

		PlayerEntity player = context.getPlayer();
		float yaw = player == null ? 0.0f : player.getYaw();
		entity.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0f);

		Team team = player == null ? Team.BLUE : TeamState.teamOf(player);
		if (entity instanceof TeamMember member) {
			member.setTeam(team);
		}
		if (entity instanceof AutonomousF47Entity aircraft) {
			// Fliegendes Geraet braucht einen Heimatflugplatz, sonst weiss der
			// Bot-Pilot nicht, wohin er zurueckkehren soll.
			aircraft.setHomeBase(pos, yaw);
			aircraft.randomiseCallsign();
			aircraft.setOwner(player);
		}
		server.spawnEntity(entity);

		if (player != null && !player.getAbilities().creativeMode) {
			context.getStack().decrement(1);
		}
		return ActionResult.CONSUME;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable(getTranslationKey() + ".hint").formatted(Formatting.DARK_GRAY));
	}
}
