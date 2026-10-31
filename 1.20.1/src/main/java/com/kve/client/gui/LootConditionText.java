package com.kve.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kve.common.Lang;

import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Texto corto y legible de condiciones de loot (de una entrada o
 * las extra de un pool) y las condiciones que el panel de la entrada deja
 * agregar. Lo que no reconoce se muestra por su id, nunca se inventa.
 * {@code tr} traduce una clave de lang: la pantalla pasa {@link Lang#get},
 * los tests un mapa propio.
 */
public final class LootConditionText {

    private LootConditionText() {}

    private static final String K = "gui.kve.loot.cond.";

    /** Una condicion que se puede agregar desde el panel: su JSON tal cual va al datapack. */
    public record Preset(String json) {
        public JsonObject create() { return JsonParser.parseString(json).getAsJsonObject(); }
    }

    private static final String SILK_TOUCH =
            "{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"enchantments\":"
            + "[{\"enchantment\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}";

    /** Formato 1.20 (lista "enchantments" en el predicado de match_tool). */
    public static final List<Preset> PRESETS = List.of(
            new Preset(SILK_TOUCH),
            new Preset("{\"condition\":\"minecraft:inverted\",\"term\":" + SILK_TOUCH + "}"),
            new Preset("{\"condition\":\"minecraft:survives_explosion\"}"),
            new Preset("{\"condition\":\"minecraft:killed_by_player\"}"),
            new Preset("{\"condition\":\"minecraft:random_chance\",\"chance\":0.5}"));

    /** Todas las condiciones de la lista, separadas por coma. */
    public static String shortText(JsonArray conds) {
        return shortText(conds, Lang::get);
    }

    public static String shortText(JsonArray conds, UnaryOperator<String> tr) {
        StringBuilder sb = new StringBuilder();
        if (conds == null) return "";
        for (JsonElement el : conds) {
            if (!el.isJsonObject()) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(one(el.getAsJsonObject(), tr));
        }
        return sb.toString();
    }

    public static String one(JsonObject c) {
        return one(c, Lang::get);
    }

    static String one(JsonObject c, UnaryOperator<String> tr) {
        String cond = c.has("condition") ? c.get("condition").getAsString() : "?";
        return switch (cond) {
            case "minecraft:survives_explosion" -> tr.apply(K + "survives_explosion");
            case "minecraft:random_chance" -> tr.apply(K + "random_chance") + " " + pct(c);
            case "minecraft:random_chance_with_looting" -> tr.apply(K + "random_chance_looting") + " " + pct(c);
            case "minecraft:match_tool" -> tr.apply(silkTouch(c) ? K + "silk_touch" : K + "match_tool");
            case "minecraft:killed_by_player" -> tr.apply(K + "killed_by_player");
            case "minecraft:entity_properties" -> tr.apply(c.toString().contains("is_on_fire") ? K + "on_fire" : K + "entity");
            case "minecraft:table_bonus" -> tr.apply(K + "table_bonus");
            case "minecraft:block_state_property" -> tr.apply(K + "block_state");
            case "minecraft:inverted" -> inverted(c, tr);
            case "minecraft:all_of" -> joined(c, " + ", tr);
            case "minecraft:any_of" -> joined(c, " / ", tr);
            default -> cond.replace("minecraft:", "");
        };
    }

    private static String inverted(JsonObject c, UnaryOperator<String> tr) {
        JsonObject term = c.has("term") && c.get("term").isJsonObject() ? c.getAsJsonObject("term") : null;
        if (term == null) return tr.apply(K + "not") + " ?";
        String inner = term.has("condition") ? term.get("condition").getAsString() : "";
        if (inner.equals("minecraft:match_tool") && silkTouch(term)) return tr.apply(K + "no_silk_touch");
        return tr.apply(K + "not") + " " + one(term, tr);
    }

    private static String joined(JsonObject c, String sep, UnaryOperator<String> tr) {
        if (!c.has("terms") || !c.get("terms").isJsonArray()) return "?";
        StringBuilder sb = new StringBuilder("(");
        for (JsonElement el : c.getAsJsonArray("terms")) {
            if (!el.isJsonObject()) continue;
            if (sb.length() > 1) sb.append(sep);
            sb.append(one(el.getAsJsonObject(), tr));
        }
        return sb.append(")").toString();
    }

    private static String pct(JsonObject c) {
        if (!c.has("chance") || !c.get("chance").isJsonPrimitive()) return "?";
        return Math.round(c.get("chance").getAsDouble() * 100) + "%";
    }

    private static boolean silkTouch(JsonObject c) {
        return c.toString().contains("minecraft:silk_touch");
    }
}
