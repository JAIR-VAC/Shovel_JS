package com.kve.common.kubejs;

import com.kve.common.recipe.RecipeData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KubeJSExporterSmithingTest {

    private static RecipeData positionalSmithing(String customTypeId, boolean withOutput) {
        RecipeData d = new RecipeData();
        d.recipeId = "trim_test";
        d.type = RecipeData.RecipeType.CUSTOM_JSON;
        d.customTypeId = customTypeId;
        d.gridWidth = 3; d.gridHeight = 1;
        d.ingredients = new java.util.ArrayList<>(List.of(
                new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                new ItemStack(Items.DIAMOND_CHESTPLATE),
                new ItemStack(Items.NETHERITE_INGOT)));
        if (withOutput) d.output = new ItemStack(Items.NETHERITE_CHESTPLATE);
        return d;
    }

    @Test
    void positionalSmithingWithFilledSlotsPassesValidation() {
        RecipeData d = positionalSmithing("minecraft:smithing_trim", false);
        List<String> problems = KubeJSExporter.validateRecipe(d);
        assertTrue(problems.isEmpty(),
                "expected no validation errors for a fully-filled smithing recipe, got: " + problems);
    }

    @Test
    void positionalSmithingWithEmptySlotsStillFailsValidation() {
        RecipeData d = positionalSmithing("minecraft:smithing_trim", false);
        d.ingredients = new java.util.ArrayList<>(List.of(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY));
        List<String> problems = KubeJSExporter.validateRecipe(d);
        assertFalse(problems.isEmpty(), "an actually-empty smithing recipe should still be rejected");
    }

    @Test
    void smithingTrimExportsTemplateBaseAdditionWithoutResult() {
        RecipeData d = positionalSmithing("minecraft:smithing_trim", false);
        String js = KubeJSExporter.previewRecipeJS(d);

        assertTrue(js.contains("type: 'minecraft:smithing_trim'"), js);
        assertTrue(js.contains("template: { item: 'minecraft:netherite_upgrade_smithing_template' }"), js);
        assertTrue(js.contains("base: { item: 'minecraft:diamond_chestplate' }"), js);
        assertTrue(js.contains("addition: { item: 'minecraft:netherite_ingot' }"), js);
        assertFalse(js.contains("result:"), "smithing_trim has no result key in vanilla:\n" + js);
        assertFalse(js.contains("ingredient"), "should not fall back to the generic ingredient(s) field:\n" + js);
    }

    @Test
    void smithingTransformExportsResultToo() {
        RecipeData d = positionalSmithing("minecraft:smithing_transform", true);
        String js = KubeJSExporter.previewRecipeJS(d);

        // descriptor minecraft:smithing_transform (minecraft.json): JSON nativo con los 3 ingredientes y el resultado
        String flat = js.replaceAll("\\s+", "");
        assertTrue(flat.contains("\"type\":\"minecraft:smithing_transform\""), js);
        assertTrue(flat.contains("\"template\":{\"item\":\"minecraft:netherite_upgrade_smithing_template\"}"), js);
        assertTrue(flat.contains("\"base\":{\"item\":\"minecraft:diamond_chestplate\"}"), js);
        assertTrue(flat.contains("\"addition\":{\"item\":\"minecraft:netherite_ingot\"}"), js);
        assertTrue(flat.contains("\"result\":{\"item\":\"minecraft:netherite_chestplate\""), js);
    }
}
