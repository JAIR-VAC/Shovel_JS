package com.kve.common.kubejs;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kve.CraftEditorMod;
import com.kve.common.recipe.RecipeData;
import com.kve.common.util.SafeFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Valores de EMC de ProjectE sin el addon kubejs-projecte. {@code projecte:emc_value} no es una receta: ProjectE
 * lee sus valores de archivos de datapack {@code data/<ns>/pe_custom_conversions/*.json}
 * (CustomConversionMapper, "Loads json files within datapacks"). La carpeta
 * {@code kubejs/data} es un datapack, asi que KVE escribe un archivo ahi.
 *
 * <p>Formato verificado con javap en ProjectE-1.20.1-PE1.0.1
 * (FixedValuesDeserializer): {@code {"values": {"before": {id: emc}, "after": {id: emc}}}}.
 * {@code before} fija el valor antes de propagar por recetas (los items
 * crafteados con el heredan EMC); {@code after} lo fuerza al final. Las claves
 * pueden ser un item o un tag ({@code #forge:ingots/iron}); el valor minimo es 1.
 */
public final class ProjectEEmcExporter {
    private ProjectEEmcExporter() {}

    public static final String TYPE = "projecte:emc_value";

    /** Archivo que escribe KVE (namespace propio: no pisa los de ProjectE ni los del modpacker). */
    public static Path file() {
        return Paths.get("kubejs", "data", "shovel_js", "pe_custom_conversions", "shovel_js_emc.json");
    }

    public static boolean handles(RecipeData r) {
        return r != null && TYPE.equals(r.customTypeId);
    }

    /** Un valor: item/tag, EMC, y si va "antes" de la propagacion. null + motivo si falta algo. */
    record Entry(String key, long emc, boolean before) {}

    static Entry entryOf(RecipeData r, StringBuilder whyNot) {
        JsonObject extra = KubeJSExporter.parseExtraJsonSafe(r.customExtraJson);
        String item = str(extra, "item");
        if (item.isEmpty() && r.output != null && !r.output.isEmpty()) {
            String id = KubeJSExporter.itemId(r.output);
            if (id != null) item = id;
        }
        if (!item.matches("#?[a-z0-9_.-]+:[a-z0-9_./-]+")) { whyNot.append("falta el item (o no es un id valido)"); return null; }
        long emc;
        try {
            emc = Long.parseLong(str(extra, "emc").trim());
        } catch (NumberFormatException ex) {
            whyNot.append("el EMC no es un numero"); return null;
        }
        if (emc < 1) { whyNot.append("ProjectE exige EMC >= 1"); return null; }
        boolean before = Boolean.parseBoolean(str(extra, "setBefore"));
        return new Entry(item, emc, before);
    }

    private static String str(JsonObject o, String k) {
        if (o == null) return "";
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? "" : (e.isJsonPrimitive() ? e.getAsString() : e.toString());
    }

    static String jsComment(RecipeData r) {
        StringBuilder why = new StringBuilder();
        Entry e = entryOf(r, why);
        if (e == null) {
            return "  // ==== NO EXPORTADO: " + r.recipeId + " [" + TYPE + "] - " + why + "\n";
        }
        return "  // " + r.recipeId + " [" + TYPE + "] " + e.key() + " = " + e.emc() + " EMC ("
                + (e.before() ? "before" : "after") + ")\n"
                + "  // -> se escribe en " + file().toString().replace('\\', '/')
                + " (formato nativo de ProjectE, sin addon de KubeJS; se aplica con /reload)\n";
    }

    static JsonObject buildJson(List<RecipeData> recipes) {
        Map<String, Long> before = new TreeMap<>(), after = new TreeMap<>();
        for (RecipeData r : recipes) {
            if (!handles(r) || !r.enabled) continue;
            Entry e = entryOf(r, new StringBuilder());
            if (e == null) continue;
            (e.before() ? before : after).put(e.key(), e.emc());
        }
        if (before.isEmpty() && after.isEmpty()) return null;
        JsonObject values = new JsonObject();
        if (!before.isEmpty()) {
            JsonObject b = new JsonObject();
            before.forEach(b::addProperty);
            values.add("before", b);
        }
        if (!after.isEmpty()) {
            JsonObject a = new JsonObject();
            after.forEach(a::addProperty);
            values.add("after", a);
        }
        JsonObject root = new JsonObject();
        root.addProperty("comment", "Shovel JS - valores de EMC (AUTO-GENERATED, no editar a mano)");
        root.add("values", values);
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
