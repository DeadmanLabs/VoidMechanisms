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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderLookup.RegistryLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import java.util.stream.Stream;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item;
import net.minecraft.network.chat.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.core.component.DataComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.Unpooled;

import javax.annotation.Nullable;

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
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (level.getBlockEntity(pos) instanceof EngineEntity blockEntity && level instanceof ServerLevel serverLevel) {
            // Check if this is a restored engine (has NBT data)
            if (stack.has(DataComponents.CUSTOM_DATA)) {
                CompoundTag itemTag = stack.get(DataComponents.CUSTOM_DATA).copyTag();
                CompoundTag blockTag = itemTag.getCompound("BlockEntityTag");
                if (!blockTag.isEmpty()) {
                    LOGGER.info("Restoring VoidEngine from NBT data");
                    RegistryLookup<BlockEntityType<?>> lookup = serverLevel.registryAccess()
                            .lookup(Registries.BLOCK_ENTITY_TYPE)
                            .orElseThrow();
                    HolderLookup.Provider registryProvider = HolderLookup.Provider.create(Stream.of(lookup));
                    blockEntity.readAdditionalSaveData(blockTag, registryProvider);
                    blockEntity.setChanged();
                    // Teleport the placer into the restored dimension
                    if (placer instanceof ServerPlayer player) {
                        blockEntity.teleportIn(player);
                    }
                    return;
                }
            }
            // This is a new engine being placed
            if (placer instanceof ServerPlayer player) {
                // Check if we're inside a contained dimension - prevent nested engines
                ResourceKey<Level> currentDim = player.level().dimension();
                if (currentDim.location().getNamespace().equals("voidspaces") && 
                    currentDim.location().getPath().startsWith("voidspace_")) {
                    LOGGER.info("Preventing VoidEngine placement inside contained dimension");
                    // Drop the item instead of placing the block
                    level.destroyBlock(pos, true);
                    return;
                }
                blockEntity.setOwner(player.getUUID());
            } else {
                LOGGER.info("placer is not a server player!");
            }
        } else {
            LOGGER.info("block entity at placed location is not engine entity!");
        }
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
                ItemStack stack = new ItemStack(this);
                if (level instanceof ServerLevel serverLevel) {
                    RegistryLookup<BlockEntityType<?>> lookup = serverLevel.registryAccess()
                            .lookup(Registries.BLOCK_ENTITY_TYPE)
                            .orElseThrow();
                    HolderLookup.Provider registryProvider = HolderLookup.Provider.create(Stream.of(lookup));
                    CompoundTag blockTag = engineEntity.saveWithoutMetadata(registryProvider);
                    if (!blockTag.isEmpty()) {
                        CompoundTag tag = new CompoundTag();
                        tag.put("BlockEntityTag", blockTag);
                        stack.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
                    }
                }
                Containers.dropItemStack(level,
                        pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                        stack);
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

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (stack.has(DataComponents.CUSTOM_DATA)) {
            CompoundTag itemTag = stack.get(DataComponents.CUSTOM_DATA).copyTag();
            CompoundTag blockTag = itemTag.getCompound("BlockEntityTag");
            if (!blockTag.isEmpty()) {
                if (blockTag.hasUUID("owner")) {
                    UUID owner = blockTag.getUUID("owner");
                    String ownerName = getPlayerName(owner);
                    tooltip.add(Component.literal("Owner: " + ownerName).withStyle(ChatFormatting.GRAY));
                }
                if (blockTag.contains("dimensionId")) {
                    String dimensionId = blockTag.getString("dimensionId");
                    tooltip.add(Component.literal("Dimension: " + dimensionId).withStyle(ChatFormatting.BLUE));
                }
                tooltip.add(Component.literal("Contains: Void Space").withStyle(ChatFormatting.DARK_PURPLE));
            } else {
                tooltip.add(Component.literal("Empty Engine").withStyle(ChatFormatting.GRAY));
            }
        } else {
            tooltip.add(Component.literal("Empty Engine").withStyle(ChatFormatting.GRAY));
        }
        super.appendHoverText(stack, context, tooltip, flag);
    }

    private String getPlayerName(UUID uuid) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != null) {
                // Try to find the player in the current level
                Player player = minecraft.level.getPlayerByUUID(uuid);
                if (player != null) {
                    return player.getName().getString();
                }
            }
            
            // If we can't find the player, check if it's the current player
            if (minecraft.player != null && minecraft.player.getUUID().equals(uuid)) {
                return minecraft.player.getName().getString();
            }
            
            // Fall back to showing a shortened UUID
            String uuidStr = uuid.toString();
            return uuidStr.substring(0, 8) + "...";
        } catch (Exception e) {
            // Safe fallback
            return "Unknown Player";
        }
    }
}