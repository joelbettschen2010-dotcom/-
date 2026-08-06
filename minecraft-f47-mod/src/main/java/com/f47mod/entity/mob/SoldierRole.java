package com.f47mod.entity.mob;

/**
 * Rollen des Bodenpersonals. Jede Rolle hat eigene Ausruestung, Werte und
 * Aufgaben auf der Basis.
 */
public enum SoldierRole {
	/** Standardinfanterie mit Sturmgewehr. */
	RIFLEMAN(24.0f, 4.5f, 26.0f, 0.30f),
	/** Schwere Waffe - verschiesst Raketen gegen Fahrzeuge und Gruppen. */
	HEAVY(28.0f, 9.0f, 30.0f, 0.26f),
	/** Sanitaeter - heilt Soldaten und den Spieler in der Naehe. */
	MEDIC(20.0f, 3.0f, 18.0f, 0.32f),
	/** Techniker - repariert und betankt Jets am Boden. */
	ENGINEER(22.0f, 3.5f, 18.0f, 0.31f),
	/** Kampfpilot - besteigt bereitstehende autonome Jets und fliegt Einsaetze. */
	PILOT(18.0f, 3.0f, 16.0f, 0.33f);

	public final float maxHealth;
	public final float damage;
	public final float attackRange;
	public final float movementSpeed;

	SoldierRole(float maxHealth, float damage, float attackRange, float movementSpeed) {
		this.maxHealth = maxHealth;
		this.damage = damage;
		this.attackRange = attackRange;
		this.movementSpeed = movementSpeed;
	}

	public String translationKey() {
		return "role.f47." + name().toLowerCase(java.util.Locale.ROOT);
	}

	public static SoldierRole byOrdinal(int ordinal) {
		SoldierRole[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : RIFLEMAN;
	}
}
