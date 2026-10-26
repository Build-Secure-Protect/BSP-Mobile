# BSP-Mobile

Moving bases for the Build Secure Protect (BSP) network: Minecraft 1.20.1, Forge 47.4.x servers behind a Velocity proxy. MIT.

A player crafts a **Mobile Core** and places it on, in or under a structure. The core turns the blocks connected to it into one construct that drives, floats and, with the right parts, flies. Every block aboard keeps working: machines, storage, cables and the rest of the pack. The core's allowance of blocks grows with its tiers. Constructs take damage: crashing into terrain, explosions, weapons and tools knock blocks off, and a construct breaks apart when its core is destroyed.

The moving blocks are Valkyrien Skies 2 ships; this mod adds the core, the allowance, the rules aboard, driving, flight, damage and the parts.

## What exists so far

- **The Mobile Core**: an iron cage round a glass heart, crafted from three parts (the Mobile Core Processor, GPS and Gyroscope, each with its own recipe from BSP-Core's metals and vanilla parts). Right-click it for its screen: the blocks it will take (every block touching the structure, up to the tier's allowance, 200 at Tier I), what it refuses and why (bedrock, other cores, admin blocks, unmovable blocks), the construct's name, ASSEMBLE and RELEASE. While the screen is open the blocks are outlined in the world. An assembled construct stands parked where it was built until a helm moves it.
- **Tiers**: the UPGRADE tab sells the next tier of allowance for Shatter Coins (6 Gold, then 3 Diamond, 2 Netherite and 1 Illyrium by default) to whoever stands at the core, with change.
- **The Mobile Core Lock**: right-clicked on a core it binds the core to its owner; only the owner, their team and admins may then use or mine it. An enemy takes a locked core by standing within 3 blocks through a 5-minute steal (the owner is warned), after which the core and its construct are theirs.
- **The Mobile Helm**: placed aboard, it drives the construct on land and water: W and S thrust, A and D turn, sprint for more, sneak to step away. The construct parks itself when left standing; the HELM tab shows speed, heading and driver and holds the anchor.
- **Parts and flight**: Mobile Engines for speed, Mobile Floaters for buoyancy, Mobile Balloons and Mobile Jet Engines for lift (space climbs, sprint and space descends), Mobile Wings for lift from speed. A construct carrying a Shatter Totem is slowed and may not climb more than 64 blocks above the ground.
- **The autopilot**: a flight plan of waypoints on the core's PLAN tab, flown from a helm aboard; the core pulses redstone on arrival.
- **Damage**: crashing knocks blocks off at the point of impact, never the core or a helm; explosions, weapons and tools break blocks aboard as anywhere; a destroyed core breaks the construct up where it stands.
- **Where constructs may go**: a Region Guard flag per region (`mobile-constructs`), and servers closed to constructs (spawn's region by its admins, the no-totem servers by default).
- **Admin tools**: `/mobile list | remove | tp | limits | save | paste | files` for operators and BSP-Core admins; caps of 50 constructs per server and 3 per player.
- The server config `serverconfig/bsp_mobile-server.toml`: the tier ladder and its prices, extra blocks a core never takes, the moving threshold, the lock's steal, the totem's caps, driving, damage, where constructs may go, what each part adds, and the caps.

The mod is in development and has not yet been tried on a live server.

## Requirements

- Minecraft 1.20.1 with Forge 47.4.10 or newer (the network runs 47.4.23).
- Valkyrien Skies 2 (2.4.11 or newer) for 1.20.1 Forge, and Kotlin for Forge 4.x, on every client and server.
- Canary is not required, but where it is installed this mod turns its point-of-interest module off (a `canary.overrides.properties` inside the jar, which Canary honours over the user's `config/canary.properties`): that module replaces the search Valkyrien Skies patches, and the game would stop on world load. Villagers, bees and the like aboard a construct keep finding their beds, job sites and hives.
- BSP-Core 1.4.5 or newer (the Shatter Coins that buy the tiers, the metals in the recipes, totems, teams and the admin list).
- Optional: BSP Region Guard 0.5.0 or newer (the `mobile-constructs` flag per region).

## Building

`./gradlew build` writes `build/libs/bsp-mobile-<version>.jar`. Valkyrien Skies comes from Modrinth's maven, and BSP-Core and BSP Region Guard are read from their sibling repositories' `build/libs` (build them first); the LuckPerms api jar is fetched for Region Guard's sake, and Canary for the tests' sake, in dev runs only. `./gradlew runGameTestServer` runs the headless tests.
