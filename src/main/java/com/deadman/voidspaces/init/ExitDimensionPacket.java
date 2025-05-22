package com.deadman.voidspaces.init;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.deadman.voidspaces.helpers.Dimensional;
import com.deadman.voidspaces.VoidSpaces;

/**
 * Packet sent from client to server to exit a contained dimension.
 */
public record ExitDimensionPacket() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ExitDimensionPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(VoidSpaces.MODID, "exit_dimension"));
    public static final StreamCodec<FriendlyByteBuf, ExitDimensionPacket> STREAM_CODEC =
        StreamCodec.unit(new ExitDimensionPacket());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            ResourceKey<Level> key = player.level().dimension();
            Dimensional wrapper = Dimensional.getWrapper(key);
            if (wrapper != null) {
                wrapper.teleportOut(player);
            }
        });
    }
}