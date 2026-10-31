package com.kve.client.structure;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * Draws {@link StructureWorldBuildSession}'s tracked box as a
 * wireframe in the real world, same
 * {@code LevelRenderer.renderLineBox()}/{@code RenderLevelStageEvent} setup
 * {@link StructureGhostPreviewRenderer} already uses for its own bounding
 * box (see that class's doc for why this hooks the render pass this way).
 * Unlike the ghost preview, there are no translucent "hologram" blocks to
 * draw here - once a world-build session is armed, the blocks inside the
 * box are REAL, already rendering themselves normally; this only ever
 * draws the box outline around them.
 * <p>
 * A different color from the ghost preview's cyan (amber/orange here) so
 * a player who has both a ghost preview AND an active world-build session
 * armed at once - unusual, but not prevented - can tell them apart at a
 * glance.
 */
@OnlyIn(Dist.CLIENT)
public final class StructureWorldBuildRenderer {

    private StructureWorldBuildRenderer() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (!StructureWorldBuildSession.isEnabled()) return;

        BlockPos origin = StructureWorldBuildSession.getOrigin();
        int[] size = StructureWorldBuildSession.getSize();
        if (origin == null) return;

        Vec3 camPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(origin.getX() - camPos.x, origin.getY() - camPos.y, origin.getZ() - camPos.z);

        MultiBufferSource.BufferSource buffers = net.minecraft.client.Minecraft.getInstance().renderBuffers().bufferSource();
        AABB box = new AABB(0, 0, 0, size[0], size[1], size[2]);
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(poseStack, lines, box, 1.00f, 0.65f, 0.15f, 0.85f);
        buffers.endBatch(RenderType.lines());

        poseStack.popPose();
    }
}
