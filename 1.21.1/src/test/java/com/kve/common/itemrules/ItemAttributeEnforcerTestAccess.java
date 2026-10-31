package com.kve.common.itemrules;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

/** Acceso de test a {@link ItemAttributeEnforcer#idFor()} (package-private). */
public final class ItemAttributeEnforcerTestAccess {
    private ItemAttributeEnforcerTestAccess() {}

    public static ResourceLocation idFor(String itemId, String attrId) {
        return ItemAttributeEnforcer.idFor(itemId, attrId, EquipmentSlot.MAINHAND);
    }
}
