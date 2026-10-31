package com.kve.client.gui;

import com.kve.common.Lang;
import com.kve.client.gui.studio.KveStudioShell;
import com.kve.common.kubejs.EventExporter;
import com.kve.common.kubejs.KubeJSExporter;
import com.kve.common.kubejs.KubeJSItemAttrExporter;
import com.kve.common.kubejs.KubeJSMobExporter;
import com.kve.common.kubejs.LootTableExporter;
import com.kve.common.kubejs.TagExporter;
import com.kve.common.recipe.RecipeData;
import com.kve.common.search.IdUsageIndex;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@OnlyIn(Dist.CLIENT)
public class IdUsageScreen extends Screen {

    private static final int GUI_W = 320;
    private static final int ROWS_VISIBLE = 9;
    private static final int ROW_H = 16;
    private static final int GUI_H = 40 + ROWS_VISIBLE * ROW_H + 26;

    private final Screen returnScreen;
    private final String initialQuery;

    private int L, T;
    private EditBox searchBox;
    private ScrollBar scrollBar;
    private final List<IdUsageIndex.Hit> results = new ArrayList<>();

    private String flashMsg = "";
    private long flashAt = 0;
    private boolean wasIndexBuilding = false;

    public IdUsageScreen(Screen returnScreen) {
        this(returnScreen, "");
    }

    /** @param initialQuery pre-fills the search box - e.g. an editor's own "?" button passing the id currently on screen. */
    public IdUsageScreen(Screen returnScreen, String initialQuery) {
        super(Lang.c("gui.kve.idusage.title"));
        this.returnScreen = returnScreen;
        this.initialQuery = initialQuery == null ? "" : initialQuery;
    }

    @Override
    protected void init() {
        L = (width - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        clearWidgets();
        setFocused(null);

        String query = searchBox != null ? searchBox.getValue() : initialQuery;
        searchBox = new KveScaledEditBox(font, L + 6, T + 18, GUI_W - 12, 12, Lang.c("gui.kve.idusage.search_hint"));
        searchBox.setMaxLength(128);
        searchBox.setValue(query);
        searchBox.setResponder(s -> runSearch());
        addRenderableWidget(searchBox);
        setFocused(searchBox);

        scrollBar = new ScrollBar(L + GUI_W - 10, T + 40, ROWS_VISIBLE * ROW_H, 0, ROWS_VISIBLE);
        runSearch();

        addRenderableWidget(new KvePlateButton(L + 6, T + GUI_H - 20, 84, 16, Lang.c("gui.kve.idusage.reindex"), b -> {
                    com.kve.common.structure.StructureUsageIndex.forceRebuild();
                    flashMsg = Lang.get("gui.kve.idusage.reindex_started");
                    flashAt = System.currentTimeMillis();
                }));

        addRenderableWidget(new KvePlateButton(L + GUI_W - 66, T + GUI_H - 20, 60, 16, Lang.c("gui.kve.btn.close"), b -> goBack()));
    }

    private void runSearch() {
        results.clear();
        results.addAll(IdUsageIndex.search(searchBox != null ? searchBox.getValue() : initialQuery));
        if (scrollBar != null) scrollBar.setTotals(results.size(), ROWS_VISIBLE);
    }

    private void goBack() { minecraft.setScreen(returnScreen != null ? returnScreen : new KveStudioShell()); }

    @Override
    public void onClose() { goBack(); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, Lang.get("gui.kve.idusage.title"));
        super.render(g, mx, my, pt);

        boolean building = com.kve.common.structure.StructureUsageIndex.isBuilding();
        if (wasIndexBuilding && !building) runSearch();
        wasIndexBuilding = building;

        int listY = T + 40;
        String query = searchBox.getValue().trim();
        if (query.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.idusage.hint"), L + 7, listY + 4, GUI_W - 22, KveGuiStyle.TEXT_MUTED);
        } else if (results.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.idusage.none"), L + 7, listY + 4, GUI_W - 22, KveGuiStyle.TEXT_MUTED);
        } else {
            int scroll = scrollBar.getScroll();
            for (int i = 0; i < Math.min(ROWS_VISIBLE, results.size() - scroll); i++) {
                IdUsageIndex.Hit hit = results.get(i + scroll);
                int ry = listY + i * ROW_H;
                boolean hov = KveGuiStyle.isHovered(mx, my, L + 4, ry, GUI_W - 22, ROW_H - 2);
                g.fill(L + 4, ry, L + GUI_W - 20, ry + ROW_H - 2, hov ? KveGuiStyle.BORDER_FIELD : KveGuiStyle.BG_FIELD);
                String domainLabel = Lang.get("gui.kve.idusage.domain." + hit.domain().name().toLowerCase(Locale.ROOT));
                KveGuiStyle.drawTextSafe(g, "[" + domainLabel + "] " + hit.entryId(), L + 7, ry + 2, GUI_W - 32, KveGuiStyle.TEXT_PRIMARY);
                KveGuiStyle.drawTextSafe(g, hit.field(), L + 7, ry + 10, GUI_W - 32, KveGuiStyle.TEXT_MUTED);
            }
            scrollBar.render(g);
        }

        boolean flashing = !flashMsg.isEmpty() && System.currentTimeMillis() - flashAt < 4000;
        if (!flashing && !results.isEmpty() && !query.isEmpty() && !building) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.idusage.count", results.size()),
                    L + 7, T + GUI_H - 34, GUI_W - 90, KveGuiStyle.TEXT_MUTED);
        }
        if (flashing) {
            KveGuiStyle.drawTextSafe(g, flashMsg, L + 7, T + GUI_H - 34, GUI_W - 90, KveGuiStyle.ACCENT_GOLD);
        } else if (!query.isEmpty() && building) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.idusage.structures_indexing"),
                    L + 7, T + GUI_H - 34, GUI_W - 90, KveGuiStyle.TEXT_MUTED);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (scrollBar.mouseClicked(mx, my, btn)) return true;
        int listY = T + 40;
        int scroll = scrollBar.getScroll();
        for (int i = 0; i < Math.min(ROWS_VISIBLE, results.size() - scroll); i++) {
            int ry = listY + i * ROW_H;
            if (mx >= L + 4 && mx < L + GUI_W - 20 && my >= ry && my < ry + ROW_H - 2) {
                openPreview(results.get(i + scroll));
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
        if (scrollBar.mouseDragged(mx, my, b, dx, dy)) return true;
        return super.mouseDragged(mx, my, b, dx, dy);
    }
    @Override public boolean mouseReleased(double mx, double my, int b) {
        scrollBar.mouseReleased(mx, my, b);
        return super.mouseReleased(mx, my, b);
    }
    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scrollBar.mouseScrolled(delta);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { goBack(); return true; }
        if (getFocused() != null && getFocused().keyPressed(key, scan, mods)) return true;
        return super.keyPressed(key, scan, mods);
    }

    private void openPreview(IdUsageIndex.Hit hit) {
        if (hit.domain() == IdUsageIndex.Domain.STRUCTURE) {
            minecraft.setScreen(new com.kve.client.structure.StructureEditorScreen(this, hit.entryId()));
            return;
        }
        String script = switch (hit.domain()) {
            case RECIPE -> previewRecipe(hit.entryId());
            case ITEM -> {
                var e = KubeJSItemAttrExporter.getEntry(hit.entryId());
                yield e != null ? KubeJSItemAttrExporter.previewItemJS(e) : notFound();
            }
            case BLOCK -> {
                var e = com.kve.common.kubejs.KubeJSBlockAttrExporter.getEntry(hit.entryId());
                yield e != null ? com.kve.common.kubejs.KubeJSBlockAttrExporter.previewBlockJS(e) : notFound();
            }
            case MOB -> {
                var e = KubeJSMobExporter.getEntry(hit.entryId());
                yield e != null ? KubeJSMobExporter.previewMobJS(e) : notFound();
            }
            case TAG -> {
                var e = TagExporter.getEntry(hit.entryId());
                yield e != null ? TagExporter.previewTagJS(e) : notFound();
            }
            case LOOT -> {
                var e = LootTableExporter.getEntry(hit.entryId());
                yield e != null ? LootTableExporter.previewLoot(e) : notFound();
            }
            case EVENT -> {
                var e = EventExporter.getEntry(hit.entryId());
                yield e != null ? EventExporter.previewEventJS(e) : notFound();
            }
            case TRADE -> {
                var e = com.kve.common.kubejs.TradeExporter.getEntry(hit.entryId());
                yield e != null ? com.kve.common.kubejs.TradeExporter.previewTradeJS(e) : notFound();
            }
            case PACK_RULE -> com.kve.common.packrules.PackRulesExporter.buildServerScript(com.kve.common.packrules.PackRules.current())
                    + "\n" + com.kve.common.packrules.PackRulesExporter.buildClientInfoScript(com.kve.common.packrules.PackRules.current());
            case STRUCTURE -> throw new IllegalStateException("handled above");
        };
        minecraft.setScreen(new RecipePreviewScreen(this, hit.entryId(), script));
    }

    private String previewRecipe(String recipeId) {
        for (RecipeData r : KubeJSExporter.getAddedRecipes()) {
            if (r.recipeId.equals(recipeId)) return KubeJSExporter.previewRecipeJS(r);
        }
        return notFound();
    }

    private String notFound() { return "// " + Lang.get("gui.kve.idusage.entry_gone"); }
}
