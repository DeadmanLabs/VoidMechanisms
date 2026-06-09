package com.deadman.voidspaces.init;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.core.registries.Registries;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;

import com.deadman.voidspaces.VoidSpaces;
import com.deadman.voidspaces.client.gui.VoidHopperScreen;
import com.deadman.voidspaces.client.gui.VoidDropperScreen;
import com.deadman.voidspaces.client.gui.VoidEngineScreen;
import com.deadman.voidspaces.client.VoidEngineRenderer;

@EventBusSubscriber(modid = VoidSpaces.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class Screens {
    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(Menus.VOID_HOPPER_MENU.get(), VoidHopperScreen::new);
        event.register(Menus.VOID_DROPPER_MENU.get(), VoidDropperScreen::new);
        event.register(Menus.VOID_ENGINE_MENU.get(), VoidEngineScreen::new);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BlockEntities.ENGINE_BLOCK_ENTITY.get(), VoidEngineRenderer::new);
    }
}