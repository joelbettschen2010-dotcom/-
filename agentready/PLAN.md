# PLAN.md — AgentReady

> Meilensteine mit Stundenschätzung, Reihenfolge, Abhängigkeiten, je ein prüfbares Abnahmekriterium.
> **Produktmodell (Entscheidung):** Gratis-Scan (alle Shops, nur Diagnose) → **bezahlte Auto-Fix-Shopify-App** (Done-for-you, Abo). Details: `PRICING.md`, `ARCHITECTURE.md`.
> Leitprinzip: Der Kunde zahlt für **behobene** AI-Auffindbarkeit, nicht für einen Report — die App baut die Fixes automatisch ein und hält sie aktuell.

---

## 0. Produkt-/Monetarisierungsmodell (bestimmt die Reihenfolge)

| Ebene | Was | Preis | Plattform | Baut welcher Meilenstein |
|---|---|---|---|---|
| **Gratis-Scan** | Score 0–100 + Befunde (Diagnose, read-only) | $0 | **alle Shops** | M1–M4 |
| **Auto-Fix-App** | Shopify-App injiziert & pflegt schema.org-Markup automatisch (Kunde: installieren + freischalten) | **$29/Mo, 7-Tage-Trial** (Shopify Billing) | **nur Shopify** | **M5–M6** |
| **Content-Auto-Fix** (später) | Beschreibungen/Alt-Texte per Claude, mit Freigabe | im Abo | Shopify | später |

**Validierungssignal:** App-Installationen und Trial→Bezahlt-Conversion. Erst danach Support-Agent (M8) und Erweiterungen.

**Warum die App der Fix-Weg ist:** Eine **Theme-App-Extension (App-Embed)** rendert das JSON-LD *dynamisch aus den echten Produktdaten* — nicht ein einmaliges Schreiben. Dadurch immer korrekt, selbstpflegend, reversibel (Toggle), überlebt Theme-Updates. Kunden­aufwand = installieren + einschalten.

---

## Reihenfolge & Abhängigkeiten

```
M0 Fundament ─► M1 Scan-Core ─► M2 Scoring ─► M3 Worker ─► M4 Frontend+Scan-Teaser ─► M5 Fix-Engine ─► M6 Shopify-App  ◄── ERSTER UMSATZ
                                                                                                              │
                                                                                            M7 Launch (App-Listing + 1 Kanal)
                                                                                                              │
                                                                                        (nach echten Abos:) M8 Support-Agent (voll)
```
M0→M6 = kürzester Pfad zum zahlenden Kunden. Fix-Engine (M5) ist der geteilte Kern; die App (M6) ist die Liefer- und Abrechnungsschicht darauf.

---

## M0 — Fundament: Repo, CLAUDE.md, Schema, RLS
**~3–4 h**
- Monorepo: `web/` (Next.js: Marketing + Scan), `app/` (Shopify-App), `worker/` (Python), `db/` (SQL), `agentready/` (Docs).
- **Zuerst `CLAUDE.md`** (Kontext, Befehle, Konventionen, Leitplanken, Produktmodell).
- Supabase-Schema (`ARCHITECTURE.md` §4) inkl. `shops`, `app_installs`, `subscriptions`, `fixes`; RLS auf **allen** Tabellen (default-deny).
- `.env.example` mit allen Namen (inkl. Shopify-App + Billing), keine Werte. Lint/Format/Testrunner.
- **Abnahme:** Migration läuft; RLS aktiv auf jeder Tabelle; `.env.example` vollständig; Testbefehl grün; `CLAUDE.md` vorhanden.

## M1 — Scan-Core (Python, aufrufbar) — die Diagnose
**~8–12 h** · hängt an M0
- `run_scan(url) -> ScanResult`: URL-Normalisierung + **SSRF**; robots.txt (Abbruch-Pfad); sitemap.xml; Plattform-Erkennung (Shopify `/products.json`); gestufte Produktseiten-Erkennung; höfliches Fetching (≥1 s, ≤15 Seiten, 10 s Timeout, ehrlicher UA, read-only); Parser JSON-LD/Microdata/RDFa → `Product`/`Offer`; **CSR-Erkennung**. Tests mit eingefrorenen Fixtures.
- **Abnahme:** Fixtures liefern erwartete Felder; SSRF weist private IPs ab; robots-blockiert → `blocked_by_robots`; CSR erkannt; Tests grün.

## M2 — Scoring-Engine (deterministisch)
**~6–8 h** · hängt an M1
- Prüfungen A–E aus `SCORING.md`, Gewichte, kritische Gates G1–G4, priorisierte Befund-Liste. Snapshot-Tests.
- **Abnahme:** Score exakt reproduzierbar; „warum X?" ablesbar; retrieval-blockiert → G1 ≤30; CSR → G2.

## M3 — Worker-Laufzeit
**~4–6 h** · hängt an M1/M2
- Poll-Schleife, atomares Claiming, Status, Concurrency-Deckel, Logging, Kosten-Logging, Heartbeat, Stuck-Job-Reset. systemd-Unit (EU-VPS, `ARCHITECTURE.md` §8). Verifikations-Re-Scan als Funktion.
- **Abnahme:** `queued` → `done` mit Score/Findings; parallele Jobs respektieren Concurrency; Heartbeat aktualisiert.

## M4 — Frontend: Gratis-Scan + „Automatisch fixen"-CTA
**~7–10 h** · hängt an M3
- Landingpage + Scan-Formular + **Turnstile**; `POST /api/scan` (Turnstile, SSRF, Rate-Limit, Domain-Cache); Polling; Ergebnis-Ansicht (Score + Befunde). **Klartext-CTA je nach Plattform:** Shopify erkannt → „Automatisch beheben — Shopify-App installieren"; sonst → „Fix-Plan als PDF" (Fallback). Teilbarer Ergebnis-Link.
- **Abnahme:** URL rein → Fortschritt → Score + Befunde + passender CTA; Shopify-Shops sehen den App-CTA; keine Secrets im Bundle.

## M5 — Fix-Engine: korrektes Markup aus echten Daten (der geteilte Kern)
**~8–12 h** · hängt an M1
- `generate_fixes(shop_products) -> FixSet`: erzeugt **valides schema.org-JSON-LD** aus den echten Produktdaten (Shopify Admin/Storefront-API bzw. `/products.json`), **deterministische Templates** — **niemals fabrizieren** (fehlende GTIN/Marke → als „braucht Merchant-Input" markiert, nicht erfunden). Claude nur für Sprach-Teile (Beschreibungs-/Alt-Text-Entwürfe, mit Freigabe). Tests mit Fixture-Produktdaten → erwartetes JSON-LD.
- **Abnahme:** Fixture-Produkte → valides, vollständiges JSON-LD (Preis>0, Währung, availability, brand/gtin wo vorhanden); erfundene Felder kommen nicht vor; Tests grün.

## M6 — Shopify-App (⭐ Bezahlprodukt / erster Umsatz)
**~16–24 h** · hängt an M4/M5 · **grösster Block, bewusst**
- Shopify-App (Shopify-CLI; Remix-Template ist der Standardweg — eingebautes OAuth/Billing/App-Bridge; auf VPS/Vercel gehostet). **OAuth-Installation**; **Theme-App-Extension / App-Embed-Block**, der das aus M5 erzeugte JSON-LD **dynamisch auf jeder Produktseite** rendert; **Shopify Billing** (7-Tage-Trial → $29/Mo); **Verifikations-Re-Scan** zeigt den Score-Anstieg im App-Dashboard; sauberes Deinstallieren/Toggle (Embed entfernt sich). Optional: `robots.txt.liquid` Retrieval-Bots freigeben.
- **Abnahme:** App auf Dev-Store installieren → App-Embed rendert valides Product-JSON-LD aus Live-Daten → Re-Scan zeigt höheren Score → Abo via Shopify Billing (Trial, dann Belastung) → Deinstallation entfernt den Embed rückstandsfrei; schreibt **nur** in den installierenden Shop.

## M7 — Launch + ein Kanal
**~5–8 h** · hängt an M6
- Datenschutz + Löschpfad, Methodik-/„Warum dieser Score?"-Seite, finaler UA/robots + Kontakt-URL, **minimaler manueller Support** (Kontaktformular → Postfach). **App-Verbreitung:** entweder App-Store-Listing (Review-Prozess einplanen) **oder** unlisted Custom-App-Install-Link für die ersten Kunden (schneller). Ein Outbound-Kanal (personalisierte Scan-Ergebnisse).
- **Abnahme:** 30 reale Shops scanbar; App auf mind. einem echten Shop live installiert + abgerechnet; Datenschutz-/Methodik-Seiten live.

---
### ⬇︎ Ab hier erst nach echten Abos (Validierung bestanden)
---

## M8 — Support-Agent (vollständig)
**~12–16 h** · hängt an M0/M1/M6 · bewusst spät
- Ganze Architektur aus `SUPPORT_AGENT.md`: Intake, Klassifikation/Routing, vier Tools + `escalate`, Belegpflicht, Never-Autonomous-Gates (Code), Stufe-A-Review-UI, KI-Kennzeichnung (EU-KI-VO Art. 50), erzwungene KB-Rückkopplung (Trigger+UI), Metriken je Kategorie, Kostendeckel, KB-Seed.
- **Abnahme:** Ticket → klassifiziert → geerdeter Entwurf mit Belegen ODER Eskalation; money/complaint eskalieren immer; Eskalation nicht ohne KB-Artikel `resolved`; KI-Kennzeichnung überall; Budgetüberschreitung erzwingt `draft_only`.

## Später (nicht jetzt, nicht verbauen)
Content-Auto-Fix (Beschreibungen/Alt-Texte mit Freigabe), WooCommerce-Plugin, Nicht-Shopify-DIY-Export, Monitoring-Alerts als eigener Tarif, Multi-Shop/Agentur-Plan.

---

## Summe & Reality-Check
**Time-to-first-dollar (M0–M6) ≈ 52–76 h** → bei 6–8 h/Woche **~8–12 Wochen bis zur zahlfähigen App.** Gesamt (M0–M8) ≈ **66–92 h**.

**Bewusster Trade-off:** Done-for-you (Auto-Fix-App) ist mehr Bau und **späterer** erster Umsatz als das frühere DIY-$79-Modell — dafür klebriger (Abo) und mit dem App Store als Distributionskanal. Diese Wahl ist getroffen.

**Streich-/Vertag-Kandidaten bei Zeitdruck:** App-Store-Listing → erst unlisted Install-Link; PDF-Fallback für Nicht-Shopify; Content-Auto-Fix; Support-Agent nach hinten. **Nicht streichbar:** Fix-Engine-Korrektheit (M5) + App-Embed-Verifikation (M6) — das ist der Kern, für den bezahlt wird.
