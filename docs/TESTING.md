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
