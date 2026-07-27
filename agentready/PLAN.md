# PLAN.md — AgentReady

> Meilensteine mit Stundenschätzung, Reihenfolge, Abhängigkeiten, je ein prüfbares Abnahmekriterium.
> **Produktmodell:** Gratis-Scan (alle Shops, nur Diagnose) → **bezahlte Auto-Fix-Shopify-App** (Done-for-you, $29/Mo Abo). Details: `PRICING.md`, `ARCHITECTURE.md`.
> **Stunden = die Stunden des Auftraggebers** (Setup, Prüfen, echtes Testen) — der Code selbst kommt von Claude Code.

---

## 0. Produkt-/Monetarisierungsmodell

| Ebene | Was | Preis | Plattform | Meilenstein |
|---|---|---|---|---|
| **Gratis-Scan** | Score 0–100 + Befunde (Diagnose, read-only, anonym) | $0 | **alle Shops** | M1–M4 |
| **Auto-Fix-App** | Shopify-App rendert & pflegt schema.org-Markup automatisch (installieren + freischalten) | **$29/Mo, 7-Tage-Trial** (Shopify Billing) | **nur Shopify** | **M5** |

**Kein Fallback-Produkt für Nicht-Shopify** (Entscheidung): ehrlicher Hinweis „Auto-Fix aktuell nur für Shopify". Spart Scope und hält das System **frei von Nutzer-PII**.

**Validierungssignal:** App-Installationen + Trial→Bezahlt-Rate.

---

## Reihenfolge & Abhängigkeiten

```
M0 Fundament ─► M1 Scan-Core ─► M2 Scoring ─► M3 Worker ─► M4 Frontend ─► M5 Shopify-App  ◄── ERSTER UMSATZ
                                                                                │
                                                                        M6 Launch + 1 Kanal
                                                                                │
                                                            (erst nach echten Abos:) M7 Support-Agent
```

---

## M0 — Fundament: Repo, CLAUDE.md, Schema, RLS
**~2–3 h**
- Monorepo: `web/` (Next.js: Marketing + Scan), `app/` (Shopify-App), `worker/` (Python), `db/` (SQL), `agentready/` (Docs).
- **Zuerst `CLAUDE.md`.** Supabase-Projekt **in EU-Region**; Schema (`ARCHITECTURE.md` §4); RLS default-deny auf **allen** Tabellen. `.env.example` vollständig. Lint/Format/Testrunner.
- **Abnahme:** Migration läuft; RLS aktiv auf jeder Tabelle; `.env.example` vollständig; Testbefehl grün; `CLAUDE.md` vorhanden.

## M1 — Scan-Core (Python, aufrufbar) — die Diagnose
**~3–5 h**
- `run_scan(url) -> ScanResult`: URL-Normalisierung + **SSRF**; robots.txt (Abbruch-Pfad); sitemap.xml; Plattform-Erkennung (Shopify); gestufte Produktseiten-Erkennung; höfliches Fetching (≥1 s, ≤15 Seiten, 10 s Timeout, ehrlicher UA, read-only); Parser JSON-LD/Microdata/RDFa → `Product`/`Offer`; **CSR-Erkennung**. Tests mit eingefrorenen Fixtures.
- **Abnahme:** Fixtures liefern erwartete Felder; SSRF weist private IPs/localhost/Metadaten ab; robots-blockiert → `blocked_by_robots`; CSR erkannt; Tests grün; **1 echter Shop erfolgreich gescannt**.

## M2 — Scoring-Engine (deterministisch)
**~2–4 h**
- Prüfungen A–E aus `SCORING.md`, Gewichte, kritische Gates G1–G4, „nicht anwendbar"-Logik, priorisierte Befund-Liste. **Snapshot-Tests.**
- **Abnahme:** gleiche Eingabe → exakt gleicher Score; Breakdown beantwortet „warum X?"; retrieval-blockierter Fixture → G1 ≤30; CSR-Fixture → G2.

## M3 — Worker-Laufzeit
**~2–4 h**
- Poll-Schleife, atomares Claiming, Status-Übergänge, Concurrency-Deckel, Domain-Delay, Logging, Kosten-Logging, **Heartbeat**, Stuck-Job-Reset. systemd-Unit für den **EU-VPS**.
- **Abnahme:** `queued` → `done` mit Score/Findings; zwei parallele Jobs respektieren den Deckel; Heartbeat aktualisiert; Worker-Neustart verliert keinen Job.

## M4 — Frontend: Gratis-Scan + App-CTA
**~3–5 h**
- Landingpage + Scan-Formular + **Turnstile**; `POST /api/scan` (Turnstile, SSRF, IP-Rate-Limit, Domain-Cache); Polling; Ergebnis-Ansicht (Score + Befunde + Evidence). **Plattform-abhängiger CTA:** Shopify → „Automatisch beheben — App installieren"; sonst → ehrlicher Hinweis. Teilbarer Ergebnis-Link.
- **Abnahme:** deployte Seite: URL rein → Fortschritt → Score + Befunde + korrekter CTA; Turnstile blockt ohne Verifikation; Domain-Cache greift; **keine Secrets im Client-Bundle**.

## M5 — Shopify-App (⭐ Bezahlprodukt / erster Umsatz)
**~10–18 h** · grösster Block, Shopify-Lernkurve inbegriffen
- Shopify-App (CLI + **Remix-Template**), **Session-Storage auf Postgres/Supabase umgestellt** (nicht die SQLite-Default!); **OAuth-Install** (`read_products`); **Theme-App-Extension mit Liquid-App-Embed**, der schema.org-JSON-LD **direkt aus dem `product`-Objekt** rendert (`ARCHITECTURE.md` §2.6 — **niemals fabrizieren**); **Shopify Billing** (7-Tage-Trial → $29/Mo); **App-Dashboard** mit Produkt-Preview (fehlende Felder) + **Verifikations-Re-Scan** (Score vorher/nachher); Pflicht-Webhooks (`app/uninstalled` + GDPR-Webhooks); sauberes Deinstallieren.
- **Abnahme:** App auf Dev-Store installieren → Embed im Theme-Editor einschalten → **Produktseite liefert valides Product-JSON-LD** (mit Googles Rich-Results-Test geprüft) → Re-Scan zeigt höheren Score → Billing-Trial startet und belastet danach → Deinstallation entfernt alles rückstandsfrei → **App schreibt nur in den installierenden Shop**.

## M6 — Launch + ein Kanal
**~3–5 h**
- Datenschutz + Löschpfad, Methodik-/„Warum dieser Score?"-Seite, finaler UA/robots + Kontakt-URL, **minimaler manueller Support** (Kontaktformular → Postfach). **App-Verbreitung:** zuerst **unlisted Install-Link** für die ersten Kunden (schnell), App-Store-Listing parallel einreichen (Review dauert). Ein Outbound-Kanal (personalisierte Scan-Antworten in Communities).
- **Abnahme:** 30 reale Shops scanbar; **mindestens 1 echter Shop installiert + im Billing**; Datenschutz-/Methodik-Seiten live.

---
### ⬇︎ Erst nach echten zahlenden Abos
---

## M7 — Support-Agent (vollständig)
**~8–14 h**
- Ganze Architektur aus `SUPPORT_AGENT.md`: Intake, Klassifikation/Routing, vier Tools + `escalate`, **Belegpflicht**, **Never-Autonomous-Gates im Code**, Stufe-A-Review-UI, **KI-Kennzeichnung** (EU-KI-VO Art. 50), **erzwungene KB-Rückkopplung** (DB-Trigger + UI), Metriken je Kategorie, Kostendeckel, KB-Seed.
- **Abnahme:** Ticket → klassifiziert → geerdeter Entwurf mit Belegen ODER Eskalation; money/complaint eskalieren immer; Eskalation nicht ohne KB-Artikel `resolved`; KI-Kennzeichnung überall; Budgetüberschreitung erzwingt `draft_only`.

## Später (nicht jetzt, nicht verbauen)
Content-Auto-Fix (Beschreibungen/Alt-Texte via Metafields, mit Freigabe), Pro-Tarif, WooCommerce-Plugin, Monitoring-Alerts, Multi-Shop/Agentur-Plan, Sichtbarkeitsprüfung gegen die Antwort-Engines.

---

## Summe & Zeitleiste

| Block | Deine Stunden |
|---|---:|
| M0–M4 (Gratis-Scan komplett) | ~12–21 h |
| **M5 (Shopify-App = Bezahlprodukt)** | **~10–18 h** |
| M6 (Launch) | ~3–5 h |
| **Bis zum ersten zahlenden Kunden (M0–M6)** | **~25–44 h** |
| M7 (Support, später) | ~8–14 h |

Bei **6–8 h/Woche → ~4–7 Wochen reine Arbeit**, mit Kalenderreibung **~6–10 Wochen (≈ 1,5–2,5 Monate)** bis zum ersten Umsatz. (Die Stunden sind *deine*: Setup, Prüfen, echtes Testen — Claude Code schreibt den Code.)

**Grösster Unsicherheitsfaktor:** M5, weil Shopify-App-Entwicklung neu ist — Dev-Store, OAuth, Theme-Editor-Toggle und Billing-Test kosten Lernzeit, nicht Tippzeit.

**Streich-/Vertag-Kandidaten:** App-Store-Listing (erst unlisted Link), Claude-Narrativ im Scan (rein deterministische Befunde reichen anfangs), Support-Agent (M7). **Nicht streichbar:** Korrektheit des Liquid-Markups + Verifikations-Re-Scan — das ist der Kern, für den bezahlt wird.
