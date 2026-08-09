package com.f47mod.registry;

import com.f47mod.F47Mod;
import com.f47mod.block.BarracksBlock;
import com.f47mod.block.HangarDoorBlock;
import com.f47mod.block.IronDomeBlock;
import com.f47mod.block.RadarBlock;
import com.f47mod.block.RearmPadBlock;
import com.f47mod.block.RunwayBlock;
import com.f47mod.block.RunwayMarkingBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

/** Alle Bloecke der Militaerbasis. */
public final class ModBlocks {
	private ModBlocks() {
	}

	// --- Startbahn --------------------------------------------------------
	public static final Block RUNWAY = register("runway",
			new RunwayBlock(AbstractBlock.Settings.create()
					.strength(1.8f, 6.0f)
					.requiresTool()
					.sounds(BlockSoundGroup.STONE)));

	public static final Block RUNWAY_CENTERLINE = register("runway_centerline",
			new RunwayMarkingBlock(AbstractBlock.Settings.create()
					.strength(1.8f, 6.0f)
					.requiresTool()
					.sounds(BlockSoundGroup.STONE)));

	public static final Block RUNWAY_THRESHOLD = register("runway_threshold",
			new RunwayMarkingBlock(AbstractBlock.Settings.create()
					.strength(1.8f, 6.0f)
					.requiresTool()
					.sounds(BlockSoundGroup.STONE)));

	public static final Block RUNWAY_LIGHT = register("runway_light",
			new RunwayBlock(AbstractBlock.Settings.create()
					.strength(1.2f)
					.requiresTool()
					.luminance(state -> 14)
					.sounds(BlockSoundGroup.GLASS)));

	// --- Hangar -----------------------------------------------------------
	public static final Block HANGAR_DOOR = register("hangar_door",
			new HangarDoorBlock(AbstractBlock.Settings.create()
					.strength(4.0f, 12.0f)
					.requiresTool()
					.nonOpaque()
					.sounds(BlockSoundGroup.METAL)));

	public static final Block HANGAR_WALL = register("hangar_wall",
			new Block(AbstractBlock.Settings.create()
					.strength(4.0f, 12.0f)
					.requiresTool()
					.sounds(BlockSoundGroup.METAL)));

	// --- Systeme ----------------------------------------------------------
	public static final Block RADAR = register("radar",
			new RadarBlock(AbstractBlock.Settings.create()
					.strength(3.5f, 9.0f)
					.requiresTool()
					.nonOpaque()
					.luminance(state -> state.get(RadarBlock.ACTIVE) ? 7 : 0)
					.sounds(BlockSoundGroup.METAL)));

	public static final Block IRON_DOME = register("iron_dome",
			new IronDomeBlock(AbstractBlock.Settings.create()
					.strength(4.5f, 14.0f)
					.requiresTool()
					.nonOpaque()
					.sounds(BlockSoundGroup.METAL)));

	public static final Block REARM_PAD = register("rearm_pad",
			new RearmPadBlock(AbstractBlock.Settings.create()
					.strength(2.5f, 8.0f)
					.requiresTool()
					.luminance(state -> 6)
					.sounds(BlockSoundGroup.METAL)));

	public static final Block BARRACKS = register("barracks",
			new BarracksBlock(AbstractBlock.Settings.create()
					.strength(3.0f, 8.0f)
					.requiresTool()
					.sounds(BlockSoundGroup.METAL)));

	// --- Rohstoffe --------------------------------------------------------
	public static final Block TITANIUM_ORE = register("titanium_ore",
			new net.minecraft.block.ExperienceDroppingBlock(
					net.minecraft.util.math.intprovider.UniformIntProvider.create(2, 5),
					AbstractBlock.Settings.create()
							.strength(3.5f, 3.0f)
							.requiresTool()
							.sounds(BlockSoundGroup.STONE)));

	public static final Block DEEPSLATE_TITANIUM_ORE = register("deepslate_titanium_ore",
			new net.minecraft.block.ExperienceDroppingBlock(
					net.minecraft.util.math.intprovider.UniformIntProvider.create(2, 5),
					AbstractBlock.Settings.create()
							.strength(5.0f, 3.0f)
							.requiresTool()
							.sounds(BlockSoundGroup.DEEPSLATE)));

	public static final Block TITANIUM_BLOCK = register("titanium_block",
			new Block(AbstractBlock.Settings.create()
					.strength(5.0f, 8.0f)
					.requiresTool()
					.sounds(BlockSoundGroup.METAL)));

	private static Block register(String name, Block block) {
		Identifier id = F47Mod.id(name);
		Block registered = Registry.register(Registries.BLOCK, id, block);
		Registry.register(Registries.ITEM, id, new BlockItem(registered, new Item.Settings()));
		return registered;
	}

	/** Wird vom Modinitialisierer aufgerufen, damit die Klasse geladen wird. */
	public static void init() {
	}
}
