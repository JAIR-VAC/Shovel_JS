package com.kve.client.gui.properties;

import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KvePlateButton;
import com.kve.client.gui.KveScaledEditBox;

import com.kve.common.Lang;
import com.kve.common.kubejs.KubeJSItemAttrExporter.AttrMod;
import com.kve.common.kubejs.KubeJSItemAttrExporter.SlotAttrMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Editor de los atributos que un item da puesto en un slot de
 * Curios/Accessories. Mismo patron de dialogo que {@link ItemCombatScreen}:
 * trabaja sobre una copia y devuelve el mapa (clave {@link SlotAttrMod#key()})
 * por un Consumer al confirmar. La aplicacion real la hace
 * {@code com.kve.common.itemrules.CurioAttributeEnforcer}.
 * <p>
 * El campo "Slot" edita el slot de la fila seleccionada; sin seleccion, es el
 * slot que reciben las filas nuevas. Vacio = cualquier slot.
 */
@OnlyIn(Dist.CLIENT)
public class ItemCurioAttrScreen extends Screen {

    private static final int GUI_W = 320;
    private static final int GUI_H = 236;
    private static final int ROWS_VISIBLE = 8;
    private static final int ROW_H = 14;

    private final Screen parent;
    private final Consumer<LinkedHashMap<String, SlotAttrMod>> onConfirm;
    private final LinkedHashMap<String, SlotAttrMod> draft = new LinkedHashMap<>();
    private final Map<String, SlotAttrMod> defaults = new LinkedHashMap<>();

    private EditBox slotBox;
    private String slotForNew = "";
    private String selectedKey = null;
    private int scroll = 0;
    private int listTop;
    private int L, T;

    public ItemCurioAttrScreen(Screen parent, Map<String, SlotAttrMod> existing, List<SlotAttrMod> itemDefaults,
                               Consumer<LinkedHashMap<String, SlotAttrMod>> onConfirm) {
        super(Lang.c("gui.kve.item.curio.title"));
        this.parent = parent;
        this.onConfirm = onConfirm;
        if (existing != null) for (SlotAttrMod m : existing.values()) draft.put(m.key(), m.copy());
        if (itemDefaults != null) for (SlotAttrMod m : itemDefaults) defaults.put(m.key(), m.copy());
    }

    private List<SlotAttrMod> rows() { return new ArrayList<>(draft.values()); }

    @Override
    protected void init() {
        L = (width  - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        clearWidgets();
        setFocused(null);

        int x0 = L + 10;
        int slotY = T + 30;
        int slotW = 110;
        slotBox = new KveScaledEditBox(font, x0 + 40, slotY, slotW, 12, Component.empty());
        slotBox.setMaxLength(64);
        SlotAttrMod sel = selectedKey != null ? draft.get(selectedKey) : null;
        slotBox.setValue(sel != null ? sel.slot : slotForNew);
        slotBox.setHint(Component.literal(Lang.get("gui.kve.item.curio.any_slot")));
        slotBox.setTooltip(Tooltip.create(Component.literal(Lang.get("gui.kve.item.curio.slot_tip"))));
        slotBox.setResponder(this::onSlotTyped);
        addRenderableWidget(slotBox);

        int addY = slotY + 18;
        int half = (GUI_W - 20 - 4) / 2;
        addRenderableWidget(new KvePlateButton(x0, addY, half, 14,
                Component.literal("+ " + Lang.get("gui.kve.attr.title")), b -> openPicker()));
        KvePlateButton resetBtn = new KvePlateButton(x0 + half + 4, addY, half, 14,
                Component.literal(Lang.get("gui.kve.item.curio.restore")), b -> restoreDefaults());
        resetBtn.setTooltip(Tooltip.create(Component.literal(Lang.get("gui.kve.item.curio.restore_tip"))));
        addRenderableWidget(resetBtn);

        listTop = addY + 30;
        List<SlotAttrMod> rows = rows();
        int maxScroll = Math.max(0, rows.size() - ROWS_VISIBLE);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        int editW = KveGuiStyle.btnWidth(Lang.get("gui.kve.btn.edit"));
        for (int i = 0; i < ROWS_VISIBLE; i++) {
            int idx = i + scroll;
            if (idx >= rows.size()) break;
            int ry = listTop + i * ROW_H;
            String key = rows.get(idx).key();
            addRenderableWidget(new KvePlateButton(L + GUI_W - 12 - 16 - 2 - editW, ry, editW, 12,
                    Component.literal(Lang.get("gui.kve.btn.edit")), b -> editRow(key)));
            addRenderableWidget(new KvePlateButton(L + GUI_W - 12 - 16, ry, 16, 12,
                    Component.literal("X"), b -> removeRow(key)));
        }

        int by = T + GUI_H - 20;
        addRenderableWidget(new KvePlateButton(L + GUI_W / 2 - 35, by, 70, 16, Component.literal(Lang.get("gui.kve.btn.confirm")), b -> save()).withPrimary(true));
        addRenderableWidget(new KvePlateButton(L + GUI_W - 17, T + 2, 15, 14, Component.literal("X"), b -> onClose()));
    }

    private void onSlotTyped(String raw) {
        String slot = raw == null ? "" : raw.trim();
        if (selectedKey == null) { slotForNew = slot; return; }
        SlotAttrMod m = draft.get(selectedKey);
        if (m == null) { selectedKey = null; slotForNew = slot; return; }
        String newKey = SlotAttrMod.key(slot, m.attrId);
        if (newKey.equals(selectedKey)) return;
        if (draft.containsKey(newKey)) return;
        LinkedHashMap<String, SlotAttrMod> copy = new LinkedHashMap<>();
        for (var e : draft.entrySet()) {
            if (e.getKey().equals(selectedKey)) {
                SlotAttrMod moved = new SlotAttrMod(m.attrId, m.value, m.op, slot);
                copy.put(newKey, moved);
            } else {
                copy.put(e.getKey(), e.getValue());
            }
        }
        draft.clear();
        draft.putAll(copy);
        selectedKey = newKey;
    }

    private void openPicker() {
        selectedKey = null;
        String slot = slotForNew;
        minecraft.setScreen(new AttributePickerScreen(this,
                mod -> putRow(new SlotAttrMod(mod.attrId, mod.value, mod.op, slot)),
                attrId -> {
                    SlotAttrMod m = draft.get(SlotAttrMod.key(slot, attrId));
                    return m != null ? new AttrMod(m.attrId, m.value, m.op) : null;
                }));
    }

    private void putRow(SlotAttrMod m) {
        if (m.attrId == null || m.attrId.isEmpty()) return;
        draft.put(m.key(), m);
        selectedKey = m.key();
    }

    private void editRow(String key) {
        SlotAttrMod m = draft.get(key);
        if (m == null) return;
        selectedKey = key;
        minecraft.setScreen(new AttributeValueScreen(this, m.attrId, attrName(m.attrId), mod -> {
            draft.put(key, new SlotAttrMod(m.attrId, mod.value, mod.op, m.slot));
            minecraft.setScreen(this);
        }).withExisting(new AttrMod(m.attrId, m.value, m.op)));
    }

    private void removeRow(String key) {
        draft.remove(key);
        if (key.equals(selectedKey)) selectedKey = null;
        init();
    }

    private void restoreDefaults() {
        draft.clear();
        for (SlotAttrMod m : defaults.values()) draft.put(m.key(), m.copy());
        selectedKey = null;
        scroll = 0;
        init();
    }

    private void save() {
        onConfirm.accept(new LinkedHashMap<>(draft));
    }

    static String attrName(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Attribute attr = rl == null ? null : BuiltInRegistries.ATTRIBUTE.get(rl);
        if (attr == null) return id;
        String translated = Component.translatable(attr.getDescriptionId()).getString();
        return translated == null || translated.isEmpty() || translated.equals(attr.getDescriptionId()) ? id : translated;
    }

    private static String describe(SlotAttrMod m) {
        String opSuffix = switch (m.op) {
            case ADDITION -> "";
            case MULTIPLY_BASE -> " (%base)";
            case MULTIPLY_TOTAL -> " (%total)";
        };
        String slot = m.slot.isEmpty() ? "*" : m.slot;
        return "[" + slot + "] " + attrName(m.attrId) + " = " + fmt(m.value) + opSuffix;
    }

    private static String fmt(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) return String.valueOf((long) v);
        return new java.math.BigDecimal(String.format(java.util.Locale.ROOT, "%.3f", v)).stripTrailingZeros().toPlainString();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int editW = KveGuiStyle.btnWidth(Lang.get("gui.kve.btn.edit"));
        int textRight = L + GUI_W - 12 - 16 - 2 - editW - 2;
        if (mx >= L + 10 && mx < textRight && my >= listTop && my < listTop + ROWS_VISIBLE * ROW_H) {
            int idx = (int) ((my - listTop) / ROW_H) + scroll;
            List<SlotAttrMod> rows = rows();
            if (idx >= 0 && idx < rows.size()) {
                String key = rows.get(idx).key();
                selectedKey = key.equals(selectedKey) ? null : key;
                init();
                return true;
            }
        }
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, Lang.get("gui.kve.item.curio.title"));

        int x0 = L + 10;
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.item.curio.slot"), x0, T + 32, 38, KveGuiStyle.th(0xFF7BC8E0));
        String slotHint = Lang.get(selectedKey != null ? "gui.kve.item.curio.slot_selected" : "gui.kve.item.curio.slot_new");
        KveGuiStyle.drawTextSafe(g, slotHint, x0 + 156, T + 32, GUI_W - 176, KveGuiStyle.th(0xFF667788));

        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.item.curio.hint"), x0, listTop - 12, GUI_W - 20, KveGuiStyle.ACCENT_GOLD);

        List<SlotAttrMod> rows = rows();
        int editW = KveGuiStyle.btnWidth(Lang.get("gui.kve.btn.edit"));
        int textW = GUI_W - 20 - 16 - editW - 8;
        for (int i = 0; i < ROWS_VISIBLE; i++) {
            int idx = i + scroll;
            if (idx >= rows.size()) break;
            SlotAttrMod m = rows.get(idx);
            int ry = listTop + i * ROW_H;
            boolean selected = m.key().equals(selectedKey);
            if (selected) g.fill(x0 - 2, ry - 1, x0 + textW + 2, ry + 12, 0x40FFFFFF);
            SlotAttrMod def = defaults.get(m.key());
            boolean isDefault = def != null && def.value == m.value && def.op == m.op;
            int color = selected ? KveGuiStyle.ACCENT_GOLD : isDefault ? KveGuiStyle.TEXT_LABEL : KveGuiStyle.th(0xFF8FE08F);
            KveGuiStyle.drawTextSafe(g, describe(m), x0, ry + 2, textW, color);
        }
        if (rows.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.item.curio.empty"), x0, listTop + 2, GUI_W - 20, KveGuiStyle.th(0xFF667788));
        } else if (rows.size() > ROWS_VISIBLE) {
            KveGuiStyle.drawTextSafe(g, (scroll + 1) + "-" + Math.min(rows.size(), scroll + ROWS_VISIBLE) + "/" + rows.size(),
                    x0, listTop + ROWS_VISIBLE * ROW_H + 1, GUI_W - 20, KveGuiStyle.th(0xFF556677));
        }

        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        int maxScroll = Math.max(0, draft.size() - ROWS_VISIBLE);
        int next = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(delta)));
        if (next != scroll) { scroll = next; init(); }
        return true;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override public boolean isPauseScreen() { return false; }
}
