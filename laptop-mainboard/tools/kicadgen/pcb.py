"""Footprint-Generatoren und Board-Erzeugung (.kicad_pcb, .kicad_mod)."""
from .sexpr import uid, f


class Fp:
    """Footprint: pads = Liste von Dicts, lines = Grafik."""

    def __init__(self, name, desc=""):
        self.name = name
        self.desc = desc
        self.pads = []
        self.lines = []
        self.texts = []

    def pad_smd(self, num, x, y, w, h, shape="roundrect", net=None):
        self.pads.append(dict(num=num, kind="smd", shape=shape, x=x, y=y,
                              w=w, h=h, drill=None, net=net))

    def pad_th(self, num, x, y, d, drill, net=None):
        self.pads.append(dict(num=num, kind="thru_hole", shape="circle",
                              x=x, y=y, w=d, h=d, drill=drill, net=net))

    def pad_npth(self, x, y, d):
        self.pads.append(dict(num="", kind="np_thru_hole", shape="circle",
                              x=x, y=y, w=d, h=d, drill=d, net=None))

    def rect(self, x1, y1, x2, y2, layer="F.SilkS", w=0.12):
        self.lines += [(x1, y1, x2, y1, layer, w), (x2, y1, x2, y2, layer, w),
                       (x2, y2, x1, y2, layer, w), (x1, y2, x1, y1, layer, w)]

    def courtyard(self, x1, y1, x2, y2):
        self.rect(x1, y1, x2, y2, "F.CrtYd", 0.05)

    def body(self, netmap=None):
        s = []
        s.append(f'  (descr "{self.desc}")')
        s.append('  (attr smd)' if all(p["kind"] == "smd" for p in self.pads)
                 else '  (attr through_hole)')
        for x1, y1, x2, y2, layer, w in self.lines:
            s.append(f'  (fp_line (start {f(x1)} {f(y1)}) (end {f(x2)} {f(y2)}) '
                     f'(stroke (width {f(w)}) (type solid)) (layer "{layer}") '
                     f'(uuid "{uid("fl")}"))')
        for t, x, y, layer in self.texts:
            s.append(f'  (fp_text user "{t}" (at {f(x)} {f(y)} 0) (layer "{layer}") '
                     f'(uuid "{uid("ft")}")')
            s.append('    (effects (font (size 1 1) (thickness 0.15)))')
            s.append('  )')
        for p in self.pads:
            net = ''
            if netmap is not None and p.get("net"):
                net = f' (net {netmap[p["net"]]} "{p["net"]}")'
            if p["kind"] == "smd":
                extra = ' (roundrect_rratio 0.25)' if p["shape"] == "roundrect" else ''
                s.append(f'  (pad "{p["num"]}" smd {p["shape"]} '
                         f'(at {f(p["x"])} {f(p["y"])}) (size {f(p["w"])} {f(p["h"])}) '
                         f'(layers "F.Cu" "F.Paste" "F.Mask"){extra}{net} (uuid "{uid("pd")}"))')
            elif p["kind"] == "thru_hole":
                s.append(f'  (pad "{p["num"]}" thru_hole circle '
                         f'(at {f(p["x"])} {f(p["y"])}) (size {f(p["w"])} {f(p["h"])}) '
                         f'(drill {f(p["drill"])}) (layers "*.Cu" "*.Mask"){net} '
                         f'(uuid "{uid("pd")}"))')
            else:
                s.append(f'  (pad "" np_thru_hole circle '
                         f'(at {f(p["x"])} {f(p["y"])}) (size {f(p["w"])} {f(p["h"])}) '
                         f'(drill {f(p["drill"])}) (layers "*.Cu" "*.Mask") '
                         f'(uuid "{uid("pd")}"))')
        return "\n".join(s)

    def mod_file(self):
        s = []
        s.append(f'(footprint "{self.name}"')
        s.append('  (version 20240108)')
        s.append('  (generator "pcbnew")')
        s.append('  (generator_version "8.0")')
        s.append('  (layer "F.Cu")')
        s.append(f'  (property "Reference" "REF**" (at 0 -2 0) (layer "F.SilkS") '
                 f'(uuid "{uid("pr")}") (effects (font (size 1 1) (thickness 0.15))))')
        s.append(f'  (property "Value" "{self.name}" (at 0 2 0) (layer "F.Fab") '
                 f'(uuid "{uid("pv")}") (effects (font (size 1 1) (thickness 0.15))))')
        s.append(self.body())
        s.append(')')
        return "\n".join(s) + "\n"

    def board_instance(self, ref, value, x, y, rot, netmap=None, side="F"):
        s = []
        s.append(f'  (footprint "laptop_mainboard:{self.name}"')
        s.append(f'    (layer "{side}.Cu")')
        s.append(f'    (uuid "{uid("bf")}")')
        s.append(f'    (at {f(x)} {f(y)} {rot})')
        s.append(f'    (property "Reference" "{ref}" (at 0 -3 {-rot if rot else 0}) '
                 f'(layer "{side}.SilkS") (uuid "{uid("br")}") '
                 f'(effects (font (size 1.2 1.2) (thickness 0.2))))')
        s.append(f'    (property "Value" "{value}" (at 0 3 {-rot if rot else 0}) '
                 f'(layer "{side}.Fab") (uuid "{uid("bv")}") '
                 f'(effects (font (size 1 1) (thickness 0.15))))')
        body = self.body(netmap)
        if side == "B":
            for a, b in [('"F.Cu"', '"B.Cu"'), ('"F.Paste"', '"B.Paste"'),
                         ('"F.Mask"', '"B.Mask"'), ('"F.SilkS"', '"B.SilkS"'),
                         ('"F.CrtYd"', '"B.CrtYd"'), ('"F.Fab"', '"B.Fab"')]:
                body = body.replace(a, b)
        s.append("\n".join("  " + ln for ln in body.split("\n")))
        s.append('  )')
        return "\n".join(s)


# ---------------------------------------------------------------------------
# Footprint-Bibliothek
# ---------------------------------------------------------------------------

def fp_am5(base_dir=None):
    """Echter AM5-Footprint aus der Pinmap-CSV (siehe kicadgen/am5.py)."""
    from . import am5
    if base_dir is None:
        base_dir = __import__("os").path.dirname(__import__("os").path.dirname(
            __import__("os").path.dirname(__import__("os").path.abspath(__file__))))
    return am5.build_fp(base_dir, Fp)


def fp_sodimm():
    fp = Fp("SODIMM_DDR5_262", "DDR5 SO-DIMM Sockel 262-polig, liegend")
    pitch = 0.5
    per_row = 131
    for i in range(per_row):
        x = (i - (per_row - 1) / 2) * pitch
        fp.pad_smd(str(2 * i + 1), round(x, 3), -3.5, 0.3, 1.6)
        fp.pad_smd(str(2 * i + 2), round(x, 3), 3.5, 0.3, 1.6)
    fp.pad_th("S1", -35.5, 0, 1.8, 1.2)
    fp.pad_th("S2", 35.5, 0, 1.8, 1.2)
    fp.rect(-36.5, -5, 36.5, 5, "F.SilkS")
    fp.rect(-36.5, -5, 36.5, 26, "F.Fab", 0.1)    # Modulflaeche
    fp.courtyard(-37, -5.5, 37, 26.5)
    return fp


def fp_m2(name, length, desc):
    fp = Fp(name, desc)
    pitch = 0.5
    n_pads = 75
    for i in range(n_pads):
        x = (i - (n_pads - 1) / 2) * pitch
        fp.pad_smd(str(i + 1), round(x, 3), 0, 0.35, 2.0)
    fp.pad_th("MP", length - 3, 0, 3.8, 2.5)      # Standoff-Gewinde
    fp.rect(-20, -2, 20, 2, "F.SilkS")
    fp.rect(-20, -2, length, 12, "F.Fab", 0.1)
    fp.courtyard(-20.5, -2.5, length + 2, 12.5)
    return fp


def fp_bga(name, rows, cols, pitch, desc):
    fp = Fp(name, desc)
    letters = "ABCDEFGHJKLMNPRTUVWY"
    for r in range(rows):
        for c in range(cols):
            x = (c - (cols - 1) / 2) * pitch
            y = (r - (rows - 1) / 2) * pitch
            fp.pad_smd(f"{letters[r]}{c+1}", round(x, 3), round(y, 3),
                       pitch * 0.55, pitch * 0.55, "circle")
    w = cols * pitch / 2 + 1
    h = rows * pitch / 2 + 1
    fp.rect(-w, -h, w, h, "F.SilkS")
    fp.courtyard(-w - 0.25, -h - 0.25, w + 0.25, h + 0.25)
    return fp


def fp_qfn(name, per_side, size, pitch, desc, ep=True):
    fp = Fp(name, desc)
    half = size / 2
    off = half + 0.35
    n = 0
    start = -(per_side - 1) / 2 * pitch
    for i in range(per_side):          # links, oben->unten
        n += 1
        fp.pad_smd(str(n), -off, round(start + i * pitch, 3), 0.9, pitch * 0.55)
    for i in range(per_side):          # unten, links->rechts
        n += 1
        fp.pad_smd(str(n), round(start + i * pitch, 3), off, pitch * 0.55, 0.9)
    for i in range(per_side):          # rechts, unten->oben
        n += 1
        fp.pad_smd(str(n), off, round(-start - i * pitch, 3), 0.9, pitch * 0.55)
    for i in range(per_side):          # oben, rechts->links
        n += 1
        fp.pad_smd(str(n), round(-start - i * pitch, 3), -off, pitch * 0.55, 0.9)
    if ep:
        n += 1
        fp.pad_smd(str(n), 0, 0, size * 0.55, size * 0.55)
    fp.rect(-half, -half, half, half, "F.SilkS")
    fp.courtyard(-off - 0.6, -off - 0.6, off + 0.6, off + 0.6)
    return fp


def fp_soic(name, pins, pitch, row_span, desc):
    fp = Fp(name, desc)
    per = pins // 2
    start = -(per - 1) / 2 * pitch
    for i in range(per):
        fp.pad_smd(str(i + 1), round(start + i * pitch, 3), row_span / 2,
                   pitch * 0.5, 1.6)
    for i in range(per):
        fp.pad_smd(str(pins - i), round(start + i * pitch, 3), -row_span / 2,
                   pitch * 0.5, 1.6)
    w = per * pitch / 2 + 0.5
    fp.rect(-w, -row_span / 2 + 1, w, row_span / 2 - 1, "F.SilkS")
    fp.courtyard(-w - 0.3, -row_span / 2 - 1.2, w + 0.3, row_span / 2 + 1.2)
    return fp


def fp_lqfp128():
    fp = Fp("LQFP128_14x14", "LQFP-128, 0.4mm Pitch, 14x14mm")
    per, pitch, half = 32, 0.4, 7.0
    off = half + 0.9
    n = 0
    start = -(per - 1) / 2 * pitch
    for i in range(per):
        n += 1
        fp.pad_smd(str(n), -off, round(start + i * pitch, 3), 1.4, 0.22)
    for i in range(per):
        n += 1
        fp.pad_smd(str(n), round(start + i * pitch, 3), off, 0.22, 1.4)
    for i in range(per):
        n += 1
        fp.pad_smd(str(n), off, round(-start - i * pitch, 3), 1.4, 0.22)
    for i in range(per):
        n += 1
        fp.pad_smd(str(n), round(-start - i * pitch, 3), -off, 0.22, 1.4)
    fp.rect(-half, -half, half, half, "F.SilkS")
    fp.courtyard(-off - 0.8, -off - 0.8, off + 0.8, off + 0.8)
    return fp


def fp_2pad(name, pad_w, pad_h, span, desc, th=False):
    fp = Fp(name, desc)
    if th:
        fp.pad_th("1", -span / 2, 0, pad_w, pad_w * 0.55)
        fp.pad_th("2", span / 2, 0, pad_w, pad_w * 0.55)
    else:
        fp.pad_smd("1", -span / 2, 0, pad_w, pad_h)
        fp.pad_smd("2", span / 2, 0, pad_w, pad_h)
    w = span / 2 + pad_w / 2 + 0.3
    h = pad_h / 2 + 0.3
    fp.rect(-w, -h, w, h, "F.SilkS")
    fp.courtyard(-w - 0.2, -h - 0.2, w + 0.2, h + 0.2)
    return fp


def fp_fpc(name, n, desc):
    fp = Fp(name, desc)
    pitch = 0.5
    start = -(n - 1) / 2 * pitch
    for i in range(n):
        fp.pad_smd(str(i + 1), round(start + i * pitch, 3), 0, 0.3, 1.3)
    w = n * pitch / 2 + 1.5
    fp.pad_smd("S1", -w + 0.3, 0.2, 1.2, 2.0)
    fp.pad_smd("S2", w - 0.3, 0.2, 1.2, 2.0)
    fp.rect(-w, -1.5, w, 4, "F.SilkS")
    fp.courtyard(-w - 0.3, -1.8, w + 0.3, 4.3)
    return fp


def fp_usbc():
    fp = Fp("USBC_24", "USB-C-Buchse 24-polig, Mid-Mount")
    for i in range(12):
        x = (i - 5.5) * 0.5
        fp.pad_smd(f"A{i+1}", round(x, 3), -1.5, 0.3, 1.1)
        fp.pad_smd(f"B{i+1}", round(x, 3), -0.0, 0.3, 1.1)
    for i, (x, y) in enumerate([(-4.32, 1.5), (4.32, 1.5), (-4.62, 5), (4.62, 5)]):
        fp.pad_th(f"S{i+1}", x, y, 1.7, 1.1)
    fp.rect(-4.7, -2.2, 4.7, 6.5, "F.SilkS")
    fp.courtyard(-5.2, -2.5, 5.2, 7.8)
    return fp


def fp_usba():
    fp = Fp("USBA3_TH", "USB-A 3.0 Buchse, Durchsteck")
    xs = [-3.5, -2.5, -1.5, 3.5]
    for i, x in enumerate(xs):
        fp.pad_th(str(i + 1), x, 0, 1.5, 0.95)
    for i in range(5):
        fp.pad_th(str(5 + i), -2.0 + i * 1.0, -2.0, 1.5, 0.95)
    fp.pad_th("S1", -6.6, 1.5, 2.6, 1.7)
    fp.pad_th("S2", 6.6, 1.5, 2.6, 1.7)
    fp.rect(-6.8, -3.5, 6.8, 14, "F.SilkS")
    fp.courtyard(-7.2, -4, 7.2, 14.5)
    return fp


def fp_rj45():
    fp = Fp("RJ45_MAGJACK", "RJ45 mit integrierten Uebertragern (2.5G)")
    for i in range(8):
        fp.pad_th(str(i + 1), -3.5 + i * 1.0, -3.0, 1.5, 0.9)
    for i in range(6):
        fp.pad_th(str(9 + i), -2.5 + i * 1.0, -5.5, 1.5, 0.9)
    fp.pad_th("S1", -7.6, 3.5, 2.4, 1.6)
    fp.pad_th("S2", 7.6, 3.5, 2.4, 1.6)
    fp.rect(-8.1, -6.5, 8.1, 13, "F.SilkS")
    fp.courtyard(-8.5, -7, 8.5, 13.5)
    return fp


def fp_hdmi():
    fp = Fp("HDMI_A", "HDMI-Typ-A-Buchse, SMD + TH-Schild")
    for i in range(19):
        x = (i - 9) * 0.5
        y = -1.5 if i % 2 == 0 else -0.4
        fp.pad_smd(str(i + 1), round(x, 3), y, 0.3, 1.0)
    for i, (x, y) in enumerate([(-5.75, 1.0), (5.75, 1.0), (-5.0, 6.0), (5.0, 6.0)]):
        fp.pad_th(f"S{i+1}", x, y, 1.9, 1.3)
    fp.rect(-7.5, -2.2, 7.5, 10, "F.SilkS")
    fp.courtyard(-8, -2.5, 8, 10.5)
    return fp


def fp_sd():
    fp = Fp("SD_PUSH", "SD-Kartenslot full size, Push-Push")
    for i in range(12):
        fp.pad_smd(str(i + 1), -13.75 + i * 2.5, -1.0, 1.2, 1.8)
    for i, (x, y) in enumerate([(-15.5, 5), (15.5, 5), (-15.5, 22), (15.5, 22)]):
        fp.pad_smd(f"S{i+1}", x, y, 1.6, 2.2)
    fp.rect(-15, 0.5, 15, 27, "F.SilkS")
    fp.courtyard(-16.5, -2.2, 16.5, 27.5)
    return fp


def fp_trrs():
    fp = Fp("TRRS_35", "3.5mm-Klinkenbuchse TRRS + Detect")
    for i in range(6):
        fp.pad_th(str(i + 1), -5 + i * 2, 0, 1.6, 1.0)
    fp.rect(-7, -3, 7, 12, "F.SilkS")
    fp.courtyard(-7.5, -3.5, 7.5, 12.5)
    return fp


def fp_conn_th(name, n, pitch, desc):
    fp = Fp(name, desc)
    start = -(n - 1) / 2 * pitch
    for i in range(n):
        fp.pad_th(str(i + 1), round(start + i * pitch, 3), 0, 1.4, 0.8)
    w = n * pitch / 2 + 1.5
    fp.rect(-w, -2, w, 2, "F.SilkS")
    fp.courtyard(-w - 0.3, -2.3, w + 0.3, 2.3)
    return fp


def fp_coin():
    fp = Fp("COIN_ML1220", "ML1220-Halter, SMD (12.5mm Knopfzelle)")
    fp.pad_smd("1", 0, -7.5, 2.5, 3)
    fp.pad_smd("2", 0, 7.5, 2.5, 3)
    fp.rect(-6.5, -6.5, 6.5, 6.5, "F.SilkS")
    fp.courtyard(-8, -9.5, 8, 9.5)
    return fp


def fp_mtg():
    fp = Fp("MTG_M2_5", "Befestigungsloch M2.5 mit Restring")
    fp.pad_th("1", 0, 0, 5.0, 2.7)
    fp.courtyard(-3, -3, 3, 3)
    return fp


def build_library():
    """Alle Footprints als dict name -> Fp."""
    fps = [
        fp_am5(), fp_sodimm(),
        fp_m2("M2_M_2280", 80, "M.2 M-Key Sockel + Standoff 2280"),
        fp_m2("M2_E_2230", 30, "M.2 E-Key Sockel + Standoff 2230"),
        fp_bga("BGA96_9x9", 10, 10, 0.8, "BGA-96/100 0.8mm (TPS65988)"),
        fp_bga("BGA_10x9_JHL8540", 20, 22, 0.45, "FCBGA ~10x9mm 0.45mm (JHL8540)"),
        fp_qfn("QFN32_4x4", 8, 4, 0.4, "QFN-32 4x4mm"),
        fp_qfn("QFN20_3x4", 5, 3.5, 0.5, "QFN-20 3x4mm"),
        fp_qfn("QFN40_5x5", 10, 5, 0.4, "QFN-40 5x5mm"),
        fp_qfn("QFN31_5x5", 8, 5, 0.5, "PowerStage-QFN 5x5mm"),
        fp_qfn("QFN48_6x6", 12, 6, 0.4, "QFN-48 6x6mm"),
        fp_qfn("QFN30_4x4", 8, 4, 0.4, "QFN-30 4x4mm"),
        fp_lqfp128(),
        fp_soic("SOIC8", 8, 1.27, 5.4, "SOIC-8"),
        fp_soic("TSSOP24", 24, 0.65, 6.4, "TSSOP-24"),
        fp_soic("TSSOP16EP", 16, 0.65, 6.4, "TSSOP-16 mit Pad"),
        fp_soic("SOT23-5", 6, 0.95, 2.6, "SOT-23-5 (Pin 6 entfaellt)"),
        fp_soic("DFN6_2x2", 6, 0.65, 2.0, "DFN-6 2x2mm"),
        fp_qfn("PowerPAK_1212", 2, 3.3, 0.65, "PowerPAK 1212-8 (vereinfacht)"),
        fp_2pad("IND_7x7", 2.4, 6.0, 5.4, "Drossel 7x7mm"),
        fp_2pad("IND_4x4", 1.4, 3.6, 3.2, "Drossel 4x4mm"),
        fp_2pad("CAP_7343", 2.4, 2.4, 5.6, "Polymer-Elko 7343"),
        fp_2pad("C_1812", 1.2, 3.4, 3.8, "Kondensator 1812"),
        fp_2pad("R_2512", 1.4, 3.4, 5.4, "Shunt 2512"),
        fp_2pad("R_0603", 0.8, 0.9, 1.5, "0603"),
        fp_2pad("SMA_DIODE", 1.6, 1.8, 4.2, "SMA-Diode"),
        fp_2pad("XTAL_3215", 1.1, 1.9, 2.2, "Quarz 3.2x1.5"),
        fp_2pad("XTAL_3225", 1.3, 2.0, 2.4, "Quarz 3.2x2.5"),
        fp_usbc(), fp_usba(), fp_rj45(), fp_hdmi(), fp_sd(), fp_trrs(),
        fp_fpc("FPC40_05", 40, "FPC 40-polig 0.5mm (eDP)"),
        fp_fpc("FPC30_05", 30, "FPC 30-polig 0.5mm (Tastatur)"),
        fp_fpc("FPC8_05", 8, "FPC 8-polig 0.5mm (Touchpad)"),
        fp_fpc("FPC6_05", 6, "FPC 6-polig 0.5mm"),
        fp_conn_th("CONN_BAT8_2mm", 8, 2.0, "Akku-Steckverbinder 8-polig"),
        fp_conn_th("CONN_FAN4", 4, 1.25, "Luefter 4-polig"),
        fp_conn_th("CONN_SPK2", 2, 1.5, "Lautsprecher 2-polig"),
        fp_coin(), fp_mtg(),
    ]
    return {fp.name: fp for fp in fps}


# ---------------------------------------------------------------------------
# Board
# ---------------------------------------------------------------------------

LAYERS = """  (layers
    (0 "F.Cu" signal)
    (1 "In1.Cu" signal "In1.Cu_SIG-DDR")
    (2 "In2.Cu" power "In2.Cu_GND1")
    (3 "In3.Cu" power "In3.Cu_PWR")
    (4 "In4.Cu" power "In4.Cu_PWR2")
    (5 "In5.Cu" power "In5.Cu_GND2")
    (6 "In6.Cu" signal "In6.Cu_SIG-PCIe")
    (31 "B.Cu" signal)
    (32 "B.Adhes" user "B.Adhesive")
    (33 "F.Adhes" user "F.Adhesive")
    (34 "B.Paste" user)
    (35 "F.Paste" user)
    (36 "B.SilkS" user "B.Silkscreen")
    (37 "F.SilkS" user "F.Silkscreen")
    (38 "B.Mask" user)
    (39 "F.Mask" user)
    (40 "Dwgs.User" user "User.Drawings")
    (41 "Cmts.User" user "User.Comments")
    (42 "Eco1.User" user "User.Eco1")
    (43 "Eco2.User" user "User.Eco2")
    (44 "Edge.Cuts" user)
    (45 "Margin" user)
    (46 "B.CrtYd" user "B.Courtyard")
    (47 "F.CrtYd" user "F.Courtyard")
    (48 "B.Fab" user)
    (49 "F.Fab" user)
  )"""

BASE_NETS = ["", "GND", "+VSYS", "+5V", "+3V3", "+VDDCR_CPU", "+VDDCR_SOC",
             "+VBAT", "+1V8", "+3V3_ALW", "+1V1_MEM"]


def gr_line(x1, y1, x2, y2, layer="Edge.Cuts", w=0.15):
    return (f'  (gr_line (start {f(x1)} {f(y1)}) (end {f(x2)} {f(y2)}) '
            f'(stroke (width {f(w)}) (type solid)) (layer "{layer}") '
            f'(uuid "{uid("gl")}"))')


def gr_text(t, x, y, layer="F.SilkS", size=1.5):
    return (f'  (gr_text "{t}" (at {f(x)} {f(y)} 0) (layer "{layer}") '
            f'(uuid "{uid("gt")}")\n'
            f'    (effects (font (size {f(size)} {f(size)}) (thickness 0.25)))\n'
            f'  )')


def zone(netnum, netname, layer, pts, name=""):
    s = []
    s.append(f'  (zone (net {netnum}) (net_name "{netname}") (layer "{layer}") '
             f'(uuid "{uid("z")}")' + (f' (name "{name}")' if name else ''))
    s.append('    (hatch edge 0.508)')
    s.append('    (connect_pads (clearance 0.3))')
    s.append('    (min_thickness 0.25) (filled_areas_thickness no)')
    s.append('    (fill yes (thermal_gap 0.4) (thermal_bridge_width 0.4))')
    s.append('    (polygon (pts ' +
             " ".join(f"(xy {f(x)} {f(y)})" for x, y in pts) + '))')
    s.append('  )')
    return "\n".join(s)


def segment(x1, y1, x2, y2, w=0.1, layer="F.Cu", net=0):
    return (f'  (segment (start {f(x1)} {f(y1)}) (end {f(x2)} {f(y2)}) '
            f'(width {f(w)}) (layer "{layer}") (net {net}) (uuid "{uid("sg")}"))')


def via(x, y, net=1, size=0.45, drill=0.25):
    return (f'  (via (at {f(x)} {f(y)}) (size {f(size)}) (drill {f(drill)}) '
            f'(layers "F.Cu" "B.Cu") (net {net}) (uuid "{uid("vi")}"))')


def diff_pair(x1, y1, x2, y2, layer="F.Cu"):
    """Repraesentatives 100-Ohm-Paar (0.1mm Breite, 0.15mm Abstand)."""
    return "\n".join([segment(x1, y1, x2, y2, 0.1, layer),
                      segment(x1, y1 + 0.25, x2, y2 + 0.25, 0.1, layer)])


def board_file(placements, lib, extras):
    """placements: (fp_name, ref, value, x, y, rot[, side])."""
    # Netztabelle: Basis-Netze + alle Pad-Netze der platzierten Footprints
    netmap = {}
    for i, n in enumerate(BASE_NETS):
        netmap[n] = i
    for pl in placements:
        fp_name = pl[0]
        for p in lib[fp_name].pads:
            n = p.get("net")
            if n and n not in netmap:
                netmap[n] = len(netmap)
    s = []
    s.append('(kicad_pcb')
    s.append('  (version 20240108)')
    s.append('  (generator "pcbnew")')
    s.append('  (generator_version "8.0")')
    s.append('  (general (thickness 1.6) (legacy_teardrops no))')
    s.append('  (paper "A2")')
    s.append(LAYERS)
    s.append('  (setup')
    s.append('    (pad_to_mask_clearance 0)')
    s.append('    (allow_soldermask_bridges_in_footprints no)')
    s.append('  )')
    for n, i in sorted(netmap.items(), key=lambda kv: kv[1]):
        s.append(f'  (net {i} "{n}")')
    for pl in placements:
        fp_name, ref, value, x, y, rot = pl[:6]
        side = pl[6] if len(pl) > 6 else "F"
        s.append(lib[fp_name].board_instance(ref, value, x, y, rot,
                                             netmap=netmap, side=side))
    s += extras
    s.append(')')
    return "\n".join(s) + "\n", netmap
