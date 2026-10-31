package com.kve.common.kubejs;

import com.kve.common.recipe.RecipeData;
import com.kve.common.recipe.RecipeData.MachineSlot;
import com.kve.common.recipe.RecipeData.MachineSlotType;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class KubeJSExporterCopyRecipeTest {

    @SuppressWarnings("unchecked")
    private static RecipeData invokeCopyRecipe(RecipeData r) throws Exception {
        Method m = KubeJSExporter.class.getDeclaredMethod("copyRecipe", RecipeData.class);
        m.setAccessible(true);
        return (RecipeData) m.invoke(null, r);
    }

    private RecipeData sample() {
        RecipeData r = new RecipeData();
        r.recipeId = "shovel_js:test_recipe";
        r.enabled = true;
        r.category = "machines";
        r.type = RecipeData.RecipeType.CUSTOM_MACHINE;
        r.ecTier = RecipeData.ECTableTier.ADVANCED;

        r.ingredients.add(ItemStack.EMPTY);
        r.gridWidth = 2;
        r.gridHeight = 2;
        r.shapelessIngredients.add(ItemStack.EMPTY);
        r.output = ItemStack.EMPTY;

        r.ecPowerRequired = 123L;
        r.ecCraftingTime = 45;

        r.combinationCenter = ItemStack.EMPTY;
        r.combinationPedestals.add(ItemStack.EMPTY);
        r.combinationRfCost = 1L;
        r.combinationRfRate = 2L;

        r.customTypeId = "somemod:some_type";
        r.customExtraJson = "\"processingTime\": 100";

        r.secondaryOutput = ItemStack.EMPTY;
        r.secondaryOutputChance = 0.3;

        MachineSlot input = new MachineSlot();
        input.type = MachineSlotType.TAG;
        input.tagId = "forge:ores";
        input.tagCount = 4;
        r.machineInputs.add(input);

        MachineSlot output = new MachineSlot();
        output.type = MachineSlotType.FLUID;
        output.fluidId = "minecraft:water";
        output.fluidAmount = 500;
        r.machineOutputs.add(output);

        return r;
    }

    @Test
    void copyPreservesAllScalarAndListContent() throws Exception {
        RecipeData original = sample();
        RecipeData copy = invokeCopyRecipe(original);

        assertEquals(original.recipeId, copy.recipeId);
        assertEquals(original.type, copy.type);
        assertEquals(original.ecTier, copy.ecTier);
        assertEquals(original.gridWidth, copy.gridWidth);
        assertEquals(original.gridHeight, copy.gridHeight);
        assertEquals(original.ecPowerRequired, copy.ecPowerRequired);
        assertEquals(original.ecCraftingTime, copy.ecCraftingTime);
        assertEquals(original.combinationRfCost, copy.combinationRfCost);
        assertEquals(original.combinationRfRate, copy.combinationRfRate);
        assertEquals(original.customTypeId, copy.customTypeId);
        assertEquals(original.customExtraJson, copy.customExtraJson);
        assertEquals(original.secondaryOutputChance, copy.secondaryOutputChance);

        assertEquals(1, copy.machineInputs.size());
        assertEquals("forge:ores", copy.machineInputs.get(0).tagId);
        assertEquals(4, copy.machineInputs.get(0).tagCount);
        assertEquals(1, copy.machineOutputs.size());
        assertEquals("minecraft:water", copy.machineOutputs.get(0).fluidId);
        assertEquals(500, copy.machineOutputs.get(0).fluidAmount);
    }

    @Test
    void copyDoesNotShareListOrSlotInstances() throws Exception {
        RecipeData original = sample();
        RecipeData copy = invokeCopyRecipe(original);

        assertNotSame(original.ingredients, copy.ingredients);
        assertNotSame(original.shapelessIngredients, copy.shapelessIngredients);
        assertNotSame(original.combinationPedestals, copy.combinationPedestals);
        assertNotSame(original.machineInputs, copy.machineInputs);
        assertNotSame(original.machineOutputs, copy.machineOutputs);
        assertNotSame(original.machineInputs.get(0), copy.machineInputs.get(0));
        assertNotSame(original.machineOutputs.get(0), copy.machineOutputs.get(0));
    }

    @Test
    void mutatingOriginalSlotsAfterCopyDoesNotAffectCopy() throws Exception {
        RecipeData original = sample();
        RecipeData copy = invokeCopyRecipe(original);

        original.machineInputs.get(0).tagId = "mutated:after_copy";
        original.machineInputs.get(0).tagCount = 999;
        original.machineOutputs.get(0).fluidAmount = 1;
        original.ingredients.add(ItemStack.EMPTY);
        original.recipeId = "mutated:after_copy";

        assertEquals("forge:ores", copy.machineInputs.get(0).tagId);
        assertEquals(4, copy.machineInputs.get(0).tagCount);
        assertEquals(500, copy.machineOutputs.get(0).fluidAmount);
        assertEquals(1, copy.ingredients.size());
        assertEquals("shovel_js:test_recipe", copy.recipeId);
    }
}
