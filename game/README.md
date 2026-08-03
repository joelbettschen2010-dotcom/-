# 🏁 Horizon Rush

Ein Arcade-Rennspiel fürs iPhone, das **komplett im Browser** läuft – kein App
Store, kein PC, keine Installation nötig. Über *Teilen → Zum Home-Bildschirm*
wird daraus eine Vollbild-App mit eigenem Icon, die auch **offline**
funktioniert.

**Spielen:** <https://joelbettschen2010-dotcom.github.io/-/game/>

## Warum Web und nicht APK oder App Store

| Weg | Geht ohne PC? |
|-----|---------------|
| APK | ❌ Android-Format, läuft auf dem iPhone gar nicht |
| Native iOS-App | ❌ Braucht Mac + Xcode + Entwicklerkonto |
| **Web-App (PWA)** | ✅ Läuft sofort, Home-Bildschirm-Icon, Vollbild, offline |

Leistungsmässig reicht das locker: gerendert wird mit Canvas 2D, gemessen
60 fps bei 230 Segmenten Sichtweite – und das schon im reinen
Software-Renderer ohne Grafikbeschleunigung.

## Der Algorithmus

Es gibt keine gebauten Strecken. Alles entsteht zur Laufzeit aus einer
**Saat** (einem Text):

1. **Streckengenerator** (`js/track.js`) – ein gewichteter Zufallsautomat setzt
   Bausteine aneinander: Geraden, weiche/mittlere/scharfe Kurven, S-Kurven,
   Kuppen, Senken, Wellen. Die Gewichte verschieben sich mit der
   Schwierigkeit, scharfe Kurven werden dort häufiger. Anschliessend werden
   Landschaftszonen, Bewuchs, Warnschilder und Verkehr verteilt.
2. **Deterministisch** – gleiche Saat ⇒ exakt dieselbe Strecke. Deshalb fährt
   sich ein Karriere-Event immer gleich, die Tagesroute ist für jeden Tag
   dieselbe, und trotzdem muss keine einzige Strecke gespeichert werden.
3. **Adaptive Gegner-KI** (`js/ui.js`, `HR.updateSkill`) – nach jedem Rennen
   wird die Fahrstärke Elo-artig nachgeführt:
   `skill += (tatsächliche Platzierung − erwartete) × 8.5`.
   Daraus ergibt sich das Grundtempo der Gegner. Zusätzlich gibt es ein
   gedeckeltes Gummiband (±8,5 %), Kurvenbremsung nach Können und
   gelegentliche Fahrfehler.
4. **Endlose Etappen** – im Marathon wird beim Rundenschluss nahtlos eine neue
   Strecke mit neuer Zone und höherer Schwierigkeit erzeugt. Anfang und Ende
   jeder Etappe sind flach und gerade, deshalb sieht man den Übergang nicht.

## Spielzeit: 5 Minuten oder 2 Stunden

| Modus | Dauer |
|-------|-------|
| ⚡ Schnellrennen | 1–2 Minuten, neue Zufallsstrecke, 5 Gegner |
| 📅 Tagesroute | ein Zeitfahren pro Tag |
| 🏁 Karriere | 30 Events in 5 Bezirken, je 2–4 Minuten |
| ♾️ Marathon | endlos – Checkpoints geben Zeit dazu, Schwierigkeit steigt |

## Fahren

- **Linkes Feld** – analog lenken: je weiter der Finger vom Mittelpunkt weg
  ist, desto stärker der Einschlag.
- **Bremse** rechts, Gas kommt automatisch (abschaltbar).
- **NOS** lädt sich durch knappe Überholmanöver, Drifts und mit der Zeit.
- Alternativ **Neigungssteuerung** in den Einstellungen.

Punkte gibt es für knappe Überholmanöver (Kombo), Driftzeit und Tempo –
sie fliessen in Guthaben und Erfahrung ein.

## Fortschritt

10 Fahrzeuge in 6 Klassen (D bis X), vier Ausbaustufen (Motor, Getriebe,
Reifen, Lachgas) mit je 5 Stufen, freie Lackfarbe, Stufenaufstieg mit
Erfahrungspunkten und Sterne pro Event. Alles liegt in `localStorage` –
**keine Konten, keine Server, keine Werbung, keine Datenübertragung.**

## Landschaftszonen

Küste · Wüste · Alpen · Wald · Nachtstadt · Canyon – jede mit eigenem Himmel,
Bergsilhouette, Bewuchs, Fahrbahnfarben, Nebelton und Wetterneigung
(Regen bzw. Schneefall).

## Technik

| Datei | Zweck |
|-------|-------|
| `index.html` | Gerüst, HUD, Bedienelemente |
| `style.css` | Oberfläche |
| `js/core.js` | Mathe, Zufall mit Saat, Speicherstand, Fahrzeuge, Motorsound |
| `js/art.js` | zeichnet alle Sprites zur Laufzeit (keine Bilddateien) |
| `js/track.js` | Streckengenerator und Landschaftszonen |
| `js/race.js` | Pseudo-3D-Renderer, Fahrphysik, Gegner-KI, HUD |
| `js/ui.js` | Menüs, Karriere, Garage, Ergebnisse |
| `sw.js` | Service Worker für den Offline-Betrieb |

Gerendert wird nach dem klassischen Verfahren projizierter Strassensegmente:
Jedes Segment hat zwei Punkte in Weltkoordinaten, die pro Bild in
Kamerakoordinaten überführt und perspektivisch auf den Bildschirm projiziert
werden. Kurven entstehen dadurch, dass beim Zeichnen ein seitlicher Versatz
aufaddiert wird – der Fahrer spürt sie als Fliehkraft.

Der Motorklang ist ein kleiner Synthesizer aus zwei Oszillatoren und
gefiltertem Rauschen (Web Audio), also ebenfalls ohne Audiodateien.

**Grösse gesamt: rund 200 KB.**
