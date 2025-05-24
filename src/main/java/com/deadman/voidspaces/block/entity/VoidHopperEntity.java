package com.deadman.voidspaces.block.entity;

import com.deadman.voidspaces.init.BlockEntities;
import com.deadman.voidspaces.world.inventory.VoidHopperMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class VoidHopperEntity extends BaseContainerBlockEntity {
    private static final Logger LOGGER = LoggerFactory.getLogger(VoidHopperEntity.class);
    
    // This stores the "template" items that define what can be inserted
    private List<ItemStack> filterItems = new ArrayList<>();
    
    // This is the visual inventory (5 slots like a hopper)
    private NonNullList<ItemStack> items = NonNullList.withSize(5, ItemStack.EMPTY);

    public VoidHopperEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntities.VOID_HOPPER_BLOCK_ENTITY.get(), pos, blockState);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, VoidHopperEntity blockEntity) {
        // VoidHopper doesn't need ticking for now
    }

    @Override
    protected Component getDefaultName() {
        return Component.literal("Void Hopper");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        LOGGER.info("Creating VoidHopper menu with containerId: {}", containerId);
        return new VoidHopperMenu(containerId, inventory, this, this);
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return ContainerHelper.removeItem(items, slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        items.clear();
        filterItems.clear();
        setChanged();
    }

    // Custom method to get items for dropping (returns empty since these are just filter templates)
    public NonNullList<ItemStack> getItemsForDropping() {
        // Return empty list since filter items are just templates, not real inventory
        return NonNullList.withSize(5, ItemStack.EMPTY);
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    // Methods for managing filter items (the transparent templates)
    public List<ItemStack> getFilterItems() {
        return new ArrayList<>(filterItems);
    }

    public void addFilterItem(ItemStack stack) {
        if (!stack.isEmpty()) {
            // Check if this item type is already in the filter
            for (ItemStack existing : filterItems) {
                if (ItemStack.isSameItemSameComponents(existing, stack)) {
                    return; // Already have this item type
                }
            }
            // Add a single item as template (ignore stack size)
            ItemStack template = stack.copy();
            template.setCount(1);
            filterItems.add(template);
            updateDisplaySlots();
            setChanged();
            LOGGER.info("Added filter item: {} to void hopper at {}", template.getDisplayName().getString(), worldPosition);
        }
    }

    public void removeFilterItem(int index) {
        if (index >= 0 && index < filterItems.size()) {
            ItemStack removed = filterItems.remove(index);
            updateDisplaySlots();
            setChanged();
            LOGGER.info("Removed filter item: {} from void hopper at {}", removed.getDisplayName().getString(), worldPosition);
        }
    }

    public void clearFilterItems() {
        filterItems.clear();
        updateDisplaySlots();
        setChanged();
        LOGGER.info("Cleared all filter items from void hopper at {}", worldPosition);
    }
    
    private void updateDisplaySlots() {
        // Clear all display slots first
        for (int i = 0; i < items.size(); i++) {
            items.set(i, ItemStack.EMPTY);
        }
        
        // Show filter items in the display slots
        for (int i = 0; i < Math.min(filterItems.size(), items.size()); i++) {
            items.set(i, filterItems.get(i).copy());
        }
    }

    public boolean acceptsItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        
        for (ItemStack filter : filterItems) {
            if (ItemStack.isSameItemSameComponents(filter, stack)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        
        // Save filter items
        ListTag filterList = new ListTag();
        for (ItemStack filterItem : filterItems) {
            CompoundTag itemTag = new CompoundTag();
            filterItem.save(registries, itemTag);
            filterList.add(itemTag);
        }
        tag.put("FilterItems", filterList);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        
        // Load filter items
        filterItems.clear();
        ListTag filterList = tag.getList("FilterItems", 10);
        for (int i = 0; i < filterList.size(); i++) {
            CompoundTag itemTag = filterList.getCompound(i);
            ItemStack filterItem = ItemStack.parseOptional(registries, itemTag);
            if (!filterItem.isEmpty()) {
                filterItems.add(filterItem);
            }
        }
        updateDisplaySlots();
    }
}