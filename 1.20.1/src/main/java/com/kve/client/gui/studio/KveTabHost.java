package com.kve.client.gui.studio;

import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Pantalla que aloja editores como pestanias (el Studio, la edicion de varias recetas a la vez).
 * Los editores alojados vuelven a su contenedor al cerrar un picker via
 * {@link KveStudioShell#reclaimIfHosted} y salen con {@link KveStudioShell#exitIfHosted}.
 */
@OnlyIn(Dist.CLIENT)
public interface KveTabHost {

    /** True si {@code tab} es un editor que esta pantalla muestra ahora mismo. */
    boolean hostsActive(Screen tab);

    /** Cierra el contenedor (lo que hace "Volver" desde un editor alojado). */
    void exitHost();
}
