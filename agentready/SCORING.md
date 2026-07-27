# SCORING.md — AgentReady

> Das Bewertungsmodell. Jede Prüfung mit Gewicht und Begründung, warum sie für **KI-Auffindbarkeit** zählt.
> Leitsatz: **Der Zahlen-Score ist zu 100 % deterministisch** (Frage 2) — reproduzierbar, erklärbar, verteidigbar. Claude schreibt nur das Narrativ, nicht die Zahl.
> Grundlage: `RESEARCH.md`.

---

## 0. Warum überhaupt deterministisch (Widerspruch zum Brief)

Der Brief schlug „Datenqualität 20 %, von Claude bewertet" vor. Das habe ich verworfen, weil:
1. **„Warum 62?" muss beantwortbar sein.** Eine LLM im Zahlenpfad macht denselben Shop mal 60, mal 64 — der Score wäre nicht verteidigbar.
2. **Kosten.** Ein LLM-Aufruf pro Gratis-Scan sprengt bei offenem Zugang die 30 CHF.
3. **Vertrauen.** Ein Produkt, das Seriosität verkauft, braucht einen Score, den der Kunde nachrechnen kann.

Claude bewertet Datenqualität deshalb **nicht als Punktzahl**, sondern liefert im *vollständigen Report* (E-Mail-gated) qualitative Erläuterungen. Die Score-Kategorie „Datenqualität" wird über **deterministische Proxys** gemessen (§3).

---

## 1. Kategorien & Gewichte (überarbeitet ggü. Brief)

| # | Kategorie | Brief | **Neu** | Warum die Änderung |
|---|---|---|---|---|
| A | Crawler- & Retrieval-Zugang | 25 % | **25 %** | unverändert — Torwächter |
| B | Strukturierte Produktdaten (schema.org) | 30 % | **30 %** | unverändert — die harte Währung |
| C | Produkt-Datenqualität (deterministisch) | 20 % | **15 %** | −5, weil Maschinenlesbarkeit fundamentaler ist |
| D | Maschinenlesbarkeit (SSR/Roh-HTML, Sitemap, Semantik) | 15 % | **20 %** | +5: KI-Crawler rendern **kein JS** → Inhalt im Roh-HTML ist Grundvoraussetzung |
| E | Vertrauens- & Conversion-Signale | 10 % | **10 %** | unverändert |
| — | **llms.txt** | — | **Bonus 0–2 (ausserhalb der 100)** | Beleglage dünn (RESEARCH §3) — kein Kern-Gewicht |

**Begründung der Umverteilung:** Die stärkste Einzelerkenntnis der Recherche ist, dass KI-Crawler kein JavaScript ausführen. Ob Produktinhalt überhaupt im **rohen HTML** steht, ist damit fundamentaler als feinkörnige Datenqualität. Deshalb wandern 5 Punkte von C nach D. Das Modell bleibt bei fünf Kategorien (verständlich), wird aber ehrlicher.

---

## 2. Kritische Gates (überschreiben die Summe)

Manche Mängel sind **disqualifizierend** — ein Shop kann strukturell top sein und trotzdem für KI unsichtbar. Solche Gates **deckeln** den Gesamtscore unabhängig von den Kategorien. Das macht „warum nur 30?" ebenso verteidigbar wie „warum 62?".

| Gate | Bedingung | Wirkung | Beleg |
|---|---|---|---|
| G1 | `robots.txt` sperrt **Retrieval-/Search-Bots** (Klasse B/C: OAI-SearchBot, Claude-User/SearchBot, PerplexityBot, …) per `Disallow: /` oder auf Produkt-/Sitemap-Pfaden | Gesamt **≤ 30** | RESEARCH §1.1 |
| G2 | Produktinhalt **nur clientseitig** (Roh-HTML leer, JS-only) | Kategorien B/C/D bzgl. Produktdaten **≤ 25 % ihres Max**, Hinweis „für KI unsichtbar" | RESEARCH §2 |
| G3 | `robots.txt` sperrt **unseren** Agenten → Scan abgebrochen | Kein Score; Ergebnis `blocked_by_robots` + Erklärung | Leitplanke 2 |
| G4 | Domain nicht erreichbar / kein HTML / kein Shop erkennbar | Kein Score; ehrliche Fehlermeldung | — |

**Wichtige Nuance (RESEARCH §1.1):** Das Sperren von **Training**-Bots (GPTBot, ClaudeBot, CCBot, meta-externalagent) löst **kein** Gate und **keinen** Abzug aus — es beeinflusst Zitierbarkeit nicht. Ein naives „irgendein KI-Bot geblockt → schlecht" wäre fachlich falsch und leicht widerlegbar.

---

## 3. Die Prüfungen im Detail

Jede Prüfung: `id`, Kategorie, max. Punkte, Bewertung `pass|partial|fail`, **Evidence** (was wir fanden, mit Beispiel-URL/Snippet), **Fix** (konkret). Partial gibt anteilige Punkte. Summe der Kategorie-Punkte → auf Kategoriegewicht skaliert → Gesamt 0–100, dann Gates anwenden.

### A — Crawler- & Retrieval-Zugang (25 %)
| id | Prüfung | Punkte | Warum KI-relevant |
|---|---|---|---|
| A1 | `robots.txt` vorhanden & wohlgeformt | 3 | Grundlage jeder Bot-Steuerung |
| A2 | **Retrieval-Bots (Klasse B) nicht gesperrt** (OAI-SearchBot, Claude-SearchBot, PerplexityBot, Bingbot, Amazonbot) | 10 | Ohne sie kein Zitat in KI-Antworten (RESEARCH §1) |
| A3 | **User-Fetch-Bots (Klasse C) nicht gesperrt** (ChatGPT-User, Claude-User, Perplexity-User) | 6 | Live-Abruf bei Nutzerfragen |
| A4 | `sitemap.xml` vorhanden, in `robots.txt` referenziert, erreichbar | 3 | Auffindbarkeit der Produkt-URLs |
| A5 | Sitemap enthält Produkt-URLs (nicht leer/nur CMS-Seiten) | 3 | Produkte müssen im Index landen |

### B — Strukturierte Produktdaten / schema.org (30 %)
> Prüft **Vorhandensein + Validität + Feld-Vollständigkeit**, nicht nur „Schema da". Grundlage: Google Merchant-Listing (RESEARCH §4). Auf gesampelten Produktseiten gemittelt.
| id | Prüfung | Punkte | Warum |
|---|---|---|---|
| B1 | `Product`-Markup vorhanden (JSON-LD/Microdata/RDFa) | 6 | Ohne Markup keine maschinelle Produkterkennung |
| B2 | `offers` als **`Offer`** mit `price` (>0) + `priceCurrency` | 7 | Google-Pflicht; KI braucht Preis/Währung |
| B3 | `availability` gesetzt (InStock/OutOfStock/…) | 3 | KI empfiehlt nur kaufbare Produkte |
| B4 | Produkt-Identifikatoren: `gtin*`/`mpn`/`sku` | 4 | Eindeutige Zuordnung durch Agenten |
| B5 | `brand.name` gesetzt | 2 | Marken-/Filterlogik der Agenten |
| B6 | `hasMerchantReturnPolicy` vorhanden | 3 | Kaufentscheidungs-Signal (RESEARCH §4) |
| B7 | `shippingDetails` vorhanden | 3 | Kaufentscheidungs-Signal |
| B8 | `aggregateRating`/`review` vorhanden & valide | 2 | Vertrauens-/Ranking-Signal |

### C — Produkt-Datenqualität, deterministische Proxys (15 %)
> Keine LLM im Score. Proxys für „ist die Beschreibung für eine Maschine brauchbar".
| id | Prüfung | Punkte | Warum |
|---|---|---|---|
| C1 | Beschreibung vorhanden & ausreichend lang (≥ ~150 Zeichen) | 4 | Zu dünne Texte → KI kann nicht zuordnen |
| C2 | Kein Platzhalter-/Boilerplate-Text (kein „lorem", „description here", Duplikate) | 3 | Häufiger realer Mangel |
| C3 | ≥ 1 Produktbild mit `alt`-Text / `image` im Markup | 3 | Maschinelle Bild-/Kontexterfassung |
| C4 | Titel eindeutig & aussagekräftig (nicht nur SKU) | 2 | Erkennbarkeit |
| C5 | Preis plausibel (>0, Währung ISO-4217, konsistent Markup↔Seite) | 3 | Inkonsistenzen kosten Vertrauen der Agenten |

### D — Maschinenlesbarkeit (20 %)
| id | Prüfung | Punkte | Warum |
|---|---|---|---|
| D1 | **Produktinhalt im rohen HTML vorhanden (SSR), nicht JS-only** | 8 | KI-Crawler rendern kein JS (RESEARCH §2) — das Fundament |
| D2 | Valides, parsebares HTML; semantische Struktur (h1, main, klare Überschriften) | 3 | Robuste Extraktion |
| D3 | Saubere, sprechende Produkt-URLs; `canonical` gesetzt | 3 | Deduplizierung/Zuordnung |
| D4 | Sitemap maschinell sauber (gültiges XML, aktuelle `lastmod`) | 3 | Frische-Signal |
| D5 | Keine kritischen Inhalte hinter Interstitials/Consent-Walls, die Roh-HTML blocken | 3 | Sonst sieht der Agent nichts |

### E — Vertrauens- & Conversion-Signale (10 %)
| id | Prüfung | Punkte | Warum |
|---|---|---|---|
| E1 | Bewertungen/Ratings vorhanden (Seite + Markup) | 3 | KI gewichtet Sozialbeweis |
| E2 | Rückgabe-/Versand-Policy als erreichbare Seite (nicht nur Markup) | 3 | Kaufentscheidung |
| E3 | Kontakt-/Impressum-/Identitätsangaben | 2 | Legitimitäts-Signal |
| E4 | HTTPS gültig | 2 | Basissignal |

### Bonus — llms.txt (0–2, ausserhalb der 100)
- `/llms.txt` vorhanden & wohlgeformt: +1; verweist auf Produkt-/Sitemap-Ressourcen: +1.
- **Report-Text pflichtgemäss ehrlich:** „Kein grosser KI-Anbieter konsumiert llms.txt derzeit nachweislich; dies ist ein optionales Zukunfts-Signal, kein Ranking-Faktor." (RESEARCH §3)

---

## 4. Aggregation (Pseudocode, Illustration)

```python
def total_score(checks) -> int:
    cats = {"A":25, "B":30, "C":15, "D":20, "E":10}
    total = 0.0
    for cat, weight in cats.items():
        earned = sum(c.points for c in checks[cat] if c.status != "fail" and c.applies)
        maxpts = sum(c.max    for c in checks[cat] if c.applies)   # nicht anwendbare Checks fair rausrechnen
        total += (earned / maxpts) * weight if maxpts else 0
    score = round(total)                    # 0..100 deterministisch
    score = apply_critical_gates(score, checks)   # G1..G4 deckeln
    return score
```
- **Nicht anwendbare Checks** (z. B. `shippingDetails` bei digitalen Gütern) werden aus Zähler *und* Nenner entfernt → kein unfairer Abzug.
- **Reproduzierbarkeit:** gleiche Eingabe → gleicher Score. Getestet mit eingefrorenen HTML-Fixtures (Snapshot-Tests, siehe `PLAN.md` M2).

---

## 5. Priorisierte Fix-Liste (was der Nutzer zuerst sieht)

Priorität eines Fixes = **erreichbarer Punktgewinn × Kategoriegewicht**, plus Gate-Treffer immer zuoberst. So sieht der Betreiber die Massnahmen mit dem grössten Effekt auf seine KI-Auffindbarkeit zuerst — nicht eine alphabetische Mängelliste. Jeder Eintrag: Problem in Klartext, warum es KI-Auffindbarkeit kostet, konkreter Fix. (Priorisierung und Befunde sind **deterministisch**; Claude formuliert höchstens die Klartext-Erklärung aus.)

**Bezug zum Bezahlprodukt:** Befunde, die die Shopify-App **automatisch behebt** (fehlendes/unvollständiges `Product`/`Offer`-Markup — Kategorie B, das grösste Gewicht), werden im Ergebnis als *„automatisch behebbar"* markiert. Befunde, die der Merchant selbst liefern muss (echte GTIN) oder die architektonisch sind (CSR/kein SSR), werden ehrlich als **nicht auto-fixbar** ausgewiesen (Leitplanke 9 — keine Garantien).

---

## 6. Transparenz nach aussen

- Der öffentliche Report zeigt Kategorien, Punkte, Evidence und Fixes — der Nutzer kann den Score nachvollziehen.
- Die vollständige Prüf-/Gewichts-Matrix wird als Methodik-Seite veröffentlicht → „warum 62?" ist jederzeit beantwortbar.
- **Keine Garantien** („bringt X % mehr KI-Traffic") — nur Hinweise & Priorisierung (Leitplanke 9).
