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

    private static int textureWidth = 32; // Width of the progress bar texture
    private static int textureHeight = 64; // Height of the progress bar texture

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
            // Rotate the texture 90 degrees for the horizontal bar
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(getX(), getY() + height, 0); // Move to the correct position
            guiGraphics.pose().rotateAround(com.mojang.math.Axis.ZP.rotationDegrees(-90), 0, 0, 0); // Rotate -90 degrees

            // Render the empty portion (left side of the texture)
            guiGraphics.blit(
                    RedTextures, // Texture
                    0, 0, // Screen position (after rotation)
                    0, 0, // Texture position (left side)
                    height, width, // Size (swapped for rotation)
                    textureWidth, textureHeight // Texture size
            );

            // Render the full portion (right side of the texture)
            int filledWidth = (int) (width * progress);
            guiGraphics.blit(
                    RedTextures, // Texture
                    0, 0, // Screen position (after rotation)
                    16, 0, // Texture position (right side)
                    height, filledWidth, // Size (swapped for rotation)
                    textureWidth, textureHeight // Texture size
            );

            guiGraphics.pose().popPose();
        } else {
            guiGraphics.blit(
                    RedTextures, // Texture
                    getX(), getY(), // Screen position (top-down)
                    0, 0, // Texture position (top-down)
                    width, height, // Size
                    textureWidth, textureHeight // Texture size
            );
            int filledHeight = (int) (height * progress);
            guiGraphics.blit(
                    RedTextures, // Texture
                    getX(), getY() + (height - filledHeight), // Screen position (bottom-up)
                    16, height - filledHeight, // Texture position (bottom-up)
                    width, filledHeight, // Size
                    textureWidth, textureHeight // Texture size
            );
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        narrationElementOutput.add(NarratedElementType.TITLE, Component.literal("Progress Bar?"));
    }
}