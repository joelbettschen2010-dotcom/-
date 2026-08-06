package com.f47mod.block;

import com.f47mod.block.entity.BarracksBlockEntity;
import com.f47mod.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Kaserne. Nimmt Eisenbarren als Nachschub an und bildet daraus mit der Zeit
 * neue Soldaten aus. Mit Rechtsklick laesst sich einstellen, welche Rolle als
 * naechstes ausgebildet wird.
 */
public class BarracksBlock extends BlockWithEntity {
	public static final MapCodec<BarracksBlock> CODEC = createCodec(BarracksBlock::new);

	public BarracksBlock(Settings settings) {
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

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new BarracksBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state,
			BlockEntityType<T> type) {
		if (world.isClient) {
			return null;
		}
		return validateTicker(type, ModBlockEntities.BARRACKS, BarracksBlockEntity::serverTick);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
			BlockHitResult hit) {
		if (world.isClient) {
			return ActionResult.SUCCESS;
		}
		if (!(world.getBlockEntity(pos) instanceof BarracksBlockEntity barracks)) {
			return ActionResult.PASS;
		}

		ItemStack held = player.getMainHandStack();
		if (held.isOf(Items.IRON_INGOT)) {
			int accepted = barracks.addSupplies(held.getCount());
			if (accepted > 0) {
				if (!player.getAbilities().creativeMode) {
					held.decrement(accepted);
				}
				player.sendMessage(Text.translatable("message.f47.barracks_supplied",
						barracks.getSupplies(), BarracksBlockEntity.MAX_SUPPLIES), true);
				return ActionResult.CONSUME;
			}
		}

		if (player.isSneaking()) {
			var role = barracks.cycleRole();
			player.sendMessage(Text.translatable("message.f47.barracks_role",
					Text.translatable(role.translationKey())), true);
			return ActionResult.CONSUME;
		}

		barracks.setOwner(player);
		player.sendMessage(Text.translatable("message.f47.barracks_status",
				Text.translatable(barracks.getTrainingRole().translationKey()),
				barracks.getSupplies(),
				barracks.getSquadSize(),
				BarracksBlockEntity.MAX_SQUAD), true);
		return ActionResult.CONSUME;
	}
}
