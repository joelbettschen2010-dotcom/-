"""AM5-Sockel: echte Pinbelegung aus docs/am5_pinmap.csv.

Quelle der Pinmap: WikiChip via Wikimedia Commons "Socket AM5 pinmap.svg",
Public Domain (Autor QuietRub). 1718 Pins mit Signalnamen.
"""
import csv
import os

# Versorgungs-Label -> Netzname im Projekt
POWER_NETS = {
    "VSS": "GND",
    "VDDCR": "+VDDCR_CPU",
    "VDDCR_SOC": "+VDDCR_SOC",
    "VDDIO_MEM_S3": "+1V1_MEM",
    "VDD_18": "+1V8",
    "VDD_33": "+3V3",
    "VDD_MISC": "+1V8",
    "RSVD": None,
}


def load_pins(base_dir):
    """Liest die Pinmap-CSV: Liste (pad, x_mm, y_mm, signal, kategorie)."""
    path = os.path.join(base_dir, "docs", "am5_pinmap.csv")
    out = []
    with open(path, newline="") as fh:
        rd = csv.reader(fh, delimiter=";")
        next(rd)
        for pad, x, y, sig, cat in rd:
            out.append((pad, float(x), float(y), sig, cat))
    return out


def pad_net(signal):
    """Netzname fuer einen Sockelpin: Rail-Mapping oder Signalname."""
    if signal in POWER_NETS:
        return POWER_NETS[signal]
    # '/' in Multiplex-Namen wuerde als Hierarchie gedeutet -> ersetzen
    return signal.replace("/", "-")


def build_fp(base_dir, FpCls):
    """Echter AM5-Footprint: 1718 Pads an Originalpositionen, echte Namen."""
    fp = FpCls("AM5_Socket_LGA1718",
               "AM5 LGA-1718, Pinbelegung nach WikiChip-Pinmap (Public "
               "Domain, Wikimedia Commons), Raster 0.94x0.81mm")
    for pad, x, y, sig, cat in load_pins(base_dir):
        fp.pad_smd(pad, x, y, 0.45, 0.45, "circle", net=pad_net(sig))
    fp.rect(-20, -19, 20, 19, "F.Fab", 0.1)
    fp.rect(-29, -35, 29, 35, "F.SilkS")          # ILM-Rahmen
    fp.courtyard(-29.5, -35.5, 29.5, 35.5)
    fp.pad_npth(0, -31, 3.2)
    fp.pad_npth(0, 31, 3.2)
    fp.texts.append(("AM5 LGA-1718", 0, -37, "F.SilkS"))
    return fp
