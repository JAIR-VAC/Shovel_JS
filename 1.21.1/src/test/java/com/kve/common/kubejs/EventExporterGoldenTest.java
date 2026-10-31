package com.kve.common.kubejs;

import com.kve.common.event.EventEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EventExporterGoldenTest {

    @BeforeEach
    void setUp() {
        EventExporter.clearAll();
    }

    @AfterEach
    void tearDown() {
        EventExporter.clearAll();
    }

    private static EventEntry welcomeKitFirstJoin() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:welcome_kit";
        e.displayName = "Welcome kit";
        e.enabled = true;
        e.category = "starter";
        e.trigger.type = EventEntry.TriggerType.FIRST_JOIN;

        EventEntry.Action message = new EventEntry.Action();
        message.type = EventEntry.ActionType.MESSAGE;
        message.text = "Bienvenido al pack!";
        e.actions.add(message);

        EventEntry.Action giveItem = new EventEntry.Action();
        giveItem.type = EventEntry.ActionType.GIVE_ITEM;
        giveItem.itemId = "minecraft:bread";
        giveItem.itemCount = 8;
        e.actions.add(giveItem);

        e.limits.oncePerPlayer = true;
        return e;
    }

    private static EventEntry raid1Command() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:raid1";
        e.displayName = "Raid 1 (staff)";
        e.enabled = true;
        e.category = "staff";
        e.trigger.type = EventEntry.TriggerType.COMMAND;
        e.trigger.commandName = "raid1";

        EventEntry.Action runCommand = new EventEntry.Action();
        runCommand.type = EventEntry.ActionType.RUN_COMMAND;
        runCommand.command = "say Raid started";
        e.actions.add(runCommand);

        return e;
    }

    @Test
    void bothFixturesValidateCleanly() {
        assertEquals(List.of(), welcomeKitFirstJoin().validate());
        assertEquals(List.of(), raid1Command().validate());
    }

    @Test
    void firstJoinAndCommandExportTogether() {
        EventExporter.stageEvent(welcomeKitFirstJoin());
        EventExporter.stageEvent(raid1Command());

        String js = EventExporter.buildEventsScript();

        assertTrue(js.contains("generado por Shovel JS"), "missing generated-by header");

        assertTrue(js.contains("function shovel_js_check_shovel_js_welcome_kit(player)"));
        assertTrue(js.contains("if (shovel_js_hasFlag(player, 'shovel_js_flag_shovel_js_welcome_kit')) return false"),
                "oncePerPlayer limit should gate on a stage flag with a GameStages-optional fallback");
        assertTrue(js.contains("function shovel_js_act_shovel_js_welcome_kit(player)"));
        assertTrue(js.contains("player.tell('Bienvenido al pack!')"));
        assertTrue(js.contains("player.give(Item.of('minecraft:bread', 8))"));
        assertTrue(js.contains("function shovel_js_mark_shovel_js_welcome_kit(player)"));
        assertTrue(js.contains("shovel_js_setFlag(player, 'shovel_js_flag_shovel_js_welcome_kit')"));

        assertTrue(js.contains("function shovel_js_hasFlag(player, flag)"));
        assertTrue(js.contains("return player.stages.has(flag)"));
        assertTrue(js.contains("return player.persistentData.getBoolean('shovel_js_pdflag_' + flag)"));
        assertTrue(js.contains("function shovel_js_setFlag(player, flag)"));
        assertTrue(js.contains("player.stages.add(flag)"));
        assertTrue(js.contains("player.persistentData.putBoolean('shovel_js_pdflag_' + flag, true)"));

        assertTrue(js.contains("PlayerEvents.loggedIn(event => {"),
                "FIRST_JOIN should dispatch through PlayerEvents.loggedIn (spec section 2)");
        assertTrue(js.contains("shovel_js_check_shovel_js_welcome_kit(player)"));
        assertTrue(js.contains("shovel_js_act_shovel_js_welcome_kit(player)"));
        assertTrue(js.contains("shovel_js_mark_shovel_js_welcome_kit(player)"));

        assertTrue(js.contains("function shovel_js_act_shovel_js_raid1(player)"));
        assertTrue(js.contains("var shovel_js_serverRef = null"));
        assertTrue(js.contains("ServerEvents.loaded(event => { shovel_js_serverRef = event.server })"));
        assertTrue(js.contains("function shovel_js_server(player) {"));
        assertTrue(js.contains("shovel_js_cmd(player, 'execute as ' + shovel_js_uuid(player) + ' at @s run ' + 'say Raid started')"));
        assertFalse(js.contains("  server.runCommandSilent('say Raid started')"),
                "RUN_COMMAND must not reference the undefined 'server' local outside INTERVAL/SERVER_STARTUP");

        assertTrue(js.contains("ServerEvents.commandRegistry(event => {"),
                "COMMAND should dispatch through ServerEvents.commandRegistry (spec section 2)");
        assertTrue(js.contains("Commands.literal('shovel')"));
        assertTrue(js.contains("Commands.literal('event').requires(src => src.hasPermission(2))"),
                "COMMAND dispatch must gate on Shovel JS/staff permission (spec section 3/6)");
        assertTrue(js.contains("name === 'raid1'"));
        assertTrue(js.contains("shovel_js_check_shovel_js_raid1(player)"));

        int loggedInIdx = js.indexOf("PlayerEvents.loggedIn(event => {");
        int commandIdx = js.indexOf("ServerEvents.commandRegistry(event => {");
        assertTrue(loggedInIdx >= 0 && commandIdx >= 0 && loggedInIdx < commandIdx,
                "expected FIRST_JOIN block before COMMAND block, per writeAll()'s fixed section order");
    }

    @Test
    void disabledEventIsCommentedOutAndNotExported() {
        EventEntry disabled = welcomeKitFirstJoin();
        disabled.enabled = false;
        EventExporter.stageEvent(disabled);

        String js = EventExporter.buildEventsScript();

        assertTrue(js.contains("// DISABLED: shovel_js:welcome_kit"));
        assertFalse(js.contains("function shovel_js_check_shovel_js_welcome_kit"),
                "a disabled event must not emit its function trio");
        assertFalse(js.contains("PlayerEvents.loggedIn"),
                "a disabled event must not open a dispatch block with nothing enabled in it");
    }

    @Test
    void intervalEventEmitsNextAtEarlyOut() {
        EventEntry heartbeat = new EventEntry();
        heartbeat.id = "shovel_js:heartbeat";
        heartbeat.displayName = "Heartbeat";
        heartbeat.enabled = true;
        heartbeat.trigger.type = EventEntry.TriggerType.INTERVAL;
        heartbeat.trigger.intervalSeconds = 60;
        EventEntry.Action msg = new EventEntry.Action();
        msg.type = EventEntry.ActionType.MESSAGE;
        msg.text = "tick";
        heartbeat.actions.add(msg);
        assertEquals(List.of(), heartbeat.validate());

        EventExporter.stageEvent(heartbeat);
        String js = EventExporter.buildEventsScript();

        assertTrue(js.contains("shovel_js_g.getLong('shovel_js_interval_next')"),
                "must read the global next-wake-up key before the per-event loop");
        assertTrue(js.contains("if (shovel_js_next > 0 && shovel_js_now < shovel_js_next) return"),
                "must early-out the whole tick handler while nothing is due yet");
        assertTrue(js.contains("shovel_js_g.putLong('shovel_js_interval_next', shovel_js_minNext)"),
                "must persist the recomputed minimum at the end of the block");

        assertTrue(js.contains("shovel_js_g.getLong('shovel_js_last_shovel_js_heartbeat')"));
        assertTrue(js.contains("shovel_js_g.putLong('shovel_js_last_shovel_js_heartbeat', shovel_js_now)"));

        // The early-out read must come before the per-event min-next write.
        int readIdx = js.indexOf("shovel_js_g.getLong('shovel_js_interval_next')");
        int writeIdx = js.indexOf("shovel_js_g.putLong('shovel_js_interval_next', shovel_js_minNext)");
        assertTrue(readIdx >= 0 && writeIdx > readIdx,
                "expected the early-out read before the final min-next write");

        assertTrue(js.contains("shovel_js_interval_next: proximo epoch-second"),
                "expected an explanatory comment next to the new persistentData key");
    }

    @Test
    void intervalEventWithMaxGlobalStartsDoesNotShortCircuitOtherIntervals() {
        EventEntry capped = new EventEntry();
        capped.id = "shovel_js:capped";
        capped.displayName = "Capped";
        capped.enabled = true;
        capped.trigger.type = EventEntry.TriggerType.INTERVAL;
        capped.trigger.intervalSeconds = 60;
        capped.limits.maxGlobalStarts = 3;
        EventEntry.Action msg1 = new EventEntry.Action();
        msg1.type = EventEntry.ActionType.MESSAGE;
        msg1.text = "capped tick";
        capped.actions.add(msg1);
        assertEquals(List.of(), capped.validate());

        EventEntry uncapped = new EventEntry();
        uncapped.id = "shovel_js:uncapped";
        uncapped.displayName = "Uncapped";
        uncapped.enabled = true;
        uncapped.trigger.type = EventEntry.TriggerType.INTERVAL;
        uncapped.trigger.intervalSeconds = 300;
        EventEntry.Action msg2 = new EventEntry.Action();
        msg2.type = EventEntry.ActionType.MESSAGE;
        msg2.text = "uncapped tick";
        uncapped.actions.add(msg2);
        assertEquals(List.of(), uncapped.validate());

        EventExporter.stageEvent(capped);
        EventExporter.stageEvent(uncapped);
        String js = EventExporter.buildEventsScript();

        long returnCount = js.lines().filter(l -> l.trim().equals("return") || l.contains("!== 0) return") || l.contains("shovel_js_next) return")).count();
        // The two intentional early-out lines still present:
        assertTrue(js.contains("if (event.server.tickCount % 20 !== 0) return"));
        assertTrue(js.contains("if (shovel_js_next > 0 && shovel_js_now < shovel_js_next) return"));
        assertEquals(2, returnCount, "no `return` should remain inside a per-event maxGlobalStarts block");

        assertTrue(js.contains("if (shovel_js_g.getInt('shovel_js_starts_shovel_js_capped') < 3) {"));
        assertFalse(js.contains("if (shovel_js_g.getInt('shovel_js_starts_shovel_js_capped') >= 3) return"),
                "the old short-circuiting form must be gone");

        // Both events' kve_last_/kve_cand_ bookkeeping must still be present
        // (i.e. the capped event's block doesn't swallow the uncapped one).
        assertTrue(js.contains("shovel_js_g.getLong('shovel_js_last_shovel_js_capped')"));
        assertTrue(js.contains("var shovel_js_cand_shovel_js_capped ="));
        assertTrue(js.contains("shovel_js_g.getLong('shovel_js_last_shovel_js_uncapped')"));
        assertTrue(js.contains("var shovel_js_cand_shovel_js_uncapped ="));

        int lastWrite = js.lastIndexOf("shovel_js_g.putLong('shovel_js_interval_next', shovel_js_minNext)");
        int cappedCandIdx = js.indexOf("var shovel_js_cand_shovel_js_capped =");
        int uncappedCandIdx = js.indexOf("var shovel_js_cand_shovel_js_uncapped =");
        assertTrue(lastWrite > cappedCandIdx && lastWrite > uncappedCandIdx,
                "final putLong must come after both events' candidate computation");
    }

    @Test
    void invalidEventIsSkippedRatherThanCorruptingTheFile() {
        // INTERVAL < 60s fails EventEntry#validate, so
        // writeAll()/buildEventsScript() must skip it silently instead of
        // emitting a broken timer block.
        EventEntry tooFast = new EventEntry();
        tooFast.id = "shovel_js:too_fast";
        tooFast.trigger.type = EventEntry.TriggerType.INTERVAL;
        tooFast.trigger.intervalSeconds = 5;
        EventEntry.Action msg = new EventEntry.Action();
        msg.type = EventEntry.ActionType.MESSAGE;
        msg.text = "should never run";
        tooFast.actions.add(msg);
        assertFalse(tooFast.validate().isEmpty(), "fixture should be invalid for this test to mean anything");

        EventExporter.stageEvent(tooFast);
        String js = EventExporter.buildEventsScript();

        assertFalse(js.contains("shovel_js_too_fast"));
        assertFalse(js.contains("ServerEvents.tick"));
    }

    @Test
    void commandEventWithMaxGlobalStartsGatesOnPersistentDataCounter() {
        EventEntry raid = raid1Command();
        raid.limits.maxGlobalStarts = 3;
        assertEquals(List.of(), raid.validate());

        EventExporter.stageEvent(raid);
        String js = EventExporter.buildEventsScript();

        assertTrue(js.contains("var shovel_js_g_shovel_js_raid1 = ctx.source.server.persistentData"),
                "CommandRegistryEventJS has no getServer() - the server comes from the command source");
        assertTrue(js.contains("if (shovel_js_g_shovel_js_raid1.getInt('shovel_js_starts_shovel_js_raid1') >= 3) {"),
                "COMMAND with maxGlobalStarts must gate on a persistentData counter before running");
        assertTrue(js.contains("shovel_js_g_shovel_js_raid1.putInt('shovel_js_starts_shovel_js_raid1', shovel_js_g_shovel_js_raid1.getInt('shovel_js_starts_shovel_js_raid1') + 1)"));
    }

    @Test
    void commandEventWithoutMaxGlobalStartsSkipsCounter() {
        EventExporter.stageEvent(raid1Command());
        String js = EventExporter.buildEventsScript();

        assertFalse(js.contains("shovel_js_g_shovel_js_raid1"),
                "no cap configured -> no persistentData counter should be emitted for this event");
    }

    @Test
    void adminResetCommandCoversStagesAndPersistentDataAndKillCounter() {
        EventEntry welcome = welcomeKitFirstJoin();
        welcome.limits.cooldownSeconds = 30;
        EventExporter.stageEvent(welcome);

        EventEntry kill = new EventEntry();
        kill.id = "shovel_js:zombie_slayer";
        kill.enabled = true;
        kill.trigger.type = EventEntry.TriggerType.PLAYER_KILL;
        kill.trigger.entityId = "minecraft:zombie";
        kill.trigger.count = 5;
        EventEntry.Action msg = new EventEntry.Action();
        msg.type = EventEntry.ActionType.MESSAGE;
        msg.text = "5 zombies!";
        kill.actions.add(msg);
        assertEquals(List.of(), kill.validate());
        EventExporter.stageEvent(kill);

        String js = EventExporter.buildEventsScript();

        assertTrue(js.contains("Commands.literal('reset').requires(src => src.hasPermission(3))"),
                "reset must require a higher permission level than the player-facing /shovel event command");
        assertTrue(js.contains("case 'shovel_js:welcome_kit':"));
        assertTrue(js.contains("shovel_js_resetFlag(shovel_js_target, 'shovel_js_flag_shovel_js_welcome_kit')"));
        assertTrue(js.contains("shovel_js_pd.remove('shovel_js_cd_shovel_js_welcome_kit')"));
        assertTrue(js.contains("case 'shovel_js:zombie_slayer':"));
        assertTrue(js.contains("shovel_js_pd.remove('shovel_js_kills_shovel_js_zombie_slayer')"),
                "PLAYER_KILL events must also have their kill counter reset");
        assertTrue(js.contains("function shovel_js_resetFlag(player, flag)"));
        assertTrue(js.contains("player.stages.remove(flag)"));
        assertTrue(js.contains("player.persistentData.remove('shovel_js_pdflag_' + flag)"));
    }

    @Test
    void adminResetCommandOmittedWhenNoEnabledEvents() {
        String js = EventExporter.buildEventsScript();
        assertFalse(js.contains("Commands.literal('reset')"),
                "with nothing enabled there's nothing to reset, so the admin block should not be emitted");
    }

    @Test
    void permissionConditionGatesOnVanillaLevel() {
        EventEntry e = welcomeKitFirstJoin();
        EventEntry.Condition perm = new EventEntry.Condition();
        perm.type = EventEntry.ConditionType.PERMISSION;
        perm.permissionNode = "op";
        e.conditions.add(perm);
        assertEquals(List.of(), e.validate());

        EventExporter.stageEvent(e);
        String js = EventExporter.buildEventsScript();

        assertTrue(js.contains("if (!player.hasPermissions(2)) return false"),
                "'op' must gate on vanilla permission level 2");
    }

    @Test
    void blankPermissionConditionEmitsNoRestriction() {
        EventEntry e = welcomeKitFirstJoin();
        EventEntry.Condition perm = new EventEntry.Condition();
        perm.type = EventEntry.ConditionType.PERMISSION;
        perm.permissionNode = "";
        e.conditions.add(perm);
        assertEquals(List.of(), e.validate());

        EventExporter.stageEvent(e);
        String js = EventExporter.buildEventsScript();

        assertFalse(js.contains("player.hasPermissions"),
                "a blank PERMISSION condition must not emit any gate");
    }
}
