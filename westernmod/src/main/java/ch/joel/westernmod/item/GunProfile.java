package ch.joel.westernmod.item;

/**
 * Ballistik-Werte einer Schusswaffe.
 *
 * @param damage    Schaden pro Kugel
 * @param cooldown  Ticks zwischen zwei Schüssen
 * @param pellets   Anzahl Kugeln pro Schuss (Schrotflinte streut mehrere)
 * @param spread    Streuung in Grad
 * @param velocity  Mündungsgeschwindigkeit
 * @param pitch     Tonhöhe des Schussgeräuschs
 * @param recoil    Rückstoss, der den Schützen nach hinten schiebt
 */
public record GunProfile(float damage, int cooldown, int pellets, float spread, float velocity, float pitch,
                         float recoil) {

	public static final GunProfile REVOLVER = new GunProfile(6.0f, 8, 1, 1.5f, 3.0f, 1.6f, 0.02f);
	public static final GunProfile WINCHESTER = new GunProfile(10.0f, 22, 1, 0.5f, 4.0f, 1.2f, 0.06f);
	public static final GunProfile SHOTGUN = new GunProfile(3.5f, 32, 7, 9.0f, 2.2f, 0.8f, 0.14f);

	/** Werte für die Revolver der NPC-Revolvermänner — etwas schwächer als beim Spieler. */
	public static final GunProfile NPC_REVOLVER = new GunProfile(4.0f, 30, 1, 3.0f, 2.6f, 1.6f, 0.0f);
}
