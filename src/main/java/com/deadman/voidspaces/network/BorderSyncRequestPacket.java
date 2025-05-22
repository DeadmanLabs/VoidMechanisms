package com.deadman.voidspaces.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.deadman.voidspaces.VoidSpaces;
import com.deadman.voidspaces.helpers.Dimensional;

/**
 * Packet sent by the client to request world border synchronization for a specific dimension.
 * This is sent after the client has registered a new dimension to ensure border packets
 * are only sent when the client is ready to process them.
 */
public record BorderSyncRequestPacket(ResourceKey<Level> dimensionKey) implements CustomPacketPayload {
    private static final Logger LOGGER = LoggerFactory.getLogger(BorderSyncRequestPacket.class);
    
    public static final CustomPacketPayload.Type<BorderSyncRequestPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(VoidSpaces.MODID, "border_sync_request"));

    public static final StreamCodec<ByteBuf, BorderSyncRequestPacket> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(Registries.DIMENSION), BorderSyncRequestPacket::dimensionKey,
            BorderSyncRequestPacket::new);

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> Handler.handle(this, context));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static class Handler {
        public static void handle(BorderSyncRequestPacket packet, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player) {
                LOGGER.info("Received border sync request from client for dimension: {}", packet.dimensionKey().location());
                
                Dimensional dim = Dimensional.getForDimension(packet.dimensionKey());
                if (dim != null) {
                    LOGGER.info("Sending border sync response for dimension: {}", packet.dimensionKey().location());
                    
                    // Get border settings from the dimension
                    WorldBorder border = dim.getWorldBorder();
                    
                    // Create and send the response packet with border information
                    BorderSyncResponsePacket responsePacket = new BorderSyncResponsePacket(
                        packet.dimensionKey(),
                        border.getCenterX(),
                        border.getCenterZ(),
                        border.getSize(),
                        border.getDamagePerBlock(),
                        border.getWarningBlocks(),
                        border.getWarningTime()
                    );
                    
                    // Send the packet to the client
                    PacketDistributor.sendToPlayer(player, responsePacket);
                    
                    LOGGER.info("Sent border sync response to player for dimension {} with size {}", 
                                packet.dimensionKey().location(), border.getSize());
                } else {
                    LOGGER.warn("Cannot find Dimensional instance for dimension: {}", packet.dimensionKey().location());
                }
            }
        }
    }
}
