package com.kve.common.worldgen;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorldGenExporterGoldenTest {

    @Test
    void multiBiomeVeinProducesArrayBiomeModifierJson() {
        OreFeatureExporter.OreFeature f = new OreFeatureExporter.OreFeature();
        f.id = "shovel_js:my_rule_vein0";
        f.oreBlock = "minecraft:copper_ore";
        f.replaceableTarget = "minecraft:stone_ore_replaceables";
        f.veinSize = 10;
        f.veinsPerChunk = 6;
        f.minY = -16;
        f.maxY = 112;
        f.discardChanceOnAirExposure = 0.0;
        f.biomeFilters = List.of("minecraft:plains", "minecraft:forest");

        String preview = OreFeatureExporter.previewOreFeatureJson(f);

        Path expectedPath = Paths.get("kubejs", "data", "shovel_js", "neoforge", "biome_modifier", "my_rule_vein0.json");
        String expected = "// " + expectedPath + "\n"
                + "{\n"
                + "  \"type\": \"neoforge:add_features\",\n"
                + "  \"biomes\": [\n"
                + "    \"minecraft:plains\",\n"
                + "    \"minecraft:forest\"\n"
                + "  ],\n"
                + "  \"features\": \"shovel_js:my_rule_vein0\",\n"
                + "  \"step\": \"underground_ores\"\n"
                + "}\n";

        assertTrue(preview.contains(expected), preview);
        assertFalse(preview.contains("/tags/worldgen/biome/"), preview);
    }

    @Test
    void singleBiomeVeinKeepsPlainStringBiomesField() {
        OreFeatureExporter.OreFeature f = new OreFeatureExporter.OreFeature();
        f.id = "shovel_js:single_vein";
        f.oreBlock = "minecraft:iron_ore";
        f.veinSize = 8;
        f.veinsPerChunk = 4;
        f.minY = -64;
        f.maxY = 64;
        f.biomeFilters = List.of("#minecraft:is_overworld");

        String preview = OreFeatureExporter.previewOreFeatureJson(f);

        assertTrue(preview.contains("\"biomes\": \"#minecraft:is_overworld\""),
                "single-biome vein should keep the plain-string shorthand, not wrap it in an array");
    }

    @Test
    void trapezoidHeightDistributionChangesHeightProviderTypeOnly() {
        OreFeatureExporter.OreFeature f = new OreFeatureExporter.OreFeature();
        f.id = "shovel_js:trap_vein";
        f.oreBlock = "minecraft:diamond_ore";
        f.veinSize = 8;
        f.veinsPerChunk = 3;
        f.minY = -80;
        f.maxY = 16;
        f.heightDistribution = OreFeatureExporter.OreFeature.HeightDistribution.TRAPEZOID;
        f.biomeFilters = List.of("#minecraft:is_overworld");

        String preview = OreFeatureExporter.previewOreFeatureJson(f);

        assertTrue(preview.contains("\"features\": \"shovel_js:trap_vein\""), preview);
        assertTrue(preview.contains("\"type\": \"minecraft:trapezoid\""), preview);
        assertFalse(preview.contains("\"type\": \"minecraft:uniform\""), preview);
    }

    private static OreFeatureExporter.OreFeature veinWithBiomes(String id, List<String> biomes) {
        OreFeatureExporter.OreFeature f = new OreFeatureExporter.OreFeature();
        f.id = id;
        f.oreBlock = "minecraft:iron_ore";
        f.veinSize = 9;
        f.veinsPerChunk = 5;
        f.minY = -24;
        f.maxY = 56;
        f.biomeFilters = biomes;
        return f;
    }

    @Test
    void mixedTagAndIdBiomesUseGeneratedBiomeTagInsteadOfInvalidInlineList() {
        OreFeatureExporter.OreFeature f = veinWithBiomes("shovel_js:mix_vein",
                List.of("minecraft:desert", "#minecraft:is_overworld"));

        String preview = OreFeatureExporter.previewOreFeatureJson(f);

        assertTrue(preview.contains("\"biomes\": \"#shovel_js:mix_vein_biomes\""), preview);
        Path tagPath = Paths.get("kubejs", "data", "shovel_js", "tags", "worldgen", "biome", "mix_vein_biomes.json");
        assertTrue(preview.contains("// " + tagPath), preview);
        assertTrue(preview.contains("\"replace\": false"), preview);
        assertTrue(preview.contains("\"minecraft:desert\""), preview);
        assertTrue(preview.contains("\"#minecraft:is_overworld\""), preview);
        assertFalse(preview.contains("\"biomes\": ["), preview);
    }

    @Test
    void severalTagsAlsoUseGeneratedBiomeTag() {
        OreFeatureExporter.OreFeature f = veinWithBiomes("shovel_js:tags_vein",
                List.of("#minecraft:is_forest", "#minecraft:is_taiga"));

        String preview = OreFeatureExporter.previewOreFeatureJson(f);

        assertTrue(preview.contains("\"biomes\": \"#shovel_js:tags_vein_biomes\""), preview);
        assertTrue(preview.contains("\"#minecraft:is_forest\""), preview);
        assertTrue(preview.contains("\"#minecraft:is_taiga\""), preview);
    }

    @Test
    void blankAndDuplicateBiomeSelectorsAreDroppedBeforeChoosingTheShape() {
        OreFeatureExporter.OreFeature f = veinWithBiomes("shovel_js:dup_vein",
                java.util.Arrays.asList("minecraft:plains", " ", "minecraft:plains", ""));

        String preview = OreFeatureExporter.previewOreFeatureJson(f);

        assertTrue(preview.contains("\"biomes\": \"minecraft:plains\""), preview);
        assertFalse(preview.contains("/tags/worldgen/biome/"), preview);
    }

    @Test
    void previewShowsAllThreeFilesSoApplyDiffSeesVeinParameters() {
        OreFeatureExporter.OreFeature f = veinWithBiomes("shovel_js:full_vein", List.of("minecraft:plains"));
        f.extraTargets = List.of(target("minecraft:deepslate_iron_ore", "minecraft:deepslate_ore_replaceables"));

        String preview = OreFeatureExporter.previewOreFeatureJson(f);

        assertTrue(preview.contains(Paths.get("kubejs", "data", "shovel_js", "worldgen", "configured_feature", "full_vein.json").toString()), preview);
        assertTrue(preview.contains(Paths.get("kubejs", "data", "shovel_js", "worldgen", "placed_feature", "full_vein.json").toString()), preview);
        assertTrue(preview.contains(Paths.get("kubejs", "data", "shovel_js", "neoforge", "biome_modifier", "full_vein.json").toString()), preview);
        assertTrue(preview.contains("\"size\": 9"), preview);
        assertTrue(preview.contains("\"count\": 5"), preview);
        assertTrue(preview.contains("\"absolute\": -24"), preview);
        assertTrue(preview.contains("\"absolute\": 56"), preview);
        assertTrue(preview.contains("minecraft:deepslate_iron_ore"), preview);

        OreFeatureExporter.OreFeature g = veinWithBiomes("shovel_js:full_vein", List.of("minecraft:plains"));
        g.extraTargets = f.extraTargets;
        g.veinSize = 12;
        assertNotEquals(preview, OreFeatureExporter.previewOreFeatureJson(g));
    }

    private static OreFeatureExporter.OreTarget target(String ore, String replaceable) {
        OreFeatureExporter.OreTarget t = new OreFeatureExporter.OreTarget();
        t.oreBlock = ore;
        t.replaceableTarget = replaceable;
        return t;
    }

    @Test
    void nanDiscardChanceIsRejectedInsteadOfCrashingGsonAtCommit() {
        OreFeatureExporter.OreFeature f = veinWithBiomes("shovel_js:nan_vein", List.of("minecraft:plains"));
        f.discardChanceOnAirExposure = Double.NaN;
        assertFalse(f.validate().isEmpty());

        WorldGenEntry.OreVein v = new WorldGenEntry.OreVein();
        v.oreBlock = "minecraft:iron_ore";
        v.discardChanceOnAirExposure = Double.NaN;
        assertFalse(v.validate().isEmpty());
    }

    @Test
    void worldGenEntryValidateCatchesEmptyBiomesAndBadVeinRange() {
        WorldGenEntry e = new WorldGenEntry();
        e.id = "shovel_js:broken_rule";

        WorldGenEntry.OreVein v = new WorldGenEntry.OreVein();
        v.oreBlock = "minecraft:gold_ore";
        v.minY = 80;
        v.maxY = 10;

        e.oreVeins.add(v);

        List<String> errors = e.validate();
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(msg -> msg.contains("bioma")));
        assertTrue(errors.stream().anyMatch(msg -> msg.contains("Veta #1")));
    }

    @Test
    void entryToJsonAndBackRoundTripsEveryField() {
        WorldGenEntry e = new WorldGenEntry();
        e.id = "shovel_js:round_trip";
        e.displayName = "Round Trip Rule";
        e.enabled = false;
        e.biomeIds.add("minecraft:desert");
        e.biomeIds.add("#minecraft:is_overworld");
        e.terrainPreset = WorldGenEntry.TerrainPreset.HILLY;

        WorldGenEntry.OreVein v = new WorldGenEntry.OreVein();
        v.oreBlock = "minecraft:diamond_ore";
        v.replaceableTarget = "minecraft:deepslate_ore_replaceables";
        v.veinSize = 12;
        v.veinsPerChunk = 2;
        v.minY = -80;
        v.maxY = -16;
        v.discardChanceOnAirExposure = 0.2;
        e.oreVeins.add(v);

        WorldGenEntry restored = WorldGenExporter.entryFromJson(WorldGenExporter.entryToJson(e));

        assertEquals(e.id, restored.id);
        assertEquals(e.displayName, restored.displayName);
        assertEquals(e.enabled, restored.enabled);
        assertEquals(e.biomeIds, restored.biomeIds);
        assertEquals(e.terrainPreset, restored.terrainPreset);
        assertEquals(1, restored.oreVeins.size());
        WorldGenEntry.OreVein rv = restored.oreVeins.get(0);
        assertEquals(v.oreBlock, rv.oreBlock);
        assertEquals(v.replaceableTarget, rv.replaceableTarget);
        assertEquals(v.veinSize, rv.veinSize);
        assertEquals(v.veinsPerChunk, rv.veinsPerChunk);
        assertEquals(v.minY, rv.minY);
        assertEquals(v.maxY, rv.maxY);
        assertEquals(v.discardChanceOnAirExposure, rv.discardChanceOnAirExposure, 0.0001);
    }
}
