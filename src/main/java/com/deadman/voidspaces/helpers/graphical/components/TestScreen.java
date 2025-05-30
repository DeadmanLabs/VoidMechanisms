package com.deadman.voidspaces.helpers.graphical.components;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import com.deadman.voidspaces.helpers.graphical.components.ProgressBar;
import com.deadman.voidspaces.helpers.graphical.components.ItemFluidList;
import com.deadman.voidspaces.helpers.graphical.components.LineChart;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.Random;

public class TestScreen extends Screen {
    private ProgressBar verticalProgressBar;
    private ProgressBar horizontalProgressBar;
    private ItemFluidList listTest;
    private LineChart chartTest;
    private float progress = 0.0f;

    private final Random random = new Random();

    public TestScreen() {
        super(Component.literal("Test Screen"));
    }

    @Override
    protected void init() {
        // Initialize the vertical progress bar (or reposition if it exists)
        if (verticalProgressBar == null) {
            verticalProgressBar = new ProgressBar(
                    this.width / 2 - 8, // Center horizontally
                    this.height / 2 - 60, // Position above the horizontal bar
                    16, Math.min(100, this.height / 3), // Width and height - scale height with window
                    false // Vertical orientation
            );
            verticalProgressBar.setProgress(progress);
        } else {
            verticalProgressBar.setPosition(this.width / 2 - 8, this.height / 2 - 60);
            verticalProgressBar.setSize(16, Math.min(100, this.height / 3)); // Scale with window
        }
        addRenderableWidget(verticalProgressBar);

        // Initialize the horizontal progress bar (or reposition if it exists)
        if (horizontalProgressBar == null) {
            horizontalProgressBar = new ProgressBar(
                    this.width / 2 - 50, // Center horizontally
                    this.height / 2, // Position below the vertical bar
                    Math.min(100, this.width / 6), 16, // Width and height - scale width with window
                    true // Horizontal orientation
            );
            horizontalProgressBar.setProgress(progress);
        } else {
            horizontalProgressBar.setPosition(this.width / 2 - 50, this.height / 2);
            horizontalProgressBar.setSize(Math.min(100, this.width / 6), 16); // Scale with window
        }
        addRenderableWidget(horizontalProgressBar);

        // Add a button to increase the progress
        addRenderableWidget(Button.builder(Component.literal("Increase Progress"), button -> increaseProgress())
                .bounds(this.width / 2 - 50, this.height / 2 + 30, 100, 20) // Position and size
                .build());

        // Initialize the item/fluid list (or reposition if it exists)
        if (listTest == null) {
            listTest = new ItemFluidList(
                    this.width / 2 + 60,
                    this.height / 2 - 60,
                    Math.min(150, this.width / 4), Math.min(120, this.height / 3) // Scale with window
            );
        } else {
            listTest.setPosition(this.width / 2 + 60, this.height / 2 - 60);
            listTest.setSize(Math.min(150, this.width / 4), Math.min(120, this.height / 3)); // Scale with window
        }
        addRenderableWidget(listTest);

        addRenderableWidget(Button.builder(Component.literal("Add Item"), button -> addItem())
                .bounds(this.width / 2 + 60, this.height / 2 + 70, 50, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Add Fluid"), button -> addFluid())
                .bounds(this.width / 2 + 110, this.height / 2 + 70, 50, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Remove Item"), button -> removeItem())
                .bounds(this.width / 2 + 60, this.height / 2 + 100, 50, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Remove Fluid"), button -> removeFluid())
                .bounds(this.width / 2 + 110, this.height / 2 + 100, 50, 20)
                .build());

        // Initialize the chart (or reposition if it exists)
        if (chartTest == null) {
            chartTest = new LineChart(
                    20, this.height / 2 - 50, 
                    Math.min(150, this.width / 4), Math.min(80, this.height / 4), // Scale with window
                    32
            );
        } else {
            chartTest.setPosition(20, this.height / 2 - 50);
            chartTest.setSize(Math.min(150, this.width / 4), Math.min(80, this.height / 4)); // Scale with window
        }
        addRenderableWidget(chartTest);

        addRenderableWidget(Button.builder(Component.literal("Add Data"), button -> addChartData())
                .bounds(20, this.height / 2 + 10, 100, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Clear Chart"), button -> clearChartData())
                .bounds(20, this.height / 2 + 40, 100, 20)
                .build());
    }

    private void addItem() {
        listTest.addItem(new ItemStack(Items.DIAMOND), 64);
    }

    private void addFluid() {
        listTest.addFluid(new FluidStack(Fluids.WATER, 5000), 5000);
    }

    private void removeItem() {
        listTest.removeItem(new ItemStack(Items.DIAMOND), 32);
    }

    private void removeFluid() {
        listTest.removeFluid(new FluidStack(Fluids.WATER, 2500), 2500);
    }

    private void addChartData() {
        if (chartTest != null) {
            double randomValue = 1 + random.nextDouble() * 99;
            chartTest.addDatapoint(randomValue);
        }
    }

    private void clearChartData() {
        if (chartTest != null) {
            chartTest.clearData();
        }
    }

    private void increaseProgress() {
        progress += 0.01f; // Increase progress by 10%
        if (progress > 1.0f) {
            progress = 0.0f; // Reset progress if it exceeds 100%
        }
        verticalProgressBar.setProgress(progress);
        horizontalProgressBar.setProgress(progress);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTicks); // Render the default background
        super.render(guiGraphics, mouseX, mouseY, partialTicks);

        // Draw a label above the vertical progress bar
        guiGraphics.drawString(
                this.font,
                "Vertical Progress: " + (int) (progress * 100) + "%",
                this.width / 2 - 40,
                this.height / 2 - 80,
                0xFFFFFF // White color
        );

        // Draw a label above the horizontal progress bar
        guiGraphics.drawString(
                this.font,
                "Horizontal Progress: " + (int) (progress * 100) + "%",
                this.width / 2 - 50,
                this.height / 2 - 20,
                0xFFFFFF // White color
        );
    }

    @Override
    public boolean isPauseScreen() {
        return false; // Don't pause the game when this screen is open
    }
}
