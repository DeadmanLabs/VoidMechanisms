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
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;
import net.minecraft.world.level.storage.ServerLevelData;


import java.util.List;
import java.util.concurrent.Executor;

import com.deadman.voidspaces.helpers.DimensionalWorldBorder;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DimensionalLevel extends ServerLevel {
    private static final Logger LOGGER = LoggerFactory.getLogger(DimensionalLevel.class);
    private DimensionalWorldBorder customWorldBorder = null; //has to be null before super is called, then intialized on first access that comes from super()
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

    @Override
    public @NotNull WorldBorder getWorldBorder() {
        /*
            So this is a bit stupid, so bare with me. When creating a world border specifically for a dimension, we have to override the existing (System Wide) world border.
            We do this by overloading the world border return method (this) inside of an extension ontop of the ServerLevel class. We then initialize the world border using
            the default system one, but then we can modify it without moving the borders in any other dimensions. Its important to note that we can ONLY modify the borders
            of dimensions that were created using voidspaces, NOT the dimensions of any other mod INCLUDING infiniverse (because voidspaces has its own copy of infiniverse
            inside due to modifications to the base code). We then cannot simply create the custom border in the constructor, because the super() calls for a world border
            before it can be intialized. This is why we create the world border on the first access, because then it gets created when super() calls, and super gets the
            custom border.
        */
        if (this.customWorldBorder == null) {
            LOGGER.info("Initializing custom world border for dimension: {}", this.dimension().location());
            this.customWorldBorder = new DimensionalWorldBorder(this);
            ServerLevel overworld = this.getServer().getLevel(Level.OVERWORLD);
            WorldBorder defaultBorder = overworld.getWorldBorder();
            // Set up appropriate world border for voidspace dimensions
            this.customWorldBorder.setCenter(0.0, 0.0);
            this.customWorldBorder.setSize(50.0); // 50x50 block area for building
            this.customWorldBorder.setDamagePerBlock(0.2);
            this.customWorldBorder.setWarningBlocks(5);
            this.customWorldBorder.setAbsoluteMaxSize(51); // Prevent expansion beyond 51
            LOGGER.info("Custom world border initialized: center=(0,0), size=50, damage=0.2, warning=5");
        }
        return this.customWorldBorder;
    }
}
