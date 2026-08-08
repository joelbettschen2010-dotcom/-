package com.f47mod.entity.vehicle;

import com.f47mod.F47Config;
import com.f47mod.util.Iff;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.List;

/**
 * Transporter - halb getarntes Grossraumflugzeug.
 *
 * <p>Er hat zwei Aufgaben und keine Waffen:
 *
 * <ul>
 *   <li><b>Nachschub in der Luft.</b> Wer in der Naehe fliegt und wenig
 *       Treibstoff hat, bekommt welchen abgegeben. Damit sind Einsaetze
 *       moeglich, die weiter reichen als eine Tankfuellung - ohne
 *       Luftbetankung waere jeder Angriff auf einen weit entfernten
 *       Stuetzpunkt eine Reise ohne Rueckfahrkarte.</li>
 *   <li><b>Truppen und Geraet.</b> Er nimmt Soldaten und Fahrzeuge auf und
 *       setzt sie ab, wo sie gebraucht werden.</li>
 * </ul>
 *
 * <p>Halb getarnt heisst: schwerer zu orten als ein Jaeger, aber laengst
 * nicht so unsichtbar wie die B-21. Ein grosses Flugzeug bleibt ein grosses
 * Flugzeug.
 */
public class TransportEntity extends AutonomousF47Entity {
	/** Faktor auf die gegnerische Ortungsreichweite - zwischen Jaeger und Bomber. */
	public static final float STEALTH_FACTOR = 0.45f;

	/** Wie viel Treibstoff je Tick abgegeben wird. */
	private static final float TRANSFER_RATE = 6.0f;
	/** Umkreis, in dem betankt wird. Formationsabstand, kein Zufallstreffer. */
	private static final double BOOM_RANGE = 26.0;
	/** Ab diesem Fuellstand gilt eine Maschine als bedueftig. */
	private static final float THIRSTY = 0.55f;

	public TransportEntity(EntityType<? extends TransportEntity> type, World world) {
		super(type, world);
	}

	@Override
	public void setHomeBase(net.minecraft.util.math.BlockPos pos, float heading) {
		super.setHomeBase(pos, heading);
		// Zwischen Jaegern und Bombern, damit sich die Kreise nicht schneiden.
		setPatrolAltitude(Math.max(pos.getY() + 48, 90));
	}

	/** Halb getarnt - die Tarnung gehoert zur Bauart. */
	@Override
	public boolean isStealthOn() {
		return true;
	}

	/** Ein Transporter schiesst nicht. Er sucht deshalb auch keine Ziele. */
	@Override
	protected boolean isWorthAttacking(Entity candidate) {
		return false;
	}

	@Override
	protected void engage(Entity target, double distance) {
		// Unbewaffnet.
	}

	@Override
	protected void serverTick() {
		super.serverTick();
		if (getWorld().isClient) {
			return;
		}
		refuelNearby();
	}

	/**
	 * Luftbetankung: Gibt Treibstoff an eigene Maschinen in der Naehe ab.
	 *
	 * <p>Bewusst nur an fliegende und nur an knappe - am Boden macht das der
	 * Techniker, und eine volle Maschine braucht nichts.
	 */
	private void refuelNearby() {
		if (getFuel() < 200.0f) {
			// Der eigene Vorrat geht vor.
			return;
		}
		F47Config config = F47Config.get();
		Box box = getBoundingBox().expand(BOOM_RANGE);
		List<F47Entity> nearby = getWorld().getEntitiesByClass(F47Entity.class, box,
				other -> other != this && !other.isOnGround()
						&& Iff.sameTeam(this, other)
						&& other.getFuel() < config.maxFuel * THIRSTY);

		for (F47Entity thirsty : nearby) {
			float give = Math.min(TRANSFER_RATE, getFuel() - 100.0f);
			if (give <= 0.0f) {
				return;
			}
			thirsty.setFuel(Math.min(config.maxFuel, thirsty.getFuel() + give));
			setFuel(getFuel() - give);

			if (getWorld() instanceof ServerWorld server && age % 10 == 0) {
				// Sichtbarer Schlauch zwischen den beiden Maschinen.
				server.spawnParticles(ParticleTypes.END_ROD,
						(getX() + thirsty.getX()) * 0.5,
						(getY() + thirsty.getY()) * 0.5,
						(getZ() + thirsty.getZ()) * 0.5,
						3, 0.6, 0.4, 0.6, 0.0);
			}
		}
	}

	/** Der Transporter nimmt Soldaten und Geraet auf. */
	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return getPassengerList().size() < 8;
	}

	@Override
	public net.minecraft.util.ActionResult interact(PlayerEntity player, net.minecraft.util.Hand hand) {
		if (getWorld().isClient) {
			return net.minecraft.util.ActionResult.SUCCESS;
		}
		// Anders als der Jaeger nimmt der Transporter Mitfliegende auf.
		return player.startRiding(this)
				? net.minecraft.util.ActionResult.CONSUME
				: net.minecraft.util.ActionResult.PASS;
	}
}
