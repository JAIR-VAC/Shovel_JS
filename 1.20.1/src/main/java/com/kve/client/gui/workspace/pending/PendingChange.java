package com.kve.client.gui.workspace.pending;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public record PendingChange(
        PendingDomain domain,
        String entryId,
        Action action,
        boolean requiresRestart,
        long queuedAt,
        Runnable onApply,
        Runnable onDiscard,
        Supplier<String> oldScript,
        Supplier<String> newScript,
        String file,
        AtomicReference<String> textOverride,
        ApplyMode mode
) {

    public enum Action { CREATE, EDIT, DELETE }

    public static PendingChange of(PendingDomain domain, String entryId, Action action,
                                    boolean requiresRestart, Runnable onApply, Runnable onDiscard) {
        return new PendingChange(domain, entryId, action, requiresRestart,
                System.currentTimeMillis(), onApply, onDiscard, null, null, null, null, ApplyMode.fromRestartFlag(requiresRestart));
    }

    public PendingChange withDiff(Supplier<String> oldScript, Supplier<String> newScript) {
        return new PendingChange(domain, entryId, action, requiresRestart, queuedAt, onApply, onDiscard,
                oldScript, newScript, file, new AtomicReference<>(), mode);
    }

    public PendingChange withFile(String file) {
        return new PendingChange(domain, entryId, action, requiresRestart, queuedAt, onApply, onDiscard,
                oldScript, newScript, file, textOverride, mode);
    }

    /**
     * Copia con otro modo de aplicacion (ver {@link ApplyMode}): para los cambios
     * que el mod aplica en vivo ({@link ApplyMode#LIVE}) o que en realidad
     * necesitan volver a entrar al mundo aunque se hayan encolado como /reload.
     */
    public PendingChange withMode(ApplyMode newMode) {
        return new PendingChange(domain, entryId, action, newMode == ApplyMode.RESTART, queuedAt, onApply, onDiscard,
                oldScript, newScript, file, textOverride, newMode);
    }

    public boolean hasDiff() {
        return oldScript != null || newScript != null;
    }

    public String effectiveNewScript() {
        if (textOverride != null) {
            String o = textOverride.get();
            if (o != null) return o;
        }
        return newScript == null ? null : newScript.get();
    }

    public boolean hasOverride() {
        return textOverride != null && textOverride.get() != null;
    }
}

