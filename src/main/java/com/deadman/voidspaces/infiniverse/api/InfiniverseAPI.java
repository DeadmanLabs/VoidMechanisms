package com.deadman.voidspaces.infiniverse.api;

import java.util.Set;
import java.util.function.Supplier;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;

import com.deadman.voidspaces.infiniverse.internal.DimensionManager;

public interface InfiniverseAPI
{
    public static InfiniverseAPI get() {
        return DimensionManager.INSTANCE;
    }

    public abstract ServerLevel getOrCreateLevel(final MinecraftServer server, final ResourceKey<Level> levelKey, final Supplier<LevelStem> dimensionFactory);
    public abstract void markDimensionForUnregistration(final MinecraftServer server, final ResourceKey<Level> levelToRemove);
    public abstract Set<ResourceKey<Level>> getLevelsPendingUnregistration();
}