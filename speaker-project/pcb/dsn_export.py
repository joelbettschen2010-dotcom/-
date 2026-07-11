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
    """(key, dsn_shape_lines) fuer diesen Pad.

    WICHTIG: Masse aus der Bounding-Box, NICHT aus GetSize() — GetSize()
    ignoriert die Pad-Rotation. Bei 90-Grad-gedrehten Steckern (USB-C J11,
    Klinke J6, BT-Header J13 ...) waren Breite/Hoehe vertauscht, Freerouting
    sah Phantom-Freiraum und legte Bahnen quer ueber echtes Kupfer."""
    bb = pad.GetBoundingBox()
    w = round(to_um(bb.GetWidth()), 1)
    h = round(to_um(bb.GetHeight()), 1)
    shp = pad.GetShape()
    drill = pad.GetDrillSize()
    dr = round(to_um(drill.x), 1) if pad.GetAttribute() != pcbnew.PAD_ATTRIB_SMD else 0
    lays = pad_layers(pad, lmap)
    # Gebohrte Pads: Form auf mind. Bohrung+0.56mm aufblasen, damit
    # Freeroutings 0.13er-Clearance auch KiCads 0.25er-HOLE-Clearance
    # erfuellt (Bahnkante >= 0.28 von der Lochkante).
    if dr:
        w = max(w, dr + 560.0)
        h = max(h, dr + 560.0)
        if not lays:
            # NPTH ohne Kupfer (Montageloecher!): als netzloses Hindernis
            # auf ALLEN Lagen exportieren, sonst routet Freerouting durchs Loch
            lays = [name for name, _ in lmap]
    key = (shp, w, h, dr, tuple(lays))
    shapes = []
    for lay in lays:
        # Kreis NUR fuer runde/ovale Pads — quadratische (w==h) RECT-Pads
        # als Kreis zu exportieren schneidet die Ecken ab (J5/J9/EPAD-Bug)
        if shp in (pcbnew.PAD_SHAPE_CIRCLE, pcbnew.PAD_SHAPE_OVAL) \
           and abs(w - h) < 0.1:
            shapes.append(f'    (shape (circle {lay} {max(w, h)}))')
        else:
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

    seen_refs = {}
    for fp in b.GetFootprints():
        ref = fp.GetReference()
        # Referenzen eindeutig machen: die 4 Montageloecher heissen alle
        # "H" — Freerouting behaelt sonst nur EINES und routet durch die
        # anderen drei Bohrungen!
        n = seen_refs.get(ref, 0)
        seen_refs[ref] = n + 1
        if n:
            ref = f"{ref}_{n+1}"
        img = f"img_{ref}"
        pins = []
        anon = 0
        seen_ids = set()
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
            pname = pad.GetNumber()
            if not pname:
                anon += 1
                pname = f"NPTH{anon}"    # Montageloch: eindeutiger Name,
                                         # kein Netz -> reines Hindernis
            pinid = pname
            while pinid in seen_ids:     # doppelte Padnummern (Shield-Tabs)
                anon += 1
                pinid = f"{pname}_{anon}"
            seen_ids.add(pinid)
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
    # Sperrzonen (Rule-Areas aus Footprints: Taster-Hebel SW1/SW2,
    # ESP32-Antennenbereich) -> Freerouting darf dort weder Bahnen noch
    # Vias legen. Als Rechteck je Signallage, auf die Platine geclippt.
    kidx = 0
    # Kupfer-GRAFIKEN in Footprints (z.B. das Sternmasse-Verbindungspolygon
    # im Net-Tie NT1) sind netzlos und fuer Freerouting unsichtbar ->
    # als Sperrflaeche exportieren, sonst legt es Signale quer darueber
    # (3V3 ueber die GND/AGND-Bruecke = Kurzschluss!).
    copper_lids = {lid for _n, lid in lmap}
    for fp in b.GetFootprints():
        for g in fp.GraphicalItems():
            if not isinstance(g, pcbnew.PCB_SHAPE):
                continue
            if g.GetLayer() not in copper_lids:
                continue
            gb = g.GetBoundingBox()
            gx0 = max(to_um(gb.GetLeft()), x0)
            gx1 = min(to_um(gb.GetRight()), x1)
            gy0 = max(-to_um(gb.GetBottom()), min(y0, y1))
            gy1 = min(-to_um(gb.GetTop()), max(y0, y1))
            if gx0 >= gx1 or gy0 >= gy1:
                continue
            lname = [n for n, lid in lmap if lid == g.GetLayer()][0]
            # Nur die POLYGON-MITTE sperren: die Net-Tie-Pads sitzen an den
            # Enden des Polygons — ein voller Keepout macht sie fuer den
            # Router unerreichbar (GND/AGND an NT1 blieben offen).
            inset = min(420.0, (gx1 - gx0) / 3)   # Pad-Zugang freihalten
            gx0i, gx1i = gx0 + inset, gx1 - inset
            if gx0i >= gx1i:
                continue
            kidx += 1
            # y leicht ausgeweitet: verhindert diagonales Anschneiden der
            # Polygon-Ecken durch 45-Grad-Bahnen
            ap(f'    (keepout "kg{kidx}" (rect {lname} '
               f'{gx0i} {gy0 - 200} {gx1i} {gy1 + 200}))')
    for fp in b.GetFootprints():
        for z in fp.Zones():
            if not z.GetIsRuleArea():
                continue
            zb = z.GetBoundingBox()
            kx0 = max(to_um(zb.GetLeft()), x0)
            kx1 = min(to_um(zb.GetRight()), x1)
            ky0 = max(-to_um(zb.GetBottom()), min(y0, y1))
            ky1 = min(-to_um(zb.GetTop()), max(y0, y1))
            if kx0 >= kx1 or ky0 >= ky1:
                continue          # liegt komplett ausserhalb der Platine
            for name, _lid in lmap:
                kidx += 1
                ap(f'    (keepout "ko{kidx}" (rect {name} '
                   f'{kx0} {ky0} {kx1} {ky1}))')
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
    # NETSHUF=<seed>: Netz-Reihenfolge mischen — Freerouting ist
    # deterministisch, ueber die Reihenfolge lassen sich alternative
    # Loesungen erzeugen, falls ein Lauf 1-2 Netze offen laesst.
    import os as _os
    import random as _rnd
    order = list(net_pins.items())
    shuf = _os.environ.get("NETSHUF")
    if shuf:
        _rnd.Random(int(shuf)).shuffle(order)
    for net, pins in order:
        if len(pins) < 2:
            continue
        ap(f'    (net "{net}" (pins {" ".join(pins)}))')
    routed_nets = [n for n, p in order if len(p) >= 2]
    ap('    (class default')
    for n in routed_nets:
        ap(f'      "{n}"')
    ap('      (circuit (use_via via_default))')
    ap('      (rule (width 150) (clearance 130))')
    ap('    )')
    ap('  )')
    ap('  (wiring')
    # WIRED=1: vorhandene Bahnen/Vias als FIXIERTE Vorverdrahtung mitgeben.
    # Damit kann Freerouting ein fast fertiges Board uebernehmen und nur
    # die Restluecken schliessen (z.B. den GVDD1-Insel-Split).
    if _os.environ.get("WIRED") == "1":
        lid2name = {lid: n for n, lid in lmap}
        for t in b.GetTracks():
            netn = t.GetNetname()
            if not netn:
                continue
            if isinstance(t, pcbnew.PCB_VIA):
                vx = round(to_um(t.GetPosition().x), 2)
                vy = round(-to_um(t.GetPosition().y), 2)
                ap(f'    (via via_default {vx} {vy} (net "{netn}")'
                   f')')
            else:
                lname = lid2name.get(t.GetLayer())
                if lname is None:
                    continue
                wmm = round(t.GetWidth() / 1000.0, 1)
                sx = round(to_um(t.GetStart().x), 2)
                sy = round(-to_um(t.GetStart().y), 2)
                ex = round(to_um(t.GetEnd().x), 2)
                ey = round(-to_um(t.GetEnd().y), 2)
                ap(f'    (wire (path {lname} {wmm} {sx} {sy} {ex} {ey})'
                   f'(net "{netn}"))')
    ap('  )')
    ap(')')

    with open(out, "w") as fh:
        fh.write("\n".join(L))
    print(f"DSN geschrieben: {out}  ({len(placements)} Komponenten, "
          f"{len(routed_nets)} Netze, {len(padstacks)} Padstacks)")


if __name__ == "__main__":
    main()
