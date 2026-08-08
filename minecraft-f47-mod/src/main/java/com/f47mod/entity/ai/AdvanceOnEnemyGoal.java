package com.f47mod.entity.ai;

import com.f47mod.F47Config;
import com.f47mod.entity.mob.SoldierEntity;
import com.f47mod.util.Iff;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Vormarsch: Der Trupp sucht den Gegner auf, statt am Posten zu warten.
 *
 * <p>Bisher blieben Soldaten an ihrer Stellung stehen und schossen nur auf das,
 * was ihnen vor die Fuesse lief. Bei zwei Stuetzpunkten in ein paar hundert
 * Bloecken Abstand hiess das: Es passierte nie etwas. Vanilla-Zielsuche
 * arbeitet nur auf wenige Bloecke genau, und die Wegfindung gibt nach kurzer
 * Strecke auf - beides zu wenig, um einen Krieg zu fuehren.
 *
 * <p>Dieses Ziel arbeitet deshalb in zwei Stufen: Es sucht selten, dafuer
 * weitraeumig nach Gegnern, und marschiert dann in kurzen Etappen dorthin.
 * Jede Etappe ist kurz genug fuer die Wegfindung, aneinandergereiht tragen
 * sie beliebig weit.
 */
public class AdvanceOnEnemyGoal extends Goal {
	/** Wie oft weitraeumig gesucht wird. Selten, weil es ueber viel Gelaende geht. */
	private static final int SEARCH_INTERVAL = 100;
	/** Laenge einer Etappe in Bloecken - so weit traegt die Wegfindung sicher. */
	private static final double LEG = 24.0;
	/** Ab hier gilt der Gegner als erreicht; das Kampfziel uebernimmt. */
	private static final double CONTACT = 20.0;

	private final SoldierEntity soldier;
	private final double speed;
	private int searchTimer;
	@Nullable
	private Vec3d objective;

	public AdvanceOnEnemyGoal(SoldierEntity soldier, double speed) {
		this.soldier = soldier;
		this.speed = speed;
		setControls(EnumSet.of(Control.MOVE));
	}

	@Override
	public boolean canStart() {
		if (!F47Config.get().soldiersEngageAutomatically) {
			return false;
		}
		// Wer gerade jemanden im Visier hat, kaempft - der Vormarsch ruht.
		if (soldier.getTarget() != null && soldier.getTarget().isAlive()) {
			return false;
		}
		if (--searchTimer > 0) {
			return objective != null;
		}
		searchTimer = SEARCH_INTERVAL;
		objective = findEnemyPosition();
		return objective != null;
	}

	@Override
	public boolean shouldContinue() {
		return objective != null
				&& (soldier.getTarget() == null || !soldier.getTarget().isAlive());
	}

	@Override
	public void stop() {
		objective = null;
		soldier.getNavigation().stop();
	}

	@Override
	public void tick() {
		if (objective == null) {
			return;
		}
		double distance = soldier.getPos().distanceTo(objective);
		if (distance < CONTACT) {
			// Angekommen - ab hier uebernimmt die normale Zielsuche.
			objective = null;
			return;
		}
		if (!soldier.getNavigation().isIdle()) {
			return;
		}
		// In Etappen marschieren: Ein Wegpunkt ueber hunderte Bloecke wuerde
		// die Wegfindung ueberfordern, sie bricht solche Auftraege sofort ab.
		Vec3d direction = objective.subtract(soldier.getPos()).normalize();
		Vec3d step = soldier.getPos().add(direction.multiply(Math.min(LEG, distance)));
		soldier.getNavigation().startMovingTo(step.x, step.y, step.z, speed);
	}

	/** Wo steht der naechste Gegner? Grosszuegig gesucht, damit Entfernung egal ist. */
	@Nullable
	private Vec3d findEnemyPosition() {
		double reach = F47Config.get().strikeRange;
		Box box = soldier.getBoundingBox().expand(reach);
		Entity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Entity candidate : soldier.getWorld().getOtherEntities(soldier, box,
				e -> Iff.isEnemy(soldier, e))) {
			double distance = candidate.squaredDistanceTo(soldier);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = candidate;
			}
		}
		return best == null ? null : best.getPos();
	}
}
