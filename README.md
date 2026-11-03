# FlexyCrates
A configurable Paper 1.21.11 crate plugin project.

## Build
Requires JDK 21 and Gradle 8.14+ (or a compatible newer Gradle), with internet access to resolve Paper API.
Run `gradle build`; the plugin JAR will be in `build/libs/`.

## Install
Put `FlexyCrates-1.0.0-1.21.11.jar` into `plugins/`, restart the Paper server, then edit `plugins/FlexyCrates/config.yml`.

## Commands
- `/fc` — help
- `/fc list` — list crates
- `/fc preview <crate>` — preview a crate
- `/fc setcrate <crate>` — look at a block and run this command to set the physical crate
- `/fc givekey <player> <crate> [amount]` — give a custom key
- `/fc reload` — reload config

Crate IDs in commands are lowercase, e.g. `common`, `spawner`, `op`. The keyall countdown and hologram key counts are configurable text values. Holograms are created when a crate block is set.

Note: this is an initial implementation. Spawner rewards are regular SPAWNER items with names/lore; mob type assignment needs server-specific block-entity handling and is not applied to the item itself.
