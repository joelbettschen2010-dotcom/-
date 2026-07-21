"""Terminal-Ausgabe mit ``rich``: Panels, Sparklines und Tabellen.

Alles ist vertikal gestapelt und passt sich der Breite an, damit es auch auf
einem schmalen Handy-Terminal (~60 Zeichen) lesbar bleibt.
"""

from __future__ import annotations

import shutil

from rich.console import Console, Group
from rich.panel import Panel
from rich.table import Table
from rich.text import Text

from . import charts
from .models import MODELS_BY_ID, VARS_BY_ID
from .report import Report
from .stats import parse_time

WEEKDAYS = ["Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"]
KIND_STYLE = {"ai": "bold magenta", "global": "cyan", "regional": "yellow"}
CONF_STYLE = {"hoch": "bold green", "mittel": "yellow", "niedrig": "bold red", "n/a": "dim"}


def _fmt_dt(t: str, with_time: bool = True) -> str:
    try:
        dt = parse_time(t)
    except ValueError:
        return t
    wd = WEEKDAYS[dt.weekday()]
    return f"{wd} {dt.hour:02d}h" if with_time else f"{wd} {dt.day:02d}.{dt.month:02d}"


def effective_width(console: Console) -> int:
    w = console.width or shutil.get_terminal_size((80, 24)).columns
    return max(40, w)


def _model_roster(report: Report) -> Text:
    t = Text()
    t.append("Modelle: ", style="bold")
    for i, mid in enumerate(report.available_models):
        m = MODELS_BY_ID.get(mid)
        label = m.label if m else mid
        style = KIND_STYLE.get(m.kind, "white") if m else "white"
        t.append(label, style=style)
        if i < len(report.available_models) - 1:
            t.append(" ")
    if report.skipped:
        t.append("\n Übersprungen: ", style="dim")
        t.append(", ".join(MODELS_BY_ID.get(mid, None).label if mid in MODELS_BY_ID else mid
                            for mid, _ in report.skipped), style="dim strike")
    return t


def _header(report: Report) -> Panel:
    loc = Text()
    loc.append("📍 ", style="")
    loc.append(report.place_label, style="bold white")
    coords = f"{report.lat:.3f}, {report.lon:.3f}"
    if report.elevation is not None:
        coords += f" · {report.elevation:.0f} m"
    loc.append(f"\n   {coords}", style="dim")
    if report.admin1:
        loc.append(f" · {report.admin1}", style="dim")

    meta = Text()
    meta.append(f"\nStand {report.generated_local}", style="")
    if report.timezone:
        meta.append(f" · {report.timezone}", style="dim")
    meta.append(f" · Horizont {report.days} d", style="dim")
    meta.append(f" · {len(report.available_models)} Modelle", style="dim")
    if report.from_cache:
        meta.append(" · 💾 Cache", style="dim italic")

    return Panel(
        Group(loc, meta, Text(), _model_roster(report)),
        title="[bold]wx – Multi-Modell-Wetter[/bold]",
        border_style="blue",
    )


def _sparkline_block(report: Report, var_id: str, chart_w: int) -> Group:
    var = VARS_BY_ID[var_id]
    med = report.disp_median(var_id)
    real = [v for v in med if v is not None]
    if not real:
        return Group(Text(f"{var.label}: keine Daten", style="dim"))
    lo, hi = min(real), max(real)

    head = Text()
    head.append(f"{var.label} ", style="bold")
    head.append(f"[{var.unit}]  ", style="dim")
    head.append(f"{lo:.1f}–{hi:.1f}", style="")
    conf = report.confidences.get(var_id)
    if conf and conf.label != "n/a":
        head.append("   Vertrauen ", style="dim")
        head.append(conf.label, style=CONF_STYLE.get(conf.label, "white"))
        if conf.mean_spread is not None:
            head.append(f" (Ø-Spread {conf.mean_spread:g}{var.unit})", style="dim")

    spark = charts.sparkline_text(med, chart_w, var_id=var_id, lo=lo, hi=hi)

    # Zeitachse: drei Marken (Start / Mitte / Ende).
    dt = report.disp_times
    axis = Text()
    if dt:
        left = _fmt_dt(dt[0])
        mid = _fmt_dt(dt[len(dt) // 2])
        right = _fmt_dt(dt[-1])
        pad = max(1, chart_w - len(left) - len(mid) - len(right))
        axis.append(left, style="dim")
        axis.append(" " * (pad // 2))
        axis.append(mid, style="dim")
        axis.append(" " * (pad - pad // 2))
        axis.append(right, style="dim")

    return Group(head, spark, axis, Text())


def _spread_hint(report: Report, chart_w: int) -> Group:
    """Sparkline der Temperatur-Streuung: wo sind sich die Modelle uneinig?"""
    spread = report.disp_spread("temperature_2m")
    real = [v for v in spread if v is not None]
    if not real:
        return Group()
    head = Text()
    head.append("Modell-Uneinigkeit ", style="bold")
    head.append("(Temp-Spread, höher = unsicherer)", style="dim")
    spark = charts.sparkline_text(spread, chart_w, var_id="generic", lo=0, hi=max(real))
    return Group(head, spark, Text())


def _daily_table(report: Report) -> Table:
    t = Table(title="Tagesübersicht (Median aller Modelle)", title_style="bold",
              expand=False, pad_edge=False, show_edge=True, border_style="dim")
    t.add_column("Tag", style="bold")
    t.add_column("T °C", justify="right")
    t.add_column("mm", justify="right")
    t.add_column("PoP", justify="right")
    t.add_column("Böen", justify="right")
    t.add_column("CAPE", justify="right")
    t.add_column("Vert.", justify="center")

    for d in report.daily[:10]:
        try:
            dt = parse_time(d.date + "T00:00")
            day_label = f"{WEEKDAYS[dt.weekday()]} {dt.day:02d}.{dt.month:02d}"
        except ValueError:
            day_label = d.date
        tcell = Text()
        if d.tmin is not None and d.tmax is not None:
            tcell.append(f"{d.tmin:.0f}", style=charts.color_for(d.tmin, charts.TEMP_STOPS))
            tcell.append("–", style="dim")
            tcell.append(f"{d.tmax:.0f}", style=charts.color_for(d.tmax, charts.TEMP_STOPS))
        else:
            tcell.append("–", style="dim")
        precip = "–" if d.precip is None else f"{d.precip:.1f}"
        pop = "–" if d.pop is None else f"{d.pop:.0f}%"
        gust = "–" if d.gust is None else f"{d.gust:.0f}"
        cape = "–" if d.cape is None else f"{d.cape:.0f}"
        conf = Text("●", style="red") if d.low_conf else Text("●", style="green")
        t.add_row(day_label, tcell, precip, pop, gust, cape, conf)
    return t


def _aifs_vs_ifs(report: Report, chart_w: int) -> Panel:
    parts: list = []
    for var_id in ("temperature_2m", "precipitation"):
        d = report.divergences.get(var_id)
        var = VARS_BY_ID[var_id]
        if not d or not d.ok:
            parts.append(Text(f"{var.label}: AIFS/IFS nicht beide verfügbar", style="dim"))
            continue
        aifs_disp = d.aifs[report.disp_slice]
        ifs_disp = d.ifs[report.disp_slice]
        real = [v for v in (aifs_disp + ifs_disp) if v is not None]
        if not real:
            continue
        lo, hi = min(real), max(real)
        head = Text()
        head.append(f"{var.label}  ", style="bold")
        head.append(f"{d.tendency}", style="bold cyan")
        head.append(f" · Ø|Δ| {d.mean_abs:g}{var.unit}", style="dim")
        head.append(f" · max |Δ| {d.max_abs:g}{var.unit} @ {_fmt_dt(d.max_abs_at or '')}",
                    style="dim")
        line_a = Text("AIFS ", style="magenta")
        line_a.append_text(charts.sparkline_text(aifs_disp, chart_w, var_id=var_id, lo=lo, hi=hi))
        line_i = Text("IFS  ", style="blue")
        line_i.append_text(charts.sparkline_text(ifs_disp, chart_w, var_id=var_id, lo=lo, hi=hi))
        parts.extend([head, line_a, line_i, Text()])
    return Panel(Group(*parts), title="[bold magenta]KI vs. klassisch[/bold magenta] · AIFS ↔ IFS",
                 border_style="magenta")


def _ensemble_block(report: Report, chart_w: int) -> Panel | None:
    if not report.ens_dist:
        return None
    parts: list = []
    counts = ", ".join(f"{tag}: {n}" for tag, n in report.ens_counts.items())
    parts.append(Text(f"Member: {counts}", style="dim"))
    parts.append(Text())
    ens = report.ensemble
    # Ensemble hat eigene Zeitachse -> auf Displayfenster (nach jetzt) beschneiden.
    from .stats import now_index
    e_now = now_index(ens.time, ens.utc_offset_seconds) if ens else 0
    e_end = min(len(ens.time), e_now + report.display_hours) if ens else 0
    sl = slice(e_now, e_end)

    for var_id, cells in report.ens_dist.items():
        var = VARS_BY_ID[var_id]
        sub = cells[sl]
        p50 = [c.p50 for c in sub]
        p10 = [c.p10 for c in sub]
        p90 = [c.p90 for c in sub]
        real = [v for v in p50 if v is not None]
        if not real:
            continue
        band = [(hi - lo) if (lo is not None and hi is not None) else None
                for lo, hi in zip(p10, p90)]
        breal = [v for v in band if v is not None]
        head = Text()
        head.append(f"{var.label} ", style="bold")
        head.append("Median (p50)", style="dim")
        spark = charts.sparkline_text(p50, chart_w, var_id=var_id)
        bhead = Text("  Unsicherheit (p10–p90-Breite)", style="dim")
        bspark = charts.sparkline_text(band, chart_w, var_id="generic",
                                       lo=0, hi=max(breal) if breal else 1)
        parts.extend([head, spark, bhead, bspark, Text()])
    return Panel(Group(*parts), title="[bold]Ensemble-Verteilung[/bold] · GEFS + ICON-EPS",
                 border_style="green")


def _aux_block(report: Report) -> Group | None:
    lines: list = []
    if report.air and report.air.ok:
        h = report.air.hourly
        parts = Text("Luftqualität: ", style="bold")
        for key, label in (("european_aqi", "AQI"), ("pm2_5", "PM2.5"),
                           ("pm10", "PM10"), ("uv_index", "UV")):
            series = h.get(key, [])
            val = next((v for v in series if v is not None), None)
            if val is not None:
                parts.append(f"{label} {val:g}  ", style="")
        lines.append(parts)
    if report.marine and report.marine.ok:
        h = report.marine.hourly
        parts = Text("Marine: ", style="bold")
        wh = next((v for v in h.get("wave_height", []) if v is not None), None)
        wp = next((v for v in h.get("wave_period", []) if v is not None), None)
        if wh is not None:
            parts.append(f"Wellen {wh:g} m  ", style="")
        if wp is not None:
            parts.append(f"Periode {wp:g} s", style="")
        lines.append(parts)
    return Group(*lines) if lines else None


def render_terminal(console: Console, report: Report) -> None:
    width = effective_width(console)
    chart_w = max(20, width - 8)

    console.print(_header(report))
    console.print()

    blocks = [
        _sparkline_block(report, "temperature_2m", chart_w),
        _sparkline_block(report, "precipitation", chart_w),
        _spread_hint(report, chart_w),
    ]
    console.print(Panel(Group(*blocks), title="[bold]Verlauf[/bold] (nächste "
                        f"{report.display_hours} h)", border_style="blue"))
    console.print()
    console.print(_daily_table(report))
    console.print()
    console.print(_aifs_vs_ifs(report, chart_w))

    ens = _ensemble_block(report, chart_w)
    if ens:
        console.print()
        console.print(ens)

    aux = _aux_block(report)
    if aux:
        console.print()
        console.print(aux)

    hint = Text("\nTipp: ", style="dim")
    hint.append("--ensemble", style="green")
    hint.append(" · ", style="dim")
    hint.append("--html", style="cyan")
    hint.append(" · ", style="dim")
    hint.append("--json", style="yellow")
    hint.append(" · ", style="dim")
    hint.append("--air --marine --days N", style="dim")
    console.print(hint)
