package com.kve.common.kubejs;

import com.kve.common.kubejs.KubeJSItemAttrExporter.ItemEntry;
import com.kve.common.kubejs.KubeJSItemAttrExporter.Op;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Forma del script que emite {@link KubeJSItemAttrExporter#previewItemJS()}
 * (mismo appendItemEntry que writeScript): editar un atributo que el item ya
 * tiene tiene que REEMPLAZAR (remove antes del add), no acumular - KubeJS
 * addAttribute() es un Multimap#put (ItemKJS, rama 2001).
 */
class KubeJSItemAttrExporterScriptTest {

    private static final String RM_ARMOR =
            "item.getAttributes('minecraft:generic.armor').forEach(a => item.removeAttribute('minecraft:generic.armor', a.id))";

    private static int count(String s, String sub) {
        int c = 0, i = 0;
        while ((i = s.indexOf(sub, i)) >= 0) { c++; i += sub.length(); }
        return c;
    }

    @Test
    void editedArmorRemovesExistingModifierBeforeAdding() {
        ItemEntry e = new ItemEntry();
        e.itemId = "minecraft:diamond_chestplate";
        e.armor = 10.0;
        String js = KubeJSItemAttrExporter.previewItemJS(e);
        int rm = js.indexOf(RM_ARMOR), add = js.indexOf("item.addAttribute(");
        assertTrue(rm >= 0 && add > rm, js);
        assertEquals(1, count(js, RM_ARMOR));
        assertEquals(1, count(js, "item.addAttribute("));
    }

    @Test
    void attackStatsKeepFixedVanillaUuidAndReplace() {
        ItemEntry e = new ItemEntry();
        e.itemId = "minecraft:stone_sword";
        e.attackDamage = 5.0;
        e.attackSpeed = -2.4;
        String js = KubeJSItemAttrExporter.previewItemJS(e);
        assertTrue(js.contains("'CB3F55D3-645C-4F38-A497-9C13A33DB5CF'"));
        assertTrue(js.contains("'FA233E1C-4180-4865-B01B-BCCE9785ACA3'"));
        assertEquals(2, count(js, ".forEach(a => item.removeAttribute("));
    }

    @Test
    void addModeKeepsOriginalModifierAndNeverReusesVanillaUuid() {
        ItemEntry e = new ItemEntry();
        e.itemId = "minecraft:iron_sword";
        e.attackDamage = 2.0;
        e.setAttribute("forge:entity_reach", 1.0, Op.ADDITION);
        e.addModeAttributes.add("minecraft:generic.attack_damage");
        e.addModeAttributes.add("forge:entity_reach");
        String js = KubeJSItemAttrExporter.previewItemJS(e);
        assertEquals(0, count(js, ".forEach(a => item.removeAttribute("), js);
        assertFalse(js.contains("'CB3F55D3-645C-4F38-A497-9C13A33DB5CF'"), js);
        assertEquals(2, count(js, "item.addAttribute("));
    }

    @Test
    void copyKeepsAddModeAttributes() {
        ItemEntry e = new ItemEntry();
        e.addModeAttributes.add("forge:entity_reach");
        ItemEntry c = e.copy();
        assertTrue(c.isAddMode("forge:entity_reach"));
        assertNotSame(e.addModeAttributes, c.addModeAttributes);
    }

    @Test
    void removedDefaultAttributeEmitsOnlyRemove() {
        ItemEntry e = new ItemEntry();
        e.itemId = "minecraft:netherite_chestplate";
        e.removedAttributes.add("minecraft:generic.knockback_resistance");
        String js = KubeJSItemAttrExporter.previewItemJS(e);
        assertEquals(1, count(js, ".forEach(a => item.removeAttribute("));
        assertEquals(0, count(js, "item.addAttribute("));
    }

    @Test
    void explicitValueBeatsRemoval() {
        ItemEntry e = new ItemEntry();
        e.itemId = "minecraft:netherite_chestplate";
        e.removedAttributes.add("minecraft:generic.knockback_resistance");
        e.setAttribute("minecraft:generic.knockback_resistance", 0.5, Op.ADDITION);
        String js = KubeJSItemAttrExporter.previewItemJS(e);
        assertEquals(1, count(js, ".forEach(a => item.removeAttribute("));
        assertEquals(1, count(js, "item.addAttribute("));
    }

    @Test
    void noItemClassGetsTheUnsupportedWarningAnymore() {
        ItemEntry bow = new ItemEntry();
        bow.itemId = "minecraft:bow";
        bow.attackDamage = 9.0;
        assertNull(KubeJSItemAttrExporter.unsupportedAttributesWarning(bow));
        assertFalse(KubeJSItemAttrExporter.previewItemJS(bow).contains("// AVISO:"));
    }

    @Test
    void effectiveModsSkipCoreFieldOverriddenByManualExtra() {
        ItemEntry e = new ItemEntry();
        e.itemId = "minecraft:diamond_sword";
        e.attackDamage = 9.0;
        e.attackSpeed = -2.0;
        e.setAttribute("minecraft:generic.attack_damage", 20.0, Op.ADDITION);
        var mods = KubeJSItemAttrExporter.effectiveAttributeMods(e);
        assertEquals(2, mods.size());
        assertEquals("minecraft:generic.attack_speed", mods.get(0).attrId);
        assertEquals(20.0, mods.get(1).value);
    }

    @Test
    void copyKeepsRemovedAttributesIndependent() {
        ItemEntry e = new ItemEntry();
        e.itemId = "minecraft:iron_chestplate";
        e.removedAttributes.add("minecraft:generic.knockback_resistance");
        ItemEntry c = e.copy();
        assertTrue(c.removedAttributes.contains("minecraft:generic.knockback_resistance"));
        c.removedAttributes.add("x:y");
        assertEquals(1, e.removedAttributes.size());
    }

    // --- "Ocultar de visores": KubeJS 6 (1.20.1) tira "'onEvent()' is no
    // longer supported!" en cada llamada a onEvent() (LegacyCodeHandler), asi
    // que el script viejo fallaba en su primera linea y nunca oculto nada.

    @Test
    void hideViewersScriptUsesTheRealKubeJs6Api() {
        String js = KubeJSItemAttrExporter.buildHideViewersScript(java.util.List.of("minecraft:stone", "mymod:gem"));
        assertFalse(js.contains("onEvent("), js);
        assertTrue(js.contains("JEIEvents.hideItems(event =>"), js);
        assertTrue(js.contains("REIEvents.hide('item', event =>"), js);
        assertEquals(4, count(js, "event.hide('"), "2 items x 2 visores");
    }

    @Test
    void hideViewersScriptGuardsAgainstMissingViewerMods() {
        String js = KubeJSItemAttrExporter.buildHideViewersScript(java.util.List.of("minecraft:stone"));
        assertTrue(js.contains("typeof JEIEvents !== 'undefined'"), js);
        assertTrue(js.contains("typeof REIEvents !== 'undefined'"), js);
    }

    @Test
    void hideViewersScriptWithNothingToHideRegistersNoHandlers() {
        String js = KubeJSItemAttrExporter.buildHideViewersScript(java.util.List.of());
        assertFalse(js.contains("JEIEvents"), js);
        assertFalse(js.contains("REIEvents.hide"), js);
    }
}
