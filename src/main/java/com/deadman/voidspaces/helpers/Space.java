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

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        for (int x = chunkPos.getMinBlockX(); x <= chunkPos.getMaxBlockX(); x++) {
            for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
                for (int z = chunkPos.getMinBlockZ(); z <= chunkPos.getMaxBlockZ(); z++) {
                    mutablePos.set(x, y, z);
                    BlockState blockState = chunk.getBlockState(mutablePos);
                    if (blockState.isAir() || blockState.getBlock() == Blocks.BEDROCK) {
                        continue;
                    }
                    CompoundTag blockTag = new CompoundTag();
                    NbtOps nbtOps = NbtOps.INSTANCE;
                    DataResult<Tag> result = BlockState.CODEC.encodeStart(nbtOps, blockState);
                    result.resultOrPartial(error -> {
                        throw new IllegalStateException("Failed to serialize BlockState: " + error);
                    }).ifPresent(serializedTag -> blockTag.put("BlockState", serializedTag));
                    contents.blocks.put(mutablePos.immutable(), blockTag);
                    BlockEntity blockEntity = chunk.getBlockEntity(mutablePos);
                    if (blockEntity != null) {
                        CompoundTag blockEntityTag = blockEntity.saveWithFullMetadata((HolderLookup.Provider)level.registryAccess().lookup(Registries.BLOCK_ENTITY_TYPE).orElseThrow());
                        contents.blockEntities.put(mutablePos.immutable(), blockEntityTag);
                    }
                }
            }
        }

        level.getAllEntities().forEach((entity) -> {
            CompoundTag entityTag = new CompoundTag();
            if (entity.save(entityTag)) {
                contents.entities.put(entity, entityTag);
            }
        });

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