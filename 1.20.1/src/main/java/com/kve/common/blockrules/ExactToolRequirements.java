package com.kve.common.blockrules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "Requiere tool exacta" - something neither vanilla nor KubeJS expose.
 * Vanilla/KubeJS block harvest requirements are TAG-based only - there is
 * no built-in way to say "only THIS exact item breaks this block", e.g. a
 * quest-only pickaxe, a named/enchanted artifact, or a specific modded tool
 * picked out of a whole family of similar ones.
 * <p>
 * Pure in-memory data + lookup - {@link ExactToolEnforcer} is what actually
 * acts on it. Deliberately NOT persisted via CraftEditorState: this is
 * meant to be (re-)populated every time the KubeJS scripts that declare it
 * run (server start / {@code /reload} / {@code /kubejs reload}), the same
 * way a script's own event listeners are just re-registered on reload
 * rather than being saved as KVE state. Call {@link #clearAll()} at the top
 * of the declaring script section before re-registering if you want stale
 * requirements from a previous version of the script to actually go away.
 * <p>
 * A block can accept more than one exact item (e.g. either of two
 * equivalent quest tools) - {@code register}/{@code requiredItemsFor()} work
 * on a {@link Set} of item ids, "any one of them counts", same OR semantics
 * {@code KubeJSBlockAttrExporter}'s old {@code BlockEvents.broken}
 * simulation had before it was replaced by this real enforcer.
 */
public final class ExactToolRequirements {

    private ExactToolRequirements() {}

    private static final Map<String, Set<String>> REQUIREMENTS = new LinkedHashMap<>();

    /**
     * From now on, {@code blockId} can only be broken (with drops) by a
     * player holding exactly {@code itemId} - any other item, including one
     * that satisfies the block's normal tool tag, is treated as "wrong
     * tool" (very slow, no drops), same as vanilla's own wrong-tool
     * behavior for tag-based requirements. Re-registering the same block
     * replaces its requirement (does not add to it) - use
     * {@link #register(String, List)} to accept more than one item.
     */
    public static void register(String blockId, String itemId) {
        if (itemId == null || itemId.isBlank()) return;
        register(blockId, Collections.singletonList(itemId));
    }

    /**
     * Same as {@link #register(String, String)} but accepts several
     * equally-valid item ids for {@code blockId} - the player only needs
     * to be holding any ONE of them. Re-registering the same block
     * replaces its whole accepted set.
     */
    public static void register(String blockId, List<String> itemIds) {
        if (blockId == null || blockId.isBlank() || itemIds == null) return;
        Set<String> ids = new LinkedHashSet<>();
        for (String itemId : itemIds) {
            if (itemId != null && !itemId.isBlank()) ids.add(itemId);
        }
        if (ids.isEmpty()) return;
        REQUIREMENTS.put(blockId, ids);
    }

    public static void unregister(String blockId) {
        REQUIREMENTS.remove(blockId);
    }

    public static void clearAll() {
        REQUIREMENTS.clear();
    }

    /** Empty if this block has no exact-tool requirement (i.e. vanilla/tag rules apply normally). Never null. */
    public static Set<String> requiredItemsFor(String blockId) {
        Set<String> ids = REQUIREMENTS.get(blockId);
        return ids == null ? Collections.emptySet() : ids;
    }

    public static boolean hasRequirement(String blockId) {
        return REQUIREMENTS.containsKey(blockId);
    }

    /** Every blockId currently carrying an exact-tool requirement. */
    public static Set<String> all() {
        return Collections.unmodifiableSet(REQUIREMENTS.keySet());
    }

    /** Convenience for callers that just want something printable, e.g. "kve:vault_key" or "kve:vault_key o kve:master_key". */
    public static String describeRequirement(String blockId) {
        return String.join(" o ", new ArrayList<>(requiredItemsFor(blockId)));
    }
}