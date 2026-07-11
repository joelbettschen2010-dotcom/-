"use client";
// Dashboard: Lautstaerke-Drehregler, BT-Status, Akku, Preset, Transport.
import { useEffect, useState } from "react";
import VolumeKnob from "@/components/VolumeKnob";
import { api, PRESET_LABELS, PRESET_NAMES, useStatus } from "@/lib/api";

export default function Dashboard() {
  const status = useStatus();
  const [vol, setVol] = useState(0);

  // Server-Wert uebernehmen, solange der Nutzer nicht gerade dreht
  useEffect(() => {
    if (status) setVol(status.volume);
  }, [status?.volume]); // eslint-disable-line react-hooks/exhaustive-deps

  const changeVol = (v: number) => {
    setVol(v);
    api("/api/volume", { value: v }).catch(() => {});
  };

  return (
    <div className="space-y-6">
      <header className="flex items-center justify-between">
        <h1 className="text-xl font-bold tracking-wide">SpeakerBox Pro</h1>
        <BatteryBadge soc={status?.battery.soc} charging={status?.battery.charging} />
      </header>

      <VolumeKnob value={vol} onChange={changeVol} />

      {/* BT-Status */}
      <div className="bg-panel border border-line rounded-2xl p-4 flex items-center justify-between">
        <div>
          <div className="text-sm text-gray-400">Bluetooth</div>
          <div className={status?.bt.connected ? "text-accent" : "text-gray-500"}>
            {status?.bt.pairing
              ? "Pairing läuft…"
              : status?.bt.connected
              ? "Verbunden"
              : "Nicht verbunden"}
          </div>
        </div>
        <button
          onClick={() => api("/api/bt/pair", {})}
          className="px-4 py-2 rounded-xl bg-accent/15 text-accent text-sm font-medium active:scale-95 transition"
        >
          Koppeln
        </button>
      </div>

      {/* Preset-Umschalter */}
      <div className="bg-panel border border-line rounded-2xl p-2 grid grid-cols-3 gap-2">
        {PRESET_LABELS.map((label, i) => (
          <button
            key={label}
            onClick={() => api("/api/preset", { value: PRESET_NAMES[i] })}
            className={`py-3 rounded-xl text-sm font-medium transition ${
              status?.preset === i
                ? "bg-accent text-black"
                : "text-gray-400 active:bg-line"
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      {/* Transport */}
      <div className="grid grid-cols-3 gap-3">
        {[
          { label: "⏮", cmd: "prev" },
          { label: "⏯", cmd: "play" },
          { label: "⏭", cmd: "next" },
        ].map((b) => (
          <button
            key={b.cmd}
            onClick={() => api("/api/media", { value: b.cmd }).catch(() => {})}
            className="bg-panel border border-line rounded-2xl py-4 text-2xl active:scale-95 transition"
          >
            {b.label}
          </button>
        ))}
      </div>

      {status?.ampFault && (
        <div className="bg-warn/15 border border-warn/40 text-warn rounded-2xl p-3 text-sm">
          ⚠ Verstärker meldet einen Fehler (Übertemperatur/Kurzschluss)
        </div>
      )}
    </div>
  );
}

function BatteryBadge({ soc, charging }: { soc?: number; charging?: boolean }) {
  const pct = soc ?? 0;
  const color = pct > 40 ? "bg-accent2" : pct > 15 ? "bg-yellow-400" : "bg-warn";
  return (
    <div className="flex items-center gap-2 text-sm text-gray-300">
      {charging && <span className="text-accent2">⚡</span>}
      <div className="w-9 h-4 border border-gray-500 rounded-sm relative">
        <div className={`absolute inset-y-0.5 left-0.5 rounded-sm ${color}`}
             style={{ width: `${Math.max(4, pct * 0.9)}%` }} />
        <div className="absolute -right-1 top-1 bottom-1 w-0.5 bg-gray-500 rounded" />
      </div>
      {pct}%
    </div>
  );
}
