package com.kve.common.code;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Busqueda/reemplazo, formateador, borradores y snippets del editor de codigo. */
class CodeToolsTest {

    private static List<String> lines(String... s) { return new ArrayList<>(List.of(s)); }

    @Test
    void findNextWrapsAroundAndIsCaseInsensitiveByDefault() {
        List<String> l = lines("foo bar", "BAR baz", "qux");
        Pattern p = TextSearch.compile("bar", TextSearch.Options.DEFAULT);
        assertEquals(new TextSearch.Match(0, 4, 7), TextSearch.findNext(l, 0, -1, p));
        assertEquals(new TextSearch.Match(1, 0, 3), TextSearch.findNext(l, 0, 4, p));
        assertEquals(new TextSearch.Match(0, 4, 7), TextSearch.findNext(l, 1, 0, p));
    }

    @Test
    void singleMatchIsFoundAgainAfterFullWrap() {
        List<String> l = lines("only one here");
        Pattern p = TextSearch.compile("one", TextSearch.Options.DEFAULT);
        assertEquals(new TextSearch.Match(0, 5, 8), TextSearch.findNext(l, 0, 5, p));
        assertEquals(new TextSearch.Match(0, 5, 8), TextSearch.findPrevious(l, 0, 5, p));
    }

    @Test
    void matchCaseAndWholeWordOptions() {
        List<String> l = lines("Stick stick sticks $stick");
        assertEquals(3, TextSearch.count(l, TextSearch.compile("stick", new TextSearch.Options(true, false, false))));
        assertEquals(2, TextSearch.count(l, TextSearch.compile("stick", new TextSearch.Options(false, true, false))));
    }

    @Test
    void findPreviousGoesBackwardsWithWrap() {
        List<String> l = lines("a x", "b x", "c");
        Pattern p = TextSearch.compile("x", TextSearch.Options.DEFAULT);
        assertEquals(new TextSearch.Match(0, 2, 3), TextSearch.findPrevious(l, 1, 2, p));
        assertEquals(new TextSearch.Match(1, 2, 3), TextSearch.findPrevious(l, 0, 2, p));
    }

    @Test
    void literalReplaceAllDoesNotInterpretDollarSigns() {
        List<String> l = lines("give('a')", "give('a') give('a')");
        TextSearch.Options o = TextSearch.Options.DEFAULT;
        int n = TextSearch.replaceAll(l, TextSearch.compile("'a'", o), "'$1'", o);
        assertEquals(3, n);
        assertEquals("give('$1')", l.get(0));
    }

    @Test
    void regexReplaceSupportsGroupsForOneAndAll() {
        TextSearch.Options o = new TextSearch.Options(true, false, true);
        Pattern p = TextSearch.compile("minecraft:(\\w+)", o);
        List<String> l = lines("x minecraft:stone y minecraft:dirt");
        TextSearch.Match m = TextSearch.findNext(l, 0, -1, p);
        int end = TextSearch.replaceOne(l, m, p, "shovel_js:$1", o);
        assertEquals("x shovel_js:stone y minecraft:dirt", l.get(0));
        assertEquals(17, end);
        assertEquals(1, TextSearch.replaceAll(l, p, "shovel_js:$1", o));
        assertEquals("x shovel_js:stone y shovel_js:dirt", l.get(0));
    }

    @Test
    void emptyRegexMatchesAreIgnored() {
        TextSearch.Options o = new TextSearch.Options(false, false, true);
        Pattern p = TextSearch.compile("z*", o);
        assertNull(TextSearch.findNext(lines("abc"), 0, -1, p));
        assertEquals(0, TextSearch.replaceAll(lines("abc"), p, "!", o));
    }

    @Test
    void reindentsByBracketDepth() {
        List<String> in = lines(
                "ServerEvents.recipes(event => {",
                "event.shaped('a', [",
                "      'AA'",
                "], {",
                "A: 'b'",
                "})   ",
                "     })");
        List<String> out = JsFormatter.reindent(in);
        assertEquals(List.of(
                "ServerEvents.recipes(event => {",
                "  event.shaped('a', [",
                "    'AA'",
                "  ], {",
                "    A: 'b'",
                "  })",
                "})"), out);
    }

    @Test
    void bracketsInsideStringsAndCommentsAreIgnored() {
        List<String> in = lines(
                "if (x) {",
                "let s = '{(['",
                "// }}}",
                "/* {",
                "   keep me */",
                "}");
        List<String> out = JsFormatter.reindent(in);
        assertEquals("  let s = '{(['", out.get(1));
        assertEquals("  // }}}", out.get(2));
        assertEquals("   keep me */", out.get(4));
        assertEquals("}", out.get(5));
    }

    @Test
    void formatterIsIdempotent() {
        List<String> once = JsFormatter.reindent(lines("a({", "b: [1,", "2]", "})"));
        assertFalse(JsFormatter.wouldChange(once));
    }

    @Test
    void draftRoundTripAndClear(@TempDir Path tmp) {
        CodeDraftStore store = new CodeDraftStore(tmp.resolve("drafts"));
        Path script = tmp.resolve("server_scripts/a.js");
        assertTrue(store.load(script).isEmpty());
        store.save(script, "line1\nline2 ñ");
        assertEquals("line1\nline2 ñ", store.load(script).orElseThrow().text());
        store.save(script, "v2");
        assertEquals("v2", store.load(script).orElseThrow().text());
        store.clear(script);
        assertTrue(store.load(script).isEmpty());
    }

    @Test
    void snippetsAreBalancedAndIndentFollowingLines() {
        for (KubeJsSnippets.Snippet s : KubeJsSnippets.ALL) {
            assertFalse(JsFormatter.wouldChange(s.lines()), "snippet mal indentado: " + s.name());
            assertTrue(List.of("server", "startup", "client").contains(s.folder()));
        }
        KubeJsSnippets.Snippet first = KubeJsSnippets.ALL.get(0);
        List<String> ind = KubeJsSnippets.indented(first, "    ");
        assertEquals(first.lines().get(0), ind.get(0));
        assertEquals("    " + first.lines().get(1), ind.get(1));
    }
}
