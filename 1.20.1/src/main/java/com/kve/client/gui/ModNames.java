package com.kve.client.gui;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** UX nombre visible de un mod a partir de su namespace ("create" -> "Create"), cacheado. */
public final class ModNames {
    private ModNames() {}

    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    public static String of(String namespace) {
        if (namespace == null || namespace.isEmpty()) return "";
        return CACHE.computeIfAbsent(namespace, ns -> {
            if (ns.equals("minecraft")) return "Minecraft";
            try {
                return net.minecraftforge.fml.ModList.get().getModContainerById(ns)
                        .map(c -> c.getModInfo().getDisplayName()).orElse(ns);
            } catch (Throwable t) {
                return ns;
            }
        });
    }
}
