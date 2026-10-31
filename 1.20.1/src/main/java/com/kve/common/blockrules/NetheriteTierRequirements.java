package com.kve.common.blockrules;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class NetheriteTierRequirements {

    private NetheriteTierRequirements() {}

    private static final Set<String> BLOCKS = new LinkedHashSet<>();

    /** From now on, {@code blockId} can only be broken (with drops) by a player holding a real netherite-tier tool. */
    public static void register(String blockId) {
        if (blockId == null || blockId.isBlank()) return;
        BLOCKS.add(blockId);
    }

    public static void unregister(String blockId) {
        BLOCKS.remove(blockId);
    }

    public static void clearAll() {
        BLOCKS.clear();
    }

    public static boolean hasRequirement(String blockId) {
        return BLOCKS.contains(blockId);
    }

    public static Set<String> all() {
        return Collections.unmodifiableSet(BLOCKS);
    }
}
