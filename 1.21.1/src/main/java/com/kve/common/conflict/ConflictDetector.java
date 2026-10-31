package com.kve.common.conflict;

import com.kve.common.recipe.RecipeData;
import com.kve.common.recipe.TagIngredient;
import com.kve.common.worldgen.WorldGenEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Busca en lo que el jugador armo con el
 * editor las combinaciones que casi siempre son un error o, por lo menos, algo
 * que conviene saber:
 * <ul>
 *   <li>{@link Kind#RECIPE_DUPLICATE}: dos recetas propias IDENTICAS (mismo tipo
 *   y mismas entradas) - una de las dos sobra, y en el juego gana cualquiera.</li>
 *   <li>{@link Kind#RECIPE_SAME_OUTPUT}: varias recetas propias con el mismo
 *   resultado (aviso: puede ser a proposito, ej. dos formas de hacer algo).</li>
 *   <li>{@link Kind#WORLDGEN_FEATURE_REMOVED_TWICE}: dos reglas de world gen
 *   quitan la misma feature (la segunda no hace nada, y al borrar una de las
 *   dos la feature sigue quitada sin que se note por que).</li>
 *   <li>{@link Kind#WORLDGEN_VEIN_DUPLICATE}: dos vetas identicas dentro de la
 *   misma regla (el mineral sale el doble).</li>
 * </ul>
 * Solo lee - no toca nada ni depende de la UI. Solo entradas habilitadas: una
 * receta o regla deshabilitada no se escribe, asi que no puede chocar.
 */
public final class ConflictDetector {

    private ConflictDetector() {}

    public enum Kind { RECIPE_DUPLICATE, RECIPE_SAME_INPUT, RECIPE_SAME_OUTPUT, WORLDGEN_FEATURE_REMOVED_TWICE, WORLDGEN_VEIN_DUPLICATE }

    public enum Severity { WARN, INFO }

    /**
     * @param subject lo que se repite: id del item resultado, id de la feature, o bloque de la veta
     * @param keys    claves de las entradas involucradas (recipeId / id de la regla), en orden
     * @param detail  dato extra (ej. numeros de veta "2 y 5"); "" si no hay
     */
    public record Conflict(Kind kind, Severity severity, String subject, List<String> keys, String detail) {
        public boolean involves(String key) { return keys.contains(key); }
    }

    /**
     * Los dos tipos de choque entre tus recetas:
     * <ul>
     *   <li>{@link Kind#RECIPE_SAME_INPUT}: mismos ingredientes y otro resultado. Es el conflicto
     *   real: el juego usa solo una de las dos recetas.</li>
     *   <li>{@link Kind#RECIPE_DUPLICATE}: misma receta dos veces (mismos ingredientes y resultado); una sobra.</li>
     * </ul>
     * Las recetas con el mismo resultado y otros ingredientes no son un error (son varias formas
     * de hacer lo mismo); se listan aparte en {@link #recipeOutputGroups}.
     */
    public static List<Conflict> recipeConflicts(Collection<RecipeData> recipes) {
        List<Conflict> out = new ArrayList<>();
        Map<String, List<RecipeData>> byInput = new LinkedHashMap<>();
        for (RecipeData r : recipes) {
            if (r == null || !r.enabled || !hasInputs(r)) continue;
            byInput.computeIfAbsent(inputSignature(r), k -> new ArrayList<>()).add(r);
        }
        for (List<RecipeData> group : byInput.values()) {
            if (group.size() < 2) continue;
            Map<String, List<String>> byOutput = new LinkedHashMap<>();
            for (RecipeData r : group) byOutput.computeIfAbsent(outputKey(r), k -> new ArrayList<>()).add(r.recipeId);
            for (Map.Entry<String, List<String>> en : byOutput.entrySet()) {
                if (en.getValue().size() > 1) {
                    out.add(new Conflict(Kind.RECIPE_DUPLICATE, Severity.WARN, en.getKey(), List.copyOf(en.getValue()), ""));
                }
            }
            if (byOutput.size() > 1) {
                List<String> ids = new ArrayList<>();
                for (RecipeData r : group) ids.add(r.recipeId);
                out.add(new Conflict(Kind.RECIPE_SAME_INPUT, Severity.WARN, outputKey(group.get(0)), ids,
                        String.join(", ", byOutput.keySet())));
            }
        }
        return out;
    }

    /** Recetas tuyas que dan el mismo resultado con otros ingredientes. Informativo, no es un error. */
    public static List<Conflict> recipeOutputGroups(Collection<RecipeData> recipes) {
        List<Conflict> out = new ArrayList<>();
        Map<String, List<RecipeData>> byOutput = new LinkedHashMap<>();
        for (RecipeData r : recipes) {
            if (r == null || !r.enabled || r.output == null || r.output.isEmpty()) continue;
            byOutput.computeIfAbsent(stackKey(r.output), k -> new ArrayList<>()).add(r);
        }
        for (Map.Entry<String, List<RecipeData>> en : byOutput.entrySet()) {
            if (en.getValue().size() < 2) continue;
            List<String> ids = new ArrayList<>();
            for (RecipeData r : en.getValue()) ids.add(r.recipeId);
            out.add(new Conflict(Kind.RECIPE_SAME_OUTPUT, Severity.INFO, en.getKey(), ids, String.valueOf(ids.size())));
        }
        return out;
    }

    private static boolean hasInputs(RecipeData r) {
        if (r.machineInputs != null && !r.machineInputs.isEmpty()) return true;
        if (r.shapelessIngredients != null && r.shapelessIngredients.stream().anyMatch(s -> s != null && !s.isEmpty())) return true;
        if (r.ingredients != null && r.ingredients.stream().anyMatch(s -> s != null && !s.isEmpty())) return true;
        return r.combinationCenter != null && !r.combinationCenter.isEmpty();
    }

    /** El resultado de la receta (o de la primera salida de maquina), para nombrarlo y agrupar. */
    static String outputKey(RecipeData r) {
        if (r.output != null && !r.output.isEmpty()) return stackKey(r.output) + (r.output.getCount() > 1 ? " x" + r.output.getCount() : "");
        if (r.machineOutputs != null) {
            for (RecipeData.MachineSlot m : r.machineOutputs) {
                switch (m.type) {
                    case ITEM -> { if (m.item != null && !m.item.isEmpty()) return stackKey(m.item) + (m.item.getCount() > 1 ? " x" + m.item.getCount() : ""); }
                    case FLUID -> { if (m.fluidId != null && !m.fluidId.isBlank()) return "fluid:" + m.fluidId; }
                    case TAG -> { if (m.tagId != null && !m.tagId.isBlank()) return "#" + m.tagId; }
                }
            }
        }
        return "?";
    }

    /**
     * Firma de los ingredientes tal como los compara el juego: tipo de receta (o estacion), y la
     * forma recortada a su caja minima y sin importar si esta espejada, porque la mesa acepta un
     * patron chico en cualquier posicion y tambien reflejado. Sin forma y maquinas: los
     * ingredientes ordenados.
     */
    static String inputSignature(RecipeData r) {
        String station = switch (r.type) {
            case VANILLA_SHAPED, VANILLA_SHAPELESS -> "crafting";
            default -> r.type + "|" + (r.customTypeId == null ? "" : r.customTypeId.toLowerCase(Locale.ROOT));
        };
        StringBuilder sb = new StringBuilder(station).append('|');
        if (r.machineInputs != null && !r.machineInputs.isEmpty()) {
            List<String> parts = new ArrayList<>();
            for (RecipeData.MachineSlot m : r.machineInputs) {
                parts.add(switch (m.type) {
                    case ITEM -> stackKey(m.item) + "x" + m.item.getCount();
                    case FLUID -> "fluid:" + m.fluidId + "x" + m.fluidAmount;
                    case TAG -> "#" + m.tagId + "x" + m.tagCount;
                });
            }
            parts.sort(null);
            sb.append("M").append(parts);
        } else if (r.type == RecipeData.RecipeType.VANILLA_SHAPED || (r.ingredients != null && !r.ingredients.isEmpty()
                && r.ingredients.size() == r.gridWidth * r.gridHeight && (r.shapelessIngredients == null || r.shapelessIngredients.isEmpty()))) {
            sb.append(shapeSignature(r.ingredients, r.gridWidth, r.gridHeight));
        } else if (r.shapelessIngredients != null && !r.shapelessIngredients.isEmpty()) {
            List<String> parts = new ArrayList<>();
            for (ItemStack s : r.shapelessIngredients) if (s != null && !s.isEmpty()) parts.add(stackKey(s));
            parts.sort(null);
            sb.append("S").append(parts);
        }
        if (r.combinationCenter != null && !r.combinationCenter.isEmpty()) sb.append("C").append(stackKey(r.combinationCenter));
        return sb.toString();
    }

    private static String shapeSignature(List<ItemStack> cells, int w, int h) {
        if (cells == null || w <= 0 || h <= 0 || cells.size() < w * h) return "G?";
        int minR = h, maxR = -1, minC = w, maxC = -1;
        for (int r = 0; r < h; r++) for (int c = 0; c < w; c++) {
            ItemStack s = cells.get(r * w + c);
            if (s != null && !s.isEmpty()) { minR = Math.min(minR, r); maxR = Math.max(maxR, r); minC = Math.min(minC, c); maxC = Math.max(maxC, c); }
        }
        if (maxR < 0) return "G[]";
        StringBuilder a = new StringBuilder("G"), b = new StringBuilder("G");
        for (int r = minR; r <= maxR; r++) {
            for (int c = minC; c <= maxC; c++) {
                ItemStack s = cells.get(r * w + c), m = cells.get(r * w + (maxC - (c - minC)));
                a.append(s == null || s.isEmpty() ? "-" : stackKey(s)).append(',');
                b.append(m == null || m.isEmpty() ? "-" : stackKey(m)).append(',');
            }
            a.append('/');
            b.append('/');
        }
        String x = a.toString(), y = b.toString();
        return x.compareTo(y) <= 0 ? x : y;
    }

    static String stackKey(ItemStack s) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(s.getItem());
        String base = id == null ? "?" : id.toString();
        String tag = TagIngredient.tagOf(s);
        if (tag != null) return "#" + tag;
        return s.getComponentsPatch().isEmpty() ? base : base + s.getComponentsPatch();
    }

    static String signature(RecipeData r) {
        StringBuilder sb = new StringBuilder();
        sb.append(r.type).append('|').append(r.customTypeId == null ? "" : r.customTypeId.toLowerCase(Locale.ROOT)).append('|');
        if (r.machineInputs != null && !r.machineInputs.isEmpty()) {
            List<String> parts = new ArrayList<>();
            for (RecipeData.MachineSlot m : r.machineInputs) {
                parts.add(switch (m.type) {
                    case ITEM -> stackKey(m.item) + "x" + m.item.getCount();
                    case FLUID -> "fluid:" + m.fluidId + "x" + m.fluidAmount;
                    case TAG -> "#" + m.tagId + "x" + m.tagCount;
                });
            }
            parts.sort(null);
            sb.append("M").append(parts);
        } else if (r.shapelessIngredients != null && !r.shapelessIngredients.isEmpty()) {
            List<String> parts = new ArrayList<>();
            for (ItemStack s : r.shapelessIngredients) if (s != null && !s.isEmpty()) parts.add(stackKey(s));
            parts.sort(null);
            sb.append("S").append(parts);
        } else if (r.ingredients != null) {
            sb.append("G").append(r.gridWidth).append('x').append(r.gridHeight).append('[');
            for (ItemStack s : r.ingredients) sb.append(s == null || s.isEmpty() ? "-" : stackKey(s)).append(',');
            sb.append(']');
        }
        if (r.combinationCenter != null && !r.combinationCenter.isEmpty()) sb.append("C").append(stackKey(r.combinationCenter));
        return sb.toString();
    }

    public static List<Conflict> worldGenConflicts(Collection<WorldGenEntry> rules) {
        List<Conflict> out = new ArrayList<>();
        Map<String, List<String>> removedBy = new LinkedHashMap<>();
        for (WorldGenEntry e : rules) {
            if (e == null || !e.enabled) continue;
            for (String f : e.removedFeatures) {
                String key = f == null ? "" : f.trim();
                if (key.isEmpty()) continue;
                List<String> ids = removedBy.computeIfAbsent(key, k -> new ArrayList<>());
                if (!ids.contains(e.key())) ids.add(e.key());
            }
            out.addAll(duplicateVeins(e));
        }
        for (Map.Entry<String, List<String>> en : removedBy.entrySet()) {
            if (en.getValue().size() > 1) {
                out.add(new Conflict(Kind.WORLDGEN_FEATURE_REMOVED_TWICE, Severity.WARN, en.getKey(), List.copyOf(en.getValue()), ""));
            }
        }
        return out;
    }

    public static List<Conflict> duplicateVeins(WorldGenEntry e) {
        List<Conflict> out = new ArrayList<>();
        Map<String, List<Integer>> bySignature = new LinkedHashMap<>();
        for (int i = 0; i < e.oreVeins.size(); i++) {
            bySignature.computeIfAbsent(veinSignature(e.oreVeins.get(i)), k -> new ArrayList<>()).add(i + 1);
        }
        for (List<Integer> idx : bySignature.values()) {
            if (idx.size() < 2) continue;
            String block = e.oreVeins.get(idx.get(0) - 1).oreBlock;
            List<String> nums = new ArrayList<>();
            for (int n : idx) nums.add(String.valueOf(n));
            out.add(new Conflict(Kind.WORLDGEN_VEIN_DUPLICATE, Severity.WARN, block, List.of(e.key()), String.join(", ", nums)));
        }
        return out;
    }

    /** Indices (base 0) de las vetas que repiten una veta anterior de la misma regla - para marcar las filas. */
    public static java.util.Set<Integer> duplicateVeinIndexes(WorldGenEntry e) {
        java.util.Set<Integer> out = new java.util.HashSet<>();
        Map<String, Integer> first = new LinkedHashMap<>();
        for (int i = 0; i < e.oreVeins.size(); i++) {
            String sig = veinSignature(e.oreVeins.get(i));
            if (first.containsKey(sig)) { out.add(i); out.add(first.get(sig)); }
            else first.put(sig, i);
        }
        return out;
    }

    static String veinSignature(WorldGenEntry.OreVein v) {
        return String.join("|",
                norm(v.oreBlock), norm(v.replaceableTarget), norm(v.deepslateOreBlock), norm(v.deepslateReplaceableTarget),
                String.valueOf(v.veinSize), String.valueOf(v.veinsPerChunk), String.valueOf(v.rarity),
                String.valueOf(v.minY), String.valueOf(v.maxY), String.valueOf(v.heightDistribution),
                String.valueOf(v.discardChanceOnAirExposure));
    }

    private static String norm(String s) {
        return Objects.requireNonNullElse(s, "").trim().toLowerCase(Locale.ROOT);
    }
}
