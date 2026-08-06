package com.f47mod.entity.ai;

import com.f47mod.entity.mob.SoldierEntity;
import com.f47mod.entity.mob.SoldierRole;
import com.f47mod.entity.vehicle.AutonomousF47Entity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * Pilot besteigt einen bereitstehenden autonomen Jet: Er laeuft zur Maschine,
 * steigt ein und uebernimmt sie. Der Jet wird dadurch flugbereit und traegt
 * anschliessend das Rufzeichen des Piloten.
 */
public class BoardJetGoal extends Goal {
	private static final double SEARCH_RANGE = 32.0;
	private static final double BOARD_RANGE = 2.6;

	private final SoldierEntity pilot;
	private AutonomousF47Entity jet;

	public BoardJetGoal(SoldierEntity pilot) {
		this.pilot = pilot;
		setControls(EnumSet.of(Control.MOVE, Control.LOOK));
	}

	@Override
	public boolean canStart() {
		if (pilot.getRole() != SoldierRole.PILOT) {
			return false;
		}
		jet = findJet();
		return jet != null;
	}

	@Override
	public boolean shouldContinue() {
		return jet != null && jet.isAlive() && !jet.hasPilot();
	}

	@Override
	public void stop() {
		jet = null;
		pilot.getNavigation().stop();
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
		pilot.getLookControl().lookAt(jet.getX(), jet.getY() + 0.5, jet.getZ());

		if (pilot.squaredDistanceTo(jet) > BOARD_RANGE * BOARD_RANGE) {
			if (pilot.getNavigation().isIdle()) {
				pilot.getNavigation().startMovingTo(jet, 1.2);
			}
			return;
		}

		board();
	}

	/** Einsteigen: Der Pilot verschwindet im Cockpit und der Jet wird aktiv. */
	private void board() {
		jet.setHasPilot(true);
		jet.randomiseCallsign();
		if (jet.getHomeBase() == null) {
			jet.setHomeBase(jet.getBlockPos());
		}
		if (jet.getOwner() == null && pilot.getOwner() != null) {
			jet.setOwner(pilot.getOwner());
		}

		if (pilot.getWorld() instanceof ServerWorld server) {
			server.spawnParticles(ParticleTypes.CLOUD,
					jet.getX(), jet.getY() + 0.8, jet.getZ(), 12, 0.5, 0.3, 0.5, 0.02);
		}
		jet.playSound(SoundEvents.BLOCK_IRON_DOOR_CLOSE, 0.8f, 1.2f);

		if (pilot.getOwner() != null) {
			pilot.getOwner().sendMessage(
					Text.translatable("message.f47.pilot_boarded", jet.getCallsign()), false);
		}
		// Der Pilot sitzt jetzt im Jet - die Entity wird entfernt.
		pilot.discard();
	}

	private AutonomousF47Entity findJet() {
		Box box = pilot.getBoundingBox().expand(SEARCH_RANGE);
		List<AutonomousF47Entity> candidates = pilot.getWorld().getEntitiesByClass(
				AutonomousF47Entity.class, box, candidate -> !candidate.hasPilot() && candidate.isAlive());
		return candidates.stream()
				.min(Comparator.comparingDouble(pilot::squaredDistanceTo))
				.orElse(null);
	}
}
