package com.f47mod.client.render;

import com.f47mod.F47Mod;
import com.f47mod.entity.vehicle.F47Entity;
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

/**
 * Zeichnet die F-47 in der Welt - samt Querlage, Tarnkappen-Transparenz und
 * dem Nachbrennerleuchten am Heck.
 */
public class F47Renderer extends EntityRenderer<F47Entity> {
	private static final Identifier TEXTURE = F47Mod.id("textures/entity/f47.png");
	private static final Identifier STEALTH_TEXTURE = F47Mod.id("textures/entity/f47_stealth.png");

	private final F47Model model;

	public F47Renderer(EntityRendererFactory.Context context) {
		super(context);
		this.model = new F47Model(context.getPart(F47Model.LAYER));
		this.shadowRadius = 1.6f;
	}

	@Override
	public void render(F47Entity entity, float yaw, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light) {
		matrices.push();

		// Minecraft-Modelle zeigen kopfueber nach hinten - erst umdrehen.
		matrices.translate(0.0, 0.6, 0.0);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(
				MathHelper.lerp(tickDelta, entity.prevPitch, entity.getPitch())));
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(entity.getRoll()));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180.0f));
		matrices.scale(0.1f, 0.1f, 0.1f);

		Identifier texture = entity.isStealthOn() ? STEALTH_TEXTURE : TEXTURE;
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
		return entity.isStealthOn() ? STEALTH_TEXTURE : TEXTURE;
	}

}
