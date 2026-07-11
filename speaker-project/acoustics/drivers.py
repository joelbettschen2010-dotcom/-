"""
Thiele-Small-Parameter für die SpeakerBox Pro Treiber (v2: Dayton Audio).

Gewählte Chassis (Budget-Empfehlung, kein AliExpress — dokumentierte
Parameter, enge Fertigungstoleranzen, in der EU z. B. via SoundImports):

  - 2x Dayton Audio ND91-4   (3.5" Fullrange, Neodym, 4 Ohm, 30 W RMS)
  - 1x Dayton Audio TCP115-4 (4" Subwoofer, Poly-Konus, 4 Ohm, 40 W RMS)

Die Werte unten sind DATENBLATTWERTE (Parts Express / SoundImports).
Vor der finalen DSP-Abstimmung trotzdem die realen Parameter messen
(REW + Impedanzmessung mit Referenzwiderstand, oder DATS) — Serienstreuung
von +-10-15 % bei Fs/Vas ist normal.

Alle Einheiten SI, ausser wo kommentiert.
"""

from dataclasses import dataclass


@dataclass
class Driver:
    name: str
    fs: float      # Freiluftresonanz [Hz]
    qts: float     # Gesamtgüte [-]
    qes: float     # elektrische Güte [-]
    qms: float     # mechanische Güte [-]
    vas: float     # Äquivalentvolumen [m^3]
    re: float      # Schwingspulen-Gleichstromwiderstand [Ohm]
    le: float      # Schwingspulen-Induktivität [H]
    sd: float      # effektive Membranfläche [m^2]
    xmax: float    # linearer Hub (einseitig) [m]
    pmax: float    # therm. Belastbarkeit RMS [W]
    znom: float    # Nennimpedanz [Ohm]

    @property
    def sens_1w(self) -> float:
        """Kennschalldruck dB/1W/1m (Halbraum) aus T/S-Parametern berechnet."""
        import numpy as np
        rho0, c0 = 1.204, 343.0
        # Referenzwirkungsgrad eta0 = 4*pi^2/c^3 * fs^3 * Vas / Qes
        eta0 = (4 * np.pi**2 / c0**3) * self.fs**3 * self.vas / self.qes
        return 112.02 + 10 * np.log10(eta0)  # 112 dB = 100% Wirkungsgrad Halbraum


# --- 3.5" Fullrange: DAYTON AUDIO ND91-4 (Datenblatt SoundImports) ---------
# Gewaehlt statt AliExpress-Generik: 4.6mm Xmax (fast 2x), enge Fertigungs-
# toleranzen, dokumentierte Parameter. VOR finaler Abstimmung trotzdem messen.
FULLRANGE_3IN = Driver(
    name='Dayton ND91-4 (Datenblatt)',
    fs=74.0,
    qts=0.41,
    qes=0.45,
    qms=4.24,
    vas=1.4e-3,        # 1.4 Liter
    re=4.3,
    le=0.83e-3,
    sd=30.4e-4,        # 30.4 cm^2
    xmax=4.6e-3,       # 4.6 mm (!)
    pmax=30.0,
    znom=4.0,
)

# --- 4" Subwoofer: DAYTON AUDIO TCP115-4 (Datenblatt SoundImports) -----------
# Qts 0.35 = stark bedaempft: in 2.5L eff. ergibt das Qtc ~0.52 — sehr
# praezise, gutmuetiger Abfall, grosser Headroom fuer DSP-Bass-Shelf.
SUBWOOFER_4IN = Driver(
    name='Dayton TCP115-4 (Datenblatt)',
    fs=53.8,
    qts=0.35,
    qes=0.40,
    qms=3.14,
    vas=3.11e-3,       # 0.11 ft^3 = 3.11 Liter
    re=3.2,
    le=0.97e-3,
    sd=50.3e-4,
    xmax=4.0e-3,       # konservativ (Quellen: 4.0-5.25 mm)
    pmax=40.0,
    znom=4.0,
)

# --- Verfügbare Kammervolumina im 3D-gedruckten Gehäuse [m^3] ---------------
# Netto = Innenvolumen minus Treiber-Verdrängung. Dämmwolle (locker gestopft)
# vergrössert das effektive Volumen um ~15 %.
V_CHAMBER_MID_MIN = 0.33e-3
V_CHAMBER_MID_MAX = 0.38e-3   # Gehaeuse v2.1: 96x80x56-Kammer, ND91-4-Verdraengung
V_CHAMBER_SUB_MIN = 1.7e-3
V_CHAMBER_SUB_MAX = 1.9e-3    # Gehaeuse v2.1: 122x142x122, TCP115-4-Verdraengung
STUFFING_FACTOR = 1.15   # effektive Volumenvergrösserung durch Dämmwolle

# --- Verstärker-Randbedingungen ----------------------------------------------
# TPA3118D2 an 14.8 V nominal: BTL an 4R ~23 W vor Clipping (Datenblatt Fig.),
# PBTL an 4R (2 Kanäle parallel) ebenfalls durch PVDD begrenzt ~30 W kurzzeitig.
P_AMP_FULLRANGE = 23.0   # W verfügbar pro Fullrange-Kanal
P_AMP_SUB = 30.0         # W verfügbar für Sub (PBTL)
