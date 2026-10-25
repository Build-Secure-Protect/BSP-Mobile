#!/usr/bin/env python3
"""Writes BSP-Mobile's language file. Edit this script, never the output.

  python3 tools/gen_lang.py
"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "src/main/resources/assets/bsp_mobile/lang/en_us.json"

KEYS = {
    "bsp_mobile.name": "BSP-Mobile",
    "block.bsp_mobile.mobile_core": "Mobile Core",
    "item.bsp_mobile.mobile_core.tier": "Tier %s",
}
# The core's screen (gui.bsp_mobile.*; every key the client code looks up with Tr.t)
GUI = {
    "core.title": "Mobile core",
    "tab.core": "Core",
    "tab.blocks": "Blocks",
    "tab.upgrade": "Upgrade",
    "tab.helm": "Helm",
    "tab.plan": "Plan",
    "name": "Name",
    "state.not_assembled": "Not assembled",
    "state.parked": "Parked",
    "state.moving": "Moving · %s blocks/s",
    "allowance": "Allowance · Tier %s",
    "of_blocks": "of %s blocks",
    "left_out": "Left out: %s",
    "left_out_none": "Nothing left out.",
    "too_many": "More than %s blocks touch this core. Build smaller, or upgrade the core.",
    "note.idle": "Blocks touching this core, counted now. The outline shows them.",
    "note.parked": "The construct's blocks. Build onto it and the count follows.",
    "note.moving": "The construct's blocks.",
    "aboard": "Aboard",
    "aboard.totems": "%s Shatter Totems",
    "aboard.chests": "%s chests · %s machines",
    "owner": "Owner: %s",
    "helm.open": "Helm: open to anyone",
    "key.assemble": "Assemble",
    "key.release": "Release",
    "key.sure": "Sure? Release",
    "key.outline": "Show outline",
    "stop_first": "stop first",
    "blocks.header": "What the core takes · %s of %s · %s kinds",
    "blocks.left_out": "%s left out",
    "blocks.footer": "Blocks the core refuses: bedrock and anything that cannot be mined, another Mobile Core, admin blocks and Anti Totem blocks, and blocks the engine lists as unmovable.",
    "reason.cannot_move": "cannot move",
    "reason.another_core": "another Mobile Core",
    "reason.admin_block": "admin block",
    "reason.engine_refuses": "the engine refuses",
    "upgrade.header": "The tier ladder",
    "upgrade.tier": "Tier %s",
    "upgrade.blocks": "%s blocks",
    "upgrade.this": "this core",
    "upgrade.soon": "Buying a tier comes with the next task.",
    "upgrade.key": "Buy Tier %s",
    "upgrade.top": "Top tier",
    # the server's answers, shown for a few seconds above the keys
    "note.assemble.ok": "Assembled. The construct stands where it was built.",
    "note.release.ok": "Released. The blocks are back in the world.",
    "note.rename.ok": "Renamed.",
    "note.fail.too_many": "Too many blocks for this tier.",
    "note.fail.alone": "Nothing touches the core.",
    "note.fail.moving": "Stop first.",
    "note.fail.engine": "The engine refused.",
    "note.fail.blocked": "No room to set the blocks down.",
    "note.fail.height": "Too high or too low to set the blocks down.",
    "note.fail.gone": "The construct is gone.",
    "note.fail.assembled": "Already assembled.",
    "note.fail.not_assembled": "Not assembled.",
    "note.fail.no_core": "No core here.",
    "note.fail.name": "A name is needed.",
}
KEYS.update({"gui.bsp_mobile." + k: v for k, v in GUI.items()})


def main():
    LANG.parent.mkdir(parents=True, exist_ok=True)
    LANG.write_text(json.dumps(KEYS, indent=2, ensure_ascii=False) + "\n")
    print(f"{len(KEYS)} keys -> {LANG.relative_to(LANG.parents[6])}")


if __name__ == "__main__":
    main()
