# DSP-Programm ADAU1701 — SpeakerBox Pro

## Übersicht

```
ADC0/1 (Aux-In) ──┐
                  ├─ Input-Mixer ── Master-Volume ─┬─ HP LR2 310 Hz ── PEQ×10 ── Limiter ── DAC0 → FR L
I2S-In (BT)     ──┘                                ├─ HP LR2 310 Hz ── PEQ×10 ── Limiter ── DAC1 → FR R
                                                   └─ Mono-Summe ── LP LR2 310 Hz ── Delay 1.1 ms
                                                        ── Invert ── PEQ×10 ── Sub-Level ── Limiter ── DAC2 → Sub
```

Abtastrate **48 kHz** (Standard bei BT-Modulen mit I2S-Out; vermeidet
Resampling). Param-RAM-Belegung: **182 / 1024 Wörter (18 %)** — reichlich
Reserve für spätere messbasierte Korrektur-Filter.

## Block-für-Block-Begründung

### 1. Input-Mixer (4 Gains)
Aux-In (ADC0/1) und Bluetooth (I2S seriell-Input) werden **addiert**, nicht
umgeschaltet. Begründung: kein Quellenwahl-UI nötig, eine stumme Quelle liefert
digitale Stille und stört nicht. Die Gains erlauben der Firmware trotzdem ein
sauberes Muten einzelner Quellen (z. B. Aux stumm, wenn BT spielt, um
Rauschen des ADC-Frontends zu unterdrücken).

### 2. Master-Volume (Single SW Slew)
**Vor** dem Crossover platziert, damit ein einziger Parameter alle drei Wege
gleich skaliert und das Sub/FR-Verhältnis lautstärkeunabhängig bleibt.
Als *Slew-Volume* ausgeführt (rampt im DSP über ~20 ms), damit Volume-Steps
der Web-App/Tasten **klickfrei** sind. 51 Stufen, logarithmisch −60…0 dB
(Tabelle in `dsp_coefficients.h`), Stufe 0 = Mute.

### 3. Crossover: Linkwitz-Riley 12 dB/Okt @ 310 Hz
* Frequenz aus `acoustics/03_crossover_optimization.py`: Sweep 100–400 Hz
  ergab **310 Hz** als Welligkeits-Minimum (1.43 dB Std-Abw. 60–500 Hz)
  für die Dayton-Treiber (ND91-4 in 0.38 L: Fc 152 Hz Qtc 0.84;
  TCP115-4 in 1.9 L: Fc 84 Hz Qtc 0.54).
  Der geforderte Startbereich 150–180 Hz ist physikalisch ungünstig, weil die
  kleinen Kammern die Fullrange-Einbauresonanz auf ~152 Hz schieben —
  ein Hochpass **oberhalb** dieser Resonanz hält die Phasendrehung aus dem
  Übernahmebereich und schützt die kleinen Treiber vor Hub.
* LR2 = **ein** Biquad mit Q = 0.5 pro Weg (kein Kaskadieren nötig) — spart
  Instruktionen und Param-RAM.
* Der Sub bekommt die **Mono-Summe** (L+R)×0.5 vor dem Tiefpass: Bass unter
  310 Hz ist auf Musikmaterial praktisch mono; die Summierung bringt +6 dB
  kohärenten Gewinn am Sub-Eingang.

### 4. Phasenkorrektur: Delay 1.1 ms + Invertierung
LR2-Wege stehen konstruktionsbedingt 180° zueinander → ein Weg muss
invertiert werden. Die Optimierung (inkl. der *natürlichen* Phasendrehungen
beider Box-Hochpässe, die sich bei Fc 84 Hz vs. 152 Hz unterscheiden)
ergab: **Sub invertiert + 1.10 ms Delay** (53 Samples @ 48 kHz) liefert die
flachste Summe im Übernahmebereich. Das Delay kompensiert den
Phasenvorlauf des Fullrange-Box-Hochpasses. **Nach der REW-Messung am realen
Gerät nachjustieren** — Membranversätze im Gehäuse kommen noch dazu.

### 5. 10-Band-PEQ pro Weg (L, R, Sub getrennt)
* Mittenfrequenzen ISO-nah: 31.5 / 63 / 125 / 250 / 500 / 1k / 2k / 4k / 8k / 16k Hz,
  Q = 1.41 (≈ 1 Oktave) — Standard für grafische EQs, gutmütige Überlappung.
* **Pro Weg getrennt**, damit der Tontechniker bei der Endabstimmung L/R-
  asymmetrische Gehäuse-/Aufstellungseffekte korrigieren kann und der Sub
  eine eigene Raumkorrektur bekommt.
* Startzustand aller Bänder: neutral (b0=1). Presets überschreiben nur die
  PEQ-Koeffizienten + Sub-Level → Preset-Wechsel ist ein reiner Param-Write.
* Die Firmware rechnet Biquads nach RBJ-Cookbook und schreibt sie per
  **Safeload** (klickfrei, synchron zum Sample-Takt).

### 6. Sub-Level (±12 dB)
Separater Gain nach dem Sub-PEQ für den "Sub-Level"-Slider der Web-App.
Getrennt vom PEQ, damit Preset und Nutzer-Trim unabhängig bleiben.

### 7. Limiter (Peak-Limiter pro Ausgang)
Auslegung der Schwellen (Kette: DAC 0.9 V RMS Vollpegel → TPA3118 Gain 20 dB
→ 9.0 V RMS bei 0 dBFS; Versorgung nominal 14.8 V):

| Weg | Grenze | Rechnung | Schwelle |
|---|---|---|---|
| FR L/R | Amp-Clipping vor Treiberlimit | ND91-4 verträgt 30 W > BTL-Clipping ~19 W an 4.3 Ω → knapp unter 0 dBFS | **−0.5 dBFS** |
| Sub | Amp-Clipping vor Treiberlimit | PBTL @14.8 V/4 Ω clippt ~23 W < 40 W Treiberlimit → knapp unter 0 dBFS | **−0.5 dBFS** |

Attack 1 ms (schnell genug ab 310 Hz), Release 200 ms (unauffällig bei Musik).
Beide Limiter schützen primär vor **Amp-Clipping** (klingt hässlich und
erzeugt HF-Anteile), nicht vor thermischer Überlastung — die Dayton-Treiber
(30 W / 40 W) können mehr ab, als der Amp liefert. Bei vollem Akku (16.8 V) steigt
die Clipping-Grenze; die Schwelle ist konservativ auf Nominalspannung
gerechnet, damit der Klang über den ganzen Akkuhub konsistent bleibt.

### 8. Presets

| Band [Hz] | 31.5 | 63 | 125 | 250 | 500 | 1k | 2k | 4k | 8k | 16k | Sub-Level |
|---|---|---|---|---|---|---|---|---|---|---|---|
| **Flat** | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 dB |
| **Musik** | +2 | +3 | +2 | 0 | −1 | 0 | 0 | +1 | +2 | +2 | +1.5 dB |
| **Outdoor** | +3 | +5 | +4 | +1 | 0 | +1 | +2 | +3 | +3 | +2 | +3 dB |

* **Flat** ist die Mess-Referenz für REW — wirklich alles neutral.
* **Musik** ist bewusst dezent (leichte "Smiley"-Kurve), damit die spätere
  messbasierte Korrektur nicht gegen ein aggressives Preset arbeitet.
* **Outdoor** = Loudness: draussen fehlen Wand-Reflexionen (−6 dB Bass
  gegenüber Innenraum) und der Hörabstand ist grösser → kräftiger Bass- und
  Präsenz-/Höhen-Lift. Die Limiter fangen den zusätzlichen Pegel ab.

## Dateien

| Datei | Inhalt |
|---|---|
| `generate_dsp_program.py` | Generator: Biquads (RBJ), 5.23-Konvertierung, Limiter, Volume-Tabelle |
| `speakerbox_pro_params.xml` | Vollständiger Parametersatz (SigmaStudio-Exportformat, 182 Parameter) |
| `presets/preset_*.xml` | Nur-PEQ+SubLevel-Parametersätze der 3 Presets |
| `../firmware/include/dsp_coefficients.h` | Identische Daten als C-Header für die ESP32-Firmware |

## Ehrliche Einschränkung: Program-RAM

Die **DSP-Instruktionen** (Program-RAM) kann nur SigmaStudio kompilieren —
das Format ist proprietär und nicht offen dokumentiert. Einmaliger manueller
Schritt (~30 min):

1. SigmaStudio öffnen, neues ADAU1701-Projekt @ 48 kHz.
2. Blöcke exakt in der Reihenfolge dieses Dokuments einfügen (Reihenfolge
   bestimmt die Param-RAM-Adressen!): Input-Mixer → Slew-Volume →
   2× HP-Filter (General 2nd Order, Q=0.5) → LP-Filter → Delay → Gain(−1) →
   3× 10-Band "Parametric EQ" → Gain (SubLevel) → 3× "Peak Limiter".
3. Kompilieren, "Export System Files" → Adressen mit
   `speakerbox_pro_params.xml` abgleichen (bei gleicher Blockreihenfolge
   stimmen sie überein; sonst die `#define DSP_ADDR_*` im Header anpassen).
4. Via ICP5/USBi ins EEPROM (24LC32A) brennen → Gerät bootet ab dann autonom,
   die Firmware ändert Parameter zur Laufzeit über I2C-Safeload.

Alle *Parameter*-Werte (Filter, Limiter, Presets, Volume) sind dagegen
vollständig hier generiert und in Firmware + XML identisch hinterlegt.
