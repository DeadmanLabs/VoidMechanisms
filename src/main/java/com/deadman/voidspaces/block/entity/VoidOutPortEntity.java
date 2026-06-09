package com.deadman.voidspaces.block.entity;

import com.deadman.voidspaces.init.BlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * VoidOutPort acts as an infinite sink for items produced by machines in a void dimension.
 * Adjacent hoppers/pipes can push items into it endlessly.
 * During simulation, insertion counts are tracked for rate calculation.
 */
public class VoidOutPortEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    private static final Logger LOGGER = LoggerFactory.getLogger(VoidOutPortEntity.class);
    // One logical slot — always appears empty so hoppers always have room to insert
    private static final int SLOT_COUNT = 1;

    // Total items inserted per item type
    private Map<Item, Long> totalInserted = new HashMap<>();
    // Snapshot taken when simulation starts
    private Map<Item, Long> simBaseline = new HashMap<>();
    private boolean simulationActive = false;

    // Dummy single slot (always empty — items immediately go to totalInserted)
    private NonNullList<ItemStack> slots = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

    public VoidOutPortEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntities.VOID_OUT_PORT_ENTITY.get(), pos, blockState);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, VoidOutPortEntity entity) {
        if (level.isClientSide) return;
        if (level.getGameTime() % 8 != 0) return;
        // Vacuum nearby item entities — catches drops regardless of entity ticking level
        AABB area = new AABB(pos.getX() - 1, pos.getY(), pos.getZ() - 1,
                             pos.getX() + 2, pos.getY() + 8, pos.getZ() + 2);
        List<ItemEntity> nearby = level.getEntitiesOfClass(ItemEntity.class, area);
        for (ItemEntity ie : nearby) {
            if (!ie.isAlive()) continue;
            entity.setItem(0, ie.getItem().copy());
            ie.discard();
        }
    }

    @Override
    protected Component getDefaultName() {
        return Component.literal("Void Out-Port");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return null;
    }

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    public boolean isEmpty() {
        return true; // Always appears empty — infinite capacity
    }

    @Override
    public ItemStack getItem(int slot) {
        return ItemStack.EMPTY; // Always appears empty so hoppers keep pushing
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return ItemStack.EMPTY; // Cannot extract from OutPort
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ItemStack.EMPTY;
    }

    /** Accepts all inserted items into infinite tracking storage. */
    @Override
    public void setItem(int slot, ItemStack stack) {
        if (!stack.isEmpty()) {
            totalInserted.merge(stack.getItem(), (long) stack.getCount(), Long::sum);
            LOGGER.info("VoidOutPort at {} received {} x{} (total now: {})", worldPosition,
                        stack.getDisplayName().getString(), stack.getCount(), totalInserted);
            setChanged();
        }
    }

    @Override
    public boolean canPlaceItem(int index, ItemStack stack) {
        return true; // Accept all items
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        totalInserted.clear();
        setChanged();
    }

    // --- WorldlyContainer: accept insertions from all faces, no extraction ---

    @Override
    public int[] getSlotsForFace(Direction side) {
        return new int[]{0}; // Expose slot 0 for insertion from all faces
    }

    @Override
    public boolean canPlaceItemThroughFace(int index, ItemStack itemStack, @Nullable Direction direction) {
        return true; // Accept all insertions
    }

    @Override
    public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction) {
        return false; // No extraction allowed
    }

    // --- Simulation tracking ---

    public void startSimulation() {
        simBaseline = new HashMap<>(totalInserted);
        simulationActive = true;
    }

    public void stopSimulation() {
        simulationActive = false;
    }

    /** Returns items-produced-since-simulation-start for each item type. */
    public Map<Item, Long> getSimulationProduced() {
        Map<Item, Long> result = new HashMap<>();
        for (Map.Entry<Item, Long> entry : totalInserted.entrySet()) {
            long baseline = simBaseline.getOrDefault(entry.getKey(), 0L);
            long delta = entry.getValue() - baseline;
            if (delta > 0) result.put(entry.getKey(), delta);
        }
        return result;
    }

    public Map<Item, Long> getTotalInserted() {
        return new HashMap<>(totalInserted);
    }

    public Map<Item, Long> getSimBaseline() {
        return new HashMap<>(simBaseline);
    }

    // --- NBT ---

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag insertedList = new ListTag();
        for (Map.Entry<Item, Long> entry : totalInserted.entrySet()) {
            CompoundTag insertTag = new CompoundTag();
            ItemStack representative = new ItemStack(entry.getKey());
            representative.save(registries, insertTag);
            insertTag.putLong("count", entry.getValue());
            insertedList.add(insertTag);
        }
        tag.put("TotalInserted", insertedList);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        totalInserted.clear();
        ListTag insertedList = tag.getList("TotalInserted", 10);
        for (int i = 0; i < insertedList.size(); i++) {
            CompoundTag insertTag = insertedList.getCompound(i);
            ItemStack representative = ItemStack.parseOptional(registries, insertTag);
            if (!representative.isEmpty()) {
                totalInserted.put(representative.getItem(), insertTag.getLong("count"));
            }
        }
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.slots = items;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.slots;
    }

    public IItemHandler getItemHandler(@Nullable Direction side) {
        return new SidedInvWrapper(this, side);
    }
}
