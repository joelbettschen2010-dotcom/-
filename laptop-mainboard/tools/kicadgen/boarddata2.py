"""Blaetter 6-9: Thunderbolt/USB, Ethernet, Display, Audio/SD/EC."""
from .sch import Comp
from .boarddata import P, cols, FP


# ----------------------------------------------------------------------------
# Blatt 6: Thunderbolt 4 + USB
# ----------------------------------------------------------------------------

def sheet_usb_tb():
    u23 = Comp("U23", "JHL8540_TB4", [
        P("PCIE_RXP[3:0]", "L", "i", "TB_TXP[3:0]"),
        P("PCIE_RXN[3:0]", "L", "i", "TB_TXN[3:0]"),
        P("PCIE_TXP[3:0]", "L", "o", "TB_RXP[3:0]"),
        P("PCIE_TXN[3:0]", "L", "o", "TB_RXN[3:0]"),
        P("REFCLKP", "L", "i", "PE_CLKP2"),
        P("REFCLKN", "L", "i", "PE_CLKN2"),
        P("PERST#", "L", "i", "TB_PERST#"),
        P("CLKREQ#", "L", "b", "TB_CLKREQ#"),
        P("WAKE#", "L", "o", "PE_WAKE#"),
        P("DPIN0_LP[3:0]", "L", "i", "TBDP0_TXP[3:0]"),
        P("DPIN0_LN[3:0]", "L", "i", "TBDP0_TXN[3:0]"),
        P("DPIN0_AUXP", "L", "b", "TBDP0_AUXP"),
        P("DPIN0_AUXN", "L", "b", "TBDP0_AUXN"),
        P("DPIN1_LP[3:0]", "L", "i", "TBDP1_TXP[3:0]"),
        P("DPIN1_LN[3:0]", "L", "i", "TBDP1_TXN[3:0]"),
        P("DPIN1_AUXP", "L", "b", "TBDP1_AUXP"),
        P("DPIN1_AUXN", "L", "b", "TBDP1_AUXN"),
        P("C0_TX1P", "R", "o", "TB0_TX1P"),
        P("C0_TX1N", "R", "o", "TB0_TX1N"),
        P("C0_RX1P", "R", "i", "TB0_RX1P"),
        P("C0_RX1N", "R", "i", "TB0_RX1N"),
        P("C0_TX2P", "R", "o", "TB0_TX2P"),
        P("C0_TX2N", "R", "o", "TB0_TX2N"),
        P("C0_RX2P", "R", "i", "TB0_RX2P"),
        P("C0_RX2N", "R", "i", "TB0_RX2N"),
        P("C0_SBTX", "R", "o", "TB0_SBU1"),
        P("C0_SBRX", "R", "i", "TB0_SBU2"),
        P("C1_TX1P", "R", "o", "TB1_TX1P"),
        P("C1_TX1N", "R", "o", "TB1_TX1N"),
        P("C1_RX1P", "R", "i", "TB1_RX1P"),
        P("C1_RX1N", "R", "i", "TB1_RX1N"),
        P("C1_TX2P", "R", "o", "TB1_TX2P"),
        P("C1_TX2N", "R", "o", "TB1_TX2N"),
        P("C1_RX2P", "R", "i", "TB1_RX2P"),
        P("C1_RX2N", "R", "i", "TB1_RX2N"),
        P("C1_SBTX", "R", "o", "TB1_SBU1"),
        P("C1_SBRX", "R", "i", "TB1_SBU2"),
        P("I2C_SCL", "R", "b", "TB_I2C_SCL"),
        P("I2C_SDA", "R", "b", "TB_I2C_SDA"),
        P("FORCE_PWR", "R", "i", "TB_FORCE_PWR"),
        P("RESET#", "R", "i", "TB_RESET#"),
        P("VCC3V3", "R", "pi", "+3V3"),
        P("VCC1V05", "R", "pi", "+1V05_TB"),
        P("VCC0V88", "R", "pi", "+0V88_TB"),
        P("GND", "R", "pi", "GND")],
        width=45.72, fp=FP + "BGA_10x9_JHL8540",
        desc="Intel Thunderbolt-4-Controller, 2 Ports (Maple Ridge)",
        mpn="Intel JHL8540")

    def usbc_tb(ref, pfx, vbus):
        return Comp(ref, "USB-C_TB4", [
            P("VBUS", "L", "pi", vbus),
            P("CC1", "L", "b", pfx.replace("TB", "C") + "_CC1"),
            P("CC2", "L", "b", pfx.replace("TB", "C") + "_CC2"),
            P("DP", "L", "b", "USB2_P" + ("5" if pfx == "TB0" else "6")),
            P("DN", "L", "b", "USB2_N" + ("5" if pfx == "TB0" else "6")),
            P("TX1P", "L", "i", f"{pfx}_TX1P"),
            P("TX1N", "L", "i", f"{pfx}_TX1N"),
            P("RX1P", "L", "o", f"{pfx}_RX1P"),
            P("RX1N", "L", "o", f"{pfx}_RX1N"),
            P("TX2P", "L", "i", f"{pfx}_TX2P"),
            P("TX2N", "L", "i", f"{pfx}_TX2N"),
            P("RX2P", "L", "o", f"{pfx}_RX2P"),
            P("RX2N", "L", "o", f"{pfx}_RX2N"),
            P("SBU1", "L", "b", f"{pfx}_SBU1"),
            P("SBU2", "L", "b", f"{pfx}_SBU2"),
            P("GND", "L", "pi", "GND"),
            P("SHIELD", "L", "p", "GND")],
            width=30.48, fp=FP + "USBC_24",
            desc="USB-C-Buchse Thunderbolt 4 / USB4 40G", mpn="JAE DX07S024WJ1R350")

    jtb0 = usbc_tb("J_TB0", "TB0", "VBUS_C0")
    jtb1 = usbc_tb("J_TB1", "TB1", "VBUS_C1")

    def _unused_mux3220(ref, pfx, host):
        return Comp(ref, "HD3SS3220_Mux", [
            P("H_TXP", "L", "i", f"{host}_TXP"),
            P("H_TXN", "L", "i", f"{host}_TXN"),
            P("H_RXP", "L", "o", f"{host}_RXP"),
            P("H_RXN", "L", "o", f"{host}_RXN"),
            P("CC1", "R", "b", f"{pfx}_CC1"),
            P("CC2", "R", "b", f"{pfx}_CC2"),
            P("C_TX1P", "R", "o", f"{pfx}_TX1P"),
            P("C_TX1N", "R", "o", f"{pfx}_TX1N"),
            P("C_RX1P", "R", "i", f"{pfx}_RX1P"),
            P("C_RX1N", "R", "i", f"{pfx}_RX1N"),
            P("C_TX2P", "R", "o", f"{pfx}_TX2P"),
            P("C_TX2N", "R", "o", f"{pfx}_TX2N"),
            P("C_RX2P", "R", "i", f"{pfx}_RX2P"),
            P("C_RX2N", "R", "i", f"{pfx}_RX2N"),
            P("VBUS_DET", "R", "i", f"{pfx}_VBUS"),
            P("VCC33", "L", "pi", "+3V3"),
            P("GND", "L", "pi", "GND")],
            width=33.02, fp=FP + "QFN30_4x4",
            desc="USB-C CC-Logik + SS-Lane-Mux (10G)", mpn="TI HD3SS3220RNHR")


    def usbc_data(ref, pfx, usb2):
        return Comp(ref, "USB-C_10G", [
            P("VBUS", "L", "pi", f"{pfx}_VBUS"),
            P("CC1", "L", "b", f"{pfx}_CC1"),
            P("CC2", "L", "b", f"{pfx}_CC2"),
            P("DP", "L", "b", f"USB2_P{usb2}"),
            P("DN", "L", "b", f"USB2_N{usb2}"),
            P("TX1P", "L", "i", f"{pfx}_TX1P"),
            P("TX1N", "L", "i", f"{pfx}_TX1N"),
            P("RX1P", "L", "o", f"{pfx}_RX1P"),
            P("RX1N", "L", "o", f"{pfx}_RX1N"),
            P("TX2P", "L", "i", f"{pfx}_TX2P"),
            P("TX2N", "L", "i", f"{pfx}_TX2N"),
            P("RX2P", "L", "o", f"{pfx}_RX2P"),
            P("RX2N", "L", "o", f"{pfx}_RX2N"),
            P("GND", "L", "pi", "GND"),
            P("SHIELD", "L", "p", "GND")],
            width=30.48, fp=FP + "USBC_24",
            desc="USB-C-Buchse USB 3.2 Gen2 (Daten, 5V/3A)", mpn="JAE DX07S024WJ1R350")


    def usba(ref, pfx, usb2, vbus):
        return Comp(ref, "USB-A_3G1", [
            P("VBUS", "L", "pi", vbus),
            P("DP", "L", "b", f"USB2_P{usb2}"),
            P("DN", "L", "b", f"USB2_N{usb2}"),
            P("SSRXP", "L", "i", f"{pfx}_TXP"),
            P("SSRXN", "L", "i", f"{pfx}_TXN"),
            P("SSTXP", "L", "o", f"{pfx}_RXP"),
            P("SSTXN", "L", "o", f"{pfx}_RXN"),
            P("GND", "L", "pi", "GND"),
            P("SHELL", "L", "p", "GND")],
            width=27.94, fp=FP + "USBA3_TH",
            desc="USB-A-Buchse USB 3.2 Gen1", mpn="Foxconn UEA1112C-4HK1-4F")

    ja1 = usba("J_USBA1", "USBA1", 1, "+5V_USBA1")
    ja2 = usba("J_USBA2", "USBA2", 2, "+5V_USBA2")

    def vsw(ref, en, out):
        return Comp(ref, "SY6280_5V_SW", [
            P("IN", "L", "pi", "+5V"), P("EN", "L", "i", en),
            P("OUT", "R", "po", out), P("GND", "L", "pi", "GND")],
            width=20.32, fp=FP + "SOT23-5",
            desc="VBUS-Lastschalter 5V/2.1A mit Strombegrenzung",
            mpn="Silergy SY6280AAC")

    u32 = vsw("U32", "EN_USBA1", "+5V_USBA1")
    u33 = vsw("U33", "EN_USBA2", "+5V_USBA2")

    notes = [
        "TB0/TB1 = die beiden nebeneinanderliegenden USB-C-Ports mittig an",
        "der rechten Kante (wie beim Original). Beide Thunderbolt 4 ueber",
        "JHL8540, je 15W Source (TPS65988). Geladen wird am separaten",
        "Ladeport J_PWR an der Barrel-Jack-Position (Blatt 7).",
        "JHL8540: PCIe Gen3 x4 Uplink + 2x DP1.4-Sink vom SoC.",
        "ESD-Schutz und USB2-Chokes: Blatt 10 (Kleinteile).",
    ]
    comps = cols([
        (100, [u23]),
        (260, [jtb0, jtb1]),
        (420, [ja1, ja2, u32, u33]),
    ], gap=12.7)
    return ("Thunderbolt 4 + USB", "06_usb_tb4.kicad_sch", comps, notes)


# ----------------------------------------------------------------------------
# Blatt 7: Dedizierter USB-C-Ladeport (an der Barrel-Jack-Position)
# ----------------------------------------------------------------------------

def sheet_ladeport():
    u44 = Comp("U44", "TPS65987D_Lader", [
        P("VBUS", "L", "b", "VBUS_PWR"),
        P("CC1", "L", "b", "PWR_CC1"),
        P("CC2", "L", "b", "PWR_CC2"),
        P("PP_HV", "R", "po", "+VBUS_IN"),
        P("VIN_3V3", "L", "pi", "+3V3_ALW"),
        P("LDO_3V3", "R", "po", "PD2_LDO3V3"),
        P("I2C_SCL", "R", "b", "PD_I2C_SCL"),
        P("I2C_SDA", "R", "b", "PD_I2C_SDA"),
        P("SPI_CS#", "R", "o", "PDF2_CS#"),
        P("SPI_CLK", "R", "o", "PDF2_CLK"),
        P("SPI_MOSI", "R", "o", "PDF2_MOSI"),
        P("SPI_MISO", "R", "i", "PDF2_MISO"),
        P("GPIO_CHGOK", "R", "o", "PWR_SRC_OK"),
        P("GND", "L", "pi", "GND")],
        width=35.56, fp=FP + "BGA96_9x9",
        desc="Single-USB-C-PD-Controller, 100W-Sink mit internem Pfad-FET "
             "-> Ladeeingang +VBUS_IN (Dead-Battery-faehig)",
        mpn="TI TPS65987DDHRSHR")
    u45 = Comp("U45", "W25Q80DV_PD2CFG", [
        P("CS#", "L", "i", "PDF2_CS#"), P("CLK", "L", "i", "PDF2_CLK"),
        P("DI", "L", "i", "PDF2_MOSI"), P("DO", "R", "o", "PDF2_MISO"),
        P("VCC", "R", "pi", "PD2_LDO3V3"), P("GND", "R", "pi", "GND")],
        width=25.4, fp=FP + "SOIC8", desc="Ladeport-Konfigurations-Flash 1 MB",
        mpn="Winbond W25Q80DVSNIG")
    jpwr = Comp("J_PWR", "USB-C_Ladeport", [
        P("VBUS", "L", "pi", "VBUS_PWR"),
        P("CC1", "L", "b", "PWR_CC1"),
        P("CC2", "L", "b", "PWR_CC2"),
        P("DP", "L", "b", "USB2_P3"),
        P("DN", "L", "b", "USB2_N3"),
        P("GND", "L", "pi", "GND"),
        P("SHIELD", "L", "p", "GND")],
        width=30.48, fp=FP + "USBC_24",
        desc="USB-C-Ladebuchse 100W (an der Barrel-Jack-Position des "
             "850 G7), zusaetzlich USB-2.0-Daten", mpn="JAE DX07S024WJ1R350")

    notes = [
        "Ersetzt den HP-Barrel-Jack an exakt derselben Gehaeuseposition",
        "(rechts hinten) durch USB-C mit 100W USB-PD (20V/5A).",
        "Eigener PD-Controller TPS65987D, Dead-Battery-Boot faehig;",
        "die beiden mittigen USB-C-Ports bleiben reine TB4-Ports (Blatt 6).",
        "SuperSpeed-Pins der Buchse unbeschaltet - Port ist Laden + USB 2.0.",
    ]
    comps = cols([
        (120, [u44]),
        (300, [u45]),
        (460, [jpwr]),
    ])
    return ("USB-C-Ladeport 100W", "07_ladeport.kicad_sch", comps, notes)


# ----------------------------------------------------------------------------
# Blatt 8: Display (eDP intern, HDMI 2.1, Backlight)
# ----------------------------------------------------------------------------

def sheet_display():
    jh = Comp("J_HDMI", "HDMI-A", [
        P("D2P", "L", "i", "HDMI_D2P"), P("D2N", "L", "i", "HDMI_D2N"),
        P("D1P", "L", "i", "HDMI_D1P"), P("D1N", "L", "i", "HDMI_D1N"),
        P("D0P", "L", "i", "HDMI_D0P"), P("D0N", "L", "i", "HDMI_D0N"),
        P("CLKP", "L", "i", "HDMI_CLKP"), P("CLKN", "L", "i", "HDMI_CLKN"),
        P("SCL", "L", "b", "HDMI_SCL_C"), P("SDA", "L", "b", "HDMI_SDA_C"),
        P("CEC", "L", "b", "HDMI_CEC_C"), P("HPD", "L", "o", "HDMI_HPD_C"),
        P("+5V", "L", "pi", "HDMI_5V"), P("GND", "L", "pi", "GND"),
        P("SHIELD", "L", "p", "GND")],
        width=27.94, fp=FP + "HDMI_A",
        desc="HDMI-Typ-A-Buchse (HDMI 2.1 TMDS/FRL vom SoC)",
        mpn="Molex 2086581051")
    u36 = Comp("U36", "TPD12S016", [
        P("SCL_A", "L", "b", "HDMI_SCL"), P("SDA_A", "L", "b", "HDMI_SDA"),
        P("CEC_A", "L", "b", "HDMI_CEC"), P("HPD_A", "L", "o", "HDMI_HPD"),
        P("SCL_B", "R", "b", "HDMI_SCL_C"), P("SDA_B", "R", "b", "HDMI_SDA_C"),
        P("CEC_B", "R", "b", "HDMI_CEC_C"), P("HPD_B", "R", "i", "HDMI_HPD_C"),
        P("5V_OUT", "R", "po", "HDMI_5V"), P("LS_OE", "L", "i", "+3V3"),
        P("VCCA", "L", "pi", "+3V3"), P("VCC5V", "L", "pi", "+5V"),
        P("GND", "L", "pi", "GND")],
        width=30.48, fp=FP + "TSSOP24",
        desc="HDMI-Companion: Pegelwandler DDC/CEC + HPD + 5V-Schalter + ESD",
        mpn="TI TPD12S016PWR")
    je = Comp("J_EDP", "eDP_40pol", [
        P("LANE0P", "L", "i", "EDP_TXP0"), P("LANE0N", "L", "i", "EDP_TXN0"),
        P("LANE1P", "L", "i", "EDP_TXP1"), P("LANE1N", "L", "i", "EDP_TXN1"),
        P("AUXP", "L", "b", "EDP_AUXP"), P("AUXN", "L", "b", "EDP_AUXN"),
        P("HPD", "L", "o", "EDP_HPD"), P("VLCD", "L", "pi", "+VLCD"),
        P("BL_PWR", "L", "pi", "+VBL"), P("BL_EN", "L", "i", "EDP_BL_EN"),
        P("BL_PWM", "L", "i", "EDP_BL_PWM"), P("GND", "L", "pi", "GND")],
        width=30.48, fp=FP + "FPC40_05",
        desc="eDP-1.4-Panelstecker 40-polig (15.6\\\" 2560x1440 IPS)",
        mpn="I-PEX 20455-040E-12")
    u37 = Comp("U37", "SY6280_VLCD", [
        P("IN", "L", "pi", "+3V3"), P("EN", "L", "i", "EN_VLCD"),
        P("OUT", "R", "po", "+VLCD"), P("GND", "L", "pi", "GND")],
        width=20.32, fp=FP + "SOT23-5",
        desc="Panel-Lastschalter 3.3V/2A", mpn="Silergy SY6280AAC")
    u38 = Comp("U38", "MP3389_Backlight", [
        P("VIN", "L", "pi", "+VSYS"), P("EN", "L", "i", "EDP_BL_EN"),
        P("PWM", "L", "i", "EDP_BL_PWM"), P("SW", "R", "po", "BL_SW"),
        P("OVP", "R", "i", "+VBL"), P("ISET", "R", "p", "BL_ISET"),
        P("GND", "L", "pi", "GND")],
        width=25.4, fp=FP + "TSSOP16EP",
        desc="Backlight-Boost-Treiber, 6 Straenge", mpn="MPS MP3389EF")
    l8 = Comp("L8", "L_10u_Boost", [
        P("1", "L", "p", "+VSYS"), P("2", "R", "p", "BL_SW")],
        width=15.24, fp=FP + "IND_4x4",
        desc="Boost-Drossel 10uH", mpn="TDK SPM5030T-100M")
    d1 = Comp("D1", "SS34_Schottky", [
        P("A", "L", "p", "BL_SW"), P("K", "R", "p", "+VBL")],
        width=15.24, fp=FP + "SMA_DIODE",
        desc="Schottky-Diode 40V/3A", mpn="Vishay SS3P4")
    jcam = Comp("J_CAM", "Webcam_Conn", [
        P("3V3", "L", "pi", "+3V3"), P("USB_DP", "L", "b", "USB2_P8_C"),
        P("USB_DN", "L", "b", "USB2_N8_C"), P("DMIC_CLK", "L", "o", "DMIC_CLK"),
        P("DMIC_DAT", "L", "i", "DMIC_DAT"), P("GND", "L", "pi", "GND")],
        width=27.94, fp=FP + "FPC6_05",
        desc="Webcam-/Mikrofon-Stecker (USB2 + DMIC)", mpn="Molex 5051100692")

    notes = [
        "Internes Display: eDP 1.4, 2 Lanes - reicht fuer 2560x1440@60.",
        "HDMI-TMDS-Paare direkt vom SoC (100 Ohm diff), DDC/CEC via TPD12S016.",
        "Backlight: Boost auf ~19V, Helligkeit ueber EC-PWM (EDP_BL_PWM).",
        "Display-Budget SoC: eDP + HDMI + 2x DP-Sink im TB-Controller = 4 Pipes.",
    ]
    comps = cols([
        (110, [jh, u36]),
        (280, [je, u37, jcam]),
        (460, [u38, l8, d1]),
    ])
    return ("Display: eDP + HDMI 2.1 + Backlight", "08_display.kicad_sch",
            comps, notes)


# ----------------------------------------------------------------------------
# Blatt 9: Audio, SD-Kartenleser, Embedded Controller
# ----------------------------------------------------------------------------

def sheet_audio_sd_ec():
    u39 = Comp("U39", "ALC256_Codec", [
        P("HDA_BCLK", "L", "i", "HDA_BCLK"),
        P("HDA_SYNC", "L", "i", "HDA_SYNC"),
        P("HDA_SDO", "L", "i", "HDA_SDO"),
        P("HDA_SDI", "L", "o", "HDA_SDI"),
        P("HDA_RST#", "L", "i", "HDA_RST#"),
        P("DMIC_CLK", "L", "o", "DMIC_CLK"),
        P("DMIC_DAT", "L", "i", "DMIC_DAT"),
        P("HP_L", "R", "o", "AUX_HPL"),
        P("HP_R", "R", "o", "AUX_HPR"),
        P("MIC_IN", "R", "i", "AUX_MIC"),
        P("JD", "R", "i", "AUX_JD"),
        P("SPK_LP", "R", "o", "SPK_LP"),
        P("SPK_LN", "R", "o", "SPK_LN"),
        P("SPK_RP", "R", "o", "SPK_RP"),
        P("SPK_RN", "R", "o", "SPK_RN"),
        P("AVDD_3V3", "L", "pi", "+3V3"),
        P("GND", "L", "pi", "GND")],
        width=33.02, fp=FP + "QFN48_6x6",
        desc="HD-Audio-Codec mit 2W-Klasse-D-Verstaerker", mpn="Realtek ALC256-CG")
    jaux = Comp("J_AUX", "Klinke_3V5_TRRS", [
        P("TIP_L", "L", "i", "AUX_HPL"),
        P("RING1_R", "L", "i", "AUX_HPR"),
        P("RING2_GND", "L", "pi", "GND"),
        P("SLEEVE_MIC", "L", "o", "AUX_MIC"),
        P("DETECT", "L", "o", "AUX_JD"),
        P("SHIELD", "L", "p", "GND")],
        width=27.94, fp=FP + "TRRS_35",
        desc="3.5mm-Kombiklinke (Kopfhoerer + Headset-Mikrofon)",
        mpn="CUI SJ-43516-SMT-TR")
    jspl = Comp("J_SPKL", "Lautsprecher_L", [
        P("+", "L", "i", "SPK_LP"), P("-", "L", "i", "SPK_LN")],
        width=22.86, fp=FP + "CONN_SPK2",
        desc="Lautsprecher links 4 Ohm/2W", mpn="JST BM02B-SRSS-TB")
    jspr = Comp("J_SPKR", "Lautsprecher_R", [
        P("+", "L", "i", "SPK_RP"), P("-", "L", "i", "SPK_RN")],
        width=22.86, fp=FP + "CONN_SPK2",
        desc="Lautsprecher rechts 4 Ohm/2W", mpn="JST BM02B-SRSS-TB")

    u41 = Comp("U41", "IT5570E_EC", [
        P("ESPI_IO[3:0]", "L", "b", "ESPI_IO[3:0]"),
        P("ESPI_CLK", "L", "i", "ESPI_CLK"),
        P("ESPI_CS#", "L", "i", "ESPI_CS#"),
        P("ESPI_ALERT#", "L", "o", "ESPI_ALERT#"),
        P("ESPI_RESET#", "L", "i", "ESPI_RESET#"),
        P("KSI[7:0]", "L", "i", "KSI[7:0]"),
        P("KSO[17:0]", "L", "o", "KSO[17:0]"),
        P("TP_SCL", "L", "b", "TP_SCL"),
        P("TP_SDA", "L", "b", "TP_SDA"),
        P("TP_INT#", "L", "i", "TP_INT#"),
        P("SMB_BAT_SCL", "L", "b", "BAT_SMB_SCL"),
        P("SMB_BAT_SDA", "L", "b", "BAT_SMB_SDA"),
        P("SMB_CHG_SCL", "L", "b", "CHG_SMB_SCL"),
        P("SMB_CHG_SDA", "L", "b", "CHG_SMB_SDA"),
        P("PD_I2C_SCL", "L", "b", "PD_I2C_SCL"),
        P("PD_I2C_SDA", "L", "b", "PD_I2C_SDA"),
        P("ADC0_THCPU", "L", "i", "TH_CPU"),
        P("ADC1_THVRM", "L", "i", "TH_VRM"),
        P("ADC2_THCHG", "L", "i", "TH_CHG"),
        P("BAT_THERM", "L", "i", "BAT_THERM"),
        P("PWRBTN_IN#", "R", "i", "PWRBTN_IN#"),
        P("LID#", "R", "i", "LID#"),
        P("PM_PWR_BTN#", "R", "o", "PM_PWR_BTN#"),
        P("SYS_PWROK", "R", "o", "SYS_PWROK"),
        P("SYS_RESET#", "R", "o", "SYS_RESET#"),
        P("SLP_S3#", "R", "i", "SLP_S3#"),
        P("SLP_S5#", "R", "i", "SLP_S5#"),
        P("PROCHOT#", "R", "b", "PROCHOT#"),
        P("THERMTRIP#", "R", "i", "THERMTRIP#"),
        P("CHG_OK", "R", "i", "CHG_OK"),
        P("EN_5V", "R", "o", "EN_5V"),
        P("EN_3V3", "R", "o", "EN_3V3"),
        P("EN_1V8", "R", "o", "EN_1V8"),
        P("EN_VRM", "R", "o", "EN_VRM"),
        P("EN_VLCD", "R", "o", "EN_VLCD"),
        P("EN_SSD1", "R", "o", "EN_SSD1"),
        P("EN_SSD2", "R", "o", "EN_SSD2"),
        P("EN_USBA1", "R", "o", "EN_USBA1"),
        P("EN_USBA2", "R", "o", "EN_USBA2"),
        P("TB_PWR_EN", "R", "o", "TB_PWR_EN"),
        P("TB_FORCE_PWR", "R", "o", "TB_FORCE_PWR"),
        P("TB_RESET#", "R", "o", "TB_RESET#"),
        P("PE_RST_SSD1#", "R", "o", "NVME1_PERST#"),
        P("PE_RST_SSD2#", "R", "o", "NVME2_PERST#"),
        P("PE_RST_TB#", "R", "o", "TB_PERST#"),
        P("PE_RST_WIFI#", "R", "o", "WIFI_PERST#"),
        P("SSD1_DEVSLP", "R", "o", "NVME1_DEVSLP"),
        P("SSD2_DEVSLP", "R", "o", "NVME2_DEVSLP"),
        P("W_DISABLE1#", "R", "o", "W_DISABLE1#"),
        P("W_DISABLE2#", "R", "o", "W_DISABLE2#"),
        P("VRM_PWRGD", "R", "i", "VRM_PWRGD"),
        P("PG_5V", "R", "i", "PG_5V"),
        P("PG_3V3", "R", "i", "PG_3V3"),
        P("FAN1_PWM", "R", "o", "FAN1_PWM"),
        P("FAN1_TACH", "R", "i", "FAN1_TACH"),
        P("FAN2_PWM", "R", "o", "FAN2_PWM"),
        P("FAN2_TACH", "R", "i", "FAN2_TACH"),
        P("BL_EN", "R", "o", "EDP_BL_EN"),
        P("BL_PWM", "R", "o", "EDP_BL_PWM"),
        P("KB_BL_CTL", "R", "o", "KB_BL_CTL"),
        P("ECSPI_CS#", "R", "o", "ECF_CS#"),
        P("ECSPI_CLK", "R", "o", "ECF_CLK"),
        P("ECSPI_MOSI", "R", "o", "ECF_MOSI"),
        P("ECSPI_MISO", "R", "i", "ECF_MISO"),
        P("VCC3V3_ALW", "L", "pi", "+3V3_ALW"),
        P("GND", "L", "pi", "GND")],
        width=43.18, fp=FP + "LQFP128_14x14",
        desc="Embedded Controller: Tastatur, Power-Sequencing, Akku, Luefter",
        mpn="ITE IT5570E-128")
    u42 = Comp("U42", "W25Q128_ECFW", [
        P("CS#", "L", "i", "ECF_CS#"), P("CLK", "L", "i", "ECF_CLK"),
        P("DI", "L", "i", "ECF_MOSI"), P("DO", "R", "o", "ECF_MISO"),
        P("VCC", "R", "pi", "+3V3_ALW"), P("GND", "R", "pi", "GND")],
        width=25.4, fp=FP + "SOIC8", desc="EC-Firmware-Flash 16 MB",
        mpn="Winbond W25Q128JVSIQ")
    jkb = Comp("J_KB", "Tastatur_FPC30", [
        P("KSI[7:0]", "L", "o", "KSI[7:0]"),
        P("KSO[17:0]", "L", "i", "KSO[17:0]"),
        P("PWRBTN#", "L", "o", "PWRBTN_IN#"),
        P("BL+", "L", "pi", "+5V"),
        P("BL-", "L", "i", "KB_BL_CTL"),
        P("GND", "L", "pi", "GND")],
        width=30.48, fp=FP + "FPC30_05",
        desc="FPC fuer HP-EliteBook-850-G7-Tastatur (Matrix-Zuordnung am "
             "Original ausmessen!)",
        mpn="HP-kompatibel, FPC 0.5mm")
    jtp = Comp("J_TP", "Touchpad_FPC8", [
        P("3V3", "L", "pi", "+3V3"), P("SCL", "L", "b", "TP_SCL"),
        P("SDA", "L", "b", "TP_SDA"), P("INT#", "L", "o", "TP_INT#"),
        P("GND", "L", "pi", "GND")],
        width=27.94, fp=FP + "FPC8_05",
        desc="FPC fuer HP-Clickpad (I2C-HID, Pinout am Original verifizieren)",
        mpn="HP-kompatibel, FPC 0.5mm")

    def fan(ref, n):
        return Comp(ref, "Luefter_4pol", [
            P("+5V", "L", "pi", "+5V"), P("PWM", "L", "i", f"FAN{n}_PWM"),
            P("TACH", "L", "o", f"FAN{n}_TACH"), P("GND", "L", "pi", "GND")],
            width=25.4, fp=FP + "CONN_FAN4",
            desc="Luefteranschluss 4-polig 5V PWM", mpn="JST BM04B-SRSS-TB")

    jf1, jf2 = fan("J_FAN1", 1), fan("J_FAN2", 2)
    bt1 = Comp("BT1", "ML1220", [
        P("+", "L", "po", "VDD_RTC"), P("-", "R", "pi", "GND")],
        width=20.32, fp=FP + "COIN_ML1220",
        desc="RTC-Pufferzelle ML1220 (wiederaufladbar)", mpn="Panasonic ML-1220/F1AN")

    def ntc(ref, net):
        return Comp(ref, "NTC_10k", [
            P("1", "L", "p", net), P("2", "R", "p", "GND")],
            width=15.24, fp=FP + "R_0603",
            desc="NTC-Thermistor 10k (Temperatursensor)", mpn="Murata NCP18XH103F03RB")

    rt1, rt2, rt3 = ntc("RT1", "TH_CPU"), ntc("RT2", "TH_VRM"), ntc("RT3", "TH_CHG")
    jlid = Comp("J_LID", "Hallsensor_Lid", [
        P("3V3", "L", "pi", "+3V3_ALW"), P("LID#", "L", "o", "LID#"),
        P("GND", "L", "pi", "GND")],
        width=25.4, fp=FP + "FPC6_05",
        desc="Deckelsensor (Hall) 3-polig", mpn="Molex 5051100392")

    notes = [
        "EC IT5570E: Power-Sequencing S5->S0, Tastaturmatrix 8x18,",
        "Akku-/Lader-SMBus, Luefterregelung, Deckel- und Power-Taste.",
        "Sequenz: +3V3_ALW -> EN_5V/EN_3V3 -> EN_1V8 -> EN_VRM -> SYS_PWROK.",
        "Audio: ALC256 am SoC-HDA-Link; Klinke links wie beim 850 G7.",
        "HP-FPC-Pinouts (Tastatur/Touchpad/Akku): docs/elitebook.md.",
    ]
    comps = cols([
        (100, [u39, jaux, jspl, jspr]),
        (240, [bt1, rt1, rt2, rt3]),
        (400, [u41]),
        (540, [u42, jkb, jtp, jf1, jf2, jlid]),
    ], gap=11.43)
    return ("Audio / Embedded Controller", "09_audio_sd_ec.kicad_sch",
            comps, notes)


# ----------------------------------------------------------------------------
# Blatt 10: Kleinteile - Entkopplung, ESD, Filter, Straps
# ----------------------------------------------------------------------------

def sheet_kleinteile():
    comps_list = []

    def cap(ref, val, rail, fp_name, desc):
        return Comp(ref, val, [
            P("1", "L", "pi", rail), P("2", "R", "pi", "GND")],
            width=15.24, fp=FP + fp_name, desc=desc,
            mpn={"22u": "Murata GRM155R60J226ME15", "10u": "Murata GRM188R61A106KE69",
                 "1u": "Murata GRM155R61A105KE15", "100n": "Murata GRM155R71C104KA88"
                 }[val.split("_")[0].replace("uF", "u").replace("nF", "n")])

    # MLCC-Baenke: VDDCR (unter dem Sockel, B.Cu)
    for i in range(12):
        comps_list.append(cap(f"C{20+i}", "22u_0402", "+VDDCR_CPU", "R_0603",
                              "MLCC 22uF/6.3V 0402 - VDDCR_CPU-Bank unter Sockel"))
    for i in range(6):
        comps_list.append(cap(f"C{32+i}", "22u_0402", "+VDDCR_SOC", "R_0603",
                              "MLCC 22uF/6.3V 0402 - VDDCR_SOC-Bank unter Sockel"))
    # VDDIO / Schienen-Bulk
    rails = ["+1V1_MEM", "+1V1_MEM", "+1V8", "+3V3", "+3V3", "+5V", "+5V",
             "+1V05_TB", "+0V88_TB", "+3V3_ALW"]
    for i, r in enumerate(rails):
        comps_list.append(cap(f"C{40+i}", "10u_0603", r, "R_0603",
                              f"MLCC 10uF/10V 0603 - Schiene {r}"))
    # 100n-Entkopplung pro IC (repraesentativ, 1 Symbol = 4 Stk. am IC)
    for i, ic in enumerate(["U1 PD", "U3 Lader", "U11 VRM", "U19 BIOS",
                            "U23 TB4", "U34 LAN", "U39 Codec", "U41 EC",
                            "U42 ECFlash", "Sockel-Rand"]):
        comps_list.append(cap(f"C{52+i}", "100n_0402", "+3V3", "R_0603",
                              f"MLCC 100nF 0402 x4 - Entkopplung {ic}"))

    def esd4(ref, nets, port):
        return Comp(ref, "TPD4E02B04", [
            P("IO1", "L", "b", nets[0]), P("IO2", "L", "b", nets[1]),
            P("IO3", "L", "b", nets[2]), P("IO4", "L", "b", nets[3]),
            P("GND", "R", "pi", "GND")],
            width=25.4, fp=FP + "DFN6_2x2",
            desc=f"ESD-Array 4-Kanal 0.25pF - {port}", mpn="TI TPD4E02B04DQAR")

    comps_list += [
        esd4("U50", ["TB0_TX1P", "TB0_TX1N", "TB0_RX1P", "TB0_RX1N"], "TB0 Lane1"),
        esd4("U51", ["TB0_TX2P", "TB0_TX2N", "TB0_RX2P", "TB0_RX2N"], "TB0 Lane2"),
        esd4("U52", ["TB1_TX1P", "TB1_TX1N", "TB1_RX1P", "TB1_RX1N"], "TB1 Lane1"),
        esd4("U53", ["TB1_TX2P", "TB1_TX2N", "TB1_RX2P", "TB1_RX2N"], "TB1 Lane2"),
        esd4("U54", ["USBA1_TXP", "USBA1_TXN", "USBA1_RXP", "USBA1_RXN"], "USB-A1 SS"),
        esd4("U55", ["USBA2_TXP", "USBA2_TXN", "USBA2_RXP", "USBA2_RXN"], "USB-A2 SS"),
        esd4("U56", ["HDMI_D0P", "HDMI_D0N", "HDMI_D1P", "HDMI_D1N"], "HDMI D0/D1"),
        esd4("U57", ["HDMI_D2P", "HDMI_D2N", "HDMI_CLKP", "HDMI_CLKN"], "HDMI D2/CLK"),
        esd4("U58", ["C0_CC1", "C0_CC2", "C1_CC1", "C1_CC2"], "USB-C CC-Pins"),
        esd4("U59", ["USB2_P5", "USB2_N5", "USB2_P6", "USB2_N6"], "TB USB2"),
        esd4("U60", ["USB2_P1", "USB2_N1", "USB2_P2", "USB2_N2"], "USB-A USB2"),
    ]

    def choke(ref, pn, nn, port):
        return Comp(ref, "DLW21SN900", [
            P("1A", "L", "b", pn), P("1B", "R", "b", pn + "_C"),
            P("2A", "L", "b", nn), P("2B", "R", "b", nn + "_C")],
            width=20.32, fp=FP + "R_0603",
            desc=f"USB2-Gleichtaktdrossel 90 Ohm - {port}",
            mpn="Murata DLW21SN900SQ2L")

    comps_list += [
        choke("FL1", "USB2_P8", "USB2_N8", "Webcam"),
        choke("FL2", "USB2_P7", "USB2_N7", "WLAN/BT"),
    ]

    def strap(ref, net, updown, desc):
        return Comp(ref, "R_10k", [
            P("1", "L", "p", net), P("2", "R", "p", updown)],
            width=15.24, fp=FP + "R_0603", desc=desc,
            mpn="Yageo RC0402FR-0710KL")

    comps_list += [
        strap("R10", "TB_FORCE_PWR", "GND", "Pulldown TB Force-Power"),
        strap("R11", "PE_WAKE#", "+3V3_ALW", "Pullup PCIe-Wake (Open-Drain)"),
        strap("R12", "PROCHOT#", "+3V3", "Pullup PROCHOT# (Open-Drain)"),
        strap("R13", "SPD_HSCL", "+3V3", "Pullup DDR5-SPD-Bus"),
        strap("R14", "SPD_HSDA", "+3V3", "Pullup DDR5-SPD-Bus"),
        strap("R15", "PWRBTN_IN#", "+3V3_ALW", "Pullup Power-Taste"),
    ]

    notes = [
        "Kleinteile: MLCC-Baenke (VDDCR-Baenke werden auf B.Cu direkt unter",
        "dem Sockel bestueckt), ESD-Arrays an allen externen Ports,",
        "USB2-Gleichtaktdrosseln, Pull-Widerstaende fuer Open-Drain-Busse.",
        "_C-Netze der Chokes: buchsenseitige Segmente der USB2-Paare.",
        "100n-Positionen stehen stellvertretend fuer je 4 Stueck am IC.",
    ]
    comps = cols([
        (90, comps_list[0:12]),
        (200, comps_list[12:28]),
        (310, comps_list[28:38]),
        (430, comps_list[38:49]),
        (540, comps_list[49:]),
    ], gap=8.89)
    return ("Kleinteile: Entkopplung / ESD / Filter", "10_kleinteile.kicad_sch",
            comps, notes)
