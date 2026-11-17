package de.tuefteltyp.infinitechest.client;

import de.tuefteltyp.infinitechest.NearbyCraftingRecipeSync;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/** Client-side UI hint only. Server validates every recipe and removes actual ingredients. */
public final class NearbyCraftingClientCache {
    private NearbyCraftingClientCache() { }
    private static int menuId = -1;
    private static int revision = 0;
    private static Map<Item, Integer> available = Map.of();

    public static void accept(NearbyCraftingRecipeSync.Available packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !(minecraft.player.containerMenu instanceof CraftingMenu)
                || minecraft.player.containerMenu.containerId != packet.menuId()) return;
        Map<Item, Integer> parsed = new LinkedHashMap<>();
        for (String line : packet.content().split("\n")) {
            int separator = line.lastIndexOf('=');
            if (separator < 1) continue;
            try {
                Identifier id = Identifier.parse(line.substring(0, separator));
                int amount = Integer.parseInt(line.substring(separator + 1));
                if (amount <= 0) continue;
                BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> parsed.put(item, amount));
            } catch (IllegalArgumentException ignored) { }
        }
        if (menuId == packet.menuId() && available.equals(parsed)) return;
        menuId = packet.menuId();
        available = Map.copyOf(parsed);
        revision++;
    }

    public static int revision(int currentMenuId) {
        return menuId == currentMenuId ? revision : -1;
    }

    public static void contribute(int currentMenuId, StackedItemContents contents) {
        if (currentMenuId != menuId) return;
        for (var entry : available.entrySet()) {
            // The normal inventory is counted separately by vanilla. Here we only add
            // remote chest items; no client-side inventory mutation takes place.
            int remainder = Math.min(entry.getValue(), 256);
            while (remainder > 0) {
                ItemStack stack = new ItemStack(entry.getKey(), Math.min(64, remainder));
                contents.accountSimpleStack(stack);
                remainder -= stack.getCount();
            }
        }
    }
}
