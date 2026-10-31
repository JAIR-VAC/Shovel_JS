package com.kve.common.kubejs;

import com.kve.common.event.EventEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventExporterApiAuditTest {

    @BeforeEach
    void setUp() { EventExporter.clearAll(); }

    @AfterEach
    void tearDown() { EventExporter.clearAll(); }

    private static EventEntry eventFor(EventEntry.TriggerType t) {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:all_" + t.name().toLowerCase(Locale.ROOT);
        e.trigger.type = t;
        e.trigger.commandName = "cmd_" + t.name().toLowerCase(Locale.ROOT);
        e.trigger.biomeId = "#minecraft:is_forest";
        e.trigger.toDimension = "minecraft:the_nether";
        e.trigger.blockId = "minecraft:stone";
        e.trigger.itemId = "minecraft:bread";
        e.trigger.advancementId = "minecraft:story/root";
        e.trigger.effectId = "minecraft:speed";
        e.trigger.entityTypeId = "";
        e.trigger.fluidId = "minecraft:water";
        e.trigger.entityId = "";
        e.trigger.intervalSeconds = 60;
        EventEntry.Action msg = new EventEntry.Action();
        msg.type = EventEntry.ActionType.MESSAGE;
        msg.text = "hi";
        e.actions.add(msg);
        return e;
    }

    private static EventEntry kitchenSink() {
        EventEntry e = eventFor(EventEntry.TriggerType.PLAYER_LOGIN);
        e.id = "shovel_js:kitchen_sink";
        e.actions.clear();
        for (EventEntry.ConditionType ct : EventEntry.ConditionType.values()) {
            EventEntry.Condition c = new EventEntry.Condition();
            c.type = ct;
            c.dimensionId = "minecraft:overworld";
            c.biomeId = "minecraft:plains";
            c.hasItemId = "minecraft:diamond";
            c.hasEffectId = "minecraft:speed";
            c.equippedItemId = "minecraft:diamond_sword";
            c.entityEquippedItemId = "minecraft:iron_sword";
            c.equippedSlot = EventEntry.EquipSlot.OFFHAND;
            c.advancementId = "minecraft:story/root";
            c.dateStartMd = "12-01";
            c.dateEndMd = "12-31";
            c.stageId = "s";
            c.flagId = "f";
            c.randomChance = 0.5;
            e.conditions.add(c);
        }
        for (EventEntry.ActionType at : EventEntry.ActionType.values()) {
            EventEntry.Action a = new EventEntry.Action();
            a.type = at;
            a.text = "it's \"quoted\"";
            a.title = "Title";
            a.subtitle = "Sub";
            a.itemId = "minecraft:diamond";
            a.command = "/say hi";
            a.soundId = "minecraft:entity.player.levelup";
            a.spawnEntityId = "minecraft:zombie";
            a.effectId = "minecraft:speed";
            a.stageId = "s";
            a.bossbarText = "Boss";
            a.particleId = "minecraft:flame";
            a.commandAsPlayer = "spawn";
            a.customEventId = "c";
            a.grantAdvancementId = "minecraft:story/root";
            a.removeEffectId = "minecraft:speed";
            a.lootTableId = "minecraft:chests/simple_dungeon";
            e.actions.add(a);
        }
        return e;
    }

    @Test
    void everyTriggerIsDispatchedFromARealHookAndNoNonExistentApiIsEmitted() throws IOException {
        for (EventEntry.TriggerType t : EventEntry.TriggerType.values()) {
            EventEntry e = eventFor(t);
            assertTrue(e.validateStructural().isEmpty(), t + " test entry must be structurally valid: " + e.validateStructural());
            EventExporter.stageEvent(e);
        }
        EventExporter.stageEvent(kitchenSink());

        String js = EventExporter.buildEventsScript();
        Path out = Paths.get("build", "tmp", "shovel_js_events_all.js");
        Files.createDirectories(out.getParent());
        Files.writeString(out, js, StandardCharsets.UTF_8);

        assertTrue(ScriptWriteGuard.looksWellFormed(js), "generated script must pass ScriptWriteGuard");

        for (EventEntry.TriggerType t : EventEntry.TriggerType.values()) {
            String s = "shovel_js_all_" + t.name().toLowerCase(Locale.ROOT);
            int calls = count(js, "shovel_js_act_" + s + "(");
            assertTrue(calls >= 2, t + " is never dispatched (only " + calls + " occurrence of shovel_js_act_)");
        }

        String[] forbidden = {
                "EntityEvents.interact", "EntityEvents.tame", "EntityEvents.breed", "FluidEvents.",
                "player.dimension", "player.biome", "potionEffects.get(", "potionEffects.remove(",
                "advancements.get(", "offhandItem", "event.amount", "gameMode.getName()",
                "    .spawn()", "createEntity('minecraft:lightning_bolt')", "getMoonPhase(player", "player.clearTitle", "setRespawnPosition(",
                "player.experienceLevel", "stats.get(", "event.deathMessage", "event.server.persistentData",
                "no se generan todavia", "sendSuccess(Text.of",
        };
        for (String f : forbidden) {
            assertFalse(js.contains(f), "generated script still uses non-existent KubeJS API: " + f);
        }
    }

    @Test
    void forgeBridgeTriggersAssignGlobalHandlersAndBridgeScriptRegistersForgeEvents() {
        for (EventEntry.TriggerType t : new EventEntry.TriggerType[]{
                EventEntry.TriggerType.TAMING, EventEntry.TriggerType.BREEDING, EventEntry.TriggerType.FISHING_CATCH,
                EventEntry.TriggerType.VILLAGER_TRADE, EventEntry.TriggerType.EMPTY_BUCKET, EventEntry.TriggerType.FILL_BUCKET}) {
            assertTrue(EventExporter.needsForgeBridge(t));
            EventExporter.stageEvent(eventFor(t));
        }
        String js = EventExporter.buildEventsScript();
        for (String k : new String[]{"tame", "breed", "fish", "trade", "bucket"}) {
            assertTrue(js.contains("global['shovel_js_fx_" + k + "'] = function (event) {"), "missing bridge handler " + k);
        }
        String bridge = EventExporter.FORGE_BRIDGE_SCRIPT;
        assertTrue(ScriptWriteGuard.looksWellFormed(bridge));
        assertTrue(bridge.contains("ForgeEvents.onEvent(cls"));
        for (String cls : new String[]{"entity.living.AnimalTameEvent", "entity.living.BabyEntitySpawnEvent",
                "entity.player.ItemFishedEvent", "entity.player.TradeWithVillagerEvent", "entity.player.FillBucketEvent"}) {
            assertTrue(bridge.contains("'net.neoforged.neoforge.event." + cls + "'"), "bridge missing " + cls);
        }
    }

    @Test
    void bridgeHandlersAreClearedWhenNoBridgeEventsExistSoReloadDropsStaleHandlers() {
        EventExporter.stageEvent(eventFor(EventEntry.TriggerType.PLAYER_LOGIN));
        String js = EventExporter.buildEventsScript();
        for (String k : new String[]{"tame", "breed", "fish", "trade", "bucket"}) {
            assertTrue(js.contains("global['shovel_js_fx_" + k + "'] = null"), "stale handler " + k + " not cleared");
        }
        assertFalse(EventExporter.needsForgeBridge(EventEntry.TriggerType.PLAYER_LOGIN));
    }

    @Test
    void firstJoinOnlyFiresOnceEvenWithoutOncePerPlayer() {
        EventExporter.stageEvent(eventFor(EventEntry.TriggerType.FIRST_JOIN));
        EventExporter.stageEvent(eventFor(EventEntry.TriggerType.PLAYER_LOGIN));
        String js = EventExporter.buildEventsScript();
        assertTrue(js.contains("if (!shovel_js_hasFlag(player, 'shovel_js_joined_shovel_js_all_first_join') && shovel_js_check_shovel_js_all_first_join(player)) {"));
        assertTrue(js.contains("shovel_js_setFlag(player, 'shovel_js_joined_shovel_js_all_first_join')"));
        assertFalse(js.contains("shovel_js_joined_shovel_js_all_player_login"), "PLAYER_LOGIN must keep firing on every login");
    }

    @Test
    void eachActionHasItsOwnTryCatchSoOneFailureDoesNotAbortTheRest() {
        EventExporter.stageEvent(kitchenSink());
        String js = EventExporter.buildEventsScript();
        int n = EventEntry.ActionType.values().length;
        assertEquals(n, count(js, "fallo: ' + ex) }"), "one catch per action");
        assertTrue(js.contains("accion #1 (MESSAGE) fallo"));
    }

    @Test
    void userTextWithQuotesIsEscapedInsideCommandJson() {
        EventExporter.stageEvent(kitchenSink());
        String js = EventExporter.buildEventsScript();
        assertTrue(js.contains("JSON.stringify('it\\'s \"quoted\"')") || js.contains("JSON.stringify('Title')"));
        assertTrue(js.contains("' title ' + JSON.stringify('Title')"), "TITLE must be a real /title, not chat");
        assertTrue(js.contains("' at @s run ' + 'say hi'"), "leading '/' must be stripped from RUN_COMMAND");
    }

    @Test
    void jsonRoundTripKeepsLootTableAndEntityEquippedItem() {
        EventEntry e = kitchenSink();
        EventEntry back = EventExporter.entryFromJson(EventExporter.entryToJson(e));
        EventEntry.Action loot = back.actions.stream().filter(a -> a.type == EventEntry.ActionType.APPLY_LOOT_TABLE).findFirst().orElseThrow();
        assertEquals("minecraft:chests/simple_dungeon", loot.lootTableId, "APPLY_LOOT_TABLE lost its table on save/load");
        EventEntry.Condition eq = back.conditions.stream().filter(c -> c.type == EventEntry.ConditionType.ENTITY_HAS_ITEM_EQUIPPED).findFirst().orElseThrow();
        assertEquals("minecraft:iron_sword", eq.entityEquippedItemId, "ENTITY_HAS_ITEM_EQUIPPED lost its item on save/load");

        com.google.gson.JsonObject noYMax = EventExporter.entryToJson(e);
        noYMax.getAsJsonArray("conditions").get(0).getAsJsonObject().remove("yMax");
        assertEquals(320, EventExporter.entryFromJson(noYMax).conditions.get(0).yMax, "missing yMax must default to 320 like the model");
    }

    private static int count(String hay, String needle) {
        int n = 0;
        for (int i = hay.indexOf(needle); i >= 0; i = hay.indexOf(needle, i + needle.length())) n++;
        return n;
    }
}
