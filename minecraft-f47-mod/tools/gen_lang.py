#!/usr/bin/env python3
"""
Erzeugt die Sprachdateien. Deutsch ist die Hauptsprache, Englisch die
Rueckfallebene fuer alle anderen Spracheinstellungen.

    python3 tools/gen_lang.py
"""

import json
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
LANG = os.path.join(ROOT, "src", "main", "resources", "assets", "f47", "lang")

# Aufbau: schluessel -> (deutsch, englisch)
ENTRIES = {
    # --- Kreativ-Reiter ---
    "itemgroup.f47.general": ("F-47 Luftwaffe", "F-47 Air Force"),

    # --- Bloecke ---
    "block.f47.runway": ("Startbahnbelag", "Runway Surface"),
    "block.f47.runway_centerline": ("Startbahn-Mittellinie", "Runway Centerline"),
    "block.f47.runway_threshold": ("Startbahn-Schwelle", "Runway Threshold"),
    "block.f47.runway_light": ("Bahnbefeuerung", "Runway Light"),
    "block.f47.hangar_door": ("Hangartor", "Hangar Door"),
    "block.f47.hangar_wall": ("Hangarwand", "Hangar Wall"),
    "block.f47.radar": ("Luftraumradar", "Air Search Radar"),
    "block.f47.iron_dome": ("Iron-Dome-Startstellung", "Iron Dome Launcher"),
    "block.f47.rearm_pad": ("Wartungsfeld", "Rearm Pad"),
    "block.f47.barracks": ("Kaserne", "Barracks"),
    "block.f47.titanium_ore": ("Titanerz", "Titanium Ore"),
    "block.f47.deepslate_titanium_ore": ("Tiefenschiefer-Titanerz", "Deepslate Titanium Ore"),
    "block.f47.titanium_block": ("Titanblock", "Block of Titanium"),

    # --- Gegenstaende ---
    "item.f47.raw_titanium": ("Rohtitan", "Raw Titanium"),
    "item.f47.titanium_ingot": ("Titanbarren", "Titanium Ingot"),
    "item.f47.titanium_plate": ("Titanplatte", "Titanium Plate"),
    "item.f47.composite_plate": ("Verbundpanzerung", "Composite Plate"),
    "item.f47.avionics_module": ("Avionikmodul", "Avionics Module"),
    "item.f47.jet_engine": ("Strahltriebwerk", "Jet Engine"),
    "item.f47.stealth_coating": ("Tarnbeschichtung", "Stealth Coating"),
    "item.f47.energy_cell": ("Energiezelle", "Energy Cell"),
    "item.f47.jet_fuel": ("Flugtreibstoff", "Jet Fuel"),
    "item.f47.cannon_ammo": ("Bordkanonenmunition", "Cannon Ammunition"),
    "item.f47.aim_missile": ("AIM-Lenkwaffe", "AIM Missile"),
    "item.f47.aerial_bomb": ("Fliegerbombe", "Aerial Bomb"),
    "item.f47.interceptor_missile": ("Abfangrakete", "Interceptor Missile"),
    "item.f47.f47_aircraft": ("F-47 Kampfjet", "F-47 Fighter Jet"),
    "item.f47.f47_autonomous": ("F-47 Drohnenjaeger", "F-47 Autonomous Fighter"),
    "item.f47.b21_bomber": ("B-21 Tarnkappenbomber", "B-21 Stealth Bomber"),
    "item.f47.b21_bomber.hint": ("Rechtsklick auf den Boden: stellt den Bomber auf",
                                  "Right-click the ground: deploys the bomber"),
    "item.f47.transport_aircraft": ("Transportflugzeug", "Transport Aircraft"),
    "item.f47.transport_aircraft.hint": ("Traegt Truppen und betankt in der Luft",
                                          "Carries troops and refuels in flight"),
    "item.f47.armored_vehicle": ("Schuetzenpanzer", "Armored Personnel Carrier"),
    "item.f47.armored_vehicle.hint": ("Bringt bis zu sechs Mann ans Ziel",
                                       "Carries up to six troops to the objective"),
    "entity.f47.b21": ("B-21", "B-21"),
    "entity.f47.transport": ("Transportflugzeug", "Transport Aircraft"),
    "entity.f47.armored_vehicle": ("Schuetzenpanzer", "Armored Personnel Carrier"),
    "item.f47.laser_rifle": ("Lasergewehr", "Laser Rifle"),
    "item.f47.railgun": ("Railgun", "Railgun"),
    "item.f47.plasma_launcher": ("Plasmawerfer", "Plasma Launcher"),
    "item.f47.command_tablet": ("Kommando-Tablet", "Command Tablet"),
    "item.f47.threat_beacon": ("Uebungsalarm", "Threat Beacon"),
    "item.f47.recruit_rifleman": ("Marschbefehl: Schuetze", "Deployment Order: Rifleman"),
    "item.f47.recruit_heavy": ("Marschbefehl: Panzerabwehr", "Deployment Order: Heavy Weapons"),
    "item.f47.recruit_medic": ("Marschbefehl: Sanitaeter", "Deployment Order: Medic"),
    "item.f47.recruit_engineer": ("Marschbefehl: Techniker", "Deployment Order: Engineer"),
    "item.f47.recruit_pilot": ("Marschbefehl: Pilot", "Deployment Order: Pilot"),

    # --- Basis-Bausatz ---
    "item.f47.base_builder": ("Basis-Bausatz", "Base Construction Kit"),
    "message.f47.base_built": ("Stuetzpunkt errichtet - %s Bloecke gesetzt, Partei: %s",
                               "Base established - %s blocks placed, side: %s"),
    "tooltip.f47.base_builder_1": ("Rechtsklick auf den Boden: baut den ganzen Stuetzpunkt",
                                   "Right-click the ground: builds the entire base"),
    "tooltip.f47.base_builder_2": ("3 Hangars, 4 Iron Domes, 2 Radar, 8 Jets, 26 Mann",
                                   "3 hangars, 4 iron domes, 2 radars, 8 jets, 26 troops"),
    "tooltip.f47.base_builder_3": ("Richtet sich nach deiner Blickrichtung aus",
                                   "Lines up with the direction you are facing"),
    "tooltip.f47.base_builder_4": ("Beim Schleichen: baut die Basis der Gegenpartei",
                                   "While sneaking: builds the opposing side's base"),
    "message.f47.war_started": ("Zwei Stuetzpunkte errichtet - %s Bloecke, %s Bloecke Abstand. "
                                "Die Parteien finden sich von allein.",
                                "Two bases established - %s blocks, %s blocks apart. "
                                "The sides will find each other on their own."),
    "message.f47.status_side": ("%s: %s Maschinen, %s Mann (davon %s mit Energiewaffen)",
                                "%s: %s aircraft, %s troops (%s with energy weapons)"),
    "message.f47.status_one_side": ("Es steht nur eine Partei da - deshalb greift niemand an. "
                                    "Mit dem Bausatz im Schleichen eine Gegenbasis setzen, oder /f47 war benutzen.",
                                    "Only one side is present - that is why nobody attacks. "
                                    "Sneak-click the kit for an enemy base, or use /f47 war."),
    "message.f47.status_distance": ("Abstand der Basen: %s Bloecke - %s", "Distance between bases: %s blocks - %s"),
    "message.f47.status_ok": ("die Staffeln finden sich", "the squadrons will find each other"),
    "message.f47.status_too_far": ("zu weit auseinander, sie finden sich nie",
                                    "too far apart, they will never meet"),
    "message.f47.status_chunks": ("%s Chunks werden gerechnet (%s Gefechtsstreifen, "
                                  "%s unterwegs). Ist das 0, laeuft nur, was du gerade siehst.",
                                  "%s chunks ticking (%s battlefield, %s en route). "
                                  "If this is 0, only what you are looking at runs."),
    "message.f47.chunks_released": ("%s dauerhaft geladene Chunks freigegeben - "
                                    "die Basen laufen jetzt nur noch in deiner Naehe weiter.",
                                    "Released %s force-loaded chunks - "
                                    "bases now only run while you are nearby."),

    # --- Parteien ---
    "team.f47.blue": ("Blaue Partei", "Blue Force"),
    "team.f47.red": ("Rote Partei", "Red Force"),
    "item.f47.team_badge": ("Truppenabzeichen", "Team Badge"),
    "message.f47.team_switched": ("Du kaempfst jetzt fuer die %s",
                                  "You are now fighting for the %s"),
    "message.f47.unit_assigned": ("%s laeuft zur %s ueber", "%s defects to the %s"),
    "message.f47.building_assigned": ("%s gehoert jetzt zur %s", "%s now belongs to the %s"),
    "tooltip.f47.badge_1": ("Rechtsklick: eigene Partei wechseln",
                            "Right-click: switch your own side"),
    "tooltip.f47.badge_2": ("Auf eine Einheit: sie laeuft zu dir ueber",
                            "On a unit: it defects to your side"),
    "tooltip.f47.badge_3": ("Auf Radar/Iron Dome/Kaserne: Anlage uebernehmen",
                            "On radar/iron dome/barracks: take over the building"),

    # --- Entities ---
    "entity.f47.f47": ("F-47", "F-47"),
    "entity.f47.autonomous_f47": ("F-47 (autonom)", "F-47 (Autonomous)"),
    "entity.f47.soldier": ("Soldat", "Soldier"),
    "entity.f47.combat_drone": ("Kampfdrohne", "Combat Drone"),
    "entity.f47.missile": ("Lenkwaffe", "Missile"),
    "entity.f47.bullet": ("Geschoss", "Bullet"),
    "entity.f47.bomb": ("Bombe", "Bomb"),
    "entity.f47.plasma_bolt": ("Plasmaladung", "Plasma Bolt"),

    # --- Rollen und Zustaende ---
    "role.f47.rifleman": ("Schuetze", "Rifleman"),
    "role.f47.heavy": ("Panzerabwehr", "Heavy Weapons"),
    "role.f47.medic": ("Sanitaeter", "Medic"),
    "role.f47.engineer": ("Techniker", "Engineer"),
    "role.f47.pilot": ("Pilot", "Pilot"),
    "role.f47.laser": ("Lasertrupp", "Laser Trooper"),
    "role.f47.railgun": ("Railguntrupp", "Railgun Trooper"),
    "role.f47.plasma": ("Plasmatrupp", "Plasma Trooper"),

    "stance.f47.follow": ("Folgen", "Follow"),
    "stance.f47.hold": ("Stellung halten", "Hold Position"),
    "stance.f47.patrol": ("Patrouille", "Patrol"),

    "order.f47.parked": ("Abgestellt", "Parked"),
    "order.f47.takeoff": ("Start", "Takeoff"),
    "order.f47.patrol": ("Patrouille", "Patrol"),
    "order.f47.escort": ("Begleitschutz", "Escort"),
    "order.f47.attack": ("Angriff", "Attack"),
    "order.f47.rtb": ("Rueckflug zur Basis", "Return to Base"),

    # --- Tastenbelegung ---
    "category.f47.jet": ("F-47 Cockpit", "F-47 Cockpit"),
    "key.f47.fire_missile": ("Lenkwaffe starten", "Launch Missile"),
    "key.f47.cycle_weapon": ("Waffe wechseln", "Cycle Weapon"),
    "key.f47.stealth": ("Tarnkappe schalten", "Toggle Stealth"),
    "key.f47.afterburner": ("Nachbrenner", "Afterburner"),
    "key.f47.free_look": ("Freie Sicht", "Free Look"),

    # --- Joystick und Schubhebel ---
    "key.f47.joystick_setup": ("Joystick zuordnen", "Joystick Setup"),
    "screen.f47.joystick": ("Joystick und Schubhebel", "Joystick and Throttle"),
    "screen.f47.assign": ("Belegen", "Assign"),
    "screen.f47.invert": ("Umkehren", "Invert"),
    "screen.f47.reset": ("Alles loeschen", "Clear all"),
    "screen.f47.joystick_on": ("Joystick: an", "Joystick: on"),
    "screen.f47.joystick_off": ("Joystick: aus", "Joystick: off"),
    "screen.f47.move_axis": ("Achse jetzt bewegen ...", "Move the axis now ..."),
    "screen.f47.press_button": ("Knopf jetzt druecken ...", "Press the button now ..."),
    "screen.f47.unbound": ("nicht belegt", "not assigned"),
    "screen.f47.no_device": ("Kein Geraet gefunden - ist es angeschlossen und eingeschaltet?",
                             "No device found - is it plugged in and switched on?"),
    "screen.f47.devices": ("%s Geraet(e) gefunden, u.a. %s", "%s device(s) found, e.g. %s"),
    "screen.f47.calibrate_throttle": ("Schubhebel vermessen", "Calibrate throttle"),
    "screen.f47.calibrating": ("Hebel ganz vor und ganz zurueck - noch %s s",
                               "Move the lever through its full travel - %s s left"),
    "joystick.f47.axis.pitch": ("Hoehenruder (ziehen/druecken)", "Pitch (pull/push)"),
    "joystick.f47.axis.roll": ("Querruder (links/rechts)", "Roll (left/right)"),
    "joystick.f47.axis.yaw": ("Seitenruder (Pedale)", "Rudder (pedals)"),
    "joystick.f47.axis.throttle": ("Schubhebel", "Throttle"),
    "joystick.f47.button.fire": ("Feuern", "Fire"),
    "joystick.f47.button.missile": ("Lenkwaffe starten", "Launch missile"),
    "joystick.f47.button.cycle_weapon": ("Waffe wechseln", "Cycle weapon"),
    "joystick.f47.button.stealth": ("Tarnkappe schalten", "Toggle stealth"),
    "joystick.f47.button.afterburner": ("Nachbrenner", "Afterburner"),
    "joystick.f47.button.brake": ("Bremse (am Boden)", "Brake (on the ground)"),
    "hud.f47.joystick": ("KNUEPPEL", "STICK"),

    # --- Cockpitanzeige ---
    "hud.f47.speed": ("TEMPO", "SPEED"),
    "hud.f47.throttle": ("SCHUB", "THR"),
    "hud.f47.altitude": ("HOEHE", "ALT"),
    "hud.f47.climb": ("STEIGEN", "V/S"),
    "hud.f47.structure": ("STRUKTUR", "HULL"),
    "hud.f47.fuel": ("SPRIT", "FUEL"),
    "hud.f47.afterburner": ("NACHBRENNER", "AFTERBURNER"),
    "hud.f47.stealth_active": ("TARNUNG AKTIV", "STEALTH ON"),
    "hud.f47.stealth_broken": ("SCHACHT OFFEN", "BAY OPEN"),
    "hud.f47.lock": ("ZIEL %s m", "LOCK %s m"),
    "hud.f47.radar": ("RADAR - %s Bedrohungen", "RADAR - %s threats"),
    "hud.f47.warn_stall": ("! STROEMUNGSABRISS !", "! STALL !"),
    "hud.f47.warn_fuel": ("! TREIBSTOFF NIEDRIG !", "! LOW FUEL !"),
    "hud.f47.warn_no_fuel": ("! TRIEBWERK AUS !", "! ENGINE OUT !"),
    "hud.f47.warn_pullup": ("! HOCHZIEHEN !", "! PULL UP !"),
    "hud.f47.weapon.cannon": ("BORDKANONE", "CANNON"),
    "hud.f47.weapon.missile": ("LENKWAFFE", "MISSILE"),
    "hud.f47.weapon.bomb": ("BOMBE", "BOMB"),
    "hud.f47.weapon.laser": ("LASER", "LASER"),

    # --- Meldungen ---
    "message.f47.stealth_on": ("Tarnkappenmodus aktiv", "Stealth mode engaged"),
    "message.f47.stealth_off": ("Tarnkappenmodus aus", "Stealth mode off"),
    "message.f47.jet_status": ("%s - Auftrag: %s | Struktur: %s | Raketen: %s",
                               "%s - order: %s | hull: %s | missiles: %s"),
    "message.f47.order_set": ("%s bestaetigt: %s", "%s acknowledges: %s"),
    "message.f47.base_set": ("Heimatbasis gesetzt - %s Jets und %s Soldaten zugewiesen",
                             "Home base set - %s jets and %s troops assigned"),
    "message.f47.stance_changed": ("%s: %s", "%s: %s"),
    "message.f47.recruit_ready": ("%s meldet sich zum Dienst", "%s reporting for duty"),
    "message.f47.soldier_ready": ("Ausbildung abgeschlossen: %s", "Training complete: %s"),
    "message.f47.jet_ready": ("Ersatzmaschine %s steht bereit", "Replacement aircraft %s ready"),
    "message.f47.pilot_boarded": ("Pilot in %s eingestiegen - startklar",
                                  "Pilot boarded %s - ready for launch"),
    "message.f47.autojet_placed": ("%s abgestellt - Pilot zuweisen oder Tablet benutzen",
                                   "%s parked - assign a pilot or use the tablet"),
    "message.f47.bingo_fuel": ("%s meldet wenig Treibstoff, kehrt zurueck",
                               "%s bingo fuel, returning to base"),
    "message.f47.on_station": ("%s auf Patrouillenhoehe", "%s on station"),
    "message.f47.scramble": ("%s startet zum Alarmeinsatz", "%s scrambling"),
    "message.f47.landed": ("%s ist gelandet", "%s has landed"),
    "message.f47.lost": ("%s ist verloren gegangen", "%s has been lost"),
    "message.f47.engaging": ("%s greift an", "%s engaging"),
    "message.f47.radar_online": ("Radar aktiv - %s Kontakte", "Radar online - %s contacts"),
    "message.f47.radar_offline": ("Radar abgeschaltet", "Radar offline"),
    "message.f47.dome_loaded": ("%s Abfangraketen geladen (%s/%s)",
                                "Loaded %s interceptors (%s/%s)"),
    "message.f47.dome_full": ("Startstellung ist voll", "Launcher is full"),
    "message.f47.dome_status": ("Iron Dome: %s/%s Raketen | %s Abfaenge",
                                "Iron Dome: %s/%s missiles | %s intercepts"),
    "message.f47.barracks_supplied": ("Nachschub: %s/%s Eisen", "Supplies: %s/%s iron"),
    "message.f47.barracks_role": ("Ausbildung umgestellt auf: %s", "Now training: %s"),
    "message.f47.barracks_status": ("Kaserne - Ausbildung: %s | Nachschub: %s | Trupp: %s/%s",
                                    "Barracks - training: %s | supplies: %s | squad: %s/%s"),
    "message.f47.raid_incoming": ("%s feindliche Drohnen im Anflug!",
                                  "%s hostile drones inbound!"),
    "message.f47.raid_warning": ("Radarwarnung: %s Drohnen naehern sich der Basis!",
                                 "Radar warning: %s drones approaching the base!"),
    "message.f47.report_header": ("=== Lagebericht ===", "=== Situation Report ==="),
    "message.f47.report_empty": ("Keine Einheiten in Reichweite", "No units in range"),
    "message.f47.report_jet": ("%s | %s | Struktur %s | Raketen %s | Sprit %s%%",
                               "%s | %s | hull %s | missiles %s | fuel %s%%"),
    "message.f47.report_troops": ("Bodentruppen in Reichweite: %s", "Ground troops in range: %s"),
    "message.f47.report_radar": ("Radar: %s Kontakte, davon %s feindlich",
                                 "Radar: %s contacts, %s hostile"),

    # --- Kurzhinweise auf Gegenstaenden ---
    "tooltip.f47.jet": ("Auf den Boden setzen und einsteigen",
                        "Place on the ground and climb in"),
    "tooltip.f47.jet_hint": ("W/S Schub, Maus steuern, Leertaste feuern",
                             "W/S throttle, mouse to fly, space to fire"),
    "tooltip.f47.autonomous_jet": ("Fliegt selbstaendig - braucht einen Piloten",
                                   "Flies on its own - needs a pilot"),
    "tooltip.f47.autonomous_jet_hint": ("Auftraege mit dem Kommando-Tablet vergeben",
                                        "Assign orders with the command tablet"),
    "tooltip.f47.energy": ("Energie: %s/%s", "Charge: %s/%s"),
    "tooltip.f47.weapon.laser": ("Sofortiger Strahl, entzuendet das Ziel",
                                 "Instant beam, sets targets alight"),
    "tooltip.f47.weapon.railgun": ("Aufladen und loslassen - durchschlaegt mehrere Ziele",
                                   "Hold to charge - pierces several targets"),
    "tooltip.f47.weapon.plasma": ("Explodierende Plasmaladung", "Explosive plasma bolt"),
    "tooltip.f47.tablet_1": ("Rechtsklick auf Jet: naechster Auftrag",
                             "Right-click a jet: next order"),
    "tooltip.f47.tablet_2": ("Rechtsklick auf Soldat: Verhalten wechseln",
                             "Right-click a soldier: change stance"),
    "tooltip.f47.tablet_3": ("Rechtsklick auf Block: Basis festlegen",
                             "Right-click a block: set home base"),
    "tooltip.f47.tablet_4": ("Rechtsklick in die Luft: Lagebericht",
                             "Right-click in the air: situation report"),
    "tooltip.f47.recruit": ("Stellt auf: %s", "Deploys: %s"),
    "tooltip.f47.threat_beacon": ("Ruft eine feindliche Drohnenwelle herbei",
                                  "Calls in a wave of hostile drones"),
    "tooltip.f47.threat_beacon_hint": ("Zum Testen der Luftabwehr",
                                       "For testing your air defence"),

    # --- Todesmeldungen ---
    "death.attack.f47_laser": ("%s wurde von einem Laser durchbohrt",
                               "%s was cut down by a laser"),
    "death.attack.f47_laser.player": ("%s wurde von %s mit dem Laser erschossen",
                                      "%s was shot by %s with a laser"),
    "death.attack.f47_missile": ("%s wurde von einer Lenkwaffe erfasst",
                                 "%s was hit by a guided missile"),
    "death.attack.f47_missile.player": ("%s wurde von %s abgeschossen",
                                        "%s was shot down by %s"),
    "death.attack.f47_bullet": ("%s wurde erschossen", "%s was gunned down"),
    "death.attack.f47_bullet.player": ("%s wurde von %s erschossen", "%s was gunned down by %s"),
}


def main():
    os.makedirs(LANG, exist_ok=True)
    for index, filename in ((0, "de_de.json"), (1, "en_us.json")):
        payload = {key: value[index] for key, value in ENTRIES.items()}
        with open(os.path.join(LANG, filename), "w", encoding="utf-8") as handle:
            json.dump(payload, handle, indent=2, ensure_ascii=False)
            handle.write("\n")
    print(f"{len(ENTRIES)} Eintraege je Sprache geschrieben.")


if __name__ == "__main__":
    main()
