package com.kve.common.customitem;

import com.kve.common.util.SafeFiles;
import com.google.gson.*;
import com.kve.CraftEditorMod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class CustomItemState {

    private CustomItemState() {}

    private static final Map<String, CustomItemDraft> DRAFTS = new LinkedHashMap<>();

    public static Collection<CustomItemDraft> getDrafts() { return Collections.unmodifiableCollection(DRAFTS.values()); }
    public static CustomItemDraft getDraft(String path) { return DRAFTS.get(path); }

    public static void put(CustomItemDraft d) {
        d.updatedAt = System.currentTimeMillis();
        DRAFTS.put(d.path(), d);
        persist();
    }

    public static void remove(String path) {
        DRAFTS.remove(path);
        persist();
    }

    private static Path configDir() { return com.kve.common.kubejs.KveScriptPaths.CONFIG_ROOT; }
    private static Path stateFile() { return configDir().resolve("shovel_js_custom_items_state.json"); }

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
            if (root.has("items")) {
                for (JsonElement el : root.getAsJsonArray("items")) {
                    try {
                        CustomItemDraft d = draftFromJson(el.getAsJsonObject());
                        if (!d.path().isBlank()) DRAFTS.put(d.path(), d);
                    } catch (Exception ex) {
                        CraftEditorMod.LOGGER.warn("[Shovel JS] Skipped custom item draft: {}", ex.getMessage());
                    }
                }
            }
            CraftEditorMod.LOGGER.info("[Shovel JS] Custom item drafts loaded from {}", stateFile());
        } catch (Exception ex) {
            CraftEditorMod.LOGGER.error("[Shovel JS] Failed to load custom item drafts: {}", ex.getMessage());
        }
    }

    private static void persist() {
        try {
            Files.createDirectories(configDir());
            JsonObject root = new JsonObject();
            JsonArray arr = new JsonArray();
            for (CustomItemDraft d : DRAFTS.values()) arr.add(draftToJson(d));
            root.add("items", arr);
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(root);
            SafeFiles.writeAtomic(stateFile(), json);
            try { lastMtime = Files.getLastModifiedTime(stateFile()).toMillis(); } catch (IOException ex) {
                CraftEditorMod.LOGGER.debug("[Shovel JS] no se pudo releer el mtime de {} tras guardar: {}", stateFile(), ex.toString());
            }
        } catch (IOException ex) {
            CraftEditorMod.LOGGER.error("[Shovel JS] Custom item drafts save failed: {}", ex.getMessage());
        }
    }

    public static JsonObject draftToJson(CustomItemDraft d) {
        JsonObject o = new JsonObject();
        o.addProperty("id", d.id);
        o.addProperty("displayName", d.displayName);
        o.addProperty("texturePath", d.texturePath);
        o.addProperty("maxStackSize", d.maxStackSize);
        o.addProperty("maxDamage", d.maxDamage);
        o.addProperty("fireResistant", d.fireResistant);
        o.addProperty("rarityIdx", d.rarityIdx);
        o.addProperty("itemType", d.itemType);
        o.addProperty("toolTier", d.toolTier);
        o.addProperty("glow", d.glow);
        o.addProperty("burnTime", d.burnTime);
        o.addProperty("tooltip", d.tooltip);
        o.addProperty("tags", d.tags);
        o.addProperty("food", d.food);
        o.addProperty("foodHunger", d.foodHunger);
        o.addProperty("foodSaturation", d.foodSaturation);
        o.addProperty("foodAlwaysEdible", d.foodAlwaysEdible);
        o.addProperty("foodFast", d.foodFast);
        JsonArray attrs = new JsonArray();
        for (com.kve.common.kubejs.KubeJSItemAttrExporter.AttrMod m : d.attributeModifiers.values()) {
            JsonObject ao = new JsonObject();
            ao.addProperty("id", m.attrId);
            ao.addProperty("value", m.value);
            ao.addProperty("op", m.op.js);
            attrs.add(ao);
        }
        o.add("attributes", attrs);
        o.addProperty("exported", d.exported);
        o.addProperty("createdAt", d.createdAt);
        o.addProperty("updatedAt", d.updatedAt);
        return o;
    }

    public static CustomItemDraft draftFromJson(JsonObject o) {
        CustomItemDraft d = new CustomItemDraft();
        d.id = str(o, "id", "");
        d.displayName = str(o, "displayName", "");
        d.texturePath = str(o, "texturePath", "");
        d.maxStackSize = o.has("maxStackSize") ? o.get("maxStackSize").getAsInt() : 64;
        d.maxDamage = o.has("maxDamage") ? o.get("maxDamage").getAsInt() : 0;
        d.fireResistant = o.has("fireResistant") && o.get("fireResistant").getAsBoolean();
        d.rarityIdx = o.has("rarityIdx") ? o.get("rarityIdx").getAsInt() : 0;
        d.itemType = str(o, "itemType", "basic");
        d.toolTier = str(o, "toolTier", "iron");
        d.glow = o.has("glow") && o.get("glow").getAsBoolean();
        d.burnTime = o.has("burnTime") ? o.get("burnTime").getAsInt() : 0;
        d.tooltip = str(o, "tooltip", "");
        d.tags = str(o, "tags", "");
        d.food = o.has("food") && o.get("food").getAsBoolean();
        d.foodHunger = o.has("foodHunger") ? o.get("foodHunger").getAsInt() : 4;
        d.foodSaturation = o.has("foodSaturation") ? o.get("foodSaturation").getAsFloat() : 0.3f;
        d.foodAlwaysEdible = o.has("foodAlwaysEdible") && o.get("foodAlwaysEdible").getAsBoolean();
        d.foodFast = o.has("foodFast") && o.get("foodFast").getAsBoolean();
        if (o.has("attributes")) {
            for (JsonElement ael : o.getAsJsonArray("attributes")) {
                try {
                    JsonObject ao = ael.getAsJsonObject();
                    String attrId = str(ao, "id", "");
                    if (attrId.isEmpty()) continue;
                    double val = ao.has("value") ? ao.get("value").getAsDouble() : 0;
                    com.kve.common.kubejs.KubeJSItemAttrExporter.Op op = ao.has("op")
                            ? com.kve.common.kubejs.KubeJSItemAttrExporter.Op.fromJs(ao.get("op").getAsString())
                            : com.kve.common.kubejs.KubeJSItemAttrExporter.Op.ADDITION;
                    d.attributeModifiers.put(attrId, new com.kve.common.kubejs.KubeJSItemAttrExporter.AttrMod(attrId, val, op));
                } catch (Exception ex) {
                    CraftEditorMod.LOGGER.warn("[Shovel JS] Skipped custom item attribute: {}", ex.getMessage());
                }
            }
        }
        d.exported = o.has("exported") && o.get("exported").getAsBoolean();
        d.createdAt = o.has("createdAt") ? o.get("createdAt").getAsLong() : System.currentTimeMillis();
        d.updatedAt = o.has("updatedAt") ? o.get("updatedAt").getAsLong() : d.createdAt;
        return d;
    }

    private static String str(JsonObject o, String key, String def) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : def;
    }
}
