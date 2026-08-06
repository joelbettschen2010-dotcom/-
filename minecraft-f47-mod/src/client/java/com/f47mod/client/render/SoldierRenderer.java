package com.f47mod.client.render;

import com.f47mod.F47Mod;
import com.f47mod.entity.mob.SoldierEntity;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.util.Identifier;

/** Zeichnet die Bodentruppen - je Rolle mit eigener Uniform. */
public class SoldierRenderer extends BipedEntityRenderer<SoldierEntity, SoldierModel> {
	private static final Identifier RIFLEMAN = F47Mod.id("textures/entity/soldier_rifleman.png");
	private static final Identifier HEAVY = F47Mod.id("textures/entity/soldier_heavy.png");
	private static final Identifier MEDIC = F47Mod.id("textures/entity/soldier_medic.png");
	private static final Identifier ENGINEER = F47Mod.id("textures/entity/soldier_engineer.png");
	private static final Identifier PILOT = F47Mod.id("textures/entity/soldier_pilot.png");

	public SoldierRenderer(EntityRendererFactory.Context context) {
		super(context, new SoldierModel(context.getPart(SoldierModel.LAYER)), 0.5f);
		addFeature(new HeldItemFeatureRenderer<>(this, context.getHeldItemRenderer()));
	}

	@Override
	public Identifier getTexture(SoldierEntity entity) {
		return switch (entity.getRole()) {
			case RIFLEMAN -> RIFLEMAN;
			case HEAVY -> HEAVY;
			case MEDIC -> MEDIC;
			case ENGINEER -> ENGINEER;
			case PILOT -> PILOT;
		};
	}
}
