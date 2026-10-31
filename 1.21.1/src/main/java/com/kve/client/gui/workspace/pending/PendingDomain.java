package com.kve.client.gui.workspace.pending;

public enum PendingDomain {

    RECIPES("gui.kve.main.recipes"),
    TAGS("gui.kve.main.tags"),
    LOOT("gui.kve.main.loot"),
    CONTENT("gui.kve.main.items"),
    WORLD("gui.kve.main.structures"),
    EVENTS("gui.kve.main.events"),
    WORLDGEN("gui.kve.main.worldgen"),
    TRADES("gui.kve.main.trades"),
    RAW_SCRIPT("gui.kve.main.code");

    public final String labelKey;

    PendingDomain(String labelKey) {
        this.labelKey = labelKey;
    }
}
