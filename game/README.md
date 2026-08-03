# 🏁 Horizon Rush

Ein Arcade-Rennspiel fürs iPhone, das **komplett im Browser** läuft – kein App
Store, kein PC, keine Installation nötig. Über *Teilen → Zum Home-Bildschirm*
wird daraus eine Vollbild-App mit eigenem Icon, die auch **offline**
funktioniert.

**Spielen:** <https://joelbettschen2010-dotcom.github.io/-/game/>

**Herunterladen:** [`Horizon-Rush.html`](Horizon-Rush.html) – das ganze Spiel in
einer einzigen Datei (182 KB, Icon und alles eingebettet). Speichern, antippen,
läuft – ohne Server, ohne Internet. Erzeugt aus den Quelldateien mit
`node build-single.js`.

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
   `skill += (tatsächliche Platzierung − erwartete) × 12`, plus einem
   Dominanz-Zuschlag von bis zu 9 Punkten, wenn der Vorsprung auf den Zweiten
   gross war. Damit zieht ein Kantersieg die Gegner sofort spürbar nach.
   Aus `skill` ergibt sich ihr Grundtempo als Anteil am Höchsttempo des
   Spielerwagens: `0.78 + skill × 0.26 + Eventschwierigkeit × 0.12`, also
   je nach Können **88 – 110 %**. Dazu ein gedeckeltes Gummiband,
   Kurvenbremsung nach Können und gelegentliche Fahrfehler.
   Wem das Nachführen zu langsam geht, stellt unter *Einstellungen → Gegner*
   auf **Hart** (+14) oder **Brutal** (+28).
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

### Fahrphysik

Das Tempo ist kein Regler, den man beliebig auf- und zudrehen kann:

- **Bremsen** wirkt mit einer festen Verzögerung statt anteilig zum Tempo –
  aus 196 km/h dauert die Vollbremsung **3,3 Sekunden**, ein schnellerer Wagen
  braucht entsprechend länger. Besserer Grip verkürzt den Weg.
- **Beschleunigung** fällt mit steigendem Tempo ab; der Einsteigerwagen
  braucht **4,8 s auf 100 km/h**, ein ausgebauter Hypersportwagen gut 1,5 s.
- **Kurvengrenze**: Wer über dem Grip in die Kurve geht, schiebt nach aussen
  und die Reifen radieren Tempo weg. Man muss also **vor** der Kurve bremsen,
  statt einfach durchzuhalten.
- **Ausrollen** aus Rollwiderstand plus quadratischem Luftwiderstand.
- **Trefferflächen** entsprechen genau der gezeichneten Wagenbreite
  (0.285 Fahrbahneinheiten) – knapp vorbeiziehen geht wirklich vorbei und
  gibt Kombopunkte plus NOS.

### Die Streckenvorschau

Die Karte oben rechts zeigt nicht die ganze Runde, sondern die **nächsten
180 Segmente** – bei Vollgas etwa die Strecke einer Vollbremsung. Sie ist
immer nach oben ausgerichtet und nach Kurvenschärfe eingefärbt:
weiss = schnell, gelb = anbremsen, rot = scharf. Gegner in Reichweite
erscheinen als farbige Punkte.

## Fortschritt und Spielstand

10 Fahrzeuge in 6 Klassen (D bis X), vier Ausbaustufen (Motor, Getriebe,
Reifen, Lachgas) mit je 5 Stufen, freie Lackfarbe, Stufenaufstieg mit
Erfahrungspunkten und Sterne pro Event.

Gespeichert wird in `localStorage` – **keine Konten, keine Server, keine
Werbung, keine Datenübertragung.** Gesichert wird nach jedem Rennen, jedem
Kauf und zusätzlich beim Wegschalten oder Schliessen der App
(`visibilitychange`, `pagehide`, `blur`), weil iOS Safari-Tabs ohne Vorwarnung
beendet.

Damit trotzdem nichts verloren geht:

- **Einstellungen → Spielstand sichern** erzeugt einen Text-Code (`HR1.…`,
  rund 700 Zeichen), der den kompletten Stand enthält. Kopieren, als Datei
  sichern oder sich selbst mailen.
- **Wiederherstellen** setzt ihn auf jedem Gerät und in jeder Version wieder
  ein. Ungültige Codes werden mit einer klaren Meldung abgewiesen.
- Kann eine Umgebung gar nicht speichern (privater Modus, eingebettete
  Ansicht, lokal geöffnete Datei), erkennt das Spiel das und **warnt im Menü**,
  statt den Fortschritt still zu verlieren.

Am zuverlässigsten speichert die über *Zum Home-Bildschirm* installierte
Version – die hat einen eigenen, dauerhaften Speicherbereich.

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
| `build-single.js` | baut daraus die Einzeldatei `Horizon-Rush.html` |

Gerendert wird nach dem klassischen Verfahren projizierter Strassensegmente:
Jedes Segment hat zwei Punkte in Weltkoordinaten, die pro Bild in
Kamerakoordinaten überführt und perspektivisch auf den Bildschirm projiziert
werden. Kurven entstehen dadurch, dass beim Zeichnen ein seitlicher Versatz
aufaddiert wird – der Fahrer spürt sie als Fliehkraft.

Der Motorklang ist ein kleiner Synthesizer aus zwei Oszillatoren und
gefiltertem Rauschen (Web Audio), also ebenfalls ohne Audiodateien.

**Grösse gesamt: rund 200 KB.**
