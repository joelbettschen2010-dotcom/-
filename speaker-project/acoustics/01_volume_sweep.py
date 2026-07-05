"""
Kammervolumen-Optimierung per Sweep.

Ziel: flachster Frequenzgang VOR DSP-Korrektur (= maximaler DSP-Headroom).
Für geschlossene Boxen heisst das: Qtc möglichst nahe 0.707 (Butterworth,
maximal flach) — höheres Qtc gibt einen Buckel an Fc, tieferes Qtc rollt
früher aber gutmütiger ab.

Randbedingung: Das 3D-gedruckte Gehäuse gibt die Volumina weitgehend vor
(Mid/High 0.30-0.35 L, Sub 1.8-2.2 L). Der Sweep zeigt, wo in diesem Fenster
das Optimum liegt und wieviel Dämmwolle (effektiv +15 %) bringt.

Ausgabe: plots/01_volume_sweep_fullrange.png, plots/01_volume_sweep_sub.png
und Konsolen-Report mit Empfehlung.
"""

import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

from drivers import (FULLRANGE_3IN, SUBWOOFER_4IN, STUFFING_FACTOR,
                     V_CHAMBER_MID_MIN, V_CHAMBER_MID_MAX,
                     V_CHAMBER_SUB_MIN, V_CHAMBER_SUB_MAX)
from boxsim import box_alignment, spl_response

F = np.logspace(np.log10(20), np.log10(2000), 800)


def sweep(driver, v_range, v_avail, fname, title):
    """Sweep über Kammervolumen: Qtc, Fc, f3 und Frequenzgang-Schar plotten."""
    vols = np.linspace(v_range[0], v_range[1], 200)
    fcs, qtcs, f3s = [], [], []
    for vb in vols:
        fc, qtc = box_alignment(driver, vb)
        fcs.append(fc)
        qtcs.append(qtc)
        # f3 numerisch: Frequenz, bei der die Box-Übertragung -3 dB erreicht
        resp = spl_response(driver, vb, F) - driver.sens_1w
        idx = np.argmin(np.abs(resp + 3.0))
        f3s.append(F[idx])
    fcs, qtcs, f3s = map(np.array, (fcs, qtcs, f3s))

    fig, axes = plt.subplots(1, 2, figsize=(13, 5))

    # Links: Qtc und f3 über Volumen, verfügbares Fenster markiert
    ax = axes[0]
    ax.plot(vols * 1e3, qtcs, "b-", label="Qtc")
    ax.axhline(0.707, color="g", ls="--", lw=1, label="Qtc = 0.707 (maximal flach)")
    ax.axvspan(v_avail[0] * 1e3, v_avail[1] * 1e3, color="orange", alpha=0.2,
               label="verfügbares Kammervolumen")
    ax.axvspan(v_avail[0] * STUFFING_FACTOR * 1e3, v_avail[1] * STUFFING_FACTOR * 1e3,
               color="green", alpha=0.12, label="effektiv mit Dämmwolle (+15%)")
    ax.set_xlabel("Kammervolumen [L]")
    ax.set_ylabel("Qtc", color="b")
    ax2 = ax.twinx()
    ax2.plot(vols * 1e3, f3s, "r-", label="f3")
    ax2.set_ylabel("f3 [Hz]", color="r")
    ax.legend(loc="upper right", fontsize=8)
    ax.set_title(f"{title}: Qtc / f3 vs. Volumen")
    ax.grid(alpha=0.3)

    # Rechts: Frequenzgang-Schar für einige Volumina
    ax = axes[1]
    for vb in np.linspace(v_range[0], v_range[1], 6):
        fc, qtc = box_alignment(driver, vb)
        r = spl_response(driver, vb, F) - driver.sens_1w
        ax.semilogx(F, r, label=f"{vb*1e3:.2f} L (Qtc={qtc:.2f})")
    ax.axhline(-3, color="k", ls=":", lw=0.8)
    ax.set_xlabel("Frequenz [Hz]")
    ax.set_ylabel("rel. Pegel [dB]")
    ax.set_ylim(-24, 6)
    ax.legend(fontsize=8)
    ax.set_title(f"{title}: Frequenzgang (normiert)")
    ax.grid(which="both", alpha=0.3)

    fig.tight_layout()
    fig.savefig(f"plots/{fname}", dpi=130)
    plt.close(fig)

    # Empfehlung: Volumen im verfügbaren Fenster (inkl. Dämmwolle),
    # dessen Qtc am nächsten an 0.707 liegt
    v_eff_lo, v_eff_hi = v_avail[0] * STUFFING_FACTOR, v_avail[1] * STUFFING_FACTOR
    mask = (vols >= v_avail[0]) & (vols <= v_eff_hi)
    best = vols[mask][np.argmin(np.abs(qtcs[mask] - 0.707))]
    fc, qtc = box_alignment(driver, best)
    return best, fc, qtc


if __name__ == "__main__":
    print("=" * 70)
    print("Kammervolumen-Sweep SpeakerBox Pro")
    print("=" * 70)

    v, fc, qtc = sweep(FULLRANGE_3IN, (0.15e-3, 0.60e-3),
                       (V_CHAMBER_MID_MIN, V_CHAMBER_MID_MAX),
                       "01_volume_sweep_fullrange.png", "3\" Fullrange")
    print(f"\n3\" Fullrange:  optimal {v*1e3:.2f} L  ->  Fc={fc:.0f} Hz, Qtc={qtc:.2f}")
    print("   Empfehlung: Kammer auf 0.35 L netto auslegen + locker Dämmwolle.")
    print("   (Qtc bleibt >0.707 — akzeptabel, da unterhalb Fc ohnehin der Sub")
    print("    übernimmt und der DSP-Hochpass die Überhöhung wegfiltert.)")

    v, fc, qtc = sweep(SUBWOOFER_4IN, (1.0e-3, 4.0e-3),
                       (V_CHAMBER_SUB_MIN, V_CHAMBER_SUB_MAX),
                       "01_volume_sweep_sub.png", "4\" Subwoofer")
    print(f"\n4\" Subwoofer:  optimal {v*1e3:.2f} L  ->  Fc={fc:.0f} Hz, Qtc={qtc:.2f}")
    print("   Empfehlung: Kammer auf 2.2 L netto auslegen + Dämmwolle (eff. ~2.5 L).")

    print("\nPlots gespeichert in plots/")
