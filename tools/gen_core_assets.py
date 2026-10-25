#!/usr/bin/env python3
"""Writes the Mobile Core's assets (owner decisions D13 and D14, the Gyro Cage with the grey / teal / gold states, and
R1 to R3): the textures, the block model, the item model, the blockstate, the loot table and the tags. Edit this
script, never the output.

The block model is twelve cage bars, 2 pixels thick, along the cube's edges (the cage face texture, open in the
middle) and a 10-pixel translucent violet heart (the heart face texture). The ring is not in the model: the client's
renderer draws it in the state's colour. The loot table copies the construct's name and tier onto the dropped item.

  python3 tools/gen_core_assets.py
"""
import json
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MOD = "bsp_mobile"
ASSETS = ROOT / "src/main/resources/assets" / MOD
DATA = ROOT / "src/main/resources/data"
NAME = "mobile_core"

IRON, IRON_D, IRON_L = (0x5B, 0x62, 0x70, 255), (0x3B, 0x42, 0x50, 255), (0x8A, 0x93, 0xA3, 255)
CLEAR = (0, 0, 0, 0)
VIO, VIO_L, VIO_M, VIO_D = (0x6B, 0x4E, 0xA8), (0xB5, 0x8C, 0xFF), (0x8F, 0x6F, 0xD0), (0x4A, 0x35, 0x76)


def png(path, pixels):
    """pixels: rows of (r, g, b) or (r, g, b, a) tuples; the first pixel decides which."""
    h, w = len(pixels), len(pixels[0])
    alpha = len(pixels[0][0]) == 4
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in pixels)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6 if alpha else 2, 0, 0, 0))
                     + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def cage_face():
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            bar = x < 2 or x > 13 or y < 2 or y > 13
            if not bar:
                row.append(CLEAR)
                continue
            outer = x in (0, 15) or y in (0, 15)
            row.append(IRON_D if outer else IRON)
        rows.append(row)
    for x, y in ((0, 0), (15, 0), (0, 15), (15, 15), (1, 1), (14, 1), (1, 14), (14, 14)):
        rows[y][x] = IRON_L
    for i in (7, 8):
        rows[0][i] = rows[15][i] = rows[i][0] = rows[i][15] = IRON_L
    return rows


def heart_face():
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            d = x - y
            row.append(VIO_D if edge else VIO_L if d in (3, 4, -6) else VIO_M if d in (5, -5) else VIO)
        rows.append(row)
    return rows


def textures():
    t = ASSETS / "textures/block"
    png(t / f"{NAME}_cage.png", cage_face())
    png(t / f"{NAME}_heart.png", heart_face())


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")


def bar(frm, to):
    """A cage bar: long faces show a strip of the cage texture, end faces a corner of it."""
    faces = {}
    for side in ("north", "south", "east", "west", "up", "down"):
        faces[side] = {"texture": "#cage", "uv": [0, 0, 16, 2]}
    size = [to[i] - frm[i] for i in range(3)]
    # the two faces across the bar's short ends
    axis = size.index(16)
    ends = {0: ("east", "west"), 1: ("up", "down"), 2: ("north", "south")}[axis]
    for side in ends:
        faces[side] = {"texture": "#cage", "uv": [0, 0, 2, 2]}
    return {"from": frm, "to": to, "faces": faces}


def models():
    elements = []
    for a in (0, 14):
        for b in (0, 14):
            elements.append(bar([0, a, b], [16, a + 2, b + 2]))   # along x
            elements.append(bar([a, 0, b], [a + 2, 16, b + 2]))   # along y
            elements.append(bar([a, b, 0], [a + 2, b + 2, 16]))   # along z
    elements.append({"from": [3, 3, 3], "to": [13, 13, 13],
                     "faces": {s: {"texture": "#heart", "uv": [3, 3, 13, 13]} for s in ("north", "south", "east", "west", "up", "down")}})
    write(ASSETS / "models/block" / f"{NAME}.json", {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:translucent",
        "textures": {"cage": f"{MOD}:block/{NAME}_cage", "heart": f"{MOD}:block/{NAME}_heart", "particle": f"{MOD}:block/{NAME}_cage"},
        "elements": elements})
    write(ASSETS / "models/item" / f"{NAME}.json", {"parent": f"{MOD}:block/{NAME}"})
    write(ASSETS / "blockstates" / f"{NAME}.json", {"variants": {"": {"model": f"{MOD}:block/{NAME}"}}})


def data():
    write(DATA / MOD / "loot_tables/blocks" / f"{NAME}.json", {"type": "minecraft:block", "pools": [{
        "rolls": 1,
        "entries": [{"type": "minecraft:item", "name": f"{MOD}:{NAME}", "functions": [
            {"function": "minecraft:copy_nbt", "source": "block_entity", "ops": [
                {"source": "Name", "target": "BlockEntityTag.Name", "op": "replace"},
                {"source": "Tier", "target": "BlockEntityTag.Tier", "op": "replace"}]}]}],
        "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    write(DATA / "minecraft/tags/blocks/mineable/pickaxe.json", {"replace": False, "values": [f"{MOD}:{NAME}"]})
    write(DATA / "minecraft/tags/blocks/needs_stone_tool.json", {"replace": False, "values": [f"{MOD}:{NAME}"]})
    # blocks a core never takes (R5): BSP-Core's admin blocks and Anti Totem blocks; each optional, so the tag loads without BSP-Core
    admin = ["bank_atm", "safety_deposit_box", "land_office", "vendor_post", "market_stall", "stall_display", "score_screen", "anti_totem", "admin_rack"]
    write(DATA / MOD / "tags/blocks/never_mobile.json", {"replace": False, "values": [{"id": f"bsp_core:{b}", "required": False} for b in admin]})


def main():
    textures()
    models()
    data()
    print(f"{NAME}: 2 textures, block model (13 elements), item model, blockstate, loot table, 3 tags")


if __name__ == "__main__":
    main()
