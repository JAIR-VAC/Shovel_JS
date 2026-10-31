package com.kve.common.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * Lista "usados recientemente" (el mas nuevo primero, sin repetidos, con
 * tope). Logica pura, sin disco: quien la usa decide donde persistirla.
 */
public final class RecentList {
    private final int max;
    private final List<String> items = new ArrayList<>();

    public RecentList(int max) { this.max = Math.max(1, max); }

    /** Marca {@code key} como el mas reciente. */
    public void touch(String key) {
        if (key == null || key.isEmpty()) return;
        items.remove(key);
        items.add(0, key);
        while (items.size() > max) items.remove(items.size() - 1);
    }

    public List<String> items() { return List.copyOf(items); }

    public void setAll(Collection<String> keys) {
        items.clear();
        if (keys != null) for (String k : keys) if (k != null && !k.isEmpty() && !items.contains(k) && items.size() < max) items.add(k);
    }

    /** Posicion en la lista (0 = mas reciente) o -1. */
    public int rank(String key) { return items.indexOf(key); }

    public <T> List<T> recentFirst(List<T> list, Function<T, String> keyOf) {
        List<T> out = new ArrayList<>(list);
        out.sort(Comparator.comparingInt(t -> {
            int r = rank(keyOf.apply(t));
            return r < 0 ? Integer.MAX_VALUE : r;
        }));
        return out;
    }
}
