package com.f47mod.block;

import com.f47mod.block.entity.RadarBlockEntity;
import com.f47mod.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Luftraumradar. Erfasst Flugzeuge, Drohnen und anfliegende Geschosse im
 * Umkreis, meldet sie an den Spieler (Kommando-Tablet, Cockpit-HUD) und
 * versorgt die Iron-Dome-Stellungen mit Zieldaten.
 */
public class RadarBlock extends BlockWithEntity {
	public static final MapCodec<RadarBlock> CODEC = createCodec(RadarBlock::new);
	public static final BooleanProperty ACTIVE = BooleanProperty.of("active");

	public RadarBlock(Settings settings) {
		super(settings);
		setDefaultState(getDefaultState().with(ACTIVE, true));
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(ACTIVE);
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		// Der Sockel wird als normales Modell gezeichnet, die Schuessel dreht
		// sich zusaetzlich im BlockEntityRenderer.
		return BlockRenderType.MODEL;
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new RadarBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state,
			BlockEntityType<T> type) {
		if (world.isClient) {
			return validateTicker(type, ModBlockEntities.RADAR, RadarBlockEntity::clientTick);
		}
		return validateTicker(type, ModBlockEntities.RADAR, RadarBlockEntity::serverTick);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
			BlockHitResult hit) {
		if (world.isClient) {
			return ActionResult.SUCCESS;
		}
		boolean active = !state.get(ACTIVE);
		world.setBlockState(pos, state.with(ACTIVE, active), Block.NOTIFY_ALL);
		world.playSound(null, pos, active ? SoundEvents.BLOCK_BEACON_ACTIVATE : SoundEvents.BLOCK_BEACON_DEACTIVATE,
				SoundCategory.BLOCKS, 0.6f, 1.4f);

		if (world.getBlockEntity(pos) instanceof RadarBlockEntity radar) {
			player.sendMessage(Text.translatable(
					active ? "message.f47.radar_online" : "message.f47.radar_offline",
					radar.getContactCount()), true);
		}
		return ActionResult.CONSUME;
	}
}
