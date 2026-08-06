package com.f47mod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;

/**
 * Bahnmarkierung (Mittellinie, Schwelle, Randstreifen). Wird beim Setzen in
 * Blickrichtung ausgerichtet, damit die Linien der Bahn folgen.
 */
public class RunwayMarkingBlock extends HorizontalFacingBlock {
	public static final MapCodec<RunwayMarkingBlock> CODEC = createCodec(RunwayMarkingBlock::new);

	public RunwayMarkingBlock(Settings settings) {
		super(settings);
		setDefaultState(getDefaultState().with(FACING, net.minecraft.util.math.Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends HorizontalFacingBlock> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<net.minecraft.block.Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext context) {
		return getDefaultState().with(FACING, context.getHorizontalPlayerFacing());
	}

	@Override
	protected BlockState rotate(BlockState state, BlockRotation rotation) {
		return state.with(FACING, rotation.rotate(state.get(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, BlockMirror mirror) {
		return state.rotate(mirror.getRotation(state.get(FACING)));
	}
}
