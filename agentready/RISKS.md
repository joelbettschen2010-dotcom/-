# RISKS.md — AgentReady

> Was das Projekt kippen kann — je Risiko: Frühwarnsignal und Gegenmassnahme.
> Sortiert nach „wie schnell tötet es das Produkt bei 6–8 h/Woche".

---

### R1 — Claude-Kosten laufen aus dem Ruder
**Warum tödlich:** Offener Gratis-Scan × LLM = unbegrenzte variable Kosten → sprengt 30 CHF/Monat.
- **Frühwarnung:** Tages-Summe `claude_cost_cents` überschreitet Schwelle; ungewöhnlich viele Full-Report-Anforderungen.
- **Gegenmassnahme:** Zahlen-Score deterministisch (kein LLM); Narrativ nur bei E-Mail-Lead; Cache pro Domain; `MAX_*_COST_CENTS` + `MONTHLY_CLAUDE_BUDGET_CENTS`; bei Deckel → Narrativ/Autonomie aus. (`ARCHITECTURE.md` §8, `SCORING.md` §0)

### R2 — Falsche Befunde beschädigen die Glaubwürdigkeit
**Warum tödlich:** Ein Produkt, das Shops berät, darf nicht falsch liegen — ein blamabler Fehlbefund kostet den ganzen Kanal.
- **Frühwarnung:** Nutzer widersprechen einem Befund; Support-Tickets „das stimmt nicht".
- **Gegenmassnahme:** konservative Prüfungen mit **Evidence** (Snippet/URL) an jedem Finding; eingefrorene Fixtures + Snapshot-Tests (M1/M2); manuelle Stichprobe der ersten ~10 realen Scans vor breiter Streuung; „niedrige Konfidenz"-Hinweis statt Rateraten bei exotischen Shops.

### R3 — Worker-Ausfall (Single Point of Failure)
**Warum tödlich:** Ein Server; fällt er aus, hängen alle Scans in `queued` → Produkt wirkt kaputt.
- **Frühwarnung:** Heartbeat altert; `queued`-Scans älter als X min; steigende `running`-Dauer.
- **Gegenmassnahme:** `systemd Restart=always`; Stuck-Job-Reset; Heartbeat-Alarm; Frontend zeigt ehrlichen „Scan dauert länger"-Status statt Hänger.

### R4 — KB veraltet → Support-Auflösung sinkt
**Warum tödlich:** Belegt dominanter Faktor (RESEARCH §6.3); ohne Rückkopplung sinkt die Rate statt zu steigen.
- **Frühwarnung:** steigende Eskalations-/Wiederkontaktquote; viele Artikel `last_reviewed_at` > 90 Tage.
- **Gegenmassnahme:** **erzwungene** KB-Erstellung bei jeder Eskalation (DB-Trigger + UI, `SUPPORT_AGENT.md` §7); Frische-Ansicht; automatischer Autonomie-Rückfall bei reissenden Metriken.

### R5 — Anti-Bot/Cloudflare blockiert unseren höflichen Crawler
**Warum kritisch:** Manche (auch Shopify-)Shops blocken Bots → nicht scanbar; wirkt wie unser Fehler.
- **Frühwarnung:** steigende `failed`-Rate mit 403/429; bestimmte Domains dauerhaft blockiert.
- **Gegenmassnahme:** ehrlicher UA + Kontakt-URL, konservative Limits (weit unter Cloudflare-Schwellen); ehrliche Ergebnismeldung „konnten nicht vollständig zugreifen" statt Score-Fantasie; robots-Abbruch sauber kommuniziert.

### R6 — Eigene EU-KI-VO-Verletzung (Art. 50)
**Warum tödlich:** Ein Produkt, das zu KI berät und selbst die Transparenzpflicht bricht, ist unverteidigbar; Bussen bis 15 Mio. €/3 %.
- **Frühwarnung:** Support-Nachricht ohne KI-Kennzeichnung im Test; Produktcopy mit Garantie-Formulierung.
- **Gegenmassnahme:** KI-Kennzeichnung ab erster Nachricht (Code, nicht Prompt); Copy-Review gegen „keine Garantien" (Leitplanke 9); Launch-Checkliste vor 2.8.2026 (RESEARCH §7).

### R7 — Rechtlich/robots: Beschwerde einer gescannten Domain
**Warum kritisch:** Ein wütender Shop-Betreiber oder ToS-Verstoss kann Ärger/Reputationsschaden bringen.
- **Frühwarnung:** Beschwerde an Kontakt-URL; auffällige Zugriffe aus unserer IP in fremden Logs.
- **Gegenmassnahme:** strikte Leitplanken (nur öffentlich, robots-konform, read-only, ≤15 Seiten, Abbruch bei Disallow); Opt-out-/Kontaktweg an der UA-URL; keine Speicherung fremder PII.

### R8 — Crawler-Token-Liste driftet → falsches Scoring
**Warum relevant:** Bot-Namen ändern sich laufend (RESEARCH §1.2); veraltete Liste → falsche A-Kategorie-Bewertung.
- **Frühwarnung:** Nutzer: „ihr sagt, ich blocke X, tue ich nicht"; neue Bots in Fachpresse.
- **Gegenmassnahme:** Tokens als **versioniertes Fixture** (`ai_crawlers.json`) mit Tests; periodische Review (Quartal); gegen Primärquellen (OpenAI/Anthropic/Google) abgleichbar.

### R9 — Supabase-Free-Limits / Projekt-Pausierung
**Warum relevant:** Free-Projekte pausieren bei Inaktivität und haben Zeilen-/Storage-Grenzen → Worker-Poll könnte stillstehen; Tabellen wachsen.
- **Frühwarnung:** Supabase-Nutzungswarnung; Worker-Poll-Fehler nach Ruhephase.
- **Gegenmassnahme:** Worker-Poll + einfacher Keep-Alive hält Projekt wach; Retention-Job für alte Scans; Storage im Wochenblick.

### R10 — Zu geringe Nachfrage (das eigentliche Validierungsrisiko)
**Warum entscheidend:** Das Erfolgskriterium ist „wie viele fragen nach mehr?" — nicht „läuft die App".
- **Frühwarnung:** 30 Scans gemacht, ~0 Nachfragen nach wiederkehrender Version.
- **Gegenmassnahme:** Produkt ist bewusst schlank, damit dieses Signal **billig und schnell** gemessen wird; klarer Call-to-Action im Report („wöchentlich überwachen?"); nicht in Features investieren, bevor dieses Signal positiv ist.

### R11 — Scope-Creep / Zeitbudget
**Warum tödlich:** 6–8 h/Woche verzeihen keine verfrühte Verallgemeinerung.
- **Frühwarnung:** Arbeit an Dingen aus Brief §7; Meilensteine ohne Bezug zum Erfolgskriterium.
- **Gegenmassnahme:** Streich-Kandidaten in `PLAN.md`; „dient das den 30 Scans?"-Test je Meilenstein; Section-7-Features strikt vertagt.

### R12 — Datenschutz der Nutzer-E-Mail
**Warum relevant:** Einzige gespeicherte PII (Frage 9) — falsch gehandhabt = Vertrauens-/Rechtsproblem.
- **Frühwarnung:** Löschanfrage ohne funktionierenden Pfad; E-Mail ohne dokumentierte Einwilligung.
- **Gegenmassnahme:** Einwilligung (`consent_at`), Datenschutz-Hinweis, Löschpfad, minimale Speicherung, gehashte IPs; keine fremde PII.
