package com.kve.common.kubejs;

import com.kve.common.event.EventEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventExporterFaseX1GoldenTest {

    @BeforeEach
    void setUp() {
        EventExporter.clearAll();
    }

    @AfterEach
    void tearDown() {
        EventExporter.clearAll();
    }

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

    @Test
    void playerDeathTriggerFiltersOnDeathMessageAndDispatchesThroughEntityEventsDeath() {
        EventEntry e = base("shovel_js:death_test", EventEntry.TriggerType.PLAYER_DEATH);
        e.trigger.deathMessageContains = "fell";
        EventExporter.stageEvent(e);

        String js = EventExporter.buildEventsScript();

        assertTrue(js.contains("EntityEvents.death(event => {"), "PLAYER_DEATH should dispatch through EntityEvents.death (spec T07)");
        assertTrue(js.contains("if (!event.entity.player) return"));
        assertTrue(js.contains("shovel_js_dmsg_shovel_js_death_test = String(event.source.getLocalizedDeathMessage(player).getString())"));
        assertTrue(js.contains("if (shovel_js_dmsg_shovel_js_death_test.indexOf('fell') !== -1 && shovel_js_check_shovel_js_death_test(player)) {"));
        assertFalse(js.contains(") return\n  if (shovel_js_check_shovel_js_death_test"), "the message filter must not return out of the shared handler");
    }

    @Test
    void playerRespawnTriggerHasNoOwnFieldsAndDispatchesThroughPlayerEventsRespawned() {
        EventEntry e = base("shovel_js:respawn_test", EventEntry.TriggerType.PLAYER_RESPAWN);
        EventExporter.stageEvent(e);

        String js = EventExporter.buildEventsScript();

        assertTrue(js.contains("PlayerEvents.respawned(event => {"), "PLAYER_RESPAWN should dispatch through PlayerEvents.respawned (spec T08)");
        assertTrue(js.contains("shovel_js_check_shovel_js_respawn_test(player)"));
    }

    @Test
    void playerHurtTriggerGatesOnMinDamageAndDispatchesThroughEntityEventsHurt() {
        EventEntry e = base("shovel_js:hurt_test", EventEntry.TriggerType.PLAYER_HURT);
        e.trigger.minDamage = 4.5f;
        EventExporter.stageEvent(e);

        String js = EventExporter.buildEventsScript();

        assertTrue(js.contains("EntityEvents.beforeHurt(event => {"), "PLAYER_HURT -> EntityEvents.beforeHurt (KubeJS 7; hurt ya no existe)");
        assertTrue(js.contains("var shovel_js_dmg = event.damage"), "LivingEntityHurtEventJS exposes getDamage(), not amount");
        assertTrue(js.contains("if (shovel_js_dmg >= 4.5 && shovel_js_check_shovel_js_hurt_test(player)) {"));
    }

    @Test
    void dimensionChangeTriggerMatchesFromAndToDimensionAndDispatchesThroughPlayerEventsTickPoll() {
        EventEntry e = base("shovel_js:dim_test", EventEntry.TriggerType.DIMENSION_CHANGE);
        e.trigger.fromDimension = "minecraft:overworld";
        e.trigger.toDimension = "minecraft:the_nether";
        EventExporter.stageEvent(e);

        String js = EventExporter.buildEventsScript();

        assertTrue(js.contains("PlayerEvents.tick(event => {"), "DIMENSION_CHANGE should dispatch through a PlayerEvents.tick poll (spec T10; PlayerEvents.changedDimension doesn't exist in KubeJS)");
        assertFalse(js.contains("PlayerEvents.changedDimension("), "PlayerEvents.changedDimension() doesn't exist in KubeJS and must never be emitted as an actual call (the explanatory comment mentioning it by name is fine)");
        assertTrue(js.contains("var shovel_js_to = String(player.level.dimension)"));
        assertTrue(js.contains("var shovel_js_from = shovel_js_pd.getString('shovel_js_lastdimension')"));
        assertTrue(js.contains("if (shovel_js_to === 'minecraft:the_nether' && shovel_js_from === 'minecraft:overworld' && shovel_js_check_shovel_js_dim_test(player)) {"));
    }

    @Test
    void blockBreakTriggerMatchesPlainIdAndDispatchesThroughBlockEventsBroken() {
        EventEntry e = base("shovel_js:break_test", EventEntry.TriggerType.BLOCK_BREAK);
        e.trigger.blockId = "minecraft:diamond_ore";
        e.trigger.count = 3;
        EventExporter.stageEvent(e);

        String js = EventExporter.buildEventsScript();

        assertTrue(js.contains("BlockEvents.broken(event => {"), "BLOCK_BREAK should dispatch through BlockEvents.broken (spec T15)");
        assertTrue(js.contains("event.block.id === 'minecraft:diamond_ore'"));
    }

    @Test
    void blockPlaceTriggerMatchesTagAndDispatchesThroughBlockEventsPlaced() {
        EventEntry e = base("shovel_js:place_test", EventEntry.TriggerType.BLOCK_PLACE);
        e.trigger.blockId = "#minecraft:logs";
        EventExporter.stageEvent(e);

        String js = EventExporter.buildEventsScript();

        assertTrue(js.contains("BlockEvents.placed(event => {"), "BLOCK_PLACE should dispatch through BlockEvents.placed (spec T16)");
        assertTrue(js.contains("event.block.hasTag('minecraft:logs')"), "leading '#' should route through hasTag(), not a literal id match");
    }

    private static EventEntry withAction(EventEntry.Action a) {
        EventEntry e = base("shovel_js:action_test", EventEntry.TriggerType.FIRST_JOIN);
        e.actions.add(a);
        return e;
    }

    @Test
    void actionbarActionUsesTellWithActionBarFlag() {
        EventEntry.Action a = new EventEntry.Action();
        a.type = EventEntry.ActionType.ACTIONBAR;
        a.text = "Cuidado!";
        EventExporter.stageEvent(withAction(a));

        String js = EventExporter.buildEventsScript();

        assertTrue(js.contains("player.displayClientMessage(Text.of('Cuidado!'), true)"), "ACTIONBAR should render via the vanilla player.displayClientMessage(Text.of(...), true) - player.tell(Component, boolean) does not exist in KubeJS and crashes at runtime (spec A07)");
    }

    @Test
    void takeItemActionShrinksMatchingStacksAcrossInventory() {
        EventEntry.Action a = new EventEntry.Action();
        a.type = EventEntry.ActionType.TAKE_ITEM;
        a.itemId = "minecraft:emerald";
        a.itemCount = 5;
        EventExporter.stageEvent(withAction(a));

        String js = EventExporter.buildEventsScript();

        assertTrue(js.contains("var shovel_js_ti_left = 5; var shovel_js_ti_inv = player.inventory.items"), "TAKE_ITEM should walk player.inventory.items shrinking matches (spec A13)");
        assertTrue(js.contains("shovel_js_ti_s.id === 'minecraft:emerald'"));
    }

    @Test
    void giveXpLevelsActionCallsGiveExperienceLevels() {
        EventEntry.Action a = new EventEntry.Action();
        a.type = EventEntry.ActionType.GIVE_XP_LEVELS;
        a.xpLevels = 7;
        EventExporter.stageEvent(withAction(a));

        String js = EventExporter.buildEventsScript();

        assertTrue(js.contains("player.giveExperienceLevels(7)"), "GIVE_XP_LEVELS should call player.giveExperienceLevels (spec A15)");
    }

    @Test
    void addEffectActionAddsPotionEffectWithDurationAndAmplifier() {
        EventEntry.Action a = new EventEntry.Action();
        a.type = EventEntry.ActionType.ADD_EFFECT;
        a.effectId = "minecraft:regeneration";
        a.effectDurationTicks = 100;
        a.effectAmplifier = 1;
        EventExporter.stageEvent(withAction(a));

        String js = EventExporter.buildEventsScript();

        assertTrue(js.contains("player.potionEffects.add('minecraft:regeneration', 100, 1)"), "ADD_EFFECT should call player.potionEffects.add (spec A21)");
    }

    @Test
    void teleportActionWithDimUsesFourPlusArgOverload() {
        EventEntry.Action a = new EventEntry.Action();
        a.type = EventEntry.ActionType.TELEPORT;
        a.teleportDim = "minecraft:the_end";
        a.teleportX = 100;
        a.teleportY = 65;
        a.teleportZ = -20;
        EventExporter.stageEvent(withAction(a));

        String js = EventExporter.buildEventsScript();

        assertTrue(js.contains("shovel_js_cmd(player, 'execute in ' + 'minecraft:the_end' + ' run tp ' + shovel_js_uuid(player) + ' 100.0 65.0 -20.0 0.0 0.0')"),
                "TELEPORT with a dim should tp via /execute in <dim> (teleportTo('dim',...) is ambiguous in KubeJS 6) (spec A27)");
    }

    @Test
    void teleportActionWithoutDimUsesThreeArgOverload() {
        EventEntry.Action a = new EventEntry.Action();
        a.type = EventEntry.ActionType.TELEPORT;
        a.teleportX = 10;
        a.teleportY = 70;
        a.teleportZ = 10;
        EventExporter.stageEvent(withAction(a));

        String js = EventExporter.buildEventsScript();

        assertTrue(js.contains("shovel_js_cmd(player, 'execute in ' + shovel_js_dim(player) + ' run tp ' + shovel_js_uuid(player) + ' 10.0 70.0 10.0 0.0 0.0')"),
                "TELEPORT without a dim should stay in the entity's current dimension (spec A27)");
    }

    @Test
    void teleportSpawnActionUsesWorldSharedSpawnPos() {
        EventEntry.Action a = new EventEntry.Action();
        a.type = EventEntry.ActionType.TELEPORT_SPAWN;
        EventExporter.stageEvent(withAction(a));

        String js = EventExporter.buildEventsScript();

        assertTrue(js.contains("var shovel_js_sp = shovel_js_server(player).overworld().sharedSpawnPos"),
                "TELEPORT_SPAWN should use the OVERWORLD shared spawn, not the current dimension's (spec A28)");
        assertTrue(js.contains("'execute in minecraft:overworld run tp ' + shovel_js_uuid(player)"));
    }
}
