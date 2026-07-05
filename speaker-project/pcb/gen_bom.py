"""JLCPCB-kompatible BOM + Pick&Place-Nachbearbeitung aus den Design-Daten.

BOM-Format JLCPCB:  Comment, Designator, Footprint, LCSC Part #
Positions-Format:   Designator, Mid X, Mid Y, Layer, Rotation
(Die Positionsdatei erzeugt kicad-cli; hier wird nur der Header angepasst.)
"""

import csv
import importlib.util
import os
import sys
from collections import defaultdict


def load_design(board_dir):
    spec = importlib.util.spec_from_file_location(
        "design", os.path.join(board_dir, "design.py"))
    mod = importlib.util.module_from_spec(spec)
    sys.path.insert(0, os.path.dirname(os.path.abspath(board_dir)))
    spec.loader.exec_module(mod)
    return mod


def write_jlc_bom(board_dir, out):
    design = load_design(board_dir)
    groups = defaultdict(list)
    for c in design.COMPS:
        key = (c.value, c.footprint, c.lcsc)
        groups[key].append(c.ref)
    with open(out, "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["Comment", "Designator", "Footprint", "LCSC Part #"])
        for (value, footprint, lcsc), refs in sorted(groups.items(),
                                                     key=lambda kv: kv[1][0]):
            w.writerow([value, ",".join(sorted(refs)),
                        footprint.split(":")[1], lcsc])
    print(f"BOM geschrieben: {out} ({len(groups)} Positionen)")


if __name__ == "__main__":
    for board in ("main-board", "button-board"):
        d = os.path.join(os.path.dirname(os.path.abspath(__file__)), board)
        write_jlc_bom(d, os.path.join(d, "fab", f"{board}-BOM-JLCPCB.csv"))
