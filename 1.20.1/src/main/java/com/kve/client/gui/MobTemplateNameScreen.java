package com.kve.client.gui;

import com.kve.common.Lang;
import com.kve.common.persist.MobTemplateStore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.function.Consumer;

/**
 * Dialogo minimo "nombre de plantilla", usado por MobEditorScreen#saveAsTemplate().
 * Mismo look (drawWindow()/KvePlateButton) que KveConfirmScreen, pero con un
 * EditBox de texto en vez de un mensaje fijo. Si el nombre ya existe, delega
 * en KveConfirmScreen para confirmar el sobreescribir antes de llamar al
 * callback - el callback en si nunca decide eso, solo recibe un nombre ya
 * aprobado para guardar.
 */
@OnlyIn(Dist.CLIENT)
public class MobTemplateNameScreen extends Screen {

    private static final int W = 220;
    private static final int H = 76;
    private static final int BTN_W = 80;
    private static final int BTN_H = 18;

    private final Screen parent;
    private final Consumer<String> onConfirm;
    private KveScaledEditBox nameBox;
    private int cx, cy;

    public MobTemplateNameScreen(Screen parent, Consumer<String> onConfirm) {
        super(Component.literal(Lang.get("gui.kve.mob.template.save_title")));
        this.parent = parent;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        cx = (width - W) / 2;
        cy = (height - H) / 2;

        nameBox = new KveScaledEditBox(font, cx + KveGuiStyle.PAD, cy + KveGuiStyle.TITLEBAR_H + 8,
                W - KveGuiStyle.PAD * 2, 14, Component.literal(Lang.get("gui.kve.mob.template.name_hint")));
        nameBox.setMaxLength(48);
        nameBox.setHint(Component.literal(Lang.get("gui.kve.mob.template.name_hint")));
        addRenderableWidget(nameBox);
        setFocused(nameBox);

        int by = cy + H - KveGuiStyle.PAD - BTN_H;
        addRenderableWidget(new KvePlateButton(cx + KveGuiStyle.PAD, by, BTN_W, BTN_H,
                Component.literal(Lang.get("gui.kve.confirm.yes")), b -> trySubmit()));
        addRenderableWidget(new KvePlateButton(cx + W - KveGuiStyle.PAD - BTN_W, by, BTN_W, BTN_H,
                Component.literal(Lang.get("gui.kve.confirm.no")), b -> minecraft.setScreen(parent)));
    }

    private void trySubmit() {
        String name = nameBox.getValue().trim();
        if (name.isEmpty()) return;
        if (MobTemplateStore.exists(name)) {
            minecraft.setScreen(new KveConfirmScreen(
                    ok -> {
                        if (ok) { onConfirm.accept(name); }
                        else minecraft.setScreen(MobTemplateNameScreen.this);
                    },
                    Component.literal(Lang.get("gui.kve.mob.template.overwrite_title")),
                    Component.literal(Lang.get("gui.kve.mob.template.overwrite_msg"))));
            return;
        }
        onConfirm.accept(name);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        KveGuiStyle.drawWindow(g, cx, cy, W, H, getTitle().getString());
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
