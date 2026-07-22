# RESEARCH.md — AgentReady

> Rechercheergebnisse für die Planung des Validierungsprodukts.
> **Recherchedatum: 2026-07-22.** Alle Weblinks an diesem Tag abgerufen.
> Sprache: Deutsch (Kommunikation mit Auftraggeber). Technische Begriffe und Bezeichner in Englisch.
> Diese Datei enthält **keinen Produktivcode** — nur Recherche mit Quellen.

---

## 0. Kernaussagen zuerst (was für den Bau zählt)

1. **KI-Crawler führen fast nie JavaScript aus.** GPTBot, ClaudeBot und PerplexityBot laden HTML, führen aber kein JS aus. Nur Googlebot/Gemini rendert. → Ein Scanner, der **rohes HTML** abruft (kein Headless-Browser), sieht **genau das, was ein KI-Agent sieht**. Das ist gleichzeitig die billigste und die ehrlichste Variante. Ein rein clientseitig gerenderter Shop ist damit selbst ein Befund („für KI unsichtbar"), kein Scanner-Fehler. *(Abschnitt 2)*

2. **Für KI-Auffindbarkeit zählt nicht «irgendein KI-Bot blockiert», sondern welche Kategorie.** Training-Bots (GPTBot, ClaudeBot, CCBot) zu blockieren hat **keinen** Einfluss auf Zitierbarkeit. Nur das Blockieren von **Retrieval-/Search-Bots** (OAI-SearchBot, Claude-User/Claude-SearchBot, PerplexityBot, Perplexity-User) verhindert, dass der Shop in KI-Antworten auftaucht. → Das Scoring muss diese Kategorien unterscheiden, sonst bestraft es Shops zu Unrecht. *(Abschnitt 1)*

3. **`llms.txt` ist Marketing-Hype mit dünner Beleglage.** Kein grosser KI-Anbieter liest es nachweislich. → Höchstens ein kleiner Bonuspunkt, keine Scoring-Säule; im Report ehrlich einordnen. *(Abschnitt 3)*

4. **Schema.org `Product`/`Offer` ist die harte Währung.** Google verlangt für Merchant Listings minimal `name`, `image`, `offers` mit `price` + `priceCurrency`. KI-Agenten brauchen mehr (`availability`, `brand`, GTIN, `hasMerchantReturnPolicy`, `shippingDetails`, `aggregateRating`), um ein Produkt überhaupt sicher zu empfehlen. *(Abschnitt 4)*

5. **KI-Support: Deflection ≠ Resolution.** Deflection-Zahlen sind um 20–40 Punkte aufgeblasen. Realistische echte Auflösung: 30–50 % früh, 70–85 % nur bei tief integrierten, handelnden Agenten auf eng gefassten Fällen. Beschwerden/Kündigungen sind nachweislich die schwächste Kategorie → eskalieren. KB-Aktualität ist der dominante Hebel. *(Abschnitt 6)*

6. **EU-KI-VO Art. 50 gilt ab 2.8.2026, extraterritorial, auch für CH-Anbieter mit EU-Kunden.** Der Support-Agent muss sich zwingend als KI zu erkennen geben. Bussen bis 15 Mio. € / 3 % Weltumsatz. *(Abschnitt 7)*

---

## 1. KI-Crawler-User-Agents & robots.txt

### 1.1 Die vier Funktionskategorien (das ist der Schlüssel)

KI-Bots sind **nicht** gleichwertig. Sie zerfallen in vier Klassen, und für unser Produkt zählt fast nur Klasse B/C:

| Klasse | Zweck | Beispiele | Für KI-Auffindbarkeit relevant? |
|---|---|---|---|
| A — **Training** | Sammelt Inhalte fürs Modelltraining | `GPTBot`, `ClaudeBot`, `CCBot`, `meta-externalagent` | **Nein.** Blockieren beeinflusst Zitierbarkeit nicht. |
| B — **Search/Retrieval-Index** | Baut den Index, aus dem KI-Antworten zitieren | `OAI-SearchBot`, `Claude-SearchBot`, `PerplexityBot`, `Bingbot`, `Amazonbot` | **Ja, zentral.** Blockieren = kein Zitat. |
| C — **User-triggered Fetch** | Holt eine Seite live, wenn ein Nutzer fragt | `ChatGPT-User`, `Claude-User`, `Perplexity-User`, `meta-externalfetcher` | **Ja.** (Beachten robots.txt teils *nicht*, weil nutzerinitiiert.) |
| D — **Opt-out-Token** (kein Crawler) | Steuert nur Nutzung, crawlt selbst nicht | `Google-Extended`, `Applebot-Extended` | Indirekt (steuert Gemini-/Apple-Nutzung). |

**Konsequenz fürs Scoring:** Ein Shop, der `GPTBot` (Training) sperrt, aber `OAI-SearchBot` (Search) erlaubt, ist für ChatGPT-Shopping **auffindbar**. Ein naives „KI-Bot in robots.txt disallowed → Punktabzug" wäre falsch und leicht widerlegbar. Wir prüfen gezielt die **Search-/Retrieval-Bots (Klasse B/C)** auf `Disallow: /` bzw. Sperren auf Produkt-/Sitemap-Pfaden.

### 1.2 Bestätigte Tokens aus Primärquellen

**OpenAI** (developers.openai.com/api/docs/bots, abgerufen 2026-07-22):
- `GPTBot` — Modelltraining. UA-Beispiel: `GPTBot/1.4; +https://openai.com/gptbot`
- `OAI-SearchBot` — bringt Seiten in die ChatGPT-Suchergebnisse. **Das ist der zitierrelevante Bot.** UA: `OAI-SearchBot/1.4; +https://openai.com/searchbot`
- `ChatGPT-User` — nutzerinitiierter Abruf; „may not be subject to robots.txt rules".
- `OAI-AdsBot` — prüft Ad-Landingpages.
- Jede Direktive wirkt unabhängig; robots.txt-Änderungen brauchen ~24 h Propagation.

**Anthropic** (support.claude.com, Artikel 8896518, abgerufen 2026-07-22):
- `ClaudeBot` — Modelltraining.
- `Claude-User` — Abruf für Antworten auf Nutzerfragen.
- `Claude-SearchBot` — Indexierung für Suchqualität.
- Offizielle Bot-IP-Liste zur Verifikation: `claude.com/crawling/bots.json`.

**Google** (developers.google.com, abgerufen 2026-07-22):
- `Google-Extended` — **Opt-out-Token**, kein eigener Crawler; steuert, ob Inhalte für Gemini-Training/Grounding genutzt werden.
- `Googlebot` — der einzige, der JS rendert; Gemini nutzt dessen Web Rendering Service.

**Weitere (aus Sekundärquellen 2026, zu verifizieren beim Bau):** `PerplexityBot` (Index) + `Perplexity-User` (nutzerinitiiert); `Bingbot` (Bing/Copilot); `Amazonbot` (Amazon/Rufus/Alexa); `Applebot-Extended` (Apple Opt-out); `meta-externalagent` (Training) + `meta-externalfetcher` (User-Fetch).

> ⚠️ **Die Liste ändert sich laufend.** Der Bau darf die Token-Liste **nicht** hart verdrahten. Empfehlung: Liste als versioniertes Config-/Fixture-File (`ai_crawlers.json`), das leicht aktualisierbar und getestet ist. Verifikation echter Bots ggf. über die publizierten IP-Listen (OpenAI, Anthropic `bots.json`, Google), aber das ist für den Gratis-Scan **nicht** nötig — wir lesen nur die robots.txt des Shops, wir empfangen keine Bots.

### 1.3 Was wir konkret prüfen
- `robots.txt` erreichbar, wohlgeformt, kein globales `Disallow: /`.
- Search-/Retrieval-Bots (Klasse B/C) **nicht** gesperrt — weder global noch auf Produkt-/Sitemap-Pfaden.
- `sitemap.xml` vorhanden, in robots.txt referenziert, erreichbar, enthält Produkt-URLs.

---

## 2. JavaScript-Rendering: KI-Crawler rendern nicht (kritischer Befund)

**Beleg:** Vercel/MERJ werteten über 500 Mio. GPTBot-Abrufe aus — **null** JS-Ausführung. GPTBot lädt JS-Dateien in 11,5 % der Fälle, führt sie aber nie aus; ClaudeBot lädt JS in ~23,8 %, führt es nie aus. GPTBot, ClaudeBot und PerplexityBot „fetch the raw HTML, extract what they find, and move on". **Einzige Ausnahme: Googlebot** (Headless Chrome, Zwei-Phasen-Indexierung); Gemini nutzt dieselbe Infrastruktur. *(Quelle: mehrere 2026-Analysen, u. a. Vercel/MERJ-Studie, s. Quellenliste.)*

**Drei Konsequenzen für uns:**
1. **Architektur:** Der Scan-Worker holt **rohes HTML per HTTP** (Python `httpx`/`requests`). **Kein** Headless-Browser für den Basis-Scan → billiger, schneller, robuster, passt zum 6–8-h/Woche-Budget. Er sieht damit exakt, was GPTBot/ClaudeBot/PerplexityBot sehen.
2. **Scoring:** Wenn Produktinformation nur clientseitig gerendert wird (React/Vue/Angular SPA ohne SSR), ist sie für KI-Agenten **nicht vorhanden**. Das ist ein schwerwiegender, konkret benennbarer Befund — nicht ein Fehler unseres Scanners.
3. **CSR-Erkennung:** Wir erkennen „nur clientseitig gerendert", indem wir prüfen: Rohes HTML enthält kaum Text/keine Produkt-Signale, aber es gibt eine `sitemap.xml`/`/products.json` mit vielen Produkten, oder ein grosses `<script>`-Bundle + leerer `<div id="root">`. → Befund: „Deine Produktdaten erscheinen erst durch JavaScript. KI-Agenten führen kein JavaScript aus und sehen deine Produkte nicht." (Siehe offene Frage an Auftraggeber zum Umgang mit CSR.)

---

## 3. `llms.txt`: dünn belegt, mit Vorsicht behandeln

- **Adoption:** ~8,7 % der Top-1000-Websites (Rankability, Juni 2026); ~10 % von 300k Domains (SE-Ranking-Erhebung). Aber **39,6 % sind Plugin-Stubs** (automatisch generierter Leerlauf).
- **Werden sie gelesen?** Nach aktueller Beleglage **nein**: Kein grosser KI-Crawler committet sich öffentlich darauf, `llms.txt` zu konsumieren (Stand: Anthropic, OpenAI, Perplexity ohne solche Aussage). Google (Gary Illyes, Juli 2025) unterstützt es nicht und plant es nicht. Eine Auswertung von >500 Mio. KI-Bot-Besuchen fand über 90 Tage nur **408** direkte `llms.txt`-Zugriffe. Semrush fand keine Korrelation mit besserer KI-Sichtbarkeit; 8 von 9 Sites sahen keine Trafficänderung.
- **Wo es real hilft:** Entwickler-Tools (Cursor, Windsurf, Claude Code, Copilot, Cline, Aider) holen `/llms.txt` bzw. `/llms-full.txt` bei Doku-Sites — das ist ein anderer Anwendungsfall als Shopping.

**Empfehlung:** `llms.txt` **nicht** als Scoring-Säule. Höchstens 0–2 Bonuspunkte im Block „Maschinenlesbarkeit", und im Report ehrlich schreiben, dass die Engines es (noch) nicht verlässlich konsumieren. Alles andere wäre unseriös — gerade für ein Produkt, das Seriosität verkauft.

---

## 4. Schema.org `Product` / `Offer` (die harte Währung)

**Quelle: Google Merchant-Listing-Dokumentation (developers.google.com), abgerufen 2026-07-22.**

**Pflicht (Merchant-Listing-Berechtigung):**
- `name`
- `image` (mehrere, hohe Auflösung; Seitenverhältnisse 16:9, 4:3, 1:1)
- `offers` → **`Offer`** (nicht `AggregateOffer`), darin:
  - `price` bzw. `priceSpecification.price` (**> 0**)
  - `priceCurrency` (ISO 4217, z. B. `USD`)

**Empfohlen (für KI-Agenten praktisch entscheidend):**
- `availability` (`InStock` / `OutOfStock` / `PreOrder` …)
- `brand.name`
- `gtin` / `gtin8/12/13/14`, `mpn`, `sku`
- `review` (mit Reviewer + Rating) und/oder `aggregateRating` (mit `reviewCount`)
- `hasMerchantReturnPolicy` (verschachtelte Rückgabebedingungen)
- `shippingDetails` (Raten, Zielländer, Lieferzeiten)

**Wichtig:** Für ein **Product Snippet** braucht Google `Product` + `Offer` + `AggregateRating`. KI-Shopping-Agenten lesen strukturierte Daten aber **umfassender** als Googles Minimalanforderung — sie brauchen den vollen Feldsatz, um ein Produkt überhaupt sicher zu empfehlen. → Unser Scoring belohnt nicht nur „valides Schema vorhanden", sondern **Feld-Vollständigkeit und -Plausibilität** (Preis > 0, Währung gesetzt, `availability` real, Rückgabe/Versand vorhanden).

**Zu verifizieren beim Bau:** exakte aktuelle Required/Recommended-Matrix direkt aus Googles Merchant-Listing- und Product-Snippet-Seiten (ändert sich) sowie JSON-LD vs. Microdata vs. RDFa (wir müssen alle drei Einbettungsformen parsen; JSON-LD ist der Standard).

---

## 5. Shopify: öffentliche Endpunkte

- **`/products.json`** — öffentlich, ohne API-Key, bis 250 Produkte/Seite, mit Titel, Beschreibung, Preis, Varianten, Bildern, Verfügbarkeit. Von Shopify automatisch erzeugt, **nicht abschaltbar**. Auch `<produkt-url>.json` / `.js` funktioniert.
- **`sitemap.xml`** — Standard-Sitemap-Index mit `sitemap_products_*.xml`.
- **Zulässigkeit:** Öffentlich zugängliche, bewusst exponierte Daten. Wir müssen trotzdem **robots.txt und die Shop-ToS respektieren** und höflich crawlen (Leitplanken Abschnitt 5). Cloudflare drosselt aggressives Crawlen — unsere Limits (≤15 Seiten, ≥1 s Pause, 10 s Timeout) liegen weit darunter.
- **Rechtliches:** Sekundärquellen verweisen auf *hiQ v. LinkedIn* (öffentliche Daten ≠ CFAA-Verstoss). **Vorsicht:** Das ist US-Recht, betrifft nur CFAA (nicht Urheberrecht/ToS/CH-/EU-Recht) und ist kein Freibrief. Unsere Position ist sauber, **weil** wir nur wenige öffentliche Seiten höflich und robots-konform lesen — nicht wegen eines Gerichtsurteils.

**Nutzen für uns:** `/products.json` ist ein billiger, zuverlässiger Weg, echte Produktseiten zu **entdecken** (statt Produktseiten heuristisch zu raten) und Schema-Angaben gegen die Rohdaten zu **plausibilisieren**. Für Nicht-Shopify-Shops brauchen wir den Sitemap-/Heuristik-Pfad (offene Frage: Produktseiten-Erkennung).

---

## 6. KI-Support: Benchmarks & Architektur

### 6.1 Deflection ≠ Resolution (der wichtigste Unterschied)
- **Deflection** = Konversation, die kein Mensch angefasst hat (inkl. Kunden, die aufgegeben haben). **Resolution** = Problem tatsächlich gelöst. Deflection-Zählung bläht die berichtete Leistung um **20–40 Punkte** auf. → Wir messen und kommunizieren **Resolution**, nicht Deflection.

### 6.2 Benchmarks 2026 (Sekundärquellen, s. Quellenliste)
- SaaS-Deflection-Median 2026: **40–45 %**, Top-Quartil 55–60 % (2025-Median war ~31,6 %).
- Echte **Resolution**: **30–50 %** frühe Deployments, **50–70 %** reifend, **70–85 %** nur bei tief integrierten, **handelnden** Agenten auf eng gefassten Fällen.
- **Intent-abhängig:** strukturierte Intents mit klarem Backend (Auth, Bestellung, Refund-Lookup) 65–80 %; **sentiment-/streitlastige Intents 19–34 %**. → Bestätigt Leitplanke: **verärgerte Kunden / Kündigungen sind die schwächste Kategorie → sofort eskalieren.**
- **Kosten:** ~5 $ pro KI-Resolution vs. ~30 $ menschlich (~6× günstiger). → Rechtfertigt Kostendeckel pro Ticket und Modell-Split (günstig für Klassifikation, stark für Antwort).

### 6.3 Einordnung der Auftraggeber-Annahmen
- **„50–80 % für reife, handelnde Agenten auf gut abgegrenzten Fällen"** → **bestätigt**, Oberkante eher 70–85 %.
- **„Median erstes Jahr 10–15 %"** → **pessimistischer** als 2026-Vendor-Medianzahlen, aber (a) Vendor-Zahlen sind selbstberichtet und deflection-aufgebläht, (b) ein brandneues Solo-Produkt mit dünner KB startet realistisch niedrig. → **Als konservativer Planungsboden brauchbar.** Nicht überversprechen.
- **„KB-Aktualität ist der grösste Hebel"** → **bestätigt** und in mehreren Quellen als dominanter Faktor genannt. Rechtfertigt die harte Regel: **jede Eskalation erzeugt einen KB-Eintrag** (Rückkopplung).

### 6.4 Architekturmuster für geerdete Support-Agenten (2026-Konsens)
- **Handelnde Agenten** (Tools, nicht nur Textsuche) erreichen die hohen Raten. Nötig: kleiner, klar begrenzter Tool-Satz (KB-Suche, Scan-Lookup des Nutzers, Konto-/Abo-Status, Re-Scan auslösen).
- **Grounding-Regel:** Steht die Antwort nicht in KB oder Nutzerdaten → **nicht raten, sondern eskalieren.** Als harte Code-Regel, nicht als Prompt-Bitte (Belegpflicht).
- **Stärkere Review-/Eskalationsregeln** für Geld, Beschwerden, schädliche Empfehlungen, Zusagen (Leitplanken 3.4).
- RAG-Praxis 2026: hybride Retrieval, kontextuelles Chunking; für ein Solo-Produkt mit kleiner KB genügt zunächst **einfaches Retrieval über eine gepflegte, kleine Wissensbasis** — kein GraphRAG-Overkill.

---

## 7. EU-KI-VO Artikel 50 (Kennzeichnungspflicht)

**Quellen: EU-Kommission (digital-strategy.ec.europa.eu), artificialintelligenceact.eu, Presse 2026-07; abgerufen 2026-07-22.**

- **Gilt ab 2. August 2026.** Finale Kommissions-Leitlinien am 20.07.2026 veröffentlicht.
- **Chatbot-Pflicht (Art. 50 Abs. 1):** Anbieter von KI-Systemen zur direkten Interaktion mit Personen müssen sicherstellen, dass Nutzer **informiert sind, dass sie mit einer KI kommunizieren** — ausser es ist offensichtlich.
- **Extraterritorial:** Gilt für Anbieter/Deployer in Drittstaaten (**inkl. Schweiz**), wenn der Output in der EU genutzt wird. Betrifft uns direkt bei EU-Kunden.
- **Bussen:** bis **15 Mio. € oder 3 % des weltweiten Jahresumsatzes** (höherer Wert).
- **Synthetische-Inhalte-Markierung:** Übergangsfrist bis 2.12.2026 nur für vor dem 2.8.2026 in Verkehr gebrachte Systeme — für uns nicht relevant, wir starten danach.
- (Randnotiz: Art. 4 „AI Literacy" gilt bereits seit Feb. 2025; für uns nachrangig.)

**Konsequenz:** Der Support-Agent gibt sich **ab der ersten Nachricht** klar als KI zu erkennen (UI-Hinweis + Selbst-Identifikation im Text). Zusätzlich: keine Ergebnis-/Compliance-Garantien im Produkttext (Leitplanke 5.9). Ein Produkt, das Shops zu KI-Themen berät und selbst die Transparenzpflicht verletzt, ist nicht zu verteidigen.

---

## 8. Legitimer Zugriff auf Antwort-Engines (nur für die *spätere* Sichtbarkeitsprüfung)

> Wird jetzt **nicht** gebaut, ist aber für die Architektur relevant, damit wir es nicht verbauen.

- **Consumer-Oberflächen abgreifen ist keine Option**, aus drei Gründen:
  1. **Gegen die ToS.** Perplexitys ToS verbietet ausdrücklich „any robot, spider, crawler, scraper … that … scrapes, extracts, or otherwise accesses the Services". OpenAI/Google haben analoge Klauseln. Ein Produkt, das anderen Compliance predigt, darf hier nicht selbst verstossen.
  2. **Technisch fragil.** Anti-Bot, CAPTCHAs, Login-Wände, Layout-Änderungen → ständige Wartung. Tödlich beim 6–8-h/Woche-Budget.
  3. **Nicht reproduzierbar.** Personalisierung/Ranking machen Ergebnisse nicht belegbar.
- **Offizielle APIs:** OpenAI API, **Perplexity „Sonar" API** (offizielles, vertraglich sauberes Such-API). Google bietet **kein** offizielles Query-API für „AI Mode/AI Overviews" → dieser Kanal ist legitim schwer prüfbar.
- **Ehrliche Einschränkung:** API-Ergebnisse ≠ das, was die Consumer-App zeigt (anderer Index/Ranking/Personalisierung). Eine spätere „Sichtbarkeitsprüfung" über APIs ist eine **Näherung**, kein Ground Truth. Das muss im späteren Produkttext ehrlich stehen.

---

## 9. Offene Belegunsicherheiten (ehrlich benannt)

- **Exakte, aktuelle Token-Liste** aller Klasse-B/C-Bots: Sekundärquellen sind konsistent, aber die Liste driftet. Beim Bau gegen Primärquellen final abgleichen und als Fixture pflegen.
- **Genaue Google-Required/Recommended-Matrix** (Merchant Listing vs. Product Snippet) ändert sich; beim Bau frisch von Google ziehen.
- **KI-Support-Benchmarks** stammen überwiegend von Anbietern/Beratungen mit Eigeninteresse — als Grössenordnung, nicht als Gesetz behandeln.
- **`llms.txt`-Zukunft** offen; falls ein grosser Anbieter es doch offiziell konsumiert, Gewicht nachziehen.

---

## 10. Quellen (abgerufen 2026-07-22)

**KI-Crawler / robots.txt (Primär):**
- OpenAI — Bots-Doku: https://developers.openai.com/api/docs/bots
- Anthropic/Claude — Crawler-FAQ (Artikel 8896518): https://support.claude.com/en/articles/8896518-does-anthropic-crawl-data-from-the-web-and-how-can-site-owners-block-the-crawler
- Anthropic Bot-IP-Liste: https://claude.com/crawling/bots.json
- Google — Product Structured Data / Merchant Listing: https://developers.google.com/search/docs/appearance/structured-data/merchant-listing

**KI-Crawler (Sekundär, Übersichten 2026):**
- Presenc AI — AI Crawler User Agents Reference: https://presenc.ai/research/ai-crawler-user-agents-complete-list
- ZeroKit — Complete List of AI Crawlers 2026: https://zerokit.dev/guides/ai-crawlers-list.html
- Known Agents (ehem. Dark Visitors): https://knownagents.com/agents

**JavaScript-Rendering:**
- SearchOptimo — Do AI Crawlers Render JavaScript? (GPTBot/ClaudeBot/Perplexity 2026): https://searchoptimo.com/blog/do-ai-crawlers-render-javascript
- Lantern — AI Crawlers Do Not Render JavaScript: https://www.asklantern.com/blogs/ai-crawlers-do-not-render-javascript
- Passionfruit — JavaScript Rendering and AI Crawlers: https://www.getpassionfruit.com/blog/javascript-rendering-and-ai-crawlers-can-llms-read-your-spa

**llms.txt:**
- Rankability — LLMS.txt Adoption (Juni 2026): https://www.rankability.com/data/llms-txt-adoption/
- aeo.press — The State of llms.txt in 2026: https://ai.aeo.press/the-state-of-llms-txt-in-2026
- OrganiKPI — llms.txt Adoption & Impact: https://organikpi.com/blog/distribution/llms-txt-adoption-impact/

**Schema.org / strukturierte Produktdaten:**
- Google Merchant Listing (s. o.)
- Shopify — Ecommerce Schema Guide 2026: https://www.shopify.com/blog/ecommerce-schema
- Xenara — Product Schema & Rich Results 2026: https://www.xenara.ai/blog/product-schema-ecommerce-rich-results-2026

**Shopify öffentliche Endpunkte:**
- Apify — Shopify Store Scraper (products.json Doku): https://apify.com/scrapeflow/shopify-store-scraper
- DataJournal/Medium — Scrape Shopify with Python (products.json): https://medium.com/@datajournal/how-to-scrape-shopify-stores-with-python-3463f570be51

**Antwort-Engine-Zugriff / ToS:**
- Perplexity API Terms of Service: https://www.perplexity.ai/hub/legal/perplexity-api-terms-of-service
- Perplexity Terms of Service: https://www.perplexity.ai/hub/legal/terms-of-service

**KI-Support-Benchmarks:**
- Lorikeet — Resolution Rate Benchmarks 2026: https://www.lorikeetcx.ai/articles/resolution-rate-ai-customer-support-benchmarks-2026
- Digital Applied — AI Support Deflection vs. Resolution (2026 Playbook): https://www.digitalapplied.com/blog/ai-support-deflection-resolution-layer-2026-playbook
- aithority — 2026 Benchmark across Six Industries: https://aithority.com/machine-learning/new-2026-benchmark-maps-ai-customer-service-performance-across-six-industries-resolution-csat-and-cost-per-resolution/

**EU-KI-VO Art. 50:**
- EU-Kommission — Transparency Obligations Art. 50 (FAQ): https://digital-strategy.ec.europa.eu/en/faqs/transparency-obligations-under-article-50-ai-act
- artificialintelligenceact.eu — Article 50 Guide: https://artificialintelligenceact.eu/transparency-rules-article-50/
- Bratby Law — AI Act Transparency Obligations 2 August 2026: https://bratby.law/ai-act-transparency-obligations-2026/

---

*Nächster Schritt gemäss Brief: Rückfragen an den Auftraggeber (Schritt 2), dann warten. Erst nach den Antworten entstehen `PLAN.md`, `ARCHITECTURE.md`, `SCORING.md`, `SUPPORT_AGENT.md`, `RISKS.md` und `BUILD_PROMPT.md`.*
