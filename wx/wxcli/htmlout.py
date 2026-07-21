"""Eigenstaendiger HTML-Export mit Inline-SVG-Charts.

Eine einzige, in sich geschlossene Datei (kein CDN, keine Build-Tools) mit
interaktiven Charts (Hover-Tooltip via minimalem Vanilla-JS). Mobil-first,
dunkles Theme – zum Oeffnen im Handy-Browser.
"""

from __future__ import annotations

import html
import json
from pathlib import Path

from . import charts
from .models import MODELS_BY_ID, VARS_BY_ID
from .report import Report
from .stats import now_index, parse_time

WEEKDAYS = ["Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"]

# Interne SVG-Koordinaten (mobil-first). Skaliert per CSS auf Containerbreite.
W = 360
H = 200
PAD_L, PAD_R, PAD_T, PAD_B = 30, 8, 12, 20
PLOT_W = W - PAD_L - PAD_R
PLOT_H = H - PAD_T - PAD_B


def _esc(s: str) -> str:
    return html.escape(str(s), quote=True)


def _x(i: int, n: int) -> float:
    if n <= 1:
        return PAD_L
    return PAD_L + i / (n - 1) * PLOT_W


def _yscale(vmin: float, vmax: float):
    if vmax <= vmin:
        vmax = vmin + 1
    span = vmax - vmin

    def y(v: float) -> float:
        return PAD_T + (1 - (v - vmin) / span) * PLOT_H

    return y


def _polyline(series: list[float | None], n: int, y) -> str:
    """Erzeugt eine SVG-Polyline (Luecken bei None trennen den Pfad)."""
    segs: list[str] = []
    cur: list[str] = []
    for i, v in enumerate(series):
        if v is None:
            if len(cur) > 1:
                segs.append(" ".join(cur))
            cur = []
            continue
        cur.append(f"{_x(i, n):.1f},{y(v):.1f}")
    if len(cur) > 1:
        segs.append(" ".join(cur))
    return " ".join(f'<polyline points="{s}"/>' for s in segs)


def _band_path(lo: list[float | None], hi: list[float | None], n: int, y) -> str:
    top: list[str] = []
    bot: list[str] = []
    for i in range(n):
        if lo[i] is None or hi[i] is None:
            continue
        top.append(f"{_x(i, n):.1f},{y(hi[i]):.1f}")
        bot.append(f"{_x(i, n):.1f},{y(lo[i]):.1f}")
    if len(top) < 2:
        return ""
    pts = top + list(reversed(bot))
    return f'<path d="M {" L ".join(pts)} Z"/>'


def _y_axis(vmin: float, vmax: float, y, unit: str) -> str:
    out: list[str] = []
    for k in range(5):
        val = vmin + (vmax - vmin) * k / 4
        yy = y(val)
        out.append(f'<line class="grid" x1="{PAD_L}" y1="{yy:.1f}" x2="{W - PAD_R}" y2="{yy:.1f}"/>')
        out.append(f'<text class="ylab" x="{PAD_L - 3}" y="{yy + 3:.1f}">{val:.0f}</text>')
    return "".join(out)


def _x_ticks(times: list[str], n: int) -> str:
    out: list[str] = []
    last_day = None
    for i, t in enumerate(times):
        day = t[:10]
        if day != last_day:
            last_day = day
            try:
                dt = parse_time(t)
                lab = WEEKDAYS[dt.weekday()]
            except ValueError:
                lab = day[5:]
            xx = _x(i, n)
            out.append(f'<line class="daysep" x1="{xx:.1f}" y1="{PAD_T}" x2="{xx:.1f}" y2="{PAD_T + PLOT_H}"/>')
            out.append(f'<text class="xlab" x="{xx + 2:.1f}" y="{H - 6}">{lab}</text>')
    return "".join(out)


def _svg_card(cid: str, title: str, subtitle: str, body: str) -> str:
    return (
        f'<div class="card"><h2>{_esc(title)}</h2>'
        f'<div class="sub">{subtitle}</div>'
        f'<svg id="{cid}" viewBox="0 0 {W} {H}" preserveAspectRatio="xMidYMid meet" '
        f'class="chart" role="img">{body}'
        f'<line class="cursor" id="{cid}-cur" x1="0" y1="{PAD_T}" x2="0" y2="{PAD_T + PLOT_H}" style="display:none"/>'
        f'</svg><div class="tip" id="{cid}-tip"></div></div>'
    )


# --- Karten -------------------------------------------------------------------

def _temp_card(report: Report, sl: slice, js: dict) -> str:
    times = report.times[sl]
    n = len(times)
    if n < 2:
        return ""
    cells = report.agg.get("temperature_2m", [])[sl]
    med = [c.median for c in cells]
    lo = [c.vmin for c in cells]
    hi = [c.vmax for c in cells]
    real = [v for v in (med + lo + hi) if v is not None]
    if not real:
        return ""
    vmin, vmax = min(real), max(real)
    pad = (vmax - vmin) * 0.1 or 1
    vmin, vmax = vmin - pad, vmax + pad
    y = _yscale(vmin, vmax)

    layers = [f'<g class="axis">{_y_axis(vmin, vmax, y, "°C")}{_x_ticks(times, n)}</g>']
    layers.append(f'<g class="band temp">{_band_path(lo, hi, n, y)}</g>')
    # Modell-Spaghetti (duenn, transparent)
    spa: list[str] = []
    for r in report.results:
        if not r.available:
            continue
        s = r.hourly.get("temperature_2m")
        if not s:
            continue
        s2 = [(s[i] if i < len(s) else None) for i in range(report.now_idx, report.now_idx + n)]
        spa.append(_polyline(s2, n, y))
    layers.append(f'<g class="spaghetti">{"".join(spa)}</g>')
    layers.append(f'<g class="median temp">{_polyline(med, n, y)}</g>')

    js[f"{report._safe}temp"] = {
        "times": times, "median": med, "lo": lo, "hi": hi, "unit": "°C",
        "n": [c.n for c in cells],
    }
    sub = "Median (kräftig), Min–Max-Band (Fläche), einzelne Modelle (dünn)"
    return _svg_card(f"{report._safe}temp", "Temperatur", sub, "".join(layers))


def _precip_card(report: Report, sl: slice, js: dict) -> str:
    times = report.times[sl]
    n = len(times)
    if n < 2:
        return ""
    cells = report.agg.get("precipitation", [])[sl]
    med = [c.median for c in cells]
    hi = [c.vmax for c in cells]
    pcells = report.agg.get("precipitation_probability", [])[sl]
    pop = [c.median for c in pcells]
    real = [v for v in (med + hi) if v is not None]
    vmax = max(real) if real else 1
    vmax = max(vmax, 1)
    y = _yscale(0, vmax)

    bars: list[str] = []
    bw = max(0.6, PLOT_W / n * 0.7)
    for i, v in enumerate(med):
        if v is None or v <= 0:
            continue
        xx = _x(i, n)
        yy = y(v)
        col = charts.color_for(v, charts.PRECIP_STOPS)
        bars.append(f'<rect x="{xx - bw / 2:.1f}" y="{yy:.1f}" width="{bw:.1f}" '
                    f'height="{PAD_T + PLOT_H - yy:.1f}" fill="{col}"/>')
    # Max-Whisker (obere Grenze) als feine Linie
    whisk = _polyline(hi, n, y)
    axis = f'<g class="axis">{_y_axis(0, vmax, y, "mm")}{_x_ticks(times, n)}</g>'
    body = axis + f'<g class="bars">{"".join(bars)}</g><g class="whisker">{whisk}</g>'

    js[f"{report._safe}precip"] = {
        "times": times, "median": med, "hi": hi, "pop": pop, "unit": "mm",
        "n": [c.n for c in cells],
    }
    return _svg_card(f"{report._safe}precip", "Niederschlag",
                     "Balken = Median mm/h, Linie = Modell-Maximum, Tooltip zeigt Regenwahrsch.",
                     body)


def _aifs_card(report: Report, sl: slice, js: dict) -> str:
    d = report.divergences.get("temperature_2m")
    if not d or not d.ok:
        return ""
    times = report.times[sl]
    n = len(times)
    aifs = d.aifs[sl]
    ifs = d.ifs[sl]
    real = [v for v in (aifs + ifs) if v is not None]
    if len(real) < 2:
        return ""
    vmin, vmax = min(real), max(real)
    pad = (vmax - vmin) * 0.1 or 1
    y = _yscale(vmin - pad, vmax + pad)
    axis = f'<g class="axis">{_y_axis(vmin - pad, vmax + pad, y, "°C")}{_x_ticks(times, n)}</g>'
    body = (axis
            + f'<g class="aifs">{_polyline(aifs, n, y)}</g>'
            + f'<g class="ifs">{_polyline(ifs, n, y)}</g>')
    js[f"{report._safe}aifs"] = {
        "times": times, "aifs": aifs, "ifs": ifs, "unit": "°C",
    }
    sub = (f'<span class="k aifs">AIFS (KI)</span> <span class="k ifs">IFS (klassisch)</span> · '
           f'{_esc(d.tendency)}, Ø|Δ| {d.mean_abs:g}°C, max {d.max_abs:g}°C')
    return _svg_card(f"{report._safe}aifs", "KI vs. klassisch – AIFS ↔ IFS", sub, body)


def _ensemble_card(report: Report, js: dict) -> str:
    if not report.ens_dist or not report.ensemble:
        return ""
    ens = report.ensemble
    e_now = now_index(ens.time, ens.utc_offset_seconds)
    cards: list[str] = []
    for var_id, cells in report.ens_dist.items():
        var = VARS_BY_ID[var_id]
        sub_cells = cells[e_now:]
        times = ens.time[e_now:]
        n = len(times)
        if n < 2:
            continue
        p50 = [c.p50 for c in sub_cells]
        p10 = [c.p10 for c in sub_cells]
        p90 = [c.p90 for c in sub_cells]
        real = [v for v in (p10 + p90 + p50) if v is not None]
        if len(real) < 2:
            continue
        vmin, vmax = min(real), max(real)
        pad = (vmax - vmin) * 0.1 or 1
        y = _yscale(vmin - pad, vmax + pad)
        axis = f'<g class="axis">{_y_axis(vmin - pad, vmax + pad, y, var.unit)}{_x_ticks(times, n)}</g>'
        body = (axis
                + f'<g class="band ens">{_band_path(p10, p90, n, y)}</g>'
                + f'<g class="median ens">{_polyline(p50, n, y)}</g>')
        cid = f"{report._safe}ens_{var_id}"
        js[cid] = {"times": times, "median": p50, "lo": p10, "hi": p90, "unit": var.unit,
                   "n": [c.n for c in sub_cells]}
        counts = ", ".join(f"{t}: {c}" for t, c in report.ens_counts.items())
        cards.append(_svg_card(cid, f"Ensemble · {var.label}",
                               f"p10–p90-Band + Median (p50) · {counts} Member", body))
    return "".join(cards)


def _daily_table_html(report: Report) -> str:
    rows: list[str] = []
    for d in report.daily[:12]:
        try:
            dt = parse_time(d.date + "T00:00")
            day = f"{WEEKDAYS[dt.weekday()]} {dt.day:02d}.{dt.month:02d}"
        except ValueError:
            day = d.date
        tcol = "—"
        if d.tmin is not None and d.tmax is not None:
            c1 = charts.color_for(d.tmin, charts.TEMP_STOPS)
            c2 = charts.color_for(d.tmax, charts.TEMP_STOPS)
            tcol = (f'<span style="color:{c1}">{d.tmin:.0f}</span>–'
                    f'<span style="color:{c2}">{d.tmax:.0f}</span>')
        conf = '<span class="dot low">●</span>' if d.low_conf else '<span class="dot ok">●</span>'
        rows.append(
            f"<tr><td>{_esc(day)}</td><td>{tcol}</td>"
            f"<td>{'—' if d.precip is None else f'{d.precip:.1f}'}</td>"
            f"<td>{'—' if d.pop is None else f'{d.pop:.0f}%'}</td>"
            f"<td>{'—' if d.gust is None else f'{d.gust:.0f}'}</td>"
            f"<td>{'—' if d.cape is None else f'{d.cape:.0f}'}</td>"
            f"<td>{conf}</td></tr>"
        )
    return (
        '<div class="card"><h2>Tagesübersicht</h2>'
        '<div class="sub">Median aller Modelle · <span class="dot ok">●</span> Modelle einig · '
        '<span class="dot low">●</span> hohe Streuung</div>'
        '<table><thead><tr><th>Tag</th><th>T °C</th><th>mm</th><th>PoP</th>'
        '<th>Böen</th><th>CAPE</th><th>Vert.</th></tr></thead>'
        f'<tbody>{"".join(rows)}</tbody></table></div>'
    )


def _models_html(report: Report) -> str:
    chips: list[str] = []
    for mid in report.available_models:
        m = MODELS_BY_ID.get(mid)
        if not m:
            continue
        chips.append(f'<span class="chip {m.kind}" title="{_esc(m.agency)} · {_esc(m.region)}">'
                     f'{_esc(m.label)}</span>')
    skipped = ""
    if report.skipped:
        names = ", ".join(MODELS_BY_ID.get(mid).label if mid in MODELS_BY_ID else mid
                          for mid, _ in report.skipped)
        skipped = f'<div class="skipped">Übersprungen (kein Datum am Ort): {_esc(names)}</div>'
    return f'<div class="chips">{"".join(chips)}</div>{skipped}'


CSS = """
:root{--bg:#0f1420;--card:#171d2b;--fg:#e7ecf3;--dim:#8794a8;--line:#263042;--accent:#4aa3ff}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--fg);font:15px/1.5 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif;padding:14px;max-width:820px;margin:0 auto}
h1{font-size:20px;margin:0 0 2px}h2{font-size:16px;margin:0 0 2px}
.meta{color:var(--dim);font-size:13px;margin-bottom:12px}
.card{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:12px;margin:12px 0;position:relative}
.sub{color:var(--dim);font-size:12px;margin-bottom:8px}
.chart{width:100%;height:auto;display:block;touch-action:none}
.grid{stroke:var(--line);stroke-width:.5}
.daysep{stroke:var(--line);stroke-width:.5;stroke-dasharray:2 2}
.ylab{fill:var(--dim);font-size:7px;text-anchor:end}
.xlab{fill:var(--dim);font-size:7px}
.band.temp path{fill:#e67e2233;stroke:none}
.band.ens path{fill:#2ecc7133;stroke:none}
.spaghetti polyline{fill:none;stroke:#6f7d92;stroke-width:.4;opacity:.5}
.median.temp polyline{fill:none;stroke:#ff9d4d;stroke-width:1.6}
.median.ens polyline{fill:none;stroke:#2ecc71;stroke-width:1.6}
.whisker polyline{fill:none;stroke:#5dade2;stroke-width:.5;opacity:.6}
.aifs polyline{fill:none;stroke:#c56cff;stroke-width:1.5}
.ifs polyline{fill:none;stroke:#4aa3ff;stroke-width:1.5}
.cursor{stroke:#ffffff88;stroke-width:.6}
.tip{position:absolute;pointer-events:none;background:#0b0f18ee;border:1px solid var(--line);border-radius:8px;padding:6px 8px;font-size:12px;display:none;z-index:5;white-space:nowrap}
.chips{display:flex;flex-wrap:wrap;gap:6px}
.chip{font-size:12px;padding:2px 8px;border-radius:999px;border:1px solid var(--line)}
.chip.ai{background:#c56cff22;border-color:#c56cff}
.chip.global{background:#4aa3ff22;border-color:#4aa3ff}
.chip.regional{background:#f1c40f22;border-color:#f1c40f}
.skipped{color:var(--dim);font-size:12px;margin-top:6px}
.k{font-size:12px;padding:1px 6px;border-radius:6px}
.k.aifs{background:#c56cff33}.k.ifs{background:#4aa3ff33}
table{width:100%;border-collapse:collapse;font-size:13px}
th,td{text-align:right;padding:5px 6px;border-bottom:1px solid var(--line)}
th:first-child,td:first-child{text-align:left}
.dot.ok{color:#2ecc71}.dot.low{color:#e74c3c}
footer{color:var(--dim);font-size:12px;margin:16px 0}
"""

JS = r"""
(function(){
  var D = window.WXDATA || {};
  function fmt(v,u){ return v==null? '—' : (Math.round(v*10)/10)+u; }
  Object.keys(D).forEach(function(cid){
    var svg=document.getElementById(cid); if(!svg) return;
    var tip=document.getElementById(cid+'-tip');
    var cur=document.getElementById(cid+'-cur');
    var d=D[cid], n=d.times.length;
    var PADL=%PADL%, PADR=%PADR%, W=%W%;
    function idxFromX(clientX){
      var r=svg.getBoundingClientRect();
      var vx=(clientX-r.left)/r.width*W;
      var frac=(vx-PADL)/(W-PADL-PADR);
      var i=Math.round(frac*(n-1));
      return Math.max(0,Math.min(n-1,i));
    }
    function show(clientX){
      var i=idxFromX(clientX), t=d.times[i];
      var vx=PADL + i/(n-1)*(W-PADL-PADR);
      cur.setAttribute('x1',vx); cur.setAttribute('x2',vx); cur.style.display='';
      var lines=['<b>'+t.replace('T',' ')+'</b>'];
      if('median'in d) lines.push('Median '+fmt(d.median[i],d.unit));
      if('lo'in d && d.lo[i]!=null) lines.push('Spanne '+fmt(d.lo[i],'')+'–'+fmt(d.hi[i],d.unit));
      if('aifs'in d) lines.push('AIFS '+fmt(d.aifs[i],d.unit)+' · IFS '+fmt(d.ifs[i],d.unit));
      if('pop'in d && d.pop[i]!=null) lines.push('Regenwahrsch. '+Math.round(d.pop[i])+'%');
      if('n'in d && d.n[i]!=null) lines.push('<span style="opacity:.6">'+d.n[i]+' Modelle</span>');
      tip.innerHTML=lines.join('<br>');
      tip.style.display='block';
      var r=svg.getBoundingClientRect();
      var px=(vx/W)*r.width;
      tip.style.left=Math.min(r.width-tip.offsetWidth-4, Math.max(4,px+8))+'px';
      tip.style.top='28px';
    }
    function hide(){ tip.style.display='none'; cur.style.display='none'; }
    svg.addEventListener('mousemove',function(e){show(e.clientX);});
    svg.addEventListener('mouseleave',hide);
    svg.addEventListener('touchstart',function(e){show(e.touches[0].clientX);},{passive:true});
    svg.addEventListener('touchmove',function(e){show(e.touches[0].clientX);},{passive:true});
  });
})();
"""


def export_html(report: Report, path: str | Path) -> Path:
    # Volle Prognose ab jetzt fuers HTML (auf grossem Screen scrollbar/lesbar).
    sl = slice(report.now_idx, len(report.times))
    js: dict = {}

    body_parts = [
        f"<h1>📍 {_esc(report.place_label)}</h1>",
        f'<div class="meta">{report.lat:.3f}, {report.lon:.3f}'
        + (f" · {report.elevation:.0f} m" if report.elevation is not None else "")
        + f" · Stand {_esc(report.generated_local)} ({_esc(report.timezone)}) · "
        + f"Horizont {report.days} d</div>",
        f'<div class="card">{_models_html(report)}</div>',
        _temp_card(report, sl, js),
        _precip_card(report, sl, js),
        _aifs_card(report, sl, js),
        _ensemble_card(report, js),
        _daily_table_html(report),
    ]

    if report.air and report.air.ok:
        h = report.air.hourly
        parts = []
        for key, label in (("european_aqi", "AQI (EU)"), ("pm2_5", "PM2.5"),
                           ("pm10", "PM10"), ("uv_index", "UV-Index")):
            val = next((v for v in h.get(key, []) if v is not None), None)
            if val is not None:
                parts.append(f"{label}: <b>{val:g}</b>")
        if parts:
            body_parts.append(f'<div class="card"><h2>Luftqualität</h2><div class="sub">'
                              + " · ".join(parts) + "</div></div>")
    if report.marine and report.marine.ok:
        h = report.marine.hourly
        wh = next((v for v in h.get("wave_height", []) if v is not None), None)
        wp = next((v for v in h.get("wave_period", []) if v is not None), None)
        if wh is not None:
            body_parts.append(f'<div class="card"><h2>Marine</h2><div class="sub">'
                              f'Wellenhöhe: <b>{wh:g} m</b>'
                              + (f' · Periode: <b>{wp:g} s</b>' if wp is not None else "")
                              + "</div></div>")

    body_parts.append('<footer>Erzeugt mit <b>wx</b> · Datenquelle: Open-Meteo · '
                      'Alle Modelle keyless, parallel abgefragt.</footer>')

    js_blob = json.dumps(js, ensure_ascii=False)
    script = (JS.replace("%PADL%", str(PAD_L)).replace("%PADR%", str(PAD_R)).replace("%W%", str(W)))
    doc = (
        "<!doctype html><html lang=de><head><meta charset=utf-8>"
        '<meta name=viewport content="width=device-width,initial-scale=1">'
        f"<title>wx · {_esc(report.place_label)}</title><style>{CSS}</style></head>"
        f"<body>{''.join(p for p in body_parts if p)}"
        f"<script>window.WXDATA={js_blob};</script><script>{script}</script>"
        "</body></html>"
    )
    p = Path(path)
    p.write_text(doc, encoding="utf-8")
    return p
