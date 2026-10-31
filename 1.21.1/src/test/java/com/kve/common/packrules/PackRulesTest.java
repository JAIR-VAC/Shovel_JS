package com.kve.common.packrules;

import com.google.gson.JsonObject;
import com.kve.common.kubejs.KveScriptPaths;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ComposterBlock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Reglas del pack: nombres, info JEI/REI, compostables, spawns y gamerules. */
class PackRulesTest {

    @AfterEach
    void cleanRuntime() {
        PackRulesRuntime.clear();
    }

    private static PackRules sample() {
        PackRules r = new PackRules();
        PackRules.Rename n = new PackRules.Rename();
        n.targetId = "create:cogwheel"; n.langKey = "block.create.cogwheel"; n.block = true; n.locale = "es_es"; n.name = "Engranaje";
        r.renames.add(n);
        PackRules.InfoPage i = new PackRules.InfoPage();
        i.itemId = "minecraft:ender_eye"; i.title = "Ojo"; i.lines.add("Encuentra la fortaleza"); i.lines.add("  "); i.lines.add("It's rare");
        r.infoPages.add(i);
        PackRules.Compostable c = new PackRules.Compostable();
        c.itemId = "minecraft:rotten_flesh"; c.chance = 0.3f;
        r.compostables.add(c);
        PackRules.SpawnBlock s = new PackRules.SpawnBlock();
        s.entityId = "minecraft:phantom"; s.dimensions.add("minecraft:overworld");
        r.spawnBlocks.add(s);
        PackRules.GameRuleDefault g = new PackRules.GameRuleDefault();
        g.rule = "doInsomnia"; g.value = "false";
        r.gameRules.add(g);
        return r;
    }

    @Test
    void validationCatchesBadIdsLocalesAndValues() {
        assertTrue(sample().validate().isEmpty(), sample().validate().toString());
        PackRules bad = sample();
        bad.renames.get(0).locale = "Spanish";
        bad.compostables.get(0).chance = 1.5f;
        bad.gameRules.get(0).value = "maybe";
        bad.spawnBlocks.get(0).entityId = "Phantom";
        assertEquals(4, bad.validate().size(), bad.validate().toString());
    }

    @Test
    void jsonRoundTripKeepsEverything() {
        PackRules back = PackRules.fromJson(sample().toJson());
        assertEquals(sample().toJson(), back.toJson());
        assertTrue(new PackRules().isEmpty());
        assertTrue(PackRules.fromJson(new JsonObject()).isEmpty(), "un archivo viejo sin secciones no rompe");
    }

    @Test
    void serverScriptRegistersRulesThroughKveApi() {
        String js = PackRulesExporter.buildServerScript(sample());
        assertTrue(js.startsWith(PackRulesExporter.HEADER));
        assertTrue(js.contains("ShovelAPI.clearPackRules()\n"));
        assertTrue(js.contains("ShovelAPI.setCompostable('minecraft:rotten_flesh', 0.3)"), js);
        assertTrue(js.contains("ShovelAPI.blockSpawn('minecraft:phantom', 'minecraft:overworld', true)"), js);
        assertTrue(js.contains("ShovelAPI.defaultGameRule('doInsomnia', 'false')"), js);
        assertTrue(com.kve.common.kubejs.ScriptWriteGuard.looksWellFormed(js));
    }

    @Test
    void clientScriptAddsInfoPagesForJeiAndRei() {
        String js = PackRulesExporter.buildClientInfoScript(sample());
        assertTrue(js.contains("RecipeViewerEvents.addInformation('item', event => {"), js);
        assertTrue(js.contains("event.add('minecraft:ender_eye', ['Ojo'].concat(['Encuentra la fortaleza', 'It\\'s rare']))"), js);
        assertFalse(js.contains("JEIEvents"), js);
        assertTrue(com.kve.common.kubejs.ScriptWriteGuard.looksWellFormed(js));
        assertFalse(PackRulesExporter.buildClientInfoScript(new PackRules()).contains("JEIEvents"));
    }

    @Test
    void renamesGoToTheModOwnNamespaceAndKeepHandWrittenKeys() {
        Map<Path, Map<String, String>> files = PackRulesExporter.langEntries(sample());
        Path expected = KveScriptPaths.langFile("create", "es_es");
        assertEquals(Set.of(expected), files.keySet());
        assertEquals(Map.of("block.create.cogwheel", "Engranaje"), files.get(expected));

        JsonObject existing = new JsonObject();
        existing.addProperty("my.own.key", "a mano");
        existing.addProperty("item.create.old_name", "viejo");
        JsonObject merged = PackRulesExporter.mergeLang(existing, Set.of("item.create.old_name"), files.get(expected));
        assertEquals("a mano", merged.get("my.own.key").getAsString(), "nunca toca claves escritas a mano");
        assertFalse(merged.has("item.create.old_name"), "saca lo que Shovel JS escribio antes y ya no esta");
        assertEquals("Engranaje", merged.get("block.create.cogwheel").getAsString());
    }

    @Test
    void compostablesApplyAndAreUndoneOnReload() {
        assertFalse(ComposterBlock.COMPOSTABLES.containsKey(Items.ROTTEN_FLESH));
        float seeds = ComposterBlock.COMPOSTABLES.getFloat(Items.WHEAT_SEEDS);
        PackRulesRuntime.setCompostable("minecraft:rotten_flesh", 0.3f);
        PackRulesRuntime.setCompostable("minecraft:wheat_seeds", 0f);
        assertEquals(0.3f, ComposterBlock.COMPOSTABLES.getFloat(Items.ROTTEN_FLESH), 1e-6);
        assertFalse(ComposterBlock.COMPOSTABLES.containsKey(Items.WHEAT_SEEDS), "chance 0 = no compostable");
        PackRulesRuntime.clear();
        assertFalse(ComposterBlock.COMPOSTABLES.containsKey(Items.ROTTEN_FLESH), "reload: vuelve a vanilla");
        assertEquals(seeds, ComposterBlock.COMPOSTABLES.getFloat(Items.WHEAT_SEEDS), 1e-6);
    }

    @Test
    void spawnRulesOnlyBlockWhatTheyShould() {
        List<PackRulesRuntime.SpawnRule> rules = List.of(
                new PackRulesRuntime.SpawnRule("minecraft:phantom", Set.of(), true),
                new PackRulesRuntime.SpawnRule("minecraft:creeper", PackRulesRuntime.parseCsv("minecraft:overworld, minecraft:the_nether"), false));
        assertTrue(PackRulesRuntime.blocks(rules, "minecraft:phantom", "minecraft:the_end", true));
        assertFalse(PackRulesRuntime.blocks(rules, "minecraft:phantom", "minecraft:overworld", false), "un spawner sigue funcionando");
        assertTrue(PackRulesRuntime.blocks(rules, "minecraft:creeper", "minecraft:the_nether", false));
        assertFalse(PackRulesRuntime.blocks(rules, "minecraft:creeper", "minecraft:the_end", true), "otra dimension");
        assertFalse(PackRulesRuntime.blocks(rules, "minecraft:zombie", "minecraft:overworld", true));
        assertTrue(PackRulesRuntime.isNaturalSpawn(MobSpawnType.NATURAL));
        assertTrue(PackRulesRuntime.isNaturalSpawn(MobSpawnType.CHUNK_GENERATION));
        assertFalse(PackRulesRuntime.isNaturalSpawn(MobSpawnType.SPAWNER));
        assertFalse(PackRulesRuntime.isNaturalSpawn(MobSpawnType.SPAWN_EGG));
    }

    @Test
    void gameRulesApplyOncePerWorldUnlessTheDefaultChanges() {
        Map<String, String> wanted = new java.util.LinkedHashMap<>(Map.of("doInsomnia", "false", "keepInventory", "true"));
        assertEquals(wanted, PackRulesRuntime.pending(wanted, Map.of()));
        assertEquals(Map.of("keepInventory", "true"), PackRulesRuntime.pending(wanted, Map.of("doInsomnia", "FALSE")));
        assertEquals(Map.of("doInsomnia", "false"), PackRulesRuntime.pending(wanted, Map.of("doInsomnia", "true", "keepInventory", "true")),
                "si el pack cambia el valor por defecto, se aplica de nuevo");
    }
}
