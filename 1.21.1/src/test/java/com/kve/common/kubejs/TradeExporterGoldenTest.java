package com.kve.common.kubejs;

import com.kve.common.kubejs.TradeExporter.*;
import com.kve.common.trade.KveNativeTradeBackend;
import com.kve.common.trade.TradeBackendRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TradeExporterGoldenTest {

    @BeforeEach
    void clear() { TradeExporter.clearAll(); }

    @AfterEach
    void cleanup() { TradeExporter.clearAll(); }

    private TradeEntry villagerEntry() {
        TradeEntry e = new TradeEntry();
        e.id = "shovel_js:armorer_level2_core";
        e.enabled = true;
        e.targetKind = TradeTargetKind.VILLAGER;
        e.professionId = "minecraft:armorer";
        e.level = 2;
        e.mode = TradeEditMode.REPLACE_LEVEL;

        TradeOffer o = new TradeOffer();
        o.inputAId = "minecraft:iron_ingot";
        o.inputACount = 4;
        o.outputId = "minecraft:iron_chestplate";
        o.outputCount = 1;
        o.maxUses = 12;
        o.villagerXp = 10;
        o.priceMultiplier = 0.05f;
        e.offers.add(o);
        return e;
    }

    @Test
    void offerExtrasRoundTripAndStayAbsentWhenDefault() {
        TradeOffer o = new TradeOffer();
        com.google.gson.JsonObject plain = new com.google.gson.JsonObject();
        TradeExporter.writeOfferExtras(plain, o);
        assertEquals(0, plain.size(), "old-format JSON unchanged for plain offers");

        o.unlockOffset = 3;
        o.editInputAId = "minecraft:emerald"; o.editInputBId = ""; o.editOutputId = "minecraft:bread";
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        TradeExporter.writeOfferExtras(json, o);
        TradeOffer back = new TradeOffer();
        TradeExporter.readOfferExtras(json, back);
        assertEquals(3, back.unlockOffset);
        assertTrue(back.isEdit());
        assertEquals("minecraft:bread", back.editOutputId);
        TradeOffer c = back.copy();
        assertEquals(3, c.unlockOffset);
        assertEquals("minecraft:emerald", c.editInputAId);
    }

    @Test
    void validateRejectsEditsOutsideAddModeAndOffsetsOutOfRange() {
        TradeEntry e = villagerEntry();
        e.offers.get(0).editInputAId = "minecraft:emerald";
        e.offers.get(0).editOutputId = "minecraft:bread";
        assertTrue(TradeExporter.validateTrade(e).stream().anyMatch(m -> m.contains("Agregar/editar")));

        TradeOffer o = villagerEntry().offers.get(0);
        o.unlockOffset = 5;
        assertTrue(TradeExporter.validateOffer(o, new KveNativeTradeBackend()).stream().anyMatch(m -> m.contains("Desbloqueo")));
    }

    @Test
    void validateTradeReportsNoBackendWhenRegistryEmpty() {
        TradeBackendRegistry.resetForTests();
        TradeEntry e = villagerEntry();

        List<String> errors = TradeExporter.validateTrade(e);

        assertTrue(errors.stream().anyMatch(msg -> msg.contains("backend")),
                "with no backend registered at all, validateTrade must flag it (D1: fail-closed, not a silent pass)");
    }

    @Test
    void stageAndUndoRoundTrip() {
        TradeEntry e = villagerEntry();
        TradeExporter.stageTrade(e);
        assertSame(e, TradeExporter.getEntry(e.id));

        TradeEntry edited = e.copy();
        edited.level = 3;
        TradeExporter.stageTrade(edited);
        assertEquals(3, TradeExporter.getEntry(e.id).level);

        assertTrue(TradeExporter.canUndo(e.id));
        TradeEntry restored = TradeExporter.undo(e.id);
        assertEquals(2, restored.level, "undo restores the level-2 draft pushed to history before the level-3 stage");
    }

    private TradeOffer validOffer() { return villagerEntry().offers.get(0); }

    @Test
    void validateOfferAcceptsAWellFormedOffer() {
        assertTrue(TradeExporter.validateOffer(validOffer(), new KveNativeTradeBackend()).isEmpty());
    }

    @Test
    void validateOfferRejectsZeroMaxUses() {
        TradeOffer o = validOffer();
        o.maxUses = 0;
        assertTrue(TradeExporter.validateOffer(o, new KveNativeTradeBackend()).stream().anyMatch(m -> m.contains("Max uses")));
    }

    @Test
    void validateOfferRejectsNegativeXp() {
        TradeOffer o = validOffer();
        o.villagerXp = -1;
        assertTrue(TradeExporter.validateOffer(o, new KveNativeTradeBackend()).stream().anyMatch(m -> m.contains("XP")));
    }

    @Test
    void validateOfferRejectsNaNInfiniteAndNegativePriceMultiplier() {
        for (float bad : new float[]{Float.NaN, Float.POSITIVE_INFINITY, -0.5f}) {
            TradeOffer o = validOffer();
            o.priceMultiplier = bad;
            assertTrue(TradeExporter.validateOffer(o, new KveNativeTradeBackend()).stream().anyMatch(m -> m.contains("Price multiplier")),
                    "priceMultiplier=" + bad);
        }
    }

    @Test
    void validateOfferAcceptsZeroPriceMultiplierAndZeroXp() {
        TradeOffer o = validOffer();
        o.priceMultiplier = 0f;
        o.villagerXp = 0;
        assertTrue(TradeExporter.validateOffer(o, new KveNativeTradeBackend()).isEmpty());
    }

    @Test
    void validateOfferRejectsUnparseableOutputNbtButAcceptsValidOne() {
        TradeOffer o = validOffer();
        o.outputNbt = "{Damage:";
        assertFalse(TradeExporter.validateOffer(o, new KveNativeTradeBackend()).isEmpty(), "broken SNBT must be reported");
        o.outputNbt = "{Damage:5}";
        assertTrue(TradeExporter.validateOffer(o, new KveNativeTradeBackend()).isEmpty());
    }

    @Test
    void validateOfferRejectsCountAboveItemMaxStackSize() {
        TradeOffer o = validOffer();
        o.inputACount = 65;
        assertTrue(TradeExporter.validateOffer(o, new KveNativeTradeBackend()).stream().anyMatch(m -> m.contains("stack maximo")));
        o.inputACount = 64;
        assertTrue(TradeExporter.validateOffer(o, new KveNativeTradeBackend()).isEmpty());

        o.outputId = "minecraft:enchanted_book";
        o.outputCount = 2;
        assertTrue(TradeExporter.validateOffer(o, new KveNativeTradeBackend()).stream().anyMatch(m -> m.contains("stack maximo")));
    }

    @Test
    void findConflictsFlagsSecondReplaceLevelOnSameProfessionAndLevel() {
        TradeEntry a = villagerEntry();
        a.id = "shovel_js:a";
        TradeEntry b = villagerEntry();
        b.id = "shovel_js:b";
        TradeExporter.stageTrade(a);
        TradeExporter.stageTrade(b);

        assertEquals(List.of("shovel_js:a"), TradeExporter.findConflicts(b));
        assertEquals(List.of("shovel_js:b"), TradeExporter.findConflicts(a));
    }

    @Test
    void findConflictsIgnoresOtherLevelDisabledOrNonReplaceEntries() {
        TradeEntry a = villagerEntry();
        a.id = "shovel_js:a";
        TradeEntry otherLevel = villagerEntry();
        otherLevel.id = "shovel_js:lvl";
        otherLevel.level = 3;
        TradeEntry disabled = villagerEntry();
        disabled.id = "shovel_js:off";
        disabled.enabled = false;
        TradeEntry adder = villagerEntry();
        adder.id = "shovel_js:add";
        adder.mode = TradeEditMode.ADD_OFFERS;
        TradeExporter.stageTrade(otherLevel);
        TradeExporter.stageTrade(disabled);
        TradeExporter.stageTrade(adder);
        TradeExporter.stageTrade(a);

        assertTrue(TradeExporter.findConflicts(a).isEmpty());
    }
}
