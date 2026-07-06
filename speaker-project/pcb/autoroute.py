"""
Grid-A*-Autorouter fuer die SpeakerBox-Boards (pcbnew, 2 Lagen).

Strategie:
  - GND / AGND / PVDD werden von den Zonen getragen (thermische Anbindung),
    diese Netze werden NICHT als Bahnen geroutet.
  - Alle uebrigen Netze: minimaler Spannbaum ueber die Pads, jede Kante wird
    per A* auf einem 0.25-mm-Raster geroutet (F.Cu bevorzugt horizontal,
    B.Cu vertikal, Via-Strafkosten, Netzklassen-Bahnbreiten).
  - Hindernisse: fremde Pads/Bahnen/Vias inkl. Clearance + halber Bahnbreite,
    Platinenrand, ESP32-Antennenbereich.
  - Fehlgeschlagene Netze werden mit erhoehtem Suchhorizont/reduzierten
    Via-Kosten erneut versucht; das Ergebnis wird per DRC verifiziert.

Aufruf: python3 autoroute.py <board.kicad_pcb> [--keepout x0,y0,x1,y1]
"""

import heapq
import os
import random
import sys
import time

import numpy as np
from scipy.ndimage import binary_dilation
import pcbnew
from pcbnew import VECTOR2I

GRID = 0.25          # mm Rasterweite
CLEARANCE = 0.20     # mm = Design-Regel (JST-PH-Breakout braucht es exakt)
VIA_D, VIA_DRILL = 0.7, 0.4
VIA_COST = 40        # Rasterschritte Strafkosten pro Via
WRONG_DIR_COST = 1.6 # Kostenfaktor gegen die Vorzugsrichtung
ZONE_NETS = {"GND", "AGND", "PVDD"}

# Netzklassen -> Bahnbreite [mm]
def track_width(net):
    n = net.upper()
    if n.startswith(("VBAT",)):
        return 2.0
    if n.startswith(("FR_", "SUB_", "U6_OUT", "U7_OUT", "SUB_OUT")):
        return 1.0
    if n in ("5V", "3V3", "3V3A", "3V3_BT", "SW5V", "PWR_CTL", "DVDD_1V8",
             "GVDD1", "GVDD2", "VBUS", "LED_VDD"):
        return 0.5
    return 0.3


def mm2g(v):
    return int(round(v / GRID))


class Router:
    def __init__(self, path, extra_keepouts=()):
        self.board = pcbnew.LoadBoard(path)
        self.path = path
        bb = self.board.GetBoardEdgesBoundingBox()
        self.x0 = bb.GetLeft() / 1e6
        self.y0 = bb.GetTop() / 1e6
        self.W = bb.GetWidth() / 1e6
        self.H = bb.GetHeight() / 1e6
        self.nx = mm2g(self.W) + 1
        self.ny = mm2g(self.H) + 1
        # Belegung je Lage: Netz-Code des Kupfers, -1 = frei, -2 = verboten
        self.occ = {0: np.full((self.nx, self.ny), -1, dtype=np.int32),
                    1: np.full((self.nx, self.ny), -1, dtype=np.int32)}
        self._mark_border()
        for k in extra_keepouts:
            self._mark_rect(k, both=True)
        self._mark_pads()
        self.committed = {}   # netcode -> Liste von Board-Objekten (Tracks/Vias)
        self.edges_of = {}    # netcode -> Liste (name, a, b, w) fuer Reroute
        self.via_pos = {}     # netcode -> Liste (gx, gy) gesetzter Vias

    # ------------------------------------------------------------------
    def g2mm(self, gx, gy):
        return self.x0 + gx * GRID, self.y0 + gy * GRID

    def mm2grid(self, x, y):
        return mm2g(x - self.x0), mm2g(y - self.y0)

    def _mark_border(self, margin=0.8):
        m = mm2g(margin)
        for L in (0, 1):
            self.occ[L][:m, :] = -2
            self.occ[L][-m:, :] = -2
            self.occ[L][:, :m] = -2
            self.occ[L][:, -m:] = -2

    def _mark_rect(self, rect, both=False, net=-2, layer=0):
        x0, y0, x1, y1 = rect
        gx0, gy0 = self.mm2grid(x0, y0)
        gx1, gy1 = self.mm2grid(x1, y1)
        gx0, gy0 = max(gx0, 0), max(gy0, 0)
        gx1, gy1 = min(gx1, self.nx - 1), min(gy1, self.ny - 1)
        layers = (0, 1) if both else (layer,)
        for L in layers:
            self.occ[L][gx0:gx1 + 1, gy0:gy1 + 1] = net

    def _mark_pads(self):
        """Alle Pads als Kupfer ihres Netzes eintragen (inkl. Clearance)."""
        for fp in self.board.GetFootprints():
            for pad in fp.Pads():
                net = pad.GetNetCode()
                bb = pad.GetBoundingBox()
                infl = CLEARANCE
                x0 = bb.GetLeft() / 1e6 - infl
                y0 = bb.GetTop() / 1e6 - infl
                x1 = bb.GetRight() / 1e6 + infl
                y1 = bb.GetBottom() / 1e6 + infl
                on_f = pad.IsOnLayer(pcbnew.F_Cu)
                on_b = pad.IsOnLayer(pcbnew.B_Cu)
                code = net if net > 0 else -2
                if on_f and on_b:
                    self._mark_rect((x0, y0, x1, y1), both=True, net=code)
                elif on_f:
                    self._mark_rect((x0, y0, x1, y1), layer=0, net=code)
                elif on_b:
                    self._mark_rect((x0, y0, x1, y1), layer=1, net=code)

    # ------------------------------------------------------------------
    def _blocked(self, L, gx, gy, netcode, halfw_cells):
        """Kollisionstest: Umgebung (halbe Bahnbreite) muss frei/eigenes Netz sein."""
        h = halfw_cells
        x0, x1 = max(gx - h, 0), min(gx + h, self.nx - 1)
        y0, y1 = max(gy - h, 0), min(gy + h, self.ny - 1)
        win = self.occ[L][x0:x1 + 1, y0:y1 + 1]
        return np.any((win != -1) & (win != netcode))

    @staticmethod
    def _disk(r):
        y, x = np.ogrid[-r:r+1, -r:r+1]
        return (x*x + y*y) <= r*r + 0.1

    def _passable(self, netcode, halfw):
        """Boolsche Karten je Lage: True = Bahnmitte hier erlaubt."""
        disk = self._disk(halfw)
        out = {}
        for L in (0, 1):
            obst = (self.occ[L] != -1) & (self.occ[L] != netcode)
            out[L] = ~binary_dilation(obst, structure=disk)
        return out

    def route_edge(self, netcode, netname, a, b, width, max_expand=400000,
                   via_cost=VIA_COST):
        """A* von Pad a nach Pad b. a/b = (x_mm, y_mm, layerset)."""
        halfw = mm2g(width / 2 + CLEARANCE)
        pass_trk = self._passable(netcode, halfw)
        pass_via = self._passable(netcode, mm2g(VIA_D / 2 + CLEARANCE))
        sx, sy = self.mm2grid(a[0], a[1])
        tx, ty = self.mm2grid(b[0], b[1])
        sx = max(1, min(sx, self.nx - 2)); sy = max(1, min(sy, self.ny - 2))
        tx = max(1, min(tx, self.nx - 2)); ty = max(1, min(ty, self.ny - 2))
        s_layers = a[2]
        t_layers = b[2]

        def h(gx, gy):
            return abs(gx - tx) + abs(gy - ty)

        start_states = [(L, sx, sy) for L in s_layers]
        best = {}
        pq = []
        for st in start_states:
            best[st] = 0.0
            heapq.heappush(pq, (h(sx, sy), 0.0, st, None))
        parent = {}
        goal = None
        expansions = 0
        while pq:
            f, g, st, par = heapq.heappop(pq)
            if best.get(st, 1e18) < g - 1e-9:
                continue
            parent[st] = par
            L, gx, gy = st
            if (gx, gy) == (tx, ty) and L in t_layers:
                goal = st
                break
            expansions += 1
            if expansions > max_expand:
                return None
            # 4 Richtungen
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nxg, nyg = gx + dx, gy + dy
                if not (0 <= nxg < self.nx and 0 <= nyg < self.ny):
                    continue
                nst = (L, nxg, nyg)
                # Vorzugsrichtung: F.Cu horizontal (dx), B.Cu vertikal (dy)
                cost = 1.0 if ((L == 0 and dx) or (L == 1 and dy)) else WRONG_DIR_COST
                ng = g + cost
                if ng < best.get(nst, 1e18) - 1e-9:
                    if not pass_trk[L][nxg, nyg]:
                        continue
                    best[nst] = ng
                    heapq.heappush(pq, (ng + h(nxg, nyg), ng, nst, st))
            # Via
            oL = 1 - L
            nst = (oL, gx, gy)
            ng = g + via_cost
            if ng < best.get(nst, 1e18) - 1e-9:
                if pass_via[0][gx, gy] and pass_via[1][gx, gy]:
                    best[nst] = ng
                    heapq.heappush(pq, (ng + h(gx, gy), ng, nst, st))
        if goal is None:
            return None

        # Pfad rekonstruieren
        path = []
        st = goal
        while st is not None:
            path.append(st)
            st = parent.get(st)
        path.reverse()
        return path

    # ------------------------------------------------------------------
    def commit(self, netcode, path, width):
        """Pfad als Bahnen+Vias aufs Board schreiben und im Raster belegen."""
        net = self.board.FindNet(netcode)
        objs = self.committed.setdefault(netcode, [])
        layers = [pcbnew.F_Cu, pcbnew.B_Cu]
        halfw = mm2g(width / 2) + 1

        # in Segmente gleicher Lage und Richtung zusammenfassen
        segs = []
        i = 0
        while i < len(path) - 1:
            L, x, y = path[i]
            j = i + 1
            if path[j][0] != L:          # Via
                segs.append(("via", path[i], path[j]))
                i = j
                continue
            dx = path[j][1] - x
            dy = path[j][2] - y
            while (j + 1 < len(path) and path[j + 1][0] == L and
                   path[j + 1][1] - path[j][1] == dx and
                   path[j + 1][2] - path[j][2] == dy):
                j += 1
            segs.append(("track", L, path[i], path[j]))
            i = j

        for s in segs:
            if s[0] == "track":
                _, L, p0, p1 = s
                x0, y0 = self.g2mm(p0[1], p0[2])
                x1, y1 = self.g2mm(p1[1], p1[2])
                t = pcbnew.PCB_TRACK(self.board)
                t.SetStart(VECTOR2I(int(x0 * 1e6), int(y0 * 1e6)))
                t.SetEnd(VECTOR2I(int(x1 * 1e6), int(y1 * 1e6)))
                t.SetWidth(int(width * 1e6))
                t.SetLayer(layers[L])
                t.SetNet(net)
                self.board.Add(t)
                objs.append(t)
                # Raster belegen
                gx0, gx1 = sorted((p0[1], p1[1]))
                gy0, gy1 = sorted((p0[2], p1[2]))
                self.occ[L][max(gx0 - halfw, 0):gx1 + halfw + 1,
                            max(gy0 - halfw, 0):gy1 + halfw + 1] = netcode
            else:
                _, p0, p1 = s
                # Dedupe: kein zweites Via desselben Netzes im Umkreis 0.5mm
                near = [vp for vp in self.via_pos.get(netcode, [])
                        if abs(vp[0] - p0[1]) <= 2 and abs(vp[1] - p0[2]) <= 2]
                if near:
                    continue
                self.via_pos.setdefault(netcode, []).append((p0[1], p0[2]))
                x, y = self.g2mm(p0[1], p0[2])
                v = pcbnew.PCB_VIA(self.board)
                v.SetPosition(VECTOR2I(int(x * 1e6), int(y * 1e6)))
                v.SetDrill(int(VIA_DRILL * 1e6))
                v.SetWidth(int(VIA_D * 1e6))
                v.SetNet(net)
                self.board.Add(v)
                objs.append(v)
                hv = mm2g(VIA_D / 2) + 1
                for L in (0, 1):
                    self.occ[L][max(p0[1] - hv, 0):p0[1] + hv + 1,
                                max(p0[2] - hv, 0):p0[2] + hv + 1] = netcode

    # ------------------------------------------------------------------
    def pads_by_net(self):
        nets = {}
        for fp in self.board.GetFootprints():
            for pad in fp.Pads():
                code = pad.GetNetCode()
                name = pad.GetNetname()
                if code <= 0 or name in ZONE_NETS:
                    continue
                x = pad.GetPosition().x / 1e6
                y = pad.GetPosition().y / 1e6
                lay = []
                if pad.IsOnLayer(pcbnew.F_Cu):
                    lay.append(0)
                if pad.IsOnLayer(pcbnew.B_Cu):
                    lay.append(1)
                nets.setdefault((code, name), []).append((x, y, tuple(lay)))
        return nets

    @staticmethod
    def mst_edges(pads):
        """Prim-MST ueber die Padliste, gibt Kanten (a, b) zurueck."""
        if len(pads) < 2:
            return []
        used = [0]
        edges = []
        rest = list(range(1, len(pads)))
        while rest:
            bi, bj, bd = None, None, 1e18
            for i in used:
                for j in rest:
                    d = (abs(pads[i][0] - pads[j][0]) +
                         abs(pads[i][1] - pads[j][1]))
                    if d < bd:
                        bi, bj, bd = i, j, d
            edges.append((pads[bi], pads[bj]))
            used.append(bj)
            rest.remove(bj)
        return edges

    def run(self):
        nets = self.pads_by_net()
        # Reihenfolge: dicke Netze zuerst, dann kurze Signale
        items = []
        for (code, name), pads in nets.items():
            w = track_width(name)
            for a, b in self.mst_edges(pads):
                dist = abs(a[0] - b[0]) + abs(a[1] - b[1])
                items.append((-w, dist, code, name, a, b, w))
        # global kuerzeste zuerst (verbraucht am wenigsten Freiheit);
        # SEED-Umgebungsvariable randomisiert die Reihenfolge leicht, damit
        # ein Wiederholungslauf Verklemmungen aufloesen kann
        seed = int(os.environ.get("SEED", "0"))
        rng = random.Random(seed)
        items.sort(key=lambda t: t[1] + (rng.random() * 8 if seed else 0))

        for it in items:
            self.edges_of.setdefault(it[2], []).append((it[3], it[4], it[5], it[6]))

        failed = []
        t0 = time.time()
        for k, (_, dist, code, name, a, b, w) in enumerate(items):
            if not self._try_edge(code, name, a, b, w):
                failed.append((code, name, a, b, w))
            if (k + 1) % 25 == 0:
                print(f"  [{k+1}/{len(items)}] ... {time.time()-t0:.0f}s")

        # Rip-up-Runden: blockierende Netze entfernen, Problemkante zuerst
        for rnd in range(5):
            if not failed:
                break
            print(f"Rip-up-Runde {rnd+1}: {len(failed)} offene Kanten")
            still = []
            for code, name, a, b, w in failed:
                victims = self._blocking_nets(code, a, b, corridor=2.0 + 2.0*rnd)
                for v in victims:
                    self._rip(v)
                ok = self._try_edge(code, name, a, b, w)
                # Opfer neu routen; deren Fehlschlaege in die naechste Runde
                for v in victims:
                    for (vn, va, vb, vw) in self.edges_of.get(v, []):
                        if not self._try_edge(v, vn, va, vb, vw, quiet=True):
                            still.append((v, vn, va, vb, vw))
                if not ok:
                    still.append((code, name, a, b, w))
            # Duplikate raus
            failed = list({(c, n, a, b): (c, n, a, b, w)
                           for c, n, a, b, w in still}.values())

        # Offene Kanten der Opfer einsammeln (Konnektivitaet final pruefen
        # macht die DRC; hier nur Report)
        print(f"Routing fertig in {time.time()-t0:.0f}s, {len(failed)} Kanten offen")
        for f in failed:
            print("  OFFEN:", f[1], f"({f[2][0]:.0f},{f[2][1]:.0f})->({f[3][0]:.0f},{f[3][1]:.0f})")
        return failed

    def _try_edge(self, code, name, a, b, w, quiet=False):
        path = self.route_edge(code, name, a, b, w)
        if path is None and w > 0.3:
            nw = max(w * 0.6, 0.3)
            path = self.route_edge(code, name, a, b, nw)
            if path:
                w = nw
        if path is None:
            path = self.route_edge(code, name, a, b, w, via_cost=15,
                                   max_expand=900000)
        if path is None:
            if not quiet:
                print(f"  FEHLGESCHLAGEN {name}")
            return False
        self.commit(code, path, w)
        return True

    def _blocking_nets(self, code, a, b, corridor=3.0):
        """Netze mit Kupfer im Korridor zwischen a und b (ohne Zonen-Netze)."""
        gx0, gy0 = self.mm2grid(min(a[0], b[0]) - corridor, min(a[1], b[1]) - corridor)
        gx1, gy1 = self.mm2grid(max(a[0], b[0]) + corridor, max(a[1], b[1]) + corridor)
        gx0, gy0 = max(gx0, 0), max(gy0, 0)
        gx1, gy1 = min(gx1, self.nx - 1), min(gy1, self.ny - 1)
        found = set()
        for L in (0, 1):
            vals = np.unique(self.occ[L][gx0:gx1 + 1, gy0:gy1 + 1])
            for v in vals:
                if v > 0 and v != code and v in self.committed and self.committed[v]:
                    found.add(int(v))
        return found

    def _rip(self, netcode):
        """Alle committeten Bahnen/Vias eines Netzes entfernen, Raster neu aufbauen."""
        for obj in self.committed.get(netcode, []):
            self.board.Remove(obj)
        self.committed[netcode] = []
        self.via_pos[netcode] = []
        # Raster von Grund auf neu (Pads + verbliebene Bahnen)
        for L in (0, 1):
            self.occ[L][:, :] = -1
        self._mark_border()
        self._mark_pads()
        for nc, objs in self.committed.items():
            for obj in objs:
                bb = obj.GetBoundingBox()
                infl = CLEARANCE
                rect = (bb.GetLeft() / 1e6 - infl, bb.GetTop() / 1e6 - infl,
                        bb.GetRight() / 1e6 + infl, bb.GetBottom() / 1e6 + infl)
                if isinstance(obj, pcbnew.PCB_VIA):
                    self._mark_rect(rect, both=True, net=nc)
                else:
                    L = 0 if obj.GetLayer() == pcbnew.F_Cu else 1
                    self._mark_rect(rect, layer=L, net=nc)

    def add_gnd_stitching(self, pitch=12.0):
        """GND-Vias auf freie Stellen setzen (verbindet F/B-Zoneninseln)."""
        gnd = None
        for i in range(self.board.GetNetCount()):
            n = self.board.FindNet(i)
            if n and n.GetNetname() == "GND":
                gnd = n
        if gnd is None:
            return
        h = mm2g(VIA_D / 2 + 0.3)
        count = 0
        y = 2.0
        while y < self.H - 1:
            x = 2.0
            while x < self.W - 1:
                gx, gy = mm2g(x), mm2g(y)
                if 0 < gx < self.nx and 0 < gy < self.ny and \
                   not self._blocked(0, gx, gy, -99, h) and \
                   not self._blocked(1, gx, gy, -99, h):
                    v = pcbnew.PCB_VIA(self.board)
                    v.SetPosition(VECTOR2I(int((self.x0 + x) * 1e6),
                                           int((self.y0 + y) * 1e6)))
                    v.SetDrill(int(VIA_DRILL * 1e6))
                    v.SetWidth(int(VIA_D * 1e6))
                    v.SetNet(gnd)
                    self.board.Add(v)
                    for L in (0, 1):
                        self.occ[L][gx-2:gx+3, gy-2:gy+3] = gnd.GetNetCode()
                    count += 1
                x += pitch
            y += pitch
        # Zusatz: neben jedem NUR-SMD-GND-Pad ein Via (verhindert Inseln,
        # die nur an einem SMD-Pad haengen)
        for fp in self.board.GetFootprints():
            for p in fp.Pads():
                if p.GetNetname() != "GND" or p.HasHole():
                    continue
                px, py = p.GetPosition().x / 1e6, p.GetPosition().y / 1e6
                placed = False
                for r in (1.0, 1.4, 1.8, 2.4):
                    if placed:
                        break
                    for ang in range(0, 360, 45):
                        import math as _m
                        x = px + r * _m.cos(_m.radians(ang)) - self.x0
                        y = py + r * _m.sin(_m.radians(ang)) - self.y0
                        gx, gy = mm2g(x), mm2g(y)
                        if not (0 < gx < self.nx and 0 < gy < self.ny):
                            continue
                        if self._blocked(0, gx, gy, gnd.GetNetCode(), h) or \
                           self._blocked(1, gx, gy, gnd.GetNetCode(), h):
                            continue
                        v = pcbnew.PCB_VIA(self.board)
                        v.SetPosition(VECTOR2I(int((self.x0 + x) * 1e6),
                                               int((self.y0 + y) * 1e6)))
                        v.SetDrill(int(VIA_DRILL * 1e6))
                        v.SetWidth(int(VIA_D * 1e6))
                        v.SetNet(gnd)
                        self.board.Add(v)
                        for L in (0, 1):
                            self.occ[L][gx-2:gx+3, gy-2:gy+3] = gnd.GetNetCode()
                        count += 1
                        placed = True
                        break
        print(f"GND-Stitching: {count} Vias")
        # SMD-GND-Pads zusaetzlich per Bahn an den naechsten THT-GND-Anker
        # anbinden (macht die Masse unabhaengig von Zonen-Fragmenten)
        anchors = []
        for fp in self.board.GetFootprints():
            for p in fp.Pads():
                if p.GetNetname() == "GND" and p.HasHole():
                    anchors.append((p.GetPosition().x / 1e6,
                                    p.GetPosition().y / 1e6))
        code = gnd.GetNetCode()
        for fp in self.board.GetFootprints():
            for p in fp.Pads():
                if p.GetNetname() != "GND" or p.HasHole():
                    continue
                px, py = p.GetPosition().x / 1e6, p.GetPosition().y / 1e6
                anchors.sort(key=lambda a: abs(a[0]-px) + abs(a[1]-py))
                for a in anchors[:3]:
                    path = self.route_edge(code, "GND", (px, py, (0,)),
                                           (a[0], a[1], (0, 1)), 0.4)
                    if path:
                        self.commit(code, path, 0.4)
                        break

    def save(self):
        pcbnew.SaveBoard(self.path, self.board)
        board2 = pcbnew.LoadBoard(self.path)
        filler = pcbnew.ZONE_FILLER(board2)
        filler.Fill(board2.Zones())
        pcbnew.SaveBoard(self.path, board2)


if __name__ == "__main__":
    board_path = sys.argv[1]
    keepouts = []
    if "--keepout" in sys.argv:
        vals = sys.argv[sys.argv.index("--keepout") + 1]
        keepouts.append(tuple(float(v) for v in vals.split(",")))
    r = Router(board_path, keepouts)
    r.run()
    r.add_gnd_stitching(float(os.environ.get("STITCH", "12")))
    r.save()
    print("gespeichert:", board_path)
