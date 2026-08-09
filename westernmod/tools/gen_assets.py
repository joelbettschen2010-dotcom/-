#!/usr/bin/env python3
"""Erzeugt sämtliche Texturen und JSON-Ressourcen des Wild-West-Mods.

Alles hier ist deterministisch: zweimal ausführen liefert byte-gleiche Dateien. Damit lassen
sich Texturen jederzeit nachjustieren, ohne von Hand in Pixeln zu wühlen.

    python3 tools/gen_assets.py
"""

from __future__ import annotations

import json
import os
import random
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/westernmod")
DATA = os.path.join(ROOT, "src/main/resources/data/westernmod")
MOD = "westernmod"

CLEAR = (0, 0, 0, 0)


# --------------------------------------------------------------------------------------
# Grundlagen
# --------------------------------------------------------------------------------------

def ensure(path: str) -> str:
    os.makedirs(path, exist_ok=True)
    return path


def write_json(path: str, payload) -> None:
    ensure(os.path.dirname(path))
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, indent=2, ensure_ascii=False)
        handle.write("\n")


def new_image(width: int, height: int) -> Image.Image:
    return Image.new("RGBA", (width, height), CLEAR)


def rect(draw: ImageDraw.ImageDraw, x: int, y: int, w: int, h: int, colour) -> None:
    if w <= 0 or h <= 0:
        return
    draw.rectangle([x, y, x + w - 1, y + h - 1], fill=colour)


def shade(colour, factor: float):
    r, g, b, a = colour
    return (
        max(0, min(255, int(r * factor))),
        max(0, min(255, int(g * factor))),
        max(0, min(255, int(b * factor))),
        a,
    )


def noise(image: Image.Image, x: int, y: int, w: int, h: int, rng: random.Random,
          amount: float = 0.12, density: float = 0.35) -> None:
    """Streut leichte Helligkeitsunterschiede ein, damit Flächen nicht tot wirken."""
    pixels = image.load()
    for py in range(y, y + h):
        for px in range(x, x + w):
            if px >= image.width or py >= image.height:
                continue
            base = pixels[px, py]
            if base[3] == 0 or rng.random() > density:
                continue
            factor = 1.0 + rng.uniform(-amount, amount)
            pixels[px, py] = shade(base, factor)


def box_faces(u: int, v: int, sx: int, sy: int, sz: int) -> dict:
    """UV-Rechtecke der sechs Seiten eines Quaders im Minecraft-Layout."""
    return {
        "up": (u + sz, v, sx, sz),
        "down": (u + sz + sx, v, sx, sz),
        "east": (u, v + sz, sz, sy),
        "north": (u + sz, v + sz, sx, sy),
        "west": (u + sz + sx, v + sz, sz, sy),
        "south": (u + sz + sx + sz, v + sz, sx, sy),
    }


def paint_box(draw: ImageDraw.ImageDraw, u: int, v: int, sx: int, sy: int, sz: int,
              colour, top=None, sides=None) -> dict:
    """Füllt alle Seiten eines Quaders; Ober- und Seitenflächen dürfen abweichen."""
    faces = box_faces(u, v, sx, sy, sz)
    top = top or shade(colour, 1.14)
    sides = sides or shade(colour, 0.88)
    for name, (fx, fy, fw, fh) in faces.items():
        if name == "up":
            fill = top
        elif name == "down":
            fill = shade(colour, 0.7)
        elif name in ("east", "west"):
            fill = sides
        else:
            fill = colour
        rect(draw, fx, fy, fw, fh, fill)
    return faces


# --------------------------------------------------------------------------------------
# Item-Texturen (16x16)
# --------------------------------------------------------------------------------------

BRASS = (198, 156, 61, 255)
BRASS_DARK = (138, 104, 33, 255)
LEAD = (176, 176, 184, 255)
GUN_METAL = (74, 78, 86, 255)
GUN_METAL_LIGHT = (108, 114, 124, 255)
WOOD = (108, 71, 40, 255)
WOOD_LIGHT = (146, 100, 58, 255)
GOLD = (238, 190, 62, 255)
GOLD_DARK = (176, 132, 30, 255)
PAPER = (226, 210, 170, 255)
PAPER_DARK = (192, 172, 130, 255)
INK = (74, 58, 40, 255)
ROPE = (198, 168, 104, 255)
ROPE_DARK = (150, 122, 68, 255)
DYNAMITE_RED = (166, 46, 38, 255)
DYNAMITE_DARK = (120, 30, 26, 255)
WHISKEY_GLASS = (108, 148, 122, 255)
WHISKEY_LIQUID = (186, 118, 38, 255)
BEAN_SAUCE = (128, 58, 34, 255)
TIN = (170, 174, 180, 255)
CANVAS = (226, 220, 200, 255)


def item_ammo(rng):
    img = new_image(16, 16)
    d = ImageDraw.Draw(img)
    for offset, height in ((3, 0), (8, 2)):
        rect(d, offset, 3 + height, 5, 8, BRASS)
        rect(d, offset, 3 + height, 1, 8, shade(BRASS, 1.2))
        rect(d, offset + 4, 3 + height, 1, 8, BRASS_DARK)
        # Geschossspitze
        rect(d, offset + 1, 1 + height, 3, 2, LEAD)
        rect(d, offset + 1, 0 + height, 3, 1, shade(LEAD, 0.8))
        rect(d, offset, 11 + height, 5, 1, BRASS_DARK)
    noise(img, 0, 0, 16, 16, rng)
    return img


def item_gold_coin(rng):
    img = new_image(16, 16)
    d = ImageDraw.Draw(img)
    d.ellipse([3, 3, 12, 12], fill=GOLD, outline=GOLD_DARK)
    d.ellipse([5, 5, 10, 10], outline=GOLD_DARK)
    rect(d, 7, 6, 2, 4, GOLD_DARK)
    noise(img, 3, 3, 10, 10, rng, amount=0.1)
    return img


def item_revolver(rng):
    """Seitenansicht, Lauf nach rechts: Lauf, Trommel, Hahn, Abzugsbügel, Holzgriff."""
    img = new_image(16, 16)
    d = ImageDraw.Draw(img)

    # Lauf mit Korn
    rect(d, 6, 4, 9, 2, GUN_METAL)
    rect(d, 6, 4, 9, 1, GUN_METAL_LIGHT)
    rect(d, 14, 3, 1, 1, GUN_METAL_LIGHT)
    rect(d, 15, 4, 1, 2, (36, 38, 44, 255))
    # Auswurfstange unter dem Lauf
    rect(d, 9, 6, 5, 1, shade(GUN_METAL, 0.85))

    # Trommel
    rect(d, 4, 3, 5, 5, GUN_METAL_LIGHT)
    rect(d, 5, 4, 1, 1, GUN_METAL)
    rect(d, 7, 4, 1, 1, GUN_METAL)
    rect(d, 5, 6, 1, 1, GUN_METAL)
    rect(d, 7, 6, 1, 1, GUN_METAL)

    # Hahn
    rect(d, 3, 2, 2, 2, GUN_METAL)
    rect(d, 2, 3, 1, 2, GUN_METAL_LIGHT)

    # Abzug und Bügel
    rect(d, 6, 8, 1, 1, GUN_METAL)
    rect(d, 5, 9, 4, 1, GUN_METAL)
    rect(d, 8, 8, 1, 1, GUN_METAL)

    # Griff, nach hinten unten geneigt
    for row, (x, width) in enumerate(((3, 4), (3, 4), (2, 4), (2, 4), (1, 4), (1, 3))):
        y = 7 + row
        rect(d, x, y, width, 1, WOOD)
        rect(d, x, y, 1, 1, WOOD_LIGHT)
    rect(d, 3, 7, 3, 1, shade(WOOD, 0.75))

    noise(img, 0, 0, 16, 16, rng, amount=0.1)
    return img


def item_winchester(rng):
    img = new_image(16, 16)
    d = ImageDraw.Draw(img)
    for i in range(12):
        # Diagonaler Lauf von unten links nach oben rechts
        x = 2 + i
        y = 12 - i
        rect(d, x, y, 2, 1, GUN_METAL if i > 3 else WOOD)
        if i > 3:
            rect(d, x, y - 1, 2, 1, GUN_METAL_LIGHT)
    # Schaft
    rect(d, 1, 11, 4, 3, WOOD)
    rect(d, 1, 11, 4, 1, WOOD_LIGHT)
    rect(d, 4, 9, 3, 2, WOOD_LIGHT)
    # Unterhebel
    rect(d, 5, 11, 3, 1, GUN_METAL)
    noise(img, 0, 0, 16, 16, rng, amount=0.1)
    return img


def item_shotgun(rng):
    img = new_image(16, 16)
    d = ImageDraw.Draw(img)
    rect(d, 5, 5, 10, 2, GUN_METAL)
    rect(d, 5, 7, 10, 2, GUN_METAL_LIGHT)
    rect(d, 14, 5, 1, 4, (40, 42, 48, 255))
    rect(d, 2, 5, 3, 5, WOOD_LIGHT)
    rect(d, 1, 8, 3, 5, WOOD)
    rect(d, 4, 9, 1, 2, GUN_METAL)
    noise(img, 0, 0, 16, 16, rng, amount=0.1)
    return img


def item_dynamite(rng):
    img = new_image(16, 16)
    d = ImageDraw.Draw(img)
    for x in (3, 6, 9):
        rect(d, x, 5, 3, 9, DYNAMITE_RED)
        rect(d, x, 5, 1, 9, shade(DYNAMITE_RED, 1.25))
        rect(d, x + 2, 5, 1, 9, DYNAMITE_DARK)
        rect(d, x, 8, 3, 1, PAPER_DARK)
    # Bindfaden und Lunte
    rect(d, 3, 11, 9, 1, INK)
    rect(d, 11, 2, 1, 3, ROPE_DARK)
    rect(d, 12, 1, 1, 2, ROPE)
    rect(d, 13, 0, 1, 1, (255, 196, 74, 255))
    noise(img, 0, 0, 16, 16, rng, amount=0.1)
    return img


def item_town_deed(rng):
    img = new_image(16, 16)
    d = ImageDraw.Draw(img)
    rect(d, 2, 2, 12, 12, PAPER)
    rect(d, 2, 2, 12, 1, PAPER_DARK)
    rect(d, 2, 13, 12, 1, PAPER_DARK)
    for y in (5, 7, 9):
        rect(d, 4, y, 8, 1, INK)
    rect(d, 4, 11, 5, 1, INK)
    # Siegel
    d.ellipse([9, 10, 12, 13], fill=DYNAMITE_RED)
    noise(img, 2, 2, 12, 12, rng, amount=0.08)
    return img


def item_sheriff_star(rng):
    img = new_image(16, 16)
    d = ImageDraw.Draw(img)
    points = [(8, 1), (10, 6), (15, 6), (11, 9), (13, 14), (8, 11), (3, 14), (5, 9), (1, 6), (6, 6)]
    d.polygon(points, fill=GOLD, outline=GOLD_DARK)
    d.ellipse([6, 6, 9, 9], fill=GOLD_DARK)
    noise(img, 0, 0, 16, 16, rng, amount=0.1)
    return img


def item_lasso(rng):
    img = new_image(16, 16)
    d = ImageDraw.Draw(img)
    d.ellipse([2, 4, 13, 13], outline=ROPE, width=2)
    d.ellipse([4, 6, 11, 11], outline=ROPE_DARK)
    rect(d, 8, 1, 1, 4, ROPE)
    rect(d, 9, 0, 2, 1, ROPE_DARK)
    noise(img, 0, 0, 16, 16, rng, amount=0.1)
    return img


def item_wagon(rng):
    img = new_image(16, 16)
    d = ImageDraw.Draw(img)
    # Plane
    rect(d, 3, 3, 10, 5, CANVAS)
    rect(d, 3, 3, 10, 1, shade(CANVAS, 1.08))
    rect(d, 4, 2, 8, 1, CANVAS)
    # Wagenkasten
    rect(d, 2, 8, 12, 3, WOOD)
    rect(d, 2, 8, 12, 1, WOOD_LIGHT)
    # Räder mit Speichen
    for cx, cy, radius in ((4, 12, 3), (11, 12, 3)):
        d.ellipse([cx - radius, cy - radius, cx + radius, cy + radius], outline=(64, 46, 30, 255))
        rect(d, cx - radius + 1, cy, 2 * radius - 1, 1, (64, 46, 30, 255))
        rect(d, cx, cy - radius + 1, 1, 2 * radius - 1, (64, 46, 30, 255))
    noise(img, 0, 0, 16, 16, rng, amount=0.1)
    return img


def item_whiskey(rng):
    img = new_image(16, 16)
    d = ImageDraw.Draw(img)
    rect(d, 6, 1, 4, 3, WHISKEY_GLASS)
    rect(d, 5, 4, 6, 10, WHISKEY_GLASS)
    rect(d, 6, 7, 4, 6, WHISKEY_LIQUID)
    rect(d, 5, 4, 1, 10, shade(WHISKEY_GLASS, 1.3))
    rect(d, 6, 0, 4, 1, INK)
    rect(d, 6, 8, 4, 2, PAPER)
    noise(img, 0, 0, 16, 16, rng, amount=0.08)
    return img


def item_beans(rng):
    img = new_image(16, 16)
    d = ImageDraw.Draw(img)
    rect(d, 3, 4, 10, 9, TIN)
    rect(d, 3, 4, 10, 1, shade(TIN, 1.2))
    rect(d, 3, 12, 10, 1, shade(TIN, 0.75))
    rect(d, 4, 6, 8, 5, BEAN_SAUCE)
    for bx, by in ((5, 7), (7, 6), (9, 8), (6, 9), (10, 6)):
        rect(d, bx, by, 2, 1, (86, 58, 32, 255))
    rect(d, 2, 4, 1, 9, shade(TIN, 1.3))
    noise(img, 0, 0, 16, 16, rng, amount=0.08)
    return img


ITEM_TEXTURES = {
    "ammo": item_ammo,
    "gold_coin": item_gold_coin,
    "revolver": item_revolver,
    "winchester": item_winchester,
    "shotgun": item_shotgun,
    "dynamite": item_dynamite,
    "town_deed": item_town_deed,
    "sheriff_star": item_sheriff_star,
    "lasso": item_lasso,
    "wagon": item_wagon,
    "whiskey": item_whiskey,
    "beans": item_beans,
}


# --------------------------------------------------------------------------------------
# Block-Texturen
# --------------------------------------------------------------------------------------

def block_hitching_post(rng):
    img = new_image(16, 16)
    d = ImageDraw.Draw(img)
    rect(d, 0, 0, 16, 16, WOOD)
    for x in range(0, 16, 3):
        rect(d, x, 0, 1, 16, shade(WOOD, 0.82))
    rect(d, 0, 4, 16, 1, WOOD_LIGHT)
    rect(d, 0, 11, 16, 1, shade(WOOD, 0.7))
    noise(img, 0, 0, 16, 16, rng, amount=0.14, density=0.5)
    return img


def block_saloon_door(rng, top: bool):
    img = new_image(16, 16)
    d = ImageDraw.Draw(img)
    if top:
        # Oberes Drittel bleibt offen — Schwingtüren reichen nur bis zur Brust.
        rect(d, 0, 11, 16, 5, WOOD_LIGHT)
        rect(d, 0, 11, 16, 1, shade(WOOD_LIGHT, 1.2))
        for x in range(1, 16, 4):
            rect(d, x, 12, 1, 4, shade(WOOD_LIGHT, 0.85))
        noise(img, 0, 11, 16, 5, rng, amount=0.12, density=0.5)
    else:
        rect(d, 0, 0, 16, 16, WOOD_LIGHT)
        for x in range(1, 16, 4):
            rect(d, x, 0, 1, 16, shade(WOOD_LIGHT, 0.85))
        rect(d, 0, 0, 16, 1, shade(WOOD_LIGHT, 1.2))
        rect(d, 0, 7, 16, 1, WOOD)
        rect(d, 13, 7, 2, 2, (60, 60, 66, 255))
        noise(img, 0, 0, 16, 16, rng, amount=0.12, density=0.5)
    return img


def block_wanted_poster(rng):
    img = new_image(16, 16)
    d = ImageDraw.Draw(img)
    rect(d, 1, 1, 14, 14, PAPER)
    rect(d, 1, 1, 14, 1, PAPER_DARK)
    rect(d, 1, 14, 14, 1, PAPER_DARK)
    # "WANTED"
    rect(d, 3, 3, 10, 2, INK)
    # Portrait
    rect(d, 5, 6, 6, 5, PAPER_DARK)
    rect(d, 5, 6, 6, 2, (92, 72, 52, 255))
    rect(d, 6, 9, 4, 1, (120, 60, 52, 255))
    # "$ REWARD"
    rect(d, 3, 12, 10, 1, INK)
    noise(img, 1, 1, 14, 14, rng, amount=0.08)
    return img


# --------------------------------------------------------------------------------------
# Entity-Texturen
# --------------------------------------------------------------------------------------

SKIN = (198, 150, 110, 255)
SKIN_DARK = (156, 112, 78, 255)
BEARD = (74, 54, 38, 255)


def biped_skin(rng, hat_colour, shirt, trousers, boots, bandana=None, star=False, vest=None):
    """Malt ein 64x64-Blatt für das Revolvermann-Modell."""
    img = new_image(64, 64)
    d = ImageDraw.Draw(img)

    # Kopf (8x8x8) bei uv 0,0
    head = paint_box(d, 0, 0, 8, 8, 8, SKIN, top=SKIN_DARK, sides=shade(SKIN, 0.94))
    fx, fy, _, _ = head["north"]
    rect(d, fx + 1, fy + 3, 2, 1, (46, 34, 26, 255))  # Augen
    rect(d, fx + 5, fy + 3, 2, 1, (46, 34, 26, 255))
    if bandana:
        # Tuch über der unteren Gesichtshälfte, rundum
        for name in ("north", "east", "west", "south"):
            bx, by, bw, bh = head[name]
            rect(d, bx, by + 5, bw, 3, bandana)
        rect(d, fx + 2, fy + 5, 4, 1, shade(bandana, 0.8))
    else:
        rect(d, fx + 2, fy + 6, 4, 1, SKIN_DARK)  # Mund
        rect(d, fx + 1, fy + 5, 6, 1, BEARD)      # Schnauzer
    # Haaransatz
    for name in ("east", "west", "south"):
        bx, by, bw, bh = head[name]
        rect(d, bx, by, bw, 2, BEARD)

    # Zweite Kopfebene bleibt leer — der Hut ist eigene Geometrie.

    # Körper (8x12x4) bei uv 16,16
    body = paint_box(d, 16, 16, 8, 12, 4, shirt)
    bx, by, _, _ = body["north"]
    if vest:
        rect(d, bx, by, 2, 12, vest)
        rect(d, bx + 6, by, 2, 12, vest)
    rect(d, bx, by + 9, 8, 3, trousers)          # Gürtellinie
    rect(d, bx, by + 9, 8, 1, (58, 44, 32, 255))
    rect(d, bx + 3, by + 9, 2, 1, BRASS)          # Gürtelschnalle
    if star:
        rect(d, bx + 5, by + 2, 2, 2, GOLD)
        rect(d, bx + 4, by + 3, 4, 1, GOLD)
        rect(d, bx + 5, by + 4, 2, 1, GOLD_DARK)

    # Arme (4x12x4)
    for u, v in ((40, 16), (32, 48)):
        arm = paint_box(d, u, v, 4, 12, 4, shirt)
        ax, ay, _, _ = arm["north"]
        rect(d, ax, ay + 9, 4, 3, SKIN)           # Hände
        rect(d, ax, ay + 8, 4, 1, shade(shirt, 0.75))

    # Beine (4x12x4)
    for u, v in ((0, 16), (16, 48)):
        leg = paint_box(d, u, v, 4, 12, 4, trousers)
        lx, ly, _, _ = leg["north"]
        rect(d, lx, ly + 9, 4, 3, boots)
        rect(d, lx, ly + 9, 4, 1, shade(boots, 1.2))

    # Hutkrempe (10x1x10) bei uv 0,32
    paint_box(d, 0, 32, 10, 1, 10, hat_colour,
              top=shade(hat_colour, 1.12), sides=shade(hat_colour, 0.8))
    # Hutkopf (6x4x6) bei uv 40,32
    crown = paint_box(d, 40, 32, 6, 4, 6, hat_colour,
                      top=shade(hat_colour, 1.12), sides=shade(hat_colour, 0.85))
    for name in ("north", "east", "west", "south"):
        cx, cy, cw, ch = crown[name]
        rect(d, cx, cy + ch - 1, cw, 1, shade(hat_colour, 0.65))  # Hutband

    noise(img, 0, 0, 64, 64, rng, amount=0.08, density=0.3)
    return img


def entity_wagon(rng):
    img = new_image(128, 128)
    d = ImageDraw.Draw(img)

    horse = (108, 74, 46, 255)
    horse_dark = (78, 52, 32, 255)
    wood = (122, 84, 48, 255)
    iron = (92, 96, 104, 255)

    paint_box(d, 0, 0, 8, 8, 13, horse)                 # Rumpf
    paint_box(d, 44, 0, 5, 8, 5, horse)                 # Hals
    head = paint_box(d, 64, 0, 5, 5, 7, horse)          # Kopf
    hx, hy, _, _ = head["north"]
    rect(d, hx + 1, hy + 1, 1, 1, (30, 24, 20, 255))
    rect(d, hx + 3, hy + 1, 1, 1, (30, 24, 20, 255))
    rect(d, hx, hy + 3, 5, 2, horse_dark)               # Maul
    paint_box(d, 88, 0, 3, 14, 3, horse)                # Bein
    leg = box_faces(88, 0, 3, 14, 3)
    for name in ("north", "east", "west", "south"):
        lx, ly, lw, lh = leg[name]
        rect(d, lx, ly + lh - 2, lw, 2, (38, 32, 28, 255))   # Hufe
    paint_box(d, 100, 0, 2, 6, 2, horse_dark)           # Schweif

    paint_box(d, 36, 110, 2, 2, 14, wood)               # Deichsel
    bed = paint_box(d, 0, 24, 16, 3, 24, wood)          # Ladefläche
    bx, by, bw, bh = bed["up"]
    for i in range(0, bw, 3):
        rect(d, bx + i, by, 1, bh, shade(wood, 0.82))   # Bretterfugen
    paint_box(d, 0, 52, 1, 6, 24, shade(wood, 0.92))    # Seitenwand
    paint_box(d, 0, 110, 14, 2, 3, shade(wood, 1.1))    # Kutschbock

    lower = paint_box(d, 52, 52, 16, 8, 20, CANVAS,
                      top=shade(CANVAS, 1.05), sides=shade(CANVAS, 0.9))
    for name in ("east", "west"):
        cx, cy, cw, ch = lower[name]
        for i in range(0, cw, 4):
            rect(d, cx + i, cy, 1, ch, shade(CANVAS, 0.82))   # Spriegel
    paint_box(d, 0, 84, 13, 5, 19, CANVAS,
              top=shade(CANVAS, 1.08), sides=shade(CANVAS, 0.92))

    wheel(d, 64, 84, 14, 2, wood, iron)
    wheel(d, 96, 84, 10, 2, wood, iron)

    noise(img, 0, 0, 128, 128, rng, amount=0.07, density=0.25)
    return img


def wheel(d: ImageDraw.ImageDraw, u: int, v: int, size: int, thickness: int, wood, iron) -> None:
    """Malt ein Speichenrad auf Vorder- und Rückseite; der Rest bleibt durchsichtig."""
    faces = box_faces(u, v, size, size, thickness)
    for name in ("north", "south"):
        fx, fy, fw, fh = faces[name]
        radius = size / 2.0
        centre = radius - 0.5
        d.ellipse([fx, fy, fx + fw - 1, fy + fh - 1], outline=iron, width=1)
        d.ellipse([fx + 1, fy + 1, fx + fw - 2, fy + fh - 2], outline=wood, width=1)
        # Speichen als Kreuz
        d.line([fx + centre, fy + 1, fx + centre, fy + fh - 2], fill=wood)
        d.line([fx + 1, fy + centre, fx + fw - 2, fy + centre], fill=wood)
        d.line([fx + 2, fy + 2, fx + fw - 3, fy + fh - 3], fill=wood)
        d.line([fx + fw - 3, fy + 2, fx + 2, fy + fh - 3], fill=wood)
        rect(d, int(fx + centre - 1), int(fy + centre - 1), 3, 3, iron)
    # Lauffläche als schmaler Reifen
    for name in ("up", "down", "east", "west"):
        fx, fy, fw, fh = faces[name]
        rect(d, fx, fy, fw, fh, CLEAR)


def entity_tumbleweed(rng):
    img = new_image(64, 64)
    d = ImageDraw.Draw(img)
    twig = (146, 122, 68, 255)
    twig_dark = (110, 90, 48, 255)

    def tangle(u, v, sx, sy, sz):
        faces = box_faces(u, v, sx, sy, sz)
        for name in ("north", "south"):
            fx, fy, fw, fh = faces[name]
            local = random.Random(1337 + u + v + (0 if name == "north" else 7))
            for _ in range(26):
                x1 = fx + local.randrange(fw)
                y1 = fy + local.randrange(fh)
                x2 = min(fx + fw - 1, max(fx, x1 + local.randrange(-4, 5)))
                y2 = min(fy + fh - 1, max(fy, y1 + local.randrange(-4, 5)))
                d.line([x1, y1, x2, y2], fill=twig if local.random() < 0.6 else twig_dark)

    tangle(0, 0, 12, 12, 1)
    tangle(28, 0, 1, 12, 12)
    tangle(0, 26, 12, 1, 12)
    return img


def mod_icon(rng):
    img = new_image(128, 128)
    d = ImageDraw.Draw(img)
    # Abendhimmel über der Prärie
    for y in range(128):
        t = y / 127.0
        colour = (
            int(58 + 180 * (1 - t) ** 1.5),
            int(38 + 120 * (1 - t) ** 2),
            int(70 + 40 * (1 - t) ** 3),
            255,
        )
        rect(d, 0, y, 128, 1, colour)
    d.ellipse([44, 34, 84, 74], fill=(248, 196, 92, 255))
    rect(d, 0, 86, 128, 42, (92, 62, 38, 255))
    rect(d, 0, 86, 128, 3, (126, 88, 52, 255))
    # Kaktus
    rect(d, 20, 56, 10, 32, (58, 96, 52, 255))
    rect(d, 12, 64, 8, 6, (58, 96, 52, 255))
    rect(d, 12, 52, 6, 14, (58, 96, 52, 255))
    rect(d, 30, 60, 8, 6, (58, 96, 52, 255))
    rect(d, 34, 46, 6, 18, (58, 96, 52, 255))
    # Hut als Silhouette
    rect(d, 78, 62, 34, 6, (38, 28, 24, 255))
    rect(d, 86, 48, 18, 16, (38, 28, 24, 255))
    rect(d, 86, 56, 18, 4, (96, 40, 34, 255))
    return img


# --------------------------------------------------------------------------------------
# JSON-Ressourcen
# --------------------------------------------------------------------------------------

HANDHELD = {"revolver", "winchester", "shotgun", "lasso"}


def gen_item_models():
    for name in ITEM_TEXTURES:
        parent = "minecraft:item/handheld" if name in HANDHELD else "minecraft:item/generated"
        write_json(os.path.join(ASSETS, f"models/item/{name}.json"), {
            "parent": parent,
            "textures": {"layer0": f"{MOD}:item/{name}"},
        })


def cube_faces(texture: str):
    """Alle sechs Seiten auf dieselbe Textur; ohne cullface, da es Teilblöcke sind."""
    return {face: {"texture": texture} for face in ("north", "south", "east", "west", "up", "down")}


def gen_block_assets():
    post = f"{MOD}:block/hitching_post"

    # Anbindepfosten: zwei Pfosten mit Querbalken, gebaut für facing=north.
    write_json(os.path.join(ASSETS, "models/block/hitching_post.json"), {
        "parent": "block/block",
        "textures": {"post": post, "particle": post},
        "elements": [
            {"from": [6, 0, 1], "to": [10, 14, 4], "faces": cube_faces("#post")},
            {"from": [6, 0, 12], "to": [10, 14, 15], "faces": cube_faces("#post")},
            {"from": [7, 9, 0], "to": [9, 12, 16], "faces": cube_faces("#post")},
        ],
    })
    write_json(os.path.join(ASSETS, "blockstates/hitching_post.json"), {
        "variants": {
            "facing=north": {"model": f"{MOD}:block/hitching_post"},
            "facing=south": {"model": f"{MOD}:block/hitching_post", "y": 180},
            "facing=east": {"model": f"{MOD}:block/hitching_post", "y": 90},
            "facing=west": {"model": f"{MOD}:block/hitching_post", "y": 270},
        }
    })
    write_json(os.path.join(ASSETS, "models/item/hitching_post.json"),
               {"parent": f"{MOD}:block/hitching_post"})

    # Steckbrief: flache Tafel an der Wand, gebaut für facing=north (Wand im Süden).
    poster = f"{MOD}:block/wanted_poster"
    write_json(os.path.join(ASSETS, "models/block/wanted_poster.json"), {
        "parent": "block/block",
        "textures": {"poster": poster, "particle": poster},
        "elements": [
            {"from": [2, 2, 14], "to": [14, 15, 15.5], "faces": cube_faces("#poster")},
        ],
    })
    write_json(os.path.join(ASSETS, "blockstates/wanted_poster.json"), {
        "variants": {
            "facing=north": {"model": f"{MOD}:block/wanted_poster"},
            "facing=south": {"model": f"{MOD}:block/wanted_poster", "y": 180},
            "facing=east": {"model": f"{MOD}:block/wanted_poster", "y": 90},
            "facing=west": {"model": f"{MOD}:block/wanted_poster", "y": 270},
        }
    })
    write_json(os.path.join(ASSETS, "models/item/wanted_poster.json"), {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": f"{MOD}:block/wanted_poster"},
    })

    # Saloontür: Modelle erben von den Vanilla-Türformen, nur die Texturen wechseln.
    door_textures = {
        "bottom": f"{MOD}:block/saloon_door_bottom",
        "top": f"{MOD}:block/saloon_door_top",
    }
    for half in ("bottom", "top"):
        for hinge in ("left", "right"):
            for state in ("", "_open"):
                name = f"saloon_door_{half}_{hinge}{state}"
                write_json(os.path.join(ASSETS, f"models/block/{name}.json"), {
                    "parent": f"minecraft:block/door_{half}_{hinge}{state}",
                    "textures": door_textures,
                })
    write_json(os.path.join(ASSETS, "models/item/saloon_door.json"), {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": f"{MOD}:item/saloon_door"},
    })

    base_rotation = {"east": 0, "south": 90, "west": 180, "north": 270}
    variants = {}
    for facing, base in base_rotation.items():
        for half, model_half in (("lower", "bottom"), ("upper", "top")):
            for hinge in ("left", "right"):
                for open_state in (False, True):
                    if not open_state:
                        rotation = base
                        model = f"{MOD}:block/saloon_door_{model_half}_{hinge}"
                    else:
                        rotation = (base + (90 if hinge == "left" else 270)) % 360
                        model = f"{MOD}:block/saloon_door_{model_half}_{hinge}_open"
                    entry = {"model": model}
                    if rotation:
                        entry["y"] = rotation
                    key = f"facing={facing},half={half},hinge={hinge},open={str(open_state).lower()}"
                    variants[key] = entry
    write_json(os.path.join(ASSETS, "blockstates/saloon_door.json"), {"variants": variants})


def gen_lang():
    en = {
        "itemgroup.westernmod.western": "Wild West",

        "item.westernmod.ammo": "Cartridges",
        "item.westernmod.gold_coin": "Gold Dollar",
        "item.westernmod.revolver": "Revolver",
        "item.westernmod.winchester": "Winchester Rifle",
        "item.westernmod.shotgun": "Shotgun",
        "item.westernmod.dynamite": "Dynamite",
        "item.westernmod.town_deed": "Town Charter",
        "item.westernmod.sheriff_star": "Sheriff's Star",
        "item.westernmod.lasso": "Lasso",
        "item.westernmod.wagon": "Covered Wagon",
        "item.westernmod.whiskey": "Whiskey",
        "item.westernmod.beans": "Beans and Bacon",

        "block.westernmod.hitching_post": "Hitching Post",
        "block.westernmod.saloon_door": "Saloon Doors",
        "block.westernmod.wanted_poster": "Wanted Poster",

        "entity.westernmod.cowboy": "Cowboy",
        "entity.westernmod.sheriff": "Sheriff",
        "entity.westernmod.bandit": "Bandit",
        "entity.westernmod.bandit_leader": "Outlaw Boss",
        "entity.westernmod.wagon": "Covered Wagon",
        "entity.westernmod.tumbleweed": "Tumbleweed",
        "entity.westernmod.bullet": "Bullet",
        "entity.westernmod.dynamite": "Dynamite",

        "tooltip.westernmod.gun.damage": "Damage: %s",
        "tooltip.westernmod.gun.pellets": "Fires %s pellets per shot",
        "tooltip.westernmod.gun.ammo": "Needs cartridges in your inventory",
        "tooltip.westernmod.town_deed.1": "Right-click the ground to found a western town.",
        "tooltip.westernmod.town_deed.2": "Watch out — outlaws camp nearby.",
        "tooltip.westernmod.dynamite": "Throw it. Then step back.",
        "tooltip.westernmod.lasso": "Tames horses instantly, leashes everything else.",
        "tooltip.westernmod.sheriff_star": "Call a manhunt on every outlaw nearby.",
        "tooltip.westernmod.wagon.1": "Place it, then right-click to drive.",
        "tooltip.westernmod.wagon.2": "Sneak + right-click opens the cargo bed.",

        "message.westernmod.no_ammo": "Out of cartridges!",
        "message.westernmod.town.building": "Staking out the town...",
        "message.westernmod.town.done": "The town is open for business!",
        "message.westernmod.manhunt": "Manhunt! %s outlaws marked, %s lawmen on the case.",
        "message.westernmod.bounty": "Bounty posted — %s outlaws are riding in!",
        "message.westernmod.lasso.tamed": "%s is yours now.",
    }

    de = {
        "itemgroup.westernmod.western": "Wilder Westen",

        "item.westernmod.ammo": "Patronen",
        "item.westernmod.gold_coin": "Golddollar",
        "item.westernmod.revolver": "Revolver",
        "item.westernmod.winchester": "Winchester",
        "item.westernmod.shotgun": "Schrotflinte",
        "item.westernmod.dynamite": "Dynamit",
        "item.westernmod.town_deed": "Siedlungsurkunde",
        "item.westernmod.sheriff_star": "Sheriffstern",
        "item.westernmod.lasso": "Lasso",
        "item.westernmod.wagon": "Planwagen",
        "item.westernmod.whiskey": "Whiskey",
        "item.westernmod.beans": "Bohnen mit Speck",

        "block.westernmod.hitching_post": "Anbindepfosten",
        "block.westernmod.saloon_door": "Saloontür",
        "block.westernmod.wanted_poster": "Steckbrief",

        "entity.westernmod.cowboy": "Cowboy",
        "entity.westernmod.sheriff": "Sheriff",
        "entity.westernmod.bandit": "Bandit",
        "entity.westernmod.bandit_leader": "Bandenchef",
        "entity.westernmod.wagon": "Planwagen",
        "entity.westernmod.tumbleweed": "Steppenläufer",
        "entity.westernmod.bullet": "Kugel",
        "entity.westernmod.dynamite": "Dynamit",

        "tooltip.westernmod.gun.damage": "Schaden: %s",
        "tooltip.westernmod.gun.pellets": "Verschiesst %s Kugeln pro Schuss",
        "tooltip.westernmod.gun.ammo": "Braucht Patronen im Inventar",
        "tooltip.westernmod.town_deed.1": "Rechtsklick auf den Boden gründet eine Westernstadt.",
        "tooltip.westernmod.town_deed.2": "Vorsicht — Banditen lagern in der Nähe.",
        "tooltip.westernmod.dynamite": "Werfen. Dann Abstand halten.",
        "tooltip.westernmod.lasso": "Zähmt Pferde sofort, alles andere kommt an die Leine.",
        "tooltip.westernmod.sheriff_star": "Ruft eine Fahndung auf alle Banditen ringsum aus.",
        "tooltip.westernmod.wagon.1": "Aufstellen, dann per Rechtsklick losfahren.",
        "tooltip.westernmod.wagon.2": "Schleichen + Rechtsklick öffnet die Ladefläche.",

        "message.westernmod.no_ammo": "Keine Patronen mehr!",
        "message.westernmod.town.building": "Die Stadt wird abgesteckt...",
        "message.westernmod.town.done": "Die Stadt steht!",
        "message.westernmod.manhunt": "Fahndung! %s Banditen markiert, %s Gesetzeshüter unterwegs.",
        "message.westernmod.bounty": "Kopfgeld ausgesetzt — %s Banditen reiten an!",
        "message.westernmod.lasso.tamed": "%s gehört jetzt dir.",
    }

    write_json(os.path.join(ASSETS, "lang/en_us.json"), en)
    write_json(os.path.join(ASSETS, "lang/de_de.json"), de)


def uniform(minimum: float, maximum: float):
    return {"type": "minecraft:uniform", "min": minimum, "max": maximum}


def entity_loot(entries):
    return {
        "type": "minecraft:entity",
        "pools": [
            {
                "rolls": 1.0,
                "bonus_rolls": 0.0,
                "entries": [entry],
            }
            for entry in entries
        ],
    }


def item_entry(item, minimum=None, maximum=None, conditions=None):
    entry = {"type": "minecraft:item", "name": item}
    if minimum is not None:
        entry["functions"] = [{"function": "minecraft:set_count", "add": False,
                               "count": uniform(minimum, maximum)}]
    if conditions:
        entry["conditions"] = conditions
    return entry


def gen_loot_tables():
    killed_by_player = [{"condition": "minecraft:killed_by_player"}]

    tables = {
        "entities/cowboy": entity_loot([
            item_entry(f"{MOD}:ammo", 0.0, 2.0),
            item_entry("minecraft:leather", 0.0, 1.0),
        ]),
        "entities/sheriff": entity_loot([
            item_entry(f"{MOD}:ammo", 1.0, 4.0),
            item_entry(f"{MOD}:gold_coin", 0.0, 2.0, killed_by_player),
        ]),
        "entities/bandit": entity_loot([
            item_entry(f"{MOD}:ammo", 0.0, 3.0),
            item_entry(f"{MOD}:gold_coin", 0.0, 1.0, killed_by_player),
        ]),
        "entities/bandit_leader": entity_loot([
            item_entry(f"{MOD}:ammo", 3.0, 8.0),
            item_entry(f"{MOD}:gold_coin", 3.0, 7.0),
            item_entry(f"{MOD}:sheriff_star", None, None, killed_by_player),
        ]),
        "entities/wagon": entity_loot([item_entry(f"{MOD}:wagon")]),
        "entities/tumbleweed": entity_loot([item_entry("minecraft:dead_bush", 0.0, 1.0)]),
    }

    for name, table in tables.items():
        write_json(os.path.join(DATA, f"loot_table/{name}.json"), table)

    def block_table(block, conditions=None):
        entry = {"type": "minecraft:item", "name": block}
        if conditions:
            entry["conditions"] = conditions
        return {
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1.0,
                "bonus_rolls": 0.0,
                "conditions": [{"condition": "minecraft:survives_explosion"}],
                "entries": [entry],
            }],
        }

    write_json(os.path.join(DATA, "loot_table/blocks/hitching_post.json"),
               block_table(f"{MOD}:hitching_post"))
    write_json(os.path.join(DATA, "loot_table/blocks/wanted_poster.json"),
               block_table(f"{MOD}:wanted_poster"))
    write_json(os.path.join(DATA, "loot_table/blocks/saloon_door.json"),
               block_table(f"{MOD}:saloon_door", [{
                   "condition": "minecraft:block_state_property",
                   "block": f"{MOD}:saloon_door",
                   "properties": {"half": "lower"},
               }]))


def shaped(pattern, key, result, count=1, category="misc"):
    return {
        "type": "minecraft:crafting_shaped",
        "category": category,
        "pattern": pattern,
        "key": key,
        "result": {"id": result, "count": count},
    }


def shapeless(ingredients, result, count=1, category="misc"):
    return {
        "type": "minecraft:crafting_shapeless",
        "category": category,
        "ingredients": ingredients,
        "result": {"id": result, "count": count},
    }


def gen_recipes():
    iron = {"item": "minecraft:iron_ingot"}
    nugget = {"item": "minecraft:iron_nugget"}
    powder = {"item": "minecraft:gunpowder"}
    planks = {"tag": "minecraft:planks"}
    stick = {"item": "minecraft:stick"}
    string = {"item": "minecraft:string"}
    paper = {"item": "minecraft:paper"}
    gold = {"item": "minecraft:gold_ingot"}
    log = {"tag": "minecraft:logs"}

    recipes = {
        "ammo": shaped(["N", "P"], {"N": nugget, "P": powder}, f"{MOD}:ammo", 4),
        "revolver": shaped([" II", "IWI", "W  "],
                           {"I": iron, "W": planks}, f"{MOD}:revolver"),
        "winchester": shaped(["III", "WWI", "W  "],
                             {"I": iron, "W": planks}, f"{MOD}:winchester"),
        "shotgun": shaped(["II ", "IWW", " W "],
                          {"I": iron, "W": planks}, f"{MOD}:shotgun"),
        "dynamite": shaped(["S", "P", "G"],
                           {"S": string, "P": paper, "G": powder}, f"{MOD}:dynamite", 2),
        "lasso": shaped([" SS", "S S", "SS "], {"S": string}, f"{MOD}:lasso"),
        "sheriff_star": shaped([" G ", "GIG", " G "],
                               {"G": gold, "I": iron}, f"{MOD}:sheriff_star"),
        "town_deed": shaped(["PPP", "PGP", "PPP"],
                            {"P": paper, "G": {"item": "minecraft:emerald_block"}},
                            f"{MOD}:town_deed"),
        "wagon": shaped(["WWW", "WCW", "S S"],
                        {"W": planks, "C": {"item": "minecraft:chest"}, "S": log},
                        f"{MOD}:wagon"),
        "hitching_post": shaped(["LLL", "L L"], {"L": log}, f"{MOD}:hitching_post", 2),
        "saloon_door": shaped(["WW", "WW", "WW"], {"W": planks}, f"{MOD}:saloon_door", 3),
        "wanted_poster": shapeless([paper, {"item": "minecraft:ink_sac"}], f"{MOD}:wanted_poster"),
        "whiskey": shapeless([{"item": "minecraft:glass_bottle"}, {"item": "minecraft:sugar_cane"},
                              {"item": "minecraft:brown_mushroom"}], f"{MOD}:whiskey", 1, "food"),
        "beans": shapeless([{"item": "minecraft:bowl"}, {"item": "minecraft:cooked_porkchop"},
                            {"item": "minecraft:wheat_seeds"}], f"{MOD}:beans", 1, "food"),
    }

    for name, recipe in recipes.items():
        write_json(os.path.join(DATA, f"recipe/{name}.json"), recipe)


# --------------------------------------------------------------------------------------

def main():
    rng = random.Random(20250809)

    textures = ensure(os.path.join(ASSETS, "textures"))
    item_dir = ensure(os.path.join(textures, "item"))
    block_dir = ensure(os.path.join(textures, "block"))
    entity_dir = ensure(os.path.join(textures, "entity"))

    for name, painter in ITEM_TEXTURES.items():
        painter(rng).save(os.path.join(item_dir, f"{name}.png"))

    # Das Saloontür-Item benutzt eine eigene, volle Ansicht.
    door_item = new_image(16, 16)
    dd = ImageDraw.Draw(door_item)
    rect(dd, 3, 1, 10, 13, WOOD_LIGHT)
    for x in range(4, 13, 3):
        rect(dd, x, 1, 1, 13, shade(WOOD_LIGHT, 0.85))
    rect(dd, 3, 6, 10, 1, WOOD)
    door_item.save(os.path.join(item_dir, "saloon_door.png"))

    block_hitching_post(rng).save(os.path.join(block_dir, "hitching_post.png"))
    block_saloon_door(rng, top=True).save(os.path.join(block_dir, "saloon_door_top.png"))
    block_saloon_door(rng, top=False).save(os.path.join(block_dir, "saloon_door_bottom.png"))
    block_wanted_poster(rng).save(os.path.join(block_dir, "wanted_poster.png"))

    biped_skin(rng, hat_colour=(150, 112, 62, 255), shirt=(178, 96, 62, 255),
               trousers=(62, 78, 122, 255), boots=(78, 52, 34, 255)) \
        .save(os.path.join(entity_dir, "cowboy.png"))
    biped_skin(rng, hat_colour=(92, 74, 52, 255), shirt=(74, 96, 138, 255),
               trousers=(58, 58, 72, 255), boots=(64, 44, 30, 255),
               star=True, vest=(58, 50, 44, 255)) \
        .save(os.path.join(entity_dir, "sheriff.png"))
    biped_skin(rng, hat_colour=(52, 46, 44, 255), shirt=(88, 74, 68, 255),
               trousers=(60, 52, 48, 255), boots=(44, 34, 28, 255),
               bandana=(158, 54, 46, 255)) \
        .save(os.path.join(entity_dir, "bandit.png"))
    biped_skin(rng, hat_colour=(36, 32, 34, 255), shirt=(46, 42, 48, 255),
               trousers=(40, 36, 40, 255), boots=(32, 26, 24, 255),
               bandana=(34, 34, 38, 255), vest=(96, 30, 28, 255)) \
        .save(os.path.join(entity_dir, "bandit_leader.png"))

    entity_wagon(rng).save(os.path.join(entity_dir, "wagon.png"))
    entity_tumbleweed(rng).save(os.path.join(entity_dir, "tumbleweed.png"))
    mod_icon(rng).save(os.path.join(ASSETS, "icon.png"))

    gen_item_models()
    gen_block_assets()
    gen_lang()
    gen_loot_tables()
    gen_recipes()

    print("Assets generated.")


if __name__ == "__main__":
    main()
