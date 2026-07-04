# PCB-Layout

## Board

- **Abmessung:** 340 × 112 mm, 8 Befestigungslöcher M2.5, ausgelegt auf das
  HP-EliteBook-850-G7-Chassis (358.5 × 233.9 mm aussen); HP-Akkuschacht
  unterhalb. Umriss/Lochbild vor Fertigung am Chassis verifizieren
  (docs/elitebook.md).
- **Dicke:** 1.6 mm (bei Bedarf 1.2 mm mit angepasstem Lagenaufbau).

## Lagenaufbau (8 Lagen)

| Lage | Funktion |
|---|---|
| F.Cu | Bauteile, kurze Anbindungen, HDMI-TMDS |
| In1.Cu | DDR5-Signale + VDDCR-Zonen unter dem Sockel |
| In2.Cu | **GND-Plane 1** (durchgehend) |
| In3.Cu | Power: +VSYS |
| In4.Cu | Power: +5V / +3V3 (gesplittet) |
| In5.Cu | **GND-Plane 2** (durchgehend) |
| In6.Cu | PCIe-Gen4-Paare, TB-Paare |
| B.Cu | GND + restliche Signale, Entkopplung unter dem Sockel |

Jede Highspeed-Lage referenziert direkt auf eine GND-Plane.

## Floorplan

```
+------------------------------------------------------------------+
| Lüfter L   Bucks 5V/3V3/…   eDP  Webcam  BL   [SSD1][SSD2] Lüfter R|
| RJ45#1  PHY1        +--------+  BIOS   [M.2 vertikal]  JHL8540 TB0|
| RJ45#2  PHY2  VRM   |  AM5   |  SO-DIMM A       WLAN   TPS65988 TB1|
| USB-A#1       4+2Ph |LGA-1718|  SO-DIMM B        SD-Ctrl  Mux USBC2|
| USB-A#2  Audio      +--------+  RT1                HDMI     USBC3  |
| Klinke   ALC256  Caps   EC  ECFlash BT1  Lader BQ25731   SD-Slot  |
| Spk L  Lid TP  KB-FPC  [Akku-Stecker]  Bulk-Caps  FETs+L   Spk R  |
+------------------------------------------------------------------+
```

Leitidee: **kurze Highspeed-Wege** — Sockel mittig-links, DIMMs direkt
rechts daneben (Kanal A/B symmetrisch), M.2 und TB-Silizium zwischen Sockel
und rechter Portleiste; Ethernet-PHYs direkt hinter den RJ45-Buchsen links.
VRM links vom Sockel mit kurzem Pfad in die Sockel-Power-Pins,
Eingangs-/Ladeteil unten rechts beim Ladeport.

## Routing-Regeln

| Signalklasse | Impedanz | Regeln |
|---|---|---|
| DDR5 (CA/DQ/DQS/CK) | 40 Ω SE / 80 Ω diff | Byte-Lane-Skew ±0.5 mm, CA-Fly-by, Referenz In2 |
| PCIe Gen4 | 85 Ω diff | Intra-Pair ±0.1 mm, max. 2 Via-Paare, Ground-Stitching |
| TB4/USB4 40G | 85 Ω diff | < 60 mm bis Buchse, keine Stubs, GND-Vias ≤ 1 mm neben jedem Signal-Via |
| USB3 Gen2 | 90 Ω diff | ESD-Dioden im Pfad, < 100 mm |
| HDMI TMDS | 100 Ω diff | Längenabgleich Paar ±0.15 mm |
| MDI (Ethernet) | 100 Ω diff | < 25 mm zum Magjack, Bob-Smith-Cap an MCT |
| VRM → Sockel | — | Kupferflächen ≥ 6 mm², Via-Farmen, Sense-Leitungen differentiell zur Die-Sense |

Im Board sind repräsentative **Routing-Korridore** (In1/In6) als
Differenzpaare eingezeichnet; das Feinrouting folgt diesen Korridoren.

## Kühlung / Mechanik

- Sockelzone: 100 × 75 mm für Kühler (Vapor-Chamber + 2 Heatpipes zu beiden
  Lüftern) freihalten — Kommentar-Layer im Board.
- AM5-ILM: Standard-Backplate; bei 45 W reicht ein 25-mm-Kühlkörperprofil,
  Bauhöhe gesamt ≈ 12 mm über Board → Gehäusedicke ~20 mm einplanen.
- VRM-Stages und Ladewandler über Wärmeleitpads an die Bodenplatte koppeln.
