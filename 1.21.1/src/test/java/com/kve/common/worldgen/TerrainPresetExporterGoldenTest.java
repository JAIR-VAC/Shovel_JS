package com.kve.common.worldgen;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * entregable: pins the
 * exact JSON {@link TerrainPresetExporter} produces per preset, via the
 * pure {@link TerrainPresetExporter#previewPresetJson()} (no disk I/O), same
 * style as {@code WorldGenExporterGoldenTest}.
 */
class TerrainPresetExporterGoldenTest {

    private static WorldGenEntry entryWith(WorldGenEntry.TerrainPreset preset) {
        WorldGenEntry e = new WorldGenEntry();
        e.id = "shovel_js:my_rule";
        e.displayName = "My Rule";
        e.biomeIds.add("minecraft:plains");
        e.terrainPreset = preset;
        return e;
    }

    @Test
    void nullPresetProducesEmptyPreview() {
        WorldGenEntry e = entryWith(null);
        assertEquals("", TerrainPresetExporter.previewPresetJson(e));
    }

    @Test
    void flatPresetWritesFlatGeneratorLabDimension() {
        WorldGenEntry e = entryWith(WorldGenEntry.TerrainPreset.FLAT);
        String preview = TerrainPresetExporter.previewPresetJson(e);

        Path expectedPath = Paths.get("kubejs", "data", "shovel_js", "dimension", "worldgen_preview_flat_my_rule.json");
        assertTrue(preview.contains("// " + expectedPath), preview);
        assertTrue(preview.contains("\"type\": \"minecraft:overworld\""), preview);
        assertTrue(preview.contains("\"type\": \"minecraft:flat\""), preview);
        assertTrue(preview.contains("\"biome\": \"minecraft:plains\""), preview);
    }

    @Test
    void amplifiedPresetReusesVanillaAmplifiedNoiseSettings() {
        WorldGenEntry e = entryWith(WorldGenEntry.TerrainPreset.AMPLIFIED);
        String preview = TerrainPresetExporter.previewPresetJson(e);

        assertTrue(preview.contains("\"type\": \"minecraft:noise\""), preview);
        assertTrue(preview.contains("\"settings\": \"minecraft:amplified\""), preview);
    }

    @Test
    void hillyAndMountainousUseDistinctNoiseSettingsPerFix2point5() {
        WorldGenEntry hilly = entryWith(WorldGenEntry.TerrainPreset.HILLY);
        WorldGenEntry mountainous = entryWith(WorldGenEntry.TerrainPreset.MOUNTAINOUS);

        String hillyPreview = TerrainPresetExporter.previewPresetJson(hilly);
        String mountainousPreview = TerrainPresetExporter.previewPresetJson(mountainous);

        assertTrue(hillyPreview.contains("\"settings\": \"minecraft:overworld\""), hillyPreview);
        assertTrue(mountainousPreview.contains("\"settings\": \"minecraft:large_biomes\""), mountainousPreview);
        assertNotEquals(TerrainPresetExporter.labDimensionId(hilly), TerrainPresetExporter.labDimensionId(mountainous));
        assertEquals("shovel_js:worldgen_preview_hilly_my_rule", TerrainPresetExporter.labDimensionId(hilly));
        assertEquals("shovel_js:worldgen_preview_mountainous_my_rule", TerrainPresetExporter.labDimensionId(mountainous));
    }

    @Test
    void oceanicLabIsAHighSeaDimensionNotADeadBiomeTag() {
        WorldGenEntry e = entryWith(WorldGenEntry.TerrainPreset.OCEANIC);
        String preview = TerrainPresetExporter.previewPresetJson(e);

        Path expectedPath = Paths.get("kubejs", "data", "shovel_js", "dimension", "worldgen_preview_oceanic_my_rule.json");
        assertTrue(preview.contains("// " + expectedPath), preview);
        assertTrue(preview.contains("\"copia_de\": \"minecraft:overworld\""), preview);
        assertTrue(preview.contains("\"sea_level\": " + TerrainPresetExporter.OCEANIC_SEA_LEVEL), preview);
        assertFalse(preview.contains("oceanic_favored"), preview);
        assertEquals(expectedPath, TerrainPresetExporter.resolvedPath(e));
    }

    @Test
    void mainWorldTargetOverridesTheOverworldNoiseSettings() {
        WorldGenEntry e = entryWith(WorldGenEntry.TerrainPreset.AMPLIFIED);
        e.presetTarget = WorldGenEntry.PresetTarget.MAIN_WORLD;
        Path expected = Paths.get("kubejs", "data", "minecraft", "worldgen", "noise_settings", "overworld.json");
        assertEquals(expected, TerrainPresetExporter.resolvedPath(e));
        String preview = TerrainPresetExporter.previewPresetJson(e);
        assertTrue(preview.contains("// " + expected), preview);
        assertTrue(preview.contains("\"copia_de\": \"minecraft:amplified\""), preview);
        assertFalse(preview.contains("dimension\\"), "el mundo principal no crea una dimension aparte");
    }

    @Test
    void mainWorldCopiesTheRealVanillaFileAndOnlyTouchesTheSeaLevel() {
        var vanilla = TerrainPresetExporter.readVanillaNoiseSettings("overworld");
        assertNotNull(vanilla, "el pack de vanilla trae worldgen/noise_settings/overworld.json");
        assertEquals(63, vanilla.get("sea_level").getAsInt());
        assertTrue(vanilla.has("noise_router"));
        assertNotNull(TerrainPresetExporter.readVanillaNoiseSettings("amplified"));
        assertNotNull(TerrainPresetExporter.readVanillaNoiseSettings("large_biomes"));
    }

    /** Un noise_settings "de mod" (como el de Terralith): su propia superficie y aquiferos. */
    private static com.google.gson.JsonObject fakeModdedOverworld() {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.addProperty("sea_level", 63);
        o.addProperty("aquifers_enabled", true);
        com.google.gson.JsonObject surface = new com.google.gson.JsonObject();
        surface.addProperty("type", "terralith:fake_surface");
        o.add("surface_rule", surface);
        com.google.gson.JsonObject router = new com.google.gson.JsonObject();
        router.addProperty("final_density", "terralith:fake_density");
        o.add("noise_router", router);
        return o;
    }

    @Test
    void mainWorldOceanicKeepsTheModdedTerrainAndOnlyRaisesTheSea() {
        var saved = TerrainPresetExporter.effectiveNoiseSettings;
        try {
            TerrainPresetExporter.effectiveNoiseSettings = n -> fakeModdedOverworld();
            WorldGenEntry e = entryWith(WorldGenEntry.TerrainPreset.OCEANIC);
            e.presetTarget = WorldGenEntry.PresetTarget.MAIN_WORLD;
            var json = TerrainPresetExporter.buildJsonForTests(e, new java.util.ArrayList<>());
            assertNotNull(json);
            assertEquals(TerrainPresetExporter.OCEANIC_SEA_LEVEL, json.get("sea_level").getAsInt());
            assertEquals("terralith:fake_surface", json.getAsJsonObject("surface_rule").get("type").getAsString());
            assertEquals("terralith:fake_density", json.getAsJsonObject("noise_router").get("final_density").getAsString());
            assertTrue(json.get(TerrainPresetExporter.GENERATED_MARKER).getAsBoolean(), "marcado para no leerse a si mismo");
        } finally {
            TerrainPresetExporter.effectiveNoiseSettings = saved;
        }
    }

    @Test
    void mainWorldAmplifiedSwapsOnlyTheNoiseRouterAndKeepsTheModdedSurface() {
        var saved = TerrainPresetExporter.effectiveNoiseSettings;
        try {
            TerrainPresetExporter.effectiveNoiseSettings = n -> fakeModdedOverworld();
            WorldGenEntry e = entryWith(WorldGenEntry.TerrainPreset.AMPLIFIED);
            e.presetTarget = WorldGenEntry.PresetTarget.MAIN_WORLD;
            var json = TerrainPresetExporter.buildJsonForTests(e, new java.util.ArrayList<>());
            assertNotNull(json);
            var vanillaAmplified = TerrainPresetExporter.readVanillaNoiseSettings("amplified");
            assertEquals(vanillaAmplified.get("noise_router"), json.get("noise_router"), "forma del terreno de amplified");
            assertEquals("terralith:fake_surface", json.getAsJsonObject("surface_rule").get("type").getAsString(), "superficie del mod");
            assertEquals(63, json.get("sea_level").getAsInt());
        } finally {
            TerrainPresetExporter.effectiveNoiseSettings = saved;
        }
    }

    @Test
    void withoutModdedTerrainTheMainWorldFallsBackToVanilla() {
        var saved = TerrainPresetExporter.effectiveNoiseSettings;
        try {
            TerrainPresetExporter.effectiveNoiseSettings = n -> null;
            WorldGenEntry e = entryWith(WorldGenEntry.TerrainPreset.OCEANIC);
            e.presetTarget = WorldGenEntry.PresetTarget.MAIN_WORLD;
            var json = TerrainPresetExporter.buildJsonForTests(e, new java.util.ArrayList<>());
            var vanilla = TerrainPresetExporter.readVanillaNoiseSettings("overworld");
            assertEquals(vanilla.get("surface_rule"), json.get("surface_rule"));
            assertEquals(TerrainPresetExporter.OCEANIC_SEA_LEVEL, json.get("sea_level").getAsInt());
        } finally {
            TerrainPresetExporter.effectiveNoiseSettings = saved;
        }
    }

    @Test
    void flatAndHillyCannotTargetTheMainWorld() {
        for (WorldGenEntry.TerrainPreset p : new WorldGenEntry.TerrainPreset[]{WorldGenEntry.TerrainPreset.FLAT, WorldGenEntry.TerrainPreset.HILLY}) {
            WorldGenEntry e = entryWith(p);
            e.presetTarget = WorldGenEntry.PresetTarget.MAIN_WORLD;
            assertFalse(e.validate().isEmpty(), p + " en el mundo principal no tendria efecto: tiene que avisar");
            assertFalse(TerrainPresetExporter.targetsMainWorld(e));
        }
    }

    @Test
    void oceanicLegacyModifierPathIsTheOldInvalidFileSoItCanBeCleanedUp() {
        WorldGenEntry e = entryWith(WorldGenEntry.TerrainPreset.OCEANIC);
        assertEquals(Paths.get("kubejs", "data", "shovel_js", "neoforge", "biome_modifier", "oceanic_preview_my_rule.json"),
                TerrainPresetExporter.legacyOceanicModifierPath(e));
    }
}
