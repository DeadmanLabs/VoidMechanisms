package com.deadman.voidspaces.helpers.graphical.components;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.HashMap;
import java.util.Map;

public class ItemFluidList extends AbstractWidget {
    private final Map<String, Entry> itemEntries = new HashMap<>();
    private final Map<String, Entry> fluidEntries = new HashMap<>();
    private final int padding = 2; // Padding between items
    private boolean showNames = true; // Whether to display item/fluid names
    private final ItemRenderer itemRenderer;
    private final Font font;

    public ItemFluidList(int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty());
        this.itemRenderer = Minecraft.getInstance().getItemRenderer();
        this.font = Minecraft.getInstance().font;
    }

    public void addItem(ItemStack itemStack, int count) {
        String key = itemStack.getItem().getDescriptionId(); // Use unique ID for merging
        itemEntries.merge(key, new Entry(itemStack, count), Entry::merge);
    }

    public void addFluid(FluidStack fluidStack, int millibuckets) {
        String key = fluidStack.getHoverName().toString(); // Use unique ID for merging
        fluidEntries.merge(key, new Entry(fluidStack, millibuckets), Entry::merge);
    }

    public void removeItem(ItemStack itemStack, int count) {
        String key = itemStack.getItem().getDescriptionId();
        itemEntries.computeIfPresent(key, (k, entry) -> entry.decrement(count));
        itemEntries.entrySet().removeIf(entry -> entry.getValue().count <= 0);
    }

    public void removeFluid(FluidStack fluidStack, int millibuckets) {
        String key = fluidStack.getHoverName().toString();
        fluidEntries.computeIfPresent(key, (k, entry) -> entry.decrement(millibuckets));
        fluidEntries.entrySet().removeIf(entry -> entry.getValue().count <= 0);
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        int currentY = getY();

        // Render items
        for (Entry entry : itemEntries.values()) {
            if (currentY + 16 > getY() + height) break; // Prevent overflow

            // Render the item icon
            guiGraphics.renderItem(entry.itemStack, getX(), currentY);

            // Render the name and count
            String displayText = showNames ? entry.itemStack.getHoverName().getString() + " x " + entry.count : "x " + entry.count;
            guiGraphics.drawString(font, displayText, getX() + 18, currentY + 4, 0xFFFFFF, false);

            currentY += 18 + padding; // Move down for the next entry
        }

        // Render fluids
        for (Entry entry : fluidEntries.values()) {
            if (currentY + 16 > getY() + height) break; // Prevent overflow

            // Render the fluid icon (using a bucket as a placeholder)
            ItemStack bucketStack = new ItemStack(entry.fluidStack.getFluid().getBucket());
            guiGraphics.renderItem(bucketStack, getX(), currentY);

            // Render the name and count
            String displayText = showNames ? entry.fluidStack.getHoverName().getString() + " x " + entry.count + "mb" : "x " + entry.count + "mb";
            guiGraphics.drawString(font, displayText, getX() + 18, currentY + 4, 0xFFFFFF, false);

            currentY += 18 + padding; // Move down for the next entry
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput pNarrationElementOutput) {
        pNarrationElementOutput.add(NarratedElementType.TITLE, Component.literal("Item and Fluid List?"));
    }

    private static class Entry {
        private final ItemStack itemStack;
        private final FluidStack fluidStack;
        private final boolean isItem;
        private int count;

        public Entry(ItemStack itemStack, int count) {
            this.itemStack = itemStack;
            this.fluidStack = FluidStack.EMPTY;
            this.isItem = true;
            this.count = count;
        }

        public Entry(FluidStack fluidStack, int count) {
            this.itemStack = ItemStack.EMPTY;
            this.fluidStack = fluidStack;
            this.isItem = false;
            this.count = count;
        }

        public static Entry merge(Entry a, Entry b) {
            a.count += b.count;
            return a;
        }

        public Entry decrement(int amount) {
            this.count -= amount;
            return this;
        }
    }
}