package com.deadman.voidspaces.world.inventory;

import com.deadman.voidspaces.block.entity.EngineEntity;
import com.deadman.voidspaces.init.Menus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public class VoidEngineMenu extends AbstractContainerMenu {

    private final EngineEntity engine;

    // ContainerData indices: 0=chunkSize, 1=dimensionStatus, 2=simulationSpeed, 3=randomTickSpeed
    private final ContainerData engineData;

    /** Server-side constructor */
    public VoidEngineMenu(int containerId, Inventory playerInventory, EngineEntity engine, ContainerData engineData) {
        super(Menus.VOID_ENGINE_MENU.get(), containerId);
        this.engine = engine;
        this.engineData = engineData;
        addDataSlots(engineData);
    }

    /** Client-side constructor (called by Menus factory via network buffer) */
    public VoidEngineMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        super(Menus.VOID_ENGINE_MENU.get(), containerId);
        BlockPos pos = buf.readBlockPos();
        BlockEntity be = playerInventory.player.level().getBlockEntity(pos);
        if (be instanceof EngineEntity eng) {
            this.engine = eng;
            this.engineData = eng.getEngineData();
        } else {
            this.engine = null;
            this.engineData = new SimpleContainerData(4);
        }
        addDataSlots(engineData);
    }

    public EngineEntity getEngine() {
        return engine;
    }

    public int getChunkSize() {
        return engineData.get(0);
    }

    public int getDimensionStatus() {
        return engineData.get(1);
    }

    public int getSimulationSpeed() {
        return engineData.get(2);
    }

    public int getRandomTickSpeed() {
        return engineData.get(3);
    }

    public boolean hasDimension() {
        return getDimensionStatus() > 0;
    }

    public boolean isSimulating() {
        return getDimensionStatus() == 2;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (engine == null) return false;
        return engine.getLevel() != null &&
               engine.getLevel().getBlockEntity(engine.getBlockPos()) == engine;
    }
}
