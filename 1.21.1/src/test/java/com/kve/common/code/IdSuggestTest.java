package com.kve.common.code;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IdSuggestTest {
    private static final List<String> KNOWN = List.of(
            "minecraft:diamond_sword", "minecraft:diamond", "goety:death_scythe", "goety:dark_altar");

    @Test
    void suggestsCloseIdInSameNamespace() {
        assertEquals("minecraft:diamond_sword", IdSuggest.nearest("minecraft:diamon_sword", KNOWN));
        assertEquals("goety:death_scythe", IdSuggest.nearest("goety:death_sythe", KNOWN));
    }

    @Test
    void noSuggestionForUnrelatedOrOtherNamespace() {
        assertNull(IdSuggest.nearest("goety:craft", KNOWN));
        assertNull(IdSuggest.nearest("other:diamond", KNOWN));
    }
}
