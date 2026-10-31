package com.kve.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchFilterQueryTest {

    private static final String ID   = "minecraft:iron_sword";
    private static final String NAME = "Iron Sword";

    @Test
    void emptyQueryMatchesEverything() {
        assertTrue(SearchFilter.matches(ID, NAME, ""));
        assertTrue(SearchFilter.matches(ID, NAME, "   "));
        assertTrue(SearchFilter.matches(ID, NAME, null));
        assertTrue(SearchFilter.compile("").isEmpty());
    }

    @Test
    void multipleTermsAreAndedInAnyOrder() {
        assertTrue(SearchFilter.matches(ID, NAME, "iron sword"));
        assertTrue(SearchFilter.matches(ID, NAME, "sword iron"));
        assertFalse(SearchFilter.matches(ID, NAME, "iron pickaxe"));
    }

    @Test
    void separatorsAreIgnoredOnBothSides() {
        assertTrue(SearchFilter.matches(ID, NAME, "iron sword"));
        assertTrue(SearchFilter.matches(ID, NAME, "iron_sword"));
        assertTrue(SearchFilter.matches(ID, NAME, "iron-sword"));
        assertTrue(SearchFilter.matches(ID, NAME, "ironsword"));
        assertTrue(SearchFilter.matches(ID, NAME, "minecraft:iron_sword"));
    }

    @Test
    void quotedPhraseIsOneTerm() {
        assertTrue(SearchFilter.matches(ID, NAME, "\"iron sword\""));
        assertFalse(SearchFilter.matches(ID, NAME, "\"sword iron\""));
    }

    @Test
    void namespacePrefixFiltersByModAndCombinesWithText() {
        assertTrue(SearchFilter.matches(ID, NAME, "@minecraft"));
        assertTrue(SearchFilter.matches(ID, NAME, "@mine"));
        assertTrue(SearchFilter.matches(ID, NAME, "@minecraft sword"));
        assertFalse(SearchFilter.matches("iceandfire:dragon_sword", "Dragon Sword", "@minecraft"));
        assertFalse(SearchFilter.matches(ID, NAME, "@sword"));
    }

    @Test
    void minusExcludes() {
        assertTrue(SearchFilter.matches(ID, NAME, "sword -wooden"));
        assertFalse(SearchFilter.matches("minecraft:wooden_sword", "Wooden Sword", "sword -wooden"));
        assertFalse(SearchFilter.matches(ID, NAME, "-iron"));
        assertFalse(SearchFilter.matches(ID, NAME, "-@minecraft"));
    }

    @Test
    void tagTermDegradesToText() {
        assertTrue(SearchFilter.matches(ID, NAME, "#sword"));
        assertEquals(SearchFilter.Kind.TAG, SearchFilter.tokenize("#minecraft:swords").get(0).kind());
        assertEquals("minecraft:swords", SearchFilter.tokenize("#minecraft:swords").get(0).raw());
    }

    @Test
    void punctuationOnlyQueriesDoNotEmptyTheList() {
        assertTrue(SearchFilter.matches(ID, NAME, "@"));
        assertTrue(SearchFilter.matches(ID, NAME, "#"));
        assertTrue(SearchFilter.matches(ID, NAME, "-"));
        assertTrue(SearchFilter.matches(ID, NAME, "_"));
        assertTrue(SearchFilter.matches(ID, NAME, "::"));
    }

    @Test
    void nullsAreSafe() {
        assertTrue(SearchFilter.matches(ID, null, "iron"));
        assertFalse(SearchFilter.matches(null, null, "iron"));
        assertTrue(SearchFilter.matches(null, NAME, "iron"));
    }

    @Test
    void caseIsIgnored() {
        assertTrue(SearchFilter.matches(ID, NAME, "IRON"));
        assertTrue(SearchFilter.matches(ID, NAME, "IrOn SwOrD"));
    }

    @Test
    void worksOnPlainLabelsToo() {
        assertTrue(SearchFilter.matches("Remove recipe (chest)", "chest"));
        assertTrue(SearchFilter.matches("Remove recipe (chest)", "remove chest"));
    }

    @Test
    void compiledQueryIsCachedPerRawText() {
        SearchFilter.Query a = SearchFilter.compile("iron sword");
        SearchFilter.Query b = SearchFilter.compile("iron sword");
        assertSame(a, b, "el refiltrado llama compile() una vez por entrada - no debe re-parsear");
        assertEquals(2, a.tokens().size());
    }

    @Test
    void scoringPutsExactAndPrefixHitsFirst() {
        SearchFilter.Token sword = SearchFilter.tokenize("sword").get(0);
        int exact    = SearchFilter.scoreToken("minecraft:sword", "Sword", sword);
        int prefix   = SearchFilter.scoreToken("minecraft:sword_of_doom", "Sword of Doom", sword);
        int contains = SearchFilter.scoreToken("minecraft:netherite_sword", "Netherite Sword", sword);
        assertTrue(exact > prefix, "exacto debe rankear sobre prefijo");
        assertTrue(prefix > contains, "prefijo debe rankear sobre substring interno");
    }

    @Test
    void normalizedHelpersAgreeWithMatching() {
        assertTrue(SearchFilter.containsNormalized("minecraft:iron_sword", "ironsword"));
        assertTrue(SearchFilter.startsWithNormalized("iron_sword", "ironsw"));
        assertFalse(SearchFilter.startsWithNormalized("wooden_sword", "sword"));
        assertTrue(SearchFilter.equalsNormalized("iron_sword", "ironsword"));
        assertFalse(SearchFilter.equalsNormalized("iron_sword_x", "ironsword"));
    }
}
