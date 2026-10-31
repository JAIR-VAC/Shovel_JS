package com.kve.common.worldgen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kve.CraftEditorMod;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Archivo que escribe el "terreno" ({@link WorldGenEntry.TerrainPreset}) de una regla.
 * <p>
 * Los {@code noise_settings} son por DIMENSION, no por bioma: un preset no puede
 * poner montañas en un bioma suelto sin un editor de density functions (fuera de
 * alcance). Hay dos destinos ({@link WorldGenEntry.PresetTarget}):
 * <ul>
 *   <li><b>Dimension de prueba</b> ({@code LAB}): una dimension nueva
 *       {@code shovel_js:worldgen_preview_<preset>_<regla>} que se visita para ver
 *       el preset; el mundo donde se juega no cambia. FLAT usa el generador plano
 *       de vanilla; HILLY/MOUNTAINOUS/AMPLIFIED los noise_settings vanilla
 *       {@code overworld}/{@code large_biomes}/{@code amplified}; OCEANIC el de
 *       {@code overworld} con el mar mas alto (inline en la dimension).</li>
 *   <li><b>Mundo principal</b>: reemplaza el
 *       {@code minecraft:overworld} de noise_settings con una copia del de vanilla
 *       que corresponde (amplified, large_biomes, o overworld con el mar alto).
 *       El Overworld de un mundo ya creado referencia ese id, asi que los chunks
 *       NUEVOS salen con el terreno nuevo (los ya generados no cambian y puede
 *       quedar un borde). Requiere volver a entrar al mundo: los registros de
 *       worldgen no se recargan con /reload.</li>
 * </ul>
 * La copia sale del pack de datos de vanilla ({@code ServerPacksSource#createVanillaPackSource()}),
 * no del registro en uso: asi no se copia otra vez nuestro propio override ni el
 * de otro mod.
 * <p>
 * Antes OCEANIC solo escribia un biome tag
 * {@code shovel_js:oceanic_favored_<regla>} que nada leia: no cambiaba el mundo.
 */
public final class TerrainPresetExporter {

    private TerrainPresetExporter() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Nivel del mar de OCEANIC (vanilla: 63). Con 80 las llanuras y valles bajos quedan bajo el agua. */
    public static final int OCEANIC_SEA_LEVEL = 80;

    static Function<String, JsonObject> vanillaNoiseSettings = TerrainPresetExporter::readVanillaNoiseSettings;

    private static Path dataDir() { return Paths.get("kubejs", "data"); }
    private static Path dimensionPath(String ns, String path) { return dataDir().resolve(ns).resolve("dimension").resolve(path + ".json"); }
    private static Path biomeModifierPath(String ns, String path) { return dataDir().resolve(ns).resolve("forge").resolve("biome_modifier").resolve(path + ".json"); }
    /** El noise_settings del Overworld que pisa el destino "mundo principal". */
    public static Path mainWorldNoisePath() {
        return dataDir().resolve("minecraft").resolve("worldgen").resolve("noise_settings").resolve("overworld.json");
    }

    /** "shovel_js:worldgen_preview_hilly_my_rule" - id de la dimension de prueba de un preset. */
    public static String labDimensionId(WorldGenEntry e) {
        String entrySlug = e.id.contains(":") ? e.id.substring(e.id.indexOf(':') + 1) : e.id;
        return "shovel_js:worldgen_preview_" + e.terrainPreset.name().toLowerCase() + "_" + entrySlug;
    }

    /** True si la regla escribe en el mundo principal (y no en una dimension de prueba). */
    public static boolean targetsMainWorld(WorldGenEntry e) {
        return e.terrainPreset != null && e.terrainPreset.supportsMainWorld()
                && e.presetTarget == WorldGenEntry.PresetTarget.MAIN_WORLD;
    }

    static String vanillaBase(WorldGenEntry.TerrainPreset p) {
        return switch (p) {
            case MOUNTAINOUS -> "large_biomes";
            case AMPLIFIED -> "amplified";
            default -> "overworld";
        };
    }

    /**
     * Archivo que versiones ANTERIORES escribian para OCEANIC: un biome_modifier
     * de tipo {@code forge:add_biome_tags}, que no existe. Se conserva la ruta
     * solo para borrarlo de instancias que ya lo generaron.
     */
    public static Path legacyOceanicModifierPath(WorldGenEntry e) {
        String entrySlug = e.id.contains(":") ? e.id.substring(e.id.indexOf(':') + 1) : e.id;
        return biomeModifierPath("shovel_js", "oceanic_preview_" + entrySlug);
    }

    /** Borra el biome_modifier invalido de OCEANIC de versiones viejas (si existe). Seguro de llamar siempre. */
    public static void deleteLegacyOceanicModifier(WorldGenEntry e) {
        if (e == null || e.id == null || e.id.isBlank()) return;
        Path p = legacyOceanicModifierPath(e);
        if (p.toString().contains("..")) return;
        try { Files.deleteIfExists(p); } catch (IOException ex) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] No se pudo borrar el modifier OCEANIC viejo {}: {}", p, ex.getMessage());
        }
    }

    private static final class Result {
        Path path;
        JsonObject json;
        String comment;
    }

    private static Result build(WorldGenEntry e, List<String> errors, boolean summaryOnly) {
        if (e.terrainPreset == null) return null;
        if (targetsMainWorld(e)) return buildMainWorld(e, errors, summaryOnly);
        return buildLabDimension(e, errors, summaryOnly);
    }

    static final String GENERATED_MARKER = "shovel_js_generated";

    static Function<String, JsonObject> effectiveNoiseSettings = TerrainPresetExporter::readEffectiveNoiseSettings;

    private static JsonObject noiseSettingsFor(WorldGenEntry.TerrainPreset p, List<String> errors, boolean summaryOnly) {
        String variant = vanillaBase(p);
        boolean swapRouter = p == WorldGenEntry.TerrainPreset.MOUNTAINOUS || p == WorldGenEntry.TerrainPreset.AMPLIFIED;
        JsonObject settings;
        if (summaryOnly) {
            settings = new JsonObject();
            settings.addProperty("copia_de", "minecraft:" + variant);
            settings.addProperty("base", "terreno actual de minecraft:overworld (con los cambios de otros mods)");
            if (swapRouter) settings.addProperty("cambia", "noise_router (forma del terreno) de minecraft:" + variant);
        } else {
            JsonObject base = effectiveNoiseSettings.apply("overworld");
            if (base == null) base = vanillaNoiseSettings.apply("overworld");
            if (base == null) {
                errors.add("No se pudo leer el terreno del Overworld (noise_settings minecraft:overworld).");
                return null;
            }
            settings = base.deepCopy();
            if (swapRouter) {
                JsonObject v = vanillaNoiseSettings.apply(variant);
                if (v == null || !v.has("noise_router")) {
                    errors.add("No se pudo leer el terreno de vanilla (noise_settings minecraft:" + variant + ").");
                    return null;
                }
                settings.add("noise_router", v.get("noise_router").deepCopy());
            }
            settings.addProperty(GENERATED_MARKER, true);
        }
        if (p == WorldGenEntry.TerrainPreset.OCEANIC) settings.addProperty("sea_level", OCEANIC_SEA_LEVEL);
        return settings;
    }

    static JsonObject readEffectiveNoiseSettings(String name) {
        try {
            net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server == null) return null;
            var stack = server.getResourceManager().getResourceStack(
                    new net.minecraft.resources.ResourceLocation("minecraft", "worldgen/noise_settings/" + name + ".json"));
            for (int i = stack.size() - 1; i >= 0; i--) {
                var res = stack.get(i);
                String pack = res.sourcePackId() == null ? "" : res.sourcePackId().toLowerCase(java.util.Locale.ROOT);
                if (pack.contains("kubejs")) continue;
                JsonObject o;
                try (Reader r = res.openAsReader()) {
                    o = JsonParser.parseReader(r).getAsJsonObject();
                }
                if (o.has(GENERATED_MARKER)) continue;
                if (pack.equals("vanilla")) return null;
                CraftEditorMod.LOGGER.info("[Shovel JS] Terreno del Overworld tomado de '{}' (no de vanilla) para el preset", res.sourcePackId());
                return o;
            }
        } catch (Exception ex) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] No se pudo leer el terreno efectivo de minecraft:{}: {}", name, ex.getMessage());
        }
        return null;
    }

    private static Result buildMainWorld(WorldGenEntry e, List<String> errors, boolean summaryOnly) {
        JsonObject settings = noiseSettingsFor(e.terrainPreset, errors, summaryOnly);
        if (settings == null) return null;
        Result r = new Result();
        r.path = mainWorldNoisePath();
        r.json = settings;
        r.comment = e.terrainPreset.name() + " en el mundo principal: reemplaza minecraft:overworld (noise_settings) - solo chunks nuevos, al volver a entrar al mundo";
        return r;
    }

    private static Result buildLabDimension(WorldGenEntry e, List<String> errors, boolean summaryOnly) {
        JsonObject dimension = new JsonObject();
        dimension.addProperty("type", "minecraft:overworld");

        JsonObject generator = new JsonObject();
        if (e.terrainPreset == WorldGenEntry.TerrainPreset.FLAT) {
            generator.addProperty("type", "minecraft:flat");
            JsonObject settings = new JsonObject();
            JsonArray layers = new JsonArray();
            layers.add(flatLayer("minecraft:bedrock", 1));
            layers.add(flatLayer("minecraft:dirt", 2));
            layers.add(flatLayer("minecraft:grass_block", 1));
            settings.add("layers", layers);
            settings.addProperty("biome", "minecraft:plains");
            settings.addProperty("lakes", false);
            settings.addProperty("features", false);
            generator.add("settings", settings);
        } else {
            generator.addProperty("type", "minecraft:noise");
            if (e.terrainPreset == WorldGenEntry.TerrainPreset.OCEANIC) {
                JsonObject inline = noiseSettingsFor(e.terrainPreset, errors, summaryOnly);
                if (inline == null) return null;
                generator.add("settings", inline);
            } else {
                generator.addProperty("settings", "minecraft:" + vanillaBase(e.terrainPreset));
            }
            JsonObject biomeSource = new JsonObject();
            biomeSource.addProperty("type", "minecraft:multi_noise");
            biomeSource.addProperty("preset", "minecraft:overworld");
            generator.add("biome_source", biomeSource);
        }
        dimension.add("generator", generator);

        String id = labDimensionId(e);
        String[] parts = id.split(":", 2);
        Result r = new Result();
        r.path = dimensionPath(parts[0], parts[1]);
        r.json = dimension;
        r.comment = "Dimension de prueba para " + e.terrainPreset.name() + " (id: " + id + ")";
        return r;
    }

    private static JsonObject flatLayer(String block, int height) {
        JsonObject layer = new JsonObject();
        layer.addProperty("block", block);
        layer.addProperty("height", height);
        return layer;
    }

    /**
     * Escribe el archivo del preset y devuelve los errores (vacio = ok). Sin preset
     * no hace nada y no es error.
     */
    public static List<String> applyPreset(WorldGenEntry e) {
        List<String> errors = new ArrayList<>();
        Result r = build(e, errors, false);
        if (r == null) return errors;

        if (r.path.toString().contains("..")) {
            errors.add("Id de preset inválido (segmento '..' no permitido).");
            return errors;
        }
        try {
            Files.createDirectories(r.path.getParent());
            Files.writeString(r.path, GSON.toJson(r.json), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ex) {
            CraftEditorMod.LOGGER.error("[Shovel JS] Failed writing terrain preset for '{}'", e.id, ex);
            errors.add("No se pudo escribir el preset de altura: " + ex.getMessage());
        }
        return errors;
    }

    static JsonObject buildJsonForTests(WorldGenEntry e, List<String> errors) {
        Result r = build(e, errors, false);
        return r == null ? null : r.json;
    }

    /** Vista previa de lo que {@link #applyPreset()} escribiria (con la copia de vanilla resumida). "" sin preset. */
    public static String previewPresetJson(WorldGenEntry e) {
        Result r = build(e, new ArrayList<>(), true);
        if (r == null) return "";
        return "// " + r.path + "\n// " + r.comment + "\n" + GSON.toJson(r.json) + "\n";
    }

    /**
     * Ruta que {@link #applyPreset()} escribiria para {@code e} tal como esta AHORA,
     * sin escribir ni leer vanilla - para borrar el archivo viejo cuando cambia
     * el preset o el destino. null sin preset.
     */
    public static Path resolvedPath(WorldGenEntry e) {
        if (e.terrainPreset == null) return null;
        if (targetsMainWorld(e)) return mainWorldNoisePath();
        String[] parts = labDimensionId(e).split(":", 2);
        return dimensionPath(parts[0], parts[1]);
    }

    static JsonObject readVanillaNoiseSettings(String name) {
        try (net.minecraft.server.packs.VanillaPackResources pack =
                     net.minecraft.server.packs.repository.ServerPacksSource.createVanillaPackSource()) {
            var supplier = pack.getResource(net.minecraft.server.packs.PackType.SERVER_DATA,
                    new net.minecraft.resources.ResourceLocation("minecraft", "worldgen/noise_settings/" + name + ".json"));
            if (supplier == null) return null;
            try (InputStream in = supplier.get(); Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(r).getAsJsonObject();
            }
        } catch (Exception ex) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] No se pudo leer noise_settings vanilla '{}': {}", name, ex.toString());
            return null;
        }
    }
}
