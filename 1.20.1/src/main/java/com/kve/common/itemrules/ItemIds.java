package com.kve.common.itemrules;

import com.kve.common.kubejs.KubeJSItemAttrExporter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

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
            ResourceLocation rl = ForgeRegistries.ITEMS.getKey(i);
            return rl == null ? "" : rl.toString();
        });
        return id.isEmpty() ? null : id;
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
