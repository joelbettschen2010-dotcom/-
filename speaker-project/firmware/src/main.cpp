// SpeakerBox Pro — ESP32-S3 Hauptprogramm.
//
// Boot-Reihenfolge:
//   1. Power-Latch setzen (sonst faellt die Versorgung beim Loslassen ab!)
//   2. I2C starten, ADAU1701 aus Selfboot abwarten, Preset laden
//   3. BT-Modul, LED, OLED, Tasten
//   4. Verstaerker freigeben (zuletzt -> kein Einschaltknacksen)
//   5. WiFi + Web-App

#include <Arduino.h>
#include <Preferences.h>
#include <Wire.h>

#include "btmodule.h"
#include "buttons.h"
#include "config.h"
#include "display.h"
#include "dsp.h"
#include "led.h"
#include "power.h"
#include "webserver.h"

static void onButton(buttons::Button b, bool longPress) {
  switch (b) {
    case buttons::VOL_UP:
      dsp::setVolume(min<uint8_t>(dsp::volume() + 1, VOLUME_STEPS));
      break;
    case buttons::VOL_DOWN:
      dsp::setVolume(dsp::volume() > 0 ? dsp::volume() - 1 : 0);
      break;
    case buttons::BT_PAIR:
      btmodule::startPairing();
      break;
    case buttons::PLAY:
      btmodule::playPause();
      break;
    case buttons::NEXT:
      // kurz = naechster Titel, lang = vorheriger Titel
      longPress ? btmodule::prev() : btmodule::next();
      break;
    default:
      break;
  }
  web::pushStatus();
}

void setup() {
  power::begin();  // ZUERST: Latch halten
  Serial.begin(115200);

  Wire.begin(PIN_I2C_SDA, PIN_I2C_SCL, 400000);

  led::begin();
  led::set(led::BOOT);

  // Gespeicherte Einstellungen
  Preferences prefs;
  prefs.begin("speakerbox", true);
  uint8_t vol = prefs.getUChar("volume", 35);
  uint8_t preset = prefs.getUChar("preset", 1);
  prefs.end();

  if (!dsp::begin()) {
    Serial.println("FEHLER: ADAU1701 antwortet nicht auf I2C!");
  }
  dsp::setVolume(vol);
  dsp::setPreset(preset);

  btmodule::begin();
  buttons::begin();
  buttons::onPress(onButton);
  display::begin();

  power::ampEnable(true);  // zuletzt: Endstufen frei
  web::begin();
  led::set(led::RUNNING);
}

void loop() {
  power::loop();
  buttons::loop();
  btmodule::loop();
  led::loop();
  display::loop();
  web::loop();

  // LED-Zustand ableiten (Prioritaet: Laden > Akku leer > Pairing > BT > Lauf)
  if (power::isCharging())
    led::set(led::CHARGING);
  else if (power::batteryVolts() < VBAT_WARN)
    led::set(led::BATT_LOW);
  else if (btmodule::pairing())
    led::set(led::BT_PAIRING);
  else if (btmodule::connected())
    led::set(led::BT_CONNECTED);
  else
    led::set(led::RUNNING);

  // Lautstaerke/Preset periodisch persistieren (nur bei Aenderung)
  static uint32_t lastSave = 0;
  static uint8_t savedVol = 255, savedPreset = 255;
  if (millis() - lastSave > 5000) {
    lastSave = millis();
    if (savedVol != dsp::volume() || savedPreset != dsp::preset()) {
      Preferences prefs;
      prefs.begin("speakerbox", false);
      prefs.putUChar("volume", dsp::volume());
      prefs.putUChar("preset", dsp::preset());
      prefs.end();
      savedVol = dsp::volume();
      savedPreset = dsp::preset();
    }
  }
  delay(2);
}
