package com.kve.common.trade;

import com.kve.common.kubejs.TradeExporter.TradeEditMode;
import com.kve.common.kubejs.TradeExporter.TradeEntry;
import com.kve.common.kubejs.TradeExporter.TradeOffer;
import com.kve.common.kubejs.TradeExporter.TradeTargetKind;
import net.minecraft.world.entity.npc.VillagerTrades;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KveNativeTradeBackendTest {

    private final KveNativeTradeBackend backend = new KveNativeTradeBackend();

    private TradeEntry villagerEntry(String id, TradeEditMode mode) {
        TradeEntry e = new TradeEntry();
        e.id = id;
        e.enabled = true;
        e.targetKind = TradeTargetKind.VILLAGER;
        e.professionId = "minecraft:armorer";
        e.level = 2;
        e.mode = mode;
        return e;
    }

    private TradeOffer offer(String inputId, String outputId) {
        TradeOffer o = new TradeOffer();
        o.inputAId = inputId;
        o.inputACount = 4;
        o.outputId = outputId;
        o.outputCount = 1;
        o.maxUses = 12;
        o.villagerXp = 10;
        o.priceMultiplier = 0.05f;
        return o;
    }

    @Test
    void supportsVillagerAndWanderingButNotBareEntityKind() {
        assertTrue(backend.supports(TradeTargetKind.VILLAGER));
        assertTrue(backend.supports(TradeTargetKind.WANDERING_TRADER));
        assertFalse(backend.supports(TradeTargetKind.ENTITY));
    }

    @Test
    void supportsEntryConsultsDescriptorRegistryForEntity() {
        TradeTypeDescriptorRegistry.resetForTests();
        try {
            TradeEntry unknown = new TradeEntry();
            unknown.targetKind = TradeTargetKind.ENTITY;
            unknown.entityTypeId = "somemod:unknown_merchant";
            assertFalse(backend.supportsEntry(unknown), "undeclared entity must never silently resolve to native");

            TradeTypeDescriptor d = new TradeTypeDescriptor();
            d.entityTypeId = "somemod:compatible_merchant";
            d.abstractVillagerCompatible = true;
            TradeTypeDescriptorRegistry.registerForTests(d);

            TradeEntry known = new TradeEntry();
            known.targetKind = TradeTargetKind.ENTITY;
            known.entityTypeId = "somemod:compatible_merchant";
            assertTrue(backend.supportsEntry(known));
        } finally {
            TradeTypeDescriptorRegistry.resetForTests();
        }
    }

    @Test
    void applyRuntimeWanderingReplaceLevelThenRetractOnDisable() {
        VillagerTrades.WANDERING_TRADER_TRADES.clear();
        try {
            TradeEntry e = new TradeEntry();
            e.id = "shovel_js:wander_generic";
            e.enabled = true;
            e.targetKind = TradeTargetKind.WANDERING_TRADER;
            e.level = 1;
            e.mode = TradeEditMode.REPLACE_LEVEL;
            e.offers.add(offer("minecraft:iron_ingot", "minecraft:emerald"));

            backend.applyRuntime(e);
            VillagerTrades.ItemListing[] afterApply = VillagerTrades.WANDERING_TRADER_TRADES.get(1);
            assertNotNull(afterApply);
            assertEquals(1, afterApply.length);

            e.enabled = false;
            backend.applyRuntime(e);
            VillagerTrades.ItemListing[] afterRetract = VillagerTrades.WANDERING_TRADER_TRADES.get(1);
            assertEquals(0, afterRetract == null ? 0 : afterRetract.length);
        } finally {
            VillagerTrades.WANDERING_TRADER_TRADES.clear();
            KveNativeTradeBackend.resetAppliedTrackingForTests();
        }
    }

    @Test
    void supportsModeMatchesReplaceAddRemoveClearProfessionAndClearEntity() {
        assertTrue(backend.supportsMode(TradeEditMode.REPLACE_LEVEL));
        assertTrue(backend.supportsMode(TradeEditMode.ADD_OFFERS));
        assertTrue(backend.supportsMode(TradeEditMode.REMOVE_MATCHING));
        assertTrue(backend.supportsMode(TradeEditMode.CLEAR_PROFESSION));
        assertTrue(backend.supportsMode(TradeEditMode.CLEAR_ENTITY));
    }

    @Test
    void emitIsAlwaysEmptySoNativeNeverLandsInTradesJs() {
        TradeEntry e = villagerEntry("shovel_js:x", TradeEditMode.REPLACE_LEVEL);
        e.offers.add(offer("minecraft:iron_ingot", "minecraft:iron_chestplate"));
        assertTrue(backend.emit(e, TradeEmitContext.of("  ")).isEmpty());
    }

    @Test
    void previewLinesIsHumanReadablePseudocodeNotEmpty() {
        TradeEntry e = villagerEntry("shovel_js:x", TradeEditMode.REPLACE_LEVEL);
        e.offers.add(offer("minecraft:iron_ingot", "minecraft:iron_chestplate"));
        List<String> lines = backend.previewLines(e, TradeEmitContext.of("  "));
        assertFalse(lines.isEmpty());
        String joined = String.join("\n", lines);
        assertTrue(joined.contains("REPLACE_LEVEL"));
        assertTrue(joined.contains("minecraft:iron_ingot"));
        assertTrue(joined.contains("minecraft:iron_chestplate"));
    }

    @Test
    void toListingSkipsUnknownItemIds() {
        TradeOffer bogus = offer("minecraft:this_item_does_not_exist", "minecraft:iron_chestplate");
        assertNull(backend.toListing("shovel_js:x", bogus), "unknown input item id must not produce a listing");
    }

    @Test
    void toListingBuildsAKveManagedListingTaggedWithEntryId() {
        TradeOffer o = offer("minecraft:iron_ingot", "minecraft:iron_chestplate");
        VillagerTrades.ItemListing listing = backend.toListing("shovel_js:armorer_l2", o);
        assertNotNull(listing);
        assertTrue(listing instanceof KveNativeTradeBackend.KveManagedListing);
        KveNativeTradeBackend.KveManagedListing kml = (KveNativeTradeBackend.KveManagedListing) listing;
        assertEquals("shovel_js:armorer_l2", kml.entryId);
        assertEquals("minecraft:iron_ingot", kml.inputAId);
    }

    @Test
    void buildListingsSkipsDisabledOffers() {
        TradeEntry e = villagerEntry("shovel_js:x", TradeEditMode.REPLACE_LEVEL);
        e.offers.add(offer("minecraft:iron_ingot", "minecraft:iron_chestplate"));
        TradeOffer disabled = offer("minecraft:emerald", "minecraft:diamond");
        disabled.enabled = false;
        e.offers.add(disabled);

        VillagerTrades.ItemListing[] listings = backend.buildListings(e);
        assertEquals(1, listings.length);
    }

    @Test
    void mergeAddIsIdempotentOnReapply() {
        TradeEntry e = villagerEntry("shovel_js:armorer_l2", TradeEditMode.ADD_OFFERS);
        e.offers.add(offer("minecraft:iron_ingot", "minecraft:iron_chestplate"));

        VillagerTrades.ItemListing[] afterFirstApply = backend.mergeAdd(null, e);
        assertEquals(1, afterFirstApply.length, "first Apply adds exactly this entry's one offer");

        VillagerTrades.ItemListing[] afterSecondApply = backend.mergeAdd(afterFirstApply, e);
        assertEquals(1, afterSecondApply.length,
                "re-Apply must not duplicate this entry's own previously-injected offer");
    }

    @Test
    void mergeAddPreservesVanillaAndOtherEntriesListings() {
        VillagerTrades.ItemListing vanilla = (trader, random) -> null;
        TradeEntry other = villagerEntry("shovel_js:other_entry", TradeEditMode.ADD_OFFERS);
        other.offers.add(offer("minecraft:emerald", "minecraft:diamond"));
        VillagerTrades.ItemListing otherListing = backend.toListing(other.id, other.offers.get(0));

        VillagerTrades.ItemListing[] existing = new VillagerTrades.ItemListing[]{vanilla, otherListing};

        TradeEntry e = villagerEntry("shovel_js:armorer_l2", TradeEditMode.ADD_OFFERS);
        e.offers.add(offer("minecraft:iron_ingot", "minecraft:iron_chestplate"));

        VillagerTrades.ItemListing[] merged = backend.mergeAdd(existing, e);
        assertEquals(3, merged.length, "vanilla + other entry's listing untouched, plus this entry's new one");

        VillagerTrades.ItemListing[] mergedAgain = backend.mergeAdd(merged, e);
        assertEquals(3, mergedAgain.length, "re-Apply still doesn't duplicate, and still doesn't touch the others");
    }

    @Test
    void removeMatchingStripsListingsByInputIdAndIsIdempotent() {
        TradeEntry adder = villagerEntry("shovel_js:armorer_l2", TradeEditMode.ADD_OFFERS);
        TradeOffer toRemoveOffer = offer("minecraft:iron_ingot", "minecraft:iron_chestplate");
        TradeOffer keepOffer = offer("minecraft:emerald", "minecraft:diamond");
        VillagerTrades.ItemListing toRemove = backend.toListing(adder.id, toRemoveOffer);
        VillagerTrades.ItemListing toKeep = backend.toListing(adder.id, keepOffer);
        VillagerTrades.ItemListing[] existing = new VillagerTrades.ItemListing[]{toRemove, toKeep};

        TradeEntry remover = villagerEntry("shovel_js:remover", TradeEditMode.REMOVE_MATCHING);
        remover.offers.add(offer("minecraft:iron_ingot", "minecraft:iron_chestplate"));

        VillagerTrades.ItemListing[] afterFirst = backend.removeMatching(existing, remover);
        assertEquals(1, afterFirst.length);
        assertSame(toKeep, afterFirst[0]);

        VillagerTrades.ItemListing[] afterSecond = backend.removeMatching(afterFirst, remover);
        assertEquals(1, afterSecond.length, "removing an already-absent match is a no-op, not an error");
    }

    @Test
    void toListingAppliesOutputNbtToTheResultStack() {
        TradeOffer o = offer("minecraft:iron_ingot", "minecraft:iron_chestplate");
        o.outputNbt = "{Damage:5}";

        KveNativeTradeBackend.KveManagedListing kml =
                (KveNativeTradeBackend.KveManagedListing) backend.toListing("shovel_js:x", o);

        assertNotNull(kml);
        assertNotNull(kml.result.getTag(), "outputNbt must reach the offer's result stack");
        assertEquals(5, kml.result.getTag().getInt("Damage"));
    }

    @Test
    void toListingWithoutOutputNbtLeavesResultTagNull() {
        KveNativeTradeBackend.KveManagedListing kml = (KveNativeTradeBackend.KveManagedListing)
                backend.toListing("shovel_js:x", offer("minecraft:iron_ingot", "minecraft:iron_chestplate"));
        assertNotNull(kml);
        var tag = kml.result.getTag();
        boolean noRealNbt = tag == null
                || (tag.size() == 1 && tag.contains("Damage") && tag.getInt("Damage") == 0);
        assertTrue(noRealNbt, "expected no meaningful NBT, got: " + tag);
    }

    @Test
    void applyOutputNbtIgnoresInvalidSnbtWithoutThrowing() {
        net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND);
        assertFalse(KveNativeTradeBackend.applyOutputNbt(stack, "{oops", "shovel_js:x"));
        assertNull(stack.getTag());
        assertFalse(KveNativeTradeBackend.applyOutputNbt(stack, "  ", "shovel_js:x"));
        assertTrue(KveNativeTradeBackend.applyOutputNbt(stack, "{Foo:1b}", "shovel_js:x"));
        assertNotNull(stack.getTag());
    }

    @Test
    void previewLinesShowsOutputNbt() {
        TradeEntry e = villagerEntry("shovel_js:x", TradeEditMode.REPLACE_LEVEL);
        TradeOffer o = offer("minecraft:iron_ingot", "minecraft:iron_chestplate");
        o.outputNbt = "{Damage:5}";
        e.offers.add(o);
        String joined = String.join("\n", backend.previewLines(e, TradeEmitContext.of("  ")));
        assertTrue(joined.contains("{Damage:5}"), joined);
    }

    private static VillagerTrades.ItemListing opaque(net.minecraft.world.item.Item cost, int n, net.minecraft.world.item.Item result) {
        return (trader, random) -> new net.minecraft.world.item.trading.MerchantOffer(
                new net.minecraft.world.item.ItemStack(cost, n), new net.minecraft.world.item.ItemStack(result), 16, 2, 0.05f);
    }

    @Test
    void removeMatchingRemovesOpaqueVanillaListingsByProbedOffer() {
        VillagerTrades.ItemListing wheat = opaque(net.minecraft.world.item.Items.WHEAT, 20, net.minecraft.world.item.Items.EMERALD);
        VillagerTrades.ItemListing coal = opaque(net.minecraft.world.item.Items.COAL, 15, net.minecraft.world.item.Items.EMERALD);

        TradeEntry remover = villagerEntry("shovel_js:remover", TradeEditMode.REMOVE_MATCHING);
        remover.offers.add(offer("minecraft:wheat", "minecraft:emerald"));

        VillagerTrades.ItemListing[] after = backend.removeMatching(new VillagerTrades.ItemListing[]{wheat, coal}, remover);

        assertEquals(1, after.length, "the vanilla wheat->emerald listing must be removed");
        assertSame(coal, after[0]);
    }

    @Test
    void removeMatchingHonorsOutputWhenFilterSpecifiesIt() {
        VillagerTrades.ItemListing wheat = opaque(net.minecraft.world.item.Items.WHEAT, 20, net.minecraft.world.item.Items.EMERALD);
        TradeEntry remover = villagerEntry("shovel_js:remover", TradeEditMode.REMOVE_MATCHING);
        remover.offers.add(offer("minecraft:wheat", "minecraft:diamond"));

        VillagerTrades.ItemListing[] after = backend.removeMatching(new VillagerTrades.ItemListing[]{wheat}, remover);

        assertEquals(1, after.length, "same input but different output must not match");
    }

    @Test
    void removeMatchingLeavesListingsThatCannotBeProbed() {
        VillagerTrades.ItemListing needsTrader = (trader, random) -> { throw new NullPointerException("trader.level()"); };
        TradeEntry remover = villagerEntry("shovel_js:remover", TradeEditMode.REMOVE_MATCHING);
        remover.offers.add(offer("minecraft:wheat", "minecraft:emerald"));

        VillagerTrades.ItemListing[] after = backend.removeMatching(new VillagerTrades.ItemListing[]{needsTrader}, remover);

        assertEquals(1, after.length);
    }
}
