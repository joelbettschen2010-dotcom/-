package com.f47mod.entity.vehicle;

import com.f47mod.F47Config;
import com.f47mod.util.Iff;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * B-21 - autonomer Tarnkappenbomber mit Radarrolle.
 *
 * <p>Zwei Dinge unterscheiden sie von der F-47:
 *
 * <ul>
 *   <li><b>Kaum zu orten.</b> Die Tarnung ist dauerhaft an und wirkt deutlich
 *       staerker als bei einem Jaeger. Gegnerische Flugabwehr bemerkt die
 *       Maschine erst, wenn sie fast ueber der Stellung steht - und wenn sie
 *       die Klappen zum Abwurf oeffnet.</li>
 *   <li><b>Sie sucht Bodenziele.</b> Ein Jaeger geht auf alles los, was
 *       fliegt; die B-21 fliegt an Luftzielen vorbei und wirft ihre Last auf
 *       Stellungen, Kasernen und Fahrzeuge ab.</li>
 * </ul>
 *
 * <p>Zugleich ist sie das fliegende Radar des Verbandes: Ihre eigene
 * Ortungsreichweite ist doppelt so gross wie die eines Jaegers, sie findet
 * also Ziele, die sonst niemand sieht.
 */
public class B21Entity extends AutonomousF47Entity {
	/** Faktor auf die gegnerische Ortungsreichweite. Deutlich unter der F-47. */
	public static final float STEALTH_FACTOR = 0.07f;

	public B21Entity(EntityType<? extends B21Entity> type, World world) {
		super(type, world);
	}

	@Override
	public void setHomeBase(net.minecraft.util.math.BlockPos pos, float heading) {
		super.setHomeBase(pos, heading);
		// Bomber fliegen hoeher als die Jaeger - schon damit sie sich nicht
		// gegenseitig in die Warteschleife fliegen.
		setPatrolAltitude(Math.max(pos.getY() + 60, 100));
	}

	/** Die Tarnung ist Bauart, kein Schalter - sie laesst sich nicht abstellen. */
	@Override
	public boolean isStealthOn() {
		return true;
	}

	/** Fliegendes Radar: sieht doppelt so weit wie ein Jaeger. */
	@Override
	protected double radarRange() {
		return F47Config.get().jetRadarRange * 2.0;
	}

	/**
	 * Ein Bomber sucht keine Luftkaempfe. Er nimmt Bodenziele - alles andere
	 * ueberlaesst er der Jagdstaffel.
	 */
	@Override
	protected boolean isWorthAttacking(Entity candidate) {
		if (candidate instanceof PlayerEntity) {
			return true;
		}
		return !Iff.isAirThreat(getTeam(), candidate) || candidate.isOnGround();
	}

	/**
	 * Bombenabwurf statt Luftkampf: Er ueberfliegt das Ziel und laesst fallen,
	 * wenn er darueber steht.
	 */
	@Override
	protected void engage(Entity target, double distance) {
		if (getWeaponTimer() > 0) {
			return;
		}
		Vec3d over = target.getPos().subtract(getPos());
		double horizontal = Math.sqrt(over.x * over.x + over.z * over.z);
		boolean above = horizontal < 12.0 && getY() > target.getY() + 8.0;

		if (above && getBombAmmo() > 0) {
			setLockedTarget(target);
			fireWeapon(WeaponType.BOMB);
			setWeaponTimer(30);
		} else if (distance < 110 && getMissileAmmo() > 0
				&& toTargetAlignment(target) > 0.85) {
			// Aus der Ferne bleibt die Lenkwaffe - besser als gar nichts.
			setLockedTarget(target);
			fireWeapon(WeaponType.MISSILE);
			setWeaponTimer(80);
		}
	}
}
