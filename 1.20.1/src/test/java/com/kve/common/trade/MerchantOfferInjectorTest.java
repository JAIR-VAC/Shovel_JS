package com.kve.common.trade;

import com.kve.common.kubejs.TradeExporter;
import com.kve.common.kubejs.TradeExporter.TradeEditMode;
import com.kve.common.kubejs.TradeExporter.TradeEntry;
import com.kve.common.kubejs.TradeExporter.TradeOffer;
import com.kve.common.kubejs.TradeExporter.TradeTargetKind;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comerciantes modded: ofertas directas sobre la lista de
 * ofertas de una entidad Merchant (ver {@link MerchantOfferInjector}) y
 * enrutamiento ENTITY del backend nativo segun el tipo detectado.
 */
class MerchantOfferInjectorTest {

    private final KveNativeTradeBackend backend = new KveNativeTradeBackend();

    @AfterEach
    void tearDown() {
        MerchantOfferInjector.clearForTests();
        MerchantTypeDetector.clearCache();
        TradeTypeDescriptorRegistry.resetForTests();
    }

    private static TradeOffer offer(String in, int inCount, String out) {
        TradeOffer o = new TradeOffer();
        o.inputAId = in;
        o.inputACount = inCount;
        o.outputId = out;
        o.outputCount = 1;
        o.maxUses = 8;
        o.villagerXp = 2;
        o.priceMultiplier = 0.05f;
        return o;
    }

    private static TradeEntry entity(String id, TradeEditMode mode, TradeOffer... offers) {
        TradeEntry e = new TradeEntry();
        e.id = id;
        e.targetKind = TradeTargetKind.ENTITY;
        e.entityTypeId = "ribbits:ribbit";
        e.mode = mode;
        e.offers.addAll(List.of(offers));
        return e;
    }

    private static MerchantOffer native1() {
        return new MerchantOffer(new ItemStack(Items.EMERALD, 3), ItemStack.EMPTY, new ItemStack(Items.LILY_PAD, 4), 10, 1, 0.05f);
    }

    @Test
    void addOffersAppendsWithoutTouchingModOffersAndIsIdempotentKeepingUses() {
        List<MerchantOffer> offers = new ArrayList<>(List.of(native1()));
        TradeEntry add = entity("shovel_js:ribbit_add", TradeEditMode.ADD_OFFERS, offer("minecraft:diamond", 2, "minecraft:golden_apple"));

        Set<String> owned = MerchantOfferInjector.applyTo(offers, List.of(add), Set.of());
        assertEquals(2, offers.size(), "mod offer kept + Shovel JS offer added");
        assertEquals(1, owned.size());

        MerchantOffer kve = offers.get(1);
        kve.increaseUses();
        Set<String> owned2 = MerchantOfferInjector.applyTo(offers, List.of(add), owned);
        assertEquals(2, offers.size(), "re-sync must not duplicate");
        assertSame(kve, offers.get(1), "existing Shovel JS offer kept as-is");
        assertEquals(1, offers.get(1).getUses(), "uses preserved across syncs");
        assertEquals(owned, owned2);
    }

    @Test
    void removedEntryDropsOnlyItsOwnOffersOnNextSync() {
        List<MerchantOffer> offers = new ArrayList<>(List.of(native1()));
        TradeEntry add = entity("shovel_js:ribbit_add", TradeEditMode.ADD_OFFERS, offer("minecraft:diamond", 2, "minecraft:golden_apple"));
        Set<String> owned = MerchantOfferInjector.applyTo(offers, List.of(add), Set.of());

        Set<String> after = MerchantOfferInjector.applyTo(offers, List.of(), owned);
        assertTrue(after.isEmpty());
        assertEquals(1, offers.size());
        assertEquals(Items.LILY_PAD, offers.get(0).getResult().getItem(), "mod's own offer untouched");
    }

    @Test
    void replaceLevelAndClearEntityRemoveModOffers() {
        List<MerchantOffer> offers = new ArrayList<>(List.of(native1()));
        TradeEntry rep = entity("shovel_js:ribbit_rep", TradeEditMode.REPLACE_LEVEL, offer("minecraft:iron_ingot", 5, "minecraft:emerald"));
        MerchantOfferInjector.applyTo(offers, List.of(rep), Set.of());
        assertEquals(1, offers.size());
        assertEquals(Items.EMERALD, offers.get(0).getResult().getItem());

        List<MerchantOffer> offers2 = new ArrayList<>(List.of(native1()));
        TradeEntry clear = entity("shovel_js:ribbit_clear", TradeEditMode.CLEAR_ENTITY);
        MerchantOfferInjector.applyTo(offers2, List.of(clear), Set.of());
        assertTrue(offers2.isEmpty());
    }

    @Test
    void removeMatchingRemovesModOfferByInputAndOutput() {
        List<MerchantOffer> offers = new ArrayList<>(List.of(native1()));
        TradeOffer f = offer("minecraft:emerald", 1, "minecraft:lily_pad");
        MerchantOfferInjector.applyTo(offers, List.of(entity("shovel_js:rm", TradeEditMode.REMOVE_MATCHING, f)), Set.of());
        assertTrue(offers.isEmpty());

        List<MerchantOffer> offers2 = new ArrayList<>(List.of(native1()));
        TradeOffer other = offer("minecraft:emerald", 1, "minecraft:stick");
        MerchantOfferInjector.applyTo(offers2, List.of(entity("shovel_js:rm2", TradeEditMode.REMOVE_MATCHING, other)), Set.of());
        assertEquals(1, offers2.size(), "output filter must match too");
    }

    private static TradeEntry villager(String id, int level, TradeOffer... offers) {
        TradeEntry e = new TradeEntry();
        e.id = id;
        e.targetKind = TradeTargetKind.VILLAGER;
        e.professionId = "minecraft:farmer";
        e.level = level;
        e.mode = TradeEditMode.ADD_OFFERS;
        e.offers.addAll(List.of(offers));
        return e;
    }

    private static MerchantOffer vanilla(net.minecraft.world.item.Item in, net.minecraft.world.item.Item out) {
        return new MerchantOffer(new ItemStack(in, 20), ItemStack.EMPTY, new ItemStack(out, 1), 16, 2, 0.05f);
    }

    private static List<MerchantOffer> farmerLevel2() {
        return new ArrayList<>(List.of(
                vanilla(Items.WHEAT, Items.EMERALD), vanilla(Items.POTATO, Items.EMERALD),
                vanilla(Items.PUMPKIN, Items.EMERALD), vanilla(Items.EMERALD, Items.PUMPKIN_PIE)));
    }

    @Test
    void villagerAddKeepsAllExistingOffersAndInsertsNextToItsLevel() {
        List<MerchantOffer> offers = farmerLevel2();
        TradeEntry add = villager("shovel_js:farmer_l1", 1, offer("minecraft:diamond", 1, "minecraft:golden_carrot"));

        MerchantOfferInjector.applyTo(offers, List.of(add), Set.of(), new java.util.HashMap<>(), 2);
        assertEquals(5, offers.size(), "the new trade must not make the others disappear");
        assertEquals(Items.GOLDEN_CARROT, offers.get(2).getResult().getItem(), "placed right after the 2 level-1 offers");
        assertEquals(Items.PUMPKIN_PIE, offers.get(4).getResult().getItem(), "level-2 offers kept after it");
    }

    @Test
    void unlockOffsetHidesTheOfferUntilTheVillagerReachesThatLevel() {
        TradeOffer later = offer("minecraft:diamond", 1, "minecraft:golden_carrot");
        later.unlockOffset = 2;
        TradeEntry add = villager("shovel_js:farmer_later", 1, later);

        List<MerchantOffer> offers = farmerLevel2();
        Set<String> owned = MerchantOfferInjector.applyTo(offers, List.of(add), Set.of(), new java.util.HashMap<>(), 2);
        assertEquals(4, offers.size(), "a level-2 villager must not get a level-3 unlock yet");
        assertTrue(owned.isEmpty());

        offers.add(vanilla(Items.CARROT, Items.EMERALD));
        MerchantOfferInjector.applyTo(offers, List.of(add), owned, new java.util.HashMap<>(), 3);
        assertEquals(6, offers.size());
        assertEquals(Items.GOLDEN_CARROT, offers.get(5).getResult().getItem());
    }

    @Test
    void editReplacesTheOriginalInPlaceAndRestoresItWhenRemoved() {
        List<MerchantOffer> offers = farmerLevel2();
        TradeOffer edit = offer("minecraft:wheat", 5, "minecraft:diamond");
        edit.editInputAId = "minecraft:wheat";
        edit.editInputBId = "";
        edit.editOutputId = "minecraft:emerald";
        TradeEntry e = villager("shovel_js:farmer_edit", 1, edit);
        java.util.Map<String, net.minecraft.nbt.CompoundTag> originals = new java.util.HashMap<>();

        Set<String> owned = MerchantOfferInjector.applyTo(offers, List.of(e), Set.of(), originals, 2);
        assertEquals(4, offers.size(), "edit replaces, never adds");
        assertEquals(Items.DIAMOND, offers.get(0).getResult().getItem(), "same slot as the original");
        assertEquals(1, owned.size());
        assertEquals(1, originals.size());

        MerchantOfferInjector.applyTo(offers, List.of(e), owned, originals, 2);
        assertEquals(4, offers.size());
        assertEquals(Items.DIAMOND, offers.get(0).getResult().getItem());

        Set<String> after = MerchantOfferInjector.applyTo(offers, List.of(), owned, originals, 2);
        assertTrue(after.isEmpty());
        assertTrue(originals.isEmpty());
        assertEquals(4, offers.size());
        assertEquals(Items.WHEAT, offers.get(0).getBaseCostA().getItem());
        assertEquals(Items.EMERALD, offers.get(0).getResult().getItem());
        assertEquals(20, offers.get(0).getBaseCostA().getCount());
    }

    @Test
    void unchangedEditIsNotClaimedSoRemovingItNeverDeletesTheOriginal() {
        List<MerchantOffer> offers = farmerLevel2();
        TradeOffer same = offer("minecraft:wheat", 20, "minecraft:emerald");
        same.maxUses = 16;
        same.editInputAId = "minecraft:wheat";
        same.editOutputId = "minecraft:emerald";
        Set<String> owned = MerchantOfferInjector.applyTo(offers, List.of(villager("shovel_js:same", 1, same)), Set.of(), new java.util.HashMap<>(), 2);
        assertTrue(owned.isEmpty());
        MerchantOfferInjector.applyTo(offers, List.of(), owned, new java.util.HashMap<>(), 2);
        assertEquals(4, offers.size());
    }

    @Test
    void moddedMerchantEntityRoutesToDirectOffersAndCanBeSavedWithoutDescriptor() {
        MerchantTypeDetector.putForTests("somemod:frog_trader", MerchantTypeDetector.Kind.MERCHANT);
        TradeEntry e = entity("shovel_js:frog", TradeEditMode.ADD_OFFERS, offer("minecraft:diamond", 1, "minecraft:emerald"));
        e.entityTypeId = "somemod:frog_trader";
        e.professionId = "somemod:fisherman";

        assertTrue(KveNativeTradeBackend.usesDirectOffers(e));
        assertFalse(KveNativeTradeBackend.usesProfessionTable(e));
        assertTrue(backend.supportsEntry(e), "a detected modded Merchant must be supported without any JSON descriptor");

        backend.applyRuntime(e);
        assertTrue(MerchantOfferInjector.registeredIds().contains("shovel_js:frog"));
        e.enabled = false;
        backend.applyRuntime(e);
        assertFalse(MerchantOfferInjector.registeredIds().contains("shovel_js:frog"), "disable retracts the direct entry");
    }

    @Test
    void villagerLikeEntityWithVanillaProfessionUsesTheProfessionTable() {
        MerchantTypeDetector.putForTests("somemod:guard_villager", MerchantTypeDetector.Kind.VILLAGER_LIKE);
        TradeEntry e = entity("shovel_js:guard", TradeEditMode.REPLACE_LEVEL, offer("minecraft:diamond", 1, "minecraft:emerald"));
        e.entityTypeId = "somemod:guard_villager";
        e.professionId = "minecraft:armorer";
        assertTrue(KveNativeTradeBackend.usesProfessionTable(e));
        assertTrue(backend.supportsEntry(e));

        e.professionId = "";
        assertTrue(KveNativeTradeBackend.usesDirectOffers(e), "no profession = direct offers on every instance");
    }

    @Test
    void nonMerchantEntityIsRejectedWithAClearMessage() {
        MerchantTypeDetector.putForTests("minecraft:zombie", MerchantTypeDetector.Kind.NOT_MERCHANT);
        TradeEntry e = entity("shovel_js:zombie", TradeEditMode.ADD_OFFERS, offer("minecraft:diamond", 1, "minecraft:emerald"));
        e.entityTypeId = "minecraft:zombie";
        assertFalse(backend.supportsEntry(e));
        List<String> errors = TradeExporter.validateTrade(e);
        assertTrue(errors.stream().anyMatch(s -> s.contains("no es un comerciante")), errors.toString());
    }

    @Test
    void ribbitsDescriptorTargetsTheRealEntityAndDeclaresItsJobs() throws Exception {
        TradeTypeDescriptorRegistry.resetForTests();
        TradeTypeDescriptorRegistry.setModLoadedCheckForTests(m -> true);
        try (var in = TradeTypeDescriptorRegistry.class.getResourceAsStream("/data/shovel_js/trade_types/ribbits.json");
             var r = new java.io.InputStreamReader(java.util.Objects.requireNonNull(in), java.nio.charset.StandardCharsets.UTF_8)) {
            for (var el : com.google.gson.JsonParser.parseReader(r).getAsJsonArray()) {
                TradeTypeDescriptorRegistry.registerForTests(new com.google.gson.Gson().fromJson(el, TradeTypeDescriptor.class));
            }
            var d = TradeTypeDescriptorRegistry.find("ribbits:ribbit");
            assertTrue(d.isPresent(), "Ribbits registers a single entity 'ribbits:ribbit' (verified against Ribbits-1.20.1-Forge-3.0.5.jar)");
            assertEquals("getRibbitData.getProfession", d.get().professionGetter);
            assertTrue(d.get().professions.contains("ribbits:merchant"));
            assertFalse(TradeTypeDescriptorRegistry.find("ribbits:merchant").isPresent(), "old descriptor pointed at non-existent entity ids");
            assertEquals(MerchantTypeDetector.Kind.MERCHANT, TradeTypeDescriptorRegistry.merchantKind("ribbits:ribbit"));
        } finally {
            TradeTypeDescriptorRegistry.setModLoadedCheckForTests(null);
        }
    }

    @Test
    void professionObjectToIdHandlesModdedProfessionShapes() {
        record FakeProf(net.minecraft.resources.ResourceLocation id) {
            public net.minecraft.resources.ResourceLocation getId() { return id; }
        }
        assertEquals("ribbits:merchant", MerchantTypeDetector.professionObjectToId(new FakeProf(new net.minecraft.resources.ResourceLocation("ribbits", "merchant"))));
        assertEquals("mymod:smith", MerchantTypeDetector.professionObjectToId("mymod:smith"));
        assertEquals("", MerchantTypeDetector.professionObjectToId(null));
    }
}
