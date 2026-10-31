package com.kve.common.persist;

import com.kve.CraftEditorMod;
import com.kve.common.kubejs.KubeJSExporter;
import com.kve.common.recipe.RecipeData;
import com.google.gson.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class RecipeSubsetIO {

    private RecipeSubsetIO() {}

    public static final String KIND = "shovel_js_recipe_subset";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final Path DEFAULT_DIR = Paths.get("kubejs", "config", "shovel_js_subsets");

    /** Lists the ids of every custom recipe currently in the project, sorted, for use in a selection UI. */
    public static List<String> listAllRecipeIds() {
        List<String> ids = new ArrayList<>();
        for (RecipeData r : KubeJSExporter.getAddedRecipes()) ids.add(r.recipeId);
        ids.sort(String.CASE_INSENSITIVE_ORDER);
        return ids;
    }

    /**
     * Groups recipe ids by their mod namespace (the part before ':'), so a
     * modpack maker can quickly select "all of mymod's recipes" instead of
     * clicking every single one. Recipes are grouped by the namespace of
     * their recipeId; falls back to "kve" (this mod's own namespace) if the
     * id has no ':' in it, since that's how custom recipe ids default.
     */
    public static Map<String, List<String>> groupByNamespace() {
        Map<String, List<String>> byNs = new TreeMap<>();
        for (RecipeData r : KubeJSExporter.getAddedRecipes()) {
            String ns = namespaceOf(r.recipeId);
            byNs.computeIfAbsent(ns, k -> new ArrayList<>()).add(r.recipeId);
        }
        for (List<String> l : byNs.values()) l.sort(String.CASE_INSENSITIVE_ORDER);
        return byNs;
    }

    private static String namespaceOf(String recipeId) {
        if (recipeId == null) return "shovel_js";
        int i = recipeId.indexOf(':');
        return i > 0 ? recipeId.substring(0, i) : "shovel_js";
    }

    public static int exportSubset(Collection<String> recipeIds, Path targetFile, String label) {
        Set<String> wanted = new LinkedHashSet<>(recipeIds);
        JsonArray arr = new JsonArray();
        int count = 0;
        for (RecipeData r : KubeJSExporter.getAddedRecipes()) {
            if (!wanted.contains(r.recipeId)) continue;
            arr.add(CraftEditorState.recipeToJson(r));
            count++;
        }
        JsonObject root = new JsonObject();
        root.addProperty("kind", KIND);
        root.addProperty("generatedFrom", label == null ? "" : label);
        root.add("recipes", arr);

        try {
            if (targetFile.getParent() != null) Files.createDirectories(targetFile.getParent());
            Files.writeString(targetFile, GSON.toJson(root), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            CraftEditorMod.LOGGER.info("[Shovel JS] Exported {} recipe(s) to {}", count, targetFile.toAbsolutePath());
        } catch (IOException e) {
            CraftEditorMod.LOGGER.error("[Shovel JS] Subset export failed: {}", e.getMessage());
            return -1;
        }
        return count;
    }

    /** True if the given file looks like a subset file (as opposed to a full-state export/import file). */
    public static boolean isSubsetFile(Path file) {
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            return root.has("kind") && KIND.equals(root.get("kind").getAsString());
        } catch (Exception e) {
            return false;
        }
    }

    /** One recipe staged by {@link #importSubsetMerge()}, plus whatever it replaced (null if it was new) - enough for a caller to queue a matching {@code PendingChange} (action + onDiscard target). */
    public record StagedRecipe(String recipeId, RecipeData previous) {}

    public static List<StagedRecipe> importSubsetMerge(Path file) {
        if (!CraftEditorState.canEditNow()) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] Blocked subset import: player lacks edit permission on this server.");
            return null;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            List<StagedRecipe> staged = new ArrayList<>();
            if (!root.has("recipes")) return staged;
            for (JsonElement el : root.getAsJsonArray("recipes")) {
                try {
                    RecipeData r = CraftEditorState.recipeFromJson(el.getAsJsonObject());
                    RecipeData previous = KubeJSExporter.getAddedRecipes().stream()
                            .filter(x -> x.recipeId.equals(r.recipeId)).findFirst().orElse(null);
                    KubeJSExporter.stageRecipe(r);
                    staged.add(new StagedRecipe(r.recipeId, previous));
                } catch (Exception ex) {
                    CraftEditorMod.LOGGER.warn("[Shovel JS] Skipped recipe in subset import: {}", ex.getMessage());
                }
            }
            CraftEditorMod.LOGGER.info("[Shovel JS] Staged {} recipe(s) from {} (pending Aplicar)", staged.size(), file.toAbsolutePath());
            return staged;
        } catch (Exception e) {
            CraftEditorMod.LOGGER.error("[Shovel JS] Subset import failed: {}", e.getMessage());
            return null;
        }
    }
}
