package com.deadman.voidspaces.block;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.Unpooled;

public class VoidAccelerator extends Block {
    public VoidAccelerator(Properties properties) {
        super(properties);
    }
}