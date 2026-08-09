package com.f47mod.util;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * Ein gemeinsames Lagebild statt tausend Einzelsuchen.
 *
 * <p>Jede Einheit soll den Gegner finden, egal wie weit er weg ist - das war
 * ausdruecklich gewuenscht und ist auch richtig so. Umgesetzt war es aber
 * teuer: Jeder Soldat, jeder Panzer und jede Maschine hat dafuer selbst einen
 * Wuerfel von viertausend Bloecken Kantenlaenge nach Gegnern abgesucht. Bei
 * dreizehnhundert Mann auf dem Feld lief der Server damit zwanzig Sekunden
 * hinterher und holte das nie wieder auf.
 *
 * <p>Hier passiert dasselbe genau einmal fuer alle: Zweimal in der Sekunde
 * wird durchgezaehlt, wo die Parteien stehen, und jede Partei bekommt einen
 * Schwerpunkt. Wer wissen will, wo der Gegner steht, liest diesen Wert ab -
 * das kostet nichts. Fuer den Zweck reicht das voellig: Auf tausend Bloecke
 * Entfernung ist nicht der einzelne Gegner interessant, sondern die Richtung.
 *
 * <p>Der Nahkampf laeuft unveraendert ueber die normale Zielsuche mit ihrer
 * Sichtweite - dieses Lagebild ersetzt nur die Fernaufklaerung.
 */
public final class EnemyIntel {
	/** Ticks zwischen zwei Aufklaerungslaeufen. */
	private static final int REFRESH = 40;

	private static final Map<Team, Vec3d> CENTRES = new EnumMap<>(Team.class);
	private static long refreshedAt = Long.MIN_VALUE;

	private EnemyIntel() {
	}

	/**
	 * Wo steht die gegnerische Partei?
	 *
	 * @param team Partei des Fragenden
	 * @param from Standort des Fragenden - entscheidet bei mehreren Gegnern
	 * @return Schwerpunkt der naechsten gegnerischen Partei oder {@code null}
	 */
	@Nullable
	public static Vec3d nearestEnemy(World world, Team team, Vec3d from) {
		refresh(world);
		Vec3d best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Map.Entry<Team, Vec3d> entry : CENTRES.entrySet()) {
			if (entry.getKey() == team) {
				continue;
			}
			double distance = entry.getValue().squaredDistanceTo(from);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = entry.getValue();
			}
		}
		return best;
	}

	private static void refresh(World world) {
		if (!(world instanceof ServerWorld server)) {
			return;
		}
		long now = world.getTime();
		// Auch rueckwaerts pruefen: Beim Weltwechsel faengt die Uhr von vorn an.
		if (now >= refreshedAt && now - refreshedAt < REFRESH) {
			return;
		}
		refreshedAt = now;
		CENTRES.clear();

		Map<Team, Vec3d> sum = new EnumMap<>(Team.class);
		Map<Team, Integer> count = new EnumMap<>(Team.class);
		// Ein einziger Durchlauf ueber alle geladenen Einheiten mit Partei.
		for (Entity entity : server.iterateEntities()) {
			if (!(entity instanceof TeamMember member) || !entity.isAlive()) {
				continue;
			}
			Team side = member.getTeam();
			sum.merge(side, entity.getPos(), Vec3d::add);
			count.merge(side, 1, Integer::sum);
		}
		for (Map.Entry<Team, Vec3d> entry : sum.entrySet()) {
			int number = count.getOrDefault(entry.getKey(), 1);
			CENTRES.put(entry.getKey(), entry.getValue().multiply(1.0 / number));
		}
	}
}
