package com.kve.client.recipe;

import com.kve.client.gui.history.KveChangeLog;
import com.kve.client.gui.workspace.pending.PendingChange;
import com.kve.client.gui.workspace.pending.PendingChangeManager;
import com.kve.client.gui.workspace.pending.PendingDomain;
import com.kve.common.kubejs.KubeJSExporter;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Encola quitar recetas existentes (vanilla o de mods) por la cola de
 * cambios pendientes - mismo contrato que {@code RecipeBrowserScreen} ya
 * usaba, compartido ahora con el detector de conflictos y la eliminacion
 * masiva.
 */
@OnlyIn(Dist.CLIENT)
public final class RecipeRemovalQueue {

    private RecipeRemovalQueue() {}

    /**
     * Ids marcados dentro de un lote todavia sin Aplicar. El cambio pendiente
     * del lote se llama "bulk:<label>", asi que hasPending(RECIPES, id) no los
     * ve; sin esto la pestana Quitar los mostraba como ya aplicados.
     */
    private static final Map<String, String> PENDING_BULK = new HashMap<>();

    /** Si {@code id} esta quitado solo en la cola (sin Aplicar todavia). */
    public static boolean isPending(String id) {
        return PENDING_BULK.containsKey(id) || PendingChangeManager.hasPending(PendingDomain.RECIPES, id);
    }

    /** entryId del cambio pendiente que quita {@code id}: el suyo propio o el de su lote. */
    public static String pendingEntryOf(String id) {
        return PENDING_BULK.getOrDefault(id, id);
    }

    /** Saca un id suelto de su lote (el resto del lote sigue en la cola). */
    public static void forgetFromBulk(String id) {
        PENDING_BULK.remove(id);
    }

    /** Una receta: un cambio pendiente. */
    public static void queueOne(String id) {
        KubeJSExporter.stageRemoveVanilla(id);
        PendingChangeManager.queue(PendingChange.of(PendingDomain.RECIPES, id, PendingChange.Action.DELETE, false,
                () -> {
                    KubeJSExporter.commitRemoved();
                    KveChangeLog.record(KveChangeLog.Log.RECIPES, PendingChange.Action.DELETE, id);
                },
                () -> KubeJSExporter.discardStagedRemoveVanilla(id)));
    }

    /**
     * Muchas recetas como UN solo cambio pendiente (eliminacion masiva):
     * cientos de entradas sueltas en la cola serian imposibles de revisar, y
     * Descartar debe deshacer el lote entero de una vez.
     */
    public static void queueBulk(String label, List<String> ids) {
        List<String> copy = ids.stream().distinct()
                .filter(id -> !KubeJSExporter.getRemovedRecipes().contains(id)).toList();
        if (copy.isEmpty()) return;
        for (String id : copy) KubeJSExporter.stageRemoveVanilla(id);
        for (String id : copy) PENDING_BULK.put(id, "bulk:" + label);
        PendingChangeManager.queue(PendingChange.of(PendingDomain.RECIPES, "bulk:" + label, PendingChange.Action.DELETE, false,
                () -> {
                    copy.forEach(PENDING_BULK::remove);
                    KubeJSExporter.commitRemoved();
                    KveChangeLog.record(KveChangeLog.Log.RECIPES, PendingChange.Action.DELETE, "bulk:" + label + " (" + copy.size() + ")");
                },
                () -> {
                    copy.forEach(PENDING_BULK::remove);
                    for (String id : copy) KubeJSExporter.discardStagedRemoveVanilla(id);
                }));
    }
}
