"""
Geschlossene-Box-Simulationsmodell (Thiele-Small, Kleinsignal + Grossignal).

Modell: klassisches Closed-Box-Modell nach Small (1972).
- Einbau in geschlossene Kammer erhöht die Steifigkeit:
    alpha = Vas / Vb
    Fc  = Fs  * sqrt(1 + alpha)     (Einbauresonanz)
    Qtc = Qts * sqrt(1 + alpha)     (Einbaugüte)
- Schalldruck-Übertragungsfunktion = Hochpass 2. Ordnung mit Fc, Qtc.
- Impedanzverlauf aus elektrischem Ersatzschaltbild (Re + Le + Motional).
- Max-SPL aus zwei Grenzen: Hubgrenze (Xmax) und Verstärker-/Thermogrenze.

Alle Funktionen vektorisiert über Frequenzachse f [Hz].
"""

import numpy as np

RHO0 = 1.204   # Luftdichte [kg/m^3] bei 20 °C
C0 = 343.0     # Schallgeschwindigkeit [m/s]
P_REF = 20e-6  # Referenzschalldruck [Pa]


def box_alignment(driver, vb):
    """Einbauresonanz Fc und Einbaugüte Qtc für Kammervolumen vb [m^3]."""
    alpha = driver.vas / vb
    fc = driver.fs * np.sqrt(1 + alpha)
    qtc = driver.qts * np.sqrt(1 + alpha)
    return fc, qtc


def hp2(f, fc, qtc):
    """Komplexe Übertragungsfunktion Hochpass 2. Ordnung (geschlossene Box)."""
    s = 1j * f / fc  # normierte komplexe Frequenz
    return s**2 / (s**2 + s / qtc + 1)


def spl_response(driver, vb, f, power=1.0):
    """SPL-Frequenzgang [dB] in 1 m Halbraum bei elektrischer Leistung power [W]."""
    fc, qtc = box_alignment(driver, vb)
    h = hp2(f, fc, qtc)
    # Kennschalldruck (Passband) + Leistungsterm + Box-Hochpass
    return driver.sens_1w + 10 * np.log10(power) + 20 * np.log10(np.abs(h) + 1e-12)


def group_delay(f, h):
    """Gruppenlaufzeit [ms] aus komplexem Frequenzgang h(f)."""
    phase = np.unwrap(np.angle(h))
    gd = -np.gradient(phase, 2 * np.pi * f)
    return gd * 1000.0


def impedance(driver, vb, f):
    """Elektrische Eingangsimpedanz [Ohm] des Treibers in geschlossener Box.

    Motional-Zweig: Parallelresonanzkreis bei Fc, transformiert auf die
    elektrische Seite. Res = Re * Qmc/Qec (Höhe des Impedanzmaximums).
    """
    fc, qtc = box_alignment(driver, vb)
    alpha = driver.vas / vb
    # Güten skalieren beim Einbau gleich wie Qts
    qmc = driver.qms * np.sqrt(1 + alpha)
    qec = driver.qes * np.sqrt(1 + alpha)
    res = driver.re * qmc / qec              # Impedanzüberhöhung bei Fc
    w = 2 * np.pi * f
    wc = 2 * np.pi * fc
    # Parallelschwingkreis (normiert): Z_mot = Res / (1 + jQmc(w/wc - wc/w))
    z_mot = res / (1 + 1j * qmc * (w / wc - wc / w))
    return driver.re + 1j * w * driver.le + z_mot


def excursion(driver, vb, f, voltage):
    """Membranhub (Spitze) [m] bei Klemmenspannung voltage [V RMS].

    Unterhalb Fc ist der Hub konstant (steifigkeitskontrolliert), oberhalb
    fällt er mit 12 dB/Okt. Kleinsignalmodell, Le vernachlässigt (tieffrequent).
    """
    fc, qtc = box_alignment(driver, vb)
    # DC-Auslenkung pro Volt: x0 = Bl / (Re * k_total); ausgedrückt über T/S:
    # Cms_total = Cms/(1+alpha); Bl^2 = Re * (2*pi*fs*Mms)/Qes; Mms aus fs & Cms.
    # Eleganter über die Hochpassfunktion: x(f) = x_dc * |1/(s^2 + s/Qtc + 1)|
    alpha = driver.vas / vb
    cms = driver.vas / (RHO0 * C0**2 * driver.sd**2)      # mech. Nachgiebigkeit
    mms = 1 / ((2 * np.pi * driver.fs)**2 * cms)          # bewegte Masse
    bl = np.sqrt(driver.re * 2 * np.pi * driver.fs * mms / driver.qes)
    cmc = cms / (1 + alpha)                               # Nachgiebigkeit in Box
    x_dc = bl * voltage / driver.re * cmc                 # statische Auslenkung
    s = 1j * f / fc
    tf = 1 / (s**2 + s / qtc + 1)                         # Tiefpass = Auslenkung
    return np.abs(x_dc * tf) * np.sqrt(2)                 # RMS -> Spitze


def spl_excursion_limited(driver, vb, f):
    """Max. SPL [dB @1m Halbraum] an der Hubgrenze Xmax (pro Frequenz)."""
    fc, qtc = box_alignment(driver, vb)
    h = np.abs(hp2(f, fc, qtc))
    # Verschiebevolumen bei Vollhub: Vd = Sd * Xmax (Spitze)
    # Fernfeld-Halbraum: p_peak = rho0 * (2*pi*f)^2 * Vd / (2*pi*r), r = 1 m
    vd = driver.sd * driver.xmax
    # Hub folgt der Tiefpassfunktion: bei f >> Fc sinkt der Hub, d.h. die
    # hubbegrenzte SPL steigt mit 40 dB/Dek über Fc weiter.
    p_peak = RHO0 * (2 * np.pi * f)**2 * vd / (2 * np.pi * 1.0)
    return 20 * np.log10(p_peak / np.sqrt(2) / P_REF)


def spl_power_limited(driver, vb, f, p_avail):
    """Max. SPL [dB] an der Leistungsgrenze (Verstärker oder therm. Limit)."""
    p_lim = min(p_avail, driver.pmax)
    return spl_response(driver, vb, f, power=p_lim)


def spl_max(driver, vb, f, p_avail):
    """Effektive Max-SPL: Minimum aus Hubgrenze und Leistungsgrenze."""
    return np.minimum(spl_excursion_limited(driver, vb, f),
                      spl_power_limited(driver, vb, f, p_avail))


def lr2_lowpass(f, fx):
    """Linkwitz-Riley 2. Ordnung Tiefpass (= 2 kaskadierte Butterworth 1. Ord.)."""
    s = 1j * f / fx
    return 1 / (1 + s)**2


def lr2_highpass(f, fx):
    """Linkwitz-Riley 2. Ordnung Hochpass."""
    s = 1j * f / fx
    return s**2 / (1 + s)**2
