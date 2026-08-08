package com.f47mod.client.render;

import com.f47mod.F47Mod;
import com.f47mod.entity.vehicle.ArmoredVehicleEntity;
import com.f47mod.util.Team;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/** Zeichnet den Schuetzenpanzer: Wanne, Turm und Ketten. */
public class ArmoredVehicleRenderer extends EntityRenderer<ArmoredVehicleEntity> {
	public static final EntityModelLayer LAYER =
			new EntityModelLayer(F47Mod.id("armored_vehicle"), "main");

	private static final Identifier BLUE = F47Mod.id("textures/entity/vehicle_blue.png");
	private static final Identifier RED = F47Mod.id("textures/entity/vehicle_red.png");

	private final ModelPart root;

	public ArmoredVehicleRenderer(EntityRendererFactory.Context context) {
		super(context);
		this.root = context.getPart(LAYER);
		this.shadowRadius = 1.4f;
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData data = new ModelData();
		var root = data.getRoot();
		// Wanne
		root.addChild("hull", ModelPartBuilder.create().uv(0, 0)
				.cuboid(-18.0f, -14.0f, -26.0f, 36.0f, 12.0f, 52.0f), ModelTransform.NONE);
		// Turm
		root.addChild("turret", ModelPartBuilder.create().uv(0, 64)
				.cuboid(-10.0f, -24.0f, -10.0f, 20.0f, 10.0f, 20.0f), ModelTransform.NONE);
		// Rohr
		root.addChild("gun", ModelPartBuilder.create().uv(80, 64)
				.cuboid(-2.0f, -21.0f, -30.0f, 4.0f, 4.0f, 22.0f), ModelTransform.NONE);
		// Ketten
		root.addChild("left_track", ModelPartBuilder.create().uv(0, 96)
				.cuboid(-20.0f, -8.0f, -26.0f, 5.0f, 8.0f, 52.0f, new Dilation(0.1f)), ModelTransform.NONE);
		root.addChild("right_track", ModelPartBuilder.create().uv(0, 96).mirrored()
				.cuboid(15.0f, -8.0f, -26.0f, 5.0f, 8.0f, 52.0f, new Dilation(0.1f)), ModelTransform.NONE);
		return TexturedModelData.of(data, 128, 128);
	}

	@Override
	public void render(ArmoredVehicleEntity entity, float yaw, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light) {
		matrices.push();
		matrices.translate(0.0, 1.4, 0.0);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180.0f));
		matrices.scale(1.0f, 1.0f, 1.0f);

		VertexConsumer consumer = vertexConsumers.getBuffer(
				RenderLayer.getEntityCutoutNoCull(getTexture(entity)));
		root.render(matrices, consumer, light, OverlayTexture.DEFAULT_UV);

		matrices.pop();
		super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
	}

	@Override
	public Identifier getTexture(ArmoredVehicleEntity entity) {
		return entity.getTeam() == Team.BLUE ? BLUE : RED;
	}
}
