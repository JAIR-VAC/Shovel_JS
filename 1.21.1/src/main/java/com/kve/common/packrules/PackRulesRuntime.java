package com.kve.common.packrules;

import com.kve.CraftEditorMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class PackRulesRuntime {
    private PackRulesRuntime() {}

    public record SpawnRule(String entityId, Set<String> dimensions, boolean naturalOnly) {}

    private static final Map<String, Float> COMPOST = new LinkedHashMap<>();
    private static final List<SpawnRule> SPAWN_RULES = new ArrayList<>();
    private static final Map<String, String> GAME_RULES = new LinkedHashMap<>();
    private static final Map<Item, Float> COMPOST_ORIGINALS = new HashMap<>();

    public static synchronized void clear() {
        restoreCompostables();
        COMPOST.clear();
        SPAWN_RULES.clear();
        GAME_RULES.clear();
    }

    public static synchronized void setCompostable(String itemId, float chance) {
        COMPOST.put(itemId, chance);
        Item item = item(itemId);
        if (item == null) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] Compostable: el item {} no existe", itemId);
            return;
        }
        if (!COMPOST_ORIGINALS.containsKey(item)) {
            COMPOST_ORIGINALS.put(item, ComposterBlock.COMPOSTABLES.containsKey(item) ? ComposterBlock.COMPOSTABLES.getFloat(item) : null);
        }
        if (chance <= 0f) ComposterBlock.COMPOSTABLES.removeFloat(item);
        else ComposterBlock.COMPOSTABLES.put(item, Math.min(1f, chance));
    }

    public static synchronized void blockSpawn(String entityId, String dimensionsCsv, boolean naturalOnly) {
        SPAWN_RULES.add(new SpawnRule(entityId, parseCsv(dimensionsCsv), naturalOnly));
    }

    public static synchronized void defaultGameRule(String rule, String value) {
        GAME_RULES.put(rule, value);
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && server.isRunning()) applyGameRules(server);
    }

    private static void restoreCompostables() {
        for (Map.Entry<Item, Float> e : COMPOST_ORIGINALS.entrySet()) {
            if (e.getValue() == null) ComposterBlock.COMPOSTABLES.removeFloat(e.getKey());
            else ComposterBlock.COMPOSTABLES.put(e.getKey(), e.getValue().floatValue());
        }
        COMPOST_ORIGINALS.clear();
    }

    static boolean isNaturalSpawn(MobSpawnType type) {
        return type == MobSpawnType.NATURAL || type == MobSpawnType.CHUNK_GENERATION || type == MobSpawnType.PATROL;
    }

    static boolean blocks(List<SpawnRule> rules, String entityId, String dimensionId, boolean natural) {
        for (SpawnRule r : rules) {
            if (!r.entityId().equals(entityId)) continue;
            if (r.naturalOnly() && !natural) continue;
            if (!r.dimensions().isEmpty() && !r.dimensions().contains(dimensionId)) continue;
            return true;
        }
        return false;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        List<SpawnRule> rules;
        synchronized (PackRulesRuntime.class) {
            if (SPAWN_RULES.isEmpty()) return;
            rules = List.copyOf(SPAWN_RULES);
        }
        ResourceLocation type = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
        String dim = event.getLevel().getLevel().dimension().location().toString();
        if (type != null && blocks(rules, type.toString(), dim, isNaturalSpawn(event.getSpawnType()))) {
            event.setSpawnCancelled(true);
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        applyGameRules(event.getServer());
    }

    /**
     * Pura, para tests: las reglas que todavia hay que aplicar en este mundo
     * (nunca aplicadas, o aplicadas con otro valor por defecto).
     */
    static Map<String, String> pending(Map<String, String> wanted, Map<String, String> alreadyApplied) {
        Map<String, String> out = new LinkedHashMap<>();
        wanted.forEach((k, v) -> {
            String done = alreadyApplied.get(k);
            if (done == null || !done.equalsIgnoreCase(v)) out.put(k, v);
        });
        return out;
    }

    private static synchronized void applyGameRules(MinecraftServer server) {
        if (GAME_RULES.isEmpty()) return;
        ServerLevel overworld = server.overworld();
        AppliedRules applied = overworld.getDataStorage().computeIfAbsent(new net.minecraft.world.level.saveddata.SavedData.Factory<>(AppliedRules::new, AppliedRules::load, null), "shovel_js_default_gamerules");
        Map<String, String> todo = pending(GAME_RULES, applied.values);
        if (todo.isEmpty()) return;
        GameRules rules = server.getGameRules();
        GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
            @Override
            public void visitBoolean(GameRules.Key<GameRules.BooleanValue> key, GameRules.Type<GameRules.BooleanValue> type) {
                String v = todo.get(key.getId());
                if (v == null) return;
                if (!v.equalsIgnoreCase("true") && !v.equalsIgnoreCase("false")) { warnValue(key.getId(), v); return; }
                rules.getRule(key).set(Boolean.parseBoolean(v.toLowerCase(Locale.ROOT)), server);
                applied.mark(key.getId(), v);
            }

            @Override
            public void visitInteger(GameRules.Key<GameRules.IntegerValue> key, GameRules.Type<GameRules.IntegerValue> type) {
                String v = todo.get(key.getId());
                if (v == null) return;
                try {
                    rules.getRule(key).set(Integer.parseInt(v.trim()), server);
                    applied.mark(key.getId(), v);
                } catch (NumberFormatException ex) {
                    warnValue(key.getId(), v);
                }
            }
        });
        for (String rule : todo.keySet()) {
            if (!applied.values.containsKey(rule)) CraftEditorMod.LOGGER.warn("[Shovel JS] Gamerule por defecto desconocida o invalida: {}", rule);
        }
    }

    private static void warnValue(String rule, String value) {
        CraftEditorMod.LOGGER.warn("[Shovel JS] Valor invalido para la gamerule {}: {}", rule, value);
    }

    /** Lo ya aplicado en ESTE mundo, guardado en data/kve_default_gamerules.dat. */
    static final class AppliedRules extends SavedData {
        final Map<String, String> values = new LinkedHashMap<>();

        static AppliedRules load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
            AppliedRules r = new AppliedRules();
            for (String k : tag.getAllKeys()) r.values.put(k, tag.getString(k));
            return r;
        }

        void mark(String rule, String value) {
            values.put(rule, value);
            setDirty();
        }

        @Override
        public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
            values.forEach(tag::putString);
            return tag;
        }
    }

    static Set<String> parseCsv(String csv) {
        Set<String> out = new java.util.LinkedHashSet<>();
        if (csv == null) return out;
        for (String s : csv.split(",")) if (!s.isBlank()) out.add(s.trim());
        return out;
    }

    private static Item item(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return null;
        Item it = BuiltInRegistries.ITEM.get(rl);
        return it == Items.AIR ? null : it;
    }
}
