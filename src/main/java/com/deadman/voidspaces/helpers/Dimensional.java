package com.deadman.voidspaces.helpers;

import com.deadman.voidspaces.VoidSpaces;
import com.deadman.voidspaces.init.DataAttachments;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
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
    private static final Map<ResourceKey<Level>, Dimensional> WRAPPERS = new HashMap<>();
    private final MinecraftServer server;
    public final ResourceKey<Level> dimension;
    private final ServerLevel dimensionLevel;
    private final UUID owner;
    private WorldBorder cage;
    private Map<Player, BlockPos> returnPositions = new HashMap<>();
    private Map<Player, ResourceKey<Level>> returnDimensions = new HashMap<>();
    private BlockPos ownerReturnPosition;
    private ResourceKey<Level> ownerReturnDimension;
    private SpaceContents machine = new SpaceContents();
    private final Map<ServerPlayer, BorderChangeListener> borderListeners = new HashMap<>();

    public Dimensional(MinecraftServer server, UUID owner) {
        if (server == null || owner == null) {
            throw new IllegalArgumentException(String.format("Minecraft Server: {} || Owner: {} were null", server, owner));
        }
        this.server = server;
        this.dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(VoidSpaces.MODID, "voidspace_" + dimensionCount));
        ServerLevel infiniverseLevel = InfiniverseAPI.get().getOrCreateLevel(this.server, this.dimension, () -> createLevel(this.server, DimensionTypeOptions.FLAT));
        this.dimensionLevel = infiniverseLevel;
        LOGGER.info("New dimensional level type: {}", infiniverseLevel.getClass().getSimpleName());
        this.owner = owner;
        this.cage = new WorldBorder();
        this.cage.setCenter(0, 0);
        this.cage.setSize(10);
        this.cage.setDamagePerBlock(1);
        this.cage.setWarningBlocks(0);
        this.cage.setAbsoluteMaxSize((int) this.cage.getSize() + 1); // Prevent expansion
        dimensionCount++;
        WRAPPERS.put(this.dimension, this);
    }

    public Dimensional(MinecraftServer server, UUID owner, ResourceKey<Level> existingDimension) {
        this.server = server;
        this.dimension = ResourceKey.create(Registries.DIMENSION, existingDimension.location());
        ServerLevel infiniverseLevel = InfiniverseAPI.get().getOrCreateLevel(this.server, this.dimension, () -> createLevel(this.server, DimensionTypeOptions.FLAT));
        this.dimensionLevel = infiniverseLevel;
        LOGGER.info("Existing dimensional level type: {}", infiniverseLevel.getClass().getSimpleName());
        this.owner = owner;
        // Initialize the cage WorldBorder for existing dimensions too
        this.cage = new WorldBorder();
        this.cage.setCenter(0, 0);
        this.cage.setSize(10);
        this.cage.setDamagePerBlock(1);
        this.cage.setWarningBlocks(0);
        this.cage.setAbsoluteMaxSize((int) this.cage.getSize() + 1);
        WRAPPERS.put(this.dimension, this);
    }

    public static Dimensional getWrapper(ResourceKey<Level> key) {
        return WRAPPERS.get(key);
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
        
        // If we don't have a saved border (new dimension), create one
        if (this.cage == null) {
            // Save the current world border settings
            WorldBorder oldBorder = new WorldBorder();
            oldBorder.setCenter(this.dimensionLevel.getWorldBorder().getCenterX(), this.dimensionLevel.getWorldBorder().getCenterZ());
            oldBorder.setSize(this.dimensionLevel.getWorldBorder().getSize());
            oldBorder.setDamagePerBlock(this.dimensionLevel.getWorldBorder().getDamagePerBlock());
            oldBorder.setWarningBlocks(this.dimensionLevel.getWorldBorder().getWarningBlocks());
            this.cage = oldBorder;
        }
        
        // Set a small world border around the spawn area
        this.dimensionLevel.getWorldBorder().setCenter(0, 0);
        this.dimensionLevel.getWorldBorder().setSize(50); // 50x50 area
        this.dimensionLevel.getWorldBorder().setDamagePerBlock(0.2);
        this.dimensionLevel.getWorldBorder().setWarningBlocks(5);
        
        // Send border packets to all players in the dimension with a slight delay
        this.dimensionLevel.getServer().execute(() -> {
            for (ServerPlayer player : this.dimensionLevel.players()) {
                if (player.connection != null) {
                    // Send all border packets
                    player.connection.send(new ClientboundSetBorderCenterPacket(this.dimensionLevel.getWorldBorder()));
                    player.connection.send(new ClientboundSetBorderSizePacket(this.dimensionLevel.getWorldBorder()));
                    player.connection.send(new ClientboundSetBorderWarningDelayPacket(this.dimensionLevel.getWorldBorder()));
                    player.connection.send(new ClientboundSetBorderWarningDistancePacket(this.dimensionLevel.getWorldBorder()));
                    
                    // Also send initialization packet
                    player.connection.send(new ClientboundInitializeBorderPacket(this.dimensionLevel.getWorldBorder()));
                    
                    LOGGER.info("Sent world border packets to player: {}", player.getName().getString());
                }
            }
        });

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
        
        // Store the player's current position and dimension for return using Data Attachments
        DataAttachments.ReturnPositionData returnData = player.getData(DataAttachments.RETURN_POSITION);
        returnData.setReturnPosition(player.blockPosition());
        returnData.setReturnDimension(player.level().dimension());
        
        // Also store in memory maps for immediate use
        returnPositions.put(player, player.blockPosition());
        returnDimensions.put(player, player.level().dimension());
        
        // Also store as owner return position for persistence
        if (player.getUUID().equals(this.owner)) {
            this.ownerReturnPosition = player.blockPosition();
            this.ownerReturnDimension = player.level().dimension();
        }
        
        player.teleportTo(dimensionLevel, 0, -63, 0, null, player.getYRot(), player.getXRot());
        
        this.setWorldBorder(); //after because sync uses players in the dimension
        
        // Ensure world border is visible for the player
        this.ensureWorldBorderForPlayer(player);
        
        if (player.getUUID().equals(this.owner)) {
            this.setBuilderMode(player, true);
        } else {
            LOGGER.info("Teleported player is not the owner!");
        }
    }

    public void teleportOut(ServerPlayer player) {
        // Try to get return position from memory maps first, then from Data Attachments
        BlockPos returnPos = returnPositions.get(player);
        ResourceKey<Level> returnDimension = returnDimensions.get(player);
        
        if (returnPos == null || returnDimension == null) {
            // Try to get from Data Attachments (persistent storage)
            DataAttachments.ReturnPositionData returnData = player.getData(DataAttachments.RETURN_POSITION);
            if (returnData.hasReturnData()) {
                returnPos = returnData.getReturnPosition();
                returnDimension = returnData.getReturnDimension();
                // Restore to memory maps
                returnPositions.put(player, returnPos);
                returnDimensions.put(player, returnDimension);
                LOGGER.info("Restored return position from Data Attachments for player: {}", player.getName().getString());
            } else {
                throw new IllegalStateException("Player's return position or dimension is not saved!");
            }
        }
        ServerLevel returnLevel = server.getLevel(returnDimension);
        if (returnLevel == null) {
            LOGGER.warn("Return dimension not found, defaulting to overworld!");
            returnLevel = server.getLevel(Level.OVERWORLD);
            if (returnLevel == null) {
                throw new IllegalStateException("Overworld not loaded!");
            }
        }
        this.resetWorldBorder(); //We can put it here because we are removing the listeners
        
        // Use changeDimension instead of teleportTo to avoid respawn packet encoding issues
        if (returnLevel.dimension() != player.level().dimension()) {
            player.changeDimension(new net.minecraft.world.level.portal.DimensionTransition(
                returnLevel, 
                returnPos.getCenter(), 
                player.getDeltaMovement(), 
                player.getYRot(), 
                player.getXRot(),
                net.minecraft.world.level.portal.DimensionTransition.DO_NOTHING
            ));
        } else {
            // Same dimension, just teleport normally
            player.teleportTo(returnPos.getX(), returnPos.getY(), returnPos.getZ());
        }
        if (player.getUUID().equals(this.owner)) {
            this.setBuilderMode(player, false);
        } else {
            LOGGER.info("Teleported player is not the owner!");
        }
        this.machine = Space.extractContents(this.dimensionLevel, new ChunkPos(0, 0));
        returnPositions.remove(player);
        returnDimensions.remove(player);
        
        // Clear Data Attachments
        DataAttachments.ReturnPositionData returnData = player.getData(DataAttachments.RETURN_POSITION);
        returnData.clear();
    }

    public void restoreOwnerReturnPosition(ServerPlayer player) {
        if (player.getUUID().equals(this.owner)) {
            // First try from saved NBT data
            if (this.ownerReturnPosition != null && this.ownerReturnDimension != null) {
                // Only restore if not already set (to avoid overwriting current session data)
                if (!returnPositions.containsKey(player) || !returnDimensions.containsKey(player)) {
                    returnPositions.put(player, this.ownerReturnPosition);
                    returnDimensions.put(player, this.ownerReturnDimension);
                    
                    // Also update Data Attachments
                    DataAttachments.ReturnPositionData returnData = player.getData(DataAttachments.RETURN_POSITION);
                    returnData.setReturnPosition(this.ownerReturnPosition);
                    returnData.setReturnDimension(this.ownerReturnDimension);
                    
                    LOGGER.info("Restored owner return position: {} in dimension {}", this.ownerReturnPosition, this.ownerReturnDimension);
                }
            } else {
                // Try to restore from Data Attachments
                DataAttachments.ReturnPositionData returnData = player.getData(DataAttachments.RETURN_POSITION);
                if (returnData.hasReturnData()) {
                    returnPositions.put(player, returnData.getReturnPosition());
                    returnDimensions.put(player, returnData.getReturnDimension());
                    LOGGER.info("Restored return position from Data Attachments: {} in dimension {}", returnData.getReturnPosition(), returnData.getReturnDimension());
                }
            }
        }
        
        // Always ensure world border is properly set for any player in this dimension
        this.ensureWorldBorderForPlayer(player);
    }
    
    public void ensureWorldBorderForPlayer(ServerPlayer player) {
        // Make sure the world border is properly applied and synced for this player
        if (player.level() == this.dimensionLevel && player.connection != null) {
            this.dimensionLevel.getServer().execute(() -> {
                // Send all border packets to ensure visibility
                player.connection.send(new ClientboundInitializeBorderPacket(this.dimensionLevel.getWorldBorder()));
                player.connection.send(new ClientboundSetBorderCenterPacket(this.dimensionLevel.getWorldBorder()));
                player.connection.send(new ClientboundSetBorderSizePacket(this.dimensionLevel.getWorldBorder()));
                player.connection.send(new ClientboundSetBorderWarningDelayPacket(this.dimensionLevel.getWorldBorder()));
                player.connection.send(new ClientboundSetBorderWarningDistancePacket(this.dimensionLevel.getWorldBorder()));
                
                LOGGER.info("Ensured world border packets sent to player: {} (center: {}, {}, size: {})", 
                           player.getName().getString(),
                           this.dimensionLevel.getWorldBorder().getCenterX(),
                           this.dimensionLevel.getWorldBorder().getCenterZ(),
                           this.dimensionLevel.getWorldBorder().getSize());
            });
        }
    }

    public CompoundTag saveReturnData() {
        CompoundTag tag = new CompoundTag();
        if (this.ownerReturnPosition != null) {
            tag.putLong("ownerReturnPos", this.ownerReturnPosition.asLong());
        }
        if (this.ownerReturnDimension != null) {
            tag.putString("ownerReturnDim", this.ownerReturnDimension.location().toString());
        }
        
        // Save world border settings
        if (this.cage != null) {
            CompoundTag borderTag = new CompoundTag();
            borderTag.putDouble("centerX", this.cage.getCenterX());
            borderTag.putDouble("centerZ", this.cage.getCenterZ());
            borderTag.putDouble("size", this.cage.getSize());
            borderTag.putDouble("damagePerBlock", this.cage.getDamagePerBlock());
            borderTag.putInt("warningBlocks", this.cage.getWarningBlocks());
            tag.put("worldBorder", borderTag);
        }
        
        return tag;
    }

    public void loadReturnData(CompoundTag tag) {
        if (tag.contains("ownerReturnPos")) {
            this.ownerReturnPosition = BlockPos.of(tag.getLong("ownerReturnPos"));
        }
        if (tag.contains("ownerReturnDim")) {
            try {
                ResourceLocation dimLocation = ResourceLocation.parse(tag.getString("ownerReturnDim"));
                this.ownerReturnDimension = ResourceKey.create(Registries.DIMENSION, dimLocation);
            } catch (Exception e) {
                LOGGER.warn("Failed to parse owner return dimension: {}", tag.getString("ownerReturnDim"));
            }
        }
        
        // Load world border settings
        if (tag.contains("worldBorder")) {
            CompoundTag borderTag = tag.getCompound("worldBorder");
            if (this.cage == null) {
                this.cage = new WorldBorder();
            }
            this.cage.setCenter(borderTag.getDouble("centerX"), borderTag.getDouble("centerZ"));
            this.cage.setSize(borderTag.getDouble("size"));
            this.cage.setDamagePerBlock(borderTag.getDouble("damagePerBlock"));
            this.cage.setWarningBlocks(borderTag.getInt("warningBlocks"));
            
            // Also apply the world border to the actual dimension immediately
            this.dimensionLevel.getWorldBorder().setCenter(0, 0);
            this.dimensionLevel.getWorldBorder().setSize(50);
            this.dimensionLevel.getWorldBorder().setDamagePerBlock(0.2);
            this.dimensionLevel.getWorldBorder().setWarningBlocks(5);
            
            // Send border update packets to all players in this dimension
            for (ServerPlayer player : this.dimensionLevel.players()) {
                if (player.connection != null) {
                    player.connection.send(new ClientboundInitializeBorderPacket(this.dimensionLevel.getWorldBorder()));
                    player.connection.send(new ClientboundSetBorderCenterPacket(this.dimensionLevel.getWorldBorder()));
                    player.connection.send(new ClientboundSetBorderSizePacket(this.dimensionLevel.getWorldBorder()));
                }
            }
            
            LOGGER.info("Restored world border settings from NBT and applied to dimension");
        }
    }

    private void setBuilderMode(ServerPlayer player, boolean enabled) {
        if (enabled) {
            // Save the original game mode and inventory
            player.getPersistentData().putString("VoidSpaces_OriginalGameMode", player.gameMode.getGameModeForPlayer().getName());
            ListTag inventoryTag = player.getInventory().save(new ListTag());
            player.getPersistentData().put("VoidSpaces_SavedInventory", inventoryTag);
            
            // Clear inventory and set creative mode
            player.getInventory().clearContent();
            player.setGameMode(GameType.CREATIVE);
            player.getAbilities().instabuild = true;
            player.getAbilities().flying = true;
            player.getAbilities().invulnerable = true;
            player.getAbilities().mayBuild = true;
            
            // Prevent dimension travel
            player.getPersistentData().putBoolean("VoidSpaces_InDimension", true);
            
            LOGGER.info("Enabled builder mode for player: {}", player.getName().getString());
        } else {
            // Restore original game mode and inventory
            String originalGameMode = player.getPersistentData().getString("VoidSpaces_OriginalGameMode");
            GameType gameType = GameType.SURVIVAL; // Default fallback
            try {
                if (!originalGameMode.isEmpty()) {
                    gameType = GameType.byName(originalGameMode, GameType.SURVIVAL);
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to restore game mode: {}", originalGameMode);
            }
            
            player.setGameMode(gameType);
            player.getAbilities().instabuild = false;
            player.getAbilities().flying = false;
            player.getAbilities().invulnerable = false;
            
            // Clear any items obtained in the dimension
            player.getInventory().clearContent();
            
            // Restore saved inventory
            if (player.getPersistentData().contains("VoidSpaces_SavedInventory")) {
                ListTag inventoryTag = player.getPersistentData().getList("VoidSpaces_SavedInventory", 10);
                player.getInventory().load(inventoryTag);
                player.getPersistentData().remove("VoidSpaces_SavedInventory");
            }
            
            // Clear dimension flag
            player.getPersistentData().remove("VoidSpaces_InDimension");
            player.getPersistentData().remove("VoidSpaces_OriginalGameMode");
            
            LOGGER.info("Disabled builder mode for player: {}", player.getName().getString());
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