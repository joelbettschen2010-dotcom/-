package com.f47mod.client.gui;

import com.f47mod.client.input.JoystickManager;
import com.f47mod.client.input.JoystickSettings;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

import java.util.List;

/**
 * Zuordnungs-Bildschirm fuer Joystick und Schubhebel.
 *
 * <p>Achsen- und Knopfnummern sind bei jedem Geraet anders, deshalb wird hier
 * nichts geraten: Du klickst auf "Belegen" und bewegst dann einfach die Achse
 * oder druckst den Knopf, den du dafuer haben willst. Der Bildschirm erkennt,
 * was sich bewegt hat, und traegt es ein.
 *
 * <p>Fuer den Schubhebel gibt es zusaetzlich eine Kalibrierung: Waehrend sie
 * laeuft, schiebst du den Hebel einmal ganz vor und ganz zurueck. So lernt der
 * Mod den tatsaechlichen Weg deines Hebels - viele geben naemlich nicht die
 * vollen -1 bis 1 aus.
 */
public class JoystickScreen extends Screen {
	private static final int ROW_HEIGHT = 22;

	private final Screen parent;
	private final JoystickSettings settings = JoystickSettings.get();

	/** Achse, die gerade angelernt wird. */
	private JoystickSettings.Axis listeningAxis;
	/** Knopf, der gerade angelernt wird. */
	private JoystickSettings.Button listeningButton;
	/** Achse, deren Weg gerade vermessen wird. */
	private JoystickSettings.Axis calibratingAxis;
	private int calibrationTicks;

	/** Ruhelage beim Start des Anlernens - daran wird Bewegung erkannt. */
	private float[][] baseline;
	/** Obere Kante der Knopf-Zeilen, in init() gesetzt. */
	private int buttonRowsTop;

	public JoystickScreen(Screen parent) {
		super(Text.translatable("screen.f47.joystick"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int left = width / 2 - 190;
		int y = 46;

		for (JoystickSettings.Axis axis : JoystickSettings.Axis.values()) {
			JoystickSettings.Axis current = axis;
			addDrawableChild(ButtonWidget.builder(Text.translatable("screen.f47.assign"),
							button -> startListeningAxis(current))
					.dimensions(left + 210, y, 70, 20).build());
			addDrawableChild(ButtonWidget.builder(Text.translatable("screen.f47.invert"),
							button -> {
								settings.axis(current).invert = !settings.axis(current).invert;
								settings.save();
							})
					.dimensions(left + 284, y, 60, 20).build());
			addDrawableChild(ButtonWidget.builder(Text.literal("X"),
							button -> {
								settings.axis(current).clear();
								settings.save();
							})
					.dimensions(left + 348, y, 20, 20).build());
			y += ROW_HEIGHT;
		}

		// Kalibrierung des Schubhebels
		addDrawableChild(ButtonWidget.builder(Text.translatable("screen.f47.calibrate_throttle"),
						button -> startCalibration(JoystickSettings.Axis.THROTTLE))
				.dimensions(left + 210, y + 2, 158, 20).build());
		y += ROW_HEIGHT + 6;

		int buttonRowTop = y;
		for (JoystickSettings.Button which : JoystickSettings.Button.values()) {
			JoystickSettings.Button current = which;
			addDrawableChild(ButtonWidget.builder(Text.translatable("screen.f47.assign"),
							button -> startListeningButton(current))
					.dimensions(left + 210, y, 70, 20).build());
			addDrawableChild(ButtonWidget.builder(Text.literal("X"),
							button -> {
								settings.button(current).clear();
								settings.save();
							})
					.dimensions(left + 348, y, 20, 20).build());
			y += ROW_HEIGHT;
		}
		this.buttonRowsTop = buttonRowTop;

		// Fusszeile
		addDrawableChild(ButtonWidget.builder(
						Text.translatable(settings.enabled
								? "screen.f47.joystick_on" : "screen.f47.joystick_off"),
						button -> {
							settings.enabled = !settings.enabled;
							settings.save();
							clearAndInit();
						})
				.dimensions(left, height - 30, 120, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("screen.f47.reset"),
						button -> {
							settings.reset();
							clearAndInit();
						})
				.dimensions(left + 126, height - 30, 100, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> close())
				.dimensions(left + 232, height - 30, 136, 20).build());
	}

	private void startListeningAxis(JoystickSettings.Axis axis) {
		listeningAxis = axis;
		listeningButton = null;
		calibratingAxis = null;
		captureBaseline();
	}

	private void startListeningButton(JoystickSettings.Button button) {
		listeningButton = button;
		listeningAxis = null;
		calibratingAxis = null;
	}

	private void startCalibration(JoystickSettings.Axis axis) {
		if (!settings.axis(axis).isBound()) {
			return;
		}
		calibratingAxis = axis;
		listeningAxis = null;
		listeningButton = null;
		calibrationTicks = 100;
		JoystickSettings.AxisBinding binding = settings.axis(axis);
		// Mit umgekehrten Grenzen starten, damit die Messung sie aufzieht.
		binding.min = Float.MAX_VALUE;
		binding.max = -Float.MAX_VALUE;
	}

	/** Ruhelage aller Achsen merken, um Bewegung erkennen zu koennen. */
	private void captureBaseline() {
		List<JoystickManager.Device> devices = JoystickManager.devices();
		baseline = new float[devices.size()][];
		for (int i = 0; i < devices.size(); i++) {
			baseline[i] = devices.get(i).axes.clone();
		}
	}

	@Override
	public void tick() {
		JoystickManager.poll();

		if (calibratingAxis != null) {
			runCalibration();
			return;
		}
		if (listeningAxis != null) {
			detectMovedAxis();
			return;
		}
		if (listeningButton != null) {
			detectPressedButton();
		}
	}

	/** Waehrend der Kalibrierung den groessten und kleinsten Wert merken. */
	private void runCalibration() {
		JoystickSettings.AxisBinding binding = settings.axis(calibratingAxis);
		float raw = binding.raw();
		binding.min = Math.min(binding.min, raw);
		binding.max = Math.max(binding.max, raw);

		if (--calibrationTicks <= 0) {
			// Zu kleiner Weg heisst: Der Hebel wurde nicht bewegt.
			if (binding.max - binding.min < 0.2f) {
				binding.min = -1.0f;
				binding.max = 1.0f;
			}
			calibratingAxis = null;
			settings.save();
		}
	}

	/** Die Achse finden, die sich seit dem Klick am staerksten bewegt hat. */
	private void detectMovedAxis() {
		List<JoystickManager.Device> devices = JoystickManager.devices();
		float bestDelta = 0.35f;
		int bestDevice = -1;
		int bestAxis = -1;

		for (int i = 0; i < devices.size() && baseline != null && i < baseline.length; i++) {
			JoystickManager.Device device = devices.get(i);
			float[] start = baseline[i];
			for (int axis = 0; axis < device.axes.length && axis < start.length; axis++) {
				float delta = Math.abs(device.axes[axis] - start[axis]);
				if (delta > bestDelta) {
					bestDelta = delta;
					bestDevice = device.id;
					bestAxis = axis;
				}
			}
		}

		if (bestDevice >= 0) {
			JoystickSettings.AxisBinding binding = settings.axis(listeningAxis);
			binding.device = bestDevice;
			binding.axis = bestAxis;
			binding.min = -1.0f;
			binding.max = 1.0f;
			settings.save();
			listeningAxis = null;
			baseline = null;
		}
	}

	private void detectPressedButton() {
		for (JoystickManager.Device device : JoystickManager.devices()) {
			for (int i = 0; i < device.buttonCount(); i++) {
				if (JoystickManager.button(device.id, i)) {
					bindButton(device.id, i);
					return;
				}
			}
			for (int hat = 0; hat < device.hats.length; hat++) {
				for (int direction = 0; direction < 4; direction++) {
					int virtual = JoystickManager.HAT_BUTTON_BASE + hat * 4 + direction;
					if (JoystickManager.button(device.id, virtual)) {
						bindButton(device.id, virtual);
						return;
					}
				}
			}
		}
	}

	private void bindButton(int device, int button) {
		JoystickSettings.ButtonBinding binding = settings.button(listeningButton);
		binding.device = device;
		binding.button = button;
		settings.save();
		listeningButton = null;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);

		int left = width / 2 - 190;
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);

		// Gefundene Geraete
		List<JoystickManager.Device> devices = JoystickManager.devices();
		Text deviceLine = devices.isEmpty()
				? Text.translatable("screen.f47.no_device").formatted(Formatting.RED)
				: Text.translatable("screen.f47.devices", devices.size(),
						devices.get(0).name).formatted(Formatting.GRAY);
		context.drawTextWithShadow(textRenderer, deviceLine, left, 28, 0xFFFFFF);

		int y = 46;
		for (JoystickSettings.Axis axis : JoystickSettings.Axis.values()) {
			drawAxisRow(context, left, y, axis);
			y += ROW_HEIGHT;
		}

		if (calibratingAxis != null) {
			context.drawTextWithShadow(textRenderer,
					Text.translatable("screen.f47.calibrating", calibrationTicks / 20 + 1)
							.formatted(Formatting.YELLOW),
					left, y + 8, 0xFFFFFF);
		}

		y = buttonRowsTop;
		for (JoystickSettings.Button which : JoystickSettings.Button.values()) {
			drawButtonRow(context, left, y, which);
			y += ROW_HEIGHT;
		}
	}

	private void drawAxisRow(DrawContext context, int left, int y, JoystickSettings.Axis axis) {
		JoystickSettings.AxisBinding binding = settings.axis(axis);
		context.drawTextWithShadow(textRenderer, Text.translatable(axis.translationKey()),
				left, y + 6, 0xFFFFFF);

		Text status;
		if (listeningAxis == axis) {
			status = Text.translatable("screen.f47.move_axis").formatted(Formatting.YELLOW);
		} else if (binding.isBound()) {
			status = Text.literal("#" + binding.device + " / Achse " + (binding.axis + 1)
					+ (binding.invert ? " (umgekehrt)" : "")).formatted(Formatting.GREEN);
		} else {
			status = Text.translatable("screen.f47.unbound").formatted(Formatting.DARK_GRAY);
		}
		context.drawTextWithShadow(textRenderer, status, left + 84, y + 6, 0xFFFFFF);

		// Lebendiger Balken, damit man sofort sieht, ob es funktioniert.
		int barX = left + 84;
		int barY = y + 16;
		int barWidth = 118;
		context.fill(barX, barY, barX + barWidth, barY + 3, 0xFF303030);
		if (binding.isBound()) {
			if (axis == JoystickSettings.Axis.THROTTLE) {
				int filled = (int) (barWidth * MathHelper.clamp(binding.unipolar(), 0.0f, 1.0f));
				context.fill(barX, barY, barX + filled, barY + 3, 0xFF3DF07A);
			} else {
				int centre = barX + barWidth / 2;
				int offset = (int) (binding.value() * barWidth / 2);
				context.fill(Math.min(centre, centre + offset), barY,
						Math.max(centre, centre + offset) + 1, barY + 3, 0xFF3DF07A);
			}
		}
	}

	private void drawButtonRow(DrawContext context, int left, int y, JoystickSettings.Button which) {
		JoystickSettings.ButtonBinding binding = settings.button(which);
		context.drawTextWithShadow(textRenderer, Text.translatable(which.translationKey()),
				left, y + 6, 0xFFFFFF);

		Text status;
		if (listeningButton == which) {
			status = Text.translatable("screen.f47.press_button").formatted(Formatting.YELLOW);
		} else if (binding.isBound()) {
			Formatting colour = binding.pressed() ? Formatting.GREEN : Formatting.GRAY;
			status = Text.literal("#" + binding.device + " / "
					+ JoystickManager.buttonName(binding.button)).formatted(colour);
		} else {
			status = Text.translatable("screen.f47.unbound").formatted(Formatting.DARK_GRAY);
		}
		context.drawTextWithShadow(textRenderer, status, left + 84, y + 6, 0xFFFFFF);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void close() {
		settings.save();
		if (client != null) {
			client.setScreen(parent);
		}
	}
}
