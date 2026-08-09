package ch.joel.westernmod.client.render;

import ch.joel.westernmod.WesternMod;
import ch.joel.westernmod.client.ModModelLayers;
import ch.joel.westernmod.client.model.TumbleweedModel;
import ch.joel.westernmod.entity.TumbleweedEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.util.Identifier;

public class TumbleweedRenderer extends MobEntityRenderer<TumbleweedEntity, TumbleweedModel> {
	private static final Identifier TEXTURE = WesternMod.id("textures/entity/tumbleweed.png");

	public TumbleweedRenderer(EntityRendererFactory.Context context) {
		super(context, new TumbleweedModel(context.getPart(ModModelLayers.TUMBLEWEED)), 0.4f);
	}

	@Override
	public Identifier getTexture(TumbleweedEntity entity) {
		return TEXTURE;
	}
}
