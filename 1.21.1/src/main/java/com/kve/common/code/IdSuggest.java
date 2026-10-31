package com.kve.common.code;

import java.util.Collection;

/**
 * Sugerencia "quisiste decir" para un id que no existe: el id conocido mas
 * parecido del MISMO namespace, a lo sumo {@link #MAX_DISTANCE} ediciones.
 * Java puro (sin Minecraft) para poder testearlo.
 */
public final class IdSuggest {
    private IdSuggest() {}

    public static final int MAX_DISTANCE = 2;

    /** Id mas cercano a {@code id} dentro de {@code known}, o null si ninguno esta a {@value #MAX_DISTANCE} ediciones o menos. */
    public static String nearest(String id, Collection<String> known) {
        if (id == null || known == null) return null;
        int colon = id.indexOf(':');
        String ns = colon < 0 ? "" : id.substring(0, colon + 1);
        String best = null;
        int bestDist = MAX_DISTANCE + 1;
        for (String k : known) {
            if (k.equals(id) || !k.startsWith(ns) || Math.abs(k.length() - id.length()) > MAX_DISTANCE) continue;
            int d = distance(id, k, bestDist - 1);
            if (d < bestDist) { bestDist = d; best = k; }
        }
        return best;
    }

    static int distance(String a, String b, int limit) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            int rowMin = cur[0];
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
                rowMin = Math.min(rowMin, cur[j]);
            }
            if (rowMin > limit) return limit + 1;
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[b.length()];
    }
}
