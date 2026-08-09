package com.f47mod.entity.ai;

import com.f47mod.entity.mob.SoldierEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.BlockPos;

import java.util.EnumSet;

/**
 * Stellung halten oder patrouillieren. Bei HOLD bleibt der Soldat am Posten,
 * bei PATROL laeuft er einen Bogen darum ab.
 */
public class GuardPostGoal extends Goal {
	private static final double PATROL_RADIUS = 10.0;
	private static final double LEASH_RADIUS = 4.0;

	private final SoldierEntity soldier;
	private final double speed;
	private int cooldown;
	private double patrolAngle;

	public GuardPostGoal(SoldierEntity soldier, double speed) {
		this.soldier = soldier;
		this.speed = speed;
		this.patrolAngle = soldier.getRandom().nextDouble() * Math.PI * 2.0;
		setControls(EnumSet.of(Control.MOVE));
	}

	@Override
	public boolean canStart() {
		SoldierEntity.Stance stance = soldier.getStance();
		if (stance == SoldierEntity.Stance.FOLLOW) {
			return false;
		}
		return soldier.getGuardPost() != null;
	}

	@Override
	public boolean shouldContinue() {
		return canStart();
	}

	@Override
	public boolean shouldRunEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		BlockPos post = soldier.getGuardPost();
		if (post == null || --cooldown > 0) {
			return;
		}

		if (soldier.getStance() == SoldierEntity.Stance.HOLD) {
			cooldown = 20;
			// Nur zurueckkehren, wenn der Soldat vom Posten abgedriftet ist.
			if (soldier.getBlockPos().getSquaredDistance(post) > LEASH_RADIUS * LEASH_RADIUS) {
				soldier.getNavigation().startMovingTo(
						post.getX() + 0.5, post.getY(), post.getZ() + 0.5, speed);
			}
			return;
		}

		// PATROL: Der Soldat laeuft einen Kreis um seinen Posten ab.
		cooldown = 60;
		patrolAngle += Math.PI / 4.0 + soldier.getRandom().nextDouble() * 0.4;
		double x = post.getX() + 0.5 + Math.cos(patrolAngle) * PATROL_RADIUS;
		double z = post.getZ() + 0.5 + Math.sin(patrolAngle) * PATROL_RADIUS;
		soldier.getNavigation().startMovingTo(x, post.getY(), z, speed);
	}
}
