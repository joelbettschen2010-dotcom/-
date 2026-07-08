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

### Main-Board — 4-Lagen, ~97 % via Freerouting verdrahtet
Das Main-Board ist ein **4-Lagen-Design** (F.Cu / In1.Cu / In2.Cu / B.Cu).

**Warum 4 Lagen:** Die Bauteildichte (ADAU1701 LQFP-48 mit 0.5 mm Pitch,
ESP32-S3-Modul, 2× TPA3118 HTSSOP, alle Stecker auf 100×80 mm) ist auf
2 Lagen **nachweislich nicht** vollständig routbar (getestet: blieb bei
~71 offenen Verbindungen stehen).

**Verdrahtet mit Freerouting.** KiCads `ExportSpecctraDSN`/`ImportSpecctraSES`
funktionieren im headless Build nicht — daher ein eigener DSN-Exporter
(`dsn_export.py`) und SES-Importer (`ses_import.py`), die das Board an die
**Freerouting-Engine** (lokaler Docker-Container, echtes Push-and-Shove)
übergeben und das Ergebnis zurückholen. Kette in `route_freerouting.sh`.

Freerouting hat **522 Verbindungen bis auf die USB-C-Gruppe geroutet**
(~2680 Bahnsegmente/Vias, **0 Kurzschlüsse, 0 isolierte Inseln, 0
starved-thermal**). Zonen (GND/AGND/PVDD) werden nach dem Import gefüllt und
umfliessen die Bahnen; Netzklasse 0.127 mm (JLCPCB-4-Lagen-5-mil).

**Was offen bleibt (10 Pad-Enden):** die 5 USB-C-Netze `VBUS`, `USB_DN`,
`USB_DP`, `CC1`, `CC2` am Steckverbinder J11 (Platinenrand, Fine-Pitch) +
2 einzelne GND-Pads in der DSP-Ecke (je ein Via genügt). Dazu 7 kleine
Clearance-Engstellen (USB-C-Fanout, ein 3V3-Segment auf In1).
Der ESP32-S3 lässt sich meanwhile über den **UART-Debug-Header J12**
programmieren (geroutet) — die USB-Buchse ist für Betrieb/Update, nicht
zwingend fürs erste Flashen.

**So wird es fertig (~20 min in KiCad 7):** Board öffnet fertig verdrahtet;
nur die 5 USB-C-Luftlinien mit dem interaktiven Router (`R`) nachziehen und
die paar Engstellen per Drag entzerren, dann `./export_fab.sh`.

> ⚠️ In diesem Zustand ist das Main-Board **fast fertig, aber noch nicht
> bestellbar** (DRC meldet die 5 USB-C-Netze + wenige Clearance-Punkte). Das
> **Button-Board ist fertig und sofort bestellbar.** Nach dem Nachziehen der USB-C-Netze
> Netze ist auch das Main-Board fertigungsreif.

**Reproduzieren des Routing-Laufs (Freerouting, empfohlen):**
```bash
cd pcb && ./route_freerouting.sh 24      # platzieren -> DSN -> Freerouting -> Board
# Voraussetzung: laufender Docker-Daemon + Image
#   ghcr.io/freerouting/freerouting:latest
```

Der mitgelieferte eigene Grid-Router (`autoroute.py`) verdrahtet ebenfalls
~91 % (siehe Git-Historie), erreicht aber die Fine-Pitch-Ausbrüche nicht so
gut wie Freerouting; er bleibt als eigenständiges Werkzeug erhalten und
routet das Button-Board zu 100 %.

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
