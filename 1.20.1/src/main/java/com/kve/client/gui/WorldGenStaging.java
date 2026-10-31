package com.kve.client.gui;

import com.kve.client.gui.workspace.pending.PendingChange;
import com.kve.client.gui.workspace.pending.PendingChangeManager;
import com.kve.client.gui.workspace.pending.PendingDomain;
import com.kve.common.worldgen.WorldGenEntry;
import com.kve.common.worldgen.WorldGenExporter;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * Encolar reglas de world gen NUEVAS que no salen del editor
 * (importadas de la biblioteca). Mismo PendingChange que Guardar en
 * {@link WorldGenEditorScreen}: requiere volver a entrar al mundo, y Descartar
 * las saca de nuevo.
 */
@OnlyIn(Dist.CLIENT)
public final class WorldGenStaging {

    private WorldGenStaging() {}

    public static void stageNew(List<WorldGenEntry> rules) {
        for (WorldGenEntry e : rules) {
            WorldGenEntry previous = WorldGenExporter.getEntry(e.key());
            WorldGenExporter.stageEntry(e);
            String key = e.key();
            PendingChangeManager.queue(PendingChange.of(PendingDomain.WORLDGEN, key,
                            previous == null ? PendingChange.Action.CREATE : PendingChange.Action.EDIT, true,
                            () -> WorldGenExporter.commitWorldGen(),
                            () -> WorldGenExporter.discardStagedEntry(key, previous))
                    .withDiff(previous == null ? null : () -> WorldGenExporter.previewWorldGenJson(previous),
                            () -> WorldGenExporter.previewWorldGenJson(e)));
        }
    }
}
