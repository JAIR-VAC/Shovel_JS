package com.kve.common.worldgen;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class WorldGenEntryVeinIdTest {

    private static WorldGenEntry.OreVein vein(String id, String ore) {
        WorldGenEntry.OreVein v = new WorldGenEntry.OreVein();
        v.veinId = id;
        v.oreBlock = ore;
        v.deepslateOreBlock = "minecraft:deepslate_" + ore.substring(ore.indexOf(':') + 1);
        v.veinSize = 11;
        v.minY = -32;
        v.maxY = 48;
        v.heightDistribution = WorldGenEntry.OreVein.HeightDistribution.TRAPEZOID;
        return v;
    }

    @Test
    void copyKeepsVeinId() {
        WorldGenEntry.OreVein v = vein("stable-1", "minecraft:iron_ore");
        assertEquals("stable-1", v.copy().veinId);
    }

    @Test
    void copyWithNewIdChangesOnlyTheId() {
        WorldGenEntry.OreVein v = vein("stable-1", "minecraft:iron_ore");
        WorldGenEntry.OreVein c = v.copyWithNewId();
        assertNotNull(c.veinId);
        assertFalse(c.veinId.isBlank());
        assertNotEquals(v.veinId, c.veinId);
        assertEquals(v.oreBlock, c.oreBlock);
        assertEquals(v.deepslateOreBlock, c.deepslateOreBlock);
        assertEquals(v.veinSize, c.veinSize);
        assertEquals(v.minY, c.minY);
        assertEquals(v.maxY, c.maxY);
        assertEquals(v.heightDistribution, c.heightDistribution);
        assertEquals("stable-1", v.veinId, "la original no se toca");
    }

    @Test
    void duplicatedVeinsAreUniqueAmongThemselvesAndVsOriginal() {
        WorldGenEntry.OreVein a = vein("a", "minecraft:iron_ore");
        WorldGenEntry.OreVein b = vein("b", "minecraft:gold_ore");
        java.util.List<WorldGenEntry.OreVein> draft = new java.util.ArrayList<>(java.util.List.of(a.copy(), b.copy()));
        draft.replaceAll(WorldGenEntry.OreVein::copyWithNewId);
        Set<String> ids = new HashSet<>(Set.of("a", "b"));
        for (WorldGenEntry.OreVein v : draft) assertTrue(ids.add(v.veinId), "id repetido: " + v.veinId);
        assertEquals(4, ids.size());
    }
}
