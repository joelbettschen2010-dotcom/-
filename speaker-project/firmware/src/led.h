// RGB-Status-LED (WS2812B) — Zustandsmaschine laut Spezifikation:
//   Blau blinkend  = BT-Pairing aktiv
//   Blau dauerhaft = BT verbunden
//   Gruen          = Betrieb (keine BT-Verbindung)
//   Rot blinkend   = Akku fast leer
//   Rot dauerhaft  = Laedt
#pragma once
#include <Arduino.h>

namespace led {

enum State : uint8_t { BOOT, RUNNING, BT_PAIRING, BT_CONNECTED, BATT_LOW, CHARGING };

void begin();
void set(State s);
void loop();  // Blink-Timing

}  // namespace led
