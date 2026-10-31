package com.kve.client.gui;

import com.kve.common.Lang;
import com.kve.common.conflict.ConflictDetector;
import com.kve.client.gui.recipe.RecipeCardRenderer;
import com.kve.common.recipe.RecipeData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Lista lo que encontro
 * {@link ConflictDetector} - un renglon por conflicto y, debajo, una fila por
 * cada entrada involucrada. Click en una entrada la abre en su editor (lo
 * decide quien abre esta pantalla, via {@code onOpen}). Con
 * {@link #withRecipes} ademas muestra la receta a la derecha y deja marcar
 * varias para editarlas a la vez.
 */
@OnlyIn(Dist.CLIENT)
public class ConflictsScreen extends Screen {

    private static final int ROW_H = 12, CARD_W = 190, CHECK = 9;

    private final Screen parent;
    private final Supplier<List<ConflictDetector.Conflict>> source;
    /** Segunda pestania opcional (recetas: "Mismo resultado"); null = sin pestanias. */
    private Supplier<List<ConflictDetector.Conflict>> altSource;
    private boolean showAlt;
    private final Consumer<String> onOpen;
    private Function<String, RecipeData> recipeLookup;
    private Consumer<List<String>> onEditMany;

    private record Row(ConflictDetector.Conflict conflict, String key) {}

    private final List<Row> rows = new ArrayList<>();
    private final Set<String> selected = new LinkedHashSet<>();
    private String previewKey;
    private int L, T, W, H, scroll;
    private KvePlateButton editManyBtn;
    /** Calculados en init: el render no vuelve a recorrer conflictos ni recetas en cada fotograma. */
    private long warnCount, infoCount;
    private final java.util.Map<String, RecipeData> recipeCache = new java.util.HashMap<>();

    /**
     * @param source lista de conflictos (se vuelve a pedir al abrir, siempre al dia)
     * @param onOpen abre la entrada con esa clave en su editor; recibe la clave (recipeId / id de la regla)
     */
    public ConflictsScreen(Screen parent, Component title, Supplier<List<ConflictDetector.Conflict>> source, Consumer<String> onOpen) {
        super(title);
        this.parent = parent;
        this.source = source;
        this.onOpen = onOpen;
    }

    /** Conflictos de recetas: vista previa enmarcada y edicion de varias a la vez. */
    public ConflictsScreen withRecipes(Function<String, RecipeData> lookup, Consumer<List<String>> editMany) {
        this.recipeLookup = lookup;
        this.onEditMany = editMany;
        return this;
    }

    private boolean recipes() { return recipeLookup != null; }

    /**
     * Agrega pestanias arriba: la primera son los conflictos de {@code source} (mismos
     * ingredientes, el error real) y la segunda {@code outputs} (mismo resultado, informativo).
     */
    public ConflictsScreen withOutputTab(Supplier<List<ConflictDetector.Conflict>> outputs) {
        this.altSource = outputs;
        return this;
    }

    /** Abre en la pestania "Mismo resultado" (cuando no hay conflictos de ingredientes que ver). */
    public ConflictsScreen startOnOutputs() {
        this.showAlt = true;
        return this;
    }

    private int tabsH() { return altSource == null ? 0 : 18; }

    private Supplier<List<ConflictDetector.Conflict>> current() { return showAlt && altSource != null ? altSource : source; }

    private void showTab(boolean alt) {
        if (showAlt == alt) return;
        showAlt = alt;
        selected.clear();
        previewKey = null;
        scroll = 0;
        init();
    }

    @Override
    protected void init() {
        W = recipes() ? Math.min(width - 16, 640) : 380;
        H = recipes() ? Math.min(height - 16, 330) : 240;
        L = (width - W) / 2;
        T = (height - H) / 2;
        rows.clear();
        Set<String> alive = new LinkedHashSet<>();
        List<ConflictDetector.Conflict> list = current().get();
        for (ConflictDetector.Conflict c : list) {
            rows.add(new Row(c, null));
            for (String k : c.keys()) { rows.add(new Row(c, k)); alive.add(k); }
        }
        selected.retainAll(alive);
        warnCount = rows.stream().filter(r -> r.key() == null && r.conflict().severity() == ConflictDetector.Severity.WARN).count();
        infoCount = rows.stream().filter(r -> r.key() == null).count() - warnCount;
        recipeCache.clear();
        if (recipeLookup != null) for (String k : alive) recipeCache.put(k, recipeLookup.apply(k));
        if (previewKey == null && recipes()) {
            for (Row r : rows) if (r.key() != null) { previewKey = r.key(); break; }
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - visibleRows())));
        clearWidgets();
        if (altSource != null) {
            int inputs = showAlt ? source.get().size() : list.size();
            int outputs = showAlt ? list.size() : altSource.get().size();
            KveGuiStyle.ButtonRow tabs = new KveGuiStyle.ButtonRow(L + KveGuiStyle.PAD, T + KveGuiStyle.TITLEBAR_H + 2, L + W - KveGuiStyle.PAD);
            KvePlateButton a = tabs.add(0, Lang.get("gui.kve.conflict.tab.inputs", inputs), b -> showTab(false)).withSelected(!showAlt);
            a.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Lang.c("gui.kve.conflict.tab.inputs_tip")));
            addRenderableWidget(a);
            KvePlateButton o = tabs.add(0, Lang.get("gui.kve.conflict.tab.outputs", outputs), b -> showTab(true)).withSelected(showAlt);
            o.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Lang.c("gui.kve.conflict.tab.outputs_tip")));
            addRenderableWidget(o);
        }
        int by = T + H - KveGuiStyle.PAD - KveGuiStyle.BTN_H;
        String closeL = Lang.get("gui.kve.btn.close");
        int closeX = L + W - KveGuiStyle.PAD - KveGuiStyle.btnWidth(closeL);
        addRenderableWidget(KveGuiStyle.smartBtn(closeX, by, 0, closeL, b -> onClose()));
        editManyBtn = null;
        if (recipes()) {
            String lbl = Lang.get("gui.kve.conflict.edit_many", selected.size());
            int bx = closeX - 6 - KveGuiStyle.btnWidth(lbl);
            editManyBtn = KveGuiStyle.smartBtn(bx, by, 0, lbl, b -> editSelected()).withPrimary(true);
            editManyBtn.active = !selected.isEmpty();
            editManyBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Lang.c("gui.kve.conflict.edit_many_tooltip")));
            addRenderableWidget(editManyBtn);
        }
    }

    private void editSelected() {
        if (selected.isEmpty() || onEditMany == null) return;
        List<String> keys = new ArrayList<>(selected);
        minecraft.setScreen(parent);
        onEditMany.accept(keys);
    }

    private int listX() { return L + KveGuiStyle.PAD; }
    private int listW() { return W - KveGuiStyle.PAD * 2 - (recipes() ? CARD_W + 8 : 0); }
    private int listTop() { return T + KveGuiStyle.TITLEBAR_H + 18 + tabsH(); }
    private int visibleRows() { return (H - KveGuiStyle.TITLEBAR_H - 18 - tabsH() - KveGuiStyle.BTN_H - KveGuiStyle.PAD - 6) / ROW_H; }

    /** Texto del renglon de un conflicto. */
    public static String describe(ConflictDetector.Conflict c) {
        return switch (c.kind()) {
            case RECIPE_DUPLICATE -> Lang.get("gui.kve.conflict.recipe_duplicate", c.subject(), c.keys().size());
            case RECIPE_SAME_INPUT -> Lang.get("gui.kve.conflict.recipe_same_input", c.keys().size(), c.detail());
            case RECIPE_SAME_OUTPUT -> Lang.get("gui.kve.conflict.recipe_same_output", c.detail(), c.subject());
            case WORLDGEN_FEATURE_REMOVED_TWICE -> Lang.get("gui.kve.conflict.feature_twice", c.subject(), c.keys().size());
            case WORLDGEN_VEIN_DUPLICATE -> Lang.get("gui.kve.conflict.vein_duplicate", c.detail(), c.subject());
        };
    }

    private Row rowAt(double mx, double my) {
        int x = listX(), w = listW(), top = listTop();
        if (mx < x || mx >= x + w || my < top || my >= top + visibleRows() * ROW_H) return null;
        int i = (int) ((my - top) / ROW_H) + scroll;
        return i < rows.size() ? rows.get(i) : null;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        long warns = warnCount;
        KveGuiStyle.drawWindow(g, L, T, W, H, getTitle().getString());
        int x = listX(), w = listW();
        long infos = infoCount;
        String summary = rows.isEmpty() ? Lang.get(showAlt ? "gui.kve.conflict.none_outputs" : "gui.kve.conflict.none")
                : showAlt ? Lang.get("gui.kve.conflict.summary_outputs", infos) : Lang.get("gui.kve.conflict.summary", warns, infos);
        KveGuiStyle.drawTextSafe(g, summary,
                x, T + KveGuiStyle.TITLEBAR_H + 4 + tabsH(), w, rows.isEmpty() ? KveGuiStyle.TEXT_OK : KveGuiStyle.TEXT_LABEL);
        int top = listTop(), vis = visibleRows();
        g.fill(x - 2, top - 2, x + w, top + vis * ROW_H + 1, KveGuiStyle.BG_FIELD);
        Row hoveredRow = rowAt(mx, my);
        for (int i = 0; i < vis && i + scroll < rows.size(); i++) {
            Row r = rows.get(i + scroll);
            int ry = top + i * ROW_H;
            boolean warn = r.conflict().severity() == ConflictDetector.Severity.WARN;
            if (r.key() == null) {
                int tx = x;
                if (recipes()) {
                    drawCheck(g, x, ry + 1, selected.containsAll(r.conflict().keys()), r == hoveredRow);
                    tx += CHECK + 4;
                }
                KveGuiStyle.drawTextSafe(g, (warn ? "⚠ " : "ℹ ") + describe(r.conflict()), tx, ry + 2, w - (tx - x) - 4,
                        warn ? KveGuiStyle.TEXT_WARN : KveGuiStyle.ACCENT_GOLD);
            } else {
                boolean hov = r == hoveredRow;
                boolean previewing = recipes() && r.key().equals(previewKey);
                if (hov || previewing) g.fill(x - 2, ry, x + w, ry + ROW_H, previewing ? 0xFF1E3550 : KveGuiStyle.BORDER_FIELD);
                int tx = x + 12;
                if (recipes()) {
                    drawCheck(g, x + 10, ry + 1, selected.contains(r.key()), hov);
                    tx = x + 10 + CHECK + 4;
                }
                KveGuiStyle.drawTextSafe(g, "→ " + r.key(), tx, ry + 2, w - (tx - x) - 64, KveGuiStyle.TEXT_PRIMARY);
                boolean hovOpen = hov && mx >= x + w - 60;
                KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.conflict.open"), x + w - 60, ry + 2, 58, hovOpen ? KveGuiStyle.TEXT_OK : KveGuiStyle.TEXT_MUTED);
            }
        }
        if (rows.size() > vis) {
            KveGuiStyle.drawTextSafe(g, (scroll + 1) + "-" + Math.min(rows.size(), scroll + vis) + " / " + rows.size(),
                    x, T + H - KveGuiStyle.PAD - 10, 120, KveGuiStyle.TEXT_MUTED);
        }
        ItemStack cardHover = ItemStack.EMPTY;
        if (recipes()) {
            String key = hoveredRow != null && hoveredRow.key() != null ? hoveredRow.key() : previewKey;
            RecipeData d = key == null ? null : recipeCache.get(key);
            int cx = L + W - KveGuiStyle.PAD - CARD_W, cy = T + KveGuiStyle.TITLEBAR_H + 4 + tabsH();
            int ch = T + H - KveGuiStyle.PAD - KveGuiStyle.BTN_H - 6 - cy;
            cardHover = RecipeCardRenderer.draw(g, font, d, cx, cy, CARD_W, ch, mx, my);
        }
        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
        if (!cardHover.isEmpty()) g.renderTooltip(font, cardHover, mx, my);
    }

    private static void drawCheck(GuiGraphics g, int x, int y, boolean on, boolean hov) {
        g.fill(x, y, x + CHECK, y + CHECK, 0xFF0A1220);
        g.renderOutline(x, y, CHECK, CHECK, hov ? KveGuiStyle.ACCENT_GOLD : KveGuiStyle.BORDER_FIELD);
        if (on) g.fill(x + 2, y + 2, x + CHECK - 2, y + CHECK - 2, KveGuiStyle.TEXT_OK);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (super.mouseClicked(mx, my, btn)) return true;
        Row r = btn == 0 ? rowAt(mx, my) : null;
        if (r == null) return false;
        int x = listX(), w = listW();
        if (r.key() == null) {
            if (!recipes()) return true;
            // la fila del conflicto marca o desmarca todas sus recetas
            if (selected.containsAll(r.conflict().keys())) r.conflict().keys().forEach(selected::remove);
            else selected.addAll(r.conflict().keys());
            init();
            return true;
        }
        if (!recipes() || mx >= x + w - 60) {
            String key = r.key();
            minecraft.setScreen(parent);
            onOpen.accept(key);
            return true;
        }
        if (mx < x + 10 + CHECK + 3) {
            if (!selected.remove(r.key())) selected.add(r.key());
            init();
        } else {
            previewKey = r.key();
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        scroll = Math.max(0, Math.min(Math.max(0, rows.size() - visibleRows()), scroll - (int) Math.signum(delta) * 3));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
