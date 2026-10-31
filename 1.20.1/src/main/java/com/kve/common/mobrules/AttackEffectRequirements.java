package com.kve.common.mobrules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AttackEffectRequirements {

    private AttackEffectRequirements() {}

    private static final Map<String, List<AttackEffectRule>> RULES = new LinkedHashMap<>();

    /**
     * Adds one attack effect for {@code entityId}. Unlike
     * {@code ExactToolRequirements#register}, this ADDS to whatever's
     * already registered for {@code entityId} instead of replacing it -
     * a mob can have several attack effects (matches how the Mob Editor
     * GUI's attack-effects list already works), so each call to this from
     * {@code KubeJSMobExporter}'s generated script appends one more rule.
     * Call {@link #clear(String)} or {@link #clearAll()} first if the
     * intent is to replace rather than append.
     */
    public static void register(String entityId, AttackEffectRule rule) {
        if (entityId == null || entityId.isBlank() || rule == null) return;
        if (rule.effectId == null || rule.effectId.isBlank()) return;
        RULES.computeIfAbsent(entityId, k -> new ArrayList<>()).add(rule);
    }

    /** Removes every attack effect registered for {@code entityId}. */
    public static void clear(String entityId) {
        RULES.remove(entityId);
    }

    public static void clearAll() {
        RULES.clear();
    }

    /** Empty if {@code entityId} has no attack effects registered. Never null. */
    public static List<AttackEffectRule> rulesFor(String entityId) {
        List<AttackEffectRule> rules = RULES.get(entityId);
        return rules == null ? Collections.emptyList() : rules;
    }

    public static boolean hasRules(String entityId) {
        List<AttackEffectRule> rules = RULES.get(entityId);
        return rules != null && !rules.isEmpty();
    }

    /** Whether ANY entity has attack effects registered - lets the enforcer's hook short-circuit instantly when the feature is unused. */
    public static boolean anyRegistered() {
        return !RULES.isEmpty();
    }
}
