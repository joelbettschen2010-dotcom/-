// Bluetooth-Modul (BTM875-B / CSR8675 mit UART-Steuerung).
// Der Status-Pin liefert die Verbindungsanzeige hardwareseitig; UART wird
// fuer Namen/Pairing-Kommandos genutzt (AT-Syntax, modulabhaengig —
// Kommandos zentral hier definiert, damit sie leicht anpassbar sind).
#pragma once
#include <Arduino.h>

namespace btmodule {

void begin();
void loop();

bool connected();      // vom STATUS-Pin
void startPairing();   // Pairing-Modus ausloesen (PAIR-Pin + AT-Kommando)
bool pairing();
void setName(const char* name);
void playPause();
void next();
void prev();

}  // namespace btmodule
