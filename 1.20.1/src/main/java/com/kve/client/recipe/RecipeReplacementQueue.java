package com.kve.client.recipe;

import com.kve.client.gui.history.KveChangeLog;
import com.kve.client.gui.workspace.pending.PendingChange;
import com.kve.client.gui.workspace.pending.PendingChangeManager;
import com.kve.client.gui.workspace.pending.PendingDomain;
import com.kve.common.kubejs.RecipeReplacementExporter;
import com.kve.common.kubejs.RecipeReplacementExporter.Rule;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * Encola el nuevo juego de reglas de reemplazo/unificacion (ver
 * {@link RecipeReplacementExporter}) como UN cambio pendiente del dominio
 * Recetas, con diff del script - mismo Queue/Apply/Discard que el resto de
 * KVE. Cada cambio lleva la lista COMPLETA de reglas; encolar otro reemplaza
 * al anterior todavia sin aplicar (si no, aplicar/descartar en distinto orden
 * dejaria un estado intermedio raro).
 */
@OnlyIn(Dist.CLIENT)
public final class RecipeReplacementQueue {

    private RecipeReplacementQueue() {}

    public static final String ENTRY_ID = "replacements";

    /** Reglas tal como quedarian si se aplicara lo que ya esta en cola (o las guardadas, si no hay nada en cola). */
    public static List<Rule> effectiveRules() {
        return pendingRules != null ? List.copyOf(pendingRules) : RecipeReplacementExporter.rules();
    }

    private static List<Rule> pendingRules = null;

    public static boolean hasPending() {
        return PendingChangeManager.hasPending(PendingDomain.RECIPES, ENTRY_ID);
    }

    public static void queue(List<Rule> newRules, String summary) {
        for (PendingChange c : PendingChangeManager.entriesForDomain(PendingDomain.RECIPES)) {
            if (ENTRY_ID.equals(c.entryId())) PendingChangeManager.discard(c);
        }
        List<Rule> snapshot = List.copyOf(new ArrayList<>(newRules));
        pendingRules = snapshot;
        List<Rule> before = RecipeReplacementExporter.rules();
        PendingChange change = PendingChange.of(PendingDomain.RECIPES, ENTRY_ID, PendingChange.Action.EDIT, false,
                        () -> {
                            RecipeReplacementExporter.commit(snapshot);
                            if (pendingRules == snapshot) pendingRules = null;
                            KveChangeLog.record(KveChangeLog.Log.RECIPES, PendingChange.Action.EDIT, "replacements: " + summary);
                        },
                        () -> { if (pendingRules == snapshot) pendingRules = null; })
                .withDiff(() -> RecipeReplacementExporter.buildServerScript(before),
                        () -> RecipeReplacementExporter.buildServerScript(snapshot))
                .withFile(RecipeReplacementExporter.serverScript().toString().replace('\\', '/'));
        PendingChangeManager.queue(change);
    }
}
