package com.f47mod.world;

import com.f47mod.F47Mod;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.PlacedFeature;

/**
 * Titanerz in die Weltgenerierung einhaengen. Ohne eigenes Erz muesste man
 * jedes Bauteil im Kreativmodus holen - so laesst sich die Basis auch im
 * Ueberlebensmodus erspielen.
 */
public final class ModWorldGen {
	private static final RegistryKey<PlacedFeature> TITANIUM_ORE =
			RegistryKey.of(RegistryKeys.PLACED_FEATURE, F47Mod.id("titanium_ore_placed"));

	private ModWorldGen() {
	}

	public static void register() {
		BiomeModifications.addFeature(
				BiomeSelectors.foundInOverworld(),
				GenerationStep.Feature.UNDERGROUND_ORES,
				TITANIUM_ORE);
	}
}
