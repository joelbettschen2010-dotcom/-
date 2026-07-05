"use client";
// EQ-Seite: 10-Band-Slider ±12 dB, Live-Kurvenvisualisierung (SVG),
// Sub-Level-Slider, 3 lokale Preset-Slots, Reset.
import { useEffect, useMemo, useState } from "react";
import { api, EQ_FREQS, useStatus } from "@/lib/api";

const FREQS_HZ = [31.5, 63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000];
const Q = 1.41;

export default function EqPage() {
  const status = useStatus();
  const [gains, setGains] = useState<number[]>(Array(10).fill(0));
  const [sub, setSub] = useState(0);
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    if (status && !loaded) {
      setGains(status.eq ?? Array(10).fill(0));
      setSub(status.subLevel ?? 0);
      setLoaded(true);
    }
  }, [status, loaded]);

  const send = (g: number[], s: number) =>
    api("/api/eq", { bands: g, channel: "both", subLevel: s }).catch(() => {});

  const setBand = (i: number, v: number) => {
    const g = [...gains];
    g[i] = v;
    setGains(g);
    send(g, sub);
  };

  return (
    <div className="space-y-5">
      <h1 className="text-xl font-bold">Equalizer</h1>

      <EqCurve gains={gains} />

      {/* Band-Slider */}
      <div className="bg-panel border border-line rounded-2xl p-3 grid grid-cols-10 gap-1">
        {gains.map((g, i) => (
          <div key={i} className="flex flex-col items-center gap-1">
            <input
              type="range" min={-12} max={12} step={0.5} value={g}
              onChange={(e) => setBand(i, parseFloat(e.target.value))}
              className="eq-slider"
              // vertikaler Slider per CSS-Rotation
              style={{ writingMode: "vertical-lr", direction: "rtl", height: "8rem", width: "1.4rem" }}
            />
            <span className="text-[9px] text-gray-500">{EQ_FREQS[i]}</span>
            <span className="text-[9px] text-gray-400">{g > 0 ? "+" : ""}{g}</span>
          </div>
        ))}
      </div>

      {/* Sub-Level */}
      <div className="bg-panel border border-line rounded-2xl p-4">
        <div className="flex justify-between text-sm mb-2">
          <span className="text-gray-400">Sub-Level</span>
          <span>{sub > 0 ? "+" : ""}{sub} dB</span>
        </div>
        <input
          type="range" min={-12} max={12} step={0.5} value={sub}
          onChange={(e) => {
            const s = parseFloat(e.target.value);
            setSub(s);
            send(gains, s);
          }}
          className="w-full accent-[#22d3ae]"
        />
      </div>

      <PresetSlots gains={gains} sub={sub}
        onLoad={(g, s) => { setGains(g); setSub(s); send(g, s); }} />

      <button
        onClick={() => { const z = Array(10).fill(0); setGains(z); setSub(0); send(z, 0); }}
        className="w-full py-3 rounded-2xl border border-line text-gray-400 active:bg-line"
      >
        Auf Flat zurücksetzen
      </button>
    </div>
  );
}

// Frequenzgang der EQ-Summe: Betrag der kaskadierten Peaking-Biquads,
// analytisch ausgewertet (gleiche RBJ-Formeln wie DSP/Firmware).
function EqCurve({ gains }: { gains: number[] }) {
  const path = useMemo(() => {
    const fs = 48000;
    const pts: string[] = [];
    const N = 120;
    for (let n = 0; n <= N; n++) {
      const f = 20 * Math.pow(1000, n / N); // 20 Hz .. 20 kHz logarithmisch
      let db = 0;
      for (let b = 0; b < 10; b++) {
        const g = gains[b];
        if (!g) continue;
        const A = Math.pow(10, g / 40);
        const w0 = (2 * Math.PI * FREQS_HZ[b]) / fs;
        const alpha = Math.sin(w0) / (2 * Q);
        const b0 = 1 + alpha * A, b1 = -2 * Math.cos(w0), b2 = 1 - alpha * A;
        const a0 = 1 + alpha / A, a1 = b1, a2 = 1 - alpha / A;
        const w = (2 * Math.PI * f) / fs;
        const mag = (bb0: number, bb1: number, bb2: number) => {
          const re = bb0 + bb1 * Math.cos(w) + bb2 * Math.cos(2 * w);
          const im = -(bb1 * Math.sin(w) + bb2 * Math.sin(2 * w));
          return re * re + im * im;
        };
        db += 10 * Math.log10(mag(b0, b1, b2) / mag(a0, a1, a2));
      }
      const x = (n / N) * 320;
      const y = 60 - (db / 14) * 55; // ±14 dB Skala
      pts.push(`${n === 0 ? "M" : "L"}${x.toFixed(1)},${y.toFixed(1)}`);
    }
    return pts.join(" ");
  }, [gains]);

  return (
    <svg viewBox="0 0 320 120" className="w-full bg-panel border border-line rounded-2xl">
      {[ -12, -6, 0, 6, 12 ].map((db) => (
        <g key={db}>
          <line x1="0" x2="320" y1={60 - (db / 14) * 55} y2={60 - (db / 14) * 55}
                stroke={db === 0 ? "#374151" : "#1f2530"} strokeWidth="1" />
          <text x="2" y={58 - (db / 14) * 55} fill="#4b5563" fontSize="7">{db > 0 ? "+" : ""}{db}</text>
        </g>
      ))}
      <path d={path} fill="none" stroke="#3b9eff" strokeWidth="2" />
    </svg>
  );
}

// 3 Preset-Slots im localStorage des Browsers
function PresetSlots({ gains, sub, onLoad }:
  { gains: number[]; sub: number; onLoad: (g: number[], s: number) => void }) {
  const [saved, setSaved] = useState<boolean[]>([false, false, false]);

  useEffect(() => {
    setSaved([0, 1, 2].map((i) => !!localStorage.getItem(`eqSlot${i}`)));
  }, []);

  return (
    <div className="grid grid-cols-3 gap-2">
      {[0, 1, 2].map((i) => (
        <div key={i} className="bg-panel border border-line rounded-2xl p-2 text-center space-y-1">
          <div className="text-xs text-gray-500">Slot {i + 1}</div>
          <div className="flex gap-1">
            <button
              className="flex-1 text-xs py-2 rounded-lg bg-line/60 active:scale-95"
              onClick={() => {
                localStorage.setItem(`eqSlot${i}`, JSON.stringify({ gains, sub }));
                setSaved((s) => s.map((v, j) => (j === i ? true : v)));
              }}>
              Sichern
            </button>
            <button
              disabled={!saved[i]}
              className="flex-1 text-xs py-2 rounded-lg bg-accent/15 text-accent disabled:opacity-30 active:scale-95"
              onClick={() => {
                const raw = localStorage.getItem(`eqSlot${i}`);
                if (raw) {
                  const d = JSON.parse(raw);
                  onLoad(d.gains, d.sub);
                }
              }}>
              Laden
            </button>
          </div>
        </div>
      ))}
    </div>
  );
}
