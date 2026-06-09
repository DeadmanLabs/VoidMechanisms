package com.deadman.voidspaces.block.entity;

import java.util.*;
import java.util.stream.IntStream;

import javax.annotation.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;

import com.deadman.voidspaces.block.VoidHopper;
import com.deadman.voidspaces.block.VoidDropper;
import com.deadman.voidspaces.block.VoidInPort;
import com.deadman.voidspaces.block.VoidOutPort;
import com.deadman.voidspaces.init.BlockEntities;
import com.deadman.voidspaces.init.MaterialAnalysisResultPacket;
import com.deadman.voidspaces.helpers.Dimensional;
import com.deadman.voidspaces.world.inventory.VoidEngineMenu;
import net.neoforged.neoforge.network.PacketDistributor;

public class EngineEntity extends RandomizableContainerBlockEntity implements WorldlyContainer {
    private static final Logger LOGGER = LoggerFactory.getLogger(EngineEntity.class);

    private UUID owner;
    private Dimensional dimension;
    private String savedDimensionId;
    private CompoundTag savedReturnData;
    private int chunkSize = 1; // 1-4 (1x1 to 4x4 chunks), locked once dimension is created

    public static final int TANK_CAPACITY = 20000;
    public BlockPos location;

    private static final int INPUT_SLOT_START = 0;
    private static final int INPUT_SLOT_COUNT = 9;
    private static final int OUTPUT_SLOT_START = 9;
    private static final int OUTPUT_SLOT_COUNT = 9;
    private NonNullList<ItemStack> stacks = NonNullList.withSize(18, ItemStack.EMPTY);
    private final SidedInvWrapper handler = new SidedInvWrapper(this, null);

    // Hopper/dropper cache to avoid O(16*height*16) scan every tick
    private List<VoidHopperEntity> cachedHoppers = null;
    private List<VoidDropperEntity> cachedDroppers = null;
    private static final int CACHE_REFRESH_INTERVAL = 20;
    private int cacheTimer = 0;

    // ContainerData indices: 0=chunkSize, 1=dimensionStatus, 2=simulationSpeed, 3=randomTickSpeed
    private final SimpleContainerData engineData = new SimpleContainerData(4);

    public EngineEntity(BlockPos pos, BlockState state) {
        super(BlockEntities.ENGINE_BLOCK_ENTITY.get(), pos, state);
        this.location = pos;
    }

    // -------------------------------------------------------------------------
    // Tick
    // -------------------------------------------------------------------------

    public static void tick(Level level, BlockPos pos, BlockState state, EngineEntity entity) {
        if (level.isClientSide || entity.dimension == null) return;

        // Refresh hopper/dropper cache periodically instead of scanning every tick
        if (--entity.cacheTimer <= 0) {
            entity.cachedHoppers = null;
            entity.cachedDroppers = null;
            entity.cacheTimer = CACHE_REFRESH_INTERVAL;
        }

        entity.transferItemsToVoidHoppers();
        entity.transferItemsFromVoidDroppers();

        // Keep ContainerData in sync
        entity.syncEngineData();
    }

    private void syncEngineData() {
        engineData.set(0, chunkSize);
        int status = 0;
        if (dimension != null) {
            status = dimension.isSimulating() ? 2 : 1;
        }
        engineData.set(1, status);
        engineData.set(2, dimension != null ? dimension.getSimulationSpeed() : 1);
        engineData.set(3, dimension != null ? dimension.getRandomTickSpeed() : 3);
    }

    // -------------------------------------------------------------------------
    // Item routing (VoidHopper/VoidDropper)
    // -------------------------------------------------------------------------

    private void transferItemsToVoidHoppers() {
        ServerLevel dimensionLevel = level.getServer().getLevel(dimension.dimension);
        if (dimensionLevel == null) return;
        for (int i = INPUT_SLOT_START; i < INPUT_SLOT_START + INPUT_SLOT_COUNT; i++) {
            ItemStack inputStack = stacks.get(i);
            if (inputStack.isEmpty()) continue;
            for (VoidHopperEntity hopper : getVoidHoppers(dimensionLevel)) {
                if (hopper.acceptsItem(inputStack) && addItemToVoidHopper(hopper, inputStack)) {
                    stacks.set(i, ItemStack.EMPTY);
                    setChanged();
                    break;
                }
            }
        }
    }

    private void transferItemsFromVoidDroppers() {
        ServerLevel dimensionLevel = level.getServer().getLevel(dimension.dimension);
        if (dimensionLevel == null) return;
        for (VoidDropperEntity dropper : getVoidDroppers(dimensionLevel)) {
            for (ItemStack type : dropper.getStoredItemTypes()) {
                for (int i = OUTPUT_SLOT_START; i < OUTPUT_SLOT_START + OUTPUT_SLOT_COUNT; i++) {
                    ItemStack out = stacks.get(i);
                    if (out.isEmpty()) {
                        ItemStack extracted = dropper.extractFromInfiniteStorage(type, type.getMaxStackSize());
                        if (!extracted.isEmpty()) { stacks.set(i, extracted); setChanged(); break; }
                    } else if (ItemStack.isSameItemSameComponents(out, type) && out.getCount() < out.getMaxStackSize()) {
                        int space = out.getMaxStackSize() - out.getCount();
                        ItemStack extracted = dropper.extractFromInfiniteStorage(type, space);
                        if (!extracted.isEmpty()) { out.grow(extracted.getCount()); setChanged(); break; }
                    }
                }
            }
        }
    }

    private List<VoidHopperEntity> getVoidHoppers(ServerLevel dimensionLevel) {
        if (cachedHoppers != null) return cachedHoppers;
        cachedHoppers = scanChunksForEntity(dimensionLevel, VoidHopperEntity.class);
        return cachedHoppers;
    }

    private List<VoidDropperEntity> getVoidDroppers(ServerLevel dimensionLevel) {
        if (cachedDroppers != null) return cachedDroppers;
        cachedDroppers = scanChunksForEntity(dimensionLevel, VoidDropperEntity.class);
        return cachedDroppers;
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> scanChunksForEntity(ServerLevel dimensionLevel, Class<T> entityClass) {
        List<T> result = new ArrayList<>();
        for (int cx = 0; cx < chunkSize; cx++) {
            for (int cz = 0; cz < chunkSize; cz++) {
                net.minecraft.world.level.chunk.LevelChunk chunk = dimensionLevel.getChunk(cx, cz);
                chunk.getBlockEntities().values().forEach(be -> {
                    if (entityClass.isInstance(be)) result.add(entityClass.cast(be));
                });
            }
        }
        return result;
    }

    private boolean addItemToVoidHopper(VoidHopperEntity hopper, ItemStack stack) {
        for (int i = 0; i < hopper.getContainerSize(); i++) {
            ItemStack slot = hopper.getItem(i);
            if (slot.isEmpty()) { hopper.setItem(i, stack.copy()); return true; }
            if (ItemStack.isSameItemSameComponents(slot, stack) && slot.getCount() < slot.getMaxStackSize()) {
                slot.grow(Math.min(stack.getCount(), slot.getMaxStackSize() - slot.getCount()));
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // GUI opening
    // -------------------------------------------------------------------------

    public void openEngineMenu(ServerPlayer player) {
        player.openMenu(this, buf -> buf.writeBlockPos(worldPosition));
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory) {
        return new VoidEngineMenu(containerId, playerInventory, this, engineData);
    }

    // -------------------------------------------------------------------------
    // Action handlers (called from EngineActionPacket)
    // -------------------------------------------------------------------------

    public void teleportIn(ServerPlayer player) {
        if (dimension == null && owner != null && level != null && level.getServer() != null) {
            LOGGER.info("Creating dimension on first entry for owner={} chunkSize={}", owner, chunkSize);
            dimension = new Dimensional(level.getServer(), owner, chunkSize);
        }
        if (dimension != null) {
            // Persist engine location so the wrapper can be restored after re-login inside the dimension
            player.getPersistentData().putLong("VoidSpaces_EnginePos", worldPosition.asLong());
            dimension.restoreOwnerReturnPosition(player);
            dimension.teleportIn(player);
        } else {
            LOGGER.warn("Cannot teleport player — dimension not available");
        }
    }

    public void analyzeContents(ServerPlayer requestingPlayer) {
        if (dimension == null) {
            requestingPlayer.sendSystemMessage(Component.literal("Dimension not yet created — enter it first."));
            return;
        }
        ServerLevel dimensionLevel = level.getServer().getLevel(dimension.dimension);
        if (dimensionLevel == null) {
            requestingPlayer.sendSystemMessage(Component.literal("Dimension not loaded. Enter it to load it."));
            return;
        }

        Map<Item, Long> blockCounts = new LinkedHashMap<>();
        Map<String, Integer> entityCounts = new LinkedHashMap<>();

        for (int cx = 0; cx < chunkSize; cx++) {
            for (int cz = 0; cz < chunkSize; cz++) {
                net.minecraft.world.level.chunk.LevelChunk chunk = dimensionLevel.getChunk(cx, cz);

                // Count blocks
                BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
                for (int x = cx * 16; x <= cx * 16 + 15; x++) {
                    for (int z = cz * 16; z <= cz * 16 + 15; z++) {
                        for (int y = dimensionLevel.getMinBuildHeight(); y < dimensionLevel.getMaxBuildHeight(); y++) {
                            mutable.set(x, y, z);
                            BlockState bs = chunk.getBlockState(mutable);
                            if (bs.isAir()) continue;
                            if (y == dimensionLevel.getMinBuildHeight()) continue; // skip bedrock floor
                            // Fluid source blocks — express as their bucket item
                            var fluidState = bs.getFluidState();
                            if (!fluidState.isEmpty() && fluidState.isSource()) {
                                Item bucketItem = fluidState.getType().getBucket();
                                if (bucketItem != Items.AIR) {
                                    blockCounts.merge(bucketItem, 1L, Long::sum);
                                }
                                continue;
                            }
                            Block block = bs.getBlock();
                            if (block instanceof VoidHopper || block instanceof VoidDropper
                                    || block instanceof VoidInPort || block instanceof VoidOutPort) continue;
                            Item item = block.asItem();
                            if (item == Items.AIR) continue;
                            blockCounts.merge(item, 1L, Long::sum);
                        }
                    }
                }

                // Count entities in this chunk
                AABB chunkBounds = new AABB(cx * 16, dimensionLevel.getMinBuildHeight(), cz * 16,
                                           cx * 16 + 16, dimensionLevel.getMaxBuildHeight(), cz * 16 + 16);
                dimensionLevel.getEntities().get(chunkBounds, entity -> {
                    if (entity instanceof ServerPlayer) return;
                    String typeName = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
                    entityCounts.merge(typeName, 1, Integer::sum);
                });
            }
        }

        // Build packet
        List<MaterialAnalysisResultPacket.BlockCount> blockList = new ArrayList<>();
        blockCounts.forEach((item, count) -> blockList.add(
                new MaterialAnalysisResultPacket.BlockCount(new ItemStack(item), count)));

        List<MaterialAnalysisResultPacket.EntityCount> entityList = new ArrayList<>();
        entityCounts.forEach((type, count) -> entityList.add(
                new MaterialAnalysisResultPacket.EntityCount(type, count)));

        PacketDistributor.sendToPlayer(requestingPlayer,
                new MaterialAnalysisResultPacket(blockList, entityList));
        LOGGER.info("Sent analysis to {} ({} block types, {} entity types)",
                    requestingPlayer.getName().getString(), blockList.size(), entityList.size());
    }

    public void startSimulation(int speed) {
        if (dimension == null) return;
        dimension.startSimulation(speed);
        setChanged();
    }

    public void stopSimulation() {
        if (dimension == null) return;
        dimension.stopSimulation();
        setChanged();
    }

    public void setSimulationSpeed(int speed) {
        if (dimension == null) return;
        dimension.setTickRate(speed); // change tick multiplier without restarting tracking
        setChanged();
    }

    public void setChunkSize(int size) {
        if (dimension != null) {
            LOGGER.warn("Cannot change chunk size once dimension is created");
            return;
        }
        chunkSize = Math.max(1, Math.min(4, size));
        setChanged();
    }

    public void setRandomTickSpeed(int speed) {
        if (dimension == null) return;
        dimension.setRandomTickSpeed(speed);
        setChanged();
    }

    // -------------------------------------------------------------------------
    // NBT
    // -------------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, Provider provider) {
        super.saveAdditional(tag, provider);
        tag.put("energyStorage", energyStorage.serializeNBT(provider));
        tag.putInt("chunkSize", chunkSize);
        if (owner != null) tag.putUUID("owner", owner);
        if (dimension != null) {
            tag.putString("dimensionId", dimension.dimension.location().toString());
            CompoundTag returnData = dimension.saveReturnData();
            if (!returnData.isEmpty()) tag.put("returnData", returnData);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.get("energyStorage") instanceof IntTag intTag) {
            energyStorage.deserializeNBT(provider, intTag);
        }
        if (tag.contains("chunkSize")) chunkSize = Math.max(1, Math.min(4, tag.getInt("chunkSize")));
        if (tag.hasUUID("owner")) owner = tag.getUUID("owner");
        if (tag.contains("dimensionId") && owner != null) {
            String dimId = tag.getString("dimensionId");
            savedDimensionId = dimId;
            if (tag.contains("returnData")) savedReturnData = tag.getCompound("returnData");
            if (level != null && level.getServer() != null) {
                try {
                    ResourceLocation loc = ResourceLocation.parse(dimId);
                    ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, loc);
                    dimension = new Dimensional(level.getServer(), owner, key, chunkSize);
                    if (savedReturnData != null) { dimension.loadReturnData(savedReturnData); savedReturnData = null; }
                    savedDimensionId = null;
                    LOGGER.info("Restored dimension {} chunkSize={}", key, chunkSize);
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("Invalid dimensionId: {}", dimId, e);
                    savedDimensionId = null;
                }
            }
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!level.isClientSide && owner != null && dimension == null && level.getServer() != null) {
            if (savedDimensionId != null) {
                try {
                    ResourceLocation loc = ResourceLocation.parse(savedDimensionId);
                    ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, loc);
                    dimension = new Dimensional(level.getServer(), owner, key, chunkSize);
                    if (savedReturnData != null) { dimension.loadReturnData(savedReturnData); savedReturnData = null; }
                    savedDimensionId = null;

                    // Restore return position for any players already in the dimension
                    for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
                        if (p.level().dimension().location().toString().equals(key.location().toString())) {
                            dimension.restoreOwnerReturnPosition(p);
                            level.getServer().execute(() -> dimension.ensureWorldBorderForPlayer(p));
                        }
                    }
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("Failed to restore dimension from savedDimensionId: {}", savedDimensionId, e);
                    savedDimensionId = null;
                    savedReturnData = null;
                }
            }
            setChanged();
        }
    }

    public void readAdditionalSaveData(CompoundTag tag, Provider provider) {
        loadAdditional(tag, provider);
    }

    // -------------------------------------------------------------------------
    // Network sync
    // -------------------------------------------------------------------------

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, Provider provider) {
        super.onDataPacket(net, pkt, provider);
        loadAdditional(pkt.getTag(), provider);
    }

    @Override
    public CompoundTag getUpdateTag(Provider lookupProvider) {
        CompoundTag tag = super.getUpdateTag(lookupProvider);
        saveAdditional(tag, lookupProvider);
        return tag;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public Dimensional getDimension() { return dimension; }
    public UUID getOwner() { return owner; }
    public int getChunkSize() { return chunkSize; }
    public boolean hasDimension() { return dimension != null; }
    public ContainerData getEngineData() { return engineData; }

    public ResourceKey<Level> getDimensionKey() {
        return dimension != null ? dimension.dimension : null;
    }

    public String getDimensionName() {
        return dimension != null ? dimension.dimension.location().toString() : "None";
    }

    public void setOwner(UUID uuid) {
        owner = uuid;
        LOGGER.info("VoidEngine owner set to: {}", uuid);
        // Dimension is created lazily on first player entry via the GUI
        setChanged();
    }

    // -------------------------------------------------------------------------
    // Container (WorldlyContainer)
    // -------------------------------------------------------------------------

    @Override
    public int getContainerSize() { return stacks.size(); }

    @Override
    public boolean isEmpty() {
        for (ItemStack s : stacks) if (!s.isEmpty()) return false;
        return true;
    }

    @Override
    public Component getDefaultName() { return Component.literal("void_engine"); }

    @Override
    public Component getDisplayName() { return Component.literal("Void Engine"); }

    @Override
    public NonNullList<ItemStack> getItems() { return stacks; }

    @Override
    protected void setItems(NonNullList<ItemStack> stacks) { this.stacks = stacks; }

    @Override
    public boolean canPlaceItem(int index, ItemStack stack) {
        return index >= INPUT_SLOT_START && index < INPUT_SLOT_START + INPUT_SLOT_COUNT
                && isItemAcceptedByVoidHopper(stack);
    }

    public boolean isItemAcceptedByVoidHopper(ItemStack stack) {
        if (dimension == null) return false;
        ServerLevel dl = level.getServer().getLevel(dimension.dimension);
        if (dl == null) return false;
        for (VoidHopperEntity h : getVoidHoppers(dl)) {
            if (h.acceptsItem(stack)) return true;
        }
        return false;
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        if (side == Direction.DOWN)
            return IntStream.range(OUTPUT_SLOT_START, OUTPUT_SLOT_START + OUTPUT_SLOT_COUNT).toArray();
        return IntStream.range(INPUT_SLOT_START, INPUT_SLOT_START + INPUT_SLOT_COUNT).toArray();
    }

    @Override
    public boolean canPlaceItemThroughFace(int index, ItemStack stack, @Nullable Direction direction) {
        return canPlaceItem(index, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction) {
        return index >= OUTPUT_SLOT_START && index < OUTPUT_SLOT_START + OUTPUT_SLOT_COUNT;
    }

    public SidedInvWrapper getItemHandler() { return handler; }

    // -------------------------------------------------------------------------
    // Energy
    // -------------------------------------------------------------------------

    private final EnergyStorage energyStorage = new EnergyStorage(200000, 200000, 200000, 0) {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int r = super.receiveEnergy(maxReceive, simulate);
            if (!simulate) { setChanged(); level.sendBlockUpdated(worldPosition, level.getBlockState(worldPosition), level.getBlockState(worldPosition), 2); }
            return r;
        }
        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            int r = super.extractEnergy(maxExtract, simulate);
            if (!simulate) { setChanged(); assert level != null; level.sendBlockUpdated(worldPosition, level.getBlockState(worldPosition), level.getBlockState(worldPosition), 2); }
            return r;
        }
    };

    public EnergyStorage getEnergyStorage() { return energyStorage; }

    public void updateEngineState(BlockPos pos, boolean isPowered) {}
}
