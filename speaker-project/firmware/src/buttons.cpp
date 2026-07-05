// Widerstandsleiter: 10k-Pullup auf 3V3 (Main-Board), Taster ziehen ueber
// 0R / 2k2 / 4k7 / 10k / 22k nach GND.
// Erwartete Spannungen: 0.00 / 0.59 / 1.06 / 1.65 / 2.27 V, offen 3.3 V.

#include "buttons.h"

#include "config.h"

namespace buttons {

static void (*s_cb)(Button, bool) = nullptr;
static Button s_current = NONE;
static Button s_stable = NONE;
static uint32_t s_changeAt = 0;
static uint32_t s_downAt = 0;
static bool s_longFired = false;

static const uint16_t DEBOUNCE_MS = 30;
static const uint16_t LONGPRESS_MS = 600;

void begin() {
  analogSetPinAttenuation(PIN_BTN_ADC, ADC_11db);
}

void onPress(void (*cb)(Button, bool)) { s_cb = cb; }

static Button decode(uint16_t mv) {
  // Schwellen jeweils mittig zwischen den Leiterspannungen
  if (mv < 300) return VOL_UP;     // ~0 V
  if (mv < 830) return VOL_DOWN;   // ~590 mV
  if (mv < 1360) return BT_PAIR;   // ~1060 mV
  if (mv < 1960) return PLAY;      // ~1650 mV
  if (mv < 2800) return NEXT;      // ~2270 mV
  return NONE;                     // offen ~3.3 V
}

void loop() {
  Button raw = decode(analogReadMilliVolts(PIN_BTN_ADC));

  if (raw != s_current) {          // Rohzustand geaendert -> Entprellfenster neu
    s_current = raw;
    s_changeAt = millis();
    return;
  }
  if (millis() - s_changeAt < DEBOUNCE_MS) return;

  if (raw != s_stable) {           // entprellter Zustandswechsel
    if (raw != NONE) {             // Taste gedrueckt
      s_downAt = millis();
      s_longFired = false;
    } else if (s_stable != NONE && !s_longFired && s_cb) {
      s_cb(s_stable, false);       // kurzer Druck beim Loslassen
    }
    s_stable = raw;
  } else if (raw != NONE && !s_longFired &&
             millis() - s_downAt > LONGPRESS_MS) {
    s_longFired = true;            // langer Druck einmalig beim Halten
    if (s_cb) s_cb(raw, true);
  }
}

}  // namespace buttons
