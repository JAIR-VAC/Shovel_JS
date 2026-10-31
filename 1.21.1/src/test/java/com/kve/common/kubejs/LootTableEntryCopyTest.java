package com.kve.common.kubejs;

import com.kve.common.kubejs.LootTableExporter.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LootTableEntryCopyTest {

    private LootTableEntry sample() {
        LootFunctionData function = new LootFunctionData();
        function.type = FunctionType.SET_NBT;
        function.levelsMin = 5;
        function.levelsMax = 10;
        function.treasure = true;
        function.nbt = "{Damage:0}";

        LootEntryData entry = new LootEntryData();
        entry.itemId = "minecraft:diamond";
        entry.weight = 3;
        entry.minCount = 1;
        entry.maxCount = 2;
        entry.functions.add(function);

        LootPoolData pool = new LootPoolData();
        pool.rolls = 2;
        pool.condition = Condition.RANDOM_CHANCE_WITH_LOOTING;
        pool.chance = 0.25;
        pool.lootingMultiplier = 0.05;
        pool.entries.add(entry);

        LootTableEntry table = new LootTableEntry();
        table.kind = TargetKind.ENTITY;
        table.event = TriggerEvent.DEATH;
        table.targetId = "minecraft:zombie";
        table.enabled = true;
        table.category = "mobs";
        table.pools.add(pool);
        return table;
    }

    @Test
    void copyPreservesContentAtEveryLevel() {
        LootTableEntry original = sample();
        LootTableEntry copy = original.copy();

        assertEquals(1, copy.pools.size());
        LootPoolData poolCopy = copy.pools.get(0);
        assertEquals(2, poolCopy.rolls);
        assertEquals(1, poolCopy.entries.size());

        LootEntryData entryCopy = poolCopy.entries.get(0);
        assertEquals("minecraft:diamond", entryCopy.itemId);
        assertEquals(1, entryCopy.functions.size());

        LootFunctionData functionCopy = entryCopy.functions.get(0);
        assertEquals(FunctionType.SET_NBT, functionCopy.type);
        assertEquals("{Damage:0}", functionCopy.nbt);
    }

    @Test
    void copyDoesNotShareListOrObjectInstancesAtAnyLevel() {
        LootTableEntry original = sample();
        LootTableEntry copy = original.copy();

        assertNotSame(original.pools, copy.pools);
        assertNotSame(original.pools.get(0), copy.pools.get(0));
        assertNotSame(original.pools.get(0).entries, copy.pools.get(0).entries);
        assertNotSame(original.pools.get(0).entries.get(0), copy.pools.get(0).entries.get(0));
        assertNotSame(original.pools.get(0).entries.get(0).functions, copy.pools.get(0).entries.get(0).functions);
        assertNotSame(original.pools.get(0).entries.get(0).functions.get(0), copy.pools.get(0).entries.get(0).functions.get(0));
    }

    @Test
    void mutatingDeepestLevelOfOriginalAfterCopyDoesNotAffectCopy() {
        LootTableEntry original = sample();
        LootTableEntry copy = original.copy();

        original.pools.get(0).entries.get(0).functions.get(0).nbt = "{Damage:1000}";
        original.pools.get(0).entries.get(0).weight = 999;
        original.pools.get(0).rolls = 999;
        original.category = "mutated";

        LootFunctionData functionCopy = copy.pools.get(0).entries.get(0).functions.get(0);
        assertEquals("{Damage:0}", functionCopy.nbt, "a mutation 4 levels deep in the original must not leak into the copy");
        assertEquals(3, copy.pools.get(0).entries.get(0).weight);
        assertEquals(2, copy.pools.get(0).rolls);
        assertEquals("mobs", copy.category);
    }
}
