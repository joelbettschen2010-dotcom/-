"""Baut aus den Rohabfragen einen fertig ausgewerteten ``Report``.

Der Report buendelt alles, was Terminal-, JSON- und HTML-Ausgabe brauchen –
so bleibt die Auswertung an einer Stelle und die Ausgaben konsistent.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone

from . import stats
from .api import AuxResult, EnsembleResult, GeoResult, ModelResult
from .models import (
    FORECAST_VARS,
    MODELS_BY_ID,
    ENSEMBLE_VARS,
    VARS_BY_ID,
)

# Variablen, fuer die Vertrauen/Panels prominent gezeigt werden.
KEY_VARS = ["temperature_2m", "precipitation", "precipitation_probability",
            "windgusts_10m", "cape"]
DIVERGENCE_VARS = ["temperature_2m", "precipitation"]


@dataclass
class Report:
    place_label: str
    lat: float
    lon: float
    country: str = ""
    admin1: str = ""
    elevation: float | None = None
    timezone: str = ""
    utc_offset_seconds: int = 0
    generated_local: str = ""

    results: list[ModelResult] = field(default_factory=list)
    available_models: list[str] = field(default_factory=list)
    skipped: list[tuple[str, str]] = field(default_factory=list)
    from_cache: bool = False

    times: list[str] = field(default_factory=list)
    now_idx: int = 0
    disp_start: int = 0
    disp_end: int = 0
    days: int = 7
    display_hours: int = 48

    agg: dict[str, list[stats.Cell]] = field(default_factory=dict)
    confidences: dict[str, stats.Confidence] = field(default_factory=dict)
    divergences: dict[str, stats.Divergence] = field(default_factory=dict)
    daily: list[stats.DayStat] = field(default_factory=list)

    ensemble: EnsembleResult | None = None
    ens_dist: dict[str, list[stats.EnsCell]] = field(default_factory=dict)
    ens_counts: dict[str, int] = field(default_factory=dict)

    air: AuxResult | None = None
    marine: AuxResult | None = None

    # --- abgeleitete Helfer ---
    @property
    def _safe(self) -> str:
        """Praefix fuer SVG-Element-IDs im HTML-Export."""
        return "c_"

    @property
    def disp_slice(self) -> slice:
        return slice(self.disp_start, self.disp_end)

    @property
    def disp_times(self) -> list[str]:
        return self.times[self.disp_slice]

    def disp_median(self, var_id: str) -> list[float | None]:
        cells = self.agg.get(var_id, [])
        return [c.median for c in cells[self.disp_slice]]

    def disp_spread(self, var_id: str) -> list[float | None]:
        cells = self.agg.get(var_id, [])
        return [c.spread for c in cells[self.disp_slice]]


def build_report(
    *,
    place_label: str,
    geo: GeoResult,
    results: list[ModelResult],
    days: int,
    display_hours: int,
    ensemble: EnsembleResult | None = None,
    air: AuxResult | None = None,
    marine: AuxResult | None = None,
) -> Report:
    times, offset = stats.master_time_axis(results)
    now_idx = stats.now_index(times, offset)
    disp_end = min(len(times), now_idx + display_hours)
    tz = geo.timezone or next(
        (r.timezone for r in results if r.available and getattr(r, "timezone", "")), "")

    now_local = datetime.now(timezone.utc) + timedelta(seconds=offset)
    generated = now_local.strftime("%Y-%m-%d %H:%M")

    var_ids = [v.id for v in FORECAST_VARS]
    agg = stats.aggregate(results, times, var_ids)

    window = slice(now_idx, disp_end)
    confidences = {v: stats.confidence(v, agg[v], window) for v in KEY_VARS if v in agg}
    divergences = {v: stats.divergence(results, times, v) for v in DIVERGENCE_VARS}

    # Tagesuebersicht ab heute (dem Tag von "jetzt").
    daily_all = stats.daily_summary(times, agg)
    today = times[now_idx][:10] if times else ""
    daily = [d for d in daily_all if d.date >= today] or daily_all

    available = [r.model_id for r in results if r.available]
    skipped: list[tuple[str, str]] = []
    for r in results:
        if not r.available:
            reason = r.error or "kein Datum an diesem Ort"
            skipped.append((r.model_id, reason))

    ens_dist: dict[str, list[stats.EnsCell]] = {}
    ens_counts: dict[str, int] = {}
    if ensemble and ensemble.ok:
        n = len(ensemble.time)
        for var in ENSEMBLE_VARS:
            by_model = ensemble.members.get(var.id, {})
            if by_model:
                ens_dist[var.id] = stats.ensemble_distribution(by_model, n)
                ens_counts.update(stats.member_counts(by_model))

    return Report(
        place_label=place_label,
        lat=geo.lat, lon=geo.lon, country=geo.country, admin1=geo.admin1,
        elevation=geo.elevation, timezone=tz,
        utc_offset_seconds=offset, generated_local=generated,
        results=results, available_models=available, skipped=skipped,
        from_cache=all(r.from_cache for r in results) if results else False,
        times=times, now_idx=now_idx, disp_start=now_idx, disp_end=disp_end,
        days=days, display_hours=display_hours,
        agg=agg, confidences=confidences, divergences=divergences, daily=daily,
        ensemble=ensemble, ens_dist=ens_dist, ens_counts=ens_counts,
        air=air, marine=marine,
    )


# --- JSON-Serialisierung ------------------------------------------------------

def report_to_json(report: Report) -> dict:
    """Vollstaendiges, maschinenlesbares Abbild (fuer --json und die Analyse)."""
    def cells(var):
        return [c.to_dict() for c in report.agg.get(var, [])]

    per_model = {}
    for r in report.results:
        per_model[r.model_id] = {
            "available": r.available,
            "error": r.error,
            "from_cache": r.from_cache,
            "hourly": r.hourly if r.available else {},
        }

    out = {
        "location": {
            "label": report.place_label,
            "latitude": report.lat,
            "longitude": report.lon,
            "country": report.country,
            "admin1": report.admin1,
            "elevation": report.elevation,
            "timezone": report.timezone,
            "utc_offset_seconds": report.utc_offset_seconds,
        },
        "generated_local": report.generated_local,
        "from_cache": report.from_cache,
        "forecast_days": report.days,
        "models": {
            "available": report.available_models,
            "skipped": [{"model": m, "reason": r} for m, r in report.skipped],
            "meta": {
                mid: {
                    "label": MODELS_BY_ID[mid].label,
                    "agency": MODELS_BY_ID[mid].agency,
                    "kind": MODELS_BY_ID[mid].kind,
                    "region": MODELS_BY_ID[mid].region,
                    "resolution": MODELS_BY_ID[mid].resolution,
                }
                for mid in report.available_models if mid in MODELS_BY_ID
            },
        },
        "time": report.times,
        "now_index": report.now_idx,
        "units": {v.id: v.unit for v in FORECAST_VARS},
        "aggregate": {v.id: cells(v.id) for v in FORECAST_VARS},
        "confidence": {
            k: {
                "label": c.label, "low_fraction": c.low_frac,
                "mean_spread": c.mean_spread, "models_typical": c.n_models_typ,
            }
            for k, c in report.confidences.items()
        },
        "aifs_vs_ifs": {
            k: {
                "ok": d.ok, "tendency": d.tendency, "mean_abs": d.mean_abs,
                "max_abs": d.max_abs, "max_abs_at": d.max_abs_at,
                "diff": d.diff,
            }
            for k, d in report.divergences.items()
        },
        "daily": [d.to_dict() for d in report.daily],
        "per_model": per_model,
    }

    if report.ens_dist:
        out["ensemble"] = {
            "time": report.ensemble.time if report.ensemble else [],
            "member_counts": report.ens_counts,
            "distribution": {
                var: [c.to_dict() for c in cells_]
                for var, cells_ in report.ens_dist.items()
            },
        }
    if report.air and report.air.ok:
        out["air_quality"] = {"time": report.air.time, "hourly": report.air.hourly}
    if report.marine and report.marine.ok:
        out["marine"] = {"time": report.marine.time, "hourly": report.marine.hourly}
    return out
