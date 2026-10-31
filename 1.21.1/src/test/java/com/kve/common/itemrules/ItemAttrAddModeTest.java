package com.kve.common.itemrules;

import com.kve.common.kubejs.KubeJSItemAttrExporter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ItemAttrAddModeTest {

    private static final String ARMOR = "minecraft:generic.armor";

    private static Component neoforgePlusLine(String attrId, double value) {
        var attr = BuiltInRegistries.ATTRIBUTE.get(ResourceLocation.parse(attrId));
        return Component.translatable("neoforge.modifier.plus",
                Component.translatable("neoforge.value.flat", String.valueOf(value)),
                Component.translatable(attr.getDescriptionId()));
    }

    @Test
    void replaceModeShowsTheValueAsTheItemsOwnStat() {
        List<Component> lines = new ArrayList<>(List.of(neoforgePlusLine(ARMOR, 10)));
        ItemAttrTooltipStyle.apply(lines, List.of(new KubeJSItemAttrExporter.AttrMod(ARMOR, 10, KubeJSItemAttrExporter.Op.ADDITION)), Set.of());
        Component shown = lines.get(0);
        TranslatableContents tc = (TranslatableContents) shown.getSiblings().get(0).getContents();
        assertEquals("attribute.modifier.equals.0", tc.getKey());
    }

    @Test
    void addModeKeepsTheSignedModifierLine() {
        Component line = neoforgePlusLine(ARMOR, 2);
        List<Component> lines = new ArrayList<>(List.of(line));
        ItemAttrTooltipStyle.apply(lines, List.of(new KubeJSItemAttrExporter.AttrMod(ARMOR, 2, KubeJSItemAttrExporter.Op.ADDITION)), Set.of(ARMOR));
        assertSame(line, lines.get(0), "en Sumar la linea \"+2\" queda como la dibuja NeoForge");
    }

    @Test
    void entryCopyKeepsTheModes() {
        var e = new KubeJSItemAttrExporter.ItemEntry();
        e.addModeAttributes.add(ARMOR);
        var c = e.copy();
        assertTrue(c.isAddMode(ARMOR));
        assertNotSame(e.addModeAttributes, c.addModeAttributes);
    }

    @Test
    void addModeNeverReusesTheVanillaBaseId() {
        String dmg = "minecraft:generic.attack_damage";
        assertEquals(net.minecraft.world.item.Item.BASE_ATTACK_DAMAGE_ID,
                ItemAttributeEnforcer.idFor("minecraft:diamond_sword", dmg, EquipmentSlot.MAINHAND, false));
        assertNotEquals(net.minecraft.world.item.Item.BASE_ATTACK_DAMAGE_ID,
                ItemAttributeEnforcer.idFor("minecraft:diamond_sword", dmg, EquipmentSlot.MAINHAND, true),
                "en Sumar el modifier va al lado del base vanilla: si reusara su id lo pisaria");
    }
}
