package com.f47mod.client.render;

import com.f47mod.F47Config;
import com.f47mod.F47Mod;
import com.f47mod.entity.vehicle.F47Entity;
import com.f47mod.util.Team;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.jetbrains.annotations.Nullable;

/**
 * Zeichnet die F-47 in der Welt - samt Querlage, Tarnkappen-Transparenz und
 * dem Nachbrennerleuchten am Heck.
 */
public class F47Renderer extends EntityRenderer<F47Entity> {
	private static final Identifier BLUE = F47Mod.id("textures/entity/f47_blue.png");
	private static final Identifier RED = F47Mod.id("textures/entity/f47_red.png");
	private static final Identifier STEALTH = F47Mod.id("textures/entity/f47_stealth.png");

	private final F47Model model;
	/** Zusaetzlicher Groessenfaktor - Bomber und Transporter sind groesser. */
	private final float sizeFactor;
	/** Feste Textur, falls dieses Muster keine Parteifarben hat. */
	@Nullable
	private final Identifier override;

	public F47Renderer(EntityRendererFactory.Context context) {
		this(context, 1.0f, null);
	}

	public F47Renderer(EntityRendererFactory.Context context, float sizeFactor,
			@Nullable Identifier override) {
		super(context);
		this.sizeFactor = sizeFactor;
		this.override = override;
		this.model = new F47Model(context.getPart(F47Model.LAYER));
		// Der Schatten soll mitwachsen, sonst schwebt eine grosse Maschine
		// ueber einem winzigen Fleck.
		this.shadowRadius = 1.5f * F47Config.get().jetModelScale * sizeFactor;
	}

	@Override
	public void render(F47Entity entity, float yaw, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light) {
		matrices.push();

		// Minecraft-Modelle zeigen kopfueber nach hinten - erst umdrehen.
		matrices.translate(0.0, 0.7, 0.0);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(
				MathHelper.lerp(tickDelta, entity.prevPitch, entity.getPitch())));
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(entity.getRoll()));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180.0f));

		// Modellmasse sind bereits Sechzehntel-Bloecke - Minecraft teilt jeden
		// Quader beim Zeichnen durch 16. Hier nochmal mit 0.1 zu verkleinern
		// hiesse, den Jet auf ein Zehntel zu schrumpfen; er war dadurch nur
		// gut drei Zentimeter gross. Der Faktor vergroessert stattdessen die
		// rund drei Bloecke des Rohmodells auf Kampfflugzeuggroesse.
		float scale = F47Config.get().jetModelScale * sizeFactor;
		matrices.scale(scale, scale, scale);

		Identifier texture = getTexture(entity);
		model.setAngles(entity, 0.0f, 0.0f, entity.age + tickDelta, 0.0f, entity.getPitch());

		if (entity.isStealthEffective()) {
			// Im Tarnkappenmodus wird die Maschine halbdurchsichtig gezeichnet.
			VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(texture));
			model.render(matrices, consumer, light, OverlayTexture.DEFAULT_UV, 0x60FFFFFF);
		} else {
			VertexConsumer consumer = vertexConsumers.getBuffer(model.getLayer(texture));
			model.render(matrices, consumer, light, OverlayTexture.DEFAULT_UV, 0xFFFFFFFF);
		}

		matrices.pop();
		super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
	}

	@Override
	public Identifier getTexture(F47Entity entity) {
		if (override != null) {
			return override;
		}
		if (entity.isStealthOn()) {
			return STEALTH;
		}
		return entity.getTeam() == Team.BLUE ? BLUE : RED;
	}

}
