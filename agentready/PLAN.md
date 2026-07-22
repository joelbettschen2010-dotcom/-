# PLAN.md — AgentReady

> Meilensteine mit Stundenschätzung, Reihenfolge, Abhängigkeiten, je ein prüfbares Abnahmekriterium.
> **Neue Go-to-Market-Strategie (Fokus: Jahr-1-Profit):** Gratis-Teaser als Traffic-Köder → **einmaliger $79-Audit** als Hauptprodukt → Monitoring später als Upsell. Details: `REVENUE_PROJECTION.md`.
> Leitprinzip der Reihenfolge: **Time-to-first-dollar.** Der erste zahlende Kunde kommt so früh wie möglich (~Monat 2), nicht am Ende.

---

## 0. Produkt-/Monetarisierungsmodell (bestimmt die Reihenfolge)

| Ebene | Was | Preis | Baut welcher Meilenstein |
|---|---|---|---|
| **Gratis-Teaser** | Score 0–100 + Top-3-Probleme (teilbar) | $0 | M4 |
| **Hauptprodukt** | Voller „AI-Readiness Audit + Fix Plan" (alle Befunde, Claude-Erklärungen, Umsetzung) | **$79 einmalig** | **M5** |
| **Upsell** | Monitoring: wöchentlicher Re-Scan + Report + Alerts | $29/Mon. / $290/Jahr | M7 (nach Validierung) |

**Validierungssignal:** Zahlungsbereitschaft ($79-Käufe), nicht eine Umfrage. Erst wenn Käufe kommen, werden Monitoring (M7) und der volle Support-Agent (M8) gebaut.

---

## Reihenfolge & Abhängigkeiten

```
M0 Fundament ─► M1 Scan-Core ─► M2 Scoring ─► M3 Worker ─► M4 Frontend+Teaser ─► M5 Checkout+$79-Audit  ◄── ERSTER FRANKEN
                                                                                        │
                                                                         M6 Launch + 1 Outbound-Kanal
                                                                                        │
                                        (nur nach echten Käufen:)  M7 Monitoring-Upsell ─► M8 Support-Agent (voll)
```
M0→M5 linear = kürzester Pfad zum Umsatz. M6 macht verkaufsfähig. M7/M8 folgen erst nach Validierung durch Käufe.

---

## M0 — Fundament: Repo, CLAUDE.md, Schema, RLS, Skeleton
**~3–4 h**
- Monorepo: `web/` (Next.js), `worker/` (Python), `db/` (SQL), `agentready/` (Docs).
- **Zuerst `CLAUDE.md`** (Kontext, Befehle, Konventionen, Leitplanken, Monetarisierungsmodell).
- Supabase-Schema aus `ARCHITECTURE.md` §4 inkl. **`purchases`-Tabelle**; RLS auf **allen** Tabellen (default-deny).
- `.env.example` mit allen Namen (inkl. Payment-Provider), keine Werte. Lint/Format/Testrunner beidseitig.
- **Abnahme:** Migration läuft; RLS auf jeder Tabelle aktiv; `.env.example` vollständig; Testbefehl grün; `CLAUDE.md` vorhanden.

## M1 — Scan-Core-Bibliothek (Python, aufrufbar)
**~8–12 h** · hängt an M0
- `run_scan(url) -> ScanResult` als reine Funktion: URL-Normalisierung + **SSRF-Validierung**; robots.txt (Abbruch-Pfad `blocked_by_robots`); sitemap.xml; Plattform-Erkennung (Shopify `/products.json`); gestufte Produktseiten-Erkennung; höfliches Fetching (≥1 s, ≤15 Seiten, 10 s Timeout, ehrlicher UA, read-only); Parser JSON-LD/Microdata/RDFa → `Product`/`Offer`; **CSR-Erkennung**.
- **Tests mit eingefrorenen HTML-Fixtures** (Shopify, Nicht-Shopify+JSON-LD, CSR-only, robots-blockiert, kaputtes Schema).
- **Abnahme:** Fixtures liefern erwartete Felder; SSRF weist private IPs/localhost/Metadaten ab; robots-blockiert → `blocked_by_robots`; CSR-Fixture erkannt; Tests grün.

## M2 — Scoring-Engine (deterministisch)
**~6–8 h** · hängt an M1
- Prüfungen A–E aus `SCORING.md`, Gewichte, **kritische Gates G1–G4**, „nicht anwendbar"-Logik, priorisierte Fix-Liste, llms.txt-Bonus + Ehrlichkeitshinweis. **Trennung Teaser (Score + Top-3) vs. voller Report** vorbereiten (gleiche Berechnung, unterschiedliche Sichtbarkeit).
- **Snapshot-Tests:** Fixture → exakter Score/Breakdown.
- **Abnahme:** Score exakt reproduzierbar; „warum X?" aus Breakdown ablesbar; retrieval-blockierter Fixture durch G1 ≤30; CSR-Fixture triggert G2.

## M3 — Worker-Laufzeit
**~4–6 h** · hängt an M1/M2
- Poll-Schleife, atomares Job-Claiming, Status-Übergänge, Concurrency-Deckel, Domain-Delay, Logging, Kosten-Logging, Heartbeat, Stuck-Job-Reset. systemd-Unit + README.
- **Abnahme:** `queued`-Zeile → `done` mit Score/Findings; blockiert/fehlerhaft korrekt; zwei Jobs respektieren Concurrency; Heartbeat aktualisiert.

## M4 — Frontend: Gratis-Scan + **Teaser** (der Traffic-Köder)
**~7–10 h** · hängt an M3
- Landingpage + Scan-Formular + **Turnstile**; `POST /api/scan` (Turnstile, SSRF, IP-Rate-Limit, Domain-Cache); `GET /api/scan/:token` Polling; **Teaser-Report-Ansicht: Score + Top-3-Probleme sichtbar, Rest verdeckt** mit Klartext-CTA „Vollen Fix-Plan freischalten"; teilbarer Ergebnis-Link. Rendering **getrennt** von der Ausführung.
- **Abnahme:** deployte Seite: URL rein → Fortschritt → Score + Top-3 + verdeckter Rest + CTA; Turnstile blockt fehlende Verifikation; Domain-Cache greift; keine Secrets im Bundle.

## M5 — **Monetarisierung: Checkout + voller $79-Audit** ⭐ erster Franken
**~5–8 h** · hängt an M4
- **Lemon Squeezy** (oder Stripe Payment Link) Checkout für den $79-Audit; Webhook verifiziert Kauf → `purchases`-Zeile `paid` → schaltet **vollen Report** frei (alle Befunde + **Claude-Narrativ/Erklärungen** + Umsetzungsanleitung). E-Mail-/Kauf-Erfassung mit **Einwilligung** + Löschpfad. Claude-Narrativ nur für **zahlende** Käufe erzeugt → Kosten an Umsatz gekoppelt, Cache pro Domain, Budgetdeckel.
- **Abnahme:** Bezahlen → voller Report sofort freigeschaltet und (optional) per Mail zugestellt; Webhook-Manipulation greift nicht (Signatur geprüft); Claude-Kosten geloggt & gedeckelt; Löschpfad funktioniert; ohne Zahlung bleibt der Rest verdeckt.

## M6 — Launch + ein Outbound-Kanal
**~3–5 h** · hängt an M5
- Datenschutz + Löschpfad-Seite, Methodik-/„Warum dieser Score?"-Seite, finaler UA/robots-Text + Kontakt-URL, **minimaler manueller Support** (Kontaktformular → dein Postfach), teilbares Ergebnis + einfacher Outbound-Flow (du scannst Shops, verschickst personalisiertes Ergebnis + CTA).
- **Abnahme:** 30 reale Shops scanbar & verkaufsfähig; Kaufabwicklung end-to-end live; Datenschutz-/Methodik-Seiten live; ein Testverkauf durchgelaufen.

---
### ⬇︎ Ab hier erst nach echten $79-Käufen (Validierung bestanden)
---

## M7 — Monitoring-Upsell (Wiederkehr, sät Jahr 2)
**~6–9 h** · hängt an M5/M6
- Konto per Magic-Link (Supabase-Auth); Abo-Checkout $29/Mon. **und $290/Jahr**; Cron-**Re-Scan** (nutzt `run_scan()` wieder); **Wochen-Report-Mail**; Alerts bei Score-Verschlechterung; RLS-Policies auf `user_id` scharf schalten.
- **Abnahme:** Käufer schaltet Monitoring frei → wöchentlicher Re-Scan läuft automatisch → Report kommt; Jahres-Prepay buchbar; Konto sieht nur eigene Daten (RLS).

## M8 — Support-Agent (vollständig, wie geplant)
**~12–16 h** · hängt an M0/M1/M3, kommt **bewusst spät**
- Ganze Architektur aus `SUPPORT_AGENT.md`: Intake, Klassifikation/Routing, vier Tools + `escalate`, geerdeter Entwurf mit **Belegpflicht**, **Never-Autonomous-Gates** (Code), Stufe-A-**Review-UI**, **KI-Kennzeichnung** (EU-KI-VO Art. 50), **erzwungene KB-Rückkopplung** (Trigger + UI), Metriken je Kategorie, Stufen-Flags B/C (gebaut, gesperrt), Kostendeckel, KB-Seed.
- **Abnahme:** Ticket → klassifiziert → geerdeter Entwurf mit Belegen ODER Eskalation ohne Beleg; money/complaint eskalieren immer; Mensch gibt frei; Eskalation nicht ohne KB-Artikel `resolved`; KI-Kennzeichnung überall; Metriken abfragbar; Budgetüberschreitung erzwingt `draft_only`.

---

## Summe & Reihenfolge-Logik
**Time-to-first-dollar (M0–M5) ≈ 33–48 h** → bei 6–8 h/Woche **~5–8 Wochen → erster Verkauf ~Monat 2.** Gesamt (M0–M8) ≈ **54–78 h**.

**Bewusste Entscheidung:** Der volle Support-Agent (M8) bleibt „das ganze" wie besprochen — aber **nach** den ersten Umsätzen, weil er kein Umsatz treibt und Zeit-bis-erster-Franken kostet. Bis dahin genügt minimaler manueller Support (M6). *Wenn du ihn lieber vor dem Launch willst, sag Bescheid — dann tausche ich M8 vor M6/M7.*

**Streich-/Vertag-Kandidaten bei Zeitdruck:** pgvector (FTS reicht), n8n-E-Mail-In (Formular reicht), Alerts in M7. Realtime-Fortschritt ist bereits durch Polling ersetzt.

**Was NICHT gebaut wird (Brief §7):** Konten-Vollsystem/Multi-Shop, Zahlungen über das Nötige hinaus, Sichtbarkeitsprüfung gegen Engines, Schema-Auto-Generierung, Shopify-App. Nur die drei Vorsorge-Punkte (nullable `user_id`, `run_scan()` als Funktion, Report getrennt) — plus jetzt die schlanke `purchases`-Tabelle, die die neue Strategie braucht.
