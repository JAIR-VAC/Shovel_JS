package com.kve.integration.viewer;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.category.extensions.IRecipeCategoryDecorator;
import mezz.jei.api.registration.IAdvancedRegistration;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import org.joml.Matrix4f;

/**
 * Integracion JEI: boton "KVE" junto a cada receta (ver {@link KveViewerOverlay}).
 * JEI descubre esta clase por {@link JeiPlugin}; si JEI no esta instalado
 * nunca se carga (las clases de JEI son compileOnly).
 */
@JeiPlugin
public class KveJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath("shovel_js", "recipe_editor_button");

    @Override
    public ResourceLocation getPluginUid() { return UID; }

    @Override
    public void registerAdvanced(IAdvancedRegistration registration) {
        registration.getJeiHelpers().getAllRecipeTypes().forEach(type -> addDecorator(registration, type));
    }

    private static <T> void addDecorator(IAdvancedRegistration registration, RecipeType<T> type) {
        registration.addRecipeCategoryDecorator(type, new Decorator<>());
    }

    private static <T> ResourceLocation recipeId(IRecipeCategory<T> category, T recipe) {
        ResourceLocation id = category.getRegistryName(recipe);
        if (id == null && recipe instanceof net.minecraft.world.item.crafting.RecipeHolder<?> h) id = h.id();
        return id;
    }

    private static final class Decorator<T> implements IRecipeCategoryDecorator<T> {
        @Override
        public void draw(T recipe, IRecipeCategory<T> category, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
            ResourceLocation id = recipeId(category, recipe);
            if (!KveViewerOverlay.isEditable(id)) return;
            int x = KveViewerOverlay.BTN_X, y = KveViewerOverlay.BTN_Y;
            boolean hovered = mouseX >= x && mouseX < x + KveViewerOverlay.BTN_W
                    && mouseY >= y && mouseY < y + KveViewerOverlay.BTN_H;
            KveViewerOverlay.drawButton(g, x, y, hovered);
            Matrix4f pose = g.pose().last().pose();
            KveViewerOverlay.recordHit(Math.round(pose.m30()) + x, Math.round(pose.m31()) + y, id);
        }
    }
}
