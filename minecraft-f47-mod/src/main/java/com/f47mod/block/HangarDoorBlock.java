package com.f47mod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * Hangartor. Ein Segment eines grossen Rolltors: geoeffnet faehrt es nach oben
 * ein und gibt den Weg frei, geschlossen ist es ein voller Block. Laesst sich
 * per Hand oder mit Redstone schalten - so bekommt der Hangar ein Tor, das
 * sich fuer die ganze Breite gleichzeitig oeffnen laesst.
 */
public class HangarDoorBlock extends Block {
	public static final MapCodec<HangarDoorBlock> CODEC = createCodec(HangarDoorBlock::new);
	public static final BooleanProperty OPEN = Properties.OPEN;
	public static final BooleanProperty POWERED = Properties.POWERED;

	/** Eingefahrenes Tor: nur eine duenne Blende unter der Decke. */
	private static final VoxelShape RETRACTED_SHAPE = Block.createCuboidShape(0.0, 14.0, 0.0, 16.0, 16.0, 16.0);

	public HangarDoorBlock(Settings settings) {
		super(settings);
		setDefaultState(getDefaultState().with(OPEN, false).with(POWERED, false));
	}

	@Override
	protected MapCodec<? extends Block> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(OPEN, POWERED);
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return state.get(OPEN) ? RETRACTED_SHAPE : VoxelShapes.fullCube();
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return state.get(OPEN) ? RETRACTED_SHAPE : VoxelShapes.fullCube();
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
			BlockHitResult hit) {
		if (world.isClient) {
			return ActionResult.SUCCESS;
		}
		boolean open = !state.get(OPEN);
		world.setBlockState(pos, state.with(OPEN, open), Block.NOTIFY_ALL);
		playToggleSound(world, pos, open);
		// Angrenzende Torsegmente mitschalten, damit das ganze Tor faehrt.
		propagate(world, pos, open, 0);
		return ActionResult.CONSUME;
	}

	@Override
	protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock,
			BlockPos sourcePos, boolean notify) {
		if (world.isClient) {
			return;
		}
		boolean powered = world.isReceivingRedstonePower(pos);
		if (powered != state.get(POWERED)) {
			world.setBlockState(pos, state.with(POWERED, powered).with(OPEN, powered), Block.NOTIFY_ALL);
			playToggleSound(world, pos, powered);
		}
	}

	/** Schaltet benachbarte Torsegmente rekursiv mit (maximal 24 Bloecke weit). */
	private void propagate(World world, BlockPos origin, boolean open, int depth) {
		if (depth > 24) {
			return;
		}
		for (net.minecraft.util.math.Direction direction : net.minecraft.util.math.Direction.values()) {
			BlockPos neighbour = origin.offset(direction);
			BlockState state = world.getBlockState(neighbour);
			if (state.getBlock() instanceof HangarDoorBlock && state.get(OPEN) != open) {
				world.setBlockState(neighbour, state.with(OPEN, open), Block.NOTIFY_ALL);
				propagate(world, neighbour, open, depth + 1);
			}
		}
	}

	private void playToggleSound(World world, BlockPos pos, boolean open) {
		world.playSound(null, pos,
				open ? SoundEvents.BLOCK_IRON_DOOR_OPEN : SoundEvents.BLOCK_IRON_DOOR_CLOSE,
				SoundCategory.BLOCKS, 1.0f, 0.55f);
	}
}
