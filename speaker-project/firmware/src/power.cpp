#include "power.h"

#include "config.h"
#include "dsp.h"

namespace power {

static uint32_t s_btnDownSince = 0;
static float s_vbatFilt = 0;

void begin() {
  // ZUERST den Latch setzen — der Nutzer haelt den Taster noch gedrueckt,
  // sobald er loslaesst muss der NPN die P-FET-Gate-Leitung halten.
  pinMode(PIN_PWR_HOLD, OUTPUT);
  digitalWrite(PIN_PWR_HOLD, HIGH);

  pinMode(PIN_BTN_PWR, INPUT);        // externer 10k-Pullup
  pinMode(PIN_CHG_STAT, INPUT);       // open-drain vom Lade-Board
  pinMode(PIN_FULL_STAT, INPUT);
  pinMode(PIN_AMP_SDZ, OUTPUT);
  pinMode(PIN_AMP_MUTE, OUTPUT);
  pinMode(PIN_AMP_FAULT, INPUT);      // 10k-Pullup extern
  digitalWrite(PIN_AMP_SDZ, LOW);     // Verstaerker aus bis DSP bereit
  digitalWrite(PIN_AMP_MUTE, HIGH);   // stumm

  analogReadResolution(12);
  analogSetPinAttenuation(PIN_VBAT_SENSE, ADC_11db);  // bis ~3.1 V
  s_vbatFilt = batteryVolts();

  // Warten bis der Einschalt-Tastendruck losgelassen wurde, sonst wuerde
  // loop() ihn sofort als Ausschalt-Druck werten.
  uint32_t t0 = millis();
  while (digitalRead(PIN_BTN_PWR) == LOW && millis() - t0 < 5000) delay(10);
}

void loop() {
  // Ausschalten: Taster 2 s halten
  if (digitalRead(PIN_BTN_PWR) == LOW) {
    if (s_btnDownSince == 0) s_btnDownSince = millis();
    if (millis() - s_btnDownSince > PWR_HOLD_MS) shutdown();
  } else {
    s_btnDownSince = 0;
  }

  // Akkuspannung tiefpassfiltern (Amp-Last moduliert die Spannung stark)
  float v = (float)analogReadMilliVolts(PIN_VBAT_SENSE) / 1000.0f * VBAT_DIVIDER;
  s_vbatFilt = s_vbatFilt * 0.98f + v * 0.02f;

  // Tiefentladeschutz: unter 12.0 V hart ausschalten
  if (s_vbatFilt < VBAT_EMPTY && !isCharging()) shutdown();
}

void shutdown() {
  ampEnable(false);
  dsp::setVolume(0);
  delay(100);                       // Fade ausklingen lassen
  digitalWrite(PIN_PWR_HOLD, LOW);  // Latch loesen -> P-FET oeffnet
  delay(1000);
  // Falls wir noch laufen (USB-Versorgung!): Deep-Sleep als Fallback
  esp_deep_sleep_start();
}

float batteryVolts() {
  if (s_vbatFilt > 1.0f) return s_vbatFilt;
  return (float)analogReadMilliVolts(PIN_VBAT_SENSE) / 1000.0f * VBAT_DIVIDER;
}

uint8_t batterySoc() {
  // OCV-Kennlinie Li-Ion (4S, unter leichter Last). Stuetzpunkte je Zelle:
  // 4.20=100, 4.00=85, 3.85=65, 3.70=40, 3.55=20, 3.40=10, 3.00=0
  static const float vt[][2] = {{16.8, 100}, {16.0, 85}, {15.4, 65},
                                {14.8, 40},  {14.2, 20}, {13.6, 10},
                                {12.0, 0}};
  float v = batteryVolts();
  if (v >= vt[0][0]) return 100;
  for (int i = 1; i < 7; i++) {
    if (v >= vt[i][0]) {
      float f = (v - vt[i][0]) / (vt[i - 1][0] - vt[i][0]);
      return (uint8_t)(vt[i][1] + f * (vt[i - 1][1] - vt[i][1]));
    }
  }
  return 0;
}

bool isCharging() { return digitalRead(PIN_CHG_STAT) == LOW; }
bool isFull() { return digitalRead(PIN_FULL_STAT) == LOW; }

void ampEnable(bool on) {
  if (on) {
    digitalWrite(PIN_AMP_SDZ, HIGH);
    delay(50);                        // TPA3118-Startup abwarten
    digitalWrite(PIN_AMP_MUTE, LOW);  // unmute
  } else {
    digitalWrite(PIN_AMP_MUTE, HIGH);
    delay(20);
    digitalWrite(PIN_AMP_SDZ, LOW);
  }
}

bool ampFault() { return digitalRead(PIN_AMP_FAULT) == LOW; }

}  // namespace power
