package com.deadman.voidspaces.helpers.graphical.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.systems.RenderSystem;

import java.util.LinkedList;
import java.util.List;

public class LineChart extends AbstractWidget {
    private final List<Double> dataPoints = new LinkedList<>();
    private final int maxDataPoints;
    private final int padding = 5;
    private final Font font;

    public LineChart(int x, int y, int width, int height, int maxDataPoints) {
        super(x, y, width, height, Component.empty());
        this.maxDataPoints = maxDataPoints;
        this.font = Minecraft.getInstance().font;
    }

    public void addDatapoint(double value) {
        if (dataPoints.size() >= maxDataPoints) {
            dataPoints.removeFirst();
        }
        dataPoints.add(value);
    }

    public void clearData() {
        dataPoints.clear();
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        if (dataPoints.isEmpty()) {
            return; // Nothing to render
        }

        double minValue = dataPoints.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double maxValue = dataPoints.stream().mapToDouble(Double::doubleValue).max().orElse(1.0);

        int chartHeight = height - padding * 2;
        int chartWidth = width - padding * 2;

        // Draw the chart background
        guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, 0x80000000); // Transparent black background

        // Draw the min and max labels
        guiGraphics.drawString(font, String.format("%.2f", minValue), getX() + 2, getY() + height - padding - 8, 0xFFFFFF);
        guiGraphics.drawString(font, String.format("%.2f", maxValue), getX() + 2, getY() + padding - 8, 0xFFFFFF);

        // Normalize data points
        double range = maxValue - minValue;
        double scaleY = chartHeight / range;

        int prevX = getX() + padding;
        int prevY = getY() + height - padding - (int) ((dataPoints.get(0) - minValue) * scaleY);

        for (int i = 1; i < dataPoints.size(); i++) {
            int currentX = getX() + padding + (int) ((double) i / (maxDataPoints - 1) * chartWidth);
            int currentY = getY() + height - padding - (int) ((dataPoints.get(i) - minValue) * scaleY);

            drawLine(guiGraphics, prevX, prevY, currentX, currentY, 0xFFFF0000); // Red line

            prevX = currentX;
            prevY = currentY;
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput pNarrationElementOutput) {
        pNarrationElementOutput.add(NarratedElementType.TITLE, Component.literal("Line Chart?"));
    }

    private void drawLine(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int color) {
        if (x1 == x2) {
            // Vertical line
            guiGraphics.vLine(x1, Math.min(y1, y2), Math.max(y1, y2), color);
        } else if (y1 == y2) {
            // Horizontal line
            guiGraphics.hLine(Math.min(x1, x2), Math.max(x1, x2), y1, color);
        } else {
            // Diagonal line approximation
            int dx = Math.abs(x2 - x1);
            int dy = Math.abs(y2 - y1);
            int steps = Math.max(dx, dy);

            float xIncrement = (float) (x2 - x1) / steps;
            float yIncrement = (float) (y2 - y1) / steps;

            float x = x1;
            float y = y1;
            for (int i = 0; i <= steps; i++) {
                guiGraphics.fill((int) x, (int) y, (int) x + 1, (int) y + 1, color); // Draw a small point
                x += xIncrement;
                y += yIncrement;
            }
        }
    }
}