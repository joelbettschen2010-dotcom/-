package com.f47mod.client;

import com.f47mod.client.input.JoystickManager;
import com.f47mod.client.input.JoystickSettings;
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
 * <p>Es gibt zwei Betriebsarten:
 * <ul>
 *   <li><b>Maus:</b> Der Jet folgt der Blickrichtung, der Schub liegt auf
 *       W und S. Einfach zu lernen.</li>
 *   <li><b>Joystick:</b> Knueppel und Schubhebel steuern Hoehen-, Quer- und
 *       Seitenruder unmittelbar, der Schubhebel gibt den Schub absolut vor.
 *       Wird automatisch benutzt, sobald eine Belegung eingerichtet ist.</li>
 * </ul>
 *
 * <p>Der Client rechnet die Flugbewegung selbst und meldet dem Server nur den
 * Zustand der Bedienelemente - das haelt die Steuerung reaktionsschnell.
 */
public final class JetController {
	/** Wie schnell der Schubhebel auf Tastendruck reagiert (Mausbetrieb). */
	private static final float THROTTLE_STEP = 0.035f;

	private static boolean stealthOn;
	private static boolean afterburnerOn;
	private static boolean stealthWasDown;
	private static boolean afterburnerWasDown;
	private static boolean cycleWasDown;
	private static float throttle;
	private static int fireCooldown;
	/** Zuletzt benutzte Betriebsart - nur fuer die Anzeige. */
	private static boolean joystickActive;

	private JetController() {
	}

	public static void tick(MinecraftClient client) {
		// Eingabegeraete immer abfragen, damit der Zuordnungs-Bildschirm und
		// die Erkennung neuer Geraete auch ausserhalb des Cockpits laufen.
		JoystickManager.poll();

		F47Entity jet = getJet(client);
		if (jet == null) {
			reset();
			return;
		}
		if (fireCooldown > 0) {
			fireCooldown--;
		}

		JoystickSettings settings = JoystickSettings.get();
		joystickActive = settings.isUsable();

		if (joystickActive) {
			updateFromJoystick(client, jet, settings);
		} else {
			updateFromKeyboard(client, jet);
		}
		updateWeapons(client, jet, settings);

		ClientPlayNetworking.send(new ModNetworking.JetControlPayload(
				throttle, jet.getRoll(), stealthOn, afterburnerOn));
	}

	// ------------------------------------------------------------------
	// Joystick
	// ------------------------------------------------------------------

	private static void updateFromJoystick(MinecraftClient client, F47Entity jet,
			JoystickSettings settings) {
		float pitch = settings.shaped(JoystickSettings.Axis.PITCH);
		float roll = settings.shaped(JoystickSettings.Axis.ROLL);
		float yaw = settings.shaped(JoystickSettings.Axis.YAW);
		jet.setControlAxes(pitch, roll, yaw, true);

		// Schubhebel gibt den Schub unmittelbar vor - kein Hochtippen noetig.
		JoystickSettings.AxisBinding throttleAxis = settings.axis(JoystickSettings.Axis.THROTTLE);
		if (throttleAxis.isBound()) {
			throttle = MathHelper.clamp(throttleAxis.unipolar(), 0.0f, 1.0f);
		} else if (KeyBinds.throttleUp.isPressed()) {
			throttle = Math.min(1.0f, throttle + THROTTLE_STEP);
		} else if (KeyBinds.throttleDown.isPressed()) {
			throttle = Math.max(0.0f, throttle - THROTTLE_STEP);
		}

		// Bremse auf dem Boden.
		if (settings.button(JoystickSettings.Button.BRAKE).pressed() && jet.isOnGround()) {
			throttle = 0.0f;
			jet.setVelocity(jet.getVelocity().multiply(0.82, 1.0, 0.82));
		}
		jet.setThrottle(throttle);

		toggleFromJoystick(client, jet, settings);

		// Die Sicht folgt der Maschine, sonst schaut man beim Rollen ins Leere.
		if (client.player != null) {
			client.player.setYaw(jet.getYaw());
			client.player.setPitch(jet.getPitch());
		}
	}

	private static void toggleFromJoystick(MinecraftClient client, F47Entity jet,
			JoystickSettings settings) {
		boolean stealthDown = settings.button(JoystickSettings.Button.STEALTH).pressed()
				|| KeyBinds.stealth.isPressed();
		if (stealthDown && !stealthWasDown) {
			setStealth(client, jet, !stealthOn);
		}
		stealthWasDown = stealthDown;

		boolean afterburnerDown = settings.button(JoystickSettings.Button.AFTERBURNER).pressed()
				|| KeyBinds.afterburner.isPressed();
		if (afterburnerDown && !afterburnerWasDown) {
			afterburnerOn = !afterburnerOn;
			jet.setAfterburner(afterburnerOn);
			playClick(client, afterburnerOn ? 1.9f : 1.0f);
		}
		afterburnerWasDown = afterburnerDown;
	}

	// ------------------------------------------------------------------
	// Maus und Tastatur
	// ------------------------------------------------------------------

	private static void updateFromKeyboard(MinecraftClient client, F47Entity jet) {
		// Ohne Knueppel folgt der Jet der Blickrichtung.
		jet.setControlAxes(0.0f, 0.0f, 0.0f, false);

		if (KeyBinds.throttleUp.isPressed()) {
			throttle = Math.min(1.0f, throttle + THROTTLE_STEP);
		} else if (KeyBinds.throttleDown.isPressed()) {
			throttle = Math.max(0.0f, throttle - THROTTLE_STEP);
		}
		jet.setThrottle(throttle);

		boolean stealthDown = KeyBinds.stealth.isPressed();
		if (stealthDown && !stealthWasDown) {
			setStealth(client, jet, !stealthOn);
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

	private static void setStealth(MinecraftClient client, F47Entity jet, boolean on) {
		stealthOn = on;
		jet.setStealth(on);
		message(client, Text.translatable(on ? "message.f47.stealth_on" : "message.f47.stealth_off"));
		playClick(client, on ? 1.6f : 0.9f);
	}

	// ------------------------------------------------------------------
	// Waffen
	// ------------------------------------------------------------------

	private static void updateWeapons(MinecraftClient client, F47Entity jet,
			JoystickSettings settings) {
		boolean cycleDown = KeyBinds.cycleWeapon.isPressed()
				|| settings.button(JoystickSettings.Button.CYCLE_WEAPON).pressed();
		if (cycleDown && !cycleWasDown) {
			ClientPlayNetworking.send(new ModNetworking.CycleWeaponPayload());
			playClick(client, 1.3f);
		}
		cycleWasDown = cycleDown;

		if (fireCooldown > 0) {
			return;
		}

		boolean fire = KeyBinds.fire.isPressed()
				|| settings.button(JoystickSettings.Button.FIRE).pressed();
		boolean missile = KeyBinds.fireMissile.isPressed()
				|| settings.button(JoystickSettings.Button.MISSILE).pressed();

		if (fire) {
			WeaponType weapon = jet.getWeapon();
			ClientPlayNetworking.send(new ModNetworking.FireWeaponPayload(weapon.ordinal()));
			fireCooldown = Math.max(1, weapon.cooldownTicks());
		} else if (missile) {
			// Eigener Knopf fuer die Lenkwaffe, egal was gerade gewaehlt ist.
			ClientPlayNetworking.send(new ModNetworking.FireWeaponPayload(WeaponType.MISSILE.ordinal()));
			fireCooldown = WeaponType.MISSILE.cooldownTicks();
		}
	}

	// ------------------------------------------------------------------

	/** Beim Einsteigen den Schubhebel auf den Stand des Jets setzen. */
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
		joystickActive = false;
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

	/** Wird gerade mit Joystick geflogen? Fuer die Anzeige im HUD. */
	public static boolean isJoystickActive() {
		return joystickActive;
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
