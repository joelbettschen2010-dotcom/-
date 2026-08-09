package ch.joel.westernmod.client.render;

import ch.joel.westernmod.client.ModModelLayers;
import ch.joel.westernmod.client.model.GunslingerModel;
import ch.joel.westernmod.entity.GunslingerEntity;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;

/** Rendert Cowboys, Sheriffs und Banditen — gleiches Modell, unterschiedliche Textur. */
public class GunslingerRenderer<T extends GunslingerEntity> extends BipedEntityRenderer<T, GunslingerModel<T>> {
	private final Identifier texture;

	public GunslingerRenderer(EntityRendererFactory.Context context, Identifier texture) {
		super(context, new GunslingerModel<>(context.getPart(ModModelLayers.GUNSLINGER)), 0.5f);
		this.texture = texture;
	}

	@Override
	public Identifier getTexture(T entity) {
		return this.texture;
	}
}
