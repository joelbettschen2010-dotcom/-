package com.f47mod.entity.vehicle;

/** Waffenstationen der F-47. Die Reihenfolge bestimmt den Wechsel im Cockpit. */
public enum WeaponType {
	CANNON("Bordkanone", 2, 1),
	MISSILE("AIM-Lenkwaffe", 30, 1),
	BOMB("Freifallbombe", 20, 1),
	LASER("Laserkanone", 6, 0);

	private final String displayName;
	private final int cooldownTicks;
	private final int ammoCost;

	WeaponType(String displayName, int cooldownTicks, int ammoCost) {
		this.displayName = displayName;
		this.cooldownTicks = cooldownTicks;
		this.ammoCost = ammoCost;
	}

	public String displayName() {
		return displayName;
	}

	public int cooldownTicks() {
		return cooldownTicks;
	}

	public int ammoCost() {
		return ammoCost;
	}

	/** Uebersetzungsschluessel fuer das HUD. */
	public String translationKey() {
		return "hud.f47.weapon." + name().toLowerCase(java.util.Locale.ROOT);
	}

	public WeaponType next() {
		WeaponType[] values = values();
		return values[(ordinal() + 1) % values.length];
	}

	public static WeaponType byOrdinal(int ordinal) {
		WeaponType[] values = values();
		if (ordinal < 0 || ordinal >= values.length) {
			return CANNON;
		}
		return values[ordinal];
	}
}
