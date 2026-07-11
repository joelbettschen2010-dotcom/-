# Debugging-Guide — SpeakerBox Pro

## Kein Ton

Checkliste in dieser Reihenfolge:

1. **Versorgung:** PVDD an U6/U7 Pin 18/19/31/32 = Akkuspannung?
   Wenn nein: Q2-Gate messen (muss ~12 V unter PVDD liegen, sonst
   Latch-Problem → PWR_HOLD-GPIO prüfen).
2. **Amp aktiv?** SDZ (Pin 2) muss HIGH sein, MUTE (Pin 12) LOW.
   Firmware setzt beides erst NACH erfolgreicher DSP-Initialisierung —
   serielle Konsole prüfen: „ADAU1701 antwortet nicht" → I2C-Problem.
3. **FAULTZ** (Pin 3) LOW = Schutzabschaltung: Kurzschluss am Ausgang,
   DC am Ausgang oder Übertemperatur. Lautsprecherklemmen abziehen,
   neu starten.
4. **DSP bootet?** DVDD = 1.8 V? MCLK am Pin 32 (12.288 MHz, Oszi)?
   SELFBOOT = 3.3 V? EEPROM programmiert? Ohne gültiges EEPROM bleibt
   der DSP nach Reset stumm — Default-Programm gibt ADC direkt auf DAC
   erst NACH Initialisierung der Core-Register durch die Firmware.
5. **I2C-Kommunikation:** mit Logic-Analyzer/Oszi auf SDA/SCL: ACK vom
   0x68/0x69 (write/read)? Pullups 2.2 kΩ bestückt? Der ADAU1701 zieht
   den Bus während des Selfboots selbst — Firmware wartet 300 ms.
6. **Signalkette rückwärts:** DAC-Ausgang (VOUT0, Pin 46) mit Oszi:
   Signal da? → Koppelkette C41→AMPIN prüfen. Kein Signal? → ADC-Eingang
   (2 V rms max!) und DSP-Programm (Volume ≠ 0? Preset geladen?).

## Verzerrung

* **Bei hoher Lautstärke, alle Kanäle:** Akku unter Last eingebrochen
  (< 13 V)? → normal, Limiter-Schwellen greifen bei Nominalspannung.
  Web-App-Volume < 100 % testen: verschwindet die Verzerrung bei −3 dB,
  clippt der Amp → Limiter-Schwelle in `dsp/generate_dsp_program.py` senken.
* **Nur Sub:** Hub-Anschlag (dumpfes Klacken)? Sub-Level reduzieren oder
  Kammer auf Undichtigkeit prüfen (undichte Kammer = kein Luftpolster =
  Überhub).
* **Nur eine Seite:** Koppel-C / kalte Lötstelle an dem Kanal; L/R am
  DSP-Ausgang tauschen um DSP vs. Amp einzugrenzen.
* **Kratzen bei bestimmten Frequenzen:** Gehäuse-Vibration (Schraube lose,
  Kabel schlägt an Membran) — nicht elektrisch.

## Brumm / Einstreuung

* **50-Hz-Brumm nur mit Aux-Kabel:** klassische Masseschleife
  Quelle↔Box. Test: Quelle auf Akku betreiben. Abhilfe: Aux-Kabel mit
  Massetrennung/DI, oder BT nutzen.
* **Zischen/Pfeifen abhängig von WiFi-Aktivität:** Einstreuung digital →
  analog. AGND-Insel-Bestückung prüfen (FB1 bestückt? NT1 = einziger
  Masseschluss?), Aux-Leitungen kurz halten, ggf. 100 pF an ADC-Pins
  (C36/C37) auf 220 pF erhöhen.
* **Rauschen am Fullrange bei Stille:** normal beim TPA3118 gering;
  wenn deutlich: GVDD-Kondensator (C48) und Gain-Widerstand prüfen
  (falscher Wert = 36 dB Gain statt 20 dB = 6× Grundrauschen).

## Bluetooth koppelt nicht

1. LED blinkt nicht blau nach „Koppeln"? → Firmware sendet AT-Kommando +
   PAIR-Pin-Puls; Modul-Doku prüfen, Kommandotabelle in
   `firmware/src/btmodule.cpp` anpassen (Modulfamilien nutzen verschiedene
   AT-Dialekte).
2. Modul bootet? 3.3 V am BT-Header Pin 1, EN (Pin 10) HIGH.
3. UART-Echo testen: `pio device monitor`, im Code `loop()` das Echo
   aktivieren — antwortet das Modul auf `AT\r\n` mit `OK`?
4. Gerät erscheint, verbindet aber nicht: gespeicherte Kopplung am Handy
   löschen; Modul-Werksreset (meist PAIR-Taste 10 s).
5. Audio läuft, aber Status-LED bleibt grün: STATUS-Pin-Polarität des
   Moduls prüfen (manche melden LOW = verbunden) → `connected()` anpassen.

## Web-App nicht erreichbar

1. **AP-Modus:** Nach Werksreset/ohne WLAN spannt die Box „SpeakerBox-Pro"
   auf → verbinden, `http://192.168.4.1`. Erscheint das Netz nicht:
   serielle Konsole — bootet der ESP32 überhaupt (Brownout bei schwachem
   Akku)?
2. **Client-Modus:** IP im Router suchen (Hostname `speakerbox-pro`) oder
   OLED-Info-Seite. mDNS ist nicht implementiert — feste IP im Router
   vergeben hilft.
3. App lädt, aber keine Live-Daten: WebSocket blockiert? Seite über
   `http://` (nicht https) öffnen — der ESP32 kann kein TLS.
4. „Firmware-Update schlägt fehl": .bin aus `pio run` verwenden
   (`.pio/build/speakerbox/firmware.bin`), nicht das ELF; bei < 50 %
   Abbruch USB-Kabel verwenden.

## Diagnose-Grundausstattung

Multimeter, USB-Seriell-Konsole (`pio device monitor`), günstiges
Oszilloskop/Scope-Meter für MCLK+Audio, Logic-Analyzer (8 ch, 10 CHF)
für I2C. Bei Amp-Problemen: 4-Ω/20-W-Lastwiderstand statt Treiber.
