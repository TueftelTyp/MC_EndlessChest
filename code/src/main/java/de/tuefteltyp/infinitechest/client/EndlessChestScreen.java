package de.tuefteltyp.infinitechest.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import de.tuefteltyp.infinitechest.GuiNetwork;
import de.tuefteltyp.infinitechest.PagedChestMenu;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Classic Minecraft inventory with a side-panel for named Endless Chest routing. */
public final class EndlessChestScreen extends AbstractContainerScreen<PagedChestMenu> {
    private static final Identifier GUI_TEXTURE = Identifier.fromNamespaceAndPath("infinitechest", "textures/gui/endless_chest_gui.png");
    private EditBox search;
    private EditBox ownName;
    private EditBox amount;
    private EditBox itemNumber;
    private EditBox recipientName;
    private EditBox selectedTargetDisplay;
    private int targetRefreshDelay = 15;
    private int chosenSlot = -1;
    // Persistent selection mode avoids relying on platform-dependent Ctrl modifiers.
    private boolean selectionMode;
    private boolean consumeSelectionRelease;
    private Button selectionButton;
    private int delayedSearch = -1;
    private String lastQuery = "";
    private List<String> targets = List.of();
    private int targetIndex;
    private String currentName = "";

    public EndlessChestScreen(PagedChestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 330, 222);
    }

    @Override protected void init() {
        super.init();
        int x = leftPos, y = topPos;
        search = field(x+8, y+7, 154, "gui.infinitechest.search");
        search.setMaxLength(64);
        search.setResponder(value -> { delayedSearch = 6; clearSelection(); });
        ownName = field(x+176, y+7, 80, "gui.infinitechest.name");
        ownName.setMaxLength(24);
        ownName.setValue(currentName);
        addRenderableWidget(Button.builder(Component.translatable("gui.infinitechest.rename"), b -> {
            ClientPlayNetworking.send(new GuiNetwork.Rename(ownName.getValue()));
        }).bounds(x+261, y+7, 62, 18).build());

        addRenderableWidget(Button.builder(Component.literal("<"), b -> sendPage(0)).bounds(x+176, y+31, 20, 18).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> sendPage(1)).bounds(x+303, y+31, 20, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.infinitechest.sort"), b -> sendPage(2))
            .bounds(x+176, y+53, 70, 18).build());
        selectionButton = Button.builder(selectionModeLabel(), b -> {
            selectionMode = !selectionMode;
            selectionButton.setMessage(selectionModeLabel());
        }).bounds(x+250, y+53, 73, 18).build();
        addRenderableWidget(selectionButton);
        addRenderableWidget(Button.builder(Component.translatable("gui.infinitechest.withdraw"), b -> sendWithdraw())
            .bounds(x+176, y+76, 147, 18).build());
        itemNumber = field(x+176, y+98, 56, "gui.infinitechest.slot");
        amount = field(x+238, y+98, 85, "gui.infinitechest.amount");
        amount.setValue("64");
        recipientName = field(x+176, y+121, 101, "gui.infinitechest.recipient");
        recipientName.setMaxLength(24);
        addRenderableWidget(Button.builder(Component.translatable("gui.infinitechest.add"), b -> {
            String name = recipientName.getValue().strip();
            if (!name.isEmpty()) ClientPlayNetworking.send(new GuiNetwork.RecipientChange(name, true));
        }).bounds(x+282, y+121, 41, 18).build());
        addRenderableWidget(Button.builder(Component.literal("<"), b -> changeTarget(-1))
            .bounds(x+176, y+144, 20, 18).build());
        selectedTargetDisplay = field(x+199, y+144, 101, "gui.infinitechest.recipient");
        selectedTargetDisplay.setEditable(false);
        updateTargetDisplay();
        addRenderableWidget(Button.builder(Component.literal(">"), b -> changeTarget(1))
            .bounds(x+303, y+144, 20, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.infinitechest.remove"), b -> {
            if (!targets.isEmpty()) ClientPlayNetworking.send(
                new GuiNetwork.RecipientChange(targets.get(targetIndex), false));
        }).bounds(x+176, y+169, 70, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.infinitechest.send"), b -> sendTransfer())
            .bounds(x+250, y+169, 73, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.infinitechest.refresh"), b ->
            ClientPlayNetworking.send(new GuiNetwork.TargetListRequest(0)))
            .bounds(x+176, y+193, 147, 18).build());
        ClientPlayNetworking.send(new GuiNetwork.TargetListRequest(0));
        targetRefreshDelay = 15;
    }

    private EditBox field(int x, int y, int w, String key) {
        EditBox edit = new EditBox(this.font, x, y, w, 17, Component.translatable(key));
        edit.setHint(Component.translatable(key));
        edit.setMaxLength(64);
        addRenderableWidget(edit);
        return edit;
    }

    public void applyTargets(String own, String json) {
        if (ownName == null) return;
        currentName = own;
        ownName.setValue(own);
        String previous = targetIndex >= 0 && targetIndex < targets.size() ? targets.get(targetIndex) : "";
        try {
            List<String> fresh = new ArrayList<>();
            for (JsonElement item : JsonParser.parseString(json).getAsJsonArray()) {
                String value = item.getAsString();
                if (!value.isBlank()) fresh.add(value);
            }
            targets = List.copyOf(fresh);
            targetIndex = Math.max(0, targets.indexOf(previous));
        } catch (RuntimeException badPacket) {
            targets = List.of();
            targetIndex = 0;
        }
        updateTargetDisplay();
    }

    private void updateTargetDisplay() {
        if (selectedTargetDisplay == null) return;
        selectedTargetDisplay.setValue(targets.isEmpty() ? "-" : targets.get(Math.min(targetIndex, targets.size() - 1)));
    }

    private void changeTarget(int direction) {
        if (targets.isEmpty()) return;
        targetIndex = Math.floorMod(targetIndex + direction, targets.size());
        updateTargetDisplay();
    }

    @Override protected void containerTick() {
        super.containerTick();
        if (targetRefreshDelay > 0 && --targetRefreshDelay == 0)
            ClientPlayNetworking.send(new GuiNetwork.TargetListRequest(0));
        if (delayedSearch >= 0 && --delayedSearch == 0) {
            String query = search.getValue();
            if (!query.equals(lastQuery)) {
                lastQuery = query;
                ClientPlayNetworking.send(new GuiNetwork.Search(query));
            }
        }
    }

    private void clearSelection() {
        chosenSlot = -1;
        if (itemNumber != null) itemNumber.setValue("");
    }

    private void sendPage(int action) {
        clearSelection();
        ClientPlayNetworking.send(new GuiNetwork.Action(action));
    }

    private int selectedSlot() {
        if (itemNumber != null && !itemNumber.getValue().isBlank()) {
            try { return Integer.parseInt(itemNumber.getValue().strip()) - 1; }
            catch (NumberFormatException ignored) { return -1; }
        }
        return chosenSlot;
    }

    private int count() {
        try { return Integer.parseInt(amount.getValue().strip()); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private void sendWithdraw() {
        int idx = selectedSlot();
        int quantity = count();
        if (idx >= 0 && idx < PagedChestMenu.STORAGE_SLOTS && quantity >= 1 && quantity <= 64)
            ClientPlayNetworking.send(new GuiNetwork.Withdraw(idx, quantity));
    }

    private void sendTransfer() {
        int idx = selectedSlot();
        int quantity = count();
        if (idx < 0 || idx >= PagedChestMenu.STORAGE_SLOTS || quantity < 1 || quantity > 2304 || targets.isEmpty()) return;
        ClientPlayNetworking.send(new GuiNetwork.NamedTransfer(idx, targets.get(targetIndex), quantity));
    }

    private Component selectionModeLabel() {
        return Component.translatable(selectionMode
            ? "gui.infinitechest.selection_on" : "gui.infinitechest.selection_off");
    }

    /**
     * When selection mode is enabled a regular left-click selects a storage slot.
     * Crucially this returns before vanilla's container handling, preventing pickup.
     * Ctrl-click is also recognized where the platform reports that modifier.
     */
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && !selectionMode && event.hasControlDown()) {
            double x = event.x() - leftPos;
            double y = event.y() - topPos;
            for (int i = 0; i < PagedChestMenu.STORAGE_SLOTS; i++) {
                Slot slot = menu.slots.get(i);
                if (x >= slot.x - 1 && x < slot.x + 17
                        && y >= slot.y - 1 && y < slot.y + 17) {
                    if (slot.hasItem()) {
                        chosenSlot = i;
                        itemNumber.setValue(Integer.toString(i + 1));
                    } else {
                        clearSelection();
                    }
                    consumeSelectionRelease = true;
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    /**
     * Minecraft 26.3 uses slotClicked as the final gateway to the inventory
     * transaction. Catch selection here instead of depending on mouse position
     * translation in mouseClicked; no container click is sent to the server.
     * Normal slot interactions continue through the vanilla implementation.
     */
    @Override protected void slotClicked(Slot slot, int slotId, int mouseButton,
                                         net.minecraft.world.inventory.ContainerInput clickType) {
        if (selectionMode && mouseButton == 0 && slot != null
                && slotId >= 0 && slotId < PagedChestMenu.STORAGE_SLOTS) {
            if (slot.hasItem()) {
                chosenSlot = slotId;
                itemNumber.setValue(Integer.toString(slotId + 1));
            } else {
                clearSelection();
            }
            consumeSelectionRelease = true;
            return; // Critical: no vanilla pickup and no server inventory transaction.
        }
        super.slotClicked(slot, slotId, mouseButton, clickType);
    }

    /** Do not allow the corresponding release to start vanilla item dragging. */
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0 && consumeSelectionRelease) {
            consumeSelectionRelease = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    /**
     * A focused text field must consume inventory shortcuts such as E.
     * Character input is delivered separately via charTyped by Minecraft.
     * Escape is still handled by the normal screen logic.
     */
    @Override public boolean keyPressed(KeyEvent event) {
        if (this.getFocused() instanceof EditBox edit && edit.isFocused()
                && event.key() != InputConstants.KEY_ESCAPE) {
            edit.keyPressed(event);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, GUI_TEXTURE, leftPos, topPos,
            0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
        // A bright border marks the currently selected shipping source, independent of
        // the hovered inventory slot or the normal Minecraft item cursor.
        int selected = selectedSlot();
        if (selected >= 0 && selected < PagedChestMenu.STORAGE_SLOTS) {
            Slot slot = menu.slots.get(selected);
            int x = leftPos + slot.x - 2;
            int y = topPos + slot.y - 2;
            int c = 0xFF32DDF1;
            graphics.fill(x, y, x + 20, y + 1, c);
            graphics.fill(x, y + 19, x + 20, y + 20, c);
            graphics.fill(x, y, x + 1, y + 20, c);
            graphics.fill(x + 19, y, x + 20, y + 20, c);
        }
    }

    @Override protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, Component.translatable("gui.infinitechest.inventory"), 8, 128, 0x404040, false);
        graphics.text(font, Component.literal((menu.page()+1)+" / "+menu.pages()), 235, 36, 0x404040, false);
        graphics.text(font, Component.literal(targets.isEmpty() ? "0/0" : (targetIndex+1) + "/" + targets.size()), 263, 134, 0x404040, false);

    }
}
