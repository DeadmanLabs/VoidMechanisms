package com.deadman.voidspaces.world.inventory;

import com.deadman.voidspaces.block.entity.VoidHopperEntity;
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

public class VoidHopperMenu extends AbstractContainerMenu {
    private final Container container;
    private final VoidHopperEntity voidHopper;

    public VoidHopperMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(5), null);
    }

    public VoidHopperMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory, new SimpleContainer(5), null);
        if (extraData != null) {
            BlockPos pos = extraData.readBlockPos();
            BlockEntity blockEntity = playerInventory.player.level().getBlockEntity(pos);
            if (blockEntity instanceof VoidHopperEntity voidHopperEntity) {
                // Update the container and voidHopper references
                this.container.clearContent();
                for (int i = 0; i < Math.min(5, voidHopperEntity.getContainerSize()); i++) {
                    this.container.setItem(i, voidHopperEntity.getItem(i));
                }
            }
        }
    }

    public VoidHopperMenu(int containerId, Inventory playerInventory, Container container, VoidHopperEntity voidHopper) {
        super(Menus.VOID_HOPPER_MENU.get(), containerId);
        this.container = container;
        this.voidHopper = voidHopper;
        
        checkContainerSize(container, 5);
        container.startOpen(playerInventory.player);

        // Add hopper slots (5 slots in a row)
        for (int i = 0; i < 5; ++i) {
            this.addSlot(new VoidHopperSlot(container, i, 44 + i * 18, 20, voidHopper));
        }

        // Add player inventory slots
        for (int l = 0; l < 3; ++l) {
            for (int k = 0; k < 9; ++k) {
                this.addSlot(new Slot(playerInventory, k + l * 9 + 9, 8 + k * 18, l * 18 + 51));
            }
        }

        // Add player hotbar slots
        for (int i1 = 0; i1 < 9; ++i1) {
            this.addSlot(new Slot(playerInventory, i1, 8 + i1 * 18, 109));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();
            if (index < this.container.getContainerSize()) {
                if (!this.moveItemStackTo(itemstack1, this.container.getContainerSize(), this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(itemstack1, 0, this.container.getContainerSize(), false)) {
                return ItemStack.EMPTY;
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

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        // Handle filter slot clicks (slots 0-4)
        if (slotId >= 0 && slotId < 5 && this.voidHopper != null) {
            ItemStack carriedItem = getCarried();
            Slot slot = this.slots.get(slotId);
            
            if (clickType == ClickType.PICKUP) {
                if (!carriedItem.isEmpty() && button == 0) { // Left click with item
                    // Add as filter without consuming the item
                    this.voidHopper.addFilterItem(carriedItem);
                    return; // Don't call super - prevents item consumption
                } else if (carriedItem.isEmpty() && button == 0 && slot.hasItem()) { // Left click empty hand on filled slot
                    // Remove the filter
                    ItemStack slotItem = slot.getItem();
                    for (int i = 0; i < this.voidHopper.getFilterItems().size(); i++) {
                        ItemStack filterItem = this.voidHopper.getFilterItems().get(i);
                        if (ItemStack.isSameItemSameComponents(filterItem, slotItem)) {
                            this.voidHopper.removeFilterItem(i);
                            break;
                        }
                    }
                    return;
                }
            }
        }
        
        // For non-filter slots, use normal behavior
        super.clicked(slotId, button, clickType, player);
    }

    // Custom slot that handles the filter item cloning behavior
    public static class VoidHopperSlot extends Slot {
        private final VoidHopperEntity voidHopper;

        public VoidHopperSlot(Container container, int slot, int x, int y, VoidHopperEntity voidHopper) {
            super(container, slot, x, y);
            this.voidHopper = voidHopper;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            // Don't allow normal placement - handled by menu click override
            return false;
        }

        @Override
        public ItemStack remove(int amount) {
            if (this.voidHopper != null && this.hasItem()) {
                // When removing an item from the slot, remove it from the filter
                ItemStack slotItem = this.getItem();
                // Find and remove this filter item
                for (int i = 0; i < this.voidHopper.getFilterItems().size(); i++) {
                    ItemStack filterItem = this.voidHopper.getFilterItems().get(i);
                    if (ItemStack.isSameItemSameComponents(filterItem, slotItem)) {
                        this.voidHopper.removeFilterItem(i);
                        break;
                    }
                }
            }
            return super.remove(amount);
        }
    }
}