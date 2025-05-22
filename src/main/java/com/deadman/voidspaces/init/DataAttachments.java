package com.deadman.voidspaces.init;

import com.deadman.voidspaces.VoidSpaces;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public class DataAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, VoidSpaces.MODID);

    public static final Supplier<AttachmentType<ReturnPositionData>> RETURN_POSITION = ATTACHMENT_TYPES.register(
        "return_position", () -> AttachmentType.builder(() -> new ReturnPositionData(null, null))
            .serialize(ReturnPositionData.CODEC)
            .build()
    );

    public static class ReturnPositionData {
        public static final Codec<ReturnPositionData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                BlockPos.CODEC.optionalFieldOf("return_position").forGetter(data -> java.util.Optional.ofNullable(data.returnPosition)),
                ResourceKey.codec(Registries.DIMENSION).optionalFieldOf("return_dimension").forGetter(data -> java.util.Optional.ofNullable(data.returnDimension))
            ).apply(instance, (pos, dim) -> new ReturnPositionData(pos.orElse(null), dim.orElse(null)))
        );

        private BlockPos returnPosition;
        private ResourceKey<Level> returnDimension;

        public ReturnPositionData(BlockPos returnPosition, ResourceKey<Level> returnDimension) {
            this.returnPosition = returnPosition;
            this.returnDimension = returnDimension;
        }

        public BlockPos getReturnPosition() {
            return returnPosition;
        }

        public ResourceKey<Level> getReturnDimension() {
            return returnDimension;
        }

        public void setReturnPosition(BlockPos returnPosition) {
            this.returnPosition = returnPosition;
        }

        public void setReturnDimension(ResourceKey<Level> returnDimension) {
            this.returnDimension = returnDimension;
        }

        public boolean hasReturnData() {
            return returnPosition != null && returnDimension != null;
        }

        public void clear() {
            this.returnPosition = null;
            this.returnDimension = null;
        }
    }
}