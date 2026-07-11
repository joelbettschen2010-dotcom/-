"use client";
// Registriert den Service Worker (PWA-Offline-Faehigkeit).
import { useEffect } from "react";
export default function SwRegister() {
  useEffect(() => {
    if ("serviceWorker" in navigator)
      navigator.serviceWorker.register("/sw.js").catch(() => {});
  }, []);
  return null;
}
