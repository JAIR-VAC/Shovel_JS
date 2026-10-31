package com.kve.client.gui.pack;

import com.kve.client.gui.KveConfirmScreen;
import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KvePlateButton;
import com.kve.client.gui.KveScaledEditBox;
import com.kve.client.gui.recipe.RecipePreviewScreen;

import com.kve.CraftEditorMod;
import com.kve.common.Lang;
import com.kve.client.gui.history.KveChangeLog;
import com.kve.client.permission.ClientPermissionState;
import com.kve.common.persist.PackSnapshotManager;
import com.kve.common.persist.ReleaseNotesBuilder;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class PackToolsScreen extends Screen {

    private static final int W = 380;
    private static final int H = 272;
    private static final int SNAP_ROWS = 7;
    private static final long DAY = 24L * 60 * 60 * 1000;
    private static final int[] RANGES_DAYS = {7, 30, 90, 0};
    private static Path notesFile() { return PackSnapshotManager.gameDir().resolve("shovel_js_notas_de_version.md"); }

    private final Screen parent;
    private int rangeIdx = 0;
    private String notesTitle = "";
    private String snapLabel = "";
    private List<PackSnapshotManager.Info> snaps = new ArrayList<>();
    private int selected = -1;
    private int scroll = 0;
    private String status = "";
    private int statusColor = KveGuiStyle.TEXT_MUTED;
    private int cx, cy;
    private final SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT);

    public PackToolsScreen(Screen parent) {
        super(Component.literal(Lang.get("gui.kve.pack_tools.title")));
        this.parent = parent;
    }

    @Override
    protected void init() {
        cx = (width - W) / 2;
        cy = (height - H) / 2;
        snaps = PackSnapshotManager.list(PackSnapshotManager.gameDir());
        if (selected >= snaps.size()) selected = -1;
        int x = cx + KveGuiStyle.PAD;
        int innerW = W - KveGuiStyle.PAD * 2;
        int y = cy + KveGuiStyle.TITLEBAR_H + 16;

        KveScaledEditBox title = new KveScaledEditBox(font, x, y, innerW - 124, 14, Component.literal(Lang.get("gui.kve.pack_tools.notes_title_hint")));
        title.setHint(Component.literal(Lang.get("gui.kve.pack_tools.notes_title_hint")));
        title.setMaxLength(80);
        title.setValue(notesTitle);
        title.setResponder(s -> notesTitle = s);
        addRenderableWidget(title);
        int d = RANGES_DAYS[rangeIdx];
        String rangeLabel = d == 0 ? Lang.get("gui.kve.pack_tools.range_all") : Lang.get("gui.kve.pack_tools.range_days", d);
        addRenderableWidget(new KvePlateButton(x + innerW - 120, y, 120, 14, Component.literal(rangeLabel), b -> {
            rangeIdx = (rangeIdx + 1) % RANGES_DAYS.length;
            rebuild();
        }));
        y += 18;
        KveGuiStyle.ButtonRow notesRow = new KveGuiStyle.ButtonRow(x, y, x + innerW);
        addRenderableWidget(notesRow.add(0, Lang.get("gui.kve.pack_tools.notes_save"), b -> exportNotes(true)));
        addRenderableWidget(notesRow.add(0, Lang.get("gui.kve.pack_tools.notes_copy"), b -> exportNotes(false)));

        y += 34;
        KveScaledEditBox label = new KveScaledEditBox(font, x, y, innerW - 124, 14, Component.literal(Lang.get("gui.kve.pack_tools.snap_label_hint")));
        label.setHint(Component.literal(Lang.get("gui.kve.pack_tools.snap_label_hint")));
        label.setMaxLength(40);
        label.setValue(snapLabel);
        label.setResponder(s -> snapLabel = s);
        addRenderableWidget(label);
        addRenderableWidget(new KvePlateButton(x + innerW - 120, y, 120, 14, Component.literal(Lang.get("gui.kve.pack_tools.snap_create")), b -> createSnapshot()));

        int by = cy + H - KveGuiStyle.PAD - 16;
        KveGuiStyle.ButtonRow row = new KveGuiStyle.ButtonRow(x, by, x + innerW, true);
        addRenderableWidget(row.add(0, Lang.get("gui.kve.pack_tools.snap_compare"), b -> compareSelected()));
        addRenderableWidget(row.add(0, Lang.get("gui.kve.pack_tools.snap_restore"), b -> confirmRestore()));
        addRenderableWidget(row.add(0, Lang.get("gui.kve.pack_tools.snap_delete"), b -> deleteSelected()));
        addRenderableWidget(row.add(0, Lang.get("gui.kve.pack_tools.snap_folder"), b -> openFolder()));
        addRenderableWidget(row.add(0, Lang.get("gui.kve.btn.close"), b -> onClose()));
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    private int snapListTop() { return cy + KveGuiStyle.TITLEBAR_H + 16 + 18 + 34 + 18 + 4; }

    private void exportNotes(boolean toFile) {
        Map<String, List<ReleaseNotesBuilder.Line>> byLog = new LinkedHashMap<>();
        for (String logId : ReleaseNotesBuilder.SECTION_TITLES.keySet()) {
            List<ReleaseNotesBuilder.Line> lines = new ArrayList<>();
            for (KveChangeLog.Entry e : KveChangeLog.snapshot(logId)) {
                lines.add(new ReleaseNotesBuilder.Line(e.timestamp(), e.action().name(), e.entryId()));
            }
            byLog.put(logId, lines);
        }
        int d = RANGES_DAYS[rangeIdx];
        long since = d == 0 ? 0 : System.currentTimeMillis() - d * DAY;
        String md = ReleaseNotesBuilder.build(byLog, since, ZoneId.systemDefault(),
                notesTitle.isBlank() ? Lang.get("gui.kve.pack_tools.notes_default_title") : notesTitle);
        if (toFile) {
            try {
                Files.writeString(notesFile(), md, StandardCharsets.UTF_8);
                setStatus(Lang.get("gui.kve.pack_tools.notes_saved", notesFile().toAbsolutePath().toString()), KveGuiStyle.TEXT_OK);
            } catch (Exception ex) {
                setStatus(ex.getMessage(), KveGuiStyle.TEXT_ERROR);
            }
        } else {
            minecraft.keyboardHandler.setClipboard(md);
            setStatus(Lang.get("gui.kve.pack_tools.notes_copied"), KveGuiStyle.TEXT_OK);
        }
    }

    private void createSnapshot() {
        try {
            Path zip = PackSnapshotManager.create(PackSnapshotManager.gameDir(), snapLabel);
            snapLabel = "";
            selected = 0;
            scroll = 0;
            rebuild();
            setStatus(Lang.get("gui.kve.pack_tools.snap_created", zip.getFileName().toString()), KveGuiStyle.TEXT_OK);
        } catch (Exception ex) {
            CraftEditorMod.LOGGER.error("[Shovel JS] Snapshot failed: {}", ex.getMessage());
            setStatus(ex.getMessage(), KveGuiStyle.TEXT_ERROR);
        }
    }

    private void confirmRestore() {
        if (selected < 0) { setStatus(Lang.get("gui.kve.pack_tools.snap_pick"), KveGuiStyle.TEXT_WARN); return; }
        if (!ClientPermissionState.canApply()) { setStatus(Lang.get("gui.kve.perm.denied"), KveGuiStyle.TEXT_ERROR); return; }
        if (!com.kve.client.gui.workspace.pending.PendingChangeManager.isEmpty()) {
            setStatus(Lang.get("gui.kve.pack_tools.snap_restore_pending"), KveGuiStyle.TEXT_WARN);
            return;
        }
        String name = snaps.get(selected).fileName();
        int files = PackSnapshotManager.countEntries(PackSnapshotManager.gameDir(), name);
        minecraft.setScreen(new KveConfirmScreen(ok -> {
            minecraft.setScreen(this);
            if (!ok) return;
            try {
                int n = PackSnapshotManager.restore(PackSnapshotManager.gameDir(), name);
                com.kve.common.persist.CraftEditorState.forceReload();
                rebuild();
                setStatus(Lang.get("gui.kve.pack_tools.snap_restored", n), KveGuiStyle.ACCENT_GOLD);
            } catch (Exception ex) {
                setStatus(ex.getMessage(), KveGuiStyle.TEXT_ERROR);
            }
        }, Component.literal(Lang.get("gui.kve.pack_tools.snap_restore_title")),
                Component.literal(Lang.get("gui.kve.pack_tools.snap_restore_msg", name, files))));
    }

    private void compareSelected() {
        if (selected < 0) { setStatus(Lang.get("gui.kve.pack_tools.snap_pick"), KveGuiStyle.TEXT_WARN); return; }
        String name = snaps.get(selected).fileName();
        try {
            PackSnapshotManager.Diff d = PackSnapshotManager.compare(PackSnapshotManager.gameDir(), name);
            StringBuilder sb = new StringBuilder();
            sb.append("// ").append(Lang.get("gui.kve.pack_tools.snap_compare_header", name)).append('\n');
            if (d.isEmpty()) sb.append("// ").append(Lang.get("gui.kve.pack_tools.snap_compare_same")).append('\n');
            sb.append('\n');
            for (String f : d.added()) sb.append("+ ").append(f).append('\n');
            for (String f : d.changed()) sb.append("~ ").append(f).append('\n');
            for (String f : d.removed()) sb.append("- ").append(f).append('\n');
            sb.append('\n').append("// ").append(Lang.get("gui.kve.pack_tools.snap_compare_legend", d.added().size(), d.changed().size(), d.removed().size()));
            minecraft.setScreen(new RecipePreviewScreen(this, Lang.get("gui.kve.pack_tools.snap_compare"), sb.toString()));
        } catch (Exception ex) {
            setStatus(ex.getMessage(), KveGuiStyle.TEXT_ERROR);
        }
    }

    private void deleteSelected() {
        if (selected < 0) { setStatus(Lang.get("gui.kve.pack_tools.snap_pick"), KveGuiStyle.TEXT_WARN); return; }
        String name = snaps.get(selected).fileName();
        minecraft.setScreen(new KveConfirmScreen(ok -> {
            minecraft.setScreen(this);
            if (!ok) return;
            try {
                PackSnapshotManager.delete(PackSnapshotManager.gameDir(), name);
                selected = -1;
                rebuild();
                setStatus(Lang.get("gui.kve.pack_tools.snap_deleted", name), KveGuiStyle.TEXT_MUTED);
            } catch (Exception ex) {
                setStatus(ex.getMessage(), KveGuiStyle.TEXT_ERROR);
            }
        }, Component.literal(Lang.get("gui.kve.pack_tools.snap_delete")), Component.literal(name)));
    }

    private void openFolder() {
        try {
            Path dir = PackSnapshotManager.dir(PackSnapshotManager.gameDir());
            Files.createDirectories(dir);
            Util.getPlatform().openFile(dir.toFile());
        } catch (Exception ex) {
            setStatus(ex.getMessage(), KveGuiStyle.TEXT_ERROR);
        }
    }

    private void setStatus(String s, int color) {
        status = s;
        statusColor = color;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, cx, cy, W, H, getTitle().getString());
        int x = cx + KveGuiStyle.PAD;
        int innerW = W - KveGuiStyle.PAD * 2;
        int y = cy + KveGuiStyle.TITLEBAR_H + 5;
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.pack_tools.notes_header"), x, y, innerW, KveGuiStyle.ACCENT_GOLD);
        y += 11 + 18 + 18 + 5;
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.pack_tools.snap_header"), x, y, innerW, KveGuiStyle.ACCENT_GOLD);

        int ly = snapListTop();
        int rowH = 12;
        g.fill(x - 2, ly - 1, x + innerW, ly + SNAP_ROWS * rowH + 1, KveGuiStyle.BG_FIELD);
        if (snaps.isEmpty()) KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.pack_tools.snap_none"), x, ly + 2, innerW, KveGuiStyle.TEXT_MUTED);
        for (int i = 0; i < SNAP_ROWS; i++) {
            int idx = i + scroll;
            if (idx >= snaps.size()) break;
            PackSnapshotManager.Info s = snaps.get(idx);
            int ry = ly + i * rowH;
            boolean hov = mx >= x && mx < x + innerW && my >= ry && my < ry + rowH;
            if (idx == selected) g.fill(x - 2, ry, x + innerW, ry + rowH, KveGuiStyle.BORDER_FIELD);
            else if (hov) g.fill(x - 2, ry, x + innerW, ry + rowH, 0x22FFFFFF);
            String size = s.sizeBytes() >= 1024 * 1024 ? String.format(Locale.ROOT, "%.1f MB", s.sizeBytes() / 1048576.0)
                    : String.format(Locale.ROOT, "%d KB", Math.max(1, s.sizeBytes() / 1024));
            KveGuiStyle.drawTextSafe(g, s.fileName(), x + 2, ry + 2, innerW - 130, KveGuiStyle.TEXT_PRIMARY);
            KveGuiStyle.drawTextSafe(g, KveGuiStyle.formatDate("yyyy-MM-dd HH:mm", s.modifiedMs()) + "  " + size, x + innerW - 124, ry + 2, 124, KveGuiStyle.TEXT_MUTED);
        }
        KveGuiStyle.drawTextSafe(g, status, x, ly + SNAP_ROWS * rowH + 5, innerW, statusColor);
        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int x = cx + KveGuiStyle.PAD;
        int innerW = W - KveGuiStyle.PAD * 2;
        int ly = snapListTop();
        if (mx >= x && mx < x + innerW && my >= ly && my < ly + SNAP_ROWS * 12) {
            int idx = (int) ((my - ly) / 12) + scroll;
            if (idx < snaps.size()) { selected = idx; return true; }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        scroll = Math.max(0, Math.min(Math.max(0, snaps.size() - SNAP_ROWS), scroll + (delta > 0 ? -1 : 1)));
        return true;
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
