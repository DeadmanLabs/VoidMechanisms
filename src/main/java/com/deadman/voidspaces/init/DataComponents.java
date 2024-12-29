package com.deadman.voidspaces.init;

import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponentType.Builder;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegistryManager;

import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;

import com.deadman.voidspaces.VoidSpaces;

public class DataComponents {
    public static final DeferredRegister.DataComponents REGISTRY = DeferredRegister.createDataComponents(VoidSpaces.MODID);
    
}