package com.f47mod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Einstellungen des Mods. Wird beim Start aus config/f47.json geladen und dort
 * angelegt, falls die Datei noch nicht existiert. Alle Werte lassen sich im
 * Spiel-Ordner anpassen, ohne den Mod neu zu bauen.
 */
public class F47Config {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static F47Config instance;

	// --- Flugmodell -------------------------------------------------------
	// Echte Kennwerte in SI-Einheiten. Der Auftrieb wird daraus gerechnet,
	// nicht vorgegeben - siehe com.f47mod.entity.vehicle.FlightModel.
	/** Startmasse in Kilogramm. Leichter heisst wendiger und kuerzerer Start. */
	public float massKg = 6000.0f;
	/**
	 * Tragflaeche in Quadratmetern. Groesser heisst mehr Auftrieb, kuerzerer
	 * Start und niedrigere Abrissgeschwindigkeit - dafuer etwas weniger
	 * Spitze. 110 ist der Wert, bei dem die Maschine sicher auf die
	 * sechsundneunzig Bloecke lange Bahn des Bausatzes passt.
	 */
	public float wingAreaM2 = 110.0f;
	/** Schub bei Vollgas ohne Nachbrenner, in Newton. */
	public float thrustNewtons = 60000.0f;
	/** Schub mit Nachbrenner, in Newton. */
	public float afterburnerNewtons = 110000.0f;
	/**
	 * Hoechste Querbeschleunigung in g. Darueber wuerde die Zelle brechen -
	 * begrenzt zugleich, wie eng die Maschine kurven kann.
	 */
	public float maxLoadFactor = 9.0f;
	/**
	 * Harte Obergrenze in Bloecken pro Tick.
	 *
	 * <p>Nicht aerodynamisch, sondern eine Notbremse: Jenseits davon kommt
	 * Minecrafts Kollisionspruefung nicht mehr mit und das Nachladen der
	 * Landschaft haengt hinterher. 12 entspricht rund 860 km/h.
	 */
	public float speedLimitBlocksPerTick = 16.0f;
	/** Wie schnell die Maschine der Blickrichtung folgt (Grad pro Tick). */
	public float pitchRateDegrees = 3.2f;
	/** Rollrate bei vollem Querruderausschlag (Grad pro Tick). */
	public float rollRateDegrees = 6.5f;
	/**
	 * Flugregelung fuer die Bot-Piloten.
	 *
	 * <p>Die volle Aerodynamik ist fuer einen Menschen im Cockpit genau
	 * richtig - er sieht, wohin er fliegt, und Minecraft laedt die Landschaft
	 * um ihn herum nach. Ein Bot hat beides nicht: Er kennt nur seinen
	 * Zielpunkt, fliegt in engem, huegeligem Gelaende und muss innerhalb der
	 * dauerhaft geladenen Chunks bleiben. Mit echter Traegheit verpasst er
	 * Kurven, ueberzieht beim Ziehen und zerschellt an Haengen.
	 *
	 * <p>Ist das hier an, bekommen <em>nur</em> die autonomen Maschinen eine
	 * gutmuetige Regelung: Der Geschwindigkeitsvektor folgt der Nase, und der
	 * Auftrieb traegt zuverlaessig. Der Spieler fliegt unveraendert nach
	 * echter Aerodynamik. Auf false stellen, wenn die Bots auch stuerzen
	 * duerfen sollen.
	 */
	public boolean botFlightAssist = true;

	// --- Kampfwerte -------------------------------------------------------
	public float missileDamage = 22.0f;
	public float missileBlastPower = 3.2f;
	public float cannonDamage = 6.0f;
	public float bombBlastPower = 5.0f;
	public float laserDamage = 9.0f;
	public float railgunDamage = 26.0f;
	public float plasmaBlastPower = 2.6f;
	public float soldierRifleDamage = 4.5f;
	/** Maximale Panzerung/Struktur der F-47. */
	public float jetMaxHealth = 60.0f;

	// --- Reichweiten ------------------------------------------------------
	public int jetRadarRange = 140;
	public int radarBlockRange = 220;
	public int ironDomeRange = 90;
	public int missileLockRange = 130;
	public int missileLockConeDegrees = 32;

	// --- Treibstoff -------------------------------------------------------
	public float maxFuel = 3600.0f;
	public float fuelBurnPerTick = 0.28f;
	public float afterburnerFuelFactor = 3.0f;
	public float stealthFuelFactor = 1.6f;

	// --- Stealth ----------------------------------------------------------
	/** Faktor, mit dem die gegnerische Ortungsreichweite bei Stealth multipliziert wird. */
	public float stealthDetectionFactor = 0.22f;
	/** Ticks, in denen Stealth nach einem Waffenstart wirkungslos ist (offene Waffenschaechte). */
	public int stealthBreakTicks = 60;

	// --- Gegner -----------------------------------------------------------
	/** Zufaellige Drohnenangriffe in der Nacht in der Naehe von Radarstationen. */
	public boolean enableRandomRaids = true;
	/** Durchschnittliche Anzahl Ticks zwischen zwei Angriffswellen. */
	public int raidIntervalTicks = 24000;
	public int raidDroneCount = 4;

	// --- Darstellung ------------------------------------------------------
	/**
	 * Groesse, in der die F-47 gezeichnet wird. 1.0 entspricht rund 3 Bloecken
	 * Laenge, der Standard 1.8 also gut 5,5 Bloecken - etwa so gross wie ein
	 * echtes Kampfflugzeug neben einem Spieler wirkt. Wer die Maschine lieber
	 * kleiner oder groesser haette, dreht hier, ohne den Mod neu zu bauen.
	 */
	public float jetModelScale = 1.8f;

	// --- Stuetzpunkt ------------------------------------------------------
	/**
	 * Wie viele Chunks rund um einen neu gebauten Stuetzpunkt dauerhaft geladen
	 * bleiben. Ohne das haelt Minecraft nur die Umgebung des Spielers am Laufen,
	 * und die Basis steht still, sobald man wegfliegt - Jets starten nicht,
	 * Soldaten kaempfen nicht.
	 *
	 * <p>6 deckt einen Kreis von gut hundert Bloecken ab und damit die
	 * Warteschleife der Staffel. Kleiner heisst: Die Maschinen fliegen hinaus
	 * und bleiben dort stehen, weil ausserhalb nicht mehr gerechnet wird. Auf
	 * 0 stellen, wenn der Rechner das nicht mitmacht - dann laeuft die Basis
	 * nur in Spielernaehe.
	 */
	public int baseForceLoadRadiusChunks = 6;
	/**
	 * Abstand zwischen den beiden Basen, die {@code /f47 war} aufstellt.
	 *
	 * <p>So gewaehlt, dass sich die geladenen Bereiche beider Seiten
	 * ueberlappen - der Luftraum dazwischen muss mitrechnen, sonst frieren
	 * angreifende Maschinen auf halbem Weg ein.
	 */
	public int warBaseSeparation = 150;
	/**
	 * Chunks, die eine unterwegs befindliche Einheit um sich herum gerechnet
	 * haelt. Ohne das steht sie ausserhalb des Stuetzpunkts still - genau
	 * daran sind Angriffe ueber groessere Entfernungen gescheitert. 2 deckt
	 * gut achtzig Bloecke ab und reicht bei jeder fliegbaren Geschwindigkeit.
	 */
	public int missionChunkRadius = 2;
	/**
	 * Obergrenze fuer die mitgefuehrten Chunks insgesamt. Verhindert, dass ein
	 * grosser Krieg mit vielen gleichzeitigen Einsaetzen den Rechner erdrueckt.
	 */
	public int missionChunkBudget = 400;
	/**
	 * Wie weit Einheiten nach Gegnern suchen, in Bloecken. Grosszuegig - der
	 * Wunsch war ausdruecklich, dass sie angreifen, egal wie weit weg der
	 * Gegner steht.
	 */
	public int strikeRange = 2000;

	// --- Sonstiges --------------------------------------------------------
	/** Explosionen beschaedigen Bloecke. Auf false stellen, um die Basis zu schonen. */
	public boolean explosionsBreakBlocks = true;
	/** Bodentruppen greifen feindliche Mobs selbstaendig an. */
	public boolean soldiersEngageAutomatically = true;

	public static F47Config get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	private static F47Config load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("f47.json");
		F47Config config = new F47Config();
		try {
			if (Files.exists(path)) {
				String json = Files.readString(path);
				F47Config parsed = GSON.fromJson(json, F47Config.class);
				if (parsed != null) {
					config = parsed;
				}
			}
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(config));
		} catch (IOException | RuntimeException e) {
			F47Mod.LOGGER.warn("[F-47] Konfiguration konnte nicht gelesen werden, benutze Standardwerte", e);
		}
		return config;
	}

	/** Explosionsart passend zur Konfiguration. */
	public net.minecraft.world.World.ExplosionSourceType explosionType() {
		return explosionsBreakBlocks
				? net.minecraft.world.World.ExplosionSourceType.TNT
				: net.minecraft.world.World.ExplosionSourceType.NONE;
	}
}
