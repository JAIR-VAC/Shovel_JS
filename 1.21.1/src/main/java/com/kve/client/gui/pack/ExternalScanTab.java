package com.kve.client.gui.pack;

import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KvePlateButton;
import com.kve.client.gui.KveScaledEditBox;
import com.kve.client.gui.settings.MainMenuScreen;
import com.kve.client.gui.ScrollBar;

import com.kve.common.Lang;
import com.kve.client.gui.studio.KveStudioShell;
import com.kve.client.gui.studio.KveStudioTab;
import com.kve.common.config.KVEConfig;
import com.kve.common.kubejs.ExternalScriptScanner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class ExternalScanTab extends Screen implements KveStudioTab {

    private static final int GUI_W_DEFAULT = 360;
    private static final int GUI_H_DEFAULT = 320;
    private static final int ROWS_VISIBLE = 8;
    private static final int ROW_H = 12;

    private int L, T, GUI_W, GUI_H;
    private boolean hosted = false;

    private Screen returnScreen = null;

    private enum Category { ITEMS, MOBS, TAGS }
    private Category shownCategory = Category.ITEMS;

    private final List<Map.Entry<String, String>> rows = new ArrayList<>();
    private ScrollBar scrollBar;

    private int toggleY, toggleW;

    private String flashMsg = "";
    private long flashAt = 0;
    private int flashColor = 0xFF66FF99;

    private KveScaledEditBox extraDirBox;

    public ExternalScanTab() {
        super(Lang.c("gui.kve.studio.tab.scan"));
    }

    public ExternalScanTab withReturnScreen(Screen s) {
        this.returnScreen = s;
        return this;
    }

    @Override
    protected void init() {
        if (KveStudioShell.reclaimIfHosted(this, returnScreen)) return;
        hosted = false;
        GUI_W = GUI_W_DEFAULT;
        GUI_H = GUI_H_DEFAULT;
        L = (width - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        buildWidgets();
    }

    @Override
    public void init(int x0, int y0, int w, int h) {
        if (this.minecraft == null) {
            this.minecraft = Minecraft.getInstance();
            this.font = this.minecraft.font;
        }
        hosted = true;
        L = x0;
        T = y0;
        GUI_W = Math.max(w, GUI_W_DEFAULT);
        GUI_H = Math.max(200, h);
        buildWidgets();
    }

    private void buildWidgets() {
        clearWidgets();

        ExternalScriptScanner.ensureRootsExist();

        int rescanW = 90;
        addRenderableWidget(new KvePlateButton(L + GUI_W - rescanW - 8, T + 18, rescanW, 14,
                Lang.c(ExternalScriptScanner.isScanning() ? "gui.kve.scan.scanning" : "gui.kve.btn.rescan"),
                b -> doScan()));

        toggleY = T + 36;
        toggleW = (GUI_W - 16) / 3;
        addRenderableWidget(new KvePlateButton(L + 8, toggleY, toggleW - 2, 14,
                categoryLabel(Category.ITEMS), b -> { shownCategory = Category.ITEMS; rebuildRows(); }));
        addRenderableWidget(new KvePlateButton(L + 8 + toggleW, toggleY, toggleW - 2, 14,
                categoryLabel(Category.MOBS), b -> { shownCategory = Category.MOBS; rebuildRows(); }));
        addRenderableWidget(new KvePlateButton(L + 8 + toggleW * 2, toggleY, toggleW - 2, 14,
                categoryLabel(Category.TAGS), b -> { shownCategory = Category.TAGS; rebuildRows(); }));

        scrollBar = new ScrollBar(L + GUI_W - 12, T + 118, ROWS_VISIBLE * ROW_H, 0, ROWS_VISIBLE);
        rebuildRows();

        int addRowY = T + GUI_H - 96;
        int addBtnW = 90;
        int rowGap = 4;
        int fieldW = GUI_W - 16 - addBtnW - rowGap;
        extraDirBox = new KveScaledEditBox(font, L + 8, addRowY + 1, fieldW, 14, Component.empty());
        extraDirBox.setMaxLength(200);
        extraDirBox.setHint(Lang.c("gui.kve.scan.extra_dir.hint"));
        addRenderableWidget(extraDirBox);
        addRenderableWidget(new KvePlateButton(L + 8 + fieldW + rowGap, addRowY, addBtnW, 14,
                Lang.c("gui.kve.scan.extra_dir.add"), b -> doAddExtraDir()));

        int exportRowY = T + GUI_H - 78;
        addRenderableWidget(new KvePlateButton(L + 8, exportRowY, GUI_W - 16, 14,
                Lang.c("gui.kve.scan.export.btn"), b -> doExport()));

        if (!ExternalScriptScanner.isScanned()) {
            doScan();
        }
    }

    private Component categoryLabel(Category c) {
        String key = switch (c) {
            case ITEMS -> "gui.kve.scan.tab.items";
            case MOBS -> "gui.kve.scan.tab.mobs";
            case TAGS -> "gui.kve.scan.tab.tags";
        };
        return Component.literal(Lang.get(key));
    }

    private int categoryCount(Category c) {
        return switch (c) {
            case ITEMS -> ExternalScriptScanner.getItemHitCount();
            case MOBS -> ExternalScriptScanner.getMobHitCount();
            case TAGS -> ExternalScriptScanner.getTagHitCount();
        };
    }

    private void drawCountBadges(GuiGraphics g) {
        Category[] cats = { Category.ITEMS, Category.MOBS, Category.TAGS };
        for (int i = 0; i < cats.length; i++) {
            String n = String.valueOf(categoryCount(cats[i]));
            int rightEdge = L + 8 + toggleW * i + (toggleW - 2) - 4;
            int nx = rightEdge - font.width(n);
            int color = shownCategory == cats[i] ? KveGuiStyle.TEXT_PRIMARY : KveGuiStyle.TEXT_MUTED;
            KveGuiStyle.drawTextSafe(g, n, nx, toggleY + 4, font.width(n) + 2, color);
        }
    }

    private void flash(String msg, boolean ok) {
        flashMsg = msg;
        flashColor = ok ? KveGuiStyle.TEXT_OK : KveGuiStyle.TEXT_ERROR;
        flashAt = System.currentTimeMillis();
    }

    private void doAddExtraDir() {
        if (extraDirBox == null) return;
        String value = extraDirBox.getValue();
        if (value == null || value.isBlank()) {
            flash(Lang.get("gui.kve.scan.extra_dir.empty"), false);
            return;
        }
        boolean added = KVEConfig.addExternalScanExtraDir(value);
        if (!added) {
            flash(Lang.get("gui.kve.scan.extra_dir.duplicate"), false);
            return;
        }
        extraDirBox.setValue("");
        flash(Lang.get("gui.kve.scan.extra_dir.added"), true);
        doScan();
    }

    private void doExport() {
        if (!ExternalScriptScanner.isScanned()
                || (ExternalScriptScanner.getItemHitCount() == 0
                    && ExternalScriptScanner.getMobHitCount() == 0
                    && ExternalScriptScanner.getTagHitCount() == 0)) {
            flash(Lang.get("gui.kve.scan.export.none"), false);
            return;
        }
        try {
            com.google.gson.JsonObject json = ExternalScriptScanner.exportHitsAsJson();
            Path dir = Paths.get("kubejs", "exports");
            Files.createDirectories(dir);
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
            Path out = dir.resolve("shovel_js_scan_" + stamp + ".json");
            String pretty = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(json);
            Files.writeString(out, pretty, StandardCharsets.UTF_8);
            flash(Lang.get("gui.kve.scan.export.done", out.toString().replace('\\', '/')), true);
        } catch (IOException ex) {
            flash(Lang.get("gui.kve.scan.export.fail", ex.getMessage()), false);
        }
    }

    private void doScan() {
        if (ExternalScriptScanner.isScanning()) return;
        ExternalScriptScanner.scanAsync(() -> {
            rebuildRows();
            long missing = ExternalScriptScanner.getLastRootStatuses().stream().filter(s -> !s.existed()).count();
            flashColor = missing == 0 ? 0xFF66FF99 : 0xFFFFAA55;
            flashMsg = Lang.get("gui.kve.rescan.done") + ": "
                    + ExternalScriptScanner.getItemHitCount() + " items, "
                    + ExternalScriptScanner.getMobHitCount() + " mobs, "
                    + ExternalScriptScanner.getTagHitCount() + " tags"
                    + (missing > 0 ? " - " + Lang.get("gui.kve.scan.missing_roots", missing) : "");
            flashAt = System.currentTimeMillis();
            if (!hosted) init();
        });
    }

    private void rebuildRows() {
        rows.clear();
        rows.addAll(switch (shownCategory) {
            case ITEMS -> ExternalScriptScanner.getItemHits();
            case MOBS -> ExternalScriptScanner.getMobHits();
            case TAGS -> ExternalScriptScanner.getTagHits();
        });
        if (scrollBar != null) scrollBar.setTotals(rows.size(), ROWS_VISIBLE);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (!hosted) renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, Lang.get("gui.kve.studio.tab.scan"));
        drawCountBadges(g);

        int statusY = T + 56;
        List<ExternalScriptScanner.RootStatus> statuses = ExternalScriptScanner.getLastRootStatuses();
        if (statuses.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.scan.not_run_yet"), L + 8, statusY, GUI_W - 16, KveGuiStyle.TEXT_MUTED);
            statusY += 10;
        } else {
            for (ExternalScriptScanner.RootStatus s : statuses) {
                String line = (s.existed()
                        ? "\u2713 " + s.path() + " (" + s.fileCount() + ")"
                        : "\u2717 " + s.path() + " - " + Lang.get("gui.kve.scan.root_missing"));
                int color = s.existed() ? KveGuiStyle.TEXT_OK : KveGuiStyle.TEXT_WARN;
                KveGuiStyle.drawTextSafe(g, line, L + 8, statusY, GUI_W - 16, color);
                statusY += 10;
            }
        }

        long lastScan = ExternalScriptScanner.getLastScanEpochMillis();
        if (lastScan > 0) {
            String when = new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date(lastScan));
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.scan.last_scan", when), L + 8, T + GUI_H - 44, GUI_W - 16, KveGuiStyle.TEXT_MUTED);
        }

        int listY = T + 118;
        if (rows.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.scan.no_hits"), L + 8, listY, GUI_W - 20, KveGuiStyle.TEXT_MUTED);
        }
        int scroll = scrollBar.getScroll();
        for (int i = 0; i < ROWS_VISIBLE; i++) {
            int idx = i + scroll;
            if (idx >= rows.size()) break;
            Map.Entry<String, String> row = rows.get(idx);
            int ry = listY + i * ROW_H;
            KveGuiStyle.drawTextSafe(g, row.getKey(), L + 8, ry, (GUI_W - 20) / 2, KveGuiStyle.TEXT_PRIMARY);
            KveGuiStyle.drawTextSafe(g, row.getValue(), L + 8 + (GUI_W - 20) / 2, ry, (GUI_W - 20) / 2, KveGuiStyle.TEXT_MUTED);
        }
        scrollBar.render(g);

        if (!flashMsg.isEmpty() && System.currentTimeMillis() - flashAt < 5000) {
            List<String> lines = KveGuiStyle.wrapTextSafe(font, flashMsg, GUI_W - 16);
            int fy = T + GUI_H - 30 - (lines.size() - 1) * 10;
            for (String line : lines) { KveGuiStyle.drawTextSafe(g, line, L + 8, fy, GUI_W - 16, flashColor); fy += 10; }
        }

        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
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
    public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        if (scrollBar != null) { scrollBar.mouseScrolled(delta); return true; }
        return super.mouseScrolled(mx, my, scrollX, delta);
    }

    @Override
    public void onClose() {
        if (KveStudioShell.exitIfHosted(this, returnScreen)) return;
        minecraft.setScreen(returnScreen != null ? returnScreen : new KveStudioShell());
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void removed() {}
}
