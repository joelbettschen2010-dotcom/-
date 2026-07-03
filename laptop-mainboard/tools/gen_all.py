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
        d2.sheet_display(), d2.sheet_audio_sd_ec(),
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
        note("Laptop-Mainboard 15.6\\\" - AMD Ryzen 5 8600G (AM5, gesockelt)", 30, 240, 3),
        note("2x DDR5 SO-DIMM | 2x M.2 NVMe Gen4 | 2x Thunderbolt 4 (JHL8540)", 30, 250, 2),
        note("2x USB-C 10G | 2x USB-A | 2x 2.5GbE | HDMI 2.1 | SD | Klinke | 100W USB-PD", 30, 257, 2),
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
    # Board: Floorplan 330 x 115 mm (15.6"-Gehaeuse, Akku unterhalb)
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
        ("CAP_7343", "C5", "470u", 104, 122, 0),
        ("CAP_7343", "C6", "470u", 113, 122, 0),
        ("CAP_7343", "C7", "470u", 122, 122, 0),
        ("CAP_7343", "C8", "470u", 146, 122, 0),
        ("CAP_7343", "C9", "470u", 155, 122, 0),
        # BIOS + RTC
        ("SOIC8", "U19", "W25Q256JW", 168, 48, 0),
        ("XTAL_3215", "Y1", "32.768k", 176, 48, 0),
        ("COIN_ML1220", "BT1", "ML1220", 172, 135, 0),
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
        # Linke Kante: LAN, USB-A, Audio
        ("RJ45_MAGJACK", "J_LAN1", "2.5GbE", 42, 46, 90),
        ("RJ45_MAGJACK", "J_LAN2", "2.5GbE", 42, 72, 90),
        ("QFN48_6x6", "U34", "RTL8125BG", 60, 46, 0),
        ("QFN48_6x6", "U35", "RTL8125BG", 60, 72, 0),
        ("XTAL_3225", "Y3", "25M", 68, 52, 0),
        ("XTAL_3225", "Y4", "25M", 68, 78, 0),
        ("C_1812", "C13", "1n/2kV", 60, 56, 0),
        ("C_1812", "C14", "1n/2kV", 60, 84, 0),
        ("USBA3_TH", "J_USBA1", "USB-A 5G", 40, 96, 90),
        ("SOT23-5", "U32", "VBUS-SW", 68, 92, 0),
        ("TRRS_35", "J_AUX", "Klinke", 38, 114, 90),
        ("QFN48_6x6", "U39", "ALC256", 60, 98, 0),
        # Rechte Kante: TB4, USB-C, HDMI, USB-A, SD
        ("USBC_24", "J_TB0", "TB4 / 100W PD", 352, 42, 270),
        ("USBC_24", "J_TB1", "TB4", 352, 54, 270),
        ("USBC_24", "J_USBC2", "USB-C 10G", 352, 68, 270),
        ("USBC_24", "J_USBC3", "USB-C 10G", 352, 80, 270),
        ("HDMI_A", "J_HDMI", "HDMI 2.1", 350, 96, 270),
        ("USBA3_TH", "J_USBA2", "USB-A 5G", 40, 132, 90),
        ("SD_PUSH", "J_SD", "SD UHS-II", 352, 124, 270),
        ("BGA_10x9_JHL8540", "U23", "JHL8540 TB4", 330, 48, 0),
        ("BGA96_9x9", "U1", "TPS65988 PD", 322, 64, 0),
        ("SOIC8", "U2", "PD-Cfg", 308, 64, 0),
        ("QFN30_4x4", "U28", "HD3SS3220", 336, 74, 0),
        ("QFN30_4x4", "U29", "HD3SS3220", 336, 86, 0),
        ("SOT23-5", "U30", "VBUS-SW", 326, 74, 0),
        ("SOT23-5", "U31", "VBUS-SW", 326, 86, 0),
        ("SOT23-5", "U33", "VBUS-SW", 68, 130, 0),
        ("TSSOP24", "U36", "TPD12S016", 332, 96, 90),
        ("QFN48_6x6", "U40", "GL3224 SD", 322, 96, 0),
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
        ("CAP_7343", "C1", "330u", 258, 142, 0),
        ("CAP_7343", "C2", "330u", 267, 142, 0),
        ("CAP_7343", "C3", "330u", 276, 142, 0),
        ("CAP_7343", "C4", "330u", 285, 142, 0),
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
        ("CONN_BAT8_2mm", "J_BAT", "Akku 4S", 195, 140, 0),
        ("FPC30_05", "J_KB", "Tastatur", 154, 140, 0),
        ("FPC8_05", "J_TP", "Touchpad", 115, 140, 0),
        ("FPC6_05", "J_LID", "Lid-Sensor", 100, 140, 0),
        ("CONN_SPK2", "J_SPKL", "Spk L", 62, 141, 0),
        ("CONN_SPK2", "J_SPKR", "Spk R", 238, 141, 0),
        ("LQFP128_14x14", "U41", "IT5570E EC", 135, 133, 0),
        ("SOIC8", "U42", "EC-Flash", 152, 133, 0),
        ("SOT23-5", "U21", "SSD1-SW", 277, 118, 0),
        ("SOT23-5", "U22", "SSD2-SW", 277, 111, 0),
        ("R_0603", "RT1", "NTC CPU", 163, 90, 0),
        ("R_0603", "RT2", "NTC VRM", 90, 48, 0),
        ("R_0603", "RT3", "NTC CHG", 270, 136, 0),
        # Befestigung
        ("MTG_M2_5", "H1", "", 33, 33, 0),
        ("MTG_M2_5", "H2", "", 160, 33, 0),
        ("MTG_M2_5", "H3", "", 345, 33, 0),
        ("MTG_M2_5", "H4", "", 33, 142, 0),
        ("MTG_M2_5", "H5", "", 248, 141, 0),
        ("MTG_M2_5", "H6", "", 274, 80, 0),
        ("MTG_M2_5", "H7", "", 33, 86, 0),
        ("MTG_M2_5", "H8", "", 300, 33, 0),
    ]

    lib_board = pcb.build_library()
    extras = []
    # Platinenumriss 330 x 115
    X0, Y0, X1, Y1 = 30, 30, 360, 145
    extras += [pcb.gr_line(X0, Y0, X1, Y0), pcb.gr_line(X1, Y0, X1, Y1),
               pcb.gr_line(X1, Y1, X0, Y1), pcb.gr_line(X0, Y1, X0, Y0)]
    # Zonen
    all_pts = [(X0, Y0), (X1, Y0), (X1, Y1), (X0, Y1)]
    extras.append(pcb.zone(1, "GND", "In2.Cu", all_pts, "GND1"))
    extras.append(pcb.zone(1, "GND", "In5.Cu", all_pts, "GND2"))
    extras.append(pcb.zone(1, "GND", "B.Cu", all_pts, "GND-Bottom"))
    extras.append(pcb.zone(2, "+VSYS", "In3.Cu",
                           [(180, 30), (360, 30), (360, 145), (180, 145)], "VSYS"))
    extras.append(pcb.zone(2, "+VSYS", "In3.Cu",
                           [(30, 30), (115, 30), (115, 120), (30, 120)], "VSYS-VRM"))
    extras.append(pcb.zone(3, "+5V", "In4.Cu",
                           [(150, 30), (270, 30), (270, 80), (150, 80)], "5V"))
    extras.append(pcb.zone(4, "+3V3", "In4.Cu",
                           [(30, 30), (150, 30), (150, 145), (30, 145)], "3V3"))
    extras.append(pcb.zone(4, "+3V3", "In4.Cu",
                           [(270, 30), (360, 30), (360, 145), (270, 145)], "3V3-IO"))
    extras.append(pcb.zone(5, "+VDDCR_CPU", "In1.Cu",
                           [(95, 55), (160, 55), (160, 112), (95, 112)], "VDDCR_CPU"))
    extras.append(pcb.zone(6, "+VDDCR_SOC", "In1.Cu",
                           [(75, 60), (93, 60), (93, 105), (75, 105)], "VDDCR_SOC"))
    # Repraesentative Routing-Korridore (Hauptsignale)
    for i in range(4):  # PCIe P0 -> SSD1 (vertikal bei x=243)
        extras.append(pcb.diff_pair(160, 88 + i * 1.5, 240, 66 + i * 1.2, "In6.Cu"))
    for i in range(4):  # PCIe P2 -> JHL8540
        extras.append(pcb.diff_pair(160, 58 + i * 1.5, 322, 46 + i * 0.8, "In6.Cu"))
    for i in range(4):  # HDMI TMDS -> Buchse
        extras.append(pcb.diff_pair(160, 100 + i * 1.5, 344, 94 + i * 0.8, "F.Cu"))
    for i in range(4):  # DDR-Korridor Kanal A (Beispielpaare)
        extras.append(pcb.diff_pair(160, 70 + i * 1.5, 178, 58 + i * 1.2, "In1.Cu"))
    # Beschriftungen
    extras.append(pcb.gr_text("Laptop-Mainboard 15.6\\\" Rev A", 195, 26, "F.SilkS", 3))
    extras.append(pcb.gr_text("AM5 - Kuehlerzone 100x75mm freihalten", 130, 82,
                              "Cmts.User", 2))
    extras.append(pcb.gr_text("Akku-Bereich unterhalb der Platine", 195, 150,
                              "Cmts.User", 2))
    extras.append(pcb.gr_text("Korridore In1/In6: DDR5- und PCIe-Gen4-Paare "
                              "(repraesentativ, Feinrouting offen)", 195, 155,
                              "Cmts.User", 1.5))

    write(os.path.join(KICAD, "laptop-mainboard.kicad_pcb"),
          pcb.board_file(PL, lib_board, extras))

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
