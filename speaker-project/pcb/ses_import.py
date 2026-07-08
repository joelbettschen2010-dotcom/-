"""Importiert eine Freerouting-Session (.ses) in ein KiCad-Board: legt die
gerouteten Bahnen (wire/path) und Vias als PCB_TRACK/PCB_VIA an.

KiCads ImportSpecctraSES braucht GUI-Kontext (nimmt nur 1 Argument) und
funktioniert headless nicht -> eigener Parser. Koordinaten: DSN/SES in um,
Y beim Export gespiegelt -> hier zuruecktransformieren (y_nm = -y_um*1000).

Aufruf: python3 ses_import.py <board.kicad_pcb> <session.ses> <out.kicad_pcb>
"""
import sys
import pcbnew
from pcbnew import VECTOR2I

LAYER = {"F.Cu": pcbnew.F_Cu, "In1.Cu": pcbnew.In1_Cu,
         "In2.Cu": pcbnew.In2_Cu, "B.Cu": pcbnew.B_Cu}


def tokenize(s):
    return s.replace("(", " ( ").replace(")", " ) ").split()


def parse(tokens):
    """S-Expression -> verschachtelte Listen."""
    it = iter(tokens)

    def rd():
        stack = [[]]
        for t in it:
            if t == "(":
                new = []
                stack[-1].append(new)
                stack.append(new)
            elif t == ")":
                stack.pop()
            else:
                stack[-1].append(t)
        return stack[0]
    return rd()


def find(node, name):
    """erste Unterliste, die mit name beginnt."""
    for x in node:
        if isinstance(x, list) and x and x[0] == name:
            return x
    return None


def find_all(node, name):
    return [x for x in node if isinstance(x, list) and x and x[0] == name]


def um2nm_x(v):
    return int(round(float(v) * 1000.0))


def um2nm_y(v):
    return int(round(-float(v) * 1000.0))   # Y-Spiegelung rueckgaengig


def main():
    src, ses, out = sys.argv[1], sys.argv[2], sys.argv[3]
    board = pcbnew.LoadBoard(src)
    txt = open(ses).read()
    tree = parse(tokenize(txt))
    # tree[0] = ('session' ...)
    session = tree[0]
    routes = find(session, "routes")
    net_out = find(routes, "network_out") if routes else None
    if net_out is None:
        print("Kein network_out in SES gefunden!")
        sys.exit(2)

    nets = {net.GetNetname(): net for net in board.GetNetInfo().NetsByName().values()} \
        if hasattr(board.GetNetInfo(), "NetsByName") else None

    def net_by_name(nm):
        n = board.FindNet(nm)
        return n

    ntracks = nvias = 0
    for net_node in find_all(net_out, "net"):
        # net_node = ['net', '"NAME"', (wire...), (via...) ...]
        nm = net_node[1].strip('"')
        knet = net_by_name(nm)
        for wire in find_all(net_node, "wire"):
            path = find(wire, "path")
            if not path:
                continue
            # path = ['path', LAYER, WIDTH, x1,y1, x2,y2, ...]
            lay = path[1]
            width = int(round(float(path[2])))
            coords = path[3:]
            lid = LAYER.get(lay)
            if lid is None:
                continue
            pts = [(um2nm_x(coords[i]), um2nm_y(coords[i+1]))
                   for i in range(0, len(coords) - 1, 2)]
            for a, b in zip(pts, pts[1:]):
                if a == b:
                    continue
                t = pcbnew.PCB_TRACK(board)
                t.SetStart(VECTOR2I(*a))
                t.SetEnd(VECTOR2I(*b))
                t.SetWidth(width)
                t.SetLayer(lid)
                if knet:
                    t.SetNet(knet)
                board.Add(t)
                ntracks += 1
        for via in find_all(net_node, "via"):
            # via = ['via', VIANAME, x, y]
            x, y = um2nm_x(via[2]), um2nm_y(via[3])
            v = pcbnew.PCB_VIA(board)
            v.SetPosition(VECTOR2I(x, y))
            v.SetViaType(pcbnew.VIATYPE_THROUGH)
            v.SetLayerPair(pcbnew.F_Cu, pcbnew.B_Cu)
            v.SetDrill(pcbnew.FromMM(0.2))
            v.SetWidth(pcbnew.FromMM(0.45))
            if knet:
                v.SetNet(knet)
            board.Add(v)
            nvias += 1

    # Netzklasse + Zonen neu fuellen
    try:
        board.GetDesignSettings().m_NetSettings.m_DefaultNetClass \
            .SetClearance(pcbnew.FromMM(0.127))
    except Exception:
        pass
    pcbnew.SaveBoard(out, board)
    b2 = pcbnew.LoadBoard(out)
    pcbnew.ZONE_FILLER(b2).Fill(b2.Zones())
    pcbnew.SaveBoard(out, b2)
    print(f"Import fertig: {ntracks} Bahnsegmente, {nvias} Vias -> {out}")


if __name__ == "__main__":
    main()
