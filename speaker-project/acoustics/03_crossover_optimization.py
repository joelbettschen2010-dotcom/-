"""
Crossover-Optimierung Sub <-> Fullrange.

Methode:
  - Beide Wege werden als Kaskade "Box-Hochpass (natürlich) x DSP-Filter"
    modelliert (komplex, d.h. inkl. Phase).
  - DSP-Filter: Linkwitz-Riley 2. Ordnung (12 dB/Okt) — wie in Teil 1.2
    gefordert. LR2 summiert sich flach, wenn beide Wege in Phase sind;
    klassisch muss dazu EIN Weg invertiert werden (LR2 hat 180° Versatz).
  - Zusätzlich wird die natürliche Phasendrehung der Box-Hochpässe
    berücksichtigt: der Sub (Fc~76 Hz) und die Fullranges (Fc~115 Hz)
    drehen unterschiedlich — deshalb wird die Übernahmefrequenz UND die
    Sub-Polarität UND ein kleines Sub-Delay gesweept.
  - Zielfunktion: minimale Welligkeit (Standardabweichung) der Summe
    im Bereich 60-500 Hz, normiert auf den Mittelwert.

Startpunkt laut Vorgabe: 150-180 Hz. Sweep: 100-250 Hz.

Ausgabe: plots/03_crossover_sweep.png, plots/03_crossover_final.png
"""

import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

from drivers import FULLRANGE_3IN, SUBWOOFER_4IN, STUFFING_FACTOR
from boxsim import box_alignment, hp2, lr2_lowpass, lr2_highpass

VB_FR = 0.35e-3 * STUFFING_FACTOR
VB_SUB = 2.2e-3 * STUFFING_FACTOR

F = np.logspace(np.log10(30), np.log10(2000), 1500)

# Natürliche (komplexe) Box-Übertragungen
fc_fr, qtc_fr = box_alignment(FULLRANGE_3IN, VB_FR)
fc_sub, qtc_sub = box_alignment(SUBWOOFER_4IN, VB_SUB)
H_FR_BOX = hp2(F, fc_fr, qtc_fr)
H_SUB_BOX = hp2(F, fc_sub, qtc_sub)

# Pegelabgleich: Sub und Fullrange-Paar auf gleiche Passband-Empfindlichkeit
# normiert (der DSP gleicht Pegel ohnehin ab); Fullrange zählt doppelt (L+R
# tragen im Bass kohärent bei -> +6 dB), hier konservativ +3 dB (Stereo-Musik).
FR_GAIN = 10 ** (3.0 / 20.0)


def summed_response(fx, invert_sub, delay_ms):
    """Komplexe Summe beider Wege bei Übernahmefrequenz fx."""
    h_fr = H_FR_BOX * lr2_highpass(F, fx) * FR_GAIN
    h_sub = H_SUB_BOX * lr2_lowpass(F, fx)
    if invert_sub:
        h_sub = -h_sub
    h_sub = h_sub * np.exp(-2j * np.pi * F * delay_ms / 1000.0)
    return h_fr + h_sub


def ripple_metric(h):
    """Welligkeit [dB] der Summenkurve im Bewertungsband 60-500 Hz."""
    band = (F >= 60) & (F <= 500)
    mag = 20 * np.log10(np.abs(h[band]) + 1e-12)
    return np.std(mag)


if __name__ == "__main__":
    # Sweep bewusst breiter als der Startbereich 150-180 Hz: die kleinen
    # Mid/High-Kammern schieben die Fullrange-Einbauresonanz auf ~200 Hz,
    # das physikalische Optimum liegt daher voraussichtlich darüber.
    fxs = np.linspace(100, 400, 301)
    delays = np.linspace(0, 3.0, 31)  # Sub-Delay 0-3 ms (DSP kann das)

    best = None
    ripple_by_fx = {True: [], False: []}
    for inv in (True, False):
        for fx in fxs:
            # bestes Delay je fx suchen
            r_best_d = min((ripple_metric(summed_response(fx, inv, d)), d)
                           for d in delays)
            ripple_by_fx[inv].append(r_best_d[0])
            cand = (r_best_d[0], fx, inv, r_best_d[1])
            if best is None or cand[0] < best[0]:
                best = cand

    ripple, fx_opt, inv_opt, delay_opt = best

    # --- Plot 1: Sweep-Ergebnis ---------------------------------------------
    fig, ax = plt.subplots(figsize=(9, 5))
    ax.plot(fxs, ripple_by_fx[False], label="Sub normal gepolt")
    ax.plot(fxs, ripple_by_fx[True], label="Sub invertiert")
    ax.axvline(fx_opt, color="g", ls="--",
               label=f"Optimum: {fx_opt:.0f} Hz ({'invertiert' if inv_opt else 'normal'}, "
                     f"Delay {delay_opt:.1f} ms)")
    ax.axvspan(150, 180, color="orange", alpha=0.15, label="Startbereich Vorgabe")
    ax.set(xlabel="LR2-Übernahmefrequenz [Hz]",
           ylabel="Welligkeit der Summe 60-500 Hz [dB, Std.-Abw.]",
           title="Crossover-Sweep: Welligkeit vs. Übernahmefrequenz")
    ax.grid(alpha=0.3)
    ax.legend(fontsize=8)
    fig.tight_layout()
    fig.savefig("plots/03_crossover_sweep.png", dpi=130)
    plt.close(fig)

    # --- Plot 2: finale Übergangs-Kurven -------------------------------------
    h_fr = H_FR_BOX * lr2_highpass(F, fx_opt) * FR_GAIN
    h_sub = (-1 if inv_opt else 1) * H_SUB_BOX * lr2_lowpass(F, fx_opt) \
        * np.exp(-2j * np.pi * F * delay_opt / 1000.0)
    h_sum = h_fr + h_sub

    fig, ax = plt.subplots(figsize=(10, 5.5))
    db = lambda h: 20 * np.log10(np.abs(h) + 1e-12)
    ax.semilogx(F, db(h_fr), "b--", lw=1, label="Fullrange L+R (Box x LR2-HP)")
    ax.semilogx(F, db(h_sub), "r--", lw=1, label="Sub (Box x LR2-LP, korrigiert)")
    ax.semilogx(F, db(h_sum), "k-", lw=2, label="Summe")
    ax.axvline(fx_opt, color="g", ls=":", lw=1)
    ax.set(xlabel="Frequenz [Hz]", ylabel="rel. Pegel [dB]",
           title=f"Finaler Crossover: LR2 @ {fx_opt:.0f} Hz, "
                 f"Sub {'invertiert' if inv_opt else 'normal'}, Delay {delay_opt:.1f} ms",
           xlim=(30, 2000), ylim=(-30, 10))
    ax.grid(which="both", alpha=0.3)
    ax.legend()
    fig.tight_layout()
    fig.savefig("plots/03_crossover_final.png", dpi=130)
    plt.close(fig)

    print("=" * 70)
    print("Crossover-Optimierung")
    print("=" * 70)
    print(f"Box-Alignments: Fullrange Fc={fc_fr:.0f} Hz Qtc={qtc_fr:.2f} | "
          f"Sub Fc={fc_sub:.0f} Hz Qtc={qtc_sub:.2f}")
    print(f"\nOPTIMUM: LR2 @ {fx_opt:.0f} Hz")
    print(f"  Sub-Polarität: {'INVERTIERT' if inv_opt else 'normal'}")
    print(f"  Sub-Delay:     {delay_opt:.2f} ms")
    print(f"  Rest-Welligkeit 60-500 Hz: {ripple:.2f} dB (Std.-Abw.)")
    print("\nDiese Werte gehen 1:1 ins DSP-Programm (Teil 1.2).")
