package com.deadman.voidspaces.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.deadman.voidspaces.VoidSpaces;

/**
 * Packet sent by the server to the client with dimension-specific world border information.
 * This is sent in response to a BorderSyncRequestPacket and ensures that border settings
 * are correctly applied to the specific dimension.
 */
public record BorderSyncResponsePacket(
    ResourceKey<Level> dimensionKey,
    double centerX,
    double centerZ,
    double size,
    double damagePerBlock,
    int warningBlocks,
    int warningTime
) implements CustomPacketPayload {
    private static final Logger LOGGER = LoggerFactory.getLogger(BorderSyncResponsePacket.class);
    
    public static final CustomPacketPayload.Type<BorderSyncResponsePacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(VoidSpaces.MODID, "border_sync_response"));

    // Using individual field codecs and combining them
    private static final StreamCodec<ByteBuf, ResourceKey<Level>> DIMENSION_CODEC = ResourceKey.streamCodec(Registries.DIMENSION);
    private static final StreamCodec<ByteBuf, Double> DOUBLE_CODEC = ByteBufCodecs.DOUBLE;
    private static final StreamCodec<ByteBuf, Integer> INT_CODEC = ByteBufCodecs.INT;
    
    public static final StreamCodec<ByteBuf, BorderSyncResponsePacket> STREAM_CODEC = 
        StreamCodec.of(
            // Encoding: packet -> bytebuf
            (buf, packet) -> {
                DIMENSION_CODEC.encode(buf, packet.dimensionKey);
                DOUBLE_CODEC.encode(buf, packet.centerX);
                DOUBLE_CODEC.encode(buf, packet.centerZ);
                DOUBLE_CODEC.encode(buf, packet.size);
                DOUBLE_CODEC.encode(buf, packet.damagePerBlock);
                INT_CODEC.encode(buf, packet.warningBlocks);
                INT_CODEC.encode(buf, packet.warningTime);
            },
            // Decoding: bytebuf -> packet
            buf -> {
                ResourceKey<Level> dimKey = DIMENSION_CODEC.decode(buf);
                double centerX = DOUBLE_CODEC.decode(buf);
                double centerZ = DOUBLE_CODEC.decode(buf);
                double size = DOUBLE_CODEC.decode(buf);
                double damagePerBlock = DOUBLE_CODEC.decode(buf);
                int warningBlocks = INT_CODEC.decode(buf);
                int warningTime = INT_CODEC.decode(buf);
                return new BorderSyncResponsePacket(
                    dimKey, centerX, centerZ, size, damagePerBlock, warningBlocks, warningTime
                );
            }
        );

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> ClientHandler.handle(this));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Client-side handler that applies the border settings to the correct dimension
     */
    private static class ClientHandler {
        public static void handle(BorderSyncResponsePacket packet) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null) {
                LOGGER.warn("Client received border sync response but no level is loaded");
                return;
            }
            
            // Only apply border if this packet is for the current dimension
            if (!minecraft.level.dimension().equals(packet.dimensionKey())) {
                LOGGER.warn("Received border sync response for {} but client is in {}. Ignoring.",
                    packet.dimensionKey().location(), minecraft.level.dimension().location());
                return;
            }
            LOGGER.info("Applying border to current dimension: {}", packet.dimensionKey().location());
            WorldBorder border = minecraft.level.getWorldBorder();
            border.setCenter(packet.centerX(), packet.centerZ());
            border.setSize(packet.size());
            border.setDamagePerBlock(packet.damagePerBlock());
            border.setWarningBlocks(packet.warningBlocks());
            border.setWarningTime(packet.warningTime());
            LOGGER.info("Applied border settings for dimension {}: center=({}, {}), size={}",
                packet.dimensionKey().location(), packet.centerX(), packet.centerZ(), packet.size());
        }
    }
}
