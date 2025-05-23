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
import java.lang.reflect.Field;

public class Dimensional {
    private static final Logger LOGGER = LoggerFactory.getLogger(Dimensional.class);
    private static int dimensionCount = 0;
    private static final Map<ResourceKey<Level>, Dimensional> WRAPPERS = new HashMap<>();
    private final MinecraftServer server;
    public final ResourceKey<Level> dimension;
    private final ServerLevel dimensionLevel;
    private final UUID owner;
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
        LOGGER.info("New dimensional level type: {} for dimension: {}", infiniverseLevel.getClass().getSimpleName(), this.dimension.location());
        
        // If this is a regular ServerLevel instead of DimensionalLevel, apply workaround
        if (!(infiniverseLevel instanceof DimensionalLevel)) {
            LOGGER.warn("New dimension {} created as regular ServerLevel instead of DimensionalLevel - applying world border workaround", this.dimension.location());
            this.applyWorldBorderWorkaround(infiniverseLevel);
        }
        
        // Immediately test the world border
        WorldBorder testBorder = infiniverseLevel.getWorldBorder();
        LOGGER.info("World border after creation - Type: {}, Center: ({}, {}), Size: {}", 
                   testBorder.getClass().getSimpleName(), 
                   testBorder.getCenterX(), 
                   testBorder.getCenterZ(), 
                   testBorder.getSize());
        this.owner = owner;
        dimensionCount++;
        WRAPPERS.put(this.dimension, this);
    }

    public Dimensional(MinecraftServer server, UUID owner, ResourceKey<Level> existingDimension) {
        this.server = server;
        this.dimension = ResourceKey.create(Registries.DIMENSION, existingDimension.location());
        ServerLevel infiniverseLevel = InfiniverseAPI.get().getOrCreateLevel(this.server, this.dimension, () -> createLevel(this.server, DimensionTypeOptions.FLAT));
        this.dimensionLevel = infiniverseLevel;
        LOGGER.info("Existing dimensional level type: {} for dimension: {}", infiniverseLevel.getClass().getSimpleName(), this.dimension.location());
        
        // If this is a regular ServerLevel from save loading, we need to work around it
        if (!(infiniverseLevel instanceof DimensionalLevel)) {
            LOGGER.warn("Dimension {} loaded as regular ServerLevel instead of DimensionalLevel - applying world border workaround", this.dimension.location());
            // We can't replace the level, but we can override its world border behavior
            this.applyWorldBorderWorkaround(infiniverseLevel);
        }
        
        // Immediately test the world border
        WorldBorder testBorder = infiniverseLevel.getWorldBorder();
        LOGGER.info("World border after existing creation - Type: {}, Center: ({}, {}), Size: {}", 
                   testBorder.getClass().getSimpleName(), 
                   testBorder.getCenterX(), 
                   testBorder.getCenterZ(), 
                   testBorder.getSize());
        this.owner = owner;
        WRAPPERS.put(this.dimension, this);
    }

    public static Dimensional getWrapper(ResourceKey<Level> key) {
        return WRAPPERS.get(key);
    }
    
    public static void cleanupAllForSave() {
        LOGGER.info("Cleaning up {} dimensional wrappers before save", WRAPPERS.size());
        for (Dimensional wrapper : WRAPPERS.values()) {
            wrapper.cleanupForSave();
        }
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

    private void ensureWorldBorderSynced() {
        // Send initialization packets to all players in the dimension
        // The actual world border is handled inherently by DimensionalLevel.getWorldBorder()
        this.dimensionLevel.getServer().execute(() -> {
            for (ServerPlayer player : this.dimensionLevel.players()) {
                if (player.connection != null) {
                    // Send initialization packet to ensure client sync
                    player.connection.send(new ClientboundInitializeBorderPacket(this.dimensionLevel.getWorldBorder()));
                    LOGGER.info("Synced inherent world border for player: {}", player.getName().getString());
                }
            }
        });
        LOGGER.info("World border synced for dimension: center={}, size={}", this.dimensionLevel.getWorldBorder().getCenterX(), this.dimensionLevel.getWorldBorder().getSize());
    }

    // World border is now inherent to DimensionalLevel - no manual reset needed

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
        
        // Sync the inherent world border with the player
        this.ensureWorldBorderForPlayer(player);
        
        if (player.getUUID().equals(this.owner)) {
            this.setBuilderMode(player, true);
        } else {
            LOGGER.info("Teleported player is not the owner!");
        }
    }

    public void teleportOut(ServerPlayer player) {
        // Remove border listener first to prevent save issues
        if (borderListeners.containsKey(player)) {
            this.dimensionLevel.getWorldBorder().removeListener(borderListeners.get(player));
            borderListeners.remove(player);
            LOGGER.info("Removed border change listener for player: {}", player.getName().getString());
        }
        
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
            } else if (player.getUUID().equals(this.owner) && this.ownerReturnPosition != null && this.ownerReturnDimension != null) {
                // Try owner return position from NBT
                returnPos = this.ownerReturnPosition;
                returnDimension = this.ownerReturnDimension;
                LOGGER.info("Using owner return position from NBT for player: {}", player.getName().getString());
            } else {
                // Fallback to overworld spawn
                LOGGER.warn("No return position found for player: {}, defaulting to overworld spawn", player.getName().getString());
                returnPos = new BlockPos(0, 64, 0);
                returnDimension = Level.OVERWORLD;
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
        // No manual world border reset needed - handled inherently by DimensionalLevel
        
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
        // Always ensure world border is properly synced for any player in this dimension
        this.ensureWorldBorderForPlayer(player);
        
        if (player.getUUID().equals(this.owner)) {
            // Check if we already have return data for this player
            DataAttachments.ReturnPositionData returnData = player.getData(DataAttachments.RETURN_POSITION);
            
            if (!returnData.hasReturnData() && this.ownerReturnPosition != null && this.ownerReturnDimension != null) {
                // Restore from NBT data if Data Attachments are empty
                returnPositions.put(player, this.ownerReturnPosition);
                returnDimensions.put(player, this.ownerReturnDimension);
                
                // Also update Data Attachments for consistency
                returnData.setReturnPosition(this.ownerReturnPosition);
                returnData.setReturnDimension(this.ownerReturnDimension);
                
                LOGGER.info("Restored owner return position from NBT: {} in dimension {}", this.ownerReturnPosition, this.ownerReturnDimension);
            } else if (returnData.hasReturnData()) {
                // Restore from Data Attachments
                returnPositions.put(player, returnData.getReturnPosition());
                returnDimensions.put(player, returnData.getReturnDimension());
                LOGGER.info("Restored return position from Data Attachments: {} in dimension {}", returnData.getReturnPosition(), returnData.getReturnDimension());
            } else {
                LOGGER.warn("No return position data available for owner: {}", player.getName().getString());
            }
        }
    }
    
    public void ensureWorldBorderForPlayer(ServerPlayer player) {
        // Make sure the inherent world border is synced for this specific player
        if (player.level() == this.dimensionLevel && player.connection != null) {
            // Force the world border to be initialized by accessing it
            WorldBorder border = this.dimensionLevel.getWorldBorder();
            
            LOGGER.info("Starting world border sync for player: {} (dimension type: {}, border center: {}, {}, size: {}, damage: {}, warning: {})", 
                       player.getName().getString(),
                       this.dimensionLevel.getClass().getSimpleName(),
                       border.getCenterX(),
                       border.getCenterZ(),
                       border.getSize(),
                       border.getDamagePerBlock(),
                       border.getWarningBlocks());
            
            // Add a border change listener to track when border changes are detected
            this.addBorderListenerForPlayer(player);
            
            // Send the border packets with proper timing (player should be fully loaded at this point)
            this.sendBorderPacketsToPlayer(player, border, 0);
        }
    }
    
    private void sendBorderPacketsToPlayer(ServerPlayer player, WorldBorder border, int delay) {
        LOGGER.info("Sending world border packets to player: {} at delay {} ticks", player.getName().getString(), delay);
        
        try {
            player.connection.send(new ClientboundInitializeBorderPacket(border));
            LOGGER.info("Sent ClientboundInitializeBorderPacket to {}", player.getName().getString());
            
            player.connection.send(new ClientboundSetBorderCenterPacket(border));
            LOGGER.info("Sent ClientboundSetBorderCenterPacket to {} (center: {}, {})", 
                       player.getName().getString(), border.getCenterX(), border.getCenterZ());
            
            player.connection.send(new ClientboundSetBorderSizePacket(border));
            LOGGER.info("Sent ClientboundSetBorderSizePacket to {} (size: {})", 
                       player.getName().getString(), border.getSize());
            
            player.connection.send(new ClientboundSetBorderWarningDelayPacket(border));
            LOGGER.info("Sent ClientboundSetBorderWarningDelayPacket to {}", player.getName().getString());
            
            player.connection.send(new ClientboundSetBorderWarningDistancePacket(border));
            LOGGER.info("Sent ClientboundSetBorderWarningDistancePacket to {} (warning blocks: {})", 
                       player.getName().getString(), border.getWarningBlocks());
            
        } catch (Exception e) {
            LOGGER.error("Failed to send world border packets to player: {}", player.getName().getString(), e);
        }
    }
    
    private void addBorderListenerForPlayer(ServerPlayer player) {
        // Remove existing listener if any
        if (borderListeners.containsKey(player)) {
            this.dimensionLevel.getWorldBorder().removeListener(borderListeners.get(player));
        }
        
        BorderChangeListener listener = new BorderChangeListener() {
            @Override
            public void onBorderSizeSet(WorldBorder border, double size) {
                LOGGER.info("Border size changed for player {}: {}", player.getName().getString(), size);
            }

            @Override
            public void onBorderSizeLerping(WorldBorder border, double oldSize, double newSize, long time) {
                LOGGER.info("Border size lerping for player {}: {} -> {} over {} ticks", 
                           player.getName().getString(), oldSize, newSize, time);
            }

            @Override
            public void onBorderCenterSet(WorldBorder border, double x, double z) {
                LOGGER.info("Border center changed for player {}: {}, {}", player.getName().getString(), x, z);
            }

            @Override
            public void onBorderSetWarningTime(WorldBorder border, int warningTime) {
                LOGGER.info("Border warning time changed for player {}: {}", player.getName().getString(), warningTime);
            }

            @Override
            public void onBorderSetWarningBlocks(WorldBorder border, int warningBlocks) {
                LOGGER.info("Border warning blocks changed for player {}: {}", player.getName().getString(), warningBlocks);
            }

            @Override
            public void onBorderSetDamagePerBlock(WorldBorder border, double damagePerBlock) {
                LOGGER.info("Border damage per block changed for player {}: {}", player.getName().getString(), damagePerBlock);
            }

            @Override
            public void onBorderSetDamageSafeZOne(WorldBorder border, double safeZone) {
                LOGGER.info("Border damage safe zone changed for player {}: {}", player.getName().getString(), safeZone);
            }
        };

        this.dimensionLevel.getWorldBorder().addListener(listener);
        borderListeners.put(player, listener);
        LOGGER.info("Added border change listener for player: {}", player.getName().getString());
    }

    public CompoundTag saveReturnData() {
        CompoundTag tag = new CompoundTag();
        if (this.ownerReturnPosition != null) {
            tag.putLong("ownerReturnPos", this.ownerReturnPosition.asLong());
        }
        if (this.ownerReturnDimension != null) {
            tag.putString("ownerReturnDim", this.ownerReturnDimension.location().toString());
        }
        
        // World border is now inherent to DimensionalLevel - no need to save
        
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
        
        // World border is now inherent to DimensionalLevel - no need to load
        // Ensure any players currently in this dimension see the inherent world border with proper delay
        for (ServerPlayer player : this.dimensionLevel.players()) {
            if (player.connection != null) {
                // Use delayed sync to ensure client is ready
                this.ensureWorldBorderForPlayer(player);
            }
        }
        LOGGER.info("Scheduled world border sync for all players in dimension");
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
    
    public void cleanupForSave() {
        // Remove all border listeners to prevent save hang
        for (Map.Entry<ServerPlayer, BorderChangeListener> entry : borderListeners.entrySet()) {
            try {
                this.dimensionLevel.getWorldBorder().removeListener(entry.getValue());
                LOGGER.info("Removed border listener for player {} during save cleanup", entry.getKey().getName().getString());
            } catch (Exception e) {
                LOGGER.warn("Failed to remove border listener for player {} during save cleanup", entry.getKey().getName().getString(), e);
            }
        }
        borderListeners.clear();
        
        // Force save the dimension before world save
        try {
            this.dimensionLevel.save(null, false, false);
            LOGGER.info("Force saved void dimension {} before world save", this.dimension.location());
        } catch (Exception e) {
            LOGGER.warn("Failed to force save void dimension {} before world save", this.dimension.location(), e);
        }
    }

    // Border sync is now handled inherently by DimensionalLevel
    
    private void applyWorldBorderWorkaround(ServerLevel level) {
        try {
            LOGGER.info("Applying world border workaround for dimension: {}", level.dimension().location());
            
            // Create our custom world border
            DimensionalWorldBorder customBorder = new DimensionalWorldBorder(level);
            
            // Set appropriate values
            customBorder.setCenter(0.0, 0.0);
            customBorder.setSize(50.0);
            customBorder.setDamagePerBlock(0.2);
            customBorder.setWarningBlocks(5);
            customBorder.setAbsoluteMaxSize(51);
            
            // Use reflection to replace the worldBorder field in ServerLevel
            Field worldBorderField = null;
            Class<?> currentClass = level.getClass();
            
            // Search through the class hierarchy for the worldBorder field
            while (currentClass != null && worldBorderField == null) {
                try {
                    worldBorderField = currentClass.getDeclaredField("worldBorder");
                } catch (NoSuchFieldException e) {
                    // Try parent class
                    currentClass = currentClass.getSuperclass();
                }
            }
            
            if (worldBorderField != null) {
                worldBorderField.setAccessible(true);
                WorldBorder oldBorder = (WorldBorder) worldBorderField.get(level);
                LOGGER.info("Found worldBorder field, replacing {} with {}", 
                           oldBorder.getClass().getSimpleName(), 
                           customBorder.getClass().getSimpleName());
                
                worldBorderField.set(level, customBorder);
                
                // Verify the replacement worked
                WorldBorder newBorder = level.getWorldBorder();
                LOGGER.info("World border replacement result - Type: {}, Size: {}", 
                           newBorder.getClass().getSimpleName(), 
                           newBorder.getSize());
            } else {
                LOGGER.error("Could not find worldBorder field in ServerLevel class hierarchy");
            }
            
        } catch (Exception e) {
            LOGGER.error("Failed to apply world border workaround", e);
        }
    }
}