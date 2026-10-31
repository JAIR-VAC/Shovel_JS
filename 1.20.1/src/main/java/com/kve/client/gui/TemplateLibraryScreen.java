package com.kve.client.gui;

import com.kve.client.permission.ClientPermissionState;
import com.kve.common.Lang;
import com.kve.common.packtools.TemplateLibrary;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Biblioteca de plantillas compartida entre packs
 * ({@link TemplateLibrary}): guardar las plantillas de mobs y comercios de
 * este pack como un paquete, e importar paquetes guardados desde otro pack
 * (o recibidos de otra persona: es un JSON en la carpeta de la biblioteca).
 */
@OnlyIn(Dist.CLIENT)
public class TemplateLibraryScreen extends Screen {

    private static final int W = 420;
    private static final int H = 250;
    private static final int ROW_H = 13;

    private final Screen parent;
    private final List<TemplateLibrary.BundleInfo> bundles = new ArrayList<>();
    private int selected = -1, scroll = 0;
    private KveScaledEditBox nameBox;
    private String name = "";
    private String flash = "";
    private int flashColor = KveGuiStyle.TEXT_OK;
    private int cx, cy;
    private int currentMobs, currentTrades, currentWorldGen;

    public TemplateLibraryScreen(Screen parent) {
        super(Component.literal(Lang.get("gui.kve.library.title")));
        this.parent = parent;
        reload();
    }

    private void reload() {
        bundles.clear();
        bundles.addAll(TemplateLibrary.list());
        int[] counts = TemplateLibrary.currentCounts();
        currentMobs = counts[0];
        currentTrades = counts[1];
        currentWorldGen = counts[2];
        if (selected >= bundles.size()) selected = -1;
    }

    private void flash(String msg, int color) { flash = msg; flashColor = color; }

    @Override
    protected void init() {
        cx = (width - W) / 2;
        cy = (height - H) / 2;
        int x = cx + KveGuiStyle.PAD, w = W - KveGuiStyle.PAD * 2;
        int y = cy + KveGuiStyle.TITLEBAR_H + 18;
        nameBox = new KveScaledEditBox(font, x, y, w - 130, 13, Component.literal(Lang.get("gui.kve.library.name_hint")));
        nameBox.setHint(Component.literal(Lang.get("gui.kve.library.name_hint")));
        nameBox.setMaxLength(40);
        nameBox.setValue(name);
        nameBox.setResponder(v -> name = v);
        addRenderableWidget(nameBox);
        KveGuiStyle.ButtonRow top = new KveGuiStyle.ButtonRow(x + w - 126, y, x + w);
        addRenderableWidget(top.add(0, Lang.get("gui.kve.library.save"), b -> saveCurrent()));

        KveGuiStyle.ButtonRow row = new KveGuiStyle.ButtonRow(x, cy + H - KveGuiStyle.PAD - 16, x + w);
        addRenderableWidget(row.add(0, Lang.get("gui.kve.library.import"), b -> importSelected()));
        addRenderableWidget(row.add(0, Lang.get("gui.kve.btn.delete"), b -> deleteSelected()).withDanger(true));
        addRenderableWidget(row.add(0, Lang.get("gui.kve.guide.open_folder"), b -> {
            try { Files.createDirectories(TemplateLibrary.dir()); } catch (Exception ignored) {}
            Util.getPlatform().openFile(TemplateLibrary.dir().toFile());
        }));
        addRenderableWidget(row.add(0, Lang.get("gui.kve.btn.close"), b -> onClose()));
    }

    private void saveCurrent() {
        int[] counts = TemplateLibrary.currentCounts();
        int mobs = counts[0], trades = counts[1], rules = counts[2];
        if (mobs + trades + rules == 0) { flash(Lang.get("gui.kve.library.nothing_to_save"), KveGuiStyle.TEXT_WARN); return; }
        try {
            String n = name.isBlank() ? Lang.get("gui.kve.library.default_name") : name.trim();
            TemplateLibrary.exportCurrent(n);
            name = "";
            reload();
            selected = bundles.isEmpty() ? -1 : 0;
            flash(Lang.get("gui.kve.library.saved", n, mobs, trades, rules), KveGuiStyle.TEXT_OK);
            rebuildWidgets();
        } catch (Exception e) {
            flash(Lang.get("gui.kve.report.failed", e.getMessage()), KveGuiStyle.TEXT_ERROR);
        }
    }

    private void importSelected() {
        if (selected < 0) { flash(Lang.get("gui.kve.library.pick_first"), KveGuiStyle.TEXT_WARN); return; }
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied"), KveGuiStyle.TEXT_ERROR); return; }
        try {
            TemplateLibrary.ImportResult r = TemplateLibrary.importBundle(bundles.get(selected).fileName());
            WorldGenStaging.stageNew(r.worldGen());
            reload();
            flash(Lang.get("gui.kve.library.imported", r.mobs(), r.trades(), r.worldGen().size()), KveGuiStyle.TEXT_OK);
        } catch (Exception e) {
            flash(Lang.get("gui.kve.report.failed", e.getMessage()), KveGuiStyle.TEXT_ERROR);
        }
    }

    private void deleteSelected() {
        if (selected < 0) { flash(Lang.get("gui.kve.library.pick_first"), KveGuiStyle.TEXT_WARN); return; }
        TemplateLibrary.BundleInfo b = bundles.get(selected);
        minecraft.setScreen(new KveConfirmScreen(ok -> {
            minecraft.setScreen(this);
            if (!ok) return;
            try {
                TemplateLibrary.delete(b.fileName());
                reload();
                selected = -1;
                flash(Lang.get("gui.kve.library.deleted", b.name()), KveGuiStyle.TEXT_OK);
            } catch (Exception e) {
                flash(Lang.get("gui.kve.report.failed", e.getMessage()), KveGuiStyle.TEXT_ERROR);
            }
        }, Component.literal(Lang.get("gui.kve.library.delete_title")), Component.literal(Lang.get("gui.kve.library.delete_msg", b.name()))));
    }

    private int listTop() { return cy + KveGuiStyle.TITLEBAR_H + 38; }

    private int rows() { return (cy + H - KveGuiStyle.PAD - 34 - listTop()) / ROW_H; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        KveGuiStyle.drawWindow(g, cx, cy, W, H, getTitle().getString());
        int x = cx + KveGuiStyle.PAD, w = W - KveGuiStyle.PAD * 2;
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.library.current", currentMobs, currentTrades, currentWorldGen),
                x, cy + KveGuiStyle.TITLEBAR_H + 5, w, KveGuiStyle.TEXT_LABEL);
        int ly = listTop(), rows = rows();
        g.fill(x - 2, ly - 1, x + w, ly + rows * ROW_H + 1, KveGuiStyle.BG_FIELD);
        if (bundles.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.library.empty"), x, ly + 3, w, KveGuiStyle.TEXT_MUTED);
        }
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
        for (int i = 0; i < rows && i + scroll < bundles.size(); i++) {
            TemplateLibrary.BundleInfo b = bundles.get(i + scroll);
            int ry = ly + i * ROW_H;
            if (i + scroll == selected) g.fill(x - 2, ry, x + w, ry + ROW_H, KveGuiStyle.selectionBg());
            KveGuiStyle.drawTextSafe(g, b.name(), x, ry + 3, w - 230, KveGuiStyle.TEXT_PRIMARY);
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.library.row", b.mobs(), b.trades(), b.worldGen(), KveGuiStyle.formatDate("yyyy-MM-dd", b.createdAt())),
                    x + w - 226, ry + 3, 224, KveGuiStyle.TEXT_MUTED);
        }
        if (!flash.isEmpty()) KveGuiStyle.drawTextSafe(g, flash, x, ly + rows * ROW_H + 4, w, flashColor);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int x = cx + KveGuiStyle.PAD, ly = listTop();
        if (btn == 0 && mx >= x && mx < cx + W - KveGuiStyle.PAD && my >= ly && my < ly + rows() * ROW_H) {
            int i = (int) ((my - ly) / ROW_H) + scroll;
            if (i < bundles.size()) { selected = i; return true; }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Math.max(0, Math.min(Math.max(0, bundles.size() - rows()), scroll + (delta > 0 ? -1 : 1)));
        return true;
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
