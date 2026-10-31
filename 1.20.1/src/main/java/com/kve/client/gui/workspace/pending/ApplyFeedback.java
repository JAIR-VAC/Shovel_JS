package com.kve.client.gui.workspace.pending;

import com.kve.common.Lang;

import java.util.ArrayList;
import java.util.List;

public final class ApplyFeedback {

    private ApplyFeedback() {}

    public enum Level { OK, WARN }

    /**
     * @param message     texto para la barra/flash y el toast (el mismo en los dos)
     * @param reloadSent  ya se mando /reload (ajuste "Auto reload")
     * @param offerReload falta /reload y no se mando: mostrar el boton "Ejecutar /reload"
     */
    public record Feedback(String message, Level level, boolean reloadSent, boolean offerReload) {}

    /** Mensaje + auto reload (si corresponde). Llamar una vez por Aplicar, en el hilo del cliente. */
    public static Feedback handle(PendingChangeManager.ApplyResult r) {
        boolean reloadSent = false;
        if (r.allSucceeded() && r.needsReload() && autoReloadEnabled()) reloadSent = sendReload();
        String msg = message(r, reloadSent);
        Level level = !r.allSucceeded() || r.restartCount() > 0 ? Level.WARN : Level.OK;
        boolean offer = r.allSucceeded() && r.needsReload() && !reloadSent;
        return new Feedback(msg, level, reloadSent, offer);
    }

    /** Solo el texto (sin mandar /reload) - para pantallas que no aplican nada propio. */
    public static String message(PendingChangeManager.ApplyResult r) {
        return handle(r).message();
    }

    static String message(PendingChangeManager.ApplyResult r, boolean reloadSent) {
        if (!r.allSucceeded()) {
            return Lang.get("gui.kve.pending.applied_partial", r.appliedCount(), r.failed().size());
        }
        int live = r.liveCount(), reload = r.reloadCount(), restart = r.restartCount();
        if (r.appliedCount() == 0) return Lang.get("gui.kve.pending.applied_none");
        if (reload == 0 && restart == 0) return Lang.get("gui.kve.pending.applied_live", live);
        if (live == 0 && restart == 0) {
            return Lang.get(reloadSent ? "gui.kve.pending.applied_reloaded" : "gui.kve.pending.applied_reload", reload);
        }
        if (live == 0 && reload == 0) return Lang.get("gui.kve.pending.applied_restart", restart, restartDomains(r));
        List<String> parts = new ArrayList<>();
        if (live > 0) parts.add(Lang.get("gui.kve.pending.part_live", live));
        if (reload > 0) parts.add(Lang.get(reloadSent ? "gui.kve.pending.part_reloaded" : "gui.kve.pending.part_reload", reload));
        if (restart > 0) parts.add(Lang.get("gui.kve.pending.part_restart", restart, restartDomains(r)));
        return Lang.get("gui.kve.pending.applied_mixed", r.appliedCount(), String.join(", ", parts));
    }

    private static String restartDomains(PendingChangeManager.ApplyResult r) {
        List<String> names = new ArrayList<>();
        for (PendingDomain d : r.restartDomains()) names.add(Lang.get(d.labelKey));
        return String.join(", ", names);
    }

    /** Hint al Guardar (encolar), coherente con lo que dira el Aplicar. */
    public static String queuedHint(ApplyMode mode) {
        return Lang.get(switch (mode) {
            case LIVE -> "gui.kve.pending.queued_hint_live";
            case RELOAD -> "gui.kve.pending.queued_hint_reload";
            case RESTART -> "gui.kve.pending.queued_hint_restart";
        });
    }

    private static boolean autoReloadEnabled() {
        return com.kve.client.gui.KveClientSettings.getOrDefault(com.kve.client.gui.KveClientSettings.AUTO_RELOAD, false);
    }

    /** /reload como si el jugador lo tipeara; false si no hay jugador (menu principal). */
    public static boolean sendReload() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return false;
        try {
            mc.player.connection.sendCommand("reload");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
