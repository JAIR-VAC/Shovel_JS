package com.kve.common.kubejs;

import com.kve.common.kubejs.TagExporter.RegistryType;
import com.kve.common.kubejs.TagExporter.TagEntry;
import com.kve.common.recipe.RecipeData;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Requirement (4): entries that fail the semantic guard must never be
 * mixed into a shared generated file alongside good entries - the bad one
 * is omitted, the good ones still get written normally.
 * <p>
 * These exercise the REAL write path ({@code writeAddScript}/{@code
 * writeAll}), not just the previewXxxJS() read-only helpers, since that's
 * the actual enforcement point requirement (1) needs. Entries are staged
 * via {@code loadRecipe()}/{@code loadEntry()} (plain in-memory registration,
 * same helper the import/undo machinery uses) and the private write
 * method is invoked directly via reflection - same pattern
 * {@code KubeJSExporterCopyRecipeTest} already uses for private methods -
 * rather than going through {@code saveRecipe}/{@code saveTag}, which also
 * call {@code CraftEditorState.save()} and, through it,
 * {@code ClientPermissionState.canEdit()} -&gt; {@code Minecraft.getInstance()}.
 * That needs an actual running client and isn't what's under test here.
 */
class WriteTimeSemanticGuardTest {

    @AfterEach
    void cleanup() {
        TagExporter.clearAll();
        KubeJSExporter.clearAll();
    }

    private static void invokePrivateNoArg(Class<?> cls, String methodName) throws Exception {
        Method m = cls.getDeclaredMethod(methodName);
        m.setAccessible(true);
        m.invoke(null);
    }

    @Test
    void tagWriteOmitsOnlyTheBadValueAndKeepsTheGoodOneAvailableForNextEdit() throws Exception {
        TagEntry e = new TagEntry();
        e.regType = RegistryType.ITEM;
        e.tagId = "shovel_js:mixed_tag";
        e.added.add("minecraft:iron_ingot");
        e.added.add("not a valid id at all");

        TagExporter.loadEntry(e);
        invokePrivateNoArg(TagExporter.class, "writeAll");

        List<String> warnings = TagExporter.getLastWriteWarnings();
        assertTrue(warnings.stream().anyMatch(w -> w.contains("not a valid id at all")),
                "the bad value should be reported as a warning: " + warnings);

        TagEntry stored = TagExporter.getEntry(e.key());
        assertEquals(2, stored.added.size());
    }

    @Test
    void recipeWriteOmitsOnlyTheSemanticallyBadRecipeAndReportsWhy() throws Exception {
        RecipeData bad = new RecipeData();
        bad.recipeId = "bad_one";
        bad.type = RecipeData.RecipeType.CUSTOM_JSON;
        bad.customTypeId = "shovel_js:some_type";
        bad.output = ItemStack.EMPTY;
        bad.customExtraJson = "{";

        KubeJSExporter.loadRecipe(bad);
        invokePrivateNoArg(KubeJSExporter.class, "writeAddScript");

        List<String> warnings = KubeJSExporter.getLastWriteWarnings();
        assertTrue(warnings.stream().anyMatch(w -> w.contains("bad_one")),
                "the semantically-invalid recipe should be reported: " + warnings);

        assertTrue(KubeJSExporter.getAddedRecipes().stream().anyMatch(r -> r.recipeId.equals("bad_one")));
    }

    @Test
    void aGoodRecipeSharingAFileWithABadOneIsUnaffected() throws Exception {
        RecipeData bad = new RecipeData();
        bad.recipeId = "bad_two";
        bad.type = RecipeData.RecipeType.CUSTOM_JSON;
        bad.customTypeId = "shovel_js:some_type";
        bad.output = ItemStack.EMPTY;
        bad.customExtraJson = "{";
        KubeJSExporter.loadRecipe(bad);

        // A structurally/semantically fine recipe loaded right alongside it
        // must still write cleanly - one bad entry never blocks or
        // corrupts the whole crafteditor_add.js batch.
        RecipeData good = new RecipeData();
        good.recipeId = "good_two";
        good.type = RecipeData.RecipeType.CUSTOM_JSON;
        good.customTypeId = "shovel_js:some_type";
        good.output = ItemStack.EMPTY;
        good.customExtraJson = "\"note\": \"fine\"";
        KubeJSExporter.loadRecipe(good);

        invokePrivateNoArg(KubeJSExporter.class, "writeAddScript");

        List<String> warnings = KubeJSExporter.getLastWriteWarnings();
        assertTrue(warnings.stream().anyMatch(w -> w.contains("bad_two")));
        assertFalse(warnings.stream().anyMatch(w -> w.contains("good_two")),
                "the good recipe must not be reported as skipped: " + warnings);
    }
}
