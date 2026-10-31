package com.kve.common.trade;

import com.kve.common.kubejs.TradeExporter.TradeEditMode;
import com.kve.common.kubejs.TradeExporter.TradeEntry;
import com.kve.common.kubejs.TradeExporter.TradeTargetKind;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class KveCommentStubTradeBackend implements TradeBackend {

    public static final String ID = "shovel_js_comment_stub";

    @Override public String id() { return ID; }

    @Override public Component displayName() { return Component.translatable("gui.kve.trades.backend.none"); }

    @Override public boolean isAvailable() { return false; }

    @Override public boolean requiresRestart() { return false; }

    @Override public boolean supports(TradeTargetKind kind) { return true; }

    @Override public boolean supportsTagOutput() { return false; }

    @Override public boolean supportsMode(TradeEditMode mode) { return true; }

    @Override
    public List<String> emit(TradeEntry entry, TradeEmitContext ctx) {
        throw new IllegalStateException("[Shovel JS] " + ID + " never actually emits (D1: fail-closed, not a fake writer) - "
                + "TradeBackendRegistry.resolve() should have returned empty for entry '" + (entry != null ? entry.id : "?") + "' instead of this backend.");
    }

    @Override
    public String wrapFile(String body) {
        throw new IllegalStateException("[Shovel JS] " + ID + " never actually emits a file (see emit()).");
    }
}
