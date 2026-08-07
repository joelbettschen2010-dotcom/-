package com.f47mod.util;

import com.f47mod.F47Config;
import com.f47mod.entity.mob.CombatDroneEntity;
import com.f47mod.entity.mob.SoldierEntity;
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
import org.jetbrains.annotations.Nullable;

/**
 * IFF - "Identification Friend or Foe". Entscheidet, wen eine Einheit angreifen
 * darf und wen nicht.
 *
 * <p>Die Regeln sind bewusst einfach gehalten:
 * <ul>
 *   <li>Gleiche Partei - nie ein Ziel, auch nicht versehentlich.</li>
 *   <li>Andere Partei - gegnerisch.</li>
 *   <li>Monster der Welt (Zombies, Ghasts, ...) - Gegner beider Parteien.
 *       Deshalb funktioniert der Mod auch dann, wenn nur eine Partei
 *       aufgestellt wird.</li>
 *   <li>Tiere und Spieler ohne Beteiligung - kein Ziel.</li>
 * </ul>
 */
public final class Iff {
	private Iff() {
	}

	/** Partei einer Entity, oder null bei allem, was zu keiner Partei gehoert. */
	@Nullable
	public static Team teamOf(@Nullable Entity entity) {
		if (entity instanceof TeamMember member) {
			return member.getTeam();
		}
		if (entity instanceof PlayerEntity player) {
			return TeamState.teamOf(player);
		}
		return null;
	}

	/** Gehoeren beide zur selben Partei? */
	public static boolean sameTeam(@Nullable Entity a, @Nullable Entity b) {
		Team teamA = teamOf(a);
		return teamA != null && teamA == teamOf(b);
	}

	/** Verbuendeter des Betrachters - wird nie beschossen. */
	public static boolean isAlly(@Nullable Team viewer, @Nullable Entity candidate) {
		return viewer != null && teamOf(candidate) == viewer;
	}

	public static boolean isAlly(@Nullable Entity viewer, @Nullable Entity candidate) {
		return isAlly(teamOf(viewer), candidate);
	}

	/**
	 * Gueltiges Ziel fuer den Betrachter: die Gegenpartei oder ein Monster der
	 * Welt. Tiere, Dorfbewohner und Unbeteiligte bleiben aussen vor.
	 */
	public static boolean isEnemy(@Nullable Team viewer, @Nullable Entity candidate) {
		if (candidate == null || !candidate.isAlive()) {
			return false;
		}
		Team other = teamOf(candidate);
		if (other != null) {
			return viewer != null && other != viewer;
		}
		// Ohne Partei: nur die feindlichen Kreaturen der Welt sind Ziele.
		return candidate instanceof HostileEntity || isHostileProjectile(candidate);
	}

	public static boolean isEnemy(@Nullable Entity viewer, @Nullable Entity candidate) {
		if (viewer == candidate) {
			return false;
		}
		return isEnemy(teamOf(viewer), candidate);
	}

	/** Geschosse, die niemandem aus dem Mod gehoeren - etwa Ghast-Feuerbaelle. */
	private static boolean isHostileProjectile(Entity entity) {
		if (entity instanceof ExplosiveProjectileEntity projectile) {
			return !(projectile.getOwner() instanceof PlayerEntity);
		}
		if (entity instanceof PersistentProjectileEntity arrow) {
			return !(arrow.getOwner() instanceof PlayerEntity) && !(arrow.getOwner() instanceof SoldierEntity);
		}
		return false;
	}

	/**
	 * Luftziel - alles, worauf Flugabwehr und Abfangjaeger ansetzen: gegnerische
	 * Raketen und Drohnen, Ghast-Feuerbaelle, fliegende Monster.
	 */
	public static boolean isAirThreat(@Nullable Team viewer, @Nullable Entity candidate) {
		if (!isEnemy(viewer, candidate)) {
			return false;
		}
		// Eine gegnerische Abfangrakete greift niemanden an - sie ist selbst
		// nur Abwehr. Wer auf sie schiesst, verschiesst seine Stellung an
		// einem Ziel, das ihm nie gefaehrlich wird.
		if (candidate instanceof com.f47mod.entity.projectile.MissileEntity missile
				&& missile.getKind() == com.f47mod.entity.projectile.MissileEntity.Kind.INTERCEPTOR) {
			return false;
		}
		if (candidate instanceof CombatDroneEntity
				|| candidate instanceof com.f47mod.entity.projectile.MissileEntity
				|| candidate instanceof ExplosiveProjectileEntity
				|| candidate instanceof PersistentProjectileEntity
				|| candidate instanceof FlyingEntity
				|| candidate instanceof PhantomEntity
				|| candidate instanceof F47Entity) {
			return true;
		}
		// Bodenmonster zaehlen nur, solange sie sich in der Luft befinden.
		return !candidate.isOnGround();
	}

	/** Bedrohungsbewertung - hoehere Werte werden zuerst abgefangen. */
	public static int threatPriority(Entity entity) {
		if (entity instanceof com.f47mod.entity.projectile.MissileEntity) {
			return 100;
		}
		if (entity instanceof ExplosiveProjectileEntity) {
			return 90;
		}
		if (entity instanceof CombatDroneEntity) {
			return 70;
		}
		if (entity instanceof F47Entity) {
			return 65;
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

	/** Laesst sich das Ziel aus dieser Entfernung ueberhaupt erfassen? */
	public static boolean canDetect(Entity target, Vec3d observerPos, double baseRange) {
		double range = detectionRange(target, baseRange);
		return target.squaredDistanceTo(observerPos) <= range * range;
	}

	/** Verwundete Verbuendete, die ein Sanitaeter versorgen darf. */
	public static boolean isHealTarget(@Nullable Team viewer, Entity entity) {
		if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
			return false;
		}
		if (!(living instanceof SoldierEntity || living instanceof PlayerEntity)) {
			return false;
		}
		return isAlly(viewer, living) && living.getHealth() < living.getMaxHealth();
	}
}
