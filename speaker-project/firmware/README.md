# Firmware — ESP32-S3 (PlatformIO)

Kompiliert mit `pio run` (getestet: SUCCESS, 898 kB / 28.5 % Flash).

## Aufbau

| Modul | Aufgabe |
|---|---|
| `src/main.cpp` | Boot-Reihenfolge (Latch → DSP → BT → Amp → WiFi), Hauptschleife, LED-Priorisierung, NVS-Persistenz |
| `src/dsp.cpp` | ADAU1701: Safeload-Writes (klickfrei), 5.23-Konvertierung, RBJ-Biquads, Presets mit Fade, Log-Volume |
| `src/power.cpp` | Soft-Power-Latch, 2-s-Ausschalten, 4S-OCV-Ladezustand, Tiefentladeschutz, Amp-Enable/Fault |
| `src/buttons.cpp` | Widerstandsleiter-Dekodierung (5 Tasten auf 1 ADC), Entprellung, Long-Press |
| `src/led.cpp` | WS2812B-Zustandsmaschine (Pairing/Verbunden/Betrieb/Akku/Laden) |
| `src/display.cpp` | SSD1306-OLED via U8g2 (Volume, BT, Akku, Preset) |
| `src/btmodule.cpp` | BT-Modul: EN/STATUS/PAIR-Pins + AT-UART (Kommandotabelle zentral anpassbar) |
| `src/webserver.cpp` | WiFi (STA + AP-Fallback), REST-API, WebSocket-Push, OTA, LittleFS-PWA |
| `include/dsp_coefficients.h` | GENERIERT von `dsp/generate_dsp_program.py` — nicht von Hand editieren |

## Flashen

```bash
pio run -t upload          # Firmware via USB-C
pio run -t uploadfs        # Web-App (data/www -> LittleFS)
```

`data/www/` wird aus dem Web-App-Build befuellt:
`cd ../webapp && npm run build && cp -r out/* ../firmware/data/www/`

## REST-API (Auszug)

GET /api/status · GET /api/info · POST /api/volume {value:0-100} ·
POST /api/preset {value:"flat|music|outdoor"} · POST /api/eq {bands:[10],subLevel} ·
POST /api/bt/pair · POST /api/media {value:"play|next|prev"} ·
POST /api/wifi {ssid,pass} · POST /api/name {value} · POST /api/ota (Datei) ·
POST /api/power/off · POST /api/factory-reset
WebSocket /ws pusht den Status alle 2 s und nach jeder Aenderung.
