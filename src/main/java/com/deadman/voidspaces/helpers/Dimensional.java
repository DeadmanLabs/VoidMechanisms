package com.deadman.voidspaces.helpers;

import com.deadman.voidspaces.VoidSpaces;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.*;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.border.WorldBorder;
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

import java.util.*;

import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorPresets;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.deadman.voidspaces.infiniverse.api.*;
import com.deadman.voidspaces.helpers.Space;
import com.deadman.voidspaces.helpers.Space.SpaceContents;

public class Dimensional {
    private static final Logger LOGGER = LoggerFactory.getLogger(Dimensional.class);
    private static int dimensionCount = 0;
    private final MinecraftServer server;
    public final ResourceKey<Level> dimension;
    private final DimensionalLevel dimensionLevel;
    private final UUID owner;
    private WorldBorder cage;
    private Map<Player, BlockPos> returnPositions = new HashMap<>();
    private SpaceContents machine = new SpaceContents();
    private final Map<ServerPlayer, BorderChangeListener> borderListeners = new HashMap<>();

    public Dimensional(MinecraftServer server, UUID owner) {
        if (server == null || owner == null) {
            throw new IllegalArgumentException(String.format("Minecraft Server: {} || Owner: {} were null", server, owner));
        }
        this.server = server;
        this.dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(VoidSpaces.MODID, "voidspace_" + dimensionCount));
        ServerLevel infiniverseLevel = InfiniverseAPI.get().getOrCreateLevel(this.server, this.dimension, () -> createLevel(this.server, DimensionTypeOptions.FLAT));
        if (infiniverseLevel instanceof DimensionalLevel dimensionalLevel) {
            this.dimensionLevel = dimensionalLevel;
        } else {
            throw new IllegalStateException("Created dimension is not of DimensionalLevel type, instead is ServerLevel");
        }
        this.owner = owner;
        this.cage = new WorldBorder();
        this.cage.setCenter(0, 0);
        this.cage.setSize(10);
        this.cage.setDamagePerBlock(1);
        this.cage.setWarningBlocks(0);
        this.cage.setAbsoluteMaxSize((int) this.cage.getSize() + 1); // Prevent expansion
        dimensionCount++;
    }

    public Dimensional(MinecraftServer server, UUID owner, ResourceKey<Level> existingDimension) {
        this.server = server;
        this.dimension = ResourceKey.create(Registries.DIMENSION, existingDimension.location());
        ServerLevel infiniverseLevel = InfiniverseAPI.get().getOrCreateLevel(this.server, this.dimension, () -> createLevel(this.server, DimensionTypeOptions.FLAT));
        if (infiniverseLevel instanceof DimensionalLevel dimensionalLevel) {
            this.dimensionLevel = dimensionalLevel;
        } else {
            throw new IllegalStateException("Created dimension is not of DimensionalLevel type, instead is ServerLevel");
        }
        this.owner = owner;
    }

    private LevelStem createLevel(MinecraftServer server, DimensionTypeOptions typeOption) {
        DynamicOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, server.registryAccess());
        Holder<DimensionType> typeHolder;
        ChunkGenerator chunkGenerator;
        switch (typeOption) {
            case OVERWORLD:
                ServerLevel overworld = server.overworld();
                typeHolder = overworld.dimensionTypeRegistration();
                chunkGenerator = copyChunkGenerator(overworld, ops);
                break;

            case NETHER:
                ServerLevel nether = server.getLevel(Level.NETHER);
                if (nether == null) throw new RuntimeException("Nether dimension is not available!");
                typeHolder = nether.dimensionTypeRegistration();
                chunkGenerator = copyChunkGenerator(nether, ops);
                break;

            case END:
                ServerLevel end = server.getLevel(Level.END);
                if (end == null) throw new RuntimeException("End dimension is not available!");
                typeHolder = end.dimensionTypeRegistration();
                chunkGenerator = copyChunkGenerator(end, ops);
                break;

            case SINGLE:
                Holder<Biome> singleBiome = server.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.DESERT);
                BiomeSource biomeSource = new FixedBiomeSource(singleBiome);
                Holder<NoiseGeneratorSettings> noiseSettings = server.registryAccess().registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);

                typeHolder = server.registryAccess().registryOrThrow(Registries.DIMENSION_TYPE).getHolderOrThrow(BuiltinDimensionTypes.OVERWORLD);
                chunkGenerator = new NoiseBasedChunkGenerator(
                        biomeSource,
                        noiseSettings
                );
                break;

            case FLAT:
                FlatLevelGeneratorSettings flatSettings = new FlatLevelGeneratorSettings(
                        Optional.empty(),
                        server.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.THE_VOID),
                        List.of()
                );
                flatSettings.getLayers().clear();
                flatSettings.getLayersInfo().clear();
                flatSettings.getLayersInfo().addFirst(new FlatLayerInfo(1, Blocks.BEDROCK));
                flatSettings.getLayers().addFirst(Blocks.BEDROCK.defaultBlockState());
                FlatLevelSource levelSource = new FlatLevelSource(flatSettings);
                typeHolder = server.registryAccess().registryOrThrow(Registries.DIMENSION_TYPE).getHolderOrThrow(BuiltinDimensionTypes.OVERWORLD);
                chunkGenerator = (ChunkGenerator)levelSource;
                break;

            default:
                throw new IllegalArgumentException("Unknown Dimension Type Option: " + typeOption);
        }
        return new LevelStem(typeHolder, chunkGenerator);
    }

    private ChunkGenerator copyChunkGenerator(ServerLevel level, DynamicOps<Tag> ops) {
        ChunkGenerator oldChunkGenerator = level.getChunkSource().getGenerator();
        return ChunkGenerator.CODEC.encodeStart(ops, oldChunkGenerator)
                .flatMap(nbt -> ChunkGenerator.CODEC.parse(ops, nbt))
                .getOrThrow(s -> new RuntimeException(String.format("Error copying chunk generator: %s", s)));
    }

    private enum DimensionTypeOptions {
        OVERWORLD,
        NETHER,
        END,
        SINGLE,
        FLAT
    }

    private void setWorldBorder() {
        this.beginBorderSync();
        // Save the current world border settings
        WorldBorder oldBorder = new WorldBorder();
        oldBorder.setCenter(this.dimensionLevel.getWorldBorder().getCenterX(), this.dimensionLevel.getWorldBorder().getCenterZ());
        oldBorder.setSize(this.dimensionLevel.getWorldBorder().getSize());
        oldBorder.setDamagePerBlock(this.dimensionLevel.getWorldBorder().getDamagePerBlock());
        oldBorder.setWarningBlocks(this.dimensionLevel.getWorldBorder().getWarningBlocks());

        // Apply the cage settings to the dimension's world border
        this.dimensionLevel.getWorldBorder().setCenter(this.cage.getCenterX(), this.cage.getCenterZ());
        this.dimensionLevel.getWorldBorder().setSize(this.cage.getSize());
        this.dimensionLevel.getWorldBorder().setDamagePerBlock(this.cage.getDamagePerBlock());
        this.dimensionLevel.getWorldBorder().setWarningBlocks(this.cage.getWarningBlocks());

        // Enable strict boundary enforcement
        this.dimensionLevel.getWorldBorder().setAbsoluteMaxSize((int) this.cage.getSize() + 1); // Prevent expansion

        // Save the old border settings to the cage
        this.cage = oldBorder;

        LOGGER.info("World border set for dimension: center={}, size={}", this.dimensionLevel.getWorldBorder().getCenterX(), this.dimensionLevel.getWorldBorder().getSize());
    }

    private void resetWorldBorder() {
        // Restore the dimension's world border to the saved settings
        this.dimensionLevel.getWorldBorder().setCenter(this.cage.getCenterX(), this.cage.getCenterZ());
        this.dimensionLevel.getWorldBorder().setSize(this.cage.getSize());
        this.dimensionLevel.getWorldBorder().setDamagePerBlock(this.cage.getDamagePerBlock());
        this.dimensionLevel.getWorldBorder().setWarningBlocks(this.cage.getWarningBlocks());

        // Reset the cage to default settings
        this.cage = new WorldBorder();
        this.cage.setCenter(0, 0);
        this.cage.setSize(10); // Default size
        this.cage.setDamagePerBlock(1);
        this.cage.setWarningBlocks(0);

        //Stop listening
        this.endBorderSync();

        LOGGER.info("World border reset for dimension: center={}, size={}", this.dimensionLevel.getWorldBorder().getCenterX(), this.dimensionLevel.getWorldBorder().getSize());
    }

    public void teleportIn(ServerPlayer player) {
        ServerLevel dimensionLevel = server.getLevel(this.dimension);
        if (dimensionLevel == null) {
            throw new IllegalStateException("Custom dimension not loaded!");
        }
        returnPositions.put(player, player.blockPosition());
        player.teleportTo(dimensionLevel, 0, -63, 0, null, player.getYRot(), player.getXRot());
        this.setWorldBorder(); //after because sync uses players in the dimension
        if (player.getUUID() == this.owner) {
            this.setBuilderMode(player, true);
        } else {
            LOGGER.info("Teleported player is not the owner!");
        }
    }

    public void teleportOut(ServerPlayer player) {
        if (!returnPositions.containsKey(player)) {
            throw new IllegalStateException("Player's return position is not saved!");
        }
        BlockPos returnPos = returnPositions.get(player);
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException("Overworld not loaded!");
        }
        this.resetWorldBorder(); //We can put it here because we are removing the listeners
        player.teleportTo(overworld, returnPos.getX(), returnPos.getY(), returnPos.getZ(), null, player.getYRot(), player.getXRot());
        if (player.getUUID() == this.owner) {
            this.setBuilderMode(player, false);
        } else {
            LOGGER.info("Teleported player is not the owner!");
        }
        this.machine = Space.extractContents(this.dimensionLevel, new ChunkPos(0, 0));
        returnPositions.remove(player);
    }

    private void setBuilderMode(ServerPlayer player, boolean enabled) {
        if (enabled) {
            ListTag inventoryTag = player.getInventory().save(new ListTag());
            player.getPersistentData().put("RestrictedInventory", inventoryTag);
            player.getInventory().clearContent();
            player.setGameMode(GameType.CREATIVE);
            player.getAbilities().instabuild = true;
            player.getAbilities().flying = true;
            player.getAbilities().invulnerable = true;
        } else {
            player.setGameMode(GameType.SURVIVAL);
            player.getAbilities().instabuild = false;
            player.getAbilities().flying = false;
            player.getAbilities().invulnerable = false;
            if (player.getPersistentData().contains("RestrictedInventory")) {
                ListTag inventoryTag = player.getPersistentData().getList("RestrictedInventory", 10);
                player.getInventory().load(inventoryTag);
            }
        }
        player.onUpdateAbilities();
    }

    public void clear() {
        ChunkPos root = new ChunkPos(0, 0);
        int minX = root.getMinBlockX();
        int maxX = root.getMaxBlockX();
        int minZ = root.getMinBlockZ();
        int maxZ = root.getMaxBlockZ();
        int minY = this.dimensionLevel.getMinBuildHeight();
        int maxY = this.dimensionLevel.getMaxBuildHeight();

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y < maxY; y++) {
                    mutablePos.set(x, y, z);

                    if (y == minY) {
                        this.dimensionLevel.setBlock(mutablePos, Blocks.BEDROCK.defaultBlockState(), 3);
                    } else {
                        this.dimensionLevel.setBlock(mutablePos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
        LevelChunk chunk = this.dimensionLevel.getChunk(root.x, root.z);
        chunk.getBlockEntities().keySet().forEach(this.dimensionLevel::removeBlockEntity);
        AABB chunkBoundingBox = new AABB(root.getMinBlockX(), minY, root.getMinBlockZ(), root.getMaxBlockX() + 1, maxY, root.getMaxBlockZ() + 1);
        this.dimensionLevel.getEntities().get(chunkBoundingBox, entity -> entity.remove(Entity.RemovalReason.DISCARDED));
        this.machine = new SpaceContents();
        chunk.setUnsaved(true);
    }

    private void beginBorderSync() {
        for (ServerPlayer player : this.dimensionLevel.players()) {
            if (borderListeners.containsKey(player)) {
                this.dimensionLevel.getWorldBorder().removeListener(borderListeners.get(player));
            }
            BorderChangeListener listener = new BorderChangeListener() {
               @Override
               public void onBorderSizeSet(WorldBorder border, double size) {
                   player.connection.send(new ClientboundSetBorderSizePacket(border));
               }

               @Override
                public void onBorderSizeLerping(WorldBorder border, double oldSize, double newSize, long time) {
                   player.connection.send(new ClientboundSetBorderLerpSizePacket(border));
               }

               @Override
                public void onBorderCenterSet(WorldBorder border, double x, double z) {
                   player.connection.send(new ClientboundSetBorderCenterPacket(border));
               }

               @Override
                public void onBorderSetWarningTime(WorldBorder border, int warningTime) {
                   player.connection.send(new ClientboundSetBorderWarningDelayPacket(border));
               }

               @Override
                public void onBorderSetWarningBlocks(WorldBorder border, int warningBlocks) {
                   player.connection.send(new ClientboundSetBorderWarningDistancePacket(border));
               }

               @Override
                public void onBorderSetDamagePerBlock(WorldBorder border, double damagePerBlock) {

               }

               @Override
                public void onBorderSetDamageSafeZOne(WorldBorder border, double safeZone) {

               }
            };

            this.dimensionLevel.getWorldBorder().addListener(listener);
            borderListeners.put(player, listener);
        }
    }

    private void endBorderSync() {
        for (Map.Entry<ServerPlayer, BorderChangeListener> entry : borderListeners.entrySet()) {
            this.dimensionLevel.getWorldBorder().removeListener(entry.getValue());
        }
        borderListeners.clear();
    }
}