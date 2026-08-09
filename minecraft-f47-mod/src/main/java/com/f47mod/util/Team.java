package com.f47mod.util;

import net.minecraft.util.Formatting;

/**
 * Die beiden Parteien des Mods.
 *
 * <p>Jede Einheit - Jet, Soldat, Drohne, Radarstation, Iron Dome - gehoert
 * genau einer Partei an und bekaempft ausschliesslich die andere. Wer nur mit
 * einer Partei spielt, merkt davon nichts: Es gibt dann schlicht keine
 * Gegenpartei, und es bleibt beim Kampf gegen die feindlichen Monster der
 * Welt, die beide Parteien angreifen.
 */
public enum Team {
	/** Blau - die Standardpartei des Spielers. */
	BLUE("blue", Formatting.BLUE, 0xFF4A8CE0),
	/** Rot - die Gegenpartei. */
	RED("red", Formatting.RED, 0xFFE05050);

	private final String key;
	private final Formatting formatting;
	private final int colour;

	Team(String key, Formatting formatting, int colour) {
		this.key = key;
		this.formatting = formatting;
		this.colour = colour;
	}

	public String key() {
		return key;
	}

	public Formatting formatting() {
		return formatting;
	}

	/** Farbe fuer Radarpunkte und Namensschilder. */
	public int colour() {
		return colour;
	}

	public Team opposite() {
		return this == BLUE ? RED : BLUE;
	}

	public String translationKey() {
		return "team.f47." + key;
	}

	public static Team byOrdinal(int ordinal) {
		Team[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : BLUE;
	}
}
