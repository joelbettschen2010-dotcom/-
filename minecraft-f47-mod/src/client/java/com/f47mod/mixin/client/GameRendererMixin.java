package com.f47mod.mixin.client;

import com.f47mod.client.JetController;
import com.f47mod.client.input.JoystickSettings;
import com.f47mod.entity.vehicle.F47Entity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Laesst die Sicht mit dem Flugzeug mitrollen.
 *
 * <p>Ohne das bleibt der Horizont beim Kurvenflug waagerecht, waehrend sich die
 * Maschine sichtbar legt - mit Joystick fuehlt sich das falsch an. Hier wird
 * die Kameramatrix zusaetzlich um die Laengsachse gedreht.
 *
 * <p>Der Eingriff ist bewusst mit {@code require = 0} angemeldet: Sollte
 * Minecraft die Stelle einmal umbauen, entfaellt nur das Mitrollen - das Spiel
 * startet trotzdem.
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

	@Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), require = 0)
	private void f47$rollWithAircraft(MatrixStack matrices, float tickDelta, CallbackInfo info) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || !client.options.getPerspective().isFirstPerson()) {
			return;
		}
		if (!(client.player.getVehicle() instanceof F47Entity jet)) {
			return;
		}

		float amount = JoystickSettings.get().cameraRoll;
		if (amount <= 0.0f) {
			return;
		}
		// Mit Knueppel voll mitrollen, bei Maussteuerung nur angedeutet - dort
		// entsteht die Querlage automatisch und wuerde sonst irritieren.
		if (!JetController.isJoystickActive()) {
			amount *= 0.35f;
		}
		float roll = MathHelper.wrapDegrees(jet.getRoll()) * amount;
		if (Math.abs(roll) > 0.01f) {
			matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll));
		}
	}
}
