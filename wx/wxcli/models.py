"""Modell- und Variablen-Registry fuer die Multi-Modell-Wetterabfrage.

Die Modell-IDs entsprechen exakt den `models=`-Parametern der Open-Meteo
Forecast API (https://open-meteo.com/en/docs). Regionale Modelle decken nur
ihr Gebiet ab; ausserhalb liefert die API HTTP 400 ("No data is available"),
was von der Verfuegbarkeitserkennung in api.py behandelt wird.
"""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class Model:
    """Ein Wettermodell aus der Open-Meteo Forecast API."""

    id: str          # Open-Meteo Modell-ID (models=-Parameter)
    label: str       # Kurzbezeichnung fuer die Anzeige
    agency: str      # Betreiber / Wetterdienst
    kind: str        # "ai" | "global" | "regional"
    region: str      # Abdeckungshinweis
    resolution: str  # Gitteraufloesung (Hinweis)


# Reihenfolge = Anzeigereihenfolge. AIFS und IFS bewusst zuerst (KI vs. klassisch).
FORECAST_MODELS: list[Model] = [
    Model("ecmwf_aifs025_single", "AIFS",      "ECMWF",        "ai",       "global",           "0.25°"),
    Model("ecmwf_ifs025",         "IFS",       "ECMWF",        "global",   "global",           "0.25°"),
    Model("gfs_seamless",         "GFS",       "NOAA",         "global",   "global",           "0.11–0.25°"),
    Model("icon_seamless",        "ICON",      "DWD",          "global",   "global",           "0.06–0.13°"),
    Model("icon_d2",              "ICON-D2",   "DWD",          "regional", "Mitteleuropa",     "2 km"),
    Model("gem_seamless",         "GEM",       "MSC Kanada",   "global",   "global",           "0.15°"),
    Model("meteofrance_seamless", "MF-ARP",    "Météo-France", "global",   "global + EU",      "0.25° / 1.5 km"),
    Model("ukmo_seamless",        "UKMO",      "Met Office",   "global",   "global + UK",      "0.09° / 2 km"),
    Model("jma_seamless",         "JMA",       "JMA Japan",    "global",   "global",           "0.25°"),
    Model("knmi_seamless",        "KNMI",      "KNMI NL",      "regional", "NL / Mitteleuropa","2–2.5 km"),
    Model("dmi_seamless",         "DMI",       "DMI Dänemark", "regional", "Nordeuropa",       "2 km"),
    Model("metno_seamless",       "MET-NO",    "MET Norwegen", "regional", "Nordics / Arktis", "1 km"),
    Model("bom_access_global",    "ACCESS",    "BOM Australien","global",  "global",           "0.15°"),
    Model("cma_grapes_global",    "GRAPES",    "CMA China",    "global",   "global",           "0.125°"),
    # Nur USA (CONUS) – wird ausserhalb automatisch uebersprungen (HTTP 400).
    Model("gfs_hrrr",             "HRRR",      "NOAA",         "regional", "USA (CONUS)",      "3 km"),
]

MODELS_BY_ID: dict[str, Model] = {m.id: m for m in FORECAST_MODELS}

# Das KI-Modell und sein klassisches Gegenstueck (fuer die separate Gegenueberstellung).
AI_MODEL_ID = "ecmwf_aifs025_single"
CLASSIC_COUNTERPART_ID = "ecmwf_ifs025"


@dataclass(frozen=True)
class Variable:
    """Eine stuendliche Wettervariable inkl. Anzeige-Metadaten."""

    id: str          # Open-Meteo hourly-Variable
    label: str       # Anzeigename
    unit: str        # Einheit
    fmt: str         # Format-String fuer Einzelwerte
    # Spread-Skala: ab diesem Streuungswert (max-min) gilt das Vertrauen als niedrig.
    low_conf_spread: float


# Genau die vom Nutzer gewuenschten Variablen.
FORECAST_VARS: list[Variable] = [
    Variable("temperature_2m",            "Temperatur",      "°C",   "{:.1f}", 5.0),
    Variable("precipitation",             "Niederschlag",    "mm",   "{:.1f}", 3.0),
    Variable("precipitation_probability", "Regenwahrsch.",   "%",    "{:.0f}", 40.0),
    Variable("windspeed_10m",             "Wind",            "km/h", "{:.0f}", 15.0),
    Variable("windgusts_10m",             "Böen",            "km/h", "{:.0f}", 25.0),
    Variable("cloudcover",                "Bewölkung",       "%",    "{:.0f}", 50.0),
    Variable("cape",                      "CAPE",            "J/kg", "{:.0f}", 800.0),
    Variable("relative_humidity_2m",      "Luftfeuchte",     "%",    "{:.0f}", 25.0),
]

VARS_BY_ID: dict[str, Variable] = {v.id: v for v in FORECAST_VARS}

# Kommaseparierte Liste fuer den hourly=-Parameter.
FORECAST_VARS_PARAM = ",".join(v.id for v in FORECAST_VARS)


# --- Optionale Zusatzmodule ---------------------------------------------------

AIR_QUALITY_VARS: list[Variable] = [
    Variable("pm2_5",        "PM2.5",     "µg/m³", "{:.0f}", 25.0),
    Variable("pm10",         "PM10",      "µg/m³", "{:.0f}", 40.0),
    Variable("european_aqi", "AQI (EU)",  "",      "{:.0f}", 40.0),
    Variable("uv_index",     "UV-Index",  "",      "{:.1f}", 3.0),
    Variable("ozone",        "Ozon",      "µg/m³", "{:.0f}", 60.0),
]
AIR_QUALITY_VARS_PARAM = ",".join(v.id for v in AIR_QUALITY_VARS)

MARINE_VARS: list[Variable] = [
    Variable("wave_height",       "Wellenhöhe",     "m", "{:.1f}", 1.0),
    Variable("wave_period",       "Wellenperiode",  "s", "{:.0f}", 3.0),
    Variable("wave_direction",    "Wellenrichtung", "°", "{:.0f}", 90.0),
]
MARINE_VARS_PARAM = ",".join(v.id for v in MARINE_VARS)


# --- Ensemble-Modelle ---------------------------------------------------------
# Die Ensemble-API (ensemble-api.open-meteo.com) liefert Member-Serien mit
# Suffix _memberNN_<modell>. Diese Modelle bieten grosse Member-Zahlen.
ENSEMBLE_MODELS: list[Model] = [
    Model("gfs_seamless",  "GEFS",     "NOAA", "global", "global (31 Member)", "0.25°"),
    Model("icon_seamless", "ICON-EPS", "DWD",  "global", "global (40 Member)", "0.13°"),
]
ENSEMBLE_VARS: list[Variable] = [
    Variable("temperature_2m",  "Temperatur",   "°C", "{:.1f}", 5.0),
    Variable("precipitation",   "Niederschlag", "mm", "{:.1f}", 3.0),
]
ENSEMBLE_VARS_PARAM = ",".join(v.id for v in ENSEMBLE_VARS)
