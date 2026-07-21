"""Statistische Auswertung ueber alle Modelle.

Kernidee: pro Zeitschritt Median, Min, Max und Spread ueber alle verfuegbaren
Modelle. Hoher Spread = geringes Vertrauen. Zusaetzlich die KI-vs-klassisch-
Gegenueberstellung (AIFS vs. IFS) und die Ensemble-Verteilung.
"""

from __future__ import annotations

import statistics
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone

from .models import AI_MODEL_ID, CLASSIC_COUNTERPART_ID, VARS_BY_ID


# --- Zeitachse ---------------------------------------------------------------

def parse_time(t: str) -> datetime:
    return datetime.strptime(t, "%Y-%m-%dT%H:%M")


def now_index(times: list[str], utc_offset_seconds: int) -> int:
    """Index des ersten Zeitschritts >= jetzt (in Ortszeit)."""
    if not times:
        return 0
    now_local = datetime.now(timezone.utc) + timedelta(seconds=utc_offset_seconds)
    now_naive = now_local.replace(tzinfo=None)
    for i, t in enumerate(times):
        try:
            if parse_time(t) >= now_naive.replace(minute=0, second=0, microsecond=0):
                return i
        except ValueError:
            continue
    return max(0, len(times) - 1)


def master_time_axis(results) -> tuple[list[str], int]:
    """Waehlt die laengste Zeitachse und den zugehoerigen UTC-Offset."""
    best: list[str] = []
    offset = 0
    for r in results:
        if getattr(r, "available", False) and len(r.time) > len(best):
            best = r.time
            offset = r.utc_offset_seconds
    return best, offset


# --- Aggregation ueber Modelle -----------------------------------------------

@dataclass
class Cell:
    n: int = 0
    median: float | None = None
    vmin: float | None = None
    vmax: float | None = None
    mean: float | None = None
    spread: float | None = None
    stdev: float | None = None

    def to_dict(self) -> dict:
        return self.__dict__


def _cell(values: list[float]) -> Cell:
    if not values:
        return Cell()
    vmin, vmax = min(values), max(values)
    return Cell(
        n=len(values),
        median=round(statistics.median(values), 3),
        vmin=round(vmin, 3),
        vmax=round(vmax, 3),
        mean=round(statistics.fmean(values), 3),
        spread=round(vmax - vmin, 3),
        stdev=round(statistics.pstdev(values), 3) if len(values) > 1 else 0.0,
    )


def aggregate(results, times: list[str], var_ids: list[str]) -> dict[str, list[Cell]]:
    """Pro Variable eine Liste von Cells, ausgerichtet auf ``times``."""
    n = len(times)
    avail = [r for r in results if getattr(r, "available", False)]
    out: dict[str, list[Cell]] = {}
    for var in var_ids:
        cells: list[Cell] = []
        for i in range(n):
            vals: list[float] = []
            for r in avail:
                series = r.hourly.get(var)
                if series and i < len(series) and series[i] is not None:
                    vals.append(float(series[i]))
            cells.append(_cell(vals))
        out[var] = cells
    return out


def median_series(cells: list[Cell]) -> list[float | None]:
    return [c.median for c in cells]


def spread_series(cells: list[Cell]) -> list[float | None]:
    return [c.spread for c in cells]


# --- Vertrauen ---------------------------------------------------------------

@dataclass
class Confidence:
    var_id: str
    n_models_typ: int          # typische Modellzahl (Median ueber Zeit)
    low_frac: float            # Anteil Zeitschritte mit hohem Spread
    mean_spread: float | None
    label: str                 # "hoch" | "mittel" | "niedrig"


def confidence(var_id: str, cells: list[Cell], window: slice | None = None) -> Confidence:
    var = VARS_BY_ID.get(var_id)
    threshold = var.low_conf_spread if var else 1e9
    sub = cells[window] if window else cells
    considered = [c for c in sub if c.n >= 2 and c.spread is not None]
    ns = [c.n for c in sub if c.n > 0]
    if not considered:
        return Confidence(var_id, statistics.median(ns) if ns else 0, 0.0, None, "n/a")
    low = sum(1 for c in considered if c.spread >= threshold)
    low_frac = low / len(considered)
    mean_spread = round(statistics.fmean([c.spread for c in considered]), 2)
    label = "hoch" if low_frac < 0.15 else "mittel" if low_frac < 0.4 else "niedrig"
    return Confidence(
        var_id=var_id,
        n_models_typ=int(statistics.median(ns)) if ns else 0,
        low_frac=round(low_frac, 3),
        mean_spread=mean_spread,
        label=label,
    )


# --- KI vs. klassisch: AIFS vs. IFS ------------------------------------------

@dataclass
class Divergence:
    var_id: str
    ok: bool = False
    aifs: list[float | None] = field(default_factory=list)
    ifs: list[float | None] = field(default_factory=list)
    diff: list[float | None] = field(default_factory=list)  # aifs - ifs
    mean_abs: float | None = None
    max_abs: float | None = None
    max_abs_at: str | None = None
    tendency: str = ""  # z.B. "AIFS waermer" / "AIFS trockener"


def _series_for(results, model_id: str, var: str, n: int) -> list[float | None] | None:
    for r in results:
        if r.model_id == model_id and getattr(r, "available", False):
            s = r.hourly.get(var)
            if not s:
                return None
            return [(float(s[i]) if i < len(s) and s[i] is not None else None) for i in range(n)]
    return None


def divergence(results, times: list[str], var_id: str) -> Divergence:
    n = len(times)
    aifs = _series_for(results, AI_MODEL_ID, var_id, n)
    ifs = _series_for(results, CLASSIC_COUNTERPART_ID, var_id, n)
    if aifs is None or ifs is None:
        return Divergence(var_id=var_id, ok=False)

    diff: list[float | None] = []
    for a, b in zip(aifs, ifs):
        diff.append(round(a - b, 3) if a is not None and b is not None else None)
    valid = [(d, times[i]) for i, d in enumerate(diff) if d is not None]
    if not valid:
        return Divergence(var_id=var_id, ok=False, aifs=aifs, ifs=ifs, diff=diff)

    mean_abs = round(statistics.fmean([abs(d) for d, _ in valid]), 3)
    max_d, max_t = max(valid, key=lambda x: abs(x[0]))
    mean_signed = statistics.fmean([d for d, _ in valid])
    if var_id == "temperature_2m":
        tendency = "AIFS wärmer" if mean_signed > 0.2 else "AIFS kühler" if mean_signed < -0.2 else "≈ gleich"
    elif var_id in ("precipitation", "precipitation_probability"):
        tendency = "AIFS nasser" if mean_signed > 0.1 else "AIFS trockener" if mean_signed < -0.1 else "≈ gleich"
    else:
        tendency = "AIFS höher" if mean_signed > 0 else "AIFS niedriger" if mean_signed < 0 else "≈ gleich"

    return Divergence(
        var_id=var_id, ok=True, aifs=aifs, ifs=ifs, diff=diff,
        mean_abs=mean_abs, max_abs=round(abs(max_d), 3), max_abs_at=max_t, tendency=tendency,
    )


# --- Ensemble-Verteilung ------------------------------------------------------

def _percentile(sorted_vals: list[float], q: float) -> float:
    if not sorted_vals:
        return float("nan")
    if len(sorted_vals) == 1:
        return sorted_vals[0]
    pos = q / 100 * (len(sorted_vals) - 1)
    lo = int(pos)
    hi = min(lo + 1, len(sorted_vals) - 1)
    frac = pos - lo
    return sorted_vals[lo] * (1 - frac) + sorted_vals[hi] * frac


@dataclass
class EnsCell:
    n: int = 0
    p10: float | None = None
    p25: float | None = None
    p50: float | None = None
    p75: float | None = None
    p90: float | None = None
    vmin: float | None = None
    vmax: float | None = None

    def to_dict(self) -> dict:
        return self.__dict__


def ensemble_distribution(members_by_model: dict[str, list[list[float]]], n: int) -> list[EnsCell]:
    """Kombiniert alle Member (ueber Modelle) zu Perzentilen pro Zeitschritt."""
    all_series = [s for series in members_by_model.values() for s in series]
    cells: list[EnsCell] = []
    for i in range(n):
        vals = sorted(float(s[i]) for s in all_series if i < len(s) and s[i] is not None)
        if not vals:
            cells.append(EnsCell())
            continue
        cells.append(EnsCell(
            n=len(vals),
            p10=round(_percentile(vals, 10), 2),
            p25=round(_percentile(vals, 25), 2),
            p50=round(_percentile(vals, 50), 2),
            p75=round(_percentile(vals, 75), 2),
            p90=round(_percentile(vals, 90), 2),
            vmin=round(vals[0], 2),
            vmax=round(vals[-1], 2),
        ))
    return cells


def member_counts(members_by_model: dict[str, list[list[float]]]) -> dict[str, int]:
    return {tag: len(series) for tag, series in members_by_model.items()}


# --- Tages-Zusammenfassung ----------------------------------------------------

@dataclass
class DayStat:
    date: str
    tmin: float | None = None
    tmax: float | None = None
    temp_spread: float | None = None
    precip: float | None = None
    pop: float | None = None
    gust: float | None = None
    cape: float | None = None
    low_conf: bool = False

    def to_dict(self) -> dict:
        return self.__dict__


def daily_summary(times: list[str], agg: dict[str, list[Cell]]) -> list[DayStat]:
    """Fasst die (Median-)Aggregate pro Kalendertag zusammen."""
    from collections import OrderedDict

    days: "OrderedDict[str, list[int]]" = OrderedDict()
    for i, t in enumerate(times):
        day = t[:10]
        days.setdefault(day, []).append(i)

    temp = agg.get("temperature_2m", [])
    precip = agg.get("precipitation", [])
    pop = agg.get("precipitation_probability", [])
    gust = agg.get("windgusts_10m", [])
    cape = agg.get("cape", [])
    temp_thr = VARS_BY_ID["temperature_2m"].low_conf_spread

    out: list[DayStat] = []
    for day, idxs in days.items():
        tmeds = [temp[i].median for i in idxs if i < len(temp) and temp[i].median is not None]
        tspreads = [temp[i].spread for i in idxs if i < len(temp) and temp[i].spread is not None]
        pmeds = [precip[i].median for i in idxs if i < len(precip) and precip[i].median is not None]
        pops = [pop[i].median for i in idxs if i < len(pop) and pop[i].median is not None]
        gusts = [gust[i].median for i in idxs if i < len(gust) and gust[i].median is not None]
        capes = [cape[i].median for i in idxs if i < len(cape) and cape[i].median is not None]
        mean_spread = statistics.fmean(tspreads) if tspreads else None
        out.append(DayStat(
            date=day,
            tmin=round(min(tmeds), 1) if tmeds else None,
            tmax=round(max(tmeds), 1) if tmeds else None,
            temp_spread=round(mean_spread, 1) if mean_spread is not None else None,
            precip=round(sum(pmeds), 1) if pmeds else None,
            pop=round(max(pops), 0) if pops else None,
            gust=round(max(gusts), 0) if gusts else None,
            cape=round(max(capes), 0) if capes else None,
            low_conf=bool(mean_spread is not None and mean_spread >= temp_thr),
        ))
    return out
