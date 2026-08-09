package ch.joel.westernmod.client;

import ch.joel.westernmod.ModBlocks;
import ch.joel.westernmod.ModEntities;
import ch.joel.westernmod.WesternMod;
import ch.joel.westernmod.client.model.GunslingerModel;
import ch.joel.westernmod.client.model.TumbleweedModel;
import ch.joel.westernmod.client.model.WagonModel;
import ch.joel.westernmod.client.render.GunslingerRenderer;
import ch.joel.westernmod.client.render.TumbleweedRenderer;
import ch.joel.westernmod.client.render.WagonRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;

@Environment(EnvType.CLIENT)
public class WesternModClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		EntityModelLayerRegistry.registerModelLayer(ModModelLayers.GUNSLINGER,
				GunslingerModel::getTexturedModelData);
		EntityModelLayerRegistry.registerModelLayer(ModModelLayers.WAGON, WagonModel::getTexturedModelData);
		EntityModelLayerRegistry.registerModelLayer(ModModelLayers.TUMBLEWEED,
				TumbleweedModel::getTexturedModelData);

		EntityRendererRegistry.register(ModEntities.COWBOY,
				context -> new GunslingerRenderer<>(context, WesternMod.id("textures/entity/cowboy.png")));
		EntityRendererRegistry.register(ModEntities.SHERIFF,
				context -> new GunslingerRenderer<>(context, WesternMod.id("textures/entity/sheriff.png")));
		EntityRendererRegistry.register(ModEntities.BANDIT,
				context -> new GunslingerRenderer<>(context, WesternMod.id("textures/entity/bandit.png")));
		EntityRendererRegistry.register(ModEntities.BANDIT_LEADER,
				context -> new GunslingerRenderer<>(context, WesternMod.id("textures/entity/bandit_leader.png")));

		EntityRendererRegistry.register(ModEntities.WAGON, WagonRenderer::new);
		EntityRendererRegistry.register(ModEntities.TUMBLEWEED, TumbleweedRenderer::new);

		EntityRendererRegistry.register(ModEntities.BULLET, FlyingItemEntityRenderer::new);
		EntityRendererRegistry.register(ModEntities.DYNAMITE, FlyingItemEntityRenderer::new);

		BlockRenderLayerMap.INSTANCE.putBlocks(RenderLayer.getCutout(),
				ModBlocks.HITCHING_POST, ModBlocks.SALOON_DOOR, ModBlocks.WANTED_POSTER);
	}
}
