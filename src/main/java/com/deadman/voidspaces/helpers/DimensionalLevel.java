package com.deadman.voidspaces.helpers;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;

import java.util.List;
import java.util.concurrent.Executor;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DimensionalLevel extends ServerLevel {
    private static final Logger LOGGER = LoggerFactory.getLogger(DimensionalLevel.class);
    private DimensionalWorldBorder customWorldBorder = null;
    private int chunkSize = 1;

    public DimensionalLevel(
            MinecraftServer server,
            Executor executor,
            LevelStorageSource.LevelStorageAccess storageAccess,
            ServerLevelData worldData,
            ResourceKey<Level> dimension,
            LevelStem dimensionOptions,
            ChunkProgressListener statusListener,
            boolean debug,
            long seed,
            List<CustomSpawner> spawners,
            boolean shouldTickTime,
            RandomSequences randomSequences
    ) {
        super(server, executor, storageAccess, worldData, dimension, dimensionOptions, statusListener, debug, seed, spawners, shouldTickTime, randomSequences);
        LOGGER.info("DimensionalLevel constructed for dimension: {}", dimension.location());
    }

    /**
     * Set the chunk size for this dimension's world border.
     * Must be called by Dimensional immediately after getOrCreateLevel().
     * If the border was already lazily created, it will be reconfigured.
     */
    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
        if (this.customWorldBorder != null) {
            this.customWorldBorder.configure(chunkSize);
            LOGGER.info("Reconfigured world border for chunkSize={} on dimension: {}", chunkSize, dimension().location());
        }
    }

    public int getChunkSize() {
        return chunkSize;
    }

    @Override
    public @NotNull WorldBorder getWorldBorder() {
        /*
            Lazily create a custom world border on first access.
            The border is initialized with the current chunkSize value.
            super() calls getWorldBorder() during construction; at that point chunkSize=1
            (the default), which is fine — Dimensional calls setChunkSize() after level creation
            to apply the correct size.
        */
        if (this.customWorldBorder == null) {
            LOGGER.info("Initializing custom world border for dimension: {} (chunkSize={})",
                        this.dimension().location(), this.chunkSize);
            this.customWorldBorder = new DimensionalWorldBorder(this, this.chunkSize);
        }
        return this.customWorldBorder;
    }
}
