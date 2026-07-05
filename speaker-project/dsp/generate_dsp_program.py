"""
DSP-Programm-Generator für den ADAU1701 (SpeakerBox Pro).

Erzeugt:
  1. speakerbox_pro_params.xml   — Parameterdatei im SigmaStudio-Exportformat
                                   (<IC><Module><Parameter Name/Address/Value>)
  2. presets/preset_flat.xml     — 3 Preset-Parametersätze (nur PEQ-Bänder)
     presets/preset_music.xml
     presets/preset_outdoor.xml
  3. ../firmware/include/dsp_coefficients.h — identische Koeffizienten als
     C-Header, damit die ESP32-Firmware die Parameter direkt via I2C-Safeload
     in den Param-RAM schreiben kann (autonomer Betrieb ohne PC).

WICHTIG (ehrlich dokumentiert): Der PROGRAM-RAM-Inhalt (DSP-Instruktionen)
kann nur SigmaStudio selbst kompilieren. Die Schaltung ist in
README.md Block für Block spezifiziert (Reihenfolge, Verdrahtung, Zellnamen),
so dass sie in SigmaStudio in ~30 min nachgebaut und einmalig ins EEPROM
geflasht wird. Die Parameter-ADRESSEN unten folgen der Allokations-Reihenfolge
von SigmaStudio (Param-RAM wird in Schaltplan-Reihenfolge vergeben) und
MÜSSEN nach dem Kompilieren einmal gegen "Capture Window"-Export abgeglichen
werden. Danach kann die Firmware alle Parameter live ändern.

Signalfluss (siehe README.md für Begründungen):

  ADC0/1 (Aux-In)  ─┐
                    ├─ Input-Mixer ── MasterVol ─┬─ HP-LR2 330Hz ── PEQ10 L ── Limiter ── DAC0 (FR L)
  I2S-In (BT-Modul)─┘                            ├─ HP-LR2 330Hz ── PEQ10 R ── Limiter ── DAC1 (FR R)
                                                 └─ Mono-Sum ── LP-LR2 330Hz ── Delay 0.9ms ── Invert
                                                        ── PEQ10 Sub ── SubLevel ── Limiter ── DAC2 (Sub)

Abtastrate: 48 kHz. Filterformat: 5.23 Fixed-Point (Bereich -16.0 … +15.9999).
Vorzeichenkonvention SigmaStudio: gespeichert werden b0, b1, b2, -a1, -a2
(a-Koeffizienten NEGIERT, a0 auf 1 normiert).
"""

import math
import os
import xml.etree.ElementTree as ET
from xml.dom import minidom

FS = 48000.0          # DSP-Abtastrate [Hz]
FX = 330.0            # Crossover-Frequenz aus 03_crossover_optimization.py [Hz]
SUB_DELAY_MS = 0.90   # Sub-Delay aus Optimierung [ms]
SUB_INVERT = True     # Sub-Polarität aus Optimierung

# --- Limiter-Auslegung -------------------------------------------------------
# Verstärkerkette: ADAU1701-DAC Vollaussteuerung = 0.9 V RMS.
# TPA3118 Gain fest auf 20 dB (GAIN0/1 = 0/0) -> 9.0 V RMS am Ausgang bei 0 dBFS.
# Versorgung 14.8 V nominal: BTL-Clipping bei ca. 14.8/sqrt(2)=10.5 V RMS (ideal),
# real ~9.8 V RMS (Ron-Verluste) -> 0 dBFS liegt knapp UNTER dem Amp-Clipping. Gut.
#
# Fullrange 15 W an 4 Ohm  -> U = sqrt(15*4)  = 7.75 V RMS -> 7.75/9.0 = -1.3 dBFS
# Subwoofer 30 W an 4 Ohm  -> U = sqrt(30*4) = 10.95 V RMS -> über Amp-Clipping!
#   PBTL an 14.8 V/4R clippt bei ~23-25 W. Limiter daher auf Amp-Grenze:
#   9.0 V RMS erreichbar bei 0 dBFS -> Schwelle -0.5 dBFS (knapp unter Clipping),
#   entspricht ~20 W Dauer am Sub — thermisch für den 30-W-Treiber unkritisch.
#   (Bei vollem Akku 16.8 V wären ~27 W drin; die Schwelle ist konservativ auf
#   die NOMINALE Versorgung gerechnet, damit es nie clippt.)
LIM_THRESHOLD_FR_DB = -1.3
LIM_THRESHOLD_SUB_DB = -0.5
LIM_ATTACK_MS = 1.0    # schnell genug für Transienten bei 330 Hz aufwärts
LIM_RELEASE_MS = 200.0

OUT = os.path.dirname(os.path.abspath(__file__))


# =============================================================================
# 5.23 Fixed-Point Konvertierung (ADAU1701 Parameter-RAM-Format)
# =============================================================================
def to_523(x: float) -> int:
    """Float -> 28-bit 5.23 Fixed-Point (als 32-bit-Wort, Zweierkomplement)."""
    if not -16.0 <= x < 16.0:
        raise ValueError(f"{x} ausserhalb 5.23-Bereich")
    v = int(round(x * (1 << 23)))
    return v & 0xFFFFFFFF if v >= 0 else (v + (1 << 28)) & 0xFFFFFFFF


def hex523(x: float) -> str:
    return f"0x{to_523(x):08X}"


# =============================================================================
# Biquad-Koeffizienten (RBJ Audio EQ Cookbook), Ausgabe in SigmaStudio-Ordnung
# =============================================================================
def _norm(b0, b1, b2, a0, a1, a2):
    """Auf a0=1 normieren, a1/a2 negieren (SigmaStudio-Konvention)."""
    return [b0 / a0, b1 / a0, b2 / a0, -a1 / a0, -a2 / a0]


def biquad_lowpass(f0, q, fs=FS):
    w0 = 2 * math.pi * f0 / fs
    alpha = math.sin(w0) / (2 * q)
    cw = math.cos(w0)
    b1 = 1 - cw
    return _norm(b1 / 2, b1, b1 / 2, 1 + alpha, -2 * cw, 1 - alpha)


def biquad_highpass(f0, q, fs=FS):
    w0 = 2 * math.pi * f0 / fs
    alpha = math.sin(w0) / (2 * q)
    cw = math.cos(w0)
    return _norm((1 + cw) / 2, -(1 + cw), (1 + cw) / 2,
                 1 + alpha, -2 * cw, 1 - alpha)


def biquad_peaking(f0, q, gain_db, fs=FS):
    a = 10 ** (gain_db / 40)
    w0 = 2 * math.pi * f0 / fs
    alpha = math.sin(w0) / (2 * q)
    cw = math.cos(w0)
    return _norm(1 + alpha * a, -2 * cw, 1 - alpha * a,
                 1 + alpha / a, -2 * cw, 1 - alpha / a)


def biquad_lowshelf(f0, s, gain_db, fs=FS):
    a = 10 ** (gain_db / 40)
    w0 = 2 * math.pi * f0 / fs
    cw = math.cos(w0)
    alpha = math.sin(w0) / 2 * math.sqrt((a + 1 / a) * (1 / s - 1) + 2)
    two_sqa = 2 * math.sqrt(a) * alpha
    return _norm(a * ((a + 1) - (a - 1) * cw + two_sqa),
                 2 * a * ((a - 1) - (a + 1) * cw),
                 a * ((a + 1) - (a - 1) * cw - two_sqa),
                 (a + 1) + (a - 1) * cw + two_sqa,
                 -2 * ((a - 1) + (a + 1) * cw),
                 (a + 1) + (a - 1) * cw - two_sqa)


def biquad_highshelf(f0, s, gain_db, fs=FS):
    a = 10 ** (gain_db / 40)
    w0 = 2 * math.pi * f0 / fs
    cw = math.cos(w0)
    alpha = math.sin(w0) / 2 * math.sqrt((a + 1 / a) * (1 / s - 1) + 2)
    two_sqa = 2 * math.sqrt(a) * alpha
    return _norm(a * ((a + 1) + (a - 1) * cw + two_sqa),
                 -2 * a * ((a - 1) + (a + 1) * cw),
                 a * ((a + 1) + (a - 1) * cw - two_sqa),
                 (a + 1) - (a - 1) * cw + two_sqa,
                 2 * ((a - 1) - (a + 1) * cw),
                 (a + 1) - (a - 1) * cw - two_sqa)


def biquad_flat():
    """Neutraler Biquad (Durchgang)."""
    return [1.0, 0.0, 0.0, 0.0, 0.0]


# =============================================================================
# EQ-Band-Definitionen: 10 Bänder, ISO-nah verteilt 31.5 Hz … 16 kHz
# =============================================================================
EQ_FREQS = [31.5, 63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000]
EQ_Q = 1.41  # ~1 Oktave Bandbreite, üblicher Wert für grafische 10-Band-EQs

# --- Presets: Gain je Band [dB] in Reihenfolge EQ_FREQS ---------------------
# "Flat":    alles 0 dB — Referenz für REW-Messungen.
# "Musik":   dezente Consumer-Kurve: etwas Bass, minimale Präsenzsenke,
#            sanfter Höhen-Lift. Bewusst zurückhaltend, damit die spätere
#            messbasierte Korrektur nicht dagegen arbeitet.
# "Outdoor": Loudness-Kurve: draussen fehlen Raum-Reflexionen und der
#            Abstand ist grösser -> kräftig Bass, Präsenz +, Höhen +.
PRESETS = {
    "flat":    {"gains": [0, 0, 0, 0, 0, 0, 0, 0, 0, 0],          "sub_level_db": 0.0},
    "music":   {"gains": [2, 3, 2, 0, -1, 0, 0, 1, 2, 2],         "sub_level_db": 1.5},
    "outdoor": {"gains": [3, 5, 4, 1, 0, 1, 2, 3, 3, 2],          "sub_level_db": 3.0},
}


# =============================================================================
# Limiter-Parameter (SigmaStudio "Peak Limiter"-Zelle)
# =============================================================================
def limiter_params(threshold_db, attack_ms, release_ms):
    """Threshold linear + Attack/Release als Filterkoeffizienten (RC-Glättung)."""
    thr = 10 ** (threshold_db / 20)
    atk = 1 - math.exp(-1 / (attack_ms * 1e-3 * FS))
    rel = 1 - math.exp(-1 / (release_ms * 1e-3 * FS))
    return {"threshold": thr, "attack": atk, "release": rel}


# =============================================================================
# Master-Volume: 50 Stufen, logarithmisch (-60 dB … 0 dB), Stufe 0 = mute
# =============================================================================
def volume_table():
    tab = [0.0]  # Stufe 0: stumm
    for i in range(1, 51):
        db = -60.0 * (1 - i / 50.0)           # -58.8 … 0 dB, logarithmisch
        tab.append(10 ** (db / 20))
    return tab


# =============================================================================
# Parameter-Layout im Param-RAM (Allokations-Reihenfolge = Schaltplanreihenfolge)
# =============================================================================
def build_param_map():
    """Liste (Name, Adresse, Wert float) in SigmaStudio-Allokationsreihenfolge."""
    params = []
    addr = 0

    def add(name, values):
        nonlocal addr
        vals = values if isinstance(values, list) else [values]
        for i, v in enumerate(vals):
            params.append((f"{name}_{i}" if len(vals) > 1 else name, addr, v))
            addr += 1

    # 1) Input-Mixer: Aux + I2S(BT) je Kanal, Einheitsgewichte
    add("MixerInL_AuxGain", 1.0)
    add("MixerInL_BtGain", 1.0)
    add("MixerInR_AuxGain", 1.0)
    add("MixerInR_BtGain", 1.0)

    # 2) Master-Volume (Single-Slew-Gain, Firmware schreibt Zielwert)
    add("MasterVolume", 1.0)

    # 3) Crossover: LR2 = 1 Biquad mit Q=0.5 (Butterworth^2 in einem Biquad)
    add("XoverHP_L", biquad_highpass(FX, 0.5))
    add("XoverHP_R", biquad_highpass(FX, 0.5))
    add("XoverLP_Sub", biquad_lowpass(FX, 0.5))

    # 4) Sub-Zeit-/Phasenkorrektur: Delay in Samples + Polarität als Gain
    add("SubDelay_Samples", round(SUB_DELAY_MS * 1e-3 * FS))   # 43 Samples
    add("SubPolarity", -1.0 if SUB_INVERT else 1.0)

    # 5) 10-Band-PEQ je Kanal (flat als Startzustand)
    for ch in ("L", "R", "Sub"):
        for band, f0 in enumerate(EQ_FREQS):
            add(f"PEQ_{ch}_B{band}", biquad_flat())

    # 6) Sub-Level (separater Gain nach PEQ, ±12 dB via Web-App)
    add("SubLevel", 1.0)

    # 7) Limiter je Ausgang
    for ch, thr in (("L", LIM_THRESHOLD_FR_DB), ("R", LIM_THRESHOLD_FR_DB),
                    ("Sub", LIM_THRESHOLD_SUB_DB)):
        lp = limiter_params(thr, LIM_ATTACK_MS, LIM_RELEASE_MS)
        add(f"Limiter_{ch}_Threshold", lp["threshold"])
        add(f"Limiter_{ch}_Attack", lp["attack"])
        add(f"Limiter_{ch}_Release", lp["release"])

    return params


# =============================================================================
# XML-Ausgabe (SigmaStudio-Parameter-Exportformat)
# =============================================================================
def write_xml(params, fname, extra_comment=""):
    root = ET.Element("SigmaStudioParameters")
    ic = ET.SubElement(root, "IC", Name="ADAU1701", Address="0x68",
                       SampleRate=str(int(FS)))
    for name, addr, val in params:
        if isinstance(val, int) and "Delay" in name:
            # Delay wird als Integer (Samples) gespeichert, nicht 5.23
            ET.SubElement(ic, "Parameter", Name=name, Address=str(addr),
                          Value=str(val), Format="int28")
        else:
            ET.SubElement(ic, "Parameter", Name=name, Address=str(addr),
                          Value=hex523(float(val)), Float=f"{float(val):.9f}",
                          Format="5.23")
    xml = minidom.parseString(ET.tostring(root)).toprettyxml(indent="  ")
    header = ("<!--\n  SpeakerBox Pro ADAU1701 Parameterdatei\n"
              "  Generiert von generate_dsp_program.py — NICHT von Hand editieren.\n"
              f"  Crossover LR2 @ {FX:.0f} Hz | Sub-Delay {SUB_DELAY_MS} ms | "
              f"Sub {'invertiert' if SUB_INVERT else 'normal'}\n"
              f"  Limiter: FR {LIM_THRESHOLD_FR_DB} dBFS, Sub {LIM_THRESHOLD_SUB_DB} dBFS"
              f"{extra_comment}\n-->\n")
    body = xml.split("\n", 1)[1]  # minidoms eigene <?xml?>-Zeile ersetzen
    with open(os.path.join(OUT, fname), "w") as fh:
        fh.write('<?xml version="1.0" encoding="utf-8"?>\n' + header + body)
    print(f"geschrieben: {fname} ({len(params)} Parameter)")


def preset_params(name):
    """Nur die PEQ- und SubLevel-Parameter eines Presets (für Preset-Wechsel)."""
    cfg = PRESETS[name]
    base = build_param_map()
    lookup = {p[0].rsplit("_", 1)[0] if p[0][-2] == "_" else p[0]: None for p in base}
    params = []
    # Adressen aus der Basiskarte übernehmen
    addr_of = {p[0]: p[1] for p in base}
    for ch in ("L", "R", "Sub"):
        for band, f0 in enumerate(EQ_FREQS):
            gain = cfg["gains"][band]
            coefs = biquad_peaking(f0, EQ_Q, gain) if gain else biquad_flat()
            for i, c in enumerate(coefs):
                params.append((f"PEQ_{ch}_B{band}_{i}",
                               addr_of[f"PEQ_{ch}_B{band}_{i}"], c))
    params.append(("SubLevel", addr_of["SubLevel"],
                   10 ** (cfg["sub_level_db"] / 20)))
    return params


# =============================================================================
# C-Header für die Firmware
# =============================================================================
def write_c_header(params):
    path = os.path.join(OUT, "..", "firmware", "include", "dsp_coefficients.h")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as fh:
        fh.write("// Automatisch generiert von dsp/generate_dsp_program.py — nicht editieren!\n")
        fh.write("// Parameter-RAM-Adressen und 5.23-Startwerte fuer den ADAU1701.\n")
        fh.write("#pragma once\n#include <stdint.h>\n\n")
        fh.write(f"#define DSP_FS            {int(FS)}\n")
        fh.write(f"#define DSP_XOVER_HZ      {FX:.0f}f\n")
        fh.write(f"#define DSP_EQ_NUM_BANDS  10\n")
        fh.write(f"#define DSP_EQ_Q          {EQ_Q}f\n\n")
        fh.write("// Parameter-RAM-Adressen (muessen nach SigmaStudio-Compile\n"
                 "// einmalig gegen den Capture-Export abgeglichen werden!)\n")
        seen_bases = []
        for name, addr, _ in params:
            base = name
            fh.write(f"#define DSP_ADDR_{base.upper():<28s} {addr}\n")
        fh.write("\n// EQ-Mittenfrequenzen [Hz]\n")
        fh.write("static const float DSP_EQ_FREQS[10] = {"
                 + ", ".join(f"{f:g}f" for f in EQ_FREQS) + "};\n\n")
        fh.write("// Preset-Gains [dB] je Band + Sub-Level\n")
        for pname, cfg in PRESETS.items():
            fh.write(f"static const float DSP_PRESET_{pname.upper()}[11] = {{"
                     + ", ".join(f"{g:g}f" for g in cfg["gains"])
                     + f", {cfg['sub_level_db']:g}f}};\n")
        fh.write("\n// Master-Volume-Tabelle: 51 Stufen (0=mute), logarithmisch -60..0 dB\n")
        fh.write("static const float DSP_VOLUME_TABLE[51] = {\n    ")
        fh.write(", ".join(f"{v:.6f}f" for v in volume_table()))
        fh.write("\n};\n")
    print(f"geschrieben: firmware/include/dsp_coefficients.h")


if __name__ == "__main__":
    os.makedirs(os.path.join(OUT, "presets"), exist_ok=True)
    base = build_param_map()
    write_xml(base, "speakerbox_pro_params.xml")
    for pname in PRESETS:
        write_xml(preset_params(pname), f"presets/preset_{pname}.xml",
                  extra_comment=f"\n  Preset: {pname}")
    write_c_header(base)
    print(f"\nParam-RAM-Belegung: {base[-1][1] + 1} Wörter von 1024 "
          f"({(base[-1][1] + 1) / 1024 * 100:.0f} %)")
