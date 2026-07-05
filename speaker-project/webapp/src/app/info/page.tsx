"use client";
// Info-Seite: Akku-Verlauf (24 h), Uptime, Firmware-Version, Verbindung.
import { useEffect, useState } from "react";
import { api, useStatus } from "@/lib/api";

interface Info {
  firmware: string;
  uptime: number;
  mac: string;
  ip: string;
  heap: number;
  batteryHistory: number[];
}

export default function InfoPage() {
  const status = useStatus();
  const [info, setInfo] = useState<Info | null>(null);

  useEffect(() => {
    const load = () => api<Info>("/api/info").then(setInfo).catch(() => {});
    load();
    const t = setInterval(load, 30000);
    return () => clearInterval(t);
  }, []);

  const fmtUptime = (s: number) => {
    const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60);
    return `${h} h ${m} min`;
  };

  return (
    <div className="space-y-5">
      <h1 className="text-xl font-bold">Info</h1>

      <div className="bg-panel border border-line rounded-2xl p-4">
        <h2 className="text-sm font-semibold text-gray-300 mb-2">Akku-Verlauf (24 h)</h2>
        <BatteryChart data={info?.batteryHistory ?? []} />
        <div className="text-sm text-gray-400 mt-2">
          Aktuell {status?.battery.soc ?? "–"} % · {status?.battery.volts ?? "–"} V
          {status?.battery.charging && " · lädt ⚡"}
        </div>
      </div>

      <div className="bg-panel border border-line rounded-2xl divide-y divide-line text-sm">
        <Row k="Firmware" v={info?.firmware ?? "–"} />
        <Row k="Laufzeit" v={info ? fmtUptime(info.uptime) : "–"} />
        <Row k="IP-Adresse" v={info?.ip ?? "–"} />
        <Row k="MAC" v={info?.mac ?? "–"} />
        <Row k="Freier Heap" v={info ? `${Math.round(info.heap / 1024)} kB` : "–"} />
      </div>
    </div>
  );
}

function Row({ k, v }: { k: string; v: string }) {
  return (
    <div className="flex justify-between px-4 py-3">
      <span className="text-gray-500">{k}</span>
      <span className="font-mono">{v}</span>
    </div>
  );
}

function BatteryChart({ data }: { data: number[] }) {
  // 288 Punkte = 24 h; 0-Werte (noch nicht beschrieben) ausblenden
  const pts = data
    .map((v, i) => ({ v, i }))
    .filter((p) => p.v > 0);
  const path = pts
    .map((p, n) => `${n === 0 ? "M" : "L"}${((p.i / 287) * 320).toFixed(1)},${(80 - (p.v / 100) * 75).toFixed(1)}`)
    .join(" ");
  return (
    <svg viewBox="0 0 320 90" className="w-full">
      {[0, 50, 100].map((pc) => (
        <line key={pc} x1="0" x2="320" y1={80 - (pc / 100) * 75} y2={80 - (pc / 100) * 75}
              stroke="#1f2530" strokeWidth="1" />
      ))}
      {pts.length > 1 ? (
        <path d={path} fill="none" stroke="#22d3ae" strokeWidth="2" />
      ) : (
        <text x="160" y="45" textAnchor="middle" fill="#4b5563" fontSize="10">
          Noch keine Daten
        </text>
      )}
    </svg>
  );
}
