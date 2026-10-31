package com.kve.common.kubejs;

import com.kve.common.kubejs.KubeJSMobExporter.EquipmentBuild;
import com.kve.common.kubejs.KubeJSMobExporter.EquipmentItem;
import com.kve.common.kubejs.KubeJSMobExporter.MobEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Golden/snapshot test for a single mob "build" (health attribute + one
 * weighted equipment build): pins the EXACT generated JS handler body via
 * {@code KubeJSMobExporter#previewMobJS()} - same read-only path
 * {@code MobEquipmentBuildsExportTest} already exercises with substring
 * checks; this one pins the whole thing verbatim so any change to
 * indentation, number formatting, or call shape is caught immediately.
 */
class MobBuildGoldenSnapshotTest {

    @Test
    void huskWithMaxHealthAndASingleEquipmentBuildMatchesExactly() {
        MobEntry e = new MobEntry();
        e.entityId = "minecraft:husk";
        e.health = 30.0;

        EquipmentBuild build = new EquipmentBuild();
        build.name = "Iron Set";
        build.weight = 0.25;
        build.equipment.add(new EquipmentItem("MAINHAND", "minecraft:iron_sword", 0.05));
        e.equipmentBuilds.add(build);

        String js = KubeJSMobExporter.previewMobJS(e);

        String expected = String.join("\n", List.of(
                "// $EquipmentSlot ya esta declarado una vez arriba de todo en el archivo real",
                "'minecraft:husk': (mob, event) => {",
                "  mob.setAttributeBaseValue('minecraft:generic.max_health', 30)",
                "  mob.health = mob.maxHealth",
                "  try {",
                "    if (Math.random() < 0.2500) {",
                "      mob.setEquipment($EquipmentSlot.MAINHAND, Item.of('minecraft:iron_sword'))",
                "      try { mob.setDropChance($EquipmentSlot.MAINHAND, 0.0500) } catch (ex2) { console.error('[Shovel JS] dropChance fallo para ' + mob.type + ' MAINHAND: ' + ex2) }",
                "    }",
                "  } catch (ex) { console.error('[Shovel JS] equipo (build) fallo para ' + mob.type + ': ' + ex) }",
                "}"
        )) + "\n";

        assertEquals(expected, js);
    }
}
