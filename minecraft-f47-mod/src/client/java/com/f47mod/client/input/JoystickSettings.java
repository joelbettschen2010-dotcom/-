package com.f47mod.client.input;

import com.f47mod.F47Mod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.MathHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Belegung von Joystick, Schubhebel und Pedalen. Wird in
 * config/f47-joystick.json gespeichert und im Spiel ueber den
 * Zuordnungs-Bildschirm gesetzt - Handarbeit an der Datei ist nicht noetig.
 */
public class JoystickSettings {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static JoystickSettings instance;

	/** Steuerbare Achsen. */
	public enum Axis {
		/** Ziehen und Druecken - Nase hoch und runter. */
		PITCH("pitch"),
		/** Stick nach links und rechts - Querlage. */
		ROLL("roll"),
		/** Seitenruder, meist Pedale oder ein Drehgriff. */
		YAW("yaw"),
		/** Schubhebel - absolute Stellung, kein Tastendruck. */
		THROTTLE("throttle");

		public final String key;

		Axis(String key) {
			this.key = key;
		}

		public String translationKey() {
			return "joystick.f47.axis." + key;
		}
	}

	/** Belegbare Knoepfe. */
	public enum Button {
		FIRE("fire"),
		MISSILE("missile"),
		CYCLE_WEAPON("cycle_weapon"),
		STEALTH("stealth"),
		AFTERBURNER("afterburner"),
		BRAKE("brake");

		public final String key;

		Button(String key) {
			this.key = key;
		}

		public String translationKey() {
			return "joystick.f47.button." + key;
		}
	}

	/**
	 * Eine Achsenbelegung.
	 *
	 * @param device   Geraetenummer (Stick und Schubhebel duerfen verschiedene sein)
	 * @param axis     Achsennummer auf diesem Geraet
	 * @param invert   Richtung umkehren
	 * @param deadzone Totbereich um die Mitte - gegen Zittern in Ruhelage
	 * @param min      kleinster gemessener Rohwert (aus der Kalibrierung)
	 * @param max      groesster gemessener Rohwert
	 */
	public static class AxisBinding {
		public int device = -1;
		public int axis = -1;
		public boolean invert;
		public float deadzone = 0.08f;
		public float min = -1.0f;
		public float max = 1.0f;

		public boolean isBound() {
			return device >= 0 && axis >= 0;
		}

		public void clear() {
			device = -1;
			axis = -1;
		}

		/** Auf -1..1 normiert, mit Totbereich und Kalibrierung. */
		public float value() {
			if (!isBound()) {
				return 0.0f;
			}
			float raw = JoystickManager.rawAxis(device, axis);
			float span = Math.max(0.05f, max - min);
			// Erst auf 0..1 der gemessenen Spanne, dann auf -1..1.
			float normalised = MathHelper.clamp((raw - min) / span, 0.0f, 1.0f) * 2.0f - 1.0f;
			if (invert) {
				normalised = -normalised;
			}
			if (Math.abs(normalised) < deadzone) {
				return 0.0f;
			}
			// Ausserhalb des Totbereichs sauber wieder auf volle Spanne ziehen,
			// damit kein Sprung entsteht.
			float sign = Math.signum(normalised);
			return sign * (Math.abs(normalised) - deadzone) / (1.0f - deadzone);
		}

		/** Auf 0..1 normiert - fuer den Schubhebel. */
		public float unipolar() {
			if (!isBound()) {
				return 0.0f;
			}
			float raw = JoystickManager.rawAxis(device, axis);
			float span = Math.max(0.05f, max - min);
			float normalised = MathHelper.clamp((raw - min) / span, 0.0f, 1.0f);
			return invert ? 1.0f - normalised : normalised;
		}

		/** Rohwert - nur fuer die Anzeige im Zuordnungs-Bildschirm. */
		public float raw() {
			return isBound() ? JoystickManager.rawAxis(device, axis) : 0.0f;
		}
	}

	/** Eine Knopfbelegung. */
	public static class ButtonBinding {
		public int device = -1;
		public int button = -1;

		public boolean isBound() {
			return device >= 0 && button >= 0;
		}

		public void clear() {
			device = -1;
			button = -1;
		}

		public boolean pressed() {
			return isBound() && JoystickManager.button(device, button);
		}
	}

	// --- gespeicherte Werte -------------------------------------------
	/** Joysticksteuerung ueberhaupt benutzen. */
	public boolean enabled = true;
	/** Empfindlichkeit der Steuerflaechen (1.0 = normal). */
	public float sensitivity = 1.0f;
	/**
	 * Kurve der Achsen: 1.0 linear, groesser macht die Mitte feinfuehliger.
	 * Fuer praezises Zielen sind 1.5 bis 2.0 angenehm.
	 */
	public float curve = 1.6f;
	/**
	 * Wie stark die Sicht mit der Maschine mitrollt (0 = gar nicht, 1 = voll).
	 * Wem beim Kurvenflug schwindelig wird, stellt das einfach auf 0.
	 */
	public float cameraRoll = 1.0f;

	public Map<String, AxisBinding> axes = new LinkedHashMap<>();
	public Map<String, ButtonBinding> buttons = new LinkedHashMap<>();

	public static JoystickSettings get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	public AxisBinding axis(Axis which) {
		return axes.computeIfAbsent(which.key, key -> new AxisBinding());
	}

	public ButtonBinding button(Button which) {
		return buttons.computeIfAbsent(which.key, key -> new ButtonBinding());
	}

	/** Ist genug belegt, damit sich damit fliegen laesst? */
	public boolean isUsable() {
		return enabled && axis(Axis.PITCH).isBound() && axis(Axis.ROLL).isBound()
				&& JoystickManager.anyPresent();
	}

	/**
	 * Achsenwert mit Kurve - kleine Ausschlaege wirken feiner, volle
	 * Ausschlaege bleiben voll.
	 */
	public float shaped(Axis which) {
		float value = axis(which).value();
		if (value == 0.0f) {
			return 0.0f;
		}
		float shapedValue = (float) Math.pow(Math.abs(value), Math.max(1.0f, curve));
		return Math.signum(value) * shapedValue * sensitivity;
	}

	// --- Laden und Speichern ------------------------------------------

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("f47-joystick.json");
	}

	private static JoystickSettings load() {
		JoystickSettings settings = new JoystickSettings();
		try {
			Path file = path();
			if (Files.exists(file)) {
				JoystickSettings parsed = GSON.fromJson(Files.readString(file), JoystickSettings.class);
				if (parsed != null) {
					settings = parsed;
					if (settings.axes == null) {
						settings.axes = new LinkedHashMap<>();
					}
					if (settings.buttons == null) {
						settings.buttons = new LinkedHashMap<>();
					}
				}
			}
		} catch (IOException | RuntimeException e) {
			F47Mod.LOGGER.warn("[F-47] Joystick-Belegung nicht lesbar, benutze Standardwerte", e);
		}
		// Alle Eintraege anlegen, damit die Datei vollstaendig aussieht.
		for (Axis which : Axis.values()) {
			settings.axis(which);
		}
		for (Button which : Button.values()) {
			settings.button(which);
		}
		settings.save();
		return settings;
	}

	public void save() {
		try {
			Path file = path();
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(this));
		} catch (IOException e) {
			F47Mod.LOGGER.warn("[F-47] Joystick-Belegung konnte nicht gespeichert werden", e);
		}
	}

	/** Alle Belegungen loeschen. */
	public void reset() {
		axes.values().forEach(AxisBinding::clear);
		buttons.values().forEach(ButtonBinding::clear);
		save();
	}
}
