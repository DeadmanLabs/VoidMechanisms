package com.deadman.voidspaces.init;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import com.deadman.voidspaces.VoidSpaces;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventBusSubscriber(modid = VoidSpaces.MODID, bus = EventBusSubscriber.Bus.MOD)
public class BlockCapabilities {
    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        
    }
}