#!/usr/bin/env python3
"""Regenerates the mod's block/item textures from Create's own art.

Every texture is derived from a Create source PNG so the palette always matches
vanilla Create. Run from the repo root, with the Create dev checkout sitting
next to this repo (../Create-mc1.21.1-dev):

    python3 tools/generate_textures.py

Design language (see also the computer/sensor/receiver block models):
- Computer  = brass encased shaft + dark terminal screen sides + copper FE port on top
- Receiver  = Create redstone link receiver (red) + green channel LED pad
- Sensor    = green-recoloured link sibling + centre lens pad + green-tipped antenna
- Controller = Create linked controller with green channel gems
"""

from PIL import Image
import os

HERE = os.path.dirname(os.path.abspath(__file__))
CR = os.path.join(HERE, "..", "..", "Create-mc1.21.1-dev",
                  "src/main/resources/assets/create/textures")
OUT = os.path.join(HERE, "..", "src/main/resources/assets/computermod/textures")


def load(p):
    return Image.open(p).convert('RGBA')


def red_to_green(im):
    """Swap strongly-red pixels to green (channel swap keeps shading).

    The threshold (r > 1.6*g and r > 1.6*b) catches Create's redstone reds but
    leaves the brown wood and brass tones untouched.
    """
    im = im.copy()
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a and r > 1.6 * g and r > 1.6 * b:
                px[x, y] = (g, r, b, a)
    return im


# Shared palette
DARK = (24, 28, 24, 255)
RIM = (52, 50, 46, 255)
GREEN = (95, 224, 138, 255)    # bright PCB green
GREEND = (46, 90, 60, 255)     # dim PCB green
GLASS = (200, 255, 217, 255)   # lens highlight


def paint_pad(im, bright_center, lens=False):
    """4x4 indicator pad art at px(26,26)-(29,29) of the 32x32 bridge texture,
    1px side-rim strip at y30. Mapped by the models at uv [13,13,15,15] /
    [13,15,15,15.5]."""
    px = im.load()
    for y in range(26, 30):
        for x in range(26, 30):
            px[x, y] = RIM
    for (x, y) in [(27, 27), (28, 27), (27, 28), (28, 28)]:
        px[x, y] = bright_center
    if lens:
        px[27, 27] = GLASS  # glint
    for x in range(26, 30):
        px[x, 30] = (38, 36, 33, 255)
    return im


def main():
    # ---- Sensor / Receiver: based on Create's redstone link ----
    bridge = load(f"{CR}/block/redstone_bridge.png")
    antenna = load(f"{CR}/block/redstone_antenna.png")
    antenna_pow = load(f"{CR}/block/redstone_antenna_powered.png")

    # Receiver: faithful red link + green LED pad (tinted by GLOW in code)
    paint_pad(bridge.copy(), GREEN).save(f"{OUT}/block/receiver_bridge.png")
    antenna.save(f"{OUT}/block/receiver_antenna.png")

    # Sensor: green 'data' sibling + lens pad
    paint_pad(red_to_green(bridge), GREEND, lens=True).save(f"{OUT}/block/sensor_bridge.png")
    red_to_green(antenna).save(f"{OUT}/block/sensor_antenna.png")
    red_to_green(antenna_pow).save(f"{OUT}/block/sensor_antenna_powered.png")

    # ---- Channel Controller: Create's linked controller, green channel gems ----
    red_to_green(load(f"{CR}/item/linked_controller.png")) \
        .save(f"{OUT}/item/channel_controller.png")
    red_to_green(load(f"{CR}/item/linked_controller_powered.png")) \
        .save(f"{OUT}/item/channel_controller_powered.png")

    # ---- Computer ----
    brass = load(f"{CR}/block/brass_casing.png")
    copper = load(f"{CR}/block/copper_casing.png")

    # Side faces: brass casing frame + dark terminal screen with green code
    side = brass.copy()
    px = side.load()
    for y in range(2, 14):
        for x in range(2, 14):
            px[x, y] = (42, 33, 24, 255)               # bezel
    for y in range(3, 13):
        for x in range(3, 13):
            px[x, y] = (8, 13, 10, 255) if y == 3 else (12, 18, 14, 255)

    def line(y, x0, x1, c=GREEND):
        for x in range(x0, x1):
            px[x, y] = c

    line(5, 4, 9)
    line(7, 4, 11)
    line(9, 4, 8)
    line(11, 4, 6)
    px[7, 11] = GREEN  # cursor
    side.save(f"{OUT}/block/computer_side.png")

    # Top face: brass frame + copper power plate with socket = the FE input cue
    top = brass.copy()
    px = top.load()
    cpx = copper.load()
    for y in range(2, 14):
        for x in range(2, 14):
            px[x, y] = cpx[x, y]                       # copper inner plate
    for y in range(5, 11):
        for x in range(5, 11):
            px[x, y] = (33, 26, 22, 255)               # socket
    for y in range(6, 10):
        for x in range(6, 10):
            px[x, y] = (22, 16, 13, 255)
    for (x, y) in [(3, 3), (12, 3), (3, 12), (12, 12)]:
        px[x, y] = (234, 178, 143, 255)                # bolts
    px[11, 2] = GREEND                                 # charge LED
    px[12, 2] = GREEN
    top.save(f"{OUT}/block/computer_top.png")
    print("textures regenerated")


if __name__ == "__main__":
    main()
