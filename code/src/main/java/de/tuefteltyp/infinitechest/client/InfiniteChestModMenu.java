package de.tuefteltyp.infinitechest.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import de.tuefteltyp.infinitechest.StorageConfig;
import de.tuefteltyp.infinitechest.RecipeConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.network.chat.Component;

public final class InfiniteChestModMenu implements ModMenuApi {
    @Override public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            StorageConfig c=StorageConfig.INSTANCE;
            ConfigBuilder b=ConfigBuilder.create().setParentScreen(parent).setTitle(Component.translatable("config.infinitechest.title"));
            b.setSavingRunnable(StorageConfig::save);
            ConfigCategory cat=b.getOrCreateCategory(Component.translatable("config.infinitechest.general"));
            ConfigEntryBuilder e=b.entryBuilder();
            cat.addEntry(e.startIntField(Component.translatable("config.infinitechest.page"),c.itemsPerPage).setMin(1).setMax(20).setDefaultValue(8).setSaveConsumer(v->c.itemsPerPage=v).build());
            cat.addEntry(e.startIntField(Component.translatable("config.infinitechest.range"),c.maxTransferDistance).setMin(1).setMax(100000).setDefaultValue(64).setSaveConsumer(v->c.maxTransferDistance=v).build());
            cat.addEntry(e.startBooleanToggle(Component.translatable("config.infinitechest.vanilla"),c.allowTransferToVanilla).setDefaultValue(true).setSaveConsumer(v->c.allowTransferToVanilla=v).build());
            cat.addEntry(e.startBooleanToggle(Component.translatable("config.infinitechest.remote"),c.allowRemoteTransfers).setDefaultValue(true).setSaveConsumer(v->c.allowRemoteTransfers=v).build());
            cat.addEntry(e.startBooleanToggle(Component.translatable("config.infinitechest.sort"),c.sortByQuantity).setDefaultValue(false).setSaveConsumer(v->c.sortByQuantity=v).build());
            ConfigCategory crafting=b.getOrCreateCategory(Component.literal("Endless Chest Recipe / Rezept"));
            for (int i=0; i<9; i++) {
                final int slot=i;
                crafting.addEntry(e.startStrField(Component.literal("Recipe slot / Rezeptfeld " + (i+1)), RecipeConfig.INSTANCE.slots[i])
                    .setDefaultValue(RecipeConfig.DEFAULT[i])
                    .setSaveConsumer(v -> {
                        if (!RecipeConfig.updateSlot(slot, v)) {
                            de.tuefteltyp.infinitechest.InfiniteChestMod.LOGGER.warn("Invalid recipe slot {}: {}", slot+1, v);
                        }
                    }).build());
            }
            b.setSavingRunnable(() -> { StorageConfig.save(); RecipeConfig.save(); });
            return b.build();
        };
    }
}
