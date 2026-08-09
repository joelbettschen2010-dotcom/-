package com.f47mod.block.entity;

import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Verzeichnis aller geladenen Radarstationen je Welt.
 *
 * <p>Ohne dieses Verzeichnis muessten Iron Dome, Kommando-Tablet und die
 * Radaruebertragung die Umgebung Block fuer Block absuchen - das kostet bei
 * jedem Suchlauf tausende Zugriffe und bremst den Server spuerbar. Stattdessen
 * trägt sich jede Station beim Laden hier ein und beim Entladen wieder aus.
 */
public final class RadarRegistry {
	private static final Map<RegistryKey<World>, Set<BlockPos>> STATIONS = new HashMap<>();

	private RadarRegistry() {
	}

	public static void add(World world, BlockPos pos) {
		STATIONS.computeIfAbsent(world.getRegistryKey(), key -> new HashSet<>()).add(pos.toImmutable());
	}

	public static void remove(World world, BlockPos pos) {
		Set<BlockPos> set = STATIONS.get(world.getRegistryKey());
		if (set != null) {
			set.remove(pos);
			if (set.isEmpty()) {
				STATIONS.remove(world.getRegistryKey());
			}
		}
	}

	public static Set<BlockPos> positions(World world) {
		return Collections.unmodifiableSet(
				STATIONS.getOrDefault(world.getRegistryKey(), Collections.emptySet()));
	}

	/** Naechstgelegene eingeschaltete Station im Umkreis - oder null. */
	@Nullable
	public static RadarBlockEntity findNearby(World world, BlockPos pos, int searchRadius) {
		RadarBlockEntity best = null;
		double bestDistance = (double) searchRadius * searchRadius;

		for (BlockPos station : positions(world)) {
			double distance = station.getSquaredDistance(pos);
			if (distance > bestDistance) {
				continue;
			}
			if (world.getBlockEntity(station) instanceof RadarBlockEntity radar && radar.isActive()) {
				bestDistance = distance;
				best = radar;
			}
		}
		return best;
	}
}
