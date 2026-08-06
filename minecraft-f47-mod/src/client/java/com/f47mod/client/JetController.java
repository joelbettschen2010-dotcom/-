package com.f47mod.client;

import com.f47mod.entity.vehicle.F47Entity;
import com.f47mod.entity.vehicle.WeaponType;
import com.f47mod.net.ModNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Cockpit-Steuerung auf dem Client.
 *
 * <p>Hier werden die Tasteneingaben in Schub, Tarnung und Waffeneinsatz
 * uebersetzt. Der Client rechnet die Flugbewegung selbst und schickt den
 * Zustand an den Server, der die Munition verwaltet und die Schuesse ausloest.
 */
public final class JetController {
	/** Wie schnell der Schubhebel auf Tastendruck reagiert. */
	private static final float THROTTLE_STEP = 0.035f;

	private static boolean stealthOn;
	private static boolean afterburnerOn;
	private static boolean stealthWasDown;
	private static boolean afterburnerWasDown;
	private static boolean cycleWasDown;
	private static float throttle;
	private static int fireCooldown;

	private JetController() {
	}

	public static void tick(MinecraftClient client) {
		F47Entity jet = getJet(client);
		if (jet == null) {
			reset();
			return;
		}
		if (fireCooldown > 0) {
			fireCooldown--;
		}

		updateThrottle(client, jet);
		updateSwitches(client, jet);
		updateWeapons(client, jet);

		// Der Server bekommt den Zustand der Bedienelemente.
		ClientPlayNetworking.send(new ModNetworking.JetControlPayload(
				throttle, jet.getRoll(), stealthOn, afterburnerOn));
	}

	private static void updateThrottle(MinecraftClient client, F47Entity jet) {
		if (KeyBinds.throttleUp.isPressed()) {
			throttle = Math.min(1.0f, throttle + THROTTLE_STEP);
		} else if (KeyBinds.throttleDown.isPressed()) {
			throttle = Math.max(0.0f, throttle - THROTTLE_STEP);
		}
		jet.setThrottle(throttle);
	}

	private static void updateSwitches(MinecraftClient client, F47Entity jet) {
		boolean stealthDown = KeyBinds.stealth.isPressed();
		if (stealthDown && !stealthWasDown) {
			stealthOn = !stealthOn;
			jet.setStealth(stealthOn);
			message(client, Text.translatable(stealthOn
					? "message.f47.stealth_on" : "message.f47.stealth_off"));
			playClick(client, stealthOn ? 1.6f : 0.9f);
		}
		stealthWasDown = stealthDown;

		boolean afterburnerDown = KeyBinds.afterburner.isPressed();
		if (afterburnerDown && !afterburnerWasDown) {
			afterburnerOn = !afterburnerOn;
			jet.setAfterburner(afterburnerOn);
			playClick(client, afterburnerOn ? 1.9f : 1.0f);
		}
		afterburnerWasDown = afterburnerDown;

		if (afterburnerOn) {
			// Nachbrenner braucht auch vollen Schub.
			throttle = 1.0f;
		}
	}

	private static void updateWeapons(MinecraftClient client, F47Entity jet) {
		boolean cycleDown = KeyBinds.cycleWeapon.isPressed();
		if (cycleDown && !cycleWasDown) {
			ClientPlayNetworking.send(new ModNetworking.CycleWeaponPayload());
			playClick(client, 1.3f);
		}
		cycleWasDown = cycleDown;

		if (fireCooldown > 0) {
			return;
		}

		if (KeyBinds.fire.isPressed()) {
			WeaponType weapon = jet.getWeapon();
			ClientPlayNetworking.send(new ModNetworking.FireWeaponPayload(weapon.ordinal()));
			fireCooldown = Math.max(1, weapon.cooldownTicks());
		} else if (KeyBinds.fireMissile.isPressed()) {
			// Zweite Taste feuert direkt eine Lenkwaffe, egal was gewaehlt ist.
			ClientPlayNetworking.send(new ModNetworking.FireWeaponPayload(WeaponType.MISSILE.ordinal()));
			fireCooldown = WeaponType.MISSILE.cooldownTicks();
		}
	}

	/** Beim Einsteigen den Hebel auf den aktuellen Stand des Jets setzen. */
	public static void onBoard(F47Entity jet) {
		throttle = jet.getThrottle();
		stealthOn = jet.isStealthOn();
		afterburnerOn = jet.isAfterburner();
	}

	private static void reset() {
		throttle = 0.0f;
		stealthOn = false;
		afterburnerOn = false;
		fireCooldown = 0;
	}

	public static float getThrottle() {
		return throttle;
	}

	public static boolean isStealthOn() {
		return stealthOn;
	}

	public static boolean isAfterburnerOn() {
		return afterburnerOn;
	}

	/** Der Jet, in dem der Spieler gerade sitzt - oder null. */
	@Nullable
	public static F47Entity getJet(MinecraftClient client) {
		if (client.player == null) {
			return null;
		}
		return client.player.getVehicle() instanceof F47Entity jet ? jet : null;
	}

	private static void message(MinecraftClient client, Text text) {
		if (client.player != null) {
			client.player.sendMessage(text, true);
		}
	}

	private static void playClick(MinecraftClient client, float pitch) {
		if (client.player != null) {
			client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f,
					MathHelper.clamp(pitch, 0.5f, 2.0f));
		}
	}
}
