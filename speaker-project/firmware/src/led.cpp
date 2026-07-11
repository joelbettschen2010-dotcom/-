#include "led.h"

#include <Adafruit_NeoPixel.h>

#include "config.h"

namespace led {

static Adafruit_NeoPixel s_px(1, PIN_LED_DATA, NEO_GRB + NEO_KHZ800);
static State s_state = BOOT;

void begin() {
  s_px.begin();
  s_px.setBrightness(60);
  s_px.setPixelColor(0, s_px.Color(255, 120, 0));  // orange = booting
  s_px.show();
}

void set(State s) { s_state = s; }

void loop() {
  static uint32_t last = 0;
  if (millis() - last < 100) return;
  last = millis();
  bool blinkOn = (millis() / 400) % 2;

  uint32_t c = 0;
  switch (s_state) {
    case BOOT:        c = s_px.Color(255, 120, 0); break;
    case RUNNING:     c = s_px.Color(0, 180, 0); break;
    case BT_PAIRING:  c = blinkOn ? s_px.Color(0, 60, 255) : 0; break;
    case BT_CONNECTED:c = s_px.Color(0, 60, 255); break;
    case BATT_LOW:    c = blinkOn ? s_px.Color(255, 0, 0) : 0; break;
    case CHARGING:    c = s_px.Color(255, 0, 0); break;
  }
  s_px.setPixelColor(0, c);
  s_px.show();
}

}  // namespace led
