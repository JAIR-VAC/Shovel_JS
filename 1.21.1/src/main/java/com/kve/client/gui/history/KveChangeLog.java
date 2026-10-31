package com.kve.client.gui.history;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kve.CraftEditorMod;
import com.kve.client.gui.workspace.pending.PendingChange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class KveChangeLog {

    private KveChangeLog() {}

    /** Known log ids - one per KveStudio tab tracked (Properties splits into Items/Blocks, each its own history). */
    public static final class Log {
        private Log() {}
        public static final String RECIPES = "recipes";
        public static final String TAGS = "tags";
        public static final String LOOT = "loot";
        public static final String ITEMS = "content_items";
        public static final String BLOCKS = "content_blocks";
        public static final String MOBS = "content_mobs";
        public static final String EVENTS = "events";
        public static final String WORLDGEN = "worldgen";
        /** C4: constante String (no enum, distinta de PendingDomain.TRADES) para KveChangeLog.record(Log.TRADES, ...). */
        public static final String TRADES = "trades";
        /** Reglas del pack (nombres, info JEI/REI, compostables, spawns, gamerules) - . */
        public static final String PACK_RULES = "pack_rules";
    }

    private static final int MAX_ENTRIES = 200;
    private static final Gson GSON = new GsonBuilder().create();

    public record Entry(long timestamp, PendingChange.Action action, String entryId) {}

    private static final Map<String, List<Entry>> entriesByLog = new HashMap<>();
    private static final Map<String, Boolean> loadedByLog = new HashMap<>();

    private static Path fileFor(String logId) {
        return Paths.get("kubejs", "config", "shovel_js_changelog_" + logId + ".json");
    }

    /** Appends one entry to {@code logId}. Call only after the write it describes actually succeeded. */
    public static void record(String logId, PendingChange.Action action, String entryId) {
        List<Entry> list = ensureLoaded(logId);
        list.add(new Entry(System.currentTimeMillis(), action, entryId));
        while (list.size() > MAX_ENTRIES) list.remove(0);
        save(logId, list);
    }

    /** Newest-first snapshot of {@code logId} for display. */
    public static List<Entry> snapshot(String logId) {
        List<Entry> list = ensureLoaded(logId);
        List<Entry> copy = new ArrayList<>(list);
        Collections.reverse(copy);
        return Collections.unmodifiableList(copy);
    }

    private static List<Entry> ensureLoaded(String logId) {
        List<Entry> list = entriesByLog.computeIfAbsent(logId, k -> new ArrayList<>());
        if (Boolean.TRUE.equals(loadedByLog.get(logId))) return list;
        loadedByLog.put(logId, true);
        Path file = fileFor(logId);
        if (!Files.exists(file)) return list;
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                long ts = o.get("timestamp").getAsLong();
                PendingChange.Action action = PendingChange.Action.valueOf(o.get("action").getAsString());
                String entryId = o.get("entryId").getAsString();
                list.add(new Entry(ts, action, entryId));
            }
            while (list.size() > MAX_ENTRIES) list.remove(0);
        } catch (Exception ex) {
            CraftEditorMod.LOGGER.error("[Shovel JS] Failed to load change log '{}': {}", logId, ex.getMessage());
        }
        return list;
    }

    private static void save(String logId, List<Entry> list) {
        try {
            Path file = fileFor(logId);
            Files.createDirectories(file.getParent());
            JsonArray arr = new JsonArray();
            for (Entry e : list) {
                JsonObject o = new JsonObject();
                o.addProperty("timestamp", e.timestamp());
                o.addProperty("action", e.action().name());
                o.addProperty("entryId", e.entryId());
                arr.add(o);
            }
            Path tmp = file.resolveSibling(file.getFileName().toString() + ".tmp");
            Files.writeString(tmp, GSON.toJson(arr), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            CraftEditorMod.LOGGER.error("[Shovel JS] Failed to save change log '{}': {}", logId, ex.getMessage());
        }
    }
}
