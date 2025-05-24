package com.deadman.voidspaces.helpers;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FluidState;

import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class Space {
    public static final Logger LOGGER = LoggerFactory.getLogger(Space.class);
    public static class SpaceContents {
        public Map<BlockPos, CompoundTag> blocks = new HashMap<>();
        public Map<BlockPos, CompoundTag> blockEntities = new HashMap<>();
        public Map<Entity, CompoundTag> entities = new HashMap<>();
    }

    public static SpaceContents extractContents(ServerLevel level, ChunkPos chunkPos) {
        SpaceContents contents = new SpaceContents();

        LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);

        // Ensure we scan the ENTIRE chunk from build limit to bottom, excluding bedrock layer at the bottom
        int minY = level.getMinBuildHeight() + 1; // +1 to skip the bedrock layer at the bottom
        int maxY = level.getMaxBuildHeight() - 1; // -1 because we use < in the loop
        
        LOGGER.info("Scanning chunk {} from Y={} to Y={} (full range excluding bottom bedrock)", chunkPos, minY, maxY + 1);

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        // For chunk 0, we want to scan coordinates (0,0) to (15,15), not the ChunkPos methods which return wrong values
        int chunkMinX = chunkPos.x * 16;
        int chunkMaxX = chunkMinX + 15;
        int chunkMinZ = chunkPos.z * 16;
        int chunkMaxZ = chunkMinZ + 15;
        
        LOGGER.info("Scanning chunk {} coordinates: X({} to {}), Z({} to {})", chunkPos, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ);
        
        for (int x = chunkMinX; x <= chunkMaxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = chunkMinZ; z <= chunkMaxZ; z++) {
                    mutablePos.set(x, y, z);
                    BlockState blockState = chunk.getBlockState(mutablePos);
                    if (blockState.isAir()) {
                        continue;
                    }
                    // Don't skip bedrock above the bottom layer - it might be placed by the player
                    CompoundTag blockTag = new CompoundTag();
                    NbtOps nbtOps = NbtOps.INSTANCE;
                    DataResult<Tag> result = BlockState.CODEC.encodeStart(nbtOps, blockState);
                    result.resultOrPartial(error -> {
                        throw new IllegalStateException("Failed to serialize BlockState: " + error);
                    }).ifPresent(serializedTag -> blockTag.put("BlockState", serializedTag));
                    
                    // Add fluid state information
                    FluidState fluidState = blockState.getFluidState();
                    if (!fluidState.isEmpty()) {
                        blockTag.putBoolean("IsFluid", true);
                        blockTag.putBoolean("IsSource", fluidState.isSource());
                        blockTag.putString("FluidType", fluidState.getType().toString());
                    }
                    
                    contents.blocks.put(mutablePos.immutable(), blockTag);
                    BlockEntity blockEntity = chunk.getBlockEntity(mutablePos);
                    if (blockEntity != null) {
                        CompoundTag blockEntityTag = blockEntity.saveWithFullMetadata(level.registryAccess());
                        contents.blockEntities.put(mutablePos.immutable(), blockEntityTag);
                    }
                }
            }
        }

        // Only get entities that are within the specified chunk bounds
        int minX = chunkPos.x * 16;
        int maxX = minX + 16; // +16 because AABB max is exclusive and chunk is 16 blocks wide
        int minZ = chunkPos.z * 16;
        int maxZ = minZ + 16; // +16 because AABB max is exclusive and chunk is 16 blocks wide
        
        net.minecraft.world.phys.AABB chunkBounds = new net.minecraft.world.phys.AABB(
            minX, level.getMinBuildHeight(), minZ, 
            maxX, level.getMaxBuildHeight(), maxZ
        );
        
        level.getEntities().get(chunkBounds, (entity) -> {
            CompoundTag entityTag = new CompoundTag();
            if (entity.save(entityTag)) {
                contents.entities.put(entity, entityTag);
            }
        });
        
        LOGGER.info("Extracted {} blocks, {} block entities, {} entities from chunk {}", 
                   contents.blocks.size(), contents.blockEntities.size(), contents.entities.size(), chunkPos);

        return contents;
    }

    public static void rebuildContents(ServerLevel level, ChunkPos chunkPos, SpaceContents contents) {
        LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);

        contents.blocks.forEach((pos, blockTag) -> {
            NbtOps nbtOps = NbtOps.INSTANCE;
            DataResult<BlockState> result = BlockState.CODEC.parse(nbtOps, blockTag.getCompound("BlockState"));
            BlockState state = result.resultOrPartial(error -> {
                LOGGER.warn("Failed to deserialize BlockState: " + error);
            }).orElse(null);
            if (state != null) {
                chunk.setBlockState(pos, state, false);
            }
        });

        contents.blockEntities.forEach((pos, blockEntityTag) -> {
           chunk.setBlockEntityNbt(blockEntityTag);
        });

        contents.entities.forEach((entity, entityTag) -> {
            Entity readEntity = EntityType.loadEntityRecursive(entityTag, level, (e) -> {
                e.moveTo(e.getX(), e.getY(), e.getZ(), e.getYRot(), e.getXRot());
                return e;
            });
            if (entity != null) {
                level.addFreshEntity(entity);
            }
        });
        chunk.setUnsaved(true);
    }

    public static void runSimulation(ServerLevel level, ChunkPos chunkPos, int tickCount) {
        ServerChunkCache chunkCache = level.getChunkSource();
        chunkCache.addRegionTicket(TicketType.UNKNOWN, chunkPos, 0, chunkPos);
        ChunkAccess chunk = level.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.FULL, true);
        if (chunk == null) {
            LOGGER.warn("Chunk at " + chunkPos + " could not be loaded.");
            return;
        }
        for (int i = 0; i < tickCount; i++) {
            level.tickChunk((LevelChunk)chunk, i);
        }
        chunkCache.removeRegionTicket(TicketType.UNKNOWN, chunkPos, 0, chunkPos);
    }
}