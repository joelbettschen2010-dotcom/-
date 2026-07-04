# Systemarchitektur

## Blockdiagramm

```mermaid
flowchart LR
    subgraph SOC["Ryzen 5 8600G (AM5-Sockel)"]
        CPU[Zen4 6C/12T]
        GPU[Radeon 760M]
        IO[SoC-I/O]
    end

    MEM1[SO-DIMM A DDR5] ---|Kanal A| SOC
    MEM2[SO-DIMM B DDR5] ---|Kanal B| SOC

    SOC ---|PCIe4 x4 P0| SSD1[M.2 NVMe 1]
    SOC ---|PCIe4 x4 P1| SSD2[M.2 NVMe 2]
    SOC ---|PCIe3 x4 P2 + 2x DP1.4| TB[JHL8540 TB4]
    TB --- TB0[USB-C TB4 #0]
    TB --- TB1[USB-C TB4 #1]
    SOC ---|PCIe x1 G2 + USB2| WIFI[M.2 E-Key WLAN]
    SOC ---|USB3 Gen1 x2| USBA[2x USB-A]
    SOC ---|HDMI 2.1| HDMI[HDMI-Buchse]
    SOC ---|eDP 1.4 2L| PANEL[15.6 Panel]
    SOC ---|HDA| AUD[ALC256] --- JACK[3.5mm AUX]
    SOC ---|eSPI| EC[IT5570E EC]
    PWR[USB-C-Ladeport 100W] --- PD2[TPS65987D]
    PD2 ---|+VBUS 20V| CHG[BQ25731 Lader] --- BAT[HP-Akku 3S 56Wh]
    PD[TPS65988] --- TB0
    PD --- TB1
    CHG ---|+VSYS| VRM[VRM 4+2 Phasen SVI3] --- SOC
```

## PCIe-Lane-Budget (Phoenix, 20× Gen4)

| SoC-Port | Breite | Gen | Verwendung |
|---|---|---|---|
| P0 | ×4 | 4 | M.2 NVMe #1 |
| P1 | ×4 | 4 | M.2 NVMe #2 |
| P2 | ×4 | 3 | Thunderbolt-Controller JHL8540 |
| G0 | ×1 | — | frei (Reserve) |
| G1 | ×1 | — | frei (Reserve) |
| G2 | ×1 | 3 | WLAN M.2 E-Key |
| Referenztakte | — | — | 6× 100 MHz vom SoC-GPP-Clocktree (PE_CLK0…5) |

## USB-Portzuordnung

| SoC-Port | Geschwindigkeit | Ziel |
|---|---|---|
| U3G2_1 / USB2_1 | 10G / 480M | USB-A #1 (links) |
| U3G2_2 / USB2_2 | 10G / 480M | USB-A #2 (links) |
| USB2_3 | 480M | USB-C-Ladeport (Datenpfad) |
| USB2_5 / USB2_6 | 480M | TB4-Ports (USB-2.0-Pfad) |
| USB2_7 | 480M | WLAN/Bluetooth |
| USB2_8 | 480M | Webcam |

## Display-Budget (4 Pipes des SoC)

1. **eDP 1.4, 2 Lanes** → internes 15.6"-Panel (bis 2560×1440@60)
2. **HDMI 2.1** → externe Buchse (TMDS direkt, DDC/CEC über TPD12S016)
3. **DP0** → JHL8540 DP-IN 0 (Thunderbolt-Ausgabe Port 0)
4. **DP1** → JHL8540 DP-IN 1 (Thunderbolt-Ausgabe Port 1)

## Management-Busse

- **eSPI**: SoC ↔ EC (IT5570E)
- **SVI3**: SoC ↔ VRM-Controller MP2857 (SVC/SVD/SVT)
- **I3C/SMBus (SPD_HSCL/HSDA)**: SoC ↔ DIMM-SPD-Hubs
- **I2C (EC)**: Akku (Smart Battery), Laderegler BQ25731, PD-Controller TPS65988
- **I2C (PD↔TB)**: TPS65988 ↔ JHL8540 (Alternate-Mode-Aushandlung)
