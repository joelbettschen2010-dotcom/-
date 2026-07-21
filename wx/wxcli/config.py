"""Konfiguration: Standardorte, Voreinstellungen und Pfade.

Liest eine TOML-Datei unter ``$XDG_CONFIG_HOME/wx/config.toml`` (Fallback
``~/.config/wx/config.toml``). Existiert keine, wird beim ersten Start eine
Vorlage mit den Standardorten Frutigen (CH) und Punta Gorda (FL) geschrieben.
"""

from __future__ import annotations

import os
import re
import tomllib
from dataclasses import dataclass, field
from pathlib import Path


@dataclass(frozen=True)
class Place:
    key: str
    name: str
    lat: float
    lon: float
    country: str = ""


@dataclass
class Config:
    places: dict[str, Place] = field(default_factory=dict)
    default: str = "frutigen"
    forecast_days: int = 7
    display_hours: int = 48
    path: Path | None = None


# In der Vorlage ausgelieferte Standardorte (vom Nutzer gewuenscht).
DEFAULT_TOML = """\
# wx – Konfiguration
# Standardort, wenn `wx` ohne Argument aufgerufen wird:
default = "frutigen"

# Prognosehorizont in Tagen (1–16, modellabhaengig):
forecast_days = 7

# Stunden, die die Terminal-Ansicht ab jetzt zeigt:
display_hours = 48

# Eigene Orte: `wx <key>` oder `wx "<name>"` nutzt diese Koordinaten direkt
# (ohne Geocoding). Weitere Orte lassen sich frei ergaenzen.
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
"""


def config_dir() -> Path:
    base = os.environ.get("XDG_CONFIG_HOME") or str(Path.home() / ".config")
    return Path(base) / "wx"


def cache_dir() -> Path:
    base = os.environ.get("XDG_CACHE_HOME") or str(Path.home() / ".cache")
    return Path(base) / "wx"


def cache_path() -> Path:
    d = cache_dir()
    d.mkdir(parents=True, exist_ok=True)
    return d / "cache.sqlite"


def config_path() -> Path:
    return config_dir() / "config.toml"


def _slug(text: str) -> str:
    return re.sub(r"[^a-z0-9]+", "_", text.lower()).strip("_")


def ensure_config() -> Path:
    """Schreibt bei Bedarf die Vorlage und gibt den Konfigurationspfad zurueck."""
    p = config_path()
    if not p.exists():
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(DEFAULT_TOML, encoding="utf-8")
    return p


def load_config() -> Config:
    """Laedt die Konfiguration (legt bei Bedarf die Vorlage an)."""
    p = ensure_config()
    try:
        data = tomllib.loads(p.read_text(encoding="utf-8"))
    except (tomllib.TOMLDecodeError, OSError):
        data = {}

    places: dict[str, Place] = {}
    for key, entry in (data.get("places") or {}).items():
        try:
            places[key] = Place(
                key=key,
                name=str(entry.get("name", key)),
                lat=float(entry["lat"]),
                lon=float(entry["lon"]),
                country=str(entry.get("country", "")),
            )
        except (KeyError, TypeError, ValueError):
            continue  # unvollstaendigen Ort ueberspringen, nicht crashen

    return Config(
        places=places,
        default=str(data.get("default", "frutigen")),
        forecast_days=int(data.get("forecast_days", 7)),
        display_hours=int(data.get("display_hours", 48)),
        path=p,
    )


def resolve_place(cfg: Config, query: str | None) -> Place | None:
    """Findet einen konfigurierten Ort per Key oder (normalisiertem) Namen.

    Gibt ``None`` zurueck, wenn nichts passt – dann muss geocodiert werden.
    """
    if query is None:
        query = cfg.default
    q = query.strip()
    if q in cfg.places:
        return cfg.places[q]
    qs = _slug(q)
    for place in cfg.places.values():
        if _slug(place.key) == qs or _slug(place.name) == qs:
            return place
    return None
