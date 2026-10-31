package com.kve.common.customitem;

import com.google.gson.*;
import com.kve.CraftEditorMod;
import com.kve.common.util.SafeFiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Borradores de fluidos: mismo formato y recarga por mtime que {@link CustomBlockState}, en su propio archivo. */
public final class CustomFluidState {

    private CustomFluidState() {}

    private static final Map<String, CustomFluidDraft> DRAFTS = new LinkedHashMap<>();

    public static Collection<CustomFluidDraft> getDrafts() { return Collections.unmodifiableCollection(DRAFTS.values()); }
    public static CustomFluidDraft getDraft(String path) { return DRAFTS.get(path); }

    public static void put(CustomFluidDraft d) {
        d.updatedAt = System.currentTimeMillis();
        DRAFTS.put(d.path(), d);
        persist();
    }

    public static void remove(String path) {
        DRAFTS.remove(path);
        persist();
    }

    private static Path configDir() { return com.kve.common.kubejs.KveScriptPaths.CONFIG_ROOT; }
    private static Path stateFile() { return configDir().resolve("shovel_js_custom_fluids_state.json"); }

    private static boolean loaded = false;
    private static long lastMtime = 0L;

    public static void loadIfNeeded() {
        if (loaded) {
            try {
                long mtime = Files.exists(stateFile()) ? Files.getLastModifiedTime(stateFile()).toMillis() : 0;
                if (mtime == lastMtime) return;
            } catch (IOException ignored) { return; }
        }
        load();
    }

    private static void load() {
        loaded = true;
        DRAFTS.clear();
        if (!Files.exists(stateFile())) {
            lastMtime = 0;
            return;
        }
        try {
            lastMtime = Files.getLastModifiedTime(stateFile()).toMillis();
            JsonObject root = JsonParser.parseString(Files.readString(stateFile(), StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.has("fluids")) {
                for (JsonElement el : root.getAsJsonArray("fluids")) {
                    try {
                        CustomFluidDraft d = draftFromJson(el.getAsJsonObject());
                        if (!d.path().isBlank()) DRAFTS.put(d.path(), d);
                    } catch (Exception ex) {
                        CraftEditorMod.LOGGER.warn("[Shovel JS] Skipped custom fluid draft: {}", ex.getMessage());
                    }
                }
            }
        } catch (Exception ex) {
            CraftEditorMod.LOGGER.error("[Shovel JS] Failed to load custom fluid drafts: {}", ex.getMessage());
        }
    }

    private static void persist() {
        try {
            Files.createDirectories(configDir());
            JsonObject root = new JsonObject();
            JsonArray arr = new JsonArray();
            for (CustomFluidDraft d : DRAFTS.values()) arr.add(draftToJson(d));
            root.add("fluids", arr);
            SafeFiles.writeAtomic(stateFile(), new GsonBuilder().setPrettyPrinting().create().toJson(root));
            try { lastMtime = Files.getLastModifiedTime(stateFile()).toMillis(); } catch (IOException ignored) {}
        } catch (IOException ex) {
            CraftEditorMod.LOGGER.error("[Shovel JS] Custom fluid drafts save failed: {}", ex.getMessage());
        }
    }

    public static JsonObject draftToJson(CustomFluidDraft d) {
        JsonObject o = new JsonObject();
        o.addProperty("id", d.id);
        o.addProperty("displayName", d.displayName);
        o.addProperty("color", d.color);
        o.addProperty("thick", d.thick);
        o.addProperty("bucket", d.bucket);
        o.addProperty("tags", d.tags);
        o.addProperty("exported", d.exported);
        o.addProperty("createdAt", d.createdAt);
        o.addProperty("updatedAt", d.updatedAt);
        return o;
    }

    public static CustomFluidDraft draftFromJson(JsonObject o) {
        CustomFluidDraft d = new CustomFluidDraft();
        d.id = str(o, "id", "");
        d.displayName = str(o, "displayName", "");
        d.color = o.has("color") ? o.get("color").getAsInt() & 0xFFFFFF : 0x3F76E4;
        d.thick = o.has("thick") && o.get("thick").getAsBoolean();
        d.bucket = !o.has("bucket") || o.get("bucket").getAsBoolean();
        d.tags = str(o, "tags", "");
        d.exported = o.has("exported") && o.get("exported").getAsBoolean();
        d.createdAt = o.has("createdAt") ? o.get("createdAt").getAsLong() : System.currentTimeMillis();
        d.updatedAt = o.has("updatedAt") ? o.get("updatedAt").getAsLong() : d.createdAt;
        return d;
    }

    private static String str(JsonObject o, String key, String def) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : def;
    }
}
