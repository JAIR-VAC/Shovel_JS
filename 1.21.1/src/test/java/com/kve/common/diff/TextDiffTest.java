package com.kve.common.diff;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TextDiffTest {

    @Test
    void identicalTextsProduceOnlyEqualLines() {
        String script = "line1\nline2\nline3";
        List<TextDiff.DiffLine> diff = TextDiff.diffLines(script, script);
        assertTrue(diff.stream().allMatch(l -> l.type() == TextDiff.LineType.EQUAL));
        assertEquals(3, diff.size());
        assertTrue(TextDiff.equalLines(script, script));
    }

    @Test
    void nullOldTreatsEveryLineAsAdded() {
        List<TextDiff.DiffLine> diff = TextDiff.diffLines(null, "a\nb");
        assertEquals(2, diff.size());
        assertTrue(diff.stream().allMatch(l -> l.type() == TextDiff.LineType.ADDED));
        assertFalse(TextDiff.equalLines(null, "a\nb"));
    }

    @Test
    void nullNewTreatsEveryLineAsRemoved() {
        List<TextDiff.DiffLine> diff = TextDiff.diffLines("a\nb", null);
        assertEquals(2, diff.size());
        assertTrue(diff.stream().allMatch(l -> l.type() == TextDiff.LineType.REMOVED));
    }

    @Test
    void detectsASingleChangedLineInTheMiddle() {
        String oldScript = "event.shaped('shovel_js:x')\n  .pattern(['AAA','ABA','AAA'])\n  .key('A', 'minecraft:stone')";
        String newScript = "event.shaped('shovel_js:x')\n  .pattern(['AAA','ABA','AAA'])\n  .key('A', 'minecraft:cobblestone')";

        List<TextDiff.DiffLine> diff = TextDiff.diffLines(oldScript, newScript);
        int[] counts = TextDiff.countChanges(diff);

        assertEquals(1, counts[0], "one added line");
        assertEquals(1, counts[1], "one removed line");
        assertFalse(TextDiff.equalLines(oldScript, newScript));

        long equalCount = diff.stream().filter(l -> l.type() == TextDiff.LineType.EQUAL).count();
        assertEquals(2, equalCount, "the two unchanged pattern lines should stay EQUAL, not get flagged");
    }

    @Test
    void toUnifiedTextUsesGitStylePrefixes() {
        List<TextDiff.DiffLine> diff = TextDiff.diffLines("old\nsame", "new\nsame");
        String text = TextDiff.toUnifiedText(diff);
        assertTrue(text.contains("- old"));
        assertTrue(text.contains("+ new"));
        assertTrue(text.contains("  same"));
    }

    @Test
    void emptyToEmptyIsEqualAndEmpty() {
        assertTrue(TextDiff.equalLines("", null));
        assertTrue(TextDiff.diffLines("", "").isEmpty());
    }
}
