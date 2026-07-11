// Tasten der Widerstandsleiter (Vol+/Vol-/BT/Play/Next) mit Entprellung.
#pragma once
#include <Arduino.h>

namespace buttons {

enum Button : uint8_t { NONE = 0, VOL_UP, VOL_DOWN, BT_PAIR, PLAY, NEXT };

void begin();
void loop();

// Callback bei kurzem Druck; longpress fuer NEXT = PREV (Doppelbelegung).
void onPress(void (*cb)(Button b, bool longPress));

}  // namespace buttons
