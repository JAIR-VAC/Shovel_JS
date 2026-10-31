package com.kve.common.kubejs;

import com.google.gson.JsonParser;
import com.kve.common.recipe.RecipeData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Forma de pe_world_transmutations (rama mc1.21.1 de sinkillerj/ProjectE, datagen defaults.json). */
class ProjectETransmutationExporterTest {

    private static RecipeData recipe(String extra) {
        RecipeData r = new RecipeData();
        r.recipeId = "t";
        r.type = RecipeData.RecipeType.CUSTOM_JSON;
        r.customTypeId = ProjectETransmutationExporter.TYPE;
        r.customExtraJson = extra;
        r.output = ItemStack.EMPTY;
        return r;
    }

    @Test
    void fieldsBuildNativeFile() {
        RecipeData a = recipe("{\"from\": \"minecraft:stone\", \"to\": \"minecraft:cobblestone\", \"alt\": \"minecraft:grass_block\"}");
        RecipeData b = recipe("{\"from\": \"minecraft:gravel\", \"to\": \"minecraft:sandstone\"}");
        assertEquals(JsonParser.parseString("{\"comment\":\"Shovel JS - transmutaciones (AUTO-GENERATED, no editar a mano)\",\"transmutations\":["
                        + "{\"alt_result\":\"minecraft:grass_block\",\"origin\":\"minecraft:stone\",\"result\":\"minecraft:cobblestone\"},"
                        + "{\"origin\":\"minecraft:gravel\",\"result\":\"minecraft:sandstone\"}]}"),
                ProjectETransmutationExporter.buildJson(List.of(a, b)));
    }

    @Test
    void gridFallbackAndInvalidSkipped() {
        RecipeData g = recipe("");
        g.shapelessIngredients = new ArrayList<>(List.of(new ItemStack(Items.DIRT)));
        g.output = new ItemStack(Items.SAND);
        RecipeData bad = recipe("{\"from\": \"Not An Id\", \"to\": \"minecraft:sand\"}");
        var json = ProjectETransmutationExporter.buildJson(List.of(g, bad));
        assertEquals(1, json.getAsJsonArray("transmutations").size());
        assertTrue(ProjectETransmutationExporter.jsComment(bad).contains("NO EXPORTADO"));
    }
}
