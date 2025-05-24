package com.deadman.voidspaces.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.Block;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

import com.deadman.voidspaces.block.entity.*;
import com.deadman.voidspaces.VoidSpaces;

public class BlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> REGISTRY = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, VoidSpaces.MODID);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EngineEntity>> ENGINE_BLOCK_ENTITY = REGISTRY.register(
            "engine_block_entity",
            () -> BlockEntityType.Builder.of(EngineEntity::new, ModBlocks.VOID_ENGINE.get()).build(null)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AcceleratorEntity>> ACCELERATOR_BLOCK_ENTITY = REGISTRY.register(
            "accelerator_block_entity",
            () -> BlockEntityType.Builder.of(AcceleratorEntity::new, ModBlocks.VOID_ACCELERATOR.get()).build(null)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ExtractorEntity>> EXTRACTOR_BLOCK_ENTITY = REGISTRY.register(
            "extractor_block_entity",
            () -> BlockEntityType.Builder.of(ExtractorEntity::new, ModBlocks.VOID_EXTRACTOR.get()).build(null)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InjectorEntity>> INJECTOR_BLOCK_ENTITY = REGISTRY.register(
            "injector_block_entity",
            () -> BlockEntityType.Builder.of(InjectorEntity::new, ModBlocks.VOID_INJECTOR.get()).build(null)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StabilizerEntity>> STABILIZER_BLOCK_ENTITY = REGISTRY.register(
            "stabilizer_block_entity",
            () -> BlockEntityType.Builder.of(StabilizerEntity::new, ModBlocks.VOID_STABILIZER.get()).build(null)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VoidHopperEntity>> VOID_HOPPER_BLOCK_ENTITY = REGISTRY.register(
            "void_hopper_block_entity",
            () -> BlockEntityType.Builder.of(VoidHopperEntity::new, ModBlocks.VOID_HOPPER.get()).build(null)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VoidDropperEntity>> VOID_DROPPER_BLOCK_ENTITY = REGISTRY.register(
            "void_dropper_block_entity",
            () -> BlockEntityType.Builder.of(VoidDropperEntity::new, ModBlocks.VOID_DROPPER.get()).build(null)
    );
}