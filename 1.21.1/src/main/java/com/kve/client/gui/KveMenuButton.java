package com.kve.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * Boton "Mas ▾" que abre una lista de acciones con su nombre. Guarda las acciones
 * poco usadas de un editor sin esconderlas detras de simbolos.
 * <p>
 * La lista se dibuja encima de todo (z alto). Los clics los toma primero
 * {@link #consumeClick}, que el Studio y cada editor llaman al principio de su
 * mouseClicked: un clic en la lista ejecuta la accion; un clic afuera solo cierra el menu.
 */
@OnlyIn(Dist.CLIENT)
public class KveMenuButton extends KvePlateButton {

    /** Una opcion del menu; {@code label} null es una linea que separa grupos. */
    public record Item(String label, Runnable action, boolean danger, boolean active) {
        public static Item separator() { return new Item(null, null, false, false); }
        boolean isSeparator() { return label == null; }
    }

    private static final int SEP_H = 5, PAD = 3;

    /** Alto de una opcion: crece con la escala de letra de Ajustes. */
    private static int itemH() { return KveGuiStyle.rowHeight(14); }
    private static KveMenuButton open;
    private static long openRenderedAt;

    private final List<Item> items;

    public KveMenuButton(int x, int y, int w, int h, Component label, List<Item> items) {
        super(x, y, w, h, label, b -> ((KveMenuButton) b).toggle());
        this.items = new ArrayList<>(items);
    }

    private void toggle() { open = open == this ? null : this; openRenderedAt = System.currentTimeMillis(); }

    public static void closeAll() { open = null; }

    private int listW() {
        Font font = Minecraft.getInstance().font;
        int w = getWidth();
        for (Item it : items) if (!it.isSeparator()) w = Math.max(w, KveGuiStyle.textWidth(it.label()) + PAD * 2 + 8);
        return w;
    }

    private static int heightOf(Item it) { return it.isSeparator() ? SEP_H : itemH(); }

    private int listH() {
        int h = PAD * 2;
        for (Item it : items) h += heightOf(it);
        return h;
    }

    /** Opcion bajo la coordenada y de la lista, o -1 (los separadores no cuentan). */
    private int itemAt(int listTop, double my) {
        int iy = listTop + PAD;
        for (int i = 0; i < items.size(); i++) {
            int h = heightOf(items.get(i));
            if (my >= iy && my < iy + h) return items.get(i).isSeparator() ? -1 : i;
            iy += h;
        }
        return -1;
    }

    /** Esquina de la lista: debajo del boton si entra, si no encima; nunca fuera de la pantalla. */
    private int[] listPos() {
        var win = Minecraft.getInstance().getWindow();
        int sw = win.getGuiScaledWidth(), sh = win.getGuiScaledHeight();
        int lw = listW(), lh = listH();
        int lx = Math.max(2, Math.min(getX(), sw - lw - 2));
        int below = getY() + getHeight() + 1;
        int ly = below + lh <= sh - 2 ? below : Math.max(2, getY() - lh - 1);
        return new int[]{lx, ly, lw, lh};
    }

    @Override
    public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
        super.renderWidget(g, mx, my, pt);
        if (open != this) return;
        openRenderedAt = System.currentTimeMillis();
        int[] p = listPos();
        Font font = Minecraft.getInstance().font;
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(p[0], p[1], p[0] + p[2], p[1] + p[3], 0xF0101826);
        g.renderOutline(p[0], p[1], p[2], p[3], KveGuiStyle.BORDER_OUTER);
        int iy = p[1] + PAD;
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            if (it.isSeparator()) {
                g.fill(p[0] + 4, iy + 2, p[0] + p[2] - 4, iy + 3, KveGuiStyle.BORDER_SUBTLE);
                iy += SEP_H;
                continue;
            }
            int ih = itemH();
            boolean hov = it.active() && mx >= p[0] && mx < p[0] + p[2] && my >= iy && my < iy + ih;
            if (hov) g.fill(p[0] + 1, iy, p[0] + p[2] - 1, iy + ih, KveGuiStyle.BORDER_FIELD);
            int color = !it.active() ? KveGuiStyle.TEXT_MUTED : it.danger() ? KveGuiStyle.TEXT_ERROR
                    : hov ? KveGuiStyle.ACCENT_GOLD : KveGuiStyle.TEXT_PRIMARY;
            KveGuiStyle.drawText(g, it.label(), p[0] + PAD + 4, iy + (ih - Math.round(font.lineHeight * KveGuiStyle.uiTextScale())) / 2 + 1, color);
            iy += ih;
        }
        g.pose().popPose();
    }

    /**
     * Si hay un menu abierto, el clic es suyo: en la lista corre la accion, en su boton lo
     * cierra, y en cualquier otro lado solo lo cierra (no activa lo que haya debajo).
     */
    public static boolean consumeClick(double mx, double my, int btn) {
        KveMenuButton m = open;
        if (m == null) return false;
        // un menu de una pantalla que ya se reconstruyo no se dibuja: se descarta sin tragarse el clic
        if (System.currentTimeMillis() - openRenderedAt > 500) { open = null; return false; }
        open = null;
        if (m.isMouseOver(mx, my)) return true;
        int[] p = m.listPos();
        if (btn == 0 && mx >= p[0] && mx < p[0] + p[2] && my >= p[1] + PAD && my < p[1] + p[3] - PAD) {
            int i = m.itemAt(p[1], my);
            if (i >= 0 && m.items.get(i).active()) m.items.get(i).action().run();
        }
        return true;
    }

    public static boolean isOpen() { return open != null; }
}
