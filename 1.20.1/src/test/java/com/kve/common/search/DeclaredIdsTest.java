package com.kve.common.search;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Ids que declara el propio proyecto: no son "ids desconocidos" para el validador. */
class DeclaredIdsTest {

    @Test
    void ownNamespacesAreDeclared() {
        assertTrue(DeclaredIds.isOwnNamespace("shovel_js:ruby"));
        assertTrue(DeclaredIds.isOwnNamespace("kubejs:ruby"));
        assertTrue(DeclaredIds.isOwnNamespace("shovel_js:ruby_block"));
        assertTrue(DeclaredIds.isOwnNamespace("#shovel_js:gems"));
        assertFalse(DeclaredIds.isOwnNamespace("minecraft:diamond"));
        assertFalse(DeclaredIds.isOwnNamespace("nonamespace"));
    }

    @Test
    void scriptDeclarationsAreFound() {
        String js = "StartupEvents.registry('item', event => {\n"
                + "  event.create('ruby').maxStackSize(16)\n"
                + "  event.create(\"mypack:sapphire\")\n"
                + "})\n"
                + "ServerEvents.recipes(event => {\n"
                + "  event.shaped('minecraft:stick', ['a'], {a: 'minecraft:oak_planks'}).id('mypack:sticks_alt')\n"
                + "})\n";
        Set<String> ids = DeclaredIds.scanScript(js);
        assertTrue(ids.contains("kubejs:ruby"), ids.toString());
        assertTrue(ids.contains("mypack:sapphire"), ids.toString());
        assertTrue(ids.contains("mypack:sticks_alt"), ids.toString());
        assertFalse(ids.contains("minecraft:stick"), ids.toString());
    }

    @Test
    void localDeclarationsCount() {
        assertTrue(DeclaredIds.isDeclared("mypack:sapphire", Set.of("mypack:sapphire")));
        assertTrue(DeclaredIds.isDeclared("#shovel_js:x", null));
        assertFalse(DeclaredIds.isDeclared("", null));
    }
}
