package com.deadman.voidspaces.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.deadman.voidspaces.VoidSpaces;
import com.deadman.voidspaces.helpers.Dimensional;

/**
 * Packet sent by the client to request return to their original location/dimension.
 */
public record ReturnHomePacket() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ReturnHomePacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(VoidSpaces.MODID, "return_home"));

    public static final StreamCodec<ByteBuf, ReturnHomePacket> STREAM_CODEC = StreamCodec.of(
        (buf, pkt) -> {},
        buf -> new ReturnHomePacket()
    );

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> Handler.handle(context));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static class Handler {
        public static void handle(IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player) {
                ResourceKey<Level> current = player.level().dimension();
                Dimensional dim = Dimensional.getForDimension(current);
                if (dim != null) {
                    dim.teleportOut(player);
                }
            }
        }
    }
}