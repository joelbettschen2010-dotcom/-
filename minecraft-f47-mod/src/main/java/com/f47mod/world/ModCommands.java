package com.f47mod.world;

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
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * Befehle des Mods.
 *
 * <p>{@code /f47 base} baut denselben Stuetzpunkt wie der Basis-Bausatz -
 * praktisch, wenn man den Gegenstand gerade nicht zur Hand hat, und die einzige
 * Moeglichkeit, die Anlage ohne Spieler zu errichten (etwa von der
 * Serverkonsole aus).
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
								.executes(context -> buildHere(context, null))
								.then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
										.executes(context -> buildHere(context,
												BlockPosArgumentType.getBlockPos(context, "pos")))))));
	}

	private static int buildHere(CommandContext<ServerCommandSource> context, @Nullable BlockPos given) {
		ServerCommandSource source = context.getSource();
		ServerWorld world = source.getWorld();
		PlayerEntity player = source.getPlayer();

		BlockPos origin = given != null
				? given.up()
				: BlockPos.ofFloored(source.getPosition());
		Direction facing = player != null ? player.getHorizontalFacing() : Direction.NORTH;
		Team team = player != null ? TeamState.teamOf(player) : Team.BLUE;

		int placed = BaseBlueprint.build(world, origin, facing, player, team);
		source.sendFeedback(() -> Text.translatable("message.f47.base_built",
				placed, Text.translatable(team.translationKey()).formatted(team.formatting())), true);
		return placed;
	}
}
