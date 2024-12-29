package com.deadman.voidspaces.init;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.core.registries.Registries;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;

import com.deadman.voidspaces.VoidSpaces;

public class Menus {
    public static final DeferredRegister<MenuType<?>> REGISTRY = DeferredRegister.create(Registries.MENU, VoidSpaces.MODID);
    
}