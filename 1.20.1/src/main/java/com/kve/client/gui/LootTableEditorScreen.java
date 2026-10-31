package com.kve.client.gui;

import org.lwjgl.glfw.GLFW;
import com.kve.common.Lang;
import com.kve.client.permission.ClientPermissionState;
import com.kve.common.config.KVEConfig;
import com.kve.common.kubejs.LootTableExporter;
import com.kve.common.persist.CraftEditorState;
import com.kve.client.gui.workspace.pending.PendingDomain;
import com.kve.client.gui.workspace.pending.PendingChange;
import com.kve.client.gui.workspace.pending.PendingChangeManager;
import com.kve.client.gui.studio.KveStudioShell;
import com.kve.client.gui.studio.KveStudioTab;
import com.kve.client.gui.history.KveChangeLog;
import com.kve.client.gui.history.KveChangeHistoryScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class LootTableEditorScreen extends Screen implements KveStudioTab {

    private static final int GUI_H_DEFAULT = 270;
    private int GUI_H = GUI_H_DEFAULT;
    private static final int LIST_ROWS_MIN = 5;
    private static final int SAVED_ROW_H = 34;

    private int listRowsVisible() { return Math.max(LIST_ROWS_MIN, (T + GUI_H - 6 - (T + 30)) / SAVED_ROW_H); }
    private static final int TREE_ROW_H = 12;

    private static final int LIST_W_DEFAULT = 140;
    private static final int LIST_W_MIN     = 100;
    private static final int LIST_W_MAX     = 220;
    private static final int RIGHT_PANEL_W  = 510 - LIST_W_DEFAULT;

    private static final int SIDE_W = 150;
    private static final int MID_W_MIN = 290;

    private int L, T;
    private int GUI_W, LIST_W;
    private ResizeHandle listResize;

    private final List<LootTableExporter.LootTableEntry> savedList = new ArrayList<>();
    private ScrollBar savedScrollBar;
    private EditBox searchBox;
    private String searchFilter = "";

    private String currentKey = "";
    private LootTableExporter.TargetKind draftKind = LootTableExporter.TargetKind.ENTITY;
    private LootTableExporter.TriggerEvent draftEvent = LootTableExporter.TriggerEvent.DEATH;
    private String draftTargetId = "";
    private String draftCategory = "";
    private boolean draftEnabled = true;
    private boolean showMoreActions = false;
    private final List<LootTableExporter.LootPoolData> draftPools = new ArrayList<>();
    private int currentPoolIdx = 0;
    private int entryScroll = 0;

    // Columna SIDE (ver SIDE_W): que entrada del arbol se previsualiza/edita
    // ahi. Se guarda por (pool, indice-dentro-del-pool) en vez de la
    // referencia LootEntryData directamente porque addSideFn()/removeSideFn()
    // mutan la lista de functions de esa entrada, y removePool/removeEntry
    // en otro lado del arbol pueden correr indices - un indice + su pool se
    // resuelve de nuevo en cada selectedEntry() en vez de arrastrar una
    // referencia que podria quedar apuntando a un objeto ya sacado de
    // draftPools. -1/-1 = nada seleccionado.
    private int selectedPoolIdx = -1;
    private int selectedEntryIdx = -1;

    private EditBox sWeight, sMin, sMax;
    private EditBox sLevelsMin, sLevelsMax, sNbt;
    private int sideFnIdx = 0;

    private EditBox entrySearchBox;
    private String entrySearchFilter = "";

    private EditBox fCategory, fRolls, fChance, fLootingMult, fCustomTableId;

    private String flashMsg = "";
    private long   flashAt  = 0;

    private Screen returnScreen = null;

    private boolean hosted = false;
    // Ultimo ancho que el shell le asigno a esta tab (el `w` del overload) -
    // refresh() lo necesita para recalcular GUI_W exactamente igual que el
    // overload lo hace, sin tener que volver a pedirselo al shell en cada
    // refresco interno (Save, Duplicate, add/remove pool o entry, drag del
    // ResizeHandle, etc.).
    private int hostedW = 0;
    private int hostedH = 0;

    public LootTableEditorScreen() {
        super(Lang.c("gui.kve.loot.title"));
        draftPools.add(new LootTableExporter.LootPoolData());
    }

    /** Fluent setter so callers can send "Volver" back to a specific screen (defaults to KveStudioShell). */
    public LootTableEditorScreen withReturnScreen(Screen s) {
        this.returnScreen = s;
        return this;
    }

    private String pendingSelectTargetId = null;

    /** Fluent setter: precarga el filtro de busqueda con targetId, para abrir esta pantalla ya filtrada a un mob/target especifico (ver MobEditorScreen#openLootTableForMob). */
    public LootTableEditorScreen withInitialTarget(String targetId) {
        this.searchFilter = targetId;
        this.pendingSelectTargetId = targetId;
        return this;
    }

    /** Consume pendingSelectTargetId una sola vez - llamado al final de ambos init() (standalone y hosted), despues de buildWidgets(). Evita repisar un draft en edicion en refresh()s posteriores (add/remove pool, resize, etc. no vuelven a pasar por aca). */
    private void applyPendingSelectTarget() {
        if (pendingSelectTargetId == null) return;
        String id = pendingSelectTargetId;
        pendingSelectTargetId = null;
        selectEntryById(id);
    }

    @Override
    protected void init() {
        // "retorno al shell" (regla 5.1, ver Javadoc de
        // KveStudioShell#reclaimIfHosted()): si esto se esta reabriendo
        // porque un picker interno (ItemPickerScreen, RecipeTypePickerScreen,
        // etc.) hizo minecraft.setScreen(this) para "volver" y esta tab
        // sigue siendo la tab activa de un KveStudioShell vivo, reabrir el
        // shell en vez de reconstruirse standalone/centrada.
        if (KveStudioShell.reclaimIfHosted(this, returnScreen)) return;
        hosted = false;
        if (listResize == null) {
            listResize = new ResizeHandle("loot_editor.list", LIST_W_MIN, LIST_W_MAX, LIST_W_DEFAULT);
        }
        LIST_W = listResize.width();
        GUI_W = LIST_W + RIGHT_PANEL_W + SIDE_W;
        GUI_H = GUI_H_DEFAULT;
        L = (width  - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        buildWidgets();
        applyPendingSelectTarget();
    }

    @Override
    public void init(int x0, int y0, int w, int h) {
        if (this.minecraft == null) {
            this.minecraft = Minecraft.getInstance();
            this.font = this.minecraft.font;
        }
        hosted = true;
        hostedW = w;
        hostedH = h;
        if (listResize == null) {
            listResize = new ResizeHandle("loot_editor.list", LIST_W_MIN, LIST_W_MAX, LIST_W_DEFAULT);
        }
        LIST_W = listResize.width();
        // El shell puede ofrecer mas ancho que RIGHT_PANEL_W+LIST_W (panel
        // derecho crece); nunca menos, GUI_W nunca baja del minimo que el
        // formulario ya necesita (evita solapar los campos fijos de
        // buildWidgets(), que siguen asumiendo el ancho de columna actual -
        // ver 8.1 sobre por que el shell no aplasta contenido, avisa en vez
        // de eso a nivel de shell, no de cada tab).
        GUI_W = Math.max(w, LIST_W + MID_W_MIN + SIDE_W);
        GUI_H = Math.max(200, h);
        L = x0;
        T = y0;
        buildWidgets();
        applyPendingSelectTarget();
    }

    private void refresh() {
        if (listResize == null) {
            listResize = new ResizeHandle("loot_editor.list", LIST_W_MIN, LIST_W_MAX, LIST_W_DEFAULT);
        }
        LIST_W = listResize.width();
        // Mismo calculo que cada init() usa - hosted reusa el ultimo ancho
        // y alto que el shell asigno (hostedW/hostedH) en vez de
        // recentrarse standalone.
        GUI_W = hosted ? Math.max(hostedW, LIST_W + MID_W_MIN + SIDE_W) : LIST_W + RIGHT_PANEL_W + SIDE_W;
        GUI_H = hosted ? Math.max(200, hostedH) : GUI_H_DEFAULT;
        buildWidgets();
    }

    private void buildWidgets() {
        listResize.reposition(L, T + 15, T + GUI_H);
        clearWidgets();
        setFocused(null);
        CraftEditorState.loadIfNeeded();
        refreshSaved();

        searchBox = new KveScaledEditBox(font, L + 2, T + 16, LIST_W - 4, 11, Lang.c("gui.kve.loot.search_hint"));
        searchBox.setMaxLength(64);
        searchBox.setValue(searchFilter);
        searchBox.setHint(Lang.c("gui.kve.loot.search_hint"));
        searchBox.setResponder(s -> { searchFilter = s; refreshSaved(); });
        addRenderableWidget(searchBox);

        int keepSavedScroll = savedScrollBar != null ? savedScrollBar.getScroll() : 0;
        savedScrollBar = new ScrollBar(L + LIST_W - 8, T + 30, listRowsVisible() * SAVED_ROW_H, savedList.size(), listRowsVisible());
        savedScrollBar.setScroll(keepSavedScroll);
        updateSavedScroll();

        int pendCount = PendingChangeManager.size();
        int pendBtnX;
        if (!hosted) {
            String applyLbl = pendCount > 0 ? Lang.get("gui.kve.pending.apply") + " (" + pendCount + ")" : Lang.get("gui.kve.pending.apply");
            String discardLbl = pendCount > 0 ? Lang.get("gui.kve.pending.discard") + " (" + pendCount + ")" : Lang.get("gui.kve.pending.discard");
            int discardW = KveGuiStyle.btnWidth(discardLbl);
            int applyW = KveGuiStyle.btnWidth(applyLbl);
            int discardX = L + GUI_W - 2 - discardW;
            int applyX = discardX - KveGuiStyle.BTN_GAP - applyW;
            Button applyPendBtn = addRenderableWidget(btn(applyLbl, applyX, T + 2, 0, this::onApplyPending));
            applyPendBtn.active = pendCount > 0 && ClientPermissionState.canApply();
            Button discardPendBtn = addRenderableWidget(btn(discardLbl, discardX, T + 2, 0, this::onDiscardPending));
            discardPendBtn.active = pendCount > 0;
            pendBtnX = applyX - KveGuiStyle.BTN_GAP - 110;
        } else {
            pendBtnX = L + GUI_W - 2 - 110;
        }
        if (!hosted) {
            addRenderableWidget(KveGuiStyle.pendingChangesButton(pendBtnX, T + 2, 110, 11, this));
        }

        int ax = L + LIST_W + 12;
        int mr = midRight();

        addRenderableWidget(btn(Lang.get("gui.kve.loot.new"), ax, T + 2, 0, this::newEntry));

        // Abajo: Guardar + Activo + "Mas opciones" (el resto detras del toggle).
        // Se arma primero porque growUpward puede subir su borde superior, y la
        // seccion del pool seleccionado se apila justo encima.
        int by = T + GUI_H - 20;
        String deleteL = Lang.get("gui.kve.btn.delete"), dupL = Lang.get("gui.kve.btn.duplicate"),
                resetL = Lang.get("gui.kve.btn.reset"), previewL = Lang.get("gui.kve.btn.preview");
        KveGuiStyle.ButtonRow actionRow = new KveGuiStyle.ButtonRow(ax, by, sideX() - 4, true);
        addRenderableWidget(actionRow.add(0, Lang.get("gui.kve.btn.save"), b -> save()).withPrimary(true));
        KveGuiStyle.ActionBar bar = new KveGuiStyle.ActionBar(actionRow, this::addRenderableWidget);
        bar.button(Lang.get("gui.kve.btn.preview"), b -> preview());
        bar.button(Lang.get("gui.kve.btn.duplicate"), b -> duplicateTable());
        bar.enabled(draftEnabled, b -> toggleEnabled());
        bar.history(Lang.get("gui.kve.btn.undo"), () -> undo(), !currentKey.isEmpty() && LootTableExporter.canUndo(currentKey))
                .history(Lang.get("gui.kve.btn.reset"), () -> resetDraft());
        bar.end(() -> delete());
        midFlashY = actionRow.topY() - 11;

        int y = T + 17;
        tableHeaderY = y;
        y += 12;
        int halfW = (mr - ax - 4) / 2;
        addRenderableWidget(KveGuiStyle.clampedBtn(ax, y, halfW, Lang.get("gui.kve.loot.target_btn", kindLabel(draftKind)), b -> cycleTargetKind()));
        addRenderableWidget(KveGuiStyle.clampedBtn(ax + halfW + 4, y, halfW, Lang.get("gui.kve.loot.event_btn", draftEvent.label()), b -> cycleEvent()));
        y += 17;

        targetRowY = y;
        int fx = ax + MID_LABEL_W;
        int fw = mr - fx;
        if (draftKind == LootTableExporter.TargetKind.CUSTOM_TABLE) {
            String loadL = Lang.get("gui.kve.loot.load_btn");
            int loadW = KveGuiStyle.btnWidth(loadL);
            fCustomTableId = new KveScaledEditBox(font, fx, y + 1, Math.max(40, fw - loadW - 4), 11, Lang.c("gui.kve.loot.table_id_narration"));
            fCustomTableId.setMaxLength(128);
            fCustomTableId.setValue(draftTargetId);
            fCustomTableId.setHint(Lang.c("gui.kve.loot.table_id_hint"));
            fCustomTableId.setResponder(s -> draftTargetId = s.trim());
            addRenderableWidget(fCustomTableId);
            addRenderableWidget(btn(loadL, mr - loadW, y, 0,
                    () -> { if (!draftTargetId.isBlank()) onTargetPicked(draftTargetId); }));
        } else {
            fCustomTableId = null;
            addRenderableWidget(KveGuiStyle.clampedBtn(fx, y, fw,
                    draftTargetId.isEmpty() ? Lang.get("gui.kve.loot.choose_ellipsis") : draftTargetId, b -> pickTarget()));
        }
        y += 16;

        categoryRowY = y;
        fCategory = new KveScaledEditBox(font, fx, y + 1, Math.min(fw, 160), 11, Lang.c("gui.kve.common.category"));
        fCategory.setMaxLength(24);
        fCategory.setValue(draftCategory);
        fCategory.setHint(Lang.c("gui.kve.common.category_hint"));
        fCategory.setResponder(s -> draftCategory = s);
        addRenderableWidget(fCategory);
        y += 19;

        poolsHeaderY = y;
        String templateL = Lang.get("gui.kve.loot.template_btn");
        int templateW = KveGuiStyle.btnWidth(templateL);
        int addPoolW = KveGuiStyle.btnWidth("+ Pool");
        poolsHeaderButtonsX = mr - templateW - KveGuiStyle.BTN_GAP - addPoolW;
        addRenderableWidget(btn("+ Pool", poolsHeaderButtonsX, y - 3, 0, this::addPool));
        addRenderableWidget(btn(templateL, mr - templateW, y - 3, 0, this::pickTemplate));
        y += 14;

        String simL = Lang.get("gui.kve.loot.simulate_btn");
        int simW = KveGuiStyle.btnWidth(simL);
        entrySearchBox = new KveScaledEditBox(font, ax, y + 1, Math.max(40, mr - ax - simW - 4), 11, Lang.c("gui.kve.loot.entry_search_hint"));
        entrySearchBox.setMaxLength(64);
        entrySearchBox.setValue(entrySearchFilter);
        entrySearchBox.setHint(Lang.c("gui.kve.loot.entry_search_hint"));
        entrySearchBox.setResponder(s -> {
            entrySearchFilter = s;
            entryScroll = 0;
        });
        addRenderableWidget(entrySearchBox);
        addRenderableWidget(btn(simL, mr - simW, y, 0, this::openSimulator));
        y += 17;
        treeTop = y;

        LootTableExporter.LootPoolData pool = currentPool();
        boolean needsChance = pool.condition == LootTableExporter.Condition.RANDOM_CHANCE
                || pool.condition == LootTableExporter.Condition.RANDOM_CHANCE_WITH_LOOTING;
        boolean needsLooting = pool.condition == LootTableExporter.Condition.RANDOM_CHANCE_WITH_LOOTING;
        String condL = pool.condition.label();
        int rollsFieldX = ax + labelWidth("gui.kve.loot.rolls_label");
        int condX = rollsFieldX + 34;
        int condW = Math.min(KveGuiStyle.btnWidth(condL), mr - condX);
        int chanceGroupW = needsChance ? labelWidth("gui.kve.loot.chance_label") + 40
                + (needsLooting ? 6 + labelWidth("gui.kve.loot.loot_pct_label") + 40 : 0) : 0;
        boolean chanceOwnRow = needsChance && condX + condW + 8 + chanceGroupW > mr;
        int poolRowsH = chanceOwnRow ? 32 : 16;
        rollsRowY = midFlashY - 2 - poolRowsH;
        poolHeaderY = rollsRowY - 13;

        fRolls = new KveScaledEditBox(font, rollsFieldX, rollsRowY + 1, 30, 11, Lang.c("gui.kve.loot.rolls_narration"));
        fRolls.setMaxLength(4);
        fRolls.setValue(String.valueOf(pool.rolls));
        addRenderableWidget(fRolls);
        addRenderableWidget(KveGuiStyle.clampedBtn(condX, rollsRowY, condW, condL, b -> cycleCondition()));

        if (needsChance) {
            chanceRowY = chanceOwnRow ? rollsRowY + 16 : rollsRowY;
            chanceLabelX = chanceOwnRow ? ax : condX + condW + 8;
            int chanceFieldX = chanceLabelX + labelWidth("gui.kve.loot.chance_label");
            fChance = new KveScaledEditBox(font, chanceFieldX, chanceRowY + 1, 36, 11, Lang.c("gui.kve.loot.chance_narration"));
            fChance.setMaxLength(6);
            fChance.setValue(String.valueOf(pool.chance));
            addRenderableWidget(fChance);
            if (needsLooting) {
                lootingLabelX = chanceFieldX + 36 + 6;
                fLootingMult = new KveScaledEditBox(font, lootingLabelX + labelWidth("gui.kve.loot.loot_pct_label"), chanceRowY + 1, 36, 11,
                        Lang.c("gui.kve.loot.looting_narration"));
                fLootingMult.setMaxLength(6);
                fLootingMult.setValue(String.valueOf(pool.lootingMultiplier));
                addRenderableWidget(fLootingMult);
            } else {
                fLootingMult = null;
            }
        } else {
            fChance = null;
            fLootingMult = null;
        }

        treeRowsVisible = Math.max(1, (poolHeaderY - 4 - treeTop) / TREE_ROW_H);

        buildSideEntryWidgets();
    }

    private void buildSideEntryWidgets() {
        sWeight = null; sMin = null; sMax = null; sLevelsMin = null; sLevelsMax = null; sNbt = null;
        LootTableExporter.LootEntryData sel = selectedEntry();
        if (sel == null) return;

        int scx = sideX() + 6;
        int scw = SIDE_W - 11;
        if (sideFnIdx >= sel.functions.size()) sideFnIdx = Math.max(0, sel.functions.size() - 1);

        int itemY = T + 29;
        addRenderableWidget(KveGuiStyle.clampedBtn(scx, itemY, scw, sideItemLabel(sel), b -> pickSideItem()));

        sWeightY = itemY + 17;
        int weightX = scx + labelWidth("gui.kve.loot.side_weight_label");
        sWeight = new KveScaledEditBox(font, weightX, sWeightY, Math.min(60, scx + scw - weightX), 11, Lang.c("gui.kve.loot.side_weight_narration"));
        sWeight.setMaxLength(6); sWeight.setValue(String.valueOf(sel.weight));
        addRenderableWidget(sWeight);

        sMinMaxY = sWeightY + 14;
        int half = (scw - 6) / 2;
        int minFieldX = scx + labelWidth("gui.kve.loot.side_min_label");
        sMaxLabelX = scx + half + 6;
        int maxFieldX = sMaxLabelX + labelWidth("gui.kve.loot.side_max_label");
        sMin = new KveScaledEditBox(font, minFieldX, sMinMaxY, Math.max(16, scx + half - minFieldX), 11, Lang.c("gui.kve.loot.side_min_narration"));
        sMin.setMaxLength(6); sMin.setValue(String.valueOf(sel.minCount));
        addRenderableWidget(sMin);
        sMax = new KveScaledEditBox(font, maxFieldX, sMinMaxY, Math.max(16, scx + scw - maxFieldX), 11, Lang.c("gui.kve.loot.side_max_narration"));
        sMax.setMaxLength(6); sMax.setValue(String.valueOf(sel.maxCount));
        addRenderableWidget(sMax);

        sFnHeaderY = sMinMaxY + 19;
        int pagerY = sFnHeaderY - 3;
        int right = scx + scw;
        if (!sel.functions.isEmpty()) addRenderableWidget(btn("-", right - 12, pagerY, 12, this::removeSideFn));
        addRenderableWidget(btn("+", right - 26, pagerY, 12, this::addSideFn));
        addRenderableWidget(btn(">", right - 40, pagerY, 12, this::nextSideFn));
        addRenderableWidget(btn("<", right - 54, pagerY, 12, this::prevSideFn));

        sFnTypeY = sFnHeaderY + 13;
        int fnTypeY = sFnTypeY;
        if (!sel.functions.isEmpty()) {
            LootTableExporter.LootFunctionData fn = sel.functions.get(sideFnIdx);
            addRenderableWidget(KveGuiStyle.clampedBtn(scx, fnTypeY, scw, fn.type.label(), b -> cycleSideFnType()));
            int fnExtraY = fnTypeY + 15;
            switch (fn.type) {
                case ENCHANT_WITH_LEVELS -> {
                    sLevelsMin = new KveScaledEditBox(font, scx, fnExtraY, (scw - 4) / 2, 11, Component.empty());
                    sLevelsMin.setMaxLength(4); sLevelsMin.setValue(String.valueOf(fn.levelsMin));
                    addRenderableWidget(sLevelsMin);
                    sLevelsMax = new KveScaledEditBox(font, scx + (scw - 4) / 2 + 4, fnExtraY, (scw - 4) / 2, 11, Component.empty());
                    sLevelsMax.setMaxLength(4); sLevelsMax.setValue(String.valueOf(fn.levelsMax));
                    addRenderableWidget(sLevelsMax);
                    addRenderableWidget(btn(fn.treasure
                            ? Lang.get("gui.kve.loot.side_treasure_on") : Lang.get("gui.kve.loot.side_treasure_off"),
                            scx, fnExtraY + 13, 0, this::toggleSideTreasure));
                }
                case LOOTING_ENCHANT -> {
                    sLevelsMin = new KveScaledEditBox(font, scx, fnExtraY, (scw - 4) / 2, 11, Component.empty());
                    sLevelsMin.setMaxLength(4); sLevelsMin.setValue(String.valueOf(fn.levelsMin));
                    addRenderableWidget(sLevelsMin);
                    sLevelsMax = new KveScaledEditBox(font, scx + (scw - 4) / 2 + 4, fnExtraY, (scw - 4) / 2, 11, Component.empty());
                    sLevelsMax.setMaxLength(4); sLevelsMax.setValue(String.valueOf(fn.levelsMax));
                    addRenderableWidget(sLevelsMax);
                }
                case SET_NBT -> {
                    sNbt = new KveScaledEditBox(font, scx, fnExtraY, scw, 11, Component.empty());
                    sNbt.setMaxLength(256); sNbt.setValue(fn.nbt);
                    addRenderableWidget(sNbt);
                }
                case SMELT -> addRenderableWidget(btn(fn.smeltOnlyIfOnFire
                        ? Lang.get("gui.kve.loot.side_fire_on") : Lang.get("gui.kve.loot.side_fire_off"),
                        scx, fnExtraY, 0, this::toggleSideSmeltOnFire));
            }
        }

        int condY = sideCondY() + 11;
        int n = sel.conditions == null ? 0 : sel.conditions.size();
        for (int i = 0; i < Math.min(n, SIDE_COND_ROWS); i++) {
            final int idx = i;
            addRenderableWidget(btn("x", scx + scw - 12, condY + i * 12, 12, () -> removeSideCondition(idx)));
        }
        int addY = condY + Math.min(n, SIDE_COND_ROWS) * 12 + (n > SIDE_COND_ROWS ? 10 : 0) + 1;
        String presetText = LootConditionText.one(LootConditionText.PRESETS.get(sideCondPreset).create());
        addRenderableWidget(KveGuiStyle.clampedBtn(scx, addY, scw - 16, presetText, b -> cycleSideCondPreset()));
        addRenderableWidget(btn("+", scx + scw - 14, addY, 14, this::addSideCondition));
    }

    private static final class TreeRow {
        final int poolIdx;
        final LootTableExporter.LootEntryData entry;
        TreeRow(int poolIdx, LootTableExporter.LootEntryData entry) { this.poolIdx = poolIdx; this.entry = entry; }
        boolean isHeader() { return entry == null; }
    }

    private List<TreeRow> buildTreeRows() {
        List<TreeRow> rows = new ArrayList<>();
        for (int pi = 0; pi < draftPools.size(); pi++) {
            rows.add(new TreeRow(pi, null));
            LootTableExporter.LootPoolData p = draftPools.get(pi);
            for (LootTableExporter.LootEntryData e : p.entries) {
                if (entrySearchFilter != null && !entrySearchFilter.isBlank()
                        && !SearchFilter.matches(e.itemId, entrySearchFilter)) continue;
                rows.add(new TreeRow(pi, e));
            }
        }
        return rows;
    }

    private int totalEntryCount() {
        int n = 0;
        for (LootTableExporter.LootPoolData p : draftPools) n += p.entries.size();
        return n;
    }

    private int tableHeaderY, targetRowY, categoryRowY, poolsHeaderY, poolsHeaderButtonsX;
    private int treeTop, treeRowsVisible = 1, poolHeaderY, rollsRowY, chanceRowY, chanceLabelX, lootingLabelX, midFlashY;
    private static final int MID_LABEL_W = 62;

    private int midRight() { return sideX() - 8; }

    private int labelWidth(String key) {
        return Math.round(font.width(Lang.get(key)) * KveGuiStyle.uiTextScale()) + 4;
    }

    private void sectionHeader(GuiGraphics g, String text, int x, int y, int w) {
        KveGuiStyle.drawTextSafe(g, text, x, y, w, KveGuiStyle.ACCENT_GOLD);
        int tw = Math.min(w, Math.round(font.width(text) * KveGuiStyle.uiTextScale()));
        if (tw + 6 < w) g.fill(x + tw + 4, y + 4, x + w, y + 5, KveGuiStyle.BORDER_SUBTLE);
    }

    private int treeXBtn() { return midRight() - 12; }
    private int treeDotsBtn() { return midRight() - 26; }
    private int treeWeightX() { return midRight() - 56; }
    private int treeCountX() { return midRight() - 88; }

    private int sideX() { return L + GUI_W - SIDE_W; }

    private LootTableExporter.LootEntryData selectedEntry() {
        if (selectedPoolIdx < 0 || selectedPoolIdx >= draftPools.size()) return null;
        List<LootTableExporter.LootEntryData> entries = draftPools.get(selectedPoolIdx).entries;
        if (selectedEntryIdx < 0 || selectedEntryIdx >= entries.size()) return null;
        return entries.get(selectedEntryIdx);
    }

    private LootTableExporter.LootPoolData currentPool() {
        if (draftPools.isEmpty()) draftPools.add(new LootTableExporter.LootPoolData());
        if (currentPoolIdx >= draftPools.size()) currentPoolIdx = draftPools.size() - 1;
        if (currentPoolIdx < 0) currentPoolIdx = 0;
        return draftPools.get(currentPoolIdx);
    }

    /**
     * Resolves a loot entry's raw {@code itemId} to a real registered
     * {@link Item} for the tree row's icon render - null (no icon drawn,
     * name text only) for anything that doesn't resolve, same
     * fail-quiet contract {@code resolveInlineItem} already uses in
     * {@link KveGuiStyle}: a blank/malformed/still-being-typed id is a
     * normal mid-edit state here, not something worth a warning.
     */
    private static Item resolveEntryIcon(String itemId) {
        if (itemId == null || itemId.isBlank()) return null;
        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        if (rl == null) return null;
        return BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
    }

    private String kindLabel(LootTableExporter.TargetKind k) {
        return switch (k) {
            case ENTITY -> Lang.get("gui.kve.common.type.entity");
            case BLOCK -> Lang.get("gui.kve.common.type.block");
            case CUSTOM_TABLE -> Lang.get("gui.kve.loot.kind.table");
        };
    }

    private static void drawTargetPreview(GuiGraphics g, LootTableExporter.LootTableEntry e, int x, int y, int w, int h) {
        String id = e.targetId == null ? "" : e.targetId.trim();
        String entityId = null;
        Item icon = null;
        switch (e.kind) {
            case ENTITY -> entityId = id;
            case BLOCK -> icon = MiniItemIcon.block(id);
            default -> {
                int c = id.indexOf(':');
                String ns = c > 0 ? id.substring(0, c) : "minecraft";
                String path = c > 0 ? id.substring(c + 1) : id;
                if (path.startsWith("entities/")) entityId = ns + ":" + path.substring("entities/".length());
                else if (path.startsWith("blocks/")) icon = MiniItemIcon.block(ns + ":" + path.substring("blocks/".length()));
                else if (path.contains("chests/")) icon = net.minecraft.world.item.Items.CHEST;
                else if (path.contains("fishing")) icon = net.minecraft.world.item.Items.FISHING_ROD;
                else if (path.contains("archaeology")) icon = net.minecraft.world.item.Items.BRUSH;
                else if (path.contains("hero_of_the_village")) icon = net.minecraft.world.item.Items.EMERALD;
                else if (path.contains("bartering")) icon = net.minecraft.world.item.Items.GOLD_INGOT;
                else if (path.contains("sniffer")) icon = net.minecraft.world.item.Items.TORCHFLOWER_SEEDS;
                else if (path.contains("cat_morning_gift")) icon = net.minecraft.world.item.Items.STRING;
                else icon = net.minecraft.world.item.Items.PAPER;
            }
        }
        if (entityId != null) {
            TradeEntityPreview.drawEntity(g, entityId, x, y, w, h);
        } else if (icon != null) {
            MiniItemIcon.draw(g, x + (w - 12) / 2, y + (h - 12) / 2, 12, icon);
        }
    }

    private String shortId(String id) {
        String n = id.contains(":") ? id.split(":")[1] : id;
        return KveGuiStyle.truncate(font, n, 118);
    }

    private void syncFieldsToDraft() {
        syncSideFieldsToEntry();
        LootTableExporter.LootPoolData pool = currentPool();
        try { pool.rolls = Math.max(1, Integer.parseInt(fRolls.getValue().trim())); } catch (Exception ignored) {}
        if (fChance != null) {
            try { pool.chance = Math.max(0.0, Math.min(1.0, Double.parseDouble(fChance.getValue().trim()))); }
            catch (Exception ignored) {}
        }
        if (fLootingMult != null) {
            try { pool.lootingMultiplier = Math.max(0.0, Double.parseDouble(fLootingMult.getValue().trim())); }
            catch (Exception ignored) {}
        }
        if (fCustomTableId != null) draftTargetId = fCustomTableId.getValue().trim();
    }

    private void cycleTargetKind() {
        LootTableExporter.TargetKind[] values = LootTableExporter.TargetKind.values();
        draftKind = values[(draftKind.ordinal() + 1) % values.length];
        if (!draftEvent.validFor(draftKind)) draftEvent = LootTableExporter.TriggerEvent.firstValidFor(draftKind);
        draftTargetId = "";
        refresh();
    }

    private void cycleEvent() {
        LootTableExporter.TriggerEvent[] values = LootTableExporter.TriggerEvent.values();
        LootTableExporter.TriggerEvent next = draftEvent;
        for (int i = 0; i < values.length; i++) {
            next = values[(next.ordinal() + 1) % values.length];
            if (next.validFor(draftKind)) break;
        }
        draftEvent = next;
        refresh();
    }

    private void pickTarget() {
        if (draftKind == LootTableExporter.TargetKind.ENTITY) {
            minecraft.setScreen(new MobPickerScreen(this, et -> {
                ResourceLocation k = BuiltInRegistries.ENTITY_TYPE.getKey(et);
                if (k != null) onTargetPicked(k.toString());
            }));
        } else if (draftKind == LootTableExporter.TargetKind.BLOCK) {
            minecraft.setScreen(new ItemPickerScreen(this, stack -> {
                ResourceLocation k = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (k == null) return;
                if (BuiltInRegistries.BLOCK.containsKey(k)) {
                    onTargetPicked(k.toString());
                } else {
                    flash(Lang.get("gui.kve.loot.no_block_for_item", k.toString()));
                }
            }));
        }
    }

    private void onTargetPicked(String targetId) {
        draftTargetId = targetId;

        String key = draftKind.name() + "|" + draftEvent.name() + "|" + targetId;
        LootTableExporter.LootTableEntry existing = LootTableExporter.getEntry(key);
        if (existing != null) {
            loadDraft(existing);
            flash(Lang.get("gui.kve.loot.loaded_saved_edit", shortId(targetId)));
            return;
        }

        LootTableExporter.LootTableEntry current =
                LootTableExporter.tryLoadCurrentLootTable(draftKind, draftEvent, targetId);
        if (current != null && !current.pools.isEmpty()) {
            draftCategory = "";
            draftEnabled = true;
            draftPools.clear();
            for (LootTableExporter.LootPoolData p : current.pools) draftPools.add(p.copy());
            currentPoolIdx = 0; entryScroll = 0;
            flash(Lang.get("gui.kve.loot.loaded_current", shortId(targetId)));
        } else {
            draftPools.clear();
            draftPools.add(new LootTableExporter.LootPoolData());
            currentPoolIdx = 0; entryScroll = 0;
            if (draftEvent.isDatapackBacked()) {
                flash(Lang.get("gui.kve.loot.load_failed"));
            }
        }
        refresh();
    }

    private void pickTemplate() {
        List<String> ids = LootTableExporter.listAllLootTableIds();
        if (ids.isEmpty()) {
            flash(Lang.get("gui.kve.loot.template_none_available"));
            return;
        }
        minecraft.setScreen(new LootTablePickerScreen(this, ids, this::onTemplatePicked));
    }

    private void onTemplatePicked(String tableId) {
        boolean hasContent = draftPools.size() > 1
                || draftPools.stream().anyMatch(p -> !p.entries.isEmpty());
        if (hasContent) {
            minecraft.setScreen(new KveConfirmScreen(
                    confirmed -> {
                        minecraft.setScreen(this);
                        if (confirmed) applyTemplate(tableId);
                    },
                    Component.literal(Lang.get("gui.kve.loot.template_confirm_title")),
                    Component.literal(Lang.get("gui.kve.loot.template_confirm_msg", shortId(tableId)))));
            return;
        }
        applyTemplate(tableId);
    }

    private void applyTemplate(String tableId) {
        LootTableExporter.LootTableEntry template = LootTableExporter.tryLoadCurrentLootTable(
                LootTableExporter.TargetKind.CUSTOM_TABLE, LootTableExporter.TriggerEvent.DIRECT, tableId);
        if (template == null || template.pools.isEmpty()) {
            flash(Lang.get("gui.kve.loot.template_load_failed", shortId(tableId)));
            return;
        }
        draftPools.clear();
        for (LootTableExporter.LootPoolData p : template.pools) draftPools.add(p.copy());
        currentPoolIdx = 0; entryScroll = 0;
        flash(Lang.get("gui.kve.loot.template_loaded", shortId(tableId)));
        refresh();
    }

    private void openSimulator() {
        syncFieldsToDraft();
        LootTableExporter.LootTableEntry snapshot = new LootTableExporter.LootTableEntry();
        snapshot.kind = draftKind;
        snapshot.event = draftEvent;
        snapshot.targetId = draftTargetId;
        snapshot.enabled = draftEnabled;
        snapshot.category = draftCategory;
        for (LootTableExporter.LootPoolData p : draftPools) snapshot.pools.add(p.copy());
        minecraft.setScreen(new LootSimulatorScreen(this, snapshot));
    }

    private void toggleEnabled() { draftEnabled = !draftEnabled; refresh(); }

    private void addPool() { syncFieldsToDraft(); draftPools.add(new LootTableExporter.LootPoolData()); currentPoolIdx = draftPools.size() - 1; entryScroll = 0; refresh(); }

    private void removePoolAt(int idx) {
        if (draftPools.size() <= 1 || idx < 0 || idx >= draftPools.size()) return;
        syncFieldsToDraft();
        draftPools.remove(idx);
        if (currentPoolIdx >= draftPools.size()) currentPoolIdx = draftPools.size() - 1;
        else if (currentPoolIdx > idx) currentPoolIdx--;
        if (selectedPoolIdx == idx) {
            selectedPoolIdx = -1; selectedEntryIdx = -1;
        } else if (selectedPoolIdx > idx) {
            selectedPoolIdx--;
        }
        entryScroll = 0;
        refresh();
    }

    private void selectPool(int idx) {
        if (idx == currentPoolIdx) return;
        syncFieldsToDraft();
        currentPoolIdx = idx;
        refresh();
    }

    private void cycleCondition() {
        syncFieldsToDraft();
        LootTableExporter.LootPoolData pool = currentPool();
        pool.condition = pool.condition.next();
        refresh();
    }

    private void addEntryToPool(int poolIdx) {
        syncFieldsToDraft();
        if (poolIdx < 0 || poolIdx >= draftPools.size()) return;
        LootTableExporter.LootPoolData targetPool = draftPools.get(poolIdx);
        LootTableExporter.LootEntryData blank = new LootTableExporter.LootEntryData();
        targetPool.entries.add(blank);
        selectedPoolIdx = poolIdx;
        selectedEntryIdx = targetPool.entries.size() - 1;
        sideFnIdx = 0;
        refresh();
    }

    private void selectEntry(int poolIdx, LootTableExporter.LootEntryData entry) {
        syncSideFieldsToEntry();
        selectedPoolIdx = poolIdx;
        selectedEntryIdx = draftPools.get(poolIdx).entries.indexOf(entry);
        sideFnIdx = 0;
        refresh();
    }

    /** Vuelca sWeight/sMin/sMax + los campos extra de la function actual del pager a la entrada seleccionada - mismo rol que LootEntryEditScreen#syncCurrentFn() pero tambien cubre weight/min/max (esa Screen los aplicaba solo en confirm(), aca no hay "confirmar" separado: se aplica en cada punto donde el resto del draft ya se sincroniza, ver syncFieldsToDraft()). No valida (min<=max, weight>=1) aca a proposito - la validacion "dura" (bloquear con flash) vive en save(), igual que el resto del draft; esto es solo "no perder lo tipeado". */
    private void syncSideFieldsToEntry() {
        LootTableExporter.LootEntryData sel = selectedEntry();
        if (sel == null) return;
        if (sWeight != null) { try { sel.weight = Integer.parseInt(sWeight.getValue().trim()); } catch (Exception ignored) {} }
        if (sMin != null) { try { sel.minCount = Integer.parseInt(sMin.getValue().trim()); } catch (Exception ignored) {} }
        if (sMax != null) { try { sel.maxCount = Integer.parseInt(sMax.getValue().trim()); } catch (Exception ignored) {} }
        if (!sel.functions.isEmpty() && sideFnIdx < sel.functions.size()) {
            LootTableExporter.LootFunctionData fn = sel.functions.get(sideFnIdx);
            if (sLevelsMin != null) { try { fn.levelsMin = Integer.parseInt(sLevelsMin.getValue().trim()); } catch (Exception ignored) {} }
            if (sLevelsMax != null) { try { fn.levelsMax = Integer.parseInt(sLevelsMax.getValue().trim()); } catch (Exception ignored) {} }
            if (sNbt != null) fn.nbt = sNbt.getValue();
        }
    }

    private String sideItemLabel(LootTableExporter.LootEntryData sel) {
        if (sel.itemId == null || sel.itemId.isEmpty()) return Lang.get("gui.kve.loot.side_pick_item_btn");
        String n = sel.itemId.contains(":") ? sel.itemId.split(":")[1] : sel.itemId;
        return n;
    }

    private void pickSideItem() {
        syncSideFieldsToEntry();
        LootTableExporter.LootEntryData sel = selectedEntry();
        if (sel == null) return;
        minecraft.setScreen(new ItemPickerScreen(this, stack -> {
            ResourceLocation k = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (k != null) sel.itemId = k.toString();
            refresh();
        }));
    }

    /**
     * Shortcut del boton "..." de cada fila de entry en el arbol (ver
     * comentario en mouseClicked()): selecciona esa entry en la columna
     * SIDE y de una abre el picker de item para ella, en vez de forzar al
     * jugador a clickear la fila primero y despues ir a buscar el boton de
     * item ya dentro de SIDE. selectEntry() deja selectedPoolIdx/
     * selectedEntryIdx apuntando exactamente a {@code entry} (via
     * indexOf(), mismo mecanismo que el resto de esta pantalla usa para
     * resolver la seleccion - ver el comentario de esos 2 campos), asi que
     * reusar pickSideItem() tal cual (selectedEntry() ya resuelve a la
     * entry correcta) evita duplicar la logica del picker/callback.
     */
    private void quickPickEntryItem(int poolIdx, LootTableExporter.LootEntryData entry) {
        selectEntry(poolIdx, entry);
        pickSideItem();
    }

    private void prevSideFn() { syncSideFieldsToEntry(); sideFnIdx = Math.max(0, sideFnIdx - 1); refresh(); }
    private void nextSideFn() {
        syncSideFieldsToEntry();
        LootTableExporter.LootEntryData sel = selectedEntry();
        int max = sel == null ? 0 : Math.max(0, sel.functions.size() - 1);
        sideFnIdx = Math.min(max, sideFnIdx + 1);
        refresh();
    }
    private void addSideFn() {
        syncSideFieldsToEntry();
        LootTableExporter.LootEntryData sel = selectedEntry();
        if (sel == null) return;
        sel.functions.add(new LootTableExporter.LootFunctionData());
        sideFnIdx = sel.functions.size() - 1;
        refresh();
    }
    private void removeSideFn() {
        syncSideFieldsToEntry();
        LootTableExporter.LootEntryData sel = selectedEntry();
        if (sel == null || sel.functions.isEmpty()) return;
        sel.functions.remove(sideFnIdx);
        sideFnIdx = Math.max(0, sideFnIdx - 1);
        refresh();
    }
    private void cycleSideFnType() {
        syncSideFieldsToEntry();
        LootTableExporter.LootEntryData sel = selectedEntry();
        if (sel == null || sel.functions.isEmpty()) return;
        LootTableExporter.LootFunctionData fn = sel.functions.get(sideFnIdx);
        fn.type = fn.type.next();
        refresh();
    }

    private static int sideCondPreset = 0;
    private static final int SIDE_COND_ROWS = 4;

    private int sideCondY() { return sFnTypeY + 15 + 13 + 18; }

    private int sWeightY, sMinMaxY, sMaxLabelX, sFnHeaderY, sFnTypeY;

    private void removeSideCondition(int i) {
        syncSideFieldsToEntry();
        LootTableExporter.LootEntryData sel = selectedEntry();
        if (sel == null || sel.conditions == null || i < 0 || i >= sel.conditions.size()) return;
        sel.conditions.remove(i);
        if (sel.conditions.isEmpty()) sel.conditions = null;
        refresh();
    }

    private void cycleSideCondPreset() {
        syncSideFieldsToEntry();
        sideCondPreset = (sideCondPreset + 1) % LootConditionText.PRESETS.size();
        refresh();
    }

    private void addSideCondition() {
        syncSideFieldsToEntry();
        LootTableExporter.LootEntryData sel = selectedEntry();
        if (sel == null) return;
        if (sel.conditions == null) sel.conditions = new com.google.gson.JsonArray();
        sel.conditions.add(LootConditionText.PRESETS.get(sideCondPreset).create());
        refresh();
    }

    private void toggleSideTreasure() {
        syncSideFieldsToEntry();
        LootTableExporter.LootEntryData sel = selectedEntry();
        if (sel == null || sel.functions.isEmpty()) return;
        LootTableExporter.LootFunctionData fn = sel.functions.get(sideFnIdx);
        fn.treasure = !fn.treasure;
        refresh();
    }
    private void toggleSideSmeltOnFire() {
        syncSideFieldsToEntry();
        LootTableExporter.LootEntryData sel = selectedEntry();
        if (sel == null || sel.functions.isEmpty()) return;
        LootTableExporter.LootFunctionData fn = sel.functions.get(sideFnIdx);
        fn.smeltOnlyIfOnFire = !fn.smeltOnlyIfOnFire;
        refresh();
    }

    private void newEntry() {
        currentKey = "";
        draftKind = LootTableExporter.TargetKind.ENTITY;
        draftEvent = LootTableExporter.TriggerEvent.DEATH;
        draftTargetId = ""; draftCategory = ""; draftEnabled = true;
        draftPools.clear(); draftPools.add(new LootTableExporter.LootPoolData());
        currentPoolIdx = 0; entryScroll = 0; entrySearchFilter = "";
        refresh();
    }

    private void duplicateTable() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }
        currentKey = "";
        draftTargetId = "";
        if (fCustomTableId != null) fCustomTableId.setValue("");
        flash(Lang.get("gui.kve.loot.duplicated_hint"));
        refresh();
    }

    private void resetDraft() {
        if (currentKey.isEmpty()) { newEntry(); return; }
        LootTableExporter.LootTableEntry e = LootTableExporter.getEntry(currentKey);
        if (e != null) loadDraft(e); else newEntry();
    }

    public void selectEntryById(String targetId) {
        if (targetId == null || targetId.isBlank()) return;
        String key = LootTableExporter.TargetKind.ENTITY.name() + "|"
                + LootTableExporter.TriggerEvent.DEATH.name() + "|" + targetId;
        LootTableExporter.LootTableEntry e = LootTableExporter.getEntry(key);
        if (e != null) {
            loadDraft(e);
            return;
        }
        searchFilter = targetId;

        draftKind = LootTableExporter.TargetKind.ENTITY;
        draftEvent = LootTableExporter.TriggerEvent.DEATH;
        draftTargetId = targetId;
        currentKey = "";

        LootTableExporter.LootTableEntry current =
                LootTableExporter.tryLoadCurrentLootTable(draftKind, draftEvent, targetId);
        draftPools.clear();
        if (current != null && !current.pools.isEmpty()) {
            for (LootTableExporter.LootPoolData p : current.pools) draftPools.add(p.copy());
            flash(Lang.get("gui.kve.loot.loaded_current", shortId(targetId)));
        } else {
            draftPools.add(new LootTableExporter.LootPoolData());
        }
        currentPoolIdx = 0; entryScroll = 0;
        refresh();
    }

    private void loadDraft(LootTableExporter.LootTableEntry e) {
        currentKey = e.key();
        draftKind = e.kind;
        draftEvent = e.event;
        draftTargetId = e.targetId;
        draftCategory = e.category;
        draftEnabled = e.enabled;
        draftPools.clear();
        for (LootTableExporter.LootPoolData p : e.pools) draftPools.add(p.copy());
        if (draftPools.isEmpty()) draftPools.add(new LootTableExporter.LootPoolData());
        currentPoolIdx = 0; entryScroll = 0;
        refresh();
    }

    private LootTableExporter.LootTableEntry buildDraftLootEntry() {
        syncFieldsToDraft();
        LootTableExporter.LootTableEntry e = new LootTableExporter.LootTableEntry();
        e.kind = draftKind;
        e.event = draftEvent;
        e.targetId = draftTargetId;
        e.category = draftCategory.trim();
        e.enabled = draftEnabled;
        for (LootTableExporter.LootPoolData p : draftPools) e.pools.add(p.copy());
        return e;
    }

    private void save() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }
        LootTableExporter.LootTableEntry e = buildDraftLootEntry();

        List<String> problems = LootTableExporter.validateLootTable(e);
        if (!problems.isEmpty()) { flash(String.join(" ", problems)); return; }

        String oldKey = (!currentKey.isEmpty() && !currentKey.equals(e.key())) ? currentKey : null;
        LootTableExporter.LootTableEntry previousUnderOldKey = oldKey != null ? LootTableExporter.getEntry(oldKey) : null;
        LootTableExporter.LootTableEntry previous = LootTableExporter.getEntry(e.key());
        PendingChange.Action action = previous == null ? PendingChange.Action.CREATE : PendingChange.Action.EDIT;

        if (oldKey != null) LootTableExporter.stageDeleteLootTable(oldKey);
        LootTableExporter.stageLootTable(e);

        PendingChangeManager.queue(PendingChange.of(PendingDomain.LOOT, e.key(), action, false,
                () -> {
                    LootTableExporter.commitLootTables();
                    KveChangeLog.record(KveChangeLog.Log.LOOT, action, e.key());
                },
                () -> {
                    LootTableExporter.discardStagedLootTable(e.key(), previous);
                    if (oldKey != null) LootTableExporter.discardStagedLootTable(oldKey, previousUnderOldKey);
                    refreshSaved();
                })
                .withDiff(previous == null ? null : () -> LootTableExporter.previewLoot(previous), () -> LootTableExporter.previewLoot(e))
                .withMode(lootApplyMode(e)));

        currentKey = e.key();
        boolean hasFunctions = e.event == LootTableExporter.TriggerEvent.INTERACT
                && e.pools.stream().flatMap(p -> p.entries.stream()).anyMatch(en -> !en.functions.isEmpty());
        String hint = com.kve.client.gui.workspace.pending.ApplyFeedback.queuedHint(lootApplyMode(e));
        flash(hasFunctions ? hint + Lang.get("gui.kve.loot.interact_functions_note") : hint);
        refreshSaved();
        refresh();
    }

    private com.kve.client.gui.workspace.pending.ApplyMode lootApplyMode(LootTableExporter.LootTableEntry e) {
        boolean live = e.event == LootTableExporter.TriggerEvent.INTERACT && minecraft != null && minecraft.isLocalServer();
        return live ? com.kve.client.gui.workspace.pending.ApplyMode.LIVE : com.kve.client.gui.workspace.pending.ApplyMode.RELOAD;
    }

    private void preview() {
        LootTableExporter.LootTableEntry e = buildDraftLootEntry();
        if (e.targetId.isEmpty()) { flash(Lang.get("gui.kve.loot.choose_target_first")); return; }
        String script = LootTableExporter.previewLoot(e);
        minecraft.setScreen(new RecipePreviewScreen(this, e.targetId, script));
    }

    private void delete() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }
        if (currentKey.isEmpty()) { flash(Lang.get("gui.kve.loot.select_saved_first")); return; }
        String key = currentKey;
        LootTableExporter.LootTableEntry previous = LootTableExporter.getEntry(key);
        LootTableExporter.stageDeleteLootTable(key);
        PendingChangeManager.queue(PendingChange.of(PendingDomain.LOOT, key, PendingChange.Action.DELETE, false,
                () -> {
                    LootTableExporter.commitLootTables();
                    KveChangeLog.record(KveChangeLog.Log.LOOT, PendingChange.Action.DELETE, key);
                },
                () -> {
                    if (previous != null) LootTableExporter.stageLootTable(previous);
                    refreshSaved();
                })
                .withDiff(previous == null ? null : () -> LootTableExporter.previewLoot(previous), null));
        flash(Lang.get("gui.kve.pending.queued_hint_reload"));
        newEntry();
        refreshSaved();
    }

    private void undo() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }
        if (currentKey.isEmpty()) return;
        String key = currentKey;
        LootTableExporter.LootTableEntry beforeUndo = LootTableExporter.getEntry(key);
        LootTableExporter.LootTableEntry restored = LootTableExporter.stageUndo(key);
        if (restored != null) {
            loadDraft(restored);
            PendingChangeManager.queue(PendingChange.of(PendingDomain.LOOT, key, PendingChange.Action.EDIT, false,
                    () -> {
                        LootTableExporter.commitLootTables();
                        KveChangeLog.record(KveChangeLog.Log.LOOT, PendingChange.Action.EDIT, key);
                    },
                    () -> {
                        LootTableExporter.discardStagedUndo(key, restored, beforeUndo);
                        refreshSaved();
                    })
                    .withDiff(() -> LootTableExporter.previewLoot(beforeUndo == null ? restored : beforeUndo),
                            () -> LootTableExporter.previewLoot(restored)));
            flash(Lang.get("gui.kve.pending.queued_hint_reload"));
            refreshSaved();
        }
        refresh();
    }

    private void onApplyPending() {
        if (!ClientPermissionState.canApply()) { flash(Lang.get("gui.kve.perm.apply_denied")); return; }
        if (PendingChangeManager.isEmpty()) return;
        if (PendingChangeManager.domainHasDiff(PendingDomain.LOOT)) {
            minecraft.setScreen(new DiffPreviewScreen(this,
                    PendingChangeManager.entriesForDomain(PendingDomain.LOOT),
                    this::proceedApply));
            return;
        }
        proceedApply();
    }

    private void proceedApply() {
        java.util.Set<PendingDomain> domains = PendingChangeManager.domainsPresent();
        if (domains.size() > 1) {
            int otherCount = PendingChangeManager.size() - PendingChangeManager.sizeForDomain(PendingDomain.LOOT);
            minecraft.setScreen(new KveConfirmScreen(
                    confirmed -> {
                        minecraft.setScreen(this);
                        applyPendingResult(confirmed ? PendingChangeManager.applyAll() : PendingChangeManager.applyDomain(PendingDomain.LOOT));
                    },
                    Component.literal(Lang.get("gui.kve.pending.other_domains_title")),
                    Component.literal(Lang.get("gui.kve.pending.other_domains_msg", otherCount))));
            return;
        }
        applyPendingResult(PendingChangeManager.applyDomain(PendingDomain.LOOT));
    }

    private void applyPendingResult(PendingChangeManager.ApplyResult result) {
                com.kve.client.gui.KveToast.applied(result, LootTableExporter.getLastWriteWarnings());
        refreshSaved();
        refresh();
    }

    private void onDiscardPending() {
        if (PendingChangeManager.isEmpty()) return;
        int count = PendingChangeManager.sizeForDomain(PendingDomain.LOOT);
        if (count == 0) return;
        PendingChangeManager.discardDomain(PendingDomain.LOOT);
        flash(Lang.get("gui.kve.pending.discarded", count));
        refreshSaved();
        refresh();
    }

    private void doOpenChangeHistory() {
        minecraft.setScreen(new KveChangeHistoryScreen(this, KveChangeLog.Log.LOOT,
                Lang.c("gui.kve.change_history.title", Lang.get("gui.kve.change_history.domain.loot")),
                key -> {
                    LootTableExporter.LootTableEntry found = LootTableExporter.getEntry(key);
                    if (found != null) loadDraft(found);
                    minecraft.setScreen(this);
                }));
    }

    private void goBack() {
        if (KveStudioShell.exitIfHosted(this, returnScreen)) return;
        minecraft.setScreen(returnScreen != null ? returnScreen : new KveStudioShell());
    }

    /** ESC acts as "back to main menu" here instead of closing the whole GUI. */
    @Override
    public void onClose() { goBack(); }

    private void refreshSaved() {
        savedList.clear();
        for (LootTableExporter.LootTableEntry e : LootTableExporter.getEntries()) {
            if (!SearchFilter.matches(e.targetId, searchFilter)) continue;
            savedList.add(e);
        }
        savedList.sort((a, b) -> {
            boolean fa = CraftEditorState.isFavoriteLoot(a.key()), fb = CraftEditorState.isFavoriteLoot(b.key());
            if (fa != fb) return fa ? -1 : 1;
            return 0;
        });
        updateSavedScroll();
    }

    private void updateSavedScroll() {
        if (savedScrollBar != null) savedScrollBar.setTotals(savedList.size(), listRowsVisible());
    }

    private void flash(String msg) { flashMsg = msg; flashAt = System.currentTimeMillis(); }

    private Button btn(String label, int x, int y, int w, Runnable a) {
        return KveGuiStyle.smartBtn(x, y, w, label, b -> a.run());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        {
            int wantListW = listResize != null ? listResize.width() : LIST_W;
            int wantW = hosted ? Math.max(hostedW, wantListW + MID_W_MIN + SIDE_W) : wantListW + RIGHT_PANEL_W + SIDE_W;
            int wantH = hosted ? Math.max(200, hostedH) : GUI_H_DEFAULT;
            if (wantW != GUI_W || wantH != GUI_H || wantListW != LIST_W) refresh();
        }
        if (!hosted) renderBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, Lang.get("gui.kve.loot.title"));

        int rowH = 34;
        int scroll = savedScrollBar.getScroll();
        for (int i = 0; i < Math.min(listRowsVisible(), savedList.size() - scroll); i++) {
            LootTableExporter.LootTableEntry e = savedList.get(i + scroll);
            int ry = T + 30 + i * rowH;
            boolean sel = e.key().equals(currentKey);
            g.fill(L + 2, ry, L + LIST_W - 10, ry + rowH - 2, sel ? KveGuiStyle.BORDER_FIELD : KveGuiStyle.BG_FIELD);
            int previewW = 18;
            int py = ry + (rowH - 2 - previewW) / 2;
            g.fill(L + 3, py, L + 3 + previewW, py + previewW, 0x40000000);
            drawTargetPreview(g, e, L + 3, py, previewW, previewW);
            int tx = L + 4 + previewW, tw = LIST_W - 24 - previewW;
            KveGuiStyle.drawTextSafe(g, shortId(e.targetId), tx, ry + 2, tw, e.enabled ? KveGuiStyle.TEXT_LABEL : KveGuiStyle.th(0xFF667788));
            KveGuiStyle.drawTextSafe(g, "(" + kindLabel(e.kind) + "/" + e.event.label() + ") " + e.pools.size() + " pool(s)",
                    tx, ry + 12, tw, KveGuiStyle.th(0xFF556677));
            boolean favd = CraftEditorState.isFavoriteLoot(e.key());
            boolean hovStar = KveGuiStyle.isHovered(mx, my, L + LIST_W - 18, ry, 12, 10);
            KveGuiStyle.drawTextSafe(g, favd ? "\u2605" : "\u2606", L + LIST_W - 18, ry + 2, 14,
                    favd ? KveGuiStyle.th(0xFFE8C96A) : (hovStar ? 0xFFAAAA77 : KveGuiStyle.th(0xFF556677)));
        }
        savedScrollBar.render(g, mx, my);
        if (savedList.isEmpty()) KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.loot.no_saved"), L + 4, T + 34, LIST_W - 8, KveGuiStyle.th(0xFF445566));

        int ax = L + LIST_W + 12;
        int mr = midRight();
        LootTableExporter.LootPoolData pool = currentPool();
        int labelColor = KveGuiStyle.th(0xFF88AACC);

        sectionHeader(g, Lang.get("gui.kve.loot.section.table"), ax, tableHeaderY, mr - ax);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.loot.target_label"), ax, targetRowY + 3, MID_LABEL_W - 4, labelColor);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.common.category"), ax, categoryRowY + 3, MID_LABEL_W - 4, labelColor);

        List<TreeRow> rows = buildTreeRows();
        int total = totalEntryCount();
        int visibleTotal = 0;
        for (TreeRow r : rows) if (!r.isHeader()) visibleTotal++;
        String countLabel = entrySearchFilter != null && !entrySearchFilter.isBlank()
                ? Lang.get("gui.kve.loot.tree_summary_filtered", draftPools.size(), visibleTotal, total)
                : Lang.get("gui.kve.loot.tree_summary", draftPools.size(), total);
        sectionHeader(g, Lang.get("gui.kve.loot.section.pools") + " · " + countLabel, ax, poolsHeaderY, poolsHeaderButtonsX - 4 - ax);

        if (visibleTotal == 0 && total > 0) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.loot.entry_search_none"), ax, treeTop + 2, mr - ax, KveGuiStyle.th(0xFF667788));
        }
        g.fill(ax - 2, treeTop - 2, mr + 4, treeTop + treeRowsVisible * TREE_ROW_H, 0x30000000);
        entryScroll = Math.max(0, Math.min(Math.max(0, rows.size() - treeRowsVisible), entryScroll));
        int visible = Math.min(treeRowsVisible, Math.max(0, rows.size() - entryScroll));
        int xBtn = treeXBtn(), dotsBtn = treeDotsBtn();
        for (int i = 0; i < visible; i++) {
            TreeRow row = rows.get(i + entryScroll);
            int ry = treeTop + i * TREE_ROW_H;
            if (row.isHeader()) {
                LootTableExporter.LootPoolData p = draftPools.get(row.poolIdx);
                boolean sel = row.poolIdx == currentPoolIdx;
                boolean hasMinus = draftPools.size() > 1;
                int textEndX = (hasMinus ? dotsBtn : xBtn) - 2;
                g.fill(ax - 1, ry - 1, mr, ry + TREE_ROW_H - 1, sel ? KveGuiStyle.BORDER_FIELD : 0x40102030);
                String headerText = Lang.get("gui.kve.loot.pool_header", row.poolIdx + 1, draftPools.size(),
                        p.rolls, p.condition.label(), p.entries.size())
                        + (LootTableExporter.hasCompetingEntries(p) ? " " + Lang.get("gui.kve.loot.competing_short") : "")
                        + (p.extraConditions != null && !p.extraConditions.isEmpty()
                            ? " [" + LootConditionText.shortText(p.extraConditions) + "]" : "");
                KveGuiStyle.drawTextSafe(g, headerText, ax + 1, ry + 2, textEndX - ax - 1, sel ? 0xFFEEDD99 : 0xFFAACCEE);
                boolean hovPlus = KveGuiStyle.isHovered(mx, my, xBtn, ry, 12, TREE_ROW_H - 1);
                KveGuiStyle.drawTextSafe(g, "+", xBtn + 3, ry + 2, 12, hovPlus ? 0xFF99EE99 : 0xFF77BB77);
                if (hasMinus) {
                    boolean hovMinus = KveGuiStyle.isHovered(mx, my, dotsBtn, ry, 12, TREE_ROW_H - 1);
                    KveGuiStyle.drawTextSafe(g, "-", dotsBtn + 4, ry + 2, 12, hovMinus ? 0xFFFF8888 : 0xFFCC6666);
                }
            } else {
                LootTableExporter.LootEntryData entry = row.entry;
                int indentX = ax + 8;
                boolean selected = row.poolIdx == selectedPoolIdx && entry == selectedEntry();
                boolean hov = KveGuiStyle.isHovered(mx, my, indentX, ry, mr - indentX, TREE_ROW_H - 1);
                if (selected || hov) g.fill(indentX - 1, ry - 1, mr, ry + TREE_ROW_H - 1, KveGuiStyle.BORDER_FIELD);
                Item icon = resolveEntryIcon(entry.itemId);
                int iconSize = 9;
                MiniItemIcon.draw(g, indentX, ry + 1, iconSize, icon);
                int nameX = indentX + iconSize + 3;
                boolean blank = entry.itemId == null || entry.itemId.isBlank();
                String n = blank ? Lang.get("gui.kve.loot.tree_no_item")
                        : (entry.itemId.contains(":") ? entry.itemId.split(":")[1] : entry.itemId);
                if (!blank && !entry.functions.isEmpty()) n = n + " •";
                if (!blank && entry.conditions != null && !entry.conditions.isEmpty()) {
                    n = n + " [" + LootConditionText.shortText(entry.conditions) + "]";
                }
                KveGuiStyle.drawTextSafe(g, n, nameX, ry + 2, treeCountX() - 4 - nameX, blank ? KveGuiStyle.th(0xFF667788) : 0xFF99CC99);
                KveGuiStyle.drawTextSafe(g, entry.minCount + "-" + entry.maxCount, treeCountX(), ry + 2, 30, labelColor);
                KveGuiStyle.drawTextSafe(g, "w" + entry.weight, treeWeightX(), ry + 2, 28, labelColor);
                boolean hovEllipsis = KveGuiStyle.isHovered(mx, my, dotsBtn, ry, 14, TREE_ROW_H - 1);
                KveGuiStyle.drawTextSafe(g, "...", dotsBtn + 2, ry + 2, 14, hovEllipsis ? 0xFFEEEE99 : 0xFFAAAA77);
                boolean hovX = KveGuiStyle.isHovered(mx, my, xBtn, ry, 12, TREE_ROW_H - 1);
                KveGuiStyle.drawTextSafe(g, "x", xBtn + 3, ry + 2, 12, hovX ? 0xFFFF8888 : 0xFFCC6666);
            }
        }
        if (rows.size() > treeRowsVisible) {
            int trackH = treeRowsVisible * TREE_ROW_H - 2;
            int thumbH = Math.max(6, trackH * treeRowsVisible / rows.size());
            int thumbY = treeTop + (trackH - thumbH) * entryScroll / Math.max(1, rows.size() - treeRowsVisible);
            g.fill(mr + 2, treeTop, mr + 4, treeTop + trackH, 0x40FFFFFF);
            g.fill(mr + 2, thumbY, mr + 4, thumbY + thumbH, KveGuiStyle.BORDER_FIELD);
        }

        sectionHeader(g, Lang.get("gui.kve.loot.section.pool", currentPoolIdx + 1), ax, poolHeaderY, mr - ax);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.loot.rolls_label"), ax, rollsRowY + 3, labelColor);
        if (fChance != null) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.loot.chance_label"), chanceLabelX, chanceRowY + 3, labelColor);
            if (fLootingMult != null) {
                KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.loot.loot_pct_label"), lootingLabelX, chanceRowY + 3, labelColor);
            }
        }

        if (!flashMsg.isEmpty() && System.currentTimeMillis() - flashAt < 4500) {
            KveGuiStyle.drawTextSafe(g, flashMsg, ax, midFlashY, mr - ax, KveGuiStyle.th(0xFFE8C96A));
        }

        int sx = sideX();
        g.fill(sx, T + 15, sx + 1, T + GUI_H - 2, KveGuiStyle.BORDER_FIELD);
        int scx = sx + 6;
        int scw = SIDE_W - 11;
        sectionHeader(g, Lang.get("gui.kve.loot.side_title"), scx, T + 17, scw);

        LootTableExporter.LootEntryData sideEntry = selectedEntry();
        if (sideEntry == null) {
            var hint = KveGuiStyle.wrapTextSafe(font, Lang.get("gui.kve.loot.side_empty"), scw);
            for (int i = 0; i < hint.size(); i++) {
                KveGuiStyle.drawTextSafe(g, hint.get(i), scx, T + 29 + i * 10, scw, KveGuiStyle.th(0xFF667788));
            }
        } else {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.loot.side_weight_label"), scx, sWeightY + 2, labelColor);
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.loot.side_min_label"), scx, sMinMaxY + 2, labelColor);
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.loot.side_max_label"), sMaxLabelX, sMinMaxY + 2, labelColor);

            String fnCount = sideEntry.functions.isEmpty() ? "0" : (sideFnIdx + 1) + "/" + sideEntry.functions.size();
            sectionHeader(g, Lang.get("gui.kve.loot.side_functions", fnCount), scx, sFnHeaderY, scw - 58);
            if (sideEntry.functions.isEmpty()) {
                KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.loot.side_no_functions"), scx, sFnTypeY + 2, scw, KveGuiStyle.TEXT_MUTED);
            }

            int condHeadY = sideCondY();
            int n = sideEntry.conditions == null ? 0 : sideEntry.conditions.size();
            sectionHeader(g, Lang.get("gui.kve.loot.side_conditions", n), scx, condHeadY, scw);
            int condY = condHeadY + 11;
            if (n == 0) {
                KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.loot.side_no_conditions"), scx, condY + 2, scw, KveGuiStyle.TEXT_MUTED);
            }
            for (int i = 0; i < Math.min(n, SIDE_COND_ROWS); i++) {
                com.google.gson.JsonElement c = sideEntry.conditions.get(i);
                String text = c.isJsonObject() ? LootConditionText.one(c.getAsJsonObject()) : "?";
                KveGuiStyle.drawTextSafe(g, text, scx, condY + i * 12 + 2, scw - 15, 0xFFE0C080);
            }
            if (n > SIDE_COND_ROWS) {
                KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.loot.side_conditions_more", n - SIDE_COND_ROWS),
                        scx, condY + SIDE_COND_ROWS * 12 + 1, scw, KveGuiStyle.TEXT_MUTED);
            }
        }

        LootTableExporter.LootPoolData exportPool = currentPool();
        String exportLine = "rolls:" + exportPool.rolls + " | " + exportPool.condition.label()
                + (exportPool.condition == LootTableExporter.Condition.RANDOM_CHANCE
                    || exportPool.condition == LootTableExporter.Condition.RANDOM_CHANCE_WITH_LOOTING
                        ? "(" + exportPool.chance + ")" : "")
                + " | " + exportPool.entries.size() + "e";
        int codeY = T + GUI_H - 20;
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.loot.side_export_label"), scx, codeY, scw, KveGuiStyle.TEXT_LABEL);
        KveGuiStyle.drawTextSafe(g, exportLine, scx, codeY + 9, scw, 0xFF9CBA88);

        listResize.render(g, mx, my);

        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        // un menu "Mas" abierto recibe el clic antes que lo que tenga debajo
        if (com.kve.client.gui.KveMenuButton.consumeClick(mx, my, btn)) return true;
        if (listResize.mouseClicked(mx, my)) return true;
        if (savedScrollBar.mouseClicked(mx, my, btn)) return true;
        int rowH = 34;
        int scroll = savedScrollBar.getScroll();
        for (int i = 0; i < Math.min(listRowsVisible(), savedList.size() - scroll); i++) {
            int ry = T + 30 + i * rowH;
            LootTableExporter.LootTableEntry e = savedList.get(i + scroll);
            if (mx >= L + LIST_W - 18 && mx < L + LIST_W - 6 && my >= ry && my < ry + 10) {
                CraftEditorState.toggleFavoriteLoot(e.key());
                flash(CraftEditorState.isFavoriteLoot(e.key())
                        ? Lang.get("gui.kve.loot.favorite_add")
                        : Lang.get("gui.kve.loot.favorite_remove"));
                refreshSaved();
                return true;
            }
            if (mx >= L + 2 && mx < L + LIST_W - 10 && my >= ry && my < ry + rowH - 2) {
                loadDraft(e);
                return true;
            }
        }
        int ax = L + LIST_W + 12;
        int mr = midRight(), xBtn = treeXBtn(), dotsBtn = treeDotsBtn();
        List<TreeRow> rows = buildTreeRows();
        int scrollNow = Math.max(0, Math.min(Math.max(0, rows.size() - treeRowsVisible), entryScroll));
        int visible = Math.min(treeRowsVisible, Math.max(0, rows.size() - scrollNow));
        for (int i = 0; i < visible; i++) {
            TreeRow row = rows.get(i + scrollNow);
            int ry = treeTop + i * TREE_ROW_H;
            if (my < ry || my >= ry + TREE_ROW_H - 1) continue;
            if (row.isHeader()) {
                boolean hasMinus = draftPools.size() > 1;
                if (mx >= xBtn && mx < xBtn + 12) { addEntryToPool(row.poolIdx); return true; }
                if (hasMinus && mx >= dotsBtn && mx < dotsBtn + 12) { removePoolAt(row.poolIdx); return true; }
                if (mx >= ax && mx < mr) { selectPool(row.poolIdx); return true; }
            } else {
                if (mx >= xBtn && mx < xBtn + 12) {
                    int delIdx = draftPools.get(row.poolIdx).entries.indexOf(row.entry);
                    if (row.poolIdx == selectedPoolIdx) {
                        if (delIdx == selectedEntryIdx) {
                            selectedPoolIdx = -1; selectedEntryIdx = -1;
                        } else if (delIdx < selectedEntryIdx) {
                            selectedEntryIdx--;
                        }
                    }
                    draftPools.get(row.poolIdx).entries.remove(row.entry);
                    refresh();
                    return true;
                }
                if (mx >= dotsBtn && mx < dotsBtn + 14) {
                    quickPickEntryItem(row.poolIdx, row.entry);
                    return true;
                }
                if (mx >= ax + 8 && mx < mr) {
                    selectEntry(row.poolIdx, row.entry);
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
        if (listResize.mouseDragged(mx)) { refresh(); return true; }
        if (listResize.isDragging()) return true;
        if (savedScrollBar.mouseDragged(mx, my, b, dx, dy)) return true;
        return super.mouseDragged(mx, my, b, dx, dy);
    }
    @Override public boolean mouseReleased(double mx, double my, int b) {
        listResize.mouseReleased();
        savedScrollBar.mouseReleased(mx, my, b);
        return super.mouseReleased(mx, my, b);
    }
    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int ax = L + LIST_W + 12;
        if (mx >= ax && mx < sideX()) {
            int total = buildTreeRows().size();
            entryScroll = Math.max(0, Math.min(Math.max(0, total - treeRowsVisible),
                    entryScroll - (int) Math.signum(delta)));
            return true;
        }
        if (mx >= L && mx < L + LIST_W) { savedScrollBar.mouseScrolled(delta); return true; }
        return super.mouseScrolled(mx, my, delta);
    }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if ((mods & GLFW.GLFW_MOD_CONTROL) != 0 && key == GLFW.GLFW_KEY_D) { duplicateTable(); return true; }
        if (getFocused() != null && getFocused().keyPressed(key, scan, mods)) return true;
        if (EditorKeys.isFocusTab(key, mods)) { super.keyPressed(key, scan, mods); return true; }
        return EditorKeys.swallow(mods);
    }
    @Override public boolean isPauseScreen() { return false; }
}
