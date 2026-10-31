package com.kve.client.gui.tag;

import com.kve.client.gui.recipe.CraftEditorScreen;
import com.kve.client.gui.DiffPreviewScreen;
import com.kve.client.gui.EditorKeys;
import com.kve.client.gui.picker.ItemPickerScreen;
import com.kve.client.gui.KveConfirmScreen;
import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KvePlateButton;
import com.kve.client.gui.KveScaledEditBox;
import com.kve.client.gui.KveTextInputScreen;
import com.kve.client.gui.MiniItemIcon;
import com.kve.client.gui.mob.MobPickerScreen;
import com.kve.client.gui.recipe.RecipePreviewScreen;
import com.kve.client.gui.ResizeHandle;
import com.kve.client.gui.ScrollBar;
import com.kve.client.gui.SearchFilter;

import org.lwjgl.glfw.GLFW;
import com.kve.common.Lang;
import com.kve.client.gui.TagPreview;
import com.kve.client.permission.ClientPermissionState;
import com.kve.common.kubejs.ExternalScriptScanner;
import com.kve.common.kubejs.TagExporter;
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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class TagEditorScreen extends Screen implements KveStudioTab {

    private static final int GUI_H_DEFAULT  = 276;
    private int GUI_H = GUI_H_DEFAULT;
    private static final int GAP    = 17;
    private static final int TAG_ITEM_ROW_H = 13;
    private static final int TAG_ITEM_ICON  = 9;

    private static final int LIST_W_DEFAULT = 140;
    private static final int LIST_W_MIN     = 100;
    private static final int LIST_W_MAX     = 220;
    private static final int RIGHT_PANEL_W  = 430 - LIST_W_DEFAULT;
    private static final int PANEL_W_MIN     = 150;
    private static final int PANEL_W_DEFAULT = 180;
    private static final int SAVED_ROW_H = 26;

    private int L, T;
    private int GUI_W, LIST_W;
    private ResizeHandle listResize;

    private boolean hosted = false;
    private int hostedW = 0;
    private int hostedH = 0;

    private final List<TagExporter.TagEntry> savedList = new ArrayList<>();
    private ScrollBar savedScrollBar;
    private EditBox searchBox;
    private String searchFilter = "";

    private String currentKey = "";
    private TagExporter.RegistryType draftType = TagExporter.RegistryType.ITEM;
    private String draftTagId   = "";
    private String draftCategory = "";
    private boolean draftEnabled = true;
    private boolean draftReplace = false;
    private final List<String> draftAdded   = new ArrayList<>();
    private final List<String> draftRemoved = new ArrayList<>();
    private int addedScroll = 0, removedScroll = 0;

    private boolean objectMode = false;
    private TagExporter.RegistryType objType = TagExporter.RegistryType.ITEM;
    private String objId = "";
    private final java.util.LinkedHashMap<String, Boolean> objChanges = new java.util.LinkedHashMap<>();
    private int objTagsScroll = 0, objChangesScroll = 0;
    private List<String> objTagsCache = null;
    private EditBox fObjId;

    private EditBox panelSearch;
    private String panelFilter = "";
    private ScrollBar panelScrollBar;
    private boolean panelShown = true;
    private final java.util.Set<String> panelSelected = new java.util.LinkedHashSet<>();
    private Button panelRemoveBtn;
    private int actionTopY;
    private List<String> membersCache = null;
    private String membersCacheKey = "";

    private EditBox fTagId, fCategory;
    private int rowTagIdY, rowListsHeaderY, rowListsTop;

    private String flashMsg = "";
    private long   flashAt  = 0;

    private Screen returnScreen = null;

    private String pendingPreloadKey = null;

    public TagEditorScreen() {
        super(Lang.c("gui.kve.tag.title"));
    }

    public TagEditorScreen(Screen returnScreen, String preloadKey) {
        this();
        this.returnScreen = returnScreen;
        this.pendingPreloadKey = preloadKey;
    }

    /** Fluent setter so callers can send "Volver" back to a specific screen (defaults to KveStudioShell). */
    public TagEditorScreen withReturnScreen(Screen s) {
        this.returnScreen = s;
        return this;
    }

    @Override
    protected void init() {
        // "retorno al shell" (regla 5.1, ver Javadoc de
        // KveStudioShell#reclaimIfHosted()): si esto se esta reabriendo
        // porque un picker/dialogo interno hizo minecraft.setScreen(this)
        // para "volver" y esta tab sigue siendo la tab activa de un
        // KveStudioShell vivo, reabrir el shell en vez de reconstruirse
        // standalone/centrada. El helper distingue esto de los refrescos
        // internos de mas abajo (cycleType()/toggleEnabled()/drag del
        // ResizeHandle, que tambien llaman init() sin argumentos estando
        // ya hosteada) chequeando Minecraft#screen - ver su Javadoc.
        if (KveStudioShell.reclaimIfHosted(this, returnScreen)) return;
        hosted = false;
        if (listResize == null) {
            listResize = new ResizeHandle("tag_editor.list", LIST_W_MIN, LIST_W_MAX, LIST_W_DEFAULT);
        }
        LIST_W = listResize.width();
        LIST_W = fitListW(LIST_W, width - 8);
        GUI_W = layoutWidth(LIST_W, width - 8);
        GUI_H = Math.min(GUI_H_DEFAULT, height - 8);
        L = (width  - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        buildWidgets();
    }

    @Override
    public void init(int x0, int y0, int w, int h) {
        // KveStudioShell nunca pasa esta tab por Minecraft#setScreen()
        // mientras esta hosteada (mismo hueco #1 documentado arriba) - por
        // lo tanto Screen#init(Minecraft,int,int), el unico lugar donde
        // vanilla llena minecraft/font, nunca corre para esta instancia en
        // su primera apertura hosteada. Sin este bootstrap buildWidgets()
        if (this.minecraft == null) {
            this.minecraft = net.minecraft.client.Minecraft.getInstance();
            this.font = this.minecraft.font;
        }
        hosted = true;
        hostedW = w;
        hostedH = h;
        if (listResize == null) {
            listResize = new ResizeHandle("tag_editor.list", LIST_W_MIN, LIST_W_MAX, LIST_W_DEFAULT);
        }
        LIST_W = listResize.width();
        // Mismo criterio que Loot: el shell puede ofrecer mas ancho que el
        // minimo (GUI_W crece), nunca menos (evita solapar los campos fijos
        // de buildWidgets(), que siguen asumiendo el ancho de columna
        // actual).
        LIST_W = fitListW(LIST_W, w);
        GUI_W = layoutWidth(LIST_W, w);
        GUI_H = h;
        L = x0;
        T = y0;
        buildWidgets();
    }

    private void refresh() {
        LIST_W = fitListW(listResize.width(), hosted ? hostedW : width - 8);
        GUI_W = layoutWidth(LIST_W, hosted ? hostedW : width - 8);
        GUI_H = hosted ? hostedH : Math.min(GUI_H_DEFAULT, height - 8);
        buildWidgets();
    }

    private void buildWidgets() {
        listResize.reposition(L, T + 15, T + GUI_H);
        clearWidgets();
        actionTopY = T + GUI_H - 20;
        setFocused(null);
        CraftEditorState.loadIfNeeded();
        refreshSaved();

        if (pendingPreloadKey != null) {
            TagExporter.TagEntry preload = TagExporter.getEntry(pendingPreloadKey);
            pendingPreloadKey = null;
            if (preload != null) {
                currentKey = preload.key();
                draftType = preload.regType;
                draftTagId = preload.tagId;
                draftCategory = preload.category;
                draftEnabled = preload.enabled;
                draftReplace = preload.replace;
                draftAdded.clear(); draftAdded.addAll(preload.added);
                draftRemoved.clear(); draftRemoved.addAll(preload.removed);
                if (healAddedRemovedConflict()) flash(Lang.get("gui.kve.tag.conflict_resolved_hint"));
                addedScroll = 0; removedScroll = 0;
            }
        }

        searchBox = new KveScaledEditBox(font, L + 2, T + 16, LIST_W - 4, 11, Lang.c("gui.kve.tag.search_hint"));
        searchBox.setMaxLength(64);
        searchBox.setValue(searchFilter);
        searchBox.setHint(Lang.c("gui.kve.tag.search_hint"));
        searchBox.setResponder(s -> { searchFilter = s; refreshSaved(); });
        addRenderableWidget(searchBox);

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

        int savedKeep = savedScrollBar == null ? 0 : savedScrollBar.getScroll();
        savedScrollBar = new ScrollBar(L + LIST_W - 8, T + 30, savedRows() * SAVED_ROW_H, 0, savedRows());
        updateSavedScroll();
        savedScrollBar.setScroll(savedKeep);

        int ax = L + LIST_W + 12;
        int viewX = ax;
        if (!objectMode) {
            String newLbl = Lang.get("gui.kve.tag.new");
            addRenderableWidget(btn(newLbl, ax, T + 2, 0, this::newTag));
            viewX = ax + KveGuiStyle.btnWidth(newLbl) + KveGuiStyle.BTN_GAP;
        }
        Button viewBtn = addRenderableWidget(btn(Lang.get(objectMode ? "gui.kve.tag.view_object" : "gui.kve.tag.view_tag"),
                viewX, T + 2, 0, this::toggleObjectMode));
        viewBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Lang.c("gui.kve.tag.view_tip")));
        if (objectMode) { buildObjectWidgets(ax); buildPanel(); return; }
        KveGuiStyle.Layout ly = new KveGuiStyle.Layout(ax, T + 16, GAP);
        int ay = ly.row();

        String typeLbl = Lang.get("gui.kve.tag.type_btn", typeLabel(draftType));
        String modeLbl = draftReplace ? Lang.get("gui.kve.tag.mode_replace") : Lang.get("gui.kve.tag.mode_add_remove");
        String commonLbl = Lang.get("gui.kve.tag.common_tags");
        int typeW = KveGuiStyle.btnWidth(typeLbl);
        int modeW = KveGuiStyle.btnWidth(modeLbl);
        int commonW = KveGuiStyle.btnWidth(commonLbl);
        int modeX = ax + typeW + KveGuiStyle.BTN_GAP;
        int commonX = modeX + modeW + KveGuiStyle.BTN_GAP;
        addRenderableWidget(btn(typeLbl, ax, ay, 0, this::cycleType));
        addRenderableWidget(btn(modeLbl, modeX, ay, 0, this::toggleReplace));
        addRenderableWidget(btn(commonLbl, commonX, ay, 0, this::openCommonTags));

        ay = ly.row();
        fTagId = new KveScaledEditBox(font, ax, ay, 200, 12, Lang.c("gui.kve.tag.id_narration"));
        fTagId.setMaxLength(96);
        fTagId.setValue(draftTagId);
        fTagId.setHint(Lang.c("gui.kve.tag.id_hint"));
        fTagId.setResponder(s -> draftTagId = s);
        addRenderableWidget(fTagId);
        rowTagIdY = ay;
        ly.gap(10);

        ay = ly.row();
        fCategory = new KveScaledEditBox(font, ax, ay, 140, 11, Lang.c("gui.kve.common.category"));
        fCategory.setMaxLength(24);
        fCategory.setValue(draftCategory);
        fCategory.setHint(Lang.c("gui.kve.common.category_hint"));
        fCategory.setResponder(s -> draftCategory = s);
        addRenderableWidget(fCategory);
        addRenderableWidget(btn(draftEnabled ? Lang.get("gui.kve.status.enabled") : Lang.get("gui.kve.status.disabled"), ax + 144, ay, 0, this::toggleEnabled));
        ly.gap(12);
        ay = ly.y();

        int colW = 130;
        int addedX = ax, removedX = ax + colW + 16;
        rowListsHeaderY = ay;
        int listBtnY = ay + 11;
        rowListsTop = listBtnY + 18;
        int listTop = rowListsTop;
        actionTopY = T + GUI_H - 20 - (tagActionRows() - 1) * (KveGuiStyle.BTN_H + KveGuiStyle.BTN_GAP);

        String addLabel = draftReplace ? Lang.get("gui.kve.tag.btn_add_value") : Lang.get("gui.kve.tag.btn_add");
        addRenderableWidget(btn(addLabel, addedX, listBtnY, 0, this::addToAdded));
        int patternX = addedX + KveGuiStyle.btnWidth(addLabel) + KveGuiStyle.BTN_GAP;
        addRenderableWidget(KveGuiStyle.clampedBtn(patternX, listBtnY, removedX - 4 - patternX,
                Lang.get("gui.kve.tag.btn_pattern"), b -> addByPattern()));
        if (!draftReplace) {
            if (!panelShown) {
                addRenderableWidget(btn(Lang.get("gui.kve.tag.btn_members"), removedX, listBtnY, 0, this::openTagMembers));
            }
        }

        int visible = Math.min(editRows(), Math.max(0, draftAdded.size() - addedScroll));
        for (int i = 0; i < visible; i++) {
            int idx = i + addedScroll;
            String id = draftAdded.get(idx);
            addRenderableWidget(btn("x", addedX + colW - 12, listTop + i * TAG_ITEM_ROW_H, 12, () -> removeFromAdded(id)));
        }
        if (!draftReplace) {
            int visibleR = Math.min(editRows(), Math.max(0, draftRemoved.size() - removedScroll));
            for (int i = 0; i < visibleR; i++) {
                int idx = i + removedScroll;
                String id = draftRemoved.get(idx);
                addRenderableWidget(btn("x", removedX + colW - 12, listTop + i * TAG_ITEM_ROW_H, 12, () -> removeFromRemoved(id)));
            }
        }

        int by = actionTopY;
        int bx = L + LIST_W + 12;
        // orden unificado Save/Delete/Duplicate/Reset/
        // Preview/[Undo] - mismo orden que CraftEditorScreen ya establecio
        // (ver su comentario). Solo se inserta Duplicate (nuevo) despues de
        // Delete, corriendo el resto +45px; el toggle Enabled de esta
        // pantalla ya vive arriba (linea ~254, junto a Category) y se deja
        // ahi a proposito - reubicarlo aca arriesgaba romper el layout del
        // resto del formulario sin necesidad, solo pide que TODAS
        // las pantallas *tengan* Duplicate en la fila de acciones, no que
        // el toggle Enabled ocupe una posicion fija en ella.
        // sec.4: offsets absolutos
        // reemplazados por cursor acumulado con btnWidth(label)+BTN_GAP.
        String saveL = Lang.get("gui.kve.btn.save"), deleteL = Lang.get("gui.kve.btn.delete"),
                dupL = Lang.get("gui.kve.btn.duplicate"), resetL = Lang.get("gui.kve.btn.reset"),
                previewL = Lang.get("gui.kve.btn.preview");
        KveGuiStyle.ButtonRow actionRow = new KveGuiStyle.ButtonRow(bx, by, L + LIST_W + RIGHT_PANEL_W - 4);
        KveGuiStyle.ActionBar bar = new KveGuiStyle.ActionBar(actionRow, this::addRenderableWidget);
        bar.primary(saveL, b -> save());
        bar.button(Lang.get("gui.kve.btn.preview"), b -> preview());
        bar.button(Lang.get("gui.kve.btn.duplicate"), b -> duplicateTag());
        bar.history(Lang.get("gui.kve.btn.undo"), () -> undo(), !currentKey.isEmpty() && TagExporter.canUndo(currentKey))
                .history(Lang.get("gui.kve.btn.reset"), () -> resetDraft());
        bar.end(() -> delete());
        buildPanel();
    }

    private String typeLabel(TagExporter.RegistryType t) {
        return switch (t) {
            case ITEM -> Lang.get("gui.kve.common.type.item");
            case BLOCK -> Lang.get("gui.kve.common.type.block");
            case ENTITY_TYPE -> Lang.get("gui.kve.common.type.entity");
            case FLUID -> Lang.get("gui.kve.common.type.fluid");
        };
    }

    private void cycleType() {
        draftType = draftType.next();
        if (!currentKey.isEmpty() || !draftAdded.isEmpty() || !draftRemoved.isEmpty()) {
            currentKey = "";
            draftAdded.clear();
            draftRemoved.clear();
            addedScroll = 0; removedScroll = 0;
            flash(Lang.get("gui.kve.tag.type_changed_hint"));
        }
        refresh();
    }
    private void toggleEnabled() { draftEnabled = !draftEnabled; refresh(); }
    private void toggleReplace() {
        draftReplace = !draftReplace;
        if (draftReplace) draftRemoved.clear();
        refresh();
    }

    private void openCommonTags() {
        List<String> loaded = loadedTags(draftType);
        List<String> options = !loaded.isEmpty() ? loaded : TagExporter.commonTags(draftType);
        String title = !loaded.isEmpty()
                ? Lang.get("gui.kve.tag.loaded_tags", options.size())
                : Lang.get("gui.kve.tag.common_tags");
        minecraft.setScreen(new TagPresetPickerScreen(this, title, options, id -> {
            if (id.equals(draftTagId)) return;
            TagExporter.TagEntry existing = TagExporter.getEntry(draftType.name() + "|" + id);
            if (existing != null) {
                loadDraft(existing);
            } else {
                currentKey = "";
                draftTagId = id;
                draftAdded.clear();
                draftRemoved.clear();
                addedScroll = 0; removedScroll = 0;
                refresh();
            }
        }));
    }

    private List<String> loadedTags(TagExporter.RegistryType type) {
        if (minecraft.level == null) return List.of();
        try {
            var access = minecraft.level.registryAccess();
            List<String> out = new ArrayList<>();
            switch (type) {
                case ITEM -> access.registryOrThrow(net.minecraft.core.registries.Registries.ITEM)
                        .getTagNames().forEach(k -> out.add(k.location().toString()));
                case BLOCK -> access.registryOrThrow(net.minecraft.core.registries.Registries.BLOCK)
                        .getTagNames().forEach(k -> out.add(k.location().toString()));
                case ENTITY_TYPE -> access.registryOrThrow(net.minecraft.core.registries.Registries.ENTITY_TYPE)
                        .getTagNames().forEach(k -> out.add(k.location().toString()));
                case FLUID -> access.registryOrThrow(net.minecraft.core.registries.Registries.FLUID)
                        .getTagNames().forEach(k -> out.add(k.location().toString()));
            }
            Collections.sort(out);
            return out;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private void addToAdded() {
        addPickerFor(draftAdded, draftRemoved);
    }

    private void addByPattern() {
        minecraft.setScreen(new KveTextInputScreen(this, Component.literal(Lang.get("gui.kve.tag.pattern_title")), "",
                Lang.get("gui.kve.tag.pattern_hint"), value -> {
                    var reg = registryFor(draftType);
                    List<String> ids = com.kve.common.util.IdPattern.select(registryIds(reg), value, tag -> tagMemberIds(reg, tag));
                    int before = draftAdded.size();
                    for (String id : ids) addResolvingConflict(draftAdded, draftRemoved, id);
                    minecraft.setScreen(this);
                    flash(Lang.get("gui.kve.tag.pattern_added", draftAdded.size() - before, ids.size()));
                }));
    }

    private static net.minecraft.core.Registry<?> registryFor(TagExporter.RegistryType type) {
        return switch (type) {
            case ITEM -> net.minecraft.core.registries.BuiltInRegistries.ITEM;
            case BLOCK -> net.minecraft.core.registries.BuiltInRegistries.BLOCK;
            case ENTITY_TYPE -> net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE;
            case FLUID -> net.minecraft.core.registries.BuiltInRegistries.FLUID;
        };
    }

    private static List<String> registryIds(net.minecraft.core.Registry<?> reg) {
        List<String> out = new ArrayList<>();
        for (ResourceLocation k : reg.keySet()) out.add(k.toString());
        Collections.sort(out);
        return out;
    }

    private static <T> List<String> tagMemberIds(net.minecraft.core.Registry<T> reg, String tagId) {
        ResourceLocation rl = ResourceLocation.tryParse(tagId);
        if (rl == null) return List.of();
        return reg.getTag(net.minecraft.tags.TagKey.create(reg.key(), rl))
                .map(set -> set.stream().map(h -> String.valueOf(reg.getKey(h.value()))).toList())
                .orElse(List.of());
    }

    private void addToRemoved() {
        addPickerFor(draftRemoved, draftAdded);
    }

    private void removeFromAdded(String id) {
        draftAdded.remove(id);
        addedScroll = Math.min(addedScroll, Math.max(0, draftAdded.size() - editRows()));
        refresh();
    }

    private void removeFromRemoved(String id) {
        draftRemoved.remove(id);
        removedScroll = Math.min(removedScroll, Math.max(0, draftRemoved.size() - editRows()));
        refresh();
    }

    private void addPickerFor(List<String> target, List<String> opposite) {
        if (draftType == TagExporter.RegistryType.ENTITY_TYPE) {
            minecraft.setScreen(new MobPickerScreen(this, et -> {
                ResourceLocation k = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(et);
                if (k != null) addResolvingConflict(target, opposite, k.toString());
            }));
        } else {
            minecraft.setScreen(new ItemPickerScreen(this, (ItemStack stack) -> {
                String id = idOf(stack);
                if (id != null) addResolvingConflict(target, opposite, id);
            }));
        }
    }

    private void addResolvingConflict(List<String> target, List<String> opposite, String id) {
        if (target.contains(id)) return;
        opposite.remove(id);
        target.add(id);
        addedScroll = Math.min(addedScroll, Math.max(0, draftAdded.size() - editRows()));
        removedScroll = Math.min(removedScroll, Math.max(0, draftRemoved.size() - editRows()));
    }

    private String idOf(ItemStack stack) {
        var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key != null ? key.toString() : null;
    }

    private void openTagMembers() {
        List<String> defaultMembers = draftReplace ? List.of() : resolveDefaultTagMembers();
        List<String> userAdded = new ArrayList<>();
        for (String a : draftAdded) if (!draftRemoved.contains(a) && !userAdded.contains(a)) userAdded.add(a);
        Collections.sort(userAdded);
        if (defaultMembers.isEmpty() && userAdded.isEmpty()) flash(Lang.get("gui.kve.tag.members.empty"));
        minecraft.setScreen(new TagMembersScreen(this, draftTagId.isBlank() ? "?" : draftTagId,
                defaultMembers, userAdded, draftType,
                this::applyTagMemberRemovals,
                () -> addPickerFor(draftRemoved, draftAdded)));
    }

    private List<String> resolveDefaultTagMembers() {
        List<String> result = new ArrayList<>();
        if (draftTagId != null && !draftTagId.isBlank() && draftTagId.contains(":") && minecraft.level != null) {
            try {
                ResourceLocation id = ResourceLocation.parse(draftTagId);
                var access = minecraft.level.registryAccess();
                switch (draftType) {
                    case ITEM -> access.registryOrThrow(net.minecraft.core.registries.Registries.ITEM)
                            .getTag(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, id))
                            .ifPresent(set -> set.stream().forEach(h -> h.unwrapKey().ifPresent(k -> result.add(k.location().toString()))));
                    case BLOCK -> access.registryOrThrow(net.minecraft.core.registries.Registries.BLOCK)
                            .getTag(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, id))
                            .ifPresent(set -> set.stream().forEach(h -> h.unwrapKey().ifPresent(k -> result.add(k.location().toString()))));
                    case ENTITY_TYPE -> access.registryOrThrow(net.minecraft.core.registries.Registries.ENTITY_TYPE)
                            .getTag(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, id))
                            .ifPresent(set -> set.stream().forEach(h -> h.unwrapKey().ifPresent(k -> result.add(k.location().toString()))));
                    case FLUID -> access.registryOrThrow(net.minecraft.core.registries.Registries.FLUID)
                            .getTag(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.FLUID, id))
                            .ifPresent(set -> set.stream().forEach(h -> h.unwrapKey().ifPresent(k -> result.add(k.location().toString()))));
                }
            } catch (Exception ignored) {}
        }
        result.removeAll(draftRemoved);
        result.removeAll(draftAdded);
        Collections.sort(result);
        return result;
    }

    private void applyTagMemberRemovals(java.util.Set<String> ids) {
        for (String id : ids) {
            if (draftAdded.remove(id)) continue;
            if (!draftRemoved.contains(id)) draftRemoved.add(id);
        }
        addedScroll = Math.min(addedScroll, Math.max(0, draftAdded.size() - editRows()));
        removedScroll = Math.min(removedScroll, Math.max(0, draftRemoved.size() - editRows()));
        refresh();
    }

    public static void applyMemberRemoval(String tagKey, String itemId) {
        TagExporter.TagEntry current = TagExporter.getEntry(tagKey);
        if (current == null) return;

        TagExporter.TagEntry previous = current.copy();
        TagExporter.TagEntry mutated = current.copy();
        if (!mutated.added.remove(itemId)) {
            if (!mutated.removed.contains(itemId)) mutated.removed.add(itemId);
        }
        TagExporter.stageTag(mutated);

        PendingChangeManager.queue(PendingChange.of(PendingDomain.TAGS, tagKey, PendingChange.Action.EDIT, false,
                () -> {
                    TagExporter.commitTags();
                    KveChangeLog.record(KveChangeLog.Log.TAGS, PendingChange.Action.EDIT, tagKey);
                },
                () -> TagExporter.discardStagedTag(tagKey, previous))
                .withDiff(() -> TagExporter.previewTagJS(previous), () -> TagExporter.previewTagJS(mutated)));
    }

    private void toggleObjectMode() {
        objectMode = !objectMode;
        objTagsCache = null;
        panelFilter = "";
        if (panelScrollBar != null) panelScrollBar.setScroll(0);
        refresh();
    }

    private void buildObjectWidgets(int ax) {
        objTagsCache = null;
        KveGuiStyle.Layout ly = new KveGuiStyle.Layout(ax, T + 16, GAP);
        int ay = ly.row();
        String typeLbl = Lang.get("gui.kve.tag.type_btn", typeLabel(objType));
        addRenderableWidget(btn(typeLbl, ax, ay, 0, this::cycleObjectType));
        addRenderableWidget(btn(Lang.get("gui.kve.tag.obj.pick"), ax + KveGuiStyle.btnWidth(typeLbl) + KveGuiStyle.BTN_GAP, ay, 0, this::pickObject));

        ay = ly.row();
        fObjId = new KveScaledEditBox(font, ax, ay, 200, 12, Lang.c("gui.kve.tag.obj.id_narration"));
        fObjId.setMaxLength(128);
        fObjId.setValue(objId);
        fObjId.setHint(Lang.c("gui.kve.tag.obj.id_hint"));
        fObjId.setResponder(s -> setObject(s.trim()));
        addRenderableWidget(fObjId);
        rowTagIdY = ay;
        ly.gap(12);

        rowListsHeaderY = ly.y();
        int listBtnY = rowListsHeaderY + 11;
        rowListsTop = listBtnY + 18;
        String addLbl = Lang.get("gui.kve.tag.obj.add_to");
        addRenderableWidget(btn(addLbl, ax, listBtnY, 0, this::pickTagForObject));
        int newX = ax + KveGuiStyle.btnWidth(addLbl) + KveGuiStyle.BTN_GAP;
        addRenderableWidget(KveGuiStyle.clampedBtn(newX, listBtnY, L + LIST_W + RIGHT_PANEL_W - 4 - newX,
                Lang.get("gui.kve.tag.obj.add_new"), b -> newTagForObject()));

        KveGuiStyle.ButtonRow actionRow = new KveGuiStyle.ButtonRow(ax, actionTopY, L + LIST_W + RIGHT_PANEL_W - 4);
        KveGuiStyle.ActionBar bar = new KveGuiStyle.ActionBar(actionRow, this::addRenderableWidget);
        bar.primary(Lang.get("gui.kve.btn.save"), b -> saveObject());
        bar.button(Lang.get("gui.kve.btn.preview"), b -> previewObject());
        bar.history(Lang.get("gui.kve.btn.reset"), () -> {
            objChanges.clear();
            objChanged();
            flash(Lang.get("gui.kve.tag.obj.cleared"));
        });
        bar.end(null);
    }

    /** Filas visibles de las listas del formulario: lo que entre hasta el aviso y la fila de acciones. */
    private int editRows() {
        return Math.max(1, (actionTopY - 14 - rowListsTop) / TAG_ITEM_ROW_H);
    }

    private int savedRows() {
        return Math.max(1, (GUI_H - 36) / SAVED_ROW_H);
    }

    /** Lineas que necesita la fila de acciones del modo tag en el ancho del formulario. */
    private int tagActionRows() {
        // a la vista solo Guardar, "Mas" y Eliminar (con su separacion de 10 px), igual que el resto de editores
        List<String> labels = List.of(Lang.get("gui.kve.btn.save"), Lang.get("gui.kve.actions.more"), Lang.get("gui.kve.btn.delete"));
        int w = 10 - KveGuiStyle.BTN_GAP;
        for (String l : labels) w += KveGuiStyle.btnWidth(l) + KveGuiStyle.BTN_GAP;
        return w > RIGHT_PANEL_W - 20 ? 2 : 1;
    }

    private int fitListW(int listW, int avail) {
        // La columna de guardados se achica (hasta su minimo) antes que dejar el formulario fuera de pantalla.
        return Math.max(LIST_W_MIN, Math.min(listW, avail - RIGHT_PANEL_W));
    }

    private int layoutWidth(int listW, int avail) {
        int base = listW + RIGHT_PANEL_W;
        panelShown = avail - base >= PANEL_W_MIN;
        if (hosted) return Math.max(avail, base);
        return base + (panelShown ? Math.min(PANEL_W_DEFAULT, avail - base) : 0);
    }

    private int formListW() {
        return RIGHT_PANEL_W - 18;
    }

    private void objChanged() {
        objTagsCache = null;
        int rows = editRows();
        objTagsScroll = Math.max(0, Math.min(objTagsScroll, objectTags().size() - rows));
        objChangesScroll = Math.max(0, Math.min(objChangesScroll, objChanges.size() - rows));
    }

    private void cycleObjectType() {
        objType = objType.next();
        setObject("");
        refresh();
    }

    private void setObject(String id) {
        if (id.equals(objId)) return;
        if (!objChanges.isEmpty()) {
            flash(Lang.get("gui.kve.tag.obj.discarded", objChanges.size()));
            objChanges.clear();
        }
        objId = id;
        objTagsScroll = 0;
        objChangesScroll = 0;
        objTagsCache = null;
    }

    private void pickObject() {
        switch (objType) {
            case ENTITY_TYPE -> minecraft.setScreen(new MobPickerScreen(this, et -> {
                ResourceLocation k = BuiltInRegistries.ENTITY_TYPE.getKey(et);
                if (k != null) setObject(k.toString());
            }));
            case FLUID -> minecraft.setScreen(new com.kve.client.gui.picker.FluidPickerScreen(this, this::setObject));
            case BLOCK -> minecraft.setScreen(new ItemPickerScreen(this, (ItemStack stack) -> {
                if (stack.getItem() instanceof net.minecraft.world.item.BlockItem bi) {
                    setObject(String.valueOf(BuiltInRegistries.BLOCK.getKey(bi.getBlock())));
                } else {
                    flash(Lang.get("gui.kve.tag.obj.not_block"));
                }
            }));
            default -> minecraft.setScreen(new ItemPickerScreen(this, (ItemStack stack) -> {
                String id = idOf(stack);
                if (id != null) setObject(id);
            }));
        }
    }

    private List<String> objectTags() {
        if (objTagsCache != null) return objTagsCache;
        java.util.TreeSet<String> tags = new java.util.TreeSet<>(liveTagsOf(objType, objId));
        if (!objId.isBlank()) {
            for (TagExporter.TagEntry e : TagExporter.getEntries()) {
                if (e.regType != objType || !e.enabled) continue;
                if (e.added.contains(objId)) tags.add(e.tagId);
                else if (e.removed.contains(objId) || e.replace) tags.remove(e.tagId);
            }
            objChanges.forEach((tag, add) -> { if (add) tags.add(tag); else tags.remove(tag); });
        }
        objTagsCache = new ArrayList<>(tags);
        return objTagsCache;
    }

    private List<String> liveTagsOf(TagExporter.RegistryType type, String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null || id.isBlank() || minecraft.level == null) return List.of();
        try {
            var access = minecraft.level.registryAccess();
            return switch (type) {
                case ITEM -> tagsOf(access.registryOrThrow(net.minecraft.core.registries.Registries.ITEM), rl);
                case BLOCK -> tagsOf(access.registryOrThrow(net.minecraft.core.registries.Registries.BLOCK), rl);
                case ENTITY_TYPE -> tagsOf(access.registryOrThrow(net.minecraft.core.registries.Registries.ENTITY_TYPE), rl);
                case FLUID -> tagsOf(access.registryOrThrow(net.minecraft.core.registries.Registries.FLUID), rl);
            };
        } catch (Exception ex) {
            return List.of();
        }
    }

    private static <T> List<String> tagsOf(net.minecraft.core.Registry<T> reg, ResourceLocation rl) {
        return reg.getHolder(net.minecraft.resources.ResourceKey.create(reg.key(), rl))
                .map(h -> h.tags().map(t -> t.location().toString()).toList())
                .orElse(List.of());
    }

    private String objName() {
        ResourceLocation rl = ResourceLocation.tryParse(objId);
        if (rl == null) return objId;
        try {
            return switch (objType) {
                case ITEM -> BuiltInRegistries.ITEM.getOptional(rl).map(i -> new ItemStack(i).getHoverName().getString()).orElse(objId);
                case BLOCK -> BuiltInRegistries.BLOCK.getOptional(rl).map(b -> b.getName().getString()).orElse(objId);
                case ENTITY_TYPE -> BuiltInRegistries.ENTITY_TYPE.getOptional(rl).map(t -> t.getDescription().getString()).orElse(objId);
                case FLUID -> objId;
            };
        } catch (Exception ex) {
            return objId;
        }
    }

    private void addObjToTag(String tag) {
        if (Boolean.FALSE.equals(objChanges.get(tag))) objChanges.remove(tag);
        else if (!objectTags().contains(tag)) objChanges.put(tag, true);
        objChanged();
    }

    private void removeObjFromTag(String tag) {
        if (Boolean.TRUE.equals(objChanges.get(tag))) objChanges.remove(tag);
        else objChanges.put(tag, false);
        objChanged();
    }

    private void pickTagForObject() {
        if (objId.isBlank()) { flash(Lang.get("gui.kve.tag.obj.need_object")); return; }
        java.util.TreeSet<String> options = new java.util.TreeSet<>(loadedTags(objType));
        for (TagExporter.TagEntry e : TagExporter.getEntries()) if (e.regType == objType) options.add(e.tagId);
        options.removeAll(objectTags());
        minecraft.setScreen(new TagPresetPickerScreen(this, Lang.get("gui.kve.tag.obj.add_title", objName()),
                new ArrayList<>(options), this::addObjToTag));
    }

    private void newTagForObject() {
        if (objId.isBlank()) { flash(Lang.get("gui.kve.tag.obj.need_object")); return; }
        minecraft.setScreen(new KveTextInputScreen(this, Component.literal(Lang.get("gui.kve.tag.obj.new_title", objName())), "",
                Lang.get("gui.kve.tag.id_hint"), value -> {
                    String v = value.trim();
                    if (v.startsWith("#")) v = v.substring(1);
                    if (ResourceLocation.tryParse(v) == null || !v.contains(":")) {
                        flash(Lang.get("gui.kve.tag.err.bad_id_format"));
                    } else {
                        addObjToTag(v);
                    }
                    minecraft.setScreen(this);
                }));
    }

    private void openTagFromObject(String tag) {
        objectMode = false;
        TagExporter.TagEntry existing = TagExporter.getEntry(objType.name() + "|" + tag);
        if (existing != null) { loadDraft(existing); return; }
        currentKey = "";
        draftType = objType;
        draftTagId = tag;
        draftCategory = "";
        draftEnabled = true;
        draftReplace = false;
        draftAdded.clear();
        draftRemoved.clear();
        addedScroll = 0; removedScroll = 0;
        refresh();
    }

    private TagExporter.TagEntry objMutated(String tag, boolean add) {
        TagExporter.TagEntry existing = TagExporter.getEntry(objType.name() + "|" + tag);
        TagExporter.TagEntry m;
        if (existing != null) {
            m = existing.copy();
        } else {
            m = new TagExporter.TagEntry();
            m.regType = objType;
            m.tagId = tag;
        }
        if (add) {
            m.removed.remove(objId);
            if (!m.added.contains(objId)) m.added.add(objId);
        } else if (!m.added.remove(objId) && !m.replace && !m.removed.contains(objId)) {
            m.removed.add(objId);
        }
        return m;
    }

    private void saveObject() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }
        if (objId.isBlank()) { flash(Lang.get("gui.kve.tag.obj.need_object")); return; }
        if (objChanges.isEmpty()) { flash(Lang.get("gui.kve.tag.obj.no_changes")); return; }
        int queued = 0;
        List<String> problemsAll = new ArrayList<>();
        for (var ch : new ArrayList<>(objChanges.entrySet())) {
            TagExporter.TagEntry e = objMutated(ch.getKey(), ch.getValue());
            List<String> problems = TagExporter.validateTag(e);
            if (!problems.isEmpty()) { problemsAll.addAll(problems); continue; }
            TagExporter.TagEntry previous = TagExporter.getEntry(e.key());
            PendingChange.Action action = previous == null ? PendingChange.Action.CREATE : PendingChange.Action.EDIT;
            TagExporter.TagEntry prevCopy = previous == null ? null : previous.copy();
            TagExporter.stageTag(e);
            String key = e.key();
            PendingChangeManager.queue(PendingChange.of(PendingDomain.TAGS, key, action, false,
                    () -> {
                        TagExporter.commitTags();
                        KveChangeLog.record(KveChangeLog.Log.TAGS, action, key);
                    },
                    () -> {
                        TagExporter.discardStagedTag(key, prevCopy);
                        refreshSaved();
                    })
                    .withDiff(prevCopy == null ? null : () -> TagExporter.previewTagJS(prevCopy), () -> TagExporter.previewTagJS(e)));
            objChanges.remove(ch.getKey());
            queued++;
        }
        objChanged();
        flash(problemsAll.isEmpty()
                ? Lang.get("gui.kve.tag.obj.saved", queued)
                : String.join(" ", problemsAll));
        refreshSaved();
        refresh();
    }

    private void previewObject() {
        if (objId.isBlank()) { flash(Lang.get("gui.kve.tag.obj.need_object")); return; }
        if (objChanges.isEmpty()) { flash(Lang.get("gui.kve.tag.obj.no_changes")); return; }
        StringBuilder sb = new StringBuilder();
        objChanges.forEach((tag, add) -> sb.append(TagExporter.previewTagJS(objMutated(tag, add))).append('\n'));
        minecraft.setScreen(new RecipePreviewScreen(this, objId, sb.toString()));
    }

    private void renderObject(GuiGraphics g, int mx, int my) {
        int ax = L + LIST_W + 12, cw = formListW();
        if (!objId.isBlank() && objType != TagExporter.RegistryType.ENTITY_TYPE) {
            Item icon = objType == TagExporter.RegistryType.BLOCK ? MiniItemIcon.block(objId) : MiniItemIcon.item(objId);
            MiniItemIcon.draw(g, ax + 204, rowTagIdY, 12, icon);
        }
        List<String> tags = objectTags();
        String summary = objId.isBlank() ? Lang.get("gui.kve.tag.obj.hint")
                : minecraft.level == null ? Lang.get("gui.kve.tag.preview_need_world")
                : Lang.get("gui.kve.tag.obj.summary", objName(), tags.size(), objChanges.size());
        KveGuiStyle.drawTextSafe(g, summary, ax, rowTagIdY + 15, cw, 0xFF6688AA);
        String header = Lang.get("gui.kve.tag.obj.changes", objChanges.size());
        if (!panelShown) header = Lang.get("gui.kve.tag.obj.in_tags", panelItems().size()) + "   " + header;
        KveGuiStyle.drawTextSafe(g, header, ax, rowListsHeaderY, cw, 0xFFE8C96A);

        int rows = editRows();
        List<String[]> middle = objectMiddleRows();
        if (middle.isEmpty() && !objId.isBlank()) {
            drawWrapped(g, Lang.get(panelShown ? "gui.kve.tag.obj.changes_empty" : "gui.kve.tag.panel.obj_none"),
                    ax + 2, rowListsTop + 2, cw - 4, KveGuiStyle.TEXT_MUTED);
        }
        for (int i = 0; i < rows && i + objChangesScroll < middle.size(); i++) {
            String[] row = middle.get(i + objChangesScroll);
            int ry = rowListsTop + i * TAG_ITEM_ROW_H;
            String text = switch (row[0]) {
                case "add" -> "+ #" + row[1];
                case "remove" -> "- #" + row[1];
                default -> "#" + row[1];
            };
            int color = switch (row[0]) {
                case "add" -> 0xFFAADDAA;
                case "remove" -> 0xFFDDAAAA;
                default -> KveGuiStyle.TEXT_LABEL;
            };
            if (row[0].equals("in") && mx >= ax && mx < ax + cw - 18 && my >= ry && my < ry + TAG_ITEM_ROW_H) {
                g.fill(ax, ry, ax + cw - 18, ry + TAG_ITEM_ROW_H - 1, KveGuiStyle.BORDER_FIELD);
            }
            KveGuiStyle.drawTextSafe(g, text, ax + 2, ry + 2, cw - 22, color);
            drawRowX(g, ax + cw - 18, ry, mx, my);
            g.fill(ax, ry + TAG_ITEM_ROW_H - 1, ax + cw - 6, ry + TAG_ITEM_ROW_H, KveGuiStyle.BORDER_SUBTLE);
        }
    }

    private List<String[]> objectMiddleRows() {
        List<String[]> out = new ArrayList<>();
        if (!panelShown) for (String tag : panelItems()) out.add(new String[]{"in", tag});
        objChanges.forEach((tag, add) -> out.add(new String[]{add ? "add" : "remove", tag}));
        return out;
    }

    private void drawWrapped(GuiGraphics g, String text, int x, int y, int maxW, int color) {
        int ly = y;
        for (String line : KveGuiStyle.wrapTextSafe(font, text, maxW)) {
            KveGuiStyle.drawTextSafe(g, line, x, ly, maxW, color);
            ly += 10;
        }
    }

    private void drawRowX(GuiGraphics g, int x, int ry, int mx, int my) {
        boolean hov = mx >= x && mx < x + 12 && my >= ry && my < ry + TAG_ITEM_ROW_H - 1;
        g.fill(x, ry + 1, x + 12, ry + TAG_ITEM_ROW_H - 1, hov ? 0xFF6A2A2A : 0xFF2A1A1A);
        KveGuiStyle.drawTextSafe(g, "x", x + 4, ry + 2, 8, hov ? 0xFFFFAAAA : 0xFFCC8888);
    }

    private boolean objectRowsClicked(double mx, double my) {
        int ax = L + LIST_W + 12, cw = formListW();
        int rows = editRows();
        if (my < rowListsTop || my >= rowListsTop + rows * TAG_ITEM_ROW_H) return false;
        int i = (int) ((my - rowListsTop) / TAG_ITEM_ROW_H) + objChangesScroll;
        List<String[]> middle = objectMiddleRows();
        if (mx < ax || mx >= ax + cw - 6 || i >= middle.size()) return false;
        String[] row = middle.get(i);
        boolean onX = mx >= ax + cw - 18;
        if (row[0].equals("in")) {
            if (onX) removeObjFromTag(row[1]); else openTagFromObject(row[1]);
            return true;
        }
        if (!onX) return false;
        objChanges.remove(row[1]);
        objChanged();
        return true;
    }

    private boolean objectRowsScrolled(double mx, double my, double delta) {
        int ax = L + LIST_W + 12, cw = formListW();
        int rows = editRows();
        if (my < rowListsTop || my >= rowListsTop + rows * TAG_ITEM_ROW_H) return false;
        if (mx >= ax && mx < ax + cw) {
            objChangesScroll = Math.max(0, Math.min(objectMiddleRows().size() - rows, objChangesScroll - (int) Math.signum(delta)));
            return true;
        }
        return false;
    }

    private List<String> panelItems() {
        List<String> base;
        if (objectMode) {
            base = new ArrayList<>(objectTags());
            base.removeIf(tag -> Boolean.TRUE.equals(objChanges.get(tag)));
        } else {
            base = draftReplace ? List.of() : panelMembers();
        }
        if (panelFilter.isBlank()) return base;
        List<String> out = new ArrayList<>();
        for (String s : base) if (SearchFilter.matches(s, panelFilter)) out.add(s);
        return out;
    }

    private List<String> panelMembers() {
        String key = draftType + "|" + draftTagId + "|" + draftAdded.hashCode() + "|" + draftRemoved.hashCode()
                + "|" + System.identityHashCode(minecraft.level);
        if (membersCache == null || !key.equals(membersCacheKey)) {
            membersCache = resolveDefaultTagMembers();
            membersCacheKey = key;
        }
        return membersCache;
    }

    private int panelX() { return L + LIST_W + RIGHT_PANEL_W + 6; }
    private int panelW() { return L + GUI_W - 4 - panelX(); }
    private int panelListTop() { return objectMode || draftReplace ? T + 45 : T + 61; }
    private int panelRows() { return Math.max(1, (T + GUI_H - 6 - panelListTop()) / TAG_ITEM_ROW_H); }
    private int panelRowW() { return panelW() - 8; }

    private void buildPanel() {
        if (!panelShown) { panelScrollBar = null; return; }
        int px = panelX(), pw = panelW();
        panelRemoveBtn = null;
        if (!objectMode && !draftReplace) {
            panelSelected.retainAll(new java.util.HashSet<>(panelMembers()));
            int gap = KveGuiStyle.BTN_GAP;
            int bw = (pw - 2 * gap) / 3;
            addRenderableWidget(KveGuiStyle.clampedBtn(px, T + 44, bw, Lang.get("gui.kve.tag.panel.select_all"), b -> panelToggleAll()));
            panelRemoveBtn = addRenderableWidget(KveGuiStyle.clampedBtn(px + bw + gap, T + 44, bw,
                    Lang.get("gui.kve.tag.panel.remove_selected", panelSelected.size()), b -> panelRemoveSelected()));
            panelRemoveBtn.active = !panelSelected.isEmpty();
            addRenderableWidget(KveGuiStyle.clampedBtn(px + 2 * (bw + gap), T + 44, pw - 2 * (bw + gap),
                    Lang.get("gui.kve.tag.members.manual_add"), b -> addPickerFor(draftRemoved, draftAdded)));
        } else {
            panelSelected.clear();
        }
        panelSearch = new KveScaledEditBox(font, px, T + 30, pw, 11, Lang.c("gui.kve.tag.members.search_hint"));
        panelSearch.setMaxLength(64);
        panelSearch.setValue(panelFilter);
        panelSearch.setHint(Lang.c(objectMode ? "gui.kve.tag.panel.search_tags" : "gui.kve.tag.members.search_hint"));
        panelSearch.setResponder(s -> { panelFilter = s; if (panelScrollBar != null) panelScrollBar.setScroll(0); });
        addRenderableWidget(panelSearch);
        int keep = panelScrollBar == null ? 0 : panelScrollBar.getScroll();
        panelScrollBar = new ScrollBar(px + pw - 6, panelListTop(), panelRows() * TAG_ITEM_ROW_H, 0, panelRows());
        panelScrollBar.setTotals(panelItems().size(), panelRows());
        panelScrollBar.setScroll(keep);
    }

    private void renderPanel(GuiGraphics g, int mx, int my) {
        if (!panelShown || panelScrollBar == null) return;
        int px = panelX(), pw = panelW(), rw = panelRowW();
        g.fill(px - 6, T + 15, px - 5, T + GUI_H - 3, KveGuiStyle.BORDER_SUBTLE);
        List<String> items = panelItems();
        String header = objectMode ? Lang.get("gui.kve.tag.obj.in_tags", items.size())
                : Lang.get("gui.kve.tag.panel.members", items.size());
        KveGuiStyle.drawTextSafe(g, header, px, T + 18, pw, 0xFF88CC88);

        int top = panelListTop(), rows = panelRows();
        String empty = panelEmptyText(items);
        if (empty != null) drawWrapped(g, empty, px + 2, top + 2, rw - 4, KveGuiStyle.TEXT_MUTED);

        panelScrollBar.setTotals(items.size(), rows);
        int scroll = panelScrollBar.getScroll();
        for (int i = 0; i < rows && i + scroll < items.size(); i++) {
            String id = items.get(i + scroll);
            int ry = top + i * TAG_ITEM_ROW_H;
            boolean sel = !objectMode && panelSelected.contains(id);
            boolean hov = mx >= px && mx < px + rw - 14 && my >= ry && my < ry + TAG_ITEM_ROW_H;
            if (hov || sel) g.fill(px, ry, px + rw - 14, ry + TAG_ITEM_ROW_H - 1, KveGuiStyle.BORDER_FIELD);
            int textX = px + 2;
            if (!objectMode) {
                KveGuiStyle.drawTextSafe(g, sel ? "☑" : "☐", px + 1, ry + 2, 10, sel ? 0xFF88CC88 : KveGuiStyle.th(0xFF556677));
                drawTagItemFrame(g, px + 11, ry, id);
                textX = px + 11 + TAG_ITEM_ICON + 3;
            }
            KveGuiStyle.drawTextSafe(g, objectMode ? "#" + id : id, textX, ry + 2, px + rw - 16 - textX, sel ? 0xFFAADDAA : KveGuiStyle.TEXT_LABEL);
            drawRowX(g, px + rw - 13, ry, mx, my);
            g.fill(px, ry + TAG_ITEM_ROW_H - 1, px + rw - 1, ry + TAG_ITEM_ROW_H, KveGuiStyle.BORDER_SUBTLE);
        }
        panelScrollBar.render(g, mx, my);
    }

    /** Por que el panel esta vacio, o null si tiene filas. */
    private String panelEmptyText(List<String> items) {
        if (!items.isEmpty()) return null;
        if (objectMode) {
            if (objId.isBlank()) return Lang.get("gui.kve.tag.obj.need_object");
            if (minecraft.level == null) return Lang.get("gui.kve.tag.preview_need_world");
            return panelFilter.isBlank() ? Lang.get("gui.kve.tag.panel.obj_none") : Lang.get("gui.kve.tag.panel.no_match");
        }
        if (draftReplace) return Lang.get("gui.kve.tag.panel.replace_hint");
        if (draftTagId.isBlank() || !draftTagId.contains(":")) return Lang.get("gui.kve.tag.panel.need_tag");
        if (minecraft.level == null) return Lang.get("gui.kve.tag.preview_need_world");
        return panelFilter.isBlank() ? Lang.get("gui.kve.tag.panel.empty") : Lang.get("gui.kve.tag.panel.no_match");
    }

    private boolean panelClicked(double mx, double my, int btn) {
        if (panelScrollBar == null) return false;
        if (panelScrollBar.mouseClicked(mx, my, btn)) return true;
        int px = panelX(), rw = panelRowW(), top = panelListTop(), rows = panelRows();
        if (mx < px || mx >= px + rw || my < top || my >= top + rows * TAG_ITEM_ROW_H) return false;
        int i = (int) ((my - top) / TAG_ITEM_ROW_H) + panelScrollBar.getScroll();
        List<String> items = panelItems();
        if (i >= items.size()) return false;
        String id = items.get(i);
        boolean onX = mx >= px + rw - 13;
        if (objectMode) {
            if (onX) removeObjFromTag(id); else openTagFromObject(id);
            return true;
        }
        if (onX) {
            panelSelected.remove(id);
            applyTagMemberRemovals(java.util.Set.of(id));
            return true;
        }
        if (!panelSelected.remove(id)) panelSelected.add(id);
        updatePanelRemoveBtn();
        return true;
    }

    private void panelToggleAll() {
        List<String> items = panelItems();
        if (!items.isEmpty() && panelSelected.containsAll(items)) items.forEach(panelSelected::remove);
        else panelSelected.addAll(items);
        updatePanelRemoveBtn();
    }

    private void panelRemoveSelected() {
        if (panelSelected.isEmpty()) return;
        java.util.Set<String> ids = new java.util.LinkedHashSet<>(panelSelected);
        panelSelected.clear();
        applyTagMemberRemovals(ids);
    }

    private void updatePanelRemoveBtn() {
        if (panelRemoveBtn == null) return;
        panelRemoveBtn.setMessage(Component.literal(Lang.get("gui.kve.tag.panel.remove_selected", panelSelected.size())));
        panelRemoveBtn.active = !panelSelected.isEmpty();
    }

    private void newTag() {
        currentKey = "";
        draftType = TagExporter.RegistryType.ITEM;
        draftTagId = ""; draftCategory = ""; draftEnabled = true; draftReplace = false;
        draftAdded.clear(); draftRemoved.clear();
        addedScroll = 0; removedScroll = 0;
        refresh();
    }

    private void resetDraft() {
        if (currentKey.isEmpty()) { newTag(); return; }
        TagExporter.TagEntry e = TagExporter.getEntry(currentKey);
        if (e != null) loadDraft(e); else newTag();
    }

    private void duplicateTag() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }
        currentKey = "";
        String base = draftTagId.trim();
        String candidate = (base.isEmpty() ? "shovel_js:tag" : base) + "_copy";
        String finalId = candidate;
        int n = 2;
        while (TagExporter.getEntry(draftType.name() + "|" + finalId) != null) {
            finalId = candidate + n;
            n++;
        }
        draftTagId = finalId;
        flash(Lang.get("gui.kve.tag.duplicated_hint"));
        refresh();
    }

    private void loadDraft(TagExporter.TagEntry e) {
        objectMode = false;
        currentKey = e.key();
        draftType = e.regType;
        draftTagId = e.tagId;
        draftCategory = e.category;
        draftEnabled = e.enabled;
        draftReplace = e.replace;
        draftAdded.clear(); draftAdded.addAll(e.added);
        draftRemoved.clear(); draftRemoved.addAll(e.removed);
        if (healAddedRemovedConflict()) flash(Lang.get("gui.kve.tag.conflict_resolved_hint"));
        addedScroll = 0; removedScroll = 0;
        refresh();
    }

    private boolean healAddedRemovedConflict() {
        return !draftReplace && draftAdded.removeIf(draftRemoved::contains);
    }

    private TagExporter.TagEntry buildDraftTagEntry() {
        TagExporter.TagEntry e = new TagExporter.TagEntry();
        e.regType = draftType;
        e.tagId = draftTagId.trim();
        e.category = draftCategory.trim();
        e.enabled = draftEnabled;
        e.replace = draftReplace;
        e.added = new ArrayList<>(draftAdded);
        e.removed = draftReplace ? new ArrayList<>() : new ArrayList<>(draftRemoved);
        return e;
    }

    private void save() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }
        TagExporter.TagEntry e = buildDraftTagEntry();

        List<String> problems = TagExporter.validateTag(e);
        if (!problems.isEmpty()) { flash(String.join(" ", problems)); return; }

        String oldKey = (!currentKey.isEmpty() && !currentKey.equals(e.key())) ? currentKey : null;
        TagExporter.TagEntry previousUnderOldKey = oldKey != null ? TagExporter.getEntry(oldKey) : null;
        TagExporter.TagEntry previous = TagExporter.getEntry(e.key());
        PendingChange.Action action = previous == null ? PendingChange.Action.CREATE : PendingChange.Action.EDIT;

        if (oldKey != null) TagExporter.stageDeleteTag(oldKey);
        TagExporter.stageTag(e);

        boolean conflict = ExternalScriptScanner.getTagStats(e.key()) != null;
        PendingChangeManager.queue(PendingChange.of(PendingDomain.TAGS, e.key(), action, false,
                () -> {
                    TagExporter.commitTags();
                    KveChangeLog.record(KveChangeLog.Log.TAGS, action, e.key());
                },
                () -> {
                    TagExporter.discardStagedTag(e.key(), previous);
                    if (oldKey != null) TagExporter.discardStagedTag(oldKey, previousUnderOldKey);
                    refreshSaved();
                })
                .withDiff(previous == null ? null : () -> TagExporter.previewTagJS(previous), () -> TagExporter.previewTagJS(e)));

        currentKey = e.key();
        flash(conflict
                ? Lang.get("gui.kve.tag.conflict")
                : e.replace
                        ? Lang.get("gui.kve.pending.queued_hint_reload") + Lang.get("gui.kve.tag.replace_hint")
                        : Lang.get("gui.kve.pending.queued_hint_reload"));
        refreshSaved();
        refresh();
    }

    private void preview() {
        TagExporter.TagEntry e = buildDraftTagEntry();
        if (e.tagId.isEmpty()) { flash(Lang.get("gui.kve.tag.type_id_first")); return; }
        String script = TagExporter.previewTagJS(e);
        minecraft.setScreen(new RecipePreviewScreen(this, e.tagId, script));
    }

    private void delete() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }
        if (currentKey.isEmpty()) { flash(Lang.get("gui.kve.tag.select_first")); return; }
        String key = currentKey;
        TagExporter.TagEntry previous = TagExporter.getEntry(key);
        TagExporter.stageDeleteTag(key);
        PendingChangeManager.queue(PendingChange.of(PendingDomain.TAGS, key, PendingChange.Action.DELETE, false,
                () -> {
                    TagExporter.commitTags();
                    KveChangeLog.record(KveChangeLog.Log.TAGS, PendingChange.Action.DELETE, key);
                },
                () -> {
                    if (previous != null) TagExporter.stageTag(previous);
                    refreshSaved();
                })
                .withDiff(previous == null ? null : () -> TagExporter.previewTagJS(previous), null));
        flash(Lang.get("gui.kve.pending.queued_hint_reload"));
        newTag();
        refreshSaved();
    }

    private void undo() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }
        if (currentKey.isEmpty()) return;
        String key = currentKey;
        TagExporter.TagEntry beforeUndo = TagExporter.getEntry(key);
        TagExporter.TagEntry restored = TagExporter.stageUndo(key);
        if (restored != null) {
            loadDraft(restored);
            PendingChangeManager.queue(PendingChange.of(PendingDomain.TAGS, key, PendingChange.Action.EDIT, false,
                    () -> {
                        TagExporter.commitTags();
                        KveChangeLog.record(KveChangeLog.Log.TAGS, PendingChange.Action.EDIT, key);
                    },
                    () -> {
                        TagExporter.discardStagedUndo(key, restored, beforeUndo);
                        refreshSaved();
                    })
                    .withDiff(() -> TagExporter.previewTagJS(beforeUndo == null ? restored : beforeUndo),
                            () -> TagExporter.previewTagJS(restored)));
            flash(Lang.get("gui.kve.pending.queued_hint_reload"));
            refreshSaved();
        }
        refresh();
    }

    private void onApplyPending() {
        if (!ClientPermissionState.canApply()) { flash(Lang.get("gui.kve.perm.apply_denied")); return; }
        if (PendingChangeManager.isEmpty()) return;
        if (PendingChangeManager.domainHasDiff(PendingDomain.TAGS)) {
            minecraft.setScreen(new DiffPreviewScreen(this,
                    PendingChangeManager.entriesForDomain(PendingDomain.TAGS),
                    this::proceedApply));
            return;
        }
        proceedApply();
    }

    private void proceedApply() {
        java.util.Set<PendingDomain> domains = PendingChangeManager.domainsPresent();
        if (domains.size() > 1) {
            int otherCount = PendingChangeManager.size() - PendingChangeManager.sizeForDomain(PendingDomain.TAGS);
            minecraft.setScreen(new KveConfirmScreen(
                    confirmed -> {
                        minecraft.setScreen(this);
                        applyPendingResult(confirmed ? PendingChangeManager.applyAll() : PendingChangeManager.applyDomain(PendingDomain.TAGS));
                    },
                    Component.literal(Lang.get("gui.kve.pending.other_domains_title")),
                    Component.literal(Lang.get("gui.kve.pending.other_domains_msg", otherCount))));
            return;
        }
        applyPendingResult(PendingChangeManager.applyDomain(PendingDomain.TAGS));
    }

    private void applyPendingResult(PendingChangeManager.ApplyResult result) {
                com.kve.client.gui.KveToast.applied(result, TagExporter.getLastWriteWarnings());
        refreshSaved();
        newTag();
    }

    private void onDiscardPending() {
        if (PendingChangeManager.isEmpty()) return;
        int count = PendingChangeManager.sizeForDomain(PendingDomain.TAGS);
        if (count == 0) return;
        PendingChangeManager.discardDomain(PendingDomain.TAGS);
        flash(Lang.get("gui.kve.pending.discarded", count));
        refreshSaved();
        refresh();
    }

    private void doOpenChangeHistory() {
        minecraft.setScreen(new KveChangeHistoryScreen(this, KveChangeLog.Log.TAGS,
                Lang.c("gui.kve.change_history.title", Lang.get("gui.kve.change_history.domain.tags")),
                key -> {
                    TagExporter.TagEntry found = TagExporter.getEntry(key);
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
        for (TagExporter.TagEntry e : TagExporter.getEntries()) {
            if (!SearchFilter.matches(e.tagId, searchFilter)) continue;
            savedList.add(e);
        }
        // favoritos arriba, como en el editor de loot
        savedList.sort((a, b) -> {
            boolean fa = CraftEditorState.isFavoriteTag(a.key()), fb = CraftEditorState.isFavoriteTag(b.key());
            return fa == fb ? 0 : fa ? -1 : 1;
        });
        updateSavedScroll();
    }

    private void updateSavedScroll() {
        if (savedScrollBar != null) savedScrollBar.setTotals(savedList.size(), savedRows());
    }

    private void flash(String msg) { flashMsg = msg; flashAt = System.currentTimeMillis(); }

    private Button btn(String label, int x, int y, int w, Runnable a) {
        return KveGuiStyle.smartBtn(x, y, w, label, b -> a.run());
    }

    /**
     * mismo resolveIcon() que
     * {@code TagMembersScreen} (icono null para ENTITY_TYPE o ids que no
     * resuelven - el marco se dibuja igual, vacio), adaptado a
     * {@link #draftType} en vez de un campo {@code regType} propio porque
     * esta pantalla es la que TODAVIA no confirmo el tag (borrador), no
     * hay un tag real cuyos miembros consultar como hace TagMembersScreen.
     */
    private Item resolveIcon(String id) {
        if (draftType == TagExporter.RegistryType.ENTITY_TYPE) return null;
        return draftType == TagExporter.RegistryType.BLOCK ? MiniItemIcon.block(id) : MiniItemIcon.item(id);
    }

    private void drawTagItemFrame(GuiGraphics g, int x, int ry, String id) {
        MiniItemIcon.draw(g, x, ry + 1, TAG_ITEM_ICON, resolveIcon(id));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        {
            int wantListW = fitListW(listResize != null ? listResize.width() : LIST_W, hosted ? hostedW : width - 8);
            boolean wasShown = panelShown;
            int wantW = layoutWidth(wantListW, hosted ? hostedW : width - 8);
            int wantH = hosted ? hostedH : Math.min(GUI_H_DEFAULT, height - 8);
            if (wantW != GUI_W || wantH != GUI_H || wantListW != LIST_W || wasShown != panelShown) refresh();
        }
        if (!hosted) renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, Lang.get("gui.kve.tag.title"));

        int rowH = SAVED_ROW_H;
        int scroll = savedScrollBar.getScroll();
        for (int i = 0; i < Math.min(savedRows(), savedList.size() - scroll); i++) {
            TagExporter.TagEntry e = savedList.get(i + scroll);
            int ry = T + 30 + i * rowH;
            boolean sel = e.key().equals(currentKey);
            boolean hov = !sel && mx >= L + 2 && mx < L + LIST_W - 10 && my >= ry && my < ry + rowH - 2;
            g.fill(L + 2, ry, L + LIST_W - 10, ry + rowH - 2,
                    sel ? KveGuiStyle.BORDER_FIELD : hov ? KveGuiStyle.BORDER_SUBTLE : KveGuiStyle.BG_FIELD);
            // mini vista del tag a la izquierda: va mostrando sus items de a uno, despacio
            MiniItemIcon.draw(g, L + 5, ry + (rowH - 2 - 16) / 2, 16, TagPreview.current(e));
            int tx = L + 26, tw = LIST_W - 48;
            String line1 = (e.replace ? "[R] " : "") + e.tagId;
            KveGuiStyle.drawTextSafe(g, line1, tx, ry + 2, tw, e.enabled ? KveGuiStyle.TEXT_LABEL : KveGuiStyle.th(0xFF667788));
            String summary = e.replace ? ("(" + typeLabel(e.regType) + ") =" + e.added.size() + " valores")
                    : ("(" + typeLabel(e.regType) + ") +" + e.added.size() + " -" + e.removed.size());
            KveGuiStyle.drawTextSafe(g, summary, tx, ry + 12, tw, KveGuiStyle.th(0xFF556677));
            boolean favd = CraftEditorState.isFavoriteTag(e.key());
            boolean hovStar = KveGuiStyle.isHovered(mx, my, L + LIST_W - 22, ry, 12, 10);
            KveGuiStyle.drawTextSafe(g, favd ? "\u2605" : "\u2606", L + LIST_W - 22, ry + 2, 14,
                    favd ? KveGuiStyle.th(0xFFE8C96A) : (hovStar ? 0xFFAAAA77 : KveGuiStyle.th(0xFF556677)));
        }
        savedScrollBar.render(g, mx, my);
        if (savedList.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.tag.empty_list"), L + 4, T + 34, LIST_W - 8, KveGuiStyle.th(0xFF445566));
        }

        if (objectMode) renderObject(g, mx, my); else renderTagLists(g);
        renderPanel(g, mx, my);
        int ax = L + LIST_W + 12;
        if (!flashMsg.isEmpty() && System.currentTimeMillis() - flashAt < 4500) {
            int flashMaxW = L + LIST_W + RIGHT_PANEL_W - 4 - ax;
            KveGuiStyle.drawTextSafe(g, flashMsg, ax, actionTopY - 12, flashMaxW, KveGuiStyle.th(0xFFE8C96A));
        }

        listResize.render(g, mx, my);

        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
    }

    private void renderTagLists(GuiGraphics g) {
        int ax = L + LIST_W + 12;
        int colW = 130;
        int addedX = ax, removedX = ax + colW + 16;

        int live = liveMemberCount();
        String summaryLine = live == LIVE_UNAVAILABLE
                ? previewText()
                : (draftReplace
                        ? Lang.get("gui.kve.tag.summary_line_replace", draftTagId, live, draftAdded.size())
                        : Lang.get("gui.kve.tag.summary_line", draftTagId, live, draftAdded.size(), draftRemoved.size()));
        int summaryMaxW = L + LIST_W + RIGHT_PANEL_W - 4 - ax; // hasta el borde del formulario (a la derecha va el panel)
        KveGuiStyle.drawTextSafe(g, summaryLine, ax, rowTagIdY + 13, summaryMaxW, 0xFF6688AA);

        KveGuiStyle.drawTextSafe(g, draftReplace
                        ? Lang.get("gui.kve.tag.values_header", draftAdded.size())
                        : Lang.get("gui.kve.tag.add_header", draftAdded.size()),
                addedX, rowListsHeaderY, colW, 0xFF88CC88);
        if (!draftReplace) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.tag.remove_header", draftRemoved.size()), removedX, rowListsHeaderY, colW, 0xFFCC8888);
        }

        int addedRowsShown = Math.min(editRows(), Math.max(0, draftAdded.size() - addedScroll));
        for (int i = 0; i < addedRowsShown; i++) {
            String id = draftAdded.get(i + addedScroll);
            int ry = rowListsTop + i * TAG_ITEM_ROW_H;
            drawTagItemFrame(g, addedX, ry, id);
            KveGuiStyle.drawTextSafe(g, id, addedX + TAG_ITEM_ICON + 3, ry + 2, colW - 6 - (TAG_ITEM_ICON + 3), 0xFFAADDAA);
            g.fill(addedX, ry + TAG_ITEM_ROW_H - 1, addedX + colW - 6, ry + TAG_ITEM_ROW_H, KveGuiStyle.BORDER_SUBTLE);
        }
        if (!draftReplace) {
            int removedRowsShown = Math.min(editRows(), Math.max(0, draftRemoved.size() - removedScroll));
            for (int i = 0; i < removedRowsShown; i++) {
                String id = draftRemoved.get(i + removedScroll);
                int ry = rowListsTop + i * TAG_ITEM_ROW_H;
                drawTagItemFrame(g, removedX, ry, id);
                KveGuiStyle.drawTextSafe(g, id, removedX + TAG_ITEM_ICON + 3, ry + 2, colW - 6 - (TAG_ITEM_ICON + 3), 0xFFDDAAAA);
                g.fill(removedX, ry + TAG_ITEM_ROW_H - 1, removedX + colW - 6, ry + TAG_ITEM_ROW_H, KveGuiStyle.BORDER_SUBTLE);
            }
        }
    }

    /**
     * Sentinel returned by {@link #liveMemberCount()} when the live count
     * genuinely can't be resolved (no id typed yet, id not in
     * "namespace:path" form, or no world loaded - tags are server-synced
     * data, same caveat {@link #previewText()} already documented). Not
     * -1, because -1 already means something different downstream in the
     * raw registry lookup below (tag not found -> 0 live, see the
     * {@code Math.max(0, count)} clamp).
     */
    private static final int LIVE_UNAVAILABLE = Integer.MIN_VALUE;

    private int liveMemberCount() {
        if (draftTagId == null || draftTagId.isBlank() || !draftTagId.contains(":")) return LIVE_UNAVAILABLE;
        if (minecraft.level == null) return LIVE_UNAVAILABLE;
        try {
            ResourceLocation id = ResourceLocation.parse(draftTagId);
            var access = minecraft.level.registryAccess();
            int count = switch (draftType) {
                case ITEM -> access.registryOrThrow(net.minecraft.core.registries.Registries.ITEM)
                        .getTag(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, id))
                        .map(s -> (int) s.stream().count()).orElse(-1);
                case BLOCK -> access.registryOrThrow(net.minecraft.core.registries.Registries.BLOCK)
                        .getTag(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, id))
                        .map(s -> (int) s.stream().count()).orElse(-1);
                case ENTITY_TYPE -> access.registryOrThrow(net.minecraft.core.registries.Registries.ENTITY_TYPE)
                        .getTag(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, id))
                        .map(s -> (int) s.stream().count()).orElse(-1);
                case FLUID -> access.registryOrThrow(net.minecraft.core.registries.Registries.FLUID)
                        .getTag(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.FLUID, id))
                        .map(s -> (int) s.stream().count()).orElse(-1);
            };
            return Math.max(0, count);
        } catch (Exception ex) {
            return LIVE_UNAVAILABLE;
        }
    }

    private String previewText() {
        if (draftTagId == null || draftTagId.isBlank() || !draftTagId.contains(":"))
            return Lang.get("gui.kve.tag.preview_hint");
        if (minecraft.level == null)
            return Lang.get("gui.kve.tag.preview_need_world");
        try {
            ResourceLocation id = ResourceLocation.parse(draftTagId);
            var access = minecraft.level.registryAccess();
            int count = switch (draftType) {
                case ITEM -> access.registryOrThrow(net.minecraft.core.registries.Registries.ITEM)
                        .getTag(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, id))
                        .map(s -> (int) s.stream().count()).orElse(-1);
                case BLOCK -> access.registryOrThrow(net.minecraft.core.registries.Registries.BLOCK)
                        .getTag(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, id))
                        .map(s -> (int) s.stream().count()).orElse(-1);
                case ENTITY_TYPE -> access.registryOrThrow(net.minecraft.core.registries.Registries.ENTITY_TYPE)
                        .getTag(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, id))
                        .map(s -> (int) s.stream().count()).orElse(-1);
                case FLUID -> access.registryOrThrow(net.minecraft.core.registries.Registries.FLUID)
                        .getTag(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.FLUID, id))
                        .map(s -> (int) s.stream().count()).orElse(-1);
            };
            return count < 0
                    ? Lang.get("gui.kve.tag.preview_not_exist")
                    : Lang.get("gui.kve.tag.preview_used_by", count, typeLabel(draftType).toLowerCase());
        } catch (Exception ex) {
            return "";
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        // un menu "Mas" abierto recibe el clic antes que lo que tenga debajo
        if (com.kve.client.gui.KveMenuButton.consumeClick(mx, my, btn)) return true;
        if (listResize.mouseClicked(mx, my)) return true;
        if (savedScrollBar.mouseClicked(mx, my, btn)) return true;
        int rowH = SAVED_ROW_H;
        int scroll = savedScrollBar.getScroll();
        for (int i = 0; i < Math.min(savedRows(), savedList.size() - scroll); i++) {
            int ry = T + 30 + i * rowH;
            TagExporter.TagEntry e = savedList.get(i + scroll);
            if (mx >= L + LIST_W - 22 && mx < L + LIST_W - 10 && my >= ry && my < ry + 10) {
                CraftEditorState.toggleFavoriteTag(e.key());
                flash(Lang.get(CraftEditorState.isFavoriteTag(e.key()) ? "gui.kve.loot.favorite_add" : "gui.kve.loot.favorite_remove"));
                refreshSaved();
                return true;
            }
            if (mx >= L + 2 && mx < L + LIST_W - 10 && my >= ry && my < ry + rowH - 2) {
                loadDraft(e);
                return true;
            }
        }
        if (panelClicked(mx, my, btn)) return true;
        if (objectMode && objectRowsClicked(mx, my)) return true;
        return super.mouseClicked(mx, my, btn);
    }

    @Override public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
        if (listResize.mouseDragged(mx)) { refresh(); return true; }
        if (listResize.isDragging()) return true;
        if (savedScrollBar.mouseDragged(mx, my, b, dx, dy)) return true;
        if (panelScrollBar != null && panelScrollBar.mouseDragged(mx, my, b, dx, dy)) return true;
        return super.mouseDragged(mx, my, b, dx, dy);
    }
    @Override public boolean mouseReleased(double mx, double my, int b) {
        listResize.mouseReleased();
        savedScrollBar.mouseReleased(mx, my, b);
        if (panelScrollBar != null) panelScrollBar.mouseReleased(mx, my, b);
        return super.mouseReleased(mx, my, b);
    }
    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        if (objectMode && objectRowsScrolled(mx, my, delta)) return true;
        int ax = L + LIST_W + 12;
        int colW = 130;
        int removedX = ax + colW + 16;
        int addedListBot = rowListsTop + editRows() * TAG_ITEM_ROW_H;
        if (mx >= ax && mx < ax + colW && my >= rowListsTop && my < addedListBot) {
            addedScroll = Math.max(0, Math.min(Math.max(0, draftAdded.size() - editRows()),
                    addedScroll - (int) Math.signum(delta)));
            return true;
        }
        if (mx >= removedX && mx < removedX + colW && my >= rowListsTop && my < addedListBot) {
            removedScroll = Math.max(0, Math.min(Math.max(0, draftRemoved.size() - editRows()),
                    removedScroll - (int) Math.signum(delta)));
            return true;
        }
        if (panelScrollBar != null && mx >= panelX() && mx < panelX() + panelW()
                && my >= panelListTop() && my < panelListTop() + panelRows() * TAG_ITEM_ROW_H) {
            panelScrollBar.mouseScrolled(delta);
            return true;
        }
        int savedListBot = T + 30 + savedRows() * SAVED_ROW_H;
        if (mx >= L && mx < L + LIST_W && my >= T + 30 && my < savedListBot) {
            savedScrollBar.mouseScrolled(delta);
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, delta);
    }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (getFocused() != null && getFocused().keyPressed(key, scan, mods)) return true;
        if ((mods & GLFW.GLFW_MOD_CONTROL) != 0) {
            if (key == GLFW.GLFW_KEY_S) { if (objectMode) saveObject(); else save(); return true; }
            if (objectMode) return EditorKeys.swallow(mods);
            if (key == GLFW.GLFW_KEY_D) { duplicateTag(); return true; }
            if (key == GLFW.GLFW_KEY_Z && !currentKey.isEmpty() && TagExporter.canUndo(currentKey)) { undo(); return true; }
        }
        if (EditorKeys.isFocusTab(key, mods)) { super.keyPressed(key, scan, mods); return true; }
        return EditorKeys.swallow(mods);
    }

    @Override public boolean isPauseScreen() { return false; }
}
