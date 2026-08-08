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
	/**
	 * Wie weit das Vorfeld seitlich reicht.
	 *
	 * <p>Breit genug fuer eigene Spuren je Muster: Jaeger bei 10, Bomber bei
	 * 30, Transporter bei 42, Panzer aussen. Enger ging es nicht - ein
	 * Transporter ist neun Bloecke breit, und wer beim Aufstellen in einer
	 * Hangarwand landet, entsteht gar nicht erst.
	 */
	private static final int APRON_WIDTH = 44;
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
	/**
	 * Seitlicher Abstand zweier Bahnen samt ihrem Vorfeld.
	 *
	 * <p>Eine Bahn belegt von -12 (Rand) bis +35 (Vorfeldkante) - vierzig
	 * Bloecke Versatz lassen dazwischen noch Platz zum Rollen.
	 */
	private static final int BAY_WIDTH = 62;

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

		int bays = Math.max(1, F47Config.get().runwayCount);
		clearArea(plan, origin, facing, side, ground, bays);
		for (int bay = 0; bay < bays; bay++) {
			BlockPos start = origin.offset(side, bay * BAY_WIDTH);
			buildRunway(plan, start, facing, side, ground);
			buildApron(plan, start, facing, side, ground, team, owner, bay == 0);
		}

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
			Direction side, int ground, int bays) {
		ServerWorld world = plan.world();
		boolean level = F47Config.get().levelTerrain;
		int outer = (bays - 1) * BAY_WIDTH + APRON_OFFSET + APRON_WIDTH;
		for (int forward = -6; forward <= RUNWAY_LENGTH + 6; forward++) {
			for (int across = -RUNWAY_HALF_WIDTH - 6; across <= outer; across++) {
				BlockPos column = origin.offset(facing, forward).offset(side, across);

				// Ueber dem Boden freiraeumen. Wie weit, verraet die Hoehenkarte:
				// Ueber dem hoechsten Block ist ohnehin nur Luft, dort muss gar
				// nicht erst gesucht werden.
				// Bei eingeschalteter Einebnung faellt alles weg, was hoeher
				// steht - auch ein ganzer Berg. Sonst nur bis zur lichten Hoehe.
				int surface = world.getTopY(Heightmap.Type.WORLD_SURFACE,
						column.getX(), column.getZ());
				int top = level ? surface : Math.min(surface, ground + CLEAR_HEIGHT);
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
			Direction side, int ground, Team team, @Nullable PlayerEntity owner, boolean mainBay) {
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

		if (!mainBay) {
			// Nebenbahnen bekommen nur Bahn, Vorfeld und Hangars. Radar,
			// Flugabwehr und Kasernen stehen einmal fuer die ganze Anlage.
			return;
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

	/**
	 * Stellt den ganzen Verband auf.
	 *
	 * <p>Die Staerke steht in der Konfiguration; ab Werk sind es vierzig
	 * Jaeger, zwoelf Bomber, zwoelf Transporter, dreissig Panzer und
	 * dreihundertfuenfzig Mann je Stuetzpunkt. Verteilt wird ueber alle
	 * Bahnen - auf einem einzigen Abstellstreifen stuende das nie.
	 */
	private static void spawnUnits(ServerWorld world, BlockPos origin, Direction facing,
			Direction side, int ground, Team team, @Nullable PlayerEntity owner) {
		// Nase in Bahnrichtung: Die Maschinen sollen losrollen koennen, ohne
		// sich erst umdrehen zu muessen.
		float yaw = facing.asRotation();
		BlockPos home = origin.offset(facing, RUNWAY_LENGTH / 2).withY(ground);
		F47Config config = F47Config.get();
		int bays = Math.max(1, config.runwayCount);

		// Zwei bemannbare Maschinen fuer den Spieler, ganz vorne.
		for (int i = 0; i < 2; i++) {
			F47Entity jet = ModEntities.F47.create(world);
			if (jet == null) {
				continue;
			}
			BlockPos spot = origin.offset(facing, 5).offset(side, i == 0 ? -3 : 3).withY(ground + 1);
			jet.refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, yaw, 0.0f);
			jet.setTeam(team);
			world.spawnEntity(jet);
		}

		// Flugzeuge auf die Abstellstreifen aller Bahnen verteilen.
		// Abstaende nach Spannweite: Ein Transporter ist neun Bloecke breit,
		// zwei davon neun Bloecke auseinander stehen ineinander - und was beim
		// Aufstellen kollidiert, entsteht gar nicht erst.
		deployAircraft(world, origin, facing, side, ground, team, owner, home, yaw, bays,
				ModEntities.AUTONOMOUS_F47, config.squadronFighters, 0, 7);
		deployAircraft(world, origin, facing, side, ground, team, owner, home, yaw, bays,
				ModEntities.B21, config.squadronBombers, 20, 12);
		deployAircraft(world, origin, facing, side, ground, team, owner, home, yaw, bays,
				ModEntities.TRANSPORT, config.squadronTransports, 32, 14);

		// Panzer in Reihen auf dem Vorfeld.
		for (int i = 0; i < config.squadronVehicles; i++) {
			ArmoredVehicleEntity carrier = ModEntities.ARMORED_VEHICLE.create(world);
			if (carrier == null) {
				continue;
			}
			int bay = i % bays;
			int index = i / bays;
			BlockPos spot = freeSpot(world, ModEntities.ARMORED_VEHICLE,
					origin.offset(facing, 20 + index * 9)
							.offset(side, bay * BAY_WIDTH + APRON_OFFSET + APRON_WIDTH - 6).withY(ground + 1),
					facing, 6);
			carrier.refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, yaw, 0.0f);
			carrier.setTeam(team);
			world.spawnEntity(carrier);
		}

		// Bodenpersonal. Die Mischung entspricht dem, was die Basis braucht:
		// genug Piloten fuer alle Maschinen, Technik und Sanitaet, der Rest
		// Infanterie.
		int troops = Math.max(0, config.garrisonTroops);
		int pilots = Math.min(troops, config.squadronFighters + config.squadronBombers
				+ config.squadronTransports);
		int engineers = troops / 12;
		int medics = troops / 20;
		int heavy = troops / 8;

		for (int i = 0; i < troops; i++) {
			SoldierRole role;
			if (i < pilots) {
				role = SoldierRole.PILOT;
			} else if (i < pilots + engineers) {
				role = SoldierRole.ENGINEER;
			} else if (i < pilots + engineers + medics) {
				role = SoldierRole.MEDIC;
			} else if (i < pilots + engineers + medics + heavy) {
				role = SoldierRole.HEAVY;
			} else {
				role = SoldierRole.RIFLEMAN;
			}
			// In Reihen ueber die ganze Anlage, damit sie nicht uebereinander
			// stehen und sich gegenseitig wegschieben.
			int bay = i % bays;
			int index = i / bays;
			BlockPos spot = origin.offset(facing, 12 + (index % 48) * 3)
					.offset(side, bay * BAY_WIDTH + APRON_OFFSET + APRON_WIDTH - 14 - (index / 48) * 3)
					.withY(ground);
			spawnSoldier(world, spot, yaw, role, team, owner);
		}
	}

	/**
	 * Sucht ab dem Wunschplatz den naechsten, an dem die Maschine wirklich
	 * frei steht.
	 *
	 * <p>Noetig, weil die Muster inzwischen bis zu neun Bloecke breit sind:
	 * Ein von Hand ausgerechnetes Raster trifft frueher oder spaeter eine
	 * Hangarwand oder eine schon abgestellte Maschine - und was beim
	 * Aufstellen kollidiert, entsteht gar nicht erst. Im Versuch fehlten so
	 * von vierzig Jaegern vierzehn und von zwoelf Transportern neun.
	 */
	private static BlockPos freeSpot(ServerWorld world,
			net.minecraft.entity.EntityType<?> type, BlockPos wanted, Direction facing, int step) {
		for (int attempt = 0; attempt < 24; attempt++) {
			BlockPos candidate = wanted.offset(facing, attempt * step);
			if (world.isSpaceEmpty(type.getSpawnBox(
					candidate.getX() + 0.5, candidate.getY(), candidate.getZ() + 0.5))) {
				return candidate;
			}
		}
		return wanted;
	}

	/**
	 * Reiht ein Muster auf den Abstellstreifen auf.
	 *
	 * @param lane Versatz quer zur Bahn, damit sich die Muster nicht
	 *             gegenseitig auf denselben Platz stellen
	 */
	private static void deployAircraft(ServerWorld world, BlockPos origin, Direction facing,
			Direction side, int ground, Team team, @Nullable PlayerEntity owner, BlockPos home,
			float yaw, int bays, net.minecraft.entity.EntityType<? extends AutonomousF47Entity> type,
			int count, int lane, int spacing) {
		for (int i = 0; i < count; i++) {
			AutonomousF47Entity aircraft = type.create(world);
			if (aircraft == null) {
				continue;
			}
			int bay = i % bays;
			int index = i / bays;
			BlockPos spot = freeSpot(world, type,
					origin.offset(facing, 4 + index * spacing)
							.offset(side, bay * BAY_WIDTH + PARKING_LANE + lane).withY(ground + 1),
					facing, spacing);
			aircraft.refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, yaw, 0.0f);
			aircraft.setTeam(team);
			aircraft.setHomeBase(home, yaw);
			aircraft.randomiseCallsign();
			aircraft.setOwner(owner);
			world.spawnEntity(aircraft);
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
