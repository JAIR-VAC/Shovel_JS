package com.kve.common.worldgen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Formas de {@link OreOverrideExporter}. Los JSON de entrada son los que el
 * codec de 1.20.1 produce para {@code minecraft:ore_diamond} y
 * {@code minecraft:ore_iron_upper} (placed) y sus configured.
 */
class OreOverrideExporterTest {

    private static final String DIAMOND_PLACED = """
            {"feature":"minecraft:ore_diamond_small","placement":[
              {"type":"minecraft:count","count":7},
              {"type":"minecraft:in_square"},
              {"type":"minecraft:height_range","height":{"type":"minecraft:trapezoid",
                "min_inclusive":{"above_bottom":-80},"max_inclusive":{"above_bottom":80}}},
              {"type":"minecraft:biome"}]}""";

    private static final String DIAMOND_CONFIGURED = """
            {"type":"minecraft:ore","config":{"size":4,"discard_chance_on_air_exposure":0.5,"targets":[
              {"target":{"predicate_type":"minecraft:tag_match","tag":"minecraft:stone_ore_replaceables"},"state":{"Name":"minecraft:diamond_ore"}},
              {"target":{"predicate_type":"minecraft:tag_match","tag":"minecraft:deepslate_ore_replaceables"},"state":{"Name":"minecraft:deepslate_diamond_ore"}}]}}""";

    private static OreOverride diamond() {
        return OreOverride.fromOriginal("minecraft:ore_diamond", obj(DIAMOND_PLACED), obj(DIAMOND_CONFIGURED),
                "underground_ores", List.of("minecraft:plains", "minecraft:desert"), -64, 384);
    }

    private static JsonObject obj(String s) { return JsonParser.parseString(s).getAsJsonObject(); }

    private static JsonObject file(Map<Path, JsonObject> files, String suffix) {
        for (Map.Entry<Path, JsonObject> e : files.entrySet()) {
            if (e.getKey().toString().replace('\\', '/').endsWith(suffix)) return e.getValue();
        }
        fail("falta " + suffix + " en " + files.keySet());
        return null;
    }

    @Test
    void readsOriginalValuesWithAnchorsResolved() {
        OreOverride o = diamond();
        assertEquals(7, o.count);
        assertNull(o.rarity);
        assertEquals(-144, o.minY);
        assertEquals(16, o.maxY);
        assertEquals(OreOverride.TRAPEZOID, o.heightType);
        assertEquals(4, o.veinSize);
        assertEquals(List.of("minecraft:diamond_ore", "minecraft:deepslate_diamond_ore"), o.targetBlocks);
        assertTrue(o.isNoop());
        assertTrue(OreOverrideExporter.buildFiles(o).isEmpty(), "sin cambios no escribe nada");
    }

    @Test
    void belowTopAnchorUsesDimensionHeight() {
        assertEquals(245, OreOverride.anchorToAbsolute(obj("{\"below_top\":10}"), 0, 256));
    }

    @Test
    void modifyCountAndHeightKeepsConfiguredAndMovesBiomes() {
        OreOverride o = diamond();
        o.count = 20;
        o.minY = -64;
        o.maxY = 32;
        Map<Path, JsonObject> files = OreOverrideExporter.buildFiles(o);

        JsonObject remove = file(files, "shovel_js/neoforge/biome_modifier/ore_override/minecraft/ore_diamond_remove.json");
        assertEquals("neoforge:remove_features", remove.get("type").getAsString());
        assertEquals("neoforge:any", remove.getAsJsonObject("biomes").get("type").getAsString());
        assertEquals("minecraft:ore_diamond", remove.get("features").getAsString());
        assertEquals("underground_ores", remove.get("steps").getAsString());

        JsonObject placed = file(files, "shovel_js/worldgen/placed_feature/ore_override/minecraft/ore_diamond.json");
        assertEquals("minecraft:ore_diamond_small", placed.get("feature").getAsString(), "config sin cambios: mismo configured");
        String p = placed.toString();
        assertTrue(p.contains("\"count\":20"), p);
        assertTrue(p.contains("\"type\":\"minecraft:trapezoid\""), p);
        assertTrue(p.contains("\"min_inclusive\":{\"absolute\":-64}"), p);
        assertTrue(p.contains("\"max_inclusive\":{\"absolute\":32}"), p);
        assertTrue(p.contains("minecraft:in_square") && p.contains("minecraft:biome"), "el resto del placement se conserva: " + p);

        JsonObject add = file(files, "shovel_js/neoforge/biome_modifier/ore_override/minecraft/ore_diamond_add.json");
        assertEquals("neoforge:add_features", add.get("type").getAsString());
        assertEquals("shovel_js:ore_override/minecraft/ore_diamond", add.get("features").getAsString());
        assertEquals(2, add.getAsJsonArray("biomes").size());
        assertEquals(3, files.size(), "sin configured propio: " + files.keySet());
    }

    @Test
    void configChangeWritesOwnConfiguredCopy() {
        OreOverride o = diamond();
        o.veinSize = 12;
        o.targetBlocks.set(1, "minecraft:emerald_ore");
        Map<Path, JsonObject> files = OreOverrideExporter.buildFiles(o);
        JsonObject cf = file(files, "shovel_js/worldgen/configured_feature/ore_override/minecraft/ore_diamond.json");
        String c = cf.toString();
        assertTrue(c.contains("\"size\":12"), c);
        assertTrue(c.contains("minecraft:diamond_ore") && c.contains("minecraft:emerald_ore"), c);
        assertFalse(c.contains("deepslate_diamond_ore"), c);
        JsonObject placed = file(files, "shovel_js/worldgen/placed_feature/ore_override/minecraft/ore_diamond.json");
        assertEquals("shovel_js:ore_override/minecraft/ore_diamond", placed.get("feature").getAsString());
        assertTrue(placed.toString().contains("\"above_bottom\":-80"), "altura sin tocar queda como estaba");
    }

    @Test
    void switchingToUniformDropsTrapezoidOnlyKeys() {
        OreOverride o = OreOverride.fromOriginal("minecraft:ore_x", obj("""
                {"feature":"minecraft:ore_x","placement":[{"type":"minecraft:height_range","height":
                {"type":"minecraft:trapezoid","plateau":4,"min_inclusive":{"absolute":0},"max_inclusive":{"absolute":40}}}]}"""),
                obj(DIAMOND_CONFIGURED), "underground_ores", List.of(), -64, 384);
        o.heightType = OreOverride.UNIFORM;
        String p = file(OreOverrideExporter.buildFiles(o), "placed_feature/ore_override/minecraft/ore_x.json").toString();
        assertTrue(p.contains("minecraft:uniform"), p);
        assertFalse(p.contains("plateau"), p);
    }

    @Test
    void removeWithBiomeTagsGeneratesTag() {
        OreOverride o = diamond();
        o.mode = OreOverride.Mode.REMOVE;
        o.biomeSelectors.add("#minecraft:is_mountain");
        o.biomeSelectors.add("minecraft:plains");
        Map<Path, JsonObject> files = OreOverrideExporter.buildFiles(o);
        JsonObject remove = file(files, "ore_diamond_remove.json");
        assertEquals("#shovel_js:ore_override/minecraft/ore_diamond_remove_biomes", remove.get("biomes").getAsString());
        file(files, "shovel_js/tags/worldgen/biome/ore_override/minecraft/ore_diamond_remove_biomes.json");
        assertEquals(2, files.size(), "quitar no escribe placed/add: " + files.keySet());
    }

    @Test
    void removeEverywhereIsValidWithoutChanges() {
        OreOverride o = diamond();
        o.mode = OreOverride.Mode.REMOVE;
        assertTrue(o.validate().isEmpty(), o.validate().toString());
        assertEquals(1, OreOverrideExporter.buildFiles(o).size());
    }

    @Test
    void validationAndStateRoundTrip() {
        OreOverride o = diamond();
        o.minY = 50;
        o.maxY = 10;
        assertFalse(o.validate().isEmpty());
        o.maxY = 60;
        o.count = 999;
        assertFalse(o.validate().isEmpty());
        o.count = 3;
        assertTrue(o.validate().isEmpty(), o.validate().toString());

        OreOverride back = OreOverrideExporter.fromJson(OreOverrideExporter.toJson(o));
        assertEquals(OreOverrideExporter.preview(o), OreOverrideExporter.preview(back));
    }

    @Test
    void unsafeIdWritesNothing() {
        OreOverride o = diamond();
        o.placedFeatureId = "minecraft:../../evil";
        o.count = 1;
        assertTrue(OreOverrideExporter.buildFiles(o).isEmpty());
    }
}
