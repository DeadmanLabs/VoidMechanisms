package com.deadman.voidspaces.init;

import com.deadman.voidspaces.helpers.Dimensional;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventBusSubscriber
public class ServerEvents {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerEvents.class);

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Check if player logged in while in a voidspace dimension
            Dimensional wrapper = Dimensional.getWrapper(player.level().dimension());
            if (wrapper != null) {
                // Player is in a voidspace dimension, ensure world border is synced after login
                LOGGER.info("Player {} logged in while in voidspace dimension, marking for world border sync", 
                           player.getName().getString());
                
                // Mark player for world border sync on next tick when they're fully loaded
                player.getPersistentData().putBoolean("VoidSpaces_NeedsBorderSync", true);
                player.getPersistentData().putInt("VoidSpaces_BorderSyncDelay", 40); // 2 second delay
            }
        }
    }
    
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (player.getPersistentData().getBoolean("VoidSpaces_NeedsBorderSync")) {
                int delay = player.getPersistentData().getInt("VoidSpaces_BorderSyncDelay");
                if (delay > 0) {
                    player.getPersistentData().putInt("VoidSpaces_BorderSyncDelay", delay - 1);
                } else {
                    // Delay has elapsed and player should be fully loaded
                    player.getPersistentData().remove("VoidSpaces_NeedsBorderSync");
                    player.getPersistentData().remove("VoidSpaces_BorderSyncDelay");
                    
                    Dimensional wrapper = Dimensional.getWrapper(player.level().dimension());
                    if (wrapper != null) {
                        LOGGER.info("Executing delayed world border sync for player: {}", player.getName().getString());
                        wrapper.ensureWorldBorderForPlayer(player);
                        wrapper.restoreOwnerReturnPosition(player);
                    }
                }
            }
        }
    }
    
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("Server stopping - cleaning up dimensional wrappers to prevent save hang");
        Dimensional.cleanupAllForSave();
    }
}