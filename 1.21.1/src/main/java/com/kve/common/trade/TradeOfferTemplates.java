package com.kve.common.trade;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kve.CraftEditorMod;
import com.kve.common.kubejs.TradeExporter.TradeOffer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TradeOfferTemplates {

    private TradeOfferTemplates() {}

    private static final Map<String, List<TradeOffer>> TEMPLATES = new LinkedHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Collection<String> names() {
        loadIfNeeded();
        return Collections.unmodifiableCollection(TEMPLATES.keySet());
    }

    public static List<TradeOffer> get(String name) {
        loadIfNeeded();
        List<TradeOffer> found = TEMPLATES.get(name);
        if (found == null) return List.of();
        List<TradeOffer> copies = new ArrayList<>();
        for (TradeOffer o : found) copies.add(o.copy());
        return copies;
    }

    /** Saves (or overwrites) a template under this name - deep-copies the offers so later mutation of the caller's draft list doesn't retroactively change the saved template. */
    public static void save(String name, List<TradeOffer> offers) {
        loadIfNeeded();
        if (name == null || name.isBlank()) return;
        List<TradeOffer> copies = new ArrayList<>();
        for (TradeOffer o : offers) copies.add(o.copy());
        TEMPLATES.put(name, copies);
        persist();
    }

    public static boolean delete(String name) {
        loadIfNeeded();
        boolean removed = TEMPLATES.remove(name) != null;
        if (removed) persist();
        return removed;
    }

    /**
     * Renombra una plantilla (si {@code newName} ya existe, la pisa).
     * Conserva la posicion en el orden de la lista. False si {@code oldName} no existe.
     */
    public static boolean rename(String oldName, String newName) {
        loadIfNeeded();
        if (newName == null || newName.isBlank() || !TEMPLATES.containsKey(oldName)) return false;
        if (oldName.equals(newName)) return true;
        Map<String, List<TradeOffer>> copy = new LinkedHashMap<>(TEMPLATES);
        TEMPLATES.clear();
        for (Map.Entry<String, List<TradeOffer>> e : copy.entrySet()) {
            if (e.getKey().equals(newName)) continue;
            TEMPLATES.put(e.getKey().equals(oldName) ? newName : e.getKey(), e.getValue());
        }
        persist();
        return true;
    }

    /** Appends fresh copies of this template's offers onto {@code target} - additive, same "ADD_OFFERS-style append" semantics as pasting, never replaces what's already in {@code target}. */
    public static void applyTo(String name, List<TradeOffer> target) {
        for (TradeOffer o : get(name)) target.add(o);
    }

    private static Path stateFile() { return Paths.get("kubejs", "config", "shovel_js_trade_templates.json"); }

    private static boolean loaded = false;
    private static long lastMtime = 0L;

    private static void loadIfNeeded() {
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
        TEMPLATES.clear();
        if (!Files.exists(stateFile())) { lastMtime = 0; return; }
        try {
            lastMtime = Files.getLastModifiedTime(stateFile()).toMillis();
            String json = Files.readString(stateFile(), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("templates")) {
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("templates").entrySet()) {
                    try {
                        List<TradeOffer> offers = new ArrayList<>();
                        for (JsonElement el : e.getValue().getAsJsonArray()) offers.add(offerFromJson(el.getAsJsonObject()));
                        TEMPLATES.put(e.getKey(), offers);
                    } catch (Exception ex) {
                        CraftEditorMod.LOGGER.warn("[Shovel JS] Skipped trade template '{}': {}", e.getKey(), ex.getMessage());
                    }
                }
            }
            CraftEditorMod.LOGGER.info("[Shovel JS] Trade offer templates loaded from {}", stateFile());
        } catch (Exception ex) {
            CraftEditorMod.LOGGER.error("[Shovel JS] Failed to load trade templates: {}", ex.getMessage());
        }
    }

    private static void persist() {
        try {
            Files.createDirectories(stateFile().getParent());
            JsonObject root = new JsonObject();
            JsonObject templates = new JsonObject();
            for (Map.Entry<String, List<TradeOffer>> e : TEMPLATES.entrySet()) {
                JsonArray arr = new JsonArray();
                for (TradeOffer o : e.getValue()) arr.add(offerToJson(o));
                templates.add(e.getKey(), arr);
            }
            root.add("templates", templates);
            Path tmp = stateFile().resolveSibling(stateFile().getFileName().toString() + ".tmp");
            Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(tmp, stateFile(), StandardCopyOption.REPLACE_EXISTING);
            try { lastMtime = Files.getLastModifiedTime(stateFile()).toMillis(); } catch (IOException ignored) {}
        } catch (IOException ex) {
            CraftEditorMod.LOGGER.error("[Shovel JS] Failed to save trade templates: {}", ex.getMessage());
        }
    }

    /** Same field set/shape as {@code TradeExporter#entryToJson}'s offer block - kept independent (not reused) since that method is private and this file has no reason to depend on TradeExporter's internals for something this small. */
    public static JsonObject offerToJson(TradeOffer of) {
        JsonObject oo = new JsonObject();
        oo.addProperty("inputAId", of.inputAId); oo.addProperty("inputACount", of.inputACount); oo.addProperty("inputAIsTag", of.inputAIsTag);
        oo.addProperty("inputBId", of.inputBId); oo.addProperty("inputBCount", of.inputBCount); oo.addProperty("inputBIsTag", of.inputBIsTag);
        oo.addProperty("outputId", of.outputId); oo.addProperty("outputCount", of.outputCount); oo.addProperty("outputIsTag", of.outputIsTag);
        oo.addProperty("outputNbt", of.outputNbt);
        oo.addProperty("maxUses", of.maxUses); oo.addProperty("villagerXp", of.villagerXp); oo.addProperty("priceMultiplier", of.priceMultiplier);
        oo.addProperty("enabled", of.enabled);
        com.kve.common.kubejs.TradeExporter.writeOfferExtras(oo, of);
        return oo;
    }

    public static TradeOffer offerFromJson(JsonObject oo) {
        TradeOffer of = new TradeOffer();
        of.inputAId = str(oo, "inputAId", ""); of.inputACount = oo.has("inputACount") ? oo.get("inputACount").getAsInt() : 1;
        of.inputAIsTag = oo.has("inputAIsTag") && oo.get("inputAIsTag").getAsBoolean();
        of.inputBId = str(oo, "inputBId", ""); of.inputBCount = oo.has("inputBCount") ? oo.get("inputBCount").getAsInt() : 1;
        of.inputBIsTag = oo.has("inputBIsTag") && oo.get("inputBIsTag").getAsBoolean();
        of.outputId = str(oo, "outputId", ""); of.outputCount = oo.has("outputCount") ? oo.get("outputCount").getAsInt() : 1;
        of.outputIsTag = oo.has("outputIsTag") && oo.get("outputIsTag").getAsBoolean();
        of.outputNbt = str(oo, "outputNbt", "");
        of.maxUses = oo.has("maxUses") ? oo.get("maxUses").getAsInt() : 12;
        of.villagerXp = oo.has("villagerXp") ? oo.get("villagerXp").getAsInt() : 1;
        of.priceMultiplier = oo.has("priceMultiplier") ? oo.get("priceMultiplier").getAsFloat() : 0.05f;
        of.enabled = !oo.has("enabled") || oo.get("enabled").getAsBoolean();
        com.kve.common.kubejs.TradeExporter.readOfferExtras(oo, of);
        return of;
    }

    private static String str(JsonObject o, String key, String def) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : def;
    }

    /** Test-only: clears in-memory state and forces a reload on next access, without touching the state file. */
    public static void clearForTests() {
        loaded = false;
        TEMPLATES.clear();
    }
}
