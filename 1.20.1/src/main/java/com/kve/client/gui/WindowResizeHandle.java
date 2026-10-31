package com.kve.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class WindowResizeHandle {

    private static final int GRIP_SIZE = 9;

    private final String storeKeyW, storeKeyH;
    private final int minW, maxW, minH, maxH;

    private int width, height;
    private int gripX, gripY;

    private boolean dragging = false;
    private int dragStartMouseX, dragStartMouseY, dragStartWidth, dragStartHeight;

    public WindowResizeHandle(String storeKey, int minW, int maxW, int minH, int maxH, int defaultW, int defaultH) {
        this.storeKeyW = storeKey + ".w";
        this.storeKeyH = storeKey + ".h";
        this.minW = minW; this.maxW = maxW;
        this.minH = minH; this.maxH = maxH;
        this.width  = clampW(PanelLayoutStore.getOrDefault(storeKeyW, defaultW));
        this.height = clampH(PanelLayoutStore.getOrDefault(storeKeyH, defaultH));
    }

    public int width()  { return width; }
    public int height() { return height; }

    /**
     * Call once per init(), after guiLeft/guiTop are known - positions the
     * grip at the window's bottom-right corner. Must be called even while
     * a drag is in progress (a resize normally re-runs init(), see class
     * doc) so the hit-box tracks the live size.
     */
    public void reposition(int guiLeft, int guiTop, int guiW, int guiH) {
        this.gripX = guiLeft + guiW - GRIP_SIZE;
        this.gripY = guiTop + guiH - GRIP_SIZE;
    }

    private boolean isHovered(double mx, double my) {
        return mx >= gripX && mx < gripX + GRIP_SIZE && my >= gripY && my < gripY + GRIP_SIZE;
    }

    public void render(GuiGraphics g, int mx, int my) {
        boolean hov = dragging || isHovered(mx, my);
        int color = dragging ? KveGuiStyle.SCROLL_THUMB_DRAG : (hov ? KveGuiStyle.SCROLL_THUMB_HI : KveGuiStyle.BORDER_SUBTLE);
        for (int i = 0; i < 3; i++) {
            int o = i * 3;
            g.fill(gripX + GRIP_SIZE - 1 - o, gripY + GRIP_SIZE - 2 - o, gripX + GRIP_SIZE - o, gripY + GRIP_SIZE - 1 - o, color);
            g.fill(gripX + GRIP_SIZE - 2 - o, gripY + GRIP_SIZE - 1 - o, gripX + GRIP_SIZE - 1 - o, gripY + GRIP_SIZE - o, color);
        }
    }

    /** @return true if the click landed on the grip (caller should treat the event as consumed and not fall through to anything else). */
    public boolean mouseClicked(double mx, double my) {
        if (!isHovered(mx, my)) return false;
        dragging = true;
        dragStartMouseX = (int) mx;
        dragStartMouseY = (int) my;
        dragStartWidth = width;
        dragStartHeight = height;
        return true;
    }

    /**
     * @return true if a drag is in progress and the size changed - the
     * caller should re-run its init()/layout so every widget positioned
     * off the old size moves/resizes to match.
     */
    public boolean mouseDragged(double mx, double my) {
        if (!dragging) return false;
        int proposedW = clampW(dragStartWidth + (int) (mx - dragStartMouseX));
        int proposedH = clampH(dragStartHeight + (int) (my - dragStartMouseY));
        if (proposedW == width && proposedH == height) return false;
        width = proposedW;
        height = proposedH;
        return true;
    }

    /** Ends the drag (no-op if none was in progress) and persists the final size - one disk write per drag, not per frame. */
    public void mouseReleased() {
        if (!dragging) return;
        dragging = false;
        PanelLayoutStore.put(storeKeyW, width);
        PanelLayoutStore.put(storeKeyH, height);
    }

    public boolean isDragging() { return dragging; }

    private int clampW(int w) { return Math.max(minW, Math.min(maxW, w)); }
    private int clampH(int h) { return Math.max(minH, Math.min(maxH, h)); }
}
