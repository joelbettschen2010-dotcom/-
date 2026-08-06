package com.f47mod.util;

import com.f47mod.F47Config;
import com.f47mod.entity.mob.EnemyDroneEntity;
import com.f47mod.entity.mob.SoldierEntity;
import com.f47mod.entity.projectile.MissileEntity;
import com.f47mod.entity.vehicle.F47Entity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.FlyingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ExplosiveProjectileEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.util.math.Vec3d;

/**
 * IFF - "Identification Friend or Foe". Zentrale Stelle, die entscheidet, wen
 * die eigenen Einheiten (Jets, Iron Dome, Soldaten) angreifen duerfen und wen
 * nicht. Ohne das schiesst die Flugabwehr auf die eigenen Flugzeuge.
 */
public final class Iff {
	private Iff() {
	}

	/** Gehoert die Entity zur eigenen Seite? Wird niemals beschossen. */
	public static boolean isFriendly(Entity entity) {
		if (entity instanceof PlayerEntity) {
			return true;
		}
		if (entity instanceof F47Entity) {
			return true;
		}
		if (entity instanceof SoldierEntity) {
			return true;
		}
		if (entity instanceof MissileEntity missile) {
			return !missile.isHostile();
		}
		// Tiere und Dekoration gehoeren nicht zum Verband - sie werden von der
		// Zielerfassung ignoriert, sind aber auch nicht besonders geschuetzt.
		return false;
	}

	/** Feindliche Einheit, die von eigenen Truppen bekaempft werden darf. */
	public static boolean isThreat(Entity entity) {
		if (entity == null || !entity.isAlive() || isFriendly(entity)) {
			return false;
		}
		if (entity instanceof EnemyDroneEntity) {
			return true;
		}
		if (entity instanceof MissileEntity missile) {
			return missile.isHostile();
		}
		return entity instanceof HostileEntity;
	}

	/**
	 * Luftziel - alles, worauf die Flugabwehr (Iron Dome) und die Jets
	 * ansetzen. Dazu zaehlen auch anfliegende Geschosse wie Ghast-Feuerbaelle.
	 */
	public static boolean isAirThreat(Entity entity) {
		if (entity == null || !entity.isAlive() || isFriendly(entity)) {
			return false;
		}
		if (entity instanceof EnemyDroneEntity) {
			return true;
		}
		if (entity instanceof MissileEntity missile) {
			return missile.isHostile();
		}
		if (entity instanceof ExplosiveProjectileEntity projectile) {
			// Ghast-Feuerbaelle, Witherschaedel - klassische Iron-Dome-Ziele.
			return !(projectile.getOwner() instanceof PlayerEntity);
		}
		if (entity instanceof PersistentProjectileEntity arrow) {
			return !(arrow.getOwner() instanceof PlayerEntity) && !(arrow.getOwner() instanceof SoldierEntity);
		}
		if (entity instanceof FlyingEntity || entity instanceof PhantomEntity) {
			return true;
		}
		// Fliegende oder gerade springende Monster in der Luft.
		return entity instanceof HostileEntity && !entity.isOnGround();
	}

	/** Bedrohungsbewertung - hoehere Werte werden zuerst abgefangen. */
	public static int threatPriority(Entity entity) {
		if (entity instanceof MissileEntity) {
			return 100;
		}
		if (entity instanceof ExplosiveProjectileEntity) {
			return 90;
		}
		if (entity instanceof EnemyDroneEntity) {
			return 70;
		}
		if (entity instanceof CreeperEntity) {
			return 60;
		}
		if (entity instanceof PersistentProjectileEntity) {
			return 50;
		}
		return 10;
	}

	/**
	 * Effektive Ortungsreichweite auf ein Ziel. Getarnte Jets werden erst sehr
	 * spaet erfasst - das ist der ganze Sinn von Stealth.
	 */
	public static double detectionRange(Entity target, double baseRange) {
		if (target instanceof F47Entity jet && jet.isStealthEffective()) {
			return baseRange * F47Config.get().stealthDetectionFactor;
		}
		return baseRange;
	}

	/** Kann {@code observer} das Ziel auf diese Entfernung ueberhaupt erfassen? */
	public static boolean canDetect(Entity target, Vec3d observerPos, double baseRange) {
		double range = detectionRange(target, baseRange);
		return target.squaredDistanceTo(observerPos) <= range * range;
	}

	/** Lebende Ziele, die geheilt werden duerfen (Sanitaeter). */
	public static boolean isHealTarget(Entity entity) {
		return entity instanceof LivingEntity living
				&& living.isAlive()
				&& (living instanceof SoldierEntity || living instanceof PlayerEntity)
				&& living.getHealth() < living.getMaxHealth();
	}
}
