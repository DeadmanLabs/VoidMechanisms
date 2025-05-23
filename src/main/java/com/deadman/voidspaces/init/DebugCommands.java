package com.deadman.voidspaces.init;

import com.deadman.voidspaces.helpers.Dimensional;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.border.WorldBorder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber
public class DebugCommands {

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
}