# F-47 Air Force — Minecraft-Mod

Ein kompletter kleiner Luftwaffenstützpunkt für **Minecraft Java Edition 1.21.1** (Fabric):
fliegbare F-47 mit Waffen und Tarnkappenmodus, autonome Jets mit Bot-Piloten,
Start- und Landebahnen mit Hangar, Luftraumradar, ein Iron-Dome-Abwehrsystem,
Energiewaffen und Bodenpersonal.

**Zwei Parteien** können vollautomatisch gegeneinander Krieg führen — und du
kannst jederzeit selbst einsteigen und für eine Seite mitfliegen und mitkämpfen.
Wer lieber nur eine Basis aufbaut, merkt vom Team-System nichts.

Fliegen geht mit Maus und Tastatur — oder mit **Joystick und Schubhebel**.

Gebaut für den **Einzelspielermodus**.

---

## 1. Installation

Du brauchst dreierlei — alles kostenlos:

| Schritt | Was | Wo |
|---|---|---|
| 1 | **Minecraft Java Edition 1.21.1** einmal starten | Minecraft Launcher |
| 2 | **Fabric Loader** für 1.21.1 installieren | https://fabricmc.net/use/installer/ |
| 3 | **Fabric API** für 1.21.1 herunterladen | https://modrinth.com/mod/fabric-api |

Dann:

1. Fabric-Installer starten → Reiter **„Client"** → Minecraft-Version **1.21.1** wählen → **Installieren**.
2. Den Mod-Ordner öffnen:
   - Windows: `%appdata%\.minecraft\mods` (in die Adressleiste des Explorers einfügen)
   - Falls der Ordner `mods` nicht existiert: einfach anlegen.
3. In diesen Ordner **zwei** Dateien legen:
   - `fabric-api-0.116.15+1.21.1.jar` (aus Schritt 3 oben)
   - `f47-military-mod-1.0.0.jar` (dieser Mod, siehe unten)
4. Im Minecraft Launcher das Profil **„fabric-loader-1.21.1"** auswählen und starten.

Fertig — im Kreativmodus gibt es einen neuen Reiter **„F-47 Luftwaffe"** mit allen Inhalten.

### Die Mod-Datei bauen

Die fertige `.jar` liegt nach einem Build unter `build/libs/f47-military-mod-1.0.0.jar`:

```bash
cd minecraft-f47-mod
./gradlew build
```

(Nimm die Datei **ohne** `-sources` im Namen.)

---

## 2. Schnellstart

Im Kreativmodus, damit du sofort losfliegen kannst:

1. **Bahn bauen:** ein paar Dutzend `Startbahnbelag` in einer geraden Linie legen
   (mindestens ~40 Blöcke lang, damit die Maschine Fahrt aufnimmt).
2. **Jet abstellen:** `F-47 Kampfjet` in die Hand nehmen und auf die Bahn rechtsklicken.
3. **Einsteigen:** Rechtsklick auf den Jet.
4. **Starten:** `W` gedrückt halten (Schub hoch), Maus leicht nach oben ziehen,
   sobald die Anzeige links über ~85 km/h zeigt — die F-47 hebt ab.
5. **Fliegen:** Die Maus steuert. Der Jet folgt deiner Blickrichtung.
6. **Schießen:** `Leertaste`. Mit `X` schaltest du zwischen Bordkanone, Lenkwaffe,
   Bombe und Laser um.
7. **Aussteigen:** `Linke Umschalttaste` (am besten am Boden).

> **Wichtig:** Ohne Schub fällt die Maschine wie ein Stein — genau wie ein echtes
> Flugzeug braucht die F-47 Fahrt, um zu fliegen. Die Warnung
> `! STRÖMUNGSABRISS !` bedeutet: mehr Gas geben und die Nase senken.

---

## 3. Die zwei Parteien

Jede Einheit — Jet, Soldat, Drohne, Radarstation, Iron Dome, Kaserne — gehört
genau **einer Partei** an: der **blauen** oder der **roten**. Die Regeln sind
kurz:

- **Gleiche Partei:** wird nie beschossen, auch nicht versehentlich.
- **Andere Partei:** gültiges Ziel für alles — Jets, Flugabwehr, Bodentruppen.
- **Monster der Welt** (Zombies, Ghasts, …): Gegner **beider** Parteien.

Der letzte Punkt ist der Grund, warum der Mod auch dann funktioniert, wenn du
nur eine Partei aufstellst: Dann gibt es schlicht keine Gegenseite, und es
bleibt beim Kampf gegen die Kreaturen der Welt und gegen die Drohnenangriffe.
**Du musst dich um Parteien also nur kümmern, wenn du willst** — ohne Zutun ist
alles blau.

### Zwei Parteien aufstellen

Dafür gibt es genau einen Gegenstand: das **Truppenabzeichen**.

| Aktion | Wirkung |
|---|---|
| Rechtsklick in die Luft | Du wechselst selbst die Seite (blau ↔ rot) |
| Rechtsklick auf eine Einheit | Diese Einheit läuft zu **deiner** Partei über |
| Rechtsklick auf Radar/Iron Dome/Kaserne | Die Anlage wechselt die Seite |

**Alles, was du aufstellst, gehört automatisch zu der Partei, für die du gerade
kämpfst.** Ein typischer Ablauf für einen Zweifrontenkrieg:

1. Blaue Basis bauen: Startbahn, Hangar, Radar, Iron Dome, Kaserne, ein paar
   `F-47 Drohnenjäger` und `Marschbefehl: Pilot`.
2. Truppenabzeichen rechtsklicken → **du kämpfst jetzt für Rot**.
3. Ein Stück entfernt die rote Basis genauso aufbauen.
4. Truppenabzeichen erneut rechtsklicken → zurück zu **Blau**.
5. Beiden Seiten mit dem Kommando-Tablet den Auftrag `Angriff` geben.

Ab hier läuft der Krieg von allein: Die Bot-Piloten starten, suchen sich Ziele,
schießen Lenkwaffen ab; die Iron-Dome-Stellungen fangen die Raketen der
Gegenseite ab; die Bodentruppen schießen aufeinander.

### Selbst mitkämpfen

Du gehörst immer einer der beiden Parteien an (Standard: blau). Steigst du in
eine F-47 deiner Partei, kämpfst du für diese Seite:

- Der **Radarschirm** zeigt eigene Einheiten grün, gegnerische rot — bewertet
  aus Sicht deiner Partei.
- Oben rechts am Radar steht, für welche Partei du gerade fliegst.
- **Die Gegenseite greift dich aktiv an:** Gegnerische Jets schalten auf dich
  auf, ihr Iron Dome fängt deine Raketen ab, ihre Soldaten schießen auf dich.
- Der **Tarnkappenmodus** wirkt gegen die gegnerische Ortung — genau hier zahlt
  er sich aus.

Mit dem Truppenabzeichen kannst du mitten im Gefecht die Seite wechseln; die
Einheiten, die du danach aufstellst, gehören der neuen Partei.

---

## 4. Steuerung im Cockpit

| Taste | Wirkung |
|---|---|
| `W` / `S` | Schub erhöhen / verringern |
| `Maus` | Fliegen (der Jet folgt der Blickrichtung) |
| `Leertaste` | Gewählte Waffe abfeuern |
| `R` | Lenkwaffe abfeuern (unabhängig von der Waffenwahl) |
| `X` | Waffe wechseln |
| `V` | Tarnkappenmodus an/aus |
| `C` | Nachbrenner |
| `Linke Alt-Taste` | Freie Sicht (Jet hält den Kurs) |
| `Linke Umschalttaste` | **Aussteigen** (Minecraft-Standard) |
| `J` | Joystick und Schubhebel zuordnen (siehe Kapitel 5) |

Alle Tasten lassen sich in den **Einstellungen → Steuerung → „F-47 Cockpit"** ändern.
Die Umschalttaste bleibt frei, weil man damit in Minecraft aus einem Fahrzeug
aussteigt — der Nachbrenner liegt deshalb auf `C`.

### Die Cockpitanzeige

- **Links:** Geschwindigkeit in km/h und Schubhebel
- **Rechts:** Höhe und Steig-/Sinkrate
- **Mitte:** Zielkreuz; ein roter Kasten bedeutet **Ziel aufgeschaltet** —
  erst dann trifft eine Lenkwaffe zuverlässig
- **Unten:** Waffe, Munition, Struktur (Panzerung) und Treibstoff
- **Unten rechts:** Radarschirm. Grün = eigene Einheiten, Rot = Feind.
  Die Nase zeigt immer nach oben.

---

## 5. Joystick und Schubhebel

Der Mod liest Joysticks, Schubhebel und Pedale direkt über GLFW aus — die
Bibliothek, auf der Minecraft ohnehin aufsetzt. Es braucht also **kein
zusätzliches Programm** und keine Tastenemulation.

### Einrichten (dauert eine Minute)

Achsen- und Knopfnummern sind bei jedem Hersteller anders. Deshalb rät der Mod
nicht herum, sondern lernt dein Gerät an:

1. Gerät anschließen, Minecraft starten, Welt betreten.
2. **`J` drücken** — der Bildschirm „Joystick und Schubhebel" geht auf.
   Oben steht, welche Geräte gefunden wurden.
3. Bei **Höhenruder** auf `Belegen` klicken und den Stick **vor und zurück**
   bewegen. Der Mod erkennt selbst, welche Achse das war.
4. Dasselbe für **Querruder** (Stick links/rechts), **Seitenruder** (Pedale
   oder Drehgriff) und **Schubhebel**.
5. Auf **`Schubhebel vermessen`** klicken und den Hebel innerhalb von 5 Sekunden
   einmal ganz vor und ganz zurück schieben. Damit kennt der Mod den echten Weg
   deines Hebels — viele geben nämlich nicht den vollen Bereich aus.
6. Die Knöpfe (Feuern, Lenkwaffe, Waffe wechseln, Tarnkappe, Nachbrenner,
   Bremse) genauso: `Belegen` klicken, Knopf drücken. **Hutschalter gehen auch.**

Läuft eine Achse verkehrt herum, hilft der Knopf `Umkehren` daneben. Neben
jeder Achse siehst du einen **Balken, der live mitgeht** — daran erkennst du
sofort, ob alles richtig sitzt.

Alles wird in `config/f47-joystick.json` gespeichert, du machst es also nur
einmal.

> **Zwei getrennte Geräte** (Stick und Schubhebel separat) sind ausdrücklich
> vorgesehen: Jede Belegung merkt sich, von **welchem** Gerät sie kommt.

### Wie es sich fliegt

Sobald Höhen- und Querruder belegt sind, schaltet der Jet automatisch auf
**echte Knüppelsteuerung** um — der Jet folgt dann nicht mehr der Blickrichtung,
sondern du steuerst Ruder für Ruder wie in einem Flugsimulator:

- **Stick links/rechts** legt die Maschine in die Querlage.
- **Ziehen** bringt sie dann in die Kurve. Nur ziehen ohne Querlage ergibt einen
  Looping — erst rollen, dann ziehen ergibt eine saubere Kurve.
- **Bei wenig Fahrt greifen die Ruder schlechter**, genau wie in echt.
- Der **Schubhebel gibt den Schub absolut vor** — kein Hochtippen mehr.
- Die **Sicht rollt mit der Maschine mit**, damit der Horizont stimmt.
  Wem davon schwindelig wird, setzt `cameraRoll` in der Config auf `0`.

Maus und Tastatur funktionieren weiterhin parallel — die Knöpfe am Stick und
die Tasten lösen dieselben Funktionen aus.

### Feineinstellung in `config/f47-joystick.json`

| Wert | Bedeutung | Standard |
|---|---|---|
| `sensitivity` | Empfindlichkeit der Ruder | `1.0` |
| `curve` | Höher = feinfühliger um die Mitte, gut zum Zielen | `1.6` |
| `cameraRoll` | Wie stark die Sicht mitrollt (0 = gar nicht) | `1.0` |
| `deadzone` | Totbereich je Achse, gegen Zittern in Ruhelage | `0.08` |
| `enabled` | Joysticksteuerung ganz abschalten | `true` |

---

## 6. Die Systeme im Einzelnen

### F-47 Kampfjet

Vier Waffenstationen:

| Waffe | Einsatz |
|---|---|
| **Bordkanone** | Schnellfeuer, wenig Schaden pro Treffer — gut auf kurze Distanz |
| **AIM-Lenkwaffe** | Sucht sich das aufgeschaltete Ziel selbst, Näherungszünder |
| **Freifallbombe** | Ballistisch, für Bodenziele im Tiefflug |
| **Laserkanone** | Trifft sofort ohne Vorhalt, verbraucht keine Munition |

**Tarnkappenmodus (`V`):** Radar und Flugabwehr erfassen dich erst auf etwa einem
Fünftel der normalen Entfernung. Beim Abfeuern von Kanone, Rakete oder Bombe
öffnen sich die Waffenschächte — dann ist die Tarnung für 3 Sekunden wirkungslos
(die Anzeige wechselt auf `SCHACHT OFFEN`). Der Laser verrät dich nicht.

**Treibstoff** geht zur Neige — der Nachbrenner verbraucht das Dreifache.
Ist der Tank leer, steht das Triebwerk und du fliegst nur noch im Gleitflug.

### Autonome Jets und Bot-Piloten

1. `F-47 Drohnenjäger` auf die Bahn setzen — die Maschine steht zunächst ohne Besatzung da.
2. Einen `Marschbefehl: Pilot` daneben benutzen. Der Pilot läuft selbstständig
   zur Maschine, steigt ein, und der Jet bekommt ein Rufzeichen (z. B. „Ghost 3"),
   das über ihm schwebt.
3. Mit dem `Kommando-Tablet` per Rechtsklick auf den Jet den Auftrag durchschalten:

   `Abgestellt` → `Start` → `Patrouille` → `Begleitschutz` → `Angriff` → `Rückflug zur Basis`

Die Bot-Piloten fliegen selbst: Sie steigen auf Patrouillenhöhe, kreisen um die
Basis, greifen Feinde mit Raketen und Kanone an, melden sich per Chat
(„Ghost 3 auf Patrouillenhöhe") und kehren bei wenig Treibstoff eigenständig zurück.

### Bodentruppen

Fünf Rollen, jede mit eigener Aufgabe:

| Rolle | Aufgabe |
|---|---|
| **Schütze** | Standardinfanterie, feuert Salven |
| **Panzerabwehr** | Verschießt Raketen auf Fahrzeuge und Gruppen |
| **Sanitäter** | Heilt verwundete Soldaten und dich |
| **Techniker** | Repariert und betankt Jets am Boden |
| **Pilot** | Besteigt bereitstehende autonome Jets |

Rechtsklick auf einen Soldaten (oder mit dem Kommando-Tablet) schaltet sein
Verhalten um: `Folgen` → `Stellung halten` → `Patrouille`.

Die **Kaserne** bildet laufend Nachschub aus: Eisenbarren hineingeben (Rechtsklick),
mit `Schleichen + Rechtsklick` die Rolle wählen. Alle 30 Sekunden rückt ein
Soldat aus, solange Nachschub da ist.

### Radar und Iron Dome

Das **Luftraumradar** tastet 220 Blöcke ab, meldet neue Bedrohungen mit einem
Alarmton und speist:
- deinen Radarschirm (wenn du ein Kommando-Tablet dabei hast),
- die Iron-Dome-Stellungen in der Nähe (deren Reichweite steigt dadurch um 60 %).

Die **Iron-Dome-Startstellung** wird mit `Abfangrakete`n geladen (Rechtsklick).
Sie bewertet Ziele nach Gefährlichkeit — anfliegende Raketen zuerst, dann
Ghast-Feuerbälle, dann Drohnen — ignoriert alles, was sich bereits entfernt, und
schießt nie zweimal auf dasselbe Ziel. Rechtsklick zeigt Munitionsstand und die
Zahl der erfolgreichen Abfänge.

### Energiewaffen

| Waffe | Wirkung |
|---|---|
| **Lasergewehr** | Sofortiger Strahl, entzündet das Ziel |
| **Railgun** | Rechte Maustaste halten zum Aufladen — durchschlägt bis zu 4 Gegner |
| **Plasmawerfer** | Langsame, explodierende Plasmakugel |

Sie brauchen keine Munition: Die Haltbarkeitsleiste ist der **Ladestand der
Energiezelle**, der sich im Inventar von selbst wieder auflädt.

### Kampfdrohnen

Kampfdrohnen gehören ebenfalls einer Partei an — **beide Seiten können sie
einsetzen**. Sie fliegen Angriffe auf gegnerische Truppen und Flugzeuge und
verschießen dabei Raketen: genau die Ziele, für die der Iron Dome gebaut ist.
Getarnte Jets erfassen sie erst sehr spät.

Damit auch bei nur einer Partei etwas los ist, greifen Drohnen der Gegenseite
von selbst an:

- **Zufällige Angriffe:** nachts, wenn eine Radarstation in der Nähe steht
  (etwa alle 20 Minuten, abschaltbar). Die Angreifer gehören immer der Partei
  an, gegen die du kämpfst.
- **Auf Kommando:** Der `Übungsalarm` ruft sofort eine Welle herbei — praktisch,
  um die Abwehr zu testen.

---

## 7. Basis bauen (Vorschlag)

```
     ══════════════════════════════════  ← Startbahn (≥40 Blöcke)
     ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─   ← Mittellinie
   ▓▓▓▓▓                        📡        ← Hangar (Wände + Tor) / Radar
   ▓ 🛠 ▓                     🚀 🚀       ← Wartungsfeld / Iron Dome
   ▓▓▓▓▓   🏠                             ← Kaserne
```

- Die **Startbahn** braucht Länge: unter ~40 Blöcken wird es eng.
- Das **Hangartor** schaltet sich mit einem Rechtsklick — und alle direkt
  angrenzenden Torsegmente fahren gleich mit hoch. So bekommst du ein großes
  Tor über die ganze Hangarbreite.
- Das **Wartungsfeld** vor dem Hangar: Jets, die darauf abgestellt werden,
  betanken, reparieren und bewaffnen sich von selbst.
- Setze die **Heimatbasis** einmal fest: mit dem Kommando-Tablet auf einen Block
  in der Mitte der Basis rechtsklicken. Alle Jets und Soldaten im Umkreis von
  96 Blöcken bekommen diesen Punkt als Heimat und Wachposten.

---

## 8. Im Überlebensmodus spielen

Der Mod bringt einen eigenen Rohstoff mit: **Titanerz** kommt zwischen Y = −32
und Y = 64 vor (etwas seltener als Eisen) und braucht mindestens eine
Eisenspitzhacke.

Der Weg dorthin:

```
Rohtitan  →(schmelzen)→  Titanbarren  →  Titanplatte
                                          ├→ Verbundpanzerung
                                          ├→ Avionikmodul
                                          ├→ Energiezelle
                                          ├→ Strahltriebwerk
                                          └→ Tarnbeschichtung
                                                    ↓
                                              F-47 Kampfjet
```

Alle Rezepte findest du im Spiel über das **Rezeptbuch**.

---

## 9. Einstellungen

Beim ersten Start entsteht die Datei `config/f47.json` im Minecraft-Ordner.
Dort lässt sich vieles anpassen, ohne den Mod neu zu bauen:

| Einstellung | Bedeutung | Standard |
|---|---|---|
| `maxSpeed` | Höchstgeschwindigkeit (Blöcke/Tick) | `4.6` |
| `thrust` | Beschleunigung | `0.055` |
| `stallSpeed` | Ab hier tragen die Flügel | `0.85` |
| `explosionsBreakBlocks` | Explosionen zerstören Blöcke | `true` |
| `enableRandomRaids` | Nächtliche Drohnenangriffe | `true` |
| `jetMaxHealth` | Panzerung der F-47 | `60` |
| `stealthDetectionFactor` | Wie stark Tarnung wirkt (kleiner = besser) | `0.22` |

**Tipp:** Wenn dir die Explosionen deine Basis zerlegen, setze
`explosionsBreakBlocks` auf `false`.

---

## 10. Aufbau des Projekts

```
minecraft-f47-mod/
├── src/main/java/com/f47mod/
│   ├── entity/vehicle/     F-47, autonome Jets, Flugmodell
│   ├── entity/mob/         Soldaten, feindliche Drohnen
│   ├── entity/ai/          Verhalten der Truppen (Folgen, Wache, Sanitäter …)
│   ├── entity/projectile/  Raketen, Bomben, Geschosse, Plasma
│   ├── block/              Startbahn, Hangar, Radar, Iron Dome, Kaserne
│   ├── item/               Jets, Energiewaffen, Kommando-Tablet
│   ├── net/                Client-Server-Kommunikation, Radarkontakte
│   ├── world/              Weltgenerierung, Radarübertragung, Angriffswellen
│   └── util/Iff.java       Freund-Feind-Erkennung
├── src/client/java/        Renderer, Modelle, Cockpitanzeige, Tastenbelegung
│   ├── input/              Joystick, Schubhebel und Pedale (GLFW)
│   ├── gui/                Zuordnungs-Bildschirm für die Geräte
│   └── mixin/              Kamera rollt mit der Maschine mit
└── tools/                  Erzeugen Texturen, Modelle, Rezepte, Sprachdateien
```

Texturen und die meisten JSON-Dateien werden von den Skripten in `tools/`
erzeugt — so bleibt alles reproduzierbar:

```bash
python3 tools/gen_assets.py   # Texturen, Modelle, Rezepte, Loot-Tabellen
python3 tools/gen_lang.py     # Sprachdateien (Deutsch + Englisch)
```

---

## 11. Wenn etwas nicht funktioniert

| Problem | Lösung |
|---|---|
| Minecraft startet nicht | Fabric API im `mods`-Ordner? Profil `fabric-loader-1.21.1` gewählt? |
| Der Jet hebt nicht ab | Längere Bahn bauen, `W` halten, erst ab ~85 km/h die Nase heben |
| Der Jet stürzt sofort ab | Schub zu niedrig — die Anzeige `! STRÖMUNGSABRISS !` beachten |
| Autonomer Jet bleibt stehen | Er braucht einen Piloten (`Marschbefehl: Pilot` daneben benutzen) |
| Iron Dome schießt nicht | Mit `Abfangrakete`n laden (Rechtsklick) |
| Soldaten greifen nicht an | Schwierigkeitsgrad steht auf „Friedlich" |
| Meine Einheiten kämpfen nicht gegeneinander | Beide gehören derselben Partei — mit dem Truppenabzeichen umstellen |
| Einheit wechselt die Seite nicht | Truppenabzeichen direkt auf die Einheit rechtsklicken, nicht daneben |
| Joystick wird nicht gefunden | Vor dem Spielstart anschließen, dann `J` drücken — oben steht die Geräteliste |
| Achse läuft verkehrt herum | Im Zuordnungs-Bildschirm auf `Umkehren` klicken |
| Schubhebel geht nur halb | `Schubhebel vermessen` und den Hebel einmal ganz durchschieben |
| Stick zittert in Ruhelage | `deadzone` in `config/f47-joystick.json` erhöhen (z. B. `0.15`) |

---

## Lizenz

MIT
