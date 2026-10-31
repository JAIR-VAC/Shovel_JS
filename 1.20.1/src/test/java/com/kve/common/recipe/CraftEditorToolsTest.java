package com.kve.common.recipe;

import com.kve.common.kubejs.KubeJSExporter;
import com.kve.common.util.UndoStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Herramientas del editor de recetas: tags, grilla, plantillas, coste, inversa, horno, pegar JSON. */
class CraftEditorToolsTest {

    private static ItemStack st(net.minecraft.world.item.Item i) { return new ItemStack(i); }

    private static GridOps.Grid grid3(ItemStack... cells) {
        ItemStack[] s = new ItemStack[9];
        Arrays.fill(s, ItemStack.EMPTY);
        for (int i = 0; i < cells.length; i++) s[i] = cells[i] == null ? ItemStack.EMPTY : cells[i];
        return GridOps.Grid.of(s, 3, 3);
    }

    @Test
    void tagIngredientsExportAsTagsEverywhere() {
        ItemStack tag = TagIngredient.of("#forge:ingots/iron", Items.IRON_INGOT);
        assertEquals("forge:ingots/iron", TagIngredient.tagOf(tag));
        assertNull(TagIngredient.tagOf(st(Items.STICK)));

        RecipeData shaped = new RecipeData();
        shaped.recipeId = "t";
        shaped.type = RecipeData.RecipeType.VANILLA_SHAPED;
        shaped.gridWidth = 1; shaped.gridHeight = 2;
        shaped.ingredients = new ArrayList<>(List.of(tag, st(Items.STICK)));
        shaped.output = st(Items.IRON_SWORD);
        String js = KubeJSExporter.previewRecipeJS(shaped);
        assertTrue(js.contains("A: '#forge:ingots/iron'"), js);
        assertFalse(js.contains("shovel_js_tag"), "la marca nunca sale al script");

        RecipeData shapeless = new RecipeData();
        shapeless.recipeId = "u";
        shapeless.type = RecipeData.RecipeType.VANILLA_SHAPELESS;
        shapeless.shapelessIngredients = new ArrayList<>(List.of(tag));
        shapeless.output = st(Items.IRON_NUGGET);
        assertTrue(KubeJSExporter.previewRecipeJS(shapeless).contains("['#forge:ingots/iron']"));

        RecipeData custom = new RecipeData();
        custom.recipeId = "c";
        custom.type = RecipeData.RecipeType.CUSTOM_JSON;
        custom.customTypeId = "somemod:grinding";
        custom.shapelessIngredients = new ArrayList<>(List.of(tag));
        custom.output = st(Items.IRON_NUGGET);
        assertTrue(KubeJSExporter.previewRecipeJS(custom).contains("ingredient: { tag: 'forge:ingots/iron' }"),
                KubeJSExporter.previewRecipeJS(custom));
    }

    @Test
    void mirrorRotateShiftTrimAndFill() {
        GridOps.Grid g = grid3(st(Items.DIAMOND), null, null, st(Items.STICK));
        GridOps.Grid h = GridOps.mirrorHorizontal(g);
        assertSame(Items.DIAMOND, h.at(2, 0).getItem());
        assertSame(Items.STICK, h.at(2, 1).getItem());
        assertSame(Items.STICK, GridOps.mirrorVertical(g).at(0, 1).getItem());
        assertSame(Items.DIAMOND, GridOps.mirrorVertical(g).at(0, 2).getItem());

        ItemStack[] wide = new ItemStack[6];
        Arrays.fill(wide, ItemStack.EMPTY);
        wide[0] = st(Items.DIAMOND);
        GridOps.Grid rot = GridOps.rotateClockwise(GridOps.Grid.of(wide, 3, 2));
        assertEquals(2, rot.w());
        assertEquals(3, rot.h());
        assertSame(Items.DIAMOND, rot.at(1, 0).getItem(), "la esquina sup. izquierda pasa a la sup. derecha");

        assertNull(GridOps.shift(g, -1, 0), "un item quedaria afuera");
        GridOps.Grid moved = GridOps.shift(g, 1, 1);
        assertSame(Items.DIAMOND, moved.at(1, 1).getItem());
        GridOps.Grid back = GridOps.trimToCorner(moved);
        assertSame(Items.DIAMOND, back.at(0, 0).getItem());
        assertArrayEquals(new int[]{0, 0, 0, 1}, GridOps.bounds(back));
        assertNull(GridOps.bounds(grid3()));

        GridOps.Grid ring = GridOps.fillBorder(grid3(), st(Items.COBBLESTONE));
        assertTrue(ring.at(1, 1).isEmpty());
        assertSame(Items.COBBLESTONE, ring.at(2, 2).getItem());
        GridOps.Grid full = GridOps.fillEmpty(g, st(Items.DIRT));
        assertSame(Items.DIAMOND, full.at(0, 0).getItem(), "no pisa lo que ya estaba");
        assertSame(Items.DIRT, full.at(2, 2).getItem());
        assertTrue(g.at(2, 2).isEmpty(), "la grilla original no cambia (deshacer)");
    }

    @Test
    void templatesFillTheGridWithTheRealVanillaShapes() {
        RecipeTemplates.Template pick = RecipeTemplates.byId("pickaxe");
        GridOps.Grid g = RecipeTemplates.apply(pick, st(Items.IRON_INGOT), st(Items.STICK), 3, 3);
        assertSame(Items.IRON_INGOT, g.at(2, 0).getItem());
        assertSame(Items.STICK, g.at(1, 2).getItem());
        assertTrue(g.at(0, 1).isEmpty());
        assertEquals(4, RecipeTemplates.byId("stairs").outputCount());
        assertNull(RecipeTemplates.apply(RecipeTemplates.byId("storage_3x3"), st(Items.IRON_INGOT), null, 2, 2), "no entra en 2x2");
        assertTrue(RecipeTemplates.ALL.stream().allMatch(t -> Arrays.stream(t.rows()).allMatch(r -> r.length() == t.width())),
                "todas las filas del mismo ancho");
    }

    @Test
    void costSuggestedIdReverseAndCookingVariants() {
        List<ItemStack> ings = List.of(st(Items.IRON_INGOT), st(Items.IRON_INGOT), ItemStack.EMPTY,
                TagIngredient.of("forge:rods/wooden", Items.STICK));
        Map<String, Integer> cost = RecipeHelpers.cost(ings);
        assertEquals(Map.of("minecraft:iron_ingot", 2, "#forge:rods/wooden", 1), cost);
        assertEquals("2× iron_ingot, 1× #forge:rods/wooden", RecipeHelpers.describeCost(cost));

        assertEquals("brass_ingot", RecipeHelpers.suggestId("create:brass_ingot", id -> false));
        assertEquals("brass_ingot_3", RecipeHelpers.suggestId("create:brass_ingot", Set.of("brass_ingot", "brass_ingot_2")::contains));

        RecipeData block = new RecipeData();
        block.type = RecipeData.RecipeType.VANILLA_SHAPED;
        block.ingredients = new ArrayList<>(Arrays.asList(grid3(st(Items.IRON_INGOT), st(Items.IRON_INGOT), st(Items.IRON_INGOT),
                st(Items.IRON_INGOT), st(Items.IRON_INGOT), st(Items.IRON_INGOT),
                st(Items.IRON_INGOT), st(Items.IRON_INGOT), st(Items.IRON_INGOT)).slots()));
        block.output = st(Items.IRON_BLOCK);
        RecipeData rev = RecipeHelpers.reverseOf(block);
        assertEquals(RecipeData.RecipeType.VANILLA_SHAPELESS, rev.type);
        assertSame(Items.IRON_BLOCK, rev.shapelessIngredients.get(0).getItem());
        assertEquals(9, rev.output.getCount());
        block.ingredients.set(4, st(Items.GOLD_INGOT));
        assertNull(RecipeHelpers.reverseOf(block), "mezcla de items: no hay inversa obvia");

        RecipeData smelt = new RecipeData();
        smelt.type = RecipeData.RecipeType.CUSTOM_JSON;
        smelt.customTypeId = "minecraft:smelting";
        smelt.customExtraJson = "\"cookingtime\": 150, \"experience\": 0.7";
        smelt.shapelessIngredients = new ArrayList<>(List.of(st(Items.RAW_IRON)));
        smelt.output = st(Items.IRON_INGOT);
        RecipeData blast = RecipeHelpers.cookingVariant(smelt, "minecraft:blasting");
        assertEquals("minecraft:blasting", blast.customTypeId);
        assertTrue(blast.customExtraJson.contains("\"cookingtime\":75"), blast.customExtraJson);
        assertTrue(blast.customExtraJson.contains("\"experience\":0.7"));
        assertNull(RecipeHelpers.cookingVariant(smelt, "minecraft:smelting"));
        smelt.customExtraJson = "";
        assertTrue(RecipeHelpers.cookingVariant(smelt, "minecraft:campfire_cooking").customExtraJson.contains("\"cookingtime\":600"));
        assertNull(RecipeHelpers.cookingVariant(block, "minecraft:blasting"));
    }

    @Test
    void pastedVanillaJsonBecomesADraft() {
        String shaped = "{\"type\":\"minecraft:crafting_shaped\",\"pattern\":[\"##\",\"#/\"],"
                + "\"key\":{\"#\":{\"tag\":\"forge:ingots/iron\"},\"/\":{\"item\":\"minecraft:stick\"}},"
                + "\"result\":{\"item\":\"minecraft:iron_axe\",\"count\":2}}";
        RecipeData d = VanillaRecipeImporter.parse(shaped).recipe();
        assertEquals(RecipeData.RecipeType.VANILLA_SHAPED, d.type);
        assertEquals(2, d.gridWidth);
        assertEquals("forge:ingots/iron", TagIngredient.tagOf(d.ingredients.get(0)));
        assertSame(Items.STICK, d.ingredients.get(3).getItem());
        assertEquals(2, d.output.getCount());

        RecipeData s = VanillaRecipeImporter.parse("{\"type\":\"smelting\",\"ingredient\":[{\"item\":\"minecraft:raw_gold\"},{\"item\":\"minecraft:gold_ore\"}],"
                + "\"result\":\"minecraft:gold_ingot\",\"experience\":1.0,\"cookingtime\":200}").recipe();
        assertEquals("minecraft:smelting", s.customTypeId);
        assertTrue(s.customExtraJson.contains("cookingtime"));
        assertSame(Items.RAW_GOLD, s.shapelessIngredients.get(0).getItem());
        assertEquals(1, VanillaRecipeImporter.parse("{\"type\":\"minecraft:smelting\",\"ingredient\":[{\"item\":\"minecraft:raw_gold\"},{\"item\":\"minecraft:gold_ore\"}],"
                + "\"result\":\"minecraft:gold_ingot\"}").warnings().size());

        RecipeData cut = VanillaRecipeImporter.parse("{\"type\":\"minecraft:stonecutting\",\"ingredient\":{\"item\":\"minecraft:stone\"},"
                + "\"result\":\"minecraft:stone_slab\",\"count\":2}").recipe();
        assertEquals(2, cut.output.getCount());

        assertThrows(IllegalArgumentException.class, () -> VanillaRecipeImporter.parse("not json"));
        assertThrows(IllegalArgumentException.class, () -> VanillaRecipeImporter.parse("{\"type\":\"create:pressing\"}"));
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class, () -> VanillaRecipeImporter.parse(
                "{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[{\"item\":\"nomod:thing\"}],\"result\":{\"item\":\"minecraft:stick\"}}"));
        assertTrue(missing.getMessage().contains("nomod:thing"));
    }

    @Test
    void undoRedoBehavesLikeAnyEditor() {
        UndoStack<String> u = new UndoStack<>(2);
        assertFalse(u.canUndo());
        u.push("a");
        u.push("b");
        u.push("c");
        assertEquals("c", u.undo("d"));
        assertEquals("b", u.undo("c"));
        assertNull(u.undo("b"));
        assertEquals("c", u.redo("b"));
        u.push("x");
        assertFalse(u.canRedo(), "un cambio nuevo borra el rehacer");
    }
}
