package com.kve.common.code;

import com.kve.common.code.CodeEditOps.Pos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CodeEditOpsTest {

    private static List<String> lines(String... l) { return new ArrayList<>(List.of(l)); }

    @Test
    void textBetweenAndDeleteRangeAcrossLines() {
        List<String> l = lines("ServerEvents.recipes(event => {", "  event.remove({})", "})");
        assertEquals("recipes(event => {\n  event", CodeEditOps.textBetween(l, new Pos(1, 7), new Pos(0, 13)));
        Pos p = CodeEditOps.deleteRange(l, new Pos(0, 13), new Pos(1, 7));
        assertEquals(new Pos(0, 13), p);
        assertEquals(List.of("ServerEvents..remove({})", "})"), l);
    }

    @Test
    void insertMultiLineReturnsEndPosition() {
        List<String> l = lines("ab");
        Pos end = CodeEditOps.insert(l, new Pos(0, 1), "X\nYY\r\nZ");
        assertEquals(List.of("aX", "YY", "Zb"), l);
        assertEquals(new Pos(2, 1), end);
    }

    @Test
    void autoPairRules() {
        assertTrue(CodeEditOps.shouldAutoPair("event.remove", 12, '('));
        assertFalse(CodeEditOps.shouldAutoPair("foo", 0, '('), "no pair before a word");
        assertFalse(CodeEditOps.shouldAutoPair("don", 3, '\''), "apostrophe inside a word");
        assertFalse(CodeEditOps.shouldAutoPair("'abc", 4, '\''), "closing an open string");
        assertTrue(CodeEditOps.shouldAutoPair("x = ", 4, '\''));
        assertTrue(CodeEditOps.shouldOvertype("()", 1, ')'));
        assertTrue(CodeEditOps.betweenEmptyPair("f()", 2));
        assertFalse(CodeEditOps.betweenEmptyPair("f(a)", 2));
    }

    @Test
    void matchingBracketSkipsStringsAndComments() {
        List<String> l = lines("event.shaped('x', [", "  ' ) ', // ]", "], {", "})");
        assertEquals(new Pos(2, 0), CodeEditOps.matchingBracket(l, new Pos(0, 18)));
        assertEquals(new Pos(0, 18), CodeEditOps.matchingBracket(l, new Pos(2, 1)), "cursor just after ']' matches backwards");
        assertEquals(new Pos(0, 12), CodeEditOps.matchingBracket(l, new Pos(3, 1)), "')' closes 'shaped(' across lines");
        assertNull(CodeEditOps.matchingBracket(lines("abc"), new Pos(0, 1)));
    }

    @Test
    void blockIndentAndDedent() {
        List<String> l = lines("a", "", "  b");
        CodeEditOps.indentLines(l, 0, 2, "  ");
        assertEquals(List.of("  a", "", "    b"), l);
        int[] removed = CodeEditOps.dedentLines(l, 0, 2, 2);
        assertArrayEquals(new int[]{2, 0, 2}, removed);
        assertEquals(List.of("a", "", "  b"), l);
    }

    @Test
    void undoRedoWithTypingCoalescing() {
        CodeUndoHistory h = new CodeUndoHistory();
        h.record(new CodeUndoHistory.Snapshot("", 0, 0), true, 1000);
        h.record(new CodeUndoHistory.Snapshot("a", 0, 1), true, 1200);
        h.record(new CodeUndoHistory.Snapshot("ab", 0, 2), false, 1300);
        var s1 = h.undo(new CodeUndoHistory.Snapshot("abc", 0, 3));
        assertEquals("ab", s1.text());
        var s2 = h.undo(s1);
        assertEquals("", s2.text(), "typing 'a','b' undone as one step");
        assertNull(h.undo(s2));
        assertEquals("ab", h.redo(s2).text());
    }
}
