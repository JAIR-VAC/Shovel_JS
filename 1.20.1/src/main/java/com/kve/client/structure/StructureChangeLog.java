package com.kve.client.structure;

import com.kve.common.structure.StructureVoxelGrid;

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
import java.util.List;

public final class StructureChangeLog {

    private StructureChangeLog() {}
        
    
            

    private static final int MAX_ENTRIES = 200;
            

    private static final Path FILE = Paths.get("kubejs", "config", "shovel_js_structure_change_log.json");
    private static final Gson GSON = new GsonBuilder().create();

    private static final List<Entry> entries = new ArrayList<>();
    private static final List<Listener> listeners = new ArrayList<>();
    private static boolean loaded = false;

    public interface Listener { void onStructureChangeLogChanged(); }

    public static void addListener(Listener l) { listeners.add(l); }
    public static void removeListener(Listener l) { listeners.remove(l); }

    public record Entry(long timestamp, PendingChange.Action action, String structureId) {}

    /**
     * One row per distinct {@code structureId()} - the state {@link #snapshotGrouped()}
     * shows in the main "Cambiadas" list instead of one row per {@link #record}
     * call. {@code lastTimestamp}/{@code lastAction} are that structure's most
     * recent entry; {@code count} is how many entries it has in the underlying
     * (capped-at-{@link #MAX_ENTRIES}) log. See {@link #snapshotForStructure()}
     * for the per-structure "its own history section" this collapses into.
     */
    public record GroupedEntry(String structureId, long lastTimestamp, PendingChange.Action lastAction, int count) {}

    /** Appends one entry. Called only after a structure write actually succeeded. */
    public static void record(PendingChange.Action action, String structureId) {
        ensureLoaded();
        entries.add(new Entry(System.currentTimeMillis(), action, structureId));
        while (entries.size() > MAX_ENTRIES) entries.remove(0);
        save();
        notifyListeners();
    }

    /** Newest-first snapshot for display. NOT what the main "Cambiadas" list renders any more - see {@link #snapshotGrouped()} - but still the source {@link #snapshotForStructure(String)} filters. */
    public static List<Entry> snapshot() {
        ensureLoaded();
        List<Entry> copy = new ArrayList<>(entries);
        Collections.reverse(copy);
        return Collections.unmodifiableList(copy);
    }

    /**
     * Newest-first, ONE row per distinct {@code structureId()} - what the main
     * "Cambiadas" list now renders, so editing the same structure repeatedly
     * doesn't push a new row in every time. A structure's row position tracks its most
     * recent {@link #record} call (re-editing an old structure floats it back
     * to the top, same "most recently touched first" ordering {@link #snapshot()}
     * always had) - only the one-row-per-Apply behavior collapses, not the
     * newest-first ordering itself. {@link GroupedEntry#count()} is how many
     * times that structure appears in the underlying log; {@link #snapshotForStructure()}
     * is the drill-down for a group with {@code count > 1}.
     */
    public static List<GroupedEntry> snapshotGrouped() {
        ensureLoaded();
        java.util.LinkedHashMap<String, GroupedEntry> byId = new java.util.LinkedHashMap<>();
        for (Entry e : entries) {
            GroupedEntry prev = byId.remove(e.structureId());
            int count = (prev != null ? prev.count() : 0) + 1;
            byId.put(e.structureId(), new GroupedEntry(e.structureId(), e.timestamp(), e.action(), count));
        }
        List<GroupedEntry> result = new ArrayList<>(byId.values());
        Collections.reverse(result);
        return Collections.unmodifiableList(result);
    }

    /** Every entry for one structure, newest first - the "apartado de historial" a {@link GroupedEntry} with {@code count > 1} drills down into. */
    public static List<Entry> snapshotForStructure(String structureId) {
        ensureLoaded();
        List<Entry> copy = new ArrayList<>();
        for (Entry e : entries) if (e.structureId().equals(structureId)) copy.add(e);
        Collections.reverse(copy);
        return Collections.unmodifiableList(copy);
    }

    public static boolean isEmpty() {
        ensureLoaded();
        return entries.isEmpty();
    }

    private static void notifyListeners() {
        for (Listener l : new ArrayList<>(listeners)) l.onStructureChangeLogChanged();
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        if (!Files.exists(FILE)) return;
        try {
            String json = Files.readString(FILE, StandardCharsets.UTF_8);
            JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                long ts = o.get("timestamp").getAsLong();
                PendingChange.Action action = PendingChange.Action.valueOf(o.get("action").getAsString());
                String structureId = o.get("structureId").getAsString();
                entries.add(new Entry(ts, action, structureId));
            }
            while (entries.size() > MAX_ENTRIES) entries.remove(0);
        } catch (Exception ex) {
            CraftEditorMod.LOGGER.error("[Shovel JS] Failed to load structure change log: {}", ex.getMessage());
        }
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            JsonArray arr = new JsonArray();
            for (Entry e : entries) {
                JsonObject o = new JsonObject();
                o.addProperty("timestamp", e.timestamp());
                o.addProperty("action", e.action().name());
                o.addProperty("structureId", e.structureId());
                arr.add(o);
            }
            Path tmp = FILE.resolveSibling(FILE.getFileName().toString() + ".tmp");
            Files.writeString(tmp, GSON.toJson(arr), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(tmp, FILE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            CraftEditorMod.LOGGER.error("[Shovel JS] Failed to save structure change log: {}", ex.getMessage());
        }
    }
}