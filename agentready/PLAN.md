# PLAN.md — AgentReady

> Meilensteine mit Stundenschätzung, Reihenfolge, Abhängigkeiten, je ein prüfbares Abnahmekriterium.
> Gemessen am **Erfolgskriterium** (Brief §8): *30 echte Shops scannen und messen, wie viele nach einer wiederkehrenden Version fragen.* Was dem nicht dient, ist Ballast.
> Schätzungen sind für einen technisch versierten Solo-Entwickler mit Claude-Code-Unterstützung.

---

## Reihenfolge & Abhängigkeiten (Überblick)

```
M0 Fundament ──► M1 Scan-Core ──► M2 Scoring ──► M3 Worker ──► M4 Frontend ──► M5 Full-Report+Mail
                     │                │              │             │
                     └────────────────┴──────────────┴─────► M6 Support (braucht Schema M0, Scan-Tools M1/M3, etwas UI M4)
                                                                                        │
                                                                              M7 Ops & Launch ◄─ alles
```
M0→M5 weitgehend linear. M6 (Support, vollständig) baut auf M0/M1/M3/M4 auf. M7 zuletzt.

---

## M0 — Fundament: Repo, CLAUDE.md, Schema, RLS, Skeleton
**~3–4 h**
- Monorepo-Layout: `web/` (Next.js), `worker/` (Python), `db/` (SQL), `agentready/` (diese Docs).
- **Zuerst `CLAUDE.md`** anlegen (Kontext, Befehle, Konventionen, Leitplanken).
- Supabase-Projekt, Schema aus `ARCHITECTURE.md` §4 als echte Migration, RLS auf **allen** Tabellen (default-deny).
- `.env.example` mit allen Namen (§6), keine Werte. Linting/Formatting/Testrunner beidseitig.
- **Abnahme:** Migration läuft sauber; `\d` zeigt RLS aktiv auf jeder Tabelle; `.env.example` vollständig; CI/Testbefehl grün (leer ok); `CLAUDE.md` vorhanden.

## M1 — Scan-Core-Bibliothek (Python, aufrufbar)
**~8–12 h** · hängt an M0
- `run_scan(url) -> ScanResult` als reine Funktion. URL-Normalisierung + **SSRF-Validierung** (§7). robots.txt holen+parsen (Abbruch-Pfad `blocked_by_robots`). sitemap.xml parsen. Plattform-Erkennung (Shopify `/products.json`). Gestufte Produktseiten-Erkennung. Höfliches Fetching (≥1 s, ≤15 Seiten, 10 s Timeout, ehrlicher UA). Parser: JSON-LD/Microdata/RDFa → `Product`/`Offer`. **CSR-Erkennung.**
- **Tests mit eingefrorenen HTML-Fixtures** (Shopify-Shop, Nicht-Shopify mit JSON-LD, CSR-only-SPA, robots-blockiert, kaputtes Schema).
- **Risiko:** exotische/Nicht-Shopify-Shops (Produkterkennung) — 4–6 h Kern, +Puffer für Heuristik.
- **Abnahme:** Fixtures liefern erwartete extrahierte Felder; SSRF weist private IPs/localhost/Metadaten ab; robots-blockierter Fixture → `blocked_by_robots`; CSR-Fixture → als JS-only erkannt; Tests grün.

## M2 — Scoring-Engine (deterministisch)
**~6–8 h** · hängt an M1
- Alle Prüfungen A–E aus `SCORING.md`, Gewichte, **kritische Gates G1–G4**, „nicht anwendbar"-Logik, priorisierte Fix-Liste. llms.txt-Bonus + Pflicht-Ehrlichkeitshinweis.
- **Snapshot-Tests:** Fixture-Eingaben → exakt erwartete Scores/Breakdowns (Reproduzierbarkeit beweisen).
- **Abnahme:** Für jede Fixture ist der Score exakt reproduzierbar; „warum X?" aus dem Breakdown ablesbar; ein retrieval-blockierter Fixture wird durch G1 auf ≤30 gedeckelt; ein CSR-Fixture triggert G2.

## M3 — Worker-Laufzeit
**~4–6 h** · hängt an M1/M2
- Poll-Schleife, atomares Job-Claiming, Status-Übergänge (`queued→running→done/failed/blocked_by_robots`), Concurrency-Deckel, Domain-Delay, Logging, Kosten-Logging, Heartbeat, Stuck-Job-Reset. systemd-Unit + README.
- **Abnahme:** `queued`-Zeile einfügen → Worker verarbeitet → `done` mit `score`/`findings`; blockierte/fehlerhafte Fälle korrekt; zwei parallele Jobs überschreiten Concurrency nicht; Heartbeat aktualisiert.

## M4 — Frontend: Scan-Flow
**~8–12 h** · hängt an M3
- Landingpage + Scan-Formular + **Turnstile**. `POST /api/scan` (Turnstile-Verify, SSRF, IP-Rate-Limit, Domain-Cache) → `queued`. `GET /api/scan/:token` Polling. Report-Ansicht (Score, Kategorien, priorisierte Fixes) — **getrennt** von der Scan-Ausführung.
- **Abnahme:** Auf der deployten Seite URL eingeben → Fortschritt → Score + priorisierte Fixes; Turnstile blockt fehlende Verifikation; zweiter Scan derselben Domain in TTL liefert Cache; keine Secrets im Client-Bundle (Build-Check).

## M5 — Vollständiger Report per E-Mail + Claude-Narrativ
**~4–6 h** · hängt an M4
- E-Mail-Erfassung mit **Einwilligung** (`report_emails`) + Löschpfad. Claude-Narrativ (Erklärungen + Fix-Liste) **erst bei Anforderung**, Cache pro Domain, `MAX_SCAN_NARRATIVE_COST_CENTS` + Monatsdeckel. Versand via Resend (oder n8n). Datenschutz-Hinweis-Seite.
- **Abnahme:** E-Mail eingeben → vollständiger Report kommt an; Claude-Kosten geloggt & gedeckelt; zweite Anforderung derselben Domain nutzt Cache; Löschpfad funktioniert.

## M6 — Support-System (vollständig, Stufe A live)
**~12–16 h** · hängt an M0/M1/M3/M4
- Ganze Architektur aus `SUPPORT_AGENT.md`: Intake (Formular + optional n8n-E-Mail-In), Klassifikation/Routing, **vier Tools**, geerdeter Entwurf mit **Belegpflicht**, **Never-Autonomous-Gates** (Code), Stufe-A-**Review-UI**, **KI-Kennzeichnung**, **erzwungene KB-Rückkopplung** (Trigger + UI), Metrik-Ansichten je Kategorie, Stufen-Flags (B/C gebaut aber gesperrt), Kostendeckel. KB-Seed.
- **Abnahme:** Ticket → klassifiziert → geerdeter Entwurf **mit** Belegen ODER Eskalation ohne Beleg; `money`/`complaint` eskalieren immer; Mensch gibt im UI frei; eine Eskalation lässt sich **nicht** ohne KB-Artikel auf `resolved` setzen; jede KI-Nachricht trägt die Kennzeichnung; Metriken je Kategorie abfragbar; Budgetüberschreitung erzwingt `draft_only`.

## M7 — Betrieb & Launch-Feinschliff
**~4–6 h** · hängt an allem
- Retention-Job, Kosten-Dashboard/-Alarme, Heartbeat-Check, finaler UA/robots-Text + Kontakt-URL, Datenschutz + Löschpfad, KB mit Start-FAQ/Fehlerkatalog befüllt, Methodik-/„Warum dieser Score?"-Seite.
- **Abnahme:** 30 reale Shops scanbar; Monatskosten unter Budget belegt; KB geseedet; Datenschutz- & Methodik-Seiten live; Alarme feuern im Test.

---

## Summe & Reality-Check
**Gesamt ~49–68 h.** Realistisch über mehrere Wochenenden für einen Solo-Entwickler mit Claude-Code. Der Support (M6) ist der grösste Einzelblock — bewusst, weil er (Antwort Frage 3) **ganz** gebaut wird, aber sicher in Stufe A startet.

**Kandidaten zum Streichen, falls Zeit knapp** (dienen dem Erfolgskriterium nur mittelbar): pgvector (FTS reicht), n8n-E-Mail-In (Formular reicht), Kosten-Dashboard (einfacher Log + Alarm reicht). Realtime-Fortschritt ist bereits gestrichen (Polling).

**Was NICHT im Plan ist (Brief §7):** Konten/Abos, Zahlungen, Monitoring/Wochenreport, Sichtbarkeitsprüfung gegen Engines, Schema-Auto-Generierung, Shopify-App, Multi-Shop. Nur die drei Vorsorge-Punkte (nullable `user_id`, `run_scan()` als Funktion, Report getrennt).
