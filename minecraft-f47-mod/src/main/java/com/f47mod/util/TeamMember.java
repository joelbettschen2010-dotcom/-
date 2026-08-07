package com.f47mod.util;

/**
 * Alles, was einer Partei angehoert: Flugzeuge, Soldaten, Drohnen, Geschosse
 * und die Anlagen der Basis. Damit muss die Freund-Feind-Erkennung nur diese
 * eine Schnittstelle kennen.
 */
public interface TeamMember {
	Team getTeam();

	void setTeam(Team team);
}
