package com.kve.client.gui.properties;

import com.kve.client.gui.picker.EffectPickerScreen;
import com.kve.client.gui.picker.EffectValueScreen;
import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KvePlateButton;
import com.kve.client.gui.KveScaledEditBox;

import com.kve.common.Lang;

import com.kve.common.kubejs.KubeJSItemAttrExporter.CombatProps;
import com.kve.common.kubejs.KubeJSItemAttrExporter.FoodEffect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.List;
import java.util.function.Consumer;

/**
 * Editor de {@link CombatProps} de un item - las 5 features de
 * "Combate y efectos" (efectos al golpear, efectos pasivos, robo de vida,
 * incendiar objetivo, enfriamiento al usar). Mismo patron de dialogo que
 * {@link ItemFoodScreen} (lista de efectos con EffectPickerScreen/
 * EffectValueScreen reutilizados), un Consumer de vuelta al confirmar.
 * La aplicacion real en el juego la hace
 * {@code com.kve.common.itemrules.ItemCombatEnforcer}.
 */
@OnlyIn(Dist.CLIENT)
public class ItemCombatScreen extends Screen {

    private static final int GUI_W = 300;
    private static final int GUI_H = 236;
    private static final int EFFECT_ROWS_VISIBLE = 5;
    private static final int EFFECT_ROW_H = 14;

    private final Screen parent;
    private final Consumer<CombatProps> onConfirm;
    private final CombatProps draft;

    private boolean passiveTab = false;
    private int effectScroll = 0;

    private EditBox lifestealBox, igniteBox, cooldownBox;
    private String snapLifesteal, snapIgnite, snapCooldown;
    private int statsY, listLabelY, listTop;

    private int L, T;

    public ItemCombatScreen(Screen parent, CombatProps existing, Consumer<CombatProps> onConfirm) {
        super(Lang.c("gui.kve.item.combat.title"));
        this.parent = parent;
        this.onConfirm = onConfirm;
        this.draft = existing != null ? existing.copy() : new CombatProps();
    }

    private List<FoodEffect> currentList() {
        return passiveTab ? draft.passiveEffects : draft.hitEffects;
    }

    @Override
    protected void init() {
        L = (width  - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        if (lifestealBox != null) {
            snapLifesteal = lifestealBox.getValue();
            snapIgnite = igniteBox.getValue();
            snapCooldown = cooldownBox.getValue();
        }
        clearWidgets();
        setFocused(null);

        int x0 = L + 10;
        int colW = (GUI_W - 20 - 8) / 3;
        statsY = T + 30;

        lifestealBox = statBox(x0, statsY, colW, snapLifesteal != null ? snapLifesteal : fmt(draft.lifestealPct), "gui.kve.item.combat.lifesteal_tip");
        igniteBox = statBox(x0 + colW + 4, statsY, colW, snapIgnite != null ? snapIgnite : String.valueOf(draft.igniteSeconds), "gui.kve.item.combat.ignite_tip");
        cooldownBox = statBox(x0 + (colW + 4) * 2, statsY, colW, snapCooldown != null ? snapCooldown : fmt(draft.useCooldownTicks / 20.0), "gui.kve.item.combat.cooldown_tip");

        int tabY = statsY + 20;
        int tabW = (GUI_W - 20 - 4) / 2;
        KvePlateButton hitTab = new KvePlateButton(x0, tabY, tabW, 14,
                Component.literal((passiveTab ? "" : "> ") + Lang.get("gui.kve.item.combat.hit_effects") + " (" + draft.hitEffects.size() + ")"),
                b -> switchTab(false));
        hitTab.setTooltip(Tooltip.create(Component.literal(Lang.get("gui.kve.item.combat.hit_effects_tip"))));
        addRenderableWidget(hitTab);
        KvePlateButton passiveBtn = new KvePlateButton(x0 + tabW + 4, tabY, tabW, 14,
                Component.literal((passiveTab ? "> " : "") + Lang.get("gui.kve.item.combat.passive_effects") + " (" + draft.passiveEffects.size() + ")"),
                b -> switchTab(true));
        passiveBtn.setTooltip(Tooltip.create(Component.literal(Lang.get("gui.kve.item.combat.passive_effects_tip"))));
        addRenderableWidget(passiveBtn);

        int addY = tabY + 18;
        addRenderableWidget(new KvePlateButton(x0, addY, GUI_W - 20, 14, Component.literal("+ " + Lang.get("gui.kve.effect.title")),
                b -> openEffectPicker()));
        listLabelY = addY + 18;
        listTop = listLabelY + 10;

        List<FoodEffect> list = currentList();
        int maxScroll = Math.max(0, list.size() - EFFECT_ROWS_VISIBLE);
        effectScroll = Math.max(0, Math.min(effectScroll, maxScroll));
        int editW = KveGuiStyle.btnWidth(Lang.get("gui.kve.btn.edit"));
        for (int i = 0; i < EFFECT_ROWS_VISIBLE; i++) {
            int idx = i + effectScroll;
            if (idx >= list.size()) break;
            int ry = listTop + i * EFFECT_ROW_H;
            int rowIdx = idx;
            addRenderableWidget(new KvePlateButton(L + GUI_W - 12 - 16 - 2 - editW, ry, editW, 12, Component.literal(Lang.get("gui.kve.btn.edit")), b -> editEffect(rowIdx)));
            addRenderableWidget(new KvePlateButton(L + GUI_W - 12 - 16, ry, 16, 12, Component.literal("X"), b -> removeEffect(rowIdx)));
        }

        int by = T + GUI_H - 20;
        addRenderableWidget(new KvePlateButton(L + 10, by, 70, 16, Component.literal(Lang.get("gui.kve.item.combat.clear")), b -> clearAll()));
        addRenderableWidget(new KvePlateButton(L + GUI_W / 2 - 36, by, 70, 16, Component.literal(Lang.get("gui.kve.btn.confirm")), b -> save()).withPrimary(true));
        addRenderableWidget(new KvePlateButton(L + GUI_W - 17, T + 2, 15, 14, Component.literal("X"), b -> onClose()));
    }

    private EditBox statBox(int x, int y, int w, String value, String tipKey) {
        EditBox b = new KveScaledEditBox(font, x, y, w, 12, Component.empty());
        b.setMaxLength(8);
        b.setValue(value);
        b.setHint(Component.literal("0"));
        b.setTooltip(Tooltip.create(Component.literal(Lang.get(tipKey))));
        addRenderableWidget(b);
        return b;
    }

    private void switchTab(boolean passive) {
        if (passiveTab == passive) return;
        passiveTab = passive;
        effectScroll = 0;
        init();
    }

    private void openEffectPicker() {
        minecraft.setScreen(new EffectPickerScreen(this, effectId -> {
            minecraft.setScreen(new EffectValueScreen(this, effectId, effectId, fx -> {
                currentList().add(fx);
                minecraft.setScreen(this);
            }));
        }));
    }

    private void editEffect(int idx) {
        List<FoodEffect> list = currentList();
        if (idx < 0 || idx >= list.size()) return;
        FoodEffect existing = list.get(idx);
        minecraft.setScreen(new EffectValueScreen(this, existing.effectId, existing.effectId, fx -> {
            list.set(idx, fx);
            minecraft.setScreen(this);
        }).withExisting(existing));
    }

    private void removeEffect(int idx) {
        List<FoodEffect> list = currentList();
        if (idx < 0 || idx >= list.size()) return;
        list.remove(idx);
        init();
    }

    private void clearAll() {
        draft.hitEffects.clear();
        draft.passiveEffects.clear();
        snapLifesteal = "0"; snapIgnite = "0"; snapCooldown = "0";
        lifestealBox = null;
        effectScroll = 0;
        init();
    }

    private void save() {
        draft.lifestealPct = Math.max(0, Math.min(100, parseD(lifestealBox, draft.lifestealPct)));
        draft.igniteSeconds = (int) Math.max(0, Math.min(3600, Math.round(parseD(igniteBox, draft.igniteSeconds))));
        double cdSec = parseD(cooldownBox, draft.useCooldownTicks / 20.0);
        draft.useCooldownTicks = (int) Math.max(0, Math.min(72000, Math.round(cdSec * 20)));
        onConfirm.accept(draft.isEmpty() ? null : draft);
    }

    private static double parseD(EditBox b, double def) {
        if (b == null) return def;
        String s = b.getValue().trim().replace(',', '.').replace("%", "");
        if (s.isEmpty()) return 0;
        try { return Double.parseDouble(s); } catch (Exception e) { return def; }
    }

    private static String fmt(double v) {
        if (v == Math.floor(v)) return String.valueOf((long) v);
        return new java.math.BigDecimal(String.format(java.util.Locale.ROOT, "%.3f", v)).stripTrailingZeros().toPlainString();
    }

    static String describe(FoodEffect e, boolean withChance) {
        ResourceLocation rl = ResourceLocation.tryParse(e.effectId);
        MobEffect effect = rl == null ? null : BuiltInRegistries.MOB_EFFECT.get(rl);
        String name = effect != null ? effect.getDisplayName().getString() : e.effectId;
        String lvl = e.amplifier > 0 ? " " + roman(e.amplifier + 1) : "";
        String s = name + lvl + " · " + fmt(e.durationTicks / 20.0) + "s";
        if (withChance && e.chance < 1.0f) s += " · " + Math.round(e.chance * 100) + "%";
        return s;
    }

    private static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 0 && n < r.length ? r[n] : String.valueOf(n);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, Lang.get("gui.kve.item.combat.title"));

        int x0 = L + 10;
        int colW = (GUI_W - 20 - 8) / 3;
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.item.combat.lifesteal"), x0, statsY - 10, colW, KveGuiStyle.th(0xFF7BC8E0));
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.item.combat.ignite"), x0 + colW + 4, statsY - 10, colW, KveGuiStyle.th(0xFF7BC8E0));
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.item.combat.cooldown"), x0 + (colW + 4) * 2, statsY - 10, colW, KveGuiStyle.th(0xFF7BC8E0));

        List<FoodEffect> list = currentList();
        String hint = Lang.get(passiveTab ? "gui.kve.item.combat.passive_hint" : "gui.kve.item.combat.hit_hint");
        KveGuiStyle.drawTextSafe(g, hint, x0, listLabelY, GUI_W - 20, KveGuiStyle.ACCENT_GOLD);
        int editW = KveGuiStyle.btnWidth(Lang.get("gui.kve.btn.edit"));
        int textW = GUI_W - 20 - 16 - editW - 8;
        for (int i = 0; i < EFFECT_ROWS_VISIBLE; i++) {
            int idx = i + effectScroll;
            if (idx >= list.size()) break;
            int ry = listTop + i * EFFECT_ROW_H;
            KveGuiStyle.drawTextSafe(g, describe(list.get(idx), !passiveTab), x0, ry + 2, textW, KveGuiStyle.TEXT_LABEL);
        }
        if (list.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.item.combat.empty"), x0, listTop + 2, GUI_W - 20, KveGuiStyle.th(0xFF667788));
        } else if (list.size() > EFFECT_ROWS_VISIBLE) {
            KveGuiStyle.drawTextSafe(g, (effectScroll + 1) + "-" + Math.min(list.size(), effectScroll + EFFECT_ROWS_VISIBLE) + "/" + list.size(),
                    x0, listTop + EFFECT_ROWS_VISIBLE * EFFECT_ROW_H + 1, GUI_W - 20, KveGuiStyle.th(0xFF556677));
        }

        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        int maxScroll = Math.max(0, currentList().size() - EFFECT_ROWS_VISIBLE);
        int next = Math.max(0, Math.min(maxScroll, effectScroll - (int) Math.signum(delta)));
        if (next != effectScroll) { effectScroll = next; init(); }
        return true;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override public boolean isPauseScreen() { return false; }
}
