package de.tuefteltyp.infinitechest;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** Server-authoritative commands; every action resolves the exact displayed item variant. */
public final class StorageCommands {
    private StorageCommands() {}
    private record Selection(BlockPos source, List<ItemStack> keys) {}
    private static final Map<UUID, Selection> SESSIONS = new HashMap<>();

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, selection) -> {
            dispatcher.register(literal("is")
                .then(literal("list")
                    .then(argument("source", BlockPosArgument.blockPos())
                        .executes(c -> list(c.getSource().getPlayerOrException(),
                            BlockPosArgument.getLoadedBlockPos(c, "source"), "", 0))
                        .then(argument("page", IntegerArgumentType.integer(1))
                            .executes(c -> list(c.getSource().getPlayerOrException(),
                                BlockPosArgument.getLoadedBlockPos(c, "source"), "",
                                IntegerArgumentType.getInteger(c, "page") - 1))))));
            dispatcher.register(literal("is")
                .then(literal("search")
                    .then(argument("source", BlockPosArgument.blockPos())
                        .then(argument("term", StringArgumentType.greedyString())
                            .executes(c -> list(c.getSource().getPlayerOrException(),
                                BlockPosArgument.getLoadedBlockPos(c, "source"),
                                StringArgumentType.getString(c, "term"), 0))))));
            dispatcher.register(literal("is")
                .then(literal("withdraw")
                    .then(argument("source", BlockPosArgument.blockPos())
                        .then(argument("index", IntegerArgumentType.integer(1))
                            .then(argument("amount", IntegerArgumentType.integer(1, 64))
                                .executes(c -> withdraw(c.getSource().getPlayerOrException(),
                                    BlockPosArgument.getLoadedBlockPos(c, "source"),
                                    IntegerArgumentType.getInteger(c, "index") - 1,
                                    IntegerArgumentType.getInteger(c, "amount"))))))));
            dispatcher.register(literal("is")
                .then(literal("send")
                    .then(argument("source", BlockPosArgument.blockPos())
                        .then(argument("index", IntegerArgumentType.integer(1))
                            .then(argument("destination", BlockPosArgument.blockPos())
                                .then(argument("amount", IntegerArgumentType.integer(1, 2304))
                                    .executes(c -> send(c.getSource().getPlayerOrException(),
                                        BlockPosArgument.getLoadedBlockPos(c, "source"),
                                        IntegerArgumentType.getInteger(c, "index") - 1,
                                        BlockPosArgument.getLoadedBlockPos(c, "destination"),
                                        IntegerArgumentType.getInteger(c, "amount")))))))));
        });
    }

    private static InfiniteChestBlockEntity source(ServerPlayer player, BlockPos position) {
        if (player.blockPosition().distSqr(position) > 64) {
            player.sendSystemMessage(Component.translatable("message.infinitechest.too_far")); return null;
        }
        if (!(player.level().getBlockEntity(position) instanceof InfiniteChestBlockEntity chest)) {
            player.sendSystemMessage(Component.translatable("message.infinitechest.not_found")); return null;
        }
        return chest;
    }

    private static List<ItemStack> variants(InfiniteChestBlockEntity chest) {
        List<ItemStack> kinds = new ArrayList<>();
        for (ItemStack stack : chest.snapshot()) {
            boolean exists = false;
            for (ItemStack key : kinds) {
                if (ItemStack.isSameItemSameComponents(key, stack)) { exists = true; break; }
            }
            if (!exists) kinds.add(stack.copyWithCount(1));
        }
        Comparator<ItemStack> cmp = StorageConfig.INSTANCE.sortByQuantity
            ? Comparator.comparingLong(chest::countMatching).reversed()
            : Comparator.comparing(s -> s.getHoverName().getString().toLowerCase(Locale.ROOT));
        kinds.sort(cmp.thenComparing(s -> s.getItem().toString()));
        return kinds;
    }

    private static String pos(BlockPos p) { return p.getX() + " " + p.getY() + " " + p.getZ(); }
    private static MutableComponent link(String label, String command) {
        return Component.literal(label).withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand(command)));
    }

    private static int list(ServerPlayer player, BlockPos position, String filter, int page) {
        InfiniteChestBlockEntity chest = source(player, position);
        if (chest == null) return 0;
        String query = filter.strip().toLowerCase(Locale.ROOT);
        List<ItemStack> kinds = variants(chest);
        if (!query.isEmpty()) kinds.removeIf(s -> !s.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query));
        // Snapshot keys isolate one player's selection from other players' listing/sorting.
        SESSIONS.put(player.getUUID(), new Selection(position.immutable(), List.copyOf(kinds)));
        int perPage = Math.max(1, Math.min(20, StorageConfig.INSTANCE.itemsPerPage));
        int pages = Math.max(1, (kinds.size() + perPage - 1) / perPage);
        page = Math.max(0, Math.min(page, pages - 1));
        player.sendSystemMessage(Component.translatable("message.infinitechest.header", chest.totalCount(), kinds.size(), page + 1, pages));
        for (int i = page * perPage; i < Math.min(kinds.size(), (page + 1) * perPage); i++) {
            ItemStack item = kinds.get(i);
            int number = i + 1;
            player.sendSystemMessage(link("#" + number + " " + item.getHoverName().getString() + " x" + chest.countMatching(item) + " [-1]",
                "/is withdraw " + pos(position) + " " + number + " 1"));
        }
        if (!query.isEmpty()) {
            // Filtered pages retain the exact same snapshot. Search again to start from page 1.
            player.sendSystemMessage(Component.literal("Filter: " + filter));
        } else {
            if (page > 0) player.sendSystemMessage(link("[<]", "/is list " + pos(position) + " " + page));
            if (page + 1 < pages) player.sendSystemMessage(link("[>]", "/is list " + pos(position) + " " + (page + 2)));
        }
        return 1;
    }

    private static ItemStack selected(ServerPlayer player, BlockPos pos, int index) {
        Selection selection = SESSIONS.get(player.getUUID());
        if (selection == null || !selection.source().equals(pos) || index < 0 || index >= selection.keys().size()) {
            player.sendSystemMessage(Component.translatable("message.infinitechest.select_first"));
            return ItemStack.EMPTY;
        }
        return selection.keys().get(index).copy();
    }

    private static int withdraw(ServerPlayer player, BlockPos position, int index, int amount) {
        InfiniteChestBlockEntity chest = source(player, position);
        if (chest == null) return 0;
        ItemStack key = selected(player, position, index);
        if (key.isEmpty()) return 0;
        ItemStack taken = chest.removeMatching(key, amount);
        if (taken.isEmpty()) return 0;
        int withdrawn = taken.getCount();
        player.getInventory().add(taken); // Inventory.add reduces the remaining stack count.
        int remaining = taken.getCount();
        if (!taken.isEmpty()) chest.deposit(taken); // No item entities; restore what would not fit.
        player.sendSystemMessage(Component.translatable("message.infinitechest.withdraw", withdrawn - remaining));
        return 1;
    }

    /** Validated menu-only transfer. It reuses the same server-side transaction rules as commands. */
    static boolean sendFromGui(net.minecraft.world.entity.player.Player player, BlockPos from, ItemStack key, BlockPos to, int amount) {
        if (!(player instanceof ServerPlayer server) || key == null || key.isEmpty() || amount < 1 || amount > 2304) return false;
        // Save and restore the command selection so opening the GUI does not corrupt /is sessions.
        Selection previous = SESSIONS.get(server.getUUID());
        try {
            SESSIONS.put(server.getUUID(), new Selection(from.immutable(), List.of(key.copyWithCount(1))));
            return send(server, from, 0, to, amount) != 0;
        } finally {
            if (previous == null) SESSIONS.remove(server.getUUID());
            else SESSIONS.put(server.getUUID(), previous);
        }
    }

    private static int send(ServerPlayer player, BlockPos from, int index, BlockPos to, int amount) {
        InfiniteChestBlockEntity chest = source(player, from);
        if (chest == null || from.equals(to)) return 0;
        StorageConfig config = StorageConfig.INSTANCE;
        double distanceSq = from.distSqr(to);
        if (!config.allowRemoteTransfers && distanceSq > 1) return 0;
        if (distanceSq > (double) config.maxTransferDistance * config.maxTransferDistance) return 0;
        ItemStack key = selected(player, from, index);
        if (key.isEmpty()) return 0;
        if (!player.level().hasChunkAt(to)) return 0;
        BlockEntity target = player.level().getBlockEntity(to);
        if (target == null) return 0;
        int pending = (int) Math.min(amount, chest.countMatching(key));
        int transferred = 0;
        if (target instanceof InfiniteChestBlockEntity other) {
            while (pending > 0) {
                ItemStack withdrawn = chest.removeMatching(key, Math.min(pending, key.getMaxStackSize()));
                if (withdrawn.isEmpty()) break;
                try {
                    other.deposit(withdrawn);
                } catch (RuntimeException failure) {
                    chest.deposit(withdrawn);
                    InfiniteChestMod.LOGGER.error("Infinite storage transfer failed; items restored", failure);
                    break;
                }
                pending -= withdrawn.getCount();
                transferred += withdrawn.getCount();
            }
        } else if (config.allowTransferToVanilla && target instanceof Container container) {
            for (int slot = 0; slot < container.getContainerSize() && pending > 0; slot++) {
                ItemStack existing = container.getItem(slot);
                if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, key)) continue;
                if (!container.canPlaceItem(slot, key)) continue;
                int maximum = Math.min(key.getMaxStackSize(), container.getMaxStackSize());
                int capacity = existing.isEmpty() ? maximum : maximum - existing.getCount();
                int n = Math.min(Math.max(0, capacity), pending);
                if (n <= 0) continue;
                ItemStack withdrawn = chest.removeMatching(key, n);
                if (withdrawn.isEmpty()) break;
                ItemStack replacement = existing.isEmpty() ? withdrawn.copy() : existing.copy();
                if (!existing.isEmpty()) replacement.grow(withdrawn.getCount());
                try {
                    container.setItem(slot, replacement);
                    container.setChanged();
                } catch (RuntimeException failure) {
                    // If a modded container rejects the insert, restore the removed items.
                    // Vanilla inventories normally cannot throw here.
                    chest.deposit(withdrawn);
                    InfiniteChestMod.LOGGER.error("Storage destination rejected transfer; items restored", failure);
                    break;
                }
                pending -= withdrawn.getCount();
                transferred += withdrawn.getCount();
            }
        }
        player.sendSystemMessage(Component.translatable("message.infinitechest.sent", transferred));
        return transferred > 0 ? 1 : 0;
    }
}
