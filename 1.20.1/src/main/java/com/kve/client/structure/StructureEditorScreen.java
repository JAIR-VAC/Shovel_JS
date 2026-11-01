package com.kve.client.structure;

import com.kve.common.structure.StructureVoxelGrid;

import com.kve.common.Lang;
import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KvePlateButton;
import com.kve.client.gui.RegistryIndex;
import com.kve.client.gui.ScrollBar;
import com.kve.client.gui.SearchFilter;
import com.kve.client.gui.WindowResizeHandle;
import com.kve.client.gui.workspace.pending.PendingDomain;
import com.kve.client.gui.workspace.pending.PendingChange;
import com.kve.client.gui.workspace.pending.PendingChangeManager;
import com.kve.client.gui.studio.KveStudioShell;
import com.kve.client.gui.studio.KveStudioTab;
import com.kve.client.permission.ClientPermissionState;
import com.kve.common.config.KVEConfig;
import com.kve.common.kubejs.SemanticValidator;
import com.kve.CraftEditorMod;
import com.kve.common.structure.StructureNbtIO;
import com.kve.common.structure.StructureSaveExecutor;
import com.kve.common.structure.StructureSpawnCap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import com.kve.client.gui.KveScaledEditBox;
import com.kve.client.gui.KveConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@OnlyIn(Dist.CLIENT)
public class StructureEditorScreen extends Screen implements KveStudioTab {

    private static final int GUI_W_MIN = 380, GUI_W_MAX = 700, GUI_W_DEFAULT = 460;
    private static final int GUI_H_MIN = 240, GUI_H_MAX = 520, GUI_H_DEFAULT = 280;
    private int L, T;
    private boolean hosted = false;
    private Screen returnScreen = null;
    private int GUI_W = GUI_W_DEFAULT;
    private int GUI_H = GUI_H_DEFAULT;
    private WindowResizeHandle windowResize;
    private static final int SIDE_W = 100;
    private static final int SLOT = 18;
    private static final int COLS = 6;
    private static final int PALETTE_W = COLS * SLOT + 8;

    private final Screen parent;
    private final StructureVoxelGrid grid;
    private OrbitCamera camera;

    private static boolean hasRememberedCamera = false;
    private static final Vector3f rememberedTarget = new Vector3f();
    private static float rememberedYaw;
    private static float rememberedPitch;
    private static float rememberedDistance;

    private EditBox searchBox;
    private List<Block> allBlocks;
    private List<Block> filtered = new ArrayList<>();
    private String filteredForQuery;
    private ScrollBar paletteScroll;
    private BlockState selectedState = Blocks.STONE.defaultBlockState();

    private EditBox nameBox;
    private String flashMsg = "";
    private long flashAt = 0;
    private int flashColor = KveGuiStyle.TEXT_OK;
    private String sourceBadge = "";
    private int infoY;

    private static final int PANEL_BTN_H = 12;
    private static final int PANEL_ROW = PANEL_BTN_H + 2;
    private static final int PANEL_HEADER_H = 12;
    private static final int PANEL_GROUP_GAP = 4;
    private static final int INFO_H = 48;

    private record PanelWidget(net.minecraft.client.gui.components.AbstractWidget widget, int virtualY) {}
    private record PanelHeader(String langKey, int virtualY) {}
    private final List<PanelWidget> panelWidgets = new ArrayList<>();
    private final List<PanelHeader> panelHeaders = new ArrayList<>();
    private int panelTop, panelBottom, panelContentH;
    private static int panelScroll = 0;

    private int vpX, vpY, vpW, vpH;
    private boolean draggingViewport = false;
    private double dragStartX, dragStartY;
    private double dragTotal = 0;
    private long dragStartTime = 0;
    private int dragButton = -1;
    private BlockPos hoveredCell = null;
    private BlockPos lastNonNullHoveredCell = null;
    private boolean showGrid = true;

    private enum ToolMode { POINT, BOX, BOX_HOLLOW, LINE, GRID, BRUSH, SPAWN_CAP, REPLACE, SELECT, PASTE }
    private ToolMode toolMode = ToolMode.POINT;
    private BlockPos toolAnchor = null;
    private int gridToolSpacing = 3;
    private boolean brushPainting = false;
    private BlockPos lastBrushTarget = null;
    private net.minecraft.core.Direction.Axis brushAxis = null;
    private int brushPlane = 0;
    private int brushStrokePlaced = 0;
    /** Region marcada con la herramienta Seleccionar (esquinas inclusive), o null. */
    private BlockPos selA, selB;
    /** Ultima region copiada o cortada; sobrevive entre estructuras durante la sesion. */
    private static StructureVoxelGrid.Clip clipboard;
    private BlockPos hoveredAdjacent = null;
    private Button replaceToolButton, selectToolButton, layerButton;

    private final java.util.Map<Button, String> shiftTipKeys = new java.util.IdentityHashMap<>();

    private <T extends Button> T tip(T b, String langKey) {
        shiftTipKeys.put(b, langKey);
        return b;
    }

    private void drawShiftTips(GuiGraphics g, int mx, int my) {
        if (!hasShiftDown()) return;
        var win = Minecraft.getInstance().getWindow();
        int screenW = win.getGuiScaledWidth();
        int screenH = win.getGuiScaledHeight();
        for (java.util.Map.Entry<Button, String> e : shiftTipKeys.entrySet()) {
            Button b = e.getKey();
            if (!b.visible) continue;
            if (KveGuiStyle.isHovered(mx, my, b.getX(), b.getY(), b.getWidth(), b.getHeight())) {
                KveGuiStyle.drawFramedTooltip(g, Lang.get(e.getValue()), mx, my, screenW, screenH);
                return;
            }
        }
    }

    private Button undoButton;
    private Button redoButton;
    private Button ghostToggleButton;
    private Button toolTypeToggleButton;
    private boolean toolTypeMenuOpen = false;
    private Button pointToolButton, boxToolButton, hollowToolButton, lineToolButton, gridToolButton, brushToolButton;
    private Button spawnCapToolButton;

    private Button includeMobsButton;

    private boolean includeMobsOnScan = false;

    private static final boolean INCLUDE_AIR_ON_SCAN = true;

    private Button sealDetectButton;

    private boolean sealDetectOnScan = false;

    private Button terrainAdjustmentButton;

    private String initialId;

    public StructureEditorScreen(Screen parent) {
        this(parent, null);
    }

    /** KveStudioShell construye esta tab sin parent (ver constructor de
     * KveStudioShell, mismo patron que MobEditorScreen/TagEditorScreen):
     * el "volver" real mientras esta hosteada pasa por {@link #returnScreen}
     * (seteado via {@link #withReturnScreen()}), no por este parent null. */
    public StructureEditorScreen() {
        this(null, null);
    }

    /** Fluent setter, mismo patron que {@code MobEditorScreen#withReturnScreen()}/
     * {@code LootTableEditorScreen#withReturnScreen()}: a donde vuelve
     * Cerrar/ESC mientras esta tab esta hosteada dentro de un KveStudioShell. */
    public StructureEditorScreen withReturnScreen(Screen s) {
        this.returnScreen = s;
        return this;
    }

    /** @param initialId namespace:path to auto-load on open (via the same {@link #doLoad()} path as the Load button), or null/blank for a blank editor. */
    public StructureEditorScreen(Screen parent, String initialId) {
        super(Lang.c("gui.kve.structure.title"));
        this.parent = parent;
        this.initialId = initialId;
        StructureVoxelGrid resume = initialId == null ? StructureGhostPreview.getGrid() : null;
        this.grid = resume != null ? resume : new StructureVoxelGrid();
        StructureGhostPreview.setGrid(this.grid);
    }

    @Override
    protected void init() {
        // "retorno al shell" (mismo patron que MobEditorScreen/
        // LootTableEditorScreen - ver Javadoc de
        // KveStudioShell#reclaimIfHosted()): si esto se esta reabriendo
        // porque un picker/dialogo hijo (StructurePickerScreen,
        // StructureHistoryScreen, StructureChangeHistoryScreen,
        // StructureFrequencyScreen, StructureBlockEntityNbtScreen, etc.)
        // hizo minecraft.setScreen(this) para "volver" y esta tab sigue
        // siendo la tab activa de un KveStudioShell vivo, reabrir el shell
        // en vez de reconstruirse standalone/centrada.
        if (KveStudioShell.reclaimIfHosted(this, returnScreen)) return;
        hosted = false;

        if (windowResize == null) {
            windowResize = new WindowResizeHandle("structure_editor.window",
                    GUI_W_MIN, GUI_W_MAX, GUI_H_MIN, GUI_H_MAX, GUI_W_DEFAULT, GUI_H_DEFAULT);
        }
        GUI_W = windowResize.width();
        GUI_H = windowResize.height();
        L = (width - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        windowResize.reposition(L, T, GUI_W, GUI_H);
        buildWidgets();
    }

    // (Structures): mismo
    // overload piloto que Tags/Loot/Mobs ya tienen - reemplaza SOLO el
    // origen de coordenadas (L/T/GUI_W/GUI_H) por el rectangulo que
    // KveStudioShell le asigna a esta tab, en vez del centrado+windowResize
    // propio de arriba. buildWidgets() no se toca (ya es relativo a
    // L/T/GUI_W/GUI_H, nunca a width/height de Screen).
    @Override
    public void init(int x0, int y0, int w, int h) {
        // Mismo bootstrap que MobEditorScreen#init(x,y,w,h): el shell nunca
        // pasa esta tab por Minecraft#setScreen() (ver
        // KveStudioShell#reclaimIfHosted()), asi que Screen#init(Minecraft,
        // int,int) nunca corre para esta instancia mientras esta hosteada.
        // Sin este guard, buildWidgets() explota con NPE en el primer
        // widget que use font. Null-check: si en algun momento SI vino de
        // un init(Minecraft,...) real (standalone reusando la misma
        // instancia), no lo pisamos.
        if (this.minecraft == null) {
            this.minecraft = Minecraft.getInstance();
            this.font = this.minecraft.font;
        }
        hosted = true;
        // El shell puede ofrecer mas ancho/alto que el minimo historico de
        // esta pantalla; nunca menos (evita solapar los campos fijos de
        // buildWidgets(), que siguen asumiendo el minimo actual). A
        // diferencia del modo standalone, GUI_W/GUI_H hosted NO pasan por
        // windowResize - el shell es dueno del rectangulo, esta tab no
        // tiene su propio drag-to-resize mientras vive adentro (ver el
        // guard `!hosted` en mouseClicked()/mouseDragged() mas abajo).
        GUI_W = Math.max(w, GUI_W_MIN);
        GUI_H = Math.max(h, GUI_H_MIN);
        L = x0;
        T = y0;
        buildWidgets();
    }

    private void refresh() {
        if (!hosted && windowResize != null) {
            GUI_W = windowResize.width();
            GUI_H = windowResize.height();
            L = (width - GUI_W) / 2;
            T = (height - GUI_H) / 2;
            windowResize.reposition(L, T, GUI_W, GUI_H);
        }
        buildWidgets();
    }

    private void buildWidgets() {
        String prevFilter = searchBox != null ? searchBox.getValue() : "";
        clearWidgets();
        setFocused(null);
        recomputePaletteVisibleRows();

        if (camera == null) {
            camera = new OrbitCamera(
                    KVEConfig.structureEditorMaxZoom * 0.4f,
                    KVEConfig.structureEditorMinZoom,
                    KVEConfig.structureEditorMaxZoom,
                    KVEConfig.structureEditorDefaultFov);
            if (hasRememberedCamera) {
                camera.setTarget(rememberedTarget);
                camera.setYawPitch(rememberedYaw, rememberedPitch);
                camera.setDistance(rememberedDistance);
            }
        }

        int guiLeft = L;
        int guiTop = T;
        int px = guiLeft + KveGuiStyle.PAD;
        int py = guiTop + 18;
        searchBox = new KveScaledEditBox(font, px, py, PALETTE_W - 8, 11, Component.literal(Lang.get("gui.kve.search.structure")));
        searchBox.setMaxLength(64);
        searchBox.setValue(prevFilter);
        searchBox.setResponder(s -> { rebuildPaletteFilter(s); updatePaletteScroll(); });
        addRenderableWidget(searchBox);
        setFocused(searchBox);

        if (allBlocks == null) {
            allBlocks = new ArrayList<>(RegistryIndex.blocks().size());
            for (Block b : RegistryIndex.blocks()) {
                if (b == Blocks.STRUCTURE_VOID) continue;
                allBlocks.add(b);
            }
        }
        if (!java.util.Objects.equals(filteredForQuery, prevFilter)) {
            rebuildPaletteFilter(prevFilter);
            filteredForQuery = prevFilter;
        }

        paletteScroll = new ScrollBar(px + COLS * SLOT + 2, py + 14, paletteVisibleRows * SLOT, 0, paletteVisibleRows);
        updatePaletteScroll();

        vpX = px + PALETTE_W + KveGuiStyle.PAD;
        vpY = guiTop + 18;
        vpW = GUI_W - PALETTE_W - SIDE_W - KveGuiStyle.PAD * 4;
        vpH = GUI_H - 18 - 34;

        int rx = guiLeft + GUI_W - KveGuiStyle.PAD - SIDE_W;
        KveGuiStyle.Layout right = new KveGuiStyle.Layout(rx, guiTop + 18, 14);
        int half = SIDE_W / 2 - 2, half2X = rx + SIDE_W / 2 + 2;

        nameBox = new KveScaledEditBox(font, right.x(), right.row(), SIDE_W, 11, Component.literal("namespace:path"));
        nameBox.setMaxLength(96);
        nameBox.setValue(grid.getId());
        nameBox.setResponder(grid::setId);
        addRenderableWidget(nameBox);

        right.gap(3);
        panelTop = right.nextY();
        panelBottom = guiTop + GUI_H - 34 - INFO_H;
        infoY = panelBottom + 6;
        panelWidgets.clear();
        panelHeaders.clear();
        int[] cursor = {0};

        undoButton = null; redoButton = null; spawnCapToolButton = null;
        includeMobsButton = null; sealDetectButton = null; terrainAdjustmentButton = null;

        panelHeader(cursor, "gui.kve.structure.section.file");
        panelPair(cursor, rx, half2X, half,
                tip(new KvePlateButton(0, 0, half, PANEL_BTN_H, Lang.c("gui.kve.btn.load"), b -> doLoad()), "gui.kve.structure.tip.load"),
                tip(new KvePlateButton(0, 0, half, PANEL_BTN_H, Lang.c("gui.kve.btn.save"), b -> doSave()).withPrimary(true), "gui.kve.structure.tip.save"));
        panelPair(cursor, rx, half2X, half,
                tip(new KvePlateButton(0, 0, half, PANEL_BTN_H, Lang.c("gui.kve.structure.btn.browse"), b -> doBrowse()), "gui.kve.structure.tip.browse"),
                tip(new KvePlateButton(0, 0, half, PANEL_BTN_H, Lang.c("gui.kve.structure.btn.new"), b -> doNewStructure()), "gui.kve.structure.tip.new"));
        panelPair(cursor, rx, half2X, half,
                tip(new KvePlateButton(0, 0, half, PANEL_BTN_H, Lang.c("gui.kve.structure.btn.history"), b -> doOpenHistory()), "gui.kve.structure.tip.history"),
                tip(new KvePlateButton(0, 0, half, PANEL_BTN_H, Lang.c("gui.kve.structure.btn.restore"), b -> doRestore()), "gui.kve.structure.tip.restore"));

        panelHeader(cursor, "gui.kve.structure.section.edit");
        // una fila de simbolos en vez de tres pares de texto; el nombre y la explicacion van en el tooltip
        undoButton = tip(new KvePlateButton(0, 0, half, PANEL_BTN_H, Lang.c("gui.kve.btn.undo"), b -> doUndo()), "gui.kve.structure.tip.undo");
        redoButton = tip(new KvePlateButton(0, 0, half, PANEL_BTN_H, Lang.c("gui.kve.btn.redo"), b -> doRedo()), "gui.kve.structure.tip.redo");
        panelPair(cursor, rx, half2X, half, undoButton, redoButton);
        panelPair(cursor, rx, half2X, half,
                tip(new KvePlateButton(0, 0, half, PANEL_BTN_H, Lang.c("gui.kve.structure.rotate"), b -> doRotate()), "gui.kve.structure.tip.rotate"),
                tip(new KvePlateButton(0, 0, half, PANEL_BTN_H, Lang.c("gui.kve.btn.clear"), b -> doClear()).withDanger(true), "gui.kve.structure.tip.clear"));
        panelPair(cursor, rx, half2X, half,
                tip(new KvePlateButton(0, 0, half, PANEL_BTN_H, Lang.c("gui.kve.structure.flip_x"), b -> doFlipX()), "gui.kve.structure.tip.flip_x"),
                tip(new KvePlateButton(0, 0, half, PANEL_BTN_H, Lang.c("gui.kve.structure.flip_z"), b -> doFlipZ()), "gui.kve.structure.tip.flip_z"));
        panelFull(cursor, rx, tip(new KvePlateButton(0, 0, SIDE_W, PANEL_BTN_H, Lang.c("gui.kve.structure.grid"), b -> showGrid = !showGrid), "gui.kve.structure.tip.grid_overlay"));

        panelHeader(cursor, "gui.kve.structure.section.region");
        panelPair(cursor, rx, half2X, half,
                tip(new KvePlateButton(0, 0, half, PANEL_BTN_H, Lang.c("gui.kve.structure.region.copy"), b -> doCopySelection(false)), "gui.kve.structure.tip.region_copy"),
                tip(new KvePlateButton(0, 0, half, PANEL_BTN_H, Lang.c("gui.kve.structure.region.cut"), b -> doCopySelection(true)), "gui.kve.structure.tip.region_cut"));
        panelPair(cursor, rx, half2X, half,
                tip(new KvePlateButton(0, 0, half, PANEL_BTN_H, Lang.c("gui.kve.structure.region.paste"), b -> startPaste()), "gui.kve.structure.tip.region_paste"),
                tip(new KvePlateButton(0, 0, half, PANEL_BTN_H, Lang.c("gui.kve.structure.region.delete"), b -> doDeleteSelection()), "gui.kve.structure.tip.region_delete"));

        panelHeader(cursor, "gui.kve.structure.section.view");
        layerButton = tip(new KvePlateButton(0, 0, SIDE_W, PANEL_BTN_H, Component.literal(layerLabel()), b -> toggleLayerCut()), "gui.kve.structure.tip.layers");
        panelFull(cursor, rx, layerButton);
        panelPair(cursor, rx, half2X, half,
                tip(new KvePlateButton(0, 0, half, PANEL_BTN_H, Lang.c("gui.kve.structure.layers.down"), b -> stepLayer(-1)), "gui.kve.structure.tip.layers"),
                tip(new KvePlateButton(0, 0, half, PANEL_BTN_H, Lang.c("gui.kve.structure.layers.up"), b -> stepLayer(1)), "gui.kve.structure.tip.layers"));
        panelFull(cursor, rx, tip(new KvePlateButton(0, 0, SIDE_W, PANEL_BTN_H, Lang.c("gui.kve.structure.materials.btn"), b -> openMaterials()), "gui.kve.structure.tip.materials"));

        panelHeader(cursor, "gui.kve.structure.section.world");
        panelPair(cursor, rx, half2X, half,
                tip(new KvePlateButton(0, 0, half, PANEL_BTN_H, Lang.c("gui.kve.structure.world.generate"), b -> doWorldGenerate()), "gui.kve.structure.tip.world_generate"),
                tip(new KvePlateButton(0, 0, half, PANEL_BTN_H, Lang.c("gui.kve.structure.world.scan"), b -> doWorldScan()), "gui.kve.structure.tip.world_scan"));
        panelFull(cursor, rx, tip(new KvePlateButton(0, 0, SIDE_W, PANEL_BTN_H, Lang.c("gui.kve.structure.ghost.anchor_here"), b -> doAnchorGhostHere()), "gui.kve.structure.tip.ghost_anchor"));
        includeMobsButton = tip(new KvePlateButton(0, 0, SIDE_W, PANEL_BTN_H, includeMobsLabel(), b -> toggleIncludeMobs()), "gui.kve.structure.tip.include_mobs");
        panelFull(cursor, rx, includeMobsButton);
        if (StructureWorldBuildSession.isEnabled()) {
            panelFull(cursor, rx, tip(new KvePlateButton(0, 0, SIDE_W, PANEL_BTN_H,
                    Lang.c("gui.kve.structure.world.close_session"), b -> doWorldCloseSession()), "gui.kve.structure.tip.close_session"));
        }

        panelHeader(cursor, "gui.kve.structure.section.generation");
        panelFull(cursor, rx, tip(new KvePlateButton(0, 0, SIDE_W, PANEL_BTN_H, Lang.c("gui.kve.structfreq.open_btn"), b -> doOpenFrequency()), "gui.kve.structure.tip.frequency"));
        spawnCapToolButton = tip(new KvePlateButton(0, 0, SIDE_W, PANEL_BTN_H, Lang.c("gui.kve.structure.spawncap.tool"), b -> setToolMode(ToolMode.SPAWN_CAP)), "gui.kve.structure.tip.spawncap");
        panelFull(cursor, rx, spawnCapToolButton);
        sealDetectButton = tip(new KvePlateButton(0, 0, SIDE_W, PANEL_BTN_H, sealDetectLabel(), b -> toggleSealDetect()), "gui.kve.structure.tip.seal_detect");
        panelFull(cursor, rx, sealDetectButton);
        terrainAdjustmentButton = tip(new KvePlateButton(0, 0, SIDE_W, PANEL_BTN_H, terrainAdjustmentLabel(), b -> cycleTerrainAdjustment()), "gui.kve.structure.tip.terrain_adjustment");
        panelFull(cursor, rx, terrainAdjustmentButton);

        panelContentH = cursor[0];
        for (PanelWidget pw : panelWidgets) addRenderableWidget(pw.widget);
        scrollRightPanel(0);

        int toolBtnCount = 2;
        int toolGap = 2;
        int toolBtnW = (vpW - toolGap * (toolBtnCount - 1)) / toolBtnCount;
        int toolBarY = vpY + vpH + 2;
        int tbx = vpX;
        toolTypeToggleButton = tip(new KvePlateButton(tbx, toolBarY, toolBtnW, 14, Lang.c("gui.kve.structure.tool.type_toggle", Lang.get(toolTypeLabelKey())), b -> toggleToolTypeMenu()), "gui.kve.structure.tip.tool_type_toggle");
        addRenderableWidget(toolTypeToggleButton);
        int dropdownX = tbx;
        tbx += toolBtnW + toolGap;
        ghostToggleButton = tip(new KvePlateButton(tbx, toolBarY, toolBtnW, 14, ghostLabel(), b -> toggleGhostPreview()), "gui.kve.structure.tip.ghost_toggle");
        addRenderableWidget(ghostToggleButton);

        int ddItemCount = 8;
        int ddY = toolBarY - ddItemCount * 16;
        pointToolButton = tip(new KvePlateButton(dropdownX, ddY, toolBtnW, 14, Lang.c("gui.kve.structure.tool.point"), b -> setToolMode(ToolMode.POINT)), "gui.kve.structure.tip.tool_point");
        pointToolButton.visible = false;
        addRenderableWidget(pointToolButton);
        ddY += 16;
        boxToolButton = tip(new KvePlateButton(dropdownX, ddY, toolBtnW, 14, Lang.c("gui.kve.structure.tool.box"), b -> setToolMode(ToolMode.BOX)), "gui.kve.structure.tip.tool_box");
        boxToolButton.visible = false;
        addRenderableWidget(boxToolButton);
        ddY += 16;
        hollowToolButton = tip(new KvePlateButton(dropdownX, ddY, toolBtnW, 14, Lang.c("gui.kve.structure.tool.box_hollow"), b -> setToolMode(ToolMode.BOX_HOLLOW)), "gui.kve.structure.tip.tool_hollow");
        hollowToolButton.visible = false;
        addRenderableWidget(hollowToolButton);
        ddY += 16;
        lineToolButton = tip(new KvePlateButton(dropdownX, ddY, toolBtnW, 14, Lang.c("gui.kve.structure.tool.line"), b -> setToolMode(ToolMode.LINE)), "gui.kve.structure.tip.tool_line");
        lineToolButton.visible = false;
        addRenderableWidget(lineToolButton);
        ddY += 16;
        gridToolButton = tip(new KvePlateButton(dropdownX, ddY, toolBtnW, 14, Lang.c("gui.kve.structure.tool.grid"), b -> setToolMode(ToolMode.GRID)), "gui.kve.structure.tip.tool_grid");
        gridToolButton.visible = false;
        addRenderableWidget(gridToolButton);
        ddY += 16;
        brushToolButton = tip(new KvePlateButton(dropdownX, ddY, toolBtnW, 14, Lang.c("gui.kve.structure.tool.brush"), b -> setToolMode(ToolMode.BRUSH)), "gui.kve.structure.tip.tool_brush");
        brushToolButton.visible = false;
        addRenderableWidget(brushToolButton);
        ddY += 16;
        replaceToolButton = tip(new KvePlateButton(dropdownX, ddY, toolBtnW, 14, Lang.c("gui.kve.structure.tool.replace"), b -> setToolMode(ToolMode.REPLACE)), "gui.kve.structure.tip.tool_replace");
        replaceToolButton.visible = false;
        addRenderableWidget(replaceToolButton);
        ddY += 16;
        selectToolButton = tip(new KvePlateButton(dropdownX, ddY, toolBtnW, 14, Lang.c("gui.kve.structure.tool.select"), b -> setToolMode(ToolMode.SELECT)), "gui.kve.structure.tip.tool_select");
        selectToolButton.visible = false;
        addRenderableWidget(selectToolButton);
        updateToolButtonStates();

        refreshSourceBadge();

        int pendCount = PendingChangeManager.size();
        int btnH = 14;
        int closeX = guiLeft + GUI_W - 18;
        int gap = 2;
        int changedW = 60;
        int changedX;
        if (!hosted) {
            String applyLbl = pendCount > 0 ? Lang.get("gui.kve.pending.apply") + " (" + pendCount + ")" : Lang.get("gui.kve.pending.apply");
            String discardLbl = pendCount > 0 ? Lang.get("gui.kve.pending.discard") + " (" + pendCount + ")" : Lang.get("gui.kve.pending.discard");
            int discardW = KveGuiStyle.btnWidth(discardLbl);
            int applyW = KveGuiStyle.btnWidth(applyLbl);
            int discardX = closeX - gap - discardW;
            int applyX = discardX - gap - applyW;
            changedX = applyX - gap - changedW;

            Button applyPendBtn = addRenderableWidget(tip(new KvePlateButton(applyX, guiTop + 2, applyW, btnH,
                    Component.literal(applyLbl), b -> onApplyPending()), "gui.kve.structure.tip.apply_pending"));
            applyPendBtn.active = pendCount > 0 && ClientPermissionState.canApply();
            Button discardPendBtn = addRenderableWidget(tip(new KvePlateButton(discardX, guiTop + 2, discardW, btnH,
                    Component.literal(discardLbl), b -> onDiscardPending()), "gui.kve.structure.tip.discard_pending"));
            discardPendBtn.active = pendCount > 0;
        } else {
            changedX = closeX - gap - changedW;
        }

        addRenderableWidget(tip(new KvePlateButton(changedX, guiTop + 2, changedW, btnH, Lang.c("gui.kve.structure.btn.changed"), b -> doOpenChangeHistory()), "gui.kve.structure.tip.changed"));

        if (!hosted) {
            addRenderableWidget(tip(new KvePlateButton(closeX, guiTop + 2, 16, 14, Component.literal(Lang.get("gui.kve.btn.close")), b -> onClose()), "gui.kve.structure.tip.close"));
        }

        if (initialId != null && !initialId.isEmpty()) {
            grid.setId(initialId);
            nameBox.setValue(initialId);
            initialId = null;
            doLoad();
        }
    }

    @Override
    public void onClose() {
        if (KveStudioShell.exitIfHosted(this, returnScreen)) return;
        minecraft.setScreen(returnScreen != null ? returnScreen : parent);
    }

    @Override
    public void removed() {
        grid.clearLayerCut();
        if (camera != null) {
            rememberedTarget.set(camera.target());
            rememberedYaw = camera.yaw();
            rememberedPitch = camera.pitch();
            rememberedDistance = camera.distance();
            hasRememberedCamera = true;
        }
        super.removed();
    }

    private void rebuildPaletteFilter(String q) {
        filtered.clear();
        for (Block b : allBlocks) {
            if (SearchFilter.matches(RegistryIndex.id(b), RegistryIndex.name(b), q)) filtered.add(b);
        }
    }

    private int paletteVisibleRows = 8;

    private void recomputePaletteVisibleRows() {
        int usable = GUI_H - 46;
        paletteVisibleRows = Math.max(4, usable / SLOT);
    }

    private void updatePaletteScroll() {
        if (paletteScroll == null) return;
        int rows = (int) Math.ceil(filtered.size() / (double) COLS);
        paletteScroll.setTotals(rows, paletteVisibleRows);
    }

    private void flash(String msg, boolean ok) {
        flashMsg = msg;
        flashColor = ok ? KveGuiStyle.TEXT_OK : KveGuiStyle.TEXT_ERROR;
        flashAt = System.currentTimeMillis();
    }

    private void doSave() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied"), false); return; }
        if (grid.getId() == null || grid.getId().isBlank()) {
            grid.setId(KveGuiStyle.randomId());
            if (nameBox != null) nameBox.setValue(grid.getId());
        }
        List<String> idErrors = SemanticValidator.validateNamespacedIdFormat(grid.getId(), "id");
        if (!idErrors.isEmpty()) { flash(idErrors.get(0), false); return; }
        if (grid.isEmpty()) { flash(Lang.get("gui.kve.structure.empty"), false); return; }
        if (grid.exceedsMaxEdge(KVEConfig.structureEditorMaxEdge)) {
            flash(Lang.get("gui.kve.structure.too_large", KVEConfig.structureEditorMaxEdge), false);
            return;
        }
        final ResourceLocation id;
        try {
            id = new ResourceLocation(grid.getId());
        } catch (RuntimeException e) {
            flash(Lang.get("gui.kve.structure.save.fail") + ": " + e.getMessage(), false);
            return;
        }

        StructureVoxelGrid snapshot = new StructureVoxelGrid();
        snapshot.copyFrom(grid);
        PendingChange.Action action = StructureNbtIO.isOverride(id)
                ? PendingChange.Action.EDIT : PendingChange.Action.CREATE;
        PendingChangeManager.queue(PendingChange.of(PendingDomain.WORLD, id.toString(), action, false,
                () -> {
                    StructureSaveExecutor.get().submit(() -> {
                        try {
                            StructureNbtIO.save(snapshot, id);
                            StructureChangeLog.record(action, id.toString());
                        } catch (Exception ex) {
                            CraftEditorMod.LOGGER.error(
                                    "[Shovel JS] Fallo al guardar estructura {} en background", id, ex);
                            Minecraft.getInstance().execute(() -> flash(
                                    Lang.get("gui.kve.structure.save.fail") + ": " + ex.getMessage(), false));
                        }
                    });
                },
                () -> { }));

        grid.recordHistory(Lang.get("gui.kve.structure.history.save", id.toString()));
        grid.markClean();
        flash(Lang.get("gui.kve.pending.queued_hint_reload"), true);
        refreshSourceBadge();
        refresh();
    }

    private void doLoad() {
        List<String> idErrors = SemanticValidator.validateNamespacedIdFormat(grid.getId(), "id");
        if (!idErrors.isEmpty()) { flash(idErrors.get(0), false); return; }
        ResourceLocation id;
        try {
            id = new ResourceLocation(grid.getId());
        } catch (RuntimeException e) {
            flash(Lang.get("gui.kve.structure.load.fail") + ": " + e.getMessage(), false);
            return;
        }
        try {
            applyLoadResult(StructureNbtIO.loadEffective(id));
        } catch (IOException | RuntimeException e) {
            if (isConnectedToRemoteServer()) {
                requestRemoteLoad(id);
            } else {
                flash(Lang.get("gui.kve.structure.load.fail") + ": " + e.getMessage(), false);
            }
        }
    }

    private boolean isConnectedToRemoteServer() {
        return minecraft != null && minecraft.getSingleplayerServer() == null && minecraft.getConnection() != null;
    }

    private void requestRemoteLoad(ResourceLocation id) {
        flash(Lang.get("gui.kve.structure.load.requesting"), true);
        StructureRemoteLoadState.request(id, this::applyLoadResult, this::onRemoteLoadFailure);
    }

    private void onRemoteLoadFailure(String idString) {
        flash(Lang.get("gui.kve.structure.load.fail") + ": " + Lang.get("gui.kve.structure.load.remote.notfound"), false);
    }

    private void applyLoadResult(StructureNbtIO.LoadResult result) {
        // Loading a DIFFERENT structure into this editor must not leave a
        // world-build session (and its rendered box) anchored at the
        // PREVIOUS structure's position - see StructureWorldBuildSession's
        // class doc, which already documented this as required but never
        // actually had a call site anywhere in the codebase, so the box
        // just stayed armed forever.
        StructureWorldBuildSession.clear();
        grid.copyFrom(result.grid);
        grid.clearUndoHistory();
        grid.recordHistory(Lang.get(result.fromOverride
                ? "gui.kve.structure.history.load.override"
                : "gui.kve.structure.history.load.original", grid.getId()));
        if (nameBox != null) nameBox.setValue(grid.getId());
        camera.setTarget(centerOfGrid());
        camera.setDistance(distanceToFrameGrid());
        flash(result.fromOverride
                ? Lang.get("gui.kve.structure.load.done.override")
                : Lang.get("gui.kve.structure.load.done.original"), true);
        refreshSourceBadge();
    }

    private void doOpenFrequency() {
        StructureFrequencyScreen.openFor(minecraft, this, grid.getId());
    }

    private void doBrowse() {
        List<ResourceLocation> ids = StructureNbtIO.listAvailableIds();
        List<String> idStrings = new ArrayList<>(ids.size());
        for (ResourceLocation r : ids) idStrings.add(r.toString());
        minecraft.setScreen(new StructurePickerScreen(this, idStrings, picked -> {
            grid.setId(picked);
            if (nameBox != null) nameBox.setValue(picked);
            refreshSourceBadge();
        }));
    }

    private void doRestore() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied"), false); return; }
        List<String> idErrors = SemanticValidator.validateNamespacedIdFormat(grid.getId(), "id");
        if (!idErrors.isEmpty()) { flash(idErrors.get(0), false); return; }
        String confirmId = grid.getId();
        minecraft.setScreen(new KveConfirmScreen(
                confirmed -> {
                    minecraft.setScreen(this);
                    if (confirmed) doRestoreConfirmed();
                },
                Component.literal(Lang.get("gui.kve.structure.restore_confirm_title")),
                Component.literal(Lang.get("gui.kve.structure.restore_confirm_msg", confirmId))));
    }

    private void doRestoreConfirmed() {
        try {
            ResourceLocation id = new ResourceLocation(grid.getId());
            boolean deleted = StructureNbtIO.restore(id);
            if (deleted) grid.recordHistory(Lang.get("gui.kve.structure.history.restore", id.toString()));
            flash(deleted ? Lang.get("gui.kve.structure.restore.done") : Lang.get("gui.kve.structure.restore.none"), deleted);
            refreshSourceBadge();
        } catch (IOException | RuntimeException e) {
            flash(Lang.get("gui.kve.structure.restore.fail") + ": " + e.getMessage(), false);
        }
    }

    private void refreshSourceBadge() {
        if (grid.getId().isEmpty()) { sourceBadge = ""; return; }
        try {
            ResourceLocation id = new ResourceLocation(grid.getId());
            sourceBadge = StructureNbtIO.isOverride(id)
                    ? Lang.get("gui.kve.structure.badge.override")
                    : Lang.get("gui.kve.structure.badge.original");
        } catch (RuntimeException ex) {
            sourceBadge = "";
        }
    }

    private void doNewStructure() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied"), false); return; }
        if (!grid.isEmpty() || grid.isDirty()) {
            minecraft.setScreen(new KveConfirmScreen(
                    confirmed -> {
                        minecraft.setScreen(this);
                        if (confirmed) resetToBlankStructure();
                    },
                    Component.literal(Lang.get("gui.kve.structure.new.confirm_title")),
                    Component.literal(Lang.get("gui.kve.structure.new.confirm_msg"))));
            return;
        }
        resetToBlankStructure();
    }

    private void resetToBlankStructure() {
        StructureWorldBuildSession.clear();
        grid.clear();
        grid.setId("");
        grid.setScannedEntities(java.util.List.of());
        grid.clearUndoHistory();
        grid.clearHistoryLog();
        grid.markClean();
        if (nameBox != null) nameBox.setValue("");
        camera.setTarget(centerOfGrid());
        camera.setDistance(distanceToFrameGrid());
        sourceBadge = "";
        flash(Lang.get("gui.kve.structure.new.done"), true);
        refresh();
    }

    private void doClear() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied"), false); return; }
        if (!grid.isEmpty()) {
            int cleared = grid.cellCount();
            grid.pushUndo();
            grid.clear();
            // los mobs escaneados tambien son parte de la estructura (deshacer los devuelve)
            grid.setScannedEntities(java.util.List.of());
            grid.recordHistory(Lang.get("gui.kve.structure.history.clear", cleared));
        }
        flash(Lang.get("gui.kve.structure.clear.done"), true);
    }

    private void doRotate() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied"), false); return; }
        if (grid.isEmpty()) return;
        grid.pushUndo();
        grid.rotate90();
        grid.recordHistory(Lang.get("gui.kve.structure.history.rotate"));
        flash(Lang.get("gui.kve.structure.rotate.done"), true);
    }

    private void doFlipX() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied"), false); return; }
        if (grid.isEmpty()) return;
        grid.pushUndo();
        grid.flipX();
        grid.recordHistory(Lang.get("gui.kve.structure.history.flip_x"));
        flash(Lang.get("gui.kve.structure.flip.done"), true);
    }

    private void doFlipZ() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied"), false); return; }
        if (grid.isEmpty()) return;
        grid.pushUndo();
        grid.flipZ();
        grid.recordHistory(Lang.get("gui.kve.structure.history.flip_z"));
        flash(Lang.get("gui.kve.structure.flip.done"), true);
    }

    private void doUndo() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied"), false); return; }
        if (!grid.canUndo()) return;
        grid.undo();
        if (nameBox != null) nameBox.setValue(grid.getId());
        grid.recordHistory(Lang.get("gui.kve.structure.history.undo"));
        flash(Lang.get("gui.kve.structure.undo.done"), true);
        refreshSourceBadge();
    }

    private void doRedo() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied"), false); return; }
        if (!grid.canRedo()) return;
        grid.redo();
        if (nameBox != null) nameBox.setValue(grid.getId());
        grid.recordHistory(Lang.get("gui.kve.structure.history.redo"));
        flash(Lang.get("gui.kve.structure.redo.done"), true);
        refreshSourceBadge();
    }

    private void doOpenHistory() {
        minecraft.setScreen(new StructureHistoryScreen(this, grid.historyView()));
    }

    private void doOpenChangeHistory() {
        minecraft.setScreen(new StructureChangeHistoryScreen(this, id -> {
            grid.setId(id);
            doLoad();
            minecraft.setScreen(this);
        }));
    }

    private void onApplyPending() {
        if (!ClientPermissionState.canApply()) { flash(Lang.get("gui.kve.perm.apply_denied"), false); return; }
        if (PendingChangeManager.isEmpty()) return;
        java.util.Set<PendingDomain> domains = PendingChangeManager.domainsPresent();
        if (domains.size() > 1) {
            int otherCount = PendingChangeManager.size() - PendingChangeManager.sizeForDomain(PendingDomain.WORLD);
            minecraft.setScreen(new KveConfirmScreen(
                    confirmed -> {
                        minecraft.setScreen(this);
                        applyPendingResult(confirmed ? PendingChangeManager.applyAll() : PendingChangeManager.applyDomain(PendingDomain.WORLD));
                    },
                    Component.literal(Lang.get("gui.kve.pending.other_domains_title")),
                    Component.literal(Lang.get("gui.kve.pending.other_domains_msg", otherCount))));
            return;
        }
        applyPendingResult(PendingChangeManager.applyDomain(PendingDomain.WORLD));
    }

    private void applyPendingResult(PendingChangeManager.ApplyResult result) {
        com.kve.client.gui.KveToast.applied(result);
        refreshSourceBadge();
        refresh();
    }

    private void onDiscardPending() {
        if (PendingChangeManager.isEmpty()) return;
        int count = PendingChangeManager.sizeForDomain(PendingDomain.WORLD);
        if (count == 0) return;
        PendingChangeManager.discardDomain(PendingDomain.WORLD);
        flash(Lang.get("gui.kve.pending.discarded", count), true);
        refreshSourceBadge();
        refresh();
    }

    private void cycleFacing() {
        if (hoveredCell != null && !grid.isAir(hoveredCell)) {
            if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied"), false); return; }
            BlockState current = grid.get(hoveredCell);
            BlockState cycled = cycleFacingProperty(current);
            if (cycled == current) {
                flash(Lang.get("gui.kve.structure.facing.none"), false);
                return;
            }
            grid.pushUndoForCell(hoveredCell);
            grid.rotateBlockInPlace(hoveredCell, cycled);
            grid.recordHistory(Lang.get("gui.kve.structure.history.rotate_block",
                    blockName(cycled), hoveredCell.getX(), hoveredCell.getY(), hoveredCell.getZ()));
            flash(Lang.get("gui.kve.structure.facing.cycled"), true);
            return;
        }
        BlockState cycled = cycleFacingProperty(selectedState);
        if (cycled == selectedState) {
            flash(Lang.get("gui.kve.structure.facing.none"), false);
            return;
        }
        selectedState = cycled;
        flash(Lang.get("gui.kve.structure.facing.cycled"), true);
    }

    private static BlockState cycleFacingProperty(BlockState state) {
        for (Property<?> prop : state.getProperties()) {
            String name = prop.getName().toLowerCase(Locale.ROOT);
            if (name.equals("facing") || name.equals("axis") || name.equals("rotation") || name.equals("orientation")) {
                return cycleProperty(state, prop);
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState cycleProperty(BlockState state, Property<T> prop) {
        List<T> values = new ArrayList<>(prop.getPossibleValues());
        int idx = values.indexOf(state.getValue(prop));
        T next = values.get((idx + 1) % values.size());
        return state.setValue(prop, next);
    }

    private void setToolMode(ToolMode mode) {
        closeToolTypeMenu();
        if (toolMode == mode) return;
        toolMode = mode;
        toolAnchor = null;
        brushPainting = false;
        lastBrushTarget = null;
        updateToolButtonStates();
    }

    private void updateToolButtonStates() {
        if (pointToolButton == null) return;
        pointToolButton.active = toolMode != ToolMode.POINT;
        boxToolButton.active = toolMode != ToolMode.BOX;
        hollowToolButton.active = toolMode != ToolMode.BOX_HOLLOW;
        lineToolButton.active = toolMode != ToolMode.LINE;
        gridToolButton.active = toolMode != ToolMode.GRID;
        brushToolButton.active = toolMode != ToolMode.BRUSH;
        if (spawnCapToolButton != null) spawnCapToolButton.active = toolMode != ToolMode.SPAWN_CAP;
        if (replaceToolButton != null) replaceToolButton.active = toolMode != ToolMode.REPLACE;
        if (selectToolButton != null) selectToolButton.active = toolMode != ToolMode.SELECT;
        if (toolTypeToggleButton != null) toolTypeToggleButton.setMessage(Lang.c("gui.kve.structure.tool.type_toggle", Lang.get(toolTypeLabelKey())));
    }

    private String toolTypeLabelKey() {
        return switch (toolMode) {
            case POINT -> "gui.kve.structure.tool.point";
            case BOX -> "gui.kve.structure.tool.box";
            case BOX_HOLLOW -> "gui.kve.structure.tool.box_hollow";
            case LINE -> "gui.kve.structure.tool.line";
            case GRID -> "gui.kve.structure.tool.grid";
            case BRUSH -> "gui.kve.structure.tool.brush";
            case SPAWN_CAP -> "gui.kve.structure.tool.point";
            case REPLACE -> "gui.kve.structure.tool.replace";
            case SELECT -> "gui.kve.structure.tool.select";
            case PASTE -> "gui.kve.structure.tool.paste";
        };
    }

    private void toggleToolTypeMenu() {
        toolTypeMenuOpen = !toolTypeMenuOpen;
        setDropdownButtonsVisible(toolTypeMenuOpen);
    }

    private void closeToolTypeMenu() {
        if (!toolTypeMenuOpen) return;
        toolTypeMenuOpen = false;
        setDropdownButtonsVisible(false);
    }

    private void renderToolTypeMenuOnTop(GuiGraphics g, int mx, int my, float pt) {
        net.minecraft.client.gui.components.AbstractWidget[] items = {
                pointToolButton, boxToolButton, hollowToolButton, lineToolButton, gridToolButton, brushToolButton,
                replaceToolButton, selectToolButton};
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE;
        for (var b : items) {
            if (b == null || !b.visible) continue;
            x0 = Math.min(x0, b.getX()); y0 = Math.min(y0, b.getY());
            x1 = Math.max(x1, b.getX() + b.getWidth()); y1 = Math.max(y1, b.getY() + b.getHeight());
        }
        if (x0 == Integer.MAX_VALUE) return;
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(x0 - 2, y0 - 2, x1 + 2, y1 + 2, KveGuiStyle.BORDER_OUTER);
        g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, KveGuiStyle.BG_PANEL);
        for (var b : items) if (b != null && b.visible) b.render(g, mx, my, pt);
        g.pose().popPose();
    }

    private void setDropdownButtonsVisible(boolean visible) {
        if (pointToolButton == null) return;
        pointToolButton.visible = visible;
        boxToolButton.visible = visible;
        hollowToolButton.visible = visible;
        lineToolButton.visible = visible;
        gridToolButton.visible = visible;
        brushToolButton.visible = visible;
        if (replaceToolButton != null) replaceToolButton.visible = visible;
        if (selectToolButton != null) selectToolButton.visible = visible;
    }

    private void handleToolClick(int button, VoxelRaycast.Hit hit, Vector3f dir) {
        if (button == 1) {
            if (toolAnchor != null) {
                toolAnchor = null;
                flash(Lang.get("gui.kve.structure.tool.cancelled"), true);
            } else if (hit != null) {
                BlockState removed = grid.get(hit.cell);
                grid.pushUndoForCell(hit.cell);
                grid.breakCell(hit.cell);
                grid.recordHistory(Lang.get("gui.kve.structure.history.break",
                        blockName(removed), hit.cell.getX(), hit.cell.getY(), hit.cell.getZ()));
            }
            return;
        }
        if (button != 0) return;

        BlockPos target = hit != null
                ? hit.adjacent
                : VoxelRaycast.castGroundPlane(camera.eyePosition(), dir, KVEConfig.structureEditorMaxEdge / 2);
        if (target == null) return;

        if (toolAnchor == null) {
            toolAnchor = target.immutable();
            flash(Lang.get("gui.kve.structure.tool.anchor_set"), true);
            return;
        }

        if (grid.wouldExceedMaxEdge(toolAnchor, target, KVEConfig.structureEditorMaxEdge)) {
            flash(Lang.get("gui.kve.structure.too_large", KVEConfig.structureEditorMaxEdge), false);
            toolAnchor = null;
            return;
        }

        grid.pushUndo();
        int changed = switch (toolMode) {
            case BOX -> grid.fillBox(toolAnchor, target, selectedState, false);
            case BOX_HOLLOW -> grid.fillBox(toolAnchor, target, selectedState, true);
            case LINE -> grid.fillLine(toolAnchor, target, selectedState);
            case GRID -> grid.fillGrid(toolAnchor, target, selectedState, gridToolSpacing);
            case POINT -> 0;
            case BRUSH -> 0;
            case SPAWN_CAP, REPLACE, SELECT, PASTE -> 0;
        };
        grid.recordHistory(Lang.get("gui.kve.structure.history.tool_fill", Lang.get(switch (toolMode) {
            case BOX -> "gui.kve.structure.tool.box";
            case BOX_HOLLOW -> "gui.kve.structure.tool.box_hollow";
            case LINE -> "gui.kve.structure.tool.line";
            case GRID -> "gui.kve.structure.tool.grid";
            case POINT -> "gui.kve.structure.tool.point";
            case BRUSH -> "gui.kve.structure.tool.brush";
            case SPAWN_CAP -> "gui.kve.structure.spawncap.tool";
            case REPLACE -> "gui.kve.structure.tool.replace";
            case SELECT -> "gui.kve.structure.tool.select";
            case PASTE -> "gui.kve.structure.tool.paste";
        }), changed, blockName(selectedState)));
        flash(Lang.get("gui.kve.structure.tool.filled", changed), true);
        toolAnchor = null;
    }

    private void handleReplaceClick(int button, VoxelRaycast.Hit hit) {
        if (button != 0 || hit == null) return;
        BlockState target = grid.get(hit.cell);
        if (target.isAir()) return;
        if (target.getBlock() == selectedState.getBlock()) {
            flash(Lang.get("gui.kve.structure.replace.same"), false);
            return;
        }
        // con una region seleccionada, el reemplazo queda limitado a ella
        List<BlockPos> targets = selA != null
                ? grid.cellsOfBlock(target.getBlock(), selA, selB)
                : grid.cellsOfBlock(target.getBlock(), null, null);
        if (targets.isEmpty()) return;
        grid.pushUndoForCells(targets);
        int n = grid.replaceCells(targets, selectedState);
        grid.recordHistory(Lang.get("gui.kve.structure.history.replace", n, blockName(target), blockName(selectedState)));
        flash(Lang.get("gui.kve.structure.replace.done", n, blockName(target), blockName(selectedState)), true);
    }

    private void handleSelectClick(int button, VoxelRaycast.Hit hit, Vector3f dir) {
        if (button == 1) { clearSelection(); return; }
        if (button != 0) return;
        BlockPos target = hit != null
                ? hit.cell
                : VoxelRaycast.castGroundPlane(camera.eyePosition(), dir, KVEConfig.structureEditorMaxEdge / 2);
        if (target == null) return;
        if (toolAnchor == null) {
            toolAnchor = target.immutable();
            selA = null;
            selB = null;
            flash(Lang.get("gui.kve.structure.tool.anchor_set"), true);
            return;
        }
        selA = toolAnchor;
        selB = target.immutable();
        toolAnchor = null;
        flash(Lang.get("gui.kve.structure.select.done", boxSizeText(selA, selB)), true);
    }

    private static String boxSizeText(BlockPos a, BlockPos b) {
        return (Math.abs(a.getX() - b.getX()) + 1) + "x" + (Math.abs(a.getY() - b.getY()) + 1) + "x" + (Math.abs(a.getZ() - b.getZ()) + 1);
    }

    private void clearSelection() {
        if (selA == null && toolAnchor == null) return;
        selA = null;
        selB = null;
        toolAnchor = null;
        flash(Lang.get("gui.kve.structure.select.cleared"), true);
    }

    private void doCopySelection(boolean cut) {
        if (selA == null) { flash(Lang.get("gui.kve.structure.select.none"), false); return; }
        if (cut && !ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied"), false); return; }
        StructureVoxelGrid.Clip clip = grid.copyRegion(selA, selB);
        if (clip.isEmpty()) { flash(Lang.get("gui.kve.structure.select.empty"), false); return; }
        clipboard = clip;
        if (cut) {
            List<BlockPos> cells = grid.cellsInBox(selA, selB);
            grid.pushUndoForCells(cells);
            grid.removeCells(cells);
            grid.recordHistory(Lang.get("gui.kve.structure.history.cut", cells.size()));
        }
        flash(Lang.get(cut ? "gui.kve.structure.region.cut_done" : "gui.kve.structure.region.copy_done", clip.cells().size()), true);
    }

    private void startPaste() {
        if (clipboard == null) { flash(Lang.get("gui.kve.structure.region.clipboard_empty"), false); return; }
        setToolMode(ToolMode.PASTE);
        flash(Lang.get("gui.kve.structure.region.paste_hint"), true);
    }

    private void handlePasteClick(int button, VoxelRaycast.Hit hit, Vector3f dir) {
        if (button == 1) { setToolMode(ToolMode.POINT); return; }
        if (button != 0 || clipboard == null) return;
        BlockPos origin = hit != null
                ? hit.adjacent
                : VoxelRaycast.castGroundPlane(camera.eyePosition(), dir, KVEConfig.structureEditorMaxEdge / 2);
        if (origin == null) return;
        BlockPos far = origin.offset(clipboard.sizeX() - 1, clipboard.sizeY() - 1, clipboard.sizeZ() - 1);
        if (grid.wouldExceedMaxEdge(origin, far, KVEConfig.structureEditorMaxEdge)) {
            flash(Lang.get("gui.kve.structure.too_large", KVEConfig.structureEditorMaxEdge), false);
            return;
        }
        grid.pushUndoForCells(grid.clipTargets(clipboard, origin));
        int n = grid.pasteClip(clipboard, origin);
        grid.recordHistory(Lang.get("gui.kve.structure.history.paste", n, origin.getX(), origin.getY(), origin.getZ()));
        flash(Lang.get("gui.kve.structure.region.paste_done", n), true);
    }

    private void doDeleteSelection() {
        if (selA == null) { flash(Lang.get("gui.kve.structure.select.none"), false); return; }
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied"), false); return; }
        List<BlockPos> cells = grid.cellsInBox(selA, selB);
        if (cells.isEmpty()) return;
        grid.pushUndoForCells(cells);
        grid.removeCells(cells);
        grid.recordHistory(Lang.get("gui.kve.structure.history.delete_region", cells.size()));
        flash(Lang.get("gui.kve.structure.region.delete_done", cells.size()), true);
    }

    private String layerLabel() {
        return grid.hasLayerCut()
                ? Lang.get("gui.kve.structure.layers.cut", grid.viewMaxY())
                : Lang.get("gui.kve.structure.layers.all");
    }

    private void toggleLayerCut() {
        if (grid.hasLayerCut()) { grid.clearLayerCut(); return; }
        BlockPos[] b = grid.computeBounds();
        if (b != null) grid.setViewMaxY(b[1].getY());
    }

    private void stepLayer(int delta) {
        BlockPos[] b = grid.computeBounds();
        if (b == null) return;
        int current = grid.hasLayerCut() ? grid.viewMaxY() : b[1].getY();
        grid.setViewMaxY(Math.max(b[0].getY(), Math.min(b[1].getY(), current + delta)));
    }

    private void openMaterials() {
        minecraft.setScreen(new StructureMaterialsScreen(this, grid::materialCounts, this::replaceAllOf));
    }

    /** "Reemplazar" de la lista de materiales: cambia todas las celdas de {@code from} por {@code to}, con deshacer. */
    private int replaceAllOf(net.minecraft.world.level.block.Block from, net.minecraft.world.level.block.Block to) {
        List<BlockPos> targets = grid.cellsOfBlock(from, null, null);
        if (targets.isEmpty() || from == to) return 0;
        grid.pushUndoForCells(targets);
        int n = grid.replaceCells(targets, to.defaultBlockState());
        grid.recordHistory(Lang.get("gui.kve.structure.history.replace", n,
                blockName(from.defaultBlockState()), blockName(to.defaultBlockState())));
        return n;
    }

    private void panelHeader(int[] cursor, String langKey) {
        if (cursor[0] > 0) cursor[0] += PANEL_GROUP_GAP;
        panelHeaders.add(new PanelHeader(langKey, cursor[0]));
        cursor[0] += PANEL_HEADER_H;
    }

    private void panelPair(int[] cursor, int leftX, int rightX, int w,
                           net.minecraft.client.gui.components.AbstractWidget a,
                           net.minecraft.client.gui.components.AbstractWidget b) {
        a.setX(leftX); a.setWidth(w);
        b.setX(rightX); b.setWidth(w);
        panelWidgets.add(new PanelWidget(a, cursor[0]));
        panelWidgets.add(new PanelWidget(b, cursor[0]));
        cursor[0] += PANEL_ROW;
    }

    /** Varios botones repartidos en el ancho del panel, en una sola fila. */
    private void panelRow(int[] cursor, int x, net.minecraft.client.gui.components.AbstractWidget... ws) {
        int gap = 2, w = (SIDE_W - gap * (ws.length - 1)) / ws.length;
        for (int i = 0; i < ws.length; i++) {
            ws[i].setX(x + i * (w + gap));
            ws[i].setWidth(w);
            panelWidgets.add(new PanelWidget(ws[i], cursor[0]));
        }
        cursor[0] += PANEL_ROW;
    }

    private void panelFull(int[] cursor, int x, net.minecraft.client.gui.components.AbstractWidget w) {
        w.setX(x); w.setWidth(SIDE_W);
        panelWidgets.add(new PanelWidget(w, cursor[0]));
        cursor[0] += PANEL_ROW;
    }

    private int panelMaxScroll() {
        return Math.max(0, panelContentH - (panelBottom - panelTop));
    }

    private void scrollRightPanel(int delta) {
        panelScroll = Math.max(0, Math.min(panelMaxScroll(), panelScroll + delta));
        for (PanelWidget pw : panelWidgets) {
            int y = panelTop + pw.virtualY() - panelScroll;
            pw.widget().setY(y);
            pw.widget().visible = y >= panelTop && y + PANEL_BTN_H <= panelBottom;
        }
    }

    private Component ghostLabel() {
        return Component.literal(Lang.get("gui.kve.structure.ghost.toggle") + ": "
                + Lang.get(StructureGhostPreview.isEnabled() ? "gui.kve.structure.ghost.on" : "gui.kve.structure.ghost.off"));
    }

    private void toggleGhostPreview() {
        if (StructureGhostPreview.isEnabled()) {
            StructureGhostPreview.setEnabled(false);
        } else {
            StructureGhostPreview.setGrid(grid);
            StructureGhostPreview.setEnabled(true);
            doAnchorGhostHere();
        }
        if (ghostToggleButton != null) ghostToggleButton.setMessage(ghostLabel());
    }

    private Component includeMobsLabel() {
        return Component.literal(Lang.get("gui.kve.structure.world.include_mobs") + ": "
                + Lang.get(includeMobsOnScan ? "gui.kve.structure.ghost.on" : "gui.kve.structure.ghost.off"));
    }

    private void toggleIncludeMobs() {
        includeMobsOnScan = !includeMobsOnScan;
        if (includeMobsButton != null) includeMobsButton.setMessage(includeMobsLabel());
    }

    private Component sealDetectLabel() {
        return Component.literal(Lang.get("gui.kve.structure.world.seal_detect") + ": "
                + Lang.get(sealDetectOnScan ? "gui.kve.structure.ghost.on" : "gui.kve.structure.ghost.off"));
    }

    private void toggleSealDetect() {
        sealDetectOnScan = !sealDetectOnScan;
        if (sealDetectButton != null) sealDetectButton.setMessage(sealDetectLabel());
    }

    private Component terrainAdjustmentLabel() {
        com.kve.common.structure.StructureTerrainAdjustment current =
                com.kve.common.structure.StructureTerrainAdjustment.parse(grid.getTerrainAdjustment());
        return Component.literal(Lang.get("gui.kve.structure.world.terrain_adjustment") + ": "
                + Lang.get("gui.kve.structure.terrain." + current.name().toLowerCase(java.util.Locale.ROOT)));
    }

    private void cycleTerrainAdjustment() {
        com.kve.common.structure.StructureTerrainAdjustment current =
                com.kve.common.structure.StructureTerrainAdjustment.parse(grid.getTerrainAdjustment());
        grid.setTerrainAdjustment(current.next().name());
        if (terrainAdjustmentButton != null) terrainAdjustmentButton.setMessage(terrainAdjustmentLabel());
    }

    private void doAnchorGhostHere() {
        if (minecraft == null || minecraft.hitResult == null) return;
        if (!(minecraft.hitResult instanceof BlockHitResult bhr)) {
            flash(Lang.get("gui.kve.structure.ghost.no_target"), false);
            return;
        }
        StructureGhostPreview.setGrid(grid);
        StructureGhostPreview.setEnabled(true);
        StructureGhostPreview.setAnchor(bhr.getBlockPos().relative(bhr.getDirection()));
        if (ghostToggleButton != null) ghostToggleButton.setMessage(ghostLabel());
        flash(Lang.get("gui.kve.structure.ghost.anchored"), true);
    }

    private void doWorldGenerate() {
        if (!ClientPermissionState.canApply()) { flash(Lang.get("gui.kve.perm.denied"), false); return; }
        if (grid.isEmpty()) { flash(Lang.get("gui.kve.structure.empty"), false); return; }
        if (grid.exceedsMaxEdge(KVEConfig.structureEditorMaxEdge)) {
            flash(Lang.get("gui.kve.structure.too_large", KVEConfig.structureEditorMaxEdge), false);
            return;
        }

        BlockPos origin;
        boolean hasSession = StructureWorldBuildSession.isEnabled();
        boolean inPlace;
        if (hasSession) {
            inPlace = true;
            BlockPos[] bounds = grid.computeBounds();
            BlockPos preNormalizeMin = bounds != null ? bounds[0] : BlockPos.ZERO;
            grid.normalizeOrigin();
            origin = StructureWorldBuildSession.getGenerateAnchor().offset(preNormalizeMin);
        } else if (grid.getScanOrigin() != null && minecraft != null && minecraft.level != null
                && minecraft.level.dimension().location().toString().equals(grid.getScanDimension())) {
            grid.normalizeOrigin();
            origin = grid.getScanOrigin();
            inPlace = true;
        } else {
            if (minecraft == null || !(minecraft.hitResult instanceof BlockHitResult bhr)) {
                flash(Lang.get("gui.kve.structure.ghost.no_target"), false);
                return;
            }
            grid.normalizeOrigin();
            origin = bhr.getBlockPos().relative(bhr.getDirection());
            inPlace = false;
        }

        flash(Lang.get("gui.kve.structure.world.generating"), true);
        StructureWorldBuildSession.requestGenerate(grid, origin, inPlace, failureKey -> {
            if (failureKey == null) {
                int shift = StructureWorldBuildSession.lastGenerateShiftY();
                if (shift == 0) flash(Lang.get("gui.kve.structure.world.generated"), true);
                else flash(Lang.get(shift < 0 ? "gui.kve.structure.world.generated_buried_down"
                        : "gui.kve.structure.world.generated_buried_up", Math.abs(shift)), true);
                refresh();
            } else {
                flash(Lang.get(failureKey), false);
            }
        });
    }

    private void doWorldScan() {
        if (!ClientPermissionState.canApply()) { flash(Lang.get("gui.kve.perm.denied"), false); return; }
        if (!StructureWorldBuildSession.isEnabled()) {
            flash(Lang.get("gui.kve.structure.world.no_session"), false);
            return;
        }
        flash(Lang.get("gui.kve.structure.world.scanning"), true);
        StructureWorldBuildSession.requestScan(includeMobsOnScan, INCLUDE_AIR_ON_SCAN, sealDetectOnScan, response -> {
            if (response.outcome == com.kve.common.network.StructureWorldScanResponsePacket.OUTCOME_SUCCESS) {
                StructureVoxelGrid scanned = StructureNbtIO.fromScanTag(response.tag);
                grid.pushUndo();
                grid.copyCellsFrom(scanned);
                grid.recordHistory(Lang.get("gui.kve.structure.history.scanned", grid.blockCount()));
                flash(Lang.get("gui.kve.structure.world.scanned", grid.blockCount()), true);
            } else if (response.outcome == com.kve.common.network.StructureWorldScanResponsePacket.OUTCOME_EMPTY) {
                flash(Lang.get("gui.kve.structure.world.scan_empty"), false);
            } else {
                flash(Lang.get(response.failureKey), false);
            }
        });
    }

    private void doWorldCloseSession() {
        StructureWorldBuildSession.clear();
        flash(Lang.get("gui.kve.structure.world.session_closed"), true);
        refresh();
    }

    private Vector3f centerOfGrid() {
        int[] size = grid.computeSize();
        if (size == null) return new Vector3f(0, 0, 0);
        return new Vector3f(size[0] / 2f, size[1] / 2f, size[2] / 2f);
    }

    private float distanceToFrameGrid() {
        int[] size = grid.computeSize();
        if (size == null) return 8f;
        float diagonal = (float) Math.sqrt(
                (double) size[0] * size[0] + (double) size[1] * size[1] + (double) size[2] * size[2]);
        return Math.max(4f, diagonal * 1.2f);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (!hosted) renderBackground(g);
        int guiLeft = L;
        int guiTop = T;

        if (undoButton != null) undoButton.active = grid.canUndo();
        if (redoButton != null) redoButton.active = grid.canRedo();
        if (ghostToggleButton != null) ghostToggleButton.setMessage(ghostLabel());

        KveGuiStyle.drawWindow(g, guiLeft, guiTop, GUI_W, GUI_H, Lang.get("gui.kve.structure.title"));

        renderPalette(g, mx, my);
        renderViewportFrame(g);
        renderRightPanel(g, guiLeft, guiTop);

        super.render(g, mx, my, pt);

        updateHoveredCell(mx, my);
        if (layerButton != null) layerButton.setMessage(Component.literal(layerLabel()));
        // caja de vista previa: la del ancla en curso, la del pegado bajo el mouse, o la region seleccionada
        BlockPos previewA = toolAnchor;
        BlockPos previewB = (toolAnchor != null && hoveredCell != null) ? hoveredCell : null;
        if (toolMode == ToolMode.PASTE && clipboard != null && hoveredAdjacent != null) {
            previewA = hoveredAdjacent;
            previewB = hoveredAdjacent.offset(clipboard.sizeX() - 1, clipboard.sizeY() - 1, clipboard.sizeZ() - 1);
        } else if (previewA == null && selA != null) {
            previewA = selA;
            previewB = selB;
        }
        StructureSceneRenderer.render(vpX, vpY, vpW, vpH, grid, camera, hoveredCell, showGrid,
                previewA, previewB);
        if (toolTypeMenuOpen) renderToolTypeMenuOnTop(g, mx, my, pt);

        if (!flashMsg.isEmpty() && System.currentTimeMillis() - flashAt < 4000) {
            KveGuiStyle.drawTextSafe(g, flashMsg, guiLeft + KveGuiStyle.PAD, guiTop + GUI_H - 10,
                    GUI_W - KveGuiStyle.PAD * 2, flashColor);
        }

        // El drag-to-resize propio de esta ventana no aplica mientras esta
        // hosteada - el shell es dueno del rectangulo (ver el guard !hosted
        // en mouseClicked()/mouseDragged() mas abajo), y su posicion nunca se
        // repositiona a L/T hosted (solo el init() standalone lo hace).
        if (!hosted && windowResize != null) windowResize.render(g, mx, my);

        drawPaletteTooltip(g, mx, my);
        drawShiftTips(g, mx, my);
    }

    private void drawPaletteTooltip(GuiGraphics g, int mx, int my) {
        int guiLeft = L;
        int guiTop = T;
        int px = guiLeft + KveGuiStyle.PAD;
        int py = guiTop + 18 + 14;
        int scroll = paletteScroll != null ? paletteScroll.getScroll() : 0;
        int firstIndex = scroll * COLS;
        for (int i = 0; i < paletteVisibleRows * COLS; i++) {
            int idx = firstIndex + i;
            if (idx >= filtered.size()) break;
            int col = i % COLS, row = i / COLS;
            int sx = px + col * SLOT, sy = py + row * SLOT;
            if (mx >= sx && mx < sx + SLOT - 1 && my >= sy && my < sy + SLOT - 1) {
                net.minecraft.world.item.ItemStack tip = RegistryIndex.blockIcon(filtered.get(idx));
                g.renderTooltip(font, tip.isEmpty() ? new net.minecraft.world.item.ItemStack(filtered.get(idx).asItem()) : tip, mx, my);
                return;
            }
        }
    }

    private void renderPalette(GuiGraphics g, int mx, int my) {
        int guiLeft = L;
        int guiTop = T;
        int px = guiLeft + KveGuiStyle.PAD;
        int py = guiTop + 18 + 14;

        int scroll = paletteScroll != null ? paletteScroll.getScroll() : 0;
        int firstIndex = scroll * COLS;
        for (int i = 0; i < paletteVisibleRows * COLS; i++) {
            int idx = firstIndex + i;
            if (idx >= filtered.size()) break;
            int col = i % COLS, row = i / COLS;
            int sx = px + col * SLOT, sy = py + row * SLOT;
            Block b = filtered.get(idx);
            boolean hovered = KveGuiStyle.isHovered(mx, my, sx, sy, SLOT - 1, SLOT - 1);
            boolean selected = b.defaultBlockState().equals(selectedState);
            g.fill(sx, sy, sx + SLOT - 1, sy + SLOT - 1, selected ? 0xFF3D5A80 : (hovered ? 0xFF223349 : KveGuiStyle.BG_FIELD));
            float iconScale = (SLOT - 2) / 16f;
            g.pose().pushPose();
            g.pose().translate(sx + 1, sy + 1, 0);
            g.pose().scale(iconScale, iconScale, 1f);
            net.minecraft.world.item.ItemStack icon = RegistryIndex.blockIcon(b);
            try { g.renderItem(icon.isEmpty() ? new net.minecraft.world.item.ItemStack(b.asItem()) : icon, 0, 0); } catch (Throwable ignored) {}
            g.pose().popPose();
        }
        if (paletteScroll != null) paletteScroll.render(g);
    }

    private void renderViewportFrame(GuiGraphics g) {
        g.fill(vpX, vpY, vpX + vpW, vpY + vpH, KveGuiStyle.BG_FIELD);
        g.hLine(vpX, vpX + vpW - 1, vpY, KveGuiStyle.BORDER_FIELD);
        g.hLine(vpX, vpX + vpW - 1, vpY + vpH - 1, KveGuiStyle.BORDER_FIELD);
        g.vLine(vpX, vpY, vpY + vpH - 1, KveGuiStyle.BORDER_FIELD);
        g.vLine(vpX + vpW - 1, vpY, vpY + vpH - 1, KveGuiStyle.BORDER_FIELD);
    }

    private void renderRightPanel(GuiGraphics g, int guiLeft, int guiTop) {
        int rx = guiLeft + GUI_W - KveGuiStyle.PAD - SIDE_W;
        int[] size = grid.computeSize();
        String sizeText = size == null
                ? Lang.get("gui.kve.structure.size.empty")
                : size[0] + "x" + size[1] + "x" + size[2];
        for (PanelHeader h : panelHeaders) {
            int hy = panelTop + h.virtualY() - panelScroll;
            if (hy < panelTop || hy + 9 > panelBottom) continue;
            String title = Lang.get(h.langKey());
            KveGuiStyle.drawTextSafe(g, title, rx, hy + 1, SIDE_W, KveGuiStyle.ACCENT_GOLD);
            int ttw = Math.round(font.width(title) * KveGuiStyle.uiTextScale());
            if (ttw + 6 < SIDE_W) g.fill(rx + ttw + 4, hy + 5, rx + SIDE_W, hy + 6, KveGuiStyle.BORDER_FIELD);
        }
        int maxScroll = panelMaxScroll();
        if (maxScroll > 0) {
            int trackH = panelBottom - panelTop;
            int thumbH = Math.max(10, trackH * trackH / (trackH + maxScroll));
            int thumbY = panelTop + (trackH - thumbH) * panelScroll / maxScroll;
            g.fill(rx + SIDE_W + 1, panelTop, rx + SIDE_W + 3, panelBottom, KveGuiStyle.BORDER_FIELD);
            g.fill(rx + SIDE_W + 1, thumbY, rx + SIDE_W + 3, thumbY + thumbH, KveGuiStyle.ACCENT_GOLD);
        }
        int y = infoY;
        g.fill(rx, y - 3, rx + SIDE_W, y - 2, KveGuiStyle.BORDER_FIELD);
        KveGuiStyle.drawTextSafe(g, sizeText, rx, y, SIDE_W, KveGuiStyle.TEXT_LABEL);
        y += 10;
        String cellsText = Lang.get("gui.kve.structure.cells") + ": " + grid.blockCount();
        KveGuiStyle.drawTextSafe(g, cellsText, rx, y, SIDE_W, KveGuiStyle.TEXT_MUTED);
        y += 10;
        if (!sourceBadge.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, sourceBadge, rx, y, SIDE_W, KveGuiStyle.TEXT_MUTED);
            y += 10;
        }
        BlockPos scanOriginInfo = grid.getScanOrigin();
        if (scanOriginInfo != null) {
            String originText = Lang.get("gui.kve.structure.world.scan_origin") + ": "
                    + scanOriginInfo.getX() + " " + scanOriginInfo.getY() + " " + scanOriginInfo.getZ();
            KveGuiStyle.drawTextSafe(g, originText, rx, y, SIDE_W, KveGuiStyle.TEXT_MUTED);
            y += 10;
        }
        if (grid.isDirty()) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.structure.dirty"), rx, y, SIDE_W, KveGuiStyle.TEXT_WARN);
        }
    }

    private void updateHoveredCell(int mx, int my) {
        if (!KveGuiStyle.isHovered(mx, my, vpX, vpY, vpW, vpH)) { hoveredCell = null; return; }
        VoxelRaycast.Hit hit = rayFromMouse(mx, my);
        hoveredCell = hit != null ? hit.cell : null;
        hoveredAdjacent = hit != null ? hit.adjacent : null;
        if (hoveredCell != null) lastNonNullHoveredCell = hoveredCell;
    }

    private VoxelRaycast.Hit rayFromMouse(double mx, double my) {
        Vector3f eye = camera.eyePosition();
        Vector3f dir = mouseRayDirection(mx, my);
        if (dir == null) return null;
        return VoxelRaycast.cast(grid, eye, dir, camera.distance() + 128f, 4);
    }

    private Vector3f mouseRayDirection(double mx, double my) {
        float ndcX = (float) ((mx - vpX) / (double) vpW) * 2f - 1f;
        float ndcY = 1f - (float) ((my - vpY) / (double) vpH) * 2f;

        Matrix4f invViewProj = camera.projectionMatrix((float) vpW / (float) Math.max(1, vpH))
                .mul(camera.viewMatrix(), new Matrix4f())
                .invert();

        Vector4f near = invViewProj.transform(new Vector4f(ndcX, ndcY, -1f, 1f));
        Vector4f far = invViewProj.transform(new Vector4f(ndcX, ndcY, 1f, 1f));
        if (near.w == 0 || far.w == 0) return null;
        near.div(near.w);
        far.div(far.w);
        return new Vector3f(far.x - near.x, far.y - near.y, far.z - near.z).normalize();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!hosted && windowResize != null && windowResize.mouseClicked(mx, my)) return true;
        if (paletteScroll != null && paletteScroll.mouseClicked(mx, my, button)) return true;
        if (handlePaletteClick(mx, my, button)) return true;
        if (toolTypeMenuOpen && pointToolButton != null) {
            int ddTop = pointToolButton.getY();
            Button lastItem = selectToolButton != null ? selectToolButton : brushToolButton;
            int ddBottom = lastItem.getY() + lastItem.getHeight();
            if (KveGuiStyle.isHovered((int) mx, (int) my, pointToolButton.getX(), ddTop,
                    pointToolButton.getWidth(), ddBottom - ddTop)) {
                return super.mouseClicked(mx, my, button);
            }
        }
        if (KveGuiStyle.isHovered((int) mx, (int) my, vpX, vpY, vpW, vpH)) {
            closeToolTypeMenu();
            if (toolMode == ToolMode.BRUSH && button == 1) {
                brushPainting = true;
                lastBrushTarget = null;
                handleBrushPaint(mx, my);
                return true;
            }
            draggingViewport = true;
            dragStartX = mx; dragStartY = my; dragTotal = 0; dragButton = button;
            dragStartTime = System.currentTimeMillis();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private boolean handlePaletteClick(double mx, double my, int button) {
        int guiLeft = L;
        int guiTop = T;
        int px = guiLeft + KveGuiStyle.PAD;
        int py = guiTop + 18 + 14;
        if (!KveGuiStyle.isHovered((int) mx, (int) my, px, py, COLS * SLOT, paletteVisibleRows * SLOT)) return false;
        int col = (int) ((mx - px) / SLOT);
        int row = (int) ((my - py) / SLOT);
        int scroll = paletteScroll != null ? paletteScroll.getScroll() : 0;
        int idx = (scroll + row) * COLS + col;
        if (idx >= 0 && idx < filtered.size()) {
            selectedState = filtered.get(idx).defaultBlockState();
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!hosted && windowResize != null && windowResize.mouseDragged(mx, my)) { init(); return true; }
        if (!hosted && windowResize != null && windowResize.isDragging()) return true;
        if (paletteScroll != null && paletteScroll.mouseDragged(mx, my, button, dx, dy)) return true;
        if (brushPainting && button == 1) {
            handleBrushPaint(mx, my);
            return true;
        }
        if (draggingViewport && button == dragButton) {
            dragTotal += Math.abs(dx) + Math.abs(dy);
            boolean pan = button == 1 || hasShiftDown();
            if (pan) camera.pan(dx, dy);
            else camera.orbit(dx, dy);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        // Mismo guard `!hosted` que mouseClicked() (linea 1485) y mouseDragged()
        // (lineas 1515-1516): sin esto, soltar el mouse mientras esta tab
        // esta hosteada en KveStudioShell persistia a disco un L/T/W/H que
        // en realidad es el rectangulo que el shell le asigna (ver init(x,y,w,h),
        // linea 276), no una ventana standalone real - corrompia
        // structure_editor.window.json con las coordenadas del shell.
        if (!hosted && windowResize != null) windowResize.mouseReleased();
        if (paletteScroll != null) paletteScroll.mouseReleased(mx, my, button);
        if (button == 1 && brushPainting) {
            brushPainting = false;
            lastBrushTarget = null;
            brushAxis = null;
            if (brushStrokePlaced > 0) {
                grid.recordHistory(Lang.get("gui.kve.structure.history.brush_stroke", brushStrokePlaced, blockName(selectedState)));
            }
            brushStrokePlaced = 0;
            return true;
        }
        boolean wasDraggingViewport = draggingViewport && button == dragButton;
        draggingViewport = false;

        boolean releasedQuickly = System.currentTimeMillis() - dragStartTime < 350;
        if (wasDraggingViewport && dragTotal < 4.0 && releasedQuickly
                && KveGuiStyle.isHovered((int) mx, (int) my, vpX, vpY, vpW, vpH)) {
            handleViewportClick(button);
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    private void handleViewportClick(int button) {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied"), false); return; }
        Vector3f dir = mouseRayDirection(dragStartX, dragStartY);
        if (dir == null) return;
        VoxelRaycast.Hit hit = VoxelRaycast.cast(grid, camera.eyePosition(), dir, camera.distance() + 128f, 4);

        if (button == 2) {
            if (hit != null) { selectedState = grid.get(hit.cell); }
            return;
        }
        if (toolMode == ToolMode.SPAWN_CAP) {
            handleSpawnCapClick(button, hit);
            return;
        }
        if (toolMode == ToolMode.REPLACE) { handleReplaceClick(button, hit); return; }
        if (toolMode == ToolMode.SELECT) { handleSelectClick(button, hit, dir); return; }
        if (toolMode == ToolMode.PASTE) { handlePasteClick(button, hit, dir); return; }
        if (toolMode != ToolMode.POINT && toolMode != ToolMode.BRUSH) {
            handleToolClick(button, hit, dir);
            return;
        }
        if (button == 0) {
            BlockPos target = hit != null
                    ? hit.adjacent
                    : VoxelRaycast.castGroundPlane(camera.eyePosition(), dir, KVEConfig.structureEditorMaxEdge / 2);
            placeAtTarget(target);
        } else if (button == 1) {
            if (hit != null) {
                BlockState removed = grid.get(hit.cell);
                grid.pushUndoForCell(hit.cell);
                grid.breakCell(hit.cell);
                grid.recordHistory(Lang.get("gui.kve.structure.history.break",
                        blockName(removed), hit.cell.getX(), hit.cell.getY(), hit.cell.getZ()));
            }
        }
    }

    private void placeAtTarget(BlockPos target) {
        placeAtTarget(target, false);
    }

    private void placeAtTarget(BlockPos target, boolean brushStroke) {
        if (target == null) return;
        if (brushStroke && grid.get(target) == selectedState) return;
        if (brushStroke && brushStrokePlaced > 0) grid.extendLastUndoForCells(java.util.Collections.singletonList(target));
        else grid.pushUndoForCell(target);
        grid.setBlock(target, selectedState);
        if (grid.exceedsMaxEdge(KVEConfig.structureEditorMaxEdge)) {
            grid.removeCell(target);
            flash(Lang.get("gui.kve.structure.too_large", KVEConfig.structureEditorMaxEdge), false);
        } else if (brushStroke) {
            brushStrokePlaced++;
        } else {
            grid.recordHistory(Lang.get("gui.kve.structure.history.place",
                    blockName(selectedState), target.getX(), target.getY(), target.getZ()));
        }
    }

    private void handleBrushPaint(double mx, double my) {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied"), false); return; }
        Vector3f dir = mouseRayDirection(mx, my);
        if (dir == null) return;
        VoxelRaycast.Hit hit = VoxelRaycast.cast(grid, camera.eyePosition(), dir, camera.distance() + 128f, 4);
        if (lastBrushTarget == null) {
            BlockPos start = hit != null
                    ? hit.adjacent
                    : VoxelRaycast.castGroundPlane(camera.eyePosition(), dir, KVEConfig.structureEditorMaxEdge / 2);
            if (start == null) return;
            brushAxis = hit != null ? hit.face.getAxis() : net.minecraft.core.Direction.Axis.Y;
            brushPlane = start.get(brushAxis);
            brushStrokePlaced = 0;
            lastBrushTarget = start.immutable();
            placeAtTarget(start, true);
            return;
        }
        BlockPos target = brushPlaneTarget(camera.eyePosition(), dir);
        if (target == null || target.equals(lastBrushTarget)) return;
        int dx = target.getX() - lastBrushTarget.getX(), dy = target.getY() - lastBrushTarget.getY(), dz = target.getZ() - lastBrushTarget.getZ();
        int steps = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        BlockPos from = lastBrushTarget;
        for (int i = 1; i <= steps; i++) {
            placeAtTarget(new BlockPos(
                    from.getX() + Math.round(dx * (float) i / steps),
                    from.getY() + Math.round(dy * (float) i / steps),
                    from.getZ() + Math.round(dz * (float) i / steps)), true);
        }
        lastBrushTarget = target.immutable();
    }

    private BlockPos brushPlaneTarget(Vector3f eye, Vector3f dir) {
        if (brushAxis == null) return null;
        Vector3f d = new Vector3f(dir).normalize();
        float o = switch (brushAxis) { case X -> eye.x; case Y -> eye.y; case Z -> eye.z; };
        float dd = switch (brushAxis) { case X -> d.x; case Y -> d.y; case Z -> d.z; };
        if (Math.abs(dd) < 1e-5f) return null;
        float t = (brushPlane + 0.5f - o) / dd;
        if (t <= 0) return null;
        int x = (int) Math.floor(eye.x + d.x * t);
        int y = (int) Math.floor(eye.y + d.y * t);
        int z = (int) Math.floor(eye.z + d.z * t);
        return switch (brushAxis) {
            case X -> new BlockPos(brushPlane, y, z);
            case Y -> new BlockPos(x, brushPlane, z);
            case Z -> new BlockPos(x, y, brushPlane);
        };
    }

    private static String blockName(BlockState state) {
        ResourceLocation rl = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return rl != null ? rl.toString() : state.getBlock().getName().getString();
    }

    private void handleSpawnCapClick(int button, VoxelRaycast.Hit hit) {
        if (hit == null) return;
        BlockPos pos = hit.cell.immutable();
        if (button == 0) {
            StructureSpawnCap existing = grid.getSpawnCap(pos);
            minecraft.setScreen(new StructureSpawnCapScreen(this, pos, existing,
                    cap -> {
                        grid.pushUndoForCell(pos);
                        grid.setSpawnCap(pos, cap);
                        grid.recordHistory(Lang.get("gui.kve.structure.history.spawncap_place",
                                cap.entityId, pos.getX(), pos.getY(), pos.getZ()));
                        flash(Lang.get("gui.kve.structure.spawncap.placed"), true);
                    },
                    () -> {
                        grid.pushUndoForCell(pos);
                        grid.removeSpawnCap(pos);
                        grid.recordHistory(Lang.get("gui.kve.structure.history.spawncap_remove",
                                pos.getX(), pos.getY(), pos.getZ()));
                        flash(Lang.get("gui.kve.structure.spawncap.removed"), true);
                    }));
        } else if (button == 1) {
            if (grid.getSpawnCap(pos) != null) {
                grid.pushUndoForCell(pos);
                grid.removeSpawnCap(pos);
                grid.recordHistory(Lang.get("gui.kve.structure.history.spawncap_remove",
                        pos.getX(), pos.getY(), pos.getZ()));
                flash(Lang.get("gui.kve.structure.spawncap.removed"), true);
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (paletteScroll != null && KveGuiStyle.isHovered((int) mx, (int) my,
                L + KveGuiStyle.PAD, T + 18 + 14, PALETTE_W, paletteVisibleRows * SLOT)
                && paletteScroll.mouseScrolled(delta)) return true;
        if (KveGuiStyle.isHovered((int) mx, (int) my, vpX, vpY, vpW, vpH)) {
            camera.zoom(-delta);
            return true;
        }
        int panelX = L + GUI_W - KveGuiStyle.PAD - SIDE_W;
        if (panelMaxScroll() > 0 && KveGuiStyle.isHovered((int) mx, (int) my, panelX, panelTop, SIDE_W + 4, panelBottom - panelTop)) {
            scrollRightPanel((int) Math.round(-delta * PANEL_ROW * 2));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (toolAnchor != null) { toolAnchor = null; return true; }
            if (toolMode == ToolMode.PASTE) { setToolMode(ToolMode.POINT); return true; }
            if (selA != null) { clearSelection(); return true; }
            onClose();
            return true;
        }
        boolean typing = (searchBox != null && searchBox.isFocused()) || (nameBox != null && nameBox.isFocused());
        if (!typing) {
            if (key == GLFW.GLFW_KEY_G) { showGrid = !showGrid; return true; }
            if (key == GLFW.GLFW_KEY_R) { cycleFacing(); return true; }
            if (key == GLFW.GLFW_KEY_Z && Screen.hasControlDown()) { doUndo(); return true; }
            if (key == GLFW.GLFW_KEY_C && Screen.hasControlDown()) { doCopySelection(false); return true; }
            if (key == GLFW.GLFW_KEY_X && Screen.hasControlDown()) { doCopySelection(true); return true; }
            if (key == GLFW.GLFW_KEY_V && Screen.hasControlDown()) { startPaste(); return true; }
            if (key == GLFW.GLFW_KEY_DELETE && selA != null) { doDeleteSelection(); return true; }
            if (key == GLFW.GLFW_KEY_PAGE_DOWN) { stepLayer(-1); return true; }
            if (key == GLFW.GLFW_KEY_PAGE_UP) { stepLayer(1); return true; }
            if (toolMode == ToolMode.GRID) {
                if (key == GLFW.GLFW_KEY_LEFT_BRACKET) {
                    gridToolSpacing = Math.max(1, gridToolSpacing - 1);
                    flash(Lang.get("gui.kve.structure.tool.grid_spacing", gridToolSpacing), true);
                    return true;
                }
                if (key == GLFW.GLFW_KEY_RIGHT_BRACKET) {
                    gridToolSpacing = Math.min(KVEConfig.structureEditorMaxEdge, gridToolSpacing + 1);
                    flash(Lang.get("gui.kve.structure.tool.grid_spacing", gridToolSpacing), true);
                    return true;
                }
            }
        }
        if (getFocused() != null && getFocused().keyPressed(key, scan, mods)) return true;
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
