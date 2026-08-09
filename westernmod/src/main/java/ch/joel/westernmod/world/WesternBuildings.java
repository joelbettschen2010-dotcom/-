package ch.joel.westernmod.world;

import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FurnaceBlock;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.WallSignBlock;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Baut die typischen Westernhäuser: rechteckiger Kasten, davor eine überdachte Veranda und
 * darüber die hohe, gerade Schaufassade ("false front"), die jedes Kaff grösser wirken liess,
 * als es war.
 */
public final class WesternBuildings {

	private WesternBuildings() {
	}

	/**
	 * @param rNear    r-Koordinate der Vorderfront (strassenseitig)
	 * @param depth    Gebäudetiefe in Blöcken
	 * @param leftSide steht das Haus links der Strasse (negatives r)?
	 */
	public static void storefront(TownBuilder b, BuildingType type, int fMin, int fMax, int rNear, int depth,
			boolean leftSide) {
		int inward = leftSide ? -1 : 1;
		int toStreet = -inward;
		int rFront = rNear;
		int rBack = rNear + inward * (depth - 1);
		int rMin = Math.min(rFront, rBack);
		int rMax = Math.max(rFront, rBack);
		Direction facing = leftSide ? b.right() : b.right().getOpposite();

		BlockState wall = type.wall();
		BlockState roof = type.roof();

		// Fundament, Boden, Wände, Decke
		b.fillMixed(fMin, -3, rMin, fMax, -1, rMax, Blocks.COBBLESTONE.getDefaultState(),
				Blocks.STONE.getDefaultState(), 0.35f);
		b.fill(fMin, 0, rMin, fMax, 0, rMax, type.floor());
		b.fill(fMin + 1, 1, rMin + 1, fMax - 1, 4, rMax - 1, Blocks.AIR.getDefaultState());
		b.walls(fMin, 1, rMin, fMax, 4, rMax, wall);
		b.fill(fMin, 5, rMin, fMax, 5, rMax, roof);

		// Schaufassade: die Strassenseite ragt über das Dach hinaus.
		b.fill(fMin, 6, rFront, fMax, 7, rFront, wall);
		for (int f = fMin; f <= fMax; f++) {
			b.set(f, 8, rFront, slab(type.roof().getBlock(), true));
		}

		// Fenster in der Fassade
		int fCenter = (fMin + fMax) / 2;
		for (int f = fMin + 1; f <= fMax - 1; f++) {
			if (Math.abs(f - fCenter) <= 1) {
				continue;
			}
			if ((f - fMin) % 2 == 1) {
				b.set(f, 2, rFront, Blocks.GLASS_PANE.getDefaultState());
				b.set(f, 3, rFront, Blocks.GLASS_PANE.getDefaultState());
			}
		}
		b.set(fCenter - 2, 7, rFront, Blocks.GLASS_PANE.getDefaultState());
		b.set(fCenter + 2, 7, rFront, Blocks.GLASS_PANE.getDefaultState());

		// Seitenfenster
		for (int r = rMin + 2; r <= rMax - 1; r += 3) {
			b.set(fMin, 2, r, Blocks.GLASS_PANE.getDefaultState());
			b.set(fMax, 2, r, Blocks.GLASS_PANE.getDefaultState());
		}

		// Tür
		placeDoor(b, type.door(), fCenter, rFront, facing);

		// Veranda
		int porchOuter = rFront + toStreet * 2;
		b.fill(fMin, 0, rFront + toStreet, fMax, 0, porchOuter, Blocks.SPRUCE_PLANKS.getDefaultState());
		for (int f = fMin; f <= fMax; f++) {
			b.set(f, 4, rFront + toStreet, slab(Blocks.SPRUCE_SLAB, true));
			b.set(f, 4, porchOuter, stairs(Blocks.SPRUCE_STAIRS, facing, true));
		}
		// Tragpfosten nur an den beiden Ecken — sonst stünde einer mitten im Eingang.
		for (int f : new int[] { fMin, fMax }) {
			for (int u = 1; u <= 3; u++) {
				b.set(f, u, porchOuter, Blocks.SPRUCE_FENCE.getDefaultState());
			}
		}
		// Geländer dazwischen, mit einer drei Blöcke breiten Lücke vor der Tür.
		for (int f = fMin + 1; f <= fMax - 1; f++) {
			if (Math.abs(f - fCenter) > 1) {
				b.setIfAir(f, 1, porchOuter, Blocks.SPRUCE_FENCE.getDefaultState());
			}
		}

		// Laterne an der Veranda
		b.set(fCenter - 2, 3, porchOuter, Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true));

		// Schild über der Tür
		placeSign(b, type.label(), fCenter, 6, rFront + toStreet, facing);

		decorate(b, type, fMin, fMax, rMin, rMax, rFront, inward, facing);
	}

	// --- Innenausbau -----------------------------------------------------------------------

	private static void decorate(TownBuilder b, BuildingType type, int fMin, int fMax, int rMin, int rMax,
			int rFront, int inward, Direction facing) {
		int fCenter = (fMin + fMax) / 2;
		int rBack = rFront + inward * (rMax - rMin);
		// Erste und letzte Reihe *innerhalb* der Wände.
		int rInFront = rFront + inward;
		int rInBack = rBack - inward;
		Direction inwardDir = facing.getOpposite();

		// Grundbeleuchtung
		b.set(fMin + 1, 4, rInFront, Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true));
		b.set(fMax - 1, 4, rInBack, Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true));

		switch (type) {
			case SALOON -> {
				// Theke quer durch den Raum, dahinter die Fässer
				for (int f = fMin + 1; f <= fMax - 1; f++) {
					b.set(f, 1, rInBack - inward, stairs(Blocks.SPRUCE_STAIRS, facing, false));
					b.set(f, 1, rInBack, Blocks.BARREL.getDefaultState());
				}
				b.set(fMin + 1, 2, rInBack, Blocks.CAULDRON.getDefaultState());
				b.set(fCenter, 1, rInBack, Blocks.JUKEBOX.getDefaultState());

				// Zwei Tische mit Hockern
				for (int f : new int[] { fMin + 2, fMax - 2 }) {
					b.set(f, 1, rInFront + inward, Blocks.OAK_FENCE.getDefaultState());
					b.set(f, 2, rInFront + inward, Blocks.OAK_PRESSURE_PLATE.getDefaultState());
					b.set(f - 1, 1, rInFront + inward, stairs(Blocks.SPRUCE_STAIRS, b.forward(), false));
					b.set(f + 1, 1, rInFront + inward,
							stairs(Blocks.SPRUCE_STAIRS, b.forward().getOpposite(), false));
				}
			}
			case SHERIFF_OFFICE -> {
				// Gitter trennen die Zellen ab
				int rCell = rInBack - inward;
				for (int f = fCenter; f <= fMax - 1; f++) {
					for (int u = 1; u <= 3; u++) {
						b.set(f, u, rCell, Blocks.IRON_BARS.getDefaultState());
					}
				}
				b.set(fCenter, 1, rCell, Blocks.AIR.getDefaultState());
				b.set(fCenter, 2, rCell, Blocks.AIR.getDefaultState());
				placeBed(b, fMax - 2, rInBack, Blocks.WHITE_BED);

				// Schreibtisch des Sheriffs
				b.set(fMin + 1, 1, rInBack, Blocks.CRAFTING_TABLE.getDefaultState());
				b.set(fMin + 2, 1, rInBack, chest(facing));
				b.set(fMin + 1, 1, rInFront, stairs(Blocks.SPRUCE_STAIRS, inwardDir, false));
			}
			case BANK -> {
				// Tresorraum hinten rechts
				b.fill(fCenter, 1, rInBack, fMax - 1, 3, rInBack, Blocks.IRON_BLOCK.getDefaultState());
				for (int u = 1; u <= 3; u++) {
					b.set(fCenter, u, rInBack, Blocks.IRON_BARS.getDefaultState());
				}
				b.set(fMax - 1, 1, rInBack - inward, chest(facing));
				b.set(fMax - 2, 1, rInBack - inward, Blocks.GOLD_BLOCK.getDefaultState());
				// Schalter zur Kundenseite
				for (int f = fMin + 1; f <= fCenter - 1; f++) {
					b.set(f, 1, rInFront + inward, Blocks.SMOOTH_STONE_SLAB.getDefaultState());
					b.set(f, 2, rInFront + inward, Blocks.IRON_BARS.getDefaultState());
				}
			}
			case GENERAL_STORE -> {
				for (int f = fMin + 1; f <= fMax - 1; f++) {
					b.set(f, 1, rInBack, Blocks.BARREL.getDefaultState());
					if (f % 2 == 0) {
						b.set(f, 2, rInBack, Blocks.BARREL.getDefaultState());
					}
				}
				b.set(fMin + 1, 1, rInFront, Blocks.CRAFTING_TABLE.getDefaultState());
				b.set(fMin + 2, 1, rInFront, Blocks.COMPOSTER.getDefaultState());
				b.set(fMax - 1, 1, rInFront, chest(facing));
				b.set(fCenter, 1, rInFront + inward, Blocks.SMOOTH_STONE_SLAB.getDefaultState());
			}
			case HOTEL -> {
				for (int f = fMin + 1; f <= fMax - 3; f += 3) {
					placeBed(b, f, rInBack, Blocks.RED_BED);
					b.set(f + 2, 1, rInBack, Blocks.SPRUCE_TRAPDOOR.getDefaultState());
				}
				b.set(fMin + 1, 1, rInFront, chest(facing));
				b.set(fCenter, 1, rInFront, Blocks.FLOWER_POT.getDefaultState());
			}
			case BLACKSMITH -> {
				b.set(fMin + 1, 1, rInBack, Blocks.ANVIL.getDefaultState());
				b.set(fMin + 2, 1, rInBack, Blocks.SMITHING_TABLE.getDefaultState());
				b.set(fCenter, 1, rInBack, Blocks.FURNACE.getDefaultState().with(FurnaceBlock.FACING, facing));
				b.set(fCenter + 1, 1, rInBack, Blocks.BLAST_FURNACE.getDefaultState()
						.with(FurnaceBlock.FACING, facing));
				b.set(fMax - 1, 1, rInBack, Blocks.WATER_CAULDRON.getDefaultState());
				b.set(fMax - 1, 1, rInFront, chest(facing));
			}
			case CHURCH -> {
				// Bankreihen, Mittelgang bleibt frei
				for (int r = rInFront + inward; insideOf(r, rInBack, inward); r += inward * 2) {
					for (int f = fMin + 1; f <= fMax - 1; f++) {
						if (f == fCenter) {
							continue;
						}
						b.set(f, 1, r, stairs(Blocks.OAK_STAIRS, inwardDir, false));
					}
				}
				b.set(fCenter, 1, rInBack, Blocks.LECTERN.getDefaultState());
				b.set(fCenter - 1, 1, rInBack, Blocks.CANDLE.getDefaultState());
				b.set(fCenter + 1, 1, rInBack, Blocks.CANDLE.getDefaultState());
				// Glockenturm über der Fassade
				b.fill(fCenter - 1, 6, rFront, fCenter + 1, 10, rFront, type.wall());
				b.set(fCenter, 11, rFront, Blocks.BELL.getDefaultState());
				b.set(fCenter, 12, rFront, Blocks.OAK_FENCE.getDefaultState());
			}
			case STABLE -> {
				// Offene Front: Wand raus, Zaun rein
				for (int f = fMin + 1; f <= fMax - 1; f++) {
					for (int u = 1; u <= 4; u++) {
						b.set(f, u, rFront, Blocks.AIR.getDefaultState());
					}
					b.set(f, 1, rFront, Blocks.OAK_FENCE.getDefaultState());
				}
				b.set(fCenter, 1, rFront, Blocks.OAK_FENCE_GATE.getDefaultState()
						.with(HorizontalFacingBlock.FACING, b.forward()));
				b.fill(fMin + 1, 1, rInBack, fMax - 1, 1, rInBack, Blocks.HAY_BLOCK.getDefaultState());
				b.set(fMin + 1, 2, rInBack, Blocks.HAY_BLOCK.getDefaultState());
				b.set(fMax - 1, 1, rInBack - inward, Blocks.WATER_CAULDRON.getDefaultState());
			}
		}
	}

	/** Liegt {@code r} auf dem Weg von der Front zur Rückwand noch im Innenraum? */
	private static boolean insideOf(int r, int rInBack, int inward) {
		return inward > 0 ? r < rInBack : r > rInBack;
	}

	// --- Bausteine -------------------------------------------------------------------------

	static BlockState stairs(Block stairsBlock, Direction facing, boolean top) {
		return stairsBlock.getDefaultState()
				.with(StairsBlock.FACING, facing)
				.with(StairsBlock.HALF, top ? BlockHalf.TOP : BlockHalf.BOTTOM);
	}

	static BlockState slab(Block block, boolean top) {
		return matchingSlab(block).getDefaultState().with(SlabBlock.TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
	}

	private static Block matchingSlab(Block block) {
		if (block == Blocks.SPRUCE_SLAB) {
			return block;
		}
		if (block == Blocks.DARK_OAK_PLANKS) {
			return Blocks.DARK_OAK_SLAB;
		}
		if (block == Blocks.OAK_PLANKS) {
			return Blocks.OAK_SLAB;
		}
		if (block == Blocks.BIRCH_PLANKS) {
			return Blocks.BIRCH_SLAB;
		}
		if (block == Blocks.DEEPSLATE_TILES) {
			return Blocks.DEEPSLATE_TILE_SLAB;
		}
		if (block == Blocks.STONE_BRICKS) {
			return Blocks.STONE_BRICK_SLAB;
		}
		return Blocks.SPRUCE_SLAB;
	}

	static BlockState chest(Direction facing) {
		return Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, facing);
	}

	/** Türen brauchen beide Hälften auf einmal — deshalb ohne Nachbar-Update setzen. */
	static void placeDoor(TownBuilder b, Block door, int f, int r, Direction facing) {
		BlockState base = door.getDefaultState().with(DoorBlock.FACING, facing);
		b.setFast(f, 1, r, base.with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		b.setFast(f, 2, r, base.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
	}

	/**
	 * Gilt genauso für Betten: Fuss- und Kopfteil müssen zusammen stehen. Das Bett liegt
	 * entlang der Strassenachse, Kopfteil in Fahrtrichtung.
	 */
	static void placeBed(TownBuilder b, int f, int r, Block bed) {
		BlockState base = bed.getDefaultState().with(BedBlock.FACING, b.forward());
		b.setFast(f, 1, r, base.with(BedBlock.PART, BedPart.FOOT));
		b.setFast(f + 1, 1, r, base.with(BedBlock.PART, BedPart.HEAD));
	}

	static void placeSign(TownBuilder b, String label, int f, int u, int r, Direction facing) {
		BlockPos pos = b.at(f, u, r);
		b.world().setBlockState(pos,
				Blocks.SPRUCE_WALL_SIGN.getDefaultState().with(WallSignBlock.FACING, facing),
				Block.NOTIFY_ALL);

		if (b.world().getBlockEntity(pos) instanceof SignBlockEntity sign) {
			sign.setText(sign.getFrontText()
					.withMessage(1, Text.literal(label))
					.withColor(DyeColor.BLACK), true);
			sign.markDirty();
		}
	}
}
