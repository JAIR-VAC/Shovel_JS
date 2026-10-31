package com.kve.common.trade;

import com.kve.common.kubejs.TradeExporter.TradeEditMode;
import com.kve.common.kubejs.TradeExporter.TradeEntry;
import com.kve.common.kubejs.TradeExporter.TradeTargetKind;
import net.minecraft.network.chat.Component;

import java.util.List;

public interface TradeBackend {

    String id();

    Component displayName();

    boolean isAvailable();

    boolean requiresRestart();

    boolean supports(TradeTargetKind kind);

    default boolean supportsEntry(TradeEntry entry) {
        return entry != null && supports(entry.targetKind);
    }

    boolean supportsTagOutput();

    boolean supportsMode(TradeEditMode mode);

    List<String> emit(TradeEntry entry, TradeEmitContext ctx);

    default List<String> previewLines(TradeEntry entry, TradeEmitContext ctx) {
        return emit(entry, ctx);
    }

    String wrapFile(String body);

    default String documentationHint() { return ""; }

    default void applyRuntime(TradeEntry entry) { }

    default void reconcileRuntime(java.util.Set<String> currentlyCommittedEntryIds) { }
}
