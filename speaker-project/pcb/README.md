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

### Main-Board — 4-Lagen, ~91% verdrahtet (Rest fine-pitch, s. u.)
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

**Fertig verdrahtet (~91 %):** Der mitgelieferte eigene Grid-Router
(`autoroute.py`, 0.125-mm-Raster, 0.15-mm-Bahnen, 0.45-mm-Vias) hat das
Board mit **~3080 Bahnsegmenten/Vias** verdrahtet:

* GND als durchgehende In1-Plane, jeder GND-Pad per Via-in-Pad angebunden
* PVDD, 5V, 3V3, AGND und alle Signalnetze als Bahnen geroutet
* SOLID-Zonenanbindung → **0 starved-thermal, 0 isolierte Kupferinseln**
* Netzklasse 0.127 mm (JLCPCB-4-Lagen-5-mil)

**Was offen ist (~38 Verbindungen + 16 enge Stellen):** Es bleiben die
**Fine-Pitch-Ausbrüche** am ADAU1701 (LQFP-48, 0.5 mm Pitch) und an den
TPA3118 (HTSSOP) offen — konkret v. a. `U6_OUT*` (Amp-Ausgänge),
`USB_D±`, `MCLKI/OSCO`, `ADC0/1_IN`, `DAC_L/R/SUB`, `SDA/SCL`, `PLL_LF`,
`DSP_NRST`, `EE_WP`, sowie ~3 GND / 5×3V3-Reste. Diese Push-and-Shove-
Ausbrüche zwischen 0.5-mm-Pins kann ein Raster-Router nicht sauber ziehen;
dazu kommen 16 reale Clearance-Engstellen (<0.127 mm, meist an breiten
VBAT-Bahnen und ESP-Boot-Pads).

**So wird es fertig (1–3 h in KiCad 7):** Das Board öffnet fertig platziert
mit ~91 % gezogener Verdrahtung; nur die genannten ~38 Luftlinien mit dem
**interaktiven Router** (Push-and-Shove, `R`-Modus) nachziehen und die 16
Engstellen per Drag entzerren. Platzierung, GND-Plane, Zonen, Netzklassen
und Regeln stehen bereits. Danach `./export_fab.sh` neu laufen lassen.

> ⚠️ In diesem Zustand ist das Main-Board **noch nicht bestellbar** (DRC
> meldet die offenen Netze + 16 Clearance-Punkte). Das **Button-Board ist
> fertig und kann sofort bestellt werden.** Nach dem Nachziehen der ~38
> Netze ist auch das Main-Board fertigungsreif.

**Reproduzieren des Routing-Laufs:**
```bash
cd pcb && (cd main-board && python3 generate.py)
RGRID=0.125 TIGHT=1 VIA=small NLAYERS=3 CLEAR=0.15 SEED=3 STITCH=10 \
  GNDANCHOR=0 python3 autoroute.py main-board/main-board.kicad_pcb --keepout 64,76,100,80
# danach Flaechen-Anbindung nachziehen:
CONNECT_ONLY=1 RGRID=0.125 TIGHT=1 VIA=small NLAYERS=3 STITCH=7 \
  python3 autoroute.py main-board/main-board.kicad_pcb --keepout 64,76,100,80
```

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
