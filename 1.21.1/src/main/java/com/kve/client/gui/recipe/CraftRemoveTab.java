package com.kve.client.gui.recipe;

import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KvePlateButton;
import com.kve.client.gui.MiniItemIcon;
import com.kve.client.gui.ScrollBar;

import com.kve.common.Lang;
import com.kve.client.gui.history.KveChangeLog;
import com.kve.client.gui.workspace.pending.PendingChange;
import com.kve.client.gui.workspace.pending.PendingChangeManager;
import com.kve.client.gui.workspace.pending.PendingDomain;
import com.kve.client.permission.ClientPermissionState;
import com.kve.client.recipe.RecipeOutputLookup;
import com.kve.client.recipe.RecipeRemovalQueue;
import com.kve.common.kubejs.KubeJSExporter;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
final class CraftRemoveTab {

    /** Lo que la pestana necesita de la pantalla que la contiene. */
    interface Host {
        void flash(String msg);
        void refreshScreen();
        void openItemPicker(Consumer<ItemStack> onPick);
        void openRecipeBrowser();
        Font font();
    }

    private static final int ROW_H = 16;

    private final Host host;
    final List<String> removeList = new ArrayList<>();
    final List<String> queuedRemoveList = new ArrayList<>();
    ScrollBar removeScrollBar;
    ScrollBar queuedScrollBar;
    int removeSelected = -1;
    int queueSelected = -1;
    /** Texto del buscador: filtra las dos listas por id de receta o nombre del resultado. */
    String filter = "";

    private int listRows = 8;
    private int queueRows = 8;
    private int queueX, queueW = 240;

    CraftRemoveTab(Host host) {
        this.host = host;
    }

    static int queueBarX(int ex, int rightEdge) { return rightEdge - 8; }

    void ensureScrollBars(int listBarX, int listBarY, int listRows, int queueBarX, int queueTopY, int queueRows) {
        this.listRows = listRows;
        this.queueRows = queueRows;
        int keepList = removeScrollBar != null ? removeScrollBar.getScroll() : 0;
        int keepQueue = queuedScrollBar != null ? queuedScrollBar.getScroll() : 0;
        removeScrollBar = new ScrollBar(listBarX, listBarY, listRows * ROW_H, removeList.size(), listRows);
        queuedScrollBar = new ScrollBar(queueBarX, queueTopY, queueRows * ROW_H, queuedRemoveList.size(), queueRows);
        removeScrollBar.setScroll(keepList);
        queuedScrollBar.setScroll(keepQueue);
    }

    void initWidgets(Consumer<AbstractWidget> add, int ex, int top, int rightEdge, int bottomRowY) {
        queueX = ex;
        queueW = Math.max(120, rightEdge - 10 - ex);

        KvePlateButton find = KveGuiStyle.clampedBtn(ex, top + 17, rightEdge - ex,
                Lang.get("gui.kve.remove.find_recipe"), b -> host.openRecipeBrowser()).withPrimary(true);
        find.setTooltip(Tooltip.create(Lang.c("gui.kve.remove.find_recipe_tooltip")));
        add.accept(find);
        int x = ex + find.getWidth() + KveGuiStyle.BTN_GAP;
        KvePlateButton byItem = KveGuiStyle.clampedBtn(x, top + 17, rightEdge - x,
                Lang.get("gui.kve.remove.by_item"), b -> openPicker());
        byItem.setTooltip(Tooltip.create(Lang.c("gui.kve.remove.by_item_tooltip")));
        add.accept(byItem);
        x += byItem.getWidth() + KveGuiStyle.BTN_GAP;
        KvePlateButton restore = KveGuiStyle.clampedBtn(x, top + 17, rightEdge - x,
                Lang.get("gui.kve.remove.restore_selected"), b -> restoreSelected());
        add.accept(restore);

        Button clear = KveGuiStyle.clampedBtn(ex, bottomRowY, rightEdge - ex,
                Lang.get("gui.kve.remove.clear_queue"), b -> clearQueue());
        clear.active = !queuedRemoveList.isEmpty();
        add.accept(clear);
    }

    /** Y donde empiezan las filas de la cola - compartido por dibujo y hit-testing para que nunca diverjan. */
    static int queueListTopY(int ex, int top) {
        KveGuiStyle.Layout ly = new KveGuiStyle.Layout(ex, top + 32, 14);
        ly.row(14);
        ly.row(12);
        return ly.y();
    }

    private void openPicker() {
        host.openItemPicker(stack -> {
            String itemId = KubeJSExporter.itemId(stack);
            if (itemId == null) return;
            List<String> ids = new ArrayList<>(RecipeOutputLookup.idsProducing(stack.getItem()));
            ids.removeIf(id -> KubeJSExporter.getRemovedRecipes().contains(id));
            if (ids.isEmpty()) {
                host.flash(Lang.get("gui.kve.remove.by_item_none", itemId));
            } else if (ids.size() == 1) {
                queueRemove(ids.get(0));
            } else {
                if (!ClientPermissionState.canEdit()) { host.flash(Lang.get("gui.kve.perm.denied")); return; }
                RecipeRemovalQueue.queueBulk(itemId, ids);
                refreshLists();
                host.flash(Lang.get("gui.kve.remove.by_item_queued", ids.size(), itemId) + " " + Lang.get("gui.kve.pending.queued_hint_reload"));
                host.refreshScreen();
            }
        });
    }

    void queueRemove(String id) {
        if (!ClientPermissionState.canEdit()) { host.flash(Lang.get("gui.kve.perm.denied")); return; }
        // ya quitada o en la cola: un segundo pendiente haria que descartar uno deshaga la quita del otro
        if (KubeJSExporter.getRemovedRecipes().contains(id)) { host.flash(Lang.get("gui.kve.remove.already", id)); return; }
        KubeJSExporter.stageRemoveVanilla(id);
        PendingChangeManager.queue(PendingChange.of(PendingDomain.RECIPES, id, PendingChange.Action.DELETE, false,
                () -> {
                    KubeJSExporter.commitRemoved();
                    KveChangeLog.record(KveChangeLog.Log.RECIPES, PendingChange.Action.DELETE, id);
                },
                () -> { KubeJSExporter.discardStagedRemoveVanilla(id); refreshLists(); })
                .withDiff(null, () -> "event.remove({ id: '" + id + "' });"));
        refreshLists();
        host.flash(Lang.get("gui.kve.recipe.queued_removal", id) + " " + Lang.get("gui.kve.pending.queued_hint_reload"));
        host.refreshScreen();
    }

    private void queueUnremove(String id) {
        if (!ClientPermissionState.canEdit()) { host.flash(Lang.get("gui.kve.perm.denied")); return; }
        KubeJSExporter.stageUnremoveVanilla(id);
        PendingChangeManager.queue(PendingChange.of(PendingDomain.RECIPES, id, PendingChange.Action.EDIT, false,
                () -> {
                    KubeJSExporter.commitRemoved();
                    KveChangeLog.record(KveChangeLog.Log.RECIPES, PendingChange.Action.EDIT, id);
                },
                () -> { KubeJSExporter.discardStagedUnremoveVanilla(id); refreshLists(); })
                .withDiff(() -> "event.remove({ id: '" + id + "' });", null));
        refreshLists();
        host.flash(Lang.get("gui.kve.recipe.restore_queued", id) + " " + Lang.get("gui.kve.pending.queued_hint_reload"));
        host.refreshScreen();
    }

    /**
     * Saca un id de la COLA (sin Aplicar): descarta su PendingChange en vez de
     * encolar un "unremove" encima de un "remove" que nunca se aplico - eso
     * dejaba un EDIT fantasma contando en "Cambios pendientes (N)". Si el id
     * vino en un lote, solo el sale; el resto del lote sigue en la cola.
     */
    private void cancelQueued(String id) {
        PendingChange own = PendingChangeManager.entriesForDomain(PendingDomain.RECIPES).stream()
                .filter(c -> c.entryId().equals(id)).findFirst().orElse(null);
        if (own != null) {
            PendingChangeManager.discard(own);
        } else {
            RecipeRemovalQueue.forgetFromBulk(id);
            KubeJSExporter.discardStagedRemoveVanilla(id);
        }
    }

    void restoreApplied(int index) {
        if (index < 0 || index >= removeList.size()) return;
        removeSelected = -1;
        queueUnremove(removeList.get(index));
    }

    private void restoreSelected() {
        if (queueSelected >= 0 && queueSelected < queuedRemoveList.size()) {
            String id = queuedRemoveList.get(queueSelected);
            queueSelected = -1;
            cancelQueued(id);
            refreshLists();
            host.refreshScreen();
            return;
        }
        restoreApplied(removeSelected);
    }

    private void clearQueue() {
        Set<String> entries = new LinkedHashSet<>();
        for (String id : queuedRemoveList) entries.add(RecipeRemovalQueue.pendingEntryOf(id));
        for (PendingChange c : new ArrayList<>(PendingChangeManager.entriesForDomain(PendingDomain.RECIPES))) {
            if (c.action() == PendingChange.Action.DELETE && entries.contains(c.entryId())) PendingChangeManager.discard(c);
        }
        refreshLists();
        for (String id : new ArrayList<>(queuedRemoveList)) cancelQueued(id);
        queueSelected = -1;
        refreshLists();
        host.refreshScreen();
    }

    void setFilter(String text) {
        filter = text == null ? "" : text;
        removeSelected = -1;
        queueSelected = -1;
        if (removeScrollBar != null) removeScrollBar.setScroll(0);
        if (queuedScrollBar != null) queuedScrollBar.setScroll(0);
        refreshLists();
    }

    private boolean matchesFilter(String id) {
        if (filter.isBlank()) return true;
        net.minecraft.world.item.Item out = RecipeOutputLookup.outputOf(id);
        String name = out == null ? "" : out.getDescription().getString();
        return com.kve.client.gui.SearchFilter.matches(id, name, filter);
    }

    void refreshLists() {
        removeList.clear();
        queuedRemoveList.clear();
        for (String id : KubeJSExporter.getRemovedRecipes()) {
            if (!matchesFilter(id)) continue;
            if (RecipeRemovalQueue.isPending(id)) queuedRemoveList.add(id);
            else removeList.add(id);
        }
        if (removeScrollBar != null) removeScrollBar.setTotals(removeList.size(), listRows);
        if (queuedScrollBar != null) queuedScrollBar.setTotals(queuedRemoveList.size(), queueRows);
        if (removeSelected >= removeList.size()) removeSelected = -1;
        if (queueSelected >= queuedRemoveList.size()) queueSelected = -1;
    }

    void drawLeftHeader(GuiGraphics g, int lx, int top, int w) {
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.remove.applied_header", removeList.size()), lx + 1, top + 20, w, KveGuiStyle.ACCENT_GOLD);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.remove.applied_hint"), lx + 1, top + 31, w, KveGuiStyle.TEXT_MUTED);
    }

    void drawLeftRowTooltip(GuiGraphics g, String id, int mx, int my) {
        g.renderComponentTooltip(host.font(), rowTooltip(id, "gui.kve.remove.applied_row_hint"), mx, my);
    }

    private static List<Component> rowTooltip(String id, String hintKey) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(id));
        String type = RecipeOutputLookup.typeOf(id);
        if (!type.isEmpty()) lines.add(Component.literal(type).withStyle(ChatFormatting.DARK_AQUA));
        lines.add(Component.literal(Lang.get(hintKey)).withStyle(ChatFormatting.GRAY));
        return lines;
    }

    private boolean overCancelGlyph(double mx, int ry) {
        int right = queueX + queueW;
        return mx >= right - 12 && mx < right;
    }

    void drawQueuePanel(GuiGraphics g, int ex, int top, int mx, int my) {
        Font font = host.font();
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.remove.hint"), ex, top + 34, queueW, KveGuiStyle.TEXT_MUTED);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.remove.queue_header", queuedRemoveList.size()), ex, top + 47, queueW, 0xFFFF9966);
        int listY = queueListTopY(ex, top);
        if (queuedRemoveList.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.remove.queue_empty"), ex + 2, listY + 4, queueW, KveGuiStyle.BORDER_SUBTLE);
            return;
        }

        String hovered = null;
        int scroll = queuedScrollBar != null ? queuedScrollBar.getScroll() : 0;
        int vis = Math.min(queueRows, queuedRemoveList.size() - scroll);
        for (int i = 0; i < vis; i++) {
            String id = queuedRemoveList.get(i + scroll);
            int ry = listY + i * ROW_H;
            boolean sel = (i + scroll) == queueSelected;
            boolean hov = mx >= ex && mx < ex + queueW && my >= ry && my < ry + ROW_H - 1;
            g.fill(ex, ry, ex + queueW, ry + ROW_H - 1, sel ? 0xFF5A3030 : hov ? 0xFF3A2020 : KveGuiStyle.BG_FIELD);
            MiniItemIcon.draw(g, ex + 3, ry + 3, 9, RecipeOutputLookup.outputOf(id));
            int textRight = ex + queueW - 4;
            if (hov) {
                boolean onX = overCancelGlyph(mx, ry);
                KveGuiStyle.drawTextSafe(g, "x", ex + queueW - 9, ry + 4, 8, onX ? 0xFFFF5555 : 0xFFCC7766);
                textRight -= 12;
                if (!onX) hovered = id;
            }
            String type = RecipeOutputLookup.typeOf(id);
            int typeW = type.isEmpty() ? 0 : Math.min(font.width(type), queueW / 3);
            if (typeW > 0) {
                KveGuiStyle.drawTextSafe(g, type, textRight - typeW, ry + 4, typeW, KveGuiStyle.TEXT_MUTED);
                textRight -= typeW + 6;
            }
            KveGuiStyle.drawTextSafe(g, id, ex + 16, ry + 4, textRight - (ex + 16), sel ? 0xFFFFBB99 : 0xFFFF7755);
        }
        if (queuedScrollBar != null) queuedScrollBar.render(g);
        if (hovered != null) g.renderComponentTooltip(font, rowTooltip(hovered, "gui.kve.remove.queued_row_hint"), mx, my);
    }

    boolean clickQueuePanel(double mx, double my, int btn, int ex, int top) {
        if (queuedScrollBar != null && queuedScrollBar.mouseClicked(mx, my, btn)) return true;
        int qscroll = queuedScrollBar != null ? queuedScrollBar.getScroll() : 0;
        int vis = Math.min(queueRows, queuedRemoveList.size() - qscroll);
        int listY = queueListTopY(ex, top);
        for (int i = 0; i < vis; i++) {
            int ry = listY + i * ROW_H;
            if (mx >= ex && mx < ex + queueW && my >= ry && my < ry + ROW_H - 1) {
                if (overCancelGlyph(mx, ry)) {
                    cancelQueued(queuedRemoveList.get(i + qscroll));
                    queueSelected = -1;
                    refreshLists();
                    host.refreshScreen();
                } else {
                    queueSelected = i + qscroll;
                    removeSelected = -1;
                }
                return true;
            }
        }
        return false;
    }

    boolean scrollQueue(double mx, double delta, int ex) {
        if (queuedScrollBar != null && mx >= ex && mx < ex + queueW + 12) {
            queuedScrollBar.mouseScrolled(delta);
            return true;
        }
        return false;
    }

    boolean dragScrollBars(double mx, double my, int btn, double dx, double dy) {
        if (removeScrollBar.mouseDragged(mx, my, btn, dx, dy)) return true;
        return queuedScrollBar != null && queuedScrollBar.mouseDragged(mx, my, btn, dx, dy);
    }

    void releaseScrollBars(double mx, double my, int btn) {
        removeScrollBar.mouseReleased(mx, my, btn);
        if (queuedScrollBar != null) queuedScrollBar.mouseReleased(mx, my, btn);
    }
}
