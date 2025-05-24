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
    private static final int BORDER_TOP = 1; // First row is border
    private static final int BORDER_BOTTOM = 1; // Last row is border  
    private static final int EMPTY_START_X = 1; // Empty bar starts at column 1 (includes left border)
    private static final int EMPTY_END_X = 14; // Empty bar ends at column 14
    private static final int FULL_START_X = 17; // Full bar starts at column 17
    private static final int FULL_END_X = 30; // Full bar ends at column 30
    private static final int BAR_WIDTH = EMPTY_END_X - EMPTY_START_X + 1; // Width of actual bar (now 14 pixels)

    private float progress; // Progress value between 0 and 1
    private final boolean isHorizontal; // Orientation of the progress bar

    public ProgressBar(int x, int y, int width, int height, boolean isHorizontal) {
        super(x, y, width, height, Component.empty());
        this.progress = 0.0f;
        this.isHorizontal = isHorizontal;
    }

    public void setPosition(int x, int y) {
        this.setX(x);
        this.setY(y);
    }

    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
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
        
        // Start from the bottom and work our way up
        int currentY = getY() + barHeight - 1; // Start at bottom
        
        // Render bottom border (1 pixel)
        if (barHeight > 0) {
            guiGraphics.blit(
                RedTextures,
                getX(), currentY,
                EMPTY_START_X, TEXTURE_HEIGHT - BORDER_BOTTOM, // Bottom border from empty texture
                this.width, BORDER_BOTTOM, // Use full component width
                TEXTURE_WIDTH, TEXTURE_HEIGHT
            );
            currentY -= BORDER_BOTTOM;
        }
        
        // Render the body from bottom to top
        int remainingHeight = barHeight - 2; // Height excluding both borders
        int filledBodyHeight = Math.max(0, filledHeight - 1); // Filled height excluding bottom border
        filledBodyHeight = Math.min(filledBodyHeight, remainingHeight); // Don't exceed available space
        
        // Render filled portion (from bottom up)
        // Use a fixed pattern so texture doesn't appear to move
        int totalBodyPixels = remainingHeight;
        for (int i = 0; i < filledBodyHeight; i++) {
            // Calculate which texture row to use based on position from bottom
            int positionFromBottom = i;
            int textureRow = BORDER_TOP + (positionFromBottom % 2); // Alternate between the two body rows
            guiGraphics.blit(
                RedTextures,
                getX(), currentY,
                FULL_START_X, textureRow, // Full texture
                this.width, 1, // Use full component width
                TEXTURE_WIDTH, TEXTURE_HEIGHT
            );
            currentY--;
        }
        
        // Render empty portion (rest of the body)
        int emptyBodyHeight = remainingHeight - filledBodyHeight;
        for (int i = 0; i < emptyBodyHeight; i++) {
            // Continue the pattern from where filled portion left off
            int positionFromBottom = filledBodyHeight + i;
            int textureRow = BORDER_TOP + (positionFromBottom % 2); // Alternate between the two body rows
            guiGraphics.blit(
                RedTextures,
                getX(), currentY,
                EMPTY_START_X, textureRow, // Empty texture
                this.width, 1, // Use full component width
                TEXTURE_WIDTH, TEXTURE_HEIGHT
            );
            currentY--;
        }
        
        // Render top border (1 pixel)
        if (barHeight > 1) {
            guiGraphics.blit(
                RedTextures,
                getX(), getY(),
                EMPTY_START_X, 0, // Top border from empty texture
                this.width, BORDER_TOP, // Use full component width
                TEXTURE_WIDTH, TEXTURE_HEIGHT
            );
        }
    }


    private void renderHorizontal(GuiGraphics guiGraphics) {
        // Render the horizontal bar as the vertical bar rotated 90 degrees clockwise
        guiGraphics.pose().pushPose();
        
        // Move to the bottom-left corner of where we want the horizontal bar
        // and rotate 90 degrees clockwise so the vertical bar becomes horizontal
        guiGraphics.pose().translate(getX(), getY(), 0);
        guiGraphics.pose().rotateAround(com.mojang.math.Axis.ZP.rotationDegrees(90), 0, height, 0);
        
        // Now render a "vertical" bar that will appear horizontal after rotation
        // In the rotated coordinate system:
        // - "height" of the rotated bar = width of the horizontal bar
        // - "width" of the rotated bar = height of the horizontal bar
        int rotatedBarHeight = this.width; // The "height" in rotated space
        int rotatedFilledHeight = (int) (rotatedBarHeight * progress);
        
        // Start from the bottom and work our way up (in rotated space)
        int currentY = rotatedBarHeight - 1; // Start at bottom in rotated space
        
        // Render bottom border (1 pixel) - use filled texture if we have any progress
        if (rotatedBarHeight > 0) {
            int textureX = (rotatedFilledHeight > 0) ? FULL_START_X : EMPTY_START_X;
            guiGraphics.blit(
                RedTextures,
                0, currentY, // No offset needed since we translated
                textureX, TEXTURE_HEIGHT - BORDER_BOTTOM,
                this.height, BORDER_BOTTOM, // Width in rotated space = height of horizontal bar
                TEXTURE_WIDTH, TEXTURE_HEIGHT
            );
            currentY -= BORDER_BOTTOM;
        }
        
        // Render the body from bottom to top (in rotated space)
        int remainingHeight = rotatedBarHeight - 2; // Height excluding both borders
        int filledBodyHeight = Math.max(0, rotatedFilledHeight - 1); // Filled height excluding bottom border
        filledBodyHeight = Math.min(filledBodyHeight, remainingHeight); // Don't exceed available space
        
        // Render filled portion (from bottom up in rotated space)
        for (int i = 0; i < filledBodyHeight; i++) {
            int positionFromBottom = i;
            int textureRow = BORDER_TOP + (positionFromBottom % 2);
            guiGraphics.blit(
                RedTextures,
                0, currentY,
                FULL_START_X, textureRow,
                this.height, 1, // Width in rotated space = height of horizontal bar
                TEXTURE_WIDTH, TEXTURE_HEIGHT
            );
            currentY--;
        }
        
        // Render empty portion (rest of the body in rotated space)
        int emptyBodyHeight = remainingHeight - filledBodyHeight;
        for (int i = 0; i < emptyBodyHeight; i++) {
            int positionFromBottom = filledBodyHeight + i;
            int textureRow = BORDER_TOP + (positionFromBottom % 2);
            guiGraphics.blit(
                RedTextures,
                0, currentY,
                EMPTY_START_X, textureRow,
                this.height, 1, // Width in rotated space = height of horizontal bar
                TEXTURE_WIDTH, TEXTURE_HEIGHT
            );
            currentY--;
        }
        
        // Render top border (1 pixel in rotated space) - always empty
        if (rotatedBarHeight > 1) {
            guiGraphics.blit(
                RedTextures,
                0, 0,
                EMPTY_START_X, 0,
                this.height, BORDER_TOP, // Width in rotated space = height of horizontal bar
                TEXTURE_WIDTH, TEXTURE_HEIGHT
            );
        }
        
        guiGraphics.pose().popPose();
    }


    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        narrationElementOutput.add(NarratedElementType.TITLE, Component.literal("Progress Bar: " + (int) (progress * 100) + "%"));
    }
}