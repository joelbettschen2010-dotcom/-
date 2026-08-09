package ch.joel.westernmod;

import ch.joel.westernmod.block.HitchingPostBlock;
import ch.joel.westernmod.block.WantedPosterBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSetType;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.MapColor;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;

public final class ModBlocks {
	/** Anbindepfosten — steht vor jedem Saloon, hier werden die Pferde angebunden. */
	public static final Block HITCHING_POST = register("hitching_post", new HitchingPostBlock(
			AbstractBlock.Settings.create()
					.mapColor(MapColor.OAK_TAN)
					.strength(2.0f)
					.sounds(BlockSoundGroup.WOOD)
					.nonOpaque()));

	/** Schwingtür des Saloons. */
	public static final Block SALOON_DOOR = register("saloon_door", new DoorBlock(BlockSetType.OAK,
			AbstractBlock.Settings.create()
					.mapColor(MapColor.SPRUCE_BROWN)
					.strength(3.0f)
					.sounds(BlockSoundGroup.WOOD)
					.nonOpaque()
					.pistonBehavior(net.minecraft.block.piston.PistonBehavior.DESTROY)));

	/** Steckbrief — Rechtsklick ruft ein Kopfgeld aus und lockt eine Banditenbande an. */
	public static final Block WANTED_POSTER = register("wanted_poster", new WantedPosterBlock(
			AbstractBlock.Settings.create()
					.mapColor(MapColor.PALE_YELLOW)
					.strength(0.5f)
					.sounds(BlockSoundGroup.WOOD)
					.nonOpaque()
					.noCollision()));

	private ModBlocks() {
	}

	private static Block register(String name, Block block) {
		Block registered = Registry.register(Registries.BLOCK, WesternMod.id(name), block);
		Registry.register(Registries.ITEM, WesternMod.id(name), new BlockItem(registered, new Item.Settings()));
		return registered;
	}

	/** Löst die statische Initialisierung aus. */
	public static void register() {
	}
}
