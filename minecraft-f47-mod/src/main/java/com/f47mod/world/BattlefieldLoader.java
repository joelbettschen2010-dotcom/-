package com.f47mod.world;

import com.f47mod.F47Config;
import com.f47mod.F47Mod;
import com.f47mod.util.Team;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Haelt den Luftraum zwischen zwei verfeindeten Stuetzpunkten gerechnet.
 *
 * <p>Der Stuetzpunkt selbst wird beim Bau festgehalten - der Raum dazwischen
 * aber nicht, und genau dort findet der Krieg statt. Was dabei herauskam, sah
 * so aus: Die Staffel startete, flog dem Gegner entgegen, verliess den
 * gehaltenen Bereich und blieb dort stehen, weil Minecraft ausserhalb nicht
 * mehr rechnet. Aus der Ferne wirkte das, als geschehe kurz etwas und dann
 * nichts mehr - und wer hinflog, brachte den Bereich mit seinem eigenen
 * Spieler wieder zum Laufen. Deshalb "geht es nur, wenn ich hinschaue".
 *
 * <p>Hier wird deshalb der ganze Streifen zwischen zwei gegnerischen
 * Stuetzpunkten gehalten, sobald sie nah genug beieinander liegen. Das kostet
 * echten Speicher - ein Chunk bleibt ein Chunk - und ist darum nach oben
 * gedeckelt: Ueber {@link F47Config#battlefieldChunkBudget} hinaus wird nichts
 * mehr gehalten, und wer die Basen weiter auseinandersetzt als
 * {@link F47Config#battlefieldSpan}, bekommt gar keinen Streifen. Fuer weite
 * Einsaetze bleibt {@link MissionLoader} zustaendig, der nur eine kleine Blase
 * je Einheit mitfuehrt.
 */
public final class BattlefieldLoader {
	/** Wie oft nachgesehen wird. Selten - Stuetzpunkte wandern nicht. */
	private static final int INTERVAL = 100;
	/** Chunks je Tick. Jeder kann die Weltgenerierung ausloesen. */
	private static final int CHUNKS_PER_TICK = 2;

	/** Was wir halten, und was wir halten wollen. */
	private static final Set<Long> HELD = new HashSet<>();
	private static final List<Long> PENDING = new ArrayList<>();
	private static Set<Long> wanted = Set.of();

	private BattlefieldLoader() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerWorld world : server.getWorlds()) {
				if (server.getTicks() % INTERVAL == 0) {
					plan(world);
				}
				apply(world);
			}
		});
	}

	/** Stellt fest, welcher Streifen gehalten werden soll. */
	private static void plan(ServerWorld world) {
		int budget = F47Config.get().battlefieldChunkBudget;
		if (budget <= 0) {
			wanted = Set.of();
			return;
		}
		Map<Team, List<BlockPos>> bases = BaseRegistry.all(world);
		double span = F47Config.get().battlefieldSpan;
		Set<Long> next = new HashSet<>();

		// Jedes Paar verfeindeter Stuetzpunkte bekommt seinen Streifen.
		for (Map.Entry<Team, List<BlockPos>> mine : bases.entrySet()) {
			for (Map.Entry<Team, List<BlockPos>> theirs : bases.entrySet()) {
				if (mine.getKey().ordinal() >= theirs.getKey().ordinal()) {
					continue;
				}
				for (BlockPos one : mine.getValue()) {
					for (BlockPos two : theirs.getValue()) {
						if (!one.isWithinDistance(two, span)) {
							continue;
						}
						collect(next, one, two, budget);
					}
				}
			}
		}
		wanted = next;

		PENDING.clear();
		for (long packed : wanted) {
			if (!HELD.contains(packed)) {
				PENDING.add(packed);
			}
		}
	}

	/** Sammelt die Chunks des Rechtecks zwischen zwei Stuetzpunkten. */
	private static void collect(Set<Long> into, BlockPos one, BlockPos two, int budget) {
		ChunkPos first = new ChunkPos(one);
		ChunkPos second = new ChunkPos(two);
		int minX = Math.min(first.x, second.x);
		int maxX = Math.max(first.x, second.x);
		int minZ = Math.min(first.z, second.z);
		int maxZ = Math.max(first.z, second.z);
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				if (into.size() >= budget) {
					return;
				}
				into.add(ChunkPos.toLong(x, z));
			}
		}
	}

	/** Fordert nach und gibt frei - gedrosselt, damit nichts einfriert. */
	private static void apply(ServerWorld world) {
		for (int i = 0; i < CHUNKS_PER_TICK && !PENDING.isEmpty(); i++) {
			long packed = PENDING.remove(PENDING.size() - 1);
			if (HELD.add(packed)) {
				world.setChunkForced(ChunkPos.getPackedX(packed), ChunkPos.getPackedZ(packed), true);
			}
		}
		if (!PENDING.isEmpty()) {
			return;
		}
		HELD.removeIf(packed -> {
			if (wanted.contains(packed)) {
				return false;
			}
			world.setChunkForced(ChunkPos.getPackedX(packed), ChunkPos.getPackedZ(packed), false);
			return true;
		});
	}

	/** Gibt alles frei - fuer {@code /f47 unload}. */
	public static void release(ServerWorld world) {
		for (long packed : HELD) {
			world.setChunkForced(ChunkPos.getPackedX(packed), ChunkPos.getPackedZ(packed), false);
		}
		if (!HELD.isEmpty()) {
			F47Mod.LOGGER.info("[F-47] {} Chunks des Gefechtsstreifens freigegeben", HELD.size());
		}
		HELD.clear();
		PENDING.clear();
		wanted = Set.of();
	}

	/** Wie viele Chunks der Streifen gerade haelt - fuer den Lagebericht. */
	public static int heldChunks() {
		return HELD.size();
	}
}
