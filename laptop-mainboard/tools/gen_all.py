#!/usr/bin/env python3
"""Erzeugt das komplette KiCad-Projekt des Laptop-Mainboards.

Aufruf:  python3 tools/gen_all.py   (aus laptop-mainboard/)
Ausgabe: kicad/  (Projekt, Schaltplaene, Board, Footprint-Lib) + docs/bom.csv
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from kicadgen.sexpr import uid, check_balanced, reset_uids
from kicadgen import sch
from kicadgen.sch import sheet_file, root_file, place, note, symbol_lib
from kicadgen import boarddata as d1
from kicadgen import boarddata2 as d2
from kicadgen import pcb

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
KICAD = os.path.join(BASE, "kicad")
DOCS = os.path.join(BASE, "docs")


def write(path, text, name=None):
    check_balanced(text, name or os.path.basename(path))
    with open(path, "w") as fh:
        fh.write(text)
    print(f"  {os.path.relpath(path, BASE)}  ({len(text)//1024} KB)")


def main():
    reset_uids()
    os.makedirs(KICAD, exist_ok=True)
    os.makedirs(os.path.join(KICAD, "laptop_mainboard.pretty"), exist_ok=True)
    os.makedirs(DOCS, exist_ok=True)

    sheets_src = [
        d1.sheet_power(), d1.sheet_vrm(), d1.sheet_cpu(), d1.sheet_memory(),
        d1.sheet_storage(), d2.sheet_usb_tb(), d2.sheet_ethernet(),
        d2.sheet_display(), d2.sheet_audio_sd_ec(), d2.sheet_kleinteile(),
    ]

    root_uuid = uid("root")
    all_comps = []
    sheet_meta = []
    for page, (title, fname, comps_xy, notes) in enumerate(sheets_src, start=2):
        s_uuid = uid("sheet" + fname)
        file_uuid = uid("file" + fname)
        placed, libs = [], []
        for comp, x, y in comps_xy:
            libs.append(comp)
            placed.append(place(comp, x, y, root_uuid, s_uuid))
            all_comps.append(comp)
        note_blocks = [note(t, 20, 260 + i * 5) for i, t in enumerate(notes)]
        text = sheet_file(file_uuid, title, placed, libs, note_blocks)
        write(os.path.join(KICAD, fname), text)
        sheet_meta.append((title, fname, s_uuid, page))

    # Root-Blatt
    sheets_layout = []
    for i, (title, fname, s_uuid, page) in enumerate(sheet_meta):
        x = 30 + (i % 3) * 130
        y = 40 + (i // 3) * 60
        sheets_layout.append((title, fname, s_uuid, x, y, str(page)))
    root_notes = [
        note("Laptop-Mainboard fuer HP-EliteBook-850-G7-Chassis - Ryzen 5 8600G (AM5, gesockelt)", 30, 240, 3),
        note("2x DDR5 SO-DIMM | 2x M.2 NVMe Gen4 | WLAN | AM5-Pinbelegung: docs/am5_pinmap.csv", 30, 250, 2),
        note("Ports wie 850 G7: 2x TB4 (einer = 100W-PD statt Barrel-Jack), HDMI, 2x USB-A, RJ45, Klinke", 30, 257, 2),
        note("Generiert aus tools/gen_all.py - Aenderungen dort vornehmen.", 30, 267, 1.6),
    ]
    write(os.path.join(KICAD, "laptop-mainboard.kicad_sch"),
          root_file(root_uuid, sheets_layout, "Laptop-Mainboard - Uebersicht",
                    root_notes))

    # Symbolbibliothek
    write(os.path.join(KICAD, "lm.kicad_sym"), symbol_lib(all_comps))

    # Projektdatei + Lib-Tabellen
    pro = {
        "board": {"3dviewports": [], "design_settings": {}, "layer_presets": [],
                   "viewports": []},
        "libraries": {"pinned_footprint_libs": ["laptop_mainboard"],
                       "pinned_symbol_libs": ["lm"]},
        "meta": {"filename": "laptop-mainboard.kicad_pro", "version": 1},
        "sheets": [[root_uuid, "Root"]],
        "text_variables": {},
    }
    with open(os.path.join(KICAD, "laptop-mainboard.kicad_pro"), "w") as fh:
        json.dump(pro, fh, indent=2)
    print("  kicad/laptop-mainboard.kicad_pro")
    with open(os.path.join(KICAD, "sym-lib-table"), "w") as fh:
        fh.write('(sym_lib_table\n  (version 7)\n'
                 '  (lib (name "lm")(type "KiCad")(uri "${KIPRJMOD}/lm.kicad_sym")'
                 '(options "")(descr "Projekt-Symbole"))\n)\n')
    with open(os.path.join(KICAD, "fp-lib-table"), "w") as fh:
        fh.write('(fp_lib_table\n  (version 7)\n'
                 '  (lib (name "laptop_mainboard")(type "KiCad")'
                 '(uri "${KIPRJMOD}/laptop_mainboard.pretty")'
                 '(options "")(descr "Projekt-Footprints"))\n)\n')

    # Footprint-Bibliothek
    lib = pcb.build_library()
    for name, fp in lib.items():
        write(os.path.join(KICAD, "laptop_mainboard.pretty", name + ".kicad_mod"),
              fp.mod_file())

    # ------------------------------------------------------------------
    # Board: 340 x 112 mm fuer HP-EliteBook-850-G7-Chassis (358.5 x 233.9mm)
    # Rechte Kante (hinten->vorn): USB-C-PD (Barrel-Position), TB4, HDMI,
    # USB-A, RJ45. Linke Kante: USB-A, Klinke. Akku-Schacht unterhalb.
    # ------------------------------------------------------------------
    PL = [
        # CPU + Speicher + Storage
        ("AM5_Socket_LGA1718", "XU1", "Ryzen 5 8600G", 130, 82, 0),
        ("SODIMM_DDR5_262", "J_MEM1", "DDR5 SO-DIMM A", 198, 60, 0),
        ("SODIMM_DDR5_262", "J_MEM2", "DDR5 SO-DIMM B", 198, 98, 0),
        ("M2_M_2280", "J_SSD1", "NVMe Gen4 x4", 248, 52, 270),
        ("M2_M_2280", "J_SSD2", "NVMe Gen4 x4", 266, 52, 270),
        ("M2_E_2230", "J_WIFI", "WLAN/BT", 292, 58, 270),
        # VRM links vom Sockel
        ("QFN40_5x5", "U11", "MP2857", 90, 42, 0),
        ("QFN31_5x5", "U12", "Stage CPU1", 76, 58, 0),
        ("QFN31_5x5", "U13", "Stage CPU2", 76, 70, 0),
        ("QFN31_5x5", "U14", "Stage CPU3", 76, 82, 0),
        ("QFN31_5x5", "U15", "Stage CPU4", 76, 94, 0),
        ("QFN31_5x5", "U16", "Stage SOC1", 76, 106, 0),
        ("QFN31_5x5", "U17", "Stage SOC2", 76, 118, 0),
        ("IND_7x7", "L2", "220n", 84, 58, 90),
        ("IND_7x7", "L3", "220n", 84, 70, 90),
        ("IND_7x7", "L4", "220n", 84, 82, 90),
        ("IND_7x7", "L5", "220n", 84, 94, 90),
        ("IND_7x7", "L6", "220n", 69, 108, 90),
        ("IND_7x7", "L7", "220n", 69, 120, 90),
        ("CAP_7343", "C5", "470u", 104, 120, 0),
        ("CAP_7343", "C6", "470u", 113, 120, 0),
        ("CAP_7343", "C7", "470u", 122, 120, 0),
        ("CAP_7343", "C8", "470u", 146, 120, 0),
        ("CAP_7343", "C9", "470u", 155, 120, 0),
        # BIOS + RTC + Systemtakt
        ("SOIC8", "U19", "W25Q256JW", 168, 48, 0),
        ("XTAL_3215", "Y1", "32.768k", 176, 48, 0),
        ("XTAL_3225", "Y2", "48M", 183, 48, 0),
        ("COIN_ML1220", "BT1", "ML1220", 223, 133, 90),
        # Systemwandler oben links neben dem Sockel
        ("QFN20_3x4", "U4", "5V Buck", 98, 38, 0),
        ("QFN20_3x4", "U5", "3V3 Buck", 109, 38, 0),
        ("QFN20_3x4", "U9", "3V3_ALW", 120, 38, 0),
        ("DFN6_2x2", "U6", "1V8", 129, 38, 0),
        ("DFN6_2x2", "U7", "1V05_TB", 137, 38, 0),
        ("DFN6_2x2", "U8", "0V88_TB", 145, 38, 0),
        ("DFN6_2x2", "U18", "1V1_MEM", 153, 38, 0),
        ("IND_4x4", "L9", "1u", 98, 44, 0),
        ("IND_4x4", "L10", "1u", 109, 44, 0),
        ("IND_4x4", "L11", "1u", 120, 44, 0),
        ("IND_4x4", "L12", "470n", 129, 44, 0),
        ("IND_4x4", "L13", "470n", 137, 44, 0),
        ("IND_4x4", "L14", "470n", 145, 44, 0),
        ("IND_4x4", "L15", "470n", 153, 44, 0),
        # Linke Kante (wie 850 G7): USB-A, Klinke; Audio-Codec
        ("USBA3_TH", "J_USBA1", "USB-A 5G", 40, 60, 90),
        ("SOT23-5", "U32", "VBUS-SW", 58, 60, 0),
        ("TRRS_35", "J_AUX", "Klinke", 38, 84, 90),
        ("QFN48_6x6", "U39", "ALC256", 60, 98, 0),
        # Rechte Kante (wie 850 G7, hinten->vorn)
        ("USBC_24", "J_TB0", "USB-C PD 100W + TB4", 352, 42, 270),
        ("USBC_24", "J_TB1", "TB4", 352, 54, 270),
        ("HDMI_A", "J_HDMI", "HDMI 2.1", 350, 70, 270),
        ("USBA3_TH", "J_USBA2", "USB-A 5G", 352, 90, 270),
        ("RJ45_MAGJACK", "J_LAN1", "2.5GbE", 352, 112, 270),
        ("BGA_10x9_JHL8540", "U23", "JHL8540 TB4", 330, 48, 0),
        ("BGA96_9x9", "U1", "TPS65988 PD", 322, 64, 0),
        ("SOIC8", "U2", "PD-Cfg", 308, 64, 0),
        ("TSSOP24", "U36", "TPD12S016", 332, 84, 90),
        ("SOT23-5", "U33", "VBUS-SW", 330, 92, 0),
        ("QFN48_6x6", "U34", "RTL8125BG", 325, 112, 0),
        ("XTAL_3225", "Y3", "25M", 316, 118, 0),
        ("C_1812", "C13", "1n/2kV", 330, 104, 0),
        # Laderegler unten rechts
        ("QFN32_4x4", "U3", "BQ25731", 287, 124, 0),
        ("R_2512", "R2", "10m", 296, 124, 0),
        ("R_2512", "R1", "10m", 305, 124, 0),
        ("PowerPAK_1212", "Q5", "BATFET", 313, 124, 0),
        ("PowerPAK_1212", "Q1", "SiSS22DN", 285, 136, 0),
        ("PowerPAK_1212", "Q2", "SiSS22DN", 292, 136, 0),
        ("IND_7x7", "L1", "3u3", 300, 136, 0),
        ("PowerPAK_1212", "Q3", "SiSS22DN", 309, 136, 0),
        ("PowerPAK_1212", "Q4", "SiSS22DN", 316, 136, 0),
        ("R_0603", "RT3", "NTC CHG", 287, 130, 0),
        ("CAP_7343", "C1", "330u", 250, 138, 0),
        ("CAP_7343", "C2", "330u", 259, 138, 0),
        ("CAP_7343", "C3", "330u", 268, 138, 0),
        ("CAP_7343", "C4", "330u", 277, 138, 0),
        # Obere Kante: eDP, Webcam, Backlight, Luefter
        ("FPC40_05", "J_EDP", "eDP-Panel", 183, 35, 0),
        ("FPC6_05", "J_CAM", "Webcam", 203, 35, 0),
        ("SOT23-5", "U37", "VLCD-SW", 212, 40, 0),
        ("TSSOP16EP", "U38", "MP3389 BL", 221, 42, 0),
        ("IND_4x4", "L8", "10u", 230, 42, 0),
        ("SMA_DIODE", "D1", "SS3P4", 212, 46, 0),
        ("CONN_FAN4", "J_FAN1", "Luefter L", 78, 35, 0),
        ("CONN_FAN4", "J_FAN2", "Luefter R", 290, 35, 0),
        # Untere Kante: Akku, Tastatur, Touchpad, Lautsprecher, EC
        ("CONN_BAT8_2mm", "J_BAT", "HP-Akku 3S 56Wh", 195, 138, 0),
        ("FPC30_05", "J_KB", "HP-Tastatur", 154, 137, 0),
        ("FPC8_05", "J_TP", "HP-Clickpad", 115, 137, 0),
        ("FPC6_05", "J_LID", "Lid-Sensor", 100, 137, 0),
        ("CONN_SPK2", "J_SPKL", "Spk L", 52, 138, 0),
        ("CONN_SPK2", "J_SPKR", "Spk R", 276, 131, 0),
        ("LQFP128_14x14", "U41", "IT5570E EC", 135, 131, 0),
        ("SOIC8", "U42", "EC-Flash", 152, 131, 0),
        ("SOT23-5", "U21", "SSD1-SW", 277, 111, 0),
        ("SOT23-5", "U22", "SSD2-SW", 277, 104, 0),
        ("R_0603", "RT1", "NTC CPU", 163, 90, 0),
        ("R_0603", "RT2", "NTC VRM", 90, 48, 0),
        # Befestigung (Raster am 850-G7-Chassis final ausmessen!)
        ("MTG_M2_5", "H1", "", 28, 33, 0),
        ("MTG_M2_5", "H2", "", 160, 33, 0),
        ("MTG_M2_5", "H3", "", 345, 33, 0),
        ("MTG_M2_5", "H4", "", 30, 138, 0),
        ("MTG_M2_5", "H5", "", 240, 139, 0),
        ("MTG_M2_5", "H6", "", 274, 80, 0),
        ("MTG_M2_5", "H7", "", 28, 105, 0),
        ("MTG_M2_5", "H8", "", 300, 33, 0),
        # Bestueckung Unterseite (B.Cu): Entkopplungsbaenke unter dem Sockel
        ("R_0603", "C20", "22u", 112, 72, 0, "B"),
        ("R_0603", "C21", "22u", 122, 72, 0, "B"),
        ("R_0603", "C22", "22u", 132, 72, 0, "B"),
        ("R_0603", "C23", "22u", 142, 72, 0, "B"),
        ("R_0603", "C24", "22u", 112, 80, 0, "B"),
        ("R_0603", "C25", "22u", 122, 80, 0, "B"),
        ("R_0603", "C26", "22u", 132, 80, 0, "B"),
        ("R_0603", "C27", "22u", 142, 80, 0, "B"),
        ("R_0603", "C28", "22u", 112, 88, 0, "B"),
        ("R_0603", "C29", "22u", 122, 88, 0, "B"),
        ("R_0603", "C30", "22u", 132, 88, 0, "B"),
        ("R_0603", "C31", "22u", 142, 88, 0, "B"),
        ("R_0603", "C32", "22u", 104, 96, 0, "B"),
        ("R_0603", "C33", "22u", 114, 96, 0, "B"),
        ("R_0603", "C34", "22u", 124, 96, 0, "B"),
        ("R_0603", "C35", "22u", 104, 64, 0, "B"),
        ("R_0603", "C36", "22u", 114, 64, 0, "B"),
        ("R_0603", "C37", "22u", 124, 64, 0, "B"),
        # Schienen-Entkopplung (B.Cu, unter DIMM-/Wandlerbereich)
        ("R_0603", "C40", "10u", 170, 60, 0, "B"),
        ("R_0603", "C41", "10u", 178, 60, 0, "B"),
        ("R_0603", "C42", "10u", 186, 60, 0, "B"),
        ("R_0603", "C43", "10u", 194, 60, 0, "B"),
        ("R_0603", "C44", "10u", 202, 60, 0, "B"),
        ("R_0603", "C45", "10u", 210, 60, 0, "B"),
        ("R_0603", "C46", "10u", 218, 60, 0, "B"),
        ("R_0603", "C47", "10u", 226, 60, 0, "B"),
        ("R_0603", "C48", "10u", 234, 60, 0, "B"),
        ("R_0603", "C49", "10u", 242, 60, 0, "B"),
        ("R_0603", "C52", "100n", 170, 68, 0, "B"),
        ("R_0603", "C53", "100n", 178, 68, 0, "B"),
        ("R_0603", "C54", "100n", 186, 68, 0, "B"),
        ("R_0603", "C55", "100n", 194, 68, 0, "B"),
        ("R_0603", "C56", "100n", 202, 68, 0, "B"),
        ("R_0603", "C57", "100n", 210, 68, 0, "B"),
        ("R_0603", "C58", "100n", 218, 68, 0, "B"),
        ("R_0603", "C59", "100n", 226, 68, 0, "B"),
        ("R_0603", "C60", "100n", 234, 68, 0, "B"),
        ("R_0603", "C61", "100n", 242, 68, 0, "B"),
        # ESD-Arrays (B.Cu, direkt hinter den Buchsen)
        ("DFN6_2x2", "U50", "ESD TB0-L1", 344, 40, 0, "B"),
        ("DFN6_2x2", "U51", "ESD TB0-L2", 344, 46, 0, "B"),
        ("DFN6_2x2", "U52", "ESD TB1-L1", 344, 52, 0, "B"),
        ("DFN6_2x2", "U53", "ESD TB1-L2", 344, 58, 0, "B"),
        ("DFN6_2x2", "U54", "ESD USBA1", 46, 60, 0, "B"),
        ("DFN6_2x2", "U55", "ESD USBA2", 344, 90, 0, "B"),
        ("DFN6_2x2", "U56", "ESD HDMI-1", 342, 70, 0, "B"),
        ("DFN6_2x2", "U57", "ESD HDMI-2", 342, 76, 0, "B"),
        ("DFN6_2x2", "U58", "ESD CC", 350, 34, 0, "B"),
        ("DFN6_2x2", "U59", "ESD TB-USB2", 344, 64, 0, "B"),
        ("DFN6_2x2", "U60", "ESD A-USB2", 46, 68, 0, "B"),
        # USB2-Chokes + Straps (B.Cu)
        ("R_0603", "FL1", "CMC Webcam", 203, 40, 0, "B"),
        ("R_0603", "FL2", "CMC WLAN", 292, 52, 0, "B"),
        ("R_0603", "R10", "10k", 60, 40, 0, "B"),
        ("R_0603", "R11", "10k", 60, 46, 0, "B"),
        ("R_0603", "R12", "10k", 60, 52, 0, "B"),
        ("R_0603", "R13", "10k", 60, 58, 0, "B"),
        ("R_0603", "R14", "10k", 60, 64, 0, "B"),
        ("R_0603", "R15", "10k", 60, 70, 0, "B"),
    ]

    lib_board = pcb.build_library()
    extras = []
    # Platinenumriss 340 x 112 (EliteBook-850-G7-Innenraum, final ausmessen)
    X0, Y0, X1, Y1 = 25, 30, 365, 142
    extras += [pcb.gr_line(X0, Y0, X1, Y0), pcb.gr_line(X1, Y0, X1, Y1),
               pcb.gr_line(X1, Y1, X0, Y1), pcb.gr_line(X0, Y1, X0, Y0)]
    # Zonen
    all_pts = [(X0, Y0), (X1, Y0), (X1, Y1), (X0, Y1)]
    extras.append(pcb.zone(1, "GND", "In2.Cu", all_pts, "GND1"))
    extras.append(pcb.zone(1, "GND", "In5.Cu", all_pts, "GND2"))
    extras.append(pcb.zone(1, "GND", "B.Cu", all_pts, "GND-Bottom"))
    extras.append(pcb.zone(2, "+VSYS", "In3.Cu",
                           [(180, 30), (365, 30), (365, 142), (180, 142)], "VSYS"))
    extras.append(pcb.zone(2, "+VSYS", "In3.Cu",
                           [(25, 30), (115, 30), (115, 125), (25, 125)], "VSYS-VRM"))
    extras.append(pcb.zone(3, "+5V", "In4.Cu",
                           [(150, 30), (270, 30), (270, 80), (150, 80)], "5V"))
    extras.append(pcb.zone(4, "+3V3", "In4.Cu",
                           [(25, 30), (150, 30), (150, 142), (25, 142)], "3V3"))
    extras.append(pcb.zone(4, "+3V3", "In4.Cu",
                           [(270, 30), (365, 30), (365, 142), (270, 142)], "3V3-IO"))
    extras.append(pcb.zone(5, "+VDDCR_CPU", "In1.Cu",
                           [(95, 55), (160, 55), (160, 112), (95, 112)], "VDDCR_CPU"))
    extras.append(pcb.zone(6, "+VDDCR_SOC", "In1.Cu",
                           [(75, 60), (93, 60), (93, 105), (75, 105)], "VDDCR_SOC"))
    # Routing-Korridore (Hauptsignale, Feinabgleich siehe docs/layout.md)
    for i in range(4):  # PCIe P0 -> SSD1 (vertikal bei x=248)
        extras.append(pcb.diff_pair(160, 88 + i * 1.5, 240, 66 + i * 1.2, "In6.Cu"))
    for i in range(4):  # PCIe P2 -> JHL8540
        extras.append(pcb.diff_pair(160, 58 + i * 1.5, 322, 46 + i * 0.8, "In6.Cu"))
    for i in range(4):  # HDMI TMDS -> Buchse
        extras.append(pcb.diff_pair(160, 100 + i * 1.5, 342, 68 + i * 0.8, "F.Cu"))
    for i in range(4):  # DDR-Korridor Kanal A (Beispielpaare)
        extras.append(pcb.diff_pair(160, 70 + i * 1.5, 178, 58 + i * 1.2, "In1.Cu"))
    for i in range(4):  # MDI: RTL8125 -> RJ45
        extras.append(pcb.diff_pair(331, 108 + i * 2, 342, 106 + i * 3, "F.Cu"))
    for i in range(2):  # DP0/DP1 -> JHL8540 DP-IN
        extras.append(pcb.diff_pair(160, 64 + i * 1.5, 324, 54 + i * 1, "In6.Cu"))
    # GND-Stitching-Vias (Raster 15mm, Sockel- und Randzonen ausgespart)
    yv = 36
    while yv < 140:
        xv = 32
        while xv < 362:
            in_socket = 92 < xv < 165 and 40 < yv < 118
            in_edge = xv > 336 or xv < 44
            in_dimm = 158 < xv < 238 and 50 < yv < 124
            in_ssd = 228 < xv < 300 and 34 < yv < 122
            if not (in_socket or in_edge or in_dimm or in_ssd):
                extras.append(pcb.via(xv, yv, net=1))
            xv += 15
        yv += 13
    # Beschriftungen
    extras.append(pcb.gr_text("Laptop-Mainboard fuer HP EliteBook 850 G7 - Rev B", 195, 26, "F.SilkS", 3))
    extras.append(pcb.gr_text("AM5 - Kuehlerzone 100x75mm freihalten", 130, 82, "Cmts.User", 2))
    extras.append(pcb.gr_text("HP-Akkuschacht (3S 56Wh) unterhalb", 195, 147, "Cmts.User", 2))
    extras.append(pcb.gr_text("Umriss + Lochbild am realen 850-G7-Chassis verifizieren (docs/elitebook.md)", 195, 152, "Cmts.User", 1.5))

    board_text, netmap = pcb.board_file(PL, lib_board, extras)
    write(os.path.join(KICAD, "laptop-mainboard.kicad_pcb"), board_text)
    print(f"  Board-Netze: {len(netmap)}")

    # Stueckliste
    bom_path = os.path.join(DOCS, "bom.csv")
    with open(bom_path, "w") as fh:
        fh.write("Referenz;Wert;Beschreibung;Hersteller-Teilenummer;Footprint\n")
        for c in all_comps:
            fh.write(f"{c.ref};{c.value};{c.desc};{c.mpn};"
                     f"{c.fp.replace('laptop_mainboard:', '')}\n")
    print(f"  docs/bom.csv  ({len(all_comps)} Positionen)")
    print("Fertig.")


if __name__ == "__main__":
    main()
