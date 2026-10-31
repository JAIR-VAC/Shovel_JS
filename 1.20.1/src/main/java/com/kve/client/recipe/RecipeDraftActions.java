package com.kve.client.recipe;

import com.kve.client.gui.history.KveChangeLog;
import com.kve.client.gui.workspace.pending.PendingChange;
import com.kve.client.gui.workspace.pending.PendingChangeManager;
import com.kve.client.gui.workspace.pending.PendingDomain;
import com.kve.common.kubejs.KubeJSExporter;
import com.kve.common.recipe.RecipeData;
import com.kve.common.recipetype.RecipeTypeDescriptorRegistry;

import java.util.function.Predicate;

public final class RecipeDraftActions {
    private RecipeDraftActions() {}

    /**
     * Un tipo "noOutput" (aether:repairing, placement bans,
     * apotheosis:spawner_modifier/reforging...) no produce ningun item -
     * exigir salida hacia imposible guardarlo desde el editor.
     */
    public static boolean typeHasNoOutput(RecipeData.RecipeType type, String customTypeId) {
        return (type == RecipeData.RecipeType.CUSTOM_JSON || type == RecipeData.RecipeType.CUSTOM_MACHINE) && customTypeId != null
                && RecipeTypeDescriptorRegistry.find(customTypeId).map(desc -> desc.noOutput).orElse(false);
    }

    /**
     * Clave de lang del motivo por el que no se puede guardar el borrador,
     * o null si se puede. {@code requireType}: Guardar exige elegir el tipo
     * de "Otros mods"; Duplicar no.
     */
    public static String saveBlocker(boolean canEdit, boolean outputEmpty, RecipeData.RecipeType type,
                                     String customTypeId, boolean requireType) {
        if (!canEdit) return "gui.kve.perm.denied";
        if (outputEmpty && !typeHasNoOutput(type, customTypeId)) return "gui.kve.recipe.output_required";
        if (requireType && type == RecipeData.RecipeType.CUSTOM_JSON && (customTypeId == null || customTypeId.isEmpty())) {
            return "gui.kve.recipe.pick_type_first";
        }
        return null;
    }

    /**
     * Id para una copia: "{base}_copy", y si ya esta en uso (duplicar dos
     * veces sin guardar) "{base}_copy2", "_copy3"... Determinista porque el
     * jugador ve y puede editar el id antes de guardar.
     */
    public static String nextCopyId(String base, Predicate<String> inUse) {
        String b = base == null ? "" : base.trim();
        String candidate = (b.isEmpty() ? "recipe" : b) + "_copy";
        String finalId = candidate;
        for (int n = 2; inUse.test(finalId); n++) finalId = candidate + n;
        return finalId;
    }

    public static boolean recipeIdInUse(String id) {
        return KubeJSExporter.getAddedRecipes().stream().anyMatch(r -> r.recipeId.equals(id));
    }

    public static PendingChange.Action queueSave(RecipeData d, String removalId, Runnable afterDiscard) {
        RecipeData previous = findAdded(d.recipeId);
        PendingChange.Action action = previous == null ? PendingChange.Action.CREATE : PendingChange.Action.EDIT;
        KubeJSExporter.stageRecipe(d);
        if (removalId != null) KubeJSExporter.stageRemoveVanilla(removalId);

        PendingChangeManager.queue(PendingChange.of(PendingDomain.RECIPES, d.recipeId, action, false,
                () -> {
                    KubeJSExporter.commitAdded();
                    if (removalId != null) KubeJSExporter.commitRemoved();
                    KveChangeLog.record(KveChangeLog.Log.RECIPES, action, d.recipeId);
                },
                () -> {
                    KubeJSExporter.discardStagedRecipe(d.recipeId, previous);
                    if (removalId != null) KubeJSExporter.discardStagedRemoveVanilla(removalId);
                    afterDiscard.run();
                })
                .withDiff(
                        previous == null ? null : () -> KubeJSExporter.previewRecipeJS(previous),
                        () -> KubeJSExporter.previewRecipeJS(d)));
        return action;
    }

    /** Encola el borrado de una receta propia (las vanilla/de mod van por la pestana Quitar). */
    public static void queueDelete(String id, Runnable afterDiscard) {
        RecipeData previous = findAdded(id);
        KubeJSExporter.stageDeleteRecipe(id);
        PendingChangeManager.queue(PendingChange.of(PendingDomain.RECIPES, id, PendingChange.Action.DELETE, false,
                () -> {
                    KubeJSExporter.commitAdded();
                    KveChangeLog.record(KveChangeLog.Log.RECIPES, PendingChange.Action.DELETE, id);
                },
                () -> {
                    if (previous != null) KubeJSExporter.stageRecipe(previous);
                    afterDiscard.run();
                })
                .withDiff(previous == null ? null : () -> KubeJSExporter.previewRecipeJS(previous), null));
    }

    private static RecipeData findAdded(String id) {
        return KubeJSExporter.getAddedRecipes().stream().filter(r -> r.recipeId.equals(id)).findFirst().orElse(null);
    }
}
