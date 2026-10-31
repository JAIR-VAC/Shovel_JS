package com.kve.client.recipe;

import com.kve.common.recipe.RecipeData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/** Busqueda de la lista del editor de recetas y derivacion de la estacion. */
class RecipeListFilterTest {

    private static RecipeData recipe(String id, String category) {
        RecipeData r = new RecipeData();
        r.recipeId = id;
        r.category = category;
        return r;
    }

    private static final List<RecipeData> RECIPES = List.of(
            recipe("shovel_js:iron_gear", "gears"),
            recipe("shovel_js:copper_gear", "gears"),
            recipe("shovel_js:stone_rod", null),
            recipe("shovel_js:gold_gear", "Gears"));
    private static final Predicate<String> FAVS = Set.of("shovel_js:gold_gear", "shovel_js:stone_rod")::contains;

    private static List<String> ids(String query) {
        return RecipeListFilter.parse(query).apply(RECIPES, FAVS).stream().map(r -> r.recipeId).toList();
    }

    @Test
    void parsesTokensAndKeepsTheRestAsText() {
        RecipeListFilter f = RecipeListFilter.parse("  gear !FAV  cat:gears iron ");
        assertTrue(f.favoritesOnly());
        assertEquals("gears", f.category());
        assertEquals("gear iron", f.text());
        assertEquals(new RecipeListFilter(false, null, "", null, null, null, null), RecipeListFilter.parse(null));
    }

    @Test
    void favoritesComeFirstKeepingOriginalOrder() {
        assertEquals(List.of("shovel_js:stone_rod", "shovel_js:gold_gear", "shovel_js:iron_gear", "shovel_js:copper_gear"), ids(""));
    }

    @Test
    void tokensCombineWithTextSearch() {
        assertEquals(List.of("shovel_js:stone_rod", "shovel_js:gold_gear"), ids("!fav"));
        assertEquals(List.of("shovel_js:gold_gear", "shovel_js:iron_gear", "shovel_js:copper_gear"), ids("cat:gears"), "categoria sin distinguir mayusculas");
        assertEquals(List.of("shovel_js:gold_gear"), ids("!fav cat:gears"));
        assertEquals(List.of("shovel_js:copper_gear"), ids("copper"));
    }

    @Test
    void typeModOutputAndStateTokensPlusSorting() {
        RecipeData a = recipe("zeta", null);
        a.type = RecipeData.RecipeType.VANILLA_SHAPELESS;
        a.output = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_BLOCK);
        RecipeData b = recipe("alpha", null);
        b.type = RecipeData.RecipeType.CUSTOM_JSON;
        b.customTypeId = "minecraft:smelting";
        b.output = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLD_INGOT);
        b.enabled = false;
        List<RecipeData> all = List.of(a, b);
        java.util.function.Predicate<String> none = id -> false;
        assertEquals(List.of(a), RecipeListFilter.parse("type:shapeless").apply(all, none));
        assertEquals(List.of(b), RecipeListFilter.parse("type:smelting").apply(all, none));
        assertEquals(List.of(a, b), RecipeListFilter.parse("mod:minecraft").apply(all, none));
        assertEquals(List.of(b), RecipeListFilter.parse("out:gold").apply(all, none));
        assertEquals(List.of(b), RecipeListFilter.parse("is:off").apply(all, none));
        assertEquals(List.of(a), RecipeListFilter.parse("is:on").apply(all, none));
        assertEquals(List.of(b, a), RecipeListFilter.parse("").apply(all, none, RecipeListFilter.Sort.NAME));
        assertEquals(List.of(b, a), RecipeListFilter.parse("").apply(all, none, RecipeListFilter.Sort.OUTPUT), "gold < iron");
        assertEquals(List.of(a, b), RecipeListFilter.parse("").apply(all, none, RecipeListFilter.Sort.DEFAULT));
    }

    @Test
    void stationKeywordDropsViewerTailsAndVerbSuffixes() {
        assertArrayEquals(new String[]{"create", "crush"}, StationResolver.namespaceAndKeyword("create:crushing"));
        assertArrayEquals(new String[]{"mekanism", "enrich"}, StationResolver.namespaceAndKeyword("mekanism:enriching"));
        assertArrayEquals(new String[]{"trinity", "trinity_starforge"}, StationResolver.namespaceAndKeyword("trinity:trinity_starforge_jei"));
        assertArrayEquals(new String[]{"thermal", "pulveriz"}, StationResolver.namespaceAndKeyword("thermal:machines/pulverizer"));
        assertArrayEquals(new String[]{"", "press"}, StationResolver.namespaceAndKeyword("pressing"));
    }
}
