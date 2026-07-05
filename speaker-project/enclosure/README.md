# Gehäuse — SpeakerBox Pro (3D-Druck)

Parametrisches OpenSCAD-Modell (`speakerbox.scad`), zwei Druckteile:
`shell.stl` (Hauptschale, Front nach unten drucken) und `rear_lid.stl`
(Rückdeckel, flach drucken). Beide ohne Stützen druckbar.

## Kammern (passend zur Simulation in `acoustics/`)

| Kammer | brutto | netto (mit Treiber) | mit Dämmwolle eff. |
|---|---|---|---|
| Sub (Mitte) | 2.06 L | ~1.95 L | ~2.2 L ✅ |
| Mid L/R (oben seitlich) | 0.40 L | ~0.35 L ✅ | ~0.40 L |
| Rückfach Elektronik+Akku | 1.82 L | — | — |

Aussenmasse: **300 × 150 × 170 mm** (passt auf 300er-Druckbett, z. B.
Prusa MK4/Ender 3 V3 SE knapp diagonal, sonst K1 Max/X1C mit 300+).

## Eingebaute Features

* Treiberausschnitte mit M3-Schraubdomen (Einschmelzmuttern von innen)
* Main-PCB-Dome (100×80, Lochbild aus `pcb/main-board/generate.py`)
* Tasterreihe (6× ø7.5, 9-mm-Raster), LED-Lichtleiter ø5, OLED-Fenster
  auf der Oberseite über dem Rückfach — passend zum Button-Board
* Rückdeckel: 6× M3 gesenkt, Lüftungsschlitze über der Amp-Zone,
  Anschluss-Ausschnitt (Position nach realer Kabellage nacharbeiten)
* Sub-Kammer allseitig geschlossen; die Seitenspalten hinter den
  Mid-Kammern gehören zum Elektronik-Luftraum

## ⚠️ Vor dem Druck anpassen (Messschieber!)

Die Treibermasse sind **Typwerte**: `fr_cutout=73`, `fr_screw_bc=68`,
`sub_cutout=94`, `sub_screw_bc=96`. An die realen Chassis anpassen und
neu rendern:

```bash
openscad -o shell.stl    -D 'part="shell"'    speakerbox.scad
openscad -o rear_lid.stl -D 'part="rear_lid"' speakerbox.scad
```

## Druckeinstellungen (PETG)

* 4 Perimeter, 5 Boden-/Deckschichten, 25–40 % Gyroid-Infill
* 0.2 mm Schichthöhe, Düse 245 °C, Bett 80 °C, Lüfter ≤ 40 %
* Materialbedarf: Schale ~920 cm³ Solid-Volumen → real ca. **600–750 g**,
  Deckel ~170 cm³ → ca. **150 g** (je nach Infill)
* Nach dem Druck: Kammern innen versiegeln (Epoxid/Acryl) — Drucke sind
  selten luftdicht! Dichtheitstest siehe `docs/montage-anleitung.md`.
* 22× M3- und 2× M2.5-Einschmelzmuttern einsetzen (Lötkolben ~220 °C).
