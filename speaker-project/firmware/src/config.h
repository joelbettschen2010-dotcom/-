// SpeakerBox Pro — Pin-Zuordnung und Konstanten.
// Muss mit pcb/main-board/design.py uebereinstimmen!
#pragma once

// --- GPIO-Zuordnung (ESP32-S3-WROOM-1) --------------------------------------
#define PIN_PWR_HOLD    4    // NPN-Latch: high = Versorgung halten
#define PIN_BTN_PWR     5    // Power-Taster (low-aktiv, 10k Pullup extern)
#define PIN_BTN_ADC     6    // Widerstandsleiter der 5 Funktionstasten
#define PIN_VBAT_SENSE  7    // Akkuspannung ueber 100k/10k-Teiler
#define PIN_AMP_SDZ     15   // TPA3118 Shutdown (high = Verstaerker an)
#define PIN_LED_DATA    16   // WS2812B auf Button-Board
#define PIN_BT_TX       17   // UART1 TX -> BT-Modul RX
#define PIN_BT_RX       18   // UART1 RX <- BT-Modul TX
#define PIN_I2C_SDA     8
#define PIN_I2C_SCL     9
#define PIN_BT_EN       3    // BT-Modul Enable
#define PIN_BT_STATUS   10   // BT-Modul Status-Ausgang (high = verbunden)
#define PIN_BT_PAIR     11   // BT-Modul Pairing-Taste (Impuls)
#define PIN_CHG_STAT    12   // Lade-Board: laedt (low-aktiv, open-drain)
#define PIN_FULL_STAT   13   // Lade-Board: voll (low-aktiv)
#define PIN_AMP_MUTE    21   // TPA3118 Mute (high = stumm)
#define PIN_AMP_FAULT   47   // TPA3118 FAULTZ (low = Fehler)
#define PIN_DSP_NRST    48   // ADAU1701 Reset (low-aktiv)

// --- I2C-Adressen ------------------------------------------------------------
#define I2C_ADDR_DSP    0x34 // ADAU1701 (ADDR1=0, ADDR0=0 -> 0x68 >> 1)
#define I2C_ADDR_OLED   0x3C // SSD1306

// --- Akku (4S Li-Ion) ---------------------------------------------------------
#define VBAT_DIVIDER    11.0f   // (100k+10k)/10k
#define VBAT_FULL       16.8f
#define VBAT_EMPTY      12.0f
#define VBAT_WARN       13.2f   // Rot blinken unterhalb

// --- Verhalten -----------------------------------------------------------------
#define PWR_HOLD_MS     2000    // Taster 2 s halten = Ein-/Ausschalten
#define VOLUME_STEPS    50
#define WIFI_AP_SSID    "SpeakerBox-Pro"
#define DEVICE_NAME     "SpeakerBox Pro"
#define FW_VERSION      "1.0.0"
