package com.deadman.voidspaces.infiniverse.internal;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class QuietPacketDistributors {
    private QuietPacketDistributors() {}
    public static <PACKET extends CustomPacketPayload> void sendToAll(MinecraftServer server, PACKET packet)
    {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.connection.hasChannel(packet)) {
                PacketDistributor.sendToPlayer(player, packet);
            }
        }
    }
}