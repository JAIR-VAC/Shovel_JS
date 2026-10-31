package com.kve.client.gui.pack;

import com.kve.CraftEditorMod;
import com.kve.client.gui.history.KveChangeLog;
import com.kve.client.gui.workspace.pending.PendingChange;
import com.kve.client.gui.workspace.pending.PendingChangeManager;
import com.kve.client.gui.workspace.pending.PendingDomain;
import com.kve.common.packrules.PackRules;
import com.kve.common.packrules.PackRulesExporter;

import java.util.List;

public final class PackRulesQueue {
    private PackRulesQueue() {}

    static final String ENTRY_ID = "pack_rules";
    private static PackRules pendingDraft;

    static boolean hasPending() {
        return PendingChangeManager.hasPending(PendingDomain.CONTENT, ENTRY_ID);
    }

    static PackRules effective() {
        return pendingDraft != null && hasPending() ? pendingDraft.copy() : PackRules.current().copy();
    }

    static List<String> queue(PackRules draft) {
        List<String> problems = draft.validate();
        if (!problems.isEmpty()) return problems;
        PackRules before = PackRules.current().copy();
        PackRules target = draft.copy();
        pendingDraft = target.copy();
        boolean restart = !before.toJson().get("info").equals(target.toJson().get("info"));
        boolean renamesChanged = !before.toJson().get("renames").equals(target.toJson().get("renames"));
        PendingChangeManager.queue(PendingChange.of(PendingDomain.CONTENT, ENTRY_ID, PendingChange.Action.EDIT, restart,
                () -> {
                    List<String> warnings = PackRulesExporter.commit(target);
                    pendingDraft = null;
                    for (String w : warnings) CraftEditorMod.LOGGER.warn("[Shovel JS] Reglas del pack: {}", w);
                    KveChangeLog.record(KveChangeLog.Log.PACK_RULES, PendingChange.Action.EDIT, "reglas del pack");
                    if (renamesChanged) reloadClientResources();
                },
                () -> pendingDraft = null)
                .withDiff(() -> scripts(before), () -> scripts(target)));
        return List.of();
    }

    private static void reloadClientResources() {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            mc.execute(mc::reloadResourcePacks);
        } catch (Throwable t) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] Reglas del pack: no se pudieron recargar los recursos (usa F3+T): {}", t.toString());
        }
    }

    private static String scripts(PackRules r) {
        return PackRulesExporter.buildServerScript(r) + "\n" + PackRulesExporter.buildClientInfoScript(r);
    }
}
