package com.kve.common.trade;

import com.kve.CraftEditorMod;
import com.kve.common.kubejs.TradeExporter.TradeEditMode;
import com.kve.common.kubejs.TradeExporter.TradeEntry;
import com.kve.common.kubejs.TradeExporter.TradeOffer;
import com.kve.common.kubejs.TradeExporter.TradeTargetKind;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class KveNativeTradeBackend implements TradeBackend {

    public static final String ID = "shovel_js_native";

    private static final Set<TradeEditMode> SUPPORTED_MODES = EnumSet.of(
            TradeEditMode.REPLACE_LEVEL, TradeEditMode.ADD_OFFERS,
            TradeEditMode.REMOVE_MATCHING, TradeEditMode.CLEAR_PROFESSION,
            TradeEditMode.CLEAR_ENTITY);

    @Override public String id() { return ID; }

    @Override public Component displayName() { return Component.translatable("gui.kve.trades.backend.native"); }

    @Override public boolean isAvailable() { return true; }

    @Override public boolean requiresRestart() { return false; }

    @Override public boolean supports(TradeTargetKind kind) {
        return kind == TradeTargetKind.VILLAGER || kind == TradeTargetKind.WANDERING_TRADER;
    }

    @Override public boolean supportsEntry(TradeEntry entry) {
        if (entry == null) return false;
        if (entry.targetKind != TradeTargetKind.ENTITY) return supports(entry.targetKind);
        if (usesProfessionTable(entry)) return true;
        MerchantTypeDetector.Kind k = TradeTypeDescriptorRegistry.merchantKind(entry.entityTypeId);
        if (k == MerchantTypeDetector.Kind.MERCHANT || k == MerchantTypeDetector.Kind.VILLAGER_LIKE) return true;
        return k == MerchantTypeDetector.Kind.UNKNOWN && isRegisteredEntityType(entry.entityTypeId);
    }

    private static boolean isRegisteredEntityType(String id) {
        try {
            ResourceLocation rl = ResourceLocation.tryParse(id == null ? "" : id);
            return rl != null && BuiltInRegistries.ENTITY_TYPE.containsKey(rl);
        } catch (Throwable t) {
            return false;
        }
    }

    /** ENTITY con profesion VANILLA sobre una entidad villager-like: comparte {@code VillagerTrades.TRADES} igual que VILLAGER. */
    public static boolean usesProfessionTable(TradeEntry entry) {
        return entry != null && entry.targetKind == TradeTargetKind.ENTITY
                && entry.professionId != null && !entry.professionId.isBlank()
                && TradeTypeDescriptorRegistry.isNativeCompatible(entry.entityTypeId);
    }

    /** ENTITY que KVE aplica directo sobre la lista de ofertas de cada instancia (ver {@link MerchantOfferInjector}). */
    public static boolean usesDirectOffers(TradeEntry entry) {
        return entry != null && entry.targetKind == TradeTargetKind.ENTITY && !usesProfessionTable(entry);
    }

    /** v1: output tags not supported (D3). */
    @Override public boolean supportsTagOutput() { return false; }

    @Override public boolean supportsMode(TradeEditMode mode) { return SUPPORTED_MODES.contains(mode); }

    /**
     * Native's actual effect lives entirely in {@link #applyRuntime()} (§5.1:
     * "native puede devolver vacío si el efecto es solo Java") - no JS body
     * is ever generated for entries resolved to this backend, so {@code
     * TradeExporter#writeScript} never puts {@code kve_native} in
     * {@code trades.js} (it only wraps backends whose {@code emit()}
     * actually produced lines).
     */
    @Override public List<String> emit(TradeEntry entry, TradeEmitContext ctx) { return List.of(); }

    @Override
    public List<String> previewLines(TradeEntry entry, TradeEmitContext ctx) {
        if (entry == null) return List.of();
        List<String> lines = new ArrayList<>();
        boolean direct = usesDirectOffers(entry);
        String target = switch (entry.targetKind) {
            case VILLAGER -> entry.professionId + " (nivel " + entry.level + ")";
            case ENTITY -> direct
                    ? entry.entityTypeId + (entry.professionId == null || entry.professionId.isBlank() ? " (todas)" : " con oficio " + entry.professionId)
                    : entry.entityTypeId + " -> profession " + entry.professionId + " (nivel " + entry.level + ")";
            case WANDERING_TRADER -> "wandering trader tier " + entry.level + (entry.level == 2 ? " (rare)" : " (generic)");
        };
        lines.add(ctx.indent() + (direct
                ? "// shovel_js_native: ofertas directas en cada entidad (se sincronizan al interactuar) - no se escribe JS."
                : "// shovel_js_native: aplica directamente en VillagerTrades - no se escribe JS."));
        switch (entry.mode) {
            case REPLACE_LEVEL -> lines.add(ctx.indent() + "REPLACE_LEVEL " + target + ": reemplaza todas las ofertas por " + enabledOfferCount(entry) + " oferta(s).");
            case ADD_OFFERS -> lines.add(ctx.indent() + "ADD_OFFERS " + target + ": agrega/edita " + enabledOfferCount(entry) + " oferta(s) junto a las existentes, sin duplicar en re-Apply.");
            case REMOVE_MATCHING -> lines.add(ctx.indent() + "REMOVE_MATCHING " + target + ": quita ofertas (vanilla o Shovel JS) que coincidan con " + enabledOfferCount(entry) + " filtro(s) input A/B/output.");
            case CLEAR_PROFESSION -> lines.add(ctx.indent() + "CLEAR_PROFESSION " + target + ": vacia todos los niveles de esa profesion.");
            case CLEAR_ENTITY -> lines.add(ctx.indent() + "CLEAR_ENTITY " + target + (direct
                    ? ": quita todas las ofertas propias de la entidad (deja solo las de Shovel JS)."
                    : ": vacia ambos tiers del wandering trader."));
        }
        for (TradeOffer o : entry.offers) {
            if (!o.enabled) continue;
            String line = o.inputAId + " x" + o.inputACount
                    + (o.inputBId != null && !o.inputBId.isBlank() ? " + " + o.inputBId + " x" + o.inputBCount : "")
                    + " -> " + o.outputId + " x" + o.outputCount
                    + (o.outputNbt != null && !o.outputNbt.isBlank() ? " " + o.outputNbt.trim() : "")
                    + " (maxUses=" + o.maxUses + ", xp=" + o.villagerXp + ", priceMult=" + o.priceMultiplier + ")";
            if (entry.mode == TradeEditMode.ADD_OFFERS && o.isEdit()) {
                line = "EDITA [" + o.editInputAId + (o.editInputBId != null && !o.editInputBId.isBlank() ? " + " + o.editInputBId : "")
                        + " -> " + o.editOutputId + "] => " + line;
            } else if (entry.mode == TradeEditMode.ADD_OFFERS && com.kve.common.kubejs.TradeExporter.hasLevels(entry)) {
                line += " [desbloquea en nivel " + o.effectiveLevel(entry.level) + "]";
            }
            lines.add(ctx.indent() + ctx.indent() + line);
        }
        return lines;
    }

    private static int enabledOfferCount(TradeEntry entry) {
        int n = 0;
        for (TradeOffer o : entry.offers) if (o.enabled) n++;
        return n;
    }

    @Override public String wrapFile(String body) { return body; }

    @Override public String documentationHint() {
        return "Aplica directamente en VillagerTrades.TRADES (servidor) - no requiere KubeJS ni addons instalados.";
    }

    @Override
    public void applyRuntime(TradeEntry entry) {
        if (entry == null) return;
        if (!entry.enabled) { retract(entry.id); return; }
        try {
            switch (entry.targetKind) {
                case VILLAGER -> applyProfessionKeyed(entry);
                case ENTITY -> {
                    if (usesDirectOffers(entry)) {
                        if (APPLIED.containsKey(entry.id)) retract(entry.id);
                        MerchantOfferInjector.register(entry);
                    } else {
                        MerchantOfferInjector.unregister(entry.id);
                        applyProfessionKeyed(entry);
                    }
                }
                case WANDERING_TRADER -> applyWandering(entry);
            }
        } catch (Exception ex) {
            CraftEditorMod.LOGGER.error("[Shovel JS] {} fallo aplicando trade '{}': {}", ID, entry.id, ex.getMessage());
        }
    }

    private void applyProfessionKeyed(TradeEntry entry) {
        if (entry.targetKind == TradeTargetKind.ENTITY && !TradeTypeDescriptorRegistry.isNativeCompatible(entry.entityTypeId)) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] {}: entidad '{}' no es abstract-villager-compatible (ver TradeTypeDescriptorRegistry) - trade '{}' ignorado",
                    ID, entry.entityTypeId, entry.id);
            return;
        }
        VillagerProfession profession = resolveProfession(entry.professionId);
        if (profession == null) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] {}: profesion desconocida '{}' (trade '{}')", ID, entry.professionId, entry.id);
            return;
        }
        Int2ObjectMap<VillagerTrades.ItemListing[]> byLevel =
                VillagerTrades.TRADES.computeIfAbsent(profession, p -> new Int2ObjectOpenHashMap<>());

        switch (entry.mode) {
            case REPLACE_LEVEL -> {
                MerchantOfferInjector.unregister(entry.id);
                byLevel.put(entry.level, buildListings(entry));
                APPLIED.put(entry.id, AppliedRef.profession(profession, entry.level));
                refreshProfessionVillagers(profession, entry.level);
            }
            case ADD_OFFERS -> {
                if (APPLIED.containsKey(entry.id)) retractPoolOnly(entry.id);
                MerchantOfferInjector.register(entry);
            }
            case REMOVE_MATCHING -> {
                if (APPLIED.containsKey(entry.id)) retractPoolOnly(entry.id);
                byLevel.put(entry.level, removeMatching(byLevel.get(entry.level), entry));
                MerchantOfferInjector.register(entry);
            }
            case CLEAR_PROFESSION -> {
                MerchantOfferInjector.unregister(entry.id);
                byLevel.clear();
                refreshProfessionVillagers(profession, null);
            }
            case CLEAR_ENTITY -> { }
        }
    }

    private void applyWandering(TradeEntry entry) {
        int tier = entry.level;
        Int2ObjectMap<VillagerTrades.ItemListing[]> byTier = VillagerTrades.WANDERING_TRADER_TRADES;

        boolean anyEdit = entry.offers.stream().anyMatch(o -> o.enabled && o.isEdit());
        if (entry.mode == TradeEditMode.ADD_OFFERS && anyEdit) MerchantOfferInjector.register(entry);
        else MerchantOfferInjector.unregister(entry.id);

        switch (entry.mode) {
            case REPLACE_LEVEL -> {
                byTier.put(tier, buildListings(entry));
                APPLIED.put(entry.id, AppliedRef.wandering(tier));
            }
            case ADD_OFFERS -> {
                TradeEntry addsOnly = entry.copy();
                addsOnly.offers.removeIf(TradeOffer::isEdit);
                byTier.put(tier, mergeAdd(byTier.get(tier), addsOnly));
                APPLIED.put(entry.id, AppliedRef.wandering(tier));
            }
            case REMOVE_MATCHING -> byTier.put(tier, removeMatching(byTier.get(tier), entry));
            case CLEAR_ENTITY -> byTier.clear();
            case CLEAR_PROFESSION -> { }
        }
        refreshWanderingTraders();
    }

    private void retract(String entryId) {
        MerchantOfferInjector.unregister(entryId);
        AppliedRef ref = retractPoolOnly(entryId);
        if (ref == null) return;
        if (ref.wandering()) refreshWanderingTraders();
        else refreshProfessionVillagers(ref.profession(), ref.level());
    }

    private AppliedRef retractPoolOnly(String entryId) {
        AppliedRef ref = APPLIED.remove(entryId);
        if (ref == null) return null;
        Int2ObjectMap<VillagerTrades.ItemListing[]> byLevel =
                ref.wandering() ? VillagerTrades.WANDERING_TRADER_TRADES : VillagerTrades.TRADES.get(ref.profession());
        if (byLevel == null) return ref;
        VillagerTrades.ItemListing[] existing = byLevel.get(ref.level());
        if (existing == null) return ref;
        List<VillagerTrades.ItemListing> kept = new ArrayList<>();
        for (VillagerTrades.ItemListing l : existing) {
            if (l instanceof KveManagedListing kml && entryId.equals(kml.entryId)) continue;
            kept.add(l);
        }
        byLevel.put(ref.level(), kept.toArray(new VillagerTrades.ItemListing[0]));
        return ref;
    }

    @Override
    public void reconcileRuntime(Set<String> currentlyCommittedEntryIds) {
        for (String id : new ArrayList<>(APPLIED.keySet())) {
            if (!currentlyCommittedEntryIds.contains(id)) retract(id);
        }
        for (String id : MerchantOfferInjector.registeredIds()) {
            if (!currentlyCommittedEntryIds.contains(id)) MerchantOfferInjector.unregister(id);
        }
    }

    private static final java.lang.reflect.Field OFFERS_FIELD = resolveOffersField();

    private static java.lang.reflect.Field resolveOffersField() {
        for (java.lang.reflect.Field f : AbstractVillager.class.getDeclaredFields()) {
            if (f.getType() == net.minecraft.world.item.trading.MerchantOffers.class
                    && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                try {
                    f.setAccessible(true);
                    return f;
                } catch (Exception ignored) {
                    break;
                }
            }
        }
        try {
            return net.neoforged.fml.util.ObfuscationReflectionHelper.findField(AbstractVillager.class, "offers");
        } catch (Exception ex) {
            // Mismo "sandbox note" que el resto de la clase: sin build real
            // no se puede confirmar el nombre de campo contra el mapping
            // vigente - si esto falla, forceOffersRegen() degrada al viejo
            // comportamiento (clear() - vacia pero no regenera) en vez de
            // tirar una excepcion, con un solo log al arrancar en vez de
            // uno por villager refrescado.
            CraftEditorMod.LOGGER.error("[Shovel JS] {}: no se pudo resolver AbstractVillager#offers via reflection - el refresh de villagers/wandering traders solo vaciara sus ofertas sin regenerarlas ({})", ID, ex.toString());
            return null;
        }
    }

    private static void forceOffersRegen(AbstractVillager villager) {
        if (OFFERS_FIELD != null) {
            try {
                OFFERS_FIELD.set(villager, null);
                return;
            } catch (Exception ignored) {
            }
        }
        villager.getOffers().clear();
    }

    private static void regenerateAllLevels(Villager v) {
        if (OFFERS_FIELD == null) { forceOffersRegen(v); return; }
        Int2ObjectMap<VillagerTrades.ItemListing[]> byLevel = VillagerTrades.TRADES.get(v.getVillagerData().getProfession());
        net.minecraft.world.item.trading.MerchantOffers offers = new net.minecraft.world.item.trading.MerchantOffers();
        RandomSource random = v.getRandom();
        if (byLevel != null) {
            for (int lvl = 1; lvl <= v.getVillagerData().getLevel(); lvl++) {
                VillagerTrades.ItemListing[] listings = byLevel.get(lvl);
                if (listings == null || listings.length == 0) continue;
                java.util.Set<Integer> picks = new java.util.LinkedHashSet<>();
                if (listings.length > 2) {
                    while (picks.size() < 2) picks.add(random.nextInt(listings.length));
                } else {
                    for (int i = 0; i < listings.length; i++) picks.add(i);
                }
                for (int i : picks) {
                    try {
                        MerchantOffer o = listings[i].getOffer(v, random);
                        if (o != null) offers.add(o);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        try {
            OFFERS_FIELD.set(v, offers);
        } catch (Exception ex) {
            forceOffersRegen(v);
        }
        MerchantOfferInjector.forgetOwnership(v);
    }

    private static int refreshProfessionVillagers(VillagerProfession profession, Integer levelOrNull) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || profession == null) return 0;
        int refreshed = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity e : level.getEntities().getAll()) {
                if (!(e instanceof Villager v)) continue;
                if (v.getVillagerData().getProfession() != profession) continue;
                if (levelOrNull != null && v.getVillagerData().getLevel() < levelOrNull) continue;
                regenerateAllLevels(v);
                refreshed++;
            }
        }
        if (refreshed > 0) {
            CraftEditorMod.LOGGER.info("[Shovel JS] {}: {} villager(s) vivo(s) refrescado(s) para profesion '{}'{}.",
                    ID, refreshed, profession, levelOrNull != null ? " nivel >= " + levelOrNull : " (todos los niveles)");
        }
        return refreshed;
    }

    private static int refreshWanderingTraders() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return 0;
        int refreshed = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity e : level.getEntities().getAll()) {
                if (e instanceof WanderingTrader wt) { forceOffersRegen(wt); refreshed++; }
            }
        }
        if (refreshed > 0) {
            CraftEditorMod.LOGGER.info("[Shovel JS] {}: {} wandering trader(s) vivo(s) refrescado(s).", ID, refreshed);
        }
        return refreshed;
    }

    private VillagerProfession resolveProfession(String id) {
        if (id == null || id.isBlank()) return null;
        try {
            return BuiltInRegistries.VILLAGER_PROFESSION.get(ResourceLocation.parse(id));
        } catch (Exception ex) {
            return null;
        }
    }

    private static final java.util.Map<String, AppliedRef> APPLIED = new java.util.concurrent.ConcurrentHashMap<>();

    private record AppliedRef(VillagerProfession profession, int level, boolean wandering) {
        static AppliedRef profession(VillagerProfession profession, int level) { return new AppliedRef(profession, level, false); }
        static AppliedRef wandering(int tier) { return new AppliedRef(null, tier, true); }
    }

    static void resetAppliedTrackingForTests() { APPLIED.clear(); }

    VillagerTrades.ItemListing[] buildListings(TradeEntry entry) {
        List<VillagerTrades.ItemListing> out = new ArrayList<>();
        for (TradeOffer o : entry.offers) {
            if (!o.enabled) continue;
            VillagerTrades.ItemListing listing = toListing(entry.id, o);
            if (listing != null) out.add(listing);
        }
        return out.toArray(new VillagerTrades.ItemListing[0]);
    }

    VillagerTrades.ItemListing[] mergeAdd(VillagerTrades.ItemListing[] existingOrNull, TradeEntry entry) {
        List<VillagerTrades.ItemListing> merged = new ArrayList<>();
        if (existingOrNull != null) {
            for (VillagerTrades.ItemListing l : existingOrNull) {
                if (l instanceof KveManagedListing kml && entry.id.equals(kml.entryId)) continue;
                merged.add(l);
            }
        }
        for (TradeOffer o : entry.offers) {
            if (!o.enabled) continue;
            VillagerTrades.ItemListing listing = toListing(entry.id, o);
            if (listing != null) merged.add(listing);
        }
        return merged.toArray(new VillagerTrades.ItemListing[0]);
    }

    VillagerTrades.ItemListing[] removeMatching(VillagerTrades.ItemListing[] existingOrNull, TradeEntry entry) {
        if (existingOrNull == null || existingOrNull.length == 0) return existingOrNull == null ? new VillagerTrades.ItemListing[0] : existingOrNull;
        List<TradeOffer> filters = new ArrayList<>();
        for (TradeOffer o : entry.offers) {
            if (o.enabled && o.inputAId != null && !o.inputAId.isBlank()) filters.add(o);
        }
        if (filters.isEmpty()) return existingOrNull;
        List<VillagerTrades.ItemListing> kept = new ArrayList<>();
        for (VillagerTrades.ItemListing l : existingOrNull) {
            if (listingMatchesAny(l, filters)) continue;
            kept.add(l);
        }
        return kept.toArray(new VillagerTrades.ItemListing[0]);
    }

    private static boolean listingMatchesAny(VillagerTrades.ItemListing listing, List<TradeOffer> filters) {
        MerchantOffer probe;
        try {
            probe = listing.getOffer(null, RandomSource.create());
        } catch (Throwable t) {
            return false;
        }
        if (probe == null) return false;
        String a = stackId(probe.getBaseCostA());
        String b = stackId(probe.getCostB());
        String out = stackId(probe.getResult());
        for (TradeOffer f : filters) {
            if (!f.inputAId.equals(a)) continue;
            if (f.inputBId != null && !f.inputBId.isBlank() && !f.inputBId.equals(b)) continue;
            if (f.outputId != null && !f.outputId.isBlank() && !f.outputId.equals(out)) continue;
            return true;
        }
        return false;
    }

    private static String stackId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }

    VillagerTrades.ItemListing toListing(String entryId, TradeOffer o) {
        Item itemA = itemFor(o.inputAId);
        Item itemOut = itemFor(o.outputId);
        if (itemA == null || itemOut == null) return null;
        Item itemB = (o.inputBId != null && !o.inputBId.isBlank()) ? itemFor(o.inputBId) : null;
        ItemStack result = new ItemStack(itemOut, Math.max(1, o.outputCount));
        applyOutputNbt(result, o.outputNbt, entryId);
        return new KveManagedListing(entryId, o.inputAId,
                new ItemStack(itemA, Math.max(1, o.inputACount)),
                itemB != null ? new ItemStack(itemB, Math.max(1, o.inputBCount)) : ItemStack.EMPTY,
                result,
                o.maxUses, o.villagerXp, o.priceMultiplier);
    }

    static boolean applyOutputNbt(ItemStack stack, String snbt, String entryId) {
        if (snbt == null || snbt.isBlank()) return false;
        try {
            TagParser.parseTag(snbt.trim());
            com.kve.common.util.ItemExtraDataCodec.applyFromSnbt(stack, snbt.trim());
            return true;
        } catch (Exception ex) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] {}: NBT de output invalido en trade '{}' - oferta aplicada sin NBT ({})",
                    ID, entryId, ex.getMessage());
            return false;
        }
    }

    private Item itemFor(String id) {
        if (id == null || id.isBlank()) return null;
        try {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
            return item == net.minecraft.world.item.Items.AIR ? null : item;
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * A KVE-authored villager offer, tagged with the {@link TradeEntry#id}
     * that produced it so a later Apply can find-and-replace just this
     * entry's own listings (see {@link #mergeAdd()}/{@link #removeMatching()})
     * without disturbing vanilla trades or other entries' contributions.
     * {@code inputAId} is kept alongside the built {@link ItemStack} purely
     * so REMOVE_MATCHING can match by id without re-deriving it from the
     * stack (tags/count aren't part of the match key).
     */
    static final class KveManagedListing implements VillagerTrades.ItemListing {
        final String entryId;
        final String inputAId;
        final ItemStack costA;
        final ItemStack costB;
        final ItemStack result;
        final int maxUses;
        final int villagerXp;
        final float priceMultiplier;

        KveManagedListing(String entryId, String inputAId, ItemStack costA, ItemStack costB, ItemStack result,
                           int maxUses, int villagerXp, float priceMultiplier) {
            this.entryId = entryId;
            this.inputAId = inputAId;
            this.costA = costA;
            this.costB = costB;
            this.result = result;
            this.maxUses = maxUses;
            this.villagerXp = villagerXp;
            this.priceMultiplier = priceMultiplier;
        }

        @Override
        public MerchantOffer getOffer(Entity trader, RandomSource random) {
            return TradeCosts.offer(costA.copy(), costB.copy(), result.copy(), maxUses, villagerXp, priceMultiplier);
        }
    }
}
