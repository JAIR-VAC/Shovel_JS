package com.kve.common.worldgen;

import com.kve.common.util.SafeFiles;
import com.google.gson.*;
import com.kve.CraftEditorMod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class WorldGenExporter {

    private WorldGenExporter() {}

    private static final Map<String, WorldGenEntry> ENTRIES = new LinkedHashMap<>();
    private static final Map<String, Deque<WorldGenEntry>> HISTORY =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Deque<WorldGenEntry>> eldest) {
                    return size() > Math.max(1, com.kve.common.config.KVEConfig.undoHistoryMaxKeys);
                }
            };

    public static Collection<WorldGenEntry> getEntries() { return Collections.unmodifiableCollection(ENTRIES.values()); }
    public static WorldGenEntry getEntry(String key) { return ENTRIES.get(key); }
    public static void clearAll() { ENTRIES.clear(); HISTORY.clear(); }
    public static void loadEntry(WorldGenEntry e) { ENTRIES.put(e.key(), e); }

    private static void pushHistory(String key, WorldGenEntry snapshot) {
        int max = com.kve.common.config.KVEConfig.undoHistorySize;
        if (max <= 0) return;
        Deque<WorldGenEntry> h = HISTORY.computeIfAbsent(key, k -> new ArrayDeque<>());
        h.addFirst(snapshot.copy());
        while (h.size() > max) h.removeLast();
    }

    public static boolean canUndo(String key) {
        Deque<WorldGenEntry> h = HISTORY.get(key);
        return h != null && !h.isEmpty();
    }

    public static WorldGenEntry undo(String key) {
        Deque<WorldGenEntry> h = HISTORY.get(key);
        if (h == null || h.isEmpty()) return null;
        WorldGenEntry previous = h.removeFirst();
        ENTRIES.put(key, previous);
        commitWorldGen();
        return previous;
    }

    public static void stageEntry(WorldGenEntry e) {
        WorldGenEntry previous = ENTRIES.get(e.key());
        if (previous != null) pushHistory(e.key(), previous);
        ENTRIES.put(e.key(), e);
    }

    public static void discardStagedEntry(String key, WorldGenEntry previousOrNull) {
        if (previousOrNull != null) ENTRIES.put(key, previousOrNull);
        else ENTRIES.remove(key);
    }

    public static void stageDelete(String key) {
        ENTRIES.remove(key);
        HISTORY.remove(key);
    }

    public static WorldGenEntry stageUndo(String key) {
        Deque<WorldGenEntry> h = HISTORY.get(key);
        if (h == null || h.isEmpty()) return null;
        WorldGenEntry restored = h.removeFirst();
        ENTRIES.put(key, restored);
        return restored;
    }

    public static void discardStagedUndo(String key, WorldGenEntry restored, WorldGenEntry beforeUndoOrNull) {
        Deque<WorldGenEntry> h = HISTORY.computeIfAbsent(key, k -> new ArrayDeque<>());
        h.addFirst(restored);
        if (beforeUndoOrNull != null) ENTRIES.put(key, beforeUndoOrNull);
        else ENTRIES.remove(key);
    }

    public static List<String> validateEntry(WorldGenEntry e) {
        if (e == null) return List.of("Regla de world gen nula.");
        List<String> errors = new ArrayList<>(e.validate());
        if (e.enabled && affectsMainWorld(e)) {
            String other = mainWorldPresetOwner(e.key());
            if (other != null) errors.add("La regla \"" + other + "\" ya cambia el terreno del mundo principal; solo puede haber una.");
        }
        return errors;
    }

    /** True si la regla cambia el terreno del Overworld de verdad (no en una dimension de prueba). */
    public static boolean affectsMainWorld(WorldGenEntry e) {
        return e.terrainPreset != null && e.terrainPreset.supportsMainWorld()
                && e.presetTarget == WorldGenEntry.PresetTarget.MAIN_WORLD;
    }

    /**
     * Id de otra regla habilitada (distinta de {@code exceptKey}) que
     * ya cambia el terreno del mundo principal, o null. Es un solo archivo
     * (el noise_settings del Overworld): dos reglas se pisarian.
     */
    public static String mainWorldPresetOwner(String exceptKey) {
        for (WorldGenEntry o : ENTRIES.values()) {
            if (o.enabled && affectsMainWorld(o) && !o.key().equals(exceptKey)) return o.id;
        }
        return null;
    }

    private static final Map<String, WrittenState> WRITTEN = new LinkedHashMap<>();

    private static final Map<String, int[]> LAST_COMMIT_COUNTS = new LinkedHashMap<>();

    public static Map<String, int[]> lastCommitSummary() {
        return Collections.unmodifiableMap(LAST_COMMIT_COUNTS);
    }

    private static final class WrittenState {
        List<String> veinFeatureIds = new ArrayList<>();
        String presetPath;
        List<String> extraPaths = new ArrayList<>();
    }

    public static List<String> commitWorldGen() {
        List<String> allErrors = new ArrayList<>();
        Map<String, WrittenState> newWritten = new LinkedHashMap<>();
        LAST_COMMIT_COUNTS.clear();
        String mainWorldOwner = null;
        boolean needsEndTag = false;

        for (WorldGenEntry e : ENTRIES.values()) {
            if (!e.enabled) continue;
            WrittenState ws = new WrittenState();
            for (WorldGenEntry.OreVein vein : e.oreVeins) {
                if (WorldGenEntry.END_ORE_REPLACEABLES.equals(vein.replaceableTarget)) needsEndTag = true;
            }
            if (affectsMainWorld(e)) {
                if (mainWorldOwner != null) {
                    allErrors.add(e.id + " / altura: \"" + mainWorldOwner + "\" ya cambia el terreno del mundo principal; esta regla no se aplico al mundo.");
                    WorldGenEntry skipped = e.copy();
                    skipped.terrainPreset = null;
                    commitOne(skipped, ws, allErrors);
                    newWritten.put(e.key(), ws);
                    continue;
                }
                mainWorldOwner = e.id;
            }
            commitOne(e, ws, allErrors);
            newWritten.put(e.key(), ws);
        }
        if (needsEndTag) writeEndOreTag(allErrors);

        finishCommit(newWritten);
        return allErrors;
    }

    private static void writeEndOreTag(List<String> errors) {
        String[] id = WorldGenEntry.END_ORE_REPLACEABLES.split(":", 2);
        Path p = Paths.get("kubejs", "data", id[0], "tags", BLOCK_TAG_DIR, id[1] + ".json");
        JsonObject tag = new JsonObject();
        tag.addProperty("replace", false);
        JsonArray values = new JsonArray();
        values.add("minecraft:end_stone");
        tag.add("values", values);
        try {
            Files.createDirectories(p.getParent());
            Files.writeString(p, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(tag), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            errors.add("No se pudo escribir el tag de end stone: " + ex.getMessage());
        }
    }

    static final String BLOCK_TAG_DIR = "block";

    private static void commitOne(WorldGenEntry e, WrittenState ws, List<String> allErrors) {
        {
            int veinsWritten = 0;
            for (WorldGenEntry.OreVein vein : e.oreVeins) {
                if (vein.veinId == null || vein.veinId.isBlank()) vein.veinId = java.util.UUID.randomUUID().toString();
                OreFeatureExporter.OreFeature f = toOreFeature(e, vein);
                List<String> errs = OreFeatureExporter.addOreFeature(f);
                if (!errs.isEmpty()) {
                    for (String err : errs) allErrors.add(e.id + " / vein " + vein.veinId + ": " + err);
                } else {
                    ws.veinFeatureIds.add(f.id);
                    veinsWritten++;
                }
            }
            LAST_COMMIT_COUNTS.put(e.key(), new int[]{veinsWritten, e.oreVeins.size()});
            List<String> presetErrors = TerrainPresetExporter.applyPreset(e);
            for (String err : presetErrors) allErrors.add(e.id + " / altura: " + err);
            if (presetErrors.isEmpty()) {
                Path p = TerrainPresetExporter.resolvedPath(e);
                ws.presetPath = p == null ? null : p.toString();
            }
            List<String> extraErrors = new ArrayList<>();
            for (Path written : BiomeExtrasExporter.write(e, extraErrors)) ws.extraPaths.add(written.toString());
            for (String err : extraErrors) allErrors.add(e.id + " / extras: " + err);
        }
    }

    private static void finishCommit(Map<String, WrittenState> newWritten) {
        for (WorldGenEntry e : ENTRIES.values()) TerrainPresetExporter.deleteLegacyOceanicModifier(e);

        cleanupOrphans(newWritten);
        WRITTEN.clear();
        WRITTEN.putAll(newWritten);
        persistState();
    }

    private static void cleanupOrphans(Map<String, WrittenState> newWritten) {
        for (Map.Entry<String, WrittenState> old : WRITTEN.entrySet()) {
            WrittenState oldWs = old.getValue();
            WrittenState nowWs = newWritten.get(old.getKey());
            Set<String> stillWanted = nowWs == null ? Set.of() : new LinkedHashSet<>(nowWs.veinFeatureIds);
            for (String oldFeatureId : oldWs.veinFeatureIds) {
                if (!stillWanted.contains(oldFeatureId)) OreFeatureExporter.removeOreFeature(oldFeatureId);
            }
            Set<String> extrasWanted = nowWs == null ? Set.of() : new LinkedHashSet<>(nowWs.extraPaths);
            for (String oldPath : oldWs.extraPaths) {
                if (extrasWanted.contains(oldPath)) continue;
                try {
                    com.kve.common.security.KvePathGuard.deleteRecordedFile(Paths.get("kubejs", "data"), oldPath);
                } catch (IOException ex) {
                    CraftEditorMod.LOGGER.warn("[Shovel JS] No se pudo borrar el archivo de world gen huerfano {}: {}", oldPath, ex.getMessage());
                }
            }
            String nowPresetPath = nowWs == null ? null : nowWs.presetPath;
            if (oldWs.presetPath != null && !oldWs.presetPath.equals(nowPresetPath)) {
                try {
                    if (!com.kve.common.security.KvePathGuard.deleteRecordedFile(Paths.get("kubejs", "data"), oldWs.presetPath)) {
                        CraftEditorMod.LOGGER.debug("[Shovel JS] preset de altura huérfano rechazado o ya inexistente: '{}'", oldWs.presetPath);
                    }
                } catch (IOException ex) {
                    CraftEditorMod.LOGGER.warn("[Shovel JS] No se pudo borrar el preset de altura huérfano {}: {}", oldWs.presetPath, ex.getMessage());
                }
            }
        }
    }

    /** Algo de una regla que la prueba en vivo puede colocar: su nombre para mostrar y el placed feature completo. */
    public record TestFeature(String label, JsonObject placed) {}

    public static List<TestFeature> testFeatures(WorldGenEntry e) {
        List<TestFeature> out = new ArrayList<>();
        for (WorldGenEntry.OreVein vein : e.oreVeins) {
            if (vein.oreBlock == null || vein.oreBlock.isBlank()) continue;
            WorldGenEntry.OreVein v = vein.copy();
            if (v.veinId == null || v.veinId.isBlank()) v.veinId = "test";
            out.add(new TestFeature(v.oreBlock, OreFeatureExporter.testPlacedFeature(toOreFeature(e, v))));
        }
        for (WorldGenEntry.Patch p : e.patches) {
            if (p.block == null || p.block.isBlank()) continue;
            out.add(new TestFeature(p.block, BiomeExtrasExporter.testPlacedFeature(p)));
        }
        return out;
    }

    private static String veinFeatureId(WorldGenEntry e, WorldGenEntry.OreVein vein) {
        return e.id + "_vein_" + vein.veinId;
    }

    private static OreFeatureExporter.OreFeature toOreFeature(WorldGenEntry e, WorldGenEntry.OreVein vein) {
        OreFeatureExporter.OreFeature f = new OreFeatureExporter.OreFeature();
        f.id = veinFeatureId(e, vein);
        f.oreBlock = vein.oreBlock;
        f.replaceableTarget = vein.replaceableTarget;
        f.veinSize = vein.veinSize;
        f.veinsPerChunk = vein.veinsPerChunk;
        f.minY = vein.minY;
        f.maxY = vein.maxY;
        f.discardChanceOnAirExposure = vein.discardChanceOnAirExposure;
        f.rarity = vein.rarity;
        f.heightDistribution = OreFeatureExporter.OreFeature.HeightDistribution.valueOf(vein.heightDistribution.name());
        f.biomeFilters = new ArrayList<>(e.biomeIds);
        if (vein.deepslateOreBlock != null && !vein.deepslateOreBlock.isBlank()) {
            OreFeatureExporter.OreTarget extra = new OreFeatureExporter.OreTarget();
            extra.oreBlock = vein.deepslateOreBlock;
            extra.replaceableTarget = vein.deepslateReplaceableTarget;
            f.extraTargets = List.of(extra);
        }
        return f;
    }

    private static Path configDir() { return com.kve.common.kubejs.KveScriptPaths.CONFIG_ROOT; }
    private static Path stateFile() { return configDir().resolve("shovel_js_worldgen_state.json"); }

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

    public static void forceReload() {
        loaded = false;
        load();
    }

    private static void load() {
        loaded = true;
        ENTRIES.clear();
        HISTORY.clear();
        WRITTEN.clear();
        if (!Files.exists(stateFile())) {
            lastMtime = 0;
            return;
        }
        try {
            lastMtime = Files.getLastModifiedTime(stateFile()).toMillis();
            String json = Files.readString(stateFile(), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("worldgen")) {
                for (JsonElement el : root.getAsJsonArray("worldgen")) {
                    try {
                        WorldGenEntry e = entryFromJson(el.getAsJsonObject());
                        if (e.id != null && !e.id.isBlank()) loadEntry(e);
                    } catch (Exception ex) {
                        CraftEditorMod.LOGGER.warn("[Shovel JS] Skipped worldgen entry: {}", ex.getMessage());
                    }
                }
            }
            if (root.has("writtenState")) {
                for (JsonElement el : root.getAsJsonArray("writtenState")) {
                    JsonObject wo = el.getAsJsonObject();
                    String key = str(wo, "key", "");
                    if (key.isBlank()) continue;
                    WrittenState ws = new WrittenState();
                    if (wo.has("veinFeatureIds")) {
                        for (JsonElement v : wo.getAsJsonArray("veinFeatureIds")) ws.veinFeatureIds.add(v.getAsString());
                    }
                    if (wo.has("presetPath") && !wo.get("presetPath").isJsonNull()) ws.presetPath = wo.get("presetPath").getAsString();
                    if (wo.has("extraPaths")) {
                        for (JsonElement v : wo.getAsJsonArray("extraPaths")) ws.extraPaths.add(v.getAsString());
                    }
                    WRITTEN.put(key, ws);
                }
            }
            CraftEditorMod.LOGGER.info("[Shovel JS] World gen state loaded from {}", stateFile());
        } catch (Exception ex) {
            CraftEditorMod.LOGGER.error("[Shovel JS] Failed to load worldgen state: {}", ex.getMessage());
        }
    }

    private static void persistState() {
        try {
            Files.createDirectories(configDir());
            JsonObject root = new JsonObject();
            JsonArray arr = new JsonArray();
            for (WorldGenEntry e : ENTRIES.values()) arr.add(entryToJson(e));
            root.add("worldgen", arr);

            JsonArray writtenArr = new JsonArray();
            for (Map.Entry<String, WrittenState> we : WRITTEN.entrySet()) {
                JsonObject wo = new JsonObject();
                wo.addProperty("key", we.getKey());
                JsonArray ids = new JsonArray();
                for (String id : we.getValue().veinFeatureIds) ids.add(id);
                wo.add("veinFeatureIds", ids);
                wo.addProperty("presetPath", we.getValue().presetPath);
                if (!we.getValue().extraPaths.isEmpty()) {
                    JsonArray extras = new JsonArray();
                    for (String x : we.getValue().extraPaths) extras.add(x);
                    wo.add("extraPaths", extras);
                }
                writtenArr.add(wo);
            }
            root.add("writtenState", writtenArr);

            String json = new GsonBuilder().setPrettyPrinting().create().toJson(root);
            SafeFiles.writeAtomic(stateFile(), json);
            try { lastMtime = Files.getLastModifiedTime(stateFile()).toMillis(); } catch (IOException ex) {
                CraftEditorMod.LOGGER.debug("[Shovel JS] no se pudo releer el mtime de {} tras guardar: {}", stateFile(), ex.toString());
            }
        } catch (IOException ex) {
            CraftEditorMod.LOGGER.error("[Shovel JS] World gen state save failed: {}", ex.getMessage());
        }
    }

    public static JsonObject entryToJson(WorldGenEntry e) {
        JsonObject o = new JsonObject();
        o.addProperty("id", e.id);
        o.addProperty("displayName", e.displayName);
        o.addProperty("enabled", e.enabled);
        o.addProperty("terrainPreset", e.terrainPreset == null ? null : e.terrainPreset.name());
        if (e.presetTarget == WorldGenEntry.PresetTarget.MAIN_WORLD) o.addProperty("presetTarget", e.presetTarget.name());

        JsonArray biomes = new JsonArray();
        for (String b : e.biomeIds) biomes.add(b);
        o.add("biomeIds", biomes);

        JsonArray veins = new JsonArray();
        for (WorldGenEntry.OreVein v : e.oreVeins) {
            JsonObject vo = new JsonObject();
            vo.addProperty("veinId", v.veinId);
            vo.addProperty("oreBlock", v.oreBlock);
            vo.addProperty("replaceableTarget", v.replaceableTarget);
            vo.addProperty("veinSize", v.veinSize);
            vo.addProperty("veinsPerChunk", v.veinsPerChunk);
            vo.addProperty("minY", v.minY);
            vo.addProperty("maxY", v.maxY);
            vo.addProperty("discardChanceOnAirExposure", v.discardChanceOnAirExposure);
            vo.addProperty("heightDistribution", v.heightDistribution.name());
            vo.addProperty("deepslateOreBlock", v.deepslateOreBlock);
            vo.addProperty("deepslateReplaceableTarget", v.deepslateReplaceableTarget);
            if (v.rarity > 0) vo.addProperty("rarity", v.rarity);
            veins.add(vo);
        }
        o.add("oreVeins", veins);
        if (!e.removedFeatures.isEmpty()) {
            JsonArray rf = new JsonArray();
            for (String f : e.removedFeatures) rf.add(f);
            o.add("removedFeatures", rf);
        }
        if (!e.spawns.isEmpty()) {
            JsonArray sp = new JsonArray();
            for (WorldGenEntry.SpawnRule r : e.spawns) {
                JsonObject so = new JsonObject();
                so.addProperty("entityId", r.entityId);
                so.addProperty("remove", r.remove);
                so.addProperty("weight", r.weight);
                so.addProperty("minCount", r.minCount);
                so.addProperty("maxCount", r.maxCount);
                sp.add(so);
            }
            o.add("spawns", sp);
        }
        if (!e.patches.isEmpty()) {
            JsonArray pa = new JsonArray();
            for (WorldGenEntry.Patch p : e.patches) {
                JsonObject po = new JsonObject();
                po.addProperty("patchId", p.patchId);
                po.addProperty("block", p.block);
                po.addProperty("tries", p.tries);
                po.addProperty("xzSpread", p.xzSpread);
                po.addProperty("ySpread", p.ySpread);
                po.addProperty("rarity", p.rarity);
                pa.add(po);
            }
            o.add("patches", pa);
        }
        return o;
    }

    public static WorldGenEntry entryFromJson(JsonObject o) {
        WorldGenEntry e = new WorldGenEntry();
        e.id = str(o, "id", "");
        e.displayName = str(o, "displayName", "");
        e.enabled = !o.has("enabled") || o.get("enabled").getAsBoolean();
        if (o.has("terrainPreset") && !o.get("terrainPreset").isJsonNull()) {
            try { e.terrainPreset = WorldGenEntry.TerrainPreset.valueOf(o.get("terrainPreset").getAsString()); }
            catch (Exception ignored) { e.terrainPreset = null; }
        }
        if (o.has("presetTarget") && !o.get("presetTarget").isJsonNull()) {
            try { e.presetTarget = WorldGenEntry.PresetTarget.valueOf(o.get("presetTarget").getAsString()); }
            catch (Exception ignored) { e.presetTarget = WorldGenEntry.PresetTarget.LAB; }
        }
        if (o.has("biomeIds")) {
            for (JsonElement el : o.getAsJsonArray("biomeIds")) e.biomeIds.add(el.getAsString());
        }
        if (o.has("oreVeins")) {
            for (JsonElement el : o.getAsJsonArray("oreVeins")) {
                JsonObject vo = el.getAsJsonObject();
                WorldGenEntry.OreVein v = new WorldGenEntry.OreVein();
                String loadedVeinId = str(vo, "veinId", "");
                v.veinId = loadedVeinId.isBlank() ? java.util.UUID.randomUUID().toString() : loadedVeinId;
                v.oreBlock = str(vo, "oreBlock", "");
                v.replaceableTarget = str(vo, "replaceableTarget", "minecraft:stone_ore_replaceables");
                v.veinSize = vo.has("veinSize") ? vo.get("veinSize").getAsInt() : 8;
                v.veinsPerChunk = vo.has("veinsPerChunk") ? vo.get("veinsPerChunk").getAsInt() : 4;
                v.minY = vo.has("minY") ? vo.get("minY").getAsInt() : -64;
                v.maxY = vo.has("maxY") ? vo.get("maxY").getAsInt() : 64;
                v.discardChanceOnAirExposure = vo.has("discardChanceOnAirExposure") ? vo.get("discardChanceOnAirExposure").getAsDouble() : 0.0;
                try {
                    v.heightDistribution = WorldGenEntry.OreVein.HeightDistribution.valueOf(str(vo, "heightDistribution", "UNIFORM"));
                } catch (Exception ignored) {
                    v.heightDistribution = WorldGenEntry.OreVein.HeightDistribution.UNIFORM;
                }
                v.deepslateOreBlock = str(vo, "deepslateOreBlock", "");
                v.deepslateReplaceableTarget = str(vo, "deepslateReplaceableTarget", "minecraft:deepslate_ore_replaceables");
                v.rarity = vo.has("rarity") ? Math.max(0, vo.get("rarity").getAsInt()) : 0;
                e.oreVeins.add(v);
            }
        }
        if (o.has("removedFeatures")) {
            for (JsonElement el : o.getAsJsonArray("removedFeatures")) e.removedFeatures.add(el.getAsString());
        }
        if (o.has("spawns")) {
            for (JsonElement el : o.getAsJsonArray("spawns")) {
                JsonObject so = el.getAsJsonObject();
                WorldGenEntry.SpawnRule r = new WorldGenEntry.SpawnRule();
                r.entityId = str(so, "entityId", "");
                r.remove = so.has("remove") && so.get("remove").getAsBoolean();
                r.weight = so.has("weight") ? so.get("weight").getAsInt() : 10;
                r.minCount = so.has("minCount") ? so.get("minCount").getAsInt() : 1;
                r.maxCount = so.has("maxCount") ? so.get("maxCount").getAsInt() : 4;
                e.spawns.add(r);
            }
        }
        if (o.has("patches")) {
            for (JsonElement el : o.getAsJsonArray("patches")) {
                JsonObject po = el.getAsJsonObject();
                WorldGenEntry.Patch p = new WorldGenEntry.Patch();
                String pid = str(po, "patchId", "");
                p.patchId = pid.isBlank() ? java.util.UUID.randomUUID().toString() : pid;
                p.block = str(po, "block", "");
                p.tries = po.has("tries") ? po.get("tries").getAsInt() : 32;
                p.xzSpread = po.has("xzSpread") ? po.get("xzSpread").getAsInt() : 7;
                p.ySpread = po.has("ySpread") ? po.get("ySpread").getAsInt() : 3;
                p.rarity = po.has("rarity") ? po.get("rarity").getAsInt() : 8;
                e.patches.add(p);
            }
        }
        return e;
    }

    private static String str(JsonObject o, String key, String def) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : def;
    }

    /**
     * Read-only preview of exactly what committing this entry would write -
     * same role as {@code EventExporter#previewEventJS}, but since this
     * domain has no single "one script" to preview,
     * this returns the file paths + contents per vein instead (configured_feature,
     * placed_feature, biome_modifier, and the generated biome tag when the
     * biome selector mixes tags - see {@link OreFeatureExporter#previewOreFeatureJson()}).
     */
    public static String previewWorldGenJson(WorldGenEntry e) {
        if (e == null) return "// (sin datos de world gen)";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < e.oreVeins.size(); i++) {
            WorldGenEntry.OreVein vein = e.oreVeins.get(i);
            sb.append("// Vein #").append(i + 1).append(" (").append(vein.oreBlock).append(")\n");
            sb.append("// -> ").append(veinFeatureId(e, vein)).append(" en biomas: ").append(e.biomeIds).append("\n");
            sb.append(OreFeatureExporter.previewOreFeatureJson(toOreFeature(e, vein)));
            sb.append("\n");
        }
        if (e.oreVeins.isEmpty()) sb.append("// (sin vetas todavía)\n");
        sb.append(BiomeExtrasExporter.preview(e));
        String presetPreview = TerrainPresetExporter.previewPresetJson(e);
        if (!presetPreview.isEmpty()) sb.append(presetPreview);
        return sb.toString();
    }
}
