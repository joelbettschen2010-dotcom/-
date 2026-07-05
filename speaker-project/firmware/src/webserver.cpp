#include "webserver.h"

#include <ArduinoJson.h>
#include <ESPAsyncWebServer.h>
#include <LittleFS.h>
#include <Preferences.h>
#include <Update.h>
#include <WiFi.h>

#include "btmodule.h"
#include "config.h"
#include "dsp.h"
#include "power.h"

namespace web {

static AsyncWebServer s_server(80);
static AsyncWebSocket s_ws("/ws");
static Preferences s_prefs;

// Akku-Verlauf: alle 5 min ein Punkt, 288 Punkte = 24 h (Ringpuffer im NVS)
static const int HIST_LEN = 288;
static uint8_t s_hist[HIST_LEN];
static uint16_t s_histPos = 0;

// ---------------------------------------------------------------------------
// Status-JSON (fuer REST und WebSocket identisch)
// ---------------------------------------------------------------------------
static void statusJson(JsonDocument& doc) {
  doc["volume"] = dsp::volume() * 2;  // intern 0..50, API 0..100
  doc["preset"] = dsp::preset();
  doc["subLevel"] = dsp::subLevel();
  doc["battery"]["soc"] = power::batterySoc();
  doc["battery"]["volts"] = serialized(String(power::batteryVolts(), 2));
  doc["battery"]["charging"] = power::isCharging();
  doc["bt"]["connected"] = btmodule::connected();
  doc["bt"]["pairing"] = btmodule::pairing();
  doc["ampFault"] = power::ampFault();
  JsonArray eq = doc["eq"].to<JsonArray>();
  for (uint8_t b = 0; b < 10; b++) eq.add(dsp::eqBand(0, b));
}

void pushStatus() {
  if (s_ws.count() == 0) return;
  JsonDocument doc;
  statusJson(doc);
  String out;
  serializeJson(doc, out);
  s_ws.textAll(out);
}

// ---------------------------------------------------------------------------
// WiFi: erst gespeicherte Netzwerke versuchen, sonst Access Point
// ---------------------------------------------------------------------------
static void startWifi() {
  s_prefs.begin("speakerbox", false);
  String ssid = s_prefs.getString("ssid", "");
  String pass = s_prefs.getString("pass", "");

  if (ssid.length()) {
    WiFi.mode(WIFI_STA);
    WiFi.setHostname("speakerbox-pro");
    WiFi.begin(ssid.c_str(), pass.c_str());
    for (int i = 0; i < 30 && WiFi.status() != WL_CONNECTED; i++) delay(500);
  }
  if (WiFi.status() != WL_CONNECTED) {
    // Fallback: eigener Access Point (offen erreichbar unter 192.168.4.1)
    WiFi.mode(WIFI_AP);
    WiFi.softAP(WIFI_AP_SSID);
  }
}

// ---------------------------------------------------------------------------
// REST-API
// ---------------------------------------------------------------------------
static void apiRoutes() {
  s_server.on("/api/status", HTTP_GET, [](AsyncWebServerRequest* req) {
    JsonDocument doc;
    statusJson(doc);
    String out;
    serializeJson(doc, out);
    req->send(200, "application/json", out);
  });

  s_server.on("/api/info", HTTP_GET, [](AsyncWebServerRequest* req) {
    JsonDocument doc;
    doc["firmware"] = FW_VERSION;
    doc["uptime"] = millis() / 1000;
    doc["mac"] = WiFi.macAddress();
    doc["ip"] = WiFi.getMode() == WIFI_AP ? WiFi.softAPIP().toString()
                                          : WiFi.localIP().toString();
    doc["heap"] = ESP.getFreeHeap();
    JsonArray h = doc["batteryHistory"].to<JsonArray>();
    for (int i = 0; i < HIST_LEN; i++)
      h.add(s_hist[(s_histPos + i) % HIST_LEN]);
    String out;
    serializeJson(doc, out);
    req->send(200, "application/json", out);
  });

  // POST-Bodies als JSON verarbeiten (onBody sammelt, Handler parst)
  auto jsonPost = [](const char* uri,
                     std::function<int(JsonDocument&)> handler) {
    s_server.on(uri, HTTP_POST,
        [](AsyncWebServerRequest* req) {},  // Antwort kommt aus onBody
        nullptr,
        [handler](AsyncWebServerRequest* req, uint8_t* data, size_t len,
                  size_t index, size_t total) {
          if (index + len != total) return;  // auf letzten Chunk warten
          JsonDocument doc;
          if (deserializeJson(doc, data, total)) {
            req->send(400, "application/json", "{\"error\":\"bad json\"}");
            return;
          }
          int code = handler(doc);
          req->send(code, "application/json",
                    code == 200 ? "{\"ok\":true}" : "{\"error\":true}");
          pushStatus();
        });
  };

  jsonPost("/api/volume", [](JsonDocument& d) {
    if (!d["value"].is<int>()) return 400;
    dsp::setVolume(constrain(d["value"].as<int>(), 0, 100) / 2);
    return 200;
  });

  jsonPost("/api/preset", [](JsonDocument& d) {
    const char* names[3] = {"flat", "music", "outdoor"};
    String p = d["value"].as<String>();
    for (uint8_t i = 0; i < 3; i++)
      if (p == names[i]) { dsp::setPreset(i); return 200; }
    return 400;
  });

  jsonPost("/api/eq", [](JsonDocument& d) {
    // {"bands":[10 x dB], "channel":"both"|"l"|"r"|"sub", "subLevel":dB}
    JsonArray bands = d["bands"].as<JsonArray>();
    if (bands.size() == 10) {
      String ch = d["channel"] | "both";
      for (uint8_t b = 0; b < 10; b++) {
        float g = bands[b].as<float>();
        if (ch == "both" || ch == "l") dsp::setEqBand(0, b, g);
        if (ch == "both" || ch == "r") dsp::setEqBand(1, b, g);
        if (ch == "sub") dsp::setEqBand(2, b, g);
      }
    }
    if (d["subLevel"].is<float>()) dsp::setSubLevel(d["subLevel"]);
    return 200;
  });

  jsonPost("/api/bt/pair", [](JsonDocument&) {
    btmodule::startPairing();
    return 200;
  });

  jsonPost("/api/media", [](JsonDocument& d) {
    String cmd = d["value"].as<String>();
    if (cmd == "play") btmodule::playPause();
    else if (cmd == "next") btmodule::next();
    else if (cmd == "prev") btmodule::prev();
    else return 400;
    return 200;
  });

  s_server.on("/api/bt/devices", HTTP_GET, [](AsyncWebServerRequest* req) {
    // Das BTM875-Modul verwaltet Paired-Devices intern; ohne erweiterte
    // UART-Firmware ist nur der aktuelle Status auslesbar.
    JsonDocument doc;
    doc["connected"] = btmodule::connected();
    String out;
    serializeJson(doc, out);
    req->send(200, "application/json", out);
  });

  jsonPost("/api/power/off", [](JsonDocument&) {
    // Antwort geht noch raus, dann herunterfahren
    delay(200);
    power::shutdown();
    return 200;
  });

  jsonPost("/api/wifi", [](JsonDocument& d) {
    s_prefs.putString("ssid", d["ssid"].as<String>());
    s_prefs.putString("pass", d["pass"].as<String>());
    return 200;
  });

  jsonPost("/api/name", [](JsonDocument& d) {
    String n = d["value"].as<String>();
    if (!n.length()) return 400;
    btmodule::setName(n.c_str());
    s_prefs.putString("name", n);
    return 200;
  });

  jsonPost("/api/factory-reset", [](JsonDocument&) {
    s_prefs.clear();
    return 200;
  });

  // OTA: Firmware-Datei per POST auf /api/ota
  s_server.on("/api/ota", HTTP_POST,
      [](AsyncWebServerRequest* req) {
        bool ok = !Update.hasError();
        req->send(ok ? 200 : 500, "application/json",
                  ok ? "{\"ok\":true}" : "{\"error\":true}");
        if (ok) { delay(500); ESP.restart(); }
      },
      [](AsyncWebServerRequest* req, String filename, size_t index,
         uint8_t* data, size_t len, bool final) {
        if (index == 0) Update.begin(UPDATE_SIZE_UNKNOWN);
        if (len) Update.write(data, len);
        if (final) Update.end(true);
      });
}

// ---------------------------------------------------------------------------
void begin() {
  startWifi();
  LittleFS.begin(true);

  memset(s_hist, 0, sizeof(s_hist));

  s_ws.onEvent([](AsyncWebSocket*, AsyncWebSocketClient* client,
                  AwsEventType type, void*, uint8_t*, size_t) {
    if (type == WS_EVT_CONNECT) pushStatus();
  });
  s_server.addHandler(&s_ws);

  apiRoutes();

  // PWA aus LittleFS; SPA-Fallback auf index.html
  s_server.serveStatic("/", LittleFS, "/www/").setDefaultFile("index.html");
  s_server.onNotFound([](AsyncWebServerRequest* req) {
    if (req->method() == HTTP_GET)
      req->send(LittleFS, "/www/index.html", "text/html");
    else
      req->send(404);
  });

  s_server.begin();
}

void loop() {
  s_ws.cleanupClients();

  // Akku-Verlauf: alle 5 Minuten ein Punkt
  static uint32_t lastHist = 0;
  if (millis() - lastHist > 300000UL) {
    lastHist = millis();
    s_hist[s_histPos] = power::batterySoc();
    s_histPos = (s_histPos + 1) % HIST_LEN;
  }

  // Status-Push (Akku/BT aendern sich ohne API-Aufrufe)
  static uint32_t lastPush = 0;
  if (millis() - lastPush > 2000) {
    lastPush = millis();
    pushStatus();
  }
}

}  // namespace web
