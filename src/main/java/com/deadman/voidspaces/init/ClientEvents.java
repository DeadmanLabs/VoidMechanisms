package com.deadman.voidspaces.init;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.deadman.voidspaces.helpers.graphical.components.TestScreen;
import com.deadman.voidspaces.network.BorderSyncRequestPacket;
import com.deadman.voidspaces.network.ReturnHomePacket;
import net.neoforged.neoforge.network.PacketDistributor;
import com.deadman.voidspaces.VoidSpaces;

@EventBusSubscriber(modid = VoidSpaces.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class ClientEvents {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientEvents.class);
    
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && KeyBindings.OPEN_TEST_MENU.consumeClick()) {
            minecraft.setScreen(new TestScreen());
        }
        if (minecraft.player != null && KeyBindings.RETURN_ORIGIN.consumeClick()) {
            PacketDistributor.sendToServer(new ReturnHomePacket());
        }
    }
    
    /**
     * Handle client-side entity travel to dimension to request border synchronization
     */
    @SubscribeEvent
    public static void onClientTravelDimension(EntityTravelToDimensionEvent.Client event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getEntity() == minecraft.player) {
            ResourceKey<Level> dimensionKey = event.getDimensionKey();
            if (dimensionKey.location().getNamespace().equals(VoidSpaces.MODID)) {
                LOGGER.info("Client traveling to custom dimension: {}", dimensionKey.location());
                PacketDistributor.sendToServer(new BorderSyncRequestPacket(dimensionKey));
            }
        }
    }
}
