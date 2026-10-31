package com.kve.common.trade;

import com.kve.CraftEditorMod;
import com.kve.common.kubejs.TradeExporter;
import com.kve.common.kubejs.TradeExporter.TradeEditMode;
import com.kve.common.kubejs.TradeExporter.TradeEntry;
import com.kve.common.kubejs.TradeExporter.TradeOffer;
import com.kve.common.kubejs.TradeExporter.TradeTargetKind;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class MerchantOfferInjector {

    private MerchantOfferInjector() {}

    static final String PD_KEY = "shovel_js_trade_sigs";
    static final String PD_ORIG_KEY = "shovel_js_trade_orig";

    private static final Map<String, TradeEntry> DIRECT = new ConcurrentHashMap<>();

    static void register(TradeEntry entry) { DIRECT.put(entry.id, entry.copy()); }

    static boolean unregister(String entryId) { return DIRECT.remove(entryId) != null; }

    static Set<String> registeredIds() { return new HashSet<>(DIRECT.keySet()); }

    static void clearForTests() { DIRECT.clear(); }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) return;
        Entity target = event.getTarget();
        if (!(target instanceof Merchant merchant)) return;
        try {
            sync(target, merchant);
        } catch (Exception ex) {
            CraftEditorMod.LOGGER.error("[Shovel JS] No se pudieron sincronizar las ofertas Shovel JS de {}: {}", target.getType(), ex.toString());
        }
    }

    static List<TradeEntry> entriesFor(Entity target) {
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
        if (key == null || DIRECT.isEmpty()) return List.of();
        String typeId = key.toString();
        String villagerProf = target instanceof Villager v
                ? String.valueOf(BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().getProfession()))
                : null;
        List<TradeEntry> out = new ArrayList<>();
        String prof = null;
        for (TradeEntry e : DIRECT.values()) {
            if (!e.enabled) continue;
            if (e.targetKind == TradeTargetKind.VILLAGER) {
                if (villagerProf != null && villagerProf.equals(e.professionId)) out.add(e);
                continue;
            }
            if (e.targetKind == TradeTargetKind.WANDERING_TRADER) {
                if (target instanceof net.minecraft.world.entity.npc.WanderingTrader) {
                    TradeEntry edits = e.copy();
                    edits.offers.removeIf(o -> !o.isEdit());
                    out.add(edits);
                }
                continue;
            }
            if (e.targetKind != TradeTargetKind.ENTITY || !typeId.equals(e.entityTypeId)) continue;
            if (KveNativeTradeBackend.usesProfessionTable(e)) {
                if (villagerProf != null && villagerProf.equals(e.professionId)) out.add(e);
                continue;
            }
            if (e.professionId != null && !e.professionId.isBlank()) {
                if (prof == null) prof = MerchantTypeDetector.professionOf(target, TradeTypeDescriptorRegistry.professionGetter(typeId));
                if (!e.professionId.equals(prof)) continue;
            }
            out.add(e);
        }
        return out;
    }

    static void sync(Entity target, Merchant merchant) {
        List<TradeEntry> entries = entriesFor(target);
        CompoundTag pd = target.getPersistentData();
        Set<String> previous = readSigs(pd);
        if (entries.isEmpty() && previous.isEmpty()) return;
        MerchantOffers offers = merchant.getOffers();
        if (offers == null) return;
        Map<String, CompoundTag> originals = readOriginals(pd);
        int level = target instanceof Villager v ? v.getVillagerData().getLevel() : 0;
        Set<String> desired = applyTo(offers, entries, previous, originals, level);
        writeSigs(pd, desired);
        writeOriginals(pd, originals);
    }

    static Set<String> applyTo(List<MerchantOffer> offers, List<TradeEntry> entries, Set<String> previous) {
        return applyTo(offers, entries, previous, new HashMap<>(), 0);
    }

    private record Desired(String sig, MerchantOffer offer, int level, TradeOffer edit) {}

    static Set<String> applyTo(List<MerchantOffer> offers, List<TradeEntry> entries, Set<String> previous,
                               Map<String, CompoundTag> originals, int merchantLevel) {
        KveNativeTradeBackend converter = new KveNativeTradeBackend();
        Map<String, Desired> adds = new LinkedHashMap<>();
        List<Desired> edits = new ArrayList<>();
        List<TradeOffer> removeFilters = new ArrayList<>();
        boolean clearOthers = false;
        for (TradeEntry e : entries) {
            switch (e.mode) {
                case REPLACE_LEVEL, ADD_OFFERS -> {
                    if (e.mode == TradeEditMode.REPLACE_LEVEL) clearOthers = true;
                    boolean levelled = TradeExporter.hasLevels(e);
                    for (TradeOffer o : e.offers) {
                        if (!o.enabled) continue;
                        VillagerTrades.ItemListing l = converter.toListing(e.id, o);
                        if (l == null) continue;
                        MerchantOffer mo = l.getOffer(null, RandomSource.create());
                        if (mo == null) continue;
                        String sig = signature(mo);
                        if (o.isEdit() && e.mode == TradeEditMode.ADD_OFFERS) {
                            edits.add(new Desired(sig, mo, 0, o));
                            continue;
                        }
                        int lvl = levelled ? o.effectiveLevel(e.level) : 0;
                        if (merchantLevel > 0 && lvl > merchantLevel) continue;
                        adds.putIfAbsent(sig, new Desired(sig, mo, lvl, null));
                    }
                }
                case REMOVE_MATCHING -> {
                    for (TradeOffer o : e.offers) if (o.enabled && o.inputAId != null && !o.inputAId.isBlank()) removeFilters.add(o);
                }
                case CLEAR_ENTITY -> clearOthers = true;
                case CLEAR_PROFESSION -> { }
            }
        }
        Set<String> wanted = new HashSet<>(adds.keySet());
        for (Desired d : edits) wanted.add(d.sig());

        for (int i = offers.size() - 1; i >= 0; i--) {
            MerchantOffer o = offers.get(i);
            String s = signature(o);
            if (wanted.contains(s)) continue;
            if (previous.contains(s)) {
                CompoundTag orig = originals.remove(s);
                MerchantOffer restored = orig == null ? null : readOffer(orig);
                if (restored != null) offers.set(i, restored);
                else offers.remove(i);
                continue;
            }
            if (clearOthers || matchesAny(o, removeFilters)) offers.remove(i);
        }
        originals.keySet().removeIf(s -> !wanted.contains(s));

        Set<String> owned = new HashSet<>(adds.keySet());

        for (Desired d : edits) {
            if (indexOfSig(offers, d.sig()) >= 0) {
                if (originals.containsKey(d.sig())) owned.add(d.sig());
                continue;
            }
            int idx = -1;
            for (int i = 0; i < offers.size(); i++) {
                MerchantOffer o = offers.get(i);
                String s = signature(o);
                if (owned.contains(s) || previous.contains(s)) continue;
                if (matchesEditTarget(o, d.edit())) { idx = i; break; }
            }
            if (idx < 0) continue;
            MerchantOffer orig = offers.get(idx);
            originals.put(d.sig(), orig.createTag());
            offers.set(idx, copy(d.offer()));
            owned.add(d.sig());
        }

        Set<String> present = new HashSet<>();
        for (MerchantOffer o : offers) present.add(signature(o));
        List<Desired> missing = new ArrayList<>();
        for (Desired d : adds.values()) if (!present.contains(d.sig())) missing.add(d);
        missing.sort(java.util.Comparator.comparingInt(Desired::level));
        for (Desired d : missing) {
            int at = (merchantLevel > 0 && d.level() > 0) ? insertIndex(offers, adds.keySet(), d.level()) : offers.size();
            offers.add(at, copy(d.offer()));
        }
        return owned;
    }

    static int insertIndex(List<MerchantOffer> offers, Set<String> kveAddSigs, int level) {
        int limit = 2 * level, nativeSeen = 0, i = 0;
        for (; i < offers.size(); i++) {
            if (kveAddSigs.contains(signature(offers.get(i)))) continue;
            if (nativeSeen >= limit) break;
            nativeSeen++;
        }
        return i;
    }

    private static int indexOfSig(List<MerchantOffer> offers, String sig) {
        for (int i = 0; i < offers.size(); i++) if (signature(offers.get(i)).equals(sig)) return i;
        return -1;
    }

    static boolean matchesEditTarget(MerchantOffer o, TradeOffer edit) {
        String b = edit.editInputBId == null ? "" : edit.editInputBId;
        return edit.editInputAId.equals(stackId(o.getBaseCostA()))
                && b.equals(stackId(o.getCostB()))
                && edit.editOutputId.equals(stackId(o.getResult()));
    }

    private static MerchantOffer readOffer(CompoundTag tag) {
        try {
            return new MerchantOffer(tag);
        } catch (Exception ex) {
            return null;
        }
    }

    private static MerchantOffer copy(MerchantOffer o) {
        return new MerchantOffer(o.getBaseCostA().copy(), o.getCostB().copy(), o.getResult().copy(),
                o.getMaxUses(), o.getXp(), o.getPriceMultiplier());
    }

    private static boolean matchesAny(MerchantOffer o, List<TradeOffer> filters) {
        if (filters.isEmpty()) return false;
        String a = stackId(o.getBaseCostA()), b = stackId(o.getCostB()), out = stackId(o.getResult());
        for (TradeOffer f : filters) {
            if (!f.inputAId.equals(a)) continue;
            if (f.inputBId != null && !f.inputBId.isBlank() && !f.inputBId.equals(b)) continue;
            if (f.outputId != null && !f.outputId.isBlank() && !f.outputId.equals(out)) continue;
            return true;
        }
        return false;
    }

    static String signature(MerchantOffer o) {
        return side(o.getBaseCostA()) + "|" + side(o.getCostB()) + "|" + side(o.getResult())
                + (o.getResult().hasTag() ? "|" + o.getResult().getTag() : "");
    }

    private static String side(ItemStack s) {
        return s == null || s.isEmpty() ? "-" : stackId(s) + "x" + s.getCount();
    }

    private static String stackId(ItemStack s) {
        if (s == null || s.isEmpty()) return "";
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(s.getItem());
        return key == null ? "" : key.toString();
    }

    private static Set<String> readSigs(CompoundTag pd) {
        Set<String> out = new HashSet<>();
        ListTag list = pd.getList(PD_KEY, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) out.add(list.getString(i));
        return out;
    }

    private static void writeSigs(CompoundTag pd, Set<String> sigs) {
        if (sigs.isEmpty()) { pd.remove(PD_KEY); return; }
        ListTag list = new ListTag();
        for (String s : sigs) list.add(StringTag.valueOf(s));
        pd.put(PD_KEY, list);
    }

    private static Map<String, CompoundTag> readOriginals(CompoundTag pd) {
        Map<String, CompoundTag> out = new HashMap<>();
        CompoundTag c = pd.getCompound(PD_ORIG_KEY);
        for (String k : c.getAllKeys()) out.put(k, c.getCompound(k));
        return out;
    }

    private static void writeOriginals(CompoundTag pd, Map<String, CompoundTag> originals) {
        if (originals.isEmpty()) { pd.remove(PD_ORIG_KEY); return; }
        CompoundTag c = new CompoundTag();
        originals.forEach(c::put);
        pd.put(PD_ORIG_KEY, c);
    }

    static void forgetOwnership(Entity target) {
        CompoundTag pd = target.getPersistentData();
        pd.remove(PD_KEY);
        pd.remove(PD_ORIG_KEY);
    }
}
