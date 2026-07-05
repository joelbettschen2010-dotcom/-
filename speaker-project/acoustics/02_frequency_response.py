"""
Vollständige Simulation der drei Treiber in ihren finalen Kammern:
Frequenzgang, Impedanzgang, Gruppenlaufzeit und maximaler SPL.

Finale Volumina (aus 01_volume_sweep.py, inkl. Dämmwolle-Effekt +15 %):
  - Mid/High L und R: 0.35 L netto -> effektiv 0.40 L
  - Sub:              2.2 L netto  -> effektiv 2.53 L

Ausgabe: plots/02_response.png, plots/02_impedance.png,
         plots/02_group_delay.png, plots/02_max_spl.png
"""

import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

from drivers import (FULLRANGE_3IN, SUBWOOFER_4IN, STUFFING_FACTOR,
                     P_AMP_FULLRANGE, P_AMP_SUB)
from boxsim import (box_alignment, hp2, spl_response, group_delay, impedance,
                    spl_max, spl_excursion_limited, spl_power_limited)

# Finale effektive Volumina
VB_FR = 0.35e-3 * STUFFING_FACTOR
VB_SUB = 2.2e-3 * STUFFING_FACTOR

F = np.logspace(np.log10(20), np.log10(20000), 1200)

SYSTEMS = [
    (FULLRANGE_3IN, VB_FR, P_AMP_FULLRANGE, "3\" Fullrange (0.40 L eff.)", "tab:blue"),
    (SUBWOOFER_4IN, VB_SUB, P_AMP_SUB, "4\" Sub (2.53 L eff.)", "tab:red"),
]

if __name__ == "__main__":
    # --- 1) Frequenzgang @ 1 W --------------------------------------------
    fig, ax = plt.subplots(figsize=(10, 5.5))
    for drv, vb, p, label, col in SYSTEMS:
        fc, qtc = box_alignment(drv, vb)
        ax.semilogx(F, spl_response(drv, vb, F), color=col,
                    label=f"{label}: Fc={fc:.0f} Hz, Qtc={qtc:.2f}")
    ax.set(xlabel="Frequenz [Hz]", ylabel="SPL [dB @ 1 W / 1 m, Halbraum]",
           title="SpeakerBox Pro — Frequenzgang in finaler Kammer (vor DSP)",
           xlim=(20, 20000), ylim=(55, 95))
    ax.grid(which="both", alpha=0.3)
    ax.legend()
    fig.tight_layout()
    fig.savefig("plots/02_response.png", dpi=130)
    plt.close(fig)

    # --- 2) Impedanzgang ----------------------------------------------------
    fig, ax = plt.subplots(figsize=(10, 5.5))
    for drv, vb, p, label, col in SYSTEMS:
        z = impedance(drv, vb, F)
        ax.semilogx(F, np.abs(z), color=col, label=label)
    ax.axhline(3.2, color="k", ls=":", lw=0.8, label="TPA3118-Minimum (3.2 Ω)")
    ax.set(xlabel="Frequenz [Hz]", ylabel="|Z| [Ω]",
           title="Impedanzgang in geschlossener Kammer", xlim=(20, 20000))
    ax.grid(which="both", alpha=0.3)
    ax.legend()
    fig.tight_layout()
    fig.savefig("plots/02_impedance.png", dpi=130)
    plt.close(fig)

    # --- 3) Gruppenlaufzeit -------------------------------------------------
    fig, ax = plt.subplots(figsize=(10, 5.5))
    for drv, vb, p, label, col in SYSTEMS:
        fc, qtc = box_alignment(drv, vb)
        gd = group_delay(F, hp2(F, fc, qtc))
        ax.semilogx(F, gd, color=col, label=label)
    ax.set(xlabel="Frequenz [Hz]", ylabel="Gruppenlaufzeit [ms]",
           title="Gruppenlaufzeit der Box-Hochpässe", xlim=(20, 2000), ylim=(0, 12))
    ax.grid(which="both", alpha=0.3)
    ax.legend()
    fig.tight_layout()
    fig.savefig("plots/02_group_delay.png", dpi=130)
    plt.close(fig)

    # --- 4) Maximaler SPL ---------------------------------------------------
    fig, ax = plt.subplots(figsize=(10, 5.5))
    for drv, vb, p, label, col in SYSTEMS:
        ax.semilogx(F, spl_max(drv, vb, F, p), color=col, lw=2, label=f"{label} (Minimum)")
        ax.semilogx(F, spl_excursion_limited(drv, vb, F), color=col, ls="--", lw=0.9,
                    label=f"{label} — Hubgrenze Xmax")
        ax.semilogx(F, spl_power_limited(drv, vb, F, p), color=col, ls=":", lw=0.9,
                    label=f"{label} — Leistungsgrenze")
    # Beide Fullranges zusammen: +3 dB (inkohärent) bzw. +6 dB (kohärent, Mono-Bass)
    ax.set(xlabel="Frequenz [Hz]", ylabel="max. SPL [dB @ 1 m, Halbraum]",
           title="Maximalpegel: Hubgrenze vs. Leistungsgrenze (1 Treiber)",
           xlim=(30, 20000), ylim=(70, 115))
    ax.grid(which="both", alpha=0.3)
    ax.legend(fontsize=8)
    fig.tight_layout()
    fig.savefig("plots/02_max_spl.png", dpi=130)
    plt.close(fig)

    # --- Konsolen-Report ----------------------------------------------------
    print("=" * 70)
    print("Systemauslegung final")
    print("=" * 70)
    for drv, vb, p, label, _ in SYSTEMS:
        fc, qtc = box_alignment(drv, vb)
        r = spl_response(drv, vb, F) - drv.sens_1w
        f3 = F[np.argmin(np.abs(r + 3.0))]
        print(f"\n{label}")
        print(f"  Fc = {fc:.1f} Hz, Qtc = {qtc:.2f}, f3 = {f3:.1f} Hz")
        print(f"  Kennschalldruck: {drv.sens_1w:.1f} dB/1W/1m")
        smax = spl_max(drv, vb, F, p)
        band = (F > 100) & (F < 8000) if "Full" in label else (F > 50) & (F < 200)
        print(f"  max. SPL im Arbeitsband: {smax[band].min():.0f}…{smax[band].max():.0f} dB")
    print("\nSummen-Abschätzung System: ~103-106 dB @ 1 m (2x FR + Sub, Musik)")
    print("Plots gespeichert in plots/")
