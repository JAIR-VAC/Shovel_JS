package com.kve.client.gui.properties;

import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KvePlateButton;
import com.kve.client.gui.tag.TagMembersScreen;

import org.lwjgl.glfw.GLFW;
import com.kve.common.Lang;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class ItemHideOptionsScreen extends Screen {

    private static final int GUI_W = 200;
    private static final int ROW_H = 14;
    private static final int LIST_TOP = 20;

    private static final String[] OPTIONS = { "jei", "tab", "loot" };

    private final Screen parent;
    private final Consumer<Set<String>> onConfirm;
    private final Set<String> selected = new LinkedHashSet<>();

    private int L, T, guiH;

    /**
     * @param hideJei  estado actual de {@code draftHideViewers}
     * @param hideTab  estado actual de {@code draftHideCreative}
     */
    public ItemHideOptionsScreen(Screen parent, boolean hideJei, boolean hideTab, boolean hideLoot, Consumer<Set<String>> onConfirm) {
        super(Lang.c("gui.kve.item.hide_options.title"));
        this.parent = parent;
        this.onConfirm = onConfirm;
        if (hideJei) selected.add("jei");
        if (hideTab) selected.add("tab");
        if (hideLoot) selected.add("loot");
    }

    @Override
    protected void init() {
        guiH = LIST_TOP + OPTIONS.length * ROW_H + 26;
        L = (width - GUI_W) / 2;
        T = (height - guiH) / 2;
        clearWidgets();
        setFocused(null);

        addRenderableWidget(new KvePlateButton(L + 6, T + guiH - 16, (GUI_W - 16) / 2, 13,
                Component.literal(Lang.get("gui.kve.btn.save")), b -> confirm()).withPrimary(true));
        addRenderableWidget(new KvePlateButton(L + 10 + (GUI_W - 16) / 2, T + guiH - 16, (GUI_W - 16) / 2, 13,
                Component.literal(Lang.get("gui.kve.btn.cancel")), b -> minecraft.setScreen(parent)));
    }

    private void toggleRow(int idx) {
        String opt = OPTIONS[idx];
        if (!selected.add(opt)) selected.remove(opt);
    }

    private void confirm() {
        onConfirm.accept(new LinkedHashSet<>(selected));
        minecraft.setScreen(parent);
    }

    /** ESC vuelve al caller sin aplicar nada (mismo contrato que MobBehaviorFlagsPickerScreen/TagMembersScreen). */
    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, guiH, Lang.get("gui.kve.item.hide_options.title"));

        for (int i = 0; i < OPTIONS.length; i++) {
            int ry = T + LIST_TOP + i * ROW_H;
            boolean sel = selected.contains(OPTIONS[i]);
            g.fill(L + 4, ry, L + GUI_W - 4, ry + ROW_H - 1, sel ? KveGuiStyle.BORDER_FIELD : KveGuiStyle.BG_FIELD);
            KveGuiStyle.drawCheck(g, L + 6, ry + 2, sel, KveGuiStyle.isHovered(mx, my, L + 4, ry, GUI_W - 8, ROW_H - 1));
            KveGuiStyle.drawTextSafe(g, optionLabel(OPTIONS[i]), L + 19, ry + 2, GUI_W - 27, sel ? 0xFFAADDAA : KveGuiStyle.TEXT_LABEL);
        }
        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
    }

    private static String optionLabel(String opt) {
        return switch (opt) {
            case "jei" -> Lang.get("gui.kve.item.hide_viewers");
            case "tab" -> Lang.get("gui.kve.item.hide_creative");
            case "loot" -> Lang.get("gui.kve.item.hide_loot");
            default -> opt;
        };
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        for (int i = 0; i < OPTIONS.length; i++) {
            int ry = T + LIST_TOP + i * ROW_H;
            if (mx >= L + 4 && mx < L + GUI_W - 4 && my >= ry && my < ry + ROW_H - 1) {
                toggleRow(i);
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (getFocused() != null && getFocused().keyPressed(key, scan, mods)) return true;
        return true;
    }
    @Override public boolean isPauseScreen() { return false; }
}
