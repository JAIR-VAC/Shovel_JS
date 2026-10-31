package com.kve.common.recipe;

import com.kve.common.recipe.RecipeData.ECTableTier;
import com.kve.common.recipe.RecipeData.RecipeType;
import com.kve.common.recipetype.RecipeTypeDescriptorRegistry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Reglas de grilla/enrutado sacadas de CraftEditorScreen. */
class RecipeGridRulesTest {

    @BeforeAll
    static void loadDescriptors() {
        RecipeTypeDescriptorRegistry.reload();
    }

    private static ItemStack[] grid(int n) {
        ItemStack[] g = new ItemStack[n];
        Arrays.fill(g, ItemStack.EMPTY);
        return g;
    }

    @Test
    void fixedStationsKeepTheirRealGridShape() {
        assertArrayEquals(new int[]{3, 3}, RecipeGridRules.dims(RecipeType.VANILLA_SHAPED, ECTableTier.BASIC, null, null, 8));
        assertArrayEquals(new int[]{7, 7}, RecipeGridRules.dims(RecipeType.EC_TABLE_SHAPED, ECTableTier.ELITE, null, null, 8));
        assertArrayEquals(new int[]{9, 9}, RecipeGridRules.dims(RecipeType.EC_TABLE_SHAPELESS, ECTableTier.ULTIMATE, null, null, 8));
        assertArrayEquals(new int[]{0, 0}, RecipeGridRules.dims(RecipeType.EC_COMBINATION, ECTableTier.BASIC, null, null, 8));
        assertArrayEquals(new int[]{1, 1}, RecipeGridRules.fixedCustomDims("minecraft:stonecutting"));
        assertArrayEquals(new int[]{1, 1}, RecipeGridRules.fixedCustomDims("minecraft:campfire_cooking"));
        assertArrayEquals(new int[]{3, 1}, RecipeGridRules.fixedCustomDims("minecraft:smithing_transform"));
        assertNull(RecipeGridRules.fixedCustomDims("create:mechanical_crafting"));
        assertNull(RecipeGridRules.fixedCustomDims(null));
    }

    @Test
    void variableGridIsExactlyTheCustomTypesWithoutAFixedShape() {
        assertTrue(RecipeGridRules.isVariableGridCustomJson(RecipeType.CUSTOM_JSON, "create:mechanical_crafting"));
        assertFalse(RecipeGridRules.isVariableGridCustomJson(RecipeType.CUSTOM_JSON, "minecraft:smelting"));
        assertFalse(RecipeGridRules.isVariableGridCustomJson(RecipeType.CUSTOM_JSON, "minecraft:smithing_trim"));
        assertFalse(RecipeGridRules.isVariableGridCustomJson(RecipeType.VANILLA_SHAPED, "create:mechanical_crafting"));
    }

    @Test
    void positionalCustomTypesAreNotSavedAsShapeless() {
        assertFalse(RecipeGridRules.isShapeless(RecipeType.CUSTOM_JSON, "minecraft:smithing_trim"), "el item saltaba de casillero");
        assertFalse(RecipeGridRules.isShapeless(RecipeType.CUSTOM_JSON, "create:mechanical_crafting"));
        assertTrue(RecipeGridRules.isShapeless(RecipeType.CUSTOM_JSON, "somemod:grinding"));
        assertTrue(RecipeGridRules.isShapeless(RecipeType.VANILLA_SHAPELESS, null));
        assertFalse(RecipeGridRules.isShapeless(RecipeType.VANILLA_SHAPED, null));
        assertTrue(RecipeGridRules.isEcType(RecipeType.EC_COMBINATION));
        assertFalse(RecipeGridRules.isEcTableType(RecipeType.EC_ENDER_SHAPED));
    }

    @Test
    void newRecipeReusesTheShapeOfAnExistingOneOfTheSameType() {
        RecipeData shaped = new RecipeData();
        shaped.type = RecipeType.CUSTOM_JSON;
        shaped.customTypeId = "create:mechanical_crafting";
        shaped.gridWidth = 5; shaped.gridHeight = 4;
        shaped.ingredients = new ArrayList<>(List.of(new ItemStack(Items.IRON_INGOT)));

        RecipeData bigShapeless = new RecipeData();
        bigShapeless.type = RecipeType.CUSTOM_JSON;
        bigShapeless.customTypeId = "somemod:grinding";
        for (int i = 0; i < 12; i++) bigShapeless.shapelessIngredients.add(new ItemStack(Items.STICK));

        List<RecipeData> saved = List.of(shaped, bigShapeless);
        assertArrayEquals(new int[]{5, 4}, RecipeGridRules.existingRecipeGridHint("create:mechanical_crafting", saved, null, 8));
        assertNull(RecipeGridRules.existingRecipeGridHint("create:mechanical_crafting", saved, shaped, 8), "no se referencia a si misma");
        assertArrayEquals(new int[]{5, 3}, RecipeGridRules.existingRecipeGridHint("somemod:grinding", saved, null, 5));
        assertNull(RecipeGridRules.existingRecipeGridHint("other:type", saved, null, 8));
        assertNull(RecipeGridRules.existingRecipeGridHint("", saved, null, 8));
    }

    @Test
    void longShapelessWrapsIntoRowsInsteadOfRunningOffScreen() {
        assertArrayEquals(new int[]{4, 1}, RecipeGridRules.shapelessGridDims(4, 8));
        assertArrayEquals(new int[]{8, 3}, RecipeGridRules.shapelessGridDims(20, 8));
    }

    @Test
    void manualResizeKeepsItemsAndRefusesToDropThem() {
        ItemStack[] g = grid(9);
        g[0] = new ItemStack(Items.DIAMOND);
        g[8] = new ItemStack(Items.EMERALD);

        ItemStack[] bigger = RecipeGridRules.resizeGrid(g, 3, 3, 4, 4);
        assertNotNull(bigger);
        assertEquals(16, bigger.length);
        assertSame(Items.DIAMOND, bigger[0].getItem());
        assertSame(Items.EMERALD, bigger[2 * 4 + 2].getItem(), "fila/columna, no indice lineal");

        assertNull(RecipeGridRules.resizeGrid(g, 3, 3, 2, 2), "achicar perderia la esmeralda");
        g[8] = ItemStack.EMPTY;
        ItemStack[] smaller = RecipeGridRules.resizeGrid(g, 3, 3, 2, 2);
        assertNotNull(smaller);
        assertEquals(4, smaller.length);
        assertEquals(9, RecipeGridRules.clampManual(12));
        assertEquals(1, RecipeGridRules.clampManual(0));
    }

    @Test
    void typesWithSlotLayoutRouteToTheMachineEditorAndKeepTheirItems() {
        RecipeData d = new RecipeData();
        d.type = RecipeType.CUSTOM_JSON;
        d.customTypeId = "aether:enchanting";
        assertTrue(RecipeGridRules.shouldRouteToMachineEditor(d));
        d.customTypeId = "somemod:unknown";
        assertFalse(RecipeGridRules.shouldRouteToMachineEditor(d));
        d.type = RecipeType.CUSTOM_MACHINE;
        d.customTypeId = "aether:enchanting";
        assertFalse(RecipeGridRules.shouldRouteToMachineEditor(d), "CUSTOM_MACHINE ya lo enruta el llamador");

        RecipeData m = new RecipeData();
        m.ingredients = new ArrayList<>(List.of(new ItemStack(Items.STICK), ItemStack.EMPTY, new ItemStack(Items.COAL)));
        m.output = new ItemStack(Items.TORCH);
        RecipeGridRules.migrateForMachineEditor(m);
        assertEquals(2, m.machineInputs.size(), "vacios se saltan");
        assertEquals(1, m.machineOutputs.size());
        RecipeGridRules.migrateForMachineEditor(m);
        assertEquals(2, m.machineInputs.size(), "corre una sola vez");
    }
}
