package com.deadman.voidspaces.block.entity;

import com.deadman.voidspaces.init.BlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class VoidPowerConnectorEntity extends BlockEntity {

    // Infinite energy storage that always provides unlimited power
    private final IEnergyStorage energyStorage = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return 0; // Doesn't receive energy
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return maxExtract; // Always provides what's requested
        }

        @Override
        public int getEnergyStored() {
            return Integer.MAX_VALUE;
        }

        @Override
        public int getMaxEnergyStored() {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    };

    public VoidPowerConnectorEntity(BlockPos pos, BlockState state) {
        super(BlockEntities.VOID_POWER_CONNECTOR_ENTITY.get(), pos, state);
    }

    public IEnergyStorage getEnergyStorage() {
        return energyStorage;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, VoidPowerConnectorEntity entity) {
        if (!level.isClientSide) {
            // Push energy to all adjacent blocks
            for (Direction dir : Direction.values()) {
                BlockPos neighborPos = pos.relative(dir);
                IEnergyStorage neighborStorage = level.getCapability(
                    Capabilities.EnergyStorage.BLOCK,
                    neighborPos,
                    dir.getOpposite()
                );
                if (neighborStorage != null && neighborStorage.canReceive()) {
                    // Push as much energy as the neighbor can accept
                    neighborStorage.receiveEnergy(Integer.MAX_VALUE, false);
                }
            }
        }
    }
}
