package com.kve.common.worldgen;

import java.util.ArrayList;
import java.util.List;

public class WorldGenEntry {

    /** e.g. "kve:my_worldgen_rule" - stable key for maps/history/UI, same role as EventEntry#key(). */
    public String id = "";
    public String displayName = "";
    public boolean enabled = true;

    /**
     * Multi-selección de biomas (o tags "#minecraft:is_overworld") - viene
     * de {@code BiomePickerScreen} en modo multi-select. Vacío = sin biomas
     * elegidos todavía (bloquea commit, ver {@link #validate()}).
     */
    public List<String> biomeIds = new ArrayList<>();

    /** 0..N vetas para este set de biomas. */
    public List<OreVein> oreVeins = new ArrayList<>();

    public TerrainPreset terrainPreset = null;

    /**
     * Donde se aplica {@link #terrainPreset}. {@link PresetTarget#LAB}
     * (lo de siempre) crea una dimension aparte para probarlo;
     * {@link PresetTarget#MAIN_WORLD} cambia el terreno del Overworld en los
     * chunks nuevos (solo los presets que {@link TerrainPreset#supportsMainWorld()}).
     */
    public PresetTarget presetTarget = PresetTarget.LAB;

    public List<String> removedFeatures = new ArrayList<>();

    public List<SpawnRule> spawns = new ArrayList<>();

    public List<Patch> patches = new ArrayList<>();

    public static boolean isValidResourceId(String s) {
        return s != null && s.matches("[a-z0-9_.-]+:[a-z0-9_./-]+");
    }

    public String key() { return id; }

    public WorldGenEntry copy() {
        WorldGenEntry c = new WorldGenEntry();
        c.id = id;
        c.displayName = displayName;
        c.enabled = enabled;
        c.biomeIds = new ArrayList<>(biomeIds);
        for (OreVein v : oreVeins) c.oreVeins.add(v.copy());
        c.terrainPreset = terrainPreset;
        c.presetTarget = presetTarget;
        c.removedFeatures = new ArrayList<>(removedFeatures);
        for (SpawnRule r : spawns) c.spawns.add(r.copy());
        for (Patch p : patches) c.patches.add(p.copy());
        return c;
    }

    public boolean hasBiomeExtras() {
        return !removedFeatures.isEmpty() || !spawns.isEmpty() || !patches.isEmpty();
    }

    /**
 * mismo criterio de validación humana que
     * {@code EventEntry#validate()} - mensajes en español, uno por problema,
     * sin abortar en el primero para que la UI pueda mostrarlos todos juntos
     * (mismo patrón que {@code errorBadge}/helpIcon ya usan).
     */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (id == null || id.isBlank() || !id.contains(":")) {
            errors.add("El id debe tener namespace, ej. \"shovel_js:mi_regla\".");
        } else if (!isValidResourceId(id)) {
            errors.add("El id solo admite minúsculas, números y _ . - (y / en la ruta), sin espacios ni mayúsculas.");
        }
        if (biomeIds == null || biomeIds.isEmpty()) {
            errors.add("Elegí al menos un bioma (o tag de bioma).");
        }
        if (terrainPreset != null && presetTarget == PresetTarget.MAIN_WORLD && !terrainPreset.supportsMainWorld()) {
            errors.add("El terreno \"" + terrainPreset.name().toLowerCase() + "\" solo se puede probar en una dimensión aparte, no en el mundo principal.");
        }
        for (int i = 0; i < oreVeins.size(); i++) {
            for (String e : oreVeins.get(i).validate()) {
                errors.add("Veta #" + (i + 1) + ": " + e);
            }
        }
        for (String f : removedFeatures) {
            String bare = f == null ? "" : f.trim().replaceFirst("^#", "");
            if (!isValidResourceId(bare)) errors.add("Feature a quitar invalida: \"" + f + "\" (usa un id como minecraft:ore_diamond o un #tag).");
        }
        for (int i = 0; i < spawns.size(); i++) {
            for (String e : spawns.get(i).validate()) errors.add("Spawn #" + (i + 1) + ": " + e);
        }
        for (int i = 0; i < patches.size(); i++) {
            for (String e : patches.get(i).validate()) errors.add("Parche #" + (i + 1) + ": " + e);
        }
        return errors;
    }

    /**
     * Un mob que aparece (o deja de aparecer) de forma natural en los biomas
     * de la regla. La categoria (monster/creature/...) la decide el propio
     * tipo de entidad, igual que vanilla.
     */
    public static class SpawnRule {
        public String entityId = "";
        /** true = quitar los spawns de esta entidad (o #tag de entidades) en vez de agregarlos. */
        public boolean remove = false;
        public int weight = 10;
        public int minCount = 1;
        public int maxCount = 4;

        public SpawnRule copy() {
            SpawnRule c = new SpawnRule();
            c.entityId = entityId;
            c.remove = remove;
            c.weight = weight;
            c.minCount = minCount;
            c.maxCount = maxCount;
            return c;
        }

        public List<String> validate() {
            List<String> errors = new ArrayList<>();
            String bare = entityId == null ? "" : entityId.trim().replaceFirst("^#", "");
            if (!isValidResourceId(bare)) errors.add("id de entidad invalido.");
            else if (!remove && entityId.trim().startsWith("#")) errors.add("para agregar spawns hace falta una entidad, no un #tag.");
            if (!remove) {
                if (weight <= 0) errors.add("el peso debe ser mayor a 0.");
                if (minCount <= 0) errors.add("la cantidad minima debe ser mayor a 0.");
                if (maxCount < minCount) errors.add("la cantidad maxima no puede ser menor que la minima.");
            }
            return errors;
        }
    }

    public static class Patch {
        /** Id estable (mismo criterio que {@link OreVein#veinId}): el nombre de los archivos no depende del orden. */
        public String patchId = "";
        public String block = "";
        public int tries = 32;
        public int xzSpread = 7;
        public int ySpread = 3;
        public int rarity = 8;

        public static final int MAX_TRIES = 256;
        public static final int MAX_XZ_SPREAD = 16;
        public static final int MAX_Y_SPREAD = 16;

        public Patch copy() {
            Patch c = new Patch();
            c.patchId = patchId;
            c.block = block;
            c.tries = tries;
            c.xzSpread = xzSpread;
            c.ySpread = ySpread;
            c.rarity = rarity;
            return c;
        }

        public List<String> validate() {
            List<String> errors = new ArrayList<>();
            if (!isValidResourceId(block == null ? "" : block.trim())) errors.add("falta el bloque (id como minecraft:poppy).");
            if (tries <= 0 || tries > MAX_TRIES) errors.add("los intentos deben estar entre 1 y " + MAX_TRIES + ".");
            if (xzSpread < 0 || xzSpread > MAX_XZ_SPREAD) errors.add("la dispersion horizontal debe estar entre 0 y " + MAX_XZ_SPREAD + ".");
            if (ySpread < 0 || ySpread > MAX_Y_SPREAD) errors.add("la dispersion vertical debe estar entre 0 y " + MAX_Y_SPREAD + ".");
            if (rarity <= 0) errors.add("la rareza debe ser 1 o mas (un parche cada N chunks).");
            return errors;
        }
    }

    /**
     * Una veta de mineral - mismos campos que
     * {@code OreFeatureExporter.OreFeature} MENOS {@code biomeFilter}/{@code id},
     * que ahora viven en {@link WorldGenEntry}.
     */
    public static class OreVein {
        public String veinId = "";
        public String oreBlock = "";
        public String replaceableTarget = "minecraft:stone_ore_replaceables";
        public int veinSize = 8;
        public int veinsPerChunk = 4;
        public int minY = -64;
        public int maxY = 64;
        public double discardChanceOnAirExposure = 0.0;
        public HeightDistribution heightDistribution = HeightDistribution.UNIFORM;

        public enum HeightDistribution { UNIFORM, TRAPEZOID }

        /**
         * 0 = {@link #veinsPerChunk} intentos por chunk (como hasta
         * ahora); N > 0 = una veta cada N chunks ({@code rarity_filter}), como
         * los minerales raros vanilla (diamante grande, esmeralda...).
         */
        public int rarity = 0;

        public String deepslateOreBlock = "";
        public String deepslateReplaceableTarget = "minecraft:deepslate_ore_replaceables";

        /** OreConfiguration#size en vanilla es {@code Codec.intRange(0, 64)} - por encima el configured_feature no decodifica y el biome modifier queda apuntando a un feature inexistente. */
        public static final int MAX_VEIN_SIZE = 64;
        /** CountPlacement en vanilla es {@code IntProvider.codec(0, 256)} - misma consecuencia que {@link #MAX_VEIN_SIZE}. */
        public static final int MAX_VEINS_PER_CHUNK = 256;

        public OreVein copy() {
            OreVein c = new OreVein();
            c.veinId = veinId;
            c.oreBlock = oreBlock;
            c.replaceableTarget = replaceableTarget;
            c.veinSize = veinSize;
            c.veinsPerChunk = veinsPerChunk;
            c.minY = minY;
            c.maxY = maxY;
            c.discardChanceOnAirExposure = discardChanceOnAirExposure;
            c.heightDistribution = heightDistribution;
            c.deepslateOreBlock = deepslateOreBlock;
            c.deepslateReplaceableTarget = deepslateReplaceableTarget;
            c.rarity = rarity;
            return c;
        }

        public OreVein copyWithNewId() {
            OreVein c = copy();
            c.veinId = java.util.UUID.randomUUID().toString();
            return c;
        }

        public List<String> validate() {
            List<String> errors = new ArrayList<>();
            if (oreBlock == null || oreBlock.isBlank()) errors.add("falta el bloque de mineral.");
            if (veinSize <= 0 || veinSize > MAX_VEIN_SIZE) errors.add("el tamaño de veta debe estar entre 1 y " + MAX_VEIN_SIZE + ".");
            if (veinsPerChunk <= 0 || veinsPerChunk > MAX_VEINS_PER_CHUNK) errors.add("las vetas por chunk deben estar entre 1 y " + MAX_VEINS_PER_CHUNK + ".");
            if (rarity < 0) errors.add("la rareza no puede ser negativa (0 = usar vetas por chunk).");
            if (minY >= maxY) errors.add("la altura mínima debe ser menor a la máxima.");
            if (!(discardChanceOnAirExposure >= 0 && discardChanceOnAirExposure <= 1)) {
                errors.add("la chance de descarte en superficie debe estar entre 0 y 1.");
            }
            if (deepslateOreBlock != null && !deepslateOreBlock.isBlank()
                    && (deepslateReplaceableTarget == null || deepslateReplaceableTarget.isBlank())) {
                errors.add("el target reemplazable de la variante deepslate no puede estar vacío.");
            }
            return errors;
        }
    }

    public enum TerrainPreset {
        FLAT, HILLY, MOUNTAINOUS, AMPLIFIED, OCEANIC;

        /**
         * Si el preset se puede aplicar al Overworld de verdad (ver
         * {@code TerrainPresetExporter}). FLAT usa otro tipo de generador (solo
         * en un mundo nuevo) y HILLY es el terreno normal de vanilla (no cambiaria nada).
         */
        public boolean supportsMainWorld() {
            return this == MOUNTAINOUS || this == AMPLIFIED || this == OCEANIC;
        }
    }

    /** Donde se aplica el preset de terreno - ver {@link #presetTarget}. */
    public enum PresetTarget { LAB, MAIN_WORLD }

    /** Tag de bloques (generado por Shovel JS) con la end stone: vanilla no trae uno para reemplazar en el End. */
    public static final String END_ORE_REPLACEABLES = "shovel_js:end_stone_ore_replaceables";

    /**
     * Regla para una dimension entera. Cada una es la etiqueta de
     * bioma de vanilla que cubre toda la dimension, y la roca que las vetas
     * reemplazan ahi (piedra, netherrack/basalto/blackstone, end stone).
     */
    public enum Dimension {
        OVERWORLD("#minecraft:is_overworld", "minecraft:stone_ore_replaceables"),
        NETHER("#minecraft:is_nether", "minecraft:base_stone_nether"),
        END("#minecraft:is_end", END_ORE_REPLACEABLES);

        public final String biomeTag;
        public final String oreTarget;

        Dimension(String biomeTag, String oreTarget) {
            this.biomeTag = biomeTag;
            this.oreTarget = oreTarget;
        }

        /** La dimension si los biomas son exactamente su etiqueta; null si es una seleccion de biomas sueltos. */
        public static Dimension of(List<String> biomeIds) {
            if (biomeIds == null || biomeIds.size() != 1) return null;
            for (Dimension d : values()) if (d.biomeTag.equals(biomeIds.get(0))) return d;
            return null;
        }

        /** True si {@code target} es la roca por defecto de alguna dimension (se puede cambiar sola al cambiar de dimension). */
        public static boolean isDefaultTarget(String target) {
            for (Dimension d : values()) if (d.oreTarget.equals(target)) return true;
            return false;
        }
    }
}
