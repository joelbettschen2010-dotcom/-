package ch.joel.westernmod.client;

import ch.joel.westernmod.WesternMod;
import net.minecraft.client.render.entity.model.EntityModelLayer;

public final class ModModelLayers {
	public static final EntityModelLayer GUNSLINGER = new EntityModelLayer(WesternMod.id("gunslinger"), "main");
	public static final EntityModelLayer WAGON = new EntityModelLayer(WesternMod.id("wagon"), "main");
	public static final EntityModelLayer TUMBLEWEED = new EntityModelLayer(WesternMod.id("tumbleweed"), "main");

	private ModModelLayers() {
	}
}
