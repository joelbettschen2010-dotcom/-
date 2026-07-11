#include "display.h"

#include <U8g2lib.h>

#include "btmodule.h"
#include "config.h"
#include "dsp.h"
#include "power.h"

namespace display {

// Full-Buffer-Variante: 1 kB RAM, dafuer flackerfrei.
static U8G2_SSD1306_128X64_NONAME_F_HW_I2C u8g2(U8G2_R0);
static bool s_present = false;

void begin() {
  s_present = u8g2.begin();  // false, wenn kein OLED gesteckt ist
}

void loop() {
  static uint32_t last = 0;
  if (!s_present || millis() - last < 250) return;
  last = millis();

  static const char* presets[3] = {"Flat", "Musik", "Outdoor"};
  u8g2.clearBuffer();

  // Kopfzeile: BT-Status + Akku
  u8g2.setFont(u8g2_font_6x12_tf);
  u8g2.drawStr(0, 10, btmodule::connected() ? "BT verbunden"
                      : btmodule::pairing() ? "BT Pairing..."
                                            : "BT getrennt");
  char buf[24];
  snprintf(buf, sizeof(buf), "%d%%", power::batterySoc());
  u8g2.drawStr(104, 10, buf);
  u8g2.drawFrame(0, 14, 128, 8);  // Akkubalken
  u8g2.drawBox(1, 15, (uint16_t)(126 * power::batterySoc() / 100), 6);

  // Lautstaerke gross
  u8g2.setFont(u8g2_font_logisoso24_tn);
  snprintf(buf, sizeof(buf), "%d", dsp::volume());
  u8g2.drawStr(46, 52, buf);
  u8g2.setFont(u8g2_font_6x12_tf);
  u8g2.drawStr(0, 46, "Vol");

  // Fusszeile: Preset
  snprintf(buf, sizeof(buf), "EQ: %s", presets[dsp::preset()]);
  u8g2.drawStr(0, 63, buf);

  u8g2.sendBuffer();
}

}  // namespace display
