"""
SpeakerBox Pro — Main Board circuit definition (netlist as data).

All IC pinouts verified against datasheets:
  - TPA3118D2DAP: TI SLOS708G pin table (32-HTSSOP, PowerPAD down)
  - ADAU1701:     ADI Rev. C pin table (48-LQFP); 1.8 V core via external
                  PNP driven by VDRIVE (datasheet "Voltage Regulator", Fig. 16);
                  PLL loop filter 475R/56n/3.3n (Fig. 15); PLL_MODE 256xfs = (0,1)
  - ESP32-S3-WROOM-1: KiCad official symbol/footprint pairing
  - LMR33630ADDA: KiCad official symbol (1 GND, 2 VIN, 3 EN, 4 PG, 5 FB,
                  6 VCC, 7 BOOT, 8 SW, EP GND); FB ref 1.0 V
  - AMS1117-3.3 (SOT-223: 1 GND, 2 VOUT+Tab, 3 VIN), 24LC32A (SOIC-8 std)

Design decisions (see ../README.md for full rationale):
  - Bluetooth module connects ANALOG into the ADAU1701 ADC inputs (current
    inputs, 18k series = 2 Vrms FS) and is passively mixed with Aux-In at the
    ADC pin. This avoids the classic ADAU1701 async-I2S clock-domain trap.
  - Fuel gauge: MAX17043/BQ27441 are 1-cell gauges and CANNOT measure a 4S
    pack. Replaced by a precision divider (100k/10k 1%) into an ESP32 ADC.
  - Amps: U6 master BTL stereo (fullrange), U7 slave PBTL (sub), SYNC bus
    connected to avoid beat noise (TI 2.1 reference configuration).
    Gain 20 dB: master R 5.6k to GND; slave 51k/51k divider (TI Table 1).
  - Soft power: two AO4407A P-FETs (reverse protection + soft switch),
    push-button pulls the gate via diode, ESP32 latches via NPN.
"""

import sys, os
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from schgen import Comp

# Kurz-Aliase fuer Footprints
R0603 = "Resistor_SMD:R_0603_1608Metric"
C0603 = "Capacitor_SMD:C_0603_1608Metric"
C0805 = "Capacitor_SMD:C_0805_2012Metric"
C1206 = "Capacitor_SMD:C_1206_3216Metric"
CELKO = "Capacitor_SMD:CP_Elec_10x10.5"
CELKO_S = "Capacitor_SMD:CP_Elec_6.3x7.7"
SOD123 = "Diode_SMD:D_SOD-123"
SOD323 = "Diode_SMD:D_SOD-323"
SMB = "Diode_SMD:D_SMB"
SMA = "Diode_SMD:D_SMA"
SOT23 = "Package_TO_SOT_SMD:SOT-23"
SO8 = "Package_SO:SOIC-8_3.9x4.9mm_P1.27mm"
LED0603 = "LED_SMD:LED_0603_1608Metric"
L_PWR = "Inductor_SMD:L_12x12mm_H8mm"   # 10uH/6A Power-Induktivitaet (SRP1245A o.ae.)
L_BUCK = "Inductor_SMD:L_6.3x6.3_H3"

COMPS = []
def C(*a, **k):
    COMPS.append(Comp(*a, **k))

# =========================================================================
# POWER INPUT / SOFT SWITCH
# =========================================================================
C("J1", "XT30U-M", "Connector_AMASS:AMASS_XT30U-M_1x02_P5.0mm_Vertical",
  [("1", "BAT+", "VBAT_RAW"), ("2", "BAT-", "GND")], desc="Akku-Eingang XT30")
C("F1", "10A", "Fuse:Fuse_1812_4532Metric",
  [("1", "~", "VBAT_RAW"), ("2", "~", "VBAT_F")], lcsc="C369167", desc="SMD-Sicherung 10A flink")
C("D1", "SMBJ18A", SMB,
  [("1", "K", "VBAT_F"), ("2", "A", "GND")], lcsc="C114213", desc="TVS 18V Ueberspannungsschutz")
# Verpolschutz: P-FET, Body-Diode leitet initial, dann Kanal (D=Akku, S=Last)
C("Q1", "AO4407A", SO8,
  [("1", "S", "VBAT_P"), ("2", "S", "VBAT_P"), ("3", "S", "VBAT_P"),
   ("4", "G", "Q1_G"), ("5", "D", "VBAT_F"), ("6", "D", "VBAT_F"),
   ("7", "D", "VBAT_F"), ("8", "D", "VBAT_F")], lcsc="C77843", desc="P-FET 30V 12A Verpolschutz")
C("R1", "100k", R0603, [("1", "~", "Q1_G"), ("2", "~", "GND")], lcsc="C25803")
C("D2", "BZT52C12", SOD123, [("1", "K", "VBAT_P"), ("2", "A", "Q1_G")],
  lcsc="C110188", desc="Zener 12V Vgs-Klemme")
# Soft-Power-Schalter
C("Q2", "AO4407A", SO8,
  [("1", "S", "VBAT_P"), ("2", "S", "VBAT_P"), ("3", "S", "VBAT_P"),
   ("4", "G", "Q2_G"), ("5", "D", "PVDD"), ("6", "D", "PVDD"),
   ("7", "D", "PVDD"), ("8", "D", "PVDD")], lcsc="C77843", desc="P-FET Soft-Power-Schalter")
C("R2", "100k", R0603, [("1", "~", "Q2_G"), ("2", "~", "VBAT_P")], lcsc="C25803")
C("D3", "BZT52C12", SOD123, [("1", "K", "VBAT_P"), ("2", "A", "Q2_G")], lcsc="C110188")
C("C90", "100n", C0603, [("1", "~", "Q2_G"), ("2", "~", "VBAT_P")], lcsc="C14663",
  desc="Soft-Start-Rampe (~10ms mit R2)")
C("R3", "1k", R0603, [("1", "~", "Q2_G"), ("2", "~", "PWR_CTL")], lcsc="C21190")
C("D4", "1N4148WS", SOD323, [("1", "K", "BTN_PWR"), ("2", "A", "PWR_CTL")], lcsc="C2128")
C("Q3", "MMBT3904", SOT23,
  [("1", "B", "Q3_B"), ("2", "E", "GND"), ("3", "C", "PWR_CTL")], lcsc="C20526",
  desc="NPN Power-Latch (ESP haelt Versorgung)")
C("R4", "10k", R0603, [("1", "~", "Q3_B"), ("2", "~", "PWR_HOLD")], lcsc="C25804")
C("R5", "10k", R0603, [("1", "~", "Q3_B"), ("2", "~", "GND")], lcsc="C25804")
C("R6", "10k", R0603, [("1", "~", "BTN_PWR"), ("2", "~", "3V3")], lcsc="C25804")

# Balancer-Durchfuehrung Akku -> Lade-Board
for ref, d in (("J2", "Balance Akku IN"), ("J3", "Balance Lade-Board OUT")):
    C(ref, "JST-XH-5", "Connector_JST:JST_XH_B5B-XH-A_1x05_P2.50mm_Vertical",
      [("1", "B-", "GND"), ("2", "C1", "BAL1"), ("3", "C2", "BAL2"),
       ("4", "C3", "BAL3"), ("5", "B+", "VBAT_RAW")], lcsc="C158012", desc=d)
C("J4", "CHG-STAT", "Connector_PinHeader_2.54mm:PinHeader_1x03_P2.54mm_Vertical",
  [("1", "CHG", "CHG_STAT"), ("2", "FULL", "FULL_STAT"), ("3", "GND", "GND")],
  lcsc="C2337", desc="Status vom Lade-Board (Open-Drain)")
C("D5", "LED rt", LED0603, [("1", "K", "CHG_STAT"), ("2", "A", "D5_A")], lcsc="C2286")
C("R7", "4k7", R0603, [("1", "~", "D5_A"), ("2", "~", "VBAT_P")], lcsc="C23162")
C("D6", "LED gn", LED0603, [("1", "K", "FULL_STAT"), ("2", "A", "D6_A")], lcsc="C72043")
C("R8", "4k7", R0603, [("1", "~", "D6_A"), ("2", "~", "VBAT_P")], lcsc="C23162")
C("D7", "LED gn", LED0603, [("1", "K", "GND"), ("2", "A", "D7_A")], lcsc="C72043", desc="Power-LED")
C("R9", "1k", R0603, [("1", "~", "D7_A"), ("2", "~", "3V3")], lcsc="C21190")
# ESP32 liest Ladezustand mit (Open-Drain-Signale, Pullup auf 3V3)
C("R60", "100k", R0603, [("1", "~", "CHG_STAT"), ("2", "~", "3V3")], lcsc="C25803")
C("R61", "100k", R0603, [("1", "~", "FULL_STAT"), ("2", "~", "3V3")], lcsc="C25803")

# =========================================================================
# 5V BUCK (LMR33630ADDA: 36V/3A synchron, FB-Referenz 1.00V)
# =========================================================================
C("U1", "LMR33630ADDA", "Package_SO:Texas_HSOP-8-1EP_3.9x4.9mm_P1.27mm_ThermalVias",
  [("1", "GND", "GND"), ("2", "VIN", "PVDD"), ("3", "EN", "PVDD"),
   ("4", "PG", ""), ("5", "FB", "FB5V"), ("6", "VCC", "U1_VCC"),
   ("7", "BOOT", "U1_BOOT"), ("8", "SW", "SW5V"), ("9", "EP", "GND")],
  lcsc="C841384", desc="Buck 5V/3A")
C("C2", "10u/25V", C1206, [("1", "~", "PVDD"), ("2", "~", "GND")], lcsc="C13585")
C("C3", "10u/25V", C1206, [("1", "~", "PVDD"), ("2", "~", "GND")], lcsc="C13585")
C("C4", "100n", C0603, [("1", "~", "PVDD"), ("2", "~", "GND")], lcsc="C14663")
C("C5", "100n", C0603, [("1", "~", "U1_BOOT"), ("2", "~", "SW5V")], lcsc="C14663")
C("C6", "1u", C0603, [("1", "~", "U1_VCC"), ("2", "~", "GND")], lcsc="C15849")
C("L1", "10uH/4A", L_BUCK, [("1", "~", "SW5V"), ("2", "~", "5V")],
  lcsc="C167219", desc="Buck-Induktivitaet 10uH ges. Saettigung >4A")
C("R10", "100k 1%", R0603, [("1", "~", "FB5V"), ("2", "~", "5V")], lcsc="C25803")
C("R11", "24k9 1%", R0603, [("1", "~", "FB5V"), ("2", "~", "GND")], lcsc="C23046")
C("C7", "22u", C0805, [("1", "~", "5V"), ("2", "~", "GND")], lcsc="C45783")
C("C8", "22u", C0805, [("1", "~", "5V"), ("2", "~", "GND")], lcsc="C45783")

# 3V3 LDO + Analog-Versorgung
C("U2", "AMS1117-3.3", "Package_TO_SOT_SMD:SOT-223-3_TabPin2",
  [("1", "GND", "GND"), ("2", "VOUT", "3V3"), ("3", "VIN", "5V")],
  lcsc="C6186", desc="LDO 3.3V 1A")
C("C9", "10u", C0805, [("1", "~", "5V"), ("2", "~", "GND")], lcsc="C15850")
C("C10", "10u", C0805, [("1", "~", "3V3"), ("2", "~", "GND")], lcsc="C15850")
C("FB1", "600R@100MHz", "Inductor_SMD:L_0805_2012Metric",
  [("1", "~", "3V3"), ("2", "~", "3V3A")], lcsc="C1017", desc="Ferrit fuer Analogversorgung DSP")
C("C11", "10u", C0805, [("1", "~", "3V3A"), ("2", "~", "AGND")], lcsc="C15850")

# Akkuspannungs-Messung (ersetzt MAX17043 — 1S-Gauge passt nicht fuer 4S!)
C("R12", "100k 1%", R0603, [("1", "~", "VBAT_P"), ("2", "~", "VBSENSE")], lcsc="C25803")
C("R13", "10k 1%", R0603, [("1", "~", "VBSENSE"), ("2", "~", "GND")], lcsc="C25804")
C("C12", "100n", C0603, [("1", "~", "VBSENSE"), ("2", "~", "GND")], lcsc="C14663")

# =========================================================================
# DSP ADAU1701 (Pinout: ADI Datenblatt Rev. C, Table 10)
# =========================================================================
C("U3", "ADAU1701JSTZ", "Package_QFP:LQFP-48_7x7mm_P0.5mm",
  [("1", "AGND", "AGND"), ("2", "ADC0", "ADC0_IN"), ("3", "ADC_RES", "ADCRES"),
   ("4", "ADC1", "ADC1_IN"), ("5", "~RESET", "DSP_NRST"), ("6", "SELFBOOT", "SELFBOOT"),
   ("7", "ADDR0", "GND"), ("8", "MP4", ""), ("9", "MP5", ""),
   ("10", "MP1", ""), ("11", "MP0", ""), ("12", "DGND", "GND"),
   ("13", "DVDD", "DVDD_1V8"), ("14", "MP7", ""), ("15", "MP6", ""),
   ("16", "MP10", ""), ("17", "VDRIVE", "VDRIVE"), ("18", "IOVDD", "3V3"),
   ("19", "MP11", ""), ("20", "ADDR1/WB", "ADDR1"), ("21", "CLATCH/WP", "EE_WP"),
   ("22", "SDA", "SDA"), ("23", "SCL", "SCL"), ("24", "DVDD", "DVDD_1V8"),
   ("25", "DGND", "GND"), ("26", "MP9", ""), ("27", "MP8", ""),
   ("28", "MP3", ""), ("29", "MP2", ""), ("30", "RSVD", "GND"),
   ("31", "OSCO", "OSCO"), ("32", "MCLKI", "MCLKI"), ("33", "PGND", "GND"),
   ("34", "PVDD", "PVDD_PLL"), ("35", "PLL_LF", "PLL_LF"), ("36", "AVDD", "3V3A"),
   ("37", "AGND", "AGND"), ("38", "PLL_MODE0", "GND"), ("39", "PLL_MODE1", "3V3"),
   ("40", "CM", "CM"), ("41", "FILTD", "FILTD"), ("42", "AGND", "AGND"),
   ("43", "VOUT3", ""), ("44", "VOUT2", "DAC_SUB"), ("45", "VOUT1", "DAC_R"),
   ("46", "VOUT0", "DAC_L"), ("47", "FILTA", "FILTA"), ("48", "AVDD", "3V3A")],
  lcsc="C51118", desc="SigmaDSP 28/56-bit, 2 ADC / 4 DAC")
# 1.8V-Core-Regler: externer PNP an VDRIVE (Datenblatt Fig. 16, 1k Basiswid.)
C("Q4", "BC807-40", SOT23,
  [("1", "B", "VDRV_B"), ("2", "E", "3V3"), ("3", "C", "DVDD_1V8")],
  lcsc="C130197", desc="PNP 1.8V-Core-Regler (hFE>100)")
C("R18", "1k", R0603, [("1", "~", "VDRIVE"), ("2", "~", "VDRV_B")], lcsc="C21190")
C("C23", "10u", C0805, [("1", "~", "DVDD_1V8"), ("2", "~", "GND")], lcsc="C15850")
C("C24", "100n", C0603, [("1", "~", "DVDD_1V8"), ("2", "~", "GND")], lcsc="C14663")
C("C25", "100n", C0603, [("1", "~", "DVDD_1V8"), ("2", "~", "GND")], lcsc="C14663")
# Quarz 12.288 MHz (256 x fs, PLL_MODE0=0 / PLL_MODE1=1)
C("Y1", "12.288MHz", "Crystal:Crystal_SMD_3225-4Pin_3.2x2.5mm",
  [("1", "X1", "MCLKI"), ("2", "GND", "GND"), ("3", "X2", "OSC_D"), ("4", "GND", "GND")],
  lcsc="C255909", desc="Quarz 12.288MHz 3225")
C("R15", "100R", R0603, [("1", "~", "OSCO"), ("2", "~", "OSC_D")], lcsc="C22775",
  desc="Daempfung OSCO (Datenblatt)")
C("C13", "22p", C0603, [("1", "~", "MCLKI"), ("2", "~", "GND")], lcsc="C1653")
C("C14", "22p", C0603, [("1", "~", "OSC_D"), ("2", "~", "GND")], lcsc="C1653")
# PLL-Loop-Filter (Datenblatt Fig. 15: 475R + 56n seriell, 3.3n parallel, an AVDD)
C("C15", "3n3", C0603, [("1", "~", "PLL_LF"), ("2", "~", "3V3A")], lcsc="C1594")
C("R16", "475R 1%", R0603, [("1", "~", "PLL_LF"), ("2", "~", "PLLRC")], lcsc="C23233")
C("C16", "56n", C0603, [("1", "~", "PLLRC"), ("2", "~", "3V3A")], lcsc="C1621")
# Referenzen/Filter
C("C17", "47u", CELKO_S, [("1", "+", "CM"), ("2", "-", "AGND")], lcsc="C134801")
C("C18", "100n", C0603, [("1", "~", "CM"), ("2", "~", "AGND")], lcsc="C14663")
C("C19", "10u", C0805, [("1", "~", "FILTD"), ("2", "~", "AGND")], lcsc="C15850")
C("C21", "10u", C0805, [("1", "~", "FILTA"), ("2", "~", "AGND")], lcsc="C15850")
C("R17", "18k 1%", R0603, [("1", "~", "ADCRES"), ("2", "~", "AGND")], lcsc="C25810",
  desc="ADC-Referenzstrom (2Vrms FS)")
# Abblockung
C("C26", "100n", C0603, [("1", "~", "3V3A"), ("2", "~", "AGND")], lcsc="C14663")
C("C27", "100n", C0603, [("1", "~", "3V3A"), ("2", "~", "AGND")], lcsc="C14663")
C("C28", "100n", C0603, [("1", "~", "3V3"), ("2", "~", "GND")], lcsc="C14663")
C("C29", "100n", C0603, [("1", "~", "PVDD_PLL"), ("2", "~", "GND")], lcsc="C14663")
C("R63", "0R", R0603, [("1", "~", "PVDD_PLL"), ("2", "~", "3V3")], lcsc="C21189",
  desc="PLL-Versorgung von 3V3 (Option RC-Filter)")
C("C30", "10u", C0805, [("1", "~", "3V3"), ("2", "~", "GND")], lcsc="C15850")
# Reset / Selfboot / Adresse
C("R19", "10k", R0603, [("1", "~", "DSP_NRST"), ("2", "~", "3V3")], lcsc="C25804")
C("R14", "10k", R0603, [("1", "~", "SELFBOOT"), ("2", "~", "3V3")], lcsc="C25804",
  desc="Selfboot aktiv (auf GND loeten zum Deaktivieren)")
C("R62", "10k", R0603, [("1", "~", "ADDR1"), ("2", "~", "GND")], lcsc="C25804")
C("R20", "10k", R0603, [("1", "~", "EE_WP"), ("2", "~", "3V3")], lcsc="C25804")

# EEPROM (Selfboot-Programm)
C("U4", "24LC32A", SO8,
  [("1", "A0", "GND"), ("2", "A1", "GND"), ("3", "A2", "GND"), ("4", "VSS", "GND"),
   ("5", "SDA", "SDA"), ("6", "SCL", "SCL"), ("7", "WP", "EE_WP"), ("8", "VCC", "3V3")],
  lcsc="C7593", desc="I2C-EEPROM 32kbit Selfboot")
C("C31", "100n", C0603, [("1", "~", "3V3"), ("2", "~", "GND")], lcsc="C14663")
C("R21", "2k2", R0603, [("1", "~", "SDA"), ("2", "~", "3V3")], lcsc="C4190")
C("R22", "2k2", R0603, [("1", "~", "SCL"), ("2", "~", "3V3")], lcsc="C4190")

# Programmier-Header (SigmaStudio USBi / ICP5-kompatibel, I2C + Reset)
C("J5", "ICP5/USBi", "Connector_PinHeader_2.54mm:PinHeader_2x05_P2.54mm_Vertical",
  [("1", "3V3", "3V3"), ("2", "GND", "GND"), ("3", "SCL", "SCL"), ("4", "SDA", "SDA"),
   ("5", "~RST", "DSP_NRST"), ("6", "SELFBOOT", "SELFBOOT"), ("7", "WB", "ADDR1"),
   ("8", "WP", "EE_WP"), ("9", "NC", ""), ("10", "GND", "GND")],
  lcsc="C2337", desc="DSP-Programmierung")

# =========================================================================
# AUDIO-EINGAENGE (Aux + BT analog, passiv am Strom-Eingang gemischt)
# =========================================================================
C("J6", "AuxIn 3.5mm", "Connector_Audio:Jack_3.5mm_CUI_SJ-3523-SMT_Horizontal",
  [("1", "SLEEVE", "AGND"), ("2", "TIP", "AUX_L"), ("3", "RING", "AUX_R")],
  lcsc="C145814", desc="Klinke 3.5mm SMD")
C("C32", "4u7", C0805, [("1", "~", "AUX_L"), ("2", "~", "AUXC_L")], lcsc="C29823")
C("R23", "18k 1%", R0603, [("1", "~", "AUXC_L"), ("2", "~", "ADC0_IN")], lcsc="C25810")
C("C33", "4u7", C0805, [("1", "~", "AUX_R"), ("2", "~", "AUXC_R")], lcsc="C29823")
C("R24", "18k 1%", R0603, [("1", "~", "AUXC_R"), ("2", "~", "ADC1_IN")], lcsc="C25810")
C("C34", "4u7", C0805, [("1", "~", "BT_L"), ("2", "~", "BTC_L")], lcsc="C29823")
C("R25", "18k 1%", R0603, [("1", "~", "BTC_L"), ("2", "~", "ADC0_IN")], lcsc="C25810")
C("C35", "4u7", C0805, [("1", "~", "BT_R"), ("2", "~", "BTC_R")], lcsc="C29823")
C("R26", "18k 1%", R0603, [("1", "~", "BTC_R"), ("2", "~", "ADC1_IN")], lcsc="C25810")
C("C36", "100p", C0603, [("1", "~", "ADC0_IN"), ("2", "~", "AGND")], lcsc="C14858")
C("C37", "100p", C0603, [("1", "~", "ADC1_IN"), ("2", "~", "AGND")], lcsc="C14858")

# DAC-Rekonstruktionsfilter + Koppel-C zu den Endstufen
for i, ch in enumerate(("L", "R", "SUB")):
    C(f"R{27 + i}", "560R", R0603,
      [("1", "~", f"DAC_{ch}"), ("2", "~", f"DACF_{ch}")], lcsc="C23204")
    C(f"C{38 + i}", "4n7", C0603,
      [("1", "~", f"DACF_{ch}"), ("2", "~", "AGND")], lcsc="C1590")
    C(f"C{41 + i}", "2u2", C0805,
      [("1", "~", f"DACF_{ch}"), ("2", "~", f"AMPIN_{ch}")], lcsc="C49217")

# =========================================================================
# ENDSTUFE 1: TPA3118D2 Stereo-BTL Fullrange (MASTER, 20 dB)
# =========================================================================
def tpa(ref, gvdd, pins_special):
    base = {
        "1": ("MODSEL", "GND"), "2": ("SDZ", "AMP_SDZ"), "3": ("FAULTZ", "AMP_FAULT"),
        "6": ("PLIMIT", gvdd), "7": ("GVDD", gvdd), "9": ("GND", "GND"),
        "12": ("MUTE", "AMP_MUTE"), "13": ("AM2", "GND"), "14": ("AM1", "GND"),
        "15": ("AM0", "GND"), "16": ("SYNC", "SYNC"), "17": ("AVCC", "PVDD"),
        "18": ("PVCC", "PVDD"), "19": ("PVCC", "PVDD"), "22": ("GND", "GND"),
        "25": ("GND", "GND"), "28": ("GND", "GND"), "31": ("PVCC", "PVDD"),
        "32": ("PVCC", "PVDD"), "33": ("PAD", "GND"),
    }
    base.update(pins_special)
    pins = [(num, base[num][0], base[num][1]) for num in
            [str(n) for n in range(1, 33)] + ["33"]]
    C(ref, "TPA3118D2DAP", "Package_SO:HTSSOP-32-1EP_6.1x11mm_P0.65mm_EP5.2x11mm_Mask4.11x4.36mm_ThermalVias",
      pins, lcsc="C88243", desc="Class-D 2x30W / PBTL 60W")

# Master: Stereo BTL, Eingaenge L/R, Gain 20dB (R 5.6k an GND, R2 offen)
tpa("U6", "GVDD1", {
    "4": ("RINP", "AMPIN_R"), "5": ("RINN", "U6_RINN"),
    "8": ("GAIN/SLV", "U6_GAIN"),
    "10": ("LINP", "AMPIN_L"), "11": ("LINN", "U6_LINN"),
    "20": ("BSNL", "U6_BSNL"), "21": ("OUTNL", "U6_OUTNL"),
    "23": ("OUTPL", "U6_OUTPL"), "24": ("BSPL", "U6_BSPL"),
    "26": ("BSNR", "U6_BSNR"), "27": ("OUTNR", "U6_OUTNR"),
    "29": ("OUTPR", "U6_OUTPR"), "30": ("BSPR", "U6_BSPR"),
})
C("R32", "5k6", R0603, [("1", "~", "U6_GAIN"), ("2", "~", "GND")], lcsc="C23189",
  desc="Gain 20dB Master (TI Table 1)")
C("C45", "2u2", C0805, [("1", "~", "U6_RINN"), ("2", "~", "AGND")], lcsc="C49217")
C("C46", "2u2", C0805, [("1", "~", "U6_LINN"), ("2", "~", "AGND")], lcsc="C49217")
C("C48", "1u", C0603, [("1", "~", "GVDD1"), ("2", "~", "GND")], lcsc="C15849")
C("R33", "100k", R0603, [("1", "~", "AMP_MUTE"), ("2", "~", "GVDD1")], lcsc="C25803",
  desc="Mute-Default beim Boot")
C("R30", "100k", R0603, [("1", "~", "AMP_SDZ"), ("2", "~", "GND")], lcsc="C25803",
  desc="Shutdown-Default beim Boot")
C("R31", "10k", R0603, [("1", "~", "AMP_FAULT"), ("2", "~", "3V3")], lcsc="C25804")
# Bootstraps
for r, (bs, out) in {"C55": ("U6_BSNL", "U6_OUTNL"), "C56": ("U6_BSPL", "U6_OUTPL"),
                     "C57": ("U6_BSNR", "U6_OUTNR"), "C58": ("U6_BSPR", "U6_OUTPR")}.items():
    C(r, "220n", C0603, [("1", "~", bs), ("2", "~", out)], lcsc="C21120")
# AVCC-Abblockung
C("C49", "1u", C0603, [("1", "~", "PVDD"), ("2", "~", "GND")], lcsc="C15849")
C("C50", "100n", C0603, [("1", "~", "PVDD"), ("2", "~", "GND")], lcsc="C14663")
# Ausgangsfilter BTL 4R BD-Mode: 10uH + 680nF je Leitung
for L, (i_, o) in {"L2": ("U6_OUTPL", "FR_L_P"), "L3": ("U6_OUTNL", "FR_L_N"),
                   "L4": ("U6_OUTPR", "FR_R_P"), "L5": ("U6_OUTNR", "FR_R_N")}.items():
    C(L, "10uH/5A", L_PWR, [("1", "~", i_), ("2", "~", o)], lcsc="C167221")
for r, n in {"C59": "FR_L_P", "C60": "FR_L_N", "C61": "FR_R_P", "C62": "FR_R_N"}.items():
    C(r, "680n/50V", C0805, [("1", "~", n), ("2", "~", "GND")], lcsc="C28779")
# PVDD-Abblockung + Bulk
for r in ("C51", "C52"):
    C(r, "100n", C0603, [("1", "~", "PVDD"), ("2", "~", "GND")], lcsc="C14663")
for r in ("C80", "C81", "C82"):
    C(r, "1000u/25V", CELKO, [("1", "+", "PVDD"), ("2", "-", "GND")], lcsc="C249785",
      desc="PVDD-Bulk (3x1000uF = 3000uF gesamt)")
for r in ("C83", "C84"):
    C(r, "10u/25V", C1206, [("1", "~", "PVDD"), ("2", "~", "GND")], lcsc="C13585")

# =========================================================================
# ENDSTUFE 2: TPA3118D2 PBTL Sub (SLAVE, 20 dB), TI Fig. 36
# =========================================================================
tpa("U7", "GVDD2", {
    "4": ("RINP", "AMPIN_SUB"), "5": ("RINN", "U7_RINN"),
    "8": ("GAIN/SLV", "U7_GAIN"),
    "10": ("LINP", "GND"), "11": ("LINN", "GND"),      # PBTL-Select: direkt an GND
    "20": ("BSNL", "U7_BSNL"), "21": ("OUTNL", "SUB_OUT_N"),
    "23": ("OUTPL", "SUB_OUT_N"), "24": ("BSPL", "U7_BSPL"),
    "26": ("BSNR", "U7_BSNR"), "27": ("OUTNR", "SUB_OUT_P"),
    "29": ("OUTPR", "SUB_OUT_P"), "30": ("BSPR", "U7_BSPR"),
})
# Slave-Gain 20dB: 51k/51k Teiler an GVDD (TI Table 1)
C("R34", "51k", R0603, [("1", "~", "U7_GAIN"), ("2", "~", "GND")], lcsc="C23196")
C("R35", "51k", R0603, [("1", "~", "U7_GAIN"), ("2", "~", "GVDD2")], lcsc="C23196")
C("C63", "2u2", C0805, [("1", "~", "U7_RINN"), ("2", "~", "AGND")], lcsc="C49217")
C("C64", "1u", C0603, [("1", "~", "GVDD2"), ("2", "~", "GND")], lcsc="C15849")
C("R36", "100k", R0603, [("1", "~", "AMP_MUTE"), ("2", "~", "GVDD2")], lcsc="C25803")
for r, (bs, out) in {"C67": ("U7_BSNL", "SUB_OUT_N"), "C68": ("U7_BSPL", "SUB_OUT_N"),
                     "C69": ("U7_BSNR", "SUB_OUT_P"), "C70": ("U7_BSPR", "SUB_OUT_P")}.items():
    C(r, "220n", C0603, [("1", "~", bs), ("2", "~", out)], lcsc="C21120")
C("C65", "1u", C0603, [("1", "~", "PVDD"), ("2", "~", "GND")], lcsc="C15849")
C("C66", "100n", C0603, [("1", "~", "PVDD"), ("2", "~", "GND")], lcsc="C14663")
# PBTL-Ausgangsfilter: 1 Spule je Klemme (verdoppelter Strom!)
C("L6", "10uH/8A", L_PWR, [("1", "~", "SUB_OUT_P"), ("2", "~", "SUB_P")], lcsc="C167221")
C("L7", "10uH/8A", L_PWR, [("1", "~", "SUB_OUT_N"), ("2", "~", "SUB_N")], lcsc="C167221")
C("C71", "680n/50V", C0805, [("1", "~", "SUB_P"), ("2", "~", "GND")], lcsc="C28779")
C("C72", "680n/50V", C0805, [("1", "~", "SUB_N"), ("2", "~", "GND")], lcsc="C28779")

# Lautsprecher-Klemmen
C("J7", "FR-L", "TerminalBlock_Phoenix:TerminalBlock_Phoenix_MKDS-1,5-2-5.08_1x02_P5.08mm_Horizontal",
  [("1", "+", "FR_L_P"), ("2", "-", "FR_L_N")], lcsc="C8465", desc="Schraubklemme Fullrange L")
C("J8", "FR-R", "TerminalBlock_Phoenix:TerminalBlock_Phoenix_MKDS-1,5-2-5.08_1x02_P5.08mm_Horizontal",
  [("1", "+", "FR_R_P"), ("2", "-", "FR_R_N")], lcsc="C8465", desc="Schraubklemme Fullrange R")
C("J9", "SUB", "TerminalBlock_Phoenix:TerminalBlock_Phoenix_MKDS-1,5-2-5.08_1x02_P5.08mm_Horizontal",
  [("1", "+", "SUB_P"), ("2", "-", "SUB_N")], lcsc="C8465", desc="Schraubklemme Subwoofer")

# =========================================================================
# ESP32-S3 (Pin-Nummern gem. offiziellem KiCad-Symbol/Footprint-Paar)
# =========================================================================
C("U8", "ESP32-S3-WROOM-1-N8R2", "RF_Module:ESP32-S3-WROOM-1",
  [("1", "GND", "GND"), ("2", "3V3", "3V3"), ("3", "EN", "ESP_EN"),
   ("4", "IO4", "PWR_HOLD"), ("5", "IO5", "BTN_PWR"), ("6", "IO6", "BTN_ADC"),
   ("7", "IO7", "VBSENSE"), ("8", "IO15", "AMP_SDZ"), ("9", "IO16", "LED_DATA"),
   ("10", "IO17", "BT_TXD"), ("11", "IO18", "BT_RXD"), ("12", "IO8", "SDA"),
   ("13", "IO19", "USB_DN"), ("14", "IO20", "USB_DP"), ("15", "IO3", "BT_EN"),
   ("16", "IO46", ""), ("17", "IO9", "SCL"), ("18", "IO10", "BT_STATUS"),
   ("19", "IO11", "BT_PAIR"), ("20", "IO12", "CHG_STAT"), ("21", "IO13", "FULL_STAT"),
   ("22", "IO14", ""), ("23", "IO21", "AMP_MUTE"), ("24", "IO47", "AMP_FAULT"),
   ("25", "IO48", "DSP_NRST"), ("26", "IO45", ""), ("27", "IO0", "ESP_BOOT"),
   ("28", "IO35", ""), ("29", "IO36", ""), ("30", "IO37", ""),
   ("31", "IO38", ""), ("32", "IO39", "JTAG_MTCK"), ("33", "IO40", "JTAG_MTDO"),
   ("34", "IO41", "JTAG_MTDI"), ("35", "IO42", "JTAG_MTMS"), ("36", "RXD0", "UART0_RX"),
   ("37", "TXD0", "UART0_TX"), ("38", "IO2", ""), ("39", "IO1", ""),
   ("40", "GND", "GND"), ("41", "EP", "GND")],
  lcsc="C2913202", desc="ESP32-S3 WiFi/BLE Modul, 8MB Flash + 2MB PSRAM")
C("R37", "10k", R0603, [("1", "~", "ESP_EN"), ("2", "~", "3V3")], lcsc="C25804")
C("C73", "1u", C0603, [("1", "~", "ESP_EN"), ("2", "~", "GND")], lcsc="C15849")
C("SW1", "RESET", "Button_Switch_SMD:SW_SPST_SKQG_WithStem",
  [("1", "1", "ESP_EN"), ("2", "1", "ESP_EN"), ("3", "2", "GND"), ("4", "2", "GND")], lcsc="C221929")
C("SW2", "BOOT", "Button_Switch_SMD:SW_SPST_SKQG_WithStem",
  [("1", "1", "ESP_BOOT"), ("2", "1", "ESP_BOOT"), ("3", "2", "GND"), ("4", "2", "GND")], lcsc="C221929")
C("R38", "10k", R0603, [("1", "~", "ESP_BOOT"), ("2", "~", "3V3")], lcsc="C25804")
C("C74", "22u", C0805, [("1", "~", "3V3"), ("2", "~", "GND")], lcsc="C45783")
C("C75", "100n", C0603, [("1", "~", "3V3"), ("2", "~", "GND")], lcsc="C14663")
C("R41", "10k", R0603, [("1", "~", "BTN_ADC"), ("2", "~", "3V3")], lcsc="C25804",
  desc="Pullup Tasten-Widerstandsleiter")
C("C76", "10n", C0603, [("1", "~", "BTN_ADC"), ("2", "~", "GND")], lcsc="C57112")

# USB-C (nur USB 2.0; CC-Pulldowns fuer Device-Rolle)
C("J11", "USB-C", "Connector_USB:USB_C_Receptacle_HRO_TYPE-C-31-M-12",
  [("A1", "GND", "GND"), ("A4", "VBUS", "VBUS"), ("A5", "CC1", "CC1"),
   ("A6", "D+", "USB_DP"), ("A7", "D-", "USB_DN"), ("A8", "SBU1", ""),
   ("A9", "VBUS", "VBUS"), ("A12", "GND", "GND"),
   ("B1", "GND", "GND"), ("B4", "VBUS", "VBUS"), ("B5", "CC2", "CC2"),
   ("B6", "D+", "USB_DP"), ("B7", "D-", "USB_DN"), ("B8", "SBU2", ""),
   ("B9", "VBUS", "VBUS"), ("B12", "GND", "GND"),
   ("S1", "SHIELD", "GND")],
  lcsc="C165948", desc="USB-C Buchse (Firmware-Upload, native USB)")
C("R39", "5k1", R0603, [("1", "~", "CC1"), ("2", "~", "GND")], lcsc="C23186")
C("R40", "5k1", R0603, [("1", "~", "CC2"), ("2", "~", "GND")], lcsc="C23186")
C("D8", "SS34", SMA, [("1", "K", "5V"), ("2", "A", "VBUS")], lcsc="C8678",
  desc="USB->5V Dioden-OR (Betrieb ohne Akku)")
C("U9", "USBLC6-2SC6", "Package_TO_SOT_SMD:SOT-23-6",
  [("1", "IO1", "USB_DN"), ("2", "GND", "GND"), ("3", "IO2", "USB_DP"),
   ("4", "IO2b", "USB_DP"), ("5", "VBUS", "VBUS"), ("6", "IO1b", "USB_DN")],
  lcsc="C7519", desc="USB-ESD-Schutz")

# JTAG-Debug (1.27mm, ESP32-S3; SWD gibt es bei Xtensa nicht — JTAG + USB-JTAG)
C("J12", "JTAG", "Connector_PinHeader_1.27mm:PinHeader_2x05_P1.27mm_Vertical_SMD",
  [("1", "3V3", "3V3"), ("2", "MTMS", "JTAG_MTMS"), ("3", "GND", "GND"),
   ("4", "MTCK", "JTAG_MTCK"), ("5", "GND", "GND"), ("6", "MTDO", "JTAG_MTDO"),
   ("7", "NC", ""), ("8", "MTDI", "JTAG_MTDI"), ("9", "GND", "GND"), ("10", "EN", "ESP_EN")],
  lcsc="C2962219", desc="JTAG 1.27mm")
C("J10", "UART0", "Connector_PinHeader_2.54mm:PinHeader_1x03_P2.54mm_Vertical",
  [("1", "TX", "UART0_TX"), ("2", "RX", "UART0_RX"), ("3", "GND", "GND")], lcsc="C2337")

# BT-Modul-Header (BTM875-B / CSR8675-Modul auf Adapter, analog out)
C("FB2", "600R@100MHz", "Inductor_SMD:L_0805_2012Metric",
  [("1", "~", "3V3"), ("2", "~", "3V3_BT")], lcsc="C1017")
C("C77", "22u", C0805, [("1", "~", "3V3_BT"), ("2", "~", "GND")], lcsc="C45783")
C("J13", "BT-Modul", "Connector_PinHeader_2.54mm:PinHeader_1x10_P2.54mm_Vertical",
  [("1", "3V3", "3V3_BT"), ("2", "GND", "GND"), ("3", "AOUT_L", "BT_L"),
   ("4", "AOUT_R", "BT_R"), ("5", "AGND", "AGND"), ("6", "MOD_TX", "BT_RXD"),
   ("7", "MOD_RX", "BT_TXD"), ("8", "STATUS", "BT_STATUS"), ("9", "PAIR", "BT_PAIR"),
   ("10", "EN", "BT_EN")], lcsc="C2337",
  desc="BTM875-B (CSR8675, aptX HD) auf Stiftleisten-Adapter")

# Button-Board-Anschluss (JST-PH 8-polig)
C("J14", "Button-PCB", "Connector_JST:JST_PH_B8B-PH-K_1x08_P2.00mm_Vertical",
  [("1", "5V", "5V"), ("2", "GND", "GND"), ("3", "3V3", "3V3"), ("4", "SDA", "SDA"),
   ("5", "SCL", "SCL"), ("6", "LED", "LED_DATA"), ("7", "BTN", "BTN_ADC"),
   ("8", "PWR", "BTN_PWR")], lcsc="C157935", desc="Zum Button-Board")

# Star-Ground: AGND-Insel trifft GND an genau einem Punkt (Net-Tie)
C("NT1", "NetTie", "NetTie:NetTie-2_SMD_Pad2.0mm",
  [("1", "A", "AGND"), ("2", "B", "GND")], desc="Sternpunkt Analog/Digital-Masse")

# Gruppen fuer die Schaltplan-Anordnung
GROUPS = [
    ("POWER INPUT + SOFT SWITCH",
     ["J1", "F1", "D1", "Q1", "R1", "D2", "Q2", "R2", "D3", "C90", "R3", "D4",
      "Q3", "R4", "R5", "R6", "J2", "J3", "J4", "D5", "R7", "D6", "R8", "D7", "R9",
      "R60", "R61"]),
    ("5V BUCK + 3V3 LDO",
     ["U1", "C2", "C3", "C4", "C5", "C6", "L1", "R10", "R11", "C7", "C8",
      "U2", "C9", "C10", "FB1", "C11", "R12", "R13", "C12"]),
    ("DSP ADAU1701",
     ["U3", "Q4", "R18", "C23", "C24", "C25", "Y1", "R15", "C13", "C14",
      "C15", "R16", "C16", "C17", "C18", "C19", "C21", "R17", "C26", "C27",
      "C28", "C29", "R63", "C30", "R19", "R14", "R62", "R20", "U4", "C31",
      "R21", "R22", "J5", "NT1"]),
    ("AUDIO IN + DAC OUT",
     ["J6", "C32", "R23", "C33", "R24", "C34", "R25", "C35", "R26", "C36", "C37",
      "R27", "C38", "C41", "R28", "C39", "C42", "R29", "C40", "C43"]),
    ("AMP FULLRANGE (U6 Master BTL)",
     ["U6", "R32", "C45", "C46", "C48", "R33", "R30", "R31", "C55", "C56", "C57",
      "C58", "C49", "C50", "L2", "L3", "L4", "L5", "C59", "C60", "C61", "C62",
      "C51", "C52", "C80", "C81", "C82", "C83", "C84", "J7", "J8"]),
    ("AMP SUB (U7 Slave PBTL)",
     ["U7", "R34", "R35", "C63", "C64", "R36", "C67", "C68", "C69", "C70",
      "C65", "C66", "L6", "L7", "C71", "C72", "J9"]),
    ("ESP32-S3 + USB + DEBUG",
     ["U8", "R37", "C73", "SW1", "SW2", "R38", "C74", "C75", "R41", "C76",
      "J11", "R39", "R40", "D8", "U9", "J12", "J10"]),
    ("BLUETOOTH + CONNECTORS",
     ["FB2", "C77", "J13", "J14"]),
]

if __name__ == "__main__":
    nets = {}
    for c in COMPS:
        for pad, name, net in c.pins:
            if net:
                nets.setdefault(net, []).append(f"{c.ref}.{pad}")
    print(f"{len(COMPS)} Bauteile, {len(nets)} Netze")
    single = {n: p for n, p in nets.items() if len(p) == 1}
    if single:
        print("WARNUNG - Netze mit nur 1 Pin:", single)
