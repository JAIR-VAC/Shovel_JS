package com.kve.client.gui.properties;

import com.kve.client.gui.DiffPreviewScreen;
import com.kve.client.gui.EditorKeys;
import com.kve.client.gui.picker.ItemPickerScreen;
import com.kve.client.gui.KveConfirmScreen;
import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KvePropertyFormScreen;
import com.kve.client.gui.KveScaledEditBox;
import com.kve.client.gui.ListKeyNav;
import com.kve.client.gui.MiniItemIcon;
import com.kve.client.gui.mob.MobEditorScreen;
import com.kve.client.gui.recipe.RecipePreviewScreen;
import com.kve.client.gui.RegistryIndex;
import com.kve.client.gui.ResizeHandle;
import com.kve.client.gui.ScrollBar;
import com.kve.client.gui.SearchFilter;

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
import com.kve.common.kubejs.KubeJSBlockAttrExporter;
import com.kve.common.persist.CraftEditorState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * Item 1.1 of - "Pestania Properties de bloque
 * incompleta". Follows the exact same screen structure as
 * {@link ItemAttributeScreen} (grid + fields column + info/saved-list
 * column, Save/Delete/Reset/Undo/Preview, PendingChangeManager staging)
 * but scoped to the block properties {@link KubeJSBlockAttrExporter}
 * exports: mining speed (destroySpeed), blast resistance
 * (explosionResistance), light emission, friction, and requires-tool.
 * Item durability/stack/attack stats stay on {@link ItemAttributeScreen} -
 * they're item properties, not block ones.
 */
@OnlyIn(Dist.CLIENT)
public class BlockAttributeScreen extends Screen implements KveStudioTab {

    private static final int GUI_W  = 500;
    private static final int GUI_H  = 360;
    // 1.4: misma familia que ItemAttributeScreen
    // (misma disposicion grilla-fija/columna-vecina/INFO_W-fijo) - ver el
    // comentario extenso en ItemAttributeScreen sobre por que el rango es
    // 140-190 y no 100-220.
    private static final int LIST_W_MIN     = 140;
    private static final int LIST_W_MAX     = 190;
    private static final int LIST_W_DEFAULT = 150;
    private int INFO_W = 110;
    private static final int ROWS   = 12;
    private static final int COLS   = 7;
    private static final int SLOT   = 18;
    private static final int FW     = 80;
    private static final int GAP    = 17;

    private int L, T, LIST_W;
    private ResizeHandle listResize;

    private final List<Block> allBlocks = new ArrayList<>();
    private final List<Block> filtered  = new ArrayList<>();
    private ScrollBar blockScrollBar;
    private EditBox   searchBox;
    private String    searchFilter = "";
    private final ListKeyNav nav = new ListKeyNav();

    private Block selectedBlock = null;

    private double  draftDestroySpeed        = -1;
    private double  draftExplosionResistance = -1;
    private int     draftLightEmission       = -1;
    private double  draftFriction            = -1;
    private boolean draftRequiresTool        = false;
    private boolean draftRequiresToolSet     = false;
    private final List<String> draftHarvestTools = new ArrayList<>();
    private final List<String> draftHarvestToolItems = new ArrayList<>();
    private int     draftHarvestLevel        = -1;
    /** See KubeJSBlockAttrExporter.BlockEntry#requireExactHarvestTool - only meaningful once at least one draftHarvestToolItems entry is non-blank, since it's "must match one of the exact items picked", not the category+tier toggle above it. */
    private boolean draftRequireExactHarvestTool = false;
    private KubeJSBlockAttrExporter.BlockEntry draftPhysics = new KubeJSBlockAttrExporter.BlockEntry();
    private static final int MAX_HARVEST_TOOLS = KubeJSBlockAttrExporter.HARVEST_TOOLS.length - 1;
    private static final int TOOL_ROW_H = 12;
    private int toolsListX, toolsListY;
    private boolean draftEnabled             = true;
    private String  draftCategory            = "";
    private boolean readOnlyMode             = false;
    private boolean showMoreActions          = false;
    private String  currentBlockId           = "";

    private EditBox fDestroySpeed, fExplosionResistance, fLightEmission, fFriction;

    private double vanillaDestroySpeed        = -1;
    private double vanillaExplosionResistance = -1;
    private int    vanillaLightEmission       = -1;
    private double vanillaFriction            = -1;

    private final List<KubeJSBlockAttrExporter.BlockEntry> savedList = new ArrayList<>();
    private int savedScroll = 0;
    private ScrollBar savedScrollBar;
    private String flashMsg = "";
    private long   flashAt  = 0;

    private Screen returnScreen = null;

    private boolean hosted = false;
    private int hostedW = 0;
    private int hostedH = 0;

    private int effH() { return hosted ? Math.max(200, hostedH) : GUI_H; }

    private int effW() { return hosted ? Math.max(400, hostedW) : GUI_W; }

    private int infoIconSize = 32;
    private int infoButtonsTopY = 0;
    private int infoButtonsBottomY = 0;

    private Screen ownerTab = null;

    /** Fluent setter - ver {@link #ownerTab}. */
    public BlockAttributeScreen withOwnerTab(Screen s) {
        this.ownerTab = s;
        return this;
    }

    private enum BlockCreatorMode { NONE, LIST, EDITOR }
    private BlockCreatorMode blockCreatorMode = BlockCreatorMode.NONE;
    private CustomBlockCreatorListScreen blockCreatorList;
    private CustomBlockCreatorEditorScreen blockCreatorEditor;

    private Screen blockCreatorOwnerTab() { return ownerTab != null ? ownerTab : this; }

    private void openBlockCreator() {
        if (blockCreatorList == null) {
            blockCreatorList = new CustomBlockCreatorListScreen(this::closeBlockCreator, this::openBlockCreatorEditor);
        }
        blockCreatorMode = BlockCreatorMode.LIST;
        blockCreatorList.init(L, T, effW(), effH());
    }

    private void openBlockCreatorEditor(com.kve.common.customitem.CustomBlockDraft draftOrNull) {
        blockCreatorEditor = new CustomBlockCreatorEditorScreen(this::backToBlockCreatorList, draftOrNull);
        blockCreatorEditor.withOwnerTab(blockCreatorOwnerTab());
        blockCreatorEditor.withReturnScreen(returnScreen);
        blockCreatorMode = BlockCreatorMode.EDITOR;
        blockCreatorEditor.init(L, T, effW(), effH());
    }

    private void backToBlockCreatorList() {
        blockCreatorMode = BlockCreatorMode.LIST;
        blockCreatorList.refreshOnReturn();
    }

    private void closeBlockCreator() {
        blockCreatorMode = BlockCreatorMode.NONE;
    }

    private Screen activeBlockCreator() {
        return switch (blockCreatorMode) {
            case LIST -> blockCreatorList;
            case EDITOR -> blockCreatorEditor;
            case NONE -> null;
        };
    }

    private final String initialId;
    private boolean initialIdApplied = false;

    public BlockAttributeScreen() {
        this(null);
    }

    public BlockAttributeScreen(String initialId) {
        super(Lang.c("gui.kve.main.blocks"));
        this.initialId = initialId;
    }

    public BlockAttributeScreen withReturnScreen(Screen s) {
        this.returnScreen = s;
        return this;
    }

    @Override
    protected void init() {
        // KveStudioShell ("Properties" tab): si esto se reabre porque un
        // picker interno hizo minecraft.setScreen(this) para "volver" y
        // esta tab sigue siendo la tab activa de un shell vivo, reabrir el
        // shell en vez de reconstruirse standalone/centrada - mismo
        // criterio que ItemAttributeScreen#init().
        // Overload owner-aware (ver #ownerTab): screenRef real del shell es
        // PropertiesTabScreen mientras embebida, no this.
        if (KveStudioShell.reclaimIfHosted(this, ownerTab != null ? ownerTab : this, returnScreen)) return;
        hosted = false;
        L = (width  - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        buildAll();
    }

    @Override
    public void init(int x0, int y0, int w, int h) {
        // Ningun host (shell o PropertiesTabScreen embebiendola) pasa esta
        // tab por Minecraft#setScreen() mientras esta hosteada - por lo tanto
        // Screen#init(Minecraft, int, int), el unico lugar donde vanilla
        // llena minecraft/font, nunca corre para esta instancia. Sin este
        // bootstrap, buildAll() explota con NPE en el primer widget.
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
            listResize = new ResizeHandle("block_attr.list", LIST_W_MIN, LIST_W_MAX, LIST_W_DEFAULT);
        }
        LIST_W = listResize.width();
        INFO_W = hosted ? Math.min(200, Math.max(160, effW() * 3 / 10)) : 110;
        listResize.reposition(L, T + 14, T + effH());
        clearWidgets();
        setFocused(null);
        CraftEditorState.loadIfNeeded();

        searchBox = new KveScaledEditBox(font, L + 2, T + 16, LIST_W - 4, 11,
                Component.literal(Lang.get("gui.kve.search.block")));
        searchBox.setMaxLength(48);
        searchBox.setValue(searchFilter);
        // Sin hint el buscador era una caja vacia sin ningun rotulo
        // (KveScaledEditBox solo pinta kveHint, nunca el "message" del
        // constructor) - MobEditorScreen si lo tenia. Ver ItemAttributeScreen.
        searchBox.setHint(Component.literal(Lang.get("gui.kve.search.block")));
        searchBox.setResponder(s -> {
            searchFilter = s;
            rebuildFilter();
            blockScrollBar.setTotals((int) Math.ceil(filtered.size() / (double) COLS), ROWS);
            nav.reset();
        });
        addRenderableWidget(searchBox);

        int barX = L + 2 + COLS * SLOT + 2;
        if (blockScrollBar == null) {
            blockScrollBar = new ScrollBar(barX, T + 30, ROWS * SLOT, 0, ROWS);
        } else {
            blockScrollBar.setPosition(barX, T + 30);
        }

        int savedRows = 8;
        if (savedScrollBar == null) {
            savedScrollBar = new ScrollBar(L + effW() - 10, T + 28, savedRows * 14, 0, savedRows);
        } else {
            savedScrollBar.setPosition(L + effW() - 10, T + 28);
        }

        int ax = L + LIST_W + 100;
        KveGuiStyle.Layout ly = new KveGuiStyle.Layout(ax, T + 16, GAP);

        fDestroySpeed        = field(ly.x(), ly.row());
        fExplosionResistance = field(ly.x(), ly.row());
        fLightEmission       = field(ly.x(), ly.row());
        fFriction            = field(ly.x(), ly.row());
        ly.gap(4);

        addRenderableWidget(btn(Lang.get("gui.kve.block.requires_tool") + " "
                + Lang.get(draftRequiresTool ? "gui.kve.item.fire_yes" : "gui.kve.item.fire_no"),
                ly.x(), ly.row(), FW + 10, this::toggleRequiresTool));
        ly.gap(4);

        int toolRowY = ly.row();
        addRenderableWidget(btn(harvestToolLabel(), ly.x(), toolRowY, FW - 10, this::pickHarvestTool));
        addRenderableWidget(btn("X", ly.x() + FW - 14 + 4, toolRowY, 20, this::clearHarvestTool));
        ly.gap(4);

        toolsListX = ly.x();
        toolsListY = ly.row(MAX_HARVEST_TOOLS * TOOL_ROW_H);
        ly.gap(4);

        addRenderableWidget(btn(Lang.get("gui.kve.block.exact_tool") + " "
                + Lang.get(draftRequireExactHarvestTool ? "gui.kve.item.fire_yes" : "gui.kve.item.fire_no"),
                ly.x(), ly.row(), FW + 10, this::toggleRequireExactHarvestTool));
        ly.gap(4);
        // velocidad/salto al pisarlo, sonido, colision y
        // tick aleatorio - en un formulario aparte (una sola fila aca) para no
        // desbordar la columna cuando la pantalla va hosteada en el shell.
        addRenderableWidget(btn(Lang.get("gui.kve.block.physics.btn") + (draftPhysics.hasPhysicsTweaks() ? " *" : ""),
                ly.x(), ly.row(), FW + 10, this::openPhysicsForm));

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

        String saveL = Lang.get("gui.kve.btn.save"), deleteL = Lang.get("gui.kve.btn.delete"),
                dupL = Lang.get("gui.kve.btn.duplicate"), resetL = Lang.get("gui.kve.btn.reset"),
                previewL = Lang.get("gui.kve.btn.preview");
        KveGuiStyle.ActionBar bar = new KveGuiStyle.ActionBar(relocated, this::addRenderableWidget);
        bar.primary(saveL, b -> saveBlock());
        bar.button(Lang.get("gui.kve.btn.preview"), b -> previewBlock());
        bar.button(Lang.get("gui.kve.btn.duplicate"), b -> duplicateBlock());
        if (ownerTab == null) bar.more(Lang.get("gui.kve.blockcreator.btn"), () -> openBlockCreator(), ClientPermissionState.canEdit());
        bar.enabled(draftEnabled, b -> toggleEnabled());
        bar.state(Lang.get("gui.kve.item.readonly") + ": " + Lang.get(readOnlyMode ? "gui.kve.item.fire_yes" : "gui.kve.item.fire_no"), () -> toggleReadOnly());
        bar.history(Lang.get("gui.kve.btn.undo"), () -> undoBlock(), !currentBlockId.isEmpty() && KubeJSBlockAttrExporter.canUndo(currentBlockId));
        bar.history(Lang.get("gui.kve.btn.reset"), () -> resetDraft());
        bar.end(() -> deleteBlock());

        moreHeaders.clear();
        moreHeader(relocated, "gui.kve.more.edit", rx, panelRight);
        String tooltipLabel = draftTooltip.isEmpty()
                ? Lang.get("gui.kve.block.tooltip.btn")
                : Lang.get("gui.kve.block.tooltip.btn") + " (" + draftTooltip.size() + ")";
        addRenderableWidget(relocated.add(0, tooltipLabel, b -> openTooltipEditor()));
        if (!hosted) {
            Button pendBtn = addRenderableWidget(relocated.add(0,
                    Lang.get("gui.kve.pending.header", pendCount),
                    b -> Minecraft.getInstance().setScreen(new DiffPreviewScreen(this,
                            PendingChangeManager.snapshot(), PendingChangeManager::applyAll))));
            pendBtn.active = pendCount > 0;
        }

        infoButtonsBottomY = relocated.bottomY();
        int keepSavedScroll = savedScrollBar != null ? savedScrollBar.getScroll() : 0;
        savedScrollBar = new ScrollBar(L + effW() - 10, infoButtonsBottomY + 19, savedRowsVisible() * 14, savedList.size(), savedRowsVisible());
        savedScrollBar.setScroll(keepSavedScroll);

        if (!currentBlockId.isEmpty()) {
            addRenderableWidget(btn(
                    CraftEditorState.isFavoriteBlock(currentBlockId) ? "\u2605" : "\u2606",
                    L + effW() - 17, T + 16, 0, this::toggleFavoriteCurrent));
        }

        buildBlockList();
        rebuildFilter();
        int rows = (int) Math.ceil(Math.max(1, filtered.size()) / (double) COLS);
        blockScrollBar.setTotals(rows, ROWS);
        refreshSaved();

        if (!initialIdApplied) {
            initialIdApplied = true;
            if (initialId != null && !initialId.isEmpty()) {
                try {
                    ResourceLocation rl = ResourceLocation.parse(initialId);
                    Block b = BuiltInRegistries.BLOCK.getOptional(rl).orElse(null);
                    if (b != null) selectBlock(b);
                } catch (Exception ignored) {}
            }
        }
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

    private void buildBlockList() {
        if (!allBlocks.isEmpty()) return;
        allBlocks.addAll(RegistryIndex.blocks());
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
        for (Block block : allBlocks) {
            if (q.matches(RegistryIndex.id(block), RegistryIndex.name(block))) {
                filtered.add(block);
            }
        }
        filtered.sort((a, b) -> {
            boolean fa = isFavorite(a), fb = isFavorite(b);
            if (fa != fb) return fa ? -1 : 1;
            return 0;
        });
    }

    private boolean isFavorite(Block block) {
        String id = RegistryIndex.id(block);
        return !id.isEmpty() && CraftEditorState.isFavoriteBlock(id);
    }

    private void toggleFavoriteCurrent() {
        if (currentBlockId.isEmpty()) return;
        CraftEditorState.toggleFavoriteBlock(currentBlockId);
        flash(CraftEditorState.isFavoriteBlock(currentBlockId)
                ? Lang.get("gui.kve.block.favorite_add")
                : Lang.get("gui.kve.block.favorite_remove"));
        refresh();
    }

    private java.util.List<String> draftTooltip = new java.util.ArrayList<>();

    private void clearDraft() {
        draftDestroySpeed        = -1;
        draftExplosionResistance = -1;
        draftLightEmission       = -1;
        draftFriction            = -1;
        draftRequiresTool        = false;
        draftRequiresToolSet     = false;
        draftHarvestTools.clear();
        draftHarvestToolItems.clear();
        draftHarvestLevel        = -1;
        draftRequireExactHarvestTool = false;
        draftPhysics = new KubeJSBlockAttrExporter.BlockEntry();
        draftEnabled             = true;
        draftCategory            = "";
        draftTooltip             = new java.util.ArrayList<>();
    }

    private void loadEntryToDraft(KubeJSBlockAttrExporter.BlockEntry e) {
        draftDestroySpeed        = e.destroySpeed        >= 0 ? e.destroySpeed        : vanillaDestroySpeed;
        draftExplosionResistance = e.explosionResistance >= 0 ? e.explosionResistance : vanillaExplosionResistance;
        draftLightEmission       = e.lightEmission       >= 0 ? e.lightEmission       : vanillaLightEmission;
        draftFriction            = e.friction            >= 0 ? e.friction            : vanillaFriction;
        draftRequiresTool        = e.requiresTool;
        draftRequiresToolSet     = e.requiresToolSet;
        draftHarvestTools.clear();
        draftHarvestToolItems.clear();
        if (e.harvestTools != null) {
            draftHarvestTools.addAll(e.harvestTools);
            if (e.harvestToolItems != null && e.harvestToolItems.size() == e.harvestTools.size()) {
                draftHarvestToolItems.addAll(e.harvestToolItems);
            } else {
                for (int i = 0; i < e.harvestTools.size(); i++) draftHarvestToolItems.add("");
            }
        }
        draftTooltip = e.tooltip != null ? new java.util.ArrayList<>(e.tooltip) : new java.util.ArrayList<>();
        draftHarvestLevel        = e.harvestLevel;
        draftRequireExactHarvestTool = e.requireExactHarvestTool;
        draftPhysics = e.copy();
        draftEnabled             = e.enabled;
        draftCategory            = e.category != null ? e.category : "";
    }

    private void loadVanillaToDraft(Block block) {
        clearDraft();
        computeVanillaBaseline(block);
        draftDestroySpeed        = vanillaDestroySpeed;
        draftExplosionResistance = vanillaExplosionResistance;
        draftLightEmission       = vanillaLightEmission;
        draftFriction             = vanillaFriction;
    }

    private void computeVanillaBaseline(Block block) {
        vanillaDestroySpeed        = roundTrip(block.defaultDestroyTime());
        vanillaExplosionResistance = roundTrip(block.getExplosionResistance());
        vanillaLightEmission       = block.defaultBlockState().getLightEmission();
        vanillaFriction             = roundTrip(block.getFriction());
    }

    /** Redondea v por el mismo camino fmt()->parseDouble() que ya sufre cualquier valor tipeado en un EditBox, para que comparar contra un baseline vanilla no dependa de precision float/double que nunca se ve en pantalla. */
    private double roundTrip(double v) {
        try { return Double.parseDouble(fmt(v)); } catch (Exception ex) { return v; }
    }

    private void applyDraftToFields() {
        setF(fDestroySpeed,        draftDestroySpeed        >= 0 ? fmt(draftDestroySpeed)        : "");
        setF(fExplosionResistance, draftExplosionResistance >= 0 ? fmt(draftExplosionResistance) : "");
        setF(fLightEmission,       draftLightEmission       >= 0 ? String.valueOf(draftLightEmission) : "");
        setF(fFriction,            draftFriction            >= 0 ? fmt(draftFriction)            : "");
    }

    private void syncFieldsToDraft() {
        draftDestroySpeed        = parseDouble(fDestroySpeed);
        draftExplosionResistance = parseDouble(fExplosionResistance);
        draftLightEmission       = parseInt(fLightEmission);
        if (draftLightEmission > 15) draftLightEmission = 15;
        draftFriction            = parseDouble(fFriction);
    }

    private void setF(EditBox b, String v) { if (b != null) b.setValue(v); }
    private String fmt(double v) { return (v == Math.floor(v)) ? String.valueOf((long) v) : String.format(java.util.Locale.ROOT, "%.3f", v); }
    private int    parseInt(EditBox b)    { try { return Integer.parseInt(b.getValue().trim()); }  catch (Exception e) { return -1; } }
    private double parseDouble(EditBox b) { try { return Double.parseDouble(b.getValue().trim()); } catch (Exception e) { return -1; } }

    private boolean pendingDuplicate = false;

    private void duplicateBlock() {
        if (readOnlyMode) { flash(Lang.get("gui.kve.item.readonly")); return; }
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }
        if (selectedBlock == null) { flash(Lang.get("gui.kve.block.select_first")); return; }
        syncFieldsToDraft();
        pendingDuplicate = true;
        flash(Lang.get("gui.kve.block.duplicate_hint"));
    }

    private void selectBlock(Block block) {
        selectedBlock = block;
        ResourceLocation k = BuiltInRegistries.BLOCK.getKey(block);
        String id = k != null ? k.toString() : "";
        currentBlockId = id;
        if (pendingDuplicate) {
            pendingDuplicate = false;
            computeVanillaBaseline(block);
            applyDraftToFields();
            if (KubeJSBlockAttrExporter.getEntry(id) != null) {
                flash(Lang.get("gui.kve.block.duplicate_target_conflict"));
            }
            return;
        }
        computeVanillaBaseline(block);
        var saved = KubeJSBlockAttrExporter.getEntry(id);
        if (saved != null) {
            loadEntryToDraft(saved);
        } else {
            loadVanillaToDraft(block);
            draftEnabled = true;
            draftCategory = "";
        }
        applyDraftToFields();
        refresh();
    }

    private void toggleRequiresTool() {
        syncFieldsToDraft();
        draftRequiresTool = !draftRequiresTool;
        draftRequiresToolSet = true;
        refresh();
    }

    private void toggleRequireExactHarvestTool() {
        syncFieldsToDraft();
        draftRequireExactHarvestTool = !draftRequireExactHarvestTool;
        refresh();
    }

    private String harvestToolLabel() {
        if (draftHarvestTools.isEmpty()) {
            return Lang.get("gui.kve.block.harvest_tool.add");
        }
        String levelName = draftHarvestLevel >= 0
                ? Lang.get("gui.kve.block.harvest_level." + draftHarvestLevel)
                : Lang.get("gui.kve.block.harvest_level.any");
        return Lang.get("gui.kve.block.harvest_tool") + " (" + draftHarvestTools.size() + ", " + levelName + ")";
    }

    private void pickHarvestTool() {
        syncFieldsToDraft();
        minecraft.setScreen(new ItemPickerScreen(this, stack -> {
            String[] classified = classifyToolItem(stack.getItem());
            if (classified == null) {
                flash(Lang.get("gui.kve.block.harvest_tool.invalid"));
                return;
            }
            String category = classified[0];
            ResourceLocation itemKey = BuiltInRegistries.ITEM.getKey(stack.getItem());
            String itemId = itemKey != null ? itemKey.toString() : "";
            int idx = draftHarvestTools.indexOf(category);
            if (idx < 0) {
                draftHarvestTools.add(category);
                draftHarvestToolItems.add(itemId);
            } else {
                draftHarvestToolItems.set(idx, itemId);
            }
            draftHarvestLevel = Integer.parseInt(classified[1]);
            refresh();
        }));
    }

    private void removeHarvestTool(String tool) {
        syncFieldsToDraft();
        int idx = draftHarvestTools.indexOf(tool);
        if (idx >= 0) {
            draftHarvestTools.remove(idx);
            draftHarvestToolItems.remove(idx);
        }
        refresh();
    }

    private void clearHarvestTool() {
        syncFieldsToDraft();
        draftHarvestTools.clear();
        draftHarvestToolItems.clear();
        draftHarvestLevel = -1;
        draftRequireExactHarvestTool = false;
        refresh();
    }

    private static ItemStack harvestToolIcon(String itemId, String toolCategory) {
        if (itemId != null && !itemId.isEmpty()) {
            try {
                ResourceLocation rl = ResourceLocation.parse(itemId);
                Item item = BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
                if (item != null) return RegistryIndex.stack(item);
            } catch (Exception ignored) {}
        }
        return switch (toolCategory) {
            case "pickaxe" -> RegistryIndex.stack(Items.IRON_PICKAXE);
            case "axe"     -> RegistryIndex.stack(Items.IRON_AXE);
            case "shovel"  -> RegistryIndex.stack(Items.IRON_SHOVEL);
            case "hoe"     -> RegistryIndex.stack(Items.IRON_HOE);
            default        -> ItemStack.EMPTY;
        };
    }

    private static String[] classifyToolItem(Item item) {
        String category;
        if (item instanceof PickaxeItem) category = "pickaxe";
        else if (item instanceof AxeItem) category = "axe";
        else if (item instanceof ShovelItem) category = "shovel";
        else if (item instanceof HoeItem) category = "hoe";
        else return null;

        int level = 0;
        if (item instanceof DiggerItem digger) {
            level = tierLevel(digger.getTier());
        }
        return new String[]{category, String.valueOf(level)};
    }

    private void toggleEnabled() {
        if (readOnlyMode) { flash(Lang.get("gui.kve.item.readonly")); return; }
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }
        syncFieldsToDraft();
        draftEnabled = !draftEnabled;
        String id = currentBlockId;
        if (!id.isEmpty() && KubeJSBlockAttrExporter.getEntry(id) != null) {
            boolean previousEnabled = !draftEnabled;
            if (KubeJSBlockAttrExporter.stageSetEnabled(id, draftEnabled)) {
                PendingChangeManager.queue(PendingChange.of(PendingDomain.CONTENT, id, PendingChange.Action.EDIT, true,
                        () -> {
                            KubeJSBlockAttrExporter.commitBlocks();
                            KveChangeLog.record(KveChangeLog.Log.BLOCKS, PendingChange.Action.EDIT, id);
                        },
                        () -> {
                            KubeJSBlockAttrExporter.discardStagedSetEnabled(id, previousEnabled);
                            refreshSaved();
                        }));
                flash(Lang.get("gui.kve.item.restart_needed"));
                refreshSaved();
            }
        }
        refresh();
    }

    private void toggleReadOnly() {
        readOnlyMode = !readOnlyMode;
        refresh();
    }

    private void undoBlock() {
        if (readOnlyMode) { flash(Lang.get("gui.kve.item.readonly")); return; }
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }
        if (currentBlockId.isEmpty()) return;
        String id = currentBlockId;
        KubeJSBlockAttrExporter.BlockEntry beforeUndo = KubeJSBlockAttrExporter.getEntry(id);
        KubeJSBlockAttrExporter.BlockEntry restored = KubeJSBlockAttrExporter.stageUndo(id);
        if (restored != null) {
            loadEntryToDraft(restored);
            applyDraftToFields();
            PendingChangeManager.queue(PendingChange.of(PendingDomain.CONTENT, id, PendingChange.Action.EDIT, true,
                    () -> {
                        KubeJSBlockAttrExporter.commitBlocks();
                        KveChangeLog.record(KveChangeLog.Log.BLOCKS, PendingChange.Action.EDIT, id);
                    },
                    () -> {
                        KubeJSBlockAttrExporter.discardStagedUndo(id, restored, beforeUndo);
                        refreshSaved();
                    })
                    .withDiff(() -> KubeJSBlockAttrExporter.previewBlockJS(beforeUndo == null ? restored : beforeUndo),
                            () -> KubeJSBlockAttrExporter.previewBlockJS(restored)));
            flash(Lang.get("gui.kve.item.restart_needed"));
            refreshSaved();
        }
        refresh();
    }

    /**
 * formulario "Fisica y sonido". Todas son propiedades
     * reales de BlockKJS en KubeJS 6 (setSpeedFactor/setJumpFactor/
     * setSoundType/setHasCollision/setIsRandomlyTicking) y se aplican en el
     * mismo startup script que el resto de este editor (requiere reiniciar).
     */
    private void openPhysicsForm() {
        if (selectedBlock == null) { flash(Lang.get("gui.kve.block.select_hint_l1")); return; }
        syncFieldsToDraft();
        List<String> sounds = new ArrayList<>();
        List<String> soundLabels = new ArrayList<>();
        sounds.add("");
        soundLabels.add(Lang.get("gui.kve.form.unchanged"));
        for (String st : KubeJSBlockAttrExporter.SOUND_TYPES) { sounds.add(st); soundLabels.add(st.replace('_', ' ')); }
        List<KvePropertyFormScreen.Field> fields = List.of(
                KvePropertyFormScreen.Field.text("speed", Lang.get("gui.kve.block.physics.speed"), Lang.get("gui.kve.block.physics.speed.desc"),
                        draftPhysics.speedFactor >= 0 ? KvePropertyFormScreen.fmt(draftPhysics.speedFactor) : "", KvePropertyFormScreen.decimal(0, 5)),
                KvePropertyFormScreen.Field.text("jump", Lang.get("gui.kve.block.physics.jump"), Lang.get("gui.kve.block.physics.jump.desc"),
                        draftPhysics.jumpFactor >= 0 ? KvePropertyFormScreen.fmt(draftPhysics.jumpFactor) : "", KvePropertyFormScreen.decimal(0, 5)),
                KvePropertyFormScreen.Field.cycle("sound", Lang.get("gui.kve.block.physics.sound"), Lang.get("gui.kve.block.physics.sound.desc"),
                        draftPhysics.soundType == null ? "" : draftPhysics.soundType, sounds, soundLabels),
                KvePropertyFormScreen.Field.toggle("collision", Lang.get("gui.kve.block.physics.collision"), Lang.get("gui.kve.block.physics.collision.desc"),
                        draftPhysics.hasCollision == null ? "" : String.valueOf(draftPhysics.hasCollision)),
                KvePropertyFormScreen.Field.toggle("ticking", Lang.get("gui.kve.block.physics.ticking"), Lang.get("gui.kve.block.physics.ticking.desc"),
                        draftPhysics.randomlyTicking == null ? "" : String.valueOf(draftPhysics.randomlyTicking)));
        minecraft.setScreen(new KvePropertyFormScreen(this, Lang.get("gui.kve.block.physics.title"),
                List.of(Lang.get("gui.kve.block.physics.hint")), fields, vals -> {
            draftPhysics.speedFactor = vals.get("speed").isBlank() ? -1 : Double.parseDouble(vals.get("speed").replace(',', '.'));
            draftPhysics.jumpFactor = vals.get("jump").isBlank() ? -1 : Double.parseDouble(vals.get("jump").replace(',', '.'));
            draftPhysics.soundType = vals.get("sound");
            draftPhysics.hasCollision = vals.get("collision").isBlank() ? null : Boolean.valueOf(vals.get("collision"));
            draftPhysics.randomlyTicking = vals.get("ticking").isBlank() ? null : Boolean.valueOf(vals.get("ticking"));
            refresh();
        }));
    }

    private KubeJSBlockAttrExporter.BlockEntry buildDraftBlockEntry() {
        if (selectedBlock == null) return null;
        syncFieldsToDraft();
        ResourceLocation k = BuiltInRegistries.BLOCK.getKey(selectedBlock);
        if (k == null) return null;

        KubeJSBlockAttrExporter.BlockEntry e = new KubeJSBlockAttrExporter.BlockEntry();
        e.blockId              = k.toString();
        e.enabled               = draftEnabled;
        e.category              = draftCategory;
        e.destroySpeed          = draftDestroySpeed        != vanillaDestroySpeed        ? draftDestroySpeed        : -1;
        e.explosionResistance   = draftExplosionResistance != vanillaExplosionResistance ? draftExplosionResistance : -1;
        e.lightEmission         = draftLightEmission       != vanillaLightEmission       ? draftLightEmission       : -1;
        e.friction              = draftFriction            != vanillaFriction            ? draftFriction            : -1;
        e.requiresTool          = draftRequiresTool;
        e.requiresToolSet       = draftRequiresToolSet;
        e.harvestTools          = new ArrayList<>(draftHarvestTools);
        e.harvestToolItems      = new ArrayList<>(draftHarvestToolItems);
        e.harvestLevel          = draftHarvestLevel;
        e.requireExactHarvestTool = draftRequireExactHarvestTool;
        e.tooltip                 = new java.util.ArrayList<>(draftTooltip);
        e.speedFactor = draftPhysics.speedFactor; e.jumpFactor = draftPhysics.jumpFactor;
        e.soundType = draftPhysics.soundType; e.hasCollision = draftPhysics.hasCollision;
        e.randomlyTicking = draftPhysics.randomlyTicking;
        return e;
    }

    private void saveBlock() {
        if (readOnlyMode) { flash(Lang.get("gui.kve.item.readonly")); return; }
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }
        if (selectedBlock == null) { flash(Lang.get("gui.kve.block.select_first")); return; }

        KubeJSBlockAttrExporter.BlockEntry e = buildDraftBlockEntry();
        if (e == null) { flash(Lang.get("gui.kve.block.select_first")); return; }

        KubeJSBlockAttrExporter.BlockEntry previous = KubeJSBlockAttrExporter.getEntry(e.blockId);
        PendingChange.Action action = previous == null ? PendingChange.Action.CREATE : PendingChange.Action.EDIT;
        KubeJSBlockAttrExporter.stageBlock(e);
        PendingChangeManager.queue(PendingChange.of(PendingDomain.CONTENT, e.blockId, action, true,
                () -> {
                    KubeJSBlockAttrExporter.commitBlocks();
                    KveChangeLog.record(KveChangeLog.Log.BLOCKS, action, e.blockId);
                },
                () -> {
                    KubeJSBlockAttrExporter.discardStagedBlock(e.blockId, previous);
                    refreshSaved();
                })
                .withDiff(previous == null ? null : () -> KubeJSBlockAttrExporter.previewBlockJS(previous),
                        () -> KubeJSBlockAttrExporter.previewBlockJS(e)));

        currentBlockId = e.blockId;
        flash(Lang.get("gui.kve.item.restart_needed"));
        refreshSaved();
        refresh();
    }

    private void openTooltipEditor() {
        syncFieldsToDraft();
        minecraft.setScreen(new TooltipEditScreen(this, draftTooltip, this::applyDraftTooltip));
    }

    /** Callback from {@link TooltipEditScreen}'s Save button. */
    public void applyDraftTooltip(java.util.List<String> lines) {
        draftTooltip = lines;
        refresh();
    }

    private void previewBlock() {
        KubeJSBlockAttrExporter.BlockEntry e = buildDraftBlockEntry();
        if (e == null) { flash(Lang.get("gui.kve.block.select_first")); return; }
        String script = KubeJSBlockAttrExporter.previewBlockJS(e);
        minecraft.setScreen(new RecipePreviewScreen(this, e.blockId, script));
    }

    private String orphanedBlockId = null;

    private void deleteBlock() {
        if (readOnlyMode) { flash(Lang.get("gui.kve.item.readonly")); return; }
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }
        if (selectedBlock != null) {
            ResourceLocation k = BuiltInRegistries.BLOCK.getKey(selectedBlock);
            if (k != null) {
                String id = k.toString();
                KubeJSBlockAttrExporter.BlockEntry previous = KubeJSBlockAttrExporter.getEntry(id);
                KubeJSBlockAttrExporter.stageDeleteBlock(id);
                PendingChangeManager.queue(PendingChange.of(PendingDomain.CONTENT, id, PendingChange.Action.DELETE, true,
                        () -> {
                            KubeJSBlockAttrExporter.commitBlocks();
                            KveChangeLog.record(KveChangeLog.Log.BLOCKS, PendingChange.Action.DELETE, id);
                        },
                        () -> {
                            if (previous != null) KubeJSBlockAttrExporter.stageBlock(previous);
                            refreshSaved();
                        })
                        .withDiff(previous == null ? null : () -> KubeJSBlockAttrExporter.previewBlockJS(previous), null));
                loadVanillaToDraft(selectedBlock);
                applyDraftToFields();
                flash(Lang.get("gui.kve.item.restart_needed"));
                refreshSaved();
                return;
            }
        }

        if (orphanedBlockId != null) {
            String id = orphanedBlockId;
            KubeJSBlockAttrExporter.BlockEntry previous = KubeJSBlockAttrExporter.getEntry(id);
            KubeJSBlockAttrExporter.stageDeleteBlock(id);
            PendingChangeManager.queue(PendingChange.of(PendingDomain.CONTENT, id, PendingChange.Action.DELETE, true,
                    () -> {
                        KubeJSBlockAttrExporter.commitBlocks();
                        KveChangeLog.record(KveChangeLog.Log.BLOCKS, PendingChange.Action.DELETE, id);
                    },
                    () -> {
                        if (previous != null) KubeJSBlockAttrExporter.stageBlock(previous);
                        refreshSaved();
                    })
                    .withDiff(previous == null ? null : () -> KubeJSBlockAttrExporter.previewBlockJS(previous), null));
            orphanedBlockId = null;
            clearDraft();
            applyDraftToFields();
            flash(Lang.get("gui.kve.item.restart_needed"));
            refreshSaved();
            return;
        }
        flash(Lang.get("gui.kve.block.select_first"));
    }

    private void resetDraft() {
        if (selectedBlock != null) {
            computeVanillaBaseline(selectedBlock);
            loadVanillaToDraft(selectedBlock);
            draftEnabled = true;
            draftCategory = "";
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
        com.kve.client.gui.KveToast.applied(result);
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
        minecraft.setScreen(new KveChangeHistoryScreen(this, KveChangeLog.Log.BLOCKS,
                Lang.c("gui.kve.change_history.title", Lang.get("gui.kve.change_history.domain.blocks")),
                id -> {
                    ResourceLocation rl = ResourceLocation.tryParse(id);
                    Block found = rl != null ? BuiltInRegistries.BLOCK.getOptional(rl).orElse(null) : null;
                    if (found != null) selectBlock(found);
                    minecraft.setScreen(this);
                    refresh();
                }).withIcons(MiniItemIcon::block));
    }

    private void goBack() {
        if (KveStudioShell.exitIfHosted(this, ownerTab != null ? ownerTab : this, returnScreen)) return;
        minecraft.setScreen(returnScreen != null ? returnScreen : new KveStudioShell());
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

    private void flash(String m) { flashMsg = m; flashAt = System.currentTimeMillis(); }
    private void refreshSaved() {
        savedList.clear();
        savedList.addAll(KubeJSBlockAttrExporter.getEntries());
        savedList.sort((a, b) -> Boolean.compare(CraftEditorState.isFavoriteBlock(b.blockId), CraftEditorState.isFavoriteBlock(a.blockId)));
        if (savedScrollBar != null) savedScrollBar.setTotals(savedList.size(), savedRowsVisible());
    }

    private ItemStack iconFor(Block block) {
        ItemStack icon = RegistryIndex.blockIcon(block);
        return icon.isEmpty() ? null : icon;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Screen creator = activeBlockCreator();
        if (creator != null) { creator.render(g, mx, my, pt); return; }
        if (!hosted) renderTransparentBackground(g);
        drawFrame(g);
        drawBlockGrid(g, mx, my);
        drawAttrLabels(g);
        drawFieldHints(g);
        drawInfoPanel(g);
        drawMoreHeaders(g);
        drawHarvestToolsList(g, mx, my);
        listResize.render(g, mx, my);
        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
        drawGridTooltip(g, mx, my);
        if (flashAt > 0 && System.currentTimeMillis() - flashAt < 5000)
            KveGuiStyle.drawCenteredTextSafe(g, flashMsg, L + effW() / 2, T + effH() - 9, 0xFF66FF99);
    }

    private void drawFrame(GuiGraphics g) {
        KveGuiStyle.drawWindow(g, L, T, effW(), effH(), Lang.get("gui.kve.main.blocks"));
        g.fill(L + LIST_W, T + 14, L + LIST_W + 1, T + effH(), KveGuiStyle.th(0xFF2A4A6A));
        g.fill(L + effW() - INFO_W, T + 14, L + effW() - INFO_W + 1, T + effH(), KveGuiStyle.th(0xFF2A4A6A));
        boolean flashing = flashAt > 0 && System.currentTimeMillis() - flashAt < 5000;
        if (!flashing)
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.item.restart_hint"), L + LIST_W + 6, T + effH() - 9, effW() - LIST_W - INFO_W - 10, 0xFFB8883A);
    }

    private void drawBlockGrid(GuiGraphics g, int mx, int my) {
        int scroll = blockScrollBar.getScroll();
        int startX = L + 2, startY = T + 30;
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int idx = (row + scroll) * COLS + col;
                if (idx >= filtered.size()) break;
                Block block = filtered.get(idx);
                int sx = startX + col * SLOT, sy = startY + row * SLOT;
                boolean hov = mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16;
                boolean sel = block == selectedBlock;
                if (sel)      g.fill(sx - 1, sy - 1, sx + 17, sy + 17, KveGuiStyle.th(0xFF2A5E9A));
                else if (hov) g.fill(sx - 1, sy - 1, sx + 17, sy + 17, KveGuiStyle.BORDER_FIELD);
                g.fill(sx, sy, sx + 16, sy + 16, KveGuiStyle.th(0xFF080C18));
                ItemStack icon = iconFor(block);
                if (icon != null) {
                    try { g.renderItem(icon, sx, sy); } catch (Throwable ignored) {
                        g.fill(sx + 4, sy + 4, sx + 12, sy + 12, KveGuiStyle.th(0xFF334455));
                    }
                } else {
                    g.fill(sx + 4, sy + 4, sx + 12, sy + 12, KveGuiStyle.th(0xFF445566));
                }
                if (nav.isCursor(idx)) {
                    g.hLine(sx - 1, sx + 16, sy - 1, KveGuiStyle.ACCENT_GOLD);
                    g.hLine(sx - 1, sx + 16, sy + 16, KveGuiStyle.ACCENT_GOLD);
                    g.vLine(sx - 1, sy - 1, sy + 16, KveGuiStyle.ACCENT_GOLD);
                    g.vLine(sx + 16, sy - 1, sy + 16, KveGuiStyle.ACCENT_GOLD);
                }
                if (isFavorite(block)) {
                    g.pose().pushPose();
                    g.pose().translate(0, 0, 300);
                    KveGuiStyle.drawTextSafe(g, "\u2605", sx + 10, sy - 1, 8, 0xFFFFD966);
                    g.pose().popPose();
                }
            }
        }
        blockScrollBar.render(g);
        int totalRows = (int) Math.ceil(Math.max(1, filtered.size()) / (double) COLS);
        KveGuiStyle.drawTextSafe(g, (scroll + 1) + "/" + totalRows, L + 2, T + 30 + ROWS * SLOT + 2, LIST_W - 4, KveGuiStyle.th(0xFF556677));
    }

    private void drawAttrLabels(GuiGraphics g) {
        int lx = L + LIST_W + 6;
        int ax = L + LIST_W + 100; // must match the field column x in init()
        int labelMaxW = ax - lx - 2;
        KveGuiStyle.Layout ly = new KveGuiStyle.Layout(lx, T + 18, GAP);
        int c1 = KveGuiStyle.th(0xFF7BC8E0);

        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.block.destroy_speed"),        lx, ly.row() + 2, labelMaxW, c1);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.block.explosion_resistance"), lx, ly.row() + 2, labelMaxW, c1);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.block.light_emission"),       lx, ly.row() + 2, labelMaxW, c1);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.block.friction"),             lx, ly.row() + 2, labelMaxW, c1);
    }

    private void drawFieldHints(GuiGraphics g) {
        if (selectedBlock == null) return;
        int ax = L + LIST_W + 100;
        KveGuiStyle.Layout ly = new KveGuiStyle.Layout(ax, T + 16, GAP);
        int hintX = ax + FW + 3;
        int maxWidth = (L + effW() - INFO_W - 4) - hintX;

        KveGuiStyle.drawFieldHint(g, fDestroySpeed.getValue(),
                vanillaDestroySpeed >= 0 ? fmt(vanillaDestroySpeed) : null, hintX, ly.row(), maxWidth);
        KveGuiStyle.drawFieldHint(g, fExplosionResistance.getValue(),
                vanillaExplosionResistance >= 0 ? fmt(vanillaExplosionResistance) : null, hintX, ly.row(), maxWidth);
        KveGuiStyle.drawFieldHint(g, fLightEmission.getValue(),
                vanillaLightEmission >= 0 ? String.valueOf(vanillaLightEmission) : null, hintX, ly.row(), maxWidth);
        KveGuiStyle.drawFieldHint(g, fFriction.getValue(),
                vanillaFriction >= 0 ? fmt(vanillaFriction) : null, hintX, ly.row(), maxWidth);
    }

    /** Filas de la lista de modificados: las que entran en el alto libre del panel derecho. */
    private int savedRowsVisible() {
        int top = infoButtonsBottomY + 19, bottom = T + effH() - 14;
        return Math.max(1, (bottom - top) / 14);
    }

    private void drawInfoPanel(GuiGraphics g) {
        int rx = L + effW() - INFO_W + 4, ry = T + 16;

        int iconScale = infoIconSize / 16;
        int labelX = rx + infoIconSize + 6;
        int labelW = Math.max(40, (rx + INFO_W - 8) - labelX);

        if (selectedBlock == null) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.block.select_hint_l1"), rx, ry + 4, INFO_W - 8, KveGuiStyle.th(0xFF445566));
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.block.select_hint_l2"), rx, ry + 14, INFO_W - 8, KveGuiStyle.th(0xFF445566));
        } else {
            ItemStack icon = iconFor(selectedBlock);
            if (icon != null) {
                g.pose().pushPose();
                g.pose().translate(rx, ry, 0);
                g.pose().scale((float) iconScale, (float) iconScale, 1f);
                try { g.renderItem(icon, 0, 0); } catch (Throwable ignored) {}
                g.pose().popPose();
            }

            String name = KveGuiStyle.truncate(font, selectedBlock.getName().getString(), labelW);
            KveGuiStyle.drawTextSafe(g, name, labelX, ry, labelW, KveGuiStyle.TEXT_PRIMARY);

            ResourceLocation k = BuiltInRegistries.BLOCK.getKey(selectedBlock);
            if (k != null) {
                KveGuiStyle.drawTextSafe(g, k.getNamespace() + ":" + k.getPath(), labelX, ry + 10, labelW, KveGuiStyle.th(0xFF5A8AAA));
            }
        }

        int sy = infoButtonsBottomY + 6;
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.block.changed_list") + " (" + savedList.size() + "):", rx, sy, INFO_W - 8, KveGuiStyle.th(0xFFE8C96A));
        sy += 10;

        g.hLine(rx, rx + INFO_W - 8, sy, KveGuiStyle.th(0xFF2A4A6A));
        sy += 3;

        if (savedList.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.common.none"), rx, sy + 2, INFO_W - 8, KveGuiStyle.th(0xFF445566));
        } else {
            int visRows = savedRowsVisible();
            int scroll  = savedScrollBar.getScroll();
            int vis     = Math.min(visRows, savedList.size() - scroll);
            for (int i = 0; i < vis; i++) {
                var entry = savedList.get(i + scroll);
                int iy = sy + i * 14;
                boolean isSel = selectedBlock != null &&
                    BuiltInRegistries.BLOCK.getKey(selectedBlock) != null &&
                    BuiltInRegistries.BLOCK.getKey(selectedBlock).toString().equals(entry.blockId);

                boolean exists = false;
                ItemStack rowIcon = null;
                try {
                    var rl2 = ResourceLocation.parse(entry.blockId);
                    var chk = BuiltInRegistries.BLOCK.get(rl2);
                    exists = chk != Blocks.AIR;
                    if (exists) rowIcon = iconFor(chk);
                } catch (Exception ignored) {}

                g.fill(rx, iy, rx + INFO_W - 10, iy + 13,
                        isSel ? KveGuiStyle.BORDER_FIELD : exists ? KveGuiStyle.BG_FIELD : 0xFF2A1500);

                if (rowIcon != null) {
                    try { g.renderItem(rowIcon, rx, iy - 1); } catch (Throwable ignored) {}
                } else {
                    g.fill(rx + 2, iy + 1, rx + 14, iy + 13, exists ? KveGuiStyle.th(0xFF334455) : 0xFF442200);
                    if (!exists) KveGuiStyle.drawTextSafe(g, "?", rx + 5, iy + 2, 8, 0xFFFF8844);
                }

                String n = entry.blockId.contains(":") ? entry.blockId.split(":")[1] : entry.blockId;
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

    private boolean isBrokenHarvestToolItem(String itemId) {
        if (itemId == null || itemId.isEmpty()) return false;
        try {
            return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(itemId)).isEmpty();
        } catch (Exception ex) {
            return true;
        }
    }

    private void drawHarvestToolsList(GuiGraphics g, int mx, int my) {
        if (draftHarvestTools.isEmpty()) return;
        int rx = toolsListX;
        for (int i = 0; i < draftHarvestTools.size(); i++) {
            String tool = draftHarvestTools.get(i);
            int ry = toolsListY + i * TOOL_ROW_H;
            boolean hovRow = mx >= rx && mx < rx + FW + 10 && my >= ry && my < ry + TOOL_ROW_H - 1;
            if (hovRow) g.fill(rx, ry, rx + FW + 10, ry + TOOL_ROW_H - 1, KveGuiStyle.BORDER_FIELD);

            String itemId = i < draftHarvestToolItems.size() ? draftHarvestToolItems.get(i) : "";
            boolean broken = isBrokenHarvestToolItem(itemId);
            ItemStack icon = harvestToolIcon(itemId, tool);
            if (!icon.isEmpty()) {
                g.pose().pushPose();
                g.pose().translate(rx, ry - 2, 0);
                g.pose().scale(0.75f, 0.75f, 1f);
                try { g.renderItem(icon, 0, 0); } catch (Throwable ignored) {}
                g.pose().popPose();
            }

            String name = null;
            if (!itemId.isEmpty() && !broken) {
                try {
                    Item item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(itemId)).orElse(null);
                    if (item != null) name = RegistryIndex.name(item);
                } catch (Exception ignored) {}
            }
            if (name == null) name = Lang.get("gui.kve.block.harvest_tool." + tool);
            if (broken) name = "[!] " + name;
            KveGuiStyle.drawTextSafe(g, name, rx + 13, ry + 2, FW - 12, broken ? KveGuiStyle.TEXT_WARN : KveGuiStyle.TEXT_LABEL);

            int xBoxL = rx + FW + 10 - 9;
            boolean hovX = mx >= xBoxL && mx < xBoxL + 9 && my >= ry && my < ry + TOOL_ROW_H - 1;
            KveGuiStyle.drawTextSafe(g, "x", xBoxL + 1, ry + 2, 8, hovX ? 0xFFFF7766 : KveGuiStyle.th(0xFF667788));
        }
    }

    private void drawGridTooltip(GuiGraphics g, int mx, int my) {
        int scroll = blockScrollBar.getScroll(), startX = L + 2, startY = T + 30;
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int idx = (row + scroll) * COLS + col;
                if (idx >= filtered.size()) break;
                int sx = startX + col * SLOT, sy = startY + row * SLOT;
                if (mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16) {
                    ItemStack icon = iconFor(filtered.get(idx));
                    if (icon != null) g.renderTooltip(font, icon, mx, my);
                    else g.renderTooltip(font, Component.literal(filtered.get(idx).getName().getString()), mx, my);
                    return;
                }
            }
        }
        if (nav.showCursorTooltip() && nav.cursor() < filtered.size() && mx < L + effW() - INFO_W) {
            int crow = nav.cursor() / COLS - scroll, ccol = nav.cursor() % COLS;
            if (crow >= 0 && crow < ROWS) {
                Block cb = filtered.get(nav.cursor());
                ItemStack cicon = iconFor(cb);
                int tx = startX + ccol * SLOT + 18, ty = startY + crow * SLOT + 12;
                if (cicon != null) g.renderTooltip(font, cicon, tx, ty);
                else g.renderTooltip(font, Component.literal(cb.getName().getString()), tx, ty);
            }
        }
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        Screen creator = activeBlockCreator();
        if (creator != null) return creator.charTyped(codePoint, modifiers);
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        // un menu "Mas" abierto recibe el clic antes que lo que tenga debajo
        if (com.kve.client.gui.KveMenuButton.consumeClick(mx, my, btn)) return true;
        Screen creator = activeBlockCreator();
        if (creator != null) return creator.mouseClicked(mx, my, btn);
        if (listResize.mouseClicked(mx, my)) return true;
        if (blockScrollBar.mouseClicked(mx, my, btn)) return true;
        if (savedScrollBar != null && savedScrollBar.mouseClicked(mx, my, btn)) return true;

        if (mx >= L + effW() - INFO_W + 4 && mx < L + effW() - 10) {
            int sy = infoButtonsBottomY + 19;
            int scroll = savedScrollBar != null ? savedScrollBar.getScroll() : 0;
            for (int i = 0; i < Math.min(savedRowsVisible(), savedList.size() - scroll); i++) {
                int iy = sy + i * 14;
                if (my >= iy && my < iy + 13) {
                    var entry = savedList.get(i + scroll);
                    try {
                        var rl = ResourceLocation.parse(entry.blockId);
                        var block = BuiltInRegistries.BLOCK.get(rl);
                        if (block != Blocks.AIR) {
                            orphanedBlockId = null;
                            selectBlock(block);
                        } else {
                            orphanedBlockId = entry.blockId;
                            selectedBlock = null;
                            flash("§c" + entry.blockId + " §r" + Lang.get("gui.kve.item.orphan_note"));
                        }
                    } catch (Exception ignored) {}
                    return true;
                }
            }
        }
        if (!draftHarvestTools.isEmpty()) {
            for (int i = 0; i < draftHarvestTools.size(); i++) {
                int ry = toolsListY + i * TOOL_ROW_H;
                if (my >= ry && my < ry + TOOL_ROW_H - 1 && mx >= toolsListX && mx < toolsListX + FW + 10) {
                    removeHarvestTool(draftHarvestTools.get(i));
                    return true;
                }
            }
        }
        int scroll = blockScrollBar.getScroll(), startX = L + 2, startY = T + 30;
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int idx = (row + scroll) * COLS + col;
                if (idx >= filtered.size()) break;
                int sx = startX + col * SLOT, sy = startY + row * SLOT;
                if (mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16) {
                    nav.setCursor(idx, filtered.size(), COLS, ROWS, blockScrollBar);
                    selectBlock(filtered.get(idx)); return true;
                }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        Screen creator = activeBlockCreator();
        if (creator != null) return creator.mouseDragged(mx, my, btn, dx, dy);
        if (listResize.mouseDragged(mx)) { refresh(); return true; }
        if (listResize.isDragging()) return true;
        if (blockScrollBar.mouseDragged(mx, my, btn, dx, dy)) return true; if (savedScrollBar != null && savedScrollBar.mouseDragged(mx, my, btn, dx, dy)) return true; return super.mouseDragged(mx, my, btn, dx, dy); }
    @Override public boolean mouseReleased(double mx, double my, int btn) {
        Screen creator = activeBlockCreator();
        if (creator != null) return creator.mouseReleased(mx, my, btn);
        listResize.mouseReleased(); blockScrollBar.mouseReleased(mx, my, btn); if (savedScrollBar != null) savedScrollBar.mouseReleased(mx, my, btn); return super.mouseReleased(mx, my, btn); }
    @Override public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        Screen creator = activeBlockCreator();
        if (creator != null) return creator.mouseScrolled(mx, my, scrollX, delta);
        if (mx >= L && mx < L + LIST_W) { blockScrollBar.mouseScrolled(delta); return true; }
        if (mx >= L + effW() - INFO_W && savedScrollBar != null) { savedScrollBar.mouseScrolled(delta); return true; }
        return super.mouseScrolled(mx, my, scrollX, delta);
    }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        Screen creator = activeBlockCreator();
        if (creator != null) return creator.keyPressed(key, scan, mods);
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (pendingDuplicate) { pendingDuplicate = false; flash(Lang.get("gui.kve.block.duplicate_cancelled")); return true; }
            onClose(); return true;
        }
        if (getFocused() == null || getFocused() == searchBox) {
            nav.clamp(filtered.size());
            if (nav.keyPressed(key, filtered.size(), COLS, ROWS, blockScrollBar)) return true;
            if (ListKeyNav.isConfirm(key) && nav.active()) { pickCursorBlock(); return true; }
        }
        if (getFocused() != null && getFocused().keyPressed(key, scan, mods)) return true;
        if ((mods & GLFW.GLFW_MOD_CONTROL) != 0 && key == GLFW.GLFW_KEY_D) { duplicateBlock(); return true; }
        if (EditorKeys.isSave(key, mods)) { saveBlock(); return true; }
        if (EditorKeys.isFocusTab(key, mods)) { super.keyPressed(key, scan, mods); return true; }
        return EditorKeys.swallow(mods); }

    private void pickCursorBlock() {
        int idx = nav.cursor();
        if (idx < 0 || idx >= filtered.size()) return;
        selectBlock(filtered.get(idx));
    }
    @Override public boolean isPauseScreen() { return false; }

    private static int tierLevel(net.minecraft.world.item.Tier tier) {
        if (tier instanceof net.minecraft.world.item.Tiers t) {
            return switch (t) {
                case WOOD, GOLD -> 0;
                case STONE -> 1;
                case IRON -> 2;
                case DIAMOND -> 3;
                case NETHERITE -> 4;
            };
        }
        var tag = tier.getIncorrectBlocksForDrops();
        if (tag == null) return 0;
        if (tag.equals(net.minecraft.tags.BlockTags.INCORRECT_FOR_NETHERITE_TOOL)) return 4;
        if (tag.equals(net.minecraft.tags.BlockTags.INCORRECT_FOR_DIAMOND_TOOL)) return 3;
        if (tag.equals(net.minecraft.tags.BlockTags.INCORRECT_FOR_IRON_TOOL)) return 2;
        if (tag.equals(net.minecraft.tags.BlockTags.INCORRECT_FOR_STONE_TOOL)) return 1;
        return 0;
    }
}
