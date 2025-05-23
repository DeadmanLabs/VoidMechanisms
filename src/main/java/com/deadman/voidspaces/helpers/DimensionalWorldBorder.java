package com.deadman.voidspaces.helpers;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DimensionalWorldBorder extends WorldBorder {
    private static final Logger LOGGER = LoggerFactory.getLogger(DimensionalWorldBorder.class);
    private final ServerLevel dimension;
    private boolean initialized = false;

    public DimensionalWorldBorder(ServerLevel dimension) {
        super();
        this.dimension = dimension;
    }

    @Override
    public boolean isWithinBounds(BlockPos pos) {
        if (pos.getY() < this.dimension.getMinBuildHeight() || pos.getY() >= this.dimension.getMaxBuildHeight()) {
            return false;
        }
        return super.isWithinBounds(pos);
    }
    
    // Override setters to prevent external modification of our custom border settings
    @Override
    public void setCenter(double x, double z) {
        if (!initialized) {
            LOGGER.info("DimensionalWorldBorder: Setting initial center to ({}, {})", x, z);
            super.setCenter(x, z);
            if (x == 0.0 && z == 0.0) {
                initialized = true;
            }
        } else if (x != 0.0 || z != 0.0) {
            LOGGER.warn("DimensionalWorldBorder: Ignoring attempt to change center from (0,0) to ({}, {})", x, z);
            // Ignore external attempts to change our center from (0,0)
        } else {
            super.setCenter(x, z);
        }
    }
    
    @Override
    public void setSize(double size) {
        if (!initialized) {
            LOGGER.info("DimensionalWorldBorder: Setting initial size to {}", size);
            super.setSize(size);
            if (size == 50.0) {
                initialized = true;
            }
        } else if (size != 50.0) {
            LOGGER.warn("DimensionalWorldBorder: Ignoring attempt to change size from 50 to {}", size);
            // Ignore external attempts to change our size from 50
        } else {
            super.setSize(size);
        }
    }
    
    @Override
    public void setAbsoluteMaxSize(int maxSize) {
        if (!initialized || maxSize == 51) {
            LOGGER.info("DimensionalWorldBorder: Setting max size to {}", maxSize);
            super.setAbsoluteMaxSize(maxSize);
        } else {
            LOGGER.warn("DimensionalWorldBorder: Ignoring attempt to change max size to {}", maxSize);
        }
    }
    
    @Override
    public void setDamagePerBlock(double damagePerBlock) {
        if (!initialized || damagePerBlock == 0.2) {
            LOGGER.info("DimensionalWorldBorder: Setting damage per block to {}", damagePerBlock);
            super.setDamagePerBlock(damagePerBlock);
        } else {
            LOGGER.warn("DimensionalWorldBorder: Ignoring attempt to change damage per block to {}", damagePerBlock);
        }
    }
    
    @Override
    public void setWarningBlocks(int warningBlocks) {
        if (!initialized || warningBlocks == 5) {
            LOGGER.info("DimensionalWorldBorder: Setting warning blocks to {}", warningBlocks);
            super.setWarningBlocks(warningBlocks);
        } else {
            LOGGER.warn("DimensionalWorldBorder: Ignoring attempt to change warning blocks to {}", warningBlocks);
        }
    }
    
    @Override
    public void addListener(BorderChangeListener listener) {
        // Check if this is a delegate listener trying to sync with overworld
        if (listener instanceof BorderChangeListener.DelegateBorderChangeListener) {
            LOGGER.warn("DimensionalWorldBorder: Ignoring delegate border listener to prevent overworld sync");
            // Don't add delegate listeners that would sync us with the overworld
            return;
        }
        LOGGER.info("DimensionalWorldBorder: Adding border listener: {}", listener.getClass().getSimpleName());
        super.addListener(listener);
    }
}
