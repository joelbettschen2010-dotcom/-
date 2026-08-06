package com.f47mod.client.render;

import com.f47mod.F47Mod;
import com.f47mod.entity.projectile.BombEntity;
import com.f47mod.entity.projectile.BulletEntity;
import com.f47mod.entity.projectile.MissileEntity;
import com.f47mod.entity.projectile.PlasmaBoltEntity;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/** Darstellung der Geschosse: Raketen, Bomben, Kugeln und Plasmaladungen. */
public final class ProjectileRenderers {
	private ProjectileRenderers() {
	}

	public static final EntityModelLayer MISSILE_LAYER = new EntityModelLayer(F47Mod.id("missile"), "main");

	/** Schlanker Koerper mit Leitwerk - fuer alle Raketenarten. */
	public static TexturedModelData missileModelData() {
		ModelData data = new ModelData();
		var root = data.getRoot();
		root.addChild("body", ModelPartBuilder.create().uv(0, 0)
				.cuboid(-1.0f, -1.0f, -6.0f, 2.0f, 2.0f, 12.0f), ModelTransform.NONE);
		root.addChild("fin_x", ModelPartBuilder.create().uv(0, 14)
				.cuboid(-3.0f, -0.5f, 3.0f, 6.0f, 1.0f, 3.0f), ModelTransform.NONE);
		root.addChild("fin_y", ModelPartBuilder.create().uv(0, 18)
				.cuboid(-0.5f, -3.0f, 3.0f, 1.0f, 6.0f, 3.0f), ModelTransform.NONE);
		return TexturedModelData.of(data, 32, 32);
	}

	/** Gemeinsame Basis: richtet das Modell entlang der Flugbahn aus. */
	private abstract static class OrientedRenderer<T extends Entity> extends EntityRenderer<T> {
		protected final ModelPart model;
		private final float scale;

		protected OrientedRenderer(EntityRendererFactory.Context context, ModelPart model, float scale) {
			super(context);
			this.model = model;
			this.scale = scale;
			this.shadowRadius = 0.0f;
		}

		@Override
		public void render(T entity, float yaw, float tickDelta, MatrixStack matrices,
				VertexConsumerProvider vertexConsumers, int light) {
			matrices.push();
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(
					MathHelper.lerp(tickDelta, entity.prevYaw, entity.getYaw()) - 90.0f));
			matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(
					MathHelper.lerp(tickDelta, entity.prevPitch, entity.getPitch())));
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90.0f));
			matrices.scale(scale, scale, scale);

			VertexConsumer consumer = vertexConsumers.getBuffer(
					RenderLayer.getEntityCutoutNoCull(getTexture(entity)));
			model.render(matrices, consumer, light, OverlayTexture.DEFAULT_UV);
			matrices.pop();
			super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
		}
	}

	public static class MissileRenderer extends OrientedRenderer<MissileEntity> {
		private static final Identifier TEXTURE = F47Mod.id("textures/entity/missile.png");
		private static final Identifier ENEMY_TEXTURE = F47Mod.id("textures/entity/missile_enemy.png");

		public MissileRenderer(EntityRendererFactory.Context context) {
			super(context, context.getPart(MISSILE_LAYER), 0.1f);
		}

		@Override
		public Identifier getTexture(MissileEntity entity) {
			return entity.isHostile() ? ENEMY_TEXTURE : TEXTURE;
		}
	}

	public static class BombRenderer extends OrientedRenderer<BombEntity> {
		private static final Identifier TEXTURE = F47Mod.id("textures/entity/bomb.png");

		public BombRenderer(EntityRendererFactory.Context context) {
			super(context, context.getPart(MISSILE_LAYER), 0.13f);
		}

		@Override
		public Identifier getTexture(BombEntity entity) {
			return TEXTURE;
		}
	}

	/**
	 * Kugeln und Plasmaladungen sind zu schnell fuer ein Modell - sie werden
	 * ueber Partikel dargestellt und brauchen keine eigene Geometrie.
	 */
	public static class InvisibleRenderer<T extends Entity> extends EntityRenderer<T> {
		private static final Identifier TEXTURE = F47Mod.id("textures/entity/missile.png");

		public InvisibleRenderer(EntityRendererFactory.Context context) {
			super(context);
			this.shadowRadius = 0.0f;
		}

		@Override
		public Identifier getTexture(T entity) {
			return TEXTURE;
		}

		@Override
		public void render(T entity, float yaw, float tickDelta, MatrixStack matrices,
				VertexConsumerProvider vertexConsumers, int light) {
			// Bewusst leer - die Spur entsteht aus Partikeln.
		}
	}

	public static EntityRendererFactory<BulletEntity> bullet() {
		return InvisibleRenderer::new;
	}

	public static EntityRendererFactory<PlasmaBoltEntity> plasma() {
		return InvisibleRenderer::new;
	}
}
