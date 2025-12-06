package com.deadman.voidspaces.client;

import com.deadman.voidspaces.block.entity.EngineEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;

public class VoidEngineRenderer implements BlockEntityRenderer<EngineEntity> {

    public VoidEngineRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(EngineEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.endPortal());

        // Render all 6 faces of the cube with end portal effect
        renderFace(matrix, consumer, Direction.UP);
        renderFace(matrix, consumer, Direction.DOWN);
        renderFace(matrix, consumer, Direction.NORTH);
        renderFace(matrix, consumer, Direction.SOUTH);
        renderFace(matrix, consumer, Direction.EAST);
        renderFace(matrix, consumer, Direction.WEST);
    }

    private void renderFace(Matrix4f matrix, VertexConsumer consumer, Direction direction) {
        switch (direction) {
            case UP -> {
                consumer.addVertex(matrix, 0.0F, 1.0F, 0.0F);
                consumer.addVertex(matrix, 0.0F, 1.0F, 1.0F);
                consumer.addVertex(matrix, 1.0F, 1.0F, 1.0F);
                consumer.addVertex(matrix, 1.0F, 1.0F, 0.0F);
            }
            case DOWN -> {
                consumer.addVertex(matrix, 0.0F, 0.0F, 1.0F);
                consumer.addVertex(matrix, 0.0F, 0.0F, 0.0F);
                consumer.addVertex(matrix, 1.0F, 0.0F, 0.0F);
                consumer.addVertex(matrix, 1.0F, 0.0F, 1.0F);
            }
            case NORTH -> {
                consumer.addVertex(matrix, 1.0F, 1.0F, 0.0F);
                consumer.addVertex(matrix, 1.0F, 0.0F, 0.0F);
                consumer.addVertex(matrix, 0.0F, 0.0F, 0.0F);
                consumer.addVertex(matrix, 0.0F, 1.0F, 0.0F);
            }
            case SOUTH -> {
                consumer.addVertex(matrix, 0.0F, 1.0F, 1.0F);
                consumer.addVertex(matrix, 0.0F, 0.0F, 1.0F);
                consumer.addVertex(matrix, 1.0F, 0.0F, 1.0F);
                consumer.addVertex(matrix, 1.0F, 1.0F, 1.0F);
            }
            case EAST -> {
                consumer.addVertex(matrix, 1.0F, 1.0F, 1.0F);
                consumer.addVertex(matrix, 1.0F, 0.0F, 1.0F);
                consumer.addVertex(matrix, 1.0F, 0.0F, 0.0F);
                consumer.addVertex(matrix, 1.0F, 1.0F, 0.0F);
            }
            case WEST -> {
                consumer.addVertex(matrix, 0.0F, 1.0F, 0.0F);
                consumer.addVertex(matrix, 0.0F, 0.0F, 0.0F);
                consumer.addVertex(matrix, 0.0F, 0.0F, 1.0F);
                consumer.addVertex(matrix, 0.0F, 1.0F, 1.0F);
            }
        }
    }
}
