package de.tuefteltyp.infinitechest.mixin;

import de.tuefteltyp.infinitechest.NearbyRecipeBookSupply;
import net.minecraft.recipebook.ServerPlaceRecipe;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

/**
 * Triggered by vanilla recipe-book placement on the server before vanilla
 * calculates the available inventory ingredients. Never replaces vanilla's
 * recipe matching, output generation, or consumption logic.
 */
@Mixin(ServerPlaceRecipe.class)
public abstract class ServerPlaceRecipeMixin {
    @Inject(method = "placeRecipe(Lnet/minecraft/recipebook/ServerPlaceRecipe$CraftingMenuAccess;IILjava/util/List;Ljava/util/List;Lnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/item/crafting/RecipeHolder;ZZ)Lnet/minecraft/world/inventory/RecipeBookMenu$PostPlaceAction;", at = @At("HEAD"))
    private static void endlessChest$prepareNearbyIngredients(
            ServerPlaceRecipe.CraftingMenuAccess<?> menu,
            int gridWidth,
            int gridHeight,
            List<Slot> inputGridSlots,
            List<Slot> slotsToClear,
            Inventory inventory,
            RecipeHolder<?> recipe,
            boolean useMaxItems,
            boolean allowDroppingItemsToClear,
            CallbackInfoReturnable<RecipeBookMenu.PostPlaceAction> callback) {
        NearbyRecipeBookSupply.supply(inventory, recipe, inputGridSlots);
    }
}
