package com.kve.common.kubejs;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kve.CraftEditorMod;
import com.kve.common.recipe.RecipeData;
import com.kve.common.util.SafeFiles;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Transmutacion de la Piedra Filosofal de ProjectE sin el addon kubejs-projecte
 * Igual que {@link ProjectEEmcExporter}: en 1.21.1 no es una
 * receta, ProjectE la lee de {@code data/<ns>/pe_world_transmutations/*.json}.
 *
 * <p>Formato verificado contra la rama mc1.21.1 de sinkillerj/ProjectE
 * (WorldTransmutationFile/WorldTransmutation y el datagen
 * {@code pe_world_transmutations/defaults.json}):
 * {@code {"comment", "transmutations": [{"origin": id, "result": id, "alt_result"?: id}]}}
 * - {@code alt_result} es lo que sale con Shift.
 */
public final class ProjectETransmutationExporter {
    private ProjectETransmutationExporter() {}

    public static final String TYPE = "projecte:world_transmutation";

    public static Path file() {
        return Paths.get("kubejs", "data", "shovel_js", "pe_world_transmutations", "shovel_js.json");
    }

    public static boolean handles(RecipeData r) {
        return r != null && TYPE.equals(r.customTypeId);
    }

    record Entry(String origin, String result, String alt) {}

    private static final String ID = "[a-z0-9_.-]+:[a-z0-9_./-]+";

    static Entry entryOf(RecipeData r, StringBuilder whyNot) {
        JsonObject extra = KubeJSExporter.parseExtraJsonSafe(r.customExtraJson);
        String from = str(extra, "from");
        String to = str(extra, "to");
        String alt = str(extra, "alt");
        if (from.isEmpty()) from = firstIngredient(r);
        if (to.isEmpty() && r.output != null && !r.output.isEmpty()) {
            String id = KubeJSExporter.itemId(r.output);
            if (id != null) to = id;
        }
        if (!from.matches(ID)) { whyNot.append("falta el bloque de origen (o no es un id valido)"); return null; }
        if (!to.matches(ID)) { whyNot.append("falta el bloque resultante (o no es un id valido)"); return null; }
        if (!alt.isEmpty() && !alt.matches(ID)) { whyNot.append("el resultado con Shift no es un id valido"); return null; }
        return new Entry(from, to, alt);
    }

    private static String firstIngredient(RecipeData r) {
        List<ItemStack> lists = r.shapelessIngredients != null && !r.shapelessIngredients.isEmpty() ? r.shapelessIngredients : r.ingredients;
        if (lists == null) return "";
        for (ItemStack s : lists) {
            if (s == null || s.isEmpty()) continue;
            String id = KubeJSExporter.itemId(s);
            return id == null ? "" : id;
        }
        return "";
    }

    private static String str(JsonObject o, String k) {
        if (o == null) return "";
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? "" : (e.isJsonPrimitive() ? e.getAsString().trim() : e.toString());
    }

    static String jsComment(RecipeData r) {
        StringBuilder why = new StringBuilder();
        Entry e = entryOf(r, why);
        if (e == null) return "  // ==== NO EXPORTADO: " + r.recipeId + " [" + TYPE + "] - " + why + "\n";
        return "  // " + r.recipeId + " [" + TYPE + "] " + e.origin() + " -> " + e.result()
                + (e.alt().isEmpty() ? "" : " (Shift: " + e.alt() + ")") + "\n"
                + "  // -> se escribe en " + file().toString().replace('\\', '/')
                + " (formato nativo de ProjectE, sin addon de KubeJS; se aplica con /reload)\n";
    }

    static JsonObject buildJson(List<RecipeData> recipes) {
        JsonArray arr = new JsonArray();
        for (RecipeData r : recipes) {
            if (!handles(r) || !r.enabled) continue;
            Entry e = entryOf(r, new StringBuilder());
            if (e == null) continue;
            JsonObject o = new JsonObject();
            if (!e.alt().isEmpty()) o.addProperty("alt_result", e.alt());
            o.addProperty("origin", e.origin());
            o.addProperty("result", e.result());
            arr.add(o);
        }
        if (arr.isEmpty()) return null;
        JsonObject root = new JsonObject();
        root.addProperty("comment", "Shovel JS - transmutaciones (AUTO-GENERATED, no editar a mano)");
        root.add("transmutations", arr);
        return root;
    }

    static void write(List<RecipeData> recipes) {
        Path f = file();
        try {
            JsonObject json = buildJson(recipes);
            if (json == null) {
                Files.deleteIfExists(f);
                return;
            }
            SafeFiles.writeAtomic(f, new GsonBuilder().setPrettyPrinting().create().toJson(json) + "\n");
        } catch (IOException ex) {
            CraftEditorMod.LOGGER.error("[Shovel JS] ProjectE: no se pudo escribir {}: {}", f, ex.toString());
        }
    }
}
