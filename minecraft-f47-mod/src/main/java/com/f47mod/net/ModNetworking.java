package com.f47mod.net;

import com.f47mod.F47Mod;
import com.f47mod.entity.vehicle.F47Entity;
import com.f47mod.entity.vehicle.WeaponType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Datenaustausch zwischen Client und Server.
 *
 * <p>Der Client des Piloten rechnet die Flugbewegung selbst - das haelt die
 * Steuerung reaktionsschnell. Er meldet dem Server aber laufend Schubstellung,
 * Querlage und Schalterstellungen, damit dieser Treibstoff, Zielerfassung und
 * Waffeneinsatz korrekt verwaltet. Ausgeloest wird geschossen immer serverseitig.
 */
public final class ModNetworking {
	private ModNetworking() {
	}

	// ------------------------------------------------------------------
	// Client -> Server: Steuereingaben
	// ------------------------------------------------------------------

	/**
	 * Zustand der Cockpit-Bedienelemente.
	 *
	 * @param throttle    Schubhebel 0..1
	 * @param roll        Querlage in Grad (nur zur Darstellung)
	 * @param stealth     Tarnkappenmodus an
	 * @param afterburner Nachbrenner an
	 */
	public record JetControlPayload(float throttle, float roll, boolean stealth, boolean afterburner)
			implements CustomPayload {
		public static final CustomPayload.Id<JetControlPayload> ID =
				new CustomPayload.Id<>(Identifier.of(F47Mod.MOD_ID, "jet_control"));
		public static final PacketCodec<RegistryByteBuf, JetControlPayload> CODEC =
				PacketCodec.of(JetControlPayload::write, JetControlPayload::new);

		public JetControlPayload(RegistryByteBuf buf) {
			this(buf.readFloat(), buf.readFloat(), buf.readBoolean(), buf.readBoolean());
		}

		public void write(RegistryByteBuf buf) {
			buf.writeFloat(throttle);
			buf.writeFloat(roll);
			buf.writeBoolean(stealth);
			buf.writeBoolean(afterburner);
		}

		@Override
		public Id<JetControlPayload> getId() {
			return ID;
		}
	}

	/** Waffenausloesung. Der Server prueft Munition und Nachladezeit selbst. */
	public record FireWeaponPayload(int weapon) implements CustomPayload {
		public static final CustomPayload.Id<FireWeaponPayload> ID =
				new CustomPayload.Id<>(Identifier.of(F47Mod.MOD_ID, "fire_weapon"));
		public static final PacketCodec<RegistryByteBuf, FireWeaponPayload> CODEC =
				PacketCodec.of(FireWeaponPayload::write, FireWeaponPayload::new);

		public FireWeaponPayload(RegistryByteBuf buf) {
			this(buf.readVarInt());
		}

		public void write(RegistryByteBuf buf) {
			buf.writeVarInt(weapon);
		}

		@Override
		public Id<FireWeaponPayload> getId() {
			return ID;
		}
	}

	/** Waffenwahl im Cockpit weiterschalten. */
	public record CycleWeaponPayload() implements CustomPayload {
		public static final CustomPayload.Id<CycleWeaponPayload> ID =
				new CustomPayload.Id<>(Identifier.of(F47Mod.MOD_ID, "cycle_weapon"));
		public static final PacketCodec<RegistryByteBuf, CycleWeaponPayload> CODEC =
				PacketCodec.unit(new CycleWeaponPayload());

		@Override
		public Id<CycleWeaponPayload> getId() {
			return ID;
		}
	}

	// ------------------------------------------------------------------
	// Server -> Client: Radarbild
	// ------------------------------------------------------------------

	/**
	 * Aktuelle Radarkontakte fuer die Anzeige im HUD. Der Server bewertet die
	 * Kontakte bereits aus Sicht des Empfaengers und schickt dessen Partei mit,
	 * damit das HUD sie anzeigen kann.
	 */
	public record RadarUpdatePayload(List<RadarContact> contacts, int viewerTeam) implements CustomPayload {
		public static final CustomPayload.Id<RadarUpdatePayload> ID =
				new CustomPayload.Id<>(Identifier.of(F47Mod.MOD_ID, "radar_update"));
		public static final PacketCodec<RegistryByteBuf, RadarUpdatePayload> CODEC =
				PacketCodec.of(RadarUpdatePayload::write, RadarUpdatePayload::new);

		public RadarUpdatePayload(RegistryByteBuf buf) {
			this(readContacts(buf), buf.readVarInt());
		}

		private static List<RadarContact> readContacts(RegistryByteBuf buf) {
			int count = buf.readVarInt();
			List<RadarContact> list = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				list.add(new RadarContact(buf));
			}
			return list;
		}

		public void write(RegistryByteBuf buf) {
			buf.writeVarInt(contacts.size());
			for (RadarContact contact : contacts) {
				contact.write(buf);
			}
			buf.writeVarInt(viewerTeam);
		}

		@Override
		public Id<RadarUpdatePayload> getId() {
			return ID;
		}
	}

	/** Anmeldung der Pakettypen - muss auf beiden Seiten passieren. */
	public static void registerPayloads() {
		PayloadTypeRegistry.playC2S().register(JetControlPayload.ID, JetControlPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(FireWeaponPayload.ID, FireWeaponPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(CycleWeaponPayload.ID, CycleWeaponPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(RadarUpdatePayload.ID, RadarUpdatePayload.CODEC);
	}

	/** Empfaenger auf dem Server. */
	public static void registerServerReceivers() {
		ServerPlayNetworking.registerGlobalReceiver(JetControlPayload.ID, (payload, context) ->
				context.player().server.execute(() -> {
					if (getJet(context.player()) instanceof F47Entity jet) {
						jet.setThrottle(payload.throttle());
						jet.setRoll(payload.roll());
						jet.setStealth(payload.stealth());
						jet.setAfterburner(payload.afterburner() && jet.getFuel() > 0.0f);
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(FireWeaponPayload.ID, (payload, context) ->
				context.player().server.execute(() -> {
					if (getJet(context.player()) instanceof F47Entity jet) {
						jet.fireWeapon(WeaponType.byOrdinal(payload.weapon()));
					}
				}));

		ServerPlayNetworking.registerGlobalReceiver(CycleWeaponPayload.ID, (payload, context) ->
				context.player().server.execute(() -> {
					if (getJet(context.player()) instanceof F47Entity jet) {
						jet.cycleWeapon();
					}
				}));
	}

	/** Der Jet, in dem der Spieler gerade sitzt - oder null. */
	private static F47Entity getJet(ServerPlayerEntity player) {
		return player.getVehicle() instanceof F47Entity jet ? jet : null;
	}

	/** Radarbild an einen Spieler schicken. */
	public static void sendRadar(ServerPlayerEntity player, List<RadarContact> contacts,
			com.f47mod.util.Team viewerTeam) {
		ServerPlayNetworking.send(player, new RadarUpdatePayload(contacts, viewerTeam.ordinal()));
	}
}
