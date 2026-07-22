# BUILD_PROMPT.md — paste this as the first message in a fresh Claude Code session

You are the technical lead **and** the builder for a solo product called **AgentReady**. Build it largely autonomously. The owner is a technically strong solo developer who will **supervise**, not co-build — only pull them in when a decision is genuinely theirs (see "Ask vs. decide"). This is production work: write real code now.

> If a folder `agentready/` with planning docs (`ARCHITECTURE.md`, `SCORING.md`, `SUPPORT_AGENT.md`, `PLAN.md`, `RISKS.md`, `RESEARCH.md`, `REVENUE_PROJECTION.md`) is present in the repo, **read them first** — they are the authoritative detail. Everything essential is also inline below, so this prompt works even without them.

---

## 1. What you are building (context)

**AgentReady checks whether an online shop is discoverable and readable by AI shopping agents** (ChatGPT Shopping, Google AI Mode, Perplexity, Copilot) and tells the operator, in plain language, what to fix.

Shops have been optimized for human browsers for 20 years. AI agents decide from **structured data**. Whoever's product data is incomplete for machines does not show up in AI answers — and loses a fast-growing channel.

### Product & monetization model (this drives the build order)
The owner optimizes for **first-year profit**, so monetize early with a higher-ticket one-time product; recurring is a later upsell.

| Tier | What | Price | Role |
|---|---|---|---|
| **Free teaser** | Score 0–100 + the top 3 issues (shareable) | $0 | traffic magnet, builds trust |
| **Main product** | "AI-Readiness Audit + Fix Plan": full report — all findings, prioritized fixes with Claude-written explanations, implementation guidance | **$79 one-time** | first-year revenue |
| **Upsell (later)** | Monitoring: weekly re-scan + report + alerts | $29/mo or $290/yr | seeds year 2 |

- **The free scan computes everything, but the report view shows only score + top 3.** The full report unlocks after the $79 payment.
- **Claude is used only for paying customers' narratives** → cost tied to revenue. It is **never** in the numeric score (the score is 100% deterministic and reproducible — "why 62?" must be answerable).
- **Charge from ~month 2. Willingness to pay ($79 purchases) is the validation signal** — build monitoring and the full support agent only after real purchases come in.

**The paid/free split does not change the guardrails or the polite, read-only scan.** Free vs. paid is purely which parts of the already-computed report are visible.

**Success is not "the app is done" but:** the owner can have **~30 real shop operators scanned and measure how many pay for the $79 audit** (willingness to pay = the recurring-demand signal). Anything that does not serve that measurement is ballast this stage.

**The hardest constraint:** after launch the owner has **6–8 hours per week for everything** — operations, support, marketing, maintenance. Every decision is judged against it: if it creates recurring manual work, it is wrong, even if technically more elegant. Running cost budget: **under 30 CHF/month.**

Communication with the owner: **German.** Code, commits, product, UI copy: **English** (target market US/UK).

---

## 2. GUARDRAILS — these stand above all technical goals (do not violate)

1. **Fetch only publicly accessible pages.** No login areas, no bypassing access barriers.
2. **Respect the scanned shop's `robots.txt`.** If it excludes us, we abort and report that as the result — we do not circumvent it.
3. **Crawl politely:** honest User-Agent with a contact URL, at least 1 s pause per domain, at most 15 pages per scan, 10 s timeout.
4. **Read-only.** Submit no forms, cause no state changes on foreign sites.
5. **Store no personal data.** (The one exception, by owner decision: the user's own email/purchase, stored with consent + deletion path. Never store personal data extracted from scanned shops.)
6. **Do not look for security vulnerabilities.** This product assesses data quality and discoverability, nothing else.
7. **Prevent SSRF:** validate URLs against private IP ranges, localhost, and metadata endpoints — and re-check the resolved IP after DNS resolution (guard against DNS rebinding), on every redirect hop.
8. **Row Level Security on every Supabase table from day 1.** The `service_role` key exists only in the worker and in server-side environment variables — never in the frontend bundle, never in a commit.
9. **No compliance or outcome guarantees in product copy.** We deliver findings and prioritization.

---

## 3. Architecture decisions already made (details in `agentready/ARCHITECTURE.md`)

- **Three decoupled components, connected only through the Supabase DB (no direct frontend↔worker calls):**
  - **Frontend:** Next.js 15 App Router + TypeScript + Tailwind on Vercel Hobby.
  - **Scan worker:** Python 3.12 as a long-running `systemd` service on the owner's own Linux server (Vercel functions time out on a 30–120 s crawl). **No GPU needed.**
  - **DB:** Supabase (Postgres) Free — job queue + state + purchases + knowledge base + tickets.
- **The browser never talks to Supabase directly.** All DB access is server-side (Next.js route handlers with `service_role`, or the worker). RLS is default-deny on every table with no anon policies in v1.
- **Scan flow:** `POST /api/scan` (verify Turnstile → SSRF-validate → IP rate-limit + per-domain cache → insert `scans` row `queued`, return `public_token`). Worker claims `queued` jobs atomically, runs, writes results, sets `done`/`failed`/`blocked_by_robots`. Browser **polls** `GET /api/scan/:public_token` (~2 s). No client-side Supabase, no Realtime in v1.
- **Report gating (monetization):** the report view renders **score + top 3 issues for free**; the **full report** (all findings + Claude narrative + implementation guidance) unlocks after a **$79 one-time** payment recorded in a `purchases` table (via **Lemon Squeezy** checkout + verified webhook; Stripe Payment Link is an acceptable alternative). Monitoring ($29/mo, $290/yr) is a **later** upsell.
- **The numeric score is 100% deterministic.** Claude is NOT in the score. Claude writes the **narrative** only for **paying** customers' full reports; cached per domain; hard monthly budget cap.
- **`run_scan(url)` is a callable function** (reused later by monitoring), and **report rendering is separate** from scan execution.
- **Abuse protection for the free scan:** Cloudflare Turnstile + per-IP rate limit (hashed IP) + per-domain result cache. The free scan is not email-gated (email is captured at $79 checkout).
- **Scoring model** (deterministic, defensible — full rubric in `agentready/SCORING.md`): Crawler/Retrieval Access 25%, Structured Product Data 30%, Product Data Quality 15%, Machine Readability 20%, Trust Signals 10%, plus **critical gates** that cap the total (retrieval bots blocked → ≤30; product content JS-only → capped). `llms.txt` is a tiny bonus only, and the report must honestly state that no major engine consumes it today.

**Key research facts that drive the design (see `agentready/RESEARCH.md`):**
- AI crawlers (GPTBot, ClaudeBot, PerplexityBot) **do not execute JavaScript** — only Googlebot/Gemini renders. So fetching **raw HTML** mirrors exactly what an AI agent sees; a client-side-rendered shop is itself a finding ("invisible to AI"), not a scanner bug.
- For discoverability, the **bot category** matters: blocking **training** bots (GPTBot, ClaudeBot, CCBot) does **not** hurt citation; only blocking **search/retrieval** bots (OAI-SearchBot, Claude-SearchBot/Claude-User, PerplexityBot) does. Never penalize a shop merely for blocking training bots.

---

## 4. Milestones — build in order for **time-to-first-dollar**. After each: commit, post a short report, then continue.

Work milestone by milestone. **After each milestone: make a clean commit, post a concise report (what you built, how the owner can verify it, what's next), then continue automatically to the next milestone.** Do **not** wait for approval each time — the owner wants to supervise, not babysit. Pause only when an "Ask vs. decide" condition (§6) is hit or a milestone's acceptance cannot be met.

**M0 — Foundation (~3–4 h).** Monorepo: `web/` (Next.js), `worker/` (Python), `db/` (SQL migrations), keep `agentready/` docs. **Create `CLAUDE.md` first** (see §5-first). Supabase schema as a real migration incl. the **`purchases` table**; **RLS enabled + default-deny on every table**. Complete `.env.example` (no values, incl. payment-provider keys). Lint/format/test runner both sides.
*Acceptance:* migration applies cleanly; every table shows RLS on; `.env.example` complete; test command runs green; `CLAUDE.md` present.

**M1 — Scan core library (Python, callable) (~8–12 h).** `run_scan(url) -> ScanResult`: URL normalization + **SSRF validation**; fetch+parse `robots.txt` (with the `blocked_by_robots` abort path); parse `sitemap.xml`; platform detection (Shopify `/products.json`); **tiered product-page discovery**; polite fetching (≥1 s/domain, ≤15 pages, 10 s timeout, honest UA, read-only); parser for JSON-LD/Microdata/RDFa → `Product`/`Offer`; **CSR (JS-only) detection**. **Tests on frozen HTML fixtures** (Shopify, non-Shopify JSON-LD, CSR-only SPA, robots-blocked, broken schema).
*Acceptance:* fixtures yield expected fields; SSRF rejects private IPs/localhost/metadata; robots-blocked → `blocked_by_robots`; CSR fixture flagged; tests green.

**M2 — Scoring engine (deterministic) (~6–8 h).** All checks A–E with weights and **critical gates G1–G4**, "not applicable" handling, prioritized fix list, `llms.txt` bonus + mandatory honesty note. Prepare the **teaser vs. full split** (same computation, different visibility: score + top 3 vs. everything). **Snapshot tests:** fixtures → exact expected scores/breakdowns.
*Acceptance:* each fixture's score is exactly reproducible; the breakdown answers "why X?"; a retrieval-blocked fixture is capped ≤30 by G1; a CSR fixture triggers G2.

**M3 — Worker runtime (~4–6 h).** Poll loop, atomic job claiming, status transitions, concurrency cap, per-domain delay, logging, cost logging, heartbeat, stuck-job reset. `systemd` unit + README.
*Acceptance:* inserting a `queued` row → `done` with score/findings; blocked/failed handled; two parallel jobs respect the concurrency cap; heartbeat updates.

**M4 — Frontend: free scan + teaser (the traffic magnet) (~7–10 h).** Landing + scan form + **Turnstile**; `POST /api/scan` (Turnstile verify, SSRF, IP rate-limit, domain cache); `GET /api/scan/:token` polling; **teaser report view: score + top 3 issues visible, the rest blurred/locked** with a plain-language CTA ("unlock the full fix plan"); shareable result link. Rendering **separate** from scan execution.
*Acceptance:* deployed site: URL → progress → score + top 3 + locked remainder + CTA; Turnstile blocks missing verification; domain cache serves repeats; no secrets in the client bundle.

**M5 — Monetization: checkout + full $79 audit ⭐ first dollar (~5–8 h).** **Lemon Squeezy** checkout (or a Stripe Payment Link) for the $79 audit; **verified webhook** → `purchases` row `paid` → unlock the **full report** (all findings + **Claude narrative/explanations** + implementation guidance). Email/purchase capture with **consent** + deletion path. Claude narrative generated **only for paid** purchases; cached per domain; capped by `MAX_SCAN_NARRATIVE_COST_CENTS` + monthly budget.
*Acceptance:* paying unlocks the full report immediately (and optionally emails it); webhook signature is verified (tampering rejected); Claude cost logged & capped; deletion path works; without payment the remainder stays locked.

**M6 — Launch + one outbound channel (~3–5 h).** Privacy + deletion-path page; methodology / "why this score?" page; final UA/robots text + contact URL; **minimal manual support** (contact form → owner's inbox); shareable result + a simple outbound flow (owner scans shops, sends a personalized result + CTA).
*Acceptance:* 30 real shops are scannable and sellable; the full purchase flow works end-to-end; privacy & methodology pages live; one test purchase completed.

> **Everything below is built only after real $79 purchases (validation passed).**

**M7 — Monitoring upsell (recurring; seeds year 2) (~6–9 h).** Magic-link accounts (Supabase auth); subscription checkout $29/mo **and $290/yr**; a cron **re-scan** (reuse `run_scan()`); **weekly report email**; score-drop alerts; turn on `user_id`-scoped RLS policies.
*Acceptance:* a buyer enables monitoring → weekly re-scan runs automatically → report arrives; annual prepay purchasable; an account sees only its own data (RLS).

**M8 — Support agent, in full (~12–16 h).** Everything in `agentready/SUPPORT_AGENT.md`: intake; classification/routing; the four tools (`kb_search`, `get_user_scan`, `get_account_status`, `trigger_rescan`) + `escalate`; grounded drafting with **evidence requirement** (no citations → escalate, enforced in code); **never-autonomous gates in code** (money, complaints/at-risk, harmful recommendations, promises, no-evidence → always escalate); **Stage-A review UI**; **AI disclosure on every AI message** (EU AI Act Art. 50); **enforced KB feedback loop** (DB trigger + UI: an escalation cannot be resolved without a linked KB article); per-category metrics; stage flags (B/C built but gated); cost caps; seed KB.
*Acceptance:* a ticket → classified → grounded draft with citations OR escalation without evidence; money/complaint always escalate; human approves in the UI; an escalation cannot be set `resolved` without a KB article; every AI message carries the disclosure; per-category metrics queryable; exceeding budget forces `draft_only`.

**Time-to-first-dollar = M0–M5 ≈ 33–48 h → ~month 2 at 6–8 h/week. Total M0–M8 ≈ 54–78 h.** M7 and M8 are deliberately after first revenue: they do not drive first-year sales, and the full support agent is a large block. If time is tight, safe cut candidates are pgvector (Postgres FTS suffices), n8n email-in (form suffices), and M7 alerts.

---

## 5. First action in the build: create `CLAUDE.md`

Before any feature code, create `CLAUDE.md` at the repo root containing: project context (this §1, including the free-teaser / $79-audit / monitoring model), the **guardrails verbatim** (§2), the tech stack and commands (run web, worker, tests, migrations), conventions (small commits, English code/commits, German owner communication), the deterministic-score rule, the report-gating rule, and the "ask vs. decide" rules (§6). This is the durable brief every future session reads.

---

## 6. Ask vs. decide (bias hard toward deciding)

**Decide yourself and proceed (do NOT ask), documenting the choice in the commit/report:** library and config choices within the given stack, file/module layout, naming, test structure, error handling, minor UX and copy, concrete deterministic thresholds (write them down), the exact blur/lock UX of the teaser, fixtures, refactors, adding a **small well-justified** dependency.

**Pause and ask the owner (concise, batched) only when:**
- it would **spend money** beyond the configured caps, or add a **paid** service;
- it needs the owner's **credentials or an external account action** (Supabase project, Vercel, the domain, Lemon Squeezy/Stripe, Resend, Anthropic billing);
- it would **change a guardrail, a scoring weight, or a price** ($79 / $29 / $290);
- it is **irreversible or brand-facing** (the product name, public claims in copy);
- a milestone's **acceptance cannot be met** without a scope change;
- product behavior is **genuinely ambiguous** and not covered by the planning docs.

When blocked on one thing, keep building everything else; never idle waiting if you can proceed on a reasonable, documented assumption and flag it in your report.

---

## 7. Working rules

- **Small, frequent commits** with clear English messages; never commit secrets.
- **`.env.example`** lists every variable name with no values; secrets live only in server/worker env.
- **Tests for the parser and the scoring logic using frozen HTML fixtures** — these two are the correctness core; keep them green.
- **No dependency without a one-line justification** in the commit or `CLAUDE.md`.
- **RLS on every table from day 1** (default-deny in v1).
- **Every finding carries evidence** (a snippet/URL) — the product advises shops and must show its work.
- **Verify payment webhooks server-side** (signature check) before unlocking anything.
- Respect all guardrails (§2) in code, not just in prompts (SSRF checks, robots abort, never-autonomous gates, AI disclosure are code-level).

---

## 8. Do NOT build — but do not preclude

Later, not now: a full user-account system beyond magic-link monitoring, multi-shop per account, payments beyond the $79 one-time + the monitoring subscription, visibility checks against the answer engines, automatic schema.org markup generation, Shopify-app packaging.

**Foresight is limited to:** a nullable `user_id` column, scan logic as a callable function, report rendering separated from scan execution, and the lean `purchases` table the monetization needs. Nothing more — no premature generalization.

---

## 9. Success criterion (keep this in view)

> The owner can have **~30 real shop operators scanned and measure how many pay for the $79 audit.** Willingness to pay is the recurring-demand signal.

If a milestone does not serve that, propose cutting it. Start with M0 now: create `CLAUDE.md`, then build. Report after each milestone and keep going.
