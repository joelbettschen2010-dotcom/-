package com.f47mod.world;

import com.f47mod.F47Config;
import com.f47mod.entity.mob.SoldierEntity;
import com.f47mod.entity.vehicle.AutonomousF47Entity;
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
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.Formatting;
import net.minecraft.util.TypeFilter;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

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
 *   <li>{@code /f47 status} sagt, wer wo steht und ob sich die Parteien
 *       ueberhaupt finden koennen - die haeufigste Ursache dafuer, dass nichts
 *       passiert, ist naemlich, dass nur eine Seite dasteht.</li>
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
								.executes(ModCommands::releaseChunks))
						.then(CommandManager.literal("status")
								.executes(ModCommands::status))));
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
		// Mindestens eine Anlagenbreite plus Rand - sonst baut die zweite Basis
		// mitten in die erste. Mit drei Bahnen ist eine Anlage breiter als der
		// eingestellte Abstand von hundertfuenfzig Bloecken.
		int gap = Math.max(BaseBlueprint.width() + 40, F47Config.get().warBaseSeparation);

		// Beide Bahnen laufen in dieselbe Richtung, die Basen liegen sich rein
		// seitlich gegenueber. Liess man die rote Bahn entgegengesetzt laufen,
		// kam ihre Laenge zum Abstand hinzu: Aus hundertfuenfzig Bloecken
		// wurden zweihundertneunzehn, die geladenen Bereiche beruehrten sich
		// nicht mehr, und wer den Zwischenraum ueberflog, blieb dort stehen.
		BlockPos blue = centre.offset(across, -gap / 2);
		BlockPos red = centre.offset(across, gap / 2);

		int placed = BaseBlueprint.build(world, blue, facing, player, Team.BLUE)
				+ BaseBlueprint.build(world, red, facing, null, Team.RED);

		source.sendFeedback(() -> Text.translatable("message.f47.war_started", placed, gap), true);
		return placed;
	}

	/**
	 * Lagebericht: Wer steht wo, und koennen sich die Parteien ueberhaupt
	 * finden?
	 *
	 * <p>Die haeufigste Ursache dafuer, dass nichts passiert, ist banal - es
	 * steht nur eine Partei da, oder die beiden Basen liegen zu weit
	 * auseinander. Beides sieht man im Spiel nicht, ohne hinzufliegen.
	 */
	private static int status(CommandContext<ServerCommandSource> context) {
		ServerCommandSource source = context.getSource();
		ServerWorld world = source.getWorld();

		Map<Team, Integer> jets = new EnumMap<>(Team.class);
		Map<Team, Integer> troops = new EnumMap<>(Team.class);
		Map<Team, Vec3d> centre = new EnumMap<>(Team.class);

		for (AutonomousF47Entity jet : world.getEntitiesByType(
				TypeFilter.instanceOf(AutonomousF47Entity.class), e -> true)) {
			jets.merge(jet.getTeam(), 1, Integer::sum);
			BlockPos home = jet.getHomeBase();
			if (home != null) {
				centre.putIfAbsent(jet.getTeam(), Vec3d.ofCenter(home));
			}
		}
		// Energiewaffentruppe getrennt zaehlen: Sie traegt den Vormarsch, und
		// ob davon noch jemand steht, sieht man sonst nirgends.
		Map<Team, Integer> energy = new EnumMap<>(Team.class);
		for (SoldierEntity soldier : world.getEntitiesByType(
				TypeFilter.instanceOf(SoldierEntity.class), e -> true)) {
			troops.merge(soldier.getTeam(), 1, Integer::sum);
			if (soldier.getRole().usesEnergyWeapon()) {
				energy.merge(soldier.getTeam(), 1, Integer::sum);
			}
		}

		for (Team team : Team.values()) {
			Text name = Text.translatable(team.translationKey()).formatted(team.formatting());
			source.sendFeedback(() -> Text.translatable("message.f47.status_side", name,
					jets.getOrDefault(team, 0), troops.getOrDefault(team, 0),
					energy.getOrDefault(team, 0)), false);
		}

		Vec3d blue = centre.get(Team.BLUE);
		Vec3d red = centre.get(Team.RED);
		if (blue == null || red == null) {
			// Genau der Fall, in dem nichts passiert und niemand weiss warum.
			source.sendFeedback(() -> Text.translatable("message.f47.status_one_side")
					.formatted(Formatting.YELLOW), false);
			return 0;
		}

		int distance = (int) blue.distanceTo(red);
		// Die Grenze ist die Suchreichweite der Einheiten, nicht mehr die alte
		// feste Zahl: Seit der gerechnete Bereich mitfliegt, greifen sie auch
		// ueber hunderte Bloecke an.
		boolean reachable = distance <= F47Config.get().strikeRange;
		source.sendFeedback(() -> Text.translatable("message.f47.status_distance", distance,
				Text.translatable(reachable ? "message.f47.status_ok" : "message.f47.status_too_far")
						.formatted(reachable ? Formatting.GREEN : Formatting.RED)), false);
		source.sendFeedback(() -> Text.translatable("message.f47.status_chunks",
				MissionLoader.heldChunks()), false);
		return distance;
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
