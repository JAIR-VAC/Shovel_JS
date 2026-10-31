package com.kve.client.gui.recipe;

import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KvePlateButton;
import com.kve.client.gui.KveScaledEditBox;
import com.kve.client.gui.SafeItemRender;
import com.kve.client.gui.picker.ItemPickerScreen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.kve.client.permission.ClientPermissionState;
import com.kve.client.recipe.RecipeDraftActions;
import com.kve.client.gui.picker.FluidPickerScreen;
import com.kve.common.Lang;
import com.kve.common.recipe.RecipeData;
import com.kve.common.recipe.RecipeGridRules;
import com.kve.common.recipe.TagIngredient;
import com.kve.common.recipetype.RecipeTypeDescriptor;
import com.kve.common.recipetype.RecipeTypeDescriptorRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Panel lateral del grafo de recetas: la receta seleccionada dibujada con su forma
 * (mesa 3x3, sin forma, estacion de otro mod con todas sus ranuras) para cambiar
 * resultado, cantidad, ingredientes y campos de la estacion sin salir del grafo.
 * Plegado es una barra vertical; Guardar encola el cambio igual que el editor completo.
 */
@OnlyIn(Dist.CLIENT)
final class RecipeQuickEditPanel {

    static final int BAR_W = 14;
    static final int PANEL_W = 196;
    private static final int SLOT = 18;
    private static final int OUT_SLOT = 24;
    private static final int PAD = 6;
    private static final int MAX_LIST = 9;
    private static final int ARROW_W = 22;
    private static final int FIELD_ROW = 15;

    /** Como se dibujan y editan los ingredientes de la receta cargada. */
    private enum Shape { GRID, SHAPELESS, LIST, MACHINE, RITUAL, NONE }

    private enum Kind { OUTPUT, INPUT, MACHINE_IN, MACHINE_OUT, CENTER }

    /** Donde vive el item del centro de un ritual, con la misma regla que el editor completo. */
    private enum CenterMode { NONE, INPUT_SLOT, EXTRA_FIELD }

    private static final int RING_R = 36;
    private static final int CENTER_SLOT = 22;

    /** Un slot en pantalla; {@code def} es la ranura del descriptor de la estacion (null en mesas). */
    private record Box(int x, int y, int size, Kind kind, int index, RecipeTypeDescriptor.SlotDef def, boolean add) {
        boolean hit(double mx, double my) { return mx >= x && mx < x + size && my >= y && my < y + size; }
    }

    private boolean open;
    private RecipeIndex.Entry entry;
    private RecipeData data;
    private RecipeData original;
    private String removal;
    private boolean dirty;
    private Shape shape = Shape.NONE;
    private RecipeTypeDescriptor descriptor;
    private CenterMode centerMode = CenterMode.NONE;
    private String centerField;
    private String status = "";
    private int statusColor;

    private int x, y, h;
    private final List<Box> boxes = new ArrayList<>();
    private int cardY, cardH, arrowX, arrowY;
    private boolean stacked;
    private KvePlateButton saveBtn, undoBtn;

    boolean isOpen() { return open; }

    int width() { return open ? PANEL_W : BAR_W; }

    void setOpen(boolean o) { open = o; }

    boolean isDirty() { return dirty; }

    RecipeIndex.Entry entry() { return entry; }

    String draftId() { return data == null ? null : data.recipeId; }

    /** Carga la receta seleccionada; null la deja vacia. No pisa cambios sin guardar de la misma receta. */
    void load(RecipeIndex.Entry e) {
        if (e == entry) return;
        entry = e;
        dirty = false;
        status = "";
        data = original = null;
        removal = null;
        shape = Shape.NONE;
        descriptor = null;
        centerMode = CenterMode.NONE;
        centerField = null;
        if (e == null) return;
        RecipeIndex.Editable ed = RecipeIndex.get().editable(e);
        if (ed == null) {
            setStatus(Lang.get("gui.kve.recipe.tree.edit_failed", e.id.toString()), KveGuiStyle.TEXT_WARN);
            return;
        }
        data = ed.data();
        // las estaciones con slots propios se guardan como receta de maquina, igual que en el editor completo
        if (RecipeGridRules.shouldRouteToMachineEditor(data)) {
            RecipeGridRules.migrateForMachineEditor(data);
            data.type = RecipeData.RecipeType.CUSTOM_MACHINE;
        }
        if (data.type == RecipeData.RecipeType.VANILLA_SHAPED) padToCraftingGrid(data);
        if (data.customTypeId != null && !data.customTypeId.isBlank()) {
            descriptor = RecipeTypeDescriptorRegistry.find(data.customTypeId).orElse(null);
        }
        original = RecipeIndex.copyOf(data);
        removal = ed.removal();
        shape = shapeOf(data);
        if (shape == Shape.MACHINE && descriptor != null && descriptor.hasRitualView()) {
            shape = Shape.RITUAL;
            resolveCenter();
        }
    }

    /** Mismo orden de reglas que MachineRecipeEditScreen#resolveRitualView. */
    private void resolveCenter() {
        RecipeTypeDescriptor.RitualViewSettings rv = descriptor.ritualView;
        List<RecipeTypeDescriptor.SlotDef> ins = defs(false);
        if (rv != null && rv.hasCenterField()) {
            for (RecipeTypeDescriptor.ExtraField f : descriptor.extraFields) {
                if (rv.centerField.equals(f.key)) { centerMode = CenterMode.EXTRA_FIELD; centerField = f.key; return; }
            }
        }
        if (rv != null && rv.hasCenterSlotId()) {
            for (RecipeTypeDescriptor.SlotDef d : ins) if (rv.centerSlotId.equals(d.id)) { centerMode = CenterMode.INPUT_SLOT; return; }
        }
        for (RecipeTypeDescriptor.SlotDef d : ins) {
            String id = d.id == null ? "" : d.id.toLowerCase(java.util.Locale.ROOT);
            if (id.equals("center") || id.equals("main_input") || id.equals("reagent")) { centerMode = CenterMode.INPUT_SLOT; return; }
        }
        for (RecipeTypeDescriptor.ExtraField f : descriptor.extraFields) {
            if (f.required && "item".equalsIgnoreCase(f.type) && (f.key.equals("reagent") || f.key.equals("activationItem"))) {
                centerMode = CenterMode.EXTRA_FIELD; centerField = f.key; return;
            }
        }
        if (!ins.isEmpty()) centerMode = CenterMode.INPUT_SLOT;
    }

    /** Indice en machineInputs donde empiezan las ofrendas (la 0 es el centro si el centro es una entrada). */
    private int firstOffering() { return centerMode == CenterMode.INPUT_SLOT ? 1 : 0; }

    private int maxOfferings() {
        RecipeTypeDescriptor.RitualViewSettings rv = descriptor == null ? null : descriptor.ritualView;
        return rv == null ? 8 : Math.max(1, rv.maxOfferings);
    }

    private ItemStack centerStack() {
        if (centerMode == CenterMode.EXTRA_FIELD) {
            JsonElement v = extraJson().get(centerField);
            return itemFromId(v != null && v.isJsonPrimitive() ? v.getAsString() : "");
        }
        if (centerMode == CenterMode.INPUT_SLOT) return emptyOr(slotAt(data.machineInputs, 0));
        return ItemStack.EMPTY;
    }

    private static ItemStack itemFromId(String id) {
        if (id == null || id.isBlank()) return ItemStack.EMPTY;
        if (id.startsWith("#")) {
            List<net.minecraft.world.item.Item> m = TagIngredient.members(id.substring(1));
            return TagIngredient.of(id.substring(1), m.isEmpty() ? Items.BARRIER : m.get(0));
        }
        net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(id);
        net.minecraft.world.item.Item it = rl == null ? null : net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
        return it == null || it == Items.AIR ? ItemStack.EMPTY : new ItemStack(it);
    }

    /**
     * Una receta con forma mas chica que 3x3 se muestra en la mesa completa para poder poner
     * items en cualquier celda. Es la misma receta: al cargarla el juego recorta filas y
     * columnas vacias del borde.
     */
    private static void padToCraftingGrid(RecipeData d) {
        int w = d.gridWidth, gh = d.gridHeight;
        if (w <= 0 || gh <= 0 || w > 3 || gh > 3 || (w == 3 && gh == 3) || d.ingredients.size() != w * gh) return;
        List<ItemStack> grid = new ArrayList<>();
        for (int i = 0; i < 9; i++) grid.add(ItemStack.EMPTY);
        for (int r = 0; r < gh; r++) for (int c = 0; c < w; c++) grid.set(r * 3 + c, d.ingredients.get(r * w + c));
        d.ingredients = grid;
        d.gridWidth = 3;
        d.gridHeight = 3;
    }

    private static Shape shapeOf(RecipeData d) {
        return switch (d.type) {
            case CUSTOM_MACHINE -> Shape.MACHINE;
            case EC_COMBINATION -> Shape.NONE;
            case VANILLA_SHAPELESS, EC_TABLE_SHAPELESS, EC_ENDER_SHAPELESS, EC_FLUX_SHAPELESS -> Shape.SHAPELESS;
            case CUSTOM_JSON -> !d.ingredients.isEmpty() && d.ingredients.size() == d.gridWidth * d.gridHeight ? Shape.GRID : Shape.LIST;
            default -> d.ingredients.isEmpty() ? Shape.NONE : Shape.GRID;
        };
    }

    private void setStatus(String s, int color) { status = s; statusColor = color; }

    void layout(int x, int y, int h) { this.x = x; this.y = y; this.h = h; }

    private int tw() { return PANEL_W - PAD * 2; }

    // ---- ranuras de la estacion ----

    private List<RecipeTypeDescriptor.SlotDef> defs(boolean outputs) {
        List<RecipeTypeDescriptor.SlotDef> out = new ArrayList<>();
        if (descriptor == null || !descriptor.hasSlotLayout()) return out;
        for (RecipeTypeDescriptor.SlotDef d : descriptor.slotLayout.slots) if (d.isValid() && d.isOutput() == outputs) out.add(d);
        return out;
    }

    private static RecipeTypeDescriptor.SlotDef repeatable(List<RecipeTypeDescriptor.SlotDef> defs) {
        for (RecipeTypeDescriptor.SlotDef d : defs) if (d.repeatable) return d;
        return null;
    }

    private record Cell(int index, RecipeTypeDescriptor.SlotDef def, boolean add) {}

    /**
     * Celdas de una seccion de la maquina, igual que el editor completo: la ranura fija k es el
     * slot k (vacio si no hay); lo que sobra va a la ranura repetible, que ademas ofrece "+".
     */
    private List<Cell> machineCells(List<RecipeData.MachineSlot> slots, boolean outputs) {
        List<RecipeTypeDescriptor.SlotDef> defs = defs(outputs);
        List<Cell> cells = new ArrayList<>();
        if (defs.isEmpty()) {
            for (int i = 0; i < slots.size(); i++) cells.add(new Cell(i, null, false));
            // sin ranuras declaradas: al menos una fila de huecos para agregar
            for (int i = slots.size(); i < Math.max(slots.size() + 1, outputs ? 1 : 3) && i < MAX_LIST; i++) cells.add(new Cell(slots.size(), null, true));
            return cells;
        }
        RecipeTypeDescriptor.SlotDef rep = repeatable(defs);
        int k = 0;
        for (RecipeTypeDescriptor.SlotDef d : defs) if (d != rep) cells.add(new Cell(k++, d, false));
        for (int i = k; i < slots.size(); i++) cells.add(new Cell(i, rep, false));
        if (rep != null && slots.size() < k + MAX_LIST) cells.add(new Cell(Math.max(k, slots.size()), rep, true));
        return cells;
    }

    private static boolean isEmptySlot(RecipeData.MachineSlot s) {
        return switch (s.type) {
            case ITEM -> s.item == null || s.item.isEmpty();
            case FLUID -> s.fluidId == null || s.fluidId.isBlank();
            case TAG -> s.tagId == null || s.tagId.isBlank();
        };
    }

    private static boolean isFluidDef(RecipeTypeDescriptor.SlotDef d) {
        return d != null && ("fluid".equalsIgnoreCase(d.type) || d.tank || d.isChemical());
    }

    private RecipeData.MachineSlot slotAt(List<RecipeData.MachineSlot> slots, int i) {
        return i < slots.size() ? slots.get(i) : null;
    }

    // ---- layout de la tarjeta ----

    private int[] gridOf(int n, int maxCols) {
        int cols = Math.min(maxCols, Math.max(1, n));
        return new int[]{cols, Math.max(1, (n + cols - 1) / cols)};
    }

    /** Calcula las cajas de slots; lo usan addWidgets, render y los clicks para que coincidan. */
    private void computeLayout() {
        boxes.clear();
        cardY = y + 46;
        if (data == null || shape == Shape.NONE) { cardH = 0; return; }
        if (shape == Shape.RITUAL) { computeRitualLayout(); return; }
        List<Cell> inCells = shape == Shape.MACHINE ? machineCells(data.machineInputs, false) : null;
        List<Cell> outCells = shape == Shape.MACHINE ? machineCells(data.machineOutputs, true) : null;
        int[] g = switch (shape) {
            case GRID -> new int[]{Math.max(1, data.gridWidth), Math.max(1, data.gridHeight)};
            case SHAPELESS -> new int[]{3, 3};
            case LIST -> gridOf(MAX_LIST, 3);
            default -> gridOf(inCells.size(), inCells.size() > 4 ? 3 : 2);
        };
        int outN = shape == Shape.MACHINE ? Math.max(1, outCells.size()) : 1;
        int outCols = outN > 4 ? 2 : 1;
        int outRows = (outN + outCols - 1) / outCols;
        int inW = g[0] * SLOT, inH = g[1] * SLOT;
        int outW = outCols * (OUT_SLOT + 2) - 2, outH = outRows * (OUT_SLOT + 2) - 2;
        stacked = inW + ARROW_W + outW > tw() - 8;
        int innerH = stacked ? inH + 16 + outH : Math.max(inH, outH);
        cardH = innerH + 16 + 14;
        int top = cardY + 8;
        int inX, inY, outX, outY0;
        if (stacked) {
            inX = x + PAD + (tw() - inW) / 2;
            inY = top;
            arrowX = x + PAD + tw() / 2 - 4;
            arrowY = inY + inH + 4;
            outX = x + PAD + (tw() - outW) / 2;
            outY0 = inY + inH + 16;
        } else {
            int total = inW + ARROW_W + outW;
            inX = x + PAD + (tw() - total) / 2;
            inY = top + (innerH - inH) / 2;
            arrowX = inX + inW + 6;
            arrowY = top + innerH / 2 - 4;
            outX = inX + inW + ARROW_W;
            outY0 = top + (innerH - outH) / 2;
        }
        switch (shape) {
            case GRID -> {
                for (int i = 0; i < data.ingredients.size(); i++)
                    boxes.add(new Box(inX + (i % g[0]) * SLOT, inY + (i / g[0]) * SLOT, SLOT, Kind.INPUT, i, null, false));
            }
            // sin forma la posicion no importa: cualquier celda vacia agrega
            case SHAPELESS -> {
                int n = data.shapelessIngredients.size();
                for (int i = 0; i < 9; i++)
                    boxes.add(new Box(inX + (i % 3) * SLOT, inY + (i / 3) * SLOT, SLOT, Kind.INPUT, i, null, i >= n));
            }
            case LIST -> {
                // como la mesa sin forma: siempre las 9 celdas, las libres agregan
                int n = data.shapelessIngredients.size();
                int total = Math.max(MAX_LIST, n);
                for (int i = 0; i < total; i++)
                    boxes.add(new Box(inX + (i % g[0]) * SLOT, inY + (i / g[0]) * SLOT, SLOT, Kind.INPUT, i, null, i >= n));
            }
            case MACHINE -> {
                for (int i = 0; i < inCells.size(); i++) {
                    Cell c = inCells.get(i);
                    boxes.add(new Box(inX + (i % g[0]) * SLOT, inY + (i / g[0]) * SLOT, SLOT, Kind.MACHINE_IN, c.index(), c.def(), c.add()));
                }
            }
            default -> { }
        }
        if (shape == Shape.MACHINE) {
            if (outCells.isEmpty()) outCells = List.of(new Cell(0, null, true));
            for (int i = 0; i < outCells.size(); i++) {
                Cell c = outCells.get(i);
                boxes.add(new Box(outX + (i % outCols) * (OUT_SLOT + 2), outY0 + (i / outCols) * (OUT_SLOT + 2), OUT_SLOT,
                        Kind.MACHINE_OUT, c.index(), c.def(), c.add()));
            }
        } else {
            boxes.add(new Box(outX, outY0, OUT_SLOT, Kind.OUTPUT, -1, null, false));
        }
    }

    private void computeRitualLayout() {
        List<Cell> outCells = machineCells(data.machineOutputs, true);
        if (outCells.isEmpty()) outCells = List.of(new Cell(0, null, true));
        int ringW = RING_R * 2 + SLOT;
        int outH = outCells.size() * (OUT_SLOT + 2) - 2;
        int innerH = Math.max(ringW, outH);
        cardH = innerH + 16 + 14;
        stacked = false;
        int top = cardY + 8;
        int total = ringW + ARROW_W + OUT_SLOT;
        int ringX = x + PAD + (tw() - total) / 2;
        int cx = ringX + ringW / 2, cy = top + innerH / 2;
        if (centerMode != CenterMode.NONE) {
            boxes.add(new Box(cx - CENTER_SLOT / 2, cy - CENTER_SLOT / 2, CENTER_SLOT, Kind.CENTER, -1, null, false));
        }
        int first = firstOffering();
        int offerings = Math.max(0, data.machineInputs.size() - first);
        boolean canAdd = offerings < maxOfferings();
        int n = offerings + (canAdd ? 1 : 0);
        RecipeTypeDescriptor.RitualViewSettings rv = descriptor.ritualView;
        for (int i = 0; i < n; i++) {
            int px, py;
            if (rv != null && rv.hasPositions() && i < rv.positions.size()) {
                int[] pos = rv.positions.get(i);
                px = cx + Math.round(pos[0] * RING_R / 30f);
                py = cy + Math.round(pos[1] * RING_R / 30f);
            } else {
                double a = -Math.PI / 2 + 2 * Math.PI * i / Math.max(1, n);
                px = cx + (int) Math.round(Math.cos(a) * RING_R);
                py = cy + (int) Math.round(Math.sin(a) * RING_R);
            }
            boolean add = i >= offerings;
            boxes.add(new Box(px - SLOT / 2, py - SLOT / 2, SLOT, Kind.MACHINE_IN, add ? Math.max(first, data.machineInputs.size()) : first + i, null, add));
        }
        arrowX = ringX + ringW + 6;
        arrowY = cy - 4;
        int outX = ringX + ringW + ARROW_W, outY0 = top + (innerH - outH) / 2;
        for (int i = 0; i < outCells.size(); i++) {
            Cell c = outCells.get(i);
            boxes.add(new Box(outX, outY0 + i * (OUT_SLOT + 2), OUT_SLOT, Kind.MACHINE_OUT, c.index(), c.def(), c.add()));
        }
    }

    private Box mainOutputBox() {
        int first = firstItemOutput();
        for (Box b : boxes) {
            if (b.kind == Kind.OUTPUT) return b;
            if (b.kind == Kind.MACHINE_OUT && !b.add && b.index == first) return b;
        }
        return null;
    }

    private int firstItemOutput() {
        if (data == null) return -1;
        for (int i = 0; i < data.machineOutputs.size(); i++) {
            RecipeData.MachineSlot s = data.machineOutputs.get(i);
            if (s.type == RecipeData.MachineSlotType.ITEM && !s.item.isEmpty()) return i;
        }
        return -1;
    }

    // ---- campos de la estacion (tiempo, energia, opciones...) ----

    private List<RecipeTypeDescriptor.ExtraField> extraFields() {
        if (descriptor == null || descriptor.extraFields == null) return List.of();
        List<RecipeTypeDescriptor.ExtraField> out = new ArrayList<>();
        for (RecipeTypeDescriptor.ExtraField f : descriptor.extraFields) {
            if (f.repeatable || f.key == null || f.key.isBlank() || f.key.equals(centerField)) continue;
            out.add(f);
        }
        return out;
    }

    private JsonObject extraJson() {
        String raw = data.customExtraJson == null ? "" : data.customExtraJson.trim();
        if (raw.isEmpty()) return new JsonObject();
        try {
            JsonElement el = JsonParser.parseString(raw.startsWith("{") ? raw : "{" + raw + "}");
            return el.isJsonObject() ? el.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException ex) {
            return new JsonObject();
        }
    }

    private boolean extraParses() {
        String raw = data.customExtraJson == null ? "" : data.customExtraJson.trim();
        if (raw.isEmpty()) return true;
        try { return JsonParser.parseString(raw.startsWith("{") ? raw : "{" + raw + "}").isJsonObject(); }
        catch (RuntimeException ex) { return false; }
    }

    private static String valueText(JsonElement v) {
        if (v == null || v.isJsonNull()) return "";
        if (v.isJsonPrimitive()) return v.getAsJsonPrimitive().isString() ? v.getAsString() : v.toString();
        return null;
    }

    private void setExtra(RecipeTypeDescriptor.ExtraField f, String text) {
        JsonObject o = extraJson();
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) o.remove(f.key);
        else if ("boolean".equals(f.type)) o.addProperty(f.key, Boolean.parseBoolean(t));
        else if (!f.isStringType()) {
            try {
                if (t.contains(".") || "float".equals(f.type) || "double".equals(f.type)) o.addProperty(f.key, Double.parseDouble(t));
                else o.addProperty(f.key, Long.parseLong(t));
            } catch (NumberFormatException ex) {
                setStatus(Lang.get("gui.kve.recipe.quick.field_number", f.effectiveLabel()), KveGuiStyle.TEXT_WARN);
                return;
            }
        } else o.add(f.key, new JsonPrimitive(t));
        data.customExtraJson = o.size() == 0 ? "" : o.toString();
        markDirty();
    }

    private int fieldsY() { return cardY + cardH + 4 + 10; }

    private int fieldRowsThatFit() {
        int bottom = y + h - PAD - KveGuiStyle.BTN_H * 2 - 8 - 22;
        return Math.max(0, (bottom - fieldsY()) / FIELD_ROW);
    }

    private void addFieldWidgets(Screen host, Consumer<AbstractWidget> add) {
        List<RecipeTypeDescriptor.ExtraField> fields = extraFields();
        if (fields.isEmpty() || !extraParses()) return;
        Font font = Minecraft.getInstance().font;
        JsonObject o = extraJson();
        int rows = Math.min(fields.size(), fieldRowsThatFit());
        int labelW = tw() / 2 - 2;
        int bx = x + PAD + labelW + 4, bw = tw() - labelW - 4;
        for (int i = 0; i < rows; i++) {
            RecipeTypeDescriptor.ExtraField f = fields.get(i);
            int ry = fieldsY() + i * FIELD_ROW;
            String cur = valueText(o.get(f.key));
            if (cur == null) continue;
            if (f.hasOptions() || "boolean".equals(f.type)) {
                List<String> opts = f.hasOptions() ? f.options : List.of("true", "false");
                String shown = cur.isEmpty() ? "-" : cur;
                add.accept(new KvePlateButton(bx, ry, bw, 13, Component.literal(shown), b -> {
                    int idx = opts.indexOf(cur);
                    setExtra(f, opts.get((idx + 1) % opts.size()));
                    reinit(host);
                }));
            } else {
                KveScaledEditBox box = new KveScaledEditBox(font, bx, ry, bw, 13, Component.literal(f.effectiveLabel()));
                box.setMaxLength(64);
                box.setValue(cur);
                if (cur.isEmpty() && f.defaultValue != null) box.setHint(Component.literal(f.defaultValue));
                box.setResponder(t -> setExtra(f, t));
                add.accept(box);
            }
        }
    }

    // ---- widgets ----

    void addWidgets(Screen host, Consumer<AbstractWidget> add, Runnable afterSave) {
        saveBtn = undoBtn = null;
        if (!open) return;
        add.accept(new KvePlateButton(x + PANEL_W - 16, y + 3, 13, 12, Component.literal("◀"), b -> { open = false; reinit(host); }));
        if (data == null) return;
        computeLayout();
        int bw = (tw() - 4) / 2;
        int by = y + h - PAD - KveGuiStyle.BTN_H;
        add.accept(new KvePlateButton(x + PAD, by - KveGuiStyle.BTN_H - 4, tw(), KveGuiStyle.BTN_H,
                Component.literal(Lang.get("gui.kve.recipe.quick.full_editor")), b -> openFullEditor(host)));
        saveBtn = new KvePlateButton(x + PAD, by, bw, KveGuiStyle.BTN_H,
                Component.literal(Lang.get("gui.kve.recipe.quick.save")), b -> save(afterSave)).withPrimary(true);
        saveBtn.active = dirty;
        add.accept(saveBtn);
        undoBtn = new KvePlateButton(x + PAD + bw + 4, by, bw, KveGuiStyle.BTN_H,
                Component.literal(Lang.get("gui.kve.recipe.quick.revert")), b -> {
                    data = RecipeIndex.copyOf(original);
                    dirty = false;
                    setStatus("", 0);
                    reinit(host);
                });
        undoBtn.active = dirty;
        add.accept(undoBtn);
        Box out = mainOutputBox();
        if (out != null) {
            int cy = cardY + cardH - 13;
            int cx = out.x + out.size / 2;
            add.accept(new KvePlateButton(cx - 26, cy, 12, 11, Component.literal("-"), b -> changeCount(host, -1)));
            add.accept(new KvePlateButton(cx + 14, cy, 12, 11, Component.literal("+"), b -> changeCount(host, 1)));
        }
        addFieldWidgets(host, add);
    }

    private void markDirty() {
        dirty = true;
        if (saveBtn != null) saveBtn.active = true;
        if (undoBtn != null) undoBtn.active = true;
    }

    private static void reinit(Screen host) {
        Minecraft mc = Minecraft.getInstance();
        host.init(mc, host.width, host.height);
    }

    private ItemStack mainOutput() {
        if (shape == Shape.MACHINE) {
            int i = firstItemOutput();
            return i < 0 ? ItemStack.EMPTY : data.machineOutputs.get(i).item;
        }
        return data.output;
    }

    private void changeCount(Screen host, int d) {
        ItemStack out = mainOutput();
        if (out.isEmpty()) return;
        int max = Math.max(1, out.getMaxStackSize());
        int n = Math.max(1, Math.min(max, out.getCount() + d));
        if (n == out.getCount()) return;
        out.setCount(n);
        if (shape == Shape.MACHINE) syncMachineOutput();
        dirty = true;
        reinit(host);
    }

    /** El resultado "visible" (grafo, avisos) sigue a la primera salida de item de la maquina. */
    private void syncMachineOutput() {
        ItemStack out = mainOutput();
        data.output = out.isEmpty() ? ItemStack.EMPTY : out.copy();
    }

    private void save(Runnable afterSave) {
        boolean outputEmpty = shape == Shape.MACHINE
                ? data.machineOutputs.stream().allMatch(RecipeQuickEditPanel::isEmptySlot)
                : data.output.isEmpty();
        String blocker = RecipeDraftActions.saveBlocker(ClientPermissionState.canEdit(), outputEmpty,
                data.type, data.customTypeId, false);
        if (blocker != null) { setStatus(Lang.get(blocker), KveGuiStyle.TEXT_WARN); return; }
        if (!hasAnyInput()) { setStatus(Lang.get("gui.kve.recipe.quick.needs_input"), KveGuiStyle.TEXT_WARN); return; }
        RecipeData toSave = RecipeIndex.copyOf(data);
        RecipeDraftActions.queueSave(toSave, removal, () -> {});
        original = RecipeIndex.copyOf(data);
        dirty = false;
        removal = null;
        entry = null;
        setStatus(Lang.get("gui.kve.recipe.quick.saved"), KveGuiStyle.TEXT_OK);
        afterSave.run();
    }

    private boolean hasAnyInput() {
        if (descriptor != null && descriptor.noIngredients) return true;
        return switch (shape) {
            case GRID -> data.ingredients.stream().anyMatch(s -> !s.isEmpty());
            case SHAPELESS, LIST -> !data.shapelessIngredients.isEmpty();
            case MACHINE, RITUAL -> data.machineInputs.stream().anyMatch(s -> !isEmptySlot(s)) || !centerStack().isEmpty();
            default -> true;
        };
    }

    /** Despues de reconstruir el grafo la receta guardada es otro Entry: se adopta sin perder el estado. */
    void adopt(RecipeIndex.Entry e) { entry = e; }

    private void openFullEditor(Screen host) {
        RecipeData d = RecipeIndex.copyOf(data);
        Screen from = host instanceof RecipeGraphScreen graph ? graph.parentScreen() : host;
        com.kve.client.gui.studio.KveStudioShell.openRecipe(from, d, removal);
    }

    // ---- dibujo ----

    void render(GuiGraphics g, Font font, int mx, int my) {
        if (!open) {
            boolean hov = mx >= x && mx < x + BAR_W && my >= y && my < y + h;
            g.fill(x, y, x + BAR_W, y + h, hov ? KveGuiStyle.BORDER_FIELD : KveGuiStyle.BG_FIELD);
            g.fill(x + BAR_W - 1, y, x + BAR_W, y + h, KveGuiStyle.BORDER_SUBTLE);
            g.drawString(font, "▶", x + 3, y + 4, KveGuiStyle.ACCENT_GOLD, false);
            String label = Lang.get("gui.kve.recipe.quick.title");
            g.pose().pushPose();
            g.pose().translate(x + 3, y + 18, 0);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(90));
            g.drawString(font, label, 0, -8, dirty ? KveGuiStyle.TEXT_WARN : KveGuiStyle.TEXT_MUTED, false);
            g.pose().popPose();
            return;
        }
        g.fill(x, y, x + PANEL_W, y + h, KveGuiStyle.BG_FIELD);
        g.fill(x + PANEL_W - 1, y, x + PANEL_W, y + h, KveGuiStyle.BORDER_SUBTLE);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.recipe.quick.title") + (dirty ? " *" : ""), x + PAD, y + 5, tw() - 16, dirty ? KveGuiStyle.TEXT_WARN : KveGuiStyle.ACCENT_GOLD);
        if (data == null) {
            if (entry == null) drawWrapped(g, font, Lang.get("gui.kve.recipe.quick.empty"), y + 22, KveGuiStyle.TEXT_MUTED);
            drawStatus(g, font);
            return;
        }
        computeLayout();
        drawStationHeader(g, font);
        int ty;
        if (shape == Shape.NONE) {
            ty = drawWrapped(g, font, Lang.get("gui.kve.recipe.quick.inputs_full_only"), cardY + 4, KveGuiStyle.TEXT_MUTED);
        } else {
            drawCard(g, font, mx, my);
            ty = cardY + cardH + 4;
            ty = drawFields(g, font, ty);
            ty = drawWrapped(g, font, Lang.get(shape == Shape.GRID ? "gui.kve.recipe.quick.hint_grid" : shape == Shape.RITUAL ? "gui.kve.recipe.quick.hint_ritual" : "gui.kve.recipe.quick.hint_list"), ty + 2, KveGuiStyle.TEXT_MUTED);
        }
        if (removal != null) drawWrapped(g, font, Lang.get("gui.kve.recipe.quick.replaces", removal), ty + 4, KveGuiStyle.TEXT_MUTED);
        drawStatus(g, font);
    }

    /** Etiquetas de los campos de la estacion; devuelve el y que sigue. */
    private int drawFields(GuiGraphics g, Font font, int ty) {
        List<RecipeTypeDescriptor.ExtraField> fields = extraFields();
        if (fields.isEmpty()) return ty;
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.recipe.quick.fields"), x + PAD, ty, tw(), KveGuiStyle.TEXT_LABEL);
        if (!extraParses()) {
            return drawWrapped(g, font, Lang.get("gui.kve.recipe.quick.fields_full_only"), ty + 10, KveGuiStyle.TEXT_MUTED);
        }
        JsonObject o = extraJson();
        int rows = Math.min(fields.size(), fieldRowsThatFit());
        int labelW = tw() / 2 - 2;
        for (int i = 0; i < rows; i++) {
            RecipeTypeDescriptor.ExtraField f = fields.get(i);
            int ry = fieldsY() + i * FIELD_ROW;
            KveGuiStyle.drawTextSafe(g, f.effectiveLabel(), x + PAD, ry + 3, labelW, KveGuiStyle.TEXT_PRIMARY);
            if (valueText(o.get(f.key)) == null) {
                KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.recipe.quick.field_complex"), x + PAD + labelW + 4, ry + 3, tw() - labelW - 4, KveGuiStyle.TEXT_MUTED);
            }
        }
        int end = fieldsY() + rows * FIELD_ROW;
        if (rows < fields.size()) {
            end = drawWrapped(g, font, Lang.get("gui.kve.recipe.quick.fields_more", fields.size() - rows), end, KveGuiStyle.TEXT_MUTED);
        }
        return end;
    }

    private void drawStationHeader(GuiGraphics g, Font font) {
        int hy = y + 19;
        g.fill(x + PAD, hy, x + PAD + tw(), hy + 22, 0xFF0E1828);
        g.renderOutline(x + PAD, hy, tw(), 22, KveGuiStyle.BORDER_SUBTLE);
        ItemStack station = entry != null ? entry.station : ItemStack.EMPTY;
        if (station == null || station.isEmpty()) station = new ItemStack(Items.CRAFTING_TABLE);
        SafeItemRender.render(g, station, x + PAD + 3, hy + 3);
        String type = entry != null ? entry.typeLabel : data.type.name();
        KveGuiStyle.drawTextSafe(g, type, x + PAD + 23, hy + 3, tw() - 26, KveGuiStyle.ACCENT_GOLD);
        KveGuiStyle.drawTextSafe(g, data.recipeId, x + PAD + 23, hy + 12, tw() - 26, KveGuiStyle.TEXT_MUTED);
    }

    private void drawCard(GuiGraphics g, Font font, int mx, int my) {
        int cx = x + PAD;
        g.fill(cx, cardY, cx + tw(), cardY + cardH, 0xFF101A2A);
        g.renderOutline(cx, cardY, tw(), cardH, KveGuiStyle.BORDER_FIELD);
        if (shape == Shape.RITUAL) drawRing(g);
        for (Box b : boxes) drawBox(g, font, b, mx, my);
        g.drawString(font, stacked ? "↓" : "→", arrowX, arrowY, KveGuiStyle.ACCENT_GOLD, false);
        Box out = mainOutputBox();
        if (out != null) {
            ItemStack o = mainOutput();
            KveGuiStyle.drawCenteredTextSafe(g, "x" + (o.isEmpty() ? 0 : o.getCount()), out.x + out.size / 2, cardY + cardH - 11, KveGuiStyle.TEXT_PRIMARY);
        }
    }

    /** Circulo tenue que une las ofrendas, para que el anillo se lea como tal. */
    private void drawRing(GuiGraphics g) {
        Box c = null;
        for (Box b : boxes) if (b.kind == Kind.CENTER) c = b;
        int cx, cy;
        if (c != null) { cx = c.x + c.size / 2; cy = c.y + c.size / 2; }
        else { int top = cardY + 8; cx = x + PAD + (tw() - (RING_R * 2 + SLOT + ARROW_W + OUT_SLOT)) / 2 + RING_R + SLOT / 2; cy = top + (cardH - 30) / 2; }
        for (int a = 0; a < 72; a++) {
            double t = 2 * Math.PI * a / 72;
            int px = cx + (int) Math.round(Math.cos(t) * RING_R), py = cy + (int) Math.round(Math.sin(t) * RING_R);
            g.fill(px, py, px + 1, py + 1, 0x704A3A6A);
        }
    }

    private void drawBox(GuiGraphics g, Font font, Box b, int mx, int my) {
        boolean output = b.kind == Kind.OUTPUT || b.kind == Kind.MACHINE_OUT;
        if (output) g.fill(b.x - 1, b.y - 1, b.x + b.size + 1, b.y + b.size + 1, 0xFF2A4A6A);
        if (b.add) {
            RecipeSlotRenderer.drawSlot(g, font, b.x, b.y, b.size, ItemStack.EMPTY, mx, my);
            KveGuiStyle.drawCenteredTextSafe(g, "+", b.x + b.size / 2, b.y + (b.size - 8) / 2, KveGuiStyle.TEXT_MUTED);
            return;
        }
        switch (b.kind) {
            case CENTER -> {
                g.fill(b.x - 2, b.y - 2, b.x + b.size + 2, b.y + b.size + 2, 0xFF6A4A8A);
                RecipeSlotRenderer.drawSlot(g, font, b.x, b.y, b.size, centerStack(), mx, my);
            }
            case OUTPUT -> RecipeSlotRenderer.drawSlot(g, font, b.x, b.y, b.size, data.output, mx, my);
            case INPUT -> RecipeSlotRenderer.drawSlot(g, font, b.x, b.y, b.size, inputAt(b.index), mx, my);
            case MACHINE_IN -> drawMachineSlot(g, font, b, slotAt(data.machineInputs, b.index), mx, my);
            case MACHINE_OUT -> drawMachineSlot(g, font, b, slotAt(data.machineOutputs, b.index), mx, my);
        }
    }

    private void drawMachineSlot(GuiGraphics g, Font font, Box b, RecipeData.MachineSlot s, int mx, int my) {
        boolean empty = s == null || isEmptySlot(s);
        RecipeSlotRenderer.drawSlot(g, font, b.x, b.y, b.size, empty ? ItemStack.EMPTY : displayOf(s), mx, my);
        boolean fluid = (s != null && s.type == RecipeData.MachineSlotType.FLUID) || (empty && isFluidDef(b.def));
        if (fluid) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            g.fill(b.x + 1, b.y + b.size - 4, b.x + b.size - 1, b.y + b.size - 1, empty ? 0x803A7FD0 : 0xC03A7FD0);
            g.pose().popPose();
        }
        if (empty && b.def != null && b.def.required) g.renderOutline(b.x, b.y, b.size, b.size, 0xFFB08040);
    }

    private static ItemStack displayOf(RecipeData.MachineSlot s) { return RecipeCardRenderer.machineIcon(s); }

    private ItemStack inputAt(int i) {
        List<ItemStack> l = shape == Shape.GRID ? data.ingredients : data.shapelessIngredients;
        return i >= 0 && i < l.size() ? l.get(i) : ItemStack.EMPTY;
    }

    private int drawWrapped(GuiGraphics g, Font font, String text, int ty, int color) {
        for (String l : KveGuiStyle.wrapTextSafe(font, text, tw())) {
            KveGuiStyle.drawTextSafe(g, l, x + PAD, ty, tw(), color);
            ty += font.lineHeight;
        }
        return ty;
    }

    private void drawStatus(GuiGraphics g, Font font) {
        if (status.isEmpty()) return;
        int sy = y + h - PAD - KveGuiStyle.BTN_H * 2 - 8 - font.lineHeight * 2;
        drawWrapped(g, font, status, sy, statusColor);
    }

    boolean contains(double mx, double my) { return mx >= x && mx < x + width() && my >= y && my < y + h; }

    private Box boxAt(double mx, double my) {
        for (Box b : boxes) if (b.hit(mx, my)) return b;
        return null;
    }

    /** Item bajo el mouse para el tooltip, o EMPTY. */
    ItemStack hovered(double mx, double my) {
        if (!open || data == null) return ItemStack.EMPTY;
        Box b = boxAt(mx, my);
        if (b == null || b.add) return ItemStack.EMPTY;
        return switch (b.kind) {
            case CENTER -> centerStack();
            case OUTPUT -> data.output;
            case INPUT -> inputAt(b.index);
            case MACHINE_IN -> emptyOr(slotAt(data.machineInputs, b.index));
            case MACHINE_OUT -> emptyOr(slotAt(data.machineOutputs, b.index));
        };
    }

    private static ItemStack emptyOr(RecipeData.MachineSlot s) {
        return s == null || isEmptySlot(s) ? ItemStack.EMPTY : displayOf(s);
    }

    /** Texto del tooltip para ranuras de estacion (nombre de la ranura, fluido, tag) o null. */
    String hoveredDetail(double mx, double my) {
        if (!open || data == null) return null;
        Box b = boxAt(mx, my);
        if (b != null && b.kind == Kind.CENTER) return Lang.get("gui.kve.recipe.quick.ritual_center");
        if (b != null && shape == Shape.RITUAL && b.kind == Kind.MACHINE_IN) {
            RecipeData.MachineSlot s = slotAt(data.machineInputs, b.index);
            String d = s == null || isEmptySlot(s) ? Lang.get("gui.kve.recipe.quick.slot_empty") : null;
            return d == null ? Lang.get("gui.kve.recipe.quick.ritual_offering") : Lang.get("gui.kve.recipe.quick.ritual_offering") + ": " + d;
        }
        if (b == null || (b.kind != Kind.MACHINE_IN && b.kind != Kind.MACHINE_OUT)) return null;
        RecipeData.MachineSlot s = slotAt(b.kind == Kind.MACHINE_IN ? data.machineInputs : data.machineOutputs, b.index);
        String slotName = b.def != null && b.def.label != null && !b.def.label.isBlank() ? b.def.label
                : b.def != null && b.def.id != null && !b.def.id.isBlank() ? b.def.id : null;
        String detail = null;
        if (s != null && s.type == RecipeData.MachineSlotType.FLUID && !isEmptySlot(s)) detail = s.fluidId + " · " + s.fluidAmount + " mB";
        else if (s != null && s.type == RecipeData.MachineSlotType.TAG && !isEmptySlot(s)) detail = "#" + s.tagId + " x" + s.tagCount;
        else if (s == null || isEmptySlot(s)) detail = Lang.get(isFluidDef(b.def) ? "gui.kve.recipe.quick.slot_fluid" : "gui.kve.recipe.quick.slot_empty");
        if (slotName == null) return detail;
        return detail == null ? slotName : slotName + ": " + detail;
    }

    /** Clic dentro del panel (true = consumido). Plegado: abre el panel. */
    boolean mouseClicked(Screen host, double mx, double my, int btn) {
        if (!contains(mx, my)) return false;
        if (!open) { open = true; reinit(host); return true; }
        if (data == null) return true;
        Box b = boxAt(mx, my);
        if (b == null) return false;
        if (btn == 1) { clearSlot(host, b); return true; }
        boolean machine = b.kind == Kind.MACHINE_IN || b.kind == Kind.MACHINE_OUT;
        if (machine) {
            RecipeData.MachineSlot s = slotAt(b.kind == Kind.MACHINE_IN ? data.machineInputs : data.machineOutputs, b.index);
            boolean fluid = s != null && !isEmptySlot(s) ? s.type == RecipeData.MachineSlotType.FLUID : isFluidDef(b.def);
            if (fluid) {
                String chem = b.def != null && b.def.isChemical() ? b.def.chemical : "";
                Minecraft.getInstance().setScreen(new FluidPickerScreen(host, id -> setFluid(b, id), chem));
                return true;
            }
        }
        boolean input = b.kind == Kind.INPUT || b.kind == Kind.MACHINE_IN || b.kind == Kind.CENTER;
        ItemPickerScreen picker = new ItemPickerScreen(host, s -> setSlot(b, s), true);
        Minecraft.getInstance().setScreen(input ? picker.withTagOption() : picker);
        return true;
    }

    private void setSlot(Box b, ItemStack picked) {
        if (picked == null || picked.isEmpty()) return;
        switch (b.kind) {
            case CENTER -> {
                if (centerMode == CenterMode.EXTRA_FIELD) {
                    String tag = TagIngredient.tagOf(picked);
                    JsonObject o = extraJson();
                    o.addProperty(centerField, tag != null ? "#" + tag : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(picked.getItem()).toString());
                    data.customExtraJson = o.toString();
                } else {
                    ensureSlot(data.machineInputs, 0);
                    data.machineInputs.set(0, machineSlotOf(picked));
                }
            }
            case OUTPUT -> data.output = withCount(picked, data.output.isEmpty() ? 1 : data.output.getCount());
            case INPUT -> {
                ItemStack in = picked.copy();
                if (TagIngredient.tagOf(in) == null) in.setCount(1);
                List<ItemStack> l = shape == Shape.GRID ? data.ingredients : data.shapelessIngredients;
                if (b.index < l.size()) l.set(b.index, in);
                else l.add(in);
            }
            case MACHINE_IN -> {
                RecipeData.MachineSlot slot = machineSlotOf(picked);
                // una ofrenda nueva del ritual ocupa el siguiente puesto del anillo
                if (shape == Shape.RITUAL && b.add) slot.ringSlot = b.index - firstOffering();
                if (shape == Shape.RITUAL && centerMode == CenterMode.INPUT_SLOT) ensureSlot(data.machineInputs, 0);
                RecipeData.MachineSlot old = ensureSlot(data.machineInputs, b.index);
                if (!(shape == Shape.RITUAL && b.add)) slot.ringSlot = old.ringSlot;
                data.machineInputs.set(b.index, slot);
            }
            case MACHINE_OUT -> {
                RecipeData.MachineSlot old = ensureSlot(data.machineOutputs, b.index);
                RecipeData.MachineSlot slot = new RecipeData.MachineSlot();
                slot.item = withCount(picked, old.type == RecipeData.MachineSlotType.ITEM && !old.item.isEmpty() ? old.item.getCount() : 1);
                slot.chance = old.chance;
                data.machineOutputs.set(b.index, slot);
                syncMachineOutput();
            }
        }
        dirty = true;
        setStatus("", 0);
    }

    /** Pone un fluido en la ranura, conservando la cantidad y la probabilidad que tenia. */
    private void setFluid(Box b, String fluidId) {
        if (fluidId == null || fluidId.isBlank()) return;
        List<RecipeData.MachineSlot> slots = b.kind == Kind.MACHINE_IN ? data.machineInputs : data.machineOutputs;
        RecipeData.MachineSlot old = ensureSlot(slots, b.index);
        RecipeData.MachineSlot slot = new RecipeData.MachineSlot();
        slot.type = RecipeData.MachineSlotType.FLUID;
        slot.fluidId = fluidId;
        slot.fluidAmount = old.type == RecipeData.MachineSlotType.FLUID && old.fluidAmount > 0 ? old.fluidAmount : 1000;
        slot.chance = old.chance;
        slot.ringSlot = old.ringSlot;
        slots.set(b.index, slot);
        if (b.kind == Kind.MACHINE_OUT) syncMachineOutput();
        dirty = true;
        setStatus("", 0);
    }

    /** Rueda sobre un fluido: cambia su cantidad de a 100 mB (Shift: de a 1000). */
    boolean mouseScrolled(Screen host, double mx, double my, double delta) {
        if (!open || data == null || !contains(mx, my)) return false;
        Box b = boxAt(mx, my);
        if (b == null || b.add || (b.kind != Kind.MACHINE_IN && b.kind != Kind.MACHINE_OUT)) return true;
        RecipeData.MachineSlot s = slotAt(b.kind == Kind.MACHINE_IN ? data.machineInputs : data.machineOutputs, b.index);
        if (s == null || s.type != RecipeData.MachineSlotType.FLUID || isEmptySlot(s)) return true;
        int step = Screen.hasShiftDown() ? 1000 : 100;
        s.fluidAmount = Math.max(1, Math.min(1_000_000, s.fluidAmount + (int) Math.signum(delta) * step));
        dirty = true;
        if (saveBtn != null) saveBtn.active = true;
        if (undoBtn != null) undoBtn.active = true;
        return true;
    }

    /** Rellena con ranuras vacias hasta {@code index}, como hace el editor de maquinas. */
    private static RecipeData.MachineSlot ensureSlot(List<RecipeData.MachineSlot> slots, int index) {
        while (slots.size() <= index) slots.add(new RecipeData.MachineSlot());
        return slots.get(index);
    }

    private static void trimTrailingEmpty(List<RecipeData.MachineSlot> slots) {
        while (!slots.isEmpty() && isEmptySlot(slots.get(slots.size() - 1))) slots.remove(slots.size() - 1);
    }

    private static ItemStack withCount(ItemStack picked, int count) {
        ItemStack out = picked.copy();
        out.setCount(Math.max(1, Math.min(out.getMaxStackSize(), count)));
        return out;
    }

    private static RecipeData.MachineSlot machineSlotOf(ItemStack picked) {
        RecipeData.MachineSlot slot = new RecipeData.MachineSlot();
        String tag = TagIngredient.tagOf(picked);
        if (tag != null) {
            slot.type = RecipeData.MachineSlotType.TAG;
            slot.tagId = tag;
            slot.tagCount = 1;
        } else {
            slot.item = picked.copy();
            slot.item.setCount(1);
        }
        return slot;
    }

    private void clearSlot(Screen host, Box b) {
        if (b.add) return;
        switch (b.kind) {
            case CENTER -> {
                if (centerMode == CenterMode.EXTRA_FIELD) {
                    JsonObject o = extraJson();
                    o.remove(centerField);
                    data.customExtraJson = o.size() == 0 ? "" : o.toString();
                } else if (!data.machineInputs.isEmpty()) {
                    data.machineInputs.set(0, new RecipeData.MachineSlot());
                }
            }
            case INPUT -> {
                if (shape == Shape.GRID) data.ingredients.set(b.index, ItemStack.EMPTY);
                else if (b.index < data.shapelessIngredients.size()) data.shapelessIngredients.remove(b.index);
                else return;
            }
            case MACHINE_IN, MACHINE_OUT -> {
                List<RecipeData.MachineSlot> slots = b.kind == Kind.MACHINE_IN ? data.machineInputs : data.machineOutputs;
                if (b.index >= slots.size()) return;
                // las ranuras fijas quedan vacias (no se corren las demas); las repetibles se quitan
                if (b.def != null && !b.def.repeatable) slots.set(b.index, new RecipeData.MachineSlot());
                else slots.remove(b.index);
                // en el ritual el centro (entrada 0) no se recorta aunque este vacio
                if (!(shape == Shape.RITUAL && centerMode == CenterMode.INPUT_SLOT && slots == data.machineInputs && slots.size() <= 1)) trimTrailingEmpty(slots);
                if (b.kind == Kind.MACHINE_OUT) syncMachineOutput();
            }
            default -> { return; }
        }
        dirty = true;
        reinit(host);
    }
}
