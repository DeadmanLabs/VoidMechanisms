package com.deadman.voidspaces.helpers;

import com.deadman.voidspaces.VoidSpaces;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;

import java.util.Optional;

import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorPresets;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.deadman.voidspaces.infiniverse.api.*;

public class Dimensional {
    private static final Logger LOGGER = LoggerFactory.getLogger(Dimensional.class);
    private static int dimensionCount = 0;
    private final MinecraftServer server;
    private final ResourceKey<Level> dimension;
    private final ServerLevel dimensionLevel;
    public Dimensional(MinecraftServer server) {
        this.server = server;
        this.dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(VoidSpaces.MODID, "voidspace_" + dimensionCount));
        this.dimensionLevel = InfiniverseAPI.get().getOrCreateLevel(this.server, this.dimension, () -> createLevel(this.server));
        dimensionCount++;
    }
    private LevelStem createLevel(MinecraftServer server) {
        ServerLevel oldLevel = server.overworld();
        DynamicOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, server.registryAccess());
        ChunkGenerator oldChunkGenerator = oldLevel.getChunkSource().getGenerator();
        ChunkGenerator newChunkGenerator = ChunkGenerator.CODEC.encodeStart(ops, oldChunkGenerator)
                .flatMap(nbt -> ChunkGenerator.CODEC.parse(ops, nbt))
                .getOrThrow(s -> new RuntimeException(String.format("Error copying dimension: {}", s)));
        Holder<DimensionType> typeHolder = oldLevel.dimensionTypeRegistration();
        return new LevelStem(typeHolder, newChunkGenerator);
    }
}