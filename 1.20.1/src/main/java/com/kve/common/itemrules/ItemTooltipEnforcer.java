package com.kve.common.itemrules;

import com.kve.common.kubejs.KubeJSItemAttrExporter;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

public class ItemTooltipEnforcer {

    /**
     * Marca NBT del ItemStack que arma la vista previa en vivo
     * de ItemAttributeScreen. Ese stack ya trae el BORRADOR (lore +
     * atributos + resumen de combate); si los enforcers le aplicaban encima
     * la entrada GUARDADA, el tooltip salia con las lineas duplicadas y los
     * atributos del borrador pisados por los guardados.
     */
    public static final String PREVIEW_TAG = "shovel_js_preview";

    public static boolean isPreview(ItemStack stack) {
        return stack.hasTag() && stack.getTag().getBoolean(PREVIEW_TAG);
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || isPreview(stack)) return;

        KubeJSItemAttrExporter.ItemEntry e = ItemIds.entryFor(stack);
        if (e == null) return;

        // Atributos en modo Reemplazar: sin signo, como stat propio (ver ItemAttrTooltipStyle)
        if (e.enabled && KubeJSItemAttrExporter.hasAttributeEdits(e)) {
            ItemAttrTooltipStyle.apply(event.getToolTip(), KubeJSItemAttrExporter.effectiveAttributeMods(e), e.addModeAttributes);
        }

        if (e.combat != null) event.getToolTip().addAll(ItemCombatTooltip.lines(e.combat));

        if (e.tooltip == null || e.tooltip.isEmpty()) return;

        int insertAt = 1;
        for (String line : e.tooltip) {
            if (line == null || line.isBlank()) continue;
            event.getToolTip().add(insertAt++, Component.literal(line.replace('&', '\u00A7')));
        }
    }
}
