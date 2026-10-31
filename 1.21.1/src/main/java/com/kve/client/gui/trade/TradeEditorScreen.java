package com.kve.client.gui.trade;

import com.kve.client.gui.KveTextInputScreen;

import com.kve.client.gui.DiffPreviewScreen;
import com.kve.client.gui.EditorKeys;
import com.kve.client.gui.picker.ItemPickerScreen;
import com.kve.client.gui.KveConfirmScreen;
import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KveScaledEditBox;
import com.kve.client.gui.loot.LootTableEditorScreen;
import com.kve.client.gui.MiniItemIcon;
import com.kve.client.gui.mob.MobEditorScreen;
import com.kve.client.gui.mob.MobPickerScreen;
import com.kve.client.gui.recipe.RecipePreviewScreen;
import com.kve.client.gui.ScrollBar;
import com.kve.client.gui.SearchFilter;

import org.lwjgl.glfw.GLFW;
import com.kve.common.Lang;
import com.kve.client.permission.ClientPermissionState;
import com.kve.common.kubejs.TradeExporter;
import com.kve.common.kubejs.TradeExporter.TradeEntry;
import com.kve.common.kubejs.TradeExporter.TradeOffer;
import com.kve.common.kubejs.TradeExporter.TradeTargetKind;
import com.kve.common.kubejs.TradeExporter.TradeEditMode;
import com.kve.common.trade.TradeBackend;
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
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class TradeEditorScreen extends Screen implements KveStudioTab {

    private static final int GUI_H_DEFAULT = 270;
    private int GUI_H = GUI_H_DEFAULT;
    private static final int GAP = 17;
    private int tradeHintY, tradeOfferRowY, tradeOfferListTop;
    private int sideTitleY = -1, sideLblNbtY = -1, sideLblUsesY = -1, sideLblPriceY = -1, sideLblEditY = -1;
    private Button sStateBtn;
    private static final int LIST_ROWS_MIN = 5;

    private int listRowsVisible() { return Math.max(LIST_ROWS_MIN, (T + GUI_H - 6 - (T + 30)) / 34); }
    private static final int OFFER_ROWS_VISIBLE = 8;
    private static final int OFFER_ROW_H = 11;
    private static final int OFFER_ICON = 8;
    private static final int OFFER_ICONS_W = 3 * (OFFER_ICON + 3) + 12;
    private static final int SIDE_ICON_W = 16;
    private final int[] sidePickY = new int[3];

    private static final int LIST_W = 140;
    private static final int RIGHT_PANEL_W = 260;
    private static final int SIDE_W = 150;
    private static final int GUI_W_MIN = LIST_W + RIGHT_PANEL_W + SIDE_W;
    private int GUI_W = GUI_W_MIN;

    private int L, T;

    private final List<TradeEntry> savedList = new ArrayList<>();
    private ScrollBar savedScrollBar;
    private EditBox searchBox;
    private String searchFilter = "";

    private String currentId = "";
    private EditBox fId, fCategory, fProfessionOrEntity, fLevel;
    private EditBox fEntityProfession;
    private String draftId = "";
    private String draftCategory = "";
    private boolean draftEnabled = true;
    private TradeTargetKind draftKind = TradeTargetKind.VILLAGER;
    private String draftProfessionId = "";
    private String draftEntityTypeId = "";
    private int draftLevel = 1;
    private TradeEditMode draftMode = TradeEditMode.ADD_OFFERS;
    private String draftBackendId = "";
    private final List<TradeOffer> draftOffers = new ArrayList<>();

    private int offerScroll = 0;
    private int selectedOfferIdx = -1;

    private final List<List<String>> offerProblems = new ArrayList<>();
    private int offersWithProblems = 0;

    private final List<String> templateNames = new ArrayList<>();

    private EditBox sInA, sInACount, sInB, sInBCount, sOut, sOutCount, sOutNbt, sMaxUses, sXp, sPrice;

    private String flashMsg = "";
    private long flashAt = 0;
    private int actionRowTopY = -1;

    private Screen returnScreen = null;
    private boolean hosted = false;
    private boolean showMoreActions = false;
    private int hostedW = 0;
    private int hostedH = 0;

    public TradeEditorScreen() {
        super(Lang.c("gui.kve.trades.title"));
        restoreEditorDraft();
    }

    private void restoreEditorDraft() {
        TradeExporter.EditorDraft d = TradeExporter.loadEditorDraft();
        if (d == null) return;
        TradeEntry e = d.entry();
        currentId = d.currentId();
        draftId = e.id;
        draftCategory = e.category;
        draftEnabled = e.enabled;
        draftKind = e.targetKind;
        draftProfessionId = e.professionId;
        draftEntityTypeId = e.entityTypeId;
        draftLevel = e.level;
        draftMode = e.mode;
        draftBackendId = e.backendId;
        draftOffers.clear();
        for (TradeOffer o : e.offers) draftOffers.add(o.copy());
        selectedOfferIdx = d.selectedOffer() >= 0 && d.selectedOffer() < draftOffers.size() ? d.selectedOffer()
                : (draftOffers.isEmpty() ? -1 : 0);
        searchFilter = d.search();
    }

    /** Se llama al cambiar de tab, cerrar el Studio o abrir un picker: guarda el borrador. */
    @Override
    public void removed() {
        if (minecraft != null && minecraft.screen == this && KveStudioShell.willReclaim(this, returnScreen)) {
            super.removed();
            return;
        }
        try {
            TradeEntry e = buildDraftTradeEntry();
            TradeExporter.saveEditorDraft(new TradeExporter.EditorDraft(e, currentId, selectedOfferIdx, searchFilter));
        } catch (Exception ignored) {
        }
        super.removed();
    }

    public TradeEditorScreen withReturnScreen(Screen s) {
        this.returnScreen = s;
        return this;
    }

    @Override
    protected void init() {
        if (KveStudioShell.reclaimIfHosted(this, returnScreen)) return;
        hosted = false;
        GUI_W = GUI_W_MIN;
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
        hostedW = w;
        hostedH = h;
        this.width = this.minecraft.getWindow().getGuiScaledWidth();
        this.height = this.minecraft.getWindow().getGuiScaledHeight();
        GUI_W = Math.max(w, GUI_W_MIN);
        GUI_H = Math.max(h, GUI_H_DEFAULT);
        L = x0;
        T = y0;
        buildWidgets();
    }

    private void refresh() {
        GUI_W = hosted ? Math.max(hostedW, GUI_W_MIN) : GUI_W_MIN;
        GUI_H = hosted ? Math.max(hostedH, GUI_H_DEFAULT) : GUI_H_DEFAULT;
        buildWidgets();
    }

    private void buildWidgets() {
        clearWidgets();
        setFocused(null);
        CraftEditorState.loadIfNeeded();
        TradeExporter.loadStateIfNeeded();
        refreshSaved();

        searchBox = new KveScaledEditBox(font, L + 2, T + 16, LIST_W - 4, 11, Lang.c("gui.kve.trades.search_hint"));
        searchBox.setMaxLength(64);
        searchBox.setValue(searchFilter);
        searchBox.setHint(Lang.c("gui.kve.trades.search_hint"));
        searchBox.setResponder(s -> { searchFilter = s; refreshSaved(); });
        addRenderableWidget(searchBox);

        int keepSavedScroll = savedScrollBar != null ? savedScrollBar.getScroll() : 0;
        savedScrollBar = new ScrollBar(L + LIST_W - 8, T + 30, listRowsVisible() * 34, savedList.size(), listRowsVisible());
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
        addRenderableWidget(btn(Lang.get("gui.kve.trades.new"), ax, T + 2, 0, this::newEntry));
        String newFromTplL = Lang.get("gui.kve.trades.template.new_from");
        Button newFromTplBtn = btn(newFromTplL, ax + KveGuiStyle.btnWidth(Lang.get("gui.kve.trades.new")) + KveGuiStyle.BTN_GAP, T + 2, 0, this::openTemplateList);
        newFromTplBtn.setTooltip(Tooltip.create(Component.literal(Lang.get("gui.kve.trades.template.new_from_tip"))));
        newFromTplBtn.active = !com.kve.common.trade.TradeOfferTemplates.names().isEmpty();
        addRenderableWidget(newFromTplBtn);

        KveGuiStyle.Layout ly = new KveGuiStyle.Layout(ax, T + 16, GAP);

        int ay = ly.row();
        fId = new KveScaledEditBox(font, ax, ay, 210, 11, Lang.c("gui.kve.trades.id_narration"));
        fId.setMaxLength(96);
        fId.setValue(draftId);
        fId.setHint(Lang.c("gui.kve.trades.id_hint"));
        fId.setResponder(s -> draftId = s.trim());
        addRenderableWidget(fId);

        ay = ly.row();
        fCategory = new KveScaledEditBox(font, ax, ay, 140, 11, Lang.c("gui.kve.common.category"));
        fCategory.setMaxLength(24);
        fCategory.setValue(draftCategory);
        fCategory.setHint(Lang.c("gui.kve.common.category_hint"));
        fCategory.setResponder(s -> draftCategory = s);
        addRenderableWidget(fCategory);

        ay = ly.row();
        addRenderableWidget(KveGuiStyle.clampedBtn(ax, ay, 134 - KveGuiStyle.BTN_GAP,
                Lang.get("gui.kve.trades.target." + targetKindKey(draftKind)), b -> cycleTargetKind()));
        addRenderableWidget(KveGuiStyle.clampedBtn(ax + 134, ay, (sideX() - 4) - (ax + 134),
                Lang.get(modeKey(draftMode)), b -> cycleMode()));

        ay = ly.row();
        fEntityProfession = null;
        int afterPickerX = ax + 144 + KveGuiStyle.btnWidth("...") + KveGuiStyle.BTN_GAP;
        if (draftKind == TradeTargetKind.VILLAGER) {
            fProfessionOrEntity = new KveScaledEditBox(font, ax, ay, 140, 11, Lang.c("gui.kve.trades.profession_narration"));
            fProfessionOrEntity.setMaxLength(96);
            fProfessionOrEntity.setValue(draftProfessionId);
            fProfessionOrEntity.setHint(Lang.c("gui.kve.trades.profession_hint"));
            fProfessionOrEntity.setResponder(s -> draftProfessionId = s.trim());
            addRenderableWidget(fProfessionOrEntity);
            addRenderableWidget(btn("...", ax + 144, ay, 0, this::openProfessionPicker));

            fLevel = new KveScaledEditBox(font, afterPickerX, ay, 30, 11, Lang.c("gui.kve.trades.level_narration"));
            fLevel.setMaxLength(1);
            fLevel.setValue(String.valueOf(draftLevel));
            fLevel.setTooltip(Tooltip.create(Lang.c("gui.kve.trades.level_tooltip")));
            addRenderableWidget(fLevel);
        } else if (draftKind == TradeTargetKind.WANDERING_TRADER) {
            fProfessionOrEntity = new KveScaledEditBox(font, ax, ay, 140, 11, Lang.c("gui.kve.trades.entity_narration"));
            fProfessionOrEntity.setMaxLength(96);
            fProfessionOrEntity.setValue(draftEntityTypeId);
            fProfessionOrEntity.setHint(Lang.c("gui.kve.trades.entity_hint"));
            fProfessionOrEntity.setResponder(s -> draftEntityTypeId = s.trim());
            addRenderableWidget(fProfessionOrEntity);
            addRenderableWidget(btn("...", ax + 144, ay, 0, this::openEntityPicker));
            fLevel = null;
            if (draftLevel < 1 || draftLevel > 2) draftLevel = 1;
            addRenderableWidget(KveGuiStyle.clampedBtn(afterPickerX, ay, (sideX() - 4) - afterPickerX,
                    wanderingTierLabel(), b -> cycleWanderingTier()));
        } else {
            fProfessionOrEntity = new KveScaledEditBox(font, ax, ay, 180, 11, Lang.c("gui.kve.trades.entity_narration"));
            fProfessionOrEntity.setMaxLength(96);
            fProfessionOrEntity.setValue(draftEntityTypeId);
            fProfessionOrEntity.setHint(Lang.c("gui.kve.trades.entity_hint"));
            fProfessionOrEntity.setResponder(s -> draftEntityTypeId = s.trim());
            addRenderableWidget(fProfessionOrEntity);
            addRenderableWidget(btn("...", ax + 184, ay, 0, this::openEntityPicker));
            fLevel = null;

            com.kve.common.trade.MerchantTypeDetector.Kind merchantKind = draftEntityTypeId.isBlank()
                    ? com.kve.common.trade.MerchantTypeDetector.Kind.UNKNOWN
                    : com.kve.common.trade.TradeTypeDescriptorRegistry.merchantKind(draftEntityTypeId);
            if (merchantKind == com.kve.common.trade.MerchantTypeDetector.Kind.VILLAGER_LIKE
                    || merchantKind == com.kve.common.trade.MerchantTypeDetector.Kind.MERCHANT) {
                boolean villagerLike = merchantKind == com.kve.common.trade.MerchantTypeDetector.Kind.VILLAGER_LIKE;
                ay = ly.row();
                fEntityProfession = new KveScaledEditBox(font, ax, ay, 140, 11, Lang.c("gui.kve.trades.profession_narration"));
                fEntityProfession.setMaxLength(96);
                fEntityProfession.setValue(draftProfessionId);
                fEntityProfession.setHint(Lang.c(villagerLike ? "gui.kve.trades.profession_optional_hint" : "gui.kve.trades.modded_profession_hint"));
                fEntityProfession.setResponder(s -> draftProfessionId = s.trim());
                addRenderableWidget(fEntityProfession);
                addRenderableWidget(btn("...", ax + 144, ay, 0, villagerLike ? this::openProfessionPicker : this::openModdedProfessionPicker));

                if (villagerLike) {
                    fLevel = new KveScaledEditBox(font, afterPickerX, ay, 30, 11, Lang.c("gui.kve.trades.level_narration"));
                    fLevel.setMaxLength(1);
                    fLevel.setValue(String.valueOf(draftLevel));
                    fLevel.setTooltip(Tooltip.create(Lang.c("gui.kve.trades.level_tooltip")));
                    addRenderableWidget(fLevel);
                }
            } else if (merchantKind == com.kve.common.trade.MerchantTypeDetector.Kind.NOT_MERCHANT) {
                ay = ly.row();
                Button warn = btn(Lang.get("gui.kve.trades.entity.not_merchant"), ax, ay, 0, () -> {});
                warn.active = false;
                addRenderableWidget(warn);
            } else if (!draftEntityTypeId.isBlank()) {
                ay = ly.row();
                addRenderableWidget(btn(Lang.get("gui.kve.trades.entity.mark_native"), ax, ay, 0, this::markEntityNativeCompatible));
            }
        }

        ay = ly.row();
        {
            int exX = ax;
            Button existingBtn = KveGuiStyle.clampedBtn(exX, ay, (sideX() - 4) - exX,
                    Lang.get("gui.kve.trades.existing.btn"), b -> loadExistingTrades());
            existingBtn.setTooltip(Tooltip.create(Lang.c("gui.kve.trades.existing.tooltip")));
            addRenderableWidget(existingBtn);
        }

        tradeHintY = ly.row() + 2;

        ay = ly.row();
        tradeOfferRowY = ay;
        addRenderableWidget(btn(Lang.get("gui.kve.trades.offer.add"), ax, ay, 0, this::addOffer));
        refreshTemplateNames();
        int saveTplX = ax + 74;
        Button saveTplBtn = KveGuiStyle.clampedBtn(saveTplX, ay, (sideX() - 4) - saveTplX,
                Lang.get("gui.kve.trades.template.save_as"), b -> saveCurrentAsTemplate());
        saveTplBtn.setTooltip(Tooltip.create(Component.literal(Lang.get("gui.kve.trades.template.save_tip"))));
        addRenderableWidget(saveTplBtn);
        ly.row(10);
        tradeOfferListTop = tradeOfferRowY + GAP + 10;

        int by = T + GUI_H - 20;
        int bx = L + LIST_W + 12;
        String saveL = Lang.get("gui.kve.btn.save"), deleteL = Lang.get("gui.kve.btn.delete"),
                dupL = Lang.get("gui.kve.btn.duplicate_short"), resetL = Lang.get("gui.kve.btn.reset_short"),
                previewL = Lang.get("gui.kve.btn.preview_short");
        KveGuiStyle.ButtonRow actionRow = new KveGuiStyle.ButtonRow(bx, by, sideX() - 4, true);
        KveGuiStyle.ActionBar bar = new KveGuiStyle.ActionBar(actionRow, this::addRenderableWidget);
        bar.primary(saveL, b -> save());
        bar.button(Lang.get("gui.kve.btn.preview"), b -> preview());
        bar.button(Lang.get("gui.kve.btn.duplicate"), b -> duplicateEntry());
        bar.enabled(draftEnabled, b -> toggleEnabled());
        bar.history(Lang.get("gui.kve.btn.undo"), () -> undo(), !currentId.isEmpty() && TradeExporter.canUndo(currentId))
                .history(Lang.get("gui.kve.btn.reset"), () -> resetDraft())
                .more(Lang.get("gui.kve.btn.history"), () -> doOpenChangeHistory());
        bar.end(() -> delete());

        actionRowTopY = actionRow.topY();

        buildSideOfferWidgets();
        recomputeOfferProblems();
    }

    /** Ver comentario de {@link #offerProblems}. Nunca tira: si la validacion de una oferta explota (registry raro en un mod, etc.) esa oferta cuenta como sin problemas - Guardar sigue siendo la barrera real. */
    private void recomputeOfferProblems() {
        offerProblems.clear();
        offersWithProblems = 0;
        TradeBackend backend = null;
        try { backend = TradeExporter.resolveBackend(buildDraftEntryReadOnly()); } catch (Throwable ignored) {}
        for (TradeOffer o : draftOffers) {
            List<String> p = List.of();
            if (o.enabled) {
                try { p = TradeExporter.validateOffer(o, backend); } catch (Throwable ignored) {}
            }
            offerProblems.add(p);
            if (!p.isEmpty()) offersWithProblems++;
        }
    }

    private List<String> problemsAt(int idx) {
        return idx >= 0 && idx < offerProblems.size() ? offerProblems.get(idx) : List.of();
    }

    private int sideX() { return L + LIST_W + RIGHT_PANEL_W; }

    private int sideW() { return Math.max(SIDE_W, GUI_W - LIST_W - RIGHT_PANEL_W); }

    private TradeOffer selectedOffer() {
        if (selectedOfferIdx < 0 || selectedOfferIdx >= draftOffers.size()) return null;
        return draftOffers.get(selectedOfferIdx);
    }

    private void buildSideOfferWidgets() {
        sInA = sInACount = sInB = sInBCount = sOut = sOutCount = sOutNbt = sMaxUses = sXp = sPrice = null;
        sStateBtn = null;
        sideTitleY = sideLblNbtY = sideLblUsesY = sideLblPriceY = sideLblEditY = -1;
        TradeOffer o = selectedOffer();
        if (o == null) return;

        int scx = sideX() + 6;
        int scw = sideW() - 11;

        // columna SIDE reordenada como formulario de arriba hacia abajo en
        // el orden en que se piensa una oferta (Ley de proximidad + flujo
        // de lectura): 1) cabecera "Oferta N/M", 2) QUE se paga y QUE se
        // recibe (A, B, output - los 3 con el mismo ancho/columna de
        // cantidad, un paso de 16px = BTN_H 14 + 2 de aire, antes 13px y
        // se pisaban), 3) ajustes finos (NBT, usos/XP, precio) cada uno
        // con su etiqueta ARRIBA del campo, 4) acciones sobre la oferta
        // (activar/quitar/duplicar/orden) al final y separadas del
        // formulario - lo destructivo (Quitar) nunca queda pegado a un
        // campo que se esta tipeando.
        sideTitleY = T + 19;
        int y = T + 30;
        int rowPitch = KveGuiStyle.BTN_H + 2;
        int pickBtnMaxW = scw - 32 - KveGuiStyle.BTN_GAP - SIDE_ICON_W;
        sidePickY[0] = y;
        addRenderableWidget(KveGuiStyle.clampedBtn(scx + SIDE_ICON_W, y, pickBtnMaxW, pickLabel(o.inputAId, "gui.kve.trades.side.pick_input_a"), b -> pickSideItem(true)));
        sInACount = new KveScaledEditBox(font, scx + scw - 32, y + 1, 32, 11, Lang.c("gui.kve.trades.side.count_narration"));
        sInACount.setMaxLength(3);
        sInACount.setValue(String.valueOf(o.inputACount));
        addRenderableWidget(sInACount);
        sInACount.setResponder(v -> onSideFieldEdited());

        y += rowPitch;
        sidePickY[1] = y;
        addRenderableWidget(KveGuiStyle.clampedBtn(scx + SIDE_ICON_W, y, pickBtnMaxW, pickLabel(o.inputBId, "gui.kve.trades.side.pick_input_b"), b -> pickSideItem(false)));
        sInBCount = new KveScaledEditBox(font, scx + scw - 32, y + 1, 32, 11, Lang.c("gui.kve.trades.side.count_narration"));
        sInBCount.setMaxLength(3);
        sInBCount.setValue(String.valueOf(o.inputBCount));
        addRenderableWidget(sInBCount);
        sInBCount.setResponder(v -> onSideFieldEdited());

        y += rowPitch;
        sidePickY[2] = y;
        addRenderableWidget(KveGuiStyle.clampedBtn(scx + SIDE_ICON_W, y, pickBtnMaxW, pickLabel(o.outputId, "gui.kve.trades.side.pick_output"), b -> pickSideOutput()));
        sOutCount = new KveScaledEditBox(font, scx + scw - 32, y + 1, 32, 11, Lang.c("gui.kve.trades.side.count_narration"));
        sOutCount.setMaxLength(3);
        sOutCount.setValue(String.valueOf(o.outputCount));
        addRenderableWidget(sOutCount);
        sOutCount.setResponder(v -> onSideFieldEdited());

        final int groupPitch = 25;
        y += rowPitch + 3;
        sideLblNbtY = y;
        sOutNbt = new KveScaledEditBox(font, scx, y + 10, scw, 11, Lang.c("gui.kve.trades.side.nbt_narration"));
        sOutNbt.setMaxLength(256);
        sOutNbt.setValue(o.outputNbt);
        sOutNbt.setHint(Lang.c("gui.kve.trades.side.nbt_hint"));
        addRenderableWidget(sOutNbt);
        sOutNbt.setResponder(v -> onSideFieldEdited());

        y += groupPitch;
        sideLblUsesY = y;
        sMaxUses = new KveScaledEditBox(font, scx, y + 10, 44, 11, Lang.c("gui.kve.trades.side.maxuses_narration"));
        sMaxUses.setMaxLength(3);
        sMaxUses.setValue(String.valueOf(o.maxUses));
        addRenderableWidget(sMaxUses);
        sMaxUses.setResponder(v -> onSideFieldEdited());
        sXp = new KveScaledEditBox(font, scx + 48, y + 10, 40, 11, Lang.c("gui.kve.trades.side.xp_narration"));
        sXp.setMaxLength(3);
        sXp.setValue(String.valueOf(o.villagerXp));
        addRenderableWidget(sXp);
        sXp.setResponder(v -> onSideFieldEdited());

        y += groupPitch;
        sideLblPriceY = y;
        sPrice = new KveScaledEditBox(font, scx, y + 10, 60, 11, Lang.c("gui.kve.trades.side.price_narration"));
        sPrice.setMaxLength(6);
        sPrice.setValue(String.valueOf(o.priceMultiplier));
        addRenderableWidget(sPrice);
        sPrice.setResponder(v -> onSideFieldEdited());

        // Acciones sobre la oferta seleccionada, separadas del formulario
        // por un paso extra. El toggle deja de decir solo "Activo" (igual
        // que el toggle de la ENTRADA, abajo en la fila de acciones - dos
        // botones identicos con significados distintos): ahora dice
        // "Oferta: activa/inactiva" (gui.kve.trades.offer.state_*).
        y += groupPitch + 3;
        boolean levelled = TradeExporter.hasLevels(buildDraftEntryReadOnly());
        if (levelled && draftMode == TradeEditMode.ADD_OFFERS && !o.isEdit()) {
            Button unlockBtn = KveGuiStyle.clampedBtn(scx, y, scw, unlockLabel(o), b -> cycleUnlockOffset());
            unlockBtn.setTooltip(Tooltip.create(Lang.c("gui.kve.trades.offer.unlock_tooltip")));
            unlockBtn.active = draftLevel < 5 || o.unlockOffset > 0;
            addRenderableWidget(unlockBtn);
            y += rowPitch;
        }
        if (o.isEdit()) {
            sideLblEditY = y;
            Button unlinkBtn = KveGuiStyle.clampedBtn(scx, y + 10, scw, Lang.get("gui.kve.trades.offer.unlink"), b -> unlinkSelectedEdit());
            unlinkBtn.setTooltip(Tooltip.create(Lang.c("gui.kve.trades.offer.unlink_tooltip")));
            addRenderableWidget(unlinkBtn);
            y += rowPitch + 10;
        }
        String stateL = Lang.get(o.enabled ? "gui.kve.trades.offer.state_on" : "gui.kve.trades.offer.state_off");
        sStateBtn = addRenderableWidget(KveGuiStyle.clampedBtn(scx, y, scw, stateL, b -> toggleSideOfferEnabled()));

        y += rowPitch;
        String dupOfferL = Lang.get("gui.kve.trades.offer.duplicate");
        String delOfferL = Lang.get("gui.kve.trades.offer.remove");
        int dupOfferW = KveGuiStyle.btnWidth(dupOfferL);
        addRenderableWidget(btn(dupOfferL, scx, y, 0, this::duplicateSelectedOffer));
        int delMaxW = scw - dupOfferW - KveGuiStyle.BTN_GAP;
        int delW = Math.min(KveGuiStyle.btnWidth(delOfferL), delMaxW);
        addRenderableWidget(KveGuiStyle.clampedBtn(scx + scw - delW, y, delMaxW, delOfferL, b -> removeSelectedOffer()));

        y += rowPitch;
        Button upBtn = btn("▲", scx, y, 0, () -> moveSelectedOffer(-1));
        upBtn.active = selectedOfferIdx > 0;
        upBtn.setTooltip(Tooltip.create(Component.literal(Lang.get("gui.kve.trades.offer.move_up"))));
        addRenderableWidget(upBtn);
        Button downBtn = btn("▼", scx + KveGuiStyle.btnWidth("▲") + KveGuiStyle.BTN_GAP, y, 0, () -> moveSelectedOffer(1));
        downBtn.active = selectedOfferIdx >= 0 && selectedOfferIdx < draftOffers.size() - 1;
        downBtn.setTooltip(Tooltip.create(Component.literal(Lang.get("gui.kve.trades.offer.move_down"))));
        addRenderableWidget(downBtn);
    }

    private void moveSelectedOffer(int delta) {
        syncSideFieldsToOffer();
        int from = selectedOfferIdx;
        int to = from + delta;
        if (from < 0 || from >= draftOffers.size() || to < 0 || to >= draftOffers.size()) return;
        TradeOffer tmp = draftOffers.get(from);
        draftOffers.set(from, draftOffers.get(to));
        draftOffers.set(to, tmp);
        selectedOfferIdx = to;
        if (to < offerScroll) offerScroll = to;
        else if (to >= offerScroll + OFFER_ROWS_VISIBLE) offerScroll = to - OFFER_ROWS_VISIBLE + 1;
        refresh();
    }

    /** Volcado + revalidacion en vivo cada vez que se tipea en la columna SIDE - sin esto el marcador "!" de la lista solo se actualizaba al perder el foco/pulsar un boton (ej. tipear maxUses "0" no se marcaba hasta despues). Solo toca el modelo y la cache de problemas, nunca reconstruye widgets (reconstruir a mitad de tipeo mataria el foco del EditBox). */
    private void onSideFieldEdited() {
        syncSideFieldsToOffer();
        autoEnableEdit(selectedOffer());
        recomputeOfferProblems();
    }

    private void autoEnableEdit(TradeOffer o) {
        if (o == null || !o.isEdit() || o.enabled) return;
        o.enabled = true;
        if (sStateBtn != null) sStateBtn.setMessage(Component.literal(Lang.get("gui.kve.trades.offer.state_on")));
    }

    private String unlockLabel(TradeOffer o) {
        String name = levelName(o.effectiveLevel(draftLevel));
        return o.unlockOffset <= 0
                ? Lang.get("gui.kve.trades.offer.unlock_with", name)
                : Lang.get("gui.kve.trades.offer.unlock_after", o.unlockOffset, name);
    }

    private static String levelName(int lvl) {
        return lvl + " " + net.minecraft.client.resources.language.I18n.get("merchant.level." + lvl);
    }

    private void cycleUnlockOffset() {
        syncFieldsToDraft();
        TradeOffer o = selectedOffer();
        if (o == null) return;
        int max = Math.max(0, 5 - draftLevel);
        o.unlockOffset = o.unlockOffset >= max ? 0 : o.unlockOffset + 1;
        refresh();
    }

    private void unlinkSelectedEdit() {
        syncFieldsToDraft();
        TradeOffer o = selectedOffer();
        if (o == null) return;
        o.editInputAId = ""; o.editInputBId = ""; o.editOutputId = "";
        refresh();
    }

    private void loadExistingTrades() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }
        syncFieldsToDraft();
        TradeEntry draft = buildDraftEntryReadOnly();
        boolean byProfession = TradeExporter.hasLevels(draft);
        if (byProfession && (draftProfessionId == null || draftProfessionId.isBlank())) {
            flash(Lang.get("gui.kve.trades.existing.need_profession")); return;
        }
        if (draft.targetKind == TradeTargetKind.ENTITY && !byProfession
                && (draftEntityTypeId == null || draftEntityTypeId.isBlank())) {
            flash(Lang.get("gui.kve.trades.existing.need_entity")); return;
        }
        com.kve.common.trade.ExistingTradeProbe.Result res = probeOnServer(draft);
        if (res.offers().isEmpty()) {
            flash(Lang.get("gui.kve.trades.existing.none_any") + (res.skipped() > 0 ? " " + Lang.get("gui.kve.trades.existing.skipped", res.skipped()) : ""));
            return;
        }
        if (byProfession) draftLevel = 1;
        int firstNew = -1, added = 0;
        for (TradeOffer o : res.offers()) {
            boolean dup = draftOffers.stream().anyMatch(d -> d.isEdit()
                    && d.editInputAId.equals(o.editInputAId)
                    && d.editInputBId.equals(o.editInputBId)
                    && d.editOutputId.equals(o.editOutputId));
            if (dup) continue;
            o.enabled = false;
            draftOffers.add(o);
            if (firstNew < 0) firstNew = draftOffers.size() - 1;
            added++;
        }
        draftMode = TradeEditMode.ADD_OFFERS;
        if (firstNew >= 0) {
            selectedOfferIdx = firstNew;
            offerScroll = Math.max(0, Math.min(firstNew, Math.max(0, draftOffers.size() - OFFER_ROWS_VISIBLE)));
        }
        flash(Lang.get("gui.kve.trades.existing.loaded_all", added)
                + (res.skipped() > 0 ? " " + Lang.get("gui.kve.trades.existing.skipped", res.skipped()) : ""));
        refresh();
    }

    private com.kve.common.trade.ExistingTradeProbe.Result probeOnServer(TradeEntry draft) {
        try {
            net.minecraft.client.server.IntegratedServer server = minecraft.getSingleplayerServer();
            if (server != null) {
                return server.submit(() -> com.kve.common.trade.ExistingTradeProbe.probe(draft, server.overworld()))
                        .get(10, java.util.concurrent.TimeUnit.SECONDS);
            }
            return com.kve.common.trade.ExistingTradeProbe.probe(draft, minecraft.level);
        } catch (Throwable t) {
            return new com.kve.common.trade.ExistingTradeProbe.Result(java.util.List.of(), 0);
        }
    }

    private void duplicateSelectedOffer() {
        syncSideFieldsToOffer();
        TradeOffer o = selectedOffer();
        if (o == null) return;
        draftOffers.add(o.copy());
        selectedOfferIdx = draftOffers.size() - 1;
        offerScroll = Math.max(0, draftOffers.size() - OFFER_ROWS_VISIBLE);
        refresh();
    }

    private void drawOfferIcons(GuiGraphics g, TradeOffer o, int x, int ry) {
        int step = OFFER_ICON + 3;
        MiniItemIcon.draw(g, x, ry, OFFER_ICON, MiniItemIcon.itemOrTag(o.inputAId, o.inputAIsTag));
        boolean hasB = o.inputBId != null && !o.inputBId.isEmpty();
        if (hasB) MiniItemIcon.draw(g, x + step, ry, OFFER_ICON, MiniItemIcon.itemOrTag(o.inputBId, o.inputBIsTag));
        int arrowX = x + 2 * step - 1;
        g.drawString(font, "\u2192", arrowX, ry, KveGuiStyle.TEXT_MUTED, false);
        MiniItemIcon.draw(g, arrowX + 10, ry, OFFER_ICON, MiniItemIcon.itemOrTag(o.outputId, o.outputIsTag));
    }

    private void drawSidePickIcons(GuiGraphics g, TradeOffer o, int scx) {
        int size = KveGuiStyle.BTN_H - 2;
        MiniItemIcon.draw(g, scx + 1, sidePickY[0] + 1, size, MiniItemIcon.itemOrTag(o.inputAId, o.inputAIsTag));
        MiniItemIcon.draw(g, scx + 1, sidePickY[1] + 1, size, MiniItemIcon.itemOrTag(o.inputBId, o.inputBIsTag));
        MiniItemIcon.draw(g, scx + 1, sidePickY[2] + 1, size, MiniItemIcon.itemOrTag(o.outputId, o.outputIsTag));
    }

    private String pickLabel(String id, String emptyKey) {
        if (id == null || id.isEmpty()) return Lang.get(emptyKey);
        return id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
    }

    private void pickSideItem(boolean sideA) {
        syncSideFieldsToOffer();
        TradeOffer o = selectedOffer();
        if (o == null) return;
        minecraft.setScreen(new ItemPickerScreen(this, stack -> {
            ResourceLocation k = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (k == null) return;
            if (sideA) { o.inputAId = k.toString(); o.inputAIsTag = false; }
            else { o.inputBId = k.toString(); o.inputBIsTag = false; }
            autoEnableEdit(o);
            refresh();
        }));
    }

    private void pickSideOutput() {
        syncSideFieldsToOffer();
        TradeOffer o = selectedOffer();
        if (o == null) return;
        minecraft.setScreen(new ItemPickerScreen(this, stack -> {
            ResourceLocation k = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (k != null) { o.outputId = k.toString(); o.outputIsTag = false; autoEnableEdit(o); }
            refresh();
        }));
    }

    private void toggleSideOfferEnabled() {
        syncSideFieldsToOffer();
        TradeOffer o = selectedOffer();
        if (o == null) return;
        o.enabled = !o.enabled;
        refresh();
    }

    private void removeSelectedOffer() {
        TradeOffer o = selectedOffer();
        if (o == null) return;
        draftOffers.remove(selectedOfferIdx);
        selectedOfferIdx = -1;
        offerScroll = Math.max(0, Math.min(offerScroll, Math.max(0, draftOffers.size() - OFFER_ROWS_VISIBLE)));
        refresh();
    }

    private void syncSideFieldsToOffer() {
        TradeOffer o = selectedOffer();
        if (o == null) return;
        if (sInACount != null) o.inputACount = parseIntSafe(sInACount.getValue(), o.inputACount);
        if (sInBCount != null) o.inputBCount = parseIntSafe(sInBCount.getValue(), o.inputBCount);
        if (sOutCount != null) o.outputCount = parseIntSafe(sOutCount.getValue(), o.outputCount);
        if (sOutNbt != null) o.outputNbt = sOutNbt.getValue();
        if (sMaxUses != null) o.maxUses = parseIntSafe(sMaxUses.getValue(), o.maxUses);
        if (sXp != null) o.villagerXp = parseIntSafe(sXp.getValue(), o.villagerXp);
        if (sPrice != null) o.priceMultiplier = parseFloatSafe(sPrice.getValue(), o.priceMultiplier);
    }

    private static int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception ex) { return def; }
    }

    private static float parseFloatSafe(String s, float def) {
        try { return Float.parseFloat(s.trim()); } catch (Exception ex) { return def; }
    }

    private void selectOffer(int idx) {
        syncSideFieldsToOffer();
        selectedOfferIdx = idx;
        refresh();
    }

    private void addOffer() {
        syncFieldsToDraft();
        draftOffers.add(new TradeOffer());
        selectedOfferIdx = draftOffers.size() - 1;
        offerScroll = Math.max(0, draftOffers.size() - OFFER_ROWS_VISIBLE);
        refresh();
    }

    private String targetKindKey(TradeTargetKind k) {
        return switch (k) {
            case VILLAGER -> "villager";
            case WANDERING_TRADER -> "wandering";
            case ENTITY -> "entity";
        };
    }

    private String modeKey(TradeEditMode m) {
        return switch (m) {
            case REPLACE_LEVEL -> "gui.kve.trades.mode.replace_level";
            case ADD_OFFERS -> "gui.kve.trades.mode.add";
            case REMOVE_MATCHING -> "gui.kve.trades.mode.remove_matching";
            case CLEAR_PROFESSION -> "gui.kve.trades.mode.clear_profession";
            case CLEAR_ENTITY -> "gui.kve.trades.mode.clear_entity";
        };
    }

    private void cycleTargetKind() {
        syncFieldsToDraft();
        TradeTargetKind[] vals = TradeTargetKind.values();
        draftKind = vals[(draftKind.ordinal() + 1) % vals.length];
        if (draftKind == TradeTargetKind.WANDERING_TRADER && (draftEntityTypeId == null || draftEntityTypeId.isBlank())) {
            draftEntityTypeId = "minecraft:wandering_trader";
        } else if (draftKind == TradeTargetKind.ENTITY && "minecraft:wandering_trader".equals(draftEntityTypeId)) {
            draftEntityTypeId = "";
        }
        if (!modeValidFor(draftKind, draftMode)) draftMode = TradeEditMode.ADD_OFFERS;
        refresh();
    }

    private void autoDetectAbstractVillagerCompat(net.minecraft.world.entity.EntityType<?> entityType, ResourceLocation key) {
        if (minecraft == null || minecraft.level == null) return; // sin mundo cargado no se puede instanciar para inspeccionar - no bloquea nada, el boton "Marcar" de mas abajo sigue disponible como fallback manual.
        String entityId = key.toString();
        if (com.kve.common.trade.TradeTypeDescriptorRegistry.find(entityId).isPresent()) return; // ya declarado (bundled o de usuario) - no pisar una decision existente (p.ej. un false a proposito).
        net.minecraft.world.entity.Entity probe;
        try {
            probe = entityType.create(minecraft.level);
        } catch (Exception ignored) {
            return;
        }
        if (!(probe instanceof net.minecraft.world.entity.npc.Villager)) return;
        com.kve.common.trade.TradeTypeDescriptor d = new com.kve.common.trade.TradeTypeDescriptor();
        d.entityTypeId = entityId;
        d.displayName = entityId;
        int colon = entityId.indexOf(':');
        d.requiresMod = colon > 0 ? entityId.substring(0, colon) : "";
        d.abstractVillagerCompatible = true;
        if (com.kve.common.trade.TradeTypeDescriptorRegistry.saveUserDescriptor(d)) {
            flash(Lang.get("gui.kve.trades.entity.auto_detect_ok"));
        }
    }

    private void openEntityPicker() {
        syncFieldsToDraft();
        minecraft.setScreen(new MobPickerScreen(this, entityType -> {
            ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
            if (key != null) {
                draftEntityTypeId = key.toString();
                autoDetectAbstractVillagerCompat(entityType, key);
            }
            minecraft.setScreen(this);
            refresh();
        }));
    }

    private void openProfessionPicker() {
        syncFieldsToDraft();
        minecraft.setScreen(new ProfessionPickerScreen(this, draftProfessionId, professionId -> {
            draftProfessionId = professionId;
            minecraft.setScreen(this);
            refresh();
        }));
    }

    private void openModdedProfessionPicker() {
        syncFieldsToDraft();
        List<String> ids = com.kve.common.trade.TradeTypeDescriptorRegistry.knownModdedProfessions(draftEntityTypeId);
        minecraft.setScreen(ProfessionPickerScreen.forIds(this, draftProfessionId, ids, draftEntityTypeId, professionId -> {
            draftProfessionId = professionId;
            minecraft.setScreen(this);
            refresh();
        }));
    }

    private void markEntityNativeCompatible() {
        syncFieldsToDraft();
        String entityId = draftEntityTypeId;
        if (entityId == null || entityId.isBlank()) return;
        com.kve.common.trade.TradeTypeDescriptor d = new com.kve.common.trade.TradeTypeDescriptor();
        d.entityTypeId = entityId;
        d.displayName = entityId;
        int colon = entityId.indexOf(':');
        d.requiresMod = colon > 0 ? entityId.substring(0, colon) : "";
        d.abstractVillagerCompatible = true;
        boolean ok = com.kve.common.trade.TradeTypeDescriptorRegistry.saveUserDescriptor(d);
        flash(ok ? Lang.get("gui.kve.trades.entity.mark_native_ok") : Lang.get("gui.kve.trades.entity.mark_native_fail"));
        refresh();
    }

    private boolean modeValidFor(TradeTargetKind kind, TradeEditMode mode) {
        boolean entityTable = kind == TradeTargetKind.ENTITY
                && com.kve.common.trade.KveNativeTradeBackend.usesProfessionTable(entityProbe());
        return switch (mode) {
            case CLEAR_PROFESSION -> kind == TradeTargetKind.VILLAGER || entityTable;
            case CLEAR_ENTITY -> kind == TradeTargetKind.WANDERING_TRADER || (kind == TradeTargetKind.ENTITY && !entityTable);
            default -> true;
        };
    }

    private TradeEntry entityProbe() {
        TradeEntry p = new TradeEntry();
        p.targetKind = TradeTargetKind.ENTITY;
        p.entityTypeId = draftEntityTypeId == null ? "" : draftEntityTypeId;
        p.professionId = draftProfessionId == null ? "" : draftProfessionId;
        return p;
    }

    private void cycleMode() {
        syncFieldsToDraft();
        TradeEditMode[] vals = TradeEditMode.values();
        int idx = draftMode.ordinal();
        for (int i = 0; i < vals.length; i++) {
            idx = (idx + 1) % vals.length;
            if (modeValidFor(draftKind, vals[idx])) { draftMode = vals[idx]; break; }
        }
        refresh();
    }

    private String wanderingTierLabel() {
        return Lang.get("gui.kve.trades.tier_btn", draftLevel == 2 ? Lang.get("gui.kve.trades.tier.rare") : Lang.get("gui.kve.trades.tier.generic"));
    }

    private void cycleWanderingTier() {
        syncFieldsToDraft();
        draftLevel = (draftLevel == 2) ? 1 : 2;
        refresh();
    }

    private void refreshTemplateNames() {
        templateNames.clear();
        templateNames.addAll(com.kve.common.trade.TradeOfferTemplates.names());
    }

    private void openTemplateList() {
        syncFieldsToDraft();
        minecraft.setScreen(new TradeTemplateListScreen(this, name -> {
            if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }
            newEntry();
            com.kve.common.trade.TradeOfferTemplates.applyTo(name, draftOffers);
            selectedOfferIdx = draftOffers.isEmpty() ? -1 : 0;
            flash(Lang.get("gui.kve.trades.template.new_created", name, draftOffers.size()));
            refresh();
        }));
    }

    private void saveCurrentAsTemplate() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }
        syncFieldsToDraft();
        if (draftOffers.isEmpty()) { flash(Lang.get("gui.kve.trades.template.empty")); return; }
        String suggested = draftId.isBlank() ? Lang.get("gui.kve.trades.template.default_name") : draftId;
        minecraft.setScreen(new KveTextInputScreen(this,
                Component.literal(Lang.get("gui.kve.trades.template.save_title", draftOffers.size())), suggested,
                Lang.get("gui.kve.trades.template.name_hint"), name -> {
            Runnable doSave = () -> {
                com.kve.common.trade.TradeOfferTemplates.save(name, draftOffers);
                refreshTemplateNames();
                flash(Lang.get("gui.kve.trades.template.saved", name));
            };
            if (com.kve.common.trade.TradeOfferTemplates.names().contains(name)) {
                minecraft.setScreen(new KveConfirmScreen(ok -> {
                    if (ok) doSave.run();
                    minecraft.setScreen(this);
                }, Component.literal(Lang.get("gui.kve.trades.template.overwrite_title")),
                        Component.literal(Lang.get("gui.kve.trades.template.overwrite_msg", name))));
                return;
            }
            doSave.run();
            minecraft.setScreen(this);
        }));
    }

    private void syncFieldsToDraft() {
        if (fId != null) draftId = fId.getValue().trim();
        if (fCategory != null) draftCategory = fCategory.getValue();
        if (fProfessionOrEntity != null) {
            if (draftKind == TradeTargetKind.VILLAGER) draftProfessionId = fProfessionOrEntity.getValue().trim();
            else draftEntityTypeId = fProfessionOrEntity.getValue().trim();
        }
        if (fLevel != null) draftLevel = Math.max(1, Math.min(5, parseIntSafe(fLevel.getValue(), draftLevel)));
        syncSideFieldsToOffer();
    }

    private TradeEntry buildDraftTradeEntry() {
        syncFieldsToDraft();
        TradeEntry e = new TradeEntry();
        e.id = draftId;
        e.enabled = draftEnabled;
        e.category = draftCategory.trim();
        e.targetKind = draftKind;
        e.professionId = draftProfessionId;
        e.entityTypeId = draftEntityTypeId;
        e.level = draftLevel;
        e.mode = draftMode;
        e.backendId = draftBackendId;
        for (TradeOffer o : draftOffers) e.offers.add(o.copy());
        return e;
    }

    private void loadDraft(TradeEntry e) {
        currentId = e.id;
        draftId = e.id;
        draftCategory = e.category;
        draftEnabled = e.enabled;
        draftKind = e.targetKind;
        draftProfessionId = e.professionId;
        draftEntityTypeId = e.entityTypeId;
        draftLevel = e.level;
        draftMode = e.mode;
        draftBackendId = e.backendId;
        draftOffers.clear();
        for (TradeOffer o : e.offers) draftOffers.add(o.copy());
        selectedOfferIdx = draftOffers.isEmpty() ? -1 : 0;
        offerScroll = 0;
        refresh();
    }

    private void newEntry() {
        currentId = "";
        draftId = ""; draftCategory = ""; draftEnabled = true;
        draftKind = TradeTargetKind.VILLAGER;
        draftProfessionId = ""; draftEntityTypeId = "";
        draftLevel = 1;
        draftMode = TradeEditMode.ADD_OFFERS;
        draftBackendId = "";
        draftOffers.clear();
        selectedOfferIdx = -1; offerScroll = 0;
        refresh();
    }

    public void selectEntryById(String id) {
        if (id == null || id.isBlank()) return;
        TradeEntry e = TradeExporter.getEntry(id);
        if (e != null) loadDraft(e);
    }

    private void resetDraft() {
        if (currentId.isEmpty()) { newEntry(); return; }
        TradeEntry e = TradeExporter.getEntry(currentId);
        if (e != null) loadDraft(e); else newEntry();
    }

    private void duplicateEntry() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }
        syncFieldsToDraft();
        currentId = "";
        draftId = "";
        if (fId != null) fId.setValue("");
        flash(Lang.get("gui.kve.trades.duplicated_hint"));
        refresh();
    }

    private void toggleEnabled() { syncFieldsToDraft(); draftEnabled = !draftEnabled; refresh(); }

    private void save() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }
        TradeEntry e = buildDraftTradeEntry();
        if (e.id == null || e.id.isBlank()) {
            e.id = KveGuiStyle.randomId();
            draftId = e.id;
            if (fId != null) fId.setValue(e.id);
        }

        if (!e.id.equals(currentId) && TradeExporter.getEntry(e.id) != null) {
            flash(Lang.get("gui.kve.trades.id_exists", e.id));
            return;
        }

        List<String> problems = TradeExporter.validateTrade(e);
        if (!problems.isEmpty()) {
            flash(problems.size() == 1
                    ? problems.get(0)
                    : Lang.get("gui.kve.trades.problems_more", problems.get(0), problems.size() - 1));
            return;
        }

        String oldId = (!currentId.isEmpty() && !currentId.equals(e.id)) ? currentId : null;
        TradeEntry previousUnderOldId = oldId != null ? TradeExporter.getEntry(oldId) : null;
        TradeEntry previous = TradeExporter.getEntry(e.id);
        PendingChange.Action action = previous == null ? PendingChange.Action.CREATE : PendingChange.Action.EDIT;

        if (oldId != null) TradeExporter.stageDeleteTrade(oldId);
        TradeExporter.stageTrade(e);

        boolean requiresRestart = TradeExporter.requiresRestartFor(e);
        PendingChangeManager.queue(PendingChange.of(PendingDomain.TRADES, e.id, action, requiresRestart,
                () -> {
                    try {
                        TradeExporter.commitTrades();
                    } catch (java.io.IOException ex) {
                        flash(Lang.get("gui.kve.trades.commit_error", ex.getMessage()));
                        return;
                    }
                    KveChangeLog.record(KveChangeLog.Log.TRADES, action, e.id);
                },
                () -> {
                    TradeExporter.discardStagedTrade(e.id, previous);
                    if (oldId != null) TradeExporter.discardStagedTrade(oldId, previousUnderOldId);
                    refreshSaved();
                })
                .withDiff(previous == null ? null : () -> TradeExporter.previewTradeJS(previous),
                        () -> TradeExporter.previewTradeJS(e))
                .withMode(tradeApplyMode(requiresRestart)));

        currentId = e.id;
        String saved = com.kve.client.gui.workspace.pending.ApplyFeedback.queuedHint(tradeApplyMode(requiresRestart));
        List<String> conflicts = TradeExporter.findConflicts(e);
        if (!conflicts.isEmpty()) saved += " " + Lang.get("gui.kve.trades.conflict_warn", conflicts.get(0));
        flash(saved);
        refreshSaved();
        refresh();
    }

    private com.kve.client.gui.workspace.pending.ApplyMode tradeApplyMode(boolean requiresRestart) {
        if (requiresRestart) return com.kve.client.gui.workspace.pending.ApplyMode.RESTART;
        return minecraft != null && minecraft.isLocalServer()
                ? com.kve.client.gui.workspace.pending.ApplyMode.LIVE
                : com.kve.client.gui.workspace.pending.ApplyMode.RELOAD;
    }

    private void delete() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }
        if (currentId.isEmpty()) { flash(Lang.get("gui.kve.trades.select_saved_first")); return; }
        String id = currentId;
        TradeEntry previous = TradeExporter.getEntry(id);
        TradeExporter.stageDeleteTrade(id);
        PendingChangeManager.queue(PendingChange.of(PendingDomain.TRADES, id, PendingChange.Action.DELETE, false,
                () -> {
                    try {
                        TradeExporter.commitTrades();
                    } catch (java.io.IOException ex) {
                        flash(Lang.get("gui.kve.trades.commit_error", ex.getMessage()));
                        return;
                    }
                    KveChangeLog.record(KveChangeLog.Log.TRADES, PendingChange.Action.DELETE, id);
                },
                () -> {
                    if (previous != null) TradeExporter.stageTrade(previous);
                    refreshSaved();
                })
                .withDiff(previous == null ? null : () -> TradeExporter.previewTradeJS(previous), null)
                .withMode(tradeApplyMode(false)));
        flash(com.kve.client.gui.workspace.pending.ApplyFeedback.queuedHint(tradeApplyMode(false)));
        newEntry();
        refreshSaved();
    }

    private void undo() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }
        if (currentId.isEmpty()) return;
        String id = currentId;
        TradeEntry beforeUndo = TradeExporter.getEntry(id);
        TradeEntry restored = TradeExporter.stageUndo(id);
        if (restored != null) {
            loadDraft(restored);
            PendingChangeManager.queue(PendingChange.of(PendingDomain.TRADES, id, PendingChange.Action.EDIT, false,
                    () -> {
                        try {
                            TradeExporter.commitTrades();
                        } catch (java.io.IOException ex) {
                            flash(Lang.get("gui.kve.trades.commit_error", ex.getMessage()));
                            return;
                        }
                        KveChangeLog.record(KveChangeLog.Log.TRADES, PendingChange.Action.EDIT, id);
                    },
                    () -> {
                        TradeExporter.discardStagedUndo(id, restored, beforeUndo);
                        refreshSaved();
                    })
                    .withDiff(() -> TradeExporter.previewTradeJS(beforeUndo == null ? restored : beforeUndo),
                            () -> TradeExporter.previewTradeJS(restored))
                    .withMode(tradeApplyMode(false)));
            flash(com.kve.client.gui.workspace.pending.ApplyFeedback.queuedHint(tradeApplyMode(false)));
            refreshSaved();
        }
        refresh();
    }

    private void preview() {
        TradeEntry e = buildDraftTradeEntry();
        if (e.id.isEmpty()) { flash(Lang.get("gui.kve.trades.id_required")); return; }
        String script = TradeExporter.previewTradeJS(e);
        minecraft.setScreen(new RecipePreviewScreen(this, e.id, script));
    }

    private void onApplyPending() {
        if (!ClientPermissionState.canApply()) { flash(Lang.get("gui.kve.perm.apply_denied")); return; }
        if (PendingChangeManager.isEmpty()) return;
        if (PendingChangeManager.domainHasDiff(PendingDomain.TRADES)) {
            minecraft.setScreen(new DiffPreviewScreen(this,
                    PendingChangeManager.entriesForDomain(PendingDomain.TRADES),
                    this::proceedApply));
            return;
        }
        proceedApply();
    }

    private void proceedApply() {
        java.util.Set<PendingDomain> domains = PendingChangeManager.domainsPresent();
        if (domains.size() > 1) {
            int otherCount = PendingChangeManager.size() - PendingChangeManager.sizeForDomain(PendingDomain.TRADES);
            minecraft.setScreen(new KveConfirmScreen(
                    confirmed -> {
                        minecraft.setScreen(this);
                        applyPendingResult(confirmed ? PendingChangeManager.applyAll() : PendingChangeManager.applyDomain(PendingDomain.TRADES));
                    },
                    Component.literal(Lang.get("gui.kve.pending.other_domains_title")),
                    Component.literal(Lang.get("gui.kve.pending.other_domains_msg", otherCount))));
            return;
        }
        applyPendingResult(PendingChangeManager.applyDomain(PendingDomain.TRADES));
    }

    private void applyPendingResult(PendingChangeManager.ApplyResult result) {
                com.kve.client.gui.KveToast.applied(result, TradeExporter.getLastWriteWarnings());
        refreshSaved();
        refresh();
    }

    private void onDiscardPending() {
        if (PendingChangeManager.isEmpty()) return;
        int count = PendingChangeManager.sizeForDomain(PendingDomain.TRADES);
        if (count == 0) return;
        PendingChangeManager.discardDomain(PendingDomain.TRADES);
        flash(Lang.get("gui.kve.pending.discarded", count));
        refreshSaved();
        refresh();
    }

    private void doOpenChangeHistory() {
        minecraft.setScreen(new KveChangeHistoryScreen(this, KveChangeLog.Log.TRADES,
                Lang.c("gui.kve.change_history.title", Lang.get("gui.kve.change_history.domain.trades")),
                id -> {
                    TradeEntry found = TradeExporter.getEntry(id);
                    if (found != null) loadDraft(found);
                    minecraft.setScreen(this);
                }).withIcons(id -> {
                    TradeEntry t = TradeExporter.getEntry(id);
                    return t == null || t.offers.isEmpty() ? null : MiniItemIcon.itemOrTag(t.offers.get(0).outputId, t.offers.get(0).outputIsTag);
                }));
    }

    private void goBack() {
        if (KveStudioShell.exitIfHosted(this, returnScreen)) return;
        minecraft.setScreen(returnScreen != null ? returnScreen : new KveStudioShell());
    }

    @Override
    public void onClose() { goBack(); }

    private void refreshSaved() {
        savedList.clear();
        for (TradeEntry e : TradeExporter.getEntries()) {
            if (!SearchFilter.matches(e.id, e.category + " " + entryTargetText(e), searchFilter)) continue;
            savedList.add(e);
        }
        savedList.sort(java.util.Comparator
                .comparing((TradeEntry e) -> e.category == null ? "" : e.category.toLowerCase())
                .thenComparingInt(e -> e.targetKind.ordinal())
                .thenComparing(e -> entryTargetText(e).toLowerCase())
                .thenComparingInt(e -> e.level)
                .thenComparing(e -> e.id));
        updateSavedScroll();
    }

    private static String entryTargetText(TradeEntry e) {
        return e.targetKind == TradeTargetKind.VILLAGER
                ? (e.professionId == null ? "" : e.professionId)
                : (e.entityTypeId == null ? "" : e.entityTypeId);
    }

    private String entrySummary(TradeEntry e) {
        String target = shortId(entryTargetText(e));
        String lvl = e.targetKind == TradeTargetKind.WANDERING_TRADER
                ? Lang.get("gui.kve.trades.tier_btn", e.level == 2 ? Lang.get("gui.kve.trades.tier.rare") : Lang.get("gui.kve.trades.tier.generic"))
                : "L" + e.level;
        return target + " " + lvl + " · " + e.offers.size();
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
            int wantW = hosted ? Math.max(hostedW, GUI_W_MIN) : GUI_W_MIN;
            int wantH = hosted ? Math.max(hostedH, GUI_H_DEFAULT) : GUI_H_DEFAULT;
            if (wantW != GUI_W || wantH != GUI_H) refresh();
        }
        if (!hosted) renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, Lang.get("gui.kve.trades.title"));
        int sepColor = KveGuiStyle.th(0xFF2A4A6A);
        g.fill(L + LIST_W + 2, T + 14, L + LIST_W + 3, T + GUI_H - 1, sepColor);
        g.fill(sideX() + 1, T + 14, sideX() + 2, T + GUI_H - 1, sepColor);

        int rowH = 34;
        int scroll = savedScrollBar.getScroll();
        for (int i = 0; i < Math.min(listRowsVisible(), savedList.size() - scroll); i++) {
            int ry = T + 30 + i * rowH;
            TradeEntry e = savedList.get(i + scroll);
            boolean selected = e.id.equals(currentId);
            boolean hovRow = mx >= L && mx < L + LIST_W - 12 && my >= ry && my < ry + rowH - 1;
            if (selected || hovRow) {
                g.fill(L, ry - 1, L + LIST_W - 12, ry + rowH - 1,
                        selected ? KveGuiStyle.th(0xFF2A5E9A) : KveGuiStyle.BORDER_FIELD);
            }
            int col = selected ? 0xFFFFFFFF : (e.enabled ? KveGuiStyle.TEXT_PRIMARY : KveGuiStyle.TEXT_MUTED);
            int previewW = 26;
            g.fill(L + 1, ry, L + 1 + previewW, ry + rowH - 2, 0x40000000);
            TradeEntityPreview.draw(g, e, L + 1, ry, previewW, rowH - 2);
            int tx = L + 3 + previewW, tw = LIST_W - 20 - previewW - 2;
            KveGuiStyle.drawTextSafe(g, e.id.isEmpty() ? Lang.get("gui.kve.trades.no_id") : e.id, tx, ry + 1, tw, col);
            if (!e.category.isEmpty()) {
                KveGuiStyle.drawTextSafe(g, e.category, tx, ry + 11, tw, KveGuiStyle.TEXT_MUTED);
            }
            KveGuiStyle.drawTextSafe(g, entrySummary(e), tx, ry + 21, tw, KveGuiStyle.TEXT_MUTED);
        }
        savedScrollBar.render(g);

        int ax = L + LIST_W + 12;
        int listTop = tradeOfferListTop;
        String summary = offersWithProblems > 0
                ? Lang.get("gui.kve.trades.offer_count_errors", draftOffers.size(), offersWithProblems)
                : Lang.get("gui.kve.trades.offer_count", draftOffers.size());
        KveGuiStyle.drawTextSafe(g, summary, ax + 8, tradeOfferRowY + 16, RIGHT_PANEL_W - 24,
                offersWithProblems > 0 ? KveGuiStyle.TEXT_ERROR : KveGuiStyle.TEXT_MUTED);

        int visible = Math.min(OFFER_ROWS_VISIBLE, Math.max(0, draftOffers.size() - offerScroll));
        String hoveredFullOfferLine = null;
        String hoveredProblemText = null;
        boolean levelledDraft = TradeExporter.hasLevels(buildDraftEntryReadOnly());
        for (int i = 0; i < visible; i++) {
            int idx = i + offerScroll;
            TradeOffer o = draftOffers.get(idx);
            int ry = listTop + i * OFFER_ROW_H;
            boolean sel = idx == selectedOfferIdx;
            List<String> rowProblems = problemsAt(idx);
            if (!rowProblems.isEmpty()) {
                KveGuiStyle.drawTextSafe(g, "!", ax + 1, ry, 7, KveGuiStyle.TEXT_ERROR);
                if (mx >= ax && mx < ax + 204 && my >= ry && my < ry + OFFER_ROW_H - 1) {
                    hoveredProblemText = String.join("; ", rowProblems);
                }
            }
            String line = (o.isEdit() ? "\u270E " : "")
                    + shortId(o.inputAId) + (o.inputBId != null && !o.inputBId.isEmpty() ? " + " + shortId(o.inputBId) : "")
                    + " -> " + shortId(o.outputId) + " x" + o.outputCount
                    + (o.unlockOffset > 0 || (o.isEdit() && levelledDraft)
                        ? " (nv." + o.effectiveLevel(draftLevel) + ")" : "");
            int col = !o.enabled ? KveGuiStyle.TEXT_MUTED : (sel ? KveGuiStyle.ACCENT_GOLD : KveGuiStyle.TEXT_PRIMARY);
            drawOfferIcons(g, o, ax + 9, ry);
            int textX = ax + 8 + OFFER_ICONS_W;
            int textW = 190 - OFFER_ICONS_W;
            KveGuiStyle.drawTextSafe(g, line, textX, ry, textW, col);
            if (mx >= ax + 8 && mx < ax + 204 && my >= ry && my < ry + OFFER_ROW_H - 1 && font.width(line) > textW) {
                String full = o.inputAId + (o.inputBId != null && !o.inputBId.isEmpty() ? " + " + o.inputBId : "")
                        + " -> " + o.outputId + " x" + o.outputCount;
                hoveredFullOfferLine = full;
            }
        }

        TradeEntry draftPreview = buildDraftEntryReadOnly();
        TradeBackend resolved = TradeExporter.resolveBackend(draftPreview);
        boolean unavailable = resolved != null && !resolved.isAvailable();
        String hintKey = resolved == null || unavailable ? "gui.kve.trades.no_backend"
                : "shovel_js_native".equals(resolved.id()) ? "gui.kve.trades.hint.native" : null;
        if (hintKey != null) {
            KveGuiStyle.drawTextSafe(g, Lang.get(hintKey), ax, tradeHintY, RIGHT_PANEL_W - 20,
                    resolved == null || unavailable ? KveGuiStyle.TEXT_WARN : KveGuiStyle.TEXT_MUTED);
        }

        int scx = sideX() + 6;
        int scw = sideW() - 11;
        TradeOffer sel = selectedOffer();
        if (sel == null) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.trades.side_empty"), scx, T + 29, scw, KveGuiStyle.TEXT_MUTED);
        } else {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.trades.side.title", selectedOfferIdx + 1, draftOffers.size()),
                    scx, sideTitleY, scw, KveGuiStyle.ACCENT_GOLD);
            drawSidePickIcons(g, sel, scx);
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.trades.side.nbt_label"), scx, sideLblNbtY, scw, KveGuiStyle.TEXT_LABEL);
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.trades.side.maxuses_xp_label"), scx, sideLblUsesY, scw, KveGuiStyle.TEXT_LABEL);
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.trades.side.price_label"), scx, sideLblPriceY, scw, KveGuiStyle.TEXT_LABEL);
            if (sel.isEdit() && sideLblEditY >= 0) {
                String target = shortId(sel.editInputAId)
                        + (sel.editInputBId != null && !sel.editInputBId.isEmpty() ? " + " + shortId(sel.editInputBId) : "")
                        + " -> " + shortId(sel.editOutputId);
                KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.trades.offer.edit_label", target), scx, sideLblEditY, scw, KveGuiStyle.ACCENT_GOLD);
            }
        }

        if (!flashMsg.isEmpty() && System.currentTimeMillis() - flashAt < 4000) {
            int flashY = actionRowTopY > 0 ? actionRowTopY - 14 : T + GUI_H - 34;
            KveGuiStyle.drawTextSafe(g, flashMsg, L + LIST_W + 12, flashY, GUI_W - LIST_W - 24, KveGuiStyle.TEXT_WARN);
        }

        if (hoveredProblemText != null) {
            KveGuiStyle.drawFramedTooltip(g, hoveredProblemText, mx, my, width, height, KveGuiStyle.TEXT_ERROR);
        } else if (hoveredFullOfferLine != null) {
            KveGuiStyle.drawFramedTooltip(g, hoveredFullOfferLine, mx, my, width, height);
        }

        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
    }

    private TradeEntry buildDraftEntryReadOnly() {
        TradeEntry e = new TradeEntry();
        e.id = draftId; e.enabled = draftEnabled; e.category = draftCategory;
        e.targetKind = draftKind; e.professionId = draftProfessionId; e.entityTypeId = draftEntityTypeId;
        e.level = draftLevel; e.mode = draftMode; e.backendId = draftBackendId;
        for (TradeOffer o : draftOffers) e.offers.add(o.copy());
        return e;
    }

    private static String shortId(String id) {
        if (id == null || id.isEmpty()) return "?";
        return id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        // un menu "Mas" abierto recibe el clic antes que lo que tenga debajo
        if (com.kve.client.gui.KveMenuButton.consumeClick(mx, my, btn)) return true;
        if (savedScrollBar.mouseClicked(mx, my, btn)) return true;
        int rowH = 34;
        int scroll = savedScrollBar.getScroll();
        for (int i = 0; i < Math.min(listRowsVisible(), savedList.size() - scroll); i++) {
            int ry = T + 30 + i * rowH;
            if (mx >= L + 2 && mx < L + LIST_W - 10 && my >= ry && my < ry + rowH - 2) {
                loadDraft(savedList.get(i + scroll));
                return true;
            }
        }

        int ax = L + LIST_W + 12;
        int listTop = tradeOfferListTop;
        int visible = Math.min(OFFER_ROWS_VISIBLE, Math.max(0, draftOffers.size() - offerScroll));
        for (int i = 0; i < visible; i++) {
            int ry = listTop + i * OFFER_ROW_H;
            if (mx >= ax + 8 && mx < ax + 204 && my >= ry && my < ry + OFFER_ROW_H - 1) {
                selectOffer(i + offerScroll);
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
        if (savedScrollBar.mouseDragged(mx, my, b, dx, dy)) return true;
        return super.mouseDragged(mx, my, b, dx, dy);
    }
    @Override public boolean mouseReleased(double mx, double my, int b) {
        savedScrollBar.mouseReleased(mx, my, b);
        return super.mouseReleased(mx, my, b);
    }
    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        int ax = L + LIST_W + 12;
        if (mx >= ax && mx < sideX()) {
            int total = draftOffers.size();
            offerScroll = Math.max(0, Math.min(Math.max(0, total - OFFER_ROWS_VISIBLE),
                    offerScroll - (int) Math.signum(delta)));
            return true;
        }
        if (mx >= L && mx < L + LIST_W) { savedScrollBar.mouseScrolled(delta); return true; }
        return super.mouseScrolled(mx, my, scrollX, delta);
    }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (getFocused() != null && getFocused().keyPressed(key, scan, mods)) return true;
        if (EditorKeys.isFocusTab(key, mods)) { super.keyPressed(key, scan, mods); return true; }
        return EditorKeys.swallow(mods);
    }
    @Override public boolean isPauseScreen() { return false; }
}
