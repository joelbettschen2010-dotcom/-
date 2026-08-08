package com.f47mod.world;

import com.f47mod.F47Config;
import com.f47mod.block.HangarDoorBlock;
import com.f47mod.block.RunwayMarkingBlock;
import com.f47mod.entity.mob.SoldierEntity;
import com.f47mod.entity.mob.SoldierRole;
import com.f47mod.entity.vehicle.ArmoredVehicleEntity;
import com.f47mod.entity.vehicle.AutonomousF47Entity;
import com.f47mod.entity.vehicle.F47Entity;
import com.f47mod.registry.ModBlocks;
import com.f47mod.registry.ModEntities;
import com.f47mod.util.Team;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * Plant einen kompletten Luftwaffenstuetzpunkt.
 *
 * <p>Von Hand eine Bahn samt Hangars zu legen ist muehsam - deshalb entsteht
 * hier alles auf einmal: hundertsechzig Bloecke Start- und Landebahn mit
 * Markierungen und Befeuerung, drei Hangars mit Rolltor und Wartungsfeld,
 * zwei Radarstationen, vier Iron-Dome-Stellungen und zwei Kasernen. Dazu
 * kommen acht Maschinen und sechsundzwanzig Mann Bodenpersonal.
 *
 * <p>Gesetzt werden die Bloecke nicht sofort, sondern ueber
 * {@link BaseConstruction} verteilt auf mehrere Ticks - sonst steht das Spiel
 * beim Bauen sekundenlang still.
 *
 * <p>Die Anlage richtet sich nach der Blickrichtung des Spielers aus und
 * gehoert seiner Partei - so lassen sich mit zwei Bausaetzen zwei
 * gegnerische Basen aufstellen.
 */
public final class BaseBlueprint {
	/**
	 * Laenge der Bahn in Bloecken.
	 *
	 * <p>Nachgerechnet aus dem Flugmodell: Bis zur Rotationsgeschwindigkeit von
	 * 33 m/s braucht die Maschine mit Vollgas rund sechzig Meter Anlauf, und bis
	 * sie sicher weg vom Boden ist nochmal gut achtzig. Mit sechsundneunzig
	 * Bloecken reichte es nur mit Nachbrenner - wer ohne startete, rollte hinten
	 * heraus. Hundertsechzig lassen auch beim Landen Luft.
	 */
	private static final int RUNWAY_LENGTH = 160;
	/** Halbe Breite der Bahn (5 = 11 Bloecke breit). */
	private static final int RUNWAY_HALF_WIDTH = 5;
	/** Wie weit neben der Bahn die Gebaeude stehen. */
	private static final int APRON_OFFSET = 9;
	/** Wie weit das Vorfeld seitlich reicht. */
	private static final int APRON_WIDTH = 26;
	/**
	 * Hoehe, die ueber der Anlage freigeraeumt wird.
	 *
	 * <p>Hoch genug fuer den ganzen Steigflug: Die Maschine steigt mit rund
	 * sechzehn Grad, gewinnt ueber die Bahnlaenge also gut fuenfundvierzig
	 * Bloecke. Mit sechzehn Bloecken Freiraum stieg sie oben aus der Baugrube
	 * heraus und schlug an dem Gelaende an, das darueber stehen geblieben war.
	 *
	 * <p>Teuer ist das nicht: Geraeumt wird nur, was auch dasteht - die
	 * Hoehenkarte sagt vorher, wie weit ueberhaupt zu suchen ist, und in flachem
	 * Gelaende faellt gar nichts an.
	 */
	private static final int CLEAR_HEIGHT = 48;
	/** Lichte Hoehe im Hangar - die Maschine ist gut fuenf Bloecke breit. */
	private static final int HANGAR_HEIGHT = 6;
	/**
	 * Abstellstreifen zwischen Bahnbefeuerung und Hangartor.
	 *
	 * <p>Die Trefferbox ist vier Bloecke breit, reicht von hier also von 8 bis
	 * 12. Links liegt bei 6 die Befeuerung, rechts beginnt bei
	 * {@code APRON_OFFSET + 6} = 15 die Hangarwand - dazwischen steht die
	 * Maschine frei.
	 */
	private static final int PARKING_LANE = 10;

	private BaseBlueprint() {
	}

	/**
	 * Plant die Anlage und stellt sie zum Bau ein.
	 *
	 * @param origin Punkt, auf den geklickt wurde - dort beginnt die Bahn
	 * @param facing Richtung, in die die Bahn zeigt
	 * @return Anzahl vorgemerkter Bloecke, nur fuer die Rueckmeldung
	 */
	public static int build(ServerWorld world, BlockPos origin, Direction facing,
			@Nullable PlayerEntity owner, Team team) {
		// Rechtwinklig zur Bahn - dort stehen Hangars und Anlagen.
		Direction side = facing.rotateYClockwise();
		int ground = origin.getY();
		BaseConstruction.Plan plan = new BaseConstruction.Plan(world);

		clearArea(plan, origin, facing, side, ground);
		buildRunway(plan, origin, facing, side, ground);
		buildApron(plan, origin, facing, side, ground, team, owner);

		// Einheiten erst aufstellen, wenn die Bahn wirklich liegt - sonst
		// fielen sie in das noch offene Gelaende.
		plan.afterwards(() -> spawnUnits(world, origin, facing, side, ground, team, owner));

		keepLoaded(plan, origin.offset(facing, RUNWAY_LENGTH / 2));
		BaseConstruction.enqueue(plan);
		return plan.blockCount();
	}

	/**
	 * Haelt die Chunks rund um den Stuetzpunkt dauerhaft geladen.
	 *
	 * <p>Minecraft rechnet ohne das nur in der Umgebung des Spielers weiter.
	 * Der Stuetzpunkt stuende also still, sobald man ein Stueck wegfliegt:
	 * keine startenden Jets, keine kaempfenden Soldaten, kein Iron Dome - und
	 * genau das sieht dann nach einem toten Mod aus. Das erzwungene Laden
	 * benutzt dasselbe Ticket wie der Vanilla-Befehl {@code /forceload} und
	 * schliesst das Ticken der Einheiten mit ein.
	 *
	 * <p>Die Chunks werden nur vorgemerkt, nicht sofort angefordert: Ein
	 * Ticket auf noch nie besuchtes Gelaende laesst die Welt an dieser Stelle
	 * erst entstehen, und einundachtzig davon auf einmal legen den Server
	 * viereinhalb Sekunden lahm.
	 */
	private static void keepLoaded(BaseConstruction.Plan plan, BlockPos centre) {
		int radius = F47Config.get().baseForceLoadRadiusChunks;
		if (radius <= 0) {
			return;
		}
		ChunkPos middle = new ChunkPos(centre);
		for (int x = -radius; x <= radius; x++) {
			for (int z = -radius; z <= radius; z++) {
				plan.keepLoaded(new ChunkPos(middle.x + x, middle.z + z));
			}
		}
	}

	// ------------------------------------------------------------------
	// Gelaende vorbereiten
	// ------------------------------------------------------------------

	/**
	 * Macht Platz: alles ueber dem Boden weg, Loecher darunter auffuellen.
	 * Ohne das laege die Bahn auf huegeligem Gelaende in der Luft oder waere
	 * von Baeumen verstellt.
	 */
	private static void clearArea(BaseConstruction.Plan plan, BlockPos origin, Direction facing,
			Direction side, int ground) {
		ServerWorld world = plan.world();
		for (int forward = -6; forward <= RUNWAY_LENGTH + 6; forward++) {
			for (int across = -RUNWAY_HALF_WIDTH - 6; across <= APRON_OFFSET + APRON_WIDTH; across++) {
				BlockPos column = origin.offset(facing, forward).offset(side, across);

				// Ueber dem Boden freiraeumen. Wie weit, verraet die Hoehenkarte:
				// Ueber dem hoechsten Block ist ohnehin nur Luft, dort muss gar
				// nicht erst gesucht werden.
				int top = Math.min(world.getTopY(Heightmap.Type.WORLD_SURFACE,
						column.getX(), column.getZ()), ground + CLEAR_HEIGHT);
				for (int y = ground + 1; y <= top; y++) {
					BlockPos above = column.withY(y);
					if (!world.getBlockState(above).isAir()) {
						plan.set(above, Blocks.AIR.getDefaultState());
					}
				}
				// Unterbau: drei Schichten, damit nichts in der Luft haengt.
				for (int y = 0; y >= -2; y--) {
					BlockPos below = column.withY(ground + y);
					if (world.getBlockState(below).isAir() || !world.getFluidState(below).isEmpty()) {
						plan.set(below, ModBlocks.RUNWAY.getDefaultState());
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------
	// Start- und Landebahn
	// ------------------------------------------------------------------

	private static void buildRunway(BaseConstruction.Plan plan, BlockPos origin, Direction facing,
			Direction side, int ground) {
		BlockState runway = ModBlocks.RUNWAY.getDefaultState();
		BlockState centerline = ModBlocks.RUNWAY_CENTERLINE.getDefaultState()
				.with(RunwayMarkingBlock.FACING, facing);
		BlockState threshold = ModBlocks.RUNWAY_THRESHOLD.getDefaultState()
				.with(RunwayMarkingBlock.FACING, facing);

		for (int forward = 0; forward < RUNWAY_LENGTH; forward++) {
			// Schwellenmarkierung an beiden Enden.
			boolean isThreshold = forward < 4 || forward >= RUNWAY_LENGTH - 4;

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
				plan.set(pos, state);
			}

			// Bahnbefeuerung alle 8 Bloecke an beiden Raendern.
			if (forward % 8 == 4) {
				for (int sign = -1; sign <= 1; sign += 2) {
					BlockPos light = origin.offset(facing, forward)
							.offset(side, sign * (RUNWAY_HALF_WIDTH + 1)).withY(ground);
					plan.set(light, ModBlocks.RUNWAY_LIGHT.getDefaultState());
				}
			}
		}
	}

	// ------------------------------------------------------------------
	// Hangars und Anlagen
	// ------------------------------------------------------------------

	private static void buildApron(BaseConstruction.Plan plan, BlockPos origin, Direction facing,
			Direction side, int ground, Team team, @Nullable PlayerEntity owner) {
		// --- Vorfeld: die ganze Flaeche zwischen Bahn und Gebaeuden ---
		// Reicht ueber die volle Bahnlaenge. Endete es frueher, liefen
		// startende Maschinen am Ende in unebenes Gelaende, blieben mit einer
		// Kollision haengen und verloren dabei drei Viertel ihrer Fahrt.
		// Reicht ueber beide Bahnenden hinaus. Wer beim Start zu lang wird,
		// rollt so auf befestigtem Grund aus statt in unebenes Gelaende - dort
		// zaehlt jede Bodenwelle als Bruchlandung.
		for (int forward = -4; forward <= RUNWAY_LENGTH + 4; forward++) {
			for (int across = RUNWAY_HALF_WIDTH + 1; across <= APRON_OFFSET + APRON_WIDTH - 2; across++) {
				plan.set(origin.offset(facing, forward).offset(side, across).withY(ground),
						ModBlocks.RUNWAY.getDefaultState());
			}
		}

		// --- Drei Hangars nebeneinander am Vorfeld ---
		for (int index = 0; index < 3; index++) {
			buildHangar(plan, origin, facing, side, ground, 14 + index * 36);
		}

		// --- Zwei Radarstationen an den Enden, fuer Rundumsicht ---
		BlockPos[] radars = {
				origin.offset(facing, 24).offset(side, APRON_OFFSET + APRON_WIDTH - 4).withY(ground + 1),
				origin.offset(facing, RUNWAY_LENGTH - 24).offset(side, -RUNWAY_HALF_WIDTH - 4).withY(ground + 1),
		};
		for (BlockPos radar : radars) {
			plan.set(radar.down(), ModBlocks.HANGAR_WALL.getDefaultState());
			plan.set(radar, ModBlocks.RADAR.getDefaultState());
			plan.assignTeam(radar, team);
		}

		// --- Vier Iron-Dome-Stellungen, ueber die Anlage verteilt ---
		BlockPos[] domes = {
				origin.offset(facing, 14).offset(side, -RUNWAY_HALF_WIDTH - 4).withY(ground + 1),
				origin.offset(facing, RUNWAY_LENGTH - 14).offset(side, -RUNWAY_HALF_WIDTH - 4).withY(ground + 1),
				origin.offset(facing, 52).offset(side, APRON_OFFSET + APRON_WIDTH - 4).withY(ground + 1),
				origin.offset(facing, RUNWAY_LENGTH - 52).offset(side, APRON_OFFSET + APRON_WIDTH - 4).withY(ground + 1),
		};
		for (BlockPos dome : domes) {
			// Einen Block hoch aufgestellt, damit die Bahn die Sicht nicht nimmt.
			plan.set(dome.down(), ModBlocks.HANGAR_WALL.getDefaultState());
			plan.set(dome, ModBlocks.IRON_DOME.getDefaultState());
			plan.assignTeam(dome, team);
			// Gleich einsatzbereit ausliefern - sonst steht die Abwehr leer da.
			plan.loadDome(dome);
		}

		// --- Zwei Kasernen, damit der Nachschub nicht abreisst ---
		BlockPos[] barracks = {
				origin.offset(facing, RUNWAY_LENGTH / 2 - 2).offset(side, APRON_OFFSET + APRON_WIDTH - 3).withY(ground + 1),
				origin.offset(facing, RUNWAY_LENGTH / 2 + 2).offset(side, APRON_OFFSET + APRON_WIDTH - 3).withY(ground + 1),
		};
		// Ersatzmaschinen sollen auf den Abstellstreifen, nicht neben die Kaserne.
		BlockPos apron = origin.offset(facing, 10).offset(side, PARKING_LANE).withY(ground);
		for (BlockPos pos : barracks) {
			plan.set(pos, ModBlocks.BARRACKS.getDefaultState());
			plan.assignTeam(pos, team);
			plan.stockBarracks(pos, 64, apron, facing.asRotation(), owner);
		}
	}

	/**
	 * Setzt einen Hangar ans Vorfeld: 11 Bloecke breit, 10 tief, mit Rolltor
	 * zur Bahn hin und einem Wartungsfeld darin, das abgestellte Maschinen
	 * betankt und bewaffnet.
	 *
	 * @param front Abstand vom Bahnanfang, an dem die Vorderwand steht
	 */
	private static void buildHangar(BaseConstruction.Plan plan, BlockPos origin, Direction facing,
			Direction side, int ground, int front) {
		BlockState wall = ModBlocks.HANGAR_WALL.getDefaultState();
		int back = front + 10;
		int left = APRON_OFFSET + 6;
		int right = left + 10;

		for (int forward = front; forward <= back; forward++) {
			for (int across = left; across <= right; across++) {
				boolean edge = forward == front || forward == back
						|| across == left || across == right;
				for (int y = 1; y <= HANGAR_HEIGHT; y++) {
					BlockPos pos = origin.offset(facing, forward).offset(side, across).withY(ground + y);
					if (y == HANGAR_HEIGHT) {
						plan.set(pos, wall);
					} else if (edge) {
						// Torlaibung in der Vorderwand offen lassen - 9 breit,
						// 5 hoch, damit die Spannweite durchpasst.
						boolean doorway = forward == front
								&& across > left && across < right;
						if (doorway) {
							plan.set(pos, ModBlocks.HANGAR_DOOR.getDefaultState()
									.with(HangarDoorBlock.OPEN, true));
						} else {
							plan.set(pos, wall);
						}
					}
				}
			}
		}

		// Wartungsfeld mittig im Hangar.
		for (int forward = front + 3; forward <= front + 7; forward++) {
			for (int across = left + 3; across <= right - 3; across++) {
				plan.set(origin.offset(facing, forward).offset(side, across).withY(ground),
						ModBlocks.REARM_PAD.getDefaultState());
			}
		}
	}

	// ------------------------------------------------------------------
	// Einheiten
	// ------------------------------------------------------------------

	private static void spawnUnits(ServerWorld world, BlockPos origin, Direction facing,
			Direction side, int ground, Team team, @Nullable PlayerEntity owner) {
		// Nase in Bahnrichtung: Die Maschinen sollen losrollen koennen, ohne
		// sich erst umdrehen zu muessen.
		float yaw = facing.asRotation();
		BlockPos home = origin.offset(facing, RUNWAY_LENGTH / 2).withY(ground);

		// Zwei bemannbare Maschinen, aufgereiht am Anfang der Bahn.
		for (int i = 0; i < 2; i++) {
			F47Entity jet = ModEntities.F47.create(world);
			if (jet == null) {
				continue;
			}
			BlockPos spot = origin.offset(facing, 5)
					.offset(side, i == 0 ? -3 : 3).withY(ground + 1);
			jet.refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, yaw, 0.0f);
			jet.setTeam(team);
			world.spawnEntity(jet);
		}

		// Sechs Drohnenjaeger in einer Reihe auf dem Abstellstreifen.
		// Sie stehen bewusst alle auf derselben Linie: Die Maschine ist vier
		// Bloecke breit, links liegt die Bahnbefeuerung, rechts beginnt bei 15
		// die Hangarwand - eine zweite Reihe weiter aussen wuerde in der Wand
		// stecken und bei jedem Tick eine Kollision ausloesen.
		for (int i = 0; i < 6; i++) {
			AutonomousF47Entity drone = ModEntities.AUTONOMOUS_F47.create(world);
			if (drone == null) {
				continue;
			}
			// Alle am Anfang der Bahn: Selbst die hinterste hat so noch ueber
			// sechzig Bloecke Rollstrecke, und gebraucht werden rund fuenfzig.
			BlockPos spot = origin.offset(facing, 2 + i * 6)
					.offset(side, PARKING_LANE).withY(ground + 1);
			drone.refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, yaw, 0.0f);
			drone.setTeam(team);
			drone.setHomeBase(home, yaw);
			drone.randomiseCallsign();
			drone.setOwner(owner);
			world.spawnEntity(drone);
		}

		// Ein Tarnkappenbomber und ein Transporter je Basis - der eine schlaegt
		// gegen Bodenziele zu, der andere betankt die Staffel in der Luft und
		// verlaengert damit ihre Reichweite.
		for (int i = 0; i < 2; i++) {
			AutonomousF47Entity heavy = i == 0
					? ModEntities.B21.create(world)
					: ModEntities.TRANSPORT.create(world);
			if (heavy == null) {
				continue;
			}
			BlockPos spot = origin.offset(facing, 46 + i * 12)
					.offset(side, PARKING_LANE).withY(ground + 1);
			heavy.refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, yaw, 0.0f);
			heavy.setTeam(team);
			heavy.setHomeBase(home, yaw);
			heavy.randomiseCallsign();
			heavy.setOwner(owner);
			world.spawnEntity(heavy);
		}

		// Zwei Schuetzenpanzer - sie fahren die Infanterie zum Gegner, statt
		// sie hunderte Bloecke zu Fuss laufen zu lassen.
		for (int i = 0; i < 2; i++) {
			ArmoredVehicleEntity carrier = ModEntities.ARMORED_VEHICLE.create(world);
			if (carrier == null) {
				continue;
			}
			BlockPos spot = origin.offset(facing, 70 + i * 6)
					.offset(side, PARKING_LANE + 6).withY(ground + 1);
			carrier.refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, yaw, 0.0f);
			carrier.setTeam(team);
			world.spawnEntity(carrier);
		}

		// Bodenpersonal. Sechs Piloten, damit auch die Reservemaschinen
		// besetzt werden koennen - ein Pilot wird beim Einsteigen verbraucht.
		BlockPos quarters = origin.offset(facing, RUNWAY_LENGTH / 2 - 6)
				.offset(side, APRON_OFFSET + APRON_WIDTH - 8).withY(ground);
		SoldierRole[] roster = {
				SoldierRole.PILOT, SoldierRole.PILOT, SoldierRole.PILOT,
				SoldierRole.PILOT, SoldierRole.PILOT, SoldierRole.PILOT,
				SoldierRole.ENGINEER, SoldierRole.ENGINEER, SoldierRole.ENGINEER,
				SoldierRole.MEDIC, SoldierRole.MEDIC,
				SoldierRole.HEAVY, SoldierRole.HEAVY, SoldierRole.HEAVY,
				SoldierRole.RIFLEMAN, SoldierRole.RIFLEMAN, SoldierRole.RIFLEMAN,
				SoldierRole.RIFLEMAN, SoldierRole.RIFLEMAN, SoldierRole.RIFLEMAN,
		};
		for (int i = 0; i < roster.length; i++) {
			BlockPos spot = quarters.offset(facing, (i % 5) * 2).offset(side, (i / 5) * 2);
			spawnSoldier(world, spot, yaw, roster[i], team, owner);
		}

		// Wachposten rund um die Anlage, damit die Basis nicht offen liegt.
		int[][] posts = {
				{8, -RUNWAY_HALF_WIDTH - 3}, {RUNWAY_LENGTH / 3, -RUNWAY_HALF_WIDTH - 3},
				{2 * RUNWAY_LENGTH / 3, -RUNWAY_HALF_WIDTH - 3}, {RUNWAY_LENGTH - 8, -RUNWAY_HALF_WIDTH - 3},
				{8, APRON_OFFSET + APRON_WIDTH - 5}, {RUNWAY_LENGTH - 8, APRON_OFFSET + APRON_WIDTH - 5},
		};
		for (int[] post : posts) {
			spawnSoldier(world, origin.offset(facing, post[0]).offset(side, post[1]).withY(ground),
					yaw, SoldierRole.RIFLEMAN, team, owner);
		}
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
}
