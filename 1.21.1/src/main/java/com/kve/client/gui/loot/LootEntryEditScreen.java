package com.kve.client.gui.loot;

import com.kve.client.gui.properties.ItemAttributeScreen;
import com.kve.client.gui.picker.ItemPickerScreen;
import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KveScaledEditBox;

import org.lwjgl.glfw.GLFW;
import com.kve.common.Lang;
import com.kve.common.kubejs.LootTableExporter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class LootEntryEditScreen extends Screen {

    private static final int GUI_W = 230;
    private static final int GUI_H = 220;

    private final Screen parent;
    private final Consumer<LootTableExporter.LootEntryData> callback;
    private final LootTableExporter.LootEntryData draft;

    private int L, T;
    private EditBox fWeight, fMin, fMax;
    private EditBox fLevelsMin, fLevelsMax, fNbt;
    private int currentFnIdx = 0;
    private String flashMsg = "";

    // mismo hueco y mismo mecanismo que
    // MobEquipmentBuildScreen#itemBtn - este boton tampoco muestra el
    // icono del item, asi que sin esto no habia forma de pasar el mouse
    // por encima y ver su tooltip real (encantamientos/lore) antes de
    // decidir si re-elegirlo. Se recrea en cada init(), por eso se guarda
    // la referencia en vez de calcular su rectangulo a mano en render().
    private Button itemBtn;

    public LootEntryEditScreen(Screen parent, LootTableExporter.LootEntryData existing,
                                Consumer<LootTableExporter.LootEntryData> callback) {
        super(Component.literal("Entrada de Loot"));
        this.parent = parent;
        this.callback = callback;
        this.draft = existing != null ? existing.copy() : new LootTableExporter.LootEntryData();
    }

    @Override
    protected void init() {
        L = (width - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        clearWidgets();
        setFocused(null);
        if (currentFnIdx >= draft.functions.size()) currentFnIdx = Math.max(0, draft.functions.size() - 1);

        boolean hasItem = draft.itemId != null && !draft.itemId.isEmpty();
        int editItemW = hasItem ? 46 : 0;
        itemBtn = btn(itemLabel(), L + 6, T + 16, 0, this::pickItem);
        addRenderableWidget(itemBtn);
        if (hasItem) {
            addRenderableWidget(btn(Lang.get("gui.kve.loot.edit_item_btn"), L + 6 + GUI_W - 12 - editItemW, T + 16, 0, this::editItemInEditor));
        }

        KveGuiStyle.Layout ly = new KveGuiStyle.Layout(L + 6, T + 32, 15);
        addRenderableWidget(labelField(ly.x(), ly.row(), draft.weight)); fWeight = lastField;
        addRenderableWidget(labelField(ly.x(), ly.row(), draft.minCount)); fMin = lastField;
        addRenderableWidget(labelField(ly.x(), ly.row(), draft.maxCount)); fMax = lastField;

        ly.gap(4);
        int ay = ly.row(16);
        KveGuiStyle.ButtonRow fnRow = new KveGuiStyle.ButtonRow(L + 6, ay, L + GUI_W - 6);
        addRenderableWidget(fnRow.add(0, "< Fn", b -> prevFn()));

        String fnCountLabel = "Functions " + (draft.functions.isEmpty() ? "0/0" :
                (currentFnIdx + 1) + "/" + draft.functions.size());
        addRenderableWidget(fnRow.add(0, fnCountLabel, b -> {}));

        addRenderableWidget(fnRow.add(0, "Fn >", b -> nextFn()));
        addRenderableWidget(fnRow.add(20, "+", b -> addFn()));
        if (!draft.functions.isEmpty()) addRenderableWidget(fnRow.add(20, "-", b -> removeFn()));

        ly.reset(fnRow.bottomY());

        if (!draft.functions.isEmpty()) {
            LootTableExporter.LootFunctionData fn = draft.functions.get(currentFnIdx);
            addRenderableWidget(btn("Tipo: " + fn.type.label(), L + 6, ly.row(16), 0, this::cycleFnType));
            switch (fn.type) {
                case ENCHANT_WITH_LEVELS -> {
                    int fy = ly.row(15);
                    fLevelsMin = fieldAt(L + 60, fy, fn.levelsMin);
                    fLevelsMax = fieldAt(L + 140, fy, fn.levelsMax);
                    addRenderableWidget(btn(fn.treasure ? "Treasure: si" : "Treasure: no", L + 6, ly.y(), 0, this::toggleTreasure));
                    fNbt = null;
                }
                case LOOTING_ENCHANT -> {
                    int fy = ly.row(15);
                    fLevelsMin = fieldAt(L + 60, fy, fn.levelsMin);
                    fLevelsMax = fieldAt(L + 140, fy, fn.levelsMax);
                    fNbt = null;
                }
                case SET_NBT -> {
                    fNbt = new KveScaledEditBox(font, L + 6, ly.y(), GUI_W - 12, 12, Component.literal("nbt"));
                    fNbt.setMaxLength(256);
                    fNbt.setValue(fn.nbt);
                    addRenderableWidget(fNbt);
                    fLevelsMin = null; fLevelsMax = null;
                }
                case SMELT -> {
                    addRenderableWidget(btn(fn.smeltOnlyIfOnFire
                            ? "Solo si murio en fuego: SI" : "Solo si murio en fuego: NO",
                            L + 6, ly.row(15), 0, this::toggleSmeltOnFire));
                    fLevelsMin = null; fLevelsMax = null; fNbt = null;
                }
            }
        } else {
            fLevelsMin = null; fLevelsMax = null; fNbt = null;
        }

        {
            KveGuiStyle.ButtonRow confirmRow = new KveGuiStyle.ButtonRow(L + 6, T + GUI_H - 16, L + GUI_W - 6);
            addRenderableWidget(confirmRow.add(0, "Confirmar", b -> confirm()));
            addRenderableWidget(confirmRow.add(0, "Cancelar", b -> minecraft.setScreen(parent)));
        }
    }

    /** ESC returns to the caller instead of closing the whole GUI. */
    @Override
    public void onClose() { minecraft.setScreen(parent); }

    private String itemLabel() {
        if (draft.itemId == null || draft.itemId.isEmpty()) return "Elegir item...";
        String n = draft.itemId.contains(":") ? draft.itemId.split(":")[1] : draft.itemId;
        return "Item: " + n;
    }

    private void pickItem() {
        minecraft.setScreen(new ItemPickerScreen(this, stack -> {
            ResourceLocation k = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (k != null) draft.itemId = k.toString();
        }));
    }

    private void editItemInEditor() {
        if (draft.itemId == null || draft.itemId.isEmpty()) return;
        minecraft.setScreen(new ItemAttributeScreen(draft.itemId).withReturnScreen(this));
    }

    /** Preview ItemStack para el tooltip de {@link #itemBtn} - sin NBT porque LootEntryData no guarda ninguno para el item en si (solo id/weight/min/max + functions, ver LootTableExporter.LootEntryData). */
    private ItemStack itemStackForTooltip() {
        if (draft.itemId == null || draft.itemId.isEmpty()) return ItemStack.EMPTY;
        ResourceLocation rl = ResourceLocation.tryParse(draft.itemId);
        net.minecraft.world.item.Item item = rl != null ? BuiltInRegistries.ITEM.get(rl) : net.minecraft.world.item.Items.AIR;
        return new ItemStack(item);
    }

    private EditBox lastField;

    private EditBox labelField(int x, int y, int value) {
        EditBox b = new KveScaledEditBox(font, x + 90, y, 80, 12, Component.empty());
        b.setMaxLength(6);
        b.setValue(String.valueOf(value));
        lastField = b;
        return b;
    }

    private EditBox fieldAt(int x, int y, int value) {
        EditBox b = new KveScaledEditBox(font, x, y, 60, 12, Component.empty());
        b.setMaxLength(6);
        b.setValue(String.valueOf(value));
        addRenderableWidget(b);
        return b;
    }

    private void syncCurrentFn() {
        if (draft.functions.isEmpty()) return;
        LootTableExporter.LootFunctionData fn = draft.functions.get(currentFnIdx);
        if (fLevelsMin != null && fLevelsMax != null) {
            try { fn.levelsMin = Integer.parseInt(fLevelsMin.getValue().trim()); } catch (Exception ignored) {}
            try { fn.levelsMax = Integer.parseInt(fLevelsMax.getValue().trim()); } catch (Exception ignored) {}
        }
        if (fNbt != null) fn.nbt = fNbt.getValue();
    }

    private void prevFn() { syncCurrentFn(); currentFnIdx = Math.max(0, currentFnIdx - 1); init(); }
    private void nextFn() { syncCurrentFn(); currentFnIdx = Math.min(Math.max(0, draft.functions.size() - 1), currentFnIdx + 1); init(); }
    private void addFn() {
        syncCurrentFn();
        draft.functions.add(new LootTableExporter.LootFunctionData());
        currentFnIdx = draft.functions.size() - 1;
        init();
    }
    private void removeFn() {
        if (draft.functions.isEmpty()) return;
        draft.functions.remove(currentFnIdx);
        currentFnIdx = Math.max(0, currentFnIdx - 1);
        init();
    }
    private void cycleFnType() {
        syncCurrentFn();
        LootTableExporter.LootFunctionData fn = draft.functions.get(currentFnIdx);
        fn.type = fn.type.next();
        init();
    }
    private void toggleTreasure() {
        syncCurrentFn();
        draft.functions.get(currentFnIdx).treasure = !draft.functions.get(currentFnIdx).treasure;
        init();
    }
    private void toggleSmeltOnFire() {
        syncCurrentFn();
        LootTableExporter.LootFunctionData fn = draft.functions.get(currentFnIdx);
        fn.smeltOnlyIfOnFire = !fn.smeltOnlyIfOnFire;
        init();
    }

    private void confirm() {
        try {
            if (draft.itemId == null || draft.itemId.isEmpty()) { flash("Elige un item primero."); return; }
            int w = Math.max(1, Integer.parseInt(fWeight.getValue().trim()));
            int mn = Math.max(1, Integer.parseInt(fMin.getValue().trim()));
            int mx = Math.max(1, Integer.parseInt(fMax.getValue().trim()));
            if (mn > mx) { flash("Cant. minima no puede ser mayor que la maxima."); return; }
            syncCurrentFn();
            draft.weight = w; draft.minCount = mn; draft.maxCount = mx;
            callback.accept(draft);
            minecraft.setScreen(parent);
        } catch (NumberFormatException ex) {
            flash("Los campos numericos deben ser enteros validos.");
        }
    }

    private void flash(String msg) { flashMsg = msg; }

    private Button btn(String label, int x, int y, int w, Runnable a) {
        return KveGuiStyle.smartBtn(x, y, w, label, b -> a.run());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, "Entrada de Loot");

        KveGuiStyle.Layout ly = new KveGuiStyle.Layout(L + 6, T + 32, 15);
        KveGuiStyle.drawTextSafe(g, "Peso:", L + 6, ly.row() + 2, 84, KveGuiStyle.TEXT_LABEL);
        KveGuiStyle.drawTextSafe(g, "Min:",  L + 6, ly.row() + 2, 84, KveGuiStyle.TEXT_LABEL);
        KveGuiStyle.drawTextSafe(g, "Max:",  L + 6, ly.row() + 2, 84, KveGuiStyle.TEXT_LABEL); ly.gap(4 + 16);

        if (!draft.functions.isEmpty()) {
            LootTableExporter.LootFunctionData fn = draft.functions.get(Math.min(currentFnIdx, draft.functions.size() - 1));
            ly.gap(16);
            switch (fn.type) {
                case ENCHANT_WITH_LEVELS -> KveGuiStyle.drawTextSafe(g, "Niveles:", L + 6, ly.y() + 2, 50, KveGuiStyle.TEXT_LABEL);
                case LOOTING_ENCHANT -> KveGuiStyle.drawTextSafe(g, "Cantidad:", L + 6, ly.y() + 2, 50, KveGuiStyle.TEXT_LABEL);
                case SET_NBT -> KveGuiStyle.drawTextSafe(g, "NBT (SNBT):", L + 6, ly.y() - 10, GUI_W - 12, KveGuiStyle.TEXT_LABEL);
                case SMELT -> {}
            }
        } else {
            KveGuiStyle.drawTextSafe(g, "(sin functions - opcional)", L + 6, ly.y() + 2, GUI_W - 12, KveGuiStyle.TEXT_MUTED);
        }

        if (!flashMsg.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, flashMsg, L + 6, T + GUI_H - 30, GUI_W - 12, 0xFFFF8888);
        }

        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
        drawItemBtnTooltip(g, mx, my);
    }

    private void drawItemBtnTooltip(GuiGraphics g, int mx, int my) {
        if (itemBtn == null || mx < itemBtn.getX() || mx >= itemBtn.getX() + itemBtn.getWidth()
                || my < itemBtn.getY() || my >= itemBtn.getY() + itemBtn.getHeight()) return;
        boolean hasItem = draft.itemId != null && !draft.itemId.isEmpty();
        java.util.List<Component> lines = new java.util.ArrayList<>();
        if (hasItem) {
            lines.addAll(getTooltipFromItem(minecraft, itemStackForTooltip()));
            lines.add(Component.empty());
        }
        lines.add(KveGuiStyle.slotHint(KveGuiStyle.HINT_CLICK_CHANGE));
        g.renderComponentTooltip(font, lines, mx, my);
    }

    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (getFocused() != null && getFocused().keyPressed(key, scan, mods)) return true;
        if (com.kve.client.gui.EditorKeys.isFocusTab(key, mods)) { super.keyPressed(key, scan, mods); return true; }
        return com.kve.client.gui.EditorKeys.swallow(mods);
    }
    @Override public boolean isPauseScreen() { return false; }
}