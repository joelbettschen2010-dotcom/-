// API-Client: REST + WebSocket zum ESP32.
// Laeuft die App direkt vom ESP32, ist die Basis-URL leer (same-origin).
// Als externe Web-App kann die Geraete-IP in localStorage hinterlegt werden.
"use client";

import { useEffect, useRef, useState } from "react";

export interface Status {
  volume: number; // 0..100
  preset: number; // 0 flat, 1 music, 2 outdoor
  subLevel: number;
  battery: { soc: number; volts: string; charging: boolean };
  bt: { connected: boolean; pairing: boolean };
  ampFault: boolean;
  eq: number[];
}

export function baseUrl(): string {
  if (typeof window === "undefined") return "";
  return localStorage.getItem("deviceUrl") ?? "";
}

export async function api<T = unknown>(
  path: string,
  body?: object
): Promise<T> {
  const res = await fetch(baseUrl() + path, {
    method: body ? "POST" : "GET",
    headers: body ? { "Content-Type": "application/json" } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) throw new Error(`${path}: HTTP ${res.status}`);
  return res.json();
}

// Live-Status per WebSocket, mit REST-Fallback + Auto-Reconnect.
export function useStatus(): Status | null {
  const [status, setStatus] = useState<Status | null>(null);
  const wsRef = useRef<WebSocket | null>(null);

  useEffect(() => {
    let closed = false;

    const connect = () => {
      if (closed) return;
      const base = baseUrl() || window.location.origin;
      const url = base.replace(/^http/, "ws") + "/ws";
      try {
        const ws = new WebSocket(url);
        wsRef.current = ws;
        ws.onmessage = (ev) => {
          try {
            setStatus(JSON.parse(ev.data));
          } catch {}
        };
        ws.onclose = () => setTimeout(connect, 2000);
        ws.onerror = () => ws.close();
      } catch {
        setTimeout(connect, 3000);
      }
    };
    connect();

    // initialer Abruf, falls der Socket langsam ist
    api<Status>("/api/status").then(setStatus).catch(() => {});

    return () => {
      closed = true;
      wsRef.current?.close();
    };
  }, []);

  return status;
}

export const PRESET_NAMES = ["flat", "music", "outdoor"] as const;
export const PRESET_LABELS = ["Flat", "Musik", "Outdoor"] as const;
export const EQ_FREQS = ["31", "63", "125", "250", "500", "1k", "2k", "4k", "8k", "16k"];
