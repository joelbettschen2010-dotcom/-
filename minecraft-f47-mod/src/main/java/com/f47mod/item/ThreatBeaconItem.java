package com.f47mod.item;

import com.f47mod.F47Config;
import com.f47mod.entity.mob.EnemyDroneEntity;
import com.f47mod.registry.ModEntities;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/**
 * Uebungsalarm. Ruft eine Welle feindlicher Drohnen herbei - damit lassen sich
 * Iron Dome, Abfangjaeger und Bodentruppen gezielt auf die Probe stellen,
 * ohne auf einen zufaelligen Angriff warten zu muessen.
 */
public class ThreatBeaconItem extends Item {
	public ThreatBeaconItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
		ItemStack stack = player.getStackInHand(hand);
		if (world.isClient) {
			return TypedActionResult.success(stack);
		}
		if (!(world instanceof ServerWorld server)) {
			return TypedActionResult.pass(stack);
		}

		int count = F47Config.get().raidDroneCount;
		int spawned = 0;
		for (int i = 0; i < count; i++) {
			EnemyDroneEntity drone = ModEntities.ENEMY_DRONE.create(world);
			if (drone == null) {
				continue;
			}
			// Anflug aus einiger Entfernung und aus grosser Hoehe.
			double angle = (Math.PI * 2.0 / count) * i + world.getRandom().nextDouble() * 0.4;
			double distance = 70.0 + world.getRandom().nextDouble() * 30.0;
			double x = player.getX() + Math.cos(angle) * distance;
			double z = player.getZ() + Math.sin(angle) * distance;
			double y = player.getY() + 28.0 + world.getRandom().nextDouble() * 12.0;

			drone.refreshPositionAndAngles(x, y, z, world.getRandom().nextFloat() * 360.0f, 0.0f);
			drone.initialize(server, world.getLocalDifficulty(drone.getBlockPos()), SpawnReason.EVENT, null);
			drone.setTarget(player);
			world.spawnEntity(drone);
			spawned++;
		}

		world.playSound(null, player.getBlockPos(), SoundEvents.EVENT_RAID_HORN.value(),
				SoundCategory.HOSTILE, 3.0f, 0.8f);
		player.sendMessage(Text.translatable("message.f47.raid_incoming", spawned)
				.formatted(Formatting.RED, Formatting.BOLD), false);

		if (!player.getAbilities().creativeMode) {
			stack.decrement(1);
		}
		return TypedActionResult.consume(stack);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.f47.threat_beacon").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.f47.threat_beacon_hint").formatted(Formatting.DARK_GRAY));
	}
}
