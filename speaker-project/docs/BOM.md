# Partsliste & Kostenübersicht — SpeakerBox Pro

Die maschinenlesbaren BOMs für JLCPCB liegen in
`pcb/main-board/fab/main-board-BOM-JLCPCB.csv` und
`pcb/button-board/fab/button-board-BOM-JLCPCB.csv`
(Format: Comment / Designator / Footprint / LCSC Part #).

Preise: LCSC-Richtwerte Stand Mitte 2026, umgerechnet in CHF, bei
Bestellmenge für **1 Gerät** (JLCPCB rechnet Mindestabnahmen pro Bauteil,
"Attrition" eingerechnet).

## Halbleiter & Module (Main-Board)

| Bauteil | Wert/Typ | LCSC | Stk. | ca. CHF |
|---|---|---|---|---|
| U6, U7 | TPA3118D2DAP Class-D | C88243 | 2 | 5.20 |
| U3 | ADAU1701JSTZ SigmaDSP | C51118 | 1 | 7.50 |
| U8 | ESP32-S3-WROOM-1-N8R2 | C2913202 | 1 | 4.80 |
| U1 | LMR33630ADDA Buck 5V/3A | C841384 | 1 | 1.60 |
| U2 | AMS1117-3.3 | C6186 | 1 | 0.15 |
| U4 | 24LC32A EEPROM | C7593 | 1 | 0.25 |
| U9 | USBLC6-2SC6 ESD | C7519 | 1 | 0.20 |
| Q1, Q2 | AO4407A P-FET | C77843 | 2 | 0.60 |
| Q3 | MMBT3904 | C20526 | 1 | 0.02 |
| Q4 | BC807-40 (1.8V-Core) | C130197 | 1 | 0.03 |
| D1 | SMBJ18A TVS | C114213 | 1 | 0.10 |
| D8 | SS34 Schottky | C8678 | 1 | 0.06 |
| D2-D7 | Zener/Signal/LEDs | div. | 6 | 0.30 |
| Y1 | Quarz 12.288 MHz 3225 | C255909 | 1 | 0.35 |

## Passiv/Elektromechanik (Main-Board)

| Gruppe | Inhalt | ca. CHF |
|---|---|---|
| Leistungsinduktivitäten | 6× 10 µH/5-8 A (Ausgangsfilter), 1× Buck | 4.50 |
| Elkos | 3× 1000 µF/25 V + 47 µF | 1.60 |
| MLCC/Widerstände 0603/0805/1206 | ~90 Positionen | 2.50 |
| Stecker | XT30, 2× JST-XH5, JST-PH8, 3× Schraubklemme, USB-C, Klinke, Header | 3.80 |
| Sicherung 10 A 1812 | C369167 | 0.30 |

## Button-Board

| Gruppe | Inhalt | ca. CHF |
|---|---|---|
| 6× Taster THT 6×6/13 mm, WS2812B, JST-PH8, Kleinteile | | 1.80 |

## Nicht-LCSC-Teile (AliExpress)

| Teil | Suchbegriff | ca. CHF |
|---|---|---|
| BT-Modul | "BTM875-B CSR8675 aptX HD bluetooth 5.0 module board" | 9.00 |
| OLED | "0.96 OLED display module I2C SSD1306 white" | 2.50 |
| ICP5/USBi-Programmer | "USBi SigmaStudio programmer" oder CH341-basiert "ADAU1701 programmer" | 8.00 (einmalig) |

## PCB-Fertigung (JLCPCB)

| Posten | ca. CHF |
|---|---|
| Main-PCB 100×80, 2-Lagen, 5 Stk. | 4.00 |
| Button-PCB 60×20, 5 Stk. | 2.00 |
| SMT-Bestückung Main (Setup + ~100 Joints, Economic) | 12.00 |
| Versand (Standard) | 6.00 |

## Gesamt

| | CHF |
|---|---|
| Halbleiter/Module LCSC | ~21 |
| Passiv/Stecker | ~13 |
| Button-Board | ~2 |
| PCB + Bestückung + Versand | ~24 |
| **Total bestückte Boards (ohne AliExpress-Module)** | **~48** ✅ Budgetziel |
| + BT-Modul + OLED (AliExpress) | +11.50 |
| + USBi-Programmer (einmalige Anschaffung) | +8 |

Hinweise:
* THT-Teile (Stecker, Schraubklemmen, Taster, Elkos) selbst löten spart
  ~8 CHF Bestückungskosten — eingeplant (JLCPCB Economic bestückt nur Top-SMD).
* Bereits vorhanden laut Projektbeschrieb: Treiber, 18650-Zellen, Lade-Board,
  Gehäuse — nicht eingerechnet.
* Mindestbestellmengen: JLCPCB liefert min. 5 PCBs; LCSC-Passivteile haben
  Mindestabnahmen (10-100 Stk.) — im Preis oben enthalten, Rest ist Vorrat.
