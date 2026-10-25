# Changelog

All notable changes to this project are documented in this file. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- The Mobile Core's tiers can be bought at the core. The UPGRADE tab shows the ladder with each step's price in Shatter Coins, the coins the viewer carries, and BUY (a second click within three seconds confirms). Whoever stands at the core may buy; the coins are taken from the buyer with change. The prices are in the server config (`tiers.prices`, one entry per step as `<coin tier>:<count>`; Tier II 6 Gold, Tier III 3 Diamond, Tier IV 2 Netherite, Tier V 1 Illyrium Shatter Coin by default).
- The Mobile Core is crafted from three parts, each with its own shaped recipe: the Mobile Core Processor (Tetrium Ingots, Redstone, a Diamond), the Mobile Core GPS (a Compass, Tetrium Ingots, Glass Panes) and the Mobile Core Gyroscope (Magnatite Ingots, Iron Bars, a Gold Ingot). The core takes the three parts with Iron Bars, a Tetrium Ingot and an Amethyst Block. The parts sit beside the core in the Functional Blocks tab.
- GameTests: a tier bought with change, a refusal when the buyer is short, the top tier, and the four recipes.

### Changed
- BSP-Core is a required mod (its Shatter Coins buy the tiers and its metals craft the parts). Valkyrien Skies and Kotlin for Forge remain required.
- The Mobile Core: a block (an iron cage round a glass heart, with a ring the client draws grey when not assembled, teal when parked and gold and turning when moving) that gathers every block touching the structure round it, through faces, edges and corners, up to its tier's allowance (200 blocks at Tier I; the ladder is in the server config), and turns them into a construct standing where they were built. Air and liquids are not gathered; bedrock and anything that cannot be mined, another Mobile Core, the blocks in the tag `bsp_mobile:never_mobile` (BSP-Core's admin blocks and Anti Totem blocks) and the config's list, and the blocks the engine lists as unmovable are refused with their reason. The core cannot be moved by pistons, is mined like an iron block, and keeps the construct's name and tier on its dropped item. While its construct is assembled it cannot be mined.
- The core's screen (right-click): CORE with the construct's name (typed on the screen), its state, the allowance with the tier, what was left out, what is aboard (totems, chests, machines, the owner), ASSEMBLE or RELEASE (a second click within three seconds confirms a release, which is refused while the construct moves) and SHOW OUTLINE; BLOCKS, the kinds of block the core takes with their counts, refused kinds first in red; UPGRADE, the tier ladder (buying comes later). While the screen is open the blocks the core takes are outlined in teal in the world and the refused ones in red.
- Releasing puts every block back into the world where the construct stands, turned to the nearest 90 degrees, block entities and their contents with them; nothing moves when a target cell is not free.
- The server config `serverconfig/bsp_mobile-server.toml`: the tier allowances, extra block ids a core never takes, and the speed above which a construct counts as moving.
- The project: a Forge 1.20.1 mod that requires Valkyrien Skies 2 (2.4.11 or newer) and Kotlin for Forge, with BSP-Core optional. One bridge class reaches Valkyrien Skies (assemble a block set into a construct, find the construct at a block, project a shipyard position into the world, remove a construct); nothing else in the mod sees the engine.
- GameTests: the mod loads beside Valkyrien Skies and BSP-Core, and eight blocks become a construct where they stood and can be taken back out.
