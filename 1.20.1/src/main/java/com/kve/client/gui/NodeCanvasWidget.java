package com.kve.client.gui;

import net.minecraft.client.gui.GuiGraphics;

import java.util.function.BiPredicate;

public final class NodeCanvasWidget {

    private final float baseCellSize;
    private final float baseStep;
    private final float minZoom;
    private final float maxZoom;

    private double panX = 0, panY = 0;
    private float zoom = 1.0F;
    private int fittedCols = -1, fittedRows = -1, fittedBoxSize = -1;
    private int fittedContentW = -1, fittedContentH = -1, fittedBoxW = -1, fittedBoxH = -1;
    private boolean dragging = false;
    private double dragLastMx, dragLastMy;
    private long lastClickAt = 0;

    public NodeCanvasWidget() {
        this(18F, 20F, 0.4F, 2.0F);
    }

    public NodeCanvasWidget(float baseCellSize, float baseStep, float minZoom, float maxZoom) {
        this.baseCellSize = baseCellSize;
        this.baseStep = baseStep;
        this.minZoom = minZoom;
        this.maxZoom = maxZoom;
    }

    public void fitIfNeeded(int cols, int rows, int boxSize) {
        if (fittedCols == cols && fittedRows == rows && fittedBoxSize == boxSize) return;
        int maxDim = Math.max(cols, rows);
        zoom = maxDim > 5 ? 0.75F : maxDim > 3 ? 0.9F : 1.0F;
        float step = step();
        float contentW = cols * step, contentH = rows * step;
        panX = (boxSize - contentW) / 2.0;
        panY = (boxSize - contentH) / 2.0;
        fittedCols = cols;
        fittedRows = rows;
        fittedBoxSize = boxSize;
    }

    /** Invalidates the current fit (both {@link #fitIfNeeded()} and {@link #fitFreeform()} modes) so the next call re-centers unconditionally - used for the double-click-to-reset gesture. */
    public void forceRefit() {
        fittedCols = -1;
        fittedRows = -1;
        fittedBoxSize = -1;
        fittedContentW = -1;
        fittedContentH = -1;
        fittedBoxW = -1;
        fittedBoxH = -1;
    }

    public void fitFreeform(int contentW, int contentH, int boxW, int boxH) {
        if (fittedContentW == contentW && fittedContentH == contentH
                && fittedBoxW == boxW && fittedBoxH == boxH) return;
        float fitW = contentW > 0 ? (float) boxW / contentW : 1F;
        float fitH = contentH > 0 ? (float) boxH / contentH : 1F;
        zoom = Math.max(minZoom, Math.min(maxZoom, Math.min(1.0F, Math.min(fitW, fitH))));
        panX = (boxW - contentW * zoom) / 2.0;
        panY = (boxH - contentH * zoom) / 2.0;
        fittedContentW = contentW;
        fittedContentH = contentH;
        fittedBoxW = boxW;
        fittedBoxH = boxH;
    }

    /** Deja el punto {@code (rawX, rawY)} del contenido en el centro de la caja, con el zoom actual. */
    public void centerOn(int boxW, int boxH, double rawX, double rawY) {
        panX = boxW / 2.0 - rawX * zoom;
        panY = boxH / 2.0 - rawY * zoom;
    }

    /** Raw-to-screen X for a {@link #fitFreeform()} canvas point (a SlotDef's raw x offset, an arrow's raw x, etc). */
    public int sx(int boxX, int rawX) { return (int) Math.round(boxX + panX + rawX * zoom); }
    /** Raw-to-screen Y for a {@link #fitFreeform()} canvas point. */
    public int sy(int boxY, int rawY) { return (int) Math.round(boxY + panY + rawY * zoom); }

    public float step() { return baseStep * zoom; }
    public int cellSize() { return Math.max(4, Math.round(baseCellSize * zoom)); }
    public int cellX(int boxX, int col) { return (int) Math.round(boxX + panX + col * step()); }
    public int cellY(int boxY, int row) { return (int) Math.round(boxY + panY + row * step()); }
    public float zoom() { return zoom; }
    public boolean isDragging() { return dragging; }

    /** Grid index (row*cols+col) under (mx,my), or null if the cursor isn't over any cell - "over the box but in the gap between cells" is null too, left for the caller to treat as pan/reset via {@link #handleEmptyClick()}. */
    public Integer indexAt(int boxX, int boxY, int cols, int rows, double mx, double my) {
        int sz = cellSize();
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int cx = cellX(boxX, col), cy = cellY(boxY, row);
                if (mx >= cx && mx < cx + sz && my >= cy && my < cy + sz) return row * cols + col;
            }
        }
        return null;
    }

    /**
     * Call when a click inside the box missed every cell (caller already
     * checked {@link #indexAt()} returned null but mx/my is still within the
     * box bounds). Left-click starts a pan drag; a quick left-click double
     * (under 350ms) instead resets pan/zoom via {@link #forceRefit()}.
     * Always returns true (the click was consumed either way) so callers
     * can {@code return true;} straight from this.
     */
    public boolean handleEmptyClick(int btn, double mx, double my) {
        long now = System.currentTimeMillis();
        if (btn == 0 && now - lastClickAt < 350) forceRefit();
        lastClickAt = now;
        dragging = btn == 0;
        dragLastMx = mx;
        dragLastMy = my;
        return true;
    }

    /** Call from the screen's mouseDragged while this canvas owns the drag; returns false (no-op) once {@link #isDragging()} is false, so the caller can fall through to its own drag handling in one line. */
    public boolean handleDrag(double mx, double my) {
        if (!dragging) return false;
        panX += mx - dragLastMx;
        panY += my - dragLastMy;
        dragLastMx = mx;
        dragLastMy = my;
        return true;
    }

    /** Call from the screen's mouseReleased unconditionally. */
    public void endDrag() { dragging = false; }

    /** Scroll-to-zoom centered on the cursor (the world point under the mouse stays put) - call only when mx/my is already known to be inside the box. Returns false if the zoom didn't actually change (already at min/max). */
    public boolean handleScrollZoom(int boxX, int boxY, double mx, double my, double delta) {
        float oldZoom = zoom;
        float newZoom = (float) Math.max(minZoom, Math.min(maxZoom, zoom + (delta > 0 ? 0.1 : -0.1)));
        if (Math.abs(newZoom - oldZoom) <= 0.001F) return false;
        double wx = (mx - boxX - panX) / oldZoom;
        double wy = (my - boxY - panY) / oldZoom;
        zoom = newZoom;
        panX = (mx - boxX) - wx * newZoom;
        panY = (my - boxY) - wy * newZoom;
        return true;
    }

    public void drawBackground(GuiGraphics g, int boxX, int boxY, int boxSize) {
        drawBackground(g, boxX, boxY, boxSize, boxSize);
    }

    public void drawBackground(GuiGraphics g, int boxX, int boxY, int boxW, int boxH) {
        net.minecraft.resources.ResourceLocation tex = backgroundTexture(KveGuiStyle.canvasBg(), KveGuiStyle.canvasDot());
        for (int y = 0; y < boxH; y += BG_TILE) {
            for (int x = 0; x < boxW; x += BG_TILE) {
                g.blit(tex, boxX + x, boxY + y, 0, 0, Math.min(BG_TILE, boxW - x), Math.min(BG_TILE, boxH - y), BG_TILE, BG_TILE);
            }
        }
    }

    private static final int BG_TILE = 256;
    private static net.minecraft.resources.ResourceLocation bgTexture;
    private static long bgTextureKey;

    private static net.minecraft.resources.ResourceLocation backgroundTexture(int bg, int dot) {
        long key = ((long) bg << 32) | (dot & 0xFFFFFFFFL);
        if (bgTexture != null && bgTextureKey == key) return bgTexture;
        com.mojang.blaze3d.platform.NativeImage img = new com.mojang.blaze3d.platform.NativeImage(BG_TILE, BG_TILE, false);
        int bgAbgr = abgr(bg), dotAbgr = abgr(dot);
        for (int y = 0; y < BG_TILE; y++) {
            for (int x = 0; x < BG_TILE; x++) {
                img.setPixelRGBA(x, y, (x & 7) == 4 && (y & 7) == 4 ? dotAbgr : bgAbgr);
            }
        }
        net.minecraft.resources.ResourceLocation id = new net.minecraft.resources.ResourceLocation("shovel_js", "dynamic/canvas_background");
        net.minecraft.client.Minecraft.getInstance().getTextureManager()
                .register(id, new net.minecraft.client.renderer.texture.DynamicTexture(img));
        bgTexture = id;
        bgTextureKey = key;
        return id;
    }

    private static int abgr(int argb) {
        return (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16);
    }

    /**
     * Draws a connecting bracket line between any two orthogonally-adjacent
     * "filled" cells
     * - {@code filled} is supplied by the caller (col,row) -> boolean so
     * this works for an ItemStack grid, a machine-slot grid, or anything
     * else with a col/row shape. Draw this BEFORE the caller's own cell
     * content loop so hover glow/item icons paint over the brackets, never
     * the other way around.
     */
    public void drawConnectingBrackets(GuiGraphics g, int boxX, int boxY, int cols, int rows, BiPredicate<Integer, Integer> filled) {
        int sz = cellSize();
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                if (!filled.test(col, row)) continue;
                int cx = cellX(boxX, col), cy = cellY(boxY, row);
                if (col + 1 < cols && filled.test(col + 1, row)) {
                    int nx = cellX(boxX, col + 1);
                    if (nx > cx + sz) g.hLine(cx + sz, nx - 1, cy + sz / 2, 0xFF3A4E66);
                }
                if (row + 1 < rows && filled.test(col, row + 1)) {
                    int ny = cellY(boxY, row + 1);
                    if (ny > cy + sz) g.vLine(cx + sz / 2, cy + sz, ny - 1, 0xFF3A4E66);
                }
            }
        }
    }

    public void drawZoomReadout(GuiGraphics g, int boxX, int boxY) {
        KveGuiStyle.drawTextSafe(g, "\u00a77" + Math.round(zoom * 100) + "%", boxX + 2, boxY + 2, 40, 0xFF6C7A94);
    }
}
