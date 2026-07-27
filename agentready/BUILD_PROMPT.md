# BUILD_PROMPT.md — paste this as the first message in a fresh Claude Code session

You are the technical lead **and** the builder for **AgentReady**, a solo-founder product. Build it autonomously, milestone by milestone. The owner supervises — they are a strong developer (Python, Next.js, Supabase, APIs) but has **never built a Shopify app**, so guide them explicitly through every Shopify-side step (§8).

> **Planning docs:** if a folder `agentready/` exists in this repo, read `ARCHITECTURE.md`, `PLAN.md`, `SCORING.md`, `PRICING.md`, `SUPPORT_AGENT.md`, `RISKS.md`, `RESEARCH.md` first — they are the authoritative detail and this prompt is their summary. Everything critical is inline here too, so you can build without them.

---

## 1. What you are building

**AgentReady tells an online shop whether AI shopping agents** (ChatGPT Shopping, Google AI Mode, Perplexity, Copilot) **can find and read it — and for Shopify shops, fixes it automatically.**

Shops have been optimized for human browsers for 20 years. AI agents decide from **structured data**. Incomplete machine-readable product data → the shop doesn't appear in AI answers.

| Tier | What | Price | Platform |
|---|---|---|---|
| **Free scan** | Score 0–100 + findings, read-only, anonymous, no email required | $0 | **all shops** |
| **Auto-Fix app** ⭐ | Shopify app that **automatically renders and maintains** correct schema.org markup on every product page. Merchant installs it, toggles it on — done. | **$29/mo, 7-day trial** via Shopify Billing | **Shopify only** |

- The free scan is the lead magnet and is platform-independent. **Non-Shopify shops get a free DIY fallback: a fix plan by email** (optional, with consent + deletion path) containing a ready-to-paste JSON-LD snippet and instructions. It stays **free** because a second paid product would need its own payment processor and tax handling — recurring manual work for a minority segment. (Later it can become paid via Lemon Squeezy if demand shows up.)
- **The paid product must genuinely fix the shop with near-zero merchant effort.** That is the whole promise; don't dilute it into "here's a report."

**Success criterion (keep in view):** the owner can have **~30 real shops scanned and measure how many install the app and convert from trial to paying.** If a milestone doesn't serve that, propose cutting it.

**Hardest constraint:** after launch the owner has **6–8 h/week for everything** (ops, support, marketing). If something creates recurring manual work, it's wrong — even if technically elegant. **Running cost budget: under 30 CHF/month** (Vercel Hobby + Supabase Free + Turnstile + Shopify Billing = 0; a ~€5 EU VPS for the worker; Claude API capped).

**Language:** code, comments, commits, UI copy in **English** (target market US/UK). Talk to the owner in **German**.

---

## 2. GUARDRAILS — above all technical goals, never violate

1. **Fetch only publicly accessible pages** when scanning. No login areas, no bypassing access barriers.
2. **Respect the scanned shop's `robots.txt`.** If it excludes us, abort and report that as the result — never circumvent.
3. **Crawl politely:** honest User-Agent with a contact URL, ≥1 s pause per domain, ≤15 pages per scan, 10 s timeout.
4. **Read-only on foreign shops.** The auto-fix writes **only** into the shop of the merchant who installed the app, within their granted OAuth scopes — never into a shop we merely scanned.
5. **Store no personal data** beyond the minimum: the fallback fix-plan email (voluntary, with documented consent + deletion path), merchant shop domain + encrypted access token, support requester emails, and hashed IPs for rate limiting. **Every one of these needs a working deletion path.** Never store personal data extracted from scanned shops.
6. **Do not look for security vulnerabilities.** This product assesses data quality/discoverability and fixes structured data. Nothing else.
7. **Prevent SSRF:** validate scan URLs against private IP ranges, localhost and metadata endpoints — re-check the **resolved IP after DNS resolution** (DNS-rebinding), on **every redirect hop**.
8. **Row Level Security on every Supabase table from day 1** (default-deny). The `service_role` key and merchant access tokens live only in server/worker env — never in a frontend bundle, never in a commit.
9. **No compliance or outcome guarantees in product copy.** We deliver findings and fixes, not promises of rankings or results.

---

## 3. Hard technical rules (these cause silent failure or real harm if missed)

**3.1 — NEVER FABRICATE STRUCTURED DATA.**
Emit only markup backed by real product data. Missing GTIN / brand / price → **omit the field** and surface it in the dashboard as "needs merchant input." Never invent, never guess, never fill with placeholders. Wrong markup gets the merchant penalized by Google and misleads the agents — it is worse than no markup. A client-side-rendered shop cannot be fixed by a snippet; flag it honestly.

**3.2 — The fix is Liquid, not a server call.**
The Theme App Extension's **app-embed block renders the JSON-LD in Liquid directly from the `product` object**. Do **not** build a pipeline that generates markup server-side and syncs it into the shop. Reasons: always live (no sync, no webhooks), no Admin API rate limits, and — decisive for a solo operator — **the merchant's markup keeps working even if our VPS is down.** Server-side we only do: analysis (what's missing), the dashboard preview, and the verification re-scan.

**3.3 — Shopify Remix template session storage.**
The template ships with **Prisma + SQLite**, which **does not work on serverless (Vercel)** — no persistent filesystem. Switch session storage to **Postgres/Supabase** before anything else, or run the app as a normal Node process on the VPS. Getting this wrong produces sessions that vanish intermittently and is painful to debug later.

**3.4 — The score is 100% deterministic.**
Same input → byte-identical score, always. Claude is **never** in the scoring path. "Why 62?" must be answerable from the breakdown alone. Claude is used only for **language** (plain-text explanation of findings; later, merchant-approved description/alt-text drafts) — capped and cached.

**3.5 — Install ≠ active.**
A merchant can install the app and never enable the app-embed in the theme editor, in which case they pay for nothing and churn silently. Track `embed_enabled`, show it prominently in the dashboard, deep-link into the theme editor, and verify with a re-scan.

**3.6 — Own shop ≠ foreign shop.**
The ≤15-page / ≥1 s politeness limits (guardrail 3) apply to scanning **foreign** shops. Inside the **installed** shop, with the merchant's consent, read **all** products via the Admin API (paginated, respecting Shopify's API limits).

**3.7 — Verify against current Shopify docs, not memory.**
Shopify's CLI, app template, API version and Billing surface change often. Before implementing an M5 sub-step, check the current official docs/CLI output rather than relying on recalled patterns. Pin the API version explicitly.

**3.8 — Adaptive model routing (cost discipline).**
Every Claude call picks a model by task, starting cheap and escalating only on demand. Configure via env, never hardcode:

| Tier | Env var → model | Use for | Price /Mtok |
|---|---|---|---|
| Triage | `ANTHROPIC_MODEL_CLASSIFY` → `claude-haiku-4-5` | classification, routing, metric summarization, "is this even an incident?" | $1 / $5 |
| Standard | `ANTHROPIC_MODEL_ANSWER` → `claude-sonnet-5` | briefing script, analysis, proposals, support replies | $3 / $15 |
| Deep | `ANTHROPIC_MODEL_DEEP` → `claude-opus-4-8` | genuine root-cause analysis, code diffs | $5 / $25 |

**Escalation rule:** always start at Triage. Go to Standard only if Triage confirms there is something to say. Go to Deep only if (a) Standard explicitly reports uncertainty, **or** (b) the task is producing a code diff — and only within the monthly cap. Log model + cost on every call. When the cap is hit, fall back to deterministic output — never silently skip the work.

**3.9 — An agent may never change production on its own.**
The paid fix renders inside paying merchants' live shops, so a bad autonomous code change breaks other people's stores silently. Any agent that touches code (§M8): **draft PRs with tests only — never merge, never push to `main`, never deploy.** Enforce a **denylist** in code where not even a draft is allowed: the Liquid embed markup, billing, auth/RLS, scoring weights, guardrail code (SSRF/robots), migrations, prices. The GitHub token must not carry merge rights. There is deliberately **no** fully-autonomous tier.

---

## 4. Architecture (details in `agentready/ARCHITECTURE.md`)

- **`web/`** — Next.js 15 (App Router, TS, Tailwind) on **Vercel Hobby**: landing page, free scan, result view, app CTA.
- **`worker/`** — Python 3.12 `systemd` service on a **small EU VPS (~€5/mo)**: runs the scan and the verification re-scan. No GPU.
- **`app/`** — the **Shopify app** (Remix template): OAuth, Theme App Extension (the Liquid fix), Billing, dashboard.
- **`db/`** — Supabase (Postgres) Free, **EU region**: scans, shops, app installs, subscriptions, fixes, heartbeat, support tables.

**Decoupling:** the frontend and worker never talk directly — only through the `scans` table (Vercel functions time out on a 30–120 s crawl; the queue means an outage delays scans, never loses them). **The browser never talks to Supabase directly** — all DB access is server-side via `service_role`, so RLS is default-deny with no anon policies needed.

**Scan flow:** `POST /api/scan` (verify Turnstile → SSRF-validate → per-IP rate limit + per-domain cache → enqueue, return `public_token`) → worker claims and scans → browser polls `GET /api/scan/:public_token` (~2 s) → result view. Shopify detected → app-install CTA.

**Scoring model** (full rubric in `agentready/SCORING.md`): Crawler/Retrieval Access 25%, Structured Product Data 30%, Product Data Quality 15%, Machine Readability 20%, Trust Signals 10%, plus **critical gates** that cap the total (retrieval bots blocked → ≤30; product content JS-only → capped). `llms.txt` is a tiny bonus with an honest disclaimer.

**Research facts that drive the design** (`agentready/RESEARCH.md`):
- AI crawlers (GPTBot, ClaudeBot, PerplexityBot) **do not execute JavaScript** — only Googlebot/Gemini renders. So raw-HTML scanning mirrors exactly what an agent sees, and a client-side-rendered shop is itself a finding.
- **Bot category matters:** blocking *training* bots (GPTBot, ClaudeBot, CCBot) does **not** hurt citation; only blocking *search/retrieval* bots (OAI-SearchBot, Claude-SearchBot/Claude-User, PerplexityBot) does. **Never penalize a shop for blocking training bots** — that would be a factually wrong finding. Keep the bot list in a versioned fixture (`ai_crawlers.json`), not hardcoded.

---

## 5. Milestones — build in order, then report and continue

**After each milestone:** commit cleanly → post a short report (what you built · how the owner verifies it · what's next · anything you assumed) → **continue automatically to the next milestone.** Do not wait for approval each time. Pause only for an "ask" condition (§7), an owner action (§8), or if acceptance can't be met.

**Acceptance means demonstrated, not written.** Run the tests. Deploy. Scan a real shop. Install on a real dev store. If you can't verify it, say so explicitly instead of claiming done.

### M0 — Foundation (~2–3 h owner time)
Monorepo (`web/`, `app/`, `worker/`, `db/`, keep `agentready/`). **Create `CLAUDE.md` first** (§6). Supabase project in **EU region**; full schema; **RLS enabled + default-deny on every table**. Complete `.env.example` (names only). Lint/format/test runner both sides.
**Acceptance:** migration applies cleanly · every table shows RLS on · `.env.example` complete · test command runs green · `CLAUDE.md` present.

### M1 — Scan core (Python, callable)
`run_scan(url) -> ScanResult`: URL normalization + **SSRF**; `robots.txt` (with the `blocked_by_robots` abort path); sitemap; platform detection (Shopify via `/products.json`, CDN, headers); tiered product-page discovery; polite fetching (≥1 s, ≤15 pages, 10 s timeout, honest UA, GET only); JSON-LD **+ Microdata + RDFa** parsing → `Product`/`Offer`; **CSR detection**. Tests on **frozen HTML fixtures** (Shopify, non-Shopify JSON-LD, CSR-only SPA, robots-blocked, malformed schema).
**Acceptance:** fixtures yield the expected extracted fields · SSRF rejects private IPs/localhost/metadata **and** a redirect to a private IP · robots-blocked fixture → `blocked_by_robots` · CSR fixture flagged · tests green · **one real shop scanned end-to-end**.

### M2 — Scoring engine (deterministic)
All checks A–E with weights, **critical gates G1–G4**, "not applicable" handling (removed from numerator *and* denominator), prioritized findings with evidence. Mark each finding as **auto-fixable by the app** vs **needs merchant** vs **architectural**. **Snapshot tests.**
**Acceptance:** identical input → identical score, twice in a row · breakdown answers "why X?" · retrieval-blocked fixture capped ≤30 by G1 · CSR fixture triggers G2 · training-bot-blocked fixture is **not** penalized.

### M3 — Worker runtime
Poll loop, atomic job claiming (`FOR UPDATE SKIP LOCKED`), status transitions, concurrency cap, per-domain delay, structured logging, Claude cost logging, **heartbeat row**, stuck-job reset. `systemd` unit + a short deploy README for the EU VPS.
**Acceptance:** inserting a `queued` row → `done` with score/findings · two parallel jobs respect the cap · heartbeat updates · **killing and restarting the worker mid-scan loses no job**.

### M4 — Frontend: free scan + app CTA + non-Shopify fallback
Landing + scan form + **Turnstile**; `POST /api/scan` (Turnstile verify, SSRF, per-IP rate limit on hashed IP, per-domain cache); polling; result view (score, category breakdown, findings **with evidence**). **Platform-aware branch:** Shopify → "Fix it automatically — install the app"; **non-Shopify → "Get your fix plan by email"** with an explicit consent checkbox → `report_emails` → send (Resend or n8n webhook) a plan containing a **ready-to-paste JSON-LD snippet + instructions**, plus a working deletion path. Shareable result link.
**Acceptance:** on the deployed site a real URL produces progress → score → findings → correct branch · a non-Shopify shop → entering an email delivers a plan whose JSON-LD **validates** · the deletion path removes the email · Turnstile blocks a missing token · repeat scan of the same domain serves cache · **`grep` the built client bundle for secrets: none present**.

### M5 — Shopify app ⭐ the paid product / first revenue (biggest block)
Shopify app via CLI + **Remix template**, with **session storage on Postgres/Supabase** (§3.3) as the very first step. Then: **OAuth install** (`read_products` only — the embed itself needs no scope); **Theme App Extension with a Liquid app-embed block** rendering schema.org `Product`/`Offer` JSON-LD from the live `product` object (§3.1, §3.2); **Shopify Billing** (7-day trial → $29/mo via `appSubscriptionCreate`); **dashboard** with product preview (missing fields), **`embed_enabled` status + theme-editor deep link** (§3.5), and the **verification re-scan** (score before/after); mandatory webhooks (`app/uninstalled` **plus the three GDPR webhooks** — these are an App Store review blocker); clean uninstall.
**Acceptance, all demonstrated on a dev store:** install → enable embed → **a product page's HTML contains valid `Product` JSON-LD** (verified with Google's Rich Results Test) → re-scan shows a higher score → Billing trial starts and charges after it → uninstall removes everything cleanly → the app writes **only** to the installing shop → **no fabricated fields anywhere in the output**.

### M6 — Launch + one channel
Privacy page + deletion path, methodology / "why this score?" page, final UA + contact URL, **minimal manual support** (contact form → owner's inbox). **Distribution:** an **unlisted custom-app install link** for the first customers (fast, no review on the critical path), App Store listing submitted in parallel. One outbound channel: personalized scan results posted as help in communities.
**Acceptance:** 30 real shops scannable · **at least one real shop installed and billing** · privacy + methodology pages live.

### M7 — Operator Cockpit: dashboard + 1-minute voice briefing (internal)
A protected internal route **`/ops`** in `web/` (Basic Auth from env, `noindex`, **never public** — a 401 without credentials is part of acceptance). Live metrics read server-side via `service_role`, plus **day-over-day deltas** from `ops_daily_snapshots` (the worker writes one snapshot per day — it already runs, so no extra cron).

Show: scans (24 h / 7 d) · new + active installs · **installs whose app-embed is NOT enabled** (§3.5 — the silent churn killer) · trials started · paying shops + MRR · cancellations · Claude spend month-to-date vs. cap · worker heartbeat age · failed/stuck scans · open tickets.

**"Play daily briefing" button** — speaks the situation in **~60 seconds**:
- **Speech: the browser's Web Speech API (`speechSynthesis`)** — free, no API key, no infrastructure, works on mobile. Browsers require a user gesture to start speaking; the button *is* that gesture. Keep the TTS provider behind one small interface so a paid cloud voice can be swapped in later via env if the owner dislikes the built-in voice.
- **Script:** generated once per day by Claude (Standard tier, §3.8) from the **deterministic** metrics and cached in `ops_daily_snapshots.brief_script`. If Claude is unavailable or the budget cap is hit, fall back to a **deterministic template script** — the button must always work.
- **Script rules:** max ~150 words (≈60 s). **Anything needing action comes first** (embed off, budget near cap, worker silent, escalation open), then the numbers with their change, then a one-line outlook. Never read raw tables aloud.

**Acceptance:** `/ops` without credentials → 401 · with credentials → the numbers match a direct DB query · pressing the button plays audio that finishes in **45–75 s** · with a simulated problem (embed off / budget near cap / stale heartbeat) that problem is spoken **first** · with Claude disabled the deterministic script still plays.

### M8 — Operator Agent (the "business agent")
An agent that watches the business, diagnoses problems, proposes improvements, and drafts fixes. It runs in the worker (same machinery as the support agent), logs everything to `operator_actions`, and uses **adaptive model routing** (§3.8).

**Tools — small and explicit:** `get_metrics(range)` · `get_errors(range)` · `get_scan(domain|id)` · `search_logs(query)` · `read_repo(path)` / `search_code(query)` (**read-only**) · `open_issue(...)` · `open_draft_pr(branch, diff, tests)` (**never merges**) · `notify_owner(urgency, message)`.

**What it does:** writes the daily briefing script (M7) · diagnoses incidents ("7 scans on `*.example.com` failed with 429 — our per-domain delay is too aggressive there") · proposes improvements **with evidence** ("trial→paid is 12 %; 4 of 9 installs never enabled the embed → the onboarding step is missing") · opens **draft PRs with tests** for narrow, well-understood bugs.

**Hard limits — enforce in code, not in the prompt (§3.9):** never merge/push/deploy · **denylist** (Liquid embed, billing, auth/RLS, scoring weights, guardrail code, migrations, prices) where not even a draft may be produced · nothing customer-facing (no merchant emails, no billing actions) · no deletions or schema changes · **evidence requirement** — a finding without concrete metrics/logs/code references is not emitted · per-run and monthly cost caps.

**Staged autonomy:** **A** = proposals only, human decides (start here). **B** = for individual, measurably reliable categories it may open draft PRs without asking first — the merge always stays human. **C (fully autonomous) deliberately does not exist.**

**Acceptance:** a simulated incident (several failed scans) → the agent detects it, names a cause **with evidence**, and it appears in the cockpit · one real simple bug → **draft PR with a test, CI green, not merged** · an attempt to modify a denylisted file → **blocked in code and logged** · the run log shows model routing (cheap first, escalation only when justified) · exceeding the budget cap → briefing degrades to the deterministic script instead of failing.

> ### ⬇︎ Build M9 only after real paid installs exist. Do not start it early.

### M9 — Support agent (full)
Everything in `agentready/SUPPORT_AGENT.md`: intake, classification/routing, the four tools + `escalate`, **evidence requirement enforced in code** (no citations → escalate, never guess), **never-autonomous gates in code** (money, complaints/at-risk, harmful recommendations, promises, no-evidence → always escalate), **Stage-A review UI**, **AI disclosure on every AI-written message** (EU AI Act Art. 50, in force since 2026-08-02), **enforced KB feedback loop** (an escalation cannot be resolved without a linked KB article — DB trigger *and* UI), per-category metrics, cost caps, seeded KB.
**Acceptance:** ticket → classified → grounded draft **with citations** OR escalation when evidence is missing · money/complaint always escalate · escalation cannot be set `resolved` without a KB article · every AI message carries the disclosure · exceeding the budget forces `draft_only`.

**Owner time to first paying customer (M0–M6): ~26–45 h → ~6–10 weeks at 6–8 h/week.** M5 carries the most uncertainty because Shopify app development is new to the owner. M7 (~3–5 h) and M8 (~6–10 h) come right after launch because they are what keeps the owner's weekly hours low; M9 (~8–14 h) waits for real subscriptions.

**Cuttable under time pressure:** App Store listing (unlisted link first), the Claude narrative in scan results (deterministic findings suffice), M8's draft-PR capability (diagnosis + proposals alone are already most of the value). **Not cuttable:** correctness of the Liquid markup, the verification re-scan, and the M8 denylist/no-merge enforcement.

---

## 6. First action: create `CLAUDE.md`

Before any feature code, write `CLAUDE.md` at the repo root containing: the product model from §1 (free scan → Shopify auto-fix app), the **guardrails verbatim** (§2), the **hard technical rules verbatim** (§3), stack + commands (run web / app / worker / tests / migrations), conventions (small commits, English code, German owner communication), and the ask-vs-decide rules (§7). This is the durable brief every future session reads — treat it as the source of truth and keep it updated when a decision changes.

---

## 7. Ask vs. decide — bias hard toward deciding

**Decide yourself and proceed** (document the choice in the commit/report): libraries and config within the given stack, file/module layout, naming, test structure, error handling, minor UX and copy, concrete deterministic thresholds (write them down), the exact JSON-LD field mapping, fixtures, refactors, a small well-justified dependency.

**Pause and ask** (concise, batched) only when:
- it **spends money** beyond configured caps or adds a **paid** service;
- it needs an **owner action** — see §8 (accounts, credentials, external setup);
- it would **change a guardrail, a hard rule, a scoring weight, or the price** ($29 / 7-day trial);
- it is **irreversible or brand-facing** (product name, public claims), or it is the **first write into a real merchant's live shop**;
- a milestone's **acceptance cannot be met** without a scope change;
- behavior is **genuinely ambiguous** and not covered by the planning docs.

Blocked on one thing → keep building everything else. Never idle when you can proceed on a documented assumption and flag it.

---

## 8. Owner actions — the owner has never built a Shopify app

Some steps only the owner can do. When you reach one: **stop, give a numbered checklist in German, say exactly what to click and what value to paste where, then verify it worked before continuing** (e.g. read back the store domain, make a test API call, confirm the env var is set). Don't hand over a wall of steps at once — one checkpoint at a time.

Expected owner actions: Supabase project (EU region) · Vercel project · domain + crawler contact URL · email sending (Resend account or n8n webhook) for the fallback plan · **Shopify Partner account** · **development store** · **app registration in the Partner dashboard** (API key/secret, app URL, redirect URLs) · Shopify CLI login · enabling the app embed in the dev store's theme editor · Billing test flow · EU VPS + `systemd` deploy · Anthropic API key · **a GitHub token without merge rights** (M8).

Where a step has a known trap, warn before it happens — e.g. the session-storage default (§3.3), and that the app embed must be **toggled on in the theme editor** after install or nothing renders (§3.5).

---

## 9. Working rules

- **Small, frequent commits**, clear English messages. Never commit secrets or merchant access tokens.
- **`.env.example`** lists every variable name with no values.
- **Tests on frozen fixtures** for the parser and the scoring logic — the correctness core. Keep them green; run them before each report.
- **No dependency without a one-line justification.**
- **RLS on every table from day 1**; merchant access tokens **encrypted at rest**.
- **Every finding carries evidence** (snippet + URL). The product advises and modifies shops — it must show its work.
- Enforce the guardrails and hard rules **in code**, not in prompts: SSRF checks, robots abort, the scan-vs-fix write boundary, never-fabricate, never-autonomous gates, AI disclosure.
- Report honestly: if tests fail, show the output; if a step was skipped, say so; if something is verified, say it plainly.

---

## 10. Do NOT build (but do not preclude)

Content auto-fix (descriptions/alt-text via metafields with merchant approval), a Pro tier, WooCommerce plugin, a **paid** non-Shopify product (the fallback stays free), recurring visibility checks against the answer engines, multi-shop/agency plans, user accounts beyond the Shopify install, any fully-autonomous agent tier.

**Foresight is limited to:** a nullable `user_id` column, `run_scan()` as a callable function, report rendering separate from scan execution, a swappable TTS interface (M7), and the lean `shops` / `app_installs` / `subscriptions` / `fixes` / `operator_actions` tables. Nothing more — no premature generalization.

---

## 11. Start now

Create `CLAUDE.md` (§6), then build M0. Report after each milestone and keep going. If anything in this prompt conflicts with `agentready/*.md`, the planning docs win on detail — **but the guardrails (§2) and hard rules (§3) always win outright.**
