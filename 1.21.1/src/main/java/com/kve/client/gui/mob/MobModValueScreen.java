package com.kve.client.gui.mob;

import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KvePlateButton;
import com.kve.client.gui.KveScaledEditBox;

import com.kve.common.Lang;

import com.kve.common.kubejs.KubeJSMobExporter.AttrMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class MobModValueScreen extends Screen {

    private static final int GUI_W = 300;
    private static final int GUI_H = 172;

    private static final String[] QUICK_DIMS = {
            "minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"
    };

    private final Screen parent;
    private final String attrId;
    private final String displayName;
    private final Consumer<AttrMod> onConfirm;

    private EditBox valueBox;
    private EditBox dimBox;
    private EditBox probBox;

    private double initialValue = 0;
    private String initialDim = "";
    private double initialProb = 1.0;

    private Boolean supportedByPreview = null;
    private final String rangeHint;

    private int L, T;

    public MobModValueScreen(Screen parent, String attrId, String displayName, Consumer<AttrMod> onConfirm) {
        super(Lang.c("gui.kve.mob.attr_title"));
        this.parent = parent;
        this.attrId = attrId;
        this.displayName = displayName;
        this.onConfirm = onConfirm;
        this.rangeHint = computeRangeHint(attrId);
    }

    public MobModValueScreen withExisting(AttrMod existing) {
        if (existing != null) {
            this.initialValue = existing.value;
            this.initialDim   = existing.dimension == null ? "" : existing.dimension;
            this.initialProb  = existing.probability;
        }
        return this;
    }

    /** Optional: si el llamador tiene un preview de entidad activo, le dice a esta pantalla si el mob elegido realmente carga este atributo (ver javadoc de la clase). Se ignora silenciosamente si nunca se llama (rangeHint sigue mostrandose igual, solo no hay linea de aviso). */
    public MobModValueScreen withSupportInfo(boolean supported) {
        this.supportedByPreview = supported;
        return this;
    }

    private static String computeRangeHint(String attrId) {
        try {
            ResourceLocation rl = ResourceLocation.tryParse(attrId);
            Attribute attr = rl == null ? null : BuiltInRegistries.ATTRIBUTE.get(rl);
            if (attr instanceof RangedAttribute ranged) {
                return fmtStatic(ranged.getMinValue()) + " .. " + fmtStatic(ranged.getMaxValue())
                        + " (" + Lang.get("gui.kve.mob.attr_default") + " " + fmtStatic(attr.getDefaultValue()) + ")";
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String fmtStatic(double v) {
        return (v == Math.floor(v) && !Double.isInfinite(v)) ? String.valueOf((long) v) : String.valueOf(v);
    }

    @Override
    protected void init() {
        L = (width  - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        clearWidgets();
        setFocused(null);

        valueBox = new KveScaledEditBox(font, L + 10, T + 38, GUI_W - 20, 14, Component.literal("valor"));
        valueBox.setMaxLength(16);
        valueBox.setValue(fmt(initialValue));
        addRenderableWidget(valueBox);
        setInitialFocus(valueBox);

        dimBox = new KveScaledEditBox(font, L + 10, T + 74, GUI_W - 20, 14, Component.literal("dimension"));
        dimBox.setMaxLength(64);
        dimBox.setValue(initialDim);
        dimBox.setHint(Lang.c("gui.kve.mob.dim_hint"));
        addRenderableWidget(dimBox);

        int qdW = (GUI_W - 24) / QUICK_DIMS.length;
        for (int i = 0; i < QUICK_DIMS.length; i++) {
            String dim = QUICK_DIMS[i];
            String label = dim.substring(dim.indexOf(':') + 1);
            addRenderableWidget(new KvePlateButton(L + 8 + i * (qdW + 4), T + 90, qdW, 14, Component.literal(label), b -> dimBox.setValue(dim)));
        }

        probBox = new KveScaledEditBox(font, L + 10, T + 120, GUI_W - 20, 14, Component.literal("probabilidad"));
        probBox.setMaxLength(8);
        probBox.setValue(fmt(initialProb));
        addRenderableWidget(probBox);
        valueBox.setResponder(v -> revalidate());
        probBox.setResponder(v -> revalidate());
        revalidate();

        addRenderableWidget(new KvePlateButton(L + GUI_W / 2 - 70, T + GUI_H - 18, 70, 14, Component.literal(Lang.get("gui.kve.btn.confirm")), b -> confirm()).withPrimary(true));
        addRenderableWidget(new KvePlateButton(L + GUI_W / 2 + 2, T + GUI_H - 18, 70, 14, Component.literal(Lang.get("gui.kve.btn.cancel")), b -> onClose()));
    }

    private static double parseNum(String raw) {
        String t = raw == null ? "" : raw.trim().replace(',', '.');
        boolean pct = t.endsWith("%");
        if (pct) t = t.substring(0, t.length() - 1).trim();
        try { double v = Double.parseDouble(t); return pct ? v / 100.0 : v; } catch (Exception e) { return Double.NaN; }
    }

    private boolean valueOk() { return !Double.isNaN(parseNum(valueBox.getValue())); }
    private boolean probOk() {
        return probBox.getValue().isBlank()
                || !Double.isNaN(com.kve.common.kubejs.KubeJSMobExporter.parseProbability(probBox.getValue()));
    }

    private void revalidate() {
        if (valueBox == null || probBox == null) return;
        valueBox.setTextColor(valueOk() ? 0xFFE0E0E0 : 0xFFFF6B6B);
        probBox.setTextColor(probOk() ? 0xFFE0E0E0 : 0xFFFF6B6B);
    }

    private void confirm() {
        revalidate();
        if (!valueOk() || !probOk()) return;
        double val = parseNum(valueBox.getValue());
        double prob = probBox.getValue().isBlank() ? 1.0 : com.kve.common.kubejs.KubeJSMobExporter.parseProbability(probBox.getValue());
        String dim = dimBox.getValue().trim();
        onConfirm.accept(new AttrMod(attrId, val, dim, prob));
    }

    private String fmt(double v) {
        return (v == Math.floor(v)) ? String.valueOf((long) v) : String.valueOf(v);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, displayName);
        KveGuiStyle.drawTextSafe(g, attrId, L + 10, T + 22, GUI_W - 20, KveGuiStyle.th(0xFF6A99BB));

        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.mob.value"), L + 10, T + 38 - 10, GUI_W - 20, KveGuiStyle.th(0xFF7BC8E0));
        int hintY = T + 54;
        if (rangeHint != null) {
            KveGuiStyle.drawTextSafe(g, rangeHint, L + 10, hintY, GUI_W - 20, KveGuiStyle.th(0xFF6A99BB));
            hintY += 9;
        }
        if (Boolean.FALSE.equals(supportedByPreview)) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.mob.attr_unsupported"), L + 10, hintY, GUI_W - 20, 0xFF9FD08A);
        }
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.mob.dimension"), L + 10, T + 74 - 10, GUI_W - 20, KveGuiStyle.th(0xFF7BC8E0));
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.mob.probability"), L + 10, T + 120 - 10, GUI_W - 20, KveGuiStyle.th(0xFF7BC8E0));
        if (!valueOk() || !probOk()) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.mob.value_invalid"), L + 10, T + 136, GUI_W - 20, 0xFFFF6B6B);
        }

        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

 /** Enter confirma (Esc ya cancelaba). */
    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) { confirm(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override public boolean isPauseScreen() { return false; }
}
