# wx – Multi-Modell-Wetter-CLI

Wetter aus **vielen Modellen gleichzeitig** – direkt im Terminal, mit
Modellvergleich, Unsicherheits-Analyse, Ensemble-Verteilung, Sparkline-Charts,
HTML-Export fürs Handy und JSON-Ausgabe. Alle Datenquellen sind **keyless**
(kein API-Key nötig) und stammen von [Open-Meteo](https://open-meteo.com).

Die Idee: nicht *ein* Modell blind glauben, sondern sehen, **wo sich die
Modelle einig sind und wo nicht** – denn hoher Spread bedeutet geringes
Vertrauen. Zusätzlich wird das KI-Modell **AIFS** dem klassischen **IFS**
gegenübergestellt.

```
wx Frutigen
wx "Punta Gorda" --html
wx Bern --ensemble
wx Zermatt --json
```

---

## Features

| # | Funktion | Beschreibung |
|---|----------|--------------|
| 1 | **Multi-Modell** | Holt dieselben Variablen von bis zu 15 Modellen **parallel** (async). |
| 2 | **Modellvergleich** | Pro Zeitschritt Median, Min, Max und **Spread** über alle Modelle. Hoher Spread wird als *niedriges Vertrauen* markiert. |
| 3 | **KI vs. klassisch** | Stellt **AIFS** (ECMWF-KI) separat dem **IFS** gegenüber und zeigt, wo sie divergieren. |
| 4 | **Ensemble** (`--ensemble`) | GEFS- (31) + ICON-EPS-Member (40), als Verteilung (p10–p90-Band + Median). |
| 5 | **Terminal-Charts** | Unicode-Sparklines, ANSI-farbcodiert, **dynamische Breite** (lesbar ab ~60 Zeichen / Handy-Terminal). |
| 6 | **HTML-Export** (`--html`) | Eine **einzige, eigenständige** Datei mit interaktiven Inline-SVG-Charts (Hover-Tooltip). Kein CDN, keine Build-Tools. |
| 7 | **Cache** | SQLite, **30 Min TTL** – spart mobile Daten bei wiederholten Abfragen. |
| 8 | **JSON** (`--json`) | Vollständige, maschinenlesbare Ausgabe zur Weiterverarbeitung/Analyse. |

Optionale Zusatzmodule: **Luftqualität** (`--air`) und **Marine/Wellen**
(`--marine`).

### Automatische Verfügbarkeitserkennung

Regionale Modelle decken nur ihr Gebiet ab. `wx` fragt jedes Modell einzeln ab
und **überspringt** Modelle, die für den Ort keine Daten liefern (HTTP 400 „No
data") – statt zu crashen. Beispiel real gemessen:

- **Frutigen (CH):** 13 Modelle, `ICON-D2` aktiv, `HRRR` übersprungen (nur USA).
- **Punta Gorda (FL):** `HRRR` aktiv, `ICON-D2` übersprungen (nur Mitteleuropa).

---

## Installation

### Termux (Android) & Linux/macOS

```bash
git clone <repo> && cd <repo>/wx
bash setup.sh
```

`setup.sh` zieht auf Termux fehlende Systempakete (`python`) nach, installiert
die Abhängigkeiten (`httpx`, `rich`) und das `wx`-Kommando und legt die
Standardkonfiguration an. Liegt `wx` danach nicht im `PATH`, ist es unter
`~/.local/bin`:

```bash
export PATH="$HOME/.local/bin:$PATH"
```

### Manuell / ohne Installation

```bash
pip install httpx rich
python3 wx.py Frutigen          # direkt startbar, ohne Installation
```

Voraussetzung: **Python 3.11+** (nutzt das eingebaute `tomllib`).

---

## Nutzung

```
wx [ort] [optionen]

  ort               Config-Key, Ortsname oder frei (per Geocoding).
                    Ohne Angabe: Standardort aus der Config.

  --ensemble        Ensemble-Modus (GEFS/ICON-EPS-Verteilung)
  --html [PFAD]     eigenständige HTML-Datei schreiben (optional Pfad)
  --json            JSON-Ausgabe (keine Farben)
  --days N          Prognosehorizont in Tagen (1–16, modellabhängig)
  --hours N         Stunden im Terminal-Verlauf (Default 48)
  --air             Luftqualität ergänzen
  --marine          Marine (Wellen) ergänzen
  --models A,B      nur diese Modelle (ID oder Kürzel, z.B. aifs,ifs,icon)
  --width N         Ausgabebreite erzwingen
  --no-cache        Cache umgehen
  --clear-cache     Cache leeren und beenden
  --list-places     konfigurierte Orte zeigen
  --version         Version
```

### Beispiele

```bash
wx                          # Standardort (Frutigen)
wx Frutigen                 # benannter Ort aus der Config (ohne Geocoding)
wx "Punta Gorda"            # zweiter Standardort
wx Reykjavik                # beliebiger Ort per Geocoding
wx Bern --ensemble          # Ensemble-Verteilung
wx Zermatt --days 10        # 10-Tage-Horizont
wx Frutigen --air --marine  # mit Luftqualität und Wellen
wx Frutigen --html          # -> wx-frutigen_ch.html
wx Frutigen --models aifs,ifs   # nur KI vs. klassisch
wx Zermatt --json | jq .confidence   # Weiterverarbeitung
```

### So sieht die Terminal-Ausgabe aus (schmal, ~60 Zeichen)

```
╭──────────── wx – Multi-Modell-Wetter ────────────╮
│ 📍 Frutigen, CH                                   │
│    46.587, 7.649                                  │
│ Stand 2026-07-21 21:12 · Horizont 7 d · 13 Modelle│
│ Modelle: AIFS IFS GFS ICON ICON-D2 GEM MF-ARP …   │
│  Übersprungen: ACCESS, HRRR                       │
╰───────────────────────────────────────────────────╯
 Temperatur [°C]  10.7–23.0   Vertrauen niedrig
 ▅▄▃▃▂▂▁▁▁▁▂▃▄▅▆▇▇█████▇▆▆▄▃▄▃▃▂▃▃▂▃▄▅▆▆▇▇█████▇▇
 Modell-Uneinigkeit (Temp-Spread, höher = unsicherer)
 ▅▆▇▇▆▆▆▆▆▅▄▄▄▄▄▃▃▃▃▃▃▄▅▅▆▇██▇▇▇▇▇▅▄▄▅▄▄▄▄▄▄▄▄▅▅▆
 …
 KI vs. klassisch · AIFS ↔ IFS
 Temperatur  AIFS kühler · Ø|Δ| 2.4°C · max |Δ| 6.4°C @ Sa 11h
```

Im Terminal sind die Sparklines zusätzlich **farbcodiert** (Temperatur von
blau→rot, Niederschlag von grau→blau→violett).

---

## Der Analyse-Workflow mit Claude Code

Kernidee des Projekts: In Claude Code fragst du z.B.

> „Wie wird das Wetter am Wochenende in Frutigen?"

Claude führt dann `wx Frutigen --json` aus, **liest die Rohdaten** und gibt eine
**meteorologische Einschätzung** – wo sich die Modelle einig sind, wo nicht, was
die CAPE fürs Gewitterrisiko bedeutet und wie verlässlich der Zeitraum ist.

**Die Interpretation kommt von Claude, nicht vom Skript.** Das Skript liefert
die Zahlen (Median/Spread/Divergenz/Ensemble), die Deutung übernimmt das Modell.

Das `--json` enthält dafür alles: pro-Modell-Rohwerte, Aggregate, Vertrauens-
Kennzahlen, AIFS-vs-IFS-Divergenz, Tages-Zusammenfassung und (mit `--ensemble`)
die Ensemble-Verteilung.

---

## Konfiguration

Beim ersten Start wird `~/.config/wx/config.toml` (bzw.
`$XDG_CONFIG_HOME/wx/config.toml`) mit zwei Standardorten angelegt. Vorlage:
siehe [`config.example.toml`](config.example.toml).

```toml
default = "frutigen"
forecast_days = 7
display_hours = 48

[places.frutigen]
name = "Frutigen"
lat = 46.587
lon = 7.649
country = "CH"

[places.punta_gorda]
name = "Punta Gorda"
lat = 26.93
lon = -82.05
country = "US"
```

Konfigurierte Orte werden **ohne Geocoding** aufgelöst (schneller, spart eine
Anfrage). `wx --list-places` zeigt alle Orte.

---

## Modelle & Variablen

**Abgefragte Variablen:** `temperature_2m`, `precipitation`,
`precipitation_probability`, `windspeed_10m`, `windgusts_10m`, `cloudcover`,
`cape`, `relative_humidity_2m`.

**Modelle** (Open-Meteo Forecast API, jeweils einzeln parallel abgefragt):

| Kürzel | Modell-ID | Betreiber | Typ |
|--------|-----------|-----------|-----|
| AIFS | `ecmwf_aifs025_single` | ECMWF | **KI** |
| IFS | `ecmwf_ifs025` | ECMWF | global |
| GFS | `gfs_seamless` | NOAA | global |
| ICON | `icon_seamless` | DWD | global |
| ICON-D2 | `icon_d2` | DWD | regional (Mitteleuropa) |
| GEM | `gem_seamless` | MSC Kanada | global |
| MF-ARP | `meteofrance_seamless` | Météo-France | global + EU |
| UKMO | `ukmo_seamless` | Met Office | global + UK |
| JMA | `jma_seamless` | JMA | global |
| KNMI | `knmi_seamless` | KNMI | regional |
| DMI | `dmi_seamless` | DMI | regional (Nordeuropa) |
| MET-NO | `metno_seamless` | MET Norwegen | regional |
| ACCESS | `bom_access_global` | BOM Australien | global |
| GRAPES | `cma_grapes_global` | CMA China | global |
| HRRR | `gfs_hrrr` | NOAA | regional (USA/CONUS) |

> Hinweis: AIFS ist ein reines KI-Modell und liefert **kein** CAPE bzw. keine
> Niederschlagswahrscheinlichkeit – diese Felder bleiben für AIFS leer.
> Die aktuelle Modellliste steht in den
> [Open-Meteo-Docs](https://open-meteo.com/en/docs); neue Modelle lassen sich in
> `wxcli/models.py` ergänzen.

**Ensemble** (`--ensemble`, Ensemble-API): `gfs_seamless` (GEFS, 31 Member) und
`icon_seamless` (ICON-EPS, 40 Member).

---

## Cache

SQLite unter `~/.cache/wx/cache.sqlite`, **30 Minuten TTL**. Gecacht werden
Modellantworten *und* „nicht verfügbar"-Ergebnisse (damit Regionalmodelle
ausserhalb ihres Gebiets nicht wiederholt angefragt werden) sowie
Geocoding-Treffer. `--no-cache` umgeht den Cache, `--clear-cache` leert ihn.

---

## Datenquellen

Alle **keyless** von Open-Meteo:

- **Forecast API** (`api.open-meteo.com`) – Hauptquelle, alle Modelle einzeln.
- **Ensemble API** (`ensemble-api.open-meteo.com`) – Spread/Unsicherheit.
- **Air-Quality API** (`air-quality-api.open-meteo.com`) – optional (`--air`).
- **Marine API** (`marine-api.open-meteo.com`) – optional (`--marine`).
- **Geocoding API** (`geocoding-api.open-meteo.com`) – Ortssuche.

Bitte die [Open-Meteo-Nutzungsbedingungen](https://open-meteo.com/en/terms)
beachten (freie Nutzung für nicht-kommerzielle Zwecke).

---

## Technik

- **Python 3.11+**, `httpx` (async, parallele Requests), `rich` (Terminal).
- Sauberes Error-Handling: ein für die Region nicht verfügbares Modell wird
  übersprungen, nicht als Absturz behandelt.
- Modularer Aufbau (`wxcli/`): `models`, `config`, `cache`, `api`, `stats`,
  `charts`, `report`, `render`, `htmlout`, `cli`.

## Lizenz

MIT.
