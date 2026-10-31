package com.kve.common.trade;

import com.kve.common.kubejs.TradeExporter.TradeEntry;
import com.kve.common.kubejs.TradeExporter.TradeOffer;
import com.kve.common.kubejs.TradeExporter.TradeTargetKind;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ExistingTradeProbe {

    private ExistingTradeProbe() {}

    /** Ofertas leidas y cuantas no se pudieron leer sin un comerciante real. */
    public record Result(List<TradeOffer> offers, int skipped) {
        static Result empty() { return new Result(List.of(), 0); }
    }

    /** Lee los trades existentes del destino de {@code draft} usando {@code level} para crear el comerciante. */
    public static Result probe(TradeEntry draft, Level level) {
        if (draft == null) return Result.empty();
        if (draft.targetKind == TradeTargetKind.WANDERING_TRADER) return probeWandering(level);
        if (draft.targetKind == TradeTargetKind.VILLAGER || KveNativeTradeBackend.usesProfessionTable(draft)) {
            return probeProfession(draft.professionId, level);
        }
        if (draft.targetKind == TradeTargetKind.ENTITY) return probeMerchantEntity(draft.entityTypeId, level);
        return Result.empty();
    }

    static Result probeProfession(String professionId, Level level) {
        VillagerProfession profession = profession(professionId);
        if (profession == null) return Result.empty();
        Int2ObjectMap<VillagerTrades.ItemListing[]> byLevel = VillagerTrades.TRADES.get(profession);
        if (byLevel == null) return Result.empty();
        Villager trader = null;
        try {
            if (level != null) trader = EntityType.VILLAGER.create(level);
        } catch (Throwable ignored) { }

        List<TradeOffer> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int skipped = 0;
        for (int lvl = 1; lvl <= 5; lvl++) {
            VillagerTrades.ItemListing[] listings = byLevel.get(lvl);
            if (listings == null) continue;
            if (trader != null) {
                try {
                    trader.setVillagerData(trader.getVillagerData().setProfession(profession).setLevel(lvl));
                } catch (Throwable ignored) {}
            }
            for (VillagerTrades.ItemListing l : listings) {
                TradeOffer o = sample(l, trader);
                if (o == null) { skipped++; continue; }
                o.unlockOffset = lvl - 1;
                if (seen.add(key(o))) out.add(o);
            }
        }
        return new Result(out, skipped);
    }

    static Result probeWandering(Level level) {
        Entity trader = null;
        try {
            if (level != null) trader = EntityType.WANDERING_TRADER.create(level);
        } catch (Throwable ignored) {}
        List<TradeOffer> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int skipped = 0;
        for (int tier = 1; tier <= 2; tier++) {
            VillagerTrades.ItemListing[] listings = VillagerTrades.WANDERING_TRADER_TRADES.get(tier);
            if (listings == null) continue;
            for (VillagerTrades.ItemListing l : listings) {
                TradeOffer o = sample(l, trader);
                if (o == null) { skipped++; continue; }
                if (seen.add(key(o))) out.add(o);
            }
        }
        return new Result(out, skipped);
    }

    static Result probeMerchantEntity(String entityTypeId, Level level) {
        ResourceLocation rl = entityTypeId == null ? null : ResourceLocation.tryParse(entityTypeId);
        if (rl == null || level == null) return Result.empty();
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(rl).orElse(null);
        if (type == null) return Result.empty();
        Entity e;
        try {
            e = type.create(level);
        } catch (Throwable t) {
            return Result.empty();
        }
        if (!(e instanceof Merchant merchant)) return Result.empty();
        MerchantOffers offers;
        try {
            offers = merchant.getOffers();
        } catch (Throwable t) {
            return new Result(List.of(), 1);
        }
        List<TradeOffer> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (offers != null) {
            for (MerchantOffer mo : offers) {
                TradeOffer o = fromOffer(mo);
                if (o != null && seen.add(key(o))) out.add(o);
            }
        }
        return new Result(out, 0);
    }

    private static TradeOffer sample(VillagerTrades.ItemListing l, Entity trader) {
        if (l instanceof KveNativeTradeBackend.KveManagedListing) return null;
        // Busca una estructura en el mundo real: lento y sin sentido para editar.
        if (l.getClass().getName().contains("TreasureMap")) return null;
        MerchantOffer mo;
        try {
            mo = l.getOffer(trader, RandomSource.create());
        } catch (Throwable t) {
            try {
                mo = trader == null ? null : l.getOffer(null, RandomSource.create());
            } catch (Throwable t2) {
                return null;
            }
        }
        return fromOffer(mo);
    }

    public static TradeOffer fromOffer(MerchantOffer mo) {
        if (mo == null || mo.getResult().isEmpty() || mo.getBaseCostA().isEmpty()) return null;
        TradeOffer o = new TradeOffer();
        o.inputAId = stackId(mo.getBaseCostA());
        o.inputACount = mo.getBaseCostA().getCount();
        o.inputBId = stackId(mo.getCostB());
        o.inputBCount = Math.max(1, mo.getCostB().getCount());
        o.outputId = stackId(mo.getResult());
        o.outputCount = mo.getResult().getCount();
        o.outputNbt = com.kve.common.util.ItemExtraDataCodec.encodeOrEmpty(mo.getResult());
        o.maxUses = mo.getMaxUses();
        o.villagerXp = mo.getXp();
        o.priceMultiplier = mo.getPriceMultiplier();
        o.editInputAId = o.inputAId;
        o.editInputBId = o.inputBId;
        o.editOutputId = o.outputId;
        if (o.inputAId.isEmpty() || o.outputId.isEmpty()) return null;
        return o;
    }

    private static String key(TradeOffer o) {
        return o.editInputAId + "|" + o.editInputBId + "|" + o.editOutputId;
    }

    private static String stackId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }

    private static VillagerProfession profession(String id) {
        ResourceLocation rl = id == null || id.isBlank() ? null : ResourceLocation.tryParse(id);
        return rl == null ? null : BuiltInRegistries.VILLAGER_PROFESSION.get(rl);
    }
}
