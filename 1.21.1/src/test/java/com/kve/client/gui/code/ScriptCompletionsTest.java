package com.kve.client.gui.code;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Autocompletado con contexto del editor de codigo. */
class ScriptCompletionsTest {

    private static String eventAt(List<String> lines) {
        int last = lines.size() - 1;
        return ScriptCompletions.enclosingEvent(lines, last, lines.get(last).length());
    }

    @Test
    void findsTheEventWhoseCallbackIsStillOpen() {
        List<String> lines = List.of(
                "ServerEvents.recipes(event => {",
                "  event.");
        assertEquals("ServerEvents.recipes", eventAt(lines));
    }

    @Test
    void closedEventAboveDoesNotCount() {
        List<String> lines = List.of(
                "ServerEvents.recipes(event => {",
                "  event.remove({ output: 'minecraft:stick' })",
                "})",
                "ServerEvents.tags('item', event => {",
                "  event.");
        assertEquals("ServerEvents.tags", eventAt(lines));

        List<String> outside = List.of(
                "ServerEvents.recipes(event => {",
                "})",
                "event.");
        assertNull(eventAt(outside), "fuera de cualquier callback no hay evento");
    }

    @Test
    void parenthesesInsideStringsAndCommentsAreIgnored() {
        List<String> lines = List.of(
                "ServerEvents.recipes(event => {",
                "  console.log(')))') // ))) cierre falso",
                "  event.");
        assertEquals("ServerEvents.recipes", eventAt(lines));
    }

    @Test
    void membersFollowTheEvent() {
        assertTrue(ScriptCompletions.eventMembers("ServerEvents.recipes").stream().anyMatch(s -> s.startsWith("event.shaped(")));
        assertTrue(ScriptCompletions.eventMembers("ServerEvents.recipes").stream().noneMatch(s -> s.startsWith("event.removeAll(")),
                "removeAll es de tags, no de recetas");
        assertTrue(ScriptCompletions.eventMembers("ServerEvents.tags").stream().anyMatch(s -> s.startsWith("event.removeAll(")));
        assertTrue(ScriptCompletions.eventMembers("PlayerEvents.loggedIn").contains("event.player"));
        assertTrue(ScriptCompletions.eventMembers("EntityEvents.death").contains("event.entity"));
        assertTrue(ScriptCompletions.eventMembers(null).isEmpty());
        assertTrue(ScriptCompletions.eventMembers("FooEvents.bar").isEmpty());
    }

    @Test
    void addMatchingCapsPrefixesAndSkipsDuplicates() {
        List<String> out = new ArrayList<>();
        out.add("#minecraft:logs");
        ScriptCompletions.addMatching(out, List.of("minecraft:logs", "minecraft:logs_that_burn", "minecraft:planks", "c:logs/oak"), "LOGS", "#", 2);
        assertEquals(List.of("#minecraft:logs", "#minecraft:logs_that_burn", "#c:logs/oak"), out);
    }

    @Test
    void filterMatchesSubstringIgnoringCase() {
        List<String> got = ScriptCompletions.filter(List.of("event.shaped(...)", "event.shapeless(...)", "event.remove(...)"), "event.SHAPE");
        assertEquals(List.of("event.shaped(...)", "event.shapeless(...)"), got);
    }
}
