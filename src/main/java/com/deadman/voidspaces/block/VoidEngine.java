package com.deadman.voidspaces.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.network.chat.Component;
import net.minecraft.network.FriendlyByteBuf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.Unpooled;

import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;

import com.deadman.voidspaces.block.entity.EngineEntity;
import com.deadman.voidspaces.init.BlockEntities;

public class VoidEngine extends Block implements EntityBlock {
    public static final Logger LOGGER = LoggerFactory.getLogger(VoidEngine.class);
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");

    public VoidEngine(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(POWERED, false));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EngineEntity(pos, state);
    }

    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return
                type == BlockEntities.ENGINE_BLOCK_ENTITY.get()
                        ?
                            (lvl, pos, st, be) -> EngineEntity.tick(lvl, pos, st, (EngineEntity)be)
                        :
                            null;
    }


    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        super.useWithoutItem(state, world, pos, player, hit);
        return InteractionResult.sidedSuccess(world.isClientSide);
    }

    @Override
    public MenuProvider getMenuProvider(BlockState state, Level world, BlockPos pos) {
        BlockEntity tileEntity = world.getBlockEntity(pos);
        return tileEntity instanceof MenuProvider menuProvider ? menuProvider : null;
    }

    @Override
    public boolean triggerEvent(BlockState state, Level world, BlockPos pos, int eventID, int eventParam) {
        super.triggerEvent(state, world, pos, eventID, eventParam);
        BlockEntity blockEntity = world.getBlockEntity(pos);
        return blockEntity == null ? false : blockEntity.triggerEvent(eventID, eventParam);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() != newState.getBlock()) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof EngineEntity engineEntity) {
                CompoundTag nbt = new CompoundTag();
                engineEntity.writePersistedData(nbt);
                ItemStack stack = new ItemStack(this);
                BlockItem.setBlockEntityData(stack, BlockEntities.ENGINE_BLOCK_ENTITY.get(), nbt);
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
                Containers.dropContents(level, pos, engineEntity);
                level.updateNeighbourForOutputSignal(pos, this);
                level.invalidateCapabilities(pos);
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    public void notifyEngineBlock(Level level, BlockPos pos, boolean isPowered) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof EngineEntity engineEntity) {
            engineEntity.updateEngineState(pos, isPowered);
        }
    }
}