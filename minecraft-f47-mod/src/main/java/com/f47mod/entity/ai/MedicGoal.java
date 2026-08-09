package com.f47mod.entity.ai;

import com.f47mod.entity.mob.SoldierEntity;
import com.f47mod.entity.mob.SoldierRole;
import com.f47mod.util.Iff;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * Sanitaeter: sucht verwundete eigene Einheiten, laeuft hin und heilt sie.
 */
public class MedicGoal extends Goal {
	private static final double SEARCH_RANGE = 20.0;
	private static final double HEAL_RANGE = 2.5;

	private final SoldierEntity medic;
	private LivingEntity patient;
	private int healTimer;

	public MedicGoal(SoldierEntity medic) {
		this.medic = medic;
		setControls(EnumSet.of(Control.MOVE, Control.LOOK));
	}

	@Override
	public boolean canStart() {
		if (medic.getRole() != SoldierRole.MEDIC || medic.getTarget() != null) {
			return false;
		}
		patient = findPatient();
		return patient != null;
	}

	@Override
	public boolean shouldContinue() {
		return patient != null && patient.isAlive() && patient.getHealth() < patient.getMaxHealth()
				&& medic.squaredDistanceTo(patient) < SEARCH_RANGE * SEARCH_RANGE * 2;
	}

	@Override
	public void stop() {
		patient = null;
		medic.getNavigation().stop();
	}

	@Override
	public boolean shouldRunEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		if (patient == null) {
			return;
		}
		medic.getLookControl().lookAt(patient, 20.0f, medic.getMaxLookPitchChange());

		if (medic.squaredDistanceTo(patient) > HEAL_RANGE * HEAL_RANGE) {
			if (medic.getNavigation().isIdle()) {
				medic.getNavigation().startMovingTo(patient, 1.1);
			}
			return;
		}

		medic.getNavigation().stop();
		if (++healTimer < 25) {
			return;
		}
		healTimer = 0;
		patient.heal(3.0f);
		medic.playSound(SoundEvents.ITEM_BOTTLE_FILL_DRAGONBREATH, 0.5f, 1.4f);
		if (medic.getWorld() instanceof ServerWorld server) {
			server.spawnParticles(ParticleTypes.HEART,
					patient.getX(), patient.getBodyY(1.0), patient.getZ(), 3, 0.3, 0.3, 0.3, 0.0);
		}
	}

	private LivingEntity findPatient() {
		Box box = medic.getBoundingBox().expand(SEARCH_RANGE);
		List<LivingEntity> candidates = medic.getWorld().getEntitiesByClass(
				LivingEntity.class, box, entity -> entity != medic && Iff.isHealTarget(medic.getTeam(), entity));
		return candidates.stream()
				.min(Comparator.comparingDouble(entity -> entity.getHealth() / entity.getMaxHealth()))
				.orElse(null);
	}
}
