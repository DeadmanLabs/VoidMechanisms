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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * VoidInPort acts as an infinite source of configured item types inside a void dimension.
 * Players right-click to configure which items it provides (up to 5 types).
 * Adjacent hoppers/pipes can pull from it endlessly — the slot is never depleted.
 * During simulation, extraction counts are tracked for rate calculation.
 */
public class VoidInPortEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    private static final Logger LOGGER = LoggerFactory.getLogger(VoidInPortEntity.class);
    private static final int SLOT_COUNT = 5;

    // Items configured as infinite sources (templates only — not real items)
    private List<ItemStack> configuredTypes = new ArrayList<>();
    // Display slots (mirrors configuredTypes for container rendering)
    private NonNullList<ItemStack> displaySlots = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

    // Total items extracted per item type (for simulation tracking)
    private Map<Item, Long> totalExtracted = new HashMap<>();
    // Snapshot taken when simulation starts
    private Map<Item, Long> simBaseline = new HashMap<>();
    private boolean simulationActive = false;

    public VoidInPortEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntities.VOID_IN_PORT_ENTITY.get(), pos, blockState);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, VoidInPortEntity entity) {
        // No per-tick logic needed
    }

    @Override
    protected Component getDefaultName() {
        return Component.literal("Void In-Port");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return null; // No GUI for now; items configured via right-click
    }

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    public boolean isEmpty() {
        return configuredTypes.isEmpty();
    }

    /** Returns a copy of the configured template with max stack size — appears infinite to hoppers. */
    @Override
    public ItemStack getItem(int slot) {
        if (slot < configuredTypes.size()) {
            ItemStack template = configuredTypes.get(slot).copy();
            template.setCount(template.getMaxStackSize());
            return template;
        }
        return ItemStack.EMPTY;
    }

    /** Extracts items without depleting the configured type — tracks count for simulation. */
    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot >= configuredTypes.size() || amount <= 0) return ItemStack.EMPTY;
        ItemStack template = configuredTypes.get(slot);
        if (template.isEmpty()) return ItemStack.EMPTY;

        ItemStack extracted = template.copyWithCount(Math.min(amount, template.getMaxStackSize()));
        trackExtraction(extracted);
        return extracted;
    }

    /** Same as removeItem — slot is never actually depleted. */
    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return removeItem(slot, SLOT_COUNT > 0 ? configuredTypes.get(Math.min(slot, configuredTypes.size() - 1)).getMaxStackSize() : 0);
    }

    /** Not supported for external insertion — InPort is output-only to the dimension. */
    @Override
    public void setItem(int slot, ItemStack stack) {
        // InPort does not accept incoming items via container protocol
    }

    @Override
    public boolean canPlaceItem(int index, ItemStack stack) {
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        configuredTypes.clear();
        updateDisplaySlots();
        setChanged();
    }

    // --- WorldlyContainer: expose all configured slots for extraction from all faces ---

    @Override
    public int[] getSlotsForFace(Direction side) {
        return IntStream.range(0, configuredTypes.size()).toArray();
    }

    @Override
    public boolean canPlaceItemThroughFace(int index, ItemStack itemStack, @Nullable Direction direction) {
        return false; // No insertion
    }

    @Override
    public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction) {
        return index < configuredTypes.size() && !configuredTypes.get(index).isEmpty();
    }

    // --- Configuration (player right-click to add/remove item types) ---

    public void addConfiguredType(ItemStack stack) {
        if (stack.isEmpty() || configuredTypes.size() >= SLOT_COUNT) return;
        for (ItemStack existing : configuredTypes) {
            if (ItemStack.isSameItemSameComponents(existing, stack)) return;
        }
        ItemStack template = stack.copyWithCount(1);
        configuredTypes.add(template);
        updateDisplaySlots();
        setChanged();
        LOGGER.info("VoidInPort at {} configured with: {}", worldPosition, template.getDisplayName().getString());
    }

    public void removeConfiguredType(int index) {
        if (index >= 0 && index < configuredTypes.size()) {
            configuredTypes.remove(index);
            updateDisplaySlots();
            setChanged();
        }
    }

    public List<ItemStack> getConfiguredTypes() {
        return new ArrayList<>(configuredTypes);
    }

    private void updateDisplaySlots() {
        for (int i = 0; i < SLOT_COUNT; i++) {
            displaySlots.set(i, i < configuredTypes.size() ? configuredTypes.get(i).copy() : ItemStack.EMPTY);
        }
    }

    // --- Simulation tracking ---

    private void trackExtraction(ItemStack stack) {
        totalExtracted.merge(stack.getItem(), (long) stack.getCount(), Long::sum);
    }

    public void startSimulation() {
        simBaseline = new HashMap<>(totalExtracted);
        simulationActive = true;
    }

    public void stopSimulation() {
        simulationActive = false;
    }

    /** Returns items-consumed-since-simulation-start for each item type. */
    public Map<Item, Long> getSimulationConsumed() {
        Map<Item, Long> result = new HashMap<>();
        for (ItemStack type : configuredTypes) {
            Item item = type.getItem();
            long total = totalExtracted.getOrDefault(item, 0L);
            long baseline = simBaseline.getOrDefault(item, 0L);
            if (total > baseline) result.put(item, total - baseline);
        }
        return result;
    }

    // --- NBT ---

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag typeList = new ListTag();
        for (ItemStack stack : configuredTypes) {
            CompoundTag itemTag = new CompoundTag();
            stack.save(registries, itemTag);
            typeList.add(itemTag);
        }
        tag.put("ConfiguredTypes", typeList);

        ListTag extractedList = new ListTag();
        for (Map.Entry<Item, Long> entry : totalExtracted.entrySet()) {
            CompoundTag extractTag = new CompoundTag();
            ItemStack representative = new ItemStack(entry.getKey());
            representative.save(registries, extractTag);
            extractTag.putLong("count", entry.getValue());
            extractedList.add(extractTag);
        }
        tag.put("TotalExtracted", extractedList);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        configuredTypes.clear();
        ListTag typeList = tag.getList("ConfiguredTypes", 10);
        for (int i = 0; i < typeList.size(); i++) {
            ItemStack stack = ItemStack.parseOptional(registries, typeList.getCompound(i));
            if (!stack.isEmpty()) configuredTypes.add(stack);
        }
        updateDisplaySlots();

        totalExtracted.clear();
        ListTag extractedList = tag.getList("TotalExtracted", 10);
        for (int i = 0; i < extractedList.size(); i++) {
            CompoundTag extractTag = extractedList.getCompound(i);
            ItemStack representative = ItemStack.parseOptional(registries, extractTag);
            if (!representative.isEmpty()) {
                totalExtracted.put(representative.getItem(), extractTag.getLong("count"));
            }
        }
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.displaySlots = items;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.displaySlots;
    }

    public IItemHandler getItemHandler(@Nullable Direction side) {
        return new SidedInvWrapper(this, side);
    }
}
