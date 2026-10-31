package com.kve.client.gui;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VirtualListWidgetFilterTest {

    private record Row(String id, String name) {}

    private static final VirtualListWidget.Descriptor<Row> DESC = new VirtualListWidget.Descriptor<>() {
        @Override public String id(Row r) { return r.id(); }
        @Override public String displayName(Row r) { return r.name(); }
    };

    private static List<Row> sample() {
        return new ArrayList<>(List.of(
                new Row("minecraft:stone", "Stone"),
                new Row("minecraft:wooden_sword", "Wooden Sword"),
                new Row("minecraft:iron_sword", "Iron Sword"),
                new Row("minecraft:sword", "Debug Sword"),
                new Row("iceandfire:dragon_sword", "Dragon Sword"),
                new Row("iceandfire:dragon_bone", "Dragon Bone")));
    }

    private static VirtualListWidget<Row> widget() {
        VirtualListWidget<Row> w = new VirtualListWidget<>(DESC);
        w.setItems(sample());
        return w;
    }

    private static List<String> ids(List<Row> rows) {
        List<String> out = new ArrayList<>();
        for (Row r : rows) out.add(r.id());
        return out;
    }

    @Test
    void emptyQueryPreservesCallerOrder() {
        VirtualListWidget<Row> w = widget();
        w.filter("", null);
        assertEquals(ids(sample()), ids(w.filtered()));
    }

    @Test
    void exactHitIsRankedFirst() {
        VirtualListWidget<Row> w = widget();
        w.filter("sword", null);
        assertEquals(4, w.filtered().size());
        assertEquals("minecraft:sword", w.filtered().get(0).id(),
                "un match exacto no debe quedar sepultado bajo el orden del registry");
    }

    @Test
    void multipleTermsAreAnded() {
        VirtualListWidget<Row> w = widget();
        w.filter("iron sword", null);
        assertEquals(List.of("minecraft:iron_sword"), ids(w.filtered()));
    }

    @Test
    void termCrossingSeparatorsFallsBackInsteadOfReturningNothing() {
        VirtualListWidget<Row> w = widget();
        w.filter("iron_sword", null);
        assertEquals(List.of("minecraft:iron_sword"), ids(w.filtered()));
        w.filter("ironsword", null);
        assertEquals(List.of("minecraft:iron_sword"), ids(w.filtered()));
        w.filter("\"iron sword\"", null);
        assertEquals(List.of("minecraft:iron_sword"), ids(w.filtered()));
    }

    @Test
    void negativeTermsExclude() {
        VirtualListWidget<Row> w = widget();
        w.filter("sword -dragon", null);
        assertFalse(ids(w.filtered()).contains("iceandfire:dragon_sword"));
        assertEquals(3, w.filtered().size());
    }

    @Test
    void onlyNegativeTermsKeepEverythingElseInOriginalOrder() {
        VirtualListWidget<Row> w = widget();
        w.filter("-stone", null);
        assertEquals(List.of("minecraft:wooden_sword", "minecraft:iron_sword", "minecraft:sword",
                "iceandfire:dragon_sword", "iceandfire:dragon_bone"), ids(w.filtered()));
    }

    @Test
    void namespaceTermStaysNamespaceOnly() {
        VirtualListWidget<Row> w = widget();
        w.filter("@iceandfire", null);
        assertEquals(2, w.filtered().size());
        w.filter("@ice sword", null);
        assertEquals(List.of("iceandfire:dragon_sword"), ids(w.filtered()));
        w.filter("@minecraft", null);
        assertEquals(4, w.filtered().size(), "@mod no debe matchear por una palabra del path");
    }

    @Test
    void extraFilterIsAnIndependentPass() {
        VirtualListWidget<Row> w = widget();
        w.filter("dragon", r -> r.id().endsWith("bone"));
        assertEquals(List.of("iceandfire:dragon_bone"), ids(w.filtered()));
    }

    @Test
    void tagMatcherIsUsedWhenRegistered() {
        VirtualListWidget<Row> w = widget();
        w.setTagMatcher((row, tag) -> tag.equals("minecraft:swords") && row.id().contains("sword"));
        w.filter("#minecraft:swords", null);
        assertEquals(4, w.filtered().size());
        w.filter("#minecraft:swords -wooden", null);
        assertEquals(3, w.filtered().size());
        w.filter("#minecraft:swords iron", null);
        assertEquals(List.of("minecraft:iron_sword"), ids(w.filtered()));
    }

    @Test
    void noMatchAndEmptyListAreSafe() {
        VirtualListWidget<Row> w = widget();
        w.filter("zzzz", null);
        assertTrue(w.filtered().isEmpty());

        VirtualListWidget<Row> empty = new VirtualListWidget<>(DESC);
        empty.setItems(new ArrayList<>());
        empty.filter("x", null);
        assertTrue(empty.filtered().isEmpty());
    }

    @Test
    void hugeResultSetsSkipRankingButKeepOrder() {
        List<Row> big = new ArrayList<>();
        for (int i = 0; i < 4000; i++) big.add(new Row("mod" + (i % 20) + ":item_" + i, "Item " + i));
        VirtualListWidget<Row> w = new VirtualListWidget<>(DESC);
        w.setItems(big);
        w.filter("item", null);
        assertEquals(4000, w.filtered().size());
        assertEquals(big.get(0).id(), w.filtered().get(0).id());
        assertEquals(big.get(3999).id(), w.filtered().get(3999).id());
    }
}
