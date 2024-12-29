package com.deadman.voidspaces.block.entity;

import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;
import java.util.stream.IntStream;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.Unpooled;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
/*
public class AcceleratorEntity extends RandomizableContainerBlockEntity implements WorldlyContainer {
    private static final Logger LOGGER = LoggerFactory.getLogger(AcceleratorEntity.class);

    public AcceleratorEntity(BlockPos pos, BlockState state) {
        super(this, pos, state);
    }

    public void setOwner(UUID uuid) {

    }

    public static void tick(Level level, BlockPos pos, BlockState state, AcceleratorEntity blockEntity) {

    }

    //Add resonant ender fluid requirement later

    @Override
    public void onLoad() {
        super.onLoad();
        if (!this.level.isClientSide) {
            this.setChanged();

        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, Provider provider) {
        super.saveAdditional(tag, provider);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, Provider provider) {
        super.loadAdditional(tag, provider);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, Provider provider) {

    }

    @Override
    public CompoundTag getUpdateTag(Provider lookupProvider) {
        CompoundTag tag = super.getUpdateTag(lookupProvider);
        this.saveAdditional(tag, lookupProvider);
        return tag;
    }

    @Override
    public int getContainerSize() {
        return 0;
    }

    @Override
    public Component getDefaultName() {
        return Component.literal("void_accelerator");
    }
}
 */