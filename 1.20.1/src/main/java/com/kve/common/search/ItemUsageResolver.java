package com.kve.common.search;

import com.kve.common.kubejs.KubeJSExporter;
import com.kve.common.kubejs.LootTableExporter;
import com.kve.common.kubejs.TagExporter;
import com.kve.common.recipe.RecipeData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class ItemUsageResolver {

    private ItemUsageResolver() {}

    /**
     * One place {@code itemId} appears today.
     *
     * @param kind    what kind of appearance this is
     * @param entryId the id to look the owning entry back up with
     *                (recipeId / tag key / loot table key)
     * @param label   short human-readable name for the row (recipe id /
     *                tag id / loot table target id)
     * @param detail  extra context for the row ("input"/"output" for
     *                recipes, "pool N" for loot, "" for tags)
     */
    public record ItemUsage(Kind kind, String entryId, String label, String detail) {
        public enum Kind { RECIPE_INPUT, RECIPE_OUTPUT, TAG_MEMBER, LOOT_ENTRY }
    }

    /**
     * O(n) over recipes+tags+loot already loaded in memory - no I/O, same
     * order of magnitude {@code TagEditorScreen#resolveTagMembers()}
     * already pays on every "Miembros" open (§4.4), so no cache/debounce
     * needed for v1.
     */
    public static List<ItemUsage> resolve(String itemId) {
        List<ItemUsage> hits = new ArrayList<>();
        if (itemId == null || itemId.isBlank()) return hits;
        resolveRecipes(itemId, hits);
        resolveTags(itemId, hits);
        resolveLoot(itemId, hits);
        return hits;
    }

    private static void resolveRecipes(String itemId, List<ItemUsage> hits) {
        for (RecipeData r : KubeJSExporter.getAddedRecipes()) {
            boolean isOutput = r.type == RecipeData.RecipeType.CUSTOM_MACHINE
                    ? r.machineOutputs.stream().anyMatch(s -> matchesItemSlot(s, itemId))
                    : itemId.equals(idOf(r.output));
            if (isOutput) hits.add(new ItemUsage(ItemUsage.Kind.RECIPE_OUTPUT, r.recipeId, r.recipeId, "output"));

            boolean isInput = r.type == RecipeData.RecipeType.CUSTOM_MACHINE
                    ? r.machineInputs.stream().anyMatch(s -> matchesItemSlot(s, itemId))
                    : r.ingredients.stream().anyMatch(s -> itemId.equals(idOf(s)))
                            || r.shapelessIngredients.stream().anyMatch(s -> itemId.equals(idOf(s)))
                            || r.combinationPedestals.stream().anyMatch(s -> itemId.equals(idOf(s)));
            if (isInput) hits.add(new ItemUsage(ItemUsage.Kind.RECIPE_INPUT, r.recipeId, r.recipeId, "input"));
        }
    }

    private static boolean matchesItemSlot(RecipeData.MachineSlot s, String itemId) {
        return s != null && s.type == RecipeData.MachineSlotType.ITEM && itemId.equals(idOf(s.item));
    }

    private static void resolveTags(String itemId, List<ItemUsage> hits) {
        for (TagExporter.TagEntry e : TagExporter.getEntries()) {
            if (e.regType != TagExporter.RegistryType.ITEM) continue;
            if (isTagMember(e, itemId)) {
                hits.add(new ItemUsage(ItemUsage.Kind.TAG_MEMBER, e.key(), e.tagId, ""));
            }
        }
    }

    private static boolean isTagMember(TagExporter.TagEntry e, String itemId) {
        if (e.removed.contains(itemId)) return false;
        if (e.added.contains(itemId)) return true;

        if (e.tagId == null || e.tagId.isBlank() || !e.tagId.contains(":")) return false;
        try {
            var id = new net.minecraft.resources.ResourceLocation(e.tagId);
            return net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getTag(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, id))
                    .map(set -> set.stream().anyMatch(h -> h.unwrapKey()
                            .map(k -> k.location().toString().equals(itemId)).orElse(false)))
                    .orElse(false);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void resolveLoot(String itemId, List<ItemUsage> hits) {
        for (LootTableExporter.LootTableEntry e : LootTableExporter.getEntries()) {
            List<Integer> poolsHit = new ArrayList<>();
            for (int i = 0; i < e.pools.size(); i++) {
                LootTableExporter.LootPoolData pool = e.pools.get(i);
                boolean hit = pool.entries.stream().anyMatch(en -> itemId.equals(en.itemId));
                if (hit) poolsHit.add(i + 1);
            }
            if (poolsHit.isEmpty()) continue;
            String detail = "pool " + poolsHit.stream().map(String::valueOf).collect(Collectors.joining(", "));
            hits.add(new ItemUsage(ItemUsage.Kind.LOOT_ENTRY, e.key(), e.targetId, detail));
        }
    }

    private static String idOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? null : key.toString();
    }
}
