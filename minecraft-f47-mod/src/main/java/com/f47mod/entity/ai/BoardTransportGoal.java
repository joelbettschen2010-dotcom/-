package com.f47mod.entity.ai;

import com.f47mod.F47Config;
import com.f47mod.entity.mob.SoldierEntity;
import com.f47mod.entity.vehicle.TransportEntity;
import com.f47mod.util.EnemyIntel;
import com.f47mod.util.Iff;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Verlegung auf dem Luftweg: Der Trupp steigt in ein Transportflugzeug.
 *
 * <p>Zu Fuss ist ein Gegner in tausend Bloecken Entfernung praktisch
 * unerreichbar - der Marsch dauert Minuten, und unterwegs geht die halbe
 * Truppe verloren. Liegt das Ziel weiter weg als
 * {@link #AIRLIFT_DISTANCE}, sucht sich der Soldat deshalb einen
 * bereitstehenden Transporter der eigenen Partei und besetzt ihn. Fliegen und
 * Absetzen uebernimmt danach der Transporter selbst.
 *
 * <p>Bei nahen Zielen greift das Ziel gar nicht erst - dort ist der Fussmarsch
 * schneller als das Warten auf einen Platz an Bord.
 */
public class BoardTransportGoal extends Goal {
	/** Ab dieser Entfernung lohnt der Lufttransport. */
	private static final double AIRLIFT_DISTANCE = 400.0;
	/**
	 * Wie weit ein Soldat laeuft, um an Bord zu kommen.
	 *
	 * <p>Quer ueber den ganzen Flugplatz: Die Infanterie tritt neben der Bahn
	 * an, die Transporter stehen auf dem Vorfeld der Gegenseite - das sind
	 * allein siebzig Bloecke. Mit sechzig kam vom Antreteplatz aus niemand an
	 * ein Flugzeug heran.
	 */
	private static final double WALK_TO_PLANE = 140.0;
	/** Wie oft ueberhaupt nachgesehen wird. */
	private static final int INTERVAL = 60;

	private final SoldierEntity soldier;
	private final double speed;
	private int timer;
	@Nullable
	private TransportEntity ride;

	public BoardTransportGoal(SoldierEntity soldier, double speed) {
		this.soldier = soldier;
		this.speed = speed;
		setControls(EnumSet.of(Control.MOVE));
	}

	@Override
	public boolean canStart() {
		if (soldier.hasVehicle() || soldier.getTarget() != null) {
			return false;
		}
		// Wachmannschaft fliegt nicht mit - sie haelt den Stuetzpunkt.
		if (soldier.getStance() == SoldierEntity.Stance.HOLD) {
			return false;
		}
		if (--timer > 0) {
			return false;
		}
		timer = INTERVAL;

		// Lohnt sich das ueberhaupt? Nur bei weit entfernten Gegnern.
		Vec3d enemy = findEnemy();
		if (enemy == null || soldier.getPos().distanceTo(enemy) < AIRLIFT_DISTANCE) {
			return false;
		}
		ride = findTransport();
		return ride != null;
	}

	@Override
	public boolean shouldContinue() {
		return ride != null && ride.isAlive() && !soldier.hasVehicle()
				&& ride.isOnGround();
	}

	@Override
	public void stop() {
		ride = null;
		soldier.getNavigation().stop();
	}

	@Override
	public void tick() {
		if (ride == null) {
			return;
		}
		if (soldier.squaredDistanceTo(ride) < 12.0) {
			soldier.startRiding(ride);
			ride = null;
			return;
		}
		if (soldier.getNavigation().isIdle()) {
			soldier.getNavigation().startMovingTo(ride, speed);
		}
	}

	/** Ein bereitstehender, noch nicht voller Transporter der eigenen Partei. */
	@Nullable
	private TransportEntity findTransport() {
		Box box = soldier.getBoundingBox().expand(WALK_TO_PLANE);
		TransportEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (TransportEntity plane : soldier.getWorld().getEntitiesByClass(
				TransportEntity.class, box, candidate -> candidate.isOnGround()
						&& Iff.sameTeam(soldier, candidate)
						&& candidate.getPassengerList().size() < 8)) {
			double distance = plane.squaredDistanceTo(soldier);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = plane;
			}
		}
		return best;
	}

	@Nullable
	private Vec3d findEnemy() {
		Vec3d enemy = EnemyIntel.nearestEnemy(soldier.getWorld(), soldier.getTeam(),
				soldier.getPos());
		if (enemy == null || soldier.getPos().distanceTo(enemy) > F47Config.get().strikeRange) {
			return null;
		}
		return enemy;
	}
}
