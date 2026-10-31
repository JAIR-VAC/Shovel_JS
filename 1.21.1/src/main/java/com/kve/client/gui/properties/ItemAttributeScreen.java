package com.kve.client.gui.properties;

import com.kve.client.gui.DiffPreviewScreen;
import com.kve.client.gui.EditorKeys;
import com.kve.client.gui.pack.IdUsageScreen;
import com.kve.client.gui.KveConfirmScreen;
import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KveScaledEditBox;
import com.kve.client.gui.ListKeyNav;
import com.kve.client.gui.MiniItemIcon;
import com.kve.client.gui.recipe.RecipePreviewScreen;
import com.kve.client.gui.RegistryIndex;
import com.kve.client.gui.ResizeHandle;
import com.kve.client.gui.ScrollBar;
import com.kve.client.gui.SearchFilter;
import com.kve.client.gui.tag.TagEditorScreen;

import org.lwjgl.glfw.GLFW;
import com.kve.common.Lang;
import com.kve.client.permission.ClientPermissionState;

import com.kve.client.gui.studio.KveStudioShell;
import com.kve.client.gui.studio.KveStudioTab;
import com.kve.client.gui.history.KveChangeLog;
import com.kve.client.gui.history.KveChangeHistoryScreen;
import com.kve.client.gui.workspace.pending.PendingDomain;
import com.kve.client.gui.workspace.pending.PendingChange;
import com.kve.client.gui.workspace.pending.PendingChangeManager;
import com.kve.common.customitem.CustomItemDraft;
import com.kve.common.kubejs.ExternalScriptScanner;
import com.kve.common.kubejs.KubeJSExporter;
import com.kve.common.kubejs.ItemAttrMath;
import com.kve.common.kubejs.KubeJSItemAttrExporter;
import com.kve.common.kubejs.KubeJsItemAttrSupport;
import com.kve.common.kubejs.LootTableExporter;
import com.kve.common.persist.CraftEditorState;
import com.kve.common.recipe.RecipeData;
import com.kve.common.search.ItemUsageResolver;
import com.kve.common.search.ItemUsageResolver.ItemUsage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class ItemAttributeScreen extends Screen implements KveStudioTab {

    private static final int GUI_W  = 500;
    private static final int GUI_H  = 384;
    // 1.4 "Paneles redimensionables con layout
    // persistente": LIST_W (divisor entre la grilla de items y la columna
    // de atributos) es ahora un valor vivo de listResize. Rango angosto
    // (140-190, contra 100-220 en las demas pantallas) porque a diferencia
    // de esas, aca GUI_W NO crece con el drag - es "columna vecina se
    // achica" entre DOS bordes fijos: la grilla de items a la izquierda
    // (COLS*SLOT=126px, ancho fijo de grilla de iconos, no texto) y el
    // panel INFO_W a la derecha. Si LIST_W crece demasiado, la columna de
    // campos (ax = L+LIST_W+100) invade INFO_W; si se achica demasiado,
    // el divisor queda mas a la izquierda que la propia grilla.
    private static final int LIST_W_MIN     = 140;
    private static final int LIST_W_MAX     = 190;
    private static final int LIST_W_DEFAULT = 150;
    private int INFO_W = 110;
    private static final int ROWS   = 12;
    private static final int COLS   = 7;
    private static final int SLOT   = 18;
    private static final int FW     = 80;
    private static final int GAP    = 17;
    private static final int CUSTOM_ROWS_MIN = 2;

    private int visibleCustomRows(int listTop) {
        int bottom = T + effH() - 24;
        return Math.max(CUSTOM_ROWS_MIN, Math.min(5, (bottom - listTop) / 12));
    }

    private int customContentRows(int listTop) {
        int capacity = visibleCustomRows(listTop);
        return draftAttributes.size() > capacity ? Math.max(1, capacity - 1) : capacity;
    }

    private int customListTop() {
        KveGuiStyle.Layout ly = new KveGuiStyle.Layout(0, T + 16, GAP);
        ly.row(); ly.row(); ly.row(); ly.row(); ly.row();
        ly.gap(4);
        ly.row(); ly.row(); ly.row(); ly.row(); ly.row();
        ly.gap(4);
        ly.gap(GAP);
        ly.gap(KveGuiStyle.BTN_H);
        return ly.y();
    }

    private static final String[] RARITIES = {"", "COMMON", "UNCOMMON", "RARE", "EPIC"};

    private int L, T, LIST_W;
    private ResizeHandle listResize;

    private final List<Item> allItems = new ArrayList<>();
    private final List<Item> filtered = new ArrayList<>();
    private ScrollBar itemScrollBar;
    private EditBox   searchBox;
    private String    searchFilter = "";
    private final ListKeyNav nav = new ListKeyNav();

    private Item selectedItem = null;

    private int    draftMaxDamage    = -1;
    private int    draftMaxStack     = -1;
    private int    draftBurnTime     = -1;
    private double draftAtkDmg       = Double.NaN;
    private double draftAtkSpd       = Double.NaN;
    private double draftArmor        = Double.NaN;
    private double draftArmorTough   = Double.NaN;
    private double draftMoveSpd      = Double.NaN;
    private KubeJSItemAttrExporter.Op draftAtkDmgOp     = KubeJSItemAttrExporter.Op.ADDITION;
    private KubeJSItemAttrExporter.Op draftAtkSpdOp     = KubeJSItemAttrExporter.Op.ADDITION;
    private KubeJSItemAttrExporter.Op draftArmorOp      = KubeJSItemAttrExporter.Op.ADDITION;
    private KubeJSItemAttrExporter.Op draftArmorToughOp = KubeJSItemAttrExporter.Op.ADDITION;
    private KubeJSItemAttrExporter.Op draftMoveSpdOp    = KubeJSItemAttrExporter.Op.ADDITION;
    private boolean draftFire        = false;
    private int    draftRarityIdx    = 0;
    private boolean draftEnabled     = true;
    private String  draftCategory    = "";
    private boolean draftHideCreative = false;
    private boolean draftHideViewers  = false;
    private boolean draftHideLoot = false;
    private boolean readOnlyMode     = false;
    private boolean showMoreActions  = false;
    private String  currentItemId    = "";

    private int     vanillaMaxDamage      = -1;
    private int     vanillaMaxStack       = 64;
    private double  vanillaAtkDmg         = Double.NaN;
    private double  vanillaAtkSpd         = Double.NaN;
    private double  vanillaArmor          = Double.NaN;
    private double  vanillaArmorTough     = Double.NaN;
    private double  vanillaMoveSpd        = Double.NaN;
    private boolean vanillaFireResistant  = false;
    private KubeJSItemAttrExporter.FoodProps vanillaFoodBaseline = null;
    private KubeJSItemAttrExporter.Op vanillaAtkDmgOp     = KubeJSItemAttrExporter.Op.ADDITION;
    private KubeJSItemAttrExporter.Op vanillaAtkSpdOp     = KubeJSItemAttrExporter.Op.ADDITION;
    private KubeJSItemAttrExporter.Op vanillaArmorOp      = KubeJSItemAttrExporter.Op.ADDITION;
    private KubeJSItemAttrExporter.Op vanillaArmorToughOp = KubeJSItemAttrExporter.Op.ADDITION;
    private KubeJSItemAttrExporter.Op vanillaMoveSpdOp    = KubeJSItemAttrExporter.Op.ADDITION;

    private final java.util.Map<String, KubeJSItemAttrExporter.AttrMod> draftAttributes = new java.util.LinkedHashMap<>();
    /**
     * Ids de atributos POR DEFECTO del item (los sembrados desde
     * {@link #vanillaExtraAttributes}) que el jugador borro con la "X" - ver
     * {@link KubeJSItemAttrExporter.ItemEntry#removedAttributes}. Sin esto
     * borrar una fila vanilla no exportaba nada (el modifier seguia en el
     * juego) y la fila reaparecia al reabrir el item.
     */
    private final java.util.Set<String> draftRemovedAttrs = new java.util.LinkedHashSet<>();
    private boolean selectedSupportsAttrs = true;
    private final java.util.Map<String, KubeJSItemAttrExporter.AttrMod> vanillaExtraAttributes = new java.util.LinkedHashMap<>();
    private int customAttrScroll = 0;
    private KubeJSItemAttrExporter.FoodProps draftFood = null;
    private java.util.List<String> draftTooltip = new java.util.ArrayList<>();
    private KubeJSItemAttrExporter.CombatProps draftCombat = null;
    private final java.util.Set<String> draftAddMode = new java.util.LinkedHashSet<>();
    private final java.util.List<java.util.Map.Entry<String, int[]>> modeHitboxes = new java.util.ArrayList<>();
    private static final String ATK_DMG_ID = "minecraft:generic.attack_damage";
    private static final String ATK_SPD_ID = "minecraft:generic.attack_speed";
    private static final String ARMOR_ID = "minecraft:generic.armor";
    private static final String ARMOR_TOUGH_ID = "minecraft:generic.armor_toughness";
    private static final String MOVE_SPD_ID = "minecraft:generic.movement_speed";
    private final java.util.LinkedHashMap<String, KubeJSItemAttrExporter.SlotAttrMod> draftCurio = new java.util.LinkedHashMap<>();
    private final java.util.LinkedHashMap<String, KubeJSItemAttrExporter.SlotAttrMod> vanillaCurio = new java.util.LinkedHashMap<>();

    private EditBox fMaxDamage, fMaxStack, fBurnTime;
    private EditBox fAtkDmg, fAtkSpd, fArmor, fArmorTough, fMoveSpd;

    private final List<KubeJSItemAttrExporter.ItemEntry> savedList = new ArrayList<>();
    private int savedScroll = 0;
    private ScrollBar savedScrollBar;
    private String flashMsg = "";
    private long   flashAt  = 0;

    private String externalSourceNote = "";

    private Screen returnScreen = null;

    private boolean hosted = false;
    private int hostedW = 0;
    private int hostedH = 0;

    // geometria del
    // panel derecho (render + label + botones reubicados), calculada UNA
    // vez por buildAll() y reusada por drawInfoPanel()/mouseClicked() -
    // mismo motivo que TradeEditorScreen's tradeHintY/etc. en de
    // botones (§11): evita 2 formulas independientes (una para construir
    // los widgets, otra para dibujar/hit-test) que se puedan desincronizar.
    private int infoIconSize = 32;
    private int infoButtonsTopY = 0;
    private int infoButtonsBottomY = 0;

    private int effH() { return hosted ? Math.max(200, hostedH) : GUI_H; }

    private int effW() { return hosted ? Math.max(400, hostedW) : GUI_W; }

    private int customRowRight() { return (L + effW() - INFO_W) - 4; }
    private int customEditW() { return KveGuiStyle.btnWidth(Lang.get("gui.kve.btn.edit")); }
    private int customEditX() { return customRowRight() - 14 - KveGuiStyle.BTN_GAP - customEditW(); }

    private Screen ownerTab = null;

    /** Fluent setter - ver {@link #ownerTab}. Llamado por {@code PropertiesTabScreen} en cada {@code layout()}. */
    public ItemAttributeScreen withOwnerTab(Screen s) {
        this.ownerTab = s;
        return this;
    }

    private enum ItemCreatorMode { NONE, LIST, EDITOR }
    private ItemCreatorMode itemCreatorMode = ItemCreatorMode.NONE;
    private CustomItemCreatorListScreen itemCreatorList;
    private CustomItemCreatorEditorScreen itemCreatorEditor;

    private Screen itemCreatorOwnerTab() { return ownerTab != null ? ownerTab : this; }

    private void openItemCreator() {
        if (itemCreatorList == null) {
            itemCreatorList = new CustomItemCreatorListScreen(this::closeItemCreator, this::openItemCreatorEditor);
        }
        itemCreatorMode = ItemCreatorMode.LIST;
        itemCreatorList.init(L, T, effW(), effH());
    }

    private void openItemCreatorEditor(CustomItemDraft draftOrNull) {
        itemCreatorEditor = new CustomItemCreatorEditorScreen(this::backToItemCreatorList, draftOrNull);
        itemCreatorEditor.withOwnerTab(itemCreatorOwnerTab());
        itemCreatorEditor.withReturnScreen(returnScreen);
        itemCreatorMode = ItemCreatorMode.EDITOR;
        itemCreatorEditor.init(L, T, effW(), effH());
    }

    private void backToItemCreatorList() {
        itemCreatorMode = ItemCreatorMode.LIST;
        itemCreatorList.refreshOnReturn();
    }

    private void closeItemCreator() {
        itemCreatorMode = ItemCreatorMode.NONE;
    }

    private Screen activeItemCreator() {
        return switch (itemCreatorMode) {
            case LIST -> itemCreatorList;
            case EDITOR -> itemCreatorEditor;
            case NONE -> null;
        };
    }

    private final String initialId;
    /** True once the {@link #initialId} auto-select has run - init() re-runs on every draft edit
     *  (toggle buttons etc.) and must NOT re-select (and so reset the in-progress draft) each time. */
    private boolean initialIdApplied = false;

    public ItemAttributeScreen() {
        this(null);
    }

    /** @param initialId namespace:path of the item to auto-select on open, or null/blank for a blank list. */
    public ItemAttributeScreen(String initialId) {
        super(Lang.c("gui.kve.main.items"));
        this.initialId = initialId;
    }

    public ItemAttributeScreen withReturnScreen(Screen s) {
        this.returnScreen = s;
        return this;
    }

    @Override
    protected void init() {
        // ("Properties" tab - mismo patron que
        // CraftEditorScreen/TagEditorScreen, ver KveStudioShell#reclaimIfHosted()
        // Javadoc): si esto se esta reabriendo porque un picker interno
        // (ItemPicker/AttributePicker/ItemFoodScreen/TagEditorScreen/
        // IdUsageScreen, etc.) hizo minecraft.setScreen(this) para "volver"
        // y esta tab sigue siendo la tab activa de un KveStudioShell vivo,
        // reabrir el shell en vez de reconstruirse standalone/centrada.
        // Overload owner-aware (ver #ownerTab): mientras embebida dentro de
        // PropertiesTabScreen, la instancia registrada como screenRef del
        // shell es esa (ownerTab), no this - sin este 3er argumento, el
        // check de "tab activa" comparaba this contra screenRef y siempre
        // fallaba, tirando el shell entero cada vez que un picker interno
        // volvia.
        if (KveStudioShell.reclaimIfHosted(this, ownerTab != null ? ownerTab : this, returnScreen)) return;
        hosted = false;
        L = (width  - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        buildAll();
    }

    @Override
    public void init(int x0, int y0, int w, int h) {
        // KveStudioShell nunca pasa esta tab por Minecraft#setScreen()
        // mientras esta hosteada (ver KveStudioShell#reclaimIfHosted()
        // Javadoc, hueco #1) - por lo tanto Screen#init(Minecraft, int,
        // int), el unico lugar donde vanilla llena minecraft/font, nunca
        // corre para esta instancia. Sin este bootstrap, buildAll()
        if (this.minecraft == null) {
            this.minecraft = Minecraft.getInstance();
            this.font = this.minecraft.font;
        }
        hosted = true;
        hostedW = w;
        hostedH = h;
        L = x0;
        T = y0;
        buildAll();
    }

    private void refresh() {
        buildAll();
    }

    private void buildAll() {
        if (listResize == null) {
            listResize = new ResizeHandle("item_attr.list", LIST_W_MIN, LIST_W_MAX, LIST_W_DEFAULT);
        }
        LIST_W = listResize.width();
        INFO_W = hosted ? Math.min(200, Math.max(160, effW() * 3 / 10)) : 110;
        listResize.reposition(L, T + 14, T + effH());
        clearWidgets();
        setFocused(null);
        CraftEditorState.loadIfNeeded();

        if (!initialIdApplied) {
            initialIdApplied = true;
            if (initialId != null && !initialId.isEmpty()) {
                try {
                    ResourceLocation rl = ResourceLocation.parse(initialId);
                    Item item = BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
                    if (item != null) selectItem(item);
                } catch (Exception ignored) {}
            }
        }

        searchBox = new KveScaledEditBox(font, L + 2, T + 16, LIST_W - 4, 11,
                Component.literal(Lang.get("gui.kve.search.item")));
        searchBox.setMaxLength(48);
        searchBox.setHint(Component.literal(Lang.get("gui.kve.search.item")));
        searchBox.setValue(searchFilter);
        searchBox.setResponder(s -> {
            searchFilter = s;
            rebuildFilter();
            itemScrollBar.setTotals((int) Math.ceil(filtered.size() / (double) COLS), ROWS);
            nav.reset();
        });
        addRenderableWidget(searchBox);

        int barX = L + 2 + COLS * SLOT + 2;
        if (itemScrollBar == null) {
            itemScrollBar = new ScrollBar(barX, T + 30, ROWS * SLOT, 0, ROWS);
        } else {
            itemScrollBar.setPosition(barX, T + 30);
        }

        int savedRows = 8;
        if (savedScrollBar == null) {
            savedScrollBar = new ScrollBar(L + effW() - 10, T + 28, savedRows * 14, 0, savedRows);
        } else {
            savedScrollBar.setPosition(L + effW() - 10, T + 28);
        }

        int ax = Math.min(L + LIST_W + 100, L + effW() - INFO_W - FW - 14);
        KveGuiStyle.Layout ly = new KveGuiStyle.Layout(ax, T + 16, GAP);

        fMaxDamage  = field(ly.x(), ly.row());
        fMaxStack   = field(ly.x(), ly.row());
        fBurnTime   = field(ly.x(), ly.row());

        String rarLabel = draftRarityIdx > 0 ? RARITIES[draftRarityIdx] : Lang.get("gui.kve.item.no_change");
        addRenderableWidget(btn(Lang.get("gui.kve.item.rarity") + " " + rarLabel, ly.x(), ly.row(), FW + 10, this::cycleRarity));

        addRenderableWidget(btn(Lang.get("gui.kve.item.fire_resistant") + " "
                + Lang.get(draftFire ? "gui.kve.item.fire_yes" : "gui.kve.item.fire_no"),
                ly.x(), ly.row(), FW + 10, this::toggleFire));

        ly.gap(4);
        fAtkDmg     = field(ly.x(), ly.row());
        fAtkSpd     = field(ly.x(), ly.row());
        fArmor      = field(ly.x(), ly.row());
        fArmorTough = field(ly.x(), ly.row());
        fMoveSpd    = field(ly.x(), ly.row());
        ly.gap(4);

        ly.gap(GAP);
        addRenderableWidget(btn("+ " + Lang.get("gui.kve.attr.title"), ly.x(), ly.y(), FW + 10, this::openAttributePicker));
        ly.gap(KveGuiStyle.BTN_H);
        int customListTop = ly.y();
        int customRowsVisible = customContentRows(customListTop);
        for (int i = 0; i < customRowsVisible; i++) {
            int idx = i + customAttrScroll;
            if (idx >= draftAttributes.size()) break;
            var entryList = new ArrayList<>(draftAttributes.values());
            KubeJSItemAttrExporter.AttrMod m = entryList.get(idx);
            int ry = customListTop + i * 12;
            addRenderableWidget(btn(Lang.get("gui.kve.btn.edit"), customEditX(), ry, customEditW(), () -> editCustomAttribute(m)));
            addRenderableWidget(btn("X", customRowRight() - 14, ry, 14, () -> removeCustomAttribute(m.attrId)));
        }

        applyDraftToFields();

        int rx = L + effW() - INFO_W + 4;
        int panelRight = L + effW() - 6;
        infoButtonsTopY = T + 16 + infoIconSize + 10;

        KveGuiStyle.ButtonRow relocated = new KveGuiStyle.ButtonRow(rx, infoButtonsTopY, panelRight);

        int pendCount = PendingChangeManager.size();
        if (!hosted) {
            String applyL = pendCount > 0 ? Lang.get("gui.kve.pending.apply") + " (" + pendCount + ")" : Lang.get("gui.kve.pending.apply");
            String discardL = pendCount > 0 ? Lang.get("gui.kve.pending.discard") + " (" + pendCount + ")" : Lang.get("gui.kve.pending.discard");
            Button applyPendBtn = addRenderableWidget(relocated.add(0, applyL, b -> onApplyPending()));
            applyPendBtn.active = pendCount > 0 && ClientPermissionState.canApply();
            Button discardPendBtn = addRenderableWidget(relocated.add(0, discardL, b -> onDiscardPending()));
            discardPendBtn.active = pendCount > 0;
        }

        // a la vista: Guardar, "Mas" y Eliminar; debajo, los editores de contenido con su nombre y su cuenta
        KveGuiStyle.ActionBar bar = new KveGuiStyle.ActionBar(relocated, this::addRenderableWidget);
        bar.primary(Lang.get("gui.kve.btn.save"), b -> saveItem());
        bar.button(Lang.get("gui.kve.btn.preview"), b -> previewItem());
        bar.button(Lang.get("gui.kve.btn.duplicate"), b -> duplicateItem());
        bar.more(Lang.get("gui.kve.btn.remove_usages"), () -> openItemRemoval(), !currentItemId.isEmpty());
        if (ownerTab == null) bar.more(Lang.get("gui.kve.itemcreator.btn"), () -> openItemCreator(), ClientPermissionState.canEdit());
        bar.enabled(draftEnabled, b -> toggleEnabled());
        bar.state(Lang.get("gui.kve.item.readonly") + ": " + Lang.get(readOnlyMode ? "gui.kve.item.fire_yes" : "gui.kve.item.fire_no"), () -> toggleReadOnly());
        bar.history(Lang.get("gui.kve.btn.undo"), () -> undoItem(), !currentItemId.isEmpty() && KubeJSItemAttrExporter.canUndo(currentItemId));
        bar.history(Lang.get("gui.kve.btn.reset"), () -> resetDraft());
        bar.end(() -> deleteItem());

        selectedIsCurio = selectedItem != null && com.kve.common.itemrules.CurioAttributeEnforcer.isCurio(selectedItem);
        moreHeaders.clear();
        moreHeader(relocated, "gui.kve.more.edit", rx, panelRight);
        String foodLabel = draftFood == null
                ? Lang.get("gui.kve.item.food.button")
                : Lang.get("gui.kve.item.food.button") + " (" + draftFood.nutrition + "/" + fmt(draftFood.saturationMod) + ")";
        addRenderableWidget(relocated.add(0, foodLabel, b -> openFoodEditor()));
        String tooltipLabel = draftTooltip.isEmpty()
                ? Lang.get("gui.kve.item.tooltip.btn")
                : Lang.get("gui.kve.item.tooltip.btn") + " (" + draftTooltip.size() + ")";
        addRenderableWidget(relocated.add(0, tooltipLabel, b -> openTooltipEditor()));
        int combatCount = draftCombat == null ? 0 : combatFeatureCount(draftCombat);
        String combatLabel = combatCount == 0
                ? Lang.get("gui.kve.item.combat.btn")
                : Lang.get("gui.kve.item.combat.btn") + " (" + combatCount + ")";
        addRenderableWidget(relocated.add(0, combatLabel, b -> openCombatEditor()));
        if (selectedIsCurio || com.kve.common.itemrules.CurioAttributeEnforcer.available()) addCurioButton(relocated);
        addRenderableWidget(relocated.add(0, Lang.get("gui.kve.item.hide_options.btn"), b -> openHideOptions()));
        if (!hosted) {
            Button pendBtn = addRenderableWidget(relocated.add(0,
                    Lang.get("gui.kve.pending.header", pendCount),
                    b -> Minecraft.getInstance().setScreen(new DiffPreviewScreen(this,
                            PendingChangeManager.snapshot(), () -> com.kve.client.gui.KveToast.applied(PendingChangeManager.applyAll())))));
            pendBtn.active = pendCount > 0;
        }

        infoButtonsBottomY = relocated.bottomY();
        // la lista de modificados baja hasta el pie del panel; se rehace porque su alto depende de los botones de arriba
        int keepSavedScroll = savedScrollBar != null ? savedScrollBar.getScroll() : 0;
        savedScrollBar = new ScrollBar(L + effW() - 10, infoButtonsBottomY + 19, savedRowsVisible() * 14, savedList.size(), savedRowsVisible());
        savedScrollBar.setScroll(keepSavedScroll);

        if (!currentItemId.isEmpty()) {
            addRenderableWidget(btn(
                    CraftEditorState.isFavoriteItem(currentItemId) ? "\u2605" : "\u2606",
                    L + effW() - 17, T + 16, 0, this::toggleFavoriteCurrent));
        }

        buildItemList();
        rebuildFilter();
        int rows = (int) Math.ceil(Math.max(1, filtered.size()) / (double) COLS);
        itemScrollBar.setTotals(rows, ROWS);
        refreshSaved();
    }

    private EditBox field(int x, int y) {
        EditBox b = new KveScaledEditBox(font, x, y, FW, 11, Component.empty());
        b.setMaxLength(12);
        b.setHint(Component.literal(Lang.get("gui.kve.item.no_change")));
        b.setEditable(true);
        addRenderableWidget(b);
        return b;
    }

    private Button btn(String label, int x, int y, int w, Runnable a) {
        return KveGuiStyle.smartBtn(x, y, w, label, b -> a.run());
    }

    private void buildItemList() {
        if (!allItems.isEmpty()) return;
        allItems.addAll(RegistryIndex.items());
        rebuildFilter();
    }

    private String filteredForQuery = null;
    private int filteredForFavEpoch = -1;

    private void rebuildFilter() {
        if (searchFilter.equals(filteredForQuery)
                && filteredForFavEpoch == CraftEditorState.favoritesEpoch()) {
            return;
        }
        filteredForQuery = searchFilter;
        filteredForFavEpoch = CraftEditorState.favoritesEpoch();

        filtered.clear();
        SearchFilter.Query q = SearchFilter.compile(searchFilter);
        for (Item item : allItems) {
            if (q.matches(RegistryIndex.id(item), RegistryIndex.name(item)))
                filtered.add(item);
        }
        filtered.sort((a, b) -> {
            boolean fa = isFavorite(a), fb = isFavorite(b);
            if (fa != fb) return fa ? -1 : 1;
            return 0;
        });
    }

    private boolean isFavorite(Item item) {
        String id = RegistryIndex.id(item);
        return !id.isEmpty() && CraftEditorState.isFavoriteItem(id);
    }

    private void clearDraft() {
        draftMaxDamage  = -1;
        draftMaxStack   = -1;
        draftBurnTime   = -1;
        draftAtkDmg     = Double.NaN;
        draftAtkSpd     = Double.NaN;
        draftArmor      = Double.NaN;
        draftArmorTough = Double.NaN;
        draftMoveSpd    = Double.NaN;
        draftAtkDmgOp     = KubeJSItemAttrExporter.Op.ADDITION;
        draftAtkSpdOp     = KubeJSItemAttrExporter.Op.ADDITION;
        draftArmorOp      = KubeJSItemAttrExporter.Op.ADDITION;
        draftArmorToughOp = KubeJSItemAttrExporter.Op.ADDITION;
        draftMoveSpdOp    = KubeJSItemAttrExporter.Op.ADDITION;
        draftFire       = false;
        draftRarityIdx  = 0;
        draftEnabled    = true;
        draftCategory   = "";
        draftHideCreative = false;
        draftHideViewers  = false;
        draftAttributes.clear();
        draftRemovedAttrs.clear();
        draftAddMode.clear();
        customAttrScroll = 0;
        draftFood = null;
        draftTooltip = new java.util.ArrayList<>();
        draftCombat = null;
        draftCurio.clear();
    }

    private void loadEntryToDraft(KubeJSItemAttrExporter.ItemEntry e) {
        draftMaxDamage  = e.maxDamage >= 0    ? e.maxDamage    : vanillaMaxDamage;
        draftMaxStack   = e.maxStackSize > 0  ? e.maxStackSize : vanillaMaxStack;
        draftBurnTime   = e.burnTime;
        draftAtkDmg     = !Double.isNaN(e.attackDamage)   ? e.attackDamage   : vanillaAtkDmg;
        draftAtkSpd     = !Double.isNaN(e.attackSpeed)    ? e.attackSpeed    : vanillaAtkSpd;
        draftArmor      = !Double.isNaN(e.armor)          ? e.armor          : vanillaArmor;
        draftArmorTough = !Double.isNaN(e.armorToughness) ? e.armorToughness : vanillaArmorTough;
        draftMoveSpd    = !Double.isNaN(e.movementSpeed)  ? e.movementSpeed  : vanillaMoveSpd;
        draftAtkDmgOp     = e.attackDamageOp;
        draftAtkSpdOp     = e.attackSpeedOp;
        draftArmorOp      = e.armorOp;
        draftArmorToughOp = e.armorToughnessOp;
        draftMoveSpdOp    = e.movementSpeedOp;
        draftFire       = e.fireResistant;
        draftEnabled    = e.enabled;
        draftCategory   = e.category != null ? e.category : "";
        draftHideCreative = e.hideFromCreativeTab;
        draftHideViewers  = e.hideFromRecipeViewers;
        draftHideLoot     = e.hideFromLoot;
        draftRarityIdx  = 0;
        if (e.rarity != null && !e.rarity.isEmpty()) {
            for (int i = 1; i < RARITIES.length; i++) {
                if (RARITIES[i].equals(e.rarity)) { draftRarityIdx = i; break; }
            }
        }
        draftAttributes.clear();
        draftAttributes.putAll(e.attributes);
        draftRemovedAttrs.clear();
        draftRemovedAttrs.addAll(e.removedAttributes);
        draftAddMode.clear();
        draftAddMode.addAll(e.addModeAttributes);
        for (var entry : vanillaExtraAttributes.entrySet()) {
            // Los que el jugador borro a proposito NO se vuelven a sembrar.
            if (draftRemovedAttrs.contains(entry.getKey())) continue;
            draftAttributes.putIfAbsent(entry.getKey(), entry.getValue().copy());
        }
        customAttrScroll = 0;
        draftFood = e.food != null ? e.food.copy() : null;
        draftTooltip = e.tooltip != null ? new java.util.ArrayList<>(e.tooltip) : new java.util.ArrayList<>();
        draftCombat = e.combat != null ? e.combat.copy() : null;
        draftCurio.clear();
        for (KubeJSItemAttrExporter.SlotAttrMod m : e.curioAttributes) draftCurio.put(m.key(), m.copy());
        for (var entry : vanillaCurio.entrySet()) {
            if (e.curioRemoved.contains(entry.getKey())) continue;
            draftCurio.putIfAbsent(entry.getKey(), entry.getValue().copy());
        }
    }

    private void loadVanillaToDraft(Item item) {
        clearDraft();
        computeVanillaBaseline(item);
        if (vanillaMaxDamage > 0) draftMaxDamage = vanillaMaxDamage;
        draftMaxStack   = vanillaMaxStack;
        draftAtkDmg     = vanillaAtkDmg;
        draftAtkSpd     = vanillaAtkSpd;
        draftArmor      = vanillaArmor;
        draftArmorTough = vanillaArmorTough;
        draftMoveSpd    = vanillaMoveSpd;
        draftAtkDmgOp     = vanillaAtkDmgOp;
        draftAtkSpdOp     = vanillaAtkSpdOp;
        draftArmorOp      = vanillaArmorOp;
        draftArmorToughOp = vanillaArmorToughOp;
        draftMoveSpdOp    = vanillaMoveSpdOp;
        draftFire = vanillaFireResistant;
        for (var entry : vanillaExtraAttributes.entrySet()) {
            draftAttributes.put(entry.getKey(), entry.getValue().copy());
        }
        for (var entry : vanillaCurio.entrySet()) {
            draftCurio.put(entry.getKey(), entry.getValue().copy());
        }

        net.minecraft.world.food.FoodProperties vanillaFood = new ItemStack(item).get(net.minecraft.core.component.DataComponents.FOOD);
        if (vanillaFood != null) {
            KubeJSItemAttrExporter.FoodProps fp = new KubeJSItemAttrExporter.FoodProps();
            fp.nutrition     = vanillaFood.nutrition();
            fp.saturationMod = vanillaFood.nutrition() > 0 ? vanillaFood.saturation() / (vanillaFood.nutrition() * 2f) : 0f;
            fp.alwaysEdible  = vanillaFood.canAlwaysEat();
            fp.fastFood      = vanillaFood.eatSeconds() < 1.6f;
            fp.meat          = new ItemStack(item).is(net.minecraft.tags.ItemTags.MEAT);
            for (var pe : vanillaFood.effects()) {
                var inst = pe.effect();
                ResourceLocation ek = inst.getEffect().unwrapKey().map(k -> k.location()).orElse(null);
                if (ek == null) continue;
                fp.effects.add(new KubeJSItemAttrExporter.FoodEffect(
                        ek.toString(), inst.getDuration(), inst.getAmplifier(), pe.probability()));
            }
            // vanillaFoodBaseline queda como snapshot para que
            // buildDraftItemEntry() pueda comparar mas tarde y no
            // re-exportar la comida real del item si nunca se toco.
            vanillaFoodBaseline = fp;
            draftFood = fp.copy();
        }
    }

    /** True si dos FoodProps representan el mismo valor (mismos campos + misma lista de efectos en el mismo orden) - usado para no re-exportar la comida vanilla del item si el jugador nunca la toco. */
    private static boolean foodEquals(KubeJSItemAttrExporter.FoodProps a, KubeJSItemAttrExporter.FoodProps b) {
        if (a == null || b == null) return a == b;
        if (a.nutrition != b.nutrition || a.saturationMod != b.saturationMod
                || a.alwaysEdible != b.alwaysEdible || a.fastFood != b.fastFood || a.meat != b.meat) return false;
        if (a.effects.size() != b.effects.size()) return false;
        for (int i = 0; i < a.effects.size(); i++) {
            var ea = a.effects.get(i); var eb = b.effects.get(i);
            if (!java.util.Objects.equals(ea.effectId, eb.effectId) || ea.durationTicks != eb.durationTicks
                    || ea.amplifier != eb.amplifier || ea.chance != eb.chance) return false;
        }
        return true;
    }

    private void computeVanillaBaseline(Item item) {
        ItemStack proto = new ItemStack(item);
        vanillaMaxDamage = proto.getMaxDamage() > 0 ? proto.getMaxDamage() : -1;
        vanillaMaxStack  = proto.getMaxStackSize();
        vanillaAtkDmg = vanillaAtkSpd = vanillaArmor = vanillaArmorTough = vanillaMoveSpd = Double.NaN;
        vanillaAtkDmgOp = vanillaAtkSpdOp = vanillaArmorOp = vanillaArmorToughOp = vanillaMoveSpdOp
                = KubeJSItemAttrExporter.Op.ADDITION;
        vanillaExtraAttributes.clear();
        vanillaCurio.clear();
        for (KubeJSItemAttrExporter.SlotAttrMod m : com.kve.common.itemrules.CurioAttributeEnforcer.defaultModifiers(item)) {
            vanillaCurio.put(m.key(), m);
        }
        vanillaFoodBaseline = null;

        ItemStack stack = new ItemStack(item);
        {
            var defaults = stack.getOrDefault(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,
                    net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY);
            if (defaults.modifiers().isEmpty()) defaults = item.getDefaultAttributeModifiers(stack);
            defaults.modifiers().forEach(entry -> {
                var attr = entry.attribute();
                var mod = entry.modifier();
                ResourceLocation ak = attr.unwrapKey().map(k -> k.location()).orElse(null);
                if (ak == null) return;
                String key = ak.toString();
                double val = mod.amount();
                KubeJSItemAttrExporter.Op realOp = KubeJSItemAttrExporter.Op.ADDITION;
                realOp = KubeJSItemAttrExporter.Op.fromVanilla(mod.operation());
                if      ("minecraft:generic.attack_damage".equals(key)   && Double.isNaN(vanillaAtkDmg))     { vanillaAtkDmg     = val; vanillaAtkDmgOp     = realOp; }
                else if ("minecraft:generic.attack_speed".equals(key)    && Double.isNaN(vanillaAtkSpd))     { vanillaAtkSpd     = val; vanillaAtkSpdOp     = realOp; }
                else if ("minecraft:generic.armor_toughness".equals(key) && Double.isNaN(vanillaArmorTough)) { vanillaArmorTough = val; vanillaArmorToughOp = realOp; }
                else if ("minecraft:generic.armor".equals(key)           && Double.isNaN(vanillaArmor))      { vanillaArmor      = val; vanillaArmorOp      = realOp; }
                else if ("minecraft:generic.movement_speed".equals(key)  && Double.isNaN(vanillaMoveSpd))    { vanillaMoveSpd    = val; vanillaMoveSpdOp    = realOp; }
                else if (!vanillaExtraAttributes.containsKey(key)) {
                    vanillaExtraAttributes.put(key, new KubeJSItemAttrExporter.AttrMod(key, val, realOp));
                }
            });
        }

        boolean fireR = false;
        try { fireR = new ItemStack(item).has(net.minecraft.core.component.DataComponents.FIRE_RESISTANT); } catch (Throwable ignored) {}
        vanillaFireResistant = fireR;
    }

    private boolean overlayExternalItemStats(String itemId) {
        ExternalScriptScanner.ExternalItemStats ext = ExternalScriptScanner.getItemStats(itemId);
        if (ext == null) return false;

        if (ext.maxDamage      >= 0) draftMaxDamage  = ext.maxDamage;
        if (ext.maxStackSize   >= 0) draftMaxStack   = ext.maxStackSize;
        if (ext.burnTime       >= 0) draftBurnTime   = ext.burnTime;
        if (!Double.isNaN(ext.attackDamage)) draftAtkDmg     = ext.attackDamage;
        if (!Double.isNaN(ext.attackSpeed))  draftAtkSpd     = ext.attackSpeed;
        if (ext.fireResistantSet)    draftFire       = ext.fireResistant;
        if (!ext.rarity.isEmpty()) {
            for (int i = 1; i < RARITIES.length; i++)
                if (RARITIES[i].equals(ext.rarity)) { draftRarityIdx = i; break; }
        }
        for (var entry : ext.attributes.entrySet()) {
            String key = entry.getKey();
            KubeJSItemAttrExporter.AttrMod mod = entry.getValue();
            if (key.equals("minecraft:generic.armor"))            draftArmor      = mod.value;
            else if (key.equals("minecraft:generic.armor_toughness")) draftArmorTough = mod.value;
            else if (key.equals("minecraft:generic.movement_speed"))  draftMoveSpd    = mod.value;
            else draftAttributes.put(key, mod);
        }
        externalSourceNote = ext.sourceFile;
        return true;
    }

    private void applyDraftToFields() {
        setF(fMaxDamage,  draftMaxDamage  > 0  ? String.valueOf(draftMaxDamage)  : "");
        setF(fMaxStack,   draftMaxStack   > 0  ? String.valueOf(draftMaxStack)   : "");
        setF(fBurnTime,   draftBurnTime   > 0  ? String.valueOf(draftBurnTime)   : "");
        setF(fAtkDmg,     !Double.isNaN(draftAtkDmg)     ? fmt(toField(draftAtkDmg, draftAtkDmgOp, ATTACK_DAMAGE_BASE, ATK_DMG_ID)) : "");
        setF(fAtkSpd,     !Double.isNaN(draftAtkSpd)     ? fmt(toField(draftAtkSpd, draftAtkSpdOp, ATTACK_SPEED_BASE, ATK_SPD_ID))   : "");
        setF(fArmor,      !Double.isNaN(draftArmor)      ? fmt(draftArmor)       : "");
        setF(fArmorTough, !Double.isNaN(draftArmorTough) ? fmt(draftArmorTough)  : "");
        setF(fMoveSpd,    !Double.isNaN(draftMoveSpd)    ? fmt(toField(draftMoveSpd, draftMoveSpdOp, MOVEMENT_SPEED_BASE, MOVE_SPD_ID)) : "");
    }

    private void syncFieldsToDraft() {
        draftMaxDamage  = parseInt(fMaxDamage);
        draftMaxStack   = parseInt(fMaxStack);
        draftBurnTime   = parseInt(fBurnTime);
        draftAtkDmg     = fromField(parseDouble(fAtkDmg), draftAtkDmgOp, ATTACK_DAMAGE_BASE, ATK_DMG_ID);
        draftAtkSpd     = fromField(parseDouble(fAtkSpd), draftAtkSpdOp, ATTACK_SPEED_BASE, ATK_SPD_ID);
        draftArmor      = parseDouble(fArmor);
        draftArmorTough = parseDouble(fArmorTough);
        draftMoveSpd    = fromField(parseDouble(fMoveSpd), draftMoveSpdOp, MOVEMENT_SPEED_BASE, MOVE_SPD_ID);
    }
    private double toField(double raw, KubeJSItemAttrExporter.Op op, double base, String attrId) {
        return draftAddMode.contains(attrId) ? raw : toEffectiveSpeed(raw, op, base);
    }

    private double fromField(double value, KubeJSItemAttrExporter.Op op, double base, String attrId) {
        return draftAddMode.contains(attrId) ? value : fromEffectiveSpeed(value, op, base);
    }

    private double coreExport(double draft, double vanilla, KubeJSItemAttrExporter.Op draftOp,
            KubeJSItemAttrExporter.Op vanillaOp, double base, String attrId) {
        if (draftAddMode.contains(attrId)) return Double.isNaN(draft) || draft == 0.0 ? Double.NaN : draft;
        return sameCore(draft, vanilla, draftOp, vanillaOp, base) ? Double.NaN : draft;
    }

    private void toggleAttrMode(String attrId) {
        syncFieldsToDraft();
        boolean toAdd = !draftAddMode.contains(attrId);
        switch (attrId) {
            case ATK_DMG_ID -> draftAtkDmg = rebaseCore(draftAtkDmg, vanillaAtkDmg, draftAtkDmgOp, toAdd);
            case ATK_SPD_ID -> draftAtkSpd = rebaseCore(draftAtkSpd, vanillaAtkSpd, draftAtkSpdOp, toAdd);
            case ARMOR_ID -> draftArmor = rebaseCore(draftArmor, vanillaArmor, draftArmorOp, toAdd);
            case ARMOR_TOUGH_ID -> draftArmorTough = rebaseCore(draftArmorTough, vanillaArmorTough, draftArmorToughOp, toAdd);
            case MOVE_SPD_ID -> draftMoveSpd = rebaseCore(draftMoveSpd, vanillaMoveSpd, draftMoveSpdOp, toAdd);
            default -> { }
        }
        if (toAdd) draftAddMode.add(attrId); else draftAddMode.remove(attrId);
        applyDraftToFields();
    }

    private static boolean isCoreAttrId(String attrId) {
        return ATK_DMG_ID.equals(attrId) || ATK_SPD_ID.equals(attrId) || ARMOR_ID.equals(attrId)
                || ARMOR_TOUGH_ID.equals(attrId) || MOVE_SPD_ID.equals(attrId);
    }

    private static double rebaseCore(double draft, double vanilla, KubeJSItemAttrExporter.Op op, boolean toAdd) {
        if (Double.isNaN(draft) || op != KubeJSItemAttrExporter.Op.ADDITION) return draft;
        double original = Double.isNaN(vanilla) ? 0.0 : vanilla;
        return toAdd ? draft - original : draft + original;
    }

    private static final double ATTACK_DAMAGE_BASE  = ItemAttrMath.ATTACK_DAMAGE_BASE;
    private static final double ATTACK_SPEED_BASE   = ItemAttrMath.ATTACK_SPEED_BASE;
    private static final double MOVEMENT_SPEED_BASE = ItemAttrMath.MOVEMENT_SPEED_BASE;

    private static double toEffectiveSpeed(double raw, KubeJSItemAttrExporter.Op op, double base) {
        return ItemAttrMath.toEffective(raw, op == KubeJSItemAttrExporter.Op.ADDITION, base);
    }

    private static double fromEffectiveSpeed(double effective, KubeJSItemAttrExporter.Op op, double base) {
        return ItemAttrMath.fromEffective(effective, op == KubeJSItemAttrExporter.Op.ADDITION, base);
    }

    /**
     * True si el valor del draft (ya leido del campo de texto) NO cambio
     * respecto al baseline vanilla: misma Op Y mismo valor salvo el
     * redondeo del propio campo (ver {@link ItemAttrMath#sameAsBaseline()}).
     * Reemplaza el {@code eqD(...) && op == op} crudo, que marcaba como
     * "editada" cualquier velocidad de ataque float vanilla (-2.4F...) en
     * CADA guardado.
     */
    private static boolean sameCore(double draft, double vanilla,
            KubeJSItemAttrExporter.Op draftOp, KubeJSItemAttrExporter.Op vanillaOp, double base) {
        return draftOp == vanillaOp
                && ItemAttrMath.sameAsBaseline(draft, vanilla, draftOp == KubeJSItemAttrExporter.Op.ADDITION, base);
    }

    private void setF(EditBox b, String v) { if (b != null) b.setValue(v); }
    private String fmt(double v) { return ItemAttrMath.fmt(v); }
    private int    parseInt(EditBox b)    { try { return Integer.parseInt(b.getValue().trim()); }  catch (Exception e) { return -1; } }
    private double parseDouble(EditBox b) { try { return Double.parseDouble(b.getValue().trim()); } catch (Exception e) { return Double.NaN; } }
    private static boolean eqD(double a, double b) { return (Double.isNaN(a) && Double.isNaN(b)) || a == b; }

    private boolean pendingDuplicate = false;

    private void duplicateItem() {
        if (readOnlyMode) { flash(Lang.get("gui.kve.item.readonly")); return; }
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }
        if (selectedItem == null) { flash(Lang.get("gui.kve.item.select_first")); return; }
        syncFieldsToDraft();
        pendingDuplicate = true;
        flash(Lang.get("gui.kve.item.duplicate_hint"));
    }

    public void selectEntryById(String itemId) {
        if (itemId == null || itemId.isBlank()) return;
        ResourceLocation rl = ResourceLocation.tryParse(itemId.trim());
        if (rl == null) return;
        Item item = BuiltInRegistries.ITEM.get(rl);
        if (item == net.minecraft.world.item.Items.AIR) return;
        closeItemCreator();
        orphanedItemId = null;
        selectItem(item);
        refresh();
    }

    private void selectItem(Item item) {
        selectedItem = item;
        selectedSupportsAttrs = true;
        ResourceLocation k = BuiltInRegistries.ITEM.getKey(item);
        String id = k != null ? k.toString() : "";
        currentItemId = id;
        externalSourceNote = "";
        if (pendingDuplicate) {
            pendingDuplicate = false;
            computeVanillaBaseline(item);
            applyDraftToFields();
            if (KubeJSItemAttrExporter.getEntry(id) != null) {
                flash(Lang.get("gui.kve.item.duplicate_target_conflict"));
            }
            return;
        }
        computeVanillaBaseline(item);
        var saved = KubeJSItemAttrExporter.getEntry(id);
        if (saved != null) {
            loadEntryToDraft(saved);
        } else {
            loadVanillaToDraft(item);
            draftEnabled = true;
            draftCategory = "";
            overlayExternalItemStats(id);
        }
        applyDraftToFields();
    }

    private void cycleRarity() {
        syncFieldsToDraft();
        draftRarityIdx = (draftRarityIdx + 1) % RARITIES.length;
        refresh();
    }

    private void toggleFire() {
        syncFieldsToDraft();
        draftFire = !draftFire;
        refresh();
    }

    private void openHideOptions() {
        syncFieldsToDraft();
        minecraft.setScreen(new ItemHideOptionsScreen(this, draftHideViewers, draftHideCreative, draftHideLoot, chosen -> {
            draftHideViewers  = chosen.contains("jei");
            draftHideCreative = chosen.contains("tab");
            draftHideLoot     = chosen.contains("loot");
            refresh();
        }));
    }

    private void toggleFavoriteCurrent() {
        if (currentItemId.isEmpty()) return;
        syncFieldsToDraft();
        CraftEditorState.toggleFavoriteItem(currentItemId);
        flash(CraftEditorState.isFavoriteItem(currentItemId)
                ? Lang.get("gui.kve.item.favorite_add")
                : Lang.get("gui.kve.item.favorite_remove"));
        refresh();
    }

    private void toggleEnabled() {
        if (readOnlyMode) { flash(Lang.get("gui.kve.item.readonly")); return; }
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }
        syncFieldsToDraft();
        draftEnabled = !draftEnabled;
        String id = currentItemId;
        if (!id.isEmpty() && KubeJSItemAttrExporter.getEntry(id) != null) {
            boolean previousEnabled = !draftEnabled;
            if (KubeJSItemAttrExporter.stageSetEnabled(id, draftEnabled)) {
                PendingChangeManager.queue(PendingChange.of(PendingDomain.CONTENT, id, PendingChange.Action.EDIT, true,
                        () -> {
                            KubeJSItemAttrExporter.commitItems();
                            KveChangeLog.record(KveChangeLog.Log.ITEMS, PendingChange.Action.EDIT, id);
                        },
                        () -> {
                            KubeJSItemAttrExporter.discardStagedSetEnabled(id, previousEnabled);
                            refreshSaved();
                        }));
                flash(Lang.get("gui.kve.item.restart_needed"));
                refreshSaved();
            }
        }
        refresh();
    }

    private void toggleReadOnly() {
        syncFieldsToDraft();
        readOnlyMode = !readOnlyMode;
        refresh();
    }

    private void undoItem() {
        if (readOnlyMode) { flash(Lang.get("gui.kve.item.readonly")); return; }
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }
        if (currentItemId.isEmpty()) return;
        String id = currentItemId;
        KubeJSItemAttrExporter.ItemEntry beforeUndo = KubeJSItemAttrExporter.getEntry(id);
        KubeJSItemAttrExporter.ItemEntry restored = KubeJSItemAttrExporter.stageUndo(id);
        if (restored != null) {
            loadEntryToDraft(restored);
            applyDraftToFields();
            PendingChangeManager.queue(PendingChange.of(PendingDomain.CONTENT, id, PendingChange.Action.EDIT, true,
                    () -> {
                        KubeJSItemAttrExporter.commitItems();
                        KveChangeLog.record(KveChangeLog.Log.ITEMS, PendingChange.Action.EDIT, id);
                    },
                    () -> {
                        KubeJSItemAttrExporter.discardStagedUndo(id, restored, beforeUndo);
                        refreshSaved();
                    })
                    .withDiff(() -> KubeJSItemAttrExporter.previewItemJS(beforeUndo == null ? restored : beforeUndo),
                            () -> KubeJSItemAttrExporter.previewItemJS(restored)));
            flash(Lang.get("gui.kve.item.restart_needed"));
            refreshSaved();
        }
        refresh();
    }

    private KubeJSItemAttrExporter.ItemEntry buildDraftItemEntry() {
        if (selectedItem == null) return null;
        syncFieldsToDraft();
        ResourceLocation k = BuiltInRegistries.ITEM.getKey(selectedItem);
        if (k == null) return null;

        KubeJSItemAttrExporter.ItemEntry e = new KubeJSItemAttrExporter.ItemEntry();
        e.itemId         = k.toString();
        e.enabled         = draftEnabled;
        e.category        = draftCategory;
        e.maxDamage      = draftMaxDamage != vanillaMaxDamage ? draftMaxDamage : -1;
        e.maxStackSize   = draftMaxStack  != vanillaMaxStack  ? draftMaxStack  : -1;
        e.burnTime       = draftBurnTime;
        e.attackDamage   = coreExport(draftAtkDmg, vanillaAtkDmg, draftAtkDmgOp, vanillaAtkDmgOp, ATTACK_DAMAGE_BASE, ATK_DMG_ID);
        e.attackSpeed    = coreExport(draftAtkSpd, vanillaAtkSpd, draftAtkSpdOp, vanillaAtkSpdOp, ATTACK_SPEED_BASE, ATK_SPD_ID);
        e.armor          = coreExport(draftArmor, vanillaArmor, draftArmorOp, vanillaArmorOp, 0.0, ARMOR_ID);
        e.armorToughness = coreExport(draftArmorTough, vanillaArmorTough, draftArmorToughOp, vanillaArmorToughOp, 0.0, ARMOR_TOUGH_ID);
        e.movementSpeed  = coreExport(draftMoveSpd, vanillaMoveSpd, draftMoveSpdOp, vanillaMoveSpdOp, MOVEMENT_SPEED_BASE, MOVE_SPD_ID);
        e.attackDamageOp   = draftAtkDmgOp;
        e.attackSpeedOp    = draftAtkSpdOp;
        e.armorOp          = draftArmorOp;
        e.armorToughnessOp = draftArmorToughOp;
        e.movementSpeedOp  = draftMoveSpdOp;
        boolean fireChanged = draftFire != vanillaFireResistant;
        e.fireResistant    = fireChanged && draftFire;
        e.fireResistantSet = fireChanged;
        e.rarity         = draftRarityIdx > 0 ? RARITIES[draftRarityIdx] : "";
        e.hideFromCreativeTab   = draftHideCreative;
        e.hideFromRecipeViewers = draftHideViewers;
        e.hideFromLoot = draftHideLoot;
        e.food           = foodEquals(draftFood, vanillaFoodBaseline) ? null : draftFood;
        e.tooltip        = new java.util.ArrayList<>(draftTooltip);
        e.combat         = draftCombat != null && !draftCombat.isEmpty() ? draftCombat.copy() : null;
        e.attributes.clear();
        for (var entry : draftAttributes.entrySet()) {
            KubeJSItemAttrExporter.AttrMod vanillaDefault = vanillaExtraAttributes.get(entry.getKey());
            KubeJSItemAttrExporter.AttrMod v = entry.getValue();
            boolean sameAsVanilla = vanillaDefault != null && eqD(v.value, vanillaDefault.value) && v.op == vanillaDefault.op
                    && !draftAddMode.contains(entry.getKey());
            if (!sameAsVanilla) e.attributes.put(entry.getKey(), v);
        }
        e.addModeAttributes.clear();
        e.addModeAttributes.addAll(draftAddMode);
        e.removedAttributes.clear();
        for (String removed : draftRemovedAttrs) {
            if (vanillaExtraAttributes.containsKey(removed) && !draftAttributes.containsKey(removed)) {
                e.removedAttributes.add(removed);
            }
        }
        e.curioAttributes.clear();
        e.curioRemoved.clear();
        for (var entry : draftCurio.entrySet()) {
            KubeJSItemAttrExporter.SlotAttrMod def = vanillaCurio.get(entry.getKey());
            KubeJSItemAttrExporter.SlotAttrMod v = entry.getValue();
            boolean sameAsDefault = def != null && eqD(v.value, def.value) && v.op == def.op;
            if (!sameAsDefault) e.curioAttributes.add(v.copy());
        }
        for (String key : vanillaCurio.keySet()) {
            if (!draftCurio.containsKey(key)) e.curioRemoved.add(key);
        }
        return e;
    }

    private void saveItem() {
        if (readOnlyMode) { flash(Lang.get("gui.kve.item.readonly")); return; }
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }
        if (selectedItem == null) { flash(Lang.get("gui.kve.item.select_first")); return; }

        KubeJSItemAttrExporter.ItemEntry e = buildDraftItemEntry();
        if (e == null) { flash(Lang.get("gui.kve.item.select_first")); return; }

        boolean conflict = ExternalScriptScanner.getItemStats(e.itemId) != null;

        KubeJSItemAttrExporter.ItemEntry previous = KubeJSItemAttrExporter.getEntry(e.itemId);
        PendingChange.Action action = previous == null ? PendingChange.Action.CREATE : PendingChange.Action.EDIT;
        KubeJSItemAttrExporter.stageItem(e);
        PendingChangeManager.queue(PendingChange.of(PendingDomain.CONTENT, e.itemId, action, true,
                () -> {
                    KubeJSItemAttrExporter.commitItems();
                    KveChangeLog.record(KveChangeLog.Log.ITEMS, action, e.itemId);
                },
                () -> {
                    KubeJSItemAttrExporter.discardStagedItem(e.itemId, previous);
                    refreshSaved();
                })
                .withDiff(previous == null ? null : () -> KubeJSItemAttrExporter.previewItemJS(previous),
                        () -> KubeJSItemAttrExporter.previewItemJS(e)));

        currentItemId = e.itemId;
        flash(!selectedSupportsAttrs && KubeJSItemAttrExporter.hasAttributeEdits(e)
                ? Lang.get("gui.kve.item.attr_unsupported_saved")
                : conflict
                    ? Lang.get("gui.kve.item.conflict")
                    : Lang.get("gui.kve.item.restart_needed"));
        refreshSaved();
        refresh();
    }

    private void previewItem() {
        KubeJSItemAttrExporter.ItemEntry e = buildDraftItemEntry();
        if (e == null) { flash(Lang.get("gui.kve.item.select_first")); return; }
        String script = KubeJSItemAttrExporter.previewItemJS(e);
        minecraft.setScreen(new RecipePreviewScreen(this, e.itemId, script));
    }

    // Mismo UUID fijo vanilla que KubeJSItemAttrExporter#addAttr ya usa
    // (Item.BASE_ATTACK_DAMAGE_UUID/BASE_ATTACK_SPEED_UUID) - ver el
    // Javadoc de ese metodo para el porque: sin este UUID exacto, el
    // tooltip real de Minecraft NO fusiona el modifier con el stat base
    // (queda como un "extra" con signo/color, en vez de un numero final
    // sin signo) - la vista previa de aca abajo tiene que reproducir
    // exactamente la MISMA regla para no mentirle al jugador sobre como
    // se va a ver el item exportado de verdad.
    private static final String PREVIEW_ATTACK_DAMAGE_UUID = "CB3F55D3-645C-4F38-A497-9C13A33DB5CF";
    private static final String PREVIEW_ATTACK_SPEED_UUID  = "FA233E1C-4180-4865-B01B-BCCE9785ACA3";

    private EquipmentSlot previewSlot = EquipmentSlot.MAINHAND;

    private ItemStack buildPreviewStack() {
        if (selectedItem == null) return ItemStack.EMPTY;
        syncFieldsToDraft();
        ItemStack stack = new ItemStack(selectedItem);
        net.minecraft.nbt.CompoundTag previewData = com.kve.common.util.ItemExtraDataCodec.customData(stack);
        previewData.putBoolean(com.kve.common.itemrules.ItemTooltipEnforcer.PREVIEW_TAG, true);
        com.kve.common.util.ItemExtraDataCodec.setCustomData(stack, previewData);
        net.minecraft.world.item.component.ItemAttributeModifiers.Builder mods = net.minecraft.world.item.component.ItemAttributeModifiers.builder();
        int[] counter = {0};
        previewSlot = KubeJsItemAttrSupport.defaultSlot(selectedItem);
        addPreviewCoreAttr(mods, "minecraft:generic.attack_damage", draftAtkDmg, draftAtkDmgOp, counter);
        addPreviewCoreAttr(mods, "minecraft:generic.attack_speed",  draftAtkSpd, draftAtkSpdOp, counter);
        addPreviewCoreAttr(mods, "minecraft:generic.armor",           draftArmor,      draftArmorOp,      counter);
        addPreviewCoreAttr(mods, "minecraft:generic.armor_toughness", draftArmorTough, draftArmorToughOp, counter);
        addPreviewCoreAttr(mods, "minecraft:generic.movement_speed",  draftMoveSpd,    draftMoveSpdOp,    counter);
        for (KubeJSItemAttrExporter.AttrMod m : draftAttributes.values()) {
            if (m.attrId == null || m.attrId.isEmpty()) continue;
            addPreviewAttrEntry(mods, m.attrId, m.value, m.op, counter[0]++, isFixedVanillaUuidAttr(m.attrId));
        }
        if (counter[0] > 0) stack.set(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS, mods.build());
        if (!draftTooltip.isEmpty()) {
            java.util.List<Component> lore = new java.util.ArrayList<>();
            for (String line : draftTooltip) if (line != null) lore.add(Component.literal(line));
            if (!lore.isEmpty()) stack.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lore));
        }
        return stack;
    }

    private java.util.List<KubeJSItemAttrExporter.AttrMod> previewMods() {
        java.util.List<KubeJSItemAttrExporter.AttrMod> out = new java.util.ArrayList<>();
        addPreviewMod(out, ATK_DMG_ID, draftAtkDmg, draftAtkDmgOp);
        addPreviewMod(out, ATK_SPD_ID, draftAtkSpd, draftAtkSpdOp);
        addPreviewMod(out, ARMOR_ID, draftArmor, draftArmorOp);
        addPreviewMod(out, ARMOR_TOUGH_ID, draftArmorTough, draftArmorToughOp);
        addPreviewMod(out, MOVE_SPD_ID, draftMoveSpd, draftMoveSpdOp);
        for (KubeJSItemAttrExporter.AttrMod m : draftAttributes.values()) {
            if (m.attrId != null && !m.attrId.isEmpty()) out.add(m);
        }
        return out;
    }

    private void addPreviewMod(java.util.List<KubeJSItemAttrExporter.AttrMod> out, String attrId, double value, KubeJSItemAttrExporter.Op op) {
        if (!Double.isNaN(value) && !draftAttributes.containsKey(attrId)) out.add(new KubeJSItemAttrExporter.AttrMod(attrId, value, op));
    }
    private void addPreviewCoreAttr(net.minecraft.world.item.component.ItemAttributeModifiers.Builder mods, String attrId, double value, KubeJSItemAttrExporter.Op op, int[] counter) {
        if (Double.isNaN(value) || draftAttributes.containsKey(attrId)) return;
        addPreviewAttrEntry(mods, attrId, value, op, counter[0]++, isFixedVanillaUuidAttr(attrId));
    }

    private void addPreviewAttrEntry(net.minecraft.world.item.component.ItemAttributeModifiers.Builder mods, String attrId,
            double value, KubeJSItemAttrExporter.Op op, int idx, boolean fixedUuid) {
        ResourceLocation rl = ResourceLocation.tryParse(attrId);
        var attr = rl == null ? java.util.Optional.<net.minecraft.core.Holder.Reference<net.minecraft.world.entity.ai.attributes.Attribute>>empty()
                : BuiltInRegistries.ATTRIBUTE.getHolder(rl);
        if (attr.isEmpty()) return;
        ResourceLocation id = fixedUuid
                ? (attrId.endsWith("attack_damage") ? net.minecraft.world.item.Item.BASE_ATTACK_DAMAGE_ID : net.minecraft.world.item.Item.BASE_ATTACK_SPEED_ID)
                : ResourceLocation.fromNamespaceAndPath("shovel_js", "preview_" + idx);
        mods.add(attr.get(), new net.minecraft.world.entity.ai.attributes.AttributeModifier(id, value, op.vanilla()),
                net.minecraft.world.entity.EquipmentSlotGroup.bySlot(previewSlot));
    }

    private void drawLiveTooltipPreview(GuiGraphics g, int mx, int my) {
        if (selectedItem == null) return;
        int rx = L + effW() - INFO_W + 4, ry = T + 16;
        int labelW = Math.max(40, (rx + INFO_W - 8) - (rx + infoIconSize + 6));
        boolean hov = mx >= rx && mx < rx + infoIconSize + 6 + labelW && my >= ry && my < ry + infoIconSize;
        if (!hov || !net.minecraft.client.gui.screens.Screen.hasShiftDown()) return;
        ItemStack stack = buildPreviewStack();
        java.util.List<Component> lines = new java.util.ArrayList<>(getTooltipFromItem(minecraft, stack));
        com.kve.common.itemrules.ItemAttrTooltipStyle.apply(lines, previewMods(), draftAddMode);
        if (draftCombat != null) lines.addAll(com.kve.common.itemrules.ItemCombatTooltip.lines(draftCombat));
        g.renderComponentTooltip(font, lines, mx, my);
    }

    private String orphanedItemId = null;

    private void deleteItem() {
        if (readOnlyMode) { flash(Lang.get("gui.kve.item.readonly")); return; }
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }

        if (selectedItem != null) {
            ResourceLocation k = BuiltInRegistries.ITEM.getKey(selectedItem);
            if (k != null) {
                String id = k.toString();
                KubeJSItemAttrExporter.ItemEntry previous = KubeJSItemAttrExporter.getEntry(id);
                KubeJSItemAttrExporter.stageDeleteItem(id);
                PendingChangeManager.queue(PendingChange.of(PendingDomain.CONTENT, id, PendingChange.Action.DELETE, true,
                        () -> {
                            KubeJSItemAttrExporter.commitItems();
                            KveChangeLog.record(KveChangeLog.Log.ITEMS, PendingChange.Action.DELETE, id);
                        },
                        () -> {
                            if (previous != null) KubeJSItemAttrExporter.stageItem(previous);
                            refreshSaved();
                        })
                        .withDiff(previous == null ? null : () -> KubeJSItemAttrExporter.previewItemJS(previous), null));
                loadVanillaToDraft(selectedItem);
                applyDraftToFields();
                flash(Lang.get("gui.kve.item.restart_needed"));
                refreshSaved();
                return;
            }
        }

        if (orphanedItemId != null) {
            String id = orphanedItemId;
            KubeJSItemAttrExporter.ItemEntry previous = KubeJSItemAttrExporter.getEntry(id);
            KubeJSItemAttrExporter.stageDeleteItem(id);
            PendingChangeManager.queue(PendingChange.of(PendingDomain.CONTENT, id, PendingChange.Action.DELETE, true,
                    () -> {
                        KubeJSItemAttrExporter.commitItems();
                        KveChangeLog.record(KveChangeLog.Log.ITEMS, PendingChange.Action.DELETE, id);
                    },
                    () -> {
                        if (previous != null) KubeJSItemAttrExporter.stageItem(previous);
                        refreshSaved();
                    })
                    .withDiff(previous == null ? null : () -> KubeJSItemAttrExporter.previewItemJS(previous), null));
            orphanedItemId = null;
            clearDraft();
            applyDraftToFields();
            flash(Lang.get("gui.kve.item.restart_needed"));
            refreshSaved();
            return;
        }
        flash(Lang.get("gui.kve.item.select_first"));
    }

    private void resetDraft() {
        if (selectedItem != null) {
            computeVanillaBaseline(selectedItem);
            loadVanillaToDraft(selectedItem);
            draftEnabled = true;
            draftCategory = "";
            overlayExternalItemStats(currentItemId);
        } else {
            clearDraft();
        }
        applyDraftToFields();
    }

    private void onApplyPending() {
        if (!ClientPermissionState.canApply()) { flash(Lang.get("gui.kve.perm.apply_denied")); return; }
        if (PendingChangeManager.isEmpty()) return;
        if (PendingChangeManager.domainHasDiff(PendingDomain.CONTENT)) {
            minecraft.setScreen(new DiffPreviewScreen(this,
                    PendingChangeManager.entriesForDomain(PendingDomain.CONTENT),
                    this::proceedApply));
            return;
        }
        proceedApply();
    }

    private void proceedApply() {
        java.util.Set<PendingDomain> domains = PendingChangeManager.domainsPresent();
        if (domains.size() > 1) {
            int otherCount = PendingChangeManager.size() - PendingChangeManager.sizeForDomain(PendingDomain.CONTENT);
            minecraft.setScreen(new KveConfirmScreen(
                    confirmed -> {
                        minecraft.setScreen(this);
                        applyPendingResult(confirmed ? PendingChangeManager.applyAll() : PendingChangeManager.applyDomain(PendingDomain.CONTENT));
                    },
                    Component.literal(Lang.get("gui.kve.pending.other_domains_title")),
                    Component.literal(Lang.get("gui.kve.pending.other_domains_msg", otherCount))));
            return;
        }
        applyPendingResult(PendingChangeManager.applyDomain(PendingDomain.CONTENT));
    }

    private void applyPendingResult(PendingChangeManager.ApplyResult result) {
                com.kve.client.gui.KveToast.applied(result, KubeJSItemAttrExporter.getLastWriteWarnings());
        refreshSaved();
        refresh();
    }

    private void onDiscardPending() {
        if (PendingChangeManager.isEmpty()) return;
        int count = PendingChangeManager.sizeForDomain(PendingDomain.CONTENT);
        if (count == 0) return;
        PendingChangeManager.discardDomain(PendingDomain.CONTENT);
        flash(Lang.get("gui.kve.pending.discarded", count));
        refreshSaved();
        refresh();
    }

    private void doOpenChangeHistory() {
        minecraft.setScreen(new KveChangeHistoryScreen(this, KveChangeLog.Log.ITEMS,
                Lang.c("gui.kve.change_history.title", Lang.get("gui.kve.change_history.domain.items")),
                id -> {
                    ResourceLocation rl = ResourceLocation.tryParse(id);
                    Item found = rl != null ? BuiltInRegistries.ITEM.getOptional(rl).orElse(null) : null;
                    if (found != null) selectItem(found);
                    minecraft.setScreen(this);
                    refresh();
                }).withIcons(MiniItemIcon::item));
    }

    private void goBack()  {
        if (KveStudioShell.exitIfHosted(this, ownerTab != null ? ownerTab : this, returnScreen)) return;
        minecraft.setScreen(returnScreen != null ? returnScreen : new KveStudioShell());
    }

    private void pickFromHand() {
        if (minecraft == null || minecraft.player == null) return;
        ItemStack stack = minecraft.player.getMainHandItem();
        if (stack.isEmpty()) stack = minecraft.player.getOffhandItem();
        if (stack.isEmpty()) { flash(Lang.get("gui.kve.item.from_hand.none")); return; }
        selectItem(stack.getItem());
        refresh();
    }

    private void pickFromCrosshair() {
        if (minecraft == null || minecraft.player == null) return;
        if (!(minecraft.hitResult instanceof BlockHitResult bhr) || minecraft.level == null) {
            flash(Lang.get("gui.kve.item.from_crosshair.none"));
            return;
        }
        BlockPos pos = bhr.getBlockPos();
        BlockState state = minecraft.level.getBlockState(pos);
        Item asItem = state.getBlock().asItem();
        if (asItem == Items.AIR) { flash(Lang.get("gui.kve.item.from_crosshair.none")); return; }
        selectItem(asItem);
        refresh();
    }

    private void openTagForItem() {
        if (selectedItem == null) return;
        List<ResourceLocation> tags = itemTagIds(selectedItem);
        if (tags.isEmpty()) return;
        syncFieldsToDraft();
        minecraft.setScreen(new TagEditorScreen(this, "#" + tags.get(0)));
    }

    private void openIdUsage() {
        if (currentItemId.isEmpty()) return;
        syncFieldsToDraft();
        minecraft.setScreen(new IdUsageScreen(this, currentItemId));
    }

    private void openItemRemoval() {
        if (currentItemId.isEmpty()) return;
        String itemId = currentItemId;
        List<ItemUsage> usages = ItemUsageResolver.resolve(itemId);
        if (usages.isEmpty()) { flash(Lang.get("gui.kve.item.removal.empty")); return; }
        minecraft.setScreen(new ItemRemovalScreen(this, itemId, usages, chosen -> onItemRemovalConfirmed(itemId, chosen)));
    }

    private void onItemRemovalConfirmed(String itemId, java.util.Set<ItemUsage> chosen) {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }
        for (ItemUsage u : chosen) {
            switch (u.kind()) {
                case RECIPE_INPUT, RECIPE_OUTPUT -> queueRecipeItemRemoval(u.entryId(), itemId);
                case TAG_MEMBER -> TagEditorScreen.applyMemberRemoval(u.entryId(), itemId);
                case LOOT_ENTRY -> queueLootEntryRemoval(u.entryId(), itemId);
            }
        }
        flash(Lang.get("gui.kve.item.removal.queued", chosen.size()));
    }

    /**
     * §5/§2.3 de las notas: mismo camino que editar la receta a mano en
     * CraftEditorScreen (stageRecipe() + PendingChange EDIT + withDiff()), solo
     * que la mutación es automática en vez de venir de la UI del editor.
     * Únicamente toca los campos que {@link ItemUsageResolver} realmente
     * resuelve (ingredients/shapelessIngredients/combinationPedestals/
     * output/machineInputs/machineOutputs) - combinationCenter y
     * secondaryOutput quedan afuera porque el resolver tampoco los mira.
     * Receta vacía tras la remoción: NO se auto-borra - deja
     * huecos y la validación existente avisa recién en Apply.
     */
    private void queueRecipeItemRemoval(String recipeId, String itemId) {
        RecipeData current = KubeJSExporter.getAddedRecipes().stream()
                .filter(r -> r.recipeId.equals(recipeId)).findFirst().orElse(null);
        if (current == null) return;

        RecipeData previous = KubeJSExporter.copyRecipe(current);
        RecipeData mutated = KubeJSExporter.copyRecipe(current);

        if (mutated.type == RecipeData.RecipeType.CUSTOM_MACHINE) {
            mutated.machineInputs.removeIf(s -> matchesItemSlot(s, itemId));
            mutated.machineOutputs.removeIf(s -> matchesItemSlot(s, itemId));
        } else {
            for (int i = 0; i < mutated.ingredients.size(); i++) {
                if (itemId.equals(idOf(mutated.ingredients.get(i)))) mutated.ingredients.set(i, ItemStack.EMPTY);
            }
            for (int i = 0; i < mutated.combinationPedestals.size(); i++) {
                if (itemId.equals(idOf(mutated.combinationPedestals.get(i)))) mutated.combinationPedestals.set(i, ItemStack.EMPTY);
            }
            mutated.shapelessIngredients.removeIf(s -> itemId.equals(idOf(s)));
            if (itemId.equals(idOf(mutated.output))) mutated.output = ItemStack.EMPTY;
        }

        KubeJSExporter.stageRecipe(mutated);
        PendingChangeManager.queue(PendingChange.of(PendingDomain.RECIPES, recipeId, PendingChange.Action.EDIT, false,
                () -> {
                    KubeJSExporter.commitAdded();
                    KveChangeLog.record(KveChangeLog.Log.RECIPES, PendingChange.Action.EDIT, recipeId);
                },
                () -> KubeJSExporter.discardStagedRecipe(recipeId, previous))
                .withDiff(() -> KubeJSExporter.previewRecipeJS(previous), () -> KubeJSExporter.previewRecipeJS(mutated)));
    }

    private static boolean matchesItemSlot(RecipeData.MachineSlot s, String itemId) {
        return s != null && s.type == RecipeData.MachineSlotType.ITEM && itemId.equals(idOf(s.item));
    }

    private static String idOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? null : key.toString();
    }

    private void queueLootEntryRemoval(String entryKey, String itemId) {
        LootTableExporter.LootTableEntry current = LootTableExporter.getEntry(entryKey);
        if (current == null) return;

        LootTableExporter.LootTableEntry previous = current.copy();
        LootTableExporter.LootTableEntry mutated = current.copy();
        for (LootTableExporter.LootPoolData pool : mutated.pools) {
            pool.entries.removeIf(e -> itemId.equals(e.itemId));
        }

        LootTableExporter.stageLootTable(mutated);
        PendingChangeManager.queue(PendingChange.of(PendingDomain.LOOT, entryKey, PendingChange.Action.EDIT, false,
                () -> {
                    LootTableExporter.commitLootTables();
                    KveChangeLog.record(KveChangeLog.Log.LOOT, PendingChange.Action.EDIT, entryKey);
                },
                () -> LootTableExporter.discardStagedLootTable(entryKey, previous))
                .withDiff(() -> LootTableExporter.previewLoot(previous), () -> LootTableExporter.previewLoot(mutated)));
    }

    private List<ResourceLocation> itemTagIds(Item item) {
        List<ResourceLocation> out = new ArrayList<>();
        try {
            item.builtInRegistryHolder().tags().forEach(tk -> out.add(tk.location()));
        } catch (Throwable ignored) {}
        return out;
    }

    private record MoreHeader(String label, int x, int y, int w) {}
    private final List<MoreHeader> moreHeaders = new ArrayList<>();

    private void moreHeader(KveGuiStyle.ButtonRow row, String key, int x, int right) {
        int y = row.section(11);
        moreHeaders.add(new MoreHeader(Lang.get(key), x, y, right - x));
    }

    private void drawMoreHeaders(GuiGraphics g) {
        for (MoreHeader h : moreHeaders) {
            KveGuiStyle.drawTextSafe(g, h.label(), h.x(), h.y(), h.w(), KveGuiStyle.TEXT_MUTED);
            g.fill(h.x(), h.y() + 9, h.x() + h.w(), h.y() + 10, KveGuiStyle.BORDER_SUBTLE);
        }
    }

    @Override
    public void onClose() { goBack(); }

    private void openAttributePicker() {
        syncFieldsToDraft();
        minecraft.setScreen(new AttributePickerScreen(this, mod -> applyPickedAttribute(mod.attrId, mod), draftAttributes::get));
    }

    public void applyPickedAttribute(String attrId, KubeJSItemAttrExporter.AttrMod mod) {
        draftAttributes.put(attrId, mod);
        draftRemovedAttrs.remove(attrId);
        refresh();
    }

    private void editCustomAttribute(KubeJSItemAttrExporter.AttrMod existing) {
        syncFieldsToDraft();
        String shortName = existing.attrId.contains(":")
                ? existing.attrId.substring(existing.attrId.indexOf(':') + 1)
                : existing.attrId;
        minecraft.setScreen(new AttributeValueScreen(this, existing.attrId, shortName,
                mod -> { applyPickedAttribute(existing.attrId, mod); minecraft.setScreen(this); })
                .withExisting(existing));
    }

    private void openFoodEditor() {
        syncFieldsToDraft();
        minecraft.setScreen(new ItemFoodScreen(this, draftFood));
    }

    private void openTooltipEditor() {
        syncFieldsToDraft();
        minecraft.setScreen(new TooltipEditScreen(this, draftTooltip, this::applyDraftTooltip));
    }

    private boolean selectedIsCurio = false;

    private void addCurioButton(KveGuiStyle.ButtonRow row) {
        String curioLabel = draftCurio.isEmpty()
                ? Lang.get("gui.kve.item.curio.btn")
                : Lang.get("gui.kve.item.curio.btn") + " (" + draftCurio.size() + ")";
        Button curioBtn = addRenderableWidget(row.add(0, curioLabel, b -> openCurioEditor()));
        curioBtn.active = selectedItem != null;
        curioBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                net.minecraft.network.chat.Component.literal(Lang.get("gui.kve.item.curio.btn_tip"))));
    }

    private void openCurioEditor() {
        if (selectedItem == null) return;
        syncFieldsToDraft();
        minecraft.setScreen(new ItemCurioAttrScreen(this, draftCurio, new java.util.ArrayList<>(vanillaCurio.values()), map -> {
            draftCurio.clear();
            draftCurio.putAll(map);
            minecraft.setScreen(this);
            refresh();
        }));
    }

    private void openCombatEditor() {
        syncFieldsToDraft();
        minecraft.setScreen(new ItemCombatScreen(this, draftCombat, props -> {
            draftCombat = props;
            minecraft.setScreen(this);
            refresh();
        }));
    }

    private static int combatFeatureCount(KubeJSItemAttrExporter.CombatProps c) {
        int n = 0;
        if (!c.hitEffects.isEmpty()) n++;
        if (!c.passiveEffects.isEmpty()) n++;
        if (c.lifestealPct > 0) n++;
        if (c.igniteSeconds > 0) n++;
        if (c.useCooldownTicks > 0) n++;
        return n;
    }

    /** Callback from {@link TooltipEditScreen}'s Save button. */
    public void applyDraftTooltip(java.util.List<String> lines) {
        draftTooltip = lines;
        refresh();
    }

    public void applyDraftFood(KubeJSItemAttrExporter.FoodProps props) {
        draftFood = props;
        refresh();
    }

    private void removeCustomAttribute(String attrId) {
        syncFieldsToDraft();
        draftAttributes.remove(attrId);
        if (!isCoreAttrId(attrId)) draftAddMode.remove(attrId);
        if (vanillaExtraAttributes.containsKey(attrId)) draftRemovedAttrs.add(attrId);
        int maxScroll = Math.max(0, draftAttributes.size() - customContentRows(customListTop()));
        customAttrScroll = Math.max(0, Math.min(customAttrScroll, maxScroll));
        refresh();
    }

    private void flash(String m) { flashMsg = m; flashAt = System.currentTimeMillis(); }
    private void refreshSaved() {
        savedList.clear();
        savedList.addAll(KubeJSItemAttrExporter.getEntries());
        savedList.sort((a, b) -> Boolean.compare(CraftEditorState.isFavoriteItem(b.itemId), CraftEditorState.isFavoriteItem(a.itemId)));
        if (savedScrollBar != null) savedScrollBar.setTotals(savedList.size(), savedRowsVisible());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Screen creator = activeItemCreator();
        if (creator != null) { creator.render(g, mx, my, pt); return; }
        if (!hosted) renderTransparentBackground(g);
        drawFrame(g);
        drawItemGrid(g, mx, my);
        drawAttrLabels(g, mx, my);
        drawFieldHints(g);
        drawInfoPanel(g);
        drawMoreHeaders(g);
        listResize.render(g, mx, my);
        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
        drawGridTooltip(g, mx, my);
        drawLiveTooltipPreview(g, mx, my);
        if (flashAt > 0 && System.currentTimeMillis() - flashAt < 5000)
            KveGuiStyle.drawCenteredTextSafe(g, flashMsg, L + effW() / 2, T + effH() - 9, 0xFF66FF99);
    }

    private void drawFrame(GuiGraphics g) {
        KveGuiStyle.drawWindow(g, L, T, effW(), effH(), Lang.get("gui.kve.main.items"));
        g.fill(L + LIST_W, T + 14, L + LIST_W + 1, T + effH(), KveGuiStyle.th(0xFF2A4A6A));
        g.fill(L + effW() - INFO_W, T + 14, L + effW() - INFO_W + 1, T + effH(), KveGuiStyle.th(0xFF2A4A6A));
        boolean flashing = flashAt > 0 && System.currentTimeMillis() - flashAt < 5000;
        if (!flashing) {
            boolean curioOnly = selectedItem != null && !selectedSupportsAttrs && selectedIsCurio;
            String bottom = curioOnly ? Lang.get("gui.kve.item.attr_use_curio")
                    : (selectedItem != null && !selectedSupportsAttrs)
                    ? Lang.get("gui.kve.item.attr_unsupported") : Lang.get("gui.kve.item.restart_hint");
            int bottomColor = curioOnly ? KveGuiStyle.ACCENT_GOLD
                    : (selectedItem != null && !selectedSupportsAttrs) ? 0xFFE06060 : 0xFFB8883A;
            KveGuiStyle.drawTextSafe(g, bottom, L + LIST_W + 6, T + effH() - 9, effW() - LIST_W - INFO_W - 10, bottomColor);
        }
    }

    private void drawItemGrid(GuiGraphics g, int mx, int my) {
        int scroll = itemScrollBar.getScroll();
        int startX = L + 2, startY = T + 30;
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int idx = (row + scroll) * COLS + col;
                if (idx >= filtered.size()) break;
                Item item = filtered.get(idx);
                int sx = startX + col * SLOT, sy = startY + row * SLOT;
                boolean hov = mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16;
                boolean sel = item == selectedItem;
                if (sel)      g.fill(sx - 1, sy - 1, sx + 17, sy + 17, KveGuiStyle.th(0xFF2A5E9A));
                else if (hov) g.fill(sx - 1, sy - 1, sx + 17, sy + 17, KveGuiStyle.BORDER_FIELD);
                g.fill(sx, sy, sx + 16, sy + 16, KveGuiStyle.th(0xFF080C18));
                try { g.renderItem(RegistryIndex.stack(item), sx, sy); } catch (Throwable ignored) {
                    g.fill(sx + 4, sy + 4, sx + 12, sy + 12, KveGuiStyle.th(0xFF334455));
                }
                if (nav.isCursor(idx)) {
                    g.hLine(sx - 1, sx + 16, sy - 1, KveGuiStyle.ACCENT_GOLD);
                    g.hLine(sx - 1, sx + 16, sy + 16, KveGuiStyle.ACCENT_GOLD);
                    g.vLine(sx - 1, sy - 1, sy + 16, KveGuiStyle.ACCENT_GOLD);
                    g.vLine(sx + 16, sy - 1, sy + 16, KveGuiStyle.ACCENT_GOLD);
                }
                if (isFavorite(item)) {
                    g.pose().pushPose();
                    g.pose().translate(0, 0, 300);
                    KveGuiStyle.drawTextSafe(g, "\u2605", sx + 10, sy - 1, 8, 0xFFFFD966);
                    g.pose().popPose();
                }
            }
        }
        itemScrollBar.render(g);
        int totalRows = (int) Math.ceil(Math.max(1, filtered.size()) / (double) COLS);
        KveGuiStyle.drawTextSafe(g, (scroll + 1) + "/" + totalRows, L + 2, T + 30 + ROWS * SLOT + 2, LIST_W - 4, KveGuiStyle.th(0xFF556677));
    }

    /**
     * Mismo criterio de merge que {@link KubeJSItemAttrExporter#addAttr}
     * ya usa (ver su Javadoc): SOLO estos 2 attrId exactos terminan con el
     * UUID fijo vanilla (Item.BASE_ATTACK_DAMAGE_UUID/BASE_ATTACK_SPEED_UUID)
     * y por lo tanto fusionan con el stat base sin signo - cualquier otro
     * atributo (core o custom) usa UUID random y se muestra como modifier
     * extra, con signo. Compartido por {@link #buildPreviewStack()} (que
     * stack construir) y {@link #drawAttrLabels()} (que badge/tooltip
     * mostrar) para que los dos SIEMPRE coincidan.
     */
    private static boolean isFixedVanillaUuidAttr(String attrId) {
        return "minecraft:generic.attack_damage".equals(attrId) || "minecraft:generic.attack_speed".equals(attrId);
    }

    private void drawAttrLabelWithMode(GuiGraphics g, int mx, int my, String label, int x, int y, int width, int labelColor, String attrId) {
        int symW = 9;
        String shown = KveGuiStyle.truncate(font, label, Math.max(10, width - symW - 3));
        KveGuiStyle.drawTextSafe(g, shown, x, y, width - symW - 3, labelColor);
        int symX = x + Math.min(font.width(shown), width - symW - 3) + 3;
        drawModeToggle(g, mx, my, symX, y, attrId);
    }

    private void drawModeToggle(GuiGraphics g, int mx, int my, int x, int y, String attrId) {
        boolean add = draftAddMode.contains(attrId);
        int bx = x - 1, by = y - 2, bw = 9, bh = 11;
        boolean hov = mx >= bx && mx < bx + bw && my >= by && my < by + bh;
        g.fill(bx, by, bx + bw, by + bh, hov ? 0x40FFFFFF : 0x20FFFFFF);
        g.renderOutline(bx, by, bw, bh, add ? 0xFFE0A03A : 0xFF7DE0B0);
        KveGuiStyle.drawTextSafe(g, add ? "+" : "=", bx + 2, y, 7, add ? 0xFFE0A03A : 0xFF7DE0B0);
        modeHitboxes.add(java.util.Map.entry(attrId, new int[]{bx, by, bw, bh}));
        if (hov) {
            String tip = Lang.get(add ? "gui.kve.item.attr_mode_add_hint" : "gui.kve.item.attr_mode_replace_hint");
            g.renderTooltip(font, font.split(Component.literal(tip), 220), mx, my);
        }
    }

    private void drawAttrLabels(GuiGraphics g, int mx, int my) {
        modeHitboxes.clear();
        int lx = L + LIST_W + 6;
        KveGuiStyle.Layout ly = new KveGuiStyle.Layout(lx, T + 18, GAP);
        int c1 = KveGuiStyle.th(0xFF7BC8E0), c2 = 0xFF7DE0B0;

        if (!externalSourceNote.isEmpty()) {
            String note = Lang.get("gui.kve.item.external_detected") + ": " + externalSourceNote;
            KveGuiStyle.drawTextSafe(g, note, lx, T + 6, 230, 0xFFE8A03A);
        }
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.item.max_damage"),     lx, ly.row() + 2, 90, c1);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.item.max_stack"),      lx, ly.row() + 2, 90, c1);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.item.fuel"),           lx, ly.row() + 2, 90, c1);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.item.rarity"),         lx, ly.row() + 2, 90, c1);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.item.fire_resistant"), lx, ly.row() + 2, 90, c1); ly.gap(4);
        // Revertido: estas 5 filas vuelven a ser texto
        // estatico de nuevo (los botones de Op +/-% que las reemplazaban se
        // sacaron en init(), ver comentario espejo ahi).
        // cada label de estos
        // 5 stats ahora lleva un simbolo - "\u2248" verde si ese stat
        // FUSIONA con el numero base del item (mismo UUID fijo vanilla que
        // usa KubeJSItemAttrExporter#addAttr para attack_damage/attack_speed,
        // ver su Javadoc), "+" naranja si se va a mostrar como modifier
        // EXTRA aparte (con signo) - mismo criterio de merge que ya usa
        // buildPreviewStack() para la vista previa de tooltip. Hover sobre
        // el label explica cual es cual (ver drawAttrLabelWithMode()).
        drawAttrLabelWithMode(g, mx, my, Lang.get("gui.kve.item.atk_damage"),   lx, ly.row() + 2, 90, c1, ATK_DMG_ID);
        drawAttrLabelWithMode(g, mx, my, Lang.get("gui.kve.item.atk_speed"),    lx, ly.row() + 2, 90, c1, ATK_SPD_ID);
        drawAttrLabelWithMode(g, mx, my, Lang.get("gui.kve.item.armor"),       lx, ly.row() + 2, 90, c1, ARMOR_ID);
        drawAttrLabelWithMode(g, mx, my, Lang.get("gui.kve.item.armor_tough"), lx, ly.row() + 2, 90, c1, ARMOR_TOUGH_ID);
        drawAttrLabelWithMode(g, mx, my, Lang.get("gui.kve.item.move_speed"),  lx, ly.row() + 2, 90, c1, MOVE_SPD_ID); ly.gap(4);

        ly.gap(GAP);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.item.custom_attrs") + " (" + draftAttributes.size() + "):", lx, ly.y() + 2, 230, KveGuiStyle.th(0xFFE8C96A));
        ly.gap(KveGuiStyle.BTN_H);
        int ay = ly.y();
        var entryList = new ArrayList<>(draftAttributes.values());
        int ax = Math.min(L + LIST_W + 100, L + effW() - INFO_W - FW - 14);
        int boxX1 = lx - 3, boxX2 = customRowRight() + 3;
        int boxY1 = ay - 2;
        int boxRows = visibleCustomRows(ay);
        int boxY2 = ay + boxRows * 12 + 2;
        int customRowsVisible = customContentRows(ay);
        KveGuiStyle.plate(g, boxX1, boxY1, boxX2, boxY2, KveGuiStyle.BG_FIELD, KveGuiStyle.BG_FIELD, KveGuiStyle.BORDER_OUTER);
        if (entryList.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.item.custom_attrs_empty"), lx + 2, ay + 2, boxX2 - boxX1 - 4, KveGuiStyle.th(0xFF556677));
        }
        for (int i = 0; i < customRowsVisible; i++) {
            int idx = i + customAttrScroll;
            if (idx >= entryList.size()) break;
            KubeJSItemAttrExporter.AttrMod m = entryList.get(idx);
            int ry = ay + i * 12;
            String opSuffix = switch (m.op) {
                case ADDITION -> "";
                case MULTIPLY_BASE -> " (%base)";
                case MULTIPLY_TOTAL -> " (%total)";
            };
            // Mismo edge case real que addAttr() ya documenta: un atributo
            // custom con attrId literal "minecraft:generic.attack_damage"/
            // "attack_speed" TAMBIEN fusiona (UUID fijo vanilla) - no es
            // exclusivo de los 2 campos core de arriba.
            String label = m.attrId + " = " + fmt(m.value) + opSuffix;
            int rowW = customEditX() - lx - 4;
            int textW = rowW - 13;
            String shownLabel = KveGuiStyle.truncate(font, label, Math.max(10, textW));
            KveGuiStyle.drawTextSafe(g, shownLabel, lx, ry + 2, textW, c2);
            drawModeToggle(g, mx, my, lx + Math.min(font.width(shownLabel), textW) + 4, ry + 2, m.attrId);
            g.fill(lx, ry + 12 - 1, lx + rowW, ry + 12, KveGuiStyle.BORDER_SUBTLE);
        }
        if (entryList.size() > customRowsVisible) {
            KveGuiStyle.drawTextSafe(g, (customAttrScroll + 1) + "-" +
                    Math.min(entryList.size(), customAttrScroll + customRowsVisible) + "/" + entryList.size(),
                    lx, ay + customRowsVisible * 12 + 2, 230, KveGuiStyle.th(0xFF556677));
        }
    }

    private void drawFieldHints(GuiGraphics g) {
        if (selectedItem == null) return;
        int ax = Math.min(L + LIST_W + 100, L + effW() - INFO_W - FW - 14);
        KveGuiStyle.Layout ly = new KveGuiStyle.Layout(ax, T + 16, GAP);
        int hintX = ax + FW + 3;
        int maxWidth = (L + effW() - INFO_W - 4) - hintX;

        KveGuiStyle.drawFieldHint(g, fMaxDamage.getValue(),
                vanillaMaxDamage > 0 ? String.valueOf(vanillaMaxDamage) : null, hintX, ly.row(), maxWidth);
        KveGuiStyle.drawFieldHint(g, fMaxStack.getValue(),
                String.valueOf(vanillaMaxStack), hintX, ly.row(), maxWidth);
        ly.row();
        ly.row();
        KveGuiStyle.drawFieldHint(g,
                Lang.get(draftFire ? "gui.kve.item.fire_yes" : "gui.kve.item.fire_no"),
                Lang.get(vanillaFireResistant ? "gui.kve.item.fire_yes" : "gui.kve.item.fire_no"),
                hintX, ly.row(), maxWidth);
        ly.gap(4);

        KveGuiStyle.drawFieldHint(g, fAtkDmg.getValue(),
                !Double.isNaN(vanillaAtkDmg) ? fmt(toEffectiveSpeed(vanillaAtkDmg, vanillaAtkDmgOp, ATTACK_DAMAGE_BASE)) : null, hintX, ly.row(), maxWidth);
        KveGuiStyle.drawFieldHint(g, fAtkSpd.getValue(),
                !Double.isNaN(vanillaAtkSpd) ? fmt(toEffectiveSpeed(vanillaAtkSpd, vanillaAtkSpdOp, ATTACK_SPEED_BASE)) : null, hintX, ly.row(), maxWidth);
        KveGuiStyle.drawFieldHint(g, fArmor.getValue(),
                !Double.isNaN(vanillaArmor) ? fmt(vanillaArmor) : null, hintX, ly.row(), maxWidth);
        KveGuiStyle.drawFieldHint(g, fArmorTough.getValue(),
                !Double.isNaN(vanillaArmorTough) ? fmt(vanillaArmorTough) : null, hintX, ly.row(), maxWidth);
        KveGuiStyle.drawFieldHint(g, fMoveSpd.getValue(),
                !Double.isNaN(vanillaMoveSpd) ? fmt(toEffectiveSpeed(vanillaMoveSpd, vanillaMoveSpdOp, MOVEMENT_SPEED_BASE)) : null, hintX, ly.row(), maxWidth);
    }

    /** Filas de la lista de modificados: las que entran en el alto libre del panel derecho. */
    private int savedRowsVisible() {
        int top = infoButtonsBottomY + 19, bottom = T + effH() - 14;
        // nunca mas filas de las que entran: con un minimo fijo la lista se salia del Studio
        return Math.max(1, (bottom - top) / 14);
    }

    private void drawInfoPanel(GuiGraphics g) {
        int rx = L + effW() - INFO_W + 4, ry = T + 16;

        int iconScale = infoIconSize / 16;
        int labelX = rx + infoIconSize + 6;
        int labelW = Math.max(40, (rx + INFO_W - 8) - labelX);

        if (selectedItem == null) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.item.select_hint_l1"), rx, ry + 4,  INFO_W - 8, KveGuiStyle.th(0xFF445566));
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.item.select_hint_l2"), rx, ry + 14, INFO_W - 8, KveGuiStyle.th(0xFF445566));
        } else {

            g.pose().pushPose();
            g.pose().translate(rx, ry, 0);
            g.pose().scale((float) iconScale, (float) iconScale, 1f);
            try { g.renderItem(RegistryIndex.stack(selectedItem), 0, 0); } catch (Throwable ignored) {}
            g.pose().popPose();

            String name = KveGuiStyle.truncate(font, RegistryIndex.name(selectedItem), labelW);
            KveGuiStyle.drawTextSafe(g, name, labelX, ry, labelW, KveGuiStyle.TEXT_PRIMARY);

            ResourceLocation k = BuiltInRegistries.ITEM.getKey(selectedItem);
            if (k != null) {
                KveGuiStyle.drawTextSafe(g, k.getNamespace() + ":" + k.getPath(), labelX, ry + 10, labelW, KveGuiStyle.th(0xFF5A8AAA));
            }
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.item.dur_base") + " "
                    + new ItemStack(selectedItem).getMaxDamage(), labelX, ry + 20, labelW, 0xFF4A6677);
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.item.stack_base") + " "
                    + new ItemStack(selectedItem).getMaxStackSize(), labelX, ry + 30, labelW, 0xFF4A6677);
        }

        int sy = infoButtonsBottomY + 6;
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.item.changed_list") + " (" + savedList.size() + "):", rx, sy, INFO_W - 8, KveGuiStyle.th(0xFFE8C96A));
        sy += 10;

        g.hLine(rx, rx + INFO_W - 8, sy, KveGuiStyle.th(0xFF2A4A6A));
        sy += 3;

        if (savedList.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.common.none"), rx, sy + 2, INFO_W - 8, KveGuiStyle.th(0xFF445566));
        } else {
            int visRows = savedRowsVisible();
            int scroll  = savedScrollBar.getScroll();
            int vis     = Math.min(visRows, savedList.size() - scroll);
            String selId = selectedItem != null ? RegistryIndex.id(selectedItem) : "";
            for (int i = 0; i < vis; i++) {
                var entry = savedList.get(i + scroll);
                int iy = sy + i * 14;
                boolean isSel = !selId.isEmpty() && selId.equals(entry.itemId);

                Item rowItem = null;
                try {
                    Item chk = BuiltInRegistries.ITEM.get(ResourceLocation.parse(entry.itemId));
                    if (chk != Items.AIR) rowItem = chk;
                } catch (Exception ignored) {}
                boolean exists = rowItem != null;

                g.fill(rx, iy, rx + INFO_W - 10, iy + 13,
                        isSel ? KveGuiStyle.BORDER_FIELD : exists ? KveGuiStyle.BG_FIELD : 0xFF2A1500);

                if (exists) {
                    try { g.renderItem(RegistryIndex.stack(rowItem), rx, iy - 1); } catch (Throwable ignored) {}
                } else {

                    g.fill(rx + 2, iy + 1, rx + 14, iy + 13, 0xFF442200);
                    KveGuiStyle.drawTextSafe(g, "?", rx + 5, iy + 2, 8, 0xFFFF8844);
                }

                String n = entry.itemId.contains(":") ? entry.itemId.split(":")[1] : entry.itemId;
                if (CraftEditorState.isFavoriteItem(entry.itemId)) n = "★" + n;
                int nameCol = !entry.enabled ? KveGuiStyle.th(0xFF667788)
                            : !exists        ? 0xFFFF8844
                            : isSel          ? 0xFFFFEE88
                                             : KveGuiStyle.TEXT_LABEL;
                KveGuiStyle.drawTextSafe(g, n, rx + 18, iy + 3, INFO_W - 38, nameCol);
                if (!entry.enabled) KveGuiStyle.drawTextSafe(g, "\u26A0", rx + INFO_W - 20, iy + 3, 12, KveGuiStyle.th(0xFF667788));
            }
            savedScrollBar.render(g);
        }
    }

    private void drawGridTooltip(GuiGraphics g, int mx, int my) {
        int scroll = itemScrollBar.getScroll(), startX = L + 2, startY = T + 30;
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int idx = (row + scroll) * COLS + col;
                if (idx >= filtered.size()) break;
                int sx = startX + col * SLOT, sy = startY + row * SLOT;
                if (mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16) {
                    g.renderTooltip(font, RegistryIndex.stack(filtered.get(idx)), mx, my);
                    return;
                }
            }
        }
        if (nav.showCursorTooltip() && nav.cursor() < filtered.size() && mx < L + effW() - INFO_W) {
            int crow = nav.cursor() / COLS - scroll, ccol = nav.cursor() % COLS;
            if (crow >= 0 && crow < ROWS) {
                g.renderTooltip(font, RegistryIndex.stack(filtered.get(nav.cursor())),
                        startX + ccol * SLOT + 18, startY + crow * SLOT + 12);
            }
        }
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        Screen creator = activeItemCreator();
        if (creator != null) return creator.charTyped(codePoint, modifiers);
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        // un menu "Mas" abierto recibe el clic antes que lo que tenga debajo
        if (com.kve.client.gui.KveMenuButton.consumeClick(mx, my, btn)) return true;
        Screen creator = activeItemCreator();
        if (creator != null) return creator.mouseClicked(mx, my, btn);
        if (listResize.mouseClicked(mx, my)) return true;
        if (itemScrollBar.mouseClicked(mx, my, btn)) return true;
        if (savedScrollBar != null && savedScrollBar.mouseClicked(mx, my, btn)) return true;

        if (btn == 0 && selectedItem != null && !readOnlyMode) {
            for (var hb : modeHitboxes) {
                int[] r = hb.getValue();
                if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                    toggleAttrMode(hb.getKey());
                    return true;
                }
            }
        }

        if (mx >= L + effW() - INFO_W + 4 && mx < L + effW() - 10) {
            int sy = infoButtonsBottomY + 19;
            int scroll = savedScrollBar != null ? savedScrollBar.getScroll() : 0;
            for (int i = 0; i < Math.min(savedRowsVisible(), savedList.size() - scroll); i++) {
                int iy = sy + i * 14;
                if (my >= iy && my < iy + 13) {
                    var entry = savedList.get(i + scroll);
                    try {
                        var rl = ResourceLocation.parse(entry.itemId);
                        var item = BuiltInRegistries.ITEM.get(rl);
                        if (item != net.minecraft.world.item.Items.AIR) {
                            orphanedItemId = null;
                            selectItem(item);
                            refresh();
                        } else {

                            orphanedItemId = entry.itemId;
                            selectedItem = null;
                            flash("§c" + entry.itemId + " §r" + Lang.get("gui.kve.item.orphan_note"));
                        }
                    } catch (Exception ignored) {}
                    return true;
                }
            }
        }
        int scroll = itemScrollBar.getScroll(), startX = L + 2, startY = T + 30;
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int idx = (row + scroll) * COLS + col;
                if (idx >= filtered.size()) break;
                int sx = startX + col * SLOT, sy = startY + row * SLOT;
                if (mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16) {
                    nav.setCursor(idx, filtered.size(), COLS, ROWS, itemScrollBar);
                    selectItem(filtered.get(idx));
                    refresh();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        Screen creator = activeItemCreator();
        if (creator != null) return creator.mouseDragged(mx, my, btn, dx, dy);
        if (listResize.mouseDragged(mx)) { refresh(); return true; }
        if (listResize.isDragging()) return true;
        if (itemScrollBar.mouseDragged(mx, my, btn, dx, dy)) return true; if (savedScrollBar != null && savedScrollBar.mouseDragged(mx, my, btn, dx, dy)) return true; return super.mouseDragged(mx, my, btn, dx, dy); }
    @Override public boolean mouseReleased(double mx, double my, int btn) {
        Screen creator = activeItemCreator();
        if (creator != null) return creator.mouseReleased(mx, my, btn);
        listResize.mouseReleased(); itemScrollBar.mouseReleased(mx, my, btn); if (savedScrollBar != null) savedScrollBar.mouseReleased(mx, my, btn); return super.mouseReleased(mx, my, btn); }
    @Override public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        Screen creator = activeItemCreator();
        if (creator != null) return creator.mouseScrolled(mx, my, scrollX, delta);
        if (mx >= L + LIST_W && mx < L + effW() - INFO_W) {
            int maxScroll = Math.max(0, draftAttributes.size() - customContentRows(customListTop()));
            customAttrScroll = Math.max(0, Math.min(maxScroll, customAttrScroll - (int) Math.signum(delta)));
            return true;
        }
        if (mx >= L && mx < L + LIST_W) { itemScrollBar.mouseScrolled(delta); return true; }
        if (mx >= L + effW() - INFO_W && savedScrollBar != null) { savedScrollBar.mouseScrolled(delta); return true; }
        return super.mouseScrolled(mx, my, scrollX, delta);
    }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        Screen creator = activeItemCreator();
        if (creator != null) return creator.keyPressed(key, scan, mods);
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (pendingDuplicate) { pendingDuplicate = false; flash(Lang.get("gui.kve.item.duplicate_cancelled")); return true; }
            onClose(); return true;
        }
        if (getFocused() == null || getFocused() == searchBox) {
            nav.clamp(filtered.size());
            if (nav.keyPressed(key, filtered.size(), COLS, ROWS, itemScrollBar)) return true;
            if (ListKeyNav.isConfirm(key) && nav.active()) { pickCursorItem(); return true; }
        }
        if (getFocused() != null && getFocused().keyPressed(key, scan, mods)) return true;
        if ((mods & GLFW.GLFW_MOD_CONTROL) != 0 && key == GLFW.GLFW_KEY_D) { duplicateItem(); return true; }
        if (EditorKeys.isSave(key, mods)) { saveItem(); return true; }
        if (EditorKeys.isFocusTab(key, mods)) { super.keyPressed(key, scan, mods); return true; }
        return EditorKeys.swallow(mods); }

    private void pickCursorItem() {
        int idx = nav.cursor();
        if (idx < 0 || idx >= filtered.size()) return;
        selectItem(filtered.get(idx));
        refresh();
    }
    @Override public boolean isPauseScreen() { return false; }
}
