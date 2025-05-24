package com.deadman.voidspaces.block.entity;

import com.deadman.voidspaces.init.BlockEntities;
import com.deadman.voidspaces.world.inventory.VoidDropperMenu;
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

public class VoidDropperEntity extends BaseContainerBlockEntity {
    private static final Logger LOGGER = LoggerFactory.getLogger(VoidDropperEntity.class);
    
    // Use a list for infinite storage instead of fixed-size NonNullList
    private List<ItemStack> infiniteStorage = new ArrayList<>();
    
    // Large visual inventory for the GUI (45 slots for pagination)
    private NonNullList<ItemStack> displayItems = NonNullList.withSize(45, ItemStack.EMPTY);

    public VoidDropperEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntities.VOID_DROPPER_BLOCK_ENTITY.get(), pos, blockState);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, VoidDropperEntity blockEntity) {
        // VoidDropper doesn't need ticking for now
    }

    @Override
    protected Component getDefaultName() {
        return Component.literal("Void Dropper");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new VoidDropperMenu(containerId, inventory, this, this);
    }

    @Override
    public int getContainerSize() {
        return displayItems.size();
    }

    @Override
    public boolean isEmpty() {
        return infiniteStorage.isEmpty();
    }
    
    @Override
    public boolean canPlaceItem(int index, ItemStack stack) {
        // Accept all items for infinite storage
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot < displayItems.size()) {
            return displayItems.get(slot);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return ContainerHelper.removeItem(displayItems, slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(displayItems, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        // For external item insertion, add to infinite storage instead of display slots
        if (!stack.isEmpty()) {
            LOGGER.info("VoidDropper setItem called with {} x{} at slot {}", 
                       stack.getDisplayName().getString(), stack.getCount(), slot);
            
            // Create a copy to add to storage
            ItemStack toAdd = stack.copy();
            addToInfiniteStorage(toAdd);
            
            // Clear the original stack to indicate it was consumed
            stack.setCount(0);
            
            // Don't actually set anything in the display slots - they're managed by updateDisplayItems
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        displayItems.clear();
        infiniteStorage.clear();
        setChanged();
        LOGGER.info("Cleared all items from void dropper at {}", worldPosition);
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.displayItems = items;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.displayItems;
    }

    // Methods for infinite storage management
    public void addToInfiniteStorage(ItemStack stack) {
        if (!stack.isEmpty()) {
            LOGGER.info("Attempting to add {} x{} to infinite storage", 
                       stack.getDisplayName().getString(), stack.getCount());
            
            // Try to merge with existing stacks first
            for (ItemStack existing : infiniteStorage) {
                // Use a more precise check that considers NBT data and stackability
                if (canStackTogether(existing, stack)) {
                    
                    // Cap at a reasonable maximum to avoid serialization issues
                    int maxSafeCount = 2000000000; // Well below Integer.MAX_VALUE to be safe
                    int maxAdd = maxSafeCount - existing.getCount();
                    int toAdd = Math.min(stack.getCount(), Math.max(0, maxAdd));
                    if (toAdd > 0) {
                        existing.setCount(existing.getCount() + toAdd);
                        stack.shrink(toAdd);
                        LOGGER.info("Merged {} x{} with existing stack, new count: {}", 
                                   existing.getDisplayName().getString(), toAdd, existing.getCount());
                    }
                    if (stack.isEmpty()) {
                        setChanged();
                        updateDisplayItems();
                        return;
                    }
                }
            }
            
            // If we still have items, add a new stack
            if (!stack.isEmpty()) {
                infiniteStorage.add(stack.copy());
                setChanged();
                updateDisplayItems();
                LOGGER.info("Added new stack {} x{} to infinite storage at {}", 
                           stack.getDisplayName().getString(), stack.getCount(), worldPosition);
            }
        }
    }

    private void updateDisplayItems() {
        // Update the actual display items for the container
        List<ItemStack> storedTypes = getStoredItemTypes();
        for (int i = 0; i < displayItems.size(); i++) {
            if (i < storedTypes.size()) {
                ItemStack storedType = storedTypes.get(i).copy();
                int totalCount = getStoredCount(storedType);
                // Display count but cap it at 99 to avoid serialization issues
                storedType.setCount(Math.min(totalCount, 99));
                displayItems.set(i, storedType);
            } else {
                displayItems.set(i, ItemStack.EMPTY);
            }
        }
        setChanged();
    }

    public ItemStack extractFromInfiniteStorage(ItemStack template, int amount) {
        if (template.isEmpty() || amount <= 0) {
            return ItemStack.EMPTY;
        }

        for (int i = 0; i < infiniteStorage.size(); i++) {
            ItemStack stored = infiniteStorage.get(i);
            if (canStackTogether(stored, template)) {
                int extractAmount = Math.min(amount, stored.getCount());
                ItemStack extracted = stored.copy();
                extracted.setCount(extractAmount);
                
                stored.shrink(extractAmount);
                if (stored.isEmpty()) {
                    infiniteStorage.remove(i);
                }
                
                setChanged();
                LOGGER.info("Extracted {} x{} from infinite storage at {}", 
                           extracted.getDisplayName().getString(), extractAmount, worldPosition);
                return extracted;
            }
        }
        
        return ItemStack.EMPTY;
    }

    public List<ItemStack> getStoredItemTypes() {
        List<ItemStack> types = new ArrayList<>();
        for (ItemStack stack : infiniteStorage) {
            if (!stack.isEmpty()) {
                ItemStack typeStack = stack.copy();
                typeStack.setCount(1);
                types.add(typeStack);
            }
        }
        return types;
    }

    public int getStoredCount(ItemStack template) {
        for (ItemStack stored : infiniteStorage) {
            if (canStackTogether(stored, template)) {
                return stored.getCount();
            }
        }
        return 0;
    }

    public int getTotalStoredItems() {
        return infiniteStorage.stream().mapToInt(ItemStack::getCount).sum();
    }

    public void clearInfiniteStorage() {
        infiniteStorage.clear();
        setChanged();
        LOGGER.info("Cleared infinite storage at {}", worldPosition);
    }

    private boolean canStackTogether(ItemStack stack1, ItemStack stack2) {
        // Both stacks must be stackable
        if (!stack1.isStackable() || !stack2.isStackable()) {
            return false;
        }
        
        // Must be the same item
        if (!ItemStack.isSameItem(stack1, stack2)) {
            return false;
        }
        
        // Must have the same components (this handles enchanted books properly)
        if (!ItemStack.isSameItemSameComponents(stack1, stack2)) {
            return false;
        }
        
        // Additional check: see if vanilla would consider them stackable
        ItemStack testStack1 = stack1.copy();
        ItemStack testStack2 = stack2.copy();
        testStack1.setCount(1);
        testStack2.setCount(1);
        
        // Try to grow one stack - if it accepts the other, they can stack
        ItemStack result = testStack1.copyWithCount(testStack1.getCount() + testStack2.getCount());
        return testStack1.getMaxStackSize() >= result.getCount();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, displayItems, registries);
        
        // Save infinite storage
        ListTag storageList = new ListTag();
        for (ItemStack stack : infiniteStorage) {
            if (!stack.isEmpty()) {
                CompoundTag itemTag = new CompoundTag();
                stack.save(registries, itemTag);
                storageList.add(itemTag);
            }
        }
        tag.put("InfiniteStorage", storageList);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        displayItems = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, displayItems, registries);
        
        // Load infinite storage
        infiniteStorage.clear();
        ListTag storageList = tag.getList("InfiniteStorage", 10);
        for (int i = 0; i < storageList.size(); i++) {
            CompoundTag itemTag = storageList.getCompound(i);
            ItemStack stack = ItemStack.parseOptional(registries, itemTag);
            if (!stack.isEmpty()) {
                infiniteStorage.add(stack);
            }
        }
    }
}