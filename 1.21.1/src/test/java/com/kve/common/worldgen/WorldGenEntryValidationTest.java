package com.kve.common.worldgen;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorldGenEntryValidationTest {

    private static WorldGenEntry validEntry() {
        WorldGenEntry e = new WorldGenEntry();
        e.id = "shovel_js:my_rule";
        e.biomeIds.add("minecraft:plains");
        WorldGenEntry.OreVein v = new WorldGenEntry.OreVein();
        v.oreBlock = "minecraft:iron_ore";
        e.oreVeins.add(v);
        return e;
    }

    @Test
    void validEntryHasNoErrors() {
        assertEquals(List.of(), validEntry().validate());
    }

    @Test
    void idCharsetMatchesResourceLocation() {
        assertTrue(WorldGenEntry.isValidResourceId("shovel_js:my_rule"));
        assertTrue(WorldGenEntry.isValidResourceId("my-mod.x:sub/path_1"));
        assertFalse(WorldGenEntry.isValidResourceId("shovel_js:Mi Regla"));
        assertFalse(WorldGenEntry.isValidResourceId("shovel_js:UPPER"));
        assertFalse(WorldGenEntry.isValidResourceId("shovel_js:con espacio"));
        assertFalse(WorldGenEntry.isValidResourceId("sin_namespace"));
        assertFalse(WorldGenEntry.isValidResourceId("shovel_js:"));
        assertFalse(WorldGenEntry.isValidResourceId(null));
    }

    @Test
    void badIdCharsetIsReportedByValidate() {
        WorldGenEntry e = validEntry();
        e.id = "shovel_js:Mi Regla";
        assertTrue(e.validate().stream().anyMatch(m -> m.contains("minúsculas")));
        e.id = "sin_namespace";
        assertTrue(e.validate().stream().anyMatch(m -> m.contains("namespace")));
    }

    @Test
    void veinSizeAndCountAreCappedAtVanillaLimits() {
        WorldGenEntry.OreVein v = new WorldGenEntry.OreVein();
        v.oreBlock = "minecraft:iron_ore";
        v.veinSize = WorldGenEntry.OreVein.MAX_VEIN_SIZE;
        v.veinsPerChunk = WorldGenEntry.OreVein.MAX_VEINS_PER_CHUNK;
        assertEquals(List.of(), v.validate());

        v.veinSize = WorldGenEntry.OreVein.MAX_VEIN_SIZE + 1;
        assertEquals(1, v.validate().size());
        v.veinSize = 0;
        assertEquals(1, v.validate().size());

        v.veinSize = 8;
        v.veinsPerChunk = WorldGenEntry.OreVein.MAX_VEINS_PER_CHUNK + 1;
        assertEquals(1, v.validate().size());
    }

    @Test
    void oreFeatureValidateUsesSameCaps() {
        OreFeatureExporter.OreFeature f = new OreFeatureExporter.OreFeature();
        f.id = "shovel_js:x_vein";
        f.oreBlock = "minecraft:iron_ore";
        assertEquals(List.of(), f.validate());
        f.veinSize = 65;
        assertFalse(f.validate().isEmpty());
        f.veinSize = 64;
        f.veinsPerChunk = 257;
        assertFalse(f.validate().isEmpty());
        f.veinsPerChunk = 256;
        assertEquals(List.of(), f.validate());
    }
}
