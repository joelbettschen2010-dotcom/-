package com.f47mod.client.render;

import com.f47mod.F47Mod;
import com.f47mod.entity.mob.SoldierEntity;
import com.f47mod.util.Team;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.util.Identifier;

/** Zeichnet die Bodentruppen - je Rolle mit eigener Uniform. */
public class SoldierRenderer extends BipedEntityRenderer<SoldierEntity, SoldierModel> {
	// Jede Rolle hat eine eigene Uniform, jede Partei eine eigene Farbe.
	private static final java.util.Map<String, Identifier> TEXTURES = new java.util.HashMap<>();

	public SoldierRenderer(EntityRendererFactory.Context context) {
		super(context, new SoldierModel(context.getPart(SoldierModel.LAYER)), 0.5f);
		addFeature(new HeldItemFeatureRenderer<>(this, context.getHeldItemRenderer()));
	}

	@Override
	public Identifier getTexture(SoldierEntity entity) {
		String role = entity.getRole().name().toLowerCase(java.util.Locale.ROOT);
		String team = entity.getTeam() == Team.BLUE ? "blue" : "red";
		return TEXTURES.computeIfAbsent(role + "_" + team,
				key -> F47Mod.id("textures/entity/soldier_" + key + ".png"));
	}
}
