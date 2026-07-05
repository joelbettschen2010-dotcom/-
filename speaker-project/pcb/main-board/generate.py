"""
Main-Board generieren: Schaltplan (.kicad_sch) + Layout (.kicad_pcb).

Platzierungskonzept (100 x 80 mm, EMV-orientiert):

   0                                  100 (mm)
   +--------------------------------------+ 0
   | J7 J8 J9 (Speaker)   J1 XT30  J2/J3  |      <- Leistungsseite oben
   |  [Filter L2-L7]   [Q1 Q2 F1]  Bulk   |
   |  U6 TPA (FR)  U7 TPA (Sub)  [Buck 5V]|
   |--------------------------------------| 40
   |  U3 ADAU1701 + Analog   U8 ESP32-S3  |
   |  [AGND-Insel]           (Antenne  -> |      <- Antenne ueber Platinenrand
   | J6 Aux   J13 BT   J5 ICP  J14 J11 USB|
   +--------------------------------------+ 80
   Lautsprecher-Ausgaenge (oben links) sind maximal vom Analog-Eingang
   (unten links) getrennt; Star-Ground-Netztie NT1 sitzt am AGND-Rand.
"""

import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, ".."))
sys.path.insert(0, HERE)

import design  # noqa: E402
from schgen import write_schematic  # noqa: E402

BOARD_W, BOARD_H = 100, 80


def gen_schematic():
    write_schematic(os.path.join(HERE, "main-board.kicad_sch"),
                    "SpeakerBox Pro - Main Board", design.COMPS, design.GROUPS,
                    page="A1")
    print("Schaltplan geschrieben: main-board.kicad_sch")


def gen_pcb():
    import pcbnew
    from pcbgen import BoardBuilder, shelf_pack

    b = BoardBuilder(os.path.join(HERE, "main-board.kicad_pcb"), BOARD_W, BOARD_H)
    by = {c.ref: c for c in design.COMPS}

    # ---- Fixe Platzierung der Schluesselbauteile -------------------------
    fixed = {
        # Lautsprecher-Klemmen Oberkante links (max. Abstand zum Analog-In)
        "J7": (8, 4, 0), "J8": (22, 4, 0), "J9": (36, 4, 0),
        # Akku-Eingang + Balancer oben rechts (XH-Stecker 15.9mm breit!)
        "J1": (60, 5, 90), "J2": (70, 6, 0), "J3": (86.5, 6, 0),
        # Ausgangsfilter FR (13.7mm-Courtyards -> 14mm-Raster)
        "L2": (7, 17, 0), "L3": (21, 17, 0), "L4": (35, 17, 0), "L5": (49, 17, 0),
        # Sub-Filter zweite Reihe
        "L6": (7, 32, 0), "L7": (21, 32, 0),
        # Endstufen
        "U6": (33, 33, 0), "U7": (46, 33, 0),
        # Eingangsschutz + Softswitch-Reihe
        "F1": (64, 14, 0), "Q1": (72, 14, 0), "Q2": (80, 14, 0), "D1": (88, 14, 0),
        "J4": (96, 16, 90),
        # Bulk-Elkos an PVDD
        "C80": (64, 27, 0), "C81": (78.5, 27, 0), "C82": (64, 38, 0),
        # Buck + LDO rechte Spalte (gestaffelt)
        "U1": (92, 35.5, 0), "D8": (84, 37, 0), "L1": (92, 43, 0),
        "U2": (85, 50, 0), "U9": (90, 56, 0),
        # DSP-Sektion unten links (AGND-Insel)
        "U3": (28, 55, 0), "Y1": (26, 66, 0), "C17": (16, 45, 0),
        "R22": (58.3, 67, 90), "R21": (91, 21, 0),
        "R14": (41, 76, 0), "R62": (45, 76, 0), "R20": (49, 76, 0), "C31": (53, 76, 0),
        "R63": (57, 76, 0), "C30": (91, 24, 0), "R19": (87, 21, 0), "C40": (58.3, 70.5, 90), "C43": (58.3, 56.5, 90), "U4": (44, 48, 0), "NT1": (46, 64, 0),
        # Audio-Eingang + Stecker Unterkante links
        "J6": (9, 72, 270), "J13": (14, 76.5, 90), "J14": (40, 70, 0), "J5": (30, 62, 0),
        # ESP32 Ecke unten rechts, Antenne ragt ueber die Unterkante
        "U8": (84, 72, 180),
        # USB-C rechte Kante
        "J11": (97, 52, 270),
        # Debug/Service
        "J12": (76.5, 42, 0), "J10": (60, 48, 0), "SW1": (52, 46, 0), "SW2": (52, 53, 0),
    }
    placed = set()
    for ref, (x, y, rot) in fixed.items():
        b.add_footprint(by[ref], x, y, rot)
        placed.add(ref)

    # ---- Rest gruppenweise in Regionen packen ---------------------------
    # ---- Bohrloecher (vor der Packung registrieren!) ----------------------
    for x, y in [(4, 43), (4, 58), (96.5, 24), (52, 5)]:
        b.mounting_hole(x, y)

    regions = {
        "POWER INPUT + SOFT SWITCH": (52, 8, 100, 21),
        "5V BUCK + 3V3 LDO": (80, 30, 100, 56),
        "DSP ADAU1701": (6, 40, 56, 52),
        "AUDIO IN + DAC OUT": (2, 54, 28, 72),
        "AMP FULLRANGE (U6 Master BTL)": (2, 23.6, 100, 26.8),
        "AMP SUB (U7 Slave PBTL)": (28, 38.8, 66, 41.8),
        "ESP32-S3 + USB + DEBUG": (56, 44, 80, 58),
        "BLUETOOTH + CONNECTORS": (28, 56, 44, 68),
    }
    # Amp-Gruppen zuerst packen (deren Baender sind am knappsten)
    order = ["AMP FULLRANGE (U6 Master BTL)", "AMP SUB (U7 Slave PBTL)",
             "POWER INPUT + SOFT SWITCH", "5V BUCK + 3V3 LDO", "DSP ADAU1701",
             "AUDIO IN + DAC OUT", "ESP32-S3 + USB + DEBUG",
             "BLUETOOTH + CONNECTORS"]
    groups = dict(design.GROUPS)
    leftover_all = []
    for gname in order:
        comps = [by[r] for r in groups[gname] if r not in placed]
        if not comps:
            continue
        leftover = shelf_pack(b, comps, regions[gname])
        leftover_all += leftover
    # Ueberlauf in Reserve-Region
    if leftover_all:
        rest = shelf_pack(b, leftover_all, (2, 2, 98, 78))
        if rest:
            rest = shelf_pack(b, rest, (52, 58, 59.5, 78))
        if rest:
            print("NICHT untergebracht:", [c.ref for c in rest])

    # ---- Zonen -----------------------------------------------------------
    # AGND-Insel (Analogbereich unten links) mit hoher Prioritaet
    b.zone("AGND", pcbnew.F_Cu, (2, 42, 52, 78), priority=3)
    b.zone("AGND", pcbnew.B_Cu, (2, 42, 52, 78), priority=3)
    # PVDD-Polygon auf B.Cu unter der Verstaerkersektion (Leistungsverteilung)
    b.zone("PVDD", pcbnew.B_Cu, (28, 8, 100, 42), priority=2)
    # GND-Flaechen ganzflaechig beide Lagen (niedrigste Prioritaet)
    b.zone("GND", pcbnew.F_Cu, (0, 0, BOARD_W, BOARD_H), priority=0)
    b.zone("GND", pcbnew.B_Cu, (0, 0, BOARD_W, BOARD_H), priority=0)

    b.save()
    print("Layout geschrieben: main-board.kicad_pcb")


def gen_project():
    proj = os.path.join(HERE, "main-board.kicad_pro")
    if not os.path.exists(proj):
        with open(proj, "w") as fh:
            fh.write('{"meta":{"filename":"main-board.kicad_pro","version":1}}\n')


if __name__ == "__main__":
    gen_schematic()
    gen_pcb()
    gen_project()
