package com.kve.client.gui;

import com.kve.common.Lang;

import com.kve.common.kubejs.KubeJSItemAttrExporter.AttrMod;
import com.kve.common.kubejs.KubeJSItemAttrExporter.Op;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class AttributeValueScreen extends Screen {

    private static final int GUI_W = 260;
    private static final int GUI_H = 110;

    private final Screen parent;
    private final String attrId;
    private final String displayName;
    private final Consumer<AttrMod> onConfirm;

    private EditBox valueBox;
    private Op selectedOp = Op.ADDITION;
    private double initialValue = 0;

    private int L, T;
    private String errorMsg = null;

    public AttributeValueScreen(Screen parent, String attrId, String displayName, Consumer<AttrMod> onConfirm) {
        super(Lang.c("gui.kve.attr.value_title"));
        this.parent = parent;
        this.attrId = attrId;
        this.displayName = displayName;
        this.onConfirm = onConfirm;
    }

    public AttributeValueScreen withExisting(AttrMod existing) {
        if (existing != null) {
            this.initialValue = existing.value;
            this.selectedOp = existing.op;
        }
        return this;
    }

    @Override
    protected void init() {
        L = (width  - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        clearWidgets();
        setFocused(null);

        valueBox = new KveScaledEditBox(font, L + 10, T + 36, GUI_W - 20, 14, Component.literal("valor"));
        valueBox.setMaxLength(16);
        valueBox.setValue(fmt(initialValue));
        valueBox.setResponder(s -> {
            errorMsg = null;
            boolean ok;
            try { Double.parseDouble(s.trim().replace(',', '.')); ok = true; } catch (Exception e) { ok = false; }
            valueBox.setTextColor(ok ? 0xFFE0E0E0 : 0xFFFF6B6B);
        });
        addRenderableWidget(valueBox);
        setInitialFocus(valueBox);

        int opY = T + 58;
        int opW = (GUI_W - 24) / 3;
        addRenderableWidget(new KvePlateButton(L + 8, opY, opW, 16, opLabel(Op.ADDITION), b -> { selectedOp = Op.ADDITION; init(); }));
        addRenderableWidget(new KvePlateButton(L + 8 + opW + 4, opY, opW, 16, opLabel(Op.MULTIPLY_BASE), b -> { selectedOp = Op.MULTIPLY_BASE; init(); }));
        addRenderableWidget(new KvePlateButton(L + 8 + (opW + 4) * 2, opY, opW, 16, opLabel(Op.MULTIPLY_TOTAL), b -> { selectedOp = Op.MULTIPLY_TOTAL; init(); }));

        addRenderableWidget(new KvePlateButton(L + GUI_W / 2 - 70, T + GUI_H - 20, 70, 16, Component.literal(Lang.get("gui.kve.btn.confirm")), b -> confirm()).withPrimary(true));
        addRenderableWidget(new KvePlateButton(L + GUI_W / 2 + 2, T + GUI_H - 20, 70, 16, Component.literal(Lang.get("gui.kve.btn.cancel")), b -> onClose()));
    }

    private Component opLabel(Op op) {
        String prefix = (selectedOp == op) ? "> " : "";
        String label = switch (op) {
            case ADDITION -> "Suma";
            case MULTIPLY_BASE -> "% Base";
            case MULTIPLY_TOTAL -> "% Total";
        };
        return Component.literal(prefix + label);
    }

    private void confirm() {
        double val;
        try {
            val = Double.parseDouble(valueBox.getValue().trim().replace(',', '.'));
        } catch (Exception e) {
            errorMsg = Lang.get("gui.kve.attr.value.invalid");
            return;
        }
        errorMsg = null;
        onConfirm.accept(new AttrMod(attrId, val, selectedOp));
    }

    private String fmt(double v) {
        return (v == Math.floor(v)) ? String.valueOf((long) v) : String.valueOf(v);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, null);

        String name = displayName.length() > 34 ? displayName.substring(0, 33) + "~" : displayName;
        KveGuiStyle.drawTextSafe(g, name, L + 4, T + 3, GUI_W - 8, KveGuiStyle.ACCENT_GOLD);
        KveGuiStyle.drawTextSafe(g, attrId, L + 10, T + 22, GUI_W - 20, KveGuiStyle.TEXT_MUTED);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.attr.value"), L + 10, T + 36 - 10, GUI_W - 20, KveGuiStyle.TEXT_LABEL);

        if (errorMsg != null) {
            KveGuiStyle.drawTextSafe(g, errorMsg, L + 10, T + 76, GUI_W - 20, KveGuiStyle.TEXT_ERROR);
        }

        super.render(g, mx, my, pt);
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