"""
SpeakerBox Pro — Button Board (60 x 20 mm Satellite-PCB).

6 Taster, WS2812B-RGB-LED, OLED-Anschluss, JST-PH-8 zum Main-Board.
5 der 6 Taster liegen auf einer Widerstandsleiter (1 ADC-Pin genuegt):
  Vol+ = 0R, Vol- = 2k2, BT = 4k7, Play = 10k, Next = 22k
  (Pullup 10k auf 3V3 sitzt auf dem Main-Board -> Spannungen
   0 / 0.59 / 1.05 / 1.65 / 2.27 V, Leerlauf 3.3 V — sauber trennbar)
Power-Taster hat eine eigene Leitung (muss das Geraet einschalten koennen,
wenn der ESP32 noch aus ist).
WS2812B: VDD ueber Schottky-Diode von 5V (~4.6V), damit der 3.3V-Pegel
von LED_DATA die V_IH-Schwelle (0.7*VDD) sicher erreicht.
"""

import sys, os
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from schgen import Comp

R0603 = "Resistor_SMD:R_0603_1608Metric"
C0603 = "Capacitor_SMD:C_0603_1608Metric"
SW = "Button_Switch_THT:SW_PUSH_6mm_H13mm"  # 13mm Stoessel durch 3D-Druck-Front

COMPS = []
def C(*a, **k):
    COMPS.append(Comp(*a, **k))

C("J1", "zum Main-Board", "Connector_JST:JST_PH_B8B-PH-K_1x08_P2.00mm_Vertical",
  [("1", "5V", "5V"), ("2", "GND", "GND"), ("3", "3V3", "3V3"), ("4", "SDA", "SDA"),
   ("5", "SCL", "SCL"), ("6", "LED", "LED_DATA"), ("7", "BTN", "BTN_ADC"),
   ("8", "PWR", "BTN_PWR")], lcsc="C157935")

# Power-Taster: eigene Leitung
C("SW1", "POWER", SW,
  [("1", "1", "BTN_PWR"), ("2", "1", "BTN_PWR"), ("3", "2", "GND"), ("4", "2", "GND")],
  lcsc="C221929")
# Widerstandsleiter-Taster
ladder = [("SW2", "VOL+", "0R", "C21189"), ("SW3", "VOL-", "2k2", "C4190"),
          ("SW4", "BT", "4k7", "C23162"), ("SW5", "PLAY", "10k", "C25804"),
          ("SW6", "NEXT", "22k", "C31850")]
for i, (ref, name, rval, lcsc) in enumerate(ladder):
    node = f"LAD{i}"
    C(ref, name, SW,
      [("1", "1", node), ("2", "1", node), ("3", "2", "GND"), ("4", "2", "GND")],
      lcsc="C221929")
    C(f"R{i+1}", rval, R0603, [("1", "~", "BTN_ADC"), ("2", "~", node)], lcsc=lcsc)

# RGB-Status-LED
C("D1", "SS14", "Diode_SMD:D_SMA", [("1", "K", "LED_VDD"), ("2", "A", "5V")],
  lcsc="C2480", desc="Pegel-Trick: WS2812B-VDD ~4.6V")
C("C1", "22u", "Capacitor_SMD:C_0805_2012Metric",
  [("1", "~", "LED_VDD"), ("2", "~", "GND")], lcsc="C45783")
C("C2", "100n", C0603, [("1", "~", "LED_VDD"), ("2", "~", "GND")], lcsc="C14663")
C("R6", "330R", R0603, [("1", "~", "LED_DATA"), ("2", "~", "LED_DIN")], lcsc="C23138")
C("LED1", "WS2812B", "LED_SMD:LED_WS2812B_PLCC4_5.0x5.0mm_P3.2mm",
  [("1", "VDD", "LED_VDD"), ("2", "DOUT", ""), ("3", "VSS", "GND"), ("4", "DIN", "LED_DIN")],
  lcsc="C114586")

# OLED 0.96" I2C als Steckmodul (4-polige Buchsenleiste)
C("J2", "OLED 0.96", "Connector_PinHeader_2.54mm:PinHeader_1x04_P2.54mm_Vertical",
  [("1", "GND", "GND"), ("2", "VCC", "3V3"), ("3", "SCL", "SCL"), ("4", "SDA", "SDA")],
  lcsc="C2337")

GROUPS = [("BUTTON BOARD", [c.ref for c in COMPS])]

if __name__ == "__main__":
    nets = {}
    for c in COMPS:
        for pad, name, net in c.pins:
            if net:
                nets.setdefault(net, []).append(f"{c.ref}.{pad}")
    print(f"{len(COMPS)} Bauteile, {len(nets)} Netze")
    single = {n: p for n, p in nets.items() if len(p) == 1}
    if single:
        print("WARNUNG:", single)
