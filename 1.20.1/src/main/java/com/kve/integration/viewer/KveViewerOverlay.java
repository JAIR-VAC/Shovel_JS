package com.kve.integration.viewer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class KveViewerOverlay {

    private KveViewerOverlay() {}

    /** Tamano y posicion del boton relativos a la esquina superior izquierda de la receta: a la izquierda, por fuera, alineado arriba. */
    public static final int BTN_W = 14, BTN_H = 14;
    public static final int BTN_X = -BTN_W - 2, BTN_Y = 0;

    private static final ResourceLocation ICON = new ResourceLocation("shovel_js", "textures/gui/shovel.png");

    private record Hit(Screen screen, int x, int y, ResourceLocation recipeId) {}

    private static final List<Hit> hits = new ArrayList<>();

    /** Dibujo compartido por JEI y EMI, en las coordenadas que el caller ya tiene aplicadas. */
    public static void drawButton(GuiGraphics g, int x, int y, boolean hovered) {
        g.blit(ICON, x, y, BTN_W, BTN_H, 0, 0, 16, 16, 16, 16);
        if (hovered) g.fill(x + 2, y + 2, x + BTN_W - 2, y + BTN_H - 2, 0x38FFFFFF);
    }

    /** True si la receta existe en el RecipeManager del cliente - {@link RecipeEditorBridge} no puede abrir otra cosa (ids sinteticos de EMI, recetas "informativas" de JEI, etc.). */
    public static boolean isEditable(ResourceLocation recipeId) {
        if (com.kve.client.gui.KveClientSettings.getOrDefault(com.kve.client.gui.KveClientSettings.HIDE_VIEWER_BUTTON, false)) return false;
        Minecraft mc = Minecraft.getInstance();
        return recipeId != null && mc.level != null && mc.level.getRecipeManager().byKey(recipeId).isPresent();
    }

    /** Llamado por los botones de JEI y EMI mientras dibujan: guarda donde quedo el boton en pantalla para {@link #onClick()}. */
    public static void recordHit(int screenX, int screenY, ResourceLocation recipeId) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen != null) hits.add(new Hit(screen, screenX, screenY, recipeId));
    }

    @SubscribeEvent
    public static void onRenderPre(ScreenEvent.Render.Pre event) {
        hits.clear();
    }

    /** Texto del tooltip del boton, compartido por JEI y EMI. */
    public static net.minecraft.network.chat.Component tooltip() {
        return com.kve.common.Lang.c("gui.kve.viewer.open_tooltip");
    }

    /**
     * Tooltip del boton. Los decoradores de JEI no tienen gancho de tooltip
     * propio, y EMI no le pasa el mouse a widgets fuera del rectangulo de la
     * receta, asi que se dibuja despues de la pantalla usando la misma
     * posicion que {@link #recordHit} ya guarda para el click.
     */
    @SubscribeEvent
    public static void onRenderPost(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        int mx = event.getMouseX(), my = event.getMouseY();
        EmiRecipeScreenCompat.drawButtons(screen, event.getGuiGraphics(), mx, my);
        if (hits.isEmpty()) return;
        for (Hit h : hits) {
            if (h.screen() != screen) continue;
            if (mx >= h.x() && mx < h.x() + BTN_W && my >= h.y() && my < h.y() + BTN_H) {
                event.getGuiGraphics().renderTooltip(Minecraft.getInstance().font, tooltip(), mx, my);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (hits.isEmpty() || event.getButton() != 0) return;
        Screen screen = event.getScreen();
        double mx = event.getMouseX(), my = event.getMouseY();
        for (Hit h : hits) {
            if (h.screen() != screen) continue;
            if (mx >= h.x() && mx < h.x() + BTN_W && my >= h.y() && my < h.y() + BTN_H) {
                RecipeEditorBridge.openRecipeInEditor(h.recipeId(), screen);
                event.setCanceled(true);
                return;
            }
        }
    }
}
