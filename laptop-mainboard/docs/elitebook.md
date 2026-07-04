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

**Rechte Kante, hinten → vorn (nach Foto des Originalgeräts):**

| Original | Dieses Board |
|---|---|
| Netzkabel-Anschluss (Barrel) | **USB-C-Ladeport 100W-PD** (J_PWR, eigener TPS65987D) — an exakt dieser Position |
| USB 3.1 Gen1 (USB-A) | USB-A 5G (J_USBA2) |
| HDMI | HDMI 2.1 (J_HDMI) |
| 2× USB-C Thunderbolt nebeneinander | 2× USB-C Thunderbolt 4 (J_TB0, J_TB1 am JHL8540) |
| Optionaler SIM-Slot | nicht bestückt (kein WWAN) |

**Linke Kante, hinten → vorn:** Diebstahlsicherung (mechanisch, kein Bauteil),
USB-A 5G mit Ladefunktion (J_USBA1), 3.5-mm-Kombiklinke (J_AUX).
Das Gerät hat **kein RJ45** — Netzwerk über das WLAN-Modul oder TB4-/USB-Adapter.

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

## Erkenntnisse aus dem Foto des Original-Mainboards

Das vom Nutzer gelieferte Foto der Originalplatine (Unterseite, mit
Heatpipe) zeigt die internen Anschluss-Positionen — fuer die naechste
Revision des Floorplans zu uebernehmen:

| Original (Silkscreen) | Position (Draufsicht) | Dieses Board |
|---|---|---|
| DC (Ladebuchse) | rechts hinten bei den Ports | ✅ J_PWR dort |
| SSD (M.2) | links | ⚠️ bei uns mittig-rechts (AM5-Sockel braucht die linke Haelfte) |
| WLAN (M.2 2230) | vorn rechts | ⚠️ bei uns mittig — umziehen sinnvoll |
| eDP + USB (Board-zu-Board) | rechte Kante | ⚠️ eDP bei uns oben Mitte |
| FAN | rechts (grosser Luefter-Ausschnitt oben rechts!) | ✅ J_FAN2 rechts oben |
| BATT | unten Mitte-rechts | ✅ J_BAT unten Mitte |
| FPR/B-L/Tastatur-FPCs | unten Mitte | ✅ |
| SPK | unten rechts | ✅ J_SPKR |
| RTC | unten links | ⚠️ bei uns rechts der DIMMs |
| S/C (Smartcard) | unten rechts aussen | nicht bestueckt |

Wichtigste Abweichung: Der Original-Umriss ist **kein Rechteck** — grosser
Luefter-Ausschnitt oben rechts, abgeschraegte Ecken, gestufte Vorderkante.
Unser AM5-Sockel + 2 SO-DIMMs brauchen jedoch deutlich mehr Flaeche als das
Original-Board (BGA-CPU + geloetetes RAM), daher nutzt unser Board bewusst
das volle Rechteck inkl. des originalen Luefter-/Freibereichs — der Kuehler
muss entsprechend als Custom-Loesung darueber liegen.

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
