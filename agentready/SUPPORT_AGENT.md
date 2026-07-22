# SUPPORT_AGENT.md — AgentReady

> Vollständige Architektur des KI-Supports (Brief Abschnitt 3). Gebaut wird **das ganze System**, betrieben zunächst in **Stufe A** (Mensch gibt jede Antwort frei); Stufen B/C sind gebaut, aber per Kategorie-Flag **gesperrt**, bis Messgrössen sie rechtfertigen.
> Grundlage: `RESEARCH.md` §6, Schema in `ARCHITECTURE.md` §4.

---

## 1. Zielbild & Grundprinzip

Ein **handelnder**, in echten Daten **geerdeter** Claude-Agent — nicht ein FAQ-Zitierer (RESEARCH §6.4). Er darf nachschlagen (Wissensbasis, konkreter Scan des Nutzers, Konto-/Abo-Status) und begrenzt handeln (Re-Scan auslösen). Grundregel über allem:

> **Belegpflicht:** Steht die Antwort nicht in der Wissensbasis oder in den Daten des Nutzers → **nicht raten, sondern eskalieren.** Als harte Code-Regel (§5), nicht als Prompt-Bitte.

---

## 2. Werkzeuge (klein & klar begrenzt)

Genau vier Read-Werkzeuge + ein eng begrenztes Handeln + Eskalation. Mehr nicht (Brief: „kleiner, klar begrenzter Satz").

| Tool | Zweck | Grenzen |
|---|---|---|
| `kb_search(query)` | Wissensbasis durchsuchen (Postgres-FTS v1, pgvector später) | nur `status='published'` |
| `get_user_scan(email\|token\|domain)` | Konkreten Scan + Findings des Anfragers laden | nur Scans, die zur Anfrage passen |
| `get_account_status(email)` | Konto-/Abo-Status | v1 minimal (keine Konten): liefert Lead-/Scan-Historie; Platzhalter für spätere Abo-Felder |
| `trigger_rescan(domain\|scan_id)` | Neuen Scan einreihen | idempotent, rate-limited, nur öffentliche URL |
| `escalate(reason)` | An Mensch übergeben | erzeugt `escalations`-Zeile; siehe §5/§7 |

**Jeder** Tool-Aufruf wird in `agent_actions` protokolliert (Input/Output/Kosten). Der Entwurf darf sich nur auf Tool-Outputs + KB stützen (Grounding).

**Was es bewusst NICHT gibt:** kein Tool, das Geld bewegt, Abos ändert, Daten löscht oder auf fremden Seiten etwas verändert. Solche Wünsche → immer Eskalation (§4).

---

## 3. Pipeline pro Ticket

```
Eingang (E-Mail/Formular) → Ticket
   │
   ├─ 1. Klassifikation (günstiges Modell): Kategorie + Sentiment + „geldbezogen?"
   │
   ├─ 2. Harte Routing-Gates (§4): money | complaint/at_risk | harmful | promise | no_evidence → ESKALIEREN
   │
   ├─ 3. Grounding: kb_search + get_user_scan + get_account_status (nach Bedarf)
   │
   ├─ 4. Entwurf (stärkeres Modell), NUR aus Grounding-Belegen; ohne Beleg → ESKALIEREN
   │
   ├─ 5. Freigabe je nach Stufe/Kategorie:
   │        Stufe A / draft_only → Mensch prüft im Review-UI → sendet
   │        Stufe B / auto (freigeschaltete Kategorie) → autonom senden, Stichprobe
   │
   └─ 6. Senden mit KI-Kennzeichnung (§6). Metriken aktualisieren (§8).
```

- **Klassifikationskategorien:** `billing`, `scan-error`, `interpreting-results`, `account`, `complaint`, `other`.
- **Sentiment:** `neutral | negative | at_risk` (Kündigungs-/Beschwerdesignale).
- **Modell-Split (Brief 3.6):** günstiges Modell für Schritt 1/2, stärkeres nur für Schritt 4.

---

## 4. Was der Agent NIEMALS autonom tut (verbindlich, aus Brief 3.4)

Diese Fälle werden **immer** eskaliert — auch in Stufe C:

1. **Geld:** Rückerstattungen, Gutschriften, Rechnungs-/Abo-Änderungen → immer Mensch.
2. **Verärgerte Kunden & Kündigungsabsichten** (Sentiment `at_risk` / Kategorie `complaint`) → sofort eskalieren. (Belegt schwächste KI-Kategorie: 19–34 %, RESEARCH §6.2.)
3. **Empfehlungen, die den Shop beschädigen könnten** — alles, was zu Löschen/Umbau von Daten, Markup oder Feeds rät → nur mit Warnhinweis, im Zweifel Mensch.
4. **Zusagen** zu Funktionen, Terminen, Ergebnissen → nie.
5. **Antworten ohne Beleg** → eskalieren statt raten.

Diese Gates sind **Code**, kein Prompt: Die Klassifikation setzt Flags; ein Router zwingt bei jedem Flag in den Eskalationspfad, bevor überhaupt ein Antwort-Entwurf erzeugt wird.

---

## 5. Grounding & Belegpflicht (harte Regel)

- Der Antwort-Entwurf muss **Belege referenzieren** (KB-Artikel-IDs und/oder konkrete Scan-Findings), die aus Tool-Outputs stammen.
- **Durchsetzung im Code:** Der Entwurf wird strukturiert erzeugt (`{answer, citations[]}`). Ist `citations` leer oder verweist auf nichts real Geladenes → System verwirft den Entwurf und ruft `escalate("no_evidence")`. Kein „bestes Raten".
- Das verhindert Halluzination als Systemeigenschaft, nicht als Bitte an das Modell.

---

## 6. Kennzeichnungspflicht (EU-KI-VO Art. 50, ab 2.8.2026)

- **Jede** vom Agenten (mit)verfasste Nachricht enthält einen klaren Hinweis: *„You're chatting with AgentReady's AI assistant."* — auch bei menschlich freigegebenen Entwürfen (Stufe A), da KI-generiert.
- UI kennzeichnet den Kanal zusätzlich als KI-gestützt.
- Extraterritorial → gilt auch für uns (CH) bei EU-Kunden (RESEARCH §7). Bussen bis 15 Mio. €/3 % Umsatz.
- Keine Ergebnis-/Compliance-Garantien in Antworten (Leitplanke 9).

---

## 7. Wissensbasis: Aufbau & Pflege

- **Seed (Launch):** FAQ, Fehlerkatalog (häufige Scan-Fehler: robots-blockiert, CSR/JS-only, kein Schema, Timeout), „Report lesen"-Erklärungen, Datenschutz/AI-Disclosure. Als `kb_articles` (`status='published'`).
- **Erzwungene Rückkopplung (Brief 3.2 — der wichtigste Pflegemechanismus):** **Jede Eskalation muss einen KB-Eintrag erzeugen**, bevor sie als `resolved` gilt. Durchgesetzt per DB-Trigger (`ARCHITECTURE.md` §4.1) **und** im Review-UI (Resolve-Button erst aktiv, wenn KB-Artikel verknüpft/erstellt). Ohne diese Schleife sinkt die Auflösungsrate über die Zeit (RESEARCH §6.3).
- **Frische:** `last_reviewed_at` je Artikel; einfache Ansicht „Artikel > 90 Tage nicht geprüft" für den wöchentlichen Betrieb.

---

## 8. Stufenweise Freigabe & Messgrössen

Autonomie wird **pro Kategorie** freigeschaltet (nie global), gesteuert über ein Flag (`tickets.autonomy_mode` bzw. eine `category_autonomy`-Konfig). Der Mensch flippt es erst, wenn Metriken es rechtfertigen.

**Metriken je Kategorie (rollierendes Fenster, z. B. letzte ≥30 Tickets):**
| Metrik | Definition |
|---|---|
| Echte Auflösungsrate | gelöst ohne relevante menschliche Korrektur **und** kein Wiederkontakt in 72 h |
| Wiederkontaktquote 72 h | `reopened_72h` / gelöste Tickets |
| Eskalationsquote | eskaliert / alle Tickets der Kategorie |
| Korrekturquote | Anteil Entwürfe, die der Mensch inhaltlich editiert (`human_edited`) |
| Policy-Verstösse | Anzahl Fälle, in denen ein Never-Autonomous-Gate hätte greifen müssen (muss 0 sein) |

**Übergangsregeln (Vorschlag, im Betrieb justierbar):**
- **A → B (Kategorie):** ≥30 Tickets, echte Auflösung ≥ 70 %, Wiederkontakt ≤ 10 %, Korrekturquote ≤ 15 %, **0** Policy-Verstösse.
- **B → C:** stabile B-Werte über weiteres Fenster + fortlaufende Stichprobenprüfung; Mensch prüft nur noch Stichproben.
- **Rückfall:** Reissen die Werte (z. B. Wiederkontakt steigt), fällt die Kategorie automatisch auf `draft_only` zurück (Alarm an Betreiber).

Diese Schwellen sind bewusst konservativ und decken sich mit den Benchmarks (RESEARCH §6.2: strukturierte Intents erreichen 65–80 %, sentimentlastige nur 19–34 % → Letztere kommen nie in den Autonomie-Pfad).

---

## 9. Kostenrahmen (Brief 3.6)

- **Deckel pro Ticket:** `MAX_TICKET_COST_CENTS`; wird er im Verlauf erreicht → Eskalation an Mensch.
- **Monatsdeckel:** `MONTHLY_CLAUDE_BUDGET_CENTS` (geteilt mit Scan-Narrativ); bei Überschreitung → gesamter Support fällt auf `draft_only` bzw. reine menschliche Bearbeitung.
- **Kosten pro Ticket protokolliert** (`tickets.cost_cents`, Summe aus `agent_actions.cost_cents`).
- **Modellwahl per Env** (`ANTHROPIC_MODEL_CLASSIFY`, `ANTHROPIC_MODEL_ANSWER`). Vergleich: ~5 $/KI-Resolution vs. ~30 $ menschlich (RESEARCH §6.2) rechtfertigt den Deckel-Ansatz.

---

## 10. Review-UI (Stufe A, minimal aber vollständig)

Eine schlanke, geschützte interne Seite (kein öffentliches Konto-System):
- Liste offener Tickets + Entwurf, Grounding-Belege, Klassifikation, geschätzte Kosten.
- Aktionen: **Freigeben / Editieren+Freigeben / Eskalieren**. Editieren setzt `human_edited=true` (misst Korrekturquote).
- Bei Eskalation: Pflichtfeld „KB-Artikel erstellen/verknüpfen" vor „Resolve".
- Metrik-Übersicht je Kategorie (§8) — die Datengrundlage für Stufen-Entscheide.

---

## 11. Betrieb auf 6–8 h/Woche

Der Support ist so gebaut, dass er **keine** wiederkehrende manuelle Arbeit erzwingt, die nicht skaliert:
- Stufe A kostet anfangs Zeit (jede Antwort prüfen) — aber jede Eskalation baut die KB aus → weniger Eskalationen über Zeit.
- Sobald eine Kategorie stabil ist, schaltet Autonomie sie frei → menschliche Zeit sinkt.
- Alarme (Budget, Rückfall, alte KB-Artikel) statt Dauer-Monitoring.
