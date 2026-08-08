package com.f47mod.entity.vehicle;

import com.f47mod.F47Config;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Aerodynamik der F-47.
 *
 * <p>Hier wird nicht geschummelt: Aus Anstellwinkel, Staudruck und Luftdichte
 * entstehen echte Kraefte - Auftrieb, Widerstand, Schub, Gewicht - die
 * aufsummiert und einmal pro Tick beschleunigen. Das Vorgaengermodell hat den
 * Auftrieb nur als Rabatt auf die Schwerkraft verrechnet und den
 * Geschwindigkeitsvektor stumpf in die Blickrichtung gezogen; damit flog die
 * Maschine wie ein gelenkter Pfeil und nicht wie ein Flugzeug.
 *
 * <p>Was sich daraus von allein ergibt:
 * <ul>
 *   <li><b>Energieerhaltung:</b> Ziehen kostet Fahrt, Druecken bringt sie
 *       zurueck. Ein Looping ohne genug Anfangsgeschwindigkeit geht schief.</li>
 *   <li><b>Stroemungsabriss:</b> Jenseits des kritischen Anstellwinkels bricht
 *       der Auftrieb ein und der Widerstand steigt - die Nase faellt.</li>
 *   <li><b>Kurvenradius haengt an der Geschwindigkeit:</b> Schnell fliegen
 *       heisst weit kurven, weil die Querbeschleunigung begrenzt ist.</li>
 *   <li><b>Induzierter Widerstand:</b> Enge Kurven kosten spuerbar Fahrt.</li>
 *   <li><b>Dienstgipfelhoehe:</b> Oben ist die Luft duenn, der Auftrieb reicht
 *       irgendwann nicht mehr zum Steigen.</li>
 * </ul>
 *
 * <p>Gerechnet wird durchgehend in SI-Einheiten (Meter, Sekunden, Newton) und
 * erst am Rand in Minecrafts Bloecke pro Tick umgesetzt. Ein Block gilt als ein
 * Meter, ein Tick als eine zwanzigstel Sekunde.
 */
public final class FlightModel {
	/** Minecraft rechnet zwanzig Ticks je Sekunde. */
	public static final double TICKS_PER_SECOND = 20.0;
	private static final double SECONDS_PER_TICK = 1.0 / TICKS_PER_SECOND;
	private static final double GRAVITY = 9.81;

	/** Luftdichte auf Meereshoehe in kg/m^3. */
	private static final double SEA_LEVEL_DENSITY = 1.225;
	/**
	 * Hoehe, auf der die Luftdichte auf ein e-tel faellt - in Bloecken.
	 *
	 * <p>Echte 8500 m waeren in Minecraft wirkungslos, weil die Welt nur gut
	 * dreihundert Bloecke hoch ist. Der gestauchte Wert legt die duenne Luft in
	 * den Bereich, in dem tatsaechlich geflogen wird.
	 */
	private static final double SCALE_HEIGHT = 210.0;
	/** Hoehe, die als Meereshoehe gilt. */
	private static final double SEA_LEVEL = 64.0;

	/** Auftriebsanstieg je Radiant Anstellwinkel. */
	private static final double CL_ALPHA = 3.5;
	/** Anstellwinkel, ab dem die Stroemung abreisst (20 Grad). */
	private static final double CRITICAL_ALPHA = 0.35;
	/** Nullwiderstandsbeiwert. */
	private static final double CD_ZERO = 0.023;
	/** Faktor des induzierten Widerstands, 1/(pi * Streckung * Wirkungsgrad). */
	private static final double INDUCED_DRAG = 0.17;

	private FlightModel() {
	}

	/**
	 * Rechnet einen Zeitschritt und liefert die neue Geschwindigkeit in
	 * Bloecken pro Tick.
	 *
	 * @param velocity  bisherige Geschwindigkeit in Bloecken pro Tick
	 * @param forward   Nasenrichtung (Einheitsvektor)
	 * @param right     rechte Tragflaeche (Einheitsvektor, Querlage schon drin)
	 * @param thrust    Schub in Newton
	 * @param altitude  Hoehe in Bloecken, fuer die Luftdichte
	 * @param onGround  am Boden? Dann traegt das Fahrwerk mit
	 * @param rolling   Rollreibungsbeiwert, nur am Boden benutzt
	 */
	public static Vec3d step(Vec3d velocity, Vec3d forward, Vec3d right, double thrust,
			double altitude, boolean onGround, double rolling) {
		F47Config config = F47Config.get();
		double mass = Math.max(500.0, config.massKg);
		double wingArea = Math.max(1.0, config.wingAreaM2);

		// In SI umrechnen: Bloecke pro Tick sind Meter je zwanzigstel Sekunde.
		Vec3d speedMs = velocity.multiply(TICKS_PER_SECOND);
		double speed = speedMs.length();

		// Ohne Fahrt gibt es keine Anstroemung - dann zaehlt nur Schub und Gewicht.
		Vec3d flow = speed > 0.05 ? speedMs.multiply(1.0 / speed) : forward;
		double alpha = angleOfAttack(flow, forward, right, speed);

		double density = airDensity(altitude);
		double pressure = 0.5 * density * speed * speed;

		double lift = liftCoefficient(alpha);
		double drag = dragCoefficient(alpha, lift);

		// Auftrieb steht senkrecht auf der Anstroemung, in der Symmetrieebene.
		Vec3d liftAxis = right.crossProduct(flow);
		if (liftAxis.lengthSquared() > 1.0e-6) {
			liftAxis = liftAxis.normalize();
		} else {
			liftAxis = Vec3d.ZERO;
		}

		Vec3d liftForce = liftAxis.multiply(pressure * wingArea * lift);
		Vec3d dragForce = flow.multiply(-pressure * wingArea * drag);
		Vec3d thrustForce = forward.multiply(thrust);
		Vec3d weight = new Vec3d(0.0, -mass * GRAVITY, 0.0);

		// Weder Zelle noch Pilot halten beliebige Querbeschleunigung aus.
		liftForce = limitLoad(liftForce, mass, config.maxLoadFactor);

		Vec3d force = liftForce.add(dragForce).add(thrustForce).add(weight);

		if (onGround) {
			force = applyGroundContact(force, speedMs, mass, rolling);
		}

		Vec3d acceleration = force.multiply(1.0 / mass);
		Vec3d updated = speedMs.add(acceleration.multiply(SECONDS_PER_TICK));

		// Sicherheitsgrenze: Jenseits davon reisst Minecrafts Kollisionspruefung
		// ab und die Maschine faellt durch die Welt.
		double limit = config.speedLimitBlocksPerTick * TICKS_PER_SECOND;
		if (updated.length() > limit) {
			updated = updated.normalize().multiply(limit);
		}
		return updated.multiply(SECONDS_PER_TICK);
	}

	/**
	 * Der Anstellwinkel: Wie schraeg trifft die Luft auf die Tragflaeche?
	 * Positiv heisst Anstroemung von unten, also Auftrieb erzeugend.
	 */
	public static double angleOfAttack(Vec3d flow, Vec3d forward, Vec3d right, double speed) {
		if (speed <= 0.05) {
			return 0.0;
		}
		Vec3d up = right.crossProduct(forward);
		double alongForward = flow.dotProduct(forward);
		double alongUp = flow.dotProduct(up);
		return Math.atan2(-alongUp, alongForward);
	}

	/**
	 * Auftriebsbeiwert ueber dem Anstellwinkel.
	 *
	 * <p>Bis zum kritischen Winkel waechst er geradlinig mit. Danach reisst die
	 * Stroemung ab: Der Auftrieb faellt deutlich, verschwindet aber nicht ganz -
	 * eine abgerissene Flaeche traegt noch wie ein schraeg gestelltes Brett.
	 */
	public static double liftCoefficient(double alpha) {
		double magnitude = Math.abs(alpha);
		if (magnitude <= CRITICAL_ALPHA) {
			return CL_ALPHA * alpha;
		}
		double beyond = magnitude - CRITICAL_ALPHA;
		double collapse = Math.max(0.30, 1.0 - beyond * 1.8);
		return Math.signum(alpha) * CL_ALPHA * CRITICAL_ALPHA * collapse;
	}

	/**
	 * Widerstandsbeiwert: Nullwiderstand plus induzierter Anteil, der mit dem
	 * Quadrat des Auftriebs waechst - deshalb kosten enge Kurven Fahrt. Hinter
	 * dem Abriss kommt der Widerstand der abgeloesten Stroemung dazu.
	 */
	public static double dragCoefficient(double alpha, double lift) {
		double drag = CD_ZERO + INDUCED_DRAG * lift * lift;
		double magnitude = Math.abs(alpha);
		if (magnitude > CRITICAL_ALPHA) {
			drag += (magnitude - CRITICAL_ALPHA) * 1.4;
		}
		return drag;
	}

	/** Luftdichte ueber der Hoehe - oben wird es duenn. */
	public static double airDensity(double altitude) {
		return SEA_LEVEL_DENSITY * Math.exp(-(altitude - SEA_LEVEL) / SCALE_HEIGHT);
	}

	/** Begrenzt den Auftrieb auf das, was die Zelle aushaelt. */
	private static Vec3d limitLoad(Vec3d lift, double mass, double maxLoadFactor) {
		double ceiling = Math.max(1.0, maxLoadFactor) * mass * GRAVITY;
		return lift.lengthSquared() > ceiling * ceiling
				? lift.normalize().multiply(ceiling)
				: lift;
	}

	/**
	 * Bodenkontakt: Das Fahrwerk traegt die Maschine, die Reifen bremsen.
	 * Nach unten gerichtete Kraefte nimmt die Bahn auf, statt sie einsinken zu
	 * lassen; waagerecht wirkt die Rollreibung.
	 */
	private static Vec3d applyGroundContact(Vec3d force, Vec3d speedMs, double mass, double rolling) {
		double vertical = Math.max(force.y, 0.0);

		// Rollreibung wirkt der Fahrt entgegen und ist proportional zur Last.
		Vec3d ground = new Vec3d(speedMs.x, 0.0, speedMs.z);
		double groundSpeed = ground.length();
		Vec3d friction = Vec3d.ZERO;
		if (groundSpeed > 0.1) {
			friction = ground.multiply(-rolling * mass * GRAVITY / groundSpeed);
		}
		return new Vec3d(force.x + friction.x, vertical, force.z + friction.z);
	}

	/** Geschwindigkeit in km/h, wie sie das Cockpit anzeigt. */
	public static double toKmh(double blocksPerTick) {
		return blocksPerTick * TICKS_PER_SECOND * 3.6;
	}

	/**
	 * Ueberzieht die Maschine gerade? Nur dafuer da, Warnungen und
	 * Bot-Entscheidungen an denselben Wert zu binden wie die Physik.
	 */
	public static boolean isStalled(double alpha) {
		return Math.abs(alpha) > CRITICAL_ALPHA;
	}

	/** Anstellwinkel in Grad, fuer die Cockpitanzeige. */
	public static double degrees(double radians) {
		return radians * MathHelper.DEGREES_PER_RADIAN;
	}
}
