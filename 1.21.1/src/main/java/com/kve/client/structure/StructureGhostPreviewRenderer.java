package com.kve.client.structure;

import com.kve.common.structure.StructureVoxelGrid;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.List;

import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
public final class StructureGhostPreviewRenderer {

    private StructureGhostPreviewRenderer() {}

    private static final int BUILD_BUFFER_CAPACITY = 786432;

    /**
     * Identity + version this bake corresponds to - mirrors
     * {@code StructureSceneRenderer}'s {@code cachedGrid}/{@code cachedGridVersion}
     * pair so an unedited structure (the common case) costs nothing to
     * re-check: one reference comparison and one int comparison.
     */
    private static StructureVoxelGrid bakedGrid;
    private static int bakedGridVersion = -1;

    private static VertexBuffer blocksVbo;
    private static VertexBuffer blocksCutoutVbo;
    private static VertexBuffer boundsVbo;
    /**
     * Baked magenta wireframe placeholders, one unit-cube per cell whose real
     * model threw during {@link #ensureBaked()} (e.g. vines/other non-full
     * "incomplete" blocks whose model lookup fails outside a real chunk
     * context). Drawn with {@link RenderType#lines()}, same fallback language
     * {@code StructureSceneRenderer#drawPlaceholderCube} already uses in the
     * GUI-embedded viewport. See the class doc + {@link #ensureBaked()} for why
     * this exists: the old behavior silently dropped the cell entirely (a
     * literal hole in the hologram, with zero trace in the log of why) - this
     * makes the failure visible both on screen and in the log instead. Null
     * if nothing failed to bake.
     */
    private static VertexBuffer placeholderVbo;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (!StructureGhostPreview.isEnabled() || !StructureGhostPreview.hasAnchor()) return;

        StructureVoxelGrid grid = StructureGhostPreview.getGrid();
        BlockPos anchor = StructureGhostPreview.getAnchor();
        if (grid == null || anchor == null || grid.isEmpty()) return;

        ensureBaked(grid);
        if (blocksVbo == null && blocksCutoutVbo == null && placeholderVbo == null && BE_CELLS.isEmpty()) return;

        Vec3 camPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack() != null ? event.getPoseStack() : new PoseStack();
        Matrix4f view = event.getModelViewMatrix();
        Matrix4f projection = event.getProjectionMatrix();

        poseStack.pushPose();
        try {
            renderInner(poseStack, anchor, camPos, view, projection);
            renderBlockEntities(poseStack);
        } finally {
            RenderSystem.disablePolygonOffset();
            poseStack.popPose();
        }
    }

    private static void renderInner(PoseStack poseStack, BlockPos anchor, Vec3 camPos, Matrix4f view, Matrix4f projection) {
        poseStack.translate(anchor.getX() - camPos.x, anchor.getY() - camPos.y, anchor.getZ() - camPos.z);
        Matrix4f modelView = ghostModelView(view, poseStack.last().pose());

        RenderSystem.polygonOffset(-1.0f, -10.0f);
        RenderSystem.enablePolygonOffset();

        if (blocksCutoutVbo != null) {
            RenderSystem.polygonOffset(-1.0f, -30.0f);
            RenderSystem.disableCull();
            // bloques opacos y con recorte (grama, pasto, hojas): el recorte solo escribe profundidad donde hay textura
            RenderType cutout = RenderType.cutoutMipped();
            cutout.setupRenderState();
            blocksCutoutVbo.bind();
            blocksCutoutVbo.drawWithShader(modelView, projection, RenderSystem.getShader());
            VertexBuffer.unbind();
            cutout.clearRenderState();
            RenderSystem.enableCull();
        }

        if (blocksVbo != null) {
            // despues de los opacos, para que el vidrio y el agua dejen ver lo que hay detras
            RenderSystem.polygonOffset(-1.0f, -10.0f);
            RenderType translucent = RenderType.translucent();
            translucent.setupRenderState();
            RenderSystem.depthMask(true);
            blocksVbo.bind();
            blocksVbo.drawWithShader(modelView, projection, RenderSystem.getShader());
            VertexBuffer.unbind();
            translucent.clearRenderState();
        }


        RenderSystem.disablePolygonOffset();

        if (boundsVbo != null) {
            RenderType lines = RenderType.lines();
            lines.setupRenderState();
            boundsVbo.bind();
            boundsVbo.drawWithShader(modelView, projection, RenderSystem.getShader());
            VertexBuffer.unbind();
            lines.clearRenderState();
        }

        if (placeholderVbo != null) {
            RenderType lines = RenderType.lines();
            lines.setupRenderState();
            placeholderVbo.bind();
            placeholderVbo.drawWithShader(modelView, projection, RenderSystem.getShader());
            VertexBuffer.unbind();
            lines.clearRenderState();
        }
    }

    static Matrix4f ghostModelView(Matrix4f view, Matrix4f anchorPose) {
        return new Matrix4f(view).mul(anchorPose);
    }

    /**
     * Rebakes {@link #blocksVbo}/{@link #boundsVbo} only when {@code grid}
     * or its {@link StructureVoxelGrid#version()} differs from what's
     * currently baked - i.e. only right after an actual edit to the armed
     * structure, never merely because another frame ticked over.
     */
    private static void ensureBaked(StructureVoxelGrid grid) {
        if (bakedGrid == grid && bakedGridVersion == grid.version()) return;

        freeBuffers();
        int targetVersion = grid.version();

        Minecraft mc = Minecraft.getInstance();
        BlockRenderDispatcher dispatcher = mc.getBlockRenderer();
        int fullbright = LightTexture.pack(15, 15);

        RenderType translucent = RenderType.translucent();
        KveMeshBuilder blockBuf = new KveMeshBuilder(BUILD_BUFFER_CAPACITY, translucent.mode(), translucent.format());

        RenderType cutoutType = RenderType.cutoutMipped();
        KveMeshBuilder cutoutBuf = new KveMeshBuilder(BUILD_BUFFER_CAPACITY, cutoutType.mode(), cutoutType.format());

        boolean[] hasContent = new boolean[] { false, false };
        boolean[] currentCellNonFull = new boolean[] { false };
        MultiBufferSource singleSink = rt -> {
            // renderSingleBlock pide Sheets.cutoutBlockSheet() para todo lo que no es translucido
            boolean translucentLayer = rt == RenderType.translucent()
                    || rt == Sheets.translucentCullBlockSheet() || rt == Sheets.translucentItemSheet();
            if (!translucentLayer || currentCellNonFull[0]) {
                hasContent[1] = true;
                return cutoutBuf.buffer;
            }
            hasContent[0] = true;
            return blockBuf.buffer;
        };

        KveMeshBuilder placeholderBuf = null;
        int failCount = 0;

        List<BlockPos> visible = grid.visibleCellPositions();
        for (BlockPos p : visible) {
            BlockState state = grid.get(p);
            if (state.isAir()) continue;
            boolean nonFull;
            try {
                VoxelShape shape = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                nonFull = !Block.isShapeFullBlock(shape);
            } catch (Exception ex) {
                nonFull = true;
            }
            currentCellNonFull[0] = nonFull;
            if (StructureSceneRenderer.isDrawnByBlockEntityRenderer(state)) {
                net.minecraft.world.level.block.entity.BlockEntity be =
                        StructureSceneRenderer.buildPreviewBlockEntityFor(Minecraft.getInstance(), grid, p, state);
                if (be != null) BE_CELLS.add(new BeCell(p.immutable(), be));
                continue;
            }
            PoseStack local = new PoseStack();
            local.translate(p.getX(), p.getY(), p.getZ());
            try {
                dispatcher.renderSingleBlock(state, local, singleSink, fullbright, OverlayTexture.NO_OVERLAY);
            } catch (Exception ex) {
                // One broken/mod block model must never take down the whole
                // bake - but it must also not just disappear silently. Log it
                // (state + position, so it's actually diagnosable next time)
                // and draw a magenta wireframe placeholder in its place so
                // the hologram never shows a literal hole for it.
                failCount++;
                com.kve.CraftEditorMod.LOGGER.warn(
                        "[Shovel JS] structure ghost: fallo al renderizar {} en {} (relativo a la estructura): {}",
                        state, p, ex.toString());
                if (placeholderBuf == null) {
                    RenderType lines = RenderType.lines();
                    placeholderBuf = new KveMeshBuilder(4096, lines.mode(), lines.format());
                }
                PoseStack placeholderPose = new PoseStack();
                placeholderPose.translate(p.getX(), p.getY(), p.getZ());
                LevelRenderer.renderLineBox(placeholderPose, placeholderBuf.buffer, 0, 0, 0, 1, 1, 1, 1f, 0f, 1f, 1f);
            }
        }
        boolean blockBufHasContent = hasContent[0];
        boolean cutoutBufHasContent = hasContent[1];
        if (failCount > 0) {
            com.kve.CraftEditorMod.LOGGER.warn(
                    "[Shovel JS] structure ghost: {} celda(s) no se pudieron renderizar y se muestran como wireframe magenta - ver warnings arriba para el detalle de cada bloque/posición.",
                    failCount);
        }

        if (placeholderBuf != null) placeholderVbo = upload(placeholderBuf);

        // Only build/upload a VertexBuffer for a bucket that actually
        // received geometry - an empty structure (or one that's 100%
        // cutout, or 100% solid) must not upload an empty buffer, same
        // caution the code already takes with boundsVbo below.
        if (blockBufHasContent) {
            blocksVbo = upload(blockBuf);
        } else {
            blockBuf.discard();
        }

        if (cutoutBufHasContent) {
            blocksCutoutVbo = upload(cutoutBuf);
        } else {
            cutoutBuf.discard();
        }

        BlockPos[] bounds = grid.computeBounds();
        if (bounds != null) {
            AABB box = new AABB(bounds[0].getX(), bounds[0].getY(), bounds[0].getZ(),
                    bounds[1].getX() + 1, bounds[1].getY() + 1, bounds[1].getZ() + 1);
            RenderType lines = RenderType.lines();
            KveMeshBuilder lineBuf = new KveMeshBuilder(1024, lines.mode(), lines.format());
            LevelRenderer.renderLineBox(new PoseStack(), lineBuf.buffer, box, 0.30f, 0.85f, 1.00f, 0.85f);
            boundsVbo = upload(lineBuf);
        }

        bakedGrid = grid;
        bakedGridVersion = targetVersion;
    }

    /** Sube el builder a un VertexBuffer nuevo, o devuelve null si el builder no tiene vertices. */
    private static VertexBuffer upload(KveMeshBuilder builder) {
        VertexBuffer vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vbo.bind();
        boolean ok = builder.uploadTo(vbo);
        VertexBuffer.unbind();
        if (ok) return vbo;
        vbo.close();
        return null;
    }

    /** Celdas dibujadas por su BlockEntityRenderer real en vez de horneadas - ver StructureSceneRenderer#isDrawnByBlockEntityRenderer(). Las BlockEntity de preview tienen referencia al ClientLevel, por eso se sueltan en freeBuffers() (y con el, en resetCache() al salir del mundo). */
    private record BeCell(BlockPos localPos, net.minecraft.world.level.block.entity.BlockEntity be) {}
    private static final java.util.List<BeCell> BE_CELLS = new java.util.ArrayList<>();

    private static void renderBlockEntities(PoseStack poseStack) {
        if (BE_CELLS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        var buffers = mc.renderBuffers().bufferSource();
        float partialTick = mc.getTimer().getGameTimeDeltaPartialTick(true);
        int fullbright = net.minecraft.client.renderer.LightTexture.pack(15, 15);
        for (BeCell cell : BE_CELLS) {
            var renderer = mc.getBlockEntityRenderDispatcher().getRenderer(cell.be());
            if (renderer == null) continue;
            poseStack.pushPose();
            try {
                poseStack.translate(cell.localPos().getX(), cell.localPos().getY(), cell.localPos().getZ());
                renderer.render(cell.be(), partialTick, poseStack, buffers, fullbright, OverlayTexture.NO_OVERLAY);
            } catch (Exception ex) {
                // un renderer de mod roto nunca debe tirar abajo el resto del holograma
            } finally {
                poseStack.popPose();
            }
        }
        buffers.endBatch();
    }

    private static void freeBuffers() {
        BE_CELLS.clear();
        if (blocksVbo != null) { blocksVbo.close(); blocksVbo = null; }
        if (blocksCutoutVbo != null) { blocksCutoutVbo.close(); blocksCutoutVbo = null; }
        if (boundsVbo != null) { boundsVbo.close(); boundsVbo = null; }
        if (placeholderVbo != null) { placeholderVbo.close(); placeholderVbo = null; }
    }

    public static void resetCache() {
        freeBuffers();
        bakedGrid = null;
        bakedGridVersion = -1;
    }
}
