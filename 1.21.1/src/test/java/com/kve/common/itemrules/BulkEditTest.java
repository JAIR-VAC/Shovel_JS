package com.kve.common.itemrules;

import com.kve.common.kubejs.KubeJSItemAttrExporter.ItemEntry;
import com.kve.common.util.IdPattern;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Seleccion por patron y edicion en masa de items. */
class BulkEditTest {

    private static final List<String> IDS = List.of("minecraft:iron_ore", "minecraft:deepslate_iron_ore", "create:zinc_ore",
            "create:cogwheel", "minecraft:iron_sword", "minecraft:stick");

    @Test
    void patternsSelectByModPathTagAndExclusion() {
        Map<String, List<String>> tags = Map.of("forge:rods", List.of("minecraft:stick"));
        assertEquals(List.of("create:zinc_ore", "create:cogwheel"), IdPattern.select(IDS, "create:*", t -> List.of()));
        assertEquals(List.of("minecraft:iron_ore", "minecraft:deepslate_iron_ore", "create:zinc_ore"),
                IdPattern.select(IDS, "*_ore", t -> List.of()), "sin ':' compara solo el path");
        assertEquals(List.of("minecraft:iron_ore", "create:zinc_ore"),
                IdPattern.select(IDS, "*_ore, !*deepslate*", t -> List.of()));
        assertEquals(List.of("minecraft:iron_sword", "minecraft:stick"),
                IdPattern.select(IDS, "minecraft:iron_s?ord #forge:rods", t -> tags.getOrDefault(t, List.of())));
        assertTrue(IdPattern.select(IDS, "  ", t -> List.of()).isEmpty());
        assertTrue(IdPattern.matches("create:cogwheel", "CREATE:COG*".toLowerCase()));
        assertFalse(IdPattern.matches("create:cogwheel", "cog"), "patron sin comodin = id exacto");
    }

    @Test
    void bulkEditOnlyTouchesTheChosenFieldsAndKeepsTheRest() {
        ItemEntry existing = new ItemEntry();
        existing.itemId = "minecraft:potion";
        existing.tooltip.add("mi tooltip");
        existing.maxDamage = 10;
        BulkItemEdit edit = new BulkItemEdit(16, "RARE", null, null, null, true);
        ItemEntry out = edit.applyTo("minecraft:potion", existing);
        assertEquals(16, out.maxStackSize);
        assertEquals("RARE", out.rarity);
        assertTrue(out.hideFromRecipeViewers);
        assertEquals(List.of("mi tooltip"), out.tooltip, "se conserva lo que ya tenia");
        assertEquals(10, out.maxDamage);
        assertEquals(-1, existing.maxStackSize, "no modifica la entrada original (para poder deshacer)");
        assertEquals("minecraft:stick", edit.applyTo("minecraft:stick", null).itemId);
        assertEquals("stack 16, rareza RARE, oculto en JEI/REI", edit.describe());
    }

    @Test
    void bulkEditValidation() {
        assertTrue(new BulkItemEdit(null, null, null, null, null, null).validate().contains("No hay ningun cambio elegido"));
        assertEquals(1, new BulkItemEdit(99, null, null, null, null, null).validate().size());
        assertEquals(1, new BulkItemEdit(null, "legendary", null, null, null, null).validate().size());
        assertTrue(BulkItemEdit.hideFromViewers().validate().isEmpty());
        ItemEntry fire = new BulkItemEdit(null, null, true, null, null, null).applyTo("a:b", null);
        assertTrue(fire.fireResistant && fire.fireResistantSet);
    }
}
