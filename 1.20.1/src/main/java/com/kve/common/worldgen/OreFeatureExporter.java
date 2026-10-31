package com.kve.common.worldgen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.kve.CraftEditorMod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * World gen ore features - something KubeJS's own README still lists as
 * not supported (custom world generation / features are a documented gap).
 * <p>
 * DELIBERATELY NOT a Forge Java registry call. Configured/placed features
 * and biome modifiers are all datapack-driven (JSON) in 1.20.1 - the actual
 * Forge registry involved (biome modifier SERIALIZERS) only needs a
 * built-in type ({@code forge:add_features}), so this can be pure JSON
 * written straight into {@code kubejs/data/**}, the same low-priority
 * datapack overlay KubeJS already serves for tags/structures (see
 * {@link com.kve.common.structure.StructureFrequencyExporter}). That
 * sidesteps the registry-event-timing problem entirely: this can be called
 * any time (script load, GUI action, doesn't matter), not just during a
 * narrow FML registry phase.
 * <p>
 * CAVEAT: unlike recipes/tags, biome modifiers are baked when a world's
 * biomes are loaded - a plain {@code /reload} is NOT enough to add a
 * feature to a world already in progress. Existing worlds need the ore to
 * already be reachable via a datapack reload only if the biome was already
 * baked without it; safest guarantee is a fresh world or a full restart -
 * same "requiresRestart" caveat this mod already surfaces elsewhere.
 */
public final class OreFeatureExporter {

    private OreFeatureExporter() {}

    public static class OreFeature {
        /** e.g. "kve:my_ruby_ore" - namespaces the JSON files this writes (3, +1 biome tag when the biome selector mixes tags). */
        public String id;
        /** Block placed by the feature, e.g. "kve:ruby_ore". */
        public String oreBlock;
        /** What the ore is allowed to replace. Defaults to vanilla's stone-ore-replaceable tag. */
        public String replaceableTarget = "minecraft:stone_ore_replaceables";
 /** Blocks per vein (vanilla iron ore is 9, diamond is 8,...). */
        public int veinSize = 8;
        /** Attempts per chunk - NOT a guaranteed count, matches vanilla's "count" placement semantics. */
        public int veinsPerChunk = 4;
        public int minY = -64;
        public int maxY = 64;
        /** Chance [0..1] a vein block exposed to air is discarded instead of placed - vanilla uses this so ores don't pop out into caves/ravines. */
        public double discardChanceOnAirExposure = 0.0;
        public HeightDistribution heightDistribution = HeightDistribution.UNIFORM;

        public enum HeightDistribution { UNIFORM, TRAPEZOID }

        /** 0 = {@link #veinsPerChunk} intentos por chunk; N > 0 = una veta cada N chunks ({@code minecraft:rarity_filter}). */
        public int rarity = 0;
        /** Biome selector for the biome modifier, e.g. "#minecraft:is_overworld" or a single "modid:biome_id". */
        public String biomeFilter = "#minecraft:is_overworld";

        /**
         * WorldGenExporter:
         * multi-biome selector, additive alongside {@link #biomeFilter} so
         * {@code KveScriptAPI#addOreFeature()} (existing public KubeJS
         * scripting surface, single-string biomeFilter) keeps working
         * unchanged. When non-null/non-empty this takes priority and is
         * emitted as a JSON array mixing plain ids and "#tag" entries -
         * [VERIFICAR CONTRA JAR REAL] mismo caveat que el resto de shapes
         * de biome modifier especulativos de este proyecto, no confirmado
         * contra un jar de Forge 1.20.1 real todavía.
         */
        public List<String> biomeFilters;

        public List<OreTarget> extraTargets;

        public List<String> validate() {
            List<String> errors = new ArrayList<>();
            if (id == null || id.isBlank() || !id.contains(":")) {
                errors.add("id must be a namespaced id, e.g. \"shovel_js:my_ore\"");
            } else {
                String[] idParts = id.split(":", 2);
                if (!com.kve.common.security.KvePathGuard.isSafeResourceId(idParts[0], idParts[1])) {
                    errors.add("Id de feature invalido (namespace o ruta contiene un segmento inseguro): " + id);
                }
            }
            if (oreBlock == null || oreBlock.isBlank()) errors.add("oreBlock is required");
            if (veinSize <= 0 || veinSize > WorldGenEntry.OreVein.MAX_VEIN_SIZE)
                errors.add("veinSize must be between 1 and " + WorldGenEntry.OreVein.MAX_VEIN_SIZE);
            if (veinsPerChunk <= 0 || veinsPerChunk > WorldGenEntry.OreVein.MAX_VEINS_PER_CHUNK)
                errors.add("veinsPerChunk must be between 1 and " + WorldGenEntry.OreVein.MAX_VEINS_PER_CHUNK);
            if (minY >= maxY) errors.add("minY must be less than maxY");
            if (!(discardChanceOnAirExposure >= 0 && discardChanceOnAirExposure <= 1)) errors.add("discardChanceOnAirExposure must be between 0 and 1");
            return errors;
        }
    }

    public static class OreTarget {
        public String replaceableTarget = "minecraft:deepslate_ore_replaceables";
        public String oreBlock = "";
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> WRITTEN_IDS = new LinkedHashSet<>();

    private static Path dataDir() { return Paths.get("kubejs", "data"); }

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
        return dataDir().resolve(ns).resolve("tags").resolve("worldgen").resolve("biome").resolve(path + BIOME_TAG_SUFFIX + ".json");
    }

    private static final String BIOME_TAG_SUFFIX = "_biomes";

    private static final class Built {
        JsonObject configuredFeature;
        JsonObject placedFeature;
        JsonObject biomeModifier;
        JsonObject biomeTag;
    }

    private static List<String> effectiveBiomeSelectors(OreFeature f) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (f.biomeFilters != null) {
            for (String b : f.biomeFilters) {
                if (b != null && !b.isBlank()) out.add(b.trim());
            }
        }
        if (out.isEmpty() && f.biomeFilter != null && !f.biomeFilter.isBlank()) out.add(f.biomeFilter.trim());
        return new ArrayList<>(out);
    }

    private static Built buildJsons(OreFeature f, String ns, String path) {
        JsonObject configuredFeature = new JsonObject();
        configuredFeature.addProperty("type", "minecraft:ore");
        JsonObject config = new JsonObject();
        config.addProperty("size", f.veinSize);
        config.addProperty("discard_chance_on_air_exposure", f.discardChanceOnAirExposure);
        JsonArray targets = new JsonArray();
        targets.add(buildTargetEntry(f.replaceableTarget, f.oreBlock));
        if (f.extraTargets != null) {
            for (OreTarget extra : f.extraTargets) {
                if (extra == null || extra.oreBlock == null || extra.oreBlock.isBlank()) continue;
                targets.add(buildTargetEntry(extra.replaceableTarget, extra.oreBlock));
            }
        }
        config.add("targets", targets);
        configuredFeature.add("config", config);

        JsonObject placedFeature = new JsonObject();
        placedFeature.addProperty("feature", f.id);
        JsonArray placement = new JsonArray();
        JsonObject countMod = new JsonObject();
        if (f.rarity > 0) {
            countMod.addProperty("type", "minecraft:rarity_filter");
            countMod.addProperty("chance", f.rarity);
        } else {
            countMod.addProperty("type", "minecraft:count");
            countMod.addProperty("count", f.veinsPerChunk);
        }
        placement.add(countMod);
        JsonObject squareMod = new JsonObject();
        squareMod.addProperty("type", "minecraft:in_square");
        placement.add(squareMod);
        JsonObject heightMod = new JsonObject();
        heightMod.addProperty("type", "minecraft:height_range");
        JsonObject heightRange = new JsonObject();
        heightRange.addProperty("type", f.heightDistribution == OreFeature.HeightDistribution.TRAPEZOID
                ? "minecraft:trapezoid" : "minecraft:uniform");
        JsonObject minInclusive = new JsonObject();
        minInclusive.addProperty("absolute", f.minY);
        heightRange.add("min_inclusive", minInclusive);
        JsonObject maxInclusive = new JsonObject();
        maxInclusive.addProperty("absolute", f.maxY);
        heightRange.add("max_inclusive", maxInclusive);
        heightMod.add("height", heightRange);
        placement.add(heightMod);
        JsonObject biomeMod = new JsonObject();
        biomeMod.addProperty("type", "minecraft:biome");
        placement.add(biomeMod);
        placedFeature.add("placement", placement);

        Built built = new Built();
        JsonObject biomeModifier = new JsonObject();
        biomeModifier.addProperty("type", "forge:add_features");
        BiomeSelector sel = biomeSelector(effectiveBiomeSelectors(f), ns, path + BIOME_TAG_SUFFIX);
        biomeModifier.add("biomes", sel.biomes);
        built.biomeTag = sel.tag;
        biomeModifier.addProperty("features", ns + ":" + path);
        biomeModifier.addProperty("step", "underground_ores");

        built.configuredFeature = configuredFeature;
        built.placedFeature = placedFeature;
        built.biomeModifier = biomeModifier;
        return built;
    }

    /**
     * El placed feature de la veta listo para colocarse en vivo
     * (prueba en el chunk del jugador, sin reiniciar): el configured feature va
     * dentro (todavia no esta en el registro) y sin el filtro de bioma ni la
     * rareza, para que la prueba coloque algo siempre.
     */
    public static JsonObject testPlacedFeature(OreFeature f) {
        String[] idParts = f.id.contains(":") ? f.id.split(":", 2) : new String[]{"shovel_js", f.id};
        Built b = buildJsons(f, idParts[0], idParts[1]);
        return WorldGenTestJson.inline(b.placedFeature, b.configuredFeature);
    }

    /** Valor del campo "biomes" de un biome modifier y, si hace falta, el biome tag generado que lo respalda. */
    public static final class BiomeSelector {
        public final JsonElement biomes;
        /** Null = los biomas van inline. */
        public final JsonObject tag;
        BiomeSelector(JsonElement biomes, JsonObject tag) { this.biomes = biomes; this.tag = tag; }
    }

    /**
     * Arma el HolderSet "biomes" de un biome modifier: un "#tag" o un id
     * sueltos, una lista de ids, o (si hay 2+ selectores y alguno es tag)
     * una referencia a un biome tag generado {@code ns:tagPath} - un "#tag"
     * dentro de la lista no decodifica (ver Javadoc de {@link #buildJsons()}).
     * Compartido por las vetas y los extras de bioma ({@link BiomeExtrasExporter}).
     */
    public static BiomeSelector biomeSelector(List<String> selectors, String ns, String tagPath) {
        List<String> clean = new ArrayList<>();
        for (String b : selectors) if (b != null && !b.isBlank() && !clean.contains(b.trim())) clean.add(b.trim());
        if (clean.size() <= 1) {
            return new BiomeSelector(new JsonPrimitive(clean.isEmpty() ? "#minecraft:is_overworld" : clean.get(0)), null);
        }
        if (clean.stream().noneMatch(b -> b.startsWith("#"))) {
            JsonArray biomes = new JsonArray();
            for (String b : clean) biomes.add(b);
            return new BiomeSelector(biomes, null);
        }
        JsonObject tag = new JsonObject();
        tag.addProperty("replace", false);
        JsonArray values = new JsonArray();
        for (String b : clean) values.add(b);
        tag.add("values", values);
        return new BiomeSelector(new JsonPrimitive("#" + ns + ":" + tagPath), tag);
    }

    private static JsonObject buildTargetEntry(String replaceableTarget, String oreBlock) {
        JsonObject target = new JsonObject();
        JsonObject targetPredicate = new JsonObject();
        targetPredicate.addProperty("predicate_type", "minecraft:tag_match");
        targetPredicate.addProperty("tag", replaceableTarget.replaceFirst("^#", ""));
        target.add("target", targetPredicate);
        JsonObject targetState = new JsonObject();
        targetState.addProperty("Name", oreBlock);
        target.add("state", targetState);
        return target;
    }

    /**
     * Writes the three JSON files this feature needs. Returns validation
     * errors (empty = success, files were written). Overwriting an id
     * that was already added replaces all three files in place.
     */
    public static List<String> addOreFeature(OreFeature f) {
        List<String> errors = f.validate();
        if (!errors.isEmpty()) return errors;

        String[] parts = f.id.split(":", 2);
        String ns = parts[0], path = parts[1];

        Built built = buildJsons(f, ns, path);
        try {
            writeJson(configuredFeaturePath(ns, path), built.configuredFeature);
            writeJson(placedFeaturePath(ns, path), built.placedFeature);
            writeJson(biomeModifierPath(ns, path), built.biomeModifier);
            if (built.biomeTag != null) writeJson(biomeTagPath(ns, path), built.biomeTag);
            else deleteQuietly(biomeTagPath(ns, path));
            WRITTEN_IDS.add(f.id);
        } catch (IOException e) {
            CraftEditorMod.LOGGER.error("[Shovel JS] Failed writing ore feature '{}'", f.id, e);
            return List.of("Could not write files to disk: " + e.getMessage());
        }
        return List.of();
    }

    public static String previewOreFeatureJson(OreFeature f) {
        List<String> errors = f.validate();
        if (!errors.isEmpty()) return "// " + String.join(" ", errors);
        String[] parts = f.id.split(":", 2);
        String ns = parts[0], path = parts[1];
        Built built = buildJsons(f, ns, path);
        StringBuilder sb = new StringBuilder();
        appendFilePreview(sb, configuredFeaturePath(ns, path), built.configuredFeature);
        appendFilePreview(sb, placedFeaturePath(ns, path), built.placedFeature);
        appendFilePreview(sb, biomeModifierPath(ns, path), built.biomeModifier);
        if (built.biomeTag != null) appendFilePreview(sb, biomeTagPath(ns, path), built.biomeTag);
        return sb.toString();
    }

    private static void appendFilePreview(StringBuilder sb, Path file, JsonObject json) {
        sb.append("// ").append(file).append('\n').append(GSON.toJson(json)).append('\n');
    }

    public static void removeOreFeature(String id) {
        if (id == null || !id.contains(":")) return;
        String[] parts = id.split(":", 2);
        String ns = parts[0], path = parts[1];
        if (!com.kve.common.security.KvePathGuard.isSafeResourceId(ns, path)) return;
        deleteQuietly(configuredFeaturePath(ns, path));
        deleteQuietly(placedFeaturePath(ns, path));
        deleteQuietly(biomeModifierPath(ns, path));
        deleteQuietly(biomeTagPath(ns, path));
        WRITTEN_IDS.remove(id);
    }

    /** Ids added since the process started (does not scan disk - callers that need the persisted set should track it themselves, same as other KVE exporters do via CraftEditorState). */
    public static Set<String> getWrittenIds() { return new LinkedHashSet<>(WRITTEN_IDS); }

    private static void writeJson(Path path, JsonObject json) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, GSON.toJson(json), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static void deleteQuietly(Path path) {
        try { Files.deleteIfExists(path); } catch (IOException ignored) {}
    }
}
