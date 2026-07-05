// WiFi-Verwaltung (AP-Fallback + Client mit NVS-Netzwerken), REST-API,
// WebSocket-Push und OTA-Update. Serviert die PWA aus LittleFS.
#pragma once
#include <Arduino.h>

namespace web {
void begin();
void loop();
void pushStatus();  // Status an alle WebSocket-Clients senden
}
