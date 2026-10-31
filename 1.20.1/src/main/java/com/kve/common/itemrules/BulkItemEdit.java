package com.kve.common.itemrules;

import com.kve.common.kubejs.KubeJSItemAttrExporter.ItemEntry;

import java.util.ArrayList;
import java.util.List;

public record BulkItemEdit(Integer maxStackSize, String rarity, Boolean fireResistant, Integer burnTime,
                           Boolean hideFromCreativeTab, Boolean hideFromRecipeViewers) {

    /** Mismos valores que ItemAttributeScreen (y que el enum Rarity de vanilla). */
    public static final List<String> RARITIES = List.of("COMMON", "UNCOMMON", "RARE", "EPIC");

    public static BulkItemEdit hideFromViewers() {
        return new BulkItemEdit(null, null, null, null, null, true);
    }

    public boolean isEmpty() {
        return maxStackSize == null && rarity == null && fireResistant == null && burnTime == null
                && hideFromCreativeTab == null && hideFromRecipeViewers == null;
    }

    public List<String> validate() {
        List<String> p = new ArrayList<>();
        if (isEmpty()) p.add("No hay ningun cambio elegido");
        if (maxStackSize != null && (maxStackSize < 1 || maxStackSize > 64)) p.add("El stack debe ir de 1 a 64");
        if (rarity != null && !RARITIES.contains(rarity)) p.add("Rareza invalida: " + rarity);
        if (burnTime != null && burnTime < 0) p.add("El tiempo de combustion no puede ser negativo");
        return p;
    }

    /** La entrada resultante para {@code itemId}: copia de {@code existing} (o nueva) con estos cambios encima. */
    public ItemEntry applyTo(String itemId, ItemEntry existing) {
        ItemEntry e = existing != null ? existing.copy() : new ItemEntry();
        e.itemId = itemId;
        if (maxStackSize != null) e.maxStackSize = maxStackSize;
        if (rarity != null) e.rarity = rarity;
        if (fireResistant != null) { e.fireResistant = fireResistant; e.fireResistantSet = true; }
        if (burnTime != null) e.burnTime = burnTime;
        if (hideFromCreativeTab != null) e.hideFromCreativeTab = hideFromCreativeTab;
        if (hideFromRecipeViewers != null) e.hideFromRecipeViewers = hideFromRecipeViewers;
        return e;
    }

    /** Resumen corto para el mensaje / la cola de cambios: "stack 16, rareza rare". */
    public String describe() {
        List<String> parts = new ArrayList<>();
        if (maxStackSize != null) parts.add("stack " + maxStackSize);
        if (rarity != null) parts.add("rareza " + rarity);
        if (fireResistant != null) parts.add(fireResistant ? "resiste fuego" : "no resiste fuego");
        if (burnTime != null) parts.add("combustible " + burnTime);
        if (hideFromCreativeTab != null) parts.add(hideFromCreativeTab ? "oculto en creativo" : "visible en creativo");
        if (hideFromRecipeViewers != null) parts.add(hideFromRecipeViewers ? "oculto en JEI/REI" : "visible en JEI/REI");
        return String.join(", ", parts);
    }
}
