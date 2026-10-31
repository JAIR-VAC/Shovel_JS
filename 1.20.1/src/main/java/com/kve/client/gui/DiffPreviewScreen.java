package com.kve.client.gui;

import com.kve.common.Lang;
import com.kve.client.gui.workspace.pending.PendingChange;
import com.kve.client.gui.workspace.pending.PendingDomain;
import com.kve.common.diff.TextDiff;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows, for every queued
 * {@link PendingChange} in one domain that has diff suppliers attached (see
 * {@link PendingChange#withDiff}), the old-vs-new script line by line
 * (green {@code +} added, red {@code -} removed, muted context) before the
 * Apply actually writes anything to disk. Entries without diff suppliers
 * (older call sites that haven't wired {@link PendingChange#withDiff} up
 * yet) are skipped here, not shown as an empty/broken section - a screen
 * should only open this when at least one queued change has a diff (see
 * {@code CraftEditorScreen#onApplyPending} for the "hasDiff() anywhere in
 * the domain" check).
 * <p>
 * Read-only: this never writes anything itself. Pressing "Aplicar" runs the
 * {@code onConfirmApply} callback the caller supplied (normally
 * {@code PendingChangeManager.applyDomain(...)} then the same result
 * flash/refresh the screen would've done directly); "Cancelar" just goes
 * back without touching the queue, same as closing any other preview.
 */
@OnlyIn(Dist.CLIENT)
public class DiffPreviewScreen extends Screen {

    private record Row(String text, int color, PendingChange editTarget) {
        Row(String text, int color) { this(text, color, null); }
    }

    private static final int GUI_W = 480;
    private static final int GUI_H = 260;
    private static final int ROW_H = 10;
    private static final int ROWS_VISIBLE = 16;

    private final Screen parent;
    private final List<PendingChange> changes;
    private final Runnable onConfirmApply;

    private final List<Row> rows = new ArrayList<>();
    private ScrollBar scrollBar;
    private int L, T;
    private int addedTotal, removedTotal;

    private boolean contentDragging = false;
    private double  dragAccumPx     = 0;

    private PendingChange editing = null;
    private final List<String> editLines = new ArrayList<>();
    private int editCursorLine = 0, editCursorCol = 0;
    private ScrollBar editScroll;

    public DiffPreviewScreen(Screen parent, List<PendingChange> changes, Runnable onConfirmApply) {
        super(Lang.c("gui.kve.diff.title"));
        this.parent = parent;
        this.changes = changes;
        this.onConfirmApply = onConfirmApply;
    }

    @Override
    protected void init() {
        L = (width  - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        clearWidgets();
        setFocused(null);
        buildRows();

        scrollBar = new ScrollBar(L + GUI_W - 10, T + 24, ROWS_VISIBLE * ROW_H, rows.size(), ROWS_VISIBLE);

        int by = T + GUI_H - 20;
        addRenderableWidget(new KvePlateButton(L + 6, by, 90, 16, Lang.c("gui.kve.diff.apply"), b -> doApply()));
        addRenderableWidget(new KvePlateButton(L + GUI_W - 66, by, 60, 16, Lang.c("gui.kve.btn.cancel"), b -> minecraft.setScreen(parent)));
    }

    private void buildRows() {
        rows.clear();
        addedTotal = 0; removedTotal = 0;

        java.util.LinkedHashMap<PendingDomain, List<PendingChange>> byDomain = new java.util.LinkedHashMap<>();
        for (PendingChange c : changes) {
            if (!c.hasDiff()) continue;
            byDomain.computeIfAbsent(c.domain(), d -> new ArrayList<>()).add(c);
        }
        boolean multiDomain = byDomain.size() > 1;

        for (var domainEntry : byDomain.entrySet()) {
            if (multiDomain) {
                rows.add(new Row("== " + domainEntry.getKey().name() + " ==", KveGuiStyle.ACCENT_PURPLE));
            }
            java.util.LinkedHashMap<String, List<PendingChange>> byFile = new java.util.LinkedHashMap<>();
            for (PendingChange c : domainEntry.getValue()) {
                String key = c.file() != null ? c.file() : "\u0000" + c.entryId();
                byFile.computeIfAbsent(key, k -> new ArrayList<>()).add(c);
            }
            for (var fileEntry : byFile.entrySet()) {
                boolean realFile = !fileEntry.getKey().startsWith("\u0000");
                if (realFile && fileEntry.getValue().size() > 1) {
                    rows.add(new Row("  \u2192 " + fileEntry.getKey(), KveGuiStyle.TEXT_LABEL));
                }
                for (PendingChange c : fileEntry.getValue()) buildEntryRows(c);
            }
        }
        if (rows.isEmpty()) {
            rows.add(new Row(Lang.get("gui.kve.diff.nothing_to_show"), KveGuiStyle.TEXT_MUTED));
        }
    }

    private void buildEntryRows(PendingChange c) {
        String oldScript = c.oldScript() == null ? null : c.oldScript().get();
        String newScript = c.effectiveNewScript();
        List<TextDiff.DiffLine> diff = TextDiff.diffLines(oldScript, newScript);
        int[] counts = TextDiff.countChanges(diff);
        addedTotal += counts[0];
        removedTotal += counts[1];

        String editedTag = c.hasOverride() ? " " + Lang.get("gui.kve.diff.edited") : "";
        rows.add(new Row(c.entryId() + "  (+" + counts[0] + " -" + counts[1] + ")" + editedTag, KveGuiStyle.ACCENT_GOLD));
        if (diff.isEmpty() || TextDiff.equalLines(oldScript, newScript)) {
            rows.add(new Row("  " + Lang.get("gui.kve.diff.no_change"), KveGuiStyle.TEXT_MUTED));
        } else {
            for (TextDiff.DiffLine l : diff) {
                int color = switch (l.type()) {
                    case ADDED -> KveGuiStyle.TEXT_OK;
                    case REMOVED -> KveGuiStyle.TEXT_ERROR;
                    case EQUAL -> KveGuiStyle.TEXT_MUTED;
                };
                String prefix = switch (l.type()) {
                    case ADDED -> "+ ";
                    case REMOVED -> "- ";
                    case EQUAL -> "  ";
                };
                rows.add(new Row(prefix + l.text(), color));
            }
        }
        if (c.action() != PendingChange.Action.DELETE && c.newScript() != null) {
            rows.add(new Row("  [" + Lang.get("gui.kve.diff.edit_btn") + "]", KveGuiStyle.TEXT_LABEL, c));
        }
        rows.add(new Row("", KveGuiStyle.TEXT_MUTED));
    }

    private void openEditor(PendingChange c) {
        editing = c;
        editLines.clear();
        String text = c.effectiveNewScript();
        if (text == null) text = "";
        for (String ln : text.split("\n", -1)) editLines.add(ln);
        if (editLines.isEmpty()) editLines.add("");
        editCursorLine = 0;
        editCursorCol = 0;
        editScroll = new ScrollBar(L + GUI_W - 10, T + 24, ROWS_VISIBLE * ROW_H, editLines.size(), ROWS_VISIBLE);
        clearWidgets();
        setFocused(null);
        int by = T + GUI_H - 20;
        addRenderableWidget(new KvePlateButton(L + 6, by, 90, 16, Lang.c("gui.kve.diff.edit_save"), b -> closeEditor(true)));
        addRenderableWidget(new KvePlateButton(L + GUI_W - 66, by, 60, 16, Lang.c("gui.kve.btn.cancel"), b -> closeEditor(false)));
    }

    private void closeEditor(boolean save) {
        if (save && editing != null) {
            editing.textOverride().set(String.join("\n", editLines));
        }
        editing = null;
        init();
    }

    private void doApply() {
        if (onConfirmApply != null) onConfirmApply.run();
        minecraft.setScreen(parent);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        if (editing != null) { renderEditor(g); super.render(g, mx, my, pt); return; }

        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, Lang.get("gui.kve.diff.title")
                + " (+" + addedTotal + " -" + removedTotal + ")");

        int listX = L + 6;
        int listY = T + 24;
        int listW = GUI_W - 20;
        int listH = ROWS_VISIBLE * ROW_H;
        g.fill(listX, listY, listX + listW, listY + listH, KveGuiStyle.BG_FIELD);

        int first = scrollBar == null ? 0 : scrollBar.getScroll();
        for (int i = 0; i < ROWS_VISIBLE; i++) {
            int idx = first + i;
            if (idx >= rows.size()) break;
            Row row = rows.get(idx);
            boolean hov = row.editTarget() != null && mx >= listX && mx < listX + listW
                    && my >= listY + i * ROW_H && my < listY + (i + 1) * ROW_H;
            KveGuiStyle.drawTextSafe(g, row.text(), listX + 2, listY + i * ROW_H + 1, listW - 4, hov ? KveGuiStyle.ACCENT_GOLD : row.color());
        }

        if (scrollBar != null) scrollBar.render(g);

        super.render(g, mx, my, pt);
    }

    private void renderEditor(GuiGraphics g) {
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, Lang.get("gui.kve.diff.edit_title", editing.entryId()));
        int listX = L + 6, listY = T + 24, listW = GUI_W - 20, listH = ROWS_VISIBLE * ROW_H;
        g.fill(listX, listY, listX + listW, listY + listH, KveGuiStyle.BG_FIELD);
        int first = editScroll == null ? 0 : editScroll.getScroll();
        for (int i = 0; i < ROWS_VISIBLE; i++) {
            int idx = first + i;
            if (idx >= editLines.size()) break;
            String line = editLines.get(idx);
            KveGuiStyle.drawTextSafe(g, line, listX + 2, listY + i * ROW_H + 1, listW - 4, KveGuiStyle.TEXT_PRIMARY);
            if (idx == editCursorLine && (System.currentTimeMillis() / 400) % 2 == 0) {
                int cx = listX + 2 + font.width(line.substring(0, Math.min(editCursorCol, line.length())));
                g.fill(cx, listY + i * ROW_H, cx + 1, listY + i * ROW_H + ROW_H - 1, KveGuiStyle.TEXT_PRIMARY);
            }
        }
        if (editScroll != null) editScroll.render(g);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (editing != null) { if (editScroll != null) editScroll.mouseScrolled(delta); return true; }
        if (scrollBar != null && scrollBar.mouseScrolled(delta)) return true;
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (editing != null) {
            if (editScroll != null && editScroll.mouseClicked(mx, my, button)) return true;
            return super.mouseClicked(mx, my, button);
        }
        if (scrollBar != null && scrollBar.mouseClicked(mx, my, button)) return true;
        if (button == 0 && isOverList(mx, my)) {
            int listY = T + 24;
            int first = scrollBar == null ? 0 : scrollBar.getScroll();
            int row = (int) ((my - listY) / ROW_H) + first;
            if (row >= 0 && row < rows.size() && rows.get(row).editTarget() != null) {
                openEditor(rows.get(row).editTarget());
                return true;
            }
            contentDragging = true;
            dragAccumPx = 0;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private boolean isOverList(double mx, double my) {
        int listX = L + 6, listY = T + 24, listW = GUI_W - 20, listH = ROWS_VISIBLE * ROW_H;
        return mx >= listX && mx < listX + listW && my >= listY && my < listY + listH;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (scrollBar != null && scrollBar.mouseDragged(mx, my, button, dx, dy)) return true;
        if (contentDragging && scrollBar != null) {
            dragAccumPx += dy;
            while (dragAccumPx >= ROW_H) { scrollBar.scrollBy(1);  dragAccumPx -= ROW_H; }
            while (dragAccumPx <= -ROW_H) { scrollBar.scrollBy(-1); dragAccumPx += ROW_H; }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        contentDragging = false;
        dragAccumPx = 0;
        if (scrollBar != null) scrollBar.mouseReleased(mx, my, button);
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean charTyped(char ch, int mods) {
        if (editing == null) return super.charTyped(ch, mods);
        String line = editLines.get(editCursorLine);
        editLines.set(editCursorLine, line.substring(0, editCursorCol) + ch + line.substring(editCursorCol));
        editCursorCol++;
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (editing == null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
            return super.keyPressed(key, scan, mods);
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) { closeEditor(false); return true; }
        String line = editLines.get(editCursorLine);
        switch (key) {
            case GLFW.GLFW_KEY_LEFT -> {
                if (editCursorCol > 0) editCursorCol--;
                else if (editCursorLine > 0) { editCursorLine--; editCursorCol = editLines.get(editCursorLine).length(); }
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (editCursorCol < line.length()) editCursorCol++;
                else if (editCursorLine < editLines.size() - 1) { editCursorLine++; editCursorCol = 0; }
                return true;
            }
            case GLFW.GLFW_KEY_UP -> { if (editCursorLine > 0) { editCursorLine--; editCursorCol = Math.min(editCursorCol, editLines.get(editCursorLine).length()); } return true; }
            case GLFW.GLFW_KEY_DOWN -> { if (editCursorLine < editLines.size() - 1) { editCursorLine++; editCursorCol = Math.min(editCursorCol, editLines.get(editCursorLine).length()); } return true; }
            case GLFW.GLFW_KEY_HOME -> { editCursorCol = 0; return true; }
            case GLFW.GLFW_KEY_END -> { editCursorCol = line.length(); return true; }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (editCursorCol > 0) {
                    editLines.set(editCursorLine, line.substring(0, editCursorCol - 1) + line.substring(editCursorCol));
                    editCursorCol--;
                } else if (editCursorLine > 0) {
                    String prev = editLines.get(editCursorLine - 1);
                    int newCol = prev.length();
                    editLines.set(editCursorLine - 1, prev + line);
                    editLines.remove(editCursorLine);
                    editCursorLine--;
                    editCursorCol = newCol;
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (editCursorCol < line.length()) {
                    editLines.set(editCursorLine, line.substring(0, editCursorCol) + line.substring(editCursorCol + 1));
                } else if (editCursorLine < editLines.size() - 1) {
                    editLines.set(editCursorLine, line + editLines.get(editCursorLine + 1));
                    editLines.remove(editCursorLine + 1);
                }
                return true;
            }
            case GLFW.GLFW_KEY_ENTER -> {
                String tail = line.substring(editCursorCol);
                editLines.set(editCursorLine, line.substring(0, editCursorCol));
                editLines.add(editCursorLine + 1, tail);
                editCursorLine++;
                editCursorCol = 0;
                return true;
            }
            case GLFW.GLFW_KEY_TAB -> {
                editLines.set(editCursorLine, line.substring(0, editCursorCol) + "  " + line.substring(editCursorCol));
                editCursorCol += 2;
                return true;
            }
            default -> { return super.keyPressed(key, scan, mods); }
        }
    }

    @Override public boolean isPauseScreen() { return false; }
}
