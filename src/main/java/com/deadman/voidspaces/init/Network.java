package com.deadman.voidspaces.init;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.deadman.voidspaces.VoidSpaces;
import com.deadman.voidspaces.infiniverse.internal.UpdateDimensionsPacket;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = VoidSpaces.MODID, bus = EventBusSubscriber.Bus.MOD)
public class Network {
    private static final Logger LOGGER = LoggerFactory.getLogger(Network.class);
    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        event.registrar(VoidSpaces.MODID).optional().playToClient(UpdateDimensionsPacket.TYPE, UpdateDimensionsPacket.STREAM_CODEC, UpdateDimensionsPacket::handle);
    }
}