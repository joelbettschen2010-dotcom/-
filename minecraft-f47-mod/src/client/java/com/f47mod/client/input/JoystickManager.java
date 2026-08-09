package com.f47mod.client.input;

import com.f47mod.F47Mod;
import org.lwjgl.glfw.GLFW;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Liest Joysticks, Schubhebel und Pedale ueber GLFW aus.
 *
 * <p>Minecraft selbst kennt nur Maus und Tastatur. GLFW - die Bibliothek, auf
 * der das Spiel aufsetzt - kann aber jedes angeschlossene Eingabegeraet
 * abfragen. Genau das passiert hier einmal pro Tick.
 *
 * <p>Achsen- und Knopfnummern sind bei jedem Hersteller anders. Deshalb wird
 * hier nichts fest verdrahtet: Der Zuordnungs-Bildschirm im Spiel liest die
 * Nummern von deinem Geraet ab, waehrend du den Stick bewegst.
 */
public final class JoystickManager {
	/** Hutschalter werden als zusaetzliche Knoepfe ab dieser Nummer gefuehrt. */
	public static final int HAT_BUTTON_BASE = 1000;

	private static final int MAX_DEVICES = GLFW.GLFW_JOYSTICK_LAST + 1;

	/** Ein angeschlossenes Geraet mit seinem aktuellen Zustand. */
	public static final class Device {
		public final int id;
		public final String name;
		public float[] axes = new float[0];
		public boolean[] buttons = new boolean[0];
		public byte[] hats = new byte[0];

		Device(int id, String name) {
			this.id = id;
			this.name = name;
		}

		public int axisCount() {
			return axes.length;
		}

		public int buttonCount() {
			return buttons.length;
		}

		@Override
		public String toString() {
			return "#" + id + " " + name;
		}
	}

	private static final Device[] DEVICES = new Device[MAX_DEVICES];
	private static int rescanTimer;
	private static boolean loggedOnce;

	private JoystickManager() {
	}

	/** Einmal pro Client-Tick aufrufen. Muss im Hauptthread laufen. */
	public static void poll() {
		// Neue Geraete nur gelegentlich suchen - das Suchen selbst kostet Zeit.
		if (--rescanTimer <= 0) {
			rescanTimer = 40;
			rescan();
		}
		for (Device device : DEVICES) {
			if (device != null) {
				read(device);
			}
		}
		if (!loggedOnce) {
			loggedOnce = true;
			logDevices();
		}
	}

	private static void rescan() {
		for (int id = 0; id < MAX_DEVICES; id++) {
			boolean present = GLFW.glfwJoystickPresent(id);
			if (present && DEVICES[id] == null) {
				String name = GLFW.glfwGetJoystickName(id);
				DEVICES[id] = new Device(id, name == null ? "Unbekannt" : name);
				F47Mod.LOGGER.info("[F-47] Eingabegeraet erkannt: #{} {}", id, name);
			} else if (!present && DEVICES[id] != null) {
				F47Mod.LOGGER.info("[F-47] Eingabegeraet abgezogen: #{} {}", id, DEVICES[id].name);
				DEVICES[id] = null;
			}
		}
	}

	private static void read(Device device) {
		FloatBuffer axes = GLFW.glfwGetJoystickAxes(device.id);
		if (axes != null) {
			if (device.axes.length != axes.remaining()) {
				device.axes = new float[axes.remaining()];
			}
			for (int i = 0; i < device.axes.length; i++) {
				device.axes[i] = axes.get(i);
			}
		}

		ByteBuffer buttons = GLFW.glfwGetJoystickButtons(device.id);
		if (buttons != null) {
			if (device.buttons.length != buttons.remaining()) {
				device.buttons = new boolean[buttons.remaining()];
			}
			for (int i = 0; i < device.buttons.length; i++) {
				device.buttons[i] = buttons.get(i) == GLFW.GLFW_PRESS;
			}
		}

		ByteBuffer hats = GLFW.glfwGetJoystickHats(device.id);
		if (hats != null) {
			if (device.hats.length != hats.remaining()) {
				device.hats = new byte[hats.remaining()];
			}
			for (int i = 0; i < device.hats.length; i++) {
				device.hats[i] = hats.get(i);
			}
		}
	}

	private static void logDevices() {
		List<Device> found = devices();
		if (found.isEmpty()) {
			F47Mod.LOGGER.info("[F-47] Kein Joystick gefunden - es bleibt bei Maus und Tastatur.");
			return;
		}
		for (Device device : found) {
			F47Mod.LOGGER.info("[F-47] {} - {} Achsen, {} Knoepfe, {} Hutschalter",
					device, device.axisCount(), device.buttonCount(), device.hats.length);
		}
	}

	/** Alle derzeit angeschlossenen Geraete. */
	public static List<Device> devices() {
		List<Device> list = new ArrayList<>();
		for (Device device : DEVICES) {
			if (device != null) {
				list.add(device);
			}
		}
		return list;
	}

	public static boolean anyPresent() {
		for (Device device : DEVICES) {
			if (device != null) {
				return true;
			}
		}
		return false;
	}

	public static Device device(int id) {
		return id >= 0 && id < MAX_DEVICES ? DEVICES[id] : null;
	}

	/** Rohwert einer Achse (-1..1), oder 0 wenn es sie nicht gibt. */
	public static float rawAxis(int deviceId, int axis) {
		Device device = device(deviceId);
		if (device == null || axis < 0 || axis >= device.axes.length) {
			return 0.0f;
		}
		return device.axes[axis];
	}

	/** Knopfzustand. Nummern ab {@link #HAT_BUTTON_BASE} sind Hutschalter. */
	public static boolean button(int deviceId, int button) {
		Device device = device(deviceId);
		if (device == null || button < 0) {
			return false;
		}
		if (button >= HAT_BUTTON_BASE) {
			int index = button - HAT_BUTTON_BASE;
			int hat = index / 4;
			int direction = index % 4;
			if (hat >= device.hats.length) {
				return false;
			}
			int mask = switch (direction) {
				case 0 -> GLFW.GLFW_HAT_UP;
				case 1 -> GLFW.GLFW_HAT_RIGHT;
				case 2 -> GLFW.GLFW_HAT_DOWN;
				default -> GLFW.GLFW_HAT_LEFT;
			};
			return (device.hats[hat] & mask) != 0;
		}
		return button < device.buttons.length && device.buttons[button];
	}

	/** Lesbarer Name einer Knopfnummer fuer die Anzeige. */
	public static String buttonName(int button) {
		if (button >= HAT_BUTTON_BASE) {
			int index = button - HAT_BUTTON_BASE;
			String direction = switch (index % 4) {
				case 0 -> "hoch";
				case 1 -> "rechts";
				case 2 -> "runter";
				default -> "links";
			};
			return "Hut " + (index / 4 + 1) + " " + direction;
		}
		return "Knopf " + (button + 1);
	}
}
