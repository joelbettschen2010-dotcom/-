package com.f47mod.net;

import com.f47mod.entity.mob.CombatDroneEntity;
import com.f47mod.entity.projectile.MissileEntity;
import com.f47mod.entity.vehicle.AutonomousF47Entity;
import com.f47mod.entity.vehicle.F47Entity;
import com.f47mod.util.Iff;
import com.f47mod.util.Team;
import net.minecraft.entity.Entity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.util.math.Vec3d;

/**
 * Ein einzelner Radarkontakt, wie ihn Radarstation und Cockpit-Radar anzeigen.
 *
 * @param entityId Entity-Nummer, damit sich Kontakte wiedererkennen lassen
 * @param x        Weltkoordinate
 * @param y        Hoehe ueber Grund - fuer die Hoehenanzeige am Schirm
 * @param z        Weltkoordinate
 * @param hostile  feindlich (rot) oder eigen (gruen)
 * @param type     Symbol auf dem Schirm
 */
public record RadarContact(int entityId, double x, double y, double z, boolean hostile, ContactType type) {

	public enum ContactType {
		/** Eigener oder fremder Kampfjet. */
		AIRCRAFT,
		/** Feindliche Drohne. */
		DRONE,
		/** Anfliegende Rakete oder Geschoss. */
		MISSILE,
		/** Bodenziel. */
		GROUND
	}

	public static final PacketCodec<RegistryByteBuf, RadarContact> CODEC =
			PacketCodec.of(RadarContact::write, RadarContact::new);

	public RadarContact(RegistryByteBuf buf) {
		this(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
				buf.readBoolean(), ContactType.values()[buf.readVarInt()]);
	}

	public void write(RegistryByteBuf buf) {
		buf.writeVarInt(entityId);
		buf.writeDouble(x);
		buf.writeDouble(y);
		buf.writeDouble(z);
		buf.writeBoolean(hostile);
		buf.writeVarInt(type.ordinal());
	}

	/** Kontakt aus Sicht einer bestimmten Partei bewerten. */
	public static RadarContact of(Entity entity, @org.jetbrains.annotations.Nullable Team viewer) {
		return new RadarContact(
				entity.getId(),
				entity.getX(), entity.getY(), entity.getZ(),
				Iff.isEnemy(viewer, entity),
				classify(entity));
	}

	private static ContactType classify(Entity entity) {
		if (entity instanceof MissileEntity) {
			return ContactType.MISSILE;
		}
		if (entity instanceof CombatDroneEntity) {
			return ContactType.DRONE;
		}
		if (entity instanceof F47Entity || entity instanceof AutonomousF47Entity) {
			return ContactType.AIRCRAFT;
		}
		if (entity instanceof net.minecraft.entity.projectile.ProjectileEntity) {
			return ContactType.MISSILE;
		}
		return ContactType.GROUND;
	}

	public Vec3d pos() {
		return new Vec3d(x, y, z);
	}
}
