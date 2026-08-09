package ch.joel.westernmod;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.world.biome.BiomeKeys;

/** Natürliches Vorkommen: Banditen lauern im trockenen Hinterland, Steppenläufer rollen dort herum. */
public final class ModWorld {

	private ModWorld() {
	}

	public static void register() {
		BiomeModifications.addSpawn(
				BiomeSelectors.includeByKey(
						BiomeKeys.DESERT,
						BiomeKeys.BADLANDS,
						BiomeKeys.ERODED_BADLANDS,
						BiomeKeys.WOODED_BADLANDS,
						BiomeKeys.SAVANNA,
						BiomeKeys.SAVANNA_PLATEAU,
						BiomeKeys.WINDSWEPT_SAVANNA),
				SpawnGroup.MONSTER, ModEntities.BANDIT, 14, 1, 3);

		BiomeModifications.addSpawn(
				BiomeSelectors.includeByKey(
						BiomeKeys.DESERT,
						BiomeKeys.BADLANDS,
						BiomeKeys.ERODED_BADLANDS,
						BiomeKeys.WOODED_BADLANDS,
						BiomeKeys.SAVANNA,
						BiomeKeys.SAVANNA_PLATEAU),
				SpawnGroup.AMBIENT, ModEntities.TUMBLEWEED, 8, 1, 2);
	}
}
