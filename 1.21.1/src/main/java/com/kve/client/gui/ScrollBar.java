package com.kve.client.gui;

import net.minecraft.client.gui.GuiGraphics;

public class ScrollBar {

    private int x;
    private int y;
    private final int trackH;
    private final int barW = 6;

    private int totalRows;
    private int visibleRows;
    private int scroll = 0;

    private boolean dragging   = false;
    private int     dragOffset = 0;
    private boolean hovered = false;

    public ScrollBar(int x, int y, int trackH, int totalRows, int visibleRows) {
        this.x           = x;
        this.y           = y;
        this.trackH      = Math.max(1, trackH);
        this.totalRows   = totalRows;
        this.visibleRows = visibleRows;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void setTotals(int totalRows, int visibleRows) {
        this.totalRows   = totalRows;
        this.visibleRows = visibleRows;
        clamp();
    }

    public int  getScroll()  { return scroll; }
    public int  maxScroll()  { return Math.max(0, totalRows - visibleRows); }
    public void setScroll(int s) { scroll = s; clamp(); }
    public void scrollBy(int delta) { scroll += delta; clamp(); }

    private int thumbH() {
        if (totalRows <= 0 || totalRows <= visibleRows) return trackH;
        int h = (int) ((double) visibleRows / totalRows * trackH);
        return Math.max(8, Math.min(h, trackH));
    }

    private int thumbTopRelY() {
        int max = maxScroll();
        if (max <= 0) return 0;
        int travel = trackH - thumbH();
        return (int) Math.round((double) scroll / max * travel);
    }

    public void render(GuiGraphics g) {
        if (!explicitHover) {
            double mx = KveGuiStyle.mouseX(), my = KveGuiStyle.mouseY();
            hovered = mx >= x && mx < x + barW && my >= y && my < y + trackH;
        }
        explicitHover = false;

        if (maxScroll() <= 0) return;

        g.fill(x, y, x + barW, y + trackH, KveGuiStyle.SCROLL_TRACK);
        g.hLine(x, x + barW - 1, y,             KveGuiStyle.SCROLL_TRACK_BORDER);
        g.hLine(x, x + barW - 1, y + trackH - 1,KveGuiStyle.SCROLL_TRACK_BORDER);
        g.vLine(x,          y, y + trackH - 1, KveGuiStyle.SCROLL_TRACK_BORDER);
        g.vLine(x + barW - 1, y, y + trackH - 1, KveGuiStyle.SCROLL_TRACK_BORDER);

        int ty = y + thumbTopRelY();
        int th = thumbH();
        int col = dragging ? KveGuiStyle.SCROLL_THUMB_DRAG
                : hovered ? KveGuiStyle.SCROLL_THUMB_HI
                : KveGuiStyle.SCROLL_THUMB;

        g.fill(x + 1, ty, x + barW - 1, ty + th, col);

        g.hLine(x + 1, x + barW - 2, ty, KveGuiStyle.SCROLL_THUMB_HI);

        g.hLine(x + 1, x + barW - 2, ty + th - 1, KveGuiStyle.SCROLL_THUMB_LO);

        int arrow = KveGuiStyle.ACCENT_GOLD;
        if (scroll > 0 && ty - y >= 5) {
            g.hLine(x + 2, x + 3, y + 2, arrow);
            g.hLine(x + 1, x + 4, y + 3, arrow);
        }
        if (scroll < maxScroll() && (y + trackH) - (ty + th) >= 5) {
            g.hLine(x + 1, x + 4, y + trackH - 4, arrow);
            g.hLine(x + 2, x + 3, y + trackH - 3, arrow);
        }

        if (dragging && totalRows > visibleRows) {
            var font = net.minecraft.client.Minecraft.getInstance().font;
            String label = (scroll + 1) + "-" + Math.min(totalRows, scroll + visibleRows) + " / " + totalRows;
            int lw = font.width(label) + 6;
            int lx = x - lw - 3, ly = Math.max(0, ty + th / 2 - 6);
            g.pose().pushPose();
            g.pose().translate(0, 0, 400);
            g.fill(lx, ly, lx + lw, ly + 12, KveGuiStyle.BG_TITLEBAR);
            g.renderOutline(lx, ly, lw, 12, KveGuiStyle.ACCENT_GOLD);
            g.drawString(font, label, lx + 3, ly + 2, KveGuiStyle.TEXT_PRIMARY, false);
            g.pose().popPose();
        }
    }

    private boolean explicitHover = false;

    /**
     * Mismo render de siempre + feedback hover en el thumb (ver comentario
     * de {@code hovered} arriba). Overload nuevo, opt-in - los 39 call
     * sites existentes que siguen llamando {@link #render(GuiGraphics)}
     * (sin mx/my) no ven ningun cambio; una screen puede migrar a este
     * cuando le convenga cambiando una sola linea (render(g) -> render(g,
     * mx, my)), sin tocar el resto de su codigo.
     */
    public void render(GuiGraphics g, int mx, int my) {
        hovered = mx >= x && mx < x + barW && my >= y && my < y + trackH;
        explicitHover = true;
        render(g);
    }

    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0 || maxScroll() <= 0) return false;
        if (mx < x || mx >= x + barW || my < y || my >= y + trackH) return false;

        int thumbTop = thumbTopRelY();
        int thumbBot = thumbTop + thumbH();
        int relY     = (int) my - y;

        if (relY >= thumbTop && relY < thumbBot) {

            dragging   = true;
            dragOffset = relY - thumbTop;
        } else {

            int travel = trackH - thumbH();
            if (travel > 0) {
                double ratio = (double)(relY - thumbH() / 2) / travel;
                scroll = (int) Math.round(ratio * maxScroll());
                clamp();
            }
            dragging   = true;
            dragOffset = thumbH() / 2;
        }
        return true;
    }

    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (!dragging) return false;
        int max = maxScroll();
        if (max <= 0) return true;
        int travel = trackH - thumbH();
        if (travel <= 0) return true;
        double ratio = (double)((int) my - y - dragOffset) / travel;
        scroll = (int) Math.round(ratio * max);
        clamp();
        return true;
    }

    public boolean mouseReleased(double mx, double my, int btn) {
        dragging = false;
        return false;
    }

    public boolean mouseScrolled(double delta) {
        int speed = Math.max(1, Math.min(3, totalRows / 120));
        scroll -= (int) Math.signum(delta) * speed;
        clamp();
        return true;
    }

    public boolean keyPressed(int keyCode) {
        switch (keyCode) {
            case 268: scroll = 0;           clamp(); return true;
            case 269: scroll = maxScroll(); clamp(); return true;
            case 266: scroll -= visibleRows; clamp(); return true;
            case 267: scroll += visibleRows; clamp(); return true;
            default: return false;
        }
    }

    private void clamp() {
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
    }
}