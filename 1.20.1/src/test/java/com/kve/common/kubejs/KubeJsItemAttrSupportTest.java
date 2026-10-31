package com.kve.common.kubejs;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KubeJsItemAttrSupportTest {

    @Test
    void supportedClasses() {
        assertTrue(KubeJsItemAttrSupport.supportsAttributeEdits(Items.DIAMOND_SWORD));
        assertTrue(KubeJsItemAttrSupport.supportsAttributeEdits(Items.IRON_PICKAXE));
        assertTrue(KubeJsItemAttrSupport.supportsAttributeEdits(Items.IRON_AXE));
        assertTrue(KubeJsItemAttrSupport.supportsAttributeEdits(Items.IRON_SHOVEL));
        assertTrue(KubeJsItemAttrSupport.supportsAttributeEdits(Items.IRON_HOE));
        assertTrue(KubeJsItemAttrSupport.supportsAttributeEdits(Items.DIAMOND_CHESTPLATE));
        assertTrue(KubeJsItemAttrSupport.supportsAttributeEdits(Items.TRIDENT));
    }

    @Test
    void unsupportedClasses() {
        assertFalse(KubeJsItemAttrSupport.supportsAttributeEdits(Items.BOW));
        assertFalse(KubeJsItemAttrSupport.supportsAttributeEdits(Items.SHIELD));
        assertFalse(KubeJsItemAttrSupport.supportsAttributeEdits(Items.STICK));
        assertFalse(KubeJsItemAttrSupport.supportsAttributeEdits(Items.APPLE));
    }

    @Test
    void byIdNeverWarnsWhenItCannotKnow() {
        assertTrue(KubeJsItemAttrSupport.supportsAttributeEdits((String) null));
        assertTrue(KubeJsItemAttrSupport.supportsAttributeEdits(""));
        assertTrue(KubeJsItemAttrSupport.supportsAttributeEdits("no es un id"));
        assertTrue(KubeJsItemAttrSupport.supportsAttributeEdits("some_mod:not_registered"));
        assertTrue(KubeJsItemAttrSupport.supportsAttributeEdits("minecraft:diamond_sword"));
        assertFalse(KubeJsItemAttrSupport.supportsAttributeEdits("minecraft:bow"));
    }

    @Test
    void defaultSlotFollowsTheItemClass() {
        assertEquals(EquipmentSlot.MAINHAND, KubeJsItemAttrSupport.defaultSlot(Items.DIAMOND_SWORD));
        assertEquals(EquipmentSlot.HEAD, KubeJsItemAttrSupport.defaultSlot(Items.IRON_HELMET));
        assertEquals(EquipmentSlot.CHEST, KubeJsItemAttrSupport.defaultSlot(Items.IRON_CHESTPLATE));
        assertEquals(EquipmentSlot.LEGS, KubeJsItemAttrSupport.defaultSlot(Items.IRON_LEGGINGS));
        assertEquals(EquipmentSlot.FEET, KubeJsItemAttrSupport.defaultSlot(Items.IRON_BOOTS));
    }
}
