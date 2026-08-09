package ch.joel.westernmod.client.render;

import ch.joel.westernmod.WesternMod;
import ch.joel.westernmod.client.ModModelLayers;
import ch.joel.westernmod.client.model.WagonModel;
import ch.joel.westernmod.entity.WagonEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.util.Identifier;

public class WagonRenderer extends MobEntityRenderer<WagonEntity, WagonModel> {
	private static final Identifier TEXTURE = WesternMod.id("textures/entity/wagon.png");

	public WagonRenderer(EntityRendererFactory.Context context) {
		super(context, new WagonModel(context.getPart(ModModelLayers.WAGON)), 0.9f);
	}

	@Override
	public Identifier getTexture(WagonEntity entity) {
		return TEXTURE;
	}
}
