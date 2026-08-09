package ch.joel.westernmod.client.model;

import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelPartNames;
import net.minecraft.entity.LivingEntity;

/**
 * Menschliches Grundmodell plus Stetson: eine breite Krempe und ein hoher Hutkopf, beide als
 * Kinder des Kopfteils — so kippt der Hut mit, wenn die Figur sich umsieht.
 *
 * <p>Die freien Texturzeilen 32–47 des 64×64-Blatts nehmen die beiden Hut-Quader auf; dort
 * liegt bei einem normalen Skin nur die zweite Ebene, die dieses Modell nicht benutzt.
 */
public class GunslingerModel<T extends LivingEntity> extends BipedEntityModel<T> {

	public GunslingerModel(ModelPart root) {
		super(root);
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData modelData = BipedEntityModel.getModelData(Dilation.NONE, 0.0f);
		ModelPartData head = modelData.getRoot().getChild(EntityModelPartNames.HEAD);

		head.addChild("hat_brim",
				ModelPartBuilder.create().uv(0, 32).cuboid(-5.0f, -8.0f, -5.0f, 10.0f, 1.0f, 10.0f),
				ModelTransform.NONE);
		head.addChild("hat_crown",
				ModelPartBuilder.create().uv(40, 32).cuboid(-3.0f, -12.0f, -3.0f, 6.0f, 4.0f, 6.0f),
				ModelTransform.NONE);

		return TexturedModelData.of(modelData, 64, 64);
	}
}
