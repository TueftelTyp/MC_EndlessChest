package de.tuefteltyp.infinitechest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;

/** Dedicated menu: 45 visible slots are a window into persistent storage. */
public final class PagedChestMenu extends AbstractContainerMenu {
    public static final int STORAGE_SLOTS = 45;
    private final InfiniteChestBlockEntity chest; // null only for client-side mirror
    private final Container visible;
    private final Inventory playerInventory;
    private List<Integer> indices = List.of();
    private int page = 0;
    private int sort = 0; // 0 name, 1 quantity, 2 registry name
    private String filter = "";
    private int selected = -1;
    private int syncedPages = 1;

    /** Client menu is populated from vanilla slot synchronization. */
    public PagedChestMenu(int id, Inventory inventory) {
        this(id, inventory, null);
    }

    public PagedChestMenu(int id, Inventory inventory, InfiniteChestBlockEntity chest) {
        super(InfiniteChestMod.CHEST_MENU, id);
        this.chest = chest;
        this.playerInventory = inventory;
        this.visible = chest == null ? new SimpleContainer(STORAGE_SLOTS) : new PageContainer();
        if (chest != null) refresh();
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(visible, row * 9 + col, 48 + col * 18, 48 + row * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 48 + col * 18, 190 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 48 + col * 18, 248));
        }
        addDataSlot(new DataSlot() {
            @Override public int get() { return page; }
            @Override public void set(int value) { page = Math.max(0, value); }
        });
        addDataSlot(new DataSlot() {
            @Override public int get() { return chest == null ? syncedPages : pages(); }
            @Override public void set(int value) { syncedPages = Math.max(1, value); }
        });
        addDataSlot(new DataSlot() {
            @Override public int get() { return sort; }
            @Override public void set(int value) { sort = value; }
        });
        if (chest != null) chest.startOpen(inventory.player);
    }

    public int page() { return page; }
    public int sort() { return sort; }
    public String filter() { return filter; }
    public int pages() { return chest == null ? syncedPages : Math.max(1, (indices.size() + STORAGE_SLOTS - 1) / STORAGE_SLOTS); }
    public int selected() { return selected; }
    public BlockPos chestPos() { return chest == null ? BlockPos.ZERO : chest.getBlockPos(); }

    private int backing(int shown) {
        int i = page * STORAGE_SLOTS + shown;
        return shown < 0 || shown >= STORAGE_SLOTS || i < 0 || i >= indices.size() ? -1 : indices.get(i);
    }

    private void refresh() {
        if (chest == null) return;
        ArrayList<Integer> selectedIndices = new ArrayList<>();
        for (int i = 0; i <= chest.highestUsedIndex(); i++) {
            ItemStack s = chest.getStored(i);
            if (s.isEmpty()) continue;
            if (!filter.isEmpty() && !s.getHoverName().getString().toLowerCase(Locale.ROOT).contains(filter)) continue;
            selectedIndices.add(i);
        }
        Comparator<Integer> comparator = switch (sort) {
            case 1 -> Comparator.comparingInt((Integer i) -> chest.getStored(i).getCount()).reversed();
            case 2 -> Comparator.comparing(i -> chest.getStored(i).getItem().toString());
            default -> Comparator.comparing(i -> chest.getStored(i).getHoverName().getString().toLowerCase(Locale.ROOT));
        };
        selectedIndices.sort(comparator.thenComparingInt(i -> i));
        indices = List.copyOf(selectedIndices);
        page = Math.clamp(page, 0, pages() - 1);
        if (selected < 0 || selected >= STORAGE_SLOTS || backing(selected) < 0) selected = -1;
    }

    /** Called ONLY on server from validated GUI payload. */
    public void applySearch(Player player, String value) {
        if (chest == null || !stillValid(player)) return;
        filter = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        if (filter.length() > 64) filter = filter.substring(0, 64);
        page = 0;
        selected = -1;
        refresh();
        broadcastFullState();
    }

    public void navigate(Player player, int action) {
        if (chest == null || !stillValid(player)) return;
        if (action == 0) page = Math.max(0, page - 1);
        else if (action == 1) page = Math.min(pages() - 1, page + 1);
        else if (action == 2) { sort = (sort + 1) % 3; page = 0; }
        else return;
        selected = -1;
        refresh();
        broadcastFullState();
    }

    /** Removes only what can be given to the player's inventory; restores any remainder. */
    public boolean withdraw(Player player, int shownSlot, int requested) {
        if (chest == null || !stillValid(player) || shownSlot < 0 || shownSlot >= STORAGE_SLOTS
            || requested <= 0 || requested > 64) return false;
        int idx = backing(shownSlot);
        if (idx < 0) return false;
        ItemStack taken = chest.removeStored(idx, requested);
        if (taken.isEmpty()) return false;
        int original = taken.getCount();
        player.getInventory().add(taken);
        int remaining = taken.getCount();
        if (!taken.isEmpty()) chest.deposit(taken);
        refresh();
        broadcastFullState();
        return remaining < original;
    }

    public boolean transfer(Player player, int shownSlot, BlockPos destination, int amount) {
        if (chest == null || !stillValid(player) || shownSlot < 0 || shownSlot >= STORAGE_SLOTS
            || amount <= 0 || amount > 2304 || destination == null) return false;
        int idx = backing(shownSlot);
        if (idx < 0) return false;
        ItemStack key = chest.getStored(idx).copy();
        if (key.isEmpty()) return false;
        boolean result = StorageCommands.sendFromGui(player, chest.getBlockPos(), key, destination, amount);
        refresh();
        broadcastFullState();
        return result;
    }

    @Override public void clicked(int slotId, int button, net.minecraft.world.inventory.ContainerInput input, Player player) {
        if (chest != null && !stillValid(player)) return;
        if (slotId >= 0 && slotId < STORAGE_SLOTS) selected = slotId;
        super.clicked(slotId, button, input, player);
        if (chest != null && !player.level().isClientSide()) {
            refresh();
            broadcastFullState();
        }
    }

    @Override public ItemStack quickMoveStack(Player player, int slotIndex) {
        if (chest == null || !stillValid(player) || slotIndex < 0 || slotIndex >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(slotIndex);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack original = slot.getItem().copy();
        if (slotIndex < STORAGE_SLOTS) {
            ItemStack remainder = original.copy();
            if (!moveItemStackTo(remainder, STORAGE_SLOTS, slots.size(), true)) return ItemStack.EMPTY;
            int actual = backing(slotIndex);
            if (actual >= 0) chest.setStored(actual, remainder);
        } else {
            chest.deposit(original);
            slot.setByPlayer(ItemStack.EMPTY);
        }
        slot.setChanged();
        refresh();
        broadcastFullState();
        return original;
    }

    @Override public boolean stillValid(Player player) {
        return chest == null || chest.stillValid(player);
    }

    @Override public void removed(Player player) {
        super.removed(player);
        if (chest != null) chest.stopOpen(player);
    }

    private final class PageContainer implements Container {
        @Override public int getContainerSize() { return STORAGE_SLOTS; }
        @Override public boolean isEmpty() { return indices.isEmpty(); }
        @Override public ItemStack getItem(int index) {
            int actual = backing(index);
            return actual < 0 ? ItemStack.EMPTY : chest.getStored(actual);
        }
        @Override public ItemStack removeItem(int index, int count) {
            int actual = backing(index);
            return actual < 0 ? ItemStack.EMPTY : chest.removeStored(actual, count);
        }
        @Override public ItemStack removeItemNoUpdate(int index) {
            int actual = backing(index);
            if (actual < 0) return ItemStack.EMPTY;
            ItemStack removed = chest.getStored(actual).copy();
            chest.setStored(actual, ItemStack.EMPTY);
            return removed;
        }
        @Override public void setItem(int index, ItemStack stack) {
            int actual = backing(index);
            if (actual < 0) {
                if (!stack.isEmpty()) chest.deposit(stack);
            } else chest.setStored(actual, stack);
        }
        @Override public void setChanged() { chest.setChanged(); }
        @Override public boolean stillValid(Player player) { return chest.stillValid(player); }
        @Override public void clearContent() { /* hidden pages must never be deleted */ }
    }
}
