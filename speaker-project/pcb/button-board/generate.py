"""Button-Board generieren: 60 x 20 mm, Taster in Reihe an der Oberkante."""

import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, ".."))
sys.path.insert(0, HERE)

import design  # noqa: E402
from schgen import write_schematic  # noqa: E402

BOARD_W, BOARD_H = 60, 20


def gen_schematic():
    write_schematic(os.path.join(HERE, "button-board.kicad_sch"),
                    "SpeakerBox Pro - Button Board", design.COMPS, design.GROUPS,
                    page="A3")
    print("Schaltplan geschrieben: button-board.kicad_sch")


def gen_pcb():
    import pcbnew
    from pcbgen import BoardBuilder, shelf_pack

    b = BoardBuilder(os.path.join(HERE, "button-board.kicad_pcb"), BOARD_W, BOARD_H)
    by = {c.ref: c for c in design.COMPS}

    # Taster in Reihe (Gehaeusefront), LED mittig, OLED-Header + JST unten
    fixed = {
        # SW_PUSH_6mm hat Pin-1-Ursprung: bei rot90 liegen die Pads bei
        # y-6.5 und y; Stoessel-Zentrum = (x+2.25, y-3.25)
        "SW1": (5, 9.5, 90), "SW2": (14, 9.5, 90), "SW3": (23, 9.5, 90),
        "SW4": (32, 9.5, 90), "SW5": (41, 9.5, 90), "SW6": (50, 9.5, 90),
        # Leiterwiderstaende direkt unter ihrem Taster (kurze LADx-Wege)
        "R1": (7.25, 12.7, 90), "R2": (16.25, 12.7, 90), "R3": (25.25, 12.7, 90),
        "R4": (34.25, 12.7, 90), "R5": (43.25, 12.7, 90),
        # LED-Sektion rechts
        "LED1": (51, 14, 0), "D1": (42, 17, 0), "C1": (51, 18.6, 0),
        "C2": (46.2, 12.6, 90), "R6": (47, 18.7, 0),
        "J1": (10, 16.5, 0),      # JST-PH 8 unten
        "J2": (36.6, 17.5, 270),  # OLED-Header gedreht: GND aussen, SDA am Reihenanfang
    }
    placed = set()
    for ref, (x, y, rot) in fixed.items():
        b.add_footprint(by[ref], x, y, rot)
        placed.add(ref)

    for x, y in [(3, 17.5), (58.2, 15)]:
        b.mounting_hole(x, y, d=2.7)   # M2.5

    rest = shelf_pack(b, [c for c in design.COMPS if c.ref not in placed],
                      (2, 12, 28, 15.5))
    if rest:
        rest = shelf_pack(b, rest, (2, 2, 58, 18.5))
    if rest:
        print("NICHT untergebracht:", [c.ref for c in rest])

    zf = b.zone("GND", pcbnew.F_Cu, (0, 0, BOARD_W, BOARD_H), priority=0)
    zb = b.zone("GND", pcbnew.B_Cu, (0, 0, BOARD_W, BOARD_H), priority=0)
    # kleines Board, THT-Handloetung: Vollanbindung statt Thermals
    zf.SetPadConnection(pcbnew.ZONE_CONNECTION_FULL)
    zb.SetPadConnection(pcbnew.ZONE_CONNECTION_FULL)
    b.save()
    print("Layout geschrieben: button-board.kicad_pcb")


if __name__ == "__main__":
    gen_schematic()
    gen_pcb()
    proj = os.path.join(HERE, "button-board.kicad_pro")
    if not os.path.exists(proj):
        with open(proj, "w") as fh:
            fh.write('{"meta":{"filename":"button-board.kicad_pro","version":1}}\n')
