package com.deadman.voidspaces.init;

import com.deadman.voidspaces.VoidSpaces;
import com.deadman.voidspaces.helpers.Dimensional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

@EventBusSubscriber
public class ServerEvents {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerEvents.class);

    // -------------------------------------------------------------------------
    // Bedrock floor protection — cancel all block breaks at minBuildHeight in void dimensions
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (Dimensional.getWrapper(level.dimension()) == null) return;
        if (event.getPos().getY() == level.getMinBuildHeight()) {
            event.setCanceled(true);
        }
    }

    // -------------------------------------------------------------------------
    // Boundary enforcement — cancel block placement outside accessible chunk area
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Dimensional wrapper = Dimensional.getWrapper(level.dimension());
        if (wrapper == null) return;
        BlockPos pos = event.getPos();
        int max = wrapper.getChunkSize() * 16;
        if (pos.getX() < 0 || pos.getX() >= max || pos.getZ() < 0 || pos.getZ() >= max) {
            event.setCanceled(true);
        }
    }

    // -------------------------------------------------------------------------
    // Simulation item capture — intercept item entity spawning in void dimensions
    // during an active simulation and route to OutPort instead of dropping physically.
    // This bypasses the need for entity ticking (chunks at BLOCK_TICKING level have
    // entity sections in TRACKING state, making getEntitiesOfClass return nothing).
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ItemEntity ie)) return;
        // Log every item join in any void dimension so we can confirm items are spawning
        Dimensional wrapper = Dimensional.getWrapper(event.getLevel().dimension());
        if (wrapper != null) {
            LOGGER.info("[SimCapture] Item {} joining void dim {} (simulating={})",
                    ie.getItem().getDescriptionId(), event.getLevel().dimension().location(), wrapper.isSimulating());
        }
        if (wrapper == null || !wrapper.isSimulating()) return;
        wrapper.captureSimulationItem(ie.getItem().copy());
        event.setCanceled(true);
    }

    // -------------------------------------------------------------------------
    // Server tick — auto-unload and simulation extra ticks (LOWEST priority runs last)
    // -------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerTick(ServerTickEvent.Post event) {
        // Snapshot the wrapper list to avoid ConcurrentModificationException
        for (Dimensional wrapper : new ArrayList<>(Dimensional.getAllWrappers())) {
            wrapper.tickUnloadCheck();
            wrapper.tickSimulationExtra(event.getServer()); // always runs; noop when speed == 1
        }
    }

    // -------------------------------------------------------------------------
    // Login — restore world border if player logs in while inside a void dimension
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var dim = player.level().dimension();
        if (!dim.location().getNamespace().equals(VoidSpaces.MODID)) return;

        LOGGER.info("Player {} logged in while in voidspace dimension — restoring wrapper",
                    player.getName().getString());

        // Force-load the engine's overworld chunk so EngineEntity.loadAdditional fires and
        // creates the Dimensional wrapper before the player tries to exit.
        if (player.getPersistentData().contains("VoidSpaces_EnginePos")) {
            BlockPos enginePos = BlockPos.of(player.getPersistentData().getLong("VoidSpaces_EnginePos"));
            ServerLevel overworld = player.server.getLevel(Level.OVERWORLD);
            if (overworld != null) {
                try {
                    overworld.getChunk(enginePos.getX() >> 4, enginePos.getZ() >> 4, ChunkStatus.FULL, true);
                    LOGGER.info("Force-loaded engine chunk at {} to restore Dimensional wrapper", enginePos);
                } catch (Exception e) {
                    LOGGER.warn("Could not force-load engine chunk — will retry via tick handler", e);
                }
            }
        }

        player.getPersistentData().putBoolean("VoidSpaces_NeedsBorderSync", true);
        player.getPersistentData().putInt("VoidSpaces_BorderSyncDelay", 40);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.getPersistentData().getBoolean("VoidSpaces_NeedsBorderSync")) return;

        int delay = player.getPersistentData().getInt("VoidSpaces_BorderSyncDelay");
        if (delay > 0) {
            player.getPersistentData().putInt("VoidSpaces_BorderSyncDelay", delay - 1);
            return;
        }

        Dimensional wrapper = Dimensional.getWrapper(player.level().dimension());
        if (wrapper == null) {
            // Wrapper still not available — retry up to 10 times (every 20 ticks = 10 s total)
            int retries = player.getPersistentData().getInt("VoidSpaces_BorderSyncRetries");
            if (retries < 10) {
                player.getPersistentData().putInt("VoidSpaces_BorderSyncDelay", 20);
                player.getPersistentData().putInt("VoidSpaces_BorderSyncRetries", retries + 1);
                // Each retry also tries to load the engine chunk in case it wasn't ready yet
                if (player.getPersistentData().contains("VoidSpaces_EnginePos")) {
                    BlockPos enginePos = BlockPos.of(player.getPersistentData().getLong("VoidSpaces_EnginePos"));
                    ServerLevel overworld = player.server.getLevel(Level.OVERWORLD);
                    if (overworld != null) {
                        try {
                            overworld.getChunk(enginePos.getX() >> 4, enginePos.getZ() >> 4, ChunkStatus.FULL, true);
                        } catch (Exception ignored) {}
                    }
                }
            } else {
                player.getPersistentData().remove("VoidSpaces_NeedsBorderSync");
                player.getPersistentData().remove("VoidSpaces_BorderSyncDelay");
                player.getPersistentData().remove("VoidSpaces_BorderSyncRetries");
                LOGGER.warn("Border sync gave up for {} after 10 retries", player.getName().getString());
            }
        } else {
            player.getPersistentData().remove("VoidSpaces_NeedsBorderSync");
            player.getPersistentData().remove("VoidSpaces_BorderSyncDelay");
            player.getPersistentData().remove("VoidSpaces_BorderSyncRetries");
            wrapper.ensureWorldBorderForPlayer(player);
            wrapper.restoreOwnerReturnPosition(player);
        }
    }

    // -------------------------------------------------------------------------
    // Server stopping — flush dimension saves
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("Server stopping — cleaning up dimensional wrappers");
        Dimensional.cleanupAllForSave();
    }
}
