package com.f47mod.client.render;

import com.f47mod.F47Mod;
import com.f47mod.entity.vehicle.F47Entity;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;

/**
 * Modell der F-47: Ein Tarnkappenjaeger in Deltaform mit angewinkelten
 * Leitwerken. Aufgebaut aus Quadern im Code - so braucht der Mod keine
 * externen Modelldateien.
 */
public class F47Model extends EntityModel<F47Entity> {
	public static final EntityModelLayer LAYER =
			new EntityModelLayer(F47Mod.id("f47"), "main");

	private final ModelPart root;
	private final ModelPart canopy;
	private final ModelPart leftElevon;
	private final ModelPart rightElevon;

	public F47Model(ModelPart root) {
		super(RenderLayer::getEntityCutoutNoCull);
		this.root = root;
		this.canopy = root.getChild("canopy");
		this.leftElevon = root.getChild("left_elevon");
		this.rightElevon = root.getChild("right_elevon");
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData data = new ModelData();
		var root = data.getRoot();

		// Rumpf: vorne schlank, hinten breiter fuer die Triebwerke.
		root.addChild("fuselage",
				ModelPartBuilder.create().uv(0, 0)
						.cuboid(-3.0f, -3.0f, -20.0f, 6.0f, 5.0f, 34.0f),
				ModelTransform.pivot(0.0f, 0.0f, 0.0f));

		// Bugsektion mit Radarnase.
		root.addChild("nose",
				ModelPartBuilder.create().uv(0, 40)
						.cuboid(-2.0f, -2.0f, -30.0f, 4.0f, 3.5f, 11.0f),
				ModelTransform.pivot(0.0f, 0.0f, 0.0f));

		// Deltafluegel, leicht nach hinten gepfeilt.
		root.addChild("left_wing",
				ModelPartBuilder.create().uv(60, 0)
						.cuboid(0.0f, -0.5f, -6.0f, 22.0f, 1.5f, 20.0f),
				ModelTransform.of(3.0f, 0.0f, 2.0f, 0.0f, 0.0f, -0.06f));
		root.addChild("right_wing",
				ModelPartBuilder.create().uv(60, 0).mirrored()
						.cuboid(-22.0f, -0.5f, -6.0f, 22.0f, 1.5f, 20.0f),
				ModelTransform.of(-3.0f, 0.0f, 2.0f, 0.0f, 0.0f, 0.06f));

		// Bewegliche Hoehen-/Querruder an den Fluegelenden.
		data.getRoot().addChild("left_elevon",
				ModelPartBuilder.create().uv(60, 42)
						.cuboid(0.0f, -0.4f, 0.0f, 14.0f, 1.0f, 5.0f),
				ModelTransform.pivot(7.0f, 0.0f, 16.0f));
		data.getRoot().addChild("right_elevon",
				ModelPartBuilder.create().uv(60, 42).mirrored()
						.cuboid(-14.0f, -0.4f, 0.0f, 14.0f, 1.0f, 5.0f),
				ModelTransform.pivot(-7.0f, 0.0f, 16.0f));

		// Nach aussen geneigte Seitenleitwerke - typisch fuer Stealth-Bauweise.
		root.addChild("left_tail",
				ModelPartBuilder.create().uv(0, 56)
						.cuboid(0.0f, -8.0f, 0.0f, 1.0f, 8.0f, 9.0f),
				ModelTransform.of(3.5f, -1.0f, 4.0f, 0.0f, 0.0f, 0.42f));
		root.addChild("right_tail",
				ModelPartBuilder.create().uv(0, 56).mirrored()
						.cuboid(-1.0f, -8.0f, 0.0f, 1.0f, 8.0f, 9.0f),
				ModelTransform.of(-3.5f, -1.0f, 4.0f, 0.0f, 0.0f, -0.42f));

		// Zwei Triebwerksduesen am Heck.
		root.addChild("left_engine",
				ModelPartBuilder.create().uv(30, 40)
						.cuboid(0.2f, -2.2f, 10.0f, 3.0f, 3.0f, 6.0f, new Dilation(0.1f)),
				ModelTransform.NONE);
		root.addChild("right_engine",
				ModelPartBuilder.create().uv(30, 40).mirrored()
						.cuboid(-3.2f, -2.2f, 10.0f, 3.0f, 3.0f, 6.0f, new Dilation(0.1f)),
				ModelTransform.NONE);

		// Cockpithaube.
		data.getRoot().addChild("canopy",
				ModelPartBuilder.create().uv(30, 52)
						.cuboid(-2.2f, -5.2f, -14.0f, 4.4f, 3.0f, 11.0f),
				ModelTransform.pivot(0.0f, 0.0f, 0.0f));

		// Waffenstationen unter den Fluegeln.
		root.addChild("left_pylon",
				ModelPartBuilder.create().uv(88, 42)
						.cuboid(9.0f, 0.8f, -2.0f, 2.0f, 2.0f, 10.0f),
				ModelTransform.NONE);
		root.addChild("right_pylon",
				ModelPartBuilder.create().uv(88, 42).mirrored()
						.cuboid(-11.0f, 0.8f, -2.0f, 2.0f, 2.0f, 10.0f),
				ModelTransform.NONE);

		return TexturedModelData.of(data, 128, 128);
	}

	@Override
	public void setAngles(F47Entity entity, float limbAngle, float limbDistance, float animationProgress,
			float headYaw, float headPitch) {
		// Die Ruder schlagen sichtbar aus, wenn der Jet gesteuert wird.
		float elevon = -headPitch * 0.012f;
		float aileron = entity.getRoll() * 0.006f;
		leftElevon.pitch = elevon + aileron;
		rightElevon.pitch = elevon - aileron;
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
		// Der Wurzelteil zeichnet alle Unterteile gleich mit.
		root.render(matrices, vertices, light, overlay, color);
	}
}
