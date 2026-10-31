package com.kve.common.kubejs;

import com.google.gson.*;
import com.kve.CraftEditorMod;
import com.kve.common.persist.CraftEditorState;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class LootTableExporter {

    /** What kind of thing the entry targets. */
    public enum TargetKind { ENTITY, BLOCK, CUSTOM_TABLE }

    public enum TriggerEvent {
        DEATH,
        BREAK,
        INTERACT,
        DIRECT;

        public boolean validFor(TargetKind kind) {
            return switch (this) {
                case DEATH -> kind == TargetKind.ENTITY;
                case BREAK -> kind == TargetKind.BLOCK;
                case INTERACT -> kind == TargetKind.ENTITY || kind == TargetKind.BLOCK;
                case DIRECT -> kind == TargetKind.CUSTOM_TABLE;
            };
        }

        public static TriggerEvent firstValidFor(TargetKind kind) {
            for (TriggerEvent ev : values()) if (ev.validFor(kind)) return ev;
            return DEATH;
        }

        public String label() {
            return switch (this) {
                case DEATH -> com.kve.common.Lang.get("gui.kve.loot.event.death");
                case BREAK -> com.kve.common.Lang.get("gui.kve.loot.event.break");
                case INTERACT -> com.kve.common.Lang.get("gui.kve.loot.event.interact");
                case DIRECT -> com.kve.common.Lang.get("gui.kve.loot.event.direct");
            };
        }

        /** Whether this event is backed by a real vanilla loot_table JSON (vs a generated script). */
        public boolean isDatapackBacked() { return this == DEATH || this == BREAK || this == DIRECT; }
    }

    public enum Condition {
        NONE, KILLED_BY_PLAYER, KILLED_BY_PLAYER_OR_PET, ON_FIRE, RANDOM_CHANCE, RANDOM_CHANCE_WITH_LOOTING, SURVIVES_EXPLOSION;

        public Condition next() {
            Condition[] v = values();
            return v[(ordinal() + 1) % v.length];
        }

        public String label() {
            return switch (this) {
                case NONE -> com.kve.common.Lang.get("gui.kve.loot.condition.none");
                case KILLED_BY_PLAYER -> com.kve.common.Lang.get("gui.kve.loot.condition.killed_by_player");
                case KILLED_BY_PLAYER_OR_PET -> com.kve.common.Lang.get("gui.kve.loot.condition.killed_by_player_or_pet");
                case ON_FIRE -> com.kve.common.Lang.get("gui.kve.loot.condition.on_fire");
                case RANDOM_CHANCE -> com.kve.common.Lang.get("gui.kve.loot.condition.random_chance");
                case RANDOM_CHANCE_WITH_LOOTING -> com.kve.common.Lang.get("gui.kve.loot.condition.random_chance_looting");
                case SURVIVES_EXPLOSION -> com.kve.common.Lang.get("gui.kve.loot.condition.survives_explosion");
            };
        }
    }

    public enum FunctionType {
        ENCHANT_WITH_LEVELS, LOOTING_ENCHANT, SET_NBT, SMELT;

        public FunctionType next() {
            FunctionType[] v = values();
            return v[(ordinal() + 1) % v.length];
        }

        public String label() {
            return switch (this) {
                case ENCHANT_WITH_LEVELS -> com.kve.common.Lang.get("gui.kve.loot.function.enchant_levels");
                case LOOTING_ENCHANT -> com.kve.common.Lang.get("gui.kve.loot.function.looting_enchant");
                case SET_NBT -> com.kve.common.Lang.get("gui.kve.loot.function.custom_nbt");
                case SMELT -> com.kve.common.Lang.get("gui.kve.loot.function.smelt");
            };
        }
    }

    public static class LootFunctionData {
        public FunctionType type = FunctionType.SET_NBT;
        public int levelsMin = 10;
        public int levelsMax = 30;
        public boolean treasure = false;
        public String nbt = "";

        /**
         * Only meaningful for {@link FunctionType#SMELT}. Vanilla's
         * "minecraft:furnace_smelt" is almost always gated by its OWN
         * function-level "conditions" array (typically an entity_properties
         * "is_on_fire" predicate - see cow/pig/chicken's real loot tables),
         * NOT a pool-level condition. Defaults to {@code true} because that
         * matches both the overwhelmingly common real-world use (only
         * auto-smelt when the mob died on fire) and, critically, what a
         * freshly-added SMELT function via "+ Modification" should do -
         * see {@link #functionJson()} / {@link #parseFunctionIntoEntry()} for
         * why leaving this unconditional silently turned every raw-beef
         * drop into cooked beef after any save, fire or not.
         */
        public boolean smeltOnlyIfOnFire = true;

        public LootFunctionData copy() {
            LootFunctionData c = new LootFunctionData();
            c.type = type; c.levelsMin = levelsMin; c.levelsMax = levelsMax;
            c.treasure = treasure; c.nbt = nbt; c.smeltOnlyIfOnFire = smeltOnlyIfOnFire;
            return c;
        }
    }

    public static class LootEntryData {
        public String itemId = "";
        public int weight = 1;
        public int minCount = 1;
        public int maxCount = 1;
        public List<LootFunctionData> functions = new ArrayList<>();
        public com.google.gson.JsonArray conditions = null;

        public LootEntryData copy() {
            LootEntryData c = new LootEntryData();
            c.itemId = itemId; c.weight = weight; c.minCount = minCount; c.maxCount = maxCount;
            for (LootFunctionData f : functions) c.functions.add(f.copy());
            c.conditions = conditions == null ? null : conditions.deepCopy();
            return c;
        }
    }

    public static class LootPoolData {
        public int rolls = 1;
        public Condition condition = Condition.NONE;
        public double chance = 0.5;
        public double lootingMultiplier = 0.02;
        public List<LootEntryData> entries = new ArrayList<>();
        public JsonArray extraConditions = null;

        public LootPoolData copy() {
            LootPoolData c = new LootPoolData();
            c.rolls = rolls; c.condition = condition; c.chance = chance; c.lootingMultiplier = lootingMultiplier;
            c.extraConditions = extraConditions == null ? null : extraConditions.deepCopy();
            for (LootEntryData e : entries) c.entries.add(e.copy());
            return c;
        }
    }

    public static class LootTableEntry {
        public TargetKind kind = TargetKind.ENTITY;
        public TriggerEvent event = TriggerEvent.DEATH;
        public String targetId = "";
        public boolean enabled = true;
        public String category = "";
        public List<LootPoolData> pools = new ArrayList<>();

        public String key() { return kind.name() + "|" + event.name() + "|" + targetId; }

        public LootTableEntry copy() {
            LootTableEntry c = new LootTableEntry();
            c.kind = kind; c.event = event; c.targetId = targetId; c.enabled = enabled; c.category = category;
            for (LootPoolData p : pools) c.pools.add(p.copy());
            return c;
        }
    }

    /**
     * Pool entries omitted from the last {@link #writeFiles()} because
     * their item id failed {@link SemanticValidator#validateItemId()}. One
     * bad entry is dropped from its pool rather than
     * ever writing a loot_table JSON referencing a nonexistent/malformed
     * item alongside the good entries in the same pool.
     */
    private static final List<String> LAST_WRITE_WARNINGS = new ArrayList<>();

    public static List<String> getLastWriteWarnings() { return Collections.unmodifiableList(LAST_WRITE_WARNINGS); }

    private static final Map<String, LootTableEntry> ENTRIES = new LinkedHashMap<>();

    /**
     * Lo ultimo aplicado (o cargado al arrancar): lo que se guarda en el estado y lo que leen
     * los enforcers. Los borradores pendientes viven solo en ENTRIES hasta Aplicar.
     */
    private static volatile Map<String, LootTableEntry> APPLIED = Map.of();

    public static Collection<LootTableEntry> getAppliedEntries() { return APPLIED.values(); }
    public static LootTableEntry getAppliedEntry(String id) { return APPLIED.get(id); }
    public static boolean hasAppliedEntries() { return !APPLIED.isEmpty(); }

    /** Toma la foto de ENTRIES como aplicada; se llama al escribir el script y al cargar el estado. */
    public static void markApplied() {
        Map<String, LootTableEntry> m = new LinkedHashMap<>();
        for (Map.Entry<String, LootTableEntry> e : ENTRIES.entrySet()) m.put(e.getKey(), e.getValue().copy());
        APPLIED = java.util.Collections.unmodifiableMap(m);
    }
    private static final Map<String, Deque<LootTableEntry>> HISTORY =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Deque<LootTableEntry>> eldest) {
                    return size() > Math.max(1, com.kve.common.config.KVEConfig.undoHistoryMaxKeys);
                }
            };

    /** Stages a loot table into ENTRIES (in-memory only, no script/state write). */
    public static void stageLootTable(LootTableEntry e) {
        LootTableEntry previous = ENTRIES.get(e.key());
        if (previous != null) pushHistory(e.key(), previous);
        ENTRIES.put(e.key(), e);
    }

    /** Undoes a stageLootTable(): drops the staged entry and restores whatever was there before (null if it was new). */
    public static void discardStagedLootTable(String key, LootTableEntry previousOrNull) {
        if (previousOrNull != null) ENTRIES.put(key, previousOrNull);
        else ENTRIES.remove(key);
    }

    /** Stages a loot table deletion (in-memory only). */
    public static void stageDeleteLootTable(String key) {
        ENTRIES.remove(key);
        HISTORY.remove(key);
    }

    /** Stages an undo (in-memory only, no script/state write) - see stageLootTable(). Returns the restored entry, or null if there's nothing to undo. */
    public static LootTableEntry stageUndo(String key) {
        Deque<LootTableEntry> h = HISTORY.get(key);
        if (h == null || h.isEmpty()) return null;
        LootTableEntry restored = h.removeFirst();
        ENTRIES.put(key, restored);
        return restored;
    }

    /** Undoes a stageUndo(): pushes the restored entry back onto the undo history and restores whatever ENTRIES had before the undo (null if it was new). */
    public static void discardStagedUndo(String key, LootTableEntry restored, LootTableEntry beforeUndoOrNull) {
        Deque<LootTableEntry> h = HISTORY.computeIfAbsent(key, k -> new ArrayDeque<>());
        h.addFirst(restored);
        if (beforeUndoOrNull != null) ENTRIES.put(key, beforeUndoOrNull);
        else ENTRIES.remove(key);
    }

    /** Writes the currently staged ENTRIES to disk (loot table json + KVE state). Only called from a PendingChange's onApply. */
    public static void commitLootTables() {
        writeFiles();
        CraftEditorState.save();
    }

    private static void pushHistory(String key, LootTableEntry snapshot) {
        int max = com.kve.common.config.KVEConfig.undoHistorySize;
        if (max <= 0) return;
        Deque<LootTableEntry> h = HISTORY.computeIfAbsent(key, k -> new ArrayDeque<>());
        h.addFirst(snapshot.copy());
        while (h.size() > max) h.removeLast();
    }

    public static boolean canUndo(String key) {
        Deque<LootTableEntry> h = HISTORY.get(key);
        return h != null && !h.isEmpty();
    }

    public static LootTableEntry undo(String key) {
        Deque<LootTableEntry> h = HISTORY.get(key);
        if (h == null || h.isEmpty()) return null;
        LootTableEntry previous = h.removeFirst();
        ENTRIES.put(key, previous);
        writeFiles();
        CraftEditorState.save();
        return previous;
    }

    public static Collection<LootTableEntry> getEntries() { return Collections.unmodifiableCollection(ENTRIES.values()); }
    public static LootTableEntry               getEntry(String key) { return ENTRIES.get(key); }
    public static void clearAll() { ENTRIES.clear(); APPLIED = Map.of(); HISTORY.clear(); }
    public static void loadEntry(LootTableEntry e) { ENTRIES.put(e.key(), e); }

    /** Block id or entity id -> its enabled INTERACT entry for that kind, if any. */
    public static LootTableEntry findInteractEntry(TargetKind kind, ResourceLocation id) {
        if (id == null) return null;
        LootTableEntry e = APPLIED.get(kind.name() + "|" + TriggerEvent.INTERACT.name() + "|" + id);
        return (e != null && e.enabled) ? e : null;
    }

    public static LootTableEntry tryLoadCurrentLootTable(TargetKind kind, TriggerEvent event, String targetId) {
        if (!event.isDatapackBacked()) return null;
        net.minecraft.server.MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;

        ResourceLocation resPath = resolveVanillaLootPath(kind, targetId);
        if (resPath == null) return null;

        try {
            var resOpt = server.getResourceManager().getResource(resPath);
            if (resOpt.isEmpty()) return null;
            String json;
            try (var in = resOpt.get().open()) {
                json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            LootTableEntry parsed = parseLootTableJson(kind, event, targetId, root);
            return parsed.pools.isEmpty() ? null : parsed;
        } catch (Exception ex) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] Could not import current loot table for {}: {}", targetId, ex.getMessage());
            return null;
        }
    }

    private static ResourceLocation resolveVanillaLootPath(TargetKind kind, String targetId) {
        ResourceLocation rl;
        try { rl = ResourceLocation.parse(targetId); } catch (Exception ex) { return null; }
        return switch (kind) {
            case ENTITY -> ResourceLocation.fromNamespaceAndPath(rl.getNamespace(), "loot_table/entities/" + rl.getPath() + ".json");
            case BLOCK -> ResourceLocation.fromNamespaceAndPath(rl.getNamespace(), "loot_table/blocks/" + rl.getPath() + ".json");
            case CUSTOM_TABLE -> ResourceLocation.fromNamespaceAndPath(rl.getNamespace(), "loot_table/" + rl.getPath() + ".json");
        };
    }

    /**
     * Punto 2.5, "Table...". Lists
     * every loot table id this client can currently see - same source and
     * same singleplayer/LAN-only limitation as {@link #tryLoadCurrentLootTable()}
     * (loot table JSON is server-only data, unreachable from a remote
     * multiplayer client), mirroring the exact pattern already used for
     * structures in {@code StructureNbtIO#listAvailableIds()}. Unlike that
     * method there's no "kubejs overrides on disk" source to merge in first -
     * this editor doesn't keep a separate on-disk override folder for loot
     * tables the way structures does, so the resource manager is the only
     * source. Returned ids are already in the exact shape
     * {@link #tryLoadCurrentLootTable()} expects for {@code targetId} when
     * called with {@code TargetKind.CUSTOM_TABLE}/{@code TriggerEvent.DIRECT}
     * (subfolder included, e.g. {@code "minecraft:entities/zombie"} or
     * {@code "minecraft:chests/simple_dungeon"} - see
     * {@link #resolveVanillaLootPath()}'s CUSTOM_TABLE case, which appends the
     * id's path directly under {@code loot_tables/} with no subfolder
     * guessing).
     */
    public static List<String> listAllLootTableIds() {
        net.minecraft.server.MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return Collections.emptyList();
        TreeSet<String> ids = new TreeSet<>();
        try {
            Map<ResourceLocation, ?> found = server.getResourceManager()
                    .listResources("loot_table", path -> path.getPath().endsWith(".json"));
            String prefix = "loot_table/";
            String suffix = ".json";
            for (ResourceLocation key : found.keySet()) {
                String path = key.getPath();
                if (!path.startsWith(prefix) || !path.endsWith(suffix)) continue;
                String idPath = path.substring(prefix.length(), path.length() - suffix.length());
                ids.add(key.getNamespace() + ":" + idPath);
            }
        } catch (Exception ex) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] Could not list loot tables: {}", ex.getMessage());
        }
        return new ArrayList<>(ids);
    }

    static LootTableEntry parseLootTableJson(TargetKind kind, TriggerEvent event, String targetId, JsonObject root) {
        LootTableEntry e = new LootTableEntry();
        e.kind = kind;
        e.event = event;
        e.targetId = targetId;
        if (!root.has("pools") || !root.get("pools").isJsonArray()) return e;

        for (JsonElement poolEl : root.getAsJsonArray("pools")) {
            if (!poolEl.isJsonObject()) continue;
            JsonObject poolJson = poolEl.getAsJsonObject();
            LootPoolData pool = new LootPoolData();
            pool.rolls = intOrDefault(poolJson, "rolls", 1);
            parseConditionInto(poolJson, pool);

            if (poolJson.has("entries") && poolJson.get("entries").isJsonArray()) {
                for (JsonElement entryEl : poolJson.getAsJsonArray("entries")) {
                    collectItemEntries(entryEl, pool.entries);
                }
            }
            if (!pool.entries.isEmpty()) e.pools.add(pool);
        }
        return e;
    }

    private static void collectItemEntries(JsonElement entryEl, List<LootEntryData> out) {
        collectItemEntries(entryEl, out, null);
    }

    private static void collectItemEntries(JsonElement entryEl, List<LootEntryData> out, com.google.gson.JsonArray inherited) {
        if (!entryEl.isJsonObject()) return;
        JsonObject entryJson = entryEl.getAsJsonObject();
        String type = strOrNull(entryJson, "type");
        com.google.gson.JsonArray own = mergeConditions(inherited, entryJson);

        if ("minecraft:item".equals(type)) {
            String itemId = strOrNull(entryJson, "name");
            if (itemId == null || itemId.isBlank()) return;

            LootEntryData entry = new LootEntryData();
            entry.itemId = itemId;
            entry.weight = intOrDefault(entryJson, "weight", 1);
            entry.minCount = 1;
            entry.maxCount = 1;
            entry.conditions = own;

            if (entryJson.has("functions") && entryJson.get("functions").isJsonArray()) {
                for (JsonElement fnEl : entryJson.getAsJsonArray("functions")) {
                    if (fnEl.isJsonObject()) parseFunctionIntoEntry(fnEl.getAsJsonObject(), entry);
                }
            }
            out.add(entry);
            return;
        }

        if ("minecraft:alternatives".equals(type) && entryJson.has("children") && entryJson.get("children").isJsonArray()) {
            com.google.gson.JsonArray notEarlier = new com.google.gson.JsonArray();
            for (JsonElement childEl : entryJson.getAsJsonArray("children")) {
                com.google.gson.JsonArray inheritedHere = concat(own, notEarlier);
                collectItemEntries(childEl, out, inheritedHere);
                JsonObject child = childEl.isJsonObject() ? childEl.getAsJsonObject() : null;
                com.google.gson.JsonArray childConds = child != null && child.has("conditions") && child.get("conditions").isJsonArray()
                        ? child.getAsJsonArray("conditions") : null;
                // Un hijo sin condiciones siempre gana: los siguientes nunca salen.
                if (childConds == null || childConds.isEmpty()) break;
                notEarlier.add(invert(childConds));
            }
            return;
        }
        if (("minecraft:group".equals(type) || "minecraft:sequence".equals(type))
                && entryJson.has("children") && entryJson.get("children").isJsonArray()) {
            for (JsonElement childEl : entryJson.getAsJsonArray("children")) {
                collectItemEntries(childEl, out, own);
            }
        }
    }

    static com.google.gson.JsonArray mergeConditions(com.google.gson.JsonArray inherited, JsonObject node) {
        com.google.gson.JsonArray out = new com.google.gson.JsonArray();
        if (inherited != null) for (JsonElement el : inherited) out.add(el.deepCopy());
        if (node.has("conditions") && node.get("conditions").isJsonArray()) {
            for (JsonElement el : node.getAsJsonArray("conditions")) out.add(el.deepCopy());
        }
        return out.isEmpty() ? null : out;
    }

    static com.google.gson.JsonArray concat(com.google.gson.JsonArray a, com.google.gson.JsonArray b) {
        com.google.gson.JsonArray out = new com.google.gson.JsonArray();
        if (a != null) for (JsonElement el : a) out.add(el.deepCopy());
        if (b != null) for (JsonElement el : b) out.add(el.deepCopy());
        return out.isEmpty() ? null : out;
    }

    static JsonObject invert(com.google.gson.JsonArray conds) {
        JsonObject term;
        if (conds.size() == 1) {
            term = conds.get(0).getAsJsonObject().deepCopy();
        } else {
            term = new JsonObject();
            term.addProperty("condition", "minecraft:all_of");
            term.add("terms", conds.deepCopy());
        }
        JsonObject inv = new JsonObject();
        inv.addProperty("condition", "minecraft:inverted");
        inv.add("term", term);
        return inv;
    }

    private static void parseConditionInto(JsonObject poolJson, LootPoolData pool) {
        if (!poolJson.has("conditions") || !poolJson.get("conditions").isJsonArray()) return;
        for (JsonElement el : poolJson.getAsJsonArray("conditions")) {
            if (!el.isJsonObject()) continue;
            JsonObject c = el.getAsJsonObject();
            if (pool.condition == Condition.NONE && mapPoolCondition(c, pool)) continue;
            if (pool.extraConditions == null) pool.extraConditions = new JsonArray();
            pool.extraConditions.add(c.deepCopy());
        }
    }

    private static boolean mapPoolCondition(JsonObject c, LootPoolData pool) {
        String cond = strOrNull(c, "condition");
        if (cond == null) return false;
        switch (cond) {
            case "minecraft:killed_by_player" -> pool.condition = Condition.KILLED_BY_PLAYER;
            case "minecraft:killed_by_player_or_pet" -> pool.condition = Condition.KILLED_BY_PLAYER_OR_PET;
            case "minecraft:survives_explosion" -> pool.condition = Condition.SURVIVES_EXPLOSION;
            case "minecraft:random_chance" -> {
                pool.condition = Condition.RANDOM_CHANCE;
                pool.chance = doubleOrDefault(c, "chance", pool.chance);
            }
            case "minecraft:random_chance_with_looting" -> {
                pool.condition = Condition.RANDOM_CHANCE_WITH_LOOTING;
                pool.chance = doubleOrDefault(c, "chance", pool.chance);
                pool.lootingMultiplier = doubleOrDefault(c, "looting_multiplier", pool.lootingMultiplier);
            }
            case "minecraft:entity_properties" -> {
                if (!c.toString().contains("is_on_fire")) return false;
                pool.condition = Condition.ON_FIRE;
            }
            default -> { return false; }
        }
        return true;
    }

    private static void parseFunctionIntoEntry(JsonObject fn, LootEntryData entry) {
        String type = strOrNull(fn, "function");
        if (type == null) return;
        switch (type) {
            case "minecraft:set_count" -> {
                JsonElement count = fn.get("count");
                if (count == null) return;
                if (count.isJsonPrimitive()) {
                    entry.minCount = entry.maxCount = count.getAsInt();
                } else if (count.isJsonObject()) {
                    JsonObject range = count.getAsJsonObject();
                    entry.minCount = intOrDefault(range, "min", 1);
                    entry.maxCount = intOrDefault(range, "max", entry.minCount);
                }
            }
            case "minecraft:enchant_with_levels" -> {
                LootFunctionData lf = new LootFunctionData();
                lf.type = FunctionType.ENCHANT_WITH_LEVELS;
                JsonElement levels = fn.get("levels");
                if (levels != null && levels.isJsonPrimitive()) {
                    lf.levelsMin = lf.levelsMax = levels.getAsInt();
                } else if (levels != null && levels.isJsonObject()) {
                    JsonObject range = levels.getAsJsonObject();
                    lf.levelsMin = intOrDefault(range, "min", lf.levelsMin);
                    lf.levelsMax = intOrDefault(range, "max", lf.levelsMax);
                }
                lf.treasure = fn.has("treasure") && fn.get("treasure").getAsBoolean();
                entry.functions.add(lf);
            }
            case "minecraft:set_nbt" -> {
                LootFunctionData lf = new LootFunctionData();
                lf.type = FunctionType.SET_NBT;
                lf.nbt = strOrNull(fn, "tag");
                if (lf.nbt != null) entry.functions.add(lf);
            }
            case "minecraft:looting_enchant" -> {
                LootFunctionData lf = new LootFunctionData();
                lf.type = FunctionType.LOOTING_ENCHANT;
                JsonElement count = fn.get("count");
                if (count != null && count.isJsonPrimitive()) {
                    lf.levelsMin = lf.levelsMax = count.getAsInt();
                } else if (count != null && count.isJsonObject()) {
                    JsonObject range = count.getAsJsonObject();
                    lf.levelsMin = intOrDefault(range, "min", lf.levelsMin);
                    lf.levelsMax = intOrDefault(range, "max", lf.levelsMax);
                }
                entry.functions.add(lf);
            }
            case "minecraft:furnace_smelt" -> {
                LootFunctionData lf = new LootFunctionData();
                lf.type = FunctionType.SMELT;
                lf.smeltOnlyIfOnFire = hasOnFirePredicate(fn);
                entry.functions.add(lf);
            }
            default -> { }
        }
    }

    private static boolean hasOnFirePredicate(JsonObject fn) {
        if (!fn.has("conditions") || !fn.get("conditions").isJsonArray()) return false;
        for (JsonElement condEl : fn.getAsJsonArray("conditions")) {
            if (!condEl.isJsonObject()) continue;
            JsonObject c = condEl.getAsJsonObject();
            if (!"minecraft:entity_properties".equals(strOrNull(c, "condition"))) continue;
            if (!c.has("predicate") || !c.get("predicate").isJsonObject()) continue;
            JsonObject predicate = c.getAsJsonObject("predicate");
            if (!predicate.has("flags") || !predicate.get("flags").isJsonObject()) continue;
            JsonObject flags = predicate.getAsJsonObject("flags");
            if (flags.has("is_on_fire") && flags.get("is_on_fire").getAsBoolean()) return true;
        }
        return false;
    }

    private static String strOrNull(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : null;
    }
    private static int intOrDefault(JsonObject o, String key, int def) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsInt() : def;
    }
    private static double doubleOrDefault(JsonObject o, String key, double def) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsDouble() : def;
    }

    public static List<String> validateLootTable(LootTableEntry e) {
        List<String> errors = new ArrayList<>();
        if (e.targetId == null || e.targetId.isBlank() || !e.targetId.contains(":"))
            errors.add(com.kve.common.Lang.get("gui.kve.loot.err.no_target"));
        if (!e.event.validFor(e.kind))
            errors.add(com.kve.common.Lang.get("gui.kve.loot.err.event_not_valid", e.event.label()));
        if (e.pools.isEmpty())
            errors.add(com.kve.common.Lang.get("gui.kve.loot.err.need_pool"));
        boolean anyEntry = e.pools.stream().anyMatch(p -> !p.entries.isEmpty());
        if (!anyEntry) errors.add(com.kve.common.Lang.get("gui.kve.loot.err.need_item"));
        for (LootPoolData p : e.pools) {
            for (LootEntryData en : p.entries) {
                if (en.itemId == null || en.itemId.isBlank()) errors.add(com.kve.common.Lang.get("gui.kve.loot.err.entry_no_item"));
                if (en.minCount > en.maxCount) errors.add(com.kve.common.Lang.get("gui.kve.loot.err.min_gt_max"));
                if (en.weight < 1) errors.add("El peso ('weight') de \"" + en.itemId + "\" debe ser un numero entero de 1 o mas (Minecraft no admite pesos decimales ni menores a 1 en las loot tables).");
                for (LootFunctionData fn : en.functions) {
                    if ((fn.type == FunctionType.ENCHANT_WITH_LEVELS || fn.type == FunctionType.LOOTING_ENCHANT) && fn.levelsMin > fn.levelsMax) {
                        errors.add(com.kve.common.Lang.get("gui.kve.loot.err.enchant_min_gt_max"));
                    }
                    if (fn.type == FunctionType.SET_NBT) {
                        if (fn.nbt == null || fn.nbt.isBlank()) {
                            errors.add(com.kve.common.Lang.get("gui.kve.loot.err.custom_nbt_empty", com.kve.common.Lang.get("gui.kve.loot.function.custom_nbt")));
                        } else {
                            errors.addAll(SemanticValidator.validateNbt(fn.nbt, com.kve.common.Lang.get("gui.kve.loot.nbt_of_function")));
                        }
                    }
                }
            }
        }

        if (e.targetId != null && !e.targetId.isBlank()) {
            switch (e.kind) {
                case ENTITY -> errors.addAll(SemanticValidator.validateEntityId(e.targetId, "Objetivo"));
                case BLOCK -> errors.addAll(SemanticValidator.validateBlockId(e.targetId, "Objetivo"));
                case CUSTOM_TABLE -> errors.addAll(SemanticValidator.validateNamespacedIdFormat(e.targetId, "Objetivo"));
            }
        }
        for (LootPoolData p : e.pools) {
            for (LootEntryData en : p.entries) {
                if (en.itemId != null && !en.itemId.isBlank())
                    errors.addAll(SemanticValidator.validateItemId(en.itemId, "Item de pool"));
            }
        }

        return errors;
    }

    public static boolean hasCompetingEntries(LootPoolData pool) {
        int validCount = 0;
        for (LootEntryData e : pool.entries) {
            if (e.itemId != null && !e.itemId.isBlank()) validCount++;
            if (validCount >= 2) return true;
        }
        return false;
    }

    /** Result of simulating N rolls of a {@link LootTableEntry}. */
    public static class SimResult {
        /** itemId -> number of times it was the weighted pick across all simulated rolls. */
        public final Map<String, Integer> itemDrops = new LinkedHashMap<>();
        public int totalRolls;
        public int noDropRolls;
    }

    /**
     * Simulates {@code rolls} independent loot-table rolls entirely in
     * Java (no JS is generated or executed), read-only over {@code entry}
     * - it never mutates the entry and works fine on an unsaved draft.
     * Reuses exactly the same weighted-pick logic
     * {@link com.kve.common.lootrules.InteractLootEnforcer} rolls live
     * against real interactions (sequential weight subtraction against a
     * random draw, via {@link #pickWeighted()}) and the same condition fields
     * (chance / lootingMultiplier) written by conditionJson() for the
     * real datapack-backed export, so the odds shown here match what
     * actually happens in-game once the table is saved.
     */
    public static SimResult simulate(LootTableEntry entry, int rolls, int lootingLevel) {
        SimResult result = new SimResult();
        result.totalRolls = Math.max(1, rolls);
        Random rnd = new Random();
        int looting = Math.max(0, Math.min(3, lootingLevel));

        for (int i = 0; i < result.totalRolls; i++) {
            boolean anyDrop = false;
            for (LootPoolData pool : entry.pools) {
                if (pool.entries.isEmpty()) continue;
                if (!poolConditionPasses(pool, looting, rnd)) continue;
                for (int roll = 0; roll < Math.max(1, pool.rolls); roll++) {
                    LootEntryData picked = pickWeighted(pool.entries, rnd);
                    if (picked == null) continue;
                    anyDrop = true;
                    result.itemDrops.merge(picked.itemId, 1, Integer::sum);
                }
            }
            if (!anyDrop) result.noDropRolls++;
        }
        return result;
    }

    private static boolean poolConditionPasses(LootPoolData pool, int lootingLevel, Random rnd) {
        return switch (pool.condition) {
            case NONE, KILLED_BY_PLAYER, KILLED_BY_PLAYER_OR_PET, ON_FIRE, SURVIVES_EXPLOSION -> true;
            case RANDOM_CHANCE -> rnd.nextDouble() < pool.chance;
            case RANDOM_CHANCE_WITH_LOOTING -> rnd.nextDouble() < (pool.chance + pool.lootingMultiplier * lootingLevel);
        };
    }

    private static LootEntryData pickWeighted(List<LootEntryData> entries, Random rnd) {
        List<LootEntryData> valid = new ArrayList<>();
        int totalWeight = 0;
        for (LootEntryData e : entries) {
            if (e.itemId == null || e.itemId.isBlank()) continue;
            valid.add(e);
            totalWeight += Math.max(1, e.weight);
        }
        if (valid.isEmpty() || totalWeight <= 0) return null;
        int r = rnd.nextInt(totalWeight);
        for (LootEntryData e : valid) {
            r -= Math.max(1, e.weight);
            if (r < 0) return e;
        }
        return valid.get(valid.size() - 1);
    }

    private static Path dataDir() { return Paths.get("kubejs", "data"); }
    private static Path manifestFile() { return dataDir().resolve(".shovel_js_loot_manifest.json"); }

    private static Path pathFor(LootTableEntry e) {
        if (!e.event.isDatapackBacked()) return null;
        ResourceLocation rl;
        try { rl = ResourceLocation.parse(e.targetId); } catch (Exception ex) { return null; }
        String subPath = switch (e.kind) {
            case ENTITY -> "entities/" + rl.getPath();
            case BLOCK -> "blocks/" + rl.getPath();
            case CUSTOM_TABLE -> rl.getPath();
        };
        try {
            return com.kve.common.security.KvePathGuard.resolveResource(
                    dataDir(), rl.getNamespace(), subPath, ".json", "loot_table");
        } catch (IllegalArgumentException ex) {
            String warning = "Loot table id invalido (" + ex.getMessage() + "): " + e.targetId;
            LAST_WRITE_WARNINGS.add(warning);
            CraftEditorMod.LOGGER.warn("[Shovel JS] {}", warning);
            return null;
        }
    }

    private static void writeFiles() {
        markApplied();
        LAST_WRITE_WARNINGS.clear();
        Set<String> previouslyWritten = readManifest();
        Set<String> nowWritten = new LinkedHashSet<>();

        for (LootTableEntry e : ENTRIES.values()) {
            if (!e.enabled) continue;
            Path p = pathFor(e);
            if (p == null) continue;
            try {
                Files.createDirectories(p.getParent());
                JsonObject json = buildLootTableJson(e);
                Files.writeString(p, new GsonBuilder().setPrettyPrinting().create().toJson(json),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                nowWritten.add(p.toString());
            } catch (IOException ex) {
                CraftEditorMod.LOGGER.error("[Shovel JS] Loot table write failed for {}: {}", e.targetId, ex.getMessage());
            }
        }

        for (String old : previouslyWritten) {
            if (!nowWritten.contains(old)) {
                try { com.kve.common.security.KvePathGuard.deleteRecordedFile(dataDir(), old); } catch (IOException ignored) {}
            }
        }
        writeManifest(nowWritten);

        deleteLegacyInteractScript();
    }

    private static Path scriptDir() { return KveScriptPaths.SERVER_ROOT; }

    /**
     * Read-only preview of exactly what saving this loot table entry would
     * emit, without touching disk or the ENTRIES map. This exporter has two
     * completely different output shapes depending on the trigger - a
     * vanilla loot_table datapack JSON for DEATH/BREAK/DIRECT, or a JS
     * handler snippet for INTERACT (see the class-level note on why) - so
     * the preview picks whichever one this entry would actually produce.
     * Same pattern as {@code KubeJSExporter#previewRecipeJS}.
     */
    public static String previewLoot(LootTableEntry e) {
        if (e == null) return "// (sin datos de loot table)";
        try {
            if (e.event == TriggerEvent.INTERACT) {
                String hookLabel = e.kind == TargetKind.BLOCK
                        ? "PlayerInteractEvent.RightClickBlock"
                        : "PlayerInteractEvent.EntityInteract";
                StringBuilder sb = new StringBuilder();
                sb.append("// No se genera ningun script - se aplica en vivo via un hook Forge real\n");
                sb.append("// (").append(hookLabel).append(", ver InteractLootEnforcer), leyendo\n");
                sb.append("// este entry directamente en Java. Vista previa de las probabilidades:\n");
                SimResult sim = simulate(e, 1000, 0);
                if (sim.itemDrops.isEmpty()) {
                    sb.append("// (ningun pool tiene items validos configurados todavia)\n");
                } else {
                    for (Map.Entry<String, Integer> drop : sim.itemDrops.entrySet()) {
                        double pct = 100.0 * drop.getValue() / sim.totalRolls;
                        sb.append(String.format(Locale.ROOT, "// %-32s ~%.1f%% por interaccion\n", drop.getKey(), pct));
                    }
                }
                return sb.toString();
            }
            Path p = pathFor(e);
            JsonObject json = buildLootTableJson(e);
            String pretty = new GsonBuilder().setPrettyPrinting().create().toJson(json);
            return "// " + (p == null ? "(id de destino invalido)" : p.toString()) + "\n" + pretty;
        } catch (Exception ex) {
            return "// ERROR generando preview: " + ex.getMessage();
        }
    }

    private static void deleteLegacyInteractScript() {
        try {
            Files.deleteIfExists(scriptDir().resolve("shovel_js_interact_loot.js"));
        } catch (IOException ignored) {}
    }

    /**
     * Real Java entry point for {@link com.kve.common.lootrules.InteractLootEnforcer}:
     * resolves whatever INTERACT entry (if any) is currently staged for
     * {@code kind}+{@code targetId}. {@link LootTableEntry#key()} is
     * {@code kind|event|targetId}, so there can only ever be one INTERACT
     * entry per (kind, targetId) pair - a direct map lookup, no scan
     * needed. Returns {@code null} for a disabled entry too, same as an
     * absent one, so callers never need a separate {@code enabled} check.
     */
    public static LootTableEntry interactEntryFor(TargetKind kind, String targetId) {
        if (kind == null || targetId == null || targetId.isBlank()) return null;
        LootTableEntry e = APPLIED.get(kind.name() + "|" + TriggerEvent.INTERACT.name() + "|" + targetId);
        return (e != null && e.enabled) ? e : null;
    }

    /**
     * Rolls one live interaction against {@code entry}'s pools and hands
     * every weighted-picked entry to {@code picker}, one call per drop.
     * Deliberately reuses {@link #poolConditionPasses()} and
     * {@link #pickWeighted()} - the exact same logic {@link #simulate}
     * already uses for the Loot Simulator screen - so what actually drops
     * in-game on a right-click always matches what the simulator predicted
     * for this entry. {@code lootingLevel} is real (read off the player's
     * held item by the caller), unlike the old JS dispatch table which
     * never applied looting to interact drops at all.
     * <p>
     * Kept free of any {@code ItemStack}/{@code Player} concerns on
     * purpose - resolving an item id and actually giving/dropping the
     * stack is {@link com.kve.common.lootrules.InteractLootEnforcer}'s job,
     * same separation {@code AttackEffectRequirements}/{@code
     * AttackEffectEnforcer} use between "what should happen" and "make it
     * happen for real".
     */
    public static void rollInteractLoot(LootTableEntry entry, int lootingLevel, java.util.function.Consumer<LootEntryData> picker) {
        if (entry == null || picker == null) return;
        Random rnd = new Random();
        int looting = Math.max(0, Math.min(3, lootingLevel));
        for (LootPoolData pool : entry.pools) {
            if (pool.entries.isEmpty()) continue;
            if (!poolConditionPasses(pool, looting, rnd)) continue;
            for (int roll = 0; roll < Math.max(1, pool.rolls); roll++) {
                LootEntryData picked = pickWeighted(pool.entries, rnd);
                if (picked != null) picker.accept(picked);
            }
        }
    }

    static JsonObject buildLootTableJson(LootTableEntry e) {
        JsonObject root = new JsonObject();
        root.addProperty("type", switch (e.kind) {
            case ENTITY -> "minecraft:entity";
            case BLOCK -> "minecraft:block";
            case CUSTOM_TABLE -> "minecraft:generic";
        });

        JsonArray pools = new JsonArray();
        for (LootPoolData pool : e.pools) {
            if (pool.entries.isEmpty()) continue;
            JsonObject poolJson = new JsonObject();
            poolJson.addProperty("rolls", pool.rolls);

            JsonArray entries = new JsonArray();
            for (LootEntryData entry : pool.entries) {
                if (entry.itemId == null || entry.itemId.isBlank()) continue;
                List<String> itemErrors = SemanticValidator.validateItemId(entry.itemId, "Item de pool");
                if (!itemErrors.isEmpty()) {
                    String warning = "Loot table '" + e.targetId + "': entrada '" + entry.itemId
                            + "' omitida - " + String.join("; ", itemErrors);
                    LAST_WRITE_WARNINGS.add(warning);
                    CraftEditorMod.LOGGER.warn("[Shovel JS] {}", warning);
                    continue;
                }
                JsonObject entryJson = new JsonObject();
                entryJson.addProperty("type", "minecraft:item");
                entryJson.addProperty("name", entry.itemId);
                entryJson.addProperty("weight", Math.max(1, entry.weight));
                if (entry.conditions != null && !entry.conditions.isEmpty()) entryJson.add("conditions", entry.conditions.deepCopy());

                JsonArray functions = new JsonArray();
                if (entry.minCount != 1 || entry.maxCount != 1) {
                    JsonObject setCount = new JsonObject();
                    setCount.addProperty("function", "minecraft:set_count");
                    if (entry.minCount == entry.maxCount) {
                        setCount.addProperty("count", entry.minCount);
                    } else {
                        JsonObject range = new JsonObject();
                        range.addProperty("min", Math.min(entry.minCount, entry.maxCount));
                        range.addProperty("max", Math.max(entry.minCount, entry.maxCount));
                        setCount.add("count", range);
                    }
                    functions.add(setCount);
                }
                for (LootFunctionData fn : entry.functions) {
                    JsonElement fnJson = functionJson(fn);
                    if (fnJson != null) functions.add(fnJson);
                }
                if (functions.size() > 0) entryJson.add("functions", functions);
                entries.add(entryJson);
            }
            if (entries.isEmpty()) continue;
            poolJson.add("entries", entries);

            JsonElement cond = conditionJson(pool);
            JsonArray conditions = new JsonArray();
            if (cond != null) conditions.add(cond);
            if (pool.extraConditions != null) for (JsonElement extra : pool.extraConditions) conditions.add(extra.deepCopy());
            if (!conditions.isEmpty()) poolJson.add("conditions", conditions);
            pools.add(poolJson);
        }
        root.add("pools", pools);
        return root;
    }

    static JsonElement functionJson(LootFunctionData fn) {
        return switch (fn.type) {
            case ENCHANT_WITH_LEVELS -> {
                JsonObject o = new JsonObject();
                o.addProperty("function", "minecraft:enchant_with_levels");
                if (fn.levelsMin == fn.levelsMax) {
                    o.addProperty("levels", fn.levelsMin);
                } else {
                    JsonObject range = new JsonObject();
                    range.addProperty("min", Math.min(fn.levelsMin, fn.levelsMax));
                    range.addProperty("max", Math.max(fn.levelsMin, fn.levelsMax));
                    o.add("levels", range);
                }
                o.addProperty("treasure", fn.treasure);
                yield o;
            }
            case SET_NBT -> {
                if (fn.nbt == null || fn.nbt.isBlank()) yield null;
                JsonObject o = new JsonObject();
                o.addProperty("function", "minecraft:set_nbt");
                o.addProperty("tag", fn.nbt);
                yield o;
            }
            case LOOTING_ENCHANT -> {
                JsonObject o = new JsonObject();
                o.addProperty("function", "minecraft:looting_enchant");
                if (fn.levelsMin == fn.levelsMax) {
                    o.addProperty("count", fn.levelsMin);
                } else {
                    JsonObject range = new JsonObject();
                    range.addProperty("min", Math.min(fn.levelsMin, fn.levelsMax));
                    range.addProperty("max", Math.max(fn.levelsMin, fn.levelsMax));
                    o.add("count", range);
                }
                yield o;
            }
            case SMELT -> {
                JsonObject o = new JsonObject();
                o.addProperty("function", "minecraft:furnace_smelt");
                if (fn.smeltOnlyIfOnFire) {
                    JsonObject cond = new JsonObject();
                    cond.addProperty("condition", "minecraft:entity_properties");
                    cond.addProperty("entity", "this");
                    JsonObject predicate = new JsonObject();
                    JsonObject flags = new JsonObject();
                    flags.addProperty("is_on_fire", true);
                    predicate.add("flags", flags);
                    cond.add("predicate", predicate);
                    JsonArray conditions = new JsonArray();
                    conditions.add(cond);
                    o.add("conditions", conditions);
                }
                yield o;
            }
        };
    }

    static JsonElement conditionJson(LootPoolData pool) {
        return switch (pool.condition) {
            case NONE -> null;
            case KILLED_BY_PLAYER -> {
                JsonObject o = new JsonObject();
                o.addProperty("condition", "minecraft:killed_by_player");
                yield o;
            }
            case KILLED_BY_PLAYER_OR_PET -> {
                JsonObject o = new JsonObject();
                o.addProperty("condition", "minecraft:killed_by_player_or_pet");
                yield o;
            }
            case ON_FIRE -> {
                JsonObject o = new JsonObject();
                o.addProperty("condition", "minecraft:entity_properties");
                o.addProperty("entity", "this");
                JsonObject predicate = new JsonObject();
                JsonObject flags = new JsonObject();
                flags.addProperty("is_on_fire", true);
                predicate.add("flags", flags);
                o.add("predicate", predicate);
                yield o;
            }
            case RANDOM_CHANCE -> {
                JsonObject o = new JsonObject();
                o.addProperty("condition", "minecraft:random_chance");
                o.addProperty("chance", pool.chance);
                yield o;
            }
            case RANDOM_CHANCE_WITH_LOOTING -> {
                JsonObject o = new JsonObject();
                o.addProperty("condition", "minecraft:random_chance_with_looting");
                o.addProperty("chance", pool.chance);
                o.addProperty("looting_multiplier", pool.lootingMultiplier);
                yield o;
            }
            case SURVIVES_EXPLOSION -> {
                JsonObject o = new JsonObject();
                o.addProperty("condition", "minecraft:survives_explosion");
                yield o;
            }
        };
    }

    public static java.util.List<String> getManifestPaths() {
        return new java.util.ArrayList<>(readManifest());
    }

    private static Set<String> readManifest() {
        try {
            if (!Files.exists(manifestFile())) return new LinkedHashSet<>();
            String json = Files.readString(manifestFile(), StandardCharsets.UTF_8);
            JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
            Set<String> out = new LinkedHashSet<>();
            for (JsonElement el : arr) out.add(el.getAsString());
            return out;
        } catch (Exception ex) {
            return new LinkedHashSet<>();
        }
    }

    private static void writeManifest(Set<String> paths) {
        try {
            Files.createDirectories(dataDir());
            JsonArray arr = new JsonArray();
            for (String p : paths) arr.add(p);
            Files.writeString(manifestFile(), new Gson().toJson(arr), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ex) {
            CraftEditorMod.LOGGER.error("[Shovel JS] Loot manifest write failed: {}", ex.getMessage());
        }
    }
}
