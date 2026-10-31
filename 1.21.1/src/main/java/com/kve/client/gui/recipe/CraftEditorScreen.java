package com.kve.client.gui.recipe;

import com.kve.client.gui.recipe.MachineRecipeEditScreen;
import com.kve.client.gui.DiffPreviewScreen;
import com.kve.client.gui.EditorKeys;
import com.kve.client.gui.picker.ItemPickerScreen;
import com.kve.client.gui.settings.KveClientSettings;
import com.kve.client.gui.KveConfirmScreen;
import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KvePlateButton;
import com.kve.client.gui.KveScaledEditBox;
import com.kve.client.gui.MiniItemIcon;
import com.kve.client.gui.picker.NbtEditorScreen;
import com.kve.client.gui.tag.TagChoiceScreen;

import com.kve.client.recipe.RecentItems;
import com.kve.client.recipe.RecipeDraftActions;
import com.kve.client.recipe.RecipeListFilter;
import com.kve.client.recipe.StationResolver;
import com.kve.common.recipe.GridOps;
import com.kve.common.recipe.RecipeGridRules;
import com.kve.common.recipe.RecipeHelpers;
import com.kve.common.recipe.RecipeTemplates;
import com.kve.common.recipe.TagIngredient;
import org.lwjgl.glfw.GLFW;
import com.kve.common.Lang;
import com.kve.client.permission.ClientPermissionState;
import com.kve.common.config.KVEConfig;

import com.kve.common.kubejs.KubeJSExporter;
import com.kve.common.persist.CraftEditorState;
import com.kve.client.gui.ScrollBar;
import com.kve.common.recipe.RecipeData;
import com.kve.client.gui.workspace.pending.PendingDomain;
import com.kve.client.gui.workspace.pending.PendingChange;
import com.kve.client.gui.workspace.pending.PendingChangeManager;
import com.kve.client.gui.studio.KveStudioShell;
import com.kve.client.gui.studio.KveStudioTab;
import com.kve.client.gui.history.KveChangeLog;
import com.kve.client.gui.history.KveChangeHistoryScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class CraftEditorScreen extends Screen implements KveStudioTab {

    private static final int GUI_W_MIN = 620;
    private int GUI_W = GUI_W_MIN;
    private static final int BOTTOM_ROW_OFF = 24;
    private static final int GUI_H_DEFAULT   = 280;
    private int GUI_H = GUI_H_DEFAULT;
    private static final int LIST_W  = 115;
    private static final int GRID_OX = 8;
    private static final int GRID_OY = 89;
    private static final int GRID_PX = 162;
    private static final int MIN_SLOT_PX = 10;

    private static final int ROW_H     = 16;

    private enum Tab { RECIPES, REMOVE }
    private Tab activeTab = Tab.RECIPES;

    private int L, T;

    private RecipeData editing = null;
    private final List<RecipeData> recipeList = new ArrayList<>();
    private ScrollBar recipeScrollBar;
    private String searchFilter = "";

    private ItemStack[] gridSlots = new ItemStack[9];
    private ItemStack outputSlot  = ItemStack.EMPTY;
    private int gridW = 3, gridH = 3;

    private final NodeCanvasWidget nodeCanvas = new NodeCanvasWidget();

    private ItemStack secondaryOutputSlot  = ItemStack.EMPTY;
    private int       secondaryChancePct   = 50;

    private EditBox outputCountBox;

    private ItemStack combinationCore = ItemStack.EMPTY;
    private final List<ItemStack> pedestals = new ArrayList<>();

    private RecipeData.RecipeType selType  = RecipeData.RecipeType.VANILLA_SHAPED;
    private RecipeData.ECTableTier selTier = RecipeData.ECTableTier.BASIC;
    private String selCustomTypeId = "";
    private EditBox fCustomExtraJson;

    private long ecPower = 1_000_000L;
    private int  ecTime  = 200;
    private long rfCost  = 50_000L;
    private long rfRate  = 1_000L;

    private final CraftRemoveTab removeTab = new CraftRemoveTab(new CraftRemoveTab.Host() {
        @Override public void flash(String msg) { CraftEditorScreen.this.flash(msg); }
        @Override public void refreshScreen() { refresh(); }
        @Override public void openItemPicker(java.util.function.Consumer<ItemStack> onPick) {
            minecraft.setScreen(new ItemPickerScreen(CraftEditorScreen.this, onPick));
        }
        @Override public void openRecipeBrowser() {
            minecraft.setScreen(new RecipeBrowserScreen(CraftEditorScreen.this));
        }
        @Override public net.minecraft.client.gui.Font font() { return font; }
    });

    private EditBox recipeIdBox;
    private EditBox searchBox;

    private String savedMsg = "";
    private long   savedAt  = 0L;

    private String pendingRemovalId = null;
    private RecipeData pendingLoad = null;

    private boolean draftEnabled = true;

    private String draftCategory = "";
    private String draftNotes = "";
    private final com.kve.common.util.UndoStack<RecipeData> undoStack = new com.kve.common.util.UndoStack<>(50);
    private ItemStack brush = ItemStack.EMPTY;
    private RecipeListFilter.Sort listSort = RecipeListFilter.Sort.DEFAULT;

    private Screen returnScreen = null;

    // KveStudioShell migration (, "Recipes" tab - mismo patron
    // que LootTableEditorScreen/TagEditorScreen): true mientras esta tab
    // vive DENTRO de un KveStudioShell (asignada via el overload
    // KveStudioTab#init(x,y,w,h)), false en modo standalone (init sin
    // argumentos). hostedW guarda el ultimo ancho que el shell asigno, para
    // que refresh (ver mas abajo) pueda recalcular GUI_W sin volver a
    // pedirselo al shell.
    private boolean hosted = false;
    private int hostedW = 0;
    private int hostedH = 0;

    private boolean machineMode = false;
    private MachineRecipeEditScreen embeddedMachine = null;
    // Rectangulo que buildWidgets() le asigno a embeddedMachine la ultima
    // vez - mouseClicked()/mouseDragged()/mouseScrolled() lo usan para decidir si
    // un evento de mouse va al editor embebido o a la lista/browser de esta
    // misma pantalla (que sigue interactiva al lado, igual que en el HTML).
    private int machineAreaX, machineAreaY, machineAreaW, machineAreaH;

    private boolean pendingListenerRegistered = false;
    private final PendingChangeManager.Listener pendingListener = this::onPendingQueueChanged;

    private void onPendingQueueChanged() {
        if (minecraft != null && minecraft.screen == this) init();
    }

    public CraftEditorScreen() {
        super(Lang.c("gui.kve.title"));
        resetAllSlots();
    }

    public CraftEditorScreen(RecipeData data, String originalIdToRemove) {
        super(Lang.c("gui.kve.title"));
        resetAllSlots();
        this.pendingLoad = data;
        this.pendingRemovalId = originalIdToRemove;
    }

    /** Fluent setter so callers can send "Volver" back to a specific screen (defaults to KveStudioShell). */
    public CraftEditorScreen withReturnScreen(Screen s) {
        this.returnScreen = s;
        return this;
    }

    public Screen returnScreen() { return returnScreen; }

    /** Ancho minimo que necesita alojado (cambia con el modo y el tipo); valido despues de init. */
    public int minHostedWidth() { return font == null ? GUI_W_MIN : computeMinGuiW(); }

    public void loadExternalRecipe(RecipeData data, String originalIdToRemove) {
        this.pendingLoad = data;
        this.pendingRemovalId = originalIdToRemove;
    }

    private void openRecipeConflicts() {
        com.kve.client.gui.ConflictsScreen screen = new com.kve.client.gui.ConflictsScreen(this, Lang.c("gui.kve.conflict.recipes_title"),
                () -> com.kve.common.conflict.ConflictDetector.recipeConflicts(KubeJSExporter.getAddedRecipes()),
                id -> KubeJSExporter.getAddedRecipes().stream().filter(r -> r.recipeId.equals(id)).findFirst()
                        .ifPresent(r -> openRecipe(r, null)))
                .withRecipes(id -> KubeJSExporter.getAddedRecipes().stream().filter(r -> r.recipeId.equals(id)).findFirst().orElse(null),
                        ids -> RecipeMultiEditScreen.open(this, ids))
                .withOutputTab(() -> com.kve.common.conflict.ConflictDetector.recipeOutputGroups(KubeJSExporter.getAddedRecipes()));
        minecraft.setScreen(conflictCount == 0 && outputGroupCount > 0 ? screen.startOnOutputs() : screen);
    }

    private void openRecipeTree() {
        // en modo maquina el grafo sale del borrador del editor de maquina, no de la grilla normal
        boolean fromMachine = machineMode && embeddedMachine != null;
        ItemStack source = fromMachine ? embeddedMachine.primaryOutputItem() : outputSlot;
        ItemStack root = source != null && !source.isEmpty() ? source.copy() : ItemStack.EMPTY;
        if (root.isEmpty() && !fromMachine && !RecipeGraphScreen.lastRoot().isEmpty()) {
            minecraft.setScreen(new RecipeGraphScreen(this, RecipeGraphScreen.lastRoot(), RecipeGraphScreen.lastUsesMode()));
        } else if (root.isEmpty()) {
            minecraft.setScreen(new ItemPickerScreen(this, s -> minecraft.tell(() -> minecraft.setScreen(new RecipeGraphScreen(this, s, false)))));
        } else {
            minecraft.setScreen(new RecipeGraphScreen(this, root, false));
        }
    }

    private void doOpenChangeHistory() {
        minecraft.setScreen(new KveChangeHistoryScreen(this, KveChangeLog.Log.RECIPES,
                Lang.c("gui.kve.change_history.title", Lang.get("gui.kve.change_history.domain.recipes")),
                id -> {
                    RecipeData found = KubeJSExporter.getAddedRecipes().stream()
                            .filter(r -> r.recipeId.equals(id)).findFirst().orElse(null);
                    minecraft.setScreen(this);
                    if (found != null) openRecipe(found, null);
                    else switchTab(Tab.RECIPES);
                }).withIcons(id -> {
                    RecipeData r = KubeJSExporter.getAddedRecipes().stream().filter(x -> x.recipeId.equals(id)).findFirst().orElse(null);
                    if (r != null && r.output != null && !r.output.isEmpty()) return r.output.getItem();
                    return MiniItemIcon.item(id);
                }));
    }

    private void goBack() {
        if (KveStudioShell.exitIfHosted(this, returnScreen)) return;
        minecraft.setScreen(returnScreen != null ? returnScreen : new KveStudioShell());
    }

    /** Counterpart to the registration in init - see pendingListenerRegistered's comment above. Screen#removed runs exactly once, when Minecraft actually swaps away from this screen (unlike init, which reruns on every tab switch). */
    @Override
    public void removed() {
        PendingChangeManager.removeListener(pendingListener);
        pendingListenerRegistered = false;
        super.removed();
    }

    private void initGrid() {
        gridSlots = new ItemStack[Math.max(1, gridW * gridH)];
        for (int i = 0; i < gridSlots.length; i++) gridSlots[i] = ItemStack.EMPTY;
    }

    private void resetAllSlots() {
        initGrid();
        outputSlot = ItemStack.EMPTY;
        secondaryOutputSlot = ItemStack.EMPTY;
        secondaryChancePct = 50;
        combinationCore = ItemStack.EMPTY;
        pedestals.clear();
        for (int i = 0; i < 48; i++) pedestals.add(ItemStack.EMPTY);
    }

    private int guiHFloor() { return GUI_H_DEFAULT + (isVariableGridCustomJson() ? 14 : 0); }

    private int computeMinGuiW() {
        if (machineMode || isCombo()) return GUI_W_MIN;

        int ex0 = LIST_W + GRID_OX; // = gox() - L, sin depender de GUI_W (evita la circularidad de outX()/gox())
        int contentEnd = ex0;

        String craftModeLbl = Lang.get("gui.kve.recipe.mode.craft");
        String machineModeLbl = Lang.get("gui.kve.recipe.machine");
        contentEnd = Math.max(contentEnd, ex0 + KveGuiStyle.btnWidth(craftModeLbl) + KveGuiStyle.BTN_GAP + KveGuiStyle.btnWidth(machineModeLbl));
        contentEnd = Math.max(contentEnd, ex0 + 185 + KveGuiStyle.btnWidth(otherModsButtonLabel()));

        int tx = ex0;
        tx += KveGuiStyle.btnWidth(Lang.get("gui.kve.recipe.type.shaped")) + KveGuiStyle.BTN_GAP;
        tx += KveGuiStyle.btnWidth(Lang.get("gui.kve.recipe.type.shapeless")) + KveGuiStyle.BTN_GAP;
        contentEnd = Math.max(contentEnd, tx);

        if (KVEConfig.isExtendedCraftingActive() && isEcTableType()) {
            int ttx = ex0;
            ttx += KveGuiStyle.btnWidth(Lang.get("gui.kve.recipe.tier.basic")) + KveGuiStyle.BTN_GAP;
            ttx += KveGuiStyle.btnWidth(Lang.get("gui.kve.recipe.tier.advanced")) + KveGuiStyle.BTN_GAP;
            ttx += KveGuiStyle.btnWidth(Lang.get("gui.kve.recipe.tier.elite")) + KveGuiStyle.BTN_GAP;
            ttx += KveGuiStyle.btnWidth(Lang.get("gui.kve.recipe.tier.ultimate"));
            contentEnd = Math.max(contentEnd, ttx);
        }

        if (isCustomMod()) {
            contentEnd = Math.max(contentEnd, ex0 + 210);
            if (pendingAddonToggleLabel != null) {
                contentEnd = Math.max(contentEnd, ex0 + 225 + 80);
            }
        }

        int gridPxW = isNodeCanvas() ? GRID_PX : (gridW > 0 ? gridW * 18 : GRID_PX);
        contentEnd = Math.max(contentEnd, ex0 + gridPxW + 22 + 44 + 16);

        int contentMin = contentEnd + 6;
        int headerLeftEnd = 232;

        if (hosted) return Math.max(headerLeftEnd, contentMin);

        // Standalone: header tambien trae "Aplicar (N)"/"Descartar (N)" ancladas al borde derecho -
        // mismo calculo de discardX/applyX que buildWidgets() ya hace, para que este piso nunca
        // termine mas angosto que lo que ese bloque realmente necesita.
        int pendCount = PendingChangeManager.size();
        String applyLbl = pendCount > 0 ? Lang.get("gui.kve.pending.apply") + " (" + pendCount + ")" : Lang.get("gui.kve.pending.apply");
        String discardLbl = pendCount > 0 ? Lang.get("gui.kve.pending.discard") + " (" + pendCount + ")" : Lang.get("gui.kve.pending.discard");
        int rightBlockW = 76 + 4 + KveGuiStyle.btnWidth(discardLbl) + KveGuiStyle.BTN_GAP + KveGuiStyle.btnWidth(applyLbl);
        int headerMin = headerLeftEnd + 8 + rightBlockW;
        return Math.max(headerMin, contentMin);
    }

    @Override
    protected void init() {
        // (mismo patron que LootTableEditorScreen/
        // TagEditorScreen - ver KveStudioShell#reclaimIfHosted() Javadoc):
        // si esto se esta reabriendo porque un picker interno hizo
        // minecraft.setScreen(this) para "volver" y esta tab sigue siendo
        // la tab activa de un KveStudioShell vivo, reabrir el shell en vez
        // de reconstruirse standalone/centrada.
        if (KveStudioShell.reclaimIfHosted(this, returnScreen)) return;
        if (!pendingListenerRegistered) {
            PendingChangeManager.addListener(pendingListener);
            pendingListenerRegistered = true;
        }
        hosted = false;
        GUI_W = computeMinGuiW();
        GUI_H = guiHFloor();
        L = Math.max(0, (width  - GUI_W) / 2);
        T = Math.max(0, (height - GUI_H) / 2);
        buildWidgets();
    }

    // KveStudioShell migration - overload piloto identico al que
    // LootTableEditorScreen/TagEditorScreen ya tienen: reemplaza SOLO el
    // origen de coordenadas (L/T/GUI_W) por el rectangulo que el shell
    // asigna a esta tab. El resto de buildWidgets() no se toca (ya es
    // relativo a L/T/GUI_W/LIST_W, nunca a width/height de Screen). El
    // init de Screen de arriba queda intacto para el modo standalone.
    @Override
    public void init(int x0, int y0, int w, int h) {
        if (this.minecraft == null) {
            this.minecraft = Minecraft.getInstance();
            this.font = this.minecraft.font;
        }
        if (!pendingListenerRegistered) {
            PendingChangeManager.addListener(pendingListener);
            pendingListenerRegistered = true;
        }
        hosted = true;
        hostedW = w;
        hostedH = h;
        GUI_W = Math.max(w, computeMinGuiW());
        GUI_H = Math.max(h, guiHFloor());
        L = x0;
        T = y0;
        buildWidgets();
    }

    private void refresh() {
        GUI_W = hosted ? Math.max(hostedW, computeMinGuiW()) : computeMinGuiW();
        GUI_H = hosted ? Math.max(hostedH, guiHFloor()) : guiHFloor();
        buildWidgets();
    }

    public void refreshHostedChrome() {
        if (hosted) refresh();
        else init();
    }

    private void exitMachineMode() {
        machineMode = false;
        refresh();
    }

    private void buildWidgets() {
        String snapRecipeId = recipeIdBox != null ? recipeIdBox.getValue() : null;

        clearWidgets();
        setFocused(null);

        Button tabRecipesBtn = addRenderableWidget(btn(Lang.get("gui.kve.tab.recipes"), L + 2,  T + 2, 0, 11, () -> switchTab(Tab.RECIPES)));
        Button tabRemoveBtn  = addRenderableWidget(btn(Lang.get("gui.kve.tab.remove"),  L + 60, T + 2, 0, 11, () -> switchTab(Tab.REMOVE)));
        tabRecipesBtn.active = activeTab != Tab.RECIPES;
        tabRemoveBtn.active  = activeTab != Tab.REMOVE;

        int pendCount = PendingChangeManager.size();
        if (!hosted) {
            String applyLbl = pendCount > 0 ? Lang.get("gui.kve.pending.apply") + " (" + pendCount + ")" : Lang.get("gui.kve.pending.apply");
            String discardLbl = pendCount > 0 ? Lang.get("gui.kve.pending.discard") + " (" + pendCount + ")" : Lang.get("gui.kve.pending.discard");
            int discardW = KveGuiStyle.btnWidth(discardLbl);
            int applyW = KveGuiStyle.btnWidth(applyLbl);
            int discardX = L + GUI_W - 76 - 4 - discardW;
            int applyX = discardX - KveGuiStyle.BTN_GAP - applyW;
            Button applyPendBtn = addRenderableWidget(btn(applyLbl, applyX, T + 2, 0, 11, this::onApplyPending));
            applyPendBtn.active = pendCount > 0 && ClientPermissionState.canApply();
            Button discardPendBtn = addRenderableWidget(btn(discardLbl, discardX, T + 2, 0, 11, this::onDiscardPending));
            discardPendBtn.active = pendCount > 0;
        }

        if (!hosted) {
            addRenderableWidget(KveGuiStyle.pendingChangesButton(L + 122, T + 2, 110, 11, this));
        }

        if (activeTab == Tab.RECIPES) initRecipes();
        else                          initRemove();

        if (snapRecipeId != null && recipeIdBox != null) recipeIdBox.setValue(snapRecipeId);

        if (recipeScrollBar == null) recipeScrollBar = new ScrollBar(L + LIST_W - 10, leftListTopY() + 1, listRows() * ROW_H, 0, listRows());
        removeTab.ensureScrollBars(L + LIST_W - 10, leftListTopY() + 1, listRows(),
                CraftRemoveTab.queueBarX(gox(), L + GUI_W - 2), removeListTopY(), queueRows());
        refreshRecipeList();
        removeTab.refreshLists();

        if (pendingLoad != null) {
            RecipeData toLoad = pendingLoad;
            pendingLoad = null;
            applyLoadedRecipe(toLoad);
            refresh();
        }
        if (draftBaseline == null && recipeIdBox != null && !machineMode && activeTab == Tab.RECIPES) markDraftClean();
    }

    private void switchTab(Tab t) { activeTab = t; refresh(); }

    /** ESC should behave like the "<- Volver" button here, not close the whole GUI. */
    @Override
    public void onClose() { goBack(); }

    /** X del boton "Aplicar" de la barra de arriba cuando el editor esta suelto (mismo calculo que buildWidgets). */
    private int standaloneApplyX() {
        int pendCount = PendingChangeManager.size();
        String applyLbl = pendCount > 0 ? Lang.get("gui.kve.pending.apply") + " (" + pendCount + ")" : Lang.get("gui.kve.pending.apply");
        String discardLbl = pendCount > 0 ? Lang.get("gui.kve.pending.discard") + " (" + pendCount + ")" : Lang.get("gui.kve.pending.discard");
        int discardX = L + GUI_W - 76 - 4 - KveGuiStyle.btnWidth(discardLbl);
        return discardX - KveGuiStyle.BTN_GAP - KveGuiStyle.btnWidth(applyLbl);
    }

    private void initRecipes() {
        int ex = L + LIST_W + GRID_OX;
        conflictCount = com.kve.common.conflict.ConflictDetector.recipeConflicts(KubeJSExporter.getAddedRecipes()).size();
        outputGroupCount = com.kve.common.conflict.ConflictDetector.recipeOutputGroups(KubeJSExporter.getAddedRecipes()).size();

        addRenderableWidget(KveGuiStyle.clampedBtn(L + 2, T + 16, LIST_W - 4, Lang.get("gui.kve.btn.new"), b -> confirmDiscardThen(this::startNew)));
        KvePlateButton existingBtn = KveGuiStyle.clampedBtn(L + 2, T + 30, LIST_W - 4, Lang.get("gui.kve.recipe.edit_existing_short"),
                b -> confirmDiscardThen(() -> minecraft.setScreen(new RecipeBrowserScreen(this)))).withPrimary(true);
        existingBtn.setTooltip(Tooltip.create(Lang.c("gui.kve.recipe.edit_existing_tooltip")));
        addRenderableWidget(existingBtn);

        // el editor se reconstruye al volver de cada picker: la lista no debe volver arriba
        int keepScroll = recipeScrollBar != null ? recipeScrollBar.getScroll() : 0;
        recipeScrollBar = new ScrollBar(L + LIST_W - 10, leftListTopY() + 1, listRows() * ROW_H, recipeList.size(), listRows());
        recipeScrollBar.setScroll(keepScroll);

        searchBox = new KveScaledEditBox(font, L + 2, T + 44, LIST_W - 22, 11, Component.literal(Lang.get("gui.kve.recipe.search_hint")));
        searchBox.setMaxLength(48);
        searchBox.setValue(searchFilter);
        searchBox.setResponder(s -> { searchFilter = s; refreshRecipeList(); });
        searchBox.setHint(Component.literal(Lang.get("gui.kve.recipe.search_hint")));
        searchBox.setTooltip(Tooltip.create(Lang.c("gui.kve.recipe.search_tooltip")));
        addRenderableWidget(searchBox);
        Button sortBtn = addRenderableWidget(btn("\u21C5", L + LIST_W - 18, T + 44, 16, 11, this::cycleListSort));
        sortBtn.setTooltip(Tooltip.create(Component.literal(Lang.get("gui.kve.recipe.sort." + listSort.name().toLowerCase(java.util.Locale.ROOT)))));

        // Fila A: modo y tipo de receta juntos. Fila B: id, deshacer/rehacer y recientes.
        int rowA = T + 16, rowB = T + 30, right = L + GUI_W - 4;
        String craftModeLbl = Lang.get("gui.kve.recipe.mode.craft");
        String machineModeLbl = Lang.get("gui.kve.recipe.machine");
        Button craftModeBtn = addRenderableWidget(btn(craftModeLbl, ex, rowA, 0, 12,
                () -> { if (machineMode) { machineMode = false; refresh(); } }));
        craftModeBtn.active = machineMode;
        int machineModeX = ex + KveGuiStyle.btnWidth(craftModeLbl) + KveGuiStyle.BTN_GAP;
        Button machineModeBtn = addRenderableWidget(btn(machineModeLbl, machineModeX, rowA, 0, 12,
                () -> { if (!machineMode) openMachineEditor(); }));
        machineModeBtn.active = !machineMode;
        int rowAEnd = machineModeX + KveGuiStyle.btnWidth(machineModeLbl);

        typeLabelX = -1;
        if (!machineMode) {
            typeLabelX = rowAEnd + 12;
            int tx = typeLabelX + font.width(Lang.get("gui.kve.recipe.type_prefix")) + 4;
            String shapedLbl = Lang.get("gui.kve.recipe.type.shaped");
            String shapelessLbl = Lang.get("gui.kve.recipe.type.shapeless");
            String otherLbl = otherModsButtonLabel();
            addRenderableWidget(KveGuiStyle.smartBtn(tx, rowA, 0, shapedLbl, b -> setType(RecipeData.RecipeType.VANILLA_SHAPED))
                    .withSelected(selType == RecipeData.RecipeType.VANILLA_SHAPED));
            tx += KveGuiStyle.btnWidth(shapedLbl) + KveGuiStyle.BTN_GAP;
            addRenderableWidget(KveGuiStyle.smartBtn(tx, rowA, 0, shapelessLbl, b -> setType(RecipeData.RecipeType.VANILLA_SHAPELESS))
                    .withSelected(selType == RecipeData.RecipeType.VANILLA_SHAPELESS));
            tx += KveGuiStyle.btnWidth(shapelessLbl) + KveGuiStyle.BTN_GAP;
            addRenderableWidget(KveGuiStyle.smartBtn(tx, rowA, 0, otherLbl, b -> openRecipeTypePicker())
                    .withSelected(isCustomMod() || isEcType()));
            rowAEnd = tx + KveGuiStyle.btnWidth(otherLbl);
        }

        // Herramientas de toda tu coleccion (no de la receta abierta): en la franja del titulo,
        // a la derecha, siempre en el mismo lugar y con la altura de las pestanias.
        String multiLbl = Lang.get("gui.kve.recipe.multi.btn"), treeLbl = Lang.get("gui.kve.recipe.tree.btn"),
                shareLbl = Lang.get("gui.kve.subset.share.btn");
        int toolsRight = hosted ? L + GUI_W - 4 : standaloneApplyX() - 8;
        int tx0 = toolsRight - KveGuiStyle.btnWidth(multiLbl) - KveGuiStyle.btnWidth(treeLbl) - KveGuiStyle.btnWidth(shareLbl)
                - KveGuiStyle.BTN_GAP * 2;
        int tx = tx0;
        Button multiBtn = addRenderableWidget(btn(multiLbl, tx, T + 2, 0, 11, () -> minecraft.setScreen(new RecipeSelectScreen(this))));
        multiBtn.setTooltip(Tooltip.create(Lang.c("gui.kve.recipe.multi.btn_tooltip")));
        multiBtn.active = KubeJSExporter.getAddedRecipes().size() >= 2;
        tx += KveGuiStyle.btnWidth(multiLbl) + KveGuiStyle.BTN_GAP;
        addRenderableWidget(btn(treeLbl, tx, T + 2, 0, 11, this::openRecipeTree))
                .setTooltip(Tooltip.create(Lang.c("gui.kve.recipe.tree.tooltip")));
        tx += KveGuiStyle.btnWidth(treeLbl) + KveGuiStyle.BTN_GAP;
        addRenderableWidget(btn(shareLbl, tx, T + 2, 0, 11, () -> minecraft.setScreen(new RecipeShareScreen(this))))
                .setTooltip(Tooltip.create(Lang.c("gui.kve.recipe.icon.share_desc")));
        // Los conflictos son entre tus recetas: el aviso va pegado debajo de esa lista.
        // tambien con solo recetas de mismo resultado: es informativo, sin el color de aviso
        if (conflictCount > 0) {
            KvePlateButton cBtn = new KvePlateButton(L + 2, conflictBtnY(), LIST_W - 4, CONFLICT_BTN_H,
                    Component.literal("⚠ " + Lang.get("gui.kve.conflict.button", conflictCount)), b -> openRecipeConflicts()).withWarning(true);
            cBtn.setTooltip(Tooltip.create(Lang.c("gui.kve.conflict.recipes_tooltip", conflictCount)));
            addRenderableWidget(cBtn);
        } else if (outputGroupCount > 0) {
            KvePlateButton cBtn = new KvePlateButton(L + 2, conflictBtnY(), LIST_W - 4, CONFLICT_BTN_H,
                    Component.literal(Lang.get("gui.kve.conflict.tab.outputs", outputGroupCount)), b -> openRecipeConflicts());
            cBtn.setTooltip(Tooltip.create(Lang.c("gui.kve.conflict.outputs_button_tip")));
            addRenderableWidget(cBtn);
        }
        int rowBLimit = right;

        recentsX = -1;
        if (!machineMode) {
            int idW = Math.max(90, Math.min(175, rowBLimit - ex - 40));
            recipeIdBox = new KveScaledEditBox(font, ex, rowB + 1, idW, 11, Lang.c("gui.kve.recipe_id"));
            recipeIdBox.setMaxLength(64);
            recipeIdBox.setHint(Component.literal(Lang.get("gui.kve.recipe.id_hint")));
            addRenderableWidget(recipeIdBox);
            int urx = ex + idW + 8;
            Button undoBtn = addRenderableWidget(btn("\u21B6", urx, rowB, 16, 12, this::undoGrid));
            undoBtn.active = undoStack.canUndo();
            undoBtn.setTooltip(Tooltip.create(Lang.c("gui.kve.recipe.undo_tooltip")));
            Button redoBtn = addRenderableWidget(btn("\u21B7", urx + 18, rowB, 16, 12, this::redoGrid));
            redoBtn.active = undoStack.canRedo();
            redoBtn.setTooltip(Tooltip.create(Lang.c("gui.kve.recipe.redo_tooltip")));
            int palX = urx + 36 + 12 + font.width(Lang.get("gui.kve.recipe.recents_label")) + 4;
            if (palX + RecentItems.MAX * 12 <= rowBLimit) recentsX = palX;
        }

        if (!machineMode) {
            KveGuiStyle.Layout ely = new KveGuiStyle.Layout(ex, T + 45, 14);
            int tierY = ely.row(14);
            ely.row(13);
            int cty = ely.y();

            if (KVEConfig.isExtendedCraftingActive() && isEcTableType()) {
                int ttx = ex;
                String basicLbl = Lang.get("gui.kve.recipe.tier.basic");
                String advancedLbl = Lang.get("gui.kve.recipe.tier.advanced");
                String eliteLbl = Lang.get("gui.kve.recipe.tier.elite");
                String ultimateLbl = Lang.get("gui.kve.recipe.tier.ultimate");
                addRenderableWidget(btn(basicLbl, ttx, tierY, 0, () -> setTier(RecipeData.ECTableTier.BASIC)));
                ttx += KveGuiStyle.btnWidth(basicLbl) + KveGuiStyle.BTN_GAP;
                addRenderableWidget(btn(advancedLbl, ttx, tierY, 0, () -> setTier(RecipeData.ECTableTier.ADVANCED)));
                ttx += KveGuiStyle.btnWidth(advancedLbl) + KveGuiStyle.BTN_GAP;
                addRenderableWidget(btn(eliteLbl, ttx, tierY, 0, () -> setTier(RecipeData.ECTableTier.ELITE)));
                ttx += KveGuiStyle.btnWidth(eliteLbl) + KveGuiStyle.BTN_GAP;
                addRenderableWidget(btn(ultimateLbl, ttx, tierY, 0, () -> setTier(RecipeData.ECTableTier.ULTIMATE)));
            }

            if (isCustomMod()) {
                boolean isSeqAssembly = "create:sequenced_assembly".equalsIgnoreCase(selCustomTypeId);
                int jsonBoxW = isSeqAssembly ? 140 : 210;
                fCustomExtraJson = new KveScaledEditBox(font, ex, cty, jsonBoxW, 11, Component.literal(Lang.get("gui.kve.recipe.extra_json_label")));
                fCustomExtraJson.setMaxLength(isSeqAssembly ? 4000 : 500);
                fCustomExtraJson.setValue(pendingExtraJsonToLoad != null ? pendingExtraJsonToLoad : "");
                fCustomExtraJson.setHint(Component.literal(Lang.get("gui.kve.recipe.extra_json_hint")));
                fCustomExtraJson.setResponder(v -> pendingExtraJsonToLoad = v);
                addRenderableWidget(fCustomExtraJson);
                if (isSeqAssembly) {
                    addRenderableWidget(new KvePlateButton(ex + jsonBoxW + 5, cty, 68, 11, Component.literal("Pasos..."),
                            b -> minecraft.setScreen(new SequencedAssemblyStepsScreen(this, fCustomExtraJson.getValue(),
                                    json -> { fCustomExtraJson.setValue(json); pendingExtraJsonToLoad = json; }))));
                }
                if (pendingAddonToggleLabel != null) {
                    addRenderableWidget(btn(KveGuiStyle.truncate(font, Lang.get("gui.kve.recipe.enable_addon", pendingAddonToggleLabel), 63),
                            ex + 225, cty, 0, this::enablePendingAddonFlag));
                }
            }

            if (isVariableGridCustomJson()) {
                int gy = gridResizeRowY();
                Button gridColDec = addRenderableWidget(btn("-", ex,       gy, 14, 12, () -> resizeGridManual(gridW - 1, gridH)));
                Button gridColInc = addRenderableWidget(btn("+", ex + 46,  gy, 14, 12, () -> resizeGridManual(gridW + 1, gridH)));
                Button gridRowDec = addRenderableWidget(btn("-", ex + 92,  gy, 14, 12, () -> resizeGridManual(gridW, gridH - 1)));
                Button gridRowInc = addRenderableWidget(btn("+", ex + 138, gy, 14, 12, () -> resizeGridManual(gridW, gridH + 1)));
                gridColDec.setTooltip(Tooltip.create(Lang.c("gui.kve.recipe.grid_col_dec_tooltip")));
                gridColInc.setTooltip(Tooltip.create(Lang.c("gui.kve.recipe.grid_col_inc_tooltip")));
                gridRowDec.setTooltip(Tooltip.create(Lang.c("gui.kve.recipe.grid_row_dec_tooltip")));
                gridRowInc.setTooltip(Tooltip.create(Lang.c("gui.kve.recipe.grid_row_inc_tooltip")));
            }

            if (!isCombo()) {
                int cox = outX(); int coy = outY() + 20;
                Button outDecBtn = addRenderableWidget(btn("-", cox, coy, 16, 12, () -> bumpOutputCount(-1)));
                outDecBtn.setTooltip(Tooltip.create(Lang.c("gui.kve.recipe.output_dec_tooltip")));
                outputCountBox = new KveScaledEditBox(font, cox + 18, coy, 24, 12, Component.literal(Lang.get("gui.kve.recipe.quantity_hint")));
                outputCountBox.setMaxLength(3);
                outputCountBox.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
                outputCountBox.setValue(String.valueOf(Math.max(1, outputSlot.getCount())));
                outputCountBox.setResponder(this::onOutputCountTyped);
                addRenderableWidget(outputCountBox);
                Button outIncBtn = addRenderableWidget(btn("+", cox + 44, coy, 16, 12, () -> bumpOutputCount(1)));
                outIncBtn.setTooltip(Tooltip.create(Lang.c("gui.kve.recipe.output_inc_tooltip")));
            }

            // Principales a la izquierda (Guardar destacado); Limpiar/Borrar a la derecha, lejos de Guardar.
            int by = T + GUI_H - BOTTOM_ROW_OFF;
            KveGuiStyle.ButtonRow row = new KveGuiStyle.ButtonRow(ex, by, right + 2);
            // misma fila que el resto de editores, todo con texto; Herramientas y Limpiar en "Mas"
            KveGuiStyle.ActionBar bar = new KveGuiStyle.ActionBar(row, this::addRenderableWidget);
            bar.primary(Lang.get("gui.kve.btn.save"), b -> saveRecipe()).setTooltip(Tooltip.create(Lang.c("gui.kve.recipe.save_tooltip")));
            bar.button(Lang.get("gui.kve.btn.preview"), b -> previewRecipe());
            bar.button(Lang.get("gui.kve.btn.duplicate"), b -> duplicateRecipe());
            bar.enabled(draftEnabled, b -> toggleDraftEnabled());
            bar.more(Lang.get("gui.kve.recipe.tools2.btn"), () -> minecraft.setScreen(new CraftToolsScreen(this)))
                    .more(Lang.get("gui.kve.recipe.clear_grid"), () -> clearGrid());
            bar.end(() -> deleteRecipe());
        } else {
            machineAreaX = ex;
            machineAreaY = T + 31;
            machineAreaW = Math.max(1, (L + GUI_W) - 4 - machineAreaX);
            machineAreaH = Math.max(1, (T + GUI_H) - 2 - machineAreaY);
            if (embeddedMachine == null) {
                embeddedMachine = new MachineRecipeEditScreen(this, null).withExitEmbedded(this::exitMachineMode);
            }
            embeddedMachine.init(machineAreaX, machineAreaY, machineAreaW, machineAreaH);
        }
        KveGuiStyle.checkOverlaps(this);
    }

    private void initRemove() {
        removeTab.initWidgets(this::addRenderableWidget, L + LIST_W + GRID_OX, T, L + GUI_W - 2, T + GUI_H - BOTTOM_ROW_OFF);
        // buscador de recetas quitadas y en cola, en el mismo lugar que el de la pestania de recetas
        EditBox removeSearch = new KveScaledEditBox(font, L + 2, T + 44, LIST_W - 4, 11, Component.literal(Lang.get("gui.kve.remove.search_hint")));
        removeSearch.setMaxLength(64);
        removeSearch.setValue(removeTab.filter);
        removeSearch.setResponder(s -> removeTab.setFilter(s));
        removeSearch.setHint(Component.literal(Lang.get("gui.kve.remove.search_hint")));
        removeSearch.setTooltip(Tooltip.create(Lang.c("gui.kve.remove.search_tooltip")));
        addRenderableWidget(removeSearch);
    }

    private void setType(RecipeData.RecipeType t) {
        selType = t;
        applyDimsFullReset();
        refresh();
    }

    void switchToNodeGridType(String id) {
        selType = RecipeData.RecipeType.CUSTOM_JSON;
        selCustomTypeId = id;
        applyDimsFullReset();
        applyRecipeTypeDescriptorHints(id);
    }

    private void openRecipeTypePicker() {
        minecraft.setScreen(new RecipeTypePickerScreen(this, id -> {
            if (id.equals(RecipeTypePickerScreen.EC_TABLE_SENTINEL_ID)) { setType(RecipeData.RecipeType.EC_TABLE_SHAPED); return; }
            if (id.equals(RecipeTypePickerScreen.EC_ENDER_SENTINEL_ID)) { setType(RecipeData.RecipeType.EC_ENDER_SHAPED); return; }
            if (id.equals(RecipeTypePickerScreen.EC_FLUX_SENTINEL_ID))  { setType(RecipeData.RecipeType.EC_FLUX_SHAPED); return; }
            if (id.equals(RecipeTypePickerScreen.EC_COMBO_SENTINEL_ID)) { setType(RecipeData.RecipeType.EC_COMBINATION); return; }
            var descriptor = com.kve.common.recipetype.RecipeTypeDescriptorRegistry.find(id);
            if (descriptor.isPresent() && descriptor.get().hasSlotLayout()) {
                openMachineEditor(null, id, null);
                return;
            }
            selType = RecipeData.RecipeType.CUSTOM_JSON;
            if (!id.equals(selCustomTypeId)) pendingExtraJsonToLoad = "";
            selCustomTypeId = id;
            applyDimsFullReset();
            applyRecipeTypeDescriptorHints(id);
            refresh();
        }));
    }

    private String otherModsButtonLabel() {
        if (isCustomMod() && selCustomTypeId != null && !selCustomTypeId.isEmpty()) {
            var descriptor = com.kve.common.recipetype.RecipeTypeDescriptorRegistry.find(selCustomTypeId);
            String name = descriptor.isPresent() ? descriptor.get().effectiveDisplayName() : selCustomTypeId;
            return KveGuiStyle.truncate(font, name, 90);
        }
        if (isEcType()) {
            String key = switch (selType) {
                case EC_TABLE_SHAPED, EC_TABLE_SHAPELESS -> "gui.kve.recipe.type.ec_table";
                case EC_ENDER_SHAPED, EC_ENDER_SHAPELESS -> "gui.kve.recipe.type.ec_ender";
                case EC_FLUX_SHAPED, EC_FLUX_SHAPELESS -> "gui.kve.recipe.type.ec_flux";
                default -> "gui.kve.recipe.type.ec_combo";
            };
            return KveGuiStyle.truncate(font, Lang.get(key), 90);
        }
        return Lang.get("gui.kve.recipe.other_mods");
    }

    private static boolean shouldRouteToMachineEditor(RecipeData d) { return RecipeGridRules.shouldRouteToMachineEditor(d); }

    private static void migrateForMachineEditor(RecipeData d) { RecipeGridRules.migrateForMachineEditor(d); }

    private void applyRecipeTypeDescriptorHints(String recipeTypeId) {
        var found = com.kve.common.recipetype.RecipeTypeDescriptorRegistry.find(recipeTypeId);
        pendingAddonToggleLabel = null;
        pendingAddonWarning = null;
        if (found.isEmpty()) return;
        var d = found.get();

        if (!d.extraFields.isEmpty() && (pendingExtraJsonToLoad == null || pendingExtraJsonToLoad.isEmpty())) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < d.extraFields.size(); i++) {
                var f = d.extraFields.get(i);
                sb.append("\"").append(f.key).append("\": ").append(f.skeletonValueLiteral());
                if (i < d.extraFields.size() - 1) sb.append(", ");
            }
            pendingExtraJsonToLoad = sb.toString();
        }

        boolean dedicated = com.kve.common.recipetype.RecipeTypeDescriptorRegistry.shouldUseDedicatedCall(d);
        boolean blockingWarning = false;
        String hint;
        if (dedicated) {
            String mod = d.dedicatedCall.requiresMod, addon = d.dedicatedCall.requiresAddon;
            String source = !mod.isBlank() && !addon.isBlank() ? mod + " + " + addon
                    : !mod.isBlank() ? mod : addon;
            hint = Lang.get("gui.kve.recipe.hint.dedicated", source);
        } else if (d.hasNativeExport()) {
            String mod = com.kve.common.recipetype.RecipeTypeDescriptorRegistry.nativeModId(d);
            if (com.kve.common.recipetype.RecipeTypeDescriptorRegistry.isNativeModMissing(d)) {
                hint = Lang.get("gui.kve.recipe.hint.native_missing", d.effectiveDisplayName(), mod);
                blockingWarning = true;
            } else {
                hint = Lang.get("gui.kve.recipe.hint.native_ok", d.effectiveDisplayName(), mod);
                if (com.kve.common.recipetype.RecipeTypeDescriptorRegistry.addonFlagIsOnlyBlocker(d)) {
                    pendingAddonToggleLabel = d.dedicatedCall.requiresAddon;
                }
            }
        } else if (d.dedicatedCall != null) {
            String addon = d.dedicatedCall.requiresAddon;
            if (com.kve.common.recipetype.RecipeTypeDescriptorRegistry.addonFlagIsOnlyBlocker(d)) {
                pendingAddonToggleLabel = addon;
                hint = Lang.get("gui.kve.recipe.hint.addon_toggle", d.effectiveDisplayName(), addon);
            } else {
                boolean modMissing = com.kve.common.recipetype.RecipeTypeDescriptorRegistry.isRequiredModMissing(d);
                String falta = modMissing
                        ? Lang.get("gui.kve.recipe.hint.missing_mod_addon", d.dedicatedCall.requiresMod, addon)
                        : Lang.get("gui.kve.recipe.hint.missing_addon", addon);
                hint = Lang.get("gui.kve.recipe.hint.missing", d.effectiveDisplayName(), falta);
                blockingWarning = true;
            }
        } else {
            hint = Lang.get("gui.kve.recipe.hint.known", d.effectiveDisplayName());
        }
        if (d.hasCustomGrid()) {
            hint += Lang.get("gui.kve.recipe.hint.grid", d.gridWidth, d.gridHeight);
        } else if (d.isNodeGridView()) {
            hint += Lang.get("gui.kve.recipe.hint.node_grid");
        }
        if (blockingWarning) {
            pendingAddonWarning = hint;
        } else {
            flash(hint);
        }
    }

    private void enablePendingAddonFlag() {
        if (pendingAddonToggleLabel == null) return;
        com.kve.common.recipetype.KveAddonFlags.setEnabled(pendingAddonToggleLabel, true);
        applyRecipeTypeDescriptorHints(selCustomTypeId);
        refresh();
    }

    private void openMachineEditor() {
        openMachineEditor(null, null, null);
    }

    private void openMachineEditor(RecipeData data, String preselectTypeId, String pendingRemoval) {
        if (hosted) {
            embeddedMachine = new MachineRecipeEditScreen(this, data, preselectTypeId)
                    .withExitEmbedded(this::exitMachineMode);
            if (pendingRemoval != null) embeddedMachine.withPendingRemovalId(pendingRemoval);
            machineMode = true;
            activeTab = Tab.RECIPES;
            refresh();
        } else {
            MachineRecipeEditScreen s = new MachineRecipeEditScreen(this, data, preselectTypeId);
            if (pendingRemoval != null) s.withPendingRemovalId(pendingRemoval);
            minecraft.setScreen(s);
        }
    }

    private void setTier(RecipeData.ECTableTier t) {
        selTier = t;
        if (selType == RecipeData.RecipeType.EC_TABLE_SHAPED ||
            selType == RecipeData.RecipeType.EC_TABLE_SHAPELESS) applyDims();
        refresh();
    }

    private void applyDims() {
        int[] d = dims(selType, selTier);
        gridW = d[0]; gridH = d[1];
        initGrid();
    }

    private void applyDimsFullReset() {
        int[] d = dims(selType, selTier);
        gridW = d[0]; gridH = d[1];
        resetAllSlots();
    }

    private int[] dims(RecipeData.RecipeType type, RecipeData.ECTableTier tier) {
        return RecipeGridRules.dims(type, tier, selCustomTypeId, editing, shapelessMaxCols());
    }

    private boolean isEcTableType() { return RecipeGridRules.isEcTableType(selType); }

    private boolean isEcType() { return RecipeGridRules.isEcType(selType); }

    private boolean isPositionalCustomJson() { return RecipeGridRules.isPositionalCustomJson(selType, selCustomTypeId); }

    private boolean isShapeless() { return RecipeGridRules.isShapeless(selType, selCustomTypeId); }

    private boolean isCustomMod() { return RecipeGridRules.isCustomMod(selType); }

    private boolean isCombo() { return RecipeGridRules.isCombo(selType); }

    private boolean isVariableGridCustomJson() { return RecipeGridRules.isVariableGridCustomJson(selType, selCustomTypeId); }

    /** Firma del borrador al cargarlo o guardarlo; si cambio, hay algo sin guardar. "" = siempre sin guardar. */
    private String draftBaseline = null;

    private String draftSignature() {
        try { return com.kve.common.persist.CraftEditorState.recipeToJson(buildDraftRecipeData()).toString(); }
        catch (Throwable t) { return ""; }
    }

    private void markDraftClean() { draftBaseline = draftSignature(); }

    private boolean hasUnsavedDraft() {
        if (machineMode || activeTab != Tab.RECIPES || draftBaseline == null) return false;
        return !draftSignature().equals(draftBaseline);
    }

    /** Corre {@code action} directo, o despues de confirmar que se descartan los cambios sin guardar. */
    private void confirmDiscardThen(Runnable action) {
        if (!hasUnsavedDraft()) { action.run(); return; }
        minecraft.setScreen(new KveConfirmScreen(
                confirmed -> {
                    minecraft.setScreen(this);
                    if (confirmed) action.run();
                },
                Component.literal(Lang.get("gui.kve.recipe.unsaved_title")),
                Component.literal(Lang.get("gui.kve.recipe.unsaved_msg"))));
    }

    private void startNew() {
        // en modo maquina "Nuevo" vacia el editor de maquina y conserva la estacion elegida
        if (machineMode && embeddedMachine != null) {
            String type = embeddedMachine.draftTypeId();
            pendingRemovalId = null;
            embeddedMachine = new MachineRecipeEditScreen(this, null, type.isBlank() ? null : type)
                    .withExitEmbedded(this::exitMachineMode);
            refresh();
            return;
        }
        editing = null; selType = RecipeData.RecipeType.VANILLA_SHAPED;
        selTier = RecipeData.ECTableTier.BASIC; gridW = 3; gridH = 3;
        selCustomTypeId = null;
        draftEnabled = true;
        draftCategory = "";
        draftNotes = "";
        pendingRemovalId = null;
        pendingExtraJsonToLoad = "";
        undoStack.clear();
        resetAllSlots();
        if (recipeIdBox != null) recipeIdBox.setValue("");
        refresh();
        markDraftClean();
    }

    private void duplicateRecipe() {
        String blocker = RecipeDraftActions.saveBlocker(ClientPermissionState.canEdit(), outputSlot.isEmpty(),
                selType, selCustomTypeId, false);
        if (blocker != null) { flash(Lang.get(blocker)); return; }
        editing = null;
        pendingRemovalId = null;
        if (recipeIdBox != null) {
            recipeIdBox.setValue(RecipeDraftActions.nextCopyId(recipeIdBox.getValue(), RecipeDraftActions::recipeIdInUse));
        }
        flash(Lang.get("gui.kve.recipe.duplicated_hint"));
    }

    private void toggleDraftEnabled() {
        draftEnabled = !draftEnabled;
        refresh();
    }

    private String pendingExtraJsonToLoad = "";
    private String pendingAddonToggleLabel = null;
    private String pendingAddonWarning = null;
    /** Rect {btnX,btnY,btnW,btnH} del boton "Aceptar" de {@link #pendingAddonWarning}, recalculado cada frame en render - null cuando no hay aviso mostrandose (ningun rect que hit-testear en mouseClicked()). */
    private int[] pendingAddonWarningBtn = null;

    private void loadRecipe(RecipeData d) {
        if (d.repairedTags != null && !d.repairedTags.isEmpty()) {
            flash(Lang.get("gui.kve.recipe.tags_repaired", d.repairedTags.size(), String.join(", ", d.repairedTags)));
        }
        editing = d; selType = d.type; selTier = d.ecTier;
        draftEnabled = d.enabled;
        draftCategory = d.category != null ? d.category : "";
        draftNotes = d.notes != null ? d.notes : "";
        selCustomTypeId = d.customTypeId != null ? d.customTypeId : "";
        pendingExtraJsonToLoad = d.customExtraJson != null ? d.customExtraJson : "";
        int[] dims = dims(selType, selTier); gridW = dims[0]; gridH = dims[1];
        initGrid();
        recipeIdBox.setValue(d.recipeId != null ? d.recipeId : "");
        outputSlot = d.output.copy();
        secondaryOutputSlot = d.secondaryOutput != null ? d.secondaryOutput.copy() : ItemStack.EMPTY;
        secondaryChancePct = (int) Math.round(Math.max(0.0, Math.min(1.0, d.secondaryOutputChance)) * 100.0);
        ecPower = d.ecPowerRequired; ecTime = d.ecCraftingTime;
        rfCost = d.combinationRfCost; rfRate = d.combinationRfRate;
        if (isCombo()) {
            combinationCore = d.combinationCenter.copy();
            for (int i = 0; i < Math.min(d.combinationPedestals.size(), pedestals.size()); i++)
                pedestals.set(i, d.combinationPedestals.get(i).copy());
        } else if (isShapeless()) {
            placeShapelessIngredients(d.shapelessIngredients);
        } else {
            placeShapedPattern(d);
        }
        refresh();
        markDraftClean();
    }

    private void placeShapelessIngredients(List<ItemStack> src) {
        if (src.size() > gridSlots.length) {
            int needed = src.size();
            int[] dims = shapelessGridDims(needed);
            gridW = dims[0]; gridH = dims[1];
            initGrid();
            flash(Lang.get("gui.kve.recipe.grid_expanded_ingredients", needed));
        }
        for (int i = 0; i < src.size(); i++) gridSlots[i] = src.get(i).copy();
    }

    private int shapelessMaxCols() {
        // GUI_W en este punto puede venir de la receta ANTERIOR (mas chica,
        // ver computeMinGuiW() - piso dinamico al contenido) todavia sin
        // recalcular para la nueva - usar GUI_W_MIN como piso de referencia
        // aca preserva la misma capacidad de columnas que esta pantalla
        // siempre tuvo, en vez de encoger de mas un shapeless grande solo
        // porque la ventana llegaba angosta de una receta chica anterior.
        int refW = Math.max(GUI_W, GUI_W_MIN);
        return (refW - LIST_W - GRID_OX - 22 - 18 - 4) / MIN_SLOT_PX;
    }

    private int[] shapelessGridDims(int count) { return RecipeGridRules.shapelessGridDims(count, shapelessMaxCols()); }

    private void placeShapedPattern(RecipeData d) {
        if (d.gridWidth <= 0 || d.gridHeight <= 0) return;
        if (d.gridWidth > gridW || d.gridHeight > gridH) {
            gridW = Math.max(gridW, d.gridWidth);
            gridH = Math.max(gridH, d.gridHeight);
            initGrid();
            flash(Lang.get("gui.kve.recipe.grid_expanded_shape", d.gridWidth, d.gridHeight));
        }
        for (int row = 0; row < d.gridHeight; row++) {
            for (int col = 0; col < d.gridWidth; col++) {
                int srcIdx = row * d.gridWidth + col;
                int dstIdx = row * gridW + col;
                if (srcIdx < d.ingredients.size() && dstIdx < gridSlots.length)
                    gridSlots[dstIdx] = d.ingredients.get(srcIdx).copy();
            }
        }
    }

    private void resizeGridManual(int newW, int newH) {
        newW = RecipeGridRules.clampManual(newW);
        newH = RecipeGridRules.clampManual(newH);
        if (newW == gridW && newH == gridH) return;

        ItemStack[] newSlots = RecipeGridRules.resizeGrid(gridSlots, gridW, gridH, newW, newH);
        if (newSlots == null) {
            flash(Lang.get("gui.kve.recipe.grid_shrink_blocked"));
            return;
        }
        gridW = newW; gridH = newH;
        gridSlots = newSlots;

        refresh();
    }

    public String pendingRemovalId() {
        return pendingRemovalId;
    }

    /**
     * Called back by {@link MachineRecipeEditScreen} once IT has staged the
     * removal itself (mirroring what {@link #saveRecipe()} does for this
     * screen's own recipes), so the flag doesn't linger and get reused/
     * double-staged if the player reopens this screen for something else.
     */
    public void clearPendingRemovalId() {
        pendingRemovalId = null;
    }

    /** Encola quitar una receta de vanilla o de un mod y muestra la pestania Quitar (lo usa el editor de maquinas). */
    public void queueVanillaRemoval(String id) {
        removeTab.queueRemove(id);
        switchTab(Tab.REMOVE);
    }

    private boolean openRecipe(RecipeData d, String pendingRemoval) {
        pendingRemovalId = pendingRemoval;
        undoStack.clear();
        brush = ItemStack.EMPTY;
        if (d.type == RecipeData.RecipeType.CUSTOM_MACHINE) {
            openMachineEditor(d, null, pendingRemoval);
            return false;
        }
        if (shouldRouteToMachineEditor(d)) {
            migrateForMachineEditor(d);
            openMachineEditor(d, null, pendingRemoval);
            return false;
        }
        activeTab = Tab.RECIPES;
        machineMode = false;
        refresh();
        loadRecipe(d);
        return true;
    }

    private void applyLoadedRecipe(RecipeData d) {
        editing = null;
        if (!openRecipe(d, pendingRemovalId)) return;
        if (pendingRemovalId != null) {
            flash(Lang.get("gui.kve.recipe.editing_will_remove", pendingRemovalId));
        }
    }

    private RecipeData buildDraftRecipeData() {
        RecipeData d = new RecipeData();
        d.recipeId = recipeIdBox != null ? recipeIdBox.getValue().trim() : "";
        d.type = selType; d.ecTier = selTier; d.output = outputSlot.copy();
        d.enabled = draftEnabled;
        d.category = draftCategory;
        d.notes = draftNotes;
        d.secondaryOutput = secondaryOutputSlot.copy();
        d.secondaryOutputChance = secondaryChancePct / 100.0;
        d.ecPowerRequired = ecPower; d.ecCraftingTime = ecTime;
        d.combinationRfCost = rfCost; d.combinationRfRate = rfRate;
        d.customTypeId = selCustomTypeId;
        d.customExtraJson = fCustomExtraJson != null ? fCustomExtraJson.getValue() : "";
        if (isCombo()) {
            d.combinationCenter = combinationCore.copy();
            d.combinationPedestals = new ArrayList<>(pedestals);
        } else if (isShapeless()) {
            d.shapelessIngredients = new ArrayList<>();
            for (ItemStack s : gridSlots) if (!s.isEmpty()) d.shapelessIngredients.add(s.copy());
        } else {
            d.gridWidth = gridW; d.gridHeight = gridH;
            d.ingredients = new ArrayList<>();
            for (ItemStack s : gridSlots) d.ingredients.add(s.copy());
        }
        return d;
    }

    private void onApplyPending() {
        if (!ClientPermissionState.canApply()) { flash(Lang.get("gui.kve.perm.apply_denied")); return; }
        if (PendingChangeManager.isEmpty()) return;
        if (PendingChangeManager.domainHasDiff(PendingDomain.RECIPES)) {
            minecraft.setScreen(new DiffPreviewScreen(this,
                    PendingChangeManager.entriesForDomain(PendingDomain.RECIPES),
                    this::proceedApply));
            return;
        }
        proceedApply();
    }

    private void proceedApply() {
        java.util.Set<PendingDomain> domains = PendingChangeManager.domainsPresent();
        if (domains.size() > 1) {
            int otherCount = PendingChangeManager.size() - PendingChangeManager.sizeForDomain(PendingDomain.RECIPES);
            minecraft.setScreen(new KveConfirmScreen(
                    confirmed -> {
                        minecraft.setScreen(this);
                        applyPendingResult(confirmed ? PendingChangeManager.applyAll() : PendingChangeManager.applyDomain(PendingDomain.RECIPES));
                    },
                    Component.literal(Lang.get("gui.kve.pending.other_domains_title")),
                    Component.literal(Lang.get("gui.kve.pending.other_domains_msg", otherCount))));
            return;
        }
        applyPendingResult(PendingChangeManager.applyDomain(PendingDomain.RECIPES));
    }

    private void applyPendingResult(PendingChangeManager.ApplyResult result) {
                com.kve.client.gui.KveToast.applied(result, KubeJSExporter.getLastWriteWarnings());
        refreshRecipeList();
        removeTab.refreshLists();
        refresh();
    }

    private void onDiscardPending() {
        if (PendingChangeManager.isEmpty()) return;
        int count = PendingChangeManager.sizeForDomain(PendingDomain.RECIPES);
        if (count == 0) return;
        if (KveClientSettings.getOrDefault(KveClientSettings.CONFIRM_DISCARDS, false)) {
            minecraft.setScreen(new KveConfirmScreen(
                    confirmed -> {
                        minecraft.setScreen(this);
                        if (!confirmed) return;
                        doDiscardPending(count);
                    },
                    Component.literal(Lang.get("gui.kve.settings.confirm_discards.title")),
                    Component.literal(Lang.get("gui.kve.settings.confirm_discards.msg", count))));
            return;
        }
        doDiscardPending(count);
    }

    private void doDiscardPending(int count) {
        PendingChangeManager.discardDomain(PendingDomain.RECIPES);
        flash(Lang.get("gui.kve.pending.discarded", count));
        refreshRecipeList();
        removeTab.refreshLists();
        refresh();
    }

    private boolean typeHasNoOutput() { return RecipeDraftActions.typeHasNoOutput(selType, selCustomTypeId); }

    private void saveRecipe() {
        String blocker = RecipeDraftActions.saveBlocker(ClientPermissionState.canEdit(), outputSlot.isEmpty(),
                selType, selCustomTypeId, true);
        if (blocker != null) { flash(Lang.get(blocker)); return; }
        RecipeData d = buildDraftRecipeData();
        if (d.recipeId.isEmpty()) {
            String outId = KubeJSExporter.itemId(d.output);
            d.recipeId = outId != null ? RecipeHelpers.suggestId(outId, RecipeDraftActions::recipeIdInUse) : KveGuiStyle.randomBareId();
            if (recipeIdBox != null) recipeIdBox.setValue(d.recipeId);
        }

        boolean renaming = editing != null && editing.recipeId != null
                && !editing.recipeId.equals(d.recipeId) && RecipeDraftActions.recipeIdInUse(editing.recipeId);
        if (RecipeDraftActions.recipeIdInUse(d.recipeId) && (editing == null || !d.recipeId.equals(editing.recipeId))) {
            flash(Lang.get("gui.kve.recipe.id_taken", d.recipeId));
            return;
        }

        List<String> problems = KubeJSExporter.validateRecipe(d);
        if (!problems.isEmpty()) {
            flash(String.join(" ", problems));
            return;
        }

        String removalId = pendingRemovalId;
        if (renaming) RecipeDraftActions.queueDelete(editing.recipeId, this::refreshRecipeList);
        RecipeDraftActions.queueSave(d, removalId, () -> { refreshRecipeList(); removeTab.refreshLists(); });

        editing = d;
        if (removalId != null) {
            flash(Lang.get("gui.kve.recipe.saved_will_remove", removalId) + " " + Lang.get("gui.kve.pending.queued_hint_reload"));
            pendingRemovalId = null;
        } else {
            flash(Lang.get("gui.kve.pending.queued_hint_reload"));
        }
        warnIfConflicts(d, removalId);
        warnIfOwnRecipeConflicts(d);
        refreshRecipeList();
        removeTab.refreshLists();
        refresh();
        markDraftClean();
    }

    /**
 * al
     * guardar una receta de mesa de crafteo, avisa si alguna receta que ya
     * existe acepta exactamente la misma grilla con otro resultado - en el
     * juego solo una de las dos ganaria. Solo avisa (nunca bloquea el
     * guardado); ver RecipeConflictFinder#conflictsWithGrid().
     */
    private void warnIfConflicts(RecipeData d, String removalId) {
        List<ItemStack> grid9 = RecipeGridRules.craftingGrid9(selType, gridSlots, gridW, gridH);
        if (grid9 == null) return;
        if (minecraft == null || minecraft.level == null || minecraft.getConnection() == null) return;
        ResourceLocation self = ResourceLocation.tryParse("shovel_js:" + d.recipeId.replaceAll("[^a-zA-Z0-9_./]", "_"));
        List<net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe>> clash;
        try {
            clash = com.kve.client.recipe.RecipeConflictFinder.conflictsWithGrid(
                    minecraft.getConnection().getRecipeManager(), minecraft.level,
                    minecraft.getConnection().registryAccess(), grid9, outputSlot, self);
        } catch (Throwable t) {
            return;
        }
        clash.removeIf(r -> r.id().toString().equals(removalId)
                || KubeJSExporter.getRemovedRecipes().contains(r.id().toString()));
        if (clash.isEmpty()) return;
        String first = clash.get(0).id().toString();
        flash(Lang.get("gui.kve.recipe.conflict_warning", first, clash.size()));
    }

    private void warnIfOwnRecipeConflicts(RecipeData d) {
        String id = d.recipeId;
        for (var c : com.kve.common.conflict.ConflictDetector.recipeConflicts(KubeJSExporter.getAddedRecipes())) {
            if (!c.involves(id)) continue;
            String other = c.keys().stream().filter(k -> !k.equals(id)).findFirst().orElse("?");
            String note = c.kind() == com.kve.common.conflict.ConflictDetector.Kind.RECIPE_DUPLICATE
                    ? Lang.get("gui.kve.conflict.saved_duplicate", other)
                    : Lang.get("gui.kve.conflict.saved_same_input", other);
            flash(savedMsg == null || savedMsg.isEmpty() ? note : savedMsg + " " + note);
            return;
        }
    }

    private void previewRecipe() {
        RecipeData d = buildDraftRecipeData();
        if (d.recipeId.isEmpty()) d.recipeId = "preview";
        String script = KubeJSExporter.previewRecipeJS(d);
        minecraft.setScreen(new RecipePreviewScreen(this, d.recipeId, script));
    }

    /** Tope de la cantidad del resultado: el stack maximo del item (un pico no sale de a 64). */
    private int outputMax() { return outputSlot.isEmpty() ? 64 : Math.max(1, outputSlot.getMaxStackSize()); }

    private void bumpOutputCount(int dir) {
        if (outputSlot.isEmpty()) return;
        int next = Math.max(1, Math.min(outputMax(), outputSlot.getCount() + dir));
        outputSlot.setCount(next);
        if (outputCountBox != null) outputCountBox.setValue(String.valueOf(next));
    }

    private void onOutputCountTyped(String value) {
        if (outputSlot.isEmpty() || value == null || value.isEmpty()) return;
        try {
            int n = Integer.parseInt(value);
            outputSlot.setCount(Math.max(1, Math.min(outputMax(), n)));
        } catch (NumberFormatException ignored) {}
    }

    private void deleteRecipe() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }

        if (editing == null || editing.recipeId == null || editing.recipeId.isEmpty()) {
            switchTab(Tab.REMOVE);
            return;
        }

        String confirmId = (pendingRemovalId != null && !pendingRemovalId.isEmpty()) ? pendingRemovalId : editing.recipeId;
        minecraft.setScreen(new KveConfirmScreen(
                confirmed -> {
                    minecraft.setScreen(this);
                    if (confirmed) doDeleteRecipe();
                },
                Component.literal(Lang.get("gui.kve.recipe.delete_confirm_title")),
                Component.literal(Lang.get("gui.kve.recipe.delete_confirm_msg", confirmId))));
    }

    private void doDeleteRecipe() {
        if (pendingRemovalId != null && !pendingRemovalId.isEmpty()) {
            String removalId = pendingRemovalId;
            pendingRemovalId = null;
            removeTab.queueRemove(removalId);
            startNew();
            switchTab(Tab.REMOVE);
            return;
        }

        RecipeDraftActions.queueDelete(editing.recipeId, this::refreshRecipeList);
        startNew(); refreshRecipeList();
        flash(Lang.get("gui.kve.pending.queued_hint_reload"));
        refresh();
    }

    private void clearGrid() {
        pushUndo();
        for (int i = 0; i < gridSlots.length; i++) gridSlots[i] = ItemStack.EMPTY;
        outputSlot = ItemStack.EMPTY; combinationCore = ItemStack.EMPTY;
        secondaryOutputSlot = ItemStack.EMPTY; secondaryChancePct = 50;
        for (int i = 0; i < pedestals.size(); i++) pedestals.set(i, ItemStack.EMPTY);
        if (outputCountBox != null) outputCountBox.setValue("1");
    }

    private void refreshRecipeList() {
        recipeList.clear();
        recipeList.addAll(RecipeListFilter.parse(searchFilter)
                .apply(KubeJSExporter.getAddedRecipes(), CraftEditorState::isFavoriteRecipe, listSort));
        recipeScrollBar.setTotals(recipeList.size(), listRows());
    }

    private void scrollRecipes(int dir) {
        recipeScrollBar.mouseScrolled(-dir);
    }

    private void flash(String msg) { savedMsg = msg; savedAt = System.currentTimeMillis(); }

    private int slotPx() {
        if (gridW <= 0) return 18;
        return Math.max(10, Math.min(18, GRID_PX / Math.max(gridW, gridH)));
    }

    private boolean isNodeCanvas() {
        if (isCombo() || gridW <= 0) return false;
        if (selCustomTypeId == null || selCustomTypeId.isBlank()) return false;
        var d = com.kve.common.recipetype.RecipeTypeDescriptorRegistry.find(selCustomTypeId);
        return d.isPresent() && d.get().isNodeGridView();
    }

    private void ncFitIfNeeded() { nodeCanvas.fitIfNeeded(gridW, gridH, GRID_PX); }
    private int ncCellSize() { return nodeCanvas.cellSize(); }
    private int ncCellX(int col) { return nodeCanvas.cellX(gox(), col); }
    private int ncCellY(int row) { return nodeCanvas.cellY(goy(), row); }
    private Integer ncSlotIndexAt(double mx, double my) { return nodeCanvas.indexAt(gox(), goy(), gridW, gridH, mx, my); }

    private int gox() { return L + LIST_W + GRID_OX; }
    private int goy() { return T + GRID_OY + (isVariableGridCustomJson() ? 14 : 0); }

    /** Y de la fila de resize manual +/- (ver isVariableGridCustomJson()) - va SIEMPRE justo debajo de fCustomExtraJson (cty = T+72 en buildWidgets(), ver goy de arriba sobre por que isCustomMod() esta implicito aca), nunca en la misma fila. */
    private int gridResizeRowY() { return T + 86; }

    private int outX() {
        if (isCombo() || gridW <= 0) return L + GUI_W - 30;
        if (isNodeCanvas()) return gox() + GRID_PX + 22;
        return gox() + gridW * slotPx() + 22;
    }
    private int outY() {
        if (isCombo() || gridW <= 0) return T + GRID_OY + (isVariableGridCustomJson() ? 14 : 0) + (GRID_PX - 16) / 2;
        if (isNodeCanvas()) return goy() + (GRID_PX - 16) / 2;
        return goy() + (gridH * slotPx() - 16) / 2;
    }

    private int secOutX() { return outX(); }
    private int secOutY() { return outY() + 36; }
    // Herreria (smithing) no tiene salida secundaria - el slot extra se encimaba con la linea de coste.
    private boolean secondaryOutputVisible() { return isCustomMod() && !isCombo() && !isPositionalCustomJson(); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        int wantW = hosted ? Math.max(hostedW, computeMinGuiW()) : computeMinGuiW();
        int wantH = hosted ? Math.max(hostedH, guiHFloor())      : guiHFloor();
        if (wantW != GUI_W || wantH != GUI_H) refresh();
        if (!hosted) renderTransparentBackground(g);
        drawFrame(g);
        drawLeftPanel(g, mx, my);
        if (activeTab == Tab.RECIPES) {
            if (machineMode && embeddedMachine != null) embeddedMachine.render(g, mx, my, pt);
            else {
                drawRecipesPanel(g, mx, my);
                drawCostLine(g);
            }
        } else {
            removeTab.drawQueuePanel(g, gox(), T, mx, my);
        }
        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
        if (activeTab == Tab.RECIPES && !machineMode) drawRecents(g, mx, my);
        drawTooltips(g, mx, my);
        if (!brush.isEmpty() && activeTab == Tab.RECIPES && !machineMode) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 400);
            MiniItemIcon.draw(g, mx + 8, my + 8, 10, brush.getItem());
            g.pose().popPose();
        }

        if (activeTab == Tab.RECIPES && !machineMode && isVariableGridCustomJson()) {
            int ex = L + LIST_W + GRID_OX;
            int gy = gridResizeRowY();
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.recipe.grid_cols_short") + " " + gridW, ex + 16, gy + 2, 88, 0xFFB8883A);
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.recipe.grid_rows_short") + " " + gridH, ex + 108, gy + 2, 88, 0xFFB8883A);
        }

        boolean flashShowing = savedAt > 0 && System.currentTimeMillis() - savedAt < 4000;
        if (flashShowing) {
            int flashCol = savedMsg.startsWith("\u26a0") ? 0xFFFFAA55 : 0xFF66FF99;
            KveGuiStyle.drawCenteredTextSafe(g, savedMsg, L + GUI_W / 2, T + GUI_H - 9, flashCol);
        } else if (activeTab == Tab.RECIPES && !machineMode && !liveProblems.isEmpty()) {
            String msg = "\u26A0 " + liveProblems.get(0) + (liveProblems.size() > 1 ? " (+" + (liveProblems.size() - 1) + ")" : "");
            KveGuiStyle.drawTextSafe(g, msg, L + LIST_W + GRID_OX, T + GUI_H - 9, GUI_W - LIST_W - GRID_OX - 4, 0xFFDD8844);
        } else {
            int pending = PendingChangeManager.size();
            String hint = pending > 0
                    ? Lang.get("gui.kve.recipe.pending_hint", pending)
                    : Lang.get("gui.kve.recipe.no_pending_hint");
            KveGuiStyle.drawTextSafe(g, hint, L + LIST_W + GRID_OX, T + GUI_H - 9, GUI_W - LIST_W - GRID_OX - 4, 0xFFB8883A);
        }

        pendingAddonWarningBtn = KveGuiStyle.drawFloatingNotice(g, pendingAddonWarning,
                Lang.get("gui.kve.confirm.ok"), L + GUI_W / 2, T + 40, width, height, KveGuiStyle.TEXT_ERROR);
    }

    private void drawFrame(GuiGraphics g) {
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, null);
        g.fill(L + LIST_W, T + 15, L + LIST_W + 1, T + GUI_H, KveGuiStyle.th(0xFF2A4A6A));

        int tx = activeTab == Tab.RECIPES ? L + 2 : L + 60;
        g.hLine(tx, tx + 54, T + 13, KveGuiStyle.th(0xFF60A8F0));

        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.recipe.editor_title"),
                L + LIST_W + GRID_OX, T + 3, 110, KveGuiStyle.ACCENT_GOLD);
    }

    private int leftListTopY() { return T + 58; }

    private static final int CONFLICT_BTN_H = 13;
    private int conflictCount, outputGroupCount;

    private boolean showsConflictBtn() { return activeTab == Tab.RECIPES && (conflictCount > 0 || outputGroupCount > 0); }

    private int conflictBtnY() { return T + GUI_H - 12 - CONFLICT_BTN_H; }

    private int listRows() {
        int bottom = T + GUI_H - 12 - (showsConflictBtn() ? CONFLICT_BTN_H + 3 : 0);
        return Math.max(4, (bottom - leftListTopY()) / ROW_H);
    }

    private int queueRows() {
        int bottom = T + GUI_H - BOTTOM_ROW_OFF - 4;
        return Math.max(3, (bottom - removeListTopY()) / ROW_H);
    }

    private void drawLeftPanel(GuiGraphics g, int mx, int my) {
        int lx = L + 2;
        int y0 = leftListTopY();
        List<?> list = activeTab == Tab.RECIPES ? recipeList : removeTab.removeList;
        int rows = listRows();
        int scroll = activeTab == Tab.RECIPES
                ? recipeScrollBar.getScroll() : removeTab.removeScrollBar.getScroll();

        if (list.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.recipe.list_empty"), lx + 3, y0 + 3, LIST_W - 22 - 6, KveGuiStyle.BORDER_SUBTLE);
            if (activeTab == Tab.REMOVE) removeTab.drawLeftHeader(g, lx, T, LIST_W - 4);
            return;
        }
        int vis = Math.min(rows, list.size() - scroll);
        for (int i = 0; i < vis; i++) {
            Object item = list.get(i + scroll);
            String label;
            boolean isSelected;
            if (activeTab == Tab.RECIPES) {
                RecipeData r = (RecipeData) item;
                String rid = r.recipeId != null ? r.recipeId : "?";
                label = (CraftEditorState.isFavoriteRecipe(rid) ? "\u2605 " : "") + rid;
                isSelected = editing != null && rid.equals(editing.recipeId);
            } else {
                label = (String) item;
                isSelected = (i + scroll) == removeTab.removeSelected;
            }
            int ry = y0 + i * ROW_H;
            boolean hov = mx >= lx && mx < lx + LIST_W - 22 && my >= ry && my < ry + ROW_H - 1;
            g.fill(lx, ry, lx + LIST_W - 22, ry + ROW_H - 1,
                    isSelected ? KveGuiStyle.th(0xFF2A5E9A) : hov ? KveGuiStyle.BORDER_FIELD : KveGuiStyle.BG_FIELD);
            int textRight = lx + LIST_W - 22 - 3;
            int color = KveGuiStyle.th(0xFFCCCCFF);
            net.minecraft.world.item.Item icon;
            if (item instanceof RecipeData r) {
                icon = listIcon(r);
                if (!r.enabled) color = KveGuiStyle.TEXT_MUTED;
            } else {
                icon = com.kve.client.recipe.RecipeOutputLookup.outputOf(label);
                color = 0xFFE0A090;
                if (hov) {
                    KveGuiStyle.drawTextSafe(g, "\u21BA", textRight - 6, ry + 4, 8, KveGuiStyle.TEXT_OK);
                    textRight -= 10;
                }
            }
            MiniItemIcon.draw(g, lx + 3, ry + 3, 9, icon);
            KveGuiStyle.drawTextSafe(g, label, lx + 16, ry + 4, textRight - (lx + 16), color);
        }

        if (activeTab == Tab.RECIPES && recipeScrollBar != null) recipeScrollBar.render(g);
        if (activeTab == Tab.REMOVE && removeTab.removeScrollBar != null) removeTab.removeScrollBar.render(g);
        if (activeTab == Tab.REMOVE) removeTab.drawLeftHeader(g, lx, T, LIST_W - 4);
        // con el boton de conflictos debajo de la lista el contador lo tapaba; la barra ya muestra la posicion
        if (list.size() > rows && !showsConflictBtn()) {
            KveGuiStyle.drawTextSafe(g, (scroll + 1) + "/" + (list.size() - rows + 1),
                    lx, y0 + rows * ROW_H + 1, 60, KveGuiStyle.th(0xFF556677));
        }
    }

    private static net.minecraft.world.item.Item listIcon(RecipeData r) {
        if (r.output != null && !r.output.isEmpty()) return r.output.getItem();
        for (RecipeData.MachineSlot s : r.machineOutputs) {
            if (s.type == RecipeData.MachineSlotType.ITEM && !s.item.isEmpty()) return s.item.getItem();
            if (s.type == RecipeData.MachineSlotType.TAG) {
                net.minecraft.world.item.Item it = MiniItemIcon.firstOfItemTag(s.tagId);
                if (it != null) return it;
            }
            if (s.type == RecipeData.MachineSlotType.FLUID) {
                net.minecraft.world.item.Item bucket = MiniItemIcon.item(s.fluidId + "_bucket");
                if (bucket != null) return bucket;
            }
        }
        for (RecipeData.MachineSlot s : r.machineInputs) {
            if (s.type == RecipeData.MachineSlotType.ITEM && !s.item.isEmpty()) return s.item.getItem();
        }
        return null;
    }

    private ItemStack stationIcon() {
        return StationResolver.resolveStationIcon(selType, selCustomTypeId);
    }

    private void drawStationIcon(GuiGraphics g) {
        ItemStack station = stationIcon();
        if (station.isEmpty()) return;
        int ix = L + GUI_W - 44;
        boolean extraRow = isCustomMod()
                || (KVEConfig.isExtendedCraftingActive() && isEcTableType());
        int iy = T + (extraRow ? 82 : 50);
        g.fill(ix - 2, iy - 2, ix + 18, iy + 18, KveGuiStyle.BG_FIELD);
        try { g.renderItem(station, ix, iy); } catch (Throwable ignored) {}
        String name = station.getHoverName().getString();
        KveGuiStyle.drawTextSafe(g, name, ix - 20, iy + 20, 56, KveGuiStyle.TEXT_MUTED);
    }

    private List<String> liveProblems = List.of();
    private long liveProblemsAt = 0L;
    private static final long LIVE_VALIDATION_INTERVAL_MS = 250L;

    private void drawRecipesPanel(GuiGraphics g, int mx, int my) {
        int ex = gox();
        if (typeLabelX >= 0) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.recipe.type_prefix"), typeLabelX, T + 19, 40, KveGuiStyle.TEXT_MUTED);
        }
        drawStationIcon(g);

        long nowMs = System.currentTimeMillis();
        if (nowMs - liveProblemsAt >= LIVE_VALIDATION_INTERVAL_MS) {
            liveProblemsAt = nowMs;
            RecipeData draftNow = buildDraftRecipeData();
            List<String> problems = new ArrayList<>(KubeJSExporter.validateRecipe(draftNow));
            problems.addAll(liveWarnings(draftNow));
            liveProblems = problems;
        }

        boolean typeMissing = isCustomMod() && selCustomTypeId.isEmpty();
        if (isCustomMod()) {
            String typeText = Lang.get("gui.kve.recipe.type_line",
                    selCustomTypeId.isEmpty() ? Lang.get("gui.kve.recipe.pick_type_prompt") : selCustomTypeId);
            KveGuiStyle.drawTextSafe(g, typeText, ex, T + 59, (L + GUI_W - 6) - ex, typeMissing ? 0xFFFF6666 : 0xFFE0B07D);
            if (!selCustomTypeId.isEmpty()) {
                String status = com.kve.common.recipetype.RecipeTypeDescriptorRegistry.dedicatedCallStatusLine(selCustomTypeId);
                if (!status.isEmpty()) {
                    int statusX = ex + font.width(typeText) + 10;
                    int maxW = (L + GUI_W - 6) - statusX;
                    if (maxW > 30) {
                        int col = status.charAt(0) == '\u2713' ? 0xFF66DD99
                                : status.charAt(0) == '\u26a0' ? 0xFFFFAA55
                                : KveGuiStyle.th(0xFF8899BB);
                        KveGuiStyle.drawTextSafe(g, status, statusX, T + 59, maxW, col);
                    }
                }
            }
        } else if (!isCombo()) {
            String info = selType.name();
            if (isEcTableType())
                info += "  " + gridW + "x" + gridH + " [" + selTier.name() + "]";
            int infoY = T + 59;
            KveGuiStyle.drawTextSafe(g, info, ex, infoY, (L + GUI_W - 6) - ex, KveGuiStyle.th(0xFF8899BB));
        }

        KveGuiStyle.drawTextSafe(g, "->", outX() - 14, outY() + 3, 14, 0xFFCCCC44);
        drawSlot(g, outX(), outY(), 18, outputSlot, mx, my, outputSlot.isEmpty());
        if (outputSlot.isEmpty() && typeHasNoOutput()) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.recipe.no_output_type"), outX() - 20, outY() + 21, 80, KveGuiStyle.TEXT_MUTED);
        }

        if (secondaryOutputVisible()) {
            drawSlot(g, secOutX(), secOutY(), 18, secondaryOutputSlot, mx, my);
            if (!secondaryOutputSlot.isEmpty()) {
                KveGuiStyle.drawTextSafe(g, secondaryChancePct + "%", secOutX() + 22, secOutY() + 5, 40, 0xFFAACCEE);
            } else {
                KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.recipe.secondary_hint"), secOutX() + 22, secOutY() + 5, 100, KveGuiStyle.th(0xFF556677));
            }
        }

        if (isCombo()) drawComboGrid(g, mx, my);
        else           drawShapedGrid(g, mx, my);
    }

    private boolean gridIsEmpty() {
        for (ItemStack s : gridSlots) if (s != null && !s.isEmpty()) return false;
        return true;
    }

    private void drawShapedGrid(GuiGraphics g, int mx, int my) {
        if (gridW <= 0) return;
        if (isNodeCanvas()) { drawNodeCanvas(g, mx, my); return; }
        int sz = slotPx(); int ox = gox(); int oy = goy();
        boolean empty = gridIsEmpty();
        for (int row = 0; row < gridH; row++)
            for (int col = 0; col < gridW; col++)
                drawSlot(g, ox + col * sz, oy + row * sz, sz, gridSlots[row * gridW + col], mx, my, empty);
    }

    private void drawNodeCanvas(GuiGraphics g, int mx, int my) {
        ncFitIfNeeded();
        int ox = gox(), oy = goy();
        g.enableScissor(ox, oy, ox + GRID_PX, oy + GRID_PX);
        nodeCanvas.drawBackground(g, ox, oy, GRID_PX);

        int sz = ncCellSize();
        nodeCanvas.drawConnectingBrackets(g, ox, oy, gridW, gridH,
                (col, row) -> !gridSlots[row * gridW + col].isEmpty());

        boolean empty = gridIsEmpty();
        for (int row = 0; row < gridH; row++) {
            for (int col = 0; col < gridW; col++) {
                int cx = ncCellX(col), cy = ncCellY(row);
                if (cx + sz < ox || cx > ox + GRID_PX || cy + sz < oy || cy > oy + GRID_PX) continue;
                drawSlot(g, cx, cy, sz, gridSlots[row * gridW + col], mx, my, empty);
            }
        }
        // Zoom readout inside the box (top-left corner, over the canvas
        // itself) instead of a new row below it - GRID_PX already sits
        // right against the bottom button row at this GUI_H, so adding an
        // external line here would risk overlapping it; drawing inside the
        // scissor region costs zero extra layout space.
        nodeCanvas.drawZoomReadout(g, ox, oy);
        g.disableScissor();
    }

    private void drawComboGrid(GuiGraphics g, int mx, int my) {
        int ex = gox(); int oy = goy(); int sz = 17; int cols = 8;
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.recipe.core"), ex, oy - 9, sz, combinationCore.isEmpty() ? 0xFFFF8866 : 0xFFBBBBBB);
        drawSlot(g, ex, oy, sz, combinationCore, mx, my, combinationCore.isEmpty());
        boolean anyPedestal = pedestals.stream().anyMatch(s -> s != null && !s.isEmpty());
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.recipe.pedestals"), ex + sz + 4, oy - 9, sz * cols, anyPedestal ? 0xFFBBBBBB : 0xFFFF8866);
        for (int i = 0; i < Math.min(pedestals.size(), 48); i++)
            drawSlot(g, ex + sz + 4 + (i % cols) * sz, oy + (i / cols) * sz, sz, pedestals.get(i), mx, my);
    }

    private int removeListTopY() { return CraftRemoveTab.queueListTopY(gox(), T); }

    private void drawSlot(GuiGraphics g, int x, int y, int sz, ItemStack stack, int mx, int my) {
        RecipeSlotRenderer.drawSlot(g, font, x, y, sz, stack, mx, my);
    }

    /** Ver {@link RecipeSlotRenderer#drawSlot()} - {@code invalid} pinta borde rojo (validacion en vivo). */
    private void drawSlot(GuiGraphics g, int x, int y, int sz, ItemStack stack, int mx, int my, boolean invalid) {
        RecipeSlotRenderer.drawSlot(g, font, x, y, sz, stack, mx, my, invalid);
    }

    private boolean wantsNbtEdit() {
        return Screen.hasControlDown();
    }

    private static boolean hitsNbtBadge(int x, int y, int sz, double mx, double my) {
        return RecipeSlotRenderer.hitsNbtBadge(x, y, sz, mx, my);
    }

    private void openNbtEditorFor(ItemStack current, java.util.function.Consumer<ItemStack> setter) {
        if (current == null || current.isEmpty()) return;
        minecraft.setScreen(new NbtEditorScreen(this, current, s -> {
            setter.accept(s);
            minecraft.setScreen(this);
        }));
    }

    /**
 * LIST_W es solo 115px, asi que cualquier recipeId largo (namespace
     * completo tipo "somemod:some_long_machine_recipe_name") termina
     * truncado con "..." por drawTextSafe() en drawLeftPanel() sin forma de
     * ver el id real salvo hacer click y cargarlo. Muestra el label
     * completo (sin truncar) como tooltip SOLO cuando de verdad no entra -
     * evita ruido en filas que ya se ven completas. Compartido por ambas
     * tabs (RECIPES y REMOVE), por eso vive fuera del gate de
     * "activeTab != RECIPES" de mas abajo.
     */
    private boolean drawListRowTooltip(GuiGraphics g, int mx, int my) {
        int lx = L + 2; int y0 = leftListTopY();
        int rows = listRows();
        List<?> list = activeTab == Tab.RECIPES ? recipeList : removeTab.removeList;
        if (list.isEmpty()) return false;
        int scroll = activeTab == Tab.RECIPES
                ? recipeScrollBar.getScroll() : removeTab.removeScrollBar.getScroll();
        int vis = Math.min(rows, list.size() - scroll);
        int maxW = LIST_W - 22 - 19;
        for (int i = 0; i < vis; i++) {
            int ry = y0 + i * ROW_H;
            if (mx >= lx && mx < lx + LIST_W - 22 && my >= ry && my < ry + ROW_H - 1) {
                Object item = list.get(i + scroll);
                String label;
                if (activeTab == Tab.RECIPES) {
                    RecipeData r = (RecipeData) item;
                    String rid = r.recipeId != null ? r.recipeId : "?";
                    label = (CraftEditorState.isFavoriteRecipe(rid) ? "\u2605 " : "") + rid;
                } else {
                    label = (String) item;
                }
                if (activeTab == Tab.RECIPES) {
                    List<Component> lines = new ArrayList<>();
                    if (font.width(label) > maxW) lines.add(Component.literal(label));
                    lines.add(Component.translatable("gui.kve.recipe.row_favorite_hint").withStyle(ChatFormatting.GRAY));
                    g.renderComponentTooltip(font, lines, mx, my);
                    return true;
                }
                removeTab.drawLeftRowTooltip(g, label, mx, my);
                return true;
            }
        }
        return false;
    }

    private void drawTooltips(GuiGraphics g, int mx, int my) {
        if (drawListRowTooltip(g, mx, my)) return;
        if (activeTab != Tab.RECIPES || machineMode) return;

        if (mx >= outX() && mx < outX() + 18 && my >= outY() && my < outY() + 18 && !outputSlot.isEmpty()) {
            g.renderComponentTooltip(font, outputTooltipLines(outputSlot), mx, my); return;
        }

        if (secondaryOutputVisible() && mx >= secOutX() && mx < secOutX() + 18 && my >= secOutY() && my < secOutY() + 18 && !secondaryOutputSlot.isEmpty()) {
            List<Component> lines = new ArrayList<>(getTooltipFromItem(minecraft, secondaryOutputSlot));
            lines.add(Component.literal(Lang.get("gui.kve.recipe.secondary_tooltip", secondaryChancePct)).withStyle(ChatFormatting.GRAY));
            lines.add(KveGuiStyle.slotHint(KveGuiStyle.HINT_CLICK_CHANGE, KveGuiStyle.HINT_RIGHT_CLEAR, KveGuiStyle.HINT_CTRL_NBT));
            g.renderComponentTooltip(font, lines, mx, my); return;
        }

        if (!isCombo() && gridW > 0) {
            int sz = slotPx(); int ox = gox(); int oy = goy();
            for (int row = 0; row < gridH; row++) for (int col = 0; col < gridW; col++) {
                int sx = ox + col * sz; int sy = oy + row * sz;
                if (mx >= sx && mx < sx + sz && my >= sy && my < sy + sz) {
                    ItemStack s = gridSlots[row * gridW + col];
                    if (!s.isEmpty()) g.renderComponentTooltip(font, ingredientTooltip(s), mx, my);
                    return;
                }
            }
        }

        if (isCombo()) {
            int ex = gox(); int oy = goy(); int sz = 17; int cols = 8;
            if (mx >= ex && mx < ex + sz && my >= oy && my < oy + sz && !combinationCore.isEmpty()) {
                List<Component> lines = new ArrayList<>(getTooltipFromItem(minecraft, combinationCore));
                lines.add(KveGuiStyle.slotHint(KveGuiStyle.HINT_CLICK_CHANGE, KveGuiStyle.HINT_RIGHT_CLEAR, KveGuiStyle.HINT_CTRL_NBT));
                g.renderComponentTooltip(font, lines, mx, my); return;
            }
            for (int i = 0; i < Math.min(pedestals.size(), 48); i++) {
                int px = ex + sz + 4 + (i % cols) * sz; int py = oy + (i / cols) * sz;
                if (mx >= px && mx < px + sz && my >= py && my < py + sz && !pedestals.get(i).isEmpty()) {
                    List<Component> lines = new ArrayList<>(getTooltipFromItem(minecraft, pedestals.get(i)));
                    lines.add(KveGuiStyle.slotHint(KveGuiStyle.HINT_CLICK_CHANGE, KveGuiStyle.HINT_RIGHT_CLEAR, KveGuiStyle.HINT_CTRL_NBT));
                    g.renderComponentTooltip(font, lines, mx, my); return;
                }
            }
        }
    }

    private List<Component> outputTooltipLines(ItemStack stack) {
        List<Component> lines = new ArrayList<>(getTooltipFromItem(minecraft, stack));
        lines.add(KveGuiStyle.slotHint(KveGuiStyle.HINT_CLICK_CHANGE, KveGuiStyle.HINT_RIGHT_CLEAR, KveGuiStyle.HINT_CTRL_NBT, KveGuiStyle.HINT_SCROLL_QTY));
        return lines;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        // un menu "Mas" abierto recibe el clic antes que lo que tenga debajo
        if (com.kve.client.gui.KveMenuButton.consumeClick(mx, my, btn)) return true;
        if (pendingAddonWarning != null && pendingAddonWarningBtn != null) {
            int[] r = pendingAddonWarningBtn;
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                pendingAddonWarning = null;
                pendingAddonWarningBtn = null;
                return true;
            }
        }
        if (activeTab == Tab.RECIPES && machineMode && embeddedMachine != null
                && embeddedMachine.consumeAddonWarningClick(mx, my)) {
            return true;
        }

        if (activeTab == Tab.RECIPES && recipeScrollBar != null && recipeScrollBar.mouseClicked(mx, my, btn)) return true;
        if (activeTab == Tab.REMOVE  && removeTab.removeScrollBar != null && removeTab.removeScrollBar.mouseClicked(mx, my, btn)) return true;

        if (activeTab == Tab.RECIPES && !machineMode && clickRecents(mx, my, btn)) return true;

        int lx = L + 2; int y0 = leftListTopY();
        int rows = listRows();
        int scroll = activeTab == Tab.RECIPES
                ? recipeScrollBar.getScroll() : removeTab.removeScrollBar.getScroll();
        List<?> list = activeTab == Tab.RECIPES ? recipeList : removeTab.removeList;
        int vis = Math.min(rows, list.size() - scroll);
        for (int i = 0; i < vis; i++) {
            int ry = y0 + i * ROW_H;
            if (mx >= lx && mx < lx + LIST_W - 22 && my >= ry && my < ry + ROW_H - 1) {
                if (activeTab == Tab.RECIPES) {
                    RecipeData sel = recipeList.get(i + scroll);
                    if (btn == 1) {
                        CraftEditorState.toggleFavoriteRecipe(sel.recipeId);
                        refreshRecipeList();
                    } else {
                        confirmDiscardThen(() -> openRecipe(sel, null));
                    }
                }
                else if (mx >= lx + LIST_W - 22 - 12) removeTab.restoreApplied(i + scroll);
                else removeTab.removeSelected = i + scroll;
                return true;
            }
        }

        if (activeTab == Tab.RECIPES && machineMode && embeddedMachine != null
                && mx >= machineAreaX && mx < machineAreaX + machineAreaW
                && my >= machineAreaY && my < machineAreaY + machineAreaH) {
            if (embeddedMachine.mouseClicked(mx, my, btn)) { setFocused(null); return true; }
        }

        if (activeTab == Tab.RECIPES) {
          if (!machineMode) {

            if (mx >= outX() && mx < outX() + 18 && my >= outY() && my < outY() + 18) {
                if (btn == 0 && !outputSlot.isEmpty() && hitsNbtBadge(outX(), outY(), 18, mx, my)) {
                    openNbtEditorFor(outputSlot, s -> { pushUndo(); outputSlot = s; });
                } else if (btn == 0 && !outputSlot.isEmpty() && wantsNbtEdit()) {
                    openNbtEditorFor(outputSlot, s -> { pushUndo(); outputSlot = s; });
                } else if (btn == 1) {
                    if (!outputSlot.isEmpty()) pushUndo();
                    outputSlot = ItemStack.EMPTY;
                    if (outputCountBox != null) outputCountBox.setValue("1");
                } else {
                    int keepCount = Math.max(1, outputSlot.getCount());
                    minecraft.setScreen(new ItemPickerScreen(this, s -> {
                        pushUndo();
                        outputSlot = s;
                        outputSlot.setCount(Math.min(keepCount, Math.max(1, s.getMaxStackSize())));
                        RecentItems.add(s);
                    }, true));
                }
                return true;
            }

            if (secondaryOutputVisible() && mx >= secOutX() && mx < secOutX() + 18 && my >= secOutY() && my < secOutY() + 18) {
                if (btn == 0 && !secondaryOutputSlot.isEmpty() && hitsNbtBadge(secOutX(), secOutY(), 18, mx, my)) {
                    openNbtEditorFor(secondaryOutputSlot, s -> { pushUndo(); secondaryOutputSlot = s; });
                } else if (btn == 0 && !secondaryOutputSlot.isEmpty() && wantsNbtEdit()) {
                    openNbtEditorFor(secondaryOutputSlot, s -> { pushUndo(); secondaryOutputSlot = s; });
                } else if (btn == 1) {
                    if (!secondaryOutputSlot.isEmpty()) pushUndo();
                    secondaryOutputSlot = ItemStack.EMPTY;
                } else {
                    minecraft.setScreen(new ItemPickerScreen(this, s -> { pushUndo(); secondaryOutputSlot = s; }, true));
                }
                return true;
            }

            if (!isCombo() && gridW > 0 && isNodeCanvas()) {
                Integer hit = ncSlotIndexAt(mx, my);
                if (hit != null) {
                    final int idx = hit;
                    int ncSz = ncCellSize();
                    int ncX = ncCellX(idx % gridW), ncY = ncCellY(idx / gridW);
                    clickIngredientSlot(() -> gridSlots[idx], s -> gridSlots[idx] = s, ncX, ncY, ncSz, mx, my, btn);
                    return true;
                }
                int ox = gox(), oy = goy();
                if (mx >= ox && mx < ox + GRID_PX && my >= oy && my < oy + GRID_PX) {
                    return nodeCanvas.handleEmptyClick(btn, mx, my);
                }
            } else if (!isCombo() && gridW > 0) {
                int sz = slotPx(); int ox = gox(); int oy = goy();
                for (int row = 0; row < gridH; row++) for (int col = 0; col < gridW; col++) {
                    int sx = ox + col * sz; int sy = oy + row * sz;
                    if (mx >= sx && mx < sx + sz && my >= sy && my < sy + sz) {
                        final int idx = row * gridW + col;
                        clickIngredientSlot(() -> gridSlots[idx], s -> gridSlots[idx] = s, sx, sy, sz, mx, my, btn);
                        return true;
                    }
                }
            }

            if (isCombo()) {
                int ex = gox(); int oy = goy(); int sz = 17; int cols = 8;
                if (mx >= ex && mx < ex + sz && my >= oy && my < oy + sz) {
                    clickIngredientSlot(() -> combinationCore, s -> combinationCore = s, ex, oy, sz, mx, my, btn);
                    return true;
                }
                for (int i = 0; i < Math.min(pedestals.size(), 48); i++) {
                    int px = ex + sz + 4 + (i % cols) * sz; int py = oy + (i / cols) * sz;
                    if (mx >= px && mx < px + sz && my >= py && my < py + sz) {
                        final int fi = i;
                        clickIngredientSlot(() -> pedestals.get(fi), s -> pedestals.set(fi, s), px, py, sz, mx, my, btn);
                        return true;
                    }
                }
            }
          }
        } else {

            if (removeTab.clickQueuePanel(mx, my, btn, gox(), T)) return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        if (activeTab == Tab.RECIPES && machineMode && embeddedMachine != null
                && mx >= machineAreaX && mx < machineAreaX + machineAreaW
                && my >= machineAreaY && my < machineAreaY + machineAreaH) {
            if (embeddedMachine.mouseScrolled(mx, my, scrollX, delta)) return true;
        }

        if (activeTab == Tab.RECIPES && !machineMode && isNodeCanvas()) {
            int ox = gox(), oy = goy();
            if (mx >= ox && mx < ox + GRID_PX && my >= oy && my < oy + GRID_PX) {
                nodeCanvas.handleScrollZoom(ox, oy, mx, my, delta);
                return true;
            }
        }

        if (activeTab == Tab.RECIPES && !machineMode && !outputSlot.isEmpty()
                && mx >= outX() && mx < outX() + 18 && my >= outY() && my < outY() + 18) {
            int step = (int) Math.signum(delta) * (hasShiftDown() ? 10 : 1);
            int next = Math.max(1, Math.min(outputMax(), outputSlot.getCount() + step));
            outputSlot.setCount(next);
            if (outputCountBox != null) outputCountBox.setValue(String.valueOf(next));
            return true;
        }

        if (activeTab == Tab.RECIPES && !machineMode && secondaryOutputVisible() && !secondaryOutputSlot.isEmpty()
                && mx >= secOutX() && mx < secOutX() + 18 && my >= secOutY() && my < secOutY() + 18) {
            int step = (int) Math.signum(delta) * (hasShiftDown() ? 10 : 1);
            secondaryChancePct = Math.max(0, Math.min(100, secondaryChancePct + step));
            return true;
        }

        if (mx >= L + 2 && mx < L + LIST_W) {
            if (activeTab == Tab.RECIPES) recipeScrollBar.mouseScrolled(delta);
            else                          removeTab.removeScrollBar.mouseScrolled(delta);
            return true;
        }
        if (activeTab == Tab.REMOVE && removeTab.scrollQueue(mx, delta, gox())) return true;
        return super.mouseScrolled(mx, my, scrollX, delta);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (activeTab == Tab.RECIPES && !machineMode && nodeCanvas.handleDrag(mx, my)) {
            return true;
        }
        if (activeTab == Tab.RECIPES && recipeScrollBar.mouseDragged(mx, my, btn, dx, dy)) return true;
        if (activeTab == Tab.REMOVE  && removeTab.dragScrollBars(mx, my, btn, dx, dy)) return true;
        if (activeTab == Tab.RECIPES && machineMode && embeddedMachine != null
                && mx >= machineAreaX && mx < machineAreaX + machineAreaW
                && my >= machineAreaY && my < machineAreaY + machineAreaH) {
            if (embeddedMachine.mouseDragged(mx, my, btn, dx, dy)) return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    /** Las letras van al campo con foco: el buscador propio o, en modo maquina, los campos del editor de maquinas. */
    @Override
    public boolean charTyped(char c, int mods) {
        if (activeTab == Tab.RECIPES && machineMode && embeddedMachine != null
                && !(getFocused() instanceof net.minecraft.client.gui.components.EditBox box && box.isFocused())) {
            return embeddedMachine.charTyped(c, mods);
        }
        return super.charTyped(c, mods);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        nodeCanvas.endDrag();
        recipeScrollBar.mouseReleased(mx, my, btn);
        removeTab.releaseScrollBars(mx, my, btn);
        // el editor de maquinas tambien tiene arrastres (scrollbars, lienzo): sin esto quedaban pegados
        if (machineMode && embeddedMachine != null) embeddedMachine.mouseReleased(mx, my, btn);
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {

        if (activeTab == Tab.RECIPES && machineMode && embeddedMachine != null) {
            // el buscador de la izquierda sigue siendo de esta pantalla aunque el centro sea la maquina
            if (getFocused() instanceof net.minecraft.client.gui.components.EditBox box && box.isFocused()
                    && key != GLFW.GLFW_KEY_ESCAPE && box.keyPressed(key, scan, mods)) return true;
            return embeddedMachine.keyPressed(key, scan, mods);
        }

        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && pendingAddonWarning != null) {
            pendingAddonWarning = null;
            pendingAddonWarningBtn = null;
            return true;
        }

        if (key == GLFW.GLFW_KEY_ESCAPE && !brush.isEmpty()) { brush = ItemStack.EMPTY; return true; }
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }

        if (getFocused() != null && getFocused().keyPressed(key, scan, mods)) return true;

        if ((mods & GLFW.GLFW_MOD_CONTROL) != 0 && activeTab == Tab.RECIPES) {
            if (key == GLFW.GLFW_KEY_S) { saveRecipe(); return true; }
            if (key == GLFW.GLFW_KEY_D) { duplicateRecipe(); return true; }
            if (key == GLFW.GLFW_KEY_R) { clearGrid(); return true; }
            boolean typing = getFocused() instanceof net.minecraft.client.gui.components.EditBox;
            if (!typing && key == GLFW.GLFW_KEY_Z && (mods & GLFW.GLFW_MOD_SHIFT) != 0) { redoGrid(); return true; }
            if (!typing && key == GLFW.GLFW_KEY_Z) { undoGrid(); return true; }
            if (!typing && key == GLFW.GLFW_KEY_Y) { redoGrid(); return true; }
        }

        if (EditorKeys.isFocusTab(key, mods)) { super.keyPressed(key, scan, mods); return true; }
        return EditorKeys.swallow(mods);
    }

    private void clickIngredientSlot(java.util.function.Supplier<ItemStack> get, java.util.function.Consumer<ItemStack> set,
                                     int sx, int sy, int sz, double mx, double my, int btn) {
        ItemStack cur = get.get();
        java.util.function.Consumer<ItemStack> setUndoable = s -> { pushUndo(); set.accept(s); };
        boolean isTag = TagIngredient.isTag(cur);
        if (btn == 0 && !cur.isEmpty() && !isTag && hitsNbtBadge(sx, sy, sz, mx, my)) { openNbtEditorFor(cur, setUndoable); return; }
        if (btn == 0 && !cur.isEmpty() && Screen.hasAltDown()) { openTagChoice(cur, setUndoable); return; }
        if (btn == 0 && !cur.isEmpty() && !isTag && wantsNbtEdit()) { openNbtEditorFor(cur, setUndoable); return; }
        if (btn == 1) { if (!cur.isEmpty()) setUndoable.accept(ItemStack.EMPTY); return; }
        if (btn == 0 && !brush.isEmpty()) { setUndoable.accept(brush.copyWithCount(1)); return; }
        if (btn == 0) {
            minecraft.setScreen(new ItemPickerScreen(this, s -> { setUndoable.accept(s); RecentItems.add(s); }, true).withTagOption());
        }
    }

    private void openTagChoice(ItemStack cur, java.util.function.Consumer<ItemStack> set) {
        net.minecraft.world.item.Item item = cur.getItem();
        if (TagIngredient.tagsOf(item).isEmpty()) { flash("\u26a0 " + Lang.get("gui.kve.recipe.tagpick.no_tags")); return; }
        minecraft.setScreen(new TagChoiceScreen(this, item, tag -> {
            ItemStack t = TagIngredient.of(tag, item);
            set.accept(t);
            RecentItems.add(t);
            flash(Lang.get("gui.kve.recipe.tagpick.done", tag));
        }));
    }

    private List<Component> ingredientTooltip(ItemStack s) {
        List<Component> lines = new ArrayList<>();
        String tag = TagIngredient.tagOf(s);
        if (tag != null) {
            lines.add(Component.literal("#" + tag).withStyle(ChatFormatting.GOLD));
            lines.add(Component.literal(Lang.get("gui.kve.recipe.tag_slot_line", TagIngredient.members(tag).size())).withStyle(ChatFormatting.GRAY));
            if (TagIngredient.members(tag).isEmpty()) lines.addAll(MachineRecipeEditScreen.brokenTagLines(tag));
        } else {
            lines.addAll(getTooltipFromItem(minecraft, s));
        }
        lines.add(KveGuiStyle.slotHint(KveGuiStyle.HINT_CLICK_CHANGE, KveGuiStyle.HINT_RIGHT_CLEAR, KveGuiStyle.HINT_CTRL_NBT));
        lines.add(Component.literal(Lang.get("gui.kve.recipe.alt_tag_hint")).withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    private void pushUndo() {
        undoStack.push(undoSnapshot());
    }

    private RecipeData undoSnapshot() {
        RecipeData d = buildDraftRecipeData();
        d.gridWidth = gridW;
        d.gridHeight = gridH;
        d.ingredients = new ArrayList<>();
        for (ItemStack s : gridSlots) d.ingredients.add(s.copy());
        d.shapelessIngredients = new ArrayList<>();
        return d;
    }

    private void undoGrid() {
        RecipeData prev = undoStack.undo(undoSnapshot());
        if (prev == null) return;
        restoreDraft(prev);
        flash(Lang.get("gui.kve.recipe.undone"));
    }

    private void redoGrid() {
        RecipeData next = undoStack.redo(undoSnapshot());
        if (next == null) return;
        restoreDraft(next);
        flash(Lang.get("gui.kve.recipe.redone"));
    }

    private void restoreDraft(RecipeData d) {
        String keepBaseline = draftBaseline;
        RecipeData keepEditing = editing;
        String keepId = recipeIdBox != null ? recipeIdBox.getValue() : "";
        String keepRemoval = pendingRemovalId;
        loadRecipe(d);
        if (!isCombo() && d.gridWidth > 0 && d.gridHeight > 0 && d.ingredients.size() == d.gridWidth * d.gridHeight) {
            if (gridW != d.gridWidth || gridH != d.gridHeight) {
                gridW = d.gridWidth;
                gridH = d.gridHeight;
                initGrid();
            }
            for (int i = 0; i < gridSlots.length && i < d.ingredients.size(); i++) gridSlots[i] = d.ingredients.get(i).copy();
            refresh();
        }
        editing = keepEditing;
        pendingRemovalId = keepRemoval;
        if (recipeIdBox != null) recipeIdBox.setValue(keepId);
        draftBaseline = keepBaseline;
    }

    private void loadDraftAsNew(RecipeData d) {
        pushUndo();
        loadRecipe(d);
        editing = null;
        pendingRemovalId = null;
        if (recipeIdBox != null) recipeIdBox.setValue("");
        refresh();
        draftBaseline = "";
    }

    private int recentsX = -1;

    private int recentsY() { return T + 31; }

    /** X de la etiqueta "Tipo:" de la fila A (-1 en modo maquina). */
    private int typeLabelX = -1;

    private boolean recentsFit() { return recentsX >= 0; }

    private void drawRecents(GuiGraphics g, int mx, int my) {
        if (!recentsFit()) return;
        List<ItemStack> rec = RecentItems.get();
        int x0 = recentsX, y = recentsY();
        String lbl = Lang.get("gui.kve.recipe.recents_label");
        KveGuiStyle.drawTextSafe(g, lbl, x0 - 4 - font.width(lbl), y + 1, font.width(lbl) + 2,
                brush.isEmpty() ? KveGuiStyle.TEXT_MUTED : KveGuiStyle.ACCENT_GOLD);
        for (int i = 0; i < RecentItems.MAX; i++) {
            int x = x0 + i * 12;
            ItemStack s = i < rec.size() ? rec.get(i) : ItemStack.EMPTY;
            boolean held = !s.isEmpty() && !brush.isEmpty() && RecentItems.same(s, brush);
            if (held) g.fill(x - 2, y - 2, x + 12, y + 12, KveGuiStyle.ACCENT_GOLD);
            MiniItemIcon.draw(g, x, y, 10, s.isEmpty() ? null : s.getItem());
            if (TagIngredient.isTag(s)) KveGuiStyle.drawTextSafe(g, "#", x, y + 3, 6, KveGuiStyle.ACCENT_GOLD);
        }
        int hov = recentAt(mx, my);
        if (hov >= 0 && hov < rec.size()) {
            List<Component> lines = new ArrayList<>();
            String tag = TagIngredient.tagOf(rec.get(hov));
            lines.add(tag != null ? Component.literal("#" + tag) : rec.get(hov).getHoverName());
            lines.add(Component.literal(Lang.get("gui.kve.recipe.recents_hint")).withStyle(ChatFormatting.GRAY));
            g.renderComponentTooltip(font, lines, mx, my);
        } else if (hov >= 0 || (mx >= x0 && mx < x0 + RecentItems.MAX * 12 && my >= y && my < y + 11 && rec.isEmpty())) {
            g.renderTooltip(font, Component.literal(Lang.get("gui.kve.recipe.recents_empty")), mx, my);
        }
    }

    private int recentAt(double mx, double my) {
        if (!recentsFit()) return -1;
        int x0 = recentsX, y = recentsY();
        if (my < y - 1 || my >= y + 11 || mx < x0 - 1) return -1;
        int i = (int) ((mx - x0 + 1) / 12);
        return i < RecentItems.MAX ? i : -1;
    }

    private boolean clickRecents(double mx, double my, int btn) {
        int i = recentAt(mx, my);
        if (i < 0) return false;
        List<ItemStack> rec = RecentItems.get();
        if (i >= rec.size()) return true;
        if (btn == 1) {
            if (!brush.isEmpty() && RecentItems.same(rec.get(i), brush)) brush = ItemStack.EMPTY;
            RecentItems.remove(i);
        } else {
            brush = !brush.isEmpty() && RecentItems.same(rec.get(i), brush) ? ItemStack.EMPTY : rec.get(i).copy();
            if (!brush.isEmpty()) flash(Lang.get("gui.kve.recipe.brush_on"));
        }
        return true;
    }

    private void cycleListSort() {
        RecipeListFilter.Sort[] all = RecipeListFilter.Sort.values();
        listSort = all[(listSort.ordinal() + 1) % all.length];
        refreshRecipeList();
        flash(Lang.get("gui.kve.recipe.sort." + listSort.name().toLowerCase(java.util.Locale.ROOT)));
        refresh();
    }

    private List<ItemStack> draftIngredients() {
        List<ItemStack> out = new ArrayList<>();
        if (isCombo()) { out.add(combinationCore); out.addAll(pedestals); }
        else out.addAll(java.util.Arrays.asList(gridSlots));
        return out;
    }

    /** "Coste: 8× iron_ingot, 1× stick" debajo de la grilla, si hay lugar. */
    private void drawCostLine(GuiGraphics g) {
        if (isCombo() || gridW <= 0) return;
        String cost = RecipeHelpers.describeCost(RecipeHelpers.cost(draftIngredients()));
        if (cost.isEmpty()) return;
        int y = goy() + (isNodeCanvas() ? GRID_PX : gridH * slotPx()) + 3;
        int outBottom = outY() + 20 + 12;
        if (secondaryOutputVisible()) outBottom = secOutY() + 18;
        y = Math.max(y, outBottom + 3);
        if (y + 9 > T + GUI_H - BOTTOM_ROW_OFF - 2) return;
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.recipe.cost_line", cost), gox(), y, (L + GUI_W - 6) - gox(), KveGuiStyle.TEXT_MUTED);
    }

    private List<String> liveWarnings(RecipeData d) {
        List<String> w = new ArrayList<>();
        String id = d.recipeId == null ? "" : d.recipeId.trim();
        if (!id.isEmpty() && (editing == null || !id.equals(editing.recipeId)) && RecipeDraftActions.recipeIdInUse(id)) {
            w.add(Lang.get("gui.kve.recipe.warn_id_taken", id));
        }
        if (d.output != null && !d.output.isEmpty() && d.output.getCount() > d.output.getMaxStackSize()) {
            w.add(Lang.get("gui.kve.recipe.warn_overstack", d.output.getCount(), d.output.getMaxStackSize()));
        }
        return w;
    }

    boolean gridToolsAvailable() { return !machineMode && !isCombo() && gridW > 0; }

    boolean rotateAvailable() { return gridW == gridH || isVariableGridCustomJson(); }

    boolean borderAvailable() { return gridW >= 3 && gridH >= 3; }

    boolean templateFits(RecipeTemplates.Template t) {
        if (!gridToolsAvailable()) return false;
        if (selType == RecipeData.RecipeType.VANILLA_SHAPELESS) return t.width() <= 3 && t.height() <= 3;
        return t.width() <= gridW && t.height() <= gridH;
    }

    void toolGrid(java.util.function.UnaryOperator<GridOps.Grid> op) {
        GridOps.Grid r = op.apply(GridOps.Grid.of(gridSlots, gridW, gridH));
        if (r == null) { flash("\u26a0 " + Lang.get("gui.kve.recipe.tools2.blocked")); return; }
        if ((r.w() != gridW || r.h() != gridH) && !isVariableGridCustomJson()) {
            flash("\u26a0 " + Lang.get("gui.kve.recipe.tools2.blocked"));
            return;
        }
        pushUndo();
        gridW = r.w();
        gridH = r.h();
        gridSlots = r.slots();
        refresh();
    }

    void toolFill(boolean border) {
        java.util.function.Consumer<ItemStack> apply = s -> toolGrid(g -> border ? GridOps.fillBorder(g, s) : GridOps.fillEmpty(g, s));
        if (!brush.isEmpty()) { minecraft.setScreen(this); apply.accept(brush); return; }
        minecraft.setScreen(new ItemPickerScreen(this, s -> { RecentItems.add(s); apply.accept(s); }, true).withTagOption());
    }

    void toolTemplate(RecipeTemplates.Template t) {
        minecraft.setScreen(new ItemPickerScreen(this, mat -> {
            RecentItems.add(mat);
            if (!t.usesSecond()) { applyTemplate(t, mat, ItemStack.EMPTY); return; }
            minecraft.tell(() -> minecraft.setScreen(new ItemPickerScreen(this, second -> {
                RecentItems.add(second);
                applyTemplate(t, mat, second);
            }, true)));
            flash(Lang.get("gui.kve.recipe.template.pick_second"));
        }, true));
    }

    private void applyTemplate(RecipeTemplates.Template t, ItemStack material, ItemStack second) {
        pushUndo();
        if (selType == RecipeData.RecipeType.VANILLA_SHAPELESS) {
            selType = RecipeData.RecipeType.VANILLA_SHAPED;
            gridW = 3; gridH = 3;
            initGrid();
        }
        GridOps.Grid g = RecipeTemplates.apply(t, material, second, gridW, gridH);
        if (g == null) { flash("\u26a0 " + Lang.get("gui.kve.recipe.tools2.blocked")); return; }
        gridSlots = g.slots();
        if (!outputSlot.isEmpty() && outputSlot.getCount() == 1) outputSlot.setCount(t.outputCount());
        flash(Lang.get(outputSlot.isEmpty() ? "gui.kve.recipe.template.done_pick_output" : "gui.kve.recipe.template.done"));
        refresh();
    }

    boolean reverseAvailable() { return !machineMode && RecipeHelpers.reverseOf(buildDraftRecipeData()) != null; }

    void toolReverse() {
        RecipeData r = RecipeHelpers.reverseOf(buildDraftRecipeData());
        if (r == null) return;
        loadDraftAsNew(r);
        flash(Lang.get("gui.kve.recipe.tools2.reverse_done"));
    }

    boolean cookingAvailable() { return !machineMode && RecipeHelpers.isCooking(buildDraftRecipeData()); }

    boolean cookingVariantAvailable(String type) { return !type.equals(selCustomTypeId); }

    void toolCookingVariant(String type) {
        RecipeData r = RecipeHelpers.cookingVariant(buildDraftRecipeData(), type);
        if (r == null) { flash("\u26a0 " + Lang.get("gui.kve.recipe.tools2.cook_failed")); return; }
        loadDraftAsNew(r);
        flash(Lang.get("gui.kve.recipe.tools2.cook_done", type));
    }

    void toolPasteJson() {
        String clip = minecraft.keyboardHandler.getClipboard();
        try {
            var res = com.kve.common.recipe.VanillaRecipeImporter.parse(clip == null ? "" : clip);
            loadDraftAsNew(res.recipe());
            flash(res.warnings().isEmpty() ? Lang.get("gui.kve.recipe.tools2.paste_done")
                    : "\u26a0 " + Lang.get("gui.kve.recipe.tools2.paste_done") + " " + String.join("; ", res.warnings()));
        } catch (IllegalArgumentException ex) {
            flash("\u26a0 " + Lang.get("gui.kve.recipe.tools2.paste_failed", ex.getMessage()));
        }
    }

    void toolCopyJs() {
        minecraft.keyboardHandler.setClipboard(KubeJSExporter.previewRecipeJS(buildDraftRecipeData()));
        flash(Lang.get("gui.kve.recipe.tools2.copied"));
    }

    boolean giveAvailable() { return minecraft != null && minecraft.player != null && !RecipeHelpers.cost(draftIngredients()).isEmpty(); }

    void toolGiveIngredients() {
        if (minecraft.player == null) return;
        int sent = 0;
        for (var e : RecipeHelpers.cost(draftIngredients()).entrySet()) {
            String id = e.getKey();
            if (id.startsWith("#")) {
                var members = TagIngredient.members(id);
                if (members.isEmpty()) continue;
                id = String.valueOf(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(members.get(0)));
            }
            try {
                minecraft.player.connection.sendCommand("give @s " + id + " " + e.getValue());
                sent++;
            } catch (Throwable ignored) {}
        }
        flash(Lang.get("gui.kve.recipe.tools2.give_done", sent));
    }

    void toolDetails() {
        minecraft.setScreen(new RecipeDetailsScreen(this, draftCategory, draftNotes, (cat, notes) -> {
            draftCategory = cat;
            draftNotes = notes;
            flash(Lang.get("gui.kve.recipe.details.saved"));
        }));
    }

    boolean autoIdAvailable() { return !machineMode && !outputSlot.isEmpty(); }

    void toolAutoId() {
        String outId = KubeJSExporter.itemId(outputSlot);
        if (outId == null || recipeIdBox == null) return;
        String keep = editing != null ? editing.recipeId : null;
        recipeIdBox.setValue(RecipeHelpers.suggestId(outId, id -> !id.equals(keep) && RecipeDraftActions.recipeIdInUse(id)));
        flash(Lang.get("gui.kve.recipe.tools2.auto_id_done", recipeIdBox.getValue()));
    }

    private Button btn(String label, int x, int y, int w, Runnable action) {
        return KveGuiStyle.smartBtn(x, y, w, label, b -> action.run());
    }

    // sec.4.3: overload con h explicito
    // colapsa a BTN_H=14 general, igual que en NbtEditorScreen - h se
    // ignora a proposito, queda en la firma para no romper call sites.
    private Button btn(String label, int x, int y, int w, int h, Runnable action) {
        return KveGuiStyle.smartBtn(x, y, w, label, b -> action.run());
    }

    @Override public boolean isPauseScreen() { return false; }
}
