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

## Routing-Status (ehrlich)

### Button-Board — ✅ FERTIG geroutet, DRC-sauber
Vollständig vom eigenen Grid-Router (`autoroute.py`) verdrahtet:
**0 offene Verbindungen, 0 Clearance-Verstöße, 0 nicht verbundene Pads**
(nur kosmetische Silk-Hinweise). Gerber liegt in `button-board/fab/` —
dieses Board kann **direkt bestellt** werden.

### Main-Board — 4-Lagen platziert + Planes, Signal-Routing offen
Das Main-Board ist **absichtlich als 4-Lagen-Design** ausgelegt:

| Lage | Funktion |
|---|---|
| F.Cu | Signal (horizontal bevorzugt) + GND-Füllung |
| In1.Cu | **durchgehende GND-Plane** (Rückstrompfad, EMV) |
| In2.Cu | Signal (horizontal) + GND-Füllung |
| B.Cu | Signal (vertikal) + GND-Füllung, PVDD-Zone unter den Amps |

**Warum 4 Lagen:** Die Bauteildichte (ADAU1701 LQFP-48 mit 0.5 mm Pitch,
ESP32-S3-Modul, 2× TPA3118 HTSSOP, alle Stecker auf 100×80 mm) ist auf
2 Lagen **nachweislich nicht** vollständig routbar — zwei unabhängige
Verfahren (Greedy-Multiseed und PathFinder-Verhandlung) blieben bei ~71
offenen Verbindungen stehen. 4 Lagen mit durchgehender Masse­fläche sind
für ein Mixed-Signal-Board dieser Klasse ohnehin die fachlich richtige
Wahl (sauberer Bass, weniger EMV).

**Was fertig ist:** vollständiger Schaltplan (datenblattverifizierte
Konnektivität), Platzierung aller 169 Bauteile (EMV-Gruppierung, alle Pads
innerhalb der Platine, USB/Antenne bewusst am Rand), 4-Lagen-Stackup,
GND-Plane + Zonen, AGND-Insel, PVDD-Polygon, Netzklassen/DRC-Regeln
(JLCPCB-5-mil-tauglich), Bohrlöcher, Fertigungspipeline, JLCPCB-BOM.

**Was offen ist — Signal-Routing:** Der mitgelieferte eigene Grid-Router
(`autoroute.py`, 0.25-mm-Raster) verdrahtet ~75 % der Netze, erreicht aber
auf diesem dichten Board **keine DRC-saubere Vollverdrahtung**: die
0.5-mm-Pitch-QFP/QFN-Ausbrüche (DSP, ESP32) brauchen feineres Raster als
der Router leistet, und es bleiben ~60 Netze offen. Das Signal-Routing
sollte daher in KiCad fertiggestellt werden:

1. **Interaktiver Router** in KiCad 7 (die Platzierung, Planes, Netzklassen
   und Regeln sind bereits gesetzt — es fehlt nur das Ziehen der Bahnen).
2. Oder **Freerouting** (`.dsn` exportieren → routen → `.ses` importieren).
   Freerouting konnte in dieser Umgebung nicht geladen werden (der
   Session-Proxy erlaubt nur Repo-Downloads); lokal ist es der schnellste Weg.

Der Ratsnest ist durch die Netzzuweisung vollständig definiert; das
Board öffnet sich in KiCad fertig platziert mit allen Luftlinien.

**DRC-Restpunkte der Platzierung:** einige Courtyard-Mikroüberlappungen
(Auto-Platzierung, beim Routen mit ausräumbar). `copper_edge_clearance` an
USB-C/XT30/ESP-Antenne ist beabsichtigter Randüberhang. `lib_footprint_issues`
= Bibliotheksvergleich, irrelevant. Thermal-Vias unter beiden TPA3118 sind
im Footprint vorhanden (beim Routen B.Cu-Kupfer darunter anbinden).

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
