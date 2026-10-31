package com.kve.common.worldgen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Set;

/**
 * JSON para probar una regla de world gen en el mundo en vivo
 * (sin reiniciar). Un placed feature normal referencia su configured feature
 * por id y lleva un filtro de bioma; para la prueba ese id todavia no existe
 * en el registro (los registros de worldgen solo se cargan al entrar al mundo)
 * y el chunk del jugador puede no ser de los biomas de la regla. Logica pura,
 * sin Minecraft: la usan los exportadores y la testean sin juego.
 */
public final class WorldGenTestJson {

    private WorldGenTestJson() {}

    /** Modificadores que se quitan: el filtro de bioma (lo explicado arriba) y la rareza (una veta "cada N chunks" casi nunca saldria en la prueba). */
    static final Set<String> STRIPPED = Set.of("minecraft:biome", "minecraft:rarity_filter");

    /** Copia de {@code placed} con {@code configured} dentro y sin los modificadores de {@link #STRIPPED}. */
    public static JsonObject inline(JsonObject placed, JsonObject configured) {
        JsonObject out = placed.deepCopy();
        out.add("feature", configured.deepCopy());
        JsonArray kept = new JsonArray();
        if (out.has("placement") && out.get("placement").isJsonArray()) {
            for (JsonElement m : out.getAsJsonArray("placement")) {
                String type = m.isJsonObject() && m.getAsJsonObject().has("type") ? m.getAsJsonObject().get("type").getAsString() : "";
                if (!STRIPPED.contains(type)) kept.add(m.deepCopy());
            }
        }
        out.add("placement", kept);
        return out;
    }
}
