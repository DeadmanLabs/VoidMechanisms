package com.deadman.voidspaces.init;

import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

import com.deadman.voidspaces.VoidSpaces;

@EventBusSubscriber(modid = VoidSpaces.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class KeyBindings {
    public static final KeyMapping OPEN_TEST_MENU = new KeyMapping(
            "key.voidspaces.open_test_menu", // Translation key
            GLFW.GLFW_KEY_P, // Default key: P
            "key.category.voidspaces" // Category
    );
    public static final KeyMapping EXIT_DIMENSION = new KeyMapping(
            "key.voidspaces.exit_dimension",
            GLFW.GLFW_KEY_O,
            "key.category.voidspaces"
    );

    @SubscribeEvent
    public static void registerKeyBindings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_TEST_MENU);
        event.register(EXIT_DIMENSION);
    }
}