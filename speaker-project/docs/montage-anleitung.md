# Montage-Anleitung — SpeakerBox Pro

## 1. PCB-Bestückung

**Von JLCPCB bestückt (SMT, Top-Seite):** alle 0603/0805/1206-Passivteile,
TPA3118, ADAU1701, LMR33630, AMS1117, EEPROM, FETs, Dioden, Quarz, WS2812B.

**Von Hand zu löten (THT/Spezial):**
1. ESP32-S3-WROOM-1-Modul (falls nicht bestückt bestellt): erst die
   GND-Pads verzinnen, Modul ausrichten, mit viel Flussmittel randlöten.
   Das EP-Pad unter dem Modul über die Durchkontaktierungen von unten heizen.
2. Leistungsinduktivitäten L1–L7 (grosse Pads, 350 °C, Geduld).
3. Elkos C80–C82 (Polung! Minus-Streifen zeigt zum Platinenrand).
4. Stecker: XT30 (J1), JST-XH (J2/J3), JST-PH (J14), Schraubklemmen (J7–J9),
   USB-C (J11 — Pads vorverzinnen), Klinkenbuchse (J6), Stiftleisten.
5. Button-Board: Taster (13-mm-Stössel), JST-PH, OLED-Buchsenleiste.

**Erstkontrolle vor dem ersten Strom:** Multimeter-Durchgangsprüfung
GND↔PVDD, GND↔5V, GND↔3V3 — nirgends darf ein Kurzschluss sein (< 10 Ω).

## 2. Akku-Zusammenbau (4S2P)

⚠️ **18650-Zellen ohne Schutzschaltung sind gefährlich. Kurzschluss = Brand.**

1. Zellen paarweise auf gleiche Spannung bringen (±0.05 V) — vorher einzeln
   auf ~3.8 V laden.
2. Je 2 Zellen parallel (P-Gruppen) mit Nickelstreifen punktschweissen
   (NICHT löten — Hitze schädigt die Zellen; wer nur löten kann: grosse
   Lötspitze, viel Leistung, < 2 s Kontaktzeit).
3. Die 4 P-Gruppen in Serie: B− → G1 → G2 → G3 → B+.
4. Balancerkabel (JST-XH 5-polig): Schwarz an B−, dann aufsteigend die
   Verbindungspunkte G1/G2/G3, Rot an B+. **Reihenfolge doppelt prüfen —
   falsch gesteckt zerstört das Lade-Board.**
5. Hauptleitungen (mind. 1.5 mm², Silikon) an B+/B− mit XT30-Stecker (male
   auf Akkuseite = Buchse führt Spannung, keine offenen Stifte!).
6. Pack mit Kaptonband isolieren, in Schrumpfschlauch einschweissen.
7. Kontrolle: Gesamtspannung 14.0–16.0 V, Balancer-Pins je ~3.5–4.0 V
   aufsteigend (1S, 2S, 3S, 4S gegen B−).

## 3. Gehäuse-Montage

1. **Dichtheit zuerst:** alle 3 Kammern innen mit Epoxid oder dickem
   Acryl-Dichtmittel an den Druckschichten versiegeln (PETG-Drucke sind
   selten luftdicht!). Test: Treiberöffnung mit Folie verschliessen,
   hineindrücken — die Folie muss langsam zurückfedern.
2. Dämmwolle: Mid/High-Kammern locker zu 1/3 füllen, Sub-Kammer zu 1/2
   (Polyesterwatte). Nicht gegen die Membranrückseite drücken.
3. Kabel durch die Kammerwände führen und die Durchführungen mit
   Heisskleber/Epoxid **luftdicht** vergiessen (2×0.75 mm² pro Treiber).
4. Treiber einsetzen: Dichtring aus 2-mm-Moosgummi oder Dichtband unter
   jeden Flansch, über Kreuz anschrauben (Einschmelzmuttern im PETG).
5. Elektronikkammer: Main-Board auf M3-Abstandshalter, Akku mit
   Klettband + Anschlagkante fixieren, Button-Board hinter die Front
   (Stössel durch die Bohrungen), OLED aufstecken.
6. Polung: Klemme „+" an Treiber „+" (meist rote Markierung). Die
   Sub-Invertierung macht der DSP — **nicht** hardwareseitig verpolen.

## 4. Erstinbetriebnahme

**Stufe 1 — ohne Treiber, ohne Akku:** USB-C anstecken. Die 3V3- und
5V-LEDs bzw. Testpunkte prüfen (5 V ≈ 4.7 V über USB-Diode, 3.3 V exakt).
ESP32 meldet sich auf dem seriellen Monitor (`pio device monitor`).

**Stufe 2 — Akku, ohne Treiber:** Akku anstecken (XT30 + Balancer).
Power-Taster 1 s drücken → Status-LED orange → grün. Spannungen messen:
PVDD = Akkuspannung, 5.0 V, 3.3 V, DVDD 1.8 V am ADAU1701 (Pin 13/24!).
2 s halten → Gerät schaltet ab (Ruhestrom < 0.1 mA).

**Stufe 3 — DSP programmieren** (siehe Abschnitt 5), dann Aux-Quelle
anschliessen und die drei Verstärkerausgänge mit dem Oszilloskop oder
Multimeter (AC) prüfen: Musiksignal sichtbar, kein DC-Offset > 50 mV.

**Stufe 4 — Treiber anschliessen** (Gerät AUS dabei!), leise testen,
Kanalzuordnung prüfen (L/R/Sub), dann Web-App verbinden.

## 5. DSP-Programmierung (einmalig)

1. SigmaStudio (kostenlos, Analog Devices) + USBi/ICP5-Programmer.
2. Programmer an J5 stecken (Pinout auf dem Bestückungsdruck: 3V3, GND,
   SCL, SDA, /RST, SELFBOOT, WB, WP).
3. Neues Projekt: ADAU1701, 48 kHz, Self-Boot. Schaltung exakt nach
   `dsp/README.md` aufbauen (Blockreihenfolge = Parameteradressen!).
4. „Link Compile Download" → testen → „Write Latest Compilation to EEPROM".
5. Parameteradressen gegen `dsp/speakerbox_pro_params.xml` prüfen
   (Capture-Fenster); bei Abweichung die `DSP_ADDR_*`-Defines in
   `firmware/include/dsp_coefficients.h` anpassen und Firmware neu flashen.
6. Ab jetzt bootet der DSP autonom aus dem EEPROM; die Firmware schreibt
   Volume/EQ/Preset zur Laufzeit per Safeload.

## 6. Abstimmung mit REW (für den Tontechniker)

1. Preset **Flat** aktivieren (Web-App), Limiter bleiben aktiv.
2. Messmikrofon auf Achse, 1 m, Gerät auf Stativ/Tisch frei aufstellen.
   REW: Sweep 20 Hz–20 kHz, 90 dB Zielpegel, je Messung 3 Mittelungen.
3. Einzelmessungen: nur Sub (FR-Klemmen abziehen), nur FR, dann Summe.
   Am Übergang (~310 Hz) prüfen: Auslöschung? → Sub-Delay in 0.1-ms-Schritten
   variieren (DSP), ggf. Polarität zurückdrehen. Ziel: Summe = Einzelpegel +6 dB.
4. Korrektur-EQ: Raumeinfluss ignorieren (nur > 500 Hz glätten, Fenster
   ~5 ms), Bass anhand des Nahfelds (Mikro 5 cm vor Membran) entzerren.
   Max. +4 dB Anhebung (Headroom!), Senken grosszügig, Peaks schmal.
5. Werte als 10-Band-Gains in die Web-App übertragen (oder feiner: als
   Biquads direkt in `dsp/generate_dsp_program.py` eintragen und Presets
   neu generieren — dann sind sie in allen Presets als Basis drin).
6. Verifikationsmessung, dann Hörtest mit bekanntem Material.
