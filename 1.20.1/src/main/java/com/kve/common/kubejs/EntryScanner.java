package com.kve.common.kubejs;

import com.kve.common.event.EventEntry;
import com.kve.common.recipe.RecipeData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class EntryScanner {
    private EntryScanner() {}

    public static class DomainResult {
        /** Corto y en ingles a proposito, mismo patron que ya usa el flash de "Rescan Scripts" (ExternalScriptScanner) - no traducido. */
        public final String label;
        public int total;
        public int problems;
        /** Hasta 5 muestras "id: primer error" por dominio, para no acumular una lista sin limite. */
        public final List<String> sampleMessages = new ArrayList<>();
        DomainResult(String label) { this.label = label; }
    }

    private static volatile List<DomainResult> LAST_RESULTS = new ArrayList<>();
    private static volatile long LAST_SCAN_AT = 0;

    public static List<DomainResult> lastResults() { return Collections.unmodifiableList(LAST_RESULTS); }
    public static long lastScanAt() { return LAST_SCAN_AT; }

    public static int totalEntries()  { return LAST_RESULTS.stream().mapToInt(r -> r.total).sum(); }
    public static int totalProblems() { return LAST_RESULTS.stream().mapToInt(r -> r.problems).sum(); }

    public static void reset() {
        LAST_RESULTS = new ArrayList<>();
        LAST_SCAN_AT = 0;
    }

    /** Corre el escaneo completo y guarda el resultado (consultable via {@link #lastResults()} sin tener que repetir el escaneo). */
    public static List<DomainResult> scan() {
        List<DomainResult> results = new ArrayList<>();

        DomainResult recipes = new DomainResult("recipes");
        for (RecipeData r : KubeJSExporter.getAddedRecipes()) {
            recipes.total++;
            addIfProblems(recipes, r.recipeId, KubeJSExporter.validateRecipe(r));
        }
        results.add(recipes);

        DomainResult items = new DomainResult("items");
        for (KubeJSItemAttrExporter.ItemEntry e : KubeJSItemAttrExporter.getEntries()) {
            items.total++;
            addIfProblems(items, e.itemId, KubeJSItemAttrExporter.validateItem(e));
        }
        results.add(items);

        DomainResult blocks = new DomainResult("blocks");
        for (KubeJSBlockAttrExporter.BlockEntry e : KubeJSBlockAttrExporter.getEntries()) {
            blocks.total++;
            addIfProblems(blocks, e.blockId, KubeJSBlockAttrExporter.validateBlock(e));
        }
        results.add(blocks);

        DomainResult mobs = new DomainResult("mobs");
        for (KubeJSMobExporter.MobEntry e : KubeJSMobExporter.getEntries()) {
            mobs.total++;
            addIfProblems(mobs, e.entityId, KubeJSMobExporter.validateMob(e));
        }
        results.add(mobs);

        DomainResult tags = new DomainResult("tags");
        for (TagExporter.TagEntry e : TagExporter.getEntries()) {
            tags.total++;
            addIfProblems(tags, e.tagId, TagExporter.validateTag(e));
        }
        results.add(tags);

        DomainResult loot = new DomainResult("loot");
        for (LootTableExporter.LootTableEntry e : LootTableExporter.getEntries()) {
            loot.total++;
            addIfProblems(loot, e.targetId, LootTableExporter.validateLootTable(e));
        }
        results.add(loot);

        DomainResult events = new DomainResult("events");
        for (EventEntry e : EventExporter.getEntries()) {
            events.total++;
            addIfProblems(events, e.id, EventExporter.validateEvent(e));
        }
        results.add(events);

        LAST_RESULTS = results;
        LAST_SCAN_AT = System.currentTimeMillis();
        return results;
    }

    private static void addIfProblems(DomainResult r, String id, List<String> errors) {
        if (errors == null || errors.isEmpty()) return;
        r.problems++;
        if (r.sampleMessages.size() < 5) {
            r.sampleMessages.add((id == null || id.isBlank() ? "?" : id) + ": " + errors.get(0));
        }
    }
}
