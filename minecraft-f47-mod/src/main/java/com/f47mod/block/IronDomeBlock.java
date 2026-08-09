package com.f47mod.block;

import com.f47mod.block.entity.IronDomeBlockEntity;
import com.f47mod.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Iron-Dome-Startstellung. Verfolgt anfliegende Raketen, Feuerbaelle und
 * Drohnen und schiesst sie mit Abfangraketen ab, bevor sie die Basis treffen.
 * Munition wird per Rechtsklick eingefuellt.
 */
public class IronDomeBlock extends BlockWithEntity {
	public static final MapCodec<IronDomeBlock> CODEC = createCodec(IronDomeBlock::new);

	public IronDomeBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Override
	public void onPlaced(World world, BlockPos pos, BlockState state,
			@Nullable net.minecraft.entity.LivingEntity placer, net.minecraft.item.ItemStack stack) {
		super.onPlaced(world, pos, state, placer, stack);
		// Die Anlage gehoert der Partei dessen, der sie aufstellt.
		if (!world.isClient && placer instanceof PlayerEntity player
				&& world.getBlockEntity(pos) instanceof com.f47mod.util.TeamMember member) {
			member.setTeam(com.f47mod.util.TeamState.teamOf(player));
		}
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new IronDomeBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state,
			BlockEntityType<T> type) {
		if (world.isClient) {
			return null;
		}
		return validateTicker(type, ModBlockEntities.IRON_DOME, IronDomeBlockEntity::serverTick);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
			BlockHitResult hit) {
		if (world.isClient) {
			return ActionResult.SUCCESS;
		}
		if (!(world.getBlockEntity(pos) instanceof IronDomeBlockEntity launcher)) {
			return ActionResult.PASS;
		}

		ItemStack held = player.getMainHandStack();
		if (held.isOf(com.f47mod.registry.ModItems.INTERCEPTOR_MISSILE)) {
			int loaded = launcher.load(held.getCount());
			if (loaded > 0) {
				if (!player.getAbilities().creativeMode) {
					held.decrement(loaded);
				}
				player.sendMessage(Text.translatable("message.f47.dome_loaded",
						loaded, launcher.getAmmo(), IronDomeBlockEntity.MAX_AMMO), true);
				return ActionResult.CONSUME;
			}
			player.sendMessage(Text.translatable("message.f47.dome_full"), true);
			return ActionResult.CONSUME;
		}

		player.sendMessage(Text.translatable("message.f47.dome_status",
				launcher.getAmmo(), IronDomeBlockEntity.MAX_AMMO, launcher.getInterceptCount()), true);
		return ActionResult.CONSUME;
	}

	@Override
	protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock()) && !world.isClient) {
			// Restmunition faellt beim Abbau heraus.
			if (world.getBlockEntity(pos) instanceof IronDomeBlockEntity launcher && launcher.getAmmo() > 0) {
				ItemStack drop = new ItemStack(com.f47mod.registry.ModItems.INTERCEPTOR_MISSILE, launcher.getAmmo());
				world.spawnEntity(new ItemEntity(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop));
			}
		}
		super.onStateReplaced(state, world, pos, newState, moved);
	}
}
