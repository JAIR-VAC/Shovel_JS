package com.kve.common.search;

import com.kve.common.structure.StructureUsageIndex;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Given a raw id (item, block, entity,
 * tag, effect...), finds every KVE-managed entry across every domain
 * (recipes, items, mobs, tags, loot, events) that references it, so a
 * player renaming/removing something doesn't have to manually re-check six
 * different editors by hand.
 * <p>
 * Scans the SAME in-memory {@code ENTRIES} maps every exporter already
 * keeps for its own {@code writeScript()}/{@code writeFiles()} (see e.g.
 * {@link KubeJSExporter#getAddedRecipes()}, {@link TagExporter#getEntries()}) -
 * no separate index to keep in sync, no disk I/O, just a plain linear scan
 * over whatever's currently loaded. That's plenty fast for what this mod
 * realistically holds (recipes/mobs/items/tags/loot/events are all
 * hand-authored KVE entries, not the whole modpack's registries - hundreds
 * of entries at most, not hundreds of thousands), so there's no need for a
 * real inverted index.
 * <p>
 * Structures are the one domain that
 * doesn't fit that "same in-memory ENTRIES map" story: they live as
 * individual .nbt files on disk, not as an in-memory map like every other
 * domain here. Reading every saved structure fresh on every keystroke would
 * make this screen noticeably laggy as the structure count grows, and this
 * is meant to stay a snappy "type and see results" tool. Rather than doing
 * that live scan (or skipping structures altogether), {@link #searchStructures()}
 * delegates to {@link StructureUsageIndex}, a small persistent, incrementally
 * -maintained cache purpose-built for exactly this: it's kept up to date as
 * structures are saved/restored (see {@code StructureNbtIO#save}/{@code #restore}),
 * so a search here only ever touches that in-memory cache, never the .nbt
 * files themselves.
 */
public final class IdUsageIndex {

    private IdUsageIndex() {}

    public enum Domain { RECIPE, ITEM, BLOCK, MOB, TAG, LOOT, EVENT, TRADE, PACK_RULE, STRUCTURE }

    /**
     * One reference to the searched id.
     *
     * @param domain  which editor owns {@code entryId()}
     * @param entryId() the id to look the owning entry back up with (recipeId/itemId/entityId/tagKey/lootKey/eventId/structureId())
     * @param field   short human-readable description of WHERE inside that entry the id showed up (e.g. "ingredient", "equipment (mainhand)", "action: give item") - shown in {@code IdUsageScreen}'s result list
     */
    public record Hit(Domain domain, String entryId, String field) {}

    /**
     * Case-insensitive substring search across every domain, including
     * structures. An empty or blank query returns no results rather than
     * "everything" - dumping every entry in the mod isn't a useful answer
     * to "where is THIS id used".
     * <p>
     * The per-domain scans now live in {@link ProjectReferences}
     * (shared with the pack diagnostics), which also covers trades and pack
     * rules. Structures still come from {@link StructureUsageIndex}'s cache.
     */
    public static List<Hit> search(String rawId) {
        String needle = normalize(rawId);
        List<Hit> hits = new ArrayList<>();
        if (needle.isEmpty()) return hits;

        for (ProjectReferences.Ref r : ProjectReferences.collect()) {
            if (!matches(r.id(), needle)) continue;
            boolean withId = r.kind() == ProjectReferences.Kind.ATTRIBUTE || r.kind() == ProjectReferences.Kind.EFFECT
                    || r.kind() == ProjectReferences.Kind.RECIPE_TYPE;
            String field = withId ? r.field() + ": " + r.id() : r.field();
            hits.add(new Hit(Domain.valueOf(r.domain().name()), r.entryId(), field));
        }
        searchStructures(needle, hits);
        return hits;
    }

    private static void searchStructures(String needle, List<Hit> hits) {
        for (StructureUsageIndex.Hit h : StructureUsageIndex.search(needle)) {
            hits.add(new Hit(Domain.STRUCTURE, h.structureId(), h.field() + ": " + h.refId()));
        }
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean matches(String candidate, String needle) {
        return candidate != null && !candidate.isEmpty() && candidate.toLowerCase(Locale.ROOT).contains(needle);
    }
}
