# Grenzen dieses Designs (bewusst offen gelegt)

Dieses Projekt liefert eine vollständige, in sich konsistente
System-Architektur auf Prototyp-Niveau. Ein fertigungsreifes
Laptop-Mainboard braucht darüber hinaus Dinge, die hier prinzipbedingt
fehlen — Transparenz ist besser als so zu tun, als wäre das ein
Wochenendprojekt:

1. **AM5-Ballout (NDA):** Die exakte Belegung der 1718 Sockelpins gibt AMD
   nur unter NDA an Boardpartner heraus. Das Sockelsymbol ist daher
   **funktional gruppiert** (Busse), der Footprint trägt ein
   repräsentatives 0.9-mm-Raster mit 1716 Pads. Für die echte Zuordnung:
   AMD Infrastructure Roadmap + Referenzdesign (über AMD-Partnerprogramm).

2. **Entkopplung:** Gezeichnet sind Bulk-Kondensatoren; die ~200 MLCCs
   (0201/0402, je IC 100 nF + 1 µF, unter dem Sockel 47× 22 µF) sind in den
   Blattnotizen und hier dokumentiert, aber nicht einzeln im Schaltplan.

3. **Feinrouting:** DDR5-Fly-by mit Längenabgleich, PCIe-Gen4-Paare und
   TB4-Lanes sind als Korridore und Regeln (docs/layout.md) definiert,
   nicht fertig geroutet. Das ist bei realen Boards Teamarbeit über Monate
   mit SI-Simulation (z. B. HyperLynx/SIwave).

4. **ESD/EMV-Kleinteile:** ESD-Arrays (TPD4E02B04 an allen SS-Paaren,
   TPD6E05U06 an USB2/CC), Common-Mode-Chokes am HDMI, Serien-R an
   Straps — in Stückliste/Notizen erwähnt, nicht einzeln gezeichnet.

5. **Firmware:** AGESA/BIOS-Port (chipsatzloses AM5 braucht angepasstes
   UEFI), EC-Firmware (Sequencing, Tastatur, Lüfterkurven), TPS65988- und
   JHL8540-Konfigurationsbinaries müssen erstellt werden.

6. **Zulassung:** TB4-Zertifizierung (Intel), USB-IF, CE/FCC, Akku-Transport
   (UN38.3) sind formale Prozesse mit Mustern und Messungen.

7. **Mechanik:** Gehäuse, Kühler (Vapor-Chamber für 45 W + gesockelte
   Bauhöhe!), Scharniere, Antennenführung sind nicht Teil dieses Repos.

## Realistische Einschätzung

| Schritt | Aufwand |
|---|---|
| Dieser Stand (Architektur, Schaltplan-Gerüst, Floorplan) | ✔ erledigt |
| Vollständiger Detail-Schaltplan mit NDA-Unterlagen | 4–8 Wochen (1 Ing.) |
| Layout + SI-Simulation | 2–4 Monate |
| Prototyp-Fertigung + Bring-up + BIOS/EC-Port | 3–6 Monate |
| Budget bis zum laufenden Prototyp | grob 50–150 k€ |

Der Weg über ein **Framework-16-Mainboard** oder ein
**DeskMini-X600-Derivat** wäre der pragmatische Mittelweg, wenn es primär
um einen wartbaren/aufrüstbaren Laptop geht.
