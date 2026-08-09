package ch.joel.westernmod.world;

import ch.joel.westernmod.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;

/** Die Häusertypen einer Westernstadt samt Baumaterial und Schild über der Tür. */
public enum BuildingType {
	SALOON("SALOON", Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_PLANKS, ModBlocks.SALOON_DOOR),
	SHERIFF_OFFICE("SHERIFF", Blocks.STRIPPED_OAK_WOOD, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_DOOR),
	BANK("BANK", Blocks.STONE_BRICKS, Blocks.POLISHED_ANDESITE, Blocks.DEEPSLATE_TILES, Blocks.SPRUCE_DOOR),
	GENERAL_STORE("STORE", Blocks.OAK_PLANKS, Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.OAK_DOOR),
	HOTEL("HOTEL", Blocks.DARK_OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_DOOR),
	BLACKSMITH("SCHMIEDE", Blocks.COBBLESTONE, Blocks.STONE_BRICKS, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_DOOR),
	CHURCH("KIRCHE", Blocks.BIRCH_PLANKS, Blocks.BIRCH_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.BIRCH_DOOR),
	STABLE("STALL", Blocks.SPRUCE_PLANKS, Blocks.DIRT, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_DOOR);

	private final String label;
	private final Block wall;
	private final Block floor;
	private final Block roof;
	private final Block door;

	BuildingType(String label, Block wall, Block floor, Block roof, Block door) {
		this.label = label;
		this.wall = wall;
		this.floor = floor;
		this.roof = roof;
		this.door = door;
	}

	public String label() {
		return this.label;
	}

	public BlockState wall() {
		return this.wall.getDefaultState();
	}

	public BlockState floor() {
		return this.floor.getDefaultState();
	}

	public BlockState roof() {
		return this.roof.getDefaultState();
	}

	public Block door() {
		return this.door;
	}
}
