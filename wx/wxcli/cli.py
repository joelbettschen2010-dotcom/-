"""CLI-Einstieg: ``wx <ort> [optionen]``.

Aufloesen des Orts (Config oder Geocoding), parallele Multi-Modell-Abfrage,
Auswertung und Ausgabe als Terminal-Ansicht, JSON oder eigenstaendiges HTML.
"""

from __future__ import annotations

import argparse
import asyncio
import json
import sys

from rich.console import Console

from . import __version__
from .api import (
    GeoResult,
    fetch_air_quality,
    fetch_all_forecast,
    fetch_ensemble,
    fetch_marine,
    geocode,
)
from .cache import Cache
from .config import _slug, cache_path, config_path, load_config
from .models import ENSEMBLE_MODELS, FORECAST_MODELS, MODELS_BY_ID
from .report import build_report, report_to_json


def _build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(
        prog="wx",
        description="Multi-Modell-Wetter aus Open-Meteo (keyless, parallel).",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=(
            "Beispiele:\n"
            "  wx                     Standardort aus der Config\n"
            "  wx Frutigen            benannter/geocodeter Ort\n"
            "  wx \"Punta Gorda\" --html   HTML-Export fuers Handy\n"
            "  wx Bern --ensemble     GEFS/ICON-EPS-Verteilung\n"
            "  wx Zermatt --json      maschinenlesbar (fuer die Analyse)\n"
        ),
    )
    p.add_argument("place", nargs="?", help="Ort (Config-Key, Name oder frei per Geocoding)")
    p.add_argument("--ensemble", action="store_true", help="Ensemble-Modus (GEFS/ICON-EPS)")
    p.add_argument("--html", nargs="?", const="", metavar="PFAD",
                   help="eigenstaendige HTML-Datei schreiben (optional Pfad)")
    p.add_argument("--json", action="store_true", help="JSON-Ausgabe (keine Farben)")
    p.add_argument("--days", type=int, metavar="N", help="Prognosehorizont in Tagen (1–16)")
    p.add_argument("--hours", type=int, metavar="N", help="Stunden im Terminal-Verlauf")
    p.add_argument("--air", action="store_true", help="Luftqualitaet ergaenzen")
    p.add_argument("--marine", action="store_true", help="Marine (Wellen) ergaenzen")
    p.add_argument("--models", metavar="A,B", help="nur diese Modelle (ID oder Kürzel)")
    p.add_argument("--width", type=int, metavar="N", help="Ausgabebreite erzwingen")
    p.add_argument("--no-cache", action="store_true", help="Cache umgehen")
    p.add_argument("--clear-cache", action="store_true", help="Cache leeren und beenden")
    p.add_argument("--list-places", action="store_true", help="konfigurierte Orte zeigen")
    p.add_argument("--version", action="version", version=f"wx {__version__}")
    return p


def _select_models(spec: str | None):
    if not spec:
        return list(FORECAST_MODELS)
    wanted = [s.strip().lower() for s in spec.split(",") if s.strip()]
    by_label = {m.label.lower(): m for m in FORECAST_MODELS}
    selected = []
    for token in wanted:
        if token in MODELS_BY_ID:
            selected.append(MODELS_BY_ID[token])
        elif token in by_label:
            selected.append(by_label[token])
    # Reihenfolge wie in FORECAST_MODELS, Duplikate raus
    ordered = [m for m in FORECAST_MODELS if m in selected]
    return ordered or list(FORECAST_MODELS)


def _label_for(geo: GeoResult) -> str:
    return geo.name + (f", {geo.country}" if geo.country else "")


async def _resolve_place(cfg, query, cache):
    """Gibt (GeoResult, Label) zurueck oder (None, fehlermeldung).

    Konfigurierte Orte kommen ohne Netz aus; Geocoding-Ergebnisse werden
    gecacht (spart mobile Daten bei wiederholten Abfragen).
    """
    from .config import resolve_place

    place = resolve_place(cfg, query)
    if place:
        geo = GeoResult(name=place.name, lat=place.lat, lon=place.lon, country=place.country)
        return geo, _label_for(geo)

    q = (query or cfg.default).strip()
    key = Cache.make_key("geocode", q.lower())
    cached = cache.get(key)
    if cached:
        geo = GeoResult(**cached)
        return geo, _label_for(geo)

    import httpx
    from .api import USER_AGENT
    async with httpx.AsyncClient(timeout=httpx.Timeout(20.0, connect=10.0),
                                 headers={"User-Agent": USER_AGENT}) as client:
        geo = await geocode(client, q)
    if not geo:
        return None, f"Ort nicht gefunden: {q!r}"
    cache.set(key, geo.__dict__)
    return geo, _label_for(geo)


async def _fetch_everything(cache, models, geo, days, args):
    tasks = {"forecast": fetch_all_forecast(cache, models, geo.lat, geo.lon, days)}
    if args.ensemble:
        tasks["ensemble"] = fetch_ensemble(cache, ENSEMBLE_MODELS, geo.lat, geo.lon, days)
    if args.air:
        tasks["air"] = fetch_air_quality(cache, geo.lat, geo.lon, days)
    if args.marine:
        tasks["marine"] = fetch_marine(cache, geo.lat, geo.lon, days)
    keys = list(tasks)
    results = await asyncio.gather(*tasks.values())
    return dict(zip(keys, results))


async def _main_async(args) -> int:
    cfg = load_config()
    out = Console(width=args.width)   # stdout (Ergebnis); width=None => auto
    err = Console(stderr=True)        # stderr (Status/Fehler)

    if args.list_places:
        out.print(f"[bold]Konfiguration:[/bold] {config_path()}")
        out.print(f"[bold]Standard:[/bold] {cfg.default}\n")
        for key, pl in cfg.places.items():
            out.print(f"  [cyan]{key}[/cyan]  {pl.name} ({pl.lat:.3f}, {pl.lon:.3f})"
                      + (f" · {pl.country}" if pl.country else ""))
        return 0

    if args.clear_cache:
        with Cache(cache_path()) as c:
            c.clear()
        out.print("[green]Cache geleert.[/green]")
        return 0

    days = max(1, min(16, args.days or cfg.forecast_days))
    display_hours = max(6, args.hours or cfg.display_hours)
    models = _select_models(args.models)

    cache = Cache(cache_path(), enabled=not args.no_cache)
    try:
        with err.status("[cyan]Ort auflösen …[/cyan]", spinner="dots"):
            geo, label = await _resolve_place(cfg, args.place, cache)
        if geo is None:
            err.print(f"[bold red]Fehler:[/bold red] {label}")
            return 2
        with err.status(f"[cyan]{len(models)} Modelle parallel abfragen …[/cyan]", spinner="dots"):
            data = await _fetch_everything(cache, models, geo, days, args)
    finally:
        cache.close()

    results = data["forecast"]
    if not any(r.available for r in results):
        err.print(f"[bold red]Keine Modelldaten für {label}.[/bold red] "
                  "Koordinaten prüfen oder anderen Ort wählen.")
        return 3

    report = build_report(
        place_label=label, geo=geo, results=results,
        days=days, display_hours=display_hours,
        ensemble=data.get("ensemble"), air=data.get("air"), marine=data.get("marine"),
    )

    if args.json:
        print(json.dumps(report_to_json(report), ensure_ascii=False, indent=2))
        return 0

    from .render import render_terminal
    render_terminal(out, report)

    if args.html is not None:
        from .htmlout import export_html
        path = args.html or f"wx-{_slug(label) or 'wetter'}.html"
        written = export_html(report, path)
        out.print(f"\n[green]HTML geschrieben:[/green] {written}")

    return 0


def main(argv: list[str] | None = None) -> int:
    args = _build_parser().parse_args(argv)
    try:
        return asyncio.run(_main_async(args))
    except KeyboardInterrupt:
        return 130
    except BrokenPipeError:
        return 0


if __name__ == "__main__":
    sys.exit(main())
