package com.kve.common.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kve.common.recipetype.RecipeTypeDescriptor;
import com.kve.common.recipetype.RecipeTypeDescriptorRegistry;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

class RecipeLoadScanTest {

    private static final Set<String> IGNORED_KEYS = Set.of("type", "conditions", "neoforge:conditions", "fabric:load_conditions", "group", "category", "show_notification");

    @Test
    void scan() throws Exception {
        String dir = System.getenv("KVE_RECIPE_SCAN_DIR");
        Assumptions.assumeTrue(dir != null && !dir.isBlank(), "KVE_RECIPE_SCAN_DIR no definido");
        RecipeTypeDescriptorRegistry.reload();

        Map<String, int[]> perType = new TreeMap<>();
        Map<String, Map<String, Integer>> problems = new TreeMap<>();
        Map<String, String> example = new HashMap<>();

        try (var files = Files.list(Paths.get(dir))) {
            for (Path jar : files.filter(p -> p.toString().endsWith(".jar")).toList()) {
                try (ZipFile zip = new ZipFile(jar.toFile())) {
                    for (ZipEntry e : Collections.list(zip.entries())) {
                        String n = e.getName();
                        if (!n.startsWith("data/") || !n.contains("/recipes/") || !n.endsWith(".json")) continue;
                        JsonObject root;
                        try (var r = new InputStreamReader(zip.getInputStream(e), StandardCharsets.UTF_8)) {
                            JsonElement el = JsonParser.parseReader(r);
                            if (!el.isJsonObject()) continue;
                            root = el.getAsJsonObject();
                        } catch (Exception ex) { continue; }
                        if (!root.has("type") || !root.get("type").isJsonPrimitive()) continue;
                        String type = root.get("type").getAsString();
                        RecipeTypeDescriptor d = RecipeTypeDescriptorRegistry.find(type).orElse(null);
                        if (d == null || d.rawJson == null) continue;
                        List<String> found = check(root, d);
                        int[] c = perType.computeIfAbsent(type, k -> new int[2]);
                        c[0]++;
                        if (!found.isEmpty()) c[1]++;
                        for (String p : found) {
                            problems.computeIfAbsent(type, k -> new TreeMap<>()).merge(p, 1, Integer::sum);
                            example.putIfAbsent(type + "|" + p, jar.getFileName() + "!" + n);
                        }
                    }
                }
            }
        }

        StringBuilder sb = new StringBuilder("Escaneo de carga de recetas\n\n");
        for (var en : perType.entrySet()) {
            sb.append(en.getKey()).append(": ").append(en.getValue()[0]).append(" recetas, ")
              .append(en.getValue()[1]).append(" con problemas\n");
            for (var p : problems.getOrDefault(en.getKey(), Map.of()).entrySet()) {
                sb.append("    ").append(p.getValue()).append(" x ").append(p.getKey())
                  .append("   (ej: ").append(example.get(en.getKey() + "|" + p.getKey())).append(")\n");
            }
        }
        Path out = Paths.get("build", "recipe-load-scan.txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, sb.toString());
        System.out.println(sb);
    }

    static List<String> check(JsonObject root, RecipeTypeDescriptor d) {
        List<String> out = new ArrayList<>();
        JsonObject extra = RawRecipeInputs.extraFields(root, d);
        Set<String> declared = new HashSet<>();
        for (RecipeTypeDescriptor.ExtraField f : d.extraFields) declared.add(f.key);

        for (String k : root.keySet()) {
            if (IGNORED_KEYS.contains(k)) continue;
            if (!d.rawJson.has(k) && !declared.contains(k)) out.add("clave desconocida '" + k + "' (se pierde al guardar)");
        }
        walk(d.rawJson, root, extra, "", out);
        int expected = countInputs(root, d);
        int read = RawRecipeInputs.inputs(root, d).size();
        if (expected >= 0 && read < expected) out.add("entradas leidas " + read + " de " + expected);
        return out;
    }

    private static void walk(JsonObject tpl, JsonObject root, JsonObject extra, String path, List<String> out) {
        for (Map.Entry<String, JsonElement> en : tpl.entrySet()) {
            JsonElement tv = en.getValue();
            JsonElement rv = root.get(en.getKey());
            if (rv == null || rv.isJsonNull()) continue;
            if (tv.isJsonObject()) {
                if (rv.isJsonObject()) walk(tv.getAsJsonObject(), rv.getAsJsonObject(), extra, path + en.getKey() + ".", out);
                else out.add("'" + path + en.getKey() + "' no es objeto en el JSON real");
                continue;
            }
            if (!tv.isJsonPrimitive() || !tv.getAsJsonPrimitive().isString()) continue;
            String s = tv.getAsString().trim();
            if (!s.startsWith("$field:")) continue;
            String key = s.split("\\|\\|")[0].trim().split(":")[1];
            if (!extra.has(key)) out.add("campo '" + key + "' (" + path + en.getKey() + ") no se carga");
        }
    }

    /** Entradas item/tag del JSON segun las claves $inputs/$slot de input del template; -1 si no se puede saber. */
    private static int countInputs(JsonObject root, RecipeTypeDescriptor d) {
        int n = 0;
        boolean any = false;
        for (Map.Entry<String, JsonElement> en : d.rawJson.entrySet()) {
            if (!en.getValue().isJsonPrimitive()) continue;
            String s = en.getValue().getAsString().trim();
            boolean inputs = s.startsWith("$inputs");
            boolean inSlot = s.startsWith("$slot:") && isInputSlot(d, s.split(":")[1]);
            if (!inputs && !inSlot) continue;
            JsonElement v = root.get(en.getKey());
            if (v == null) continue;
            any = true;
            if (v.isJsonArray() && inputs) {
                for (JsonElement x : (JsonArray) v) if (isItemish(x)) n++;
            } else if (isItemish(v)) n++;
        }
        return any ? n : -1;
    }

    private static boolean isInputSlot(RecipeTypeDescriptor d, String id) {
        if (!d.hasSlotLayout()) return false;
        for (RecipeTypeDescriptor.SlotDef s : d.slotLayout.slots) {
            if (id.equals(s.id)) return "input".equalsIgnoreCase(s.role) && (s.type == null || !s.type.equalsIgnoreCase("fluid"));
        }
        return false;
    }

    private static boolean isItemish(JsonElement e) {
        if (e == null) return false;
        if (e.isJsonArray()) return ((JsonArray) e).size() > 0 && isItemish(((JsonArray) e).get(0));
        if (e.isJsonPrimitive()) return e.getAsJsonPrimitive().isString();
        if (!e.isJsonObject()) return false;
        JsonObject o = e.getAsJsonObject();
        if (o.has("ingredient")) return isItemish(o.get("ingredient"));
        return o.has("item") || o.has("tag");
    }
}
