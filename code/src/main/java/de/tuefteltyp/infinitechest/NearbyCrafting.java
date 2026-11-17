package de.tuefteltyp.infinitechest;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Server-authoritative nearby-crafting command. Never removes anything until a
 * complete matching recipe and every ingredient have been validated.
 *
 * This does not inject into vanilla recipe-book screens (which require their
 * own sync/placement protocol). This command is the safe first integration.
 */
public final class NearbyCrafting {
    private NearbyCrafting() {}
    private static final int MAX_ENTRIES = 4096;
    private static final int MAX_STEPS = 30000;

    private static final class Source {
        final InfiniteChestBlockEntity chest;
        final int slot;
        final ItemStack item;
        int remaining;
        Source(InfiniteChestBlockEntity chest, int slot, ItemStack stack) {
            this.chest = chest; this.slot = slot;
            this.item = stack.copyWithCount(1);
            this.remaining = stack.getCount();
        }
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, selection) -> {
            dispatcher.register(literal("endlesscraft")
                .then(argument("recipe", StringArgumentType.word())
                    .executes(ctx -> craft(ctx.getSource().getPlayerOrException(),
                        StringArgumentType.getString(ctx, "recipe")))));
        });
    }

    private static void message(ServerPlayer player, String key, Object... args) {
        player.sendSystemMessage(Component.translatable(key, args));
    }

    private static int craft(ServerPlayer player, String raw) {
        StorageConfig config = StorageConfig.INSTANCE;
        if (!config.enableNearbyCrafting) {
            message(player, "message.infinitechest.craft_disabled");
            return 0;
        }
        Identifier id;
        try { id = Identifier.parse(raw); }
        catch (IllegalArgumentException error) {
            message(player, "message.infinitechest.craft_invalid");
            return 0;
        }
        ServerLevel level = (ServerLevel) player.level();
        ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, id);
        Optional<net.minecraft.world.item.crafting.RecipeHolder<?>> found = level.recipeAccess().byKey(key);
        if (found.isEmpty() || !(found.get().value() instanceof CraftingRecipe recipe)) {
            message(player, "message.infinitechest.craft_invalid");
            return 0;
        }
        PlacementInfo placement = recipe.placementInfo();
        if (placement.isImpossibleToPlace() || placement.ingredients().isEmpty()
                || placement.ingredients().size() > 9) {
            message(player, "message.infinitechest.craft_unsupported");
            return 0;
        }

        List<Source> pool = new ArrayList<>();
        int radius = Math.max(1, Math.min(config.nearbyCraftingRadius, 32));
        long radiusSquared = (long) radius * radius;
        // Enumerate the directory rather than every block in a cube. Never force chunks.
        for (BlockPos pos : ChestDirectory.locations(level)) {
            if (player.blockPosition().distSqr(pos) > radiusSquared || !level.hasChunkAt(pos)) continue;
            BlockEntity entity = level.getBlockEntity(pos);
            if (!(entity instanceof InfiniteChestBlockEntity chest) ||
                    !level.getBlockState(pos).is(InfiniteChestMod.CHEST)) continue;
            for (int i = 0; i <= chest.highestUsedIndex(); i++) {
                ItemStack stack = chest.getStored(i);
                if (stack.isEmpty()) continue;
                pool.add(new Source(chest, i, stack));
                if (pool.size() >= MAX_ENTRIES) break;
            }
            if (pool.size() >= MAX_ENTRIES) break;
        }
        if (pool.isEmpty()) {
            message(player, "message.infinitechest.craft_no_chests");
            return 0;
        }
        pool.sort(Comparator.comparing(source -> source.item.getItem().toString()));
        // Try valid grid dimensions, including narrow or rectangular shaped recipes.
        int[] tries = {9, 6, 4, 3, 2, 1};
        for (int cells : tries) {
            if (cells < placement.slotsToIngredientIndex().size()) continue;
            for (int width = 1; width <= 3; width++) {
                if (cells % width != 0 || cells / width > 3) continue;
                int height = cells / width;
                List<ItemStack> selected = new ArrayList<>();
                for (int i = 0; i < cells; i++) selected.add(ItemStack.EMPTY);
                int[] selection = new int[cells];
                java.util.Arrays.fill(selection, -1);
                int[] attempts = {0};
                if (!resolve(recipe, placement, pool, selected, selection, width, height, 0, attempts, level)) continue;
                CraftingInput input = CraftingInput.of(width, height, selected);
                ItemStack output = recipe.assemble(input);
                if (output.isEmpty()) continue;

                // Revalidate the exact stacks immediately before committing.
                boolean valid = true;
                for (int index : selection) {
                    if (index < 0) continue;
                    Source source = pool.get(index);
                    if (source.chest.getStored(source.slot).isEmpty() ||
                        !ItemStack.isSameItemSameComponents(source.chest.getStored(source.slot), source.item)) {
                        valid = false; break;
                    }
                }
                if (!valid) break;
                List<ItemStack> removed = new ArrayList<>();
                for (int index : selection) {
                    if (index < 0) continue;
                    Source source = pool.get(index);
                    ItemStack item = source.chest.removeStored(source.slot, 1);
                    if (item.isEmpty()) { valid = false; break; }
                    removed.add(item);
                }
                if (!valid) {
                    // Roll back any removed ingredients; none were output yet.
                    for (ItemStack item : removed) pool.get(0).chest.deposit(item);
                    message(player, "message.infinitechest.craft_failed");
                    return 0;
                }
                // Handle buckets / other crafting remainder items exactly once.
                for (ItemStack rest : recipe.getRemainingItems(input)) give(player, rest.copy());
                give(player, output.copy());
                message(player, "message.infinitechest.craft_success", output.getHoverName().getString(), output.getCount());
                return 1;
            }
        }
        message(player, "message.infinitechest.craft_missing");
        return 0;
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return;
        player.getInventory().add(stack);
        if (!stack.isEmpty()) player.drop(stack, false, Prediction.SERVER_ONLY);
    }

    private static boolean resolve(CraftingRecipe recipe, PlacementInfo placement,
            List<Source> pool, List<ItemStack> selected, int[] selection,
            int width, int height, int slot, int[] attempts, ServerLevel level) {
        if (++attempts[0] > MAX_STEPS) return false;
        if (slot >= selection.length) {
            return recipe.matches(CraftingInput.of(width, height, selected), level);
        }
        int ingredientIndex = slot < placement.slotsToIngredientIndex().size()
            ? placement.slotsToIngredientIndex().getInt(slot) : -1;
        if (ingredientIndex < 0) return resolve(recipe, placement, pool, selected, selection, width, height, slot + 1, attempts, level);
        if (ingredientIndex >= placement.ingredients().size()) return false;
        Ingredient ingredient = placement.ingredients().get(ingredientIndex);
        for (int i = 0; i < pool.size(); i++) {
            Source source = pool.get(i);
            if (source.remaining <= 0 || !ingredient.test(source.item)) continue;
            source.remaining--;
            selected.set(slot, source.item.copyWithCount(1));
            selection[slot] = i;
            if (resolve(recipe, placement, pool, selected, selection, width, height, slot + 1, attempts, level)) return true;
            selection[slot] = -1;
            selected.set(slot, ItemStack.EMPTY);
            source.remaining++;
            if (attempts[0] > MAX_STEPS) break;
        }
        return false;
    }
}
