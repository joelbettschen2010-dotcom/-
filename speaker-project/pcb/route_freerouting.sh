#!/bin/bash
# Vollstaendige Verdrahtung des Main-Boards mit Freerouting (headless, lokal
# via Docker). Erzeugt DSN aus dem platzierten Board, routet, importiert das
# Ergebnis zurueck und fuellt die Zonen.
#
#   ./route_freerouting.sh [passes]
#
# Voraussetzung: laufender Docker-Daemon + Image
#   ghcr.io/freerouting/freerouting:latest
set -e
cd "$(dirname "$0")"
PASSES="${1:-30}"
RL="$PWD/.routelogs"; mkdir -p "$RL"
IMG="ghcr.io/freerouting/freerouting:latest"

echo "1) Platziertes Board erzeugen"
(cd main-board && python3 generate.py >/dev/null)
cp main-board/main-board.kicad_pcb "$RL/placed.kicad_pcb"

echo "2) DSN exportieren"
python3 dsn_export.py "$RL/placed.kicad_pcb" "$RL/mainboard.dsn"

echo "3) Freerouting ($PASSES Passe)"
docker run --rm -v "$RL":/work --entrypoint java "$IMG" \
    -jar /app/freerouting-executable.jar \
    -de /work/mainboard.dsn -do /work/mainboard.ses -mp "$PASSES" -oit 2

echo "4) SES zurueck ins Board importieren"
python3 ses_import.py "$RL/placed.kicad_pcb" "$RL/mainboard.ses" \
    main-board/main-board.kicad_pcb

echo "5) DRC"
python3 - <<'PY'
import pcbnew
b=pcbnew.LoadBoard("main-board/main-board.kicad_pcb"); b.BuildConnectivity()
print("  unconnected:", b.GetConnectivity().GetUnconnectedCount(False),
      "tracks/vias:", sum(1 for _ in b.GetTracks()))
PY
echo "fertig."
