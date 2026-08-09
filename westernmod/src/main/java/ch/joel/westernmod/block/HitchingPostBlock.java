package ch.joel.westernmod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/** Anbindepfosten: zwei Pfosten mit Querbalken, an denen die Pferde warten. */
public class HitchingPostBlock extends HorizontalFacingBlock {
	public static final MapCodec<HitchingPostBlock> CODEC = createCodec(HitchingPostBlock::new);

	private static final VoxelShape NORTH_SOUTH = Block.createCuboidShape(6.0, 0.0, 0.0, 10.0, 16.0, 16.0);
	private static final VoxelShape EAST_WEST = Block.createCuboidShape(0.0, 0.0, 6.0, 16.0, 16.0, 10.0);

	public HitchingPostBlock(Settings settings) {
		super(settings);
		this.setDefaultState(this.stateManager.getDefaultState().with(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends HorizontalFacingBlock> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext context) {
		return this.getDefaultState().with(FACING, context.getHorizontalPlayerFacing());
	}

	@Override
	protected BlockState rotate(BlockState state, BlockRotation rotation) {
		return state.with(FACING, rotation.rotate(state.get(FACING)));
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		Direction facing = state.get(FACING);
		return facing.getAxis() == Direction.Axis.X ? EAST_WEST : NORTH_SOUTH;
	}
}
