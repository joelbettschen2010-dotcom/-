package com.f47mod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;

/**
 * Startbahnbelag. Jets rollen darauf fast reibungsfrei und erreichen so
 * ueberhaupt erst die Abhebegeschwindigkeit - auf Gras oder Sand bremst die
 * Maschine dagegen stark ab.
 */
public class RunwayBlock extends Block {
	public static final MapCodec<RunwayBlock> CODEC = createCodec(RunwayBlock::new);

	public RunwayBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<? extends Block> getCodec() {
		return CODEC;
	}

	/** Prueft, ob unter einem Flugzeug befestigte Bahn liegt. */
	public static boolean isRunway(BlockState state) {
		return state.getBlock() instanceof RunwayBlock
				|| state.getBlock() instanceof RunwayMarkingBlock;
	}
}
