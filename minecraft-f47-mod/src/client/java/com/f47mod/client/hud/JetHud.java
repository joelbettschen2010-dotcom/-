package com.f47mod.client.hud;

import com.f47mod.F47Config;
import com.f47mod.client.ClientRadarState;
import com.f47mod.client.JetController;
import com.f47mod.entity.vehicle.F47Entity;
import com.f47mod.entity.vehicle.WeaponType;
import com.f47mod.net.RadarContact;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Cockpitanzeige der F-47.
 *
 * <p>Links Geschwindigkeit und Schub, rechts Hoehe, unten Waffen und Zustand,
 * in der Mitte das Zielkreuz mit Aufschaltanzeige und unten rechts der
 * Radarschirm. Alles direkt gezeichnet - dafuer braucht es keine Texturen.
 */
public final class JetHud {
	// Farben im typischen Grün eines Head-up-Displays.
	private static final int HUD_GREEN = 0xFF3DF07A;
	private static final int HUD_DIM = 0xFF1F7A42;
	private static final int HUD_RED = 0xFFFF4444;
	private static final int HUD_AMBER = 0xFFFFC845;
	private static final int PANEL_BG = 0x90000000;

	private JetHud() {
	}

	public static void render(DrawContext context, float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		F47Entity jet = JetController.getJet(client);
		if (jet == null || client.options.hudHidden) {
			return;
		}

		int width = context.getScaledWindowWidth();
		int height = context.getScaledWindowHeight();
		TextRenderer font = client.textRenderer;

		drawSpeedTape(context, font, jet, width, height);
		drawAltitudeTape(context, font, jet, width, height);
		drawStatusPanel(context, font, jet, width, height);
		drawReticle(context, font, jet, width, height);
		drawRadarScope(context, font, jet, width, height);
		drawWarnings(context, font, jet, width, height);
		drawStickIndicator(context, font, jet, width, height);
	}

	/** Linke Seite: Geschwindigkeit in km/h und Schubbalken. */
	private static void drawSpeedTape(DrawContext context, TextRenderer font, F47Entity jet,
			int width, int height) {
		int x = 18;
		int y = height / 2 - 40;

		// 1 Block/Tick entspricht 20 Bloecken/Sekunde = 72 km/h.
		int speed = (int) (jet.getVelocity().length() * 72.0);
		context.fill(x - 4, y - 6, x + 62, y + 86, PANEL_BG);
		context.drawText(font, Text.translatable("hud.f47.speed"), x, y, HUD_DIM, false);
		context.drawText(font, Text.literal(speed + " km/h"), x, y + 10, HUD_GREEN, false);

		// Schubhebel als senkrechter Balken.
		context.drawText(font, Text.translatable("hud.f47.throttle"), x, y + 26, HUD_DIM, false);
		int barTop = y + 36;
		int barHeight = 40;
		context.fill(x, barTop, x + 10, barTop + barHeight, 0xFF202020);
		int filled = (int) (barHeight * jet.getThrottle());
		int throttleColour = jet.isAfterburner() ? HUD_AMBER : HUD_GREEN;
		context.fill(x, barTop + barHeight - filled, x + 10, barTop + barHeight, throttleColour);
		context.drawText(font, Text.literal((int) (jet.getThrottle() * 100) + "%"),
				x + 14, barTop + barHeight - 10, HUD_GREEN, false);

		if (jet.isAfterburner()) {
			context.drawText(font, Text.translatable("hud.f47.afterburner"), x, y + 78, HUD_AMBER, false);
		}
	}

	/** Rechte Seite: Hoehe ueber Grund und Steigrate. */
	private static void drawAltitudeTape(DrawContext context, TextRenderer font, F47Entity jet,
			int width, int height) {
		int x = width - 78;
		int y = height / 2 - 40;

		context.fill(x - 4, y - 6, x + 72, y + 52, PANEL_BG);
		context.drawText(font, Text.translatable("hud.f47.altitude"), x, y, HUD_DIM, false);
		context.drawText(font, Text.literal((int) jet.getY() + " m"), x, y + 10, HUD_GREEN, false);

		double climb = jet.getVelocity().y * 20.0;
		int climbColour = climb < -12.0 ? HUD_RED : HUD_GREEN;
		context.drawText(font, Text.translatable("hud.f47.climb"), x, y + 24, HUD_DIM, false);
		context.drawText(font, Text.literal(String.format("%+.0f m/s", climb)), x, y + 34, climbColour, false);
	}

	/** Unten: Waffen, Munition, Struktur, Treibstoff, Tarnkappe. */
	private static void drawStatusPanel(DrawContext context, TextRenderer font, F47Entity jet,
			int width, int height) {
		F47Config config = F47Config.get();
		int x = width / 2 - 100;
		int y = height - 62;
		context.fill(x - 6, y - 6, x + 206, y + 34, PANEL_BG);

		WeaponType weapon = jet.getWeapon();
		int ammo = jet.getAmmo(weapon);
		int ammoColour = ammo <= 0 ? HUD_RED : HUD_GREEN;
		context.drawText(font, Text.translatable(weapon.translationKey()), x, y, HUD_AMBER, false);
		context.drawText(font, Text.literal(weapon == WeaponType.LASER ? "∞" : String.valueOf(ammo)),
				x + 100, y, ammoColour, false);

		// Struktur und Treibstoff als schmale Balken.
		drawBar(context, x, y + 12, 90, jet.getStructure() / config.jetMaxHealth,
				jet.getStructure() < config.jetMaxHealth * 0.3f ? HUD_RED : HUD_GREEN);
		context.drawText(font, Text.translatable("hud.f47.structure"), x + 96, y + 12, HUD_DIM, false);

		drawBar(context, x, y + 24, 90, jet.getFuel() / config.maxFuel,
				jet.getFuel() < config.maxFuel * 0.15f ? HUD_RED : HUD_GREEN);
		context.drawText(font, Text.translatable("hud.f47.fuel"), x + 96, y + 24, HUD_DIM, false);

		if (jet.isStealthOn()) {
			Text label = jet.isStealthEffective()
					? Text.translatable("hud.f47.stealth_active")
					: Text.translatable("hud.f47.stealth_broken");
			context.drawText(font, label, x + 140, y + 12,
					jet.isStealthEffective() ? HUD_GREEN : HUD_AMBER, false);
		}
	}

	private static void drawBar(DrawContext context, int x, int y, int width, float fraction, int colour) {
		context.fill(x, y, x + width, y + 6, 0xFF202020);
		int filled = (int) (width * MathHelper.clamp(fraction, 0.0f, 1.0f));
		context.fill(x, y, x + filled, y + 6, colour);
	}

	/** Zielkreuz und Aufschaltrahmen um das erfasste Ziel. */
	private static void drawReticle(DrawContext context, TextRenderer font, F47Entity jet,
			int width, int height) {
		int cx = width / 2;
		int cy = height / 2;

		// Einfaches Fadenkreuz.
		context.fill(cx - 12, cy, cx - 4, cy + 1, HUD_GREEN);
		context.fill(cx + 4, cy, cx + 12, cy + 1, HUD_GREEN);
		context.fill(cx, cy - 12, cx + 1, cy - 4, HUD_GREEN);
		context.fill(cx, cy + 4, cx + 1, cy + 12, HUD_GREEN);

		Entity target = jet.getLockedTarget();
		if (target == null) {
			return;
		}

		// Aufschaltkasten mit Entfernung.
		int size = 16;
		int colour = HUD_RED;
		context.fill(cx - size, cy - size, cx + size, cy - size + 1, colour);
		context.fill(cx - size, cy + size - 1, cx + size, cy + size, colour);
		context.fill(cx - size, cy - size, cx - size + 1, cy + size, colour);
		context.fill(cx + size - 1, cy - size, cx + size, cy + size, colour);

		int distance = (int) jet.distanceTo(target);
		Text label = Text.translatable("hud.f47.lock", distance);
		context.drawText(font, label, cx - font.getWidth(label) / 2, cy + size + 4, HUD_RED, false);
	}

	/** Radarschirm unten rechts: eigene Position in der Mitte, Kontakte drumherum. */
	private static void drawRadarScope(DrawContext context, TextRenderer font, F47Entity jet,
			int width, int height) {
		int radius = 42;
		int cx = width - radius - 16;
		int cy = height - radius - 16;
		double range = F47Config.get().jetRadarRange;

		// Hintergrund und Ringe.
		context.fill(cx - radius, cy - radius, cx + radius, cy + radius, 0xA0001505);
		drawCircle(context, cx, cy, radius, HUD_DIM);
		drawCircle(context, cx, cy, radius / 2, HUD_DIM);
		context.fill(cx - radius, cy, cx + radius, cy + 1, HUD_DIM);
		context.fill(cx, cy - radius, cx + 1, cy + radius, HUD_DIM);

		// Der Schirm dreht sich mit dem Flugzeug: oben ist immer die Nase.
		double heading = Math.toRadians(jet.getYaw());
		double sin = Math.sin(heading);
		double cos = Math.cos(heading);
		Vec3d self = jet.getPos();

		for (RadarContact contact : ClientRadarState.getContacts()) {
			if (contact.entityId() == jet.getId()) {
				continue;
			}
			double dx = contact.x() - self.x;
			double dz = contact.z() - self.z;
			double distance = Math.sqrt(dx * dx + dz * dz);
			if (distance > range) {
				continue;
			}

			// In Flugzeugkoordinaten drehen.
			double relX = dx * cos - dz * sin;
			double relZ = dz * cos + dx * sin;
			int px = cx + (int) (relX / range * radius);
			int py = cy - (int) (relZ / range * radius);

			int colour = contact.hostile() ? HUD_RED : HUD_GREEN;
			int blipSize = switch (contact.type()) {
				case MISSILE -> 1;
				case DRONE, AIRCRAFT -> 2;
				case GROUND -> 1;
			};
			context.fill(px - blipSize, py - blipSize, px + blipSize + 1, py + blipSize + 1, colour);

			// Hoehenunterschied als kleiner Strich ueber oder unter dem Punkt.
			double dy = contact.y() - self.y;
			if (Math.abs(dy) > 6.0) {
				int tick = dy > 0 ? -3 : 3;
				context.fill(px, py + tick, px + 1, py + tick + (dy > 0 ? 2 : -2), colour);
			}
		}

		int threats = (int) ClientRadarState.getContacts().stream().filter(RadarContact::hostile).count();
		context.drawText(font, Text.translatable("hud.f47.radar", threats),
				cx - radius, cy - radius - 10, threats > 0 ? HUD_RED : HUD_DIM, false);

		// Eigene Partei - wichtig, sobald zwei Seiten im Spiel sind.
		var team = ClientRadarState.getViewerTeam();
		Text label = Text.translatable(team.translationKey());
		context.drawText(font, label, cx + radius - font.getWidth(label), cy - radius - 10,
				team.colour(), false);
	}

	/** Warnhinweise: Ueberziehen, Treibstoff, Bodenannaeherung. */
	private static void drawWarnings(DrawContext context, TextRenderer font, F47Entity jet,
			int width, int height) {
		F47Config config = F47Config.get();
		int cx = width / 2;
		int y = height / 2 - 60;
		boolean blink = (jet.age / 5) % 2 == 0;

		if (jet.getFuel() <= 0.0f) {
			drawCentered(context, font, Text.translatable("hud.f47.warn_no_fuel"), cx, y, HUD_RED);
			y += 12;
		} else if (jet.getFuel() < config.maxFuel * 0.12f && blink) {
			drawCentered(context, font, Text.translatable("hud.f47.warn_fuel"), cx, y, HUD_AMBER);
			y += 12;
		}

		// Der Abriss haengt am Anstellwinkel, nicht an der Geschwindigkeit -
		// die Warnung liest deshalb genau den Wert, mit dem die Physik rechnet.
		if (jet.isStalled() && blink) {
			drawCentered(context, font, Text.translatable("hud.f47.warn_stall"), cx, y, HUD_RED);
			y += 12;
		}

		// Bodenannaeherung: Sinkflug und wenig Platz nach unten.
		if (!jet.isOnGround() && jet.getVelocity().y < -0.25) {
			int ground = groundDistance(jet);
			if (ground < 25 && blink) {
				drawCentered(context, font, Text.translatable("hud.f47.warn_pullup"), cx, y, HUD_RED);
			}
		}
	}

	/**
	 * Kleine Knueppelanzeige unten links: zeigt die Stellung von Stick und
	 * Schubhebel. Praktisch, um zu sehen, ob das Geraet richtig erkannt wird.
	 */
	private static void drawStickIndicator(DrawContext context, TextRenderer font, F47Entity jet,
			int width, int height) {
		if (!JetController.isJoystickActive()) {
			return;
		}
		var settings = com.f47mod.client.input.JoystickSettings.get();
		int size = 34;
		int x = 18;
		int y = height - size - 18;

		context.fill(x, y, x + size, y + size, 0xA0001505);
		context.fill(x, y + size / 2, x + size, y + size / 2 + 1, HUD_DIM);
		context.fill(x + size / 2, y, x + size / 2 + 1, y + size, HUD_DIM);

		float stickX = settings.axis(com.f47mod.client.input.JoystickSettings.Axis.ROLL).value();
		float stickY = settings.axis(com.f47mod.client.input.JoystickSettings.Axis.PITCH).value();
		int px = x + size / 2 + (int) (stickX * (size / 2 - 2));
		int py = y + size / 2 + (int) (stickY * (size / 2 - 2));
		context.fill(px - 2, py - 2, px + 3, py + 3, HUD_GREEN);

		context.drawText(font, Text.translatable("hud.f47.joystick"), x, y - 10, HUD_DIM, false);
	}

	private static int groundDistance(F47Entity jet) {
		var world = jet.getWorld();
		var pos = jet.getBlockPos();
		for (int i = 1; i <= 40; i++) {
			if (!world.getBlockState(pos.down(i)).isAir()) {
				return i;
			}
		}
		return 999;
	}

	private static void drawCentered(DrawContext context, TextRenderer font, Text text, int cx, int y, int colour) {
		context.drawText(font, text, cx - font.getWidth(text) / 2, y, colour, false);
	}

	/** Zeichnet einen Kreis aus einzelnen Punkten (kein Textur-Bedarf). */
	private static void drawCircle(DrawContext context, int cx, int cy, int radius, int colour) {
		for (int i = 0; i < 72; i++) {
			double angle = Math.PI * 2.0 * i / 72.0;
			int x = cx + (int) (Math.cos(angle) * radius);
			int y = cy + (int) (Math.sin(angle) * radius);
			context.fill(x, y, x + 1, y + 1, colour);
		}
	}
}
