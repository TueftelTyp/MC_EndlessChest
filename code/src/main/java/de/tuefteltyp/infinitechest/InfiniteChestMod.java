package de.tuefteltyp.infinitechest;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.world.level.storage.LevelResource;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.core.Registry;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.flag.FeatureFlags;

public final class InfiniteChestMod implements ModInitializer {
    public static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("infinitechest");
    public static final String ID = "infinitechest";
    public static final Identifier CHEST_ID = Identifier.fromNamespaceAndPath(ID, "infinite_chest");
    public static final ResourceKey<Block> BLOCK_KEY = ResourceKey.create(Registries.BLOCK, CHEST_ID);
    public static final ResourceKey<Item> ITEM_KEY = ResourceKey.create(Registries.ITEM, CHEST_ID);
    public static final InfiniteChestBlock CHEST = new InfiniteChestBlock(BlockBehaviour.Properties.of().strength(3.0f, 3600000.0f).requiresCorrectToolForDrops().setId(BLOCK_KEY));
    public static final BlockItem CHEST_ITEM = new BlockItem(CHEST, new Item.Properties().useBlockDescriptionPrefix().setId(ITEM_KEY));
    public static BlockEntityType<InfiniteChestBlockEntity> CHEST_BE;
    public static final MenuType<PagedChestMenu> CHEST_MENU = Registry.register(
        BuiltInRegistries.MENU, Identifier.fromNamespaceAndPath(ID, "endless_chest_menu"),
        new MenuType<>(PagedChestMenu::new, FeatureFlags.VANILLA_SET));

    @Override public void onInitialize() {
        StorageConfig.load();
        RecipeConfig.load();
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            RecipeConfig.load();
            RecipeConfig.installPack(server.getWorldPath(LevelResource.ROOT));
        });
        StorageCommands.register();
        GuiNetwork.register();
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, entity) -> {
            if (entity instanceof InfiniteChestBlockEntity chest && !chest.isStorageEmpty()) {
                if (!world.isClientSide()) player.sendOverlayMessage(Component.translatable("message.infinitechest.not_empty"));
                return false;
            }
            return true;
        });
        Registry.register(BuiltInRegistries.BLOCK, CHEST_ID, CHEST);
        Registry.register(BuiltInRegistries.ITEM, CHEST_ID, CHEST_ITEM);
        CHEST_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, CHEST_ID,
                FabricBlockEntityTypeBuilder.create(InfiniteChestBlockEntity::new, CHEST).build());
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> output.accept(CHEST_ITEM));
    }
}
