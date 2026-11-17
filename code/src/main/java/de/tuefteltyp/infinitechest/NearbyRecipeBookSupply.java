package de.tuefteltyp.infinitechest;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Makes ingredients from nearby Endless Chests available to vanilla's recipe
 * placement. Items are moved into the player's inventory only after the entire
 * recipe has been verified and there is enough inventory capacity. Vanilla
 * then places them into the crafting grid and handles crafting outputs.
 *
 * This is not a client recipe-book availability overlay: recipe icons may remain
 * red until the player clicks them. It works for basic placeable recipes at a
 * standard crafting table; special recipes remain handled by vanilla.
 */
public final class NearbyRecipeBookSupply {
    private NearbyRecipeBookSupply() { }
    private static final int MAX_SOURCES = 3000;
    private static final int MAX_ATTEMPTS = 50000;

    private static final class Source {
        final InfiniteChestBlockEntity chest; // null means present in player inventory / crafting grid
        final int slot;
        final ItemStack sample;
        int available;

        Source(InfiniteChestBlockEntity chest, int slot, ItemStack stack) {
            this.chest = chest;
            this.slot = slot;
            this.sample = stack.copyWithCount(1);
            this.available = stack.getCount();
        }
    }

    /** Called ONLY on the logical server as part of recipe-book placement. */
    public static void supply(Inventory inventory, RecipeHolder<?> holder, List<Slot> gridSlots) {
        if (!StorageConfig.INSTANCE.enableNearbyCrafting) return;
        if (!(inventory.player instanceof ServerPlayer player)) return;
        if (!(player.containerMenu instanceof CraftingMenu)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        PlacementInfo placement = holder.value().placementInfo();
        if (placement.isImpossibleToPlace()) return;
        List<Ingredient> ingredients = placement.ingredients();
        if (ingredients.isEmpty() || ingredients.size() > 9) return;

        List<Source> pool = new ArrayList<>();
        // Vanilla can already use items in the inventory and crafting grid.
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (!item.isEmpty()) pool.add(new Source(null, slot, item));
        }
        for (Slot grid : gridSlots) {
            ItemStack item = grid.getItem();
            if (!item.isEmpty()) pool.add(new Source(null, -1, item));
        }
        int radius = Math.clamp(StorageConfig.INSTANCE.nearbyCraftingRadius, 1, 32);
        long radiusSquared = (long) radius * radius;
        int localSources = 0;
        for (BlockPos pos : ChestDirectory.locations(level)) {
            if (pos.distSqr(player.blockPosition()) > radiusSquared || !level.hasChunkAt(pos)) continue;
            BlockEntity entity = level.getBlockEntity(pos);
            if (!(entity instanceof InfiniteChestBlockEntity chest) ||
                    !level.getBlockState(pos).is(InfiniteChestMod.CHEST)) continue;
            for (int index = 0; index <= chest.highestUsedIndex(); index++) {
                ItemStack item = chest.getStored(index);
                if (item.isEmpty()) continue;
                pool.add(new Source(chest, index, item));
                if (++localSources >= MAX_SOURCES) break;
            }
            if (localSources >= MAX_SOURCES) break;
        }
        if (localSources == 0) return;

        int[] selected = new int[ingredients.size()];
        java.util.Arrays.fill(selected, -1);
        int[] attempts = {0};
        if (!plan(ingredients, pool, 0, selected, attempts)) return;

        // Aggregate transfers by exact source slot. We never remove inventory items.
        int[] demands = new int[pool.size()];
        for (int sourceIndex : selected) {
            if (sourceIndex >= 0 && pool.get(sourceIndex).chest != null) demands[sourceIndex]++;
        }
        int needed = 0;
        for (int demand : demands) needed += demand;
        if (needed == 0) return;

        // Dry-run the inventory insertions to avoid partial transfers when full.
        ArrayList<ItemStack> simulation = new ArrayList<>(36);
        for (int i = 0; i < 36; i++) simulation.add(inventory.getItem(i).copy());
        for (int index = 0; index < demands.length; index++) {
            if (demands[index] == 0) continue;
            Source src = pool.get(index);
            if (!fits(simulation, src.sample, demands[index])) return;
            ItemStack live = src.chest.getStored(src.slot);
            if (live.isEmpty() || live.getCount() < demands[index] ||
                    !ItemStack.isSameItemSameComponents(live, src.sample)) return;
        }

        // One server-thread transaction: ingredients leave chest storage and enter
        // the real player inventory before vanilla places the selected recipe.
        for (int index = 0; index < demands.length; index++) {
            if (demands[index] == 0) continue;
            Source src = pool.get(index);
            ItemStack moved = src.chest.removeStored(src.slot, demands[index]);
            if (moved.isEmpty()) continue;
            inventory.add(moved);
            // Under unexpected inventory mutation, put all uninserted items back.
            if (!moved.isEmpty()) src.chest.deposit(moved);
        }
        inventory.setChanged();
    }

    private static boolean fits(List<ItemStack> inventory, ItemStack sample, int count) {
        int remainder = count;
        for (ItemStack stack : inventory) {
            if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, sample)) continue;
            int added = Math.min(remainder, stack.getMaxStackSize() - stack.getCount());
            if (added > 0) { stack.grow(added); remainder -= added; }
            if (remainder == 0) return true;
        }
        for (int i = 0; i < inventory.size() && remainder > 0; i++) {
            if (!inventory.get(i).isEmpty()) continue;
            int added = Math.min(remainder, sample.getMaxStackSize());
            inventory.set(i, sample.copyWithCount(added));
            remainder -= added;
        }
        return remainder == 0;
    }

    private static boolean plan(List<Ingredient> ingredients, List<Source> pool, int index,
                                int[] selected, int[] attempts) {
        if (++attempts[0] > MAX_ATTEMPTS) return false;
        if (index >= ingredients.size()) return true;
        Ingredient ingredient = ingredients.get(index);
        for (int i = 0; i < pool.size(); i++) {
            Source source = pool.get(i);
            if (source.available <= 0 || !ingredient.test(source.sample)) continue;
            source.available--;
            selected[index] = i;
            if (plan(ingredients, pool, index + 1, selected, attempts)) return true;
            selected[index] = -1;
            source.available++;
            if (attempts[0] > MAX_ATTEMPTS) break;
        }
        return false;
    }
}
