package com.kve.client.gui;

import com.kve.client.gui.history.KveChangeLog;
import com.kve.client.gui.workspace.pending.PendingChange;
import com.kve.client.gui.workspace.pending.PendingChangeManager;
import com.kve.client.gui.workspace.pending.PendingDomain;
import com.kve.common.itemrules.BulkItemEdit;
import com.kve.common.kubejs.KubeJSItemAttrExporter;
import com.kve.common.kubejs.KubeJSItemAttrExporter.ItemEntry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Encola una {@link BulkItemEdit} sobre muchos items como UN solo cambio
 * pendiente (no 200 filas en la cola), con su deshacer completo: Descartar
 * devuelve cada item a como estaba. Lo usan la edicion en masa y
 * "Ocultar en JEI" de la lista de items sin receta.
 */
public final class BulkItemQueue {
    private BulkItemQueue() {}

    private static final AtomicInteger BATCH = new AtomicInteger();

    /** @return los problemas de validacion (nada encolado) o lista vacia si se encolo. */
    public static List<String> queue(List<String> itemIds, BulkItemEdit edit, Runnable afterDiscard) {
        List<String> problems = edit.validate();
        if (itemIds.isEmpty()) problems = List.of("No hay items seleccionados");
        if (!problems.isEmpty()) return problems;

        Map<String, ItemEntry> previous = new LinkedHashMap<>();
        for (String id : itemIds) {
            ItemEntry before = KubeJSItemAttrExporter.getEntry(id);
            previous.put(id, before == null ? null : before.copy());
            KubeJSItemAttrExporter.stageItem(edit.applyTo(id, before));
        }
        String entryId = "masa#" + BATCH.incrementAndGet() + ": " + edit.describe() + " (" + itemIds.size() + ")";
        PendingChangeManager.queue(PendingChange.of(PendingDomain.CONTENT, entryId, PendingChange.Action.EDIT, true,
                () -> {
                    KubeJSItemAttrExporter.commitItems();
                    for (String id : itemIds) KveChangeLog.record(KveChangeLog.Log.ITEMS, PendingChange.Action.EDIT, id);
                },
                () -> {
                    previous.forEach(KubeJSItemAttrExporter::discardStagedItem);
                    afterDiscard.run();
                })
                .withDiff(() -> previews(previous, true), () -> previews(previous, false)));
        return List.of();
    }

    private static String previews(Map<String, ItemEntry> previous, boolean before) {
        StringBuilder sb = new StringBuilder();
        previous.forEach((id, old) -> {
            ItemEntry e = before ? old : KubeJSItemAttrExporter.getEntry(id);
            if (e != null) sb.append(KubeJSItemAttrExporter.previewItemJS(e)).append('\n');
        });
        return sb.toString();
    }
}
