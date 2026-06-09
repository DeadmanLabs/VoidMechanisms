package com.deadman.voidspaces.helpers;

import com.deadman.voidspaces.VoidSpaces;
import com.deadman.voidspaces.block.entity.VoidInPortEntity;
import com.deadman.voidspaces.block.entity.VoidOutPortEntity;
import com.deadman.voidspaces.init.DataAttachments;
import com.deadman.voidspaces.init.SimulationDataPacket;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.*;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.*;

import com.deadman.voidspaces.infiniverse.api.*;

public class Dimensional {
    private static final Logger LOGGER = LoggerFactory.getLogger(Dimensional.class);
    private static int dimensionCount = 0;
    private static final Map<ResourceKey<Level>, Dimensional> WRAPPERS = new HashMap<>();

    private final MinecraftServer server;
    public final ResourceKey<Level> dimension;
    private final ServerLevel dimensionLevel;
    private final UUID owner;
    private final int chunkSize;

    // Return position tracking
    private final Map<Player, BlockPos> returnPositions = new HashMap<>();
    private final Map<Player, ResourceKey<Level>> returnDimensions = new HashMap<>();
    private BlockPos ownerReturnPosition;
    private ResourceKey<Level> ownerReturnDimension;
    private Space.SpaceContents machine = new Space.SpaceContents();
    private final Map<ServerPlayer, BorderChangeListener> borderListeners = new HashMap<>();

    // Auto-unload
    private int emptyTicks = 0;
    private static final int UNLOAD_GRACE_TICKS = 1200; // 60 seconds

    // Simulation
    private boolean simulating = false;
    private int simulationSpeed = 1;
    private long simStartRealTick = 0;
    private long simTotalTicks = 0;
    private long simRealTicks = 0;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    public Dimensional(MinecraftServer server, UUID owner, int chunkSize) {
        if (server == null || owner == null) {
            throw new IllegalArgumentException("MinecraftServer and owner UUID must not be null");
        }
        this.server = server;
        this.chunkSize = Math.max(1, Math.min(4, chunkSize));

        int nextIndex = getNextAvailableDimensionIndex(server);
        this.dimension = ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath(VoidSpaces.MODID, "voidspace_" + nextIndex));

        LOGGER.info("Creating new dimension index={} chunkSize={}", nextIndex, this.chunkSize);

        ServerLevel level = InfiniverseAPI.get().getOrCreateLevel(server, dimension,
                () -> createLevel(server, DimensionTypeOptions.FLAT));
        this.dimensionLevel = level;
        applyChunkSize(level);

        dimensionCount = Math.max(dimensionCount, nextIndex + 1);
        this.owner = owner;
        WRAPPERS.put(dimension, this);
        forceLoadAccessibleChunks();
    }

    /** Constructor for restoring a dimension that was saved to disk. */
    public Dimensional(MinecraftServer server, UUID owner, ResourceKey<Level> existingDimension, int chunkSize) {
        this.server = server;
        this.chunkSize = Math.max(1, Math.min(4, chunkSize));
        this.dimension = ResourceKey.create(Registries.DIMENSION, existingDimension.location());
        this.owner = owner;

        ServerLevel level = InfiniverseAPI.get().getOrCreateLevel(server, dimension,
                () -> createLevel(server, DimensionTypeOptions.FLAT));
        this.dimensionLevel = level;
        applyChunkSize(level);

        LOGGER.info("Restored dimension {} chunkSize={}", dimension.location(), this.chunkSize);
        WRAPPERS.put(dimension, this);
        forceLoadAccessibleChunks();
    }

    /** Apply the correct chunk size to the level's world border. */
    private void applyChunkSize(ServerLevel level) {
        if (level instanceof DimensionalLevel dl) {
            dl.setChunkSize(chunkSize);
        } else {
            LOGGER.warn("Dimension {} loaded as plain ServerLevel — applying reflection border workaround", dimension.location());
            applyWorldBorderWorkaround(level);
        }
    }

    /** Force-load only the accessible chunks so they stay ticked and saves stay scoped. */
    private void forceLoadAccessibleChunks() {
        ServerChunkCache chunkSource = dimensionLevel.getChunkSource();
        for (int cx = 0; cx < chunkSize; cx++) {
            for (int cz = 0; cz < chunkSize; cz++) {
                ChunkPos cp = new ChunkPos(cx, cz);
                chunkSource.addRegionTicket(TicketType.FORCED, cp, 3, cp);
                LOGGER.debug("Force-loaded chunk ({},{}) in dimension {}", cx, cz, dimension.location());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Static accessors
    // -------------------------------------------------------------------------

    public static Dimensional getWrapper(ResourceKey<Level> key) {
        return WRAPPERS.get(key);
    }

    public static Collection<Dimensional> getAllWrappers() {
        return Collections.unmodifiableCollection(WRAPPERS.values());
    }

    /**
     * Fallback exit used when the Dimensional wrapper hasn't been created yet (e.g. engine
     * chunk not loaded after re-login). Restores inventory and game mode from persistent data
     * and teleports the player to their saved return position.
     */
    public static void emergencyExit(ServerPlayer player) {
        double x = player.getX(), y = player.getY(), z = player.getZ();
        float yRot = player.getYRot(), xRot = player.getXRot();
        String originalGameMode = player.getPersistentData().getString("VoidSpaces_OriginalGameMode");

        player.getInventory().clearContent();
        if (player.getPersistentData().contains("VoidSpaces_SavedPlayerData")) {
            player.load(player.getPersistentData().getCompound("VoidSpaces_SavedPlayerData"));
            player.getPersistentData().remove("VoidSpaces_SavedPlayerData");
        }
        // Restore current position so changeDimension has a sane source position
        player.setPos(x, y, z);
        player.setYRot(yRot);
        player.setXRot(xRot);

        GameType gameType = originalGameMode.isEmpty()
                ? GameType.SURVIVAL
                : GameType.byName(originalGameMode, GameType.SURVIVAL);
        player.setGameMode(gameType);
        player.getAbilities().instabuild = false;
        player.getAbilities().flying = false;
        player.getAbilities().invulnerable = false;
        player.onUpdateAbilities();
        player.getPersistentData().remove("VoidSpaces_InDimension");
        player.getPersistentData().remove("VoidSpaces_OriginalGameMode");

        DataAttachments.ReturnPositionData returnData = player.getData(DataAttachments.RETURN_POSITION);
        BlockPos returnPos = returnData.hasReturnData() ? returnData.getReturnPosition() : new BlockPos(0, 64, 0);
        ResourceKey<Level> returnDimKey = returnData.hasReturnData() ? returnData.getReturnDimension() : Level.OVERWORLD;
        returnData.clear();

        ServerLevel returnLevel = player.server.getLevel(returnDimKey);
        if (returnLevel == null) returnLevel = player.server.getLevel(Level.OVERWORLD);
        if (returnLevel == null) {
            LOGGER.error("Emergency exit failed: cannot find return level for {}", player.getName().getString());
            return;
        }

        player.changeDimension(new net.minecraft.world.level.portal.DimensionTransition(
                returnLevel, returnPos.getCenter(), player.getDeltaMovement(),
                yRot, xRot, net.minecraft.world.level.portal.DimensionTransition.DO_NOTHING));
        LOGGER.warn("Emergency exit executed for {} (Dimensional wrapper not loaded)", player.getName().getString());
    }

    public static void cleanupAllForSave() {
        LOGGER.info("Cleaning up {} dimensional wrappers before save", WRAPPERS.size());
        for (Dimensional wrapper : WRAPPERS.values()) {
            wrapper.cleanupForSave();
        }
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public int getChunkSize() {
        return chunkSize;
    }

    public boolean isSimulating() {
        return simulating;
    }

    public int getSimulationSpeed() {
        return simulationSpeed;
    }

    public int getRandomTickSpeed() {
        return dimensionLevel.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_RANDOMTICKING).get();
    }

    /** Sets the randomTickSpeed gamerule ONLY on this void dimension's level — never touches other levels. */
    public void setRandomTickSpeed(int speed) {
        int clamped = Math.max(0, Math.min(4096, speed));
        dimensionLevel.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_RANDOMTICKING).set(clamped, server);
        LOGGER.info("Set randomTickSpeed={} on dimension {}", clamped, dimension.location());
    }

    /** Sets the tick rate multiplier for this dimension (always-on, not just during simulation). */
    public void setTickRate(int rate) {
        simulationSpeed = Math.max(1, rate);
        LOGGER.info("Set tick rate multiplier={}x on dimension {}", simulationSpeed, dimension.location());
    }

    // -------------------------------------------------------------------------
    // Auto-unload
    // -------------------------------------------------------------------------

    public void tickUnloadCheck() {
        if (simulating || simulationSpeed > 1) {
            emptyTicks = 0; // never auto-unload while running at elevated tick rate
            return;
        }
        if (dimensionLevel.players().isEmpty()) {
            emptyTicks++;
            if (emptyTicks >= UNLOAD_GRACE_TICKS) {
                LOGGER.info("Auto-unloading dimension {} (empty for {} ticks)", dimension.location(), emptyTicks);
                WRAPPERS.remove(dimension);
                InfiniverseAPI.get().markDimensionForUnregistration(server, dimension);
            }
        } else {
            emptyTicks = 0;
        }
    }

    // -------------------------------------------------------------------------
    // Simulation
    // -------------------------------------------------------------------------

    public void startSimulation(int speed) {
        if (simulating) stopSimulation();
        simulationSpeed = Math.max(1, speed);
        simulating = true;
        simTotalTicks = 0;
        simRealTicks = 0;
        // Snapshot InPort/OutPort baselines
        forEachAccessibleChunk((cx, cz) -> {
            LevelChunk chunk = dimensionLevel.getChunk(cx, cz);
            chunk.getBlockEntities().values().forEach(be -> {
                if (be instanceof VoidInPortEntity port) port.startSimulation();
                if (be instanceof VoidOutPortEntity port) port.startSimulation();
            });
        });
        LOGGER.info("Started simulation speed={}x randomTickSpeed={} on dimension {}",
                    speed, getRandomTickSpeed(), dimension.location());
        // Send an immediate packet so the GUI shows "running" state right away
        collectAndSendSimData(this.server);
    }

    /**
     * Called from EntityJoinLevelEvent when an item entity spawns in this dimension
     * while simulation is active. Routes the item directly to the first OutPort found
     * in accessible chunks, bypassing entity ticking entirely.
     */
    public void captureSimulationItem(ItemStack stack) {
        if (!simulating || stack.isEmpty()) return;
        LOGGER.info("[SimCapture] Routing {} x{} in {}", stack.getDescriptionId(), stack.getCount(), dimension.location());
        for (int cx = 0; cx < chunkSize; cx++) {
            for (int cz = 0; cz < chunkSize; cz++) {
                LevelChunk chunk = dimensionLevel.getChunk(cx, cz);
                for (var be : chunk.getBlockEntities().values()) {
                    if (be instanceof VoidOutPortEntity port) {
                        port.setItem(0, stack);
                        return;
                    }
                }
            }
        }
        LOGGER.warn("[SimCapture] No OutPort found for {} in {}", stack.getDescriptionId(), dimension.location());
    }

    public void stopSimulation() {
        simulating = false;
        forEachAccessibleChunk((cx, cz) -> {
            LevelChunk chunk = dimensionLevel.getChunk(cx, cz);
            chunk.getBlockEntities().values().forEach(be -> {
                if (be instanceof VoidInPortEntity port) port.stopSimulation();
                if (be instanceof VoidOutPortEntity port) port.stopSimulation();
            });
        });
        LOGGER.info("Stopped simulation on dimension {} after {} sim-ticks ({} real-ticks)",
                    dimension.location(), simTotalTicks, simRealTicks);
    }

    /**
     * Called from ServerTickEvent.Post at LOWEST priority every game tick.
     * Runs (simulationSpeed - 1) extra full level ticks so block entities (hoppers,
     * dispensers), random ticks (crops), scheduled ticks (redstone), and entity ticks
     * (spawners, mobs) all advance at the configured multiplier — always-on, not just
     * during tracked simulation runs.
     */
    public void tickSimulationExtra(MinecraftServer server) {
        int extraTicks = simulationSpeed - 1;
        // Loop simply does not execute when extraTicks == 0; tracking still runs below when simulating

        for (int i = 0; i < extraTicks; i++) {
            try {
                dimensionLevel.tick(() -> true);
            } catch (Exception e) {
                LOGGER.error("Error during extra tick on {}", dimension.location(), e);
            }
        }

        // Only advance tracking counters and send data while a formal simulation is active
        if (simulating) {
            simTotalTicks += simulationSpeed;
            simRealTicks++;
            if (simRealTicks % 20 == 0) {
                collectAndSendSimData(server);
            }
        }
    }

    private void collectAndSendSimData(MinecraftServer server) {
        Map<Item, Long> consumed = new HashMap<>();
        Map<Item, Long> produced = new HashMap<>();

        forEachAccessibleChunk((cx, cz) -> {
            LevelChunk chunk = dimensionLevel.getChunk(cx, cz);
            int beCount = chunk.getBlockEntities().size();
            int outPortCount = 0;
            for (var be : chunk.getBlockEntities().values()) {
                if (be instanceof VoidInPortEntity port) {
                    port.getSimulationConsumed().forEach((item, count) ->
                            consumed.merge(item, count, Long::sum));
                }
                if (be instanceof VoidOutPortEntity port) {
                    outPortCount++;
                    Map<Item, Long> portProduced = port.getSimulationProduced();
                    LOGGER.info("[Sim] OutPort at {} baseline={} total={} delta={}",
                            be.getBlockPos(),
                            port.getSimBaseline(),
                            port.getTotalInserted(),
                            portProduced);
                    portProduced.forEach((item, count) ->
                            produced.merge(item, count, Long::sum));
                }
            }
            LOGGER.info("[Sim] Chunk ({},{}) has {} block entities, {} OutPorts", cx, cz, beCount, outPortCount);
        });

        List<SimulationDataPacket.PortEntry> consumedList = new ArrayList<>();
        consumed.forEach((item, count) -> consumedList.add(
                new SimulationDataPacket.PortEntry(new ItemStack(item), count)));

        List<SimulationDataPacket.PortEntry> producedList = new ArrayList<>();
        produced.forEach((item, count) -> producedList.add(
                new SimulationDataPacket.PortEntry(new ItemStack(item), count)));

        LOGGER.info("[Sim] Sending data: simTicks={} produced={}", simTotalTicks, produced);

        SimulationDataPacket packet = new SimulationDataPacket(
                simTotalTicks, simRealTicks, simulationSpeed, consumedList, producedList);

        ServerPlayer ownerPlayer = server.getPlayerList().getPlayer(owner);
        if (ownerPlayer != null) {
            PacketDistributor.sendToPlayer(ownerPlayer, packet);
        } else {
            LOGGER.warn("[Sim] Owner player {} not online, cannot send sim data", owner);
        }
    }

    // -------------------------------------------------------------------------
    // Teleport in/out
    // -------------------------------------------------------------------------

    public void teleportIn(ServerPlayer player) {
        ServerLevel level = server.getLevel(dimension);
        if (level == null) throw new IllegalStateException("Void dimension not loaded!");

        DataAttachments.ReturnPositionData returnData = player.getData(DataAttachments.RETURN_POSITION);
        returnData.setReturnPosition(player.blockPosition());
        returnData.setReturnDimension(player.level().dimension());
        returnPositions.put(player, player.blockPosition());
        returnDimensions.put(player, player.level().dimension());

        if (player.getUUID().equals(owner)) {
            ownerReturnPosition = player.blockPosition();
            ownerReturnDimension = player.level().dimension();
        }

        // Center of accessible area, one block above the bedrock floor
        double cx = chunkSize * 8.0 - 0.5;
        double cz = chunkSize * 8.0 - 0.5;
        double y = level.getMinBuildHeight() + 2.0;
        player.teleportTo(level, cx, y, cz, null, player.getYRot(), player.getXRot());

        ensureWorldBorderForPlayer(player);
        setBuilderMode(player, true);
    }

    public void teleportOut(ServerPlayer player) {
        if (borderListeners.containsKey(player)) {
            dimensionLevel.getWorldBorder().removeListener(borderListeners.get(player));
            borderListeners.remove(player);
        }

        BlockPos returnPos = returnPositions.get(player);
        ResourceKey<Level> returnDimension = returnDimensions.get(player);

        if (returnPos == null || returnDimension == null) {
            DataAttachments.ReturnPositionData returnData = player.getData(DataAttachments.RETURN_POSITION);
            if (returnData.hasReturnData()) {
                returnPos = returnData.getReturnPosition();
                returnDimension = returnData.getReturnDimension();
                returnPositions.put(player, returnPos);
                returnDimensions.put(player, returnDimension);
            } else if (player.getUUID().equals(owner) && ownerReturnPosition != null) {
                returnPos = ownerReturnPosition;
                returnDimension = ownerReturnDimension;
            } else {
                LOGGER.warn("No return position for player {}, defaulting to overworld spawn", player.getName().getString());
                returnPos = new BlockPos(0, 64, 0);
                returnDimension = Level.OVERWORLD;
            }
        }

        ServerLevel returnLevel = server.getLevel(returnDimension);
        if (returnLevel == null) {
            returnLevel = server.getLevel(Level.OVERWORLD);
            if (returnLevel == null) throw new IllegalStateException("Overworld not loaded!");
        }

        if (!returnLevel.dimension().equals(player.level().dimension())) {
            player.changeDimension(new net.minecraft.world.level.portal.DimensionTransition(
                    returnLevel,
                    returnPos.getCenter(),
                    player.getDeltaMovement(),
                    player.getYRot(),
                    player.getXRot(),
                    net.minecraft.world.level.portal.DimensionTransition.DO_NOTHING
            ));
        } else {
            player.teleportTo(returnPos.getX(), returnPos.getY(), returnPos.getZ());
        }

        setBuilderMode(player, false);
        // Snapshot machine contents from all accessible chunks
        machine = Space.extractAllContents(dimensionLevel, chunkSize);
        returnPositions.remove(player);
        returnDimensions.remove(player);

        DataAttachments.ReturnPositionData returnData = player.getData(DataAttachments.RETURN_POSITION);
        returnData.clear();
    }

    public void restoreOwnerReturnPosition(ServerPlayer player) {
        ensureWorldBorderForPlayer(player);
        if (player.getUUID().equals(owner)) {
            DataAttachments.ReturnPositionData returnData = player.getData(DataAttachments.RETURN_POSITION);
            if (!returnData.hasReturnData() && ownerReturnPosition != null && ownerReturnDimension != null) {
                returnPositions.put(player, ownerReturnPosition);
                returnDimensions.put(player, ownerReturnDimension);
                returnData.setReturnPosition(ownerReturnPosition);
                returnData.setReturnDimension(ownerReturnDimension);
            } else if (returnData.hasReturnData()) {
                returnPositions.put(player, returnData.getReturnPosition());
                returnDimensions.put(player, returnData.getReturnDimension());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Builder mode (item isolation)
    // -------------------------------------------------------------------------

    private void setBuilderMode(ServerPlayer player, boolean enabled) {
        if (enabled) {
            CompoundTag playerData = new CompoundTag();
            player.saveWithoutId(playerData);
            player.getPersistentData().put("VoidSpaces_SavedPlayerData", playerData);
            player.getPersistentData().putString("VoidSpaces_OriginalGameMode",
                    player.gameMode.getGameModeForPlayer().getName());

            // Clear vanilla inventory
            player.getInventory().clearContent();

            // Clear modded inventories (handles Curios, Baubles, and similar)
            try {
                IItemHandler extraHandler = player.getCapability(
                        Capabilities.ItemHandler.ENTITY, null);
                if (extraHandler != null) {
                    for (int i = 0; i < extraHandler.getSlots(); i++) {
                        extraHandler.extractItem(i, Integer.MAX_VALUE, false);
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("Could not clear modded inventory capabilities for {}", player.getName().getString(), e);
            }

            player.setGameMode(GameType.CREATIVE);
            player.getAbilities().instabuild = true;
            player.getAbilities().flying = true;
            player.getAbilities().invulnerable = true;
            player.getAbilities().mayBuild = true;
            player.getPersistentData().putBoolean("VoidSpaces_InDimension", true);
        } else {
            double x = player.getX(), y = player.getY(), z = player.getZ();
            float yRot = player.getYRot(), xRot = player.getXRot();
            String originalGameMode = player.getPersistentData().getString("VoidSpaces_OriginalGameMode");

            player.getInventory().clearContent();

            if (player.getPersistentData().contains("VoidSpaces_SavedPlayerData")) {
                CompoundTag playerData = player.getPersistentData().getCompound("VoidSpaces_SavedPlayerData");
                player.load(playerData);
            }

            player.setPos(x, y, z);
            player.setYRot(yRot);
            player.setXRot(xRot);

            GameType gameType = GameType.SURVIVAL;
            if (!originalGameMode.isEmpty()) {
                gameType = GameType.byName(originalGameMode, GameType.SURVIVAL);
            }
            player.setGameMode(gameType);
            player.getAbilities().instabuild = false;
            player.getAbilities().flying = false;
            player.getAbilities().invulnerable = false;

            player.getPersistentData().remove("VoidSpaces_InDimension");
            player.getPersistentData().remove("VoidSpaces_OriginalGameMode");
        }
        player.onUpdateAbilities();
    }

    // -------------------------------------------------------------------------
    // World border
    // -------------------------------------------------------------------------

    public void ensureWorldBorderForPlayer(ServerPlayer player) {
        if (player.level() == dimensionLevel && player.connection != null) {
            WorldBorder border = dimensionLevel.getWorldBorder();
            LOGGER.info("Syncing world border for {} in {} (size={})",
                        player.getName().getString(), dimension.location(), border.getSize());
            addBorderListenerForPlayer(player);
            sendBorderPacketsToPlayer(player, border);
        }
    }

    private void sendBorderPacketsToPlayer(ServerPlayer player, WorldBorder border) {
        try {
            player.connection.send(new ClientboundInitializeBorderPacket(border));
            player.connection.send(new ClientboundSetBorderCenterPacket(border));
            player.connection.send(new ClientboundSetBorderSizePacket(border));
            player.connection.send(new ClientboundSetBorderWarningDelayPacket(border));
            player.connection.send(new ClientboundSetBorderWarningDistancePacket(border));
        } catch (Exception e) {
            LOGGER.error("Failed to send border packets to {}", player.getName().getString(), e);
        }
    }

    private void addBorderListenerForPlayer(ServerPlayer player) {
        if (borderListeners.containsKey(player)) {
            dimensionLevel.getWorldBorder().removeListener(borderListeners.get(player));
        }
        BorderChangeListener listener = new BorderChangeListener() {
            @Override public void onBorderSizeSet(WorldBorder b, double size) {}
            @Override public void onBorderSizeLerping(WorldBorder b, double o, double n, long t) {}
            @Override public void onBorderCenterSet(WorldBorder b, double x, double z) {}
            @Override public void onBorderSetWarningTime(WorldBorder b, int t) {}
            @Override public void onBorderSetWarningBlocks(WorldBorder b, int blocks) {}
            @Override public void onBorderSetDamagePerBlock(WorldBorder b, double dmg) {}
            @Override public void onBorderSetDamageSafeZOne(WorldBorder b, double zone) {}
        };
        dimensionLevel.getWorldBorder().addListener(listener);
        borderListeners.put(player, listener);
    }

    // -------------------------------------------------------------------------
    // Clear / save / load
    // -------------------------------------------------------------------------

    public void clear() {
        forEachAccessibleChunk((cx, cz) -> {
            ChunkPos root = new ChunkPos(cx, cz);
            int minX = root.getMinBlockX(), maxX = root.getMaxBlockX();
            int minZ = root.getMinBlockZ(), maxZ = root.getMaxBlockZ();
            int minY = dimensionLevel.getMinBuildHeight();
            int maxY = dimensionLevel.getMaxBuildHeight();

            BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int y = minY; y < maxY; y++) {
                        mutablePos.set(x, y, z);
                        dimensionLevel.setBlock(mutablePos,
                                y == minY ? Blocks.BEDROCK.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
            LevelChunk chunk = dimensionLevel.getChunk(root.x, root.z);
            chunk.getBlockEntities().keySet().forEach(dimensionLevel::removeBlockEntity);
            AABB bb = new AABB(root.getMinBlockX(), minY, root.getMinBlockZ(),
                               root.getMaxBlockX() + 1, maxY, root.getMaxBlockZ() + 1);
            dimensionLevel.getEntities().get(bb, entity -> entity.remove(Entity.RemovalReason.DISCARDED));
            chunk.setUnsaved(true);
        });
        machine = new Space.SpaceContents();
    }

    public void cleanupForSave() {
        for (Map.Entry<ServerPlayer, BorderChangeListener> entry : borderListeners.entrySet()) {
            try {
                dimensionLevel.getWorldBorder().removeListener(entry.getValue());
            } catch (Exception e) {
                LOGGER.warn("Failed to remove border listener during save cleanup", e);
            }
        }
        borderListeners.clear();
        try {
            dimensionLevel.save(null, true, false);
            LOGGER.info("Force-saved void dimension {} before world save", dimension.location());
        } catch (Exception e) {
            LOGGER.warn("Failed to force-save void dimension {}", dimension.location(), e);
        }
    }

    public CompoundTag saveReturnData() {
        CompoundTag tag = new CompoundTag();
        if (ownerReturnPosition != null) tag.putLong("ownerReturnPos", ownerReturnPosition.asLong());
        if (ownerReturnDimension != null) tag.putString("ownerReturnDim", ownerReturnDimension.location().toString());
        tag.putInt("chunkSize", chunkSize);
        return tag;
    }

    public void loadReturnData(CompoundTag tag) {
        if (tag.contains("ownerReturnPos")) ownerReturnPosition = BlockPos.of(tag.getLong("ownerReturnPos"));
        if (tag.contains("ownerReturnDim")) {
            try {
                ResourceLocation loc = ResourceLocation.parse(tag.getString("ownerReturnDim"));
                ownerReturnDimension = ResourceKey.create(Registries.DIMENSION, loc);
            } catch (Exception e) {
                LOGGER.warn("Failed to parse ownerReturnDim: {}", tag.getString("ownerReturnDim"));
            }
        }
        for (ServerPlayer player : dimensionLevel.players()) {
            if (player.connection != null) ensureWorldBorderForPlayer(player);
        }
    }

    // -------------------------------------------------------------------------
    // Helper: iterate accessible chunks
    // -------------------------------------------------------------------------

    @FunctionalInterface
    private interface ChunkConsumer {
        void accept(int cx, int cz);
    }

    private void forEachAccessibleChunk(ChunkConsumer consumer) {
        for (int cx = 0; cx < chunkSize; cx++) {
            for (int cz = 0; cz < chunkSize; cz++) {
                consumer.accept(cx, cz);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Dimension index management
    // -------------------------------------------------------------------------

    private static int getNextAvailableDimensionIndex(MinecraftServer server) {
        for (int i = 0; i < 1000; i++) {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.fromNamespaceAndPath(VoidSpaces.MODID, "voidspace_" + i));
            if (server.getLevel(key) == null) {
                LOGGER.info("Next available dimension index: {}", i);
                return i;
            }
        }
        LOGGER.warn("All 1000 dimension slots checked, using fallback");
        return dimensionCount;
    }

    // -------------------------------------------------------------------------
    // Level creation
    // -------------------------------------------------------------------------

    private LevelStem createLevel(MinecraftServer server, DimensionTypeOptions typeOption) {
        DynamicOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, server.registryAccess());
        Holder<DimensionType> typeHolder;
        ChunkGenerator chunkGenerator;

        switch (typeOption) {
            case FLAT: {
                FlatLevelGeneratorSettings flatSettings = new FlatLevelGeneratorSettings(
                        Optional.empty(),
                        server.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.THE_VOID),
                        List.of());
                flatSettings.getLayers().clear();
                flatSettings.getLayersInfo().clear();
                flatSettings.getLayersInfo().addFirst(new FlatLayerInfo(1, Blocks.BEDROCK));
                flatSettings.getLayers().addFirst(Blocks.BEDROCK.defaultBlockState());
                chunkGenerator = new FlatLevelSource(flatSettings);
                typeHolder = server.registryAccess().registryOrThrow(Registries.DIMENSION_TYPE)
                        .getHolderOrThrow(BuiltinDimensionTypes.OVERWORLD);
                break;
            }
            default:
                throw new IllegalArgumentException("Unsupported dimension type: " + typeOption);
        }
        return new LevelStem(typeHolder, chunkGenerator);
    }

    private ChunkGenerator copyChunkGenerator(ServerLevel level, DynamicOps<Tag> ops) {
        ChunkGenerator old = level.getChunkSource().getGenerator();
        return ChunkGenerator.CODEC.encodeStart(ops, old)
                .flatMap(nbt -> ChunkGenerator.CODEC.parse(ops, nbt))
                .getOrThrow(s -> new RuntimeException("Error copying chunk generator: " + s));
    }

    private enum DimensionTypeOptions { FLAT }

    // -------------------------------------------------------------------------
    // Reflection world border workaround (for save-restored plain ServerLevel)
    // -------------------------------------------------------------------------

    private void applyWorldBorderWorkaround(ServerLevel level) {
        try {
            DimensionalWorldBorder customBorder = new DimensionalWorldBorder(level, chunkSize);

            Field worldBorderField = null;
            Class<?> cls = level.getClass();
            while (cls != null && worldBorderField == null) {
                try {
                    worldBorderField = cls.getDeclaredField("worldBorder");
                } catch (NoSuchFieldException e) {
                    cls = cls.getSuperclass();
                }
            }
            if (worldBorderField != null) {
                worldBorderField.setAccessible(true);
                worldBorderField.set(level, customBorder);
                LOGGER.info("Replaced world border via reflection for dimension {}", level.dimension().location());
            } else {
                LOGGER.error("Could not find worldBorder field in ServerLevel hierarchy");
            }
        } catch (Exception e) {
            LOGGER.error("Failed to apply world border workaround", e);
        }
    }
}
