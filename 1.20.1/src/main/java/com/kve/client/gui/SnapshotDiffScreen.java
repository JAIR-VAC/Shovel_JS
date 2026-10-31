package com.kve.client.gui;

import com.kve.client.permission.ClientPermissionState;
import com.kve.common.Lang;
import com.kve.common.diff.TextDiff;
import com.kve.common.persist.PackSnapshotManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class SnapshotDiffScreen extends Screen {

    private static final int W = 520;
    private static final int H = 290;
    private static final int ROW_H = 11;
    private static final int LIST_W = 200;
    private static final int CONTEXT = 2;

    private record FileRow(String path, char kind) {}

    private final Screen parent;
    private final java.nio.file.Path game = PackSnapshotManager.gameDir();
    private String from, to;
    private boolean fromSet = false, toSet = true;
    private final List<FileRow> files = new ArrayList<>();
    private final List<TextDiff.DiffLine> diff = new ArrayList<>();
    private int selected = -1, fileScroll = 0, diffScroll = 0;
    private String message = "";
    private int cx, cy;

    public SnapshotDiffScreen(Screen parent) {
        super(Component.literal(Lang.get("gui.kve.snapdiff.title")));
        this.parent = parent;
        List<PackSnapshotManager.Info> list = PackSnapshotManager.list(game);
        if (!list.isEmpty()) { from = list.get(0).fileName(); fromSet = true; }
        to = null; toSet = true;
        recompute();
    }

    private String label(String point) {
        return point == null ? Lang.get("gui.kve.snapdiff.current") : point.replaceFirst("\\.zip$", "");
    }

    private void recompute() {
        files.clear();
        diff.clear();
        selected = -1;
        fileScroll = diffScroll = 0;
        if (!fromSet) { message = Lang.get("gui.kve.snapdiff.none"); return; }
        if (java.util.Objects.equals(from, to)) { message = Lang.get("gui.kve.snapdiff.same_point"); return; }
        try {
            PackSnapshotManager.Diff d = PackSnapshotManager.compareSnapshots(game, from, to);
            for (String f : d.changed()) files.add(new FileRow(f, '~'));
            for (String f : d.added()) files.add(new FileRow(f, '+'));
            for (String f : d.removed()) files.add(new FileRow(f, '-'));
            message = d.isEmpty() ? Lang.get("gui.kve.snapdiff.no_changes")
                    : Lang.get("gui.kve.snapdiff.summary", d.changed().size(), d.added().size(), d.removed().size());
        } catch (Exception e) {
            message = Lang.get("gui.kve.report.failed", e.getMessage());
        }
    }

    private void select(int i) {
        selected = i;
        diff.clear();
        diffScroll = 0;
        try {
            String path = files.get(i).path();
            String a = PackSnapshotManager.readText(game, from, path);
            String b = PackSnapshotManager.readText(game, to, path);
            List<TextDiff.DiffLine> all = TextDiff.diffLines(a == null ? "" : a, b == null ? "" : b);
            boolean[] keep = new boolean[all.size()];
            for (int k = 0; k < all.size(); k++) {
                if (all.get(k).type() == TextDiff.LineType.EQUAL) continue;
                for (int c = Math.max(0, k - CONTEXT); c <= Math.min(all.size() - 1, k + CONTEXT); c++) keep[c] = true;
            }
            boolean gap = false;
            for (int k = 0; k < all.size(); k++) {
                if (keep[k]) { diff.add(all.get(k)); gap = false; }
                else if (!gap) { diff.add(new TextDiff.DiffLine(TextDiff.LineType.EQUAL, "...")); gap = true; }
            }
        } catch (Exception e) {
            diff.add(new TextDiff.DiffLine(TextDiff.LineType.REMOVED, String.valueOf(e.getMessage())));
        }
    }

    @Override
    protected void init() {
        cx = (width - W) / 2;
        cy = (height - H) / 2;
        int y = cy + KveGuiStyle.TITLEBAR_H + 4;
        int half = (W - KveGuiStyle.PAD * 2 - 6) / 2;
        addRenderableWidget(new KvePlateButton(cx + KveGuiStyle.PAD, y, half, 14,
                Component.literal(Lang.get("gui.kve.snapdiff.from", label(from))),
                b -> minecraft.setScreen(new SnapshotPickerScreen(this, Lang.get("gui.kve.snapdiff.pick_from"), true, pick -> {
                    from = pick; fromSet = true; recompute(); minecraft.setScreen(this);
                }))));
        addRenderableWidget(new KvePlateButton(cx + KveGuiStyle.PAD + half + 6, y, half, 14,
                Component.literal(Lang.get("gui.kve.snapdiff.to", label(to))),
                b -> minecraft.setScreen(new SnapshotPickerScreen(this, Lang.get("gui.kve.snapdiff.pick_to"), true, pick -> {
                    to = pick; recompute(); minecraft.setScreen(this);
                }))));

        int by = cy + H - KveGuiStyle.PAD - 16;
        int x = cx + KveGuiStyle.PAD;
        addRenderableWidget(new KvePlateButton(x + W - KveGuiStyle.PAD * 2 - 260, by, 130, 14,
                Lang.c("gui.kve.snapdiff.restore_file"), b -> confirmRestoreFile()));
        addRenderableWidget(new KvePlateButton(cx + W - KveGuiStyle.PAD - 60, by, 60, 14,
                Lang.c("gui.kve.btn.close"), b -> onClose()));
    }

    private void confirmRestoreFile() {
        if (selected < 0) { message = Lang.get("gui.kve.snapdiff.pick_file"); return; }
        if (from == null) { message = Lang.get("gui.kve.snapdiff.restore_needs_snapshot"); return; }
        if (!ClientPermissionState.canEdit()) { message = Lang.get("gui.kve.perm.denied"); return; }
        FileRow f = files.get(selected);
        minecraft.setScreen(new KveConfirmScreen(ok -> {
            minecraft.setScreen(this);
            if (!ok) return;
            try {
                boolean done = PackSnapshotManager.restoreFile(game, from, f.path());
                message = done ? Lang.get("gui.kve.snapdiff.restored", f.path())
                        : Lang.get("gui.kve.snapdiff.restore_missing", f.path());
                recompute();
            } catch (Exception e) {
                message = Lang.get("gui.kve.report.failed", e.getMessage());
            }
        }, Component.literal(Lang.get("gui.kve.snapdiff.restore_title")),
                Component.literal(Lang.get("gui.kve.snapdiff.restore_msg", f.path(), label(from)))));
    }

    private int listTop() { return cy + KveGuiStyle.TITLEBAR_H + 34; }

    private int rows() { return (cy + H - KveGuiStyle.PAD - 22 - listTop()) / ROW_H; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        KveGuiStyle.drawWindow(g, cx, cy, W, H, getTitle().getString());
        int x = cx + KveGuiStyle.PAD;
        KveGuiStyle.drawTextSafe(g, message, x, cy + KveGuiStyle.TITLEBAR_H + 22, W - KveGuiStyle.PAD * 2, KveGuiStyle.TEXT_LABEL);
        int ly = listTop(), rows = rows();
        g.fill(x - 2, ly - 1, x + LIST_W, ly + rows * ROW_H + 1, KveGuiStyle.BG_FIELD);
        for (int i = 0; i < rows && i + fileScroll < files.size(); i++) {
            FileRow f = files.get(i + fileScroll);
            int ry = ly + i * ROW_H;
            if (i + fileScroll == selected) g.fill(x - 2, ry, x + LIST_W, ry + ROW_H, KveGuiStyle.selectionBg());
            int color = f.kind() == '+' ? KveGuiStyle.TEXT_OK : f.kind() == '-' ? KveGuiStyle.TEXT_ERROR : KveGuiStyle.ACCENT_GOLD;
            String name = f.path().substring(f.path().lastIndexOf('/') + 1);
            String dir = f.path().contains("/") ? f.path().substring(0, f.path().lastIndexOf('/')) : "";
            KveGuiStyle.drawTextSafe(g, f.kind() + " " + name + "  " + dir.replace("kubejs/", ""), x, ry + 2, LIST_W - 4, color);
        }
        int dx = x + LIST_W + 8, dw = cx + W - KveGuiStyle.PAD - dx;
        g.fill(dx - 2, ly - 1, dx + dw, ly + rows * ROW_H + 1, KveGuiStyle.BG_FIELD);
        if (selected < 0) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.snapdiff.pick_file"), dx, ly + 2, dw, KveGuiStyle.TEXT_MUTED);
        }
        for (int i = 0; i < rows && i + diffScroll < diff.size(); i++) {
            TextDiff.DiffLine l = diff.get(i + diffScroll);
            int ry = ly + i * ROW_H;
            String prefix = switch (l.type()) { case ADDED -> "+ "; case REMOVED -> "- "; default -> "  "; };
            int color = switch (l.type()) { case ADDED -> KveGuiStyle.TEXT_OK; case REMOVED -> KveGuiStyle.TEXT_ERROR; default -> KveGuiStyle.TEXT_MUTED; };
            if (l.type() != TextDiff.LineType.EQUAL) g.fill(dx - 2, ry, dx + dw, ry + ROW_H, (color & 0x00FFFFFF) | 0x22000000);
            KveGuiStyle.drawTextSafe(g, prefix + l.text().replace("\t", "  "), dx, ry + 2, dw - 2, color);
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int x = cx + KveGuiStyle.PAD, ly = listTop();
        if (btn == 0 && mx >= x && mx < x + LIST_W && my >= ly && my < ly + rows() * ROW_H) {
            int i = (int) ((my - ly) / ROW_H) + fileScroll;
            if (i < files.size()) { select(i); return true; }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int step = delta > 0 ? -3 : 3;
        if (mx < cx + KveGuiStyle.PAD + LIST_W) fileScroll = Math.max(0, Math.min(Math.max(0, files.size() - rows()), fileScroll + step));
        else diffScroll = Math.max(0, Math.min(Math.max(0, diff.size() - rows()), diffScroll + step));
        return true;
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
