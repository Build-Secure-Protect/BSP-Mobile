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
    # the three parts the core is crafted from (D22, D25) and their tooltip line
    "item.bsp_mobile.mobile_core_processor": "Mobile Core Processor",
    "item.bsp_mobile.mobile_core_gps": "Mobile Core GPS",
    "item.bsp_mobile.mobile_core_gyroscope": "Mobile Core Gyroscope",
    "item.bsp_mobile.part.tooltip": "A part of the Mobile Core.",
    # the Mobile Core Lock (D27 to D31)
    "item.bsp_mobile.mobile_core_lock": "Mobile Core Lock",
    "item.bsp_mobile.mobile_core_lock.tooltip": "Right-click a Mobile Core to bind it to you. Your team may still use it; anyone else must steal it.",
    # chat messages from the server (the lock and the steal)
    "message.bsp_mobile.lock.on": "%s is locked to you.",
    "message.bsp_mobile.steal.warning": "%s is stealing your Mobile Core %s! %s minutes to stop them.",
    "message.bsp_mobile.steal.begun": "You are stealing %s from %s. Stay within %s blocks for %s minutes.",
    "message.bsp_mobile.steal.lost": "%s has taken your Mobile Core %s.",
    "message.bsp_mobile.steal.won": "%s is yours.",
    "message.bsp_mobile.steal.failed": "The steal of %s failed.",
    "message.bsp_mobile.steal.saved": "The steal of your Mobile Core %s failed.",
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
    "state.moving_short": "Moving",
    "state.locked": "Locked · %s",
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
    "aboard.totem_capped": "Totem aboard: slowed and height-capped",
    "owner": "Owner: %s",
    "helm.open": "Helm: open to anyone",
    "key.assemble": "Assemble",
    "key.release": "Release",
    "key.sure": "Sure? Release",
    "key.outline": "Show outline",
    "stop_first": "stop first",
    "locked_hint": "locked",
    "key.unlock": "Unlock",
    "key.sure_unlock": "Sure? Unlock",
    "key.steal": "Steal",
    "key.sure_steal": "Sure? Steal",
    "key.stealing": "Stealing…",
    "steal.line": "%s is stealing this core · %s left",
    "steal.paused": "%s is stealing this core · paused, thief away · %s left",
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
    "upgrade.key": "Buy Tier %s",
    "upgrade.sure": "Sure? Buy Tier %s",
    "upgrade.top": "Top tier",
    "upgrade.carried": "Coins carried: worth %s",
    "upgrade.carried_none": "Coins carried: none",
    "upgrade.short": "Not enough coins carried. Anyone at the core may buy a tier.",
    "upgrade.enough": "Anyone at the core may buy a tier. Coins are taken from the buyer, with change.",
    "upgrade.no_coins": "Upgrades need BSP-Core's coins.",
    "upgrade.at_top": "This core carries as much as a core can.",
    "upgrade.locked": "Locked: only the owner and their team may buy a tier.",
    # Shatter Coins, named by their tier (BSP-Core's coins.one and coins.many take the count and the tier's name)
    "coins.one": "%s %s Shatter Coin",
    "coins.many": "%s %s Shatter Coins",
    "coin.copper": "Copper",
    "coin.gold": "Gold",
    "coin.diamond": "Diamond",
    "coin.netherite": "Netherite",
    "coin.illyrium": "Illyrium",
    # the server's answers, shown for a few seconds above the keys
    "note.assemble.ok": "Assembled. The construct stands where it was built.",
    "note.release.ok": "Released. The blocks are back in the world.",
    "note.rename.ok": "Renamed.",
    "note.upgrade.ok": "Upgraded. The allowance has grown.",
    "note.fail.short": "Not enough coins carried.",
    "note.fail.top": "Top tier already.",
    "note.fail.no_coins": "Upgrades need BSP-Core's coins.",
    "note.fail.price": "No price is set for this tier.",
    "note.unlock.ok": "Unlocked. The lock is back in your inventory.",
    "note.steal.ok": "The steal has begun. Stay within reach of the core.",
    "note.fail.locked": "Locked by its owner.",
    "note.fail.not_locked": "Not locked.",
    "note.fail.stealing": "A steal is running.",
    "note.fail.yours": "This core already admits you.",
    "note.fail.far": "Stand closer to the core.",
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
