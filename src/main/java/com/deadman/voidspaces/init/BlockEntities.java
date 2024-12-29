package com.deadman.voidspaces.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.Block;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

import com.deadman.voidspaces.block.entity.EngineEntity;
import com.deadman.voidspaces.VoidSpaces;

public class BlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> REGISTRY = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, VoidSpaces.MODID);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EngineEntity>> ENGINE_BLOCK_ENTITY = REGISTRY.register(
            "engine_block_entity",
            () -> BlockEntityType.Builder.of(EngineEntity::new, ModBlocks.VOID_ENGINE.get()).build(null)
    );
}