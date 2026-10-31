package com.kve.common.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kve.common.recipetype.RecipeTypeDescriptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RawRecipeInputs {
    private RawRecipeInputs() {}

    private static final String[] LIST_KEYS = {"ingredients", "inputs", "inputItems", "pedestalItems"};
    private static final String[] SINGLE_KEYS = {
            "ingredient", "input", "main_input", "mainInput", "extra_input", "extraInput", "item_input", "itemInput",
    };

    /**
     * Elementos JSON de ingrediente (cada uno un {@code {"item"|"tag"}}, o una
     * lista de alternativas) en orden de slot. Nunca null.
     */
    public static List<JsonElement> inputs(JsonObject root, RecipeTypeDescriptor descriptor) {
        List<JsonElement> out = new ArrayList<>();
        if (root == null) return out;
        if (descriptor != null && descriptor.hasRawJson() && descriptor.hasSlotLayout()) {
            fromTemplate(root, descriptor, out);
            if (!out.isEmpty()) return out;
        }
        if (root.has("key") && root.get("key").isJsonObject()) {
            for (Map.Entry<String, JsonElement> en : root.getAsJsonObject("key").entrySet()) out.add(en.getValue());
        }
        for (String k : LIST_KEYS) addAll(root.get(k), out, true);
        for (String k : SINGLE_KEYS) addAll(root.get(k), out, false);
        return out;
    }

    public static JsonObject extraFields(JsonObject root, RecipeTypeDescriptor d) {
        JsonObject extra = new JsonObject();
        if (root == null || d == null || d.extraFields == null || d.extraFields.isEmpty()) return extra;
        for (RecipeTypeDescriptor.ExtraField f : d.extraFields) {
            if (f.key != null && root.has(f.key)) {
                JsonElement v = plain(root.get(f.key), "");
                if (v != null) extra.add(f.key, v);
            }
        }
        if (d.rawJson != null) reverse(d.rawJson, root, extra);
        return extra;
    }

    private static void reverse(JsonObject template, JsonObject root, JsonObject extra) {
        for (Map.Entry<String, JsonElement> en : template.entrySet()) {
            JsonElement tv = en.getValue();
            JsonElement rv = root.get(en.getKey());
            if (rv == null || rv.isJsonNull() || tv == null) continue;
            if (tv.isJsonObject() && rv.isJsonObject()) {
                reverse(tv.getAsJsonObject(), rv.getAsJsonObject(), extra);
                continue;
            }
            if (!tv.isJsonPrimitive() || !tv.getAsJsonPrimitive().isString()) continue;
            for (String alt : tv.getAsString().split("\\|\\|")) {
                String a = alt.trim();
                if (!a.startsWith("$field:")) continue;
                String[] p = a.split(":");
                if (p.length < 2 || p[1].isEmpty() || extra.has(p[1])) continue;
                JsonElement v = plain(rv, p.length > 2 ? p[2] : "");
                if (v != null) extra.add(p[1], v);
                if (p.length > 4 && !p[4].isEmpty() && !extra.has(p[4]) && rv.isJsonObject()
                        && rv.getAsJsonObject().has("amount") && rv.getAsJsonObject().get("amount").isJsonPrimitive()) {
                    extra.add(p[4], rv.getAsJsonObject().get("amount"));
                }
            }
        }
    }

    static JsonElement plain(JsonElement v, String fmt) {
        JsonElement simple = plainSimple(v, fmt);
        if (simple != null || v == null || v.isJsonNull() || v.isJsonPrimitive()) return simple;
        if (v.isJsonArray() && v.getAsJsonArray().isEmpty()) return null;
        return new com.google.gson.JsonPrimitive(v.toString());
    }

    private static JsonElement plainSimple(JsonElement v, String fmt) {
        if (v == null || v.isJsonNull()) return null;
        if (v.isJsonPrimitive()) return v;
        if (v.isJsonArray()) {
            JsonArray a = v.getAsJsonArray();
            if ("list".equals(fmt)) {
                StringBuilder sb = new StringBuilder();
                for (JsonElement e : a) {
                    if (!e.isJsonPrimitive()) continue;
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(e.getAsString());
                }
                return new com.google.gson.JsonPrimitive(sb.toString());
            }
            // Una sola alternativa = ese valor; varias no se pueden reducir a un id (ver plain()).
            return a.size() == 1 ? plainSimple(a.get(0), fmt) : null;
        }
        JsonObject o = v.getAsJsonObject();
        for (String k : new String[]{"item", "block", "fluid", "gas", "infuse_type", "pigment", "slurry", "chemical", "id"}) {
            if (o.has(k) && o.get(k).isJsonPrimitive()) return o.get(k);
            if (o.has(k) && o.get(k).isJsonObject()) return plainSimple(o.get(k), fmt);
        }
        for (String k : new String[]{"tag", "fluidTag"}) {
            if (o.has(k) && o.get(k).isJsonPrimitive()) return new com.google.gson.JsonPrimitive("#" + o.get(k).getAsString());
        }
        return null;
    }

    private static void fromTemplate(JsonObject root, RecipeTypeDescriptor d, List<JsonElement> out) {
        List<JsonElement> pool = new ArrayList<>();
        String inputsKey = templateKeyStartingWith(d.rawJson, "$inputs");
        if (inputsKey != null) addAll(root.get(inputsKey), pool, true);
        List<JsonElement> tail = new ArrayList<>();
        for (RecipeTypeDescriptor.SlotDef slot : d.slotLayout.slots) {
            if (!"input".equals(slot.role == null ? "" : slot.role.toLowerCase(Locale.ROOT))) continue;
            if (slot.type != null && slot.type.equalsIgnoreCase("fluid")) continue;
            String key = templateKeyFor(d.rawJson, slot.id);
            if (slot.repeatable) {
                if (key != null) addAll(root.get(key), tail, true);
                continue;
            }
            if (key != null) {
                addAll(root.get(key), out, isListKey(key));
            } else if (!pool.isEmpty()) {
                out.add(pool.remove(0));
            }
        }
        out.addAll(tail);
        out.addAll(pool);
    }

    private static final String[] FLUID_KEYS = {"fluid", "fluidTag", "FluidName", "gas", "slurry", "pigment", "infuse_type", "chemical"};

    public static boolean isFluidJson(JsonElement el) {
        if (el != null && el.isJsonArray() && el.getAsJsonArray().size() > 0) return isFluidJson(el.getAsJsonArray().get(0));
        if (el == null || !el.isJsonObject()) return false;
        JsonObject o = el.getAsJsonObject();
        for (String k : FLUID_KEYS) if (o.has(k)) return true;
        return o.has("amount") && !o.has("count") && !o.has("ingredient") && !o.has("item") && (o.has("id") || o.has("tag"));
    }

    public static List<List<JsonElement>> slotElements(JsonObject root, RecipeTypeDescriptor descriptor, boolean outputs) {
        List<List<JsonElement>> out = new ArrayList<>();
        if (root == null || descriptor == null || !descriptor.hasRawJson() || !descriptor.hasSlotLayout()) return out;
        List<RecipeTypeDescriptor.SlotDef> defs = new ArrayList<>();
        for (RecipeTypeDescriptor.SlotDef s : descriptor.slotLayout.slots) if (s.isOutput() == outputs) defs.add(s);

        List<JsonElement> pool = new ArrayList<>();
        String poolKey = templateKeyStartingWith(descriptor.rawJson, outputs ? "$outputs" : "$inputs");
        if (poolKey != null) addAll(root.get(poolKey), pool, true);

        for (RecipeTypeDescriptor.SlotDef s : defs) {
            List<JsonElement> mine = new ArrayList<>();
            String key = templateKeyFor(descriptor.rawJson, s.id);
            if (key != null) {
                addAll(root.get(key), mine, s.repeatable || isListKey(key));
            }
            out.add(mine);
        }
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < defs.size(); i++) {
                RecipeTypeDescriptor.SlotDef s = defs.get(i);
                if (s.repeatable != (pass == 1) || !out.get(i).isEmpty()) continue;
                if (templateKeyFor(descriptor.rawJson, s.id) != null) continue;
                boolean fluidSlot = s.type != null && s.type.equalsIgnoreCase("fluid");
                for (int p = 0; p < pool.size(); ) {
                    if (isFluidJson(pool.get(p)) == fluidSlot) {
                        out.get(i).add(pool.remove(p));
                        if (!s.repeatable) break;
                    } else {
                        p++;
                    }
                }
            }
        }
        return out;
    }

    private static String templateKeyStartingWith(JsonObject template, String prefix) {
        if (template == null) return null;
        for (Map.Entry<String, JsonElement> en : template.entrySet()) {
            JsonElement v = en.getValue();
            if (v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isString() && v.getAsString().trim().startsWith(prefix)) {
                return en.getKey();
            }
        }
        return null;
    }

    static String templateKeyFor(JsonObject template, String slotId) {
        if (template == null || slotId == null || slotId.isEmpty()) return null;
        String needle = "$slot:" + slotId + ":";
        String needleEnd = "$slot:" + slotId;
        for (Map.Entry<String, JsonElement> en : template.entrySet()) {
            JsonElement v = en.getValue();
            if (v == null || !v.isJsonPrimitive() || !v.getAsJsonPrimitive().isString()) continue;
            String s = v.getAsString();
            if (s.contains(needle) || s.trim().equals(needleEnd)) return en.getKey();
        }
        return null;
    }

    private static boolean isListKey(String key) {
        for (String k : LIST_KEYS) if (k.equals(key)) return true;
        return false;
    }

    private static void addAll(JsonElement el, List<JsonElement> out, boolean listMeansSlots) {
        if (el == null || el.isJsonNull()) return;
        if (el.isJsonArray() && !listMeansSlots) {
            if (el.getAsJsonArray().size() > 0) out.add(el);
        } else if (el.isJsonArray()) {
            JsonArray arr = el.getAsJsonArray();
            for (JsonElement sub : arr) if (sub != null && !sub.isJsonNull()) out.add(sub);
        } else if (el.isJsonObject()) {
            out.add(el);
        } else if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
            JsonObject o = new JsonObject();
            String s = el.getAsString();
            if (s.startsWith("#")) o.addProperty("tag", s.substring(1)); else o.addProperty("item", s);
            out.add(o);
        }
    }
}
