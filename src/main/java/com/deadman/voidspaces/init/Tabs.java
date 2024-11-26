package com.deadman.voidspaces;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

import com.deadman.voidspaces.VoidSpaces;

public class Tabs {
    public static final DeferredRegister<CreativeModeTab> REGISTRY = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, VoidSpaces.MODID);
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> VOID_SPACES = REGISTRY.register(
        "void_spaces",
        () -> CreativeModeTab.builder()
            .title(Component.literal("Void Spaces"))
            .icon(() -> new ItemStack())
            .displayItems((parameters, output) -> {
                
            })
            .withSearchBar()
            .build()
    )
}