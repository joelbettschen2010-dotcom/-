# AgentReady — Planungs-Prompt für Claude Code

> **So benutzt du das:**
> 1. Leeren Ordner anlegen, diese Datei darin als `PLANNING_BRIEF.md` speichern
> 2. Claude Code im Ordner starten, erste Nachricht:
>    **„Lies `PLANNING_BRIEF.md` vollständig und arbeite es ab. Schreibe keinen Produktivcode."**
> 3. Claude Code recherchiert, stellt dir Rückfragen, plant — und erzeugt am Ende `BUILD_PROMPT.md`
> 4. Diese `BUILD_PROMPT.md` gibst du in einer **neuen** Claude-Code-Sitzung als erste Nachricht ein. Dann wird gebaut.

---

## 0. Die eine absolute Regel dieser Sitzung

**In dieser Sitzung wird kein Produktivcode geschrieben.** Kein `src/`, keine Komponenten, keine Migrationen, keine Worker-Implementierung. Erlaubt sind ausschliesslich: Recherche, Markdown-Dokumente, Datenbankschema als SQL *im Dokument* (nicht als ausführbare Migration), und maximal kurze Pseudocode-Ausschnitte zur Illustration einer Architekturentscheidung.

Wenn du merkst, dass du anfängst zu implementieren: stopp, und schreib stattdessen auf, *wie* es implementiert werden soll.

---

## 1. Rolle und Kontext

Du bist technischer Lead für ein Solo-Produkt. Der Auftraggeber ist ein technisch versierter Einzelentwickler (Python, Next.js, Supabase, APIs, n8n, eigener Linux-Server mit GPU). Kein Team. Budget unter 30 CHF/Monat laufend. Zeitbudget nach dem Launch: **6–8 Stunden pro Woche, für alles zusammen** — Betrieb, Support, Marketing, Wartung.

Diese 6–8 Stunden sind die härteste Randbedingung des ganzen Projekts. Jede Architekturentscheidung wird daran gemessen: Erzeugt sie wiederkehrende manuelle Arbeit? Dann ist sie falsch, auch wenn sie technisch eleganter ist.

**Kommunikation mit dem Auftraggeber: Deutsch. Code, Commits, Produkt: Englisch.**

---

## 2. Das Produkt

**AgentReady** prüft, ob ein Onlineshop für KI-Einkaufsagenten (ChatGPT Shopping, Google AI Mode, Perplexity, Copilot) auffindbar und lesbar ist, und sagt dem Betreiber in klarer Sprache, was er ändern muss.

Shops sind seit 20 Jahren für menschliche Browser optimiert. KI-Agenten entscheiden aber anhand strukturierter Daten. Wessen Produktdaten für Maschinen unvollständig sind, taucht in KI-Antworten nicht auf — und verliert einen Kanal, der dreistellig wächst.

**In dieser Ausbaustufe zu planen ist nur das Validierungsprodukt:**

> Kostenloser öffentlicher Scan: URL rein → nach 1–3 Minuten ein Score von 0–100 plus priorisierte Fix-Liste. E-Mail optional für den vollständigen Report.

Bezahltes Monitoring, Shopify-App und Nutzerkonten kommen später. Sie werden **nicht gebaut**, aber die Architektur darf sie nicht verbauen (Abschnitt 7).

---

## 3. Der KI-Support — gleichrangige Komponente, nicht Beiwerk

Support ist bei diesem Zeitbudget die Komponente, die das Produkt am schnellsten unbenutzbar macht. Sie wird deshalb von Anfang an mitgeplant, nicht nachgerüstet.

### 3.1 Zielbild
Ein Support-Agent auf Claude-Basis, der Anfragen selbständig löst — **geerdet in echten Daten, nicht in Vermutungen**:
- die Wissensbasis (Doku, FAQ, Fehlerkatalog)
- die tatsächlichen Scan-Ergebnisse des anfragenden Nutzers
- dessen Konto- und Abo-Status

**Nur handelnde Agenten erreichen hohe Auflösungsraten.** Ein Agent, der bloss FAQ-Texte zitiert, löst wenig. Einer, der den konkreten Scan des Kunden nachschlagen, einen Befund erklären und einen Re-Scan auslösen kann, löst viel. Plane deshalb einen kleinen, klar begrenzten Satz von Werkzeugen für den Agenten, nicht nur Textsuche.

### 3.2 Realistische Erwartung — recherchiere und prüfe das nach
Der Auftraggeber geht von folgenden Grössenordnungen aus; verifiziere sie und korrigiere, wenn du Besseres findest:
- Reife, handelnde Agenten auf gut abgegrenzten Anwendungsfällen: **etwa 50–80% echte Auflösung**
- Median im ersten Jahr über alle B2B-SaaS: **nur 10–15%**
- Der grösste Einflussfaktor ist nicht das Modell, sondern die **Aktualität der Wissensbasis** — Teams mit frischer Doku erreichen ein Vielfaches der Rate von Teams mit veralteter

Daraus folgt eine Anforderung, die du in den Plan aufnehmen musst: **Jede Eskalation an den Menschen muss verpflichtend einen Wissensbasis-Eintrag erzeugen.** Ohne diese Rückkopplung sinkt die Auflösungsrate über die Zeit, statt zu steigen. Plane den Mechanismus, der das erzwingt.

### 3.3 Stufenweise Freigabe — nicht sofort autonom
- **Stufe A (Start):** Der Agent entwirft, der Mensch gibt jede Antwort frei. Alle Entwürfe und Korrekturen werden protokolliert.
- **Stufe B:** Für einzelne, gemessen zuverlässige Anfragekategorien wird autonomes Antworten freigeschaltet — kategorieweise, nicht global.
- **Stufe C:** Mehrheit autonom, Mensch prüft Stichproben.

Plane die Messgrössen, die den Übergang zwischen den Stufen rechtfertigen. Vorschlag: echte Auflösungsrate pro Kategorie, Wiederkontaktquote innerhalb von 72 Stunden, Eskalationsquote, Anteil vom Menschen korrigierter Entwürfe. Verbessere den Vorschlag.

### 3.4 Was der Agent niemals autonom tun darf
Diese Grenzen sind nicht verhandelbar und gehören verbindlich in den Plan:
1. **Geld:** Rückerstattungen, Gutschriften, Rechnungs- und Abo-Änderungen — immer Mensch.
2. **Verärgerte Kunden und Kündigungsabsichten** — Beschwerden sind nachweislich die schwächste Kategorie für autonome KI. Sofort eskalieren.
3. **Empfehlungen, die den Shop des Kunden beschädigen könnten** — alles, was ihn dazu bringt, Daten, Markup oder Feeds zu löschen oder umzubauen, geht mit Warnhinweis und, im Zweifel, an den Menschen.
4. **Zusagen zu Funktionen, Terminen oder Ergebnissen.** Nie.
5. **Antworten ohne Beleg.** Steht es nicht in der Wissensbasis oder in den Daten des Nutzers, wird nicht geraten, sondern eskaliert. Plane das als harte Regel, nicht als Prompt-Bitte.

### 3.5 Kennzeichnungspflicht
Der Agent muss sich als KI zu erkennen geben. Ab dem **2. August 2026** verlangt Artikel 50 der EU-KI-Verordnung genau das, mit extraterritorialer Wirkung — auch für Schweizer Anbieter mit EU-Kunden. Recherchiere den aktuellen Stand und die konkrete Umsetzung. Ein Produkt, das Shops zu KI-Themen berät und selbst gegen die Transparenzpflicht verstösst, ist nicht zu verteidigen.

### 3.6 Kostenrahmen
Obergrenze pro Ticket festlegen, Kosten pro Ticket protokollieren, Modellwahl über Umgebungsvariable steuerbar. Günstiges Modell für Klassifikation und Routing, stärkeres nur für die eigentliche Antwort.

---

## 4. Rahmenbedingungen

| Bereich | Vorgabe |
|---|---|
| Frontend | Next.js 15 App Router, TypeScript, Tailwind → Vercel Hobby |
| Datenbank | Supabase Free |
| Scan-Worker | Python 3.12 auf dem eigenen Linux-Server (Vercel-Funktionen haben zu kurze Laufzeitgrenzen für einen 30–120-Sekunden-Crawl) |
| KI | Claude API |
| Produktsprache | Englisch (Zielmarkt US/UK) |
| Zahlungen | später Shopify Billing bzw. Lemon Squeezy — jetzt nicht |
| Self-serve | Alles ohne Anruf, ohne Onboarding-Gespräch, ohne manuelle Freischaltung |

---

## 5. Leitplanken — stehen über allen technischen Zielen

1. **Nur öffentlich zugängliche Seiten abrufen.** Keine Login-Bereiche, keine Umgehung von Zugangsschranken.
2. **`robots.txt` des gescannten Shops respektieren.** Schliesst sie uns aus, brechen wir ab und melden das als Ergebnis — wir umgehen es nicht.
3. **Höflich crawlen:** ehrlicher User-Agent mit Kontakt-URL, mindestens 1 s Pause pro Domain, maximal 15 Seiten pro Scan, 10 s Timeout.
4. **Read-only.** Keine Formulare absenden, keine Zustandsänderungen auf fremden Seiten.
5. **Keine personenbezogenen Daten speichern.**
6. **Keine Sicherheitslücken suchen.** Dieses Produkt bewertet Datenqualität und Auffindbarkeit, sonst nichts.
7. **SSRF verhindern:** URL-Validierung gegen private IP-Bereiche, localhost, Metadaten-Endpunkte.
8. **Row Level Security auf jeder Supabase-Tabelle ab Tag 1.** Der `service_role`-Key existiert nur im Worker und in Server-Umgebungsvariablen — nie im Frontend-Bundle, nie in einem Commit.
9. **Keine Compliance- oder Ergebnisgarantien im Produkttext.** Wir liefern Hinweise und Priorisierung.

---

## 6. Dein Arbeitsablauf in dieser Sitzung

### Schritt 1 — Recherche
Deine Trainingsdaten reichen hier nicht. Recherchiere mit Quellenangabe:

- **Aktuelle KI-Crawler-User-Agents** und ihre Adressierung in `robots.txt`. Mindestens: OpenAI (`GPTBot`, `OAI-SearchBot`, `ChatGPT-User`), Anthropic (`ClaudeBot`, `Claude-User`, `Claude-SearchBot`), `PerplexityBot`, `Perplexity-User`, `Google-Extended`, `Bingbot`, `Amazonbot`, `Applebot-Extended`, `meta-externalagent`. Die Liste ändert sich laufend.
- **`llms.txt`:** Spezifikation, Verbreitung, und ob die Engines sie überhaupt lesen. Sei skeptisch und schreib ehrlich ins Dokument, wenn die Belege dünn sind.
- **Schema.org `Product`:** Pflicht- und Empfehlungsfelder, insbesondere `offers`, `shippingDetails`, `hasMerchantReturnPolicy`, `aggregateRating`.
- **Legitimer Zugriff auf die Antwort-Engines** für spätere Sichtbarkeitsprüfung: nur offizielle APIs. Begründe, warum das Abgreifen der Consumer-Oberflächen keine Option ist.
- **Shopify:** öffentliche Endpunkte (`/products.json`, `sitemap.xml`), was zulässig ist.
- **KI-Support:** aktuelle Auflösungsraten-Benchmarks, Unterschied Deflection/Resolution, Architekturmuster für geerdete Support-Agenten.
- **EU-KI-Verordnung Artikel 50:** aktueller Stand der Kennzeichnungspflicht.

Ergebnis: **`RESEARCH.md`** mit Quellen und Datum.

### Schritt 2 — Rückfragen an mich
Bevor du planst: Stell mir die Fragen, die du nicht selbst entscheiden kannst oder solltest. Maximal die zehn wichtigsten, jeweils mit deiner Empfehlung und der Alternative. Beispiele für Fragen, die du wahrscheinlich haben wirst: Umgang mit rein clientseitig gerenderten Shops, Produktname und Domain, Erkennung von Produktseiten, Missbrauchsschutz für den Gratis-Scan.

**Dann warten.** Plane nicht weiter, bevor ich geantwortet habe.

### Schritt 3 — Planungsdokumente
Nach meinen Antworten erstellst du:

- **`PLAN.md`** — Meilensteine mit Stundenschätzung, Reihenfolge, Abhängigkeiten, je ein prüfbares Abnahmekriterium
- **`ARCHITECTURE.md`** — Komponenten, Datenfluss, Schema mit RLS-Policies, Umgebungsvariablen, Deployment, Betrieb
- **`SCORING.md`** — das Bewertungsmodell, jede Prüfung mit Gewicht und Begründung, warum sie für KI-Auffindbarkeit relevant ist. Ausgangsvorschlag: Crawler-Zugang 25%, strukturierte Produktdaten 30%, Datenqualität 20% (von Claude bewertet), Maschinenlesbarkeit 15%, Vertrauenssignale 10%. **Prüfe das kritisch und verbessere es** — der Score muss verteidigbar sein, wenn jemand fragt „warum 62?"
- **`SUPPORT_AGENT.md`** — Architektur nach Abschnitt 3: Werkzeuge, Wissensbasis-Aufbau und -Pflege, Eskalationsregeln, Stufenfreigabe mit Messgrössen, Kennzeichnung, Kostenrahmen
- **`RISKS.md`** — was das Projekt kippen kann, mit Frühwarnsignal und Gegenmassnahme je Risiko

### Schritt 4 — Der Bau-Prompt
Letztes und wichtigstes Ergebnis dieser Sitzung: **`BUILD_PROMPT.md`**.

Das ist der Text, den ich in einer **frischen Claude-Code-Sitzung ohne jeden Kontext** als erste Nachricht einfüge. Er muss deshalb allein funktionieren.

Er muss enthalten:
1. **Produktkontext in Kurzform** — genug, dass eine Sitzung ohne Vorwissen versteht, was gebaut wird und warum
2. **Die Leitplanken aus Abschnitt 5 wörtlich** — sie dürfen nicht in einem Nebendokument verschwinden
3. **Die getroffenen Architekturentscheidungen** mit Verweis auf `ARCHITECTURE.md` für Details
4. **Die Meilensteine in Reihenfolge**, jeder mit Abnahmekriterium und der Anweisung, nach jedem Meilenstein zu stoppen und Bericht zu erstatten
5. **Die Anweisung, zuerst `CLAUDE.md` im Repo anzulegen** mit Projektkontext, Befehlen, Konventionen und Leitplanken
6. **Klare „frag nach statt zu raten"-Regeln** — bei welchen Entscheidungen soll gefragt, bei welchen selbständig entschieden werden
7. **Was ausdrücklich nicht gebaut wird** (Abschnitt 7)
8. **Die Arbeitsregeln:** kleine Commits, `.env.example`, keine Geheimnisse im Repo, Tests für Parser und Scoring-Logik mit eingefrorenen HTML-Fixtures, keine Abhängigkeit ohne Begründung
9. **Das Erfolgskriterium** (Abschnitt 8)

Schreib ihn so, dass ich ihn ohne eine einzige Änderung einfügen kann.

---

## 7. Ausdrücklich nicht bauen — aber nicht verbauen

Später, nicht jetzt: Nutzerkonten und Abos, Zahlungen, wiederkehrendes Monitoring mit Wochenreport, Sichtbarkeitsprüfung gegen die Antwort-Engines, automatische Erzeugung von Schema.org-Markup, Shopify-App-Verpackung, mehrere Shops pro Konto.

**Vorsorge beschränkt sich auf:** `user_id`-Spalte (nullable) im Schema, Scan-Logik als aufrufbare Funktion statt Skript-Monolith, Report-Rendering getrennt von Scan-Ausführung. Mehr nicht — jede weitere Vorsorge ist verfrühte Verallgemeinerung.

---

## 8. Erfolgskriterium

Nicht „die App ist fertig", sondern:

> **Der Auftraggeber kann 30 echte Shop-Betreiber scannen lassen und messen, wie viele danach nach einer wiederkehrenden Version fragen.**

Alles, was diesem Messpunkt nicht dient, ist in dieser Ausbaustufe Ballast. Wenn ein Meilenstein diesem Ziel nicht dient, schlag vor, ihn zu streichen.

---

## 9. Wie du arbeitest

- **Widersprich mir.** Wenn etwas in diesem Brief technisch schlecht ist, sag es und begründe es. Ich habe das Produkt entworfen, nicht die Implementierung.
- **Rückfragen kosten zwei Minuten, falsche Annahmen kosten einen Tag.**
- **Jede Empfehlung mit Begründung**, nicht mit Behauptung.
- **Sei konkret bei Schätzungen.** „Ein paar Stunden" hilft nicht, „4–6 h, Risiko bei clientseitig gerenderten Shops" schon.

---

**Starte jetzt mit Schritt 1. Kein Produktivcode in dieser Sitzung.**
