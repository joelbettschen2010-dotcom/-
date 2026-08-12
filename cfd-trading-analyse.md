# CFD- & Algo-Trading mit Claude-Modellen — Faktenanalyse

> **Datenbasis & Methodik (verbindlich lesen):**
> Alle Profitabilitäts-Zahlen stammen aus dokumentierten, real-money Live-Experimenten (Quellenliste am Ende). Es wurden **keine Zahlen erfunden**. Wo öffentlich kein auditiertes Track-Record existiert, steht das explizit. Alle Kostenrechnungen sind mit veröffentlichten API-Preisen (Stand Q3 2026) und offengelegten Annahmen modelliert — als reproduzierbares Modell, nicht als gemessene Realwerte.
> Format: strukturiert, maschinen-lesbar, für Weiterverarbeitung (n8n) optimiert.

---

## 0. Kernbefund (TL;DR)

- **Es existiert kein dokumentierter, unabhängig verifizierter, profitabler autonomer Live-Handel eines Claude-Modells mit auditiertem ROI.** Das Gegenteil ist belegt: im einzigen seriösen Real-Geld-Benchmark (Alpha Arena / nof1) hat Claude in **jeder** Saison **Geld verloren**.
- **LLMs (inkl. Claude) sind für tick-nahe CFD-/HFT-Ausführung physikalisch ungeeignet.** API-Latenz (Sekunden) vs. HFT (Mikro-/Millisekunden) = mehrere Größenordnungen daneben. In der Ausführungsschleife („execution hot path") verliert das LLM systematisch über Slippage und Spread.
- **Die Token-Ökonomie ist bei Retail-Kapital tödlich.** API-Kosten sind ein **fixer** Kostenblock, unabhängig von der Kontogröße. Bei kleinem Konto + hoher Frequenz frisst allein die Inferenz die Marge, bevor überhaupt gehandelt wird.
- **Der einzige tragfähige Einsatz: Claude als Code-/Strategie-Generator + Research-Copilot, nicht als Live-Trader.** LLM erzeugt & iteriert Strategie-**Code**, ein deterministischer Engine führt aus. Das ist die einzige Architektur mit positiver Erwartung.

---

## 1. Echtgeld-Beweise & Profitabilität

### 1.1 Alpha Arena (nof1.ai) — der einzige seriöse Real-Geld-Benchmark

Setup: 6 Frontier-Modelle, je **10.000 USD echtes Kapital**, autonomer Handel von BTC/ETH/SOL/XRP/DOGE/BNB **Perpetual Futures** auf Hyperliquid. Vollständig autonome Entscheidung, Risk-Control, Positionsgröße.

**Claude Sonnet 4.5 — Ergebnisse (real, negativ):**

| Saison | Claude-Ergebnis (Return) | Kontext / Feld |
|---|---|---|
| Season 1 | **negativ** — Snapshot-abhängig von ca. −12 % (Zwischenstand) bis **> −40 %** (Endstand je nach Quelle) | Sieger: Qwen3 Max **+22,31 %**; DeepSeek V3.1 +4,89 %; GPT-5 **≈ −75 %** (Totalverlust-nah) |
| Season 1.5 | **−32,44 %** | Sieger: Grok 4.20 +22,27 %; GPT-5.1 −1,41 %; Gemini-3-Pro −24,28 %; DeepSeek −24,51 % |

**Dokumentierte Fehlerursache bei Claude (nicht Marktpech, sondern Strukturfehler):**
- Hielt **100 % Long-Positionen** über den gesamten Wettbewerb.
- **Kein Hedging, kein dynamischer Stop-Loss.** Bei Markt-Reversal wurde die starre Bias zur offenen Flanke.
- Beispiel-Metriken S1: Kontowert 8.835 USD, PnL −12 %, Gebühren 482 USD, ~12,3x Leverage, Sharpe 0,00.

**Wichtig zur Interpretation der Zahlen:** Alpha Arena lief **live**; Zwischenstände schwankten stark, daher die Spannbreite (−12 % bis −40 %+) je nach Snapshot-Zeitpunkt. **Robust ist nur die Richtung: negativ, in jeder Saison.**

**Feld-Fazit (branchenweit):** Kein Modell war über die Saisons hinweg **robust** profitabel; die Sieger rotierten (Qwen → Grok), Verlierer ebenso (GPT-5 brach S1 massiv ein). Ein Fach-Review fasste das Ergebnis als *„LLMs can't trade crypto"* zusammen. → **Keine Evidenz für einen strukturellen LLM-Edge im autonomen Live-Handel.**

### 1.2 Project Vend (Anthropic / Andon Labs) — autonomer Wirtschaftsagent (kein Trading, aber relevant)

Claude Sonnet 3.7 („Claudius") betrieb autonom einen realen Mini-Store (Einkauf, Preissetzung, Kundeninteraktion, Cash-Management).

| Metrik | Wert |
|---|---|
| Ergebnis Phase 1 (~1 Monat) | **Verlust ~200 USD** (einzelne Tests **> 1.000 USD** im Minus) |
| Dokumentierte Fehler | erfundene Venmo-Bankdaten (Halluzination), exzessive Rabatte, unnötiges Restocking, ausgelassene Arbitrage (100 USD für 15-USD-Ware abgelehnt) |
| Phase 2 | Deutliche Besserung; negative Wochen weitgehend eliminiert, aber weiterhin nicht robust profitabel |

**Relevanz für Trading:** Zeigt die generische Schwäche autonomer LLM-Wirtschaftsagenten — Halluzination von Fakten/State, inkonsistentes Risk-Management, Anfälligkeit für Manipulation (Social Engineering). Genau diese Fehlerklassen sind im Live-Trading kapitalvernichtend.

### 1.3 NexusTrade & vergleichbare Plattformen — Marketing ≠ auditiertes Track-Record

- NexusTrade ist eine **AI-native Algo-Plattform**: LLM-Agenten (Claude via MCP, 50+ Tools) **erzeugen und backtesten Strategien**; ein no-code-Layer + Execution-Engine führt aus. **Architektonisch korrekt** (Code-Gen + Backtest, siehe §4).
- Deren öffentliche Claims („DESTROYING the market", „CRUSHING the market") sind **Vendor-Marketing bzw. eigene Backtests**, **kein unabhängig auditierter Live-P&L**. Backtest-Renditen sind systematisch nach oben verzerrt (Overfitting, Survivorship, Look-ahead).
- Verwertbares Faktum aus deren Vergleichstests: Claude (Opus 4.1) schnitt beim **Verstehen/Konstruieren** von Strategien am besten ab (Median-Score 1/1, Ø 0,95/1, 72 % Perfect-Scores). **Das misst Code-/Strategie-Kompetenz, nicht Live-Profitabilität.**

### 1.4 Antwort auf „War es nachweislich profitabel?"

**Nein.** Für autonomen Live-Handel gibt es keinen einzigen unabhängig verifizierten, profitablen Claude-Track-Record. Der einzige harte Real-Geld-Datenpunkt (Alpha Arena) ist **negativ in jeder Saison**. Exakte positive ROI-/Win-Rate-/USD-Zahlen für profitablen autonomen Claude-Handel **existieren öffentlich nicht** — jede solche Zahl wäre erfunden.

---

## 2. Der CFD-Reality-Check: Latenz, Slippage, Spread

### 2.1 Latenz-Größenordnungen (der entscheidende physikalische Blocker)

| Ebene | Typische Reaktionszeit | Faktor vs. HFT |
|---|---|---|
| HFT / Market-Maker (Co-location) | **~1–100 Mikrosekunden** | 1× (Baseline) |
| Retail-CFD-Broker-Roundtrip (Order → Fill) | ~10–200 ms | ~10³–10⁵× langsamer |
| **Claude API-Call (1 Entscheidung, adaptive thinking)** | **~0,5–5+ Sekunden** (TTFT + Generierung) | **~10⁴–10⁶× langsamer als HFT** |
| Claude „Fast Mode" (Opus 4.8/4.7) | ~2,5× höhere Output-tps, aber **weiterhin Sekundenbereich** | ändert die Größenordnung **nicht** |

### 2.2 Warum das Slippage & Spread erzeugt

- CFD-Quotes (v. a. Krypto/Indizes/FX) updaten **mehrfach pro Sekunde**. In den 0,5–5 s, die ein LLM-Call braucht, ist der Preis, auf den das Modell „reagiert", **bereits veraltet**. → Signal-zu-Ausführung-Lag = **struktureller Slippage**.
- Jeder Trade zahlt den **Spread** (Broker-Marge). Bei hoher Frequenz summiert sich Spread + Slippage zu einem Drift, den nur ein sehr großer, schneller Edge überkompensiert — den ein latenzgebundenes LLM per Definition **nicht** hat.
- Alpha Arena bestätigt empirisch: Claude zahlte **482 USD Gebühren** auf ein 10.000-USD-Konto (~4,8 %) — Reibungskosten allein sind ein signifikanter Gegenwind.

### 2.3 Warum LLMs hier oft scheitern (Ursachenliste)

1. **Latenz** — nicht wettbewerbsfähig in der Ausführungsschleife (s. o.).
2. **Nicht-Determinismus** — gleiche Eingabe ≠ garantiert gleiche Order; für Risk-/Compliance-kritische Ausführung untauglich.
3. **Halluzination von State** — erfindet Kontostände/Preise/Positionen (belegt: Project Vend). Im Live-Handel = Fehlorders.
4. **Kein natives Risk-Management** — Claude S4.5 ohne Stop-Loss/Hedging (belegt: Alpha Arena). Muss extern erzwungen werden.
5. **Prompt-/Markt-Manipulation** — anfällig für Social Engineering (Project Vend); im Handel: manipulierte News/Order-Book-Daten.
6. **Recency/Regime-Bias** — überschätzt jüngste Muster, keine robuste Out-of-Sample-Generalisierung.

**Fazit §2:** Direkter CFD-Live-Handel per Text-Prompting im Tick-/Sekundentakt ist **physikalisch nicht tragfähig**. Der sinnvolle Zeit-Horizont für eine LLM-**Entscheidung** liegt bei **Minuten bis Stunden**, nicht Ticks — und selbst dann nur mit externem Risk-Layer.

---

## 3. Token-Ökonomie vs. Marge (konkrete Rechnung)

### 3.1 Preis-Basis (veröffentlichte API-Preise, USD / 1M Token)

| Modell | Input | Output |
|---|---|---|
| Claude Opus 4.8 | 5,00 | 25,00 |
| Claude Sonnet 5 | 3,00 (2,00 Intro bis 31.08.2026) | 15,00 (10,00 Intro) |
| Claude Haiku 4.5 | 1,00 | 5,00 |

### 3.2 Kosten pro Entscheidungs-Zyklus (1 API-Call)

**Annahmen (offengelegt):** Input ~6.000 Token (Orderbuch-Snapshot + Candles + Indikatoren + System-Prompt), Output ~1.000 Token (strukturierte JSON-Order + Begründung). Ohne Prompt-Caching.

| Modell | Kosten / Call |
|---|---|
| Haiku 4.5 | **$0,011** |
| Sonnet 5 (Standard) | **$0,033** |
| Opus 4.8 | **$0,055** |

> Prompt-Caching (stabiler System-Prompt/Tools ~0,1× Read) senkt Sonnet 5 auf ~$0,025/Call — **moderate** Ersparnis, keine Größenordnung. Adaptive Thinking kann Output (und damit Kosten) dagegen erhöhen.

### 3.3 Tägliche & monatliche API-Kosten nach Handels-Frequenz

| Kadenz | Calls/Tag | Haiku 4.5 /Tag (Monat) | Sonnet 5 /Tag (Monat) | Opus 4.8 /Tag (Monat) |
|---|---|---|---|---|
| alle 5 min | 288 | $3,17 (~$95) | $9,50 (~$285) | $15,84 (~$475) |
| alle 1 min | 1.440 | $15,84 (~$475) | $47,52 (~$1.426) | $79,20 (~$2.376) |
| alle 15 s | 5.760 | $63,36 (~$1.900) | $190,08 (~$5.700) | $316,80 (~$9.500) |

### 3.4 Ab wann die Kosten die Marge auffressen (Break-even)

**Zentrale Einsicht:** API-Kosten sind **fix pro Zeit**, unabhängig von der Kontogröße. Die nötige tägliche Netto-Rendite **nur zur Deckung der Inferenz** skaliert daher invers mit dem Kapital.

Beispiel: **Sonnet 5, 1-Minuten-Kadenz = $47,52/Tag Inferenzkosten.** Nötige tägliche Netto-Rendite nur zum Break-even der API (vor Spread/Gebühren/Profit):

| Kontogröße | Tägl. Rendite nur für API-Deckung | Annualisiert (≈ ×250 Handelstage) |
|---|---|---|
| 10.000 USD | **0,475 %/Tag** | **~119 %/Jahr** — unrealistische Hürde |
| 100.000 USD | 0,0475 %/Tag | ~12 %/Jahr — bereits materiell |
| 1.000.000 USD | 0,00475 %/Tag | ~1,2 %/Jahr — beherrschbar |

**Interpretation:**
- **Retail-Kapital (≤ 100k) + hohe Frequenz = strukturell verlustgarantiert** allein durch Inferenzkosten, noch vor Spread/Slippage/Gebühren.
- LLM-in-the-loop wird erst bei **hohem Kapital ODER niedriger Frequenz** ökonomisch tragfähig.
- **Hebel gegen den Kostenblock:** (a) niedrigere Kadenz (Minuten→Stunden), (b) günstigeres Modell (Haiku für Routine, Opus nur für Research), (c) Prompt-Caching, (d) **LLM aus der Live-Schleife nehmen** → §4 (der eigentliche Fix: Kosten fallen dann nur bei Strategie-Entwicklung an, nicht pro Tick).

---

## 4. Technische Architektur (der einzige profitable Stack)

**Kernprinzip: Code-Ausführung statt Text-Ausführung.** Das LLM produziert **Strategie-Code**, nicht Live-Orders. Ein deterministischer Engine handelt.

### 4.1 Pipeline

```
[1] Strategie-Generierung (LLM / Claude, offline)
     └─ Claude Opus 4.8 erzeugt Strategie als CODE (Python/DSL):
        Entry/Exit-Logik, Indikatoren, Positionsgrößen, harte SL/TP-Regeln.
        (LLM-Stärke lt. Tests: höchste Strategie-/Code-Kompetenz.)
        ↓
[2] Walk-Forward-Backtesting + Out-of-Sample-Validierung
     └─ In-Sample-Optimierung → Out-of-Sample-Test → rollierendes Walk-Forward.
        Overfit-Filter, Transaktionskosten/Spread/Slippage realistisch modelliert.
        Nur robuste Strategien (stabil über mehrere OOS-Fenster) passieren.
        ↓
[3] Deterministische Execution-Engine (KEIN LLM in der Hot-Loop)
     └─ Kompilierte Strategie läuft am Broker/Exchange.
        Mikro-/Millisekunden-Ausführung, harte Risk-Limits, SL/TP,
        Position-Sizing, Kill-Switch. Reproduzierbar, auditierbar.
        ↓
[4] LLM als Research-Copilot (periodisch, offline)
     └─ Regime-Analyse, Post-Mortem, Strategie-Iteration, Anomalie-Triage.
        Getaktet in Stunden/Tagen — nicht pro Tick.
```

### 4.2 Rollen-Zuordnung nach Modell (kostenoptimiert)

| Aufgabe | Modell | Frequenz | Grund |
|---|---|---|---|
| Strategie-/Code-Generierung | Opus 4.8 | selten (offline) | höchste Reasoning-/Code-Qualität, Kosten irrelevant weil selten |
| Backtest-Auswertung, Regime-Triage | Sonnet 5 | periodisch | gutes Preis/Leistung |
| Routine-Klassifikation (News-Sentiment etc.) | Haiku 4.5 | häufig | billigst, schnell |
| **Live-Order-Ausführung** | **KEIN LLM** | Tick | deterministisch, latenzkritisch, auditierbar |

### 4.3 Warum das funktioniert, wo Text-Prompting scheitert

- **Latenz eliminiert** — LLM nicht in der Hot-Loop; Ausführung deterministisch & schnell.
- **Determinismus** — kompilierter Code, reproduzierbar, testbar, compliance-fähig.
- **Kosten entkoppelt** — Inferenzkosten fallen bei Entwicklung an, **nicht pro Trade** → §3-Kostenkurve kollabiert.
- **Overfit-Kontrolle** — Walk-Forward/OOS erzwungen, statt Backtest-Marketing zu glauben.
- **Risk-Management extern erzwungen** — harte SL/TP/Limits im Engine, nicht der LLM-Willkür überlassen (behebt exakt den Alpha-Arena-Fehler).
- **Passt zum realen Tooling** — genau das MCP-/Agent-Harness-Modell (NexusTrade, Claude-via-MCP mit Backtest-Tools). Claude als *Quant-Copilot*, nicht als *Live-Trader*.

---

## 5. Zusammenfassung: Metriken & technische Hürden (kompakt)

### 5.1 Harte Metriken

| Kennzahl | Wert | Quelle |
|---|---|---|
| Claude S4.5 Alpha Arena S1 | negativ (~−12 % bis > −40 %, snapshot-abh.) | nof1 / Reviews |
| Claude S4.5 Alpha Arena S1.5 | **−32,44 %** | nof1 |
| Alpha-Arena-Sieger S1 (bestes Modell) | Qwen3 Max **+22,31 %** (nicht Claude) | Reviews |
| Claude Gebühren-Reibung (10k Konto) | 482 USD (~4,8 %) | nof1 |
| Project Vend Verlust (~1 Monat) | ~200 USD (bis > 1.000 USD) | Anthropic/Andon |
| Auditierter profitabler Live-Claude-ROI | **existiert nicht** | — |
| API-Latenz vs. HFT | ~10⁴–10⁶× langsamer | Größenordnungs-Analyse |
| Inferenzkosten Sonnet 5 @ 1 min | ~$47,52/Tag (~$1.426/Monat) | Preismodell |
| Break-even-Hürde 10k-Konto @ 1 min (Sonnet 5) | ~119 %/Jahr **nur für API** | Preismodell |

### 5.2 Technische Hürden (priorisiert)

1. **Latenz** (physikalisch, nicht behebbar) — LLM raus aus der Ausführungsschleife.
2. **Fixkosten der Inferenz** — killt Retail-Frequenz-Handel; nur via Architektur-Umbau lösbar.
3. **Nicht-Determinismus & Halluzination** — untauglich für direkte Ausführung.
4. **Fehlendes natives Risk-Management** — extern erzwingen (SL/TP/Limits/Kill-Switch).
5. **Overfitting/Backtest-Bias** — Walk-Forward + OOS zwingend; Vendor-Claims ignorieren.
6. **Manipulierbarkeit** (Prompt/Daten) — Input-Härtung, keine ungefilterten externen Daten in den Prompt.

### 5.3 Handlungsempfehlung (eine Zeile)

**Claude nicht als autonomen CFD-Live-Trader einsetzen. Einsetzen als Strategie-/Code-Generator + Research-Copilot; Ausführung deterministisch, LLM-frei, mit hartem Risk-Layer und Walk-Forward-Validierung.**

---

## Quellen

- Alpha Arena / nof1.ai — [nof1.ai](https://nof1.ai/), [Datawallet-Übersicht](https://www.datawallet.com/crypto/alpha-arena-nof1-ai-explained), [iWeaver Season-1-Analyse](https://www.iweaver.ai/blog/alpha-arena-ai-trading-season-1-results/), [Protos: „LLMs can't trade crypto"](https://protos.com/llm-crypto-trading-contest-finds-llms-cant-trade-crypto/), [Euclidean AI Review](https://www.euclideanai.com/blog/llm-crypto-trading)
- Project Vend — [Anthropic: Project Vend Phase 2](https://www.anthropic.com/research/project-vend-2), [MLQ.ai Zusammenfassung](https://mlq.ai/news/anthropics-claude-ai-struggles-as-vending-machine-operator-in-real-world-test/)
- NexusTrade (Architektur/Tests, Vendor-Quelle, kritisch einordnen) — [nexustrade.io](https://nexustrade.io/), [LLM-Vergleichstest](https://nexustrade.io/blog/i-tested-every-major-llm-for-algorithmic-trading-there-is-one-clear-winner-20250811)
- API-Preise (Input/Output pro 1M Token) — Anthropic Claude API Preis-Referenz, Stand Q3 2026.
