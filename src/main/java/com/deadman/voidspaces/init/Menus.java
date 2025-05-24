package com.deadman.voidspaces.init;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.core.registries.Registries;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;

import com.deadman.voidspaces.VoidSpaces;
import com.deadman.voidspaces.world.inventory.VoidHopperMenu;
import com.deadman.voidspaces.world.inventory.VoidDropperMenu;

public class Menus {
    public static final DeferredRegister<MenuType<?>> REGISTRY = DeferredRegister.create(Registries.MENU, VoidSpaces.MODID);
    
    public static final DeferredHolder<MenuType<?>, MenuType<VoidHopperMenu>> VOID_HOPPER_MENU = REGISTRY.register(
        "void_hopper", 
        () -> IMenuTypeExtension.create((containerId, inventory, buffer) -> {
            return new VoidHopperMenu(containerId, inventory, buffer);
        })
    );
    
    public static final DeferredHolder<MenuType<?>, MenuType<VoidDropperMenu>> VOID_DROPPER_MENU = REGISTRY.register(
        "void_dropper", 
        () -> IMenuTypeExtension.create((containerId, inventory, buffer) -> {
            return new VoidDropperMenu(containerId, inventory, buffer);
        })
    );
}