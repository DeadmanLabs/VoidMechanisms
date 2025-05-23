package com.deadman.voidspaces.init;

import com.deadman.voidspaces.helpers.Dimensional;
import com.deadman.voidspaces.helpers.Space;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

@EventBusSubscriber
public class DebugCommands {
    private static final Logger LOGGER = LoggerFactory.getLogger(DebugCommands.class);

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        
        dispatcher.register(Commands.literal("voidspaces")
            .then(Commands.literal("debug")
                .then(Commands.literal("border")
                    .executes(DebugCommands::debugBorder)
                )
                .then(Commands.literal("sync")
                    .executes(DebugCommands::forceBorderSync)
                )
                .then(Commands.literal("vanilla")
                    .executes(DebugCommands::testVanillaBorder)
                )
                .then(Commands.literal("analyze")
                    .then(Commands.argument("index", IntegerArgumentType.integer(0))
                        .executes(DebugCommands::analyzeDimension)
                    )
                )
                .then(Commands.literal("list")
                    .executes(DebugCommands::listVoidDimensions)
                )
            )
        );
    }

    private static int debugBorder(CommandContext<CommandSourceStack> context) {
        if (context.getSource().getEntity() instanceof ServerPlayer player) {
            WorldBorder border = player.level().getWorldBorder();
            
            player.sendSystemMessage(Component.literal("=== World Border Debug ==="));
            player.sendSystemMessage(Component.literal("Dimension: " + player.level().dimension().location()));
            player.sendSystemMessage(Component.literal("Level Type: " + player.level().getClass().getSimpleName()));
            player.sendSystemMessage(Component.literal("Border Type: " + border.getClass().getSimpleName()));
            player.sendSystemMessage(Component.literal("Center: (" + border.getCenterX() + ", " + border.getCenterZ() + ")"));
            player.sendSystemMessage(Component.literal("Size: " + border.getSize()));
            player.sendSystemMessage(Component.literal("Damage: " + border.getDamagePerBlock()));
            player.sendSystemMessage(Component.literal("Warning: " + border.getWarningBlocks()));
            player.sendSystemMessage(Component.literal("Max Size: " + border.getAbsoluteMaxSize()));
            
            // Check if this is a voidspace dimension
            Dimensional wrapper = Dimensional.getWrapper(player.level().dimension());
            if (wrapper != null) {
                player.sendSystemMessage(Component.literal("VoidSpace Wrapper: Found"));
            } else {
                player.sendSystemMessage(Component.literal("VoidSpace Wrapper: None"));
            }
            
            return 1;
        }
        return 0;
    }

    private static int forceBorderSync(CommandContext<CommandSourceStack> context) {
        if (context.getSource().getEntity() instanceof ServerPlayer player) {
            WorldBorder border = player.level().getWorldBorder();
            
            player.sendSystemMessage(Component.literal("Force syncing world border..."));
            
            // Send all border packets manually
            try {
                player.connection.send(new ClientboundInitializeBorderPacket(border));
                player.sendSystemMessage(Component.literal("Sent: ClientboundInitializeBorderPacket"));
                
                player.connection.send(new ClientboundSetBorderCenterPacket(border));
                player.sendSystemMessage(Component.literal("Sent: ClientboundSetBorderCenterPacket"));
                
                player.connection.send(new ClientboundSetBorderSizePacket(border));
                player.sendSystemMessage(Component.literal("Sent: ClientboundSetBorderSizePacket"));
                
                player.connection.send(new ClientboundSetBorderWarningDelayPacket(border));
                player.sendSystemMessage(Component.literal("Sent: ClientboundSetBorderWarningDelayPacket"));
                
                player.connection.send(new ClientboundSetBorderWarningDistancePacket(border));
                player.sendSystemMessage(Component.literal("Sent: ClientboundSetBorderWarningDistancePacket"));
                
                player.sendSystemMessage(Component.literal("All border packets sent!"));
                
                // Also trigger the wrapper sync if available
                Dimensional wrapper = Dimensional.getWrapper(player.level().dimension());
                if (wrapper != null) {
                    wrapper.ensureWorldBorderForPlayer(player);
                    player.sendSystemMessage(Component.literal("Triggered VoidSpace border sync"));
                }
                
            } catch (Exception e) {
                player.sendSystemMessage(Component.literal("Error: " + e.getMessage()));
            }
            
            return 1;
        }
        return 0;
    }

    private static int testVanillaBorder(CommandContext<CommandSourceStack> context) {
        if (context.getSource().getEntity() instanceof ServerPlayer player) {
            player.sendSystemMessage(Component.literal("Testing vanilla worldborder command..."));
            
            // Execute vanilla worldborder commands through the server's command system
            try {
                context.getSource().getServer().getCommands().performPrefixedCommand(
                    context.getSource().withSuppressedOutput(),
                    "worldborder center 0 0"
                );
                player.sendSystemMessage(Component.literal("Executed: worldborder center 0 0"));
                
                context.getSource().getServer().getCommands().performPrefixedCommand(
                    context.getSource().withSuppressedOutput(),
                    "worldborder set 50"
                );
                player.sendSystemMessage(Component.literal("Executed: worldborder set 50"));
                
                player.sendSystemMessage(Component.literal("Vanilla border commands executed!"));
                
            } catch (Exception e) {
                player.sendSystemMessage(Component.literal("Error executing vanilla commands: " + e.getMessage()));
            }
            
            return 1;
        }
        return 0;
    }

    private static int analyzeDimension(CommandContext<CommandSourceStack> context) {
        if (context.getSource().getEntity() instanceof ServerPlayer player) {
            int dimensionIndex = IntegerArgumentType.getInteger(context, "index");
            
            try {
                // Construct the void dimension name using the index
                String dimensionName = "voidspace_" + dimensionIndex;
                ResourceLocation dimensionLocation = ResourceLocation.fromNamespaceAndPath("voidspaces", dimensionName);
                ResourceKey<Level> dimensionKey = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimensionLocation);
                
                // Get the dimension level
                ServerLevel dimensionLevel = player.getServer().getLevel(dimensionKey);
                if (dimensionLevel == null) {
                    player.sendSystemMessage(Component.literal("§cVoid dimension " + dimensionIndex + " not found or not loaded"));
                    player.sendSystemMessage(Component.literal("§7Tried: " + dimensionLocation));
                    return 0;
                }
                
                player.sendSystemMessage(Component.literal("§6=== Analyzing Void Dimension " + dimensionIndex + " ==="));
                player.sendSystemMessage(Component.literal("§7Full ID: " + dimensionLocation));
                player.sendSystemMessage(Component.literal("§7Dimension Type: " + dimensionLevel.getClass().getSimpleName()));
                
                // Extract contents from the dimension (analyzing chunk 0,0)
                ChunkPos chunkPos = new ChunkPos(0, 0);
                Space.SpaceContents contents = Space.extractContents(dimensionLevel, chunkPos);
                
                // Analyze blocks
                player.sendSystemMessage(Component.literal("§a--- BLOCKS (" + contents.blocks.size() + " total) ---"));
                Map<String, Integer> blockCounts = new HashMap<>();
                
                for (Map.Entry<BlockPos, CompoundTag> entry : contents.blocks.entrySet()) {
                    BlockPos pos = entry.getKey();
                    CompoundTag blockTag = entry.getValue();
                    
                    // Extract block name from the BlockState NBT
                    String blockName = "Unknown Block";
                    if (blockTag.contains("BlockState")) {
                        CompoundTag blockStateTag = blockTag.getCompound("BlockState");
                        if (blockStateTag.contains("Name")) {
                            blockName = blockStateTag.getString("Name");
                        }
                    }
                    
                    blockCounts.put(blockName, blockCounts.getOrDefault(blockName, 0) + 1);
                    
                    if (contents.blocks.size() <= 20) { // Only show individual positions if there aren't too many
                        player.sendSystemMessage(Component.literal("  §7" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ": §f" + blockName));
                    }
                }
                
                // Show block summary if there are many blocks
                if (contents.blocks.size() > 20) {
                    player.sendSystemMessage(Component.literal("§7Block Summary:"));
                    for (Map.Entry<String, Integer> entry : blockCounts.entrySet()) {
                        player.sendSystemMessage(Component.literal("  §f" + entry.getKey() + ": §e" + entry.getValue()));
                    }
                }
                
                // Analyze block entities (containers, machines, etc.)
                player.sendSystemMessage(Component.literal("§a--- BLOCK ENTITIES (" + contents.blockEntities.size() + " total) ---"));
                for (Map.Entry<BlockPos, CompoundTag> entry : contents.blockEntities.entrySet()) {
                    BlockPos pos = entry.getKey();
                    CompoundTag beTag = entry.getValue();
                    
                    String beType = beTag.getString("id");
                    player.sendSystemMessage(Component.literal("  §7" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ": §b" + beType));
                    
                    // Analyze inventory contents if it has items
                    if (beTag.contains("Items")) {
                        ListTag itemsTag = beTag.getList("Items", 10);
                        if (!itemsTag.isEmpty()) {
                            player.sendSystemMessage(Component.literal("    §7Items (" + itemsTag.size() + " slots):"));
                            for (int i = 0; i < itemsTag.size(); i++) {
                                CompoundTag itemTag = itemsTag.getCompound(i);
                                ItemStack item = ItemStack.parseOptional(player.level().registryAccess(), itemTag);
                                if (!item.isEmpty()) {
                                    String displayName = item.getDisplayName().getString();
                                    int count = item.getCount();
                                    player.sendSystemMessage(Component.literal("      §f" + displayName + " x" + count));
                                }
                            }
                        }
                    }
                    
                    // Analyze fluid tanks if present
                    if (beTag.contains("FluidTank") || beTag.contains("tank") || beTag.contains("fluid")) {
                        // Check various common fluid storage NBT keys
                        String[] fluidKeys = {"FluidTank", "tank", "fluid", "Tank"};
                        for (String key : fluidKeys) {
                            if (beTag.contains(key)) {
                                CompoundTag fluidTag = beTag.getCompound(key);
                                if (fluidTag.contains("FluidName") && fluidTag.contains("Amount")) {
                                    String fluidName = fluidTag.getString("FluidName");
                                    int amount = fluidTag.getInt("Amount");
                                    if (amount > 0) {
                                        player.sendSystemMessage(Component.literal("    §9Fluid: " + fluidName + " (" + amount + " mB)"));
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Analyze entities
                player.sendSystemMessage(Component.literal("§a--- ENTITIES (" + contents.entities.size() + " total) ---"));
                Map<String, Integer> entityCounts = new HashMap<>();
                
                for (Map.Entry<Entity, CompoundTag> entry : contents.entities.entrySet()) {
                    Entity entity = entry.getKey();
                    CompoundTag entityTag = entry.getValue();
                    
                    String entityType = entityTag.getString("id");
                    entityCounts.put(entityType, entityCounts.getOrDefault(entityType, 0) + 1);
                    
                    if (contents.entities.size() <= 10) { // Only show individual entities if there aren't too many
                        String pos = String.format("%.1f,%.1f,%.1f", entity.getX(), entity.getY(), entity.getZ());
                        player.sendSystemMessage(Component.literal("  §7" + pos + ": §d" + entityType));
                        
                        // If it's an item entity, show what item it is
                        if (entityType.equals("minecraft:item")) {
                            if (entityTag.contains("Item")) {
                                CompoundTag itemTag = entityTag.getCompound("Item");
                                ItemStack item = ItemStack.parseOptional(player.level().registryAccess(), itemTag);
                                if (!item.isEmpty()) {
                                    player.sendSystemMessage(Component.literal("    §fItem: " + item.getDisplayName().getString() + " x" + item.getCount()));
                                }
                            }
                        }
                    }
                }
                
                // Show entity summary if there are many entities
                if (contents.entities.size() > 10) {
                    player.sendSystemMessage(Component.literal("§7Entity Summary:"));
                    for (Map.Entry<String, Integer> entry : entityCounts.entrySet()) {
                        player.sendSystemMessage(Component.literal("  §d" + entry.getKey() + ": §e" + entry.getValue()));
                    }
                }
                
                player.sendSystemMessage(Component.literal("§6=== Analysis Complete ==="));
                
                return 1;
                
            } catch (Exception e) {
                player.sendSystemMessage(Component.literal("§cError analyzing dimension: " + e.getMessage()));
                LOGGER.error("Error in analyzeDimension command", e);
                return 0;
            }
        }
        return 0;
    }

    private static int listVoidDimensions(CommandContext<CommandSourceStack> context) {
        if (context.getSource().getEntity() instanceof ServerPlayer player) {
            player.sendSystemMessage(Component.literal("§6=== Available Void Dimensions ==="));
            
            int foundCount = 0;
            
            // Check for void dimensions up to index 50 (should be more than enough)
            for (int i = 0; i < 50; i++) {
                String dimensionName = "voidspace_" + i;
                ResourceLocation dimensionLocation = ResourceLocation.fromNamespaceAndPath("voidspaces", dimensionName);
                ResourceKey<Level> dimensionKey = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimensionLocation);
                
                ServerLevel dimensionLevel = player.getServer().getLevel(dimensionKey);
                if (dimensionLevel != null) {
                    foundCount++;
                    
                    // Check if there's a Dimensional wrapper for additional info
                    Dimensional wrapper = Dimensional.getWrapper(dimensionKey);
                    String ownerInfo = "";
                    if (wrapper != null) {
                        // Try to get owner info if available
                        ownerInfo = " §7(has wrapper)";
                    }
                    
                    player.sendSystemMessage(Component.literal("  §a" + i + ": §f" + dimensionLocation + ownerInfo));
                    
                    // Quick stats about the dimension
                    try {
                        ChunkPos chunkPos = new ChunkPos(0, 0);
                        Space.SpaceContents contents = Space.extractContents(dimensionLevel, chunkPos);
                        String stats = String.format(" §7[%d blocks, %d entities, %d block entities]", 
                                                    contents.blocks.size(), 
                                                    contents.entities.size(), 
                                                    contents.blockEntities.size());
                        player.sendSystemMessage(Component.literal("    " + stats));
                    } catch (Exception e) {
                        player.sendSystemMessage(Component.literal("    §cError getting stats: " + e.getMessage()));
                    }
                }
            }
            
            if (foundCount == 0) {
                player.sendSystemMessage(Component.literal("§7No void dimensions found."));
                player.sendSystemMessage(Component.literal("§7Create one by placing a Void Engine!"));
            } else {
                player.sendSystemMessage(Component.literal("§7Found " + foundCount + " void dimension(s)."));
                player.sendSystemMessage(Component.literal("§7Use §a/voidspaces debug analyze <index>§7 to analyze a specific dimension."));
            }
            
            return 1;
        }
        return 0;
    }
}