package com.deadman.voidspaces.init;

import com.deadman.voidspaces.VoidSpaces;
import com.deadman.voidspaces.block.entity.EngineEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client→Server: player action on a VoidEngine (enter, analyze, start/stop sim, change settings).
 */
public record EngineActionPacket(int actionOrdinal, int param, BlockPos enginePos)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EngineActionPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(VoidSpaces.MODID, "engine_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EngineActionPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public EngineActionPacket decode(RegistryFriendlyByteBuf buf) {
                    int action = buf.readVarInt();
                    int param = buf.readVarInt();
                    BlockPos pos = buf.readBlockPos();
                    return new EngineActionPacket(action, param, pos);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, EngineActionPacket packet) {
                    buf.writeVarInt(packet.actionOrdinal);
                    buf.writeVarInt(packet.param);
                    buf.writeBlockPos(packet.enginePos);
                }
            };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Action {
        ENTER,
        ANALYZE,
        START_SIM,
        STOP_SIM,
        SET_SPEED,         // param = new sim speed (1, 10, 20, 100)
        SET_CHUNK_SIZE,    // param = new chunk size (1-4)
        SET_RANDOM_TICK    // param = new randomTickSpeed for this dimension only (0-4096)
    }

    public Action getAction() {
        Action[] values = Action.values();
        if (actionOrdinal >= 0 && actionOrdinal < values.length) return values[actionOrdinal];
        return Action.ENTER;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!(player.serverLevel() instanceof ServerLevel level)) return;
            BlockPos pos = enginePos;
            if (!(level.getBlockEntity(pos) instanceof EngineEntity engine)) return;

            switch (getAction()) {
                case ENTER -> engine.teleportIn(player);
                case ANALYZE -> engine.analyzeContents(player);
                case START_SIM -> engine.startSimulation(param);
                case STOP_SIM -> engine.stopSimulation();
                case SET_SPEED -> engine.setSimulationSpeed(param);
                case SET_CHUNK_SIZE -> engine.setChunkSize(param);
                case SET_RANDOM_TICK -> engine.setRandomTickSpeed(param);
            }
        });
    }
}
