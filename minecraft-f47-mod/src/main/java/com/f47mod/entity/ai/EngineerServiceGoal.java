package com.f47mod.entity.ai;

import com.f47mod.F47Config;
import com.f47mod.entity.mob.SoldierEntity;
import com.f47mod.entity.mob.SoldierRole;
import com.f47mod.entity.vehicle.F47Entity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * Bodenpersonal/Techniker: laeuft zu beschaedigten oder leergeflogenen Jets am
 * Boden, repariert die Struktur und fuellt Treibstoff nach.
 */
public class EngineerServiceGoal extends Goal {
	private static final double SEARCH_RANGE = 24.0;
	private static final double WORK_RANGE = 3.2;

	private final SoldierEntity engineer;
	private F47Entity jet;
	private int workTimer;

	public EngineerServiceGoal(SoldierEntity engineer) {
		this.engineer = engineer;
		setControls(EnumSet.of(Control.MOVE, Control.LOOK));
	}

	@Override
	public boolean canStart() {
		if (engineer.getRole() != SoldierRole.ENGINEER || engineer.getTarget() != null) {
			return false;
		}
		jet = findJet();
		return jet != null;
	}

	@Override
	public boolean shouldContinue() {
		return jet != null && jet.isAlive() && needsService(jet)
				&& engineer.squaredDistanceTo(jet) < SEARCH_RANGE * SEARCH_RANGE * 2;
	}

	@Override
	public void stop() {
		jet = null;
		engineer.getNavigation().stop();
	}

	@Override
	public boolean shouldRunEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		if (jet == null) {
			return;
		}
		engineer.getLookControl().lookAt(jet.getX(), jet.getY() + 0.5, jet.getZ());

		if (engineer.squaredDistanceTo(jet) > WORK_RANGE * WORK_RANGE) {
			if (engineer.getNavigation().isIdle()) {
				engineer.getNavigation().startMovingTo(jet, 1.0);
			}
			return;
		}

		engineer.getNavigation().stop();
		if (++workTimer < 15) {
			return;
		}
		workTimer = 0;

		F47Config config = F47Config.get();
		if (jet.getStructure() < config.jetMaxHealth) {
			jet.repair(2.5f);
			engineer.playSound(SoundEvents.BLOCK_ANVIL_USE, 0.4f, 1.6f);
			if (engineer.getWorld() instanceof ServerWorld server) {
				server.spawnParticles(ParticleTypes.CRIT,
						jet.getX(), jet.getY() + 0.6, jet.getZ(), 5, 0.6, 0.3, 0.6, 0.05);
			}
		} else if (jet.getFuel() < config.maxFuel) {
			jet.refuel(120.0f);
			engineer.playSound(SoundEvents.ITEM_BUCKET_FILL, 0.4f, 1.2f);
		}
	}

	private boolean needsService(F47Entity candidate) {
		F47Config config = F47Config.get();
		// Nur Maschinen der eigenen Partei werden gewartet.
		return com.f47mod.util.Iff.sameTeam(engineer, candidate)
				&& candidate.isOnGround()
				&& (candidate.getStructure() < config.jetMaxHealth || candidate.getFuel() < config.maxFuel * 0.9f);
	}

	private F47Entity findJet() {
		Box box = engineer.getBoundingBox().expand(SEARCH_RANGE);
		List<F47Entity> candidates = engineer.getWorld().getEntitiesByClass(
				F47Entity.class, box, this::needsService);
		return candidates.stream()
				.min(Comparator.comparingDouble(engineer::squaredDistanceTo))
				.orElse(null);
	}
}
