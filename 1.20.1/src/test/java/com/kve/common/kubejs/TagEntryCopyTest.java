package com.kve.common.kubejs;

import com.kve.common.kubejs.TagExporter.RegistryType;
import com.kve.common.kubejs.TagExporter.TagEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TagEntryCopyTest {

    private TagEntry sample() {
        TagEntry e = new TagEntry();
        e.regType = RegistryType.BLOCK;
        e.tagId = "shovel_js:some_tag";
        e.enabled = false;
        e.replace = true;
        e.category = "ores";
        e.added.add("minecraft:iron_ore");
        e.added.add("minecraft:copper_ore");
        e.removed.add("minecraft:coal_ore");
        return e;
    }

    @Test
    void copyPreservesAllScalarFields() {
        TagEntry original = sample();
        TagEntry copy = original.copy();

        assertEquals(original.regType, copy.regType);
        assertEquals(original.tagId, copy.tagId);
        assertEquals(original.enabled, copy.enabled);
        assertEquals(original.replace, copy.replace);
        assertEquals(original.category, copy.category);
        assertEquals(original.added, copy.added);
        assertEquals(original.removed, copy.removed);
    }

    @Test
    void copyDoesNotShareListInstances() {
        TagEntry original = sample();
        TagEntry copy = original.copy();

        assertNotSame(original.added, copy.added);
        assertNotSame(original.removed, copy.removed);
    }

    @Test
    void mutatingOriginalAfterCopyDoesNotAffectCopy() {
        TagEntry original = sample();
        TagEntry copy = original.copy();

        List<String> copiedAddedSnapshot = List.copyOf(copy.added);
        List<String> copiedRemovedSnapshot = List.copyOf(copy.removed);

        original.added.add("minecraft:gold_ore");
        original.removed.clear();
        original.tagId = "shovel_js:mutated_after_copy";

        assertEquals(copiedAddedSnapshot, copy.added, "copy's 'added' list must be unaffected by edits to the original made after copy()");
        assertEquals(copiedRemovedSnapshot, copy.removed, "copy's 'removed' list must be unaffected by edits to the original made after copy()");
        assertEquals("shovel_js:some_tag", copy.tagId);
    }

    @Test
    void mutatingCopyAfterCreationDoesNotAffectOriginal() {
        TagEntry original = sample();
        TagEntry copy = original.copy();

        copy.added.add("minecraft:diamond_ore");
        copy.category = "changed";

        assertEquals(2, original.added.size());
        assertEquals("ores", original.category);
    }
}
