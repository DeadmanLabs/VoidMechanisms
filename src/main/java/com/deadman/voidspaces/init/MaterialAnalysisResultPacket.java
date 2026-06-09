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
 * Server→Client: result of a Materials Analysis scan on a VoidEngine dimension.
 * Contains a list of block types with their counts, and entity type counts.
 */
public record MaterialAnalysisResultPacket(
        List<BlockCount> blockCounts,
        List<EntityCount> entityCounts
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MaterialAnalysisResultPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(VoidSpaces.MODID, "material_analysis"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MaterialAnalysisResultPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public MaterialAnalysisResultPacket decode(RegistryFriendlyByteBuf buf) {
                    int blockSize = buf.readVarInt();
                    List<BlockCount> blocks = new ArrayList<>(blockSize);
                    for (int i = 0; i < blockSize; i++) {
                        ItemStack stack = ItemStack.STREAM_CODEC.decode(buf);
                        long count = buf.readVarLong();
                        blocks.add(new BlockCount(stack, count));
                    }
                    int entitySize = buf.readVarInt();
                    List<EntityCount> entities = new ArrayList<>(entitySize);
                    for (int i = 0; i < entitySize; i++) {
                        String type = buf.readUtf();
                        int count = buf.readVarInt();
                        entities.add(new EntityCount(type, count));
                    }
                    return new MaterialAnalysisResultPacket(blocks, entities);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, MaterialAnalysisResultPacket packet) {
                    buf.writeVarInt(packet.blockCounts.size());
                    for (BlockCount bc : packet.blockCounts) {
                        ItemStack.STREAM_CODEC.encode(buf, bc.stack);
                        buf.writeVarLong(bc.count);
                    }
                    buf.writeVarInt(packet.entityCounts.size());
                    for (EntityCount ec : packet.entityCounts) {
                        buf.writeUtf(ec.entityType);
                        buf.writeVarInt(ec.count);
                    }
                }
            };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Handles the packet on the client side — stores result for the open screen. */
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            com.deadman.voidspaces.client.gui.VoidEngineScreen.onAnalysisResult(this);
        });
    }

    public record BlockCount(ItemStack stack, long count) {}
    public record EntityCount(String entityType, int count) {}
}
