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
        "SW1": (5, 6, 90), "SW2": (14, 6, 90), "SW3": (23, 6, 90),
        "SW4": (32, 6, 90), "SW5": (41, 6, 90), "SW6": (50, 6, 90),
        "LED1": (50.5, 15, 0),
        "J1": (12, 16.5, 0),      # JST-PH 8 unten
        "J2": (36, 16.5, 0),      # OLED-Header
        "D1": (44.5, 14, 0), "C1": (44, 17.5, 0),
    }
    placed = set()
    for ref, (x, y, rot) in fixed.items():
        b.add_footprint(by[ref], x, y, rot)
        placed.add(ref)

    for x, y in [(3, 17.5), (57.5, 17.5)]:
        b.mounting_hole(x, y, d=2.7)   # M2.5

    rest = shelf_pack(b, [c for c in design.COMPS if c.ref not in placed],
                      (2, 11, 30, 14))
    if rest:
        rest = shelf_pack(b, rest, (2, 2, 58, 18.5))
    if rest:
        print("NICHT untergebracht:", [c.ref for c in rest])

    b.zone("GND", pcbnew.F_Cu, (0, 0, BOARD_W, BOARD_H), priority=0)
    b.zone("GND", pcbnew.B_Cu, (0, 0, BOARD_W, BOARD_H), priority=0)
    b.save()
    print("Layout geschrieben: button-board.kicad_pcb")


if __name__ == "__main__":
    gen_schematic()
    gen_pcb()
    proj = os.path.join(HERE, "button-board.kicad_pro")
    if not os.path.exists(proj):
        with open(proj, "w") as fh:
            fh.write('{"meta":{"filename":"button-board.kicad_pro","version":1}}\n')
