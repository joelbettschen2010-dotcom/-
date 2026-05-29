# 🚲 Velo Navi Schweiz

Eine Velo-/E-Bike-Navigations-App für die **ganze Schweiz**. Sie schlägt
mehrere Routen vor, berechnet die Fahrzeit **physikalisch** (inkl. Steigung
und Velo-Typ) und **lernt dein persönliches Tempo** aus deinem Feedback nach
jeder Fahrt.

Läuft als **einzelne Web-App ohne Server** – einfach `index.html` im Browser
öffnen (oder in der Claude-App als Vorschau).

## Funktionen

- **Ganze Schweiz** – Ortssuche über swisstopo/geo.admin.ch oder Klick auf die Karte.
- **Mehrere Routenvorschläge** – Alternativrouten + optional andere Fahrstile als Varianten.
- **Velo-Typ wählbar** – **Velo**, **E-Bike 25** (Abregelung 25 km/h),
  **E-Bike 45** (S-Pedelec, 45 km/h). Der Motor wird im Zeitmodell berücksichtigt.
- **Routen-Stil** – **Trekking/Stadt**, **Rennvelo**, **Gravel**, **MTB Trail**
  (eigene BRouter-Profile mit passenden Untergründen).
- **Höhenberechnung** – die Steigung jedes Streckenabschnitts fliesst in die Zeit ein
  (bergauf dauert länger, bergab schneller), mit Höhenprofil-Diagramm.
- **Routenbeschreibung** – Anteil Strasse / Veloweg / Schotter / Wald / Trail,
  Höhenmeter ↑↓ und maximale Steigung pro Route.
- **Tempo-Feedback & Kalibrierung** – nach jeder Fahrt angeben „war schneller/langsamer".
  Die App passt den Tempo-Faktor **pro Velo-Typ** an und speichert ihn lokal.
- **GPX-Export** je Route.

## Bedienung

1. `index.html` im Browser öffnen.
2. Start & Ziel eingeben (Suche) oder auf die Karte klicken.
3. Velo-Typ, Routen-Stil und Tretleistung wählen.
4. **Routen berechnen** drücken.
5. Eine Route anklicken → Karte + Höhenprofil. Mit **🏁 Gefahren** Feedback geben.

## Wie die Zeit berechnet wird

Pro Streckenabschnitt wird die Gleichgewichts­geschwindigkeit aus der
Leistungs­gleichung gelöst:

```
P = v · ( Crr·m·g·cosθ  +  m·g·sinθ  +  ½·ρ·CdA·v² ) / η
```

- `Crr` Rollwiderstand (je nach Untergrund: Asphalt … Trail)
- `m` Gesamtmasse (Fahrer + Velo, je nach Velo-Typ)
- `θ` Steigungswinkel aus den Höhendaten
- `CdA` Luftwiderstandsfläche, `ρ` Luftdichte, `η` Antriebs­wirkungsgrad
- `P` = Tretleistung **+ Motorleistung** (E-Bike, nur unterhalb der Abregelgeschwindigkeit)

Das Ergebnis wird mit deinem persönlichen **Kalibrierungs­faktor** multipliziert.

## Datenquellen

- **Karten:** [swisstopo](https://www.swisstopo.admin.ch) WMTS (Landeskarte / Satellit)
- **Routing & Höhen:** [BRouter](https://brouter.de) (OSM-basiert, kostenlos)
- **Ortssuche:** geo.admin.ch SearchServer
- **Karten-Engine:** [Leaflet](https://leafletjs.com)

Alle Dienste sind kostenlos und benötigen keinen API-Schlüssel. Es ist eine
Internet­verbindung nötig (Kacheln, Routing, Suche).

## Dateien

| Datei | Zweck |
|-------|-------|
| `index.html` | Aufbau / UI |
| `style.css` | Gestaltung (dunkles Theme, responsiv) |
| `app.js` | Karte, Routing, Zeitmodell, Höhenprofil, Feedback |

## Hinweise / Limiten

- Der öffentliche BRouter-Server hat eine Fair-Use-Begrenzung.
- Die berechnete Zeit ist eine Schätzung ohne Pausen; mit Feedback wird sie genauer.
- E-Bike-Reichweite/Akku wird (noch) nicht modelliert.
