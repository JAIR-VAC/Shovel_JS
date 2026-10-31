package com.kve.client.recipe;

import com.kve.client.gui.workspace.pending.PendingChange;
import com.kve.client.gui.workspace.pending.PendingChangeManager;
import com.kve.client.gui.workspace.pending.PendingDomain;
import com.kve.common.kubejs.KubeJSExporter;
import com.kve.common.recipe.RecipeData;
import com.kve.common.recipe.RecipeData.RecipeType;
import com.kve.common.recipe.RecipeGridRules;
import com.kve.common.recipetype.RecipeTypeDescriptorRegistry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RecipeDraftActionsTest {

    @BeforeAll
    static void loadDescriptors() {
        RecipeTypeDescriptorRegistry.reload();
    }

    @BeforeEach
    @AfterEach
    void cleanState() {
        PendingChangeManager.clear();
        KubeJSExporter.clearAll();
    }

    private static RecipeData recipe(String id, net.minecraft.world.item.Item out) {
        RecipeData d = new RecipeData();
        d.recipeId = id;
        d.type = RecipeType.VANILLA_SHAPED;
        d.output = new ItemStack(out);
        return d;
    }

    private static PendingChange onlyQueued() {
        List<PendingChange> q = PendingChangeManager.entriesForDomain(PendingDomain.RECIPES);
        assertEquals(1, q.size());
        return q.get(0);
    }

    @Test
    void saveBlockersInTheSameOrderTheScreenChecksThem() {
        assertEquals("gui.kve.perm.denied", RecipeDraftActions.saveBlocker(false, true, RecipeType.VANILLA_SHAPED, null, true));
        assertEquals("gui.kve.recipe.output_required", RecipeDraftActions.saveBlocker(true, true, RecipeType.VANILLA_SHAPED, null, true));
        assertEquals("gui.kve.recipe.pick_type_first", RecipeDraftActions.saveBlocker(true, false, RecipeType.CUSTOM_JSON, "", true));
        assertNull(RecipeDraftActions.saveBlocker(true, false, RecipeType.CUSTOM_JSON, "", false), "duplicar no exige tipo");
        assertNull(RecipeDraftActions.saveBlocker(true, false, RecipeType.VANILLA_SHAPED, null, true));
        assertNull(RecipeDraftActions.saveBlocker(true, true, RecipeType.CUSTOM_JSON, "aether:item_placement_ban", true),
                "un tipo noOutput se guarda sin salida");
    }

    @Test
    void copyIdSkipsIdsAlreadyInUse() {
        assertEquals("gear_copy", RecipeDraftActions.nextCopyId(" gear ", id -> false));
        assertEquals("recipe_copy", RecipeDraftActions.nextCopyId("", id -> false));
        Set<String> used = Set.of("gear_copy", "gear_copy2");
        assertEquals("gear_copy3", RecipeDraftActions.nextCopyId("gear", used::contains));
    }

    @Test
    void saveQueuesCreateThenEditAndDiscardRestoresThePreviousVersion() {
        RecipeData v1 = recipe("shovel_js_gear", Items.IRON_INGOT);
        assertEquals(PendingChange.Action.CREATE, RecipeDraftActions.queueSave(v1, null, () -> {}));
        assertTrue(onlyQueued().hasDiff());
        PendingChangeManager.clear();

        RecipeData v2 = recipe("shovel_js_gear", Items.GOLD_INGOT);
        int[] refreshed = {0};
        assertEquals(PendingChange.Action.EDIT, RecipeDraftActions.queueSave(v2, "minecraft:stick", () -> refreshed[0]++));
        assertSame(v2, KubeJSExporter.getAddedRecipes().get(0));
        assertTrue(KubeJSExporter.getRemovedRecipes().contains("minecraft:stick"), "reemplaza la receta original");

        PendingChangeManager.discard(onlyQueued());
        assertSame(v1, KubeJSExporter.getAddedRecipes().get(0), "descartar vuelve a la version anterior");
        assertFalse(KubeJSExporter.getRemovedRecipes().contains("minecraft:stick"));
        assertEquals(1, refreshed[0], "la pantalla refresca sus listas al descartar");
    }

    @Test
    void deleteIsQueuedAndDiscardBringsTheRecipeBack() {
        RecipeData v1 = recipe("shovel_js_gear", Items.IRON_INGOT);
        KubeJSExporter.stageRecipe(v1);
        RecipeDraftActions.queueDelete("shovel_js_gear", () -> {});
        assertTrue(KubeJSExporter.getAddedRecipes().isEmpty());
        assertEquals(PendingChange.Action.DELETE, onlyQueued().action());
        PendingChangeManager.discard(onlyQueued());
        assertEquals(List.of(v1), KubeJSExporter.getAddedRecipes());
    }

    @Test
    void conflictGridMatchesAVanillaCraftingTable() {
        ItemStack[] g = new ItemStack[4];
        Arrays.fill(g, ItemStack.EMPTY);
        g[1] = new ItemStack(Items.STICK, 5);
        g[2] = new ItemStack(Items.COAL);
        List<ItemStack> shaped = RecipeGridRules.craftingGrid9(RecipeType.VANILLA_SHAPED, g, 2, 2);
        assertSame(Items.STICK, shaped.get(1).getItem());
        assertEquals(1, shaped.get(1).getCount(), "cantidades a 1");
        assertSame(Items.COAL, shaped.get(3).getItem(), "fila 1 de 3 columnas");
        List<ItemStack> shapeless = RecipeGridRules.craftingGrid9(RecipeType.VANILLA_SHAPELESS, g, 2, 2);
        assertSame(Items.STICK, shapeless.get(0).getItem());
        assertSame(Items.COAL, shapeless.get(1).getItem());
        assertNull(RecipeGridRules.craftingGrid9(RecipeType.VANILLA_SHAPED, new ItemStack[25], 5, 5));
        assertNull(RecipeGridRules.craftingGrid9(RecipeType.CUSTOM_JSON, g, 2, 2));
    }
}
