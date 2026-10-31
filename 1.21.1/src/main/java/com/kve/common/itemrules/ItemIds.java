package com.kve.common.itemrules;

import com.kve.common.kubejs.KubeJSItemAttrExporter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Optimizacion, mismo criterio que {@code MobTypeIds}: el tooltip
 * (cada frame mientras se mira un item) y los enforcers de combate (cada
 * golpe) hacian {@code getKey(item).toString()} para buscar la regla del
 * item. Los Item no cambian en runtime: id calculado una vez por item.
 */
public final class ItemIds {
    private ItemIds() {}

    private static final ConcurrentHashMap<Item, String> IDS = new ConcurrentHashMap<>();

    public static String idOf(Item item) {
        String id = IDS.computeIfAbsent(item, i -> {
            ResourceLocation rl = BuiltInRegistries.ITEM.getKey(i);
            return rl == null ? "" : rl.toString();
        });
        return id.isEmpty() ? null : id;
    }

    /**
     * Slot "natural" del item (armadura en su slot, escudo en la mano
     * secundaria, el resto en la principal). 1.21: vanilla solo lo expone como
     * metodo de instancia de LivingEntity; esta es la misma logica sin
     * entidad (override de NeoForge primero, despues Equipable).
     */
    public static net.minecraft.world.entity.EquipmentSlot naturalSlot(ItemStack stack) {
        var forced = stack.getEquipmentSlot();
        if (forced != null) return forced;
        var equipable = net.minecraft.world.item.Equipable.get(stack);
        return equipable != null ? equipable.getEquipmentSlot() : net.minecraft.world.entity.EquipmentSlot.MAINHAND;
    }

    /** Regla habilitada del editor de items para este stack, o null (corte inmediato si no hay reglas). */
    public static KubeJSItemAttrExporter.ItemEntry entryFor(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !KubeJSItemAttrExporter.hasAppliedEntries()) return null;
        String id = idOf(stack.getItem());
        if (id == null) return null;
        KubeJSItemAttrExporter.ItemEntry e = KubeJSItemAttrExporter.getAppliedEntry(id);
        return e != null && e.enabled ? e : null;
    }
}
