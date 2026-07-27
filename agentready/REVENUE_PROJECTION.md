# REVENUE_PROJECTION.md — AgentReady

> Umsatz-/Profit-Projektion **Monat 0–12** unter dem gewählten Modell: **Gratis-Scan → bezahlte Auto-Fix-Shopify-App (Abo, $29/Mo via Shopify Billing).**
> Details Preis: `PRICING.md`. Werte USD (Shopify rechnet USD); CHF ≈ ~10 % weniger.

---

## 1. Das Modell (und was es für Jahr 1 heisst)

Bezahlt ist jetzt ein **Abo** (Done-for-you-App), nicht eine Einmalzahlung. Das heisst:
- **Jahr-1-Cash läuft langsamer an** (Abo compoundet erst über die Zeit) und **später** (die App muss vor dem ersten Umsatz fertig sein, ~Monat 2–3).
- Dafür **klebriger** (wiederkehrend) und der **Shopify App Store ist ein eigener Distributionskanal** → stärkeres Jahr 2.

Bewusster Trade-off gegenüber dem früheren $79-Einmal-Modell (das brachte mehr Jahr-1-Cash, aber kein Auto-Fix und keine Wiederkehr).

---

## 2. Annahmen (Stellschrauben)

| Stellschraube | Basis | Pessimistisch | Optimistisch |
|---|---|---|---|
| Kum. Gratis-Scans Jahr 1 | ~2.000 | ~800 | ~3.500 |
| Anteil Shopify-Shops (App-fähig) | 60 % | 50 % | 70 % |
| Scan → App-Trial | ~15 % der Shopify-Shops | ~8 % | ~25 % |
| Trial → Bezahlt | ~35 % | ~20 % | ~50 % |
| Monatliche Churn | ~5 % | ~8 % | ~3 % |
| Preis | $29/Mo | $29/Mo | Mix inkl. Pro $49 |

**Profit = Umsatz − Kosten, ohne deine Arbeitszeit.** Kosten Jahr 1 ≈ $600 (VPS ~$60 + Claude gedeckelt + ~0 Zahlungsgebühr, weil Shopify Billing bis $1 Mio./Jahr 0 % nimmt).

---

## 3. Basis-Szenario, Monat 0–12

> **Zeitleiste korrigiert:** Mit Claude Code zählen nur *deine* Stunden (Setup/Prüfen/Testen) → **App live ~Monat 2–3**, nicht Monat 5 (`PLAN.md`).

| Monat | Phase | Scans/Mon. | Neue Trials | Zahlende Shops (Ende) | **MRR (USD)** |
|---|---|---:|---:|---:|---:|
| 0–1 | Bauen (Scan → Worker → Frontend) | 0 | 0 | 0 | **$0** |
| 2 | Gratis-Scan live, App im Bau | 50 | 0 | 0 | **$0** |
| 3 | **App live** ⭐ | 100 | 4 | 2 | **~$58** |
| 4 | Unlisted Link + Outbound | 150 | 6 | 5 | **~$145** |
| 5 | Ramp | 200 | 8 | 9 | **~$261** |
| 6 | Ramp | 240 | 9 | 13 | **~$377** |
| 7 | Ramp | 280 | 11 | 18 | **~$522** |
| 8 | Ramp | 310 | 12 | 23 | **~$667** |
| 9 | Ramp | 340 | 13 | 28 | **~$812** |
| 10 | Ramp | 360 | 14 | 33 | **~$957** |
| 11 | Ramp | 380 | 15 | 38 | **~$1.102** |
| 12 | Ramp | 400 | 16 | **~43** | **~$1.247** |

**Basis Jahr 1:** Total eingenommen ≈ **$5.000–5.500**, Profit ≈ **$4.500–5.000**. Exit Monat 12: **~$1.250 MRR** (~$15k ARR-Run-Rate), **alles wiederkehrend**. (Höher als die frühere Rechnung, weil die App ~2 Monate früher live ist.)

Vergleich: Weniger Jahr-1-Cash als das $79-Einmal-Modell (~$6k), aber **höhere Exit-MRR und voll wiederkehrend** — das zahlt sich in Jahr 2 aus.

---

## 4. Bandbreite

| Szenario | Jahr-1-Umsatz | Jahr-1-Profit | Exit-MRR |
|---|---:|---:|---:|
| **Pessimistisch** | $600–1.500 | $0–1.000 | ~$200 |
| **Basis** | ~$3.000–3.500 | ~$2.700 | ~$780 |
| **Optimistisch** (App-Store rankt + Pro-Tarif) | $8.000–14.000 | ~$7.500–13.000 | ~$1.800 |

---

## 5. Ausblick Jahr 2 (hier zahlt sich das Abo aus)

Weil alles wiederkehrend ist, compoundet Jahr 2 stark (Neukunden minus Churn auf wachsender Basis):
- **Basis:** Exit-MRR von ~$780 → ~$2.000–2.800/Mo bis Monat 24; **Jahr-2-Umsatz ~$20.000–28.000**, Profit ~$18.000–25.000 — grob **6–8× Jahr 1**.
- **Grösster Hebel:** Churn (Jahres-Abrechnung/Jahrestarif als Gegenmittel) + **Shopify-App-Store-Ranking** (Bewertungen der ersten Kunden = Ranking = organische Installs). Der App Store ist selbst power-law (Median-App < $1k/Mo) — kein Selbstläufer, aber ein echter Kanal, den das $79-Modell nicht hatte.

---

## 6. Der ehrliche Vorbehalt

Alles hängt an: (a) genug Traffic auf den Gratis-Scan, (b) Anteil Shopify-Shops, (c) ob die App im Trial sichtbaren Wert liefert (Score-Anstieg zeigen!), (d) ob das App-Store-Listing rankt. Der **App-Store-Weg braucht ausserdem einen Review-Prozess** — für die ersten Kunden geht ein *unlisted Custom-App-Install-Link* schneller (siehe `PLAN.md` M7). Validierungssignal: **App-Installationen + Trial→Bezahlt-Rate.**
