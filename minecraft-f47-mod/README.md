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

1. **Basis aufstellen:** `Basis-Bausatz` in die Hand nehmen, in die Richtung
   schauen, in die die Startbahn zeigen soll, und **auf den Boden rechtsklicken**.
   Der ganze Stützpunkt entsteht in einem Rutsch — du musst nichts von Hand bauen.
2. **Einsteigen:** Rechtsklick auf die F-47 am Anfang der Bahn.
3. **Starten:** `W` gedrückt halten (Schub hoch), Maus leicht nach oben ziehen,
   sobald die Anzeige links über ~120 km/h zeigt — die F-47 hebt ab.
4. **Fliegen:** Die Maus steuert. Der Jet folgt deiner Blickrichtung.
5. **Schießen:** **Linke Maustaste**. Mit `X` schaltest du zwischen Bordkanone,
   Lenkwaffe, Bombe und Laser um.
6. **Aussteigen:** `Linke Umschalttaste` (am besten am Boden).

### Was der Basis-Bausatz hinstellt

Rund 15 000 Blöcke, ausgerichtet nach deiner Blickrichtung:

| Teil | Beschreibung |
|---|---|
| **Startbahn** | 96 Blöcke lang, 11 breit, mit Mittellinie, Schwellen und Befeuerung |
| **3 × Hangar** | je 11 × 10 × 6 mit Rolltor — breit genug für die Spannweite |
| **Wartungsfelder** | in jedem Hangar — betanken, reparieren und bewaffnen abgestellte Jets |
| **2 × Radarstation** | an beiden Enden, für Rundumsicht |
| **4 × Iron Dome** | bereits voll mit Abfangraketen geladen |
| **2 × Kaserne** | mit je 64 Eisenbarren Nachschub, bilden sofort Soldaten aus |
| **8 Maschinen** | 2 F-47 für dich, 6 Drohnenjäger |
| **26 Mann** | 6 Piloten, 3 Techniker, 2 Sanitäter, 3 Panzerabwehr, 12 Schützen |

Das Gelände wird dabei planiert: Bewuchs kommt weg, Senken werden aufgefüllt.
Auf **Superflach** sieht es am saubersten aus.

Beim Bauen stockt das Spiel einmalig ein bis zwei Sekunden, wenn die Anlage auf
Gelände steht, das du noch nie besucht hast — Minecraft muss die Landschaft dort
erst erzeugen. Die Blöcke selbst werden über mehrere Ticks verteilt gesetzt, du
siehst den Stützpunkt also entstehen, statt dass das Spiel einfriert.

> **Es passiert nichts?** `/f47 status` eintippen. Der Lagebericht sagt, wie
> viele Maschinen und Mann jede Partei hat und wie weit die Basen auseinander
> liegen. Die mit Abstand häufigste Ursache: Es steht nur **eine** Partei da.

> **Kein Bausatz zur Hand?** Der Befehl `/f47 base` baut dasselbe an deiner
> Position. Mit `/f47 base <x> <y> <z>` auch woanders, und mit
> `/f47 base <x> <y> <z> rot` gleich für die Gegenpartei.

> **Die Piloten steigen selbst ein und starten von allein.** Kurz nach dem Bau
> laufen sie zu den Drohnenjägern und übernehmen sie — danach tragen die
> Maschinen ihre Rufzeichen. Nach einer knappen halben Minute hebt die erste ab,
> die übrigen zeitversetzt, damit immer ein Teil der Staffel am Boden auftankt.
> Aufträge gibst du mit dem Kommando-Tablet, nötig ist das aber nicht.

> **Die Basis läuft weiter, auch wenn du weg bist.** Beim Bauen bleiben die
> Chunks rund um den Stützpunkt dauerhaft geladen (Standard: 6 Chunks Radius,
> deckt den Patrouillenkreis ab). Ohne das rechnet Minecraft nur in deiner
> Umgebung, und die ganze Anlage steht still, sobald du wegfliegst. Bremst das
> deinen Rechner, stell `baseForceLoadRadiusChunks` in `config/f47.json` kleiner
> oder auf `0`; `/f47 unload` gibt alle geladenen Chunks sofort wieder frei.

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
kämpfst.**

#### Der kürzeste Weg: `/f47 war`

Ein Befehl stellt **beide** Stützpunkte auf einmal hin — blau und rot, 150
Blöcke auseinander, mit den Bahnen parallel zueinander. Das ist genau der
Abstand, bei dem die Patrouillen einander in die Bordradarreichweite fliegen.
Mehr ist nicht zu tun: Nach gut einer halben Minute starten die ersten
Maschinen, und der Krieg läuft.

> Das ist der einzige Befehl, bei dem das Spiel spürbar stockt (etwa vier
> Sekunden), weil zwei komplette Anlagen gleichzeitig auf frischem Gelände
> entstehen. Danach ist Ruhe.

#### Von Hand, mit dem Bausatz

1. Basis-Bausatz auf den Boden rechtsklicken → **deine** (blaue) Basis.
2. Ein Stück weiter **im Schleichen** rechtsklicken → die **rote** Basis.
3. Fertig.

Der Abstand sollte zwischen etwa 120 und 250 Blöcken liegen. Zu nah, und die
Bahnen überbauen sich; zu weit, und die Patrouillen finden einander nie.

Alternativ setzt das **Truppenabzeichen** deine eigene Seite um:

| Aktion | Wirkung |
|---|---|
| Rechtsklick in die Luft | Du wechselst selbst die Seite (blau ↔ rot) |
| Rechtsklick auf eine Einheit | Diese Einheit läuft zu **deiner** Partei über |
| Rechtsklick auf Radar/Iron Dome/Kaserne | Die Anlage wechselt die Seite |

Ab hier läuft der Krieg von allein: Die Bot-Piloten starten von sich aus, suchen
sich Ziele, schießen Lenkwaffen ab; die Iron-Dome-Stellungen fangen die Raketen
der Gegenseite ab; die Bodentruppen schießen aufeinander. In einem Testlauf ohne
jeden Spieler waren von zwölf Maschinen nach drei Minuten fünf abgeschossen und
fünf weitere hatten ihre Raketen restlos verschossen.

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
| `W` / `S` | Schub erhöhen / verringern (Minecrafts normale Lauftasten) |
| `Maus` | Fliegen (der Jet folgt der Blickrichtung) |
| `Linke Maustaste` | Gewählte Waffe abfeuern |
| `R` | Lenkwaffe abfeuern (unabhängig von der Waffenwahl) |
| `X` | Waffe wechseln |
| `V` | Tarnkappenmodus an/aus |
| `C` | Nachbrenner |
| `Linke Alt-Taste` | Freie Sicht (Jet hält den Kurs) |
| `Linke Umschalttaste` | **Aussteigen** (Minecraft-Standard) |
| `J` | Joystick und Schubhebel zuordnen (siehe Kapitel 5) |

Alle Tasten lassen sich in den **Einstellungen → Steuerung → „F-47 Cockpit"** ändern.

**Warum Schub und Feuern keine eigenen Tasten haben:** Minecraft erlaubt pro
Taste nur *eine* Belegung — ein Mod auf `W` würde also das Laufen abschalten.
Deshalb liest der Mod im Cockpit einfach Minecrafts eigene Vorwärts-,
Rückwärts- und Angriffstaste mit. Außerhalb des Jets bleibt alles wie gewohnt.
Auch die Umschalttaste bleibt frei, weil man damit aus Fahrzeugen aussteigt —
der Nachbrenner liegt deshalb auf `C`.

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
| `massKg` | Startmasse in Kilogramm | `6000` |
| `wingAreaM2` | Tragfläche in m² — größer = kürzerer Start, langsamer | `110` |
| `thrustNewtons` | Schub bei Vollgas | `60000` |
| `afterburnerNewtons` | Schub mit Nachbrenner | `110000` |
| `maxLoadFactor` | Höchste Querbeschleunigung in g | `9` |
| `explosionsBreakBlocks` | Explosionen zerstören Blöcke | `true` |
| `enableRandomRaids` | Nächtliche Drohnenangriffe | `true` |
| `jetMaxHealth` | Panzerung der F-47 | `60` |
| `stealthDetectionFactor` | Wie stark Tarnung wirkt (kleiner = besser) | `0.22` |
| `jetModelScale` | **Wie groß die F-47 gezeichnet wird** | `1.8` |
| `baseForceLoadRadiusChunks` | Chunks um die Basis, die geladen bleiben | `4` |
| `warBaseSeparation` | Abstand der Basen bei `/f47 war` | `180` |

**Tipp:** Wenn dir die Explosionen deine Basis zerlegen, setze
`explosionsBreakBlocks` auf `false`.

**Zum Flugmodell:** Die F-47 fliegt nach echter Aerodynamik. Aus Anstellwinkel,
Staudruck und Luftdichte werden Auftrieb und Widerstand berechnet und zusammen
mit Schub und Gewicht aufsummiert — nichts davon ist gemogelt. Wer schneller
oder träger fliegen will, dreht an `thrustNewtons`, `massKg` und `wingAreaM2`.
Mehr Schub heißt mehr Spitze, aber auch weitere Kurven; mehr Tragfläche heißt
kürzerer Start und langsamerer Flug.

**Zur Größe der Jets:** `1.8` entspricht gut 5,5 Blöcken Länge und Spannweite —
neben einem Spieler wirkt die Maschine damit wie ein echtes Kampfflugzeug. Wer
sie größer oder kleiner will, dreht an `jetModelScale`; `1.0` wäre gut drei
Blöcke. Die Trefferbox bleibt davon unberührt (4 Blöcke breit), damit die
Maschine durch das Hangartor passt.

**Wenn der Rechner ächzt:** `baseForceLoadRadiusChunks` kleiner stellen. Bei `0`
laufen die Basen nur noch, während du in der Nähe bist — dafür kostet es nichts.

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
| Der Jet hebt nicht ab | `W` halten, erst ab ~120 km/h die Nase heben — vorher trägt die Fläche nicht |
| Der Jet stürzt sofort ab | Schub zu niedrig — die Anzeige `! STRÖMUNGSABRISS !` beachten |
| Autonomer Jet bleibt stehen | Er braucht einen Piloten (`Marschbefehl: Pilot` daneben benutzen) |
| Iron Dome schießt nicht | Mit `Abfangrakete`n laden (Rechtsklick) |
| Soldaten greifen nicht an | Schwierigkeitsgrad steht auf „Friedlich" |
| Meine Einheiten kämpfen nicht gegeneinander | Beide gehören derselben Partei — mit dem Truppenabzeichen umstellen |
| Einheit wechselt die Seite nicht | Truppenabzeichen direkt auf die Einheit rechtsklicken, nicht daneben |
| Startbahn zu bauen ist mühsam | `Basis-Bausatz` benutzen oder `/f47 base` eingeben |
| **Nichts greift sich an** | `/f47 status` eingeben — sagt dir, ob eine zweite Partei da ist und ob der Abstand passt |
| **Sie kreisen nur über der Basis** | Kein Gegner in 320 Blöcken Umkreis. Näher zusammen bauen oder `/f47 war` benutzen |
| **Es passiert nur, wenn ich daneben stehe** | `baseForceLoadRadiusChunks` in `config/f47.json` steht auf `0` — auf `4` setzen |
| **Die Jets sind winzig** | Alte Mod-Datei. Die neue ersetzen; sonst `jetModelScale` in `config/f47.json` prüfen |
| Die Staffel bleibt am Boden stehen | Kein Pilot an Bord (Kaserne baut Nachschub) oder Tank unter einem Viertel — ein Techniker muss auftanken |
| Joystick wird nicht gefunden | Vor dem Spielstart anschließen, dann `J` drücken — oben steht die Geräteliste |
| Achse läuft verkehrt herum | Im Zuordnungs-Bildschirm auf `Umkehren` klicken |
| Schubhebel geht nur halb | `Schubhebel vermessen` und den Hebel einmal ganz durchschieben |
| Stick zittert in Ruhelage | `deadzone` in `config/f47-joystick.json` erhöhen (z. B. `0.15`) |

---

## Lizenz

MIT
