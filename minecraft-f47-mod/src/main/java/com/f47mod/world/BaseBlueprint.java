package com.f47mod.world;

import com.f47mod.block.HangarDoorBlock;
import com.f47mod.block.RunwayMarkingBlock;
import com.f47mod.block.entity.BarracksBlockEntity;
import com.f47mod.block.entity.IronDomeBlockEntity;
import com.f47mod.entity.mob.SoldierEntity;
import com.f47mod.entity.mob.SoldierRole;
import com.f47mod.entity.vehicle.AutonomousF47Entity;
import com.f47mod.entity.vehicle.F47Entity;
import com.f47mod.registry.ModBlocks;
import com.f47mod.registry.ModEntities;
import com.f47mod.util.Team;
import com.f47mod.util.TeamMember;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Baut einen kompletten Luftwaffenstuetzpunkt in einem Rutsch.
 *
 * <p>Von Hand eine 60 Bloecke lange Bahn zu legen ist muehsam - deshalb setzt
 * dieser Bausatz alles auf einmal: Start- und Landebahn mit Markierungen und
 * Befeuerung, Hangar mit Rolltor, Wartungsfeld, Radarstation, zwei
 * Iron-Dome-Stellungen und eine Kaserne. Dazu kommen ein einsatzbereiter Jet,
 * ein Drohnenjaeger mit Pilot und etwas Bodenpersonal.
 *
 * <p>Die Anlage richtet sich nach der Blickrichtung des Spielers aus und
 * gehoert seiner Partei - so lassen sich mit zwei Bausaetzen zwei
 * gegnerische Basen aufstellen.
 */
public final class BaseBlueprint {
	/** Laenge der Bahn in Bloecken. Kuerzer reicht zum Abheben nicht. */
	private static final int RUNWAY_LENGTH = 60;
	/** Halbe Breite der Bahn (3 = 7 Bloecke breit). */
	private static final int RUNWAY_HALF_WIDTH = 3;
	/** Wie weit neben der Bahn die Gebaeude stehen. */
	private static final int APRON_OFFSET = 7;
	/** Hoehe, die ueber der Anlage freigeraeumt wird. */
	private static final int CLEAR_HEIGHT = 9;

	private BaseBlueprint() {
	}

	/**
	 * Errichtet die Anlage.
	 *
	 * @param origin Punkt, auf den geklickt wurde - dort beginnt die Bahn
	 * @param facing Richtung, in die die Bahn zeigt
	 * @return Anzahl gesetzter Bloecke, nur fuer die Rueckmeldung
	 */
	public static int build(ServerWorld world, BlockPos origin, Direction facing,
			@Nullable PlayerEntity owner, Team team) {
		// Rechtwinklig zur Bahn - dort stehen Hangar und Anlagen.
		Direction side = facing.rotateYClockwise();
		int ground = origin.getY();
		Counter placed = new Counter();

		clearArea(world, origin, facing, side, ground, placed);
		buildRunway(world, origin, facing, side, ground, placed);
		buildApron(world, origin, facing, side, ground, team, owner, placed);
		spawnUnits(world, origin, facing, side, ground, team, owner);

		return placed.value;
	}

	// ------------------------------------------------------------------
	// Gelaende vorbereiten
	// ------------------------------------------------------------------

	/**
	 * Macht Platz: alles ueber dem Boden weg, Loecher darunter auffuellen.
	 * Ohne das laege die Bahn auf huegeligem Gelaende in der Luft oder waere
	 * von Baeumen verstellt.
	 */
	private static void clearArea(ServerWorld world, BlockPos origin, Direction facing,
			Direction side, int ground, Counter placed) {
		for (int forward = -2; forward < RUNWAY_LENGTH + 2; forward++) {
			for (int across = -RUNWAY_HALF_WIDTH - 2; across <= APRON_OFFSET + 14; across++) {
				BlockPos column = origin.offset(facing, forward).offset(side, across);

				// Ueber dem Boden freiraeumen.
				for (int y = 1; y <= CLEAR_HEIGHT; y++) {
					BlockPos above = column.up(y).withY(ground + y);
					if (!world.getBlockState(above).isAir()) {
						set(world, above, Blocks.AIR.getDefaultState(), placed);
					}
				}
				// Unterbau: drei Schichten, damit nichts in der Luft haengt.
				for (int y = 0; y >= -2; y--) {
					BlockPos below = column.withY(ground + y);
					if (world.getBlockState(below).isAir() || !world.getFluidState(below).isEmpty()) {
						set(world, below, ModBlocks.RUNWAY.getDefaultState(), placed);
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------
	// Start- und Landebahn
	// ------------------------------------------------------------------

	private static void buildRunway(ServerWorld world, BlockPos origin, Direction facing,
			Direction side, int ground, Counter placed) {
		BlockState runway = ModBlocks.RUNWAY.getDefaultState();
		BlockState centerline = ModBlocks.RUNWAY_CENTERLINE.getDefaultState()
				.with(RunwayMarkingBlock.FACING, facing);
		BlockState threshold = ModBlocks.RUNWAY_THRESHOLD.getDefaultState()
				.with(RunwayMarkingBlock.FACING, facing);

		for (int forward = 0; forward < RUNWAY_LENGTH; forward++) {
			// Schwellenmarkierung an beiden Enden.
			boolean isThreshold = forward < 3 || forward >= RUNWAY_LENGTH - 3;

			for (int across = -RUNWAY_HALF_WIDTH; across <= RUNWAY_HALF_WIDTH; across++) {
				BlockPos pos = origin.offset(facing, forward).offset(side, across).withY(ground);
				BlockState state;
				if (isThreshold) {
					state = threshold;
				} else if (across == 0 && forward % 4 < 2) {
					// Unterbrochene Mittellinie.
					state = centerline;
				} else {
					state = runway;
				}
				set(world, pos, state, placed);
			}

			// Bahnbefeuerung alle 8 Bloecke an beiden Raendern.
			if (forward % 8 == 4) {
				for (int sign = -1; sign <= 1; sign += 2) {
					BlockPos light = origin.offset(facing, forward)
							.offset(side, sign * (RUNWAY_HALF_WIDTH + 1)).withY(ground);
					set(world, light, ModBlocks.RUNWAY_LIGHT.getDefaultState(), placed);
				}
			}
		}
	}

	// ------------------------------------------------------------------
	// Hangar und Anlagen
	// ------------------------------------------------------------------

	private static void buildApron(ServerWorld world, BlockPos origin, Direction facing,
			Direction side, int ground, Team team, @Nullable PlayerEntity owner, Counter placed) {
		BlockState wall = ModBlocks.HANGAR_WALL.getDefaultState();

		// --- Vorfeld vor dem Hangar ---
		for (int forward = 6; forward <= 20; forward++) {
			for (int across = RUNWAY_HALF_WIDTH + 1; across <= APRON_OFFSET + 12; across++) {
				set(world, origin.offset(facing, forward).offset(side, across).withY(ground),
						ModBlocks.RUNWAY.getDefaultState(), placed);
			}
		}

		// --- Hangar: 9 breit, 8 tief, 5 hoch, Tor zur Bahn hin ---
		int hangarFront = 8;
		int hangarBack = hangarFront + 8;
		int hangarLeft = APRON_OFFSET;
		int hangarRight = hangarLeft + 8;

		for (int forward = hangarFront; forward <= hangarBack; forward++) {
			for (int across = hangarLeft; across <= hangarRight; across++) {
				boolean edge = forward == hangarFront || forward == hangarBack
						|| across == hangarLeft || across == hangarRight;
				for (int y = 1; y <= 5; y++) {
					BlockPos pos = origin.offset(facing, forward).offset(side, across).withY(ground + y);
					if (y == 5) {
						// Dach
						set(world, pos, wall, placed);
					} else if (edge) {
						// Torlaibung in der Vorderwand offen lassen.
						boolean doorway = forward == hangarFront
								&& across > hangarLeft + 1 && across < hangarRight - 1;
						if (doorway) {
							set(world, pos, ModBlocks.HANGAR_DOOR.getDefaultState()
									.with(HangarDoorBlock.OPEN, true), placed);
						} else {
							set(world, pos, wall, placed);
						}
					}
				}
			}
		}

		// --- Wartungsfeld im Hangar ---
		for (int forward = hangarFront + 2; forward <= hangarFront + 4; forward++) {
			for (int across = hangarLeft + 3; across <= hangarLeft + 5; across++) {
				set(world, origin.offset(facing, forward).offset(side, across).withY(ground),
						ModBlocks.REARM_PAD.getDefaultState(), placed);
			}
		}

		// --- Radarstation, etwas abseits mit freiem Blick ---
		BlockPos radar = origin.offset(facing, 26).offset(side, APRON_OFFSET + 4).withY(ground + 1);
		set(world, radar.down(), ModBlocks.HANGAR_WALL.getDefaultState(), placed);
		set(world, radar, ModBlocks.RADAR.getDefaultState(), placed);
		assignTeam(world, radar, team);

		// --- Zwei Iron-Dome-Stellungen, links und rechts der Bahn ---
		BlockPos[] domes = {
				origin.offset(facing, 14).offset(side, -RUNWAY_HALF_WIDTH - 4).withY(ground + 1),
				origin.offset(facing, 34).offset(side, APRON_OFFSET + 9).withY(ground + 1),
		};
		for (BlockPos dome : domes) {
			set(world, dome.down(), ModBlocks.HANGAR_WALL.getDefaultState(), placed);
			set(world, dome, ModBlocks.IRON_DOME.getDefaultState(), placed);
			assignTeam(world, dome, team);
			// Gleich einsatzbereit ausliefern - sonst steht die Abwehr leer da.
			if (world.getBlockEntity(dome) instanceof IronDomeBlockEntity launcher) {
				launcher.load(IronDomeBlockEntity.MAX_AMMO);
			}
		}

		// --- Kaserne ---
		BlockPos barracks = origin.offset(facing, 20).offset(side, APRON_OFFSET + 10).withY(ground + 1);
		set(world, barracks, ModBlocks.BARRACKS.getDefaultState(), placed);
		assignTeam(world, barracks, team);
		if (world.getBlockEntity(barracks) instanceof BarracksBlockEntity kaserne) {
			kaserne.addSupplies(32);
			if (owner != null) {
				kaserne.setOwner(owner);
			}
		}
	}

	// ------------------------------------------------------------------
	// Einheiten
	// ------------------------------------------------------------------

	private static void spawnUnits(ServerWorld world, BlockPos origin, Direction facing,
			Direction side, int ground, Team team, @Nullable PlayerEntity owner) {
		float yaw = facing.getOpposite().asRotation();
		BlockPos base = origin.offset(facing, 20).offset(side, APRON_OFFSET + 4).withY(ground);

		// Ein bemannbarer Jet am Anfang der Bahn.
		F47Entity jet = ModEntities.F47.create(world);
		if (jet != null) {
			BlockPos spot = origin.offset(facing, 4).withY(ground + 1);
			jet.refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, yaw, 0.0f);
			jet.setTeam(team);
			world.spawnEntity(jet);
		}

		// Ein Drohnenjaeger samt Pilot, der ihn selbst besteigt.
		AutonomousF47Entity drone = ModEntities.AUTONOMOUS_F47.create(world);
		if (drone != null) {
			BlockPos spot = origin.offset(facing, 10).offset(side, RUNWAY_HALF_WIDTH + 3).withY(ground + 1);
			drone.refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, yaw, 0.0f);
			drone.setTeam(team);
			drone.setHomeBase(origin.withY(ground));
			drone.randomiseCallsign();
			drone.setOwner(owner);
			world.spawnEntity(drone);
		}

		// Bodenpersonal: ein Pilot, ein Techniker, zwei Schuetzen.
		spawnSoldier(world, base.offset(side, -2), yaw, SoldierRole.PILOT, team, owner);
		spawnSoldier(world, base.offset(side, -1), yaw, SoldierRole.ENGINEER, team, owner);
		spawnSoldier(world, base.offset(facing, 2), yaw, SoldierRole.RIFLEMAN, team, owner);
		spawnSoldier(world, base.offset(facing, 3), yaw, SoldierRole.RIFLEMAN, team, owner);
	}

	private static void spawnSoldier(ServerWorld world, BlockPos pos, float yaw, SoldierRole role,
			Team team, @Nullable PlayerEntity owner) {
		SoldierEntity soldier = ModEntities.SOLDIER.create(world);
		if (soldier == null) {
			return;
		}
		soldier.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, yaw, 0.0f);
		soldier.initialize(world, world.getLocalDifficulty(pos), SpawnReason.TRIGGERED, null);
		soldier.setTeam(team);
		soldier.setRole(role);
		soldier.setOwner(owner);
		// Am Posten bleiben, damit die Basis bewacht ist.
		soldier.setStance(SoldierEntity.Stance.PATROL);
		soldier.setGuardPost(pos);
		world.spawnEntity(soldier);
	}

	// ------------------------------------------------------------------
	// Hilfsmittel
	// ------------------------------------------------------------------

	private static void set(World world, BlockPos pos, BlockState state, Counter placed) {
		// NOTIFY_LISTENERS statt NOTIFY_ALL: Bei tausenden Bloecken am Stueck
		// sind Nachbar-Aktualisierungen unnoetig teuer.
		if (world.setBlockState(pos, state, net.minecraft.block.Block.NOTIFY_LISTENERS)) {
			placed.value++;
		}
	}

	private static void assignTeam(World world, BlockPos pos, Team team) {
		BlockEntity blockEntity = world.getBlockEntity(pos);
		if (blockEntity instanceof TeamMember member) {
			member.setTeam(team);
		}
	}

	/** Zaehler in einem Objekt, damit ihn die Hilfsmethoden hochzaehlen koennen. */
	private static final class Counter {
		private int value;
	}
}
