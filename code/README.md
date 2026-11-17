# Endless Chest v2.6 — Source / Build candidate

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


## v2.6: Minecraft 26.3 compile fix

Replaced all five `GuiGraphicsExtractor.drawString(...)` calls with the supported `GuiGraphicsExtractor.text(...)` API in `EndlessChestScreen.java`. No inventory or transfer behavior was intentionally changed. Compilation and in-game tests remain to be performed locally.

## Named chest network (v3.0)

- Every newly placed Endless Chest receives a random unique name such as `Chest-5A7C2D91`.
- Open the chest, edit its name at the top right, and press **Ändern / Rename**.
- Names are unique across the entire save, including all dimensions and unloaded chunks. Case does not matter (`Mine` and `mine` are the same name). A duplicate is rejected and the old name stays in place.
- Naming allows letters, numbers, spaces, hyphens and underscores, up to 24 characters.
- Use the `<` and `>` recipient selectors in the GUI, select an item and amount, then press **Senden / Send**. The directory lists registered Endless Chests; normal vanilla chests do not have names in this system.
- Refresh the list using **Ziele aktualisieren / Refresh destinations** when another chest is placed or renamed.
- Existing chests from v2.6 are registered when they are next opened. Their original storage remains unchanged.
- A destination must be **loaded** (chunk currently active). This works across dimensions but does not force chunks to load. An unavailable destination is rejected *before* withdrawing items.
- Names are persisted in `<world>/endlesschest_names.json`, while item contents and the name are also saved in the chest block entity. Back up the world and the directory file together.
- When an empty chest is broken normally, its name is released. External tools or unusual block removals may leave stale names; reinstalling a chest at the same location generates a fresh identity.

**Build verification:** Java 25 and full Minecraft/Fabric dependencies are needed for the final Gradle build. The included `tests/NameRulesTest.java` compiles independently and validates name normalization; this alone does not prove the mod is runnable.


## v3.0 Buildkorrekturen

- Fehlenden `java.util.Locale`-Import in `ChestDirectory` hinzugefuegt.
- Aktuelle Minecraft-26.3-Bildschirmabfrage auf `client.gui.screen()` umgestellt.
- Name, Netzwerk und Grafik der v2.7 beibehalten.
- Vollstaendiger Fabric-Build muss lokal mit JDK 25 ausgefuehrt werden.


### Saved recipients (v3.0)
Each chest has its own recipient bookmarks. Enter a registered chest name and click +. Browse with < / > and remove with Remove. Only saved targets can receive transfers. Bookmarks persist in `endlesschest_names.json`, follow renamed chests, and are purged on a valid block break. Offline chunks remain unavailable for actual transfer.


## v3.0 storage interaction and chest removal

- **Vanilla-style mouse interaction:** Pick up, split, and place stacks in the visible storage slots. Shift-click moves stacks between storage and the player inventory.
- **Destination browsing:** The selected saved destination is displayed in a read-only field between the arrows, together with its index/count. A delayed target-list refresh after opening helps ensure the list appears.
- **Break filled chests:** When a player successfully mines the chest, all stored stacks are dropped on the ground and its registered name/bookmarks are released. This **does not** preserve items inside the dropped chest item.
- **Caution:** A chest containing very large quantities can create a large number of dropped ItemEntities and cause severe lag. Empty very large chests before breaking.
- **Limitations:** Automated destruction by commands, world-editing tools or explosions is not covered by the player-break drop hook; such methods can delete stored items. Back up your worlds before updating.
- **Testing:** This source archive was not Gradle-built or run in Minecraft in this environment.


## v3.6 – Doppelklick zur Versandauswahl

Doppelklicke auf einen belegten Lager-Slot. Der Slot wird als Quelle für
„Senden“ markiert, seine Nummer automatisch in das Slot-Feld eingetragen
und die Auswahl mit einem cyanfarbenen Rand angezeigt. Die üblichen
Minecraft-Klickaktionen bleiben erhalten. Nach Seitenwechsel oder Suche
wird die Auswahl zurückgesetzt.


## Versand-Auswahl / Selecting items for transfer (v3.6)

- **DE:** Mit **Strg + Linksklick** auf ein gelagertes Item wird es fuer den Versand markiert. Der Slot wird automatisch eingetragen und cyan umrandet; der Itemstack bleibt dabei unveraendert. Normale Klicks und Shift-Klicks funktionieren weiterhin wie in Vanilla.
- **EN:** Use **Ctrl + left click** on a stored item to select it for transfer without moving the stack. The slot number and cyan outline update automatically. Regular clicks and Shift-clicks remain vanilla.
- **DE:** Die Tastatur-Eingabe in fokussierten Textfeldern (Suche, Name, Empfaenger) hat Vorrang vor Inventar-Tasten wie E. Escape behaelt die normale Schliessfunktion.
- **EN:** Focused text fields (search, name, recipient) consume inventory shortcuts such as E. Escape still closes the screen normally.

**Hinweis:** Ctrl + Linksklick ist bei Mac mit aktivierter Rechtsklick-Emulation eventuell anderweitig belegt.

## Auswahlmodus (v3.6)

Der Button `Auswahl: AUS` aktiviert den Auswahlmodus. Solange `Auswahl: AN` angezeigt wird, markiert ein einfacher Linksklick auf einen gefuellten Lager-Slot den Versandartikel, ohne Gegenstaende aufzunehmen. Die Slotnummer wird automatisch gesetzt. Durch erneuten Klick auf den Button wird der normale Vanilla-Klickmodus wiederhergestellt. Strg + Linksklick funktioniert zusaetzlich auf Plattformen, die den Modifikator im Mausereignis melden.
\n\n### v3.6 selection mode correction\nWhen selection mode is on, left-clicking any storage slot is handled in `slotClicked` before Minecraft sends the normal container transaction; the selected slot number is filled automatically. Normal clicks remain unchanged with selection mode off.\n

### Version 3.6
- Prevents adjacent floor/wall faces from being culled by registering the chest block with `noOcclusion()`.
- Uses the project-provided `fabric.mod.json` as the metadata baseline, including links and dependencies.


### Version 3.6 model fix
Removed the extra underside base plate from all chest model states to avoid overlapping coplanar bottom faces (z-fighting). The existing chest body retains its bottom face and the block remains `noOcclusion()` to render neighboring surfaces correctly. Textures and animation are unchanged.

## Nearby Crafting (v3.8 preview)

Server-side nearby crafting is controlled by `config/mc-infinite-storage.json`:

```json
"enableNearbyCrafting": true,
"nearbyCraftingRadius": 8
```

Both options also appear under **Nearby crafting** in Mod Menu (Cloth Config). On dedicated servers, the **server's JSON** is authoritative; client-side Mod Menu settings do not alter the remote server's rules. Restart the server after JSON edits. Radius is measured from the player's position to the Endless Chest in the same dimension, 1–32 blocks. Only loaded, registered chests participate.

**Current interface:** `/endlesscraft minecraft:chest` crafts one recipe directly using items stored in nearby Endless Chests, placing the result in the player's inventory. Standard shaped/shapeless recipes are supported where ingredient placement can be resolved. This is an initial server-side integration, **not yet native recipe-book/vanilla crafting-table integration**. Special or complex recipes may not be supported. Back up your world before testing.


## v3.8 Build Fix

Updated `ServerPlayer.drop` to the Minecraft 26.3 three-argument API using `Prediction.SERVER_ONLY`.


### Experimental vanilla recipe-book integration (v3.9)

When nearby crafting is enabled, selecting a standard crafting recipe in a regular
crafting table attempts to supply missing ingredients from loaded Endless Chests
within the configured radius. The server moves only the required ingredients into
the player inventory, then vanilla handles recipe placement and consumption.
Recipes may still appear **red** in the client recipe book until clicked.
This feature has not been validated by a Minecraft 26.3 Gradle build or in-game test.
Use a backup/test world first. Existing `/endlesscraft` command is retained.
