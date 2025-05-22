package com.deadman.voidspaces.infiniverse.internal;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import io.netty.buffer.ByteBuf;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.deadman.voidspaces.VoidSpaces;
import com.deadman.voidspaces.network.BorderSyncRequestPacket;

public record UpdateDimensionsPacket(Set<ResourceKey<Level>> keys, boolean add) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<UpdateDimensionsPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(VoidSpaces.MODID, "update_dimensions"));
    public static final StreamCodec<ByteBuf, UpdateDimensionsPacket> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(Registries.DIMENSION).apply(ByteBufCodecs.list()).map(Set::copyOf, List::copyOf), UpdateDimensionsPacket::keys,
            ByteBufCodecs.BOOL, UpdateDimensionsPacket::add,
            UpdateDimensionsPacket::new);
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> ClientHandler.handle(this));
    }
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static class ClientHandler
    {
        private static final Logger LOGGER = LoggerFactory.getLogger(ClientHandler.class);
        
        private static void handle(UpdateDimensionsPacket packet) {
            final LocalPlayer player = Minecraft.getInstance().player;
            if (player == null)
                return;
            final Set<ResourceKey<Level>> dimensionList = player.connection.levels();
            if (dimensionList == null)
                return;
                
            // If we're adding dimensions
            if (packet.add()) {
                LOGGER.info("Client received new dimensions: {}", packet.keys());
                
                // Add each dimension to the list
                for (ResourceKey<Level> dimensionKey : packet.keys()) {
                    dimensionList.add(dimensionKey);
                    
                    // Request border sync for this new dimension
                    LOGGER.info("Sending border sync request for dimension: {}", dimensionKey.location());
                    PacketDistributor.sendToServer(new BorderSyncRequestPacket(dimensionKey));
                }
            } else {
                // Just remove dimensions
                packet.keys().forEach(dimensionList::remove);
            }
        }
    }
}
