package com.deadman.voidspaces.init;

import com.deadman.voidspaces.VoidSpaces;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server→Client: periodic simulation state update sent while a dimension simulation is running.
 * Contains tick counts and InPort/OutPort throughput data.
 */
public record SimulationDataPacket(
        long simTicks,      // total accelerated simulation ticks elapsed
        long realTicks,     // real game ticks elapsed since simulation started
        int simulationSpeed,
        List<PortEntry> consumed,  // items consumed from InPorts
        List<PortEntry> produced   // items received by OutPorts
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SimulationDataPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(VoidSpaces.MODID, "simulation_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SimulationDataPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public SimulationDataPacket decode(RegistryFriendlyByteBuf buf) {
                    long simTicks = buf.readVarLong();
                    long realTicks = buf.readVarLong();
                    int speed = buf.readVarInt();
                    int consumedSize = buf.readVarInt();
                    List<PortEntry> consumed = new ArrayList<>(consumedSize);
                    for (int i = 0; i < consumedSize; i++) {
                        ItemStack stack = ItemStack.STREAM_CODEC.decode(buf);
                        long count = buf.readVarLong();
                        consumed.add(new PortEntry(stack, count));
                    }
                    int producedSize = buf.readVarInt();
                    List<PortEntry> produced = new ArrayList<>(producedSize);
                    for (int i = 0; i < producedSize; i++) {
                        ItemStack stack = ItemStack.STREAM_CODEC.decode(buf);
                        long count = buf.readVarLong();
                        produced.add(new PortEntry(stack, count));
                    }
                    return new SimulationDataPacket(simTicks, realTicks, speed, consumed, produced);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, SimulationDataPacket packet) {
                    buf.writeVarLong(packet.simTicks);
                    buf.writeVarLong(packet.realTicks);
                    buf.writeVarInt(packet.simulationSpeed);
                    buf.writeVarInt(packet.consumed.size());
                    for (PortEntry e : packet.consumed) {
                        ItemStack.STREAM_CODEC.encode(buf, e.stack);
                        buf.writeVarLong(e.count);
                    }
                    buf.writeVarInt(packet.produced.size());
                    for (PortEntry e : packet.produced) {
                        ItemStack.STREAM_CODEC.encode(buf, e.stack);
                        buf.writeVarLong(e.count);
                    }
                }
            };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            com.deadman.voidspaces.client.gui.VoidEngineScreen.onSimulationData(this);
        });
    }

    /** Computed rate: items per real tick (float). Returns 0 if no ticks elapsed. */
    public float getRatePerTick(PortEntry entry) {
        if (realTicks <= 0) return 0f;
        return (float) entry.count / realTicks;
    }

    public record PortEntry(ItemStack stack, long count) {}
}
