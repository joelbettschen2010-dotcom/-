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
	/** Schub pro Tick bei Vollgas (Bloecke/Tick^2). */
	public float thrust = 0.055f;
	/** Zusaetzlicher Schubfaktor im Nachbrenner. */
	public float afterburnerFactor = 1.85f;
	/** Hoechstgeschwindigkeit in Bloecken pro Tick (20 Ticks = 1 Sekunde). */
	public float maxSpeed = 4.6f;
	/** Geschwindigkeit, ab der die Tragflaechen genug Auftrieb erzeugen. */
	public float stallSpeed = 0.85f;
	/** Mindestgeschwindigkeit zum Abheben von der Bahn. */
	public float takeoffSpeed = 1.15f;
	/** Wie schnell der Jet der Blickrichtung folgt (0..1 pro Tick). */
	public float turnRate = 0.12f;
	/** Luftwiderstand pro Tick. */
	public float drag = 0.988f;

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
	 * Soldaten kaempfen nicht. 4 deckt den Patrouillenkreis ab. Auf 0 stellen,
	 * wenn der Rechner das nicht mitmacht.
	 */
	public int baseForceLoadRadiusChunks = 4;
	/** Abstand zwischen den beiden Basen, die {@code /f47 war} aufstellt. */
	public int warBaseSeparation = 180;

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
