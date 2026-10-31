package com.kve.common.kubejs;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LootEntryConditionsTest {

    private static final String STONE = """
        {"type":"minecraft:block","pools":[{"bonus_rolls":0.0,"entries":[{"type":"minecraft:alternatives",
          "children":[{"type":"minecraft:item","conditions":[{"condition":"minecraft:match_tool","predicate":
            {"predicates":{"minecraft:enchantments":[{"enchantments":"minecraft:silk_touch","levels":{"min":1}}]}}}],
            "name":"minecraft:stone"},
          {"type":"minecraft:item","conditions":[{"condition":"minecraft:survives_explosion"}],"name":"minecraft:cobblestone"}]}],
          "rolls":1.0}]}
        """;

    @Test
    void stoneKeepsSilkTouchOnTheRightEntry() {
        var e = LootTableExporter.parseLootTableJson(LootTableExporter.TargetKind.BLOCK, LootTableExporter.TriggerEvent.BREAK,
                "minecraft:stone", JsonParser.parseString(STONE).getAsJsonObject());
        assertEquals(2, e.pools.get(0).entries.size());
        var stone = e.pools.get(0).entries.get(0);
        var cobble = e.pools.get(0).entries.get(1);
        assertEquals("minecraft:stone", stone.itemId);
        assertNotNull(stone.conditions, "la entrada de stone debe conservar match_tool");
        assertTrue(stone.conditions.toString().contains("minecraft:silk_touch"));
        assertNotNull(cobble.conditions);
        assertTrue(cobble.conditions.toString().contains("minecraft:survives_explosion"));
    }

    @Test
    void conditionsSurviveTheRewrite() {
        var e = LootTableExporter.parseLootTableJson(LootTableExporter.TargetKind.BLOCK, LootTableExporter.TriggerEvent.BREAK,
                "minecraft:stone", JsonParser.parseString(STONE).getAsJsonObject());
        JsonObject out = LootTableExporter.buildLootTableJson(e);
        String text = out.toString();
        assertTrue(text.contains("minecraft:match_tool"), text);
        assertTrue(text.contains("minecraft:silk_touch"), text);
    }

    @Test
    void laterAlternativeOnlyWhenEarlierOnesFail() {
        var e = LootTableExporter.parseLootTableJson(LootTableExporter.TargetKind.BLOCK, LootTableExporter.TriggerEvent.BREAK,
                "minecraft:stone", JsonParser.parseString(STONE).getAsJsonObject());
        var stone = e.pools.get(0).entries.get(0);
        var cobble = e.pools.get(0).entries.get(1);
        assertFalse(stone.conditions.toString().contains("minecraft:inverted"), "el primero no depende de nadie");
        assertEquals(2, cobble.conditions.size(), cobble.conditions.toString());
        JsonObject inv = cobble.conditions.get(0).getAsJsonObject();
        assertEquals("minecraft:inverted", inv.get("condition").getAsString());
        assertTrue(inv.get("term").toString().contains("minecraft:silk_touch"));
    }

    @Test
    void childAfterAnUnconditionalOneIsUnreachable() {
        String json = """
            {"pools":[{"rolls":1,"entries":[{"type":"minecraft:alternatives","children":[
              {"type":"minecraft:item","name":"minecraft:dirt"},
              {"type":"minecraft:item","name":"minecraft:diamond"}]}]}]}
            """;
        var e = LootTableExporter.parseLootTableJson(LootTableExporter.TargetKind.BLOCK, LootTableExporter.TriggerEvent.BREAK,
                "minecraft:dirt", JsonParser.parseString(json).getAsJsonObject());
        assertEquals(1, e.pools.get(0).entries.size(), "diamond nunca podia salir: no se agrega como entrada");
    }

    @Test
    void unknownPoolConditionsAreKeptNotDropped() {
        String json = """
            {"pools":[{"rolls":1,"conditions":[{"condition":"minecraft:killed_by_player"},
              {"condition":"minecraft:match_tool","predicate":{"items":"minecraft:shears"}}],
              "entries":[{"type":"minecraft:item","name":"minecraft:string"}]}]}
            """;
        var e = LootTableExporter.parseLootTableJson(LootTableExporter.TargetKind.ENTITY, LootTableExporter.TriggerEvent.DEATH,
                "minecraft:spider", JsonParser.parseString(json).getAsJsonObject());
        var pool = e.pools.get(0);
        assertEquals(LootTableExporter.Condition.KILLED_BY_PLAYER, pool.condition);
        assertNotNull(pool.extraConditions);
        assertEquals(1, pool.extraConditions.size());
        String out = LootTableExporter.buildLootTableJson(e).toString();
        assertTrue(out.contains("minecraft:killed_by_player") && out.contains("minecraft:shears"), out);
        assertNotSame(pool.extraConditions, pool.copy().extraConditions);
    }

    @Test
    void entityPropertiesThatIsNotFireIsNotTurnedIntoOnFire() {
        String json = """
            {"pools":[{"rolls":1,"conditions":[{"condition":"minecraft:entity_properties","entity":"this",
              "predicate":{"flags":{"is_baby":true}}}],"entries":[{"type":"minecraft:item","name":"minecraft:egg"}]}]}
            """;
        var e = LootTableExporter.parseLootTableJson(LootTableExporter.TargetKind.ENTITY, LootTableExporter.TriggerEvent.DEATH,
                "minecraft:chicken", JsonParser.parseString(json).getAsJsonObject());
        assertEquals(LootTableExporter.Condition.NONE, e.pools.get(0).condition);
        assertTrue(e.pools.get(0).extraConditions.toString().contains("is_baby"));
    }

    @Test
    void oldStateWithoutConditionsStillLoads() {
        var e = new LootTableExporter.LootEntryData();
        assertNull(e.conditions);
        assertNull(e.copy().conditions);
    }
}
