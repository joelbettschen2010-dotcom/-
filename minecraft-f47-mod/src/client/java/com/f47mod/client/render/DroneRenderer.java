package com.f47mod.client.render;

import com.f47mod.F47Mod;
import com.f47mod.entity.mob.EnemyDroneEntity;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/** Feindliche Drohne: flacher Rumpf mit vier Rotorarmen. */
public class DroneRenderer extends MobEntityRenderer<EnemyDroneEntity, DroneRenderer.DroneModel> {
	private static final Identifier TEXTURE = F47Mod.id("textures/entity/enemy_drone.png");

	public DroneRenderer(EntityRendererFactory.Context context) {
		super(context, new DroneModel(context.getPart(DroneModel.LAYER)), 0.6f);
	}

	@Override
	public Identifier getTexture(EnemyDroneEntity entity) {
		return TEXTURE;
	}

	public static class DroneModel extends EntityModel<EnemyDroneEntity> {
		public static final EntityModelLayer LAYER = new EntityModelLayer(F47Mod.id("enemy_drone"), "main");

		private final ModelPart root;
		private final ModelPart rotorA;
		private final ModelPart rotorB;

		public DroneModel(ModelPart root) {
			super(RenderLayer::getEntityCutoutNoCull);
			this.root = root;
			this.rotorA = root.getChild("rotor_a");
			this.rotorB = root.getChild("rotor_b");
		}

		public static TexturedModelData getTexturedModelData() {
			ModelData data = new ModelData();
			var root = data.getRoot();
			root.addChild("body", ModelPartBuilder.create().uv(0, 0)
					.cuboid(-5.0f, -2.0f, -5.0f, 10.0f, 4.0f, 10.0f), ModelTransform.NONE);
			root.addChild("sensor", ModelPartBuilder.create().uv(0, 16)
					.cuboid(-2.0f, 2.0f, -2.0f, 4.0f, 3.0f, 4.0f), ModelTransform.NONE);
			root.addChild("rotor_a", ModelPartBuilder.create().uv(0, 24)
					.cuboid(-11.0f, -3.0f, -1.0f, 22.0f, 1.0f, 2.0f, new Dilation(0.0f)),
					ModelTransform.pivot(0.0f, 0.0f, 0.0f));
			root.addChild("rotor_b", ModelPartBuilder.create().uv(0, 28)
					.cuboid(-1.0f, -3.0f, -11.0f, 2.0f, 1.0f, 22.0f, new Dilation(0.0f)),
					ModelTransform.pivot(0.0f, 0.0f, 0.0f));
			return TexturedModelData.of(data, 64, 64);
		}

		@Override
		public void setAngles(EnemyDroneEntity entity, float limbAngle, float limbDistance,
				float animationProgress, float headYaw, float headPitch) {
			// Rotoren drehen sichtbar durch.
			rotorA.yaw = animationProgress * 1.4f;
			rotorB.yaw = animationProgress * 1.4f;
		}

		@Override
		public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
			root.render(matrices, vertices, light, overlay, color);
		}
	}
}
