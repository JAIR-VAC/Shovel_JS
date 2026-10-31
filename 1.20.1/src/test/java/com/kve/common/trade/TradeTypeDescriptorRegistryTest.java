package com.kve.common.trade;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TradeTypeDescriptorRegistryTest {

    @AfterEach
    void tearDown() {
        TradeTypeDescriptorRegistry.resetForTests();
        TradeTypeDescriptorRegistry.setModLoadedCheckForTests(null);
    }

    @Test
    void unknownEntityIsCompatibleWithNothing() {
        TradeTypeDescriptorRegistry.resetForTests();
        assertFalse(TradeTypeDescriptorRegistry.isNativeCompatible("somemod:whatever"));
    }

    @Test
    void nativeCompatibleFlagIsRespected() {
        TradeTypeDescriptorRegistry.resetForTests();
        TradeTypeDescriptor d = new TradeTypeDescriptor();
        d.entityTypeId = "somemod:native_merchant";
        d.abstractVillagerCompatible = true;
        TradeTypeDescriptorRegistry.registerForTests(d);

        assertTrue(TradeTypeDescriptorRegistry.isNativeCompatible("somemod:native_merchant"));
    }

    @Test
    void requiresModGatesCompatibility() {
        TradeTypeDescriptorRegistry.resetForTests();
        TradeTypeDescriptor d = new TradeTypeDescriptor();
        d.entityTypeId = "somemod:gated_merchant";
        d.requiresMod = "somemod";
        d.abstractVillagerCompatible = true;
        TradeTypeDescriptorRegistry.registerForTests(d);

        TradeTypeDescriptorRegistry.setModLoadedCheckForTests(modid -> false);
        assertFalse(TradeTypeDescriptorRegistry.isNativeCompatible("somemod:gated_merchant"), "requiresMod not loaded -> not compatible");

        TradeTypeDescriptorRegistry.setModLoadedCheckForTests(modid -> "somemod".equals(modid));
        assertTrue(TradeTypeDescriptorRegistry.isNativeCompatible("somemod:gated_merchant"));
    }

    @Test
    void oldDescriptorWithUnknownFlagStillParses() {
        TradeTypeDescriptor d = new com.google.gson.Gson().fromJson(
                "{\"entityTypeId\":\"somemod:old\",\"abstractVillagerCompatible\":true,\"legacyCompatible\":true}",
                TradeTypeDescriptor.class);
        assertEquals("somemod:old", d.entityTypeId);
        assertTrue(d.abstractVillagerCompatible);
    }

    @Test
    void effectiveDisplayNameFallsBackToEntityTypeId() {
        TradeTypeDescriptor d = new TradeTypeDescriptor();
        d.entityTypeId = "somemod:x";
        assertEquals("somemod:x", d.effectiveDisplayName());
        d.displayName = "X the Merchant";
        assertEquals("X the Merchant", d.effectiveDisplayName());
    }
}
