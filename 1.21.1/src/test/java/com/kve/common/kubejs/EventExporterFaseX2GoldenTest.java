package com.kve.common.kubejs;

import com.kve.common.event.EventEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EventExporterFaseX2GoldenTest {

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
    void xpLevelReachTriggerPollsExperienceLevelAtOneHzAndDispatchesOnceThresholdMet() {
        EventEntry e = base("shovel_js:xplvl_test", EventEntry.TriggerType.XP_LEVEL_REACH);
        e.trigger.xpLevel = 10;
        EventExporter.stageEvent(e);

        String js = EventExporter.buildEventsScript();

        assertTrue(js.contains("PlayerEvents.tick(event => {"), "XP_LEVEL_REACH should share the 1Hz poll via PlayerEvents.tick (spec T11)");
        assertTrue(js.contains("if (event.player.tickCount % 20 !== 0) return"));
        assertTrue(js.contains("var shovel_js_lvl = player.xpLevel"));
        assertTrue(js.contains("if (shovel_js_prev_shovel_js_xplvl_test < 10 && shovel_js_lvl >= 10 && shovel_js_check_shovel_js_xplvl_test(player)) {"));
        assertTrue(js.contains("shovel_js_pd.putInt('shovel_js_reachlvl_shovel_js_xplvl_test', shovel_js_lvl)"));
    }

    @Test
    void xpChangedTriggerComparesAgainstLastSeenLevelInPersistentData() {
        EventEntry e = base("shovel_js:xpchg_test", EventEntry.TriggerType.XP_CHANGED);
        e.trigger.xpMinDelta = 2;
        EventExporter.stageEvent(e);

        String js = EventExporter.buildEventsScript();

        assertTrue(js.contains("if (shovel_js_pd.contains('shovel_js_lastlvl_shovel_js_xpchg_test')) {"), "first poll must only initialize");
        assertTrue(js.contains("var shovel_js_lastlvl_shovel_js_xpchg_test = shovel_js_pd.getInt('shovel_js_lastlvl_shovel_js_xpchg_test')"),
                "XP_CHANGED should read the last seen level from persistentData (spec T12)");
        assertTrue(js.contains("if (shovel_js_lvl - shovel_js_lastlvl_shovel_js_xpchg_test >= 2 && shovel_js_check_shovel_js_xpchg_test(player)) {"));
        assertTrue(js.contains("shovel_js_pd.putInt('shovel_js_lastlvl_shovel_js_xpchg_test', shovel_js_lvl)"));
    }

    @Test
    void effectGainedTriggerDetectsRisingEdgeOfPotionEffectViaPersistentDataFlag() {
        EventEntry e = base("shovel_js:eff_test", EventEntry.TriggerType.EFFECT_GAINED);
        e.trigger.effectId = "minecraft:speed";
        EventExporter.stageEvent(e);

        String js = EventExporter.buildEventsScript();

        assertTrue(js.contains("shovel_js_ef_shovel_js_eff_test = player.potionEffects.isActive('minecraft:speed')"),
                "EFFECT_GAINED should read the potion effect by id (spec T14)");
        assertTrue(js.contains("if (shovel_js_ef_shovel_js_eff_test && !shovel_js_hadef_shovel_js_eff_test && shovel_js_check_shovel_js_eff_test(player)) {"),
                "should only fire on the rising edge (didn't have it last poll, has it now)");
    }

    @Test
    void advancementEarnedTriggerMatchesAdvancementIdAndDispatchesThroughPlayerEventsAdvancement() {
        EventEntry e = base("shovel_js:adv_test", EventEntry.TriggerType.ADVANCEMENT_EARNED);
        e.trigger.advancementId = "minecraft:end/kill_dragon";
        EventExporter.stageEvent(e);

        String js = EventExporter.buildEventsScript();

        assertTrue(js.contains("PlayerEvents.advancement(event => {"), "ADVANCEMENT_EARNED should dispatch through PlayerEvents.advancement (spec T13)");
        assertTrue(js.contains("if (shovel_js_advid === 'minecraft:end/kill_dragon' && shovel_js_check_shovel_js_adv_test(player)) {"));
    }

    @Test
    void itemUseTriggerMatchesPlainIdAndDispatchesThroughItemEventsRightClicked() {
        EventEntry e = base("shovel_js:use_test", EventEntry.TriggerType.ITEM_USE);
        e.trigger.itemId = "minecraft:bow";
        EventExporter.stageEvent(e);

        String js = EventExporter.buildEventsScript();

        assertTrue(js.contains("ItemEvents.rightClicked(event => {"), "ITEM_USE should dispatch through ItemEvents.rightClicked (spec T19)");
        assertTrue(js.contains("if (shovel_js_useid === 'minecraft:bow' && shovel_js_check_shovel_js_use_test(player)) {"));
    }

    @Test
    void itemUseTriggerMatchesTagViaHasTag() {
        EventEntry e = base("shovel_js:use_tag_test", EventEntry.TriggerType.ITEM_USE);
        e.trigger.itemId = "#minecraft:arrows";
        EventExporter.stageEvent(e);

        String js = EventExporter.buildEventsScript();

        assertTrue(js.contains("event.item.hasTag('minecraft:arrows')"), "leading '#' should route through hasTag(), not a literal id match");
    }

    private static EventEntry withCondition(EventEntry.Condition c) {
        EventEntry e = base("shovel_js:cond_test", EventEntry.TriggerType.FIRST_JOIN);
        e.conditions.add(c);
        return e;
    }

    @Test
    void healthMaxConditionGatesWhenCurrentHealthAboveThreshold() {
        EventEntry.Condition c = new EventEntry.Condition();
        c.type = EventEntry.ConditionType.HEALTH_MAX;
        c.healthMax = 10.0f;
        EventExporter.stageEvent(withCondition(c));

        String js = EventExporter.buildEventsScript();

        assertTrue(js.contains("if (player.getHealth() > 10.0) return false"), "HEALTH_MAX should block when health is above the cap (spec C14)");
    }

    @Test
    void healthMinConditionGatesWhenCurrentHealthBelowThreshold() {
        EventEntry.Condition c = new EventEntry.Condition();
        c.type = EventEntry.ConditionType.HEALTH_MIN;
        c.healthMin = 5.0f;
        EventExporter.stageEvent(withCondition(c));

        String js = EventExporter.buildEventsScript();

        assertTrue(js.contains("if (player.getHealth() < 5.0) return false"), "HEALTH_MIN should block when health is below the floor (spec C15)");
    }

    @Test
    void foodLevelMinConditionReadsFoodData() {
        EventEntry.Condition c = new EventEntry.Condition();
        c.type = EventEntry.ConditionType.FOOD_LEVEL_MIN;
        c.foodLevelMin = 15;
        EventExporter.stageEvent(withCondition(c));

        String js = EventExporter.buildEventsScript();

        assertTrue(js.contains("if (player.foodData.getFoodLevel() < 15) return false"), "FOOD_LEVEL_MIN should read player.foodData (spec C16)");
    }

    @Test
    void hasEffectConditionChecksPotionEffectsGet() {
        EventEntry.Condition c = new EventEntry.Condition();
        c.type = EventEntry.ConditionType.HAS_EFFECT;
        c.hasEffectId = "minecraft:strength";
        EventExporter.stageEvent(withCondition(c));

        String js = EventExporter.buildEventsScript();

        assertTrue(js.contains("shovel_js_hef = player.potionEffects.isActive('minecraft:strength')"), "HAS_EFFECT should use EntityPotionEffectsJS#isActive - get(id) does not exist (spec C17)");
        assertTrue(js.contains("if (!shovel_js_hef) return false"));
    }

    @Test
    void hasItemEquippedConditionIndexesArmorSlotsInVanillaOrder() {
        EventEntry.Condition c = new EventEntry.Condition();
        c.type = EventEntry.ConditionType.HAS_ITEM_EQUIPPED;
        c.equippedSlot = EventEntry.EquipSlot.HEAD;
        c.equippedItemId = "minecraft:diamond_helmet";
        EventExporter.stageEvent(withCondition(c));

        String js = EventExporter.buildEventsScript();

        assertTrue(js.contains("shovel_js_eq = player.headArmorItem"), "HEAD should use LivingEntityKJS#getHeadArmorItem (spec C19)");
        assertTrue(js.contains("shovel_js_eq.id !== 'minecraft:diamond_helmet'"));
    }

    @Test
    void hasItemEquippedConditionUsesMainHandItemAccessorForMainhandSlot() {
        EventEntry.Condition c = new EventEntry.Condition();
        c.type = EventEntry.ConditionType.HAS_ITEM_EQUIPPED;
        c.equippedSlot = EventEntry.EquipSlot.MAINHAND;
        c.equippedItemId = "minecraft:diamond_sword";
        EventExporter.stageEvent(withCondition(c));

        String js = EventExporter.buildEventsScript();

        assertTrue(js.contains("shovel_js_eq = player.mainHandItem"), "MAINHAND should use the dedicated LivingEntityKJS accessor, not an armor slot index");
    }

    @Test
    void advancementDoneConditionChecksAdvancementsGetIsDone() {
        EventEntry.Condition c = new EventEntry.Condition();
        c.type = EventEntry.ConditionType.ADVANCEMENT_DONE;
        c.advancementId = "minecraft:story/mine_diamond";
        EventExporter.stageEvent(withCondition(c));

        String js = EventExporter.buildEventsScript();

        assertTrue(js.contains("shovel_js_adn = player.isAdvancementDone('minecraft:story/mine_diamond')"), "ADVANCEMENT_DONE should use ServerPlayerKJS#isAdvancementDone (spec C27)");
        assertTrue(js.contains("if (!shovel_js_adn) return false"));
    }

    @Test
    void advancementMissingConditionInvertsAdvancementDoneCheck() {
        EventEntry.Condition c = new EventEntry.Condition();
        c.type = EventEntry.ConditionType.ADVANCEMENT_MISSING;
        c.advancementId = "minecraft:story/mine_diamond";
        EventExporter.stageEvent(withCondition(c));

        String js = EventExporter.buildEventsScript();

        assertTrue(js.contains("shovel_js_adm = player.isAdvancementDone('minecraft:story/mine_diamond')"), "ADVANCEMENT_MISSING should reuse the same isAdvancementDone() check (spec C28)");
        assertTrue(js.contains("if (shovel_js_adm) return false"), "but block when the advancement IS done, the inverse of ADVANCEMENT_DONE");
    }

    private static EventEntry withAction(EventEntry.Action a) {
        EventEntry e = base("shovel_js:action_test", EventEntry.TriggerType.FIRST_JOIN);
        e.actions.add(a);
        return e;
    }

    @Test
    void clearItemActionEmptiesEveryMatchingStackWithNoCountCap() {
        EventEntry.Action a = new EventEntry.Action();
        a.type = EventEntry.ActionType.CLEAR_ITEM;
        a.itemId = "minecraft:rotten_flesh";
        EventExporter.stageEvent(withAction(a));

        String js = EventExporter.buildEventsScript();

        assertTrue(js.contains("shovel_js_ci_s.id === 'minecraft:rotten_flesh') shovel_js_ci_s.setCount(0)"),
                "CLEAR_ITEM should zero out every matching stack in the inventory (spec A14)");
    }

    @Test
    void giveXpPointsActionCallsGiveExperiencePoints() {
        EventEntry.Action a = new EventEntry.Action();
        a.type = EventEntry.ActionType.GIVE_XP_POINTS;
        a.xpPoints = 50;
        EventExporter.stageEvent(withAction(a));

        String js = EventExporter.buildEventsScript();

        assertTrue(js.contains("player.giveExperiencePoints(50)"), "GIVE_XP_POINTS should call player.giveExperiencePoints (spec A16)");
    }

    @Test
    void setXpLevelActionAssignsExperienceLevelDirectly() {
        EventEntry.Action a = new EventEntry.Action();
        a.type = EventEntry.ActionType.SET_XP_LEVEL;
        a.setXpLevel = 30;
        EventExporter.stageEvent(withAction(a));

        String js = EventExporter.buildEventsScript();

        assertTrue(js.contains("player.setXpLevel(30)"), "SET_XP_LEVEL should use PlayerKJS#setXpLevel so the client is synced (spec A17)");
    }

    @Test
    void addStageActionIsWrappedInTryCatchLikeFlags() {
        EventEntry.Action a = new EventEntry.Action();
        a.type = EventEntry.ActionType.ADD_STAGE;
        a.stageId = "met_the_elder";
        EventExporter.stageEvent(withAction(a));

        String js = EventExporter.buildEventsScript();

        assertTrue(js.contains("    player.stages.add('met_the_elder')\n  } catch (ex) {"), "ADD_STAGE runs inside its own per-action try/catch (spec A18 + section 6)");
    }

    @Test
    void removeStageActionIsWrappedInTryCatchLikeFlags() {
        EventEntry.Action a = new EventEntry.Action();
        a.type = EventEntry.ActionType.REMOVE_STAGE;
        a.stageId = "met_the_elder";
        EventExporter.stageEvent(withAction(a));

        String js = EventExporter.buildEventsScript();

        assertTrue(js.contains("    player.stages.remove('met_the_elder')\n  } catch (ex) {"), "REMOVE_STAGE runs inside its own per-action try/catch (spec A19)");
    }

    @Test
    void healActionWithFullFlagSetsHealthToMaxHealth() {
        EventEntry.Action a = new EventEntry.Action();
        a.type = EventEntry.ActionType.HEAL;
        a.healFull = true;
        EventExporter.stageEvent(withAction(a));

        String js = EventExporter.buildEventsScript();

        assertTrue(js.contains("player.setHealth(player.getMaxHealth())"), "HEAL with healFull should set health to max (spec A23)");
    }

    @Test
    void healActionWithAmountAddsAndClampsToMaxHealth() {
        EventEntry.Action a = new EventEntry.Action();
        a.type = EventEntry.ActionType.HEAL;
        a.healFull = false;
        a.healAmount = 4.0f;
        EventExporter.stageEvent(withAction(a));

        String js = EventExporter.buildEventsScript();

        assertTrue(js.contains("player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + 4.0))"),
                "HEAL with an amount should add and clamp to max health (spec A23)");
    }

    @Test
    void setFoodActionSetsFoodLevelAndOptionalSaturation() {
        EventEntry.Action a = new EventEntry.Action();
        a.type = EventEntry.ActionType.SET_FOOD;
        a.setFoodLevel = 18;
        a.setFoodSaturationEnabled = true;
        a.setFoodSaturation = 5.0f;
        EventExporter.stageEvent(withAction(a));

        String js = EventExporter.buildEventsScript();

        assertTrue(js.contains("player.foodData.setFoodLevel(18)"), "SET_FOOD should always set the food level (spec A24)");
        assertTrue(js.contains("player.foodData.setSaturation(5.0)"), "SET_FOOD should set saturation when explicitly enabled");
    }

    @Test
    void setFoodActionOmitsSaturationCallWhenNotEnabled() {
        EventEntry.Action a = new EventEntry.Action();
        a.type = EventEntry.ActionType.SET_FOOD;
        a.setFoodLevel = 10;
        a.setFoodSaturationEnabled = false;
        EventExporter.stageEvent(withAction(a));

        String js = EventExporter.buildEventsScript();

        assertTrue(js.contains("player.foodData.setFoodLevel(10)"));
        assertTrue(!js.contains("player.foodData.setSaturation"), "SET_FOOD should skip setSaturation entirely when the optional flag is off");
    }

    @Test
    void setHealthActionSetsHealthDirectlyToGivenAmount() {
        EventEntry.Action a = new EventEntry.Action();
        a.type = EventEntry.ActionType.SET_HEALTH;
        a.setHealthAmount = 6.0f;
        EventExporter.stageEvent(withAction(a));

        String js = EventExporter.buildEventsScript();

        assertTrue(js.contains("player.setHealth(6.0)"), "SET_HEALTH should set health directly, unclamped by a min/current comparison (spec A25)");
    }

    @Test
    void clearEffectsActionTriesPotionEffectsClearThenFallsBackToRemoveAllEffects() {
        EventEntry.Action a = new EventEntry.Action();
        a.type = EventEntry.ActionType.CLEAR_EFFECTS;
        EventExporter.stageEvent(withAction(a));

        String js = EventExporter.buildEventsScript();

        assertTrue(js.contains("    player.potionEffects.clear()\n"),
                "CLEAR_EFFECTS should call EntityPotionEffectsJS#clear (verified API) (spec A26)");
    }
}
