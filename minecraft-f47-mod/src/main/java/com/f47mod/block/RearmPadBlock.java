package com.f47mod.block;

import com.f47mod.F47Config;
import com.f47mod.entity.vehicle.F47Entity;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Wartungs- und Munitionsfeld. Ein Jet, der darauf abgestellt wird, bekommt
 * laufend Treibstoff, neue Munition und Reparaturen - der Boxenstopp der Basis.
 */
public class RearmPadBlock extends Block {
	public static final MapCodec<RearmPadBlock> CODEC = createCodec(RearmPadBlock::new);

	public RearmPadBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<? extends Block> getCodec() {
		return CODEC;
	}

	@Override
	protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
		if (world.isClient || !(entity instanceof F47Entity jet)) {
			return;
		}
		// Nur am Boden und im Stand wird gewartet.
		if (jet.getVelocity().horizontalLengthSquared() > 0.02) {
			return;
		}
		if (!jet.rearmAndRefuel()) {
			return;
		}
		if (world.getTime() % 20 == 0) {
			world.playSound(null, pos, SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.BLOCKS, 0.3f, 1.8f);
			if (world instanceof ServerWorld server) {
				server.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
						pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 4, 0.5, 0.2, 0.5, 0.0);
			}
		}
	}

	/** Ist der Jet auf diesem Feld voll einsatzbereit? */
	public static boolean isFullyServiced(F47Entity jet) {
		F47Config config = F47Config.get();
		return jet.getFuel() >= config.maxFuel
				&& jet.getStructure() >= config.jetMaxHealth
				&& jet.getMissileAmmo() >= F47Entity.MAX_MISSILES;
	}
}
