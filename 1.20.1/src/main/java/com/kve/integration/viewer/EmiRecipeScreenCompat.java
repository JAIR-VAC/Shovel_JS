package com.kve.integration.viewer;

import com.kve.CraftEditorMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Boton "abrir en Shovel JS" en la pantalla de recetas de EMI.
 * <p>
 * No usa {@code EmiRecipeDecorator}: EMI solo dibuja decoradores con la opcion
 * {@code show-recipe-decorators}, que por defecto vale "entorno de desarrollo",
 * o sea apagada para cualquier jugador. En su lugar se leen por reflexion las
 * recetas visibles de {@code RecipeScreen} (sus coordenadas son absolutas) y el
 * boton se dibuja al lado de cada una; el clic y el tooltip los atiende
 * {@link KveViewerOverlay} con los mismos eventos de pantalla que JEI.
 * <p>
 * Si otra version de EMI cambia estos nombres internos, el boton simplemente
 * deja de aparecer (se avisa una vez en el log) en vez de romper la pantalla.
 */
final class EmiRecipeScreenCompat {

    private EmiRecipeScreenCompat() {}

    private static final String RECIPE_SCREEN = "dev.emi.emi.screen.RecipeScreen";

    private static boolean disabled;
    private static Field currentPage;
    private static Field groupRecipe;
    private static Method groupX, groupY, recipeId;

    static void drawButtons(Screen screen, GuiGraphics g, int mx, int my) {
        if (disabled || screen == null || !RECIPE_SCREEN.equals(screen.getClass().getName())) return;
        try {
            if (currentPage == null) resolve(screen.getClass());
            List<?> groups = (List<?>) currentPage.get(screen);
            if (groups == null) return;
            for (Object group : groups) {
                Object recipe = groupRecipe.get(group);
                if (recipe == null) continue;
                if (!(recipeId.invoke(recipe) instanceof ResourceLocation id) || !KveViewerOverlay.isEditable(id)) continue;
                int x = (int) groupX.invoke(group) + KveViewerOverlay.BTN_X;
                int y = (int) groupY.invoke(group) + KveViewerOverlay.BTN_Y;
                boolean hovered = mx >= x && mx < x + KveViewerOverlay.BTN_W && my >= y && my < y + KveViewerOverlay.BTN_H;
                KveViewerOverlay.drawButton(g, x, y, hovered);
                KveViewerOverlay.recordHit(x, y, id);
            }
        } catch (Throwable t) {
            disabled = true;
            CraftEditorMod.LOGGER.warn("[Shovel JS] Boton de EMI desactivado: esta version de EMI no expone lo esperado ({})", t.toString());
        }
    }

    private static void resolve(Class<?> screenClass) throws ReflectiveOperationException {
        ClassLoader loader = screenClass.getClassLoader();
        currentPage = screenClass.getDeclaredField("currentPage");
        currentPage.setAccessible(true);
        Class<?> widgetGroup = Class.forName("dev.emi.emi.screen.WidgetGroup", false, loader);
        groupRecipe = widgetGroup.getField("recipe");
        groupX = widgetGroup.getMethod("x");
        groupY = widgetGroup.getMethod("y");
        recipeId = Class.forName("dev.emi.emi.api.recipe.EmiRecipe", false, loader).getMethod("getId");
    }
}
