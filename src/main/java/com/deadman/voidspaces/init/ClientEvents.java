package com.deadman.voidspaces.init;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import com.deadman.voidspaces.helpers.graphical.components.TestScreen;
import com.deadman.voidspaces.VoidSpaces;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = VoidSpaces.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class ClientEvents {
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && KeyBindings.OPEN_TEST_MENU.consumeClick()) {
            minecraft.setScreen(new TestScreen());
        }
        if (minecraft.player != null && KeyBindings.EXIT_DIMENSION.consumeClick()) {
            PacketDistributor.sendToServer(new ExitDimensionPacket());
        }
    }
}