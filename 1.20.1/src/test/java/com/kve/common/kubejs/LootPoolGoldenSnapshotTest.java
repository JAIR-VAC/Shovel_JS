package com.kve.common.kubejs;

import com.kve.common.kubejs.LootTableExporter.LootEntryData;
import com.kve.common.kubejs.LootTableExporter.LootPoolData;
import com.kve.common.kubejs.LootTableExporter.LootTableEntry;
import com.kve.common.kubejs.LootTableExporter.TargetKind;
import com.kve.common.kubejs.LootTableExporter.TriggerEvent;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Golden/snapshot test for a single loot pool: pins the exact vanilla
 * loot_table datapack JSON (path + pretty-printed content) that a DEATH
 * entry with one pool/one entry produces, via
 * {@code LootTableExporter#previewLoot()} - same read-only path the
 * in-game preview button uses.
 */
class LootPoolGoldenSnapshotTest {

    @Test
    void singlePoolSingleEntryProducesTheExpectedVanillaLootTableJson() {
        LootTableEntry e = new LootTableEntry();
        e.kind = TargetKind.ENTITY;
        e.event = TriggerEvent.DEATH;
        e.targetId = "minecraft:zombie";

        LootPoolData pool = new LootPoolData();
        pool.rolls = 1;
        LootEntryData entry = new LootEntryData();
        entry.itemId = "minecraft:rotten_flesh";
        entry.weight = 1;
        entry.minCount = 1;
        entry.maxCount = 1;
        pool.entries.add(entry);
        e.pools.add(pool);

        String preview = LootTableExporter.previewLoot(e);

        ResourceLocation rl = new ResourceLocation(e.targetId);
        Path expectedPath = Paths.get("kubejs", "data")
                .resolve(rl.getNamespace()).resolve("loot_tables").resolve("entities/zombie.json");
        String expected = "// " + expectedPath + "\n"
                + "{\n"
                + "  \"type\": \"minecraft:entity\",\n"
                + "  \"pools\": [\n"
                + "    {\n"
                + "      \"rolls\": 1,\n"
                + "      \"entries\": [\n"
                + "        {\n"
                + "          \"type\": \"minecraft:item\",\n"
                + "          \"name\": \"minecraft:rotten_flesh\",\n"
                + "          \"weight\": 1\n"
                + "        }\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}";

        assertEquals(expected, preview);
    }

    @Test
    void anEntryWithAMalformedItemIdIsOmittedFromThePoolInsteadOfBreakingTheFile() {
        LootTableEntry e = new LootTableEntry();
        e.kind = TargetKind.ENTITY;
        e.event = TriggerEvent.DEATH;
        e.targetId = "minecraft:zombie";

        LootPoolData pool = new LootPoolData();
        LootEntryData good = new LootEntryData();
        good.itemId = "minecraft:rotten_flesh";
        LootEntryData bad = new LootEntryData();
        bad.itemId = "not a valid item id";
        pool.entries.add(good);
        pool.entries.add(bad);
        e.pools.add(pool);

        String preview = LootTableExporter.previewLoot(e);

        assertTrue(preview.contains("minecraft:rotten_flesh"), "the good entry must still be written:\n" + preview);
        assertFalse(preview.contains("not a valid item id"), "the bad entry must be omitted, not mixed in:\n" + preview);
    }
}
