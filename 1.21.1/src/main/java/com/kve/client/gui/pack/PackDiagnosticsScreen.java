package com.kve.client.gui.pack;

import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KveScaledEditBox;

import com.kve.common.Lang;
import com.kve.common.search.PackDiagnostics;
import com.kve.common.search.ProjectReferences;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@OnlyIn(Dist.CLIENT)
public class PackDiagnosticsScreen extends Screen {

    private static final int W = 420;
    private static final int H = 262;
    private static final int ROW_H = 12;
    private static final int ROWS = 15;

    private final Screen parent;
    private final List<PackDiagnostics.Problem> all = new ArrayList<>();
    private final List<PackDiagnostics.Problem> problems = new ArrayList<>();
    private enum Filter { ALL, MODS, IDS }
    private Filter filter = Filter.ALL;
    private KveScaledEditBox searchBox;
    private String query = "";
    private int scroll = 0;
    private String flash = "";
    private int cx, cy;

    public PackDiagnosticsScreen(Screen parent) {
        super(Component.literal(Lang.get("gui.kve.health.title")));
        this.parent = parent;
        rescan();
    }

    private void rescan() {
        all.clear();
        all.addAll(PackDiagnostics.scanProject());
        applyFilter();
    }

    private void applyFilter() {
        problems.clear();
        String q = query.trim().toLowerCase(Locale.ROOT);
        for (PackDiagnostics.Problem p : all) {
            if (filter == Filter.MODS && !p.modMissing()) continue;
            if (filter == Filter.IDS && p.modMissing()) continue;
            if (!q.isEmpty() && !line(p).toLowerCase(Locale.ROOT).contains(q)) continue;
            problems.add(p);
        }
        scroll = 0;
    }

    private String filterLabel() {
        return Lang.get("gui.kve.health.filter." + filter.name().toLowerCase(Locale.ROOT));
    }

    @Override
    protected void init() {
        cx = (width - W) / 2;
        cy = (height - H) / 2;
        searchBox = new KveScaledEditBox(font, cx + W - KveGuiStyle.PAD - 140, cy + KveGuiStyle.TITLEBAR_H + 3, 140, 11,
                Component.literal(Lang.get("gui.kve.health.search")));
        searchBox.setHint(Component.literal(Lang.get("gui.kve.health.search")));
        searchBox.setValue(query);
        searchBox.setResponder(v -> { query = v; applyFilter(); });
        addRenderableWidget(searchBox);
        KveGuiStyle.ButtonRow row = new KveGuiStyle.ButtonRow(cx + KveGuiStyle.PAD, cy + H - KveGuiStyle.PAD - 16, cx + W - KveGuiStyle.PAD);
        addRenderableWidget(row.add(0, Lang.get("gui.kve.health.rescan"), b -> { rescan(); flash = Lang.get("gui.kve.health.rescanned", all.size()); }));
        addRenderableWidget(row.add(0, filterLabel(), b -> {
            filter = Filter.values()[(filter.ordinal() + 1) % Filter.values().length];
            applyFilter();
            b.setMessage(Component.literal(filterLabel()));
        }));
        addRenderableWidget(row.add(0, Lang.get("gui.kve.health.copy"), b -> copy()));
        addRenderableWidget(row.add(0, Lang.get("gui.kve.btn.close"), b -> onClose()));
    }

    static String domainLabel(ProjectReferences.Domain d) {
        return Lang.get("gui.kve.health.domain." + d.name().toLowerCase(Locale.ROOT));
    }

    private String line(PackDiagnostics.Problem p) {
        var r = p.ref();
        String why = Lang.get(p.modMissing() ? "gui.kve.health.mod_missing" : "gui.kve.health.id_missing");
        return "[" + domainLabel(r.domain()) + "] " + r.entryId() + " · " + r.field() + ": " + r.id() + "  — " + why;
    }

    private void copy() {
        if (problems.isEmpty()) return;
        List<String> lines = new ArrayList<>();
        for (PackDiagnostics.Problem p : problems) lines.add(line(p).replace("§", ""));
        minecraft.keyboardHandler.setClipboard(String.join("\n", lines));
        flash = Lang.get("gui.kve.health.copied", problems.size());
    }

    private int listTop() { return cy + KveGuiStyle.TITLEBAR_H + 30; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, cx, cy, W, H, getTitle().getString());
        int x = cx + KveGuiStyle.PAD;
        int w = W - KveGuiStyle.PAD * 2;
        int y = cy + KveGuiStyle.TITLEBAR_H + 5;
        int sumW = w - 146;
        if (all.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.health.ok"), x, y, sumW, KveGuiStyle.TEXT_OK);
        } else {
            long missingMods = all.stream().filter(PackDiagnostics.Problem::modMissing).count();
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.health.summary", all.size(), PackDiagnostics.affectedEntries(all), missingMods),
                    x, y, sumW, KveGuiStyle.TEXT_WARN);
        }
        if (problems.isEmpty() && !all.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.health.filtered_none"), x, listTop() + 2, w, KveGuiStyle.TEXT_MUTED);
        }
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.health.hint"), x, y + 11, w, KveGuiStyle.TEXT_MUTED);

        int ly = listTop();
        g.fill(x - 2, ly - 1, x + w, ly + ROWS * ROW_H + 1, KveGuiStyle.BG_FIELD);
        int hover = rowAt(mx, my);
        for (int i = 0; i < ROWS && i + scroll < problems.size(); i++) {
            PackDiagnostics.Problem p = problems.get(i + scroll);
            int ry = ly + i * ROW_H;
            if (i + scroll == hover) g.fill(x - 2, ry, x + w, ry + ROW_H, 0x22FFFFFF);
            KveGuiStyle.drawTextSafe(g, line(p), x, ry + 2, w - 2, p.modMissing() ? KveGuiStyle.TEXT_WARN : KveGuiStyle.TEXT_ERROR);
        }
        if (problems.size() > ROWS) {
            KveGuiStyle.drawTextSafe(g, (scroll + 1) + "-" + Math.min(problems.size(), scroll + ROWS) + " / " + problems.size(),
                    x, ly + ROWS * ROW_H + 4, 120, KveGuiStyle.TEXT_MUTED);
        }
        if (!flash.isEmpty()) KveGuiStyle.drawTextSafe(g, flash, x + 130, ly + ROWS * ROW_H + 4, w - 130, KveGuiStyle.TEXT_OK);
        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
        if (hover >= 0) {
            g.renderTooltip(font, List.of(Component.literal(line(problems.get(hover))),
                    Component.literal(Lang.get("gui.kve.health.click_hint"))), java.util.Optional.empty(), mx, my);
        }
    }

    private int rowAt(double mx, double my) {
        int x = cx + KveGuiStyle.PAD;
        int ly = listTop();
        if (mx < x || mx >= cx + W - KveGuiStyle.PAD || my < ly || my >= ly + ROWS * ROW_H) return -1;
        int i = (int) ((my - ly) / ROW_H) + scroll;
        return i < problems.size() ? i : -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int i = rowAt(mx, my);
        if (i >= 0 && btn == 0) {
            minecraft.setScreen(new IdUsageScreen(this, problems.get(i).ref().id()));
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        scroll = Math.max(0, Math.min(Math.max(0, problems.size() - ROWS), scroll + (delta > 0 ? -3 : 3)));
        return true;
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
