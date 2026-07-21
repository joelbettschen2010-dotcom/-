#!/usr/bin/env bash
# setup.sh – Einrichtung von wx (Termux & generisches Linux/macOS)
#
#   bash setup.sh
#
# Installiert die Abhaengigkeiten und das `wx`-Kommando. Auf Termux werden
# fehlende Systempakete (python) via pkg nachgezogen.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "==> wx-Setup in $SCRIPT_DIR"

# --- Termux erkennen und Systempakete sicherstellen --------------------------
if [ -n "${PREFIX:-}" ] && echo "$PREFIX" | grep -q "com.termux"; then
  echo "==> Termux erkannt – Systempakete pruefen"
  pkg update -y || true
  pkg install -y python || true
fi

# --- Python finden -----------------------------------------------------------
PY="$(command -v python3 || command -v python || true)"
if [ -z "$PY" ]; then
  echo "FEHLER: Kein Python 3 gefunden. Bitte Python 3.11+ installieren." >&2
  exit 1
fi
echo "==> Python: $($PY --version)"

# --- Abhaengigkeiten + wx installieren ---------------------------------------
echo "==> Abhaengigkeiten installieren (httpx, rich)"
"$PY" -m pip install --user --upgrade pip >/dev/null 2>&1 || true

if "$PY" -m pip install --user -e . ; then
  echo "==> wx als Kommando installiert (pip -e .)"
  INSTALLED_CMD=1
else
  echo "==> Editierbare Installation nicht moeglich – nur Abhaengigkeiten"
  "$PY" -m pip install --user -r requirements.txt
  INSTALLED_CMD=0
fi

# --- Konfiguration anlegen ---------------------------------------------------
echo "==> Standardkonfiguration anlegen (falls noch keine existiert)"
"$PY" -c "from wxcli.config import ensure_config; print('Config:', ensure_config())" \
  || echo "   (Config wird beim ersten Aufruf erzeugt)"

# --- Abschluss ---------------------------------------------------------------
echo
echo "Fertig. Nutzung:"
if [ "$INSTALLED_CMD" = "1" ]; then
  echo "   wx Frutigen"
  echo "   wx \"Punta Gorda\" --html"
  echo
  echo "Falls 'wx' nicht gefunden wird, liegt es unter \$HOME/.local/bin –"
  echo "diesen Pfad ggf. zur PATH-Variable hinzufuegen:"
  echo "   export PATH=\"\$HOME/.local/bin:\$PATH\""
else
  echo "   python3 wx.py Frutigen"
  echo "   python3 wx.py \"Punta Gorda\" --html"
fi
