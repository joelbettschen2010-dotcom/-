package com.f47mod.entity.ai;

import com.f47mod.entity.mob.SoldierEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

/**
 * Der Soldat bleibt in der Naehe seines Kommandanten - aber nicht direkt auf
 * ihm, sondern versetzt, damit ein Trupp nicht zum Knaeuel wird.
 */
public class FollowCommanderGoal extends Goal {
	private final SoldierEntity soldier;
	private final double speed;
	private final float minDistance;
	private final float maxDistance;
	private PlayerEntity commander;
	private int updateCountdown;
	private final double offsetAngle;

	public FollowCommanderGoal(SoldierEntity soldier, double speed, float minDistance, float maxDistance) {
		this.soldier = soldier;
		this.speed = speed;
		this.minDistance = minDistance;
		this.maxDistance = maxDistance;
		// Jeder Soldat haelt einen eigenen Platz im Verband.
		this.offsetAngle = soldier.getRandom().nextDouble() * Math.PI * 2.0;
		setControls(EnumSet.of(Control.MOVE, Control.LOOK));
	}

	@Override
	public boolean canStart() {
		if (soldier.getStance() != SoldierEntity.Stance.FOLLOW) {
			return false;
		}
		PlayerEntity owner = soldier.getOwner();
		if (owner == null || owner.isSpectator()) {
			return false;
		}
		if (soldier.squaredDistanceTo(owner) < minDistance * minDistance) {
			return false;
		}
		commander = owner;
		return true;
	}

	@Override
	public boolean shouldContinue() {
		if (commander == null || soldier.getStance() != SoldierEntity.Stance.FOLLOW) {
			return false;
		}
		if (soldier.getNavigation().isIdle()) {
			return false;
		}
		return soldier.squaredDistanceTo(commander) > minDistance * minDistance * 0.6;
	}

	@Override
	public void start() {
		updateCountdown = 0;
	}

	@Override
	public void stop() {
		commander = null;
		soldier.getNavigation().stop();
	}

	@Override
	public void tick() {
		if (commander == null) {
			return;
		}
		soldier.getLookControl().lookAt(commander, 10.0f, soldier.getMaxLookPitchChange());
		if (--updateCountdown > 0) {
			return;
		}
		updateCountdown = 10;

		double distance = soldier.distanceTo(commander);
		if (distance > maxDistance * 2.5) {
			// Zu weit abgehaengt: direkt zum Kommandanten aufschliessen.
			teleportNear();
			return;
		}

		// Zielpunkt seitlich neben dem Kommandanten, damit der Trupp Front bildet.
		double radius = Math.min(4.0, Math.max(2.0, distance * 0.25));
		Vec3d target = commander.getPos().add(
				Math.cos(offsetAngle) * radius, 0.0, Math.sin(offsetAngle) * radius);
		soldier.getNavigation().startMovingTo(target.x, target.y, target.z, speed);
	}

	/** Notfall-Aufschluss, wenn der Weg zu lang oder blockiert ist. */
	private void teleportNear() {
		if (commander == null || !commander.isOnGround()) {
			return;
		}
		Vec3d spot = commander.getPos().add(
				Math.cos(offsetAngle) * 2.5, 0.0, Math.sin(offsetAngle) * 2.5);
		soldier.refreshPositionAndAngles(spot.x, spot.y, spot.z, soldier.getYaw(), soldier.getPitch());
		soldier.getNavigation().stop();
	}
}
