package com.f47mod.world;

import com.f47mod.block.entity.BarracksBlockEntity;
import com.f47mod.block.entity.IronDomeBlockEntity;
import com.f47mod.util.Team;
import com.f47mod.util.TeamMember;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Setzt einen geplanten Stuetzpunkt ueber mehrere Ticks hinweg.
 *
 * <p>Eine Anlage besteht aus rund 14.000 Bloecken. Alle auf einmal zu setzen
 * blockiert den Server fuer mehrere Sekunden - im Einzelspieler friert das
 * Spiel dabei sichtbar ein und sieht aus wie ein Absturz. Deshalb wandert der
 * Bauplan in eine Warteschlange und wird haeppchenweise abgearbeitet: Das
 * Ergebnis ist dasselbe, aber das Spiel laeuft waehrenddessen weiter, und man
 * sieht die Anlage entstehen.
 */
public final class BaseConstruction {
	/**
	 * Bloecke pro Tick. Bei diesem Wert steht eine Anlage nach gut zwei
	 * Sekunden, ohne dass ein Tick die 50-ms-Grenze reisst.
	 */
	private static final int BLOCKS_PER_TICK = 6000;

	/**
	 * Chunks, die pro Tick dauerhaft geladen werden.
	 *
	 * <p>Bewusst einer: Jedes neue Ticket kann die Weltgenerierung eines noch
	 * nie besuchten Chunks ausloesen, und das ist mit Abstand der teuerste
	 * Teil des ganzen Vorgangs - alle einundachtzig auf einmal anzufordern
	 * hat den Server viereinhalb Sekunden lang blockiert.
	 */
	private static final int CHUNKS_PER_TICK = 1;

	private static final Deque<Job> QUEUE = new ArrayDeque<>();

	private BaseConstruction() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (QUEUE.isEmpty()) {
				return;
			}
			Job job = QUEUE.peek();
			job.advance();
			if (job.isDone()) {
				QUEUE.poll();
			}
		});
	}

	/** Stellt einen fertigen Bauplan in die Warteschlange. */
	public static void enqueue(Plan plan) {
		QUEUE.add(new Job(plan));
	}

	/** Wartet gerade noch etwas auf den Bau? Fuer Tests und Rueckmeldungen. */
	public static boolean isBusy() {
		return !QUEUE.isEmpty();
	}

	// ------------------------------------------------------------------

	/**
	 * Ein Bauplan: erst die Bloecke, danach die Nacharbeiten (Parteizuordnung,
	 * Munition, Einheiten). Die Reihenfolge ist wichtig - eine Radarstation
	 * bekommt ihre Partei erst, wenn der Block auch wirklich steht.
	 */
	public static final class Plan {
		private final ServerWorld world;
		private final List<BlockPos> positions = new ArrayList<>();
		private final List<BlockState> states = new ArrayList<>();
		private final List<Runnable> finishers = new ArrayList<>();
		private final List<ChunkPos> chunks = new ArrayList<>();

		public Plan(ServerWorld world) {
			this.world = world;
		}

		public ServerWorld world() {
			return world;
		}

		/** Merkt einen Block vor. Gesetzt wird er erst beim Abarbeiten. */
		public void set(BlockPos pos, BlockState state) {
			positions.add(pos.toImmutable());
			states.add(state);
		}

		/** Nacharbeit, die laeuft, sobald alle Bloecke stehen. */
		public void afterwards(Runnable action) {
			finishers.add(action);
		}

		/** Ordnet den Block einer Partei zu, sobald er steht. */
		public void assignTeam(BlockPos pos, Team team) {
			BlockPos frozen = pos.toImmutable();
			afterwards(() -> {
				BlockEntity blockEntity = world.getBlockEntity(frozen);
				if (blockEntity instanceof TeamMember member) {
					member.setTeam(team);
				}
			});
		}

		/** Fuellt eine Iron-Dome-Stellung, sobald sie steht. */
		public void loadDome(BlockPos pos) {
			BlockPos frozen = pos.toImmutable();
			afterwards(() -> {
				if (world.getBlockEntity(frozen) instanceof IronDomeBlockEntity launcher) {
					launcher.load(IronDomeBlockEntity.MAX_AMMO);
				}
			});
		}

		/**
		 * Versorgt eine Kaserne und sagt ihr, wo die Bahn liegt - dorthin
		 * stellt sie spaeter ihre Ersatzmaschinen.
		 */
		public void stockBarracks(BlockPos pos, int supplies, BlockPos home, float heading,
				@Nullable PlayerEntity owner) {
			BlockPos frozen = pos.toImmutable();
			BlockPos frozenHome = home.toImmutable();
			afterwards(() -> {
				if (world.getBlockEntity(frozen) instanceof BarracksBlockEntity barracks) {
					barracks.addSupplies(supplies);
					barracks.setHomeBase(frozenHome, heading);
					if (owner != null) {
						barracks.setOwner(owner);
					}
				}
			});
		}

		/**
		 * Merkt einen Chunk vor, der nach dem Bau dauerhaft geladen bleiben
		 * soll. Angefordert wird er einzeln und erst am Schluss - siehe
		 * {@link #CHUNKS_PER_TICK}.
		 */
		public void keepLoaded(ChunkPos chunk) {
			chunks.add(chunk);
		}

		public int blockCount() {
			return positions.size();
		}
	}

	/**
	 * Der Fortschritt eines Bauplans: erst Bloecke, dann Nacharbeiten, zuletzt
	 * das dauerhafte Laden der Umgebung.
	 */
	private static final class Job {
		private final Plan plan;
		private int index;
		private int chunkIndex;
		private boolean finished;

		private Job(Plan plan) {
			this.plan = plan;
		}

		private void advance() {
			if (index < plan.positions.size()) {
				int end = Math.min(plan.positions.size(), index + BLOCKS_PER_TICK);
				for (; index < end; index++) {
					// NOTIFY_LISTENERS statt NOTIFY_ALL: Bei tausenden Bloecken
					// am Stueck sind Nachbar-Aktualisierungen unnoetig teuer.
					plan.world.setBlockState(plan.positions.get(index), plan.states.get(index),
							Block.NOTIFY_LISTENERS);
				}
				if (index < plan.positions.size()) {
					return;
				}
				// Alle Bloecke stehen - jetzt Partei, Munition und Einheiten.
				for (Runnable action : plan.finishers) {
					action.run();
				}
			}

			int end = Math.min(plan.chunks.size(), chunkIndex + CHUNKS_PER_TICK);
			for (; chunkIndex < end; chunkIndex++) {
				ChunkPos chunk = plan.chunks.get(chunkIndex);
				plan.world.setChunkForced(chunk.x, chunk.z, true);
			}
			finished = chunkIndex >= plan.chunks.size();
		}

		private boolean isDone() {
			return finished;
		}
	}
}
