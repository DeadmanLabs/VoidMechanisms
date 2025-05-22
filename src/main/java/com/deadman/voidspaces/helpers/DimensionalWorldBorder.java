package com.deadman.voidspaces.helpers;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.server.level.ServerLevel;

public class DimensionalWorldBorder extends WorldBorder {
    private final ServerLevel dimension;

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
}
