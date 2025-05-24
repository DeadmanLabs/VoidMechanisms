package com.deadman.voidspaces.init;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredBlock;

import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import com.deadman.voidspaces.block.VoidAccelerator;
import com.deadman.voidspaces.block.VoidEngine;
import com.deadman.voidspaces.block.VoidExtractor;
import com.deadman.voidspaces.block.VoidHopper;
import com.deadman.voidspaces.block.VoidDropper;
import com.deadman.voidspaces.block.VoidInjector;
import com.deadman.voidspaces.block.VoidStabilizer;
import com.deadman.voidspaces.VoidSpaces;

public class ModBlocks {
    public static final DeferredRegister.Blocks REGISTRY = DeferredRegister.createBlocks(VoidSpaces.MODID);
    public static final DeferredBlock<VoidAccelerator> VOID_ACCELERATOR = REGISTRY.register(
        "void_accelerator",
        () -> new VoidAccelerator(BlockBehaviour.Properties.of().mapColor(MapColor.STONE))
    );
    public static final DeferredBlock<VoidEngine> VOID_ENGINE = REGISTRY.register(
        "void_engine",
        () -> new VoidEngine(BlockBehaviour.Properties.of().mapColor(MapColor.STONE))
    );
    public static final DeferredBlock<VoidExtractor> VOID_EXTRACTOR = REGISTRY.register(
        "void_extractor",
        () -> new VoidExtractor(BlockBehaviour.Properties.of().mapColor(MapColor.STONE))
    );
    public static final DeferredBlock<VoidInjector> VOID_INJECTOR = REGISTRY.register(
        "void_injector",
        () -> new VoidInjector(BlockBehaviour.Properties.of().mapColor(MapColor.STONE))
    );
    public static final DeferredBlock<VoidStabilizer> VOID_STABILIZER = REGISTRY.register(
        "void_stabilizer",
        () -> new VoidStabilizer(BlockBehaviour.Properties.of().mapColor(MapColor.STONE))
    );
    public static final DeferredBlock<VoidHopper> VOID_HOPPER = REGISTRY.register(
        "void_hopper",
        () -> new VoidHopper(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3.0F, 4.8F).sound(net.minecraft.world.level.block.SoundType.METAL))
    );
    public static final DeferredBlock<VoidDropper> VOID_DROPPER = REGISTRY.register(
        "void_dropper",
        () -> new VoidDropper(BlockBehaviour.Properties.of().mapColor(MapColor.STONE))
    );
}