# BSP-Mobile

Moving bases for the Build Secure Protect (BSP) network: Minecraft 1.20.1, Forge 47.4.x servers behind a Velocity proxy. MIT.

A player crafts a **Mobile Core** and places it on, in or under a structure. The core turns the blocks connected to it into one construct that can drive, float and, later, fly. Every block aboard keeps working: machines, storage, cables and the rest of the pack. The core's allowance of blocks grows with its upgrades. Constructs take damage: crashing into terrain, explosions, weapons and tools knock blocks off, and a construct breaks apart when its core is destroyed.

The moving blocks are Valkyrien Skies 2 ships; this mod adds the core, the allowance, the rules aboard, damage and the parts.

## What exists so far

- **The Mobile Core**: an iron cage round a glass heart, crafted from three parts (the Mobile Core Processor, GPS and Gyroscope, each with its own recipe from BSP-Core's metals and vanilla parts). Right-click it for its screen: the blocks it will take (every block touching the structure, up to the tier's allowance, 200 at Tier I), what it refuses and why (bedrock, other cores, admin blocks, unmovable blocks), the construct's name, ASSEMBLE and RELEASE. While the screen is open the blocks are outlined in the world. An assembled construct stands parked where it was built until a helm moves it (a later task).
- **Tiers**: the UPGRADE tab sells the next tier for Shatter Coins (6 Gold, then 3 Diamond, 2 Netherite and 1 Illyrium by default) to whoever stands at the core, with change.
- The server config `serverconfig/bsp_mobile-server.toml`: the tier ladder and its prices, extra blocks a core never takes, the moving threshold.

The mod is in development and has not yet been tried on a live server.

## Requirements

- Minecraft 1.20.1 with Forge 47.4.10 or newer (the network runs 47.4.23).
- Valkyrien Skies 2.4.11 or newer for 1.20.1 Forge, and Kotlin for Forge 4.x, on every client and server.
- BSP-Core 1.4.5 or newer (the Shatter Coins that buy the tiers, the metals in the recipes, totems, teams and the admin list).

## Building

`./gradlew build` writes `build/libs/bsp-mobile-<version>.jar`. Valkyrien Skies is fetched from Modrinth's maven; BSP-Core is read from the sibling repository's `build/libs` (build BSP-Core first). `./gradlew runGameTestServer` runs the headless tests.
