#include "btmodule.h"

#include "config.h"

namespace btmodule {

static HardwareSerial& BT = Serial1;
static uint32_t s_pairingUntil = 0;

// AT-Kommandos: Default fuer BTM8xx-Serie mit UART-Firmware. Falls das
// konkrete Modul andere Kommandos nutzt (z. B. JL-BT401: "AT+BM..."),
// nur diese Tabelle anpassen.
static const char* CMD_NAME = "AT+NAME=";     // + Name
static const char* CMD_PAIR = "AT+PAIR\r\n";
static const char* CMD_PLAY = "AT+PLAY\r\n";  // Play/Pause-Toggle (AVRCP)
static const char* CMD_NEXT = "AT+NEXT\r\n";
static const char* CMD_PREV = "AT+PREV\r\n";

void begin() {
  pinMode(PIN_BT_EN, OUTPUT);
  pinMode(PIN_BT_STATUS, INPUT);
  pinMode(PIN_BT_PAIR, OUTPUT);
  digitalWrite(PIN_BT_PAIR, LOW);
  digitalWrite(PIN_BT_EN, HIGH);  // Modul einschalten
  BT.begin(115200, SERIAL_8N1, PIN_BT_RX, PIN_BT_TX);
  delay(500);                     // Modul-Boot
  setName(DEVICE_NAME);
}

void loop() {
  // UART-Antworten verwerfen/loggen (Statusauswertung laeuft ueber Pin)
  while (BT.available()) {
    char c = BT.read();
    (void)c;
  }
}

bool connected() { return digitalRead(PIN_BT_STATUS) == HIGH; }

void startPairing() {
  // Hardware-Pin pulsen (funktioniert auch ohne passende UART-Firmware)
  digitalWrite(PIN_BT_PAIR, HIGH);
  delay(120);
  digitalWrite(PIN_BT_PAIR, LOW);
  BT.print(CMD_PAIR);
  s_pairingUntil = millis() + 60000;  // 60 s Pairing-Fenster anzeigen
}

bool pairing() { return millis() < s_pairingUntil && !connected(); }

void setName(const char* name) {
  BT.print(CMD_NAME);
  BT.print(name);
  BT.print("\r\n");
}

void playPause() { BT.print(CMD_PLAY); }
void next() { BT.print(CMD_NEXT); }
void prev() { BT.print(CMD_PREV); }

}  // namespace btmodule
