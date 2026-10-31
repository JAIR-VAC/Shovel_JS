package com.kve.common.trade;

import com.kve.common.kubejs.TradeExporter.TradeEditMode;
import com.kve.common.kubejs.TradeExporter.TradeEntry;
import com.kve.common.kubejs.TradeExporter.TradeTargetKind;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** D7: auto-resolve picks native; an explicit registered backendId wins. */
class TradeBackendRegistryResolveOrderTest {

    @BeforeEach
    void setUp() {
        TradeBackendRegistry.resetForTests();
        TradeBackendRegistry.registerDefaults();
    }

    @AfterEach
    void tearDown() {
        TradeBackendRegistry.resetForTests();
    }

    private TradeEntry villager() {
        TradeEntry e = new TradeEntry();
        e.id = "shovel_js:test";
        e.enabled = true;
        e.targetKind = TradeTargetKind.VILLAGER;
        e.professionId = "minecraft:armorer";
        e.level = 1;
        e.mode = TradeEditMode.REPLACE_LEVEL;
        e.backendId = "";
        return e;
    }

    @Test
    void autoResolvePrefersNativeWhenEnabled() {
        TradeEntry e = villager();
        var resolved = TradeBackendRegistry.resolve(e);
        assertTrue(resolved.isPresent());
        assertEquals(KveNativeTradeBackend.ID, resolved.get().id());
    }

    @Test
    void explicitNativeBackendIdIsHonored() {
        TradeEntry e = villager();
        e.backendId = KveNativeTradeBackend.ID;
        var resolved = TradeBackendRegistry.resolve(e);
        assertTrue(resolved.isPresent());
        assertEquals(KveNativeTradeBackend.ID, resolved.get().id());
    }

    @Test
    void savedUnknownBackendIdFallsBackToNative() {
        TradeEntry e = villager();
        e.backendId = "old_backend";
        var resolved = TradeBackendRegistry.resolve(e);
        assertTrue(resolved.isPresent());
        assertEquals(KveNativeTradeBackend.ID, resolved.get().id());
    }

    @Test
    void defaultsAreOnlyNativeAndStub() {
        var ids = TradeBackendRegistry.all().stream().map(TradeBackend::id).toList();
        assertEquals(java.util.List.of(KveNativeTradeBackend.ID, KveCommentStubTradeBackend.ID), ids);
    }

    @Test
    void undeclaredEntityNeverAutoResolves() {
        TradeTypeDescriptorRegistry.resetForTests();
        try {
            TradeEntry e = new TradeEntry();
            e.id = "shovel_js:entity_test";
            e.enabled = true;
            e.targetKind = TradeTargetKind.ENTITY;
            e.entityTypeId = "somemod:undeclared_merchant";
            e.mode = TradeEditMode.REPLACE_LEVEL;
            assertTrue(TradeBackendRegistry.resolve(e).isEmpty());
        } finally {
            TradeTypeDescriptorRegistry.resetForTests();
        }
    }

    @Test
    void abstractVillagerCompatibleEntityAutoResolvesToNative() {
        TradeTypeDescriptorRegistry.resetForTests();
        try {
            TradeTypeDescriptor d = new TradeTypeDescriptor();
            d.entityTypeId = "somemod:compatible_merchant";
            d.abstractVillagerCompatible = true;
            TradeTypeDescriptorRegistry.registerForTests(d);

            TradeEntry e = new TradeEntry();
            e.id = "shovel_js:entity_test2";
            e.enabled = true;
            e.targetKind = TradeTargetKind.ENTITY;
            e.entityTypeId = "somemod:compatible_merchant";
            e.professionId = "minecraft:armorer";
            e.level = 1;
            e.mode = TradeEditMode.REPLACE_LEVEL;

            var resolved = TradeBackendRegistry.resolve(e);
            assertTrue(resolved.isPresent());
            assertEquals(KveNativeTradeBackend.ID, resolved.get().id());
        } finally {
            TradeTypeDescriptorRegistry.resetForTests();
        }
    }
}
