package com.kve.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * {@code Button.builder(...).build()} draws the vanilla gray 9-slice
 * texture, which reads nothing from {@link KveGuiStyle}'s palette: next to
 * a themed panel it produced a clash of 3 palettes (panel navy/bronce +
 * texto dorado/celeste + boton gris vanilla).
 * <p>
 * This is a normal {@link Button} in every way (bounds, click handling,
 * keyboard/gamepad focus, tooltips, narration) - it just overrides
 * {@link #renderWidget()} to call {@link KveGuiStyle#button} (the plate/
 * bisel recipe) instead of drawing the vanilla
 * texture. Deliberately does NOT reimplement {@code mouseClicked}/
 * {@code isMouseOver}/etc by hand - subclassing keeps the free
 * integration with Screen's widget system (tab order, addRenderableWidget,
 * accessibility narration) that a from-scratch widget would give up.
 * <p>
 * Usage is a drop-in replacement for {@code Button.builder(msg, onPress())
 *.bounds(x, y, w, h).build()}:
 * <pre>
 *   addRenderableWidget(new KvePlateButton(x, y, w, h, msg, onPress()));
 * </pre>
 */
public class KvePlateButton extends Button {

    private boolean selected = false;

    public KvePlateButton(int x, int y, int w, int h, Component message, OnPress onPress) {
        super(x, y, w, h, message, onPress, DEFAULT_NARRATION);
    }

    /** Fluent setter, mismo patron que {@code Screen#withReturnScreen}. Llamar con true en el boton de la tab actualmente activa. */
    public KvePlateButton withSelected(boolean selected) {
        this.selected = selected;
        return this;
    }

    /**
     * Boton "principal": borde dorado para la accion mas
     * importante de un panel (ej. "Receta existente" en el editor de
     * recetas). Distinto de {@link #withSelected()}, que se ve "hundido" como
     * una pestaña activa.
     */
    private boolean primary = false;

    public KvePlateButton withPrimary(boolean primary) {
        this.primary = primary;
        return this;
    }

    /** UX accion destructiva (borrar, descartar...) - borde rojo. */
    private boolean danger = false;

    public KvePlateButton withDanger(boolean danger) {
        this.danger = danger;
        return this;
    }

    /** Aviso (conflictos, problemas a revisar) - placa ambar. */
    private boolean warning = false;

    public KvePlateButton withWarning(boolean warning) {
        this.warning = warning;
        return this;
    }

    private long pressedAt = 0L;
    private boolean autoTooltip = false;

    @Override
    public void onPress() {
        pressedAt = System.currentTimeMillis();
        super.onPress();
    }

    private void syncLabelTooltip() {
        if (getTooltip() != null && !autoTooltip) return;
        var font = net.minecraft.client.Minecraft.getInstance().font;
        String label = getMessage().getString();
        int avail = Math.max(1, getWidth() - 4);
        boolean squeezed = !label.isEmpty() && font.width(label) * KveGuiStyle.uiTextScale() > avail * 1.18f;
        if (squeezed && !autoTooltip) {
            setTooltip(net.minecraft.client.gui.components.Tooltip.create(getMessage()));
            autoTooltip = true;
        } else if (!squeezed && autoTooltip) {
            setTooltip(null);
            autoTooltip = false;
        }
    }

    @Override
    public void renderWidget(GuiGraphics g, int mx, int my, float partialTick) {
        KveGuiStyle.ButtonState state = !this.active ? KveGuiStyle.ButtonState.DISABLED
                : this.selected ? KveGuiStyle.ButtonState.ACTIVE
                : this.isHovered() ? KveGuiStyle.ButtonState.HOVER : KveGuiStyle.ButtonState.NORMAL;
        syncLabelTooltip();
        if (warning && this.active) {
            boolean hov = this.isHovered();
            KveGuiStyle.plate(g, getX(), getY(), getX() + getWidth(), getY() + getHeight(),
                    hov ? 0xFF6A4A14 : 0xFF4A3410, hov ? 0xFF4A3410 : 0xFF2E200A, hov ? 0xFFFFC860 : 0xFFE0A040);
            String label = KveGuiStyle.truncate(net.minecraft.client.Minecraft.getInstance().font, getMessage().getString(), getWidth() - 4);
            KveGuiStyle.drawCenteredTextSafe(g, label, getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, hov ? 0xFFFFE0A0 : 0xFFFFC860);
        } else {
            KveGuiStyle.button(g, getX(), getY(), getWidth(), getHeight(), getMessage().getString(), state);
        }
        long since = System.currentTimeMillis() - pressedAt;
        if (since < 180) {
            int alpha = (int) (0x50 * (1f - since / 180f));
            g.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1, (alpha << 24) | 0xFFFFFF);
        }
        if (danger && this.active) {
            g.renderOutline(getX(), getY(), getWidth(), getHeight(), KveGuiStyle.TEXT_ERROR);
            if (this.isHovered()) g.renderOutline(getX() + 1, getY() + 1, getWidth() - 2, getHeight() - 2, KveGuiStyle.TEXT_ERROR);
        }
        if (primary && this.active) {
            g.renderOutline(getX(), getY(), getWidth(), getHeight(), KveGuiStyle.ACCENT_GOLD);
            if (this.isHovered()) g.renderOutline(getX() + 1, getY() + 1, getWidth() - 2, getHeight() - 2, KveGuiStyle.ACCENT_GOLD);
        }
    }
}
