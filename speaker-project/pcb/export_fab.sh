#!/bin/bash
# Fertigungsdaten-Export fuer beide Boards (KiCad 7 CLI).
# Erzeugt: Gerber, Drill (Excellon), Pick&Place (CSV), SVG-Vorschau, DRC-Report.
set -e
cd "$(dirname "$0")"

for BOARD in main-board button-board; do
    PCB="$BOARD/$BOARD.kicad_pcb"
    FAB="$BOARD/fab"
    mkdir -p "$FAB/gerber"
    echo "=== $BOARD ==="

    # Gerber (JLCPCB-Empfehlung: Protel-Endungen, keine X2-Attribute)
    kicad-cli pcb export gerbers \
        --layers F.Cu,B.Cu,F.Paste,B.Paste,F.Silkscreen,B.Silkscreen,F.Mask,B.Mask,Edge.Cuts \
        --use-drill-file-origin --no-x2 \
        -o "$FAB/gerber/" "$PCB"

    # Bohrdaten (Excellon, JLCPCB: PTH+NPTH getrennt, Format 4.6 mm)
    kicad-cli pcb export drill \
        --format excellon --drill-origin absolute --excellon-separate-th \
        --generate-map --map-format gerberx2 \
        -o "$FAB/gerber/" "$PCB"

    # Pick&Place fuer SMT-Bestueckung
    kicad-cli pcb export pos \
        --format csv --units mm --side both --use-drill-file-origin \
        -o "$FAB/$BOARD-positions.csv" "$PCB"

    # Visuelle Kontrolle
    kicad-cli pcb export svg --layers F.Cu,F.Silkscreen,Edge.Cuts \
        --page-size-mode 2 -o "$FAB/$BOARD-top.svg" "$PCB"
    kicad-cli pcb export svg --layers B.Cu,B.Silkscreen,Edge.Cuts \
        --page-size-mode 2 -o "$FAB/$BOARD-bottom.svg" "$PCB"

    # Gerber als ZIP (JLCPCB-Upload)
    (cd "$FAB" && zip -qr "$BOARD-gerber.zip" gerber/)
done

# BOM mit LCSC-Nummern
python3 gen_bom.py
echo "Fertigungsdaten komplett."
