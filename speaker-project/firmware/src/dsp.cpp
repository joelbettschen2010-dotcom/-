// ADAU1701-Treiber.
//
// Registerkarte (ADAU1701-Datenblatt "RAMs and Registers"):
//   0x0000-0x03FF  Parameter-RAM (1024 x 32 bit, Werte im 5.23-Format)
//   0x0810-0x0814  Safeload-Data-Register  (5 Stueck, 40 bit)
//   0x0815-0x0819  Safeload-Address-Register
//   0x081C (2076)  DSP-Core-Control (IST-Bit 5 loest Safeload-Transfer aus)
//   0x0827 (2087)  DAC-Setup
//
// Safeload: Wert + Zieladresse in die Safeload-Register schreiben, dann
// IST-Bit setzen -> der Core uebernimmt den Wert synchron zum Sampletakt
// (klickfrei). Genau so laedt auch SigmaStudio Parameter zur Laufzeit.
//
// Die Parameter-ADRESSEN kommen aus include/dsp_coefficients.h, das vom
// DSP-Generator (dsp/generate_dsp_program.py) erzeugt wird.

#include "dsp.h"

#include <Wire.h>
#include <math.h>

#include "config.h"
#include "dsp_coefficients.h"

namespace dsp {

static const uint16_t REG_CORE_CTRL = 0x081C;
static const uint16_t REG_SAFE_DATA = 0x0810;
static const uint16_t REG_SAFE_ADDR = 0x0815;

static uint8_t s_volume = 35;      // Startlautstaerke (Stufe von 50)
static uint8_t s_preset = 1;       // Musik
static float s_eq[3][10] = {{0}};  // aktuelle EQ-Gains [dB]
static float s_subLevel = 0.0f;

// ---------------------------------------------------------------------------
// I2C-Grundfunktionen. Der ADAU1701 adressiert Register mit 2 Byte-Adresse.
// ---------------------------------------------------------------------------
bool writeRegister(uint16_t addr, const uint8_t* data, uint8_t len) {
  Wire.beginTransmission(I2C_ADDR_DSP);
  Wire.write(addr >> 8);
  Wire.write(addr & 0xFF);
  Wire.write(data, len);
  return Wire.endTransmission() == 0;
}

// Float -> 5.23-Festkomma (28 bit, als 4 Bytes big-endian)
static void to523(float v, uint8_t out[4]) {
  if (v > 15.9999f) v = 15.9999f;
  if (v < -16.0f) v = -16.0f;
  int32_t fix = (int32_t)lroundf(v * 8388608.0f);  // 2^23
  uint32_t u = ((uint32_t)fix) & 0x0FFFFFFF;
  out[0] = (u >> 24) & 0x0F;
  out[1] = (u >> 16) & 0xFF;
  out[2] = (u >> 8) & 0xFF;
  out[3] = u & 0xFF;
}

// Ein Parameter klickfrei per Safeload schreiben.
bool writeParam(uint16_t paramAddr, float value) {
  // Safeload-Data 0: 5 Bytes, erstes Byte 0 (Padding auf 40 bit)
  uint8_t data[5] = {0};
  to523(value, data + 1);
  if (!writeRegister(REG_SAFE_DATA, data, 5)) return false;
  uint8_t addr[2] = {(uint8_t)(paramAddr >> 8), (uint8_t)(paramAddr & 0xFF)};
  if (!writeRegister(REG_SAFE_ADDR, addr, 2)) return false;
  // Core-Control lesen-modifizieren geht nicht (write-only sinnvoll) —
  // Standardwert mit gesetztem IST-Bit (Bit 5) schreiben, ADM/DAM/CR = 1.
  uint8_t core[2] = {0x00, 0x3C};  // IST | ADM | DAM | CR
  return writeRegister(REG_CORE_CTRL, core, 2);
}

// Mehrere aufeinanderfolgende Parameter (z. B. 5 Biquad-Koeffizienten):
// nacheinander safeloaden. Fuer einen Biquad ist das ok — der ADAU1701
// hat 5 Safeload-Slots, wir nutzen der Einfachheit halber Slot 0 seriell.
static bool writeParams(uint16_t startAddr, const float* v, uint8_t n) {
  for (uint8_t i = 0; i < n; i++) {
    if (!writeParam(startAddr + i, v[i])) return false;
    delayMicroseconds(50);  // > 1 Sample-Periode @48 kHz
  }
  return true;
}

// ---------------------------------------------------------------------------
// Biquad-Berechnung (RBJ-Cookbook, identisch zum Python-Generator).
// Ausgabe in SigmaStudio-Reihenfolge b0,b1,b2,-a1,-a2 (a0-normiert).
// ---------------------------------------------------------------------------
static void peakingBiquad(float f0, float q, float gainDb, float out[5]) {
  if (fabsf(gainDb) < 0.01f) {  // neutral
    out[0] = 1; out[1] = 0; out[2] = 0; out[3] = 0; out[4] = 0;
    return;
  }
  float a = powf(10.0f, gainDb / 40.0f);
  float w0 = 2.0f * PI * f0 / DSP_FS;
  float alpha = sinf(w0) / (2.0f * q);
  float cw = cosf(w0);
  float a0 = 1 + alpha / a;
  out[0] = (1 + alpha * a) / a0;
  out[1] = (-2 * cw) / a0;
  out[2] = (1 - alpha * a) / a0;
  out[3] = (2 * cw) / a0;          // = -a1/a0
  out[4] = -(1 - alpha / a) / a0;  // = -a2/a0
}

// Parameter-Adresse des Biquads: Kanal ch (0=L,1=R,2=Sub), Band b.
// Layout laut Generator: PEQ_L_B0 beginnt bei DSP_ADDR_PEQ_L_B0_0 und
// jedes Band belegt 5 aufeinanderfolgende Woerter.
static uint16_t eqAddr(uint8_t ch, uint8_t band) {
  static const uint16_t base[3] = {DSP_ADDR_PEQ_L_B0_0, DSP_ADDR_PEQ_R_B0_0,
                                   DSP_ADDR_PEQ_SUB_B0_0};
  return base[ch] + band * 5;
}

// ---------------------------------------------------------------------------
// API
// ---------------------------------------------------------------------------
bool begin() {
  pinMode(PIN_DSP_NRST, OUTPUT);
  digitalWrite(PIN_DSP_NRST, LOW);
  delay(10);
  digitalWrite(PIN_DSP_NRST, HIGH);
  // Selfboot: PLL-Start + EEPROM-Boot dauert ~200 ms (Datenblatt Table 11).
  // Solange NICHT auf den Bus schreiben.
  delay(300);
  // Verbindungstest: Core-Control schreiben
  uint8_t core[2] = {0x00, 0x1C};  // ADM | DAM | CR
  bool ok = writeRegister(REG_CORE_CTRL, core, 2);
  if (ok) {
    setVolume(s_volume);
    setPreset(s_preset);
  }
  return ok;
}

void setVolume(uint8_t step) {
  if (step > VOLUME_STEPS) step = VOLUME_STEPS;
  s_volume = step;
  // Slew-Volume-Zelle rampt selbst -> einfacher Parameter-Write reicht.
  writeParam(DSP_ADDR_MASTERVOLUME, DSP_VOLUME_TABLE[step]);
}

uint8_t volume() { return s_volume; }

void setEqBand(uint8_t ch, uint8_t band, float gainDb) {
  if (ch > 2 || band > 9) return;
  gainDb = constrain(gainDb, -12.0f, 12.0f);
  s_eq[ch][band] = gainDb;
  float c[5];
  peakingBiquad(DSP_EQ_FREQS[band], DSP_EQ_Q, gainDb, c);
  writeParams(eqAddr(ch, band), c, 5);
}

float eqBand(uint8_t ch, uint8_t band) { return s_eq[ch][band]; }

void setSubLevel(float db) {
  s_subLevel = constrain(db, -12.0f, 12.0f);
  writeParam(DSP_ADDR_SUBLEVEL, powf(10.0f, s_subLevel / 20.0f));
}

float subLevel() { return s_subLevel; }

void setPreset(uint8_t preset) {
  if (preset > 2) return;
  s_preset = preset;
  const float* p = preset == 0   ? DSP_PRESET_FLAT
                   : preset == 1 ? DSP_PRESET_MUSIC
                                 : DSP_PRESET_OUTDOOR;
  // Klickfreier Wechsel: kurz auf Stufe herunterfahren, Filter tauschen,
  // wieder hochfahren. Die Slew-Zelle macht daraus Rampen.
  uint8_t oldVol = s_volume;
  writeParam(DSP_ADDR_MASTERVOLUME, DSP_VOLUME_TABLE[oldVol] * 0.1f);
  delay(30);
  for (uint8_t ch = 0; ch < 3; ch++)
    for (uint8_t b = 0; b < 10; b++) setEqBand(ch, b, p[b]);
  setSubLevel(p[10]);
  delay(10);
  writeParam(DSP_ADDR_MASTERVOLUME, DSP_VOLUME_TABLE[oldVol]);
}

uint8_t preset() { return s_preset; }

}  // namespace dsp
