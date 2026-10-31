package com.kve.common.trade;

import com.kve.common.kubejs.TradeExporter.TradeEntry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * §5.2. Registered at
 * bootstrap.
 */
public final class TradeBackendRegistry {

    private TradeBackendRegistry() {}

    private static final Map<String, TradeBackend> BACKENDS = new LinkedHashMap<>();
    private static boolean defaultsRegistered = false;

    public static void register(TradeBackend b) {
        BACKENDS.put(b.id(), b);
    }

    public static List<TradeBackend> all() {
        return new ArrayList<>(BACKENDS.values());
    }

    public static Optional<TradeBackend> resolve(TradeEntry e) {
        if (e == null) return Optional.empty();
        if (e.backendId != null && !e.backendId.isBlank()) {
            TradeBackend explicit = BACKENDS.get(e.backendId);
            if (explicit != null) return Optional.of(explicit);
        }
        for (TradeBackend b : BACKENDS.values()) {
            if (KveCommentStubTradeBackend.ID.equals(b.id())) continue;
            if (!b.isAvailable()) continue;
            if (!b.supportsEntry(e)) continue;
            if (!b.supportsMode(e.mode)) continue;
            return Optional.of(b);
        }
        return Optional.empty();
    }

    /**
     * Registers the backend set: {@code kve_native} (applies trades in Java,
     * no addon needed), then {@code kve_comment_stub} (fallback, never
     * auto-resolved - see {@link #resolve}). Idempotent.
     */
    public static synchronized void registerDefaults() {
        if (defaultsRegistered) return;
        defaultsRegistered = true;
        register(new KveNativeTradeBackend());
        register(new KveCommentStubTradeBackend());
    }

    /** Test-only: clears everything registered, including whether defaults were registered, so a test can start from a clean registry. */
    public static void resetForTests() {
        BACKENDS.clear();
        defaultsRegistered = false;
    }
}
