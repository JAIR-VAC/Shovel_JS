package com.kve.common.kubejs;

import com.kve.common.kubejs.KubeJSBlockAttrExporter.BlockEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage for KubeJSBlockAttrExporter.BlockEntry.copy() - same
 * rationale as ItemEntryCopyTest: copy() backs the undo history
 * (pushHistory() snapshots via copy()), so a shallow/incomplete copy would
 * silently corrupt Undo instead of failing loudly.
 */
class BlockEntryCopyTest {

    private BlockEntry sample() {
        BlockEntry e = new BlockEntry();
        e.blockId = "minecraft:stone";
        e.enabled = true;
        e.category = "terrain";
        e.destroySpeed = 1.5;
        e.explosionResistance = 6.0;
        e.lightEmission = 7;
        e.friction = 0.6;
        e.requiresTool = true;
        e.requiresToolSet = true;
        return e;
    }

    @Test
    void copyPreservesAllFields() {
        BlockEntry original = sample();
        BlockEntry copy = original.copy();

        assertEquals(original.blockId, copy.blockId);
        assertEquals(original.enabled, copy.enabled);
        assertEquals(original.category, copy.category);
        assertEquals(original.destroySpeed, copy.destroySpeed);
        assertEquals(original.explosionResistance, copy.explosionResistance);
        assertEquals(original.lightEmission, copy.lightEmission);
        assertEquals(original.friction, copy.friction);
        assertEquals(original.requiresTool, copy.requiresTool);
        assertEquals(original.requiresToolSet, copy.requiresToolSet);
    }

    @Test
    void copyIsIndependentInstance() {
        BlockEntry original = sample();
        BlockEntry copy = original.copy();
        assertNotSame(original, copy);
    }

    @Test
    void mutatingOriginalAfterCopyDoesNotAffectCopy() {
        BlockEntry original = sample();
        BlockEntry copy = original.copy();

        original.destroySpeed = 999.0;
        original.requiresTool = false;
        original.category = "mutated";

        assertEquals(1.5, copy.destroySpeed,
                "mutating the original after copy() must not affect the copy");
        assertTrue(copy.requiresTool);
        assertEquals("terrain", copy.category);
    }

    @Test
    void freshEntryDefaultsMeanNoChange() {
        BlockEntry e = new BlockEntry();
        assertEquals(-1, e.destroySpeed, 0.0001);
        assertEquals(-1, e.explosionResistance, 0.0001);
        assertEquals(-1, e.lightEmission);
        assertEquals(-1, e.friction, 0.0001);
        assertFalse(e.requiresToolSet, "requiresToolSet must start false so an untouched block never gets an explicit requiredTool line");
        assertTrue(e.enabled);
    }
}
