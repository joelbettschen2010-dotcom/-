package com.f47mod.world;

import com.f47mod.util.Team;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Merkt sich, wo welche Partei einen Stuetzpunkt hat.
 *
 * <p>Klingt nach Buchhaltung, ist aber der Grund, warum ueberhaupt ein Krieg
 * zustande kommt. Bisher hat jede Einheit den Gegner gesucht, indem sie
 * nachgesehen hat, welche gegnerischen Einheiten gerade in der Welt stehen -
 * und Minecraft rechnet nur dort, wo ein Spieler ist oder wo ein Chunk
 * ausdruecklich festgehalten wird. Steht die gegnerische Basis gerade still,
 * <em>gibt</em> es dort keine Einheiten, die man finden koennte. Beide Seiten
 * hielten sich fuer allein auf der Welt, kreisten ueber der eigenen Bahn und
 * warteten auf einen Gegner, der erst auftauchte, wenn der Spieler hinflog.
 *
 * <p>Ein eingetragener Stuetzpunkt verschwindet dagegen nicht, wenn niemand
 * hinsieht. Er steht im Spielstand und ueberlebt auch einen Neustart. Damit
 * weiss jede Einheit jederzeit, wo der Gegner sitzt - ohne jede Suche und
 * ueber beliebige Entfernung.
 */
public class BaseRegistry extends PersistentState {
	private static final String KEY = "f47_bases";

	/**
	 * Wie bei {@link com.f47mod.util.TeamState}: Der Datenreparatur-Typ darf
	 * nicht null sein, sonst stuerzt erst der zweite Start einer Welt ab.
	 */
	public static final Type<BaseRegistry> TYPE =
			new Type<>(BaseRegistry::new, BaseRegistry::fromNbt, DataFixTypes.SAVED_DATA_SCOREBOARD);

	private final Map<Team, List<BlockPos>> bases = new EnumMap<>(Team.class);

	public static BaseRegistry get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, KEY);
	}

	/**
	 * Traegt einen neuen Stuetzpunkt ein.
	 *
	 * <p>Steht schon einer derselben Partei dicht daneben, wird er nur
	 * aufgefrischt statt doppelt gefuehrt - sonst sammelt sich bei jedem
	 * Wiederaufbau ein weiterer Eintrag an derselben Stelle an.
	 */
	public static void register(World world, Team team, BlockPos centre) {
		if (world.isClient || world.getServer() == null) {
			return;
		}
		BaseRegistry registry = get(world.getServer());
		List<BlockPos> known = registry.bases.computeIfAbsent(team, key -> new ArrayList<>());
		for (BlockPos existing : known) {
			if (existing.isWithinDistance(centre, 200.0)) {
				return;
			}
		}
		known.add(centre.toImmutable());
		registry.markDirty();
	}

	/** Alle bekannten Stuetzpunkte einer Partei. */
	public static List<BlockPos> of(World world, Team team) {
		if (world.isClient || world.getServer() == null) {
			return List.of();
		}
		return List.copyOf(get(world.getServer()).bases.getOrDefault(team, List.of()));
	}

	/** Alle bekannten Stuetzpunkte, gleich welcher Partei. */
	public static Map<Team, List<BlockPos>> all(World world) {
		if (world.isClient || world.getServer() == null) {
			return Map.of();
		}
		return Map.copyOf(get(world.getServer()).bases);
	}

	/** Loescht alle Eintraege - fuer {@code /f47 unload}. */
	public static void clear(MinecraftServer server) {
		BaseRegistry registry = get(server);
		registry.bases.clear();
		registry.markDirty();
	}

	private static BaseRegistry fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		BaseRegistry registry = new BaseRegistry();
		for (Team team : Team.values()) {
			NbtList list = nbt.getList(team.name(), NbtElement.COMPOUND_TYPE);
			List<BlockPos> positions = new ArrayList<>();
			for (int i = 0; i < list.size(); i++) {
				NbtCompound entry = list.getCompound(i);
				positions.add(new BlockPos(entry.getInt("X"), entry.getInt("Y"), entry.getInt("Z")));
			}
			if (!positions.isEmpty()) {
				registry.bases.put(team, positions);
			}
		}
		return registry;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		for (Map.Entry<Team, List<BlockPos>> entry : bases.entrySet()) {
			NbtList list = new NbtList();
			for (BlockPos pos : entry.getValue()) {
				NbtCompound compound = new NbtCompound();
				compound.putInt("X", pos.getX());
				compound.putInt("Y", pos.getY());
				compound.putInt("Z", pos.getZ());
				list.add(compound);
			}
			nbt.put(entry.getKey().name(), list);
		}
		return nbt;
	}
}
