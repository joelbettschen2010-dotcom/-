package com.f47mod.world;

import com.f47mod.F47Config;
import com.f47mod.block.entity.RadarBlockEntity;
import com.f47mod.entity.vehicle.F47Entity;
import com.f47mod.net.ModNetworking;
import com.f47mod.net.RadarContact;
import com.f47mod.registry.ModItems;
import com.f47mod.util.Iff;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Schickt das Radarbild an die Spieler. Zwei Quellen speisen den Schirm:
 * das Bordradar im Cockpit und die Radarstationen der Basis, die ihr Bild an
 * jeden Spieler mit Kommando-Tablet weitergeben.
 */
public final class RadarSyncHandler {
	/** Abstand zwischen zwei Aktualisierungen. Haeufiger waere Verschwendung. */
	private static final int SYNC_INTERVAL = 10;

	private RadarSyncHandler() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTicks() % SYNC_INTERVAL != 0) {
				return;
			}
			for (ServerWorld world : server.getWorlds()) {
				for (ServerPlayerEntity player : world.getPlayers()) {
					List<RadarContact> contacts = collect(player);
					if (contacts != null) {
						ModNetworking.sendRadar(player, contacts);
					}
				}
			}
		});
	}

	/** Kontakte fuer diesen Spieler - oder null, wenn er gar kein Radar hat. */
	private static List<RadarContact> collect(ServerPlayerEntity player) {
		boolean inJet = player.getVehicle() instanceof F47Entity;
		boolean hasTablet = player.getInventory().contains(
				stack -> stack.isOf(ModItems.COMMAND_TABLET));

		if (!inJet && !hasTablet) {
			return null;
		}

		List<RadarContact> contacts = new ArrayList<>();
		Vec3d origin = player.getPos();

		if (inJet) {
			// Bordradar: eigener Umkreis um das Flugzeug.
			int range = F47Config.get().jetRadarRange;
			collectAround(player, origin, range, contacts);
		}

		if (hasTablet) {
			// Bodenradar: Bild der naechsten Station dazunehmen.
			RadarBlockEntity radar = RadarBlockEntity.findNearby(player.getWorld(), player.getBlockPos(), 96);
			if (radar != null) {
				for (RadarContact contact : radar.getContacts()) {
					if (contacts.stream().noneMatch(existing -> existing.entityId() == contact.entityId())) {
						contacts.add(contact);
					}
				}
			}
		}

		return contacts;
	}

	private static void collectAround(ServerPlayerEntity player, Vec3d origin, int range,
			List<RadarContact> contacts) {
		Box box = player.getBoundingBox().expand(range);
		for (Entity entity : player.getWorld().getOtherEntities(player, box, RadarSyncHandler::isInteresting)) {
			if (entity == player.getVehicle()) {
				continue;
			}
			if (!Iff.canDetect(entity, origin, range)) {
				continue;
			}
			contacts.add(RadarContact.of(entity));
		}
	}

	private static boolean isInteresting(Entity entity) {
		if (Iff.isThreat(entity)) {
			return true;
		}
		// Eigene Flugzeuge zeigt der Schirm als befreundete Kontakte.
		return entity instanceof F47Entity;
	}
}
