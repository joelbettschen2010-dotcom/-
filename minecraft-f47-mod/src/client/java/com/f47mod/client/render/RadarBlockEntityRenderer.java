package com.f47mod.client.render;

import com.f47mod.F47Mod;
import com.f47mod.block.entity.RadarBlockEntity;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/** Laesst die Radarschuessel sichtbar rotieren, solange die Station laeuft. */
public class RadarBlockEntityRenderer implements BlockEntityRenderer<RadarBlockEntity> {
	public static final EntityModelLayer LAYER = new EntityModelLayer(F47Mod.id("radar_dish"), "main");
	private static final Identifier TEXTURE = F47Mod.id("textures/entity/radar_dish.png");

	private final ModelPart dish;

	public RadarBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
		this.dish = context.getLayerModelPart(LAYER);
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData data = new ModelData();
		var root = data.getRoot();
		// Mast
		root.addChild("mast", ModelPartBuilder.create().uv(0, 0)
				.cuboid(-1.0f, -8.0f, -1.0f, 2.0f, 8.0f, 2.0f), ModelTransform.NONE);
		// Schuessel, leicht nach oben geneigt
		root.addChild("dish", ModelPartBuilder.create().uv(0, 12)
				.cuboid(-7.0f, -1.0f, -0.5f, 14.0f, 9.0f, 1.0f),
				ModelTransform.of(0.0f, -8.0f, 0.0f, -0.5f, 0.0f, 0.0f));
		return TexturedModelData.of(data, 32, 32);
	}

	@Override
	public void render(RadarBlockEntity entity, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light, int overlay) {
		matrices.push();
		matrices.translate(0.5, 1.5, 0.5);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(entity.getSweepAngle(tickDelta)));
		matrices.scale(0.0625f, 0.0625f, 0.0625f);
		matrices.scale(1.0f, -1.0f, 1.0f);

		VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
		dish.render(matrices, consumer, light, OverlayTexture.DEFAULT_UV);
		matrices.pop();
	}
}
