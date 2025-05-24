package com.deadman.voidspaces.world.inventory;

import com.deadman.voidspaces.block.entity.VoidDropperEntity;
import com.deadman.voidspaces.init.Menus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

public class VoidDropperMenu extends AbstractContainerMenu {
    private static final int ITEMS_PER_PAGE = 45; // 5 rows of 9 items
    private final Container container;
    private VoidDropperEntity voidDropper; // Remove final to allow assignment in constructor
    private int currentPage = 0;
    private int maxPages = 1;

    public VoidDropperMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(45), null);
    }

    public VoidDropperMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        super(Menus.VOID_DROPPER_MENU.get(), containerId);
        this.container = new SimpleContainer(45);
        
        VoidDropperEntity foundDropper = null;
        if (extraData != null) {
            BlockPos pos = extraData.readBlockPos();
            BlockEntity blockEntity = playerInventory.player.level().getBlockEntity(pos);
            if (blockEntity instanceof VoidDropperEntity voidDropperEntity) {
                foundDropper = voidDropperEntity;
            }
        }
        this.voidDropper = foundDropper;
        
        checkContainerSize(container, 45);
        container.startOpen(playerInventory.player);

        if (this.voidDropper != null) {
            updatePagedItems();
        }

        // Add storage display slots (5 rows of 9 slots)
        for (int row = 0; row < 5; ++row) {
            for (int col = 0; col < 9; ++col) {
                int slotIndex = col + row * 9;
                this.addSlot(new VoidDropperSlot(container, slotIndex, 8 + col * 18, 18 + row * 18, voidDropper, this));
            }
        }

        // Add player inventory slots (moved down to accommodate 5 rows)
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 123 + row * 18));
            }
        }

        // Add player hotbar slots
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 181));
        }
    }

    public VoidDropperMenu(int containerId, Inventory playerInventory, Container container, VoidDropperEntity voidDropper) {
        super(Menus.VOID_DROPPER_MENU.get(), containerId);
        this.container = container;
        this.voidDropper = voidDropper;
        
        checkContainerSize(container, 45);
        container.startOpen(playerInventory.player);

        updatePagedItems();

        // Add storage display slots (5 rows of 9 slots)
        for (int row = 0; row < 5; ++row) {
            for (int col = 0; col < 9; ++col) {
                int slotIndex = col + row * 9;
                this.addSlot(new VoidDropperSlot(container, slotIndex, 8 + col * 18, 18 + row * 18, voidDropper, this));
            }
        }

        // Add player inventory slots (moved down to accommodate 5 rows)
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 123 + row * 18));
            }
        }

        // Add player hotbar slots
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 181));
        }
    }

    private void updateContainer(VoidDropperEntity voidDropperEntity) {
        // This constructor is used for network sync - the voidDropper reference is already set
        // Just trigger a display update if we have a valid dropper
        if (this.voidDropper != null) {
            updatePagedItems();
        }
    }

    public void updatePagedItems() {
        if (voidDropper == null) return;

        List<ItemStack> storedTypes = voidDropper.getStoredItemTypes();
        int oldMaxPages = maxPages;
        maxPages = Math.max(1, (storedTypes.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
        
        // If we have more items than before, automatically go to the last page to show new items
        if (maxPages > oldMaxPages && currentPage == oldMaxPages - 1) {
            currentPage = maxPages - 1;
        }
        
        // Ensure current page is valid
        if (currentPage >= maxPages) {
            currentPage = maxPages - 1;
        }
        if (currentPage < 0) {
            currentPage = 0;
        }

        // Clear container
        for (int i = 0; i < container.getContainerSize(); i++) {
            container.setItem(i, ItemStack.EMPTY);
        }

        // Fill container with items for current page
        int startIndex = currentPage * ITEMS_PER_PAGE;
        for (int i = 0; i < ITEMS_PER_PAGE && (startIndex + i) < storedTypes.size(); i++) {
            ItemStack storedType = storedTypes.get(startIndex + i).copy();
            int totalCount = voidDropper.getStoredCount(storedType);
            // Display count but cap it at 99 to avoid serialization issues in display
            storedType.setCount(Math.min(totalCount, 99));
            container.setItem(i, storedType);
        }
        
        // Broadcast changes to client
        this.broadcastChanges();
    }

    public void nextPage() {
        if (currentPage < maxPages - 1) {
            currentPage++;
            updatePagedItems();
        }
    }

    public void previousPage() {
        if (currentPage > 0) {
            currentPage--;
            updatePagedItems();
        }
    }

    public int getCurrentPage() {
        return currentPage + 1; // 1-based for display
    }

    public int getMaxPages() {
        return maxPages;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();
            
            if (index < 45) { // Storage slots
                // Move from storage to player inventory
                if (!this.moveItemStackTo(itemstack1, 45, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else { // Player inventory slots
                // Add to infinite storage
                if (voidDropper != null) {
                    System.out.println("VoidDropper shift-click: adding " + itemstack1.getDisplayName().getString() + " x" + itemstack1.getCount());
                    voidDropper.addToInfiniteStorage(itemstack1.copy());
                    slot.setByPlayer(ItemStack.EMPTY);
                    updatePagedItems();
                    return itemstack;
                }
            }

            if (itemstack1.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return itemstack;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.container.stopOpen(player);
    }


    // Custom slot for void dropper storage display
    public static class VoidDropperSlot extends Slot {
        private final VoidDropperEntity voidDropper;
        private final VoidDropperMenu menu;

        public VoidDropperSlot(Container container, int slot, int x, int y, VoidDropperEntity voidDropper, VoidDropperMenu menu) {
            super(container, slot, x, y);
            this.voidDropper = voidDropper;
            this.menu = menu;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            // Don't allow normal placement - items are added via shift-click only
            return false;
        }

        @Override
        public ItemStack remove(int amount) {
            if (this.voidDropper != null && this.hasItem()) {
                ItemStack slotItem = this.getItem();
                ItemStack extracted = this.voidDropper.extractFromInfiniteStorage(slotItem, amount);
                // Update the container to reflect changes
                if (this.menu != null) {
                    this.menu.updatePagedItems();
                }
                return extracted;
            }
            return super.remove(amount);
        }

        @Override
        public void setByPlayer(ItemStack stack) {
            // Prevent direct setting by player to avoid freezes
        }

        @Override
        public boolean mayPickup(Player player) {
            // Allow picking up items from display
            return this.hasItem();
        }
    }
}