package com.deadman.voidspaces.block.entity;

import java.util.*;
import java.util.stream.IntStream;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.core.registries.Registries;
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
    //dimension
    public static final int TANK_CAPACITY = 20000;
    //private FluidStack fluidStorage = new FluidStack(20000);
    public BlockPos location;

    private NonNullList<ItemStack> stacks = NonNullList.<ItemStack>withSize(1, ItemStack.EMPTY);
    private final SidedInvWrapper handler = new SidedInvWrapper(this, null);

    public EngineEntity(BlockPos pos, BlockState state) {
        super(BlockEntities.ENGINE_BLOCK_ENTITY.get(), pos, state);
        this.location = pos;
    }

    /**
     * Holds the raw persisted NBT from disk for deferred processing when the level is available.
     */
    private CompoundTag persistedDataTag;

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

    }

    //private int getTotalFluidVolume()

    @Override
    public void onLoad() {
        super.onLoad();
        if (!this.level.isClientSide) {
            this.setChanged();
            if (this.persistedDataTag != null) {
                readPersistedData(this.persistedDataTag);
                this.persistedDataTag = null;
            }
            if (this.dimension != null) {
                this.dimension.beginBorderSync();
                this.dimension.setWorldBorder();
                
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, Provider provider) {
        super.saveAdditional(tag, provider);
        tag.put("energyStorage", energyStorage.serializeNBT(provider));
        writePersistedData(tag);
    }

    public Dimensional getDimensional() {
        return this.dimension;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.get("energyStorage") instanceof IntTag intTag) {
            energyStorage.deserializeNBT(provider, intTag);
        }
        this.persistedDataTag = tag.copy();
        if (this.level != null && this.level.getServer() != null) {
            readPersistedData(tag);
        }
    }

    /**
     * Writes the dimension and return location into the given tag for persistence.
     */
    public void writePersistedData(CompoundTag tag) {
        if (this.owner != null) {
            tag.putUUID("owner", this.owner);
        }
        if (this.dimension != null) {
            tag.putString("dimension", this.dimension.dimension.location().toString());
            CompoundTag returnTag = this.dimension.serializeReturnLocation();
            if (returnTag != null) {
                tag.put("returnLocation", returnTag);
            }
            CompoundTag cageTag = this.dimension.serializeCageSettings();
            tag.put("cage", cageTag);
        }
    }


    /**
     * Reads the persisted dimension and return location from the given tag.
     */
    public void readPersistedData(CompoundTag tag) {
        if (tag.contains("owner")) {
            this.owner = tag.getUUID("owner");
        }
        if (this.level != null && this.level.getServer() != null
            && tag.contains("dimension") && this.owner != null) {
            String dimStr = tag.getString("dimension");
            ResourceKey<Level> dimKey = ResourceKey.create(
                Registries.DIMENSION,
                ResourceLocation.parse(dimStr)
            );
            Dimensional existing = Dimensional.getForDimension(dimKey);
            if (existing != null) {
                this.dimension = existing;
            } else {
                this.dimension = new Dimensional(this.level.getServer(), this.owner, dimKey);
            }
            if (tag.contains("returnLocation")) {
                CompoundTag returnTag = tag.getCompound("returnLocation");
                String origStr = returnTag.getString("origDim");
                ResourceKey<Level> origKey = ResourceKey.create(
                    Registries.DIMENSION,
                    ResourceLocation.parse(origStr)
                );
                BlockPos origPos = new BlockPos(
                    returnTag.getInt("x"),
                    returnTag.getInt("y"),
                    returnTag.getInt("z")
                );
                this.dimension.restoreReturnLocation(this.owner, origKey, origPos);
            }
            if (tag.contains("cage")) {
                CompoundTag cageTag = tag.getCompound("cage");
                this.dimension.restoreCageSettings(cageTag);
            }
        }
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
        return true;
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return IntStream.range(0, this.getContainerSize()).toArray();
    }

    @Override
    public boolean canPlaceItemThroughFace(int index, ItemStack stack, @Nullable Direction direction) {
        return this.canPlaceItem(index, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction) {
        if (index == 0)
            return false;
        if (index == 1)
            return false;
        return true;
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
