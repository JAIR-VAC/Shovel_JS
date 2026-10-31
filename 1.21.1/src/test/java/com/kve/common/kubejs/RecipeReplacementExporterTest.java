package com.kve.common.kubejs;

import com.kve.common.kubejs.RecipeReplacementExporter.Kind;
import com.kve.common.kubejs.RecipeReplacementExporter.Rule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Reglas de reemplazo global / unificacion. */
class RecipeReplacementExporterTest {

    private static Rule rule(Kind k, String match, String repl, String mod) {
        return new Rule("r1", k, match, repl, mod, List.of(), false);
    }

    @Test
    void validatesIdsTagsAndKinds() {
        assertTrue(RecipeReplacementExporter.validate(rule(Kind.INPUT, "mekanism:ingot_copper", "#forge:ingots/copper", "")).isEmpty());
        assertTrue(RecipeReplacementExporter.validate(rule(Kind.OUTPUT, "#forge:ingots/tin", "thermal:tin_ingot", "!thermal")).isEmpty());
        assertFalse(RecipeReplacementExporter.validate(rule(Kind.OUTPUT, "a:b", "#forge:ingots/tin", "")).isEmpty(), "un resultado no puede ser un tag");
        assertFalse(RecipeReplacementExporter.validate(rule(Kind.INPUT, "not an id", "a:b", "")).isEmpty());
        assertFalse(RecipeReplacementExporter.validate(rule(Kind.INPUT, "a:b", "a:b", "")).isEmpty(), "buscar == reemplazo");
        assertFalse(RecipeReplacementExporter.validate(rule(Kind.UNIFY, "a:b", "a:c", "")).isEmpty(), "unificar pide un #tag");
        assertFalse(RecipeReplacementExporter.validate(rule(Kind.INPUT, "a:b", "a:c", "Create Mod")).isEmpty(), "filtro de mod invalido");
    }

    @Test
    void emitsRealKubeJsCallsWithFilters() {
        String js = RecipeReplacementExporter.buildServerScript(List.of(
                rule(Kind.INPUT, "mekanism:ingot_copper", "#forge:ingots/copper", ""),
                new Rule("r2", Kind.OUTPUT, "#forge:ingots/tin", "thermal:tin_ingot", "create", List.of(), false),
                new Rule("r3", Kind.INPUT, "minecraft:stick", "minecraft:bamboo", "!create", List.of(), false)));
        assertTrue(js.startsWith("// Shovel JS"));
        assertTrue(js.contains("ServerEvents.recipes(event => {"));
        assertTrue(js.contains("event.replaceInput({}, 'mekanism:ingot_copper', '#forge:ingots/copper')"), js);
        assertTrue(js.contains("event.replaceOutput({ mod: 'create' }, '#forge:ingots/tin', 'thermal:tin_ingot')"), js);
        assertTrue(js.contains("event.replaceInput({ not: { mod: 'create' } }, 'minecraft:stick', 'minecraft:bamboo')"), js);
        assertEquals(3, js.split("catch \\(ex\\)", -1).length - 1, "cada regla en su propio try/catch");
    }

    @Test
    void unifyReplacesOutputsAndAcceptsTheTagForOtherMembers() {
        Rule u = new Rule("u1", Kind.UNIFY, "#forge:ingots/copper", "minecraft:copper_ingot", "",
                List.of("create:copper_ingot_x", "minecraft:copper_ingot", "mekanism:ingot_copper"), true);
        String js = RecipeReplacementExporter.buildServerScript(List.of(u));
        assertTrue(js.contains("event.replaceOutput({}, '#forge:ingots/copper', 'minecraft:copper_ingot')"), js);
        assertTrue(js.contains("event.replaceInput({}, 'mekanism:ingot_copper', '#forge:ingots/copper')"), js);
        assertFalse(js.contains("event.replaceInput({}, 'minecraft:copper_ingot'"), "el preferido no se reemplaza");

        String hide = RecipeReplacementExporter.buildClientHideScript(List.of(u));
        assertTrue(hide.contains("RecipeViewerEvents.removeEntries('item'"), hide);
        assertTrue(hide.contains("event.remove('mekanism:ingot_copper')"), hide);
        assertFalse(hide.contains("event.remove('minecraft:copper_ingot')"), "nunca se oculta el preferido");

        Rule noHide = new Rule("u2", Kind.UNIFY, "#forge:ingots/copper", "minecraft:copper_ingot", "", List.of("mekanism:ingot_copper"), false);
        assertFalse(RecipeReplacementExporter.buildClientHideScript(List.of(noHide)).contains("event.remove("));
    }

    @Test
    void invalidRulesAreSkippedAndEmptyScriptIsHarmless() {
        String js = RecipeReplacementExporter.buildServerScript(List.of(rule(Kind.OUTPUT, "a:b", "#x:y", "")));
        assertFalse(js.contains("replaceOutput"));
        assertTrue(RecipeReplacementExporter.buildServerScript(List.of()).contains("(sin reglas)"));
        assertFalse(RecipeReplacementExporter.buildServerScript(List.of()).contains("ServerEvents"));
    }

    @Test
    void quotesAreEscapedAndIdsAreUnique() {
        List<Rule> existing = List.of(new Rule("r1", Kind.INPUT, "a:b", "a:c", "", List.of(), false),
                new Rule("r2", Kind.INPUT, "a:d", "a:c", "", List.of(), false));
        assertEquals("r3", RecipeReplacementExporter.newRuleId(existing));
        assertTrue(RecipeReplacementExporter.describe(existing.get(0)).contains("a:b -> a:c"));
    }
}
