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
    private double expectedCenter;
    private double expectedSize;
    private int expectedMaxSize;

    public DimensionalWorldBorder(ServerLevel dimension, int chunkSize) {
        super();
        this.dimension = dimension;
        configure(chunkSize);
    }

    /** Re-apply border parameters when chunk size changes after initial creation. */
    public void configure(int chunkSize) {
        initialized = false;
        expectedSize = chunkSize * 16.0;
        expectedCenter = chunkSize * 8.0 - 0.5;
        expectedMaxSize = chunkSize * 16;
        setCenter(expectedCenter, expectedCenter);
        setSize(expectedSize);
        setAbsoluteMaxSize(expectedMaxSize);
        setDamagePerBlock(0.2);
        setWarningBlocks(0);
        initialized = true;
        LOGGER.info("DimensionalWorldBorder configured: chunkSize={}, center=({},{}), size={}",
                    chunkSize, expectedCenter, expectedCenter, expectedSize);
    }

    @Override
    public boolean isWithinBounds(BlockPos pos) {
        if (pos.getY() < dimension.getMinBuildHeight() || pos.getY() >= dimension.getMaxBuildHeight()) {
            return false;
        }
        return super.isWithinBounds(pos);
    }

    @Override
    public void setCenter(double x, double z) {
        if (!initialized) {
            super.setCenter(x, z);
        } else if (x != expectedCenter || z != expectedCenter) {
            LOGGER.warn("DimensionalWorldBorder: Ignoring attempt to change center to ({},{})", x, z);
        } else {
            super.setCenter(x, z);
        }
    }

    @Override
    public void setSize(double size) {
        if (!initialized) {
            super.setSize(size);
        } else if (size != expectedSize) {
            LOGGER.warn("DimensionalWorldBorder: Ignoring attempt to change size to {}", size);
        } else {
            super.setSize(size);
        }
    }

    @Override
    public void setAbsoluteMaxSize(int maxSize) {
        if (!initialized || maxSize == expectedMaxSize) {
            super.setAbsoluteMaxSize(maxSize);
        } else {
            LOGGER.warn("DimensionalWorldBorder: Ignoring attempt to change max size to {}", maxSize);
        }
    }

    @Override
    public void setDamagePerBlock(double damagePerBlock) {
        if (!initialized || damagePerBlock == 0.2) {
            super.setDamagePerBlock(damagePerBlock);
        } else {
            LOGGER.warn("DimensionalWorldBorder: Ignoring attempt to change damage per block to {}", damagePerBlock);
        }
    }

    @Override
    public void setWarningBlocks(int warningBlocks) {
        if (!initialized || warningBlocks == 0) {
            super.setWarningBlocks(warningBlocks);
        } else {
            LOGGER.warn("DimensionalWorldBorder: Ignoring attempt to change warning blocks to {}", warningBlocks);
        }
    }

    @Override
    public void addListener(BorderChangeListener listener) {
        if (listener instanceof BorderChangeListener.DelegateBorderChangeListener) {
            LOGGER.warn("DimensionalWorldBorder: Ignoring delegate border listener to prevent overworld sync");
            return;
        }
        super.addListener(listener);
    }
}
