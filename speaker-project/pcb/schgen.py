"""
Schematic generator: emits KiCad 7 .kicad_sch files from a component/net table.

Style: every component is drawn as a generated box symbol (pins left/right),
connectivity is made with global net labels attached to each pin — the same
netlist-driven style used for dense auto-generated schematics. All pin
numbers/names were verified against the datasheets (see design.py comments).
"""

import uuid


def _u():
    return str(uuid.uuid4())


GRID = 1.27


def snap(v):
    return round(round(v / GRID) * GRID, 4)


class Comp:
    """Ein Bauteil: ref, value, footprint (lib:name), pins = Liste
    (padnummer, pinname, netz). lcsc = LCSC-Bestellnummer ('' = Hand/DNP)."""

    def __init__(self, ref, value, footprint, pins, lcsc="", desc="", dnp=False):
        self.ref = ref
        self.value = value
        self.footprint = footprint
        self.pins = pins            # [(pad, name, net), ...]
        self.lcsc = lcsc
        self.desc = desc
        self.dnp = dnp


def _sym_name(comp):
    return f"SB_{comp.ref}"


def _box_symbol(comp):
    """Erzeugt die lib_symbols-Definition: Box mit Pins links/rechts."""
    n = len(comp.pins)
    left = comp.pins[: (n + 1) // 2]
    right = comp.pins[(n + 1) // 2:]
    rows = max(len(left), len(right))
    h = snap(max(rows * 2.54 + 2.54, 7.62))
    # Breite nach laengstem Pin-Namen
    maxname = max((len(p[1]) for p in comp.pins), default=4)
    w = snap(max(maxname * 1.3 + 8, 15.24))
    top = snap(h / 2)
    s = []
    name = _sym_name(comp)
    s.append(f'    (symbol "{name}" (pin_names (offset 1.016)) (in_bom yes) (on_board yes)')
    s.append(f'      (property "Reference" "{comp.ref[0]}" (at 0 {top + 2.54} 0) (effects (font (size 1.27 1.27))))')
    s.append(f'      (property "Value" "{comp.value}" (at 0 {-(top + 2.54)} 0) (effects (font (size 1.27 1.27))))')
    s.append(f'      (property "Footprint" "{comp.footprint}" (at 0 0 0) (effects (font (size 1.27 1.27)) hide))')
    s.append(f'      (symbol "{name}_0_1"')
    s.append(f'        (rectangle (start {-w/2} {top}) (end {w/2} {-top})'
             f' (stroke (width 0.254) (type default)) (fill (type background))))')
    s.append(f'      (symbol "{name}_1_1"')
    for i, (pad, pname, net) in enumerate(left):
        y = snap(top - 2.54 - i * 2.54)
        s.append(f'        (pin passive line (at {snap(-w/2 - 3.81)} {y} 0) (length 3.81)'
                 f' (name "{pname}" (effects (font (size 1.0 1.0))))'
                 f' (number "{pad}" (effects (font (size 1.0 1.0)))))')
    for i, (pad, pname, net) in enumerate(right):
        y = snap(top - 2.54 - i * 2.54)
        s.append(f'        (pin passive line (at {snap(w/2 + 3.81)} {y} 180) (length 3.81)'
                 f' (name "{pname}" (effects (font (size 1.0 1.0))))'
                 f' (number "{pad}" (effects (font (size 1.0 1.0)))))')
    s.append('      )')
    s.append('    )')
    return "\n".join(s), w, h


def write_schematic(path, title, comps, groups, page="A2"):
    """Schreibt .kicad_sch. groups = [(gruppen_titel, [refs]), ...] steuert die
    Platzierung in Spalten-Clustern."""
    root_uuid = _u()
    out = []
    out.append('(kicad_sch (version 20230121) (generator speakerbox_gen)')
    out.append(f'  (uuid {root_uuid})')
    out.append(f'  (paper "{page}")')
    out.append(f'  (title_block (title "{title}") (company "SpeakerBox Pro") (rev "1.0"))')

    bycomp = {c.ref: c for c in comps}
    symdefs, dims = [], {}
    for c in comps:
        d, w, h = _box_symbol(c)
        symdefs.append(d)
        dims[c.ref] = (w, h)
    out.append('  (lib_symbols')
    out.extend(symdefs)
    out.append('  )')

    body = []
    # Platzierung: Gruppen als Spalten von links nach rechts, Bauteile
    # innerhalb der Gruppe von oben nach unten gestapelt.
    x0 = 30.0
    page_h = 500.0
    col_x = x0
    col_w_max = 0
    y = 30.0
    for gtitle, refs in groups:
        # Gruppentitel
        gh = sum(dims[r][1] + 14 for r in refs if r in bycomp)
        if y > 40 and y + gh > page_h:
            col_x += col_w_max + 30
            col_w_max = 0
            y = 30.0
        body.append(f'  (text "{gtitle}" (at {snap(col_x)} {snap(y)} 0)'
                    f' (effects (font (size 2.5 2.5) bold)))')
        y += 10
        for ref in refs:
            c = bycomp[ref]
            w, h = dims[ref]
            cx, cy = snap(col_x + w / 2 + 10), snap(y + h / 2)
            body.append(_symbol_instance(c, cx, cy, root_uuid))
            body.extend(_pin_labels(c, cx, cy, w, h))
            y += h + 14
            col_w_max = max(col_w_max, w + 30)
        y += 8
    out.extend(body)
    out.append(f'  (sheet_instances (path "/" (page "1")))')
    out.append(')')
    with open(path, "w") as fh:
        fh.write("\n".join(out) + "\n")


def _symbol_instance(comp, cx, cy, root_uuid):
    s = []
    s.append(f'  (symbol (lib_id "{_sym_name(comp)}") (at {cx} {cy} 0) (unit 1)')
    s.append(f'    (in_bom {"no" if comp.dnp else "yes"}) (on_board yes) (dnp {"yes" if comp.dnp else "no"})')
    s.append(f'    (uuid {_u()})')
    s.append(f'    (property "Reference" "{comp.ref}" (at {cx} {cy - 2} 0) (effects (font (size 1.27 1.27))))')
    s.append(f'    (property "Value" "{comp.value}" (at {cx} {cy + 2} 0) (effects (font (size 1.27 1.27))))')
    s.append(f'    (property "Footprint" "{comp.footprint}" (at {cx} {cy} 0) (effects (font (size 1.27 1.27)) hide))')
    s.append(f'    (property "LCSC" "{comp.lcsc}" (at {cx} {cy} 0) (effects (font (size 1.27 1.27)) hide))')
    for pad, name, net in comp.pins:
        s.append(f'    (pin "{pad}" (uuid {_u()}))')
    s.append(f'    (instances (project "speakerbox" (path "/{root_uuid}"')
    s.append(f'      (reference "{comp.ref}") (unit 1))))')
    s.append('  )')
    return "\n".join(s)


def _pin_labels(comp, cx, cy, w, h):
    """Global-Label an jeder Pinspitze (verbindet per Netznamen)."""
    n = len(comp.pins)
    left = comp.pins[: (n + 1) // 2]
    right = comp.pins[(n + 1) // 2:]
    top = snap(h / 2)
    lbl = []
    for i, (pad, name, net) in enumerate(left):
        if not net:
            continue
        px = snap(cx - w / 2 - 3.81)
        py = snap(cy - (top - 2.54 - i * 2.54))
        lbl.append(f'  (global_label "{net}" (shape input) (at {px} {py} 180)'
                   f' (effects (font (size 1.0 1.0)) (justify right)) (uuid {_u()}))')
    for i, (pad, name, net) in enumerate(right):
        if not net:
            continue
        px = snap(cx + w / 2 + 3.81)
        py = snap(cy - (top - 2.54 - i * 2.54))
        lbl.append(f'  (global_label "{net}" (shape input) (at {px} {py} 0)'
                   f' (effects (font (size 1.0 1.0)) (justify left)) (uuid {_u()}))')
    return lbl
