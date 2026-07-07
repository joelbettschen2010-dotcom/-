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

GRID = float(os.environ.get("RGRID", "0.25"))   # mm Rasterweite

# Routing-Lagen. NLAYERS=2 -> F.Cu/B.Cu (Button-Board). NLAYERS=3 ->
# F.Cu/In2.Cu/B.Cu als Signallagen, In1.Cu bleibt eine durchgehende
# GND-Plane (nicht geroutet, Antipads beim Zonenfuellen). NLAYERS=4 ->
# alle vier als Signallagen.
def _layer_ids(n):
    if n <= 2:
        return [pcbnew.F_Cu, pcbnew.B_Cu]
    if n == 3:
        return [pcbnew.F_Cu, pcbnew.In2_Cu, pcbnew.B_Cu]
    return [pcbnew.F_Cu, pcbnew.In1_Cu, pcbnew.In2_Cu, pcbnew.B_Cu]
# Design-Regel-Clearance; per ENV CLEAR uebersteuerbar (Main-Board: 0.15 —
# JLCPCB-faehig und noetig, damit die 1.1-mm-Packluecken passierbar sind)
CLEARANCE = float(os.environ.get("CLEAR", "0.20"))
# Via-Groesse per ENV: VIA=small -> 0.45/0.2mm (JLCPCB-4-Lagen-Minimum,
# passt in 0.5mm-Pitch-Fanout von QFP/QFN); Default 0.7/0.4 fuer 2-Lagen.
if os.environ.get("VIA", "std") == "small":
    VIA_D, VIA_DRILL = 0.45, 0.2
else:
    VIA_D, VIA_DRILL = 0.7, 0.4
VIA_COST = int(os.environ.get("VIACOST", "40"))  # Rasterschritte Strafkosten pro Via
# A*-Suchbudget skaliert mit der Rasterfeinheit (feiner = mehr Zellen pro Weg)
EXPAND_SCALE = (0.25 / GRID) ** 2
WRONG_DIR_COST = 1.6 # Kostenfaktor gegen die Vorzugsrichtung
# Netze, die von Zonen/Planes getragen werden (nicht als Bahnen geroutet).
# PVDD (30 Pads) liegt als Zone auf B.Cu. Optional kann 3V3 ueber
# NLAYERS-Stackup + POWER_PLANE_NETS ebenfalls auf eine Plane; Messungen
# zeigen aber: eine 3. SIGNAL-Lage (F/In2/B) schlaegt eine 3V3-Plane.
# Nur GND (In1-Plane, via-in-pad) und AGND (Analog-Insel) werden rein von
# Flaechen getragen. PVDD wird als Bahn GEROUTET (ROUTEPVDD, Default an) —
# so werden auch die Entkoppel-Kondensatoren erreicht, die der Packer weit
# weg von den Amps abgelegt hat; die B.Cu-PVDD-Zone bleibt als Zusatzkupfer.
ZONE_NETS = ({"GND", "AGND"} if os.environ.get("ROUTEPVDD", "1") == "1"
             else {"GND", "AGND", "PVDD"})
# Kompensation (in Zellen) fuer Bahnen, deren Kupfer beim Committen nur nahe
# der Mittellinie im Raster landet: jede Bahn mit Halbbreite < ~1 Zelle
# (Breite < 2*GRID = 0.5mm) markiert nur die Mittelzelle. THIN_COMP deckt die
# groesste so unter-markierte Halbbreite (knapp unter GRID) ab, damit der
# Kantenabstand auch gegen 0.3/0.4mm-Fremdbahnen >= Clearance bleibt.
THIN_COMP = GRID / GRID   # = 1.0 Zelle (0.25mm)
# Bewegungsrichtungen: orthogonal immer, Diagonalen nur bei DIAG=1
_ORTHO = ((1, 0), (-1, 0), (0, 1), (0, -1))
_DIAG = ((1, 1), (1, -1), (-1, 1), (-1, -1))
DIRECTIONS = _ORTHO + _DIAG if os.environ.get("DIAG", "1") == "1" else _ORTHO
# Netzname -> Plane-Lage: jedes Pad dieses Netzes bekommt eine clearance-
# gepruefte Durchkontaktierung zur Zone/Plane. MAINPLANES=1 -> PVDD auf B.Cu
# (verbindet die 30 PVDD-SMD-Pads mit dem B.Cu-PVDD-Polygon).
POWER_PLANE_NETS = ({"PVDD": pcbnew.B_Cu}
                    if os.environ.get("MAINPLANES") == "1" else {})

# Netzklassen -> Bahnbreite [mm]. TIGHT=1 (feines Raster + kleine Vias):
# schmalere Signalbahnen fuer die Fine-Pitch-Ausbrueche (QFP/QFN); JLCPCB
# 4-Lagen koennen 0.09mm, 0.15mm ist sicher. Strom-Netze bleiben breit genug.
_TIGHT = os.environ.get("TIGHT", "0") == "1"
def track_width(net):
    n = net.upper()
    if n.startswith(("VBAT",)):
        return 1.2 if _TIGHT else 2.0
    if n.startswith(("FR_", "SUB_", "U6_OUT", "U7_OUT", "SUB_OUT")):
        return 0.6 if _TIGHT else 1.0
    if n in ("5V", "3V3", "3V3A", "3V3_BT", "SW5V", "PWR_CTL", "DVDD_1V8",
             "GVDD1", "GVDD2", "VBUS", "LED_VDD"):
        return 0.3 if _TIGHT else 0.5
    return 0.15 if _TIGHT else 0.3


def mm2g(v):
    return int(round(v / GRID))


class Router:
    def __init__(self, path, extra_keepouts=()):
        self.board = pcbnew.LoadBoard(path)
        self.path = path
        self.nL = int(os.environ.get("NLAYERS", "2"))
        self.layer_ids = _layer_ids(self.nL)
        self.nL = len(self.layer_ids)
        self.lid2idx = {lid: i for i, lid in enumerate(self.layer_ids)}
        self.all_layers = tuple(range(self.nL))
        # Vorzugsrichtung je interner Lage: H, V, H, V ... (0 = horizontal)
        self.pref_h = [(i % 2 == 0) for i in range(self.nL)]
        bb = self.board.GetBoardEdgesBoundingBox()
        self.x0 = bb.GetLeft() / 1e6
        self.y0 = bb.GetTop() / 1e6
        self.W = bb.GetWidth() / 1e6
        self.H = bb.GetHeight() / 1e6
        self.nx = mm2g(self.W) + 1
        self.ny = mm2g(self.H) + 1
        # Belegung je Lage: Netz-Code des Kupfers, -1 = frei, -2 = verboten
        self.occ = {L: np.full((self.nx, self.ny), -1, dtype=np.int32)
                    for L in range(self.nL)}
        # Pad-KERNE ohne Clearance-Inflation: eigene Kernzellen sind fuer das
        # eigene Netz immer passierbar (Fine-Pitch-Ausfahrt entlang des Pads)
        self.core = {L: np.full((self.nx, self.ny), -1, dtype=np.int32)
                     for L in range(self.nL)}
        self._mark_border()
        self.extra_keepouts = tuple(extra_keepouts)
        for k in self.extra_keepouts:
            self._mark_rect(k, both=True)
        self._mark_pads()
        self.committed = {}   # netcode -> Liste von Board-Objekten (Tracks/Vias)
        self.edges_of = {}    # netcode -> Liste (name, a, b, w) fuer Reroute
        self.via_pos = {}     # netcode -> Liste (gx, gy) gesetzter Vias
        # Netzbaum-Verwaltung: Union-Find ueber Pad-Indizes + verlegte Zellen
        # je Cluster (fuer Multi-Goal-Routing an bestehendes eigenes Kupfer)
        self.net_pads = {}    # netcode -> Liste Pads
        self.pad_idx = {}     # (netcode, gx, gy) -> Index in net_pads
        self.uf = {}          # netcode -> Parent-Liste
        self.cells = {}       # netcode -> dict {(L,gx,gy): pad_rep_idx}

    def _find(self, code, i):
        p = self.uf[code]
        while p[i] != i:
            p[i] = p[p[i]]
            i = p[i]
        return i

    def _union(self, code, i, j):
        self.uf[code][self._find(code, i)] = self._find(code, j)

    # ------------------------------------------------------------------
    def g2mm(self, gx, gy):
        return self.x0 + gx * GRID, self.y0 + gy * GRID

    def mm2grid(self, x, y):
        return mm2g(x - self.x0), mm2g(y - self.y0)

    def _mark_border(self, margin=0.8):
        m = mm2g(margin)
        for L in range(self.nL):
            self.occ[L][:m, :] = -2
            self.occ[L][-m:, :] = -2
            self.occ[L][:, :m] = -2
            self.occ[L][:, -m:] = -2

    def _mark_rect(self, rect, both=False, net=-2, layer=0, layers=None):
        x0, y0, x1, y1 = rect
        gx0, gy0 = self.mm2grid(x0, y0)
        gx1, gy1 = self.mm2grid(x1, y1)
        gx0, gy0 = max(gx0, 0), max(gy0, 0)
        gx1, gy1 = min(gx1, self.nx - 1), min(gy1, self.ny - 1)
        if layers is None:
            layers = tuple(range(self.nL)) if both else (layer,)
        for L in layers:
            self.occ[L][gx0:gx1 + 1, gy0:gy1 + 1] = net

    def _mark_pads(self):
        """Alle Pads als Kupfer ihres Netzes eintragen (inkl. Clearance)."""
        for fp in self.board.GetFootprints():
            for pad in fp.Pads():
                net = pad.GetNetCode()
                bb = pad.GetBoundingBox()
                # Pad auf wahre Kupferausdehnung markieren (keine Clearance-
                # Inflation mehr — die kommt einheitlich ueber die Dilatation
                # beim Routen, sonst wuerde die Clearance doppelt gezaehlt).
                infl = 0.0
                x0 = bb.GetLeft() / 1e6 - infl
                y0 = bb.GetTop() / 1e6 - infl
                x1 = bb.GetRight() / 1e6 + infl
                y1 = bb.GetBottom() / 1e6 + infl
                code = net if net > 0 else -2
                # Auf welchen ROUTING-Lagen liegt das Pad? (THT-Pads liegen
                # auf allen; SMD nur auf F oder B.)
                on = tuple(L for L, lid in enumerate(self.layer_ids)
                           if pad.IsOnLayer(lid))
                if on:
                    self._mark_rect((x0, y0, x1, y1), net=code, layers=on)
                # Kern (ohne Inflation) fuer die Eigen-Netz-Freischaltung
                if code > 0 and on:
                    cx0, cy0 = bb.GetLeft() / 1e6, bb.GetTop() / 1e6
                    cx1, cy1 = bb.GetRight() / 1e6, bb.GetBottom() / 1e6
                    gx0, gy0 = self.mm2grid(cx0, cy0)
                    gx1, gy1 = self.mm2grid(cx1, cy1)
                    gx0, gy0 = max(gx0, 0), max(gy0, 0)
                    gx1 = min(gx1, self.nx - 1); gy1 = min(gy1, self.ny - 1)
                    for L in on:
                        self.core[L][gx0:gx1 + 1, gy0:gy1 + 1] = code

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
        rr = int(np.ceil(r))
        y, x = np.ogrid[-rr:rr+1, -rr:rr+1]
        return (x*x + y*y) <= r*r + 0.1

    def _passable(self, netcode, halfw):
        """Boolsche Karten je Lage: True = Bahnmitte hier erlaubt.
        halfw = (Bahnbreite/2 + Clearance)/GRID in Zellen (float). Fremdes
        Kupfer ist auf seine wahre Breite markiert; die Dilatation haelt die
        eigene Bahnmitte um genau halfw Zellen fern -> Kantenabstand >=
        Clearance. Kein kuenstlicher 1.45-Boden mehr (der erzeugte 0.06mm-
        Verstoesse); die reale Disk-Groesse deckt auch Diagonalnachbarn ab."""
        disk = self._disk(float(halfw))
        out = {}
        for L in range(self.nL):
            obst = (self.occ[L] != -1) & (self.occ[L] != netcode)
            out[L] = ~binary_dilation(obst, structure=disk)
        return out

    def route_edge(self, netcode, netname, a, b, width, max_expand=None,
                   via_cost=VIA_COST, extra_goals=None, clearance=None):
        """A* von Pad a nach Pad b. a/b = (x_mm, y_mm, layerset).
        extra_goals: Menge {(L,gx,gy)} bereits verlegten EIGENEN Kupfers —
        die Suche darf dort enden (Anschluss an den bestehenden Netzbaum).
        clearance: Override (Notfallstufe 0.127 = JLCPCB-Minimum 5 mil)."""
        cl = CLEARANCE if clearance is None else clearance
        if max_expand is None:
            max_expand = int(80000 * EXPAND_SCALE)
        # Dilatationsradius = eigene Halbbreite + Clearance + Kompensation fuer
        # duenne Fremdbahnen, die nur auf der Mittellinie markiert sind
        # (THIN_COMP). Garantiert Kantenabstand >= Clearance ohne den alten
        # 1.45-Boden, der 0.06mm-Verstoesse erzeugte.
        halfw = (width / 2 + cl) / GRID + THIN_COMP
        pass_trk = self._passable(netcode, halfw)
        pass_via = self._passable(netcode, (VIA_D / 2 + cl) / GRID + THIN_COMP)
        # Eigene Pad-Kerne freischalten: die Bahn darf ueber eigenem Kupfer
        # laufen (Fine-Pitch: einzige Ausfahrt ist die Pad-Laengsachse)
        for L in range(self.nL):
            pass_trk[L] = pass_trk[L] | (self.core[L] == netcode)
        sx, sy = self.mm2grid(a[0], a[1])
        tx, ty = self.mm2grid(b[0], b[1])
        sx = max(1, min(sx, self.nx - 2)); sy = max(1, min(sy, self.ny - 2))
        tx = max(1, min(tx, self.nx - 2)); ty = max(1, min(ty, self.ny - 2))
        for L in a[2]:
            pass_trk[L][sx, sy] = True
        for L in b[2]:
            pass_trk[L][tx, ty] = True
        s_layers = a[2]
        t_layers = b[2]

        def h(gx, gy):
            # 0.92: Diagonalschritt kostet 1.84 pro 2 Manhattan-Einheiten —
            # Heuristik muss zulaessig bleiben
            return 0.92 * (abs(gx - tx) + abs(gy - ty))

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
            if ((gx, gy) == (tx, ty) and L in t_layers) or \
               (extra_goals and st in extra_goals):
                goal = st
                break
            expansions += 1
            if expansions > max_expand:
                return None
            # 4 orthogonale (+ optional 4 diagonale 45-Grad-Bahnen). Diagonalen
            # packen dichter, ihre Rasterabdeckung ist aber unpraeziser ->
            # DIAG=0 fuer DRC-sauberes (orthogonales) Routing.
            for dx, dy in DIRECTIONS:
                nxg, nyg = gx + dx, gy + dy
                if not (0 <= nxg < self.nx and 0 <= nyg < self.ny):
                    continue
                nst = (L, nxg, nyg)
                if dx and dy:
                    # Diagonale: kein Eckenschneiden — beide Nachbarzellen
                    # muessen ebenfalls frei sein
                    cost = 1.414 * (1.0 + WRONG_DIR_COST) / 2.0
                else:
                    # Vorzugsrichtung je Lage (H,V,H,V...)
                    good = (self.pref_h[L] and dx) or (not self.pref_h[L] and dy)
                    cost = 1.0 if good else WRONG_DIR_COST
                ng = g + cost
                if ng < best.get(nst, 1e18) - 1e-9:
                    if not pass_trk[L][nxg, nyg]:
                        continue
                    if dx and dy and not (pass_trk[L][gx + dx, gy] and
                                          pass_trk[L][gx, gy + dy]):
                        continue
                    best[nst] = ng
                    heapq.heappush(pq, (ng + h(nxg, nyg), ng, nst, st))
            # Via: Durchkontaktierung verbindet ALLE Lagen; erlaubt Wechsel
            # auf jede andere Routing-Lage, sofern die Via-Flaeche auf jeder
            # Lage frei ist (die GND-Plane In1 wird durch Antipad beim
            # Zonenfuellen automatisch freigehalten -> nicht im occ-Grid).
            if all(pass_via[LL][gx, gy] for LL in range(self.nL)):
                for oL in range(self.nL):
                    if oL == L:
                        continue
                    nst = (oL, gx, gy)
                    ng = g + via_cost * abs(oL - L)
                    if ng < best.get(nst, 1e18) - 1e-9:
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
    def commit(self, netcode, path, width, rep=None):
        """Pfad als Bahnen+Vias aufs Board schreiben und im Raster belegen.
        rep: Pad-Cluster-Index — die Pfadzellen werden als Kupfer dieses
        Clusters registriert (Multi-Goal-Ziele fuer spaetere Kanten)."""
        net = self.board.FindNet(netcode)
        objs = self.committed.setdefault(netcode, [])
        layers = self.layer_ids
        if rep is not None:
            cells = self.cells.setdefault(netcode, {})
            for st in path:
                cells[st] = rep
        # Echtes Kupfer als Disk der wahren Halbbreite markieren; die
        # Clearance kommt beim FREMDEN Netz ueber die Dilatation (+THIN_COMP)
        # dazu. Fuer breite Bahnen (>=1 Zelle Halbbreite) deckt die Disk das
        # Kupfer korrekt; duenne Bahnen landen ~auf der Mittellinie und werden
        # ueber THIN_COMP in der Dilatation kompensiert.
        mark = self._disk(width / 2 / GRID)
        mr = mark.shape[0] // 2

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
                # Raster belegen — zellenweise entlang des Segments (bei
                # Diagonalen wuerde das Bounding-Rect viel zu viel sperren)
                n = max(abs(p1[1] - p0[1]), abs(p1[2] - p0[2]))
                ddx = (p1[1] > p0[1]) - (p1[1] < p0[1])
                ddy = (p1[2] > p0[2]) - (p1[2] < p0[2])
                for k in range(n + 1):
                    cx, cy = p0[1] + ddx * k, p0[2] + ddy * k
                    x0i, x1i = max(cx - mr, 0), min(cx + mr + 1, self.nx)
                    y0i, y1i = max(cy - mr, 0), min(cy + mr + 1, self.ny)
                    sub = mark[mr - (cx - x0i):mr + (x1i - cx),
                               mr - (cy - y0i):mr + (y1i - cy)]
                    self.occ[L][x0i:x1i, y0i:y1i][sub] = netcode
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
                v.SetViaType(pcbnew.VIATYPE_THROUGH)
                v.SetLayerPair(pcbnew.F_Cu, pcbnew.B_Cu)
                v.SetDrill(int(VIA_DRILL * 1e6))
                v.SetWidth(int(VIA_D * 1e6))
                v.SetNet(net)
                self.board.Add(v)
                objs.append(v)
                # Durchgangs-Via belegt ALLE Routing-Lagen an dieser Stelle
                hv = int(VIA_D / 2 / GRID + 1e-9)
                for L in range(self.nL):
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
                lay = tuple(L for L, lid in enumerate(self.layer_ids)
                            if pad.IsOnLayer(lid))
                nets.setdefault((code, name), []).append((x, y, lay))
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
            # Cluster-Verwaltung initialisieren
            self.net_pads[code] = pads
            self.uf[code] = list(range(len(pads)))
            self.cells[code] = {}
            for i, p in enumerate(pads):
                gx, gy = self.mm2grid(p[0], p[1])
                self.pad_idx[(code, gx, gy)] = i
            for a, b in self.mst_edges(pads):
                dist = abs(a[0] - b[0]) + abs(a[1] - b[1])
                items.append((-w, dist, code, name, a, b, w))
        # global kuerzeste zuerst (verbraucht am wenigsten Freiheit);
        # SEED-Umgebungsvariable randomisiert die Reihenfolge leicht, damit
        # ein Wiederholungslauf Verklemmungen aufloesen kann
        seed = int(os.environ.get("SEED", "0"))
        rng = random.Random(seed)
        # FAILFIRST: Datei mit Kanten, die im Vorlauf scheiterten — die
        # bekommen im Restart Prioritaet (freie Flaeche, bevor der Rest kommt)
        prio = set()
        ff = os.environ.get("FAILFIRST")
        if ff and os.path.exists(ff):
            for line in open(ff):
                parts = line.split()
                if len(parts) == 5:
                    prio.add((parts[0], int(parts[1]), int(parts[2]),
                              int(parts[3]), int(parts[4])))
        def edge_key(t):
            k = (t[3], int(round(t[4][0])), int(round(t[4][1])),
                 int(round(t[5][0])), int(round(t[5][1])))
            first = 0 if k in prio else 1
            # WIDEFIRST=1: breiteste Netze (>=1.0mm) vorziehen; Messung
            # zeigte aber, dass reine Kurz-vor-lang-Ordnung besser ist.
            wide = 1 if (t[6] >= 1.0 and os.environ.get("WIDEFIRST")) else 0
            return (first, -wide, t[1] + (rng.random() * 8 if seed else 0))
        items.sort(key=edge_key)

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
        # (RIPUP=0 deaktiviert — Erfahrung: Kaskaden koennen verschlechtern)
        for rnd in range(int(os.environ.get("RIPUP", "5"))):
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
        fo = os.environ.get("FAILOUT")
        if fo:
            with open(fo, "w") as fh:
                for c, n, a, b, w in failed:
                    fh.write(f"{n} {round(a[0])} {round(a[1])} "
                             f"{round(b[0])} {round(b[1])}\n")
        return failed

    def _try_edge(self, code, name, a, b, w, quiet=False):
        # Cluster-Logik: Kante gilt als erledigt, wenn beide Pads schon im
        # selben Baum haengen; sonst von b zum a-CLUSTER routen (jede Zelle
        # bereits verlegten Kupfers dieses Clusters ist gueltiges Ziel).
        ia = self.pad_idx.get((code,) + self.mm2grid(a[0], a[1]))
        ib = self.pad_idx.get((code,) + self.mm2grid(b[0], b[1]))
        goals = None
        if ia is not None and ib is not None:
            if self._find(code, ia) == self._find(code, ib):
                return True
            ra = self._find(code, ia)
            goals = {st for st, rep in self.cells[code].items()
                     if self._find(code, rep) == ra} or None

        def attempt(width_, **kw):
            return self.route_edge(code, name, b, a, width_,
                                   extra_goals=goals, **kw)

        used_w = w
        path = attempt(w)
        if path is None and w > 0.3:
            used_w = max(w * 0.6, 0.3)
            path = attempt(used_w)
        if path is None:
            used_w = w
            path = attempt(w, via_cost=15, max_expand=int(300000*EXPAND_SCALE))
        if path is None:
            # Eskalation 3: voller Suchraum, Vias fast gratis
            used_w = max(w * 0.6, 0.3) if w > 0.3 else w
            path = attempt(used_w, via_cost=6, max_expand=int(1200000*EXPAND_SCALE))
        if path is None:
            # Notfallstufe: JLCPCB-Minimum 5 mil (0.127mm Clearance,
            # 0.25mm-Bahn) — nur fuer die letzten hartnaeckigen Kanten
            used_w = min(w, 0.25)
            path = attempt(used_w, via_cost=6, max_expand=int(1500000*EXPAND_SCALE),
                           clearance=0.127)
        if path is None:
            if not quiet:
                print(f"  FEHLGESCHLAGEN {name}")
            return False
        rep = self._find(code, ia) if ia is not None else None
        self.commit(code, path, used_w, rep=rep)
        if ia is not None and ib is not None:
            self._union(code, ia, ib)
        return True

    def _blocking_nets(self, code, a, b, corridor=3.0):
        """Netze mit Kupfer im Korridor zwischen a und b (ohne Zonen-Netze)."""
        gx0, gy0 = self.mm2grid(min(a[0], b[0]) - corridor, min(a[1], b[1]) - corridor)
        gx1, gy1 = self.mm2grid(max(a[0], b[0]) + corridor, max(a[1], b[1]) + corridor)
        gx0, gy0 = max(gx0, 0), max(gy0, 0)
        gx1, gy1 = min(gx1, self.nx - 1), min(gy1, self.ny - 1)
        found = set()
        for L in range(self.nL):
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
        # Cluster-Zustand zuruecksetzen (alles wieder einzeln)
        if netcode in self.uf:
            self.uf[netcode] = list(range(len(self.net_pads[netcode])))
        self.cells[netcode] = {}
        # Raster von Grund auf neu (Pads + verbliebene Bahnen)
        for L in range(self.nL):
            self.occ[L][:, :] = -1
        self._mark_border()
        for k in self.extra_keepouts:
            self._mark_rect(k, both=True)
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
                    L = self.lid2idx.get(obj.GetLayer())
                    if L is not None:
                        self._mark_rect(rect, layer=L, net=nc)

    def add_power_plane_vias(self):
        """Fuer jedes Pad eines Plane-Netzes (3V3 ...) eine Durchkontaktierung
        zur Plane setzen. Via sitzt am Pad (gleiches Netz -> kein Clearance-
        Problem); die Plane-Zone bindet es thermisch an."""
        for netname, plane_layer in POWER_PLANE_NETS.items():
            net = None
            for i in range(self.board.GetNetCount()):
                n = self.board.FindNet(i)
                if n and n.GetNetname() == netname:
                    net = n
                    break
            if net is None:
                continue
            code = net.GetNetCode()
            count = 0
            for fp in self.board.GetFootprints():
                for p in fp.Pads():
                    if p.GetNetCode() != code:
                        continue
                    px, py = p.GetPosition().x / 1e6, p.GetPosition().y / 1e6
                    # SMD-Pad: Via leicht versetzt (nicht mitten aufs Pad, um
                    # Lotperlen-Absaugen zu vermeiden); THT-Pad ist selbst
                    # schon durchkontaktiert -> nur wenn keine Plane-Lage.
                    placed = False
                    for r in (0.0, 0.9, 1.2, 1.6):
                        for ang in (0, 90, 180, 270, 45, 135):
                            import math as _m
                            vx = px + (r * _m.cos(_m.radians(ang)) if r else 0)
                            vy = py + (r * _m.sin(_m.radians(ang)) if r else 0)
                            gx, gy = self.mm2grid(vx, vy)
                            if not (0 < gx < self.nx and 0 < gy < self.ny):
                                continue
                            # Via-Spalte darf auf den SIGNAL-Lagen nur eigenes
                            # Netz/frei sein (Plane-Lage nicht im occ-Grid).
                            h = mm2g(VIA_D / 2 + CLEARANCE)
                            if any(self._blocked(L, gx, gy, code, h)
                                   for L in range(self.nL)):
                                continue
                            v = pcbnew.PCB_VIA(self.board)
                            v.SetPosition(VECTOR2I(int(vx * 1e6), int(vy * 1e6)))
                            v.SetViaType(pcbnew.VIATYPE_THROUGH)
                            v.SetLayerPair(pcbnew.F_Cu, pcbnew.B_Cu)
                            v.SetDrill(int(VIA_DRILL * 1e6))
                            v.SetWidth(int(VIA_D * 1e6))
                            v.SetNet(net)
                            self.board.Add(v)
                            hv = int(VIA_D / 2 / GRID + 1e-9)
                            for L in range(self.nL):
                                self.occ[L][max(gx-hv,0):gx+hv+1,
                                            max(gy-hv,0):gy+hv+1] = code
                            count += 1
                            placed = True
                            break
                        if placed:
                            break
            print(f"Power-Plane {netname}: {count} Vias")

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
                   all(not self._blocked(L, gx, gy, -99, h)
                       for L in range(self.nL)):
                    v = pcbnew.PCB_VIA(self.board)
                    v.SetPosition(VECTOR2I(int((self.x0 + x) * 1e6),
                                           int((self.y0 + y) * 1e6)))
                    v.SetDrill(int(VIA_DRILL * 1e6))
                    v.SetWidth(int(VIA_D * 1e6))
                    v.SetNet(gnd)
                    self.board.Add(v)
                    for L in range(self.nL):
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
                # r=0 = Via-in-Pad: verbindet den GND-SMD-Pad DIREKT mit der
                # durchgehenden In1-GND-Plane (garantiert, unabhaengig davon,
                # ob die F/B-Fuellung durch Bahnen fragmentiert wurde). r>0 als
                # Fallback (versetztes Stitching-Via).
                for r in (0.0, 1.0, 1.4, 1.8, 2.4):
                    if placed:
                        break
                    for ang in range(0, 360, 45):
                        import math as _m
                        x = px + r * _m.cos(_m.radians(ang)) - self.x0
                        y = py + r * _m.sin(_m.radians(ang)) - self.y0
                        gx, gy = mm2g(x), mm2g(y)
                        if not (0 < gx < self.nx and 0 < gy < self.ny):
                            continue
                        if any(self._blocked(L, gx, gy, gnd.GetNetCode(), h)
                               for L in range(self.nL)):
                            continue
                        v = pcbnew.PCB_VIA(self.board)
                        v.SetPosition(VECTOR2I(int((self.x0 + x) * 1e6),
                                               int((self.y0 + y) * 1e6)))
                        v.SetDrill(int(VIA_DRILL * 1e6))
                        v.SetWidth(int(VIA_D * 1e6))
                        v.SetNet(gnd)
                        self.board.Add(v)
                        for L in range(self.nL):
                            self.occ[L][gx-2:gx+3, gy-2:gy+3] = gnd.GetNetCode()
                        count += 1
                        placed = True
                        break
        print(f"GND-Stitching: {count} Vias")
        # SMD-GND-Pads zusaetzlich per Bahn an den naechsten THT-GND-Anker
        # anbinden. NUR sinnvoll ohne durchgehende GND-Plane (2-Lagen-Boards);
        # bei 4-Lagen traegt die In1-Plane die Masse -> GNDANCHOR=0 vermeidet
        # die Zonenfragmentierung/Same-Net-Ueberlappungen dieser Extra-Bahnen.
        if os.environ.get("GNDANCHOR", "1") != "1":
            return
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
                                           (a[0], a[1], self.all_layers), 0.4)
                    if path:
                        self.commit(code, path, 0.4)
                        break

    def save(self):
        pcbnew.SaveBoard(self.path, self.board)
        board2 = pcbnew.LoadBoard(self.path)
        # Netzklassen-Clearance geht beim projektlosen LoadBoard auf 0.2mm
        # zurueck -> hier robust wieder auf 0.127 (JLCPCB-5mil) setzen, sonst
        # meldet die DRC alle 0.15mm-Abstaende als Verstoss.
        try:
            board2.GetDesignSettings().m_NetSettings.m_DefaultNetClass \
                .SetClearance(pcbnew.FromMM(0.127))
        except Exception:
            pass
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
    r.add_power_plane_vias()
    r.add_gnd_stitching(float(os.environ.get("STITCH", "12")))
    r.save()
    print("gespeichert:", board_path)
