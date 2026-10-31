package com.kve.client.gui;

import com.kve.common.Lang;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * Dialogo generico "un campo de texto + Aceptar/Cancelar". Mismo patron
 * visual ({@link KveGuiStyle#drawWindow()}/{@link KvePlateButton}) que
 * {@link KveConfirmScreen}, con un {@link KveScaledEditBox} en vez de un
 * mensaje fijo.
 * <p>
 * Deliberadamente sin validacion propia: el {@code onConfirm} recibe el
 * texto tal cual lo tipeo el jugador (recortado de espacios) y es el
 * caller quien decide si es valido (nombre vacio, extension, caracteres
 * permitidos, colision con un archivo existente) - esta clase no conoce
 * nada de archivos ni de KVE, es un dialogo de texto puro reusable para
 * lo que haga falta despues.
 */
@OnlyIn(Dist.CLIENT)
public class KveTextInputScreen extends Screen {

    private static final int W = 240;
    private static final int H = 82;
    private static final int BTN_W = 90;
    private static final int BTN_H = 18;

    private final Screen parent;
    private final String initialValue;
    private final String hint;
    private final Consumer<String> onConfirm;
    private KveScaledEditBox valueBox;
    private int cx, cy;

    public KveTextInputScreen(Screen parent, Component title, String initialValue, String hint,
                               Consumer<String> onConfirm) {
        super(title);
        this.parent = parent;
        this.initialValue = initialValue == null ? "" : initialValue;
        this.hint = hint == null ? "" : hint;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        cx = (width - W) / 2;
        cy = (height - H) / 2;

        valueBox = new KveScaledEditBox(font, cx + KveGuiStyle.PAD, cy + KveGuiStyle.TITLEBAR_H + 10,
                W - KveGuiStyle.PAD * 2, 14, Component.literal(hint));
        valueBox.setMaxLength(96);
        valueBox.setHint(Component.literal(hint));
        valueBox.setValue(initialValue);
        addRenderableWidget(valueBox);
        setFocused(valueBox);

        int by = cy + H - KveGuiStyle.PAD - BTN_H;
        addRenderableWidget(new KvePlateButton(cx + KveGuiStyle.PAD, by, BTN_W, BTN_H,
                Component.literal(Lang.get("gui.kve.confirm.yes")), b -> trySubmit()));
        addRenderableWidget(new KvePlateButton(cx + W - KveGuiStyle.PAD - BTN_W, by, BTN_W, BTN_H,
                Component.literal(Lang.get("gui.kve.confirm.no")), b -> minecraft.setScreen(parent)));
    }

    private void trySubmit() {
        String v = valueBox.getValue().trim();
        if (v.isEmpty()) return;
        onConfirm.accept(v);
    }

    @Override
    public boolean keyPressed(int key, int scancode, int mods) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            trySubmit();
            return true;
        }
        return super.keyPressed(key, scancode, mods);
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
