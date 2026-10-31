package com.kve.common.worldgen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kve.CraftEditorMod;
import com.kve.common.util.SafeFiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class OreOverrideExporter {

    private OreOverrideExporter() {}

    static final String NS = "shovel_js";
    static final String DIR = "ore_override";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static final Map<String, OreOverride> ENTRIES = new LinkedHashMap<>();
    private static final Set<String> WRITTEN = new LinkedHashSet<>();

    public static Collection<OreOverride> getEntries() { return Collections.unmodifiableCollection(ENTRIES.values()); }
    public static OreOverride getEntry(String placedId) { return ENTRIES.get(placedId); }

    public static void stage(OreOverride o) { ENTRIES.put(o.key(), o); }
    public static void stageDelete(String key) { ENTRIES.remove(key); }
    /** Deshace un stage: vuelve a {@code previousOrNull} (null = no habia override). */
    public static void discardStaged(String key, OreOverride previousOrNull) {
        if (previousOrNull != null) ENTRIES.put(key, previousOrNull); else ENTRIES.remove(key);
    }

    static String base(String placedId) {
        if (placedId == null || !placedId.contains(":")) return null;
        String[] p = placedId.split(":", 2);
        if (!com.kve.common.security.KvePathGuard.isSafeResourceId(p[0], p[1])) return null;
        return DIR + "/" + p[0] + "/" + p[1];
    }

    private static Path dataRoot() { return Paths.get("kubejs", "data"); }
    private static Path kveData() { return dataRoot().resolve(NS); }

    /**
     * Archivo -> contenido que este override necesita. Vacio si esta
     * deshabilitado, no cambia nada o su id es inseguro. Sin I/O.
     */
    public static Map<Path, JsonObject> buildFiles(OreOverride o) {
        Map<Path, JsonObject> out = new LinkedHashMap<>();
        if (o == null || !o.enabled || o.isNoop()) return out;
        String base = base(o.placedFeatureId);
        if (base == null) return out;
        Path modifierDir = kveData().resolve("forge").resolve("biome_modifier");

        JsonObject remove = new JsonObject();
        remove.addProperty("type", "forge:remove_features");
        if (o.mode == OreOverride.Mode.REMOVE && !o.biomeSelectors.isEmpty()) {
            remove.add("biomes", biomesField(o.biomeSelectors, base + "_remove", out));
        } else {
            JsonObject any = new JsonObject();
            any.addProperty("type", "forge:any");
            remove.add("biomes", any);
        }
        remove.addProperty("features", o.placedFeatureId);
        remove.addProperty("steps", stepOrDefault(o.step));
        out.put(modifierDir.resolve(base + "_remove.json"), remove);
        if (o.mode == OreOverride.Mode.REMOVE) return out;

        OreOverride.Original orig = o.original();
        JsonObject placed = o.originalPlaced.deepCopy();
        if (o.configChanged()) {
            JsonObject configured = o.configuredJson() == null ? new JsonObject() : o.configuredJson().deepCopy();
            patchConfig(configured, o);
            if (placed.has("feature") && placed.get("feature").isJsonObject()) {
                placed.add("feature", configured);
            } else {
                out.put(kveData().resolve("worldgen").resolve("configured_feature").resolve(base + ".json"), configured);
                placed.addProperty("feature", NS + ":" + base);
            }
        }
        patchPlacement(placed, o, orig);
        out.put(kveData().resolve("worldgen").resolve("placed_feature").resolve(base + ".json"), placed);

        JsonObject add = new JsonObject();
        add.addProperty("type", "forge:add_features");
        List<String> where = o.biomeSelectors.isEmpty() ? o.originalBiomes : o.biomeSelectors;
        if (where.isEmpty()) {
            JsonObject any = new JsonObject();
            any.addProperty("type", "forge:any");
            add.add("biomes", any);
        } else {
            add.add("biomes", biomesField(where, base, out));
        }
        add.addProperty("features", NS + ":" + base);
        add.addProperty("step", stepOrDefault(o.step));
        out.put(modifierDir.resolve(base + "_add.json"), add);
        return out;
    }

    private static String stepOrDefault(String step) {
        return step == null || step.isBlank() ? "underground_ores" : step;
    }

    private static JsonElement biomesField(List<String> selectors, String base, Map<Path, JsonObject> out) {
        List<String> clean = new ArrayList<>(new LinkedHashSet<>(selectors.stream()
                .filter(s -> s != null && !s.isBlank()).map(String::trim).toList()));
        if (clean.size() == 1) return new com.google.gson.JsonPrimitive(clean.get(0));
        boolean anyTag = clean.stream().anyMatch(s -> s.startsWith("#"));
        JsonArray arr = new JsonArray();
        for (String s : clean) arr.add(s);
        if (!anyTag) return arr;
        JsonObject tag = new JsonObject();
        tag.addProperty("replace", false);
        tag.add("values", arr);
        out.put(kveData().resolve("tags").resolve("worldgen").resolve("biome").resolve(base + "_biomes.json"), tag);
        return new com.google.gson.JsonPrimitive("#" + NS + ":" + base + "_biomes");
    }

    private static void patchConfig(JsonObject configured, OreOverride o) {
        if (!configured.has("config") || !configured.get("config").isJsonObject()) return;
        JsonObject config = configured.getAsJsonObject("config");
        if (o.veinSize != null) config.addProperty("size", o.veinSize);
        if (o.discardChance != null) config.addProperty("discard_chance_on_air_exposure", o.discardChance);
        if (config.has("targets") && config.get("targets").isJsonArray()) {
            JsonArray targets = config.getAsJsonArray("targets");
            for (int i = 0; i < targets.size() && i < o.targetBlocks.size(); i++) {
                JsonElement t = targets.get(i);
                String want = o.targetBlocks.get(i);
                if (!t.isJsonObject() || want == null || want.isBlank()) continue;
                if (want.equals(OreOverride.targetBlock(t))) continue;
                JsonObject state = new JsonObject();
                state.addProperty("Name", want);
                t.getAsJsonObject().add("state", state);
            }
        }
    }

    private static void patchPlacement(JsonObject placed, OreOverride o, OreOverride.Original orig) {
        JsonArray placement = OreOverride.placement(placed);
        if (placement == null) return;
        boolean heightChanged = o.heightChanged(orig);
        for (JsonElement el : placement) {
            if (!el.isJsonObject()) continue;
            JsonObject m = el.getAsJsonObject();
            String type = OreOverride.typeOf(m);
            if (type.equals("minecraft:count") && o.count != null && !o.count.equals(orig.count)) {
                m.addProperty("count", o.count);
            } else if (type.equals("minecraft:rarity_filter") && o.rarity != null) {
                m.addProperty("chance", o.rarity);
            } else if (type.equals("minecraft:height_range") && heightChanged && o.minY != null && o.maxY != null
                    && m.has("height") && m.get("height").isJsonObject()) {
                JsonObject h = m.getAsJsonObject("height");
                String newType = o.heightType == null || o.heightType.isBlank() ? OreOverride.UNIFORM : o.heightType;
                if (!newType.equals(orig.heightType)) {
                    JsonElement plateau = h.get("plateau");
                    h = new JsonObject();
                    if (newType.equals(OreOverride.TRAPEZOID) && plateau != null) h.add("plateau", plateau);
                    m.add("height", h);
                }
                h.addProperty("type", newType);
                h.add("min_inclusive", absolute(o.minY));
                h.add("max_inclusive", absolute(o.maxY));
            }
        }
    }

    private static JsonObject absolute(int y) {
        JsonObject a = new JsonObject();
        a.addProperty("absolute", y);
        return a;
    }

    /** Lo que se escribiria para este override, archivo por archivo (diff/vista previa). */
    public static String preview(OreOverride o) {
        if (o == null) return "";
        Map<Path, JsonObject> files = buildFiles(o);
        if (files.isEmpty()) return "// " + o.placedFeatureId + ": sin cambios\n";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Path, JsonObject> f : files.entrySet()) {
            sb.append("// ").append(f.getKey().toString().replace('\\', '/')).append('\n')
                    .append(GSON.toJson(f.getValue())).append('\n');
        }
        return sb.toString();
    }

    public static List<String> commit() {
        List<String> errors = new ArrayList<>();
        Set<String> now = new LinkedHashSet<>();
        for (OreOverride o : ENTRIES.values()) {
            List<String> problems = o.enabled && !o.isNoop() ? o.validate() : List.of();
            if (!problems.isEmpty()) {
                for (String p : problems) errors.add(o.placedFeatureId + ": " + p);
                continue;
            }
            for (Map.Entry<Path, JsonObject> f : buildFiles(o).entrySet()) {
                try {
                    Files.createDirectories(f.getKey().getParent());
                    SafeFiles.writeAtomic(f.getKey(), GSON.toJson(f.getValue()));
                    now.add(f.getKey().toString());
                } catch (IOException ex) {
                    errors.add(o.placedFeatureId + ": no se pudo escribir " + f.getKey() + " (" + ex.getMessage() + ")");
                }
            }
        }
        for (String old : WRITTEN) {
            if (now.contains(old)) continue;
            try {
                com.kve.common.security.KvePathGuard.deleteRecordedFile(kveData(), old);
            } catch (IOException ex) {
                CraftEditorMod.LOGGER.warn("[Shovel JS] No se pudo borrar el override de mineral huerfano {}: {}", old, ex.getMessage());
            }
        }
        WRITTEN.clear();
        WRITTEN.addAll(now);
        persistState();
        return errors;
    }

    private static Path stateFile() { return com.kve.common.kubejs.KveScriptPaths.CONFIG_ROOT.resolve("shovel_js_ore_overrides.json"); }
    private static boolean loaded = false;

    public static void loadIfNeeded() {
        if (!loaded) load();
    }

    public static void forceReload() { load(); }

    private static void load() {
        loaded = true;
        ENTRIES.clear();
        WRITTEN.clear();
        if (!Files.exists(stateFile())) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(stateFile(), StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.has("overrides")) {
                for (JsonElement el : root.getAsJsonArray("overrides")) {
                    try {
                        OreOverride o = fromJson(el.getAsJsonObject());
                        if (!o.placedFeatureId.isBlank()) ENTRIES.put(o.key(), o);
                    } catch (Exception ex) {
                        CraftEditorMod.LOGGER.warn("[Shovel JS] Override de mineral salteado: {}", ex.getMessage());
                    }
                }
            }
            if (root.has("written")) {
                for (JsonElement el : root.getAsJsonArray("written")) WRITTEN.add(el.getAsString());
            }
        } catch (Exception ex) {
            CraftEditorMod.LOGGER.error("[Shovel JS] No se pudo leer {}: {}", stateFile(), ex.getMessage());
        }
    }

    private static void persistState() {
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        for (OreOverride o : ENTRIES.values()) arr.add(toJson(o));
        root.add("overrides", arr);
        JsonArray written = new JsonArray();
        for (String w : WRITTEN) written.add(w);
        root.add("written", written);
        try {
            Files.createDirectories(stateFile().getParent());
            SafeFiles.writeAtomic(stateFile(), GSON.toJson(root));
        } catch (IOException ex) {
            CraftEditorMod.LOGGER.error("[Shovel JS] No se pudo guardar {}: {}", stateFile(), ex.getMessage());
        }
    }

    public static JsonObject toJson(OreOverride o) {
        JsonObject j = new JsonObject();
        j.addProperty("placedFeatureId", o.placedFeatureId);
        j.addProperty("mode", o.mode.name());
        j.addProperty("enabled", o.enabled);
        j.add("originalPlaced", o.originalPlaced);
        if (o.originalConfigured != null) j.add("originalConfigured", o.originalConfigured);
        j.addProperty("step", o.step);
        j.add("originalBiomes", strings(o.originalBiomes));
        j.add("biomeSelectors", strings(o.biomeSelectors));
        j.addProperty("dimMinY", o.dimMinY);
        j.addProperty("dimHeight", o.dimHeight);
        if (o.count != null) j.addProperty("count", o.count);
        if (o.rarity != null) j.addProperty("rarity", o.rarity);
        if (o.minY != null) j.addProperty("minY", o.minY);
        if (o.maxY != null) j.addProperty("maxY", o.maxY);
        if (o.heightType != null) j.addProperty("heightType", o.heightType);
        if (o.veinSize != null) j.addProperty("veinSize", o.veinSize);
        if (o.discardChance != null) j.addProperty("discardChance", o.discardChance);
        j.add("targetBlocks", strings(o.targetBlocks));
        return j;
    }

    public static OreOverride fromJson(JsonObject j) {
        OreOverride o = new OreOverride();
        o.placedFeatureId = j.has("placedFeatureId") ? j.get("placedFeatureId").getAsString() : "";
        try { o.mode = OreOverride.Mode.valueOf(j.get("mode").getAsString()); } catch (Exception ignored) { o.mode = OreOverride.Mode.MODIFY; }
        o.enabled = !j.has("enabled") || j.get("enabled").getAsBoolean();
        o.originalPlaced = j.has("originalPlaced") && j.get("originalPlaced").isJsonObject() ? j.getAsJsonObject("originalPlaced") : new JsonObject();
        o.originalConfigured = j.has("originalConfigured") && j.get("originalConfigured").isJsonObject() ? j.getAsJsonObject("originalConfigured") : null;
        if (j.has("step")) o.step = j.get("step").getAsString();
        o.originalBiomes = readStrings(j, "originalBiomes");
        o.biomeSelectors = readStrings(j, "biomeSelectors");
        if (j.has("dimMinY")) o.dimMinY = j.get("dimMinY").getAsInt();
        if (j.has("dimHeight")) o.dimHeight = j.get("dimHeight").getAsInt();
        o.count = j.has("count") ? j.get("count").getAsInt() : null;
        o.rarity = j.has("rarity") ? j.get("rarity").getAsInt() : null;
        o.minY = j.has("minY") ? j.get("minY").getAsInt() : null;
        o.maxY = j.has("maxY") ? j.get("maxY").getAsInt() : null;
        o.heightType = j.has("heightType") ? j.get("heightType").getAsString() : null;
        o.veinSize = j.has("veinSize") ? j.get("veinSize").getAsInt() : null;
        o.discardChance = j.has("discardChance") ? j.get("discardChance").getAsDouble() : null;
        o.targetBlocks = readStrings(j, "targetBlocks");
        return o;
    }

    private static JsonArray strings(List<String> l) {
        JsonArray a = new JsonArray();
        for (String s : l) a.add(s);
        return a;
    }

    private static List<String> readStrings(JsonObject j, String key) {
        List<String> out = new ArrayList<>();
        if (j.has(key) && j.get(key).isJsonArray()) for (JsonElement e : j.getAsJsonArray(key)) out.add(e.getAsString());
        return out;
    }
}
