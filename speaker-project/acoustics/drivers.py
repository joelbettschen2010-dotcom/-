"""
Thiele-Small-Parameter für die SpeakerBox Pro Treiber.

WICHTIG: Das sind RECHERCHIERTE TYPWERTE für generische AliExpress-Treiber
dieser Klasse — KEINE gemessenen Werte der konkreten Exemplare!
Vor der finalen Abstimmung müssen die echten Parameter gemessen werden
(REW + Impedanzmessung mit Referenzwiderstand, oder DATS).

Quellenlage / Begründung der Werte:

3" Fullrange, 4 Ohm, 15 W RMS (typisch: "3 inch full range 4ohm 15w"
auf AliExpress, baugleich mit Sounderlink/Aiyima/Denon-Klon-Chassis):
  - Vergleichbare Marken-Chassis: Dayton Audio ND91-4 (Fs 79 Hz, Qts 0.54,
    Vas 2.4 L, Xmax 4.4 mm), Tang Band W3-881SJF (Fs 110 Hz, Qts 0.74,
    Vas 1.1 L), Peerless TC9FD (Fs 125 Hz, Qts 0.9, Vas 0.9 L).
  - Generische AliExpress-3"-Chassis liegen laut Messungen in DIY-Foren
    (diyaudio.com "cheap full range roundup", ASR-Forum) meist bei:
    Fs 95-115 Hz, Qts 0.6-0.8, Vas 0.8-1.5 L, Xmax 2-3 mm, SPL 84-86 dB.
  - Gewählt: Mittelwerte dieser Spanne (eher konservativ).

4" Subwoofer, 4 Ohm, 30 W RMS (typisch: "4 inch subwoofer 4ohm 30w",
lange Schwingspule, Gummisicke):
  - Vergleichbare Marken-Chassis: Peerless SDS-P830945 (Fs 58 Hz, Qts 0.55,
    Vas 5.5 L), Dayton SD-115A (Fs 60 Hz, Qts 0.52), Tang Band W4-1320SIF
    (Fs 45 Hz, Qts 0.4, teurer).
  - Generische 4"-Subwoofer aus Fernost: Fs 55-70 Hz, Qts 0.45-0.65,
    Vas 3-5 L, Xmax 3-4.5 mm (linear), SPL 82-85 dB.
  - Gewählt: Mittelwerte, Xmax konservativ 3.5 mm.

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


# --- 3" Fullrange (Mitten/Höhen, je einer L und R) --------------------------
FULLRANGE_3IN = Driver(
    name='3" Fullrange 4R 15W (AliExpress-Typwerte)',
    fs=100.0,          # Hz
    qts=0.65,
    qes=0.75,
    qms=4.9,           # aus 1/Qts = 1/Qes + 1/Qms
    vas=1.2e-3,        # 1.2 Liter
    re=3.5,            # Ohm
    le=0.12e-3,        # 0.12 mH
    sd=32e-4,          # 32 cm^2 (eff. Durchmesser ~6.4 cm)
    xmax=2.5e-3,       # 2.5 mm
    pmax=15.0,
    znom=4.0,
)

# --- 4" Subwoofer (dedizierter Bass) ----------------------------------------
SUBWOOFER_4IN = Driver(
    name='4" Subwoofer 4R 30W (AliExpress-Typwerte)',
    fs=60.0,           # Hz
    qts=0.55,
    qes=0.62,
    qms=4.9,
    vas=4.0e-3,        # 4.0 Liter
    re=3.4,            # Ohm
    le=0.35e-3,        # 0.35 mH (lange Schwingspule)
    sd=52e-4,          # 52 cm^2 (eff. Durchmesser ~8.1 cm)
    xmax=3.5e-3,       # 3.5 mm
    pmax=30.0,
    znom=4.0,
)

# --- Verfügbare Kammervolumina im 3D-gedruckten Gehäuse [m^3] ---------------
# Netto = Innenvolumen minus Treiber-Verdrängung. Dämmwolle (locker gestopft)
# vergrössert das effektive Volumen um ~15 %.
V_CHAMBER_MID_MIN = 0.30e-3
V_CHAMBER_MID_MAX = 0.35e-3
V_CHAMBER_SUB_MIN = 1.8e-3
V_CHAMBER_SUB_MAX = 2.2e-3
STUFFING_FACTOR = 1.15   # effektive Volumenvergrösserung durch Dämmwolle

# --- Verstärker-Randbedingungen ----------------------------------------------
# TPA3118D2 an 14.8 V nominal: BTL an 4R ~23 W vor Clipping (Datenblatt Fig.),
# PBTL an 4R (2 Kanäle parallel) ebenfalls durch PVDD begrenzt ~30 W kurzzeitig.
P_AMP_FULLRANGE = 23.0   # W verfügbar pro Fullrange-Kanal
P_AMP_SUB = 30.0         # W verfügbar für Sub (PBTL)
