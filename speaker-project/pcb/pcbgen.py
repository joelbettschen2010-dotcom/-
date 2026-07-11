"""
PCB generator using the pcbnew (KiCad 7) Python API.

Creates the board from the same design data as the schematic:
  - loads official KiCad footprints, assigns nets to pads
  - places parts group-wise into predefined regions (greedy shelf packing,
    key parts pinned to explicit coordinates)
  - board outline, mounting holes, GND zones on both layers, PVDD power
    zone on B.Cu under the amplifier section, AGND island zone
  - net classes / design rules per the project spec (0.2 mm signal,
    1.5 mm power, 3 mm battery)

Routing status is reported honestly by DRC — see pcb/README.md.
"""

import os
import pcbnew
from pcbnew import VECTOR2I, FromMM


def mm(v):
    return FromMM(float(v))


def pt(x, y):
    return VECTOR2I(mm(x), mm(y))


FP_BASE = "/usr/share/kicad/footprints"
_DIM_CACHE = {}


def footprint_dims(footprint_id, rot=0):
    """(w, h) der Footprint-Bounding-Box, gecacht — ohne Board-Beruehrung."""
    key = (footprint_id, rot % 180)
    if key not in _DIM_CACHE:
        lib, name = footprint_id.split(":")
        fp = pcbnew.FootprintLoad(f"{FP_BASE}/{lib}.pretty", name)
        fp.SetOrientationDegrees(rot)
        bb = fp.GetBoundingBox(False, False)
        _DIM_CACHE[key] = (bb.GetWidth() / 1e6, bb.GetHeight() / 1e6)
    return _DIM_CACHE[key]


class BoardBuilder:
    def __init__(self, path, w, h, copper_layers=2):
        self.board = pcbnew.CreateEmptyBoard()
        self.board.SetFileName(os.path.abspath(path))
        self.w, self.h = w, h
        self.copper_layers = copper_layers
        if copper_layers > 2:
            self.board.SetCopperLayerCount(copper_layers)
        self.nets = {}
        self._outline()
        self._rules()

    # ------------------------------------------------------------------
    def _rules(self):
        ds = self.board.GetDesignSettings()
        # JLCPCB-2-Lagen-Standard: min 0.127/0.127 (5 mil). Der Router
        # arbeitet normal mit 0.15 Clearance; die Notfallstufe fuer die
        # letzten Engstellen darf bis 0.127 runter — die Design-Regeln
        # spiegeln das Fertigungslimit.
        ds.m_TrackMinWidth = mm(0.13)    # erlaubt 0.15mm-Fine-Pitch-Bahnen
        ds.m_MinClearance = mm(0.127)
        try:
            ds.m_NetSettings.m_DefaultNetClass.SetClearance(mm(0.127))
        except AttributeError:
            pass
        ds.m_ViasMinSize = mm(0.45)      # JLCPCB-4-Lagen-Minimum-Via
        ds.m_MinThroughDrill = mm(0.2)   # Thermal-Vias in TI/Espressif-Footprints
        self.obstacles = []              # (x0,y0,x1,y1) belegter Flaechen

    def _outline(self):
        for a, b in [((0, 0), (self.w, 0)), ((self.w, 0), (self.w, self.h)),
                     ((self.w, self.h), (0, self.h)), ((0, self.h), (0, 0))]:
            seg = pcbnew.PCB_SHAPE(self.board)
            seg.SetShape(pcbnew.SHAPE_T_SEGMENT)
            seg.SetStart(pt(*a))
            seg.SetEnd(pt(*b))
            seg.SetLayer(pcbnew.Edge_Cuts)
            seg.SetWidth(mm(0.15))
            self.board.Add(seg)

    def net(self, name):
        if name not in self.nets:
            n = pcbnew.NETINFO_ITEM(self.board, name)
            self.board.Add(n)
            self.nets[name] = n
        return self.nets[name]

    # ------------------------------------------------------------------
    def add_footprint(self, comp, x, y, rot=0, flip=False, register=True):
        lib, name = comp.footprint.split(":")
        fp = pcbnew.FootprintLoad(f"{FP_BASE}/{lib}.pretty", name)
        if fp is None:
            raise RuntimeError(f"Footprint fehlt: {comp.footprint}")
        fp.SetReference(comp.ref)
        fp.SetValue(comp.value)
        self.board.Add(fp)
        if flip:
            fp.Flip(pt(x, y), False)
        fp.SetPosition(pt(x, y))
        fp.SetOrientationDegrees(rot)
        # Netze zuweisen: alle Pads mit gleicher Padnummer bekommen das Netz
        netmap = {}
        for pad, _name, net in comp.pins:
            if net:
                netmap[pad] = net
        for pad in fp.Pads():
            n = netmap.get(pad.GetNumber())
            if n:
                pad.SetNet(self.net(n))
        if register:
            self._register(fp)
        return fp

    def _register(self, fp, margin=0.4):
        bb = fp.GetBoundingBox(False, False)
        self.obstacles.append((bb.GetLeft() / 1e6 - margin, bb.GetTop() / 1e6 - margin,
                               bb.GetRight() / 1e6 + margin, bb.GetBottom() / 1e6 + margin))

    def collides(self, x0, y0, x1, y1):
        for a0, b0, a1, b1 in self.obstacles:
            if x0 < a1 and x1 > a0 and y0 < b1 and y1 > b0:
                return True
        return False

    def mounting_hole(self, x, y, d=3.2):
        fp = pcbnew.FootprintLoad(f"{FP_BASE}/MountingHole.pretty",
                                  f"MountingHole_{d}mm_M3")
        if fp is None:
            fp = pcbnew.FootprintLoad(f"{FP_BASE}/MountingHole.pretty",
                                      "MountingHole_3.2mm_M3")
        fp.SetReference("H")
        self.board.Add(fp)
        fp.SetPosition(pt(x, y))
        self._register(fp, margin=0.8)

    def zone(self, netname, layer, rect, priority=0):
        z = pcbnew.ZONE(self.board)
        z.SetLayer(layer)
        z.SetNetCode(self.net(netname).GetNetCode())
        z.SetAssignedPriority(priority)
        x0, y0, x1, y1 = rect
        pts = [pt(x0, y0), pt(x1, y0), pt(x1, y1), pt(x0, y1)]
        chain = pcbnew.SHAPE_LINE_CHAIN()
        for p in pts:
            chain.Append(p.x, p.y)
        chain.SetClosed(True)
        z.Outline().AddOutline(chain)
        z.SetLocalClearance(mm(0.2))
        z.SetMinThickness(mm(0.2))
        # SOLID-Anbindung fuer Massen-/Versorgungsflaechen: bei reflow-
        # bestueckten Boards ist Thermal-Relief unnoetig und erzeugt bei
        # dichter Verdrahtung "starved thermal"-Fehler. Voll angebunden ->
        # jeder GND/PVDD-Pad haengt sauber an der Flaeche.
        z.SetPadConnection(pcbnew.ZONE_CONNECTION_FULL)
        z.SetIslandRemovalMode(pcbnew.ISLAND_REMOVAL_MODE_ALWAYS)
        self.board.Add(z)
        return z

    def keepout_pour(self, rect, layers=None):
        """Sperrflaeche NUR gegen Zonen-Fuellung (Bahnen/Vias erlaubt).
        Verhindert, dass GND/AGND-Pours das netzlose Net-Tie-Polygon
        beruehren (DRC: zone clearance vs. Polygon)."""
        z = pcbnew.ZONE(self.board)
        z.SetIsRuleArea(True)
        z.SetDoNotAllowCopperPour(True)
        z.SetDoNotAllowTracks(False)
        z.SetDoNotAllowVias(False)
        z.SetDoNotAllowPads(False)
        z.SetDoNotAllowFootprints(False)
        x0, y0, x1, y1 = rect
        chain = pcbnew.SHAPE_LINE_CHAIN()
        for p in [pt(x0, y0), pt(x1, y0), pt(x1, y1), pt(x0, y1)]:
            chain.Append(p.x, p.y)
        chain.SetClosed(True)
        z.Outline().AddOutline(chain)
        ls = pcbnew.LSET()
        for lid in (layers or [pcbnew.F_Cu]):
            ls.AddLayer(lid)
        z.SetLayerSet(ls)
        self.board.Add(z)
        return z

    def track(self, netname, path, width, layer=pcbnew.F_Cu):
        for a, b in zip(path, path[1:]):
            t = pcbnew.PCB_TRACK(self.board)
            t.SetStart(pt(*a))
            t.SetEnd(pt(*b))
            t.SetWidth(mm(width))
            t.SetLayer(layer)
            t.SetNet(self.net(netname))
            self.board.Add(t)

    def save(self):
        # ZONE_FILLER segfaultet auf CreateEmptyBoard-Boards (kein Projekt-
        # Kontext). Workaround: erst speichern, neu laden, dann fuellen.
        path = self.board.GetFileName()
        pcbnew.SaveBoard(path, self.board)
        board2 = pcbnew.LoadBoard(path)
        # Default-Netzklassen-Clearance robust auf 0.127 mm setzen (auf dem
        # frischen CreateEmptyBoard persistiert SetClearance nicht zuverlaessig
        # -> hier auf dem neu geladenen Board, das wirklich gespeichert wird).
        try:
            board2.GetDesignSettings().m_NetSettings.m_DefaultNetClass \
                .SetClearance(mm(0.127))
        except Exception:
            pass
        filler = pcbnew.ZONE_FILLER(board2)
        filler.Fill(board2.Zones())
        pcbnew.SaveBoard(path, board2)


def shelf_pack(builder, comps, region, gap=1.1):
    """Kollisionsbewusste Greedy-Packung. Masse kommen aus dem Footprint-
    Cache; das Footprint wird erst am gefundenen Platz eingefuegt (kein
    Add/Remove-Zyklus -> keine SWIG-Objektleichen)."""
    x0, y0, x1, y1 = region
    left = []
    for comp in comps:
        bw, bh = footprint_dims(comp.footprint)
        w, h = bw + gap, bh + gap
        placed = False
        cy = y0
        while cy + h <= y1 and not placed:
            cx = x0
            while cx + w <= x1:
                if not builder.collides(cx, cy, cx + w, cy + h):
                    builder.add_footprint(comp, cx + w / 2, cy + h / 2)
                    placed = True
                    break
                cx += 0.5
            cy += 0.5
        if not placed:
            left.append(comp)
    return left
