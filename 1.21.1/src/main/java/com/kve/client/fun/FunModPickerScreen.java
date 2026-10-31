package com.kve.client.fun;

import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KveScaledEditBox;
import com.kve.client.gui.ModNames;
import com.kve.client.gui.ScrollBar;
import com.kve.common.Lang;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** Lista de mods con contenido: clic marca o desmarca un mod como excluido del aleatorizador. */
@OnlyIn(Dist.CLIENT)
public class FunModPickerScreen extends Screen {

    private static final int W = 300;
    private static final int ROW_H = 14;

    private final Screen parent;
    private final Set<String> excluded;
    private final List<String> all = new ArrayList<>();
    private final List<String> shown = new ArrayList<>();
    private KveScaledEditBox search;
    private ScrollBar scrollBar;
    private int cx, cy, h;

    public FunModPickerScreen(Screen parent, Set<String> excluded) {
        super(Component.literal(Lang.get("gui.kve.fun.mods.title")));
        this.parent = parent;
        this.excluded = excluded;
        Set<String> ns = new TreeSet<>();
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) ns.add(id.getNamespace());
        for (ResourceLocation id : BuiltInRegistries.ENTITY_TYPE.keySet()) ns.add(id.getNamespace());
        all.addAll(ns);
        all.sort(Comparator.comparing((String n) -> !n.equals("minecraft")).thenComparing(n -> ModNames.of(n).toLowerCase(Locale.ROOT)));
    }

    @Override
    protected void init() {
        h = Math.min(height - 20, 260);
        cx = (width - W) / 2;
        cy = (height - h) / 2;
        String q = search != null ? search.getValue() : "";
        search = new KveScaledEditBox(font, cx + KveGuiStyle.PAD, cy + KveGuiStyle.TITLEBAR_H + 4, W - KveGuiStyle.PAD * 2, 14,
                Component.literal(Lang.get("gui.kve.hub.search")));
        search.setHint(Component.literal(Lang.get("gui.kve.hub.search")));
        search.setValue(q);
        search.setResponder(v -> filter());
        addRenderableWidget(search);
        setInitialFocus(search);
        KveGuiStyle.ButtonRow row = new KveGuiStyle.ButtonRow(cx + KveGuiStyle.PAD, cy + h - KveGuiStyle.PAD - KveGuiStyle.BTN_H, cx + W - KveGuiStyle.PAD);
        addRenderableWidget(row.add(0, Lang.get("gui.kve.fun.mods.none"), b -> excluded.clear()));
        addRenderableWidget(row.add(0, Lang.get("gui.kve.btn.close"), b -> onClose()).withPrimary(true));
        int keep = scrollBar != null ? scrollBar.getScroll() : 0;
        scrollBar = new ScrollBar(cx + W - KveGuiStyle.PAD - 4, listTop(), visibleRows() * ROW_H, 0, visibleRows());
        filter();
        scrollBar.setScroll(keep);
    }

    private void filter() {
        shown.clear();
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        for (String n : all) {
            if (q.isEmpty() || n.contains(q) || ModNames.of(n).toLowerCase(Locale.ROOT).contains(q)) shown.add(n);
        }
        if (scrollBar != null) scrollBar.setTotals(shown.size(), visibleRows());
    }

    private int listTop() { return cy + KveGuiStyle.TITLEBAR_H + 24; }

    private int visibleRows() { return Math.max(1, (cy + h - 28 - listTop()) / ROW_H); }

    private int rowAt(double mx, double my) {
        int x = cx + KveGuiStyle.PAD;
        if (mx < x || mx >= cx + W - KveGuiStyle.PAD - 8 || my < listTop()) return -1;
        int vis = (int) ((my - listTop()) / ROW_H);
        if (vis >= visibleRows()) return -1;
        int i = vis + scrollBar.getScroll();
        return i < shown.size() ? i : -1;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, cx, cy, W, h, getTitle().getString());
        int x = cx + KveGuiStyle.PAD, w = W - KveGuiStyle.PAD * 2 - 8;
        int hover = rowAt(mx, my);
        for (int vis = 0; vis < visibleRows(); vis++) {
            int i = scrollBar.getScroll() + vis;
            if (i >= shown.size()) break;
            String ns = shown.get(i);
            int ry = listTop() + vis * ROW_H;
            boolean off = excluded.contains(ns);
            g.fill(x, ry, x + w, ry + ROW_H - 1, i == hover ? KveGuiStyle.BORDER_FIELD : KveGuiStyle.BG_FIELD);
            KveGuiStyle.drawTextSafe(g, off ? "✖" : "✔", x + 3, ry + 3, 10, off ? KveGuiStyle.TEXT_ERROR : KveGuiStyle.TEXT_OK);
            KveGuiStyle.drawTextSafe(g, ModNames.of(ns) + "  (" + ns + ")", x + 16, ry + 3, w - 90, off ? KveGuiStyle.TEXT_MUTED : KveGuiStyle.TEXT_PRIMARY);
            String state = Lang.get(off ? "gui.kve.fun.mods.excluded" : "gui.kve.fun.mods.included");
            int sw = Math.round(font.width(state) * KveGuiStyle.uiTextScale());
            KveGuiStyle.drawTextSafe(g, state, x + w - sw - 4, ry + 3, sw + 2, off ? KveGuiStyle.TEXT_ERROR : KveGuiStyle.TEXT_MUTED);
        }
        if (shown.size() > visibleRows()) scrollBar.render(g, mx, my);
        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (shown.size() > visibleRows() && scrollBar.mouseClicked(mx, my, btn)) return true;
        int i = rowAt(mx, my);
        if (i >= 0 && btn == 0) {
            String ns = shown.get(i);
            if (!excluded.remove(ns)) excluded.add(ns);
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (scrollBar.mouseDragged(mx, my, btn, dx, dy)) return true;
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        scrollBar.mouseReleased(mx, my, btn);
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        return scrollBar.mouseScrolled(delta) || super.mouseScrolled(mx, my, scrollX, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
