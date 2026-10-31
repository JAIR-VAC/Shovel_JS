package com.kve.client.gui;

import com.kve.common.Lang;
import com.kve.client.gui.studio.KveStudioShell;
import com.kve.client.gui.studio.KveStudioTab;
import com.kve.client.permission.ClientPermissionState;
import com.kve.client.gui.workspace.pending.PendingChangeManager;
import com.kve.client.gui.workspace.pending.PendingDomain;
import com.kve.common.event.EventEntry;
import com.kve.common.kubejs.EventExporter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class EventListScreen extends Screen implements KveStudioTab {

    private static final int GUI_W_MIN = 260;
    private int GUI_W = GUI_W_MIN;
    private static final int ROWS_VISIBLE_MIN = 8;
    private int ROWS_VISIBLE = ROWS_VISIBLE_MIN;
    private static final int ROW_H = 22;
    private int GUI_H = 34 + ROWS_VISIBLE_MIN * ROW_H + 46;

    private final Screen returnScreen;

    private boolean hosted = false;
    private int hostedX, hostedY, hostedW, hostedH;

    private int L, T;
    private final List<EventEntry> savedList = new ArrayList<>();
    private ScrollBar scrollBar;
    private EditBox searchBox;
    private String searchFilter = "";

    private String flashMsg = "";
    private long flashAt = 0;

    private EventEditorScreen embeddedEditor;

    public EventListScreen(Screen returnScreen) {
        super(Lang.c("gui.kve.event.list.title"));
        this.returnScreen = returnScreen;
    }

    @Override
    protected void init() {
        if (KveStudioShell.reclaimIfHosted(this, returnScreen)) return;
        hosted = false;
        GUI_W = GUI_W_MIN;
        ROWS_VISIBLE = ROWS_VISIBLE_MIN;
        GUI_H = 34 + ROWS_VISIBLE * ROW_H + 46;
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
        hostedX = x0; hostedY = y0; hostedW = w; hostedH = h;
        if (embeddedEditor != null) { embeddedEditor.init(x0, y0, w, h); return; }
        GUI_W = Math.max(w, GUI_W_MIN);
        ROWS_VISIBLE = Math.max(ROWS_VISIBLE_MIN, (h - 34 - 46) / ROW_H);
        GUI_H = Math.max(h, 34 + ROWS_VISIBLE * ROW_H + 46);
        L = x0;
        T = y0;
        buildWidgets();
    }

    private void buildWidgets() {
        clearWidgets();
        setFocused(null);
        EventExporter.loadIfNeeded();
        refreshSaved();

        int pendCount = PendingChangeManager.size();
        if (!hosted) {
            String applyLabel = pendCount > 0 ? Lang.get("gui.kve.pending.apply") + " (" + pendCount + ")" : Lang.get("gui.kve.pending.apply");
            String discardLabel = pendCount > 0 ? Lang.get("gui.kve.pending.discard") + " (" + pendCount + ")" : Lang.get("gui.kve.pending.discard");
            int applyW = KveGuiStyle.btnWidth(applyLabel);
            int discardW = KveGuiStyle.btnWidth(discardLabel);
            int discardX = L + GUI_W - 2 - discardW;
            int applyX = discardX - KveGuiStyle.BTN_GAP - applyW;
            KveGuiStyle.ButtonRow pendRow = new KveGuiStyle.ButtonRow(applyX, T + 2, L + GUI_W - 2);
            Button applyPendBtn = addRenderableWidget(pendRow.add(0, applyLabel, b -> onApplyPending()));
            applyPendBtn.active = pendCount > 0 && ClientPermissionState.canApply();
            Button discardPendBtn = addRenderableWidget(pendRow.add(0, discardLabel, b -> onDiscardPending()));
            discardPendBtn.active = pendCount > 0;
        }

        searchBox = new KveScaledEditBox(font, L + 6, T + 18, GUI_W - 12, 12, Lang.c("gui.kve.event.search_hint"));
        searchBox.setMaxLength(64);
        searchBox.setValue(searchFilter);
        searchBox.setResponder(s -> { searchFilter = s; refreshSaved(); });
        addRenderableWidget(searchBox);

        int listY = T + 34;
        scrollBar = new ScrollBar(L + GUI_W - 14, listY, ROWS_VISIBLE * ROW_H, 0, ROWS_VISIBLE);
        updateScroll();

        int newBtnY = listY + ROWS_VISIBLE * ROW_H + 6;
        addRenderableWidget(btn(Lang.get("gui.kve.event.new"), L + 6, newBtnY, 0, this::onNewEventClicked));

    }

    private void refresh() {
        if (embeddedEditor != null) { embeddedEditor.init(hostedX, hostedY, hostedW, hostedH); return; }
        buildWidgets();
    }

    private void showEditor(EventEditorScreen ed) {
        if (hosted) {
            ed.withEmbedding(this, returnScreen, this::closeEmbeddedEditor);
            embeddedEditor = ed;
            ed.init(hostedX, hostedY, hostedW, hostedH);
            // onNewEventClicked()/openTemplatePicker() llegan aca desde el
            // callback de un picker standalone (TypeOptionPickerScreen/
            // EventTemplatePickerScreen) que en este momento SIGUE siendo
            // la pantalla real de Minecraft (nunca hicimos
            // minecraft.setScreen() sobre esta lista mientras esta hosteada -
            // el shell la dibuja por delegacion). Sin este setScreen() el
            // picker se quedaria en pantalla para siempre con el editor ya
            // embebido pero invisible atrás.
            minecraft.setScreen(returnScreen);
        } else {
            minecraft.setScreen(ed);
        }
    }

    private void closeEmbeddedEditor() {
        embeddedEditor = null;
        refreshSaved();
        refresh();
    }

    private final Map<String, Boolean> brokenRefCache = new HashMap<>();

    private void refreshSaved() {
        savedList.clear();
        brokenRefCache.clear();
        for (EventEntry e : EventExporter.getEntries()) {
            if (!SearchFilter.matches(e.id, searchFilter)) continue;
            savedList.add(e);
            brokenRefCache.put(e.id, !e.referenceWarnings().isEmpty());
        }
        updateScroll();
    }

    private void updateScroll() {
        if (scrollBar != null) scrollBar.setTotals(savedList.size(), ROWS_VISIBLE);
    }

    private void onNewEventClicked() {
        List<TypeOptionPickerScreen.Option> opts = new ArrayList<>();
        opts.add(new TypeOptionPickerScreen.Option(
                Lang.get("gui.kve.event.template.pick_template"), "",
                this::openTemplatePicker));
        opts.add(new TypeOptionPickerScreen.Option(
                Lang.get("gui.kve.event.template.pick_from_scratch"), "",
                () -> showEditor(new EventEditorScreen(this))));
        minecraft.setScreen(new TypeOptionPickerScreen(this, Lang.get("gui.kve.event.template.title"), opts, -1));
    }

    private void openTemplatePicker() {
        minecraft.setScreen(new EventTemplatePickerScreen(this, tpl ->
                showEditor(new EventEditorScreen(this, tpl.create()).withAppliedTemplateHint())));
    }

    private void onApplyPending() {
        if (!ClientPermissionState.canApply()) { flash(Lang.get("gui.kve.perm.apply_denied")); return; }
        if (PendingChangeManager.isEmpty()) return;
        if (PendingChangeManager.domainHasDiff(PendingDomain.EVENTS)) {
            minecraft.setScreen(new DiffPreviewScreen(this,
                    PendingChangeManager.entriesForDomain(PendingDomain.EVENTS),
                    this::proceedApply));
            return;
        }
        proceedApply();
    }

    private void proceedApply() {
        java.util.Set<PendingDomain> domains = PendingChangeManager.domainsPresent();
        if (domains.size() > 1) {
            int otherCount = PendingChangeManager.size() - PendingChangeManager.sizeForDomain(PendingDomain.EVENTS);
            minecraft.setScreen(new KveConfirmScreen(
                    confirmed -> {
                        minecraft.setScreen(this);
                        applyPendingResult(confirmed ? PendingChangeManager.applyAll() : PendingChangeManager.applyDomain(PendingDomain.EVENTS));
                    },
                    Component.literal(Lang.get("gui.kve.pending.other_domains_title")),
                    Component.literal(Lang.get("gui.kve.pending.other_domains_msg", otherCount))));
            return;
        }
        applyPendingResult(PendingChangeManager.applyDomain(PendingDomain.EVENTS));
    }

    private void applyPendingResult(PendingChangeManager.ApplyResult result) {
        com.kve.client.gui.KveToast.applied(result);
        refreshSaved();
        refresh();
    }

    private void onDiscardPending() {
        if (PendingChangeManager.isEmpty()) return;
        int count = PendingChangeManager.sizeForDomain(PendingDomain.EVENTS);
        if (count == 0) return;
        PendingChangeManager.discardDomain(PendingDomain.EVENTS);
        flash(Lang.get("gui.kve.pending.discarded", count));
        refreshSaved();
        refresh();
    }

    private void flash(String msg) { flashMsg = msg; flashAt = System.currentTimeMillis(); }

    private void goBack() {
        if (KveStudioShell.exitIfHosted(this, returnScreen)) return;
        minecraft.setScreen(returnScreen != null ? returnScreen : new KveStudioShell());
    }

    @Override
    public void onClose() { goBack(); }

    private Button btn(String label, int x, int y, int w, Runnable a) {
        return KveGuiStyle.smartBtn(x, y, w, label, b -> a.run());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (embeddedEditor != null) { embeddedEditor.render(g, mx, my, pt); return; }
        if (hosted) {
            int wantW = Math.max(hostedW, GUI_W_MIN);
            int wantRows = Math.max(ROWS_VISIBLE_MIN, (hostedH - 34 - 46) / ROW_H);
            int wantH = Math.max(hostedH, 34 + wantRows * ROW_H + 46);
            if (wantW != GUI_W || wantH != GUI_H || wantRows != ROWS_VISIBLE) init(hostedX, hostedY, hostedW, hostedH);
        }
        if (!hosted) renderBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, Lang.get("gui.kve.event.list.title"));

        int listY = T + 34;
        int scroll = scrollBar.getScroll();
        for (int i = 0; i < Math.min(ROWS_VISIBLE, savedList.size() - scroll); i++) {
            EventEntry e = savedList.get(i + scroll);
            int ry = listY + i * ROW_H;
            boolean hov = KveGuiStyle.isHovered(mx, my, L + 4, ry, GUI_W - 20, ROW_H - 2);
            g.fill(L + 4, ry, L + GUI_W - 20, ry + ROW_H - 2, hov ? KveGuiStyle.BORDER_FIELD : KveGuiStyle.BG_FIELD);
            // "mismo
            // indicador visual que ya usan otros editores para 'id no resoluble'" -
            // same referenceWarnings()-driven TEXT_WARN color TradeEditorScreen already
            // uses for an unresolved trade ref, applied here per-row. Doesn't touch
            // export (EventExporter already emits the warning comment regardless of
            // whether this screen is ever opened) - this is purely the editor-side cue.
            // Read from brokenRefCache (see refreshSaved()), NOT recomputed here -
            // referenceWarnings() is too expensive to call per-row-per-frame.
            boolean brokenRef = Boolean.TRUE.equals(brokenRefCache.get(e.id));
            String rowLabel = (e.enabled ? "" : "[OFF] ") + (brokenRef ? "[!] " : "") + e.id;
            int rowColor = !e.enabled ? KveGuiStyle.TEXT_MUTED : (brokenRef ? KveGuiStyle.TEXT_WARN : KveGuiStyle.TEXT_PRIMARY);
            KveGuiStyle.drawTextSafe(g, rowLabel, L + 7, ry + 3, GUI_W - 30, rowColor);
            String summary = triggerLabel(e.trigger.type) + " - " + e.actions.size() + " " + Lang.get("gui.kve.event.actions_short");
            KveGuiStyle.drawTextSafe(g, summary, L + 7, ry + 13, GUI_W - 30, KveGuiStyle.TEXT_MUTED);
        }
        scrollBar.render(g);
        if (savedList.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.event.empty_list"), L + 7, listY + 4, GUI_W - 14, KveGuiStyle.TEXT_MUTED);
        }

        if (!flashMsg.isEmpty() && System.currentTimeMillis() - flashAt < 4500) {
            KveGuiStyle.drawTextSafe(g, flashMsg, L + 6, T + GUI_H - 30, GUI_W - 12, KveGuiStyle.ACCENT_GOLD);
        }

        super.render(g, mx, my, pt);
    }

    private String triggerLabel(EventEntry.TriggerType t) {
        return switch (t) {
            case FIRST_JOIN -> Lang.get("gui.kve.event.trigger.first_join");
            case PLAYER_LOGIN -> Lang.get("gui.kve.event.trigger.player_login");
            case INTERVAL -> Lang.get("gui.kve.event.trigger.interval");
            case PLAYER_KILL -> Lang.get("gui.kve.event.trigger.player_kill");
            case COMMAND -> Lang.get("gui.kve.event.trigger.command");
            case ENTER_BIOME -> Lang.get("gui.kve.event.trigger.enter_biome");
            case PLAYER_DEATH -> Lang.get("gui.kve.event.trigger.player_death");
            case PLAYER_RESPAWN -> Lang.get("gui.kve.event.trigger.player_respawn");
            case PLAYER_HURT -> Lang.get("gui.kve.event.trigger.player_hurt");
            case DIMENSION_CHANGE -> Lang.get("gui.kve.event.trigger.dimension_change");
            case BLOCK_BREAK -> Lang.get("gui.kve.event.trigger.block_break");
            case BLOCK_PLACE -> Lang.get("gui.kve.event.trigger.block_place");
            case ITEM_PICKUP -> Lang.get("gui.kve.event.trigger.item_pickup");
            case ITEM_CRAFT -> Lang.get("gui.kve.event.trigger.item_craft");
            case XP_LEVEL_REACH -> Lang.get("gui.kve.event.trigger.xp_level_reach");
            case XP_CHANGED -> Lang.get("gui.kve.event.trigger.xp_changed");
            case ADVANCEMENT_EARNED -> Lang.get("gui.kve.event.trigger.advancement_earned");
            case EFFECT_GAINED -> Lang.get("gui.kve.event.trigger.effect_gained");
            case ITEM_USE -> Lang.get("gui.kve.event.trigger.item_use");
            case ITEM_CONSUME -> Lang.get("gui.kve.event.trigger.item_consume");
            case ITEM_SMELT -> Lang.get("gui.kve.event.trigger.item_smelt");
            case FISHING_CATCH -> Lang.get("gui.kve.event.trigger.fishing_catch");
            case VILLAGER_TRADE -> Lang.get("gui.kve.event.trigger.villager_trade");
            case ENTITY_KILL_ANY -> Lang.get("gui.kve.event.trigger.entity_kill_any");
            case ENTITY_INTERACT -> Lang.get("gui.kve.event.trigger.entity_interact");
            case TAMING -> Lang.get("gui.kve.event.trigger.taming");
            case BREEDING -> Lang.get("gui.kve.event.trigger.breeding");
            case MOUNT -> Lang.get("gui.kve.event.trigger.mount");
            case PLAYER_DISCONNECT -> Lang.get("gui.kve.event.trigger.player_disconnect");
            case SERVER_STARTUP -> Lang.get("gui.kve.event.trigger.server_startup");
            case EMPTY_BUCKET -> Lang.get("gui.kve.event.trigger.empty_bucket");
            case FILL_BUCKET -> Lang.get("gui.kve.event.trigger.fill_bucket");
            case ENTITY_SPAWN -> Lang.get("gui.kve.event.trigger.entity_spawn");
            case ENTITY_DEATH -> Lang.get("gui.kve.event.trigger.entity_death");
            case ENTITY_ATTACKS -> Lang.get("gui.kve.event.trigger.entity_attacks");
        };
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (embeddedEditor != null) return embeddedEditor.mouseClicked(mx, my, btn);
        if (scrollBar.mouseClicked(mx, my, btn)) return true;
        int listY = T + 34;
        int scroll = scrollBar.getScroll();
        for (int i = 0; i < Math.min(ROWS_VISIBLE, savedList.size() - scroll); i++) {
            int ry = listY + i * ROW_H;
            if (mx >= L + 4 && mx < L + GUI_W - 20 && my >= ry && my < ry + ROW_H - 2) {
                showEditor(new EventEditorScreen(this, savedList.get(i + scroll)));
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
        if (embeddedEditor != null) return embeddedEditor.mouseDragged(mx, my, b, dx, dy);
        if (scrollBar.mouseDragged(mx, my, b, dx, dy)) return true;
        return super.mouseDragged(mx, my, b, dx, dy);
    }
    @Override public boolean mouseReleased(double mx, double my, int b) {
        if (embeddedEditor != null) return embeddedEditor.mouseReleased(mx, my, b);
        scrollBar.mouseReleased(mx, my, b);
        return super.mouseReleased(mx, my, b);
    }
    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (embeddedEditor != null) return embeddedEditor.mouseScrolled(mx, my, delta);
        scrollBar.mouseScrolled(delta);
        return true;
    }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (embeddedEditor != null) return embeddedEditor.keyPressed(key, scan, mods);
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (getFocused() != null && getFocused().keyPressed(key, scan, mods)) return true;
        if (EditorKeys.isFocusTab(key, mods)) { super.keyPressed(key, scan, mods); return true; }
        return EditorKeys.swallow(mods);
    }
    @Override public boolean charTyped(char c, int mods) {
        if (embeddedEditor != null) return embeddedEditor.charTyped(c, mods);
        return super.charTyped(c, mods);
    }
    @Override public boolean isPauseScreen() { return false; }
}
