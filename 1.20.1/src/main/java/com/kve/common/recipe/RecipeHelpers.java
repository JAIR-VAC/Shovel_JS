package com.kve.common.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Ayudas del editor de recetas sin nada de pantalla: coste en
 * materiales, id sugerido, receta inversa y variantes de horno.
 */
public final class RecipeHelpers {
    private RecipeHelpers() {}

    /** Clave de un ingrediente para el resumen: "#tag" o id del item. */
    public static String ingredientKey(ItemStack s) {
        String tag = TagIngredient.tagOf(s);
        if (tag != null) return "#" + tag;
        ResourceLocation k = BuiltInRegistries.ITEM.getKey(s.getItem());
        return k == null ? "?" : k.toString();
    }

    /** Cuantos de cada ingrediente pide la receta, en el orden en que aparecen. */
    public static Map<String, Integer> cost(List<ItemStack> ingredients) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (ItemStack s : ingredients) {
            if (s == null || s.isEmpty()) continue;
            out.merge(ingredientKey(s), Math.max(1, s.getCount()), Integer::sum);
        }
        return out;
    }

    /** "4× iron_ingot, 1× #forge:rods" - sin namespace minecraft para que entre en una linea. */
    public static String describeCost(Map<String, Integer> cost) {
        List<String> parts = new ArrayList<>();
        cost.forEach((k, n) -> parts.add(n + "× " + (k.startsWith("minecraft:") ? k.substring(10) : k)));
        return String.join(", ", parts);
    }

    /**
     * Id legible a partir del resultado ("create:brass_ingot" -> "brass_ingot"),
     * con sufijo _2, _3... si ya esta en uso. Mejor que un id aleatorio: en el
     * script y en JEI se entiende que receta es.
     */
    public static String suggestId(String outputId, Predicate<String> inUse) {
        String base = outputId == null || outputId.isBlank() ? "recipe"
                : outputId.substring(outputId.indexOf(':') + 1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_");
        String id = base;
        for (int n = 2; inUse.test(id); n++) id = base + "_" + n;
        return id;
    }

    /**
     * La receta que deshace esta: si todos los ingredientes son el MISMO item
     * (tipico bloque de almacenamiento: 9 lingotes -> 1 bloque) y la salida
     * es 1, devuelve un borrador shapeless "salida -> N ingredientes". null si
     * no aplica (mezcla de items, tags, o salida multiple).
     */
    public static RecipeData reverseOf(RecipeData d) {
        if (d.output == null || d.output.isEmpty() || d.output.getCount() != 1) return null;
        List<ItemStack> ings = new ArrayList<>();
        if (d.type == RecipeData.RecipeType.VANILLA_SHAPED) ings.addAll(d.ingredients);
        else if (d.type == RecipeData.RecipeType.VANILLA_SHAPELESS) ings.addAll(d.shapelessIngredients);
        else return null;
        ItemStack first = null;
        int count = 0;
        for (ItemStack s : ings) {
            if (s == null || s.isEmpty()) continue;
            if (TagIngredient.isTag(s)) return null;
            if (first == null) first = s;
            else if (!ItemStack.isSameItemSameTags(first, s)) return null;
            count++;
        }
        if (first == null || count < 2 || count > 64) return null;
        RecipeData r = new RecipeData();
        r.type = RecipeData.RecipeType.VANILLA_SHAPELESS;
        r.shapelessIngredients = new ArrayList<>(List.of(d.output.copyWithCount(1)));
        r.output = first.copyWithCount(count);
        r.category = d.category;
        return r;
    }

    /** Tipos de coccion vanilla y su tiempo por defecto (ticks), como en las recetas vanilla. */
    public static final Map<String, Integer> COOKING_TYPES = new LinkedHashMap<>();
    static {
        COOKING_TYPES.put("minecraft:smelting", 200);
        COOKING_TYPES.put("minecraft:blasting", 100);
        COOKING_TYPES.put("minecraft:smoking", 100);
        COOKING_TYPES.put("minecraft:campfire_cooking", 600);
    }

    public static boolean isCooking(RecipeData d) {
        return d.type == RecipeData.RecipeType.CUSTOM_JSON && d.customTypeId != null && COOKING_TYPES.containsKey(d.customTypeId);
    }

    /**
     * Copia de una receta de horno para otro tipo de coccion, con el
     * {@code cookingtime} escalado en la misma proporcion que vanilla (el alto
     * horno tarda la mitad). La experiencia y el resto del JSON extra se
     * conservan. null si {@code d} no es de coccion o ya es de ese tipo.
     */
    public static RecipeData cookingVariant(RecipeData d, String targetType) {
        if (!isCooking(d) || !COOKING_TYPES.containsKey(targetType) || targetType.equals(d.customTypeId)) return null;
        JsonObject extra = new JsonObject();
        if (d.customExtraJson != null && !d.customExtraJson.isBlank()) {
            String raw = d.customExtraJson.trim();
            try {
                extra = JsonParser.parseString(raw.startsWith("{") ? raw : "{" + raw + "}").getAsJsonObject();
            } catch (Exception ex) {
                return null;
            }
        }
        int srcDefault = COOKING_TYPES.get(d.customTypeId);
        int dstDefault = COOKING_TYPES.get(targetType);
        int srcTime = extra.has("cookingtime") ? extra.get("cookingtime").getAsInt() : srcDefault;
        extra.addProperty("cookingtime", Math.max(1, Math.round(srcTime * (float) dstDefault / srcDefault)));
        RecipeData r = new RecipeData();
        r.type = RecipeData.RecipeType.CUSTOM_JSON;
        r.customTypeId = targetType;
        r.customExtraJson = extra.toString();
        r.shapelessIngredients = new ArrayList<>();
        for (ItemStack s : d.shapelessIngredients) r.shapelessIngredients.add(s.copy());
        r.output = d.output.copy();
        r.category = d.category;
        return r;
    }
}
