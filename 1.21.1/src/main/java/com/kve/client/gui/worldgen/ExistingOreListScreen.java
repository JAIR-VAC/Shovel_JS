package com.kve.client.gui.worldgen;

import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KvePlateButton;
import com.kve.client.gui.KveScaledEditBox;
import com.kve.client.gui.ScrollBar;
import com.kve.client.gui.MiniItemIcon;
import com.kve.client.gui.SearchFilter;

import com.kve.common.Lang;
import com.kve.client.worldgen.ExistingOreScanner;
import com.kve.client.worldgen.ExistingOreScanner.ExistingOre;
import com.kve.common.worldgen.OreOverride;
import com.kve.common.worldgen.OreOverrideExporter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * "Minerales existentes" del editor de World Gen: todos los ores que el
 * mundo genera hoy (vanilla y de mods, ver {@link ExistingOreScanner}) con
 * su cantidad, altura y tamano actuales, y si KVE ya los modifico o quito.
 * Click en una fila abre {@link ExistingOreEditScreen}.
 */
@OnlyIn(Dist.CLIENT)
public class ExistingOreListScreen extends Screen {

    private static final int GUI_W = 340;
    private static final int ROW_H = 22;
    private static final int ROWS = 9;
    private static final int GUI_H = 40 + ROWS * ROW_H + 26;

    private final Screen parent;
    /** null = sin servidor integrado (no se pueden leer los registros de worldgen). */
    private List<ExistingOre> all;
    private final List<ExistingOre> shown = new ArrayList<>();
    private String query = "";
    private boolean onlyChanged = false;
    private int L, T;
    private ScrollBar scrollBar;

    public ExistingOreListScreen(Screen parent) {
        super(Lang.c("gui.kve.worldgen.ores.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        L = (width - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        clearWidgets();
        OreOverrideExporter.loadIfNeeded();
        if (all == null) all = mergeWithOverrides(ExistingOreScanner.scan());

        EditBox search = new KveScaledEditBox(font, L + 6, T + 18, GUI_W - 110, 12, Lang.c("gui.kve.event.search_hint"));
        search.setMaxLength(64);
        search.setValue(query);
        search.setResponder(s -> { query = s; filter(); });
        addRenderableWidget(search);
        addRenderableWidget(new KvePlateButton(L + GUI_W - 100, T + 17, 94, 14,
                Lang.c(onlyChanged ? "gui.kve.worldgen.ores.filter_changed" : "gui.kve.worldgen.ores.filter_all"),
                b -> { onlyChanged = !onlyChanged; init(); }).withSelected(onlyChanged));

        scrollBar = new ScrollBar(L + GUI_W - 14, T + 36, ROWS * ROW_H, 0, ROWS);
        filter();

        addRenderableWidget(new KvePlateButton(L + 6, T + GUI_H - 20, 80, 14,
                Lang.c("gui.kve.btn.back"), b -> onClose()));
        addRenderableWidget(new KvePlateButton(L + 92, T + GUI_H - 20, 80, 14,
                Lang.c("gui.kve.btn.rescan"), b -> { all = null; init(); }));
    }

    private static List<ExistingOre> mergeWithOverrides(List<ExistingOre> scanned) {
        if (scanned == null) return null;
        List<ExistingOre> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (ExistingOre ore : scanned) {
            OreOverride o = OreOverrideExporter.getEntry(ore.placedId());
            if (o != null) {
                out.add(new ExistingOre(o.placedFeatureId, o.originalPlaced, o.originalConfigured, o.step,
                        o.originalBiomes, o.dimMinY, o.dimHeight, ore.oreBlock()));
            } else {
                out.add(ore);
            }
            seen.add(ore.placedId());
        }
        for (OreOverride o : OreOverrideExporter.getEntries()) {
            if (seen.contains(o.placedFeatureId)) continue;
            String block = o.original().targetBlocks.isEmpty() ? "" : o.original().targetBlocks.get(0);
            out.add(new ExistingOre(o.placedFeatureId, o.originalPlaced, o.originalConfigured, o.step,
                    o.originalBiomes, o.dimMinY, o.dimHeight, block));
        }
        out.sort(java.util.Comparator.comparing(ExistingOre::placedId));
        return out;
    }

    private void filter() {
        shown.clear();
        if (all != null) {
            String q = query.trim().toLowerCase(Locale.ROOT);
            for (ExistingOre ore : all) {
                if (onlyChanged && OreOverrideExporter.getEntry(ore.placedId()) == null) continue;
                if (!q.isEmpty() && !SearchFilter.matches(ore.placedId(), ore.oreBlock(), query)) continue;
                shown.add(ore);
            }
        }
        if (scrollBar != null) scrollBar.setTotals(shown.size(), ROWS);
    }

    private void open(ExistingOre ore) {
        OreOverride existing = OreOverrideExporter.getEntry(ore.placedId());
        minecraft.setScreen(new ExistingOreEditScreen(this, existing != null ? existing.copy() : ore.toOverride(), existing));
    }

    static String summary(OreOverride o) {
        StringBuilder sb = new StringBuilder();
        if (o.mode == OreOverride.Mode.REMOVE) return Lang.get("gui.kve.worldgen.ores.removed_summary");
        if (o.count != null) sb.append('x').append(o.count).append(Lang.get("gui.kve.worldgen.ores.per_chunk"));
        if (o.rarity != null) sb.append(sb.length() > 0 ? " · " : "").append("1/").append(o.rarity);
        if (o.minY != null && o.maxY != null) {
            sb.append(sb.length() > 0 ? " · " : "").append("Y ").append(o.minY).append("..").append(o.maxY)
                    .append(" (").append(OreOverride.shortType(o.heightType)).append(')');
        }
        if (o.veinSize != null) sb.append(" · ").append(Lang.get("gui.kve.worldgen.ores.size_short", o.veinSize));
        return sb.toString();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, Lang.get("gui.kve.worldgen.ores.title")
                + (all == null ? "" : " (" + shown.size() + ")"));
        int listY = T + 36;
        if (all == null) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.worldgen.ores.no_server"), L + 8, listY + 4, GUI_W - 16, KveGuiStyle.TEXT_WARN);
        } else if (shown.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.worldgen.ores.empty"), L + 8, listY + 4, GUI_W - 16, KveGuiStyle.TEXT_MUTED);
        }
        int scroll = scrollBar.getScroll();
        for (int i = 0; i < Math.min(ROWS, shown.size() - scroll); i++) {
            ExistingOre ore = shown.get(i + scroll);
            int ry = listY + i * ROW_H;
            boolean hov = KveGuiStyle.isHovered(mx, my, L + 4, ry, GUI_W - 20, ROW_H - 2);
            g.fill(L + 4, ry, L + GUI_W - 20, ry + ROW_H - 2, hov ? KveGuiStyle.BORDER_FIELD : KveGuiStyle.BG_FIELD);
            MiniItemIcon.draw(g, L + 7, ry + 2, 16, MiniItemIcon.block(ore.oreBlock()));
            OreOverride ov = OreOverrideExporter.getEntry(ore.placedId());
            String status = "";
            int statusCol = KveGuiStyle.TEXT_MUTED;
            if (ov != null) {
                status = Lang.get(ov.mode == OreOverride.Mode.REMOVE ? "gui.kve.worldgen.ores.status_removed" : "gui.kve.worldgen.ores.status_modified")
                        + (ov.enabled ? "" : " [OFF]");
                statusCol = ov.mode == OreOverride.Mode.REMOVE ? KveGuiStyle.TEXT_ERROR : KveGuiStyle.ACCENT_GOLD;
            }
            int statusW = status.isEmpty() ? 0 : font.width(status) + 6;
            KveGuiStyle.drawTextSafe(g, ore.placedId(), L + 27, ry + 2, GUI_W - 50 - statusW, KveGuiStyle.TEXT_PRIMARY);
            if (!status.isEmpty()) KveGuiStyle.drawTextSafe(g, status, L + GUI_W - 22 - statusW, ry + 2, statusW, statusCol);
            OreOverride view = ov != null ? ov : ore.toOverride();
            String line = summary(view) + " · " + Lang.get("gui.kve.worldgen.ores.biomes_short", ore.biomes().size());
            KveGuiStyle.drawTextSafe(g, line, L + 27, ry + 12, GUI_W - 50, KveGuiStyle.TEXT_MUTED);
        }
        scrollBar.render(g);
        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (scrollBar.mouseClicked(mx, my, btn)) return true;
        int listY = T + 36;
        int scroll = scrollBar.getScroll();
        for (int i = 0; i < Math.min(ROWS, shown.size() - scroll); i++) {
            int ry = listY + i * ROW_H;
            if (mx >= L + 4 && mx < L + GUI_W - 20 && my >= ry && my < ry + ROW_H - 2) {
                open(shown.get(i + scroll));
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
    @Override public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        scrollBar.mouseScrolled(delta);
        return true;
    }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
