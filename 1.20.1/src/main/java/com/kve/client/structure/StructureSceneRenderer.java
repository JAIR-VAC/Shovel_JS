package com.kve.client.structure;

import com.kve.common.structure.StructureVoxelGrid;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class StructureSceneRenderer {

    private StructureSceneRenderer() {}

    /**
     * Perf cache for {@link #buildPreviewBlockEntity()} instances - see the
     * class doc's "Perf" section. {@code cachedGrid}/{@code cachedGridVersion}
     * together identify which grid+version the current cache contents were
     * built for; {@link #ensureBeCacheFresh()} clears and rebuilds the
     * association whenever either changes (a different {@code StructureVoxelGrid}
     * instance, or the same one but mutated). A plain identity comparison on
     * {@code cachedGrid} is enough - only one structure editor screen (hence
     * one live grid) is ever open at a time.
     */
    private static StructureVoxelGrid cachedGrid;
    private static int cachedGridVersion = -1;
    private static final Map<BlockPos, BlockEntity> BE_RENDER_CACHE = new HashMap<>();

    /**
     * Perf: geometria de bloque horneada a
     * VertexBuffer, mismo patron cache-por-version que
     * {@link #cachedGrid}/{@link #cachedGridVersion} de arriba ya usa para
     * el cache de BlockEntity, pero en variables separadas - la geometria y
     * las instancias de BlockEntity se invalidan por la misma razon
     * (version() del grid cambio) pero son dos caches independientes, no
     * hay necesidad de acoplarlos.
     */
    private static StructureVoxelGrid blockGeoBakedGrid;
    private static int blockGeoBakedVersion = -1;
    private static final java.util.LinkedHashMap<RenderType, VertexBuffer> bakedByType = new java.util.LinkedHashMap<>();
    private static VertexBuffer bakedPlaceholderVbo;
    /** Mismo tamaño inicial que StructureGhostPreviewRenderer usa para su propio bake, ver esa clase para el porque. */
    private static final int BUILD_BUFFER_CAPACITY = 786432;

    private static final int SPAWN_CAP_OUTLINE_ARGB = 0xFFFF3355;

    private static int lastLoggedVersion = -2;

    private static MultiBufferSource.BufferSource localBuffers;

    private static MultiBufferSource.BufferSource localBufferSource() {
        if (localBuffers == null) {
            localBuffers = MultiBufferSource.immediate(new BufferBuilder(786432));
        }
        return localBuffers;
    }

    private static void ensureBeCacheFresh(StructureVoxelGrid grid) {
        if (cachedGrid == grid && cachedGridVersion == grid.version()) return;
        BE_RENDER_CACHE.clear();
        cachedGrid = grid;
        cachedGridVersion = grid.version();
    }

    /**
     * Drops every cached preview {@link BlockEntity} and forgets which grid
     * they belonged to. Each cached entry holds a strong reference to the
     * {@link net.minecraft.client.multiplayer.ClientLevel} that was active
     * when it was built (see {@link #buildPreviewBlockEntity()}'s {@code
     * be.setLevel(mc.level)}), so leaving this cache populated across a
     * disconnect/world-change pins that entire (already-unloaded) level in
     * memory until the next time a structure editor screen happens to open
     * a grid with a different identity/version than whatever was cached
     * before. Called from {@link com.kve.client.events.WorldChangeListener#onLoggedOut}
     * for the same reason {@link com.kve.client.structure.StructureWorldBuildSession#clear()}
     * is - client-side render state tied to a specific world must not
     * outlive that world.
     */
    public static void resetCache() {
        BE_RENDER_CACHE.clear();
        cachedGrid = null;
        cachedGridVersion = -1;
        freeBlockGeometryBuffers();
        blockGeoBakedGrid = null;
        blockGeoBakedVersion = -1;
    }

    private static void freeBlockGeometryBuffers() {
        for (VertexBuffer vbo : bakedByType.values()) vbo.close();
        bakedByType.clear();
        if (bakedPlaceholderVbo != null) { bakedPlaceholderVbo.close(); bakedPlaceholderVbo = null; }
    }

    /**
     * Bakes every visible cell's block geometry into static VertexBuffers
     * (2 real buckets by render type + a 3rd for any cell whose model
     * threw), only when {@code grid} or its
     * {@link StructureVoxelGrid#version()} differs from what's currently
     * baked - same cache-by-identity+version contract
     * {@link #ensureBeCacheFresh()} already uses right above for the
     * BlockEntity cache, and the exact same VertexBuffer-baking technique
     * {@code StructureGhostPreviewRenderer#ensureBaked} already uses for
     * the world-anchored hologram.
     * <p>
     * One difference from the hologram version: that one bakes at the
     * grid's own local origin and reapplies an anchor translate every
     * frame, because the anchor (and the real world camera) can move
     * independently of the baked geometry. Here the roles are reversed -
     * the structure never moves, {@link OrbitCamera} orbits AROUND it - so
     * there is nothing to reapply per frame at all; the baked vertices
     * already sit at each cell's real grid coordinate, and the current
     * frame's view matrix (supplied by the caller via its own
     * {@code poseStack}) is the only transform {@link #render} still needs
     * to apply at draw time.
     * <p>
     * Another difference: the hologram forces its main bucket into
     * {@code RenderType.translucent()} for the deliberate ghost tint. This
     * viewport is a real editor preview, not a ghost, so blocks should
     * look like normal opaque blocks - the main bucket here uses
     * {@link RenderType#solid()} instead. Trade-off accepted: a structure
 * containing a genuinely translucent vanilla block (glass, ice,...)
     * previews as opaque rather than see-through, same class of
     * "not pixel-perfect but never wrong-looking" trade-off the hologram's
     * own doc already accepts for non-full blocks losing their tint.
     * <p>
 * Block ENTITIES (chests, signs, banners,...) are deliberately NOT
     * part of this bake - see {@link #render}, which still walks
     * {@code visible} and renders each cached {@link BlockEntity} live
     * every frame via its real {@code BlockEntityRenderer}. Those can be
     * animated (a chest lid mid-open) and read {@code partialTick}, so
     * baking them into a static buffer would freeze that animation - only
     * the static block MODEL geometry belongs in this bake.
     */
    private static void ensureBlockGeometryBaked(Minecraft mc, StructureVoxelGrid grid) {
        if (blockGeoBakedGrid == grid && blockGeoBakedVersion == grid.version()) return;

        freeBlockGeometryBuffers();
        int targetVersion = grid.version();

        BlockRenderDispatcher dispatcher = mc.getBlockRenderer();
        int fullbright = LightTexture.pack(15, 15);

        java.util.LinkedHashMap<RenderType, BufferBuilder> buildersByType = new java.util.LinkedHashMap<>();
        MultiBufferSource singleSink = rt -> buildersByType.computeIfAbsent(rt, t -> {
            BufferBuilder buf = new BufferBuilder(BUILD_BUFFER_CAPACITY);
            buf.begin(t.mode(), t.format());
            return buf;
        });

        BufferBuilder placeholderBuf = null;
        int failCount = 0;

        for (BlockPos p : grid.visibleCellPositions()) {
            BlockState state = grid.get(p);
            if (state.isAir()) continue;
            if (isDrawnByBlockEntityRenderer(state)) continue;

            PoseStack cellPose = new PoseStack();
            cellPose.translate(p.getX(), p.getY(), p.getZ());
            try {
                FluidState fluidState = state.getFluidState();
                if (!fluidState.isEmpty()) {
                    renderFluidCube(fluidState, cellPose, singleSink, fullbright);
                } else {
                    dispatcher.renderSingleBlock(state, cellPose, singleSink, fullbright, OverlayTexture.NO_OVERLAY);
                }
            } catch (Exception ex) {
                // Mismo fallback de 2 pasos que el render() en vivo ya
                // usaba antes de este bake existir (Compat Oculus/
                // Embeddium B) - intentar el ItemStack del bloque primero,
                // wireframe magenta si tampoco hay o tambien falla.
                PoseStack fallbackPose = new PoseStack();
                fallbackPose.translate(p.getX(), p.getY(), p.getZ());
                boolean drewItem;
                try {
                    drewItem = renderBlockItemFallback(mc, state, fallbackPose, singleSink, fullbright);
                } catch (Exception ignored) {
                    drewItem = false;
                }
                if (!drewItem) {
                    failCount++;
                    com.kve.CraftEditorMod.LOGGER.warn(
                            "[Shovel JS] structure scene: fallo al renderizar {} en {} (preview GUI): {}",
                            state, p, ex.toString());
                    if (placeholderBuf == null) {
                        RenderType lines = RenderType.lines();
                        placeholderBuf = new BufferBuilder(4096);
                        placeholderBuf.begin(lines.mode(), lines.format());
                    }
                    PoseStack placeholderPose = new PoseStack();
                    placeholderPose.translate(p.getX(), p.getY(), p.getZ());
                    LevelRenderer.renderLineBox(placeholderPose, placeholderBuf, 0, 0, 0, 1, 1, 1, 1f, 0f, 1f, 1f);
                }
            }
        }

        if (failCount > 0) {
            com.kve.CraftEditorMod.LOGGER.warn(
                    "[Shovel JS] structure scene: {} celda(s) no se pudieron renderizar en el preview GUI y se muestran como wireframe magenta.",
                    failCount);
        }

        if (placeholderBuf != null) {
            VertexBuffer pvbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
            pvbo.bind();
            pvbo.upload(placeholderBuf.end());
            VertexBuffer.unbind();
            bakedPlaceholderVbo = pvbo;
        }

        for (var entry : buildersByType.entrySet()) {
            VertexBuffer vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
            vbo.bind();
            vbo.upload(entry.getValue().end());
            VertexBuffer.unbind();
            bakedByType.put(entry.getKey(), vbo);
        }

        blockGeoBakedGrid = grid;
        blockGeoBakedVersion = targetVersion;
    }

    private static final Map<BlockState, Boolean> BER_DRAWN_CACHE = new HashMap<>();

    static boolean isDrawnByBlockEntityRenderer(BlockState state) {
        if (state.getRenderShape() != net.minecraft.world.level.block.RenderShape.ENTITYBLOCK_ANIMATED) return false;
        if (!(state.getBlock() instanceof EntityBlock eb)) return false;
        return BER_DRAWN_CACHE.computeIfAbsent(state, s -> {
            try {
                BlockEntity be = eb.newBlockEntity(BlockPos.ZERO, s);
                return be != null && Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(be) != null;
            } catch (Exception ex) {
                return false;
            }
        });
    }

    static BlockEntity buildPreviewBlockEntityFor(Minecraft mc, StructureVoxelGrid grid, BlockPos pos, BlockState state) {
        return buildPreviewBlockEntity(mc, grid, pos, state);
    }

    private static BlockEntity buildPreviewBlockEntity(Minecraft mc, StructureVoxelGrid grid, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof EntityBlock eb)) return null;
        BlockEntity be;
        try {
            be = eb.newBlockEntity(pos, state);
        } catch (Exception ex) {
            return null;
        }
        if (be == null) return null;
        CompoundTag nbt = grid.getBlockEntityNbt(pos);
        if (nbt != null) {
            try {
                be.load(nbt);
            } catch (Exception ex) {
            }
        }
        if (mc.level != null) {
            try {
                be.setLevel(mc.level);
            } catch (Exception ex) {
                // Some block entities assume their level is the one they're actually
                // placed in; render without one rather than crash the preview over it.
            }
        }
        return be;
    }

    /**
     * @param vpX/vpY/vpW/vpH viewport rectangle in GUI (logical) pixel space
     * @param highlight       currently ray-picked cell (for a wireframe outline), or null
     */
    public static void render(int vpX, int vpY, int vpW, int vpH,
                               StructureVoxelGrid grid, OrbitCamera camera,
                               BlockPos highlight, boolean showGrid) {
        render(vpX, vpY, vpW, vpH, grid, camera, highlight, showGrid, null, null);
    }

    /**
     * Overload adding the box/line tool preview: while the
     * player has placed the first corner of a box/line fill but not yet
     * the second, {@code previewA}/{@code previewB} (both non-null, or
     * both null for "no tool preview active") draw a live wireframe box
     * spanning the placed anchor and the cell currently under the cursor,
     * so the fill's extent is visible BEFORE committing it - the same
     * "see it before you place it" reasoning as {@code highlight}'s single-
     * cell outline, just for the multi-cell tools added alongside it.
     */
    public static void render(int vpX, int vpY, int vpW, int vpH,
                               StructureVoxelGrid grid, OrbitCamera camera,
                               BlockPos highlight, boolean showGrid,
                               BlockPos previewA, BlockPos previewB) {
        if (vpW <= 0 || vpH <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        var window = mc.getWindow();
        double scale = window.getGuiScale();

        int fbX = (int) Math.round(vpX * scale);
        int fbW = (int) Math.round(vpW * scale);
        int fbH = (int) Math.round(vpH * scale);
        int fbY = window.getHeight() - (int) Math.round((vpY + vpH) * scale);

        mc.renderBuffers().bufferSource().endBatch();

        boolean prevScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        int[] prevBox = new int[4];
        int sx = fbX, sy = fbY, sw = fbW, sh = fbH;
        if (prevScissor) {
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, prevBox);
            int x0 = Math.max(sx, prevBox[0]), y0 = Math.max(sy, prevBox[1]);
            int x1 = Math.min(sx + sw, prevBox[0] + prevBox[2]), y1 = Math.min(sy + sh, prevBox[1] + prevBox[3]);
            if (x1 <= x0 || y1 <= y0) return;
            sx = x0; sy = y0; sw = x1 - x0; sh = y1 - y0;
        }

        RenderSystem.enableScissor(sx, sy, sw, sh);
        GL11.glViewport(fbX, fbY, fbW, fbH);
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);

        Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting savedSorting = RenderSystem.getVertexSorting();
        Matrix4f projection = camera.projectionMatrix((float) vpW / (float) Math.max(1, vpH));
        RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);

        var modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.last().pose().identity();
        // La identity de arriba solo toca el PoseStack en memoria - el uniform
        // de modelview que el shader realmente usa no se actualiza hasta que
        // se llama applyModelViewMatrix(). Sin esto, el frame puede dibujar
        // con un uniform desfasado respecto al PoseStack que este método
        // arma a mano más abajo - visualmente se puede leer como "no se
        // dibuja nada" aunque los vértices y la proyección estén bien.
        RenderSystem.applyModelViewMatrix();

        try {
            PoseStack poseStack = new PoseStack();
            Matrix4f viewMatrix = camera.viewMatrix();
            poseStack.last().pose().mul(viewMatrix);

            // Compat Oculus/Embeddium (A) - ver el javadoc de localBufferSource():
            // NO usar mc.renderBuffers().bufferSource() acá, esa es justo la
            // instancia compartida que el shader pack engancha.
            MultiBufferSource.BufferSource buffers = localBufferSource();
            int fullbright = LightTexture.pack(15, 15);
            float partialTick = mc.getFrameTime();

            // Block RenderTypes sample the game's lightmap texture (bound to
            // texture unit 2) using the packed-light int above - that binding
            // is normally done once per frame by the world's own render loop
            // (LevelRenderer/GameRenderer) before any block/entity is drawn.
            // A GUI screen never does that on its own, so without explicitly
            // turning the light layer on here, unit 2 has nothing valid bound
            // and every block face samples as black - invisible against this
            // screen's dark background, while the wireframe boxes below still
            // show fine since their color is baked directly into the line
            // vertices instead of coming from a lightmap lookup.
            //
            // turnOnLightLayer() por sí solo NO alcanza: solo activa/bindea
            // la textura de lightmap tal cual está en memoria en ese momento;
            // updateLightTexture(partialTick) es lo que efectivamente sube
            // los valores de luz al GPU antes de bindearla. Sin este paso, la
            // textura de lightmap puede quedar con contenido viejo/sin
            // inicializar la primera vez que se abre este screen en la
            // sesión (nunca se disparó un frame de mundo real todavía que la
            // hubiese poblado), y el resultado es el mismo síntoma: caras
            // muestreando negro/uniforme.
            mc.gameRenderer.lightTexture().updateLightTexture(partialTick);
            mc.gameRenderer.lightTexture().turnOnLightLayer();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.setShaderTexture(0, net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS);
            ensureBeCacheFresh(grid);
            List<BlockPos> visible = grid.visibleCellPositions();
            if (lastLoggedVersion != grid.version()) {
                lastLoggedVersion = grid.version();
                com.kve.CraftEditorMod.LOGGER.debug("[Shovel JS] structure render: cellCount={} visibleCellPositions={}",
                        grid.cellCount(), visible.size());
            }
            ensureBlockGeometryBaked(mc, grid);

            try {
                for (var entry : bakedByType.entrySet()) {
                    RenderType type = entry.getKey();
                    VertexBuffer vbo = entry.getValue();
                    type.setupRenderState();
                    vbo.bind();
                    vbo.drawWithShader(poseStack.last().pose(), RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
                    VertexBuffer.unbind();
                    type.clearRenderState();
                }
                if (bakedPlaceholderVbo != null) {
                    RenderType linesType = RenderType.lines();
                    linesType.setupRenderState();
                    bakedPlaceholderVbo.bind();
                    bakedPlaceholderVbo.drawWithShader(poseStack.last().pose(), RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
                    VertexBuffer.unbind();
                    linesType.clearRenderState();
                }

                // Block entities siguen vivas cada frame (animacion/estado
                // real) - ver el javadoc de ensureBlockGeometryBaked(),
                // seccion final, sobre por que esto NUNCA se hornea.
                for (BlockPos p : visible) {
                    BlockState state = grid.get(p);
                    if (state.isAir()) continue;
                    BlockEntity be = BE_RENDER_CACHE.computeIfAbsent(p, pp -> buildPreviewBlockEntity(mc, grid, pp, state));
                    if (be != null) {
                        BlockEntityRenderer<BlockEntity> renderer = mc.getBlockEntityRenderDispatcher().getRenderer(be);
                        if (renderer != null) {
                            try {
                                PoseStack bePose = new PoseStack();
                                bePose.last().pose().mul(viewMatrix);
                                bePose.translate(p.getX(), p.getY(), p.getZ());
                                renderer.render(be, partialTick, bePose, buffers, fullbright, OverlayTexture.NO_OVERLAY);
                            } catch (Exception ex) {
                                // one broken/mod block-entity renderer must never take down the whole preview
                            }
                        }
                    }
                }
                buffers.endBatch();
            } finally {
                mc.gameRenderer.lightTexture().turnOffLightLayer();
            }

            if (showGrid) {
                drawBoundsWire(poseStack, grid);
            }
            if (!grid.spawnCapsView().isEmpty()) {
                for (BlockPos capPos : grid.spawnCapsView().keySet()) {
                    drawCellOutline(poseStack, capPos, SPAWN_CAP_OUTLINE_ARGB);
                }
            }
            if (highlight != null) {
                drawCellOutline(poseStack, highlight, 0xFFFFFFFF);
            }
            if (previewA != null && previewB != null) {
                drawToolPreviewBox(poseStack, previewA, previewB);
            }
        } finally {
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(savedProjection, savedSorting);
            if (prevScissor) RenderSystem.enableScissor(prevBox[0], prevBox[1], prevBox[2], prevBox[3]);
            else RenderSystem.disableScissor();
            GL11.glViewport(0, 0, window.getWidth(), window.getHeight());
        }
    }

    /**
     * Dibuja un cubo unidad (0,0,0)-(1,1,1) para una celda con {@link FluidState}
     * (agua/lava puestos por {@link com.kve.client.gui.MachineStationPreview}'s
     * MULTIBLOCK_OVERRIDES, ej. el agua de create:splashing o la soul campfire-
     * adyacente de create:haunting) usando la textura "still" real del fluido +
     * su tint real via {@link IClientFluidTypeExtensions} - ver el comentario
     * largo en el call site (loop de bake) para el diagnostico completo de por
     * que {@code renderSingleBlock()} no dibuja nada para estas celdas.
     * <p>
     * Deliberadamente NO usa {@link net.minecraft.client.renderer.LiquidBlockRenderer}
     * (el que usa el chunk mesher real) porque ese pide un
     * {@code BlockAndTintGetter} con vecinos reales para calcular AO/altura de
     * superficie - este preview es una celda aislada sin ese contexto. Un cubo
     * simple con la sprite still + tint es visualmente indistinguible para una
     * celda decorativa unica como esta (mismo truco que cualquier GUI de tanque
     * de fluido Forge), a costo de no mostrar el efecto de "superficie con
     * onditas" del fluido fluyendo de verdad - trade-off aceptado, igual que el
     * de blockState.isAir()/hologram en otros comentarios de este archivo.
     */
    private static void renderFluidCube(FluidState fluidState, PoseStack poseStack,
                                         MultiBufferSource buffers, int packedLight) {
        var fluidType = fluidState.getType().getFluidType();
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluidType);
        net.minecraft.resources.ResourceLocation stillTexture = ext.getStillTexture();
        if (stillTexture == null) return;
        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(stillTexture);

        int tint = ext.getTintColor();
        float a = ((tint >>> 24) & 0xFF) / 255f;
        if (a <= 0f) a = 1f;
        float r = ((tint >> 16) & 0xFF) / 255f;
        float g = ((tint >> 8) & 0xFF) / 255f;
        float b = (tint & 0xFF) / 255f;

        VertexConsumer vc = buffers.getBuffer(RenderType.translucent());
        Matrix4f m = poseStack.last().pose();
        float u0 = sprite.getU0(), u1 = sprite.getU1();
        float v0 = sprite.getV0(), v1 = sprite.getV1();

        // 6 caras del cubo unidad, normales hacia afuera, UV de la sprite
        // still repetida en cada cara (aproximacion suficiente para una
        // celda decorativa - el chunk mesher real varia la UV por altura de
        // columna de fluido, irrelevante para un cubo lleno aislado).
        quadFace(vc, m, r, g, b, a, packedLight, u0, v0, u1, v1,
                0, 1, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0, 0, 1, 0);
        quadFace(vc, m, r, g, b, a, packedLight, u0, v0, u1, v1,
                0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1, 0, -1, 0);
        quadFace(vc, m, r, g, b, a, packedLight, u0, v0, u1, v1,
                0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0, 0, 0, 0, -1);
        quadFace(vc, m, r, g, b, a, packedLight, u0, v0, u1, v1,
                0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1, 0, 0, 1);
        quadFace(vc, m, r, g, b, a, packedLight, u0, v0, u1, v1,
                0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0, -1, 0, 0);
        quadFace(vc, m, r, g, b, a, packedLight, u0, v0, u1, v1,
                1, 0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1, 0, 0);
    }

    private static void quadFace(VertexConsumer vc, Matrix4f m, float r, float g, float b, float a, int packedLight,
                                  float u0, float v0, float u1, float v1,
                                  float x1, float y1, float z1, float x2, float y2, float z2,
                                  float x3, float y3, float z3, float x4, float y4, float z4,
                                  float nx, float ny, float nz) {
        vc.vertex(m, x1, y1, z1).color(r, g, b, a).uv(u0, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(nx, ny, nz).endVertex();
        vc.vertex(m, x2, y2, z2).color(r, g, b, a).uv(u1, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(nx, ny, nz).endVertex();
        vc.vertex(m, x3, y3, z3).color(r, g, b, a).uv(u1, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(nx, ny, nz).endVertex();
        vc.vertex(m, x4, y4, z4).color(r, g, b, a).uv(u0, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(nx, ny, nz).endVertex();
    }

    private static boolean renderBlockItemFallback(Minecraft mc, BlockState state, PoseStack poseStack,
                                                     MultiBufferSource buffers, int packedLight) {
        Item item = state.getBlock().asItem();
        if (item == null || item == Items.AIR) return false;
        try {
            ItemStack stack = new ItemStack(item);
            poseStack.pushPose();
            poseStack.translate(0.5, 0.5, 0.5);
            poseStack.scale(0.9f, 0.9f, 0.9f);
            mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, packedLight,
                    OverlayTexture.NO_OVERLAY, poseStack, buffers, mc.level, 0);
            poseStack.popPose();
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private static void drawBoundsWire(PoseStack poseStack, StructureVoxelGrid grid) {
        BlockPos[] bounds = grid.computeBounds();
        if (bounds == null) return;
        AABB box = new AABB(
                bounds[0].getX(), bounds[0].getY(), bounds[0].getZ(),
                bounds[1].getX() + 1, bounds[1].getY() + 1, bounds[1].getZ() + 1);
        drawLineBox(poseStack, box, 0.30f, 0.70f, 1.00f, 0.85f);
    }

    private static void drawToolPreviewBox(PoseStack poseStack, BlockPos a, BlockPos b) {
        int minX = Math.min(a.getX(), b.getX()), maxX = Math.max(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY()), maxY = Math.max(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ()), maxZ = Math.max(a.getZ(), b.getZ());
        AABB box = new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
        drawLineBox(poseStack, box, 1.00f, 0.75f, 0.20f, 0.9f);
    }

    private static void drawCellOutline(PoseStack poseStack, BlockPos pos, int argb) {
        AABB box = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
        float a = ((argb >>> 24) & 0xFF) / 255f;
        float r = ((argb >>> 16) & 0xFF) / 255f;
        float gg = ((argb >>> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        drawLineBox(poseStack, box, r, gg, b, a);
    }

    private static void drawLineBox(PoseStack poseStack, AABB box, float r, float g, float b, float a) {
        // Compat Oculus/Embeddium (A) - mismo motivo que en render(): esto se
        // llama para el wireframe de límites y el outline de celda, todavía
        // dentro del mismo paso de dibujo 3D del preview, así que también
        // tiene que usar el BufferSource propio y no el compartido.
        MultiBufferSource.BufferSource buffers = localBufferSource();
        VertexConsumer consumer = buffers.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(poseStack, consumer, box, r, g, b, a);
        buffers.endBatch(RenderType.lines());
    }

    /** Utility for the screen: project a world-space point to GUI pixel coords, for click-testing UI chrome vs. the 3D viewport. */
    public static Vector3f worldToScreen(Vector3f world, Matrix4f view, Matrix4f proj, int vpX, int vpY, int vpW, int vpH) {
        Vector3f clip = view.transformPosition(new Vector3f(world));
        proj.transformProject(clip);
        float sx = vpX + (clip.x * 0.5f + 0.5f) * vpW;
        float sy = vpY + (1f - (clip.y * 0.5f + 0.5f)) * vpH;
        return new Vector3f(sx, sy, clip.z);
    }
}
