package com.kve.common.packtools;

import com.google.gson.JsonParser;
import com.kve.common.persist.PackSnapshotManager;
import com.kve.common.recipe.RecipeData;
import com.kve.common.worldgen.WorldGenEntry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Probador del pack, diff de instantaneas, biblioteca, balance y guia. */
class PackToolsFeaturesTest {

    @TempDir
    Path game;

    @AfterEach
    void resetLibrary() { TemplateLibrary.setDirForTests(null); }

    private void write(String rel, String text) throws Exception {
        Path p = game.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, text, StandardCharsets.UTF_8);
    }

    @Test
    void packCheckFindsBrokenScriptsJsonAndKubeJsErrors() throws Exception {
        write("kubejs/server_scripts/shovel_js/recipes/create/add.js",
                "ServerEvents.recipes(event => {\n  // ==== NO EXPORTADO: mi_receta [create:mixing] ====\n  event.custom({\n})\n");
        write("kubejs/server_scripts/shovel_js/tags/tags.js", "ServerEvents.tags('item', event => {\n  event.add('c:x', 'minecraft:stone')\n})\n");
        write("kubejs/data/shovel_js/recipe/roto.json", "{\"type\": \"minecraft:crafting_shaped\",");
        write("kubejs/data/shovel_js/recipe/bien.json", "{\"type\": \"minecraft:crafting_shaped\"}");
        write("logs/kubejs/server.log", "[12:00:00] [INFO] Loaded 3 scripts\n"
                + "[12:00:01] [ERROR] add.js#7: TypeError: Cannot read property 'id' of undefined\n"
                + "[12:00:01] [ERROR] add.js#7: TypeError: Cannot read property 'id' of undefined\n");

        List<PackCheck.Issue> issues = PackCheck.scanFiles(game);
        assertTrue(issues.stream().anyMatch(i -> i.key().equals("gui.kve.check.script_syntax")
                && i.where().endsWith("recipes/create/add.js")), issues.toString());
        assertTrue(issues.stream().anyMatch(i -> i.key().equals("gui.kve.check.not_exported")
                && i.args().get(0).startsWith("mi_receta")), issues.toString());
        assertTrue(issues.stream().anyMatch(i -> i.key().equals("gui.kve.check.bad_json") && i.where().endsWith("roto.json")));
        assertFalse(issues.stream().anyMatch(i -> i.where().endsWith("bien.json") || i.where().endsWith("tags.js")));
        assertEquals(1, issues.stream().filter(i -> i.key().equals("gui.kve.check.kubejs_error")).count(), "errores repetidos se agrupan");
        assertTrue(issues.stream().anyMatch(i -> i.where().startsWith("add.js:7")));
    }

    @Test
    void packCheckIsQuietOnEmptyPack() {
        assertTrue(PackCheck.scanFiles(game).isEmpty());
    }

    @Test
    void compareTwoSnapshotsAndReadEachVersion() throws Exception {
        write("kubejs/server_scripts/shovel_js/a.js", "uno\n");
        write("kubejs/server_scripts/shovel_js/b.js", "b\n");
        String first = PackSnapshotManager.create(game, "antes").getFileName().toString();
        write("kubejs/server_scripts/shovel_js/a.js", "dos\n");
        Files.delete(game.resolve("kubejs/server_scripts/shovel_js/b.js"));
        write("kubejs/server_scripts/shovel_js/c.js", "c\n");
        String second = PackSnapshotManager.create(game, "despues").getFileName().toString();

        PackSnapshotManager.Diff d = PackSnapshotManager.compareSnapshots(game, first, second);
        assertEquals(List.of("kubejs/server_scripts/shovel_js/c.js"), d.added());
        assertEquals(List.of("kubejs/server_scripts/shovel_js/b.js"), d.removed());
        assertEquals(List.of("kubejs/server_scripts/shovel_js/a.js"), d.changed());
        assertEquals("uno\n", PackSnapshotManager.readText(game, first, "kubejs/server_scripts/shovel_js/a.js"));
        assertEquals("dos\n", PackSnapshotManager.readText(game, null, "kubejs/server_scripts/shovel_js/a.js"));
        assertNull(PackSnapshotManager.readText(game, second, "kubejs/server_scripts/shovel_js/b.js"));
        assertTrue(PackSnapshotManager.compareSnapshots(game, second, null).isEmpty(), "la segunda es igual al estado actual");
        assertNull(PackSnapshotManager.readText(game, null, "../fuera.txt"), "fuera de lo que maneja Shovel JS");
    }

    @Test
    void libraryBundlesAreListedAndValidated() throws Exception {
        TemplateLibrary.setDirForTests(game.resolve("library"));
        Files.createDirectories(game.resolve("library"));
        Files.writeString(game.resolve("library/jefes.json"),
                "{\"format\":1,\"name\":\"Jefes\",\"createdAt\":5,\"mobTemplates\":[{\"name\":\"a\",\"data\":{}},{\"name\":\"b\",\"data\":{}}],\"tradeTemplates\":{\"t\":[]}}");
        Files.writeString(game.resolve("library/basura.json"), "no es json");
        List<TemplateLibrary.BundleInfo> list = TemplateLibrary.list();
        assertEquals(1, list.size());
        assertEquals("Jefes", list.get(0).name());
        assertEquals(2, list.get(0).mobs());
        assertEquals(1, list.get(0).trades());
        assertThrows(java.io.IOException.class, () -> TemplateLibrary.importBundle("../../fuera.json"));
        assertEquals("jefes-del-nether", TemplateLibrary.slug("Jefes del Nether!"));
        assertEquals("plantillas", TemplateLibrary.slug("  "));
    }

    @Test
    void worldGenRuleTravelsThroughTheLibraryWithFreshVeinIds() throws Exception {
        TemplateLibrary.setDirForTests(game.resolve("library"));
        WorldGenEntry rule = new WorldGenEntry();
        rule.id = "rubi_nether";
        rule.biomeIds = new ArrayList<>(List.of(WorldGenEntry.Dimension.NETHER.biomeTag));
        WorldGenEntry.OreVein v = new WorldGenEntry.OreVein();
        v.veinId = "veta-original";
        v.oreBlock = "minecraft:emerald_ore";
        v.replaceableTarget = WorldGenEntry.Dimension.NETHER.oreTarget;
        rule.oreVeins.add(v);

        String file = TemplateLibrary.exportWorldGenRule(rule, "Rubi del Nether").getFileName().toString();
        TemplateLibrary.BundleInfo info = TemplateLibrary.list().get(0);
        assertEquals(1, info.worldGen());
        assertEquals(0, info.mobs());

        TemplateLibrary.ImportResult r = TemplateLibrary.importBundle(file);
        assertEquals(1, r.worldGen().size());
        WorldGenEntry back = r.worldGen().get(0);
        assertEquals(List.of("#minecraft:is_nether"), back.biomeIds);
        assertEquals("minecraft:base_stone_nether", back.oreVeins.get(0).replaceableTarget);
        assertNotEquals("veta-original", back.oreVeins.get(0).veinId, "cada copia importada lleva sus propios archivos");

        assertEquals("rubi", TemplateLibrary.uniqueRuleId("rubi", List.of("otra")));
        assertEquals("rubi_3", TemplateLibrary.uniqueRuleId("rubi", List.of("rubi", "rubi_2")));
    }

    @Test
    void dimensionRulesAreRecognisedByTheirBiomeTag() {
        assertEquals(WorldGenEntry.Dimension.END, WorldGenEntry.Dimension.of(List.of("#minecraft:is_end")));
        assertNull(WorldGenEntry.Dimension.of(List.of("#minecraft:is_end", "minecraft:plains")), "mezcla = regla por biomas");
        assertNull(WorldGenEntry.Dimension.of(List.of("minecraft:plains")));
        assertTrue(WorldGenEntry.Dimension.isDefaultTarget("minecraft:stone_ore_replaceables"));
        assertFalse(WorldGenEntry.Dimension.isDefaultTarget("minecraft:deepslate_ore_replaceables"));
    }

    @Test
    void dupeLoopAgainstGameRecipeIsFound() {
        var pack = List.of(new BalanceCheck.Conversion("minecraft:iron_block", 1, "minecraft:iron_ingot", 10, "shovel_js:mas_hierro"));
        var game = List.of(new BalanceCheck.Conversion("minecraft:iron_ingot", 9, "minecraft:iron_block", 1, "minecraft:iron_block"));
        List<BalanceCheck.Finding> f = BalanceCheck.dupeLoops(pack, game);
        assertEquals(1, f.size());
        assertEquals("gui.kve.balance.dupe", f.get(0).key());
        var fair = List.of(new BalanceCheck.Conversion("minecraft:iron_block", 1, "minecraft:iron_ingot", 9, "x"));
        assertTrue(BalanceCheck.dupeLoops(fair, game).isEmpty());
    }

    @Test
    void conversionOnlyForSingleItemRecipes() {
        RecipeData r = new RecipeData();
        r.recipeId = "r";
        r.type = RecipeData.RecipeType.VANILLA_SHAPELESS;
        r.shapelessIngredients = new ArrayList<>(List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.IRON_INGOT)));
        r.output = new ItemStack(Items.IRON_NUGGET, 20);
        BalanceCheck.Conversion c = BalanceCheck.conversionOf(r);
        assertNotNull(c);
        assertEquals(2, c.inCount());
        assertEquals(20, c.outCount());
        r.shapelessIngredients.add(new ItemStack(Items.STICK));
        assertNull(BalanceCheck.conversionOf(r), "mezcla de items: no es una conversion simple");
    }

    @Test
    void mobValuesAboveVanillaCapsAreFlagged() {
        var m = new com.kve.common.kubejs.KubeJSMobExporter.MobEntry();
        m.entityId = "minecraft:zombie";
        m.health = 5000;
        m.attackDamage = 10;
        List<BalanceCheck.Finding> out = new ArrayList<>();
        BalanceCheck.checkMob(m, out);
        assertEquals(1, out.size());
        assertEquals(List.of("health", "5000", "1024"), out.get(0).args());
    }

    @Test
    void guideDescribesRecipes() {
        RecipeData r = new RecipeData();
        r.recipeId = "r";
        r.type = RecipeData.RecipeType.VANILLA_SHAPED;
        r.ingredients = new ArrayList<>(List.of(new ItemStack(Items.IRON_INGOT), ItemStack.EMPTY, new ItemStack(Items.IRON_INGOT)));
        r.output = new ItemStack(Items.IRON_BARS, 16);
        assertEquals("16x `minecraft:iron_bars` <- 2x `minecraft:iron_ingot`", PackGuide.describe(r));
        assertEquals("Crafteo con forma", PackGuide.typeLabel(r));
        String md = PackGuide.build("Mi Pack");
        assertTrue(md.startsWith("# Mi Pack\n"));
        JsonParser.parseString("{}");
    }
}
