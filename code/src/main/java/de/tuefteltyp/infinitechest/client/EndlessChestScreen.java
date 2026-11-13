package de.tuefteltyp.infinitechest.client;

import de.tuefteltyp.infinitechest.GuiNetwork;
import de.tuefteltyp.infinitechest.PagedChestMenu;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.core.BlockPos;

/** Native in-game widgets around the server-synchronized storage slots. */
public final class EndlessChestScreen extends AbstractContainerScreen<PagedChestMenu> {
    private static final Identifier GUI_TEXTURE = Identifier.fromNamespaceAndPath("infinitechest", "textures/gui/endless_chest_gui.png");
    private EditBox search;
    private EditBox targetX;
    private EditBox targetY;
    private EditBox targetZ;
    private EditBox amount;
    private EditBox itemNumber;
    private int chosenSlot = -1;
    private int delayedSearch = -1;
    private String lastQuery = "";

    public EndlessChestScreen(PagedChestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 258, 278);
    }

    @Override protected void init() {
        super.init();
        int x = this.leftPos;
        int y = this.topPos;
        search = new EditBox(this.font, x + 48, y + 23, 160, 17, Component.translatable("gui.infinitechest.search"));
        search.setHint(Component.translatable("gui.infinitechest.search"));
        search.setMaxLength(64);
        search.setResponder(value -> delayedSearch = 6);
        addRenderableWidget(search);
        addRenderableWidget(Button.builder(Component.literal("◀"), b -> sendPage(0)).bounds(x + 25, y + 145, 30, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.infinitechest.sort"), b -> sendPage(2)).bounds(x + 62, y + 145, 68, 18).build());
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> sendPage(1)).bounds(x + 138, y + 145, 30, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.infinitechest.withdraw"), b -> sendWithdraw())
                .bounds(x + 177, y + 145, 68, 18).build());
        itemNumber = field(x + 5, y + 166, 20, "#");
        targetX = field(x + 28, y + 166, 40, "X");
        targetY = field(x + 72, y + 166, 40, "Y");
        targetZ = field(x + 116, y + 166, 40, "Z");
        amount = field(x + 160, y + 166, 32, "64");
        amount.setValue("64");
        addRenderableWidget(Button.builder(Component.translatable("gui.infinitechest.send"), b -> sendTransfer())
                .bounds(x + 196, y + 165, 50, 18).build());
    }

    private EditBox field(int x, int y, int w, String hint) {
        EditBox box = new EditBox(this.font, x, y, w, 17, Component.literal(hint));
        box.setMaxLength(11);
        box.setHint(Component.literal(hint));
        addRenderableWidget(box);
        return box;
    }

    @Override protected void containerTick() {
        super.containerTick();
        if (delayedSearch >= 0 && --delayedSearch == 0) {
            String query = search.getValue();
            if (!query.equals(lastQuery)) {
                lastQuery = query;
                ClientPlayNetworking.send(new GuiNetwork.Search(query));
            }
        }
    }

    private void sendPage(int action) { ClientPlayNetworking.send(new GuiNetwork.Action(action)); }

    private int selectedSlot() {
        if (!itemNumber.getValue().isBlank()) {
            try { return Integer.parseInt(itemNumber.getValue().strip()) - 1; }
            catch (NumberFormatException ignored) { return -1; }
        }
        return chosenSlot;
    }

    private void sendWithdraw() {
        int index = selectedSlot();
        if (index < 0 || index >= PagedChestMenu.STORAGE_SLOTS) return;
        int count = 64;
        try { count = Math.clamp(Integer.parseInt(amount.getValue().strip()), 1, 64); }
        catch (NumberFormatException ignored) { }
        ClientPlayNetworking.send(new GuiNetwork.Withdraw(index, count));
    }

    private void sendTransfer() {
        int index = selectedSlot();
        if (index < 0 || index >= PagedChestMenu.STORAGE_SLOTS) return;
        try {
            int x = Integer.parseInt(targetX.getValue().trim());
            int y = Integer.parseInt(targetY.getValue().trim());
            int z = Integer.parseInt(targetZ.getValue().trim());
            int count = Integer.parseInt(amount.getValue().trim());
            if (count < 1 || count > 2304) return;
            ClientPlayNetworking.send(new GuiNetwork.Transfer(index, new BlockPos(x, y, z), count));
        } catch (NumberFormatException ignored) { /* invalid coordinates: no request is sent */ }
    }

    @Override protected void slotClicked(Slot slot, int slotId, int mouseButton, net.minecraft.world.inventory.ContainerInput clickType) {
        if (slot != null && slotId >= 0 && slotId < PagedChestMenu.STORAGE_SLOTS) {
            chosenSlot = slotId;
            // Empty-hand clicks only select; use Take button to withdraw stored items.
            if (this.menu.getCarried().isEmpty()) return;
        }
        super.slotClicked(slot, slotId, mouseButton, clickType);
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int x = leftPos; int y = topPos;
        graphics.blit(RenderPipelines.GUI_TEXTURED, GUI_TEXTURE, x, y, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
    }

    @Override protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(this.font, Component.literal((menu.page()+1) + " / " + menu.pages()), 85, 149, 0xFFEFD57A, false);
        graphics.text(this.font, Component.literal(chosenSlot < 0 ? "-" : ("#" + (chosenSlot + 1))), 215, 29, 0xFFEFD57A, false);
    }
}