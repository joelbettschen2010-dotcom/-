package com.f47mod.world;

import com.f47mod.F47Config;
import com.f47mod.F47Mod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;

import java.util.HashSet;
import java.util.Set;

/**
 * Haelt das Gelaende unter unterwegs befindlichen Einheiten gerechnet.
 *
 * <p>Minecraft rechnet nur dort, wo ein Spieler ist oder wo ein Chunk
 * ausdruecklich festgehalten wird. Ein fester Bereich um jeden Stuetzpunkt
 * reicht dafuer nicht: Sobald eine Staffel zum Gegner aufbricht, verlaesst sie
 * ihn - und bleibt ausserhalb einfach stehen. Genau daran sind Angriffe ueber
 * groessere Entfernungen bisher gescheitert, und deshalb kreisten die Maschinen
 * nur ueber der eigenen Bahn.
 *
 * <p>Hier wandert der gerechnete Bereich stattdessen <em>mit</em>. Jede
 * unterwegs befindliche Einheit zieht eine kleine Blase geladener Chunks
 * hinter sich her; wo keine mehr ist, wird wieder freigegeben. Damit sind
 * Einsaetze ueber beliebige Entfernungen moeglich - der Aufwand haengt an der
 * Zahl der Einheiten, nicht an der Strecke.
 *
 * <p>Nach oben begrenzt: Mehr als {@link F47Config#missionChunkBudget} Chunks
 * werden nie gehalten, damit ein grosser Krieg den Rechner nicht erdrueckt.
 */
public final class MissionLoader {
	/** Wie oft nachgefuehrt wird. Haeufiger kostet, seltener reisst die Blase ab. */
	private static final int INTERVAL = 20;

	/** Aktuell von uns gehaltene Chunks - nur diese geben wir auch wieder frei. */
	private static final Set<Long> HELD = new HashSet<>();

	private MissionLoader() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTicks() % INTERVAL != 0) {
				return;
			}
			for (ServerWorld world : server.getWorlds()) {
				update(world);
			}
		});
	}

	private static void update(ServerWorld world) {
		F47Config config = F47Config.get();
		int radius = config.missionChunkRadius;
		if (radius <= 0) {
			release(world);
			return;
		}

		Set<Long> wanted = new HashSet<>();
		for (Entity entity : world.iterateEntities()) {
			if (!(entity instanceof MissionUnit unit) || !unit.needsLoadedTerrain()) {
				continue;
			}
			ChunkPos centre = entity.getChunkPos();
			for (int x = -radius; x <= radius; x++) {
				for (int z = -radius; z <= radius; z++) {
					if (wanted.size() >= config.missionChunkBudget) {
						break;
					}
					wanted.add(ChunkPos.toLong(centre.x + x, centre.z + z));
				}
			}
		}

		// Neue anfordern.
		for (long packed : wanted) {
			if (HELD.add(packed)) {
				world.setChunkForced(ChunkPos.getPackedX(packed), ChunkPos.getPackedZ(packed), true);
			}
		}
		// Nicht mehr gebrauchte freigeben.
		HELD.removeIf(packed -> {
			if (wanted.contains(packed)) {
				return false;
			}
			world.setChunkForced(ChunkPos.getPackedX(packed), ChunkPos.getPackedZ(packed), false);
			return true;
		});
	}

	/** Gibt alles frei, was dieser Lader haelt. */
	public static void release(ServerWorld world) {
		for (long packed : HELD) {
			world.setChunkForced(ChunkPos.getPackedX(packed), ChunkPos.getPackedZ(packed), false);
		}
		if (!HELD.isEmpty()) {
			F47Mod.LOGGER.info("[F-47] {} mitgefuehrte Chunks freigegeben", HELD.size());
		}
		HELD.clear();
	}

	/** Wie viele Chunks der Lader gerade haelt - fuer den Lagebericht. */
	public static int heldChunks() {
		return HELD.size();
	}

	/**
	 * Einheiten, die unterwegs eigenes Gelaende brauchen. Eine abgestellte
	 * Maschine meldet sich nicht - die steht ohnehin im Bereich ihres
	 * Stuetzpunkts.
	 */
	public interface MissionUnit {
		boolean needsLoadedTerrain();
	}
}
