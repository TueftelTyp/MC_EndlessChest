package de.tuefteltyp.infinitechest.client;

import de.tuefteltyp.infinitechest.InfiniteChestMod;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

public final class EndlessChestClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        MenuScreens.register(InfiniteChestMod.CHEST_MENU, EndlessChestScreen::new);
    }
}
