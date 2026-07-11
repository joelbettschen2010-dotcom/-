// ADAU1701-Ansteuerung: Safeload-Writes, 5.23-Konvertierung, Biquads, Presets.
#pragma once
#include <Arduino.h>

namespace dsp {

// Initialisierung: Reset-Sequenz, warten bis Selfboot fertig, Preset laden.
bool begin();

// Lautstaerke 0..50 (logarithmische Tabelle aus dsp_coefficients.h),
// klickfrei ueber Slew-Volume-Zelle.
void setVolume(uint8_t step);
uint8_t volume();

// Preset 0=flat, 1=music, 2=outdoor. Mit Fade-Out/Fade-In ohne Knacksen.
void setPreset(uint8_t preset);
uint8_t preset();

// 10-Band-EQ direkt setzen (gains je Band in dB, -12..+12), ch: 0=L,1=R,2=Sub.
void setEqBand(uint8_t ch, uint8_t band, float gainDb);
float eqBand(uint8_t ch, uint8_t band);

// Sub-Level -12..+12 dB relativ.
void setSubLevel(float db);
float subLevel();

// Rohzugriff (fuer Debug/Erweiterung)
bool writeParam(uint16_t addr, float value);          // Safeload, 5.23
bool writeRegister(uint16_t addr, const uint8_t* data, uint8_t len);

}  // namespace dsp
