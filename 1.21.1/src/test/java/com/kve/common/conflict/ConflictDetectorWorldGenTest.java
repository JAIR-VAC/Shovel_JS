package com.kve.common.conflict;

import com.kve.common.worldgen.WorldGenEntry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ConflictDetectorWorldGenTest {

    private static WorldGenEntry rule(String id, String... removed) {
        WorldGenEntry e = new WorldGenEntry();
        e.id = id;
        e.biomeIds.add("#minecraft:is_overworld");
        e.removedFeatures.addAll(List.of(removed));
        return e;
    }

    private static WorldGenEntry.OreVein vein(String block, int size) {
        WorldGenEntry.OreVein v = new WorldGenEntry.OreVein();
        v.veinId = java.util.UUID.randomUUID().toString();
        v.oreBlock = block;
        v.veinSize = size;
        return v;
    }

    @Test
    void featureRemovedByTwoRulesIsReported() {
        var a = rule("shovel_js:a", "minecraft:ore_diamond", "minecraft:ore_iron_upper");
        var b = rule("shovel_js:b", "minecraft:ore_diamond");
        List<ConflictDetector.Conflict> c = ConflictDetector.worldGenConflicts(List.of(a, b));
        assertEquals(1, c.size());
        assertEquals(ConflictDetector.Kind.WORLDGEN_FEATURE_REMOVED_TWICE, c.get(0).kind());
        assertEquals("minecraft:ore_diamond", c.get(0).subject());
        assertEquals(List.of("shovel_js:a", "shovel_js:b"), c.get(0).keys());
    }

    @Test
    void disabledRulesDoNotConflict() {
        var a = rule("shovel_js:a", "minecraft:ore_diamond");
        var b = rule("shovel_js:b", "minecraft:ore_diamond");
        b.enabled = false;
        assertTrue(ConflictDetector.worldGenConflicts(List.of(a, b)).isEmpty());
    }

    @Test
    void identicalVeinsIgnoringIdAreReported() {
        var a = rule("shovel_js:a");
        a.oreVeins.add(vein("minecraft:iron_ore", 9));
        a.oreVeins.add(vein("minecraft:gold_ore", 9));
        a.oreVeins.add(vein("minecraft:iron_ore", 9));
        a.oreVeins.add(vein("minecraft:iron_ore", 4));
        List<ConflictDetector.Conflict> c = ConflictDetector.worldGenConflicts(List.of(a));
        assertEquals(1, c.size());
        assertEquals(ConflictDetector.Kind.WORLDGEN_VEIN_DUPLICATE, c.get(0).kind());
        assertEquals("1, 3", c.get(0).detail());
        assertEquals(Set.of(0, 2), ConflictDetector.duplicateVeinIndexes(a));
    }
}
