# BUILD_PROMPT.md — paste this as the first message in a fresh Claude Code session

You are the technical lead **and** the builder for a solo product called **AgentReady**. Build it largely autonomously. The owner is a technically strong solo developer who will **supervise**, not co-build — only pull them in when a decision is genuinely theirs (see "Ask vs. decide"). This is production work: write real code now.

> If a folder `agentready/` with planning docs (`ARCHITECTURE.md`, `SCORING.md`, `SUPPORT_AGENT.md`, `PLAN.md`, `RISKS.md`, `RESEARCH.md`, `REVENUE_PROJECTION.md`, `PRICING.md`) is present in the repo, **read them first** — they are the authoritative detail. Everything essential is also inline below, so this prompt works even without them.

---

## 1. What you are building (context)

**AgentReady checks whether an online shop is discoverable and readable by AI shopping agents** (ChatGPT Shopping, Google AI Mode, Perplexity, Copilot) and, for Shopify shops, **fixes it automatically**.

Shops have been optimized for human browsers for 20 years. AI agents decide from **structured data**. Whoever's product data is incomplete for machines does not show up in AI answers — and loses a fast-growing channel.

### Product & monetization model (this drives the build order)

| Tier | What | Price | Platform |
|---|---|---|---|
| **Free scan** | Score 0–100 + findings (diagnosis, read-only) | $0 | **all shops** |
| **Auto-Fix app** | A Shopify app that **automatically injects and maintains** the shop's schema.org markup. The merchant installs it and toggles it on — no manual work. | **$29/mo, 7-day trial** via **Shopify Billing** | **Shopify only** |

- **The paid product FIXES the shop, done-for-you.** The mechanism is a **Theme App Extension (app embed block)** that renders correct schema.org `Product`/`Offer` JSON-LD **dynamically from the shop's live product data** on every product page. Not a one-time write — it's a live template, so it stays correct as products change, is reversible (toggle off), and survives theme updates. Merchant effort = install + enable.
- **The free scan is universal** (lead-gen, on the marketing site). **The auto-fix is Shopify-only** (that's where a write API exists). Non-Shopify shops get the scan + a downloadable fix plan (fallback).
- **Billing is Shopify Billing** — Shopify handles payment + tax, and the owner keeps ~100% up to $1M/yr (no Stripe/Lemon needed for the subscription).
- **The score is 100% deterministic** (reproducible — "why 62?" must be answerable). **The fix markup is deterministic too** (generated from real product data — see the hard rule below). Claude is used only for **language** (the scan's plain-text findings; optional description/alt-text drafts that the merchant approves) — never in the score, never to fabricate structured data.

**HARD RULE — never fabricate structured data.** Emit only markup that matches real, verified product data. Missing GTIN/brand/price → mark as "needs merchant input," do **not** invent it (Google penalizes mismatched markup, and it misleads the agents). A client-side-rendered shop cannot be fixed by a snippet — flag it, don't pretend.

**Success is:** the owner can have **~30 real shops scanned and measure how many install the app and convert from trial to paying.** Willingness to pay (paid installs) is the validation signal.

**The hardest constraint:** after launch the owner has **6–8 hours per week for everything** — operations, support, marketing, maintenance. If a decision creates recurring manual work, it is wrong, even if technically more elegant. Running cost budget: **under 30 CHF/month.** (Vercel Hobby, Supabase Free, a ~€5 EU VPS for the worker, Turnstile, Shopify Billing = 0% fee at this scale; only Claude API is variable and capped.)

Communication with the owner: **German.** Code, commits, product, UI copy: **English** (target market US/UK).

---

## 2. GUARDRAILS — these stand above all technical goals (do not violate)

1. **Fetch only publicly accessible pages** (during scanning). No login areas, no bypassing access barriers.
2. **Respect the scanned shop's `robots.txt`.** If it excludes us, we abort and report that as the result — we do not circumvent it.
3. **Crawl politely:** honest User-Agent with a contact URL, at least 1 s pause per domain, at most 15 pages per scan, 10 s timeout.
4. **Read-only when scanning foreign shops.** The auto-fix writes **only** into the shop of the merchant who installed the app, using their granted OAuth scopes — never into any shop we merely scanned.
5. **Store no personal data** beyond what the product needs: the user's own email (scan report, with consent + deletion path) and the installing merchant's shop domain + encrypted access token. Never store personal data extracted from scanned shops.
6. **Do not look for security vulnerabilities.** This product assesses data quality/discoverability and fixes structured data, nothing else.
7. **Prevent SSRF:** validate scan URLs against private IP ranges, localhost, and metadata endpoints — re-check the resolved IP after DNS resolution (guard against DNS rebinding), on every redirect hop.
8. **Row Level Security on every Supabase table from day 1.** The `service_role` key and merchant access tokens exist only in the worker and server-side env — never in a frontend bundle, never in a commit.
9. **No compliance or outcome guarantees in product copy.** We deliver findings and fixes, not promises of results.

---

## 3. Architecture decisions already made (details in `agentready/ARCHITECTURE.md`)

- **Four components:**
  - **Marketing + scan frontend:** Next.js 15 on Vercel Hobby — landing, free scan, results, "fix it automatically → install app" CTA.
  - **Scan worker:** Python 3.12 as a long-running `systemd` service on a **small EU cloud VPS** (~€5/mo). Runs the diagnosis scan **and** the verification re-scan. No GPU. (Own hardware only if the owner accepts outage risk during the pure validation phase — the queue means outages delay, never lose, since state lives in Supabase.)
  - **Shopify app (the paid product):** a Shopify-embedded app (Shopify CLI; the **Remix template** is the standard path — built-in OAuth, Billing, App Bridge), hosted on the VPS/Vercel. OAuth install (`read_products`), the **app-embed block** that renders the generated JSON-LD dynamically, Shopify Billing (trial → $29/mo), an app dashboard showing the before/after re-scan score.
  - **DB:** Supabase (Postgres) Free — scans, shops, app installs, subscriptions, fixes, knowledge base, tickets. Set the **Supabase region to EU**.
- **Fix engine (shared core):** `generate_fixes(shop_products)` produces valid `Product`/`Offer` JSON-LD from real product data (Shopify Admin/Storefront API or `/products.json`) via **deterministic templates**. Claude only for language parts, merchant-approved. Never fabricate (see §1 hard rule).
- **The browser never talks to Supabase directly.** All DB access is server-side (`service_role`) or via the worker/app backend. RLS is default-deny on every table.
- **Scan flow:** `POST /api/scan` (verify Turnstile → SSRF-validate → IP rate-limit + per-domain cache → enqueue). Worker claims the job, scans, writes score+findings. Browser polls `GET /api/scan/:token`. On a Shopify shop, the result CTA leads to the app install; the app embed applies the fix; a re-scan shows the score lift.
- **Abuse protection for the free scan:** Cloudflare Turnstile + per-IP rate limit (hashed IP) + per-domain result cache. The free scan is not email-gated.
- **Scoring model** (deterministic — full rubric in `agentready/SCORING.md`): Crawler/Retrieval Access 25%, Structured Product Data 30%, Product Data Quality 15%, Machine Readability 20%, Trust Signals 10%, plus **critical gates** that cap the total (retrieval bots blocked → ≤30; product content JS-only → capped). `llms.txt` is a tiny bonus only.

**Key research facts that drive the design (see `agentready/RESEARCH.md`):**
- AI crawlers (GPTBot, ClaudeBot, PerplexityBot) **do not execute JavaScript** — only Googlebot/Gemini renders. So raw-HTML scanning mirrors exactly what an AI agent sees; a client-side-rendered shop is itself a finding ("invisible to AI").
- For discoverability, the **bot category** matters: blocking **training** bots (GPTBot, ClaudeBot, CCBot) does **not** hurt citation; only blocking **search/retrieval** bots (OAI-SearchBot, Claude-SearchBot/Claude-User, PerplexityBot) does. Never penalize a shop merely for blocking training bots.
- schema.org `Product`/`Offer` is the highest-value, most auto-fixable lever — which is exactly what the app injects.

---

## 4. Milestones — build in order for time-to-first-dollar. After each: commit, post a short report, then continue.

Work milestone by milestone. **After each milestone: make a clean commit, post a concise report (what you built, how the owner can verify it, what's next), then continue automatically to the next milestone.** Do **not** wait for approval each time — the owner supervises, not babysits. Pause only when an "Ask vs. decide" condition (§6) is hit or a milestone's acceptance cannot be met.

**M0 — Foundation (~3–4 h).** Monorepo: `web/` (Next.js marketing+scan), `app/` (Shopify app), `worker/` (Python), `db/` (SQL), keep `agentready/` docs. **Create `CLAUDE.md` first** (§5-first). Supabase schema incl. `scans`, `shops`, `app_installs`, `subscriptions`, `fixes`, support tables; **RLS enabled + default-deny on every table**. Complete `.env.example` (no values; incl. Shopify app keys). Lint/format/test runner.
*Acceptance:* migration applies; every table shows RLS on; `.env.example` complete; test command green; `CLAUDE.md` present.

**M1 — Scan core (Python, callable) — the diagnosis (~8–12 h).** `run_scan(url) -> ScanResult`: URL normalization + **SSRF**; robots.txt (with `blocked_by_robots` abort); sitemap; platform detection (Shopify `/products.json`); tiered product-page discovery; polite fetching (≥1 s, ≤15 pages, 10 s timeout, honest UA, read-only); JSON-LD/Microdata/RDFa parsing → `Product`/`Offer`; **CSR detection**. Tests on frozen HTML fixtures.
*Acceptance:* fixtures yield expected fields; SSRF rejects private IPs/localhost/metadata; robots-blocked → `blocked_by_robots`; CSR flagged; tests green.

**M2 — Scoring engine (deterministic) (~6–8 h).** Checks A–E with weights + critical gates G1–G4, prioritized findings. Snapshot tests.
*Acceptance:* each fixture's score is exactly reproducible; breakdown answers "why X?"; retrieval-blocked → capped ≤30 (G1); CSR → G2.

**M3 — Worker runtime (~4–6 h).** Poll/claim queue, status transitions, concurrency cap, per-domain delay, logging, cost logging, heartbeat, stuck-job reset. `systemd` unit for the EU VPS. Verification re-scan as a callable path.
*Acceptance:* `queued` → `done` with score/findings; parallel jobs respect the cap; heartbeat updates.

**M4 — Frontend: free scan + "fix automatically" CTA (~7–10 h).** Landing + scan form + **Turnstile**; `POST /api/scan` (Turnstile, SSRF, rate-limit, cache); polling; result view (score + findings). **Platform-aware CTA:** Shopify detected → "Fix automatically — install the Shopify app"; otherwise → "Download fix plan (PDF)" fallback. Shareable result link.
*Acceptance:* deployed site: URL → progress → score + findings + correct CTA; Shopify shops see the app CTA; no secrets in the bundle.

**M5 — Fix engine: correct markup from real data (shared core) (~8–12 h).** `generate_fixes(shop_products) -> FixSet`: deterministic templates emit valid `Product`/`Offer` JSON-LD from real product data; **never fabricate** (missing GTIN/brand/price → flagged "needs merchant input"). Claude only for language drafts (merchant-approved). Tests: fixture products → expected JSON-LD.
*Acceptance:* fixture products → valid, complete JSON-LD (price>0, currency, availability, brand/gtin where present); no invented fields; tests green.

**M6 — Shopify app ⭐ paid product / first revenue (~16–24 h).** Shopify-embedded app (Remix template): **OAuth install** (`read_products`); **Theme App Extension / app-embed block** rendering the M5 JSON-LD **dynamically on every product page** from live data; **Shopify Billing** (7-day trial → $29/mo); app dashboard with the **before/after verification re-scan**; clean uninstall/toggle (embed removes itself). Store the access token **encrypted** (`app_installs`); never in a bundle or commit.
*Acceptance:* install on a Shopify dev store → app-embed renders valid Product JSON-LD from live data → re-scan shows a higher score → Shopify Billing charges after the trial → uninstall removes the embed cleanly; the app writes **only** to the installing shop.

**M7 — Launch + one channel (~5–8 h).** Privacy + deletion-path page; methodology / "why this score?" page; final UA/robots + contact URL; **minimal manual support** (contact form → owner's inbox); **app distribution** — either an App Store listing (plan for the review process) **or** an unlisted custom-app install link for the first customers (faster); one outbound channel (personalized scan results).
*Acceptance:* 30 real shops scannable; the app is installed and billing on at least one real shop; privacy & methodology pages live.

> **Everything below is built only after real paid installs (validation passed).**

**M8 — Support agent, in full (~12–16 h).** Everything in `agentready/SUPPORT_AGENT.md`: intake; classification/routing; the four tools + `escalate`; grounded drafting with **evidence requirement** (no citations → escalate, in code); **never-autonomous gates in code** (money, complaints/at-risk, harmful recommendations, promises, no-evidence → always escalate); **Stage-A review UI**; **AI disclosure on every AI message** (EU AI Act Art. 50); **enforced KB feedback loop** (escalation can't resolve without a linked KB article); per-category metrics; cost caps; seed KB.
*Acceptance:* ticket → classified → grounded draft with citations OR escalation; money/complaint always escalate; escalation can't be `resolved` without a KB article; every AI message carries the disclosure; exceeding budget forces `draft_only`.

**Later (do not build now, do not preclude):** content auto-fix (descriptions/alt-text with approval), a Pro plan, WooCommerce plugin, non-Shopify DIY export, multi-shop/agency plan.

**Time-to-first-dollar = M0–M6 ≈ 52–76 h → ~month 5 at 6–8 h/week.** The Shopify app (M6) is the biggest block and is unavoidable before revenue — that is the deliberate cost of done-for-you auto-fix. Safe cut candidates under time pressure: App Store listing → unlisted install link first; PDF fallback; Claude language drafts. **Not cuttable:** fix-engine correctness (M5) + app-embed verification (M6) — that's the core the customer pays for.

---

## 5. First action in the build: create `CLAUDE.md`

Before any feature code, create `CLAUDE.md` at the repo root with: project context (this §1, incl. the free-scan / auto-fix-app model and the never-fabricate rule), the **guardrails verbatim** (§2), tech stack + commands (run web, app, worker, tests, migrations), conventions (small commits, English code/commits, German owner communication), the deterministic-score rule, and the "ask vs. decide" rules (§6). This is the durable brief every future session reads.

---

## 6. Ask vs. decide (bias hard toward deciding)

**Decide yourself and proceed (do NOT ask), documenting the choice:** library/config choices within the given stack, file layout, naming, test structure, error handling, minor UX/copy, concrete deterministic thresholds (write them down), the app-embed markup shape, fixtures, refactors, a small well-justified dependency.

**Pause and ask the owner (concise, batched) only when:**
- it would **spend money** beyond the configured caps, or add a **paid** service;
- it needs the owner's **credentials or an external account action** (Supabase project, Vercel, the domain, the **Shopify Partner account / app registration**, Anthropic billing, the VPS);
- it would **change a guardrail, a scoring weight, or the price** ($29/mo / trial length);
- it is **irreversible or brand-facing** (product name, public claims in copy), or **writes to a real merchant's live shop** for the first time (get a go on the first real install);
- a milestone's **acceptance cannot be met** without a scope change;
- product behavior is **genuinely ambiguous** and not covered by the planning docs.

When blocked on one thing, keep building everything else; never idle if you can proceed on a reasonable, documented assumption and flag it.

---

## 7. Working rules

- **Small, frequent commits** with clear English messages; never commit secrets or merchant access tokens.
- **`.env.example`** lists every variable name with no values; secrets live only in server/worker env.
- **Tests for the parser, the scoring logic, and the fix engine using frozen fixtures** — these are the correctness core; keep them green.
- **No dependency without a one-line justification.**
- **RLS on every table from day 1** (default-deny in v1); encrypt merchant access tokens at rest.
- **Every finding carries evidence** (a snippet/URL) — the product advises and fixes shops and must show its work.
- **Never fabricate structured data** (§1 hard rule) — flag missing real data, don't invent it.
- Respect all guardrails (§2) in code, not just in prompts (SSRF, robots abort, scan-vs-fix write boundary, never-autonomous gates, AI disclosure are code-level).

---

## 8. Do NOT build — but do not preclude

Later, not now: content auto-fix, a Pro plan, WooCommerce, non-Shopify DIY export beyond the PDF fallback, multi-shop/agency plans, recurring visibility checks against the answer engines.

**Foresight is limited to:** a nullable `user_id` column, scan logic as a callable function, the fix engine as a callable function shared by app and worker, and the lean shops/installs/subscriptions/fixes tables. Nothing more.

---

## 9. Success criterion (keep this in view)

> The owner can have **~30 real shops scanned and measure how many install the Shopify app and convert from trial to paying.**

If a milestone does not serve that, propose cutting it. Start with M0 now: create `CLAUDE.md`, then build. Report after each milestone and keep going.
