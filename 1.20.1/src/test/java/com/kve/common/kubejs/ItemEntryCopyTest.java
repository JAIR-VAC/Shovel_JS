package com.kve.common.kubejs;

import com.kve.common.kubejs.KubeJSItemAttrExporter.ItemEntry;
import com.kve.common.kubejs.KubeJSItemAttrExporter.Op;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ItemEntryCopyTest {

    private ItemEntry sample() {
        ItemEntry e = new ItemEntry();
        e.itemId = "minecraft:diamond_sword";
        e.enabled = true;
        e.category = "weapons";
        e.maxDamage = 1000;
        e.maxStackSize = 1;
        e.fireResistant = true;
        e.fireResistantSet = true;
        e.rarity = "epic";
        e.burnTime = 200;
        e.attackDamage = 7.0;
        e.attackSpeed = 1.6;
        e.armor = 0.0;
        e.armorToughness = 0.0;
        e.movementSpeed = 0.0;
        e.setAttribute("minecraft:generic.attack_damage", 7.0, Op.ADDITION);
        return e;
    }

    @Test
    void copyPreservesAllFieldsIncludingAttributeMap() {
        ItemEntry original = sample();
        ItemEntry copy = original.copy();

        assertEquals(original.itemId, copy.itemId);
        assertEquals(original.rarity, copy.rarity);
        assertEquals(original.attackDamage, copy.attackDamage);
        assertEquals(original.attributes.size(), copy.attributes.size());
        assertEquals(7.0, copy.attributes.get("minecraft:generic.attack_damage").value);
        assertEquals(Op.ADDITION, copy.attributes.get("minecraft:generic.attack_damage").op);
    }

    @Test
    void copyDoesNotShareMapOrAttrModInstances() {
        ItemEntry original = sample();
        ItemEntry copy = original.copy();

        assertNotSame(original.attributes, copy.attributes);
        assertNotSame(
                original.attributes.get("minecraft:generic.attack_damage"),
                copy.attributes.get("minecraft:generic.attack_damage"));
    }

    @Test
    void mutatingOriginalAfterCopyDoesNotAffectCopy() {
        ItemEntry original = sample();
        ItemEntry copy = original.copy();

        original.attributes.get("minecraft:generic.attack_damage").value = 999.0;
        original.setAttribute("minecraft:generic.attack_speed", -2.0, Op.MULTIPLY_TOTAL);
        original.rarity = "mutated";

        assertEquals(7.0, copy.attributes.get("minecraft:generic.attack_damage").value,
                "mutating the original AttrMod after copy() must not affect the copy's AttrMod");
        assertEquals(1, copy.attributes.size(), "adding a new attribute to the original after copy() must not appear in the copy");
        assertEquals("epic", copy.rarity);
    }
}
