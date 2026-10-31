package com.kve.integration.viewer;

import com.kve.client.access.KveAccessGate;
import com.kve.common.recipe.RecipeData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;

import java.util.ArrayList;
import java.util.Optional;

public final class RecipeEditorBridge {

    private RecipeEditorBridge() {}

    /**
     * @param recipeId id real de la receta tal como el visor la muestra
     *                 (no el id de KVE - si es de otro namespace,
     *                 {@code CraftEditorScreen} la trata como "importar
     *                 receta externa", igual que {@code
     *                 RecipeBrowserScreen#pick} ya hace para recetas que
     *                 no son {@code kve:}).
     * @param returnTo sin uso desde la receta se abre dentro de KveStudioShell, que tiene su propia salida (ESC/Menu). Se mantiene la firma para los callers existentes.
     */
    public static void openRecipeInEditor(ResourceLocation recipeId, Screen returnTo) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        RecipeManager mgr = mc.level.getRecipeManager();
        Optional<? extends Recipe<?>> found = mgr.byKey(recipeId);
        if (found.isEmpty()) return;
        Recipe<?> recipe = found.get();

        RegistryAccess regAccess = mc.level.registryAccess();
        ItemStack output;
        try {
            output = recipe.getResultItem(regAccess);
        } catch (Throwable ignored) {
            output = ItemStack.EMPTY;
        }
        if (output == null || output.isEmpty()) {
            output = new ItemStack(net.minecraft.world.item.Items.BARRIER);
        }

        RecipeData data = toRecipeData(recipeId, recipe, output);
        boolean isOurs = recipeId.getNamespace().equals("shovel_js");
        String originalIdToRemove = isOurs ? null : recipeId.toString();

        KveAccessGate.open(() -> new com.kve.client.gui.studio.KveStudioShell().withRecipe(data, originalIdToRemove));
    }

    private static RecipeData toRecipeData(ResourceLocation id, Recipe<?> recipe, ItemStack output) {
        return com.kve.client.recipe.ExistingRecipeLoader.toRecipeData(id, recipe, output);
    }
}
