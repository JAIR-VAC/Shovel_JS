package com.kve.common.customitem;

import com.kve.common.util.SafeFiles;
import com.google.gson.*;
import com.kve.CraftEditorMod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Equivalente de {@link CustomItemState} para {@link CustomBlockDraft} -
 * mismo load/loadIfNeeded()/persist shape (mtime check, atomic tmp+move
 * write, sin undo history). Archivo separado
 * ({@code kve_custom_blocks_state.json}) en vez de compartir
 * {@code kve_custom_items_state.json}: evita tocar el parser/schema de
 * items ya en uso y evita cualquier colision de "path" entre un item y un
 * bloque que casualmente elijan el mismo id (ambos son validos a la vez -
 * KubeJS los registra en registries distintos).
 */
public final class CustomBlockState {

    private CustomBlockState() {}

    private static final Map<String, CustomBlockDraft> DRAFTS = new LinkedHashMap<>();

    public static Collection<CustomBlockDraft> getDrafts() { return Collections.unmodifiableCollection(DRAFTS.values()); }
    public static CustomBlockDraft getDraft(String path) { return DRAFTS.get(path); }

    public static void put(CustomBlockDraft d) {
        d.updatedAt = System.currentTimeMillis();
        DRAFTS.put(d.path(), d);
        persist();
    }

    public static void remove(String path) {
        DRAFTS.remove(path);
        persist();
    }

    private static Path configDir() { return com.kve.common.kubejs.KveScriptPaths.CONFIG_ROOT; }
    private static Path stateFile() { return configDir().resolve("shovel_js_custom_blocks_state.json"); }

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
            String json = Files.readString(stateFile(), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("blocks")) {
                for (JsonElement el : root.getAsJsonArray("blocks")) {
                    try {
                        CustomBlockDraft d = draftFromJson(el.getAsJsonObject());
                        if (!d.path().isBlank()) DRAFTS.put(d.path(), d);
                    } catch (Exception ex) {
                        CraftEditorMod.LOGGER.warn("[Shovel JS] Skipped custom block draft: {}", ex.getMessage());
                    }
                }
            }
            CraftEditorMod.LOGGER.info("[Shovel JS] Custom block drafts loaded from {}", stateFile());
        } catch (Exception ex) {
            CraftEditorMod.LOGGER.error("[Shovel JS] Failed to load custom block drafts: {}", ex.getMessage());
        }
    }

    private static void persist() {
        try {
            Files.createDirectories(configDir());
            JsonObject root = new JsonObject();
            JsonArray arr = new JsonArray();
            for (CustomBlockDraft d : DRAFTS.values()) arr.add(draftToJson(d));
            root.add("blocks", arr);
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(root);
            SafeFiles.writeAtomic(stateFile(), json);
            try { lastMtime = Files.getLastModifiedTime(stateFile()).toMillis(); } catch (IOException ex) {
                CraftEditorMod.LOGGER.debug("[Shovel JS] no se pudo releer el mtime de {} tras guardar: {}", stateFile(), ex.toString());
            }
        } catch (IOException ex) {
            CraftEditorMod.LOGGER.error("[Shovel JS] Custom block drafts save failed: {}", ex.getMessage());
        }
    }

    public static JsonObject draftToJson(CustomBlockDraft d) {
        JsonObject o = new JsonObject();
        o.addProperty("id", d.id);
        o.addProperty("displayName", d.displayName);
        o.addProperty("texturePath", d.texturePath);
        o.addProperty("hardness", d.hardness);
        o.addProperty("resistance", d.resistance);
        o.addProperty("requiresTool", d.requiresTool);
        o.addProperty("lightLevel", d.lightLevel);
        o.addProperty("sound", d.sound);
        o.addProperty("toolType", d.toolType);
        o.addProperty("toolLevel", d.toolLevel);
        o.addProperty("seeThrough", d.seeThrough);
        o.addProperty("noCollision", d.noCollision);
        o.addProperty("slippery", d.slippery);
        o.addProperty("slow", d.slow);
        o.addProperty("tags", d.tags);
        o.addProperty("exported", d.exported);
        o.addProperty("createdAt", d.createdAt);
        o.addProperty("updatedAt", d.updatedAt);
        return o;
    }

    public static CustomBlockDraft draftFromJson(JsonObject o) {
        CustomBlockDraft d = new CustomBlockDraft();
        d.id = str(o, "id", "");
        d.displayName = str(o, "displayName", "");
        d.texturePath = str(o, "texturePath", "");
        d.hardness = o.has("hardness") ? o.get("hardness").getAsFloat() : -1f;
        d.resistance = o.has("resistance") ? o.get("resistance").getAsFloat() : -1f;
        d.requiresTool = o.has("requiresTool") && o.get("requiresTool").getAsBoolean();
        d.lightLevel = o.has("lightLevel") ? o.get("lightLevel").getAsInt() : -1;
        d.sound = str(o, "sound", "");
        d.toolType = str(o, "toolType", "");
        d.toolLevel = str(o, "toolLevel", "");
        d.seeThrough = o.has("seeThrough") && o.get("seeThrough").getAsBoolean();
        d.noCollision = o.has("noCollision") && o.get("noCollision").getAsBoolean();
        d.slippery = o.has("slippery") && o.get("slippery").getAsBoolean();
        d.slow = o.has("slow") && o.get("slow").getAsBoolean();
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
