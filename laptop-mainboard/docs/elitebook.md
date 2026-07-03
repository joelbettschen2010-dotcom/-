# Anpassung an das HP EliteBook 850 G7 (15.6")

Das Board ist auf den Einbau in ein EliteBook-850-G7-Chassis ausgelegt.

## Gehäuse-Randdaten

| Grösse | Wert |
|---|---|
| Chassis aussen | 358.5 × 233.9 × 17.9 mm |
| Board-Umriss (dieses Projekt) | 340 × 112 mm, oberer Gehäusebereich |
| Akku | HP-Original 3S / 56 Wh (z. B. HP L35766-005), Schacht unterhalb |
| Display | 15.6" eDP (30/40-polig) — **elektrisch kompatibel** (eDP ist Standard) |

## Portlayout (wie 850 G7)

**Rechte Kante, hinten → vorn:**

| Original 850 G7 | Dieses Board |
|---|---|
| Barrel-Jack (Netzteil) | **USB-C 100W-PD + Thunderbolt 4** (J_TB0) — wie gewünscht statt Barrel |
| USB-C Thunderbolt 3 | USB-C Thunderbolt 4 (J_TB1) |
| HDMI | HDMI 2.1 (J_HDMI) |
| USB-A (Laden) | USB-A 5G (J_USBA2) |
| RJ45 | RJ45 2.5GbE (J_LAN1) |

**Linke Kante:** USB-A 5G (J_USBA1), 3.5-mm-Kombiklinke (J_AUX).
Smartcard-Leser und SIM-Slot des Originals sind nicht bestückt (Blende bleibt zu).

## Was direkt passt und was verifiziert werden muss

| Komponente | Status |
|---|---|
| Display (eDP) | ✅ Standard-Schnittstelle, 40-Pin-I-PEX vorgesehen; HP-Kabelbelegung gegen eDP-Standard prüfen |
| Webcam | ✅ USB 2.0 — Belegung des HP-Displaykabels ausmessen |
| Lüfter | ✅ 5V-PWM-Anschlüsse vorgesehen, Stecker ggf. umpinnen |
| Lautsprecher | ✅ 2× 2W an ALC256 |
| Akku | ⚠️ HP nutzt einen proprietären Stecker + Smart-Battery-SMBus; **Pinout muss am Original-Board ausgemessen werden** (J_BAT ist dafür vorgesehen: 2×BAT+, 2×GND, SCL, SDA, THERM, PRES#) |
| Tastatur | ⚠️ HP-Matrix-FPC — Zuordnung KSI/KSO **muss per Multimeter am Original ausgemessen** und in der EC-Firmware hinterlegt werden |
| Touchpad/Clickpad | ⚠️ I2C-HID üblich — Belegung des FPC verifizieren |
| Fingerprint/NFC | ❌ nicht vorgesehen |
| Umriss + Schraublöcher | ⚠️ **Muss am realen Chassis ausgemessen werden** (siehe unten) |

## Wichtig: Masse und Lochbild

HP veröffentlicht keine mechanischen Zeichnungen des Mainboards. Der
Board-Umriss (340 × 112 mm) und die 8 Befestigungslöcher in diesem Projekt
sind eine **fundierte Annäherung** an den verfügbaren Innenraum — kein
vermessenes Abbild. Vor einer Fertigung:

1. Original-Mainboard ausbauen, auf Flachbettscanner legen (1:1, 600 dpi),
   oder mit Messschieber Umriss + Lochpositionen aufnehmen.
2. Werte in `tools/gen_all.py` (Konstanten `X0/Y0/X1/Y1` + `MTG`-Positionen
   und Port-Y-Koordinaten) eintragen, `python3 tools/gen_all.py` ausführen.
3. Papier-/Karton-Probedruck des Umrisses ins Gehäuse legen.

Die Port-Positionen an der rechten Kante müssen auf ±0.5 mm mit den
Gehäuseausschnitten fluchten — auch diese Y-Koordinaten stammen aus Fotos
des Geräts, nicht aus einer Zeichnung, und sind zu verifizieren.

## Kühlung

Das 850 G7 kühlt eine 15–25-W-CPU mit einem flachen Blower. Der gesockelte
8600G (35–45 W) braucht **deutlich mehr**: Vapor-Chamber über dem Sockel,
2 Heatpipes, beide Lüfterpositionen bestückt. Die Bauhöhe des Sockels
(CPU + ILM ≈ 7.5 mm) plus Kühler sprengt die originalen 17.9 mm —
realistisch ist ein **modifizierter/erhöhter Bodendeckel (+6–8 mm)**.
Das ist die härteste mechanische Einschränkung dieses Umbaus.
