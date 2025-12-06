package com.deadman.voidspaces.block;

import com.deadman.voidspaces.block.entity.VoidPowerConnectorEntity;
import com.deadman.voidspaces.helpers.Dimensional;
import com.deadman.voidspaces.init.BlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class VoidPowerConnector extends BaseEntityBlock {
    public static final MapCodec<VoidPowerConnector> CODEC = simpleCodec(VoidPowerConnector::new);

    public VoidPowerConnector(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VoidPowerConnectorEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(blockEntityType, BlockEntities.VOID_POWER_CONNECTOR_ENTITY.get(), VoidPowerConnectorEntity::tick);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);

        if (!level.isClientSide) {
            // Check if we're in a void dimension
            Dimensional wrapper = Dimensional.getWrapper(level.dimension());
            if (wrapper == null) {
                // Not in a void dimension - remove the block
                System.out.println("VoidPowerConnector placed in non-void dimension - removing at " + pos);
                level.destroyBlock(pos, true);
                return;
            }
        }
    }
}
