package de.tuefteltyp.infinitechest.mixin;

import de.tuefteltyp.infinitechest.client.NearbyCraftingClientCache;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds server-synchronized nearby ingredient counts to the vanilla green craftable filter. */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookContentsMixin {
    @Shadow @Final private StackedItemContents stackedContents;
    @Shadow @Final protected RecipeBookMenu menu;
    @Shadow private void selectMatchingRecipes() { throw new AssertionError("Mixin shadow"); }
    @Shadow private void updateStackedContents() { throw new AssertionError("Mixin shadow"); }
    @Shadow private void updateCollections(boolean resetPage, boolean isFiltering) { throw new AssertionError("Mixin shadow"); }
    @Shadow private void updateTabs(boolean isFiltering) { throw new AssertionError("Mixin shadow"); }
    @Shadow private boolean isFiltering() { throw new AssertionError("Mixin shadow"); }
    @Unique private int endlessChest$seenRevision = Integer.MIN_VALUE;

    @Inject(method = "updateStackedContents", at = @At("TAIL"))
    private void endlessChest$includeNearChestStacks(CallbackInfo ci) {
        if (!(menu instanceof CraftingMenu)) return;
        NearbyCraftingClientCache.contribute(menu.containerId, stackedContents);
        selectMatchingRecipes();
        updateCollections(false, isFiltering());
        updateTabs(isFiltering());
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void endlessChest$refreshAfterServerSnapshot(CallbackInfo ci) {
        if (!(menu instanceof CraftingMenu)) return;
        int revision = NearbyCraftingClientCache.revision(menu.containerId);
        if (revision == endlessChest$seenRevision) return;
        endlessChest$seenRevision = revision;
        updateStackedContents();
    }
}
