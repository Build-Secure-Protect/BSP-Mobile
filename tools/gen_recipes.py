#!/usr/bin/env python3
"""Writes the crafting recipes (owner decisions D22, D24 and D27): the three parts the Mobile Core is made of, each a shaped
recipe in the crafting table, the core itself from the three parts, and the Mobile Core Lock. Edit this script, never the output.

The BSP metals come from BSP-Core (bsp_core:tetrium_ingot, bsp_core:magnatite_ingot; BSP-Core is a required mod, so
the recipes always load). Everything else is vanilla.

  python3 tools/gen_recipes.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MOD = "bsp_mobile"
RECIPES = ROOT / "src/main/resources/data" / MOD / "recipes"

TETRIUM = "bsp_core:tetrium_ingot"
MAGNATITE = "bsp_core:magnatite_ingot"

# name -> (pattern of three rows, key letter -> item id)
RECIPES_SHAPED = {
    # 4 Tetrium Ingots at the corners, 4 Redstone at the edges, a Diamond in the middle
    "mobile_core_processor": (["TRT", "RDR", "TRT"], {"T": TETRIUM, "R": "minecraft:redstone", "D": "minecraft:diamond"}),
    # a Compass in the middle, 4 Tetrium Ingots at the edges, 4 Glass Panes at the corners
    "mobile_core_gps": (["GTG", "TCT", "GTG"], {"G": "minecraft:glass_pane", "T": TETRIUM, "C": "minecraft:compass"}),
    # 4 Magnatite Ingots at the edges, 4 Iron Bars at the corners, a Gold Ingot in the middle
    "mobile_core_gyroscope": (["BMB", "MGM", "BMB"], {"B": "minecraft:iron_bars", "M": MAGNATITE, "G": "minecraft:gold_ingot"}),
    # the Mobile Core Lock (D27): 4 Tetrium Ingots at the edges, 4 Iron Ingots at the corners, a Magnatite Ingot in the middle
    "mobile_core_lock": (["ITI", "TMT", "ITI"], {"I": "minecraft:iron_ingot", "T": TETRIUM, "M": MAGNATITE}),
    # Iron Bars at the corners, the GPS top-middle, the Processor left-middle, the Gyroscope right-middle,
    # a Tetrium Ingot bottom-middle, an Amethyst Block in the middle
    "mobile_core": (["BSB", "PAY", "BTB"], {"B": "minecraft:iron_bars", "S": f"{MOD}:mobile_core_gps", "P": f"{MOD}:mobile_core_processor",
                                          "Y": f"{MOD}:mobile_core_gyroscope", "T": TETRIUM, "A": "minecraft:amethyst_block"}),
}


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")


def main():
    for name, (pattern, key) in RECIPES_SHAPED.items():
        used = {c for row in pattern for c in row if c != " "}
        if used != set(key):
            raise SystemExit(f"{name}: the pattern uses {sorted(used)} but the key defines {sorted(key)}")
        write(RECIPES / f"{name}.json", {
            "type": "minecraft:crafting_shaped",
            "category": "misc",
            "pattern": pattern,
            "key": {k: {"item": v} for k, v in key.items()},
            "result": {"item": f"{MOD}:{name}", "count": 1},
        })
    print(f"{len(RECIPES_SHAPED)} shaped recipes -> {RECIPES.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
