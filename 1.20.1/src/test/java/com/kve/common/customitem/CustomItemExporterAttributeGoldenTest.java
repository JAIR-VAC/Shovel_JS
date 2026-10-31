package com.kve.common.customitem;

import com.kve.common.kubejs.KubeJSItemAttrExporter.AttrMod;
import com.kve.common.kubejs.KubeJSItemAttrExporter.Op;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * "Attribute modifiers
 * completos". Same style as {@code WorldGenExporterGoldenTest} - pins the
 * exact KubeJS script {@link CustomItemExporter#previewJs()} produces for a
 * draft that has {@link CustomItemDraft#attributeModifiers} set, via the
 * read-only preview method (no disk I/O, no registry bootstrap needed since
 * {@link CustomItemExporter#previewJs()} never touches {@code
 * BuiltInRegistries} - that live check only happens in {@code
 * validateForExport}).
 */
class CustomItemExporterAttributeGoldenTest {

    @Test
    void draftWithNoAttributesOmitsModificationBlockEntirely() {
        CustomItemDraft d = new CustomItemDraft();
        d.id = "shovel_js:plain_sword";
        d.displayName = "Plain Sword";

        String preview = CustomItemExporter.previewJs(d);

        assertFalse(preview.contains("ItemEvents.modification"),
                "no attribute modifiers on the draft -> no modification block at all");
    }

    @Test
    void draftWithAttributesAppendsModificationBlockAfterRegistryBlock() {
        CustomItemDraft d = new CustomItemDraft();
        d.id = "shovel_js:ruby_sword";
        d.displayName = "Ruby Sword";
        d.maxDamage = 500;
        d.attributeModifiers.put("minecraft:generic.attack_damage",
                new AttrMod("minecraft:generic.attack_damage", 7.0, Op.ADDITION));
        d.attributeModifiers.put("minecraft:generic.movement_speed",
                new AttrMod("minecraft:generic.movement_speed", 0.1, Op.MULTIPLY_TOTAL));

        String preview = CustomItemExporter.previewJs(d);

        String expected = "// Shovel JS - Custom Item (AUTO-GENERATED)\n"
                + "// Apply with: RESTART/relaunch the world (item registration only happens at startup_scripts load)\n\n"
                + "StartupEvents.registry('item', event => {\n"
                + "  event.create('ruby_sword')\n"
                + "    .maxDamage(500)\n"
                + "})\n"
                + "\n"
                + "ItemEvents.modification(event => {\n"
                + "  event.modify('shovel_js:ruby_sword', item => {\n"
                + "    try {\n"
                + "      item.addAttribute(\n"
                + "        'minecraft:generic.attack_damage',\n"
                + "        'CB3F55D3-645C-4F38-A497-9C13A33DB5CF',\n"
                + "        'shovel_js_customitem_attr_0',\n"
                + "        7,\n"
                + "        'addition'\n"
                + "      )\n"
                + "      item.addAttribute(\n"
                + "        'minecraft:generic.movement_speed',\n"
                + "        $UUID.randomUUID().toString(),\n"
                + "        'shovel_js_customitem_attr_1',\n"
                + "        0.1000,\n"
                + "        'multiply_total'\n"
                + "      )\n"
                + "    } catch (ex) {\n"
                + "      console.error('[Shovel JS] custom item attribute modify failed for shovel_js:ruby_sword: ' + ex)\n"
                + "    }\n"
                + "  })\n"
                + "})\n";

        assertEquals(expected, preview);
    }

    @Test
    void draftAttributeModifiersRoundTripThroughStateJson() {
        CustomItemDraft d = new CustomItemDraft();
        d.id = "shovel_js:round_trip_item";
        d.displayName = "Round Trip Item";
        d.attributeModifiers.put("minecraft:generic.armor",
                new AttrMod("minecraft:generic.armor", 4.0, Op.ADDITION));
        d.attributeModifiers.put("minecraft:generic.luck",
                new AttrMod("minecraft:generic.luck", 0.25, Op.MULTIPLY_BASE));

        CustomItemDraft restored = CustomItemState.draftFromJson(CustomItemState.draftToJson(d));

        assertEquals(2, restored.attributeModifiers.size());
        AttrMod armor = restored.attributeModifiers.get("minecraft:generic.armor");
        assertNotNull(armor);
        assertEquals(4.0, armor.value, 0.0001);
        assertEquals(Op.ADDITION, armor.op);

        AttrMod luck = restored.attributeModifiers.get("minecraft:generic.luck");
        assertNotNull(luck);
        assertEquals(0.25, luck.value, 0.0001);
        assertEquals(Op.MULTIPLY_BASE, luck.op);
    }

    @Test
    void draftCopyDuplicatesAttributeModifiersIndependently() {
        CustomItemDraft d = new CustomItemDraft();
        d.id = "shovel_js:copy_source";
        d.attributeModifiers.put("minecraft:generic.max_health",
                new AttrMod("minecraft:generic.max_health", 2.0, Op.ADDITION));

        CustomItemDraft copy = d.copy();
        copy.attributeModifiers.put("minecraft:generic.armor",
                new AttrMod("minecraft:generic.armor", 1.0, Op.ADDITION));

        assertEquals(1, d.attributeModifiers.size(), "mutating the copy's map must not leak back into the original draft");
        assertEquals(2, copy.attributeModifiers.size());
    }
}
