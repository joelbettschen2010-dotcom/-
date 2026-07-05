"use client";
// Grosser Drehregler (touch-optimiert): Ziehen auf dem Kreisbogen setzt die
// Lautstaerke, der Bogen zeigt den Wert. 270°-Bogen wie an Audio-Geraeten.
import { useCallback, useRef } from "react";

const START = 135; // Bogen-Start (Grad, 0 = rechts, im Uhrzeigersinn)
const SWEEP = 270;

export default function VolumeKnob({
  value,
  onChange,
}: {
  value: number; // 0..100
  onChange: (v: number) => void;
}) {
  const ref = useRef<SVGSVGElement>(null);

  const fromPointer = useCallback(
    (clientX: number, clientY: number) => {
      const el = ref.current;
      if (!el) return;
      const r = el.getBoundingClientRect();
      const dx = clientX - (r.left + r.width / 2);
      const dy = clientY - (r.top + r.height / 2);
      let deg = (Math.atan2(dy, dx) * 180) / Math.PI; // -180..180, 0 = rechts
      deg = (deg - START + 360) % 360;
      if (deg > SWEEP) deg = deg > SWEEP + (360 - SWEEP) / 2 ? 0 : SWEEP;
      onChange(Math.round((deg / SWEEP) * 100));
    },
    [onChange]
  );

  const angle = START + (value / 100) * SWEEP;
  const polar = (deg: number, rad: number) => {
    const a = (deg * Math.PI) / 180;
    return [100 + rad * Math.cos(a), 100 + rad * Math.sin(a)];
  };
  const [ax, ay] = polar(START, 80);
  const [bx, by] = polar(angle, 80);
  const large = angle - START > 180 ? 1 : 0;
  const [nx, ny] = polar(angle, 62);

  return (
    <svg
      ref={ref}
      viewBox="0 0 200 200"
      className="w-64 h-64 mx-auto touch-none"
      onPointerDown={(e) => {
        (e.target as Element).setPointerCapture?.(e.pointerId);
        fromPointer(e.clientX, e.clientY);
      }}
      onPointerMove={(e) => e.buttons && fromPointer(e.clientX, e.clientY)}
    >
      {/* Hintergrund-Bogen */}
      <path
        d={`M ${polar(START, 80)} A 80 80 0 1 1 ${polar(START + SWEEP, 80)}`}
        fill="none" stroke="#262c37" strokeWidth="10" strokeLinecap="round"
      />
      {/* Wert-Bogen */}
      {value > 0 && (
        <path
          d={`M ${ax} ${ay} A 80 80 0 ${large} 1 ${bx} ${by}`}
          fill="none" stroke="#3b9eff" strokeWidth="10" strokeLinecap="round"
        />
      )}
      {/* Knopf */}
      <circle cx="100" cy="100" r="56" fill="#161a21" stroke="#262c37" />
      <line x1={100 + (nx - 100) * 0.55} y1={100 + (ny - 100) * 0.55}
            x2={nx} y2={ny} stroke="#3b9eff" strokeWidth="5" strokeLinecap="round" />
      <text x="100" y="108" textAnchor="middle" fill="#e5e7eb"
            fontSize="30" fontWeight="700">{value}</text>
      <text x="100" y="128" textAnchor="middle" fill="#6b7280" fontSize="11">
        LAUTSTÄRKE
      </text>
    </svg>
  );
}
