# SpeakerBox Pro

Custom 2.1-Bluetooth-Lautsprecher: 2× 3"-Fullrange + 4"-Sub, ADAU1701-DSP,
2× TPA3118, ESP32-S3 mit PWA-Steuerung, 4S2P-18650-Akku, 3D-gedrucktes
Gehäuse mit drei geschlossenen Kammern.

## Projektstruktur

| Ordner | Inhalt | Status |
|---|---|---|
| `acoustics/` | T/S-Simulation, Volumen-Sweep, Crossover-Optimierung + Plots | ✅ läuft, Plots generiert |
| `dsp/` | ADAU1701-Programmgenerator, Parameter-XML, 3 Presets, Doku | ✅ generiert |
| `pcb/main-board/` | Haupt-PCB 100×80 (Amps, DSP, ESP32, Power) | ✅ Schaltplan+Platzierung+Fab-Export, ⚠️ Routing manuell offen |
| `pcb/button-board/` | Bedien-PCB 60×20 (Taster, RGB-LED, OLED) | ✅ dito |
| `firmware/` | ESP32-S3 PlatformIO | ✅ kompiliert (`pio run` SUCCESS) |
| `webapp/` | Next.js-14-PWA (Dashboard, EQ, Settings, Info) | ✅ baut (`npm run build`), läuft vom ESP32 |
| `docs/` | BOM/Kosten, Montage-Anleitung, Debugging-Guide | ✅ |

## Kernergebnisse der Simulation

* Fullrange-Kammern 0.35 L netto → Fc 200 Hz, Qtc 1.30 (klein, aber vom
  DSP-Hochpass entschärft); Sub-Kammer 2.2 L netto + Dämmwolle → Fc 96 Hz,
  Qtc 0.88, f3 ≈ 81 Hz.
* **Crossover: Linkwitz-Riley 12 dB/Okt @ 330 Hz**, Sub invertiert,
  0.9 ms Delay (Optimum aus komplexem Summen-Sweep; der Vorgabebereich
  150–180 Hz ist wegen der 200-Hz-Einbauresonanz der kleinen Kammern
  physikalisch ungünstig).
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

1. **PCB-Routing** in KiCad fertigstellen (Ratsnest komplett, Regeln in
   `pcb/README.md`; geschätzt 4–8 h) und Gerber neu exportieren.
2. Reale T/S-Parameter der gelieferten Treiber messen und die Simulation
   damit erneut laufen lassen (Crossover-Werte prüfen).
3. SigmaStudio-Projekt nach `dsp/README.md` aufbauen und EEPROM flashen.
4. Bestellen (JLCPCB + LCSC + AliExpress-Module), löten, Montage nach
   `docs/montage-anleitung.md`, REW-Abstimmung.
