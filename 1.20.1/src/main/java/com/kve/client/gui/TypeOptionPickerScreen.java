package com.kve.client.gui;

import com.kve.common.Lang;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public class TypeOptionPickerScreen extends Screen {

    /** One selectable row: a human label, an optional plain-language sub-text explanation, and what to do when picked. */
    public static final class Option {
        final String label;
        final String subtext;
        final Runnable onPick;

        public Option(String label, String subtext, Runnable onPick) {
            this.label = label;
            this.subtext = subtext == null ? "" : subtext;
            this.onPick = onPick;
        }
    }

    private static final int GUI_W = 300;
    private static final int ROW_H = 26;
    private static final int TOP_PAD = 20;
    private static final int BOTTOM_PAD = 20;
    private static final int MIN_GUI_H = 70;

    private final Screen parent;
    private final String titleText;
    private final List<Option> options;
    private final int selectedIndex;

    private int L, T, guiH;
    private ScrollBar scrollBar;
    private int rowsVisible;

    /**
     * @param parent        screen to return to on confirm/cancel/ESC
     * @param titleText     window title, already resolved (caller does Lang.get(...))
     * @param options       rows to show, top to bottom, in the order given
     * @param selectedIndex index to highlight as "currently chosen" (-1 for none)
     */
    public TypeOptionPickerScreen(Screen parent, String titleText, List<Option> options, int selectedIndex) {
        super(Component.literal(titleText));
        this.parent = parent;
        this.titleText = titleText;
        this.options = options;
        this.selectedIndex = selectedIndex;
    }

    @Override
    protected void init() {
        int maxWindowH = Math.max(MIN_GUI_H, height - 20);
        int maxRowsThatFit = Math.max(1, (maxWindowH - TOP_PAD - BOTTOM_PAD) / ROW_H);
        rowsVisible = Math.min(Math.max(1, options.size()), maxRowsThatFit);

        int contentH = TOP_PAD + rowsVisible * ROW_H + BOTTOM_PAD;
        guiH = Math.max(MIN_GUI_H, Math.min(height - 20, contentH));
        L = (width - GUI_W) / 2;
        T = (height - guiH) / 2;
        clearWidgets();
        setFocused(null);

        if (options.size() > rowsVisible) {
            int barX = L + GUI_W - 10;
            if (scrollBar == null) {
                scrollBar = new ScrollBar(barX, T + TOP_PAD, rowsVisible * ROW_H, options.size(), rowsVisible);
            } else {
                scrollBar.setPosition(barX, T + TOP_PAD);
                scrollBar.setTotals(options.size(), rowsVisible);
            }
        } else {
            scrollBar = null;
        }

        addRenderableWidget(new KvePlateButton(L + GUI_W / 2 - 40, T + guiH - 16, 80, 14, Component.literal(Lang.get("gui.kve.btn.cancel")), b -> onClose()));
    }

    private int rowY(int i) { return T + TOP_PAD + i * ROW_H; }

    private int scroll() { return scrollBar != null ? scrollBar.getScroll() : 0; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, guiH, titleText);

        int scroll = scroll();
        int visible = Math.min(rowsVisible, Math.max(0, options.size() - scroll));
        for (int i = 0; i < visible; i++) {
            Option opt = options.get(i + scroll);
            int ry = rowY(i);
            boolean sel = (i + scroll) == selectedIndex;
            boolean hov = KveGuiStyle.isHovered(mx, my, L + 6, ry, GUI_W - 12, ROW_H - 2);
            g.fill(L + 6, ry, L + GUI_W - 6, ry + ROW_H - 2, (sel || hov) ? KveGuiStyle.BORDER_FIELD : KveGuiStyle.BG_FIELD);
            if (sel) g.vLine(L + 6, ry, ry + ROW_H - 3, KveGuiStyle.ACCENT_GOLD);
            KveGuiStyle.drawTextSafe(g, opt.label, L + 12, ry + 3, GUI_W - 24, sel ? KveGuiStyle.ACCENT_GOLD : 0xFFFFFFFF);
            if (!opt.subtext.isEmpty()) {
                KveGuiStyle.drawTextSafe(g, opt.subtext, L + 12, ry + 14, GUI_W - 24, KveGuiStyle.TEXT_LABEL);
            }
        }
        if (scrollBar != null) scrollBar.render(g);

        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (scrollBar != null && scrollBar.mouseClicked(mx, my, btn)) return true;

        int scroll = scroll();
        int visible = Math.min(rowsVisible, Math.max(0, options.size() - scroll));
        for (int i = 0; i < visible; i++) {
            int ry = rowY(i);
            if (mx >= L + 6 && mx < L + GUI_W - 6 && my >= ry && my < ry + ROW_H - 2) {
                options.get(i + scroll).onPick.run();
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (scrollBar != null && scrollBar.mouseDragged(mx, my, btn, dx, dy)) return true;
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (scrollBar != null) scrollBar.mouseReleased(mx, my, btn);
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (scrollBar != null) { scrollBar.mouseScrolled(delta); return true; }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override public boolean isPauseScreen() { return false; }
}