package com.kve.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.kve.CraftEditorMod;
import com.kve.common.Lang;
import com.kve.common.kubejs.KveScriptPaths;
import com.kve.common.packrules.PackRules;
import com.kve.common.persist.PackSnapshotManager;
import com.kve.common.search.PackDiagnostics;
import com.kve.common.util.RecentList;
import com.kve.common.util.SafeFiles;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

@OnlyIn(Dist.CLIENT)
public class PackHubScreen extends Screen {

    enum Category {
        HEALTH("gui.kve.hub.cat.health", 0xFFE07A6A),
        CONTENT("gui.kve.hub.cat.content", 0xFF7DB8E0),
        RECIPES("gui.kve.hub.cat.recipes", 0xFFE8C96A),
        PROJECT("gui.kve.hub.cat.project", 0xFF7DE0B0),
        FUN("gui.kve.hub.cat.fun", 0xFFD98AE8);
        final String key;
        final int color;
        Category(String key, int color) { this.key = key; this.color = color; }
    }

    /** {@code subTool}: solo aparece al buscar (vive dentro de otra herramienta). */
    record Tool(String nameKey, String descKey, Category cat, String keywords, boolean subTool, Function<Screen, Screen> open) {
        boolean matches(String q) {
            if (q.isEmpty()) return !subTool;
            String hay = (Lang.get(nameKey) + " " + Lang.get(descKey) + " " + Lang.get(cat.key) + " " + keywords).toLowerCase(Locale.ROOT);
            for (String w : q.split("\\s+")) if (!hay.contains(w)) return false;
            return true;
        }
    }

    static List<Tool> tools() {
        return List.of(
                new Tool("gui.kve.hub.check.name", "gui.kve.hub.check.desc", Category.HEALTH,
                        "probar test revisar check errores errors reload carga load logs kubejs sintaxis json",
                        false, PackToolReports::packCheck),
                new Tool("gui.kve.hub.balance.name", "gui.kve.hub.balance.desc", Category.HEALTH,
                        "balance duplicacion dupe exploit bucle loop tope cap vida health dano damage stack",
                        false, PackToolReports::balance),
                new Tool("gui.kve.hub.diag.name", "gui.kve.hub.diag.desc", Category.HEALTH, "ids rotos broken missing errores actualizar update",
                        false, PackDiagnosticsScreen::new),
                new Tool("gui.kve.hub.modreport.name", "gui.kve.hub.modreport.desc", Category.HEALTH, "mod quitar remove informe report dependencias",
                        false, ModReportScreen::new),
                new Tool("gui.kve.hub.usage.name", "gui.kve.hub.usage.desc", Category.HEALTH, "donde usa where used buscar id search",
                        false, p -> new IdUsageScreen(p, "")),
                new Tool("gui.kve.hub.rules.name", "gui.kve.hub.rules.desc", Category.CONTENT,
                        "nombre rename renombrar jei rei info compost compostar spawn mob gamerule reglas",
                        false, PackRulesScreen::new),
                new Tool("gui.kve.hub.bulk.name", "gui.kve.hub.bulk.desc", Category.CONTENT, "masa bulk stack rareza rarity combustible fuel ocultar hide",
                        false, BulkItemEditScreen::new),
                new Tool("gui.kve.hub.recipes.name", "gui.kve.hub.recipes.desc", Category.RECIPES,
                        "reemplazar replace unificar unify conflictos conflicts quitar remove sin receta uncraftable",
                        false, RecipeToolsScreen::new),
                recipeSub("replace", "reemplazar replace sustituir", p -> new RecipeReplaceScreen(p, false)),
                recipeSub("unify", "unificar unify tag", p -> new RecipeReplaceScreen(p, true)),
                recipeSub("uncraftable", "sin receta uncraftable crafteable", UncraftableItemsScreen::new),
                recipeSub("conflicts", "conflictos conflicts duplicadas", RecipeConflictsScreen::new),
                recipeSub("bulk", "quitar remove masa bulk borrar", RecipeBulkRemoveScreen::new),
                new Tool("gui.kve.hub.fun.name", "gui.kve.hub.fun.desc", Category.FUN,
                        "fun diversion aleatorio random randomizer aleatorizar caos chaos sorpresa",
                        false, com.kve.client.fun.FunScreen::new),
                new Tool("gui.kve.hub.packtools.name", "gui.kve.hub.packtools.desc", Category.PROJECT,
                        "notas version release notes changelog instantanea snapshot copia backup comparar compare restaurar",
                        false, PackToolsScreen::new),
                new Tool("gui.kve.hub.snapdiff.name", "gui.kve.hub.snapdiff.desc", Category.PROJECT,
                        "comparar compare diferencias diff instantanea snapshot version cambios changes",
                        false, SnapshotDiffScreen::new),
                new Tool("gui.kve.hub.library.name", "gui.kve.hub.library.desc", Category.PROJECT,
                        "plantillas templates biblioteca library compartir share mobs comercios trades importar",
                        false, TemplateLibraryScreen::new),
                new Tool("gui.kve.hub.guide.name", "gui.kve.hub.guide.desc", Category.PROJECT,
                        "guia guide documentacion docs markdown publicar wiki contenido",
                        false, PackToolReports::guide),
                new Tool("gui.kve.studio.tab.scan", "gui.kve.hub.scan.desc", Category.PROJECT,
                        "escaneo scan scripts externos external carpetas folders detectar",
                        false, p -> shellTab(p, "gui.kve.studio.tab.scan")),
                new Tool("gui.kve.studio.tab.io", "gui.kve.hub.io.desc", Category.PROJECT,
                        "importar exportar import export zip compartir share proyecto project",
                        false, p -> shellTab(p, "gui.kve.studio.tab.io")));
    }

    private static Tool recipeSub(String key, String keywords, Function<Screen, Screen> open) {
        return new Tool("gui.kve.recipe.tools." + key, "gui.kve.recipe.tools." + key + ".desc", Category.RECIPES, keywords, true, open);
    }

    private static Screen shellTab(Screen hub, String tabKey) {
        Screen parent = hub instanceof PackHubScreen h ? h.parent : null;
        com.kve.client.gui.studio.KveStudioShell shell = parent instanceof com.kve.client.gui.studio.KveStudioShell s
                ? s : new com.kve.client.gui.studio.KveStudioShell();
        return shell.showTab(tabKey);
    }

    private static final RecentList RECENT = new RecentList(4);
    private static boolean recentLoaded = false;

    private static Path recentFile() { return KveScriptPaths.CONFIG_ROOT.resolve("shovel_js_hub_recent.json"); }

    private static void loadRecent() {
        if (recentLoaded) return;
        recentLoaded = true;
        try {
            Path f = recentFile();
            if (!Files.exists(f)) return;
            List<String> keys = new ArrayList<>();
            for (JsonElement e : JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonArray()) keys.add(e.getAsString());
            RECENT.setAll(keys);
        } catch (Exception ex) {
            CraftEditorMod.LOGGER.debug("[Shovel JS] recientes del hub ilegibles: {}", ex.toString());
        }
    }

    private static void saveRecent() {
        JsonArray a = new JsonArray();
        for (String k : RECENT.items()) a.add(k);
        try { SafeFiles.writeAtomic(recentFile(), a.toString()); }
        catch (Exception ex) { CraftEditorMod.LOGGER.debug("[Shovel JS] no se pudo guardar recientes del hub: {}", ex.toString()); }
    }

    private static final int W = 380;
    private static final int H_MIN = 200;
    private static final int ROW_H = 26;

    private final Screen parent;
    private final List<Tool> shown = new ArrayList<>();
    private final Map<String, String> badgeText = new HashMap<>();
    private final Map<String, Integer> badgeColor = new HashMap<>();
    private KveScaledEditBox search;
    private ScrollBar scrollBar;
    private int cx, cy, h;
    private int selected = 0;

    public PackHubScreen(Screen parent) {
        super(Component.literal(Lang.get("gui.kve.hub.title")));
        this.parent = parent;
        loadRecent();
        computeBadges();
    }

    private void computeBadges() {
        try {
            int problems = PackDiagnostics.scanProject().size();
            badge("gui.kve.hub.diag.name", problems == 0 ? Lang.get("gui.kve.hub.badge.ok") : Lang.get("gui.kve.hub.badge.problems", problems),
                    problems == 0 ? KveGuiStyle.TEXT_OK : KveGuiStyle.TEXT_ERROR);
        } catch (Throwable ignored) { }
        try {
            PackRules r = PackRules.current();
            int n = r.renames.size() + r.infoPages.size() + r.compostables.size() + r.spawnBlocks.size() + r.gameRules.size();
            if (n > 0) badge("gui.kve.hub.rules.name", Lang.get("gui.kve.hub.badge.rules", n), KveGuiStyle.TEXT_MUTED);
        } catch (Throwable ignored) { }
        try {
            int n = PackSnapshotManager.list(PackSnapshotManager.gameDir()).size();
            if (n > 0) badge("gui.kve.hub.packtools.name", Lang.get("gui.kve.hub.badge.snapshots", n), KveGuiStyle.TEXT_MUTED);
        } catch (Throwable ignored) { }
    }

    private void badge(String nameKey, String text, int color) {
        badgeText.put(nameKey, text);
        badgeColor.put(nameKey, color);
    }

    @Override
    protected void init() {
        h = Math.max(H_MIN, Math.min(height - 20, KveGuiStyle.TITLEBAR_H + 24 + tools().size() * ROW_H + 30));
        cx = (width - W) / 2;
        cy = (height - h) / 2;
        String q = search != null ? search.getValue() : "";
        search = new KveScaledEditBox(font, cx + KveGuiStyle.PAD, cy + KveGuiStyle.TITLEBAR_H + 4, W - KveGuiStyle.PAD * 2, 14,
                Component.literal(Lang.get("gui.kve.hub.search")));
        search.setHint(Component.literal(Lang.get("gui.kve.hub.search")));
        search.setValue(q);
        search.setResponder(v -> { selected = 0; filter(); });
        addRenderableWidget(search);
        setInitialFocus(search);
        KveGuiStyle.ButtonRow row = new KveGuiStyle.ButtonRow(cx + KveGuiStyle.PAD, cy + h - KveGuiStyle.PAD - 16, cx + W - KveGuiStyle.PAD);
        addRenderableWidget(row.add(0, Lang.get("gui.kve.btn.close"), b -> onClose()));
        int keep = scrollBar != null ? scrollBar.getScroll() : 0;
        scrollBar = new ScrollBar(cx + W - KveGuiStyle.PAD - 4, listTop(), visibleRows() * ROW_H, 0, visibleRows());
        filter();
        scrollBar.setScroll(keep);
    }

    private void filter() {
        shown.clear();
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        List<Tool> base = new ArrayList<>();
        for (Tool t : tools()) if (t.matches(q)) base.add(t);
        shown.addAll(q.isEmpty() ? RECENT.recentFirst(base, Tool::nameKey) : base);
        selected = Math.max(0, Math.min(selected, shown.size() - 1));
        if (scrollBar != null) scrollBar.setTotals(shown.size(), visibleRows());
    }

    private int listTop() { return cy + KveGuiStyle.TITLEBAR_H + 24; }

    private int listBottom() { return cy + h - 26; }

    private int visibleRows() { return Math.max(1, (listBottom() - listTop()) / ROW_H); }

    private int rowW() { return W - KveGuiStyle.PAD * 2 - 8; }

    private int rowAt(double mx, double my) {
        int x = cx + KveGuiStyle.PAD;
        if (mx < x || mx >= x + rowW() || my < listTop()) return -1;
        int vis = (int) ((my - listTop()) / ROW_H);
        if (vis >= visibleRows()) return -1;
        int i = vis + scrollBar.getScroll();
        return i < shown.size() ? i : -1;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        KveGuiStyle.drawWindow(g, cx, cy, W, h, getTitle().getString());
        int x = cx + KveGuiStyle.PAD;
        int w = rowW();
        if (shown.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.hub.none"), x, listTop() + 4, w, KveGuiStyle.TEXT_MUTED);
        }
        int hover = rowAt(mx, my);
        int first = scrollBar.getScroll();
        boolean noQuery = search == null || search.getValue().isBlank();
        for (int vis = 0; vis < visibleRows(); vis++) {
            int i = first + vis;
            if (i >= shown.size()) break;
            int ry = listTop() + vis * ROW_H;
            Tool t = shown.get(i);
            int bg = i == selected ? KveGuiStyle.selectionBg() : (i == hover ? KveGuiStyle.BORDER_FIELD : KveGuiStyle.BG_FIELD);
            g.fill(x - 2, ry, x + w, ry + ROW_H - 2, bg);
            g.fill(x - 2, ry, x, ry + ROW_H - 2, KveGuiStyle.th(t.cat().color));

            String cat = Lang.get(t.cat().key);
            int catW = Math.round(font.width(cat) * KveGuiStyle.uiTextScale());
            KveGuiStyle.drawTextSafe(g, cat, x + w - catW - 3, ry + 13, catW + 2, KveGuiStyle.th(t.cat().color));
            String badge = badgeText.get(t.nameKey());
            int badgeCol = badgeColor.getOrDefault(t.nameKey(), KveGuiStyle.TEXT_MUTED);
            if (badge == null && noQuery && RECENT.rank(t.nameKey()) >= 0) {
                badge = Lang.get("gui.kve.hub.recent");
                badgeCol = KveGuiStyle.TEXT_MUTED;
            }
            int badgeW = 0;
            if (badge != null) {
                badgeW = Math.round(font.width(badge) * KveGuiStyle.uiTextScale());
                KveGuiStyle.drawTextSafe(g, badge, x + w - badgeW - 3, ry + 3, badgeW + 2, badgeCol);
            }
            KveGuiStyle.drawTextSafe(g, Lang.get(t.nameKey()), x + 3, ry + 3, w - badgeW - 12, KveGuiStyle.ACCENT_GOLD);
            KveGuiStyle.drawTextSafe(g, Lang.get(t.descKey()), x + 3, ry + 13, w - catW - 12, KveGuiStyle.TEXT_MUTED);
        }
        if (shown.size() > visibleRows()) scrollBar.render(g, mx, my);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (shown.size() > visibleRows() && scrollBar.mouseClicked(mx, my, btn)) return true;
        int i = rowAt(mx, my);
        if (i >= 0 && btn == 0) { open(shown.get(i)); return true; }
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
    public boolean mouseScrolled(double mx, double my, double delta) {
        return scrollBar.mouseScrolled(delta) || super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && !shown.isEmpty()) {
            open(shown.get(selected));
            return true;
        }
        if ((key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_UP) && !shown.isEmpty()) {
            selected = Math.floorMod(selected + (key == GLFW.GLFW_KEY_DOWN ? 1 : -1), shown.size());
            int first = scrollBar.getScroll();
            if (selected < first) scrollBar.setScroll(selected);
            else if (selected >= first + visibleRows()) scrollBar.setScroll(selected - visibleRows() + 1);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    private void open(Tool t) {
        RECENT.touch(t.nameKey());
        saveRecent();
        minecraft.setScreen(t.open().apply(this));
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
