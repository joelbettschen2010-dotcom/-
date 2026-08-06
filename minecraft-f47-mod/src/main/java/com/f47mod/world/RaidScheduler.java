package com.f47mod.world;

import com.f47mod.F47Config;
import com.f47mod.block.entity.RadarBlockEntity;
import com.f47mod.entity.mob.EnemyDroneEntity;
import com.f47mod.registry.ModEntities;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.SpawnReason;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.Difficulty;

/**
 * Gelegentliche Drohnenangriffe auf die Basis. Ohne Gegner waeren Iron Dome
 * und Abfangjaeger nur Deko - hier bekommen sie ihre Aufgabe.
 *
 * <p>Angriffe kommen nur nachts, nur wenn eine Radarstation in der Naehe steht
 * (also tatsaechlich eine Basis existiert) und lassen sich in der Konfiguration
 * ganz abschalten.
 */
public final class RaidScheduler {
	private static int cooldown = 6000;

	private RaidScheduler() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			F47Config config = F47Config.get();
			if (!config.enableRandomRaids) {
				return;
			}
			// Nur einmal pro Sekunde pruefen.
			if (server.getTicks() % 20 != 0) {
				return;
			}
			if (--cooldown > 0) {
				return;
			}
			cooldown = config.raidIntervalTicks / 20;

			for (ServerWorld world : server.getWorlds()) {
				if (world.getDifficulty() == Difficulty.PEACEFUL || !world.isNight()) {
					continue;
				}
				for (ServerPlayerEntity player : world.getPlayers()) {
					tryRaid(world, player, config);
				}
			}
		});
	}

	private static void tryRaid(ServerWorld world, ServerPlayerEntity player, F47Config config) {
		// Ohne Radarstation gibt es keine Basis, die angegriffen werden koennte.
		RadarBlockEntity radar = RadarBlockEntity.findNearby(world, player.getBlockPos(), 96);
		if (radar == null) {
			return;
		}

		int spawned = 0;
		for (int i = 0; i < config.raidDroneCount; i++) {
			EnemyDroneEntity drone = ModEntities.ENEMY_DRONE.create(world);
			if (drone == null) {
				continue;
			}
			double angle = world.getRandom().nextDouble() * Math.PI * 2.0;
			double distance = 90.0 + world.getRandom().nextDouble() * 40.0;
			double x = player.getX() + Math.cos(angle) * distance;
			double z = player.getZ() + Math.sin(angle) * distance;
			double y = Math.min(world.getTopY() - 6, player.getY() + 30.0 + world.getRandom().nextDouble() * 15.0);

			drone.refreshPositionAndAngles(x, y, z, world.getRandom().nextFloat() * 360.0f, 0.0f);
			drone.initialize(world, world.getLocalDifficulty(drone.getBlockPos()), SpawnReason.EVENT, null);
			world.spawnEntity(drone);
			spawned++;
		}

		if (spawned > 0) {
			player.sendMessage(Text.translatable("message.f47.raid_warning", spawned)
					.formatted(Formatting.RED), false);
			world.playSound(null, player.getBlockPos(), SoundEvents.EVENT_RAID_HORN.value(),
					SoundCategory.HOSTILE, 2.0f, 0.7f);
		}
	}
}
