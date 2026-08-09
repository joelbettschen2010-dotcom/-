package com.f47mod.client;

import com.f47mod.net.RadarContact;
import com.f47mod.util.Team;

import java.util.Collections;
import java.util.List;

/**
 * Letztes vom Server empfangenes Radarbild. Der Server schickt es alle paar
 * Ticks; dazwischen zeigt das HUD den zuletzt bekannten Stand - so wie ein
 * echter Radarschirm zwischen zwei Umlaeufen auch.
 */
public final class ClientRadarState {
	private static List<RadarContact> contacts = List.of();
	private static long lastUpdate;
	private static Team viewerTeam = Team.BLUE;

	private ClientRadarState() {
	}

	public static void update(List<RadarContact> newContacts, Team team) {
		contacts = List.copyOf(newContacts);
		viewerTeam = team;
		lastUpdate = System.currentTimeMillis();
	}

	/** Partei, fuer die der Spieler gerade kaempft. */
	public static Team getViewerTeam() {
		return viewerTeam;
	}

	public static List<RadarContact> getContacts() {
		// Nach einer Sekunde ohne Aktualisierung gilt das Bild als veraltet.
		if (System.currentTimeMillis() - lastUpdate > 1500) {
			return Collections.emptyList();
		}
		return contacts;
	}

	public static void clear() {
		contacts = List.of();
	}
}
