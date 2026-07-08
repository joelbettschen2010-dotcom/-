# SpeakerBox Pro

Custom 2.1-Bluetooth-Lautsprecher: 2× Dayton ND91-4 (3.5"-Fullrange) +
Dayton TCP115-4 (4"-Sub), ADAU1701-DSP, 2× TPA3118, ESP32-S3 mit
PWA-Steuerung, 4S2P-18650-Akku, 3D-gedrucktes Gehäuse mit drei
geschlossenen Kammern.

## Projektstruktur

| Ordner | Inhalt | Status |
|---|---|---|
| `acoustics/` | T/S-Simulation, Volumen-Sweep, Crossover-Optimierung + Plots | ✅ läuft, Plots generiert |
| `dsp/` | ADAU1701-Programmgenerator, Parameter-XML, 3 Presets, Doku | ✅ generiert |
| `pcb/main-board/` | Haupt-PCB 100×80, **4-Lagen** (Amps, DSP, ESP32, Power) | ✅ 4-Lagen, ~97% via **Freerouting** verdrahtet (~2715 Bahnen/Vias, 0 Shorts); nur 5 USB-C-Netze + 2 GND-Pads offen (s. `pcb/README.md`) |
| `pcb/button-board/` | Bedien-PCB 60×20 (Taster, RGB-LED, OLED) | ✅ **vollständig geroutet, DRC-sauber, bestellbar** |
| `firmware/` | ESP32-S3 PlatformIO | ✅ kompiliert (`pio run` SUCCESS) |
| `webapp/` | Next.js-14-PWA (Dashboard, EQ, Settings, Info) | ✅ baut (`npm run build`), läuft vom ESP32 |
| `docs/` | BOM/Kosten, Montage-Anleitung, Debugging-Guide | ✅ |

## Kernergebnisse der Simulation

* Treiber: **Dayton ND91-4** (Fs 74 Hz, Qts 0.41, Xmax 4.6 mm) und
  **Dayton TCP115-4** (Fs 53.8 Hz, Qts 0.35, Xmax 4+ mm) — Datenblattwerte,
  vor der finalen Abstimmung am realen Chassis nachmessen.
* Fullrange-Kammern 0.38 L netto (+Dämmwolle) → Fc 152 Hz, Qtc 0.84,
  f3 ≈ 132 Hz; Sub-Kammer 1.9 L netto (+Dämmwolle) → Fc 84 Hz, Qtc 0.54,
  f3 ≈ 115 Hz (mit DSP-Bass-Shelf tiefer spielbar, grosser Hub-Headroom).
* **Crossover: Linkwitz-Riley 12 dB/Okt @ 310 Hz**, Sub invertiert,
  1.10 ms Delay (Optimum aus komplexem Summen-Sweep, Rest-Welligkeit
  1.4 dB; der Vorgabebereich 150–180 Hz ist wegen der Einbauresonanz der
  kleinen Kammern physikalisch ungünstig).
* Max-SPL-Abschätzung System ~103–106 dB @ 1 m.

## Wichtige Design-Abweichungen (begründet)

* **BT-Audio analog statt I2S** in den DSP: der ADAU1701 hat keine ASRC,
  asynchrones I2S vom BT-Modul erzeugt Klicks. Analog über die
  ADC-Strom-Eingänge ist die etablierte Lösung (Details `pcb/README.md`).
* **MAX17043/BQ27441 entfallen**: das sind 1S-Fuel-Gauges, ungeeignet für
  4S. Ersatz: Präzisionsteiler + ESP32-ADC + OCV-Kennlinie.
* **MP2315 → LMR33630**: verifiziertes Pinout, 36-V-Reserve.
* **Buttons als Widerstandsleiter** (5 Tasten auf 1 ADC-Pin), Power-Taster
  separat (muss bei ausgeschaltetem ESP32 funktionieren).

## Reproduzieren

```bash
cd acoustics && python3 01_volume_sweep.py && python3 02_frequency_response.py && python3 03_crossover_optimization.py
cd dsp && python3 generate_dsp_program.py
cd pcb/main-board && python3 generate.py && cd ../button-board && python3 generate.py
cd pcb && ./export_fab.sh
cd firmware && pio run
cd webapp && npm install && npm run build && cp -r out/* ../firmware/data/www/
```

## Was noch von Hand zu tun ist

1. **Main-Board-Signal-Routing** in KiCad fertigstellen (4-Lagen-Board ist
   platziert, GND-Plane + Zonen + Netzklassen gesetzt, Ratsnest komplett —
   es fehlt nur das Ziehen der Signalbahnen; interaktiv oder via Freerouting,
   s. `pcb/README.md`), dann Gerber neu exportieren. Das **Button-Board ist
   fertig geroutet und kann sofort bestellt werden.**
2. Reale T/S-Parameter der gelieferten Dayton-Treiber messen und die
   Simulation damit erneut laufen lassen (Crossover-Werte prüfen).
3. SigmaStudio-Projekt nach `dsp/README.md` aufbauen und EEPROM flashen.
4. Bestellen (JLCPCB + LCSC + Module), löten, Montage nach
   `docs/montage-anleitung.md`, REW-Abstimmung.
