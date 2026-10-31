package com.kve.client.gui.workspace.pending;

public enum ApplyMode {
    LIVE,
    RELOAD,
    RESTART;

    public static ApplyMode fromRestartFlag(boolean requiresRestart) {
        return requiresRestart ? RESTART : RELOAD;
    }

    public ApplyMode max(ApplyMode other) {
        return other != null && other.ordinal() > ordinal() ? other : this;
    }
}
