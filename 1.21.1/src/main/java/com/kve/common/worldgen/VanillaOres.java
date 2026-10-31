package com.kve.common.worldgen;

import java.util.List;
import java.util.Locale;

public final class VanillaOres {

    private VanillaOres() {}

    private static final String STONE = "minecraft:stone_ore_replaceables";
    private static final String NETHER = "minecraft:base_stone_nether";

    /** Una veta de partida. {@code key} es el sufijo de la clave de idioma ({@code gui.kve.worldgen.template.<key>}). */
    public record Template(String key, String ore, String deepslateOre, String target, int size, int perChunk,
                           int minY, int maxY, WorldGenEntry.OreVein.HeightDistribution dist, double discard) {

        public WorldGenEntry.OreVein create() {
            WorldGenEntry.OreVein v = new WorldGenEntry.OreVein();
            v.veinId = java.util.UUID.randomUUID().toString();
            v.oreBlock = ore;
            v.replaceableTarget = target;
            v.deepslateOreBlock = deepslateOre == null ? "" : deepslateOre;
            v.veinSize = size;
            v.veinsPerChunk = perChunk;
            v.minY = minY;
            v.maxY = maxY;
            v.heightDistribution = dist;
            v.discardChanceOnAirExposure = discard;
            return v;
        }

        /** True si la veta va en el Overworld (reemplaza piedra): en una regla de Nether/End se cambia la roca. */
        public boolean overworld() { return STONE.equals(target); }
    }

    private static final WorldGenEntry.OreVein.HeightDistribution U = WorldGenEntry.OreVein.HeightDistribution.UNIFORM;
    private static final WorldGenEntry.OreVein.HeightDistribution TR = WorldGenEntry.OreVein.HeightDistribution.TRAPEZOID;

    public static final List<Template> TEMPLATES = List.of(
            new Template("coal", "minecraft:coal_ore", "minecraft:deepslate_coal_ore", STONE, 17, 20, 0, 192, TR, 0.0),
            new Template("iron", "minecraft:iron_ore", "minecraft:deepslate_iron_ore", STONE, 9, 10, -24, 56, TR, 0.0),
            new Template("copper", "minecraft:copper_ore", "minecraft:deepslate_copper_ore", STONE, 10, 16, -16, 112, TR, 0.0),
            new Template("gold", "minecraft:gold_ore", "minecraft:deepslate_gold_ore", STONE, 9, 4, -64, 32, TR, 0.5),
            new Template("redstone", "minecraft:redstone_ore", "minecraft:deepslate_redstone_ore", STONE, 8, 4, -64, 15, U, 0.0),
            new Template("lapis", "minecraft:lapis_ore", "minecraft:deepslate_lapis_ore", STONE, 7, 2, -32, 32, TR, 0.0),
            new Template("diamond", "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore", STONE, 4, 7, -64, 16, TR, 0.5),
            new Template("emerald", "minecraft:emerald_ore", "minecraft:deepslate_emerald_ore", STONE, 3, 12, -16, 320, TR, 0.0),
            new Template("quartz", "minecraft:nether_quartz_ore", "", NETHER, 14, 16, 10, 117, U, 0.0),
            new Template("nether_gold", "minecraft:nether_gold_ore", "", NETHER, 10, 10, 10, 117, U, 0.0),
            new Template("debris", "minecraft:ancient_debris", "", NETHER, 3, 1, 8, 24, TR, 1.0)
    );

    /**
     * Placed features vanilla (1.21.1, con ore_diamond_medium de 1.21) que generan el mineral de {@code oreBlock},
     * o una lista vacia si no es un mineral vanilla conocido. Acepta tambien la
     * variante deepslate ("minecraft:deepslate_iron_ore" -> las de hierro).
     */
    public static List<String> vanillaFeaturesFor(String oreBlock) {
        String id = oreBlock == null ? "" : oreBlock.trim().toLowerCase(Locale.ROOT);
        if (!id.startsWith("minecraft:")) return List.of();
        String path = id.substring("minecraft:".length()).replace("deepslate_", "");
        return switch (path) {
            case "coal_ore" -> List.of("minecraft:ore_coal_upper", "minecraft:ore_coal_lower");
            case "iron_ore" -> List.of("minecraft:ore_iron_upper", "minecraft:ore_iron_middle", "minecraft:ore_iron_small");
            case "copper_ore" -> List.of("minecraft:ore_copper", "minecraft:ore_copper_large");
            case "gold_ore" -> List.of("minecraft:ore_gold", "minecraft:ore_gold_lower", "minecraft:ore_gold_extra");
            case "redstone_ore" -> List.of("minecraft:ore_redstone", "minecraft:ore_redstone_lower");
            case "lapis_ore" -> List.of("minecraft:ore_lapis", "minecraft:ore_lapis_buried");
            case "diamond_ore" -> List.of("minecraft:ore_diamond", "minecraft:ore_diamond_medium", "minecraft:ore_diamond_large", "minecraft:ore_diamond_buried");
            case "emerald_ore" -> List.of("minecraft:ore_emerald");
            case "nether_quartz_ore" -> List.of("minecraft:ore_quartz_nether", "minecraft:ore_quartz_deltas");
            case "nether_gold_ore" -> List.of("minecraft:ore_gold_nether", "minecraft:ore_gold_deltas");
            case "ancient_debris" -> List.of("minecraft:ore_ancient_debris_large", "minecraft:ore_debris_small");
            default -> List.of();
        };
    }

    /** True si todas las features vanilla del mineral ya estan en {@code removed}. */
    public static boolean vanillaRemoved(String oreBlock, List<String> removed) {
        List<String> f = vanillaFeaturesFor(oreBlock);
        return !f.isEmpty() && removed.containsAll(f);
    }
}
