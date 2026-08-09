package ch.joel.westernmod;

import ch.joel.westernmod.world.WesternTownGenerator;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * {@code /westerntown [<Position>]} — baut eine Westernstadt ohne Siedlungsurkunde.
 *
 * <p>Gedacht für Kreativmodus und Serverbetrieb; braucht Rechtestufe 2. Ohne Position
 * entsteht die Stadt dort, wo der Befehl ausgeführt wird, ausgerichtet an der
 * Blickrichtung.
 */
public final class ModCommands {

	private ModCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(CommandManager.literal("westerntown")
						.requires(source -> source.hasPermissionLevel(2))
						.executes(context -> build(context,
								BlockPos.ofFloored(context.getSource().getPosition())))
						.then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
								.executes(context -> build(context,
										BlockPosArgumentType.getLoadedBlockPos(context, "pos"))))));
	}

	private static int build(CommandContext<ServerCommandSource> context, BlockPos origin) {
		ServerCommandSource source = context.getSource();
		ServerWorld world = source.getWorld();
		Direction facing = Direction.fromRotation(source.getRotation().y);

		WesternTownGenerator.generate(world, origin, facing, world.getRandom());

		source.sendFeedback(() -> Text.translatable("message.westernmod.town.done")
				.formatted(Formatting.GOLD), true);
		return 1;
	}
}
