"""Gezielter Finisher fuer die letzten offenen Verbindungen des Main-Boards.

Statt global neu zu routen, wird jede noch offene Ratsnest-Verbindung EINZELN
mit hohem Aufwand geroutet — und wenn der direkte Weg blockiert ist, werden
die wenigen kreuzenden Fremd-Segmente im Korridor kurz entfernt (lokales
Rip-up), die Zielverbindung gezogen und die entfernten Segmente danach als
Punkt-zu-Punkt-Bahnen wieder verlegt. Das entspricht dem manuellen Nachziehen
im interaktiven Router, nur im Code.

Die offenen Paare kommen aus dem DRC-Report (unconnected_items).

Aufruf:
  RGRID=0.1 TIGHT=1 VIA=small NLAYERS=3 CLEAR=0.15 \
    python3 finish_route.py main-board/main-board.kicad_pcb --keepout 64,76,100,80
"""
import os
import re
import sys
import pcbnew

import autoroute as ar


def unconnected_pairs(board):
    """(netname, x0,y0,x1,y1) je offener Ratsnest-Kante aus dem DRC-Report."""
    tmp = "/tmp/_finish_drc.txt"
    board.BuildConnectivity()
    pcbnew.WriteDRCReport(board, tmp, pcbnew.EDA_UNITS_MILLIMETRES, True)
    txt = open(tmp).read()
    out = []
    for bl in re.split(r'\n(?=\[)', txt):
        if bl.startswith('[unconnected_items]'):
            pts = re.findall(r'@\(([\d.]+) mm, ([\d.]+) mm\)[^\n]*?\[([A-Z0-9_]+)\]', bl)
            if len(pts) >= 2:
                (x0, y0, n0), (x1, y1, _n1) = pts[0], pts[1]
                x0, y0, x1, y1 = map(float, (x0, y0, x1, y1))
                if (x0, y0) == (0.0, 0.0) or (x1, y1) == (0.0, 0.0):
                    continue           # degenerierte GND-Anker ignorieren
                out.append((n0, x0, y0, x1, y1))
    return out


class Finisher(ar.Router):
    def __init__(self, path, keepouts):
        super().__init__(path, keepouts)
        self.mark_existing()
        self.name2code = {}
        for i in range(self.board.GetNetCount()):
            n = self.board.FindNet(i)
            if n:
                self.name2code[n.GetNetname()] = i

    # ----- Hilfen ------------------------------------------------------
    def _foreign_tracks_in_corridor(self, x0, y0, x1, y1, code, m=1.2):
        """Fremd-Bahnsegmente (nicht via), deren Bounding-Box den Korridor
        zwischen den Endpunkten schneidet — Kandidaten fuers Rip-up."""
        lo_x, hi_x = min(x0, x1) - m, max(x0, x1) + m
        lo_y, hi_y = min(y0, y1) - m, max(y0, y1) + m
        vic = []
        for t in self.board.GetTracks():
            if isinstance(t, pcbnew.PCB_VIA):
                continue
            if t.GetNetCode() == code or t.GetNetCode() <= 0:
                continue
            bb = t.GetBoundingBox()
            tx0, ty0 = bb.GetLeft()/1e6, bb.GetTop()/1e6
            tx1, ty1 = bb.GetRight()/1e6, bb.GetBottom()/1e6
            if tx0 <= hi_x and tx1 >= lo_x and ty0 <= hi_y and ty1 >= lo_y:
                vic.append(t)
        return vic

    def _try(self, code, name, x0, y0, x1, y1, budget):
        w = ar.track_width(name)
        a = (x0, y0, self.all_layers)
        b = (x1, y1, self.all_layers)
        for vc in (12, 6):     # normale + billige Vias
            p = self.route_edge(code, name, a, b, w, max_expand=budget,
                                via_cost=vc, clearance=0.127)
            if p:
                self.commit(code, p, w)
                return True
        return False

    def _unconnected(self):
        self.board.BuildConnectivity()
        try:
            return self.board.GetConnectivity().GetUnconnectedCount(False)
        except TypeError:
            return self.board.GetConnectivity().GetUnconnectedCount()

    def _reload(self):
        """Board vom Snapshot neu laden und Router-Zustand neu aufbauen —
        einfachster 100%-korrekter Rollback."""
        self.board = pcbnew.LoadBoard(self.snap)
        for L in range(self.nL):
            self.occ[L][:, :] = -1
            self.core[L][:, :] = -1
        self._mark_border()
        for k in self.extra_keepouts:
            self._mark_rect(k, both=True)
        self._mark_pads()
        self.mark_existing()
        self.committed = {}
        self.name2code = {}
        for i in range(self.board.GetNetCount()):
            n = self.board.FindNet(i)
            if n:
                self.name2code[n.GetNetname()] = i

    def finish(self, pairs):
        import pcbnew as _pn
        budget = int(os.environ.get("FBUDGET", "180000"))
        done = 0
        self.snap = self.path.replace(".kicad_pcb", ".snap.kicad_pcb")
        _pn.SaveBoard(self.snap, self.board)     # Ausgangs-Snapshot
        base = self._unconnected()
        # kurze Verbindungen zuerst (billig, entlasten den Bereich)
        pairs = sorted(pairs, key=lambda p: abs(p[3]-p[1])+abs(p[4]-p[2]))
        for idx, (name, x0, y0, x1, y1) in enumerate(pairs):
            code = self.name2code.get(name)
            if code is None:
                continue
            # (1) direkter Versuch (fuegt nur eigenes Kupfer hinzu)
            if self._try(code, name, x0, y0, x1, y1, budget):
                if self._unconnected() < base:
                    base = self._unconnected(); done += 1
                    _pn.SaveBoard(self.snap, self.board); continue
                self._reload()                    # nichts gebracht -> zurueck
            # (2) lokales Rip-up + globale Verifikation
            vic = self._foreign_tracks_in_corridor(x0, y0, x1, y1, code)
            if not vic or len(vic) > 25:
                continue
            saved = [(t.GetNetCode(), t.GetStart(), t.GetEnd(),
                      t.GetWidth(), t.GetLayer()) for t in vic]
            for t in vic:
                self.board.Remove(t)
            self.mark_existing()
            ok = self._try(code, name, x0, y0, x1, y1, budget)
            if ok:
                for nc, s, e, wdt, lay in saved:
                    L = self.lid2idx.get(lay)
                    if L is None:
                        continue
                    p = self.route_edge(nc,
                                        self.board.FindNet(nc).GetNetname(),
                                        (s.x/1e6, s.y/1e6, (L,)),
                                        (e.x/1e6, e.y/1e6, self.all_layers),
                                        wdt/1e6, max_expand=budget, via_cost=6)
                    if p:
                        self.commit(nc, p, wdt/1e6)
            if ok and self._unconnected() < base:
                base = self._unconnected(); done += 1
                _pn.SaveBoard(self.snap, self.board)
            else:
                self._reload()                    # Rollback auf letzten Stand
        # sicherstellen, dass wir auf dem letzten guten Snapshot arbeiten
        self._reload()
        print(f"Finisher: {done} Verbindungen zusaetzlich geschlossen, "
              f"offene Pad-Enden jetzt {self._unconnected()} (vorher schon "
              f"gezaehlt)")
        return done


if __name__ == "__main__":
    path = sys.argv[1]
    keepouts = []
    if "--keepout" in sys.argv:
        keepouts.append(tuple(float(v) for v in
                              sys.argv[sys.argv.index("--keepout")+1].split(",")))
    b0 = pcbnew.LoadBoard(path)
    pairs = unconnected_pairs(b0)
    print(f"Offene Verbindungen laut DRC: {len(pairs)}")
    f = Finisher(path, keepouts)
    f.finish(pairs)
    f.add_gnd_stitching(float(os.environ.get("STITCH", "8")))
    f.save()
    print("gespeichert:", path)
