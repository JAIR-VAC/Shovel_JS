package com.kve.client.gui.picker;

import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KvePlateButton;
import com.kve.client.gui.KveScaledEditBox;
import com.kve.client.gui.recipe.RecipeSubsetExportScreen;
import com.kve.client.gui.ScrollBar;
import com.kve.client.gui.VirtualListWidget;

import com.kve.common.Lang;
import com.kve.common.persist.RecipeSubsetIO;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class NamespacePickerScreen extends Screen {

    private static final int GUI_W = 220;
    private static final int GUI_H = 220;
    private static final int ROWS_VISIBLE = 12;
    private static final int ROW_H = 14;

    private final Screen parent;
    private final Consumer<String> callback;

    private static class Entry {
        final String namespace;
        final int count;
        Entry(String namespace, int count) { this.namespace = namespace; this.count = count; }
    }

    private final List<Entry> all = new ArrayList<>();
    private final List<Entry> filtered = new ArrayList<>();
    private static final VirtualListWidget.Descriptor<Entry> LIST_DESCRIPTOR = new VirtualListWidget.Descriptor<>() {
        @Override public String id(Entry e) { return e.namespace; }
        @Override public String displayName(Entry e) { return null; }
    };
    private final VirtualListWidget<Entry> listIndex = new VirtualListWidget<>(LIST_DESCRIPTOR);
    private EditBox searchBox;
    private String searchFilter = "";
    private ScrollBar scrollBar;

    private int L, T;

    public NamespacePickerScreen(Screen parent, Consumer<String> callback) {
        super(Lang.c("gui.kve.subset.by_namespace"));
        this.parent = parent;
        this.callback = callback;
    }

    @Override
    protected void init() {
        L = (width - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        clearWidgets();
        setFocused(null);

        buildList();

        searchBox = new KveScaledEditBox(font, L + 6, T + 18, GUI_W - 12, 14,
                Component.literal(Lang.get("gui.kve.search.item")));
        searchBox.setMaxLength(64);
        searchBox.setHint(Component.literal(Lang.get("gui.kve.search.list_hint")));
        searchBox.setResponder(s -> { searchFilter = s; rebuildFilter(); });
        addRenderableWidget(searchBox);
        setInitialFocus(searchBox);

        scrollBar = new ScrollBar(L + GUI_W - 10, T + 38, ROWS_VISIBLE * ROW_H, 0, ROWS_VISIBLE);
        rebuildFilter();

        addRenderableWidget(new KvePlateButton(L + GUI_W / 2 - 40, T + GUI_H - 16, 80, 12, Lang.c("gui.kve.btn.back"), b -> onClose()));
    }

    private void buildList() {
        if (!all.isEmpty()) { listIndex.setItems(all); return; }
        Map<String, List<String>> byNs = RecipeSubsetIO.groupByNamespace();
        for (Map.Entry<String, List<String>> e : byNs.entrySet()) {
            all.add(new Entry(e.getKey(), e.getValue().size()));
        }
        all.sort(Comparator.comparing((Entry e) -> !e.namespace.equals("minecraft"))
                .thenComparing(e -> e.namespace));
        listIndex.setItems(all);
    }

    private void rebuildFilter() {
        listIndex.filter(searchFilter, null);
        filtered.clear();
        filtered.addAll(listIndex.filtered());
        if (scrollBar != null) scrollBar.setTotals(filtered.size(), ROWS_VISIBLE);
    }

    private void pick(Entry e) {
        callback.accept(e.namespace);
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, Lang.get("gui.kve.subset.by_namespace") + " (" + filtered.size() + ")");

        if (filtered.isEmpty() && all.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.nbt.fluid.none_registered"), L + 6, T + 40, GUI_W - 12, KveGuiStyle.TEXT_MUTED);
        }

        int listY = T + 38;
        int scroll = scrollBar.getScroll();
        for (int i = 0; i < ROWS_VISIBLE; i++) {
            int idx = i + scroll;
            if (idx >= filtered.size()) break;
            Entry e = filtered.get(idx);
            int ry = listY + i * ROW_H;
            boolean hov = mx >= L + 4 && mx < L + GUI_W - 12 && my >= ry && my < ry + ROW_H - 1;
            g.fill(L + 4, ry, L + GUI_W - 12, ry + ROW_H - 1, hov ? KveGuiStyle.BORDER_FIELD : KveGuiStyle.BG_FIELD);

            int nsColor = e.namespace.equals("minecraft") ? 0xFF7DE0B0 : KveGuiStyle.TEXT_PRIMARY;
            String label = e.namespace + " (" + e.count + ")";
            KveGuiStyle.drawTextSafe(g, label, L + 8, ry + 3, (L + GUI_W - 12) - (L + 8), nsColor);
        }
        scrollBar.render(g);

        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (scrollBar.mouseClicked(mx, my, btn)) return true;
        int listY = T + 38;
        int scroll = scrollBar.getScroll();
        for (int i = 0; i < ROWS_VISIBLE; i++) {
            int idx = i + scroll;
            if (idx >= filtered.size()) break;
            int ry = listY + i * ROW_H;
            if (mx >= L + 4 && mx < L + GUI_W - 12 && my >= ry && my < ry + ROW_H - 1) {
                pick(filtered.get(idx));
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (scrollBar.mouseDragged(mx, my, btn, dx, dy)) return true;
        return super.mouseDragged(mx, my, btn, dx, dy);
    }
    @Override public boolean mouseReleased(double mx, double my, int btn) {
        scrollBar.mouseReleased(mx, my, btn);
        return super.mouseReleased(mx, my, btn);
    }
    @Override public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        scrollBar.mouseScrolled(delta); return true;
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
