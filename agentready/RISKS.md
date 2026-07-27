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
**Warum entscheidend:** Das Erfolgskriterium ist „wie viele installieren die App und zahlen nach dem Trial?" — nicht „läuft die App".
- **Frühwarnung:** 30 Scans gemacht, ~0 App-Installationen; oder Installationen, aber Trial→Bezahlt nahe 0.
- **Gegenmassnahme:** Produkt ist bewusst schlank, damit dieses Signal **billig und schnell** gemessen wird; klarer Call-to-Action im Report („wöchentlich überwachen?"); nicht in Features investieren, bevor dieses Signal positiv ist.

### R11 — Scope-Creep / Zeitbudget
**Warum tödlich:** 6–8 h/Woche verzeihen keine verfrühte Verallgemeinerung.
- **Frühwarnung:** Arbeit an Dingen aus Brief §7; Meilensteine ohne Bezug zum Erfolgskriterium.
- **Gegenmassnahme:** Streich-Kandidaten in `PLAN.md`; „dient das den 30 Scans?"-Test je Meilenstein; Section-7-Features strikt vertagt.

### R13 — Plattform-Abhängigkeit Shopify (neu, durch das Bezahlprodukt)
**Warum kritisch:** Das gesamte Bezahlprodukt lebt in Shopifys Ökosystem — Regeln, App-Review, Revenue-Share und API können sich einseitig ändern. Zusätzlich: **App-Store-Review kann Tage bis Wochen dauern** und ist ausserhalb deiner Kontrolle.
- **Frühwarnung:** Review-Ablehnung; angekündigte API-/Policy-Änderungen; Deprecation-Hinweise auf genutzten Endpunkten.
- **Gegenmassnahme:** Erste Kunden über **unlisted Custom-App-Install-Link** (umgeht den Review auf dem kritischen Pfad); Listing parallel einreichen; API-Versionen gepinnt und im Quartals-Blick; der **Gratis-Scan ist plattformunabhängig** und bleibt der Lead-Kanal, falls Shopify wegbricht.

### R14 — App-Embed vom Merchant nicht eingeschaltet (stiller Killer)
**Warum kritisch:** Installation ≠ aktiv. Der Merchant muss den App-Embed im Theme-Editor **einschalten**. Passiert das nicht, zahlt er für nichts, sieht keinen Effekt und churnt — ohne dass du es merkst.
- **Frühwarnung:** `app_installs.embed_enabled = false` trotz aktivem Abo; Re-Scan zeigt keinen Score-Anstieg.
- **Gegenmassnahme:** Onboarding im Dashboard erzwingt den Schritt (Deep-Link in den Theme-Editor), **Statusanzeige „Embed aktiv/inaktiv"**, automatische Verifikation per Re-Scan, Erinnerung bei inaktivem Embed.

### R12 — Datenschutz
**Warum relevant:** PII an mehreren Stellen: Fallback-E-Mail (`report_emails`), Merchant-Shop-Domain + Token, Support-Ticket-E-Mails, gehashte IPs.
- **Frühwarnung:** Löschanfrage ohne funktionierenden Pfad; Shopify-Pflicht-GDPR-Webhooks nicht implementiert (**App-Review-Blocker!**); E-Mail ohne dokumentierte Einwilligung.
- **Gegenmassnahme:** Einwilligung (`consent_at`) + Löschpfad (`deleted_at`) beim Fallback; Shopifys **verpflichtende GDPR-Webhooks** (`customers/data_request`, `customers/redact`, `shop/redact`); Token verschlüsselt; keine fremde PII.

### R15 — Operator-Agent richtet Schaden an (neu)
**Warum kritisch:** Ein Agent mit Code-Zugriff auf ein Produkt, dessen Markup in **fremden Live-Shops** rendert, ist die höchste Blast-Radius-Komponente. Ein fehlerhafter autonomer Eingriff bricht zahlende Kunden **still**.
- **Frühwarnung:** Draft-PR berührt einen Denylist-Bereich; Vorschlag ohne Beleg; Agentenkosten laufen hoch; PR ohne Test.
- **Gegenmassnahme (Code, nicht Prompt):** **nie Merge/Push/Deploy** — nur Draft-PRs mit Tests, CI muss grün sein, Merge nur durch den Menschen; **harte Denylist** (Liquid-Embed, Billing, Auth/RLS, Scoring-Gewichte, Leitplanken-Code, Migrationen, Preise) — dort nicht mal ein Entwurf; GitHub-Token **ohne** Merge-Rechte; Belegpflicht; Kostendeckel pro Lauf/Monat; **Stufe C (vollautonom) existiert dauerhaft nicht**.
