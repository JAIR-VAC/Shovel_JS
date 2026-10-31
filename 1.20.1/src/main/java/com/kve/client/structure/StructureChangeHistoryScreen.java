package com.kve.client.structure;

import com.kve.common.structure.StructureVoxelGrid;

import com.kve.common.Lang;
import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.ResizeHandle;
import com.kve.client.gui.ScrollBar;
import com.kve.client.gui.workspace.pending.PendingChange;
import net.minecraft.client.gui.GuiGraphics;
import com.kve.client.gui.KvePlateButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class StructureChangeHistoryScreen extends Screen {

    private static final int GUI_W = 380;
    private static final int GUI_H = 210;
    private static final int ROW_H = 12;
    private static final int ROWS_VISIBLE = 14;

    private static final int LIST_W_DEFAULT = 230;
    private static final int LIST_W_MIN     = 150;
    private static final int LIST_W_MAX     = 260;

    private final Screen parent;
    private final List<StructureChangeLog.GroupedEntry> groupedEntries;
    private List<StructureChangeLog.Entry> detailEntries = null;
    private String detailStructureId = null;
    private final Consumer<String> onContinueEditing;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.ROOT);
    private final StructurePreviewCache previewCache = new StructurePreviewCache();

    private int L, T, LIST_W;
    private ScrollBar scrollBar;
    private ResizeHandle listResize;

    private String previewId = null;
    private int pvX, pvY, pvW, pvH;
    private OrbitCamera previewCamera;

    /**
     * @param onContinueEditing called with a row's structure id when the
     *                           player clicks it, wanting to resume editing
     *                           that structure; this screen closes itself
     *                           right after invoking it.
     */
    public StructureChangeHistoryScreen(Screen parent, Consumer<String> onContinueEditing) {
        super(Lang.c("gui.kve.structure.change_history.title"));
        this.parent = parent;
        this.onContinueEditing = onContinueEditing;
        this.groupedEntries = StructureChangeLog.snapshotGrouped();
    }

    private int currentRowCount() {
        return detailEntries != null ? detailEntries.size() : groupedEntries.size();
    }

    private void enterDetail(String id) {
        detailStructureId = id;
        detailEntries = StructureChangeLog.snapshotForStructure(id);
        previewId = id;
        init();
    }

    private void exitDetail() {
        detailStructureId = null;
        detailEntries = null;
        previewId = groupedEntries.isEmpty() ? null : groupedEntries.get(0).structureId();
        init();
    }

    @Override
    protected void init() {
        L = (width - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        clearWidgets();

        if (previewCamera == null) {
            previewCamera = new OrbitCamera(10f, 2f, 60f, 60f);
        }
        if (listResize == null) {
            listResize = new ResizeHandle("structure_change_history.list", LIST_W_MIN, LIST_W_MAX, LIST_W_DEFAULT);
        }
        LIST_W = listResize.width();
        listResize.reposition(L, T + 15, T + GUI_H);

        scrollBar = new ScrollBar(L + LIST_W - 8, T + 26, ROWS_VISIBLE * ROW_H, currentRowCount(), ROWS_VISIBLE);

        if (detailEntries != null) {
            int halfW = (LIST_W - 12 - 4) / 2;
            addRenderableWidget(new KvePlateButton(L + 6, T + GUI_H - 16, halfW, 12, Lang.c("gui.kve.btn.back"), b -> exitDetail()));
            addRenderableWidget(new KvePlateButton(L + 6 + halfW + 4, T + GUI_H - 16, halfW, 12, Lang.c("gui.kve.btn.close"), b -> onClose()));
        } else {
            addRenderableWidget(new KvePlateButton(L + 6, T + GUI_H - 16, LIST_W - 12, 12, Lang.c("gui.kve.btn.close"), b -> onClose()));
        }

        pvX = L + LIST_W + 8;
        pvY = T + 16;
        pvW = GUI_W - LIST_W - 16;
        pvH = GUI_H - 32;

        if (previewId == null) {
            if (detailEntries != null && !detailEntries.isEmpty()) previewId = detailStructureId;
            else if (!groupedEntries.isEmpty()) previewId = groupedEntries.get(0).structureId();
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        String title = detailStructureId != null
                ? Lang.get("gui.kve.structure.change_history.detail_title", detailStructureId)
                : Lang.get("gui.kve.structure.change_history.title");
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, title);

        boolean empty = detailEntries != null ? detailEntries.isEmpty() : groupedEntries.isEmpty();
        String hoveredFullLabel = null;
        if (empty) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.structure.change_history.empty"),
                    L + 6, T + 20, LIST_W - 12, KveGuiStyle.TEXT_MUTED);
        } else {
            KveGuiStyle.drawTextSafe(g, Lang.get(detailEntries != null
                            ? "gui.kve.structure.change_history.hint"
                            : "gui.kve.structure.change_history.hint_grouped"),
                    L + 6, T + 16, LIST_W - 16, KveGuiStyle.TEXT_MUTED);
            int scroll = scrollBar.getScroll();
            int visible = Math.min(ROWS_VISIBLE, Math.max(0, currentRowCount() - scroll));
            int idLabelMaxW = LIST_W - 100;
            for (int i = 0; i < visible; i++) {
                int ry = T + 26 + i * ROW_H;
                boolean hoveredRow = KveGuiStyle.isHovered(mx, my, L + 6, ry, LIST_W - 20, ROW_H - 1);

                long timestamp;
                PendingChange.Action action;
                String idLabel;
                String rowStructureId;
                if (detailEntries != null) {
                    StructureChangeLog.Entry e = detailEntries.get(i + scroll);
                    timestamp = e.timestamp();
                    action = e.action();
                    rowStructureId = e.structureId();
                    idLabel = rowStructureId;
                } else {
                    StructureChangeLog.GroupedEntry ge = groupedEntries.get(i + scroll);
                    timestamp = ge.lastTimestamp();
                    action = ge.lastAction();
                    rowStructureId = ge.structureId();
                    idLabel = ge.count() > 1
                            ? Lang.get("gui.kve.structure.change_history.count_suffix", ge.structureId(), ge.count())
                            : ge.structureId();
                }

                if (hoveredRow) {
                    g.fill(L + 6, ry, L + LIST_W - 14, ry + ROW_H - 1, com.kve.client.gui.KveGuiStyle.th(0xFF14243A));
                    previewId = rowStructureId;
                    if (font.width(idLabel) > idLabelMaxW) hoveredFullLabel = idLabel;
                }
                String time = com.kve.client.gui.KveGuiStyle.formatDate("HH:mm:ss", timestamp);
                KveGuiStyle.drawTextSafe(g, time, L + 6, ry + 1, 44, KveGuiStyle.TEXT_MUTED);
                KveGuiStyle.drawTextSafe(g, actionLabel(action), L + 52, ry + 1, 36,
                        action == PendingChange.Action.CREATE ? KveGuiStyle.TEXT_OK : KveGuiStyle.ACCENT_GOLD);
                KveGuiStyle.drawTextSafe(g, idLabel, L + 90, ry + 1, idLabelMaxW,
                        detailEntries == null && groupedEntries.get(i + scroll).count() > 1
                                ? KveGuiStyle.ACCENT_GOLD : KveGuiStyle.TEXT_PRIMARY);
            }
        }
        scrollBar.render(g);

        renderPreviewPanel(g);
        listResize.render(g, mx, my);

        super.render(g, mx, my, pt);

        if (previewId != null) {
            StructureVoxelGrid previewGrid = previewCache.get(previewId);
            if (previewGrid != null) {
                previewCamera.setTarget(centerOf(previewGrid));
                previewCamera.orbit(0.35, 0);
                StructureSceneRenderer.render(pvX, pvY, pvW, pvH, previewGrid, previewCamera, null, false);
            }
        }

        if (hoveredFullLabel != null) {
            KveGuiStyle.drawFramedTooltip(g, hoveredFullLabel, mx, my, width, height);
        }
    }

    private void renderPreviewPanel(GuiGraphics g) {
        g.fill(pvX, pvY, pvX + pvW, pvY + pvH, KveGuiStyle.BG_FIELD);
        g.hLine(pvX, pvX + pvW - 1, pvY, KveGuiStyle.BORDER_FIELD);
        g.hLine(pvX, pvX + pvW - 1, pvY + pvH - 1, KveGuiStyle.BORDER_FIELD);
        g.vLine(pvX, pvY, pvY + pvH - 1, KveGuiStyle.BORDER_FIELD);
        g.vLine(pvX + pvW - 1, pvY, pvY + pvH - 1, KveGuiStyle.BORDER_FIELD);

        if (previewId == null) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.structure.picker.hover_hint"),
                    pvX + 6, pvY + pvH / 2 - 4, pvW - 12, KveGuiStyle.TEXT_MUTED);
            return;
        }
        if (!previewCache.hasAttempted(previewId)) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.structure.picker.loading"),
                    pvX + 6, pvY + pvH / 2 - 4, pvW - 12, KveGuiStyle.TEXT_MUTED);
        } else if (previewCache.get(previewId) == null) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.structure.picker.no_preview"),
                    pvX + 6, pvY + pvH / 2 - 4, pvW - 12, KveGuiStyle.TEXT_MUTED);
        }
        KveGuiStyle.drawTextSafe(g, previewId, pvX + 4, pvY + pvH - 10, pvW - 8, KveGuiStyle.TEXT_LABEL);
    }

    private static org.joml.Vector3f centerOf(StructureVoxelGrid grid) {
        int[] size = grid.computeSize();
        if (size == null) return new org.joml.Vector3f(0, 0, 0);
        return new org.joml.Vector3f(size[0] / 2f, size[1] / 2f, size[2] / 2f);
    }

    private String actionLabel(PendingChange.Action action) {
        return switch (action) {
            case CREATE -> Lang.get("gui.kve.structure.change_history.action.create");
            case EDIT -> Lang.get("gui.kve.structure.change_history.action.edit");
            case DELETE -> Lang.get("gui.kve.structure.change_history.action.delete");
        };
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (listResize.mouseClicked(mx, my)) return true;
        if (scrollBar != null && scrollBar.mouseClicked(mx, my, button)) return true;
        int scroll = scrollBar.getScroll();
        int visible = Math.min(ROWS_VISIBLE, Math.max(0, currentRowCount() - scroll));
        for (int i = 0; i < visible; i++) {
            int ry = T + 26 + i * ROW_H;
            if (mx >= L + 6 && mx < L + LIST_W - 14 && my >= ry && my < ry + ROW_H - 1) {
                if (detailEntries != null) {
                    String id = detailEntries.get(i + scroll).structureId();
                    if (onContinueEditing != null) onContinueEditing.accept(id);
                } else {
                    StructureChangeLog.GroupedEntry ge = groupedEntries.get(i + scroll);
                    if (ge.count() > 1) {
                        enterDetail(ge.structureId());
                    } else if (onContinueEditing != null) {
                        onContinueEditing.accept(ge.structureId());
                    }
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (listResize.mouseDragged(mx)) { init(); return true; }
        if (listResize.isDragging()) return true;
        if (scrollBar != null && scrollBar.mouseDragged(mx, my, button, dx, dy)) return true;
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        listResize.mouseReleased();
        if (scrollBar != null) scrollBar.mouseReleased(mx, my, button);
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        // "en el historial de estructuras
        // modificadas del structure editor en el render no se puede hacer
        // zoom in/out como en el picker structure" - este render de
        // preview usa el mismo OrbitCamera/StructureSceneRenderer que
        // StructurePickerScreen, pero a diferencia de ese, mouseScrolled()
        // aqui siempre mandaba el delta al scrollBar de la lista sin
        // importar donde estuviera el cursor. Ahora, igual que en
        // StructurePickerScreen#mouseScrolled(), si el cursor esta sobre el
        // panel de preview (pvX/pvY/pvW/pvH) el scroll hace zoom de la
        // camara en vez de mover la lista - la lista solo se desplaza
        // cuando el cursor esta sobre ella, nunca robandole el scroll al
        // render.
        if (previewId != null && KveGuiStyle.isHovered((int) mx, (int) my, pvX, pvY, pvW, pvH)) {
            previewCamera.zoom(-delta);
            return true;
        }
        if (scrollBar != null) { scrollBar.mouseScrolled(delta); return true; }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
