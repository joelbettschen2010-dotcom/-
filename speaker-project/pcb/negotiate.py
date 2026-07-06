"""
PathFinder-Verhandlungsrouting fuer das Main-Board (2 Lagen).

Der Greedy-Router (autoroute.py) plateaut bei ~75 offenen Kanten, weil
frueh committete Bahnen die Korridore der spaeten Kanten verbrauchen.
PathFinder loest das klassisch: ALLE Kanten werden in jeder Runde neu
geroutet; Zellen, die mehrere Netze gleichzeitig wollen, bekommen einen
wachsenden Historien-Aufschlag. Nach einigen Runden weichen die flexiblen
Netze aus und die Ueberlastung konvergiert gegen 0 — oder sie tut es
nicht, dann fehlt physikalisch die Kapazitaet (ehrliches Resultat).

Aufruf:
  ROUNDS=16 CLEAR=0.15 python3 negotiate.py main-board/main-board.kicad_pcb \
      --keepout 64,76,100,80

Ergebnis wird nur gespeichert, wenn alle Kanten geroutet und ueberlappungs-
frei sind (sonst Report der Rest-Ueberlastung).
"""

import heapq
import os
import sys
import time

import numpy as np

import autoroute as ar


class Negotiator:
    def __init__(self, path, keepouts=()):
        # Router nur fuer Raster/Pads/Netzlisten wiederverwenden —
        # es wird nichts committet, occ enthaelt nur Pads/Rand/Keepouts.
        self.r = ar.Router(path, extra_keepouts=keepouts)
        self.nx, self.ny = self.r.nx, self.r.ny
        self.nL = self.r.nL
        self.hist = {L: np.zeros((self.nx, self.ny), dtype=np.float32)
                     for L in range(self.nL)}
        # Runden-Belegung: STAMP = physisches Kupfer, CLAIM = Kupfer +
        # 1-Zellen-Clearance-Ring. Konflikt = mein Stamp auf fremdem Claim
        # (entspricht Mittenabstand < 0.5mm bei 0.3er-Bahnen — Regel 0.45).
        self.stamp_owner = {L: np.zeros((self.nx, self.ny), dtype=np.int32)
                            for L in range(self.nL)}
        self.claim = {L: np.zeros((self.nx, self.ny), dtype=np.int32)
                      for L in range(self.nL)}
        self.multi = {L: np.zeros((self.nx, self.ny), dtype=bool)
                      for L in range(self.nL)}
        self._pass_cache = {}

    # -- statische Passierbarkeit (nur Pads/Rand als harte Hindernisse) ----
    def passable(self, netcode, halfw):
        key = (netcode, round(halfw, 2))
        if key not in self._pass_cache:
            self._pass_cache[key] = self.r._passable(netcode, halfw)
            for L in range(self.nL):
                self._pass_cache[key][L] |= (self.r.core[L] == netcode)
        return self._pass_cache[key]

    def stamp_cells(self, path, width):
        """Physische Kupferzellen des Pfads (Zellzentrum in der Bahnbreite)."""
        hw = int(width / 2 / ar.GRID + 1e-9)
        hv = int(ar.VIA_D / 2 / ar.GRID + 1e-9)
        cells = set()
        for i, (L, x, y) in enumerate(path):
            if i and path[i - 1][0] != L:      # Via: alle Lagen
                for LL in range(self.nL):
                    for dx in range(-hv, hv + 1):
                        for dy in range(-hv, hv + 1):
                            cells.add((LL, x + dx, y + dy))
            for dx in range(-hw, hw + 1):
                for dy in range(-hw, hw + 1):
                    cells.add((L, x + dx, y + dy))
        return {(L, x, y) for (L, x, y) in cells
                if 0 <= x < self.nx and 0 <= y < self.ny}

    def apply_stamps(self, code, stamps):
        """Stamps + Claim-Ring eintragen, Konflikte markieren."""
        so, cl, mu = self.stamp_owner, self.claim, self.multi
        for (L, x, y) in stamps:
            o = cl[L][x, y]
            if o and o != code:
                mu[L][x, y] = True
            s = so[L][x, y]
            if s == 0:
                so[L][x, y] = code
            elif s != code:
                mu[L][x, y] = True
        for (L, x, y) in stamps:
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    xx, yy = x + dx, y + dy
                    if not (0 <= xx < self.nx and 0 <= yy < self.ny):
                        continue
                    s = so[L][xx, yy]
                    if s and s != code:
                        mu[L][xx, yy] = True
                    if cl[L][xx, yy] == 0:
                        cl[L][xx, yy] = code

    def route_edge(self, netcode, a, b, width, extra_goals=None,
                   max_expand=250000, via_cost=14):
        """A* wie autoroute, aber mit Congestion-Kosten statt harter Sperre
        gegen fremde Bahnen (Pads bleiben hart)."""
        halfw = (width / 2 + ar.CLEARANCE) / ar.GRID
        p_trk = self.passable(netcode, halfw)
        p_via = self.passable(netcode, (ar.VIA_D / 2 + ar.CLEARANCE) / ar.GRID)
        R = self.r
        sx, sy = R.mm2grid(b[0], b[1])
        tx, ty = R.mm2grid(a[0], a[1])
        sx = max(1, min(sx, self.nx - 2)); sy = max(1, min(sy, self.ny - 2))
        tx = max(1, min(tx, self.nx - 2)); ty = max(1, min(ty, self.ny - 2))

        hist, claim, multi = self.hist, self.claim, self.multi

        def congestion(L, x, y):
            c = hist[L][x, y]
            o = claim[L][x, y]
            if o and o != netcode:
                c += P_NOW * (2.0 if multi[L][x, y] else 1.0)
            return c

        def h(x, y):
            return 0.92 * (abs(x - tx) + abs(y - ty))

        start = [(L, sx, sy) for L in b[2]]
        best = {}
        pq = []
        for st in start:
            best[st] = 0.0
            heapq.heappush(pq, (h(sx, sy), 0.0, st, None))
        parent = {}
        goal = None
        exp = 0
        while pq:
            f, g, st, par = heapq.heappop(pq)
            if best.get(st, 1e18) < g - 1e-9:
                continue
            parent[st] = par
            L, gx, gy = st
            if ((gx, gy) == (tx, ty) and L in a[2]) or \
               (extra_goals and st in extra_goals):
                goal = st
                break
            exp += 1
            if exp > max_expand:
                return None
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1),
                           (1, 1), (1, -1), (-1, 1), (-1, -1)):
                nxg, nyg = gx + dx, gy + dy
                if not (0 <= nxg < self.nx and 0 <= nyg < self.ny):
                    continue
                if not p_trk[L][nxg, nyg]:
                    continue
                if dx and dy and not (p_trk[L][gx + dx, gy] and
                                      p_trk[L][gx, gy + dy]):
                    continue
                if dx and dy:
                    base = 1.414 * (1.0 + ar.WRONG_DIR_COST) / 2.0
                else:
                    good = (self.r.pref_h[L] and dx) or \
                           (not self.r.pref_h[L] and dy)
                    base = 1.0 if good else ar.WRONG_DIR_COST
                ng = g + base + congestion(L, nxg, nyg)
                nst = (L, nxg, nyg)
                if ng < best.get(nst, 1e18) - 1e-9:
                    best[nst] = ng
                    heapq.heappush(pq, (ng + h(nxg, nyg), ng, nst, st))
            # Durchgangs-Via: alle Lagen frei -> Wechsel auf jede andere Lage
            if all(p_via[LL][gx, gy] for LL in range(self.nL)):
                for oL in range(self.nL):
                    if oL == L:
                        continue
                    ng = g + via_cost * abs(oL - L) + congestion(oL, gx, gy)
                    nst = (oL, gx, gy)
                    if ng < best.get(nst, 1e18) - 1e-9:
                        best[nst] = ng
                        heapq.heappush(pq, (ng + h(gx, gy), ng, nst, st))
        if goal is None:
            return None
        path = []
        st = goal
        while st is not None:
            path.append(st)
            st = parent.get(st)
        path.reverse()
        return path

    # ------------------------------------------------------------------
    def run(self, rounds):
        R = self.r
        nets = R.pads_by_net()
        edges = []           # (code, name, a, b, w, idx)
        for (code, name), pads in nets.items():
            w = ar.track_width(name)
            R.net_pads[code] = pads
            for i, p in enumerate(pads):
                gx, gy = R.mm2grid(p[0], p[1])
                R.pad_idx[(code, gx, gy)] = i
            for a, b in R.mst_edges(pads):
                edges.append([code, name, a, b, w])
        edges.sort(key=lambda e: abs(e[2][0] - e[3][0]) + abs(e[2][1] - e[3][1]))
        print(f"{len(edges)} Kanten, {rounds} Verhandlungsrunden")

        routes = [None] * len(edges)     # idx -> (path, stampcells)
        for rnd in range(rounds):
            t0 = time.time()
            # Runde beginnt leer
            for L in range(self.nL):
                self.stamp_owner[L][:, :] = 0
                self.claim[L][:, :] = 0
                self.multi[L][:, :] = False
            # Cluster je Netz zuruecksetzen
            uf = {code: list(range(len(R.net_pads[code])))
                  for (code, name) in nets}

            def find(code, i):
                p = uf[code]
                while p[i] != i:
                    p[i] = p[p[i]]
                    i = p[i]
                return i

            cells_of = {code: {} for (code, name) in nets}
            failed = 0
            for idx, (code, name, a, b, w) in enumerate(edges):
                ia = R.pad_idx.get((code,) + R.mm2grid(a[0], a[1]))
                ib = R.pad_idx.get((code,) + R.mm2grid(b[0], b[1]))
                goals = None
                if ia is not None and ib is not None:
                    if find(code, ia) == find(code, ib):
                        routes[idx] = ([], set())
                        continue
                    ra = find(code, ia)
                    goals = {st for st, rep in cells_of[code].items()
                             if find(code, rep) == ra} or None
                path = self.route_edge(code, a, b, w, extra_goals=goals)
                if path is None:
                    routes[idx] = None
                    failed += 1
                    continue
                stamps = self.stamp_cells(path, w)
                routes[idx] = (path, stamps)
                self.apply_stamps(code, stamps)
                if ia is not None:
                    rep = find(code, ia)
                    for st in path:
                        cells_of[code][st] = rep
                    if ib is not None:
                        uf[code][find(code, ib)] = rep

            over = int(sum(self.multi[L].sum() for L in range(self.nL)))
            print(f"Runde {rnd+1}: {failed} ungeroutete Kanten, "
                  f"{over} ueberlastete Zellen, {time.time()-t0:.0f}s")
            if failed == 0 and over == 0:
                print("KONVERGIERT!")
                return edges, routes, True
            # Historie erhoehen, wo mehrere Netze dieselbe Zelle wollen
            for L in range(self.nL):
                self.hist[L][self.multi[L]] += P_HIST
        return edges, routes, False

    # ------------------------------------------------------------------
    def commit_all(self, edges, routes):
        """Konfliktfreie Loesung als echte Bahnen/Vias aufs Board."""
        R = self.r
        for idx, (code, name, a, b, w) in enumerate(edges):
            pr = routes[idx]
            if pr is None or not pr[0]:
                continue
            R.commit(code, pr[0], w)
        R.add_gnd_stitching(float(os.environ.get("STITCH", "10")))
        R.save()   # fuellt Zonen nach dem Routen neu (Pours umfliessen Bahnen)
        print("gespeichert:", R.path)


P_NOW = float(os.environ.get("PNOW", "6.0"))    # Sofort-Strafe fremdbelegte Zelle
P_HIST = float(os.environ.get("PHIST", "2.0"))  # Historien-Inkrement je Runde


def main():
    path = sys.argv[1]
    keepouts = []
    if "--keepout" in sys.argv:
        i = sys.argv.index("--keepout")
        keepouts.append(tuple(float(v) for v in sys.argv[i + 1].split(",")))
    n = Negotiator(path, keepouts)
    rounds = int(os.environ.get("ROUNDS", "16"))
    edges, routes, ok = n.run(rounds)
    if ok:
        n.commit_all(edges, routes)
    else:
        print("NICHT konvergiert — Board wird NICHT gespeichert.")
        sys.exit(2)


if __name__ == "__main__":
    main()
