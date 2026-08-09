package com.f47mod.entity.ai;

import com.f47mod.entity.mob.SoldierEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Feuerkampf: heranarbeiten, zielen, schiessen.
 *
 * <p>Ersetzt das Vanilla-Ziel {@code ProjectileAttackGoal}, und zwar aus einem
 * einzigen Grund: Dieses ruft, solange das Ziel ausser Schussweite ist, in
 * <em>jedem</em> Tick {@code startMovingTo} auf - und das ist jedes Mal eine
 * vollstaendige Wegsuche ueber die halbe Sichtweite. Bei einer Handvoll Mobs
 * faellt das nicht auf. Bei elfhundert Soldaten, die gleichzeitig aufeinander
 * zulaufen, dauerte ein einzelner Server-Tick im Versuch hundertachtzig
 * Sekunden, und der Watchdog hat den Server als abgestuerzt abgeschossen.
 *
 * <p>Hier wird der Weg deshalb nur alle {@link #REPATH} Ticks neu gesucht.
 * Dazwischen laeuft der Soldat den schon gefundenen Weg weiter - das sieht
 * genauso aus und kostet ein Zwanzigstel.
 */
public class SoldierAttackGoal extends Goal {
	/** Ticks zwischen zwei Wegsuchen. */
	private static final int REPATH = 20;

	private final SoldierEntity soldier;
	private final double speed;
	private final int cooldown;
	private final float range;
	private final float squaredRange;

	private int repathTimer;
	private int attackTimer;
	private int seenTicks;

	public SoldierAttackGoal(SoldierEntity soldier, double speed, int cooldown, float range) {
		this.soldier = soldier;
		this.speed = speed;
		this.cooldown = cooldown;
		this.range = range;
		this.squaredRange = range * range;
		setControls(EnumSet.of(Control.MOVE, Control.LOOK));
	}

	@Override
	public boolean canStart() {
		LivingEntity target = soldier.getTarget();
		return target != null && target.isAlive();
	}

	@Override
	public boolean shouldContinue() {
		return canStart() || !soldier.getNavigation().isIdle();
	}

	@Override
	public void start() {
		repathTimer = 0;
		attackTimer = -1;
		seenTicks = 0;
	}

	@Override
	public void stop() {
		seenTicks = 0;
		attackTimer = -1;
		soldier.getNavigation().stop();
	}

	@Override
	public boolean shouldRunEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		LivingEntity target = soldier.getTarget();
		if (target == null) {
			return;
		}
		double distance = soldier.squaredDistanceTo(target.getX(), target.getY(), target.getZ());
		boolean visible = soldier.getVisibilityCache().canSee(target);
		seenTicks = visible ? seenTicks + 1 : 0;

		// Zielen kostet nichts und macht den Unterschied zwischen "steht herum"
		// und "nimmt Ziel auf".
		soldier.getLookControl().lookAt(target, 30.0f, 30.0f);

		if (distance <= squaredRange && seenTicks >= 5) {
			soldier.getNavigation().stop();
			repathTimer = 0;
		} else if (--repathTimer <= 0) {
			// Nur hier wird wirklich gesucht.
			repathTimer = REPATH;
			soldier.getNavigation().startMovingTo(target, speed);
		}

		if (--attackTimer == 0) {
			if (!visible) {
				return;
			}
			soldier.shootAt(target, 1.0f);
			attackTimer = cooldown;
		} else if (attackTimer < 0) {
			// Anlaufzeit: Naeher heran heisst frueher schussbereit.
			attackTimer = Math.max(4, (int) (Math.sqrt(distance) / range * cooldown * 0.5) + cooldown / 2);
		}
	}

	@Nullable
	protected LivingEntity target() {
		return soldier.getTarget();
	}
}
