package com.kve.common.worldgen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BiomeExtrasExporterTest {

    private static WorldGenEntry entry() {
        WorldGenEntry e = new WorldGenEntry();
        e.id = "shovel_js:rule";
        e.biomeIds.add("#minecraft:is_overworld");
        return e;
    }

    private static Path modifier(String name) {
        return Paths.get("kubejs", "data", "shovel_js", "forge", "biome_modifier", name + ".json");
    }

    private static void assertJson(String expected, JsonObject actual) {
        assertEquals(JsonParser.parseString(expected), actual, "\nactual: " + actual);
    }

    @Test
    void nothingToWriteWithoutExtras() {
        assertTrue(BiomeExtrasExporter.build(entry()).isEmpty());
    }

    @Test
    void removeFeaturesSplitsTagsIntoTheirOwnModifier() {
        WorldGenEntry e = entry();
        e.removedFeatures.addAll(List.of("minecraft:ore_diamond", "minecraft:ore_diamond_large", "#c:ores"));
        Map<Path, JsonObject> files = BiomeExtrasExporter.build(e);
        assertJson("{\"type\":\"forge:remove_features\",\"biomes\":\"#minecraft:is_overworld\","
                + "\"features\":[\"minecraft:ore_diamond\",\"minecraft:ore_diamond_large\"]}", files.get(modifier("rule_remove_features")));
        assertJson("{\"type\":\"forge:remove_features\",\"biomes\":\"#minecraft:is_overworld\",\"features\":\"#c:ores\"}",
                files.get(modifier("rule_remove_features_1")));
    }

    @Test
    void spawnsAddAndRemove() {
        WorldGenEntry e = entry();
        WorldGenEntry.SpawnRule add = new WorldGenEntry.SpawnRule();
        add.entityId = "minecraft:fox";
        add.weight = 8;
        add.minCount = 2;
        add.maxCount = 4;
        WorldGenEntry.SpawnRule rem = new WorldGenEntry.SpawnRule();
        rem.entityId = "minecraft:creeper";
        rem.remove = true;
        e.spawns.addAll(List.of(add, rem));
        Map<Path, JsonObject> files = BiomeExtrasExporter.build(e);
        assertJson("{\"type\":\"forge:add_spawns\",\"biomes\":\"#minecraft:is_overworld\","
                + "\"spawners\":[{\"type\":\"minecraft:fox\",\"weight\":8,\"minCount\":2,\"maxCount\":4}]}", files.get(modifier("rule_add_spawns")));
        assertJson("{\"type\":\"forge:remove_spawns\",\"biomes\":\"#minecraft:is_overworld\",\"entity_types\":\"minecraft:creeper\"}",
                files.get(modifier("rule_remove_spawns")));
    }

    @Test
    void patchMatchesVanillaFlowerShape() {
        WorldGenEntry e = entry();
        WorldGenEntry.Patch p = new WorldGenEntry.Patch();
        p.patchId = "abc";
        p.block = "minecraft:poppy";
        p.tries = 64;
        p.xzSpread = 7;
        p.ySpread = 3;
        p.rarity = 4;
        e.patches.add(p);
        Map<Path, JsonObject> files = BiomeExtrasExporter.build(e);
        assertJson("{\"type\":\"minecraft:random_patch\",\"config\":{\"tries\":64,\"xz_spread\":7,\"y_spread\":3,\"feature\":{"
                        + "\"feature\":{\"type\":\"minecraft:simple_block\",\"config\":{\"to_place\":{\"type\":\"minecraft:simple_state_provider\",\"state\":{\"Name\":\"minecraft:poppy\"}}}},"
                        + "\"placement\":[{\"type\":\"minecraft:block_predicate_filter\",\"predicate\":{\"type\":\"minecraft:matching_blocks\",\"blocks\":\"minecraft:air\"}}]}}}",
                files.get(Paths.get("kubejs", "data", "shovel_js", "worldgen", "configured_feature", "rule_patch_abc.json")));
        assertJson("{\"feature\":\"shovel_js:rule_patch_abc\",\"placement\":[{\"type\":\"minecraft:rarity_filter\",\"chance\":4},"
                        + "{\"type\":\"minecraft:in_square\"},{\"type\":\"minecraft:heightmap\",\"heightmap\":\"MOTION_BLOCKING\"},{\"type\":\"minecraft:biome\"}]}",
                files.get(Paths.get("kubejs", "data", "shovel_js", "worldgen", "placed_feature", "rule_patch_abc.json")));
        assertJson("{\"type\":\"forge:add_features\",\"biomes\":\"#minecraft:is_overworld\",\"features\":\"shovel_js:rule_patch_abc\",\"step\":\"vegetal_decoration\"}",
                files.get(modifier("rule_patch_abc")));
    }

    @Test
    void mixedBiomeSelectionUsesGeneratedTag() {
        WorldGenEntry e = entry();
        e.biomeIds.add("minecraft:plains");
        e.removedFeatures.add("minecraft:ore_coal_upper");
        Map<Path, JsonObject> files = BiomeExtrasExporter.build(e);
        assertJson("{\"replace\":false,\"values\":[\"#minecraft:is_overworld\",\"minecraft:plains\"]}",
                files.get(Paths.get("kubejs", "data", "shovel_js", "tags", "worldgen", "biome", "rule_extra_biomes.json")));
        assertEquals("#shovel_js:rule_extra_biomes", files.get(modifier("rule_remove_features")).get("biomes").getAsString());
    }

    @Test
    void validationRejectsBadExtras() {
        WorldGenEntry e = entry();
        WorldGenEntry.SpawnRule tagAdd = new WorldGenEntry.SpawnRule();
        tagAdd.entityId = "#minecraft:skeletons";
        e.spawns.add(tagAdd);
        WorldGenEntry.Patch p = new WorldGenEntry.Patch();
        p.block = "";
        e.patches.add(p);
        e.removedFeatures.add("Not An Id");
        List<String> errors = e.validate();
        assertEquals(3, errors.size(), errors.toString());
    }

    @Test
    void extrasAndRaritySurviveJsonRoundTrip() {
        WorldGenEntry e = entry();
        e.removedFeatures.add("minecraft:ore_emerald");
        WorldGenEntry.SpawnRule r = new WorldGenEntry.SpawnRule();
        r.entityId = "minecraft:fox";
        e.spawns.add(r);
        WorldGenEntry.Patch p = new WorldGenEntry.Patch();
        p.patchId = "x";
        p.block = "minecraft:dandelion";
        e.patches.add(p);
        WorldGenEntry.OreVein v = new WorldGenEntry.OreVein();
        v.veinId = "v";
        v.oreBlock = "minecraft:emerald_ore";
        v.rarity = 9;
        e.oreVeins.add(v);
        WorldGenEntry back = WorldGenExporter.entryFromJson(WorldGenExporter.entryToJson(e));
        assertEquals(WorldGenExporter.entryToJson(e), WorldGenExporter.entryToJson(back));
        assertEquals(9, back.oreVeins.get(0).rarity);
        assertEquals("minecraft:dandelion", back.patches.get(0).block);
    }

    @Test
    void rareVeinUsesRarityFilterInsteadOfCount() {
        WorldGenEntry e = entry();
        WorldGenEntry.OreVein v = new WorldGenEntry.OreVein();
        v.veinId = "v";
        v.oreBlock = "minecraft:emerald_ore";
        v.rarity = 9;
        e.oreVeins.add(v);
        String preview = WorldGenExporter.previewWorldGenJson(e);
        assertTrue(preview.contains("\"minecraft:rarity_filter\""), preview);
        assertTrue(preview.contains("\"chance\": 9"), preview);
        assertFalse(preview.contains("\"minecraft:count\""), preview);
    }
}
