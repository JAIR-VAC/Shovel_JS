package com.kve.common.kubejs;

import com.kve.common.util.SafeFiles;
import com.kve.common.util.JsStrings;
import com.google.gson.*;
import com.kve.CraftEditorMod;
import com.kve.common.event.EventEntry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class EventExporter {

    private EventExporter() {}

    private static final Map<String, EventEntry> ENTRIES = new LinkedHashMap<>();
    private static final Map<String, Deque<EventEntry>> HISTORY =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Deque<EventEntry>> eldest) {
                    return size() > Math.max(1, com.kve.common.config.KVEConfig.undoHistoryMaxKeys);
                }
            };

    public static Collection<EventEntry> getEntries() { return Collections.unmodifiableCollection(ENTRIES.values()); }
    public static EventEntry getEntry(String key) { return ENTRIES.get(key); }
    public static void clearAll() { ENTRIES.clear(); HISTORY.clear(); }
    public static void loadEntry(EventEntry e) { ENTRIES.put(e.key(), e); }

    private static void pushHistory(String key, EventEntry snapshot) {
        int max = com.kve.common.config.KVEConfig.undoHistorySize;
        if (max <= 0) return;
        Deque<EventEntry> h = HISTORY.computeIfAbsent(key, k -> new ArrayDeque<>());
        h.addFirst(snapshot.copy());
        while (h.size() > max) h.removeLast();
    }

    public static boolean canUndo(String key) {
        Deque<EventEntry> h = HISTORY.get(key);
        return h != null && !h.isEmpty();
    }

    public static EventEntry undo(String key) {
        Deque<EventEntry> h = HISTORY.get(key);
        if (h == null || h.isEmpty()) return null;
        EventEntry previous = h.removeFirst();
        ENTRIES.put(key, previous);
        writeAll();
        persistState();
        return previous;
    }

    /** Stages an event into ENTRIES (in-memory only, no script/state write). */
    public static void stageEvent(EventEntry e) {
        EventEntry previous = ENTRIES.get(e.key());
        if (previous != null) pushHistory(e.key(), previous);
        ENTRIES.put(e.key(), e);
    }

    /** Undoes a stageEvent(): drops the staged entry and restores whatever was there before (null if it was new). */
    public static void discardStagedEvent(String key, EventEntry previousOrNull) {
        if (previousOrNull != null) ENTRIES.put(key, previousOrNull);
        else ENTRIES.remove(key);
    }

    /** Stages an event deletion (in-memory only). */
    public static void stageDeleteEvent(String key) {
        ENTRIES.remove(key);
        HISTORY.remove(key);
    }

    /** Stages an undo (in-memory only, no script/state write) - see stageEvent(). Returns the restored entry, or null if there's nothing to undo. */
    public static EventEntry stageUndo(String key) {
        Deque<EventEntry> h = HISTORY.get(key);
        if (h == null || h.isEmpty()) return null;
        EventEntry restored = h.removeFirst();
        ENTRIES.put(key, restored);
        return restored;
    }

    /** Undoes a stageUndo(): pushes the restored entry back onto the undo history and restores whatever ENTRIES had before the undo (null if it was new). */
    public static void discardStagedUndo(String key, EventEntry restored, EventEntry beforeUndoOrNull) {
        Deque<EventEntry> h = HISTORY.computeIfAbsent(key, k -> new ArrayDeque<>());
        h.addFirst(restored);
        if (beforeUndoOrNull != null) ENTRIES.put(key, beforeUndoOrNull);
        else ENTRIES.remove(key);
    }

    /** Writes the currently staged ENTRIES to disk (KubeJS script + KVE events state). Only called from a PendingChange's onApply. */
    public static void commitEvents() {
        writeAll();
        persistState();
    }

    public static List<String> validateEvent(EventEntry e) {
        if (e == null) return List.of("Evento nulo.");
        return e.validate();
    }

    private static Path configDir()  { return KveScriptPaths.CONFIG_ROOT; }
    private static Path stateFile()  { return configDir().resolve("shovel_js_events_state.json"); }
    private static Path scriptDir()  { return KveScriptPaths.eventsDir(); }
    private static Path scriptFile() { return KveScriptPaths.eventsFile(); }

    private static boolean loaded = false;
    private static long lastMtime = 0L;

    /** Same load-if-changed pattern as {@code CraftEditorState.loadIfNeeded()}, but scoped to just this file - so opening {@code EventEditorScreen} doesn't depend on the main state file's load order. */
    public static void loadIfNeeded() {
        if (loaded) {
            try {
                long mtime = Files.exists(stateFile()) ? Files.getLastModifiedTime(stateFile()).toMillis() : 0;
                if (mtime == lastMtime) return;
            } catch (IOException ignored) { return; }
        }
        load();
    }

    public static void forceReload() {
        loaded = false;
        load();
    }

    private static void load() {
        loaded = true;
        ENTRIES.clear();
        HISTORY.clear();
        if (!Files.exists(stateFile())) {
            lastMtime = 0;
            return;
        }
        try {
            lastMtime = Files.getLastModifiedTime(stateFile()).toMillis();
            String json = Files.readString(stateFile(), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("events")) {
                for (JsonElement el : root.getAsJsonArray("events")) {
                    try {
                        EventEntry e = entryFromJson(el.getAsJsonObject());
                        if (e.id != null && !e.id.isBlank()) loadEntry(e);
                    } catch (Exception ex) {
                        CraftEditorMod.LOGGER.warn("[Shovel JS] Skipped event: {}", ex.getMessage());
                    }
                }
            }
            CraftEditorMod.LOGGER.info("[Shovel JS] Events state loaded from {}", stateFile());
        } catch (Exception ex) {
            CraftEditorMod.LOGGER.error("[Shovel JS] Failed to load events state: {}", ex.getMessage());
        }
    }

    private static void persistState() {
        try {
            Files.createDirectories(configDir());
            JsonObject root = new JsonObject();
            JsonArray arr = new JsonArray();
            for (EventEntry e : ENTRIES.values()) arr.add(entryToJson(e));
            root.add("events", arr);
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(root);
            SafeFiles.writeAtomic(stateFile(), json);
            try { lastMtime = Files.getLastModifiedTime(stateFile()).toMillis(); } catch (IOException ex) {
                CraftEditorMod.LOGGER.debug("[Shovel JS] no se pudo releer el mtime de {} tras guardar: {}", stateFile(), ex.toString());
            }
        } catch (IOException ex) {
            CraftEditorMod.LOGGER.error("[Shovel JS] Events state save failed: {}", ex.getMessage());
        }
    }

    public static JsonObject entryToJson(EventEntry e) {
        JsonObject o = new JsonObject();
        o.addProperty("id", e.id);
        o.addProperty("displayName", e.displayName);
        o.addProperty("enabled", e.enabled);
        o.addProperty("category", e.category);
        o.addProperty("requiresRestart", e.requiresRestart);

        JsonObject t = new JsonObject();
        t.addProperty("type", e.trigger.type.name());
        t.addProperty("intervalSeconds", e.trigger.intervalSeconds);
        t.addProperty("entityId", e.trigger.entityId);
        t.addProperty("count", e.trigger.count);
        t.addProperty("commandName", e.trigger.commandName);
        t.addProperty("biomeId", e.trigger.biomeId);
        t.addProperty("deathMessageContains", e.trigger.deathMessageContains);
        t.addProperty("minDamage", e.trigger.minDamage);
        t.addProperty("fromDimension", e.trigger.fromDimension);
        t.addProperty("toDimension", e.trigger.toDimension);
        t.addProperty("blockId", e.trigger.blockId);
        t.addProperty("itemId", e.trigger.itemId);
        t.addProperty("xpLevel", e.trigger.xpLevel);
        t.addProperty("xpMinDelta", e.trigger.xpMinDelta);
        t.addProperty("advancementId", e.trigger.advancementId);
        t.addProperty("effectId", e.trigger.effectId);
        t.addProperty("entityTypeId", e.trigger.entityTypeId);
        t.addProperty("fluidId", e.trigger.fluidId);
        o.add("trigger", t);

        JsonArray conds = new JsonArray();
        for (EventEntry.Condition c : e.conditions) {
            JsonObject co = new JsonObject();
            co.addProperty("type", c.type.name());
            co.addProperty("dimensionId", c.dimensionId);
            co.addProperty("permissionNode", c.permissionNode);
            co.addProperty("gamemode", c.gamemode.name());
            co.addProperty("timeOfDay", c.timeOfDay.name());
            co.addProperty("yMin", c.yMin);
            co.addProperty("yMax", c.yMax);
            co.addProperty("biomeId", c.biomeId);
            co.addProperty("xpLevelMin", c.xpLevelMin);
            co.addProperty("hasItemId", c.hasItemId);
            co.addProperty("hasItemMinCount", c.hasItemMinCount);
            co.addProperty("randomChance", c.randomChance);
            co.addProperty("healthMax", c.healthMax);
            co.addProperty("healthMin", c.healthMin);
            co.addProperty("foodLevelMin", c.foodLevelMin);
            co.addProperty("hasEffectId", c.hasEffectId);
            co.addProperty("equippedItemId", c.equippedItemId);
            co.addProperty("equippedSlot", c.equippedSlot.name());
            co.addProperty("advancementId", c.advancementId);
            co.addProperty("weatherState", c.weatherState.name());
            co.addProperty("moonPhase", c.moonPhase);
            co.addProperty("dayOfWeek", c.dayOfWeek.name());
            co.addProperty("dateStartMd", c.dateStartMd);
            co.addProperty("dateEndMd", c.dateEndMd);
            co.addProperty("hourStart", c.hourStart);
            co.addProperty("hourEnd", c.hourEnd);
            co.addProperty("stageId", c.stageId);
            co.addProperty("flagId", c.flagId);
            co.addProperty("playtimeMinSeconds", c.playtimeMinSeconds);
            co.addProperty("entityEquippedItemId", c.entityEquippedItemId);
            conds.add(co);
        }
        o.add("conditions", conds);

        JsonArray actions = new JsonArray();
        for (EventEntry.Action a : e.actions) {
            JsonObject ao = new JsonObject();
            ao.addProperty("type", a.type.name());
            ao.addProperty("text", a.text);
            ao.addProperty("title", a.title);
            ao.addProperty("subtitle", a.subtitle);
            ao.addProperty("itemId", a.itemId);
            ao.addProperty("itemCount", a.itemCount);
            ao.addProperty("nbtString", a.nbtString);
            ao.addProperty("command", a.command);
            ao.addProperty("soundId", a.soundId);
            ao.addProperty("volume", a.volume);
            ao.addProperty("pitch", a.pitch);
            ao.addProperty("spawnEntityId", a.spawnEntityId);
            ao.addProperty("spawnCount", a.spawnCount);
            ao.addProperty("spawnRadius", a.spawnRadius);
            ao.addProperty("xpLevels", a.xpLevels);
            ao.addProperty("effectId", a.effectId);
            ao.addProperty("effectDurationTicks", a.effectDurationTicks);
            ao.addProperty("effectAmplifier", a.effectAmplifier);
            ao.addProperty("teleportDim", a.teleportDim);
            ao.addProperty("teleportX", a.teleportX);
            ao.addProperty("teleportY", a.teleportY);
            ao.addProperty("teleportZ", a.teleportZ);
            ao.addProperty("teleportYaw", a.teleportYaw);
            ao.addProperty("teleportPitch", a.teleportPitch);
            ao.addProperty("xpPoints", a.xpPoints);
            ao.addProperty("setXpLevel", a.setXpLevel);
            ao.addProperty("stageId", a.stageId);
            ao.addProperty("healFull", a.healFull);
            ao.addProperty("healAmount", a.healAmount);
            ao.addProperty("setFoodLevel", a.setFoodLevel);
            ao.addProperty("setFoodSaturation", a.setFoodSaturation);
            ao.addProperty("setFoodSaturationEnabled", a.setFoodSaturationEnabled);
            ao.addProperty("setHealthAmount", a.setHealthAmount);
            ao.addProperty("bossbarText", a.bossbarText);
            ao.addProperty("bossbarColor", a.bossbarColor.name());
            ao.addProperty("bossbarSeconds", a.bossbarSeconds);
            ao.addProperty("particleId", a.particleId);
            ao.addProperty("particleCount", a.particleCount);
            ao.addProperty("particleSpread", a.particleSpread);
            ao.addProperty("setTimeMode", a.setTimeMode.name());
            ao.addProperty("setTimeValue", a.setTimeValue);
            ao.addProperty("setWeatherMode", a.setWeatherMode.name());
            ao.addProperty("setWeatherDurationTicks", a.setWeatherDurationTicks);
            ao.addProperty("explosionPower", a.explosionPower);
            ao.addProperty("commandAsPlayer", a.commandAsPlayer);
            ao.addProperty("moneyAmount", a.moneyAmount);
            ao.addProperty("currencyKey", a.currencyKey);
            ao.addProperty("customEventId", a.customEventId);
            ao.addProperty("grantAdvancementId", a.grantAdvancementId);
            ao.addProperty("removeEffectId", a.removeEffectId);
            ao.addProperty("setSpawnDim", a.setSpawnDim);
            ao.addProperty("setSpawnX", a.setSpawnX);
            ao.addProperty("setSpawnY", a.setSpawnY);
            ao.addProperty("setSpawnZ", a.setSpawnZ);
            ao.addProperty("lootTableId", a.lootTableId);
            actions.add(ao);
        }
        o.add("actions", actions);

        JsonObject lim = new JsonObject();
        lim.addProperty("oncePerPlayer", e.limits.oncePerPlayer);
        lim.addProperty("cooldownSeconds", e.limits.cooldownSeconds);
        lim.addProperty("maxGlobalStarts", e.limits.maxGlobalStarts);
        o.add("limits", lim);

        return o;
    }

    public static EventEntry entryFromJson(JsonObject o) {
        EventEntry e = new EventEntry();
        e.id = str(o, "id", "");
        e.displayName = str(o, "displayName", "");
        e.enabled = !o.has("enabled") || bool(o, "enabled", true);
        e.category = str(o, "category", "");
        e.requiresRestart = bool(o, "requiresRestart", false);

        if (o.has("trigger")) {
            JsonObject t = o.getAsJsonObject("trigger");
            e.trigger.type = enumVal(t, "type", EventEntry.TriggerType.class, EventEntry.TriggerType.FIRST_JOIN);
            e.trigger.intervalSeconds = t.has("intervalSeconds") ? t.get("intervalSeconds").getAsInt() : 60;
            e.trigger.entityId = str(t, "entityId", "");
            e.trigger.count = t.has("count") ? t.get("count").getAsInt() : 1;
            e.trigger.commandName = str(t, "commandName", "");
            e.trigger.biomeId = str(t, "biomeId", "");
            e.trigger.deathMessageContains = str(t, "deathMessageContains", "");
            e.trigger.minDamage = t.has("minDamage") ? t.get("minDamage").getAsFloat() : 1.0f;
            e.trigger.fromDimension = str(t, "fromDimension", "");
            e.trigger.toDimension = str(t, "toDimension", "");
            e.trigger.blockId = str(t, "blockId", "");
            e.trigger.itemId = str(t, "itemId", "");
            e.trigger.xpLevel = t.has("xpLevel") ? t.get("xpLevel").getAsInt() : 1;
            e.trigger.xpMinDelta = t.has("xpMinDelta") ? t.get("xpMinDelta").getAsInt() : 1;
            e.trigger.advancementId = str(t, "advancementId", "");
            e.trigger.effectId = str(t, "effectId", "");
            e.trigger.entityTypeId = str(t, "entityTypeId", "");
            e.trigger.fluidId = str(t, "fluidId", "");
        }

        if (o.has("conditions")) {
            for (JsonElement el : o.getAsJsonArray("conditions")) {
                JsonObject co = el.getAsJsonObject();
                EventEntry.Condition c = new EventEntry.Condition();
                c.type = enumVal(co, "type", EventEntry.ConditionType.class, EventEntry.ConditionType.DIMENSION);
                c.dimensionId = str(co, "dimensionId", "");
                c.permissionNode = str(co, "permissionNode", "");
                c.gamemode = enumVal(co, "gamemode", EventEntry.GameMode.class, EventEntry.GameMode.SURVIVAL);
                c.timeOfDay = enumVal(co, "timeOfDay", EventEntry.TimeOfDay.class, EventEntry.TimeOfDay.DAY);
                c.yMin = co.has("yMin") ? co.get("yMin").getAsInt() : 0;
                c.yMax = co.has("yMax") ? co.get("yMax").getAsInt() : 320;
                c.biomeId = str(co, "biomeId", "");
                c.xpLevelMin = co.has("xpLevelMin") ? co.get("xpLevelMin").getAsInt() : 0;
                c.hasItemId = str(co, "hasItemId", "");
                c.hasItemMinCount = co.has("hasItemMinCount") ? co.get("hasItemMinCount").getAsInt() : 1;
                c.randomChance = co.has("randomChance") ? co.get("randomChance").getAsDouble() : 1.0;
                c.healthMax = co.has("healthMax") ? co.get("healthMax").getAsFloat() : 20.0f;
                c.healthMin = co.has("healthMin") ? co.get("healthMin").getAsFloat() : 0.0f;
                c.foodLevelMin = co.has("foodLevelMin") ? co.get("foodLevelMin").getAsInt() : 0;
                c.hasEffectId = str(co, "hasEffectId", "");
                c.equippedItemId = str(co, "equippedItemId", "");
                c.equippedSlot = enumVal(co, "equippedSlot", EventEntry.EquipSlot.class, EventEntry.EquipSlot.MAINHAND);
                c.advancementId = str(co, "advancementId", "");
                c.weatherState = enumVal(co, "weatherState", EventEntry.WeatherState.class, EventEntry.WeatherState.CLEAR);
                c.moonPhase = str(co, "moonPhase", "0");
                c.dayOfWeek = enumVal(co, "dayOfWeek", EventEntry.WeekDay.class, EventEntry.WeekDay.MON);
                c.dateStartMd = str(co, "dateStartMd", "");
                c.dateEndMd = str(co, "dateEndMd", "");
                c.hourStart = co.has("hourStart") ? co.get("hourStart").getAsInt() : 0;
                c.hourEnd = co.has("hourEnd") ? co.get("hourEnd").getAsInt() : 23;
                c.stageId = str(co, "stageId", "");
                c.flagId = str(co, "flagId", "");
                c.playtimeMinSeconds = co.has("playtimeMinSeconds") ? co.get("playtimeMinSeconds").getAsInt() : 0;
                c.entityEquippedItemId = str(co, "entityEquippedItemId", "");
                e.conditions.add(c);
            }
        }

        if (o.has("actions")) {
            for (JsonElement el : o.getAsJsonArray("actions")) {
                JsonObject ao = el.getAsJsonObject();
                EventEntry.Action a = new EventEntry.Action();
                a.type = enumVal(ao, "type", EventEntry.ActionType.class, EventEntry.ActionType.MESSAGE);
                a.text = str(ao, "text", "");
                a.title = str(ao, "title", "");
                a.subtitle = str(ao, "subtitle", "");
                a.itemId = str(ao, "itemId", "");
                a.itemCount = ao.has("itemCount") ? ao.get("itemCount").getAsInt() : 1;
                a.nbtString = str(ao, "nbtString", "");
                a.command = str(ao, "command", "");
                a.soundId = str(ao, "soundId", "");
                a.volume = ao.has("volume") ? ao.get("volume").getAsFloat() : 1.0f;
                a.pitch = ao.has("pitch") ? ao.get("pitch").getAsFloat() : 1.0f;
                a.spawnEntityId = str(ao, "spawnEntityId", "");
                a.spawnCount = ao.has("spawnCount") ? ao.get("spawnCount").getAsInt() : 1;
                a.spawnRadius = ao.has("spawnRadius") ? ao.get("spawnRadius").getAsDouble() : 2.0;
                a.xpLevels = ao.has("xpLevels") ? ao.get("xpLevels").getAsInt() : 1;
                a.effectId = str(ao, "effectId", "");
                a.effectDurationTicks = ao.has("effectDurationTicks") ? ao.get("effectDurationTicks").getAsInt() : 200;
                a.effectAmplifier = ao.has("effectAmplifier") ? ao.get("effectAmplifier").getAsInt() : 0;
                a.teleportDim = str(ao, "teleportDim", "");
                a.teleportX = ao.has("teleportX") ? ao.get("teleportX").getAsDouble() : 0;
                a.teleportY = ao.has("teleportY") ? ao.get("teleportY").getAsDouble() : 64;
                a.teleportZ = ao.has("teleportZ") ? ao.get("teleportZ").getAsDouble() : 0;
                a.teleportYaw = ao.has("teleportYaw") ? ao.get("teleportYaw").getAsFloat() : 0;
                a.teleportPitch = ao.has("teleportPitch") ? ao.get("teleportPitch").getAsFloat() : 0;
                a.xpPoints = ao.has("xpPoints") ? ao.get("xpPoints").getAsInt() : 1;
                a.setXpLevel = ao.has("setXpLevel") ? ao.get("setXpLevel").getAsInt() : 0;
                a.stageId = str(ao, "stageId", "");
                a.healFull = bool(ao, "healFull", true);
                a.healAmount = ao.has("healAmount") ? ao.get("healAmount").getAsFloat() : 1.0f;
                a.setFoodLevel = ao.has("setFoodLevel") ? ao.get("setFoodLevel").getAsInt() : 20;
                a.setFoodSaturation = ao.has("setFoodSaturation") ? ao.get("setFoodSaturation").getAsFloat() : 0.0f;
                a.setFoodSaturationEnabled = bool(ao, "setFoodSaturationEnabled", false);
                a.setHealthAmount = ao.has("setHealthAmount") ? ao.get("setHealthAmount").getAsFloat() : 20.0f;
                a.bossbarText = str(ao, "bossbarText", "");
                a.bossbarColor = enumVal(ao, "bossbarColor", EventEntry.BossbarColor.class, EventEntry.BossbarColor.PURPLE);
                a.bossbarSeconds = ao.has("bossbarSeconds") ? ao.get("bossbarSeconds").getAsInt() : 10;
                a.particleId = str(ao, "particleId", "");
                a.particleCount = ao.has("particleCount") ? ao.get("particleCount").getAsInt() : 10;
                a.particleSpread = ao.has("particleSpread") ? ao.get("particleSpread").getAsDouble() : 0.5;
                a.setTimeMode = enumVal(ao, "setTimeMode", EventEntry.SetTimeMode.class, EventEntry.SetTimeMode.DAY);
                a.setTimeValue = ao.has("setTimeValue") ? ao.get("setTimeValue").getAsLong() : 1000L;
                a.setWeatherMode = enumVal(ao, "setWeatherMode", EventEntry.WeatherState.class, EventEntry.WeatherState.CLEAR);
                a.setWeatherDurationTicks = ao.has("setWeatherDurationTicks") ? ao.get("setWeatherDurationTicks").getAsInt() : 0;
                a.explosionPower = ao.has("explosionPower") ? ao.get("explosionPower").getAsFloat() : 2.0f;
                a.commandAsPlayer = str(ao, "commandAsPlayer", "");
                a.moneyAmount = ao.has("moneyAmount") ? ao.get("moneyAmount").getAsDouble() : 1.0;
                a.currencyKey = str(ao, "currencyKey", "");
                a.customEventId = str(ao, "customEventId", "");
                a.grantAdvancementId = str(ao, "grantAdvancementId", "");
                a.removeEffectId = str(ao, "removeEffectId", "");
                a.setSpawnDim = str(ao, "setSpawnDim", "");
                a.setSpawnX = ao.has("setSpawnX") ? ao.get("setSpawnX").getAsDouble() : 0;
                a.setSpawnY = ao.has("setSpawnY") ? ao.get("setSpawnY").getAsDouble() : 64;
                a.setSpawnZ = ao.has("setSpawnZ") ? ao.get("setSpawnZ").getAsDouble() : 0;
                a.lootTableId = str(ao, "lootTableId", "");
                e.actions.add(a);
            }
        }

        if (o.has("limits")) {
            JsonObject lim = o.getAsJsonObject("limits");
            e.limits.oncePerPlayer = bool(lim, "oncePerPlayer", false);
            e.limits.cooldownSeconds = lim.has("cooldownSeconds") ? lim.get("cooldownSeconds").getAsInt() : 0;
            e.limits.maxGlobalStarts = lim.has("maxGlobalStarts") ? lim.get("maxGlobalStarts").getAsInt() : 0;
        }

        return e;
    }

    private static String str(JsonObject o, String k, String def) {
        try { return o.has(k) ? o.get(k).getAsString() : def; } catch (Exception e) { return def; }
    }

    private static boolean bool(JsonObject o, String k, boolean def) {
        try { return o.has(k) ? o.get(k).getAsBoolean() : def; } catch (Exception e) { return def; }
    }

    private static <E extends Enum<E>> E enumVal(JsonObject o, String k, Class<E> cls, E def) {
        try { return o.has(k) ? Enum.valueOf(cls, o.get(k).getAsString()) : def; }
        catch (Exception e) { return def; }
    }

    private static final int KVE_EVENT_COMMAND_PERMISSION_LEVEL = 2;

    private static String safe(String id) {
        String s = id == null ? "evt" : id.replaceAll("[^a-zA-Z0-9_]", "_");
        return s.isEmpty() ? "evt" : s;
    }

    private static void writeAll() {
        if (!ScriptWriteGuard.writeIfValid(scriptFile(), buildEventsScript(), "shovel_js_events")) {
            throw new IllegalStateException("Write rejected by ScriptWriteGuard for " + scriptFile().getFileName());
        }
        deleteLegacyEventsScript();
        writeForgeBridgeIfNeeded();
    }

    static final String FORGE_BRIDGE_SCRIPT =
            "// Shovel JS forge bridge - generado por Shovel JS, no editar a mano.\n"
            + "// Reenvia eventos Forge sin equivalente en KubeJS al server script de eventos Shovel JS\n"
            + "// (handlers en global['shovel_js_fx_*']). Requiere reiniciar el juego la primera vez.\n"
            + "function shovel_js_fb(name, cls) {\n"
            + "  try {\n"
            + "    ForgeEvents.onEvent(cls, function (event) {\n"
            + "      try { var h = global['shovel_js_fx_' + name]; if (h) h(event) } catch (ex) { console.error('[Shovel JS] forge bridge ' + name + ': ' + ex) }\n"
            + "    })\n"
            + "  } catch (ex) { console.error('[Shovel JS] no se pudo registrar ' + cls + ': ' + ex) }\n"
            + "}\n"
            + "shovel_js_fb('tame', 'net.minecraftforge.event.entity.living.AnimalTameEvent')\n"
            + "shovel_js_fb('breed', 'net.minecraftforge.event.entity.living.BabyEntitySpawnEvent')\n"
            + "shovel_js_fb('fish', 'net.minecraftforge.event.entity.player.ItemFishedEvent')\n"
            + "shovel_js_fb('trade', 'net.minecraftforge.event.entity.player.TradeWithVillagerEvent')\n"
            + "shovel_js_fb('bucket', 'net.minecraftforge.event.entity.player.FillBucketEvent')\n";

    private static final Set<EventEntry.TriggerType> FORGE_BRIDGE_TRIGGERS = EnumSet.of(
            EventEntry.TriggerType.TAMING, EventEntry.TriggerType.BREEDING,
            EventEntry.TriggerType.FISHING_CATCH, EventEntry.TriggerType.VILLAGER_TRADE,
            EventEntry.TriggerType.EMPTY_BUCKET, EventEntry.TriggerType.FILL_BUCKET);

    /** True si el trigger depende del startup script puente (ver {@link #FORGE_BRIDGE_SCRIPT}). */
    public static boolean needsForgeBridge(EventEntry.TriggerType t) {
        return FORGE_BRIDGE_TRIGGERS.contains(t);
    }

    /** True si el puente ya esta escrito en disco con el contenido actual (no hace falta reiniciar para usarlo). */
    public static boolean forgeBridgeInstalled() {
        try {
            Path f = KveScriptPaths.eventsForgeBridgeFile();
            return Files.exists(f) && Files.readString(f, StandardCharsets.UTF_8).equals(FORGE_BRIDGE_SCRIPT);
        } catch (IOException ex) {
            return false;
        }
    }

    private static void writeForgeBridgeIfNeeded() {
        boolean needed = false;
        for (EventEntry e : ENTRIES.values()) {
            if (e.enabled && needsForgeBridge(e.trigger.type)) { needed = true; break; }
        }
        if (!needed || forgeBridgeInstalled()) return;
        if (!ScriptWriteGuard.writeIfValid(KveScriptPaths.eventsForgeBridgeFile(), FORGE_BRIDGE_SCRIPT, "shovel_js_events_forge_bridge")) {
            CraftEditorMod.LOGGER.error("[Shovel JS] No se pudo escribir el puente Forge de eventos ({})", KveScriptPaths.eventsForgeBridgeFile());
        } else {
            CraftEditorMod.LOGGER.info("[Shovel JS] Puente Forge de eventos instalado en {} - reiniciar el juego para activarlo", KveScriptPaths.eventsForgeBridgeFile());
        }
    }

    private static void deleteLegacyEventsScript() {
        try {
            Files.deleteIfExists(KveScriptPaths.SERVER_ROOT.resolve("shovel_js_events.js"));
        } catch (IOException ignored) {}
    }

    /** Public boot-time entry point for {@code CraftEditorMod#commonSetup} - same cleanup as {@link #deleteLegacyEventsScript()}, reachable before any Event Editor Save this session. */
    public static void deleteLegacyEventsScriptOnBoot() { deleteLegacyEventsScript(); }

    static String buildEventsScript() {
        Map<EventEntry.TriggerType, List<EventEntry>> byTrigger = new EnumMap<>(EventEntry.TriggerType.class);
        List<EventEntry> enabled = new ArrayList<>();
        for (EventEntry e : ENTRIES.values()) {
            if (!e.enabled) continue;
            if (!e.validateStructural().isEmpty()) continue;
            enabled.add(e);
            byTrigger.computeIfAbsent(e.trigger.type, k -> new ArrayList<>()).add(e);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("// shovel_js_events.js - generado por Shovel JS, no editar a mano\n");
        sb.append("// Aplicar con: /reload\n\n");

        for (EventEntry e : ENTRIES.values()) {
            if (!e.enabled) sb.append("// DISABLED: ").append(e.id).append("\n");
        }
        if (!ENTRIES.isEmpty()) sb.append("\n");

        sb.append("var shovel_js_serverRef = null\n");
        sb.append("ServerEvents.loaded(event => { shovel_js_serverRef = event.server })\n\n");

        sb.append("function shovel_js_server(player) {\n");
        sb.append("  try { if (player && player.server) return player.server } catch (ex) {}\n");
        sb.append("  return shovel_js_serverRef\n");
        sb.append("}\n\n");
        sb.append("function shovel_js_cmd(player, cmd) {\n");
        sb.append("  var shovel_js_s = shovel_js_server(player)\n");
        sb.append("  if (shovel_js_s) shovel_js_s.runCommandSilent(cmd)\n");
        sb.append("}\n\n");
        sb.append("function shovel_js_uuid(player) { return String(player.uuid.toString()) }\n\n");
        sb.append("function shovel_js_dim(player) {\n");
        sb.append("  try { return String(player.level.dimension) } catch (ex) { return '' }\n");
        sb.append("}\n\n");
        sb.append("function shovel_js_biomeHolder(player) { return player.level.getBiome(player.blockPosition()) }\n");
        sb.append("function shovel_js_inBiome(player, id) {\n");
        sb.append("  try {\n");
        sb.append("    var shovel_js_h = shovel_js_biomeHolder(player)\n");
        sb.append("    if (id.charAt(0) === '#') {\n");
        sb.append("      var shovel_js_tag = id.substring(1)\n");
        sb.append("      var shovel_js_it = shovel_js_h.tags().iterator()\n");
        sb.append("      while (shovel_js_it.hasNext()) { if (String(shovel_js_it.next().location()) === shovel_js_tag) return true }\n");
        sb.append("      return false\n");
        sb.append("    }\n");
        sb.append("    return String(shovel_js_h.unwrapKey().get().location()) === id\n");
        sb.append("  } catch (ex) { return false }\n");
        sb.append("}\n\n");
        sb.append("function shovel_js_gameMode(player) {\n");
        sb.append("  try { return String(player.gameMode.getGameModeForPlayer().getName()) } catch (ex) {}\n");
        sb.append("  try { if (player.isSpectator()) return 'spectator'; if (player.isCreative()) return 'creative' } catch (ex) {}\n");
        sb.append("  return ''\n");
        sb.append("}\n\n");

        sb.append("function shovel_js_isDay(player) {\n");
        sb.append("  var t = player.level.getDayTime() % 24000\n");
        sb.append("  return t < 12000\n");
        sb.append("}\n\n");

        sb.append("function shovel_js_hasFlag(player, flag) {\n");
        sb.append("  try {\n");
        sb.append("    return player.stages.has(flag)\n");
        sb.append("  } catch (ex) {\n");
        sb.append("    return player.persistentData.getBoolean('shovel_js_pdflag_' + flag)\n");
        sb.append("  }\n");
        sb.append("}\n\n");
        sb.append("function shovel_js_setFlag(player, flag) {\n");
        sb.append("  try {\n");
        sb.append("    player.stages.add(flag)\n");
        sb.append("  } catch (ex) {\n");
        sb.append("    player.persistentData.putBoolean('shovel_js_pdflag_' + flag, true)\n");
        sb.append("  }\n");
        sb.append("}\n\n");
        sb.append("function shovel_js_resetFlag(player, flag) {\n");
        sb.append("  try { player.stages.remove(flag) } catch (ex) {}\n");
        sb.append("  try { player.persistentData.remove('shovel_js_pdflag_' + flag) } catch (ex) {}\n");
        sb.append("}\n\n");
        sb.append("function shovel_js_fireCustomEvent(player, eventId) {\n");
        sb.append("  try { if (typeof shovel_js_onCustomEvent === 'function') shovel_js_onCustomEvent(player, eventId) } catch (ex) {}\n");
        sb.append("}\n\n");
        sb.append("function shovel_js_playSeconds(player) {\n");
        sb.append("  try {\n");
        sb.append("    return Math.floor(player.stats.getPlayTime() / 20)\n");
        sb.append("  } catch (ex) {\n");
        sb.append("    var shovel_js_pd = player.persistentData\n");
        sb.append("    var shovel_js_now = Math.floor(Date.now() / 1000)\n");
        sb.append("    var shovel_js_start = shovel_js_pd.getLong('shovel_js_playtime_started')\n");
        sb.append("    if (shovel_js_start === 0) { shovel_js_pd.putLong('shovel_js_playtime_started', shovel_js_now); return 0 }\n");
        sb.append("    return shovel_js_now - shovel_js_start\n");
        sb.append("  }\n");
        sb.append("}\n\n");

        for (EventEntry e : enabled) appendEventFunctions(sb, e);
        PLAYER_POLLS.get().clear();

        appendFirstJoinAndLoginBlock(sb, byTrigger.get(EventEntry.TriggerType.FIRST_JOIN), byTrigger.get(EventEntry.TriggerType.PLAYER_LOGIN));
        appendIntervalBlock(sb, byTrigger.get(EventEntry.TriggerType.INTERVAL));
        appendPlayerKillBlock(sb, byTrigger.get(EventEntry.TriggerType.PLAYER_KILL));
        appendCommandBlock(sb, byTrigger.get(EventEntry.TriggerType.COMMAND));
        appendEnterBiomeBlock(sb, byTrigger.get(EventEntry.TriggerType.ENTER_BIOME));
        appendPlayerDeathBlock(sb, byTrigger.get(EventEntry.TriggerType.PLAYER_DEATH));
        appendPlayerRespawnBlock(sb, byTrigger.get(EventEntry.TriggerType.PLAYER_RESPAWN));
        appendPlayerHurtBlock(sb, byTrigger.get(EventEntry.TriggerType.PLAYER_HURT));
        appendDimensionChangeBlock(sb, byTrigger.get(EventEntry.TriggerType.DIMENSION_CHANGE));
        appendBlockBreakBlock(sb, byTrigger.get(EventEntry.TriggerType.BLOCK_BREAK));
        appendBlockPlaceBlock(sb, byTrigger.get(EventEntry.TriggerType.BLOCK_PLACE));
        appendItemCraftBlock(sb, byTrigger.get(EventEntry.TriggerType.ITEM_CRAFT));
        appendItemPickupBlock(sb, byTrigger.get(EventEntry.TriggerType.ITEM_PICKUP));
        appendXpAndEffectPollBlock(sb, byTrigger.get(EventEntry.TriggerType.XP_LEVEL_REACH),
                byTrigger.get(EventEntry.TriggerType.XP_CHANGED), byTrigger.get(EventEntry.TriggerType.EFFECT_GAINED));
        appendAdvancementEarnedBlock(sb, byTrigger.get(EventEntry.TriggerType.ADVANCEMENT_EARNED));
        appendItemUseBlock(sb, byTrigger.get(EventEntry.TriggerType.ITEM_USE));
        appendItemStackEventBlock(sb, byTrigger.get(EventEntry.TriggerType.ITEM_CONSUME), "ITEM_CONSUME", "foodEaten", "T20");
        appendItemStackEventBlock(sb, byTrigger.get(EventEntry.TriggerType.ITEM_SMELT), "ITEM_SMELT", "smelted", "T21");
        appendEntityKillAnyBlock(sb, byTrigger.get(EventEntry.TriggerType.ENTITY_KILL_ANY));
        appendEntityInteractBlock(sb, byTrigger.get(EventEntry.TriggerType.ENTITY_INTERACT));
        appendMountBlock(sb, byTrigger.get(EventEntry.TriggerType.MOUNT));
        appendPlayerDisconnectBlock(sb, byTrigger.get(EventEntry.TriggerType.PLAYER_DISCONNECT));
        appendServerStartupBlock(sb, byTrigger.get(EventEntry.TriggerType.SERVER_STARTUP));
        appendForgeBridgeHandlers(sb, byTrigger);
        appendEntitySpawnBlock(sb, byTrigger.get(EventEntry.TriggerType.ENTITY_SPAWN));
        appendEntityDeathBlock(sb, byTrigger.get(EventEntry.TriggerType.ENTITY_DEATH));
        appendEntityAttacksBlock(sb, byTrigger.get(EventEntry.TriggerType.ENTITY_ATTACKS));
        appendAdminResetBlock(sb, enabled);
        appendCombinedPlayerPoll(sb);

        return sb.toString();
    }

    /**
     * si
     * {@link EventEntry#referenceWarnings()} no esta vacio, escribe un
     * comentario de advertencia (mismo estilo que
     * {@code KubeJSExporter#unsupportedWarning}) antes de las funciones del
     * evento - el evento se sigue exportando y funcionando normalmente
     * esto es solo una nota visible en el.js generado para quien lo revise
     * a mano o corra {@code /reload} y vea la consola.
     */
    private static void appendReferenceWarnings(StringBuilder sb, EventEntry e) {
        List<String> refs = e.referenceWarnings();
        if (refs.isEmpty()) return;
        sb.append("// ==== ADVERTENCIA: ").append(e.id).append(" tiene referencia(s) rota(s) ====\n");
        sb.append("// El evento se exporta igual (no se bloquea), pero revisa estos campos:\n");
        for (String w : refs) sb.append("//   - ").append(w).append("\n");
    }

    private static void appendEventFunctions(StringBuilder sb, EventEntry e) {
        String s = safe(e.id);
        appendReferenceWarnings(sb, e);
        sb.append("// ").append(e.id);
        if (!e.displayName.isEmpty()) sb.append(" - ").append(e.displayName);
        sb.append("\n");

        sb.append("function shovel_js_check_").append(s).append("(player) {\n");
        for (EventEntry.Condition c : e.conditions) appendConditionGuard(sb, c);
        if (e.limits.oncePerPlayer) {
            sb.append("  if (shovel_js_hasFlag(player, 'shovel_js_flag_").append(s).append("')) return false\n");
        }
        if (e.limits.cooldownSeconds > 0) {
            sb.append("  var shovel_js_pd_").append(s).append(" = player.persistentData\n");
            sb.append("  var shovel_js_now_").append(s).append(" = Math.floor(Date.now() / 1000)\n");
            sb.append("  if (shovel_js_now_").append(s).append(" - shovel_js_pd_").append(s).append(".getLong('shovel_js_cd_").append(s).append("') < ")
                    .append(e.limits.cooldownSeconds).append(") return false\n");
        }
        sb.append("  return true\n");
        sb.append("}\n");

        sb.append("function shovel_js_mark_").append(s).append("(player) {\n");
        if (e.limits.oncePerPlayer) {
            sb.append("  shovel_js_setFlag(player, 'shovel_js_flag_").append(s).append("')\n");
        }
        if (e.limits.cooldownSeconds > 0) {
            sb.append("  player.persistentData.putLong('shovel_js_cd_").append(s).append("', Math.floor(Date.now() / 1000))\n");
        }
        sb.append("}\n");

        sb.append("function shovel_js_act_").append(s).append("(player) {\n");
        for (int i = 0; i < e.actions.size(); i++) {
            EventEntry.Action a = e.actions.get(i);
            sb.append("  try {\n");
            appendAction(sb, a);
            sb.append("  } catch (ex) { console.error('[Shovel JS] evento ").append(JsStrings.escape(e.id)).append(" accion #").append(i + 1)
                    .append(" (").append(a.type).append(") fallo: ' + ex) }\n");
        }
        sb.append("}\n\n");
    }

    private static void appendConditionGuard(StringBuilder sb, EventEntry.Condition c) {
        switch (c.type) {
            case DIMENSION -> sb.append("  if (shovel_js_dim(player) !== '").append(JsStrings.escape(c.dimensionId)).append("') return false\n");
            case PERMISSION -> {
                Integer lvl = c.permissionLevel();
                if (lvl == null) {
                    sb.append("  // PERMISSION vacio - sin restriccion\n");
                } else {
                    sb.append("  if (!player.hasPermissions(").append(lvl).append(")) return false\n");
                }
            }
            case GAMEMODE -> sb.append("  if (shovel_js_gameMode(player) !== '").append(c.gamemode.name().toLowerCase(Locale.ROOT)).append("') return false\n");
            case TIME_OF_DAY -> sb.append("  if (shovel_js_isDay(player) !== ")
                    .append(c.timeOfDay == EventEntry.TimeOfDay.DAY).append(") return false\n");
            case Y_LEVEL_MIN -> sb.append("  if (player.getY() < ").append(c.yMin).append(") return false\n");
            case Y_LEVEL_MAX -> sb.append("  if (player.getY() > ").append(c.yMax).append(") return false\n");
            case BIOME -> sb.append("  if (!shovel_js_inBiome(player, '").append(JsStrings.escape(c.biomeId)).append("')) return false\n");
            case XP_LEVEL_MIN -> sb.append("  if (player.xpLevel < ").append(c.xpLevelMin).append(") return false\n");
            case HAS_ITEM -> {
                sb.append("  { var shovel_js_hi_n = 0; var shovel_js_hi_inv = player.inventory.items\n");
                sb.append("    for (var shovel_js_hi_i = 0; shovel_js_hi_i < shovel_js_hi_inv.size(); shovel_js_hi_i++) {\n");
                sb.append("      var shovel_js_hi_s = shovel_js_hi_inv.get(shovel_js_hi_i)\n");
                sb.append("      if (!shovel_js_hi_s.isEmpty() && shovel_js_hi_s.item === Item.of('").append(JsStrings.escape(c.hasItemId)).append("').item) shovel_js_hi_n += shovel_js_hi_s.count\n");
                sb.append("    }\n");
                sb.append("    if (shovel_js_hi_n < ").append(c.hasItemMinCount).append(") return false }\n");
            }
            case RANDOM_CHANCE -> sb.append("  if (Math.random() >= ").append(c.randomChance).append(") return false\n");
            case HEALTH_MAX -> sb.append("  if (player.getHealth() > ").append(c.healthMax).append(") return false\n");
            case HEALTH_MIN -> sb.append("  if (player.getHealth() < ").append(c.healthMin).append(") return false\n");
            case FOOD_LEVEL_MIN -> sb.append("  if (player.foodData.getFoodLevel() < ").append(c.foodLevelMin).append(") return false\n");
            case HAS_EFFECT -> {
                sb.append("  { var shovel_js_hef = false; try { shovel_js_hef = player.potionEffects.isActive('").append(JsStrings.escape(c.hasEffectId)).append("') } catch (ex) {}\n");
                sb.append("    if (!shovel_js_hef) return false }\n");
            }
            case HAS_ITEM_EQUIPPED -> {
                sb.append("  { var shovel_js_eq = null; try { shovel_js_eq = ").append(equippedItemExpr(c.equippedSlot)).append(" } catch (ex) {}\n");
                sb.append("    if (!shovel_js_eq || shovel_js_eq.isEmpty() || shovel_js_eq.id !== '").append(JsStrings.escape(c.equippedItemId)).append("') return false }\n");
            }
            case ENTITY_HAS_ITEM_EQUIPPED -> {
                sb.append("  { var shovel_js_eeq = null; try { shovel_js_eeq = ").append(equippedItemExpr(c.equippedSlot)).append(" } catch (ex) {}\n");
                sb.append("    if (!shovel_js_eeq || shovel_js_eeq.isEmpty() || shovel_js_eeq.id !== '").append(JsStrings.escape(c.entityEquippedItemId)).append("') return false }\n");
            }
            case ADVANCEMENT_DONE -> {
                sb.append("  { var shovel_js_adn = false; try { shovel_js_adn = player.isAdvancementDone('").append(JsStrings.escape(c.advancementId)).append("') } catch (ex) {}\n");
                sb.append("    if (!shovel_js_adn) return false }\n");
            }
            case ADVANCEMENT_MISSING -> {
                sb.append("  { var shovel_js_adm = false; try { shovel_js_adm = player.isAdvancementDone('").append(JsStrings.escape(c.advancementId)).append("') } catch (ex) {}\n");
                sb.append("    if (shovel_js_adm) return false }\n");
            }
            case WEATHER -> {
                switch (c.weatherState) {
                    case CLEAR -> sb.append("  if (player.level.isRaining()) return false\n");
                    case RAIN -> sb.append("  if (!player.level.isRaining() || player.level.isThundering()) return false\n");
                    case THUNDER -> sb.append("  if (!player.level.isThundering()) return false\n");
                }
            }
            case MOON_PHASE -> {
                sb.append("  { var shovel_js_mp = -1; try { shovel_js_mp = player.level.getMoonPhase() } catch (ex) {}\n");
                sb.append("    if (shovel_js_mp !== ").append(moonPhaseExpr(c.moonPhase)).append(") return false }\n");
            }
            case DAY_OF_WEEK -> {
                sb.append("  if (((new Date().getDay() + 6) % 7) !== ").append(c.dayOfWeek.ordinal()).append(") return false\n");
            }
            case DATE_BETWEEN -> {
                sb.append("  { var shovel_js_dnow = new Date(); var shovel_js_md = (shovel_js_dnow.getMonth() + 1) * 100 + shovel_js_dnow.getDate()\n");
                sb.append("    if (shovel_js_md < ").append(monthDayToInt(c.dateStartMd)).append(" || shovel_js_md > ").append(monthDayToInt(c.dateEndMd)).append(") return false }\n");
            }
            case HOUR_BETWEEN -> {
                sb.append("  { var shovel_js_h = new Date().getHours()\n");
                sb.append("    if (shovel_js_h < ").append(c.hourStart).append(" || shovel_js_h > ").append(c.hourEnd).append(") return false }\n");
            }
            case HAS_TAG_STAGE ->
                // Mismo storage dual GameStages/persistentData() que kve_hasFlag ya usa
                // para oncePerPlayer() - no requiere GameStages instalado.
                sb.append("  if (!shovel_js_hasFlag(player, '").append(JsStrings.escape(c.stageId)).append("')) return false\n");
            case NOT_HAS_FLAG ->
                sb.append("  if (shovel_js_hasFlag(player, '").append(JsStrings.escape(c.flagId)).append("')) return false\n");
            case PLAYTIME_MIN_SECONDS ->
                sb.append("  if (shovel_js_playSeconds(player) < ").append(c.playtimeMinSeconds).append(") return false\n");
        }
    }

    private static int moonPhaseExpr(String moonPhase) {
        String t = moonPhase == null ? "" : moonPhase.trim();
        if (t.equalsIgnoreCase("FULL")) return 0;
        if (t.equalsIgnoreCase("NEW")) return 4;
        try { return Integer.parseInt(t); } catch (NumberFormatException ex) { return 0; }
    }

    private static int monthDayToInt(String md) {
        try {
            String t = md.trim();
            int month = Integer.parseInt(t.substring(0, 2));
            int day = Integer.parseInt(t.substring(3, 5));
            return month * 100 + day;
        } catch (Exception ex) {
            return 0;
        }
    }

    private static String equippedItemExpr(EventEntry.EquipSlot slot) {
        return switch (slot) {
            case MAINHAND -> "player.mainHandItem";
            case OFFHAND -> "player.offHandItem";
            case FEET -> "player.feetArmorItem";
            case LEGS -> "player.legsArmorItem";
            case CHEST -> "player.chestArmorItem";
            case HEAD -> "player.headArmorItem";
        };
    }

    private static void appendAction(StringBuilder sb, EventEntry.Action a) {
        switch (a.type) {
            case MESSAGE -> sb.append("    player.tell('").append(JsStrings.escape(a.text)).append("')\n");
            case TITLE -> {
                if (a.subtitle != null && !a.subtitle.isBlank()) {
                    sb.append("    shovel_js_cmd(player, 'title ' + shovel_js_uuid(player) + ' subtitle ' + JSON.stringify('").append(JsStrings.escape(a.subtitle)).append("'))\n");
                }
                sb.append("    shovel_js_cmd(player, 'title ' + shovel_js_uuid(player) + ' title ' + JSON.stringify('").append(JsStrings.escape(a.title)).append("'))\n");
            }
            case GIVE_ITEM -> {
                sb.append("    player.give(Item.of('").append(JsStrings.escape(a.itemId)).append("', ").append(a.itemCount).append(")");
                if (a.nbtString != null && !a.nbtString.isBlank()) {
                    sb.append(".withNBT('").append(JsStrings.escape(a.nbtString)).append("')");
                }
                sb.append(")\n");
            }
            case RUN_COMMAND -> sb.append("    if (player) shovel_js_cmd(player, 'execute as ' + shovel_js_uuid(player) + ' at @s run ' + '")
                    .append(JsStrings.escape(stripSlash(a.command))).append("'); else shovel_js_cmd(null, '").append(JsStrings.escape(stripSlash(a.command))).append("')\n");
            case PLAY_SOUND -> sb.append("    shovel_js_cmd(player, 'playsound ").append(JsStrings.escape(a.soundId)).append(" master @a ' + player.getX() + ' ' + player.getY() + ' ' + player.getZ() + ' ")
                    .append(a.volume).append(" ").append(a.pitch).append("')\n");
            case SPAWN_ENTITY -> {
                sb.append("    for (var shovel_js_i = 0; shovel_js_i < ").append(a.spawnCount).append("; shovel_js_i++) {\n");
                sb.append("      var shovel_js_ang = Math.random() * Math.PI * 2\n");
                sb.append("      var shovel_js_ent = player.level.createEntity('").append(JsStrings.escape(a.spawnEntityId)).append("')\n");
                sb.append("      shovel_js_ent.setPosition(player.x + Math.cos(shovel_js_ang) * ").append(a.spawnRadius)
                        .append(", player.y, player.z + Math.sin(shovel_js_ang) * ").append(a.spawnRadius).append(")\n");
                sb.append("      shovel_js_ent.spawn()\n");
                sb.append("    }\n");
            }
            case ACTIONBAR -> sb.append("    player.displayClientMessage(Text.of('").append(JsStrings.escape(a.text)).append("'), true)\n");
            case TAKE_ITEM -> {
                sb.append("    var shovel_js_ti_left = ").append(a.itemCount).append("; var shovel_js_ti_inv = player.inventory.items\n");
                sb.append("    for (var shovel_js_ti_i = 0; shovel_js_ti_i < shovel_js_ti_inv.size() && shovel_js_ti_left > 0; shovel_js_ti_i++) {\n");
                sb.append("      var shovel_js_ti_s = shovel_js_ti_inv.get(shovel_js_ti_i)\n");
                sb.append("      if (!shovel_js_ti_s.isEmpty() && shovel_js_ti_s.id === '").append(JsStrings.escape(a.itemId)).append("') {\n");
                sb.append("        var shovel_js_ti_take = Math.min(shovel_js_ti_left, shovel_js_ti_s.count)\n");
                sb.append("        shovel_js_ti_s.shrink(shovel_js_ti_take)\n");
                sb.append("        shovel_js_ti_left -= shovel_js_ti_take\n");
                sb.append("      }\n");
                sb.append("    }\n");
            }
            case GIVE_XP_LEVELS -> sb.append("    player.giveExperienceLevels(").append(a.xpLevels).append(")\n");
            case ADD_EFFECT -> sb.append("    player.potionEffects.add('").append(JsStrings.escape(a.effectId)).append("', ")
                    .append(a.effectDurationTicks).append(", ").append(a.effectAmplifier).append(")\n");
            case TELEPORT -> {
                String dim = (a.teleportDim != null && !a.teleportDim.isBlank()) ? "'" + JsStrings.escape(a.teleportDim) + "'" : "shovel_js_dim(player)";
                sb.append("    shovel_js_cmd(player, 'execute in ' + ").append(dim).append(" + ' run tp ' + shovel_js_uuid(player) + ' ")
                        .append(a.teleportX).append(" ").append(a.teleportY).append(" ").append(a.teleportZ).append(" ")
                        .append(a.teleportYaw).append(" ").append(a.teleportPitch).append("')\n");
            }
            case TELEPORT_SPAWN -> {
                sb.append("    var shovel_js_sp = shovel_js_server(player).overworld().sharedSpawnPos\n");
                sb.append("    shovel_js_cmd(player, 'execute in minecraft:overworld run tp ' + shovel_js_uuid(player) + ' ' + (shovel_js_sp.x + 0.5) + ' ' + shovel_js_sp.y + ' ' + (shovel_js_sp.z + 0.5))\n");
            }
            case CLEAR_ITEM -> {
                sb.append("    var shovel_js_ci_inv = player.inventory.items\n");
                sb.append("    for (var shovel_js_ci_i = 0; shovel_js_ci_i < shovel_js_ci_inv.size(); shovel_js_ci_i++) {\n");
                sb.append("      var shovel_js_ci_s = shovel_js_ci_inv.get(shovel_js_ci_i)\n");
                sb.append("      if (!shovel_js_ci_s.isEmpty() && shovel_js_ci_s.id === '").append(JsStrings.escape(a.itemId)).append("') shovel_js_ci_s.setCount(0)\n");
                sb.append("    }\n");
            }
            case GIVE_XP_POINTS -> sb.append("    player.giveExperiencePoints(").append(a.xpPoints).append(")\n");
            case SET_XP_LEVEL -> sb.append("    player.setXpLevel(").append(a.setXpLevel).append(")\n");
            // player.stages es nativo de KubeJS (PlayerKJS#getStages), no requiere GameStages.
            case ADD_STAGE -> sb.append("    player.stages.add('").append(JsStrings.escape(a.stageId)).append("')\n");
            case REMOVE_STAGE -> sb.append("    player.stages.remove('").append(JsStrings.escape(a.stageId)).append("')\n");
            case HEAL -> {
                if (a.healFull) {
                    sb.append("    player.setHealth(player.getMaxHealth())\n");
                } else {
                    sb.append("    player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + ").append(a.healAmount).append("))\n");
                }
            }
            case SET_FOOD -> {
                sb.append("    player.foodData.setFoodLevel(").append(a.setFoodLevel).append(")\n");
                if (a.setFoodSaturationEnabled) {
                    sb.append("    player.foodData.setSaturation(").append(a.setFoodSaturation).append(")\n");
                }
            }
            case SET_HEALTH -> sb.append("    player.setHealth(").append(a.setHealthAmount).append(")\n");
            case CLEAR_EFFECTS -> sb.append("    player.potionEffects.clear()\n");
            case BOSSBAR_SHOW -> {
                String hash = Integer.toHexString(a.bossbarText.hashCode());
                sb.append("    var shovel_js_bb = 'shovel_js:bb_").append(hash).append("_' + shovel_js_uuid(player)\n");
                sb.append("    var shovel_js_bbtxt = JSON.stringify('").append(JsStrings.escape(a.bossbarText)).append("')\n");
                sb.append("    shovel_js_cmd(player, 'bossbar add ' + shovel_js_bb + ' ' + shovel_js_bbtxt)\n");
                sb.append("    shovel_js_cmd(player, 'bossbar set ' + shovel_js_bb + ' name ' + shovel_js_bbtxt)\n");
                sb.append("    shovel_js_cmd(player, 'bossbar set ' + shovel_js_bb + ' color ").append(a.bossbarColor.name().toLowerCase(Locale.ROOT)).append("')\n");
                sb.append("    shovel_js_cmd(player, 'bossbar set ' + shovel_js_bb + ' players ' + shovel_js_uuid(player))\n");
                sb.append("    shovel_js_cmd(player, 'bossbar set ' + shovel_js_bb + ' visible true')\n");
                sb.append("    player.persistentData.putString('shovel_js_last_bb', shovel_js_bb)\n");
                sb.append("    shovel_js_server(player).scheduleInTicks(").append((long) a.bossbarSeconds * 20L)
                        .append(", function () { shovel_js_cmd(null, 'bossbar remove ' + shovel_js_bb) })\n");
            }
            case BOSSBAR_CLEAR -> {
                sb.append("    var shovel_js_lbb = player.persistentData.getString('shovel_js_last_bb')\n");
                sb.append("    if (shovel_js_lbb !== '') { shovel_js_cmd(player, 'bossbar remove ' + shovel_js_lbb); player.persistentData.remove('shovel_js_last_bb') }\n");
            }
            case PARTICLES -> sb.append("    shovel_js_cmd(player, 'particle ").append(JsStrings.escape(a.particleId)).append(" ' + player.getX() + ' ' + (player.getY() + 1) + ' ' + player.getZ() + ' ")
                    .append(a.particleSpread).append(" ").append(a.particleSpread).append(" ").append(a.particleSpread)
                    .append(" 0 ").append(a.particleCount).append(" force')\n");
            case SET_TIME -> {
                long value = switch (a.setTimeMode) {
                    case DAY -> 1000L;
                    case NIGHT -> 13000L;
                    case VALUE -> a.setTimeValue;
                };
                sb.append("    shovel_js_cmd(player, 'time set ").append(value).append("')\n");
            }
            case SET_WEATHER -> {
                String mode = switch (a.setWeatherMode) {
                    case CLEAR -> "clear";
                    case RAIN -> "rain";
                    case THUNDER -> "thunder";
                };
                sb.append("    shovel_js_cmd(player, 'weather ").append(mode);
                if (a.setWeatherDurationTicks > 0) sb.append(" ").append(Math.max(1, a.setWeatherDurationTicks / 20));
                sb.append("')\n");
            }
            case EXPLOSION_HARMLESS -> {
                String particle = a.explosionPower >= 4.0f ? "minecraft:explosion_emitter" : "minecraft:explosion";
                sb.append("    shovel_js_cmd(player, 'particle ").append(particle).append(" ' + player.getX() + ' ' + player.getY() + ' ' + player.getZ() + ' ")
                        .append(a.explosionPower / 4.0f).append(" ").append(a.explosionPower / 4.0f).append(" ").append(a.explosionPower / 4.0f)
                        .append(" 0 ").append(Math.max(1, Math.round(a.explosionPower))).append(" force')\n");
                sb.append("    shovel_js_cmd(player, 'playsound minecraft:entity.generic.explode master @a ' + player.getX() + ' ' + player.getY() + ' ' + player.getZ() + ' ")
                        .append(Math.max(1.0f, a.explosionPower / 2.0f)).append(" 1')\n");
            }
            case RUN_COMMAND_AS_PLAYER -> sb.append("    player.runCommandSilent('").append(JsStrings.escape(stripSlash(a.commandAsPlayer))).append("')\n");
            case GIVE_MONEY, TAKE_MONEY -> {
                String fn = a.type == EventEntry.ActionType.GIVE_MONEY ? "shovel_js_giveMoney" : "shovel_js_takeMoney";
                sb.append("    if (typeof ").append(fn).append(" === 'function') ").append(fn)
                        .append("(player, ").append(a.moneyAmount).append(", '").append(JsStrings.escape(a.currencyKey)).append("')\n");
            }
            case FIRE_CUSTOM_EVENT -> sb.append("    shovel_js_fireCustomEvent(player, '").append(JsStrings.escape(a.customEventId)).append("')\n");
            case CLEAR_TITLE -> sb.append("    shovel_js_cmd(player, 'title ' + shovel_js_uuid(player) + ' clear')\n");
            case GRANT_ADVANCEMENT -> sb.append("    player.unlockAdvancement('").append(JsStrings.escape(a.grantAdvancementId)).append("')\n");
            case REMOVE_EFFECT -> sb.append("    player.removeEffect('").append(JsStrings.escape(a.removeEffectId)).append("')\n");
            case SET_SPAWN -> {
                String dim = (a.setSpawnDim != null && !a.setSpawnDim.isBlank()) ? "'" + JsStrings.escape(a.setSpawnDim) + "'" : "shovel_js_dim(player)";
                sb.append("    shovel_js_cmd(player, 'execute in ' + ").append(dim).append(" + ' run spawnpoint ' + shovel_js_uuid(player) + ' ")
                        .append((long) Math.floor(a.setSpawnX)).append(" ").append((long) Math.floor(a.setSpawnY)).append(" ")
                        .append((long) Math.floor(a.setSpawnZ)).append("')\n");
            }
            case LIGHTNING -> sb.append("    player.level.spawnLightning(player.x, player.y, player.z, false)\n");
            case APPLY_LOOT_TABLE -> sb.append("    shovel_js_cmd(player, 'loot spawn ' + player.getX() + ' ' + player.getY() + ' ' + player.getZ() + ' loot ")
                    .append(JsStrings.escape(a.lootTableId)).append("')\n");
        }
    }

    private static String stripSlash(String cmd) {
        String t = cmd == null ? "" : cmd.trim();
        return t.startsWith("/") ? t.substring(1) : t;
    }

    private static void appendFirstJoinAndLoginBlock(StringBuilder sb, List<EventEntry> firstJoin, List<EventEntry> login) {
        List<EventEntry> all = new ArrayList<>();
        if (firstJoin != null) all.addAll(firstJoin);
        if (login != null) all.addAll(login);
        if (all.isEmpty()) return;

        sb.append("// FIRST_JOIN / PLAYER_LOGIN -> PlayerEvents.loggedIn\n");
        sb.append("try {\n");
        sb.append("PlayerEvents.loggedIn(event => {\n");
        sb.append("  var player = event.player\n");
        for (EventEntry e : all) {
            String s = safe(e.id);
            if (e.trigger.type == EventEntry.TriggerType.FIRST_JOIN) {
                sb.append("  if (!shovel_js_hasFlag(player, 'shovel_js_joined_").append(s).append("') && shovel_js_check_").append(s).append("(player)) {\n");
                sb.append("    shovel_js_setFlag(player, 'shovel_js_joined_").append(s).append("')\n");
            } else {
                sb.append("  if (shovel_js_check_").append(s).append("(player)) {\n");
            }
            sb.append("    shovel_js_act_").append(s).append("(player)\n");
            sb.append("    shovel_js_mark_").append(s).append("(player)\n");
            sb.append("  }\n");
        }
        sb.append("})\n");
        sb.append("} catch (ex) { console.error('[Shovel JS] evento FIRST_JOIN/PLAYER_LOGIN (PlayerEvents.loggedIn) no se pudo registrar: ' + ex) }\n\n");
    }

    private static void appendIntervalBlock(StringBuilder sb, List<EventEntry> intervalEvents) {
        if (intervalEvents == null || intervalEvents.isEmpty()) return;

        sb.append("// INTERVAL -> ServerEvents.tick + contador en persistentData del server\n");
        sb.append("// shovel_js_interval_next: proximo epoch-second en que ALGUN INTERVAL podria vencer; early-out barato mientras no llega\n");
        sb.append("try {\n");
        sb.append("ServerEvents.tick(event => {\n");
        sb.append("  if (event.server.tickCount % 20 !== 0) return\n");
        sb.append("  var server = event.server\n");
        sb.append("  var shovel_js_g = server.persistentData\n");
        sb.append("  var shovel_js_now = Math.floor(Date.now() / 1000)\n");
        sb.append("  var shovel_js_next = shovel_js_g.getLong('shovel_js_interval_next')\n");
        sb.append("  if (shovel_js_next > 0 && shovel_js_now < shovel_js_next) return\n");
        sb.append("  var shovel_js_minNext = 0\n");
        for (EventEntry e : intervalEvents) {
            String s = safe(e.id);
            int secs = e.trigger.intervalSeconds;
            sb.append("  // ").append(e.id).append(" (INTERVAL ").append(secs).append("s)\n");
            sb.append("  var shovel_js_last_").append(s).append(" = shovel_js_g.getLong('shovel_js_last_").append(s).append("')\n");
            sb.append("  if (shovel_js_now - shovel_js_last_").append(s).append(" >= ").append(secs).append(") {\n");
            if (e.limits.maxGlobalStarts > 0) {
                sb.append("    if (shovel_js_g.getInt('shovel_js_starts_").append(s).append("') < ").append(e.limits.maxGlobalStarts).append(") {\n");
                sb.append("      shovel_js_g.putInt('shovel_js_starts_").append(s).append("', shovel_js_g.getInt('shovel_js_starts_").append(s).append("') + 1)\n");
                sb.append("      shovel_js_g.putLong('shovel_js_last_").append(s).append("', shovel_js_now)\n");
                sb.append("      shovel_js_last_").append(s).append(" = shovel_js_now\n");
                sb.append("      for (var shovel_js_p = 0; shovel_js_p < server.players.length; shovel_js_p++) {\n");
                sb.append("        var player = server.players[shovel_js_p]\n");
                sb.append("        if (shovel_js_check_").append(s).append("(player)) {\n");
                sb.append("          shovel_js_act_").append(s).append("(player)\n");
                sb.append("          shovel_js_mark_").append(s).append("(player)\n");
                sb.append("        }\n");
                sb.append("      }\n");
                sb.append("    }\n");
            } else {
                sb.append("    shovel_js_g.putLong('shovel_js_last_").append(s).append("', shovel_js_now)\n");
                sb.append("    shovel_js_last_").append(s).append(" = shovel_js_now\n");
                sb.append("    for (var shovel_js_p = 0; shovel_js_p < server.players.length; shovel_js_p++) {\n");
                sb.append("      var player = server.players[shovel_js_p]\n");
                sb.append("      if (shovel_js_check_").append(s).append("(player)) {\n");
                sb.append("        shovel_js_act_").append(s).append("(player)\n");
                sb.append("        shovel_js_mark_").append(s).append("(player)\n");
                sb.append("      }\n");
                sb.append("    }\n");
            }
            sb.append("  }\n");
            sb.append("  var shovel_js_cand_").append(s).append(" = shovel_js_last_").append(s).append(" + ").append(secs).append("\n");
            sb.append("  if (shovel_js_minNext === 0 || shovel_js_cand_").append(s).append(" < shovel_js_minNext) shovel_js_minNext = shovel_js_cand_").append(s).append("\n");
        }
        sb.append("  shovel_js_g.putLong('shovel_js_interval_next', shovel_js_minNext)\n");
        sb.append("})\n");
        sb.append("} catch (ex) { console.error('[Shovel JS] evento INTERVAL (ServerEvents.tick) no se pudo registrar: ' + ex) }\n\n");
    }

    private static void appendPlayerKillBlock(StringBuilder sb, List<EventEntry> killEvents) {
        if (killEvents == null || killEvents.isEmpty()) return;

        sb.append("// PLAYER_KILL -> EntityEvents.death filtrando attacker jugador + tipo\n");
        sb.append("try {\n");
        sb.append("EntityEvents.death(event => {\n");
        sb.append("  var src = event.source\n");
        sb.append("  var attacker = null\n");
        sb.append("  if (src) {\n");
        sb.append("    if (typeof src.getEntity === 'function') { try { attacker = src.getEntity() } catch (ex) {} }\n");
        sb.append("    if (!attacker && typeof src.getDirectEntity === 'function') { try { attacker = src.getDirectEntity() } catch (ex) {} }\n");
        sb.append("    if (!attacker && src.entity) attacker = src.entity\n");
        sb.append("    if (!attacker && src.directEntity) attacker = src.directEntity\n");
        sb.append("    if (!attacker && src.causingEntity) attacker = src.causingEntity\n");
        sb.append("  }\n");
        sb.append("  if (!attacker || !attacker.player) return\n");
        sb.append("  var player = attacker\n");
        sb.append("  var victimId = event.entity.type\n");
        for (EventEntry e : killEvents) {
            String s = safe(e.id);
            sb.append("  // ").append(e.id).append(" (PLAYER_KILL ").append(e.trigger.entityId).append(" x").append(e.trigger.count).append(")\n");
            sb.append("  if (victimId === '").append(JsStrings.escape(e.trigger.entityId)).append("' && shovel_js_check_").append(s).append("(player)) {\n");
            sb.append("    var shovel_js_pd_").append(s).append(" = player.persistentData\n");
            sb.append("    var shovel_js_n_").append(s).append(" = shovel_js_pd_").append(s).append(".getInt('shovel_js_kills_").append(s).append("') + 1\n");
            sb.append("    shovel_js_pd_").append(s).append(".putInt('shovel_js_kills_").append(s).append("', shovel_js_n_").append(s).append(")\n");
            sb.append("    if (shovel_js_n_").append(s).append(" >= ").append(e.trigger.count).append(") {\n");
            sb.append("      shovel_js_pd_").append(s).append(".putInt('shovel_js_kills_").append(s).append("', 0)\n");
            sb.append("      shovel_js_act_").append(s).append("(player)\n");
            sb.append("      shovel_js_mark_").append(s).append("(player)\n");
            sb.append("    }\n");
            sb.append("  }\n");
        }
        sb.append("})\n");
        sb.append("} catch (ex) { console.error('[Shovel JS] evento PLAYER_KILL (EntityEvents.death) no se pudo registrar: ' + ex) }\n\n");
    }

    private static void appendPlayerDeathBlock(StringBuilder sb, List<EventEntry> deathEvents) {
        if (deathEvents == null || deathEvents.isEmpty()) return;

        sb.append("// PLAYER_DEATH -> EntityEvents.death filtrando victima jugador\n");
        sb.append("try {\n");
        sb.append("EntityEvents.death(event => {\n");
        sb.append("  if (!event.entity.player) return\n");
        sb.append("  var player = event.entity\n");
        for (EventEntry e : deathEvents) {
            String s = safe(e.id);
            sb.append("  // ").append(e.id).append(" (PLAYER_DEATH)\n");
            String msgGuard = "";
            if (e.trigger.deathMessageContains != null && !e.trigger.deathMessageContains.isBlank()) {
                sb.append("  var shovel_js_dmsg_").append(s).append(" = ''\n");
                sb.append("  try { shovel_js_dmsg_").append(s).append(" = String(event.source.getLocalizedDeathMessage(player).getString()) } catch (ex) {}\n");
                msgGuard = "shovel_js_dmsg_" + s + ".indexOf('" + JsStrings.escape(e.trigger.deathMessageContains) + "') !== -1 && ";
            }
            sb.append("  if (").append(msgGuard).append("shovel_js_check_").append(s).append("(player)) {\n");
            sb.append("    shovel_js_act_").append(s).append("(player)\n");
            sb.append("    shovel_js_mark_").append(s).append("(player)\n");
            sb.append("  }\n");
        }
        sb.append("})\n");
        sb.append("} catch (ex) { console.error('[Shovel JS] evento PLAYER_DEATH (EntityEvents.death) no se pudo registrar: ' + ex) }\n\n");
    }

    private static void appendPlayerRespawnBlock(StringBuilder sb, List<EventEntry> respawnEvents) {
        if (respawnEvents == null || respawnEvents.isEmpty()) return;

        sb.append("// PLAYER_RESPAWN -> PlayerEvents.respawned\n");
        sb.append("try {\n");
        sb.append("PlayerEvents.respawned(event => {\n");
        sb.append("  var player = event.player\n");
        for (EventEntry e : respawnEvents) {
            String s = safe(e.id);
            sb.append("  if (shovel_js_check_").append(s).append("(player)) {\n");
            sb.append("    shovel_js_act_").append(s).append("(player)\n");
            sb.append("    shovel_js_mark_").append(s).append("(player)\n");
            sb.append("  }\n");
        }
        sb.append("})\n");
        sb.append("} catch (ex) { console.error('[Shovel JS] evento PLAYER_RESPAWN (PlayerEvents.respawned) no se pudo registrar: ' + ex) }\n\n");
    }

    private static void appendPlayerHurtBlock(StringBuilder sb, List<EventEntry> hurtEvents) {
        if (hurtEvents == null || hurtEvents.isEmpty()) return;

        sb.append("// PLAYER_HURT -> EntityEvents.hurt filtrando victima jugador\n");
        sb.append("try {\n");
        sb.append("EntityEvents.hurt(event => {\n");
        sb.append("  if (!event.entity.player) return\n");
        sb.append("  var player = event.entity\n");
        sb.append("  var shovel_js_dmg = event.damage\n");
        for (EventEntry e : hurtEvents) {
            String s = safe(e.id);
            sb.append("  // ").append(e.id).append(" (PLAYER_HURT minDamage ").append(e.trigger.minDamage).append(")\n");
            sb.append("  if (shovel_js_dmg >= ").append(e.trigger.minDamage).append(" && shovel_js_check_").append(s).append("(player)) {\n");
            sb.append("    shovel_js_act_").append(s).append("(player)\n");
            sb.append("    shovel_js_mark_").append(s).append("(player)\n");
            sb.append("  }\n");
        }
        sb.append("})\n");
        sb.append("} catch (ex) { console.error('[Shovel JS] evento PLAYER_HURT (EntityEvents.hurt) no se pudo registrar: ' + ex) }\n\n");
    }

    private static void appendDimensionChangeBlock(StringBuilder sb, List<EventEntry> dimEvents) {
        if (dimEvents == null || dimEvents.isEmpty()) return;

        sb.append("// DIMENSION_CHANGE -> PlayerEvents.tick a 1 Hz comparando la dimension actual contra la\n");
        sb.append("// ultima vista en persistentData (PlayerEvents.changedDimension no existe en KubeJS)\n");
        sb.append("function shovel_js_ptick_dimension(player) {\n");
        sb.append("  var shovel_js_pd = player.persistentData\n");
        sb.append("  var shovel_js_to = String(player.level.dimension)\n");
        sb.append("  var shovel_js_from = shovel_js_pd.getString('shovel_js_lastdimension')\n");
        sb.append("  if (shovel_js_from !== '' && shovel_js_to !== shovel_js_from) {\n");
        for (EventEntry e : dimEvents) {
            String s = safe(e.id);
            sb.append("    // ").append(e.id).append(" (DIMENSION_CHANGE -> ").append(e.trigger.toDimension).append(")\n");
            sb.append("    if (shovel_js_to === '").append(JsStrings.escape(e.trigger.toDimension)).append("'");
            if (e.trigger.fromDimension != null && !e.trigger.fromDimension.isBlank()) {
                sb.append(" && shovel_js_from === '").append(JsStrings.escape(e.trigger.fromDimension)).append("'");
            }
            sb.append(" && shovel_js_check_").append(s).append("(player)) {\n");
            sb.append("      shovel_js_act_").append(s).append("(player)\n");
            sb.append("      shovel_js_mark_").append(s).append("(player)\n");
            sb.append("    }\n");
        }
        sb.append("  }\n");
        sb.append("  if (shovel_js_to !== shovel_js_from) shovel_js_pd.putString('shovel_js_lastdimension', shovel_js_to)\n");
        sb.append("}\n\n");
        PLAYER_POLLS.get().add("dimension");
    }

    private static void appendBlockBreakBlock(StringBuilder sb, List<EventEntry> breakEvents) {
        appendBlockHookBlock(sb, breakEvents, "BLOCK_BREAK", "broken", "T15");
    }

    private static void appendBlockPlaceBlock(StringBuilder sb, List<EventEntry> placeEvents) {
        appendBlockHookBlock(sb, placeEvents, "BLOCK_PLACE", "placed", "T16");
    }

    private static void appendBlockHookBlock(StringBuilder sb, List<EventEntry> events, String triggerName, String kubejsMethod, String specCode) {
        if (events == null || events.isEmpty()) return;

        sb.append("// ").append(triggerName).append(" -> BlockEvents.").append(kubejsMethod).append("\n");
        sb.append("try {\n");
        sb.append("BlockEvents.").append(kubejsMethod).append("(event => {\n");
        sb.append("  var player = event.player\n");
        sb.append("  if (!player) return\n");
        for (EventEntry e : events) {
            String s = safe(e.id);
            boolean isTag = e.trigger.blockId != null && e.trigger.blockId.startsWith("#");
            sb.append("  // ").append(e.id).append(" (").append(triggerName).append(" ").append(e.trigger.blockId)
                    .append(" x").append(e.trigger.count).append(")\n");
            String matchExpr = isTag
                    ? "event.block.hasTag('" + JsStrings.escape(e.trigger.blockId.substring(1)) + "')"
                    : "event.block.id === '" + JsStrings.escape(e.trigger.blockId) + "'";
            sb.append("  if (").append(matchExpr).append(" && shovel_js_check_").append(s).append("(player)) {\n");
            if (e.trigger.count > 1) {
                sb.append("    var shovel_js_pd_").append(s).append(" = player.persistentData\n");
                sb.append("    var shovel_js_n_").append(s).append(" = shovel_js_pd_").append(s).append(".getInt('shovel_js_bcnt_").append(s).append("') + 1\n");
                sb.append("    shovel_js_pd_").append(s).append(".putInt('shovel_js_bcnt_").append(s).append("', shovel_js_n_").append(s).append(")\n");
                sb.append("    if (shovel_js_n_").append(s).append(" >= ").append(e.trigger.count).append(") {\n");
                sb.append("      shovel_js_pd_").append(s).append(".putInt('shovel_js_bcnt_").append(s).append("', 0)\n");
                sb.append("      shovel_js_act_").append(s).append("(player)\n");
                sb.append("      shovel_js_mark_").append(s).append("(player)\n");
                sb.append("    }\n");
            } else {
                sb.append("    shovel_js_act_").append(s).append("(player)\n");
                sb.append("    shovel_js_mark_").append(s).append("(player)\n");
            }
            sb.append("  }\n");
        }
        sb.append("})\n");
        sb.append("} catch (ex) { console.error('[Shovel JS] evento ").append(triggerName).append(" no se pudo registrar: ' + ex) }\n\n");
    }

    private static void appendItemStackEventBlock(StringBuilder sb, List<EventEntry> events, String triggerName, String kubejsMethod, String specCode) {
        if (events == null || events.isEmpty()) return;

        sb.append("// ").append(triggerName).append(" -> ItemEvents.").append(kubejsMethod).append("\n");
        sb.append("try {\n");
        sb.append("ItemEvents.").append(kubejsMethod).append("(event => {\n");
        sb.append("  var player = event.player\n");
        sb.append("  if (!player) return\n");
        sb.append("  var shovel_js_stack = event.item\n");
        for (EventEntry e : events) {
            String s = safe(e.id);
            sb.append("  // ").append(e.id).append(" (").append(triggerName).append(" ").append(e.trigger.itemId).append(")\n");
            sb.append("  if (").append(itemMatchExpr("shovel_js_stack", e.trigger.itemId)).append(" && shovel_js_check_").append(s).append("(player)) {\n");
            sb.append("    shovel_js_act_").append(s).append("(player)\n");
            sb.append("    shovel_js_mark_").append(s).append("(player)\n");
            sb.append("  }\n");
        }
        sb.append("})\n");
        sb.append("} catch (ex) { console.error('[Shovel JS] evento ").append(triggerName).append(" (ItemEvents.").append(kubejsMethod).append(") no se pudo registrar: ' + ex) }\n\n");
    }

    private static String itemMatchExpr(String stackVar, String itemId) {
        if (itemId == null || itemId.isBlank()) return "!" + stackVar + ".isEmpty()";
        if (itemId.startsWith("#")) return stackVar + ".hasTag('" + JsStrings.escape(itemId.substring(1)) + "')";
        return stackVar + ".id === '" + JsStrings.escape(itemId) + "'";
    }

    private static void appendItemCraftBlock(StringBuilder sb, List<EventEntry> craftEvents) {
        if (craftEvents == null || craftEvents.isEmpty()) return;

        sb.append("// ITEM_CRAFT -> ItemEvents.crafted\n");
        sb.append("try {\n");
        sb.append("ItemEvents.crafted(event => {\n");
        sb.append("  var player = event.player\n");
        sb.append("  if (!player) return\n");
        sb.append("  var shovel_js_craftid = event.item.id\n");
        for (EventEntry e : craftEvents) {
            String s = safe(e.id);
            boolean isTag = e.trigger.itemId != null && e.trigger.itemId.startsWith("#");
            sb.append("  // ").append(e.id).append(" (ITEM_CRAFT ").append(e.trigger.itemId).append(" x").append(e.trigger.count).append(")\n");
            String matchExpr = isTag
                    ? "event.item.hasTag('" + JsStrings.escape(e.trigger.itemId.substring(1)) + "')"
                    : "shovel_js_craftid === '" + JsStrings.escape(e.trigger.itemId) + "'";
            sb.append("  if (").append(matchExpr).append(" && shovel_js_check_").append(s).append("(player)) {\n");
            if (e.trigger.count > 1) {
                sb.append("    var shovel_js_pd_").append(s).append(" = player.persistentData\n");
                sb.append("    var shovel_js_n_").append(s).append(" = shovel_js_pd_").append(s).append(".getInt('shovel_js_craftcnt_").append(s).append("') + 1\n");
                sb.append("    shovel_js_pd_").append(s).append(".putInt('shovel_js_craftcnt_").append(s).append("', shovel_js_n_").append(s).append(")\n");
                sb.append("    if (shovel_js_n_").append(s).append(" >= ").append(e.trigger.count).append(") {\n");
                sb.append("      shovel_js_pd_").append(s).append(".putInt('shovel_js_craftcnt_").append(s).append("', 0)\n");
                sb.append("      shovel_js_act_").append(s).append("(player)\n");
                sb.append("      shovel_js_mark_").append(s).append("(player)\n");
                sb.append("    }\n");
            } else {
                sb.append("    shovel_js_act_").append(s).append("(player)\n");
                sb.append("    shovel_js_mark_").append(s).append("(player)\n");
            }
            sb.append("  }\n");
        }
        sb.append("})\n");
        sb.append("} catch (ex) { console.error('[Shovel JS] evento ITEM_CRAFT (ItemEvents.crafted) no se pudo registrar: ' + ex) }\n\n");
    }

    private static void appendItemPickupBlock(StringBuilder sb, List<EventEntry> pickupEvents) {
        if (pickupEvents == null || pickupEvents.isEmpty()) return;

        sb.append("// ITEM_PICKUP -> ItemEvents.pickedUp\n");
        sb.append("try {\n");
        sb.append("ItemEvents.pickedUp(event => {\n");
        sb.append("  var player = event.player\n");
        sb.append("  if (!player) return\n");
        sb.append("  var shovel_js_stack = event.item\n");
        sb.append("  var shovel_js_n = shovel_js_stack.count\n");
        for (EventEntry e : pickupEvents) {
            String s = safe(e.id);
            sb.append("  // ").append(e.id).append(" (ITEM_PICKUP ").append(e.trigger.itemId).append(" x").append(e.trigger.count).append(")\n");
            sb.append("  if (").append(itemMatchExpr("shovel_js_stack", e.trigger.itemId)).append(") {\n");
            sb.append("    var shovel_js_pd_").append(s).append(" = player.persistentData\n");
            sb.append("    var shovel_js_acc_").append(s).append(" = shovel_js_pd_").append(s).append(".getInt('shovel_js_pkacc_").append(s).append("') + shovel_js_n\n");
            sb.append("    if (shovel_js_acc_").append(s).append(" >= ").append(e.trigger.count).append(" && shovel_js_check_").append(s).append("(player)) {\n");
            sb.append("      shovel_js_acc_").append(s).append(" = 0\n");
            sb.append("      shovel_js_act_").append(s).append("(player)\n");
            sb.append("      shovel_js_mark_").append(s).append("(player)\n");
            sb.append("    }\n");
            sb.append("    shovel_js_pd_").append(s).append(".putInt('shovel_js_pkacc_").append(s).append("', shovel_js_acc_").append(s).append(")\n");
            sb.append("  }\n");
        }
        sb.append("})\n");
        sb.append("} catch (ex) { console.error('[Shovel JS] evento ITEM_PICKUP (ItemEvents.pickedUp) no se pudo registrar: ' + ex) }\n\n");
    }

    private static void appendXpAndEffectPollBlock(StringBuilder sb, List<EventEntry> xpLevelEvents,
                                                     List<EventEntry> xpChangedEvents, List<EventEntry> effectEvents) {
        List<EventEntry> all = new ArrayList<>();
        if (xpLevelEvents != null) all.addAll(xpLevelEvents);
        if (xpChangedEvents != null) all.addAll(xpChangedEvents);
        if (effectEvents != null) all.addAll(effectEvents);
        if (all.isEmpty()) return;

        sb.append("// XP_LEVEL_REACH / XP_CHANGED / EFFECT_GAINED -> PlayerEvents.tick a 1 Hz, comparando\n");
        sb.append("// contra el ultimo valor visto en persistentData\n");
        sb.append("function shovel_js_ptick_xp_effect(player) {\n");
        sb.append("  var shovel_js_pd = player.persistentData\n");
        sb.append("  var shovel_js_lvl = player.xpLevel\n");
        if (xpLevelEvents != null) {
            for (EventEntry e : xpLevelEvents) {
                String s = safe(e.id);
                sb.append("  // ").append(e.id).append(" (XP_LEVEL_REACH ").append(e.trigger.xpLevel).append(")\n");
                sb.append("  if (shovel_js_pd.contains('shovel_js_reachlvl_").append(s).append("')) {\n");
                sb.append("    var shovel_js_prev_").append(s).append(" = shovel_js_pd.getInt('shovel_js_reachlvl_").append(s).append("')\n");
                sb.append("    if (shovel_js_prev_").append(s).append(" < ").append(e.trigger.xpLevel).append(" && shovel_js_lvl >= ").append(e.trigger.xpLevel)
                        .append(" && shovel_js_check_").append(s).append("(player)) {\n");
                sb.append("      shovel_js_act_").append(s).append("(player)\n");
                sb.append("      shovel_js_mark_").append(s).append("(player)\n");
                sb.append("    }\n");
                sb.append("  }\n");
                sb.append("  shovel_js_pd.putInt('shovel_js_reachlvl_").append(s).append("', shovel_js_lvl)\n");
            }
        }
        if (xpChangedEvents != null) {
            for (EventEntry e : xpChangedEvents) {
                String s = safe(e.id);
                sb.append("  // ").append(e.id).append(" (XP_CHANGED minDelta ").append(e.trigger.xpMinDelta).append(")\n");
                sb.append("  if (shovel_js_pd.contains('shovel_js_lastlvl_").append(s).append("')) {\n");
                sb.append("    var shovel_js_lastlvl_").append(s).append(" = shovel_js_pd.getInt('shovel_js_lastlvl_").append(s).append("')\n");
                sb.append("    if (shovel_js_lvl - shovel_js_lastlvl_").append(s).append(" >= ").append(e.trigger.xpMinDelta).append(" && shovel_js_check_").append(s).append("(player)) {\n");
                sb.append("      shovel_js_act_").append(s).append("(player)\n");
                sb.append("      shovel_js_mark_").append(s).append("(player)\n");
                sb.append("    }\n");
                sb.append("  }\n");
                sb.append("  shovel_js_pd.putInt('shovel_js_lastlvl_").append(s).append("', shovel_js_lvl)\n");
            }
        }
        if (effectEvents != null) {
            for (EventEntry e : effectEvents) {
                String s = safe(e.id);
                sb.append("  // ").append(e.id).append(" (EFFECT_GAINED ").append(e.trigger.effectId).append(")\n");
                sb.append("  { var shovel_js_ef_").append(s).append(" = false; try { shovel_js_ef_").append(s).append(" = player.potionEffects.isActive('").append(JsStrings.escape(e.trigger.effectId)).append("') } catch (ex) {}\n");
                sb.append("    var shovel_js_hadef_").append(s).append(" = shovel_js_pd.getBoolean('shovel_js_hadef_").append(s).append("')\n");
                sb.append("    if (shovel_js_ef_").append(s).append(" && !shovel_js_hadef_").append(s).append(" && shovel_js_check_").append(s).append("(player)) {\n");
                sb.append("      shovel_js_act_").append(s).append("(player)\n");
                sb.append("      shovel_js_mark_").append(s).append("(player)\n");
                sb.append("    }\n");
                sb.append("    shovel_js_pd.putBoolean('shovel_js_hadef_").append(s).append("', shovel_js_ef_").append(s).append(") }\n");
            }
        }
        sb.append("}\n\n");
        PLAYER_POLLS.get().add("xp_effect");
    }

    private static void appendAdvancementEarnedBlock(StringBuilder sb, List<EventEntry> advEvents) {
        if (advEvents == null || advEvents.isEmpty()) return;

        sb.append("// ADVANCEMENT_EARNED -> PlayerEvents.advancement\n");
        sb.append("try {\n");
        sb.append("PlayerEvents.advancement(event => {\n");
        sb.append("  var player = event.player\n");
        sb.append("  var shovel_js_advid = ''\n");
        sb.append("  try { shovel_js_advid = String(event.advancement.id) } catch (ex) { try { shovel_js_advid = String(event.advancement) } catch (ex2) {} }\n");
        for (EventEntry e : advEvents) {
            String s = safe(e.id);
            sb.append("  // ").append(e.id).append(" (ADVANCEMENT_EARNED ").append(e.trigger.advancementId).append(")\n");
            sb.append("  if (shovel_js_advid === '").append(JsStrings.escape(e.trigger.advancementId)).append("' && shovel_js_check_").append(s).append("(player)) {\n");
            sb.append("    shovel_js_act_").append(s).append("(player)\n");
            sb.append("    shovel_js_mark_").append(s).append("(player)\n");
            sb.append("  }\n");
        }
        sb.append("})\n");
        sb.append("} catch (ex) { console.error('[Shovel JS] evento ADVANCEMENT_EARNED (PlayerEvents.advancement) no se pudo registrar: ' + ex) }\n\n");
    }

    private static void appendItemUseBlock(StringBuilder sb, List<EventEntry> useEvents) {
        if (useEvents == null || useEvents.isEmpty()) return;

        sb.append("// ITEM_USE -> ItemEvents.rightClicked\n");
        sb.append("try {\n");
        sb.append("ItemEvents.rightClicked(event => {\n");
        sb.append("  var player = event.player\n");
        sb.append("  if (!player) return\n");
        sb.append("  var shovel_js_useid = event.item.id\n");
        for (EventEntry e : useEvents) {
            String s = safe(e.id);
            boolean isTag = e.trigger.itemId != null && e.trigger.itemId.startsWith("#");
            sb.append("  // ").append(e.id).append(" (ITEM_USE ").append(e.trigger.itemId).append(")\n");
            String matchExpr = isTag
                    ? "event.item.hasTag('" + JsStrings.escape(e.trigger.itemId.substring(1)) + "')"
                    : "shovel_js_useid === '" + JsStrings.escape(e.trigger.itemId) + "'";
            sb.append("  if (").append(matchExpr).append(" && shovel_js_check_").append(s).append("(player)) {\n");
            sb.append("    shovel_js_act_").append(s).append("(player)\n");
            sb.append("    shovel_js_mark_").append(s).append("(player)\n");
            sb.append("  }\n");
        }
        sb.append("})\n");
        sb.append("} catch (ex) { console.error('[Shovel JS] evento ITEM_USE (ItemEvents.rightClicked) no se pudo registrar: ' + ex) }\n\n");
    }

    private static void appendEntityKillAnyBlock(StringBuilder sb, List<EventEntry> events) {
        if (events == null || events.isEmpty()) return;

        sb.append("// ENTITY_KILL_ANY -> EntityEvents.death filtrando victima no-jugador\n");
        sb.append("try {\n");
        sb.append("EntityEvents.death(event => {\n");
        sb.append("  var src = event.source\n");
        sb.append("  var attacker = null\n");
        sb.append("  if (src) {\n");
        sb.append("    if (typeof src.getEntity === 'function') { try { attacker = src.getEntity() } catch (ex) {} }\n");
        sb.append("    if (!attacker && typeof src.getDirectEntity === 'function') { try { attacker = src.getDirectEntity() } catch (ex) {} }\n");
        sb.append("    if (!attacker && src.entity) attacker = src.entity\n");
        sb.append("    if (!attacker && src.directEntity) attacker = src.directEntity\n");
        sb.append("    if (!attacker && src.causingEntity) attacker = src.causingEntity\n");
        sb.append("  }\n");
        sb.append("  if (!attacker || !attacker.player) return\n");
        sb.append("  if (event.entity.player) return\n");
        sb.append("  var player = attacker\n");
        for (EventEntry e : events) {
            String s = safe(e.id);
            sb.append("  // ").append(e.id).append(" (ENTITY_KILL_ANY x").append(e.trigger.count).append(")\n");
            sb.append("  if (shovel_js_check_").append(s).append("(player)) {\n");
            sb.append("    var shovel_js_pd_").append(s).append(" = player.persistentData\n");
            sb.append("    var shovel_js_n_").append(s).append(" = shovel_js_pd_").append(s).append(".getInt('shovel_js_ekills_").append(s).append("') + 1\n");
            sb.append("    shovel_js_pd_").append(s).append(".putInt('shovel_js_ekills_").append(s).append("', shovel_js_n_").append(s).append(")\n");
            sb.append("    if (shovel_js_n_").append(s).append(" >= ").append(e.trigger.count).append(") {\n");
            sb.append("      shovel_js_pd_").append(s).append(".putInt('shovel_js_ekills_").append(s).append("', 0)\n");
            sb.append("      shovel_js_act_").append(s).append("(player)\n");
            sb.append("      shovel_js_mark_").append(s).append("(player)\n");
            sb.append("    }\n");
            sb.append("  }\n");
        }
        sb.append("})\n");
        sb.append("} catch (ex) { console.error('[Shovel JS] evento ENTITY_KILL_ANY (EntityEvents.death) no se pudo registrar: ' + ex) }\n\n");
    }

    private static void appendEntitySpawnBlock(StringBuilder sb, List<EventEntry> events) {
        if (events == null || events.isEmpty()) return;

        sb.append("// ENTITY_SPAWN -> EntityEvents.spawned filtrando entidad no-jugador\n");
        sb.append("try {\n");
        sb.append("EntityEvents.spawned(event => {\n");
        sb.append("  var mob = event.entity\n");
        sb.append("  if (!mob || mob.player || !mob.living) return\n");
        sb.append("  var shovel_js_type = String(mob.type)\n");
        sb.append("  var player = mob\n");
        for (EventEntry e : events) {
            String s = safe(e.id);
            boolean hasFilter = e.trigger.entityTypeId != null && !e.trigger.entityTypeId.isBlank();
            sb.append("  // ").append(e.id).append(" (ENTITY_SPAWN").append(hasFilter ? " " + e.trigger.entityTypeId : " cualquier entidad").append(")\n");
            sb.append("  if (").append(hasFilter ? "shovel_js_type === '" + JsStrings.escape(e.trigger.entityTypeId) + "' && " : "").append("shovel_js_check_").append(s).append("(player)) {\n");
            sb.append("    shovel_js_act_").append(s).append("(player)\n");
            sb.append("    shovel_js_mark_").append(s).append("(player)\n");
            sb.append("  }\n");
        }
        sb.append("})\n");
        sb.append("} catch (ex) { console.error('[Shovel JS] evento ENTITY_SPAWN (EntityEvents.spawned) no se pudo registrar: ' + ex) }\n\n");
    }

    private static void appendEntityDeathBlock(StringBuilder sb, List<EventEntry> events) {
        if (events == null || events.isEmpty()) return;

        sb.append("// ENTITY_DEATH -> EntityEvents.death filtrando victima no-jugador\n");
        sb.append("try {\n");
        sb.append("EntityEvents.death(event => {\n");
        sb.append("  var mob = event.entity\n");
        sb.append("  if (!mob || mob.player) return\n");
        sb.append("  var shovel_js_type = String(mob.type)\n");
        sb.append("  var player = mob\n");
        for (EventEntry e : events) {
            String s = safe(e.id);
            boolean hasFilter = e.trigger.entityTypeId != null && !e.trigger.entityTypeId.isBlank();
            sb.append("  // ").append(e.id).append(" (ENTITY_DEATH").append(hasFilter ? " " + e.trigger.entityTypeId : " cualquier entidad").append(")\n");
            sb.append("  if (").append(hasFilter ? "shovel_js_type === '" + JsStrings.escape(e.trigger.entityTypeId) + "' && " : "").append("shovel_js_check_").append(s).append("(player)) {\n");
            sb.append("    shovel_js_act_").append(s).append("(player)\n");
            sb.append("    shovel_js_mark_").append(s).append("(player)\n");
            sb.append("  }\n");
        }
        sb.append("})\n");
        sb.append("} catch (ex) { console.error('[Shovel JS] evento ENTITY_DEATH (EntityEvents.death) no se pudo registrar: ' + ex) }\n\n");
    }

    private static void appendEntityAttacksBlock(StringBuilder sb, List<EventEntry> events) {
        if (events == null || events.isEmpty()) return;

        sb.append("// ENTITY_ATTACKS -> EntityEvents.hurt filtrando atacante no-jugador\n");
        sb.append("try {\n");
        sb.append("EntityEvents.hurt(event => {\n");
        sb.append("  var src = event.source\n");
        sb.append("  var attacker = null\n");
        sb.append("  if (src) {\n");
        sb.append("    if (typeof src.getEntity === 'function') { try { attacker = src.getEntity() } catch (ex) {} }\n");
        sb.append("    if (!attacker && typeof src.getDirectEntity === 'function') { try { attacker = src.getDirectEntity() } catch (ex) {} }\n");
        sb.append("    if (!attacker && src.entity) attacker = src.entity\n");
        sb.append("    if (!attacker && src.directEntity) attacker = src.directEntity\n");
        sb.append("    if (!attacker && src.causingEntity) attacker = src.causingEntity\n");
        sb.append("  }\n");
        sb.append("  if (!attacker || attacker.player) return\n");
        sb.append("  var shovel_js_type = String(attacker.type)\n");
        sb.append("  var player = attacker\n");
        for (EventEntry e : events) {
            String s = safe(e.id);
            boolean hasFilter = e.trigger.entityTypeId != null && !e.trigger.entityTypeId.isBlank();
            sb.append("  // ").append(e.id).append(" (ENTITY_ATTACKS").append(hasFilter ? " " + e.trigger.entityTypeId : " cualquier entidad").append(")\n");
            sb.append("  if (").append(hasFilter ? "shovel_js_type === '" + JsStrings.escape(e.trigger.entityTypeId) + "' && " : "").append("shovel_js_check_").append(s).append("(player)) {\n");
            sb.append("    shovel_js_act_").append(s).append("(player)\n");
            sb.append("    shovel_js_mark_").append(s).append("(player)\n");
            sb.append("  }\n");
        }
        sb.append("})\n");
        sb.append("} catch (ex) { console.error('[Shovel JS] evento ENTITY_ATTACKS (EntityEvents.hurt) no se pudo registrar: ' + ex) }\n\n");
    }

    private static void appendEntityInteractBlock(StringBuilder sb, List<EventEntry> events) {
        if (events == null || events.isEmpty()) return;

        sb.append("// ENTITY_INTERACT -> ItemEvents.entityInteracted\n");
        sb.append("try {\n");
        sb.append("ItemEvents.entityInteracted(event => {\n");
        sb.append("  var player = event.player\n");
        sb.append("  if (!player || String(event.hand) !== 'MAIN_HAND') return\n");
        sb.append("  var shovel_js_entid = String(event.target.type)\n");
        for (EventEntry e : events) {
            String s = safe(e.id);
            sb.append("  // ").append(e.id).append(" (ENTITY_INTERACT ").append(e.trigger.entityTypeId).append(")\n");
            sb.append("  if (shovel_js_entid === '").append(JsStrings.escape(e.trigger.entityTypeId)).append("' && shovel_js_check_").append(s).append("(player)) {\n");
            sb.append("    shovel_js_act_").append(s).append("(player)\n");
            sb.append("    shovel_js_mark_").append(s).append("(player)\n");
            sb.append("  }\n");
        }
        sb.append("})\n");
        sb.append("} catch (ex) { console.error('[Shovel JS] evento ENTITY_INTERACT (ItemEvents.entityInteracted) no se pudo registrar: ' + ex) }\n\n");
    }

    private static void appendMountBlock(StringBuilder sb, List<EventEntry> events) {
        if (events == null || events.isEmpty()) return;

        sb.append("// MOUNT -> PlayerEvents.tick a 1 Hz comparando player.getVehicle()\n");
        sb.append("function shovel_js_ptick_mount(player) {\n");
        sb.append("  var shovel_js_pd = player.persistentData\n");
        sb.append("  var shovel_js_veh = null; try { shovel_js_veh = player.getVehicle() } catch (ex) {}\n");
        sb.append("  var shovel_js_vehid = shovel_js_veh ? String(shovel_js_veh.type) : ''\n");
        for (EventEntry e : events) {
            String s = safe(e.id);
            sb.append("  // ").append(e.id).append(" (MOUNT ").append(e.trigger.entityTypeId).append(")\n");
            sb.append("  { var shovel_js_wasmounted_").append(s).append(" = shovel_js_pd.getBoolean('shovel_js_wasmounted_").append(s).append("')\n");
            String typeGuard = (e.trigger.entityTypeId != null && !e.trigger.entityTypeId.isBlank())
                    ? " && shovel_js_vehid === '" + JsStrings.escape(e.trigger.entityTypeId) + "'" : "";
            sb.append("    if (shovel_js_veh && !shovel_js_wasmounted_").append(s).append(typeGuard).append(" && shovel_js_check_").append(s).append("(player)) {\n");
            sb.append("      shovel_js_act_").append(s).append("(player)\n");
            sb.append("      shovel_js_mark_").append(s).append("(player)\n");
            sb.append("    }\n");
            sb.append("    shovel_js_pd.putBoolean('shovel_js_wasmounted_").append(s).append("', !!shovel_js_veh) }\n");
        }
        sb.append("}\n\n");
        PLAYER_POLLS.get().add("mount");
    }

    private static void appendPlayerDisconnectBlock(StringBuilder sb, List<EventEntry> events) {
        if (events == null || events.isEmpty()) return;

        sb.append("// PLAYER_DISCONNECT -> PlayerEvents.loggedOut\n");
        sb.append("try {\n");
        sb.append("PlayerEvents.loggedOut(event => {\n");
        sb.append("  var player = event.player\n");
        for (EventEntry e : events) {
            String s = safe(e.id);
            sb.append("  if (shovel_js_check_").append(s).append("(player)) {\n");
            sb.append("    shovel_js_act_").append(s).append("(player)\n");
            sb.append("    shovel_js_mark_").append(s).append("(player)\n");
            sb.append("  }\n");
        }
        sb.append("})\n");
        sb.append("} catch (ex) { console.error('[Shovel JS] evento PLAYER_DISCONNECT (PlayerEvents.loggedOut) no se pudo registrar: ' + ex) }\n\n");
    }

    private static void appendServerStartupBlock(StringBuilder sb, List<EventEntry> events) {
        if (events == null || events.isEmpty()) return;

        sb.append("// SERVER_STARTUP -> ServerEvents.loaded, una vez sin jugador (solo acciones globales/broadcast)\n");
        sb.append("try {\n");
        sb.append("ServerEvents.loaded(event => {\n");
        sb.append("  shovel_js_serverRef = event.server\n");
        for (EventEntry e : events) {
            sb.append("  // ").append(e.id).append(" (SERVER_STARTUP)\n");
            sb.append("  shovel_js_act_").append(safe(e.id)).append("(null)\n");
        }
        sb.append("})\n");
        sb.append("} catch (ex) { console.error('[Shovel JS] evento SERVER_STARTUP (ServerEvents.loaded) no se pudo registrar: ' + ex) }\n\n");
    }

    private static void appendForgeBridgeHandlers(StringBuilder sb, Map<EventEntry.TriggerType, List<EventEntry>> byTrigger) {
        List<EventEntry> tame = byTrigger.get(EventEntry.TriggerType.TAMING);
        List<EventEntry> breed = byTrigger.get(EventEntry.TriggerType.BREEDING);
        List<EventEntry> fish = byTrigger.get(EventEntry.TriggerType.FISHING_CATCH);
        List<EventEntry> trade = byTrigger.get(EventEntry.TriggerType.VILLAGER_TRADE);
        List<EventEntry> empty = byTrigger.get(EventEntry.TriggerType.EMPTY_BUCKET);
        List<EventEntry> fill = byTrigger.get(EventEntry.TriggerType.FILL_BUCKET);
        sb.append("// TAMING / BREEDING / FISHING_CATCH / VILLAGER_TRADE / EMPTY_BUCKET / FILL_BUCKET -> eventos Forge\n");
        sb.append("// reenviados por kubejs/startup_scripts/shovel_js/events/forge_bridge.js (KubeJS 6 no tiene hooks propios\n");
        sb.append("// para estos; ForgeEvents solo existe en startup scripts). Reiniciar el juego tras instalar el puente.\n");
        sb.append("try {\n");

        sb.append("global['shovel_js_fx_tame'] = ");
        if (notEmpty(tame)) {
            sb.append("function (event) {\n");
            sb.append("  var player = event.getTamer()\n");
            sb.append("  if (!player) return\n");
            sb.append("  var shovel_js_entid = String(event.getAnimal().type)\n");
            appendEntityTypeDispatch(sb, tame, "TAMING");
            sb.append("}\n");
        } else sb.append("null\n");

        sb.append("global['shovel_js_fx_breed'] = ");
        if (notEmpty(breed)) {
            sb.append("function (event) {\n");
            sb.append("  var player = event.getCausedByPlayer()\n");
            sb.append("  if (!player) return\n");
            sb.append("  var shovel_js_entid = String(event.getParentA().type)\n");
            appendEntityTypeDispatch(sb, breed, "BREEDING");
            sb.append("}\n");
        } else sb.append("null\n");

        sb.append("global['shovel_js_fx_fish'] = ");
        if (notEmpty(fish)) {
            sb.append("function (event) {\n");
            sb.append("  var player = event.getEntity()\n");
            sb.append("  if (!player) return\n");
            sb.append("  var shovel_js_drops = event.getDrops()\n");
            for (EventEntry e : fish) {
                String s = safe(e.id);
                sb.append("  // ").append(e.id).append(" (FISHING_CATCH ").append(e.trigger.itemId).append(")\n");
                sb.append("  { var shovel_js_hit_").append(s).append(" = false\n");
                sb.append("    for (var shovel_js_i = 0; shovel_js_i < shovel_js_drops.size(); shovel_js_i++) { var shovel_js_stack = shovel_js_drops.get(shovel_js_i); if (")
                        .append(itemMatchExpr("shovel_js_stack", e.trigger.itemId)).append(") shovel_js_hit_").append(s).append(" = true }\n");
                sb.append("    if (shovel_js_hit_").append(s).append(" && shovel_js_check_").append(s).append("(player)) {\n");
                sb.append("      shovel_js_act_").append(s).append("(player)\n");
                sb.append("      shovel_js_mark_").append(s).append("(player)\n");
                sb.append("    } }\n");
            }
            sb.append("}\n");
        } else sb.append("null\n");

        sb.append("global['shovel_js_fx_trade'] = ");
        if (notEmpty(trade)) {
            sb.append("function (event) {\n");
            sb.append("  var player = event.getEntity()\n");
            sb.append("  if (!player || player.level.isClientSide()) return\n");
            sb.append("  var shovel_js_stack = event.getMerchantOffer().getResult()\n");
            for (EventEntry e : trade) {
                String s = safe(e.id);
                sb.append("  // ").append(e.id).append(" (VILLAGER_TRADE ").append(e.trigger.itemId).append(")\n");
                sb.append("  if (").append(itemMatchExpr("shovel_js_stack", e.trigger.itemId)).append(" && shovel_js_check_").append(s).append("(player)) {\n");
                sb.append("    shovel_js_act_").append(s).append("(player)\n");
                sb.append("    shovel_js_mark_").append(s).append("(player)\n");
                sb.append("  }\n");
            }
            sb.append("}\n");
        } else sb.append("null\n");

        sb.append("global['shovel_js_fx_bucket'] = ");
        if (notEmpty(empty) || notEmpty(fill)) {
            sb.append("function (event) {\n");
            sb.append("  var player = event.getEntity()\n");
            sb.append("  if (!player || event.getLevel().isClientSide()) return\n");
            sb.append("  var shovel_js_held = event.getEmptyBucket()\n");
            sb.append("  var shovel_js_filling = String(shovel_js_held.id) === 'minecraft:bucket'\n");
            sb.append("  var shovel_js_fluid = null\n");
            sb.append("  try {\n");
            sb.append("    shovel_js_fluid = shovel_js_filling\n");
            sb.append("      ? event.getLevel().getFluidState(event.getTarget().getBlockPos()).getType()\n");
            sb.append("      : shovel_js_held.item.getFluid()\n");
            sb.append("  } catch (ex) { return }\n");
            sb.append("  var shovel_js_fluidid = String(shovel_js_fluid.builtInRegistryHolder().key().location())\n");
            sb.append("  if (shovel_js_fluidid === 'minecraft:empty') return\n");
            if (notEmpty(fill)) {
                sb.append("  if (shovel_js_filling) {\n");
                appendFluidDispatch(sb, fill, "FILL_BUCKET");
                sb.append("  }\n");
            }
            if (notEmpty(empty)) {
                sb.append("  if (!shovel_js_filling) {\n");
                appendFluidDispatch(sb, empty, "EMPTY_BUCKET");
                sb.append("  }\n");
            }
            sb.append("}\n");
        } else sb.append("null\n");

        sb.append("} catch (ex) { console.error('[Shovel JS] handlers del puente Forge no se pudieron asignar: ' + ex) }\n\n");
    }

    private static boolean notEmpty(List<EventEntry> l) { return l != null && !l.isEmpty(); }

    private static void appendEntityTypeDispatch(StringBuilder sb, List<EventEntry> events, String triggerName) {
        for (EventEntry e : events) {
            String s = safe(e.id);
            sb.append("  // ").append(e.id).append(" (").append(triggerName).append(" ").append(e.trigger.entityTypeId).append(")\n");
            String guard = (e.trigger.entityTypeId != null && !e.trigger.entityTypeId.isBlank())
                    ? "shovel_js_entid === '" + JsStrings.escape(e.trigger.entityTypeId) + "' && " : "";
            sb.append("  if (").append(guard).append("shovel_js_check_").append(s).append("(player)) {\n");
            sb.append("    shovel_js_act_").append(s).append("(player)\n");
            sb.append("    shovel_js_mark_").append(s).append("(player)\n");
            sb.append("  }\n");
        }
    }

    private static void appendFluidDispatch(StringBuilder sb, List<EventEntry> events, String triggerName) {
        for (EventEntry e : events) {
            String s = safe(e.id);
            String f = e.trigger.fluidId == null ? "" : e.trigger.fluidId.trim();
            sb.append("    // ").append(e.id).append(" (").append(triggerName).append(" ").append(f).append(")\n");
            String guard;
            if (f.isEmpty()) guard = "";
            else if (f.startsWith("#")) guard = "shovel_js_fluid.defaultFluidState().is(Java.loadClass('net.minecraft.tags.TagKey').create(Java.loadClass('net.minecraft.core.registries.Registries').FLUID, '"
                    + JsStrings.escape(f.substring(1)) + "')) && ";
            else guard = "shovel_js_fluidid === '" + JsStrings.escape(f) + "' && ";
            sb.append("    if (").append(guard).append("shovel_js_check_").append(s).append("(player)) {\n");
            sb.append("      shovel_js_act_").append(s).append("(player)\n");
            sb.append("      shovel_js_mark_").append(s).append("(player)\n");
            sb.append("    }\n");
        }
    }

    private static void appendCommandBlock(StringBuilder sb, List<EventEntry> commandEvents) {
        if (commandEvents == null || commandEvents.isEmpty()) return;

        sb.append("// COMMAND -> /shovel event <name>, un solo comando compartido.\n");
        sb.append("// Permiso de Shovel JS: nivel de\n");
        sb.append("// comando vanilla, igual que /op - MVP no tiene un puente runtime hacia\n");
        sb.append("// ClientPermissionState (esa clase es @OnlyIn(Dist.CLIENT), sin homologo de server\n");
        sb.append("// accesible desde KubeJS), asi que el gate real usa el mismo mecanismo de\n");
        sb.append("// permisos que ya entiende cualquier server vanilla/Forge.\n");
        sb.append("try {\n");
        sb.append("ServerEvents.commandRegistry(event => {\n");
        sb.append("  var Commands = event.commands\n");
        sb.append("  var Arguments = event.arguments\n");
        sb.append("  event.register(\n");
        sb.append("    Commands.literal('shovel').then(\n");
        sb.append("      Commands.literal('event').requires(src => src.hasPermission(").append(KVE_EVENT_COMMAND_PERMISSION_LEVEL).append(")).then(\n");
        sb.append("        Commands.argument('name', Arguments.GREEDY_STRING.create(event)).executes(ctx => {\n");
        sb.append("          var player = ctx.source.player\n");
        sb.append("          if (!player) return 0\n");
        sb.append("          var name = String(Arguments.GREEDY_STRING.getResult(ctx, 'name')).trim()\n");
        for (EventEntry e : commandEvents) {
            String s = safe(e.id);
            sb.append("          if (name === '").append(JsStrings.escape(e.trigger.commandName)).append("') {\n");
            if (e.limits.maxGlobalStarts > 0) {
                sb.append("            var shovel_js_g_").append(s).append(" = ctx.source.server.persistentData\n");
                sb.append("            if (shovel_js_g_").append(s).append(".getInt('shovel_js_starts_").append(s).append("') >= ").append(e.limits.maxGlobalStarts).append(") {\n");
                sb.append("              player.tell('Shovel JS: este evento ya alcanzo su limite global de usos.')\n");
                sb.append("              return 0\n");
                sb.append("            }\n");
                sb.append("            if (shovel_js_check_").append(s).append("(player)) {\n");
                sb.append("              shovel_js_g_").append(s).append(".putInt('shovel_js_starts_").append(s).append("', shovel_js_g_").append(s).append(".getInt('shovel_js_starts_").append(s).append("') + 1)\n");
                sb.append("              shovel_js_act_").append(s).append("(player)\n");
                sb.append("              shovel_js_mark_").append(s).append("(player)\n");
                sb.append("            }\n");
            } else {
                sb.append("            if (shovel_js_check_").append(s).append("(player)) {\n");
                sb.append("              shovel_js_act_").append(s).append("(player)\n");
                sb.append("              shovel_js_mark_").append(s).append("(player)\n");
                sb.append("            }\n");
            }
            sb.append("            return 1\n");
            sb.append("          }\n");
        }
        sb.append("          return 0\n");
        sb.append("        })\n");
        sb.append("      )\n");
        sb.append("    )\n");
        sb.append("  )\n");
        sb.append("})\n");
        sb.append("} catch (ex) { console.error('[Shovel JS] evento COMMAND (/kve event) no se pudo registrar: ' + ex) }\n\n");
    }

    private static final int KVE_ADMIN_RESET_PERMISSION_LEVEL = 3;

    /**
     * {@code /kve reset <player> <event>},
     * borra el estado de limite (flag oncePerPlayer() en ambos storages posibles via
     * {@code kve_resetFlag}, cooldown, y contador de kills si el trigger es
     * PLAYER_KILL) de un evento puntual para un jugador puntual, sin tener que
     * borrar/recrear al jugador entero para poder volver a probar un evento.
     * Se emite para TODOS los eventos habilitados (no solo COMMAND) porque el
     * limite a resetear puede venir de cualquier trigger.
     */
    private static void appendAdminResetBlock(StringBuilder sb, List<EventEntry> allEnabled) {
        if (allEnabled == null || allEnabled.isEmpty()) return;

        sb.append("// ADMIN: /shovel reset <player> <event> - limpia oncePerPlayer (stage y/o\n");
        sb.append("// shovel_js_pdflag_*), cooldown y contador de kills de un evento puntual para un\n");
        sb.append("// jugador puntual\n");
        sb.append("try {\n");
        sb.append("ServerEvents.commandRegistry(event => {\n");
        sb.append("  var Commands = event.commands\n");
        sb.append("  var Arguments = event.arguments\n");
        sb.append("  event.register(\n");
        sb.append("    Commands.literal('shovel').then(\n");
        sb.append("      Commands.literal('reset').requires(src => src.hasPermission(").append(KVE_ADMIN_RESET_PERMISSION_LEVEL).append(")).then(\n");
        sb.append("        Commands.argument('player', Arguments.STRING.create(event)).then(\n");
        sb.append("          Commands.argument('event', Arguments.GREEDY_STRING.create(event)).executes(ctx => {\n");
        sb.append("            var shovel_js_pname = Arguments.STRING.getResult(ctx, 'player')\n");
        sb.append("            var shovel_js_ename = String(Arguments.GREEDY_STRING.getResult(ctx, 'event')).trim()\n");
        sb.append("            var shovel_js_target = null\n");
        sb.append("            var shovel_js_players = ctx.source.server.players\n");
        sb.append("            for (var shovel_js_i = 0; shovel_js_i < shovel_js_players.length; shovel_js_i++) {\n");
        sb.append("              if (shovel_js_players[shovel_js_i].username === shovel_js_pname) { shovel_js_target = shovel_js_players[shovel_js_i]; break }\n");
        sb.append("            }\n");
        sb.append("            if (!shovel_js_target) {\n");
        sb.append("              ctx.source.sendFailure(Text.of('Shovel JS: jugador no encontrado: ' + shovel_js_pname))\n");
        sb.append("              return 0\n");
        sb.append("            }\n");
        sb.append("            var shovel_js_pd = shovel_js_target.persistentData\n");
        sb.append("            switch (shovel_js_ename) {\n");
        for (EventEntry e : allEnabled) {
            String s = safe(e.id);
            sb.append("              case '").append(JsStrings.escape(e.id)).append("':\n");
            sb.append("                shovel_js_resetFlag(shovel_js_target, 'shovel_js_flag_").append(s).append("')\n");
            sb.append("                shovel_js_pd.remove('shovel_js_cd_").append(s).append("')\n");
            if (e.trigger.type == EventEntry.TriggerType.PLAYER_KILL) {
                sb.append("                shovel_js_pd.remove('shovel_js_kills_").append(s).append("')\n");
            }
            if (e.trigger.type == EventEntry.TriggerType.ENTITY_KILL_ANY) {
                sb.append("                shovel_js_pd.remove('shovel_js_ekills_").append(s).append("')\n");
            }
            if (e.trigger.type == EventEntry.TriggerType.MOUNT) {
                sb.append("                shovel_js_pd.remove('shovel_js_wasmounted_").append(s).append("')\n");
            }
            switch (e.trigger.type) {
                case FIRST_JOIN -> sb.append("                shovel_js_resetFlag(shovel_js_target, 'shovel_js_joined_").append(s).append("')\n");
                case BLOCK_BREAK, BLOCK_PLACE -> sb.append("                shovel_js_pd.remove('shovel_js_bcnt_").append(s).append("')\n");
                case ITEM_CRAFT -> sb.append("                shovel_js_pd.remove('shovel_js_craftcnt_").append(s).append("')\n");
                case ITEM_PICKUP -> sb.append("                shovel_js_pd.remove('shovel_js_pkacc_").append(s).append("')\n");
                case XP_LEVEL_REACH -> sb.append("                shovel_js_pd.remove('shovel_js_reachlvl_").append(s).append("')\n");
                case ENTER_BIOME -> sb.append("                shovel_js_pd.remove('shovel_js_inb_").append(s).append("')\n");
                default -> { }
            }
            sb.append("                break\n");
        }
        sb.append("              default:\n");
        sb.append("                ctx.source.sendFailure(Text.of('Shovel JS: evento desconocido: ' + shovel_js_ename))\n");
        sb.append("                return 0\n");
        sb.append("            }\n");
        sb.append("            ctx.source.sendSuccess(() => Text.of('Shovel JS: reset de \\'' + shovel_js_ename + '\\' para ' + shovel_js_pname), true)\n");
        sb.append("            return 1\n");
        sb.append("          })\n");
        sb.append("        )\n");
        sb.append("      )\n");
        sb.append("    )\n");
        sb.append("  )\n");
        sb.append("})\n");
        sb.append("} catch (ex) { console.error('[Shovel JS] evento ADMIN_RESET (/kve reset) no se pudo registrar: ' + ex) }\n\n");
    }

    private static final ThreadLocal<List<String>> PLAYER_POLLS = ThreadLocal.withInitial(ArrayList::new);

    private static void appendCombinedPlayerPoll(StringBuilder sb) {
        List<String> polls = PLAYER_POLLS.get();
        if (polls.isEmpty()) return;
        sb.append("// Poll de jugador a 1 Hz compartido por todos los disparadores de sondeo (un solo listener)\n");
        sb.append("try {\n");
        sb.append("PlayerEvents.tick(event => {\n");
        sb.append("  if (event.player.tickCount % 20 !== 0) return\n");
        sb.append("  var player = event.player\n");
        for (String name : polls) {
            sb.append("  try { shovel_js_ptick_").append(name).append("(player) } catch (ex) { console.error('[Shovel JS] poll ")
              .append(name).append(" fallo: ' + ex) }\n");
        }
        sb.append("})\n");
        sb.append("} catch (ex) { console.error('[Shovel JS] poll de jugador (PlayerEvents.tick) no se pudo registrar: ' + ex) }\n\n");
        polls.clear();
    }

    private static void appendEnterBiomeBlock(StringBuilder sb, List<EventEntry> biomeEvents) {
        if (biomeEvents == null || biomeEvents.isEmpty()) return;

        sb.append("// ENTER_BIOME -> PlayerEvents.tick a 1 Hz comparando el bioma actual contra el ultimo\n");
        sb.append("// visto en persistentData (T06)\n");
        sb.append("function shovel_js_ptick_biome(player) {\n");
        sb.append("  var shovel_js_pd = player.persistentData\n");
        for (EventEntry e : biomeEvents) {
            String s = safe(e.id);
            sb.append("  // ").append(e.id).append(" (ENTER_BIOME ").append(e.trigger.biomeId).append(")\n");
            sb.append("  { var shovel_js_in_").append(s).append(" = shovel_js_inBiome(player, '").append(JsStrings.escape(e.trigger.biomeId)).append("')\n");
            sb.append("    if (shovel_js_in_").append(s).append(" && !shovel_js_pd.getBoolean('shovel_js_inb_").append(s).append("') && shovel_js_check_").append(s).append("(player)) {\n");
            sb.append("      shovel_js_act_").append(s).append("(player)\n");
            sb.append("      shovel_js_mark_").append(s).append("(player)\n");
            sb.append("    }\n");
            sb.append("    shovel_js_pd.putBoolean('shovel_js_inb_").append(s).append("', shovel_js_in_").append(s).append(") }\n");
        }
        sb.append("}\n\n");
        PLAYER_POLLS.get().add("biome");
    }

    /**
     * Read-only preview of exactly what saving this event would emit -
     * just the kve_check_/kve_mark_/kve_act_ function trio, without the
     * shared trigger-dispatch blocks (those only make sense assembled
     * together for every enabled event of that trigger type at once).
     * Same pattern as {@code TagExporter#previewTagJS}.
     */
    public static String previewEventJS(EventEntry e) {
        if (e == null) return "// (sin datos de evento)";
        try {
            StringBuilder sb = new StringBuilder();
            appendEventFunctions(sb, e);
            sb.append("// Trigger: ").append(e.trigger.type).append(" - ver shovel_js_events.js para el bloque de despacho completo.\n");
            return sb.toString();
        } catch (Exception ex) {
            return "// ERROR generando preview: " + ex.getMessage();
        }
    }
}