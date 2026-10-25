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

## 2026-10-25: MB-4, the Mobile Helm, driving and floating

Headless: 22 GameTests; every required one passes (twice running) and the one optional test fails as expected. New: the drive maths (full thrust north on 100 mass is 400 along the facing, sprint doubles it, at the top speed only drag remains, coasting drags on both axes, a left turn is a positive torque that vanishes once yawing at the turn rate); the helm says "assemble first" on an unassembled structure, refuses a stranger on a locked core, seats the owner (the helm knows its driver, the player rides the seat, the construct is no longer parked) and refuses a second taker; the helm's recipe. **The optional test** (a driven construct moving north, then parking itself when left) cannot pass headlessly: the engine's physics tick reaches the mod (hundreds of ticks are counted), but its physics level never holds a body for the construct, so no force is ever applied. **Driving, floating and self-parking have therefore not run anywhere yet.**

In game (survival unless said; a flat area and some water):
1. Craft a Mobile Helm (Iron Ingots round a Compass, a Tetrium Ingot on top). Its icon shows the pedestal and wheel; the tooltip explains the keys. It sits beside the core in Functional Blocks.
2. Place it on a small build with a core: the wheel faces the way you looked when placing, and you stand behind the pedestal to use it. Right-click it before assembling: "Assemble the construct first." above the hotbar.
3. Assemble, then right-click the helm: you are held at the helm facing the wheel; the core's HELM tab shows you as the driver and "Anchor: weighed". Press W: the construct moves off the way the helm faces; S slows and backs it; A and D turn it; sprint with W is faster; let go and it coasts to a halt within a few seconds; sneak and you step off. The core's ring turns gold while it moves, teal when it has stopped.
4. Leave it standing: about 3 seconds after it stops the HELM tab says "Anchor: down" and the construct is parked (RELEASE works again). WEIGH ANCHOR frees it; DROP ANCHOR parks it at once.
5. Drive it onto water: it floats (the hull sits in the water like a boat) and drives on; adjust `drive.buoyancy` in the server config if it sits too high or sinks. Drive it into a wall: it stops (damage comes with the next task).
6. Top speed: with `drive.maxSpeed` 8 the speed readout settles near 8; set it to 3 and restart: it settles near 3. With a placed Shatter Totem aboard the speed settles near 5 (`totem.maxSpeed`) and the HELM tab says so.
7. Lock the core as its owner; a second account cannot take the helm ("This construct is locked by its owner.") and sees the anchor key greyed with "locked"; the owner can. A teammate (BSP-Core Teams) can.
8. Two helms aboard: either drives; the HELM tab counts "2 helms aboard" and names whoever is driving.
9. Break the helm while someone drives: they are set down and the construct coasts to a halt and parks. Release the construct with a driver at the helm: the driver is set down.
10. On a server: `serverconfig/bsp_mobile-server.toml` gains `drive.acceleration`, `drive.maxSpeed`, `drive.turnRate`, `drive.drag`, `drive.buoyancy` and `drive.parkSeconds`.

## 2026-10-25: MB-5, damage

Headless: 25 required GameTests pass (twice running), the three new ones: the impact rule (nothing at or under 6 blocks a second, one block per 2 over it rounded up, the cap, a cap of 0 switches impacts off); an impact at a corner of an assembled construct knocks off the two nearest blocks and never the core, and a huge impact takes every block but the core; a construct whose core is destroyed in the shipyard breaks up on the next tick, its blocks back in the world where it stood and the core gone. **Impacts from real collisions need the engine's physics and have not run anywhere**; the collision hook is in place.

In game:
1. Drive a construct into a stone wall at speed (sprint, a long run-up): blocks at the bow break off and drop; the core and the helm never do. At a crawl nothing breaks. Hit the wall again within a second: no second loss.
2. Drive one construct into another: both lose blocks at the contact.
3. Light TNT aboard: the blast breaks blocks aboard and pushes the construct; the gather's count on the CORE tab falls.
4. Fire a Create Big Cannons shot (or any weapon on the owner's list) at a construct: the hit breaks blocks aboard as on land.
5. Mine blocks aboard with a pickaxe: they drop, the count falls, the construct goes on working.
6. Destroy the core of an assembled construct with TNT (survival cannot mine it): chat says the construct broke up, its blocks stand in the world where it was, snapped to 90 degrees, the core is gone. Do the same with the construct hanging over a pit so there is no room: chat says it lies where it is as a derelict; it cannot be driven; place a new core in it and assemble again.
7. On a server: `serverconfig/bsp_mobile-server.toml` gains `damage.impactSpeed`, `damage.blocksPerSpeed`, `damage.maxBlocksPerImpact` and `damage.cooldownTicks`; set `maxBlocksPerImpact` to 0 and crashes break nothing.

## 2026-10-25: MB-6, where constructs may go

Headless: 28 required GameTests pass (twice running), the three new ones: a closed server (`regions.closed`, set and restored within the tick) refuses assembly with "closed_server"; the back-out rule refuses forward thrust inside a closed region and nothing else; with Region Guard on the test server, `/rg flag __global__ mobile-constructs deny` makes the position refuse constructs and assembly fail with "closed_region", and `allow` lets it through. **Driving into a closed region (the clamp on the helm) needs physics and has not run anywhere.**

In game (with Region Guard):
1. `/rg flag <spawn region> mobile-constructs deny` (Region Guard's admin screen lists the flag without a description). Place a core inside spawn and ASSEMBLE: "Constructs may not be assembled here." Outside spawn it assembles.
2. Drive a construct into spawn: the HELM tab shows "Closed region: the construct can only back out."; W does nothing, S backs it out; outside again W works.
3. RELEASE inside a denying region: allowed.
4. On a no-totem server (BSP-Core's `restrictions.noTotemServers`): ASSEMBLE says "This server allows no constructs."; set `regions.closedOnNoTotemServers = false` in that server's config and it assembles. On any server `regions.closed = true` refuses every assembly.
5. Without Region Guard installed the mod starts and only the server switches apply.

## 2026-10-25: MB-7, parts and flight

Headless: 31 required GameTests pass (twice running), the three new ones: the flight maths (no lift without parts; half the lift carries half the weight; balloons cancel the weight exactly and damp a rise; space climbs and sprint with space descends at the climb rate; at the ceiling climbing only holds the weight; jets alone sink slowly; the forces carry the lift upward only); two engines, a floater, two balloons, a wing and a jet beside a core are counted from the gather with the wing's position, add up to the config's figures, and the helm's command carries them with the totem's cap and the back-out clamp; the five parts and the helm have recipes with the right results (the wing makes two). **Flight, buoyancy from floaters and the engine's wing aerodynamics need physics and have not run anywhere.**

In game:
1. Craft the five parts (D57). Each shows its block and tooltip; they sit beside the core in Functional Blocks.
2. Two Mobile Engines aboard: the HELM tab's parts line counts them and the top speed reads 12; the construct accelerates and runs faster than without.
3. A stone hull that sinks: add Mobile Floaters until it floats; the parts line counts them.
4. Balloons: add Mobile Balloons until the HELM tab's lift line carries the whole mass; the construct lifts off the ground and hovers level; space climbs; sprint with space descends; let go and it holds its height. With half the balloons the lift line shows a part of the mass and the construct only falls slowly.
5. A placed Shatter Totem aboard a balloon construct: the height line shows the ceiling (the ground beneath plus 64); climbing stops there.
6. Mobile Wings on a construct with jet engines: drive it fast and level and it lifts (the engine's aerodynamics); fly it into a wall at speed and blocks break off.
7. Jet engines alone: space climbs; let go and it sinks slowly to the ground.
8. On a server: `serverconfig/bsp_mobile-server.toml` gains the `parts` section; halve `balloonLift` and twice the balloons are needed.

## 2026-10-25: MB-8, admin tools

Headless: 34 required GameTests pass (twice running), the three new ones: with the per-player cap set to 1 (set and restored within the tick) a second construct for the same owner is refused with "limit_player"; a construct saved as a structure file and pasted elsewhere comes back as a working, parked copy with its name, tier and the admin as owner (the file is removed after); `mobile list` counts the construct and `mobile remove <id>` sets it down with its core unassembled, both run as server commands.

In game (as an operator or BSP-Core admin):
1. `/mobile list`: every construct with its number, name, owner, block count, tier, place, dimension and state. Without any: "No constructs on this server."
2. `/mobile tp <id>`: you stand on the construct's core.
3. `/mobile remove <id>`: the construct is set down where it stands, like a release; `/mobile remove <id> delete` removes it and its blocks (nothing drops). Both appear in the server log with your name.
4. `/mobile save <id> harbour` then `/mobile files`: the file `config/bsp_mobile/constructs/harbour.nbt` exists and is listed. `/mobile paste harbour` while looking at open ground four blocks away: the construct appears there, assembled and parked, owned by you, with its chests' contents; the command tab-completes saved names. Pasting into blocks already there overwrites them.
5. `/mobile limits`: the count against the caps, the tier ladder, the impact speed and whether this server is open.
6. With `limits.perPlayer = 1`: your second ASSEMBLE says "You own as many constructs as a player may."; with `limits.perServer = 1` anyone's second says the server carries as many as it allows.
7. A player who is neither operator nor admin: `/mobile` is unknown to them.

## 2026-10-25: MB-9, the autopilot

Headless: 37 required GameTests pass (twice running), the three new ones: the steering (north 0, east 90, south 180, west 270; dead ahead full thrust and no turn; to the right a right turn and no thrust; a little left within the cone thrust and a gentle turn; half thrust within 12 blocks; climb below the cruise height, sprint and up above it, nothing within the band or without lift; a waypoint counts within 4 blocks); the plan on an unassembled core (GO refused, ADD HERE adds the player's block, the cap of 16, REMOVE, the cruise height, save and load, a stranger refused on a locked core); on an assembled construct with a helm one step of the plan sets thrust toward the waypoint and frees the construct, and a driver taking the helm stops it and clears the inputs. **The core's ticker that steps the plan does not run in the shipyard on the headless server, so the plan running on its own, the arrival, the pulse and the parking have not run anywhere.**

In game:
1. Open PLAN on an unassembled core: "No waypoints…" and GO greyed. Walk a route and press ADD HERE at each turn: the rows fill with your positions; REMOVE drops the last; type a cruise height (30).
2. Assemble a construct with a helm and some engines (balloons for a flying one), open PLAN and press GO: "Under way."; the construct frees itself, turns toward waypoint 1, drives there, turns to the next; the HELM tab shows the autopilot's speed and heading; the PLAN tab highlights the next waypoint with its distance and dims the passed ones; STOP halts it.
3. With balloons and a cruise height: it climbs to the height above the ground and holds it between waypoints; with a totem aboard it stays under the ceiling.
4. At the last waypoint it stops, parks itself, and a redstone lamp touching the core lights for two seconds.
5. Take a helm while it flies: the plan stops and you drive. Drive it into a closed region under the plan: it stops.
6. A construct without a helm: GO greyed ("No helm aboard." when forced). Break the core's construct apart and re-assemble: the plan is still there.

## 2026-10-25: MB-11, Canary beside the BSP build of Valkyrien Skies

Headless: 40 required GameTests pass, the dev run carrying Canary 0.3.3 at its defaults and the BSP build of Valkyrien Skies 2.4.11.1 (`../BSP-Valkyrien Skies`). The three new ones (`gametest/CompatGameTests`): both Canary's and Valkyrien Skies' patches are on `PoiManager` and the loaded Valkyrien Skies is the BSP build; a bed's point of interest is found by the ordinary search; a bed assembled into a construct is found from a world position beside it (the shipyard record projects to within 2.5 blocks of where the bed stood). With upstream 2.4.11 in place of the BSP build the server does not reach the tests: it stops on world load. **Nothing of this has run in a client.**

In game (the pack instance with the BSP build of Valkyrien Skies in `mods/` in place of `valkyrienskies-120-2.4.11.jar`):
1. Delete `config/canary.properties` (or remove its `mixin.ai.poi=false` line) so Canary runs at its defaults. Launch, load a world: no crash, and the log has no `Mixin apply for mod (unknown) failed ... MixinPOIManager` line. The mod list shows Valkyrien Skies 2.4.11.1.
2. In a village, villagers still claim beds and workstations and path to them at dusk (Canary's search is on; the game's village AI is unchanged).
3. Build a small platform with a bed and a composter on it, put a Mobile Core in it and assemble it; lead a villager aboard (or let one wander on). At dusk the villager claims the bed aboard and sleeps in it; a farmer claims the composter. This is the Valkyrien Skies feature the patch keeps.
4. Fly or drive the construct a few hundred blocks and park it: the villager keeps the bed and sleeps aboard where the construct stands.
5. On a dedicated server with Canary: the same as 1 and 3; `/spark tps` shows no change against the stock build.
6. Walk through a nether portal with the construct nearby: nothing crashes (Canary's portal search is on and still finds the vanilla POI manager's own methods).
