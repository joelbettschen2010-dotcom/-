"""Eigener Specctra-DSN-Exporter (KiCads ExportSpecctraDSN ist im headless
Python-Build defekt -> gibt False zurueck). Erzeugt eine DSN, die die
Freerouting-API/-Engine routen kann.

Vereinfachungen, die trotzdem korrekt sind:
  - Jede Komponente wird an (0,0) front rot 0 platziert; ihre Pads stehen mit
    ABSOLUTEN (bereits gedrehten) Koordinaten im eigenen Image -> keine
    Rotations-/Spiegelungs-Mathematik noetig, Pad-Endlage stimmt exakt.
  - Y-Achse gespiegelt (dsn_y = -kicad_y), wie in KiCads eigenem Export.
  - Padstacks werden nach (Form,Groesse,Bohrung,Lagen) dedupliziert.
  - GND/PVDD/AGND werden als normale Netze mitgeroutet (Planes/Zonen werden
    nach dem Import in KiCad wieder gefuellt).

Aufruf: python3 dsn_export.py <board.kicad_pcb> <out.dsn>
"""
import sys
import pcbnew

SIGNAL_LAYERS = ["F.Cu", "In1.Cu", "In2.Cu", "B.Cu"]
UM = 1000.0  # nm pro um? nein: pcbnew-Koordinaten sind nm; /1000 = um


def to_um(nm):
    return nm / 1000.0


def layer_names(board):
    ids = [pcbnew.F_Cu, pcbnew.In1_Cu, pcbnew.In2_Cu, pcbnew.B_Cu]
    return [(board.GetLayerName(i), i) for i in ids]


def pad_layers(pad, lmap):
    out = []
    for name, lid in lmap:
        if pad.IsOnLayer(lid):
            out.append(name)
    return out


def padstack_def(pad, lmap):
    """(key, dsn_shape_lines) fuer diesen Pad."""
    sz = pad.GetSize()
    w, h = round(to_um(sz.x), 1), round(to_um(sz.y), 1)
    shp = pad.GetShape()
    drill = pad.GetDrillSize()
    dr = round(to_um(drill.x), 1) if pad.GetAttribute() != pcbnew.PAD_ATTRIB_SMD else 0
    lays = pad_layers(pad, lmap)
    key = (shp, w, h, dr, tuple(lays))
    shapes = []
    for lay in lays:
        if shp == pcbnew.PAD_SHAPE_CIRCLE or (shp == pcbnew.PAD_SHAPE_OVAL and abs(w-h) < 0.1):
            shapes.append(f'    (shape (circle {lay} {w}))')
        else:
            # Rechteck-Naeherung (bounding); leicht konservativ
            shapes.append(f'    (shape (rect {lay} {-w/2} {-h/2} {w/2} {h/2}))')
    return key, shapes


def main():
    src, out = sys.argv[1], sys.argv[2]
    b = pcbnew.LoadBoard(src)
    lmap = layer_names(b)

    # --- Board-Umriss (bounding aus Edge.Cuts) ---
    bb = b.GetBoardEdgesBoundingBox()
    x0, y0 = to_um(bb.GetLeft()), -to_um(bb.GetTop())
    x1, y1 = to_um(bb.GetRight()), -to_um(bb.GetBottom())
    # Rechteck-Pfad (geschlossen)
    boundary = f"{x0} {y0} {x1} {y0} {x1} {y1} {x0} {y1} {x0} {y0}"

    padstacks = {}   # key -> (name, shapes)
    images = []      # (imgname, ref, [(pinname, padstackname, x_um, y_um)])
    placements = []  # (imgname, ref)
    net_pins = {}    # netname -> [ "REF-pad" ]

    for fp in b.GetFootprints():
        ref = fp.GetReference()
        img = f"img_{ref}"
        pins = []
        for pad in fp.Pads():
            key, shapes = padstack_def(pad, lmap)
            if not shapes:
                continue           # kein Kupfer (NPTH-Bohrung) -> ueberspringen
            if key not in padstacks:
                psn = f"ps{len(padstacks)}"
                padstacks[key] = (psn, shapes)
            psn = padstacks[key][0]
            pos = pad.GetPosition()
            px, py = to_um(pos.x), -to_um(pos.y)
            pname = pad.GetNumber() or "1"
            pinid = f"{pname}"
            pins.append((pinid, psn, round(px, 2), round(py, 2)))
            net = pad.GetNetname()
            if net:
                net_pins.setdefault(net, []).append(f"{ref}-{pinid}")
        if not pins:
            continue
        images.append((img, ref, pins))
        placements.append((img, ref))

    # --- DSN schreiben ---
    L = []
    ap = L.append
    ap('(pcb main-board')
    ap('  (parser (string_quote ")(space_in_quoted_tokens on)(host_cad "kicad")(host_version "custom"))')
    ap('  (resolution um 1)')
    ap('  (unit um)')
    ap('  (structure')
    for name, _lid in lmap:
        idx = SIGNAL_LAYERS.index(name) if name in SIGNAL_LAYERS else 0
        # Alle 4 Lagen als Signal (GND wird als Bahn mitgeroutet — erreichte
        # 15 offen vs. 87 mit Plane; die GND-Fuellung in KiCad umfliesst die
        # Bahnen danach).
        ap(f'    (layer {name} (type signal) (property (index {idx})))')
    # Umriss als geschlossener Pfad (Freerouting mag (path pcb 0 ...))
    ap(f'    (boundary (path pcb 0 {boundary}))')
    ap('    (via via_default)')
    ap('    (rule (width 150) (clearance 130))')
    ap('  )')

    ap('  (placement')
    for img, ref in placements:
        ap(f'    (component {img} (place {ref} 0 0 front 0))')
    ap('  )')

    ap('  (library')
    for img, ref, pins in images:
        ap(f'    (image {img}')
        for pinid, psn, px, py in pins:
            ap(f'      (pin {psn} {pinid} {px} {py})')
        ap('    )')
    for key, (psn, shapes) in padstacks.items():
        ap(f'    (padstack {psn}')
        for s in shapes:
            ap(s)
        ap('      (attach off)')
        ap('    )')
    # Via-Padstack (durchgehend, 450um Kupfer)
    ap('    (padstack via_default')
    for name, _lid in lmap:
        ap(f'      (shape (circle {name} 450))')
    ap('      (attach off)')
    ap('    )')
    ap('  )')

    ap('  (network')
    # Netzklassen-Breiten grob: Power breiter
    for net, pins in net_pins.items():
        if len(pins) < 2:
            continue
        ap(f'    (net "{net}" (pins {" ".join(pins)}))')
    routed_nets = [n for n, p in net_pins.items() if len(p) >= 2]
    ap('    (class default')
    for n in routed_nets:
        ap(f'      "{n}"')
    ap('      (circuit (use_via via_default))')
    ap('      (rule (width 150) (clearance 130))')
    ap('    )')
    ap('  )')
    ap('  (wiring')
    ap('  )')
    ap(')')

    with open(out, "w") as fh:
        fh.write("\n".join(L))
    print(f"DSN geschrieben: {out}  ({len(placements)} Komponenten, "
          f"{len(routed_nets)} Netze, {len(padstacks)} Padstacks)")


if __name__ == "__main__":
    main()
