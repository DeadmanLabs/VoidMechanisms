package com.deadman.voidspaces.helpers.graphical.components;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class ProgressBar extends AbstractWidget {
    private static final ResourceLocation RedTextures = ResourceLocation.fromNamespaceAndPath("voidspaces", "textures/gui/storage_energy.png");
    private static final ResourceLocation PurpleTextures = ResourceLocation.fromNamespaceAndPath("voidspaces", "textures/gui/storage_energy_c.png");
    private static final ResourceLocation BlueTextures = ResourceLocation.fromNamespaceAndPath("voidspaces", "textures/gui/storage_coolant.png");

    private static final int TEXTURE_WIDTH = 32; // Width of the progress bar texture
    private static final int TEXTURE_HEIGHT = 64; // Height of the progress bar texture
    private static final int TOP_HEIGHT = 2; // Height of the top section (non-repeatable)
    private static final int BOTTOM_HEIGHT = 2; // Height of the bottom section (non-repeatable)
    private static final int MIDDLE_HEIGHT = TEXTURE_HEIGHT - TOP_HEIGHT - BOTTOM_HEIGHT; // Height of the middle section (repeatable)

    private float progress; // Progress value between 0 and 1
    private final boolean isHorizontal; // Orientation of the progress bar

    public ProgressBar(int x, int y, int width, int height, boolean isHorizontal) {
        super(x, y, width, height, Component.empty());
        this.progress = 0.0f;
        this.isHorizontal = isHorizontal;
    }

    public void setProgress(float progress) {
        this.progress = Math.max(0.0f, Math.min(1.0f, progress));
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        RenderSystem.setShaderTexture(0, RedTextures);

        if (isHorizontal) {
            renderHorizontal(guiGraphics);
        } else {
            renderVertical(guiGraphics);
        }
    }

    private void renderVertical(GuiGraphics guiGraphics) {
        int barHeight = this.height;
        int filledHeight = (int) (barHeight * progress);

        // Render the empty portion (left side of the texture)
        renderVerticalSection(guiGraphics, 0, 0, barHeight, false);

        // Render the full portion (right side of the texture)
        renderVerticalSection(guiGraphics, 16, barHeight - filledHeight, filledHeight, true);
    }

    private void renderVerticalSection(GuiGraphics guiGraphics, int textureX, int yOffset, int sectionHeight, boolean isFilled) {
        int remainingHeight = sectionHeight;

        // Render the bottom section (if needed)
        if (remainingHeight > 0 && sectionHeight <= BOTTOM_HEIGHT) {
            guiGraphics.blit(
                    RedTextures, // Texture
                    getX(), getY() + yOffset, // Screen position
                    textureX, TEXTURE_HEIGHT - BOTTOM_HEIGHT, // Texture position (bottom)
                    this.width, remainingHeight, // Size
                    TEXTURE_WIDTH, TEXTURE_HEIGHT // Texture size
            );
            return;
        }

        // Render the bottom section
        if (remainingHeight > 0) {
            guiGraphics.blit(
                    RedTextures, // Texture
                    getX(), getY() + yOffset, // Screen position
                    textureX, TEXTURE_HEIGHT - BOTTOM_HEIGHT, // Texture position (bottom)
                    this.width, BOTTOM_HEIGHT, // Size
                    TEXTURE_WIDTH, TEXTURE_HEIGHT // Texture size
            );
            yOffset += BOTTOM_HEIGHT;
            remainingHeight -= BOTTOM_HEIGHT;
        }

        // Render the middle section (repeatable)
        if (remainingHeight > 0) {
            int middleY = TOP_HEIGHT;
            int middleTextureHeight = MIDDLE_HEIGHT;

            // Tile the middle section vertically
            while (remainingHeight > 0) {
                int renderHeight = Math.min(remainingHeight, middleTextureHeight);
                guiGraphics.blit(
                        RedTextures, // Texture
                        getX(), getY() + yOffset, // Screen position
                        textureX, middleY, // Texture position (middle)
                        this.width, renderHeight, // Size
                        TEXTURE_WIDTH, TEXTURE_HEIGHT // Texture size
                );
                yOffset += renderHeight;
                remainingHeight -= renderHeight;
            }
        }

        // Render the top section
        if (remainingHeight > 0) {
            guiGraphics.blit(
                    RedTextures, // Texture
                    getX(), getY() + yOffset, // Screen position
                    textureX, 0, // Texture position (top)
                    this.width, TOP_HEIGHT, // Size
                    TEXTURE_WIDTH, TEXTURE_HEIGHT // Texture size
            );
        }
    }

    private void renderHorizontal(GuiGraphics guiGraphics) {
        int barWidth = this.width;
        int filledWidth = (int) (barWidth * progress);

        // Rotate the texture 90 degrees for the horizontal bar
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(getX(), getY() + height, 0); // Move to the correct position
        guiGraphics.pose().rotateAround(com.mojang.math.Axis.ZP.rotationDegrees(-90), 0, 0, 0); // Rotate -90 degrees

        // Render the empty portion (left side of the texture)
        renderVerticalSection(guiGraphics, 0, 0, barWidth, false);

        // Render the full portion (right side of the texture)
        renderVerticalSection(guiGraphics, 16, barWidth - filledWidth, filledWidth, true);

        guiGraphics.pose().popPose();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        narrationElementOutput.add(NarratedElementType.TITLE, Component.literal("Progress Bar: " + (int) (progress * 100) + "%"));
    }
}