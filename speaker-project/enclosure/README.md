# Gehäuse — SpeakerBox Pro v2 "Boombox" (3D-Druck)

Parametrisches OpenSCAD-Modell (`speakerbox.scad`), **drei** Druckteile,
alle ohne Stützen druckbar:

| Teil | Druckorientierung | Inhalt |
|---|---|---|
| `shell.stl` | Front nach unten | Körper mit Kammern, Trapez-Tragegriff, Bedienmulde |
| `rear_lid.stl` | flach | Rückdeckel mit Lüftung + Anschlussnische |
| `grille.stl` | flach | Front-Grill, Hex-Perforation, Press-Fit-Zapfen |

## Creality-K1-Version (Bett 220×220): 3-geteilte Schale

Für Drucker unter 300 mm liegt die Schale als Dreiteiler bei — getrennt
**in den Kammerteilerwänden**, d. h. die Sub-Kammer bleibt ungeschnitten
und luftdicht im Mittelteil:

| Teil | Druckfläche | Inhalt |
|---|---|---|
| `k1_center.stl` | 128×188 | Sub-Kammer, Griff, Bedienfeld, PCB-Dome |
| `k1_cap_left/right.stl` | 100×150 | je eine Mid-Kammer mit Dichtflansch |
| `k1_lid_*.stl`, `k1_grille_*.stl` | ≤128 breit | Deckel/Grill 3-geteilt |

Montage: je Fuge 6× M3 (Einschmelzmuttern im Mittelteil, Zugang mit dem
Schraubendreher durch die Treiberöffnung), Flanschring mit dünner Raupe
Acryl-Dichtmasse bestreichen — die Fuge bildet die Seitenwand der
Mid-Kammer und muss luftdicht sein. Kabeldurchlass Mid-Treiber sitzt im
Elektronikfach (24×14 mm, nach Verkabelung mit Heisskleber schliessen).

## Design (nach Vorbild Anker Soundcore Motion Boom / JBL Xtreme)

* Boombox-Silhouette: Vertikalkanten R14, 45°-Fasen vorn/hinten
  (45° = stützenfrei druckbar, im Gegensatz zu Radien an der Bettkante)
* **Integrierter Trapez-Bügelgriff** oben (konstantes Profil längs der
  Tiefe extrudiert → druckt sich in Front-Lage komplett sauber);
  Griffloch ~82×22 mm, gerundet
* **Versenkte Bedienmulde** oben: 6 Taster, RGB-Lichtleiter, OLED-Fenster
  (OLED-Modul liegt links neben dem Button-Board, 4-adrig an J2)
* **Vollflächiger Hex-Grill** vorn, 6 Press-Fit-Zapfen (Ø8.0 in Ø8.4)
* Silikonfuss-Kanäle unten (2× 220×10×1.5 für Klebe-Gummistreifen)
* Interne Verstrebungsrippen (Sub-Kammer 2× vertikal + 1 Steg,
  Rückfach 2× horizontal) gegen Wandresonanzen

## Umgesetzte Regeln (Recherche diyAudio / Parts-Express / Prusa-Forum)

1. Wand ≥ 3–4 mm und hohe Perimeterzahl/Infill gegen "Buzz" → 4 mm + 4 Perimeter + ≥40 % Infill
2. Verstrebungen zwischen gegenüberliegenden Wänden → Rippen s. o.
3. Druckteile sind selten luftdicht → Kammern innen versiegeln (Epoxid/Acryl), Dichtheitstest in `docs/montage-anleitung.md`
4. Schraubdome mit Einschmelzmuttern statt selbstschneidender Schrauben; nicht überdrehen
5. Treiber mit Dichtband hinterlegen, Kanten gerundet (Beugungskanten)

## Kammern (v2.1, passend zur Dayton-Simulation in `acoustics/`)

| Kammer | brutto | netto (mit Treiber) | mit Dämmwolle eff. |
|---|---|---|---|
| Sub TCP115-4 (Mitte, 122×142×122) | 2.11 L | ~1.9 L ✅ | ~2.2 L |
| Mid ND91-4 L/R (oben seitlich, 96×96×56) | 0.52 L | ~0.38 L ✅ | ~0.44 L |
| Rückfach Elektronik+Akku | 2.0 L | — | — |

Aussenmasse: **328 × 150 × 177 mm** (Monolith-Schale braucht ein
330er-Bett, z. B. K1 Max/X1E; für den normalen K1 den Dreiteiler drucken).

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

Die Treibermasse sind **Datenblattwerte für Dayton ND91-4 / TCP115-4**:
`fr_cutout=76`, `fr_screw_bc=83`, `sub_cutout=96`, `sub_screw_bc=106`.
Die Quellen widersprechen sich teils (ND91: Flansch 93 vs. 103.5 mm,
Tiefe 46 vs. 63 mm) — **am gelieferten Chassis nachmessen**, Werte oben
in `speakerbox.scad` anpassen und neu rendern. Falls die ND91-Tiefe
über 52 mm liegt: `mid_d = 70`, `mid_h = 78` setzen (Volumen bleibt gleich):

```bash
openscad -o shell.stl    -D 'part="shell"'    speakerbox.scad
openscad -o rear_lid.stl -D 'part="rear_lid"' speakerbox.scad
```

## Druckeinstellungen (PETG)

* 4 Perimeter, 5 Boden-/Deckschichten, 40 % Gyroid-Infill (Regel: hohes Infill gegen Wandresonanz)
* 0.2 mm Schichthöhe, Düse 245 °C, Bett 80 °C, Lüfter ≤ 40 %
* Materialbedarf: Schale ~920 cm³ Solid-Volumen → real ca. **600–750 g**,
  Deckel ~170 cm³ → ca. **150 g** (je nach Infill)
* Nach dem Druck: Kammern innen versiegeln (Epoxid/Acryl) — Drucke sind
  selten luftdicht! Dichtheitstest siehe `docs/montage-anleitung.md`.
* 22× M3- und 2× M2.5-Einschmelzmuttern einsetzen (Lötkolben ~220 °C).
