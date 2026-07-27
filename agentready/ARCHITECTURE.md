# ARCHITECTURE.md — AgentReady

> Architektur des Validierungsprodukts. Sprache: Deutsch (Kommunikation); Bezeichner/Code Englisch.
> Enthält Schema als SQL **im Dokument** (keine ausführbare Migration) und kurzen Pseudocode zur Illustration.
> Entscheidungen basieren auf `RESEARCH.md` und den Antworten des Auftraggebers (Fragen 1–9).

---

## 1. Überblick & Leitidee

Drei Laufzeit-Bausteine, strikt entkoppelt über die Supabase-Datenbank als Nachrichten-/Zustandsschicht:

```
  Browser ──HTTPS──► Next.js (Vercel Hobby) ──service_role──► Supabase (Postgres)
     ▲   POST /api/scan  (Turnstile, SSRF, Rate-Limit, Cache)        ▲
     │   GET  /api/scan/:token (Polling Fortschritt/Report)          │ claim/poll
     │                                                     service_role│ (queued→running→done)
     └────────────────── Report (Score + Fix-Liste) ◄─────  Python Scan-Worker
                                                            (kleiner EU-VPS, systemd)
                                                                    │
                                                            Claude API (nur Narrativ + Support)

  Shopify-Merchant ──installiert──► Shopify-App (Remix)  ──read_products──► Shopify Admin API
                                          │                                    (Dashboard/Preview)
                                          └──► Theme-App-Extension (Liquid App-Embed)
                                               rendert JSON-LD im Theme des Merchants
                                               ⚠ läuft OHNE unseren Server weiter
```

**Warum diese Entkopplung:** Vercel-Funktionen haben zu kurze Laufzeitgrenzen für einen 30–120-s-Crawl. Der Worker läuft als langlebiger Dienst auf dem eigenen Server. Frontend und Worker reden **nie** direkt miteinander — nur über die `scans`-Tabelle. Das überlebt Worker-Neustarts, braucht keine Extra-Queue-Infrastruktur und hält die 6–8-h/Woche-Randbedingung.

**Der Client spricht nie direkt mit Supabase.** Jeder DB-Zugriff läuft server-seitig (Next.js Route Handler mit `service_role`, oder Worker). Damit ist RLS trivially wasserdicht: alle Tabellen default-deny, kein `anon`-Zugriff nötig. (Details §5.)

**Vierte Komponente (Bezahlprodukt): die Shopify-App.** Getrennt von der Marketing-/Scan-Seite (eigenes `app/`), läuft als Shopify-eingebettete App (OAuth, App-Bridge, Shopify Billing) und schreibt **ausschliesslich in den Shop des installierenden Merchants** — mit dessen OAuth-Erlaubnis. Der Fix ist eine Theme-App-Extension (App-Embed), die schema.org-JSON-LD **dynamisch aus den Live-Produktdaten** rendert (§2.5). Klar getrennt vom read-only-Scan fremder Shops (Leitplanke 4 gilt fürs Scannen; die App schreibt nur in den eigenen Shop des zahlenden Kunden).

---

## 2. Komponenten

### 2.1 Frontend — Next.js 15 App Router (Vercel Hobby)
- **Öffentliche Landingpage** mit Scan-Formular (URL-Eingabe) + Cloudflare-Turnstile-Widget.
- **Route Handler `POST /api/scan`** (server-seitig):
  1. Turnstile-Token server-seitig verifizieren.
  2. URL normalisieren + **SSRF-Validierung** (Leitplanke 7, siehe §7).
  3. IP-Rate-Limit prüfen (gehashte IP) + **Domain-Cache** (gleiche Domain frisch < TTL → bestehenden Scan zurückgeben).
  4. `scans`-Zeile `status='queued'` per `service_role` einfügen, `public_token` zurückgeben.
- **Route Handler `GET /api/scan/:public_token`**: liest Status/Report server-seitig, gibt JSON zurück. Der Browser **pollt** diese Route alle ~2 s bis `done`/`failed`.
  - *Bewusste Vereinfachung ggü. Supabase Realtime:* Polling einer Server-Route hält RLS airtight (kein Client-DB-Zugriff nötig) und ist bei 1–3-min-Scans völlig ausreichend. Realtime ist ein optionales späteres Upgrade.
- **Report-Ansicht**: rendert Score (0–100), Kategorie-Aufschlüsselung und priorisierte Fix-Liste aus dem `report`-JSON. Report-Rendering ist **getrennt** von der Scan-Ausführung (Vorsorge, Abschnitt 7 des Briefs).
- **Kein E-Mail-Gate, kein Nicht-Shopify-Fallback** (Entscheidung): Der Scan ist vollständig gratis und anonym; Shopify-Shops sehen den App-CTA, Nicht-Shopify-Shops einen ehrlichen Hinweis „Auto-Fix aktuell nur für Shopify". → **Es wird keine Nutzer-E-Mail gespeichert** (siehe §7).

### 2.2 Scan-Worker — Python 3.12 (kleiner EU-VPS, systemd)
- Langlebiger Dienst. **Poll-Schleife**: beansprucht `queued`-Scans atomar (`UPDATE ... WHERE status='queued' ... RETURNING`, bzw. `FOR UPDATE SKIP LOCKED`), setzt `running`, führt `run_scan(url)` aus, schreibt Ergebnis, setzt `done` / `failed` / `blocked_by_robots`.
- **Concurrency-Deckel** (z. B. 2–3 gleichzeitige Scans), globaler Höflichkeits-Delay pro Domain.
- Nutzt `service_role`-Key (Server-Env) und `ANTHROPIC_API_KEY`.
- **`run_scan(url)` ist eine aufrufbare Funktion**, kein Skript-Monolith (Vorsorge, Abschnitt 7). Dieselbe Funktion wird später vom Monitoring wiederverwendet.

### 2.3 Datenbank — Supabase (Postgres) Free
- Job-Queue + Zustandsspeicher + Wissensbasis + Ticket-System. Schema §4, RLS §5.

### 2.4 KI — Claude API
- **Nicht im Zahlen-Score** (Frage 2). Nur:
  1. **Scan-Narrativ** (Befunde in Klartext + priorisierte Fix-Hinweise) im Gratis-Ergebnis — knapp gehalten, Cache pro Domain, harter Monatsdeckel. Der eigentliche *Fix* ist die App (§2.5), nicht der Text.
  2. **Support-Agent** (siehe `SUPPORT_AGENT.md`).
- Modell-Split über Env: günstiges Modell (Klassifikation/Routing), stärkeres (Antwort/Narrativ).

### 2.5 Shopify-App (Bezahlprodukt)
- Eigene Shopify-eingebettete App (Shopify-CLI; **Remix-Template = Standardweg** mit eingebautem OAuth/Billing/App-Bridge).
- **OAuth-Installation** mit minimalem Scope: **`read_products`** — ausschliesslich fürs Dashboard/Preview. **Der Fix-Embed selbst braucht gar keinen Scope** (er rendert im Theme-Kontext, §2.6). Access-Token **verschlüsselt** in `app_installs`.
- **⚠ Session-Storage-Falle:** Das Remix-Template nutzt per Default **Prisma + SQLite** — das funktioniert auf serverless (Vercel) **nicht** (kein persistentes Dateisystem). → **Session-Storage auf Postgres/Supabase umstellen** (oder die App auf dem VPS als Node-Prozess betreiben). Muss beim Bau als Erstes erledigt werden.
- **Shopify Billing** (7-Tage-Trial → $29/Mo, `PRICING.md`) über die GraphQL-Admin-API (`appSubscriptionCreate`) — **kein eigener Scope nötig**, kein Stripe/Lemon. Shopify wickelt Zahlung + Steuer ab; 0 % Plattformgebühr bis $1 Mio./Jahr.
- **App-Dashboard:** Produkt-Übersicht mit fehlenden Feldern (Preview), **Verifikations-Re-Scan** (Score vorher/nachher) und ehrliche Liste der nicht-auto-fixbaren Punkte (fehlende GTIN, CSR).
- **Eigener Shop ≠ fremder Shop:** Die 15-Seiten-/Höflichkeitsgrenzen (Leitplanke 3) gelten für den **Scan fremder Shops**. Im **installierten** Shop liest die App mit Einwilligung des Merchants **alle** Produkte über die Admin-API (paginiert, respektiert API-Limits).

### 2.6 Der Fix: Liquid-App-Embed (kein Server im Pfad)
**Entscheidung (wichtig):** Das schema.org-JSON-LD wird **direkt im Liquid-Template der Theme-App-Extension** aus dem `product`-Objekt gerendert — **nicht** server-seitig erzeugt und synchronisiert.

| | server-erzeugt + Sync | **Liquid-App-Embed (gewählt)** |
|---|---|---|
| Datenaktualität | Webhooks/Sync nötig | immer live |
| Admin-API-Limits | ja | entfällt |
| **VPS-Ausfall** | Markup veraltet/kaputt | **läuft weiter** ✅ |
| Bauaufwand | Engine + Sync + Abruf | ein Liquid-Template |

- **Warum es geht:** Ein App-Embed-Block wird in `theme.liquid` eingehängt und hat auf Produktseiten Zugriff auf das globale `product`-Objekt (Standardweg aller Schema-Apps).
- **Niemals fabrizieren (harte Regel):** Nur Felder ausgeben, die real vorhanden sind. Fehlende GTIN/Marke → Feld **weglassen**, nicht erfinden (Google bestraft falsches Markup; es täuscht die Agenten). Im Dashboard als „braucht Merchant-Input" ausweisen.
- **Server-seitig bleibt nur:** die **Analyse** (was fehlt — das kann die Scan-/Scoring-Logik aus M1/M2 bereits), das Dashboard-Preview und der Verifikations-Re-Scan.
- **Claude nur für Sprache (später):** Beschreibungs-/Alt-Text-Entwürfe bei dürftigen Daten — in **Metafields** abgelegt, vom Liquid gelesen, **nur mit Merchant-Freigabe**.

---

## 3. Datenfluss (Scan, End-to-End)

1. Nutzer gibt Shop-URL ein, löst Turnstile → `POST /api/scan`.
2. Route Handler validiert (Turnstile, SSRF, Rate-Limit, Cache) → `scans`-Zeile `queued`, `public_token` zurück.
3. Browser navigiert zu `/scan/:public_token`, pollt `GET /api/scan/:public_token`.
4. Worker beansprucht Job → `running`. Führt aus:
   a. URL normalisieren, DNS auflösen, **SSRF-Recheck der aufgelösten IP**.
   b. `robots.txt` holen (immer erlaubt) und **analysieren** (für Crawler-Zugang-Score). Verbietet sie *unserem* Agenten das Crawlen → wir brechen ab und melden `blocked_by_robots` als Ergebnis (Leitplanke 2).
   c. `sitemap.xml` holen/parsen; Plattform erkennen (Shopify via `/products.json` / `cdn.shopify.com` / Header).
   d. Produktseiten wählen: gestuft (Shopify `/products.json` → Sitemap-Produkt-URLs → Startseite + Link-Heuristik + `JSON-LD @type=Product`). **Deckel 15 Seiten.**
   e. Jede Seite höflich holen (≥1 s/Domain, 10 s Timeout, ehrlicher UA, read-only), rohes HTML parsen: JSON-LD/Microdata/RDFa extrahieren, `Product`/`Offer`-Felder, **CSR-Erkennung** (leeres Rohtext-HTML + grosses JS-Bundle / Sitemap voller Produkte).
   f. **Deterministische Checks** → Kategorie-Scores → Gesamt 0–100 mit kritischen Gates (siehe `SCORING.md`).
   g. Findings (`id, category, severity, status, evidence, fix, weight`) + priorisierte Fix-Liste bauen.
5. Worker schreibt `score`, `score_breakdown`, `findings`, `report`, setzt `done`.
6. Browser-Poll erhält `done` → **Ergebnis** (Score + Befunde) rendert. Bei erkanntem Shopify-Shop: CTA „Automatisch beheben — Shopify-App installieren" → OAuth-Install (§2.5) → Merchant schaltet App-Embed im Theme-Editor ein → Liquid rendert das Fix-Markup (§2.6) → **Verifikations-Re-Scan** zeigt den Score-Anstieg im App-Dashboard. Nicht-Shopify: ehrlicher Hinweis „Auto-Fix aktuell nur für Shopify" (kein Fallback-Produkt).

---

## 4. Datenbankschema (SQL im Dokument — keine Migration)

> `user_id uuid null` überall als Vorsorge (Abschnitt 7), FK zu `auth.users` später. Alle Tabellen bekommen RLS (§5).

```sql
-- ========== SCAN ==========
create table scans (
  id                uuid primary key default gen_random_uuid(),
  public_token      uuid not null unique default gen_random_uuid(),  -- teilbarer Lookup-Token
  user_id           uuid null,                    -- Vorsorge (nullable)
  input_url         text not null,
  normalized_domain text not null,
  cache_key         text not null,                -- = normalized_domain (Dedup/Cache)
  status            text not null default 'queued',
                     -- queued | running | done | failed | blocked_by_robots
  platform          text null,                    -- shopify | woocommerce | unknown
  score             int  null,                    -- 0..100
  score_breakdown   jsonb null,                   -- {category: {points, max, checks:[...]}}
  findings          jsonb null,                   -- [{id,category,severity,status,evidence,fix,weight}]
  report            jsonb null,                   -- gerendertes View-Model (deterministisch)
  narrative         jsonb null,                   -- Claude-Narrativ (knapp, gecacht, gedeckelt)
  narrative_at      timestamptz null,
  pages_crawled     int  null,
  claude_cost_cents int  not null default 0,
  ip_hash           text null,                    -- gehashte IP (Rate-Limit/Abuse), keine Roh-IP
  error             text null,
  requested_at      timestamptz not null default now(),
  started_at        timestamptz null,
  finished_at       timestamptz null
);
create index on scans (status);
create index on scans (cache_key, finished_at desc);

-- Betriebs-Heartbeat (Worker-Ausfall erkennen, §8)
create table worker_heartbeat (
  id           int primary key default 1,
  last_seen_at timestamptz not null default now(),
  worker_id    text null,
  constraint single_row check (id = 1)
);

-- ========== BEZAHLPRODUKT: SHOPIFY-APP (Auto-Fix, Abo) ==========
-- Ein Shop, der die App installiert. Access-Token verschlüsselt (nur Server/Worker).
create table shops (
  id                 uuid primary key default gen_random_uuid(),
  shop_domain        text not null unique,           -- foo.myshopify.com
  user_id            uuid null,                       -- Vorsorge
  created_at         timestamptz not null default now()
);

create table app_installs (
  id                 uuid primary key default gen_random_uuid(),
  shop_id            uuid not null references shops(id) on delete cascade,
  access_token_enc   text not null,                   -- verschlüsselt; nie Klartext/Bundle
  scopes             text not null,
  status             text not null default 'installed', -- installed | uninstalled
  embed_enabled      boolean not null default false,  -- App-Embed vom Merchant freigeschaltet?
  installed_at       timestamptz not null default now(),
  uninstalled_at     timestamptz null
);

create table subscriptions (
  id                 uuid primary key default gen_random_uuid(),
  shop_id            uuid not null references shops(id) on delete cascade,
  shopify_charge_id  text null,                       -- Shopify Billing recurring charge
  plan               text not null default 'basic',   -- basic | pro
  status             text not null default 'trial',   -- trial | active | canceled | frozen
  trial_ends_at      timestamptz null,
  current_period_end timestamptz null,
  created_at         timestamptz not null default now()
);

-- Erzeugte Fixes je Shop (Audit-Trail; das Markup selbst rendert der App-Embed live)
create table fixes (
  id                 uuid primary key default gen_random_uuid(),
  shop_id            uuid not null references shops(id) on delete cascade,
  kind               text not null,                   -- product_jsonld | robots | content_suggestion
  status             text not null default 'proposed', -- proposed | approved | applied
  needs_merchant     boolean not null default false,  -- z.B. fehlende GTIN
  payload            jsonb,
  created_at         timestamptz not null default now()
);

-- ========== SUPPORT: WISSENSBASIS ==========
create table kb_articles (
  id               uuid primary key default gen_random_uuid(),
  slug             text not null unique,
  title            text not null,
  body_md          text not null,
  category         text not null,   -- billing | scan-error | interpreting-results | account | other
  source           text not null default 'manual',  -- manual | escalation
  status           text not null default 'draft',   -- draft | published | archived
  last_reviewed_at timestamptz not null default now(),  -- Frischeverfolgung
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now()
);

-- Retrieval: v1 Postgres-Volltextsuche (tsvector). pgvector optional später.
create table kb_chunks (
  id         uuid primary key default gen_random_uuid(),
  article_id uuid not null references kb_articles(id) on delete cascade,
  chunk      text not null,
  tsv        tsvector generated always as (to_tsvector('english', chunk)) stored
);
create index on kb_chunks using gin (tsv);

-- ========== SUPPORT: TICKETS ==========
create table tickets (
  id               uuid primary key default gen_random_uuid(),
  public_token     uuid not null unique default gen_random_uuid(),
  user_id          uuid null,                 -- Vorsorge
  scan_id          uuid null references scans(id) on delete set null,
  requester_email  text null,                 -- personenbezogen, mit Einwilligung
  subject          text null,
  category         text null,                 -- klassifiziert
  sentiment        text null,                 -- neutral | negative | at_risk
  status           text not null default 'open',
                    -- open | awaiting_human | awaiting_customer | resolved | escalated
  autonomy_mode    text not null default 'draft_only',  -- draft_only | auto (pro Kategorie)
  cost_cents       int  not null default 0,
  reopened_72h     boolean not null default false,   -- Metrik Wiederkontakt
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now(),
  resolved_at      timestamptz null
);

create table ticket_messages (
  id                 uuid primary key default gen_random_uuid(),
  ticket_id          uuid not null references tickets(id) on delete cascade,
  role               text not null,   -- customer | agent_draft | agent_sent | human
  body               text not null,
  is_ai              boolean not null default false,
  approved_by_human  boolean not null default false,
  human_edited       boolean not null default false,   -- für Korrekturquote
  created_at         timestamptz not null default now()
);

-- Audit-Log aller Werkzeugaufrufe/Entscheidungen des Agenten
create table agent_actions (
  id         uuid primary key default gen_random_uuid(),
  ticket_id  uuid not null references tickets(id) on delete cascade,
  action     text not null,  -- kb_search | scan_lookup | account_status | trigger_rescan | escalate | draft_reply
  input      jsonb,
  output     jsonb,
  cost_cents int not null default 0,
  created_at timestamptz not null default now()
);

-- Eskalationen — erzwingen die KB-Rückkopplung (Brief 3.2)
create table escalations (
  id            uuid primary key default gen_random_uuid(),
  ticket_id     uuid not null references tickets(id) on delete cascade,
  reason        text not null,  -- money | complaint | no_evidence | harmful_recommendation | promise | other
  kb_article_id uuid null references kb_articles(id),  -- PFLICHT vor resolved (siehe Trigger)
  created_at    timestamptz not null default now(),
  resolved_at   timestamptz null
);
```

### 4.1 Erzwungene KB-Rückkopplung (harte Regel, nicht Prompt-Bitte)
Eine Eskalation darf nicht als `resolved` markiert werden, ohne dass ein KB-Artikel verknüpft ist. Als **DB-Trigger** (Pseudocode-Illustration):

```sql
-- Illustration: Trigger verhindert resolved_at ohne kb_article_id
create function enforce_kb_on_escalation() returns trigger as $$
begin
  if new.resolved_at is not null and new.kb_article_id is null then
    raise exception 'Escalation cannot be resolved without a linked kb_article_id';
  end if;
  return new;
end; $$ language plpgsql;
create trigger trg_enforce_kb before update on escalations
  for each row execute function enforce_kb_on_escalation();
```

---

## 5. Row Level Security (Tag 1)

**Prinzip:** Jede Tabelle `enable row level security`. In v1 (kein Nutzer-Login) gibt es **keine** Policies für `anon`/`authenticated` → default-deny → aus dem Browser ist nichts direkt lesbar/schreibbar. Sämtlicher Zugriff läuft über `service_role` (Next.js Server-Routen + Worker). `service_role` umgeht RLS bauartbedingt.

```sql
alter table scans            enable row level security;
alter table worker_heartbeat enable row level security;
alter table shops           enable row level security;
alter table app_installs    enable row level security;
alter table subscriptions   enable row level security;
alter table fixes           enable row level security;
alter table kb_articles     enable row level security;
alter table kb_chunks       enable row level security;
alter table tickets         enable row level security;
alter table ticket_messages enable row level security;
alter table agent_actions   enable row level security;
alter table escalations     enable row level security;
-- Keine anon/authenticated-Policies in v1 → default deny.
```

**Warum das reicht und sauber ist:** Der `service_role`-Key existiert nur im Worker und in Vercel-Server-Env — **nie im Frontend-Bundle, nie im Commit** (Leitplanke 8). Der Browser hat gar keinen DB-Zugang, also keine Angriffsfläche über den Client.

**Späterer Ausbau (nur Vorsorge, nicht bauen):** Bei Nutzerkonten Policies wie `using (auth.uid() = user_id)` je Tabelle ergänzen; die `user_id`-Spalten sind bereits da.

---

## 6. Umgebungsvariablen

`.env.example` listet **alle** Namen ohne Werte. Geheimnisse nie im Repo (Leitplanke 8).

**Frontend (Vercel, Server-Scope sofern nicht `NEXT_PUBLIC_`):**
```
SUPABASE_URL=
SUPABASE_SERVICE_ROLE_KEY=          # nur Server-Routen, nie Bundle
NEXT_PUBLIC_TURNSTILE_SITE_KEY=     # öffentlich (Widget)
TURNSTILE_SECRET_KEY=               # Server
# --- Shopify-App (Bezahlprodukt, Billing via Shopify) ---
SHOPIFY_API_KEY=
SHOPIFY_API_SECRET=
SHOPIFY_APP_URL=                    # öffentliche App-URL (OAuth-Callback)
SHOPIFY_SCOPES=read_products        # Embed braucht keinen Scope; nur Dashboard/Preview
SHOPIFY_WEBHOOK_SECRET=             # app/uninstalled + Pflicht-GDPR-Webhooks verifizieren
SHOPIFY_SESSION_DB_URL=             # Postgres/Supabase — NICHT die Prisma-SQLite-Default
APP_TOKEN_ENCRYPTION_KEY=           # verschlüsselt Access-Tokens in app_installs
BILLING_PLAN_PRICE_USD=29
BILLING_TRIAL_DAYS=7
APP_BASE_URL=                       # z.B. https://agentready.<domain>
CRAWLER_CONTACT_URL=                # ehrliche UA-Kontakt-URL (Leitplanke 3)
SCAN_RATE_LIMIT_PER_IP_PER_HOUR=
SCAN_CACHE_TTL_MINUTES=
```

**Scan-Worker (Linux-Server):**
```
SUPABASE_URL=
SUPABASE_SERVICE_ROLE_KEY=
ANTHROPIC_API_KEY=
ANTHROPIC_MODEL_CLASSIFY=           # günstig (Klassifikation/Routing)
ANTHROPIC_MODEL_ANSWER=             # stärker (Narrativ/Support-Antwort)
SCAN_USER_AGENT=                    # ehrlicher UA inkl. CRAWLER_CONTACT_URL
CRAWL_MAX_PAGES=15
CRAWL_DELAY_SECONDS=1
CRAWL_TIMEOUT_SECONDS=10
WORKER_CONCURRENCY=2
MONTHLY_CLAUDE_BUDGET_CENTS=        # harter Monatsdeckel
MAX_SCAN_NARRATIVE_COST_CENTS=      # pro Report
MAX_TICKET_COST_CENTS=              # pro Ticket
```
*(GPU wird nicht gebraucht — reiner HTTP-/Parsing-/API-Dienst.)*

---

## 7. Sicherheit & Leitplanken (technische Umsetzung)

- **SSRF (Leitplanke 7):** URL-Schema nur `http`/`https`; Host auflösen; **jede** aufgelöste IP gegen Blocklisten prüfen: private Bereiche (10/8, 172.16/12, 192.168/16), Loopback (127/8, ::1), Link-Local (169.254/16, `fe80::/10`), Cloud-Metadaten (`169.254.169.254`), `0.0.0.0`. Recheck **nach** DNS-Auflösung (nicht nur am Hostnamen), gegen DNS-Rebinding. Redirects: bei jedem Hop erneut prüfen.
- **robots.txt (Leitplanke 2):** vor dem Crawlen holen; verbietet sie unseren UA → abbrechen + `blocked_by_robots` melden. Nie umgehen.
- **Höflichkeit (Leitplanke 3):** ehrlicher UA mit Kontakt-URL, ≥1 s/Domain, ≤15 Seiten, 10 s Timeout.
- **Read-only (Leitplanke 4):** nur GET; keine Formulare/Zustandsänderungen.
- **Keine PII aus Shops (Leitplanke 5):** wir extrahieren Produkt-/Struktur­daten, keine Personendaten. **Der Gratis-Scan speichert gar keine Nutzer-E-Mail** (kein E-Mail-Gate, kein Fallback-Produkt) → nur gehashte IP fürs Rate-Limit. Personenbezogen im System sind nur: die Shop-Domain des installierenden Merchants und — falls jemand Support schreibt — dessen E-Mail im Ticket (mit Löschpfad).
- **Kein Security-Scanning (Leitplanke 6):** nur Datenqualität/Auffindbarkeit.
- **Missbrauch (Frage 6):** Turnstile + IP-Rate-Limit (gehashte IP) + Domain-Cache. SSRF ohnehin Pflicht.

---

## 8. Deployment & Betrieb (auf 6–8 h/Woche ausgelegt)

- **Frontend:** Vercel Hobby, Auto-Deploy aus dem Repo.
- **Worker:** `systemd`-Service mit `Restart=always`. Logs via `journald`. **Heartbeat**: Worker schreibt periodisch `worker_heartbeat` (kleine Tabelle/Zeile); ein einfacher Cron/Check meldet, wenn der Heartbeat altert.
- **Stuck-Job-Schutz:** Scans, die > N min in `running` hängen, werden auf `failed`/`queued` zurückgesetzt (Zeitstempel-Check).
- **Kostenüberwachung:** `claude_cost_cents` je Scan/Ticket summieren; bei Annäherung an `MONTHLY_CLAUDE_BUDGET_CENTS` E-Mail an Betreiber; bei Überschreitung: Narrativ/Autonomie aus → alles deterministisch bzw. an Mensch.
- **Retention (Supabase Free schonen):** Job, der Scans älter als N Tage löscht (enthält keine PII ausser gehashter IP). Achtung: Supabase-Free-Projekte **pausieren bei Inaktivität** → Worker-Poll hält es faktisch wach.
- **Kosten-Realität (<30 CHF/Monat):** Vercel Hobby, Supabase Free, Turnstile, Shopify Billing (0 % bis $1 Mio.) = 0. **EU-VPS für den Worker ~€4–5/Mo.** Einzige variable Kosten = Claude API, gedeckelt. **Supabase-Region EU** wählen.

---

## 9. Bewusst *nicht* verbaut (Vorsorge, Abschnitt 7)

- `user_id`-Spalten (nullable) vorhanden.
- `run_scan()` als aufrufbare Funktion (Monitoring-wiederverwendbar).
- Report-Rendering getrennt von Scan-Ausführung.
- Mehr Vorsorge bewusst **nicht** — keine verfrühte Verallgemeinerung.
