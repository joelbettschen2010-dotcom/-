"""Erzeugt KiCad-8-Schaltplaene (.kicad_sch) aus kompakten Bauteil-Definitionen.

Symbolik: vereinfachte Funktionssymbole; ein Pin kann ein gebuendelter Bus
sein (Name mit [n:0]). Netze werden ueber globale Labels verbunden.
"""
from .sexpr import uid, f

PROJECT = "laptop-mainboard"
PIN_LEN = 3.81       # Pinlaenge
STUB = 7.62          # Drahtstummel vom Pin zum Label
PITCH = 2.54         # Pinabstand


class Comp:
    """Ein Bauteil: pins = Liste (name, side 'L'/'R', etype, netz)."""

    def __init__(self, ref, value, pins, width=30.48, fp="", desc="", mpn=""):
        self.ref = ref
        self.value = value
        self.pins = pins
        self.width = width
        self.fp = fp
        self.desc = desc
        self.mpn = mpn
        left = [p for p in pins if p[1] == "L"]
        right = [p for p in pins if p[1] == "R"]
        rows = max(len(left), len(right), 1)
        self.height = (rows + 1) * PITCH
        self.libname = f"lm:{value}_{ref}"

    def pin_positions(self):
        """Relative Pin-Anschlusspunkte (Bibliotheks-Koordinaten, Y nach oben)."""
        out = []
        li = ri = 0
        w2 = self.width / 2
        h2 = self.height / 2
        num = 0
        for name, side, etype, net in self.pins:
            num += 1
            if side == "L":
                y = h2 - PITCH * (li + 1)
                out.append((str(num), name, etype, net, -w2 - PIN_LEN, y, 0))
                li += 1
            else:
                y = h2 - PITCH * (ri + 1)
                out.append((str(num), name, etype, net, w2 + PIN_LEN, y, 180))
                ri += 1
        return out


def lib_symbol(comp):
    """lib_symbols-Eintrag fuer ein Bauteil."""
    w2, h2 = comp.width / 2, comp.height / 2
    s = []
    s.append(f'    (symbol "{comp.libname}" (pin_names (offset 1.016)) '
             f'(exclude_from_sim no) (in_bom yes) (on_board yes)')
    s.append(f'      (property "Reference" "{comp.ref[0]}" (at 0 {f(h2 + 2.54)} 0) '
             f'(effects (font (size 1.27 1.27))))')
    s.append(f'      (property "Value" "{comp.value}" (at 0 {f(-h2 - 2.54)} 0) '
             f'(effects (font (size 1.27 1.27))))')
    s.append(f'      (property "Footprint" "{comp.fp}" (at 0 0 0) '
             f'(effects (font (size 1.27 1.27)) (hide yes)))')
    s.append(f'      (property "Datasheet" "" (at 0 0 0) '
             f'(effects (font (size 1.27 1.27)) (hide yes)))')
    s.append(f'      (symbol "{comp.value}_{comp.ref}_0_1"')
    s.append(f'        (rectangle (start {f(-w2)} {f(h2)}) (end {f(w2)} {f(-h2)}) '
             f'(stroke (width 0.254) (type default)) (fill (type background)))')
    s.append('      )')
    s.append(f'      (symbol "{comp.value}_{comp.ref}_1_1"')
    for num, name, etype, net, px, py, rot in comp.pin_positions():
        s.append(f'        (pin {etype} line (at {f(px)} {f(py)} {rot}) (length {f(PIN_LEN)})')
        s.append(f'          (name "{name}" (effects (font (size 1.27 1.27))))')
        s.append(f'          (number "{num}" (effects (font (size 1.27 1.27))))')
        s.append('        )')
    s.append('      )')
    s.append('    )')
    return "\n".join(s)


def place(comp, x, y, root_uuid, sheet_uuid):
    """Symbolinstanz + Stummeldraehte + globale Labels an allen Pins."""
    s = []
    h2 = comp.height / 2
    s.append(f'  (symbol (lib_id "{comp.libname}") (at {f(x)} {f(y)} 0) (unit 1)')
    s.append('    (exclude_from_sim no) (in_bom yes) (on_board yes) (dnp no)')
    s.append(f'    (uuid "{uid(comp.ref)}")')
    s.append(f'    (property "Reference" "{comp.ref}" (at {f(x)} {f(y - h2 - 3.81)} 0) '
             f'(effects (font (size 1.27 1.27))))')
    s.append(f'    (property "Value" "{comp.value}" (at {f(x)} {f(y + h2 + 3.81)} 0) '
             f'(effects (font (size 1.27 1.27))))')
    s.append(f'    (property "Footprint" "{comp.fp}" (at {f(x)} {f(y)} 0) '
             f'(effects (font (size 1.27 1.27)) (hide yes)))')
    s.append(f'    (property "Datasheet" "" (at {f(x)} {f(y)} 0) '
             f'(effects (font (size 1.27 1.27)) (hide yes)))')
    for num, name, etype, net, px, py, rot in comp.pin_positions():
        s.append(f'    (pin "{num}" (uuid "{uid(comp.ref + num)}"))')
    s.append(f'    (instances (project "{PROJECT}" '
             f'(path "/{root_uuid}/{sheet_uuid}" (reference "{comp.ref}") (unit 1))))')
    s.append('  )')
    # Draehte + Labels
    for num, name, etype, net, px, py, rot in comp.pin_positions():
        if not net:
            continue
        ax, ay = x + px, y - py
        if rot == 0:      # linker Pin, Stummel nach links
            bx, lrot, just = ax - STUB, 180, "right"
        else:             # rechter Pin, Stummel nach rechts
            bx, lrot, just = ax + STUB, 0, "left"
        s.append(f'  (wire (pts (xy {f(ax)} {f(ay)}) (xy {f(bx)} {f(ay)})) '
                 f'(stroke (width 0) (type default)) (uuid "{uid("w")}"))')
        shape = {"power_in": "input", "power_out": "output",
                 "input": "input", "output": "output"}.get(etype, "bidirectional")
        s.append(f'  (global_label "{net}" (shape {shape}) (at {f(bx)} {f(ay)} {lrot}) '
                 f'(fields_autoplaced yes)')
        s.append(f'    (effects (font (size 1.27 1.27)) (justify {just}))')
        s.append(f'    (uuid "{uid("gl")}")')
        s.append(f'    (property "Intersheetrefs" "${{INTERSHEET_REFS}}" (at {f(bx)} {f(ay)} 0) '
                 f'(effects (font (size 1.27 1.27)) (hide yes)))')
        s.append('  )')
    return "\n".join(s)


def note(text, x, y, size=1.6):
    lines = []
    lines.append(f'  (text "{text}" (exclude_from_sim no) (at {f(x)} {f(y)} 0)')
    lines.append(f'    (effects (font (size {f(size)} {f(size)})) (justify left))')
    lines.append(f'    (uuid "{uid("t")}")')
    lines.append('  )')
    return "\n".join(lines)


def sheet_file(sheet_uuid_file, title, comps_placed, lib_comps, notes, paper="A2"):
    """Komplette .kicad_sch-Datei fuer ein Unterblatt."""
    s = []
    s.append('(kicad_sch')
    s.append('  (version 20231120)')
    s.append('  (generator "eeschema")')
    s.append('  (generator_version "8.0")')
    s.append(f'  (uuid "{sheet_uuid_file}")')
    s.append(f'  (paper "{paper}")')
    s.append('  (title_block')
    s.append(f'    (title "{title}")')
    s.append('    (date "2026-07-03")')
    s.append('    (rev "A")')
    s.append('    (company "Laptop-Mainboard-Projekt")')
    s.append('  )')
    s.append('  (lib_symbols')
    for c in lib_comps:
        s.append(lib_symbol(c))
    s.append('  )')
    for block in notes:
        s.append(block)
    for block in comps_placed:
        s.append(block)
    s.append(')')
    return "\n".join(s) + "\n"


def root_file(root_uuid, sheets, title, notes):
    """Root-Blatt mit Hierarchie-Sheets. sheets: (name, file, s_uuid, x, y, page)."""
    s = []
    s.append('(kicad_sch')
    s.append('  (version 20231120)')
    s.append('  (generator "eeschema")')
    s.append('  (generator_version "8.0")')
    s.append(f'  (uuid "{root_uuid}")')
    s.append('  (paper "A3")')
    s.append('  (title_block')
    s.append(f'    (title "{title}")')
    s.append('    (date "2026-07-03")')
    s.append('    (rev "A")')
    s.append('    (company "Laptop-Mainboard-Projekt")')
    s.append('  )')
    s.append('  (lib_symbols)')
    for block in notes:
        s.append(block)
    for name, fname, s_uuid, x, y, page in sheets:
        s.append(f'  (sheet (at {f(x)} {f(y)}) (size 50.8 20.32) (fields_autoplaced yes)')
        s.append('    (stroke (width 0.1524) (type solid))')
        s.append('    (fill (color 0 0 0 0.0000))')
        s.append(f'    (uuid "{s_uuid}")')
        s.append(f'    (property "Sheetname" "{name}" (at {f(x)} {f(y - 0.8)} 0)')
        s.append('      (effects (font (size 1.27 1.27)) (justify left bottom))')
        s.append('    )')
        s.append(f'    (property "Sheetfile" "{fname}" (at {f(x)} {f(y + 21.1)} 0)')
        s.append('      (effects (font (size 1.27 1.27)) (justify left top))')
        s.append('    )')
        s.append(f'    (instances (project "{PROJECT}" (path "/{root_uuid}" (page "{page}"))))')
        s.append('  )')
    s.append('  (sheet_instances (path "/" (page "1")))')
    s.append(')')
    return "\n".join(s) + "\n"


def symbol_lib(all_comps):
    """Eigenstaendige Symbolbibliothek lm.kicad_sym (gleiche Definitionen)."""
    s = []
    s.append('(kicad_symbol_lib')
    s.append('  (version 20231120)')
    s.append('  (generator "kicad_symbol_editor")')
    s.append('  (generator_version "8.0")')
    for c in all_comps:
        # Bibliotheksdatei nutzt Namen ohne "lm:"-Praefix
        s.append(lib_symbol(c).replace(f'"{c.libname}"', f'"{c.value}_{c.ref}"', 1))
    s.append(')')
    return "\n".join(s) + "\n"
