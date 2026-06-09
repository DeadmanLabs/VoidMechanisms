package com.deadman.voidspaces.block;

import com.deadman.voidspaces.block.entity.VoidOutPortEntity;
import com.deadman.voidspaces.helpers.Dimensional;
import com.deadman.voidspaces.init.BlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

/**
 * Infinite item sink inside a void dimension.
 * Can only be placed inside a VoidMechanisms dimension.
 * Adjacent hoppers can push into it endlessly to collect machine output.
 * Tracks total items received for simulation rate analysis.
 */
public class VoidOutPort extends Block implements EntityBlock {
    private static final Logger LOGGER = LoggerFactory.getLogger(VoidOutPort.class);

    public VoidOutPort(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VoidOutPortEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return type == BlockEntities.VOID_OUT_PORT_ENTITY.get()
                ? (lvl, pos, st, be) -> VoidOutPortEntity.tick(lvl, pos, st, (VoidOutPortEntity) be)
                : null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        if (level instanceof ServerLevel serverLevel) {
            if (Dimensional.getWrapper(serverLevel.dimension()) == null) {
                LOGGER.info("VoidOutPort placement outside void dimension rejected at {}", pos);
                serverLevel.destroyBlock(pos, true);
            }
        }
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                            Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (level.getBlockEntity(pos) instanceof VoidOutPortEntity port) {
                // Show stored counts to the player
                long total = port.getTotalInserted().values().stream().mapToLong(Long::longValue).sum();
                serverPlayer.sendSystemMessage(
                        net.minecraft.network.chat.Component.literal(
                                "Out-Port total items received: " + total));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                         BlockState newState, boolean isMoving) {
        if (state.getBlock() != newState.getBlock()) {
            level.updateNeighbourForOutputSignal(pos, this);
            level.invalidateCapabilities(pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
