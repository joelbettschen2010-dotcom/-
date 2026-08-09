package ch.joel.westernmod.block;

import ch.joel.westernmod.entity.BanditEntity;
import ch.joel.westernmod.entity.BanditLeaderEntity;
import ch.joel.westernmod.world.WesternTownGenerator;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

/**
 * Steckbrief an der Wand. Ein Rechtsklick setzt ein Kopfgeld aus: eine Banditenbande mit
 * Anführer reitet an — wer den Anführer erledigt, kassiert die Golddollar.
 */
public class WantedPosterBlock extends HorizontalFacingBlock {
	public static final MapCodec<WantedPosterBlock> CODEC = createCodec(WantedPosterBlock::new);

	private static final VoxelShape NORTH = Block.createCuboidShape(2.0, 2.0, 14.0, 14.0, 15.0, 16.0);
	private static final VoxelShape SOUTH = Block.createCuboidShape(2.0, 2.0, 0.0, 14.0, 15.0, 2.0);
	private static final VoxelShape WEST = Block.createCuboidShape(14.0, 2.0, 2.0, 16.0, 15.0, 14.0);
	private static final VoxelShape EAST = Block.createCuboidShape(0.0, 2.0, 2.0, 2.0, 15.0, 14.0);

	public WantedPosterBlock(Settings settings) {
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
		return this.getDefaultState().with(FACING, context.getHorizontalPlayerFacing().getOpposite());
	}

	@Override
	protected BlockState rotate(BlockState state, BlockRotation rotation) {
		return state.with(FACING, rotation.rotate(state.get(FACING)));
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return switch (state.get(FACING)) {
			case SOUTH -> SOUTH;
			case WEST -> WEST;
			case EAST -> EAST;
			default -> NORTH;
		};
	}

	/** FACING zeigt vom Steckbrief weg — die tragende Wand liegt also dahinter. */
	@Override
	protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
		Direction wallSide = state.get(FACING).getOpposite();
		BlockPos support = pos.offset(wallSide);
		return world.getBlockState(support).isSideSolidFullSquare(world, support, wallSide.getOpposite());
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (world.isClient) {
			return ActionResult.SUCCESS;
		}

		ServerWorld serverWorld = (ServerWorld) world;
		int bandits = WesternTownGenerator.spawnBountyGang(serverWorld, pos, world.getRandom());

		world.playSound(null, pos, SoundEvents.ENTITY_ILLUSIONER_PREPARE_MIRROR, SoundCategory.BLOCKS, 0.9f, 0.8f);
		player.sendMessage(Text.translatable("message.westernmod.bounty", bandits).formatted(Formatting.RED), false);
		return ActionResult.CONSUME;
	}
}
