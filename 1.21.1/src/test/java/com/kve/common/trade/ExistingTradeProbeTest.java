package com.kve.common.trade;

import com.kve.common.kubejs.TradeExporter;
import com.kve.common.kubejs.TradeExporter.TradeEditMode;
import com.kve.common.kubejs.TradeExporter.TradeEntry;
import com.kve.common.kubejs.TradeExporter.TradeOffer;
import com.kve.common.kubejs.TradeExporter.TradeTargetKind;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** "Editar existentes": conversion de ofertas y edicion del comerciante errante. */
class ExistingTradeProbeTest {

    @Test
    void existingOfferBecomesAnEditOfItself() {
        MerchantOffer mo = com.kve.common.trade.TradeCosts.offer(new ItemStack(Items.EMERALD, 3), ItemStack.EMPTY,
                new ItemStack(Items.BREAD, 6), 12, 2, 0.05f);
        TradeOffer o = ExistingTradeProbe.fromOffer(mo);
        assertNotNull(o);
        assertTrue(o.isEdit());
        assertEquals("minecraft:emerald", o.editInputAId);
        assertEquals("", o.editInputBId);
        assertEquals("minecraft:bread", o.editOutputId);
        assertEquals(3, o.inputACount);
        assertEquals(6, o.outputCount);
        assertEquals(12, o.maxUses);
    }

    @Test
    void offerWithoutResultIsNotEditable() {
        MerchantOffer mo = com.kve.common.trade.TradeCosts.offer(new ItemStack(Items.EMERALD), ItemStack.EMPTY, ItemStack.EMPTY, 1, 1, 0f);
        assertNull(ExistingTradeProbe.fromOffer(mo));
    }

    @Test
    void wanderingTraderEditsAreValidNow() {
        TradeEntry e = new TradeEntry();
        e.id = "shovel_js:wandering_edit";
        e.targetKind = TradeTargetKind.WANDERING_TRADER;
        e.entityTypeId = "minecraft:wandering_trader";
        e.level = 1;
        e.mode = TradeEditMode.ADD_OFFERS;
        TradeOffer o = ExistingTradeProbe.fromOffer(com.kve.common.trade.TradeCosts.offer(new ItemStack(Items.EMERALD, 5), ItemStack.EMPTY,
                new ItemStack(Items.BLUE_ICE), 6, 1, 0.05f));
        o.inputACount = 2;
        e.offers.add(o);
        List<String> errors = TradeExporter.validateTrade(e);
        assertTrue(errors.stream().noneMatch(s -> s.contains("errante")), errors.toString());
    }

    @Test
    void wanderingEditReplacesTheOriginalInPlace() {
        TradeEntry e = new TradeEntry();
        e.id = "shovel_js:w";
        e.targetKind = TradeTargetKind.WANDERING_TRADER;
        e.mode = TradeEditMode.ADD_OFFERS;
        TradeOffer edit = ExistingTradeProbe.fromOffer(com.kve.common.trade.TradeCosts.offer(new ItemStack(Items.EMERALD, 5), ItemStack.EMPTY,
                new ItemStack(Items.BLUE_ICE), 6, 1, 0.05f));
        edit.inputACount = 1;
        e.offers.add(edit);

        List<MerchantOffer> offers = new ArrayList<>();
        offers.add(com.kve.common.trade.TradeCosts.offer(new ItemStack(Items.EMERALD, 1), ItemStack.EMPTY, new ItemStack(Items.SAND, 8), 8, 1, 0.05f));
        offers.add(com.kve.common.trade.TradeCosts.offer(new ItemStack(Items.EMERALD, 5), ItemStack.EMPTY, new ItemStack(Items.BLUE_ICE), 6, 1, 0.05f));
        MerchantOfferInjector.applyTo(offers, List.of(e), java.util.Set.of(), new java.util.HashMap<>(), 0);
        assertEquals(2, offers.size());
        assertEquals(Items.BLUE_ICE, offers.get(1).getResult().getItem());
        assertEquals(1, offers.get(1).getBaseCostA().getCount(), "la edicion ocupa el lugar del original");
    }
}
