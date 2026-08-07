package com.f47mod.client;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Cockpit-Belegung. Bewusst auf Tasten gelegt, die beim Fliegen frei sind.
 *
 * <p>Die Umschalttaste bleibt absichtlich unbelegt: Damit steigt man in
 * Minecraft aus einem Fahrzeug aus - der Nachbrenner liegt deshalb auf C.
 */
public final class KeyBinds {
	private static final String CATEGORY = "category.f47.jet";

	public static KeyBinding throttleUp;
	public static KeyBinding throttleDown;
	public static KeyBinding fire;
	public static KeyBinding fireMissile;
	public static KeyBinding cycleWeapon;
	public static KeyBinding stealth;
	public static KeyBinding afterburner;
	public static KeyBinding freeLook;
	public static KeyBinding joystickSetup;

	private KeyBinds() {
	}

	public static void register() {
		throttleUp = register("key.f47.throttle_up", GLFW.GLFW_KEY_W);
		throttleDown = register("key.f47.throttle_down", GLFW.GLFW_KEY_S);
		fire = register("key.f47.fire", GLFW.GLFW_KEY_SPACE);
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
