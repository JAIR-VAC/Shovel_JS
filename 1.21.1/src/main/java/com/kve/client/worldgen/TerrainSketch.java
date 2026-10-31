package com.kve.client.worldgen;

import com.kve.common.worldgen.WorldGenEntry;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

import java.util.Locale;
import java.util.Optional;

/**
 * Boceto del terreno de un bioma para el editor de world gen: un mapa
 * topografico simple (relieve, agua, curvas de nivel) y su corte vertical.
 * <p>
 * NO es el terreno real: el de 1.18+ sale del noise router de la dimension y
 * del clima, no del bioma. Esto es una representacion aproximada de como se
 * ve un bioma de ese tipo (llano, montañoso, oceano...), calculada una vez por
 * bioma y preset con un ruido propio barato - nada de generar chunks.
 */
public final class TerrainSketch {

    /** Celdas por lado del mapa. */
    public static final int SIZE = 64;
    /** Valor de altura de una celda sin suelo (vacio del End). */
    public static final int VOID = Integer.MIN_VALUE;

    /** Como es el terreno de un tipo de bioma. Alturas en bloques. */
    public record Profile(String kind, float base, float amp, float freq, boolean terraces, int seaLevel,
                          int surface, int water, int shore, boolean snowy, boolean nether, boolean end) {

        Profile with(float base, float amp, float freq, int seaLevel) {
            return new Profile(kind, base, amp, freq, terraces, seaLevel, surface, water, shore, snowy, nether, end);
        }
    }

    public final Profile profile;
    /** Altura (Y) de cada celda, fila por fila; {@link #VOID} si no hay suelo. */
    public final int[] heights = new int[SIZE * SIZE];
    /** Color ARGB de cada celda, ya con relieve, agua y curvas de nivel. */
    public final int[] colors = new int[SIZE * SIZE];

    private TerrainSketch(Profile profile) {
        this.profile = profile;
    }

    public int height(int x, int z) { return heights[z * SIZE + x]; }

    public int color(int x, int z) { return colors[z * SIZE + x]; }

    /** Mismo perfil y semilla dan siempre el mismo boceto. */
    public static TerrainSketch build(Profile p, long seed) {
        TerrainSketch s = new TerrainSketch(p);
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                s.heights[z * SIZE + x] = heightAt(p, seed, x, z);
            }
        }
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                s.colors[z * SIZE + x] = s.shade(x, z);
            }
        }
        return s;
    }

    private static int heightAt(Profile p, long seed, int x, int z) {
        float n = fbm(seed, x * p.freq() / 16f, z * p.freq() / 16f);
        if (p.end()) {
            return n < 0.05f ? VOID : Math.round(48 + n * 40);
        }
        float h = p.base() + p.amp() * n;
        if (p.terraces()) h = (float) Math.floor(h / 6f) * 6f;
        return Math.round(h);
    }

    private int shade(int x, int z) {
        int h = height(x, z);
        if (h == VOID) return 0xFF0E0A14;
        Profile p = profile;
        int base;
        if (h < p.seaLevel()) {
            float depth = Math.min(0.6f, (p.seaLevel() - h) / 40f);
            base = mix(p.water(), 0xFF000000, depth);
        } else if (p.snowy() && h > p.seaLevel() + 18) {
            base = 0xFFF0F4F8;
        } else if (!p.nether() && !p.end() && h > 105 && slope(x, z) > 5) {
            base = 0xFF8A8A8A;
        } else if (h <= p.seaLevel() + 1) {
            base = p.shore();
        } else {
            base = p.surface();
        }
        int nw = x > 0 && z > 0 ? height(x - 1, z - 1) : h;
        if (nw == VOID) nw = h;
        float light = Math.max(0.6f, Math.min(1.25f, 1f + (h - nw) * 0.05f));
        int c = scale(base, light);
        if (h >= p.seaLevel() && isContour(x, z, h)) c = scale(c, 0.72f);
        return c;
    }

    private boolean isContour(int x, int z, int h) {
        int band = Math.floorDiv(h, 8);
        int right = x + 1 < SIZE ? height(x + 1, z) : h;
        int down = z + 1 < SIZE ? height(x, z + 1) : h;
        return (right != VOID && Math.floorDiv(right, 8) != band) || (down != VOID && Math.floorDiv(down, 8) != band);
    }

    private int slope(int x, int z) {
        int h = height(x, z);
        int max = 0;
        if (x + 1 < SIZE && height(x + 1, z) != VOID) max = Math.max(max, Math.abs(height(x + 1, z) - h));
        if (z + 1 < SIZE && height(x, z + 1) != VOID) max = Math.max(max, Math.abs(height(x, z + 1) - h));
        return max;
    }

    static float fbm(long seed, float x, float z) {
        float sum = 0f, amp = 1f, norm = 0f, f = 1f;
        for (int o = 0; o < 4; o++) {
            sum += amp * valueNoise(seed + o * 7919L, x * f, z * f);
            norm += amp;
            amp *= 0.5f;
            f *= 2f;
        }
        return sum / norm;
    }

    private static float valueNoise(long seed, float x, float z) {
        int x0 = (int) Math.floor(x), z0 = (int) Math.floor(z);
        float tx = smooth(x - x0), tz = smooth(z - z0);
        float a = lattice(seed, x0, z0), b = lattice(seed, x0 + 1, z0);
        float c = lattice(seed, x0, z0 + 1), d = lattice(seed, x0 + 1, z0 + 1);
        float top = a + (b - a) * tx, bottom = c + (d - c) * tx;
        return top + (bottom - top) * tz;
    }

    private static float smooth(float t) { return t * t * (3 - 2 * t); }

    private static float lattice(long seed, int x, int z) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
        h ^= (h >>> 33);
        h *= 0xFF51AFD7ED558CCDL;
        h ^= (h >>> 33);
        return ((h >>> 40) & 0xFFFFFF) / (float) 0xFFFFFF * 2f - 1f;
    }

    static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    static int scale(int c, float f) {
        int r = Math.min(255, (int) (((c >> 16) & 0xFF) * f));
        int g = Math.min(255, (int) (((c >> 8) & 0xFF) * f));
        int b = Math.min(255, (int) ((c & 0xFF) * f));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static final int GRASS = 0xFF79C05A, WATER = 0xFF3F76E4, SAND = 0xFFDBCF9A;

    /** Perfil por defecto (llanura) - lo que se muestra sin biomas elegidos. */
    public static Profile plains() {
        return new Profile("plains", 67, 6, 1f, false, 63, GRASS, WATER, SAND, false, false, false);
    }

    public static Profile profileFor(RegistryAccess access, String biomeOrTag, WorldGenEntry.TerrainPreset preset) {
        Profile p = baseProfile(access, biomeOrTag == null ? "" : biomeOrTag.trim());
        return applyPreset(p, preset);
    }

    static Profile applyPreset(Profile p, WorldGenEntry.TerrainPreset preset) {
        if (preset == null || p.nether() || p.end()) return p;
        return switch (preset) {
            case FLAT -> p.with(64, 0, p.freq(), p.seaLevel());
            case AMPLIFIED -> p.with(p.base() + (p.base() > 70 ? 30 : 10), p.amp() * 2.3f, p.freq(), p.seaLevel());
            case MOUNTAINOUS -> p.with(p.base(), p.amp(), p.freq() * 0.45f, p.seaLevel());
            case OCEANIC -> p.with(p.base(), p.amp(), p.freq(), com.kve.common.worldgen.TerrainPresetExporter.OCEANIC_SEA_LEVEL);
            case HILLY -> p;
        };
    }

    private static Profile baseProfile(RegistryAccess access, String id) {
        boolean tag = id.startsWith("#");
        String bare = tag ? id.substring(1) : id;
        ResourceLocation rl = ResourceLocation.tryParse(bare);
        String path = rl == null ? bare.toLowerCase(Locale.ROOT) : rl.getPath();

        Holder<Biome> holder = null;
        if (access != null && rl != null) {
            try {
                Registry<Biome> reg = access.registryOrThrow(Registries.BIOME);
                if (tag) {
                    Optional<HolderSet.Named<Biome>> set = reg.getTag(TagKey.create(Registries.BIOME, rl));
                    if (set.isPresent() && set.get().size() > 0) holder = set.get().get(0);
                } else {
                    holder = reg.getHolder(ResourceKey.create(Registries.BIOME, rl)).orElse(null);
                }
            } catch (Exception ignored) {
            }
        }

        int grass = GRASS, water = WATER;
        boolean cold = path.contains("snow") || path.contains("frozen") || path.contains("ice") || path.contains("peak");
        if (holder != null) {
            try {
                Biome b = holder.value();
                grass = 0xFF000000 | b.getGrassColor(0, 0);
                water = 0xFF000000 | b.getWaterColor();
                cold = cold || b.getBaseTemperature() < 0.15f;
            } catch (Exception ignored) {}
        }
        boolean nether = path.equals("is_nether") || is(holder, BiomeTags.IS_NETHER) || path.contains("nether") || path.contains("crimson")
                || path.contains("warped") || path.contains("basalt") || path.contains("soul_sand");
        boolean end = path.equals("is_end") || is(holder, BiomeTags.IS_END) || path.contains("end_") || path.equals("the_end");

        if (nether) {
            return new Profile("nether", 44, 20, 1.6f, false, 31, 0xFF7A2E2E, 0xFFFF6A1A, 0xFF5A2020, false, true, false);
        }
        if (end) {
            return new Profile("end", 48, 30, 0.9f, false, 0, 0xFFDBDE9E, 0xFF000000, 0xFFDBDE9E, false, false, true);
        }
        if (path.contains("deep_ocean") || is(holder, BiomeTags.IS_DEEP_OCEAN)) {
            return new Profile("deep_ocean", 34, 8, 1f, false, 63, SAND, water, SAND, cold, false, false);
        }
        if (path.contains("ocean") || is(holder, BiomeTags.IS_OCEAN)) {
            return new Profile("ocean", 46, 7, 1f, false, 63, SAND, water, SAND, cold, false, false);
        }
        if (path.contains("river") || is(holder, BiomeTags.IS_RIVER)) {
            return new Profile("river", 59, 3, 1.4f, false, 63, grass, water, SAND, cold, false, false);
        }
        if (path.contains("beach") || path.contains("shore") || is(holder, BiomeTags.IS_BEACH)) {
            return new Profile("beach", 64, 2, 1f, false, 63, SAND, water, SAND, cold, false, false);
        }
        if (path.contains("peak") || path.contains("slope") || path.contains("mountain") || is(holder, BiomeTags.IS_MOUNTAIN)) {
            return new Profile("mountain", 115, 50, 1.4f, false, 63, grass, water, SAND, true, false, false);
        }
        if (path.contains("badlands") || path.contains("mesa") || is(holder, BiomeTags.IS_BADLANDS)) {
            return new Profile("badlands", 78, 16, 1.1f, true, 63, 0xFFB5653A, water, 0xFFC8834F, false, false, false);
        }
        if (path.contains("hill") || path.contains("windswept") || is(holder, BiomeTags.IS_HILL)) {
            return new Profile("hills", 85, 22, 1.2f, false, 63, grass, water, SAND, cold, false, false);
        }
        if (path.contains("desert") || path.contains("dune")) {
            return new Profile("desert", 67, 4, 0.8f, false, 63, SAND, water, SAND, false, false, false);
        }
        if (path.contains("swamp") || path.contains("mangrove") || path.contains("marsh")) {
            return new Profile("swamp", 62, 2.5f, 1.5f, false, 63, grass, water, grass, cold, false, false);
        }
        if (path.contains("jungle") || is(holder, BiomeTags.IS_JUNGLE)) {
            return new Profile("jungle", 72, 12, 1.2f, false, 63, grass, water, SAND, false, false, false);
        }
        if (path.contains("savanna") || is(holder, BiomeTags.IS_SAVANNA)) {
            return new Profile("savanna", 74, 8, 0.9f, false, 63, grass, water, SAND, false, false, false);
        }
        if (path.contains("forest") || path.contains("taiga") || is(holder, BiomeTags.IS_FOREST) || is(holder, BiomeTags.IS_TAIGA)) {
            return new Profile("forest", 69, 8, 1.1f, false, 63, grass, water, SAND, cold, false, false);
        }
        return new Profile("plains", 67, 6, 1f, false, 63, grass, water, SAND, cold, false, false);
    }

    private static boolean is(Holder<Biome> holder, TagKey<Biome> tag) {
        try {
            return holder != null && holder.is(tag);
        } catch (Exception ex) {
            return false;
        }
    }
}
