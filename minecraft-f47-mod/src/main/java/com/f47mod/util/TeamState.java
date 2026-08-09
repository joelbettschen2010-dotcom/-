package com.f47mod.util;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Merkt sich, fuer welche Partei ein Spieler kaempft. Wird im Spielstand
 * gespeichert, damit die Zugehoerigkeit einen Neustart uebersteht.
 */
public class TeamState extends PersistentState {
	private static final String KEY = "f47_teams";

	/**
	 * Wichtig: Der Datenreparatur-Typ darf nicht null sein. Minecraft ruft ihn
	 * beim Einlesen einer bereits gespeicherten Datei ohne Null-Pruefung auf -
	 * mit null wuerde also erst der zweite Start einer Welt abstuerzen.
	 * SAVED_DATA_SCOREBOARD passt: Es kennt keine unserer Schluessel und
	 * laesst die Daten daher unveraendert durch.
	 */
	public static final Type<TeamState> TYPE =
			new Type<>(TeamState::new, TeamState::fromNbt, DataFixTypes.SAVED_DATA_SCOREBOARD);

	private final Map<UUID, Team> playerTeams = new HashMap<>();

	public static TeamState get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, KEY);
	}

	/** Partei eines Spielers. Standard ist Blau. */
	public static Team teamOf(PlayerEntity player) {
		World world = player.getWorld();
		if (world.isClient || world.getServer() == null) {
			// Auf dem Client ist die eigene Partei nicht noetig - die
			// Anzeige bekommt bereits ausgewertete Daten vom Server.
			return Team.BLUE;
		}
		return get(world.getServer()).playerTeams.getOrDefault(player.getUuid(), Team.BLUE);
	}

	public static void setTeam(PlayerEntity player, Team team) {
		World world = player.getWorld();
		if (world.isClient || world.getServer() == null) {
			return;
		}
		TeamState state = get(world.getServer());
		state.playerTeams.put(player.getUuid(), team);
		state.markDirty();
	}

	private static TeamState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		TeamState state = new TeamState();
		NbtCompound players = nbt.getCompound("Players");
		for (String key : players.getKeys()) {
			try {
				state.playerTeams.put(UUID.fromString(key), Team.byOrdinal(players.getInt(key)));
			} catch (IllegalArgumentException ignored) {
				// Unbrauchbarer Eintrag - der Spieler faengt wieder bei Blau an.
			}
		}
		return state;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		NbtCompound players = new NbtCompound();
		playerTeams.forEach((uuid, team) -> players.putInt(uuid.toString(), team.ordinal()));
		nbt.put("Players", players);
		return nbt;
	}
}
