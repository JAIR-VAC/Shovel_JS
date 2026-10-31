package com.kve.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public class KveScaledEditBox extends EditBox {

    private Component kveHint = null;
    private int kveTextColor = 0xE0E0E0;

    public KveScaledEditBox(Font font, int x, int y, int w, int h, Component message) {
        super(font, x, y, w, h, message);
    }

    private int kveMaxLength = 32;
    private boolean kveEditable = true;
    private boolean kveBordered = true;

    @Override
    public void setMaxLength(int len) {
        this.kveMaxLength = len;
        super.setMaxLength(len);
    }

    @Override
    public void setEditable(boolean editable) {
        this.kveEditable = editable;
        super.setEditable(editable);
    }

    @Override
    public void setBordered(boolean bordered) {
        this.kveBordered = bordered;
        super.setBordered(bordered);
    }

    private boolean showClear() {
        return kveEditable && kveBordered && isVisible() && active && getWidth() >= 50 && !getValue().isEmpty();
    }

    private boolean overClear(double mx, double my) {
        int cx = getX() + getWidth() - 10;
        return mx >= cx && mx < getX() + getWidth() && my >= getY() && my < getY() + getHeight();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && showClear() && overClear(mx, my)) {
            setValue("");
            setFocused(true);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    /**
 * con el campo enfocado y el mouse encima, la rueda sube o
     * baja un valor numerico (1 para enteros; 0.1/0.01... segun los decimales
     * que ya tenga). Solo con foco, para no robarle el scroll a la lista de atras.
     */
    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        if (!kveEditable || !isFocused() || !isMouseOver(mx, my) || delta == 0) return super.mouseScrolled(mx, my, scrollX, delta);
        String v = getValue().trim();
        if (!v.matches("-?\\d+(\\.\\d+)?")) return super.mouseScrolled(mx, my, scrollX, delta);
        int dot = v.indexOf('.');
        int decimals = dot < 0 ? 0 : v.length() - dot - 1;
        java.math.BigDecimal step = java.math.BigDecimal.ONE.movePointLeft(decimals);
        java.math.BigDecimal next = new java.math.BigDecimal(v).add(delta > 0 ? step : step.negate());
        setValue(next.setScale(decimals, java.math.RoundingMode.HALF_UP).toPlainString());
        return true;
    }

    @Override
    public void setTextColor(int color) {
        this.kveTextColor = color;
        super.setTextColor(color);
    }

    /** Nunca delega a vanilla (ver javadoc de clase) - guarda el hint local, mismo punto de entrada que cualquier call site ya usaba con EditBox comun. */
    @Override
    public void setHint(Component hint) {
        this.kveHint = hint;
    }

    @Override
    public void renderWidget(GuiGraphics g, int mx, int my, float partialTick) {
        boolean wasFocused = isFocused();
        boolean scaledValue = !getValue().isEmpty() && KveGuiStyle.uiTextScale() < 0.999f;
        if (scaledValue) {
            super.setTextColor(0x00000000);
            if (wasFocused) setFocused(false);
        }
        super.renderWidget(g, mx, my, partialTick);
        if (scaledValue) {
            super.setTextColor(kveTextColor);
            if (wasFocused) setFocused(true);
        }

        Font font = Minecraft.getInstance().font;
        drawKveDecorations(g, font, mx, my);
        String text = scaledValue ? getValue() : (kveHint != null && getValue().isEmpty() ? kveHint.getString() : null);
        if (text == null) return;

        int availW = Math.max(0, getWidth() - 8);
        float scale = KveGuiStyle.uiTextScale();
        if (font.width(text) > availW) {
            float fitScale = availW > 0 ? Math.min(scale, availW / (float) font.width(text)) : scale;
            scale = Math.max(0.5f, fitScale);
        }
        int color = scaledValue ? kveTextColor : KveGuiStyle.TEXT_MUTED;
        int x = getX() + 4;
        int y = getY() + (getHeight() - (int) (font.lineHeight * scale)) / 2;

        String safe = scale < 0.999f ? KveGuiStyle.truncate(font, text, (int) (availW / scale)) : KveGuiStyle.truncate(font, text, availW);

        if (scale < 0.999f) {
            float snapped = Math.round(scale * 20f) / 20f;
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().scale(snapped, snapped, 1f);
            g.drawString(font, safe, 0, 0, color, true);
            g.pose().popPose();
        } else {
            g.drawString(font, safe, x, y, color, true);
        }

        // Cursor propio, escalado, en reemplazo del que se silencio arriba
        // via setFocused(false). Aproximado (no reproduce seleccion con
        // drag ni scroll de texto largo - el resto del GUI no usa campos
        // multi-linea/largos), pero alineado con el texto que el jugador
        // realmente ve, a diferencia del cursor vanilla que hubiera usado
        // metricas sin escalar.
        if (scaledValue && wasFocused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            int cursorChars = Math.min(getCursorPosition(), safe.length());
            int cursorX = x + (int) (font.width(safe.substring(0, cursorChars)) * scale);
            int cursorH = Math.max(1, (int) (font.lineHeight * scale));
            g.fill(cursorX, y, cursorX + 1, y + cursorH, kveTextColor | 0xFF000000);
        }
    }

    private void drawKveDecorations(GuiGraphics g, Font font, int mx, int my) {
        if (!isVisible()) return;
        boolean clear = showClear();
        boolean counter = isFocused() && kveMaxLength > 0 && kveMaxLength < 10000
                && getValue().length() >= Math.max(1, (int) (kveMaxLength * 0.75f));
        boolean focusBorder = isFocused() && kveBordered;
        if (!clear && !counter && !focusBorder) return;
        g.flush();
        if (focusBorder) g.renderOutline(getX() - 1, getY() - 1, getWidth() + 2, getHeight() + 2, KveGuiStyle.ACCENT_GOLD);
        if (clear) {
            int cx = getX() + getWidth() - 10;
            boolean hot = overClear(mx, my);
            g.fill(cx, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1, 0xFF000000);
            g.drawString(font, "\u00d7", cx + 2, getY() + (getHeight() - font.lineHeight) / 2 + 1,
                    hot ? KveGuiStyle.ACCENT_GOLD : KveGuiStyle.TEXT_MUTED, false);
        }
        if (counter) {
            String c = getValue().length() + "/" + kveMaxLength;
            boolean full = getValue().length() >= kveMaxLength;
            float sc = 0.5f;
            int right = getX() + getWidth() - (clear ? 11 : 2);
            g.pose().pushPose();
            g.pose().translate(right - font.width(c) * sc, getY() + 1, 0);
            g.pose().scale(sc, sc, 1f);
            g.drawString(font, c, 0, 0, full ? KveGuiStyle.TEXT_WARN : KveGuiStyle.TEXT_MUTED, false);
            g.pose().popPose();
        }
    }
}
