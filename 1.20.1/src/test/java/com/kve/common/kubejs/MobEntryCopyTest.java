package com.kve.common.kubejs;

import com.kve.common.kubejs.KubeJSMobExporter.AttrMod;
import com.kve.common.kubejs.KubeJSMobExporter.EquipmentBuild;
import com.kve.common.kubejs.KubeJSMobExporter.EquipmentItem;
import com.kve.common.kubejs.KubeJSMobExporter.MobEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MobEntryCopyTest {

    private MobEntry sample() {
        MobEntry e = new MobEntry();
        e.entityId = "minecraft:zombie";
        e.enabled = true;
        e.category = "undead";
        e.health = 40.0;
        e.armor = 2.0;
        e.attackDamage = 5.0;
        e.customName = "Big Zombie";
        e.customNameSet = true;
        e.customNameVisible = true;
        e.attributeMods.add(new AttrMod("minecraft:generic.max_health", 40.0, "minecraft:overworld", 1.0));
        e.equipment.add(new EquipmentItem("MAINHAND", "minecraft:iron_sword", 0.1));

        EquipmentBuild build = new EquipmentBuild();
        build.name = "Diamond Set";
        build.weight = 2.0;
        build.equipment.add(new EquipmentItem("MAINHAND", "minecraft:diamond_sword", 0.05));
        e.equipmentBuilds.add(build);
        return e;
    }

    @Test
    void copyPreservesAllFieldsIncludingLists() {
        MobEntry original = sample();
        MobEntry copy = original.copy();

        assertEquals(original.entityId, copy.entityId);
        assertEquals(original.customName, copy.customName);
        assertEquals(1, copy.attributeMods.size());
        assertEquals("minecraft:generic.max_health", copy.attributeMods.get(0).attrId);
        assertEquals(1, copy.equipment.size());
        assertEquals("minecraft:iron_sword", copy.equipment.get(0).itemId);

        assertEquals(1, copy.equipmentBuilds.size());
        assertEquals("Diamond Set", copy.equipmentBuilds.get(0).name);
        assertEquals(2.0, copy.equipmentBuilds.get(0).weight);
        assertEquals(1, copy.equipmentBuilds.get(0).equipment.size());
        assertEquals("minecraft:diamond_sword", copy.equipmentBuilds.get(0).equipment.get(0).itemId);
    }

    @Test
    void copyDoesNotShareListOrElementInstances() {
        MobEntry original = sample();
        MobEntry copy = original.copy();

        assertNotSame(original.attributeMods, copy.attributeMods);
        assertNotSame(original.equipment, copy.equipment);
        assertNotSame(original.attributeMods.get(0), copy.attributeMods.get(0));
        assertNotSame(original.equipment.get(0), copy.equipment.get(0));

        assertNotSame(original.equipmentBuilds, copy.equipmentBuilds);
        assertNotSame(original.equipmentBuilds.get(0), copy.equipmentBuilds.get(0));
        assertNotSame(original.equipmentBuilds.get(0).equipment, copy.equipmentBuilds.get(0).equipment);
        assertNotSame(original.equipmentBuilds.get(0).equipment.get(0), copy.equipmentBuilds.get(0).equipment.get(0));
    }

    @Test
    void mutatingOriginalAfterCopyDoesNotAffectCopy() {
        MobEntry original = sample();
        MobEntry copy = original.copy();

        original.attributeMods.get(0).value = 1.0;
        original.equipment.get(0).dropChance = 1.0;
        original.attributeMods.add(new AttrMod("minecraft:generic.armor", 2.0, "", 1.0));
        original.customName = "mutated";

        original.equipmentBuilds.get(0).name = "mutated";
        original.equipmentBuilds.get(0).weight = 999;
        original.equipmentBuilds.get(0).equipment.get(0).dropChance = 1.0;
        original.equipmentBuilds.add(new EquipmentBuild());

        assertEquals(40.0, copy.attributeMods.get(0).value, "mutating the original AttrMod after copy() must not affect the copy's AttrMod");
        assertEquals(0.1, copy.equipment.get(0).dropChance, "mutating the original EquipmentItem after copy() must not affect the copy's EquipmentItem");
        assertEquals(1, copy.attributeMods.size(), "adding to the original's list after copy() must not appear in the copy");
        assertEquals("Big Zombie", copy.customName);

        assertEquals("Diamond Set", copy.equipmentBuilds.get(0).name, "mutating the original build's name after copy() must not affect the copy");
        assertEquals(2.0, copy.equipmentBuilds.get(0).weight);
        assertEquals(0.05, copy.equipmentBuilds.get(0).equipment.get(0).dropChance,
                "a mutation 2 levels deep (inside a build's own equipment list) must not leak into the copy");
        assertEquals(1, copy.equipmentBuilds.size(), "adding a new build to the original after copy() must not appear in the copy");
    }
}
