package de.tuefteltyp.infinitechest.client;

import de.tuefteltyp.infinitechest.InfiniteChestMod;
import de.tuefteltyp.infinitechest.GuiNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

public final class EndlessChestClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        MenuScreens.register(InfiniteChestMod.CHEST_MENU, EndlessChestScreen::new);
        ClientPlayNetworking.registerGlobalReceiver(de.tuefteltyp.infinitechest.NearbyCraftingRecipeSync.Available.TYPE, (packet, context) ->
            context.client().execute(() -> NearbyCraftingClientCache.accept(packet)));
        ClientPlayNetworking.registerGlobalReceiver(GuiNetwork.Targets.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().gui.screen() instanceof EndlessChestScreen screen)
                    screen.applyTargets(payload.ownName(), payload.jsonNames());
            });
        });
    }
}
