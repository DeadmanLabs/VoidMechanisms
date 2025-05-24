package com.deadman.voidspaces.block.entity;

import java.util.*;
import java.util.stream.IntStream;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.Unpooled;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;

import com.deadman.voidspaces.init.BlockEntities;
import com.deadman.voidspaces.helpers.Dimensional;

public class EngineEntity extends RandomizableContainerBlockEntity implements WorldlyContainer {
    private static final Logger LOGGER = LoggerFactory.getLogger(EngineEntity.class);
    private UUID owner;
    private Dimensional dimension;
    private String savedDimensionId; // Temporary storage for dimension ID during load
    private CompoundTag savedReturnData; // Temporary storage for return data during load
    //dimension
    public static final int TANK_CAPACITY = 20000;
    //private FluidStack fluidStorage = new FluidStack(20000);
    public BlockPos location;

    // 9 input slots + 9 output slots = 18 total slots
    private NonNullList<ItemStack> stacks = NonNullList.<ItemStack>withSize(18, ItemStack.EMPTY);
    public static final int INPUT_SLOT_START = 0;
    public static final int INPUT_SLOT_COUNT = 9;
    public static final int OUTPUT_SLOT_START = 9;
    public static final int OUTPUT_SLOT_COUNT = 9;
    private final SidedInvWrapper handler = new SidedInvWrapper(this, null);

    public EngineEntity(BlockPos pos, BlockState state) {
        super(BlockEntities.ENGINE_BLOCK_ENTITY.get(), pos, state);
        this.location = pos;
    }

    public void setOwner(UUID uuid) {
        this.owner = uuid;
        LOGGER.info("Owner set to: {}", uuid);
        if (this.level.getServer() != null) {
            this.dimension = new Dimensional(this.level.getServer(), uuid);
            LOGGER.info("Wrote new dimension: {}", this.dimension.dimension.toString());
            this.dimension.teleportIn(this.level.getServer().getPlayerList().getPlayer(this.owner));
        } else {
            LOGGER.info("Level is not serverside!");
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, EngineEntity blockEntity) {
        if (!level.isClientSide && blockEntity.dimension != null) {
            // Transfer items from input slots to VoidHoppers in the dimension
            blockEntity.transferItemsToVoidHoppers();
            
            // Transfer items from VoidDroppers to output slots
            blockEntity.transferItemsFromVoidDroppers();
        }
    }
    
    private void transferItemsToVoidHoppers() {
        ServerLevel dimensionLevel = this.level.getServer().getLevel(this.dimension.dimension);
        if (dimensionLevel == null) return;
        
        // Check input slots for items to transfer
        for (int i = INPUT_SLOT_START; i < INPUT_SLOT_START + INPUT_SLOT_COUNT; i++) {
            ItemStack inputStack = this.stacks.get(i);
            if (!inputStack.isEmpty()) {
                // Find a VoidHopper in the dimension that wants this item
                List<VoidHopperEntity> voidHoppers = findVoidHoppersInDimension(dimensionLevel);
                for (VoidHopperEntity hopper : voidHoppers) {
                    if (hopper.acceptsItem(inputStack)) {
                        // Transfer the item to the hopper's visual inventory
                        if (addItemToVoidHopper(hopper, inputStack)) {
                            this.stacks.set(i, ItemStack.EMPTY);
                            this.setChanged();
                            LOGGER.info("Transferred {} to VoidHopper at {}", inputStack.getDisplayName().getString(), hopper.getBlockPos());
                            break;
                        }
                    }
                }
            }
        }
    }
    
    private void transferItemsFromVoidDroppers() {
        ServerLevel dimensionLevel = this.level.getServer().getLevel(this.dimension.dimension);
        if (dimensionLevel == null) return;
        
        List<VoidDropperEntity> voidDroppers = findVoidDroppersInDimension(dimensionLevel);
        for (VoidDropperEntity dropper : voidDroppers) {
            // Try to extract items from dropper to output slots
            List<ItemStack> availableTypes = dropper.getStoredItemTypes();
            for (ItemStack type : availableTypes) {
                // Find an empty output slot
                for (int i = OUTPUT_SLOT_START; i < OUTPUT_SLOT_START + OUTPUT_SLOT_COUNT; i++) {
                    ItemStack outputStack = this.stacks.get(i);
                    if (outputStack.isEmpty()) {
                        // Extract up to a full stack
                        ItemStack extracted = dropper.extractFromInfiniteStorage(type, type.getMaxStackSize());
                        if (!extracted.isEmpty()) {
                            this.stacks.set(i, extracted);
                            this.setChanged();
                            LOGGER.info("Extracted {} from VoidDropper at {}", extracted.getDisplayName().getString(), dropper.getBlockPos());
                            break;
                        }
                    } else if (ItemStack.isSameItemSameComponents(outputStack, type) && outputStack.getCount() < outputStack.getMaxStackSize()) {
                        // Add to existing stack
                        int spaceLeft = outputStack.getMaxStackSize() - outputStack.getCount();
                        ItemStack extracted = dropper.extractFromInfiniteStorage(type, spaceLeft);
                        if (!extracted.isEmpty()) {
                            outputStack.grow(extracted.getCount());
                            this.setChanged();
                            LOGGER.info("Added {} to existing stack in output slot", extracted.getDisplayName().getString());
                            break;
                        }
                    }
                }
            }
        }
    }
    
    private List<VoidHopperEntity> findVoidHoppersInDimension(ServerLevel dimensionLevel) {
        List<VoidHopperEntity> hoppers = new ArrayList<>();
        ChunkPos chunkPos = new ChunkPos(0, 0);
        
        for (int x = chunkPos.x * 16; x <= chunkPos.x * 16 + 15; x++) {
            for (int y = dimensionLevel.getMinBuildHeight(); y < dimensionLevel.getMaxBuildHeight(); y++) {
                for (int z = chunkPos.z * 16; z <= chunkPos.z * 16 + 15; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (dimensionLevel.getBlockEntity(pos) instanceof VoidHopperEntity hopper) {
                        hoppers.add(hopper);
                    }
                }
            }
        }
        return hoppers;
    }
    
    private List<VoidDropperEntity> findVoidDroppersInDimension(ServerLevel dimensionLevel) {
        List<VoidDropperEntity> droppers = new ArrayList<>();
        ChunkPos chunkPos = new ChunkPos(0, 0);
        
        for (int x = chunkPos.x * 16; x <= chunkPos.x * 16 + 15; x++) {
            for (int y = dimensionLevel.getMinBuildHeight(); y < dimensionLevel.getMaxBuildHeight(); y++) {
                for (int z = chunkPos.z * 16; z <= chunkPos.z * 16 + 15; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (dimensionLevel.getBlockEntity(pos) instanceof VoidDropperEntity dropper) {
                        droppers.add(dropper);
                    }
                }
            }
        }
        return droppers;
    }
    
    private boolean addItemToVoidHopper(VoidHopperEntity hopper, ItemStack stack) {
        // Try to add to the hopper's visual inventory
        for (int i = 0; i < hopper.getContainerSize(); i++) {
            ItemStack slotStack = hopper.getItem(i);
            if (slotStack.isEmpty()) {
                hopper.setItem(i, stack.copy());
                return true;
            } else if (ItemStack.isSameItemSameComponents(slotStack, stack) && slotStack.getCount() < slotStack.getMaxStackSize()) {
                int spaceLeft = slotStack.getMaxStackSize() - slotStack.getCount();
                int toAdd = Math.min(spaceLeft, stack.getCount());
                slotStack.grow(toAdd);
                return true;
            }
        }
        return false;
    }

    //private int getTotalFluidVolume()

    @Override
    public void onLoad() {
        super.onLoad();
        if (!this.level.isClientSide) {
            this.setChanged();
            // Initialize dimension if we have owner but no dimension (restored from NBT)
            if (this.owner != null && this.dimension == null && this.level.getServer() != null) {
                if (this.savedDimensionId != null) {
                    // Restore existing dimension from saved ID
                    LOGGER.info("Restoring existing dimension on load: {}", this.savedDimensionId);
                    try {
                        ResourceLocation dimensionLocation = ResourceLocation.parse(this.savedDimensionId);
                        ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, dimensionLocation);
                        this.dimension = new Dimensional(this.level.getServer(), this.owner, dimensionKey);
                        
                        // Load return data if we saved it
                        if (this.savedReturnData != null) {
                            this.dimension.loadReturnData(this.savedReturnData);
                            this.savedReturnData = null;
                        }
                        
                        this.savedDimensionId = null; // Clear the temporary storage
                        LOGGER.info("Successfully restored dimension from saved ID: {}", dimensionKey);
                        
                        // Check if the owner is already in this dimension and restore their return position
                        ServerPlayer ownerPlayer = this.level.getServer().getPlayerList().getPlayer(this.owner);
                        if (ownerPlayer != null && ownerPlayer.level().dimension().location().toString().equals(dimensionKey.location().toString())) {
                            this.dimension.restoreOwnerReturnPosition(ownerPlayer);
                            LOGGER.info("Player {} was already in dimension on restore, restored return position", ownerPlayer.getName().getString());
                        }
                        
                        // Also check for any other players who might be in this dimension
                        for (ServerPlayer player : this.level.getServer().getPlayerList().getPlayers()) {
                            if (player.level().dimension().location().toString().equals(dimensionKey.location().toString())) {
                                this.dimension.restoreOwnerReturnPosition(player);
                                LOGGER.info("Restored return position for player {} found in dimension on reload", player.getName().getString());
                                
                                // Also ensure world border is set up for this player
                                this.level.getServer().execute(() -> {
                                    this.dimension.ensureWorldBorderForPlayer(player);
                                    LOGGER.info("Ensured world border for player {} during dimension restoration", player.getName().getString());
                                });
                            }
                        }
                    } catch (IllegalArgumentException e) {
                        LOGGER.warn("Failed to restore dimension from saved ID: {}", this.savedDimensionId, e);
                        this.savedDimensionId = null;
                        this.savedReturnData = null;
                    }
                } else {
                    // Create new dimension (fresh engine placement)
                    LOGGER.info("Creating new dimension on load for owner: {}", this.owner);
                    this.dimension = new Dimensional(this.level.getServer(), this.owner);
                }
            }
        }
    }

    public Dimensional getDimension() {
        return this.dimension;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, Provider provider) {
        super.saveAdditional(tag, provider);
        tag.put("energyStorage", energyStorage.serializeNBT(provider));
        if (this.dimension != null) {
            tag.putString("dimensionId", this.dimension.dimension.location().toString());
        }
        if (this.owner != null) {
            tag.putUUID("owner", this.owner);
        }
        if (this.dimension != null) {
            CompoundTag returnData = this.dimension.saveReturnData();
            if (!returnData.isEmpty()) {
                tag.put("returnData", returnData);
            }
        }
        LOGGER.info("Saved EngineEntity NBT - Owner: {}, DimensionId: {}", this.owner, this.dimension != null ? this.dimension.dimension.location().toString() : "null");
    }

    @Override
    protected void loadAdditional(CompoundTag tag, Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.get("energyStorage") instanceof IntTag intTag) {
            energyStorage.deserializeNBT(provider, intTag);
        }
        if (tag.hasUUID("owner")) {
            this.owner = tag.getUUID("owner");
            LOGGER.info("Loaded owner from NBT: {}", this.owner);
        }
        if (tag.contains("dimensionId") && this.owner != null) {
            String dimensionIdString = tag.getString("dimensionId");
            this.savedDimensionId = dimensionIdString; // Save for onLoad()
            LOGGER.info("Loading existing dimension from NBT: {}", dimensionIdString);
            try {
                ResourceLocation dimensionLocation = ResourceLocation.parse(dimensionIdString);
                ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, dimensionLocation);
                if (this.level != null && this.level.getServer() != null) {
                    this.dimension = new Dimensional(this.level.getServer(), this.owner, dimensionKey);
                    // Load return data if available
                    if (tag.contains("returnData")) {
                        this.dimension.loadReturnData(tag.getCompound("returnData"));
                    }
                    this.savedDimensionId = null; // Clear since we successfully created the dimension
                    this.savedReturnData = null;
                    LOGGER.info("Successfully restored dimension: {}", dimensionKey);
                } else {
                    // Save return data for later
                    if (tag.contains("returnData")) {
                        this.savedReturnData = tag.getCompound("returnData");
                    }
                    LOGGER.warn("Server in load is null, will restore in onLoad()");
                }
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Invalid dimensionId: {}", dimensionIdString, e);
                this.savedDimensionId = null;
            }
        }
        // Don't create a new dimension here if we don't have an existing one
        // That should only happen when a player places the block
    }

    public void readAdditionalSaveData(CompoundTag tag, Provider provider) {
        this.loadAdditional(tag, provider);
    }

    public void teleportIn(ServerPlayer player) {
        if (this.dimension != null) {
            // Restore return position for owner if available
            this.dimension.restoreOwnerReturnPosition(player);
            this.dimension.teleportIn(player);
        } else {
            LOGGER.warn("Attempted to teleport player into null dimension!");
        }
    }

    public UUID getOwner() {
        return this.owner;
    }

    public ResourceKey<Level> getDimensionKey() {
        return this.dimension != null ? this.dimension.dimension : null;
    }

    public String getDimensionName() {
        return this.dimension != null ? this.dimension.dimension.location().toString() : "None";
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, Provider provider) {
        super.onDataPacket(net, pkt, provider);
        this.loadAdditional(pkt.getTag(), provider);
    }

    @Override
    public CompoundTag getUpdateTag(Provider lookupProvider) {
        CompoundTag tag = super.getUpdateTag(lookupProvider);
        this.saveAdditional(tag, lookupProvider);
        return tag;
    }

    @Override
    public int getContainerSize() {
        return stacks.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack itemstack : this.stacks) {
            if (!itemstack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Component getDefaultName() {
        return Component.literal("void_engine");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory) {
        //return new ScreenMenu(id, inventory, new FriendlyByteBuf(Unpooed.buffer()).writeBlockPos(this.worldPosition));
        return null;
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Void Engine");
    }

    @Override
    public NonNullList<ItemStack> getItems() {
        return this.stacks;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> stacks) {
        this.stacks = stacks;
    }

    @Override
    public boolean canPlaceItem(int index, ItemStack stack) {
        // Only allow items in input slots, and only if they match the void hopper filter
        if (index >= INPUT_SLOT_START && index < INPUT_SLOT_START + INPUT_SLOT_COUNT) {
            return isItemAcceptedByVoidHopper(stack);
        }
        return false; // Don't allow items in output slots
    }
    
    public boolean isItemAcceptedByVoidHopper(ItemStack stack) {
        if (this.dimension == null) return false;
        
        // Check all VoidHoppers in the dimension to see if any accept this item
        ServerLevel dimensionLevel = this.level.getServer().getLevel(this.dimension.dimension);
        if (dimensionLevel == null) return false;
        
        // Scan chunk 0 for VoidHoppers
        ChunkPos chunkPos = new ChunkPos(0, 0);
        for (int x = chunkPos.x * 16; x <= chunkPos.x * 16 + 15; x++) {
            for (int y = dimensionLevel.getMinBuildHeight(); y < dimensionLevel.getMaxBuildHeight(); y++) {
                for (int z = chunkPos.z * 16; z <= chunkPos.z * 16 + 15; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (dimensionLevel.getBlockEntity(pos) instanceof VoidHopperEntity voidHopper) {
                        if (voidHopper.acceptsItem(stack)) {
                            return true;
                        }
                    }
                }
            }
        }
        
        return false; // No VoidHopper accepts this item
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        // Return input slots for insertion, output slots for extraction
        if (side == Direction.UP || side == Direction.NORTH || side == Direction.SOUTH || side == Direction.EAST || side == Direction.WEST) {
            // Input slots for insertion
            return IntStream.range(INPUT_SLOT_START, INPUT_SLOT_START + INPUT_SLOT_COUNT).toArray();
        } else if (side == Direction.DOWN) {
            // Output slots for extraction
            return IntStream.range(OUTPUT_SLOT_START, OUTPUT_SLOT_START + OUTPUT_SLOT_COUNT).toArray();
        }
        return new int[0];
    }

    @Override
    public boolean canPlaceItemThroughFace(int index, ItemStack stack, @Nullable Direction direction) {
        return this.canPlaceItem(index, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction) {
        // Only allow extraction from output slots
        return index >= OUTPUT_SLOT_START && index < OUTPUT_SLOT_START + OUTPUT_SLOT_COUNT;
    }

    public SidedInvWrapper getItemHandler() {
        return handler;
    }

    private final EnergyStorage energyStorage = new EnergyStorage(200000, 200000, 200000, 0) {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int retval = super.receiveEnergy(maxReceive, simulate);
            if (!simulate) {
                setChanged();
                level.sendBlockUpdated(worldPosition, level.getBlockState(worldPosition), level.getBlockState(worldPosition), 2);
            }
            return retval;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            int retval = super.extractEnergy(maxExtract, simulate);
            if (!simulate) {
                setChanged();
                assert level != null;
                level.sendBlockUpdated(worldPosition, level.getBlockState(worldPosition), level.getBlockState(worldPosition), 2);
            }
            return retval;
        }
    };

    public EnergyStorage getEnergyStorage() {
        return energyStorage;
    }

    public void updateEngineState(BlockPos pos, boolean isPowered) {

    }
}