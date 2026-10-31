package com.kve.client.gui;

import org.lwjgl.glfw.GLFW;
import com.kve.common.Lang;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Editor visual de NBT para un ItemStack individual: encantamientos,
 * datos numericos comunes (damage, energy, burn time, etc. via campos
 * personalizados), un contenedor de fluido generico, y un editor de
 * texto NBT/SNBT crudo con validacion. Pensado para usarse desde el
 * grid de recetas (CraftEditorScreen) y desde el selector de items
 * (ItemPickerScreen), de forma que las recetas puedan usar items con
 * encantamientos, energia (RF/FE), fluidos o datos custom de otros mods
 * (AE2, Mekanism, etc.) en vez de perder esa informacion.
 */
@OnlyIn(Dist.CLIENT)
public class NbtEditorScreen extends Screen {

    private static final int GUI_W_MAX = 460;
    private static final int GUI_H_MAX = 280;
    private int GUI_W = GUI_W_MAX;
    private int GUI_H = GUI_H_MAX;
    private static final int NUM_LABEL_W = 116;
    private static final int FLUID_LABEL_W = 96;
    private static final int PREV_W = 108;

    private static final int ROWS_VISIBLE = 6;
    private static final int ROW_H = 15;

    private enum Tab { ENCHANTMENTS, NUMERIC, FLUID, RAW }

    private static final String[] RESERVED_NUMERIC_KEYS =
            { "Damage", "Unbreakable", "CustomModelData", "RepairCost" };

    // Bounds for the cheap pre-flight probe run before ever calling
    // CompoundTag#toString() on an item's tag for the Raw tab - see
    // isSafeToStringify(). Kept comfortably above what any normal item
    // needs (a handful to a few hundred NBT nodes) but well short of what
    // would actually hitch the render thread or risk a stack overflow.
    private static final int RAW_MAX_CHARS = 8000;
    private static final int RAW_SANITY_NODE_LIMIT = 20_000;
    private static final int RAW_SANITY_DEPTH_LIMIT = 64;

    private final Screen parent;
    private final ItemStack original;
    private final ItemStack workingStack;
    private final Consumer<ItemStack> onSave;

    private int L, T;
    private int tabContentY;
    private Tab activeTab = Tab.ENCHANTMENTS;

    private final Map<Enchantment, Integer> draftEnchants = new LinkedHashMap<>();
    private boolean enchantsLoaded = false;
    private ScrollBar enchantScrollBar;

    private EditBox fDamage, fCustomModelData, fRepairCost;
    private String draftDamage = "", draftCustomModelData = "", draftRepairCost = "";
    private boolean draftUnbreakable = false;
    private boolean numericLoaded = false;

    private static class NumRow {
        String key;
        String type;
        String value;
        NumRow(String key, String type, String value) { this.key = key; this.type = type; this.value = value; }
    }
    private final List<NumRow> numRows = new ArrayList<>();
    private final Set<String> trackedCustomKeys = new HashSet<>();
    private ScrollBar numericScrollBar;

    private EditBox fFluidKey, fFluidId, fFluidAmount;
    private String draftFluidKey = "Fluid";
    private String draftFluidId = "";
    private String draftFluidAmount = "1000";
    private String trackedFluidKey = null;
    private boolean fluidLoaded = false;

    private RawNbtTextArea fRawNbt;
    private String rawErrorMsg = "";
    private boolean rawTooLargeToEdit = false;

    private String flashMsg = "";
    private long flashAt = 0;

    private int listHeaderY, listTop, fieldsY, rawTop, rawBoxH;
    private int listHeaderBtnX;
    private List<String> numericProblems = List.of();

    public NbtEditorScreen(Screen parent, ItemStack stack, Consumer<ItemStack> onSave) {
        super(Component.literal("Editor NBT"));
        this.parent = parent;
        this.original = stack == null ? ItemStack.EMPTY : stack;
        this.workingStack = this.original.isEmpty() ? ItemStack.EMPTY : this.original.copy();
        this.onSave = onSave;
    }

    @Override
    protected void init() {
        GUI_W = Math.min(GUI_W_MAX, width - 8);
        GUI_H = Math.min(GUI_H_MAX, height - 8);
        L = (width - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        clearWidgets();
        setFocused(null);

        addRenderableWidget(btn("X", L + GUI_W - 17, T + 2, 15, 11, this::cancel));
        if (workingStack.isEmpty()) return;

        if (!enchantsLoaded) { loadEnchantsFromTag(); enchantsLoaded = true; }
        if (!numericLoaded)  { loadNumericFromTag();  numericLoaded  = true; }
        if (!fluidLoaded)    { loadFluidFromTag();     fluidLoaded    = true; }

        KveGuiStyle.ButtonRow tabRow = new KveGuiStyle.ButtonRow(cx(), T + 17, cr());
        addTab(tabRow, Tab.ENCHANTMENTS, "gui.kve.nbt.tab.enchant");
        addTab(tabRow, Tab.NUMERIC, "gui.kve.nbt.tab.numeric");
        addTab(tabRow, Tab.FLUID, "gui.kve.nbt.tab.fluid");
        addTab(tabRow, Tab.RAW, "gui.kve.nbt.tab.raw");
        tabContentY = tabRow.bottomY() + 8;

        switch (activeTab) {
            case ENCHANTMENTS -> initEnchantTab();
            case NUMERIC      -> initNumericTab();
            case FLUID        -> initFluidTab();
            case RAW          -> initRawTab();
        }

        addRenderableWidget(btn(Lang.get("gui.kve.nbt.btn.save"), cx(), bottomY(), 0, this::save));
        String clearL = Lang.get("gui.kve.nbt.btn.clear");
        addRenderableWidget(btn(clearL, cr() - KveGuiStyle.btnWidth(clearL), bottomY(), 0, this::clearAllNbt));
    }

    private void addTab(KveGuiStyle.ButtonRow row, Tab tab, String key) {
        addRenderableWidget(row.add(0, Lang.get(key), b -> switchTab(tab)).withSelected(activeTab == tab));
    }

    private int cx() { return L + PREV_W + 8; }
    private int cr() { return L + GUI_W - 8; }
    private int bottomY() { return T + GUI_H - 20; }
    private int contentBottom() { return bottomY() - 16; }
    private int rowsFor(int top) { return Math.max(1, (contentBottom() - top) / ROW_H); }

    private void switchTab(Tab t) { activeTab = t; init(); }

    private void loadEnchantsFromTag() {
        draftEnchants.clear();
        draftEnchants.putAll(EnchantmentHelper.getEnchantments(workingStack));
    }

    private void loadNumericFromTag() {
        numRows.clear();
        trackedCustomKeys.clear();
        CompoundTag tag = workingStack.getTag();

        draftDamage = tag != null && tag.contains("Damage") ? String.valueOf(tag.getInt("Damage")) : "";
        draftCustomModelData = tag != null && tag.contains("CustomModelData") ? String.valueOf(tag.getInt("CustomModelData")) : "";
        draftRepairCost = tag != null && tag.contains("RepairCost") ? String.valueOf(tag.getInt("RepairCost")) : "";
        draftUnbreakable = tag != null && tag.contains("Unbreakable") && tag.getBoolean("Unbreakable");

        if (tag == null) return;
        for (String key : tag.getAllKeys()) {
            if (isReservedKey(key)) continue;
            Tag t = tag.get(key);
            if (t instanceof NumericTag num) {
                String type = numericTypeOf(t);
                numRows.add(new NumRow(key, type, formatNumeric(num, type)));
                trackedCustomKeys.add(key);
            }
        }
    }

    private void loadFluidFromTag() {
        draftFluidKey = "Fluid";
        draftFluidId = "";
        draftFluidAmount = "1000";
        trackedFluidKey = null;
        CompoundTag tag = workingStack.getTag();
        if (tag != null && tag.contains(draftFluidKey, Tag.TAG_COMPOUND)) {
            CompoundTag fluidTag = tag.getCompound(draftFluidKey);
            if (fluidTag.contains("FluidName")) draftFluidId = fluidTag.getString("FluidName");
            if (fluidTag.contains("Amount")) draftFluidAmount = String.valueOf(fluidTag.getLong("Amount"));
            trackedFluidKey = draftFluidKey;
        }
    }

    private boolean isReservedKey(String key) {
        if (key.equals("Enchantments") || key.equals("StoredEnchantments")) return true;
        for (String r : RESERVED_NUMERIC_KEYS) if (r.equals(key)) return true;
        if (key.equals(currentFluidKey())) return true;
        return false;
    }

    private String currentFluidKey() {
        String k = draftFluidKey == null ? "" : draftFluidKey.trim();
        return k.isEmpty() ? "Fluid" : k;
    }

    private void initEnchantTab() {
        int cr = cr();
        List<Enchantment> keys = new ArrayList<>(draftEnchants.keySet());
        listHeaderY = tabContentY;
        String addL = Lang.get("gui.kve.nbt.enchant.add");
        listHeaderBtnX = cr - KveGuiStyle.btnWidth(addL);
        addRenderableWidget(btn(addL, listHeaderBtnX, listHeaderY, 0, this::openEnchantPicker));
        listTop = listHeaderY + 18;
        int rows = rowsFor(listTop);
        int keep = enchantScrollBar == null ? 0 : enchantScrollBar.getScroll();
        enchantScrollBar = new ScrollBar(cr - 6, listTop, rows * ROW_H, keys.size(), rows);
        enchantScrollBar.setScroll(keep);
        int scroll = enchantScrollBar.getScroll();
        int xBtn = cr - 22;
        for (int i = 0; i < rows && i + scroll < keys.size(); i++) {
            Enchantment ench = keys.get(i + scroll);
            int ry = listTop + i * ROW_H;
            addRenderableWidget(btn("-", xBtn - 28, ry, 12, () -> changeEnchantLevel(ench, -1)));
            addRenderableWidget(btn("+", xBtn - 14, ry, 12, () -> changeEnchantLevel(ench, 1)));
            addRenderableWidget(btn("x", xBtn, ry, 12, () -> removeEnchant(ench)));
        }
    }

    private void openEnchantPicker() {
        minecraft.setScreen(new EnchantmentPickerScreen(this, ench -> {
            int lvl = draftEnchants.getOrDefault(ench, 0);
            draftEnchants.put(ench, Math.max(1, lvl == 0 ? 1 : lvl));
            applyEnchants();
        }));
    }

    private void changeEnchantLevel(Enchantment ench, int delta) {
        int cur = draftEnchants.getOrDefault(ench, 1);
        int next = Math.max(1, Math.min(255, cur + delta));
        draftEnchants.put(ench, next);
        applyEnchants();
    }

    private void removeEnchant(Enchantment ench) {
        draftEnchants.remove(ench);
        applyEnchants();
    }

    private void applyEnchants() {
        CompoundTag tag = workingStack.getOrCreateTag();
        String key = workingStack.is(Items.ENCHANTED_BOOK) ? "StoredEnchantments" : "Enchantments";

        if (draftEnchants.isEmpty()) {
            tag.remove(key);
        } else {
            ListTag list = new ListTag();
            for (Map.Entry<Enchantment, Integer> entry : draftEnchants.entrySet()) {
                ResourceLocation id = ForgeRegistries.ENCHANTMENTS.getKey(entry.getKey());
                if (id == null) continue;
                CompoundTag entryTag = new CompoundTag();
                entryTag.putString("id", id.toString());
                entryTag.putShort("lvl", entry.getValue().shortValue());
                list.add(entryTag);
            }
            tag.put(key, list);
        }
        tidyTag();
        init();
    }

    private void initNumericTab() {
        int cx = cx(), cr = cr();
        int fx = cx + NUM_LABEL_W;
        fieldsY = tabContentY;
        int y = fieldsY;

        fDamage = field(fx, y, 70);
        fDamage.setValue(draftDamage);
        fDamage.setHint(Component.literal("0"));
        fDamage.setResponder(s -> { draftDamage = s; numericProblems = commitNumericFields(); });
        addRenderableWidget(fDamage);
        String unbL = draftUnbreakable ? Lang.get("gui.kve.nbt.unbreak.on") : Lang.get("gui.kve.nbt.unbreak.off");
        addRenderableWidget(btn(unbL, cr - KveGuiStyle.btnWidth(unbL), y - 1, 0, this::toggleUnbreakable));

        y += 16;
        fCustomModelData = field(fx, y, 70);
        fCustomModelData.setValue(draftCustomModelData);
        fCustomModelData.setResponder(s -> { draftCustomModelData = s; numericProblems = commitNumericFields(); });
        addRenderableWidget(fCustomModelData);

        y += 16;
        fRepairCost = field(fx, y, 70);
        fRepairCost.setValue(draftRepairCost);
        fRepairCost.setResponder(s -> { draftRepairCost = s; numericProblems = commitNumericFields(); });
        addRenderableWidget(fRepairCost);

        listHeaderY = y + 22;
        String addL = Lang.get("gui.kve.nbt.numeric.add");
        listHeaderBtnX = cr - KveGuiStyle.btnWidth(addL);
        addRenderableWidget(btn(addL, listHeaderBtnX, listHeaderY, 0, this::addCustomNumericRow));
        listTop = listHeaderY + 18;
        int rows = rowsFor(listTop);
        int keep = numericScrollBar == null ? 0 : numericScrollBar.getScroll();
        numericScrollBar = new ScrollBar(cr - 6, listTop, rows * ROW_H, numRows.size(), rows);
        numericScrollBar.setScroll(keep);
        int scroll = numericScrollBar.getScroll();

        int keyW = 96, typeW = 46;
        int typeX = cx + keyW + 4;
        int valX = typeX + typeW + 4;
        int xBtn = cr - 22;
        for (int i = 0; i < rows && i + scroll < numRows.size(); i++) {
            NumRow row = numRows.get(i + scroll);
            int ry = listTop + i * ROW_H;
            EditBox kBox = new KveScaledEditBox(font, cx, ry + 1, keyW, 11, Component.literal("key"));
            kBox.setMaxLength(48);
            kBox.setValue(row.key);
            kBox.setResponder(s -> { row.key = s; numericProblems = commitNumericFields(); });
            addRenderableWidget(kBox);
            addRenderableWidget(KveGuiStyle.clampedBtn(typeX, ry, typeW, row.type, b -> cycleNumType(row)));
            EditBox vBox = new KveScaledEditBox(font, valX, ry + 1, Math.max(30, xBtn - 4 - valX), 11, Component.literal("value"));
            vBox.setMaxLength(32);
            vBox.setValue(row.value);
            vBox.setResponder(s -> { row.value = s; numericProblems = commitNumericFields(); });
            addRenderableWidget(vBox);
            addRenderableWidget(btn("x", xBtn, ry, 12, () -> removeCustomNumericRow(row)));
        }
    }

    private void addCustomNumericRow() {
        String base = "custom_field";
        String key = base;
        int n = 1;
        while (numRowKeyTaken(key) || isReservedKey(key)) key = base + "_" + (n++);
        numRows.add(new NumRow(key, "INT", "0"));
        numericProblems = commitNumericFields();
        init();
    }

    private boolean numRowKeyTaken(String key) {
        for (NumRow r : numRows) if (r.key.equals(key)) return true;
        return false;
    }

    private void cycleNumType(NumRow row) {
        String[] order = {"BYTE", "SHORT", "INT", "LONG", "FLOAT", "DOUBLE"};
        int idx = 0;
        for (int i = 0; i < order.length; i++) if (order[i].equals(row.type)) idx = i;
        row.type = order[(idx + 1) % order.length];
        numericProblems = commitNumericFields();
        init();
    }

    private void removeCustomNumericRow(NumRow row) {
        numRows.remove(row);
        numericProblems = commitNumericFields();
        init();
    }

    private void toggleUnbreakable() {
        draftUnbreakable = !draftUnbreakable;
        CompoundTag tag = workingStack.getOrCreateTag();
        if (draftUnbreakable) tag.putBoolean("Unbreakable", true);
        else tag.remove("Unbreakable");
        tidyTag();
        init();
    }

    private List<String> commitNumericFields() {
        CompoundTag tag = workingStack.getOrCreateTag();
        List<String> problems = new ArrayList<>();

        applyIntFieldOrRemove(tag, "Damage", draftDamage, problems);
        applyIntFieldOrRemove(tag, "CustomModelData", draftCustomModelData, problems);
        applyIntFieldOrRemove(tag, "RepairCost", draftRepairCost, problems);

        Set<String> seenKeys = new HashSet<>();
        Set<String> validKeys = new HashSet<>();
        for (NumRow row : numRows) {
            String key = row.key == null ? "" : row.key.trim();
            if (key.isBlank()) continue;
            if (isReservedKey(key)) { problems.add(key + " (nombre reservado)"); continue; }
            if (!seenKeys.add(key)) { problems.add(key + " (clave duplicada)"); continue; }
            try {
                putNumeric(tag, key, row.type, row.value);
                validKeys.add(key);
            } catch (NumberFormatException ex) {
                problems.add(key + " = '" + row.value + "'");
            }
        }

        for (String staleKey : trackedCustomKeys) {
            if (!validKeys.contains(staleKey) && tag.get(staleKey) instanceof NumericTag) {
                tag.remove(staleKey);
            }
        }
        trackedCustomKeys.clear();
        trackedCustomKeys.addAll(validKeys);

        tidyTag();
        return problems;
    }

    private void applyIntFieldOrRemove(CompoundTag tag, String key, String text, List<String> problems) {
        String v = text == null ? "" : text.trim();
        if (v.isEmpty()) { tag.remove(key); return; }
        try {
            tag.putInt(key, Integer.parseInt(v));
        } catch (NumberFormatException ex) {
            problems.add(key + " = '" + v + "'");
        }
    }

    private void putNumeric(CompoundTag tag, String key, String type, String value) {
        switch (type) {
            case "BYTE"   -> tag.putByte(key, Byte.parseByte(value.trim()));
            case "SHORT"  -> tag.putShort(key, Short.parseShort(value.trim()));
            case "INT"    -> tag.putInt(key, Integer.parseInt(value.trim()));
            case "LONG"   -> tag.putLong(key, Long.parseLong(value.trim()));
            case "FLOAT"  -> tag.putFloat(key, Float.parseFloat(value.trim()));
            case "DOUBLE" -> tag.putDouble(key, Double.parseDouble(value.trim()));
            default -> tag.putInt(key, Integer.parseInt(value.trim()));
        }
    }

    private String numericTypeOf(Tag t) {
        if (t instanceof ByteTag) return "BYTE";
        if (t instanceof ShortTag) return "SHORT";
        if (t instanceof IntTag) return "INT";
        if (t instanceof LongTag) return "LONG";
        if (t instanceof FloatTag) return "FLOAT";
        if (t instanceof DoubleTag) return "DOUBLE";
        return "INT";
    }

    private String formatNumeric(NumericTag t, String type) {
        return switch (type) {
            case "BYTE" -> String.valueOf(t.getAsByte());
            case "SHORT" -> String.valueOf(t.getAsShort());
            case "LONG" -> String.valueOf(t.getAsLong());
            case "FLOAT" -> String.valueOf(t.getAsFloat());
            case "DOUBLE" -> String.valueOf(t.getAsDouble());
            default -> String.valueOf(t.getAsInt());
        };
    }

    private void initFluidTab() {
        int cx = cx(), cr = cr();
        int fx = cx + FLUID_LABEL_W;
        fieldsY = tabContentY;
        int y = fieldsY;

        fFluidKey = field(fx, y, 90);
        fFluidKey.setMaxLength(32);
        fFluidKey.setValue(draftFluidKey);
        fFluidKey.setResponder(s -> { draftFluidKey = s; commitFluidField(); });
        addRenderableWidget(fFluidKey);

        y += 18;
        String pickL = Lang.get("gui.kve.nbt.fluid.pick");
        int pickW = KveGuiStyle.btnWidth(pickL);
        fFluidId = field(fx, y, Math.max(60, cr - pickW - 4 - fx));
        fFluidId.setMaxLength(64);
        fFluidId.setValue(draftFluidId);
        fFluidId.setHint(Component.literal("minecraft:water"));
        fFluidId.setResponder(s -> { draftFluidId = s; commitFluidField(); });
        addRenderableWidget(fFluidId);
        addRenderableWidget(btn(pickL, cr - pickW, y - 1, 0, this::openFluidPicker));

        y += 18;
        fFluidAmount = field(fx, y, 70);
        fFluidAmount.setValue(draftFluidAmount);
        fFluidAmount.setResponder(s -> { draftFluidAmount = s; commitFluidField(); });
        addRenderableWidget(fFluidAmount);

        listHeaderY = y + 22;
        addRenderableWidget(btn(Lang.get("gui.kve.nbt.fluid.remove"), cx, listHeaderY, 0, this::removeFluidTab));
    }

    private void openFluidPicker() {
        minecraft.setScreen(new FluidPickerScreen(this, id -> {
            draftFluidId = id;
            if (fFluidId != null) fFluidId.setValue(id);
            commitFluidField();
        }));
    }

    private boolean commitFluidField() {
        String id = draftFluidId == null ? "" : draftFluidId.trim();
        String key = draftFluidKey == null || draftFluidKey.isBlank() ? "Fluid" : draftFluidKey.trim();
        CompoundTag tag = workingStack.getOrCreateTag();

        if (id.isEmpty()) {
            if (trackedFluidKey != null && !trackedFluidKey.equals(key)) {
                tag.remove(trackedFluidKey);
                trackedFluidKey = null;
            }
            tidyTag();
            return true;
        }
        long amount;
        try {
            amount = Long.parseLong(draftFluidAmount.trim());
        } catch (NumberFormatException ex) {
            return false;
        }
        if (trackedFluidKey != null && !trackedFluidKey.equals(key)) {
            tag.remove(trackedFluidKey);
        }
        CompoundTag fluidTag = new CompoundTag();
        fluidTag.putString("FluidName", id);
        fluidTag.putLong("Amount", amount);
        tag.put(key, fluidTag);
        trackedFluidKey = key;
        return true;
    }

    private void removeFluidTab() {
        CompoundTag tag = workingStack.getTag();
        String key = draftFluidKey == null || draftFluidKey.isBlank() ? "Fluid" : draftFluidKey.trim();
        if (tag != null) tag.remove(key);
        if (trackedFluidKey != null && trackedFluidKey.equals(key)) trackedFluidKey = null;
        tidyTag();
        flash(Lang.get("gui.kve.nbt.fluid.removed"));
        init();
    }

    private static final int RAW_BOX_H = 150;

    private void initRawTab() {
        int cx = cx(), cr = cr();
        rawTop = tabContentY + 12;
        rawBoxH = Math.max(40, contentBottom() - rawTop - 18 - 28);
        CompoundTag tag = workingStack.getTag();

        fRawNbt = new RawNbtTextArea(font, cx, rawTop, cr - cx, rawBoxH);
        fRawNbt.setMaxLength(RAW_MAX_CHARS);
        fRawNbt.setResponder(() -> {});
        loadRawTextFromTag(tag);
        addRenderableWidget(fRawNbt);

        KveGuiStyle.ButtonRow rawRow = new KveGuiStyle.ButtonRow(cx, rawTop + rawBoxH + 4, cr);
        Button validateBtn = rawRow.add(0, Lang.get("gui.kve.nbt.raw.validate"), b -> applyRawTab());
        validateBtn.active = !rawTooLargeToEdit;
        addRenderableWidget(validateBtn);
        addRenderableWidget(rawRow.add(0, Lang.get("gui.kve.nbt.raw.reload"), b -> reloadRawTab()));
    }

    private void loadRawTextFromTag(CompoundTag tag) {
        if (tag == null) {
            fRawNbt.setValue("{}");
            rawTooLargeToEdit = false;
        } else if (isSafeToStringify(tag, 0, new int[1])) {
            fRawNbt.setValue(tag.toString());
            rawTooLargeToEdit = false;
        } else {
            fRawNbt.setValue("");
            rawTooLargeToEdit = true;
        }
        fRawNbt.setEditable(!rawTooLargeToEdit);
        rawErrorMsg = rawTooLargeToEdit ? Lang.get("gui.kve.nbt.raw.too_large") : "";
    }

    /**
     * Cheaply checks whether a tag is safe to run through
     * CompoundTag#toString() for display. Some items (a full written book,
     * a deeply-nested modded container, a bundle stuffed with NBT-heavy
     * items, etc.) can carry NBT that's enormous or pathologically nested.
     * Calling toString() directly on those builds a huge String on the
     * render thread (visible hitch/freeze) or, for extreme nesting, blows
     * the stack. This walks the tree counting nodes and tracking depth,
     * bailing out the instant either limit is crossed - so the worst case
     * cost is bounded by the limit, not by however big the real tag is.
     */
    private boolean isSafeToStringify(Tag t, int depth, int[] nodeCount) {
        if (depth > RAW_SANITY_DEPTH_LIMIT) return false;
        if (++nodeCount[0] > RAW_SANITY_NODE_LIMIT) return false;
        if (t instanceof CompoundTag ct) {
            for (String k : ct.getAllKeys()) {
                if (!isSafeToStringify(ct.get(k), depth + 1, nodeCount)) return false;
            }
        } else if (t instanceof ListTag lt) {
            for (Tag child : lt) {
                if (!isSafeToStringify(child, depth + 1, nodeCount)) return false;
            }
        }
        return true;
    }

    private void applyRawTab() {
        if (rawTooLargeToEdit) return;
        if (tryApplyRawText(fRawNbt.getValue().trim())) {
            flash(Lang.get("gui.kve.nbt.raw.applied"));
        } else {
            flash(Lang.get("gui.kve.nbt.raw.invalid") + ": " + rawErrorMsg);
        }
        init();
    }

    private boolean tryApplyRawText(String text) {
        try {
            CompoundTag parsed = text.isEmpty() ? new CompoundTag() : TagParser.parseTag(text);
            sanitizeParsedTag(parsed);
            workingStack.setTag(parsed.isEmpty() ? null : parsed);
            rawErrorMsg = "";
            enchantsLoaded = false;
            numericLoaded = false;
            fluidLoaded = false;
            return true;
        } catch (Throwable ex) {
            // Throwable, not just Exception: TagParser is a recursive-descent
            // parser, so a pathological/deeply-nested SNBT string (typed by
            // accident or pasted) throws StackOverflowError rather than a
            // normal Exception. Catching only Exception let that crash the
            // whole screen instead of showing a validation message.
            rawErrorMsg = ex instanceof StackOverflowError
                    ? "NBT demasiado anidado"
                    : (ex.getMessage() != null ? ex.getMessage() : "NBT invalido");
            return false;
        }
    }

    private void sanitizeParsedTag(CompoundTag tag) {
        if (tag.contains("Enchantments") && tag.get("Enchantments").getId() != Tag.TAG_LIST) tag.remove("Enchantments");
        if (tag.contains("StoredEnchantments") && tag.get("StoredEnchantments").getId() != Tag.TAG_LIST) tag.remove("StoredEnchantments");
        for (String reserved : RESERVED_NUMERIC_KEYS) {
            if (tag.contains(reserved) && !(tag.get(reserved) instanceof NumericTag)) tag.remove(reserved);
        }
        String fluidKey = currentFluidKey();
        if (tag.contains(fluidKey) && tag.get(fluidKey).getId() != Tag.TAG_COMPOUND) tag.remove(fluidKey);
    }

    private void reloadRawTab() {
        loadRawTextFromTag(workingStack.getTag());
    }

    private void tidyTag() {
        if (workingStack.hasTag() && workingStack.getTag() != null && workingStack.getTag().isEmpty()) {
            workingStack.setTag(null);
        }
    }

    private void save() {
        if (numericLoaded) commitNumericFields();
        if (fluidLoaded) commitFluidField();
        if (activeTab == Tab.RAW && fRawNbt != null && !rawTooLargeToEdit
                && !tryApplyRawText(fRawNbt.getValue().trim())) {
            flash(Lang.get("gui.kve.nbt.raw.invalid") + ": " + rawErrorMsg);
            init();
            return;
        }
        if (onSave != null) onSave.accept(workingStack.copy());
        else minecraft.setScreen(parent);
    }

    private void cancel() {
        minecraft.setScreen(parent);
    }

    /** ESC returns to the caller instead of closing the whole GUI. */
    @Override
    public void onClose() { cancel(); }

    private void clearAllNbt() {
        workingStack.setTag(null);

        draftEnchants.clear();

        draftDamage = "";
        draftCustomModelData = "";
        draftRepairCost = "";
        draftUnbreakable = false;
        numRows.clear();
        trackedCustomKeys.clear();

        draftFluidKey = "Fluid";
        draftFluidId = "";
        draftFluidAmount = "1000";
        trackedFluidKey = null;

        rawErrorMsg = "";
        rawTooLargeToEdit = false;
        if (fRawNbt != null) { fRawNbt.setValue("{}"); fRawNbt.setEditable(true); }

        enchantsLoaded = true;
        numericLoaded = true;
        fluidLoaded = true;

        flash(Lang.get("gui.kve.nbt.cleared"));
        init();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, Lang.get("gui.kve.nbt.title"));

        if (workingStack.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.nbt.no_item"), L + 8, T + 30, GUI_W - 16, KveGuiStyle.TEXT_WARN);
            super.render(g, mx, my, pt);
            return;
        }

        drawPreview(g, mx, my);
        g.fill(L + PREV_W + 2, T + 15, L + PREV_W + 3, T + GUI_H - 3, KveGuiStyle.BORDER_SUBTLE);
        g.fill(cx(), tabContentY - 4, cr(), tabContentY - 3, KveGuiStyle.BORDER_SUBTLE);

        switch (activeTab) {
            case ENCHANTMENTS -> drawEnchantTab(g, mx, my);
            case NUMERIC      -> drawNumericTab(g);
            case FLUID        -> drawFluidTab(g);
            case RAW          -> drawRawTab(g);
        }

        super.render(g, mx, my, pt);

        String status = null;
        int color = KveGuiStyle.TEXT_OK;
        if (!flashMsg.isEmpty() && System.currentTimeMillis() - flashAt < 4500) {
            status = flashMsg;
        } else if (activeTab == Tab.NUMERIC && !numericProblems.isEmpty()) {
            status = Lang.get("gui.kve.nbt.numeric.errors") + ": " + String.join(", ", numericProblems);
            color = KveGuiStyle.TEXT_WARN;
        }
        if (status != null) KveGuiStyle.drawTextSafe(g, status, cx(), bottomY() - 12, cr() - cx(), color);
    }

    private void drawPreview(GuiGraphics g, int mx, int my) {
        int px = L + 6, py = T + 20;

        int slotX = px + 12, slotY = py - 6, slotSize = 56;
        boolean hovered = KveGuiStyle.isHovered(mx, my, slotX, slotY, slotSize, slotSize);
        boolean hasTag = workingStack.hasTag();
        int glowColor = !draftEnchants.isEmpty() ? KveGuiStyle.ACCENT_GOLD
                : hasTag ? KveGuiStyle.BORDER_OUTER
                : KveGuiStyle.ACCENT_PURPLE;
        KveGuiStyle.drawSlotGlow(g, slotX, slotY, slotSize, hovered, glowColor);

        int iconRenderSize = 16 * 3;
        g.pose().pushPose();
        g.pose().translate(slotX + slotSize / 2f - iconRenderSize / 2f,
                            slotY + slotSize / 2f - iconRenderSize / 2f, 0);
        g.pose().scale(3f, 3f, 1f);
        try { g.renderItem(workingStack, 0, 0); } catch (Throwable ignored) {}
        g.pose().popPose();

        String name = workingStack.getHoverName().getString();
        KveGuiStyle.drawTextSafe(g, name, px + 6, py + 66, PREV_W - 12, KveGuiStyle.TEXT_PRIMARY);

        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(workingStack.getItem());
        if (id != null) {
            KveGuiStyle.drawTextSafe(g, id.getNamespace() + ":", px, py + 80, PREV_W - 6, 0xFF4A7A99);
            KveGuiStyle.drawTextSafe(g, id.getPath(), px, py + 90, PREV_W - 6, KveGuiStyle.th(0xFF5A8AAA));
        }

        int keyCount = hasTag ? workingStack.getTag().getAllKeys().size() : 0;
        KveGuiStyle.drawTextSafe(g, hasTag ? (keyCount + " " + Lang.get("gui.kve.nbt.keys_label")) : Lang.get("gui.kve.nbt.no_tag"),
                px, py + 106, PREV_W - 6, hasTag ? 0xFF88CC88 : KveGuiStyle.TEXT_MUTED);

        int enchCount = draftEnchants.size();
        if (enchCount > 0) {
            KveGuiStyle.drawTextSafe(g, "\u2726 " + enchCount, px, py + 118, PREV_W - 6, KveGuiStyle.ACCENT_GOLD);
        }
    }

    private void drawEnchantTab(GuiGraphics g, int mx, int my) {
        int cx = cx(), cr = cr();
        List<Enchantment> keys = new ArrayList<>(draftEnchants.keySet());
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.nbt.enchant.current") + " (" + keys.size() + ")",
                cx, listHeaderY + 3, listHeaderBtnX - 4 - cx, KveGuiStyle.TEXT_LABEL);
        if (keys.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.nbt.enchant.none"), cx + 2, listTop + 3, cr - cx - 4, KveGuiStyle.TEXT_MUTED);
        }
        int rows = rowsFor(listTop);
        int scroll = enchantScrollBar != null ? enchantScrollBar.getScroll() : 0;
        int lvlX = cr - 22 - 28 - 52;
        for (int i = 0; i < rows && i + scroll < keys.size(); i++) {
            Enchantment ench = keys.get(i + scroll);
            int level = draftEnchants.get(ench);
            int ry = listTop + i * ROW_H;
            g.fill(cx, ry, cr - 8, ry + ROW_H - 1, KveGuiStyle.BG_FIELD);
            boolean overMax = level > ench.getMaxLevel();
            KveGuiStyle.drawTextSafe(g, safeEnchantName(ench), cx + 3, ry + 3, lvlX - cx - 6, KveGuiStyle.TEXT_PRIMARY);
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.nbt.enchant.level", level), lvlX, ry + 3, 50,
                    overMax ? KveGuiStyle.TEXT_WARN : KveGuiStyle.TEXT_OK);
        }
        if (enchantScrollBar != null) enchantScrollBar.render(g);
    }

    private String safeEnchantName(Enchantment ench) {
        try {
            String translated = Component.translatable(ench.getDescriptionId()).getString();
            if (translated != null && !translated.isEmpty() && !translated.equals(ench.getDescriptionId()))
                return translated;
        } catch (Exception ignored) {}
        ResourceLocation k = ForgeRegistries.ENCHANTMENTS.getKey(ench);
        return k != null ? k.toString() : "?";
    }

    private void drawNumericTab(GuiGraphics g) {
        int cx = cx(), cr = cr();
        int lw = NUM_LABEL_W - 6;
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.nbt.field.damage"), cx, fieldsY + 2, lw, KveGuiStyle.TEXT_LABEL);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.nbt.field.cmd"), cx, fieldsY + 18, lw, KveGuiStyle.TEXT_LABEL);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.nbt.field.repair"), cx, fieldsY + 34, lw, KveGuiStyle.TEXT_LABEL);
        g.fill(cx, listHeaderY - 6, cr, listHeaderY - 5, KveGuiStyle.BORDER_SUBTLE);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.nbt.numeric.custom") + " (" + numRows.size() + ")",
                cx, listHeaderY + 3, listHeaderBtnX - 4 - cx, KveGuiStyle.ACCENT_GOLD);
        if (numRows.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.nbt.numeric.none"), cx + 2, listTop + 3, cr - cx - 4, KveGuiStyle.TEXT_MUTED);
        }
        if (numericScrollBar != null) numericScrollBar.render(g);
    }

    private void drawFluidTab(GuiGraphics g) {
        int cx = cx(), cr = cr();
        int lw = FLUID_LABEL_W - 6;
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.nbt.fluid.key"), cx, fieldsY + 2, lw, KveGuiStyle.TEXT_LABEL);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.nbt.fluid.id"), cx, fieldsY + 20, lw, KveGuiStyle.TEXT_LABEL);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.nbt.fluid.amount"), cx, fieldsY + 38, lw, KveGuiStyle.TEXT_LABEL);
        String id = draftFluidId == null ? "" : draftFluidId.trim();
        boolean amountOk;
        try { Long.parseLong(draftFluidAmount.trim()); amountOk = true; } catch (NumberFormatException ex) { amountOk = false; }
        String state = id.isEmpty() ? Lang.get("gui.kve.nbt.fluid.need_id")
                : !amountOk ? Lang.get("gui.kve.nbt.fluid.bad_amount")
                : Lang.get("gui.kve.nbt.fluid.applied");
        int color = id.isEmpty() ? KveGuiStyle.TEXT_MUTED : amountOk ? KveGuiStyle.TEXT_OK : KveGuiStyle.TEXT_WARN;
        KveGuiStyle.drawTextSafe(g, state, cx, listHeaderY + 20, cr - cx, color);
    }

    private void drawRawTab(GuiGraphics g) {
        int cx = cx(), boxW = cr() - cx;
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.nbt.raw.label"), cx, rawTop - 11, boxW, KveGuiStyle.TEXT_LABEL);
        if (!rawErrorMsg.isEmpty()) {
            int labelY = rawTop + rawBoxH + 22;
            int maxLines = 2;
            List<String> lines = KveGuiStyle.wrapTextSafe(font, "\u26A0 " + rawErrorMsg, boxW - 8);
            int shown = Math.min(lines.size(), maxLines);
            g.fill(cx, labelY, cx + boxW, labelY + shown * 10 + 6, 0xC0331111);
            g.fill(cx, labelY, cx + boxW, labelY + 1, 0xFFFF6644);
            for (int i = 0; i < shown; i++) {
                KveGuiStyle.drawTextSafe(g, lines.get(i), cx + 4, labelY + 3 + i * 10, boxW - 8, 0xFFFF9977);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (activeTab == Tab.ENCHANTMENTS && enchantScrollBar != null && enchantScrollBar.mouseClicked(mx, my, btn)) return true;
        if (activeTab == Tab.NUMERIC && numericScrollBar != null && numericScrollBar.mouseClicked(mx, my, btn)) return true;
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (activeTab == Tab.ENCHANTMENTS && enchantScrollBar != null && enchantScrollBar.mouseDragged(mx, my, btn, dx, dy)) return true;
        if (activeTab == Tab.NUMERIC && numericScrollBar != null && numericScrollBar.mouseDragged(mx, my, btn, dx, dy)) return true;
        if (activeTab == Tab.RAW && fRawNbt != null && fRawNbt.mouseDragged(mx, my, btn, dx, dy)) return true;
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (enchantScrollBar != null) enchantScrollBar.mouseReleased(mx, my, btn);
        if (numericScrollBar != null) numericScrollBar.mouseReleased(mx, my, btn);
        if (fRawNbt != null) fRawNbt.mouseReleased(mx, my, btn);
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (activeTab == Tab.ENCHANTMENTS && enchantScrollBar != null) { enchantScrollBar.mouseScrolled(delta); return true; }
        if (activeTab == Tab.NUMERIC && numericScrollBar != null) { numericScrollBar.mouseScrolled(delta); return true; }
        if (activeTab == Tab.RAW && fRawNbt != null && fRawNbt.mouseScrolled(mx, my, delta)) return true;
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (getFocused() != null && getFocused().keyPressed(key, scan, mods)) return true;
        if (com.kve.client.gui.EditorKeys.isSave(key, mods)) { save(); return true; }
        if (com.kve.client.gui.EditorKeys.isFocusTab(key, mods)) { super.keyPressed(key, scan, mods); return true; }
        return com.kve.client.gui.EditorKeys.swallow(mods);
    }

    private void flash(String msg) { flashMsg = msg; flashAt = System.currentTimeMillis(); }

    private EditBox field(int x, int y) { return field(x, y, 56); }

    private EditBox field(int x, int y, int w) {
        EditBox e = new KveScaledEditBox(font, x, y, w, 11, Component.literal(""));
        e.setMaxLength(32);
        return e;
    }

    private Button btn(String label, int x, int y, int w, Runnable action) {
        return KveGuiStyle.smartBtn(x, y, w, label, b -> action.run());
    }

    // sec.4.3: overload con h=12 fijo
    // colapsa a BTN_H=14 general - el parametro h se ignora a proposito
    // (queda en la firma solo para no romper los call sites existentes).
    private Button btn(String label, int x, int y, int w, int h, Runnable action) {
        return KveGuiStyle.smartBtn(x, y, w, label, b -> action.run());
    }

    @Override public boolean isPauseScreen() { return false; }
}
