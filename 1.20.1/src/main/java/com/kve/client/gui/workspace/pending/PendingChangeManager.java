package com.kve.client.gui.workspace.pending;

import com.kve.CraftEditorMod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

public final class PendingChangeManager {

    private PendingChangeManager() {}

    private static final List<PendingChange> queue = new ArrayList<>();
    private static final List<Listener> listeners = new ArrayList<>();

    /**
     * Permission gate used by {@link #applyMatching()}. In production it's always
     * {@code ClientPermissionState::canApply}; tests swap it via
     * {@link #setApplyPermissionForTests()} because the real check calls
     * {@code Minecraft.getInstance()}, which is null in headless JUnit.
     */
    private static BooleanSupplier applyPermission = com.kve.client.permission.ClientPermissionState::canApply;

    static void setApplyPermissionForTests(BooleanSupplier permission) {
        applyPermission = permission != null ? permission : com.kve.client.permission.ClientPermissionState::canApply;
    }

    public interface Listener { void onPendingQueueChanged(); }

    public static void addListener(Listener l) { listeners.add(l); }
    public static void removeListener(Listener l) { listeners.remove(l); }

    public static void queue(PendingChange change) {
        PendingChange old = null;
        for (PendingChange c : queue) {
            if (c.domain() == change.domain() && c.entryId().equals(change.entryId())) { old = c; break; }
        }
        queue.removeIf(c -> c.domain() == change.domain() && c.entryId().equals(change.entryId()));
        queue.add(old == null ? change : merge(old, change));
        notifyListeners();
    }

    static PendingChange merge(PendingChange old, PendingChange next) {
        Runnable newDiscard = next.onDiscard(), oldDiscard = old.onDiscard();
        Runnable discard = () -> {
            if (newDiscard != null) newDiscard.run();
            if (oldDiscard != null) oldDiscard.run();
        };
        PendingChange.Action action = old.action() == PendingChange.Action.CREATE && next.action() == PendingChange.Action.EDIT
                ? PendingChange.Action.CREATE : next.action();
        ApplyMode mode = old.mode().max(next.mode());
        return new PendingChange(next.domain(), next.entryId(), action,
                mode == ApplyMode.RESTART, next.queuedAt(),
                next.onApply(), discard,
                old.hasDiff() ? old.oldScript() : next.oldScript(), next.newScript(),
                next.file() != null ? next.file() : old.file(),
                next.textOverride(), mode);
    }

    public static List<PendingChange> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(queue));
    }

    public static int size() { return queue.size(); }

    public static boolean isEmpty() { return queue.isEmpty(); }

    public static boolean hasPending(PendingDomain domain, String entryId) {
        for (PendingChange c : queue) {
            if (c.domain() == domain && c.entryId().equals(entryId)) return true;
        }
        return false;
    }

    public static long countRequiringRestart() {
        return queue.stream().filter(PendingChange::requiresRestart).count();
    }

    public static int sizeForDomain(PendingDomain domain) {
        int n = 0;
        for (PendingChange c : queue) if (c.domain() == domain) n++;
        return n;
    }

    /**
     * Defensive-copy snapshot of every queued entry for one domain, in
     * queue order. a
     * screen needs the actual {@link PendingChange} objects (not just a
     * count) to read their {@link PendingChange#oldScript()}/{@link PendingChange#newScript()}
     * suppliers and build a diff before calling {@link #applyDomain()}.
     */
    public static List<PendingChange> entriesForDomain(PendingDomain domain) {
        List<PendingChange> out = new ArrayList<>();
        for (PendingChange c : queue) if (c.domain() == domain) out.add(c);
        return out;
    }

    /** True if at least one queued entry in this domain has diff suppliers attached (see {@link PendingChange#hasDiff()}) - screens use this to decide whether Apply should offer "Ver diff" before writing. */
    public static boolean domainHasDiff(PendingDomain domain) {
        for (PendingChange c : queue) if (c.domain() == domain && c.hasDiff()) return true;
        return false;
    }

    /**
     * Every domain that currently has at least one queued entry. A screen's
     * Apply button can compare this against its own domain to tell whether
     * pressing Apply would also touch tickets queued by a different editor,
     * and warn before doing so instead of silently sweeping them in.
     */
    public static Set<PendingDomain> domainsPresent() {
        EnumSet<PendingDomain> present = EnumSet.noneOf(PendingDomain.class);
        for (PendingChange c : queue) present.add(c.domain());
        return present;
    }

    /** Discards a single queued entry (runs its onDiscard(), then removes it). */
    public static void discard(PendingChange change) {
        if (!queue.remove(change)) return;
        safeRun(change.onDiscard());
        notifyListeners();
    }

    /** Discards every queued entry, reverting each draft in turn. */
    public static void discardAll() {
        discardMatching(c -> true);
    }

    public static void clear() {
        if (queue.isEmpty()) return;
        queue.clear();
        notifyListeners();
    }

    /** Discards only the queued entries belonging to one domain, leaving every other domain's tickets untouched. */
    public static void discardDomain(PendingDomain domain) {
        discardMatching(c -> c.domain() == domain);
    }

    private static void discardMatching(Predicate<PendingChange> filter) {
        List<PendingChange> toDiscard = new ArrayList<>();
        for (PendingChange c : queue) if (filter.test(c)) toDiscard.add(c);
        queue.removeAll(toDiscard);
        for (PendingChange c : toDiscard) safeRun(c.onDiscard());
        notifyListeners();
    }

    /**
     * Applies every queued entry (writes to disk). If an individual
     * change's onApply() throws, that one entry is kept in the queue (so the
     * draft isn't silently lost) and everything else still goes through -
     * one bad recipe shouldn't block forty good ones.
     */
    public static ApplyResult applyAll() {
        return applyMatching(c -> true);
    }

    /**
     * Applies only the queued entries belonging to one domain.
     * Every other domain's tickets stay queued exactly as they were - this
     * is what lets a screen's Apply button act on just its own tab instead
     * of forcing {@link #applyAll()}'s "everything, right now" scope.
     * Same partial-failure behaviour as {@link #applyAll()}: an entry whose
     * onApply() throws stays in the queue instead of being silently dropped.
     */
    public static ApplyResult applyDomain(PendingDomain domain) {
        return applyMatching(c -> c.domain() == domain);
    }

    private static ApplyResult applyMatching(Predicate<PendingChange> filter) {
        List<PendingChange> toApply = new ArrayList<>();
        for (PendingChange c : queue) if (filter.test(c)) toApply.add(c);

        if (!applyPermission.getAsBoolean()) {
            if (!toApply.isEmpty()) {
                CraftEditorMod.LOGGER.warn("[Shovel JS] Apply rechazado: rol actual no es ADMIN ({} cambios pendientes sin aplicar)", toApply.size());
            }
            return new ApplyResult(0, new ArrayList<>(toApply), false, 0, 0, 0, EnumSet.noneOf(PendingDomain.class), List.of());
        }

        int applied = 0, live = 0, reload = 0, restart = 0;
        EnumSet<PendingDomain> restartDomains = EnumSet.noneOf(PendingDomain.class);
        List<PendingChange> failed = new ArrayList<>();
        List<PendingChange> done = new ArrayList<>();
        for (PendingChange c : toApply) {
            try {
                c.onApply().run();
                queue.remove(c);
                done.add(c);
                applied++;
                switch (c.mode()) {
                    case LIVE -> live++;
                    case RELOAD -> reload++;
                    case RESTART -> { restart++; restartDomains.add(c.domain()); }
                }
            } catch (Exception ex) {
                CraftEditorMod.LOGGER.warn("[Shovel JS] Fallo al aplicar cambio pendiente ({} / {}): {}",
                        c.domain(), c.entryId(), ex.toString());
                failed.add(c);
            }
        }
        notifyListeners();
        return new ApplyResult(applied, failed, restart > 0, live, reload, restart, restartDomains, done);
    }

    /**
     * Resultado de un Aplicar. ademas del viejo {@code requiresRestart}
     * cuenta cuantos cambios aplicados entran en efecto en vivo, con /reload o al
     * volver a entrar al mundo (ver {@link ApplyMode}), y que dominios piden
     * reinicio - {@link ApplyFeedback} arma el mensaje con eso.
     */
    public record ApplyResult(int appliedCount, List<PendingChange> failed, boolean requiresRestart,
                              int liveCount, int reloadCount, int restartCount, Set<PendingDomain> restartDomains,
                              List<PendingChange> applied) {
        public boolean allSucceeded() { return failed.isEmpty(); }
        /** True si algo aplicado necesita /reload para verse. */
        public boolean needsReload() { return reloadCount > 0; }
    }

    private static void safeRun(Runnable r) {
        try {
            r.run();
        } catch (Exception ignored) {
            // Discard callbacks are best-effort reverts of in-memory state;
            // a failure here must not block the rest of the discard batch.
        }
    }

    private static void notifyListeners() {
        for (Listener l : new ArrayList<>(listeners)) l.onPendingQueueChanged();
    }
}
