package com.kve.client.gui;

import com.kve.client.gui.tag.TagEditorScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
public class ResizeHandle {

    private static final int GRAB_W = 5;

    private final String storeKey;
    private final int minW, maxW;

    private int width;
    private int screenX, topY, bottomY;

    private boolean dragging = false;
    private int dragStartMouseX, dragStartWidth;

    public ResizeHandle(String storeKey, int minW, int maxW, int defaultW) {
        this.storeKey = storeKey;
        this.minW = minW;
        this.maxW = maxW;
        this.width = clamp(PanelLayoutStore.getOrDefault(storeKey, defaultW));
    }

    public int width() { return width; }

    /**
     * Call once per init(), after L/T are known - positions the grip at
     * {@code x = left + width()} spanning {@code [top, bottom)}. Must be
     * called even while a drag is in progress (a resize normally re-runs
     * init(), see class doc) so the hit-box tracks the live width.
     */
    public void reposition(int left, int top, int bottom) {
        this.screenX = left + width;
        this.topY = top;
        this.bottomY = bottom;
    }

    private boolean isHovered(double mx, double my) {
        return mx >= screenX - GRAB_W / 2.0 && mx < screenX + GRAB_W / 2.0 + 1 && my >= topY && my < bottomY;
    }

    public void render(GuiGraphics g, int mx, int my) {
        boolean hov = dragging || isHovered(mx, my);
        int color = dragging ? KveGuiStyle.SCROLL_THUMB_DRAG : (hov ? KveGuiStyle.SCROLL_THUMB_HI : KveGuiStyle.BORDER_SUBTLE);
        g.fill(screenX, topY, screenX + 1, bottomY, color);
        if (hov) {
            int cy = (topY + bottomY) / 2;
            g.fill(screenX - 1, cy - 4, screenX + 2, cy - 3, color);
            g.fill(screenX - 1, cy,     screenX + 2, cy + 1, color);
            g.fill(screenX - 1, cy + 4, screenX + 2, cy + 5, color);
        }
    }

    /** @return true if the click landed on the grip (caller should treat the event as consumed and not fall through to anything else). */
    public boolean mouseClicked(double mx, double my) {
        if (!isHovered(mx, my)) return false;
        dragging = true;
        dragStartMouseX = (int) mx;
        dragStartWidth = width;
        return true;
    }

    /**
     * @return true if a drag is in progress and the width changed - the
     * caller should re-run its init()/layout so every widget positioned
     * off the old width moves to match (same "resize re-lays-out"
     * pattern the screen already uses for window resizes).
     */
    public boolean mouseDragged(double mx) {
        if (!dragging) return false;
        if (!isLeftButtonDown()) { mouseReleased(); return false; }
        int proposed = clamp(dragStartWidth + (int) (mx - dragStartMouseX));
        if (proposed == width) return false;
        width = proposed;
        return true;
    }

    /** Ends the drag (no-op if none was in progress) and persists the final width - one disk write per drag, not per frame. */
    public void mouseReleased() {
        if (!dragging) return;
        dragging = false;
        PanelLayoutStore.put(storeKey, width);
    }

    public boolean isDragging() {
        if (dragging && !isLeftButtonDown()) mouseReleased();
        return dragging;
    }

    private static boolean isLeftButtonDown() {
        return GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().getWindow(),
                GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    private int clamp(int w) { return Math.max(minW, Math.min(maxW, w)); }
}
