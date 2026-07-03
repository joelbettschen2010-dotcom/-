"""Bauteil-, Pin- und Netzdefinitionen aller Schaltplanblaetter.

Pin-Tupel: (Name, Seite L/R, Typ, Netz)  — Typ-Kuerzel siehe ET.
Busse nutzen KiCad-Syntax NAME[msb:0]; Member heissen NAME<n>.
"""
from .sch import Comp

ET = {"pi": "power_in", "po": "power_out", "i": "input", "o": "output",
      "b": "bidirectional", "p": "passive"}


def P(name, side, t, net):
    return (name, side, ET[t], net)


def cols(specs, y0=45.0, gap=17.78):
    """Spaltenweise Auto-Platzierung: specs = [(x, [comps]), ...]."""
    placed = []
    for x, comps in specs:
        y = y0
        for c in comps:
            cy = round((y + c.height / 2) / 1.27) * 1.27
            placed.append((c, x, cy))
            y = cy + c.height / 2 + gap
    return placed


FP = "laptop_mainboard:"

# ----------------------------------------------------------------------------
# Blatt 3: CPU-Sockel AM5
# ----------------------------------------------------------------------------

def cpu_socket():
    pins = [
        # --- Versorgung (links oben) ---
        P("VDDCR_CPU", "L", "pi", "+VDDCR_CPU"),
        P("VDDCR_SOC", "L", "pi", "+VDDCR_SOC"),
        P("VDD_MISC_1V8", "L", "pi", "+1V8"),
        P("VDDIO_MEM_1V1", "L", "pi", "+1V1_MEM"),
        P("VDD_1V8", "L", "pi", "+1V8"),
        P("VDD_3V3", "L", "pi", "+3V3"),
        P("VDD_3V3_S5", "L", "pi", "+3V3_ALW"),
        P("VDD_BT_RTC", "L", "pi", "VDD_RTC"),
        P("GND", "L", "pi", "GND"),
        # --- Speicher Kanal A ---
        P("MA_CA[13:0]", "L", "o", "MA_CA[13:0]"),
        P("MA_CK_T", "L", "o", "MA_CK_T"),
        P("MA_CK_C", "L", "o", "MA_CK_C"),
        P("MA_CS[1:0]", "L", "o", "MA_CS[1:0]"),
        P("MA_DQ[31:0]", "L", "b", "MA_DQ[31:0]"),
        P("MA_DQS_T[3:0]", "L", "b", "MA_DQS_T[3:0]"),
        P("MA_DQS_C[3:0]", "L", "b", "MA_DQS_C[3:0]"),
        P("MA_ALERT#", "L", "i", "MA_ALERT#"),
        P("MA_RESET#", "L", "o", "MA_RESET#"),
        # --- Speicher Kanal B ---
        P("MB_CA[13:0]", "L", "o", "MB_CA[13:0]"),
        P("MB_CK_T", "L", "o", "MB_CK_T"),
        P("MB_CK_C", "L", "o", "MB_CK_C"),
        P("MB_CS[1:0]", "L", "o", "MB_CS[1:0]"),
        P("MB_DQ[31:0]", "L", "b", "MB_DQ[31:0]"),
        P("MB_DQS_T[3:0]", "L", "b", "MB_DQS_T[3:0]"),
        P("MB_DQS_C[3:0]", "L", "b", "MB_DQS_C[3:0]"),
        P("MB_ALERT#", "L", "i", "MB_ALERT#"),
        P("MB_RESET#", "L", "o", "MB_RESET#"),
        # --- SPI-BIOS / eSPI / SMBus (links unten) ---
        P("SPI_CS#", "L", "o", "BIOS_CS#"),
        P("SPI_CLK", "L", "o", "BIOS_CLK"),
        P("SPI_MOSI", "L", "o", "BIOS_MOSI"),
        P("SPI_MISO", "L", "i", "BIOS_MISO"),
        P("ESPI_IO[3:0]", "L", "b", "ESPI_IO[3:0]"),
        P("ESPI_CLK", "L", "o", "ESPI_CLK"),
        P("ESPI_CS#", "L", "o", "ESPI_CS#"),
        P("ESPI_ALERT#", "L", "i", "ESPI_ALERT#"),
        P("ESPI_RESET#", "L", "o", "ESPI_RESET#"),
        P("SMB_SCL", "L", "b", "SPD_HSCL"),
        P("SMB_SDA", "L", "b", "SPD_HSDA"),
        # --- Steuerung / SVI3 / RTC ---
        P("PWR_BTN#", "L", "i", "PM_PWR_BTN#"),
        P("SYS_RESET#", "L", "i", "SYS_RESET#"),
        P("PWROK", "L", "i", "SYS_PWROK"),
        P("SLP_S3#", "L", "o", "SLP_S3#"),
        P("SLP_S5#", "L", "o", "SLP_S5#"),
        P("PROCHOT#", "L", "b", "PROCHOT#"),
        P("THERMTRIP#", "L", "o", "THERMTRIP#"),
        P("SVI3_SVC", "L", "o", "SVI3_SVC"),
        P("SVI3_SVD", "L", "b", "SVI3_SVD"),
        P("SVI3_SVT", "L", "i", "SVI3_SVT"),
        P("X32K_XI", "L", "p", "X32K_XI"),
        P("X32K_XO", "L", "p", "X32K_XO"),
        P("X48M_X1", "L", "p", "X48M_X1"),
        P("X48M_OSC", "L", "p", "X48M_OSC"),
        # --- PCIe (rechts) ---
        P("P0_TXP[3:0]", "R", "o", "NVME1_TXP[3:0]"),
        P("P0_TXN[3:0]", "R", "o", "NVME1_TXN[3:0]"),
        P("P0_RXP[3:0]", "R", "i", "NVME1_RXP[3:0]"),
        P("P0_RXN[3:0]", "R", "i", "NVME1_RXN[3:0]"),
        P("P1_TXP[3:0]", "R", "o", "NVME2_TXP[3:0]"),
        P("P1_TXN[3:0]", "R", "o", "NVME2_TXN[3:0]"),
        P("P1_RXP[3:0]", "R", "i", "NVME2_RXP[3:0]"),
        P("P1_RXN[3:0]", "R", "i", "NVME2_RXN[3:0]"),
        P("P2_TXP[3:0]", "R", "o", "TB_TXP[3:0]"),
        P("P2_TXN[3:0]", "R", "o", "TB_TXN[3:0]"),
        P("P2_RXP[3:0]", "R", "i", "TB_RXP[3:0]"),
        P("P2_RXN[3:0]", "R", "i", "TB_RXN[3:0]"),
        P("G0_TXP", "R", "o", "LAN1_PE_TXP"),
        P("G0_TXN", "R", "o", "LAN1_PE_TXN"),
        P("G0_RXP", "R", "i", "LAN1_PE_RXP"),
        P("G0_RXN", "R", "i", "LAN1_PE_RXN"),
        P("G1_TXP", "R", "o", None),
        P("G1_TXN", "R", "o", None),
        P("G1_RXP", "R", "i", None),
        P("G1_RXN", "R", "i", None),
        P("G2_TXP", "R", "o", "WIFI_PE_TXP"),
        P("G2_TXN", "R", "o", "WIFI_PE_TXN"),
        P("G2_RXP", "R", "i", "WIFI_PE_RXP"),
        P("G2_RXN", "R", "i", "WIFI_PE_RXN"),
        P("GPP_CLKP[5:0]", "R", "o", "PE_CLKP[5:0]"),
        P("GPP_CLKN[5:0]", "R", "o", "PE_CLKN[5:0]"),
        P("CLKREQ0#", "R", "i", "NVME1_CLKREQ#"),
        P("CLKREQ1#", "R", "i", "NVME2_CLKREQ#"),
        P("CLKREQ2#", "R", "i", "TB_CLKREQ#"),
        P("CLKREQ3#", "R", "i", "LAN1_CLKREQ#"),
        P("CLKREQ4#", "R", "i", None),
        P("CLKREQ5#", "R", "i", "WIFI_CLKREQ#"),
        P("PE_WAKE#", "R", "i", "PE_WAKE#"),
        # --- USB SuperSpeed ---
        P("U3G2_1_TXP", "R", "o", "USBA1_TXP"),
        P("U3G2_1_TXN", "R", "o", "USBA1_TXN"),
        P("U3G2_1_RXP", "R", "i", "USBA1_RXP"),
        P("U3G2_1_RXN", "R", "i", "USBA1_RXN"),
        P("U3G2_2_TXP", "R", "o", "USBA2_TXP"),
        P("U3G2_2_TXN", "R", "o", "USBA2_TXN"),
        P("U3G2_2_RXP", "R", "i", "USBA2_RXP"),
        P("U3G2_2_RXN", "R", "i", "USBA2_RXN"),
        P("U3G2_3", "R", "o", None),
        P("U3G2_4", "R", "o", None),
        P("U3G1_5", "R", "o", None),
        P("USB2_P[8:1]", "R", "b", "USB2_P[8:1]"),
        P("USB2_N[8:1]", "R", "b", "USB2_N[8:1]"),
        # --- Display ---
        P("DP0_TXP[3:0]", "R", "o", "TBDP0_TXP[3:0]"),
        P("DP0_TXN[3:0]", "R", "o", "TBDP0_TXN[3:0]"),
        P("DP0_AUXP", "R", "b", "TBDP0_AUXP"),
        P("DP0_AUXN", "R", "b", "TBDP0_AUXN"),
        P("DP0_HPD", "R", "i", "TBDP0_HPD"),
        P("DP1_TXP[3:0]", "R", "o", "TBDP1_TXP[3:0]"),
        P("DP1_TXN[3:0]", "R", "o", "TBDP1_TXN[3:0]"),
        P("DP1_AUXP", "R", "b", "TBDP1_AUXP"),
        P("DP1_AUXN", "R", "b", "TBDP1_AUXN"),
        P("DP1_HPD", "R", "i", "TBDP1_HPD"),
        P("HDMI_D2P", "R", "o", "HDMI_D2P"),
        P("HDMI_D2N", "R", "o", "HDMI_D2N"),
        P("HDMI_D1P", "R", "o", "HDMI_D1P"),
        P("HDMI_D1N", "R", "o", "HDMI_D1N"),
        P("HDMI_D0P", "R", "o", "HDMI_D0P"),
        P("HDMI_D0N", "R", "o", "HDMI_D0N"),
        P("HDMI_CLKP", "R", "o", "HDMI_CLKP"),
        P("HDMI_CLKN", "R", "o", "HDMI_CLKN"),
        P("HDMI_SCL", "R", "b", "HDMI_SCL"),
        P("HDMI_SDA", "R", "b", "HDMI_SDA"),
        P("HDMI_CEC", "R", "b", "HDMI_CEC"),
        P("HDMI_HPD", "R", "i", "HDMI_HPD"),
        P("EDP_TXP[1:0]", "R", "o", "EDP_TXP[1:0]"),
        P("EDP_TXN[1:0]", "R", "o", "EDP_TXN[1:0]"),
        P("EDP_AUXP", "R", "b", "EDP_AUXP"),
        P("EDP_AUXN", "R", "b", "EDP_AUXN"),
        P("EDP_HPD", "R", "i", "EDP_HPD"),
        # --- Audio (HDA-Link) ---
        P("HDA_BCLK", "R", "o", "HDA_BCLK"),
        P("HDA_SYNC", "R", "o", "HDA_SYNC"),
        P("HDA_SDO", "R", "o", "HDA_SDO"),
        P("HDA_SDI", "R", "i", "HDA_SDI"),
        P("HDA_RST#", "R", "o", "HDA_RST#"),
    ]
    return Comp("XU1", "AM5_Socket_R5-8600G", pins, width=55.88,
                fp=FP + "AM5_Socket_LGA1718",
                desc="CPU-Sockel AM5 (LGA-1718) mit Ryzen 5 8600G, cTDP 35-45W; "
                     "Pinbelegung: docs/am5_pinmap.csv (WikiChip, Public Domain)",
                mpn="Foxconn LGA1718 + AMD 100-100001237BOX")


def sheet_cpu():
    xu1 = cpu_socket()
    y1 = Comp("Y1", "XTAL_32K768", [
        P("XI", "L", "p", "X32K_XI"), P("XO", "R", "p", "X32K_XO")],
        width=15.24, fp=FP + "XTAL_3215", desc="RTC-Quarz 32.768 kHz",
        mpn="Epson FC-135")
    y2 = Comp("Y2", "XTAL_48M", [
        P("X1", "L", "p", "X48M_X1"), P("OSC", "R", "p", "X48M_OSC")],
        width=15.24, fp=FP + "XTAL_3225", desc="Systemtakt-Quarz 48 MHz (X48M)",
        mpn="TXC 7M-48.000MAAJ-T")
    u19 = Comp("U19", "W25Q256JW_BIOS", [
        P("CS#", "L", "i", "BIOS_CS#"), P("CLK", "L", "i", "BIOS_CLK"),
        P("DI", "L", "i", "BIOS_MOSI"), P("DO", "R", "o", "BIOS_MISO"),
        P("WP#", "L", "i", "+1V8"), P("HOLD#", "R", "i", "+1V8"),
        P("VCC", "R", "pi", "+1V8"), P("GND", "R", "pi", "GND")],
        width=25.4, fp=FP + "SOIC8", desc="BIOS-SPI-Flash 32 MB, 1.8 V",
        mpn="Winbond W25Q256JWEIQ")
    notes = [
        "AM5-Sockel (LGA-1718) - CPU gesockelt, nicht geloetet.",
        "Ryzen 5 8600G: 6C/12T Zen4, Radeon 760M, cTDP 35-45W (Eco-Mode).",
        "Chipsatzloses Design (wie DeskMini X600): alle I/O direkt vom SoC.",
        "PCIe Gen4 Lane-Budget: 2x4 NVMe, x4 TB4, 2x1 (LAN, WLAN).",
        "Pinbelegung aller 1718 Pads: docs/am5_pinmap.csv",
        "(Quelle: WikiChip via Wikimedia Commons, Public Domain).",
        "Footprint-Pads tragen die echten Signal-Netznamen.",
    ]
    comps = cols([(120, [xu1]), (420, [y1, y2, u19])])
    return ("CPU AM5-Sockel + BIOS", "03_cpu_am5.kicad_sch", comps, notes)


# ----------------------------------------------------------------------------
# Blatt 1: USB-PD-Eingang, Laderegler, Systemschienen
# ----------------------------------------------------------------------------

def sheet_power():
    u1 = Comp("U1", "TPS65988DH", [
        P("VBUS1", "L", "b", "VBUS_C0"),
        P("VBUS2", "L", "b", "VBUS_C1"),
        P("PP_HV1", "R", "po", "+VBUS_IN"),
        P("PP_HV2", "L", "pi", "+5V"),
        P("C0_CC1", "L", "b", "C0_CC1"),
        P("C0_CC2", "L", "b", "C0_CC2"),
        P("C1_CC1", "L", "b", "C1_CC1"),
        P("C1_CC2", "L", "b", "C1_CC2"),
        P("VIN_3V3", "L", "pi", "+3V3_ALW"),
        P("LDO_3V3", "R", "po", "PD_LDO3V3"),
        P("I2C1_SCL", "R", "b", "PD_I2C_SCL"),
        P("I2C1_SDA", "R", "b", "PD_I2C_SDA"),
        P("I2C2_SCL", "R", "b", "TB_I2C_SCL"),
        P("I2C2_SDA", "R", "b", "TB_I2C_SDA"),
        P("SPI_CS#", "R", "o", "PDF_CS#"),
        P("SPI_CLK", "R", "o", "PDF_CLK"),
        P("SPI_MOSI", "R", "o", "PDF_MOSI"),
        P("SPI_MISO", "R", "i", "PDF_MISO"),
        P("HPD1", "R", "o", "TBDP0_HPD"),
        P("HPD2", "R", "o", "TBDP1_HPD"),
        P("GND", "L", "pi", "GND")],
        width=35.56, fp=FP + "BGA96_9x9",
        desc="Dual-USB-C-PD-Controller, 100W Sink Port C0 / 15W Source Port C1",
        mpn="TI TPS65988DHRSHR")
    u2 = Comp("U2", "W25Q80DV_PDCFG", [
        P("CS#", "L", "i", "PDF_CS#"), P("CLK", "L", "i", "PDF_CLK"),
        P("DI", "L", "i", "PDF_MOSI"), P("DO", "R", "o", "PDF_MISO"),
        P("VCC", "R", "pi", "PD_LDO3V3"), P("GND", "R", "pi", "GND")],
        width=25.4, fp=FP + "SOIC8", desc="PD-Konfigurations-Flash 1 MB",
        mpn="Winbond W25Q80DVSNIG")
    u3 = Comp("U3", "BQ25731", [
        P("VBUS", "L", "pi", "+VBUS_IN"),
        P("ACP", "L", "i", "+VBUS_IN"),
        P("ACN", "L", "i", "CHG_ACN"),
        P("CHRG_OK", "R", "o", "CHG_OK"),
        P("BTST1", "L", "p", "CHG_BST1"),
        P("HIDRV1", "L", "o", "G_Q1"),
        P("SW1", "L", "p", "CHG_SW1"),
        P("LODRV1", "L", "o", "G_Q2"),
        P("BTST2", "L", "p", "CHG_BST2"),
        P("HIDRV2", "L", "o", "G_Q4"),
        P("SW2", "L", "p", "CHG_SW2"),
        P("LODRV2", "L", "o", "G_Q3"),
        P("SRP", "R", "i", "CHG_SRP"),
        P("SRN", "R", "i", "+VBAT"),
        P("BAT", "R", "b", "+VBAT"),
        P("BATDRV", "R", "o", "G_Q5"),
        P("VSYS", "R", "po", "+VSYS"),
        P("CELL_BATPRES", "R", "i", "BAT_PRES#"),
        P("PROCHOT#", "R", "o", "PROCHOT#"),
        P("ILIM_HIZ", "R", "i", "CHG_ILIM"),
        P("REGN", "R", "po", "CHG_REGN"),
        P("SDA", "R", "b", "CHG_SMB_SDA"),
        P("SCL", "R", "b", "CHG_SMB_SCL"),
        P("GND", "R", "pi", "GND")],
        width=33.02, fp=FP + "QFN32_4x4",
        desc="Buck-Boost-NVDC-Laderegler, 3S-Akku (CELL=3S), 20V/5A Eingang",
        mpn="TI BQ25731RSNR")
    fets = []
    fet_net = {"Q1": ("CHG_ACN", "G_Q1", "CHG_SW1"),
               "Q2": ("CHG_SW1", "G_Q2", "GND"),
               "Q3": ("CHG_SW2", "G_Q3", "GND"),
               "Q4": ("+VSYS", "G_Q4", "CHG_SW2"),
               "Q5": ("+VSYS", "G_Q5", "CHG_SRP")}
    for q, (d, g, s) in fet_net.items():
        fets.append(Comp(q, "SiSS22DN", [
            P("D", "R", "p", d), P("G", "L", "i", g), P("S", "R", "p", s)],
            width=17.78, fp=FP + "PowerPAK_1212",
            desc="30V N-FET Ladewandler", mpn="Vishay SiSS22DN"))
    l1 = Comp("L1", "L_3u3_IHLP", [
        P("1", "L", "p", "CHG_SW1"), P("2", "R", "p", "CHG_SW2")],
        width=15.24, fp=FP + "IND_7x7",
        desc="Speicherdrossel 3.3 uH / 12 A", mpn="Vishay IHLP-2828CZ-3R3")
    rs = Comp("R1", "R_SHUNT_10m", [
        P("1", "L", "p", "CHG_SRP"), P("2", "R", "p", "+VBAT")],
        width=15.24, fp=FP + "R_2512",
        desc="Shunt 10 mOhm Ladestrommessung (BATFET Q5 -> Akku)",
        mpn="Bourns CSS2H-2512R-L100F")
    rs2 = Comp("R2", "R_SHUNT_10m_IN", [
        P("1", "L", "p", "+VBUS_IN"), P("2", "R", "p", "CHG_ACN")],
        width=15.24, fp=FP + "R_2512",
        desc="Shunt 10 mOhm Eingangsstrommessung (ACP/ACN)",
        mpn="Bourns CSS2H-2512R-L100F")
    jbat = Comp("J_BAT", "AkkuStecker_HP_3S", [
        P("BAT+_1", "L", "pi", "+VBAT"),
        P("BAT+_2", "L", "pi", "+VBAT"),
        P("SMB_SCL", "L", "b", "BAT_SMB_SCL"),
        P("SMB_SDA", "L", "b", "BAT_SMB_SDA"),
        P("THERM", "L", "i", "BAT_THERM"),
        P("PRES#", "L", "o", "BAT_PRES#"),
        P("BAT-_1", "L", "pi", "GND"),
        P("BAT-_2", "L", "pi", "GND")],
        width=27.94, fp=FP + "CONN_BAT8_2mm",
        desc="Akku-Stecker fuer HP-Originalakku 3S / 56 Wh (EliteBook 850 G7, "
             "HP L35766-005) - Pinout am Originalboard verifizieren!",
        mpn="HP-kompatibel (Reverse-Engineering noetig)")

    def buck8633(ref, en, pg, sw, rail, desc):
        return Comp(ref, "MPQ8633B", [
            P("VIN", "L", "pi", "+VSYS"), P("EN", "L", "i", en),
            P("PG", "R", "o", pg), P("BST", "L", "p", ref + "_BST"),
            P("SW", "R", "po", sw), P("FB", "R", "i", rail),
            P("VCC", "R", "p", ref + "_VCC"), P("GND", "L", "pi", "GND")],
            width=25.4, fp=FP + "QFN20_3x4", desc=desc, mpn="MPS MPQ8633BGLE")

    u4 = buck8633("U4", "EN_5V", "PG_5V", "SW_5V", "+5V",
                  "Synchronbuck 5V/8A Systemschiene")
    u5 = buck8633("U5", "EN_3V3", "PG_3V3", "SW_3V3", "+3V3",
                  "Synchronbuck 3.3V/8A Systemschiene")
    u9 = buck8633("U9", "+VSYS", "", "SW_3V3ALW", "+3V3_ALW",
                  "Synchronbuck 3.3V/2A Always-On (EC, PD)")

    def buck628(ref, en, sw, rail, desc):
        return Comp(ref, "TPS62823", [
            P("VIN", "L", "pi", "+5V"), P("EN", "L", "i", en),
            P("SW", "R", "po", sw), P("FB", "R", "i", rail),
            P("GND", "L", "pi", "GND")],
            width=22.86, fp=FP + "DFN6_2x2", desc=desc, mpn="TI TPS62823DMQR")

    u6 = buck628("U6", "EN_1V8", "SW_1V8", "+1V8", "Buck 1.8V/3A (BIOS, VDD_MISC)")
    u7 = buck628("U7", "TB_PWR_EN", "SW_1V05", "+1V05_TB", "Buck 1.05V/3A Thunderbolt")
    u8 = buck628("U8", "TB_PWR_EN", "SW_0V88", "+0V88_TB", "Buck 0.88V/3A Thunderbolt-Core")
    u18 = buck628("U18", "EN_VRM", "SW_1V1", "+1V1_MEM", "Buck 1.1V/4A VDDIO_MEM (SoC-Seite)")

    def ind(ref, a, b, val="1uH"):
        return Comp(ref, "L_" + val, [
            P("1", "L", "p", a), P("2", "R", "p", b)],
            width=15.24, fp=FP + "IND_4x4",
            desc=f"Speicherdrossel {val}", mpn="TDK SPM4020T-1R0M")

    l9 = ind("L9", "SW_5V", "+5V")
    l10 = ind("L10", "SW_3V3", "+3V3")
    l11 = ind("L11", "SW_3V3ALW", "+3V3_ALW")
    l12 = ind("L12", "SW_1V8", "+1V8", "470n")
    l13 = ind("L13", "SW_1V05", "+1V05_TB", "470n")
    l14 = ind("L14", "SW_0V88", "+0V88_TB", "470n")
    l15 = ind("L15", "SW_1V1", "+1V1_MEM", "470n")

    caps = []
    for i, rail in enumerate(["+VSYS", "+VSYS", "+VBUS_IN", "+5V"], start=1):
        caps.append(Comp(f"C{i}", "330u_Polymer", [
            P("+", "L", "pi", rail), P("-", "R", "pi", "GND")],
            width=15.24, fp=FP + "CAP_7343",
            desc="Polymer-Bulkkondensator 330uF/25V", mpn="Panasonic 25SVPF330M"))

    notes = [
        "Laden: 100W USB-PD (20V/5A) am Port TB0 (Position des HP-Barrel-Jacks)",
        "-> TPS65988 Pfad-FET -> BQ25731. NVDC: +VSYS = max(Akku, Wandler).",
        "Akku: HP-Original 3S Li-Ion 11.55V / 56Wh (EliteBook 850 G7).",
        "Q5/R1: BATFET + Lade-Shunt; R2: Eingangsstrom-Shunt (ACP/ACN).",
        "Alle Bucks: Feedback-Teiler und MLCC-Entkopplung auf Blatt 10.",
        "+3V3_ALW versorgt EC und PD-Controller auch im Soft-Off (S5).",
    ]
    comps = cols([
        (85, [u1, u2, jbat]),
        (215, [u3, l1, rs, rs2]),
        (330, fets + [l9, l10]),
        (445, [u4, u5, u9, u6]),
        (545, [u7, u8, u18, l11, l12, l13, l14, l15] + caps),
    ], gap=12.7)
    return ("USB-PD Eingang / Laderegler / Systemschienen",
            "01_power_pd.kicad_sch", comps, notes)


# ----------------------------------------------------------------------------
# Blatt 2: CPU-VRM (SVI3, 4+2 Phasen)
# ----------------------------------------------------------------------------

def sheet_vrm():
    u11 = Comp("U11", "MP2857_SVI3", [
        P("VCC3V3", "L", "pi", "+3V3"),
        P("VDRV5V", "L", "pi", "+5V"),
        P("EN", "L", "i", "EN_VRM"),
        P("SVI3_SVC", "L", "i", "SVI3_SVC"),
        P("SVI3_SVD", "L", "b", "SVI3_SVD"),
        P("SVI3_SVT", "L", "o", "SVI3_SVT"),
        P("VR_RDY", "R", "o", "VRM_PWRGD"),
        P("VR_HOT#", "R", "o", "PROCHOT#"),
        P("PWM_CPU1", "R", "o", "PWM_CPU1"),
        P("PWM_CPU2", "R", "o", "PWM_CPU2"),
        P("PWM_CPU3", "R", "o", "PWM_CPU3"),
        P("PWM_CPU4", "R", "o", "PWM_CPU4"),
        P("PWM_SOC1", "R", "o", "PWM_SOC1"),
        P("PWM_SOC2", "R", "o", "PWM_SOC2"),
        P("CS_CPU", "R", "i", "CS_CPU"),
        P("CS_SOC", "R", "i", "CS_SOC"),
        P("VSEN_CPU", "R", "i", "+VDDCR_CPU"),
        P("VSEN_SOC", "R", "i", "+VDDCR_SOC"),
        P("GND", "L", "pi", "GND")],
        width=33.02, fp=FP + "QFN40_5x5",
        desc="SVI3-Dual-Rail-VRM-Controller 4+2 Phasen", mpn="MPS MP2857GU")

    stages, inds = [], []
    for i in range(1, 5):
        stages.append(Comp(f"U{11+i}", "MP86957_Stage", [
            P("VIN", "L", "pi", "+VSYS"), P("VDRV", "L", "pi", "+5V"),
            P("PWM", "L", "i", f"PWM_CPU{i}"), P("BST", "L", "p", f"BST_CPU{i}"),
            P("SW", "R", "po", f"SW_CPU{i}"), P("CS", "R", "o", "CS_CPU"),
            P("GND", "L", "pi", "GND")],
            width=25.4, fp=FP + "QFN31_5x5",
            desc="Smart-Power-Stage 70A (VDDCR_CPU)", mpn="MPS MP86957GQVT"))
        inds.append(Comp(f"L{1+i}", "L_220n_15A", [
            P("1", "L", "p", f"SW_CPU{i}"), P("2", "R", "p", "+VDDCR_CPU")],
            width=15.24, fp=FP + "IND_7x7",
            desc="VRM-Drossel 220nH/40A", mpn="Vishay IHLP-4040DZ-R22M"))
    for i in range(1, 3):
        stages.append(Comp(f"U{15+i}", "MP86957_Stage", [
            P("VIN", "L", "pi", "+VSYS"), P("VDRV", "L", "pi", "+5V"),
            P("PWM", "L", "i", f"PWM_SOC{i}"), P("BST", "L", "p", f"BST_SOC{i}"),
            P("SW", "R", "po", f"SW_SOC{i}"), P("CS", "R", "o", "CS_SOC"),
            P("GND", "L", "pi", "GND")],
            width=25.4, fp=FP + "QFN31_5x5",
            desc="Smart-Power-Stage 70A (VDDCR_SOC)", mpn="MPS MP86957GQVT"))
        inds.append(Comp(f"L{5+i}", "L_220n_15A", [
            P("1", "L", "p", f"SW_SOC{i}"), P("2", "R", "p", "+VDDCR_SOC")],
            width=15.24, fp=FP + "IND_7x7",
            desc="VRM-Drossel 220nH/40A", mpn="Vishay IHLP-4040DZ-R22M"))

    caps = []
    for i, rail in enumerate(["+VDDCR_CPU"] * 3 + ["+VDDCR_SOC"] * 2, start=5):
        caps.append(Comp(f"C{i}", "470u_Polymer", [
            P("+", "L", "pi", rail), P("-", "R", "pi", "GND")],
            width=15.24, fp=FP + "CAP_7343",
            desc="Polymer-Ausgangskondensator 470uF/2.5V", mpn="Panasonic 2R5TPE470M9"))

    notes = [
        "VDDCR_CPU: 4 Phasen a 70A Smart-Stage - reichlich Reserve fuer 65W-APU",
        "im 35-45W Eco-Betrieb; VDDCR_SOC/GFX: 2 Phasen.",
        "SVI3-Bus (SVC/SVD/SVT) direkt vom Sockel; VDDIO_MEM 1.1V auf Blatt 1.",
        "MLCC-Arrays (47x 22uF 0402/0603) unter dem Sockel, siehe docs/layout.md.",
    ]
    comps = cols([
        (90, [u11] + caps[:2]),
        (230, stages[:3]),
        (340, stages[3:] ),
        (460, inds),
        (550, caps[2:]),
    ], gap=12.7)
    return ("CPU-Spannungsversorgung (VRM, SVI3)", "02_vrm_cpu.kicad_sch",
            comps, notes)


# ----------------------------------------------------------------------------
# Blatt 4: Arbeitsspeicher 2x DDR5 SO-DIMM
# ----------------------------------------------------------------------------

def sheet_memory():
    def dimm(ref, ch, addr):
        return Comp(ref, "SODIMM_DDR5", [
            P("VIN_BULK", "L", "pi", "+5V"),
            P("VIN_MGMT", "L", "pi", "+3V3"),
            P("CA[13:0]", "L", "i", f"{ch}_CA[13:0]"),
            P("CK_T", "L", "i", f"{ch}_CK_T"),
            P("CK_C", "L", "i", f"{ch}_CK_C"),
            P("CS[1:0]", "L", "i", f"{ch}_CS[1:0]"),
            P("DQ[31:0]", "L", "b", f"{ch}_DQ[31:0]"),
            P("DQS_T[3:0]", "L", "b", f"{ch}_DQS_T[3:0]"),
            P("DQS_C[3:0]", "L", "b", f"{ch}_DQS_C[3:0]"),
            P("ALERT#", "L", "o", f"{ch}_ALERT#"),
            P("RESET#", "L", "i", f"{ch}_RESET#"),
            P("HSCL", "L", "b", "SPD_HSCL"),
            P("HSDA", "L", "b", "SPD_HSDA"),
            P("HSA", "L", "i", addr),
            P("GND", "L", "pi", "GND")],
            width=35.56, fp=FP + "SODIMM_DDR5_262",
            desc="DDR5 SO-DIMM Sockel 262-polig (1DPC)",
            mpn="TE 2309413-1")
    j1 = dimm("J_MEM1", "MA", "GND")
    j2 = dimm("J_MEM2", "MB", "+3V3")
    notes = [
        "1 DIMM pro Kanal (1DPC) - beste Signalintegritaet, DDR5-5600 moeglich.",
        "DDR5-PMIC sitzt auf dem DIMM: Board liefert nur 5V Bulk + 3.3V Mgmt.",
        "SPD ueber I3C/HSCL+HSDA, Adresse per HSA-Strap (DIMM1=0, DIMM2=1).",
        "Routing: max. Skew +/-5 mil je Byte-Lane, 40 Ohm SE / 80 Ohm diff.",
    ]
    comps = cols([(150, [j1]), (350, [j2])])
    return ("Arbeitsspeicher 2x DDR5 SO-DIMM", "04_memory.kicad_sch",
            comps, notes)


# ----------------------------------------------------------------------------
# Blatt 5: Storage 2x M.2 NVMe + WLAN
# ----------------------------------------------------------------------------

def sheet_storage():
    def ssd(ref, pfx, clk, rail, en):
        return Comp(ref, "M2_M-Key_2280", [
            P("3V3", "L", "pi", rail),
            P("PERP[3:0]", "L", "i", f"{pfx}_TXP[3:0]"),
            P("PERN[3:0]", "L", "i", f"{pfx}_TXN[3:0]"),
            P("PETP[3:0]", "L", "o", f"{pfx}_RXP[3:0]"),
            P("PETN[3:0]", "L", "o", f"{pfx}_RXN[3:0]"),
            P("REFCLKP", "L", "i", f"PE_CLKP{clk}"),
            P("REFCLKN", "L", "i", f"PE_CLKN{clk}"),
            P("PERST#", "L", "i", f"{pfx}_PERST#"),
            P("CLKREQ#", "L", "b", f"{pfx}_CLKREQ#"),
            P("PEWAKE#", "L", "b", "PE_WAKE#"),
            P("DEVSLP", "L", "i", f"{pfx}_DEVSLP"),
            P("GND", "L", "pi", "GND")],
            width=35.56, fp=FP + "M2_M_2280",
            desc="M.2 M-Key 2280, PCIe 4.0 x4 NVMe", mpn="TE 2199230-4")

    j1 = ssd("J_SSD1", "NVME1", 0, "+3V3_SSD1", "EN_SSD1")
    j2 = ssd("J_SSD2", "NVME2", 1, "+3V3_SSD2", "EN_SSD2")

    def lsw(ref, en, out):
        return Comp(ref, "SY6280_LoadSW", [
            P("IN", "L", "pi", "+3V3"), P("EN", "L", "i", en),
            P("OUT", "R", "po", out), P("GND", "L", "pi", "GND")],
            width=20.32, fp=FP + "SOT23-5",
            desc="Lastschalter 3.3V/2A", mpn="Silergy SY6280AAC")

    u21 = lsw("U21", "EN_SSD1", "+3V3_SSD1")
    u22 = lsw("U22", "EN_SSD2", "+3V3_SSD2")

    jw = Comp("J_WIFI", "M2_E-Key_2230", [
        P("3V3", "L", "pi", "+3V3_ALW"),
        P("PERP0", "L", "i", "WIFI_PE_TXP"),
        P("PERN0", "L", "i", "WIFI_PE_TXN"),
        P("PETP0", "L", "o", "WIFI_PE_RXP"),
        P("PETN0", "L", "o", "WIFI_PE_RXN"),
        P("REFCLKP", "L", "i", "PE_CLKP5"),
        P("REFCLKN", "L", "i", "PE_CLKN5"),
        P("PERST#", "L", "i", "WIFI_PERST#"),
        P("CLKREQ#", "L", "b", "WIFI_CLKREQ#"),
        P("PEWAKE#", "L", "b", "PE_WAKE#"),
        P("USB_DP", "L", "b", "USB2_P7_C"),
        P("USB_DN", "L", "b", "USB2_N7_C"),
        P("W_DISABLE1#", "L", "i", "W_DISABLE1#"),
        P("W_DISABLE2#", "L", "i", "W_DISABLE2#"),
        P("GND", "L", "pi", "GND")],
        width=35.56, fp=FP + "M2_E_2230",
        desc="M.2 E-Key 2230 WLAN/BT (PCIe x1 + USB2)", mpn="TE 2199119-4")

    notes = [
        "SSD1 an P0 (x4 Gen4), SSD2 an P1 (x4 Gen4) - beide direkt vom SoC.",
        "Getrennt schaltbare 3.3V-Schienen fuer SSD-Powercycling durch EC.",
        "WLAN-Modul (z.B. MediaTek MT7925, Wi-Fi 7) an G2 + USB2-Port 7.",
        "REFCLK: 100 MHz vom SoC-GPP-Clocktree (PE_CLK0/1/5).",
    ]
    comps = cols([(140, [j1, u21]), (330, [j2, u22]), (500, [jw])])
    return ("Storage: 2x M.2 NVMe + WLAN", "05_storage.kicad_sch", comps, notes)
