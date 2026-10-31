package com.kve.common.worldgen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OreOverride {

    public enum Mode { MODIFY, REMOVE }

    public static final String UNIFORM = "minecraft:uniform";
    public static final String TRAPEZOID = "minecraft:trapezoid";

    public String placedFeatureId = "";
    public Mode mode = Mode.MODIFY;
    public boolean enabled = true;

    /** Foto del placed feature tal como estaba en el registro al elegirlo. */
    public JsonObject originalPlaced = new JsonObject();
    /** Foto del configured feature referenciado (null si el placed lo trae inline). */
    public JsonObject originalConfigured;
    /** Paso de generacion donde aparece ({@code underground_ores}, {@code underground_decoration}...). */
    public String step = "underground_ores";
    /** Biomas donde generaba al elegirlo - destino del mineral modificado si {@link #biomeSelectors} esta vacio. */
    public List<String> originalBiomes = new ArrayList<>();
    /** Biomas/tags elegidos a mano. Vacio = MODIFY: {@link #originalBiomes}; REMOVE: todos los biomas. */
    public List<String> biomeSelectors = new ArrayList<>();
    /** Fondo y alto de la dimension, para pasar {@code above_bottom}/{@code below_top} a Y absoluta. */
    public int dimMinY = -64;
    public int dimHeight = 384;

    public Integer count;
    public Integer rarity;
    public Integer minY;
    public Integer maxY;
    public String heightType;
    public Integer veinSize;
    public Double discardChance;
    public List<String> targetBlocks = new ArrayList<>();

    public String key() { return placedFeatureId; }

    public OreOverride copy() {
        OreOverride c = new OreOverride();
        c.placedFeatureId = placedFeatureId;
        c.mode = mode;
        c.enabled = enabled;
        c.originalPlaced = originalPlaced == null ? null : originalPlaced.deepCopy();
        c.originalConfigured = originalConfigured == null ? null : originalConfigured.deepCopy();
        c.step = step;
        c.originalBiomes = new ArrayList<>(originalBiomes);
        c.biomeSelectors = new ArrayList<>(biomeSelectors);
        c.dimMinY = dimMinY;
        c.dimHeight = dimHeight;
        c.count = count;
        c.rarity = rarity;
        c.minY = minY;
        c.maxY = maxY;
        c.heightType = heightType;
        c.veinSize = veinSize;
        c.discardChance = discardChance;
        c.targetBlocks = new ArrayList<>(targetBlocks);
        return c;
    }

    public static OreOverride fromOriginal(String placedId, JsonObject placed, JsonObject configured,
                                           String step, List<String> biomes, int dimMinY, int dimHeight) {
        OreOverride o = new OreOverride();
        o.placedFeatureId = placedId;
        o.originalPlaced = placed == null ? new JsonObject() : placed.deepCopy();
        o.originalConfigured = configured == null ? null : configured.deepCopy();
        if (step != null && !step.isBlank()) o.step = step;
        if (biomes != null) o.originalBiomes = new ArrayList<>(biomes);
        o.dimMinY = dimMinY;
        o.dimHeight = dimHeight;
        o.resetToOriginal();
        return o;
    }

    public void resetToOriginal() {
        Original orig = original();
        count = orig.count;
        rarity = orig.rarity;
        minY = orig.minY;
        maxY = orig.maxY;
        heightType = orig.heightType;
        veinSize = orig.veinSize;
        discardChance = orig.discardChance;
        targetBlocks = new ArrayList<>(orig.targetBlocks);
    }

    public Original original() {
        Original r = new Original();
        JsonArray placement = placement(originalPlaced);
        if (placement != null) {
            for (JsonElement el : placement) {
                if (!el.isJsonObject()) continue;
                JsonObject m = el.getAsJsonObject();
                String type = typeOf(m);
                if (type.equals("minecraft:count") && m.has("count") && isInt(m.get("count"))) {
                    r.count = m.get("count").getAsInt();
                } else if (type.equals("minecraft:count") && m.has("count")) {
                    r.countVariable = true;
                    r.count = 0;
                } else if (type.equals("minecraft:rarity_filter") && m.has("chance") && isInt(m.get("chance"))) {
                    r.rarity = m.get("chance").getAsInt();
                } else if (type.equals("minecraft:height_range") && m.has("height") && m.get("height").isJsonObject()) {
                    JsonObject h = m.getAsJsonObject("height");
                    r.heightType = typeOf(h);
                    r.minAnchor = h.get("min_inclusive");
                    r.maxAnchor = h.get("max_inclusive");
                    r.minY = anchorToAbsolute(r.minAnchor, dimMinY, dimHeight);
                    r.maxY = anchorToAbsolute(r.maxAnchor, dimMinY, dimHeight);
                    if (r.minY == null || r.maxY == null) { r.minY = null; r.maxY = null; }
                }
            }
        }
        JsonObject config = oreConfig();
        if (config != null) {
            if (config.has("size") && isInt(config.get("size"))) r.veinSize = config.get("size").getAsInt();
            if (config.has("discard_chance_on_air_exposure") && config.get("discard_chance_on_air_exposure").isJsonPrimitive()) {
                r.discardChance = config.get("discard_chance_on_air_exposure").getAsDouble();
            }
            if (config.has("targets") && config.get("targets").isJsonArray()) {
                for (JsonElement t : config.getAsJsonArray("targets")) r.targetBlocks.add(targetBlock(t));
            }
        }
        return r;
    }

    public static final class Original {
        public Integer count, rarity, minY, maxY, veinSize;
        public boolean countVariable;
        public String heightType;
        public JsonElement minAnchor, maxAnchor;
        public Double discardChance;
        public List<String> targetBlocks = new ArrayList<>();
    }

    /** Config del ore ({@code minecraft:ore}/{@code scattered_ore}): del configured referenciado o del inline. */
    public JsonObject oreConfig() {
        JsonObject cf = configuredJson();
        return cf != null && cf.has("config") && cf.get("config").isJsonObject() ? cf.getAsJsonObject("config") : null;
    }

    /** Configured feature efectivo: el inline del placed, o la foto aparte. */
    public JsonObject configuredJson() {
        if (originalPlaced != null && originalPlaced.has("feature") && originalPlaced.get("feature").isJsonObject()) {
            return originalPlaced.getAsJsonObject("feature");
        }
        return originalConfigured;
    }

    /** Id del configured referenciado ({@code minecraft:ore_diamond_small}), o null si es inline. */
    public String configuredFeatureId() {
        if (originalPlaced == null || !originalPlaced.has("feature")) return null;
        JsonElement f = originalPlaced.get("feature");
        return f.isJsonPrimitive() ? f.getAsString() : null;
    }

    public boolean placementChanged() {
        Original o = original();
        return !eq(count, o.count) || !eq(rarity, o.rarity) || heightChanged(o);
    }

    public boolean heightChanged(Original o) {
        return !eq(minY, o.minY) || !eq(maxY, o.maxY) || !eq(heightType, o.heightType);
    }

    public boolean configChanged() {
        Original o = original();
        return !eq(veinSize, o.veinSize) || !eq(discardChance, o.discardChance) || !targetBlocks.equals(o.targetBlocks);
    }

    public boolean isNoop() {
        return mode == Mode.MODIFY && biomeSelectors.isEmpty() && !placementChanged() && !configChanged();
    }

    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (placedFeatureId == null || !placedFeatureId.contains(":")) errors.add("Falta el id del mineral.");
        if (mode == Mode.REMOVE) return errors;
        if (count != null && (count < 0 || count > WorldGenEntry.OreVein.MAX_VEINS_PER_CHUNK)) {
            errors.add("Las vetas por chunk deben estar entre 0 y " + WorldGenEntry.OreVein.MAX_VEINS_PER_CHUNK + ".");
        }
        if (rarity != null && rarity < 1) errors.add("La rareza (1 de cada N chunks) debe ser 1 o más.");
        if (veinSize != null && (veinSize < 0 || veinSize > WorldGenEntry.OreVein.MAX_VEIN_SIZE)) {
            errors.add("El tamaño de veta debe estar entre 0 y " + WorldGenEntry.OreVein.MAX_VEIN_SIZE + ".");
        }
        if (minY != null && maxY != null && minY > maxY) errors.add("La altura mínima no puede ser mayor que la máxima.");
        if (discardChance != null && !(discardChance >= 0 && discardChance <= 1)) {
            errors.add("La chance de descarte debe estar entre 0 y 1.");
        }
        for (String b : targetBlocks) {
            if (b == null || !b.contains(":")) { errors.add("Cada bloque de mineral necesita un id (ns:bloque)."); break; }
        }
        if (!placementChanged() && !configChanged() && biomeSelectors.isEmpty()) {
            errors.add("No hay cambios respecto del original.");
        }
        return errors;
    }

    static JsonArray placement(JsonObject placed) {
        return placed != null && placed.has("placement") && placed.get("placement").isJsonArray()
                ? placed.getAsJsonArray("placement") : null;
    }

    static String typeOf(JsonObject o) {
        if (o == null || !o.has("type") || !o.get("type").isJsonPrimitive()) return "";
        String t = o.get("type").getAsString();
        return t.contains(":") ? t : "minecraft:" + t;
    }

    static boolean isInt(JsonElement e) {
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return false;
        double d = e.getAsDouble();
        return d == Math.rint(d);
    }

    static String targetBlock(JsonElement t) {
        if (t == null || !t.isJsonObject()) return "";
        JsonObject o = t.getAsJsonObject();
        if (o.has("state") && o.get("state").isJsonObject()) {
            JsonObject s = o.getAsJsonObject("state");
            if (s.has("Name") && s.get("Name").isJsonPrimitive()) return s.get("Name").getAsString();
        }
        return "";
    }

    /**
     * {@code {"absolute": n}} / {@code {"above_bottom": n}} / {@code {"below_top": n}}
     * a Y absoluta (misma cuenta que {@code VerticalAnchor} de vanilla:
     * below_top = fondo + alto - 1 - n). Null si no es un anchor conocido.
     */
    public static Integer anchorToAbsolute(JsonElement anchor, int dimMinY, int dimHeight) {
        if (anchor == null || !anchor.isJsonObject()) return null;
        JsonObject a = anchor.getAsJsonObject();
        if (a.has("absolute")) return a.get("absolute").getAsInt();
        if (a.has("above_bottom")) return dimMinY + a.get("above_bottom").getAsInt();
        if (a.has("below_top")) return dimMinY + dimHeight - 1 - a.get("below_top").getAsInt();
        return null;
    }

    public static String anchorLabel(JsonElement anchor) {
        if (anchor == null || !anchor.isJsonObject()) return "?";
        for (String k : new String[]{"absolute", "above_bottom", "below_top"}) {
            if (anchor.getAsJsonObject().has(k)) return k + " " + anchor.getAsJsonObject().get(k).getAsString();
        }
        return anchor.toString();
    }

    /** "minecraft:trapezoid" -> "trapezoid" para la UI. */
    public static String shortType(String t) {
        if (t == null || t.isEmpty()) return "?";
        return t.startsWith("minecraft:") ? t.substring("minecraft:".length()).toLowerCase(Locale.ROOT) : t;
    }

    private static boolean eq(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }
}
