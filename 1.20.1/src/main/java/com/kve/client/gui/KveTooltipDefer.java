package com.kve.client.gui;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Tooltips al final del fotograma en las pantallas del mod. Las pantallas (y el Studio, que dibuja
 * la pestania activa y despues sus barras) piden el tooltip de un item en medio de su render; vanilla
 * los deja para el final. Mods como Tooltip Overhaul cambian el estado de dibujo al pintar el tooltip
 * de un item, y todo lo que se dibujaba despues (pestanias, barras, botones) parpadeaba o desaparecia.
 * Mientras se dibuja una pantalla del mod, {@code GuiGraphicsTooltipScaleMixin} guarda cada tooltip
 * y aca se dibujan todos juntos, despues de la pantalla.
 */
@OnlyIn(Dist.CLIENT)
public final class KveTooltipDefer {

    private KveTooltipDefer() {}

    private static boolean deferring;
    private static final List<Runnable> PENDING = new ArrayList<>();

    /** true mientras se dibuja una pantalla del mod: el tooltip se guarda en vez de dibujarse ya. */
    public static boolean isDeferring() { return deferring; }

    public static void defer(Runnable draw) { PENDING.add(draw); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderPre(ScreenEvent.Render.Pre event) {
        PENDING.clear();
        deferring = event.getScreen() != null && event.getScreen().getClass().getName().startsWith("com.kve.");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPost(ScreenEvent.Render.Post event) {
        deferring = false;
        if (PENDING.isEmpty()) return;
        List<Runnable> draw = new ArrayList<>(PENDING);
        PENDING.clear();
        for (Runnable r : draw) {
            try { r.run(); } catch (RuntimeException ignored) {}
        }
    }
}
