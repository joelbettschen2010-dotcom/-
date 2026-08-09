package com.f47mod.client.render;

import com.f47mod.F47Mod;
import com.f47mod.entity.mob.SoldierEntity;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayer;

/**
 * Soldatenmodell. Nutzt die bewaehrte humanoide Grundform von Minecraft, damit
 * Laufanimation, Armhaltung und gehaltene Waffen automatisch stimmen.
 */
public class SoldierModel extends BipedEntityModel<SoldierEntity> {
	public static final EntityModelLayer LAYER = new EntityModelLayer(F47Mod.id("soldier"), "main");

	public SoldierModel(ModelPart root) {
		super(root);
	}

	public static TexturedModelData getTexturedModelData() {
		// Leichte Verdickung fuer die Schutzweste.
		return TexturedModelData.of(BipedEntityModel.getModelData(new Dilation(0.15f), 0.0f), 64, 64);
	}
}
