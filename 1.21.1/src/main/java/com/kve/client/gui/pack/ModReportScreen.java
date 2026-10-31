package com.kve.client.gui.pack;

import com.kve.client.gui.KveGuiStyle;

import com.kve.common.Lang;
import com.kve.common.search.PackDiagnostics;
import com.kve.common.search.ProjectReferences;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Informe por mod: "¿que cambie de este mod?". Izquierda, los
 * mods que el proyecto referencia (mas referencias primero); derecha, cada
 * referencia agrupada por editor. Sirve antes de sacar o actualizar un mod
 * del pack: muestra exactamente que recetas/tags/loot/trades se rompen.
 */
@OnlyIn(Dist.CLIENT)
public class ModReportScreen extends Screen {

    private static final int W = 440;
    private static final int H = 262;
    private static final int LIST_W = 130;
    private static final int ROW_H = 12;
    private static final int ROWS = 16;

    private final Screen parent;
    private final List<ProjectReferences.Ref> refs;
    private final List<Map.Entry<String, Integer>> mods = new ArrayList<>();
    private final List<Object> rows = new ArrayList<>();
    private int selected = 0, modScroll = 0, rowScroll = 0;
    private String flash = "";
    private int cx, cy;

    public ModReportScreen(Screen parent) {
        super(Component.literal(Lang.get("gui.kve.modreport.title")));
        this.parent = parent;
        this.refs = ProjectReferences.collect();
        mods.addAll(PackDiagnostics.namespaceCounts(refs).entrySet());
        rebuildRows();
    }

    private void rebuildRows() {
        rows.clear();
        rowScroll = 0;
        if (mods.isEmpty()) return;
        PackDiagnostics.forNamespace(refs, mods.get(selected).getKey()).forEach((domain, list) -> {
            rows.add(PackDiagnosticsScreen.domainLabel(domain) + " (" + list.size() + ")");
            rows.addAll(list);
        });
    }

    @Override
    protected void init() {
        cx = (width - W) / 2;
        cy = (height - H) / 2;
        KveGuiStyle.ButtonRow row = new KveGuiStyle.ButtonRow(cx + KveGuiStyle.PAD, cy + H - KveGuiStyle.PAD - 16, cx + W - KveGuiStyle.PAD);
        addRenderableWidget(row.add(0, Lang.get("gui.kve.modreport.copy"), b -> copy()));
        addRenderableWidget(row.add(0, Lang.get("gui.kve.btn.close"), b -> onClose()));
    }

    private void copy() {
        if (mods.isEmpty()) return;
        List<String> out = new ArrayList<>();
        out.add(Lang.get("gui.kve.modreport.copy_header", mods.get(selected).getKey()));
        for (Object o : rows) out.add(o instanceof String s ? "\n" + s : "  " + line((ProjectReferences.Ref) o));
        minecraft.keyboardHandler.setClipboard(String.join("\n", out));
        flash = Lang.get("gui.kve.modreport.copied");
    }

    private static String line(ProjectReferences.Ref r) {
        return r.entryId() + " · " + r.field() + ": " + r.id();
    }

    private int top() { return cy + KveGuiStyle.TITLEBAR_H + 18; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, cx, cy, W, H, getTitle().getString());
        int x = cx + KveGuiStyle.PAD;
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.modreport.hint"), x, cy + KveGuiStyle.TITLEBAR_H + 4, W - 16, KveGuiStyle.TEXT_MUTED);
        int ly = top();
        g.fill(x - 2, ly - 1, x + LIST_W, ly + ROWS * ROW_H + 1, KveGuiStyle.BG_FIELD);
        if (mods.isEmpty()) KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.modreport.empty"), x, ly + 2, LIST_W - 4, KveGuiStyle.TEXT_MUTED);
        for (int i = 0; i < ROWS && i + modScroll < mods.size(); i++) {
            int idx = i + modScroll;
            var m = mods.get(idx);
            int ry = ly + i * ROW_H;
            boolean hov = mx >= x && mx < x + LIST_W && my >= ry && my < ry + ROW_H;
            if (idx == selected || hov) g.fill(x - 2, ry, x + LIST_W, ry + ROW_H, idx == selected ? KveGuiStyle.th(0xFF2A5E9A) : KveGuiStyle.BORDER_FIELD);
            boolean loaded = ModList.get() == null || "minecraft".equals(m.getKey()) || ModList.get().isLoaded(m.getKey());
            KveGuiStyle.drawTextSafe(g, m.getKey() + " (" + m.getValue() + ")", x, ry + 2, LIST_W - 4,
                    loaded ? KveGuiStyle.TEXT_PRIMARY : KveGuiStyle.TEXT_WARN);
        }

        int rx = x + LIST_W + 8;
        int rw = cx + W - KveGuiStyle.PAD - rx;
        g.fill(rx - 2, ly - 1, rx + rw, ly + ROWS * ROW_H + 1, KveGuiStyle.BG_FIELD);
        for (int i = 0; i < ROWS && i + rowScroll < rows.size(); i++) {
            Object o = rows.get(i + rowScroll);
            int ry = ly + i * ROW_H;
            if (o instanceof String header) KveGuiStyle.drawTextSafe(g, header, rx, ry + 2, rw - 4, KveGuiStyle.ACCENT_GOLD);
            else KveGuiStyle.drawTextSafe(g, "  " + line((ProjectReferences.Ref) o), rx, ry + 2, rw - 4, KveGuiStyle.TEXT_LABEL);
        }
        if (!flash.isEmpty()) KveGuiStyle.drawTextSafe(g, flash, rx, ly + ROWS * ROW_H + 4, rw, KveGuiStyle.TEXT_OK);
        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int x = cx + KveGuiStyle.PAD;
        int ly = top();
        if (btn == 0 && mx >= x && mx < x + LIST_W && my >= ly && my < ly + ROWS * ROW_H) {
            int idx = (int) ((my - ly) / ROW_H) + modScroll;
            if (idx < mods.size()) { selected = idx; rebuildRows(); flash = ""; }
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        int step = delta > 0 ? -3 : 3;
        if (mx < cx + KveGuiStyle.PAD + LIST_W) modScroll = Math.max(0, Math.min(Math.max(0, mods.size() - ROWS), modScroll + step));
        else rowScroll = Math.max(0, Math.min(Math.max(0, rows.size() - ROWS), rowScroll + step));
        return true;
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
