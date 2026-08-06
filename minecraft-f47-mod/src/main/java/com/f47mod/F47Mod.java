package com.f47mod;

import com.f47mod.net.ModNetworking;
import com.f47mod.registry.ModBlockEntities;
import com.f47mod.registry.ModBlocks;
import com.f47mod.registry.ModEntities;
import com.f47mod.registry.ModItemGroups;
import com.f47mod.registry.ModItems;
import com.f47mod.world.ModWorldGen;
import com.f47mod.world.RadarSyncHandler;
import com.f47mod.world.RaidScheduler;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Einstiegspunkt des F-47-Mods.
 *
 * <p>Der Mod bringt eine komplette kleine Luftwaffenbasis nach Minecraft:
 * fliegbare F-47 mit Waffen und Tarnkappenmodus, autonome Jets mit
 * Bot-Piloten, Start- und Landebahnen samt Hangar, Luftraumradar, ein
 * Iron-Dome-Abwehrsystem, Energiewaffen und Bodenpersonal.
 */
public class F47Mod implements ModInitializer {
	public static final String MOD_ID = "f47";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		// Reihenfolge ist wichtig: Bloecke vor Block-Entities, Items vor der Gruppe.
		ModBlocks.init();
		ModItems.init();
		ModBlockEntities.init();
		ModEntities.init();
		ModEntities.registerAttributes();
		ModItemGroups.register();

		ModNetworking.registerPayloads();
		ModNetworking.registerServerReceivers();

		ModWorldGen.register();
		RadarSyncHandler.register();
		RaidScheduler.register();

		F47Mod.LOGGER.info("[F-47] Luftwaffenstuetzpunkt einsatzbereit");
	}
}
