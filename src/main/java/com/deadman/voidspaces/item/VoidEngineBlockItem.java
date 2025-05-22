package com.deadman.voidspaces.item;

import javax.annotation.Nullable;

import com.deadman.voidspaces.block.entity.EngineEntity;
import com.deadman.voidspaces.init.BlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;

/**
 * Custom BlockItem for the Void Engine that preserves its dimension and return-location NBT across
 * breaking and placing.
 */
public class VoidEngineBlockItem extends BlockItem {
    private static final Logger LOGGER = LoggerFactory.getLogger(VoidEngineBlockItem.class);
    public VoidEngineBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level world, @Nullable Player player, ItemStack stack, BlockState state) {
        boolean applied = super.updateCustomBlockEntityTag(pos, world, player, stack, state);
        if (world.isClientSide()) {
            return applied;
        }
        if (world.getBlockEntity(pos) instanceof EngineEntity engine) {
            if (applied) {
                if (player instanceof ServerPlayer serverPlayer) {
                    engine.getDimensional().teleportIn(serverPlayer);
                } else {
                    LOGGER.info("placer is not a server player!");
                }
            } else {
                if (player instanceof ServerPlayer serverPlayer) {
                    engine.setOwner(serverPlayer.getUUID());
                } else {
                    LOGGER.info("placer is not a server player!");
                }
            }
            CompoundTag engineData = new CompoundTag();
            engine.writePersistedData(engineData);
            LOGGER.info("Writing VoidEngine NBT: {}", engineData);
            BlockItem.setBlockEntityData(stack, BlockEntities.ENGINE_BLOCK_ENTITY.get(), engineData);
            return true;
        }
        return applied;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        // Read full NBT of the item stack (including BlockEntityTag) for display
        Tag raw = stack.saveOptional(context.registries());
        if (raw instanceof CompoundTag root && root.contains("BlockEntityTag", CompoundTag.TAG_COMPOUND)) {
            CompoundTag tag = root.getCompound("BlockEntityTag");
            if (tag.hasUUID("owner")) {
                tooltip.add(Component.literal("Owner: " + tag.getUUID("owner")));
            }
            if (tag.contains("dimension")) {
                tooltip.add(Component.literal("Dimension: " + tag.getString("dimension")));
            }
            if (tag.contains("returnLocation", CompoundTag.TAG_COMPOUND)) {
                CompoundTag rt = tag.getCompound("returnLocation");
                tooltip.add(Component.literal(
                    String.format("Return: %s [%d, %d, %d]",
                        rt.getString("origDim"), rt.getInt("x"), rt.getInt("y"), rt.getInt("z"))));
            }
        }
    }
}