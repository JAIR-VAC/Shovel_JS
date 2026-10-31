package com.kve.common.mobrules;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class EnemyRequirements {

    private EnemyRequirements() {}

    private static final Map<String, Set<String>> ENEMIES = new LinkedHashMap<>();

    /** Registers a one-directional forced-aggro relationship: {@code entityId} will always attack {@code enemyEntityId} on sight (and, per {@link #areEnemies()}, the reverse holds too regardless of which side registered it). Adds to whatever's already registered - call {@link #clear(String)}/{@link #clearAll()} first to replace rather than accumulate. */
    public static void register(String entityId, String enemyEntityId) {
        if (entityId == null || entityId.isBlank()) return;
        if (enemyEntityId == null || enemyEntityId.isBlank()) return;
        if (entityId.equals(enemyEntityId)) return;
        ENEMIES.computeIfAbsent(entityId, k -> new LinkedHashSet<>()).add(enemyEntityId);
    }

    /** Removes every enemy relationship registered FROM {@code entityId} (does not remove {@code entityId} from other mobs' lists - see {@link #areEnemies()} for why that's still enough). */
    public static void clear(String entityId) {
        ENEMIES.remove(entityId);
    }

    public static void clearAll() {
        ENEMIES.clear();
    }

    /**
     * True if {@code a} and {@code b} are configured as forced enemies in
     * EITHER direction - {@code a} has {@code b} on its list, or {@code b}
     * has {@code a} on its. Symmetric on purpose, same reasoning as
     * {@link AllyRequirements#areAllies}: the GUI's "Enemigos" tab edits
     * one mob at a time, so a relationship typed into either mob's entry
     * has to force the fight both ways.
     */
    public static boolean areEnemies(String a, String b) {
        if (a == null || b == null) return false;
        Set<String> enemiesOfA = ENEMIES.get(a);
        if (enemiesOfA != null && enemiesOfA.contains(b)) return true;
        Set<String> enemiesOfB = ENEMIES.get(b);
        return enemiesOfB != null && enemiesOfB.contains(a);
    }

    /**
     * Every entity id registered as an enemy of {@code entityId}, checked
     * in both directions (own list, plus every other entry that lists
     * {@code entityId} back) - what {@link EnemyEnforcer}'s scan needs to
     * know which nearby entities count as a forced target for this mob.
     * Returns an empty set (never null) when nothing is registered either
     * way, so callers can iterate unconditionally.
     */
    public static Set<String> enemiesOf(String entityId) {
        if (entityId == null) return Set.of();
        Set<String> direct = ENEMIES.get(entityId);
        Set<String> result = direct == null ? new LinkedHashSet<>() : new LinkedHashSet<>(direct);
        for (Map.Entry<String, Set<String>> entry : ENEMIES.entrySet()) {
            if (entry.getValue().contains(entityId)) result.add(entry.getKey());
        }
        return result;
    }

    /** Whether ANY entity has enemy relationships registered - lets the enforcer's scan short-circuit instantly when the feature is unused, same as {@link AllyRequirements#anyRegistered()}. */
    public static boolean anyRegistered() {
        return !ENEMIES.isEmpty();
    }
}
