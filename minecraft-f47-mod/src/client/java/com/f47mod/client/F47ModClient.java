package com.f47mod.client;

import com.f47mod.client.gui.JoystickScreen;
import com.f47mod.client.hud.JetHud;
import com.f47mod.client.render.DroneRenderer;
import com.f47mod.client.render.F47Model;
import com.f47mod.client.render.F47Renderer;
import com.f47mod.client.render.ProjectileRenderers;
import com.f47mod.client.render.RadarBlockEntityRenderer;
import com.f47mod.client.render.SoldierModel;
import com.f47mod.client.render.SoldierRenderer;
import com.f47mod.entity.vehicle.F47Entity;
import com.f47mod.net.ModNetworking;
import com.f47mod.registry.ModBlockEntities;
import com.f47mod.registry.ModBlocks;
import com.f47mod.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.render.RenderLayer;

/**
 * Client-Teil des Mods: Modelle, Renderer, Tastenbelegung, Cockpitanzeige und
 * die Verarbeitung der Radarpakete.
 */
public class F47ModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		registerModels();
		registerRenderers();
		registerTransparency();

		KeyBinds.register();

		// Steuerung: einmal pro Client-Tick auswerten und an den Server melden.
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null) {
				ClientRadarState.clear();
				return;
			}
			JetController.tick(client);

			// Zuordnungs-Bildschirm fuer Joystick und Schubhebel.
			while (KeyBinds.joystickSetup.wasPressed()) {
				client.setScreen(new JoystickScreen(client.currentScreen));
			}
		});

		// Cockpitanzeige.
		HudRenderCallback.EVENT.register((context, tickCounter) ->
				JetHud.render(context, tickCounter.getTickDelta(false)));

		// Radarbild vom Server entgegennehmen.
		ClientPlayNetworking.registerGlobalReceiver(ModNetworking.RadarUpdatePayload.ID,
				(payload, context) -> context.client().execute(() ->
						ClientRadarState.update(payload.contacts(),
								com.f47mod.util.Team.byOrdinal(payload.viewerTeam()))));
	}

	private void registerModels() {
		EntityModelLayerRegistry.registerModelLayer(F47Model.LAYER, F47Model::getTexturedModelData);
		EntityModelLayerRegistry.registerModelLayer(SoldierModel.LAYER, SoldierModel::getTexturedModelData);
		EntityModelLayerRegistry.registerModelLayer(DroneRenderer.DroneModel.LAYER,
				DroneRenderer.DroneModel::getTexturedModelData);
		EntityModelLayerRegistry.registerModelLayer(ProjectileRenderers.MISSILE_LAYER,
				ProjectileRenderers::missileModelData);
		EntityModelLayerRegistry.registerModelLayer(RadarBlockEntityRenderer.LAYER,
				RadarBlockEntityRenderer::getTexturedModelData);
	}

	private void registerRenderers() {
		EntityRendererRegistry.register(ModEntities.F47, F47Renderer::new);
		EntityRendererRegistry.register(ModEntities.AUTONOMOUS_F47, F47Renderer::new);
		EntityRendererRegistry.register(ModEntities.SOLDIER, SoldierRenderer::new);
		EntityRendererRegistry.register(ModEntities.COMBAT_DRONE, DroneRenderer::new);
		EntityRendererRegistry.register(ModEntities.MISSILE, ProjectileRenderers.MissileRenderer::new);
		EntityRendererRegistry.register(ModEntities.BOMB, ProjectileRenderers.BombRenderer::new);
		EntityRendererRegistry.register(ModEntities.BULLET, ProjectileRenderers.bullet());
		EntityRendererRegistry.register(ModEntities.PLASMA_BOLT, ProjectileRenderers.plasma());

		BlockEntityRendererRegistry.register(ModBlockEntities.RADAR, RadarBlockEntityRenderer::new);
	}

	/** Bloecke, die durchsichtige Stellen haben. */
	private void registerTransparency() {
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.RADAR, RenderLayer.getCutout());
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.IRON_DOME, RenderLayer.getCutout());
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.HANGAR_DOOR, RenderLayer.getCutout());
	}

	/** Beim Einsteigen den Schubhebel auf den Stand des Jets bringen. */
	public static void onMount(F47Entity jet) {
		JetController.onBoard(jet);
	}
}
