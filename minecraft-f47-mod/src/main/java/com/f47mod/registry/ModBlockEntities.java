package com.f47mod.registry;

import com.f47mod.F47Mod;
import com.f47mod.block.entity.BarracksBlockEntity;
import com.f47mod.block.entity.IronDomeBlockEntity;
import com.f47mod.block.entity.RadarBlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/** Block-Entities der Basis (Radar, Iron Dome, Kaserne). */
public final class ModBlockEntities {
	private ModBlockEntities() {
	}

	public static final BlockEntityType<RadarBlockEntity> RADAR = Registry.register(
			Registries.BLOCK_ENTITY_TYPE, F47Mod.id("radar"),
			BlockEntityType.Builder.create(RadarBlockEntity::new, ModBlocks.RADAR).build(null));

	public static final BlockEntityType<IronDomeBlockEntity> IRON_DOME = Registry.register(
			Registries.BLOCK_ENTITY_TYPE, F47Mod.id("iron_dome"),
			BlockEntityType.Builder.create(IronDomeBlockEntity::new, ModBlocks.IRON_DOME).build(null));

	public static final BlockEntityType<BarracksBlockEntity> BARRACKS = Registry.register(
			Registries.BLOCK_ENTITY_TYPE, F47Mod.id("barracks"),
			BlockEntityType.Builder.create(BarracksBlockEntity::new, ModBlocks.BARRACKS).build(null));

	public static void init() {
	}
}
