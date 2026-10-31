package com.kve.common;

import java.util.LinkedHashMap;
import java.util.Map;

public final class Lang {

    private Lang() {}

    private static final Map<String, String> CACHE_NO_ARGS = new java.util.HashMap<>();

    private static final int ARGS_CACHE_CAP = 512;

    private static final Map<String, String> CACHE_ARGS = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > ARGS_CACHE_CAP;
        }
    };

    private static String cachedLanguageCode = null;

    /**
     * Cheap per-call check: if the client's active language changed since
     * our caches were last populated, both caches are stale (every
     * resolved string in them is in the OLD language) and must be
     * dropped. This is just a getter + a String#equals against a field we
     * already hold - no component building, no I/O - so calling it on
     * every {@link #get} is not a regression versus the un-cached code
     * this replaces.
     */
    private static final boolean CLIENT = net.minecraftforge.fml.loading.FMLEnvironment.dist == null
            || net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient();

    private static final class ClientLanguage {
        static String selected() {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            return mc == null ? null : mc.getLanguageManager().getSelected();
        }
    }

    private static void checkLanguageAndMaybeClear() {
        // en un servidor dedicado no hay idioma de cliente que seguir (ni clase Minecraft)
        if (!CLIENT) return;
        String current;
        try {
            current = ClientLanguage.selected();
            if (current == null) { CACHE_NO_ARGS.clear(); CACHE_ARGS.clear(); cachedLanguageCode = null; return; }
        } catch (Throwable ignored) {
            CACHE_NO_ARGS.clear();
            CACHE_ARGS.clear();
            cachedLanguageCode = null;
            return;
        }
        if (!java.util.Objects.equals(current, cachedLanguageCode)) {
            CACHE_NO_ARGS.clear();
            CACHE_ARGS.clear();
            cachedLanguageCode = current;
        }
    }

    public static String get(String key) {
        checkLanguageAndMaybeClear();
        String cached = CACHE_NO_ARGS.get(key);
        if (cached != null) return cached;
        String resolved = net.minecraft.network.chat.Component.translatable(key).getString();
        CACHE_NO_ARGS.put(key, resolved);
        return resolved;
    }

    public static String get(String key, Object... args) {
        if (args == null || args.length == 0) return get(key);
        checkLanguageAndMaybeClear();
        String cacheKey = argsCacheKey(key, args);
        String cached = CACHE_ARGS.get(cacheKey);
        if (cached != null) return cached;
        String resolved = net.minecraft.network.chat.Component.translatable(key, args).getString();
        CACHE_ARGS.put(cacheKey, resolved);
        return resolved;
    }

    private static String argsCacheKey(String key, Object[] args) {
        StringBuilder sb = new StringBuilder(key);
        for (Object a : args) {
            sb.append('\u0000').append(String.valueOf(a));
        }
        return sb.toString();
    }

    public static net.minecraft.network.chat.Component c(String key) {
        return net.minecraft.network.chat.Component.translatable(key);
    }

    public static net.minecraft.network.chat.Component c(String key, Object... args) {
        return net.minecraft.network.chat.Component.translatable(key, args);
    }
}
