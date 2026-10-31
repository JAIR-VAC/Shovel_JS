package com.kve.client.gui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Horizontal min/max range widget - .
 * Went with a real slider (this class) instead of the EditBox pair
 * offered as an alternative, since it's the same amount of code and reads
 * better for "pick a vertical band" than two raw number fields do.
 * <p>
 * Same shape as {@link ScrollBar}/{@link ResizeHandle}: a standalone class
 * with its own {@code render}/{@code mouseClicked()}/{@code mouseDragged()}/
 * {@code mouseReleased()}, not a vanilla {@code AbstractWidget} - this codebase
 * doesn't route custom drag widgets through vanilla's widget system anywhere
 * else, so this doesn't either (see {@code ScrollBar}'s own class doc
 * history for why: vanilla's widget click/drag lifecycle doesn't map well
 * onto "two independent draggable handles on one track").
 * <p>
 * Two callers today: {@code OreVeinEditScreen} (minY/maxY, bound to
 * Minecraft's build limits) and, later, any {@code TerrainPreset} override
 * that exposes a height range.
 */
public class RangeSlider {

    private int x, y;
    private final int w;
    private static final int TRACK_H = 6;
    private static final int HANDLE_W = 5;
    private static final int HANDLE_H = 12;

    private final int boundMin, boundMax;
    private int valueMin, valueMax;

    private boolean draggingMin = false;
    private boolean draggingMax = false;

    public RangeSlider(int x, int y, int w, int boundMin, int boundMax, int valueMin, int valueMax) {
        this.x = x;
        this.y = y;
        this.w = Math.max(20, w);
        this.boundMin = boundMin;
        this.boundMax = Math.max(boundMin + 1, boundMax);
        setValues(valueMin, valueMax);
    }

    public void setPosition(int x, int y) { this.x = x; this.y = y; }

    public int getMin() { return valueMin; }
    public int getMax() { return valueMax; }

    /** Clamps to [boundMin, boundMax] and keeps min &lt;= max, same cross-validation the EditBox-pair alternative would have needed. */
    public void setValues(int min, int max) {
        valueMin = clampBound(min);
        valueMax = clampBound(max);
        if (valueMin > valueMax) { int t = valueMin; valueMin = valueMax; valueMax = t; }
    }

    private int clampBound(int v) { return Math.max(boundMin, Math.min(boundMax, v)); }

    private int xForValue(int value) {
        double ratio = (double) (value - boundMin) / (boundMax - boundMin);
        return x + (int) Math.round(ratio * (w - HANDLE_W));
    }

    private int valueForX(int px) {
        double ratio = (double) (px - x) / Math.max(1, w - HANDLE_W);
        ratio = Math.max(0, Math.min(1, ratio));
        return boundMin + (int) Math.round(ratio * (boundMax - boundMin));
    }

    public void render(GuiGraphics g) {
        int trackY = y + (HANDLE_H - TRACK_H) / 2;
        g.fill(x, trackY, x + w, trackY + TRACK_H, KveGuiStyle.BG_FIELD);
        g.hLine(x, x + w - 1, trackY, KveGuiStyle.BORDER_FIELD);
        g.hLine(x, x + w - 1, trackY + TRACK_H - 1, KveGuiStyle.BORDER_FIELD);

        int minX = xForValue(valueMin);
        int maxX = xForValue(valueMax);
        g.fill(minX + HANDLE_W / 2, trackY + 1, maxX + HANDLE_W / 2, trackY + TRACK_H - 1, KveGuiStyle.ACCENT_GOLD);

        drawHandle(g, minX, draggingMin);
        drawHandle(g, maxX, draggingMax);
    }

    private void drawHandle(GuiGraphics g, int hx, boolean dragging) {
        int col = dragging ? KveGuiStyle.ACCENT_GOLD : KveGuiStyle.TEXT_LABEL;
        g.fill(hx, y, hx + HANDLE_W, y + HANDLE_H, col);
        g.hLine(hx, hx + HANDLE_W - 1, y, KveGuiStyle.BORDER_FIELD);
        g.hLine(hx, hx + HANDLE_W - 1, y + HANDLE_H - 1, KveGuiStyle.BORDER_FIELD);
    }

    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0) return false;
        if (my < y || my >= y + HANDLE_H) return false;
        int minX = xForValue(valueMin);
        int maxX = xForValue(valueMax);
        boolean overMin = mx >= minX && mx < minX + HANDLE_W;
        boolean overMax = mx >= maxX && mx < maxX + HANDLE_W;
        if (overMin && overMax) { draggingMax = true; return true; }
        if (overMin) { draggingMin = true; return true; }
        if (overMax) { draggingMax = true; return true; }
        return false;
    }

    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (!draggingMin && !draggingMax) return false;
        int v = valueForX((int) mx);
        if (draggingMin) valueMin = Math.min(v, valueMax);
        if (draggingMax) valueMax = Math.max(v, valueMin);
        return true;
    }

    public boolean mouseReleased(double mx, double my, int btn) {
        boolean was = draggingMin || draggingMax;
        draggingMin = false;
        draggingMax = false;
        return was;
    }
}
