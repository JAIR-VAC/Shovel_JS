package com.kve.common.mobrules;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class MobBehaviorTypeFlags {

    private MobBehaviorTypeFlags() {}

    private static final Map<String, Set<String>> FLAGS = new LinkedHashMap<>();

    public static void register(String entityId, Set<String> flags) {
        if (entityId == null || entityId.isBlank() || flags == null || flags.isEmpty()) return;
        FLAGS.computeIfAbsent(entityId, k -> new LinkedHashSet<>()).addAll(flags);
    }

    public static void clear(String entityId) {
        FLAGS.remove(entityId);
    }

    public static void clearAll() {
        FLAGS.clear();
    }

    public static Set<String> flagsOf(String entityId) {
        Set<String> f = FLAGS.get(entityId);
        return f == null ? Set.of() : f;
    }

    public static boolean has(String entityId, String flag) {
        Set<String> f = FLAGS.get(entityId);
        return f != null && f.contains(flag);
    }

    /** Whether ANY entity has extended behavior flags registered - lets the enforcer's tick scan short-circuit instantly when the feature is unused. */
    public static boolean anyRegistered() {
        return !FLAGS.isEmpty();
    }
}
