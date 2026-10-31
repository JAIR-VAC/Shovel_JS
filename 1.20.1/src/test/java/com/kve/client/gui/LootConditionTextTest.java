package com.kve.client.gui;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.*;

class LootConditionTextTest {

    private static final UnaryOperator<String> TR = k -> Map.of(
            "gui.kve.loot.cond.silk_touch", "solo con Toque de seda",
            "gui.kve.loot.cond.no_silk_touch", "sin Toque de seda",
            "gui.kve.loot.cond.survives_explosion", "si sobrevive explosion",
            "gui.kve.loot.cond.killed_by_player", "matado por un jugador",
            "gui.kve.loot.cond.not", "no").getOrDefault(k, k);

    private static final String SILK = "{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"enchantments\":"
            + "[{\"enchantment\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}";

    @Test
    void readableForVanillaBlockConditions() {
        var arr = JsonParser.parseString("[" + SILK + "]").getAsJsonArray();
        assertEquals("solo con Toque de seda", LootConditionText.shortText(arr, TR));
        var explosion = JsonParser.parseString("[{\"condition\":\"minecraft:survives_explosion\"}]").getAsJsonArray();
        assertEquals("si sobrevive explosion", LootConditionText.shortText(explosion, TR));
    }

    @Test
    void invertedSilkTouchReadsAsWithout() {
        var arr = JsonParser.parseString("[{\"condition\":\"minecraft:inverted\",\"term\":" + SILK + "}]").getAsJsonArray();
        assertEquals("sin Toque de seda", LootConditionText.shortText(arr, TR));
        var other = JsonParser.parseString("[{\"condition\":\"minecraft:inverted\",\"term\":{\"condition\":\"minecraft:killed_by_player\"}}]").getAsJsonArray();
        assertEquals("no matado por un jugador", LootConditionText.shortText(other, TR));
    }

    @Test
    void unknownConditionIsShownByNameNotInvented() {
        var arr = JsonParser.parseString("[{\"condition\":\"minecraft:weather_check\"}]").getAsJsonArray();
        assertEquals("weather_check", LootConditionText.shortText(arr, TR));
    }

    @Test
    void presetsAreValidConditions() {
        for (var p : LootConditionText.PRESETS) {
            assertTrue(p.create().has("condition"), p.json());
        }
        assertEquals("sin Toque de seda", LootConditionText.shortText(
                JsonParser.parseString("[" + LootConditionText.PRESETS.get(1).json() + "]").getAsJsonArray(), TR));
    }
}
