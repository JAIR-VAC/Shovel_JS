package com.kve.client.worldgen;

import com.kve.common.worldgen.WorldGenEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Boceto de terreno del editor de world gen: forma segun el tipo de bioma y el preset, sin registros. */
class TerrainSketchTest {

    private static double waterShare(TerrainSketch s) {
        int water = 0;
        for (int h : s.heights) if (h != TerrainSketch.VOID && h < s.profile.seaLevel()) water++;
        return water / (double) s.heights.length;
    }

    private static int range(TerrainSketch s) {
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int h : s.heights) {
            if (h == TerrainSketch.VOID) continue;
            min = Math.min(min, h);
            max = Math.max(max, h);
        }
        return max - min;
    }

    @Test
    void sameProfileAndSeedGiveTheSameSketch() {
        TerrainSketch a = TerrainSketch.build(TerrainSketch.plains(), 42);
        TerrainSketch b = TerrainSketch.build(TerrainSketch.plains(), 42);
        assertArrayEquals(a.heights, b.heights);
        assertArrayEquals(a.colors, b.colors);
    }

    @Test
    void biomeKindsLookDifferent() {
        TerrainSketch ocean = TerrainSketch.build(TerrainSketch.profileFor(null, "minecraft:ocean", null), 1);
        TerrainSketch plains = TerrainSketch.build(TerrainSketch.profileFor(null, "minecraft:plains", null), 1);
        TerrainSketch peaks = TerrainSketch.build(TerrainSketch.profileFor(null, "minecraft:jagged_peaks", null), 1);
        assertTrue(waterShare(ocean) > 0.9, "un oceano es casi todo agua");
        assertTrue(waterShare(plains) < 0.1, "una llanura casi no tiene agua");
        assertTrue(range(peaks) > range(plains) * 3, "los picos tienen mucho mas relieve que la llanura");
    }

    @Test
    void presetsChangeTheSketch() {
        TerrainSketch.Profile hills = TerrainSketch.profileFor(null, "minecraft:windswept_hills", null);
        TerrainSketch normal = TerrainSketch.build(hills, 3);
        TerrainSketch amplified = TerrainSketch.build(TerrainSketch.profileFor(null, "minecraft:windswept_hills", WorldGenEntry.TerrainPreset.AMPLIFIED), 3);
        TerrainSketch flat = TerrainSketch.build(TerrainSketch.profileFor(null, "minecraft:windswept_hills", WorldGenEntry.TerrainPreset.FLAT), 3);
        TerrainSketch highSea = TerrainSketch.build(TerrainSketch.profileFor(null, "minecraft:plains", WorldGenEntry.TerrainPreset.OCEANIC), 3);
        assertTrue(range(amplified) > range(normal), "amplificado tiene mas relieve");
        assertEquals(0, range(flat), "plano no tiene relieve");
        assertTrue(waterShare(highSea) > 0.9, "con el mar alto la llanura queda bajo el agua");
    }

    @Test
    void dimensionTagsGetTheirOwnLook() {
        TerrainSketch.Profile nether = TerrainSketch.profileFor(null, "#minecraft:is_nether", null);
        TerrainSketch.Profile end = TerrainSketch.profileFor(null, "#minecraft:is_end", null);
        assertTrue(nether.nether());
        assertTrue(end.end());
        TerrainSketch islands = TerrainSketch.build(end, 9);
        boolean anyVoid = false, anyLand = false;
        for (int h : islands.heights) { if (h == TerrainSketch.VOID) anyVoid = true; else anyLand = true; }
        assertTrue(anyVoid && anyLand, "el End son islas sobre el vacio");
    }
}
