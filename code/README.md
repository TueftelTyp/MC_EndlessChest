# Endless Chest v2.2 — Source / Build candidate

Author: **TueftelTyp**  
Minecraft **26.3**, Fabric, Java **25**.

## Changes since v1.9

- Dedicated custom menu type and themed GUI, replacing the vanilla six-row chest screen.
- Text search (server-authoritative), sort by name/quantity/item, previous/next pages.
- 45 visible virtual storage slots per page; hidden items remain in persistent block data.
- GUI transfer: click a stored item with an empty cursor to select it (or type its page slot number 1–45), enter target chest X/Y/Z and amount (1–2304), then Send. Use Take to withdraw up to 64 into your player inventory. Server validates target and distance. Local dimension only.
- Three-stage lid opening and closing using scheduled block ticks (NOT a fully interpolated BlockEntityRenderer; approximately 0.15s stepping).
- Dedicated client entrypoint, network payloads, menu registration and translations.
- Existing Mod Menu config, configurable recipe, block textures and source commands retained.

## Build

From the project directory, with Java 25:

```bat
gradle clean build
```

Install resulting `build/libs/EndlessChest-2.0.jar` with the required Fabric API / Cloth Config / Mod Menu dependencies.

## How to use

`/give @s infinitechest:infinite_chest`

Right-click to open. Click stored item to select transfer source, enter destination coordinates and quantity, then click Send. Item transfers are performed by the server. Only loaded target chunks are accepted.

Config recipe defaults to 8 diamond blocks around a chest.

## Limitations and safety

- **UNVERIFIED**: this archive has undergone static source/resource checks but no successful Minecraft Gradle compile or in-game test in this environment.
- Lid uses 3 model animation stages rather than a smooth interpolated renderer; visual appearance needs in-game validation.
- GUI sends search requests after a short debounce, and sorting/page information is synchronized through data slots.
- Very large storage can still be limited by disk, RAM and serialization size; back up worlds.
- Servers are authoritative; all participating clients must install the mod.
- Do not deploy in an important world without testing save/reload, insertion, extraction, page navigation, transfer failures and mod upgrades.


## v2.2: Minecraft 26.3 compile fix

Replaced all five `GuiGraphicsExtractor.drawString(...)` calls with the supported `GuiGraphicsExtractor.text(...)` API in `EndlessChestScreen.java`. No inventory or transfer behavior was intentionally changed. Compilation and in-game tests remain to be performed locally.
