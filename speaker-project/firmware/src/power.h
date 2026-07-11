// Soft-Power-Verwaltung + Akku-Messung + Verstaerker-Steuerung.
#pragma once
#include <Arduino.h>

namespace power {

void begin();          // Latch setzen (haelt die Versorgung), ADC konfigurieren
void loop();           // Power-Taster ueberwachen (2 s halten = aus)
void shutdown();       // geordnet ausschalten (Amp stumm, Latch loesen)

float batteryVolts();  // gefilterte Akkuspannung [V]
uint8_t batterySoc();  // Ladezustand 0..100 % (OCV-Kennlinie 4S)
bool isCharging();
bool isFull();

void ampEnable(bool on);   // SDZ + MUTE ansteuern
bool ampFault();           // FAULTZ vom TPA3118

}  // namespace power
