package com.kve.client.recipe;

import com.kve.client.gui.SearchFilter;
import com.kve.common.recipe.RecipeData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

public record RecipeListFilter(boolean favoritesOnly, String category, String text,
                               String type, String mod, String out, Boolean enabled) {

    /** Orden de la lista. DEFAULT = orden en que se crearon. */
    public enum Sort { DEFAULT, NAME, OUTPUT, TYPE }

    public static RecipeListFilter parse(String query) {
        boolean favOnly = false;
        String cat = null, type = null, mod = null, out = null;
        Boolean enabled = null;
        StringBuilder remaining = new StringBuilder();
        for (String tok : (query == null ? "" : query).trim().split("\\s+")) {
            String low = tok.toLowerCase(Locale.ROOT);
            if (low.equals("!fav")) favOnly = true;
            else if (low.startsWith("cat:")) cat = tok.substring(4);
            else if (low.startsWith("type:")) type = low.substring(5);
            else if (low.startsWith("mod:")) mod = low.substring(4);
            else if (low.startsWith("out:")) out = low.substring(4);
            else if (low.equals("is:on")) enabled = true;
            else if (low.equals("is:off")) enabled = false;
            else if (!tok.isEmpty()) {
                if (remaining.length() > 0) remaining.append(' ');
                remaining.append(tok);
            }
        }
        return new RecipeListFilter(favOnly, cat, remaining.toString(), type, mod, out, enabled);
    }

    public boolean matches(RecipeData r, Predicate<String> isFavorite) {
        if (!SearchFilter.matches(r.recipeId, text)) return false;
        if (favoritesOnly && !isFavorite.test(r.recipeId)) return false;
        if (category != null && !category.isEmpty()
                && !category.equalsIgnoreCase(r.category == null ? "" : r.category)) return false;
        if (enabled != null && r.enabled != enabled) return false;
        if (type != null && !type.isEmpty() && !typeLabel(r).contains(type)) return false;
        String outId = outputId(r);
        if (mod != null && !mod.isEmpty() && !outId.startsWith(mod + ":")) return false;
        return out == null || out.isEmpty() || outId.contains(out);
    }

    static String typeLabel(RecipeData r) {
        if ((r.type == RecipeData.RecipeType.CUSTOM_JSON || r.type == RecipeData.RecipeType.CUSTOM_MACHINE)
                && r.customTypeId != null && !r.customTypeId.isEmpty()) {
            return r.customTypeId.toLowerCase(Locale.ROOT);
        }
        return r.type.name().toLowerCase(Locale.ROOT).replace("vanilla_", "");
    }

    static String outputId(RecipeData r) {
        if (r.output == null || r.output.isEmpty()) return "";
        ResourceLocation k = BuiltInRegistries.ITEM.getKey(r.output.getItem());
        return k == null ? "" : k.toString();
    }

    /** Recetas que pasan el filtro, favoritas primero; cada grupo ordenado por {@code sort} (estable). */
    public List<RecipeData> apply(Iterable<RecipeData> recipes, Predicate<String> isFavorite, Sort sort) {
        List<RecipeData> favs = new ArrayList<>();
        List<RecipeData> rest = new ArrayList<>();
        for (RecipeData r : recipes) {
            if (!matches(r, isFavorite)) continue;
            (isFavorite.test(r.recipeId) ? favs : rest).add(r);
        }
        Comparator<RecipeData> cmp = switch (sort) {
            case DEFAULT -> null;
            case NAME -> Comparator.comparing(r -> r.recipeId == null ? "" : r.recipeId.toLowerCase(Locale.ROOT));
            case OUTPUT -> Comparator.comparing(RecipeListFilter::outputId);
            case TYPE -> Comparator.comparing(RecipeListFilter::typeLabel);
        };
        if (cmp != null) { favs.sort(cmp); rest.sort(cmp); }
        favs.addAll(rest);
        return favs;
    }

    public List<RecipeData> apply(Iterable<RecipeData> recipes, Predicate<String> isFavorite) {
        return apply(recipes, isFavorite, Sort.DEFAULT);
    }
}
