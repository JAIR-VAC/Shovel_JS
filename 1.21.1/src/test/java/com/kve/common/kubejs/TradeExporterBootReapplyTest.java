package com.kve.common.kubejs;

import com.kve.common.kubejs.TradeExporter.TradeEditMode;
import com.kve.common.kubejs.TradeExporter.TradeEntry;
import com.kve.common.kubejs.TradeExporter.TradeOffer;
import com.kve.common.kubejs.TradeExporter.TradeTargetKind;
import com.kve.common.trade.TradeBackend;
import com.kve.common.trade.TradeBackendRegistry;
import com.kve.common.trade.TradeEmitContext;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TradeExporterBootReapplyTest {

    static final class RecordingBackend implements TradeBackend {
        final List<String> applied = new ArrayList<>();
        Set<String> lastReconcile = null;
        boolean throwOnApply = false;

        @Override public String id() { return "test_recording"; }
        @Override public Component displayName() { return Component.literal("rec"); }
        @Override public boolean isAvailable() { return true; }
        @Override public boolean requiresRestart() { return false; }
        @Override public boolean supports(TradeTargetKind kind) { return true; }
        @Override public boolean supportsTagOutput() { return false; }
        @Override public boolean supportsMode(TradeEditMode mode) { return true; }
        @Override public List<String> emit(TradeEntry entry, TradeEmitContext ctx) { return List.of(); }
        @Override public String wrapFile(String body) { return body; }
        @Override public void applyRuntime(TradeEntry entry) {
            if (throwOnApply) throw new IllegalStateException("boom");
            applied.add(entry.id);
        }
        @Override public void reconcileRuntime(Set<String> ids) { lastReconcile = ids; }
    }

    private RecordingBackend backend;

    @BeforeEach
    void setUp() {
        TradeBackendRegistry.resetForTests();
        backend = new RecordingBackend();
        TradeBackendRegistry.register(backend);
    }

    @AfterEach
    void tearDown() { TradeBackendRegistry.resetForTests(); }

    private TradeEntry entry(String id, boolean enabled) {
        TradeEntry e = new TradeEntry();
        e.id = id;
        e.enabled = enabled;
        e.targetKind = TradeTargetKind.VILLAGER;
        e.professionId = "minecraft:armorer";
        e.level = 1;
        e.mode = TradeEditMode.REPLACE_LEVEL;
        TradeOffer o = new TradeOffer();
        o.inputAId = "minecraft:iron_ingot";
        o.inputACount = 4;
        o.outputId = "minecraft:iron_chestplate";
        e.offers.add(o);
        return e;
    }

    @Test
    void appliesOnlyEnabledValidEntriesAndReportsCount() {
        TradeEntry bad = entry("shovel_js:bad", true);
        bad.offers.get(0).maxUses = 0;

        int n = TradeExporter.reapplyRuntime(List.of(entry("shovel_js:ok", true), entry("shovel_js:off", false), bad));

        assertEquals(1, n);
        assertEquals(List.of("shovel_js:ok"), backend.applied);
    }

    @Test
    void reconcileGetsExactlyTheAppliedIds() {
        TradeExporter.reapplyRuntime(List.of(entry("shovel_js:a", true), entry("shovel_js:b", true)));
        assertEquals(Set.of("shovel_js:a", "shovel_js:b"), backend.lastReconcile);
    }

    @Test
    void oneFailingEntryDoesNotAbortTheRest() {
        backend.throwOnApply = true;
        assertEquals(0, TradeExporter.reapplyRuntime(List.of(entry("shovel_js:a", true))));
        backend.throwOnApply = false;
        assertEquals(1, TradeExporter.reapplyRuntime(List.of(entry("shovel_js:a", true))));
    }

    @Test
    void emptyInputIsANoOp() {
        assertEquals(0, TradeExporter.reapplyRuntime(List.of()));
        assertTrue(backend.applied.isEmpty());
        assertEquals(Set.of(), backend.lastReconcile);
    }
}
