package com.deadman.voidspaces.client.gui;

import com.deadman.voidspaces.world.inventory.VoidDropperMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class VoidDropperScreen extends AbstractContainerScreen<VoidDropperMenu> {
    private static final ResourceLocation CHEST_LOCATION = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final int CONTAINER_ROWS = 5;

    public VoidDropperScreen(VoidDropperMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageHeight = 114 + CONTAINER_ROWS * 18; // Adjust height for 5 rows
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int i = (this.width - this.imageWidth) / 2;
        int j = (this.height - this.imageHeight) / 2;
        guiGraphics.blit(CHEST_LOCATION, i, j, 0, 0, this.imageWidth, CONTAINER_ROWS * 18 + 17);
        guiGraphics.blit(CHEST_LOCATION, i, j + CONTAINER_ROWS * 18 + 17, 0, 126, this.imageWidth, 96);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Render title
        guiGraphics.drawString(this.font, this.title, 8, 6, 4210752, false);
        
        // Render page info
        String pageInfo = "Page " + this.menu.getCurrentPage() + " / " + this.menu.getMaxPages();
        int pageInfoWidth = this.font.width(pageInfo);
        guiGraphics.drawString(this.font, pageInfo, this.imageWidth - pageInfoWidth - 8, 6, 4210752, false);
        
        // Render inventory label
        guiGraphics.drawString(this.font, this.playerInventoryTitle, 8, this.inventoryLabelY, 4210752, false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Handle page navigation with arrow keys
        if (keyCode == 262) { // Right arrow
            this.menu.nextPage();
            return true;
        } else if (keyCode == 263) { // Left arrow
            this.menu.previousPage();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        // Could add auto-refresh logic here if needed
    }
}