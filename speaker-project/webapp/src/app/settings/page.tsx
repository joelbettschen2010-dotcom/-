"use client";
// Einstellungen: Geraetename, WiFi, BT-Pairing, OTA-Update, Werksreset.
import { useRef, useState } from "react";
import { api, baseUrl, useStatus } from "@/lib/api";

export default function SettingsPage() {
  const status = useStatus();
  const [name, setName] = useState("");
  const [ssid, setSsid] = useState("");
  const [pass, setPass] = useState("");
  const [msg, setMsg] = useState("");

  const flash = (m: string) => { setMsg(m); setTimeout(() => setMsg(""), 3000); };

  return (
    <div className="space-y-5">
      <h1 className="text-xl font-bold">Einstellungen</h1>
      {msg && <div className="bg-accent2/15 border border-accent2/40 text-accent2 rounded-xl p-3 text-sm">{msg}</div>}

      <Section title="Gerätename">
        <div className="flex gap-2">
          <input value={name} onChange={(e) => setName(e.target.value)}
                 placeholder="SpeakerBox Pro" className="input flex-1" />
          <button className="btn" onClick={() =>
            api("/api/name", { value: name }).then(() => flash("Name gespeichert")).catch(() => flash("Fehler"))}>
            Speichern
          </button>
        </div>
      </Section>

      <Section title="WLAN">
        <div className="space-y-2">
          <input value={ssid} onChange={(e) => setSsid(e.target.value)}
                 placeholder="Netzwerkname (SSID)" className="input w-full" />
          <input value={pass} onChange={(e) => setPass(e.target.value)} type="password"
                 placeholder="Passwort" className="input w-full" />
          <button className="btn w-full" onClick={() =>
            api("/api/wifi", { ssid, pass }).then(() => flash("Gespeichert — Neustart nötig")).catch(() => flash("Fehler"))}>
            Verbinden
          </button>
          <p className="text-xs text-gray-500">
            Ohne bekanntes Netzwerk startet die Box als Access Point
            „SpeakerBox-Pro“ (App unter 192.168.4.1).
          </p>
        </div>
      </Section>

      <Section title="Bluetooth">
        <div className="flex items-center justify-between">
          <span className="text-sm text-gray-400">
            {status?.bt.connected ? "Gerät verbunden" : "Kein Gerät verbunden"}
          </span>
          <button className="btn" onClick={() =>
            api("/api/bt/pair", {}).then(() => flash("Pairing gestartet"))}>
            Pairing starten
          </button>
        </div>
      </Section>

      <OtaSection onDone={() => flash("Update installiert — Neustart…")} />

      <Section title="Gefahrenzone">
        <div className="space-y-2">
          <button className="w-full py-3 rounded-xl border border-warn/40 text-warn text-sm active:bg-warn/10"
            onClick={() => confirm("Wirklich alle Einstellungen löschen?") &&
              api("/api/factory-reset", {}).then(() => flash("Zurückgesetzt"))}>
            Werksreset
          </button>
          <button className="w-full py-3 rounded-xl border border-warn/40 text-warn text-sm active:bg-warn/10"
            onClick={() => api("/api/power/off", {})}>
            Ausschalten
          </button>
        </div>
      </Section>

      <style jsx global>{`
        .input { background: #161a21; border: 1px solid #262c37; border-radius: 0.75rem; padding: 0.6rem 0.9rem; font-size: 0.875rem; }
        .btn { background: rgba(59,158,255,.15); color: #3b9eff; border-radius: 0.75rem; padding: 0.6rem 1rem; font-size: 0.875rem; font-weight: 500; }
      `}</style>
    </div>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="bg-panel border border-line rounded-2xl p-4 space-y-3">
      <h2 className="text-sm font-semibold text-gray-300">{title}</h2>
      {children}
    </div>
  );
}

// OTA-Upload mit Fortschrittsbalken (XMLHttpRequest wegen progress-Event)
function OtaSection({ onDone }: { onDone: () => void }) {
  const fileRef = useRef<HTMLInputElement>(null);
  const [progress, setProgress] = useState<number | null>(null);

  const upload = (file: File) => {
    const xhr = new XMLHttpRequest();
    xhr.open("POST", baseUrl() + "/api/ota");
    xhr.upload.onprogress = (e) =>
      e.lengthComputable && setProgress(Math.round((e.loaded / e.total) * 100));
    xhr.onload = () => { setProgress(null); onDone(); };
    xhr.onerror = () => setProgress(null);
    const fd = new FormData();
    fd.append("firmware", file);
    xhr.send(fd);
  };

  return (
    <Section title="Firmware-Update (OTA)">
      <input ref={fileRef} type="file" accept=".bin" hidden
             onChange={(e) => e.target.files?.[0] && upload(e.target.files[0])} />
      {progress === null ? (
        <button className="btn w-full" onClick={() => fileRef.current?.click()}>
          firmware.bin auswählen…
        </button>
      ) : (
        <div className="h-3 bg-line rounded-full overflow-hidden">
          <div className="h-full bg-accent transition-all" style={{ width: `${progress}%` }} />
        </div>
      )}
    </Section>
  );
}
