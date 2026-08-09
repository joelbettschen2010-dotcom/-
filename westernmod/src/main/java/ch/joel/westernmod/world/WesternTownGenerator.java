package ch.joel.westernmod.world;

import ch.joel.westernmod.ModBlocks;
import ch.joel.westernmod.ModEntities;
import ch.joel.westernmod.entity.WagonEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.WallSignBlock;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * Baut eine komplette kleine Westernstadt: eine staubige Hauptstrasse, links und rechts die
 * typischen Häuser mit Schaufassade, Wasserturm und Windmühle am Ortsausgang, dazu Bewohner,
 * Pferde, Planwagen — und ein Banditenlager in Sichtweite, damit es nicht langweilig wird.
 */
public final class WesternTownGenerator {

	private static final String[] TOWN_NAMES = {
			"DEADWOOD", "TOMBSTONE", "RED ROCK", "DRY GULCH",
			"SILVER CREEK", "GOLD HILL", "EL PASO", "COYOTE FLATS",
	};

	/** Bebauungsplan: Typ, Beginn und Ende entlang der Strasse, Tiefe. */
	private record Lot(BuildingType type, int fMin, int fMax, int depth, boolean leftSide) {
	}

	private static final Lot[] LOTS = {
			new Lot(BuildingType.SALOON, 2, 13, 9, true),
			new Lot(BuildingType.GENERAL_STORE, 16, 25, 8, true),
			new Lot(BuildingType.BANK, 28, 37, 8, true),
			new Lot(BuildingType.HOTEL, 40, 51, 9, true),

			new Lot(BuildingType.SHERIFF_OFFICE, 3, 12, 8, false),
			new Lot(BuildingType.BLACKSMITH, 15, 24, 8, false),
			new Lot(BuildingType.CHURCH, 27, 37, 10, false),
			new Lot(BuildingType.STABLE, 40, 51, 9, false),
	};

	private WesternTownGenerator() {
	}

	public static void generate(ServerWorld world, BlockPos origin, Direction facing, Random random) {
		TownBuilder b = new TownBuilder(world, origin, facing, random);

		// Baufeld planieren. Höhe 15 deckt Kirchturm, Wasserturm und Windmühlenflügel ab.
		b.levelGround(-10, 62, -18, 18, 15, 4);

		street(b);
		for (Lot lot : LOTS) {
			WesternBuildings.storefront(b, lot.type(), lot.fMin(), lot.fMax(),
					lot.leftSide() ? -5 : 5, lot.depth(), lot.leftSide());
		}

		entranceArch(b, random);
		waterTower(b, 56, -9);
		windmill(b, 56, 9);
		townSquare(b);
		streetProps(b);
		scatterDesert(b);

		populate(b);
		banditCamp(b, 26, -46, random);
	}

	// --- Strasse und Platz -----------------------------------------------------------------

	private static void street(TownBuilder b) {
		BlockState path = Blocks.DIRT_PATH.getDefaultState();
		BlockState coarse = Blocks.COARSE_DIRT.getDefaultState();

		for (int f = -8; f <= 60; f++) {
			for (int r = -4; r <= 4; r++) {
				boolean edge = Math.abs(r) == 4;
				b.setFast(f, 0, r, edge || b.random().nextFloat() < 0.18f ? coarse : path);
			}
		}

		// Wagenspuren: zwei ausgefahrene Rillen in der Fahrbahnmitte
		for (int f = -8; f <= 60; f++) {
			if (b.random().nextFloat() < 0.75f) {
				b.setFast(f, 0, -2, coarse);
				b.setFast(f, 0, 2, coarse);
			}
		}
	}

	private static void entranceArch(TownBuilder b, Random random) {
		BlockState post = Blocks.SPRUCE_LOG.getDefaultState();

		for (int u = 1; u <= 5; u++) {
			b.set(-6, u, -5, post);
			b.set(-6, u, 5, post);
		}
		for (int r = -5; r <= 5; r++) {
			b.set(-6, 6, r, Blocks.STRIPPED_SPRUCE_WOOD.getDefaultState());
		}
		b.set(-6, 5, -4, Blocks.SPRUCE_FENCE.getDefaultState());
		b.set(-6, 5, 4, Blocks.SPRUCE_FENCE.getDefaultState());
		b.set(-6, 7, -5, Blocks.LANTERN.getDefaultState());
		b.set(-6, 7, 5, Blocks.LANTERN.getDefaultState());

		String name = TOWN_NAMES[random.nextInt(TOWN_NAMES.length)];
		BlockPos signPos = b.at(-7, 6, 0);
		b.world().setBlockState(signPos,
				Blocks.SPRUCE_WALL_SIGN.getDefaultState().with(WallSignBlock.FACING, b.forward().getOpposite()),
				Block.NOTIFY_ALL);
		if (b.world().getBlockEntity(signPos) instanceof SignBlockEntity sign) {
			sign.setText(sign.getFrontText()
					.withMessage(1, Text.literal("WILLKOMMEN IN"))
					.withMessage(2, Text.literal(name))
					.withColor(DyeColor.BLACK), true);
			sign.markDirty();
		}
	}

	/** Platz am Ortsausgang mit Brunnen und Lagerfeuer. */
	private static void townSquare(TownBuilder b) {
		// Brunnen
		int f = 54;
		b.fill(f - 1, 0, -2, f + 1, 0, 0, Blocks.STONE_BRICKS.getDefaultState());
		b.walls(f - 1, 1, -2, f + 1, 1, 0, Blocks.STONE_BRICK_WALL.getDefaultState());
		b.setFast(f, 0, -1, Blocks.WATER.getDefaultState());
		b.set(f - 1, 2, -2, Blocks.OAK_FENCE.getDefaultState());
		b.set(f + 1, 2, 0, Blocks.OAK_FENCE.getDefaultState());
		b.set(f - 1, 3, -2, Blocks.OAK_FENCE.getDefaultState());
		b.set(f + 1, 3, 0, Blocks.OAK_FENCE.getDefaultState());
		b.set(f, 4, -1, Blocks.SPRUCE_SLAB.getDefaultState());
		b.set(f, 3, -1, Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true));

		// Lagerfeuer mit Sitzgelegenheit
		b.set(58, 1, 2, Blocks.CAMPFIRE.getDefaultState().with(CampfireBlock.FACING, b.forward()));
		b.set(57, 1, 2, WesternBuildings.stairs(Blocks.SPRUCE_STAIRS, b.forward(), false));
		b.set(59, 1, 2, WesternBuildings.stairs(Blocks.SPRUCE_STAIRS, b.forward().getOpposite(), false));
	}

	private static void waterTower(TownBuilder b, int f, int r) {
		// Vier Beine
		for (int u = 1; u <= 7; u++) {
			b.set(f - 2, u, r - 2, Blocks.SPRUCE_FENCE.getDefaultState());
			b.set(f + 2, u, r - 2, Blocks.SPRUCE_FENCE.getDefaultState());
			b.set(f - 2, u, r + 2, Blocks.SPRUCE_FENCE.getDefaultState());
			b.set(f + 2, u, r + 2, Blocks.SPRUCE_FENCE.getDefaultState());
		}
		// Querstreben
		for (int d = -2; d <= 2; d++) {
			b.set(f + d, 4, r - 2, Blocks.SPRUCE_FENCE.getDefaultState());
			b.set(f + d, 4, r + 2, Blocks.SPRUCE_FENCE.getDefaultState());
		}
		// Tank
		b.fill(f - 2, 8, r - 2, f + 2, 8, r + 2, Blocks.SPRUCE_PLANKS.getDefaultState());
		b.walls(f - 2, 9, r - 2, f + 2, 11, r + 2, Blocks.SPRUCE_PLANKS.getDefaultState());
		b.fill(f - 1, 9, r - 1, f + 1, 10, r + 1, Blocks.WATER.getDefaultState());
		b.fill(f - 2, 12, r - 2, f + 2, 12, r + 2, Blocks.DARK_OAK_SLAB.getDefaultState());
		// Fallrohr
		for (int u = 1; u <= 8; u++) {
			b.set(f, u, r + 3, Blocks.SPRUCE_FENCE.getDefaultState());
		}
	}

	private static void windmill(TownBuilder b, int f, int r) {
		b.fill(f - 1, 1, r - 1, f + 1, 9, r + 1, Blocks.STRIPPED_SPRUCE_WOOD.getDefaultState());
		b.fill(f, 1, r, f, 9, r, Blocks.AIR.getDefaultState());
		b.fill(f - 1, 10, r - 1, f + 1, 10, r + 1, Blocks.SPRUCE_SLAB.getDefaultState());

		// Windrad: vier Flügel aus Zaun mit Leinwand
		for (int arm = 1; arm <= 4; arm++) {
			b.set(f - 2, 8 + arm - 1, r, Blocks.OAK_FENCE.getDefaultState());
			b.set(f - 2, 8 - arm + 1, r, Blocks.OAK_FENCE.getDefaultState());
			b.set(f - 2, 8, r + arm, Blocks.OAK_FENCE.getDefaultState());
			b.set(f - 2, 8, r - arm, Blocks.OAK_FENCE.getDefaultState());
		}
		b.set(f - 2, 12, r, Blocks.WHITE_WOOL.getDefaultState());
		b.set(f - 2, 4, r, Blocks.WHITE_WOOL.getDefaultState());
		b.set(f - 2, 8, r + 5, Blocks.WHITE_WOOL.getDefaultState());
		b.set(f - 2, 8, r - 5, Blocks.WHITE_WOOL.getDefaultState());
	}

	/** Anbindepfosten, Tröge, Fässer und Laternen entlang der Strasse. */
	private static void streetProps(TownBuilder b) {
		for (int f = 4; f <= 50; f += 6) {
			b.set(f, 1, -4, ModBlocks.HITCHING_POST.getDefaultState()
					.with(HorizontalFacingBlock.FACING, b.forward()));
			b.set(f + 3, 1, 4, ModBlocks.HITCHING_POST.getDefaultState()
					.with(HorizontalFacingBlock.FACING, b.forward()));
		}

		for (int f = 8; f <= 46; f += 12) {
			b.set(f, 1, -4, Blocks.WATER_CAULDRON.getDefaultState());
			b.set(f + 6, 1, 4, Blocks.BARREL.getDefaultState());
		}

		// Strassenlaternen
		for (int f = 6; f <= 48; f += 14) {
			for (int u = 1; u <= 3; u++) {
				b.set(f, u, 4, Blocks.SPRUCE_FENCE.getDefaultState());
			}
			b.set(f, 4, 4, Blocks.LANTERN.getDefaultState());
		}

		// Steckbrief an der Aussenwand des Sheriffbüros; er blickt zur Strasse.
		b.set(9, 3, 4, ModBlocks.WANTED_POSTER.getDefaultState()
				.with(HorizontalFacingBlock.FACING, b.right().getOpposite()));
	}

	/** Kakteen, Gestrüpp und ein paar Felsen ausserhalb der Bebauung. */
	private static void scatterDesert(TownBuilder b) {
		Random random = b.random();
		for (int i = 0; i < 90; i++) {
			int f = random.nextBetween(-10, 62);
			int r = random.nextBetween(-21, 21);
			if (Math.abs(r) < 16) {
				continue;
			}
			if (!b.isAir(f, 1, r) || !b.get(f, 0, r).isOpaqueFullCube(b.world(), b.at(f, 0, r))) {
				continue;
			}

			// Ohne Nachbar-Update gesetzt: Kaktus und Gestrüpp würden ihre strengen
			// Platzierungsregeln sonst sofort selbst prüfen und wieder abfallen.
			float roll = random.nextFloat();
			if (roll < 0.35f) {
				b.setFast(f, 0, r, Blocks.SAND.getDefaultState());
				b.setFast(f, 1, r, Blocks.CACTUS.getDefaultState());
				if (random.nextBoolean()) {
					b.setFast(f, 2, r, Blocks.CACTUS.getDefaultState());
				}
			} else if (roll < 0.75f) {
				b.setFast(f, 1, r, Blocks.DEAD_BUSH.getDefaultState());
			} else {
				b.setFast(f, 1, r, Blocks.COBBLESTONE.getDefaultState());
			}
		}
	}

	// --- Bewohner --------------------------------------------------------------------------

	private static void populate(TownBuilder b) {
		Random random = b.random();

		spawn(b, ModEntities.SHERIFF, 8, 1, 3);

		for (int i = 0; i < 5; i++) {
			spawn(b, ModEntities.COWBOY, random.nextBetween(2, 50), 1, random.nextBetween(-3, 3));
		}
		for (int i = 0; i < 3; i++) {
			spawn(b, EntityType.VILLAGER, random.nextBetween(6, 46), 1, random.nextBetween(-3, 3));
		}

		// Pferde im Stall
		for (int i = 0; i < 3; i++) {
			HorseEntity horse = spawn(b, EntityType.HORSE, 42 + i * 3, 1, 9);
			if (horse != null) {
				horse.setTame(true);
				horse.setPersistent();
			}
		}

		// Zwei Planwagen an der Strasse
		WagonEntity.spawn(b.world(), b.at(20, 1, -3), yawOf(b.forward()));
		WagonEntity.spawn(b.world(), b.at(35, 1, 3), yawOf(b.forward().getOpposite()));
	}

	private static void banditCamp(TownBuilder b, int f, int r, Random random) {
		b.levelGround(f - 8, f + 8, r - 8, r + 8, 8, 3);

		// Feuerstelle
		b.set(f, 1, r, Blocks.CAMPFIRE.getDefaultState().with(CampfireBlock.FACING, b.forward()));
		for (int i = 0; i < 8; i++) {
			int df = random.nextBetween(-5, 5);
			int dr = random.nextBetween(-5, 5);
			b.setIfAir(f + df, 1, r + dr, Blocks.COBBLESTONE.getDefaultState());
		}

		// Drei Zelte aus Wolle
		int[][] tents = { { f - 5, r - 3 }, { f + 4, r - 2 }, { f - 1, r + 5 } };
		for (int[] tent : tents) {
			int tf = tent[0];
			int tr = tent[1];
			BlockState canvas = random.nextBoolean()
					? Blocks.WHITE_WOOL.getDefaultState()
					: Blocks.BROWN_WOOL.getDefaultState();

			b.fill(tf - 1, 1, tr - 1, tf + 1, 1, tr + 1, canvas);
			b.fill(tf, 2, tr - 1, tf, 2, tr + 1, canvas);
			b.setFast(tf, 1, tr, Blocks.AIR.getDefaultState());
			b.setFast(tf, 1, tr - 1, Blocks.AIR.getDefaultState());
			b.set(tf, 1, tr, Blocks.SPRUCE_TRAPDOOR.getDefaultState());
		}

		b.set(f + 2, 1, r + 2, Blocks.CHEST.getDefaultState());
		b.set(f - 2, 1, r + 2, Blocks.BARREL.getDefaultState());
		b.set(f + 3, 1, r - 3, Blocks.CRAFTING_TABLE.getDefaultState());

		// Umzäunung als Pferch
		for (int d = -6; d <= 6; d++) {
			b.setIfAir(f + d, 1, r - 7, Blocks.OAK_FENCE.getDefaultState());
			b.setIfAir(f + d, 1, r + 7, Blocks.OAK_FENCE.getDefaultState());
		}

		spawn(b, ModEntities.BANDIT_LEADER, f, 2, r + 2);
		for (int i = 0; i < 4; i++) {
			spawn(b, ModEntities.BANDIT, f + random.nextBetween(-4, 4), 2, r + random.nextBetween(-4, 4));
		}
	}

	// --- Kopfgeld --------------------------------------------------------------------------

	/** Lässt eine Banditenbande in der Nähe auftauchen. Gibt zurück, wie viele es geworden sind. */
	public static int spawnBountyGang(ServerWorld world, BlockPos near, Random random) {
		int spawned = 0;

		for (int i = 0; i < 4; i++) {
			BlockPos pos = surfaceNear(world, near, random, 22, 40);
			if (world.spawnEntity(create(world, ModEntities.BANDIT, pos))) {
				spawned++;
			}
		}

		BlockPos leaderPos = surfaceNear(world, near, random, 26, 44);
		if (world.spawnEntity(create(world, ModEntities.BANDIT_LEADER, leaderPos))) {
			spawned++;
		}

		return spawned;
	}

	private static BlockPos surfaceNear(ServerWorld world, BlockPos center, Random random, int minRadius,
			int maxRadius) {
		double angle = random.nextDouble() * Math.PI * 2.0;
		int radius = random.nextBetween(minRadius, maxRadius);
		int x = center.getX() + (int) (Math.cos(angle) * radius);
		int z = center.getZ() + (int) (Math.sin(angle) * radius);
		int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
		return new BlockPos(x, y, z);
	}

	// --- Hilfsmittel -----------------------------------------------------------------------

	@Nullable
	private static <T extends Entity> T spawn(TownBuilder b, EntityType<T> type, int f, int u, int r) {
		T entity = create(b.world(), type, b.at(f, u, r));
		if (entity == null) {
			return null;
		}
		b.world().spawnEntity(entity);
		return entity;
	}

	@Nullable
	private static <T extends Entity> T create(ServerWorld world, EntityType<T> type, BlockPos pos) {
		T entity = type.create(world);
		if (entity == null) {
			return null;
		}
		entity.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
				world.getRandom().nextFloat() * 360.0f, 0.0f);
		if (entity instanceof MobEntity mob) {
			mob.setPersistent();
		}
		return entity;
	}

	private static float yawOf(Direction direction) {
		return direction.asRotation();
	}
}
