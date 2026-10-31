package com.kve.common.kubejs;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;

public final class KubeJsItemAttrSupport {
    private KubeJsItemAttrSupport() {}

    /** True si KubeJS 1.20.1 puede editar los atributos por defecto de este item. */
    public static boolean supportsAttributeEdits(Item item) {
        return item instanceof SwordItem
                || item instanceof DiggerItem
                || item instanceof ArmorItem
                || item instanceof TridentItem;
    }

    /**
     * Version por id, para el exporter (que solo tiene el string). Devuelve
     * {@code true} - "no advertir" - cuando no se puede saber: id mal
     * formado, id que no esta en el registry, o registry aun sin poblar
     * (JUnit sin bootstrap). Un falso aviso es peor que ningun aviso.
     */
    public static boolean supportsAttributeEdits(String itemId) {
        if (itemId == null || itemId.isBlank()) return true;
        try {
            ResourceLocation rl = ResourceLocation.tryParse(itemId);
            if (rl == null) return true;
            Item item = BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
            return item == null || supportsAttributeEdits(item);
        } catch (Throwable t) {
            return true;
        }
    }

    public static EquipmentSlot defaultSlot(Item item) {
        if (item instanceof ArmorItem armor) return armor.getType().getSlot();
        return EquipmentSlot.MAINHAND;
    }
}
