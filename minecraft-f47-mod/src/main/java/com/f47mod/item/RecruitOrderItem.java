package com.f47mod.item;

import com.f47mod.entity.mob.SoldierEntity;
import com.f47mod.entity.mob.SoldierRole;
import com.f47mod.registry.ModEntities;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/** Marschbefehl - stellt einen Soldaten der jeweiligen Rolle auf. */
public class RecruitOrderItem extends Item {
	private final SoldierRole role;

	public RecruitOrderItem(Settings settings, SoldierRole role) {
		super(settings);
		this.role = role;
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		World world = context.getWorld();
		if (world.isClient) {
			return ActionResult.SUCCESS;
		}
		BlockPos pos = context.getBlockPos().up();
		PlayerEntity player = context.getPlayer();

		SoldierEntity soldier = ModEntities.SOLDIER.create(world);
		if (soldier == null) {
			return ActionResult.FAIL;
		}
		soldier.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
				player == null ? 0.0f : player.getYaw(), 0.0f);
		if (world instanceof ServerWorld server) {
			soldier.initialize(server, world.getLocalDifficulty(pos), SpawnReason.TRIGGERED, null);
		}
		// Rolle nach der Initialisierung setzen, damit Werte und Ausruestung stimmen.
		soldier.setRole(role);
		soldier.setOwner(player);
		soldier.setStance(SoldierEntity.Stance.FOLLOW);
		world.spawnEntity(soldier);

		world.playSound(null, pos, SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.NEUTRAL, 0.5f, 1.4f);
		if (player != null) {
			player.sendMessage(Text.translatable("message.f47.recruit_ready",
					Text.translatable(role.translationKey())), true);
			if (!player.getAbilities().creativeMode) {
				context.getStack().decrement(1);
			}
		}
		return ActionResult.CONSUME;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.f47.recruit", Text.translatable(role.translationKey()))
				.formatted(Formatting.GRAY));
	}
}
