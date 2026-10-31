package com.kve.client.gui;

import com.kve.common.Lang;

import com.kve.common.kubejs.KubeJSItemAttrExporter.FoodEffect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class EffectValueScreen extends Screen {

    private static final int GUI_W = 280;
    private static final int GUI_H = 150;
    private static final int[] PRESET_TICKS = {100, 600, 1200, 6000};

    private final Screen parent;
    private final String effectId;
    private final String displayName;
    private final Consumer<FoodEffect> onConfirm;

    private EditBox durationBox;
    private EditBox amplifierBox;
    private EditBox chanceBox;
    private Button confirmBtn;

    private int initialDuration = 100;
    private int initialAmplifier = 0;
    private float initialChance = 1.0f;

    private int L, T;

    public EffectValueScreen(Screen parent, String effectId, String displayName, Consumer<FoodEffect> onConfirm) {
        super(Lang.c("gui.kve.effect.value_title"));
        this.parent = parent;
        this.effectId = effectId;
        this.displayName = displayName == null || displayName.equals(effectId) ? KveEffectInfo.name(effectId) : displayName;
        this.onConfirm = onConfirm;
    }

    public EffectValueScreen withExisting(FoodEffect existing) {
        if (existing != null) {
            this.initialDuration = existing.durationTicks;
            this.initialAmplifier = existing.amplifier;
            this.initialChance = existing.chance;
        }
        return this;
    }

    @Override
    protected void init() {
        L = (width  - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        String keepDur = durationBox != null ? durationBox.getValue() : String.valueOf(initialDuration);
        String keepAmp = amplifierBox != null ? amplifierBox.getValue() : String.valueOf(initialAmplifier + 1);
        String keepChance = chanceBox != null ? chanceBox.getValue() : fmt(initialChance);
        clearWidgets();
        setFocused(null);

        int fieldW = (GUI_W - 30) / 3;
        int fy = T + 46;

        durationBox = new KveScaledEditBox(font, L + 10, fy, fieldW, 14, Component.literal("ticks"));
        durationBox.setMaxLength(8);
        durationBox.setValue(keepDur);
        durationBox.setResponder(v -> validate());
        addRenderableWidget(durationBox);
        setInitialFocus(durationBox);

        amplifierBox = new KveScaledEditBox(font, L + 10 + fieldW + 5, fy, fieldW, 14, Component.literal("nivel"));
        amplifierBox.setMaxLength(4);
        amplifierBox.setValue(keepAmp);
        amplifierBox.setResponder(v -> validate());
        addRenderableWidget(amplifierBox);

        chanceBox = new KveScaledEditBox(font, L + 10 + (fieldW + 5) * 2, fy, fieldW, 14, Component.literal("0-1"));
        chanceBox.setMaxLength(7);
        chanceBox.setValue(keepChance);
        chanceBox.setResponder(v -> validate());
        addRenderableWidget(chanceBox);

        int px = L + 10;
        for (int ticks : PRESET_TICKS) {
            String label = KveEffectInfo.duration(ticks);
            int bw = font.width(label) + 10;
            addRenderableWidget(new KvePlateButton(px, fy + 30, bw, 12, Component.literal(label),
                    b -> durationBox.setValue(String.valueOf(ticks))));
            px += bw + 3;
        }

        confirmBtn = addRenderableWidget(new KvePlateButton(L + GUI_W / 2 - 72, T + GUI_H - 20, 70, 16,
                Component.literal(Lang.get("gui.kve.btn.confirm")), b -> confirm()).withPrimary(true));
        addRenderableWidget(new KvePlateButton(L + GUI_W / 2 + 2, T + GUI_H - 20, 70, 16, Component.literal(Lang.get("gui.kve.btn.cancel")), b -> onClose()));
        validate();
    }

    private static int parseInt(EditBox b) {
        try { return Integer.parseInt(b.getValue().trim()); } catch (Exception e) { return Integer.MIN_VALUE; }
    }

    private static float parseChance(EditBox b) {
        String t = b.getValue().trim().replace(',', '.');
        boolean pct = t.endsWith("%");
        if (pct) t = t.substring(0, t.length() - 1).trim();
        try {
            float v = Float.parseFloat(t);
            return pct ? v / 100f : v;
        } catch (Exception e) {
            return Float.NaN;
        }
    }

    private boolean durationOk()  { int d = parseInt(durationBox); return d != Integer.MIN_VALUE && d >= 0; }
    private boolean levelOk()     { int a = parseInt(amplifierBox); return a != Integer.MIN_VALUE && a >= 1 && a <= 256; }
    private boolean chanceOk()    { float c = parseChance(chanceBox); return !Float.isNaN(c) && c >= 0f && c <= 1f; }

    private void validate() {
        if (durationBox == null || amplifierBox == null || chanceBox == null) return;
        durationBox.setTextColor(durationOk() ? 0xFFE0E0E0 : 0xFFFF6B6B);
        amplifierBox.setTextColor(levelOk() ? 0xFFE0E0E0 : 0xFFFF6B6B);
        chanceBox.setTextColor(chanceOk() ? 0xFFE0E0E0 : 0xFFFF6B6B);
        if (confirmBtn != null) confirmBtn.active = durationOk() && levelOk() && chanceOk();
    }

    private void confirm() {
        if (!(durationOk() && levelOk() && chanceOk())) return;
        int duration = parseInt(durationBox);
        int amplifier = parseInt(amplifierBox) - 1;
        float chance = parseChance(chanceBox);
        onConfirm.accept(new FoodEffect(effectId, Math.max(0, duration), Math.max(0, amplifier), chance));
    }

    private String fmt(float v) {
        return (v == Math.floor(v)) ? String.valueOf((int) v) : String.valueOf(v);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, displayName);
        KveEffectInfo.drawIcon(g, effectId, L + 10, T + 19, 12);
        KveGuiStyle.drawTextSafe(g, effectId, L + 26, T + 21, GUI_W - 36, KveEffectInfo.categoryColor(effectId));

        int fieldW = (GUI_W - 30) / 3;
        int labelY = T + 46 - 10;
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.effect.duration"), L + 10, labelY, fieldW, KveGuiStyle.th(0xFF7BC8E0));
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.effect.level"), L + 10 + fieldW + 5, labelY, fieldW, KveGuiStyle.th(0xFF7BC8E0));
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.effect.chance"), L + 10 + (fieldW + 5) * 2, labelY, fieldW, KveGuiStyle.th(0xFF7BC8E0));

        int hintY = T + 46 + 17;
        int hint = KveGuiStyle.th(0xFF8899AA), bad = 0xFFFF6B6B;
        KveGuiStyle.drawTextSafe(g, durationOk() ? "= " + KveEffectInfo.duration(parseInt(durationBox)) : Lang.get("gui.kve.effect.invalid"),
                L + 10, hintY, fieldW, durationOk() ? hint : bad);
        KveGuiStyle.drawTextSafe(g, levelOk() ? "= " + KveEffectInfo.level(parseInt(amplifierBox) - 1) : Lang.get("gui.kve.effect.invalid_level"),
                L + 10 + fieldW + 5, hintY, fieldW, levelOk() ? hint : bad);
        KveGuiStyle.drawTextSafe(g, chanceOk() ? "= " + KveEffectInfo.percent(parseChance(chanceBox)) : Lang.get("gui.kve.effect.invalid_chance"),
                L + 10 + (fieldW + 5) * 2, hintY, fieldW, chanceOk() ? hint : bad);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.effect.keys_hint"), L + 10, T + GUI_H - 32, GUI_W - 20, KveGuiStyle.th(0xFF556677));

        super.render(g, mx, my, pt);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { confirm(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override public boolean isPauseScreen() { return false; }
}
