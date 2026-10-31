package com.kve.client.gui.event;

import com.kve.client.gui.worldgen.BiomePickerScreen;
import com.kve.client.gui.DiffPreviewScreen;
import com.kve.client.gui.EditorKeys;
import com.kve.client.gui.picker.ItemPickerScreen;
import com.kve.client.gui.KveConfirmScreen;
import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KveScaledEditBox;
import com.kve.client.gui.mob.MobPickerScreen;
import com.kve.client.gui.recipe.RecipePreviewScreen;
import com.kve.client.gui.ScrollBar;
import com.kve.client.gui.picker.TypeOptionPickerScreen;

import org.lwjgl.glfw.GLFW;
import com.kve.common.Lang;
import com.kve.client.permission.ClientPermissionState;
import com.kve.client.gui.workspace.pending.PendingChange;
import com.kve.client.gui.workspace.pending.PendingChangeManager;
import com.kve.client.gui.workspace.pending.PendingDomain;
import com.kve.client.gui.studio.KveStudioShell;
import com.kve.common.config.KVEConfig;
import com.kve.common.event.EventEntry;
import com.kve.common.kubejs.EventExporter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class EventEditorScreen extends Screen {

    private enum CardId {
        IDENTITY, TRIGGER, COND_ACTIONS, LIMITS
    }

    private static final int GUI_W_MIN = 300;
    private int GUI_W = GUI_W_MIN;
    private static final int OUTER_PAD = 4;
    private static final int MIN_GUI_H = 160;
    private static final int PAD = 10;
    private static final int HEADER_H = 14;
    private static final int FIELD_ROW_H = 16;

    private static final int COND_ROWS_MAX = 3;
    private static final int COND_ROW_H = 13;
    private static final int ACTION_ROWS_VISIBLE = 4;
    private static final int ACTION_ROW_H = 14;

    private static final int SUMMARY_H = 44;
    private static final int SUMMARY_GAP = 6;
    private static final int SUMMARY_MAX_LINES = 3;

    private static final int BOTTOM_FIXED_H = 6 + 12 + 8;

    private int L, T, GUI_H;
    private int cardX, cardW;

    private CardId expandedCard = CardId.IDENTITY;

    private String currentKey = "";
    private String draftId = "";
    private String draftDisplayName = "";
    private String draftCategory = "";
    private boolean draftEnabled = true;

    private EventEntry.TriggerType draftTriggerType = EventEntry.TriggerType.FIRST_JOIN;
    private int draftIntervalSeconds = 60;
    private String draftEntityId = "";
    private int draftKillCount = 1;
    private String draftCommandName = "";
    private String draftBiomeId = "";
    private String draftDeathMessageContains = "";
    private float draftMinDamage = 1.0f;
    private String draftFromDimension = "";
    private String draftToDimension = "";
    private String draftBlockId = "";
    private String draftItemId = "";
    private int draftXpLevel = 1;
    private int draftXpMinDelta = 1;
    private String draftAdvancementId = "";
    private String draftTriggerEffectId = "";
    private String draftEntityTypeId = "";
    private String draftFluidId = "";

    private final List<EventEntry.Condition> draftConditions = new ArrayList<>();
    private final List<EventEntry.Action> draftActions = new ArrayList<>();
    private int actionsScroll = 0;

    private EventEntry.BrokenRefIndices brokenRefCache = new EventEntry.BrokenRefIndices(java.util.Set.of(), java.util.Set.of());
    private List<String> cachedRefWarnings = List.of();

    private boolean draftOncePerPlayer = false;
    private int draftCooldownSeconds = 0;
    private int draftMaxGlobalStarts = 0;

    private int identityHeaderY, triggerHeaderY, condActionsHeaderY, limitsHeaderY;
    private int idFieldY, nameFieldY, categoryFieldY;
    private int triggerBtnY, triggerFieldY;
    private int condHeaderTextY, condListY;
    private int actionsHeaderTextY, actionsListY;
    private int oncePerPlayerRowY, limitsFieldY;
    private int cardsBottomY;

    private Button saveBtn;

    private String flashMsg = "";
    private long flashAt = 0;
    private String pendingFlash = null;

    private Screen returnScreen;

    private boolean hosted = false;
    private int hostedW, hostedH;
    private Screen ownerTab = null;
    private Screen shellReturnScreen = null;
    private Runnable onEmbeddedExit = null;

    /** Fluent setter, llamado por {@code EventListScreen} al embeber esta pantalla. */
    public EventEditorScreen withEmbedding(Screen ownerTab, Screen shellReturnScreen, Runnable onEmbeddedExit) {
        this.ownerTab = ownerTab;
        this.shellReturnScreen = shellReturnScreen;
        this.onEmbeddedExit = onEmbeddedExit;
        return this;
    }

    public EventEditorScreen() {
        super(Lang.c("gui.kve.event.editor.title"));
    }

    public EventEditorScreen(Screen returnScreen) {
        this();
        this.returnScreen = returnScreen;
    }

    /**
     * Opens the editor already loaded with {@code preload} (an existing saved event
     * or a freshly-created template draft) - replaces the old pattern of
     * constructing blank then calling a private loadDraft() from outside.
     */
    public EventEditorScreen(Screen returnScreen, EventEntry preload) {
        this(returnScreen);
        if (preload != null)
            applyEntryToDraft(preload);
    }

    /**
     * Fluent setter so callers can send "Volver" back to a specific screen
     * (defaults to KveStudioShell), same convention as
     * TagEditorScreen#withReturnScreen().
     */
    public EventEditorScreen withReturnScreen(Screen s) {
        this.returnScreen = s;
        return this;
    }

    /**
 * shows the "Plantilla aplicada..." hint once, on this
     * screen's first render after being opened from {@link EventListScreen}'s
     * template picker - consumed on the first init() so later init() reruns
     * (toggling a card, editing a field) don't re-flash it.
     */
    public EventEditorScreen withAppliedTemplateHint() {
        this.pendingFlash = Lang.get("gui.kve.event.template.applied_hint");
        return this;
    }

    @Override
    protected void init() {
        if (KveStudioShell.reclaimIfHosted(this, ownerTab != null ? ownerTab : this, shellReturnScreen)) return;
        hosted = false;
        EventExporter.loadIfNeeded();
        buildWidgets(width - OUTER_PAD * 2, height - OUTER_PAD * 2, true, 0, 0);
    }

    /** Overload de KveStudioTab: EventListScreen llama esto directo (sin minecraft.setScreen()) para embeber esta pantalla en el rectangulo asignado a la lista. */
    public void init(int x0, int y0, int w, int h) {
        if (this.minecraft == null) {
            this.minecraft = net.minecraft.client.Minecraft.getInstance();
            this.font = this.minecraft.font;
        }
        hosted = true;
        hostedW = w;
        hostedH = h;
        EventExporter.loadIfNeeded();
        buildWidgets(w, h, false, x0, y0);
    }

    private void refresh() {
        if (hosted) buildWidgets(hostedW, hostedH, false, L, T);
        else init();
    }

    private void buildWidgets(int availW, int availH, boolean centered, int originX, int originY) {
        GUI_W = Math.max(GUI_W_MIN, availW);
        L = centered ? (width - GUI_W) / 2 : originX;
        cardX = L + PAD;
        cardW = GUI_W - 2 * PAD;

        int actionRowLines = actionButtonRowLines();
        int bottomAreaH = BOTTOM_FIXED_H + actionRowLines * KveGuiStyle.BTN_H
                + (actionRowLines - 1) * KveGuiStyle.BTN_GAP;
        int contentH = layoutCards(0, false) + bottomAreaH;
        int contentBasedH = Math.min(availH - 20, 16 + contentH);
        GUI_H = Math.max(MIN_GUI_H, Math.max(contentBasedH, availH));
        T = centered ? (height - GUI_H) / 2 : originY;

        clearWidgets();
        setFocused(null);

        if (pendingFlash != null) {
            flash(pendingFlash);
            pendingFlash = null;
        }

        int pendCount = PendingChangeManager.size();
        String applyLabel = pendCount > 0 ? Lang.get("gui.kve.pending.apply") + " (" + pendCount + ")"
                : Lang.get("gui.kve.pending.apply");
        String discardLabel = pendCount > 0 ? Lang.get("gui.kve.pending.discard") + " (" + pendCount + ")"
                : Lang.get("gui.kve.pending.discard");
        int applyW = KveGuiStyle.btnWidth(applyLabel);
        int discardW = KveGuiStyle.btnWidth(discardLabel);
        int discardX = L + GUI_W - 2 - discardW;
        int applyX = discardX - KveGuiStyle.BTN_GAP - applyW;
        Button applyPendBtn = addRenderableWidget(btn(applyLabel, applyX, T + 2, 0, this::onApplyPending));
        applyPendBtn.active = pendCount > 0 && ClientPermissionState.canApply();
        Button discardPendBtn = addRenderableWidget(btn(discardLabel, discardX, T + 2, 0, this::onDiscardPending));
        discardPendBtn.active = pendCount > 0;

        cardsBottomY = layoutCards(T + 16, true);

        int by = cardsBottomY + 6 + 12;
        int cx = cardX;
        KveGuiStyle.ButtonRow actionRow = new KveGuiStyle.ButtonRow(cardX, by, cardX + cardW - 4);
        KveGuiStyle.ActionBar bar = new KveGuiStyle.ActionBar(actionRow, this::addRenderableWidget);
        saveBtn = bar.primary(Lang.get("gui.kve.btn.save"), b -> save());
        bar.button(Lang.get("gui.kve.btn.preview"), b -> preview());
        bar.button(Lang.get("gui.kve.btn.duplicate"), b -> duplicateEvent());
        bar.history(Lang.get("gui.kve.btn.undo"), () -> undo(), !currentKey.isEmpty() && EventExporter.canUndo(currentKey))
                .history(Lang.get("gui.kve.btn.reset"), () -> resetDraft());
        bar.end(() -> delete());

        refreshRefWarnings();
        lastLayoutSig = expandedCard + "|" + draftConditions.size() + "|" + draftActions.size()
                + "|" + hostedW + "|" + hostedH + "|" + actionButtonRowLines();
    }

    private String lastLayoutSig = "";

    private void refreshRefWarnings() {
        EventEntry draft = buildDraftEntry();
        brokenRefCache = draft.brokenRefIndices();
        cachedRefWarnings = draft.referenceWarnings();
    }

    /** Lineas que ocupa la fila de acciones (mismo calculo que el ButtonRow): Guardar, Mas y Eliminar. */
    private int actionButtonRowLines() {
        String[] labels = {Lang.get("gui.kve.btn.save"), Lang.get("gui.kve.actions.more"), Lang.get("gui.kve.btn.delete")};
        int maxX = cardX + cardW - 4, x = cardX, lines = 1;
        for (int i = 0; i < labels.length; i++) {
            int bw = KveGuiStyle.btnWidth(labels[i]);
            if (i == labels.length - 1) x += 10;
            if (x != cardX && x + bw > maxX) { x = cardX; lines++; }
            x += bw + KveGuiStyle.BTN_GAP;
        }
        return lines;
    }

    private int layoutCards(int topY, boolean addWidgets) {
        KveGuiStyle.Layout ly = new KveGuiStyle.Layout(cardX, topY, FIELD_ROW_H);
        ly.gap(SUMMARY_H + SUMMARY_GAP);

        identityHeaderY = ly.row(HEADER_H);
        if (addWidgets)
            addRenderableWidget(headerBtn(CardId.IDENTITY, identityHeaderY));
        if (expandedCard == CardId.IDENTITY) {
            idFieldY = ly.row();
            if (addWidgets) {
                EditBox fId = field(cardX, idFieldY, cardW - 60, draftId, Lang.c("gui.kve.event.id"));
                fId.setHint(Lang.c("gui.kve.tag.id_hint"));
                fId.setResponder(s -> draftId = s);
                addRenderableWidget(fId);
                addRenderableWidget(
                        btn(draftEnabled ? Lang.get("gui.kve.status.enabled") : Lang.get("gui.kve.status.disabled"),
                                cardX + cardW - 56, idFieldY, 0, this::toggleEnabled));
            }
            nameFieldY = ly.row();
            if (addWidgets) {
                EditBox fName = field(cardX, nameFieldY, cardW, draftDisplayName, Lang.c("gui.kve.event.display_name"));
                fName.setResponder(s -> draftDisplayName = s);
                addRenderableWidget(fName);
            }
            categoryFieldY = ly.row();
            if (addWidgets) {
                EditBox fCat = field(cardX, categoryFieldY, cardW, draftCategory, Lang.c("gui.kve.common.category"));
                fCat.setHint(Lang.c("gui.kve.common.category_hint"));
                fCat.setResponder(s -> draftCategory = s);
                addRenderableWidget(fCat);
            }
        }
        ly.gap(2);

        triggerHeaderY = ly.row(HEADER_H);
        if (addWidgets)
            addRenderableWidget(headerBtn(CardId.TRIGGER, triggerHeaderY));
        if (expandedCard == CardId.TRIGGER) {
            triggerBtnY = ly.row();
            if (addWidgets) {
                addRenderableWidget(btn(Lang.get("gui.kve.event.trigger_btn", triggerLabel(draftTriggerType)), cardX,
                        triggerBtnY, 0, this::openTriggerPicker));
            }
            triggerFieldY = ly.row();
            if (addWidgets)
                addTriggerFieldWidgets(triggerFieldY, cardW);
            ly.gap(FIELD_ROW_H);
        }
        ly.gap(2);

        condActionsHeaderY = ly.row(HEADER_H);
        if (addWidgets)
            addRenderableWidget(headerBtn(CardId.COND_ACTIONS, condActionsHeaderY));
        if (expandedCard == CardId.COND_ACTIONS) {
            condHeaderTextY = ly.row(12);
            if (addWidgets) {
                String addCondLabel = Lang.get("gui.kve.event.add_condition");
                int addCondW = KveGuiStyle.btnWidth(addCondLabel);
                addRenderableWidget(btn(addCondLabel, cardX + cardW - addCondW,
                        condHeaderTextY - 2, 0, this::addCondition)).active = draftConditions.size() < COND_ROWS_MAX;
            }
            condListY = ly.y();
            ly.gap(COND_ROWS_MAX * COND_ROW_H);
            if (addWidgets) {
                for (int i = 0; i < Math.min(COND_ROWS_MAX, draftConditions.size()); i++) {
                    int ry = condListY + i * COND_ROW_H;
                    int idx = i;
                    int editCondW = KveGuiStyle.btnWidth(Lang.get("gui.kve.btn.edit"));
                    addRenderableWidget(btn(Lang.get("gui.kve.btn.edit"), cardX, ry, 0, () -> editCondition(idx)));
                    addRenderableWidget(btn("X", cardX + editCondW + KveGuiStyle.BTN_GAP, ry, 14, () -> removeCondition(idx)));
                }
            }
            ly.gap(6);

            actionsHeaderTextY = ly.row(12);
            if (addWidgets) {
                String addActLabel = Lang.get("gui.kve.event.add_action");
                int addActW = KveGuiStyle.btnWidth(addActLabel);
                addRenderableWidget(btn(addActLabel, cardX + cardW - addActW,
                        actionsHeaderTextY - 2, 0, this::addAction));
            }
            actionsListY = ly.y();
            ly.gap(ACTION_ROWS_VISIBLE * ACTION_ROW_H);
            if (addWidgets) {
                int maxActionsScroll = Math.max(0, draftActions.size() - ACTION_ROWS_VISIBLE);
                actionsScroll = Math.max(0, Math.min(maxActionsScroll, actionsScroll));
                for (int i = 0; i < ACTION_ROWS_VISIBLE; i++) {
                    int idx = i + actionsScroll;
                    if (idx >= draftActions.size())
                        break;
                    int ry = actionsListY + i * ACTION_ROW_H;
                    int finalIdx = idx;
                    int aCx = cardX;
                    int editActW = KveGuiStyle.btnWidth(Lang.get("gui.kve.btn.edit"));
                    addRenderableWidget(btn(Lang.get("gui.kve.btn.edit"), aCx, ry, 0, () -> editAction(finalIdx)));
                    aCx += editActW + KveGuiStyle.BTN_GAP;
                    Button up = addRenderableWidget(btn("^", aCx, ry, 12, () -> moveAction(finalIdx, -1)));
                    up.active = idx > 0;
                    aCx += 12 + KveGuiStyle.BTN_GAP;
                    Button down = addRenderableWidget(btn("v", aCx, ry, 12, () -> moveAction(finalIdx, 1)));
                    down.active = idx < draftActions.size() - 1;
                    aCx += 12 + KveGuiStyle.BTN_GAP;
                    addRenderableWidget(btn("X", aCx, ry, 12, () -> removeAction(finalIdx)));
                }
            }
            ly.gap(10);
        }
        ly.gap(2);

        limitsHeaderY = ly.row(HEADER_H);
        if (addWidgets)
            addRenderableWidget(headerBtn(CardId.LIMITS, limitsHeaderY));
        if (expandedCard == CardId.LIMITS) {
            oncePerPlayerRowY = ly.row();
            if (addWidgets) {
                addRenderableWidget(btn(
                        draftOncePerPlayer ? Lang.get("gui.kve.event.once_per_player_on")
                                : Lang.get("gui.kve.event.once_per_player_off"),
                        cardX, oncePerPlayerRowY, 0, this::toggleOncePerPlayer));
            }
            limitsFieldY = ly.row();
            if (addWidgets) {
                EditBox fCooldown = field(cardX, limitsFieldY, cardW / 2 - 2, String.valueOf(draftCooldownSeconds),
                        Lang.c("gui.kve.event.cooldown"));
                fCooldown.setHint(Lang.c("gui.kve.event.cooldown_hint"));
                fCooldown.setResponder(s -> draftCooldownSeconds = parseInt(s, draftCooldownSeconds));
                addRenderableWidget(fCooldown);
                EditBox fMaxGlobal = field(cardX + cardW / 2 + 2, limitsFieldY, cardW / 2 - 2,
                        String.valueOf(draftMaxGlobalStarts), Lang.c("gui.kve.event.max_global_starts"));
                fMaxGlobal.setHint(Lang.c("gui.kve.event.max_global_starts_hint"));
                fMaxGlobal.setResponder(s -> draftMaxGlobalStarts = parseInt(s, draftMaxGlobalStarts));
                addRenderableWidget(fMaxGlobal);
            }
        }

        return ly.y();
    }

    private Button headerBtn(CardId id, int y) {
        String marker = expandedCard == id ? "v" : ">";
        String label = marker + " " + cardOrdinal(id) + ". " + cardTitle(id);
        if (expandedCard != id)
            label += "  -  " + cardCollapsedSummary(id);
        return btn(label, cardX, y, 0, () -> {
            expandedCard = (expandedCard == id) ? null : id;
            refresh();
        });
    }

    private int cardOrdinal(CardId id) {
        return switch (id) {
            case IDENTITY -> 1;
            case TRIGGER -> 2;
            case COND_ACTIONS -> 3;
            case LIMITS -> 4;
        };
    }

    private String cardTitle(CardId id) {
        return switch (id) {
            case IDENTITY -> Lang.get("gui.kve.event.card.identity");
            case TRIGGER -> Lang.get("gui.kve.event.card.trigger");
            case COND_ACTIONS -> Lang.get("gui.kve.event.card.cond_actions");
            case LIMITS -> Lang.get("gui.kve.event.card.limits");
        };
    }

    private String cardCollapsedSummary(CardId id) {
        return switch (id) {
            case IDENTITY -> {
                String idPart = draftId.isEmpty() ? Lang.get("gui.kve.event.card.no_id") : draftId;
                String statePart = draftEnabled ? Lang.get("gui.kve.status.enabled")
                        : Lang.get("gui.kve.status.disabled");
                yield idPart + " - " + statePart;
            }
            case TRIGGER -> triggerSummaryClause();
            case COND_ACTIONS ->
                Lang.get("gui.kve.event.card.cond_actions_summary", draftConditions.size(), draftActions.size());
            case LIMITS -> Lang.get("gui.kve.event.card.limits_summary", draftCooldownSeconds, draftMaxGlobalStarts,
                    draftOncePerPlayer ? Lang.get("gui.kve.event.once_per_player_on")
                            : Lang.get("gui.kve.event.once_per_player_off"));
        };
    }

    private void addTriggerFieldWidgets(int y, int width) {
        switch (draftTriggerType) {
            case FIRST_JOIN, PLAYER_LOGIN -> {
                }
            case INTERVAL -> {
                EditBox f = field(cardX, y, width, String.valueOf(draftIntervalSeconds),
                        Lang.c("gui.kve.event.interval_seconds"));
                f.setHint(Lang.c("gui.kve.event.interval_seconds_hint"));
                f.setResponder(s -> draftIntervalSeconds = parseInt(s, draftIntervalSeconds));
                addRenderableWidget(f);
            }
            case PLAYER_KILL -> {
                EditBox f1 = field(cardX, y, width - 40, draftEntityId, Lang.c("gui.kve.event.entity_id"));
                f1.setHint(Lang.c("gui.kve.tag.id_hint"));
                f1.setResponder(s -> draftEntityId = s);
                addRenderableWidget(f1);
                addRenderableWidget(pickBtn(Lang.get("gui.kve.event.action.pick_mob"), cardX, width, y,
                        this::pickTriggerMob));
                EditBox f2 = field(cardX, y + FIELD_ROW_H, width, String.valueOf(draftKillCount),
                        Lang.c("gui.kve.event.kill_count"));
                f2.setHint(Lang.c("gui.kve.event.kill_count_hint"));
                f2.setResponder(s -> draftKillCount = parseInt(s, draftKillCount));
                addRenderableWidget(f2);
            }
            case COMMAND -> {
                EditBox f = field(cardX, y, width, draftCommandName, Lang.c("gui.kve.event.command_name"));
                f.setHint(Lang.c("gui.kve.event.command_name_hint"));
                f.setResponder(s -> draftCommandName = s);
                addRenderableWidget(f);
            }
            case ENTER_BIOME -> {
                EditBox f = field(cardX, y, width - 40, draftBiomeId, Lang.c("gui.kve.event.biome_id"));
                f.setHint(Lang.c("gui.kve.tag.id_hint"));
                f.setResponder(s -> draftBiomeId = s);
                addRenderableWidget(f);
                addRenderableWidget(pickBtn("...", cardX, width, y,
                        this::pickTriggerBiome));
            }
            case PLAYER_DEATH -> {
                EditBox f = field(cardX, y, width, draftDeathMessageContains,
                        Lang.c("gui.kve.event.death_message_contains"));
                f.setHint(Lang.c("gui.kve.event.death_message_contains_hint"));
                f.setResponder(s -> draftDeathMessageContains = s);
                addRenderableWidget(f);
            }
            case PLAYER_RESPAWN -> {
                }
            case PLAYER_HURT -> {
                EditBox f = field(cardX, y, width, fmtF(draftMinDamage), Lang.c("gui.kve.event.min_damage"));
                f.setHint(Lang.c("gui.kve.event.min_damage_hint"));
                f.setResponder(s -> draftMinDamage = parseFloat(s, draftMinDamage));
                addRenderableWidget(f);
            }
            case DIMENSION_CHANGE -> {
                EditBox f1 = field(cardX, y, width, draftToDimension, Lang.c("gui.kve.event.to_dimension"));
                f1.setHint(Lang.c("gui.kve.tag.id_hint"));
                f1.setResponder(s -> draftToDimension = s);
                addRenderableWidget(f1);
                EditBox f2 = field(cardX, y + FIELD_ROW_H, width, draftFromDimension,
                        Lang.c("gui.kve.event.from_dimension"));
                f2.setHint(Lang.c("gui.kve.event.from_dimension_hint"));
                f2.setResponder(s -> draftFromDimension = s);
                addRenderableWidget(f2);
            }
            case BLOCK_BREAK, BLOCK_PLACE -> {
                EditBox f1 = field(cardX, y, width, draftBlockId, Lang.c("gui.kve.event.block_id"));
                f1.setHint(Lang.c("gui.kve.tag.id_hint"));
                f1.setResponder(s -> draftBlockId = s);
                addRenderableWidget(f1);
                EditBox f2 = field(cardX, y + FIELD_ROW_H, width, String.valueOf(draftKillCount),
                        Lang.c("gui.kve.event.count"));
                f2.setHint(Lang.c("gui.kve.event.block_count_hint"));
                f2.setResponder(s -> draftKillCount = parseInt(s, draftKillCount));
                addRenderableWidget(f2);
            }
            case ITEM_PICKUP, ITEM_CRAFT -> {
                EditBox f1 = field(cardX, y, width - 40, draftItemId, Lang.c("gui.kve.event.item_id"));
                f1.setHint(Lang.c("gui.kve.tag.id_hint"));
                f1.setResponder(s -> draftItemId = s);
                addRenderableWidget(f1);
                addRenderableWidget(pickBtn(Lang.get("gui.kve.event.action.pick_item"), cardX, width, y,
                        this::pickTriggerItem));
                EditBox f2 = field(cardX, y + FIELD_ROW_H, width, String.valueOf(draftKillCount),
                        Lang.c("gui.kve.event.count"));
                f2.setHint(Lang.c("gui.kve.event.block_count_hint"));
                f2.setResponder(s -> draftKillCount = parseInt(s, draftKillCount));
                addRenderableWidget(f2);
            }
            case XP_LEVEL_REACH -> {
                EditBox f = field(cardX, y, width, String.valueOf(draftXpLevel), Lang.c("gui.kve.event.xp_level"));
                f.setHint(Lang.c("gui.kve.event.xp_level_hint"));
                f.setResponder(s -> draftXpLevel = parseInt(s, draftXpLevel));
                addRenderableWidget(f);
            }
            case XP_CHANGED -> {
                EditBox f = field(cardX, y, width, String.valueOf(draftXpMinDelta),
                        Lang.c("gui.kve.event.xp_min_delta"));
                f.setHint(Lang.c("gui.kve.event.xp_min_delta_hint"));
                f.setResponder(s -> draftXpMinDelta = parseInt(s, draftXpMinDelta));
                addRenderableWidget(f);
            }
            case ADVANCEMENT_EARNED -> {
                EditBox f = field(cardX, y, width, draftAdvancementId, Lang.c("gui.kve.event.advancement_id"));
                f.setHint(Lang.c("gui.kve.tag.id_hint"));
                f.setResponder(s -> draftAdvancementId = s);
                addRenderableWidget(f);
            }
            case EFFECT_GAINED -> {
                EditBox f = field(cardX, y, width, draftTriggerEffectId, Lang.c("gui.kve.event.trigger_effect_id"));
                f.setHint(Lang.c("gui.kve.tag.id_hint"));
                f.setResponder(s -> draftTriggerEffectId = s);
                addRenderableWidget(f);
            }
            case ITEM_USE, ITEM_CONSUME, ITEM_SMELT -> {
                EditBox f1 = field(cardX, y, width - 40, draftItemId, Lang.c("gui.kve.event.item_id"));
                f1.setHint(Lang.c("gui.kve.tag.id_hint"));
                f1.setResponder(s -> draftItemId = s);
                addRenderableWidget(f1);
                addRenderableWidget(pickBtn(Lang.get("gui.kve.event.action.pick_item"), cardX, width, y,
                        this::pickTriggerItem));
            }
            case FISHING_CATCH, VILLAGER_TRADE -> {
                EditBox f1 = field(cardX, y, width - 40, draftItemId, Lang.c("gui.kve.event.item_id_optional"));
                f1.setHint(Lang.c("gui.kve.event.item_id_optional_hint"));
                f1.setResponder(s -> draftItemId = s);
                addRenderableWidget(f1);
                addRenderableWidget(pickBtn(Lang.get("gui.kve.event.action.pick_item"), cardX, width, y,
                        this::pickTriggerItem));
            }
            case ENTITY_KILL_ANY -> {
                EditBox f = field(cardX, y, width, String.valueOf(draftKillCount), Lang.c("gui.kve.event.kill_count"));
                f.setHint(Lang.c("gui.kve.event.kill_count_hint"));
                f.setResponder(s -> draftKillCount = parseInt(s, draftKillCount));
                addRenderableWidget(f);
            }
            case ENTITY_INTERACT -> {
                EditBox f1 = field(cardX, y, width - 40, draftEntityTypeId, Lang.c("gui.kve.event.entity_type_id"));
                f1.setHint(Lang.c("gui.kve.tag.id_hint"));
                f1.setResponder(s -> draftEntityTypeId = s);
                addRenderableWidget(f1);
                addRenderableWidget(pickBtn(Lang.get("gui.kve.event.action.pick_mob"), cardX, width, y,
                        this::pickTriggerEntityType));
            }
            case TAMING, BREEDING, MOUNT -> {
                EditBox f1 = field(cardX, y, width - 40, draftEntityTypeId,
                        Lang.c("gui.kve.event.entity_type_id_optional"));
                f1.setHint(Lang.c("gui.kve.event.entity_type_id_optional_hint"));
                f1.setResponder(s -> draftEntityTypeId = s);
                addRenderableWidget(f1);
                addRenderableWidget(pickBtn(Lang.get("gui.kve.event.action.pick_mob"), cardX, width, y,
                        this::pickTriggerEntityType));
            }
            case PLAYER_DISCONNECT, SERVER_STARTUP -> {
                }
            case EMPTY_BUCKET, FILL_BUCKET -> {
                EditBox f = field(cardX, y, width, draftFluidId, Lang.c("gui.kve.event.fluid_id_optional"));
                f.setHint(Lang.c("gui.kve.event.fluid_id_optional_hint"));
                f.setResponder(s -> draftFluidId = s);
                addRenderableWidget(f);
            }
            case ENTITY_SPAWN, ENTITY_DEATH, ENTITY_ATTACKS -> {
                EditBox f1 = field(cardX, y, width - 40, draftEntityTypeId,
                        Lang.c("gui.kve.event.entity_type_id_optional"));
                f1.setHint(Lang.c("gui.kve.event.entity_type_id_optional_hint"));
                f1.setResponder(s -> draftEntityTypeId = s);
                addRenderableWidget(f1);
                addRenderableWidget(pickBtn(Lang.get("gui.kve.event.action.pick_mob"), cardX, width, y,
                        this::pickTriggerEntityType));
            }
        }
    }

    private void pickTriggerEntityType() {
        minecraft.setScreen(new MobPickerScreen(this, (EntityType<?> et) -> {
            ResourceLocation k = BuiltInRegistries.ENTITY_TYPE.getKey(et);
            if (k != null)
                draftEntityTypeId = k.toString();
        }));
    }

    private void pickTriggerItem() {
        minecraft.setScreen(new ItemPickerScreen(this, (net.minecraft.world.item.ItemStack stack) -> {
            ResourceLocation k = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (k != null)
                draftItemId = k.toString();
        }));
    }

    private void pickTriggerMob() {
        minecraft.setScreen(new MobPickerScreen(this, (EntityType<?> et) -> {
            ResourceLocation k = BuiltInRegistries.ENTITY_TYPE.getKey(et);
            if (k != null)
                draftEntityId = k.toString();
        }));
    }

    private void pickTriggerBiome() {
        minecraft.setScreen(new BiomePickerScreen(this,
                draftBiomeId.isEmpty() ? java.util.List.of() : java.util.List.of(draftBiomeId), true,
                picked -> { if (!picked.isEmpty()) draftBiomeId = picked.get(0); }));
    }

    private EditBox field(int x, int y, int w, String value, Component narration) {
        EditBox f = new KveScaledEditBox(font, x, y, w, 12, narration);
        f.setMaxLength(128);
        f.setValue(value);
        return f;
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

    private void openTriggerPicker() {
        List<TypeOptionPickerScreen.Option> opts = new ArrayList<>();
        EventEntry.TriggerType[] values = EventEntry.TriggerType.values();
        for (EventEntry.TriggerType t : values) {
            if (isStubTrigger(t) && !KVEConfig.eventsShowStubTriggers && draftTriggerType != t)
                continue;
            opts.add(new TypeOptionPickerScreen.Option(triggerLabel(t), triggerDesc(t), () -> {
                draftTriggerType = t;
                minecraft.setScreen(this);
            }));
        }
        minecraft.setScreen(new TypeOptionPickerScreen(this, Lang.get("gui.kve.event.trigger.pick_title"), opts,
                draftTriggerType.ordinal()));
    }

    private boolean isStubTrigger(EventEntry.TriggerType t) {
        return false;
    }

    private String triggerDesc(EventEntry.TriggerType t) {
        return switch (t) {
            case FIRST_JOIN -> Lang.get("gui.kve.event.trigger.desc.first_join");
            case PLAYER_LOGIN -> Lang.get("gui.kve.event.trigger.desc.player_login");
            case INTERVAL -> Lang.get("gui.kve.event.trigger.desc.interval");
            case PLAYER_KILL -> Lang.get("gui.kve.event.trigger.desc.player_kill");
            case COMMAND -> Lang.get("gui.kve.event.trigger.desc.command");
            case ENTER_BIOME -> Lang.get("gui.kve.event.trigger.desc.enter_biome");
            case PLAYER_DEATH -> Lang.get("gui.kve.event.trigger.desc.player_death");
            case PLAYER_RESPAWN -> Lang.get("gui.kve.event.trigger.desc.player_respawn");
            case PLAYER_HURT -> Lang.get("gui.kve.event.trigger.desc.player_hurt");
            case DIMENSION_CHANGE -> Lang.get("gui.kve.event.trigger.desc.dimension_change");
            case BLOCK_BREAK -> Lang.get("gui.kve.event.trigger.desc.block_break");
            case BLOCK_PLACE -> Lang.get("gui.kve.event.trigger.desc.block_place");
            case ITEM_PICKUP -> Lang.get("gui.kve.event.trigger.desc.item_pickup");
            case ITEM_CRAFT -> Lang.get("gui.kve.event.trigger.desc.item_craft");
            case XP_LEVEL_REACH -> Lang.get("gui.kve.event.trigger.desc.xp_level_reach");
            case XP_CHANGED -> Lang.get("gui.kve.event.trigger.desc.xp_changed");
            case ADVANCEMENT_EARNED -> Lang.get("gui.kve.event.trigger.desc.advancement_earned");
            case EFFECT_GAINED -> Lang.get("gui.kve.event.trigger.desc.effect_gained");
            case ITEM_USE -> Lang.get("gui.kve.event.trigger.desc.item_use");
            case ITEM_CONSUME -> Lang.get("gui.kve.event.trigger.desc.item_consume");
            case ITEM_SMELT -> Lang.get("gui.kve.event.trigger.desc.item_smelt");
            case FISHING_CATCH -> Lang.get("gui.kve.event.trigger.desc.fishing_catch");
            case VILLAGER_TRADE -> Lang.get("gui.kve.event.trigger.desc.villager_trade");
            case ENTITY_KILL_ANY -> Lang.get("gui.kve.event.trigger.desc.entity_kill_any");
            case ENTITY_INTERACT -> Lang.get("gui.kve.event.trigger.desc.entity_interact");
            case TAMING -> Lang.get("gui.kve.event.trigger.desc.taming");
            case BREEDING -> Lang.get("gui.kve.event.trigger.desc.breeding");
            case MOUNT -> Lang.get("gui.kve.event.trigger.desc.mount");
            case PLAYER_DISCONNECT -> Lang.get("gui.kve.event.trigger.desc.player_disconnect");
            case SERVER_STARTUP -> Lang.get("gui.kve.event.trigger.desc.server_startup");
            case EMPTY_BUCKET -> Lang.get("gui.kve.event.trigger.desc.empty_bucket");
            case FILL_BUCKET -> Lang.get("gui.kve.event.trigger.desc.fill_bucket");
            case ENTITY_SPAWN -> Lang.get("gui.kve.event.trigger.desc.entity_spawn");
            case ENTITY_DEATH -> Lang.get("gui.kve.event.trigger.desc.entity_death");
            case ENTITY_ATTACKS -> Lang.get("gui.kve.event.trigger.desc.entity_attacks");
        };
    }

    private void toggleEnabled() {
        draftEnabled = !draftEnabled;
        refresh();
    }

    private void toggleOncePerPlayer() {
        draftOncePerPlayer = !draftOncePerPlayer;
        refresh();
    }

    private void addCondition() {
        if (draftConditions.size() >= COND_ROWS_MAX)
            return;
        minecraft.setScreen(new EventConditionEditScreen(this, c -> {
            draftConditions.add(c);
            minecraft.setScreen(this);
        }));
    }

    private void editCondition(int idx) {
        if (idx < 0 || idx >= draftConditions.size())
            return;
        EventEntry.Condition existing = draftConditions.get(idx);
        minecraft.setScreen(new EventConditionEditScreen(this, c -> {
            draftConditions.set(idx, c);
            minecraft.setScreen(this);
        }).withExisting(existing));
    }

    private void removeCondition(int idx) {
        if (idx < 0 || idx >= draftConditions.size())
            return;
        draftConditions.remove(idx);
        refresh();
    }

    private void addAction() {
        minecraft.setScreen(new EventActionEditScreen(this, a -> {
            draftActions.add(a);
            minecraft.setScreen(this);
        }));
    }

    private void editAction(int idx) {
        if (idx < 0 || idx >= draftActions.size())
            return;
        EventEntry.Action existing = draftActions.get(idx);
        minecraft.setScreen(new EventActionEditScreen(this, a -> {
            draftActions.set(idx, a);
            minecraft.setScreen(this);
        }).withExisting(existing));
    }

    private void removeAction(int idx) {
        if (idx < 0 || idx >= draftActions.size())
            return;
        draftActions.remove(idx);
        if (actionsScroll > 0 && actionsScroll >= draftActions.size())
            actionsScroll--;
        refresh();
    }

    private void moveAction(int idx, int delta) {
        int j = idx + delta;
        if (idx < 0 || idx >= draftActions.size() || j < 0 || j >= draftActions.size())
            return;
        EventEntry.Action tmp = draftActions.get(idx);
        draftActions.set(idx, draftActions.get(j));
        draftActions.set(j, tmp);
        refresh();
    }

    private void newEvent() {
        currentKey = "";
        draftId = "";
        draftDisplayName = "";
        draftCategory = "";
        draftEnabled = true;
        draftTriggerType = EventEntry.TriggerType.FIRST_JOIN;
        draftIntervalSeconds = 60;
        draftEntityId = "";
        draftKillCount = 1;
        draftCommandName = "";
        draftBiomeId = "";
        draftDeathMessageContains = "";
        draftMinDamage = 1.0f;
        draftFromDimension = "";
        draftToDimension = "";
        draftBlockId = "";
        draftItemId = "";
        draftXpLevel = 1;
        draftXpMinDelta = 1;
        draftAdvancementId = "";
        draftTriggerEffectId = "";
        draftEntityTypeId = "";
        draftFluidId = "";
        draftConditions.clear();
        draftActions.clear();
        actionsScroll = 0;
        draftOncePerPlayer = false;
        draftCooldownSeconds = 0;
        draftMaxGlobalStarts = 0;
        refresh();
    }

    private void duplicateEvent() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }
        currentKey = "";
        String base = draftId.trim();
        String candidate = (base.isEmpty() ? "shovel_js_event" : base) + "_copy";
        String finalId = candidate;
        int n = 2;
        while (EventExporter.getEntry(finalId) != null) { finalId = candidate + n; n++; }
        draftId = finalId;
        flash(Lang.get("gui.kve.event.duplicated_hint"));
        refresh();
    }

    private void resetDraft() {
        if (currentKey.isEmpty()) {
            newEvent();
            return;
        }
        EventEntry e = EventExporter.getEntry(currentKey);
        if (e != null)
            loadDraft(e);
        else
            newEvent();
    }

    private void loadDraft(EventEntry e) {
        applyEntryToDraft(e);
        refresh();
    }

    private void applyEntryToDraft(EventEntry e) {
        currentKey = e.key();
        draftId = e.id;
        draftDisplayName = e.displayName;
        draftCategory = e.category;
        draftEnabled = e.enabled;
        draftTriggerType = e.trigger.type;
        draftIntervalSeconds = e.trigger.intervalSeconds;
        draftEntityId = e.trigger.entityId;
        draftKillCount = e.trigger.count;
        draftCommandName = e.trigger.commandName;
        draftBiomeId = e.trigger.biomeId;
        draftDeathMessageContains = e.trigger.deathMessageContains;
        draftMinDamage = e.trigger.minDamage;
        draftFromDimension = e.trigger.fromDimension;
        draftToDimension = e.trigger.toDimension;
        draftBlockId = e.trigger.blockId;
        draftItemId = e.trigger.itemId;
        draftXpLevel = e.trigger.xpLevel;
        draftXpMinDelta = e.trigger.xpMinDelta;
        draftAdvancementId = e.trigger.advancementId;
        draftTriggerEffectId = e.trigger.effectId;
        draftEntityTypeId = e.trigger.entityTypeId;
        draftFluidId = e.trigger.fluidId;
        draftConditions.clear();
        for (EventEntry.Condition c : e.conditions)
            draftConditions.add(c.copy());
        draftActions.clear();
        for (EventEntry.Action a : e.actions)
            draftActions.add(a.copy());
        actionsScroll = 0;
        draftOncePerPlayer = e.limits.oncePerPlayer;
        draftCooldownSeconds = e.limits.cooldownSeconds;
        draftMaxGlobalStarts = e.limits.maxGlobalStarts;
    }

    private List<String> cachedStructuralProblems = List.of();
    private long structuralProblemsAt = 0L;

    private EventEntry buildDraftEntry() {
        EventEntry e = new EventEntry();
        e.id = draftId.trim();
        e.displayName = draftDisplayName.trim();
        e.category = draftCategory.trim();
        e.enabled = draftEnabled;

        e.trigger.type = draftTriggerType;
        e.trigger.intervalSeconds = draftIntervalSeconds;
        e.trigger.entityId = draftEntityId.trim();
        e.trigger.count = draftKillCount;
        e.trigger.commandName = draftCommandName.trim();
        e.trigger.biomeId = draftBiomeId.trim();
        e.trigger.deathMessageContains = draftDeathMessageContains.trim();
        e.trigger.minDamage = draftMinDamage;
        e.trigger.fromDimension = draftFromDimension.trim();
        e.trigger.toDimension = draftToDimension.trim();
        e.trigger.blockId = draftBlockId.trim();
        e.trigger.itemId = draftItemId.trim();
        e.trigger.xpLevel = draftXpLevel;
        e.trigger.xpMinDelta = draftXpMinDelta;
        e.trigger.advancementId = draftAdvancementId.trim();
        e.trigger.effectId = draftTriggerEffectId.trim();
        e.trigger.entityTypeId = draftEntityTypeId.trim();
        e.trigger.fluidId = draftFluidId.trim();

        for (EventEntry.Condition c : draftConditions)
            e.conditions.add(c.copy());
        for (EventEntry.Action a : draftActions)
            e.actions.add(a.copy());

        e.limits.oncePerPlayer = draftOncePerPlayer;
        e.limits.cooldownSeconds = draftCooldownSeconds;
        e.limits.maxGlobalStarts = draftMaxGlobalStarts;
        e.requiresRestart = EventExporter.needsForgeBridge(e.trigger.type) && !EventExporter.forgeBridgeInstalled();
        return e;
    }

    private void save() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }
        EventEntry e = buildDraftEntry();
        if (e.id == null || e.id.isBlank()) {
            e.id = KveGuiStyle.randomId();
            draftId = e.id;
        }

        List<String> problems = EventExporter.validateEvent(e);
        if (!problems.isEmpty()) {
            flash(String.join(" ", problems));
            return;
        }

        String oldKey = (!currentKey.isEmpty() && !currentKey.equals(e.key())) ? currentKey : null;
        EventEntry previousUnderOldKey = oldKey != null ? EventExporter.getEntry(oldKey) : null;
        EventEntry previous = EventExporter.getEntry(e.key());
        PendingChange.Action action = previous == null ? PendingChange.Action.CREATE : PendingChange.Action.EDIT;

        if (oldKey != null)
            EventExporter.stageDeleteEvent(oldKey);
        EventExporter.stageEvent(e);

        PendingChangeManager.queue(PendingChange.of(PendingDomain.EVENTS, e.key(), action, e.requiresRestart,
                EventExporter::commitEvents,
                () -> {
                    EventExporter.discardStagedEvent(e.key(), previous);
                    if (oldKey != null)
                        EventExporter.discardStagedEvent(oldKey, previousUnderOldKey);
                })
                .withDiff(previous == null ? null : () -> EventExporter.previewEventJS(previous),
                        () -> EventExporter.previewEventJS(e)));

        currentKey = e.key();
        flash(e.requiresRestart
                ? Lang.get("gui.kve.pending.queued_hint_restart")
                : Lang.get("gui.kve.pending.queued_hint_reload"));
        refresh();
    }

    private void delete() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }
        if (currentKey.isEmpty()) {
            flash(Lang.get("gui.kve.event.select_first"));
            return;
        }
        String key = currentKey;
        EventEntry previous = EventExporter.getEntry(key);
        EventExporter.stageDeleteEvent(key);
        PendingChangeManager.queue(PendingChange.of(PendingDomain.EVENTS, key, PendingChange.Action.DELETE, false,
                EventExporter::commitEvents,
                () -> {
                    if (previous != null)
                        EventExporter.stageEvent(previous);
                })
                .withDiff(previous == null ? null : () -> EventExporter.previewEventJS(previous), null));
        flash(Lang.get("gui.kve.pending.queued_hint_reload"));
        newEvent();
    }

    private void preview() {
        EventEntry e = buildDraftEntry();
        if (e.id.isEmpty()) {
            flash(Lang.get("gui.kve.event.type_id_first"));
            return;
        }
        String script = EventExporter.previewEventJS(e);
        minecraft.setScreen(new RecipePreviewScreen(this, e.id, script));
    }

    private void undo() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }
        if (currentKey.isEmpty())
            return;
        String key = currentKey;
        EventEntry beforeUndo = EventExporter.getEntry(key);
        EventEntry restored = EventExporter.stageUndo(key);
        if (restored != null) {
            loadDraft(restored);
            PendingChangeManager.queue(PendingChange.of(PendingDomain.EVENTS, key, PendingChange.Action.EDIT, restored.requiresRestart,
                    EventExporter::commitEvents,
                    () -> EventExporter.discardStagedUndo(key, restored, beforeUndo))
                    .withDiff(() -> EventExporter.previewEventJS(beforeUndo == null ? restored : beforeUndo),
                            () -> EventExporter.previewEventJS(restored)));
            flash(restored.requiresRestart
                    ? Lang.get("gui.kve.pending.queued_hint_restart")
                    : Lang.get("gui.kve.pending.queued_hint_reload"));
        }
    }

    private void onApplyPending() {
        if (!ClientPermissionState.canApply()) {
            flash(Lang.get("gui.kve.perm.apply_denied"));
            return;
        }
        if (PendingChangeManager.isEmpty())
            return;
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
                        applyPendingResult(confirmed ? PendingChangeManager.applyAll()
                                : PendingChangeManager.applyDomain(PendingDomain.EVENTS));
                    },
                    Component.literal(Lang.get("gui.kve.pending.other_domains_title")),
                    Component.literal(Lang.get("gui.kve.pending.other_domains_msg", otherCount))));
            return;
        }
        applyPendingResult(PendingChangeManager.applyDomain(PendingDomain.EVENTS));
    }

    private void applyPendingResult(PendingChangeManager.ApplyResult result) {
        com.kve.client.gui.KveToast.applied(result);
        refresh();
    }

    private void onDiscardPending() {
        if (PendingChangeManager.isEmpty())
            return;
        int count = PendingChangeManager.sizeForDomain(PendingDomain.EVENTS);
        if (count == 0)
            return;
        PendingChangeManager.discardDomain(PendingDomain.EVENTS);
        flash(Lang.get("gui.kve.pending.discarded", count));
        refresh();
    }

    private void goBack() {
        if (hosted && onEmbeddedExit != null) { onEmbeddedExit.run(); return; }
        minecraft.setScreen(returnScreen != null ? returnScreen : new EventListScreen(new KveStudioShell()));
    }

    /** ESC acts as "back" here instead of closing the whole GUI. */
    @Override
    public void onClose() {
        goBack();
    }

    private void flash(String msg) {
        flashMsg = msg;
        flashAt = System.currentTimeMillis();
    }

    private int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private float parseFloat(String s, float def) {
        try {
            return Float.parseFloat(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private String fmtF(float v) {
        return (v == Math.floor(v)) ? String.valueOf((long) v) : String.valueOf(v);
    }

    private Button btn(String label, int x, int y, int w, Runnable a) {
        return KveGuiStyle.smartBtn(x, y, w, label, b -> a.run());
    }

    private Button pickBtn(String label, int cardX, int width, int y, Runnable a) {
        int w = KveGuiStyle.btnWidth(label);
        return btn(label, cardX + width - w, y, 0, a);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (hosted) {
            String wantSig = expandedCard + "|" + draftConditions.size() + "|" + draftActions.size()
                    + "|" + hostedW + "|" + hostedH + "|" + actionButtonRowLines();
            if (!wantSig.equals(lastLayoutSig)) refresh();
        }
        if (!hosted) renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, Lang.get("gui.kve.event.editor.title"));

        long nowMs = System.currentTimeMillis();
        if (nowMs - structuralProblemsAt >= 250L) {
            structuralProblemsAt = nowMs;
            cachedStructuralProblems = buildDraftEntry().validateStructural();
        }
        List<String> structuralProblems = cachedStructuralProblems;
        List<String> liveProblems = structuralProblems;
        if (!cachedRefWarnings.isEmpty()) {
            liveProblems = new ArrayList<>(structuralProblems);
            liveProblems.addAll(cachedRefWarnings);
        }
        if (saveBtn != null)
            saveBtn.active = liveProblems.isEmpty();

        drawHumanSummaryBox(g);

        if (expandedCard == CardId.COND_ACTIONS) {
            int condHeaderMaxW = cardW - 90 - 16;
            String condHeaderDrawn = KveGuiStyle.truncate(font,
                    Lang.get("gui.kve.event.conditions_header", draftConditions.size()), condHeaderMaxW);
            KveGuiStyle.drawTextSafe(g, condHeaderDrawn, cardX, condHeaderTextY, condHeaderMaxW, 0xFF88CC88);
            KveGuiStyle.helpIcon(g, cardX + font.width(condHeaderDrawn) + 3, condHeaderTextY - 1, mx, my,
                    Lang.c("gui.kve.event.cond.and_hint"));
            for (int i = 0; i < Math.min(COND_ROWS_MAX, draftConditions.size()); i++) {
                EventEntry.Condition c = draftConditions.get(i);
                int ry = condListY + i * COND_ROW_H;
                // cont. (mismo indicador [!] que EventListScreen ya usa a
                // nivel de evento, ahora a nivel de fila condición) - leído de
                // brokenRefCache (ver #refreshRefWarnings()), nunca recalculado acá.
                boolean brokenRef = brokenRefCache.conditionIndices().contains(i);
                String condLine = (brokenRef ? "[!] " : "") + conditionSummary(c);
                KveGuiStyle.drawTextSafe(g, condLine, cardX + 58, ry + 2, cardW - 62, brokenRef ? KveGuiStyle.TEXT_WARN : 0xFFAADDAA);
            }

            int actionsHeaderMaxW = cardW - 90 - 16;
            String actionsHeaderDrawn = KveGuiStyle.truncate(font,
                    Lang.get("gui.kve.event.actions_header", draftActions.size()), actionsHeaderMaxW);
            KveGuiStyle.drawTextSafe(g, actionsHeaderDrawn, cardX, actionsHeaderTextY, actionsHeaderMaxW, 0xFF88CC88);
            KveGuiStyle.helpIcon(g, cardX + font.width(actionsHeaderDrawn) + 3, actionsHeaderTextY - 1, mx, my,
                    Lang.c("gui.kve.event.action.order_hint"));
            for (int i = 0; i < ACTION_ROWS_VISIBLE; i++) {
                int idx = i + actionsScroll;
                if (idx >= draftActions.size())
                    break;
                EventEntry.Action a = draftActions.get(idx);
                int ry = actionsListY + i * ACTION_ROW_H;
                boolean brokenRef = brokenRefCache.actionIndices().contains(idx);
                String actionLine = (brokenRef ? "[!] " : "") + actionSummary(a);
                KveGuiStyle.drawTextSafe(g, actionLine, cardX + 72, ry + 2, cardW - 76, brokenRef ? KveGuiStyle.TEXT_WARN : 0xFFAADDAA);
            }
            if (draftActions.size() > ACTION_ROWS_VISIBLE) {
                KveGuiStyle.drawTextSafe(g,
                        (actionsScroll + 1) + "-" + Math.min(draftActions.size(), actionsScroll + ACTION_ROWS_VISIBLE)
                                + "/" + draftActions.size(),
                        cardX, actionsListY + ACTION_ROWS_VISIBLE * ACTION_ROW_H + 2, cardW, KveGuiStyle.th(0xFF556677));
            }
        }

        if (!flashMsg.isEmpty() && System.currentTimeMillis() - flashAt < 4500) {
            KveGuiStyle.drawTextSafe(g, flashMsg, cardX, cardsBottomY + 6, cardW, KveGuiStyle.th(0xFFE8C96A));
        }

        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
        drawFieldHelpIcons(g, mx, my);
        drawValidationBadges(g, mx, my, liveProblems);
    }

    private void drawHumanSummaryBox(GuiGraphics g) {
        int sumX = cardX;
        int sumY = T + 16;
        int sumW = cardW;

        g.fill(sumX, sumY, sumX + sumW, sumY + SUMMARY_H, KveGuiStyle.BG_FIELD);
        g.hLine(sumX, sumX + sumW - 1, sumY, KveGuiStyle.BORDER_SUBTLE);
        g.hLine(sumX, sumX + sumW - 1, sumY + SUMMARY_H - 1, KveGuiStyle.BORDER_SUBTLE);
        g.vLine(sumX, sumY, sumY + SUMMARY_H - 1, KveGuiStyle.BORDER_SUBTLE);
        g.vLine(sumX + sumW - 1, sumY, sumY + SUMMARY_H - 1, KveGuiStyle.BORDER_SUBTLE);

        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.event.summary_header"), sumX + 4, sumY + 2, sumW - 8, KveGuiStyle.th(0xFF88AACC));

        List<String> lines = KveGuiStyle.wrapTextSafe(font, buildHumanSummary(), sumW - 8);
        int shown = Math.min(lines.size(), SUMMARY_MAX_LINES);
        for (int i = 0; i < shown; i++) {
            KveGuiStyle.drawTextSafe(g, lines.get(i), sumX + 4, sumY + 13 + i * 9, sumW - 8, KveGuiStyle.TEXT_PRIMARY);
        }
        if (lines.size() > SUMMARY_MAX_LINES) {
            KveGuiStyle.drawTextSafe(g, "~", sumX + sumW - 10, sumY + 13 + (SUMMARY_MAX_LINES - 1) * 9, 8, KveGuiStyle.TEXT_PRIMARY);
        }
    }

    private enum ValidationSection {
        IDENTITY, TRIGGER, CONDITIONS, ACTIONS, LIMITS, OTHER
    }

    private ValidationSection categorize(String raw) {
        if (raw.startsWith("Event id"))
            return ValidationSection.IDENTITY;
        if (raw.startsWith("Evento sin trigger.") || raw.startsWith("Trigger "))
            return ValidationSection.TRIGGER;
        if (raw.startsWith("Condition #"))
            return ValidationSection.CONDITIONS;
        if (raw.startsWith("Evento sin acciones") || raw.startsWith("Action #"))
            return ValidationSection.ACTIONS;
        if (raw.startsWith("Limits."))
            return ValidationSection.LIMITS;
        return ValidationSection.OTHER;
    }

    private String translateValidationMessage(String raw) {
        if (raw == null || raw.isEmpty())
            return "";

        if (raw.equals("Evento sin trigger."))
            return Lang.get("gui.kve.event.validation.no_trigger");
        if (raw.startsWith("Trigger INTERVAL: intervalSeconds debe ser >="))
            return Lang.get("gui.kve.event.validation.interval_too_short");
        if (raw.startsWith("Trigger PLAYER_KILL: count debe ser >="))
            return Lang.get("gui.kve.event.validation.kill_count_too_low");
        if (raw.equals("Trigger COMMAND: commandName no puede estar vacio."))
            return Lang.get("gui.kve.event.validation.command_name_empty");
        if (raw.equals("Trigger ENTER_BIOME: biomeId no puede estar vacio."))
            return Lang.get("gui.kve.event.validation.biome_id_empty");
        if (raw.startsWith("Trigger PLAYER_HURT: minDamage no puede ser negativo"))
            return Lang.get("gui.kve.event.validation.min_damage_negative");
        if (raw.startsWith("Trigger DIMENSION_CHANGE") && raw.contains("id vacio."))
            return Lang.get("gui.kve.event.validation.to_dimension_empty");
        if (raw.startsWith("Trigger DIMENSION_CHANGE") && raw.contains("no tiene formato"))
            return Lang.get("gui.kve.event.validation.id_bad_format");
        if (raw.startsWith("Trigger BLOCK_BREAK") && raw.contains("blockId no puede estar vacio."))
            return Lang.get("gui.kve.event.validation.block_id_empty");
        if (raw.startsWith("Trigger BLOCK_PLACE") && raw.contains("blockId no puede estar vacio."))
            return Lang.get("gui.kve.event.validation.block_id_empty");
        if ((raw.startsWith("Trigger BLOCK_BREAK") || raw.startsWith("Trigger BLOCK_PLACE"))
                && raw.contains("count debe ser >="))
            return Lang.get("gui.kve.event.validation.count_too_low");
        if ((raw.startsWith("Trigger ITEM_PICKUP") || raw.startsWith("Trigger ITEM_CRAFT"))
                && raw.contains("itemId no puede estar vacio."))
            return Lang.get("gui.kve.event.validation.item_id_empty");
        if ((raw.startsWith("Trigger ITEM_PICKUP") || raw.startsWith("Trigger ITEM_CRAFT"))
                && raw.contains("count debe ser >="))
            return Lang.get("gui.kve.event.validation.count_too_low");
        if (raw.equals("Evento sin acciones: se requiere al menos 1."))
            return Lang.get("gui.kve.event.validation.no_actions");
        if (raw.equals("Event id: id vacio."))
            return Lang.get("gui.kve.event.validation.id_empty");
        if (raw.startsWith("Event id: '") && raw.contains("no tiene formato"))
            return Lang.get("gui.kve.event.validation.id_bad_format");
        if (raw.equals("Limits.cooldownSeconds no puede ser negativo."))
            return Lang.get("gui.kve.event.validation.cooldown_negative");
        if (raw.equals("Limits.maxGlobalStarts no puede ser negativo."))
            return Lang.get("gui.kve.event.validation.max_global_negative");
        if (raw.startsWith("Trigger ENTITY_KILL_ANY") && raw.contains("count debe ser >="))
            return Lang.get("gui.kve.event.validation.count_too_low");
        if (raw.startsWith("Trigger ENTITY_INTERACT"))
            return Lang.get("gui.kve.event.validation.entity_type_id_empty");

        Integer conditionIdx = extractIndex(raw, "Condition #");
        if (conditionIdx != null) {
            String prefix = Lang.get("gui.kve.event.validation.row_prefix", conditionIdx);
            if (raw.contains("id vacio."))
                return prefix + Lang.get("gui.kve.event.validation.id_empty");
            if (raw.contains("no tiene formato"))
                return prefix + Lang.get("gui.kve.event.validation.id_bad_format");
        }

        Integer actionIdx = extractIndex(raw, "Action #");
        if (actionIdx != null) {
            String prefix = Lang.get("gui.kve.event.validation.row_prefix", actionIdx);
            if (raw.contains("(MESSAGE): text no puede estar vacio."))
                return prefix + Lang.get("gui.kve.event.validation.message_text_empty");
            if (raw.contains("(TITLE): title no puede estar vacio."))
                return prefix + Lang.get("gui.kve.event.validation.title_empty");
            if (raw.contains("count debe estar entre 1 y 64"))
                return prefix + Lang.get("gui.kve.event.validation.give_item_count_range");
            if (raw.contains("(RUN_COMMAND): command no puede estar vacio."))
                return prefix + Lang.get("gui.kve.event.validation.run_command_empty");
            if (raw.contains("esta en la lista negra."))
                return prefix + Lang.get("gui.kve.event.validation.run_command_blacklisted");
            if (raw.contains("(PLAY_SOUND): soundId no puede estar vacio."))
                return prefix + Lang.get("gui.kve.event.validation.play_sound_empty");
            if (raw.contains("(SPAWN_ENTITY): count debe ser >="))
                return prefix + Lang.get("gui.kve.event.validation.spawn_count_too_low");
            if (raw.contains("(ACTIONBAR): text no puede estar vacio."))
                return prefix + Lang.get("gui.kve.event.validation.actionbar_text_empty");
            if (raw.contains("(TAKE_ITEM)") && raw.contains("count debe estar entre"))
                return prefix + Lang.get("gui.kve.event.validation.give_item_count_range");
            if (raw.contains("(GIVE_XP_LEVELS): levels debe ser >="))
                return prefix + Lang.get("gui.kve.event.validation.xp_levels_too_low");
            if (raw.contains("(ADD_EFFECT)") && raw.contains("durationTicks debe ser >="))
                return prefix + Lang.get("gui.kve.event.validation.effect_duration_too_low");
            if (raw.contains("(BOSSBAR_SHOW): text no puede estar vacio."))
                return prefix + Lang.get("gui.kve.event.validation.bossbar_text_empty");
            if (raw.contains("(BOSSBAR_SHOW): seconds debe ser >="))
                return prefix + Lang.get("gui.kve.event.validation.bossbar_seconds_too_low");
            if (raw.contains("(PARTICLES): count debe ser >="))
                return prefix + Lang.get("gui.kve.event.validation.particle_count_too_low");
            if (raw.contains("(EXPLOSION_HARMLESS): power debe ser >"))
                return prefix + Lang.get("gui.kve.event.validation.explosion_power_too_low");
            if (raw.contains("(RUN_COMMAND_AS_PLAYER): command no puede estar vacio."))
                return prefix + Lang.get("gui.kve.event.validation.run_command_empty");
            if (raw.contains("(GIVE_MONEY)") || raw.contains("(TAKE_MONEY)"))
                return prefix + Lang.get("gui.kve.event.validation.money_amount_too_low");
            if (raw.contains("(FIRE_CUSTOM_EVENT): eventId no puede estar vacio."))
                return prefix + Lang.get("gui.kve.event.validation.custom_event_id_empty");
        }

        return raw;
    }

    private Integer extractIndex(String raw, String prefix) {
        int i = raw.indexOf(prefix);
        if (i < 0)
            return null;
        int start = i + prefix.length();
        int end = start;
        while (end < raw.length() && Character.isDigit(raw.charAt(end)))
            end++;
        if (end == start)
            return null;
        try {
            return Integer.parseInt(raw.substring(start, end));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String conditionSummary(EventEntry.Condition c) {
        return switch (c.type) {
            case DIMENSION -> Lang.get("gui.kve.event.cond.summary.dimension", c.dimensionId);
            case PERMISSION -> Lang.get("gui.kve.event.cond.summary.permission", c.permissionNode);
            case GAMEMODE -> Lang.get("gui.kve.event.cond.summary.gamemode", gamemodeLabel(c.gamemode));
            case TIME_OF_DAY -> Lang.get("gui.kve.event.cond.summary.time_of_day", timeOfDayLabel(c.timeOfDay));
            case Y_LEVEL_MIN -> Lang.get("gui.kve.event.cond.summary.y_level_min", c.yMin);
            case Y_LEVEL_MAX -> Lang.get("gui.kve.event.cond.summary.y_level_max", c.yMax);
            case BIOME -> Lang.get("gui.kve.event.cond.summary.biome", c.biomeId);
            case XP_LEVEL_MIN -> Lang.get("gui.kve.event.cond.summary.xp_level_min", c.xpLevelMin);
            case HAS_ITEM -> Lang.get("gui.kve.event.cond.summary.has_item", c.hasItemMinCount, c.hasItemId);
            case RANDOM_CHANCE -> Lang.get("gui.kve.event.cond.summary.random_chance", c.randomChance);
            case HEALTH_MAX -> Lang.get("gui.kve.event.cond.summary.health_max", fmtF(c.healthMax));
            case HEALTH_MIN -> Lang.get("gui.kve.event.cond.summary.health_min", fmtF(c.healthMin));
            case FOOD_LEVEL_MIN -> Lang.get("gui.kve.event.cond.summary.food_level_min", c.foodLevelMin);
            case HAS_EFFECT -> Lang.get("gui.kve.event.cond.summary.has_effect", c.hasEffectId);
            case HAS_ITEM_EQUIPPED -> Lang.get("gui.kve.event.cond.summary.has_item_equipped", c.equippedItemId,
                    equipSlotLabel(c.equippedSlot));
            case ADVANCEMENT_DONE -> Lang.get("gui.kve.event.cond.summary.advancement_done", c.advancementId);
            case ADVANCEMENT_MISSING -> Lang.get("gui.kve.event.cond.summary.advancement_missing", c.advancementId);
            case WEATHER -> Lang.get("gui.kve.event.cond.summary.weather", weatherLabel(c.weatherState));
            case MOON_PHASE -> Lang.get("gui.kve.event.cond.summary.moon_phase", c.moonPhase);
            case DAY_OF_WEEK -> Lang.get("gui.kve.event.cond.summary.day_of_week", dayOfWeekLabel(c.dayOfWeek));
            case DATE_BETWEEN -> Lang.get("gui.kve.event.cond.summary.date_between", c.dateStartMd, c.dateEndMd);
            case HOUR_BETWEEN -> Lang.get("gui.kve.event.cond.summary.hour_between", c.hourStart, c.hourEnd);
            case HAS_TAG_STAGE -> Lang.get("gui.kve.event.cond.summary.has_tag_stage", c.stageId);
            case NOT_HAS_FLAG -> Lang.get("gui.kve.event.cond.summary.not_has_flag", c.flagId);
            case PLAYTIME_MIN_SECONDS ->
                Lang.get("gui.kve.event.cond.summary.playtime_min_seconds", c.playtimeMinSeconds);
            case ENTITY_HAS_ITEM_EQUIPPED -> Lang.get("gui.kve.event.cond.summary.entity_has_item_equipped",
                    c.entityEquippedItemId, equipSlotLabel(c.equippedSlot));
        };
    }

    private String actionSummary(EventEntry.Action a) {
        return switch (a.type) {
            case MESSAGE -> Lang.get("gui.kve.event.action.summary.message", a.text);
            case TITLE -> Lang.get("gui.kve.event.action.summary.title", a.title);
            case GIVE_ITEM -> Lang.get("gui.kve.event.action.summary.give_item", a.itemCount, a.itemId);
            case RUN_COMMAND -> Lang.get("gui.kve.event.action.summary.run_command", a.command);
            case PLAY_SOUND -> Lang.get("gui.kve.event.action.summary.play_sound", a.soundId);
            case SPAWN_ENTITY -> Lang.get("gui.kve.event.action.summary.spawn_entity", a.spawnCount, a.spawnEntityId);
            case ACTIONBAR -> Lang.get("gui.kve.event.action.summary.actionbar", a.text);
            case TAKE_ITEM -> Lang.get("gui.kve.event.action.summary.take_item", a.itemCount, a.itemId);
            case GIVE_XP_LEVELS -> Lang.get("gui.kve.event.action.summary.give_xp_levels", a.xpLevels);
            case ADD_EFFECT -> Lang.get("gui.kve.event.action.summary.add_effect", a.effectId, a.effectDurationTicks,
                    a.effectAmplifier);
            case TELEPORT -> Lang.get("gui.kve.event.action.summary.teleport", a.teleportX, a.teleportY, a.teleportZ,
                    a.teleportDim == null || a.teleportDim.isEmpty() ? "" : " (" + a.teleportDim + ")");
            case TELEPORT_SPAWN -> Lang.get("gui.kve.event.action.summary.teleport_spawn");
            case CLEAR_ITEM -> Lang.get("gui.kve.event.action.summary.clear_item", a.itemId);
            case GIVE_XP_POINTS -> Lang.get("gui.kve.event.action.summary.give_xp_points", a.xpPoints);
            case SET_XP_LEVEL -> Lang.get("gui.kve.event.action.summary.set_xp_level", a.setXpLevel);
            case ADD_STAGE -> Lang.get("gui.kve.event.action.summary.add_stage", a.stageId);
            case REMOVE_STAGE -> Lang.get("gui.kve.event.action.summary.remove_stage", a.stageId);
            case HEAL -> a.healFull
                    ? Lang.get("gui.kve.event.action.summary.heal_full")
                    : Lang.get("gui.kve.event.action.summary.heal_amount", fmtF(a.healAmount));
            case SET_FOOD -> Lang.get("gui.kve.event.action.summary.set_food", a.setFoodLevel);
            case SET_HEALTH -> Lang.get("gui.kve.event.action.summary.set_health", fmtF(a.setHealthAmount));
            case CLEAR_EFFECTS -> Lang.get("gui.kve.event.action.summary.clear_effects");
            case BOSSBAR_SHOW -> Lang.get("gui.kve.event.action.summary.bossbar_show", a.bossbarText);
            case BOSSBAR_CLEAR -> Lang.get("gui.kve.event.action.summary.bossbar_clear");
            case PARTICLES -> Lang.get("gui.kve.event.action.summary.particles", a.particleId, a.particleCount);
            case SET_TIME -> Lang.get("gui.kve.event.action.summary.set_time", setTimeModeLabel(a.setTimeMode));
            case SET_WEATHER -> Lang.get("gui.kve.event.action.summary.set_weather", weatherLabel(a.setWeatherMode));
            case EXPLOSION_HARMLESS ->
                Lang.get("gui.kve.event.action.summary.explosion_harmless", fmtF(a.explosionPower));
            case RUN_COMMAND_AS_PLAYER ->
                Lang.get("gui.kve.event.action.summary.run_command_as_player", a.commandAsPlayer);
            case GIVE_MONEY -> Lang.get("gui.kve.event.action.summary.give_money", a.moneyAmount);
            case TAKE_MONEY -> Lang.get("gui.kve.event.action.summary.take_money", a.moneyAmount);
            case FIRE_CUSTOM_EVENT -> Lang.get("gui.kve.event.action.summary.fire_custom_event", a.customEventId);
            case CLEAR_TITLE -> Lang.get("gui.kve.event.action.summary.clear_title");
            case GRANT_ADVANCEMENT ->
                Lang.get("gui.kve.event.action.summary.grant_advancement", a.grantAdvancementId);
            case REMOVE_EFFECT -> Lang.get("gui.kve.event.action.summary.remove_effect", a.removeEffectId);
            case SET_SPAWN -> Lang.get("gui.kve.event.action.summary.set_spawn", a.setSpawnX, a.setSpawnY,
                    a.setSpawnZ, a.setSpawnDim == null || a.setSpawnDim.isEmpty() ? "" : " (" + a.setSpawnDim + ")");
            case LIGHTNING -> Lang.get("gui.kve.event.action.summary.lightning");
            case APPLY_LOOT_TABLE -> Lang.get("gui.kve.event.action.summary.apply_loot_table", a.lootTableId);
        };
    }

    private String equipSlotLabel(EventEntry.EquipSlot s) {
        return switch (s) {
            case MAINHAND -> Lang.get("gui.kve.event.equipslot.mainhand");
            case OFFHAND -> Lang.get("gui.kve.event.equipslot.offhand");
            case HEAD -> Lang.get("gui.kve.event.equipslot.head");
            case CHEST -> Lang.get("gui.kve.event.equipslot.chest");
            case LEGS -> Lang.get("gui.kve.event.equipslot.legs");
            case FEET -> Lang.get("gui.kve.event.equipslot.feet");
        };
    }

    private String gamemodeLabel(EventEntry.GameMode m) {
        return switch (m) {
            case SURVIVAL -> Lang.get("gui.kve.event.gamemode.survival");
            case CREATIVE -> Lang.get("gui.kve.event.gamemode.creative");
            case ADVENTURE -> Lang.get("gui.kve.event.gamemode.adventure");
            case SPECTATOR -> Lang.get("gui.kve.event.gamemode.spectator");
        };
    }

    private String timeOfDayLabel(EventEntry.TimeOfDay t) {
        return switch (t) {
            case DAY -> Lang.get("gui.kve.event.timeofday.day");
            case NIGHT -> Lang.get("gui.kve.event.timeofday.night");
        };
    }

    private String weatherLabel(EventEntry.WeatherState w) {
        return switch (w) {
            case CLEAR -> Lang.get("gui.kve.event.weather.clear");
            case RAIN -> Lang.get("gui.kve.event.weather.rain");
            case THUNDER -> Lang.get("gui.kve.event.weather.thunder");
        };
    }

    private String dayOfWeekLabel(EventEntry.WeekDay d) {
        return switch (d) {
            case MON -> Lang.get("gui.kve.event.weekday.mon");
            case TUE -> Lang.get("gui.kve.event.weekday.tue");
            case WED -> Lang.get("gui.kve.event.weekday.wed");
            case THU -> Lang.get("gui.kve.event.weekday.thu");
            case FRI -> Lang.get("gui.kve.event.weekday.fri");
            case SAT -> Lang.get("gui.kve.event.weekday.sat");
            case SUN -> Lang.get("gui.kve.event.weekday.sun");
        };
    }

    private String setTimeModeLabel(EventEntry.SetTimeMode m) {
        return switch (m) {
            case VALUE -> Lang.get("gui.kve.event.settimemode.value");
            case DAY -> Lang.get("gui.kve.event.settimemode.day");
            case NIGHT -> Lang.get("gui.kve.event.settimemode.night");
        };
    }

    private void drawValidationBadges(GuiGraphics g, int mx, int my, List<String> problems) {
        List<Component> identity = new ArrayList<>();
        List<Component> trigger = new ArrayList<>();
        List<Component> condActions = new ArrayList<>();
        List<Component> limits = new ArrayList<>();

        for (String raw : problems) {
            Component translated = Component.literal(translateValidationMessage(raw));
            switch (categorize(raw)) {
                case IDENTITY -> identity.add(translated);
                case TRIGGER -> trigger.add(translated);
                case CONDITIONS, ACTIONS -> condActions.add(translated);
                case LIMITS -> limits.add(translated);
                case OTHER -> {
                    /*
                     * no dedicated card - still blocks Save via saveBtn.active, just no badge home
                     */ }
            }
        }

        KveGuiStyle.errorBadge(g, cardX - 10, identityHeaderY, mx, my, identity);
        KveGuiStyle.errorBadge(g, cardX - 10, triggerHeaderY, mx, my, trigger);
        KveGuiStyle.errorBadge(g, cardX - 10, condActionsHeaderY, mx, my, condActions);
        KveGuiStyle.errorBadge(g, cardX - 10, limitsHeaderY, mx, my, limits);
    }

    private void drawFieldHelpIcons(GuiGraphics g, int mx, int my) {
        if (expandedCard == null)
            return;
        switch (expandedCard) {
            case IDENTITY -> {
                KveGuiStyle.helpIcon(g, cardX + (cardW - 60) - 9, idFieldY, mx, my, Lang.c("gui.kve.event.help.id"));
                KveGuiStyle.helpIcon(g, cardX + cardW - 9, nameFieldY, mx, my,
                        Lang.c("gui.kve.event.help.display_name"));
                KveGuiStyle.helpIcon(g, cardX + cardW - 9, categoryFieldY, mx, my,
                        Lang.c("gui.kve.event.help.category"));
            }
            case TRIGGER -> {
                switch (draftTriggerType) {
                    case INTERVAL -> KveGuiStyle.helpIcon(g, cardX + cardW - 9, triggerFieldY, mx, my,
                            Lang.c("gui.kve.event.trigger.field_help.interval"));
                    case PLAYER_KILL -> {
                        KveGuiStyle.helpIcon(g, cardX + (cardW - 40) - 9, triggerFieldY, mx, my,
                                Lang.c("gui.kve.event.trigger.field_help.entity_id"));
                        KveGuiStyle.helpIcon(g, cardX + cardW - 9, triggerFieldY + FIELD_ROW_H, mx, my,
                                Lang.c("gui.kve.event.trigger.field_help.kill_count"));
                    }
                    case COMMAND -> KveGuiStyle.helpIcon(g, cardX + cardW - 9, triggerFieldY, mx, my,
                            Lang.c("gui.kve.event.trigger.field_help.command"));
                    case ENTER_BIOME -> KveGuiStyle.helpIcon(g, cardX + cardW - 9, triggerFieldY, mx, my,
                            Lang.c("gui.kve.event.trigger.field_help.enter_biome"));
                    case FIRST_JOIN, PLAYER_LOGIN, PLAYER_RESPAWN -> {
                        }
                    case PLAYER_DEATH -> KveGuiStyle.helpIcon(g, cardX + cardW - 9, triggerFieldY, mx, my,
                            Lang.c("gui.kve.event.trigger.field_help.death_message_contains"));
                    case PLAYER_HURT -> KveGuiStyle.helpIcon(g, cardX + cardW - 9, triggerFieldY, mx, my,
                            Lang.c("gui.kve.event.trigger.field_help.min_damage"));
                    case DIMENSION_CHANGE -> {
                        KveGuiStyle.helpIcon(g, cardX + cardW - 9, triggerFieldY, mx, my,
                                Lang.c("gui.kve.event.trigger.field_help.to_dimension"));
                        KveGuiStyle.helpIcon(g, cardX + cardW - 9, triggerFieldY + FIELD_ROW_H, mx, my,
                                Lang.c("gui.kve.event.trigger.field_help.from_dimension"));
                    }
                    case BLOCK_BREAK, BLOCK_PLACE -> {
                        KveGuiStyle.helpIcon(g, cardX + cardW - 9, triggerFieldY, mx, my,
                                Lang.c("gui.kve.event.trigger.field_help.block_id"));
                        KveGuiStyle.helpIcon(g, cardX + cardW - 9, triggerFieldY + FIELD_ROW_H, mx, my,
                                Lang.c("gui.kve.event.trigger.field_help.count"));
                    }
                    case ITEM_PICKUP, ITEM_CRAFT -> {
                        KveGuiStyle.helpIcon(g, cardX + (cardW - 40) - 9, triggerFieldY, mx, my,
                                Lang.c("gui.kve.event.trigger.field_help.item_id"));
                        KveGuiStyle.helpIcon(g, cardX + cardW - 9, triggerFieldY + FIELD_ROW_H, mx, my,
                                Lang.c("gui.kve.event.trigger.field_help.count"));
                    }
                    case XP_LEVEL_REACH -> KveGuiStyle.helpIcon(g, cardX + cardW - 9, triggerFieldY, mx, my,
                            Lang.c("gui.kve.event.trigger.field_help.xp_level"));
                    case XP_CHANGED -> KveGuiStyle.helpIcon(g, cardX + cardW - 9, triggerFieldY, mx, my,
                            Lang.c("gui.kve.event.trigger.field_help.xp_min_delta"));
                    case ADVANCEMENT_EARNED -> KveGuiStyle.helpIcon(g, cardX + cardW - 9, triggerFieldY, mx, my,
                            Lang.c("gui.kve.event.trigger.field_help.advancement_id"));
                    case EFFECT_GAINED -> KveGuiStyle.helpIcon(g, cardX + cardW - 9, triggerFieldY, mx, my,
                            Lang.c("gui.kve.event.trigger.field_help.trigger_effect_id"));
                    case ITEM_USE, ITEM_CONSUME, ITEM_SMELT -> KveGuiStyle.helpIcon(g, cardX + (cardW - 40) - 9,
                            triggerFieldY, mx, my, Lang.c("gui.kve.event.trigger.field_help.item_id"));
                    case FISHING_CATCH, VILLAGER_TRADE -> KveGuiStyle.helpIcon(g, cardX + (cardW - 40) - 9,
                            triggerFieldY, mx, my, Lang.c("gui.kve.event.trigger.field_help.item_id_optional"));
                    case ENTITY_KILL_ANY -> KveGuiStyle.helpIcon(g, cardX + cardW - 9, triggerFieldY, mx, my,
                            Lang.c("gui.kve.event.trigger.field_help.kill_count"));
                    case ENTITY_INTERACT -> KveGuiStyle.helpIcon(g, cardX + (cardW - 40) - 9, triggerFieldY, mx, my,
                            Lang.c("gui.kve.event.trigger.field_help.entity_type_id"));
                    case TAMING, BREEDING, MOUNT -> KveGuiStyle.helpIcon(g, cardX + (cardW - 40) - 9, triggerFieldY, mx,
                            my, Lang.c("gui.kve.event.trigger.field_help.entity_type_id_optional"));
                    case PLAYER_DISCONNECT, SERVER_STARTUP -> {
                        }
                    case EMPTY_BUCKET, FILL_BUCKET -> KveGuiStyle.helpIcon(g, cardX + cardW - 9, triggerFieldY, mx, my,
                            Lang.c("gui.kve.event.trigger.field_help.fluid_id_optional"));
                }
            }
            case COND_ACTIONS -> {
                }
            case LIMITS -> {
                KveGuiStyle.helpIcon(g, cardX + cardW - 9, oncePerPlayerRowY, mx, my,
                        Lang.c("gui.kve.event.help.once_per_player"));
                KveGuiStyle.helpIcon(g, cardX + (cardW / 2 - 2) - 9, limitsFieldY, mx, my,
                        Lang.c("gui.kve.event.help.cooldown"));
                KveGuiStyle.helpIcon(g, cardX + cardW - 9, limitsFieldY, mx, my,
                        Lang.c("gui.kve.event.help.max_global_starts"));
            }
        }
    }

    private String buildHumanSummary() {
        String base = Lang.get("gui.kve.event.summary.when", triggerSummaryClause());

        if (!draftConditions.isEmpty()) {
            StringBuilder condJoined = new StringBuilder();
            for (int i = 0; i < draftConditions.size(); i++) {
                if (i > 0)
                    condJoined.append(Lang.get("gui.kve.event.summary.cond_and"));
                condJoined.append(conditionSummary(draftConditions.get(i)));
            }
            base += Lang.get("gui.kve.event.summary.if_clause", condJoined.toString());
        }

        if (draftActions.isEmpty()) {
            return base + Lang.get("gui.kve.event.summary.then_clause",
                    Lang.get("gui.kve.event.summary_incomplete_no_actions"));
        }

        StringBuilder actionsJoined = new StringBuilder();
        for (int i = 0; i < draftActions.size(); i++) {
            if (i > 0)
                actionsJoined.append("; ");
            actionsJoined.append(actionSummary(draftActions.get(i)));
        }
        return base + Lang.get("gui.kve.event.summary.then_clause", actionsJoined.toString());
    }

    private String triggerSummaryClause() {
        return switch (draftTriggerType) {
            case FIRST_JOIN -> Lang.get("gui.kve.event.summary.trigger.first_join");
            case PLAYER_LOGIN -> Lang.get("gui.kve.event.summary.trigger.player_login");
            case INTERVAL -> Lang.get("gui.kve.event.summary.trigger.interval", draftIntervalSeconds);
            case PLAYER_KILL -> Lang.get("gui.kve.event.summary.trigger.player_kill", draftKillCount,
                    draftEntityId.isEmpty() ? "?" : draftEntityId);
            case COMMAND -> Lang.get("gui.kve.event.summary.trigger.command",
                    draftCommandName.isEmpty() ? "?" : draftCommandName);
            case ENTER_BIOME -> Lang.get("gui.kve.event.summary.trigger.enter_biome",
                    draftBiomeId.isEmpty() ? "?" : draftBiomeId);
            case PLAYER_DEATH -> Lang.get("gui.kve.event.summary.trigger.player_death");
            case PLAYER_RESPAWN -> Lang.get("gui.kve.event.summary.trigger.player_respawn");
            case PLAYER_HURT -> Lang.get("gui.kve.event.summary.trigger.player_hurt", fmtF(draftMinDamage));
            case DIMENSION_CHANGE -> Lang.get("gui.kve.event.summary.trigger.dimension_change",
                    draftToDimension.isEmpty() ? "?" : draftToDimension);
            case BLOCK_BREAK -> Lang.get("gui.kve.event.summary.trigger.block_break", draftKillCount,
                    draftBlockId.isEmpty() ? "?" : draftBlockId);
            case BLOCK_PLACE -> Lang.get("gui.kve.event.summary.trigger.block_place", draftKillCount,
                    draftBlockId.isEmpty() ? "?" : draftBlockId);
            case ITEM_PICKUP -> Lang.get("gui.kve.event.summary.trigger.item_pickup", draftKillCount,
                    draftItemId.isEmpty() ? "?" : draftItemId);
            case ITEM_CRAFT -> Lang.get("gui.kve.event.summary.trigger.item_craft", draftKillCount,
                    draftItemId.isEmpty() ? "?" : draftItemId);
            case XP_LEVEL_REACH -> Lang.get("gui.kve.event.summary.trigger.xp_level_reach", draftXpLevel);
            case XP_CHANGED -> Lang.get("gui.kve.event.summary.trigger.xp_changed", draftXpMinDelta);
            case ADVANCEMENT_EARNED -> Lang.get("gui.kve.event.summary.trigger.advancement_earned",
                    draftAdvancementId.isEmpty() ? "?" : draftAdvancementId);
            case EFFECT_GAINED -> Lang.get("gui.kve.event.summary.trigger.effect_gained",
                    draftTriggerEffectId.isEmpty() ? "?" : draftTriggerEffectId);
            case ITEM_USE -> Lang.get("gui.kve.event.summary.trigger.item_use",
                    draftItemId.isEmpty() ? "?" : draftItemId);
            case ITEM_CONSUME -> Lang.get("gui.kve.event.summary.trigger.item_consume",
                    draftItemId.isEmpty() ? "?" : draftItemId);
            case ITEM_SMELT -> Lang.get("gui.kve.event.summary.trigger.item_smelt",
                    draftItemId.isEmpty() ? "?" : draftItemId);
            case FISHING_CATCH -> Lang.get("gui.kve.event.summary.trigger.fishing_catch",
                    draftItemId.isEmpty() ? Lang.get("gui.kve.event.summary.any_item") : draftItemId);
            case VILLAGER_TRADE -> Lang.get("gui.kve.event.summary.trigger.villager_trade",
                    draftItemId.isEmpty() ? Lang.get("gui.kve.event.summary.any_item") : draftItemId);
            case ENTITY_KILL_ANY -> Lang.get("gui.kve.event.summary.trigger.entity_kill_any", draftKillCount);
            case ENTITY_INTERACT -> Lang.get("gui.kve.event.summary.trigger.entity_interact",
                    draftEntityTypeId.isEmpty() ? "?" : draftEntityTypeId);
            case TAMING -> Lang.get("gui.kve.event.summary.trigger.taming",
                    draftEntityTypeId.isEmpty() ? Lang.get("gui.kve.event.summary.any_entity") : draftEntityTypeId);
            case BREEDING -> Lang.get("gui.kve.event.summary.trigger.breeding",
                    draftEntityTypeId.isEmpty() ? Lang.get("gui.kve.event.summary.any_entity") : draftEntityTypeId);
            case MOUNT -> Lang.get("gui.kve.event.summary.trigger.mount",
                    draftEntityTypeId.isEmpty() ? Lang.get("gui.kve.event.summary.any_entity") : draftEntityTypeId);
            case PLAYER_DISCONNECT -> Lang.get("gui.kve.event.summary.trigger.player_disconnect");
            case SERVER_STARTUP -> Lang.get("gui.kve.event.summary.trigger.server_startup");
            case EMPTY_BUCKET -> Lang.get("gui.kve.event.summary.trigger.empty_bucket",
                    draftFluidId.isEmpty() ? Lang.get("gui.kve.event.summary.any_fluid") : draftFluidId);
            case FILL_BUCKET -> Lang.get("gui.kve.event.summary.trigger.fill_bucket",
                    draftFluidId.isEmpty() ? Lang.get("gui.kve.event.summary.any_fluid") : draftFluidId);
            case ENTITY_SPAWN -> Lang.get("gui.kve.event.summary.trigger.entity_spawn",
                    draftEntityTypeId.isEmpty() ? Lang.get("gui.kve.event.summary.any_entity") : draftEntityTypeId);
            case ENTITY_DEATH -> Lang.get("gui.kve.event.summary.trigger.entity_death",
                    draftEntityTypeId.isEmpty() ? Lang.get("gui.kve.event.summary.any_entity") : draftEntityTypeId);
            case ENTITY_ATTACKS -> Lang.get("gui.kve.event.summary.trigger.entity_attacks",
                    draftEntityTypeId.isEmpty() ? Lang.get("gui.kve.event.summary.any_entity") : draftEntityTypeId);
        };
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        if (expandedCard == CardId.COND_ACTIONS
                && mx >= cardX && mx < cardX + cardW
                && my >= actionsListY && my < actionsListY + ACTION_ROWS_VISIBLE * ACTION_ROW_H) {
            int max = Math.max(0, draftActions.size() - ACTION_ROWS_VISIBLE);
            actionsScroll = Math.max(0, Math.min(max, actionsScroll - (int) Math.signum(delta)));
            refresh();
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if ((mods & GLFW.GLFW_MOD_CONTROL) != 0 && key == GLFW.GLFW_KEY_D) { duplicateEvent(); return true; }
        if (getFocused() != null && getFocused().keyPressed(key, scan, mods))
            return true;
        if (EditorKeys.isFocusTab(key, mods)) { super.keyPressed(key, scan, mods); return true; }
        return EditorKeys.swallow(mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** El menu "Mas" abierto recibe el clic antes que lo que tenga debajo. */
    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (com.kve.client.gui.KveMenuButton.consumeClick(mx, my, btn)) return true;
        return super.mouseClicked(mx, my, btn);
    }
}