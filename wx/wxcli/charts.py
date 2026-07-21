"""Sparkline-Charts fuers Terminal.

Unicode-Blockzeichen (▁▂▃▄▅▆▇█) fuer die Kurvenform, ANSI-Farbe pro Zeichen
ueber ``rich``. Die Breite passt sich dynamisch an (per Downsampling), damit
alles auch auf einem schmalen Handy-Terminal (~60 Zeichen) lesbar bleibt.
"""

from __future__ import annotations

from rich.text import Text

BLOCKS = "▁▂▃▄▅▆▇█"
Number = float | int


# --- Farbverlaeufe ------------------------------------------------------------

# (Wert, (r, g, b)) – aufsteigend sortiert.
TEMP_STOPS = [
    (-15, (59, 76, 192)),   # tiefes Blau
    (-5, (74, 144, 226)),
    (0, (57, 192, 237)),    # Cyan
    (8, (46, 204, 113)),    # Gruen
    (16, (241, 196, 15)),   # Gelb
    (24, (230, 126, 34)),   # Orange
    (32, (231, 76, 60)),    # Rot
    (40, (142, 45, 226)),   # Violett (extrem)
]

PRECIP_STOPS = [
    (0.0, (90, 96, 110)),   # gedaempftes Grau (kein Regen)
    (0.2, (93, 173, 226)),  # helles Blau (Niesel)
    (1.0, (46, 134, 222)),
    (3.0, (27, 79, 156)),   # tiefes Blau
    (8.0, (108, 52, 131)),  # Violett (Starkregen)
]

GENERIC_STOPS = [
    (0.0, (90, 96, 110)),
    (0.5, (46, 134, 222)),
    (1.0, (142, 45, 226)),
]


def _lerp(a: int, b: int, t: float) -> int:
    return int(round(a + (b - a) * t))


def color_for(value: Number, stops: list[tuple[float, tuple[int, int, int]]]) -> str:
    """Interpoliert eine Farbe (#rrggbb) fuer ``value`` entlang der Stops."""
    if value <= stops[0][0]:
        r, g, b = stops[0][1]
        return f"#{r:02x}{g:02x}{b:02x}"
    if value >= stops[-1][0]:
        r, g, b = stops[-1][1]
        return f"#{r:02x}{g:02x}{b:02x}"
    for (v0, c0), (v1, c1) in zip(stops, stops[1:]):
        if v0 <= value <= v1:
            t = 0.0 if v1 == v0 else (value - v0) / (v1 - v0)
            r = _lerp(c0[0], c1[0], t)
            g = _lerp(c0[1], c1[1], t)
            b = _lerp(c0[2], c1[2], t)
            return f"#{r:02x}{g:02x}{b:02x}"
    r, g, b = stops[-1][1]
    return f"#{r:02x}{g:02x}{b:02x}"


def ramp_for_var(var_id: str):
    if var_id == "temperature_2m":
        return TEMP_STOPS
    if var_id in ("precipitation",):
        return PRECIP_STOPS
    return GENERIC_STOPS


# --- Downsampling -------------------------------------------------------------

def downsample(values: list[float | None], width: int) -> list[float | None]:
    """Reduziert (oder belaesst) die Serie auf ``width`` Punkte (Bucket-Mittel)."""
    n = len(values)
    if width <= 0 or n == 0:
        return []
    if width >= n:
        return list(values)
    out: list[float | None] = []
    for k in range(width):
        start = k * n // width
        end = max(start + 1, (k + 1) * n // width)
        bucket = [v for v in values[start:end] if v is not None]
        out.append(sum(bucket) / len(bucket) if bucket else None)
    return out


# --- Sparklines ---------------------------------------------------------------

def _block_for(value: float, lo: float, hi: float) -> str:
    if hi <= lo:
        return BLOCKS[0]  # konstante/flache Serie = ruhige, niedrige Linie
    frac = (value - lo) / (hi - lo)
    idx = max(0, min(len(BLOCKS) - 1, round(frac * (len(BLOCKS) - 1))))
    return BLOCKS[idx]


def sparkline_text(
    values: list[float | None],
    width: int,
    var_id: str = "",
    lo: float | None = None,
    hi: float | None = None,
    colored: bool = True,
) -> Text:
    """Farbige Sparkline als rich.Text (leere Buckets als Luecke)."""
    pts = downsample(values, width)
    real = [v for v in pts if v is not None]
    if not real:
        return Text("·" * max(1, min(width, len(pts) or 1)), style="dim")
    if lo is None:
        lo = min(real)
    if hi is None:
        hi = max(real)
    stops = ramp_for_var(var_id)
    text = Text()
    for v in pts:
        if v is None:
            text.append(" ")
            continue
        ch = _block_for(v, lo, hi)
        if colored:
            text.append(ch, style=color_for(v, stops))
        else:
            text.append(ch)
    return text


def sparkline_str(values: list[float | None], width: int) -> str:
    """Reine Textvariante (ohne Farbe), z.B. fuer Logs/Tests."""
    pts = downsample(values, width)
    real = [v for v in pts if v is not None]
    if not real:
        return "·" * max(1, len(pts))
    lo, hi = min(real), max(real)
    return "".join(" " if v is None else _block_for(v, lo, hi) for v in pts)


def legend_line(var_id: str, lo: float, hi: float, unit: str) -> Text:
    """Kleine Skala-Legende: min-Farbe .. max-Farbe."""
    stops = ramp_for_var(var_id)
    t = Text()
    t.append(f"{lo:g}{unit} ", style="dim")
    for frac in (0.0, 0.25, 0.5, 0.75, 1.0):
        val = lo + (hi - lo) * frac
        t.append("█", style=color_for(val, stops))
    t.append(f" {hi:g}{unit}", style="dim")
    return t
