# Stromversorgung

## Eingangs- und Ladepfad

```
USB-C TB0 (20V/5A, 100W PD)
   └─ TPS65988 (PD-Aushandlung, interner Pfad-FET, 5A)
        └─ +VBUS_IN ── R2 (10 mΩ, ACP/ACN) ── BQ25731 Buck-Boost (Q1–Q4 + L1 3.3µH)
             ├─ +VSYS (NVDC-Systemschiene, 12…16.8V)
             └─ Q5 (BATFET) ── R1 (10 mΩ, SRP/SRN) ── +VBAT (Akku 4S)
```

- **Akku:** 4S1P Li-Ion, 15.4 V nominal, 80 Wh, Smart-Battery (SMBus) am J_BAT.
- **NVDC:** System läuft immer aus +VSYS; der Lader teilt die 100 W dynamisch
  zwischen System und Akku auf (Input-Current-Limit über ILIM_HIZ + SMBus).
- Port TB1 ist Source-only (5 V/3 A über PP_HV2 aus +5V).

## Schienenübersicht

| Schiene | Quelle | Spannung | Last (max.) | Verbraucher |
|---|---|---|---|---|
| +VSYS | BQ25731 | 12–16.8 V | 100 W | VRM, Bucks, Backlight |
| +VDDCR_CPU | MP2857 + 4× MP86957 | 0.6–1.4 V (SVI3) | 60 A | CPU-Kerne |
| +VDDCR_SOC | MP2857 + 2× MP86957 | 0.8–1.2 V (SVI3) | 30 A | SoC/iGPU |
| +5V | MPQ8633B (U4) | 5 V | 8 A | DIMM-Bulk, USB-VBUS, Lüfter, HDA |
| +3V3 | MPQ8633B (U5) | 3.3 V | 8 A | LAN, Audio, SD, TB-I/O, Panel |
| +3V3_ALW | MPQ8633B (U9) | 3.3 V | 2 A | EC, PD-Controller (S5-Domäne) |
| +1V8 | TPS62823 (U6) | 1.8 V | 3 A | BIOS-Flash, VDD_MISC, VDD_1V8 |
| +1V1_MEM | TPS62823 (U18) | 1.1 V | 4 A | VDDIO_MEM (SoC-Seite) |
| +1V05_TB | TPS62823 (U7) | 1.05 V | 3 A | JHL8540 I/O |
| +0V88_TB | TPS62823 (U8) | 0.88 V | 3 A | JHL8540 Core |
| +VLCD | SY6280 (U37) | 3.3 V | 2 A | Panel-Logik |
| +VBL | MP3389 (U38) | ~19 V | 1.2 A | Backlight-LEDs |
| VDD_RTC | ML1220 | 3 V | µA | RTC-Domäne |

DDR5-Besonderheit: VDD/VDDQ (1.1 V) der Module erzeugt der **PMIC auf dem
DIMM** aus +5V — das Board liefert nur 5 V Bulk + 3.3 V Management.

## Power-Budget (worst case)

| Verbraucher | Leistung |
|---|---|
| CPU-Package (cTDP 45 W, kurzzeitig 65 W) | 45–65 W |
| 2× DDR5 SO-DIMM | 8 W |
| 2× NVMe SSD | 16 W (Spitze) |
| JHL8540 + 2 TB-Ports (ohne Buspower) | 4 W |
| 2× RTL8125BG | 3 W |
| Display + Backlight | 8 W |
| USB-Buspower (2×A, 2×C) | 21 W (Spitze) |
| Rest (EC, Audio, SD, WLAN, Lüfter) | 5 W |
| **Summe Spitze** | **~110–130 W** → Akku puffert über 100 W hinaus (NVDC) |
| **Typisch (Office)** | **15–25 W** |

## Power-Sequencing (EC-gesteuert)

1. Akku oder PD vorhanden → +VSYS steht → U9 liefert **+3V3_ALW** → EC bootet.
2. Power-Taste → EC: **EN_5V, EN_3V3** → PG_5V/PG_3V3 abwarten.
3. **EN_1V8**, **EN_VLCD**, TB_PWR_EN (1V05/0V88).
4. **EN_VRM** → MP2857 regelt VDDCR_CPU/SOC auf Boot-Spannung → VRM_PWRGD.
5. EC setzt **SYS_PWROK** und gibt **PM_PWR_BTN#** an den SoC.
6. SoC meldet Schlafzustände über **SLP_S3#/SLP_S5#** zurück; EC schaltet
   Schienen entsprechend (S3: nur +5V für DIMM-Selbstauffrischung + ALW).

Schutz: PROCHOT# (Lader ↔ SoC ↔ VRM), THERMTRIP# → EC schaltet hart ab,
3 NTCs (CPU, VRM, Lader) + Akku-Thermistor am EC-ADC.
