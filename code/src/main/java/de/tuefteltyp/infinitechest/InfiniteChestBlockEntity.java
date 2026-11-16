package de.tuefteltyp.infinitechest;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.MenuProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Server-thread-owned persistent inventory; component-exact stacks only. */
public final class InfiniteChestBlockEntity extends BlockEntity implements Container, MenuProvider {
    private int openCount = 0;
    private String networkName = "";

    public String networkName() { return networkName; }
    public void updateNetworkName(String name) { networkName = name; setChanged(); }
    private final List<ItemStack> stacks = new ArrayList<>();

    public InfiniteChestBlockEntity(BlockPos pos, BlockState state) {
        super(InfiniteChestMod.CHEST_BE, pos, state);
    }

    /** The caller retains its own stack. Reject amounts that cannot be saved safely. */
    public void deposit(ItemStack incoming) {
        if (incoming == null || incoming.isEmpty()) return;
        ItemStack remaining = incoming.copy();
        for (ItemStack stored : stacks) {
            if (stored.isEmpty() || !ItemStack.isSameItemSameComponents(stored, remaining)) continue;
            int moved = Math.min(stored.getMaxStackSize() - stored.getCount(), remaining.getCount());
            if (moved > 0) {
                stored.grow(moved);
                remaining.shrink(moved);
            }
            if (remaining.isEmpty()) break;
        }
        for (int i = 0; i < stacks.size() && !remaining.isEmpty(); i++) {
            if (stacks.get(i).isEmpty()) {
                int n = Math.min(remaining.getCount(), remaining.getMaxStackSize());
                stacks.set(i, remaining.copyWithCount(n));
                remaining.shrink(n);
            }
        }
        while (!remaining.isEmpty()) {
            int moved = Math.min(remaining.getCount(), remaining.getMaxStackSize());
            stacks.add(remaining.copyWithCount(moved));
            remaining.shrink(moved);
        }
        setChanged();
    }

    public long countMatching(ItemStack key) {
        if (key == null || key.isEmpty()) return 0;
        long total = 0;
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, key)) total += stack.getCount();
        }
        return total;
    }

    /** Returns at most one vanilla stack. Never subtracts more than available. */
    public ItemStack removeMatching(ItemStack key, int requested) {
        if (key == null || key.isEmpty() || requested <= 0) return ItemStack.EMPTY;
        int wanted = Math.min(requested, key.getMaxStackSize());
        int available = (int) Math.min(wanted, countMatching(key));
        if (available == 0) return ItemStack.EMPTY;
        int left = available;
        for (int i = stacks.size() - 1; i >= 0 && left > 0; i--) {
            ItemStack stored = stacks.get(i);
            if (!ItemStack.isSameItemSameComponents(stored, key)) continue;
            int n = Math.min(left, stored.getCount());
            stored.shrink(n);
            left -= n;
            if (stored.isEmpty()) stacks.set(i, ItemStack.EMPTY);
        }
        setChanged();
        return key.copyWithCount(available - left);
    }

    public ItemStack takeLast() {
        int i = stacks.size() - 1;
        while (i >= 0 && stacks.get(i).isEmpty()) i--;
        if (i < 0) return ItemStack.EMPTY;
        ItemStack stack = stacks.set(i, ItemStack.EMPTY);
        setChanged();
        return stack;
    }

    public boolean isStorageEmpty() { return stacks.stream().allMatch(ItemStack::isEmpty); }

    public int stackTypes() {
        List<ItemStack> kinds = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            boolean exists = false;
            for (ItemStack key : kinds) {
                if (ItemStack.isSameItemSameComponents(key, stack)) { exists = true; break; }
            }
            if (!exists) kinds.add(stack);
        }
        return kinds.size();
    }

    public long totalCount() {
        long total = 0;
        for (ItemStack s : stacks) total += s.getCount();
        return total;
    }

    public List<ItemStack> snapshot() { return stacks.stream().filter(s -> !s.isEmpty()).map(ItemStack::copy).toList(); }

    // Legacy Container implementation. The 45-slot paginated menu below uses
    // the safe explicit indices to access the entire unbounded backing store.
    public ItemStack getStored(int index) {
        return index >= 0 && index < stacks.size() ? stacks.get(index) : ItemStack.EMPTY;
    }
    public void setStored(int index, ItemStack value) {
        if (index < 0 || index > 1_000_000) return; // guard hostile menu inputs
        while (stacks.size() <= index) stacks.add(ItemStack.EMPTY);
        stacks.set(index, value == null ? ItemStack.EMPTY : value.copy());
        setChanged();
    }
    public ItemStack removeStored(int index, int count) {
        if (index < 0 || index >= stacks.size() || count <= 0) return ItemStack.EMPTY;
        ItemStack result = stacks.get(index).split(count);
        if (!result.isEmpty()) setChanged();
        return result;
    }
    public int highestUsedIndex() {
        for (int i = stacks.size() - 1; i >= 0; i--)
            if (!stacks.get(i).isEmpty()) return i;
        return -1;
    }
    // Vanilla still sees 54 slots; our menu reserves the sixth row for controls.

    @Override public int getContainerSize() { return 54; }
    @Override public boolean isEmpty() { return isStorageEmpty(); }
    @Override public ItemStack getItem(int slot) {
        return slot >= 0 && slot < stacks.size() && slot < 54 ? stacks.get(slot) : ItemStack.EMPTY;
    }
    @Override public ItemStack removeItem(int slot, int amount) {
        if (slot < 0 || slot >= 54 || amount <= 0 || slot >= stacks.size()) return ItemStack.EMPTY;
        ItemStack result = stacks.get(slot).split(amount);
        if (!result.isEmpty()) setChanged();
        return result;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) {
        if (slot < 0 || slot >= 54 || slot >= stacks.size()) return ItemStack.EMPTY;
        ItemStack result = stacks.set(slot, ItemStack.EMPTY);
        if (!result.isEmpty()) setChanged();
        return result;
    }
    @Override public void setItem(int slot, ItemStack value) {
        if (slot < 0 || slot >= 54) return;
        while (stacks.size() <= slot) stacks.add(ItemStack.EMPTY);
        stacks.set(slot, value == null ? ItemStack.EMPTY : value.copy());
        setChanged();
    }
    @Override public void clearContent() {
        for (int i = 0; i < Math.min(54, stacks.size()); i++) stacks.set(i, ItemStack.EMPTY);
        setChanged();
    }
    @Override public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this &&
               player.blockPosition().distSqr(worldPosition) <= 64;
    }
    @Override public Component getDisplayName() {
        return Component.translatable("block.infinitechest.infinite_chest");
    }
    @Override public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new PagedChestMenu(id, playerInventory, this);
    }
    @Override public void startOpen(ContainerUser user) {
        if (level == null || level.isClientSide()) return;
        openCount++;
        syncLid();
    }
    @Override public void stopOpen(ContainerUser user) {
        if (level == null || level.isClientSide()) return;
        openCount = Math.max(0, openCount - 1);
        syncLid();
    }
    private void syncLid() {
        if (level == null || !level.getBlockState(worldPosition).is(InfiniteChestMod.CHEST)) return;
        var state = level.getBlockState(worldPosition);
        if (state.getValue(InfiniteChestBlock.OPEN) != (openCount > 0)) {
            level.setBlock(worldPosition, state.setValue(InfiniteChestBlock.OPEN, openCount > 0), 3);
            level.scheduleTick(worldPosition, InfiniteChestMod.CHEST, 1);
        }
    }

    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("stored", ItemStack.CODEC.listOf(), snapshot());
        output.putString("endlessName", networkName);
    }

    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        networkName = input.getStringOr("endlessName", "");
        stacks.clear();
        input.read("stored", ItemStack.CODEC.listOf()).ifPresent(saved ->
            saved.stream().filter(s -> s != null).map(ItemStack::copy).forEach(stacks::add));
    }
}
