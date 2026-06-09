package com.deadman.voidspaces.client.gui;

import com.deadman.voidspaces.helpers.graphical.components.ItemFluidList;
import com.deadman.voidspaces.helpers.graphical.components.LineChart;
import com.deadman.voidspaces.init.EngineActionPacket;
import com.deadman.voidspaces.init.MaterialAnalysisResultPacket;
import com.deadman.voidspaces.init.SimulationDataPacket;
import com.deadman.voidspaces.world.inventory.VoidEngineMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class VoidEngineScreen extends AbstractContainerScreen<VoidEngineMenu> {

    private static final int TAB_INFO = 0;
    private static final int TAB_MATERIALS = 1;
    private static final int TAB_SIMULATION = 2;

    private static final int[] SIM_SPEEDS = {1, 5, 10, 20, 50};

    private static VoidEngineScreen INSTANCE;

    private int activeTab = TAB_INFO;

    // Info tab
    private Button enterButton;
    private Button[] chunkSizeButtons;

    // Materials tab
    private Button analyzeButton;
    private ItemFluidList materialsList;
    private List<MaterialAnalysisResultPacket.EntityCount> pendingEntityCounts = new ArrayList<>();

    // Simulation tab
    private Button runButton;
    private Button stopButton;
    private Button[] speedButtons;
    private ItemFluidList consumedList;
    private ItemFluidList producedList;
    private LineChart rateChart;
    private long displaySimTicks = 0;
    private long displayRealTicks = 0;

    // Pending sim data (received from server packet)
    private SimulationDataPacket pendingSimData = null;

    public VoidEngineScreen(VoidEngineMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 256;
        this.imageHeight = 220;
        INSTANCE = this;
    }

    @Override
    protected void init() {
        super.init();
        int ox = (this.width - this.imageWidth) / 2;
        int oy = (this.height - this.imageHeight) / 2;

        buildTabButtons(ox, oy);
        switchTab(activeTab);
    }

    private void buildTabButtons(int ox, int oy) {
        Button infoTab = Button.builder(Component.literal("Info"), b -> switchTab(TAB_INFO))
                .bounds(ox + 4, oy + 4, 60, 18).build();
        Button matsTab = Button.builder(Component.literal("Materials"), b -> switchTab(TAB_MATERIALS))
                .bounds(ox + 68, oy + 4, 70, 18).build();
        Button simTab = Button.builder(Component.literal("Simulation"), b -> switchTab(TAB_SIMULATION))
                .bounds(ox + 142, oy + 4, 80, 18).build();
        addRenderableWidget(infoTab);
        addRenderableWidget(matsTab);
        addRenderableWidget(simTab);
    }

    private void switchTab(int tab) {
        activeTab = tab;
        // Remove all non-tab widgets before rebuilding
        clearWidgets();
        int ox = (this.width - this.imageWidth) / 2;
        int oy = (this.height - this.imageHeight) / 2;
        buildTabButtons(ox, oy);
        switch (tab) {
            case TAB_INFO -> buildInfoTab(ox, oy);
            case TAB_MATERIALS -> buildMaterialsTab(ox, oy);
            case TAB_SIMULATION -> buildSimulationTab(ox, oy);
        }
    }

    // -------------------------------------------------------------------------
    // Info tab
    // -------------------------------------------------------------------------

    private void buildInfoTab(int ox, int oy) {
        int chunkSize = menu.getChunkSize();
        boolean locked = menu.hasDimension();
        int[] sizes = {1, 2, 3, 4};
        chunkSizeButtons = new Button[4];
        for (int i = 0; i < 4; i++) {
            final int sz = sizes[i];
            Button btn = Button.builder(
                    Component.literal(sz + "x" + sz + (locked ? "" : " ✓")),
                    b -> onChunkSizeClick(sz))
                    .bounds(ox + 8 + i * 54, oy + 34, 50, 18)
                    .build();
            btn.active = !locked;
            if (chunkSize == sz) btn.active = false;
            addRenderableWidget(btn);
            chunkSizeButtons[i] = btn;
        }

        int status = menu.getDimensionStatus();
        String statusText = switch (status) {
            case 0 -> "No dimension";
            case 1 -> "Loaded";
            case 2 -> "Simulating";
            default -> "Unknown";
        };

        enterButton = Button.builder(Component.literal("Enter Dimension"), b -> sendAction(EngineActionPacket.Action.ENTER, 0))
                .bounds(ox + 8, oy + 60, 140, 20)
                .build();
        addRenderableWidget(enterButton);

        // Random tick speed presets — only active when a dimension exists
        boolean hasDim = menu.hasDimension();
        int currentTickSpeed = menu.getRandomTickSpeed();
        int[] tickSpeeds = {3, 10, 20, 50, 100};
        for (int i = 0; i < tickSpeeds.length; i++) {
            final int ts = tickSpeeds[i];
            Button btn = Button.builder(Component.literal(String.valueOf(ts)),
                    b -> sendAction(EngineActionPacket.Action.SET_RANDOM_TICK, ts))
                    .bounds(ox + 8 + i * 46, oy + 116, 42, 16)
                    .build();
            btn.active = hasDim && currentTickSpeed != ts;
            addRenderableWidget(btn);
        }
    }

    private void onChunkSizeClick(int sz) {
        sendAction(EngineActionPacket.Action.SET_CHUNK_SIZE, sz);
        switchTab(TAB_INFO);
    }

    // -------------------------------------------------------------------------
    // Materials tab
    // -------------------------------------------------------------------------

    private void buildMaterialsTab(int ox, int oy) {
        analyzeButton = Button.builder(Component.literal("Analyze"), b -> sendAction(EngineActionPacket.Action.ANALYZE, 0))
                .bounds(ox + 8, oy + 34, 80, 18).build();
        addRenderableWidget(analyzeButton);

        materialsList = new ItemFluidList(ox + 8, oy + 58, imageWidth - 16, imageHeight - 70);
        addRenderableWidget(materialsList);
    }

    // -------------------------------------------------------------------------
    // Simulation tab
    // -------------------------------------------------------------------------

    private void buildSimulationTab(int ox, int oy) {
        boolean simulating = menu.isSimulating();
        int speed = menu.getSimulationSpeed();

        // Run uses the speed from ContainerData at click time, not at build time
        runButton = Button.builder(Component.literal("Run"),
                b -> sendAction(EngineActionPacket.Action.START_SIM, menu.getSimulationSpeed()))
                .bounds(ox + 8, oy + 34, 50, 18).build();
        runButton.active = !simulating && menu.hasDimension();
        addRenderableWidget(runButton);

        stopButton = Button.builder(Component.literal("Stop"),
                b -> sendAction(EngineActionPacket.Action.STOP_SIM, 0))
                .bounds(ox + 62, oy + 34, 50, 18).build();
        stopButton.active = simulating;
        addRenderableWidget(stopButton);

        // Speed buttons: 1x, 5x, 10x, 20x, 50x
        speedButtons = new Button[SIM_SPEEDS.length];
        for (int i = 0; i < SIM_SPEEDS.length; i++) {
            final int sp = SIM_SPEEDS[i];
            Button btn = Button.builder(Component.literal(sp + "x"),
                    b -> sendAction(EngineActionPacket.Action.SET_SPEED, sp))
                    .bounds(ox + 8 + i * 48, oy + 58, 44, 16).build();
            btn.active = (speed != sp);
            addRenderableWidget(btn);
            speedButtons[i] = btn;
        }

        consumedList = new ItemFluidList(ox + 8, oy + 82, (imageWidth / 2) - 12, 60);
        addRenderableWidget(consumedList);

        producedList = new ItemFluidList(ox + imageWidth / 2 + 4, oy + 82, (imageWidth / 2) - 12, 60);
        addRenderableWidget(producedList);

        rateChart = new LineChart(ox + 8, oy + 148, imageWidth - 16, 60, 60);
        addRenderableWidget(rateChart);

        // Apply pending sim data if any
        if (pendingSimData != null) {
            applySimData(pendingSimData);
            pendingSimData = null;
        }
    }

    // -------------------------------------------------------------------------
    // Real-time button state sync (runs every client tick)
    // -------------------------------------------------------------------------

    @Override
    protected void containerTick() {
        super.containerTick();
        if (activeTab != TAB_SIMULATION) return;
        boolean sim = menu.isSimulating();
        if (runButton != null) runButton.active = !sim && menu.hasDimension();
        if (stopButton != null) stopButton.active = sim;
        if (speedButtons != null) {
            int speed = menu.getSimulationSpeed();
            for (int i = 0; i < speedButtons.length && i < SIM_SPEEDS.length; i++) {
                if (speedButtons[i] != null) speedButtons[i].active = (speed != SIM_SPEEDS[i]);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Server→Client packet handlers (static, called from packets on client thread)
    // -------------------------------------------------------------------------

    public static void onAnalysisResult(MaterialAnalysisResultPacket pkt) {
        if (INSTANCE == null || INSTANCE.activeTab != TAB_MATERIALS) return;
        if (INSTANCE.materialsList == null) return;
        INSTANCE.materialsList.clearItems();
        for (MaterialAnalysisResultPacket.BlockCount bc : pkt.blockCounts()) {
            INSTANCE.materialsList.addItem(bc.stack(), (int) Math.min(bc.count(), Integer.MAX_VALUE));
        }
        INSTANCE.pendingEntityCounts = pkt.entityCounts();
    }

    public static void onSimulationData(SimulationDataPacket pkt) {
        if (INSTANCE == null) return;
        INSTANCE.pendingSimData = pkt;
        if (INSTANCE.activeTab == TAB_SIMULATION) {
            INSTANCE.applySimData(pkt);
            INSTANCE.pendingSimData = null;
        }
    }

    private void applySimData(SimulationDataPacket pkt) {
        displaySimTicks = pkt.simTicks();
        displayRealTicks = pkt.realTicks();

        if (consumedList != null) {
            consumedList.clearItems();
            for (SimulationDataPacket.PortEntry e : pkt.consumed()) {
                consumedList.addItem(e.stack(), (int) Math.min(e.count(), Integer.MAX_VALUE));
            }
        }

        if (producedList != null) {
            producedList.clearItems();
            for (SimulationDataPacket.PortEntry e : pkt.produced()) {
                producedList.addItem(e.stack(), (int) Math.min(e.count(), Integer.MAX_VALUE));
            }
            // Plot cumulative total items out across all OutPorts
            if (rateChart != null) {
                long totalOut = pkt.produced().stream().mapToLong(SimulationDataPacket.PortEntry::count).sum();
                rateChart.addDatapoint((double) totalOut);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int ox = (this.width - this.imageWidth) / 2;
        int oy = (this.height - this.imageHeight) / 2;

        // Panel background
        guiGraphics.fill(ox, oy, ox + imageWidth, oy + imageHeight, 0xFF2A2A2A);
        guiGraphics.fill(ox + 1, oy + 1, ox + imageWidth - 1, oy + imageHeight - 1, 0xFF3C3C3C);

        // Tab separator line
        guiGraphics.hLine(ox + 4, ox + imageWidth - 4, oy + 23, 0xFF888888);

        switch (activeTab) {
            case TAB_INFO -> renderInfoBg(guiGraphics, ox, oy);
            case TAB_MATERIALS -> renderMaterialsBg(guiGraphics, ox, oy);
            case TAB_SIMULATION -> renderSimulationBg(guiGraphics, ox, oy);
        }
    }

    private void renderInfoBg(GuiGraphics g, int ox, int oy) {
        int status = menu.getDimensionStatus();
        String statusStr = switch (status) {
            case 0 -> "Status: No dimension";
            case 1 -> "Status: Loaded (idle)";
            case 2 -> "Status: Simulating";
            default -> "Status: Unknown";
        };
        g.drawString(font, "Chunk Size:", ox + 8, oy + 27, 0xFFCCCCCC, false);
        g.drawString(font, statusStr, ox + 8, oy + 86, 0xFFCCCCCC, false);
        if (!menu.hasDimension()) {
            g.drawString(font, "(size locked after first entry)", ox + 8, oy + 97, 0xFF888888, false);
        }
        g.drawString(font, "Random Tick Speed:", ox + 8, oy + 107, 0xFFCCCCCC, false);
        if (menu.hasDimension()) {
            g.drawString(font, "current: " + menu.getRandomTickSpeed(), ox + 130, oy + 107, 0xFF888888, false);
        } else {
            g.drawString(font, "(create dimension first)", ox + 8, oy + 136, 0xFF666666, false);
        }
    }

    private void renderMaterialsBg(GuiGraphics g, int ox, int oy) {
        g.drawString(font, "Dimension Materials", ox + 8, oy + 27, 0xFFCCCCCC, false);
        if (!pendingEntityCounts.isEmpty()) {
            int y = oy + 120;
            g.drawString(font, "Entities:", ox + 8, y, 0xFFAAAAFF, false);
            y += 10;
            for (MaterialAnalysisResultPacket.EntityCount ec : pendingEntityCounts) {
                if (y > oy + imageHeight - 10) break;
                g.drawString(font, ec.entityType() + ": " + ec.count(), ox + 12, y, 0xFFCCCCCC, false);
                y += 10;
            }
        }
    }

    private void renderSimulationBg(GuiGraphics g, int ox, int oy) {
        g.drawString(font, "Consumed (InPort):", ox + 8, oy + 76, 0xFFAAAAFF, false);
        g.drawString(font, "Produced (OutPort):", ox + imageWidth / 2 + 4, oy + 76, 0xFFAAFFAA, false);
        g.drawString(font, "Sim ticks: " + displaySimTicks + "  Real: " + displayRealTicks,
                     ox + 8, oy + 144, 0xFF888888, false);
        g.drawString(font, "Total Out (graph):", ox + 8, oy + 154, 0xFF888888, false);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Skip vanilla title/inventory labels — we handle them in renderBg
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void sendAction(EngineActionPacket.Action action, int param) {
        if (menu.getEngine() == null) return;
        PacketDistributor.sendToServer(new EngineActionPacket(action.ordinal(), param, menu.getEngine().getBlockPos()));
    }

    @Override
    public void onClose() {
        INSTANCE = null;
        super.onClose();
    }
}
