package com.kve.client.gui;

import com.kve.common.config.KVEConfig;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * Escala de GUI propia de las pantallas de Shovel JS. Los editores necesitan
 * {@link #MIN_W}x{@link #MIN_H} unidades de GUI; con la escala del juego alta
 * (Auto en 1080p da 480x270) se salian de la pantalla. Mientras una pantalla
 * del mod esta abierta se usa la escala mas grande que no supere la del
 * jugador y en la que la interfaz entra completa; al salir vuelve la suya.
 * <p>
 * Se aplica al abrir la pantalla ({@link ScreenEvent.Opening}, antes de su
 * init) y se corrige en el tick si la ventana cambio de tamano (ese camino
 * vuelve a la escala del juego sin pasar por ningun evento).
 */
@OnlyIn(Dist.CLIENT)
public final class KveUiScale {

    private KveUiScale() {}

    /** Area minima en unidades de GUI que necesitan el Studio y los editores grandes. */
    public static final int MIN_W = 640, MIN_H = 340;

    /** Valores de la opcion: "auto", "game" o una escala fija "1".."4". */
    public static final String[] MODES = {"auto", "game", "1", "2", "3", "4"};

    private static boolean applied;

    public static boolean isKveScreen(Screen s) {
        return s != null && s.getClass().getName().startsWith("com.kve.");
    }

    private static int playerScale(Minecraft mc) {
        return mc.getWindow().calculateScale(mc.options.guiScale().get(), mc.isEnforceUnicode());
    }

    /** La escala que corresponde a las pantallas del mod con la opcion y la ventana actuales. */
    public static int desiredScale(Minecraft mc) {
        Window win = mc.getWindow();
        int player = playerScale(mc);
        String mode = KVEConfig.uiScale == null ? "auto" : KVEConfig.uiScale;
        switch (mode) {
            case "game":
                return player;
            case "auto": {
                int s = player;
                while (s > 1 && (win.getWidth() / (double) s < MIN_W || win.getHeight() / (double) s < MIN_H)) s--;
                return s;
            }
            default:
                try { return win.calculateScale(Integer.parseInt(mode), mc.isEnforceUnicode()); }
                catch (NumberFormatException e) { return player; }
        }
    }

    private static void setScale(Minecraft mc, int scale) {
        Window win = mc.getWindow();
        if ((int) win.getGuiScale() == scale) return;
        win.setGuiScale(scale);
    }

    private static void restore(Minecraft mc) {
        if (!applied) return;
        applied = false;
        setScale(mc, playerScale(mc));
    }

    @SubscribeEvent
    public static void onOpening(ScreenEvent.Opening event) {
        Minecraft mc = Minecraft.getInstance();
        if (isKveScreen(event.getNewScreen())) {
            setScale(mc, desiredScale(mc));
            applied = true;
        } else {
            restore(mc);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Screen s = mc.screen;
        if (!isKveScreen(s)) { restore(mc); return; }
        int want = desiredScale(mc);
        if ((int) mc.getWindow().getGuiScale() != want) {
            setScale(mc, want);
            applied = true;
            s.resize(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        }
    }

    /** Tras cambiar la opcion en Ajustes: rearma la pantalla abierta con la escala nueva. */
    public static void reapply() {
        Minecraft mc = Minecraft.getInstance();
        if (!isKveScreen(mc.screen)) return;
        setScale(mc, desiredScale(mc));
        applied = true;
        mc.screen.resize(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
    }
}
