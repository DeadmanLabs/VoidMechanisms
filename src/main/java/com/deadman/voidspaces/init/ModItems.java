package com.deadman.voidspaces.init;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;

import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.deadman.voidspaces.item.*;

import com.deadman.voidspaces.VoidSpaces;

public class ModItems {
    public static DeferredRegister.Items REGISTRY = DeferredRegister.createItems(VoidSpaces.MODID);
    public static final DeferredItem<BlockItem> VOID_ACCELERATOR = REGISTRY.register(
            "void_accelerator",
            () -> new BlockItem(ModBlocks.VOID_ACCELERATOR.get(), new Item.Properties())
    );
    public static final DeferredItem<BlockItem> VOID_ENGINE = REGISTRY.register(
            "void_engine",
            () -> new BlockItem(ModBlocks.VOID_ENGINE.get(), new Item.Properties())
    );
    public static final DeferredItem<BlockItem> VOID_EXTRACTOR = REGISTRY.register(
            "void_extractor",
            () -> new BlockItem(ModBlocks.VOID_EXTRACTOR.get(), new Item.Properties())
    );
    public static final DeferredItem<BlockItem> VOID_INJECTOR = REGISTRY.register(
            "void_injector",
            () -> new BlockItem(ModBlocks.VOID_INJECTOR.get(), new Item.Properties())
    );
    public static final DeferredItem<BlockItem> VOID_STABILIZER = REGISTRY.register(
            "void_stabilizer",
            () -> new BlockItem(ModBlocks.VOID_STABILIZER.get(), new Item.Properties())
    );
    public static final DeferredItem<VoidAlloy> VOID_ALLOY = REGISTRY.register(
            "void_alloy",
            VoidAlloy::new
    );
    public static final DeferredItem<VoidBlueprint> VOID_BLUEPRINT = REGISTRY.register(
            "void_blueprint",
            VoidBlueprint::new
    );
    public static final DeferredItem<VoidCard> VOID_CARD = REGISTRY.register(
            "void_card",
            VoidCard::new
    );
    public static final DeferredItem<VoidCore> VOID_CORE = REGISTRY.register(
            "void_core",
            VoidCore::new
    );
    public static final DeferredItem<VoidFrame> VOID_FRAME = REGISTRY.register(
            "void_frame",
            VoidFrame::new
    );
}