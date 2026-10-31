package com.kve.client.gui.recipe;

import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KvePlateButton;
import com.kve.client.gui.KveScaledEditBox;
import com.kve.client.gui.picker.NamespacePickerScreen;
import com.kve.client.gui.ScrollBar;
import com.kve.client.gui.SearchFilter;

import com.kve.common.Lang;
import com.kve.client.permission.ClientPermissionState;
import com.kve.common.persist.RecipeSubsetIO;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.*;

/**
 * "Export selection" - lets the player pick a SUBSET of the project's
 * recipes (e.g. all recipes belonging to one mod's namespace, or a
 * hand-picked list) and write them to their own small file under
 * kubejs/config/kve_subsets/, separate from the full-project
 * kve_export.json. Meant to be copy-pasted into another KVE project via
 * {@link RecipeSubsetImportScreen}, which merges instead of replacing.
 */
@OnlyIn(Dist.CLIENT)
public class RecipeSubsetExportScreen extends Screen {

    private static final int GUI_W = 320;
    private static final int GUI_H = 240;
    private static final int ROWS_VISIBLE = 9;
    private static final int ROW_H = 14;

    private final Screen parent;
    private final List<String> allIds = new ArrayList<>();
    private final List<String> filtered = new ArrayList<>();
    private final Set<String> selected = new LinkedHashSet<>();

    private int L, T;
    private EditBox searchBox;
    private EditBox fileNameBox;
    private String filter = "";
    private ScrollBar scrollBar;
    private String flashMsg = "";
    private long flashAt = 0;
    private int flashColor = 0xFF66FF99;

    public RecipeSubsetExportScreen(Screen parent) {
        super(Lang.c("gui.kve.subset.export.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        L = (width - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        clearWidgets();
        setFocused(null);

        allIds.clear();
        allIds.addAll(RecipeSubsetIO.listAllRecipeIds());
        refreshFilter();

        searchBox = new KveScaledEditBox(font, L + 6, T + 18, GUI_W - 12, 12, Component.literal(""));
        searchBox.setValue(filter);
        searchBox.setHint(Component.literal(Lang.get("gui.kve.search.recipe")));
        searchBox.setResponder(s -> { filter = s; refreshFilter(); init(); });
        addRenderableWidget(searchBox);

        if (scrollBar == null) scrollBar = new ScrollBar(L + GUI_W - 12, T + 34, ROWS_VISIBLE * ROW_H, filtered.size(), ROWS_VISIBLE);
        else scrollBar.setTotals(filtered.size(), ROWS_VISIBLE);
        int scroll = scrollBar.getScroll();

        int by1 = T + 34 + ROWS_VISIBLE * ROW_H + 4;
        addRenderableWidget(new KvePlateButton(L + 6, by1, 100, 12, Lang.c("gui.kve.subset.select_all_visible"),
                b -> { selected.addAll(filtered); init(); }));
        addRenderableWidget(new KvePlateButton(L + 110, by1, 100, 12, Lang.c("gui.kve.subset.select_none"),
                b -> { selected.clear(); init(); }));
        addRenderableWidget(new KvePlateButton(L + 214, by1, 100, 12, Lang.c("gui.kve.subset.by_namespace"),
                b -> minecraft.setScreen(new NamespacePickerScreen(this, ns -> {
                    selected.addAll(RecipeSubsetIO.groupByNamespace().getOrDefault(ns, List.of()));
                }))));

        int by2 = by1 + 16;
        fileNameBox = new KveScaledEditBox(font, L + 6, by2, GUI_W - 130, 12, Component.literal(""));
        fileNameBox.setValue(defaultFileName());
        fileNameBox.setMaxLength(80);
        addRenderableWidget(fileNameBox);

        addRenderableWidget(new KvePlateButton(L + GUI_W - 118, by2 - 1, 56, 14, Lang.c("gui.kve.btn.export"), b -> doExport()));
        addRenderableWidget(new KvePlateButton(L + GUI_W - 58, by2 - 1, 52, 14, Lang.c("gui.kve.btn.back"), b -> minecraft.setScreen(parent)));
    }

    private String defaultFileName() {
        return "shovel_js_subset_" + selected.size() + "recipes.json";
    }

    private void refreshFilter() {
        filtered.clear();
        for (String id : allIds) if (SearchFilter.matches(id, filter)) filtered.add(id);
        if (scrollBar != null) scrollBar.setTotals(filtered.size(), ROWS_VISIBLE);
    }

    private void doExport() {
        if (!ClientPermissionState.canEdit()) {
            flashColor = 0xFFFF6666;
            flashMsg = Lang.get("gui.kve.perm.denied");
            flashAt = System.currentTimeMillis();
            return;
        }
        if (selected.isEmpty()) {
            flashColor = 0xFFFF6666;
            flashMsg = Lang.get("gui.kve.subset.none_selected");
            flashAt = System.currentTimeMillis();
            return;
        }
        String name = fileNameBox.getValue().trim();
        if (name.isEmpty()) name = defaultFileName();
        if (!name.endsWith(".json")) name = name + ".json";
        Path target;
        try {
            target = com.kve.common.security.KvePathGuard.resolveContained(RecipeSubsetIO.DEFAULT_DIR, name);
        } catch (IllegalArgumentException ex) {
            flashColor = 0xFFFF6666;
            flashMsg = Lang.get("gui.kve.files.name_invalid");
            flashAt = System.currentTimeMillis();
            return;
        }
        int count = RecipeSubsetIO.exportSubset(selected, target, "Shovel JS export");
        flashColor = count >= 0 ? 0xFF66FF99 : 0xFFFF6666;
        flashMsg = count >= 0
                ? Lang.get("gui.kve.subset.export.done", count, target.toAbsolutePath().toString())
                : Lang.get("gui.kve.subset.export.fail");
        flashAt = System.currentTimeMillis();
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, Lang.get("gui.kve.subset.export.title"));
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.subset.selected_count", selected.size(), allIds.size()),
                L + 6, T + GUI_H - 40, GUI_W - 12, KveGuiStyle.th(0xFF8899BB));
        if (filtered.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.recipe.list_empty"), L + 6, T + 50, GUI_W - 12, KveGuiStyle.th(0xFF667788));
        }
        if (scrollBar != null) scrollBar.render(g);
        int scroll = scrollBar != null ? scrollBar.getScroll() : 0;
        int hovIdx = rowAt(mx, my);
        for (int i = 0; i < ROWS_VISIBLE && i + scroll < filtered.size(); i++) {
            String id = filtered.get(i + scroll);
            int ry = T + 34 + i * ROW_H;
            boolean sel = selected.contains(id), hov = hovIdx == i + scroll;
            g.fill(L + 6, ry, L + GUI_W - 16, ry + ROW_H - 2, hov ? KveGuiStyle.BORDER_FIELD : KveGuiStyle.BG_FIELD);
            KveGuiStyle.drawCheck(g, L + 8, ry + 2, sel, hov);
            KveGuiStyle.drawTextSafe(g, id, L + 21, ry + 3, GUI_W - 41, sel ? KveGuiStyle.TEXT_OK : KveGuiStyle.TEXT_PRIMARY);
        }
        KveGuiStyle.renderWidgets(this, g, mx, my, pt);

        if (!flashMsg.isEmpty() && System.currentTimeMillis() - flashAt < 4000) {
            KveGuiStyle.drawCenteredTextSafe(g, KveGuiStyle.truncate(font, flashMsg, GUI_W - 12), L + GUI_W / 2, T + GUI_H - 10, flashColor);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (scrollBar != null && scrollBar.mouseClicked(mx, my, btn)) return true;
        int idx = rowAt(mx, my);
        if (btn == 0 && idx >= 0) {
            String id = filtered.get(idx);
            if (!selected.remove(id)) selected.add(id);
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    /** Indice en {@code filtered} de la fila bajo el mouse, o -1. */
    private int rowAt(double mx, double my) {
        if (mx < L + 6 || mx >= L + GUI_W - 16 || my < T + 34) return -1;
        int i = (int) ((my - (T + 34)) / ROW_H);
        int scroll = scrollBar != null ? scrollBar.getScroll() : 0;
        return i < ROWS_VISIBLE && i + scroll < filtered.size() ? i + scroll : -1;
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
    public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        if (scrollBar != null) { scrollBar.mouseScrolled(delta); init(); return true; }
        return super.mouseScrolled(mx, my, scrollX, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (getFocused() != null && getFocused().keyPressed(key, scan, mods)) return true;
        return true;
    }

    @Override public boolean isPauseScreen() { return false; }
}
