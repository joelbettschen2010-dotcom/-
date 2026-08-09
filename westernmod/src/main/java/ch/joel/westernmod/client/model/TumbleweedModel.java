package ch.joel.westernmod.client.model;

import ch.joel.westernmod.entity.TumbleweedEntity;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;

/**
 * Steppenläufer: drei gekreuzte Flächen mit Gestrüpp-Textur. Der durchsichtige Rand macht aus
 * den Rechtecken ein zerzaustes Knäuel, das beim Rollen um die Querachse kippt.
 */
public class TumbleweedModel extends SinglePartEntityModel<TumbleweedEntity> {
	private final ModelPart root;
	private final ModelPart bush;

	public TumbleweedModel(ModelPart root) {
		this.root = root;
		this.bush = root.getChild("bush");
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData modelData = new ModelData();
		ModelPartData root = modelData.getRoot();

		ModelPartData bush = root.addChild("bush", ModelPartBuilder.create(),
				ModelTransform.pivot(0.0f, 18.0f, 0.0f));
		bush.addChild("plane_x",
				ModelPartBuilder.create().uv(0, 0).cuboid(-6.0f, -6.0f, -0.5f, 12.0f, 12.0f, 1.0f),
				ModelTransform.NONE);
		bush.addChild("plane_z",
				ModelPartBuilder.create().uv(28, 0).cuboid(-0.5f, -6.0f, -6.0f, 1.0f, 12.0f, 12.0f),
				ModelTransform.NONE);
		bush.addChild("plane_y",
				ModelPartBuilder.create().uv(0, 26).cuboid(-6.0f, -0.5f, -6.0f, 12.0f, 1.0f, 12.0f),
				ModelTransform.NONE);

		return TexturedModelData.of(modelData, 64, 64);
	}

	@Override
	public ModelPart getPart() {
		return this.root;
	}

	@Override
	public void setAngles(TumbleweedEntity entity, float limbAngle, float limbDistance, float animationProgress,
			float headYaw, float headPitch) {
		this.bush.pitch = limbAngle * 0.55f;
		this.bush.roll = limbAngle * 0.12f;
	}
}
