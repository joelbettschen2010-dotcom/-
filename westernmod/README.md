# 🤠 Wild West — Minecraft-Mod

Der ganze Wilde Westen für **Minecraft Java Edition 1.21.1** (Fabric): mit einem Item
gründest du eine komplette Westernstadt, Cowboys und Sheriffs liefern sich echte
Schiessereien mit Banditen, und Planwagen bringen dich samt Ladung durch die Prärie.

---

## Installation

1. **Fabric Loader** für Minecraft **1.21.1** installieren → <https://fabricmc.net/use/installer/>
2. **Fabric API** für 1.21.1 herunterladen → <https://modrinth.com/mod/fabric-api>
3. `westernmod-1.0.0.jar` **und** die Fabric-API-Datei in den `mods`-Ordner legen:
   - Windows: `%appdata%\.minecraft\mods`
   - macOS: `~/Library/Application Support/minecraft/mods`
   - Linux: `~/.minecraft/mods`
4. Minecraft mit dem Profil **fabric-loader-1.21.1** starten.

Braucht Java 21 — das bringt der aktuelle Minecraft-Launcher mit.

---

## Die Stadt aus dem Nichts

Das Herzstück ist die **Siedlungsurkunde** (`Town Charter`). Rechtsklick auf den Boden,
und in Blickrichtung wächst eine ganze Stadt:

- **Hauptstrasse** aus festgetretenem Sand mit ausgefahrenen Wagenspuren
- **Acht Gebäude** links und rechts, alle mit der typischen hohen Schaufassade, überdachter
  Veranda und beschriftetem Schild: Saloon, Sheriffbüro, Bank, Gemischtwarenladen, Hotel,
  Schmiede, Kirche und Stall
- **Eingerichtete Innenräume** — Theke und Tische im Saloon, Gitterzellen beim Sheriff,
  Tresorraum in der Bank, Bankreihen und Glockenturm in der Kirche, Amboss und Esse in
  der Schmiede, Heu im Stall
- **Ortseingang** mit Torbogen und Ortsschild (der Name wird ausgewürfelt)
- **Wasserturm, Windmühle, Brunnen und Lagerfeuer** am Ortsausgang
- **Anbindepfosten, Tröge, Fässer und Strassenlaternen** entlang der Strasse
- **Bewohner**: ein Sheriff, fünf Cowboys, Dorfbewohner, gezähmte Pferde im Stall und
  zwei Planwagen
- **Ein Banditenlager** rund 45 Blöcke ausserhalb — mit Zelten, Lagerfeuer, Truhen und
  einer Bande samt Anführer

Das Gelände wird vorher planiert, die Stadt steht also auch am Hang gerade.

> Der Bau setzt einige zehntausend Blöcke auf einmal — je nach Rechner ruckelt es dabei
> kurz. Das ist normal und dauert nur einen Moment.

Für Kreativmodus und Server gibt es denselben Bau auch als Befehl (Rechtestufe 2):

```
/westerntown              # baut die Stadt an der eigenen Position
/westerntown 100 64 -250  # baut sie an einer bestimmten Stelle
```

Die angegebene Stelle muss geladen sein — wie bei `/setblock`. Für weit entfernte
Koordinaten also erst hinteleportieren.

---

## Schiessereien

Vier Revolvermänner-Typen teilen sich dieselbe KI: sie halten Distanz, gehen auf
Schussweite heran und feuern aufeinander.

| Figur | Seite | Leben | Waffe |
|---|---|---|---|
| **Cowboy** | Gesetz | 24 | Revolver |
| **Sheriff** | Gesetz | 40 + Rüstung | schnellerer Revolver, grössere Reichweite |
| **Bandit** | Gesetzlos | 26 | Revolver |
| **Bandenchef** | Gesetzlos | 60 + Rüstung | abgesägte Schrotflinte (5 Kugeln pro Schuss) |

Cowboys und Sheriffs greifen Banditen von sich aus an, Banditen zielen auf Gesetzeshüter,
Dorfbewohner und Spieler. Wer auf einen Cowboy schiesst, hat die ganze Stadt gegen sich.

Banditen tauchen ausserdem nachts von selbst in **Wüste, Badlands und Savanne** auf
(nicht auf Schwierigkeitsgrad *Friedlich*).

---

## Planwagen

Der Planwagen ist ein Pferdegespann und fährt sich wie ein Pferd:

- **Rechtsklick** → aufsteigen und mit WASD losfahren (drei Plätze: Kutschbock + Ladefläche)
- **Schleichen + Rechtsklick** → Ladefläche öffnen, 27 Slots Stauraum
- Auf **Wegen, grobem Boden, Kies und festgestampftem Schlamm** rollt er **60 % schneller**
  als querfeldein — es lohnt sich, Strassen zu bauen
- Räder und Pferdebeine laufen im Takt der Fahrt mit

---

## Waffen und Ausrüstung

Alle Schusswaffen ziehen **Patronen** direkt aus dem Inventar — kein Nachladen, die
Feuerrate steckt in der Waffe.

| Item | Schaden | Nachladezeit | Besonderheit |
|---|---|---|---|
| **Revolver** | 6 | 8 Ticks | schnell, kaum Rückstoss |
| **Winchester** | 10 | 22 Ticks | präzise, hohe Geschossgeschwindigkeit |
| **Schrotflinte** | 3,5 × 7 | 32 Ticks | breite Streuung, verheerend auf kurze Distanz |
| **Dynamit** | — | 20 Ticks | Wurfgeschoss, zündet beim Aufschlag |

Dazu:

- **Lasso** — zähmt Pferde und Esel mit einem Wurf, nimmt alles andere an die Leine
- **Sheriffstern** — ruft eine Fahndung aus: alle Banditen im Umkreis von 48 Blöcken
  leuchten auf, alle Gesetzeshüter nehmen die Verfolgung auf
- **Steckbrief** (Block) — Rechtsklick setzt ein Kopfgeld aus; eine Bande mit Anführer
  reitet an, der Anführer lässt Golddollar und einen Sheriffstern fallen
- **Whiskey** — Stärke und Feuerresistenz, dafür wird einem schwummrig
- **Bohnen mit Speck** — sättigt kräftig
- **Golddollar** — Beute der Banditen
- **Anbindepfosten** und **Saloontür** (Schwingtür) als Bauklötze
- **Steppenläufer** — rollen durch trockene Biome, reine Atmosphäre

---

## Rezepte

Alle Rezepte sind normale Werkbank-Rezepte (`P` = Papier, `I` = Eisenbarren,
`W` = Holzbretter, `G` = Schiesspulver, `N` = Eisennugget, `S` = Faden, `L` = Stamm):

| Item | Muster |
|---|---|
| Patronen ×4 | `N` über `G` |
| Revolver | `_II` / `IWI` / `W__` |
| Winchester | `III` / `WWI` / `W__` |
| Schrotflinte | `II_` / `IWW` / `_W_` |
| Dynamit ×2 | `S` / `P` / `G` (senkrecht) |
| Lasso | Faden im Ring |
| Sheriffstern | Gold im Kreuz um einen Eisenbarren |
| Siedlungsurkunde | 8 × Papier um einen **Smaragdblock** |
| Planwagen | Bretter + Truhe + zwei Stämme als Räder |
| Anbindepfosten ×2 | `LLL` / `L_L` |
| Saloontür ×3 | 2 × 3 Bretter |
| Steckbrief | Papier + Tintenbeutel |
| Whiskey | Glasflasche + Zuckerrohr + brauner Pilz |
| Bohnen mit Speck | Schüssel + gebratenes Schweinefleisch + Weizensamen |

---

## Selber bauen

Es reicht ein installiertes **JDK 21** — Gradle bringt der mitgelieferte Wrapper mit:

```bash
cd westernmod
./gradlew build          # ergibt build/libs/westernmod-1.0.0.jar
./gradlew runClient      # startet Minecraft mit dem Mod zum Testen
./gradlew runServer      # startet einen Server mit dem Mod
```

(Unter Windows `gradlew.bat` statt `./gradlew`.)

Getestet mit Gradle 8.14.3, Fabric Loom 1.11.8, Yarn `1.21.1+build.3`,
Fabric Loader 0.16.14 und Fabric API 0.116.15.

### Texturen und JSON neu erzeugen

Sämtliche Texturen, Modelle, Blockstates, Sprachdateien, Loot-Tabellen und Rezepte
entstehen aus einem Skript — nichts davon ist von Hand gepixelt:

```bash
pip install Pillow
python3 tools/gen_assets.py
```

Das Skript ist deterministisch: zweimal ausgeführt liefert es byte-gleiche Dateien.
Wer eine Farbe ändern will, ändert sie im Skript und lässt es neu laufen.

---

## Aufbau des Codes

```
src/main/java/ch/joel/westernmod/
├── WesternMod.java          Einstiegspunkt
├── ModItems / ModBlocks / ModEntities / ModItemGroups / ModWorld
├── item/                    Waffen, Urkunde, Lasso, Stern, Dynamit
├── block/                   Anbindepfosten, Steckbrief
├── entity/                  Revolvermänner, Planwagen, Kugel, Steppenläufer
├── world/                   Stadtgenerator, Häuserbau, Bauhelfer
└── client/                  Modelle und Renderer
```

Der Stadtgenerator rechnet in **lokalen Koordinaten** (`f` = die Strasse entlang,
`u` = nach oben, `r` = quer zur Strasse) und rechnet erst beim Setzen in Weltkoordinaten
um — deshalb steht die Stadt immer richtig herum, egal aus welcher Richtung man sie
gründet.

---

## Bekannte Grenzen

- Der Mod nutzt **Vanilla-Sounds** für die Schüsse (Feuerwerk + Armbrust überlagert);
  eigene `.ogg`-Dateien sind noch keine dabei.
- **Kein tragbarer Cowboyhut** als Rüstungsteil — die Hüte gehören zur Modell-Geometrie
  der Figuren.
- Der Stadtgenerator baut immer denselben Grundriss; nur Ortsname, Streuung und
  Bodendetails werden gewürfelt.
