package com.kve.common.kubejs;

import com.kve.common.recipe.RecipeData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code create:sequenced_assembly}: shape confirmed against
 * Create's real source (mc1.20.1/dev,
 * com.simibubi.create.content.processing.sequenced.
 * SequencedAssemblyRecipeSerializer#writeToJson) - see
 * {@link KubeJSExporter#customJsonCreateSequencedAssembly} javadoc for the
 * full reasoning on why {@code sequence}/{@code results} stay as
 * hand-authored raw JSON rather than a structured KVE field.
 */
class KubeJSExporterSequencedAssemblyTest {

    private static RecipeData base() {
        RecipeData d = new RecipeData();
        d.recipeId = "precision_mechanism_test";
        d.type = RecipeData.RecipeType.CUSTOM_JSON;
        d.customTypeId = "create:sequenced_assembly";
        d.shapelessIngredients = new java.util.ArrayList<>(List.of(new ItemStack(Items.IRON_INGOT)));
        return d;
    }

    @Test
    void missingSequenceOrResultsIsNotExportedSilently() {
        RecipeData d = base();
        d.customExtraJson = "{\"transitionalItem\": {\"item\":\"create:incomplete_precision_mechanism\"}, \"loops\": 5}";
        String js = KubeJSExporter.previewRecipeJS(d);

        assertTrue(js.contains("NO EXPORTADO"), js);
        assertTrue(js.contains("sequence"), js);
        assertTrue(js.contains("results"), js);
        assertFalse(js.contains("event.custom"), "should not emit a recipe Create's codec would reject:\n" + js);
    }

    @Test
    void missingIngredientIsSkipped() {
        RecipeData d = base();
        d.shapelessIngredients = new java.util.ArrayList<>();
        d.customExtraJson = "{\"transitionalItem\": {\"item\":\"x\"}, \"sequence\": [], \"results\": []}";
        String js = KubeJSExporter.previewRecipeJS(d);

        assertTrue(js.contains("SKIPPED"), js);
    }

    @Test
    void fullyAuthoredRecipeExportsAllFiveTopLevelKeys() {
        RecipeData d = base();
        d.customExtraJson = "{"
                + "\"transitionalItem\": {\"item\":\"create:incomplete_precision_mechanism\"},"
                + "\"loops\": 5,"
                + "\"sequence\": ["
                + "  {\"type\":\"create:deploying\",\"ingredients\":[{\"item\":\"minecraft:air\"},{\"item\":\"minecraft:iron_nugget\"}],\"results\":[{\"item\":\"create:incomplete_precision_mechanism\"}]},"
                + "  {\"type\":\"create:pressing\",\"ingredients\":[{\"item\":\"minecraft:air\"}],\"results\":[{\"item\":\"create:incomplete_precision_mechanism\"}]}"
                + "],"
                + "\"results\": [{\"item\":\"create:precision_mechanism\",\"chance\":1.0},{\"item\":\"create:precision_mechanism\",\"chance\":0.75}]"
                + "}";
        String js = KubeJSExporter.previewRecipeJS(d);

        assertTrue(js.contains("type: 'create:sequenced_assembly'"), js);
        assertTrue(js.contains("ingredient: { item: 'minecraft:iron_ingot' }"), js);
        assertTrue(js.contains("transitional_item: {\"id\":\"create:incomplete_precision_mechanism\"}"), js);
        assertTrue(js.contains("\"results\":[{\"id\":\"create:incomplete_precision_mechanism\"}]"), js);
        assertTrue(js.contains("loops: 5"), js);
        assertTrue(js.contains("\"type\":\"create:deploying\""), js);
        assertTrue(js.contains("\"chance\":0.75"), js);
        assertFalse(js.contains("NO EXPORTADO"), js);
    }

    @Test
    void loopsDefaultsTo5WhenAbsent() {
        RecipeData d = base();
        d.customExtraJson = "{\"transitionalItem\": {\"item\":\"x\"}, \"sequence\": [], \"results\": []}";
        String js = KubeJSExporter.previewRecipeJS(d);

        assertTrue(js.contains("loops: 5"), js);
    }
}
