package com.kve.common.worldgen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.kve.CraftEditorMod;
import com.kve.common.util.SafeFiles;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BiomeExtrasExporter {

    private BiomeExtrasExporter() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static Path dataDir() { return Paths.get("kubejs", "data"); }

    /** Archivos que la regla necesita (ruta -> contenido), en orden estable. Vacio si no hay extras o el id no es usable. */
    public static Map<Path, JsonObject> build(WorldGenEntry e) {
        Map<Path, JsonObject> out = new LinkedHashMap<>();
        if (e == null || !e.hasBiomeExtras() || e.id == null || !e.id.contains(":")) return out;
        String[] parts = e.id.split(":", 2);
        String ns = parts[0], path = parts[1];
        if (!com.kve.common.security.KvePathGuard.isSafeResourceId(ns, path)) return out;

        OreFeatureExporter.BiomeSelector sel = OreFeatureExporter.biomeSelector(e.biomeIds, ns, path + "_extra_biomes");
        if (sel.tag != null) out.put(biomeTagPath(ns, path + "_extra_biomes"), sel.tag);

        List<com.google.gson.JsonElement> featureSets = holderSets(e.removedFeatures);
        for (int i = 0; i < featureSets.size(); i++) {
            JsonObject m = new JsonObject();
            m.addProperty("type", "forge:remove_features");
            m.add("biomes", sel.biomes.deepCopy());
            m.add("features", featureSets.get(i));
            out.put(biomeModifierPath(ns, path + "_remove_features" + (i == 0 ? "" : "_" + i)), m);
        }

        List<WorldGenEntry.SpawnRule> adds = new ArrayList<>();
        List<String> removes = new ArrayList<>();
        for (WorldGenEntry.SpawnRule r : e.spawns) {
            if (r.remove) removes.add(r.entityId.trim());
            else adds.add(r);
        }
        if (!adds.isEmpty()) {
            JsonObject m = new JsonObject();
            m.addProperty("type", "forge:add_spawns");
            m.add("biomes", sel.biomes.deepCopy());
            JsonArray spawners = new JsonArray();
            for (WorldGenEntry.SpawnRule r : adds) {
                JsonObject s = new JsonObject();
                s.addProperty("type", r.entityId.trim());
                s.addProperty("weight", r.weight);
                s.addProperty("minCount", r.minCount);
                s.addProperty("maxCount", r.maxCount);
                spawners.add(s);
            }
            m.add("spawners", spawners);
            out.put(biomeModifierPath(ns, path + "_add_spawns"), m);
        }
        List<com.google.gson.JsonElement> spawnSets = holderSets(removes);
        for (int i = 0; i < spawnSets.size(); i++) {
            JsonObject m = new JsonObject();
            m.addProperty("type", "forge:remove_spawns");
            m.add("biomes", sel.biomes.deepCopy());
            m.add("entity_types", spawnSets.get(i));
            out.put(biomeModifierPath(ns, path + "_remove_spawns" + (i == 0 ? "" : "_" + i)), m);
        }

        for (WorldGenEntry.Patch p : e.patches) {
            String pid = patchPath(path, p);
            String featureId = ns + ":" + pid;
            out.put(configuredFeaturePath(ns, pid), patchConfigured(p));
            out.put(placedFeaturePath(ns, pid), patchPlaced(p, featureId));
            JsonObject m = new JsonObject();
            m.addProperty("type", "forge:add_features");
            m.add("biomes", sel.biomes.deepCopy());
            m.addProperty("features", featureId);
            m.addProperty("step", "vegetal_decoration");
            out.put(biomeModifierPath(ns, pid), m);
        }
        return out;
    }

    public static JsonObject testPlacedFeature(WorldGenEntry.Patch p) {
        return WorldGenTestJson.inline(patchPlaced(p, "shovel_js:test_patch"), patchConfigured(p));
    }

    private static String patchPath(String entryPath, WorldGenEntry.Patch p) {
        String pid = p.patchId == null || p.patchId.isBlank() ? "0" : p.patchId.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        return entryPath + "_patch_" + pid;
    }

    private static List<com.google.gson.JsonElement> holderSets(List<String> ids) {
        List<String> plain = new ArrayList<>();
        List<String> tags = new ArrayList<>();
        for (String s : ids) {
            if (s == null || s.isBlank()) continue;
            String t = s.trim();
            List<String> into = t.startsWith("#") ? tags : plain;
            if (!into.contains(t)) into.add(t);
        }
        List<com.google.gson.JsonElement> out = new ArrayList<>();
        if (plain.size() == 1) out.add(new com.google.gson.JsonPrimitive(plain.get(0)));
        else if (plain.size() > 1) {
            JsonArray a = new JsonArray();
            for (String s : plain) a.add(s);
            out.add(a);
        }
        for (String t : tags) out.add(new com.google.gson.JsonPrimitive(t));
        return out;
    }

    private static JsonObject patchConfigured(WorldGenEntry.Patch p) {
        JsonObject state = new JsonObject();
        state.addProperty("Name", p.block.trim());
        JsonObject provider = new JsonObject();
        provider.addProperty("type", "minecraft:simple_state_provider");
        provider.add("state", state);
        JsonObject simpleConfig = new JsonObject();
        simpleConfig.add("to_place", provider);
        JsonObject simple = new JsonObject();
        simple.addProperty("type", "minecraft:simple_block");
        simple.add("config", simpleConfig);

        JsonObject predicate = new JsonObject();
        predicate.addProperty("type", "minecraft:matching_blocks");
        predicate.addProperty("blocks", "minecraft:air");
        JsonObject filter = new JsonObject();
        filter.addProperty("type", "minecraft:block_predicate_filter");
        filter.add("predicate", predicate);
        JsonArray innerPlacement = new JsonArray();
        innerPlacement.add(filter);
        JsonObject inner = new JsonObject();
        inner.add("feature", simple);
        inner.add("placement", innerPlacement);

        JsonObject config = new JsonObject();
        config.addProperty("tries", p.tries);
        config.addProperty("xz_spread", p.xzSpread);
        config.addProperty("y_spread", p.ySpread);
        config.add("feature", inner);
        JsonObject cf = new JsonObject();
        cf.addProperty("type", "minecraft:random_patch");
        cf.add("config", config);
        return cf;
    }

    private static JsonObject patchPlaced(WorldGenEntry.Patch p, String featureId) {
        JsonArray placement = new JsonArray();
        JsonObject rarity = new JsonObject();
        rarity.addProperty("type", "minecraft:rarity_filter");
        rarity.addProperty("chance", Math.max(1, p.rarity));
        placement.add(rarity);
        JsonObject square = new JsonObject();
        square.addProperty("type", "minecraft:in_square");
        placement.add(square);
        JsonObject heightmap = new JsonObject();
        heightmap.addProperty("type", "minecraft:heightmap");
        heightmap.addProperty("heightmap", "MOTION_BLOCKING");
        placement.add(heightmap);
        JsonObject biome = new JsonObject();
        biome.addProperty("type", "minecraft:biome");
        placement.add(biome);
        JsonObject pf = new JsonObject();
        pf.addProperty("feature", featureId);
        pf.add("placement", placement);
        return pf;
    }

    public static List<Path> write(WorldGenEntry e, List<String> errors) {
        List<Path> written = new ArrayList<>();
        if (e == null || !e.hasBiomeExtras()) return written;
        List<String> problems = new ArrayList<>();
        for (String f : e.removedFeatures) {
            String bare = f == null ? "" : f.trim().replaceFirst("^#", "");
            if (!WorldGenEntry.isValidResourceId(bare)) problems.add("feature a quitar invalida: " + f);
        }
        for (WorldGenEntry.SpawnRule r : e.spawns) problems.addAll(r.validate());
        for (WorldGenEntry.Patch p : e.patches) problems.addAll(p.validate());
        if (!problems.isEmpty()) { errors.addAll(problems); return written; }
        for (Map.Entry<Path, JsonObject> f : build(e).entrySet()) {
            try {
                SafeFiles.writeAtomic(f.getKey(), GSON.toJson(f.getValue()));
                written.add(f.getKey());
            } catch (IOException ex) {
                CraftEditorMod.LOGGER.error("[Shovel JS] No se pudo escribir {}", f.getKey(), ex);
                errors.add("no se pudo escribir " + f.getKey() + ": " + ex.getMessage());
            }
        }
        return written;
    }

    /** Vista previa de lo que {@link #write} escribiria (mismo formato que el resto del preview de world gen). */
    public static String preview(WorldGenEntry e) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Path, JsonObject> f : build(e).entrySet()) {
            sb.append("// ").append(f.getKey()).append('\n').append(GSON.toJson(f.getValue())).append('\n');
        }
        return sb.toString();
    }

    private static Path configuredFeaturePath(String ns, String path) {
        return dataDir().resolve(ns).resolve("worldgen").resolve("configured_feature").resolve(path + ".json");
    }

    private static Path placedFeaturePath(String ns, String path) {
        return dataDir().resolve(ns).resolve("worldgen").resolve("placed_feature").resolve(path + ".json");
    }

    private static Path biomeModifierPath(String ns, String path) {
        return dataDir().resolve(ns).resolve("forge").resolve("biome_modifier").resolve(path + ".json");
    }

    private static Path biomeTagPath(String ns, String path) {
        return dataDir().resolve(ns).resolve("tags").resolve("worldgen").resolve("biome").resolve(path + ".json");
    }
}
