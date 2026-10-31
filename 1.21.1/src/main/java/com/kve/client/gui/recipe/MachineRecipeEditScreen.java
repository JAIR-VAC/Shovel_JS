package com.kve.client.gui.recipe;

import com.kve.client.gui.EditorKeys;
import com.kve.client.gui.picker.FluidPickerScreen;
import com.kve.client.gui.picker.ItemPickerScreen;
import com.kve.client.gui.KveConfirmScreen;
import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.SafeItemRender;
import com.kve.client.gui.KvePlateButton;
import com.kve.client.gui.KveScaledEditBox;
import com.kve.client.gui.ScrollBar;
import com.kve.client.gui.tag.TagPresetPickerScreen;

import org.lwjgl.glfw.GLFW;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kve.common.Lang;
import com.kve.client.gui.recipe.ritual.RitualRingLayout;
import com.kve.common.kubejs.KubeJSExporter;
import com.kve.common.kubejs.TagExporter;
import com.kve.common.recipe.RecipeData;
import com.kve.common.recipetype.RecipeTypeDescriptor;
import com.kve.client.gui.workspace.pending.PendingDomain;
import com.kve.client.gui.workspace.pending.PendingChange;
import com.kve.client.gui.workspace.pending.PendingChangeManager;
import com.kve.client.permission.ClientPermissionState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Dedicated editor for {@link RecipeData.RecipeType#CUSTOM_MACHINE} recipes.
 * <p>
 * Renders inputs/outputs as a clickable 18x18 icon grid (item render /
 * fluid sprite / tag badge) in the slot's real position, instead of a
 * scrollable text list - the same slot look-and-feel JEI/REI/EMI use for
 * *displaying* a recipe, except every slot here is directly editable:
 * left-click a filled slot to replace its value, right-click to clear it,
 * scroll over it to bump the amount, or click the trailing dashed "+"
 * slot to add a new one. This is a from-scratch renderer built against
 * vanilla/Forge rendering calls only ({@code GuiGraphics#renderItem()},
 * atlas sprite blitting for fluids) - KVE does not depend on JEI, REI or
 * EMI at compile or runtime.
 * <p>
 * The concrete recipe type (which machine, from any mod) is still picked
 * through the existing registry-generic {@link RecipeTypePickerScreen},
 * and slot values are picked through the existing {@link ItemPickerScreen},
 * {@link FluidPickerScreen} and {@link TagPresetPickerScreen} - no new
 * pickers were needed for this rewrite.
 * <p>
 * Reached from CraftEditorScreen's "Maquina (fluidos/tags)" button (new
 * recipe) or by clicking a saved CUSTOM_MACHINE recipe in its list
 * (existing recipe) - never touches CraftEditorScreen's ItemStack grid
 * state, so nothing about the existing vanilla/EC/CUSTOM_JSON flow changes.
 */
@OnlyIn(Dist.CLIENT)
public class MachineRecipeEditScreen extends Screen {

    private static final int GUI_W_MIN = 480;
    private int GUI_W = GUI_W_MIN;
    private static final int GUI_H_MIN = 232;
    private int GUI_H = GUI_H_MIN;
    private static final int BOTTOM_MARGIN = 8;

    private static final int SLOT = 18;
    private static final int TYPE_ICON_W = 16;
    private static final int TANK_W = SLOT;
    private static final int TANK_H = SLOT * 2;
    private static final int GRID_ROWS_VISIBLE = 2;
    private static final int MAX_GRID_COLS = 18;

    private static final int STATION_CANVAS_H = 90;
    private final NodeCanvasWidget stationCanvas = new NodeCanvasWidget();

    private final CraftEditorScreen parent;
    private RecipeData editing;

    private int L, T;
    private int ax;
    private int gridCols;

    private int row1Y, row2Y, row3Y;
    private int inputsLabelY, inputsGridY;
    private int outputsLabelY, outputsGridY;
    private int bottomBtnY;

    private static final int HINT_AREA_H = 26;

    private String draftRecipeId = "";
    private String draftCategory = "";
    private boolean draftEnabled = true;
    private String draftCustomTypeId = "";
    private String draftExtraJson = "";
    private String pendingAddonToggleLabel = null;
    private String pendingAddonWarning = null;
    /** Rect {btnX,btnY,btnW,btnH} del boton "Aceptar" de {@link #pendingAddonWarning}, recalculado cada frame en render - null cuando no hay aviso mostrandose (ningun rect que hit-testear en mouseClicked()). */
    private int[] pendingAddonWarningBtn = null;
    private final List<RecipeData.MachineSlot> draftInputs  = new ArrayList<>();
    private final List<RecipeData.MachineSlot> draftOutputs = new ArrayList<>();

    private RecipeData.MachineSlotType pendingInputType  = RecipeData.MachineSlotType.ITEM;
    private RecipeData.MachineSlotType pendingOutputType = RecipeData.MachineSlotType.ITEM;

    private RecipeTypeDescriptor activeDescriptor;
    private RecipeTypeDescriptor.SlotLayout activeLayout;
    private RecipeTypeDescriptor.ExtraField containerFieldSlotField;
    private List<RecipeTypeDescriptor.SlotDef> layoutInputDefs  = Collections.emptyList();
    private List<RecipeTypeDescriptor.SlotDef> layoutOutputDefs = Collections.emptyList();

    private ScrollBar inputScrollBar, outputScrollBar;

    private EditBox recipeIdBox, categoryBox, extraJsonBox;

    private enum RitualCenterMode { NONE, EXTRA_FIELD, INPUT_SLOT }

    private static final int RITUAL_CANVAS_H = 140;
    private static final int RITUAL_RING_COL_W = 190;
    private static final int RITUAL_OPTION_ROW_H = KveGuiStyle.BTN_H;

    private boolean ritualMode;
    private RitualCenterMode ritualCenterMode = RitualCenterMode.NONE;
    private RecipeTypeDescriptor.ExtraField ritualCenterExtraField;
    private int ritualMaxOfferings = 8;
    private final List<RecipeTypeDescriptor.ExtraField> ritualOptionFields = new ArrayList<>();

    private int ritualLabelY;
    private int ritualCenterX, ritualCenterY;
    private int ritualOutX, ritualOutY;
    private int ritualParamsColX, ritualParamsColY, ritualParamsColW;
    private List<RitualRingLayout.Offering> ritualRingPositions = Collections.emptyList();
    private final List<Integer> ritualOfferingRingSlot = new ArrayList<>();

    private String flashMsg = "";
    private long   flashAt  = 0;

    private String pendingRemovalId = null;
    private boolean pendingRemovalHintShown = false;

    private boolean hosted = false;
    private int hostedX, hostedY, hostedW, hostedH;

    private Runnable onExitEmbedded;

    /** Fluent setter - see {@link #onExitEmbedded}. */
    /** Estacion del borrador actual ("" si no se eligio ninguna). */
    public String draftTypeId() { return draftCustomTypeId == null ? "" : draftCustomTypeId; }

    public MachineRecipeEditScreen withExitEmbedded(Runnable r) {
        this.onExitEmbedded = r;
        return this;
    }

    private Screen pickerReturn() {
        return hosted ? parent : this;
    }

    public MachineRecipeEditScreen(CraftEditorScreen parent, RecipeData editing) {
        this(parent, editing, null);
    }

    /**
     * @param preselectTypeId When {@code editing} is null (a brand new
     *                        recipe) and this is non-blank, immediately
     *                        applies this recipe type - same as if the
     *                        player had opened blank and clicked "Tipo:
     *                        Elegir..." themselves - instead of opening
     *                        with no type picked yet. Used by
     *                        {@code CraftEditorScreen#openRecipeTypePicker}
     *                        so picking a type with a real {@code slotLayout}
     *                        from "Otros Mods" lands directly in this
     *                        screen already showing that type's custom
     *                        layout, instead of silently falling into the
     *                        generic ItemStack pattern grid that has no
     *                        idea what a {@code slotLayout} even is.
     *                        Ignored (same as the old single-arg
     *                        constructor) when {@code editing} is not null.
     */
    public MachineRecipeEditScreen(CraftEditorScreen parent, RecipeData editing, String preselectTypeId) {
        super(Lang.c("gui.kve.machine.title"));
        this.parent = parent;
        this.editing = editing;
        if (editing != null) loadFrom(editing);
        else if (preselectTypeId != null && !preselectTypeId.isBlank()) {
            draftCustomTypeId = preselectTypeId;
            applyRecipeTypeDescriptorHints(preselectTypeId);
        }
        if (editing != null && editing.repairedTags != null && !editing.repairedTags.isEmpty()) {
            flash(Lang.get("gui.kve.recipe.tags_repaired", editing.repairedTags.size(), String.join(", ", editing.repairedTags)));
        }
    }

    public MachineRecipeEditScreen withPendingRemovalId(String id) {
        this.pendingRemovalId = id;
        return this;
    }

    /** Primer item de salida del borrador (para abrir el grafo desde el editor de recetas), o vacio. */
    public ItemStack primaryOutputItem() {
        for (RecipeData.MachineSlot s : draftOutputs) {
            if (s.type == RecipeData.MachineSlotType.ITEM && s.item != null && !s.item.isEmpty()) return s.item.copy();
        }
        return ItemStack.EMPTY;
    }

    private void loadFrom(RecipeData d) {
        draftRecipeId = d.recipeId != null ? d.recipeId : "";
        draftCategory = d.category != null ? d.category : "";
        draftEnabled = d.enabled;
        draftCustomTypeId = d.customTypeId != null ? d.customTypeId : "";
        draftExtraJson = d.customExtraJson != null ? d.customExtraJson : "";
        draftInputs.clear();
        for (RecipeData.MachineSlot s : d.machineInputs) draftInputs.add(s.copy());
        draftOutputs.clear();
        for (RecipeData.MachineSlot s : d.machineOutputs) draftOutputs.add(s.copy());
    }

    /**
     * Dry-run of buildWidgets()'s vertical {@link KveGuiStyle.Layout} cursor
     * (throwaway Layout starting at y=0, no side effects on row1Y/
     * inputsGridY/bottomBtnY/etc.) to measure how tall the CURRENT
     * activeLayout/ritualMode actually needs - same principle
     * CraftEditorScreen's own {@code computeMinGuiW()} already uses for its
     * dimension (measure real content via the same helpers buildWidgets()
     * uses, zero invented numbers). Must mirror that method's row sequence
     * exactly or the two can drift apart. Calls resolveActiveLayout()/
     * resolveRitualView() itself so it can run before buildWidgets() (which
     * calls them again right after - cheap, and both need the same
     * activeLayout/ritualMode fields regardless of call order).
     */
    private int computeMinGuiH() {
        resolveActiveLayout();
        resolveRitualView();
        KveGuiStyle.Layout ly = new KveGuiStyle.Layout(0, 18, 16);
        ly.row(16);
        ly.row(16);
        ly.row(18);
        int bottomY;
        if (ritualMode) {
            ly.row(RITUAL_CANVAS_H);
            ly.gap(HINT_AREA_H);
            bottomY = ly.row();
        } else {
            boolean unified = activeLayout != null && activeLayout.unified;
            ly.row(12);
            ly.row(activeLayout != null ? (unified ? STATION_CANVAS_H : sectionHeight(layoutInputDefs)) : (GRID_ROWS_VISIBLE * SLOT + 8));
            if (!unified) {
                ly.row(12);
                ly.row(activeLayout != null ? sectionHeight(layoutOutputDefs) : (GRID_ROWS_VISIBLE * SLOT + 10));
            }
            ly.gap(HINT_AREA_H);
            bottomY = ly.row();
        }
        return bottomY + KveGuiStyle.BTN_H + BOTTOM_MARGIN;
    }

    private int computeMinGuiW() {
        resolveActiveLayout();
        resolveRitualView();
        if (ritualMode || activeLayout == null || activeLayout.unified) return GUI_W_MIN;
        int contentRight = Math.max(
                maxRightEdge(layoutInputDefs, draftInputs.size()),
                maxRightEdge(layoutOutputDefs, draftOutputs.size()));
        return Math.max(GUI_W_MIN, contentRight + 16 + 16);
    }

    @Override
    protected void init() {
        hosted = false;
        GUI_W = Math.max(GUI_W_MIN, computeMinGuiW());
        GUI_H = Math.max(GUI_H_MIN, computeMinGuiH());
        L = (width  - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        buildWidgets();
    }

    /**
 * KveStudioShell embed - same overload contract as
     * {@link CraftEditorScreen#init(int, int, int, int)}/
     * {@code LootTableEditorScreen}'s own: CraftEditorScreen calls this
     * directly (never via {@code Minecraft#setScreen()}) to hand this
     * instance the rectangle of its own mid column instead of letting it
     * center against {@code Screen#width}/{@code height} (which would stay
     * 0 anyway, since this instance's own {@code Screen#init(Minecraft,
     * int, int)} never runs while embedded - same bootstrap gap
     * {@code KveStudioShell#reclaimIfHosted}'s Javadoc documents, hueco #1).
     */
    public void init(int x0, int y0, int w, int h) {
        if (this.minecraft == null) {
            this.minecraft = net.minecraft.client.Minecraft.getInstance();
            this.font = this.minecraft.font;
        }
        hosted = true;
        hostedX = x0; hostedY = y0; hostedW = w; hostedH = h;
        GUI_W = Math.max(Math.max(GUI_W_MIN, computeMinGuiW()), w);
        GUI_H = Math.max(Math.max(GUI_H_MIN, computeMinGuiH()), h);
        L = x0;
        T = y0;
        buildWidgets();
    }

    private void refresh() {
        if (hosted) init(hostedX, hostedY, hostedW, hostedH);
        else init();
    }

    private void buildWidgets() {
        ax = L + 8;
        gridCols = Math.max(4, Math.min(MAX_GRID_COLS, (GUI_W - 16 - 16) / SLOT));
        if (pendingRemovalId != null && !pendingRemovalHintShown) {
            pendingRemovalHintShown = true;
            flash(Lang.get("gui.kve.recipe.editing_will_remove", pendingRemovalId));
        }
        clearWidgets();
        setFocused(null);

        resolveActiveLayout();
        resolveRitualView();

        KveGuiStyle.Layout ly = new KveGuiStyle.Layout(ax, T + 18, 16);
        row1Y = ly.row(16);
        row2Y = ly.row(16);
        row3Y = ly.row(18);

        boolean unified;
        if (ritualMode) {
            unified = false;
            int canvasTop = ly.row(RITUAL_CANVAS_H);
            layoutRitualCanvas(canvasTop);
            inputsLabelY = outputsLabelY = inputsGridY = outputsGridY = canvasTop;
            ly.gap(HINT_AREA_H);
            bottomBtnY = ly.row();
        } else {
            unified = activeLayout != null && activeLayout.unified;
            boolean sameRow = !unified && activeLayout != null && activeLayout.sameRow;
            inputsLabelY = ly.row(12);
            inputsGridY = ly.row(activeLayout != null ? (unified ? STATION_CANVAS_H : sectionHeight(layoutInputDefs)) : (GRID_ROWS_VISIBLE * SLOT + 8));
            if (unified) {
                outputsLabelY = inputsLabelY;
                outputsGridY = inputsGridY;
                fitStationCanvasIfNeeded();
            } else if (sameRow) {
                outputsLabelY = inputsLabelY;
                outputsGridY = inputsGridY;
            } else {
                outputsLabelY = ly.row(12);
                outputsGridY = ly.row(activeLayout != null ? sectionHeight(layoutOutputDefs) : (GRID_ROWS_VISIBLE * SLOT + 10));
            }
            ly.gap(HINT_AREA_H);
            bottomBtnY = ly.row();
        }

        recipeIdBox = new KveScaledEditBox(font, ax, row1Y, 170, 12, Component.literal("ID"));
        recipeIdBox.setMaxLength(64);
        recipeIdBox.setValue(draftRecipeId);
        recipeIdBox.setHint(Lang.c("gui.kve.machine.id_hint"));
        recipeIdBox.setResponder(s -> draftRecipeId = s.trim());
        addRenderableWidget(recipeIdBox);
        addRenderableWidget(btn(
                Lang.get(draftEnabled ? "gui.kve.status.enabled" : "gui.kve.status.disabled"),
                ax + 184, row1Y, 0, this::toggleEnabled));

        addRenderableWidget(btn(KveGuiStyle.truncate(font,
                        Lang.get("gui.kve.machine.type", draftCustomTypeId.isEmpty() ? Lang.get("gui.kve.machine.type_pick") : draftCustomTypeId), 320 - TYPE_ICON_W - 2),
                ax + TYPE_ICON_W + 2, row2Y, 0, this::openTypePicker));
        if (pendingAddonToggleLabel != null) {
            addRenderableWidget(btn(KveGuiStyle.truncate(font, Lang.get("gui.kve.recipe.enable_addon", pendingAddonToggleLabel), 129),
                    ax + 325, row2Y, 0, this::enablePendingAddonFlag));
        }

        categoryBox = new KveScaledEditBox(font, ax, row3Y, 150, 12, Lang.c("gui.kve.common.category"));
        categoryBox.setMaxLength(24);
        categoryBox.setValue(draftCategory);
        categoryBox.setHint(Lang.c("gui.kve.machine.category_hint"));
        categoryBox.setResponder(s -> draftCategory = s);
        addRenderableWidget(categoryBox);

        int extraJsonX = ritualMode ? ritualParamsColX : ax + 156;
        int extraJsonY = ritualMode ? ritualParamsColY : row3Y;
        int extraJsonW = ritualMode ? ritualParamsColW : 200;
        extraJsonBox = new KveScaledEditBox(font, extraJsonX, extraJsonY, extraJsonW, 12, Component.literal("Extra JSON"));
        extraJsonBox.setMaxLength(500);
        extraJsonBox.setValue(draftExtraJson);
        extraJsonBox.setHint(Component.literal("\"processingTime\": 100 (opcional)"));
        extraJsonBox.setResponder(s -> draftExtraJson = s);
        addRenderableWidget(extraJsonBox);

        if (ritualMode) {
            int rbW = Math.min(120, ritualParamsColW);
            int rbX = ritualParamsColX + ritualParamsColW - rbW;
            for (int i = 0; i < ritualOptionFields.size(); i++) {
                RecipeTypeDescriptor.ExtraField field = ritualOptionFields.get(i);
                int rbY = (i == 0) ? ritualLabelY - 2 : ritualParamsColY + 26 + (i - 1) * RITUAL_OPTION_ROW_H;
                RitualOptionButton rb = new RitualOptionButton(rbX, rbY, rbW, KveGuiStyle.BTN_H,
                        ritualOptionFieldButtonLabel(field),
                        () -> cycleRitualOptionField(field, 1),
                        () -> cycleRitualOptionField(field, -1));
                rb.setTooltip(Tooltip.create(Lang.c("gui.kve.ritual.option_hint")));
                addRenderableWidget(rb);
            }
        }

        if (!ritualMode && activeLayout == null) {
            addRenderableWidget(btn(Lang.get("gui.kve.machine.type", typeLabel(pendingInputType)), ax + 130, inputsLabelY - 2, 0,
                    () -> { pendingInputType = cycle(pendingInputType); refresh(); }));
            inputScrollBar = new ScrollBar(gridScrollBarX(), inputsGridY, GRID_ROWS_VISIBLE * SLOT, 0, GRID_ROWS_VISIBLE);
            inputScrollBar.setTotals(gridRows(draftInputs.size()), GRID_ROWS_VISIBLE);

            addRenderableWidget(btn(Lang.get("gui.kve.machine.type", typeLabel(pendingOutputType)), ax + 130, outputsLabelY - 2, 0,
                    () -> { pendingOutputType = cycle(pendingOutputType); refresh(); }));
            outputScrollBar = new ScrollBar(gridScrollBarX(), outputsGridY, GRID_ROWS_VISIBLE * SLOT, 0, GRID_ROWS_VISIBLE);
            outputScrollBar.setTotals(gridRows(draftOutputs.size()), GRID_ROWS_VISIBLE);
        } else {
            inputScrollBar = null;
            outputScrollBar = null;
        }

        KveGuiStyle.ButtonRow actionRow = new KveGuiStyle.ButtonRow(ax, bottomBtnY, L + GUI_W - 4);
        // mismo orden que el resto de editores: Guardar, simbolos, volver, y Eliminar al final y separado
        KveGuiStyle.ActionBar bar = new KveGuiStyle.ActionBar(actionRow, this::addRenderableWidget);
        bar.primary(Lang.get("gui.kve.btn.save"), b -> save());
        bar.button(Lang.get("gui.kve.btn.preview"), b -> previewRecipe());
        bar.button(Lang.get("gui.kve.btn.duplicate"), b -> duplicateMachineRecipe());
        bar.more(Lang.get("gui.kve.btn.cancel"), () -> cancel());
        bar.end(() -> delete());
    }

    private void resolveActiveLayout() {
        activeDescriptor = null;
        activeLayout = null;
        containerFieldSlotField = null;
        layoutInputDefs = Collections.emptyList();
        layoutOutputDefs = Collections.emptyList();
        if (draftCustomTypeId == null || draftCustomTypeId.isBlank()) return;
        var found = com.kve.common.recipetype.RecipeTypeDescriptorRegistry.find(draftCustomTypeId);
        if (found.isEmpty()) return;
        activeDescriptor = found.get();
        activeLayout = activeDescriptor.slotLayout;
        if (activeLayout != null) {
            List<RecipeTypeDescriptor.SlotDef> in = new ArrayList<>(), out = new ArrayList<>();
            for (RecipeTypeDescriptor.SlotDef d : activeLayout.slots) (d.isOutput() ? out : in).add(d);
            layoutInputDefs = in;
            layoutOutputDefs = out;
            if (activeLayout.containerFieldSlot != null) {
                for (RecipeTypeDescriptor.ExtraField f : activeDescriptor.extraFields) {
                    if (activeLayout.containerFieldSlot.extraFieldKey.equals(f.key)) {
                        containerFieldSlotField = f;
                        break;
                    }
                }
            }
        }
    }

    /**
     * Resolves {@link #ritualMode}/{@link #ritualCenterMode}/
     * {@link #ritualCenterExtraField} from {@link #activeDescriptor},
     * following the priority order in
     * an explicit {@code ritualView.centerField} or
     * {@code centerSlotId} wins if present; otherwise fall back to a
     * slotLayout slot literally named like a center, then a required
     * item-typed extraField named like a reagent/activation item, then
     * (last resort) just the first input slot. Leaves
     * {@link #ritualCenterMode} at {@code NONE} (center drawn locked,
     * non-interactive) if nothing at all can be resolved - a malformed
     * ritualView block can only degrade the view, never crash the editor.
     */
    private void resolveRitualView() {
        ritualMode = activeDescriptor != null && activeDescriptor.hasRitualView();
        ritualCenterMode = RitualCenterMode.NONE;
        ritualCenterExtraField = null;
        ritualOptionFields.clear();
        if (!ritualMode) return;

        for (RecipeTypeDescriptor.ExtraField f : activeDescriptor.extraFields) {
            if (f.hasOptions()) ritualOptionFields.add(f);
        }

        RecipeTypeDescriptor.RitualViewSettings rv = activeDescriptor.ritualView;

        if (rv != null && rv.hasCenterField()) {
            for (RecipeTypeDescriptor.ExtraField f : activeDescriptor.extraFields) {
                if (rv.centerField.equals(f.key)) {
                    ritualCenterExtraField = f;
                    ritualCenterMode = RitualCenterMode.EXTRA_FIELD;
                    return;
                }
            }
        }
        if (rv != null && rv.hasCenterSlotId() && activeLayout != null) {
            for (RecipeTypeDescriptor.SlotDef d : layoutInputDefs) {
                if (rv.centerSlotId.equals(d.id)) {
                    ritualCenterMode = RitualCenterMode.INPUT_SLOT;
                    return;
                }
            }
        }
        if (activeLayout != null) {
            for (RecipeTypeDescriptor.SlotDef d : layoutInputDefs) {
                String id = d.id == null ? "" : d.id.toLowerCase(Locale.ROOT);
                if (id.equals("center") || id.equals("main_input") || id.equals("reagent")) {
                    ritualCenterMode = RitualCenterMode.INPUT_SLOT;
                    return;
                }
            }
        }
        for (RecipeTypeDescriptor.ExtraField f : activeDescriptor.extraFields) {
            if (f.required && "item".equalsIgnoreCase(f.type)
                    && (f.key.equals("reagent") || f.key.equals("activationItem"))) {
                ritualCenterExtraField = f;
                ritualCenterMode = RitualCenterMode.EXTRA_FIELD;
                return;
            }
        }
        if (activeLayout != null && !layoutInputDefs.isEmpty()) {
            ritualCenterMode = RitualCenterMode.INPUT_SLOT;
        }
    }

    private List<RecipeData.MachineSlot> ritualOfferings() {
        if (ritualCenterMode == RitualCenterMode.INPUT_SLOT && !draftInputs.isEmpty()) {
            return draftInputs.subList(1, draftInputs.size());
        }
        return draftInputs;
    }

    private void layoutRitualCanvas(int canvasTop) {
        ritualLabelY = canvasTop;
        int contentTop = canvasTop + 12;

        ritualCenterX = ax + RITUAL_RING_COL_W / 2 - RitualRingLayout.CENTER_SLOT / 2;
        ritualCenterY = contentTop + 38;

        RecipeTypeDescriptor.RitualViewSettings rv = activeDescriptor != null ? activeDescriptor.ritualView : null;
        ritualMaxOfferings = RitualRingLayout.clampMaxOfferings(rv != null ? rv.maxOfferings : 8);

        int n = ritualMaxOfferings;
        int ringCx = ritualCenterX + RitualRingLayout.CENTER_SLOT / 2;
        int ringCy = ritualCenterY + RitualRingLayout.CENTER_SLOT / 2;
        ritualRingPositions = rv != null && rv.hasPositions()
                ? RitualRingLayout.computeCustomPositions(ringCx, ringCy, rv.positions)
                : RitualRingLayout.computeRingPositions(ringCx, ringCy, n);

        ritualParamsColX = ax + RITUAL_RING_COL_W + 10;
        ritualParamsColY = contentTop;
        ritualParamsColW = Math.max(80, (L + GUI_W - 8) - ritualParamsColX);

        int extraOptionRows = Math.max(0, ritualOptionFields.size() - 1);
        ritualOutX = ritualParamsColX;
        ritualOutY = ritualParamsColY + 46 + extraOptionRows * RITUAL_OPTION_ROW_H;
    }

    private static int slotHeight(RecipeTypeDescriptor.SlotDef d) { return d != null && d.tank ? tankHeight(d.h) : SLOT; }

    private static int tankHeight(int declared) { return declared > 0 ? Math.max(SLOT, Math.min(declared, 72)) : TANK_H; }

    private int sectionHeight(List<RecipeTypeDescriptor.SlotDef> defs) {
        int maxBottom = 0;
        for (RecipeTypeDescriptor.SlotDef d : defs) maxBottom = Math.max(maxBottom, d.y + slotHeight(d));
        return maxBottom + 8;
    }

    private int unifiedHeight() {
        int h = Math.max(sectionHeight(layoutInputDefs), sectionHeight(layoutOutputDefs));
        if (activeLayout != null && activeLayout.arrow != null) h = Math.max(h, activeLayout.arrow.y + 18 + 8);
        if (activeLayout != null) {
            for (RecipeTypeDescriptor.FieldTank ft : activeLayout.fieldTanks) h = Math.max(h, ft.y + tankHeight(ft.h) + 8);
            if (activeLayout.energyBar != null) h = Math.max(h, activeLayout.energyBar.y + activeLayout.energyBar.h + 8);
            for (RecipeTypeDescriptor.DecorItem di : activeLayout.decorItems) h = Math.max(h, di.y + SLOT + 8);
            for (RecipeTypeDescriptor.CanvasNote n : activeLayout.notes) h = Math.max(h, n.y + font.lineHeight + 4);
        }
        return h;
    }

    private int unifiedWidth() {
        int w = Math.max(maxRightEdge(layoutInputDefs, draftInputs.size()), maxRightEdge(layoutOutputDefs, draftOutputs.size()));
        if (activeLayout != null && activeLayout.arrow != null) w = Math.max(w, activeLayout.arrow.x + pushShiftFor(activeLayout.arrow.x) + 18);
        if (activeLayout != null) {
            for (RecipeTypeDescriptor.FieldTank ft : activeLayout.fieldTanks) w = Math.max(w, ft.x + pushShiftFor(ft.x) + TANK_W);
            if (activeLayout.energyBar != null) w = Math.max(w, activeLayout.energyBar.x + pushShiftFor(activeLayout.energyBar.x) + activeLayout.energyBar.w);
            for (RecipeTypeDescriptor.DecorItem di : activeLayout.decorItems) w = Math.max(w, di.x + pushShiftFor(di.x) + SLOT);
            for (RecipeTypeDescriptor.CanvasNote n : activeLayout.notes) w = Math.max(w, n.x + pushShiftFor(n.x) + font.width(n.text));
        }
        return w;
    }

    private int maxRightEdge(List<RecipeTypeDescriptor.SlotDef> defs, int actualCount) {
        RecipeTypeDescriptor.SlotDef anchor = firstRepeatableDef(defs);
        int maxRight = 0;
        for (RecipeTypeDescriptor.SlotDef d : defs) {
            if (d == anchor) continue;
            int w = d.tank ? TANK_W : SLOT;
            maxRight = Math.max(maxRight, d.x + pushShiftFor(d.x) + w);
        }
        if (anchor != null) {
            int fixedCount = fixedSlotCount(defs, anchor);
            int extra = Math.max(0, actualCount - fixedCount);
            maxRight = Math.max(maxRight, anchor.x + (extra + 1) * SLOT);
        }
        return maxRight;
    }

    private int pushShiftFor(int rawX) {
        if (activeLayout == null || !(activeLayout.unified || activeLayout.sameRow)) return 0;
        int shift = 0;
        shift = Math.max(shift, pushShiftIfDownstream(layoutInputDefs, draftInputs.size(), rawX));
        shift = Math.max(shift, pushShiftIfDownstream(layoutOutputDefs, draftOutputs.size(), rawX));
        return shift;
    }

    private int rightEdgeBefore(List<RecipeTypeDescriptor.SlotDef> defs, int actualCount, int limitRaw) {
        RecipeTypeDescriptor.SlotDef anchor = firstRepeatableDef(defs);
        int edge = 0;
        for (RecipeTypeDescriptor.SlotDef d : defs) {
            int right;
            if (d == anchor) {
                int extra = Math.max(0, actualCount - fixedSlotCount(defs, anchor));
                right = anchor.x + (extra + 1) * SLOT;
            } else {
                right = d.x + pushShiftFor(d.x) + (d.tank ? TANK_W : SLOT);
            }
            if (right <= limitRaw) edge = Math.max(edge, right);
        }
        return edge;
    }

    private static int pushShiftIfDownstream(List<RecipeTypeDescriptor.SlotDef> defs, int actualCount, int rawX) {
        RecipeTypeDescriptor.SlotDef anchor = firstRepeatableDef(defs);
        if (anchor == null || rawX < anchor.x + SLOT) return 0;
        int fixedCount = fixedSlotCount(defs, anchor);
        int extra = Math.max(0, actualCount - fixedCount);
        return Math.max(0, (extra - 1) * SLOT);
    }

    private boolean isStationCanvas() { return activeLayout != null && activeLayout.unified; }

    private int stationCanvasW() { return Math.max(60, GUI_W - 32); }

    private void fitStationCanvasIfNeeded() {
        if (!isStationCanvas()) return;
        stationCanvas.fitFreeform(unifiedWidth(), unifiedHeight(), stationCanvasW(), STATION_CANVAS_H);
    }

    private int slotScreenX(int rawX) { return isStationCanvas() ? stationCanvas.sx(ax, rawX) : ax + rawX; }

    private int slotScreenY(int sectionTop, int rawY) { return isStationCanvas() ? stationCanvas.sy(sectionTop, rawY) : sectionTop + rawY; }

    /** The first {@code repeatable} def in a section (its designated overflow anchor), or null if none. Only the first one counts - see {@link RecipeTypeDescriptor.SlotDef#repeatable}. */
    private static RecipeTypeDescriptor.SlotDef firstRepeatableDef(List<RecipeTypeDescriptor.SlotDef> defs) {
        for (RecipeTypeDescriptor.SlotDef d : defs) if (d.repeatable) return d;
        return null;
    }

    private static int fixedSlotCount(List<RecipeTypeDescriptor.SlotDef> defs, RecipeTypeDescriptor.SlotDef repeatableDef) {
        int count = 0;
        for (RecipeTypeDescriptor.SlotDef d : defs) if (d != repeatableDef) count++;
        return count;
    }

    private int gridScrollBarX() { return ax + gridCols * SLOT + 4; }

    private int gridRows(int count) {
        return Math.max(1, (int) Math.ceil((count + 1) / (double) gridCols));
    }

    private RecipeData.MachineSlotType cycle(RecipeData.MachineSlotType t) {
        RecipeData.MachineSlotType[] v = RecipeData.MachineSlotType.values();
        return v[(t.ordinal() + 1) % v.length];
    }

    private String typeLabel(RecipeData.MachineSlotType t) {
        return switch (t) {
            case ITEM -> Lang.get("gui.kve.machine.kind.item");
            case FLUID -> Lang.get("gui.kve.machine.kind.fluid");
            case TAG -> Lang.get("gui.kve.machine.kind.tag");
        };
    }

    private static List<Component> tagTooltip(String tagId, int count) {
        List<Component> lines = new ArrayList<>();
        String id = tagId == null ? "" : (tagId.startsWith("#") ? tagId.substring(1) : tagId);
        lines.add(Component.literal("#" + id).withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
        if (count > 1) lines.add(Component.literal("x" + count));
        var members = com.kve.common.recipe.TagIngredient.members(id);
        for (int i = 0; i < Math.min(6, members.size()); i++) {
            lines.add(Component.literal("  ").append(new ItemStack(members.get(i)).getHoverName()).withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        if (members.size() > 6) lines.add(Component.literal("  +" + (members.size() - 6)).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        if (members.isEmpty()) lines.addAll(brokenTagLines(id));
        return lines;
    }

    /** Lineas de aviso para un tag sin items (vacio o roto segun el log). Las usa CraftEditorScreen. */
    public static List<Component> brokenTagLines(String tagId) {
        List<Component> out = new ArrayList<>();
        String reason = com.kve.common.recipe.BrokenTagLog.reason(tagId);
        out.add(Component.literal(Lang.get(reason != null ? "gui.kve.recipe.tag_broken" : "gui.kve.recipe.tag_empty"))
                .withStyle(net.minecraft.ChatFormatting.RED));
        if (reason != null) {
            out.add(Component.literal(Lang.get("gui.kve.recipe.tag_broken_cause", reason)).withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        return out;
    }

    private void toggleEnabled() { draftEnabled = !draftEnabled; refresh(); }

    private void openTypePicker() {
        minecraft.setScreen(new RecipeTypePickerScreen(pickerReturn(), id -> {
            var descriptor = com.kve.common.recipetype.RecipeTypeDescriptorRegistry.find(id);
            if (descriptor.isEmpty() || !descriptor.get().hasSlotLayout()) {
                parent.switchToNodeGridType(id);
                backToParent();
                return;
            }
            String previous = draftCustomTypeId;
            draftCustomTypeId = id;
            if (previous != null && !previous.isBlank() && !previous.equals(id)) adaptDraftToType(descriptor.get());
            applyRecipeTypeDescriptorHints(id);
            refresh();
        }));
    }

    private void adaptDraftToType(com.kve.common.recipetype.RecipeTypeDescriptor d) {
        JsonObject old = parseExtraJsonFragment(draftExtraJson);
        JsonObject kept = new JsonObject();
        for (var f : d.extraFields) {
            if (old.has(f.key)) {
                kept.add(f.key, old.get(f.key));
            } else {
                try { kept.add(f.key, com.google.gson.JsonParser.parseString(f.skeletonValueLiteral())); }
                catch (Exception ignored) { kept.addProperty(f.key, ""); }
            }
        }
        draftExtraJson = d.extraFields.isEmpty() ? "" : fragmentFromObject(kept);

        int removed = trimToCapacity(draftInputs, slotCapacity(d, false));
        removed += trimToCapacity(draftOutputs, slotCapacity(d, true));
        if (removed > 0) flash(Lang.get("gui.kve.recipe.type_switch_trimmed", removed));
    }

    private static int slotCapacity(com.kve.common.recipetype.RecipeTypeDescriptor d, boolean outputs) {
        if (!outputs && d.hasRitualView() && d.ritualView != null) return Math.max(1, d.ritualView.maxOfferings);
        if (!d.hasSlotLayout()) return Integer.MAX_VALUE;
        int n = 0;
        for (var slot : d.slotLayout.slots) {
            if (slot.isOutput() != outputs) continue;
            if (slot.repeatable) return Integer.MAX_VALUE;
            n++;
        }
        return n;
    }

    private static int trimToCapacity(List<RecipeData.MachineSlot> list, int capacity) {
        int removed = 0;
        while (list.size() > capacity) { list.remove(list.size() - 1); removed++; }
        return removed;
    }

    private void applyRecipeTypeDescriptorHints(String recipeTypeId) {
        var found = com.kve.common.recipetype.RecipeTypeDescriptorRegistry.find(recipeTypeId);
        pendingAddonToggleLabel = null;
        pendingAddonWarning = null;
        if (found.isEmpty()) return;
        var d = found.get();

        if (!d.extraFields.isEmpty() && (draftExtraJson == null || draftExtraJson.isEmpty())) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < d.extraFields.size(); i++) {
                var f = d.extraFields.get(i);
                sb.append("\"").append(f.key).append("\": ").append(f.skeletonValueLiteral());
                if (i < d.extraFields.size() - 1) sb.append(", ");
            }
            draftExtraJson = sb.toString();
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
        if (d.hasRitualView()) {
            hint += Lang.get("gui.kve.recipe.hint.ritual");
        } else if (d.slotLayout != null) {
            hint += Lang.get("gui.kve.recipe.hint.slot_grid");
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
        applyRecipeTypeDescriptorHints(draftCustomTypeId);
        refresh();
    }

    private void addSlot(boolean input) {
        addSlotOfType(input, input ? pendingInputType : pendingOutputType);
    }

    private static RecipeData.MachineSlotType mapSlotType(String type) {
        if (type == null) return RecipeData.MachineSlotType.ITEM;
        return switch (type.toLowerCase(java.util.Locale.ROOT)) {
            case "fluid" -> RecipeData.MachineSlotType.FLUID;
            case "tag" -> RecipeData.MachineSlotType.TAG;
            default -> RecipeData.MachineSlotType.ITEM;
        };
    }

    private void addSlotOfType(boolean input, RecipeData.MachineSlotType type) {
        List<RecipeData.MachineSlot> target = input ? draftInputs : draftOutputs;
        pickNewSlot(type, "", input, target::add);
    }

    private RecipeData.MachineSlot nbtBadgeHoverSlot = null;

    private void replaceSlot(RecipeData.MachineSlot slot) {
        if (slot.type == RecipeData.MachineSlotType.ITEM && slot.item != null && !slot.item.isEmpty()
                && (slot == nbtBadgeHoverSlot || hasControlDown())) {
            minecraft.setScreen(new com.kve.client.gui.picker.NbtEditorScreen(pickerReturn(), slot.item, s -> {
                slot.item = s.copy();
                refresh();
                minecraft.setScreen(pickerReturn());
            }));
            return;
        }
        switch (slot.type) {
            case ITEM -> {
                ItemPickerScreen picker = new ItemPickerScreen(pickerReturn(), stack -> {
                    int count = Math.max(1, slot.item.getCount());
                    fillFromPick(slot, stack);
                    if (slot.type == RecipeData.MachineSlotType.TAG) slot.tagCount = count;
                    else slot.item.setCount(count);
                    refresh();
                }, false);
                minecraft.setScreen(isInputSlot(slot) ? picker.withTagOption() : picker);
            }
            case FLUID -> minecraft.setScreen(new FluidPickerScreen(pickerReturn(), id -> {
                slot.fluidId = id;
                refresh();
            }));
            case TAG -> minecraft.setScreen(new TagPresetPickerScreen(pickerReturn(), "Tags", tagOptions(), id -> {
                slot.tagId = id;
                refresh();
            }));
        }
    }

    private int slotAmount(RecipeData.MachineSlot s) {
        return switch (s.type) {
            case ITEM -> Math.max(1, s.item.getCount());
            case FLUID -> Math.max(1, s.fluidAmount);
            case TAG -> Math.max(1, s.tagCount);
        };
    }

    private static String slotKindText(RecipeTypeDescriptor.SlotDef d) {
        if (d.isChemical()) {
            List<String> kinds = com.kve.common.recipetype.ChemicalRegistryHelper.parseKinds(d.chemical);
            List<String> names = new ArrayList<>();
            for (String k : kinds) names.add(switch (k) {
                case "gas" -> "gas";
                case "slurry" -> "slurry";
                case "pigment" -> Lang.get("gui.kve.machine.chem.pigment");
                case "infuse_type" -> Lang.get("gui.kve.machine.chem.infuse_type");
                default -> k;
            });
            return Lang.get("gui.kve.machine.chemical", String.join(Lang.get("gui.kve.machine.or"), names));
        }
        return switch (mapSlotType(d.type)) {
            case FLUID -> Lang.get("gui.kve.machine.kind.fluid");
            case TAG -> Lang.get("gui.kve.machine.kind.item_tag");
            case ITEM -> Lang.get("gui.kve.machine.kind.item");
        };
    }

    private static int amountStepFor(RecipeData.MachineSlot s) {
        return s.type == RecipeData.MachineSlotType.FLUID ? fluidScrollStep() : (hasShiftDown() ? 10 : 1);
    }

    private void bumpSlotAmount(RecipeData.MachineSlot s, int delta, RecipeTypeDescriptor.SlotDef def) {
        bumpSlotAmount(s, delta);
        if (def != null && def.maxCount > 0 && s.type != RecipeData.MachineSlotType.FLUID && slotAmount(s) > def.maxCount) {
            if (s.type == RecipeData.MachineSlotType.ITEM) s.item.setCount(def.maxCount);
            else s.tagCount = def.maxCount;
            flash(Lang.get("gui.kve.machine.max_count", def.maxCount));
        }
    }

    private void bumpSlotAmount(RecipeData.MachineSlot s, int delta) {
        int newAmount = Math.max(1, slotAmount(s) + delta);
        switch (s.type) {
            case ITEM -> s.item.setCount(Math.min(newAmount, 64));
            case FLUID -> s.fluidAmount = newAmount;
            case TAG -> s.tagCount = newAmount;
        }
    }

    private void bumpSlotChance(RecipeData.MachineSlot s, int deltaPct) {
        int pct = (int) Math.round(s.chance * 100);
        pct = Math.max(0, Math.min(100, pct + deltaPct));
        s.chance = pct / 100.0;
    }

    private List<String> tagOptions() {
        List<String> loaded = new ArrayList<>();
        if (minecraft.level != null) {
            try {
                minecraft.level.registryAccess()
                        .registryOrThrow(net.minecraft.core.registries.Registries.ITEM)
                        .getTagNames().forEach(k -> loaded.add(k.location().toString()));
                Collections.sort(loaded);
            } catch (Exception ignored) { loaded.clear(); }
        }
        return !loaded.isEmpty() ? loaded : TagExporter.commonTags(TagExporter.RegistryType.ITEM);
    }

    /**
 * (paso Preview): extraido de {@link #save} sin
     * cambiar ninguna linea de logica - arma el {@link RecipeData} del
     * draft actual para que {@link #save} y {@link #previewRecipe()}
     * lean exactamente lo mismo del formulario, mismo patron que
     * CraftEditorScreen#buildDraftRecipeData(). No stagea ni valida nada -
     * eso sigue siendo responsabilidad de cada caller.
     */
    private RecipeData buildDraftRecipeData() {
        RecipeData d = new RecipeData();
        String id = draftRecipeId.trim();
        d.recipeId = id.isEmpty() ? KveGuiStyle.randomBareId() : id;
        d.type = RecipeData.RecipeType.CUSTOM_MACHINE;
        d.enabled = draftEnabled;
        d.category = draftCategory.trim();
        d.customTypeId = draftCustomTypeId;
        d.customExtraJson = draftExtraJson;
        if (ritualMode && ritualCenterMode == RitualCenterMode.INPUT_SLOT && !draftInputs.isEmpty()) {
            d.machineInputs.add(draftInputs.get(0).copy());
            List<RecipeData.MachineSlot> offerings = ritualOfferings();
            syncRitualOfferingRingSlots(offerings);
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < offerings.size(); i++) order.add(i);
            order.sort((a, b) -> Integer.compare(ritualOfferingRingSlot.get(a), ritualOfferingRingSlot.get(b)));
            for (int i : order) {
                RecipeData.MachineSlot copy = offerings.get(i).copy();
                copy.ringSlot = ritualOfferingRingSlot.get(i);
                d.machineInputs.add(copy);
            }
        } else if (ritualMode) {
            List<RecipeData.MachineSlot> offerings = ritualOfferings();
            syncRitualOfferingRingSlots(offerings);
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < offerings.size(); i++) order.add(i);
            order.sort((a, b) -> Integer.compare(ritualOfferingRingSlot.get(a), ritualOfferingRingSlot.get(b)));
            for (int i : order) {
                RecipeData.MachineSlot copy = offerings.get(i).copy();
                copy.ringSlot = ritualOfferingRingSlot.get(i);
                d.machineInputs.add(copy);
            }
        } else {
            for (RecipeData.MachineSlot s : draftInputs) d.machineInputs.add(s.copy());
        }
        for (RecipeData.MachineSlot s : draftOutputs) d.machineOutputs.add(s.copy());
        return d;
    }

    private void previewRecipe() {
        RecipeData d = buildDraftRecipeData();
        if (d.recipeId.isEmpty()) d.recipeId = "preview";
        String script = KubeJSExporter.previewRecipeJS(d);
        minecraft.setScreen(new RecipePreviewScreen(pickerReturn(), d.recipeId, script));
    }

    private void save() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }
        RecipeData d = buildDraftRecipeData();

        List<String> problems = KubeJSExporter.validateRecipe(d);
        if (!problems.isEmpty()) { flash(String.join(" ", problems)); return; }

        // solo es renombrar si la receta original es una de las tuyas (las de mods se reemplazan via pendingRemovalId)
        RecipeData renamedOriginal = editing == null || editing.recipeId == null || editing.recipeId.equals(d.recipeId) ? null
                : KubeJSExporter.getAddedRecipes().stream().filter(r -> r.recipeId.equals(editing.recipeId)).findFirst().orElse(null);
        String renamedFromId = renamedOriginal != null ? renamedOriginal.recipeId : null;
        RecipeData previous = KubeJSExporter.getAddedRecipes().stream()
                .filter(r -> r.recipeId.equals(d.recipeId)).findFirst().orElse(null);
        // renamedFromId != null means the player changed the ID box while
        // editing an existing recipe (not just leaving it as-is, which is
        // the normal "EDIT" case below). If `previous` is ALSO non-null
        // here, that means the new ID the player typed already belongs to
        // a totally different recipe - without this check, the code below
        // would silently stage-delete the recipe under the OLD id and
        // stageRecipe() this one over the top of that unrelated recipe,
        // destroying it with no warning at all. Block the save instead and
        // let the player pick a free ID, same as the "no recipe type
        // chosen"/"no output" validation errors above.
        // un ID que ya usa OTRA receta (al renombrar o en una receta nueva) pisaria esa receta sin avisar
        boolean sameRecipe = editing != null && d.recipeId.equals(editing.recipeId);
        if (previous != null && !sameRecipe) {
            flash(Lang.get("gui.kve.recipe.id_taken", d.recipeId));
            return;
        }
        PendingChange.Action action = previous == null ? PendingChange.Action.CREATE : PendingChange.Action.EDIT;

        if (renamedFromId != null) KubeJSExporter.stageDeleteRecipe(renamedFromId);
        KubeJSExporter.stageRecipe(d);

        String removalId = pendingRemovalId;
        if (removalId != null) KubeJSExporter.stageRemoveVanilla(removalId);

        PendingChangeManager.queue(PendingChange.of(PendingDomain.RECIPES, d.recipeId, action, false,
                () -> {
                    KubeJSExporter.commitAdded();
                    if (removalId != null) KubeJSExporter.commitRemoved();
                },
                () -> {
                    KubeJSExporter.discardStagedRecipe(d.recipeId, previous);
                    // se restaura la receta original capturada antes de guardar, no el borrador actual
                    if (renamedOriginal != null) KubeJSExporter.stageRecipe(renamedOriginal);
                    if (removalId != null) KubeJSExporter.discardStagedRemoveVanilla(removalId);
                }));

        editing = d;
        draftRecipeId = d.recipeId;
        parent.refreshHostedChrome();
        if (removalId != null) {
            flash(Lang.get("gui.kve.recipe.saved_will_remove", removalId) + " " + Lang.get("gui.kve.pending.queued_hint_reload"));
            pendingRemovalId = null;
            parent.clearPendingRemovalId();
        } else {
            flash(Lang.get("gui.kve.pending.queued_hint_reload"));
        }
        refresh();
    }

    private void delete() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }
        // receta de un mod cargada desde "Receta existente": borrarla es quitarla, igual que en el editor normal
        if (pendingRemovalId != null && !pendingRemovalId.isEmpty()) {
            String removal = pendingRemovalId;
            minecraft.setScreen(new KveConfirmScreen(
                    confirmed -> {
                        minecraft.setScreen(this);
                        if (!confirmed) return;
                        pendingRemovalId = null;
                        parent.clearPendingRemovalId();
                        backToParent();
                        parent.queueVanillaRemoval(removal);
                    },
                    Component.literal(Lang.get("gui.kve.recipe.delete_confirm_title")),
                    Component.literal(Lang.get("gui.kve.recipe.delete_confirm_msg", removal))));
            return;
        }
        if (editing == null || editing.recipeId == null || editing.recipeId.isEmpty()) {
            backToParent();
            return;
        }
        String id = editing.recipeId;
        minecraft.setScreen(new KveConfirmScreen(
                confirmed -> {
                    minecraft.setScreen(this);
                    if (confirmed) doDelete();
                },
                Component.literal(Lang.get("gui.kve.recipe.delete_confirm_title")),
                Component.literal(Lang.get("gui.kve.recipe.delete_confirm_msg", id))));
    }

    private void doDelete() {
        String id = editing.recipeId;
        RecipeData previous = KubeJSExporter.getAddedRecipes().stream()
                .filter(r -> r.recipeId.equals(id)).findFirst().orElse(null);
        KubeJSExporter.stageDeleteRecipe(id);
        PendingChangeManager.queue(PendingChange.of(PendingDomain.RECIPES, id, PendingChange.Action.DELETE, false,
                KubeJSExporter::commitAdded,
                () -> { if (previous != null) KubeJSExporter.stageRecipe(previous); }));
        backToParent();
    }

    private void duplicateMachineRecipe() {
        if (!ClientPermissionState.canEdit()) { flash(Lang.get("gui.kve.perm.denied")); return; }
        editing = null;
        pendingRemovalId = null;
        if (recipeIdBox != null) {
            String base = draftRecipeId.trim();
            String candidate = (base.isEmpty() ? "recipe" : base) + "_copy";
            String finalId = candidate;
            int n = 2;
            while (machineRecipeIdInUse(finalId)) {
                finalId = candidate + n;
                n++;
            }
            recipeIdBox.setValue(finalId);
        }
        flash(Lang.get("gui.kve.recipe.duplicated_hint"));
    }

    private boolean machineRecipeIdInUse(String id) {
        return KubeJSExporter.getAddedRecipes().stream().anyMatch(r -> r.recipeId.equals(id));
    }

    private void cancel() { backToParent(); }

    private void backToParent() {
        if (hosted) {
            if (onExitEmbedded != null) onExitEmbedded.run();
            return;
        }
        parent.init();
        minecraft.setScreen(parent);
    }

    private void flash(String msg) { flashMsg = msg; flashAt = System.currentTimeMillis(); }

    private Button btn(String label, int x, int y, int w, Runnable a) {
        return KveGuiStyle.smartBtn(x, y, w, label, b -> a.run());
    }

    private static final class RitualOptionButton extends KvePlateButton {
        private final Runnable onRightClick;

        RitualOptionButton(int x, int y, int w, int h, String label, Runnable onLeftClick, Runnable onRightClick) {
            super(x, y, w, h, Component.literal(label), b -> onLeftClick.run());
            this.onRightClick = onRightClick;
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button == 1 && this.active && this.visible && isMouseOver(mx, my)) {
                onRightClick.run();
                return true;
            }
            return super.mouseClicked(mx, my, button);
        }
    }

    private record MachineSnapshot(List<RecipeData.MachineSlot> in, List<RecipeData.MachineSlot> out, String extra, String sig) {}

    private final com.kve.common.util.UndoStack<MachineSnapshot> undoStack = new com.kve.common.util.UndoStack<>(50);
    private MachineSnapshot lastSnapshot;

    private MachineSnapshot snapshot() {
        List<RecipeData.MachineSlot> in = new ArrayList<>(), out = new ArrayList<>();
        StringBuilder sig = new StringBuilder();
        for (RecipeData.MachineSlot s : draftInputs) { in.add(s.copy()); slotSig(sig.append('i'), s); }
        for (RecipeData.MachineSlot s : draftOutputs) { out.add(s.copy()); slotSig(sig.append('o'), s); }
        String extra = draftExtraJson == null ? "" : draftExtraJson;
        sig.append('x').append(extra);
        return new MachineSnapshot(in, out, extra, sig.toString());
    }

    private static void slotSig(StringBuilder sb, RecipeData.MachineSlot s) {
        sb.append(s.type).append('|');
        if (s.item != null && !s.item.isEmpty()) {
            sb.append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.item.getItem())).append('*').append(s.item.getCount());
            if (!s.item.getComponentsPatch().isEmpty()) sb.append(s.item.getComponentsPatch());
        }
        sb.append('|').append(s.fluidId).append('*').append(s.fluidAmount)
          .append('|').append(s.tagId).append('*').append(s.tagCount)
          .append('|').append(s.ringSlot).append('|').append(s.chance).append(';');
    }

    private void trackUndo() {
        MachineSnapshot now = snapshot();
        if (lastSnapshot != null && !lastSnapshot.sig().equals(now.sig())) undoStack.push(lastSnapshot);
        lastSnapshot = now;
    }

    private void undoMachine() {
        trackUndo();
        MachineSnapshot prev = undoStack.undo(lastSnapshot);
        if (prev == null) return;
        restoreSnapshot(prev);
        flash(Lang.get("gui.kve.recipe.undone"));
    }

    private void redoMachine() {
        trackUndo();
        MachineSnapshot next = undoStack.redo(lastSnapshot);
        if (next == null) return;
        restoreSnapshot(next);
        flash(Lang.get("gui.kve.recipe.redone"));
    }

    private void restoreSnapshot(MachineSnapshot s) {
        draftInputs.clear();
        for (RecipeData.MachineSlot m : s.in()) draftInputs.add(m.copy());
        draftOutputs.clear();
        for (RecipeData.MachineSlot m : s.out()) draftOutputs.add(m.copy());
        draftExtraJson = s.extra();
        lastSnapshot = s;
        refresh();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        trackUndo();
        nbtBadgeHoverSlot = null;
        {
            int wantW = hosted ? Math.max(hostedW, GUI_W_MIN) : GUI_W_MIN;
            int wantH = hosted ? Math.max(Math.max(GUI_H_MIN, computeMinGuiH()), hostedH) : Math.max(GUI_H_MIN, computeMinGuiH());
            if (wantW != GUI_W || wantH != GUI_H) refresh();
        }
        if (!hosted) {
            renderTransparentBackground(g);
            KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H,
                    ritualMode ? Lang.get("gui.kve.machine.title_ritual") : Lang.get("gui.kve.machine.title_machine"));
        }

        ItemStack typeIcon = RecipeTypePickerScreen.stationIconForTypeId(draftCustomTypeId);
        KveGuiStyle.drawSlotGlowRect(g, ax, row2Y - 1, TYPE_ICON_W, KveGuiStyle.BTN_H, false, KveGuiStyle.th(0xFF60A8F0));
        if (!typeIcon.isEmpty()) {
            SafeItemRender.render(g, typeIcon, ax, row2Y);
        } else {
            KveGuiStyle.drawTextSafe(g, "?", ax + TYPE_ICON_W / 2 - 2, row2Y + 2, 12, KveGuiStyle.TEXT_MUTED);
        }

        if (ritualMode) {
            renderRitualCanvas(g, mx, my);
        } else {
            boolean unified = activeLayout != null && activeLayout.unified;
            boolean sameRow = !unified && activeLayout != null && activeLayout.sameRow;
            if (!sameRow) {
                drawGridLabel(g, unified ? Lang.get("gui.kve.machine.section.recipe") : Lang.get("gui.kve.machine.section.inputs"), unified ? draftInputs.size() + draftOutputs.size() : draftInputs.size(), ax, inputsLabelY);
            }

            if (isStationCanvas()) {
                g.enableScissor(ax, inputsGridY, ax + stationCanvasW(), inputsGridY + STATION_CANVAS_H);
                stationCanvas.drawBackground(g, ax, inputsGridY, stationCanvasW(), STATION_CANVAS_H);
            }
            if (activeLayout != null) drawCustomSlotGrid(g, draftInputs, layoutInputDefs, inputsGridY, mx, my);
            else drawSlotGrid(g, draftInputs, inputScrollBar, inputsGridY, mx, my);

            if (!unified && !sameRow) drawGridLabel(g, Lang.get("gui.kve.machine.section.outputs"), draftOutputs.size(), ax, outputsLabelY);
            if (activeLayout != null) drawCustomSlotGrid(g, draftOutputs, layoutOutputDefs, outputsGridY, mx, my);
            else drawSlotGrid(g, draftOutputs, outputScrollBar, outputsGridY, mx, my);

            if (unified || sameRow) {
                if (activeLayout.arrow != null) drawLayoutArrow(g, activeLayout.arrow, inputsGridY, sameRow);
            } else if (activeLayout != null) {
                drawSectionGapArrow(g);
            }
            if (activeLayout != null && activeLayout.containerFieldSlot != null && containerFieldSlotField != null) {
                drawContainerFieldSlot(g, activeLayout.containerFieldSlot, inputsGridY, mx, my);
            }
            if (isStationCanvas()) {
                drawFieldTanks(g, mx, my);
                drawEnergyBar(g);
                drawDecorItemsAndNotes(g, mx, my);
            }
            if (isStationCanvas()) {
                stationCanvas.drawZoomReadout(g, ax, inputsGridY);
                g.disableScissor();
            }
        }

        if (draftCustomTypeId != null && !draftCustomTypeId.isEmpty()) {
            String status = com.kve.common.recipetype.RecipeTypeDescriptorRegistry.dedicatedCallStatusLine(draftCustomTypeId);
            if (!status.isEmpty()) {
                int col = status.charAt(0) == '\u2713' ? 0xFF66DD99
                        : status.charAt(0) == '\u26a0' ? 0xFFFFAA55
                        : KveGuiStyle.th(0xFF8899BB);
                KveGuiStyle.drawTextSafe(g, status, ax, bottomBtnY - 22, GUI_W - 20, col);
            }
        }

        if (!flashMsg.isEmpty() && System.currentTimeMillis() - flashAt < 4500) {
            KveGuiStyle.drawTextSafe(g, flashMsg, ax, bottomBtnY - 11, GUI_W - 20, KveGuiStyle.ACCENT_GOLD);
        }

        KveGuiStyle.renderWidgets(this, g, mx, my, pt);

        if (ritualMode) {
            drawRitualHoverTooltip(g, mx, my);
        } else if (!drawFieldTankOrEnergyTooltip(g, mx, my)) {
            drawHoverTooltip(g, draftInputs, inputsGridY, mx, my);
            drawHoverTooltip(g, draftOutputs, outputsGridY, mx, my);
            if (hitsContainerFieldSlot((int) mx, (int) my)) {
                ItemStack cStack = resolveItemFromId(getExtraFieldRawValue(containerFieldSlotField.key));
                if (!cStack.isEmpty()) g.renderTooltip(font, cStack, mx, my);
            }
        }

        pendingAddonWarningBtn = KveGuiStyle.drawFloatingNotice(g, pendingAddonWarning,
                Lang.get("gui.kve.confirm.ok"), L + GUI_W / 2, T + 40, width, height, KveGuiStyle.TEXT_ERROR);
    }

    private void renderRitualCanvas(GuiGraphics g, int mx, int my) {
        List<RecipeData.MachineSlot> offerings = ritualOfferings();
        syncRitualOfferingRingSlots(offerings);

        boolean centerHov = RitualRingLayout.hitTestCenter(mx, my, ritualCenterX, ritualCenterY);
        drawRitualCenterSlot(g, centerHov);
        int centerLabelY = ritualCenterY + RitualRingLayout.CENTER_SLOT / 2
                + RitualRingLayout.RING_RADIUS + RitualRingLayout.OFFERING_SLOT / 2 + 2;
        KveGuiStyle.drawCenteredTextSafe(g, Lang.get("gui.kve.ritual.center"),
                ritualCenterX, centerLabelY, KveGuiStyle.TEXT_LABEL);

        for (int i = 0; i < ritualRingPositions.size(); i++) {
            RitualRingLayout.Offering pos = ritualRingPositions.get(i);
            boolean hov = RitualRingLayout.hits(mx, my, pos.x, pos.y, RitualRingLayout.OFFERING_SLOT);
            int offeringIdx = offeringAtRingSlot(offerings, i);
            if (offeringIdx >= 0) drawFilledSlot(g, offerings.get(offeringIdx), pos.x, pos.y, hov, 0);
            else drawAddSlot(g, pos.x, pos.y, hov, 0);
        }
        KveGuiStyle.drawTextSafe(g,
                Lang.get("gui.kve.ritual.offerings") + " (" + offerings.size() + "/" + ritualMaxOfferings + ")",
                ax, ritualLabelY, RITUAL_RING_COL_W, KveGuiStyle.ACCENT_GOLD);

        boolean outHov = RitualRingLayout.hits(mx, my, ritualOutX, ritualOutY, RitualRingLayout.OFFERING_SLOT);
        drawOutputSlotOrAdd(g, ritualOutX, ritualOutY, outHov);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.machine.slot.output"), ritualOutX + RitualRingLayout.OFFERING_SLOT + 4, ritualOutY + 4, 80, KveGuiStyle.TEXT_LABEL);

        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.ritual.params"), ritualParamsColX, ritualLabelY, ritualParamsColW, KveGuiStyle.ACCENT_GOLD);
        if (activeDescriptor != null) {
            StringBuilder sb = new StringBuilder();
            for (RecipeTypeDescriptor.ExtraField f : activeDescriptor.extraFields) {
                if (ritualCenterMode == RitualCenterMode.EXTRA_FIELD && ritualCenterExtraField == f) continue;
                if (ritualOptionFields.contains(f)) continue;
                if (sb.length() > 0) sb.append(", ");
                sb.append(f.key);
            }
            if (sb.length() > 0) {
                KveGuiStyle.drawTextSafe(g, sb.toString(), ritualParamsColX, ritualParamsColY + 14, ritualParamsColW, KveGuiStyle.TEXT_MUTED);
            }
        }
    }

    private void drawRitualCenterSlot(GuiGraphics g, boolean hov) {
        if (ritualCenterMode == RitualCenterMode.NONE) {
            drawLockedSlot(g, ritualCenterX, ritualCenterY, 0);
            return;
        }
        ItemStack stack = ritualCenterItemStack();
        KveGuiStyle.drawSlotGlow(g, ritualCenterX, ritualCenterY, RitualRingLayout.CENTER_SLOT, hov, KveGuiStyle.th(0xFFE8C96A));
        int ix = ritualCenterX + 1, iy = ritualCenterY + 1;
        if (!stack.isEmpty()) {
            try {
                SafeItemRender.render(g, stack, ix, iy);
                g.renderItemDecorations(font, stack, ix, iy, null);
            } catch (Throwable ignored) {
                g.fill(ix, iy, ix + 16, iy + 16, KveGuiStyle.th(0xFF334455));
            }
        } else {
            KveGuiStyle.drawTextSafe(g, "+", ix + 4, iy + 4, 12, hov ? KveGuiStyle.TEXT_OK : KveGuiStyle.TEXT_MUTED);
        }
    }

    private void drawOutputSlotOrAdd(GuiGraphics g, int x, int y, boolean hov) {
        if (draftOutputs.isEmpty()) drawAddSlot(g, x, y, hov, 0);
        else drawFilledSlot(g, draftOutputs.get(0), x, y, hov, 0);
    }

    private void drawContainerFieldSlot(GuiGraphics g, RecipeTypeDescriptor.ContainerFieldSlot cfs, int sectionTop, int mx, int my) {
        int sx = slotScreenX(cfs.x);
        int sy = slotScreenY(sectionTop, cfs.y);
        boolean hov = mx >= sx && mx < sx + SLOT && my >= sy && my < sy + SLOT;
        ItemStack stack = resolveItemFromId(getExtraFieldRawValue(containerFieldSlotField.key));
        KveGuiStyle.drawSlotGlow(g, sx, sy, SLOT, hov, 0xFF77BBEE);
        int ix = sx + 1, iy = sy + 1;
        if (!stack.isEmpty()) {
            try {
                SafeItemRender.render(g, stack, ix, iy);
                g.renderItemDecorations(font, stack, ix, iy, null);
            } catch (Throwable ignored) {
                g.fill(ix, iy, ix + 16, iy + 16, KveGuiStyle.th(0xFF334455));
            }
        } else {
            KveGuiStyle.drawTextSafe(g, "+", ix + 4, iy + 4, 12, hov ? KveGuiStyle.TEXT_OK : KveGuiStyle.TEXT_MUTED);
        }
        String label = cfs.label != null && !cfs.label.isBlank() ? cfs.label : containerFieldSlotField.key;
        KveGuiStyle.drawTextSafe(g, label, sx + SLOT + 4, sy + 4, 70, KveGuiStyle.TEXT_LABEL);
    }

    private int[] fieldTankRect(RecipeTypeDescriptor.FieldTank ft) {
        return new int[]{slotScreenX(ft.x + pushShiftFor(ft.x)), slotScreenY(inputsGridY, ft.y), TANK_W, tankHeight(ft.h)};
    }

    private RecipeTypeDescriptor.FieldTank fieldTankAt(double mx, double my) {
        if (!isStationCanvas() || activeLayout == null) return null;
        for (RecipeTypeDescriptor.FieldTank ft : activeLayout.fieldTanks) {
            int[] r = fieldTankRect(ft);
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) return ft;
        }
        return null;
    }

    private String fieldTankId(RecipeTypeDescriptor.FieldTank ft) {
        String v = getExtraFieldRawValue(ft.idField);
        if (!v.isBlank()) return v;
        RecipeTypeDescriptor.ExtraField f = findExtraField(ft.idField);
        return f == null ? "" : f.defaultValue;
    }

    private int fieldTankAmount(RecipeTypeDescriptor.FieldTank ft) {
        if (!ft.hasAmountField()) return 0;
        String v = getExtraFieldRawValue(ft.amountField);
        if (v.isBlank()) {
            RecipeTypeDescriptor.ExtraField f = findExtraField(ft.amountField);
            v = f == null ? "" : f.defaultValue;
        }
        try { return (int) Math.max(0, Math.min(Integer.MAX_VALUE, Long.parseLong(v.trim()))); }
        catch (NumberFormatException e) { return 0; }
    }

    private RecipeTypeDescriptor.ExtraField findExtraField(String key) {
        if (activeDescriptor == null || key == null) return null;
        for (RecipeTypeDescriptor.ExtraField f : activeDescriptor.extraFields) if (f.key.equals(key)) return f;
        return null;
    }

    private void drawFieldTanks(GuiGraphics g, int mx, int my) {
        if (activeLayout == null) return;
        int boxX0 = ax, boxX1 = ax + stationCanvasW(), boxY0 = inputsGridY, boxY1 = inputsGridY + STATION_CANVAS_H;
        for (RecipeTypeDescriptor.FieldTank ft : activeLayout.fieldTanks) {
            int[] r = fieldTankRect(ft);
            if (stationSlotOutOfView(r[0], r[1], r[2], r[3], boxX0, boxX1, boxY0, boxY1)) continue;
            boolean hov = mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
            int glow = ft.isOutput() ? 0xFFE0A040 : 0xFF4FC3E0;
            String id = fieldTankId(ft);
            if (id.isBlank()) drawAddSlot(g, r[0], r[1], hov, r[3]);
            else drawFluidTank(g, id, fieldTankAmount(ft), r[0], r[1], r[3], hov, glow);
        }
    }

    private int[] decorItemRect(RecipeTypeDescriptor.DecorItem di) {
        return new int[]{slotScreenX(di.x + pushShiftFor(di.x)), slotScreenY(inputsGridY, di.y), SLOT, SLOT};
    }

    private RecipeTypeDescriptor.DecorItem decorItemAt(double mx, double my) {
        if (!isStationCanvas() || activeLayout == null) return null;
        for (RecipeTypeDescriptor.DecorItem di : activeLayout.decorItems) {
            int[] r = decorItemRect(di);
            if (stationSlotOutOfView(r[0], r[1], r[2], r[3], ax, ax + stationCanvasW(), inputsGridY, inputsGridY + STATION_CANVAS_H)) continue;
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) return di;
        }
        return null;
    }

    private void drawDecorItemsAndNotes(GuiGraphics g, int mx, int my) {
        if (activeLayout == null) return;
        int bx0 = ax, bx1 = ax + stationCanvasW(), by0 = inputsGridY, by1 = inputsGridY + STATION_CANVAS_H;
        for (RecipeTypeDescriptor.DecorItem di : activeLayout.decorItems) {
            int[] r = decorItemRect(di);
            if (stationSlotOutOfView(r[0], r[1], r[2], r[3], bx0, bx1, by0, by1)) continue;
            boolean hov = mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
            KveGuiStyle.drawSlotGlowRect(g, r[0], r[1], SLOT, SLOT, hov, 0xFF8A7A4A);
            ItemStack st = resolveItemFromId(di.item);
            if (!st.isEmpty()) {
                try { g.renderItem(st, r[0] + 1, r[1] + 1); } catch (Throwable ignored) {}
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                g.fill(r[0] + 1, r[1] + 1, r[0] + SLOT - 1, r[1] + SLOT - 1, hov ? 0x20000000 : 0x55101418);
                g.pose().popPose();
            }
        }
        for (RecipeTypeDescriptor.CanvasNote n : activeLayout.notes) {
            int x = slotScreenX(n.x + pushShiftFor(n.x)), y = slotScreenY(inputsGridY, n.y);
            int w = font.width(n.text);
            if (stationSlotOutOfView(x, y, w, font.lineHeight, bx0, bx1, by0, by1)) continue;
            KveGuiStyle.drawTextSafe(g, n.text, x, y, Math.max(8, bx1 - x - 2), n.color != 0 ? n.color : KveGuiStyle.TEXT_MUTED);
        }
    }

    private void drawEnergyBar(GuiGraphics g) {
        if (activeLayout == null || activeLayout.energyBar == null) return;
        RecipeTypeDescriptor.EnergyBar eb = activeLayout.energyBar;
        int x = slotScreenX(eb.x + pushShiftFor(eb.x)), y = slotScreenY(inputsGridY, eb.y);
        if (stationSlotOutOfView(x, y, eb.w, eb.h, ax, ax + stationCanvasW(), inputsGridY, inputsGridY + STATION_CANVAS_H)) return;
        g.fill(x - 1, y - 1, x + eb.w + 1, y + eb.h + 1, 0xFF1A1D26);
        g.fill(x, y, x + eb.w, y + eb.h, 0xFF3A1414);
        if (eb.h >= eb.w) {
            int fillTop = y + eb.h / 4;
            g.fillGradient(x, fillTop, x + eb.w, y + eb.h, 0xFFFF5A3C, 0xFFB01E1E);
        } else {
            int fillRight = x + eb.w * 3 / 4;
            g.fill(x, y, fillRight, y + eb.h, 0xFFE0402A);
        }
    }

    private boolean hitsEnergyBar(double mx, double my) {
        if (!isStationCanvas() || activeLayout == null || activeLayout.energyBar == null) return false;
        RecipeTypeDescriptor.EnergyBar eb = activeLayout.energyBar;
        int x = slotScreenX(eb.x + pushShiftFor(eb.x)), y = slotScreenY(inputsGridY, eb.y);
        return mx >= x - 1 && mx < x + eb.w + 1 && my >= y - 1 && my < y + eb.h + 1;
    }

    private boolean drawFieldTankOrEnergyTooltip(GuiGraphics g, int mx, int my) {
        RecipeTypeDescriptor.DecorItem di = decorItemAt(mx, my);
        if (di != null) {
            List<Component> lines = new ArrayList<>();
            ItemStack st = resolveItemFromId(di.item);
            String label = di.label != null && !di.label.isBlank() ? di.label : (st.isEmpty() ? di.item : st.getHoverName().getString());
            lines.add(Component.literal(label).withStyle(ChatFormatting.YELLOW));
            if (!st.isEmpty()) lines.add(Component.literal(st.getHoverName().getString()).withStyle(ChatFormatting.WHITE));
            else lines.add(Component.literal(di.item + " (mod no instalado)").withStyle(ChatFormatting.DARK_GRAY));
            lines.add(Component.literal(Lang.get("gui.kve.machine.decor_hint")).withStyle(ChatFormatting.DARK_GRAY));
            g.renderComponentTooltip(font, lines, mx, my);
            return true;
        }
        RecipeTypeDescriptor.FieldTank ft = fieldTankAt(mx, my);
        if (ft != null) {
            List<Component> lines = new ArrayList<>();
            String label = ft.label != null && !ft.label.isBlank() ? ft.label : ft.idField;
            lines.add(Component.literal(label).withStyle(ft.isOutput() ? ChatFormatting.GOLD : ChatFormatting.AQUA));
            String id = fieldTankId(ft);
            lines.add(Component.literal(id.isBlank() ? Lang.get("gui.kve.machine.unset") : id));
            if (ft.hasAmountField()) lines.add(Component.literal(fieldTankAmount(ft) + " mB"));
            String kinds = String.join(" / ", com.kve.common.recipetype.ChemicalRegistryHelper.parseKinds(ft.chemical));
            lines.add(Component.literal(kinds.isEmpty() ? Lang.get("gui.kve.machine.kind.fluid") : Lang.get("gui.kve.machine.chemical", kinds)).withStyle(ChatFormatting.DARK_GRAY));
            lines.add(Component.empty());
            lines.add(Component.literal(Lang.get("gui.kve.machine.slot_hint")).withStyle(ChatFormatting.GRAY));
            if (ft.hasAmountField()) lines.add(Component.literal(Lang.get("gui.kve.machine.tank_scroll_hint")).withStyle(ChatFormatting.DARK_GRAY));
            g.renderComponentTooltip(font, lines, mx, my);
            return true;
        }
        if (hitsEnergyBar(mx, my)) {
            RecipeTypeDescriptor.EnergyBar eb = activeLayout.energyBar;
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(Lang.get("gui.kve.machine.energy")).withStyle(ChatFormatting.RED));
            if (eb.hasField()) {
                String v = getExtraFieldRawValue(eb.field);
                if (v.isBlank()) {
                    RecipeTypeDescriptor.ExtraField f = findExtraField(eb.field);
                    v = f == null ? "?" : f.defaultValue;
                }
                lines.add(Lang.c("gui.kve.machine.energy_field", v + " " + eb.unit, eb.field));
            } else {
                lines.add(Component.literal(Lang.get("gui.kve.machine.energy_by_machine")).withStyle(ChatFormatting.GRAY));
            }
            g.renderComponentTooltip(font, lines, mx, my);
            return true;
        }
        return false;
    }

    private boolean handleFieldTankClick(RecipeTypeDescriptor.FieldTank ft, int btn) {
        if (btn == 1) { setExtraFieldStringValue(ft.idField, ""); refresh(); return true; }
        if (btn == 0) {
            minecraft.setScreen(new FluidPickerScreen(pickerReturn(), id -> {
                setExtraFieldStringValue(ft.idField, id);
                refresh();
            }, ft.chemical));
        }
        return true;
    }

    private static int fluidScrollStep() {
        int step = hasShiftDown() ? 10 : 1;
        if (hasAltDown()) step *= 100;
        return step;
    }

    private void bumpFieldTankAmount(RecipeTypeDescriptor.FieldTank ft, int delta) {
        if (!ft.hasAmountField()) return;
        long v = Math.max(1, (long) fieldTankAmount(ft) + delta);
        JsonObject obj = parseExtraJsonFragment(draftExtraJson);
        if (obj == null) obj = new JsonObject();
        obj.addProperty(ft.amountField, v);
        draftExtraJson = fragmentFromObject(obj);
        if (extraJsonBox != null) extraJsonBox.setValue(draftExtraJson);
    }

    private boolean handleContainerFieldSlotClick(int btn) {
        if (btn == 1) { setExtraFieldStringValue(containerFieldSlotField.key, ""); refresh(); return true; }
        if (btn == 0) {
            minecraft.setScreen(new ItemPickerScreen(pickerReturn(), stack -> {
                setExtraFieldStringValue(containerFieldSlotField.key, itemId(stack));
                refresh();
            }, false));
        }
        return true;
    }

    private boolean hitsContainerFieldSlot(int mx, int my) {
        if (activeLayout == null || activeLayout.containerFieldSlot == null || containerFieldSlotField == null) return false;
        RecipeTypeDescriptor.ContainerFieldSlot cfs = activeLayout.containerFieldSlot;
        int sx = slotScreenX(cfs.x);
        int sy = slotScreenY(inputsGridY, cfs.y);
        return mx >= sx && mx < sx + SLOT && my >= sy && my < sy + SLOT;
    }

    private ItemStack ritualCenterItemStack() {
        if (ritualCenterMode == RitualCenterMode.EXTRA_FIELD && ritualCenterExtraField != null) {
            return resolveItemFromId(getExtraFieldRawValue(ritualCenterExtraField.key));
        }
        if (ritualCenterMode == RitualCenterMode.INPUT_SLOT && !draftInputs.isEmpty()) {
            RecipeData.MachineSlot s = draftInputs.get(0);
            return s.type == RecipeData.MachineSlotType.ITEM ? s.item : ItemStack.EMPTY;
        }
        return ItemStack.EMPTY;
    }

    private void drawRitualHoverTooltip(GuiGraphics g, int mx, int my) {
        if (RitualRingLayout.hitTestCenter(mx, my, ritualCenterX, ritualCenterY)) {
            ItemStack stack = ritualCenterItemStack();
            if (!stack.isEmpty()) g.renderTooltip(font, stack, mx, my);
            else if (ritualCenterMode != RitualCenterMode.NONE) g.renderTooltip(font, Component.literal(Lang.get("gui.kve.ritual.center")), mx, my);
            return;
        }
        List<RecipeData.MachineSlot> offerings = ritualOfferings();
        syncRitualOfferingRingSlots(offerings);
        int ringHit = RitualRingLayout.hitTestRing(mx, my, ritualRingPositions);
        if (ringHit >= 0) {
            int offeringIdx = offeringAtRingSlot(offerings, ringHit);
            if (offeringIdx >= 0) {
                RecipeData.MachineSlot s = offerings.get(offeringIdx);
                if (s.type == RecipeData.MachineSlotType.ITEM && !s.item.isEmpty()) g.renderTooltip(font, s.item, mx, my);
                else if (s.type == RecipeData.MachineSlotType.TAG) g.renderComponentTooltip(font, tagTooltip(s.tagId, s.tagCount), mx, my);
            }
            return;
        }
        if (RitualRingLayout.hits(mx, my, ritualOutX, ritualOutY, RitualRingLayout.OFFERING_SLOT) && !draftOutputs.isEmpty()) {
            RecipeData.MachineSlot s = draftOutputs.get(0);
            if (s.type == RecipeData.MachineSlotType.ITEM && !s.item.isEmpty()) g.renderTooltip(font, s.item, mx, my);
        }
    }

    private void drawGridLabel(GuiGraphics g, String name, int count, int x, int y) {
        String text = name + " (" + count + "):" + (count == 0 ? "  click en + para agregar" : "");
        KveGuiStyle.drawTextSafe(g, text, x, y, GUI_W - 20, KveGuiStyle.ACCENT_GOLD);
    }

    private void drawLayoutArrow(GuiGraphics g, RecipeTypeDescriptor.Arrow arrow, int sectionTop, boolean compact) {
        int arrowX = slotScreenX(arrow.x + pushShiftFor(arrow.x)), arrowY = slotScreenY(sectionTop, arrow.y);
        if (activeDescriptor != null) {
            if (compact) {
                int arrowRaw = arrow.x + pushShiftFor(arrow.x);
                int leftEdgeRaw = Math.max(rightEdgeBefore(layoutInputDefs, draftInputs.size(), arrowRaw),
                        rightEdgeBefore(layoutOutputDefs, draftOutputs.size(), arrowRaw));
                int gap = arrowRaw - leftEdgeRaw;
                int size = Math.min(28, gap - 4);
                if (size >= 12) {
                    int hx = slotScreenX(leftEdgeRaw) + (gap - size) / 2;
                    int hy = arrowY + 6 - size / 2;
                    MachineStationPreview.render(g, activeDescriptor, hx, hy, size, size);
                    if (activeLayout != null && activeLayout.heatSourceHint) drawHeatGlyph(g, hx + size / 2, hy + size);
                }
            } else {
                // El bloque de la estacion va al costado del contenido (derecha, o izquierda si no entra),
                // nunca encima de la flecha: ahi tapaba slots de entrada y se cortaba contra el borde superior.
                int size = 24;
                int heat = activeLayout != null && activeLayout.heatSourceHint ? 9 : 2;
                int contentLeft = slotScreenX(0), contentRight = slotScreenX(unifiedWidth());
                int boxLeft = ax + 4, boxRight = ax + stationCanvasW() - 4;
                int hx = contentRight + 10;
                if (hx + size > boxRight) hx = contentLeft - 10 - size;
                if (hx >= boxLeft) {
                    int hy = slotScreenY(sectionTop, unifiedHeight() / 2) - size / 2;
                    hy = Math.max(sectionTop + 2, Math.min(hy, sectionTop + STATION_CANVAS_H - size - heat));
                    MachineStationPreview.render(g, activeDescriptor, hx, hy, size, size);
                    if (heat > 2) drawHeatGlyph(g, hx + size / 2, hy + size);
                }
            }
        }
        drawArrowBox(g, arrowX, arrowY, arrow.direction);
    }

    private void drawSectionGapArrow(GuiGraphics g) {
        int gapTop = inputsGridY + sectionHeight(layoutInputDefs);
        int gapBottom = outputsLabelY;
        if (gapBottom - gapTop < 10) return;
        RecipeTypeDescriptor.Arrow custom = activeLayout.arrow;
        int cx, cy;
        String direction;
        if (custom != null) {
            cx = ax + custom.x;
            cy = gapTop + custom.y;
            direction = custom.direction;
        } else {
            cx = ax + Math.max(0, (GUI_W - 20) / 2 - 6);
            cy = gapTop + Math.max(0, (gapBottom - gapTop - 18) / 2);
            direction = "down";
        }
        drawStationHero(g, gapTop, gapBottom, cx);
        drawArrowBox(g, cx, cy, direction);
    }

    private void drawStationHero(GuiGraphics g, int gapTop, int gapBottom, int arrowX) {
        if (activeDescriptor == null) return;
        int gapH = gapBottom - gapTop;
        int size = Math.min(gapH, 40);
        if (size < 16) return;
        int hx = arrowX - 8 - size;
        if (hx < ax) hx = arrowX + 12 + 8;
        int hy = gapTop + Math.max(0, (gapH - size) / 2);
        MachineStationPreview.render(g, activeDescriptor, hx, hy, size, size);
        if (activeLayout != null && activeLayout.heatSourceHint) drawHeatGlyph(g, hx + size / 2, hy + size);
    }

    private void drawHeatGlyph(GuiGraphics g, int cx, int bottomY) {
        int w = 8, h = 7;
        int x0 = cx - w / 2, y0 = bottomY;
        g.fill(x0 + 1, y0, x0 + w - 1, y0 + 2, 0xFFFFA000);
        g.fill(x0, y0 + 2, x0 + w, y0 + h, 0xFFFF5A1F);
    }

    private void drawArrowBox(GuiGraphics g, int cx, int cy, String direction) {
        boolean down = "down".equalsIgnoreCase(direction);
        int w = down ? 12 : 18, h = down ? 18 : 12;
        g.fill(cx, cy, cx + w, cy + h, 0xFF23283A);
        if (down) {
            g.fill(cx + 2, cy + 2, cx + w - 2, cy + 8, KveGuiStyle.th(0xFF8899BB));
            g.fill(cx + 4, cy + 8, cx + w - 4, cy + 14, KveGuiStyle.th(0xFF8899BB));
        } else {
            g.fill(cx + 2, cy + 2, cx + 8, cy + h - 2, KveGuiStyle.th(0xFF8899BB));
            g.fill(cx + 8, cy + 4, cx + w - 2, cy + h - 4, KveGuiStyle.th(0xFF8899BB));
        }
    }

    private void drawSlotGrid(GuiGraphics g, List<RecipeData.MachineSlot> slots, ScrollBar bar, int gridTop, int mx, int my) {
        int scroll = bar != null ? bar.getScroll() : 0;
        int startIdx = scroll * gridCols;
        int total = slots.size() + 1;
        for (int i = 0; i < GRID_ROWS_VISIBLE * gridCols; i++) {
            int idx = startIdx + i;
            if (idx >= total) break;
            int col = i % gridCols, row = i / gridCols;
            int sx = ax + col * SLOT, sy = gridTop + row * SLOT;
            boolean hov = mx >= sx && mx < sx + SLOT && my >= sy && my < sy + SLOT;
            if (idx == slots.size()) {
                drawAddSlot(g, sx, sy, hov, 0);
            } else {
                drawFilledSlot(g, slots.get(idx), sx, sy, hov, 0);
            }
        }
        if (bar != null) bar.render(g);
    }

    private boolean stationSlotOutOfView(int sx, int sy, int w, int h, int boxX0, int boxX1, int boxY0, int boxY1) {
        return isStationCanvas() && (sx + w < boxX0 || sx > boxX1 || sy + h < boxY0 || sy > boxY1);
    }

    private void drawCustomSlotGrid(GuiGraphics g, List<RecipeData.MachineSlot> slots, List<RecipeTypeDescriptor.SlotDef> defs, int sectionTop, int mx, int my) {
        RecipeTypeDescriptor.SlotDef repeatableDef = firstRepeatableDef(defs);
        int fixedCount = fixedSlotCount(defs, repeatableDef);
        int boxX0 = ax, boxX1 = ax + stationCanvasW();
        int boxY0 = sectionTop, boxY1 = sectionTop + STATION_CANVAS_H;

        int i = 0;
        for (RecipeTypeDescriptor.SlotDef d : defs) {
            if (d == repeatableDef) continue;
            int sx = slotScreenX(d.x + pushShiftFor(d.x)), sy = slotScreenY(sectionTop, d.y);
            int sh = slotHeight(d);
            if (stationSlotOutOfView(sx, sy, SLOT, sh, boxX0, boxX1, boxY0, boxY1)) { i++; continue; }
            boolean hov = mx >= sx && mx < sx + SLOT && my >= sy && my < sy + sh;
            if (i < slots.size() && !isPlaceholder(slots.get(i))) {
                drawFilledSlot(g, slots.get(i), sx, sy, hov, tankHOf(d));
            } else {
                drawAddSlot(g, sx, sy, hov, tankHOf(d), d.required);
            }
            i++;
        }

        if (repeatableDef != null) {
            if (slots.size() < fixedCount) {
                int sx = slotScreenX(repeatableDef.x), sy = slotScreenY(sectionTop, repeatableDef.y);
                boolean hov = mx >= sx && mx < sx + SLOT && my >= sy && my < sy + SLOT;
                drawAddSlot(g, sx, sy, hov, tankHOf(repeatableDef), repeatableDef.required && slots.size() <= fixedCount);
            } else {
                int extra = slots.size() - fixedCount;
                for (int k = 0; k <= extra; k++) {
                    int sx = slotScreenX(repeatableDef.x + k * SLOT), sy = slotScreenY(sectionTop, repeatableDef.y);
                    if (stationSlotOutOfView(sx, sy, SLOT, SLOT, boxX0, boxX1, boxY0, boxY1)) continue;
                    boolean hov = mx >= sx && mx < sx + SLOT && my >= sy && my < sy + SLOT;
                    if (k < extra) drawFilledSlot(g, slots.get(fixedCount + k), sx, sy, hov, 0);
                    else drawAddSlot(g, sx, sy, hov, 0);
                }
            }
        } else if (repeatableDef == null && slots.size() > fixedCount) {
            RecipeTypeDescriptor.SlotDef last = defs.isEmpty() ? null : defs.get(defs.size() - 1);
            int rawBaseX = last != null ? last.x + SLOT : 0;
            int rawBaseY = last != null ? last.y : 0;
            for (int k = fixedCount; k < slots.size(); k++) {
                int sx = slotScreenX(rawBaseX + (k - fixedCount) * SLOT), sy = slotScreenY(sectionTop, rawBaseY);
                if (stationSlotOutOfView(sx, sy, SLOT, SLOT, boxX0, boxX1, boxY0, boxY1)) continue;
                boolean hov = mx >= sx && mx < sx + SLOT && my >= sy && my < sy + SLOT;
                drawFilledSlot(g, slots.get(k), sx, sy, hov, 0);
            }
        }
    }

    private void drawAddSlot(GuiGraphics g, int x, int y, boolean hov, int tankH) {
        drawAddSlot(g, x, y, hov, tankH, false);
    }

    private void drawAddSlot(GuiGraphics g, int x, int y, boolean hov, int tankH, boolean required) {
        int w = tankH > 0 ? TANK_W : SLOT, h = tankH > 0 ? tankH : SLOT;
        int accent = required && !hov ? 0xFFFFAA55 : 0xFF66DD99;
        KveGuiStyle.drawSlotGlowRect(g, x, y, w, h, hov || required, accent);
        int col = hov ? KveGuiStyle.TEXT_OK : required ? 0xFFFFAA55 : KveGuiStyle.TEXT_MUTED;
        KveGuiStyle.drawTextSafe(g, "+", x + w / 2 - 2, y + h / 2 - 4, 12, col);
    }

    private void drawLockedSlot(GuiGraphics g, int x, int y, int tankH) {
        int w = tankH > 0 ? TANK_W : SLOT, h = tankH > 0 ? tankH : SLOT;
        KveGuiStyle.drawSlotGlowRect(g, x, y, w, h, false, 0xFF3A4250);
    }

    private static int tankHOf(RecipeTypeDescriptor.SlotDef d) { return d != null && d.tank ? tankHeight(d.h) : 0; }

    private void drawFilledSlot(GuiGraphics g, RecipeData.MachineSlot slot, int x, int y, boolean hov, int tankH) {
        int glow = switch (slot.type) {
            case ITEM -> KveGuiStyle.th(0xFF60A8F0);
            case FLUID -> 0xFF4FC3E0;
            case TAG -> 0xFFB07FE0;
        };
        if (tankH > 0 && slot.type == RecipeData.MachineSlotType.FLUID) {
            drawFluidTank(g, slot.fluidId, slot.fluidAmount, x, y, tankH, hov, glow);
            return;
        }
        KveGuiStyle.drawSlotGlow(g, x, y, SLOT, hov, glow);
        int ix = x + 1, iy = y + 1;
        switch (slot.type) {
            case ITEM -> {
                // Render protegido (ver SafeItemRender): un item de un mod roto se marca, no crashea.
                if (SafeItemRender.render(g, slot.item, ix, iy)) SafeItemRender.decorations(g, font, slot.item, ix, iy);
                if (slot.item != null && !slot.item.isEmpty()) {
                    int mx = (int) KveGuiStyle.mouseX(), my = (int) KveGuiStyle.mouseY();
                    RecipeSlotRenderer.drawNbtHotspot(g, font, x, y, SLOT, slot.item, mx, my);
                    if (RecipeSlotRenderer.hitsNbtBadge(x, y, SLOT, mx, my)) nbtBadgeHoverSlot = slot;
                }
            }
            case FLUID -> {
                renderFluidIcon(g, slot.fluidId, ix, iy);
                drawAmountBadge(g, x, y, SLOT, formatAmount(slot.fluidAmount));
            }
            case TAG -> {
                var members = slot.tagId == null ? List.<net.minecraft.world.item.Item>of()
                        : com.kve.common.recipe.TagIngredient.members(slot.tagId);
                if (members.isEmpty()) {
                    // Rojo: el tag no tiene items (roto o vacio) - ver el tooltip.
                    g.fill(ix, iy, ix + 16, iy + 16, 0xFF3A1820);
                    KveGuiStyle.drawTextSafe(g, "#", ix + 5, iy + 4, 12, 0xFFFF6B6B);
                } else {
                    int i = (int) ((System.currentTimeMillis() / 1000) % members.size());
                    try {
                        SafeItemRender.render(g, new ItemStack(members.get(i)), ix, iy);
                    } catch (Throwable ignored) {
                        g.fill(ix, iy, ix + 16, iy + 16, 0xFF2A1C40);
                    }
                }
                g.pose().pushPose();
                g.pose().translate(0, 0, 300);
                if (!members.isEmpty()) {
                    g.fill(x + 1, y + SLOT - 8, x + 7, y + SLOT - 1, 0xCC000000);
                    g.drawString(font, "#", x + 1, y + SLOT - 9, 0xFFCFA8FF, false);
                }
                if (slot.tagCount > 1) drawAmountBadge(g, x, y, SLOT, formatAmount(slot.tagCount));
                g.pose().popPose();
            }
        }
        drawChanceBadge(g, slot, x, y);
    }

    private void drawChanceBadge(GuiGraphics g, RecipeData.MachineSlot slot, int x, int y) {
        if (slot.chance >= 0.999) return;
        String text = Math.round(Math.max(0.0, slot.chance) * 100) + "%";
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        int w = Math.round(font.width(text) * 0.6f) + 2;
        g.fill(x + 1, y + 1, x + 1 + w, y + 7, 0xC0101018);
        KveGuiStyle.drawTextSafeScaled(g, text, x + 2, y + 1, SLOT, 0xFFFFD866, 0.6f);
        g.pose().popPose();
    }

    private void drawFluidTank(GuiGraphics g, String fluidId, int amount, int x, int y, int tankH, boolean hov, int glow) {
        KveGuiStyle.drawSlotGlowRect(g, x, y, TANK_W, tankH, hov, glow);
        int ix = x + 1, iy = y + 1;
        renderFluidIcon(g, fluidId, ix, iy);
        int fillTop = iy + 17, fillBottom = y + tankH - 1;
        if (fillBottom > fillTop) {
            g.fill(ix, fillTop, ix + 16, fillBottom, tankTintARGB(fluidId, 0xA0));
            for (int q = 1; q < 4; q++) {
                int ty = fillBottom - (fillBottom - fillTop) * q / 4;
                g.fill(ix + 11, ty, ix + 16, ty + 1, 0x80000000);
            }
        }
        drawAmountBadge(g, x, y, tankH, formatAmount(amount));
    }

    private void drawAmountBadge(GuiGraphics g, int x, int y, int slotH, String text) {
        if (text.isEmpty()) return;
        int tx = x + SLOT - font.width(text) - 2;
        int ty = y + slotH - 9;
        KveGuiStyle.drawTextSafe(g, text, tx + 1, ty, SLOT, 0xFF000000);
        KveGuiStyle.drawTextSafe(g, text, tx, ty - 1, SLOT, 0xFFFFFFFF);
    }

    private String formatAmount(int amount) {
        if (amount >= 1000) return (amount / 1000) + "k";
        return String.valueOf(amount);
    }

    private void renderFluidIcon(GuiGraphics g, String fluidId, int x, int y) {
        Fluid fluid = resolveFluid(fluidId);
        if (fluid == Fluids.EMPTY) {
            if (renderChemicalIcon(g, fluidId, x, y)) return;
            g.fill(x, y, x + 16, y + 16, 0xFF33445A);
            KveGuiStyle.drawTextSafe(g, fluidId != null && fluidId.startsWith("#") ? "#" : "?", x + 5, y + 4, 12, 0xFFAABBCC);
            return;
        }
        try {
            IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid.getFluidType());
            TextureAtlasSprite sprite = minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ext.getStillTexture());
            int tint = ext.getTintColor();
            float a = ((tint >>> 24) & 0xFF) / 255f; if (a <= 0f) a = 1f;
            float r = ((tint >> 16) & 0xFF) / 255f;
            float gr = ((tint >> 8) & 0xFF) / 255f;
            float b = (tint & 0xFF) / 255f;
            g.setColor(r, gr, b, a);
            g.blit(x, y, 0, 16, 16, sprite);
            g.setColor(1f, 1f, 1f, 1f);
        } catch (Throwable t) {
            g.fill(x, y, x + 16, y + 16, 0xFF335544);
        }
    }

    private Fluid resolveFluid(String fluidId) {
        if (fluidId != null && !fluidId.isBlank()) {
            ResourceLocation rl = ResourceLocation.tryParse(fluidId);
            if (rl != null) {
                Fluid f = BuiltInRegistries.FLUID.get(rl);
                if (f != null) return f;
            }
        }
        return Fluids.EMPTY;
    }

    private boolean renderChemicalIcon(GuiGraphics g, String id, int x, int y) {
        if (id == null || id.isBlank() || id.startsWith("#")) return false;
        String kind = com.kve.common.recipetype.ChemicalRegistryHelper.kindOf(id, null);
        if (kind == null) return false;
        ResourceLocation icon = com.kve.common.recipetype.ChemicalRegistryHelper.icon(kind, id);
        int tint = com.kve.common.recipetype.ChemicalRegistryHelper.tint(kind, id);
        try {
            if (icon != null) {
                TextureAtlasSprite sprite = minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(icon);
                float r = tint < 0 ? 1f : ((tint >> 16) & 0xFF) / 255f;
                float gr = tint < 0 ? 1f : ((tint >> 8) & 0xFF) / 255f;
                float b = tint < 0 ? 1f : (tint & 0xFF) / 255f;
                g.setColor(r, gr, b, 1f);
                g.blit(x, y, 0, 16, 16, sprite);
                g.setColor(1f, 1f, 1f, 1f);
            } else {
                g.fill(x, y, x + 16, y + 16, 0xFF000000 | (tint < 0 ? 0x557788 : tint));
            }
        } catch (Throwable t) {
            g.setColor(1f, 1f, 1f, 1f);
            g.fill(x, y, x + 16, y + 16, 0xFF000000 | (tint < 0 ? 0x557788 : tint));
        }
        return true;
    }

    private int tankTintARGB(String id, int alpha) {
        Fluid fluid = resolveFluid(id);
        if (fluid == Fluids.EMPTY && id != null && !id.isBlank() && !id.startsWith("#")) {
            String kind = com.kve.common.recipetype.ChemicalRegistryHelper.kindOf(id, null);
            int tint = kind == null ? -1 : com.kve.common.recipetype.ChemicalRegistryHelper.tint(kind, id);
            if (tint >= 0) return (alpha << 24) | tint;
        }
        return fluidTintARGB(fluid, alpha);
    }

    private int fluidTintARGB(Fluid fluid, int alpha) {
        if (fluid == Fluids.EMPTY) return (alpha << 24) | 0x33445A;
        try {
            int tint = IClientFluidTypeExtensions.of(fluid.getFluidType()).getTintColor();
            return (alpha << 24) | (tint & 0xFFFFFF);
        } catch (Throwable t) {
            return (alpha << 24) | 0x335544;
        }
    }

    private void drawHoverTooltip(GuiGraphics g, List<RecipeData.MachineSlot> slots, int gridTop, int mx, int my) {
        int idx;
        RecipeTypeDescriptor.SlotDef hoverDef = null;
        if (activeLayout != null) {
            CustomSlotHit hit = customSlotIndexAt(slots, slots == draftInputs ? layoutInputDefs : layoutOutputDefs, gridTop, mx, my);
            idx = hit != null ? hit.listIndex : -1;
            hoverDef = hit != null ? hit.def : null;
            if (hit != null && hit.def != null && (hit.listIndex >= slots.size() || isPlaceholder(slots.get(hit.listIndex)))) {
                List<Component> empty = new ArrayList<>();
                String label = hit.def.label != null && !hit.def.label.isBlank() ? hit.def.label
                        : (hit.def.isOutput() ? Lang.get("gui.kve.machine.slot.output") : Lang.get("gui.kve.machine.slot.input"));
                empty.add(Component.literal(label).withStyle(hit.def.isOutput() ? ChatFormatting.GOLD : ChatFormatting.AQUA));
                empty.add(Component.literal(slotKindText(hit.def)).withStyle(ChatFormatting.GRAY));
                if (hit.def.required) empty.add(Component.literal(Lang.get("gui.kve.machine.required")).withStyle(ChatFormatting.RED));
                empty.add(Component.literal(Lang.get("gui.kve.machine.click_pick")).withStyle(ChatFormatting.DARK_GRAY));
                g.renderComponentTooltip(font, empty, mx, my);
                return;
            }
        } else {
            ScrollBar bar = slots == draftInputs ? inputScrollBar : outputScrollBar;
            int scroll = bar != null ? bar.getScroll() : 0;
            if (my < gridTop || my >= gridTop + GRID_ROWS_VISIBLE * SLOT) return;
            int col = (mx - ax) / SLOT, row = (my - gridTop) / SLOT;
            if (col < 0 || col >= gridCols || mx >= ax + gridCols * SLOT) return;
            idx = scroll * gridCols + row * gridCols + col;
        }
        if (idx < 0 || idx >= slots.size()) return;
        RecipeData.MachineSlot slot = slots.get(idx);
        boolean isOutputSlot = slots == draftOutputs;
        List<Component> lines = new ArrayList<>();
        switch (slot.type) {
            case ITEM -> {
                if (!slot.item.isEmpty()) {
                    if (!isOutputSlot || slot.chance >= 1.0) { g.renderTooltip(font, slot.item, mx, my); return; }
                    lines.addAll(getTooltipFromItem(minecraft, slot.item));
                }
                else lines.add(Component.literal("(sin elegir)"));
            }
            case FLUID -> {
                if (hoverDef != null && hoverDef.label != null && !hoverDef.label.isBlank()) {
                    lines.add(Component.literal(hoverDef.label).withStyle(isOutputSlot ? ChatFormatting.GOLD : ChatFormatting.AQUA));
                }
                String id = slot.fluidId == null || slot.fluidId.isBlank() ? "(sin elegir)" : slot.fluidId;
                lines.add(Component.literal(id));
                lines.add(Component.literal(slot.fluidAmount + " mB"));
                if (hoverDef != null && hoverDef.isChemical()) {
                    lines.add(Component.literal(slotKindText(hoverDef)).withStyle(ChatFormatting.DARK_GRAY));
                }
            }
            case TAG -> {
                String id = slot.tagId == null || slot.tagId.isBlank() ? "(sin elegir)" : "#" + slot.tagId;
                lines.add(Component.literal(id));
                lines.add(Component.literal("x" + slot.tagCount));
            }
        }
        if (isOutputSlot) {
            lines.add(Component.literal("p " + String.format(java.util.Locale.ROOT, "%.2f", slot.chance)).withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.empty());
        lines.add(KveGuiStyle.slotHint(KveGuiStyle.HINT_CLICK_CHANGE, KveGuiStyle.HINT_RIGHT_CLEAR, KveGuiStyle.HINT_SCROLL_QTY));
        if (isOutputSlot) {
            lines.add(Component.literal(Lang.get("gui.kve.slot.hint.ctrl_scroll_chance")).withStyle(ChatFormatting.DARK_GRAY));
        }
        g.renderComponentTooltip(font, lines, mx, my);
    }

    private int slotIndexAt(List<RecipeData.MachineSlot> slots, ScrollBar bar, int gridTop, double mx, double my) {
        if (my < gridTop || my >= gridTop + GRID_ROWS_VISIBLE * SLOT) return -1;
        if (mx < ax || mx >= ax + gridCols * SLOT) return -1;
        int col = (int) (mx - ax) / SLOT, row = (int) (my - gridTop) / SLOT;
        int scroll = bar != null ? bar.getScroll() : 0;
        int idx = scroll * gridCols + row * gridCols + col;
        return idx <= slots.size() ? idx : -1;
    }

    private static final class CustomSlotHit {
        final int listIndex;
        final RecipeTypeDescriptor.SlotDef def;
        CustomSlotHit(int listIndex, RecipeTypeDescriptor.SlotDef def) { this.listIndex = listIndex; this.def = def; }
    }

    private CustomSlotHit customSlotIndexAt(List<RecipeData.MachineSlot> slots, List<RecipeTypeDescriptor.SlotDef> defs, int sectionTop, double mx, double my) {
        RecipeTypeDescriptor.SlotDef repeatableDef = firstRepeatableDef(defs);
        int fixedCount = fixedSlotCount(defs, repeatableDef);

        int i = 0;
        for (RecipeTypeDescriptor.SlotDef d : defs) {
            if (d == repeatableDef) continue;
            int sx = slotScreenX(d.x + pushShiftFor(d.x)), sy = slotScreenY(sectionTop, d.y);
            if (mx >= sx && mx < sx + SLOT && my >= sy && my < sy + slotHeight(d)) return new CustomSlotHit(i, d);
            i++;
        }

        if (repeatableDef != null && slots.size() < fixedCount) {
            int sx = slotScreenX(repeatableDef.x), sy = slotScreenY(sectionTop, repeatableDef.y);
            if (mx >= sx && mx < sx + SLOT && my >= sy && my < sy + SLOT) return new CustomSlotHit(fixedCount, repeatableDef);
        } else if (repeatableDef != null) {
            int extra = slots.size() - fixedCount;
            for (int k = 0; k <= extra; k++) {
                int sx = slotScreenX(repeatableDef.x + k * SLOT), sy = slotScreenY(sectionTop, repeatableDef.y);
                if (mx >= sx && mx < sx + SLOT && my >= sy && my < sy + SLOT) return new CustomSlotHit(fixedCount + k, repeatableDef);
            }
        } else if (repeatableDef == null && slots.size() > fixedCount) {
            RecipeTypeDescriptor.SlotDef last = defs.isEmpty() ? null : defs.get(defs.size() - 1);
            int rawBaseX = last != null ? last.x + SLOT : 0;
            int rawBaseY = last != null ? last.y : 0;
            for (int k = fixedCount; k < slots.size(); k++) {
                int sx = slotScreenX(rawBaseX + (k - fixedCount) * SLOT), sy = slotScreenY(sectionTop, rawBaseY);
                if (mx >= sx && mx < sx + SLOT && my >= sy && my < sy + SLOT) return new CustomSlotHit(k, last);
            }
        }
        return null;
    }

    private boolean handleCustomSlotClick(List<RecipeData.MachineSlot> slots, boolean input, CustomSlotHit hit, int btn) {
        boolean empty = hit.listIndex >= slots.size() || isPlaceholder(slots.get(hit.listIndex));
        if (empty) {
            if (btn == 0) {
                RecipeData.MachineSlotType type = hit.def != null ? mapSlotType(hit.def.type) : (input ? pendingInputType : pendingOutputType);
                String chem = hit.def != null && hit.def.isChemical() && type == RecipeData.MachineSlotType.FLUID ? hit.def.chemical : "";
                int index = hit.listIndex;
                pickNewSlot(type, chem, input, s -> putSlotAt(slots, index, s));
            }
            return true;
        }
        if (btn == 1) {
            List<RecipeTypeDescriptor.SlotDef> defs = input ? layoutInputDefs : layoutOutputDefs;
            int fixedCount = fixedSlotCount(defs, firstRepeatableDef(defs));
            if (hit.listIndex < fixedCount) slots.set(hit.listIndex, new RecipeData.MachineSlot());
            else slots.remove(hit.listIndex);
            trimTrailingPlaceholders(slots);
            refresh();
            return true;
        }
        if (btn == 0) {
            RecipeData.MachineSlot target = slots.get(hit.listIndex);
            if (target.type == RecipeData.MachineSlotType.FLUID && hit.def != null && hit.def.isChemical()) {
                minecraft.setScreen(new FluidPickerScreen(pickerReturn(), id -> {
                    target.fluidId = id;
                    refresh();
                }, hit.def.chemical));
            } else {
                replaceSlot(target);
            }
        }
        return true;
    }

    private static boolean isPlaceholder(RecipeData.MachineSlot s) {
        return switch (s.type) {
            case ITEM -> s.item == null || s.item.isEmpty();
            case FLUID -> s.fluidId == null || s.fluidId.isBlank();
            case TAG -> s.tagId == null || s.tagId.isBlank();
        };
    }

    private static void putSlotAt(List<RecipeData.MachineSlot> list, int index, RecipeData.MachineSlot s) {
        while (list.size() < index) list.add(new RecipeData.MachineSlot());
        if (index < list.size()) list.set(index, s);
        else list.add(s);
    }

    private static void trimTrailingPlaceholders(List<RecipeData.MachineSlot> list) {
        while (!list.isEmpty() && isPlaceholder(list.get(list.size() - 1))) list.remove(list.size() - 1);
    }

    /**
     * Opens the picker for a brand-new slot value of {@code type} and hands
     * the built slot to {@code onPicked}. Mejora compat: con
     * {@code chemicalSpec} no vacio (slot de gas/slurry/pigmento/infusion de
     * Mekanism), el selector abre en modo quimico - lista el registro real
     * de Mekanism, no los fluidos de Forge (que nunca incluian un gas) - y la
     * cantidad inicial es 1, la escala de las recetas de gas reales del jar
     * (ej. purifying: 1 de oxigeno), no los 1000 mB de un cubo.
     */
    private void pickNewSlot(RecipeData.MachineSlotType type, String chemicalSpec, boolean allowTag,
                             java.util.function.Consumer<RecipeData.MachineSlot> onPicked) {
        switch (type) {
            case ITEM -> {
                ItemPickerScreen picker = new ItemPickerScreen(pickerReturn(), stack -> {
                    RecipeData.MachineSlot s = new RecipeData.MachineSlot();
                    fillFromPick(s, stack);
                    onPicked.accept(s);
                    refresh();
                }, false);
                minecraft.setScreen(allowTag ? picker.withTagOption() : picker);
            }
            case FLUID -> minecraft.setScreen(new FluidPickerScreen(pickerReturn(), id -> {
                RecipeData.MachineSlot s = new RecipeData.MachineSlot();
                s.type = RecipeData.MachineSlotType.FLUID;
                s.fluidId = id;
                if (chemicalSpec != null && !chemicalSpec.isBlank()) s.fluidAmount = 1;
                onPicked.accept(s);
                refresh();
            }, chemicalSpec == null ? "" : chemicalSpec));
            case TAG -> minecraft.setScreen(new TagPresetPickerScreen(pickerReturn(), "Tags", tagOptions(), id -> {
                RecipeData.MachineSlot s = new RecipeData.MachineSlot();
                s.type = RecipeData.MachineSlotType.TAG;
                s.tagId = id;
                onPicked.accept(s);
                refresh();
            }));
        }
    }

    public boolean consumeAddonWarningClick(double mx, double my) {
        if (pendingAddonWarning == null || pendingAddonWarningBtn == null) return false;
        int[] r = pendingAddonWarningBtn;
        if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
            dismissAddonWarning();
            return true;
        }
        return false;
    }

    /** Closes the floating addon notice (button click or Enter). */
    public boolean dismissAddonWarning() {
        if (pendingAddonWarning == null) return false;
        pendingAddonWarning = null;
        pendingAddonWarningBtn = null;
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (consumeAddonWarningClick(mx, my)) return true;
        if (inputScrollBar != null && inputScrollBar.mouseClicked(mx, my, btn)) return true;
        if (outputScrollBar != null && outputScrollBar.mouseClicked(mx, my, btn)) return true;

        if (ritualMode) {
            if (RitualRingLayout.hitTestCenter(mx, my, ritualCenterX, ritualCenterY)) return handleRitualCenterClick(btn);
            int ringHit = RitualRingLayout.hitTestRing(mx, my, ritualRingPositions);
            if (ringHit >= 0) return handleRitualRingClick(ringHit, btn);
            if (RitualRingLayout.hits(mx, my, ritualOutX, ritualOutY, RitualRingLayout.OFFERING_SLOT)) return handleRitualOutputClick(btn);
            return super.mouseClicked(mx, my, btn);
        }

        if (activeLayout != null) {
            CustomSlotHit inHit = customSlotIndexAt(draftInputs, layoutInputDefs, inputsGridY, mx, my);
            if (inHit != null) return handleCustomSlotClick(draftInputs, true, inHit, btn);
            CustomSlotHit outHit = customSlotIndexAt(draftOutputs, layoutOutputDefs, outputsGridY, mx, my);
            if (outHit != null) return handleCustomSlotClick(draftOutputs, false, outHit, btn);
            RecipeTypeDescriptor.FieldTank tankHit = fieldTankAt(mx, my);
            if (tankHit != null) return handleFieldTankClick(tankHit, btn);
            if (hitsContainerFieldSlot((int) mx, (int) my)) return handleContainerFieldSlotClick(btn);
            if (isStationCanvas() && mx >= ax && mx < ax + stationCanvasW() && my >= inputsGridY && my < inputsGridY + STATION_CANVAS_H) {
                return stationCanvas.handleEmptyClick(btn, mx, my);
            }
            return super.mouseClicked(mx, my, btn);
        }

        int inIdx = slotIndexAt(draftInputs, inputScrollBar, inputsGridY, mx, my);
        if (inIdx >= 0) return handleSlotClick(draftInputs, true, inIdx, btn);

        int outIdx = slotIndexAt(draftOutputs, outputScrollBar, outputsGridY, mx, my);
        if (outIdx >= 0) return handleSlotClick(draftOutputs, false, outIdx, btn);

        return super.mouseClicked(mx, my, btn);
    }

    private boolean handleRitualCenterClick(int btn) {
        switch (ritualCenterMode) {
            case EXTRA_FIELD -> {
                if (btn == 1) { setExtraFieldStringValue(ritualCenterExtraField.key, ""); refresh(); return true; }
                if (btn == 0) {
                    minecraft.setScreen(new ItemPickerScreen(pickerReturn(), stack -> {
                        setExtraFieldStringValue(ritualCenterExtraField.key, itemId(stack));
                        refresh();
                    }, false));
                }
                return true;
            }
            case INPUT_SLOT -> {
                if (draftInputs.isEmpty()) {
                    if (btn == 0) {
                        minecraft.setScreen(new ItemPickerScreen(pickerReturn(), stack -> {
                            RecipeData.MachineSlot s = new RecipeData.MachineSlot();
                            fillFromPick(s, stack);
                            draftInputs.add(0, s);
                            refresh();
                        }, false).withTagOption());
                    }
                    return true;
                }
                if (btn == 1) { draftInputs.remove(0); refresh(); return true; }
                if (btn == 0) replaceSlot(draftInputs.get(0));
                return true;
            }
            default -> { return true; }
        }
    }

    private boolean handleRitualRingClick(int idx, int btn) {
        List<RecipeData.MachineSlot> offerings = ritualOfferings();
        syncRitualOfferingRingSlots(offerings);
        int offeringIdx = offeringAtRingSlot(offerings, idx);
        if (offeringIdx < 0) {
            if (btn == 0 && offerings.size() < ritualMaxOfferings) {
                minecraft.setScreen(new ItemPickerScreen(pickerReturn(), stack -> {
                    RecipeData.MachineSlot s = new RecipeData.MachineSlot();
                    fillFromPick(s, stack);
                    s.ringSlot = idx;
                    offerings.add(s);
                    ritualOfferingRingSlot.add(idx);
                    refresh();
                }, false).withTagOption());
            }
            return true;
        }
        if (btn == 1) {
            offerings.remove(offeringIdx);
            ritualOfferingRingSlot.remove(offeringIdx);
            refresh();
            return true;
        }
        if (btn == 0) replaceSlot(offerings.get(offeringIdx));
        return true;
    }

    private void syncRitualOfferingRingSlots(List<RecipeData.MachineSlot> offerings) {
        if (ritualOfferingRingSlot.size() > offerings.size()) {
            ritualOfferingRingSlot.subList(offerings.size(), ritualOfferingRingSlot.size()).clear();
        }
        boolean freshLoad = ritualOfferingRingSlot.isEmpty();
        RecipeTypeDescriptor.RitualViewSettings rv = activeDescriptor != null ? activeDescriptor.ritualView : null;
        boolean fixedPositions = rv != null && rv.hasPositions();
        int total = offerings.size();
        while (ritualOfferingRingSlot.size() < offerings.size()) {
            int i = ritualOfferingRingSlot.size();
            int stored = offerings.get(i).ringSlot;
            int assigned;
            if (stored >= 0 && stored < ritualMaxOfferings && !ritualOfferingRingSlot.contains(stored)) {
                assigned = stored;
            } else {
                int next = freshLoad && !fixedPositions ? RitualRingLayout.spreadSlot(i, total, ritualMaxOfferings) : 0;
                for (int tries = 0; tries < ritualMaxOfferings && ritualOfferingRingSlot.contains(next); tries++) {
                    next = (next + 1) % ritualMaxOfferings;
                }
                if (ritualOfferingRingSlot.contains(next)) {
                    next = 0;
                    while (ritualOfferingRingSlot.contains(next)) next++;
                }
                assigned = next;
            }
            ritualOfferingRingSlot.add(assigned);
            offerings.get(i).ringSlot = assigned;
        }
    }

    private int offeringAtRingSlot(List<RecipeData.MachineSlot> offerings, int ringSlot) {
        for (int i = 0; i < ritualOfferingRingSlot.size() && i < offerings.size(); i++) {
            if (ritualOfferingRingSlot.get(i) == ringSlot) return i;
        }
        return -1;
    }

    private boolean handleRitualOutputClick(int btn) {
        if (draftOutputs.isEmpty()) {
            if (btn == 0) {
                minecraft.setScreen(new ItemPickerScreen(pickerReturn(), stack -> {
                    RecipeData.MachineSlot s = new RecipeData.MachineSlot();
                    s.type = RecipeData.MachineSlotType.ITEM;
                    s.item = stack.copy();
                    draftOutputs.add(s);
                    refresh();
                }, false));
            }
            return true;
        }
        if (btn == 1) { draftOutputs.remove(0); refresh(); return true; }
        if (btn == 0) replaceSlot(draftOutputs.get(0));
        return true;
    }

    /** Item o tag, segun lo que devolvio el selector: con la opcion Tag el stack trae la marca de tag. */
    private static void fillFromPick(RecipeData.MachineSlot s, ItemStack stack) {
        String tag = com.kve.common.recipe.TagIngredient.tagOf(stack);
        if (tag != null) {
            s.type = RecipeData.MachineSlotType.TAG;
            s.tagId = tag;
            s.item = ItemStack.EMPTY;
        } else {
            s.type = RecipeData.MachineSlotType.ITEM;
            s.item = stack.copy();
        }
    }

    /** Las salidas no aceptan tags; todo lo demas (entradas, ofrendas, centro del ritual) si. */
    private boolean isInputSlot(RecipeData.MachineSlot slot) {
        for (RecipeData.MachineSlot o : draftOutputs) if (o == slot) return false;
        return true;
    }

    private static String itemId(ItemStack stack) {
        var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }

    private ItemStack resolveItemFromId(String id) {
        if (id == null || id.isBlank()) return ItemStack.EMPTY;
        if (id.startsWith("#")) {
            var members = com.kve.common.recipe.TagIngredient.members(id);
            return members.isEmpty() ? ItemStack.EMPTY
                    : com.kve.common.recipe.TagIngredient.of(id, members.get((int) ((System.currentTimeMillis() / 1000) % members.size())));
        }
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return ItemStack.EMPTY;
        var item = BuiltInRegistries.ITEM.get(rl);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    /**
     * Label del boton de un {@link #ritualOptionFields} - "key: X"
     * truncado al ancho del boton (mismo helper que el resto de botones
     * "Tipo:" de esta pantalla, ver {@code KveGuiStyle.truncate}).
     * Prefijo generico ({@code field.key}, no un texto fijo tipo "Tipo:")
     * porque ahora puede haber mas de un boton en pantalla a la vez (ej.
     * Goety: {@code ritualType} Y {@code craftType}) y cada uno necesita
     * distinguirse del resto de un vistazo. Muestra el valor CRUDO actual
     * (puede ser un id custom no listado en {@code options}, escrito a
     * mano en el cuadro de Extra JSON - ver javadoc de {@link #ritualOptionFields}).
     */
    private String ritualOptionFieldButtonLabel(RecipeTypeDescriptor.ExtraField field) {
        String raw = getExtraFieldRawValue(field.key);
        String shown = raw.isBlank() ? field.defaultValue : raw;
        return KveGuiStyle.truncate(font, field.key + ": " + shown, 116);
    }

    private void cycleRitualOptionField(RecipeTypeDescriptor.ExtraField field, int dir) {
        if (field == null || !field.hasOptions()) return;
        var opts = field.options;
        String current = getExtraFieldRawValue(field.key);
        int idx = opts.indexOf(current);
        int next = idx < 0 ? 0 : Math.floorMod(idx + dir, opts.size());
        setExtraFieldStringValue(field.key, opts.get(next));
        refresh();
    }

    /**
     * Reads one key's raw string value out of {@link #draftExtraJson} - a
     * freeform JSON-object FRAGMENT (no outer braces, same format
     * {@code SemanticValidator#validateExtraJson} parses), by wrapping it
     * in {} before parsing. Returns "" for a missing key, blank/absent
     * box, or unparsable JSON - never throws, matching every other
     * malformed-input path in this class.
     */
    private String getExtraFieldRawValue(String key) {
        JsonObject obj = parseExtraJsonFragment(draftExtraJson);
        if (obj == null || !obj.has(key)) return "";
        JsonElement v = obj.get(key);
        return v.isJsonPrimitive() ? v.getAsString() : "";
    }

    private void setExtraFieldStringValue(String key, String value) {
        JsonObject obj = parseExtraJsonFragment(draftExtraJson);
        if (obj == null) obj = new JsonObject();
        if (value == null || value.isBlank()) obj.remove(key);
        else obj.addProperty(key, value);
        draftExtraJson = fragmentFromObject(obj);
        if (extraJsonBox != null) extraJsonBox.setValue(draftExtraJson);
    }

    private static JsonObject parseExtraJsonFragment(String extraJson) {
        String frag = extraJson == null ? "" : extraJson.trim();
        if (frag.isEmpty()) return new JsonObject();
        String toParse = frag.startsWith("{") ? frag : "{" + frag + "}";
        try {
            JsonElement el = JsonParser.parseString(toParse);
            return el.isJsonObject() ? el.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Serializes a JsonObject back to the bare-fragment style (no outer braces) every other extraJson box in this codebase uses. */
    private static String fragmentFromObject(JsonObject obj) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (var e : obj.entrySet()) {
            if (!first) sb.append(", ");
            first = false;
            sb.append('"').append(e.getKey()).append("\": ").append(e.getValue());
        }
        return sb.toString();
    }

    private boolean handleSlotClick(List<RecipeData.MachineSlot> slots, boolean input, int idx, int btn) {
        if (idx == slots.size()) {
            if (btn == 0) addSlot(input);
            return true;
        }
        RecipeData.MachineSlot slot = slots.get(idx);
        if (btn == 1) {
            slots.remove(idx);
            refresh();
        } else if (btn == 0) {
            replaceSlot(slot);
        }
        return true;
    }

    @Override public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
        if (inputScrollBar != null && inputScrollBar.mouseDragged(mx, my, b, dx, dy)) return true;
        if (outputScrollBar != null && outputScrollBar.mouseDragged(mx, my, b, dx, dy)) return true;
        if (isStationCanvas() && stationCanvas.handleDrag(mx, my)) return true;
        return super.mouseDragged(mx, my, b, dx, dy);
    }

    @Override public boolean mouseReleased(double mx, double my, int b) {
        if (inputScrollBar != null) inputScrollBar.mouseReleased(mx, my, b);
        if (outputScrollBar != null) outputScrollBar.mouseReleased(mx, my, b);
        stationCanvas.endDrag();
        return super.mouseReleased(mx, my, b);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        if (ritualMode) {
            List<RecipeData.MachineSlot> offerings = ritualOfferings();
            syncRitualOfferingRingSlots(offerings);
            int ringHit = RitualRingLayout.hitTestRing(mx, my, ritualRingPositions);
            int ringOfferingIdx = ringHit >= 0 ? offeringAtRingSlot(offerings, ringHit) : -1;
            if (ringOfferingIdx >= 0) {
                bumpSlotAmount(offerings.get(ringOfferingIdx), (int) Math.signum(delta) * (hasShiftDown() ? 10 : 1));
                return true;
            }
            if (RitualRingLayout.hits(mx, my, ritualOutX, ritualOutY, RitualRingLayout.OFFERING_SLOT) && !draftOutputs.isEmpty()) {
                bumpSlotAmount(draftOutputs.get(0), (int) Math.signum(delta) * (hasShiftDown() ? 10 : 1));
                return true;
            }
            return super.mouseScrolled(mx, my, scrollX, delta);
        }

        if (activeLayout != null) {
            CustomSlotHit inHit = customSlotIndexAt(draftInputs, layoutInputDefs, inputsGridY, mx, my);
            if (inHit != null && inHit.listIndex < draftInputs.size() && !isPlaceholder(draftInputs.get(inHit.listIndex))) {
                RecipeData.MachineSlot inSlot = draftInputs.get(inHit.listIndex);
                bumpSlotAmount(inSlot, (int) Math.signum(delta) * amountStepFor(inSlot), inHit.def);
                return true;
            }
            CustomSlotHit outHit = customSlotIndexAt(draftOutputs, layoutOutputDefs, outputsGridY, mx, my);
            if (outHit != null && outHit.listIndex < draftOutputs.size() && !isPlaceholder(draftOutputs.get(outHit.listIndex))) {
                RecipeData.MachineSlot outSlot = draftOutputs.get(outHit.listIndex);
                if (Screen.hasControlDown()) bumpSlotChance(outSlot, (int) Math.signum(delta) * (hasShiftDown() ? 10 : 1));
                else bumpSlotAmount(outSlot, (int) Math.signum(delta) * amountStepFor(outSlot), outHit.def);
                return true;
            }
            RecipeTypeDescriptor.FieldTank tankHit = fieldTankAt(mx, my);
            if (tankHit != null) {
                bumpFieldTankAmount(tankHit, (int) Math.signum(delta) * fluidScrollStep());
                return true;
            }
            if (isStationCanvas() && mx >= ax && mx < ax + stationCanvasW() && my >= inputsGridY && my < inputsGridY + STATION_CANVAS_H) {
                if (stationCanvas.handleScrollZoom(ax, inputsGridY, mx, my, delta)) return true;
            }
            return super.mouseScrolled(mx, my, scrollX, delta);
        }

        int inIdx = slotIndexAt(draftInputs, inputScrollBar, inputsGridY, mx, my);
        if (inIdx >= 0 && inIdx < draftInputs.size()) {
            bumpSlotAmount(draftInputs.get(inIdx), (int) Math.signum(delta) * (hasShiftDown() ? 10 : 1));
            return true;
        }
        int outIdx = slotIndexAt(draftOutputs, outputScrollBar, outputsGridY, mx, my);
        if (outIdx >= 0 && outIdx < draftOutputs.size()) {
            RecipeData.MachineSlot outSlot = draftOutputs.get(outIdx);
            if (Screen.hasControlDown()) bumpSlotChance(outSlot, (int) Math.signum(delta) * (hasShiftDown() ? 10 : 1));
            else bumpSlotAmount(outSlot, (int) Math.signum(delta) * (hasShiftDown() ? 10 : 1));
            return true;
        }

        if (my >= inputsGridY && my < outputsLabelY && inputScrollBar != null) {
            inputScrollBar.mouseScrolled(delta);
            return true;
        }
        if (my >= outputsGridY && outputScrollBar != null) {
            outputScrollBar.mouseScrolled(delta);
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, delta);
    }

    @Override public boolean keyPressed(int key, int scan, int mods) {
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && dismissAddonWarning()) return true;
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        // mismo atajo que TagEditorScreen/EventEditorScreen/
        // etc. (Ctrl+S = Save, Ctrl+D = Duplicate) - interceptado ANTES de
        // reenviar al widget enfocado, captura global segura porque ningun
        // EditBox de esta pantalla usa Ctrl+S/Ctrl+D para nada (mismo
        // criterio que las demas pantallas migradas). No hay atajo Ctrl+R:
        // esta pantalla no tiene un Clear/Reset propio (ver
        // duplicateMachineRecipe(), mismo motivo).
        if ((mods & GLFW.GLFW_MOD_CONTROL) != 0) {
            if (key == GLFW.GLFW_KEY_S) { save(); return true; }
            if (key == GLFW.GLFW_KEY_D) { duplicateMachineRecipe(); return true; }
        }
        if (getFocused() != null && getFocused().keyPressed(key, scan, mods)) return true;
        if ((mods & GLFW.GLFW_MOD_CONTROL) != 0
                && !(getFocused() instanceof net.minecraft.client.gui.components.EditBox)) {
            if (key == GLFW.GLFW_KEY_Z && (mods & GLFW.GLFW_MOD_SHIFT) != 0) { redoMachine(); return true; }
            if (key == GLFW.GLFW_KEY_Z) { undoMachine(); return true; }
            if (key == GLFW.GLFW_KEY_Y) { redoMachine(); return true; }
        }
        if (EditorKeys.isFocusTab(key, mods)) { super.keyPressed(key, scan, mods); return true; }
        return EditorKeys.swallow(mods);
    }

    @Override
    public void onClose() { backToParent(); }

    @Override public boolean isPauseScreen() { return false; }
}