package com.f47mod.world;

import com.f47mod.F47Config;
import com.f47mod.util.Team;
import com.f47mod.util.TeamState;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * Befehle des Mods.
 *
 * <ul>
 *   <li>{@code /f47 base} baut denselben Stuetzpunkt wie der Basis-Bausatz -
 *       praktisch, wenn man den Gegenstand nicht zur Hand hat, und die einzige
 *       Moeglichkeit, die Anlage ohne Spieler zu errichten.</li>
 *   <li>{@code /f47 base <pos> <partei>} baut ihn fuer eine bestimmte Seite.</li>
 *   <li>{@code /f47 war} stellt beide Parteien auf einmal auf, weit genug
 *       auseinander zum Starten und nah genug, dass sie sich finden.</li>
 *   <li>{@code /f47 unload} gibt die dauerhaft geladenen Chunks wieder frei.</li>
 * </ul>
 */
public final class ModCommands {
	private ModCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
				dispatcher.register(CommandManager.literal("f47")
						.requires(source -> source.hasPermissionLevel(2))
						.then(CommandManager.literal("base")
								// Ohne Angabe: dort, wo der Spieler steht und hinschaut.
								.executes(context -> buildHere(context, null, null))
								.then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
										.executes(context -> buildHere(context,
												BlockPosArgumentType.getBlockPos(context, "pos"), null))
										.then(CommandManager.literal("blau")
												.executes(context -> buildHere(context,
														BlockPosArgumentType.getBlockPos(context, "pos"), Team.BLUE)))
										.then(CommandManager.literal("rot")
												.executes(context -> buildHere(context,
														BlockPosArgumentType.getBlockPos(context, "pos"), Team.RED)))))
						.then(CommandManager.literal("war")
								.executes(ModCommands::startWar))
						.then(CommandManager.literal("unload")
								.executes(ModCommands::releaseChunks))));
	}

	private static int buildHere(CommandContext<ServerCommandSource> context, @Nullable BlockPos given,
			@Nullable Team chosen) {
		ServerCommandSource source = context.getSource();
		ServerWorld world = source.getWorld();
		PlayerEntity player = source.getPlayer();

		BlockPos origin = given != null
				? given.up()
				: BlockPos.ofFloored(source.getPosition());
		Direction facing = player != null ? player.getHorizontalFacing() : Direction.NORTH;
		Team team = chosen != null
				? chosen
				: (player != null ? TeamState.teamOf(player) : Team.BLUE);

		int placed = BaseBlueprint.build(world, origin, facing, player, team);
		source.sendFeedback(() -> Text.translatable("message.f47.base_built",
				placed, Text.translatable(team.translationKey()).formatted(team.formatting())), true);
		return placed;
	}

	/**
	 * Stellt zwei verfeindete Stuetzpunkte einander gegenueber.
	 *
	 * <p>Eine einzelne Basis kaempft gegen niemanden - es gibt schlicht keinen
	 * Gegner. Dieser Befehl setzt beide Seiten so weit auseinander, dass jede
	 * ihre Bahn hat, und so nah, dass die Patrouillen einander in die
	 * Bordradarreichweite fliegen. Danach laeuft der Krieg von allein.
	 */
	private static int startWar(CommandContext<ServerCommandSource> context) {
		ServerCommandSource source = context.getSource();
		ServerWorld world = source.getWorld();
		PlayerEntity player = source.getPlayer();

		BlockPos centre = BlockPos.ofFloored(source.getPosition());
		Direction facing = player != null ? player.getHorizontalFacing() : Direction.NORTH;
		Direction across = facing.rotateYClockwise();
		int gap = Math.max(80, F47Config.get().warBaseSeparation);

		// Beide Bahnen laufen parallel, die Basen liegen sich seitlich gegenueber.
		BlockPos blue = centre.offset(across, -gap / 2);
		BlockPos red = centre.offset(across, gap / 2);

		int placed = BaseBlueprint.build(world, blue, facing, player, Team.BLUE)
				+ BaseBlueprint.build(world, red, facing.getOpposite(), null, Team.RED);

		source.sendFeedback(() -> Text.translatable("message.f47.war_started", placed, gap), true);
		return placed;
	}

	/**
	 * Gibt alle dauerhaft geladenen Chunks wieder frei - fuer den Fall, dass
	 * mehrere Stuetzpunkte den Rechner ausbremsen.
	 */
	private static int releaseChunks(CommandContext<ServerCommandSource> context) {
		ServerCommandSource source = context.getSource();
		ServerWorld world = source.getWorld();

		// Kopieren, bevor wir darueber laufen - setChunkForced aendert die Menge.
		long[] forced = world.getForcedChunks().toLongArray();
		for (long packed : forced) {
			world.setChunkForced(ChunkPos.getPackedX(packed), ChunkPos.getPackedZ(packed), false);
		}

		source.sendFeedback(() -> Text.translatable("message.f47.chunks_released", forced.length), true);
		return forced.length;
	}
}
