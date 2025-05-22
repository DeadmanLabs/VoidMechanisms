package com.deadman.voidspaces.init;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.deadman.voidspaces.VoidSpaces;
import com.deadman.voidspaces.infiniverse.internal.UpdateDimensionsPacket;
import com.deadman.voidspaces.init.ExitDimensionPacket;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = VoidSpaces.MODID, bus = EventBusSubscriber.Bus.MOD)
public class Network {
    private static final Logger LOGGER = LoggerFactory.getLogger(Network.class);
    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(VoidSpaces.MODID).optional();
        registrar.playToClient(UpdateDimensionsPacket.TYPE, UpdateDimensionsPacket.STREAM_CODEC, UpdateDimensionsPacket::handle);
        registrar.playToServer(ExitDimensionPacket.TYPE, ExitDimensionPacket.STREAM_CODEC, ExitDimensionPacket::handle);
    }
}