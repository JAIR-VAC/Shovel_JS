package com.kve.common.mobrules;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class AllyRequirements {

    private AllyRequirements() {}

    private static final Map<String, Set<String>> ALLIES = new LinkedHashMap<>();

    /** Registers a one-directional ally relationship: {@code entityId} won't fight {@code allyEntityId} (and, per {@link #areAllies()}, the reverse holds too regardless of which side registered it). Adds to whatever's already registered - call {@link #clear(String)}/{@link #clearAll()} first to replace rather than accumulate. */
    public static void register(String entityId, String allyEntityId) {
        if (entityId == null || entityId.isBlank()) return;
        if (allyEntityId == null || allyEntityId.isBlank()) return;
        if (entityId.equals(allyEntityId)) return;
        ALLIES.computeIfAbsent(entityId, k -> new LinkedHashSet<>()).add(allyEntityId);
    }

    /** Removes every ally relationship registered FROM {@code entityId} (does not remove {@code entityId} from other mobs' lists - see {@link #areAllies()} for why that's still enough). */
    public static void clear(String entityId) {
        ALLIES.remove(entityId);
    }

    public static void clearAll() {
        ALLIES.clear();
    }

    public static boolean areAllies(String a, String b) {
        if (a == null || b == null) return false;
        Set<String> alliesOfA = ALLIES.get(a);
        if (alliesOfA != null && alliesOfA.contains(b)) return true;
        Set<String> alliesOfB = ALLIES.get(b);
        return alliesOfB != null && alliesOfB.contains(a);
    }

    /** Whether ANY entity has ally relationships registered - lets the enforcer's hook short-circuit instantly when the feature is unused. */
    public static boolean anyRegistered() {
        return !ALLIES.isEmpty();
    }
}
