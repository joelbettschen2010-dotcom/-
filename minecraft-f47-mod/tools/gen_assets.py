#!/usr/bin/env python3
"""
Erzeugt alle Assets des F-47-Mods: Texturen, Modelle, Blockstates,
Rezepte, Loot-Tabellen, Schadensarten und Sprachdateien.

Die Texturen werden programmatisch gezeichnet - so bleibt der Mod ohne
Bildbearbeitung reproduzierbar und alles passt farblich zusammen.

    python3 tools/gen_assets.py
"""

import json
import os
import random
import struct
import zlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
MAIN = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(MAIN, "assets", "f47")
DATA = os.path.join(MAIN, "data", "f47")
MOD = "f47"


# ---------------------------------------------------------------------------
# PNG-Ausgabe (ohne externe Bibliotheken)
# ---------------------------------------------------------------------------

def write_png(path, image):
    """Schreibt ein 2D-Pixelfeld [[(r,g,b,a), ...], ...] als PNG."""
    height = len(image)
    width = len(image[0])
    raw = bytearray()
    for row in image:
        raw.append(0)  # Filtertyp "none"
        for r, g, b, a in row:
            raw += bytes((r, g, b, a))

    def chunk(tag, data):
        out = struct.pack(">I", len(data)) + tag + data
        return out + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", ihdr)
           + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
           + chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as handle:
        handle.write(png)


def blank(width, height, colour=(0, 0, 0, 0)):
    return [[colour for _ in range(width)] for _ in range(height)]


def shade(colour, amount):
    """Hellt auf (amount > 0) oder dunkelt ab (amount < 0)."""
    r, g, b, a = colour
    def clamp(value):
        return max(0, min(255, int(value)))
    return (clamp(r + amount), clamp(g + amount), clamp(b + amount), a)


def put(image, x, y, colour):
    if 0 <= y < len(image) and 0 <= x < len(image[0]):
        image[y][x] = colour


def rect(image, x0, y0, x1, y1, colour):
    for y in range(y0, y1):
        for x in range(x0, x1):
            put(image, x, y, colour)


def paint(image, art, palette, ox=0, oy=0):
    """Zeichnet ASCII-Pixelart. '.' bleibt durchsichtig."""
    for y, line in enumerate(art):
        for x, char in enumerate(line):
            if char != ".":
                put(image, ox + x, oy + y, palette[char])


# ---------------------------------------------------------------------------
# Farbpalette des Mods
# ---------------------------------------------------------------------------

TITANIUM = (168, 176, 184, 255)
TITANIUM_DARK = (118, 126, 136, 255)
RAW_TITANIUM = (146, 140, 128, 255)
STEEL = (92, 99, 107, 255)
STEEL_DARK = (58, 63, 70, 255)
JET_GREY = (74, 80, 88, 255)
JET_DARK = (48, 52, 58, 255)
STEALTH_BLACK = (34, 36, 40, 255)
ASPHALT = (52, 54, 58, 255)
ASPHALT_LIGHT = (68, 70, 76, 255)
MARKING = (224, 226, 220, 255)
AMBER = (232, 168, 42, 255)
LASER_CYAN = (86, 226, 236, 255)
PLASMA = (150, 108, 236, 255)
DANGER_RED = (198, 58, 48, 255)
OLIVE = (86, 94, 60, 255)
OLIVE_DARK = (62, 68, 44, 255)
KHAKI = (150, 136, 100, 255)
SKIN = (214, 168, 130, 255)
VISOR = (40, 48, 62, 255)
GLASS_BLUE = (118, 176, 208, 255)


# ---------------------------------------------------------------------------
# Block-Texturen
# ---------------------------------------------------------------------------

def noisy_fill(image, base, spread, rng, x0=0, y0=0, x1=None, y1=None):
    x1 = len(image[0]) if x1 is None else x1
    y1 = len(image) if y1 is None else y1
    for y in range(y0, y1):
        for x in range(x0, x1):
            put(image, x, y, shade(base, rng.randint(-spread, spread)))


def tex_asphalt(seed, marking=None):
    """Startbahnbelag, optional mit Markierung auf der Oberseite."""
    rng = random.Random(seed)
    image = blank(16, 16)
    noisy_fill(image, ASPHALT, 8, rng)
    # Feine Fugen zwischen den Betonplatten.
    for x in range(16):
        put(image, x, 0, shade(ASPHALT, -14))
        put(image, x, 15, shade(ASPHALT, -14))
    for y in range(16):
        put(image, 0, y, shade(ASPHALT, -14))
        put(image, 15, y, shade(ASPHALT, -14))

    if marking == "centerline":
        # Unterbrochene Mittellinie laengs des Blocks.
        for y in range(2, 14):
            rect(image, 7, y, 9, y + 1, MARKING if y % 8 < 6 else shade(ASPHALT, 4))
    elif marking == "threshold":
        # Schwellenbalken quer zur Bahn.
        for x in (1, 4, 7, 10, 13):
            rect(image, x, 2, x + 2, 14, MARKING)
    elif marking == "edge":
        rect(image, 0, 6, 16, 10, AMBER)
    return image


def tex_runway_light():
    rng = random.Random(7)
    image = blank(16, 16)
    noisy_fill(image, ASPHALT_LIGHT, 6, rng)
    rect(image, 5, 5, 11, 11, shade(AMBER, 30))
    rect(image, 6, 6, 10, 10, (255, 236, 170, 255))
    rect(image, 7, 7, 9, 9, (255, 255, 236, 255))
    return image


def tex_panel(base, seed, rivets=True, stripe=None):
    """Metallpaneel mit Nieten - Grundlage fuer Hangar und Technikbloecke."""
    rng = random.Random(seed)
    image = blank(16, 16)
    noisy_fill(image, base, 7, rng)
    rect(image, 0, 0, 16, 1, shade(base, 22))
    rect(image, 0, 15, 16, 16, shade(base, -22))
    rect(image, 0, 0, 1, 16, shade(base, 14))
    rect(image, 15, 0, 16, 16, shade(base, -16))
    if rivets:
        for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
            put(image, x, y, shade(base, 34))
            put(image, x, y + 1, shade(base, -20))
    if stripe:
        for x in range(16):
            if (x // 2) % 2 == 0:
                rect(image, x, 6, x + 1, 10, stripe)
    return image


def tex_ore(stone, seed):
    rng = random.Random(seed)
    image = blank(16, 16)
    noisy_fill(image, stone, 9, rng)
    # Erzadern als kleine Nester.
    for (cx, cy) in ((4, 4), (11, 7), (6, 12)):
        for dy in range(-2, 3):
            for dx in range(-2, 3):
                if abs(dx) + abs(dy) <= 2 + rng.randint(0, 1):
                    put(image, cx + dx, cy + dy, shade(TITANIUM, rng.randint(-18, 18)))
        put(image, cx, cy, shade(TITANIUM, 40))
    return image


def tex_metal_block(base, seed):
    rng = random.Random(seed)
    image = blank(16, 16)
    noisy_fill(image, base, 6, rng)
    for offset in (0, 8):
        rect(image, offset, 0, offset + 1, 16, shade(base, -20))
        rect(image, 0, offset, 16, offset + 1, shade(base, -20))
        rect(image, offset + 1, 1, offset + 7, 2, shade(base, 20))
    return image


def tex_radar_top():
    rng = random.Random(21)
    image = blank(16, 16)
    noisy_fill(image, STEEL, 6, rng)
    rect(image, 5, 5, 11, 11, shade(STEEL, -22))
    rect(image, 6, 6, 10, 10, (60, 170, 110, 255))
    return image


def tex_radar_side():
    image = tex_panel(STEEL, 22)
    rect(image, 3, 4, 13, 6, (48, 132, 92, 255))
    rect(image, 3, 8, 13, 9, shade(STEEL, -24))
    put(image, 4, 11, (90, 220, 140, 255))
    put(image, 6, 11, AMBER)
    return image


def tex_dome_top():
    rng = random.Random(31)
    image = blank(16, 16)
    noisy_fill(image, STEEL_DARK, 6, rng)
    # Vier Startrohre von oben.
    for (cx, cy) in ((4, 4), (11, 4), (4, 11), (11, 11)):
        rect(image, cx - 2, cy - 2, cx + 2, cy + 2, shade(STEEL, 10))
        rect(image, cx - 1, cy - 1, cx + 1, cy + 1, (26, 26, 30, 255))
    return image


def tex_dome_side():
    image = tex_panel(STEEL_DARK, 32)
    for x in (3, 8, 12):
        rect(image, x, 2, x + 2, 12, shade(STEEL, 14))
    rect(image, 0, 12, 16, 16, shade(STEEL_DARK, -12))
    rect(image, 2, 13, 5, 15, DANGER_RED)
    return image


def tex_rearm_top():
    rng = random.Random(41)
    image = blank(16, 16)
    noisy_fill(image, ASPHALT_LIGHT, 6, rng)
    # Gelbes Warnkreuz als Abstellposition.
    for i in range(16):
        put(image, i, i, AMBER)
        put(image, 15 - i, i, AMBER)
    rect(image, 6, 6, 10, 10, shade(ASPHALT_LIGHT, 12))
    return image


def tex_barracks_side():
    image = tex_panel(OLIVE, 51)
    rect(image, 2, 3, 7, 8, shade(GLASS_BLUE, -20))
    rect(image, 9, 3, 14, 8, shade(GLASS_BLUE, -20))
    rect(image, 2, 10, 14, 11, shade(OLIVE, -24))
    return image


def tex_barracks_front():
    image = tex_panel(OLIVE, 52)
    rect(image, 5, 4, 11, 16, shade(OLIVE_DARK, -10))
    rect(image, 6, 5, 10, 15, shade(OLIVE_DARK, 6))
    put(image, 9, 10, AMBER)
    return image


def tex_hangar_door():
    image = tex_panel(STEEL, 61, rivets=False)
    # Waagerechte Lamellen wie bei einem Rolltor.
    for y in range(0, 16, 4):
        rect(image, 0, y, 16, y + 1, shade(STEEL, -26))
        rect(image, 0, y + 1, 16, y + 2, shade(STEEL, 16))
    rect(image, 0, 14, 16, 16, AMBER)
    rect(image, 0, 15, 16, 16, shade(STEEL, -30))
    return image


# ---------------------------------------------------------------------------
# Item-Icons
# ---------------------------------------------------------------------------

INGOT_ART = [
    "................",
    "................",
    "................",
    "................",
    ".....11111111...",
    "....1222222231..",
    "...12222222331..",
    "..122222223331..",
    "..132222233331..",
    "..133222333331..",
    "...1333333331...",
    "....13333331....",
    ".....1111111....",
    "................",
    "................",
    "................",
]

PLATE_ART = [
    "................",
    "................",
    "..111111111111..",
    "..122222222231..",
    "..12433333342 1.",
    "..123333333321..",
    "..123333333321..",
    "..123333333321..",
    "..123333333321..",
    "..123333333321..",
    "..12433333342 1.",
    "..133222222331..",
    "..111111111111..",
    "................",
    "................",
    "................",
]

CHIP_ART = [
    "................",
    "....4.4.4.4.....",
    "..111111111111..",
    "..122222222221..",
    "..123333333321..",
    "4.123355553321.4",
    "..123355553321..",
    "4.123333333321.4",
    "..123333333321..",
    "4.123355333321.4",
    "..122222222221..",
    "..111111111111..",
    "....4.4.4.4.....",
    "................",
    "................",
    "................",
]

CANISTER_ART = [
    "................",
    "......1111......",
    ".....122221.....",
    "....11111111....",
    "...122222221....",
    "...123333321....",
    "...123444321....",
    "...123444321....",
    "...123444321....",
    "...123444321....",
    "...123444321....",
    "...122222221....",
    "...111111111....",
    "................",
    "................",
    "................",
]

MISSILE_ART = [
    "................",
    "..............1.",
    ".............121",
    "............1221",
    "...........12221",
    "..........122211",
    ".....1...1222111",
    "....11..122211..",
    "...1111122211...",
    "....111222111...",
    ".....1222111....",
    "....12221.1.....",
    "...12221........",
    "..1222.1........",
    "..1111..........",
    "................",
]

BOMB_ART = [
    "................",
    "................",
    ".......111......",
    "......12221.....",
    "......12221.....",
    "......12321.....",
    "......12321.....",
    "......12321.....",
    "......12321.....",
    "......12221.....",
    ".....1122211....",
    "....11.121.11...",
    "...11..121..11..",
    "...1...111...1..",
    "................",
    "................",
]

RIFLE_ART = [
    "................",
    "................",
    "..............1.",
    ".............14.",
    "...........1441.",
    "..1111111114411.",
    "..12222222444 1.",
    "..1233333344411.",
    "..1223333331111.",
    "...1122333111...",
    "....1.1231......",
    "......1231......",
    ".....12331......",
    ".....1231.......",
    "......11........",
    "................",
]

LAUNCHER_ART = [
    "................",
    "................",
    "................",
    "...1111111111...",
    "..122222222221..",
    "..12344444443 1.",
    "..12345555443 1.",
    "..12344444443 1.",
    "..122222222221..",
    "...1111.1111....",
    "......1231......",
    "......1231......",
    ".....12331......",
    ".....1231.......",
    "......11........",
    "................",
]

TABLET_ART = [
    "................",
    "..111111111111..",
    "..122222222221..",
    "..123333333321..",
    "..123444444321..",
    "..123455554321..",
    "..123454454321..",
    "..123455554321..",
    "..123444444321..",
    "..123333333321..",
    "..122222222221..",
    "..12211111 221..",
    "..111111111111..",
    "................",
    "................",
    "................",
]

JET_ART = [
    "................",
    ".......11.......",
    ".......22.......",
    "......1221......",
    "......1221......",
    "......1331......",
    ".1....1331....1.",
    ".11111133111111.",
    ".12222233322221.",
    ".11111133111111.",
    "......1331......",
    "......1331......",
    ".....113311.....",
    "....11144111....",
    "....1.1441.1....",
    "................",
]

ORDER_ART = [
    "................",
    "..1111111111 1..",
    "..122222222221..",
    "..123333333321..",
    "..124444444321..",
    "..123333333321..",
    "..124444444321..",
    "..123333333321..",
    "..124444444321..",
    "..123333333321..",
    "..122222222221..",
    "..155555555551..",
    "..111111111111..",
    "................",
    "................",
    "................",
]

BEACON_ART = [
    "................",
    "................",
    "......1111......",
    ".....122221.....",
    "....12444421....",
    "....12455421....",
    "....12444421....",
    ".....122221.....",
    "......1221......",
    "......1221......",
    ".....112211.....",
    "....11222211....",
    "...1122222211...",
    "...1111111111...",
    "................",
    "................",
]


def icon(art, palette):
    image = blank(16, 16)
    paint(image, art, palette)
    return image


def metal_palette(base, outline=(28, 28, 32, 255), accent=None):
    return {
        "1": outline,
        "2": shade(base, 32),
        "3": base,
        "4": shade(base, -30) if accent is None else accent,
        "5": shade(base, -55),
        " ": shade(base, -14),
    }


# ---------------------------------------------------------------------------
# Entity-Texturen
# ---------------------------------------------------------------------------

def tex_aircraft(base, seed, insignia=True):
    """Flugzeughaut: Paneelraster mit Nieten, dazu Kennzeichen."""
    rng = random.Random(seed)
    image = blank(128, 128, (0, 0, 0, 0))
    # Volle Flaeche fuellen, damit jede Modellflaeche Farbe bekommt.
    for y in range(128):
        for x in range(128):
            image[y][x] = shade(base, rng.randint(-6, 6))
    # Paneellinien.
    for y in range(0, 128, 16):
        for x in range(128):
            image[y][x] = shade(base, -16)
    for x in range(0, 128, 16):
        for y in range(128):
            image[y][x] = shade(base, -16)
    # Nieten entlang der Linien.
    for y in range(4, 128, 16):
        for x in range(4, 128, 8):
            image[y][x] = shade(base, 16)
    # Kante oben heller - wirkt wie Streiflicht.
    for x in range(128):
        image[0][x] = shade(base, 26)

    if insignia:
        # Vereinfachtes Hoheitszeichen (Stern im Kreis) an zwei Stellen.
        for (ox, oy) in ((70, 6), (70, 22)):
            for dy in range(10):
                for dx in range(10):
                    if (dx - 4.5) ** 2 + (dy - 4.5) ** 2 <= 20:
                        image[oy + dy][ox + dx] = (38, 58, 122, 255)
            star = [
                "....11....",
                "....11....",
                ".11111111.",
                "..111111..",
                "..111111..",
                "..11..11..",
                ".1......1.",
                "..........",
            ]
            for dy, line in enumerate(star):
                for dx, char in enumerate(line):
                    if char == "1":
                        image[oy + dy + 1][ox + dx] = (232, 234, 238, 255)
    return image


def tex_soldier(uniform, vest, helmet, accent=None):
    """Klassisches 64x64-Skinlayout mit Uniform, Weste und Helm."""
    image = blank(64, 64, (0, 0, 0, 0))

    def fill(x0, y0, w, h, colour):
        rect(image, x0, y0, x0 + w, y0 + h, colour)

    # Kopf: rundum Helm, vorne ein Gesicht.
    fill(0, 0, 32, 16, helmet)
    fill(8, 8, 8, 8, SKIN)          # Vorderseite = Gesicht
    fill(8, 8, 8, 3, helmet)        # Helmrand ueber den Augen
    put(image, 10, 12, (40, 34, 30, 255))
    put(image, 13, 12, (40, 34, 30, 255))
    fill(8, 14, 8, 2, shade(SKIN, -30))
    # Helmschicht (zweite Ebene) leicht dunkler.
    fill(32, 0, 32, 16, shade(helmet, -14))

    # Rumpf mit Schutzweste.
    fill(16, 16, 24, 16, uniform)
    fill(20, 20, 8, 12, vest)
    fill(32, 20, 8, 12, vest)
    if accent:
        fill(22, 23, 4, 4, accent)

    # Arme.
    fill(40, 16, 16, 16, uniform)
    fill(32, 48, 16, 16, uniform)
    # Handschuhe/Haende.
    fill(44, 28, 4, 4, SKIN)
    fill(36, 60, 4, 4, SKIN)

    # Beine.
    fill(0, 16, 16, 16, shade(uniform, -12))
    fill(16, 48, 16, 16, shade(uniform, -12))
    # Stiefel.
    fill(4, 28, 4, 4, (40, 36, 32, 255))
    fill(20, 60, 4, 4, (40, 36, 32, 255))

    # Uniform-Ueberzieher (zweite Ebene) transparent lassen ausser Weste.
    return image


def tex_drone():
    rng = random.Random(77)
    image = blank(64, 64, (0, 0, 0, 0))
    for y in range(40):
        for x in range(64):
            image[y][x] = shade((58, 46, 46, 255), rng.randint(-8, 8))
    # Sensorauge in Rot.
    rect(image, 2, 18, 10, 26, (26, 22, 22, 255))
    rect(image, 4, 20, 8, 24, DANGER_RED)
    # Rotorblaetter dunkler.
    rect(image, 0, 24, 64, 32, (34, 30, 30, 255))
    return image


def tex_missile(body, tip):
    image = blank(32, 32, (0, 0, 0, 0))
    for y in range(24):
        for x in range(32):
            image[y][x] = body
    rect(image, 0, 0, 32, 2, shade(body, 24))
    rect(image, 0, 6, 32, 8, tip)
    rect(image, 0, 14, 32, 24, shade(body, -18))
    return image


def tex_radar_dish():
    image = blank(32, 32, (0, 0, 0, 0))
    for y in range(32):
        for x in range(32):
            image[y][x] = shade(STEEL, (x + y) % 5 - 2)
    rect(image, 0, 12, 32, 22, shade(STEEL, 18))
    rect(image, 0, 16, 32, 18, (60, 170, 110, 255))
    return image


def tex_icon_128():
    """Mod-Symbol fuer die Modliste."""
    image = blank(128, 128, (0, 0, 0, 0))
    for y in range(128):
        for x in range(128):
            t = y / 128.0
            image[y][x] = (int(24 + 30 * t), int(30 + 40 * t), int(44 + 52 * t), 255)
    # Jet-Silhouette gross in die Mitte.
    for y, line in enumerate(JET_ART):
        for x, char in enumerate(line):
            if char != ".":
                colour = TITANIUM if char in "23" else STEALTH_BLACK
                rect(image, x * 8, y * 8, x * 8 + 8, y * 8 + 8, colour)
    return image


# ---------------------------------------------------------------------------
# JSON-Hilfen
# ---------------------------------------------------------------------------

def write_json(path, payload):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, indent=2, ensure_ascii=False)
        handle.write("\n")


def item_model(name, texture=None):
    write_json(os.path.join(ASSETS, "models", "item", name + ".json"), {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": f"{MOD}:item/{texture or name}"},
    })


def block_item_model(name, model=None):
    write_json(os.path.join(ASSETS, "models", "item", name + ".json"), {
        "parent": f"{MOD}:block/{model or name}"
    })


def cube_all(name, texture=None):
    write_json(os.path.join(ASSETS, "models", "block", name + ".json"), {
        "parent": "minecraft:block/cube_all",
        "textures": {"all": f"{MOD}:block/{texture or name}"},
    })


def simple_blockstate(name, model=None):
    write_json(os.path.join(ASSETS, "blockstates", name + ".json"), {
        "variants": {"": {"model": f"{MOD}:block/{model or name}"}}
    })


def drop_self_loot(name):
    write_json(os.path.join(DATA, "loot_table", "blocks", name + ".json"), {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1.0,
            "bonus_rolls": 0.0,
            "entries": [{"type": "minecraft:item", "name": f"{MOD}:{name}"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
    })


def ore_loot(name, drop):
    write_json(os.path.join(DATA, "loot_table", "blocks", name + ".json"), {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1.0,
            "bonus_rolls": 0.0,
            "entries": [{
                "type": "minecraft:alternatives",
                "children": [
                    {
                        "type": "minecraft:item",
                        "name": f"{MOD}:{name}",
                        "conditions": [{
                            "condition": "minecraft:match_tool",
                            "predicate": {"predicates": {"minecraft:enchantments": [
                                {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}
                            ]}},
                        }],
                    },
                    {
                        "type": "minecraft:item",
                        "name": f"{MOD}:{drop}",
                        "functions": [
                            {"function": "minecraft:apply_bonus",
                             "enchantment": "minecraft:fortune",
                             "formula": "minecraft:ore_drops"},
                            {"function": "minecraft:explosion_decay"},
                        ],
                    },
                ],
            }],
        }],
    })


def shaped(name, pattern, key, result, count=1, category="misc"):
    write_json(os.path.join(DATA, "recipe", name + ".json"), {
        "type": "minecraft:crafting_shaped",
        "category": category,
        "pattern": pattern,
        "key": {k: ({"tag": v[4:]} if v.startswith("tag:") else {"item": v}) for k, v in key.items()},
        "result": {"id": result, "count": count},
    })


def shapeless(name, ingredients, result, count=1, category="misc"):
    write_json(os.path.join(DATA, "recipe", name + ".json"), {
        "type": "minecraft:crafting_shapeless",
        "category": category,
        "ingredients": [{"item": i} for i in ingredients],
        "result": {"id": result, "count": count},
    })


def smelting(name, ingredient, result, experience=0.7, time=200, kind="smelting"):
    write_json(os.path.join(DATA, "recipe", name + ".json"), {
        "type": f"minecraft:{kind}",
        "category": "misc",
        "ingredient": {"item": ingredient},
        "result": {"id": result},
        "experience": experience,
        "cookingtime": time,
    })


# ---------------------------------------------------------------------------
# Erzeugung
# ---------------------------------------------------------------------------

def gen_block_textures():
    out = os.path.join(ASSETS, "textures", "block")
    write_png(os.path.join(out, "runway.png"), tex_asphalt(1))
    write_png(os.path.join(out, "runway_centerline.png"), tex_asphalt(2, "centerline"))
    write_png(os.path.join(out, "runway_threshold.png"), tex_asphalt(3, "threshold"))
    write_png(os.path.join(out, "runway_light.png"), tex_runway_light())
    write_png(os.path.join(out, "hangar_door.png"), tex_hangar_door())
    write_png(os.path.join(out, "hangar_wall.png"), tex_panel(STEEL, 62, stripe=None))
    write_png(os.path.join(out, "radar_top.png"), tex_radar_top())
    write_png(os.path.join(out, "radar_side.png"), tex_radar_side())
    write_png(os.path.join(out, "iron_dome_top.png"), tex_dome_top())
    write_png(os.path.join(out, "iron_dome_side.png"), tex_dome_side())
    write_png(os.path.join(out, "rearm_pad_top.png"), tex_rearm_top())
    write_png(os.path.join(out, "rearm_pad_side.png"), tex_panel(ASPHALT_LIGHT, 42))
    write_png(os.path.join(out, "barracks_side.png"), tex_barracks_side())
    write_png(os.path.join(out, "barracks_front.png"), tex_barracks_front())
    write_png(os.path.join(out, "barracks_top.png"), tex_panel(OLIVE_DARK, 53))
    write_png(os.path.join(out, "titanium_ore.png"), tex_ore((128, 128, 128, 255), 11))
    write_png(os.path.join(out, "deepslate_titanium_ore.png"), tex_ore((80, 80, 84, 255), 12))
    write_png(os.path.join(out, "titanium_block.png"), tex_metal_block(TITANIUM, 13))


ITEM_ICONS = {
    "raw_titanium": (INGOT_ART, metal_palette(RAW_TITANIUM)),
    "titanium_ingot": (INGOT_ART, metal_palette(TITANIUM)),
    "titanium_plate": (PLATE_ART, metal_palette(TITANIUM)),
    "composite_plate": (PLATE_ART, metal_palette(STEEL, accent=STEALTH_BLACK)),
    "avionics_module": (CHIP_ART, metal_palette(STEEL_DARK, accent=(70, 200, 130, 255))),
    "energy_cell": (CHIP_ART, metal_palette(STEEL_DARK, accent=LASER_CYAN)),
    "jet_engine": (CHIP_ART, metal_palette(TITANIUM_DARK, accent=AMBER)),
    "stealth_coating": (PLATE_ART, metal_palette(STEALTH_BLACK, accent=(70, 74, 84, 255))),
    "jet_fuel": (CANISTER_ART, metal_palette(OLIVE, accent=AMBER)),
    "cannon_ammo": (CANISTER_ART, metal_palette(STEEL, accent=(196, 158, 76, 255))),
    "aim_missile": (MISSILE_ART, metal_palette(TITANIUM, accent=DANGER_RED)),
    "interceptor_missile": (MISSILE_ART, metal_palette(STEEL, accent=(80, 200, 140, 255))),
    "aerial_bomb": (BOMB_ART, metal_palette(OLIVE_DARK, accent=AMBER)),
    "laser_rifle": (RIFLE_ART, metal_palette(STEEL_DARK, accent=LASER_CYAN)),
    "railgun": (RIFLE_ART, metal_palette(TITANIUM_DARK, accent=(226, 226, 250, 255))),
    "plasma_launcher": (LAUNCHER_ART, metal_palette(STEEL_DARK, accent=PLASMA)),
    "command_tablet": (TABLET_ART, metal_palette(STEEL_DARK, accent=(60, 180, 120, 255))),
    "f47_aircraft": (JET_ART, metal_palette(JET_GREY, accent=AMBER)),
    "f47_autonomous": (JET_ART, metal_palette(STEALTH_BLACK, accent=LASER_CYAN)),
    "threat_beacon": (BEACON_ART, metal_palette(STEEL_DARK, accent=DANGER_RED)),
    "recruit_rifleman": (ORDER_ART, metal_palette((222, 216, 196, 255), accent=OLIVE)),
    "recruit_heavy": (ORDER_ART, metal_palette((222, 216, 196, 255), accent=DANGER_RED)),
    "recruit_medic": (ORDER_ART, metal_palette((222, 216, 196, 255), accent=(226, 232, 236, 255))),
    "recruit_engineer": (ORDER_ART, metal_palette((222, 216, 196, 255), accent=AMBER)),
    "recruit_pilot": (ORDER_ART, metal_palette((222, 216, 196, 255), accent=LASER_CYAN)),
}


def gen_item_textures():
    out = os.path.join(ASSETS, "textures", "item")
    for name, (art, palette) in ITEM_ICONS.items():
        write_png(os.path.join(out, name + ".png"), icon(art, palette))


def gen_entity_textures():
    out = os.path.join(ASSETS, "textures", "entity")
    write_png(os.path.join(out, "f47.png"), tex_aircraft(JET_GREY, 101))
    write_png(os.path.join(out, "f47_stealth.png"), tex_aircraft(STEALTH_BLACK, 102, insignia=False))
    write_png(os.path.join(out, "soldier_rifleman.png"), tex_soldier(OLIVE, OLIVE_DARK, OLIVE_DARK))
    write_png(os.path.join(out, "soldier_heavy.png"),
              tex_soldier(OLIVE_DARK, (44, 46, 40, 255), (40, 42, 38, 255), accent=DANGER_RED))
    write_png(os.path.join(out, "soldier_medic.png"),
              tex_soldier(OLIVE, (226, 228, 226, 255), (226, 228, 226, 255), accent=DANGER_RED))
    write_png(os.path.join(out, "soldier_engineer.png"),
              tex_soldier(KHAKI, (162, 118, 44, 255), AMBER, accent=(60, 60, 64, 255)))
    write_png(os.path.join(out, "soldier_pilot.png"),
              tex_soldier((62, 68, 76, 255), (48, 52, 60, 255), (70, 76, 86, 255), accent=VISOR))
    write_png(os.path.join(out, "enemy_drone.png"), tex_drone())
    write_png(os.path.join(out, "missile.png"), tex_missile(TITANIUM_DARK, DANGER_RED))
    write_png(os.path.join(out, "missile_enemy.png"), tex_missile((70, 52, 52, 255), AMBER))
    write_png(os.path.join(out, "bomb.png"), tex_missile(OLIVE_DARK, AMBER))
    write_png(os.path.join(out, "radar_dish.png"), tex_radar_dish())
    write_png(os.path.join(ASSETS, "icon.png"), tex_icon_128())


def gen_models_and_states():
    # --- Startbahn ----------------------------------------------------
    for name in ("runway", "runway_light", "hangar_wall", "titanium_block",
                 "titanium_ore", "deepslate_titanium_ore"):
        cube_all(name)
        simple_blockstate(name)
        block_item_model(name)

    # Markierungen: Oberseite eigene Textur, Seiten wie die Bahn.
    for name in ("runway_centerline", "runway_threshold"):
        write_json(os.path.join(ASSETS, "models", "block", name + ".json"), {
            "parent": "minecraft:block/cube_bottom_top",
            "textures": {
                "top": f"{MOD}:block/{name}",
                "side": f"{MOD}:block/runway",
                "bottom": f"{MOD}:block/runway",
            },
        })
        write_json(os.path.join(ASSETS, "blockstates", name + ".json"), {
            "variants": {
                "facing=north": {"model": f"{MOD}:block/{name}"},
                "facing=east": {"model": f"{MOD}:block/{name}", "y": 90},
                "facing=south": {"model": f"{MOD}:block/{name}", "y": 180},
                "facing=west": {"model": f"{MOD}:block/{name}", "y": 270},
            }
        })
        block_item_model(name)

    # --- Hangartor ----------------------------------------------------
    cube_all("hangar_door")
    write_json(os.path.join(ASSETS, "models", "block", "hangar_door_open.json"), {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": f"{MOD}:block/hangar_door",
            "all": f"{MOD}:block/hangar_door",
        },
        "elements": [{
            "from": [0, 14, 0],
            "to": [16, 16, 16],
            "faces": {
                "north": {"uv": [0, 0, 16, 2], "texture": "#all"},
                "east": {"uv": [0, 0, 16, 2], "texture": "#all"},
                "south": {"uv": [0, 0, 16, 2], "texture": "#all"},
                "west": {"uv": [0, 0, 16, 2], "texture": "#all"},
                "up": {"uv": [0, 0, 16, 16], "texture": "#all"},
                "down": {"uv": [0, 0, 16, 16], "texture": "#all"},
            },
        }],
    })
    write_json(os.path.join(ASSETS, "blockstates", "hangar_door.json"), {
        "variants": {
            "open=false,powered=false": {"model": f"{MOD}:block/hangar_door"},
            "open=false,powered=true": {"model": f"{MOD}:block/hangar_door"},
            "open=true,powered=false": {"model": f"{MOD}:block/hangar_door_open"},
            "open=true,powered=true": {"model": f"{MOD}:block/hangar_door_open"},
        }
    })
    block_item_model("hangar_door")

    # --- Radar --------------------------------------------------------
    write_json(os.path.join(ASSETS, "models", "block", "radar.json"), {
        "parent": "minecraft:block/cube_bottom_top",
        "textures": {
            "top": f"{MOD}:block/radar_top",
            "side": f"{MOD}:block/radar_side",
            "bottom": f"{MOD}:block/radar_side",
        },
    })
    write_json(os.path.join(ASSETS, "blockstates", "radar.json"), {
        "variants": {
            "active=true": {"model": f"{MOD}:block/radar"},
            "active=false": {"model": f"{MOD}:block/radar"},
        }
    })
    block_item_model("radar")

    # --- Iron Dome ----------------------------------------------------
    write_json(os.path.join(ASSETS, "models", "block", "iron_dome.json"), {
        "parent": "minecraft:block/cube_bottom_top",
        "textures": {
            "top": f"{MOD}:block/iron_dome_top",
            "side": f"{MOD}:block/iron_dome_side",
            "bottom": f"{MOD}:block/iron_dome_side",
        },
    })
    simple_blockstate("iron_dome")
    block_item_model("iron_dome")

    # --- Wartungsfeld -------------------------------------------------
    write_json(os.path.join(ASSETS, "models", "block", "rearm_pad.json"), {
        "parent": "minecraft:block/cube_bottom_top",
        "textures": {
            "top": f"{MOD}:block/rearm_pad_top",
            "side": f"{MOD}:block/rearm_pad_side",
            "bottom": f"{MOD}:block/rearm_pad_side",
        },
    })
    simple_blockstate("rearm_pad")
    block_item_model("rearm_pad")

    # --- Kaserne ------------------------------------------------------
    write_json(os.path.join(ASSETS, "models", "block", "barracks.json"), {
        "parent": "minecraft:block/orientable",
        "textures": {
            "top": f"{MOD}:block/barracks_top",
            "front": f"{MOD}:block/barracks_front",
            "side": f"{MOD}:block/barracks_side",
        },
    })
    simple_blockstate("barracks")
    block_item_model("barracks")

    # --- Items --------------------------------------------------------
    for name in ITEM_ICONS:
        item_model(name)


def gen_loot():
    for name in ("runway", "runway_centerline", "runway_threshold", "runway_light",
                 "hangar_door", "hangar_wall", "radar", "iron_dome", "rearm_pad",
                 "barracks", "titanium_block"):
        drop_self_loot(name)
    ore_loot("titanium_ore", "raw_titanium")
    ore_loot("deepslate_titanium_ore", "raw_titanium")


def gen_recipes():
    T = f"{MOD}:titanium_ingot"
    P = f"{MOD}:titanium_plate"
    C = f"{MOD}:composite_plate"
    E = f"{MOD}:energy_cell"

    smelting("titanium_ingot_from_smelting", f"{MOD}:raw_titanium", T)
    smelting("titanium_ingot_from_blasting", f"{MOD}:raw_titanium", T,
             experience=0.7, time=100, kind="blasting")

    shaped("titanium_block", ["###", "###", "###"], {"#": T}, f"{MOD}:titanium_block",
           category="building")
    shapeless("titanium_ingot_from_block", [f"{MOD}:titanium_block"], T, count=9)

    shaped("titanium_plate", ["##"], {"#": T}, P, count=3)
    shaped("composite_plate", ["PI", "IP"],
           {"P": P, "I": "minecraft:iron_ingot"}, C, count=2)

    shaped("avionics_module", [" R ", "CAC", " P "],
           {"R": "minecraft:redstone", "C": "minecraft:copper_ingot",
            "A": "minecraft:amethyst_shard", "P": P},
           f"{MOD}:avionics_module")

    shaped("energy_cell", [" P ", "RAR", " P "],
           {"P": P, "R": "minecraft:redstone", "A": "minecraft:amethyst_shard"},
           f"{MOD}:energy_cell")

    shaped("jet_engine", ["PPP", "PRP", "PPP"],
           {"P": P, "R": "minecraft:redstone_block"}, f"{MOD}:jet_engine")

    shapeless("stealth_coating", [C, "minecraft:black_dye", "minecraft:obsidian"],
              f"{MOD}:stealth_coating", count=2)

    # Flugzeuge
    shaped("f47_aircraft", ["SAS", "CEC", "PJP"],
           {"S": f"{MOD}:stealth_coating", "A": f"{MOD}:avionics_module", "C": C,
            "E": E, "P": P, "J": f"{MOD}:jet_engine"},
           f"{MOD}:f47_aircraft", category="equipment")
    shapeless("f47_autonomous", [f"{MOD}:f47_aircraft", f"{MOD}:avionics_module",
                                 "minecraft:redstone_block"],
              f"{MOD}:f47_autonomous", category="equipment")

    # Waffen
    shaped("laser_rifle", ["  A", "PEP", "P  "],
           {"A": "minecraft:amethyst_shard", "P": P, "E": E},
           f"{MOD}:laser_rifle", category="equipment")
    shaped("railgun", ["CCA", "PEP", "P  "],
           {"C": "minecraft:copper_ingot", "A": "minecraft:amethyst_shard", "P": P, "E": E},
           f"{MOD}:railgun", category="equipment")
    shaped("plasma_launcher", ["PPB", "CEC", "PP "],
           {"P": P, "B": "minecraft:blaze_powder", "C": C, "E": E},
           f"{MOD}:plasma_launcher", category="equipment")

    # Munition und Nachschub
    shaped("aim_missile", [" G ", "GPG", " P "],
           {"G": "minecraft:gunpowder", "P": P}, f"{MOD}:aim_missile", count=2)
    shaped("interceptor_missile", [" G ", "GPG", " R "],
           {"G": "minecraft:gunpowder", "P": P, "R": "minecraft:redstone"},
           f"{MOD}:interceptor_missile", count=4)
    shaped("aerial_bomb", ["PPP", "PGP", "PPP"],
           {"P": P, "G": "minecraft:gunpowder"}, f"{MOD}:aerial_bomb", count=2)
    shaped("cannon_ammo", ["II", "GG"],
           {"I": "minecraft:iron_ingot", "G": "minecraft:gunpowder"},
           f"{MOD}:cannon_ammo", count=8)
    shapeless("jet_fuel", ["minecraft:coal", "minecraft:coal", "minecraft:coal",
                           "minecraft:coal", "minecraft:glass_bottle"],
              f"{MOD}:jet_fuel", count=2)

    # Fuehrung
    shaped("command_tablet", ["CCC", "CAC", "CEC"],
           {"C": C, "A": f"{MOD}:avionics_module", "E": E},
           f"{MOD}:command_tablet", category="equipment")
    shaped("threat_beacon", [" R ", "RGR", " R "],
           {"R": "minecraft:redstone", "G": "minecraft:gunpowder"},
           f"{MOD}:threat_beacon", count=2)

    # Basisbau
    shaped("runway", ["SSS", "SBS", "SSS"],
           {"S": "minecraft:smooth_stone", "B": "minecraft:black_dye"},
           f"{MOD}:runway", count=8, category="building")
    shapeless("runway_centerline", [f"{MOD}:runway", "minecraft:white_dye"],
              f"{MOD}:runway_centerline", category="building")
    shapeless("runway_threshold", [f"{MOD}:runway", "minecraft:white_dye",
                                   "minecraft:white_dye"],
              f"{MOD}:runway_threshold", category="building")
    shaped("runway_light", ["G", "R", "S"],
           {"G": "minecraft:glowstone", "R": "minecraft:redstone_lamp",
            "S": f"{MOD}:runway"}, f"{MOD}:runway_light", count=2, category="building")

    shaped("hangar_wall", ["PP", "PP"], {"P": P},
           f"{MOD}:hangar_wall", count=4, category="building")
    shaped("hangar_door", ["PIP", "PIP", "PIP"],
           {"P": P, "I": "minecraft:iron_ingot"},
           f"{MOD}:hangar_door", count=3, category="building")

    shaped("radar", [" A ", "PEP", "III"],
           {"A": f"{MOD}:avionics_module", "P": P, "E": E, "I": "minecraft:iron_block"},
           f"{MOD}:radar")
    shaped("iron_dome", ["MMM", "PAP", "III"],
           {"M": f"{MOD}:interceptor_missile", "P": P,
            "A": f"{MOD}:avionics_module", "I": "minecraft:iron_block"},
           f"{MOD}:iron_dome")
    shaped("rearm_pad", ["PPP", "IHI", "III"],
           {"P": P, "I": "minecraft:iron_ingot", "H": "minecraft:hopper"},
           f"{MOD}:rearm_pad")
    shaped("barracks", ["III", "IBI", "III"],
           {"I": "minecraft:iron_ingot", "B": "minecraft:red_bed"},
           f"{MOD}:barracks")

    # Marschbefehle
    orders = {
        "recruit_rifleman": "minecraft:iron_ingot",
        "recruit_heavy": "minecraft:gunpowder",
        "recruit_medic": "minecraft:golden_apple",
        "recruit_engineer": "minecraft:iron_pickaxe",
        "recruit_pilot": "minecraft:feather",
    }
    for name, marker in orders.items():
        shaped(name, ["PPP", "PMP", "PEP"],
               {"P": "minecraft:paper", "M": marker, "E": "minecraft:emerald"},
               f"{MOD}:{name}")


def gen_damage_types():
    for name, message in (("laser", "f47_laser"), ("missile", "f47_missile"),
                          ("bullet", "f47_bullet")):
        write_json(os.path.join(DATA, "damage_type", name + ".json"), {
            "exhaustion": 0.1,
            "message_id": message,
            "scaling": "when_caused_by_living_non_player",
        })


def gen_worldgen():
    write_json(os.path.join(DATA, "worldgen", "configured_feature", "titanium_ore.json"), {
        "type": "minecraft:ore",
        "config": {
            "discard_chance_on_air_exposure": 0.0,
            "size": 7,
            "targets": [
                {"target": {"predicate_type": "minecraft:tag_match",
                            "tag": "minecraft:stone_ore_replaceables"},
                 "state": {"Name": f"{MOD}:titanium_ore"}},
                {"target": {"predicate_type": "minecraft:tag_match",
                            "tag": "minecraft:deepslate_ore_replaceables"},
                 "state": {"Name": f"{MOD}:deepslate_titanium_ore"}},
            ],
        },
    })
    write_json(os.path.join(DATA, "worldgen", "placed_feature", "titanium_ore_placed.json"), {
        "feature": f"{MOD}:titanium_ore",
        "placement": [
            {"type": "minecraft:count", "count": 6},
            {"type": "minecraft:in_square"},
            {"type": "minecraft:height_range",
             "height": {"type": "minecraft:uniform",
                        "min_inclusive": {"absolute": -32},
                        "max_inclusive": {"absolute": 64}}},
            {"type": "minecraft:biome"},
        ],
    })


def gen_tags():
    # Werkzeuganforderungen, damit die Bloecke nur mit Spitzhacke etwas droppen.
    pickaxe = ["runway", "runway_centerline", "runway_threshold", "runway_light",
               "hangar_door", "hangar_wall", "radar", "iron_dome", "rearm_pad",
               "barracks", "titanium_ore", "deepslate_titanium_ore", "titanium_block"]
    write_json(os.path.join(MAIN, "data", "minecraft", "tags", "block",
                            "mineable", "pickaxe.json"),
               {"replace": False, "values": [f"{MOD}:{name}" for name in pickaxe]})
    write_json(os.path.join(MAIN, "data", "minecraft", "tags", "block",
                            "needs_iron_tool.json"),
               {"replace": False,
                "values": [f"{MOD}:titanium_ore", f"{MOD}:deepslate_titanium_ore",
                           f"{MOD}:titanium_block", f"{MOD}:radar", f"{MOD}:iron_dome"]})


def main():
    gen_block_textures()
    gen_item_textures()
    gen_entity_textures()
    gen_models_and_states()
    gen_loot()
    gen_recipes()
    gen_damage_types()
    gen_worldgen()
    gen_tags()
    print("Assets erzeugt.")


if __name__ == "__main__":
    main()
