# PCB — SpeakerBox Pro

Zwei Boards, vollständig aus Python-Design-Daten generiert (`design.py` je
Board = Netzliste, `generate.py` = Schaltplan + Layout via pcbnew-API).

| Board | Grösse | Inhalt |
|---|---|---|
| `main-board/` | 100 × 80 mm | 2× TPA3118D2 (BTL-Stereo + PBTL-Sub), ADAU1701-DSP, ESP32-S3, Power-Management, alle Anschlüsse |
| `button-board/` | 60 × 20 mm | 6 Taster (THT, 13-mm-Stössel), WS2812B, OLED-Header, JST-PH-8 |

## Verifizierte Design-Grundlagen

* **TPA3118D2**: Pinout + Gain-Tabelle + PBTL-Beschaltung aus TI SLOS708G.
  U6 = Master, Stereo-BTL, 20 dB (R 5.6 kΩ an GAIN/SLV). U7 = Slave, PBTL
  (LINP/LINN direkt an GND, Signal auf RINP, OUTPR+OUTNR = Plus-Klemme),
  20 dB Slave (51k/51k). SYNC-Bus verbindet beide (keine Schwebungstöne).
  Ausgangsfilter 10 µH + 680 nF (BD-Mode, 4 Ω).
* **ADAU1701**: 1.8-V-Core über externen PNP (BC807-40) an VDRIVE mit 1 kΩ
  (Datenblatt Fig. 16). PLL-Filter 475 Ω + 56 nF ∥ 3.3 nF an AVDD (Fig. 15).
  12.288-MHz-Quarz = 256×fs → PLL_MODE0=0, PLL_MODE1=1. ADC-Strom-Eingänge:
  18 kΩ Serie = 2 V rms Full-Scale, ADC_RES 18 kΩ. Selfboot mit 24LC32A.
* **Bluetooth**: BTM875-B (CSR8675, aptX HD) über 10-poligen Header,
  **analog** in die ADC-Eingänge gemischt (Aux + BT je 18 kΩ in denselben
  Strom-Eingang = passiver Mischer). Bewusst NICHT über I2S: der ADAU1701
  hat keine ASRC — asynchrones I2S vom BT-Modul erzeugt Klicks/Drift.
* **Fuel Gauge**: MAX17043/BQ27441 sind 1S-Gauges und können kein 4S-Pack
  messen → ersetzt durch Präzisionsteiler 100k/10k (1 %) auf ESP32-ADC.
* **Soft-Power**: 2× AO4407A (Verpolschutz + Schalter), Taster zieht Gate
  über Diode, ESP32 hält über NPN-Latch (PWR_HOLD), Soft-Start ~10 ms.
* **Buck**: LMR33630ADDA (36 V/3 A synchron) statt MP2315 — verifiziertes
  Pinout, mehr Spannungsreserve. FB-Teiler 100k/24.9k → 5.01 V.
* **Star-Ground**: AGND-Insel (DSP-Analogteil + Audio-Eingänge, unten
  links) trifft GND an genau einem Punkt (Net-Tie NT1).

## Status / verbleibende Arbeit (ehrlich)

**Fertig:** Schaltplan (vollständige Konnektivität über Global-Labels),
Bauteilplatzierung (alle 172 + 19 Bauteile, EMV-orientierte Gruppierung),
Board-Umriss, Bohrlöcher, GND-Flächen beide Lagen, PVDD-Polygon,
AGND-Insel, Fertigungsexporte, JLCPCB-BOM mit LCSC-Nummern.

**Offen — muss in KiCad interaktiv gemacht werden (geschätzt 4–8 h):**

1. **Signal-Routing**: ~330 Verbindungen (Ratsnest ist durch die
   Netzzuweisung komplett definiert). Regeln: Signal ≥0.2 mm,
   5V/3V3 ≥0.5 mm, PVDD/Lautsprecher ≥1.5 mm, Akku-Pfad ≥3 mm
   (XT30 → F1 → Q1 → Q2 liegen dafür in einer Reihe).
2. **DRC-Restpunkte**: 3 Courtyard-Mikroüberlappungen, 1 Zonen-Artefakt —
   werden beim Routen mit verschoben. Silk-Warnungen sind kosmetisch
   (Auto-Platzierung dreht Referenztexte nicht).
   `copper_edge_clearance` an USB-C/XT30/ESP-Antenne ist beabsichtigter
   Überhang. `lib_footprint_issues` = Bibliotheksvergleich, irrelevant.
3. **Thermal-Vias-Kontrolle** unter beiden TPA3118 (Footprint bringt sie
   mit, beim Routen B.Cu-Kupfer darunter freihalten/anbinden).

## Dateien

```
pcb/
├── schgen.py / pcbgen.py       gemeinsame Generator-Bibliothek
├── export_fab.sh               Gerber/Drill/Pos/SVG/BOM-Export
├── gen_bom.py                  JLCPCB-BOM-Generator
├── main-board/
│   ├── design.py               Netzliste (alle Verbindungen, LCSC-Nummern)
│   ├── generate.py             Platzierung + Zonen
│   ├── main-board.kicad_sch/.kicad_pcb/.kicad_pro
│   ├── drc-report.txt
│   └── fab/                    gerber.zip, BOM, positions.csv, SVG-Preview
└── button-board/               (analog)
```

Fertigungsparameter JLCPCB: 2 Lagen, FR4 1.6 mm, HASL bleifrei, 1 oz,
grün; SMT-Bestückung Top-Seite (Economic), THT-Stecker von Hand.
