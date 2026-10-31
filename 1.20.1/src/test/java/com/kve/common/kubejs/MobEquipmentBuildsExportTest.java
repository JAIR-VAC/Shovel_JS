package com.kve.common.kubejs;

import com.kve.common.kubejs.KubeJSMobExporter.EquipmentBuild;
import com.kve.common.kubejs.KubeJSMobExporter.EquipmentItem;
import com.kve.common.kubejs.KubeJSMobExporter.MobEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Coverage for the new weighted equipment "builds" feature: a mob with
 * equipmentBuilds defined should spawn with exactly one build, chosen by
 * weighted random - e.g. a zombie that's sometimes unarmed, sometimes
 * carries an iron sword, sometimes a diamond sword, each at a different
 * relative weight. This exercises KubeJSMobExporter#previewMobJS() (which
 * shares its equipment-writing code with the real writeScript() export),
 * checking the generated JS rather than trying to actually run it.
 */
class MobEquipmentBuildsExportTest {

    private MobEntry mobWithBuilds(EquipmentBuild... builds) {
        MobEntry e = new MobEntry();
        e.entityId = "minecraft:zombie";
        for (EquipmentBuild b : builds) e.equipmentBuilds.add(b);
        return e;
    }

    private EquipmentBuild build(String name, double weight, String... itemIds) {
        EquipmentBuild b = new EquipmentBuild();
        b.name = name;
        b.weight = weight;
        for (String id : itemIds) b.equipment.add(new EquipmentItem("MAINHAND", id, 0.085));
        return b;
    }

    @Test
    void mobWithNoBuildsAndNoFlatEquipmentEmitsNothing() {
        MobEntry e = new MobEntry();
        String js = KubeJSMobExporter.previewMobJS(e);
        assertFalse(js.contains("setEquipment"), "a mob with neither equipment nor equipmentBuilds should not emit any equipment code");
    }

    @Test
    void singleBuildAtWeight100IsAppliedUnconditionallyWithNoRandomRoll() {
        MobEntry e = mobWithBuilds(build("Iron Set", 100.0, "minecraft:iron_sword"));
        String js = KubeJSMobExporter.previewMobJS(e);

        assertTrue(js.contains("minecraft:iron_sword"));
        assertFalse(js.contains("Math.random()"),
                "a single build at weight >= 100 is an unconditional 100% outcome and shouldn't need a random roll");
    }

    @Test
    void singleBuildBelow100WeightIsAPercentChanceNotAlwaysApplied() {
        MobEntry e = mobWithBuilds(build("Iron Set", 10.0, "minecraft:iron_sword"));
        String js = KubeJSMobExporter.previewMobJS(e);

        assertTrue(js.contains("minecraft:iron_sword"));
        assertTrue(js.contains("Math.random() < 0.1"),
                "a lone build's weight below 100 must be honored as its own % chance (10 -> 10%), not silently forced to 100%:\n" + js);
    }

    @Test
    void multipleBuildsProduceAWeightedRandomChain() {
        MobEntry e = mobWithBuilds(
                build("Unarmed", 7.0),
                build("Iron Set", 2.0, "minecraft:iron_sword"),
                build("Diamond Set", 1.0, "minecraft:diamond_sword"));
        String js = KubeJSMobExporter.previewMobJS(e);

        assertTrue(js.contains("Math.random()"), "2+ competing builds need a weighted random roll");
        assertTrue(js.contains("minecraft:iron_sword"));
        assertTrue(js.contains("minecraft:diamond_sword"));
        assertTrue(js.contains("0.7"), "expected the first build's cumulative weight (7/10) as a literal threshold in the generated JS:\n" + js);
        assertTrue(js.contains("0.9"), "expected the second build's cumulative weight (9/10) as a literal threshold in the generated JS:\n" + js);
        assertTrue(js.contains("} else {"), "the last build should be an unconditional else, not another random-threshold check");
    }

    @Test
    void disabledOrZeroWeightBuildsDoNotCompeteForTheRoll() {
        EquipmentBuild disabled = build("Disabled Set", 5.0, "minecraft:netherite_sword");
        disabled.enabled = false;
        EquipmentBuild zeroWeight = build("Zero Weight Set", 0.0, "minecraft:golden_sword");
        MobEntry e = mobWithBuilds(disabled, zeroWeight, build("Iron Set", 100.0, "minecraft:iron_sword"));

        String js = KubeJSMobExporter.previewMobJS(e);

        assertFalse(js.contains("netherite_sword"), "a disabled build must never be exported");
        assertFalse(js.contains("golden_sword"), "a zero-weight build must never be exported");
        assertTrue(js.contains("iron_sword"));
        assertFalse(js.contains("Math.random()"));
    }

    @Test
    void allBuildsFilteredOutEmitsNothing() {
        EquipmentBuild disabled = build("Disabled Set", 1.0, "minecraft:iron_sword");
        disabled.enabled = false;
        MobEntry e = mobWithBuilds(disabled);

        String js = KubeJSMobExporter.previewMobJS(e);
        assertFalse(js.contains("setEquipment"));
    }

    @Test
    void equipmentBuildsTakesOverFromTheFlatEquipmentList() {
        MobEntry e = mobWithBuilds(build("Iron Set", 1.0, "minecraft:iron_sword"));
        e.equipment.add(new EquipmentItem("OFFHAND", "minecraft:shield", 0.0));

        String js = KubeJSMobExporter.previewMobJS(e);
        assertTrue(js.contains("iron_sword"));
        assertFalse(js.contains("shield"), "once equipmentBuilds is non-empty, the flat equipment list must be ignored entirely");
    }

    @Test
    void singleBuildAtDefaultWeightOneIsAlways() {
        MobEntry e = mobWithBuilds(build("Iron Set", 1.0, "minecraft:iron_sword"));
        String js = KubeJSMobExporter.previewMobJS(e);
        assertTrue(js.contains("minecraft:iron_sword"));
        assertFalse(js.contains("Math.random()"), js);
    }

    @Test
    void buildWeightsSummingBelowOneAreAbsoluteAndLeaveAnUnarmedRemainder() {
        MobEntry e = mobWithBuilds(
                build("Iron Set", 0.1, "minecraft:iron_sword"),
                build("Diamond Set", 0.3, "minecraft:diamond_sword"));
        double[] p = KubeJSMobExporter.buildProbabilities(e.equipmentBuilds);
        assertEquals(0.1, p[0], 1e-9);
        assertEquals(0.3, p[1], 1e-9);

        String js = KubeJSMobExporter.previewMobJS(e);
        assertTrue(js.contains("$roll < 0.1000"), js);
        assertTrue(js.contains("$roll < 0.4000"), js);
        assertFalse(js.contains("} else {"), "with a remainder there must be no unconditional else:\n" + js);
    }

    @Test
    void itemSpawnChanceBelowOneWrapsTheItemInARoll() {
        MobEntry e = new MobEntry();
        e.entityId = "minecraft:zombie";
        e.equipment.add(new EquipmentItem("HEAD", "minecraft:iron_helmet", 0.085, "", 0.1));
        e.equipment.add(new EquipmentItem("MAINHAND", "minecraft:iron_sword", 0.085));
        String js = KubeJSMobExporter.previewMobJS(e);
        assertTrue(js.contains("if (Math.random() < 0.1000) {"), js);
        assertEquals(1, js.split("Math.random\\(\\)", -1).length - 1, "only the helmet (spawnChance 0.1) should be rolled:\n" + js);
    }

    @Test
    void parseProbabilityAcceptsFractionsAndPercentages() {
        assertEquals(1.0, KubeJSMobExporter.parseProbability("1"), 1e-9);
        assertEquals(0.1, KubeJSMobExporter.parseProbability("0.1"), 1e-9);
        assertEquals(0.1, KubeJSMobExporter.parseProbability("0,1"), 1e-9);
        assertEquals(0.25, KubeJSMobExporter.parseProbability("25"), 1e-9);
        assertEquals(1.0, KubeJSMobExporter.parseProbability("500"), 1e-9);
        assertTrue(Double.isNaN(KubeJSMobExporter.parseProbability("abc")));
    }

    @Test
    void emptyBuildIsAValidSpawnsWithNothingOutcomeAndStillGetsItsShareOfTheWeight() {
        MobEntry e = mobWithBuilds(
                build("Unarmed", 1.0),
                build("Iron Set", 1.0, "minecraft:iron_sword"));
        String js = KubeJSMobExporter.previewMobJS(e);

        assertTrue(js.contains("0.5"), "an empty build should still consume its share of the weight, producing a 50% threshold here:\n" + js);
        assertTrue(js.contains("iron_sword"));
    }
}
