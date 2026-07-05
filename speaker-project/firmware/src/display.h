// OLED 0.96" (SSD1306, I2C) via U8g2: Lautstaerke, BT, Akku, Preset.
#pragma once
#include <Arduino.h>

namespace display {
void begin();
void loop();  // zeichnet ~4x pro Sekunde neu
}
