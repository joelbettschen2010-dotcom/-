# Stand des Designs und verbleibende Grenzen

## Was seit Rev A dazugekommen ist (Rev B)

1. **Echte AM5-Pinbelegung:** Alle 1718 Sockelpins sind jetzt einzeln
   benannt — Quelle ist die Public-Domain-Pinmap von WikiChip
   (Wikimedia Commons, „Socket AM5 pinmap.svg", Autor QuietRub).
   Tabelle: `docs/am5_pinmap.csv`. Der Sockel-Footprint trägt die echten
   Padnamen (A1…) und **1708 Pads haben ihr echtes Signal als Netz** im
   Board (VSS→GND, VDDCR, MAA/MAB/MBA/MBB-Speicherkanäle, PCIE_TX/RX[27:0],
   DP0–2, USBC0–2, SPI, eSPI, AZ/HDA …).
2. **Kleinteile:** Blatt 10 mit MLCC-Bänken (VDDCR/SOC unter dem Sockel,
   Bottom-Side), Schienen-Entkopplung, 11 ESD-Arrays an allen externen
   Ports, USB2-Gleichtaktdrosseln, Pull-Up/Down-Straps. 48-MHz-Systemquarz
   ergänzt (X48M-Pins der Pinmap). Bestückung beidseitig (B.Cu-Parts).
3. **Mehr Routing:** GND-Stitching-Via-Raster, MDI-, DP-, PCIe-, HDMI- und
   DDR-Korridore als Differenzpaare, Netz-Zuordnung an allen Sockelpads.
4. **EliteBook-850-G7-Anpassung:** Umriss 340×112 mm, Portreihenfolge wie
   beim Original (USB-C-PD statt Barrel-Jack), 3S-56-Wh-Akku, HP-FPC-
   Steckplätze — Details und offene Messpunkte in `docs/elitebook.md`.

## Was weiterhin fehlt — ehrlich gesagt

1. **Pinmap-Verifikation:** Die WikiChip-Pinmap ist Community-Arbeit,
   kein AMD-Originaldokument. Vor Fertigung gegen ein echtes AM5-Board
   (Beeper/Multimeter, mind. Power/GND + einige Signale) gegenprüfen.
   Die PCIe-Lane→Port-Bifurcation (welche 4er-Gruppe zu SSD/TB wird)
   ist firmwareabhängig und muss mit dem AGESA-Port abgestimmt werden.
2. **Feinrouting:** Die Korridore zeigen Topologie und Lagen; das
   DRC-saubere Ausrouten aller ~640 Netze mit DDR5-Längenabgleich
   (±0.1 mm) und SI-Simulation bleibt Monate Ingenieursarbeit.
   Ohne dieses Feinrouting ist das Board **nicht bestellbar**.
3. **HP-Proprietäres:** Akku-, Tastatur-, Touchpad- und Displaykabel-
   Pinouts müssen am Originalgerät ausgemessen werden (docs/elitebook.md).
   Chassis-Umriss und Lochbild sind Annäherungen bis zum Scan des Originals.
4. **Firmware:** UEFI/AGESA-Port für chipsatzloses AM5, EC-Firmware
   (inkl. HP-Tastaturmatrix), TPS65988-/JHL8540-Konfigurationsbinaries —
   ausdrücklich noch nicht Teil des Auftrags.
5. **Mechanik/Thermik:** 35–45 W gesockelt in einem 17.9-mm-Chassis
   erfordert einen erhöhten Bodendeckel und eine Custom-Vapor-Chamber.
6. **Zulassung:** TB4-/USB-IF-Zertifizierung, CE/FCC, UN38.3.

## Realistische Einschätzung bis zum Prototyp

| Schritt | Aufwand |
|---|---|
| Dieser Stand (Architektur, Pinbelegung, Kleinteile, Floorplan) | ✔ |
| Pinmap-Verifikation + Detail-Schaltplan-Review | 2–4 Wochen |
| Feinrouting + SI-Simulation | 2–4 Monate |
| Chassis-Vermessung + Mechanik (Kühler, Bodendeckel) | 4–8 Wochen |
| Prototyp + Bring-up + BIOS/EC-Port | 3–6 Monate |
| Budget | grob 50–150 k€ |
