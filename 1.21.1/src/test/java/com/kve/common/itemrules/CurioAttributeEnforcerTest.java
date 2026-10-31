package com.kve.common.itemrules;

import com.google.gson.JsonParser;
import com.kve.common.kubejs.KubeJSItemAttrExporter;
import com.kve.common.kubejs.KubeJSItemAttrExporter.Op;
import com.kve.common.kubejs.KubeJSItemAttrExporter.SlotAttrMod;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CurioAttributeEnforcerTest {

    @Test
    void parsesCuriosComponentJson() {
        String json = "{\"modifiers\":[{\"type\":\"minecraft:generic.max_health\",\"slot\":\"ring\","
                + "\"id\":\"x:a\",\"amount\":4.0,\"operation\":\"add_value\"},"
                + "{\"type\":\"minecraft:generic.luck\",\"slot\":\"\",\"id\":\"x:b\",\"amount\":0.5,"
                + "\"operation\":\"add_multiplied_total\"}],\"show_in_tooltip\":true}";
        List<SlotAttrMod> mods = CurioAttributeEnforcer.parseModifiers(JsonParser.parseString(json), "slot");
        assertEquals(2, mods.size());
        assertEquals("minecraft:generic.max_health", mods.get(0).attrId);
        assertEquals("ring", mods.get(0).slot);
        assertEquals(4.0, mods.get(0).value);
        assertEquals(Op.ADDITION, mods.get(0).op);
        assertEquals("", mods.get(1).slot);
        assertEquals(Op.MULTIPLY_TOTAL, mods.get(1).op);
    }

    @Test
    void parsesAccessoriesComponentJson() {
        String json = "{\"modifiers\":[{\"type\":\"minecraft:generic.armor\",\"id\":\"x:c\",\"amount\":2,"
                + "\"operation\":\"add_multiplied_base\",\"slot_name\":\"necklace\",\"is_stackable\":false}]}";
        List<SlotAttrMod> mods = CurioAttributeEnforcer.parseModifiers(JsonParser.parseString(json), "slot_name");
        assertEquals(1, mods.size());
        assertEquals("necklace", mods.get(0).slot);
        assertEquals(Op.MULTIPLY_BASE, mods.get(0).op);
    }

    @Test
    void slotMatchingAndClearing() {
        KubeJSItemAttrExporter.ItemEntry e = new KubeJSItemAttrExporter.ItemEntry();
        e.itemId = "x:ring";
        e.curioAttributes.add(new SlotAttrMod("minecraft:generic.max_health", 6, Op.ADDITION, "ring"));
        e.curioAttributes.add(new SlotAttrMod("minecraft:generic.luck", 1, Op.ADDITION, ""));
        e.curioRemoved.add(SlotAttrMod.key("necklace", "minecraft:generic.armor"));

        assertEquals(2, CurioAttributeEnforcer.modsForSlot(e, "ring").size());
        assertEquals(1, CurioAttributeEnforcer.modsForSlot(e, "necklace").size());
        assertEquals(Set.of("minecraft:generic.luck", "minecraft:generic.armor"),
                CurioAttributeEnforcer.attrsToClear(e, "necklace"));
        assertEquals(Set.of("minecraft:generic.max_health", "minecraft:generic.luck"),
                CurioAttributeEnforcer.attrsToClear(e, "ring"));
    }

    @Test
    void idsDifferPerSlotIndex() {
        assertNotEquals(CurioAttributeEnforcer.idFor("x:ring", "a:b", "ring", 0),
                CurioAttributeEnforcer.idFor("x:ring", "a:b", "ring", 1));
        assertEquals(CurioAttributeEnforcer.idFor("x:ring", "a:b", "ring", 0),
                CurioAttributeEnforcer.idFor("x:ring", "a:b", "ring", 0));
    }
}
