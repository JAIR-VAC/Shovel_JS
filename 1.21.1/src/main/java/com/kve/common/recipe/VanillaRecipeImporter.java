package com.kve.common.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * "Pegar receta": convierte el JSON de una receta vanilla de
 * datapack (formato 1.20.1: {@code key}/{@code ingredients} con
 * {@code {"item"}}/{@code {"tag"}}, {@code result} objeto o string) en un
 * borrador del editor. Sirve para copiar recetas de una wiki, de otro
 * datapack o de un jar y ajustarlas. Tipos: crafting_shaped,
 * crafting_shapeless, smelting/blasting/smoking/campfire_cooking y
 * stonecutting. Un tag entra como ingrediente-tag (ver {@link TagIngredient}).
 */
public final class VanillaRecipeImporter {
    private VanillaRecipeImporter() {}

    public record Result(RecipeData recipe, List<String> warnings) {}

    /** @throws IllegalArgumentException con un mensaje legible si el JSON no sirve */
    public static Result parse(String json) {
        JsonObject o;
        try {
            o = JsonParser.parseString(json.trim()).getAsJsonObject();
        } catch (Exception ex) {
            throw new IllegalArgumentException("El texto no es un JSON de receta valido");
        }
        String type = o.has("type") ? o.get("type").getAsString() : "";
        if (!type.contains(":")) type = "minecraft:" + type;
        List<String> warnings = new ArrayList<>();
        RecipeData r = new RecipeData();
        switch (type) {
            case "minecraft:crafting_shaped" -> shaped(o, r, warnings);
            case "minecraft:crafting_shapeless" -> {
                r.type = RecipeData.RecipeType.VANILLA_SHAPELESS;
                if (!o.has("ingredients")) throw new IllegalArgumentException("Falta 'ingredients'");
                for (JsonElement el : o.getAsJsonArray("ingredients")) r.shapelessIngredients.add(ingredient(el, warnings));
                if (r.shapelessIngredients.size() > 9) warnings.add("Mas de 9 ingredientes: la mesa vanilla no la acepta");
            }
            case "minecraft:smelting", "minecraft:blasting", "minecraft:smoking", "minecraft:campfire_cooking" -> {
                r.type = RecipeData.RecipeType.CUSTOM_JSON;
                r.customTypeId = type;
                r.shapelessIngredients.add(ingredient(required(o, "ingredient"), warnings));
                JsonObject extra = new JsonObject();
                if (o.has("cookingtime")) extra.add("cookingtime", o.get("cookingtime"));
                if (o.has("experience")) extra.add("experience", o.get("experience"));
                r.customExtraJson = extra.size() == 0 ? "" : extra.toString();
            }
            case "minecraft:stonecutting" -> {
                r.type = RecipeData.RecipeType.CUSTOM_JSON;
                r.customTypeId = type;
                r.shapelessIngredients.add(ingredient(required(o, "ingredient"), warnings));
            }
            default -> throw new IllegalArgumentException("Tipo no soportado para pegar: " + type
                    + " (solo mesa de crafteo, hornos y cortapiedras)");
        }
        r.output = result(required(o, "result"), o, warnings);
        return new Result(r, warnings);
    }

    private static void shaped(JsonObject o, RecipeData r, List<String> warnings) {
        r.type = RecipeData.RecipeType.VANILLA_SHAPED;
        JsonArray pattern = required(o, "pattern").getAsJsonArray();
        JsonObject key = required(o, "key").getAsJsonObject();
        int h = pattern.size();
        int w = 0;
        for (JsonElement row : pattern) w = Math.max(w, row.getAsString().length());
        if (w == 0 || h == 0 || w > 3 || h > 3) throw new IllegalArgumentException("El patron debe ser de hasta 3x3");
        r.gridWidth = w;
        r.gridHeight = h;
        r.ingredients = new ArrayList<>();
        for (JsonElement rowEl : pattern) {
            String row = rowEl.getAsString();
            for (int c = 0; c < w; c++) {
                char ch = c < row.length() ? row.charAt(c) : ' ';
                if (ch == ' ') { r.ingredients.add(ItemStack.EMPTY); continue; }
                JsonElement ing = key.get(String.valueOf(ch));
                if (ing == null) throw new IllegalArgumentException("El simbolo '" + ch + "' no esta en 'key'");
                r.ingredients.add(ingredient(ing, warnings));
            }
        }
    }

    private static JsonElement required(JsonObject o, String key) {
        if (!o.has(key)) throw new IllegalArgumentException("Falta '" + key + "'");
        return o.get(key);
    }

    static ItemStack ingredient(JsonElement el, List<String> warnings) {
        if (el.isJsonArray()) {
            JsonArray a = el.getAsJsonArray();
            if (a.isEmpty()) throw new IllegalArgumentException("Ingrediente vacio");
            if (a.size() > 1) warnings.add("Un ingrediente tenia varias opciones; se uso la primera");
            return ingredient(a.get(0), warnings);
        }
        JsonObject o = el.getAsJsonObject();
        if (o.has("tag")) return TagIngredient.of(o.get("tag").getAsString(), null);
        if (o.has("item")) return new ItemStack(item(o.get("item").getAsString()));
        throw new IllegalArgumentException("Ingrediente sin 'item' ni 'tag': " + o);
    }

    private static ItemStack result(JsonElement el, JsonObject recipe, List<String> warnings) {
        ItemStack out;
        if (el.isJsonPrimitive()) {
            out = new ItemStack(item(el.getAsString()));
        } else {
            JsonObject o = el.getAsJsonObject();
            out = new ItemStack(item(o.get("item").getAsString()));
            if (o.has("count")) out.setCount(Math.max(1, o.get("count").getAsInt()));
            if (o.has("nbt")) {
                try {
                    JsonElement nbt = o.get("nbt");
                    com.kve.common.util.ItemExtraDataCodec.applyFromSnbt(out, nbt.isJsonPrimitive() ? nbt.getAsString() : nbt.toString());
                } catch (Exception ex) {
                    warnings.add("No se pudo leer el NBT del resultado");
                }
            }
        }
        if (recipe.has("count")) out.setCount(Math.max(1, recipe.get("count").getAsInt()));
        return out;
    }

    private static Item item(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Item it = rl == null ? Items.AIR : BuiltInRegistries.ITEM.get(rl);
        if (it == Items.AIR) throw new IllegalArgumentException("El item '" + id + "' no existe en esta instancia");
        return it;
    }
}
