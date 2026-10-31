package com.kve.common.persist;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.kve.common.kubejs.KubeJSItemAttrExporter;
import com.kve.common.kubejs.KubeJSItemAttrExporter.FoodEffect;
import com.kve.common.kubejs.KubeJSItemAttrExporter.FoodProps;
import com.kve.common.kubejs.KubeJSItemAttrExporter.ItemEntry;
import com.kve.common.kubejs.KubeJSItemAttrExporter.Op;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class CraftEditorStateItemRoundTripTest {

    @AfterEach
    void cleanup() {
        KubeJSItemAttrExporter.clearAll();
    }

    private static JsonArray invokeSaveItems() throws Exception {
        // el estado guarda solo lo aplicado; lo cargado con loadEntry cuenta como aplicado
        KubeJSItemAttrExporter.markApplied();
        Method m = CraftEditorState.class.getDeclaredMethod("saveItems");
        m.setAccessible(true);
        return (JsonArray) m.invoke(null);
    }

    private static void invokeLoadItems(JsonObject root) throws Exception {
        Method m = CraftEditorState.class.getDeclaredMethod("loadItems", JsonObject.class);
        m.setAccessible(true);
        m.invoke(null, root);
    }

    private static ItemEntry roundTrip(String itemId) throws Exception {
        JsonArray saved = invokeSaveItems();
        KubeJSItemAttrExporter.clearAll();
        JsonObject root = new JsonObject();
        root.add("items", saved);
        invokeLoadItems(root);
        return KubeJSItemAttrExporter.getEntry(itemId);
    }

    private ItemEntry sampleWithFoodAndAttributes() {
        ItemEntry e = new ItemEntry();
        e.itemId = "minecraft:golden_apple";
        e.enabled = true;
        e.category = "food";
        e.maxStackSize = 16;
        e.rarity = "rare";
        e.armor = 1.5;
        e.armorToughness = 0.5;
        e.movementSpeed = 0.05;
        e.setAttribute("minecraft:generic.max_health", 4.0, Op.ADDITION);
        e.setAttribute("minecraft:generic.luck", 1.0, Op.MULTIPLY_TOTAL);

        FoodProps food = new FoodProps();
        food.nutrition = 6;
        food.saturationMod = 1.2f;
        food.alwaysEdible = true;
        food.fastFood = false;
        food.meat = false;
        food.effects.add(new FoodEffect("minecraft:regeneration", 100, 1, 1.0f));
        food.effects.add(new FoodEffect("minecraft:absorption", 2400, 0, 0.5f));
        e.food = food;

        return e;
    }

    @Test
    void foodPropertiesSurviveASaveLoadRoundTrip() throws Exception {
        KubeJSItemAttrExporter.loadEntry(sampleWithFoodAndAttributes());

        ItemEntry reloaded = roundTrip("minecraft:golden_apple");

        assertNotNull(reloaded, "Item vanished after round-trip.");
        assertNotNull(reloaded.food, "food properties were lost after round-trip (this is the exact bug the inline comments describe).");
        assertEquals(6, reloaded.food.nutrition);
        assertEquals(1.2f, reloaded.food.saturationMod, 0.0001f);
        assertTrue(reloaded.food.alwaysEdible);
        assertFalse(reloaded.food.fastFood);
        assertFalse(reloaded.food.meat);
        assertEquals(2, reloaded.food.effects.size());
        assertEquals("minecraft:regeneration", reloaded.food.effects.get(0).effectId);
        assertEquals(100, reloaded.food.effects.get(0).durationTicks);
        assertEquals(1, reloaded.food.effects.get(0).amplifier);
        assertEquals(1.0f, reloaded.food.effects.get(0).chance, 0.0001f);
        assertEquals("minecraft:absorption", reloaded.food.effects.get(1).effectId);
        assertEquals(0.5f, reloaded.food.effects.get(1).chance, 0.0001f);
    }

    @Test
    void attributesSurviveASaveLoadRoundTrip() throws Exception {
        KubeJSItemAttrExporter.loadEntry(sampleWithFoodAndAttributes());

        ItemEntry reloaded = roundTrip("minecraft:golden_apple");

        assertNotNull(reloaded);
        assertEquals(1.5, reloaded.armor, 0.0001);
        assertEquals(0.5, reloaded.armorToughness, 0.0001);
        assertEquals(0.05, reloaded.movementSpeed, 0.0001);
        assertEquals(2, reloaded.attributes.size());
        assertEquals(4.0, reloaded.attributes.get("minecraft:generic.max_health").value, 0.0001);
        assertEquals(Op.ADDITION, reloaded.attributes.get("minecraft:generic.max_health").op);
        assertEquals(1.0, reloaded.attributes.get("minecraft:generic.luck").value, 0.0001);
        assertEquals(Op.MULTIPLY_TOTAL, reloaded.attributes.get("minecraft:generic.luck").op);
    }

    @Test
    void itemWithNoFoodOverrideStaysNullAfterRoundTrip() throws Exception {
        ItemEntry e = new ItemEntry();
        e.itemId = "minecraft:iron_sword";
        e.setAttribute("minecraft:generic.attack_damage", 7.0, Op.ADDITION);
        KubeJSItemAttrExporter.loadEntry(e);

        ItemEntry reloaded = roundTrip("minecraft:iron_sword");

        assertNotNull(reloaded);
        assertNull(reloaded.food, "An item that never had food properties should not gain them from a round-trip.");
    }

    @Test
    void emptyFoodEffectsListSurvivesRoundTrip() throws Exception {
        ItemEntry e = new ItemEntry();
        e.itemId = "minecraft:apple";
        FoodProps food = new FoodProps();
        food.nutrition = 4;
        food.saturationMod = 0.3f;
        e.food = food;
        KubeJSItemAttrExporter.loadEntry(e);

        ItemEntry reloaded = roundTrip("minecraft:apple");

        assertNotNull(reloaded.food);
        assertTrue(reloaded.food.effects.isEmpty());
    }

    @Test
    void removedDefaultAttributesSurviveRoundTrip() throws Exception {
        ItemEntry e = new ItemEntry();
        e.itemId = "minecraft:netherite_chestplate";
        e.removedAttributes.add("minecraft:generic.knockback_resistance");
        e.removedAttributes.add("irons_spellbooks:max_mana");
        KubeJSItemAttrExporter.loadEntry(e);

        ItemEntry reloaded = roundTrip("minecraft:netherite_chestplate");

        assertNotNull(reloaded);
        assertEquals(2, reloaded.removedAttributes.size());
        assertTrue(reloaded.removedAttributes.contains("minecraft:generic.knockback_resistance"));
        assertTrue(reloaded.removedAttributes.contains("irons_spellbooks:max_mana"));
    }

    @Test
    void itemWithoutRemovedAttributesWritesNoKeyAndReloadsEmpty() throws Exception {
        ItemEntry e = new ItemEntry();
        e.itemId = "minecraft:iron_sword";
        e.setAttribute("minecraft:generic.attack_damage", 7.0, Op.ADDITION);
        KubeJSItemAttrExporter.loadEntry(e);

        JsonArray saved = invokeSaveItems();
        assertFalse(saved.get(0).getAsJsonObject().has("removedAttributes"),
                "JSON de items que nunca borraron un atributo por defecto tiene que quedar identico al de antes.");

        KubeJSItemAttrExporter.clearAll();
        JsonObject root = new JsonObject();
        root.add("items", saved);
        invokeLoadItems(root);
        assertTrue(KubeJSItemAttrExporter.getEntry("minecraft:iron_sword").removedAttributes.isEmpty());
    }

    @Test
    void combatPropsSurviveTheRoundTrip() throws Exception {
        ItemEntry e = new ItemEntry();
        e.itemId = "minecraft:diamond_sword";
        KubeJSItemAttrExporter.CombatProps c = new KubeJSItemAttrExporter.CombatProps();
        c.hitEffects.add(new FoodEffect("minecraft:poison", 100, 1, 0.3f));
        c.passiveEffects.add(new FoodEffect("minecraft:speed", 60, 0, 1.0f));
        c.lifestealPct = 12.5;
        c.igniteSeconds = 4;
        c.useCooldownTicks = 30;
        e.combat = c;
        KubeJSItemAttrExporter.loadEntry(e);

        ItemEntry reloaded = roundTrip("minecraft:diamond_sword");

        assertNotNull(reloaded.combat);
        assertEquals(1, reloaded.combat.hitEffects.size());
        assertEquals("minecraft:poison", reloaded.combat.hitEffects.get(0).effectId);
        assertEquals(0.3f, reloaded.combat.hitEffects.get(0).chance, 1e-6);
        assertEquals(1, reloaded.combat.hitEffects.get(0).amplifier);
        assertEquals("minecraft:speed", reloaded.combat.passiveEffects.get(0).effectId);
        assertEquals(12.5, reloaded.combat.lifestealPct, 1e-9);
        assertEquals(4, reloaded.combat.igniteSeconds);
        assertEquals(30, reloaded.combat.useCooldownTicks);
    }

    @Test
    void itemWithoutCombatWritesNoKeyAndReloadsNull() throws Exception {
        ItemEntry e = new ItemEntry();
        e.itemId = "minecraft:stick";
        e.maxStackSize = 16;
        KubeJSItemAttrExporter.loadEntry(e);

        JsonArray saved = invokeSaveItems();
        assertFalse(saved.get(0).getAsJsonObject().has("combat"));
        assertNull(roundTrip("minecraft:stick").combat);
    }

    @Test
    void combatPropsCopyIsDeep() {
        KubeJSItemAttrExporter.CombatProps c = new KubeJSItemAttrExporter.CombatProps();
        assertTrue(c.isEmpty());
        c.hitEffects.add(new FoodEffect("minecraft:poison", 100, 0, 1.0f));
        ItemEntry e = new ItemEntry();
        e.itemId = "minecraft:iron_sword";
        e.combat = c;

        ItemEntry copy = e.copy();
        c.hitEffects.get(0).amplifier = 5;
        c.lifestealPct = 50;

        assertFalse(copy.combat.isEmpty());
        assertEquals(0, copy.combat.hitEffects.get(0).amplifier);
        assertEquals(0, copy.combat.lifestealPct, 1e-9);
    }
}
