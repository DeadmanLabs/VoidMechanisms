package com.deadman.voidspaces.init;

import com.deadman.voidspaces.block.entity.EngineEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

import com.deadman.voidspaces.VoidSpaces;
import com.deadman.voidspaces.init.ModBlocks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Tabs {
    private static final Logger LOGGER = LoggerFactory.getLogger(Tabs.class);
    public static final DeferredRegister<CreativeModeTab> REGISTRY = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, VoidSpaces.MODID);
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> VOID_SPACES = REGISTRY.register(
        "void_spaces",
        () -> CreativeModeTab.builder()
            .title(Component.literal("Void Spaces"))
            .icon(() -> new ItemStack(ModBlocks.VOID_ENGINE.get(), 1))
            .displayItems((parameters, output) -> {
                LOGGER.info("ModBlocks.VOID_ENGINE: " + ModBlocks.VOID_ENGINE.get());
                LOGGER.info("ModBlocks.VOID_ENGINE as Item: " + ModBlocks.VOID_ENGINE.get().asItem());
                output.accept(new ItemStack(ModBlocks.VOID_ENGINE.get().asItem(), 1));
            })
            .withSearchBar()
            .build()
    );
}