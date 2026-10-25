# Testing

Headless checks run with `./gradlew runGameTestServer`. The sections below are the in-game checks, one per task, newest at the bottom.

## 2026-10-25: MB-0, the project

Headless: 2 GameTests (`mod_is_loaded_beside_valkyrien_skies_and_bsp_core_and_a_mock_player_can_be_made`, `eight_blocks_become_a_construct_where_they_stood_and_can_be_taken_back_out`).

In game:
1. Put `bsp-mobile-0.1.0.jar` in a client's and a server's `mods` folder beside Valkyrien Skies 2.4.11 (1.20.1 Forge), Kotlin for Forge 4.12.0 and BSP-Core 1.4.5. The game and the server start, and the mod list shows "BSP-Mobile 0.1.0". The server log says "BSP-Mobile: constructs run on Valkyrien Skies 2.4.11".
2. Remove BSP-Core from a test server: it still starts with this mod and Valkyrien Skies alone (BSP-Core is optional).
3. Remove Valkyrien Skies: Forge refuses to start with a missing-dependency screen naming it (it is required).

## 2026-10-25: MB-1, the Mobile Core

Headless: 8 GameTests pass (twice running): the gather counts every block touching the core and stops at air; it refuses bedrock, another core and BSP-Core's Anti Totem block with their reasons; it refuses more blocks than the allowance; assembling makes a parked construct whose core, now in the shipyard, knows its ship; releasing puts every block back (stone bricks, stairs unchanged, a chest with its diamonds, the core with its name and no ship); the dropped core keeps its name and tier. **Nothing of this task has run in a client.**

In game (survival unless said):
1. Craft or take a Mobile Core from the Functional Blocks tab (no recipe yet). Hold it: the tooltip says "Tier I". Place it in a small build (a 3 x 3 floor of planks with the core on it, a chest beside the core with some items). The block is an iron cage with a violet heart and a grey ring inside; it looks the same from every side.
2. Right-click the core. The screen opens: MOBILE CORE, tabs CORE BLOCKS UPGRADE (HELM and PLAN greyed), the name "Construct 1", "Not assembled", the count of blocks touching the core out of 200, "Nothing left out.", the Aboard box (0 totems, 1 chest, 0 machines, your name), ASSEMBLE and SHOW OUTLINE. In the world every counted block is boxed in teal while the screen is open; SHOW OUTLINE toggles the boxes.
3. Put a bedrock block (creative) touching the build: the count stays, "Left out: Bedrock 1" shows in red, and that block is boxed in red. The BLOCKS tab lists it first in red with "cannot move", then the kinds with counts. Remove it again.
4. Type a name in the name box: a second after you stop typing, "Renamed." shows; close and reopen the screen: the name stays.
5. ASSEMBLE. "Assembled." shows; the build does not move or fall (it is parked); the chip says "Parked"; the ring turns teal and the heart glows; the count still shows the blocks. Walk onto the build and open the chest: the items are there. Try to mine the core: it does not break.
6. Build a few more blocks onto the construct and reopen the screen: the count has grown.
7. RELEASE: the key turns red "SURE? RELEASE" for three seconds; click it again. "Released." shows; the blocks are ordinary world blocks again where they stood, the chest keeps its items, the ring is grey, and the core can be mined. Its dropped item shows the name and "Tier I"; place it again and the name is back.
8. Build a line of more than 200 blocks touching a core: the count turns red with "More than 200 blocks touch this core"; ASSEMBLE answers "Too many blocks for this tier."
9. Place a second core touching the build: the first core's screen lists it in red as "another Mobile Core" and the construct assembles without it.
10. On a server: `serverconfig/bsp_mobile-server.toml` appears with `tiers.allowance`, `gather.neverMobile` and `constructs.movingSpeed`.

## 2026-10-25: MB-2, tiers, upgrades and the parts

Headless: 12 GameTests pass (twice running), the four new ones: buying a tier takes the price with change (a buyer carrying one copper unit more than Tier II's price, as the largest coins, ends with Tier II and one copper unit); a short buyer is refused with "short" and keeps their coins; the top tier is refused with "top"; the four recipes exist, each fills nine slots and makes one item from the ingredients of D24. **Nothing of this task has run in a client.**

In game (survival unless said; BSP-Core must be installed now):
1. Remove BSP-Core from a test server: Forge refuses to start with a missing-dependency screen naming it (it is required since this task).
2. In the crafting table: 4 Tetrium Ingots at the corners, 4 Redstone at the edges and a Diamond in the middle make a Mobile Core Processor; a Compass in the middle, 4 Tetrium Ingots at the edges and 4 Glass Panes at the corners make a Mobile Core GPS; 4 Magnatite Ingots at the edges, 4 Iron Bars at the corners and a Gold Ingot in the middle make a Mobile Core Gyroscope. Each shows its icon (a pinned violet chip, a handheld unit with an antenna and a teal screen, a blue gimbal on an iron base) and the tooltip line "A part of the Mobile Core."
3. Iron Bars at the four corners, the GPS top-middle, the Processor left-middle, the Gyroscope right-middle, a Tetrium Ingot bottom-middle and an Amethyst Block in the middle make a Mobile Core ("Tier I"). The three parts and the core sit together in the Functional Blocks creative tab; the parts also appear in the recipe book.
4. Place the core and open UPGRADE: the ladder shows Tier I "this core" and the prices on the rows above (6 Gold Shatter Coins, 3 Diamond, 2 Netherite, 1 Illyrium), the line "Coins carried: none" and the hint that not enough is carried; BUY TIER II is greyed.
5. Take 1 Diamond Shatter Coin and 1 Gold Shatter Coin from BSP-Core's tab (worth 6 copper units; Tier II costs 12): still greyed. Add another Diamond coin (worth 10): still greyed. Add one more Gold coin (worth 12): the carried line turns teal and BUY TIER II lights up.
6. Click BUY TIER II: it turns red "SURE? BUY TIER II" for three seconds; click again. "Upgraded. The allowance has grown." shows, the row moves to Tier II "this core", the CORE tab says "Allowance · Tier II" with 500 blocks, and the coins are gone (change in copper when you overpaid: give 1 Netherite and 1 Diamond coin, worth 12 for a 12 price, then 1 Netherite, 1 Diamond and 1 Copper, worth 13: 1 Copper stays).
7. A second player standing at the same core with coins may buy Tier III (anyone at the core may buy).
8. At Tier V the key reads "Top tier" and the hint says the core carries as much as a core can.
9. Break the Tier II core: the dropped item says "Tier II"; placed again it keeps the tier and 500 blocks.
10. On a server: `serverconfig/bsp_mobile-server.toml` gains `tiers.prices`; set an entry to `gold:1`, restart, and that step costs 1 Gold Shatter Coin; remove the list's last entry and Tier V's row shows no price and cannot be bought ("No price is set for this tier." when forced).

## 2026-10-25: MB-3, the Mobile Core Lock and the rules aboard

Headless: 19 GameTests pass (twice running), the seven new ones: the lock binds the core to the player and is used up (a second lock is refused and kept); a locked core refuses a stranger's assemble, rename and upgrade as "locked", admits the owner, and a stranger cannot mine it; the owner unlocks and gets the lock back, a stranger cannot, an unlocked core cannot be unlocked; the steal refuses an unlocked core, a thief ten blocks away, the owner and a second thief, and the owner cannot unlock mid-steal; a steal shortened to two seconds ends with the thief owning the core, admitted by the lock, the old owner refused; a thief 30 blocks away with one second of grace fails the steal and the owner keeps the core; a construct is capped exactly when a placed Shatter Totem is aboard, with the config's figures. Teams are not covered headlessly (BSP-Core's api makes no teams). **Nothing of this task has run in a client.**

In game (two accounts needed for the steal; survival unless said):
1. Craft a Mobile Core Lock: 4 Tetrium Ingots at the edges, 4 Iron Ingots at the corners, a Magnatite Ingot in the middle. The icon is a padlock with a violet body; the tooltip explains it. It sits beside the core and the parts in Functional Blocks.
2. Right-click a placed core with the lock: the lock is used up, chat says the construct is locked to you, and the CORE tab shows a violet "Locked · <you>" chip beside the state chip and UNLOCK at the right end of the key row. In creative the lock is not used up.
3. As the second account (not on your team), open the core: the chip shows the owner, ASSEMBLE is greyed with "locked", the name box cannot be typed in, BUY on UPGRADE is greyed with the locked hint, and STEAL stands at the right end of the key row. Try to mine the core: no cracks appear.
4. Stand more than 3 blocks away and click STEAL twice: "Stand closer to the core." Stand within 3 blocks and click STEAL, then "SURE? STEAL": chat tells you the steal has begun (the construct, its owner, 3 blocks, 5 minutes); the owner (and their team) get a red warning. Both screens show the red bar and "<thief> is stealing this core · 4:59 left"; the chip turns red; the owner's UNLOCK is refused with "A steal is running."
5. Walk 5 blocks away: the line says "paused, thief away" and the time stops. Come back within 30 seconds: it resumes. Stay away longer: chat says the steal failed; the owner is told it failed; the bar is gone, the lock stays.
6. Start again and stay: after 5 minutes chat says the construct is yours, the owner is told it was taken, the chip shows your name, and you may assemble, rename and unlock; the old owner is now refused.
7. As the owner, UNLOCK twice: the lock is back in your inventory, the chip is gone, anyone may use the core again. As a BSP-Core admin who is not the owner, UNLOCK: the lock drops at the core.
8. Mine a locked core as its owner: the core item and a lock drop.
9. Assemble a construct with a placed Shatter Totem touching it: the Aboard box shows "1 Shatter Totems" and the gold line "Totem aboard: slowed and height-capped" (the caps act once constructs drive).
10. On a server: `serverconfig/bsp_mobile-server.toml` gains `lock.stealSeconds`, `lock.stealRadius`, `lock.graceSeconds`, `totem.maxSpeed` and `totem.maxHeight`; set `stealSeconds` to 20 and a steal takes 20 seconds. Teammates (BSP-Core Teams) may use a locked core: check with a team of two.
