package com.kve.client.gui;

import org.lwjgl.glfw.GLFW;
import com.kve.common.Lang;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Popup for picking one of the loot table ids returned by
 * {@link com.kve.common.kubejs.LootTableExporter#listAllLootTableIds()} - the
 * "Table..." button from (loading a full reference
 * loot table as a template). Same {@link VirtualListWidget} consumer pattern
 * as {@link TagPresetPickerScreen} (ninth consumer overall): entries are
 * plain {@code String} ids, no separate display name, no TagMatcher - a
 * loot table id has no natural "#tag" search operator, so it falls back to
 * VirtualListWidget's own substring matching like Fluid/Biome/Tag preset did.
 */
@OnlyIn(Dist.CLIENT)
public class LootTablePickerScreen extends Screen {

    private static final int GUI_W = 220;
    private static final int GUI_H = 180;
    private static final int ROWS_VISIBLE = 9;
    private static final int ROW_H = 12;

    private final Screen parent;
    private final List<String> ids;
    private final Consumer<String> callback;
    private final List<String> filtered = new ArrayList<>();
    private static final VirtualListWidget.Descriptor<String> LIST_DESCRIPTOR = new VirtualListWidget.Descriptor<>() {
        @Override public String id(String s) { return s; }
        @Override public String displayName(String s) { return null; }
    };
    private final VirtualListWidget<String> listIndex = new VirtualListWidget<>(LIST_DESCRIPTOR);

    private int L, T;
    private EditBox searchBox;
    private ScrollBar scrollBar;
    private String filter = "";

    public LootTablePickerScreen(Screen parent, List<String> ids, Consumer<String> callback) {
        super(Lang.c("gui.kve.loot.template_picker_title"));
        this.parent = parent;
        this.ids = ids;
        this.callback = callback;
        listIndex.setItems(ids);
    }

    @Override
    protected void init() {
        L = (width - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        clearWidgets();
        setFocused(null);
        refresh();

        searchBox = new KveScaledEditBox(font, L + 6, T + 16, GUI_W - 12, 12, Lang.c("gui.kve.search.list_hint"));
        searchBox.setHint(Lang.c("gui.kve.search.list_hint"));
        searchBox.setValue(filter);
        searchBox.setResponder(s -> { filter = s; refresh(); });
        addRenderableWidget(searchBox);

        if (scrollBar == null) {
            scrollBar = new ScrollBar(L + GUI_W - 10, T + 32, ROWS_VISIBLE * ROW_H, filtered.size(), ROWS_VISIBLE);
        } else {
            scrollBar.setTotals(filtered.size(), ROWS_VISIBLE);
        }
        int scroll = scrollBar.getScroll();

        int visible = Math.min(ROWS_VISIBLE, Math.max(0, filtered.size() - scroll));
        for (int i = 0; i < visible; i++) {
            String id = filtered.get(i + scroll);
            addRenderableWidget(new KvePlateButton(L + 6, T + 32 + i * ROW_H, GUI_W - 20, 11,
                    Component.literal(KveGuiStyle.truncate(font, id, GUI_W - 26)), b -> {
                        callback.accept(id);
                        minecraft.setScreen(parent);
                    }));
        }

        addRenderableWidget(new KvePlateButton(L + 6, T + GUI_H - 16, GUI_W - 12, 12, Lang.c("gui.kve.btn.cancel"), b -> minecraft.setScreen(parent)));
    }

    private void refresh() {
        listIndex.filter(filter, null);
        filtered.clear();
        filtered.addAll(listIndex.filtered());
        if (scrollBar != null) scrollBar.setTotals(filtered.size(), ROWS_VISIBLE);
    }

    /** ESC returns to the caller instead of closing the whole GUI. */
    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, getTitle().getString());
        if (filtered.isEmpty()) KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.structure.picker.no_results"), L + 6, T + 34, GUI_W - 12, KveGuiStyle.TEXT_MUTED);
        if (scrollBar != null) scrollBar.render(g);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (scrollBar != null && scrollBar.mouseClicked(mx, my, btn)) return true;
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
        if (scrollBar != null) { scrollBar.mouseScrolled(delta); init(); return true; }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (getFocused() != null && getFocused().keyPressed(key, scan, mods)) return true;
        return true;
    }
    @Override public boolean isPauseScreen() { return false; }
}
