package com.f47mod.client;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Cockpit-Belegung.
 *
 * <p><b>Wichtig:</b> Minecraft haelt je Taste nur <em>eine</em> Belegung
 * ({@code KeyBinding.KEY_TO_BINDINGS} ist eine Map von Taste auf Belegung).
 * Wer hier eine Taste doppelt belegt, macht die Vanilla-Funktion damit kaputt -
 * ein Mod auf W wuerde also das Laufen abschalten.
 *
 * <p>Deshalb sind hier nur Tasten belegt, die Minecraft selbst frei laesst.
 * Schub und Feuer benutzen bewusst <em>keine</em> eigene Belegung, sondern
 * lesen im Cockpit direkt Minecrafts Vorwaerts-, Rueckwaerts- und
 * Angriffstaste aus - siehe {@link JetController}.
 */
public final class KeyBinds {
	private static final String CATEGORY = "category.f47.jet";

	public static KeyBinding fireMissile;
	public static KeyBinding cycleWeapon;
	public static KeyBinding stealth;
	public static KeyBinding afterburner;
	public static KeyBinding freeLook;
	public static KeyBinding joystickSetup;

	private KeyBinds() {
	}

	public static void register() {
		// Alle folgenden Tasten sind in Minecraft standardmaessig unbelegt.
		fireMissile = register("key.f47.fire_missile", GLFW.GLFW_KEY_R);
		cycleWeapon = register("key.f47.cycle_weapon", GLFW.GLFW_KEY_X);
		stealth = register("key.f47.stealth", GLFW.GLFW_KEY_V);
		afterburner = register("key.f47.afterburner", GLFW.GLFW_KEY_C);
		freeLook = register("key.f47.free_look", GLFW.GLFW_KEY_LEFT_ALT);
		joystickSetup = register("key.f47.joystick_setup", GLFW.GLFW_KEY_J);
	}

	private static KeyBinding register(String translationKey, int key) {
		return KeyBindingHelper.registerKeyBinding(
				new KeyBinding(translationKey, InputUtil.Type.KEYSYM, key, CATEGORY));
	}
}
