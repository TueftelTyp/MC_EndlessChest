package de.tuefteltyp.infinitechest;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Server-owned recipe-book ingredient snapshot; never used for actual item consumption. */
public final class NearbyCraftingRecipeSync {
    private NearbyCraftingRecipeSync() { }
    private static final int MAX_TYPES = 256;
    private static final int MAX_SLOTS = 4096;
    private static final int MAX_BYTES = 16000;
    private static final Map<UUID, LastSync> LAST = new java.util.HashMap<>();
    private record LastSync(int menuId, String content) { }

    public record Available(int menuId, String content) implements CustomPacketPayload {
        public static final Type<Available> TYPE = new Type<>(Identifier.fromNamespaceAndPath(InfiniteChestMod.ID, "nearby_recipe_items"));
        public static final StreamCodec<FriendlyByteBuf, Available> CODEC = StreamCodec.composite(
                ByteBufCodecs.INT, Available::menuId,
                ByteBufCodecs.stringUtf8(MAX_BYTES), Available::content,
                Available::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(Available.TYPE, Available.CODEC);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 10 != 0) return;
            java.util.Set<UUID> online = new java.util.HashSet<>();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                online.add(player.getUUID());
                if (!(player.containerMenu instanceof CraftingMenu)) {
                    LAST.remove(player.getUUID());
                    continue;
                }
                String content = scan(player);
                int menuId = player.containerMenu.containerId;
                LastSync previous = LAST.get(player.getUUID());
                if (previous != null && previous.menuId() == menuId && previous.content().equals(content)) continue;
                if (ServerPlayNetworking.canSend(player, Available.TYPE)) {
                    ServerPlayNetworking.send(player, new Available(menuId, content));
                    LAST.put(player.getUUID(), new LastSync(menuId, content));
                }
            }
            LAST.keySet().retainAll(online);
        });
    }

    private static String scan(ServerPlayer player) {
        if (!StorageConfig.INSTANCE.enableNearbyCrafting) return "";
        if (!(player.level() instanceof ServerLevel level)) return "";
        int radius = Math.clamp(StorageConfig.INSTANCE.nearbyCraftingRadius, 1, 32);
        long distance = (long) radius * radius;
        Map<Item, Integer> found = new LinkedHashMap<>();
        int slots = 0;
        for (BlockPos pos : ChestDirectory.locations(level)) {
            if (!level.hasChunkAt(pos) || player.blockPosition().distSqr(pos) > distance) continue;
            BlockEntity entity = level.getBlockEntity(pos);
            if (!(entity instanceof InfiniteChestBlockEntity chest) || !level.getBlockState(pos).is(InfiniteChestMod.CHEST)) continue;
            for (int slot = 0; slot <= chest.highestUsedIndex() && slots < MAX_SLOTS; slot++) {
                ItemStack stack = chest.getStored(slot);
                if (stack.isEmpty()) continue;
                if (!found.containsKey(stack.getItem()) && found.size() >= MAX_TYPES) continue;
                found.merge(stack.getItem(), stack.getCount(), (a,b) -> Math.min(999999, a+b));
                slots++;
            }
            if (slots >= MAX_SLOTS) break;
        }
        StringBuilder result = new StringBuilder();
        for (var entry : found.entrySet()) {
            String line = BuiltInRegistries.ITEM.getKey(entry.getKey()) + "=" + entry.getValue() + "\n";
            if (result.length() + line.length() > MAX_BYTES - 256) break;
            result.append(line);
        }
        return result.toString();
    }
}
