package com.kve.common.kubejs;

import com.kve.common.util.JsStrings;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.kve.CraftEditorMod;
import com.kve.common.persist.CraftEditorState;
import com.kve.common.recipe.RecipeData;
import com.kve.common.recipetype.RecipeTypeDescriptor;
import com.kve.common.recipetype.RecipeTypeDescriptorRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class KubeJSExporter {

    private static final List<RecipeData> ADDED   = new ArrayList<>();
    private static final List<String>     REMOVED = new ArrayList<>();

    /**
     * Lo ultimo aplicado (o cargado al arrancar): lo que se guarda en el estado y lo que leen
     * los enforcers. Los borradores pendientes viven solo en ADDED/REMOVED hasta Aplicar.
     */
    private static volatile List<RecipeData> APPLIED_ADDED = List.of();
    private static volatile List<String> APPLIED_REMOVED = List.of();

    public static List<RecipeData> getAppliedAddedRecipes() { return APPLIED_ADDED; }
    public static List<String> getAppliedRemovedRecipes() { return APPLIED_REMOVED; }

    public static void markAppliedAdded() {
        List<RecipeData> l = new ArrayList<>();
        for (RecipeData r : ADDED) l.add(copyRecipe(r));
        APPLIED_ADDED = Collections.unmodifiableList(l);
    }

    public static void markAppliedRemoved() { APPLIED_REMOVED = List.copyOf(REMOVED); }

    private static final List<String> LAST_WRITE_WARNINGS = new ArrayList<>();

    public static List<String> getLastWriteWarnings() { return Collections.unmodifiableList(LAST_WRITE_WARNINGS); }
    private static final Map<String, Deque<RecipeData>> HISTORY =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Deque<RecipeData>> eldest) {
                    return size() > Math.max(1, com.kve.common.config.KVEConfig.undoHistoryMaxKeys);
                }
            };

    /** @deprecated recetas ahora viven bajo {@link KveScriptPaths#recipesDir()}, separadas por mod. */
    @Deprecated
    public static Path getScriptDir() {
        return KveScriptPaths.recipesDir();
    }

    private static String recipeModId(RecipeData r) {
        return switch (r.type) {
            case VANILLA_SHAPED, VANILLA_SHAPELESS -> "vanilla";
            case EC_TABLE_SHAPED, EC_TABLE_SHAPELESS, EC_ENDER_SHAPED, EC_ENDER_SHAPELESS,
                 EC_FLUX_SHAPED, EC_FLUX_SHAPELESS, EC_COMBINATION -> "extendedcrafting";
            case CUSTOM_JSON, CUSTOM_MACHINE -> namespaceOf(r.customTypeId);
        };
    }

    private static String namespaceOf(String id) {
        if (id == null) return "other";
        int i = id.indexOf(':');
        return i > 0 ? id.substring(0, i) : "other";
    }

    private static void pruneStaleModDirs(Path parentDir, Set<String> keep) {
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(parentDir)) {
            for (Path p : ds) {
                if (!Files.isDirectory(p) || keep.contains(p.getFileName().toString())) continue;
                try (var walk = Files.walk(p)) {
                    walk.sorted(Comparator.reverseOrder())
                        .forEach(q -> { try { Files.deleteIfExists(q); } catch (IOException ignored) {} });
                }
            }
        } catch (IOException ignored) {
        }
    }

    /**
     * Validates a recipe before it's written to disk, catching the most
     * common ways an in-progress edit can produce a broken/no-op recipe
     * script (empty id, no output, shaped grid with no ingredients at all,
     * shapeless with zero ingredients, combination missing its center, a
     * custom-mod recipe with no recipe type chosen, etc.). Returns a list
     * of human-readable problems; an empty list means the recipe is OK to
     * save.
     */
    public static boolean isPositionalCustomType(String typeId) {
        if (typeId == null) return false;
        String t = typeId.toLowerCase(Locale.ROOT);
        return t.contains("smithing") || "create:mechanical_crafting".equals(t) || "twilightforest:uncrafting".equals(t);
    }

    public static List<String> validateRecipe(RecipeData d) {
        List<String> errors = new ArrayList<>();
        if (d == null) { errors.add("Recipe data is null."); return errors; }

        if (d.recipeId == null || d.recipeId.isBlank())
            errors.add("Recipe ID is empty.");
        else if (!d.recipeId.matches("[a-z0-9_./-]+"))
            errors.add("Recipe ID contains characters not valid in a KubeJS/resource id.");

        boolean isCombo = d.type == RecipeData.RecipeType.EC_COMBINATION;
        boolean isCustom = d.type == RecipeData.RecipeType.CUSTOM_JSON;
        boolean isMachine = d.type == RecipeData.RecipeType.CUSTOM_MACHINE;

        if (isCustom || isMachine) {
            if (d.customTypeId == null || d.customTypeId.isBlank())
                errors.add("No recipe type chosen for this custom/mod recipe.");
        } else if (d.output == null || d.output.isEmpty()) {
            errors.add("No output item set.");
        }

        if (isMachine) {
            boolean anyInput = d.machineInputs != null && d.machineInputs.stream().anyMatch(KubeJSExporter::isValidMachineSlot);
            boolean anyOutput = d.machineOutputs != null && d.machineOutputs.stream().anyMatch(KubeJSExporter::isValidMachineSlot);
            var desc = com.kve.common.recipetype.RecipeTypeDescriptorRegistry.find(d.customTypeId);
            if (!anyInput && !desc.map(x -> x.noIngredients).orElse(false)) errors.add("Machine recipe has no valid input slots.");
            if (!anyOutput && !desc.map(x -> x.noOutput).orElse(false)) errors.add("Machine recipe has no valid output slots.");
        } else if (isCombo) {
            if (d.combinationCenter == null || d.combinationCenter.isEmpty())
                errors.add("Combination recipe has no center item.");
            boolean anyPedestal = d.combinationPedestals != null
                    && d.combinationPedestals.stream().anyMatch(s -> s != null && !s.isEmpty());
            if (!anyPedestal)
                errors.add("Combination recipe has no pedestal items.");
        } else {
            boolean isPositionalCustom = isCustom && isPositionalCustomType(d.customTypeId);
            boolean shapeless = d.type == RecipeData.RecipeType.VANILLA_SHAPELESS
                    || d.type == RecipeData.RecipeType.EC_TABLE_SHAPELESS
                    || d.type == RecipeData.RecipeType.EC_ENDER_SHAPELESS
                    || d.type == RecipeData.RecipeType.EC_FLUX_SHAPELESS
                    || (isCustom && !isPositionalCustom);
            boolean noIngredients = isCustom && com.kve.common.recipetype.RecipeTypeDescriptorRegistry.find(d.customTypeId)
                    .map(desc -> desc.noIngredients).orElse(false);
            if (noIngredients) {
            } else if (shapeless) {
                boolean any = d.shapelessIngredients != null
                        && d.shapelessIngredients.stream().anyMatch(s -> s != null && !s.isEmpty());
                if (!any) errors.add("No ingredients set.");
            } else {
                if (d.gridWidth <= 0 || d.gridHeight <= 0) {
                    errors.add("Invalid grid size.");
                } else {
                    boolean any = d.ingredients != null
                            && d.ingredients.stream().anyMatch(s -> s != null && !s.isEmpty());
                    if (!any) errors.add("Shaped grid has no ingredients.");
                }
            }
        }

        if ((isCustom || isMachine) && d.customExtraJson != null && !d.customExtraJson.isBlank()) {
            String toParse = d.customExtraJson.trim().startsWith("{")
                    ? d.customExtraJson.trim() : "{" + d.customExtraJson.trim() + "}";
            try {
                JsonElement el = JsonParser.parseString(toParse);
                if (!el.isJsonObject()) errors.add("'Extra JSON' must be a JSON object.");
            } catch (JsonSyntaxException ex) {
                errors.add("'Extra JSON' is not valid JSON: " + ex.getMessage());
            }
        }

        // Semantic layer, on top of the structural checks above: are the ids
        // this recipe references actually well-formed (and, when a registry
        // is available to check against, do they actually exist)? See
        // SemanticValidator for why registry checks degrade gracefully
        // instead of failing every id when there's nothing to check against.
        errors.addAll(semanticErrors(d));

        return errors;
    }

    private static List<String> semanticErrors(RecipeData d) {
        List<String> errors = new ArrayList<>();
        if (d == null) return errors;

        boolean isCustom = d.type == RecipeData.RecipeType.CUSTOM_JSON;
        boolean isMachine = d.type == RecipeData.RecipeType.CUSTOM_MACHINE;

        if (!isMachine) {
            String outId = itemId(d.output);
            if (outId != null) errors.addAll(SemanticValidator.validateItemId(outId, "Output"));
            if (d.ingredients != null) {
                for (ItemStack s : d.ingredients) {
                    String id = itemId(s);
                    if (id != null) errors.addAll(SemanticValidator.validateItemId(id, "Ingrediente"));
                }
            }
            if (d.shapelessIngredients != null) {
                for (ItemStack s : d.shapelessIngredients) {
                    String id = itemId(s);
                    if (id != null) errors.addAll(SemanticValidator.validateItemId(id, "Ingrediente"));
                }
            }
            if (d.type == RecipeData.RecipeType.EC_COMBINATION) {
                String centerId = itemId(d.combinationCenter);
                if (centerId != null) errors.addAll(SemanticValidator.validateItemId(centerId, "Centro"));
                if (d.combinationPedestals != null) {
                    for (ItemStack s : d.combinationPedestals) {
                        String id = itemId(s);
                        if (id != null) errors.addAll(SemanticValidator.validateItemId(id, "Pedestal"));
                    }
                }
            }
        }

        RecipeTypeDescriptor descriptor = (isCustom || isMachine)
                ? RecipeTypeDescriptorRegistry.find(d.customTypeId).orElse(null) : null;
        if (isMachine) {
            List<String> chemicalKinds = SemanticValidator.chemicalKindsOf(descriptor);
            if (d.machineInputs != null)  errors.addAll(semanticErrorsForSlots(d.machineInputs, "Input", chemicalKinds));
            if (d.machineOutputs != null) errors.addAll(semanticErrorsForSlots(d.machineOutputs, "Output", chemicalKinds));
        }

        if ((isCustom || isMachine) && d.customExtraJson != null && !d.customExtraJson.isBlank()) {
            errors.addAll(SemanticValidator.validateExtraJson(d.customExtraJson, descriptor, "Extra JSON"));
        }

        return errors;
    }

    private static List<String> semanticErrorsForSlots(List<RecipeData.MachineSlot> slots, String label, List<String> chemicalKinds) {
        List<String> errors = new ArrayList<>();
        for (RecipeData.MachineSlot s : slots) {
            if (s == null) continue;
            switch (s.type) {
                case ITEM -> {
                    String id = itemId(s.item);
                    if (id != null) errors.addAll(SemanticValidator.validateItemId(id, label));
                }
                case FLUID -> {
                    if (s.fluidId != null && !s.fluidId.isBlank())
                        errors.addAll(SemanticValidator.validateFluidOrChemicalId(s.fluidId, label, chemicalKinds));
                }
                case TAG -> {
                    if (s.tagId != null && !s.tagId.isBlank())
                        errors.addAll(SemanticValidator.validateTagId(s.tagId, label));
                }
            }
        }
        return errors;
    }

    static boolean isValidMachineSlot(RecipeData.MachineSlot s) {
        if (s == null) return false;
        return switch (s.type) {
            case ITEM -> s.item != null && !s.item.isEmpty();
            case FLUID -> s.fluidId != null && !s.fluidId.isBlank();
            case TAG -> s.tagId != null && !s.tagId.isBlank();
        };
    }

    public static void saveRecipe(RecipeData data) {
        RecipeData previous = ADDED.stream().filter(r -> r.recipeId.equals(data.recipeId)).findFirst().orElse(null);
        if (previous != null) pushHistory(data.recipeId, previous);
        ADDED.removeIf(r -> r.recipeId.equals(data.recipeId));
        ADDED.add(data);
        pruneHistory();
        writeAddScript();
        CraftEditorState.save();
    }

    private static void pruneHistory() {
        if (HISTORY.isEmpty()) return;
        Set<String> liveIds = new HashSet<>();
        for (RecipeData r : ADDED) liveIds.add(r.recipeId);
        HISTORY.keySet().removeIf(id -> !liveIds.contains(id));
    }

    public static void deleteRecipe(String recipeId) {
        ADDED.removeIf(r -> r.recipeId.equals(recipeId));
        HISTORY.remove(recipeId);
        writeAddScript();
        CraftEditorState.save();
    }

    /** Stages a recipe into ADDED (in-memory only, no script/state write). */
    public static void stageRecipe(RecipeData data) {
        RecipeData previous = ADDED.stream().filter(r -> r.recipeId.equals(data.recipeId)).findFirst().orElse(null);
        if (previous != null) pushHistory(data.recipeId, previous);
        ADDED.removeIf(r -> r.recipeId.equals(data.recipeId));
        ADDED.add(data);
        pruneHistory();
    }

    /** Undoes a stageRecipe(): drops the staged entry and restores whatever was there before (null if it was new). */
    public static void discardStagedRecipe(String recipeId, RecipeData previousOrNull) {
        ADDED.removeIf(r -> r.recipeId.equals(recipeId));
        if (previousOrNull != null) ADDED.add(previousOrNull);
    }

    /** Stages a recipe removal (in-memory only). */
    public static void stageDeleteRecipe(String recipeId) {
        ADDED.removeIf(r -> r.recipeId.equals(recipeId));
        HISTORY.remove(recipeId);
    }

    /** Writes the currently staged ADDED list to disk (KubeJS script + KVE state). Only called from a PendingChange's onApply - never directly from an editor Save/Delete button. */
    public static void commitAdded() {
        writeAddScript();
        CraftEditorState.save();
    }

    /** Stages a vanilla-recipe removal (in-memory only). */
    public static void stageRemoveVanilla(String recipeId) {
        if (!REMOVED.contains(recipeId)) REMOVED.add(recipeId);
    }

    /** Undoes a stageRemoveVanilla(). */
    public static void discardStagedRemoveVanilla(String recipeId) {
        REMOVED.remove(recipeId);
    }

    public static void stageUnremoveVanilla(String recipeId) {
        REMOVED.remove(recipeId);
    }

    /** Undoes a stageUnremoveVanilla(): puts the id back on the staged REMOVED list. */
    public static void discardStagedUnremoveVanilla(String recipeId) {
        if (!REMOVED.contains(recipeId)) REMOVED.add(recipeId);
    }

    /** Writes the currently staged REMOVED list to disk. Only called from a PendingChange's onApply. */
    public static void commitRemoved() {
        writeRemoveScript();
        CraftEditorState.save();
    }

    private static void pushHistory(String id, RecipeData snapshot) {
        int max = com.kve.common.config.KVEConfig.undoHistorySize;
        if (max <= 0) return;
        Deque<RecipeData> h = HISTORY.computeIfAbsent(id, k -> new ArrayDeque<>());
        h.addFirst(copyRecipeInternal(snapshot));
        while (h.size() > max) h.removeLast();
    }

    public static RecipeData copyRecipe(RecipeData r) {
        return copyRecipeInternal(r);
    }

    private static RecipeData copyRecipeInternal(RecipeData r) {
        RecipeData c = new RecipeData();
        c.recipeId = r.recipeId; c.enabled = r.enabled; c.category = r.category; c.notes = r.notes;
        c.type = r.type; c.ecTier = r.ecTier;
        c.ingredients = new ArrayList<>();
        for (ItemStack s : r.ingredients) c.ingredients.add(s.copy());
        c.gridWidth = r.gridWidth; c.gridHeight = r.gridHeight;
        c.shapelessIngredients = new ArrayList<>();
        for (ItemStack s : r.shapelessIngredients) c.shapelessIngredients.add(s.copy());
        c.output = r.output.copy();
        c.ecPowerRequired = r.ecPowerRequired; c.ecCraftingTime = r.ecCraftingTime;
        c.combinationCenter = r.combinationCenter.copy();
        c.combinationPedestals = new ArrayList<>();
        for (ItemStack s : r.combinationPedestals) c.combinationPedestals.add(s.copy());
        c.combinationRfCost = r.combinationRfCost; c.combinationRfRate = r.combinationRfRate;
        c.customTypeId = r.customTypeId; c.customExtraJson = r.customExtraJson;
        c.secondaryOutput = r.secondaryOutput.copy(); c.secondaryOutputChance = r.secondaryOutputChance;
        for (RecipeData.MachineSlot s : r.machineInputs) c.machineInputs.add(s.copy());
        for (RecipeData.MachineSlot s : r.machineOutputs) c.machineOutputs.add(s.copy());
        return c;
    }

    public static boolean canUndo(String id) {
        Deque<RecipeData> h = HISTORY.get(id);
        return h != null && !h.isEmpty();
    }

    public static RecipeData undo(String id) {
        Deque<RecipeData> h = HISTORY.get(id);
        if (h == null || h.isEmpty()) return null;
        RecipeData previous = h.removeFirst();
        ADDED.removeIf(r -> r.recipeId.equals(id));
        ADDED.add(previous);
        writeAddScript();
        CraftEditorState.save();
        return previous;
    }

    public static void setEnabled(String recipeId, boolean enabled) {
        RecipeData r = ADDED.stream().filter(x -> x.recipeId.equals(recipeId)).findFirst().orElse(null);
        if (r == null) return;
        pushHistory(recipeId, r);
        r.enabled = enabled;
        writeAddScript();
        CraftEditorState.save();
    }

    public static void removeVanillaRecipe(String recipeId) {
        if (!REMOVED.contains(recipeId)) REMOVED.add(recipeId);
        writeRemoveScript();
        CraftEditorState.save();
    }

    public static void unremoveVanillaRecipe(String recipeId) {
        REMOVED.remove(recipeId);
        writeRemoveScript();
        CraftEditorState.save();
    }

    public static List<RecipeData> getAddedRecipes()  { return Collections.unmodifiableList(ADDED); }
    public static List<String>     getRemovedRecipes() { return Collections.unmodifiableList(REMOVED); }

    public static void clearAll() { ADDED.clear(); REMOVED.clear(); HISTORY.clear(); APPLIED_ADDED = List.of(); APPLIED_REMOVED = List.of(); }

    public static void loadRecipe(com.kve.common.recipe.RecipeData r) {
        ADDED.removeIf(x -> x.recipeId.equals(r.recipeId));
        ADDED.add(r);
    }

    public static void loadRemovedRecipe(String id) {
        if (!REMOVED.contains(id)) REMOVED.add(id);
    }

    private static void writeAddScript() {
        markAppliedAdded();
        Path dir = KveScriptPaths.recipesDir();
        try { Files.createDirectories(dir); } catch (IOException e) { return; }

        LAST_WRITE_WARNINGS.clear();

        Map<String, List<RecipeData>> byMod = new LinkedHashMap<>();
        for (RecipeData r : ADDED) byMod.computeIfAbsent(recipeModId(r), k -> new ArrayList<>()).add(r);

        Set<String> writtenMods = new LinkedHashSet<>();
        for (Map.Entry<String, List<RecipeData>> e : byMod.entrySet()) {
            String mod = e.getKey();
            StringBuilder sb = new StringBuilder();
            sb.append("// Shovel JS - Custom Recipes: ").append(mod).append(" (AUTO-GENERATED)\n");
            sb.append("// Run /reload after changes.\n\n");
            sb.append("ServerEvents.recipes(event => {\n\n");
            for (RecipeData r : e.getValue()) {
                if (!r.enabled) {
                    sb.append("  // DISABLED: ").append(r.recipeId).append("\n\n");
                    continue;
                }
                List<String> semantic = semanticErrors(r);
                if (!semantic.isEmpty()) {
                    String reason = String.join("; ", semantic);
                    sb.append("  // OMITIDO (fallo la validacion semantica): ").append(r.recipeId)
                      .append(" - ").append(reason).append("\n\n");
                    String warning = "Receta '" + r.recipeId + "' omitida: " + reason;
                    LAST_WRITE_WARNINGS.add(warning);
                    CraftEditorMod.LOGGER.warn("[Shovel JS] {}", warning);
                    continue;
                }
                if (!r.category.isEmpty()) sb.append("  // category: ").append(r.category).append("\n");
                for (String note : r.notes.split("\n")) {
                    if (!note.isBlank()) sb.append("  // nota: ").append(note.trim()).append("\n");
                }
                sb.append(buildRecipeJS(r));
                sb.append("\n");
            }
            sb.append("});\n");
            try { Files.createDirectories(KveScriptPaths.recipeModDir(mod)); } catch (IOException ex) {
                CraftEditorMod.LOGGER.error("[CraftEditor] No se pudo crear la carpeta del mod {}: {}", mod, ex.getMessage());
                continue;
            }
            writeSafe(KveScriptPaths.recipeAddFile(mod), sb.toString());
            writtenMods.add(KveScriptPaths.sanitize(mod));
        }
        pruneStaleRecipeDirs(dir);
        deleteLegacyAddScript();
        ProjectEEmcExporter.write(ADDED);
    }

    private static void deleteLegacyAddScript() {
        try {
            Files.deleteIfExists(KveScriptPaths.SERVER_ROOT.resolve("shovel_js_add.js"));
        } catch (IOException ignored) {}
    }

    /** Public boot-time entry point for {@code CraftEditorMod#commonSetup} - same cleanup as {@link #deleteLegacyAddScript()}, reachable before any Recipe Editor Save this session. */
    public static void deleteLegacyAddScriptOnBoot() { deleteLegacyAddScript(); }

    private static void writeRemoveScript() {
        markAppliedRemoved();
        Path dir = KveScriptPaths.recipesDir();
        try { Files.createDirectories(dir); } catch (IOException e) { return; }

        Map<String, List<String>> byMod = new LinkedHashMap<>();
        for (String id : REMOVED) byMod.computeIfAbsent(namespaceOf(id), k -> new ArrayList<>()).add(id);

        Set<String> writtenMods = new LinkedHashSet<>();
        for (Map.Entry<String, List<String>> e : byMod.entrySet()) {
            String mod = e.getKey();
            StringBuilder sb = new StringBuilder();
            sb.append("// Shovel JS - Recipe Removals: ").append(mod).append(" (AUTO-GENERATED)\n\n");
            sb.append("ServerEvents.recipes(event => {\n\n");
            for (String id : e.getValue()) {
                sb.append("  event.remove({ id: '").append(id).append("' });\n");
            }
            sb.append("\n});\n");
            try { Files.createDirectories(KveScriptPaths.recipeModDir(mod)); } catch (IOException ex) {
                CraftEditorMod.LOGGER.error("[CraftEditor] No se pudo crear la carpeta del mod {}: {}", mod, ex.getMessage());
                continue;
            }
            writeSafe(KveScriptPaths.recipeRemoveFile(mod), sb.toString());
            writtenMods.add(KveScriptPaths.sanitize(mod));
        }
        pruneStaleRecipeDirs(KveScriptPaths.recipesDir());
    }

    private static void pruneStaleRecipeDirs(Path recipesDir) {
        Set<String> modsWithAdds = new LinkedHashSet<>();
        for (RecipeData r : ADDED) modsWithAdds.add(KveScriptPaths.sanitize(recipeModId(r)));
        Set<String> modsWithRemoves = new LinkedHashSet<>();
        for (String id : REMOVED) modsWithRemoves.add(KveScriptPaths.sanitize(namespaceOf(id)));

        Set<String> keep = new LinkedHashSet<>(modsWithAdds);
        keep.addAll(modsWithRemoves);
        pruneStaleModDirs(recipesDir, keep);

        for (String mod : keep) {
            Path modDir = KveScriptPaths.recipeModDir(mod);
            if (!modsWithAdds.contains(mod)) {
                try { Files.deleteIfExists(modDir.resolve("add.js")); } catch (IOException ignored) {}
            }
            if (!modsWithRemoves.contains(mod)) {
                try { Files.deleteIfExists(modDir.resolve("remove.js")); } catch (IOException ignored) {}
            }
        }
    }

    private static void writeSafe(Path path, String content) {
        if (ScriptWriteGuard.writeIfValid(path, content, "shovel_js_recipes")) {
            CraftEditorMod.LOGGER.info("[CraftEditor] Wrote: {}", path.toAbsolutePath());
        }
    }

    /**
     * Renders the exact KubeJS script line(s) a recipe would produce,
     * without writing anything to disk or touching ADDED/history - used by
     * the "Preview" button in the editor so the player can see the real
     * generated code before saving. Pure/read-only: safe to call every
     * frame or on every keystroke.
     */
    public static String previewRecipeJS(RecipeData r) {
        if (r == null) return "// (sin datos de receta)";
        try {
            return buildRecipeJS(r);
        } catch (Exception e) {
            return "// ERROR generando preview: " + e.getMessage();
        }
    }

    private static String buildRecipeJS(RecipeData r) {
        if (ProjectEEmcExporter.handles(r)) return ProjectEEmcExporter.jsComment(r);
        if (r.type == RecipeData.RecipeType.CUSTOM_JSON) return customJson(r);
        if (r.type == RecipeData.RecipeType.CUSTOM_MACHINE) return customMachineJson(r);
        String outputId = itemId(r.output);
        if (outputId == null) return "  // SKIPPED (no output): " + r.recipeId + "\n";
        String output = itemJs(r.output);
        return switch (r.type) {
            case VANILLA_SHAPED     -> vanillaShaped(r, output);
            case VANILLA_SHAPELESS  -> vanillaShapeless(r, output);
            case EC_TABLE_SHAPED    -> ecTableShaped(r, output);
            case EC_TABLE_SHAPELESS -> ecTableShapeless(r, output);
            case EC_ENDER_SHAPED    -> ecEnderShaped(r, output);
            case EC_ENDER_SHAPELESS -> ecEnderShapeless(r, output);
            case EC_FLUX_SHAPED     -> ecFluxShaped(r, output);
            case EC_FLUX_SHAPELESS  -> ecFluxShapeless(r, output);
            case EC_COMBINATION     -> ecCombination(r, output);
            case CUSTOM_JSON        -> customJson(r);
            case CUSTOM_MACHINE     -> customMachineJson(r);
        };
    }

    private static String customJson(RecipeData r) {
        if (r.customTypeId == null || r.customTypeId.isEmpty())
            return "  // SKIPPED (no recipe type): " + r.recipeId + "\n";

        String unsupported = unsupportedWarning(r);
        if (unsupported != null) return unsupported;

        String dedicated = tryDedicatedCall(r);
        if (dedicated != null) return dedicated;

        String nativeJson = tryRawJsonTemplate(r);
        if (nativeJson != null) return nativeJson;

        String addonOnly = addonOnlyWarning(r);
        if (addonOnly != null) return addonOnly;

        if ("create:mechanical_crafting".equals(r.customTypeId)) {
            return customJsonCreateMechanicalCrafting(r);
        }

        if ("create:sequenced_assembly".equals(r.customTypeId)) {
            return customJsonCreateSequencedAssembly(r);
        }

        if ("twilightforest:uncrafting".equals(r.customTypeId)) {
            return customJsonTwilightUncrafting(r);
        }

        if (r.customTypeId.toLowerCase(Locale.ROOT).contains("smithing")) {
            return customJsonSmithing(r);
        }

        String output = itemId(r.output);

        StringBuilder sb = new StringBuilder();
        sb.append("  // ").append(r.recipeId).append(" [").append(r.customTypeId).append("]\n");
        sb.append("  event.custom({\n");
        sb.append("    type: '").append(r.customTypeId).append("',\n");

        Set<String> emitted = new java.util.HashSet<>();
        emitted.add("type");

        List<String> ingIds = new ArrayList<>();
        for (ItemStack s : r.shapelessIngredients) {
            String id = itemId(s);
            if (id != null) ingIds.add(itemToJsonFragment(s));
        }
        if (!ingIds.isEmpty()) {
            if (ingIds.size() == 1) {
                sb.append("    ingredient: ").append(ingIds.get(0)).append(",\n");
                emitted.add("ingredient");
            } else {
                sb.append("    ingredients: [\n");
                for (int i = 0; i < ingIds.size(); i++) {
                    sb.append("      ").append(ingIds.get(i));
                    sb.append(i < ingIds.size() - 1 ? ",\n" : "\n");
                }
                sb.append("    ],\n");
                emitted.add("ingredients");
            }
        }

        if (output != null) {
            sb.append("    result: { item: '").append(output).append("'");
            if (r.output.getCount() > 1) sb.append(", count: ").append(r.output.getCount());
            if (hasMeaningfulTag(r.output)) {
                String nbt = JsStrings.escape(Objects.requireNonNull(r.output.getTag()).toString());
                sb.append(", nbt: '").append(nbt).append("'");
            }
            sb.append(" },\n");
            emitted.add("result");
        }

        String secondary = itemId(r.secondaryOutput);
        if (secondary != null) {
            sb.append("    secondaryOutput: { item: '").append(secondary).append("'");
            if (r.secondaryOutput.getCount() > 1) sb.append(", count: ").append(r.secondaryOutput.getCount());
            sb.append(" },\n");
            sb.append("    secondaryChance: ").append(clampChance(r.secondaryOutputChance)).append(",\n");
            emitted.add("secondaryOutput");
            emitted.add("secondaryChance");
        }

        appendExtraJson(sb, r, emitted);

        sb.append("  }).id('shovel_js:").append(sanitizeId(r.recipeId)).append("');\n");
        return sb.toString();
    }

    private static String customJsonSmithing(RecipeData r) {
        List<ItemStack> slots = r.ingredients;
        ItemStack template = slots != null && slots.size() > 0 ? slots.get(0) : ItemStack.EMPTY;
        ItemStack base      = slots != null && slots.size() > 1 ? slots.get(1) : ItemStack.EMPTY;
        ItemStack addition  = slots != null && slots.size() > 2 ? slots.get(2) : ItemStack.EMPTY;

        StringBuilder sb = new StringBuilder();
        sb.append("  // ").append(r.recipeId).append(" [").append(r.customTypeId).append("]\n");
        sb.append("  event.custom({\n");
        sb.append("    type: '").append(r.customTypeId).append("',\n");

        Set<String> emitted = new java.util.HashSet<>();
        emitted.add("type");

        if (!template.isEmpty()) { sb.append("    template: ").append(itemToJsonFragment(template)).append(",\n"); emitted.add("template"); }
        if (!base.isEmpty())     { sb.append("    base: ").append(itemToJsonFragment(base)).append(",\n"); emitted.add("base"); }
        if (!addition.isEmpty()) { sb.append("    addition: ").append(itemToJsonFragment(addition)).append(",\n"); emitted.add("addition"); }

        boolean isTrim = r.customTypeId.toLowerCase(Locale.ROOT).contains("trim");
        String output = itemId(r.output);
        if (!isTrim && output != null) {
            sb.append("    result: { item: '").append(output).append("'");
            if (r.output.getCount() > 1) sb.append(", count: ").append(r.output.getCount());
            if (hasMeaningfulTag(r.output)) {
                String nbt = JsStrings.escape(Objects.requireNonNull(r.output.getTag()).toString());
                sb.append(", nbt: '").append(nbt).append("'");
            }
            sb.append(" },\n");
            emitted.add("result");
        }

        appendExtraJson(sb, r, emitted);

        sb.append("  }).id('shovel_js:").append(sanitizeId(r.recipeId)).append("');\n");
        return sb.toString();
    }

    private static String customMachineJson(RecipeData r) {
        if (r.customTypeId == null || r.customTypeId.isEmpty())
            return "  // SKIPPED (no recipe type): " + r.recipeId + "\n";

        String unsupported = unsupportedWarning(r);
        if (unsupported != null) return unsupported;

        String dedicated = tryDedicatedCall(r);

        if (r.customTypeId != null && r.customTypeId.equalsIgnoreCase("create:pressing")) {
            return customMachineJsonCreatePressing(r);
        }

        if (dedicated != null) return dedicated;

        String nativeJson = tryRawJsonTemplate(r);
        if (nativeJson != null) return nativeJson;

        String addonOnly = addonOnlyWarning(r);
        if (addonOnly != null) return addonOnly;

        StringBuilder sb = new StringBuilder();
        sb.append("  // ").append(r.recipeId).append(" [").append(r.customTypeId).append("] (machine)\n");
        sb.append("  event.custom({\n");
        sb.append("    type: '").append(r.customTypeId).append("',\n");

        Set<String> emitted = new java.util.HashSet<>();
        emitted.add("type");
        if (appendMachineSlotArray(sb, "inputs", r.machineInputs))  emitted.add("inputs");
        if (appendMachineSlotArray(sb, "outputs", r.machineOutputs)) emitted.add("outputs");

        appendExtraJson(sb, r, emitted);

        sb.append("  }).id('shovel_js:").append(sanitizeId(r.recipeId)).append("');\n");
        return sb.toString();
    }

    private static String customMachineJsonCreatePressing(RecipeData r) {
        StringBuilder sb = new StringBuilder();
        sb.append("  // ").append(r.recipeId).append(" [").append(r.customTypeId).append("] (machine, raw JSON - ver bug 2026-08-25 en customMachineJson)\n");
        sb.append("  event.custom({\n");
        sb.append("    type: '").append(r.customTypeId).append("',\n");

        Set<String> emitted = new java.util.HashSet<>();
        emitted.add("type");
        if (appendMachineSlotArray(sb, "ingredients", r.machineInputs)) emitted.add("ingredients");
        if (appendMachineSlotArray(sb, "results", r.machineOutputs))   emitted.add("results");

        appendExtraJson(sb, r, emitted);

        sb.append("  }).id('shovel_js:").append(sanitizeId(r.recipeId)).append("');\n");
        return sb.toString();
    }

    private static boolean appendMachineSlotArray(StringBuilder sb, String key, List<RecipeData.MachineSlot> slots) {
        List<String> frags = new ArrayList<>();
        for (RecipeData.MachineSlot s : slots) {
            String f = machineSlotFragment(s);
            if (f != null) frags.add(f);
        }
        if (frags.isEmpty()) return false;
        sb.append("    ").append(key).append(": [\n");
        for (int i = 0; i < frags.size(); i++) {
            sb.append("      ").append(frags.get(i));
            sb.append(i < frags.size() - 1 ? ",\n" : "\n");
        }
        sb.append("    ],\n");
        return true;
    }

    private static String firstMachineSlotJs(List<RecipeData.MachineSlot> slots) {
        for (RecipeData.MachineSlot s : slots) {
            String f = machineSlotFragment(s);
            if (f != null) return f;
        }
        return null;
    }

    private static String machineSlotArrayJs(List<RecipeData.MachineSlot> slots) {
        List<String> frags = new ArrayList<>();
        for (RecipeData.MachineSlot s : slots) {
            String f = machineSlotFragment(s);
            if (f != null) frags.add(f);
        }
        if (frags.isEmpty()) return null;
        return "[" + String.join(", ", frags) + "]";
    }

    public static String sourceFluidId(String id) {
        if (id == null) return "";
        String t = id.trim();
        if (t.isEmpty() || t.startsWith("#")) return t;
        try {
            ResourceLocation rl = ResourceLocation.tryParse(t);
            if (rl == null || !BuiltInRegistries.FLUID.containsKey(rl)) return t;
            net.minecraft.world.level.material.Fluid f = BuiltInRegistries.FLUID.get(rl);
            if (f instanceof net.minecraft.world.level.material.FlowingFluid ff && !f.isSource(f.defaultFluidState())) {
                ResourceLocation src = BuiltInRegistries.FLUID.getKey(ff.getSource());
                if (src != null) return src.toString();
            }
        } catch (Throwable ignored) {
        }
        return t;
    }

    private static String machineSlotFragment(RecipeData.MachineSlot s) {
        if (!isValidMachineSlot(s)) return null;
        String base = switch (s.type) {
            case ITEM -> itemToJsonFragment(s.item);
            case FLUID -> "{ fluid: '" + sourceFluidId(s.fluidId) + "', amount: " + Math.max(1, s.fluidAmount) + " }";
            case TAG -> {
                String tag = s.tagId.startsWith("#") ? s.tagId : "#" + s.tagId;
                StringBuilder o = new StringBuilder("{ tag: '").append(tag).append("'");
                if (s.tagCount > 1) o.append(", count: ").append(s.tagCount);
                o.append(" }");
                yield o.toString();
            }
        };
        double c = clampChance(s.chance);
        if (c >= 1.0 || base == null || !base.endsWith("}")) return base;
        return base.substring(0, base.length() - 1).stripTrailing() + ", chance: " + c + " }";
    }

    private static String machineSlotFragmentTuple(RecipeData.MachineSlot s) {
        if (s.type != RecipeData.MachineSlotType.FLUID) return null;
        if (!isValidMachineSlot(s)) return null;
        return "['" + sourceFluidId(s.fluidId) + "', " + Math.max(1, s.fluidAmount) + "]";
    }

    private static String nthMachineFluidSlotTupleJs(List<RecipeData.MachineSlot> slots, int n) {
        int seen = 0;
        for (RecipeData.MachineSlot s : slots) {
            String f = machineSlotFragmentTuple(s);
            if (f == null) continue;
            seen++;
            if (seen == n) return f;
        }
        return null;
    }

    private static String machineSlotArrayTupleJs(List<RecipeData.MachineSlot> slots) {
        List<String> frags = new ArrayList<>();
        for (RecipeData.MachineSlot s : slots) {
            String f = machineSlotFragmentTuple(s);
            if (f != null) frags.add(f);
        }
        if (frags.isEmpty()) return null;
        return "[" + String.join(", ", frags) + "]";
    }

    private static String addonOnlyWarning(RecipeData r) {
        Optional<RecipeTypeDescriptor> found = RecipeTypeDescriptorRegistry.find(r.customTypeId);
        if (found.isEmpty()) return null;
        RecipeTypeDescriptor d = found.get();
        if (d.dedicatedCall == null || d.hasNativeExport()) return null;
        String addon = d.dedicatedCall.requiresAddon;
        if (addon == null || addon.isBlank()) return null;
        String mod = d.dedicatedCall.requiresMod;
        StringBuilder sb = new StringBuilder();
        sb.append("  // ==== NO EXPORTADO: ").append(r.recipeId).append(" [").append(r.customTypeId).append("] ====\n");
        sb.append("  // Shovel JS todavia no tiene el formato nativo verificado de este tipo: solo se\n");
        sb.append("  // puede generar con el addon de KubeJS '").append(addon).append("'");
        if (mod != null && !mod.isBlank()) sb.append(" (y el mod '").append(mod).append("')");
        sb.append(".\n");
        sb.append("  // Instalalo (o habilitalo en Ajustes > addons) y volve a exportar; sin el\n");
        sb.append("  // addon la receta saldria con claves que el mod no reconoce y no cargaria.\n");
        return sb.toString();
    }

    private static String unsupportedWarning(RecipeData r) {
        Optional<RecipeTypeDescriptor> found = RecipeTypeDescriptorRegistry.find(r.customTypeId);
        if (found.isEmpty() || !found.get().isUnsupported()) return null;
        String reason = found.get().unsupportedReason;
        StringBuilder sb = new StringBuilder();
        sb.append("  // ==== NO EXPORTADO: ").append(r.recipeId).append(" [").append(r.customTypeId).append("] ====\n");
        sb.append("  // Este tipo de receta esta marcado \"unsupported\" en el catalogo de Shovel JS\n");
        sb.append("  // (data/shovel_js/recipe_types/*.json).\n");
        if (reason != null && !reason.isBlank()) {
            for (String line : reason.split("\n")) sb.append("  // ").append(line).append("\n");
        }
        sb.append("  // No se genero el registro de la receta, para no cargar algo roto en silencio.\n");
        return sb.toString();
    }

    private static String customJsonCreateMechanicalCrafting(RecipeData r) {
        String output = itemId(r.output);
        if (output == null) return "  // SKIPPED (no output): " + r.recipeId + "\n";

        Map<String, Character> keyMap = new LinkedHashMap<>();
        String[] rows = shapedJsonRows(r, keyMap);

        boolean acceptMirrored = true;
        JsonObject extraObj = parseExtraJsonSafe(r.customExtraJson);
        if (extraObj != null && extraObj.has("acceptMirrored")) {
            acceptMirrored = extraObj.get("acceptMirrored").getAsBoolean();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("  // ").append(r.recipeId).append(" [create:mechanical_crafting]\n");
        sb.append("  event.custom({\n");
        sb.append("    type: 'create:mechanical_crafting',\n");
        appendPatternAndKey(sb, rows, keyMap);
        sb.append("    result: { item: '").append(output).append("'");
        if (r.output.getCount() > 1) sb.append(", count: ").append(r.output.getCount());
        sb.append(" },\n");
        sb.append("    acceptMirrored: ").append(acceptMirrored).append("\n");
        sb.append("  }).id('shovel_js:").append(sanitizeId(r.recipeId)).append("');\n");
        return sb.toString();
    }

    private static String[] shapedJsonRows(RecipeData r, Map<String, Character> keyMap) {
        char next = 'A';
        String[] rows = new String[Math.max(1, r.gridHeight)];
        java.util.Arrays.fill(rows, "");
        for (int row = 0; row < r.gridHeight; row++) {
            StringBuilder pat = new StringBuilder();
            for (int col = 0; col < r.gridWidth; col++) {
                int idx = row * r.gridWidth + col;
                ItemStack s = r.ingredients != null && idx < r.ingredients.size() ? r.ingredients.get(idx) : ItemStack.EMPTY;
                if (s.isEmpty()) { pat.append(' '); continue; }
                String frag = itemToJsonFragment(s);
                if (!keyMap.containsKey(frag)) {
                    keyMap.put(frag, next++);
                    if (next > 'Z') next = 'a';
                }
                pat.append(keyMap.get(frag));
            }
            rows[row] = pat.toString();
        }
        int last = rows.length - 1;
        while (last > 0 && rows[last].isBlank()) last--;
        return java.util.Arrays.copyOf(rows, last + 1);
    }

    private static void appendPatternAndKey(StringBuilder sb, String[] rows, Map<String, Character> keyMap) {
        sb.append("    pattern: [\n");
        for (int i = 0; i < rows.length; i++) {
            sb.append("      '").append(rows[i]).append("'");
            sb.append(i < rows.length - 1 ? ",\n" : "\n");
        }
        sb.append("    ],\n");
        sb.append("    key: {\n");
        int idx = 0;
        for (Map.Entry<String, Character> e : keyMap.entrySet()) {
            sb.append("      '").append(e.getValue()).append("': ").append(e.getKey());
            sb.append(++idx < keyMap.size() ? ",\n" : "\n");
        }
        sb.append("    },\n");
    }

    /**
     * Dedicated raw-JSON export for {@code twilightforest:uncrafting}
     * Shape confirmed against the real jar
     * (twilightforest-1.20.1-4.3.2508: UncraftingRecipe$Serializer.fromJson
     * + data/twilightforest/recipes/uncrafting/tipped_arrow.json):
     * {@code cost} (int, required), {@code input} = {@code {"ingredient":
     * <Ingredient>, "count"?: n}} (count defaults to 1), plus a vanilla-style
     * {@code pattern}/{@code key}. In the editor the grid is what the
     * Uncrafting Table GIVES BACK and the output slot is the item being
     * uncrafted (its stack size = the required count) - the only mapping
     * that fits KVE's grid + one-item model.
     */
    private static String customJsonTwilightUncrafting(RecipeData r) {
        String inputId = itemId(r.output);
        if (inputId == null) return "  // SKIPPED (twilightforest:uncrafting sin item a descraftear en la salida): " + r.recipeId + "\n";
        Map<String, Character> keyMap = new LinkedHashMap<>();
        String[] rows = shapedJsonRows(r, keyMap);
        if (keyMap.isEmpty()) return "  // SKIPPED (twilightforest:uncrafting sin resultados en la grilla): " + r.recipeId + "\n";

        long cost = 0;
        JsonObject extraObj = parseExtraJsonSafe(r.customExtraJson);
        if (extraObj != null && extraObj.has("cost")) {
            try { cost = extraObj.get("cost").getAsLong(); } catch (Exception ignored) {}
        }

        StringBuilder sb = new StringBuilder();
        sb.append("  // ").append(r.recipeId).append(" [twilightforest:uncrafting] (grilla = lo que devuelve la mesa; salida = item a descraftear)\n");
        sb.append("  event.custom({\n");
        sb.append("    type: 'twilightforest:uncrafting',\n");
        sb.append("    input: { ingredient: { item: '").append(inputId).append("' }");
        if (r.output.getCount() > 1) sb.append(", count: ").append(r.output.getCount());
        sb.append(" },\n");
        appendPatternAndKey(sb, rows, keyMap);
        sb.append("    cost: ").append(Math.max(0, cost)).append("\n");
        sb.append("  }).id('shovel_js:").append(sanitizeId(r.recipeId)).append("');\n");
        return sb.toString();
    }

    private static String customJsonCreateSequencedAssembly(RecipeData r) {
        ItemStack mainIngredient = ItemStack.EMPTY;
        if (r.shapelessIngredients != null) {
            for (ItemStack s : r.shapelessIngredients) {
                if (!s.isEmpty()) { mainIngredient = s; break; }
            }
        }
        if (mainIngredient.isEmpty()) {
            return "  // SKIPPED (create:sequenced_assembly sin ingredient de entrada): " + r.recipeId + "\n";
        }

        JsonObject extraObj = parseExtraJsonSafe(r.customExtraJson);
        JsonElement transitionalItem = extraObj != null ? extraObj.get("transitionalItem") : null;
        JsonElement sequence = extraObj != null ? extraObj.get("sequence") : null;
        JsonElement results = extraObj != null ? extraObj.get("results") : null;

        if (transitionalItem == null || sequence == null || results == null) {
            StringBuilder warn = new StringBuilder();
            warn.append("  // ==== NO EXPORTADO: ").append(r.recipeId).append(" [create:sequenced_assembly] ====\n");
            warn.append("  // Faltan campos obligatorios en el JSON extra: ");
            List<String> missing = new ArrayList<>();
            if (transitionalItem == null) missing.add("transitionalItem");
            if (sequence == null) missing.add("sequence");
            if (results == null) missing.add("results");
            warn.append(String.join(", ", missing)).append("\n");
            warn.append("  // \"sequence\" (array de sub-recetas create:cutting/pressing/deploying/filling)\n");
            warn.append("  // y \"results\" (array de {item,chance}) se escriben a mano en el cuadro\n");
            warn.append("  // \"Extra JSON\" - Shovel JS no tiene todavia un editor dedicado para armarlos.\n");
            warn.append("  // No se genero el registro de la receta, para no cargar algo roto en silencio.\n");
            return warn.toString();
        }

        int loops = 5;
        JsonElement loopsEl = extraObj.get("loops");
        if (loopsEl != null && loopsEl.isJsonPrimitive()) {
            try { loops = loopsEl.getAsInt(); } catch (Exception ignored) {}
        }

        StringBuilder sb = new StringBuilder();
        sb.append("  // ").append(r.recipeId).append(" [create:sequenced_assembly]\n");
        sb.append("  event.custom({\n");
        sb.append("    type: 'create:sequenced_assembly',\n");
        sb.append("    ingredient: ").append(itemToJsonFragment(mainIngredient)).append(",\n");
        sb.append("    transitionalItem: ").append(transitionalItem).append(",\n");
        sb.append("    loops: ").append(loops).append(",\n");
        sb.append("    sequence: ").append(sequence).append(",\n");
        sb.append("    results: ").append(results).append("\n");
        sb.append("  }).id('shovel_js:").append(sanitizeId(r.recipeId)).append("');\n");
        return sb.toString();
    }

    private static String tryRawJsonTemplate(RecipeData r) {
        Optional<RecipeTypeDescriptor> found = RecipeTypeDescriptorRegistry.find(r.customTypeId);
        if (found.isEmpty() || !found.get().hasRawJson()) return null;
        return RawJsonTemplate.exportJs(r, found.get(), sanitizeId(r.recipeId));
    }

    private static String tryDedicatedCall(RecipeData r) {
        Optional<RecipeTypeDescriptor> found = RecipeTypeDescriptorRegistry.find(r.customTypeId);
        if (found.isEmpty()) return null;
        RecipeTypeDescriptor d = found.get();
        if (!RecipeTypeDescriptorRegistry.shouldUseDedicatedCall(d)) return null;

        String template = d.dedicatedCall.argsTemplate;
        if (template == null || template.isBlank()) return null;

        // {output}/{input}/{input2}/{inputs} are populated from the
        // CUSTOM_JSON-only r.output/r.shapelessIngredients fields, which
        // are ALWAYS empty for CUSTOM_MACHINE recipes (see the comment
        // below) - i.e. every recipe type that has a slotLayout (routed
        // to the typed-slot machine editor). Since most descriptors write
        // these exact singular tokens because they match the real addon's
        // KubeJS call signature 1:1 (e.g. createCrushing(output, input),
        // mekanism.pulverizer(output, input), thermal.smelter(output,
        // input)), falling straight through to "null -> bail out" here
        // meant the dedicated call NEVER fired for almost any machine-
        // editor recipe type, no matter the mod/addon/preferOverRawJson -
        // silently falling back to the generic raw-JSON export instead
        // (which uses made-up "inputs"/"outputs" keys that don't match
        // most mods' real codec). Falling back to the machine slots here
        // (same item/fluid/tag data, just read from machineOutputs/
        // machineInputs instead) makes those already-correct templates
        // actually usable, without changing anything for genuine
        // CUSTOM_JSON recipes (whose machineInputs/machineOutputs are
        // themselves always empty, so this fallback is a no-op for them).
        String outputJs = itemJs(r.output);
        if (outputJs == null) outputJs = firstMachineSlotJs(r.machineOutputs);
        if (template.contains("{output}") && outputJs == null) return null;

        List<ItemStack> ings = r.shapelessIngredients;
        String input1 = !ings.isEmpty() ? itemJs(ings.get(0)) : null;
        String input2 = ings.size() > 1 ? itemJs(ings.get(1)) : null;
        if (input1 == null) input1 = firstMachineSlotJs(r.machineInputs);
        if (input2 == null && ings.size() <= 1) {
            int seen = 0;
            for (RecipeData.MachineSlot s : r.machineInputs) {
                String js = machineSlotFragment(s);
                if (js == null) continue;
                seen++;
                if (seen == 2) { input2 = js; break; }
            }
        }
        if (template.contains("{input}") && input1 == null) return null;
        if (template.contains("{input2}") && input2 == null) return null;

        String inputsArray = null;
        if (template.contains("{inputs}")) {
            List<String> frags = new ArrayList<>();
            for (ItemStack s : ings) {
                String js = itemJs(s);
                if (js != null) frags.add(js);
            }
            if (frags.isEmpty()) {
                String machineFrags = machineSlotArrayJs(r.machineInputs);
                if (machineFrags == null) return null;
                inputsArray = machineFrags;
            } else {
                inputsArray = "[" + String.join(", ", frags) + "]";
            }
        }

        String machineInputSingle = firstMachineSlotJs(r.machineInputs);
        String machineOutputSingle = firstMachineSlotJs(r.machineOutputs);
        if (template.contains("{machineInput}") && machineInputSingle == null) return null;
        if (template.contains("{machineOutput}") && machineOutputSingle == null) return null;

        String machineInputsArray = null;
        if (template.contains("{machineInputs}")) {
            machineInputsArray = machineSlotArrayJs(r.machineInputs);
            if (machineInputsArray == null) return null;
        }
        String machineOutputsArray = null;
        if (template.contains("{machineOutputs}")) {
            machineOutputsArray = machineSlotArrayJs(r.machineOutputs);
            if (machineOutputsArray == null) return null;
        }

        String machineInputTuple1 = nthMachineFluidSlotTupleJs(r.machineInputs, 1);
        String machineInputTuple2 = nthMachineFluidSlotTupleJs(r.machineInputs, 2);
        String machineOutputTuple1 = nthMachineFluidSlotTupleJs(r.machineOutputs, 1);
        String machineOutputTuple2 = nthMachineFluidSlotTupleJs(r.machineOutputs, 2);
        if (template.contains("{machineInputTuple}") && machineInputTuple1 == null) return null;
        if (template.contains("{machineInputTuple2}") && machineInputTuple2 == null) return null;
        if (template.contains("{machineOutputTuple}") && machineOutputTuple1 == null) return null;
        if (template.contains("{machineOutputTuple2}") && machineOutputTuple2 == null) return null;

        String machineInputsTupleArray = null;
        if (template.contains("{machineInputsTuple}")) {
            machineInputsTupleArray = machineSlotArrayTupleJs(r.machineInputs);
            if (machineInputsTupleArray == null) return null;
        }
        String machineOutputsTupleArray = null;
        if (template.contains("{machineOutputsTuple}")) {
            machineOutputsTupleArray = machineSlotArrayTupleJs(r.machineOutputs);
            if (machineOutputsTupleArray == null) return null;
        }

        JsonObject extraObj = parseExtraJsonSafe(r.customExtraJson);
        for (RecipeTypeDescriptor.ExtraField f : d.extraFields) {
            String placeholder = "{field:" + f.key + "}";
            if (!template.contains(placeholder)) continue;
            JsonElement val = (extraObj != null && extraObj.has(f.key)) ? extraObj.get(f.key) : null;
            boolean blank = val != null && val.isJsonPrimitive() && val.getAsJsonPrimitive().isString()
                    && val.getAsString().isBlank();
            if (val == null || blank) {
                if (f.required) return null;
                template = template.replace(placeholder, f.repeatable ? "[]" : dedicatedDefaultLiteral(f));
                continue;
            }
            if (f.repeatable) {
                JsonArray filtered = new JsonArray();
                if (val.isJsonArray()) {
                    for (JsonElement el : val.getAsJsonArray()) {
                        if (!isBlankStringElement(el)) filtered.add(el);
                    }
                } else if (!isBlankStringElement(val)) {
                    filtered.add(val);
                }
                template = template.replace(placeholder, filtered.toString());
                continue;
            }
            template = template.replace(placeholder, dedicatedValueLiteral(val));
        }

        String extra = buildDedicatedExtra(r, d);
        String sanitizedId = sanitizeId(r.recipeId);

        String line = template
                .replace("{output}", outputJs == null ? "" : outputJs)
                .replace("{input2}", input2 == null ? "" : input2)
                .replace("{input}", input1 == null ? "" : input1)
                .replace("{inputs}", inputsArray == null ? "" : inputsArray)
                .replace("{machineOutput}", machineOutputSingle == null ? "" : machineOutputSingle)
                .replace("{machineInput}", machineInputSingle == null ? "" : machineInputSingle)
                .replace("{machineOutputs}", machineOutputsArray == null ? "" : machineOutputsArray)
                .replace("{machineInputs}", machineInputsArray == null ? "" : machineInputsArray)
                .replace("{machineOutputTuple2}", machineOutputTuple2 == null ? "" : machineOutputTuple2)
                .replace("{machineOutputTuple}", machineOutputTuple1 == null ? "" : machineOutputTuple1)
                .replace("{machineInputTuple2}", machineInputTuple2 == null ? "" : machineInputTuple2)
                .replace("{machineInputTuple}", machineInputTuple1 == null ? "" : machineInputTuple1)
                .replace("{machineOutputsTuple}", machineOutputsTupleArray == null ? "" : machineOutputsTupleArray)
                .replace("{machineInputsTuple}", machineInputsTupleArray == null ? "" : machineInputsTupleArray)
                .replace("{id}", sanitizedId)
                .replace("{extra}", extra);

        StringBuilder sb = new StringBuilder();
        String gate = !d.dedicatedCall.requiresMod.isBlank() && !d.dedicatedCall.requiresAddon.isBlank()
                ? d.dedicatedCall.requiresMod + " + " + d.dedicatedCall.requiresAddon + " (opted in)"
                : !d.dedicatedCall.requiresMod.isBlank()
                        ? d.dedicatedCall.requiresMod
                        : d.dedicatedCall.requiresAddon + " (opted in via shovel_js_addon_flags.json)";
        sb.append("  // ").append(r.recipeId).append(" [").append(r.customTypeId)
          .append("] - using ").append(gate).append("'s dedicated KubeJS call")
          .append(" (descriptor: ").append(d.effectiveDisplayName()).append(")\n");
        if (d.dedicatedCall.skipAutoId) {
            sb.append("  ").append(line).append(";\n");
        } else {
            sb.append("  ").append(line).append(".id('shovel_js:").append(sanitizedId).append("');\n");
        }
        return sb.toString();
    }

    private static String buildDedicatedExtra(RecipeData r, RecipeTypeDescriptor d) {
        Map<String, String> templates = d.dedicatedCall.extraFieldCallTemplates;
        if (templates.isEmpty()) return "";
        JsonObject extraObj = parseExtraJsonSafe(r.customExtraJson);
        if (extraObj == null) return "";

        StringBuilder sb = new StringBuilder();
        for (RecipeTypeDescriptor.ExtraField f : d.extraFields) {
            String tmpl = templates.get(f.key);
            if (tmpl == null || !extraObj.has(f.key)) continue;
            JsonElement val = extraObj.get(f.key);

            if (f.repeatable) {
                JsonArray elements;
                if (val.isJsonArray()) {
                    elements = val.getAsJsonArray();
                } else {
                    elements = new JsonArray();
                    elements.add(val);
                }
                int idx = 0;
                for (JsonElement el : elements) {
                    if (isBlankStringElement(el)) continue;
                    sb.append(tmpl.replace("{value}", dedicatedValueLiteral(el)).replace("{index}", String.valueOf(idx)));
                    idx++;
                }
            } else {
                if (isBlankStringElement(val)) continue;
                if (!tmpl.contains("{value}")) {
                    // Presence-by-boolean template (e.g. Create Deploying's
                    // ".keepHeldItem"): the real addon method takes NO
                    // arguments - its mere presence in the chain IS the
                    // flag, unlike every other extra field where {value}
                    // is threaded into the call. A template author signals
                    // this by simply omitting {value} from the template.
                    // Only emit the call when the field is actually
                    // true - emitting it unconditionally would mean the
                    // dedicated call always enables the flag regardless of
                    // what the player picked; false (or any non-boolean
                    // value) means "don't call it at all", same as if the
                    // extra field were never set.
                    boolean truthy = val.isJsonPrimitive() && val.getAsJsonPrimitive().isBoolean() && val.getAsBoolean();
                    if (truthy) sb.append(tmpl);
                    continue;
                }
                sb.append(tmpl.replace("{value}", dedicatedValueLiteral(val)));
            }
        }
        return sb.toString();
    }

    private static boolean isBlankStringElement(JsonElement el) {
        return el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isString() && el.getAsString().isBlank();
    }

    private static String dedicatedDefaultLiteral(RecipeTypeDescriptor.ExtraField f) {
        String val = f.defaultValue == null ? "" : f.defaultValue;
        if (!f.isStringType()) {
            if (!val.isEmpty()) return val;
            return "boolean".equals(f.type) ? "false" : "0";
        }
        return JsStrings.quote(val);
    }

    private static String dedicatedValueLiteral(JsonElement val) {
        return val.isJsonPrimitive() && val.getAsJsonPrimitive().isString()
                ? JsStrings.quote(val.getAsString())
                : val.toString();
    }

    static JsonObject parseExtraJsonSafe(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String toParse = raw.trim().startsWith("{") ? raw.trim() : "{" + raw.trim() + "}";
        try {
            JsonElement el = JsonParser.parseString(toParse);
            return el.isJsonObject() ? el.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static void appendExtraJson(StringBuilder sb, RecipeData r, Set<String> alreadyEmittedKeys) {
        String extra = r.customExtraJson == null ? "" : r.customExtraJson.trim();
        if (extra.isEmpty()) return;

        String toParse = extra.startsWith("{") ? extra : "{" + extra + "}";

        JsonObject extraObj;
        try {
            JsonElement parsed = JsonParser.parseString(toParse);
            if (!parsed.isJsonObject()) throw new JsonSyntaxException("not an object");
            extraObj = parsed.getAsJsonObject();
        } catch (JsonSyntaxException | IllegalStateException ex) {
            sb.append("    // WARNING: 'extra JSON' for this recipe is not valid JSON and was skipped: ")
              .append(ex.getMessage() == null ? "parse error" : ex.getMessage().replace("\n", " "))
              .append("\n");
            return;
        }

        for (Map.Entry<String, JsonElement> entry : extraObj.entrySet()) {
            if (alreadyEmittedKeys.contains(entry.getKey())) {
                sb.append("    // skipped duplicate key from extra JSON: ").append(entry.getKey()).append("\n");
                continue;
            }
            sb.append("    ").append(entry.getKey()).append(": ").append(entry.getValue()).append(",\n");
        }
    }

    private static double clampChance(double chance) {
        double c = Math.max(0.0, Math.min(1.0, chance));
        return Math.round(c * 100.0) / 100.0;
    }

    static String sanitizeId(String id) {
        return id.replaceAll("[^a-zA-Z0-9_./]", "_");
    }

    private static String vanillaShaped(RecipeData r, String output) {
        Map<String, Character> keyMap = new LinkedHashMap<>();
        char[] next = {'A'};
        String[] rows = buildPatternRows(r, keyMap, next);

        int last = rows.length - 1;
        while (last > 0 && rows[last].isBlank()) last--;

        StringBuilder sb = new StringBuilder();
        sb.append("  // ").append(r.recipeId).append("\n");
        sb.append("  event.shaped(\n");
        sb.append("    ").append(output).append(",\n");
        sb.append("    [\n");
        for (int i = 0; i <= last; i++) {
            sb.append("      '").append(rows[i]).append("'");
            if (i < last) sb.append(",");
            sb.append("\n");
        }
        sb.append("    ],\n    {\n");
        for (Map.Entry<String, Character> e : keyMap.entrySet()) {
            sb.append("      ").append(e.getValue()).append(": ").append(e.getKey()).append(",\n");
        }
        sb.append("    }\n  ).id('shovel_js:").append(r.recipeId).append("');\n");
        return sb.toString();
    }

    private static String vanillaShapeless(RecipeData r, String output) {
        StringBuilder sb = new StringBuilder();
        sb.append("  // ").append(r.recipeId).append("\n");
        sb.append("  event.shapeless(\n");
        sb.append("    ").append(output).append(",\n    [");
        List<String> ings = new ArrayList<>();
        for (ItemStack s : r.shapelessIngredients) { String js = itemJs(s); if (js != null) ings.add(js); }
        sb.append(String.join(", ", ings));
        sb.append("]\n  ).id('shovel_js:").append(r.recipeId).append("');\n");
        return sb.toString();
    }

    private static String ecTableShaped(RecipeData r, String output) {
        int tier = ecTierNum(r.ecTier);
        Map<String, Character> keyMap = new LinkedHashMap<>();
        char[] next = {'A'};
        String[] rows = buildPatternRows(r, keyMap, next);

        StringBuilder sb = new StringBuilder();
        sb.append("  // ").append(r.recipeId).append(" [EC Table tier ").append(tier).append("]\n");
        sb.append("  event.recipes.extendedcrafting.shaped_table(\n");
        sb.append("    ").append(output).append(",\n");
        sb.append("    [\n");
        for (int i = 0; i < rows.length; i++) {
            sb.append("      '").append(rows[i]).append("'");
            if (i < rows.length - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("    ],\n    {\n");
        for (Map.Entry<String, Character> e : keyMap.entrySet()) {
            sb.append("      ").append(e.getValue()).append(": ").append(e.getKey()).append(",\n");
        }
        sb.append("    }\n");
        sb.append("  ).tier(").append(tier).append(")\n");
        sb.append("  .id('shovel_js:").append(r.recipeId).append("');\n");
        return sb.toString();
    }

    private static String ecTableShapeless(RecipeData r, String output) {
        int tier = ecTierNum(r.ecTier);
        StringBuilder sb = new StringBuilder();
        sb.append("  // ").append(r.recipeId).append("\n");
        sb.append("  event.recipes.extendedcrafting.shapeless_table(\n");
        sb.append("    ").append(output).append(",\n");
        sb.append("    ").append(tier).append(",\n    [");
        List<String> ings = new ArrayList<>();
        for (ItemStack s : r.shapelessIngredients) { String js = itemJs(s); if (js != null) ings.add(js); }
        sb.append(String.join(", ", ings));
        sb.append("]\n  ).id('shovel_js:").append(r.recipeId).append("');\n");
        return sb.toString();
    }

    private static String ecEnderShaped(RecipeData r, String output) {
        Map<String, Character> keyMap = new LinkedHashMap<>();
        char[] next = {'A'};
        String[] rows = buildPatternRows(r, keyMap, next);
        StringBuilder sb = new StringBuilder();
        sb.append("  // ").append(r.recipeId).append(" [Ender Crafter time=").append(r.ecCraftingTime).append("t]\n");
        sb.append("  event.recipes.extendedcrafting.ender_crafter(\n");
        sb.append("    ").append(output).append(",\n");
        sb.append("    ").append(r.ecCraftingTime).append(",\n");
        sb.append("    [\n");
        for (String row : rows) sb.append("      '").append(row).append("',\n");
        sb.append("    ],\n    {\n");
        for (Map.Entry<String, Character> e : keyMap.entrySet()) {
            sb.append("      ").append(e.getValue()).append(": ").append(e.getKey()).append(",\n");
        }
        sb.append("    }\n  ).id('shovel_js:").append(r.recipeId).append("');\n");
        return sb.toString();
    }

    private static String ecEnderShapeless(RecipeData r, String output) {
        StringBuilder sb = new StringBuilder();
        sb.append("  // ").append(r.recipeId).append("\n");
        sb.append("  event.recipes.extendedcrafting.ender_crafter(\n");
        sb.append("    ").append(output).append(",\n");
        sb.append("    ").append(r.ecCraftingTime).append(",\n    [");
        List<String> ings = new ArrayList<>();
        for (ItemStack s : r.shapelessIngredients) { String js = itemJs(s); if (js != null) ings.add(js); }
        sb.append(String.join(", ", ings));
        sb.append("]\n  ).id('shovel_js:").append(r.recipeId).append("');\n");
        return sb.toString();
    }

    private static String ecFluxShaped(RecipeData r, String output) {
        Map<String, Character> keyMap = new LinkedHashMap<>();
        char[] next = {'A'};
        String[] rows = buildPatternRows(r, keyMap, next);
        StringBuilder sb = new StringBuilder();
        sb.append("  // ").append(r.recipeId).append(" [Flux Crafter power=").append(r.ecPowerRequired).append("RF]\n");
        sb.append("  event.recipes.extendedcrafting.flux_crafter(\n");
        sb.append("    ").append(output).append(",\n");
        sb.append("    ").append(r.ecPowerRequired).append(",\n");
        sb.append("    [\n");
        for (String row : rows) sb.append("      '").append(row).append("',\n");
        sb.append("    ],\n    {\n");
        for (Map.Entry<String, Character> e : keyMap.entrySet()) {
            sb.append("      ").append(e.getValue()).append(": ").append(e.getKey()).append(",\n");
        }
        sb.append("    }\n  ).id('shovel_js:").append(r.recipeId).append("');\n");
        return sb.toString();
    }

    private static String ecFluxShapeless(RecipeData r, String output) {
        StringBuilder sb = new StringBuilder();
        sb.append("  // ").append(r.recipeId).append("\n");
        sb.append("  event.recipes.extendedcrafting.flux_crafter(\n");
        sb.append("    ").append(output).append(",\n");
        sb.append("    ").append(r.ecPowerRequired).append(",\n    [");
        List<String> ings = new ArrayList<>();
        for (ItemStack s : r.shapelessIngredients) { String js = itemJs(s); if (js != null) ings.add(js); }
        sb.append(String.join(", ", ings));
        sb.append("]\n  ).id('shovel_js:").append(r.recipeId).append("');\n");
        return sb.toString();
    }

    private static String ecCombination(RecipeData r, String output) {
        String centerId = itemId(r.combinationCenter);
        if (centerId == null) return "  // SKIPPED (no center): " + r.recipeId + "\n";
        String center = itemJs(r.combinationCenter);
        StringBuilder sb = new StringBuilder();
        sb.append("  // ").append(r.recipeId).append(" [Combination rfCost=").append(r.combinationRfCost).append("]\n");
        sb.append("  event.recipes.extendedcrafting.combination(\n");
        sb.append("    ").append(output).append(",\n");
        sb.append("    ").append(r.combinationRfCost).append(",\n");
        sb.append("    ").append(r.combinationRfRate).append(",\n");
        sb.append("    ").append(center).append(",\n    [");
        List<String> peds = new ArrayList<>();
        for (ItemStack s : r.combinationPedestals) { String js = itemJs(s); if (js != null) peds.add(js); }
        sb.append(String.join(", ", peds));
        sb.append("]\n  ).id('shovel_js:").append(r.recipeId).append("');\n");
        return sb.toString();
    }

    private static String[] buildPatternRows(RecipeData r,
            Map<String, Character> keyMap, char[] next) {
        String[] rows = new String[r.gridHeight];
        for (int row = 0; row < r.gridHeight; row++) {
            StringBuilder pat = new StringBuilder();
            for (int col = 0; col < r.gridWidth; col++) {
                int idx = row * r.gridWidth + col;
                ItemStack s = idx < r.ingredients.size() ? r.ingredients.get(idx) : ItemStack.EMPTY;
                if (s.isEmpty()) {
                    pat.append(' ');
                } else {
                    String js = itemJs(s);
                    if (js == null) { pat.append(' '); continue; }
                    if (!keyMap.containsKey(js)) {
                        keyMap.put(js, next[0]++);

                        if (next[0] > 'Z') next[0] = 'a';
                    }
                    pat.append(keyMap.get(js));
                }
            }
            rows[row] = pat.toString();
        }
        return rows;
    }

    private static int ecTierNum(RecipeData.ECTableTier tier) {
        return switch (tier) {
            case BASIC -> 1; case ADVANCED -> 2; case ELITE -> 3; case ULTIMATE -> 4;
        };
    }

    /**
     * Raw-JSON-style fragment for customJson()'s ingredient/ingredients
     * fields: { item: 'id'[, count: N][, nbt: '...'] }. Kept separate from
     * itemJs() (which produces Item.of(...) expressions) because addJson
     * expects plain JSON objects, not KubeJS builder expressions.
     */
    private static String itemToJsonFragment(ItemStack s) {
        String id = itemId(s);
        if (id == null) return "{}";
        String tag = com.kve.common.recipe.TagIngredient.tagOf(s);
        if (tag != null) return "{ tag: " + JsStrings.quote(tag) + " }";
        StringBuilder sb = new StringBuilder("{ item: '").append(id).append("'");
        if (s.getCount() > 1) sb.append(", count: ").append(s.getCount());
        if (hasMeaningfulTag(s)) {
            String nbt = JsStrings.escape(Objects.requireNonNull(s.getTag()).toString());
            sb.append(", nbt: '").append(nbt).append("'");
        }
        sb.append(" }");
        return sb.toString();
    }

    public static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key != null ? key.toString() : null;
    }

    static boolean hasMeaningfulTag(ItemStack s) {
        if (!s.hasTag()) return false;
        var tag = Objects.requireNonNull(s.getTag());
        if (com.kve.common.recipe.TagIngredient.onlyMarker(tag)) return false;
        if (tag.size() == 1 && tag.contains("Damage") && tag.getInt("Damage") == 0) return false;
        return true;
    }

    /**
     * Builds the KubeJS expression for a stack, preserving count and NBT
     * (enchantments, energy/fluid tags from mods like Mekanism or Applied
     * Energistics, etc.) instead of emitting a bare item id string that
     * silently discards them. Falls back to a plain quoted id when there's
     * no extra data to preserve, so simple recipes stay simple to read.
     */
    public static String itemJs(ItemStack stack) {
        String id = itemId(stack);
        if (id == null) return null;
        String tag = com.kve.common.recipe.TagIngredient.tagOf(stack);
        if (tag != null) return JsStrings.quote("#" + tag);
        boolean hasNbt = hasMeaningfulTag(stack);
        boolean hasCount = stack.getCount() > 1;
        if (!hasNbt && !hasCount) return "'" + id + "'";

        StringBuilder js = new StringBuilder("Item.of('").append(id).append("'");
        if (hasCount) js.append(", ").append(stack.getCount());
        js.append(")");
        if (hasNbt) {
            String nbt = JsStrings.escape(Objects.requireNonNull(stack.getTag()).toString());
            js.append(".withNBT('").append(nbt).append("')");
        }
        return js.toString();
    }
}
