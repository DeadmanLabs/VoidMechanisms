package com.deadman.voidspaces.infiniverse.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.Lists;
import com.ibm.icu.impl.locale.XCldrStub.ImmutableSet;
import com.mojang.serialization.Lifecycle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistryAccess.ImmutableRegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;
import net.minecraft.world.level.storage.WorldData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import com.deadman.voidspaces.VoidSpaces;
import com.deadman.voidspaces.infiniverse.api.InfiniverseAPI;
import com.deadman.voidspaces.infiniverse.api.UnregisterDimensionEvent;
import com.deadman.voidspaces.helpers.DimensionalLevel;

public final class DimensionManager implements InfiniverseAPI {
    private static final RegistrationInfo DIMENSION_REGISTRATION_INFO = new RegistrationInfo(Optional.empty(), Lifecycle.stable());
    private DimensionManager() {}

    public static final DimensionManager INSTANCE = new DimensionManager();
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Set<ResourceKey<Level>> VANILLA_LEVELS = Set.of(Level.OVERWORLD, Level.NETHER, Level.END);

    private Set<ResourceKey<Level>> levelsPendingUnregistration = new HashSet<>();

    public ServerLevel getOrCreateLevel(final MinecraftServer server, final ResourceKey<Level> levelKey, final Supplier<LevelStem> dimensionFactory) {
        Map<ResourceKey<Level>, ServerLevel> map = server.forgeGetWorldMap();
        @Nullable ServerLevel existingLevel = map.get(levelKey);
        return existingLevel == null
                ? createAndRegisterLevel(server, map, levelKey, dimensionFactory)
                : existingLevel;
    }

    public void markDimensionForUnregistration(final MinecraftServer server, final ResourceKey<Level> levelToRemove) {
        if (!VANILLA_LEVELS.contains(levelToRemove)) {
            ServerLevel level = server.getLevel(levelToRemove);
            if (level != null) {
                level.save(null, true, false);
                levelsPendingUnregistration.add(levelToRemove);
            }
        }
    }

    public Set<ResourceKey<Level>> getLevelsPendingUnregistration() {
        return ImmutableSet.copyOf(levelsPendingUnregistration);
    }

    private static ServerLevel createAndRegisterLevel(final MinecraftServer server, final Map<ResourceKey<Level>, ServerLevel> map, final ResourceKey<Level> levelKey, Supplier<LevelStem> dimensionFactory)
    {
        // get everything we need to create the dimension and the level
        final ServerLevel overworld = server.getLevel(Level.OVERWORLD);

        // dimension keys have a 1:1 relationship with level keys, they have the same IDs as well
        final ResourceKey<LevelStem> dimensionKey = ResourceKey.create(Registries.LEVEL_STEM, levelKey.location());
        final LevelStem dimension = dimensionFactory.get();

        // the int in create() here is radius of chunks to watch, 11 is what the server uses when it initializes levels
        final ChunkProgressListener chunkProgressListener = ReflectionBuddy.MinecraftServerAccess.progressListenerFactory.apply(server).create(11);
        final Executor executor = ReflectionBuddy.MinecraftServerAccess.executor.apply(server);
        final LevelStorageAccess anvilConverter = ReflectionBuddy.MinecraftServerAccess.storageSource.apply(server);
        final WorldData worldData = server.getWorldData();
        final DerivedLevelData derivedLevelData = new DerivedLevelData(worldData, worldData.overworldData());

        // now we have everything we need to create the dimension and the level
        // this is the same order server init creates levels:
        // the dimensions are already registered when levels are created, we'll do that first
        // then instantiate level, add border listener, add to map, fire world load event

        // register the actual dimension
        Registry<LevelStem> dimensionRegistry = server.registryAccess().registryOrThrow(Registries.LEVEL_STEM);
        if (dimensionRegistry instanceof MappedRegistry<LevelStem> writableRegistry)
        {
            writableRegistry.unfreeze();
            writableRegistry.register(dimensionKey, dimension, DIMENSION_REGISTRATION_INFO);
        }
        else
        {
            throw new IllegalStateException(String.format("Unable to register dimension %s -- dimension registry not writable", dimensionKey.location()));
        }

        // create the level instance
        final DimensionalLevel newLevel = new DimensionalLevel(
                server,
                executor,
                anvilConverter,
                derivedLevelData,
                levelKey,
                dimension,
                chunkProgressListener,
                worldData.isDebugWorld(),
                overworld.getSeed(), // don't need to call BiomeManager#obfuscateSeed, overworld seed is already obfuscated
                List.of(), // "special spawn list"
                // phantoms, travelling traders, patrolling/sieging raiders, and cats are overworld special spawns
                // this is always empty for non-overworld dimensions (including json dimensions)
                // these spawners are ticked when the world ticks to do their spawning logic,
                // mods that need "special spawns" for their own dimensions should implement them via tick events or other systems
                false, // "tick time", true for overworld, always false for nether, end, and json dimensions
                (RandomSequences)null // as of 1.20.1 this argument is always null in vanilla, indicating the level should load the sequence from storage
        );

        // add world border listener, for parity with json dimensions
        // the vanilla behaviour is that world borders exist in every dimension simultaneously with the same size and position
        // these border listeners are automatically added to the overworld as worlds are loaded, so we should do that here too
        // TODO if world-specific world borders are ever added, change it here too
        overworld.getWorldBorder().addListener(new BorderChangeListener.DelegateBorderChangeListener(newLevel.getWorldBorder()));

        // register level
        map.put(levelKey, newLevel);

        // update forge's world cache so the new level can be ticked
        server.markWorldsDirty();

        // fire world load event
        NeoForge.EVENT_BUS.post(new LevelEvent.Load(newLevel));

        // update clients' dimension lists
        QuietPacketDistributors.sendToAll(server, new UpdateDimensionsPacket(Set.of(levelKey), true));

        return newLevel;
    }

    private void unregisterScheduledDimensions(final MinecraftServer server)
    {
        if (this.levelsPendingUnregistration.isEmpty())
            return;

        // flush the buffer
        final Set<ResourceKey<Level>> keysToRemove = this.levelsPendingUnregistration;
        this.levelsPendingUnregistration = new HashSet<>();

        // we need to remove the dimension/level from three places:
        // the server's dimension/levelstem registry, the server's level registry, and
        // the overworld's border listener
        // the level registry is just a simple map and the border listener has a remove() method
        // the dimension registry has five sub-collections that need to be cleaned up
        // we should also eject players from removed worlds so they don't get stuck there

        final Registry<LevelStem> oldRegistry = server.registryAccess().registryOrThrow(Registries.LEVEL_STEM);
        if (!(oldRegistry instanceof MappedRegistry<LevelStem> oldMappedRegistry))
        {
            LOGGER.warn("Cannot unload dimensions: dimension registry not an instance of MappedRegistry. There may be another mod causing incompatibility with Infiniverse, or Infiniverse may need to be updated for your version of forge/minecraft.");
            return;
        }
        LayeredRegistryAccess<RegistryLayer> layeredRegistryAccess = ReflectionBuddy.MinecraftServerAccess.registries.apply(server);
        RegistryAccess.Frozen composite = ReflectionBuddy.LayeredRegistryAccessAccess.composite.apply(layeredRegistryAccess);
        if (!(composite instanceof ImmutableRegistryAccess immutableRegistryAccess))
        {
            LOGGER.warn("Cannot unload dimensions: composite registry not an instance of ImmutableRegistryAccess. There may be another mod causing incompatibility with Infiniverse, or Infiniverse may be updated for your version of forge/minecraft.");
            return;
        }

        final Set<ResourceKey<Level>> removedLevelKeys = new HashSet<>();
        final ServerLevel overworld = server.getLevel(Level.OVERWORLD);

        for (final ResourceKey<Level> levelKeyToRemove : keysToRemove)
        {
            final @Nullable ServerLevel levelToRemove = server.getLevel(levelKeyToRemove);
            if (levelToRemove == null)
                continue;

            UnregisterDimensionEvent unregisterDimensionEvent = new UnregisterDimensionEvent(levelToRemove);
            NeoForge.EVENT_BUS.post(unregisterDimensionEvent);
            if (unregisterDimensionEvent.isCanceled())
                continue;

            // null if specified level not present
            final @Nullable ServerLevel removedLevel = server.forgeGetWorldMap().remove(levelKeyToRemove);

            if (removedLevel != null) // if we removed the key from the map
            {
                // eject players from dead world
                // iterate over a copy as the world will remove players from the original list
                for (final ServerPlayer player : Lists.newArrayList(removedLevel.players()))
                {
                    // send players to their respawn point
                    ResourceKey<Level> respawnKey = player.getRespawnDimension();
                    // if we're removing their respawn world then just send them to the overworld
                    if (keysToRemove.contains(respawnKey))
                    {
                        respawnKey = Level.OVERWORLD;
                        player.setRespawnPosition(respawnKey, null, 0, false, false);
                    }
                    if (respawnKey == null)
                    {
                        respawnKey = Level.OVERWORLD;
                    }
                    @Nullable ServerLevel destinationLevel = server.getLevel(respawnKey);
                    if (destinationLevel == null)
                    {
                        destinationLevel = overworld;
                    }

                    @Nullable
                    BlockPos destinationPos = player.getRespawnPosition();
                    if (destinationPos == null)
                    {
                        destinationPos = destinationLevel.getSharedSpawnPos();
                    }

                    final float respawnAngle = player.getRespawnAngle();
                    // "respawning" the player via the player list schedules a task in the server to
                    // run after the post-server tick
                    // that causes some minor logspam due to the player's world no longer being
                    // loaded
                    // teleporting the player via a teleport avoids this
                    player.teleportTo(destinationLevel, destinationPos.getX(), destinationPos.getY(), destinationPos.getZ(), respawnAngle, 0F);
                }
                // save the world now or it won't be saved later and data that may be wanted to
                // be kept may be lost
                removedLevel.save(null, false, removedLevel.noSave());

                // fire world unload event -- when the server stops, this would fire after
                // worlds get saved, we'll do that here too
                NeoForge.EVENT_BUS.post(new LevelEvent.Unload(removedLevel));

                // remove the world border listener if possible
                final WorldBorder overworldBorder = overworld.getWorldBorder();
                final WorldBorder removedWorldBorder = removedLevel.getWorldBorder();
                final List<BorderChangeListener> listeners = ReflectionBuddy.WorldBorderAccess.listeners.apply(overworldBorder);
                BorderChangeListener targetListener = null;
                for (BorderChangeListener listener : listeners)
                {
                    if (listener instanceof BorderChangeListener.DelegateBorderChangeListener delegate
                            && removedWorldBorder == ReflectionBuddy.DelegateBorderChangeListenerAccess.worldBorder.apply(delegate))
                    {
                        targetListener = listener;
                        break;
                    }
                }
                if (targetListener != null)
                {
                    overworldBorder.removeListener(targetListener);
                }

                // track the removed level
                removedLevelKeys.add(levelKeyToRemove);
            }
        }

        if (!removedLevelKeys.isEmpty())
        {
            // replace the old dimension registry with a new one containing the dimensions
            // that weren't removed, in the same order
            final MappedRegistry<LevelStem> newRegistry = new MappedRegistry<>(Registries.LEVEL_STEM, oldMappedRegistry.registryLifecycle());

            for (final var entry : oldRegistry.entrySet())
            {
                final ResourceKey<LevelStem> oldKey = entry.getKey();
                final ResourceKey<Level> oldLevelKey = ResourceKey.create(Registries.DIMENSION, oldKey.location());
                final LevelStem dimension = entry.getValue();
                if (oldKey != null && dimension != null && !removedLevelKeys.contains(oldLevelKey))
                {
                    newRegistry.register(oldKey, dimension, oldRegistry.registrationInfo(oldKey).orElse(DIMENSION_REGISTRATION_INFO));
                }
            }

            // then replace the old registry with the new registry
            // as of 1.20.1 the dimension registry is stored in the server's layered registryaccess
            // this has several immutable collections of sub-registryaccesses,
            // so we'll need to recreate each of them.

            // Each ServerLevel has a reference to the layered registry access's *composite* registry access
            // so we should edit the internal fields where possible (instead of reconstructing the registry accesses)

            List<RegistryAccess.Frozen> newRegistryAccessList = new ArrayList<>();
            for (RegistryLayer layer : RegistryLayer.values())
            {
                if (layer == RegistryLayer.DIMENSIONS)
                {
                    newRegistryAccessList.add(new RegistryAccess.ImmutableRegistryAccess(List.of(newRegistry)).freeze());
                }
                else
                {
                    newRegistryAccessList.add(layeredRegistryAccess.getLayer(layer));
                }
            }
            Map<ResourceKey<? extends Registry<?>>, Registry<?>> newRegistryMap = new HashMap<>();
            for (var registryAccess : newRegistryAccessList)
            {
                var registries = registryAccess.registries().toList();
                for (var registryEntry : registries)
                {
                    newRegistryMap.put(registryEntry.key(), registryEntry.value());
                }
            }
            ReflectionBuddy.LayeredRegistryAccessAccess.values.set(layeredRegistryAccess, List.copyOf(newRegistryAccessList));
            ReflectionBuddy.ImmutableRegistryAccessAccess.registries.set(immutableRegistryAccess, newRegistryMap);

            // update the server's levels so dead levels don't get ticked
            server.markWorldsDirty();

            // notify client of the removed levels
            QuietPacketDistributors.sendToAll(server, new UpdateDimensionsPacket(removedLevelKeys, false));
        }
    }

    @EventBusSubscriber(modid = VoidSpaces.MODID)
    private static class ForgeEventHandler
    {
        @SubscribeEvent(priority=EventPriority.LOWEST)
        public static void onServerTick(final ServerTickEvent.Post event)
        {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null)
            {
                DimensionManager.INSTANCE.unregisterScheduledDimensions(server);
            }
        }

        @SubscribeEvent
        public static void onServerStopped(final ServerStoppedEvent event)
        {
            // clear state on server exit (important for singleplayer worlds)
            DimensionManager.INSTANCE.levelsPendingUnregistration = new HashSet<>();
        }
    }
}