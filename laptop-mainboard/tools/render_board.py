#!/usr/bin/env python3
"""Rendert laptop-mainboard.kicad_pcb als PNG (Bestueckungsansicht von oben)."""
import math
import os
import sys

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import Rectangle, Circle, Polygon

from kiutils.board import Board

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
b = Board.from_file(os.path.join(BASE, "kicad", "laptop-mainboard.kicad_pcb"))

fig, ax = plt.subplots(figsize=(20, 7.5), dpi=160)
ax.set_facecolor("#0a3d20")
fig.patch.set_facecolor("#111")

# Zonen (Innenlagen) angedeutet
ZCOL = {"In3.Cu": "#1a5c30", "In4.Cu": "#15502a"}
for z in b.zones:
    lay = z.layers[0] if z.layers else ""
    if lay in ZCOL:
        for poly in z.polygons:
            pts = [(pt.X, pt.Y) for pt in poly.coordinates]
            ax.add_patch(Polygon(pts, closed=True, facecolor=ZCOL[lay],
                                 edgecolor="none", zorder=1))

# Platinenumriss
for g in b.graphicItems:
    if getattr(g, "layer", "") == "Edge.Cuts" and hasattr(g, "start"):
        ax.plot([g.start.X, g.end.X], [g.start.Y, g.end.Y],
                color="#e8c860", lw=2.2, zorder=6)

# Leiterbahnen + Vias
for t in b.traceItems:
    cls = type(t).__name__
    if cls == "Segment":
        col = "#d4b34a" if t.layer == "F.Cu" else "#8fb3d4"
        ax.plot([t.start.X, t.end.X], [t.start.Y, t.end.Y],
                color=col, lw=0.7, alpha=0.85, zorder=3)
    elif cls == "Via":
        ax.add_patch(Circle((t.position.X, t.position.Y), 0.35,
                            facecolor="#c9c9c9", edgecolor="none", zorder=3))


def rot_pt(px, py, deg):
    a = math.radians(deg or 0)
    c, s = math.cos(a), math.sin(a)
    return px * c + py * s, -px * s + py * c


for fp in b.footprints:
    fx, fy = fp.position.X, fp.position.Y
    ang = fp.position.angle or 0
    back = fp.layer.startswith("B")
    padcol = "#f2d16b" if not back else "#e08840"
    bodycol = "#173318" if not back else "#26200e"
    # Gehaeuseumriss aus Fab/Silk-Linien
    xs, ys = [], []
    for it in fp.graphicItems:
        if type(it).__name__ == "FpLine":
            for pt in (it.start, it.end):
                rx, ry = rot_pt(pt.X, pt.Y, ang)
                xs.append(fx + rx)
                ys.append(fy + ry)
    if xs:
        ax.add_patch(Rectangle((min(xs), min(ys)), max(xs) - min(xs),
                               max(ys) - min(ys), facecolor=bodycol,
                               edgecolor="#3f5a3f" if not back else "#5a4a2a",
                               lw=0.5, alpha=0.9 if not back else 0.55,
                               zorder=2))
    # Pads
    many = len(fp.pads) > 200
    for p in fp.pads:
        rx, ry = rot_pt(p.position.X, p.position.Y, ang)
        px, py = fx + rx, fy + ry
        w = p.size.X, p.size.Y
        if p.type == "np_thru_hole":
            ax.add_patch(Circle((px, py), p.size.X / 2, facecolor="#0a3d20",
                                edgecolor="#e8c860", lw=0.6, zorder=4))
        elif many:
            ax.add_patch(Circle((px, py), p.size.X / 2 * 0.9,
                                facecolor="#d9b95c", edgecolor="none", zorder=4))
        else:
            ww, hh = p.size.X, p.size.Y
            if (ang or 0) % 180 == 90:
                ww, hh = hh, ww
            fc = padcol if p.type == "smd" else "#cfcfcf"
            ax.add_patch(Rectangle((px - ww / 2, py - hh / 2), ww, hh,
                                   facecolor=fc, edgecolor="none",
                                   zorder=4, alpha=1 if not back else 0.7))
    # Referenz
    ref = fp.properties.get("Reference", "")
    val = fp.properties.get("Value", "")
    if ref.startswith("H"):
        continue
    big = ref in ("XU1", "J_MEM1", "J_MEM2", "J_SSD1", "J_SSD2", "U23", "U41",
                  "J_BAT", "J_LAN1", "J_TB0", "J_TB1", "J_HDMI", "J_WIFI",
                  "J_USBA1", "J_USBA2", "J_AUX", "J_EDP", "U1", "U3")
    if back and not big:
        continue
    label = f"{ref}\n{val}" if big else ref
    ax.text(fx, fy, label, ha="center", va="center",
            fontsize=6.5 if big else 4.2,
            color="#ffffff" if big else "#b8ccb8",
            weight="bold" if big else "normal", zorder=7)

# Board-Titel + Portbeschriftung
ax.text(195, 24, "Laptop-Mainboard im HP-EliteBook-850-G7-Format  -  "
                 "AMD Ryzen 5 8600G (AM5, gesockelt), 2x DDR5 SO-DIMM, "
                 "2x NVMe Gen4, 2x Thunderbolt 4, 2.5GbE",
        ha="center", fontsize=11, color="#f0f0f0", weight="bold")
ports_r = [(42, "USB-C  100W-PD + TB4"), (54, "USB-C  TB4"), (70, "HDMI 2.1"),
           (90, "USB-A"), (112, "RJ45 2.5GbE")]
for y, t in ports_r:
    ax.text(368, y, t, ha="left", va="center", fontsize=7.5, color="#ffd27f")
for y, t in [(60, "USB-A"), (84, "Klinke")]:
    ax.text(22, y, t, ha="right", va="center", fontsize=7.5, color="#ffd27f")
ax.text(195, 146.5, "HP-Akkuschacht 3S / 56 Wh", ha="center", fontsize=8,
        color="#9fc79f", style="italic")

ax.set_xlim(0, 400)
ax.set_ylim(155, 18)
ax.set_aspect("equal")
ax.axis("off")
out = os.path.join(BASE, "docs", "board_render.png")
plt.tight_layout()
plt.savefig(out, bbox_inches="tight", facecolor="#111")
print(out)
