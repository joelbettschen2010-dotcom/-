"""Asynchrone Open-Meteo-Abfragen (httpx) mit Verfuegbarkeitserkennung.

Jedes Modell wird als *eigener* paralleler Request geholt. Liefert die API
HTTP 400 mit "No data is available for this location", gilt das Modell an
diesem Ort als nicht verfuegbar und wird uebersprungen statt zu crashen.
Ergebnisse (auch "nicht verfuegbar") landen im SQLite-Cache.
"""

from __future__ import annotations

import asyncio
from dataclasses import dataclass, field

import httpx

from .cache import Cache
from .models import (
    AIR_QUALITY_VARS_PARAM,
    ENSEMBLE_VARS,
    ENSEMBLE_VARS_PARAM,
    FORECAST_VARS_PARAM,
    MARINE_VARS_PARAM,
    Model,
)

FORECAST_URL = "https://api.open-meteo.com/v1/forecast"
ENSEMBLE_URL = "https://ensemble-api.open-meteo.com/v1/ensemble"
GEOCODE_URL = "https://geocoding-api.open-meteo.com/v1/search"
AIR_QUALITY_URL = "https://air-quality-api.open-meteo.com/v1/air-quality"
MARINE_URL = "https://marine-api.open-meteo.com/v1/marine"

USER_AGENT = "wx-multimodel-cli/1.0 (+https://open-meteo.com)"
MAX_CONCURRENCY = 8


# --- Ergebnis-Container -------------------------------------------------------

@dataclass
class GeoResult:
    name: str
    lat: float
    lon: float
    country: str = ""
    admin1: str = ""
    elevation: float | None = None
    timezone: str = ""


@dataclass
class ModelResult:
    model_id: str
    available: bool = False
    error: str | None = None
    time: list[str] = field(default_factory=list)
    hourly: dict[str, list] = field(default_factory=dict)
    utc_offset_seconds: int = 0
    timezone: str = ""
    from_cache: bool = False


@dataclass
class EnsembleResult:
    ok: bool = False
    error: str | None = None
    time: list[str] = field(default_factory=list)
    utc_offset_seconds: int = 0
    # var -> model_tag -> Liste von Member-Serien
    members: dict[str, dict[str, list[list[float]]]] = field(default_factory=dict)
    from_cache: bool = False


@dataclass
class AuxResult:
    """Air-Quality- oder Marine-Ergebnis."""

    ok: bool = False
    error: str | None = None
    time: list[str] = field(default_factory=list)
    hourly: dict[str, list] = field(default_factory=dict)
    utc_offset_seconds: int = 0


# --- HTTP-Helfer --------------------------------------------------------------

def _is_no_data(payload: object) -> bool:
    if isinstance(payload, dict) and payload.get("error"):
        reason = str(payload.get("reason", "")).lower()
        return "no data" in reason or "out of" in reason or "available" in reason
    return False


async def _get_json(
    client: httpx.AsyncClient, url: str, params: dict, retries: int = 1
) -> tuple[dict | None, str | None, bool]:
    """GET -> (payload, error, is_no_data).

    ``is_no_data`` markiert das HTTP-400-"kein-Datum-hier"-Signal, das *keine*
    Stoerung ist, sondern schlicht fehlende Modellabdeckung.
    """
    last_err: str | None = None
    for attempt in range(retries + 1):
        try:
            resp = await client.get(url, params=params)
        except httpx.HTTPError as exc:
            last_err = f"{type(exc).__name__}: {exc}"
            if attempt < retries:
                await asyncio.sleep(1.5 * (attempt + 1))
                continue
            return None, last_err, False

        if resp.status_code == 200:
            try:
                return resp.json(), None, False
            except ValueError as exc:
                return None, f"Ungueltiges JSON: {exc}", False

        # 400 kann "kein Datum hier" bedeuten -> nicht verfuegbar, kein Fehler.
        try:
            payload = resp.json()
        except ValueError:
            payload = None
        if resp.status_code == 400 and _is_no_data(payload):
            return payload, None, True

        reason = payload.get("reason") if isinstance(payload, dict) else None
        last_err = f"HTTP {resp.status_code}" + (f": {reason}" if reason else "")
        # 4xx nicht erneut versuchen; 5xx schon.
        if resp.status_code < 500 or attempt >= retries:
            return None, last_err, False
        await asyncio.sleep(1.5 * (attempt + 1))
    return None, last_err, False


# --- Geocoding ----------------------------------------------------------------

async def geocode(client: httpx.AsyncClient, query: str) -> GeoResult | None:
    payload, err, _ = await _get_json(
        client, GEOCODE_URL,
        {"name": query, "count": 1, "language": "de", "format": "json"},
    )
    if err or not payload:
        return None
    results = payload.get("results") or []
    if not results:
        return None
    r = results[0]
    return GeoResult(
        name=r.get("name", query),
        lat=float(r["latitude"]),
        lon=float(r["longitude"]),
        country=r.get("country", ""),
        admin1=r.get("admin1", ""),
        elevation=r.get("elevation"),
        timezone=r.get("timezone", ""),
    )


# --- Forecast (pro Modell, parallel) -----------------------------------------

def _has_real_data(hourly: dict, var: str = "temperature_2m") -> bool:
    return any(v is not None for v in hourly.get(var, []))


async def _fetch_forecast_model(
    client: httpx.AsyncClient,
    cache: Cache,
    sem: asyncio.Semaphore,
    model: Model,
    lat: float,
    lon: float,
    days: int,
) -> ModelResult:
    key = Cache.make_key("forecast", model.id, round(lat, 3), round(lon, 3), days, FORECAST_VARS_PARAM)
    cached = cache.get(key)
    if cached is not None:
        return ModelResult(
            model_id=model.id,
            available=cached.get("available", False),
            error=cached.get("error"),
            time=cached.get("time", []),
            hourly=cached.get("hourly", {}),
            utc_offset_seconds=cached.get("utc_offset_seconds", 0),
            timezone=cached.get("timezone", ""),
            from_cache=True,
        )

    params = {
        "latitude": round(lat, 4),
        "longitude": round(lon, 4),
        "hourly": FORECAST_VARS_PARAM,
        "models": model.id,
        "forecast_days": days,
        "timezone": "auto",
    }
    async with sem:
        payload, err, no_data = await _get_json(client, FORECAST_URL, params)

    if no_data:
        result = ModelResult(model_id=model.id, available=False, error=None)
    elif err or not payload:
        result = ModelResult(model_id=model.id, available=False, error=err or "Keine Antwort")
    else:
        hourly = payload.get("hourly", {}) or {}
        time_axis = hourly.pop("time", []) if "time" in hourly else []
        available = _has_real_data(hourly)
        result = ModelResult(
            model_id=model.id,
            available=available,
            error=None if available else "Keine Daten in der Antwort",
            time=time_axis,
            hourly=hourly,
            utc_offset_seconds=int(payload.get("utc_offset_seconds", 0)),
            timezone=str(payload.get("timezone", "")),
        )

    cache.set(key, {
        "available": result.available,
        "error": result.error,
        "time": result.time,
        "hourly": result.hourly,
        "utc_offset_seconds": result.utc_offset_seconds,
        "timezone": result.timezone,
    })
    return result


async def fetch_all_forecast(
    cache: Cache, models: list[Model], lat: float, lon: float, days: int
) -> list[ModelResult]:
    sem = asyncio.Semaphore(MAX_CONCURRENCY)
    timeout = httpx.Timeout(25.0, connect=10.0)
    async with httpx.AsyncClient(timeout=timeout, headers={"User-Agent": USER_AGENT}) as client:
        tasks = [
            _fetch_forecast_model(client, cache, sem, m, lat, lon, days)
            for m in models
        ]
        return await asyncio.gather(*tasks)


# --- Ensemble -----------------------------------------------------------------

def _parse_ensemble(payload: dict) -> dict[str, dict[str, list[list[float]]]]:
    """Zerlegt die Ensemble-Antwort in var -> modell_tag -> [member-serien]."""
    import re

    hourly = payload.get("hourly", {}) or {}
    out: dict[str, dict[str, list[list[float]]]] = {}
    member_re = re.compile(r"member(\d+)_")

    for var in ENSEMBLE_VARS:
        prefix = var.id + "_"
        by_model: dict[str, list[list[float]]] = {}
        for key, series in hourly.items():
            if key == "time" or not key.startswith(prefix):
                continue
            suffix = key[len(prefix):]
            if suffix.startswith("probability"):  # gegen precipitation-Kollision
                continue
            m = member_re.match(suffix)
            model_tag = member_re.sub("", suffix, count=1) if m else suffix
            by_model.setdefault(model_tag, []).append(series)
        if by_model:
            out[var.id] = by_model
    return out


async def fetch_ensemble(
    cache: Cache, ensemble_models: list[Model], lat: float, lon: float, days: int
) -> EnsembleResult:
    key = Cache.make_key(
        "ensemble", [m.id for m in ensemble_models], round(lat, 3), round(lon, 3), days, ENSEMBLE_VARS_PARAM
    )
    cached = cache.get(key)
    if cached is not None:
        return EnsembleResult(
            ok=cached.get("ok", False), error=cached.get("error"),
            time=cached.get("time", []), utc_offset_seconds=cached.get("utc_offset_seconds", 0),
            members=cached.get("members", {}), from_cache=True,
        )

    params = {
        "latitude": round(lat, 4),
        "longitude": round(lon, 4),
        "hourly": ENSEMBLE_VARS_PARAM,
        "models": ",".join(m.id for m in ensemble_models),
        "forecast_days": days,
        "timezone": "auto",
    }
    timeout = httpx.Timeout(30.0, connect=10.0)
    async with httpx.AsyncClient(timeout=timeout, headers={"User-Agent": USER_AGENT}) as client:
        payload, err, no_data = await _get_json(client, ENSEMBLE_URL, params)

    if err or no_data or not payload:
        result = EnsembleResult(ok=False, error=err or "Kein Ensemble fuer diesen Ort")
    else:
        members = _parse_ensemble(payload)
        result = EnsembleResult(
            ok=bool(members),
            error=None if members else "Keine Member erhalten",
            time=(payload.get("hourly", {}) or {}).get("time", []),
            utc_offset_seconds=int(payload.get("utc_offset_seconds", 0)),
            members=members,
        )
    cache.set(key, {
        "ok": result.ok, "error": result.error, "time": result.time,
        "utc_offset_seconds": result.utc_offset_seconds, "members": result.members,
    })
    return result


# --- Zusatzmodule: Air Quality & Marine --------------------------------------

async def _fetch_aux(
    cache: Cache, url: str, kind: str, vars_param: str, lat: float, lon: float, days: int
) -> AuxResult:
    key = Cache.make_key(kind, round(lat, 3), round(lon, 3), days, vars_param)
    cached = cache.get(key)
    if cached is not None:
        return AuxResult(**cached)

    params = {
        "latitude": round(lat, 4),
        "longitude": round(lon, 4),
        "hourly": vars_param,
        "forecast_days": days,
        "timezone": "auto",
    }
    timeout = httpx.Timeout(25.0, connect=10.0)
    async with httpx.AsyncClient(timeout=timeout, headers={"User-Agent": USER_AGENT}) as client:
        payload, err, no_data = await _get_json(client, url, params)

    if err or no_data or not payload:
        result = AuxResult(ok=False, error=err or "Nicht verfuegbar")
    else:
        hourly = payload.get("hourly", {}) or {}
        time_axis = hourly.pop("time", []) if "time" in hourly else []
        ok = any(_has_real_data(hourly, v) for v in hourly)
        result = AuxResult(
            ok=ok, error=None if ok else "Keine Daten",
            time=time_axis, hourly=hourly,
            utc_offset_seconds=int(payload.get("utc_offset_seconds", 0)),
        )
    cache.set(key, result.__dict__)
    return result


async def fetch_air_quality(cache: Cache, lat: float, lon: float, days: int) -> AuxResult:
    return await _fetch_aux(cache, AIR_QUALITY_URL, "air_quality", AIR_QUALITY_VARS_PARAM, lat, lon, min(days, 5))


async def fetch_marine(cache: Cache, lat: float, lon: float, days: int) -> AuxResult:
    return await _fetch_aux(cache, MARINE_URL, "marine", MARINE_VARS_PARAM, lat, lon, days)
