#!/usr/bin/env python3
"""Direkt-Startpunkt: ``python3 wx.py <ort>`` (ohne Installation).

Aequivalent zum installierten ``wx``-Kommando (siehe pyproject.toml).
"""

import sys

from wxcli.cli import main

if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
