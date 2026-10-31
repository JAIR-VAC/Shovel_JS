package com.kve.common.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecentListTest {

    @Test
    void newestFirstNoDuplicatesCapped() {
        RecentList r = new RecentList(3);
        r.touch("a"); r.touch("b"); r.touch("c"); r.touch("a"); r.touch("d");
        assertEquals(List.of("d", "a", "c"), r.items());
    }

    @Test
    void recentFirstKeepsOriginalOrderForTheRest() {
        RecentList r = new RecentList(4);
        r.touch("z"); r.touch("x");
        List<String> sorted = r.recentFirst(List.of("w", "x", "y", "z"), s -> s);
        assertEquals(List.of("x", "z", "w", "y"), sorted);
    }

    @Test
    void setAllIgnoresBlanksAndDuplicates() {
        RecentList r = new RecentList(2);
        r.setAll(List.of("a", "", "a", "b", "c"));
        assertEquals(List.of("a", "b"), r.items());
        assertEquals(-1, r.rank("c"));
    }
}
