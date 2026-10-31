package com.kve.client.gui.properties;

import com.kve.client.gui.picker.EffectPickerScreen;
import com.kve.client.gui.picker.EffectValueScreen;
import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KvePlateButton;
import com.kve.client.gui.KveScaledEditBox;

import com.kve.common.Lang;

import com.kve.common.kubejs.KubeJSItemAttrExporter.FoodEffect;
import com.kve.common.kubejs.KubeJSItemAttrExporter.FoodProps;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * Editor for a single item's {@code FoodProperties}: nutrition (hunger
 * restored), saturation modifier, the always-edible/fast-food/meat flags,
 * and a scrollable list of "chance to also apply this status effect when
 * eaten" entries. Reached from {@link ItemAttributeScreen}'s "Food..."
 * button; writes back via {@link ItemAttributeScreen#applyDraftFood()}.
 * <p>
 * See {@code KubeJSItemAttrExporter#appendFoodBlock} for the caveat on the
 * exact KubeJS {@code FoodJS} method names used when this is exported.
 */
@OnlyIn(Dist.CLIENT)
public class ItemFoodScreen extends Screen {

    private static final int GUI_W = 280;
    private static final int GUI_H = 230;
    private static final int EFFECT_ROWS_VISIBLE = 5;
    private static final int EFFECT_ROW_H = 14;

    private final ItemAttributeScreen parent;

    private boolean enabled;
    private EditBox nutritionBox;
    private EditBox saturationBox;
    private boolean alwaysEdible;
    private boolean fastFood;
    private boolean meat;
    private final List<FoodEffect> effects = new ArrayList<>();
    private int effectScroll = 0;

    private final int initialNutrition;
    private final float initialSaturation;

    private int L, T;

    public ItemFoodScreen(ItemAttributeScreen parent, FoodProps existing) {
        super(Lang.c("gui.kve.item.food.title"));
        this.parent = parent;
        if (existing != null) {
            this.enabled = true;
            this.alwaysEdible = existing.alwaysEdible;
            this.fastFood = existing.fastFood;
            this.meat = existing.meat;
            for (FoodEffect fx : existing.effects) effects.add(fx.copy());
        } else {
            this.enabled = false;
        }
        this.initialNutrition = existing != null ? existing.nutrition : 4;
        this.initialSaturation = existing != null ? existing.saturationMod : 0.3f;
    }

    @Override
    protected void init() {
        L = (width  - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        clearWidgets();
        setFocused(null);

        int fx0 = L + 10;
        int fy = T + 18;

        addRenderableWidget(new KvePlateButton(fx0, fy, GUI_W - 20, 14, Component.literal(Lang.get("gui.kve.item.food.enabled") + ": " + (enabled ? "ON" : "OFF")),
                b -> { enabled = !enabled; init(); }));
        fy += 18;

        if (enabled) {
            nutritionBox = new KveScaledEditBox(font, fx0 + 70, fy, 60, 12, Component.literal("4"));
            nutritionBox.setMaxLength(4);
            nutritionBox.setValue(String.valueOf(initialNutrition));
            addRenderableWidget(nutritionBox);
            fy += 16;

            saturationBox = new KveScaledEditBox(font, fx0 + 70, fy, 60, 12, Component.literal("0.3"));
            saturationBox.setMaxLength(8);
            saturationBox.setValue(fmt(initialSaturation));
            addRenderableWidget(saturationBox);
            fy += 20;

            int toggleW = (GUI_W - 20 - 8) / 3;
            addRenderableWidget(new KvePlateButton(fx0, fy, toggleW, 14, Component.literal(toggleLabel(Lang.get("gui.kve.item.food.always_edible"), alwaysEdible)),
                    b -> { alwaysEdible = !alwaysEdible; init(); }));
            addRenderableWidget(new KvePlateButton(fx0 + toggleW + 4, fy, toggleW, 14, Component.literal(toggleLabel(Lang.get("gui.kve.item.food.fast"), fastFood)),
                    b -> { fastFood = !fastFood; init(); }));
            addRenderableWidget(new KvePlateButton(fx0 + (toggleW + 4) * 2, fy, toggleW, 14, Component.literal(toggleLabel(Lang.get("gui.kve.item.food.meat"), meat)),
                    b -> { meat = !meat; init(); }));
            fy += 20;

            int listTop = fy;
            addRenderableWidget(new KvePlateButton(fx0, listTop, GUI_W - 20, 14, Component.literal("+ " + Lang.get("gui.kve.effect.title")),
                    b -> openEffectPicker()));
            fy += 18;
            fy += 10;

            for (int i = 0; i < EFFECT_ROWS_VISIBLE; i++) {
                int idx = i + effectScroll;
                if (idx >= effects.size()) break;
                int ry = fy + i * EFFECT_ROW_H;
                int rowIdx = idx;
                addRenderableWidget(new KvePlateButton(L + GUI_W - 60, ry, 30, 12, Component.literal("edit"), b -> editEffect(rowIdx)));
                addRenderableWidget(new KvePlateButton(L + GUI_W - 28, ry, 16, 12, Component.literal("X"), b -> removeEffect(rowIdx)));
            }
        }

        addRenderableWidget(new KvePlateButton(L + GUI_W / 2 - 35, T + GUI_H - 20, 70, 16, Component.literal(Lang.get("gui.kve.btn.confirm")), b -> save()).withPrimary(true));
        addRenderableWidget(new KvePlateButton(L + GUI_W - 17, T + 2, 15, 14, Component.literal("X"), b -> onClose()));
    }

    private String toggleLabel(String base, boolean value) {
        return (value ? "> " : "") + base;
    }

    private void openEffectPicker() {
        minecraft.setScreen(new EffectPickerScreen(this, effectId -> {
            minecraft.setScreen(new EffectValueScreen(this, effectId, effectId, fx -> {
                effects.add(fx);
                minecraft.setScreen(this);
                init();
            }));
        }));
    }

    private void editEffect(int idx) {
        if (idx < 0 || idx >= effects.size()) return;
        FoodEffect existing = effects.get(idx);
        minecraft.setScreen(new EffectValueScreen(this, existing.effectId, existing.effectId, fx -> {
            effects.set(idx, fx);
            minecraft.setScreen(this);
            init();
        }).withExisting(existing));
    }

    private void removeEffect(int idx) {
        if (idx < 0 || idx >= effects.size()) return;
        effects.remove(idx);
        int maxScroll = Math.max(0, effects.size() - EFFECT_ROWS_VISIBLE);
        effectScroll = Math.max(0, Math.min(effectScroll, maxScroll));
        init();
    }

    private void save() {
        if (!enabled) {
            parent.applyDraftFood(null);
            minecraft.setScreen(parent);
            return;
        }
        FoodProps props = new FoodProps();
        props.nutrition = parseInt(nutritionBox, initialNutrition);
        props.saturationMod = parseFloat(saturationBox, initialSaturation);
        props.alwaysEdible = alwaysEdible;
        props.fastFood = fastFood;
        props.meat = meat;
        props.effects.addAll(effects);
        parent.applyDraftFood(props);
        minecraft.setScreen(parent);
    }

    private int parseInt(EditBox b, int def) {
        if (b == null) return def;
        try { return Integer.parseInt(b.getValue().trim()); } catch (Exception e) { return def; }
    }

    private float parseFloat(EditBox b, float def) {
        if (b == null) return def;
        try { return Float.parseFloat(b.getValue().trim()); } catch (Exception e) { return def; }
    }

    private String fmt(float v) {
        return (v == Math.floor(v)) ? String.valueOf((int) v) : String.valueOf(v);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, Lang.get("gui.kve.item.food.title"));

        if (enabled) {
            int fx0 = L + 10;
            int fy = T + 18 + 18;
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.item.food.nutrition"), fx0, fy + 2, 66, KveGuiStyle.th(0xFF7BC8E0));
            fy += 16;
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.item.food.saturation"), fx0, fy + 2, 66, KveGuiStyle.th(0xFF7BC8E0));
            fy += 20 + 20;

            // Must mirror init(): "+Effect" button at fy, then +18 to sit
            // right below it (labelY), then +10 reserved for this label
            // before the entry rows start.
            int labelY = fy + 18;
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.effect.title") + " (" + effects.size() + "):",
                    fx0, labelY, GUI_W - 20, KveGuiStyle.ACCENT_GOLD);
            int listTop = labelY + 10;
            for (int i = 0; i < EFFECT_ROWS_VISIBLE; i++) {
                int idx = i + effectScroll;
                if (idx >= effects.size()) break;
                FoodEffect e = effects.get(idx);
                int ry = listTop + i * EFFECT_ROW_H;
                String label = ItemCombatScreen.describe(e, true);
                KveGuiStyle.drawTextSafe(g, label, fx0, ry + 2, GUI_W - 20 - 66, KveGuiStyle.TEXT_LABEL);
            }
        }

        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        int maxScroll = Math.max(0, effects.size() - EFFECT_ROWS_VISIBLE);
        effectScroll = Math.max(0, Math.min(maxScroll, effectScroll - (int) Math.signum(delta)));
        return true;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override public boolean isPauseScreen() { return false; }
}