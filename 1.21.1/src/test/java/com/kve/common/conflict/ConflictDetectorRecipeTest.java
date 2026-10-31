package com.kve.common.conflict;

import com.kve.common.recipe.RecipeData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConflictDetectorRecipeTest {

    private static RecipeData shaped(String id, Item out, int w, int h, Item... cells) {
        RecipeData d = new RecipeData();
        d.recipeId = id;
        d.type = RecipeData.RecipeType.VANILLA_SHAPED;
        d.output = new ItemStack(out);
        d.gridWidth = w;
        d.gridHeight = h;
        d.ingredients = new ArrayList<>();
        for (Item c : cells) d.ingredients.add(c == null ? ItemStack.EMPTY : new ItemStack(c));
        return d;
    }

    private static RecipeData shapeless(String id, Item out, Item... ins) {
        RecipeData d = new RecipeData();
        d.recipeId = id;
        d.type = RecipeData.RecipeType.VANILLA_SHAPELESS;
        d.output = new ItemStack(out);
        for (Item c : ins) d.shapelessIngredients.add(new ItemStack(c));
        return d;
    }

    @Test
    void sameOutputWithDifferentInputsIsNotAConflict() {
        List<RecipeData> rs = List.of(
                shapeless("a", Items.STICK, Items.OAK_PLANKS),
                shapeless("b", Items.STICK, Items.BIRCH_PLANKS));
        assertTrue(ConflictDetector.recipeConflicts(rs).isEmpty());
        assertEquals(1, ConflictDetector.recipeOutputGroups(rs).size());
    }

    @Test
    void sameInputsWithDifferentOutputsIsAConflict() {
        List<RecipeData> rs = List.of(
                shapeless("a", Items.STICK, Items.OAK_PLANKS, Items.DIRT),
                shapeless("b", Items.TORCH, Items.DIRT, Items.OAK_PLANKS));
        List<ConflictDetector.Conflict> cs = ConflictDetector.recipeConflicts(rs);
        assertEquals(1, cs.size());
        assertEquals(ConflictDetector.Kind.RECIPE_SAME_INPUT, cs.get(0).kind());
        assertEquals(List.of("a", "b"), cs.get(0).keys());
    }

    @Test
    void shapedPatternIsComparedWhereverItSitsAndMirrored() {
        // un palo vertical en la columna izquierda de un 3x3 == el mismo palo en un 1x2;
        // un "L" y su espejo son la misma receta para la mesa
        RecipeData a = shaped("a", Items.STICK, 3, 3, Items.OAK_PLANKS, null, null, Items.OAK_PLANKS, null, null, null, null, null);
        RecipeData b = shaped("b", Items.TORCH, 1, 2, Items.OAK_PLANKS, Items.OAK_PLANKS);
        RecipeData c = shaped("c", Items.STONE, 2, 2, Items.DIRT, null, Items.DIRT, Items.COBBLESTONE);
        RecipeData d = shaped("d", Items.GRAVEL, 2, 2, null, Items.DIRT, Items.COBBLESTONE, Items.DIRT);
        List<ConflictDetector.Conflict> cs = ConflictDetector.recipeConflicts(List.of(a, b, c, d));
        assertEquals(2, cs.size());
        assertTrue(cs.stream().allMatch(x -> x.kind() == ConflictDetector.Kind.RECIPE_SAME_INPUT));
    }

    @Test
    void sameRecipeTwiceIsADuplicateNotADifferentResult() {
        List<RecipeData> rs = List.of(
                shapeless("a", Items.STICK, Items.OAK_PLANKS),
                shapeless("b", Items.STICK, Items.OAK_PLANKS));
        List<ConflictDetector.Conflict> cs = ConflictDetector.recipeConflicts(rs);
        assertEquals(1, cs.size());
        assertEquals(ConflictDetector.Kind.RECIPE_DUPLICATE, cs.get(0).kind());
    }

    @Test
    void shapedAndShapelessAtTheTableDoNotMixWithMachines() {
        RecipeData table = shapeless("a", Items.STICK, Items.OAK_PLANKS);
        RecipeData furnace = shapeless("b", Items.CHARCOAL, Items.OAK_PLANKS);
        furnace.type = RecipeData.RecipeType.CUSTOM_JSON;
        furnace.customTypeId = "minecraft:smelting";
        assertTrue(ConflictDetector.recipeConflicts(List.of(table, furnace)).isEmpty());
    }
}
