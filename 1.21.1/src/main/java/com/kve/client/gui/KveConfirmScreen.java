package com.kve.client.gui;

import com.kve.common.Lang;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public class KveConfirmScreen extends Screen {

    private static final int W = 240;
    private static final int BTN_W = 90;
    private static final int BTN_H = 18;
    private static final int BTN_GAP = 10;

    private final BooleanConsumer onResult;
    private final Component message;

    private List<String> messageLines;
    private int windowH;
    private int cx, cy;

    public KveConfirmScreen(BooleanConsumer onResult, Component title, Component message) {
        super(title);
        this.onResult = onResult;
        this.message = message;
        this.behind = net.minecraft.client.Minecraft.getInstance().screen;
        this.danger = isDestructive(title.getString() + " " + message.getString());
    }

    private final Screen behind;
    private final boolean danger;

    private static final String[] DESTRUCTIVE = {"elimin", "borr", "quitar", "descart", "sobrescrib", "restablec",
            "delete", "remove", "discard", "overwrite", "reset", "clear"};

    /** UX accion destructiva -> "Si" en rojo e icono de aviso en el titulo. */
    static boolean isDestructive(String text) {
        String t = text.toLowerCase(java.util.Locale.ROOT);
        for (String w : DESTRUCTIVE) if (t.contains(w)) return true;
        return false;
    }

    @Override
    protected void init() {
        messageLines = KveGuiStyle.wrapTextSafe(font, message.getString(), W - 16);
        int titleH = KveGuiStyle.TITLEBAR_H;
        int msgH = messageLines.size() * font.lineHeight;
        windowH = titleH + KveGuiStyle.PAD + msgH + KveGuiStyle.PAD + BTN_H + KveGuiStyle.PAD + 10;
        windowH = Math.max(windowH, 90);

        cx = (width - W) / 2;
        cy = (height - windowH) / 2;

        int by = cy + windowH - KveGuiStyle.PAD - BTN_H;
        int totalBtnW = BTN_W * 2 + BTN_GAP;
        int bx = cx + (W - totalBtnW) / 2;

        addRenderableWidget(new KvePlateButton(bx, by, BTN_W, BTN_H,
                Component.literal(Lang.get("gui.kve.confirm.yes")),
                b -> onResult.accept(true)).withDanger(danger));
        addRenderableWidget(new KvePlateButton(bx + BTN_W + BTN_GAP, by, BTN_W, BTN_H,
                Component.literal(Lang.get("gui.kve.confirm.no")),
                b -> onResult.accept(false)));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (!renderBehind(g, pt)) renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, cx, cy, W, windowH, (danger ? "\u26a0 " : "") + getTitle().getString());

        int ty = cy + KveGuiStyle.TITLEBAR_H + 4;
        for (String line : messageLines) {
            KveGuiStyle.drawCenteredTextSafe(g, line, cx + W / 2, ty, KveGuiStyle.TEXT_PRIMARY);
            ty += font.lineHeight;
        }
        KveGuiStyle.drawCenteredTextSafe(g, Lang.get(danger ? "gui.kve.confirm.keys_danger" : "gui.kve.confirm.keys"),
                cx + W / 2, cy + windowH - KveGuiStyle.PAD - BTN_H - 11, KveGuiStyle.th(0xFF667788));

        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
    }

    private boolean renderBehind(GuiGraphics g, float pt) {
        if (behind == null || behind == this || behind.width != width || behind.height != height) return false;
        try {
            behind.render(g, -1000, -1000, pt);
        } catch (Throwable t) {
            return false;
        }
        g.flush();
        com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT, net.minecraft.client.Minecraft.ON_OSX);
        g.fill(0, 0, width, height, 0xB0000000);
        return true;
    }

    /**
     * Teclado. S/Y = Si, N = No (Esc ya era No). Enter = Si
     * solo si la accion NO es destructiva - para borrar/descartar hace falta
     * un gesto explicito (clic o S), asi un Enter apurado no pierde nada.
     */
    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        boolean enter = key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER;
        if ((enter && !danger) || key == org.lwjgl.glfw.GLFW.GLFW_KEY_S || key == org.lwjgl.glfw.GLFW.GLFW_KEY_Y) {
            onResult.accept(true);
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_N) { onResult.accept(false); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() {
        onResult.accept(false);
    }
}
