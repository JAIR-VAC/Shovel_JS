package com.kve.client.gui;

import com.kve.common.Lang;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class EventTemplatePickerScreen extends Screen {

    private static final int GUI_W = 200;
    private static final int GUI_H = 170;
    private static final int ROW_H = 26;
    private static final int TOP_PAD = 20;
    private static final int BOTTOM_PAD = 20;
    private static final int ROWS_VISIBLE = 5;

    private final Screen parent;
    private final List<EventTemplates.Template> templates;
    private final Consumer<EventTemplates.Template> callback;

    private int L, T;
    private ScrollBar scrollBar;

    public EventTemplatePickerScreen(Screen parent, Consumer<EventTemplates.Template> callback) {
        super(Component.literal(Lang.get("gui.kve.event.template.list_title")));
        this.parent = parent;
        this.templates = EventTemplates.all();
        this.callback = callback;
    }

    @Override
    protected void init() {
        L = (width - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        clearWidgets();
        setFocused(null);

        if (scrollBar == null) {
            scrollBar = new ScrollBar(L + GUI_W - 10, T + TOP_PAD, ROWS_VISIBLE * ROW_H, templates.size(), ROWS_VISIBLE);
        } else {
            scrollBar.setTotals(templates.size(), ROWS_VISIBLE);
        }

        addRenderableWidget(new KvePlateButton(L + GUI_W / 2 - 40, T + GUI_H - 16, 80, 14, Component.literal(Lang.get("gui.kve.btn.cancel")), b -> onClose()));
    }

    private int rowY(int i) { return T + TOP_PAD + i * ROW_H; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, Lang.get("gui.kve.event.template.list_title"));

        int scroll = scrollBar != null ? scrollBar.getScroll() : 0;
        int visible = Math.min(ROWS_VISIBLE, Math.max(0, templates.size() - scroll));
        for (int i = 0; i < visible; i++) {
            EventTemplates.Template tpl = templates.get(i + scroll);
            int ry = rowY(i);
            boolean hov = KveGuiStyle.isHovered(mx, my, L + 6, ry, GUI_W - 12, ROW_H - 2);
            g.fill(L + 6, ry, L + GUI_W - 6, ry + ROW_H - 2, hov ? KveGuiStyle.BORDER_FIELD : KveGuiStyle.BG_FIELD);
            KveGuiStyle.drawTextSafe(g, Lang.get(tpl.nameKey), L + 12, ry + 3, GUI_W - 24, 0xFFFFFFFF);
            KveGuiStyle.drawTextSafe(g, Lang.get(tpl.descKey), L + 12, ry + 14, GUI_W - 24, KveGuiStyle.TEXT_LABEL);
        }
        if (scrollBar != null) scrollBar.render(g);

        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (scrollBar != null && scrollBar.mouseClicked(mx, my, btn)) return true;

        int scroll = scrollBar != null ? scrollBar.getScroll() : 0;
        int visible = Math.min(ROWS_VISIBLE, Math.max(0, templates.size() - scroll));
        for (int i = 0; i < visible; i++) {
            int ry = rowY(i);
            if (mx >= L + 6 && mx < L + GUI_W - 6 && my >= ry && my < ry + ROW_H - 2) {
                callback.accept(templates.get(i + scroll));
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
