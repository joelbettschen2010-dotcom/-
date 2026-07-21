"""SQLite-Cache mit 30-Minuten-TTL.

Spart mobile Daten: identische Abfragen innerhalb der TTL werden aus der
lokalen SQLite-Datei bedient statt erneut ueber das Netz geladen. Auch
"nicht verfuegbar"-Ergebnisse werden gecacht, damit Regionalmodelle
ausserhalb ihres Gebiets nicht wiederholt angefragt werden.
"""

from __future__ import annotations

import hashlib
import json
import sqlite3
import time
from pathlib import Path
from typing import Any

DEFAULT_TTL = 1800  # 30 Minuten in Sekunden


class Cache:
    def __init__(self, path: Path, ttl: float = DEFAULT_TTL, enabled: bool = True):
        self.path = path
        self.ttl = ttl
        self.enabled = enabled
        self._conn: sqlite3.Connection | None = None
        if enabled:
            self._conn = sqlite3.connect(str(path))
            self._conn.execute(
                "CREATE TABLE IF NOT EXISTS cache ("
                "  key TEXT PRIMARY KEY,"
                "  ts REAL NOT NULL,"
                "  payload TEXT NOT NULL"
                ")"
            )
            self._conn.commit()

    @staticmethod
    def make_key(*parts: Any) -> str:
        raw = "\x1f".join(json.dumps(p, sort_keys=True, default=str) for p in parts)
        return hashlib.sha256(raw.encode("utf-8")).hexdigest()

    def get(self, key: str) -> Any | None:
        """Gibt den gecachten Wert zurueck, sofern vorhanden und nicht abgelaufen."""
        if not self._conn:
            return None
        row = self._conn.execute(
            "SELECT ts, payload FROM cache WHERE key = ?", (key,)
        ).fetchone()
        if not row:
            return None
        ts, payload = row
        if time.time() - ts > self.ttl:
            self._conn.execute("DELETE FROM cache WHERE key = ?", (key,))
            self._conn.commit()
            return None
        try:
            return json.loads(payload)
        except json.JSONDecodeError:
            return None

    def set(self, key: str, value: Any) -> None:
        if not self._conn:
            return
        self._conn.execute(
            "INSERT OR REPLACE INTO cache (key, ts, payload) VALUES (?, ?, ?)",
            (key, time.time(), json.dumps(value, default=str)),
        )
        self._conn.commit()

    def purge_expired(self) -> int:
        if not self._conn:
            return 0
        cutoff = time.time() - self.ttl
        cur = self._conn.execute("DELETE FROM cache WHERE ts < ?", (cutoff,))
        self._conn.commit()
        return cur.rowcount

    def clear(self) -> None:
        if not self._conn:
            return
        self._conn.execute("DELETE FROM cache")
        self._conn.commit()

    def close(self) -> None:
        if self._conn:
            self._conn.close()
            self._conn = None

    def __enter__(self) -> "Cache":
        return self

    def __exit__(self, *exc: object) -> None:
        self.close()
