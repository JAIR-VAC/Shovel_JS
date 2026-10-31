package com.kve.common.kubejs;

import com.kve.common.event.EventEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The five polling triggers (ENTER_BIOME, DIMENSION_CHANGE,
 * ITEM_PICKUP, XP/EFFECT, MOUNT) share a single {@code PlayerEvents.tick}
 * listener instead of registering one each, and ITEM_PICKUP walks the
 * inventory once for all its events with the target item resolved outside
 * the slot loop. The generated script is also dumped to
 * {@code build/tmp/kve_player_poll_events.js} so it can be syntax-checked
 * with {@code node --check}.
 */
class EventExporterPlayerPollTest {

    @BeforeEach
    void setUp() { EventExporter.clearAll(); }

    @AfterEach
    void tearDown() { EventExporter.clearAll(); }

    private static EventEntry base(String id, EventEntry.TriggerType type) {
        EventEntry e = new EventEntry();
        e.id = id;
        e.displayName = id;
        e.enabled = true;
        e.category = "test";
        e.trigger.type = type;
        EventEntry.Action msg = new EventEntry.Action();
        msg.type = EventEntry.ActionType.MESSAGE;
        msg.text = "test";
        e.actions.add(msg);
        return e;
    }

    private static int count(String haystack, String needle) {
        int n = 0, i = 0;
        while ((i = haystack.indexOf(needle, i)) >= 0) { n++; i += needle.length(); }
        return n;
    }

    @Test
    void allPollingTriggersShareOnePlayerTickListener() throws Exception {
        EventEntry biome = base("shovel_js:biome", EventEntry.TriggerType.ENTER_BIOME);
        biome.trigger.biomeId = "minecraft:plains";
        EventExporter.stageEvent(biome);

        EventEntry dim = base("shovel_js:dim", EventEntry.TriggerType.DIMENSION_CHANGE);
        dim.trigger.toDimension = "minecraft:the_nether";
        EventExporter.stageEvent(dim);

        EventEntry pickA = base("shovel_js:pick_a", EventEntry.TriggerType.ITEM_PICKUP);
        pickA.trigger.itemId = "minecraft:diamond";
        pickA.trigger.count = 3;
        EventExporter.stageEvent(pickA);

        EventEntry pickB = base("shovel_js:pick_b", EventEntry.TriggerType.ITEM_PICKUP);
        pickB.trigger.itemId = "#minecraft:logs";
        pickB.trigger.count = 1;
        EventExporter.stageEvent(pickB);

        EventEntry xp = base("shovel_js:xp", EventEntry.TriggerType.XP_LEVEL_REACH);
        xp.trigger.xpLevel = 10;
        EventExporter.stageEvent(xp);

        EventEntry mount = base("shovel_js:mount", EventEntry.TriggerType.MOUNT);
        EventExporter.stageEvent(mount);

        String js = EventExporter.buildEventsScript();

        Path out = Paths.get("build", "tmp", "shovel_js_player_poll_events.js");
        Files.createDirectories(out.getParent());
        Files.writeString(out, js, StandardCharsets.UTF_8);

        assertEquals(1, count(js, "PlayerEvents.tick("), "polling triggers must share a single PlayerEvents.tick listener");
        for (String fn : new String[]{"biome", "dimension", "xp_effect", "mount"}) {
            assertTrue(js.contains("function shovel_js_ptick_" + fn + "(player) {"), "missing poll function " + fn);
            assertTrue(js.contains("try { shovel_js_ptick_" + fn + "(player) }"), "poll " + fn + " not dispatched from the shared listener");
        }
        assertFalse(js.contains("shovel_js_ptick_pickup"), "ITEM_PICKUP must not poll the inventory any more");
        assertFalse(js.contains("player.inventory.items"), "no inventory walk for ITEM_PICKUP");
        assertEquals(1, count(js, "ItemEvents.pickedUp("), "all ITEM_PICKUP events share one ItemEvents.pickedUp listener");
        assertTrue(js.contains("shovel_js_stack.id === 'minecraft:diamond'"));
        assertTrue(js.contains("shovel_js_stack.hasTag('minecraft:logs')"));
    }
}
